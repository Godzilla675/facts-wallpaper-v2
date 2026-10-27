package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.ai.SchemaAdapter
import com.factswallpaper.core.model.ProviderProfile
import io.ktor.client.HttpClient
import java.util.Base64
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Anthropic Messages client with tool-forced structured output (spec §6.3.3, §11.2). */
class AnthropicClient(
    profile: ProviderProfile,
    apiKey: String,
    httpClient: HttpClient,
    onTemperatureUnsupported: suspend () -> Unit = {},
    onResponseFormatUnsupported: suspend () -> Unit = {},
) : BaseAiClient(
    profile = profile,
    apiKey = apiKey,
    httpClient = httpClient,
    onTemperatureUnsupported = onTemperatureUnsupported,
    onResponseFormatUnsupported = onResponseFormatUnsupported,
) {

    override suspend fun execute(request: AiRequest): AiResponse {
        val url = "${profile.baseUrl.normalizedBaseUrl()}/messages"
        val headers = mapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to "2023-06-01",
            "Content-Type" to "application/json",
        )
        if (request.pdfBytes != null && request.pdfBytes.size.toLong() > profile.maxInlinePdfBytes) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                "The PDF exceeds this profile's ${profile.maxInlinePdfBytes} byte inline limit.",
            )
        }
        var useTool = request.jsonSchema != null && profile.supportsJsonSchema
        var omitTemperature = !shouldSendTemperature(request) ||
            profile.modelName.contains("opus-5-5", ignoreCase = true)

        suspend fun call(): AiResponse {
            val body = buildBody(request, useTool, omitTemperature)
            return parse(post(url, headers, body))
        }

        return try {
            call()
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                exception.message.contains("temperature", ignoreCase = true)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call()
            } else if (exception.statusCode == 400 && useTool &&
                (exception.message.contains("tool", ignoreCase = true) ||
                    exception.message.contains("schema", ignoreCase = true))
            ) {
                useTool = false
                onResponseFormatUnsupported()
                call()
            } else {
                throw exception
            }
        }
    }

    private fun buildBody(request: AiRequest, useTool: Boolean, omitTemperature: Boolean): String {
        val systemPrompt = if (request.jsonSchema != null && !useTool) {
            SchemaAdapter.embedSchema(request.systemPrompt, request.jsonSchema)
        } else {
            request.systemPrompt
        }
        val body = buildJsonObject {
            put("model", profile.modelName)
            put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            put("system", systemPrompt)
            if (!omitTemperature && request.temperature != null) {
                put("temperature", JsonPrimitive(request.temperature))
            }
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    request.pdfBytes?.let { bytes ->
                                        add(
                                            buildJsonObject {
                                                put("type", "document")
                                                put(
                                                    "source",
                                                    buildJsonObject {
                                                        put("type", "base64")
                                                        put("media_type", "application/pdf")
                                                        put(
                                                            "data",
                                                            Base64.getEncoder().encodeToString(bytes),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    request.imageBytes?.let { bytes ->
                                        add(
                                            buildJsonObject {
                                                put("type", "image")
                                                put(
                                                    "source",
                                                    buildJsonObject {
                                                        put("type", "base64")
                                                        put(
                                                            "media_type",
                                                            request.imageMimeType ?: "image/png",
                                                        )
                                                        put(
                                                            "data",
                                                            Base64.getEncoder().encodeToString(bytes),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", request.userPrompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            if (request.jsonSchema != null && useTool) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("name", "emit_result")
                                put("description", "Return the structured result for this task.")
                                put(
                                    "input_schema",
                                    ProviderJson.parseToJsonElement(request.jsonSchema),
                                )
                            },
                        )
                    },
                )
                put(
                    "tool_choice",
                    buildJsonObject {
                        put("type", "tool")
                        put("name", "emit_result")
                    },
                )
            }
        }
        return RequestParameterOverrides.apply(
            profile = profile,
            request = request,
            kind = RequestBodyKind.ANTHROPIC,
            body = body,
        ).toString()
    }

    private fun parse(body: String): AiResponse {
        val root = ProviderJson.parseToJsonElement(body).asObject()
            ?: throw AiException(AiErrorKind.CONTENT, "Anthropic payload was not a JSON object.")
        val content = root.arr("content")?.objects().orEmpty()
        val toolInput = content.firstOrNull { it.string("type") == "tool_use" }
            ?.get("input") as? JsonObject
        val text = toolInput?.toString()
            ?: content.firstOrNull { it.string("type") == "text" }?.string("text")
        val reasoning = content.filter {
            it.string("type") == "thinking" || it.string("type") == "redacted_thinking"
        }.mapNotNull { it.string("thinking") ?: it.string("text") }
        val parsed = ProviderReasoningParser.parse(text, reasoning)
        val finish = root.string("stop_reason")
        val usage = root.obj("usage")
        return AiResponse(
            text = parsed.content,
            model = root.string("model") ?: profile.modelName,
            inputTokens = usage?.int("input_tokens") ?: 0,
            outputTokens = usage?.int("output_tokens") ?: 0,
            finishReason = finish,
            reasoningText = parsed.reasoning,
        )
    }
}

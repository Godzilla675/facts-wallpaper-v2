package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.ai.SchemaAdapter
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import io.ktor.client.HttpClient
import java.util.Base64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** OpenAI Responses + Chat Completions client (spec §6.3.2). */
class OpenAiClient(
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
        val useResponses = request.pdfBytes != null &&
            profile.supportsPdfInput &&
            request.pageTool == null
        return if (useResponses) executeResponses(request) else executeChat(request)
    }

    private suspend fun executeChat(request: AiRequest): AiResponse {
        val url = "${profile.baseUrl.normalizedBaseUrl()}/chat/completions"
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )
        var omitTemperature = !shouldSendTemperature(request)
        var includeFormat = request.jsonSchema != null &&
            (profile.supportsJsonSchema || profile.supportsJsonMode)
        var usePageTool = request.pageTool != null
        val schema = ChatCompletionsSupport.schemaFor(profile, request)

        suspend fun call(): AiResponse {
            val body = ChatCompletionsSupport.buildBody(
                profile = profile,
                request = request.copy(
                    temperature = if (omitTemperature) null else request.temperature,
                    pageTool = if (usePageTool) request.pageTool else null,
                ),
                includeResponseFormat = includeFormat,
                schema = schema,
            )
            return ChatCompletionsSupport.parse(post(url, headers, body), profile)
        }

        return try {
            call()
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                ChatCompletionsSupport.isTemperatureRejection(exception.message)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call()
            } else if (exception.statusCode == 400 && usePageTool &&
                ChatCompletionsSupport.isToolRejection(exception.message)
            ) {
                usePageTool = false
                call()
            } else if (exception.statusCode == 400 && includeFormat &&
                ChatCompletionsSupport.isSchemaRejection(exception.message)
            ) {
                includeFormat = false
                onResponseFormatUnsupported()
                call()
            } else {
                throw exception
            }
        }
    }

    private suspend fun executeResponses(request: AiRequest): AiResponse {
        val url = "${profile.baseUrl.normalizedBaseUrl()}/responses"
        val headers = mapOf(
            "Authorization" to "Bearer $apiKey",
            "Content-Type" to "application/json",
        )
        var omitTemperature = !shouldSendTemperature(request)
        var includeFormat = request.jsonSchema != null &&
            (profile.supportsJsonSchema || profile.supportsJsonMode)
        val schema = ChatCompletionsSupport.schemaFor(profile, request)

        suspend fun call(): AiResponse {
            val body = buildResponsesBody(
                request = if (omitTemperature) request.copy(temperature = null) else request,
                includeFormat = includeFormat,
                schema = schema,
            )
            return parseResponses(post(url, headers, body))
        }

        return try {
            call()
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                ChatCompletionsSupport.isTemperatureRejection(exception.message)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call()
            } else if (exception.statusCode == 400 && includeFormat &&
                ChatCompletionsSupport.isSchemaRejection(exception.message)
            ) {
                includeFormat = false
                onResponseFormatUnsupported()
                call()
            } else {
                throw exception
            }
        }
    }

    private fun buildResponsesBody(
        request: AiRequest,
        includeFormat: Boolean,
        schema: String?,
    ): String {
        val systemPrompt = if (request.jsonSchema != null &&
            (!includeFormat || schema == null)
        ) {
            SchemaAdapter.embedSchema(request.systemPrompt, request.jsonSchema)
        } else {
            request.systemPrompt
        }
        val allImages = buildList {
            request.imageBytes?.let { add(it) }
            addAll(request.images)
        }
        val body = buildJsonObject {
            put("model", profile.modelName)
            put(
                "input",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "input_text")
                                            put("text", systemPrompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "content",
                                buildJsonArray {
                                    request.pdfBytes?.let { bytes ->
                                        add(
                                            buildJsonObject {
                                                put("type", "input_file")
                                                put(
                                                    "filename",
                                                    request.pdfFileName ?: "study.pdf",
                                                )
                                                put(
                                                    "file_data",
                                                    "data:application/pdf;base64," +
                                                        Base64.getEncoder().encodeToString(bytes),
                                                )
                                            },
                                        )
                                    }
                                    allImages.forEach { bytes ->
                                        add(
                                            buildJsonObject {
                                                put("type", "input_image")
                                                put(
                                                    "image_url",
                                                    "data:${request.imageMimeType ?: "image/jpeg"};base64," +
                                                        Base64.getEncoder().encodeToString(bytes),
                                                )
                                            },
                                        )
                                    }
                                    add(
                                        buildJsonObject {
                                            put("type", "input_text")
                                            put("text", request.userPrompt)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
            if (profile.supportsTemperature && request.temperature != null) {
                put("temperature", JsonPrimitive(request.temperature))
            }
            put("max_output_tokens", JsonPrimitive(request.maxOutputTokens))
            if (request.task == AiTask.FORMULA_VERIFICATION || request.task == AiTask.JSON_REPAIR) {
                put(
                    "reasoning",
                    buildJsonObject { put("effort", "minimal") },
                )
            }
            if (request.jsonSchema != null && includeFormat && schema != null) {
                put(
                    "text",
                    buildJsonObject {
                        put(
                            "format",
                            buildJsonObject {
                                put("type", "json_schema")
                                put("name", "structured_output")
                                put("strict", true)
                                put("schema", ProviderJson.parseToJsonElement(schema))
                            },
                        )
                    },
                )
            }
        }
        return RequestParameterOverrides.apply(
            profile = profile,
            request = request,
            kind = RequestBodyKind.OPENAI_RESPONSES,
            body = body,
        ).toString()
    }

    private fun parseResponses(body: String): AiResponse {
        val root = ProviderJson.parseToJsonElement(body).asObject()
            ?: throw AiException(AiErrorKind.CONTENT, "Responses payload was not a JSON object.")
        val direct = root.string("output_text")?.trim().orEmpty()
        val fromOutput = root.arr("output")
            ?.objects()
            .orEmpty()
            .flatMap { item -> item.arr("content")?.objects().orEmpty() }
            .mapNotNull { it.string("text") }
            .joinToString("\n")
            .trim()
        val text = direct.ifBlank { fromOutput }
        val reasoning = root.arr("output")
            ?.objects()
            .orEmpty()
            .filter { it.string("type") == "reasoning" }
            .mapNotNull { item ->
                item.string("reasoning")
                    ?: item.string("summary_text")
                    ?: ProviderReasoningParser.jsonText(item["summary"])
            }
        val parsed = ProviderReasoningParser.parse(
            content = text,
            reasoningFields = reasoning + listOf(
                root.string("reasoning_content"),
                root.string("reasoning"),
            ),
        )
        val usage = root.obj("usage")
        val finish = root.string("status")
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

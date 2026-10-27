package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.ai.AiSchemas
import com.factswallpaper.core.ai.SchemaAdapter
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import io.ktor.client.HttpClient
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Gemini native `v1beta` generateContent client (spec §6.3.1). */
class GeminiClient(
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
        val base = geminiBaseUrl(profile.baseUrl)
        val url = "$base/models/${profile.modelName}:generateContent"
        val headers = mapOf("x-goog-api-key" to apiKey)
        var omitTemperature = !shouldSendTemperature(request)
        var schema = SchemaAdapter.forProvider(
            type = ProviderType.GEMINI,
            supportsJsonSchema = profile.supportsJsonSchema,
            canonicalSchema = request.jsonSchema ?: AiSchemas.OutlineJsonSchema,
        ).takeIf { request.jsonSchema != null && profile.supportsJsonSchema }

        suspend fun call(useSchema: Boolean): AiResponse {
            val body = buildBody(request, omitTemperature, if (useSchema) schema else null)
            val text = post(url, headers, body)
            return parse(text)
        }

        return try {
            call(schema != null)
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                exception.message.contains("temperature", ignoreCase = true)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call(schema != null)
            } else if (exception.statusCode == 400 && schema != null &&
                (exception.message.contains("responseSchema", ignoreCase = true) ||
                    exception.message.contains("schema", ignoreCase = true))
            ) {
                schema = null
                onResponseFormatUnsupported()
                call(false)
            } else {
                throw exception
            }
        }
    }

    private fun buildBody(
        request: AiRequest,
        omitTemperature: Boolean,
        schema: String?,
    ): String {
        val systemPrompt = if (request.jsonSchema != null && schema == null) {
            SchemaAdapter.embedSchema(request.systemPrompt, request.jsonSchema)
        } else {
            request.systemPrompt
        }
        val allImages = buildList {
            request.imageBytes?.let { add(it) }
            addAll(request.images)
        }
        val body = buildJsonObject {
            put(
                "systemInstruction",
                buildJsonObject {
                    put(
                        "parts",
                        buildJsonArray {
                            add(buildJsonObject { put("text", systemPrompt) })
                        },
                    )
                },
            )
            put(
                "contents",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "user")
                            put(
                                "parts",
                                buildJsonArray {
                                    request.pdfBytes?.let { bytes ->
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "inline_data",
                                                    buildJsonObject {
                                                        put("mime_type", "application/pdf")
                                                        put("data", base64(bytes))
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    allImages.forEach { bytes ->
                                        add(
                                            buildJsonObject {
                                                put(
                                                    "inline_data",
                                                    buildJsonObject {
                                                        put(
                                                            "mime_type",
                                                            request.imageMimeType ?: "image/jpeg",
                                                        )
                                                        put("data", base64(bytes))
                                                    },
                                                )
                                            },
                                        )
                                    }
                                    add(buildJsonObject { put("text", request.userPrompt) })
                                },
                            )
                        },
                    )
                },
            )
            put(
                "generationConfig",
                buildJsonObject {
                    if (!omitTemperature && request.temperature != null) {
                        put("temperature", JsonPrimitive(request.temperature))
                    }
                    put("maxOutputTokens", JsonPrimitive(request.maxOutputTokens))
                    if (request.jsonSchema != null && schema != null) {
                        put("responseMimeType", "application/json")
                        put("responseSchema", ProviderJson.parseToJsonElement(schema))
                    }
                },
            )
        }
        return RequestParameterOverrides.apply(
            profile = profile,
            request = request,
            kind = RequestBodyKind.GEMINI,
            body = body,
        ).toString()
    }

    private fun parse(body: String): AiResponse {
        val root = ProviderJson.parseToJsonElement(body).asObject()
            ?: throw AiException(
                com.factswallpaper.core.ai.AiErrorKind.CONTENT,
                "Gemini response was not a JSON object.",
            )
        val candidate = root.arr("candidates")?.objects()?.firstOrNull()
        val parts = candidate?.obj("content")?.arr("parts")?.objects().orEmpty()
        val content = parts.filterNot {
            (it["thought"] as? JsonPrimitive)?.booleanOrNull == true
        }.mapNotNull { it.string("text") }.joinToString("\n")
        val reasoning = parts.filter {
            (it["thought"] as? JsonPrimitive)?.booleanOrNull == true
        }.mapNotNull { it.string("text") }
        val parsed = ProviderReasoningParser.parse(content, reasoning)
        val finish = candidate?.string("finishReason")
        val usage = root.obj("usageMetadata")
        val input = usage?.int("promptTokenCount") ?: 0
        val output = (usage?.int("candidatesTokenCount") ?: 0) +
            (usage?.int("thoughtsTokenCount") ?: 0)
        return AiResponse(
            text = parsed.content,
            model = profile.modelName,
            inputTokens = input,
            outputTokens = output,
            finishReason = finish,
            reasoningText = parsed.reasoning,
        )
    }

    private fun geminiBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.normalizedBaseUrl()
        return if (
            trimmed.contains("generativelanguage.googleapis.com") &&
            !trimmed.contains("/v1beta") &&
            !trimmed.contains("/v1")
        ) {
            "$trimmed/v1beta"
        } else {
            trimmed
        }
    }

    private fun base64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
}

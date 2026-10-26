package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.ai.AiSchemas
import com.factswallpaper.core.ai.SchemaAdapter
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import java.util.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

internal object ChatCompletionsSupport {

    fun buildBody(
        profile: ProviderProfile,
        request: AiRequest,
        includeResponseFormat: Boolean,
        schema: String?,
    ): String {
        val systemPrompt = if (request.jsonSchema != null &&
            (!includeResponseFormat || schema == null)
        ) {
            SchemaAdapter.embedSchema(request.systemPrompt, request.jsonSchema)
        } else {
            request.systemPrompt
        }
        return buildJsonObject {
            put("model", profile.modelName)
            put(
                "messages",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", systemPrompt)
                        },
                    )
                    add(
                        buildJsonObject {
                            put("role", "user")
                            if (request.imageBytes != null) {
                                put(
                                    "content",
                                    buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("type", "text")
                                                put("text", request.userPrompt)
                                            },
                                        )
                                        add(
                                            buildJsonObject {
                                                put("type", "image_url")
                                                put(
                                                    "image_url",
                                                    buildJsonObject {
                                                        put(
                                                            "url",
                                                            "data:${request.imageMimeType ?: "image/png"};base64," +
                                                                Base64.getEncoder().encodeToString(
                                                                    request.imageBytes,
                                                                ),
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            } else {
                                put("content", request.userPrompt)
                            }
                        },
                    )
                },
            )
            if (profile.supportsTemperature && request.temperature != null) {
                put("temperature", JsonPrimitive(request.temperature))
            }
            if (profile.providerType == ProviderType.OPENAI) {
                put("max_completion_tokens", JsonPrimitive(request.maxOutputTokens))
            } else {
                put("max_tokens", JsonPrimitive(request.maxOutputTokens))
            }
            if (request.task == AiTask.FORMULA_VERIFICATION || request.task == AiTask.JSON_REPAIR) {
                if (profile.providerType == ProviderType.OPENAI ||
                    profile.providerType == ProviderType.AZURE_OPENAI
                ) {
                    put("reasoning_effort", "minimal")
                }
            }
            if (request.jsonSchema != null && includeResponseFormat) {
                when {
                    schema != null -> put(
                        "response_format",
                        buildJsonObject {
                            put("type", "json_schema")
                            put(
                                "json_schema",
                                buildJsonObject {
                                    put("name", "structured_output")
                                    put("strict", true)
                                    put("schema", ProviderJson.parseToJsonElement(schema))
                                },
                            )
                        },
                    )
                    profile.supportsJsonMode -> put(
                        "response_format",
                        buildJsonObject { put("type", "json_object") },
                    )
                }
            }
        }.toString()
    }

    fun schemaFor(
        profile: ProviderProfile,
        request: AiRequest,
    ): String? {
        val canonical = request.jsonSchema ?: return null
        return SchemaAdapter.forProvider(
            type = profile.providerType,
            supportsJsonSchema = profile.supportsJsonSchema,
            canonicalSchema = canonical,
        )
    }

    fun parse(body: String, profile: ProviderProfile): AiResponse {
        val root = ProviderJson.parseToJsonElement(body).asObject()
            ?: throw AiException(AiErrorKind.CONTENT, "Chat response was not a JSON object.")
        val choice = root.arr("choices")?.objects()?.firstOrNull()
        val message = choice?.obj("message")
        val content = message?.get("content")
        val text = when (content) {
            is JsonPrimitive -> content.contentOrNull
            is JsonArray -> content.objects().mapNotNull { it.string("text") }
                .joinToString("\n")
                .ifBlank { null }
            else -> null
        }
        val finish = choice?.string("finish_reason")
        val usage = root.obj("usage")
        return AiResponse(
            text = text?.trim().orEmpty().ifEmpty {
                throw AiException(
                    AiErrorKind.CONTENT,
                    "Chat response contained no text (finishReason=$finish).",
                )
            },
            model = root.string("model") ?: profile.modelName,
            inputTokens = usage?.int("prompt_tokens") ?: 0,
            outputTokens = usage?.int("completion_tokens") ?: 0,
            finishReason = finish,
        )
    }

    fun isSchemaRejection(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("response_format") ||
            lower.contains("json_schema") ||
            lower.contains("json schema") ||
            lower.contains("response format")
    }

    fun isTemperatureRejection(message: String): Boolean =
        message.contains("temperature", ignoreCase = true)

    fun isPdfTooLarge(request: AiRequest, profile: ProviderProfile): Boolean =
        request.pdfBytes != null && request.pdfBytes.size.toLong() > profile.maxInlinePdfBytes

    fun defaultSchemaFor(request: AiRequest): String = request.jsonSchema ?: AiSchemas.FactsJsonSchema
}

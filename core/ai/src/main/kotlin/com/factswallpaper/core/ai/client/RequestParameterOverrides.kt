package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.model.ProviderProfile
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class RequestBodyKind {
    OPENAI_CHAT,
    OPENAI_RESPONSES,
    ANTHROPIC,
    GEMINI,
}

data class RawRequestJsonValidation(
    val isValid: Boolean,
    val error: String? = null,
    val position: Int? = null,
)

object RawRequestJson {
    private val secretPattern = Regex(
        """(?i)"(?:api[_-]?key|x-api-key|authorization|access[_-]?token|client[_-]?secret|secret[_-]?key)"\s*:\s*"|sk-[A-Za-z0-9_-]{12,}|AIza[A-Za-z0-9_-]{20,}|Bearer\s+[A-Za-z0-9._~-]{12,}""",
    )
    private val offsetPattern = Regex("""(?:offset|character|position)\s+(\d+)""", RegexOption.IGNORE_CASE)

    fun validate(value: String): RawRequestJsonValidation = try {
        val parsed = Json.parseToJsonElement(value)
        if (parsed is JsonObject) {
            RawRequestJsonValidation(isValid = true)
        } else {
            RawRequestJsonValidation(
                isValid = false,
                error = "Expected a JSON object.",
                position = 0,
            )
        }
    } catch (exception: SerializationException) {
        RawRequestJsonValidation(
            isValid = false,
            error = exception.message ?: "Invalid JSON.",
            position = offsetPattern.find(exception.message.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull(),
        )
    }

    fun looksLikeSecret(value: String): Boolean = secretPattern.containsMatchIn(value)

    fun mergePreset(current: String, preset: String): String {
        val currentObject = (runCatching { Json.parseToJsonElement(current) }.getOrNull() as? JsonObject)
            ?: JsonObject(emptyMap())
        val presetObject = (runCatching { Json.parseToJsonElement(preset) }.getOrNull() as? JsonObject)
            ?: throw IllegalArgumentException("The selected request preset is not a JSON object.")
        return deepMerge(currentObject, presetObject).toString()
    }

    internal fun deepMerge(base: JsonObject, override: JsonObject): JsonObject =
        JsonObject(
            base.toMutableMap().apply {
                override.forEach { (key, value) ->
                    val existing = this[key] as? JsonObject
                    this[key] = if (existing != null && value is JsonObject) {
                        deepMerge(existing, value)
                    } else {
                        value
                    }
                }
            },
        )
}

data class ProviderHeaderValue(
    val key: String = "",
    val value: String = "",
)

data class ProviderHeadersParseResult(
    val entries: List<ProviderHeaderValue>,
    val error: String? = null,
)

object ProviderHeadersJson {
    fun parse(value: String): ProviderHeadersParseResult {
        return try {
            val parsed = Json.parseToJsonElement(value) as? JsonObject
            if (parsed == null) {
                ProviderHeadersParseResult(emptyList(), "Headers must be a JSON object.")
            } else {
                ProviderHeadersParseResult(
                    entries = parsed.mapNotNull { (key, element) ->
                        (element as? JsonPrimitive)?.contentOrNull?.let {
                            ProviderHeaderValue(key, it)
                        }
                    },
                )
            }
        } catch (exception: SerializationException) {
            ProviderHeadersParseResult(
                entries = emptyList(),
                error = exception.message ?: "Headers contain invalid JSON.",
            )
        }
    }

    fun serialize(entries: List<ProviderHeaderValue>): String = JsonObject(
        entries.mapNotNull { entry ->
            val key = entry.key.trim()
            val value = entry.value.trim()
            if (key.isEmpty() || value.isEmpty()) {
                null
            } else {
                key to JsonPrimitive(value)
            }
        }.toMap(),
    ).toString()
}

internal object RequestParameterOverrides {
    fun apply(
        profile: ProviderProfile,
        request: AiRequest,
        kind: RequestBodyKind,
        body: JsonObject,
    ): JsonObject {
        var result = body

        fun putPath(path: List<String>, value: JsonElement) {
            result = putPath(result, path, value)
        }

        if (profile.supportsTemperature) {
            (profile.temperature ?: request.temperature)?.let { temperature ->
                putPath(temperaturePath(kind), JsonPrimitive(temperature))
            }
        }
        profile.topP?.let { topP ->
            putPath(topPPath(kind), JsonPrimitive(topP))
        }
        profile.reasoningEffort
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { effort ->
                applyReasoningEffort(
                    body = { result },
                    putPath = ::putPath,
                    profile = profile,
                    kind = kind,
                    effort = effort,
                )
            }

        val rawOverrides = try {
            Json.parseToJsonElement(profile.rawRequestJson) as? JsonObject
                ?: throw IllegalArgumentException("Raw request parameters must be a JSON object.")
        } catch (exception: SerializationException) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                "Raw request parameters are invalid JSON: ${exception.message.orEmpty()}",
                cause = exception,
            )
        } catch (exception: IllegalArgumentException) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                exception.message ?: "Raw request parameters must be a JSON object.",
                cause = exception,
            )
        }
        return RawRequestJson.deepMerge(result, rawOverrides)
    }

    private fun applyReasoningEffort(
        body: () -> JsonObject,
        putPath: (List<String>, JsonElement) -> Unit,
        profile: ProviderProfile,
        kind: RequestBodyKind,
        effort: String,
    ) {
        when (kind) {
            RequestBodyKind.OPENAI_CHAT -> putPath(
                listOf("reasoning_effort"),
                JsonPrimitive(effort),
            )
            RequestBodyKind.OPENAI_RESPONSES -> putPath(
                listOf("reasoning", "effort"),
                JsonPrimitive(effort),
            )
            RequestBodyKind.ANTHROPIC -> {
                if (effort.equals("none", ignoreCase = true)) {
                    putPath(
                        listOf("thinking"),
                        JsonObject(mapOf("type" to JsonPrimitive("disabled"))),
                    )
                } else {
                    putPath(
                        listOf("output_config", "effort"),
                        JsonPrimitive(effort),
                    )
                    val outputBudget = body()["max_tokens"]?.toString()?.toIntOrNull()
                        ?: profile.maxOutputTokens
                    val thinkingBudget = thinkingBudget(effort, profile.maxOutputTokens)
                        .coerceAtMost((outputBudget - 1).coerceAtLeast(0))
                    if (thinkingBudget >= 1_024) {
                        putPath(
                            listOf("thinking"),
                            JsonObject(
                                mapOf(
                                    "type" to JsonPrimitive("enabled"),
                                    "budget_tokens" to JsonPrimitive(thinkingBudget),
                                ),
                            ),
                        )
                    }
                }
            }
            RequestBodyKind.GEMINI -> {
                val thinkingBudget = if (effort.equals("none", ignoreCase = true)) {
                    0
                } else {
                    effort.toIntOrNull()?.coerceAtLeast(0)
                        ?: thinkingBudget(effort, profile.maxOutputTokens)
                }
                putPath(
                    listOf("generationConfig", "thinkingConfig"),
                    JsonObject(
                        mapOf(
                            "thinkingBudget" to JsonPrimitive(thinkingBudget),
                            "includeThoughts" to JsonPrimitive(false),
                        ),
                    ),
                )
            }
        }
    }

    private fun temperaturePath(kind: RequestBodyKind): List<String> =
        if (kind == RequestBodyKind.GEMINI) {
            listOf("generationConfig", "temperature")
        } else {
            listOf("temperature")
        }

    private fun topPPath(kind: RequestBodyKind): List<String> =
        if (kind == RequestBodyKind.GEMINI) {
            listOf("generationConfig", "topP")
        } else {
            listOf("top_p")
        }

    private fun thinkingBudget(effort: String, maxOutputTokens: Int): Int {
        val maxBudget = maxOutputTokens.coerceAtLeast(0)
        val preferred = effort.toIntOrNull() ?: when (effort.lowercase()) {
            "low" -> 1_024
            "medium" -> 4_096
            else -> 8_192
        }
        return preferred.coerceAtMost(maxBudget)
    }

    private fun putPath(
        root: JsonObject,
        path: List<String>,
        value: JsonElement,
    ): JsonObject {
        if (path.isEmpty()) return root
        val key = path.first()
        val updated = if (path.size == 1) {
            root.toMutableMap().apply {
                if (value == JsonNull) remove(key) else this[key] = value
            }
        } else {
            val child = root[key] as? JsonObject ?: JsonObject(emptyMap())
            root.toMutableMap().apply {
                this[key] = putPath(child, path.drop(1), value)
            }
        }
        return JsonObject(updated)
    }
}

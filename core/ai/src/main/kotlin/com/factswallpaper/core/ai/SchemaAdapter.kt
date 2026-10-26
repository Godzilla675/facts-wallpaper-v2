package com.factswallpaper.core.ai

import com.factswallpaper.core.model.ProviderType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Adapts the canonical §6.6 JSON Schema to each provider's accepted dialect
 * (spec §11.2). The local [SchemaValidator] remains the final authority.
 */
object SchemaAdapter {

    private val strictStrip = setOf(
        "\$schema",
        "\$id",
        "title",
        "default",
        "examples",
        "minLength",
        "maxLength",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "multipleOf",
        "minItems",
        "maxItems",
        "uniqueItems",
        "pattern",
    )

    private val geminiStrip = setOf(
        "\$schema",
        "\$id",
        "title",
        "default",
        "examples",
        "additionalProperties",
        "minLength",
        "maxLength",
        "minimum",
        "maximum",
        "exclusiveMinimum",
        "exclusiveMaximum",
        "multipleOf",
        "minItems",
        "maxItems",
        "uniqueItems",
        "pattern",
    )

    /**
     * Returns the schema to send to the provider, or `null` when the provider
     * cannot accept a schema and the caller must embed it in the prompt.
     */
    fun forProvider(
        type: ProviderType,
        supportsJsonSchema: Boolean,
        canonicalSchema: String,
    ): String? = when (type) {
        ProviderType.GEMINI -> forGemini(canonicalSchema)
        ProviderType.ANTHROPIC -> canonicalSchema
        ProviderType.OPENAI, ProviderType.AZURE_OPENAI ->
            if (supportsJsonSchema) forOpenAiStrict(canonicalSchema) else null
        ProviderType.OPENROUTER, ProviderType.DEEPSEEK, ProviderType.QWEN,
        ProviderType.OPENAI_COMPATIBLE ->
            if (supportsJsonSchema) forOpenAiStrict(canonicalSchema) else null
    }

    /** OpenAI strict mode: all properties required, additionalProperties=false. */
    fun forOpenAiStrict(canonicalSchema: String): String {
        val element = Json.parseToJsonElement(canonicalSchema)
        return strict(element).toString()
    }

    /** Gemini responseSchema subset: nullable instead of `["X","null"]`. */
    fun forGemini(canonicalSchema: String): String {
        val element = Json.parseToJsonElement(canonicalSchema)
        return gemini(element).toString()
    }

    private fun strict(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> {
            val properties = element["properties"] as? JsonObject
            buildJsonObject {
                element.forEach { (key, value) ->
                    if (key in strictStrip) return@forEach
                    when (key) {
                        "properties" -> put(
                            "properties",
                            JsonObject(
                                value.jsonObject.mapValues { (_, schema) -> strict(schema) },
                            ),
                        )
                        "items" -> put("items", strict(value))
                        "additionalProperties" -> put("additionalProperties", JsonPrimitive(false))
                        else -> put(key, strict(value))
                    }
                }
                if (properties != null) {
                    put("required", buildJsonArray { properties.keys.forEach { add(JsonPrimitive(it)) } })
                    put("additionalProperties", JsonPrimitive(false))
                }
            }
        }
        is JsonArray -> JsonArray(element.map(::strict))
        else -> element
    }

    private fun gemini(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            var nullable = false
            element.forEach { (key, value) ->
                if (key in geminiStrip) return@forEach
                when {
                    key == "type" && value is JsonArray -> {
                        val types = value.mapNotNull { (it as? JsonPrimitive)?.content }
                        nullable = types.any { it == "null" }
                        val concrete = types.firstOrNull { it != "null" } ?: "string"
                        put("type", JsonPrimitive(concrete))
                    }
                    key == "properties" -> put(
                        "properties",
                        JsonObject(value.jsonObject.mapValues { (_, schema) -> gemini(schema) }),
                    )
                    else -> put(key, gemini(value))
                }
            }
            if (nullable) put("nullable", JsonPrimitive(true))
        }
        is JsonArray -> JsonArray(element.map(::gemini))
        else -> element
    }

    /** Prompt-only fallback used by OpenAI-compatible endpoints without schema support. */
    fun embedSchema(systemPrompt: String, canonicalSchema: String): String =
        systemPrompt + AiPrompts.schemaInstruction(canonicalSchema)

    fun customHeaders(customHeadersJson: String): Map<String, String> {
        if (customHeadersJson.isBlank()) return emptyMap()
        return runCatching {
            val element = Json.parseToJsonElement(customHeadersJson) as JsonObject
            element.mapNotNull { (key, value) ->
                val text = (value as? JsonPrimitive)?.content ?: return@mapNotNull null
                if (text.isBlank()) null else key to text
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun isBoolean(element: JsonElement?): Boolean? =
        (element as? JsonPrimitive)?.booleanOrNull
}

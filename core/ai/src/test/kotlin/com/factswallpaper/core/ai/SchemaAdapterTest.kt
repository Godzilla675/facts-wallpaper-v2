package com.factswallpaper.core.ai

import com.factswallpaper.core.model.ProviderType
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class SchemaAdapterTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `openai strict mode requires every property and forbids extras`() {
        val adapted = SchemaAdapter.forOpenAiStrict(AiSchemas.FactsJsonSchema)
        val root = json.parseToJsonElement(adapted).jsonObject
        val factSchema = root["properties"]!!.jsonObject["facts"]!!.jsonObject["items"]!!.jsonObject

        assertThat(factSchema["additionalProperties"]!!.jsonPrimitive.content).isEqualTo("false")
        val required = factSchema["required"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertThat(required).containsExactly(
            "text",
            "latex",
            "requiresLatex",
            "templateType",
            "diagramType",
            "diagramSource",
            "sourceLessonTitle",
            "sourcePage",
            "tags",
            "confidence",
        )
        assertThat(adapted).doesNotContain("minLength")
        assertThat(adapted).doesNotContain("\"minimum\"")
        assertThat(adapted).doesNotContain("\"maximum\"")
    }

    @Test
    fun `gemini dialect converts nullable unions and drops additionalProperties`() {
        val adapted = SchemaAdapter.forGemini(AiSchemas.FactsJsonSchema)
        val root = json.parseToJsonElement(adapted).jsonObject
        val latex = root["properties"]!!.jsonObject["facts"]!!.jsonObject["items"]!!.jsonObject
            .let { it["properties"]!!.jsonObject["latex"]!!.jsonObject }

        assertThat(latex["type"]!!.jsonPrimitive.content).isEqualTo("string")
        assertThat(latex["nullable"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(adapted).doesNotContain("additionalProperties")
        assertThat(adapted).doesNotContain("minLength")
    }

    @Test
    fun `anthropic receives the canonical schema unchanged`() {
        val adapted = SchemaAdapter.forProvider(
            type = ProviderType.ANTHROPIC,
            supportsJsonSchema = true,
            canonicalSchema = AiSchemas.OutlineJsonSchema,
        )

        assertThat(adapted).isEqualTo(AiSchemas.OutlineJsonSchema)
    }

    @Test
    fun `openai compatible without schema support falls back to prompt embedding`() {
        val adapted = SchemaAdapter.forProvider(
            type = ProviderType.OPENAI_COMPATIBLE,
            supportsJsonSchema = false,
            canonicalSchema = AiSchemas.OutlineJsonSchema,
        )

        assertThat(adapted).isNull()
        val prompt = SchemaAdapter.embedSchema("system", AiSchemas.OutlineJsonSchema)
        assertThat(prompt).contains("Respond ONLY with a single JSON object")
        assertThat(prompt).contains("\"lessons\"")
    }

    @Test
    fun `openai compatible with schema support uses strict mode`() {
        val adapted = SchemaAdapter.forProvider(
            type = ProviderType.OPENROUTER,
            supportsJsonSchema = true,
            canonicalSchema = AiSchemas.OutlineJsonSchema,
        )

        assertThat(adapted).isNotNull()
        assertThat(adapted).contains("\"additionalProperties\":false")
    }

    @Test
    fun `custom headers json parses valid values and ignores invalid input`() {
        val headers = SchemaAdapter.customHeaders("""{"X-Org":"facts","X-Number":42,"X-Blank":" "}""")

        assertThat(headers).containsExactly("X-Org", "facts", "X-Number", "42")
        assertThat(SchemaAdapter.customHeaders("not-json")).isEmpty()
        assertThat(SchemaAdapter.customHeaders("")).isEmpty()
    }

    @Test
    fun `schemas remain verbatim canonical contracts`() {
        assertThat(AiSchemas.OutlineJsonSchema).contains("\"required\": [\"lessons\"]")
        assertThat(AiSchemas.FactsJsonSchema).contains("\"TIMELINE\"")
        assertThat(AiSchemas.FormulaVerificationJsonSchema).contains("\"valid\"")
        assertThat(AiPrompts.OutlineSystemPrompt)
            .startsWith("You are an expert academic curriculum analyst.")
        assertThat(AiPrompts.FactsSystemPrompt)
            .startsWith("You are an elite scientific and academic study-card creator.")
    }
}

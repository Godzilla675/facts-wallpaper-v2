package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.security.LogRedactor
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class JsonRepairServiceTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val canonicalSchema = """
        {
          "type": "object",
          "properties": { "facts": { "type": "array" } },
          "required": ["facts"]
        }
    """.trimIndent()

    private fun chatEnvelope(content: String): String {
        val quoted = kotlinx.serialization.json.JsonPrimitive(content).toString()
        return """
            {
              "model": "gpt-test",
              "choices": [{ "message": { "content": $quoted }, "finish_reason": "stop" }],
              "usage": { "prompt_tokens": 40, "completion_tokens": 10 }
            }
        """.trimIndent()
    }

    private fun buildService(
        profile: com.factswallpaper.core.model.ProviderProfile,
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): Triple<JsonRepairService, FakeUsageRecorder, FakeLogRecorder> {
        val usage = FakeUsageRecorder()
        val logs = FakeLogRecorder()
        val callService = AiCallService(
            profileProvider = FakeProfileProvider(listOf(profile)),
            apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test")),
            preferences = FakePreferences(diagnostics = true),
            clientFactory = AiClientFactory(mockHttpClient(handler)),
            retryPolicy = AiRetryPolicy(RetrySleeper { }),
            redactor = LogRedactor(),
            usageRecorder = usage,
            logRecorder = logs,
            networkMonitor = FakeNetworkMonitor(),
        )
        return Triple(JsonRepairService(callService), usage, logs)
    }

    @Test
    fun `repair sends one deterministic schema-bound JSON repair request`() = runTest {
        val profile = testProfile(type = ProviderType.OPENAI, model = "gpt-test")
        var capturedBody: String? = null
        val (service, usage, logs) = buildService(profile) { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = chatEnvelope("""{"facts":[]}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val outcome = service.repair(
            profile = profile,
            canonicalSchema = canonicalSchema,
            malformed = "{\"facts\":[{\"text\":\"broken\"",
        )

        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        val messages = body["messages"]!!.jsonArray
        assertThat(messages[0].jsonObject["content"]!!.jsonPrimitive.content)
            .isEqualTo(AiPrompts.JsonRepairSystemPrompt)
        val userPrompt = messages[1].jsonObject["content"]!!.jsonPrimitive.content
        assertThat(userPrompt).contains("Target JSON Schema")
        assertThat(userPrompt).contains("\"facts\"")
        assertThat(userPrompt).contains("Malformed response")
        assertThat(userPrompt).contains("broken")
        assertThat(body["temperature"]!!.jsonPrimitive.content).isEqualTo("0.0")
        assertThat(body["max_completion_tokens"]!!.jsonPrimitive.content).isEqualTo("4096")
        assertThat(body["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            .isEqualTo("json_schema")

        assertThat(outcome.response.text).isEqualTo("""{"facts":[]}""")
        assertThat(usage.entries).hasSize(1)
        assertThat(usage.entries.single().taskType).isEqualTo(AiTask.JSON_REPAIR)
        assertThat(logs.logs).hasSize(1)
        assertThat(logs.logs.single().repairTriggered).isTrue()
    }

    @Test
    fun `repair clamps token budget to the 1024 floor`() = runTest {
        val profile = testProfile(type = ProviderType.OPENAI, model = "gpt-test")
        var capturedBody: String? = null
        val (service, _, _) = buildService(profile) { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = chatEnvelope("""{"facts":[]}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        service.repair(
            profile = profile,
            canonicalSchema = canonicalSchema,
            malformed = "{",
            maxOutputTokens = 64,
        )

        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        assertThat(body["max_completion_tokens"]!!.jsonPrimitive.content).isEqualTo("1024")
    }

    @Test
    fun `repair honours a larger token budget`() = runTest {
        val profile = testProfile(type = ProviderType.OPENAI, model = "gpt-test")
        var capturedBody: String? = null
        val (service, _, _) = buildService(profile) { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = chatEnvelope("""{"facts":[]}"""),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        service.repair(
            profile = profile,
            canonicalSchema = canonicalSchema,
            malformed = "{",
            maxOutputTokens = 8192,
        )

        val body = json.parseToJsonElement(capturedBody!!).jsonObject
        assertThat(body["max_completion_tokens"]!!.jsonPrimitive.content).isEqualTo("8192")
    }
}

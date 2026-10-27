package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.ProviderType
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class StructuredAiServiceTest {

    private fun validFactJson(text: String = "Entropy of an isolated system never decreases over time.") = """
        {
          "facts": [{
            "text": "$text",
            "latex": null,
            "requiresLatex": false,
            "templateType": "TEXT",
            "diagramType": "NONE",
            "diagramSource": null,
            "sourceLessonTitle": "Thermodynamics",
            "sourcePage": 1,
            "tags": ["entropy", "thermodynamics"],
            "confidence": 0.9
          }]
        }
    """.trimIndent()

    private fun chatEnvelope(content: String, finishReason: String = "stop"): String {
        val quoted = kotlinx.serialization.json.JsonPrimitive(content).toString()
        return """
            {
              "model": "gpt-test",
              "choices": [{ "message": { "content": $quoted }, "finish_reason": "$finishReason" }],
              "usage": { "prompt_tokens": 100, "completion_tokens": 50 }
            }
        """.trimIndent()
    }

    private fun buildService(
        profile: com.factswallpaper.core.model.ProviderProfile,
        handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): Triple<StructuredAiService, FakeUsageRecorder, FakeLogRecorder> {
        val usage = FakeUsageRecorder()
        val logs = FakeLogRecorder()
        val callService = AiCallService(
            profileProvider = FakeProfileProvider(listOf(profile)),
            apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test")),
            preferences = FakePreferences(),
            clientFactory = AiClientFactory(mockHttpClient(handler)),
            retryPolicy = AiRetryPolicy(RetrySleeper { }),
            redactor = com.factswallpaper.core.security.LogRedactor(),
            usageRecorder = usage,
            logRecorder = logs,
            networkMonitor = FakeNetworkMonitor(),
        )
        return Triple(
            StructuredAiService(
                callService = callService,
                validator = SchemaValidator(),
                repairService = JsonRepairService(callService),
            ),
            usage,
            logs,
        )
    }

    @Test
    fun `facts pipeline repairs malformed json once and records both calls`() = runTest {
        var calls = 0
        var repairBody = ""
        val profile = testProfile(
            type = ProviderType.OPENAI,
            model = "gpt-test",
            context = 100_000,
            inputPrice = 1.0,
            outputPrice = 2.0,
        )
        val (service, usage, _) = buildService(profile) { request ->
            calls++
            val body = request.body.toByteArray().decodeToString()
            if (body.contains("deterministic JSON repair engine")) {
                repairBody = body
                respond(
                    content = chatEnvelope(validFactJson()),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                respond(
                    content = chatEnvelope("""{"facts":[{"text":"broken"""),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }

        val result = service.generateFacts(
            documentId = "doc-1",
            lessonTitle = "Thermodynamics",
            pages = listOf(PageText(1, "Entropy never decreases in an isolated system.")),
            documentPageCount = 10,
            density = FactDensity.BALANCED,
            includeFormulas = true,
            includeDiagrams = true,
        )

        assertThat(calls).isEqualTo(2)
        assertThat(result.repairUsed).isTrue()
        assertThat(result.facts).hasSize(1)
        assertThat(result.facts.first().tags).containsExactly("entropy", "thermodynamics").inOrder()
        assertThat(repairBody).contains("Target JSON Schema")
        assertThat(repairBody).contains("Malformed response")
        assertThat(usage.entries).hasSize(2)
        assertThat(usage.entries.map { it.taskType }).containsExactly(
            AiTask.FACTS,
            AiTask.JSON_REPAIR,
        ).inOrder()
        assertThat(usage.entries.first().documentId).isEqualTo("doc-1")
        assertThat(result.actualCostUsd).isAtLeast(0.0)
        assertThat(result.estimatedCostUsd).isAtLeast(0.0)
    }

    @Test
    fun `truncated length response retries once with doubled output budget before repair`() = runTest {
        var calls = 0
        val budgets = mutableListOf<String>()
        val profile = testProfile(context = 100_000)
        val (service, usage, _) = buildService(profile) { request ->
            calls++
            val body = kotlinx.serialization.json.Json.parseToJsonElement(
                request.body.toByteArray().decodeToString(),
            ).jsonObject
            budgets += body["max_completion_tokens"]!!.jsonPrimitive.content
            val response = if (calls == 1) {
                chatEnvelope("""{"facts":[{"text":"truncated""", finishReason = "length")
            } else {
                chatEnvelope(validFactJson())
            }
            respond(
                content = response,
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = service.generateFacts(
            documentId = "doc-1",
            lessonTitle = "Thermodynamics",
            pages = listOf(PageText(1, "Entropy never decreases in an isolated system.")),
            documentPageCount = 10,
            density = FactDensity.BALANCED,
            includeFormulas = true,
            includeDiagrams = true,
        )

        assertThat(calls).isEqualTo(2)
        assertThat(budgets).containsExactly("4096", "8192").inOrder()
        assertThat(result.repairUsed).isFalse()
        assertThat(result.facts).hasSize(1)
        assertThat(usage.entries).hasSize(2)
    }

    @Test
    fun `facts pipeline deduplicates against existing facts`() = runTest {
        val profile = testProfile(context = 100_000)
        val (service, _, _) = buildService(profile) {
            respond(
                content = chatEnvelope(validFactJson()),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = service.generateFacts(
            documentId = "doc-1",
            lessonTitle = "Thermodynamics",
            pages = listOf(PageText(1, "Entropy never decreases.")),
            documentPageCount = 10,
            density = FactDensity.CONCISE,
            includeFormulas = false,
            includeDiagrams = false,
            existingFactTexts = listOf("Entropy of an isolated system never decreases over time."),
        )

        assertThat(result.facts).isEmpty()
    }

    @Test
    fun `outline pipeline merges overlapping lessons across chunks`() = runTest {
        var calls = 0
        val profile = testProfile(context = 10_000)
        val (service, _, _) = buildService(profile) {
            calls++
            val lessons = when (calls) {
                1 -> """{"lessons":[{"title":"Thermodynamics","summary":"First half.","startPage":1,"endPage":1}]}"""
                2 -> """{"lessons":[{"title":"Thermodynamics","summary":"Second half.","startPage":1,"endPage":2}]}"""
                else -> """{"lessons":[{"title":"Thermodynamics","summary":"First half. Second half.","startPage":1,"endPage":2}]}"""
            }
            respond(
                content = chatEnvelope(lessons),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val pages = listOf(
            PageText(1, "a".repeat(20_000)),
            PageText(2, "b".repeat(20_000)),
        )

        val result = service.generateOutline(
            documentId = "doc-1",
            pages = pages,
            pdfPageCount = 2,
        )

        assertThat(calls).isEqualTo(3)
        assertThat(result.lessons).hasSize(1)
        assertThat(result.lessons.first().endPage).isEqualTo(2)
        assertThat(result.lessons.first().summary).contains("First half.")
        assertThat(result.lessons.first().summary).contains("Second half.")
    }

    @Test
    fun `outline pipeline rejects an empty text document`() = runTest {
        val (service, _, _) = buildService(testProfile()) {
            respond("{}", HttpStatusCode.OK, jsonHeaders())
        }

        val error = runCatching {
            service.generateOutline(documentId = "doc-1", pages = emptyList())
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
        assertThat((error as AiException).kind).isEqualTo(AiErrorKind.INVALID_REQUEST)
    }

    @Test
    fun `offline execution fails before touching the network`() = runTest {
        var calls = 0
        val profile = testProfile()
        val usage = FakeUsageRecorder()
        val callService = AiCallService(
            profileProvider = FakeProfileProvider(listOf(profile)),
            apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test")),
            preferences = FakePreferences(),
            clientFactory = AiClientFactory(
                mockHttpClient {
                    calls++
                    respond("{}", HttpStatusCode.OK, jsonHeaders())
                },
            ),
            retryPolicy = AiRetryPolicy(RetrySleeper { }),
            redactor = com.factswallpaper.core.security.LogRedactor(),
            usageRecorder = usage,
            logRecorder = FakeLogRecorder(),
            networkMonitor = FakeNetworkMonitor(online = false),
        )
        val service = StructuredAiService(callService, SchemaValidator(), JsonRepairService(callService))

        val error = runCatching {
            service.generateFacts(
                documentId = "doc-1",
                lessonTitle = "Lesson",
                pages = listOf(PageText(1, "text")),
                documentPageCount = 1,
                density = FactDensity.CONCISE,
                includeFormulas = false,
                includeDiagrams = false,
            )
        }.exceptionOrNull()

        assertThat(calls).isEqualTo(0)
        assertThat(error).isInstanceOf(AiException::class.java)
        assertThat((error as AiException).kind).isEqualTo(AiErrorKind.NETWORK)
        assertThat(usage.entries).isEmpty()
    }
}

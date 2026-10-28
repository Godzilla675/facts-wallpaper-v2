package com.factswallpaper.core.ai

import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.security.LogRedactor
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class ProviderDiagnosticsServiceTest {

    @Test
    fun `connection test accepts a reasoning only response and requests 1024 output tokens`() = runTest {
        var requestBody = ""
        val profile = testProfile(type = ProviderType.OPENAI)
        val httpClient = mockHttpClient { request ->
            requestBody = request.body.toByteArray().decodeToString()
            respond(
                content = """
                    {
                      "choices": [{
                        "message": { "content": null, "reasoning_content": "Connected." },
                        "finish_reason": "length"
                      }],
                      "usage": { "prompt_tokens": 2, "completion_tokens": 20 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profileProvider = FakeProfileProvider(listOf(profile))
        val apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test"))
        val callService = AiCallService(
            profileProvider = profileProvider,
            apiKeyStore = apiKeyStore,
            preferences = FakePreferences(),
            clientFactory = AiClientFactory(httpClient),
            retryPolicy = AiRetryPolicy(RetrySleeper { }),
            redactor = LogRedactor(),
            usageRecorder = FakeUsageRecorder(),
            logRecorder = FakeLogRecorder(),
            networkMonitor = FakeNetworkMonitor(),
        )
        val diagnostics = DefaultProviderDiagnosticsService(
            callService = callService,
            profileProvider = profileProvider,
            apiKeyStore = apiKeyStore,
            httpClient = httpClient,
            json = Json,
        )

        val result = diagnostics.testConnection(profile)

        assertThat(result.success).isTrue()
        assertThat(result.keyStatus).isEqualTo(ProviderKeyStatus.VALID)
        val body = Json.parseToJsonElement(requestBody).jsonObject
        assertThat(body["max_completion_tokens"]!!.jsonPrimitive.content).isEqualTo("1024")
    }

    @Test
    fun `empty length response recommends increasing output budget or disabling thinking`() = runTest {
        val profile = testProfile(type = ProviderType.OPENAI)
        val httpClient = mockHttpClient {
            respond(
                content = """
                    {
                      "choices": [{
                        "message": { "content": null },
                        "finish_reason": "length"
                      }],
                      "usage": { "prompt_tokens": 2, "completion_tokens": 1024 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profileProvider = FakeProfileProvider(listOf(profile))
        val apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test"))
        val diagnostics = DefaultProviderDiagnosticsService(
            callService = AiCallService(
                profileProvider = profileProvider,
                apiKeyStore = apiKeyStore,
                preferences = FakePreferences(),
                clientFactory = AiClientFactory(httpClient),
                retryPolicy = AiRetryPolicy(RetrySleeper { }),
                redactor = LogRedactor(),
                usageRecorder = FakeUsageRecorder(),
                logRecorder = FakeLogRecorder(),
                networkMonitor = FakeNetworkMonitor(),
            ),
            profileProvider = profileProvider,
            apiKeyStore = apiKeyStore,
            httpClient = httpClient,
            json = Json,
        )

        val result = diagnostics.testConnection(profile)

    @Test
    fun `fetch models parses gemini catalog and prefers the key override`() = runTest {
        val profile = testProfile(
            type = ProviderType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com",
        )
        var requestedUrl = ""
        var requestKey = ""
        val httpClient = mockHttpClient { request ->
            requestedUrl = request.url.toString()
            requestKey = request.headers["x-goog-api-key"].orEmpty()
            respond(
                content = """
                    {
                      "models": [
                        { "name": "models/gemini-2.0-flash", "displayName": "Gemini 2.0 Flash" },
                        { "name": "models/text-embedding-004", "supportedGenerationMethods": ["embedContent"] },
                        { "name": "models/gemini-1.5-pro" }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profileProvider = FakeProfileProvider(listOf(profile))
        val apiKeyStore = FakeApiKeyStore()
        val diagnostics = DefaultProviderDiagnosticsService(
            callService = AiCallService(
                profileProvider = profileProvider,
                apiKeyStore = apiKeyStore,
                preferences = FakePreferences(),
                clientFactory = AiClientFactory(httpClient),
                retryPolicy = AiRetryPolicy(RetrySleeper { }),
                redactor = LogRedactor(),
                usageRecorder = FakeUsageRecorder(),
                logRecorder = FakeLogRecorder(),
                networkMonitor = FakeNetworkMonitor(),
            ),
            profileProvider = profileProvider,
            apiKeyStore = apiKeyStore,
            httpClient = httpClient,
            json = Json,
        )

        val result = diagnostics.fetchModels(profile, apiKeyOverride = "draft-key")

        assertThat(result.error).isNull()
        assertThat(result.models.map { it.id })
            .containsExactly("gemini-1.5-pro", "gemini-2.0-flash")
            .inOrder()
        assertThat(result.models.first { it.id == "gemini-2.0-flash" }.displayName)
            .isEqualTo("Gemini 2.0 Flash")
        assertThat(requestedUrl)
            .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200")
        assertThat(requestKey).isEqualTo("draft-key")
    }

    @Test
    fun `fetch models accepts openai compatible data wrappers and drops malformed entries`() = runTest {
        val profile = testProfile(type = ProviderType.OPENAI_COMPATIBLE)
        val httpClient = mockHttpClient {
            respond(
                content = """
                    {
                      "data": [
                        { "id": "meta/llama-3.1-70b" },
                        { "id": "models/openai/gpt-4o-mini", "display_name": "GPT-4o mini" },
                        { "name": "bare-name" },
                        { "unexpected": true }
                      ]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profileProvider = FakeProfileProvider(listOf(profile))
        val apiKeyStore = FakeApiKeyStore(mutableMapOf(profile.id to "sk-test"))
        val diagnostics = DefaultProviderDiagnosticsService(
            callService = AiCallService(
                profileProvider = profileProvider,
                apiKeyStore = apiKeyStore,
                preferences = FakePreferences(),
                clientFactory = AiClientFactory(httpClient),
                retryPolicy = AiRetryPolicy(RetrySleeper { }),
                redactor = LogRedactor(),
                usageRecorder = FakeUsageRecorder(),
                logRecorder = FakeLogRecorder(),
                networkMonitor = FakeNetworkMonitor(),
            ),
            profileProvider = profileProvider,
            apiKeyStore = apiKeyStore,
            httpClient = httpClient,
            json = Json,
        )

        val result = diagnostics.fetchModels(profile)

        assertThat(result.error).isNull()
        assertThat(result.models.map { it.id })
            .containsExactly("bare-name", "meta/llama-3.1-70b", "openai/gpt-4o-mini")
            .inOrder()
        assertThat(result.models.first { it.id == "openai/gpt-4o-mini" }.displayName)
            .isEqualTo("GPT-4o mini")
    }
}
}

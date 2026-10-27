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

        assertThat(result.success).isFalse()
        assertThat(result.message.lowercase()).contains("raise max output tokens")
        assertThat(result.message.lowercase()).contains("disable thinking")
    }
}

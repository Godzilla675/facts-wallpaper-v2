package com.factswallpaper.core.ai

import com.factswallpaper.core.ai.client.AnthropicClient
import com.factswallpaper.core.ai.client.AzureOpenAiClient
import com.factswallpaper.core.ai.client.GeminiClient
import com.factswallpaper.core.ai.client.OpenAiClient
import com.factswallpaper.core.ai.client.OpenAiCompatibleClient
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderType
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class WireFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun request(
        task: AiTask = AiTask.OUTLINE,
        pdf: ByteArray? = null,
        schema: String? = AiSchemas.OutlineJsonSchema,
    ) = AiRequest(
        task = task,
        systemPrompt = "system prompt",
        userPrompt = "user prompt",
        pdfBytes = pdf,
        pdfFileName = "study.pdf",
        jsonSchema = schema,
        temperature = 0.2,
        maxOutputTokens = 1024,
    )

    @Test
    fun `gemini native request uses v1beta generateContent and parses usage`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "candidates": [{
                        "content": { "parts": [{ "text": "{\"lessons\":[]}" }] },
                        "finishReason": "STOP"
                      }],
                      "usageMetadata": {
                        "promptTokenCount": 120,
                        "candidatesTokenCount": 30,
                        "thoughtsTokenCount": 5
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(
            type = ProviderType.GEMINI,
            baseUrl = "https://generativelanguage.googleapis.com",
            model = "gemini-3.8-flash",
        )

        val response = GeminiClient(profile, "test-key", client).execute(request())

        assertThat(captured!!.url.toString())
            .isEqualTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent")
        assertThat(captured!!.headers["x-goog-api-key"]).isEqualTo("test-key")
        val body = json.parseToJsonElement(captured!!.body.toByteArray().decodeToString()).jsonObject
        assertThat(body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
            .isEqualTo("system prompt")
        val config = body["generationConfig"]!!.jsonObject
        assertThat(config["responseMimeType"]!!.jsonPrimitive.content).isEqualTo("application/json")
        val schema = config["responseSchema"]!!.jsonObject
        assertThat(schema.toString()).doesNotContain("additionalProperties")
        val lesson = schema["properties"]!!.jsonObject["lessons"]!!.jsonObject["items"]!!.jsonObject
        assertThat(lesson["required"]!!.jsonArray.map { it.jsonPrimitive.content })
            .containsExactly("title", "summary", "startPage", "endPage")
        assertThat(response.inputTokens).isEqualTo(120)
        assertThat(response.outputTokens).isEqualTo(35)
    }

    @Test
    fun `gemini inline PDF uses inline_data part`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """{"candidates":[{"content":{"parts":[{"text":"{}"}]},"finishReason":"STOP"}]}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(type = ProviderType.GEMINI)
        GeminiClient(profile, "test-key", client).execute(request(pdf = byteArrayOf(1, 2, 3)))

        val body = json.parseToJsonElement(captured!!.body.toByteArray().decodeToString()).jsonObject
        val part = body["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray[0].jsonObject
        assertThat(part["inline_data"]!!.jsonObject["mime_type"]!!.jsonPrimitive.content)
            .isEqualTo("application/pdf")
        assertThat(part["inline_data"]!!.jsonObject["data"]!!.jsonPrimitive.content).isEqualTo("AQID")
    }

    @Test
    fun `openai chat uses strict json schema and max completion tokens`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "model": "gpt-6.1-sol",
                      "choices": [{ "message": { "content": "{\"lessons\":[]}" }, "finish_reason": "stop" }],
                      "usage": { "prompt_tokens": 100, "completion_tokens": 20 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(type = ProviderType.OPENAI, model = "gpt-6.1-sol")

        val response = OpenAiClient(profile, "sk-test", client).execute(request())

        assertThat(captured!!.url.toString()).isEqualTo("https://api.example.com/v1/chat/completions")
        assertThat(captured!!.headers["Authorization"]).isEqualTo("Bearer sk-test")
        val body = json.parseToJsonElement(captured!!.body.toByteArray().decodeToString()).jsonObject
        assertThat(body["max_completion_tokens"]!!.jsonPrimitive.content).isEqualTo("1024")
        assertThat(body["messages"]!!.jsonArray).hasSize(2)
        val format = body["response_format"]!!.jsonObject
        assertThat(format["type"]!!.jsonPrimitive.content).isEqualTo("json_schema")
        val schema = format["json_schema"]!!.jsonObject
        assertThat(schema["strict"]!!.jsonPrimitive.content).isEqualTo("true")
        assertThat(schema["schema"].toString()).doesNotContain("minLength")
        assertThat(response.inputTokens).isEqualTo(100)
        assertThat(response.outputTokens).isEqualTo(20)
    }

    @Test
    fun `chat response removes think blocks before returning structured content`() = runTest {
        val client = mockHttpClient {
            respond(
                content = """
                    {
                      "choices": [{
                        "message": {
                          "content": "<think>private reasoning</think>\n{\"ok\":true}",
                          "reasoning": "separate reasoning"
                        },
                        "finish_reason": "stop"
                      }]
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val response = OpenAiClient(
            testProfile(type = ProviderType.OPENAI),
            "sk-test",
            client,
        ).execute(request(schema = null))

        assertThat(response.text).isEqualTo("""{"ok":true}""")
        assertThat(response.finishReason).isEqualTo("stop")
    }

    @Test
    fun `openai responses path is used for direct PDF`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "model": "gpt-6.1-sol",
                      "status": "completed",
                      "output_text": "{\"lessons\":[]}",
                      "usage": { "input_tokens": 11, "output_tokens": 7 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(type = ProviderType.OPENAI, model = "gpt-6.1-sol")

        val response = OpenAiClient(profile, "sk-test", client).execute(request(pdf = byteArrayOf(1)))

        assertThat(captured!!.url.toString()).isEqualTo("https://api.example.com/v1/responses")
        val body = json.parseToJsonElement(captured!!.body.toByteArray().decodeToString()).jsonObject
        val userContent = body["input"]!!.jsonArray[1].jsonObject["content"]!!.jsonArray
        assertThat(userContent[0].jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("input_file")
        assertThat(userContent[0].jsonObject["file_data"]!!.jsonPrimitive.content)
            .startsWith("data:application/pdf;base64,")
        assertThat(response.inputTokens).isEqualTo(11)
        assertThat(response.outputTokens).isEqualTo(7)
    }

    @Test
    fun `chat response with reasoning only remains available to connection diagnostics`() = runTest {
        val client = mockHttpClient {
            respond(
                content = """
                    {
                      "choices": [{
                        "message": { "content": null, "reasoning_content": "Model is thinking." },
                        "finish_reason": "length"
                      }],
                      "usage": {
                        "prompt_tokens": 2,
                        "completion_tokens": 20,
                        "completion_tokens_details": { "reasoning_tokens": 30 }
                      }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val response = OpenAiClient(
            testProfile(type = ProviderType.OPENAI),
            "sk-test",
            client,
        ).execute(request(schema = null))

        assertThat(response.text).isEmpty()
        assertThat(response.finishReason).isEqualTo("length")
        assertThat(response.outputTokens).isEqualTo(30)
    }

    @Test
    fun `anthropic uses forced emit_result tool and reads tool_use input`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "model": "claude-sonnet-5-5",
                      "stop_reason": "tool_use",
                      "content": [
                        { "type": "tool_use", "name": "emit_result", "input": { "lessons": [] } }
                      ],
                      "usage": { "input_tokens": 10, "output_tokens": 5 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(
            type = ProviderType.ANTHROPIC,
            baseUrl = "https://api.anthropic.com/v1",
            model = "claude-sonnet-5-5",
        )

        val response = AnthropicClient(profile, "sk-ant-test", client).execute(request())

        assertThat(captured!!.url.toString()).isEqualTo("https://api.anthropic.com/v1/messages")
        assertThat(captured!!.headers["x-api-key"]).isEqualTo("sk-ant-test")
        assertThat(captured!!.headers["anthropic-version"]).isEqualTo("2023-06-01")
        val body = json.parseToJsonElement(captured!!.body.toByteArray().decodeToString()).jsonObject
        assertThat(body["tool_choice"]!!.jsonObject["type"]!!.jsonPrimitive.content).isEqualTo("tool")
        assertThat(body["tool_choice"]!!.jsonObject["name"]!!.jsonPrimitive.content).isEqualTo("emit_result")
        assertThat(body["tools"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)
            .isEqualTo("emit_result")
        assertThat(response.text).isEqualTo("""{"lessons":[]}""")
        assertThat(response.inputTokens).isEqualTo(10)
    }

    @Test
    fun `azure uses deployment path and api-key header`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "model": "gpt-6.1-sol",
                      "choices": [{ "message": { "content": "{}" }, "finish_reason": "stop" }],
                      "usage": { "prompt_tokens": 3, "completion_tokens": 4 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(type = ProviderType.AZURE_OPENAI).copy(
            baseUrl = "https://resource.openai.azure.com",
            deploymentName = "my-deployment",
            apiVersion = "2025-01-01-preview",
        )

        AzureOpenAiClient(profile, "azure-key", client).execute(request())

        assertThat(captured!!.url.toString()).isEqualTo(
            "https://resource.openai.azure.com/openai/deployments/my-deployment/chat/completions?api-version=2025-01-01-preview",
        )
        assertThat(captured!!.headers["api-key"]).isEqualTo("azure-key")
    }

    @Test
    fun `openai compatible sends bearer plus custom headers`() = runTest {
        var captured: HttpRequestData? = null
        val client = mockHttpClient { data ->
            captured = data
            respond(
                content = """
                    {
                      "model": "local-model",
                      "choices": [{ "message": { "content": "{}" }, "finish_reason": "stop" }],
                      "usage": { "prompt_tokens": 1, "completion_tokens": 2 }
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val profile = testProfile(type = ProviderType.OPENAI_COMPATIBLE).copy(
            customHeadersJson = """{"X-Custom":"custom-value"}""",
        )

        OpenAiCompatibleClient(profile, "local-key", client).execute(request())

        assertThat(captured!!.headers["Authorization"]).isEqualTo("Bearer local-key")
        assertThat(captured!!.headers["X-Custom"]).isEqualTo("custom-value")
    }

    @Test
    fun `temperature rejection retries once without temperature and persists capability`() = runTest {
        var calls = 0
        var capturedSecond: HttpRequestData? = null
        val client = mockHttpClient { data ->
            calls++
            if (calls == 1) {
                respond(
                    content = """{"error":{"message":"temperature is not supported for this model"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders(),
                )
            } else {
                capturedSecond = data
                respond(
                    content = """
                        {
                          "choices": [{ "message": { "content": "{}" }, "finish_reason": "stop" }],
                          "usage": { "prompt_tokens": 1, "completion_tokens": 1 }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        var downgraded = false
        val profile = testProfile(type = ProviderType.OPENAI)

        OpenAiClient(
            profile = profile,
            apiKey = "sk-test",
            httpClient = client,
            onTemperatureUnsupported = { downgraded = true },
        ).execute(request())

        assertThat(calls).isEqualTo(2)
        assertThat(downgraded).isTrue()
        val body = json.parseToJsonElement(capturedSecond!!.body.toByteArray().decodeToString()).jsonObject
        assertThat(body.containsKey("temperature")).isFalse()
    }

    @Test
    fun `openai schema rejection retries without response format`() = runTest {
        var calls = 0
        var capturedSecond: HttpRequestData? = null
        val client = mockHttpClient { data ->
            calls++
            if (calls == 1) {
                respond(
                    content = """{"error":{"message":"response_format json_schema is not supported"}}""",
                    status = HttpStatusCode.BadRequest,
                    headers = jsonHeaders(),
                )
            } else {
                capturedSecond = data
                respond(
                    content = """
                        {
                          "choices": [{ "message": { "content": "{}" }, "finish_reason": "stop" }],
                          "usage": { "prompt_tokens": 1, "completion_tokens": 1 }
                        }
                    """.trimIndent(),
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            }
        }
        var downgraded = false
        val profile = testProfile(type = ProviderType.OPENAI)

        OpenAiClient(
            profile = profile,
            apiKey = "sk-test",
            httpClient = client,
            onResponseFormatUnsupported = { downgraded = true },
        ).execute(request())

        assertThat(calls).isEqualTo(2)
        assertThat(downgraded).isTrue()
        val body = json.parseToJsonElement(capturedSecond!!.body.toByteArray().decodeToString()).jsonObject
        assertThat(body.containsKey("response_format")).isFalse()
        assertThat(body["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content)
            .contains("Respond ONLY with a single JSON object")
    }

    @Test
    fun `http errors map to the documented taxonomy`() {
        assertThat(AiException.fromHttp(401, "").kind).isEqualTo(AiErrorKind.AUTHENTICATION)
        assertThat(AiException.fromHttp(404, "").kind).isEqualTo(AiErrorKind.NOT_FOUND)
        assertThat(AiException.fromHttp(429, "").kind).isEqualTo(AiErrorKind.RATE_LIMITED)
        assertThat(AiException.fromHttp(503, "").kind).isEqualTo(AiErrorKind.SERVER)
        assertThat(AiException.fromHttp(400, "").kind).isEqualTo(AiErrorKind.INVALID_REQUEST)
        assertThat(AiException.fromHttp(429, "", retryAfterSeconds = 7).retryAfterSeconds)
            .isEqualTo(7)
    }
}

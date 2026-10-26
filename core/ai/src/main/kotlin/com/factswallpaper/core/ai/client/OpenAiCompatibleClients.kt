package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.model.ProviderProfile
import io.ktor.client.HttpClient
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Azure OpenAI resource + deployment + api-version client (spec §6.3.4). */
class AzureOpenAiClient(
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
        val deployment = profile.deploymentName?.takeIf { it.isNotBlank() }
            ?: throw AiException(AiErrorKind.INVALID_REQUEST, "Azure deployment name is missing.")
        val apiVersion = profile.apiVersion?.takeIf { it.isNotBlank() } ?: "2025-01-01-preview"
        val url = "${profile.baseUrl.normalizedBaseUrl()}/openai/deployments/$deployment" +
            "/chat/completions?api-version=$apiVersion"
        val headers = mapOf(
            "api-key" to apiKey,
            "Content-Type" to "application/json",
        )
        var omitTemperature = !shouldSendTemperature(request)
        var includeFormat = request.jsonSchema != null && profile.supportsJsonSchema
        val schema = ChatCompletionsSupport.schemaFor(profile, request)

        suspend fun call(): AiResponse {
            val body = ChatCompletionsSupport.buildBody(
                profile = profile,
                request = if (omitTemperature) request.copy(temperature = null) else request,
                includeResponseFormat = includeFormat,
                schema = schema,
            )
            return ChatCompletionsSupport.parse(post(url, headers, body), profile)
        }

        return try {
            call()
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                ChatCompletionsSupport.isTemperatureRejection(exception.message)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call()
            } else if (exception.statusCode == 400 && includeFormat &&
                ChatCompletionsSupport.isSchemaRejection(exception.message)
            ) {
                includeFormat = false
                onResponseFormatUnsupported()
                call()
            } else {
                throw exception
            }
        }
    }
}

/** OpenRouter / DeepSeek / Qwen / custom OpenAI-compatible client (spec §6.3.5). */
class OpenAiCompatibleClient(
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
        val url = "${profile.baseUrl.normalizedBaseUrl()}/chat/completions"
        val headers = buildMap {
            put("Authorization", "Bearer $apiKey")
            put("Content-Type", "application/json")
            putAll(com.factswallpaper.core.ai.SchemaAdapter.customHeaders(profile.customHeadersJson))
        }
        var omitTemperature = !shouldSendTemperature(request)
        var includeFormat = request.jsonSchema != null &&
            (profile.supportsJsonSchema || profile.supportsJsonMode)
        val schema = ChatCompletionsSupport.schemaFor(profile, request)

        suspend fun call(): AiResponse {
            val body = ChatCompletionsSupport.buildBody(
                profile = profile,
                request = if (omitTemperature) request.copy(temperature = null) else request,
                includeResponseFormat = includeFormat,
                schema = schema,
            )
            return ChatCompletionsSupport.parse(post(url, headers, body), profile)
        }

        return try {
            call()
        } catch (exception: AiException) {
            if (exception.statusCode == 400 && !omitTemperature &&
                ChatCompletionsSupport.isTemperatureRejection(exception.message)
            ) {
                omitTemperature = true
                onTemperatureUnsupported()
                call()
            } else if (exception.statusCode == 400 && includeFormat &&
                ChatCompletionsSupport.isSchemaRejection(exception.message)
            ) {
                includeFormat = false
                onResponseFormatUnsupported()
                call()
            } else {
                throw exception
            }
        }
    }
}

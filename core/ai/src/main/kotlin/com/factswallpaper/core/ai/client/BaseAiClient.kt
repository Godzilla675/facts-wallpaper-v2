package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiClient
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.model.ProviderProfile
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json

abstract class BaseAiClient(
    protected val profile: ProviderProfile,
    protected val apiKey: String,
    protected val httpClient: HttpClient,
    protected val json: Json = ProviderJson,
    protected val onTemperatureUnsupported: suspend () -> Unit = {},
    protected val onResponseFormatUnsupported: suspend () -> Unit = {},
) : AiClient {

    protected suspend fun post(
        url: String,
        headers: Map<String, String>,
        body: String,
    ): String = httpClient.postJson(url, headers, body)

    protected fun shouldSendTemperature(request: AiRequest): Boolean =
        request.temperature != null && profile.supportsTemperature

    protected fun requireText(text: String?, finishReason: String?): String {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            throw AiException(
                kind = com.factswallpaper.core.ai.AiErrorKind.CONTENT,
                message = "Provider returned no text (finishReason=$finishReason).",
            )
        }
        return trimmed
    }
}

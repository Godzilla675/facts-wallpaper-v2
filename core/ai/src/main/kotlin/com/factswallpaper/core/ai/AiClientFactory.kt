package com.factswallpaper.core.ai

import com.factswallpaper.core.ai.client.AnthropicClient
import com.factswallpaper.core.ai.client.AzureOpenAiClient
import com.factswallpaper.core.ai.client.GeminiClient
import com.factswallpaper.core.ai.client.OpenAiClient
import com.factswallpaper.core.ai.client.OpenAiCompatibleClient
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiClientFactory @Inject constructor(
    private val httpClient: HttpClient,
) {

    fun create(
        profile: ProviderProfile,
        apiKey: String,
        onTemperatureUnsupported: suspend () -> Unit = {},
        onResponseFormatUnsupported: suspend () -> Unit = {},
    ): AiClient = when (profile.providerType) {
        ProviderType.GEMINI -> GeminiClient(
            profile = profile,
            apiKey = apiKey,
            httpClient = httpClient,
            onTemperatureUnsupported = onTemperatureUnsupported,
            onResponseFormatUnsupported = onResponseFormatUnsupported,
        )
        ProviderType.OPENAI -> OpenAiClient(
            profile = profile,
            apiKey = apiKey,
            httpClient = httpClient,
            onTemperatureUnsupported = onTemperatureUnsupported,
            onResponseFormatUnsupported = onResponseFormatUnsupported,
        )
        ProviderType.ANTHROPIC -> AnthropicClient(
            profile = profile,
            apiKey = apiKey,
            httpClient = httpClient,
            onTemperatureUnsupported = onTemperatureUnsupported,
            onResponseFormatUnsupported = onResponseFormatUnsupported,
        )
        ProviderType.AZURE_OPENAI -> AzureOpenAiClient(
            profile = profile,
            apiKey = apiKey,
            httpClient = httpClient,
            onTemperatureUnsupported = onTemperatureUnsupported,
            onResponseFormatUnsupported = onResponseFormatUnsupported,
        )
        ProviderType.OPENROUTER, ProviderType.DEEPSEEK, ProviderType.QWEN,
        ProviderType.OPENAI_COMPATIBLE -> OpenAiCompatibleClient(
            profile = profile,
            apiKey = apiKey,
            httpClient = httpClient,
            onTemperatureUnsupported = onTemperatureUnsupported,
            onResponseFormatUnsupported = onResponseFormatUnsupported,
        )
    }
}

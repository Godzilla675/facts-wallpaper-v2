package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiClient
import com.factswallpaper.core.ai.AiRequest
import com.factswallpaper.core.ai.AiResponse
import com.factswallpaper.core.model.ProviderProfile
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

}

internal data class ParsedProviderText(
    val content: String,
    val reasoning: String,
)

internal object ProviderReasoningParser {
    private val thinkTag = Regex("</?think\\b[^>]*>", RegexOption.IGNORE_CASE)

    fun parse(
        content: String?,
        reasoningFields: List<String?> = emptyList(),
    ): ParsedProviderText {
        val source = content.orEmpty()
        val visible = StringBuilder()
        val hidden = StringBuilder()
        val extractedReasoning = mutableListOf<String>()
        var depth = 0
        var cursor = 0

        thinkTag.findAll(source).forEach { match ->
            val tagStart = match.range.first
            if (depth == 0) {
                visible.append(source, cursor, tagStart)
            } else {
                hidden.append(source, cursor, tagStart)
            }

            if (match.value.startsWith("</")) {
                if (depth > 0) {
                    depth--
                    if (depth == 0) {
                        extractedReasoning += hidden.toString()
                        hidden.setLength(0)
                    }
                }
            } else {
                depth++
            }
            cursor = match.range.last + 1
        }

        if (depth == 0) {
            visible.append(source, cursor, source.length)
        } else {
            hidden.append(source, cursor, source.length)
            extractedReasoning += hidden.toString()
        }

        val reasoning = (reasoningFields + extractedReasoning)
            .mapNotNull { it?.trim()?.takeIf { text -> text.isNotEmpty() } }
            .distinct()
            .joinToString("\n")
        return ParsedProviderText(
            content = visible.toString().trim(),
            reasoning = reasoning,
        )
    }

    fun jsonText(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.contentOrNull
        is JsonArray -> element.mapNotNull(::jsonText)
            .joinToString("\n")
            .takeIf { it.isNotBlank() }
        is JsonObject -> element["text"]?.let(::jsonText)
        else -> null
    }
}

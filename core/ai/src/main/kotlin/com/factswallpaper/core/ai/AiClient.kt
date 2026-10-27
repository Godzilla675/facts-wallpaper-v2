package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import kotlinx.serialization.Serializable

/** Universal provider-agnostic AI client contract (spec §6.1). */
interface AiClient {
    suspend fun execute(request: AiRequest): AiResponse
}

data class AiRequest(
    val task: AiTask,
    val systemPrompt: String,
    val userPrompt: String,
    val pdfBytes: ByteArray? = null,
    val pdfFileName: String? = null,
    val imageBytes: ByteArray? = null,
    val imageMimeType: String? = null,
    val images: List<ByteArray> = emptyList(),
    val jsonSchema: String? = null,
    val temperature: Double? = 0.2,
    val maxOutputTokens: Int = 8_192,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AiRequest) return false
        return task == other.task &&
            systemPrompt == other.systemPrompt &&
            userPrompt == other.userPrompt &&
            pdfBytes.contentEquals(other.pdfBytes) &&
            pdfFileName == other.pdfFileName &&
            imageBytes.contentEquals(other.imageBytes) &&
            imageMimeType == other.imageMimeType &&
            images.size == other.images.size &&
            images.indices.all { images[it].contentEquals(other.images[it]) } &&
            jsonSchema == other.jsonSchema &&
            temperature == other.temperature &&
            maxOutputTokens == other.maxOutputTokens
    }

    override fun hashCode(): Int {
        var result = task.hashCode()
        result = 31 * result + systemPrompt.hashCode()
        result = 31 * result + userPrompt.hashCode()
        result = 31 * result + (pdfBytes?.contentHashCode() ?: 0)
        result = 31 * result + (pdfFileName?.hashCode() ?: 0)
        result = 31 * result + (imageBytes?.contentHashCode() ?: 0)
        result = 31 * result + (imageMimeType?.hashCode() ?: 0)
        result = 31 * result + images.fold(0) { acc, arr -> 31 * acc + arr.contentHashCode() }
        result = 31 * result + (jsonSchema?.hashCode() ?: 0)
        result = 31 * result + (temperature?.hashCode() ?: 0)
        result = 31 * result + maxOutputTokens
        return result
    }
}

@Serializable
data class AiResponse(
    val text: String,
    val model: String,
    val inputTokens: Int = 0,
    val outputTokens: Int = 0,
    val finishReason: String? = null,
    val rawUsageJson: String? = null,
    val reasoningText: String = "",
)

internal const val MIN_OUTPUT_TOKENS = 1_024
internal const val MAX_OUTPUT_TOKENS = 1_000_000

internal fun ProviderProfile.outputTokenLimit(): Int =
    maxOutputTokens.coerceIn(MIN_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS)

internal fun ProviderProfile.initialOutputTokenBudget(): Int =
    (outputTokenLimit() / 2).coerceAtLeast(MIN_OUTPUT_TOKENS)

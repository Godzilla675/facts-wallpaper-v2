package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
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
    val jsonSchema: String? = null,
    val temperature: Double? = 0.2,
    val maxOutputTokens: Int = 4096,
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
)

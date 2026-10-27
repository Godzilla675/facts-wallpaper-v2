package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

/**
 * Domain representation of an imported PDF. Room entities are internal to
 * `:core:database`; UI and feature modules consume this model instead.
 */
@Serializable
data class Document(
    val id: String,
    val title: String,
    val sourceUri: String,
    val localFilePath: String,
    val compiledOcrTextPath: String? = null,
    val fileHash: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val pagesWithText: Int,
    val processingRoute: PdfProcessingRoute,
    val ocrEngine: OcrEngineType? = null,
    val ocrLanguageCode: String = "eng",
    val ocrLastStartedPage: Int = 0,
    val ocrLastCompletedPage: Int = 0,
    val extractionStatus: ExtractionStatus,
    val lastErrorMessage: String? = null,
    val includedInRotation: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val lessonCount: Int = 0,
    val factCount: Int = 0,
    val pipelineProgress: Float = 0f,
) {
    val scannedPageCount: Int get() = (pageCount - pagesWithText).coerceAtLeast(0)
    val isFullyTextExtractable: Boolean get() = pageCount > 0 && pagesWithText >= pageCount
}

@Serializable
data class DocumentSettings(
    val documentId: String,
    val pageRangeStart: Int = 1,
    val pageRangeEnd: Int,
    val forceFullLocalOcr: Boolean = false,
    val preferDirectPdfUpload: Boolean = false,
    val overrideOcrPageLimit: Boolean = false,
    val ocrEngine: OcrEngineType = OcrEngineType.ML_KIT,
    val ocrLanguageCode: String = "eng",
    val ocrExecutionMode: OcrExecutionMode = OcrExecutionMode.BACKGROUND,
    val factDensity: FactDensity = FactDensity.BALANCED,
    val includeFormulas: Boolean = true,
    val includeDiagrams: Boolean = true,
    val autoVerifyFormulas: Boolean = true,
    val preferredProviderProfileId: String? = null,
    val autoProcessMode: AutoProcessMode = AutoProcessMode.OUTLINE_ONLY,
    val documentInputMode: DocumentInputMode = DocumentInputMode.AUTO,
)

@Serializable
data class DocumentChunk(
    val documentId: String,
    val pageNumber: Int,
    val extractedText: String,
    val status: String = "COMPLETED",
    val isFromOcr: Boolean,
    val ocrConfidence: Float? = null,
    val updatedAtEpochMs: Long,
)

package com.factswallpaper.core.pdf

import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.OcrExecutionMode
import com.factswallpaper.core.model.PdfProcessingRoute

/** Text characteristics of a single page after local extraction. */
data class PdfPageTextStat(
    val pageNumber: Int,
    val charCount: Int,
) {
    val hasExtractableText: Boolean get() = charCount >= PdfMetadata.MIN_EXTRACTABLE_TEXT_CHARS
}

/**
 * Result of local PDF inspection (spec F3). [pagesWithExtractableText] counts
 * text-layer pages (>= 40 non-whitespace characters), exactly as specified.
 */
data class PdfMetadata(
    val title: String,
    val embeddedTitle: String?,
    val sourceUri: String,
    val localFilePath: String,
    val fileHash: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val pageTextStats: List<PdfPageTextStat>,
) {
    val pagesWithExtractableText: Int get() = pageTextStats.count { it.hasExtractableText }

    fun pagesWithExtractableTextIn(range: PdfPageRange): Int =
        pageTextStats.count { it.pageNumber in range.startPage..range.endPage && it.hasExtractableText }

    fun charCountIn(range: PdfPageRange): Int =
        pageTextStats
            .filter { it.pageNumber in range.startPage..range.endPage }
            .sumOf { it.charCount }

    fun hasTextOnPage(pageNumber: Int): Boolean =
        pageTextStats.firstOrNull { it.pageNumber == pageNumber }?.hasExtractableText == true

    val isFullyTextExtractable: Boolean
        get() = pageCount > 0 && pagesWithExtractableText >= pageCount

    companion object {
        const val MIN_EXTRACTABLE_TEXT_CHARS = 40
    }
}

/** A page of extracted text, always carrying its one-based source page number. */
data class PdfPageText(
    val pageNumber: Int,
    val text: String,
) {
    val charCount: Int get() = text.trim().length
}

/** Capabilities of the currently selected provider that influence PDF routing. */
data class PdfProviderCapabilities(
    val supportsDirectPdfInput: Boolean,
    val maxInlinePdfBytes: Long = DEFAULT_MAX_INLINE_PDF_BYTES,
    val maxDirectPdfPages: Int = DEFAULT_MAX_DIRECT_PDF_PAGES,
) {
    companion object {
        /** Spec 11.4 defaults; provider-specific overrides land with the AI phase. */
        const val DEFAULT_MAX_INLINE_PDF_BYTES: Long = 20L * 1024L * 1024L
        const val DEFAULT_MAX_DIRECT_PDF_PAGES: Int = 100
    }
}

enum class DirectPdfBlockReason {
    PROVIDER_UNSUPPORTED,
    FILE_TOO_LARGE,
    TOO_MANY_PAGES,
}

/** Deterministic classification result shown before processing starts (F3). */
data class PdfRouteDecision(
    val route: PdfProcessingRoute,
    val pageRange: PdfPageRange,
    val filteredPageCount: Int,
    val textPagesInRange: Int,
    val scannedPagesInRange: Int,
    val ocrPageLimit: Int,
    val directPdfEligible: Boolean,
    val directPdfBlockReason: DirectPdfBlockReason?,
    val estimatedDirectPdfBytes: Long,
) {
    val requiresOcrOverride: Boolean
        get() = route == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE

    val usesOcr: Boolean
        get() = route == PdfProcessingRoute.LOCAL_OCR || route == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE
}

/** Token pricing used by the pre-processing cost preview. */
data class PdfPricing(
    val inputUsdPerMillion: Double,
    val outputUsdPerMillion: Double,
    val sourceLabel: String,
    val isBaseline: Boolean,
) {
    companion object {
        val BASELINE = PdfPricing(
            inputUsdPerMillion = 0.15,
            outputUsdPerMillion = 0.60,
            sourceLabel = "Default baseline",
            isBaseline = true,
        )
    }
}

/** Estimated LLM usage for a document/page range before any request is sent. */
data class PdfCostEstimate(
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val pricing: PdfPricing,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

/** User choices for one import; persisted as [DocumentSettings] at commit time. */
data class PdfProcessingOptions(
    val pageRange: PdfPageRange,
    val forceFullLocalOcr: Boolean = false,
    val preferDirectPdfUpload: Boolean = false,
    val overrideOcrPageLimit: Boolean = false,
    val ocrEngine: OcrEngineType = OcrEngineType.ML_KIT,
    val ocrLanguageCode: String = "eng",
    val ocrExecutionMode: OcrExecutionMode = OcrExecutionMode.BACKGROUND,
    val factDensity: FactDensity = FactDensity.BALANCED,
    val preferredProviderProfileId: String? = null,
)

/** How an already-imported document with the same SHA-256 should be handled (F2). */
enum class DuplicatePolicy {
    /** Stage the file and ask the user via [PdfImportResult.Duplicate]. */
    ASK,

    /** Throw away the staged copy and return the existing document. */
    OPEN_EXISTING,

    /** Re-analyse into the existing document id, preserving its generated facts. */
    REPLACE_EXISTING,

    /** Import the same bytes as a separate document. */
    IMPORT_AS_COPY,
}

enum class PdfImportErrorCode {
    UNREADABLE_SOURCE,
    NOT_A_PDF,
    ENCRYPTED_PDF,
    CORRUPT_PDF,
    HTTPS_REQUIRED,
    INVALID_URL,
    DOWNLOAD_FAILED,
    CONTENT_TYPE_REJECTED,
    FILE_TOO_LARGE,
    INSPECTION_FAILED,
    STORAGE_FAILED,
}

data class PdfImportError(
    val code: PdfImportErrorCode,
    val detail: String? = null,
    val cause: Throwable? = null,
)

/**
 * A PDF that has been copied to app-private staging storage and inspected, but
 * not yet committed to the library. Keeping the staged file makes duplicate
 * resolution and page-range changes instant and avoids re-copying the source.
 */
data class StagedPdf(
    val id: String,
    val filePath: String,
    val displayName: String?,
    val metadata: PdfMetadata,
    val duplicateOfDocumentId: String? = null,
    val duplicateOfTitle: String? = null,
) {
    val title: String get() = metadata.title
    val pageCount: Int get() = metadata.pageCount
}

sealed interface PdfImportResult {
    data class Ready(val staged: StagedPdf) : PdfImportResult

    data class Duplicate(
        val staged: StagedPdf,
        val existingDocumentId: String,
        val existingTitle: String,
    ) : PdfImportResult

    data class Existing(val documentId: String, val title: String) : PdfImportResult

    data class Failure(val error: PdfImportError) : PdfImportResult
}

/** A committed document plus the settings that were persisted with it. */
data class PdfCommittedDocument(
    val documentId: String,
    val title: String,
    val route: PdfProcessingRoute,
)

data class PdfExtractionResult(
    val pages: List<PdfPageText>,
    val range: PdfPageRange,
) {
    val totalCharacters: Int get() = pages.sumOf { it.charCount }
}

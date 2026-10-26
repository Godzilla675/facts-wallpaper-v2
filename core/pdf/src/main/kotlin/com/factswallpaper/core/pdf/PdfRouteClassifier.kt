package com.factswallpaper.core.pdf

import com.factswallpaper.core.model.PdfProcessingRoute
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, local-only processing route classifier (spec F3).
 *
 * The direct-PDF path is only offered when the selected provider accepts PDF
 * input and the page-range-filtered upload stays inside the spec 11.4 limits.
 * Everything else falls back to the text layer or checkpointed local OCR.
 */
@Singleton
class PdfRouteClassifier @Inject constructor() {

    fun classify(
        metadata: PdfMetadata,
        options: PdfProcessingOptions,
        capabilities: PdfProviderCapabilities,
        ocrPageLimit: Int,
    ): PdfRouteDecision {
        val range = options.pageRange.coerceTo(metadata.pageCount)
        val pageCount = metadata.pageCount.coerceAtLeast(1)
        val textPages = metadata.pagesWithExtractableTextIn(range)
        val scannedPages = range.pageCount - textPages

        // A sub-PDF contains only the selected pages, so its size is estimated
        // proportionally. A 1% container overhead is included conservatively.
        val estimatedDirectBytes = if (range.pageCount == pageCount) {
            metadata.fileSizeBytes
        } else {
            ((metadata.fileSizeBytes.toDouble() * range.pageCount / pageCount) * 1.01).toLong()
        }

        val blockReason = when {
            !capabilities.supportsDirectPdfInput -> DirectPdfBlockReason.PROVIDER_UNSUPPORTED
            estimatedDirectBytes > capabilities.maxInlinePdfBytes -> DirectPdfBlockReason.FILE_TOO_LARGE
            range.pageCount > capabilities.maxDirectPdfPages -> DirectPdfBlockReason.TOO_MANY_PAGES
            else -> null
        }
        val directEligible = blockReason == null

        val route = when {
            options.forceFullLocalOcr -> PdfProcessingRoute.LOCAL_OCR
            directEligible && options.preferDirectPdfUpload -> PdfProcessingRoute.DIRECT_PDF
            scannedPages <= 0 -> PdfProcessingRoute.EXTRACT_TEXT
            options.overrideOcrPageLimit -> PdfProcessingRoute.LOCAL_OCR
            scannedPages <= ocrPageLimit -> PdfProcessingRoute.LOCAL_OCR
            else -> PdfProcessingRoute.OCR_REQUIRES_OVERRIDE
        }

        return PdfRouteDecision(
            route = route,
            pageRange = range,
            filteredPageCount = range.pageCount,
            textPagesInRange = textPages,
            scannedPagesInRange = scannedPages,
            ocrPageLimit = ocrPageLimit,
            directPdfEligible = directEligible,
            directPdfBlockReason = blockReason,
            estimatedDirectPdfBytes = estimatedDirectBytes,
        )
    }
}

/**
 * Pre-flight token/cost preview. The heuristics are deliberately local,
 * deterministic and documented in `docs/DECISIONS.md`; they are estimates,
 * not a guarantee of provider billing.
 */
object PdfCostEstimator {

    private const val CHARS_PER_TOKEN = 4.0
    private const val PROMPT_OVERHEAD_TOKENS = 1_800
    private const val TEXT_OUTPUT_RATIO = 0.20
    private const val MIN_OUTPUT_TOKENS = 400
    private const val OCR_CHARS_PER_SCANNED_PAGE = 2_400
    private const val DIRECT_PDF_TOKENS_PER_PAGE = 900

    fun estimate(
        metadata: PdfMetadata,
        range: PdfPageRange,
        route: PdfProcessingRoute,
        pricing: PdfPricing = PdfPricing.BASELINE,
    ): PdfCostEstimate {
        val safeRange = range.coerceTo(metadata.pageCount)
        val textChars = metadata.charCountIn(safeRange)
        val textPages = metadata.pagesWithExtractableTextIn(safeRange)
        val scannedPages = (safeRange.pageCount - textPages).coerceAtLeast(0)

        val inputTokens = when (route) {
            PdfProcessingRoute.DIRECT_PDF ->
                PROMPT_OVERHEAD_TOKENS + safeRange.pageCount * DIRECT_PDF_TOKENS_PER_PAGE

            PdfProcessingRoute.EXTRACT_TEXT ->
                PROMPT_OVERHEAD_TOKENS + (textChars / CHARS_PER_TOKEN).toInt()

            PdfProcessingRoute.LOCAL_OCR,
            PdfProcessingRoute.OCR_REQUIRES_OVERRIDE,
            -> PROMPT_OVERHEAD_TOKENS +
                (textChars / CHARS_PER_TOKEN).toInt() +
                (scannedPages * OCR_CHARS_PER_SCANNED_PAGE / CHARS_PER_TOKEN).toInt()
        }

        val outputTokens = maxOf(
            MIN_OUTPUT_TOKENS,
            (inputTokens * TEXT_OUTPUT_RATIO).toInt(),
        )

        val cost = inputTokens / 1_000_000.0 * pricing.inputUsdPerMillion +
            outputTokens / 1_000_000.0 * pricing.outputUsdPerMillion
        return PdfCostEstimate(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            estimatedCostUsd = cost,
            pricing = pricing,
        )
    }
}

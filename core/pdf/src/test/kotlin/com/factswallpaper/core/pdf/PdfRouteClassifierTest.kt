package com.factswallpaper.core.pdf

import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfRouteClassifierTest {

    private val classifier = PdfRouteClassifier()

    @Test
    fun `all pages with text use the local text route`() {
        val decision = classify(metadata(pageCount = 4, textPages = setOf(1, 2, 3, 4)))

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.EXTRACT_TEXT)
        assertThat(decision.textPagesInRange).isEqualTo(4)
        assertThat(decision.scannedPagesInRange).isEqualTo(0)
        assertThat(decision.requiresOcrOverride).isFalse()
    }

    @Test
    fun `scanned pages within the limit use checkpointed local ocr`() {
        val decision = classify(metadata(pageCount = 10, textPages = setOf(1, 2, 3)))

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
        assertThat(decision.textPagesInRange).isEqualTo(3)
        assertThat(decision.scannedPagesInRange).isEqualTo(7)
        assertThat(decision.usesOcr).isTrue()
    }

    @Test
    fun `scanned pages above the limit require the explicit override`() {
        val decision = classify(
            metadata = metadata(pageCount = 30, textPages = setOf(1, 2, 3)),
            ocrPageLimit = 20,
        )

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.OCR_REQUIRES_OVERRIDE)
        assertThat(decision.requiresOcrOverride).isTrue()
        assertThat(decision.ocrPageLimit).isEqualTo(20)
    }

    @Test
    fun `override turns an over-limit scan into local ocr`() {
        val decision = classify(
            metadata = metadata(pageCount = 30, textPages = setOf(1, 2, 3)),
            options = options(overrideOcrPageLimit = true),
        )

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
        assertThat(decision.requiresOcrOverride).isFalse()
    }

    @Test
    fun `full local ocr is forced even for a complete text layer`() {
        val decision = classify(
            metadata = metadata(pageCount = 3, textPages = setOf(1, 2, 3)),
            options = options(forceFullLocalOcr = true),
        )

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
    }

    @Test
    fun `direct pdf is chosen only for a supporting provider and an explicit preference`() {
        val eligible = classify(
            metadata = metadata(pageCount = 10, textPages = setOf(1, 2, 3)),
            options = options(preferDirectPdfUpload = true),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = true),
        )
        assertThat(eligible.route).isEqualTo(PdfProcessingRoute.DIRECT_PDF)
        assertThat(eligible.directPdfEligible).isTrue()
        assertThat(eligible.directPdfBlockReason).isNull()

        val unsupported = classify(
            metadata = metadata(pageCount = 10, textPages = setOf(1, 2, 3)),
            options = options(preferDirectPdfUpload = true),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = false),
        )
        assertThat(unsupported.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
        assertThat(unsupported.directPdfEligible).isFalse()
        assertThat(unsupported.directPdfBlockReason)
            .isEqualTo(DirectPdfBlockReason.PROVIDER_UNSUPPORTED)
    }

    @Test
    fun `direct pdf falls back when the estimated sub-pdf exceeds 20 MB`() {
        val decision = classify(
            metadata = metadata(pageCount = 10, textPages = setOf(1, 2, 3), fileSizeBytes = 30L * MB),
            options = options(preferDirectPdfUpload = true),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = true),
        )

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
        assertThat(decision.directPdfBlockReason).isEqualTo(DirectPdfBlockReason.FILE_TOO_LARGE)
        assertThat(decision.estimatedDirectPdfBytes).isGreaterThan(20L * MB)
    }

    @Test
    fun `direct pdf falls back when the range exceeds 100 pages`() {
        val decision = classify(
            metadata = metadata(
                pageCount = 150,
                textPages = (1..150).toSet(),
                fileSizeBytes = 5L * MB,
            ),
            options = options(preferDirectPdfUpload = true),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = true),
        )

        assertThat(decision.route).isEqualTo(PdfProcessingRoute.EXTRACT_TEXT)
        assertThat(decision.directPdfBlockReason)
            .isEqualTo(DirectPdfBlockReason.TOO_MANY_PAGES)
    }

    @Test
    fun `page range only counts pages inside the requested range`() {
        val decision = classify(
            metadata = metadata(pageCount = 100, textPages = (1..50).toSet()),
            options = options(pageRange = PdfPageRange(40, 60)),
        )

        assertThat(decision.pageRange).isEqualTo(PdfPageRange(40, 60))
        assertThat(decision.filteredPageCount).isEqualTo(21)
        assertThat(decision.textPagesInRange).isEqualTo(11) // 40..50
        assertThat(decision.scannedPagesInRange).isEqualTo(10) // 51..60
        assertThat(decision.route).isEqualTo(PdfProcessingRoute.LOCAL_OCR)
    }

    @Test
    fun `page range is coerced to the real document length`() {
        val decision = classify(
            metadata = metadata(pageCount = 5, textPages = setOf(1)),
            options = options(pageRange = PdfPageRange(4, 99)),
        )

        assertThat(decision.pageRange).isEqualTo(PdfPageRange(4, 5))
        assertThat(decision.filteredPageCount).isEqualTo(2)
        assertThat(decision.scannedPagesInRange).isEqualTo(2)
    }

    @Test
    fun `direct byte estimate is proportional for a filtered range`() {
        val decision = classify(
            metadata = metadata(
                pageCount = 100,
                textPages = (1..100).toSet(),
                fileSizeBytes = 10L * MB,
            ),
            options = options(
                pageRange = PdfPageRange(1, 10),
                preferDirectPdfUpload = true,
            ),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = true),
        )

        // 10% of 10 MB plus 1% container overhead.
        assertThat(decision.estimatedDirectPdfBytes).isEqualTo(1_059_061L)
        assertThat(decision.route).isEqualTo(PdfProcessingRoute.DIRECT_PDF)
    }

    private fun classify(
        metadata: PdfMetadata,
        options: PdfProcessingOptions = options(),
        capabilities: PdfProviderCapabilities = PdfProviderCapabilities(
            supportsDirectPdfInput = false,
        ),
        ocrPageLimit: Int = 20,
    ): PdfRouteDecision = classifier.classify(metadata, options, capabilities, ocrPageLimit)

    private fun options(
        pageRange: PdfPageRange = PdfPageRange(1, Int.MAX_VALUE),
        forceFullLocalOcr: Boolean = false,
        preferDirectPdfUpload: Boolean = false,
        overrideOcrPageLimit: Boolean = false,
    ) = PdfProcessingOptions(
        pageRange = pageRange,
        forceFullLocalOcr = forceFullLocalOcr,
        preferDirectPdfUpload = preferDirectPdfUpload,
        overrideOcrPageLimit = overrideOcrPageLimit,
        ocrEngine = OcrEngineType.ML_KIT,
        ocrLanguageCode = "eng",
    )

    private fun metadata(
        pageCount: Int,
        textPages: Set<Int>,
        fileSizeBytes: Long = 1L * MB,
    ) = PdfMetadata(
        title = "Test PDF",
        embeddedTitle = null,
        sourceUri = "content://test/pdf",
        localFilePath = "/tmp/test.pdf",
        fileHash = "abc123",
        fileSizeBytes = fileSizeBytes,
        pageCount = pageCount,
        pageTextStats = (1..pageCount).map { page ->
            PdfPageTextStat(
                pageNumber = page,
                charCount = if (page in textPages) 120 else 0,
            )
        },
    )

    private companion object {
        const val MB = 1024L * 1024L
    }
}

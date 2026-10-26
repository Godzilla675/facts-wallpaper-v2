package com.factswallpaper.core.pdf

import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfCostEstimatorTest {

    @Test
    fun `text route estimates from the real extracted character count`() {
        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = mapOf(1 to 400, 2 to 800)),
            range = PdfPageRange(1, 2),
            route = PdfProcessingRoute.EXTRACT_TEXT,
            pricing = PdfPricing.BASELINE,
        )

        // 1,800 prompt overhead + (1,200 chars / 4 chars-per-token).
        assertThat(estimate.inputTokens).isEqualTo(2_100)
        assertThat(estimate.outputTokens).isEqualTo(420)
        assertThat(estimate.estimatedCostUsd).isWithin(1e-9).of(
            2_100 / 1_000_000.0 * 0.15 + 420 / 1_000_000.0 * 0.60,
        )
        assertThat(estimate.totalTokens).isEqualTo(2_520)
    }

    @Test
    fun `output tokens never fall below the documented floor`() {
        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = mapOf(1 to 40)),
            range = PdfPageRange(1, 1),
            route = PdfProcessingRoute.EXTRACT_TEXT,
        )

        assertThat(estimate.outputTokens).isEqualTo(400)
    }

    @Test
    fun `local ocr adds estimated text for every scanned page in the range`() {
        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = mapOf(1 to 400, 2 to 0, 3 to 0)),
            range = PdfPageRange(1, 3),
            route = PdfProcessingRoute.LOCAL_OCR,
        )

        // 1,800 + 100 text tokens + (2 scanned pages * 2,400 chars / 4).
        assertThat(estimate.inputTokens).isEqualTo(3_100)
    }

    @Test
    fun `direct pdf estimates from the filtered page count only`() {
        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = (1..100).associateWith { 10_000 }),
            range = PdfPageRange(10, 19),
            route = PdfProcessingRoute.DIRECT_PDF,
        )

        assertThat(estimate.inputTokens).isEqualTo(1_800 + 10 * 900)
    }

    @Test
    fun `range is coerced before estimating`() {
        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = mapOf(1 to 400, 2 to 400, 3 to 400)),
            range = PdfPageRange(2, 99),
            route = PdfProcessingRoute.EXTRACT_TEXT,
        )

        assertThat(estimate.inputTokens).isEqualTo(1_800 + 800 / 4)
    }

    @Test
    fun `provider pricing is carried into the estimate`() {
        val pricing = PdfPricing(
            inputUsdPerMillion = 1.0,
            outputUsdPerMillion = 2.0,
            sourceLabel = "Test provider",
            isBaseline = false,
        )

        val estimate = PdfCostEstimator.estimate(
            metadata = metadata(pageTextChars = mapOf(1 to 4_000)),
            range = PdfPageRange(1, 1),
            route = PdfProcessingRoute.EXTRACT_TEXT,
            pricing = pricing,
        )

        assertThat(estimate.pricing).isEqualTo(pricing)
        assertThat(estimate.estimatedCostUsd).isWithin(1e-9).of(
            estimate.inputTokens / 1_000_000.0 + estimate.outputTokens / 1_000_000.0 * 2.0,
        )
    }

    private fun metadata(pageTextChars: Map<Int, Int>): PdfMetadata {
        val pageCount = pageTextChars.keys.maxOrNull() ?: 1
        return PdfMetadata(
            title = "Cost fixture",
            embeddedTitle = null,
            sourceUri = "content://fixture",
            localFilePath = "/tmp/fixture.pdf",
            fileHash = "hash",
            fileSizeBytes = 1_000L,
            pageCount = pageCount,
            pageTextStats = (1..pageCount).map { page ->
                PdfPageTextStat(pageNumber = page, charCount = pageTextChars[page] ?: 0)
            },
        )
    }
}

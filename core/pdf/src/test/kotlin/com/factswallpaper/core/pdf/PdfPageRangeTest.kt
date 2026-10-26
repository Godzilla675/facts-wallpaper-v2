package com.factswallpaper.core.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfPageRangeTest {

    @Test
    fun `parse accepts blank and all-pages aliases`() {
        assertThat(PdfPageRange.parse("", pageCount = 20)).isEqualTo(PdfPageRange(1, 20))
        assertThat(PdfPageRange.parse("   ", pageCount = 20)).isEqualTo(PdfPageRange(1, 20))
        assertThat(PdfPageRange.parse("all", pageCount = 20)).isEqualTo(PdfPageRange(1, 20))
        assertThat(PdfPageRange.parse("ALL PAGES", pageCount = 20))
            .isEqualTo(PdfPageRange(1, 20))
    }

    @Test
    fun `parse accepts the range separators used by the specification`() {
        assertThat(PdfPageRange.parse("12-48", pageCount = 100))
            .isEqualTo(PdfPageRange(12, 48))
        assertThat(PdfPageRange.parse("12\u201348", pageCount = 100))
            .isEqualTo(PdfPageRange(12, 48))
        assertThat(PdfPageRange.parse("12\u201448", pageCount = 100))
            .isEqualTo(PdfPageRange(12, 48))
        assertThat(PdfPageRange.parse("12..48", pageCount = 100))
            .isEqualTo(PdfPageRange(12, 48))
        assertThat(PdfPageRange.parse(" 12 - 48 ", pageCount = 100))
            .isEqualTo(PdfPageRange(12, 48))
    }

    @Test
    fun `parse treats a single number as one page`() {
        assertThat(PdfPageRange.parse("42", pageCount = 100))
            .isEqualTo(PdfPageRange(42, 42))
    }

    @Test
    fun `parse rejects malformed and out-of-bounds ranges`() {
        assertThat(PdfPageRange.parse("0-4", pageCount = 10)).isNull()
        assertThat(PdfPageRange.parse("5-4", pageCount = 10)).isNull()
        assertThat(PdfPageRange.parse("1-11", pageCount = 10)).isNull()
        assertThat(PdfPageRange.parse("a-b", pageCount = 10)).isNull()
        assertThat(PdfPageRange.parse("1-2-3", pageCount = 10)).isNull()
        assertThat(PdfPageRange.parse("", pageCount = 0)).isNull()
    }

    @Test
    fun `field resolver defaults the end to the final page`() {
        assertThat(PageRangeInput.resolve(startText = "5", endText = "", pageCount = 20))
            .isEqualTo(PdfPageRange(5, 20))
        assertThat(PageRangeInput.resolve(startText = "5", endText = "9", pageCount = 20))
            .isEqualTo(PdfPageRange(5, 9))
    }

    @Test
    fun `field resolver rejects blank start and invalid bounds`() {
        assertThat(PageRangeInput.resolve(startText = "", endText = "9", pageCount = 20))
            .isNull()
        assertThat(PageRangeInput.resolve(startText = "0", endText = "9", pageCount = 20))
            .isNull()
        assertThat(PageRangeInput.resolve(startText = "9", endText = "5", pageCount = 20))
            .isNull()
        assertThat(PageRangeInput.resolve(startText = "5", endText = "21", pageCount = 20))
            .isNull()
        assertThat(PageRangeInput.resolve(startText = "5", endText = "", pageCount = 0))
            .isNull()
    }

    @Test
    fun `coerce clamps both edges into the real document`() {
        assertThat(PdfPageRange(4, 99).coerceTo(5)).isEqualTo(PdfPageRange(4, 5))
        assertThat(PdfPageRange(7, 7).coerceTo(5)).isEqualTo(PdfPageRange(5, 5))
        assertThat(PdfPageRange(1, 5).coerceTo(0)).isEqualTo(PdfPageRange(1, 1))
    }

    @Test
    fun `intersection returns the overlap or null`() {
        assertThat(PdfPageRange(1, 10).intersection(PdfPageRange(8, 20)))
            .isEqualTo(PdfPageRange(8, 10))
        assertThat(PdfPageRange(1, 10).intersection(PdfPageRange(10, 20)))
            .isEqualTo(PdfPageRange(10, 10))
        assertThat(PdfPageRange(1, 10).intersection(PdfPageRange(11, 20))).isNull()
    }
}

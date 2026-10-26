package com.factswallpaper.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EnumsTest {

    @Test
    fun `fact density ranges follow the specification`() {
        assertThat(FactDensity.CONCISE.minFactsPerLesson).isEqualTo(3)
        assertThat(FactDensity.CONCISE.maxFactsPerLesson).isEqualTo(5)
        assertThat(FactDensity.BALANCED.minFactsPerLesson).isEqualTo(6)
        assertThat(FactDensity.BALANCED.maxFactsPerLesson).isEqualTo(10)
        assertThat(FactDensity.DEEP.minFactsPerLesson).isEqualTo(12)
        assertThat(FactDensity.DEEP.maxFactsPerLesson).isEqualTo(18)
    }

    @Test
    fun `density ranges do not overlap and increase`() {
        val densities = FactDensity.entries
        densities.forEach { density ->
            assertThat(density.minFactsPerLesson).isAtMost(density.maxFactsPerLesson)
        }
        densities.zipWithNext().forEach { (lower, higher) ->
            assertThat(lower.maxFactsPerLesson).isLessThan(higher.minFactsPerLesson)
        }
    }

    @Test
    fun `extraction status parses stored names and falls back to imported`() {
        assertThat(ExtractionStatus.fromStorage("FACTS_READY")).isEqualTo(ExtractionStatus.FACTS_READY)
        assertThat(ExtractionStatus.fromStorage("OCR_PAUSED")).isEqualTo(ExtractionStatus.OCR_PAUSED)
        assertThat(ExtractionStatus.fromStorage("not-a-status")).isEqualTo(ExtractionStatus.IMPORTED)
        assertThat(ExtractionStatus.fromStorage("")).isEqualTo(ExtractionStatus.IMPORTED)
    }

    @Test
    fun `only ocr progress states are considered ocr active`() {
        val active = ExtractionStatus.entries.filter { it.isOcrActive }
        assertThat(active).containsExactly(
            ExtractionStatus.OCR_IN_PROGRESS,
            ExtractionStatus.OCR_PAUSED,
        )
    }
}

package com.factswallpaper.core.data.repository

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StorageStatsTest {

    @Test
    fun `totalBytes calculates sum of all storage categories accurately`() {
        val stats = StorageStats(
            pdfBytes = 10_000_000L,
            cardCacheBytes = 2_500_000L,
            diagramCacheBytes = 1_200_000L,
            databaseBytes = 800_000L,
        )

        assertThat(stats.totalBytes).isEqualTo(14_500_000L)
    }

    @Test
    fun `totalBytes with zeros yields zero`() {
        val emptyStats = StorageStats()
        assertThat(emptyStats.totalBytes).isEqualTo(0L)
    }
}

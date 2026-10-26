package com.factswallpaper.core.ui.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MetricsFormatterTest {

    @Test
    fun `bytes use binary units with one decimal below one hundred`() {
        assertThat(MetricsFormatter.bytes(0L)).isEqualTo("0 B")
        assertThat(MetricsFormatter.bytes(1_023L)).isEqualTo("1023 B")
        assertThat(MetricsFormatter.bytes(1_024L)).isEqualTo("1.0 KB")
        assertThat(MetricsFormatter.bytes(1_536L)).isEqualTo("1.5 KB")
        assertThat(MetricsFormatter.bytes(1_048_576L)).isEqualTo("1.0 MB")
        assertThat(MetricsFormatter.bytes(118_111_436L)).isEqualTo("113 MB")
        assertThat(MetricsFormatter.bytes(1_099_511_627_776L)).isEqualTo("1.0 TB")
    }

    @Test
    fun `bytes do not exceed the largest supported unit`() {
        assertThat(MetricsFormatter.bytes(5_000_000_000_000L)).endsWith(" TB")
    }

    @Test
    fun `cost is rendered with four decimal places`() {
        assertThat(MetricsFormatter.costUsd(0.0)).isEqualTo("$0.0000")
        assertThat(MetricsFormatter.costUsd(0.012_34)).isEqualTo("$0.0123")
        assertThat(MetricsFormatter.costUsd(12.5)).isEqualTo("$12.5000")
    }

    @Test
    fun `tokens are compacted at thousands and millions`() {
        assertThat(MetricsFormatter.compactTokens(0)).isEqualTo("0")
        assertThat(MetricsFormatter.compactTokens(999)).isEqualTo("999")
        assertThat(MetricsFormatter.compactTokens(1_000)).isEqualTo("1k")
        assertThat(MetricsFormatter.compactTokens(12_345)).isEqualTo("12k")
        assertThat(MetricsFormatter.compactTokens(999_999)).isEqualTo("999k")
        assertThat(MetricsFormatter.compactTokens(1_000_000)).isEqualTo("1.0M")
        assertThat(MetricsFormatter.compactTokens(2_450_000)).isEqualTo("2.5M")
    }

    @Test
    fun `percent clamps the fraction and rounds down`() {
        assertThat(MetricsFormatter.percent(0f)).isEqualTo("0%")
        assertThat(MetricsFormatter.percent(0.755f)).isEqualTo("75%")
        assertThat(MetricsFormatter.percent(1f)).isEqualTo("100%")
        assertThat(MetricsFormatter.percent(1.4f)).isEqualTo("100%")
        assertThat(MetricsFormatter.percent(-0.2f)).isEqualTo("0%")
    }
}

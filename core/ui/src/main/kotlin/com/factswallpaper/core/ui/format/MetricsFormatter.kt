package com.factswallpaper.core.ui.format

import java.util.Locale

/** Locale-stable metric formatting used by pills and settings summaries. */
object MetricsFormatter {

    fun bytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = listOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = -1
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        val pattern = if (value >= 100.0) "%.0f %s" else "%.1f %s"
        return String.format(Locale.US, pattern, value, units[unitIndex])
    }

    fun costUsd(usd: Double): String = String.format(Locale.US, "$%.4f", usd)

    fun compactTokens(tokens: Long): String = compactTokens(tokens.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())

    fun compactTokens(tokens: Int): String = when {
        tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
        tokens >= 1_000 -> "${tokens / 1_000}k"
        else -> tokens.toString()
    }

    fun percent(fraction: Float): String =
        String.format(Locale.US, "%d%%", (fraction.coerceIn(0f, 1f) * 100).toInt())
}

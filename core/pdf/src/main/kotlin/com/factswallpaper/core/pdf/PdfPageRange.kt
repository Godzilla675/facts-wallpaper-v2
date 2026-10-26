package com.factswallpaper.core.pdf

/** One-based, inclusive page range used for extraction, OCR and direct-PDF uploads. */
data class PdfPageRange(
    val startPage: Int,
    val endPage: Int,
) {
    init {
        require(startPage >= 1) { "startPage must be >= 1" }
        require(endPage >= startPage) { "endPage must be >= startPage" }
    }

    val pageCount: Int get() = endPage - startPage + 1

    fun contains(page: Int): Boolean = page in startPage..endPage

    fun intersection(other: PdfPageRange): PdfPageRange? {
        val start = maxOf(startPage, other.startPage)
        val end = minOf(endPage, other.endPage)
        return if (end >= start) PdfPageRange(start, end) else null
    }

    fun coerceTo(pageCount: Int): PdfPageRange {
        val safePageCount = pageCount.coerceAtLeast(1)
        val start = startPage.coerceIn(1, safePageCount)
        val end = endPage.coerceIn(start, safePageCount)
        return PdfPageRange(start, end)
    }

    companion object {
        fun all(pageCount: Int): PdfPageRange = PdfPageRange(1, pageCount.coerceAtLeast(1))

        /**
         * Parses the free-form ranges accepted by the import screen:
         * `All`, `All pages`, `12-48`, `12–48`, `12..48`, `12` and blank (= all).
         * Returns null for malformed or out-of-bounds input.
         */
        fun parse(raw: String, pageCount: Int): PdfPageRange? {
            if (pageCount < 1) return null
            val value = raw.trim()
            if (value.isEmpty() || value.equals("all", ignoreCase = true) ||
                value.equals("all pages", ignoreCase = true)
            ) {
                return all(pageCount)
            }

            val normalized = value
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replace("..", "-")
                .replace(" ", "")
            val parts = normalized.split('-')
            if (parts.size > 2) return null

            val start = parts.first().toIntOrNull() ?: return null
            val end = parts.getOrNull(1)?.toIntOrNull() ?: start
            if (start < 1 || end < start || end > pageCount) return null
            return PdfPageRange(start, end)
        }
    }
}

/** Parses `startPage`/`endPage` text fields, clamping them to the document. */
object PageRangeInput {
    fun resolve(
        startText: String,
        endText: String,
        pageCount: Int,
    ): PdfPageRange? {
        if (pageCount < 1) return null
        val start = startText.trim().toIntOrNull() ?: return null
        val end = endText.trim().toIntOrNull() ?: pageCount
        if (start < 1 || end < start || end > pageCount) return null
        return PdfPageRange(start, end)
    }
}

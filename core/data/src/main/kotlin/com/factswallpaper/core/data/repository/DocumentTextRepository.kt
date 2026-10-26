package com.factswallpaper.core.data.repository

import com.factswallpaper.core.ai.PageText

/**
 * Read-only access to the per-page extracted/OCR text used as AI input.
 * Chunks are written by the PDF/OCR pipeline; this repository only projects the
 * completed ones into [PageText] so outline and facts workers never touch Room
 * entities directly.
 */
interface DocumentTextRepository {
    suspend fun getPages(
        documentId: String,
        startPage: Int = 1,
        endPage: Int = Int.MAX_VALUE,
    ): List<PageText>

    suspend fun getPageCount(documentId: String): Int
}

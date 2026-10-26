package com.factswallpaper.core.ocr

import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings

/**
 * Exact crash/cutoff resume rule from spec F5. If a page was marked started but
 * never atomically committed, that page is restarted from scratch; previously
 * completed pages are never re-OCRed.
 */
data class OcrResumePoint(
    val rangeStart: Int,
    val rangeEnd: Int,
    val resumeFromPage: Int,
    val alreadyComplete: Boolean,
    val restartsInterruptedPage: Boolean,
    val completedPages: Int,
) {
    val totalPages: Int get() = (rangeEnd - rangeStart + 1).coerceAtLeast(0)
}

object OcrCheckpointResolver {

    fun resolve(
        document: Document,
        settings: DocumentSettings,
        pageCount: Int = document.pageCount,
    ): OcrResumePoint {
        val safePageCount = pageCount.coerceAtLeast(1)
        val rangeStart = settings.pageRangeStart.coerceIn(1, safePageCount)
        val rangeEnd = settings.pageRangeEnd.coerceIn(rangeStart, safePageCount)
        val completed = document.ocrLastCompletedPage
        val started = document.ocrLastStartedPage

        val restartsInterruptedPage = started > completed && started in rangeStart..rangeEnd
        val resumeFromPage = when {
            completed < rangeStart -> rangeStart
            started > completed -> started
            else -> completed + 1
        }
        val alreadyComplete = resumeFromPage > rangeEnd
        return OcrResumePoint(
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            resumeFromPage = resumeFromPage,
            alreadyComplete = alreadyComplete,
            restartsInterruptedPage = restartsInterruptedPage && !alreadyComplete,
            completedPages = (completed.coerceAtLeast(rangeStart - 1) - rangeStart + 1)
                .coerceIn(0, rangeEnd - rangeStart + 1),
        )
    }
}

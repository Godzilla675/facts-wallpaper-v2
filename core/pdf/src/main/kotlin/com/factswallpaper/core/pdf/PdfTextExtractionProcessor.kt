package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class PdfTextExtractionProgress(
    val page: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val completedPages: Int,
) {
    val totalPages: Int get() = (rangeEnd - rangeStart + 1).coerceAtLeast(0)
    val fraction: Float
        get() = if (totalPages <= 0) 0f
        else (completedPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
}

/**
 * F4 text-layer extraction. Pages are persisted as completed chunks with their
 * one-based page numbers, so every future lesson keeps exact provenance.
 */
@Singleton
class PdfTextExtractionProcessor @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val extractor: PdfTextExtractor,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun extract(
        document: Document,
        settings: DocumentSettings,
        onProgress: suspend (PdfTextExtractionProgress) -> Unit = {},
    ): Int = withContext(dispatchers.io) {
        val result = extractor.extract(
            File(document.localFilePath),
            settings.toRange(document.pageCount),
        )
        if (documentRepository.getLocalFilePath(document.id) != document.localFilePath) {
            return@withContext 0
        }
        documentRepository.clearChunks(document.id)
        var completed = 0
        for (page in result.pages) {
            coroutineContext.ensureActive()
            if (documentRepository.getLocalFilePath(document.id) != document.localFilePath) {
                return@withContext completed
            }
            documentRepository.upsertChunk(
                DocumentChunk(
                    documentId = document.id,
                    pageNumber = page.pageNumber,
                    extractedText = page.text,
                    status = STATUS_COMPLETED,
                    isFromOcr = false,
                    ocrConfidence = null,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            completed += 1
            onProgress(
                PdfTextExtractionProgress(
                    page = page.pageNumber,
                    rangeStart = result.range.startPage,
                    rangeEnd = result.range.endPage,
                    completedPages = completed,
                ),
            )
        }
        if (documentRepository.getLocalFilePath(document.id) != document.localFilePath) {
            return@withContext completed
        }
        documentRepository.updateExtractionStatus(document.id, ExtractionStatus.ANALYZED)
        completed
    }

    private fun DocumentSettings.toRange(pageCount: Int): PdfPageRange {
        val safePageCount = pageCount.coerceAtLeast(1)
        val start = pageRangeStart.coerceIn(1, safePageCount)
        val end = pageRangeEnd.coerceIn(start, safePageCount)
        return PdfPageRange(start, end)
    }

    private companion object {
        const val STATUS_COMPLETED = "COMPLETED"
    }
}

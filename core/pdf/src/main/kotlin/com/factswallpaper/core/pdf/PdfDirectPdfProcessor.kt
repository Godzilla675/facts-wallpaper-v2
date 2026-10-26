package com.factswallpaper.core.pdf

import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class DirectPdfPreparation(
    val file: File,
    val range: PdfPageRange,
    val isSubPdf: Boolean,
)

/**
 * Prepares the exact bytes the direct-PDF route would upload: the original PDF
 * for an unfiltered document, or a sub-PDF containing only the chosen range.
 */
@Singleton
class PdfDirectPdfProcessor @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val subPdfBuilder: PdfSubPdfBuilder,
) {

    suspend fun prepare(
        document: Document,
        settings: DocumentSettings,
    ): DirectPdfPreparation {
        val pageCount = document.pageCount.coerceAtLeast(1)
        val start = settings.pageRangeStart.coerceIn(1, pageCount)
        val end = settings.pageRangeEnd.coerceIn(start, pageCount)
        val range = PdfPageRange(start, end)
        val file = subPdfBuilder.build(document, range)
        val isSubPdf = file.absolutePath != File(document.localFilePath).absolutePath
        if (documentRepository.getLocalFilePath(document.id) == document.localFilePath) {
            documentRepository.updateExtractionStatus(document.id, ExtractionStatus.ANALYZED)
        }
        return DirectPdfPreparation(file = file, range = range, isSubPdf = isSubPdf)
    }
}

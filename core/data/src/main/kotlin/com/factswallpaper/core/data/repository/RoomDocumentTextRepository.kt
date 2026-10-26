package com.factswallpaper.core.data.repository

import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.common.FactsDispatchers
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Consolidates DocumentTextRepository to project directly from the single source of truth [DocumentRepository].
 */
@Singleton
class RoomDocumentTextRepository @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val dispatchers: FactsDispatchers,
) : DocumentTextRepository {

    override suspend fun getPages(
        documentId: String,
        startPage: Int,
        endPage: Int,
    ): List<PageText> = withContext(dispatchers.io) {
        documentRepository.getChunks(documentId, startPage, endPage)
            .asSequence()
            .filter { it.status == "COMPLETED" && it.extractedText.isNotBlank() }
            .map { PageText(pageNumber = it.pageNumber, text = it.extractedText) }
            .toList()
    }

    override suspend fun getPageCount(documentId: String): Int = withContext(dispatchers.io) {
        documentRepository.getDocument(documentId)?.pageCount ?: 0
    }
}

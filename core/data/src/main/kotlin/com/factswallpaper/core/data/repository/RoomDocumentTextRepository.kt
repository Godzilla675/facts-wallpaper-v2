package com.factswallpaper.core.data.repository

import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.dao.PdfDocumentDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class RoomDocumentTextRepository @Inject constructor(
    private val documentDao: PdfDocumentDao,
    private val dispatchers: FactsDispatchers,
) : DocumentTextRepository {

    override suspend fun getPages(
        documentId: String,
        startPage: Int,
        endPage: Int,
    ): List<PageText> = withContext(dispatchers.io) {
        documentDao.getChunksForPageRange(documentId, startPage, endPage)
            .asSequence()
            .filter { it.status == "COMPLETED" && it.extractedText.isNotBlank() }
            .map { PageText(pageNumber = it.pageNumber, text = it.extractedText) }
            .toList()
    }

    override suspend fun getPageCount(documentId: String): Int = withContext(dispatchers.io) {
        documentDao.getById(documentId)?.pageCount ?: 0
    }
}

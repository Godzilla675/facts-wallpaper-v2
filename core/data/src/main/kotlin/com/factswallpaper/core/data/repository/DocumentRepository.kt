package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(documentId: String): Flow<Document?>
    suspend fun getDocument(documentId: String): Document?
    suspend fun findByHash(fileHash: String): Document?

    fun observeSettings(documentId: String): Flow<DocumentSettings?>
    suspend fun getSettings(documentId: String): DocumentSettings?

    suspend fun saveDocument(document: Document)
    suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings)
    suspend fun saveSettings(settings: DocumentSettings)
    suspend fun setIncludedInRotation(documentId: String, included: Boolean)
    suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String? = null,
    )

    suspend fun deleteDocument(documentId: String): AppResult<Unit>
    fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>>
}

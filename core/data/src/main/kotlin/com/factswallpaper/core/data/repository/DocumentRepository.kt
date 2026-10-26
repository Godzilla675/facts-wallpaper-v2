package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>
    fun observeDocument(documentId: String): Flow<Document?>
    suspend fun getDocument(documentId: String): Document?
    suspend fun findByHash(fileHash: String): Document?

    /** Current app-private PDF path, used to detect replaced/deleted documents mid-job. */
    suspend fun getLocalFilePath(documentId: String): String?

    fun observeSettings(documentId: String): Flow<DocumentSettings?>
    suspend fun getSettings(documentId: String): DocumentSettings?

    /** Settings for every document, used by the library's OCR progress cards. */
    fun observeAllSettings(): Flow<List<DocumentSettings>>

    suspend fun saveDocument(document: Document)
    suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings)

    /** Replaces an existing document atomically and drops its stale chunks. */
    suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings)
    suspend fun saveSettings(settings: DocumentSettings)
    suspend fun setIncludedInRotation(documentId: String, included: Boolean)
    suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String? = null,
    )

    suspend fun deleteDocument(documentId: String): AppResult<Unit>
    fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>>

    /** Live per-page extraction/OCR chunks, used for checkpoint progress in the UI. */
    fun observeChunks(documentId: String): Flow<List<DocumentChunk>>

    suspend fun getChunks(
        documentId: String,
        startPage: Int = 1,
        endPage: Int = Int.MAX_VALUE,
    ): List<DocumentChunk>

    suspend fun upsertChunk(chunk: DocumentChunk)

    suspend fun clearChunks(documentId: String)

    /** Marks [page] as started before rasterization so a killed page is restarted. */
    suspend fun markOcrPageStarted(documentId: String, page: Int)

    /** Atomically stores a completed OCR page and advances the resume marker. */
    suspend fun commitOcrPage(
        documentId: String,
        page: Int,
        text: String,
        confidence: Float?,
    )

    suspend fun updateCompiledText(
        documentId: String,
        compiledTextPath: String,
        status: ExtractionStatus,
    )
}

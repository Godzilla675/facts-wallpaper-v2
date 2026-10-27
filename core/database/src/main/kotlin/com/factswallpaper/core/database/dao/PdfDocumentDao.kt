package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.DocumentChunkEntity
import com.factswallpaper.core.database.entity.DocumentSettingsEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PdfDocumentDao {

    @Query("SELECT * FROM pdf_documents ORDER BY createdAtEpochMs DESC")
    abstract fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    abstract fun observeById(id: String): Flow<PdfDocumentEntity?>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    abstract suspend fun getById(id: String): PdfDocumentEntity?

    @Query("SELECT localFilePath FROM pdf_documents WHERE id = :id")
    abstract suspend fun getLocalFilePath(id: String): String?

    @Query("SELECT * FROM pdf_documents WHERE fileHash = :fileHash LIMIT 1")
    abstract suspend fun findByHash(fileHash: String): PdfDocumentEntity?

    @Query("SELECT COUNT(*) FROM pdf_documents")
    abstract fun observeCount(): Flow<Int>

    @Query(
        "SELECT processingRoute, COUNT(*) AS count FROM pdf_documents " +
            "GROUP BY processingRoute"
    )
    abstract fun observeRouteCounts(): Flow<List<ProcessingRouteCount>>

    @Upsert
    abstract suspend fun upsert(doc: PdfDocumentEntity)

    @Transaction
    open suspend fun insertDocumentWithSettings(
        document: PdfDocumentEntity,
        settings: DocumentSettingsEntity,
    ) {
        upsert(document)
        upsertSettings(settings)
    }

    /**
     * Re-import & replace: the new PDF metadata/settings replace the old row in
     * the same transaction that drops stale page chunks, so a failure can never
     * leave old OCR text attached to a new file.
     */
    @Transaction
    open suspend fun replaceDocumentWithSettings(
        document: PdfDocumentEntity,
        settings: DocumentSettingsEntity,
    ) {
        upsert(document)
        upsertSettings(settings)
        deleteChunks(document.id)
    }

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    @Query(
        "UPDATE pdf_documents SET includedInRotation = :included, " +
            "updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun setIncludedInRotation(id: String, included: Boolean, updatedAtEpochMs: Long)

    @Query(
        "UPDATE pdf_documents SET extractionStatus = :status, lastErrorMessage = :errorMessage, " +
            "updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun updateExtractionStatus(
        id: String,
        status: String,
        errorMessage: String?,
        updatedAtEpochMs: Long,
    )

    @Query(
        "UPDATE pdf_documents SET pipelineProgress = :progress, " +
            "updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun updatePipelineProgress(id: String, progress: Float, updatedAtEpochMs: Long)

    @Query(
        "UPDATE pdf_documents SET extractionStatus = :status, pipelineProgress = :progress, " +
            "lastErrorMessage = :errorMessage, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun updatePipelineStatus(
        id: String,
        status: String,
        progress: Float,
        errorMessage: String?,
        updatedAtEpochMs: Long,
    )

    @Query(
        "UPDATE pdf_documents SET ocrLastStartedPage = :page, extractionStatus = :status, " +
            "updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun markOcrPageStarted(
        id: String,
        page: Int,
        status: String,
        updatedAtEpochMs: Long,
    )

    @Query(
        "UPDATE pdf_documents SET ocrLastCompletedPage = :page, " +
            "updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun markOcrPageCompleted(id: String, page: Int, updatedAtEpochMs: Long)

    @Query(
        "UPDATE pdf_documents SET compiledOcrTextPath = :path, extractionStatus = :status, " +
            "lastErrorMessage = NULL, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id"
    )
    abstract suspend fun updateCompiledText(
        id: String,
        path: String,
        status: String,
        updatedAtEpochMs: Long,
    )

    @Query("SELECT * FROM document_settings WHERE documentId = :documentId")
    abstract suspend fun getSettings(documentId: String): DocumentSettingsEntity?

    @Query("SELECT * FROM document_settings WHERE documentId = :documentId")
    abstract fun observeSettings(documentId: String): Flow<DocumentSettingsEntity?>

    @Query("SELECT * FROM document_settings")
    abstract fun observeAllSettings(): Flow<List<DocumentSettingsEntity>>

    @Upsert
    abstract suspend fun upsertSettings(settings: DocumentSettingsEntity)

    @Query(
        "SELECT * FROM document_chunks WHERE documentId = :documentId " +
            "AND pageNumber BETWEEN :startPage AND :endPage ORDER BY pageNumber ASC"
    )
    abstract suspend fun getChunksForPageRange(
        documentId: String,
        startPage: Int,
        endPage: Int,
    ): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId = :documentId ORDER BY pageNumber ASC")
    abstract suspend fun getChunks(documentId: String): List<DocumentChunkEntity>

    @Query("SELECT * FROM document_chunks WHERE documentId = :documentId ORDER BY pageNumber ASC")
    abstract fun observeChunks(documentId: String): Flow<List<DocumentChunkEntity>>

    @Query("SELECT COUNT(*) FROM document_chunks WHERE documentId = :documentId AND status = 'COMPLETED'")
    abstract suspend fun countCompletedChunks(documentId: String): Int

    @Upsert
    abstract suspend fun upsertChunks(chunks: List<DocumentChunkEntity>)

    @Upsert
    abstract suspend fun upsertChunk(chunk: DocumentChunkEntity)

    @Query("DELETE FROM document_chunks WHERE documentId = :documentId")
    abstract suspend fun deleteChunks(documentId: String)

    /**
     * Atomic per-page OCR checkpoint: the page text and the resume marker are
     * committed together so a process death can never lose a finished page or
     * claim a half-written page is complete.
     */
    @Transaction
    open suspend fun commitOcrPage(
        documentId: String,
        pageNumber: Int,
        text: String,
        confidence: Float?,
        nowEpochMs: Long,
    ) {
        upsertChunk(
            DocumentChunkEntity(
                documentId = documentId,
                pageNumber = pageNumber,
                extractedText = text,
                status = "COMPLETED",
                isFromOcr = true,
                ocrConfidence = confidence,
                updatedAtEpochMs = nowEpochMs,
            )
        )
        markOcrPageCompleted(documentId, pageNumber, nowEpochMs)
    }
}

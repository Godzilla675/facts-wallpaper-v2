package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.database.dao.LessonDao
import com.factswallpaper.core.database.dao.PdfDocumentDao
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomDocumentRepository @Inject constructor(
    private val documentDao: PdfDocumentDao,
    private val lessonDao: LessonDao,
    private val factDao: FactDao,
    private val dispatchers: FactsDispatchers,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> = combine(
        documentDao.observeAll(),
        lessonDao.observeLessonCounts(),
        factDao.observeFactCountsByDocument(),
    ) { documents, lessonCounts, factCounts ->
        val lessonsByDocument = lessonCounts.associate { it.documentId to it.count }
        val factsByDocument = factCounts.associate { it.documentId to it.count }
        documents.map {
            it.toDomain(
                lessonCount = lessonsByDocument[it.id] ?: 0,
                factCount = factsByDocument[it.id] ?: 0,
            )
        }
    }

    override fun observeDocument(documentId: String): Flow<Document?> = combine(
        documentDao.observeById(documentId),
        lessonDao.observeLessonCounts(),
        factDao.observeFactCountsByDocument(),
    ) { document, lessonCounts, factCounts ->
        document?.toDomain(
            lessonCount = lessonCounts.firstOrNull { it.documentId == documentId }?.count ?: 0,
            factCount = factCounts.firstOrNull { it.documentId == documentId }?.count ?: 0,
        )
    }

    override suspend fun getDocument(documentId: String): Document? {
        val entity = documentDao.getById(documentId) ?: return null
        return entity.toDomain(
            lessonCount = lessonDao.countByDocument(documentId),
            factCount = factDao.countByDocument(documentId),
        )
    }

    override suspend fun findByHash(fileHash: String): Document? =
        documentDao.findByHash(fileHash)?.toDomain()

    override suspend fun getLocalFilePath(documentId: String): String? =
        documentDao.getLocalFilePath(documentId)

    override fun observeSettings(documentId: String): Flow<DocumentSettings?> =
        documentDao.observeSettings(documentId).map { it?.toDomain() }

    override suspend fun getSettings(documentId: String): DocumentSettings? =
        documentDao.getSettings(documentId)?.toDomain()

    override fun observeAllSettings(): Flow<List<DocumentSettings>> =
        documentDao.observeAllSettings().map { settings -> settings.map { it.toDomain() } }

    override suspend fun saveDocument(document: Document) {
        withContext(dispatchers.io) { documentDao.upsert(document.toEntity()) }
    }

    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {
        withContext(dispatchers.io) {
            documentDao.insertDocumentWithSettings(document.toEntity(), settings.toEntity())
        }
    }

    override suspend fun replaceDocumentWithSettings(
        document: Document,
        settings: DocumentSettings,
    ) {
        withContext(dispatchers.io) {
            documentDao.replaceDocumentWithSettings(document.toEntity(), settings.toEntity())
        }
    }

    override suspend fun saveSettings(settings: DocumentSettings) {
        withContext(dispatchers.io) { documentDao.upsertSettings(settings.toEntity()) }
    }

    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {
        withContext(dispatchers.io) {
            documentDao.setIncludedInRotation(documentId, included, System.currentTimeMillis())
        }
    }

    override suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String?,
    ) {
        withContext(dispatchers.io) {
            documentDao.updateExtractionStatus(
                id = documentId,
                status = status.name,
                errorMessage = errorMessage,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updatePipelineProgress(documentId: String, progress: Float) {
        withContext(dispatchers.io) {
            documentDao.updatePipelineProgress(
                id = documentId,
                progress = progress.coerceIn(0f, 1f),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updatePipelineStatus(
        documentId: String,
        status: ExtractionStatus,
        progress: Float,
        errorMessage: String?,
    ) {
        withContext(dispatchers.io) {
            documentDao.updatePipelineStatus(
                id = documentId,
                status = status.name,
                progress = progress.coerceIn(0f, 1f),
                errorMessage = errorMessage,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            val document = try {
                documentDao.getById(documentId)
            } catch (t: Throwable) {
                return@withContext AppResult.Failure(AppError.Unknown(t))
            } ?: return@withContext AppResult.Failure(AppError.NotFound("Document"))

            try {
                // Room cascades lessons, chunks, facts and cross references.
                documentDao.deleteById(documentId)
            } catch (t: Throwable) {
                return@withContext AppResult.Failure(AppError.Storage(t))
            }

            deleteQuietly(document.localFilePath)
            deleteQuietly(document.compiledOcrTextPath)
            AppResult.Success(Unit)
        }

    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> =
        documentDao.observeRouteCounts().map { counts ->
            counts.associate { it.processingRoute to it.count }
        }

    override fun observeChunks(documentId: String): Flow<List<DocumentChunk>> =
        documentDao.observeChunks(documentId).map { chunks -> chunks.map { it.toDomain() } }

    override suspend fun getChunks(
        documentId: String,
        startPage: Int,
        endPage: Int,
    ): List<DocumentChunk> = withContext(dispatchers.io) {
        documentDao.getChunksForPageRange(documentId, startPage, endPage).map { it.toDomain() }
    }

    override suspend fun upsertChunk(chunk: DocumentChunk) {
        withContext(dispatchers.io) { documentDao.upsertChunk(chunk.toEntity()) }
    }

    override suspend fun clearChunks(documentId: String) {
        withContext(dispatchers.io) { documentDao.deleteChunks(documentId) }
    }

    override suspend fun markOcrPageStarted(documentId: String, page: Int) {
        withContext(dispatchers.io) {
            documentDao.markOcrPageStarted(
                id = documentId,
                page = page,
                status = ExtractionStatus.OCR_IN_PROGRESS.name,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun commitOcrPage(
        documentId: String,
        page: Int,
        text: String,
        confidence: Float?,
    ) {
        withContext(dispatchers.io) {
            documentDao.commitOcrPage(
                documentId = documentId,
                pageNumber = page,
                text = text,
                confidence = confidence,
                nowEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun updateCompiledText(
        documentId: String,
        compiledTextPath: String,
        status: ExtractionStatus,
    ) {
        withContext(dispatchers.io) {
            documentDao.updateCompiledText(
                id = documentId,
                path = compiledTextPath,
                status = status.name,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching { File(path).delete() }
    }
}

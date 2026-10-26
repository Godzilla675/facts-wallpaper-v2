package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

sealed interface PdfCommitOutcome {
    data class Success(val document: PdfCommittedDocument) : PdfCommitOutcome
    data class Failure(val error: PdfImportError) : PdfCommitOutcome
}

/**
 * Multi-source import pipeline (F2): copy to private storage while hashing,
 * inspect, detect SHA-256 duplicates, then commit the staged document with its
 * persisted settings. Staged files survive duplicate resolution without being
 * copied twice.
 */
interface PdfImportRepository {
    suspend fun stageLocal(
        uri: String,
        displayName: String? = null,
        policy: DuplicatePolicy = DuplicatePolicy.ASK,
    ): PdfImportResult

    suspend fun stageUrl(
        url: String,
        policy: DuplicatePolicy = DuplicatePolicy.ASK,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): PdfImportResult

    suspend fun resolveDuplicate(stagedId: String, policy: DuplicatePolicy): PdfImportResult

    suspend fun commit(
        stagedId: String,
        options: PdfProcessingOptions,
        route: PdfProcessingRoute,
    ): PdfCommitOutcome

    suspend fun discard(stagedId: String)
}

@Singleton
class DefaultPdfImportRepository @Inject constructor(
    private val fileStore: PdfFileStore,
    private val analyzer: PdfAnalyzer,
    private val downloader: PdfUrlDownloader,
    private val documentRepository: DocumentRepository,
    private val scheduler: DocumentProcessingScheduler,
    private val dispatchers: FactsDispatchers,
) : PdfImportRepository {

    private val staged = ConcurrentHashMap<String, StagedPdf>()

    override suspend fun stageLocal(
        uri: String,
        displayName: String?,
        policy: DuplicatePolicy,
    ): PdfImportResult = withContext(dispatchers.io) {
        fileStore.pruneStaging()
        val copy = try {
            fileStore.copyFromUri(uri, displayName)
        } catch (error: PdfImportException) {
            return@withContext PdfImportResult.Failure(
                PdfImportError(error.code, error.message, error.cause),
            )
        }
        finishStaging(
            copy = copy,
            sourceUri = uri,
            policy = policy,
        )
    }

    override suspend fun stageUrl(
        url: String,
        policy: DuplicatePolicy,
        onProgress: (Long, Long?) -> Unit,
    ): PdfImportResult = withContext(dispatchers.io) {
        fileStore.pruneStaging()
        val destination = fileStore.newStagingFile()
        val downloaded = try {
            downloader.download(url, destination, onProgress)
        } catch (error: PdfDownloadException) {
            destination.delete()
            return@withContext PdfImportResult.Failure(
                PdfImportError(error.code, error.message, error.cause),
            )
        }
        val copy = StagedCopy(
            file = downloaded.file,
            sha256 = downloaded.sha256,
            sizeBytes = downloaded.sizeBytes,
            displayName = downloaded.finalUrl.substringAfterLast('/').substringBefore('?')
                .takeIf { it.endsWith(".pdf", ignoreCase = true) },
            contentType = downloaded.contentType,
        )
        finishStaging(
            copy = copy,
            sourceUri = url,
            policy = policy,
        )
    }

    override suspend fun resolveDuplicate(
        stagedId: String,
        policy: DuplicatePolicy,
    ): PdfImportResult = withContext(dispatchers.io) {
        val document = staged[stagedId]
            ?: return@withContext PdfImportResult.Failure(
                PdfImportError(PdfImportErrorCode.STORAGE_FAILED, "Unknown staged import"),
            )
        val existingId = document.duplicateOfDocumentId
        when (policy) {
            DuplicatePolicy.OPEN_EXISTING -> {
                if (existingId == null) {
                    PdfImportResult.Failure(
                        PdfImportError(PdfImportErrorCode.STORAGE_FAILED, "No duplicate to open"),
                    )
                } else {
                    val title = document.duplicateOfTitle ?: "Document"
                    staged.remove(stagedId)
                    fileStore.deleteStaged(document.filePath)
                    PdfImportResult.Existing(existingId, title)
                }
            }

            DuplicatePolicy.REPLACE_EXISTING -> PdfImportResult.Ready(document)

            DuplicatePolicy.IMPORT_AS_COPY -> {
                val copy = document.copy(
                    duplicateOfDocumentId = null,
                    duplicateOfTitle = null,
                )
                staged[stagedId] = copy
                PdfImportResult.Ready(copy)
            }

            DuplicatePolicy.ASK -> PdfImportResult.Duplicate(
                staged = document,
                existingDocumentId = existingId.orEmpty(),
                existingTitle = document.duplicateOfTitle.orEmpty(),
            )
        }
    }

    override suspend fun commit(
        stagedId: String,
        options: PdfProcessingOptions,
        route: PdfProcessingRoute,
    ): PdfCommitOutcome = withContext(dispatchers.io) {
        val stagedDocument = staged[stagedId]
            ?: return@withContext PdfCommitOutcome.Failure(
                PdfImportError(PdfImportErrorCode.STORAGE_FAILED, "Staged PDF not found"),
            )
        val existing = stagedDocument.duplicateOfDocumentId?.let { documentId ->
            documentRepository.getDocument(documentId)
        }
        val documentId = existing?.id ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val target = if (existing != null) {
            fileStore.replacementDocumentFile(documentId)
        } else {
            fileStore.documentFile(documentId)
        }
        // Stop the previous unique job before its PDF path is replaced. The
        // pipeline's path guard also protects the window until cancellation lands.
        if (existing != null) {
            scheduler.cancel(documentId)
        }

        val moved = try {
            fileStore.moveStaged(stagedDocument, target)
        } catch (error: Throwable) {
            return@withContext PdfCommitOutcome.Failure(
                PdfImportError(
                    code = PdfImportErrorCode.STORAGE_FAILED,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        val safeRange = options.pageRange.coerceTo(stagedDocument.metadata.pageCount)
        val document = Document(
            id = documentId,
            title = stagedDocument.metadata.title,
            sourceUri = stagedDocument.metadata.sourceUri,
            localFilePath = moved.absolutePath,
            compiledOcrTextPath = null,
            fileHash = stagedDocument.metadata.fileHash,
            fileSizeBytes = stagedDocument.metadata.fileSizeBytes,
            pageCount = stagedDocument.metadata.pageCount,
            pagesWithText = stagedDocument.metadata.pagesWithExtractableText,
            processingRoute = route,
            ocrEngine = if (route == PdfProcessingRoute.LOCAL_OCR ||
                route == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE
            ) {
                options.ocrEngine
            } else {
                null
            },
            ocrLanguageCode = options.ocrLanguageCode,
            ocrLastStartedPage = 0,
            ocrLastCompletedPage = 0,
            extractionStatus = ExtractionStatus.IMPORTED,
            lastErrorMessage = null,
            includedInRotation = existing?.includedInRotation ?: true,
            createdAtEpochMs = existing?.createdAtEpochMs ?: now,
            updatedAtEpochMs = now,
        )
        val settings = DocumentSettings(
            documentId = documentId,
            pageRangeStart = safeRange.startPage,
            pageRangeEnd = safeRange.endPage,
            forceFullLocalOcr = options.forceFullLocalOcr,
            preferDirectPdfUpload = options.preferDirectPdfUpload,
            overrideOcrPageLimit = options.overrideOcrPageLimit,
            ocrEngine = options.ocrEngine,
            ocrLanguageCode = options.ocrLanguageCode,
            ocrExecutionMode = options.ocrExecutionMode,
            factDensity = options.factDensity,
            includeFormulas = true,
            includeDiagrams = true,
            autoVerifyFormulas = true,
            preferredProviderProfileId = options.preferredProviderProfileId,
        )

        try {
            if (existing != null) {
                documentRepository.replaceDocumentWithSettings(document, settings)
            } else {
                documentRepository.saveDocumentWithSettings(document, settings)
            }
        } catch (error: Throwable) {
            moved.delete()
            staged.remove(stagedId)
            return@withContext PdfCommitOutcome.Failure(
                PdfImportError(
                    code = PdfImportErrorCode.STORAGE_FAILED,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        staged.remove(stagedId)
        if (existing != null) {
            if (existing.localFilePath != moved.absolutePath) {
                runCatching { File(existing.localFilePath).delete() }
            }
            fileStore.deleteCompiledText(existing.compiledOcrTextPath)
        }
        PdfCommitOutcome.Success(
            PdfCommittedDocument(
                documentId = documentId,
                title = document.title,
                route = route,
            ),
        )
    }

    override suspend fun discard(stagedId: String) {
        staged.remove(stagedId)?.let { fileStore.deleteStaged(it.filePath) }
    }

    private suspend fun finishStaging(
        copy: StagedCopy,
        sourceUri: String,
        policy: DuplicatePolicy,
    ): PdfImportResult {
        val metadata = try {
            analyzer.inspect(
                file = copy.file,
                sourceUri = sourceUri,
                displayName = copy.displayName,
                fileHash = copy.sha256,
            )
        } catch (error: PdfOpenException) {
            copy.file.delete()
            return PdfImportResult.Failure(
                PdfImportError(error.code, error.message, error.cause),
            )
        } catch (error: Throwable) {
            copy.file.delete()
            return PdfImportResult.Failure(
                PdfImportError(
                    code = PdfImportErrorCode.INSPECTION_FAILED,
                    detail = error.message,
                    cause = error,
                ),
            )
        }

        val duplicate = documentRepository.findByHash(copy.sha256)
        val stagedId = UUID.randomUUID().toString()
        val stagedDocument = StagedPdf(
            id = stagedId,
            filePath = copy.file.absolutePath,
            displayName = copy.displayName,
            metadata = metadata,
            duplicateOfDocumentId = duplicate?.id,
            duplicateOfTitle = duplicate?.title,
        )
        staged[stagedId] = stagedDocument

        return when {
            duplicate == null -> PdfImportResult.Ready(stagedDocument)
            policy == DuplicatePolicy.ASK -> PdfImportResult.Duplicate(
                staged = stagedDocument,
                existingDocumentId = duplicate.id,
                existingTitle = duplicate.title,
            )

            policy == DuplicatePolicy.OPEN_EXISTING -> {
                staged.remove(stagedId)
                copy.file.delete()
                PdfImportResult.Existing(duplicate.id, duplicate.title)
            }

            policy == DuplicatePolicy.REPLACE_EXISTING -> PdfImportResult.Ready(stagedDocument)

            else -> {
                val asCopy = stagedDocument.copy(
                    duplicateOfDocumentId = null,
                    duplicateOfTitle = null,
                )
                staged[stagedId] = asCopy
                PdfImportResult.Ready(asCopy)
            }
        }
    }
}

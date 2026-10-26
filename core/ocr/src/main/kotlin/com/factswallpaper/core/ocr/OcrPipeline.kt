package com.factswallpaper.core.ocr

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** Live page-level progress reported while the pipeline advances a checkpoint. */
data class OcrPageProgress(
    val page: Int,
    val rangeStart: Int,
    val rangeEnd: Int,
    val completedPages: Int,
    val totalPages: Int,
    val isPageFinished: Boolean,
) {
    val fraction: Float
        get() = if (totalPages <= 0) 0f
        else (completedPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)
}

sealed interface OcrPipelineOutcome {
    data class Completed(
        val compiledTextPath: String,
        val pagesProcessed: Int,
    ) : OcrPipelineOutcome

    data class Paused(
        val nextPage: Int,
        val completedPages: Int,
    ) : OcrPipelineOutcome

    data class Failed(
        val error: Throwable,
        val completedPages: Int,
    ) : OcrPipelineOutcome

    /**
     * The document was deleted or replaced (a new private PDF path was written)
     * while this job was running. No state was written for the stale job.
     */
    data object Superseded : OcrPipelineOutcome
}

interface OcrPipeline {
    /**
     * Runs checkpointed OCR for [documentId]. A cancellation (WorkManager pause,
     * user cancel or process stop) leaves the document in `OCR_PAUSED` and keeps
     * every committed page, so the next invocation restarts only the page that
     * was interrupted.
     */
    suspend fun process(
        documentId: String,
        stopRequested: () -> Boolean = { false },
        onProgress: suspend (OcrPageProgress) -> Unit = {},
    ): OcrPipelineOutcome
}

@Singleton
class DefaultOcrPipeline @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val pageRenderer: OcrPageRenderer,
    private val engineFactory: OcrEngineFactory,
    private val textFileCompiler: OcrTextFileCompiler,
    private val dispatchers: FactsDispatchers,
) : OcrPipeline {

    override suspend fun process(
        documentId: String,
        stopRequested: () -> Boolean,
        onProgress: suspend (OcrPageProgress) -> Unit,
    ): OcrPipelineOutcome = withContext(dispatchers.io) {
        val document = documentRepository.getDocument(documentId)
            ?: return@withContext OcrPipelineOutcome.Superseded
        val ownedFilePath = document.localFilePath
        val settings = documentRepository.getSettings(documentId)
            ?: DocumentSettings(
                documentId = documentId,
                pageRangeEnd = document.pageCount.coerceAtLeast(1),
                ocrLanguageCode = document.ocrLanguageCode,
            )

        if (document.processingRoute == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE &&
            !settings.overrideOcrPageLimit &&
            !settings.forceFullLocalOcr
        ) {
            val overrideError = IllegalStateException(
                "Scanned PDF exceeds 20 pages; user override required before running local OCR.",
            )
            if (stillOwns(documentId, ownedFilePath)) {
                documentRepository.updateExtractionStatus(
                    documentId,
                    ExtractionStatus.ERROR,
                    overrideError.message,
                )
            }
            return@withContext OcrPipelineOutcome.Failed(overrideError, 0)
        }

        val resume = OcrCheckpointResolver.resolve(document, settings)

        if (resume.alreadyComplete) {
            return@withContext finish(documentId, document, resume, resume.completedPages)
        }

        val language = OcrLanguageCatalog.forCode(settings.ocrLanguageCode)
        val engine = try {
            engineFactory.create(language)
        } catch (error: OcrEngineUnavailableException) {
            if (stillOwns(documentId, ownedFilePath)) {
                documentRepository.updateExtractionStatus(
                    documentId,
                    ExtractionStatus.ERROR,
                    error.message,
                )
            }
            return@withContext OcrPipelineOutcome.Failed(error, resume.completedPages)
        }

        var completedPages = resume.completedPages
        try {
            for (page in resume.resumeFromPage..resume.rangeEnd) {
                coroutineContext.ensureActive()
                if (!stillOwns(documentId, ownedFilePath)) {
                    return@withContext OcrPipelineOutcome.Superseded
                }
                if (stopRequested()) {
                    markPaused(documentId, ownedFilePath)
                    return@withContext OcrPipelineOutcome.Paused(page, completedPages)
                }

                onProgress(
                    OcrPageProgress(
                        page = page,
                        rangeStart = resume.rangeStart,
                        rangeEnd = resume.rangeEnd,
                        completedPages = completedPages,
                        totalPages = resume.totalPages,
                        isPageFinished = false,
                    ),
                )
                documentRepository.markOcrPageStarted(documentId, page)
                val previous = documentRepository
                    .getChunks(documentId, page, page)
                    .firstOrNull()

                val isInterruptedRestart = resume.restartsInterruptedPage && page == resume.resumeFromPage
                val hasDigitalText = !settings.forceFullLocalOcr &&
                    !isInterruptedRestart &&
                    previous != null &&
                    !previous.isFromOcr &&
                    previous.status == STATUS_COMPLETED &&
                    previous.extractedText.count { !it.isWhitespace() } >= MIN_TEXT_CHARS_PER_PAGE

                if (hasDigitalText && previous != null) {
                    if (!stillOwns(documentId, ownedFilePath)) {
                        return@withContext OcrPipelineOutcome.Superseded
                    }
                    documentRepository.commitOcrPage(
                        documentId = documentId,
                        page = page,
                        text = previous.extractedText,
                        confidence = previous.ocrConfidence,
                    )
                    // Preserve the non-OCR provenance flag on the retained digital text chunk
                    documentRepository.upsertChunk(
                        previous.copy(
                            status = STATUS_COMPLETED,
                            isFromOcr = false,
                            updatedAtEpochMs = System.currentTimeMillis(),
                        ),
                    )
                    completedPages += 1
                    onProgress(
                        OcrPageProgress(
                            page = page,
                            rangeStart = resume.rangeStart,
                            rangeEnd = resume.rangeEnd,
                            completedPages = completedPages,
                            totalPages = resume.totalPages,
                            isPageFinished = true,
                        ),
                    )
                    continue
                }

                documentRepository.upsertChunk(
                    DocumentChunk(
                        documentId = documentId,
                        pageNumber = page,
                        extractedText = previous?.extractedText.orEmpty(),
                        status = STATUS_IN_PROGRESS,
                        isFromOcr = true,
                        ocrConfidence = previous?.ocrConfidence,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )

                val result = pageRenderer.render(ownedFilePath, page).use { image ->
                    engine.recognize(image, language)
                }
                // A replacement import writes a new versioned PDF path. Never
                // commit this stale page or advance the new document's marker.
                if (!stillOwns(documentId, ownedFilePath)) {
                    return@withContext OcrPipelineOutcome.Superseded
                }
                documentRepository.commitOcrPage(
                    documentId = documentId,
                    page = page,
                    text = result.text,
                    confidence = result.confidence,
                )
                completedPages += 1
                onProgress(
                    OcrPageProgress(
                        page = page,
                        rangeStart = resume.rangeStart,
                        rangeEnd = resume.rangeEnd,
                        completedPages = completedPages,
                        totalPages = resume.totalPages,
                        isPageFinished = true,
                    ),
                )
            }

            if (stopRequested()) {
                markPaused(documentId, ownedFilePath)
                return@withContext OcrPipelineOutcome.Paused(
                    resume.rangeEnd,
                    completedPages,
                )
            }
            finish(documentId, document, resume, completedPages)
        } catch (cancel: CancellationException) {
            markPaused(documentId, ownedFilePath)
            throw cancel
        } catch (error: Throwable) {
            if (stillOwns(documentId, ownedFilePath)) {
                documentRepository.updateExtractionStatus(
                    documentId,
                    ExtractionStatus.ERROR,
                    error.message,
                )
            }
            OcrPipelineOutcome.Failed(error, completedPages)
        } finally {
            runCatching { engine.close() }
        }
    }

    private suspend fun stillOwns(documentId: String, localFilePath: String): Boolean =
        documentRepository.getLocalFilePath(documentId) == localFilePath

    private suspend fun finish(
        documentId: String,
        document: com.factswallpaper.core.model.Document,
        resume: OcrResumePoint,
        completedPages: Int,
    ): OcrPipelineOutcome {
        if (!stillOwns(documentId, document.localFilePath)) {
            return OcrPipelineOutcome.Superseded
        }
        val compiled = textFileCompiler.compile(
            document = document,
            rangeStart = resume.rangeStart,
            rangeEnd = resume.rangeEnd,
        )
        documentRepository.updateCompiledText(
            documentId = documentId,
            compiledTextPath = compiled.absolutePath,
            status = ExtractionStatus.OCR_DONE,
        )
        return OcrPipelineOutcome.Completed(
            compiledTextPath = compiled.absolutePath,
            pagesProcessed = completedPages,
        )
    }

    private suspend fun markPaused(documentId: String, localFilePath: String) {
        if (!stillOwns(documentId, localFilePath)) return
        runCatching {
            documentRepository.updateExtractionStatus(documentId, ExtractionStatus.OCR_PAUSED)
        }
    }

    private companion object {
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val MIN_TEXT_CHARS_PER_PAGE = 40
    }
}

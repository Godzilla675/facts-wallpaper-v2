package com.factswallpaper.core.pdf

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.ocr.OcrEngineUnavailableException
import com.factswallpaper.core.ocr.OcrPipeline
import com.factswallpaper.core.ocr.OcrPipelineOutcome
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * Durable, checkpointed processor for every import route. One unique job exists
 * per document; process death and WorkManager restarts resume from Room state.
 */
@HiltWorker
class PdfProcessingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val textProcessor: PdfTextExtractionProcessor,
    private val directProcessor: PdfDirectPdfProcessor,
    private val ocrPipeline: OcrPipeline,
    private val notifications: PdfNotifications,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(KEY_DOCUMENT_ID)
            ?: return Result.failure(errorData("Missing document id"))
        val document = documentRepository.getDocument(documentId)
            ?: return Result.success()
        val settings = documentRepository.getSettings(documentId)
            ?: defaultSettings(document)

        return when (document.processingRoute) {
            PdfProcessingRoute.EXTRACT_TEXT -> runTextExtraction(document, settings)
            PdfProcessingRoute.DIRECT_PDF -> runDirectPdf(document, settings)
            PdfProcessingRoute.LOCAL_OCR,
            PdfProcessingRoute.OCR_REQUIRES_OVERRIDE,
            -> runOcr(document, settings)
        }
    }

    private suspend fun runTextExtraction(
        document: Document,
        settings: DocumentSettings,
    ): Result = try {
        val pages = textProcessor.extract(document, settings) { progress ->
            setProgress(
                progressData(
                    page = progress.page,
                    rangeEnd = progress.rangeEnd,
                    completedPages = progress.completedPages,
                    stage = STAGE_TEXT,
                ),
            )
        }
        Result.success(workDataOf(KEY_COMPLETED_PAGES to pages))
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        handleFailure(document, error)
    }

    private suspend fun runDirectPdf(
        document: Document,
        settings: DocumentSettings,
    ): Result = try {
        val preparation = directProcessor.prepare(document, settings)
        setProgress(
            progressData(
                page = preparation.range.endPage,
                rangeEnd = preparation.range.endPage,
                completedPages = preparation.range.pageCount,
                stage = STAGE_DIRECT,
            ),
        )
        Result.success()
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Throwable) {
        handleFailure(document, error)
    }

    private suspend fun runOcr(
        document: Document,
        settings: DocumentSettings,
    ): Result {
        if (document.processingRoute == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE &&
            !settings.overrideOcrPageLimit
        ) {
            val message = applicationContext.getString(R.string.pdf_error_override_required)
            documentRepository.updateExtractionStatus(document.id, ExtractionStatus.ERROR, message)
            return Result.failure(errorData(message))
        }

        setForeground(notifications.ocrForeground(document))
        val outcome = ocrPipeline.process(
            documentId = document.id,
            stopRequested = { isStopped },
            onProgress = { progress ->
                setProgress(progressData(progress))
                notifications.notifyOcrProgress(document, progress)
                setForeground(notifications.ocrForeground(document, progress))
            },
        )
        return when (outcome) {
            is OcrPipelineOutcome.Completed -> Result.success(
                workDataOf(KEY_COMPLETED_PAGES to outcome.pagesProcessed),
            )

            is OcrPipelineOutcome.Paused -> Result.success(
                workDataOf(KEY_COMPLETED_PAGES to outcome.completedPages),
            )

            OcrPipelineOutcome.Superseded -> Result.success()

            is OcrPipelineOutcome.Failed -> {
                val message = outcome.error.message
                    ?: applicationContext.getString(R.string.pdf_error_ocr_failed)
                documentRepository.updateExtractionStatus(
                    document.id,
                    ExtractionStatus.ERROR,
                    message,
                )
                when {
                    outcome.error is OcrEngineUnavailableException -> Result.failure(errorData(message))
                    runAttemptCount < MAX_RETRIES -> Result.retry()
                    else -> Result.failure(errorData(message))
                }
            }
        }
    }

    private suspend fun handleFailure(document: Document, error: Throwable): Result {
        val message = error.message
            ?: applicationContext.getString(R.string.pdf_error_processing_failed)
        documentRepository.updateExtractionStatus(
            document.id,
            ExtractionStatus.ERROR,
            message,
        )
        val permanent = error is PdfOpenException
        return if (!permanent && runAttemptCount < MAX_RETRIES) {
            Result.retry()
        } else {
            Result.failure(errorData(message))
        }
    }

    private fun defaultSettings(document: Document) = DocumentSettings(
        documentId = document.id,
        pageRangeEnd = document.pageCount.coerceAtLeast(1),
        ocrEngine = document.ocrEngine ?: OcrEngineType.ML_KIT,
        ocrLanguageCode = document.ocrLanguageCode,
    )

    private fun progressData(
        page: Int,
        rangeEnd: Int,
        completedPages: Int,
        stage: String,
    ) = workDataOf(
        KEY_PAGE to page,
        KEY_PAGE_COUNT to rangeEnd,
        KEY_COMPLETED_PAGES to completedPages,
        KEY_STAGE to stage,
    )

    private fun progressData(progress: com.factswallpaper.core.ocr.OcrPageProgress) =
        progressData(
            page = progress.page,
            rangeEnd = progress.rangeEnd,
            completedPages = progress.completedPages,
            stage = STAGE_OCR,
        )

    private fun errorData(message: String) = workDataOf(KEY_ERROR_MESSAGE to message)

    companion object {
        const val KEY_DOCUMENT_ID = "document_id"
        const val KEY_PAGE = "page"
        const val KEY_PAGE_COUNT = "page_count"
        const val KEY_COMPLETED_PAGES = "completed_pages"
        const val KEY_STAGE = "stage"
        const val KEY_ERROR_MESSAGE = "error_message"

        const val STAGE_OCR = "OCR"
        const val STAGE_TEXT = "TEXT"
        const val STAGE_DIRECT = "DIRECT_PDF"

        private const val MAX_RETRIES = 3
    }
}

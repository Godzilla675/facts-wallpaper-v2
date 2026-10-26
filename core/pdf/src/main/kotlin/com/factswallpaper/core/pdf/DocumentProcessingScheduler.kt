package com.factswallpaper.core.pdf

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.ExtractionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the unique durable job for each document (spec 11.5). Starting uses
 * KEEP so a live/restarted job is never duplicated; resuming explicitly uses
 * REPLACE because the paused job has already been cancelled.
 */
@Singleton
open class DocumentProcessingScheduler internal constructor(
    private val context: Context?,
    private val documentRepository: DocumentRepository,
    @Suppress("UNUSED_PARAMETER") marker: Any?,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        documentRepository: DocumentRepository,
    ) : this(context, documentRepository, null)

    protected constructor(documentRepository: DocumentRepository) : this(null, documentRepository, null)

    private val workManager: WorkManager
        get() = WorkManager.getInstance(checkNotNull(context) { "Android Context required for WorkManager" })

    open fun enqueue(documentId: String) {
        workManager.enqueueUniqueWork(
            workName(documentId),
            ExistingWorkPolicy.KEEP,
            request(documentId),
        )
    }

    open fun resume(documentId: String) {
        workManager.enqueueUniqueWork(
            workName(documentId),
            ExistingWorkPolicy.REPLACE,
            request(documentId),
        )
    }

    /** Pauses OCR: the committed pages are kept and the next run restarts the interrupted page. */
    open suspend fun pause(documentId: String) {
        documentRepository.updateExtractionStatus(documentId, ExtractionStatus.OCR_PAUSED)
        workManager.cancelUniqueWork(workName(documentId))
    }

    /** Cancels active work without changing the persisted checkpoint (used by delete). */
    open fun cancel(documentId: String) {
        workManager.cancelUniqueWork(workName(documentId))
    }

    private fun request(documentId: String) = OneTimeWorkRequestBuilder<PdfProcessingWorker>()
        .setInputData(
            Data.Builder()
                .putString(PdfProcessingWorker.KEY_DOCUMENT_ID, documentId)
                .build(),
        )
        .addTag(TAG)
        .addTag(workName(documentId))
        .build()

    companion object {
        const val TAG = "pdf_processing"

        fun workName(documentId: String): String = "pdf-processing-$documentId"
    }
}

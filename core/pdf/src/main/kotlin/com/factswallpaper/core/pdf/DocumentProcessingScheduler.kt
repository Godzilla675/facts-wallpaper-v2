package com.factswallpaper.core.pdf

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.pipeline.PipelineScheduler
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.ExtractionStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Owns the unique durable job for each document (spec 11.5). Starting uses
 * KEEP so a live/restarted job is never duplicated; resuming explicitly uses
 * REPLACE because the paused job has already been cancelled.
 * Also coordinates pipeline stage handoffs (import -> outline -> facts -> render).
 */
@Singleton
open class DocumentProcessingScheduler internal constructor(
    private val context: Context?,
    private val documentRepository: DocumentRepository,
    private val lessonRepository: LessonRepository?,
    private val dispatchers: FactsDispatchers,
    @Suppress("UNUSED_PARAMETER") marker: Any?,
) : PipelineScheduler {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        documentRepository: DocumentRepository,
        lessonRepository: LessonRepository,
        dispatchers: FactsDispatchers,
    ) : this(context, documentRepository, lessonRepository, dispatchers, null)

    constructor(
        documentRepository: DocumentRepository,
        lessonRepository: LessonRepository? = null,
    ) : this(
        context = null,
        documentRepository = documentRepository,
        lessonRepository = lessonRepository,
        dispatchers = FactsDispatchers(
            io = kotlinx.coroutines.Dispatchers.Unconfined,
            default = kotlinx.coroutines.Dispatchers.Unconfined,
            main = kotlinx.coroutines.Dispatchers.Unconfined,
        ),
        marker = null,
    )

    private val scope = CoroutineScope(dispatchers.io)

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
        scope.launch {
            val document = documentRepository.getDocument(documentId) ?: return@launch
            when (document.extractionStatus) {
                ExtractionStatus.OUTLINE_READY,
                ExtractionStatus.FACTS_IN_PROGRESS -> {
                    val lessons = lessonRepository?.getLessons(documentId) ?: emptyList()
                    val targetLessons = lessons.filter { it.selected }.ifEmpty { lessons }
                    enqueueFacts(documentId, targetLessons.map { it.id })
                }
                ExtractionStatus.ANALYZED,
                ExtractionStatus.OCR_DONE,
                ExtractionStatus.OUTLINE_IN_PROGRESS -> {
                    enqueueOutline(documentId)
                }
                else -> {
                    workManager.enqueueUniqueWork(
                        workName(documentId),
                        ExistingWorkPolicy.REPLACE,
                        request(documentId),
                    )
                }
            }
        }
    }

    /** Pauses OCR: the committed pages are kept and the next run restarts the interrupted page. */
    open suspend fun pause(documentId: String) {
        documentRepository.updateExtractionStatus(documentId, ExtractionStatus.OCR_PAUSED)
        workManager.cancelUniqueWork(workName(documentId))
        workManager.cancelUniqueWork(outlineWorkName(documentId))
        workManager.cancelUniqueWork(factsWorkName(documentId))
    }

    /** Cancels active work without changing the persisted checkpoint (used by delete). */
    open fun cancel(documentId: String) {
        workManager.cancelUniqueWork(workName(documentId))
        workManager.cancelUniqueWork(outlineWorkName(documentId))
        workManager.cancelUniqueWork(factsWorkName(documentId))
    }

    override fun enqueueOutline(documentId: String) {
        val request = OneTimeWorkRequestBuilder<OutlineGenerationWorker>()
            .setInputData(
                Data.Builder()
                    .putString(OutlineWork.KEY_DOCUMENT_ID, documentId)
                    .build(),
            )
            .addTag(TAG_OUTLINE)
            .addTag(outlineWorkName(documentId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            outlineWorkName(documentId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun enqueueFacts(documentId: String, lessonIds: List<String>) {
        if (lessonIds.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<com.factswallpaper.core.data.ai.FactsGenerationWorker>()
            .setInputData(
                Data.Builder()
                    .putString(com.factswallpaper.core.data.ai.FactsWork.KEY_DOCUMENT_ID, documentId)
                    .putStringArray(com.factswallpaper.core.data.ai.FactsWork.KEY_LESSON_IDS, lessonIds.toTypedArray())
                    .build(),
            )
            .addTag(TAG_FACTS)
            .addTag(factsWorkName(documentId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()

        workManager.enqueueUniqueWork(
            factsWorkName(documentId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun onExtractionCompleted(documentId: String) {
        scope.launch {
            val settings = documentRepository.getSettings(documentId)
            val autoMode = settings?.autoProcessMode ?: AutoProcessMode.OUTLINE_ONLY
            when (autoMode) {
                AutoProcessMode.OUTLINE_ONLY,
                AutoProcessMode.OUTLINE_AND_GENERATE_ALL -> {
                    documentRepository.updatePipelineStatus(
                        documentId = documentId,
                        status = ExtractionStatus.OUTLINE_IN_PROGRESS,
                        progress = 0.4f,
                    )
                    enqueueOutline(documentId)
                }
                AutoProcessMode.ASK_ME -> {
                    documentRepository.updatePipelineStatus(
                        documentId = documentId,
                        status = ExtractionStatus.ANALYZED,
                        progress = 1.0f,
                    )
                }
            }
        }
    }

    override fun onOutlineCompleted(documentId: String) {
        scope.launch {
            val settings = documentRepository.getSettings(documentId)
            val autoMode = settings?.autoProcessMode ?: AutoProcessMode.OUTLINE_ONLY
            if (autoMode == AutoProcessMode.OUTLINE_AND_GENERATE_ALL) {
                val lessons = lessonRepository?.getLessons(documentId) ?: emptyList()
                val targetLessons = lessons.filter { it.selected }.ifEmpty { lessons }
                if (targetLessons.isNotEmpty()) {
                    documentRepository.updatePipelineStatus(
                        documentId = documentId,
                        status = ExtractionStatus.FACTS_IN_PROGRESS,
                        progress = 0.65f,
                    )
                    enqueueFacts(documentId, targetLessons.map { it.id })
                    return@launch
                }
            }
            documentRepository.updatePipelineStatus(
                documentId = documentId,
                status = ExtractionStatus.OUTLINE_READY,
                progress = 0.6f,
            )
        }
    }

    override fun onFactsCompleted(documentId: String, createdFactsCount: Int) {
        scope.launch {
            documentRepository.updatePipelineStatus(
                documentId = documentId,
                status = ExtractionStatus.COMPLETED,
                progress = 1.0f,
            )
        }
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
        const val TAG_OUTLINE = "outline_processing"
        const val TAG_FACTS = "facts_processing"

        fun workName(documentId: String): String = "pdf-processing-$documentId"
        fun outlineWorkName(documentId: String): String = "outline-generation-$documentId"
        fun factsWorkName(documentId: String): String = "facts-generation-$documentId"
    }
}

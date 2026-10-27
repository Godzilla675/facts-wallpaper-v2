package com.factswallpaper.core.pdf

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.data.pipeline.PipelineNotifier
import com.factswallpaper.core.data.pipeline.PipelineScheduler
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.DocumentTextRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Lesson
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.util.UUID

object OutlineWork {
    const val KEY_DOCUMENT_ID = "documentId"
    const val KEY_PROFILE_ID = "profileId"
    const val KEY_PHASE = "phase"
    const val KEY_COMPLETED = "completed"
    const val KEY_TOTAL = "total"
    const val KEY_MESSAGE = "message"
    const val KEY_ESTIMATED_COST = "estimatedCostUsd"
    const val KEY_ACTUAL_COST = "actualCostUsd"
    const val KEY_LESSON_COUNT = "lessonCount"
    const val KEY_WARNINGS = "warnings"
    const val KEY_PROVIDER = "provider"

    fun uniqueName(documentId: String): String = "outline_$documentId"
}

/**
 * Durable outline detection keyed by document id. Promoted to foreground dataSync service.
 * Existing lessons whose page range still matches keep their ids so facts stay attached;
 * the rest are replaced in one transaction after the AI response validates.
 */
@HiltWorker
class OutlineGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val documentTextRepository: DocumentTextRepository,
    private val lessonRepository: LessonRepository,
    private val structuredAiService: StructuredAiService,
    private val pdfBoxDocumentReader: PdfBoxDocumentReader,
    private val pipelineNotifier: PipelineNotifier,
    private val pipelineScheduler: PipelineScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(OutlineWork.KEY_DOCUMENT_ID)
            ?: return Result.failure(workDataOf(OutlineWork.KEY_MESSAGE to "Missing document id."))
        val manualProfileId = inputData.getString(OutlineWork.KEY_PROFILE_ID)
        val document = documentRepository.getDocument(documentId)
            ?: return Result.failure(workDataOf(OutlineWork.KEY_MESSAGE to "Document not found."))
        val settings = documentRepository.getSettings(documentId)
        val startPage = settings?.pageRangeStart ?: 1
        val endPage = settings?.pageRangeEnd ?: document.pageCount
        val pages = documentTextRepository.getPages(documentId, startPage, endPage)
        if (pages.isEmpty()) {
            return Result.failure(
                workDataOf(
                    OutlineWork.KEY_MESSAGE to
                        "No extracted text is available for pages $startPage–$endPage. Run OCR first.",
                ),
            )
        }

        runCatching {
            setForeground(
                pipelineNotifier.pipelineForeground(
                    documentId = documentId,
                    documentTitle = document.title,
                    stageText = "Detecting outline…",
                    progressFraction = 0.4f,
                ),
            )
        }
        documentRepository.updatePipelineStatus(
            documentId = documentId,
            status = ExtractionStatus.OUTLINE_IN_PROGRESS,
            progress = 0.4f,
        )

        setProgress(progress(phase = "starting", completed = 0, total = pages.size))
        return try {
            val bookmarkWarnings = mutableListOf<String>()
            val bookmarkLessons = try {
                BookmarkOutlineMapper.map(
                    bookmarks = pdfBoxDocumentReader.readBookmarks(File(document.localFilePath)),
                    pageRangeStart = startPage,
                    pageRangeEnd = endPage,
                )
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                bookmarkWarnings += "Could not read embedded PDF bookmarks; using AI outline detection."
                emptyList()
            }
            val result = if (bookmarkLessons.size >= 2) {
                null
            } else {
                structuredAiService.generateOutline(
                    documentId = documentId,
                    pages = pages,
                    manualProfileId = manualProfileId ?: settings?.preferredProviderProfileId,
                    progress = { completed, total ->
                        val fraction = if (total > 0) completed.toFloat() / total.toFloat() else 0f
                        val pipelineProg = (0.4f + fraction * 0.2f).coerceIn(0.4f, 0.6f)
                        runCatching {
                            pipelineNotifier.notifyProgress(
                                documentId = documentId,
                                documentTitle = document.title,
                                stageText = "Detecting outline: $completed/$total",
                                progressFraction = pipelineProg,
                            )
                        }
                        setProgressAsync(
                            progress(
                                phase = "detecting",
                                completed = completed,
                                total = total,
                                estimatedCostUsd = null,
                            ),
                        )
                    },
                )
            }
            val detectedLessons = result?.lessons ?: bookmarkLessons
            val warnings = bookmarkWarnings + result?.warnings.orEmpty()
            val estimatedCost = result?.estimatedCostUsd ?: 0.0
            val actualCost = result?.actualCostUsd ?: 0.0
            val providerName = result?.profile?.name ?: "PDF bookmarks"
            setProgress(
                progress(
                    phase = "saving",
                    completed = pages.size,
                    total = pages.size,
                    estimatedCostUsd = estimatedCost,
                ),
            )
            val existing = lessonRepository.getLessons(documentId)
            val lessons = detectedLessons.map { draft ->
                val match = existing.firstOrNull {
                    it.startPage == draft.startPage && it.endPage == draft.endPage
                }
                Lesson(
                    id = match?.id ?: UUID.randomUUID().toString(),
                    documentId = documentId,
                    title = draft.title,
                    summary = draft.summary,
                    chapterTitle = draft.chapterTitle,
                    startPage = draft.startPage,
                    endPage = draft.endPage,
                    selected = match?.selected ?: true,
                    sortOrder = draft.sortOrder,
                    factCount = match?.factCount ?: 0,
                )
            }
            lessonRepository.replaceLessons(documentId, lessons)
            documentRepository.updatePipelineProgress(documentId, 0.6f)
            documentRepository.updatePipelineStatus(
                documentId = documentId,
                status = ExtractionStatus.OUTLINE_READY,
                progress = 0.6f,
            )
            pipelineScheduler.onOutlineCompleted(documentId)

            Result.success(
                workDataOf(
                    OutlineWork.KEY_LESSON_COUNT to lessons.size,
                    OutlineWork.KEY_ESTIMATED_COST to estimatedCost,
                    OutlineWork.KEY_ACTUAL_COST to actualCost,
                    OutlineWork.KEY_PROVIDER to providerName,
                    OutlineWork.KEY_WARNINGS to warnings.joinToString("\n").take(2_000),
                    OutlineWork.KEY_MESSAGE to
                        "Detected ${lessons.size} lessons with $providerName.",
                ),
            )
        } catch (exception: Throwable) {
            if (exception is kotlinx.coroutines.CancellationException) throw exception
            val errorMsg = (exception.message ?: "Outline detection failed.").take(1_000)
            documentRepository.updatePipelineStatus(
                documentId = documentId,
                status = ExtractionStatus.ERROR,
                progress = 0.4f,
                errorMessage = errorMsg,
            )
            pipelineNotifier.notifyError(documentId, document.title, errorMsg)
            Result.failure(
                workDataOf(
                    OutlineWork.KEY_MESSAGE to errorMsg,
                ),
            )
        }
    }

    private fun progress(
        phase: String,
        completed: Int,
        total: Int,
        estimatedCostUsd: Double? = null,
    ) = workDataOf(
        OutlineWork.KEY_PHASE to phase,
        OutlineWork.KEY_COMPLETED to completed,
        OutlineWork.KEY_TOTAL to total,
        OutlineWork.KEY_ESTIMATED_COST to estimatedCostUsd,
    )
}

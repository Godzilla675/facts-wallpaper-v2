package com.factswallpaper.core.data.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.common.CardPreRenderNotifier
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.DocumentTextRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.model.VerificationStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.UUID
import kotlinx.coroutines.flow.first

object FactsWork {
    const val KEY_DOCUMENT_ID = "documentId"
    const val KEY_LESSON_IDS = "lessonIds"
    const val KEY_PROFILE_ID = "profileId"
    const val KEY_PHASE = "phase"
    const val KEY_COMPLETED = "completed"
    const val KEY_TOTAL = "total"
    const val KEY_CURRENT_LESSON = "currentLesson"
    const val KEY_FACTS_CREATED = "factsCreated"
    const val KEY_MESSAGE = "message"
    const val KEY_WARNINGS = "warnings"
    const val KEY_ESTIMATED_COST = "estimatedCostUsd"
    const val KEY_ACTUAL_COST = "actualCostUsd"

    fun uniqueName(documentId: String): String = "facts_$documentId"
}

/**
 * Durable per-lesson fact generation from spec §11.5. Each lesson is persisted
 * as soon as it completes, so cancellation or a later failure keeps partial
 * success. Dedupe runs against facts already stored for the document, which also
 * covers earlier lessons in the same job.
 */
@HiltWorker
class FactsGenerationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val documentRepository: DocumentRepository,
    private val documentTextRepository: DocumentTextRepository,
    private val lessonRepository: LessonRepository,
    private val factRepository: FactRepository,
    private val structuredAiService: StructuredAiService,
    private val preRenderNotifier: CardPreRenderNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val documentId = inputData.getString(FactsWork.KEY_DOCUMENT_ID)
            ?: return Result.failure(workDataOf(FactsWork.KEY_MESSAGE to "Missing document id."))
        val lessonIds = inputData.getStringArray(FactsWork.KEY_LESSON_IDS)?.toList().orEmpty()
        if (lessonIds.isEmpty()) {
            return Result.failure(workDataOf(FactsWork.KEY_MESSAGE to "No lessons were selected."))
        }
        val manualProfileId = inputData.getString(FactsWork.KEY_PROFILE_ID)
        val document = documentRepository.getDocument(documentId)
            ?: return Result.failure(workDataOf(FactsWork.KEY_MESSAGE to "Document not found."))
        val settings = documentRepository.getSettings(documentId)
        val lessonsById = lessonRepository.getLessons(documentId).associateBy { it.id }
        val lessons = lessonIds.mapNotNull { lessonsById[it] }
        if (lessons.isEmpty()) {
            return Result.failure(workDataOf(FactsWork.KEY_MESSAGE to "Lessons not found."))
        }

        val warnings = mutableListOf<String>()
        var created = 0
        var estimated = 0.0
        var actual = 0.0
        var completedLessons = 0

        lessons.forEachIndexed { index, lesson ->
            setProgress(
                progress(
                    phase = "generating",
                    completed = index,
                    total = lessons.size,
                    currentLesson = lesson.title,
                    factsCreated = created,
                ),
            )
            val pages = documentTextRepository.getPages(documentId, lesson.startPage, lesson.endPage)
            if (pages.isEmpty()) {
                warnings += "${lesson.title}: no extracted text on pages ${lesson.startPage}–${lesson.endPage}."
                return@forEachIndexed
            }
            try {
                val existingFacts = factRepository
                    .observeFacts(FactFilter(documentId = documentId))
                    .first()
                val result = structuredAiService.generateFacts(
                    documentId = documentId,
                    lessonTitle = lesson.title,
                    pages = pages,
                    documentPageCount = document.pageCount,
                    density = settings?.factDensity ?: com.factswallpaper.core.model.FactDensity.BALANCED,
                    includeFormulas = settings?.includeFormulas ?: true,
                    includeDiagrams = settings?.includeDiagrams ?: true,
                    manualProfileId = manualProfileId ?: settings?.preferredProviderProfileId,
                    existingFactTexts = existingFacts.map { it.text },
                )
                estimated += result.estimatedCostUsd
                actual += result.actualCostUsd
                warnings += result.warnings

                val verify = settings?.autoVerifyFormulas ?: true
                val newFacts = result.facts.map { draft ->
                    val template = runCatching {
                        CardTemplateType.valueOf(draft.templateType.uppercase())
                    }.getOrDefault(CardTemplateType.TEXT)
                    val diagram = runCatching {
                        DiagramKind.valueOf(draft.diagramType.uppercase())
                    }.getOrDefault(DiagramKind.NONE)
                    var verification = VerificationStatus.UNVERIFIED
                    var effectiveTemplate = template
                    if (verify && (draft.requiresLatex || diagram != DiagramKind.NONE)) {
                        val source = draft.latex ?: draft.diagramSource ?: draft.text
                        val verdict = runCatching {
                            structuredAiService.verifyFormula(source, manualProfileId)
                        }.getOrNull()
                        if (verdict?.value?.valid == true) {
                            verification = VerificationStatus.VERIFIED
                        } else {
                            verification = VerificationStatus.FAILED
                            effectiveTemplate = CardTemplateType.TEXT
                        }
                    }
                    Fact(
                        id = UUID.randomUUID().toString(),
                        documentId = documentId,
                        lessonId = lesson.id,
                        text = draft.text,
                        latex = draft.latex,
                        requiresLatex = draft.requiresLatex,
                        templateType = effectiveTemplate,
                        diagramType = diagram,
                        diagramSource = draft.diagramSource,
                        sourceBookTitle = document.title,
                        sourceLessonTitle = draft.sourceLessonTitle ?: lesson.title,
                        sourcePage = draft.sourcePage ?: lesson.startPage,
                        tags = draft.tags,
                        confidence = draft.confidence,
                        verificationStatus = verification,
                        active = true,
                        createdAtEpochMs = System.currentTimeMillis(),
                    )
                }
                if (newFacts.isNotEmpty()) {
                    factRepository.saveFacts(newFacts)
                    created += newFacts.size
                }
                lessonRepository.upsert(
                    lesson.copy(factCount = existingFacts.count { it.lessonId == lesson.id } + newFacts.size),
                )
                completedLessons++
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                warnings += "${lesson.title}: ${exception.message ?: "generation failed"}"
            }
            setProgress(
                progress(
                    phase = "generating",
                    completed = index + 1,
                    total = lessons.size,
                    currentLesson = lesson.title,
                    factsCreated = created,
                ),
            )
        }

        if (completedLessons == 0) {
            return Result.failure(
                workDataOf(
                    FactsWork.KEY_MESSAGE to
                        (warnings.firstOrNull() ?: "Fact generation failed for every selected lesson."),
                    FactsWork.KEY_WARNINGS to warnings.joinToString("\n").take(2_000),
                ),
            )
        }
        documentRepository.updateExtractionStatus(documentId, ExtractionStatus.FACTS_READY)
        preRenderNotifier.enqueue()
        return Result.success(
            workDataOf(
                FactsWork.KEY_FACTS_CREATED to created,
                FactsWork.KEY_MESSAGE to
                    "Generated $created facts across $completedLessons lessons.",
                FactsWork.KEY_WARNINGS to warnings.filter { it.isNotBlank() }
                    .joinToString("\n").take(2_000),
                FactsWork.KEY_ESTIMATED_COST to estimated,
                FactsWork.KEY_ACTUAL_COST to actual,
            ),
        )
    }

    private fun progress(
        phase: String,
        completed: Int,
        total: Int,
        currentLesson: String,
        factsCreated: Int,
    ) = workDataOf(
        FactsWork.KEY_PHASE to phase,
        FactsWork.KEY_COMPLETED to completed,
        FactsWork.KEY_TOTAL to total,
        FactsWork.KEY_CURRENT_LESSON to currentLesson,
        FactsWork.KEY_FACTS_CREATED to factsCreated,
    )
}

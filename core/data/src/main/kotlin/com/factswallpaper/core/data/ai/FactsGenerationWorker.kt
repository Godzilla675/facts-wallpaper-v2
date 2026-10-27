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
import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.ai.FactDraft
import com.factswallpaper.core.data.ai.GenerationLessonRequest
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
    private val collectionRepository: CollectionRepository,
    private val structuredAiService: StructuredAiService,
    private val generationOrchestrator: GenerationOrchestrator,
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
        val generatedFactIds = mutableListOf<String>()

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
                val result = generationOrchestrator.generateLesson(
                    request = GenerationLessonRequest(
                    documentId = documentId,
                    lessonTitle = lesson.title,
                    lessonPages = pages,
                    documentPageCount = document.pageCount,
                    density = settings?.factDensity ?: com.factswallpaper.core.model.FactDensity.BALANCED,
                    includeFormulas = settings?.includeFormulas ?: true,
                    includeDiagrams = settings?.includeDiagrams ?: true,
                    manualProfileId = manualProfileId ?: settings?.preferredProviderProfileId,
                    existingFactTexts = existingFacts.map { it.text },
                    ),
                    pageProvider = { start, end ->
                        documentTextRepository.getPages(documentId, start, end)
                    },
                )
                estimated += result.estimatedCostUsd
                actual += result.actualCostUsd
                warnings += result.warnings

                val sourceMaterial = pages.joinToString("\n") {
                    "[PAGE ${it.pageNumber}]\n${it.text}"
                }
                val verify = settings?.autoVerifyFormulas ?: true
                val newFacts = result.facts.mapNotNull { draft ->
                    createVerifiedFact(
                        draft = draft,
                        documentId = documentId,
                        documentTitle = document.title,
                        lesson = lesson,
                        pages = pages,
                        sourceMaterial = sourceMaterial,
                        verify = verify,
                        manualProfileId = manualProfileId ?: settings?.preferredProviderProfileId,
                        warnings = warnings,
                    )
                }
                if (newFacts.isNotEmpty()) {
                    factRepository.saveFacts(newFacts)
                    newFacts.forEach { fact ->
                        val renderResult = generationOrchestrator.renderWithFixes(
                            fact = fact,
                            sourceMaterial = sourceMaterial,
                            profileId = manualProfileId ?: settings?.preferredProviderProfileId,
                        )
                        warnings += renderResult.warnings.map { "${lesson.title}: $it" }
                        if (renderResult.fact != null) {
                            if (renderResult.fact != fact) factRepository.saveFact(renderResult.fact)
                            generatedFactIds += renderResult.fact.id
                            created++
                        } else {
                            val deletion = factRepository.deleteFact(fact.id)
                            if (!deletion.isSuccess) {
                                warnings += "${lesson.title}: failed to remove an unrenderable card from storage."
                            }
                        }
                    }
                }
                lessonRepository.upsert(
                    lesson.copy(
                        factCount = existingFacts.count { it.lessonId == lesson.id } +
                            generatedFactIds.count { id ->
                                newFacts.any { it.id == id && it.lessonId == lesson.id }
                            },
                    ),
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
        if (generatedFactIds.isNotEmpty()) {
            val selectionName = when (lessons.size) {
                1 -> lessons.single().title
                else -> "Selected lessons"
            }
            val collection = collectionRepository.createCollection(
                name = "${document.title} — $selectionName",
                description = "Generated study cards for ${lessons.joinToString { it.title }}.",
            )
            collectionRepository.setCollectionFacts(collection.id, generatedFactIds.distinct())
        }
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

    private suspend fun createVerifiedFact(
        draft: FactDraft,
        documentId: String,
        documentTitle: String,
        lesson: Lesson,
        pages: List<com.factswallpaper.core.ai.PageText>,
        sourceMaterial: String,
        verify: Boolean,
        manualProfileId: String?,
        warnings: MutableList<String>,
    ): Fact? {
        var candidate = draft
        var status = VerificationStatus.UNVERIFIED
        val sourcePage = candidate.sourcePage?.let { page ->
            pages.firstOrNull { it.pageNumber == page }?.let { "[PAGE $page]\n${it.text}" }
        } ?: sourceMaterial
        if (verify) {
            val verified = try {
                generationOrchestrator.verifyAndFixDraft(
                    draft = candidate,
                    sourceMaterial = sourcePage,
                    lessonTitle = lesson.title,
                    profileId = manualProfileId,
                )
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                warnings += "${lesson.title}: fact verification failed: ${exception.message ?: "unknown error"}"
                null
            } ?: run {
                warnings += "${lesson.title}: unsupported card was dropped after verifier correction."
                return null
            }
            candidate = verified.draft
            status = verified.status
        }

        var template = parseTemplate(candidate.templateType)
        var diagram = parseDiagram(candidate.diagramType)
        var latex = candidate.latex
        var requiresLatex = candidate.requiresLatex
        var diagramSource = candidate.diagramSource
        if (verify && (requiresLatex || diagram != DiagramKind.NONE)) {
            val markup = latex ?: diagramSource ?: candidate.text
            val renderVerdict = try {
                structuredAiService.verifyFormula(markup, manualProfileId)
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                warnings += "${lesson.title}: formula/diagram verification failed."
                null
            }
            if (renderVerdict?.value?.valid == false) {
                status = VerificationStatus.FAILED
                template = CardTemplateType.TEXT
                diagram = DiagramKind.NONE
                latex = null
                requiresLatex = false
                diagramSource = null
                warnings += "${lesson.title}: invalid formula/diagram markup was converted to a text card."
            }
        }
        return Fact(
            id = UUID.randomUUID().toString(),
            documentId = documentId,
            lessonId = lesson.id,
            text = candidate.text,
            latex = latex,
            requiresLatex = requiresLatex,
            templateType = template,
            diagramType = diagram,
            diagramSource = diagramSource,
            sourceBookTitle = documentTitle,
            sourceLessonTitle = candidate.sourceLessonTitle ?: lesson.title,
            sourcePage = candidate.sourcePage ?: lesson.startPage,
            tags = candidate.tags,
            confidence = candidate.confidence,
            verificationStatus = status,
            active = true,
            createdAtEpochMs = System.currentTimeMillis(),
        )
    }

    private fun parseTemplate(value: String): CardTemplateType =
        runCatching { CardTemplateType.valueOf(value.uppercase()) }
            .getOrDefault(CardTemplateType.TEXT)

    private fun parseDiagram(value: String): DiagramKind =
        runCatching { DiagramKind.valueOf(value.uppercase()) }
            .getOrDefault(DiagramKind.NONE)

}

package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.PageToolFeedback
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.datastore.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class StructuredGenerationAiClient @Inject constructor(
    private val structuredAiService: StructuredAiService,
    private val settingsRepository: SettingsRepository,
) : GenerationAiClient {

    override val supportsPageTools: Boolean = true

    override suspend fun generate(request: GenerationAiRequest): GenerationAiResponse {
        val lesson = request.lesson
        val pageToolEnabled = settingsRepository.observePreferences()
            .first()
            .pageToolCallsEnabled
        val result = if (pageToolEnabled) {
            structuredAiService.generateFactsWithPageTool(
                documentId = lesson.documentId,
                lessonTitle = lesson.lessonTitle,
                pages = lesson.lessonPages,
                documentPageCount = lesson.documentPageCount,
                density = lesson.density,
                includeFormulas = lesson.includeFormulas,
                includeDiagrams = lesson.includeDiagrams,
                manualProfileId = lesson.manualProfileId,
                existingFactTexts = lesson.existingFactTexts,
                pageFeedback = request.toolResults.map { toolResult ->
                    PageToolFeedback(
                        startPage = toolResult.range.startPage,
                        endPage = toolResult.range.endPage,
                        pages = toolResult.pages,
                        error = toolResult.error,
                    )
                },
            )
        } else {
            val pages = (lesson.lessonPages + request.toolResults.flatMap { it.pages })
                .distinctBy { it.pageNumber }
                .sortedBy { it.pageNumber }
            structuredAiService.generateFacts(
                documentId = lesson.documentId,
                lessonTitle = lesson.lessonTitle,
                pages = pages,
                documentPageCount = lesson.documentPageCount,
                density = lesson.density,
                includeFormulas = lesson.includeFormulas,
                includeDiagrams = lesson.includeDiagrams,
                manualProfileId = lesson.manualProfileId,
                existingFactTexts = lesson.existingFactTexts,
            )
        }
        return GenerationAiResponse(
            facts = result.facts,
            pageRequest = result.pageRequest?.let { range ->
                PageRangeRequest(startPage = range.startPage, endPage = range.endPage)
            },
            warnings = result.warnings,
            estimatedCostUsd = result.estimatedCostUsd,
            actualCostUsd = result.actualCostUsd,
        )
    }

    override suspend fun verifyFact(
        factText: String,
        sourceMaterial: String,
        profileId: String?,
    ): Boolean? = structuredAiService.verifyFact(
        fact = factText,
        sourceMaterial = sourceMaterial,
        manualProfileId = profileId,
    ).value?.valid

    override suspend fun fixFact(
        factText: String,
        sourceMaterial: String,
        lessonTitle: String,
        instruction: String,
        profileId: String?,
    ) = structuredAiService.regenerateFact(
        originalText = factText,
        pageContent = sourceMaterial,
        lessonTitle = lessonTitle,
        extraInstruction = instruction,
        manualProfileId = profileId,
    )
}

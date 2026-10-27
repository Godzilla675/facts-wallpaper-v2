package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.StructuredAiService
import javax.inject.Inject

class StructuredGenerationAiClient @Inject constructor(
    private val structuredAiService: StructuredAiService,
) : GenerationAiClient {

    override val supportsPageTools: Boolean = false

    override suspend fun generate(request: GenerationAiRequest): GenerationAiResponse {
        val lesson = request.lesson
        val pages = (
            lesson.lessonPages + request.toolResults.flatMap { it.pages }
            )
            .distinctBy { it.pageNumber }
            .sortedBy { it.pageNumber }
        val result = structuredAiService.generateFacts(
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
        return GenerationAiResponse(
            facts = result.facts,
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

package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.FactDraft
import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.common.GeneratedCardRenderer
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.VerificationStatus
import javax.inject.Inject
import javax.inject.Singleton

data class GenerationLessonRequest(
    val documentId: String?,
    val lessonTitle: String,
    val lessonPages: List<PageText>,
    val documentPageCount: Int,
    val density: FactDensity,
    val includeFormulas: Boolean,
    val includeDiagrams: Boolean,
    val manualProfileId: String?,
    val existingFactTexts: List<String>,
)

data class PageRangeRequest(
    val startPage: Int,
    val endPage: Int,
)

data class PageToolResult(
    val range: PageRangeRequest,
    val pages: List<PageText> = emptyList(),
    val error: String? = null,
)

data class GenerationAiRequest(
    val lesson: GenerationLessonRequest,
    val toolResults: List<PageToolResult> = emptyList(),
)

data class GenerationAiResponse(
    val facts: List<FactDraft> = emptyList(),
    val pageRequest: PageRangeRequest? = null,
    val warnings: List<String> = emptyList(),
    val estimatedCostUsd: Double = 0.0,
    val actualCostUsd: Double = 0.0,
)

data class GenerationLessonResult(
    val facts: List<FactDraft>,
    val warnings: List<String>,
    val estimatedCostUsd: Double,
    val actualCostUsd: Double,
)

data class VerifiedFactDraft(
    val draft: FactDraft,
    val status: VerificationStatus,
)

data class RenderRecoveryResult(
    val fact: Fact?,
    val warnings: List<String>,
)

interface GenerationAiClient {
    val supportsPageTools: Boolean

    suspend fun generate(request: GenerationAiRequest): GenerationAiResponse

    suspend fun verifyFact(factText: String, sourceMaterial: String, profileId: String?): Boolean?

    suspend fun fixFact(
        factText: String,
        sourceMaterial: String,
        lessonTitle: String,
        instruction: String,
        profileId: String?,
    ): FactDraft
}

@Singleton
class GenerationOrchestrator @Inject constructor(
    private val aiClient: GenerationAiClient,
    private val cardRenderer: GeneratedCardRenderer,
) {

    suspend fun generateLesson(
        request: GenerationLessonRequest,
        pageProvider: suspend (startPage: Int, endPage: Int) -> List<PageText>,
        maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
        maxPagesPerCall: Int = DEFAULT_MAX_PAGES_PER_CALL,
    ): GenerationLessonResult {
        require(maxToolCalls >= 0)
        require(maxPagesPerCall > 0)
        val toolResults = mutableListOf<PageToolResult>()
        val warnings = mutableListOf<String>()
        var estimatedCost = 0.0
        var actualCost = 0.0
        var response: GenerationAiResponse? = null
        var calls = 0

        while (true) {
            val result = aiClient.generate(
                GenerationAiRequest(lesson = request, toolResults = toolResults.toList()),
            )
            response = result
            warnings += result.warnings
            estimatedCost += result.estimatedCostUsd
            actualCost += result.actualCostUsd
            val range = result.pageRequest
            if (range == null) break
            if (!aiClient.supportsPageTools) {
                warnings += "The selected provider does not support page tools; used the lesson pages directly."
                break
            }
            if (calls >= maxToolCalls) {
                warnings += "The page-tool call limit was reached before generation completed."
                response = result.copy(facts = emptyList())
                break
            }
            calls++
            val rangeSize = range.endPage.toLong() - range.startPage.toLong() + 1
            val rangeError = when {
                range.startPage < 1 || range.endPage < range.startPage ->
                    "Requested page range is invalid."
                range.endPage > request.documentPageCount ->
                    "Requested pages exceed the document."
                rangeSize > maxPagesPerCall ->
                    "A page-tool call may request at most $maxPagesPerCall pages."
                else -> null
            }
            if (rangeError != null) {
                toolResults += PageToolResult(range = range, error = rangeError)
                warnings += "Rejected page-tool request ${range.startPage}-${range.endPage}: $rangeError"
                continue
            }
            val pages = pageProvider(range.startPage, range.endPage)
                .filter { it.pageNumber in range.startPage..range.endPage }
                .distinctBy { it.pageNumber }
                .sortedBy { it.pageNumber }
            if (pages.isEmpty()) {
                toolResults += PageToolResult(range = range, error = "No extracted text exists for the requested pages.")
                continue
            }
            toolResults += PageToolResult(range = range, pages = pages)
        }

        return GenerationLessonResult(
            facts = response?.facts.orEmpty(),
            warnings = warnings.distinct(),
            estimatedCostUsd = estimatedCost,
            actualCostUsd = actualCost,
        )
    }

    suspend fun verifyAndFixDraft(
        draft: FactDraft,
        sourceMaterial: String,
        lessonTitle: String,
        profileId: String?,
        maxFixAttempts: Int = MAX_FIX_ATTEMPTS,
    ): VerifiedFactDraft? {
        var candidate = draft
        repeat(maxFixAttempts + 1) { attempt ->
            val verification = try {
                aiClient.verifyFact(candidate.text, sourceMaterial, profileId)
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                return VerifiedFactDraft(candidate, VerificationStatus.UNVERIFIED)
            }
            when (verification) {
                true -> return VerifiedFactDraft(candidate, VerificationStatus.VERIFIED)
                null -> return VerifiedFactDraft(candidate, VerificationStatus.UNVERIFIED)
                false -> {
                    if (attempt == maxFixAttempts) return null
                    candidate = aiClient.fixFact(
                        factText = candidate.text,
                        sourceMaterial = sourceMaterial,
                        lessonTitle = lessonTitle,
                        instruction = "Correct the claim using only the source material and keep it concise.",
                        profileId = profileId,
                    )
                }
            }
        }
        return null
    }

    suspend fun renderWithFixes(
        fact: Fact,
        sourceMaterial: String,
        profileId: String?,
        maxFixAttempts: Int = MAX_FIX_ATTEMPTS,
    ): RenderRecoveryResult {
        var current = fact
        val warnings = mutableListOf<String>()
        var rendered = cardRenderer.render(current)
        repeat(maxFixAttempts) {
            if (rendered.rendered) return RenderRecoveryResult(current, warnings)
            val fixed = try {
                aiClient.fixFact(
                    factText = current.text,
                    sourceMaterial = sourceMaterial,
                    lessonTitle = current.sourceLessonTitle.orEmpty(),
                    instruction = "Fix this card's offline rendering failure and preserve a source-supported claim. " +
                        "Renderer error: ${rendered.error ?: "unknown error"}",
                    profileId = profileId,
                )
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                warnings += "Render repair failed: ${exception.message ?: "unknown error"}"
                return@repeat
            }
            val verification = try {
                aiClient.verifyFact(fixed.text, sourceMaterial, profileId)
            } catch (exception: Throwable) {
                if (exception is kotlinx.coroutines.CancellationException) throw exception
                warnings += "The repaired card could not be verified."
                null
            }
            if (verification == false) {
                warnings += "A render repair was rejected by the source verifier."
                return@repeat
            }
            current = current.withDraft(
                fixed,
                if (verification == true) {
                    VerificationStatus.VERIFIED
                } else {
                    VerificationStatus.UNVERIFIED
                },
            )
            rendered = cardRenderer.render(current)
        }
        if (rendered.rendered) return RenderRecoveryResult(current, warnings)

        val fallback = current.copy(
            latex = null,
            requiresLatex = false,
            templateType = CardTemplateType.TEXT,
            diagramType = DiagramKind.NONE,
            diagramSource = null,
            verificationStatus = VerificationStatus.FAILED,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val fallbackResult = cardRenderer.render(fallback)
        if (fallbackResult.rendered) {
            warnings += "Renderer repair exhausted; used plain-text fallback."
            return RenderRecoveryResult(fallback, warnings)
        }
        warnings += "Card was dropped after plain-text rendering failed: " +
            (fallbackResult.error ?: "unknown error")
        return RenderRecoveryResult(null, warnings)
    }

    private fun Fact.withDraft(
        draft: FactDraft,
        verificationStatus: VerificationStatus,
    ): Fact = copy(
        text = draft.text,
        latex = draft.latex,
        requiresLatex = draft.requiresLatex,
        templateType = runCatching { CardTemplateType.valueOf(draft.templateType.uppercase()) }
            .getOrDefault(CardTemplateType.TEXT),
        diagramType = runCatching { DiagramKind.valueOf(draft.diagramType.uppercase()) }
            .getOrDefault(DiagramKind.NONE),
        diagramSource = draft.diagramSource,
        sourcePage = draft.sourcePage ?: sourcePage,
        tags = draft.tags,
        confidence = draft.confidence,
        verificationStatus = verificationStatus,
        updatedAtEpochMs = System.currentTimeMillis(),
    )

    private companion object {
        const val DEFAULT_MAX_TOOL_CALLS = 3
        const val DEFAULT_MAX_PAGES_PER_CALL = 5
        const val MAX_FIX_ATTEMPTS = 2
    }
}

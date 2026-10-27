package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.FactDraft
import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.common.GeneratedCardRenderer
import com.factswallpaper.core.common.GeneratedCardRenderResult
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class GenerationOrchestratorTest {

    @Test
    fun `page tool loop fetches bounded extra context before returning facts`() = runBlocking {
        val ai = FakeGenerationAiClient().apply {
            supportsPageTools = true
            replies += GenerationAiResponse(pageRequest = PageRangeRequest(4, 5))
            replies += GenerationAiResponse(facts = listOf(draft("Generated fact.")))
        }
        val orchestrator = GenerationOrchestrator(ai, FakeCardRenderer())
        val fetched = mutableListOf<IntRange>()

        val result = orchestrator.generateLesson(request(), pageProvider = { start, end ->
            fetched += start..end
            listOf(PageText(4, "Page four"), PageText(5, "Page five"), PageText(99, "Outside range"))
        })

        assertThat(fetched).containsExactly(4..5)
        assertThat(ai.requests).hasSize(2)
        assertThat(ai.requests.last().toolResults.single().pages.map { it.pageNumber })
            .containsExactly(4, 5)
            .inOrder()
        assertThat(result.facts).hasSize(1)
    }

    @Test
    fun `page tool rejects invalid and oversized ranges without fetching them`() = runBlocking {
        val ai = FakeGenerationAiClient().apply {
            supportsPageTools = true
            replies += GenerationAiResponse(pageRequest = PageRangeRequest(0, 20))
            replies += GenerationAiResponse(facts = listOf(draft("Generated fact.")))
        }
        val orchestrator = GenerationOrchestrator(ai, FakeCardRenderer())
        var fetchCount = 0

        val result = orchestrator.generateLesson(request(), pageProvider = { _, _ ->
            fetchCount++
            emptyList()
        })

        assertThat(fetchCount).isEqualTo(0)
        assertThat(ai.requests.last().toolResults.single().error).contains("invalid")
        assertThat(result.warnings.single()).contains("Rejected page-tool request")
        assertThat(result.facts).hasSize(1)
    }

    @Test
    fun `verifier rejection receives a fix and retries verification`() = runBlocking {
        val ai = FakeGenerationAiClient().apply {
            verificationResults += false
            verificationResults += true
            fixedDraft = draft("Corrected, source-supported fact.")
        }
        val orchestrator = GenerationOrchestrator(ai, FakeCardRenderer())

        val result = orchestrator.verifyAndFixDraft(
            draft = draft("Unsupported fact."),
            sourceMaterial = "[PAGE 3] Source evidence.",
            lessonTitle = "Chapter",
            profileId = "profile-1",
        )

        assertThat(result?.draft?.text).isEqualTo("Corrected, source-supported fact.")
        assertThat(result?.status).isEqualTo(VerificationStatus.VERIFIED)
        assertThat(ai.verificationInputs).hasSize(2)
        assertThat(ai.fixInstructions.single()).contains("Correct the claim")
    }

    @Test
    fun `repeated render errors fall back to a plain text card`() = runBlocking {
        val ai = FakeGenerationAiClient().apply {
            fixedDraft = draft("Repaired fact.", templateType = "FORMULA")
            verificationResults += true
            verificationResults += true
        }
        val renderer = FakeCardRenderer().apply {
            results += GeneratedCardRenderResult(false, error = "KaTeX compilation failed")
            results += GeneratedCardRenderResult(false, error = "KaTeX compilation failed")
            results += GeneratedCardRenderResult(false, error = "KaTeX compilation failed")
            results += GeneratedCardRenderResult(true)
        }
        val orchestrator = GenerationOrchestrator(ai, renderer)

        val result = orchestrator.renderWithFixes(
            fact(),
            sourceMaterial = "Supported source.",
            profileId = "profile-1",
        )

        assertThat(result.fact?.templateType).isEqualTo(CardTemplateType.TEXT)
        assertThat(result.fact?.verificationStatus).isEqualTo(VerificationStatus.FAILED)
        assertThat(result.warnings.single()).contains("plain-text fallback")
        assertThat(renderer.renderedFacts).hasSize(4)
    }

    private fun request() = GenerationLessonRequest(
        documentId = "doc-1",
        lessonTitle = "Chapter",
        lessonPages = listOf(PageText(3, "Initial lesson context")),
        documentPageCount = 20,
        density = FactDensity.CONCISE,
        includeFormulas = false,
        includeDiagrams = false,
        manualProfileId = "profile-1",
        existingFactTexts = emptyList(),
    )

    private fun draft(
        text: String,
        templateType: String = "TEXT",
    ) = FactDraft(
        text = text,
        templateType = templateType,
        diagramType = "NONE",
    )

    private fun fact() = Fact(
        id = "fact-1",
        documentId = "doc-1",
        lessonId = "lesson-1",
        text = "Generated formula fact.",
        latex = "x^2",
        requiresLatex = true,
        templateType = CardTemplateType.FORMULA,
        diagramType = DiagramKind.NONE,
        sourceBookTitle = "Book",
        sourceLessonTitle = "Chapter",
        createdAtEpochMs = 1L,
    )

    private class FakeGenerationAiClient : GenerationAiClient {
        override var supportsPageTools: Boolean = false
        val replies = ArrayDeque<GenerationAiResponse>()
        val requests = mutableListOf<GenerationAiRequest>()
        val verificationResults = ArrayDeque<Boolean?>()
        val verificationInputs = mutableListOf<String>()
        val fixInstructions = mutableListOf<String>()
        var fixedDraft = FactDraft(text = "Fixed fact.")

        override suspend fun generate(request: GenerationAiRequest): GenerationAiResponse {
            requests += request
            return replies.removeFirst()
        }

        override suspend fun verifyFact(
            factText: String,
            sourceMaterial: String,
            profileId: String?,
        ): Boolean? {
            verificationInputs += factText
            return if (verificationResults.isEmpty()) true else verificationResults.removeFirst()
        }

        override suspend fun fixFact(
            factText: String,
            sourceMaterial: String,
            lessonTitle: String,
            instruction: String,
            profileId: String?,
        ): FactDraft {
            fixInstructions += instruction
            return fixedDraft
        }
    }

    private class FakeCardRenderer : GeneratedCardRenderer {
        val results = ArrayDeque<GeneratedCardRenderResult>()
        val renderedFacts = mutableListOf<Fact>()

        override suspend fun render(fact: Fact): GeneratedCardRenderResult {
            renderedFacts += fact
            return if (results.isEmpty()) {
                GeneratedCardRenderResult(true)
            } else {
                results.removeFirst()
            }
        }
    }
}

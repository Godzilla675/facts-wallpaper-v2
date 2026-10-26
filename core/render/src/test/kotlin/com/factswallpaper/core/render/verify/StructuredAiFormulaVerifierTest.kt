package com.factswallpaper.core.render.verify

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.ai.ValidationResult
import com.factswallpaper.core.ai.VerificationEnvelope
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.render.diagram.MarkupKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StructuredAiFormulaVerifierTest {

    @Test
    fun `returns valid verdict when StructuredAiService reports valid formula`() = runTest {
        val fakeAiService = FakeStructuredAiService(valid = true)
        val verifier = StructuredAiFormulaVerifier(fakeAiService)

        val verdict = verifier.verify(MarkupKind.LATEX, "E = mc^2")

        assertThat(verdict).isNotNull()
        assertThat(verdict!!.valid).isTrue()
        assertThat(verdict.reason).isNull()
    }

    @Test
    fun `returns invalid verdict when StructuredAiService reports invalid formula`() = runTest {
        val fakeAiService = FakeStructuredAiService(valid = false, reason = "Syntax error in superscripts")
        val verifier = StructuredAiFormulaVerifier(fakeAiService)

        val verdict = verifier.verify(MarkupKind.LATEX, "E = m c ^")

        assertThat(verdict).isNotNull()
        assertThat(verdict!!.valid).isFalse()
        assertThat(verdict.reason).isEqualTo("Syntax error in superscripts")
    }

    @Test
    fun `returns null verdict on AI exception so status stays pending`() = runTest {
        val fakeAiService = FakeStructuredAiService(throwError = true)
        val verifier = StructuredAiFormulaVerifier(fakeAiService)

        val verdict = verifier.verify(MarkupKind.LATEX, "E = mc^2")

        assertThat(verdict).isNull()
    }

    private class FakeStructuredAiService(
        private val valid: Boolean = true,
        private val reason: String? = null,
        private val throwError: Boolean = false,
    ) : StructuredAiService {
        override suspend fun verifyFormula(
            source: String,
            manualProfileId: String?,
        ): ValidationResult<VerificationEnvelope> {
            if (throwError) throw AiException(AiErrorKind.AUTHENTICATION, "No profile configured")
            return ValidationResult(VerificationEnvelope(valid = valid, reason = reason))
        }

        override suspend fun generateOutline(
            documentId: String?,
            pages: List<PageText>,
            pdfBytes: ByteArray?,
            pdfFileName: String?,
            pdfPageCount: Int?,
            manualProfileId: String?,
            progress: ((completed: Int, total: Int) -> Unit)?,
        ) = error("Not needed")

        override suspend fun generateFacts(
            documentId: String?,
            lessonTitle: String,
            pages: List<PageText>,
            documentPageCount: Int,
            density: FactDensity,
            includeFormulas: Boolean,
            includeDiagrams: Boolean,
            manualProfileId: String?,
            existingFactTexts: List<String>,
            extraInstruction: String?,
            progress: ((completed: Int, total: Int) -> Unit)?,
        ) = error("Not needed")

        override suspend fun estimateOutline(
            pages: List<PageText>,
            pdfPageCount: Int?,
            manualProfileId: String?,
        ) = error("Not needed")

        override suspend fun estimateFacts(
            lessonTitle: String,
            pages: List<PageText>,
            density: FactDensity,
            includeFormulas: Boolean,
            includeDiagrams: Boolean,
            manualProfileId: String?,
        ) = error("Not needed")

        override suspend fun generateDiagram(
            description: String,
            kindHint: String?,
            manualProfileId: String?,
        ) = error("Not needed")

        override suspend fun generateTemplate(
            description: String,
            manualProfileId: String?,
        ) = error("Not needed")

        override suspend fun suggestCollections(
            facts: List<com.factswallpaper.core.ai.FactSummaryInput>,
            manualProfileId: String?,
        ) = error("Not needed")

        override suspend fun regenerateFact(
            originalText: String,
            pageContent: String?,
            lessonTitle: String?,
            extraInstruction: String?,
            manualProfileId: String?,
        ) = error("Not needed")
    }
}

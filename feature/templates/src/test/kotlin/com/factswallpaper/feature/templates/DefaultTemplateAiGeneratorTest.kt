package com.factswallpaper.feature.templates

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.ai.TemplateGenerationResult
import com.factswallpaper.core.ai.ValidationResult
import com.factswallpaper.core.ai.VerificationEnvelope
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class DefaultTemplateAiGeneratorTest {

    @Test
    fun `decodes and validates a well formed AI template spec`() = runTest {
        val validJson = """
            {
              "id": "custom_neon_1",
              "name": "Neon Cyan",
              "archetype": "TEXT",
              "lightBackgroundHex": "#F0F8FF",
              "darkBackgroundHex": "#0A101D",
              "amoledBackgroundHex": "#000000",
              "cardSurfaceLightHex": "#FFFFFF",
              "cardSurfaceDarkHex": "#121E33",
              "cardSurfaceAmoledHex": "#050B14",
              "accentHexLight": "#0088CC",
              "accentHexDark": "#00E5FF",
              "titleFontFamily": "MONOSPACE",
              "bodyFontFamily": "MONOSPACE",
              "borderWidthDp": 2,
              "cornerRadiusDp": 16
            }
        """.trimIndent()

        val fakeAiService = FakeStructuredAiService(templateJson = validJson)
        val generator = DefaultTemplateAiGenerator(fakeAiService)

        val result = generator.generate("modern neon cyan card with monospace font")

        assertThat(result.name).isEqualTo("Neon Cyan")
        assertThat(result.spec.id).isEqualTo("custom_neon_1")
        assertThat(result.spec.accentHexDark).isEqualTo("#00E5FF")
        assertThat(CardTemplateCodec.isValid(result.spec)).isTrue()
    }

    @Test
    fun `assigns unique id if AI response omits id`() = runTest {
        val jsonWithoutId = """
            {
              "name": "Soft Sunset",
              "archetype": "QUOTE",
              "lightBackgroundHex": "#FFF5F5",
              "darkBackgroundHex": "#1C1416",
              "amoledBackgroundHex": "#000000",
              "cardSurfaceLightHex": "#FFFFFF",
              "cardSurfaceDarkHex": "#271B1E",
              "cardSurfaceAmoledHex": "#080405",
              "accentHexLight": "#C53030",
              "accentHexDark": "#FEB2B2"
            }
        """.trimIndent()

        val fakeAiService = FakeStructuredAiService(templateJson = jsonWithoutId)
        val generator = DefaultTemplateAiGenerator(fakeAiService)

        val result = generator.generate("sunset quote card")

        assertThat(result.name).isEqualTo("Soft Sunset")
        assertThat(result.spec.id).startsWith("custom_")
        assertThat(CardTemplateCodec.isValid(result.spec)).isTrue()
    }

    @Test
    fun `throws IllegalStateException when AI output fails CardTemplateCodec validation`() = runTest {
        val invalidColorJson = """
            {
              "id": "bad_spec",
              "name": "Bad Color",
              "archetype": "TEXT",
              "lightBackgroundHex": "NOT_A_HEX_COLOR"
            }
        """.trimIndent()

        val fakeAiService = FakeStructuredAiService(templateJson = invalidColorJson)
        val generator = DefaultTemplateAiGenerator(fakeAiService)

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                generator.generate("aesthetic prompt")
            }
        }
    }

    @Test
    fun `throws AiException when AI provider fails`() = runTest {
        val fakeAiService = FakeStructuredAiService(throwAiException = true)
        val generator = DefaultTemplateAiGenerator(fakeAiService)

        val ex = assertThrows(AiException::class.java) {
            kotlinx.coroutines.runBlocking {
                generator.generate("aesthetic prompt")
            }
        }
        assertThat(ex.kind).isEqualTo(AiErrorKind.AUTHENTICATION)
    }

    private class FakeStructuredAiService(
        private val templateJson: String = "{}",
        private val throwAiException: Boolean = false,
    ) : StructuredAiService {

        override suspend fun generateTemplate(
            description: String,
            manualProfileId: String?,
        ): TemplateGenerationResult {
            if (throwAiException) {
                throw AiException(AiErrorKind.AUTHENTICATION, "No AI provider configured")
            }
            return TemplateGenerationResult(
                templateJson = templateJson,
                profile = ProviderProfile(
                    id = "test_profile",
                    name = "Test Provider",
                    providerType = ProviderType.GEMINI,
                    baseUrl = "https://example.com",
                    modelName = "test-model",
                ),
                actualCostUsd = 0.001,
            )
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

        override suspend fun verifyFormula(
            source: String,
            manualProfileId: String?,
        ): ValidationResult<VerificationEnvelope> = error("Not needed")

        override suspend fun generateDiagram(
            description: String,
            kindHint: String?,
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

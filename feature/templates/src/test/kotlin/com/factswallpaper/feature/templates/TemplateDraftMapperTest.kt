package com.factswallpaper.feature.templates

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.TemplateAlignment
import com.factswallpaper.core.render.template.TemplateFontFamily
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class TemplateDraftMapperTest {

    @Test
    fun `default draft round-trips through the spec mapper without losing fields`() {
        val source = BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)!!
        val draft = TemplateDraftMapper.newDraft(source)

        val roundTripped = TemplateDraftMapper.toSpec(draft, source.id)

        assertThat(roundTripped).isEqualTo(source)
        assertThat(CardTemplateCodec.validate(roundTripped)).isEmpty()
    }

    @Test
    fun `draftFrom reads a stored native JSON template`() {
        val builtIn = BuiltInTemplates.byId(BuiltInTemplates.QUOTE_HIGHLIGHT_ID)!!

        val draft = TemplateDraftMapper.draftFrom(builtIn)

        assertThat(draft).isNotNull()
        assertThat(draft!!.name).isEqualTo(builtIn.name)
        assertThat(draft.showQuoteMark).isTrue()
        assertThat(draft.textAlignment).isEqualTo(TemplateAlignment.CENTER)
    }

    @Test
    fun `toSpec trims the name, keeps the requested id and stays valid`() {
        val draft = TemplateDraftMapper.newDraft().copy(
            name = "  My Study Card  ",
            archetype = CardTemplateType.FORMULA,
            bodyFontScale = 1.2f,
            accentHexDark = "#00E5FF",
        )

        val spec = TemplateDraftMapper.toSpec(draft, "custom_study_card")

        assertThat(spec.id).isEqualTo("custom_study_card")
        assertThat(spec.name).isEqualTo("My Study Card")
        assertThat(spec.archetype).isEqualTo(CardTemplateType.FORMULA.name)
        assertThat(spec.bodyFontScale).isEqualTo(1.2f)
        assertThat(spec.accentHexDark).isEqualTo("#00E5FF")
        assertThat(CardTemplateCodec.validate(spec)).isEmpty()
    }

    @Test
    fun `local ai generator maps palette typography and archetype keywords`() = runTest {
        val generated = LocalTemplateAiGenerator().generate(
            "neon centered monospace formula card with gradient and page only source",
        )

        assertThat(generated.name).isEqualTo("Neon Study Card")
        assertThat(generated.spec.id).startsWith("custom_")
        assertThat(generated.spec.archetype).isEqualTo(CardTemplateType.FORMULA.name)
        assertThat(generated.spec.titleFontFamily).isEqualTo(TemplateFontFamily.MONOSPACE)
        assertThat(generated.spec.textAlignment).isEqualTo(TemplateAlignment.CENTER)
        assertThat(generated.spec.accentHexDark).isEqualTo("#00E5FF")
        assertThat(generated.spec.defaultSourceRefMode)
            .isEqualTo(SourceReferenceMode.PAGE_ONLY.name)
        assertThat(CardTemplateCodec.validate(generated.spec)).isEmpty()
    }

    @Test
    fun `local ai generator honours quoted names and produces unique ids`() = runTest {
        val first = LocalTemplateAiGenerator().generate("create a \"Parchment Notes\" template")
        val second = LocalTemplateAiGenerator().generate("create a \"Parchment Notes\" template")

        assertThat(first.name).isEqualTo("Parchment notes")
        assertThat(first.spec.id).isNotEqualTo(second.spec.id)
    }

    @Test
    fun `local ai generator rejects blank prompts instead of inventing a template`() = runTest {
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking {
                LocalTemplateAiGenerator().generate("   ")
            }
        }
    }
}

package com.factswallpaper.core.render.template

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.TemplateEngineKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardTemplateCodecTest {

    @Test
    fun `built-in catalogue exposes eight valid and uniquely identified templates`() {
        val specs = BuiltInTemplates.specs

        assertThat(specs).hasSize(8)
        assertThat(specs.map { it.id }).containsNoDuplicates()
        assertThat(specs.map { it.name }).containsNoDuplicates()
        specs.forEach { spec ->
            assertThat(CardTemplateType.entries.map { it.name }).contains(spec.archetype)
            assertThat(CardTemplateCodec.validate(spec)).isEmpty()
        }
        assertThat(specs.map { it.archetype }.toSet()).hasSize(7)
        assertThat(BuiltInTemplates.DEFAULT_TEMPLATE_ID).isEqualTo(BuiltInTemplates.MINIMAL_TEXT_ID)
    }

    @Test
    fun `every built-in spec survives a JSON round trip`() {
        BuiltInTemplates.specs.forEach { spec ->
            val encoded = CardTemplateCodec.encode(spec)
            val decoded = CardTemplateCodec.decode(encoded)

            assertThat(decoded).isEqualTo(spec)
            assertThat(CardTemplateCodec.encode(decoded!!)).isEqualTo(encoded)
        }
    }

    @Test
    fun `built-in templates are stored as native JSON with default metadata`() {
        val templates = BuiltInTemplates.all()

        assertThat(templates).hasSize(8)
        assertThat(templates.filter { it.isDefault }.map { it.id })
            .containsExactly(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
        templates.forEach { template ->
            assertThat(template.engineKind).isEqualTo(TemplateEngineKind.NATIVE_JSON)
            assertThat(template.builtIn).isTrue()
            val spec = CardTemplateCodec.fromCardTemplate(template)
            assertThat(spec).isNotNull()
            assertThat(spec!!.id).isEqualTo(template.id)
        }
    }

    @Test
    fun `validation rejects malformed palettes and out-of-range layout values`() {
        val valid = BuiltInTemplates.spec(BuiltInTemplates.MINIMAL_TEXT_ID)!!

        val badColor = valid.copy(accentHexDark = "blue")
        val badScale = valid.copy(bodyFontScale = 3f)
        val badArchetype = valid.copy(archetype = "UNKNOWN")
        val badSourceMode = valid.copy(defaultSourceRefMode = "EVERYTHING")
        val badMargins = valid.copy(
            topSafeMarginFraction = 0.9f,
            bottomSafeMarginFraction = 0.7f,
            diagramHeightFraction = 0.95f,
        )
        val badPadding = valid.copy(horizontalPaddingDp = 2, verticalPaddingDp = 200)
        val badName = valid.copy(name = " ")

        listOf(
            badColor,
            badScale,
            badArchetype,
            badSourceMode,
            badMargins,
            badPadding,
            badName,
        ).forEach { spec ->
            assertThat(CardTemplateCodec.validate(spec)).isNotEmpty()
            assertThat(CardTemplateCodec.isValid(spec)).isFalse()
        }
    }

    @Test
    fun `decode rejects malformed or missing JSON instead of throwing`() {
        assertThat(CardTemplateCodec.decode("not json")).isNull()
        assertThat(CardTemplateCodec.decode("{}")).isNull()
        assertThat(CardTemplateCodec.decode("""{"id":"x"}""")).isNull()
    }

    @Test
    fun `defaults are encoded so an exported template remains self-describing`() {
        val spec = CardTemplateSpec(
            id = "custom_test",
            name = "Test",
            archetype = CardTemplateType.TEXT.name,
        )
        val encoded = CardTemplateCodec.encode(spec)

        assertThat(encoded).contains("\"backgroundStyle\":\"SOLID\"")
        assertThat(encoded).contains("\"defaultSourceRefMode\":\"COMPACT\"")
        assertThat(encoded).contains("\"topSafeMarginFraction\":0.24")
        assertThat(CardTemplateCodec.decode(encoded)).isEqualTo(spec)
        assertThat(SourceReferenceMode.entries.map { it.name }).contains(spec.defaultSourceRefMode)
    }
}

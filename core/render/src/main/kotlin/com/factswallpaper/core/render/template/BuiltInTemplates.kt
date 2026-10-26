package com.factswallpaper.core.render.template

import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType

/**
 * The eight canonical built-in templates from specification §7.2.
 *
 * These specs are the single source of truth for the built-in gallery. `TemplateSeeder`
 * inserts only missing rows, so a user who changed their default keeps that choice across
 * upgrades while new built-ins still appear.
 */
object BuiltInTemplates {

    const val MINIMAL_TEXT_ID = "builtin_minimal_text"
    const val SPLIT_HEADER_ID = "builtin_split_header"
    const val QUOTE_HIGHLIGHT_ID = "builtin_quote_highlight"
    const val FORMULA_SPOTLIGHT_ID = "builtin_formula_spotlight"
    const val DIAGRAM_CARD_ID = "builtin_diagram_card"
    const val COMPACT_BULLET_ID = "builtin_compact_bullet"
    const val MIDNIGHT_AMOLED_ID = "builtin_midnight_amoled"
    const val NEON_STUDY_ID = "builtin_neon_study"

    const val DEFAULT_TEMPLATE_ID = MINIMAL_TEXT_ID

    val specs: List<CardTemplateSpec> = listOf(
        CardTemplateSpec(
            id = MINIMAL_TEXT_ID,
            name = "Minimal Text",
            archetype = CardTemplateType.TEXT.name,
            titleFontFamily = TemplateFontFamily.SERIF,
            bodyFontFamily = TemplateFontFamily.SANS,
            textAlignment = TemplateAlignment.START,
            borderWidthDp = 1,
            cornerRadiusDp = 24,
            showCategoryPill = true,
        ),
        CardTemplateSpec(
            id = SPLIT_HEADER_ID,
            name = "Split Header",
            archetype = CardTemplateType.DEFINITION.name,
            splitHeaderBand = true,
            titleFontFamily = TemplateFontFamily.SERIF,
            bodyFontFamily = TemplateFontFamily.SANS,
            cornerRadiusDp = 20,
        ),
        CardTemplateSpec(
            id = QUOTE_HIGHLIGHT_ID,
            name = "Quote Highlight",
            archetype = CardTemplateType.QUOTE.name,
            showQuoteMark = true,
            bodyFontFamily = TemplateFontFamily.SERIF,
            bodyFontScale = 1.12f,
            textAlignment = TemplateAlignment.CENTER,
        ),
        CardTemplateSpec(
            id = FORMULA_SPOTLIGHT_ID,
            name = "Formula Spotlight",
            archetype = CardTemplateType.FORMULA.name,
            formulaStageFramed = true,
            titleFontFamily = TemplateFontFamily.MONOSPACE,
            textAlignment = TemplateAlignment.CENTER,
        ),
        CardTemplateSpec(
            id = DIAGRAM_CARD_ID,
            name = "Diagram Card",
            archetype = CardTemplateType.DIAGRAM.name,
            diagramHeightFraction = 0.52f,
            bodyFontScale = 0.94f,
            cornerRadiusDp = 20,
        ),
        CardTemplateSpec(
            id = COMPACT_BULLET_ID,
            name = "Compact Bullet",
            archetype = CardTemplateType.BULLET.name,
            bodyFontScale = 0.95f,
            verticalPaddingDp = 20,
            textAlignment = TemplateAlignment.START,
        ),
        CardTemplateSpec(
            id = MIDNIGHT_AMOLED_ID,
            name = "Midnight AMOLED",
            archetype = CardTemplateType.TEXT.name,
            backgroundStyle = BackgroundStyle.AMOLED_PURE_BLACK,
            amoledBackgroundHex = "#000000",
            cardSurfaceAmoledHex = "#000000",
            borderWidthDp = 1,
            accentHexDark = "#64B5F6",
        ),
        CardTemplateSpec(
            id = NEON_STUDY_ID,
            name = "Neon Study Card",
            archetype = CardTemplateType.QA.name,
            darkBackgroundHex = "#070B12",
            cardSurfaceDarkHex = "#0E1525",
            accentHexDark = "#00E5FF",
            titleFontFamily = TemplateFontFamily.MONOSPACE,
            borderWidthDp = 2,
        ),
    )

    fun all(): List<CardTemplate> = specs.map { spec ->
        CardTemplateCodec.toCardTemplate(
            spec = spec,
            builtIn = true,
            isDefault = spec.id == DEFAULT_TEMPLATE_ID,
        )
    }

    fun spec(templateId: String): CardTemplateSpec? = specs.firstOrNull { it.id == templateId }

    fun byId(templateId: String): CardTemplate? = all().firstOrNull { it.id == templateId }
}

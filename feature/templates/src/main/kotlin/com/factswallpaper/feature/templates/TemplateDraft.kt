package com.factswallpaper.feature.templates

import androidx.annotation.StringRes
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.template.BackgroundStyle
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSpec
import com.factswallpaper.core.render.template.TemplateAlignment
import com.factswallpaper.core.render.template.TemplateFontFamily

/**
 * Editable form state for the visual template customizer (F13).
 *
 * The draft keeps every palette value from the source template so duplicating a built-in
 * preserves its identity; only the controls exposed in the customizer mutate it.
 */
data class TemplateDraft(
    val name: String,
    val archetype: CardTemplateType,
    val backgroundStyle: BackgroundStyle,
    val lightBackgroundHex: String,
    val darkBackgroundHex: String,
    val amoledBackgroundHex: String,
    val cardSurfaceLightHex: String,
    val cardSurfaceDarkHex: String,
    val cardSurfaceAmoledHex: String,
    val accentHexLight: String,
    val accentHexDark: String,
    val titleFontFamily: TemplateFontFamily,
    val bodyFontFamily: TemplateFontFamily,
    val bodyFontScale: Float,
    val textAlignment: TemplateAlignment,
    val horizontalPaddingDp: Int,
    val verticalPaddingDp: Int,
    val cornerRadiusDp: Int,
    val borderWidthDp: Int,
    val topSafeMarginFraction: Float,
    val bottomSafeMarginFraction: Float,
    val diagramHeightFraction: Float,
    val showCategoryPill: Boolean,
    val showQuoteMark: Boolean,
    val splitHeaderBand: Boolean,
    val formulaStageFramed: Boolean,
    val defaultSourceRefMode: SourceReferenceMode,
)

/** A named accent role offered by the customizer. The stored template keeps raw hex values. */
data class AccentRole(
    @StringRes val labelRes: Int,
    val lightHex: String,
    val darkHex: String,
) {
    companion object {
        val all: List<AccentRole> = listOf(
            AccentRole(R.string.templates_accent_teal, "#006684", "#76D1FF"),
            AccentRole(R.string.templates_accent_amber, "#9A5B00", "#FFC46B"),
            AccentRole(R.string.templates_accent_violet, "#6750A4", "#C7BFFF"),
            AccentRole(R.string.templates_accent_rose, "#9C4146", "#FFB3B8"),
            AccentRole(R.string.templates_accent_forest, "#2E6B3F", "#8FD3A2"),
            AccentRole(R.string.templates_accent_slate, "#455A64", "#B0BEC5"),
        )
    }
}

object TemplateDraftMapper {

    fun newDraft(source: CardTemplateSpec = defaultSpec()): TemplateDraft =
        source.toDraft()

    fun draftFrom(template: CardTemplate): TemplateDraft? =
        CardTemplateCodec.fromCardTemplate(template)?.toDraft()

    fun toSpec(draft: TemplateDraft, id: String): CardTemplateSpec = CardTemplateSpec(
        id = id,
        name = draft.name.trim(),
        archetype = draft.archetype.name,
        backgroundStyle = draft.backgroundStyle,
        lightBackgroundHex = draft.lightBackgroundHex,
        darkBackgroundHex = draft.darkBackgroundHex,
        amoledBackgroundHex = draft.amoledBackgroundHex,
        cardSurfaceLightHex = draft.cardSurfaceLightHex,
        cardSurfaceDarkHex = draft.cardSurfaceDarkHex,
        cardSurfaceAmoledHex = draft.cardSurfaceAmoledHex,
        accentHexLight = draft.accentHexLight,
        accentHexDark = draft.accentHexDark,
        borderWidthDp = draft.borderWidthDp,
        cornerRadiusDp = draft.cornerRadiusDp,
        horizontalPaddingDp = draft.horizontalPaddingDp,
        verticalPaddingDp = draft.verticalPaddingDp,
        topSafeMarginFraction = draft.topSafeMarginFraction,
        bottomSafeMarginFraction = draft.bottomSafeMarginFraction,
        titleFontFamily = draft.titleFontFamily,
        bodyFontFamily = draft.bodyFontFamily,
        bodyFontScale = draft.bodyFontScale,
        textAlignment = draft.textAlignment,
        showCategoryPill = draft.showCategoryPill,
        showQuoteMark = draft.showQuoteMark,
        splitHeaderBand = draft.splitHeaderBand,
        formulaStageFramed = draft.formulaStageFramed,
        diagramHeightFraction = draft.diagramHeightFraction,
        defaultSourceRefMode = draft.defaultSourceRefMode.name,
    )

    fun defaultSpec(): CardTemplateSpec =
        BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: BuiltInTemplates.specs.first()

    private fun CardTemplateSpec.toDraft(): TemplateDraft = TemplateDraft(
        name = name,
        archetype = CardTemplateType.entries.firstOrNull { it.name == archetype }
            ?: CardTemplateType.TEXT,
        backgroundStyle = backgroundStyle,
        lightBackgroundHex = lightBackgroundHex,
        darkBackgroundHex = darkBackgroundHex,
        amoledBackgroundHex = amoledBackgroundHex,
        cardSurfaceLightHex = cardSurfaceLightHex,
        cardSurfaceDarkHex = cardSurfaceDarkHex,
        cardSurfaceAmoledHex = cardSurfaceAmoledHex,
        accentHexLight = accentHexLight,
        accentHexDark = accentHexDark,
        titleFontFamily = titleFontFamily,
        bodyFontFamily = bodyFontFamily,
        bodyFontScale = bodyFontScale,
        textAlignment = textAlignment,
        horizontalPaddingDp = horizontalPaddingDp,
        verticalPaddingDp = verticalPaddingDp,
        cornerRadiusDp = cornerRadiusDp,
        borderWidthDp = borderWidthDp,
        topSafeMarginFraction = topSafeMarginFraction,
        bottomSafeMarginFraction = bottomSafeMarginFraction,
        diagramHeightFraction = diagramHeightFraction,
        showCategoryPill = showCategoryPill,
        showQuoteMark = showQuoteMark,
        splitHeaderBand = splitHeaderBand,
        formulaStageFramed = formulaStageFramed,
        defaultSourceRefMode = SourceReferenceMode.entries.firstOrNull {
            it.name == defaultSourceRefMode
        } ?: SourceReferenceMode.COMPACT,
    )
}

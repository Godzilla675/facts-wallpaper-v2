package com.factswallpaper.core.render.template

import kotlinx.serialization.Serializable

/**
 * Formal Native JSON Template DSL from specification §7.1.
 *
 * `schemaOrMarkup` for every [com.factswallpaper.core.model.TemplateEngineKind.NATIVE_JSON]
 * template serializes to this contract. The field order and defaults intentionally mirror
 * the specification so that exported templates remain readable by future app versions.
 */
@Serializable
data class CardTemplateSpec(
    val id: String,
    val name: String,
    val archetype: String, // CardTemplateType.name default affinity
    val backgroundStyle: BackgroundStyle = BackgroundStyle.SOLID,
    val lightBackgroundHex: String = "#FAF8F5",
    val darkBackgroundHex: String = "#0F1419",
    val amoledBackgroundHex: String = "#000000",
    val cardSurfaceLightHex: String = "#FFFFFF",
    val cardSurfaceDarkHex: String = "#1B2026",
    val cardSurfaceAmoledHex: String = "#070A0E",
    val accentHexLight: String = "#006684",
    val accentHexDark: String = "#76D1FF",
    val borderWidthDp: Int = 1,
    val cornerRadiusDp: Int = 24,
    val horizontalPaddingDp: Int = 24,
    val verticalPaddingDp: Int = 24,
    val topSafeMarginFraction: Float = 0.24f, // Reserves top 24% for lock-screen clock
    val bottomSafeMarginFraction: Float = 0.10f, // Reserves bottom 10% for shortcuts/pill
    val titleFontFamily: TemplateFontFamily = TemplateFontFamily.SERIF,
    val bodyFontFamily: TemplateFontFamily = TemplateFontFamily.SANS,
    val bodyFontScale: Float = 1.0f, // 0.8f..1.4f
    val textAlignment: TemplateAlignment = TemplateAlignment.START,
    val showCategoryPill: Boolean = true,
    val showQuoteMark: Boolean = false,
    val splitHeaderBand: Boolean = false,
    val formulaStageFramed: Boolean = true,
    val diagramHeightFraction: Float = 0.45f,
    val defaultSourceRefMode: String = "COMPACT", // FULL | COMPACT | PAGE_ONLY | HIDDEN
)

@Serializable
enum class BackgroundStyle { SOLID, SUBTLE_GRADIENT, AMOLED_PURE_BLACK }

@Serializable
enum class TemplateFontFamily { SERIF, SANS, MONOSPACE }

@Serializable
enum class TemplateAlignment { START, CENTER }

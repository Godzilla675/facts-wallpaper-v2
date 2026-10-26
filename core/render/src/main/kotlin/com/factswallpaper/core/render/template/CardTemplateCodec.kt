package com.factswallpaper.core.render.template

import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.TemplateEngineKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Serializes and validates [CardTemplateSpec] documents (§7.1, §7.4).
 *
 * Validation is intentionally strict: a malformed template must be rejected before it can
 * reach the rasterizer so a corrupt JSON document can never crash the wallpaper service.
 */
object CardTemplateCodec {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
        prettyPrint = false
    }

    private val colorRegex = Regex("^#[0-9A-Fa-f]{6}$")

    fun encode(spec: CardTemplateSpec): String = json.encodeToString(CardTemplateSpec.serializer(), spec)

    fun decode(serialized: String): CardTemplateSpec? =
        try {
            json.decodeFromString(CardTemplateSpec.serializer(), serialized)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun decodeOrThrow(serialized: String): CardTemplateSpec =
        json.decodeFromString(CardTemplateSpec.serializer(), serialized)

    fun validate(spec: CardTemplateSpec): List<String> = buildList {
        if (spec.id.isBlank()) add("Template id must not be blank")
        if (spec.name.isBlank()) add("Template name must not be blank")
        if (CardTemplateType.entries.none { it.name == spec.archetype }) {
            add("Unknown archetype '${spec.archetype}'")
        }
        listOf(
            spec.lightBackgroundHex,
            spec.darkBackgroundHex,
            spec.amoledBackgroundHex,
            spec.cardSurfaceLightHex,
            spec.cardSurfaceDarkHex,
            spec.cardSurfaceAmoledHex,
            spec.accentHexLight,
            spec.accentHexDark,
        ).forEach { color ->
            if (!colorRegex.matches(color)) add("Invalid color '$color'")
        }
        if (spec.borderWidthDp !in 0..8) add("Border width must be between 0 and 8 dp")
        if (spec.cornerRadiusDp !in 0..48) add("Corner radius must be between 0 and 48 dp")
        if (spec.horizontalPaddingDp !in 8..96) add("Horizontal padding must be between 8 and 96 dp")
        if (spec.verticalPaddingDp !in 8..96) add("Vertical padding must be between 8 and 96 dp")
        if (spec.bodyFontScale !in 0.8f..1.4f) add("Body font scale must be between 0.8 and 1.4")
        if (spec.topSafeMarginFraction !in 0.05f..0.45f) {
            add("Top safe margin must be between 0.05 and 0.45")
        }
        if (spec.bottomSafeMarginFraction !in 0f..0.3f) {
            add("Bottom safe margin must be between 0 and 0.30")
        }
        if (spec.diagramHeightFraction !in 0.2f..0.8f) {
            add("Diagram height fraction must be between 0.2 and 0.8")
        }
        if (SourceReferenceMode.entries.none { it.name == spec.defaultSourceRefMode }) {
            add("Unknown source reference mode '${spec.defaultSourceRefMode}'")
        }
    }

    fun isValid(spec: CardTemplateSpec): Boolean = validate(spec).isEmpty()

    fun toCardTemplate(
        spec: CardTemplateSpec,
        builtIn: Boolean,
        isDefault: Boolean = false,
        version: Int = 1,
        createdAtEpochMs: Long = 0L,
    ): CardTemplate = CardTemplate(
        id = spec.id,
        name = spec.name,
        engineKind = TemplateEngineKind.NATIVE_JSON,
        schemaOrMarkup = encode(spec),
        version = version,
        builtIn = builtIn,
        isDefault = isDefault,
        createdAtEpochMs = createdAtEpochMs,
    )

    fun fromCardTemplate(template: CardTemplate): CardTemplateSpec? =
        if (template.engineKind == TemplateEngineKind.NATIVE_JSON) {
            decode(template.schemaOrMarkup)
        } else {
            null
        }
}

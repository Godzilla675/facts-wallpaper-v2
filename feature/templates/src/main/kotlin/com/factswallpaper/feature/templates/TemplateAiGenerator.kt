package com.factswallpaper.feature.templates

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.template.BackgroundStyle
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSpec
import com.factswallpaper.core.render.template.TemplateAlignment
import com.factswallpaper.core.render.template.TemplateFontFamily
import com.factswallpaper.core.ai.StructuredAiService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class GeneratedTemplate(
    val name: String,
    val spec: CardTemplateSpec,
)

/**
 * Port for F13's `AiTask.TEMPLATE_GENERATION` flow. In P4 `:core:ai` does not exist, so the
 * app ships [LocalTemplateAiGenerator], a deterministic prompt-to-spec generator. A future
 * AI implementation can replace the binding without touching the gallery or customizer.
 */
interface TemplateAiGenerator {
    suspend fun generate(prompt: String): GeneratedTemplate
}

/**
 * Deterministic local interpretation of an aesthetic prompt.
 *
 * This is deliberately not a pretend AI call: it maps explicit visual keywords (palette,
 * typography, alignment, frame, source mode, archetype) onto a real [CardTemplateSpec] and
 * validates it with the same codec used for saved templates. Unrecognized prose produces the
 * clean minimal layout instead of an invented result.
 */
@Singleton
class LocalTemplateAiGenerator @Inject constructor() : TemplateAiGenerator {

    override suspend fun generate(prompt: String): GeneratedTemplate {
        val normalized = prompt.trim()
        require(normalized.isNotEmpty()) { "Prompt must not be blank" }
        val lower = normalized.lowercase()
        val base = BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: BuiltInTemplates.specs.first()

        var spec = base.copy(
            id = "custom_${UUID.randomUUID()}",
            name = generatedName(lower),
        )
        var palette = Palette.default()

        when {
            lower.hasAny("neon", "cyber", "glow", "electric") -> {
                palette = Palette(
                    lightBackground = "#F2F7FF",
                    darkBackground = "#070B12",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#0E1525",
                    amoledSurface = "#05080D",
                    accentLight = "#00639B",
                    accentDark = "#00E5FF",
                )
                spec = spec.copy(
                    titleFontFamily = TemplateFontFamily.MONOSPACE,
                    borderWidthDp = 2,
                    backgroundStyle = BackgroundStyle.SUBTLE_GRADIENT,
                )
            }

            lower.hasAny("vintage", "parchment", "warm", "paper", "antique") -> {
                palette = Palette(
                    lightBackground = "#F2E6CF",
                    darkBackground = "#211A13",
                    amoledBackground = "#000000",
                    lightSurface = "#FFF9EC",
                    darkSurface = "#2F251A",
                    amoledSurface = "#07090C",
                    accentLight = "#8A5A2B",
                    accentDark = "#E0B072",
                )
                spec = spec.copy(
                    titleFontFamily = TemplateFontFamily.SERIF,
                    bodyFontFamily = TemplateFontFamily.SERIF,
                    cornerRadiusDp = 18,
                )
            }

            lower.hasAny("amoled", "oled", "pure black") -> {
                palette = Palette(
                    lightBackground = "#FAF8F5",
                    darkBackground = "#0F1419",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#1B2026",
                    amoledSurface = "#000000",
                    accentLight = "#006684",
                    accentDark = "#64B5F6",
                )
                spec = spec.copy(backgroundStyle = BackgroundStyle.AMOLED_PURE_BLACK)
            }

            lower.hasAny("ocean", "blue", "cool", "sky") -> {
                palette = Palette(
                    lightBackground = "#E9F4F9",
                    darkBackground = "#081820",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#10242E",
                    amoledSurface = "#050A0E",
                    accentLight = "#005B7F",
                    accentDark = "#7FD4FF",
                )
            }

            lower.hasAny("forest", "green", "botanical") -> {
                palette = Palette(
                    lightBackground = "#EAF1E7",
                    darkBackground = "#0C160E",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#15251A",
                    amoledSurface = "#050805",
                    accentLight = "#2E6B3F",
                    accentDark = "#8FD3A2",
                )
            }

            lower.hasAny("sunset", "rose", "pink", "romantic") -> {
                palette = Palette(
                    lightBackground = "#FBE9E6",
                    darkBackground = "#1A0F11",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#2A171B",
                    amoledSurface = "#080405",
                    accentLight = "#9C4146",
                    accentDark = "#FFB3B8",
                )
            }

            lower.hasAny("midnight", "night", "dark", "charcoal") -> {
                palette = Palette(
                    lightBackground = "#FAF8F5",
                    darkBackground = "#0B1117",
                    amoledBackground = "#000000",
                    lightSurface = "#FFFFFF",
                    darkSurface = "#151E27",
                    amoledSurface = "#070A0E",
                    accentLight = "#006684",
                    accentDark = "#76D1FF",
                )
            }
        }

        spec = spec.copy(
            lightBackgroundHex = palette.lightBackground,
            darkBackgroundHex = palette.darkBackground,
            amoledBackgroundHex = palette.amoledBackground,
            cardSurfaceLightHex = palette.lightSurface,
            cardSurfaceDarkHex = palette.darkSurface,
            cardSurfaceAmoledHex = palette.amoledSurface,
            accentHexLight = palette.accentLight,
            accentHexDark = palette.accentDark,
        )

        if (lower.hasAny("serif", "editorial", "book")) {
            spec = spec.copy(
                titleFontFamily = TemplateFontFamily.SERIF,
                bodyFontFamily = TemplateFontFamily.SERIF,
            )
        }
        if (lower.hasAny("monospace", "typewriter", "technical", "code")) {
            spec = spec.copy(
                titleFontFamily = TemplateFontFamily.MONOSPACE,
                bodyFontFamily = TemplateFontFamily.MONOSPACE,
            )
        }
        if (lower.hasAny("sans", "modern", "clean")) {
            spec = spec.copy(bodyFontFamily = TemplateFontFamily.SANS)
        }
        if (lower.hasAny("center", "centered")) {
            spec = spec.copy(textAlignment = TemplateAlignment.CENTER)
        }
        if (lower.hasAny("large", "big", "oversized", "headline")) {
            spec = spec.copy(bodyFontScale = 1.2f)
        }
        if (lower.hasAny("compact", "small", "dense")) {
            spec = spec.copy(bodyFontScale = 0.9f)
        }
        if (lower.hasAny("rounded", "soft")) {
            spec = spec.copy(cornerRadiusDp = 32)
        }
        if (lower.hasAny("sharp", "square", "geometric")) {
            spec = spec.copy(cornerRadiusDp = 6)
        }
        if (lower.hasAny("border", "framed", "frame")) {
            spec = spec.copy(borderWidthDp = 2)
        }
        if (lower.hasAny("gradient")) {
            spec = spec.copy(backgroundStyle = BackgroundStyle.SUBTLE_GRADIENT)
        }
        if (lower.hasAny("split header", "header band", "band")) {
            spec = spec.copy(splitHeaderBand = true)
        }
        if (lower.hasAny("quote", "quotation")) {
            spec = spec.copy(
                archetype = CardTemplateType.QUOTE.name,
                showQuoteMark = true,
                textAlignment = TemplateAlignment.CENTER,
            )
        }
        if (lower.hasAny("formula", "math", "equation")) {
            spec = spec.copy(archetype = CardTemplateType.FORMULA.name)
        }
        if (lower.hasAny("diagram", "chart", "graph")) {
            spec = spec.copy(archetype = CardTemplateType.DIAGRAM.name)
        }
        if (lower.hasAny("hidden source", "no source", "hide source")) {
            spec = spec.copy(defaultSourceRefMode = SourceReferenceMode.HIDDEN.name)
        } else if (lower.hasAny("full source", "full citation", "book and page")) {
            spec = spec.copy(defaultSourceRefMode = SourceReferenceMode.FULL.name)
        } else if (lower.hasAny("page number", "page only", "page badge")) {
            spec = spec.copy(defaultSourceRefMode = SourceReferenceMode.PAGE_ONLY.name)
        }

        val errors = CardTemplateCodec.validate(spec)
        check(errors.isEmpty()) { "Generated template failed validation: ${errors.joinToString()}" }
        return GeneratedTemplate(name = spec.name, spec = spec)
    }

    private fun generatedName(lower: String): String {
        val quoted = Regex("\"([^\"]{2,32})\"").find(lower)?.groupValues?.getOrNull(1)
        if (quoted != null) return quoted.trim().replaceFirstChar { it.uppercase() }
        val label = when {
            lower.hasAny("vintage", "parchment", "warm", "paper") -> "Vintage Parchment"
            lower.hasAny("neon", "cyber", "glow") -> "Neon Study"
            lower.hasAny("amoled", "oled", "pure black") -> "Midnight AMOLED"
            lower.hasAny("ocean", "blue", "cool", "sky") -> "Ocean Study"
            lower.hasAny("forest", "green", "botanical") -> "Botanical Notes"
            lower.hasAny("sunset", "rose", "pink") -> "Warm Sunset"
            lower.hasAny("midnight", "night", "dark") -> "Midnight Reading"
            else -> "Custom Study"
        }
        return "$label Card"
    }

    private fun String.hasAny(vararg needles: String): Boolean = needles.any { contains(it) }

    private data class Palette(
        val lightBackground: String,
        val darkBackground: String,
        val amoledBackground: String,
        val lightSurface: String,
        val darkSurface: String,
        val amoledSurface: String,
        val accentLight: String,
        val accentDark: String,
    ) {
        companion object {
            fun default() = Palette(
                lightBackground = "#FAF8F5",
                darkBackground = "#0F1419",
                amoledBackground = "#000000",
                lightSurface = "#FFFFFF",
                darkSurface = "#1B2026",
                amoledSurface = "#070A0E",
                accentLight = "#006684",
                accentDark = "#76D1FF",
            )
        }
    }
}

/**
 * Real AI implementation of [TemplateAiGenerator] using [StructuredAiService] (spec F13, §6.6).
 * Generates a [CardTemplateSpec] via AiTask.TEMPLATE_GENERATION and validates it with [CardTemplateCodec].
 */
@Singleton
class DefaultTemplateAiGenerator @Inject constructor(
    private val structuredAiService: StructuredAiService,
) : TemplateAiGenerator {

    override suspend fun generate(prompt: String): GeneratedTemplate {
        val normalized = prompt.trim()
        require(normalized.isNotEmpty()) { "Prompt must not be blank" }

        val result = structuredAiService.generateTemplate(normalized)
        val rawJson = result.templateJson

        val parsedElement = runCatching {
            Json.parseToJsonElement(rawJson).jsonObject
        }.getOrNull()

        val jsonToDecode = if (parsedElement != null && !parsedElement.containsKey("id")) {
            val mutable = parsedElement.toMutableMap()
            mutable["id"] = JsonPrimitive("custom_${UUID.randomUUID()}")
            if (!mutable.containsKey("archetype")) {
                mutable["archetype"] = JsonPrimitive(CardTemplateType.TEXT.name)
            }
            JsonObject(mutable).toString()
        } else {
            rawJson
        }

        val decoded = CardTemplateCodec.decode(jsonToDecode)
            ?: throw IllegalStateException("AI returned invalid template JSON: unable to decode CardTemplateSpec")

        val finalSpec = if (decoded.id.isBlank()) {
            decoded.copy(id = "custom_${UUID.randomUUID()}")
        } else {
            decoded
        }

        val errors = CardTemplateCodec.validate(finalSpec)
        if (errors.isNotEmpty()) {
            throw IllegalStateException("Generated template failed validation: ${errors.joinToString("; ")}")
        }

        return GeneratedTemplate(name = finalSpec.name, spec = finalSpec)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TemplatesModule {

    @Binds
    abstract fun bindTemplateAiGenerator(generator: DefaultTemplateAiGenerator): TemplateAiGenerator
}

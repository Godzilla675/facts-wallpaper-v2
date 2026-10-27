package com.factswallpaper.core.render.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.render.R
import com.factswallpaper.core.render.cache.RenderCacheKey
import com.factswallpaper.core.render.template.BackgroundStyle
import java.io.ByteArrayOutputStream
import com.factswallpaper.core.render.template.CardTemplateSpec
import com.factswallpaper.core.render.template.TemplateAlignment
import com.factswallpaper.core.render.template.TemplateFontFamily
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class CardRenderOptions(
    val width: Int = 1080,
    val height: Int = 2400,
    val targetSurface: WallpaperTarget = WallpaperTarget.LIVE,
    val themeMode: ThemeMode = ThemeMode.AMOLED,
    val sourceReferenceMode: SourceReferenceMode = SourceReferenceMode.COMPACT,
)

data class RenderedCard(
    val bitmap: Bitmap,
    val cacheKey: String,
    val effectiveSourceMode: SourceReferenceMode,
    val missingStage: Boolean,
) {
    fun toPngBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        output.toByteArray()
    }
}

/**
 * Offscreen card compositor from §7.5.
 *
 * The renderer is deliberately a pure pixel operation: it never touches Room or the file
 * system, which keeps it deterministic and easy to verify in tests. Persistence and cache
 * lookup are handled by [com.factswallpaper.core.render.CardRenderCoordinator].
 */
@Singleton
class CardBitmapRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun render(
        fact: Fact,
        template: CardTemplate,
        spec: CardTemplateSpec,
        options: CardRenderOptions,
        stageBitmap: Bitmap? = null,
    ): RenderedCard = withContext(dispatchers.default) {
        require(options.width > 0 && options.height > 0) { "Render size must be positive" }
        val effectiveMode = fact.sourceReferenceModeOverride ?: options.sourceReferenceMode
        val cacheKey = RenderCacheKey.forFact(
            fact = fact,
            template = template,
            width = options.width,
            height = options.height,
            targetSurface = options.targetSurface,
            themeKey = options.themeMode.name,
            sourceReferenceMode = effectiveMode,
        )
        val palette = paletteFor(spec, options.themeMode)
        val bitmap = Bitmap.createBitmap(options.width, options.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = options.width / 360f
        drawBackground(canvas, options, spec, palette, scale)

        val card = RectF(
            options.width * 0.055f,
            options.height * spec.topSafeMarginFraction,
            options.width * 0.945f,
            options.height * (1f - spec.bottomSafeMarginFraction),
        )
        val corner = spec.cornerRadiusDp * scale
        drawCardContainer(canvas, card, corner, spec, palette, scale)

        val inner = RectF(
            card.left + spec.horizontalPaddingDp * scale,
            card.top + spec.verticalPaddingDp * scale,
            card.right - spec.horizontalPaddingDp * scale,
            card.bottom - spec.verticalPaddingDp * scale,
        )
        var contentTop = inner.top
        val headerHeight = if (spec.splitHeaderBand) {
            minOf(card.height() * 0.20f, 92f * scale)
        } else {
            0f
        }
        if (spec.splitHeaderBand) {
            drawSplitHeader(canvas, card, corner, inner, headerHeight, spec, palette, scale, fact)
            contentTop = inner.top + headerHeight + 18f * scale
        }

        if (spec.showCategoryPill) {
            contentTop = drawCategoryPill(
                canvas = canvas,
                fact = fact,
                left = inner.left,
                top = contentTop,
                scale = scale,
                palette = palette,
                centered = spec.textAlignment == TemplateAlignment.CENTER,
                innerWidth = inner.width(),
            ) + 20f * scale
        }

        if (spec.showQuoteMark) {
            drawQuoteMark(canvas, inner, contentTop, palette, scale)
            contentTop += 82f * scale
        }

        val stageMarkup = com.factswallpaper.core.render.diagram.StageMarkup.of(fact)
        var missingStage = false
        if (stageMarkup != null) {
            val stageHeight = when {
                stageMarkup.kind == com.factswallpaper.core.render.diagram.MarkupKind.LATEX ->
                    inner.height() * 0.30f
                else -> inner.height() * spec.diagramHeightFraction
            }
            val stageRect = RectF(inner.left, contentTop, inner.right, contentTop + stageHeight)
            missingStage = drawStage(
                canvas = canvas,
                rect = stageRect,
                bitmap = stageBitmap,
                framed = spec.formulaStageFramed || stageMarkup.kind != com.factswallpaper.core.render.diagram.MarkupKind.LATEX,
                fallbackText = stageMarkup.source,
                palette = palette,
                scale = scale,
            )
            contentTop = stageRect.bottom + 22f * scale
        }

        val footerHeight = if (effectiveMode == SourceReferenceMode.HIDDEN) 0f else 62f * scale
        val bodyBottom = inner.bottom - footerHeight
        val bodyHeight = (bodyBottom - contentTop).coerceAtLeast(20f)
        val text = formattedBody(fact, spec)
        drawBodyText(
            canvas = canvas,
            text = text,
            left = inner.left,
            top = contentTop,
            width = inner.width(),
            maxHeight = bodyHeight,
            spec = spec,
            scale = scale,
            palette = palette,
        )

        if (footerHeight > 0f && !fact.sourceBookTitle.isBlank()) {
            drawFooter(canvas, inner, effectiveMode, fact, palette, scale, spec.textAlignment)
        }

        RenderedCard(
            bitmap = bitmap,
            cacheKey = cacheKey,
            effectiveSourceMode = effectiveMode,
            missingStage = missingStage,
        )
    }

    private fun paletteFor(spec: CardTemplateSpec, themeMode: ThemeMode): CardPalette =
        when {
            themeMode == ThemeMode.AMOLED ||
                spec.backgroundStyle == BackgroundStyle.AMOLED_PURE_BLACK -> CardPalette(
                background = "#000000",
                surface = spec.cardSurfaceAmoledHex,
                surfaceAlt = blend(spec.cardSurfaceAmoledHex, spec.accentHexDark, 0.16f),
                foreground = "#E2E8F0",
                muted = "#8B98A5",
                accent = spec.accentHexDark,
                border = "#1A222B",
            )
            themeMode == ThemeMode.LIGHT -> CardPalette(
                background = spec.lightBackgroundHex,
                surface = spec.cardSurfaceLightHex,
                surfaceAlt = blend(spec.cardSurfaceLightHex, spec.accentHexLight, 0.10f),
                foreground = "#1A1D21",
                muted = "#5D6673",
                accent = spec.accentHexLight,
                border = blend(spec.cardSurfaceLightHex, "#000000", 0.16f),
            )
            else -> CardPalette(
                background = spec.darkBackgroundHex,
                surface = spec.cardSurfaceDarkHex,
                surfaceAlt = blend(spec.cardSurfaceDarkHex, spec.accentHexDark, 0.12f),
                foreground = "#E8EEF5",
                muted = "#9AA8B6",
                accent = spec.accentHexDark,
                border = blend(spec.cardSurfaceDarkHex, "#FFFFFF", 0.14f),
            )
        }

    private fun drawBackground(
        canvas: Canvas,
        options: CardRenderOptions,
        spec: CardTemplateSpec,
        palette: CardPalette,
        scale: Float,
    ) {
        val isPureBlack = options.themeMode == ThemeMode.AMOLED ||
            spec.backgroundStyle == BackgroundStyle.AMOLED_PURE_BLACK
        when {
            isPureBlack -> {
                canvas.drawColor(Color.BLACK)
            }
            spec.backgroundStyle == BackgroundStyle.SUBTLE_GRADIENT -> {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f,
                        0f,
                        0f,
                        options.height.toFloat(),
                        palette.background.toColorInt(),
                        blend(palette.background, palette.surface, 0.65f).toColorInt(),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(0f, 0f, options.width.toFloat(), options.height.toFloat(), paint)
            }
            else -> canvas.drawColor(palette.background.toColorInt())
        }
        if (!isPureBlack) {
            // A faint accent wash keeps the reserved clock area visually connected to the card.
            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent.toColorInt()
                alpha = 12
            }
            canvas.drawCircle(
                options.width * 0.86f,
                options.height * spec.topSafeMarginFraction * 0.52f,
                options.width * 0.42f,
                wash,
            )
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = palette.accent.toColorInt()
                alpha = 38
                strokeWidth = 2f * scale
            }
            canvas.drawLine(
                options.width * 0.42f,
                options.height * 0.5f,
                options.width * 0.96f,
                options.height * 0.5f,
                rule,
            )
        }
    }

    private fun drawCardContainer(
        canvas: Canvas,
        card: RectF,
        corner: Float,
        spec: CardTemplateSpec,
        palette: CardPalette,
        scale: Float,
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surface.toColorInt() }
        canvas.drawRoundRect(card, corner, corner, fill)
        if (spec.borderWidthDp > 0) {
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = spec.borderWidthDp * scale
                color = palette.border.toColorInt()
            }
            val inset = border.strokeWidth / 2f
            val borderRect = RectF(card).apply { inset(inset, inset) }
            canvas.drawRoundRect(borderRect, corner, corner, border)
        }
        val accentBar = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.toColorInt()
        }
        canvas.drawRoundRect(
            RectF(card.left + 28f * scale, card.top, card.left + 34f * scale, card.bottom),
            3f * scale,
            3f * scale,
            accentBar,
        )
    }

    private fun drawSplitHeader(
        canvas: Canvas,
        card: RectF,
        corner: Float,
        inner: RectF,
        headerHeight: Float,
        spec: CardTemplateSpec,
        palette: CardPalette,
        scale: Float,
        fact: Fact,
    ) {
        val clip = Path().apply {
            addRoundRect(card, corner, corner, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(clip)
        val band = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.surfaceAlt.toColorInt()
        }
        canvas.drawRect(
            card.left,
            card.top,
            card.right,
            card.top + spec.verticalPaddingDp * scale + headerHeight,
            band,
        )
        val accentRule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.toColorInt()
            strokeWidth = 3f * scale
        }
        val bandBottom = card.top + spec.verticalPaddingDp * scale + headerHeight
        canvas.drawLine(card.left, bandBottom, card.right, bandBottom, accentRule)
        canvas.restore()

        val titlePaint = textPaint(
            spec.titleFontFamily,
            minOf(15f * scale, headerHeight * 0.34f),
            palette.accent,
            bold = true,
        )
        val title = fact.sourceLessonTitle?.uppercase()
            ?: fact.sourceBookTitle.uppercase().take(48)
        canvas.drawText(
            ellipsize(title, titlePaint, inner.width()),
            inner.left,
            inner.top + titlePaint.textSize,
            titlePaint,
        )
        val subtitlePaint = textPaint(
            spec.bodyFontFamily,
            minOf(12f * scale, headerHeight * 0.24f),
            palette.muted,
        )
        val subtitle = listOfNotNull(
            fact.sourceBookTitle.takeIf { it.isNotBlank() },
            fact.sourcePage?.let { context.getString(R.string.card_page_short, it) },
        ).joinToString(" · ")
        canvas.drawText(
            ellipsize(subtitle, subtitlePaint, inner.width()),
            inner.left,
            inner.top + titlePaint.textSize + subtitlePaint.textSize + 8f * scale,
            subtitlePaint,
        )
    }

    private fun drawCategoryPill(
        canvas: Canvas,
        fact: Fact,
        left: Float,
        top: Float,
        scale: Float,
        palette: CardPalette,
        centered: Boolean,
        innerWidth: Float,
    ): Float {
        val label = when {
            fact.hasFormula -> context.getString(R.string.card_label_formula)
            fact.hasDiagram -> context.getString(R.string.card_label_diagram)
            else -> displayName(fact.templateType)
        }
        val paint = textPaint(TemplateFontFamily.SANS, 12f * scale, palette.accent, bold = true)
        val textWidth = paint.measureText(label)
        val pillWidth = textWidth + 30f * scale
        val pillHeight = 30f * scale
        val x = if (centered) left + (innerWidth - pillWidth) / 2f else left
        val pill = RectF(x, top, x + pillWidth, top + pillHeight)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.accent.toColorInt()
            alpha = 32
        }
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, fill)
        canvas.drawText(
            label,
            x + 15f * scale,
            top + pillHeight / 2f - (paint.descent() + paint.ascent()) / 2f,
            paint,
        )
        return pill.bottom
    }

    private fun drawQuoteMark(
        canvas: Canvas,
        inner: RectF,
        top: Float,
        palette: CardPalette,
        scale: Float,
    ) {
        val paint = textPaint(TemplateFontFamily.SERIF, 120f * scale, palette.accent, bold = true).apply {
            alpha = 70
        }
        canvas.drawText("\u201C", inner.left, top + 90f * scale, paint)
    }

    /** Returns true when the source had to be shown as plain text fallback. */
    private fun drawStage(
        canvas: Canvas,
        rect: RectF,
        bitmap: Bitmap?,
        framed: Boolean,
        fallbackText: String,
        palette: CardPalette,
        scale: Float,
    ): Boolean {
        if (framed) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.surfaceAlt.toColorInt() }
            val corner = 18f * scale
            canvas.drawRoundRect(rect, corner, corner, fill)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * scale
                color = palette.border.toColorInt()
            }
            canvas.drawRoundRect(rect, corner, corner, border)
        }

        if (bitmap != null && !bitmap.isRecycled) {
            val padding = 16f * scale * if (framed) 1.6f else 1f
            val availableWidth = (rect.width() - padding * 2f).coerceAtLeast(1f)
            val availableHeight = (rect.height() - padding * 2f).coerceAtLeast(1f)
            val ratio = minOf(availableWidth / bitmap.width, availableHeight / bitmap.height)
            val drawWidth = bitmap.width * ratio
            val drawHeight = bitmap.height * ratio
            val destination = RectF(
                rect.centerX() - drawWidth / 2f,
                rect.centerY() - drawHeight / 2f,
                rect.centerX() + drawWidth / 2f,
                rect.centerY() + drawHeight / 2f,
            )
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                isDither = true
            }
            canvas.drawBitmap(bitmap, null, destination, paint)
            return false
        }

        val chipHeight = 26f * scale
        val chipPaint = textPaint(TemplateFontFamily.SANS, 10.5f * scale, palette.muted, bold = true)
        val chipText = context.getString(R.string.card_label_diagram_pending)
        val chipWidth = chipPaint.measureText(chipText) + 22f * scale
        val chip = RectF(
            rect.left + 14f * scale,
            rect.top + 12f * scale,
            rect.left + 14f * scale + chipWidth,
            rect.top + 12f * scale + chipHeight,
        )
        val chipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted.toColorInt()
            alpha = 34
        }
        canvas.drawRoundRect(chip, chipHeight / 2f, chipHeight / 2f, chipFill)
        canvas.drawText(
            chipText,
            chip.left + 11f * scale,
            chip.centerY() - (chipPaint.descent() + chipPaint.ascent()) / 2f,
            chipPaint,
        )

        val sourcePaint = textPaint(TemplateFontFamily.MONOSPACE, 13f * scale, palette.muted)
        val sourceTop = chip.bottom + 8f * scale
        val sourceWidth = (rect.width() - 28f * scale).coerceAtLeast(1f)
        val sourceHeight = (rect.bottom - sourceTop - 10f * scale).coerceAtLeast(1f)
        val layout = fitLayout(
            text = fallbackText,
            paint = sourcePaint,
            width = sourceWidth.toInt().coerceAtLeast(1),
            maxHeight = sourceHeight.toInt().coerceAtLeast(1),
            minTextSize = 9f * scale,
            alignment = Layout.Alignment.ALIGN_NORMAL,
        )
        canvas.save()
        canvas.translate(rect.left + 14f * scale, sourceTop)
        layout.draw(canvas)
        canvas.restore()
        return true
    }

    private fun formattedBody(fact: Fact, spec: CardTemplateSpec): String {
        val text = fact.text.trim()
        if (spec.archetype != CardTemplateType.BULLET.name) return text
        val segments = text
            .split('\n', ';')
            .map { it.trim().trimStart('-', '*', '\u2022').trim() }
            .filter { it.isNotEmpty() }
        return if (segments.size <= 1) text else segments.joinToString("\n") { "\u2022 $it" }
    }

    private fun drawBodyText(
        canvas: Canvas,
        text: String,
        left: Float,
        top: Float,
        width: Float,
        maxHeight: Float,
        spec: CardTemplateSpec,
        scale: Float,
        palette: CardPalette,
    ) {
        if (text.isBlank() || width <= 0f || maxHeight <= 0f) return
        val baseSize = width * 0.118f * spec.bodyFontScale
        val minSize = (9.5f * scale).coerceAtMost(baseSize)
        val paint = textPaint(
            family = spec.bodyFontFamily,
            size = baseSize,
            colorHex = palette.foreground,
            italic = spec.showQuoteMark,
        ).apply {
            letterSpacing = if (spec.showQuoteMark) 0.008f else 0.002f
        }
        val alignment = if (spec.textAlignment == TemplateAlignment.CENTER) {
            Layout.Alignment.ALIGN_CENTER
        } else {
            Layout.Alignment.ALIGN_NORMAL
        }
        val layout = fitLayout(
            text = text,
            paint = paint,
            width = width.toInt().coerceAtLeast(1),
            maxHeight = maxHeight.toInt().coerceAtLeast(1),
            minTextSize = minSize,
            alignment = alignment,
        )
        canvas.save()
        canvas.clipRect(left, top, left + width, top + maxHeight)
        canvas.translate(left, top)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun fitLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        maxHeight: Int,
        minTextSize: Float,
        alignment: Layout.Alignment,
    ): StaticLayout {
        var size = paint.textSize
        val floor = minTextSize.coerceAtMost(size)
        var layout = buildLayout(text, paint, width, alignment)
        while (layout.height > maxHeight && size > floor) {
            size = (size - 1.5f).coerceAtLeast(floor)
            paint.textSize = size
            layout = buildLayout(text, paint, width, alignment)
        }
        if (layout.height > maxHeight) {
            val lineHeight = layout.getLineBottom(0) - layout.getLineTop(0)
            val maxLines = (maxHeight / lineHeight.coerceAtLeast(1).toFloat()).toInt().coerceAtLeast(1)
            val ellipsized = TextUtils.ellipsize(
                text,
                paint,
                width.toFloat() * maxLines,
                TextUtils.TruncateAt.END,
            )
            layout = buildLayout(ellipsized.toString(), paint, width, alignment)
            while (layout.height > maxHeight && maxLines > 1) {
                val tighter = TextUtils.ellipsize(
                    text,
                    paint,
                    width.toFloat() * (maxLines - 1),
                    TextUtils.TruncateAt.END,
                )
                layout = buildLayout(tighter.toString(), paint, width, alignment)
                break
            }
        }
        return layout
    }

    private fun buildLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment,
    ): StaticLayout = StaticLayout.Builder
        .obtain(text, 0, text.length, paint, width)
        .setAlignment(alignment)
        .setIncludePad(false)
        .setLineSpacing(0f, 1.16f)
        .setBreakStrategy(LineBreaker.BREAK_STRATEGY_HIGH_QUALITY)
        .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
        .build()

    private fun drawFooter(
        canvas: Canvas,
        inner: RectF,
        mode: SourceReferenceMode,
        fact: Fact,
        palette: CardPalette,
        scale: Float,
        alignment: TemplateAlignment,
    ) {
        val provenance = provenance(fact, mode) ?: return
        val paint = textPaint(TemplateFontFamily.SANS, 11f * scale, palette.muted)
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.border.toColorInt()
            strokeWidth = 1f * scale
        }
        val lineY = inner.bottom - 36f * scale
        canvas.drawLine(inner.left, lineY, inner.right, lineY, divider)
        val x = when (alignment) {
            TemplateAlignment.CENTER -> inner.centerX() - paint.measureText(provenance) / 2f
            TemplateAlignment.START -> inner.left
        }
        canvas.drawText(
            ellipsize(provenance, paint, inner.width()),
            x,
            inner.bottom - 8f * scale,
            paint,
        )
    }

    private fun provenance(fact: Fact, mode: SourceReferenceMode): String? = when (mode) {
        SourceReferenceMode.HIDDEN -> null
        SourceReferenceMode.PAGE_ONLY -> fact.sourcePage?.let {
            context.getString(R.string.card_page_short, it)
        }
        SourceReferenceMode.COMPACT -> listOfNotNull(
            fact.sourceBookTitle.takeIf { it.isNotBlank() },
            fact.sourcePage?.let { context.getString(R.string.card_page_short, it) },
        ).joinToString("  \u2022  ").ifBlank { null }
        SourceReferenceMode.FULL -> listOfNotNull(
            fact.sourceBookTitle.takeIf { it.isNotBlank() },
            fact.sourceLessonTitle?.takeIf { it.isNotBlank() },
            fact.sourcePage?.let { context.getString(R.string.card_page_short, it) },
        ).joinToString("  \u2022  ").ifBlank { null }
    }

    private fun textPaint(
        family: TemplateFontFamily,
        size: Float,
        colorHex: String,
        bold: Boolean = false,
        italic: Boolean = false,
    ): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(
            when (family) {
                TemplateFontFamily.SERIF -> "serif"
                TemplateFontFamily.SANS -> "sans-serif"
                TemplateFontFamily.MONOSPACE -> "monospace"
            },
            when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        textSize = size
        this.color = colorHex.toColorInt()
        isAntiAlias = true
        isSubpixelText = true
    }

    private fun ellipsize(text: String, paint: TextPaint, width: Float): String {
        if (paint.measureText(text) <= width) return text
        return TextUtils.ellipsize(text, paint, width, TextUtils.TruncateAt.END).toString()
    }

    private fun displayName(type: CardTemplateType): String = context.getString(
        when (type) {
            CardTemplateType.TEXT -> R.string.card_type_text
            CardTemplateType.QUOTE -> R.string.card_type_quote
            CardTemplateType.BULLET -> R.string.card_type_bullet
            CardTemplateType.DEFINITION -> R.string.card_type_definition
            CardTemplateType.QA -> R.string.card_type_qa
            CardTemplateType.FORMULA -> R.string.card_type_formula
            CardTemplateType.DIAGRAM -> R.string.card_type_diagram
            CardTemplateType.TIMELINE -> R.string.card_type_timeline
        },
    )

    private fun String.toColorInt(): Int = Color.parseColor(this)

    private fun blend(baseHex: String, overlayHex: String, amount: Float): String {
        val base = baseHex.toColorInt()
        val overlay = overlayHex.toColorInt()
        fun channel(shift: Int): Int {
            val baseChannel = (base shr shift) and 0xFF
            val overlayChannel = (overlay shr shift) and 0xFF
            return (baseChannel + (overlayChannel - baseChannel) * amount).toInt().coerceIn(0, 255)
        }
        return String.format("#%02X%02X%02X", channel(16), channel(8), channel(0))
    }

    private data class CardPalette(
        val background: String,
        val surface: String,
        val surfaceAlt: String,
        val foreground: String,
        val muted: String,
        val accent: String,
        val border: String,
    )
}

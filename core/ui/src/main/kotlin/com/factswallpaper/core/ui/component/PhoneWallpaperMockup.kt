package com.factswallpaper.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.ui.R
import com.factswallpaper.core.ui.format.SourceReferenceFormatter

/**
 * Realistic 9:20 lock-screen mock-up: simulated clock inside the configurable
 * top safe zone and the live fact card underneath (spec 9.3.11). Used by
 * onboarding, the template gallery and the card preview customizer.
 */
@Composable
fun PhoneWallpaperMockup(
    fact: Fact?,
    templateName: String,
    themeMode: ThemeMode,
    sourceReferenceMode: SourceReferenceMode,
    modifier: Modifier = Modifier,
    topSafeInsetFraction: Float = 0.24f,
    bodyFontScale: Float = 1f,
) {
    val palette = wallpaperPalette(themeMode)
    val safeFraction = topSafeInsetFraction.coerceIn(0.18f, 0.32f)
    val bodySize = (17f * bodyFontScale.coerceIn(0.8f, 1.4f)).sp

    Surface(
        modifier = modifier.aspectRatio(9f / 20f),
        shape = RoundedCornerShape(34.dp),
        color = Color(0xFF17181C),
        border = BorderStroke(1.dp, Color(0xFF3A3D44)),
        shadowElevation = 8.dp,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            shape = RoundedCornerShape(28.dp),
            color = palette.background,
            contentColor = palette.content,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(safeFraction),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = Modifier.padding(top = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.phone_clock_time),
                            color = palette.content,
                            fontFamily = FontFamily.Serif,
                            fontSize = 34.sp,
                            lineHeight = 38.sp,
                        )
                        Text(
                            text = stringResource(R.string.phone_clock_date),
                            color = palette.content.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f - safeFraction)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    Text(
                        text = templateName,
                        color = palette.accent,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (fact == null) {
                        Text(
                            text = stringResource(R.string.wallpaper_empty_fallback),
                            color = palette.content,
                            fontSize = bodySize,
                            lineHeight = (bodySize.value * 1.35f).sp,
                            fontFamily = FontFamily.Serif,
                        )
                    } else {
                        Text(
                            text = fact.displayBody(),
                            color = palette.content,
                            fontSize = bodySize,
                            lineHeight = (bodySize.value * 1.35f).sp,
                            fontFamily = FontFamily.Serif,
                            fontStyle = if (fact.templateType == CardTemplateType.QUOTE) FontStyle.Italic else FontStyle.Normal,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (fact.hasFormula && !fact.latex.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = fact.latex.orEmpty(),
                                color = palette.accent,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    val source = SourceReferenceFormatter.format(
                        bookTitle = fact?.sourceBookTitle,
                        lessonTitle = fact?.sourceLessonTitle,
                        page = fact?.sourcePage,
                        mode = sourceReferenceMode,
                        unknownBook = stringResource(R.string.source_unknown_book),
                        pagePrefix = stringResource(R.string.source_page_prefix),
                    )
                    if (source.isNotBlank()) {
                        Text(
                            text = source,
                            color = palette.content.copy(alpha = 0.66f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            textAlign = TextAlign.Start,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Fact.displayBody(): String = when {
    templateType == CardTemplateType.QUOTE -> "\u201C$text\u201D"
    else -> text
}

private data class WallpaperPalette(
    val background: Color,
    val content: Color,
    val accent: Color,
)

private fun wallpaperPalette(themeMode: ThemeMode): WallpaperPalette = when (themeMode) {
    ThemeMode.SYSTEM -> WallpaperPalette(
        background = Color(0xFF10151B),
        content = Color(0xFFE7EAF0),
        accent = Color(0xFF76D1FF),
    )

    ThemeMode.LIGHT -> WallpaperPalette(
        background = Color(0xFFF6F0E7),
        content = Color(0xFF1A1C20),
        accent = Color(0xFF006684),
    )

    ThemeMode.DARK -> WallpaperPalette(
        background = Color(0xFF10151B),
        content = Color(0xFFE7EAF0),
        accent = Color(0xFF76D1FF),
    )

    ThemeMode.AMOLED -> WallpaperPalette(
        background = Color.Black,
        content = Color(0xFFE8EDF4),
        accent = Color(0xFF76D1FF),
    )

    ThemeMode.DYNAMIC -> WallpaperPalette(
        background = Color(0xFF1C1B2E),
        content = Color(0xFFEDEAFE),
        accent = Color(0xFFC7C2EA),
    )
}

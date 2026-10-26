package com.factswallpaper.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Scholar's Slate — Light -----------------------------------------------------
private val LightBackground = Color(0xFFFAF8F5)
private val LightOnBackground = Color(0xFF181C20)
private val LightPrimary = Color(0xFF006684)
private val LightPrimaryContainer = Color(0xFFBFE9FF)
private val LightSecondary = Color(0xFF4C616B)
private val LightTertiary = Color(0xFF5E5B7D)
private val LightError = Color(0xFFBA1A1A)
private val LightOutline = Color(0xFF71787E)
private val LightOutlineVariant = Color(0xFFC1C7CE)

// Scholar's Slate — Dark ------------------------------------------------------
private val DarkBackground = Color(0xFF0F1419)
private val DarkSurface = Color(0xFF13181E)
private val DarkOnSurface = Color(0xFFE1E2E8)
private val DarkOnSurfaceVariant = Color(0xFFC1C7CE)
private val DarkPrimary = Color(0xFF76D1FF)
private val DarkPrimaryContainer = Color(0xFF004D64)
private val DarkSecondary = Color(0xFFB4CAD5)
private val DarkTertiary = Color(0xFFC7C2EA)
private val DarkError = Color(0xFFFFB4AB)
private val DarkOutline = Color(0xFF8B9198)
private val DarkOutlineVariant = Color(0xFF41484D)

// Scholar's Slate — AMOLED ----------------------------------------------------
private val AmoledSurfaceContainerLow = Color(0xFF05070A)
private val AmoledSurfaceContainer = Color(0xFF090D12)
private val AmoledSurfaceContainerHigh = Color(0xFF11161D)
private val AmoledOnSurface = Color(0xFFE2E8F0)
private val AmoledOnSurfaceVariant = Color(0xFF94A3B8)
private val AmoledOutline = Color(0xFF262F3A)
private val AmoledOutlineVariant = Color(0xFF1A222B)

val FactsLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = Color(0xFF001E2B),
    inversePrimary = Color(0xFF7BD0F0),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE6F1),
    onSecondaryContainer = Color(0xFF081E27),
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4DFFF),
    onTertiaryContainer = Color(0xFF1A1836),
    error = LightError,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightBackground,
    onSurface = LightOnBackground,
    surfaceVariant = Color(0xFFE0E3E6),
    onSurfaceVariant = Color(0xFF41484D),
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFF1F0EE),
    surfaceDim = Color(0xFFDAD9D5),
    surfaceBright = LightBackground,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF4F2EE),
    surfaceContainer = Color(0xFFEEECE8),
    surfaceContainerHigh = Color(0xFFE8E6E1),
    surfaceContainerHighest = Color(0xFFE2E0DB),
)

val FactsDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003546),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = Color(0xFFBFE9FF),
    inversePrimary = LightPrimary,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF1F343D),
    secondaryContainer = Color(0xFF364B54),
    onSecondaryContainer = Color(0xFFD0E4EF),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF2F2C4D),
    tertiaryContainer = Color(0xFF464364),
    onTertiaryContainer = Color(0xFFE4DFFF),
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = Color(0xFF000000),
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Color(0xFF2D3135),
    surfaceDim = Color(0xFF0F1419),
    surfaceBright = Color(0xFF353A3F),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF171C22),
    surfaceContainer = Color(0xFF1B2026),
    surfaceContainerHigh = Color(0xFF252B31),
    surfaceContainerHighest = Color(0xFF30363C),
)

val FactsAmoledColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF003546),
    onPrimaryContainer = Color(0xFFBFE9FF),
    inversePrimary = LightPrimary,
    secondary = DarkSecondary,
    onSecondary = Color(0xFF1F343D),
    secondaryContainer = Color(0xFF24363E),
    onSecondaryContainer = Color(0xFFD0E4EF),
    tertiary = DarkTertiary,
    onTertiary = Color(0xFF2F2C4D),
    tertiaryContainer = Color(0xFF343151),
    onTertiaryContainer = Color(0xFFE4DFFF),
    error = DarkError,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C0006),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color.Black,
    onBackground = AmoledOnSurface,
    surface = Color.Black,
    onSurface = AmoledOnSurface,
    surfaceVariant = Color(0xFF1A222B),
    onSurfaceVariant = AmoledOnSurfaceVariant,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant,
    scrim = Color.Black,
    inverseSurface = AmoledOnSurface,
    inverseOnSurface = Color(0xFF1A1F24),
    surfaceDim = Color.Black,
    surfaceBright = Color(0xFF2A3138),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = AmoledSurfaceContainerLow,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    surfaceContainerHighest = Color(0xFF171D25),
)

/** Semantic accents used by fact cards, template chips and verification badges. */
@Immutable
data class FactColors(
    val formulaAccent: Color,
    val formulaContainer: Color,
    val diagramAccent: Color,
    val diagramContainer: Color,
    val quoteAccent: Color,
    val quoteContainer: Color,
    val warningAmber: Color,
    val warningContainer: Color,
    val verifiedGreen: Color,
    val verifiedContainer: Color,
)

val LightFactColors = FactColors(
    formulaAccent = Color(0xFF6B3FA0),
    formulaContainer = Color(0xFFF3E8FF),
    diagramAccent = Color(0xFF006B5E),
    diagramContainer = Color(0xFFD4F7F0),
    quoteAccent = Color(0xFF8B5000),
    quoteContainer = Color(0xFFFFDEA8),
    warningAmber = Color(0xFF7A5900),
    warningContainer = Color(0xFFFFDEA6),
    verifiedGreen = Color(0xFF1B6D36),
    verifiedContainer = Color(0xFFC4F1CE),
)

val DarkFactColors = FactColors(
    formulaAccent = Color(0xFFD0BCFF),
    formulaContainer = Color(0xFF3B1F6E),
    diagramAccent = Color(0xFF66F7DF),
    diagramContainer = Color(0xFF005046),
    quoteAccent = Color(0xFFFFB951),
    quoteContainer = Color(0xFF4A2800),
    warningAmber = Color(0xFFF9BC41),
    warningContainer = Color(0xFF3F2E00),
    verifiedGreen = Color(0xFF7CDA93),
    verifiedContainer = Color(0xFF005221),
)

val LocalFactColors = staticCompositionLocalOf { LightFactColors }

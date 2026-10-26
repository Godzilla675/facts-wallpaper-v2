package com.factswallpaper.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.factswallpaper.core.model.ThemeMode

/**
 * Facts Wallpaper theme. [themeMode] is supplied by DataStore (`appThemeMode`) so
 * the UI theme is independent from the rendered wallpaper theme.
 */
@Composable
fun FactsTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        ThemeMode.LIGHT -> FactsLightColorScheme
        ThemeMode.DARK -> FactsDarkColorScheme
        ThemeMode.AMOLED -> FactsAmoledColorScheme
        ThemeMode.DYNAMIC ->
            if (systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val factColors = when {
        themeMode == ThemeMode.LIGHT -> LightFactColors
        themeMode == ThemeMode.DYNAMIC && !systemDark -> LightFactColors
        else -> DarkFactColors
    }
    CompositionLocalProvider(
        LocalFactColors provides factColors,
        LocalSpacing provides Spacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FactsTypography,
            shapes = FactsShapes,
            content = content,
        )
    }
}

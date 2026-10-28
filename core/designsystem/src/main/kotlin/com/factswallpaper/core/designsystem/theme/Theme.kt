package com.factswallpaper.core.designsystem.theme

import android.os.Build
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
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColorEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
        ThemeMode.DYNAMIC -> systemDark
    }
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val applyDynamic = (dynamicColorEnabled && themeMode != ThemeMode.AMOLED) || themeMode == ThemeMode.DYNAMIC

    val colorScheme = when {
        supportsDynamic && applyDynamic && isDark -> dynamicDarkColorScheme(context)
        supportsDynamic && applyDynamic && !isDark -> dynamicLightColorScheme(context)
        themeMode == ThemeMode.AMOLED -> FactsAmoledColorScheme
        isDark -> FactsDarkColorScheme
        else -> FactsLightColorScheme
    }
    val factColors = if (isDark) DarkFactColors else LightFactColors
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

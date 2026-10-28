package com.factswallpaper.core.ui

import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Whether light haptic feedback is enabled. Provided once at the app root from
 * the `hapticsEnabled` preference so leaf composables stay free of view-model
 * plumbing. Defaults to on for previews and isolated tests.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

/**
 * Returns a callback that performs a light keyboard-tap haptic when the user
 * has not turned haptics off. Safe to call from click handlers.
 */
@Composable
fun rememberHapticFeedback(): () -> Unit {
    val enabled = LocalHapticsEnabled.current
    val view = LocalView.current
    return remember(enabled, view) {
        {
            if (enabled) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }
    }
}

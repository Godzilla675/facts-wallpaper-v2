package com.factswallpaper.core.designsystem.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/** Shared motion tokens so every screen animates with the same rhythm. */
object FactsMotion {
    const val DurationFast = 150
    const val DurationMedium = 300
    const val DurationSlow = 500

    val fastTween = tween<Float>(durationMillis = DurationFast, easing = FastOutSlowInEasing)
    val mediumTween = tween<Float>(durationMillis = DurationMedium, easing = FastOutSlowInEasing)
    val slowTween = tween<Float>(durationMillis = DurationSlow, easing = FastOutSlowInEasing)
}

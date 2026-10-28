package com.factswallpaper.core.datastore

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.DocumentInputMode
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode

/**
 * Typed snapshot of the lightweight global preferences defined in spec §5.4.
 * The two map-valued preferences are stored as JSON text in DataStore exactly as
 * the schema requires, but exposed as typed maps to callers.
 */
data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    val appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperThemeMode: ThemeMode = ThemeMode.AMOLED,
    val routingStrategy: RoutingStrategy = RoutingStrategy.BALANCED,
    val taskProfileOverrides: Map<AiTask, String> = emptyMap(),
    val customTaskPromptSuffixes: Map<AiTask, String> = emptyMap(),
    val defaultOcrPageLimit: Int = 20,
    val allowAdvancedOcrEngines: Boolean = false,
    val preRenderQueueSize: Int = 25,
    val encryptedDatabaseEnabled: Boolean = false,
    val enableAiDiagnosticsLog: Boolean = true,
    val enableDoubleTapWallpaperAdvance: Boolean = false,
    val aiCostGuardThresholdUsd: Double = 0.50,
    val defaultAutoProcessMode: AutoProcessMode = AutoProcessMode.OUTLINE_ONLY,
    val defaultDocumentInputMode: DocumentInputMode = DocumentInputMode.AUTO,
    val maxImagesPerRequest: Int = 10,
    val rotationSource: RotationSource = RotationSource.COLLECTIONS,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 22 * 60,
    val quietHoursEndMinute: Int = 7 * 60,
    val rotationPaused: Boolean = false,
    val batterySaverSlowdownEnabled: Boolean = true,
    val dailyFactNotificationEnabled: Boolean = false,
    val dailyFactNotificationTimeMinutes: Int = 8 * 60,
    val dynamicColorEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val libraryCoachHintDismissed: Boolean = false,
    val outlineCoachHintDismissed: Boolean = false,
)

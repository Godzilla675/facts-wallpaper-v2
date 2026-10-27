package com.factswallpaper.core.datastore

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.DocumentInputMode
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode

/**
 * Typed snapshot of the lightweight global preferences defined in spec §5.4.
 * The two map-valued preferences are stored as JSON text in DataStore exactly as
 * the schema requires, but exposed as typed maps to callers.
 */
data class AppPreferences(
    val onboardingCompleted: Boolean = false,
    val appThemeMode: ThemeMode = ThemeMode.DARK,
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
)

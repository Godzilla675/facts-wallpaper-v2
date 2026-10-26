package com.factswallpaper.core.datastore

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Single source of truth for the app-level DataStore preferences. */
interface SettingsRepository {
    fun observePreferences(): Flow<AppPreferences>

    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setAppThemeMode(mode: ThemeMode)
    suspend fun setWallpaperThemeMode(mode: ThemeMode)
    suspend fun setRoutingStrategy(strategy: RoutingStrategy)
    suspend fun setTaskProfileOverrides(overrides: Map<AiTask, String>)
    suspend fun setTaskProfileOverride(task: AiTask, profileId: String?)
    suspend fun setCustomTaskPromptSuffixes(suffixes: Map<AiTask, String>)
    suspend fun setCustomTaskPromptSuffix(task: AiTask, instruction: String?)
    suspend fun setDefaultOcrPageLimit(limit: Int)
    suspend fun setAllowAdvancedOcrEngines(allowed: Boolean)
    suspend fun setPreRenderQueueSize(size: Int)
    suspend fun setEncryptedDatabaseEnabled(enabled: Boolean)
    suspend fun setEnableAiDiagnosticsLog(enabled: Boolean)
    suspend fun setEnableDoubleTapWallpaperAdvance(enabled: Boolean)
}

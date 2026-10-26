package com.factswallpaper.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.json.Json

@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) : SettingsRepository {

    private val taskMapSerializer = MapSerializer(AiTask.serializer(), String.serializer())

    override fun observePreferences(): Flow<AppPreferences> =
        dataStore.data
            .catch { throwable ->
                if (throwable is IOException) emit(emptyPreferences()) else throw throwable
            }
            .map { it.toAppPreferences(json, taskMapSerializer) }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    override suspend fun setAppThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.APP_THEME_MODE] = mode.name }
    }

    override suspend fun setWallpaperThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.WALLPAPER_THEME_MODE] = mode.name }
    }

    override suspend fun setRoutingStrategy(strategy: RoutingStrategy) {
        dataStore.edit { it[Keys.ROUTING_STRATEGY] = strategy.name }
    }

    override suspend fun setTaskProfileOverrides(overrides: Map<AiTask, String>) {
        dataStore.edit {
            it[Keys.TASK_PROFILE_OVERRIDES_JSON] = json.encodeToString(taskMapSerializer, overrides)
        }
    }

    override suspend fun setTaskProfileOverride(task: AiTask, profileId: String?) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.TASK_PROFILE_OVERRIDES_JSON]
                .decodeTaskMap(json, taskMapSerializer)
                .toMutableMap()
            if (profileId.isNullOrBlank()) current.remove(task) else current[task] = profileId
            preferences[Keys.TASK_PROFILE_OVERRIDES_JSON] = json.encodeToString(taskMapSerializer, current)
        }
    }

    override suspend fun setCustomTaskPromptSuffixes(suffixes: Map<AiTask, String>) {
        dataStore.edit {
            it[Keys.CUSTOM_TASK_PROMPT_SUFFIXES_JSON] = json.encodeToString(taskMapSerializer, suffixes)
        }
    }

    override suspend fun setCustomTaskPromptSuffix(task: AiTask, instruction: String?) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.CUSTOM_TASK_PROMPT_SUFFIXES_JSON]
                .decodeTaskMap(json, taskMapSerializer)
                .toMutableMap()
            if (instruction.isNullOrBlank()) current.remove(task) else current[task] = instruction
            preferences[Keys.CUSTOM_TASK_PROMPT_SUFFIXES_JSON] = json.encodeToString(taskMapSerializer, current)
        }
    }

    override suspend fun setDefaultOcrPageLimit(limit: Int) {
        dataStore.edit { it[Keys.DEFAULT_OCR_PAGE_LIMIT] = limit.coerceIn(1, 500) }
    }

    override suspend fun setAllowAdvancedOcrEngines(allowed: Boolean) {
        dataStore.edit { it[Keys.ALLOW_ADVANCED_OCR_ENGINES] = allowed }
    }

    override suspend fun setPreRenderQueueSize(size: Int) {
        dataStore.edit { it[Keys.PRE_RENDER_QUEUE_SIZE] = size.coerceIn(1, 100) }
    }

    override suspend fun setEncryptedDatabaseEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.ENCRYPTED_DATABASE_ENABLED] = enabled }
    }

    override suspend fun setEnableAiDiagnosticsLog(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_AI_DIAGNOSTICS_LOG] = enabled }
    }

    override suspend fun setEnableDoubleTapWallpaperAdvance(enabled: Boolean) {
        dataStore.edit { it[Keys.ENABLE_DOUBLE_TAP_ADVANCE] = enabled }
    }
}

private object Keys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
    val WALLPAPER_THEME_MODE = stringPreferencesKey("wallpaper_theme_mode")
    val ROUTING_STRATEGY = stringPreferencesKey("routing_strategy")
    val TASK_PROFILE_OVERRIDES_JSON = stringPreferencesKey("task_profile_overrides_json")
    val CUSTOM_TASK_PROMPT_SUFFIXES_JSON = stringPreferencesKey("custom_task_prompt_suffixes_json")
    val DEFAULT_OCR_PAGE_LIMIT = intPreferencesKey("default_ocr_page_limit")
    val ALLOW_ADVANCED_OCR_ENGINES = booleanPreferencesKey("allow_advanced_ocr_engines")
    val PRE_RENDER_QUEUE_SIZE = intPreferencesKey("pre_render_queue_size")
    val ENCRYPTED_DATABASE_ENABLED = booleanPreferencesKey("encrypted_database_enabled")
    val ENABLE_AI_DIAGNOSTICS_LOG = booleanPreferencesKey("enable_ai_diagnostics_log")
    val ENABLE_DOUBLE_TAP_ADVANCE = booleanPreferencesKey("enable_double_tap_wallpaper_advance")
}

private fun Preferences.toAppPreferences(
    json: Json,
    taskMapSerializer: KSerializer<Map<AiTask, String>>,
): AppPreferences = AppPreferences(
    onboardingCompleted = this[Keys.ONBOARDING_COMPLETED] ?: false,
    appThemeMode = this[Keys.APP_THEME_MODE].toEnum(ThemeMode.DARK),
    wallpaperThemeMode = this[Keys.WALLPAPER_THEME_MODE].toEnum(ThemeMode.AMOLED),
    routingStrategy = this[Keys.ROUTING_STRATEGY].toEnum(RoutingStrategy.BALANCED),
    taskProfileOverrides = this[Keys.TASK_PROFILE_OVERRIDES_JSON].decodeTaskMap(json, taskMapSerializer),
    customTaskPromptSuffixes = this[Keys.CUSTOM_TASK_PROMPT_SUFFIXES_JSON].decodeTaskMap(json, taskMapSerializer),
    defaultOcrPageLimit = (this[Keys.DEFAULT_OCR_PAGE_LIMIT] ?: 20).coerceIn(1, 500),
    allowAdvancedOcrEngines = this[Keys.ALLOW_ADVANCED_OCR_ENGINES] ?: false,
    preRenderQueueSize = (this[Keys.PRE_RENDER_QUEUE_SIZE] ?: 25).coerceIn(1, 100),
    encryptedDatabaseEnabled = this[Keys.ENCRYPTED_DATABASE_ENABLED] ?: false,
    enableAiDiagnosticsLog = this[Keys.ENABLE_AI_DIAGNOSTICS_LOG] ?: true,
    enableDoubleTapWallpaperAdvance = this[Keys.ENABLE_DOUBLE_TAP_ADVANCE] ?: false,
)

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

private fun String?.decodeTaskMap(
    json: Json,
    serializer: KSerializer<Map<AiTask, String>>,
): Map<AiTask, String> {
    if (this.isNullOrBlank()) return emptyMap()
    return runCatching { json.decodeFromString(serializer, this) }.getOrDefault(emptyMap())
}

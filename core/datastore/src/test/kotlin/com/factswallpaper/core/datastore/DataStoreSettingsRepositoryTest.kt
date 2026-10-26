package com.factswallpaper.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * The production repository is exercised against an in-memory [DataStore]. This
 * keeps the test deterministic on every host OS while still going through the
 * real DataStore updateData/edit path used on device.
 */
class DataStoreSettingsRepositoryTest {

    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = DataStoreSettingsRepository(dataStore, Json)

    private fun preferences(): AppPreferences = runBlocking {
        repository.observePreferences().first()
    }

    @Test
    fun `defaults match the documented specification values`() {
        assertThat(preferences()).isEqualTo(AppPreferences())
    }

    @Test
    fun `onboarding theme and routing preferences round trip`() {
        runBlocking<Unit> {
            repository.setOnboardingCompleted(true)
            repository.setAppThemeMode(ThemeMode.AMOLED)
            repository.setWallpaperThemeMode(ThemeMode.LIGHT)
            repository.setRoutingStrategy(RoutingStrategy.HIGHEST_QUALITY)
        }

        val stored = preferences()
        assertThat(stored.onboardingCompleted).isTrue()
        assertThat(stored.appThemeMode).isEqualTo(ThemeMode.AMOLED)
        assertThat(stored.wallpaperThemeMode).isEqualTo(ThemeMode.LIGHT)
        assertThat(stored.routingStrategy).isEqualTo(RoutingStrategy.HIGHEST_QUALITY)
    }

    @Test
    fun `unknown stored enum values fall back to defaults instead of crashing`() = runBlocking<Unit> {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                set(stringPreferencesKey("app_theme_mode"), "NEON")
                set(stringPreferencesKey("routing_strategy"), "MAGIC")
            }
        }

        val stored = preferences()
        assertThat(stored.appThemeMode).isEqualTo(ThemeMode.DARK)
        assertThat(stored.routingStrategy).isEqualTo(RoutingStrategy.BALANCED)
    }

    @Test
    fun `task profile overrides are added updated and removed independently`() {
        runBlocking {
            repository.setTaskProfileOverride(AiTask.OUTLINE, "profile-outline")
            repository.setTaskProfileOverride(AiTask.FACTS, "profile-facts")
            repository.setTaskProfileOverrides(mapOf(AiTask.OUTLINE to "profile-outline-v2"))
        }

        assertThat(preferences().taskProfileOverrides)
            .containsExactly(AiTask.OUTLINE, "profile-outline-v2")

        runBlocking { repository.setTaskProfileOverride(AiTask.OUTLINE, null) }

        assertThat(preferences().taskProfileOverrides).isEmpty()
    }

    @Test
    fun `blank task overrides remove entries rather than storing empty ids`() = runBlocking<Unit> {
        repository.setTaskProfileOverride(AiTask.FACTS, "profile")
        repository.setTaskProfileOverride(AiTask.FACTS, "   ")

        assertThat(preferences().taskProfileOverrides).isEmpty()
    }

    @Test
    fun `prompt suffixes round trip and can be cleared`() {
        runBlocking {
            repository.setCustomTaskPromptSuffix(AiTask.FACTS, "Prefer one-line facts")
            repository.setCustomTaskPromptSuffix(AiTask.OUTLINE, "Use chapter titles")
        }
        assertThat(preferences().customTaskPromptSuffixes).containsExactly(
            AiTask.FACTS,
            "Prefer one-line facts",
            AiTask.OUTLINE,
            "Use chapter titles",
        )

        runBlocking { repository.setCustomTaskPromptSuffix(AiTask.FACTS, null) }
        assertThat(preferences().customTaskPromptSuffixes)
            .containsExactly(AiTask.OUTLINE, "Use chapter titles")
    }

    @Test
    fun `numeric preferences are clamped to their supported ranges`() = runBlocking<Unit> {
        repository.setDefaultOcrPageLimit(0)
        repository.setPreRenderQueueSize(1_000)
        assertThat(preferences().defaultOcrPageLimit).isEqualTo(1)
        assertThat(preferences().preRenderQueueSize).isEqualTo(100)

        repository.setDefaultOcrPageLimit(999)
        repository.setPreRenderQueueSize(0)
        assertThat(preferences().defaultOcrPageLimit).isEqualTo(500)
        assertThat(preferences().preRenderQueueSize).isEqualTo(1)
    }

    @Test
    fun `advanced toggles round trip`() = runBlocking<Unit> {
        repository.setAllowAdvancedOcrEngines(true)
        repository.setEncryptedDatabaseEnabled(true)
        repository.setEnableAiDiagnosticsLog(false)
        repository.setEnableDoubleTapWallpaperAdvance(true)

        val stored = preferences()
        assertThat(stored.allowAdvancedOcrEngines).isTrue()
        assertThat(stored.encryptedDatabaseEnabled).isTrue()
        assertThat(stored.enableAiDiagnosticsLog).isFalse()
        assertThat(stored.enableDoubleTapWallpaperAdvance).isTrue()
    }
}

private class InMemoryPreferencesDataStore : DataStore<Preferences> {

    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

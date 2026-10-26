package com.factswallpaper.app

import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel

enum class StartDestination { ONBOARDING, LIBRARY }

/** Pure mapping used by [AppViewModel] and covered by unit tests. */
fun startDestinationFor(onboardingCompleted: Boolean): StartDestination =
    if (onboardingCompleted) StartDestination.LIBRARY else StartDestination.ONBOARDING

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val wallpaperRepository: WallpaperRepository,
) : ViewModel() {

    val themeMode = settingsRepository.observePreferences()
        .map { it.appThemeMode }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.DARK,
        )

    /**
     * One-shot start decision. [take] keeps the graph start destination stable
     * after onboarding writes `onboardingCompleted = true`.
     */
    val startDestination = settingsRepository.observePreferences()
        .map { preferences -> startDestinationFor(preferences.onboardingCompleted) }
        .take(1)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    init {
        viewModelScope.launch {
            runCatching { wallpaperRepository.ensureInitialized() }
        }
    }
}

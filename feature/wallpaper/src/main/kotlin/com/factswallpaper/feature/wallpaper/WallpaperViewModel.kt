package com.factswallpaper.feature.wallpaper

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.ui.FeatureViewModel
import com.factswallpaper.core.wallpaper.FactOfTheDayScheduler
import com.factswallpaper.core.wallpaper.StaticWallpaperExporter
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import com.factswallpaper.core.wallpaper.WallpaperRotationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface WallpaperUiState {
    data object Loading : WallpaperUiState
    data class Error(@StringRes val messageRes: Int) : WallpaperUiState
    data class Content(
        val wallpaper: WallpaperState,
        val preferences: AppPreferences,
        val pool: List<Fact>,
        val poolSummary: RotationPoolSummary,
        val templates: List<CardTemplate>,
    ) : WallpaperUiState
}

@HiltViewModel
class WallpaperViewModel @Inject constructor(
    private val wallpaperRepository: WallpaperRepository,
    rotationPoolRepository: RotationPoolRepository,
    templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val rotationController: WallpaperRotationController,
    private val staticWallpaperExporter: StaticWallpaperExporter,
    private val scheduler: WallpaperRotationScheduler,
    private val factOfTheDayScheduler: FactOfTheDayScheduler,
) : FeatureViewModel() {

    init {
        viewModelScope.launch { wallpaperRepository.ensureInitialized() }
    }

    private val retrySignal = MutableStateFlow(0)

    val uiState = retrySignal.flatMapLatest {
        combine(
            wallpaperRepository.observeState(),
            settingsRepository.observePreferences(),
            rotationPoolRepository.observePool(),
            rotationPoolRepository.observePoolSummary(),
            templateRepository.observeTemplates(),
        ) { state, prefs, pool, summary, templates ->
            WallpaperUiState.Content(
                wallpaper = state,
                preferences = prefs,
                pool = pool,
                poolSummary = summary,
                templates = templates,
            )
        }
            .map<WallpaperUiState.Content, WallpaperUiState> { it }
            .catch { emit(WallpaperUiState.Error(R.string.wallpaper_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WallpaperUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun setScheduleMode(mode: WallpaperScheduleMode) {
        viewModelScope.launch {
            val current = wallpaperRepository.getState()
            wallpaperRepository.updateState { it.copy(scheduleMode = mode) }
            scheduler.updateSchedule(mode, current.intervalMinutes)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        val clamped = minutes.coerceIn(15, 1_440)
        viewModelScope.launch {
            val current = wallpaperRepository.getState()
            wallpaperRepository.updateState { it.copy(intervalMinutes = clamped) }
            scheduler.updateSchedule(current.scheduleMode, clamped)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setWallpaperTarget(target: WallpaperTarget) {
        update { it.copy(target = target) }
    }

    fun setRotationOrder(order: RotationOrder) {
        update { it.copy(rotationOrder = order) }
    }

    fun setOnlyFavorites(enabled: Boolean) {
        update { it.copy(onlyFavoritesInPool = enabled) }
    }

    fun setSourceReferenceMode(mode: SourceReferenceMode) {
        update { it.copy(sourceReferenceMode = mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(wallpaperThemeMode = mode) }
            settingsRepository.setWallpaperThemeMode(mode)
        }
    }

    fun selectTemplate(templateId: String) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(activeTemplateId = templateId) }
            showMessage(R.string.wallpaper_template_saved)
        }
    }

    fun setRotationSource(source: RotationSource) {
        viewModelScope.launch {
            settingsRepository.setRotationSource(source)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setRotationPaused(paused: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRotationPaused(paused)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setQuietHoursEnabled(enabled)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {
        viewModelScope.launch {
            settingsRepository.setQuietHoursWindow(startMinute, endMinute)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setBatterySaverSlowdown(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBatterySaverSlowdownEnabled(enabled)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setDailyFactNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDailyFactNotificationEnabled(enabled)
            val prefs = settingsRepository.observePreferences().first()
            factOfTheDayScheduler.updateSchedule(enabled, prefs.dailyFactNotificationTimeMinutes)
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun setDailyFactNotificationTime(timeMinutes: Int) {
        viewModelScope.launch {
            settingsRepository.setDailyFactNotificationTime(timeMinutes)
            val prefs = settingsRepository.observePreferences().first()
            if (prefs.dailyFactNotificationEnabled) {
                factOfTheDayScheduler.updateSchedule(true, timeMinutes)
            }
            showMessage(R.string.wallpaper_saved)
        }
    }

    fun showNextCard() {
        viewModelScope.launch {
            val newState = rotationController.advance()
            if (newState.currentFactId != null) {
                showMessage(R.string.wallpaper_next_ready)
            } else {
                showMessage(R.string.wallpaper_pool_empty)
            }
        }
    }

    fun applyStaticWallpaper(target: WallpaperTarget) {
        viewModelScope.launch {
            val pngPath = rotationController.getCurrentOrFallbackPngPath()
            if (pngPath != null) {
                val result = staticWallpaperExporter.exportPngFile(File(pngPath), target)
                when (result) {
                    is AppResult.Success -> showMessage(R.string.wallpaper_static_applied)
                    is AppResult.Failure -> showMessage(R.string.wallpaper_static_failed)
                }
            } else {
                showMessage(R.string.wallpaper_static_failed)
            }
        }
    }

    private fun update(transform: (WallpaperState) -> WallpaperState) {
        viewModelScope.launch {
            wallpaperRepository.updateState(transform)
            showMessage(R.string.wallpaper_saved)
        }
    }
}

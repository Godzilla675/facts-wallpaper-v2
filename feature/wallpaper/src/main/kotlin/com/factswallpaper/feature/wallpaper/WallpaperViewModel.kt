package com.factswallpaper.feature.wallpaper

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.ui.FeatureViewModel
import com.factswallpaper.core.wallpaper.StaticWallpaperExporter
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import com.factswallpaper.core.wallpaper.WallpaperRotationScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface WallpaperUiState {
    data object Loading : WallpaperUiState
    data class Error(@StringRes val messageRes: Int) : WallpaperUiState
    data class Content(
        val wallpaper: WallpaperState,
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
) : FeatureViewModel() {

    init {
        viewModelScope.launch { wallpaperRepository.ensureInitialized() }
    }

    val uiState = combine(
        wallpaperRepository.observeState(),
        rotationPoolRepository.observePool(),
        rotationPoolRepository.observePoolSummary(),
        templateRepository.observeTemplates(),
    ) { state, pool, summary, templates ->
        WallpaperUiState.Content(
            wallpaper = state,
            pool = pool,
            poolSummary = summary,
            templates = templates,
        )
    }
        .map<WallpaperUiState.Content, WallpaperUiState> { it }
        .catch { emit(WallpaperUiState.Error(R.string.wallpaper_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WallpaperUiState.Loading,
        )

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

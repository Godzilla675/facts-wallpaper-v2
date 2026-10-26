package com.factswallpaper.feature.wallpaper

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
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
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
        update { it.copy(scheduleMode = mode) }
    }

    fun setIntervalMinutes(minutes: Int) {
        update { it.copy(intervalMinutes = minutes.coerceIn(15, 1_440)) }
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
        val content = uiState.value as? WallpaperUiState.Content ?: return
        if (content.pool.isEmpty()) {
            showMessage(R.string.wallpaper_pool_empty)
            return
        }
        val currentIndex = content.pool.indexOfFirst {
            it.id == content.wallpaper.currentFactId
        }
        val nextIndex = (currentIndex + 1).mod(content.pool.size)
        val fact = content.pool[nextIndex]
        viewModelScope.launch {
            wallpaperRepository.setCurrentFact(fact.id, nextIndex)
            showMessage(R.string.wallpaper_next_ready)
        }
    }

    fun applyStaticWallpaper() {
        showMessage(R.string.wallpaper_static_not_yet)
    }

    private fun update(transform: (WallpaperState) -> WallpaperState) {
        viewModelScope.launch {
            wallpaperRepository.updateState(transform)
            showMessage(R.string.wallpaper_saved)
        }
    }
}

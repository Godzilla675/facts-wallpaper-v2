package com.factswallpaper.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CardPreviewUiState {
    data object Loading : CardPreviewUiState
    data class Error(@StringRes val messageRes: Int) : CardPreviewUiState
    data class Content(
        val pool: List<Fact>,
        val templates: List<CardTemplate>,
        val selectedFact: Fact?,
        val selectedTemplateId: String,
        val wallpaper: WallpaperState,
    ) : CardPreviewUiState {
        val selectedIndex: Int
            get() = pool.indexOfFirst { it.id == selectedFact?.id }.coerceAtLeast(0)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    factRepository: FactRepository,
    templateRepository: TemplateRepository,
    private val wallpaperRepository: WallpaperRepository,
    rotationPoolRepository: RotationPoolRepository,
) : FeatureViewModel() {

    private val requestedFactId = MutableStateFlow(savedStateHandle.get<String>("factId"))
    private val previewTemplateId = MutableStateFlow(savedStateHandle.get<String>("templateId"))

    private val requestedFact = requestedFactId.flatMapLatest { factId ->
        if (factId == null) flowOf(null) else factRepository.observeFact(factId)
    }

    val uiState = combine(
        rotationPoolRepository.observePool(),
        templateRepository.observeTemplates(),
        wallpaperRepository.observeState(),
        previewTemplateId,
        requestedFact,
    ) { pool, templates, wallpaper, previewTemplate, explicitFact ->
        val selected = explicitFact
            ?: pool.firstOrNull()
        CardPreviewUiState.Content(
            pool = pool,
            templates = templates,
            selectedFact = selected,
            selectedTemplateId = previewTemplate ?: wallpaper.activeTemplateId,
            wallpaper = wallpaper,
        )
    }
        .map<CardPreviewUiState.Content, CardPreviewUiState> { it }
        .catch { emit(CardPreviewUiState.Error(R.string.preview_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CardPreviewUiState.Loading,
        )

    fun nextFact() {
        val content = uiState.value as? CardPreviewUiState.Content ?: return
        if (content.pool.isEmpty()) return
        val next = content.pool[(content.selectedIndex + 1) % content.pool.size]
        requestedFactId.value = next.id
    }

    fun previousFact() {
        val content = uiState.value as? CardPreviewUiState.Content ?: return
        if (content.pool.isEmpty()) return
        val index = (content.selectedIndex - 1 + content.pool.size) % content.pool.size
        requestedFactId.value = content.pool[index].id
    }

    fun selectTemplate(templateId: String) {
        previewTemplateId.value = null
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(activeTemplateId = templateId) }
            showMessage(R.string.preview_template_selected)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(wallpaperThemeMode = mode) }
        }
    }

    fun setSourceReferenceMode(mode: SourceReferenceMode) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(sourceReferenceMode = mode) }
        }
    }

    fun applyWallpaper() {
        showMessage(R.string.preview_apply_not_yet)
    }
}

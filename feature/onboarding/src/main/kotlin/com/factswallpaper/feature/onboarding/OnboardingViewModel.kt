package com.factswallpaper.feature.onboarding

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface OnboardingUiState {
    data object Loading : OnboardingUiState
    data class Error(@StringRes val messageRes: Int) : OnboardingUiState
    data class Content(
        val step: Int,
        val selectedPreset: ProviderType,
        val apiKey: String,
        val apiKeyVisible: Boolean,
        val themeMode: ThemeMode,
        val templates: List<CardTemplate>,
        val selectedTemplateId: String?,
        val isSaving: Boolean,
        val finished: Boolean,
    ) : OnboardingUiState
}

private data class Draft(
    val step: Int = 1,
    val selectedPreset: ProviderType = ProviderType.GEMINI,
    val apiKey: String = "",
    val apiKeyVisible: Boolean = false,
    val selectedTemplateId: String? = null,
    val isSaving: Boolean = false,
    val finished: Boolean = false,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val templateRepository: TemplateRepository,
    private val wallpaperRepository: WallpaperRepository,
    providerProfileRepository: ProviderProfileRepository,
) : FeatureViewModel() {

    private val draft = MutableStateFlow(Draft())

    val uiState = combine(
        draft,
        settingsRepository.observePreferences(),
        templateRepository.observeTemplates(),
        providerProfileRepository.observeProfiles(),
    ) { current, preferences, templates, _ ->
        OnboardingUiState.Content(
            step = current.step,
            selectedPreset = current.selectedPreset,
            apiKey = current.apiKey,
            apiKeyVisible = current.apiKeyVisible,
            themeMode = preferences.appThemeMode,
            templates = templates,
            selectedTemplateId = current.selectedTemplateId
                ?: templates.firstOrNull { it.isDefault }?.id,
            isSaving = current.isSaving,
            finished = current.finished,
        )
    }
        .map<OnboardingUiState.Content, OnboardingUiState> { it }
        .catch { emit(OnboardingUiState.Error(R.string.onboarding_load_error)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OnboardingUiState.Loading)

    fun selectPreset(type: ProviderType) {
        draft.update { it.copy(selectedPreset = type) }
    }

    fun updateApiKey(value: String) {
        draft.update { it.copy(apiKey = value) }
    }

    fun toggleApiKeyVisibility() {
        draft.update { it.copy(apiKeyVisible = !it.apiKeyVisible) }
    }

    fun saveApiKey() {
        showMessage(R.string.onboarding_key_not_yet)
    }

    fun testConnection() {
        showMessage(R.string.onboarding_test_not_yet)
    }

    fun nextStep() {
        draft.update { it.copy(step = (it.step + 1).coerceAtMost(LAST_STEP)) }
    }

    fun previousStep() {
        draft.update { it.copy(step = (it.step - 1).coerceAtLeast(1)) }
    }

    fun selectTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setAppThemeMode(mode) }
    }

    fun selectTemplate(templateId: String) {
        draft.update { it.copy(selectedTemplateId = templateId) }
        viewModelScope.launch {
            templateRepository.setDefaultTemplate(templateId)
            wallpaperRepository.updateState { it.copy(activeTemplateId = templateId) }
        }
    }

    fun finish() {
        if (draft.value.isSaving || draft.value.finished) return
        draft.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            runCatching {
                settingsRepository.setOnboardingCompleted(true)
                val selected = draft.value.selectedTemplateId
                if (!selected.isNullOrBlank()) {
                    templateRepository.setDefaultTemplate(selected)
                    wallpaperRepository.updateState { it.copy(activeTemplateId = selected) }
                }
                wallpaperRepository.ensureInitialized()
            }.onSuccess {
                draft.update { it.copy(isSaving = false, finished = true) }
                showMessage(R.string.onboarding_completed_message)
            }.onFailure {
                draft.update { it.copy(isSaving = false) }
                showMessage(R.string.onboarding_load_error)
            }
        }
    }

    companion object {
        const val LAST_STEP = 5
    }
}

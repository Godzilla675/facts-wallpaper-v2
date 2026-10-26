package com.factswallpaper.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.UsageSummary
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProviderSettingsUiState {
    data object Loading : ProviderSettingsUiState
    data class Error(@StringRes val messageRes: Int) : ProviderSettingsUiState
    data class Content(
        val profiles: List<ProviderProfile>,
        val preferences: AppPreferences,
        val usage: UsageSummary,
    ) : ProviderSettingsUiState
}

@HiltViewModel
class ProviderSettingsViewModel @Inject constructor(
    private val profileRepository: ProviderProfileRepository,
    private val settingsRepository: SettingsRepository,
    diagnosticsRepository: DiagnosticsRepository,
) : FeatureViewModel() {

    val uiState = combine(
        profileRepository.observeProfiles(),
        settingsRepository.observePreferences(),
        diagnosticsRepository.observeUsageSummary(),
    ) { profiles, preferences, usage ->
        ProviderSettingsUiState.Content(
            profiles = profiles,
            preferences = preferences,
            usage = usage,
        )
    }
        .map<ProviderSettingsUiState.Content, ProviderSettingsUiState> { it }
        .catch { emit(ProviderSettingsUiState.Error(R.string.provider_settings_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProviderSettingsUiState.Loading,
        )

    fun setRoutingStrategy(strategy: RoutingStrategy) {
        viewModelScope.launch {
            settingsRepository.setRoutingStrategy(strategy)
            showMessage(R.string.provider_routing_saved)
        }
    }

    fun setTaskOverride(task: AiTask, profileId: String?) {
        viewModelScope.launch {
            settingsRepository.setTaskProfileOverride(task, profileId)
            showMessage(R.string.provider_override_saved)
        }
    }

    fun saveProfile(
        existing: ProviderProfile?,
        name: String,
        providerType: ProviderType,
        baseUrl: String,
        modelName: String,
        maxContextTokens: Int,
    ) {
        if (name.isBlank() || modelName.isBlank() || baseUrl.isBlank()) {
            showMessage(R.string.provider_fields_required)
            return
        }
        val content = uiState.value as? ProviderSettingsUiState.Content
        viewModelScope.launch {
            val profile = existing?.copy(
                name = name.trim(),
                providerType = providerType,
                baseUrl = baseUrl.trim(),
                modelName = modelName.trim(),
                maxContextTokens = maxContextTokens.coerceIn(1_000, 2_000_000),
            ) ?: ProviderProfile(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                providerType = providerType,
                baseUrl = baseUrl.trim(),
                modelName = modelName.trim(),
                maxContextTokens = maxContextTokens.coerceIn(1_000, 2_000_000),
                supportsPdfInput = providerType == ProviderType.GEMINI ||
                    providerType == ProviderType.OPENAI ||
                    providerType == ProviderType.ANTHROPIC,
                supportsImageInput = true,
            )
            profileRepository.saveProfile(profile)
            if (existing == null && content?.profiles.isNullOrEmpty()) {
                profileRepository.setDefaultProfile(profile.id)
            }
            showMessage(R.string.provider_saved)
        }
    }

    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.setDefaultProfile(profileId)
            showMessage(R.string.provider_default_saved)
        }
    }

    fun deleteProfile(profile: ProviderProfile) {
        val content = uiState.value as? ProviderSettingsUiState.Content
        viewModelScope.launch {
            when (profileRepository.deleteProfile(profile.id)) {
                is AppResult.Success -> {
                    if (profile.isDefault) {
                        content?.profiles
                            ?.firstOrNull { it.id != profile.id }
                            ?.let { profileRepository.setDefaultProfile(it.id) }
                    }
                    showMessage(R.string.provider_deleted)
                }
                is AppResult.Failure -> showMessage(R.string.provider_delete_failed)
            }
        }
    }

    fun apiKeysNotYet() {
        showMessage(R.string.provider_keys_not_yet)
    }

    fun testConnection() {
        showMessage(R.string.provider_test_not_yet)
    }
}

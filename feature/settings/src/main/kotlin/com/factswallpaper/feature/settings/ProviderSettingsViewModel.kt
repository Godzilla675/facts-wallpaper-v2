package com.factswallpaper.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.ai.AiModelInfo
import com.factswallpaper.core.ai.ProviderDiagnosticsService
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
import com.factswallpaper.core.security.ApiKeyStore
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
        val configuredKeyProfileIds: Set<String> = emptySet(),
        val testingProfileId: String? = null,
    ) : ProviderSettingsUiState
}

@HiltViewModel
class ProviderSettingsViewModel @Inject constructor(
    private val profileRepository: ProviderProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
    private val diagnosticsService: ProviderDiagnosticsService,
    diagnosticsRepository: DiagnosticsRepository,
) : FeatureViewModel() {

    private val configuredKeyIds = MutableStateFlow<Set<String>>(emptySet())
    private val testingProfileId = MutableStateFlow<String?>(null)

    val uiState = combine(
        profileRepository.observeProfiles(),
        settingsRepository.observePreferences(),
        diagnosticsRepository.observeUsageSummary(),
        configuredKeyIds,
        testingProfileId,
    ) { profiles, preferences, usage, keyIds, testingId ->
        ProviderSettingsUiState.Content(
            profiles = profiles,
            preferences = preferences,
            usage = usage,
            configuredKeyProfileIds = keyIds,
            testingProfileId = testingId,
        )
    }
        .map<ProviderSettingsUiState.Content, ProviderSettingsUiState> { it }
        .catch { emit(ProviderSettingsUiState.Error(R.string.provider_settings_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ProviderSettingsUiState.Loading,
        )

    init {
        viewModelScope.launch {
            profileRepository.observeProfiles().collect { profiles ->
                val configured = apiKeyStore.configuredProfileIds(profiles.map { it.id })
                configuredKeyIds.value = configured
            }
        }
    }

    private suspend fun refreshKeys(profiles: List<ProviderProfile>) {
        configuredKeyIds.value = apiKeyStore.configuredProfileIds(profiles.map { it.id })
    }

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
        apiKey: String? = null,
    ) {
        if (name.isBlank() || modelName.isBlank() || baseUrl.isBlank()) {
            showMessage(R.string.provider_fields_required)
            return
        }
        val content = uiState.value as? ProviderSettingsUiState.Content
        viewModelScope.launch {
            val id = existing?.id ?: UUID.randomUUID().toString()
            val profile = existing?.copy(
                name = name.trim(),
                providerType = providerType,
                baseUrl = baseUrl.trim(),
                modelName = modelName.trim(),
                maxContextTokens = maxContextTokens.coerceIn(1_000, 2_000_000),
            ) ?: ProviderProfile(
                id = id,
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
            if (!apiKey.isNullOrBlank()) {
                apiKeyStore.setApiKey(id, apiKey.trim())
            }
            if (existing == null && content?.profiles.isNullOrEmpty()) {
                profileRepository.setDefaultProfile(profile.id)
            }
            content?.profiles?.let { refreshKeys(it + profile) }
            showMessage(R.string.provider_saved)
        }
    }

    fun saveApiKey(profileId: String, apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isBlank()) {
                apiKeyStore.deleteApiKey(profileId)
                showMessage(R.string.provider_key_cleared)
            } else {
                apiKeyStore.setApiKey(profileId, apiKey.trim())
                showMessage(R.string.provider_key_saved)
            }
            val content = uiState.value as? ProviderSettingsUiState.Content
            content?.profiles?.let { refreshKeys(it) }
        }
    }

    fun deleteApiKey(profileId: String) {
        viewModelScope.launch {
            apiKeyStore.deleteApiKey(profileId)
            val content = uiState.value as? ProviderSettingsUiState.Content
            content?.profiles?.let { refreshKeys(it) }
            showMessage(R.string.provider_key_cleared)
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
                    apiKeyStore.deleteApiKey(profile.id)
                    if (profile.isDefault) {
                        content?.profiles
                            ?.firstOrNull { it.id != profile.id }
                            ?.let { profileRepository.setDefaultProfile(it.id) }
                    }
                    content?.profiles?.let { refreshKeys(it.filter { p -> p.id != profile.id }) }
                    showMessage(R.string.provider_deleted)
                }
                is AppResult.Failure -> showMessage(R.string.provider_delete_failed)
            }
        }
    }

    fun testConnection(profile: ProviderProfile) {
        viewModelScope.launch {
            testingProfileId.value = profile.id
            val result = diagnosticsService.testConnection(profile)
            testingProfileId.value = null
            if (result.success) {
                showMessage(R.string.provider_test_success, result.latencyMs)
            } else {
                showMessage(R.string.provider_test_failed, result.message)
            }
        }
    }

    fun fetchModels(profile: ProviderProfile, onResult: (List<AiModelInfo>, String?) -> Unit) {
        viewModelScope.launch {
            val result = diagnosticsService.fetchModels(profile)
            onResult(result.models, result.error)
        }
    }
}

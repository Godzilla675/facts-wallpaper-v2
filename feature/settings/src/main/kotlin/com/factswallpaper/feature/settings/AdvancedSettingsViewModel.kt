package com.factswallpaper.feature.settings

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.StorageRepository
import com.factswallpaper.core.data.repository.StorageStats
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AdvancedSettingsUiState {
    data object Loading : AdvancedSettingsUiState
    data class Error(@StringRes val messageRes: Int) : AdvancedSettingsUiState
    data class Content(
        val preferences: AppPreferences,
        val logs: List<AiRequestLogEntry>,
        val storage: StorageStats,
    ) : AdvancedSettingsUiState
}

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val storageRepository: StorageRepository,
) : FeatureViewModel() {

    private val storage = MutableStateFlow(StorageStats())

    init {
        refreshStorage()
    }

    val uiState = combine(
        settingsRepository.observePreferences(),
        diagnosticsRepository.observeRecentLogs(limit = 20),
        storage,
    ) { preferences, logs, stats ->
        AdvancedSettingsUiState.Content(
            preferences = preferences,
            logs = logs,
            storage = stats,
        )
    }
        .map<AdvancedSettingsUiState.Content, AdvancedSettingsUiState> { it }
        .catch { emit(AdvancedSettingsUiState.Error(R.string.advanced_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AdvancedSettingsUiState.Loading,
        )

    fun setDefaultOcrPageLimit(limit: Int) {
        viewModelScope.launch {
            settingsRepository.setDefaultOcrPageLimit(limit.coerceIn(1, 500))
            showMessage(R.string.advanced_saved)
        }
    }

    fun setAllowAdvancedOcrEngines(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAllowAdvancedOcrEngines(enabled)
            showMessage(R.string.advanced_saved)
        }
    }

    fun setPreRenderQueueSize(size: Int) {
        viewModelScope.launch {
            settingsRepository.setPreRenderQueueSize(size.coerceIn(1, 100))
            showMessage(R.string.advanced_saved)
        }
    }

    fun setEnableAiDiagnosticsLog(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableAiDiagnosticsLog(enabled)
            showMessage(R.string.advanced_saved)
        }
    }

    fun setEnableDoubleTapAdvance(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableDoubleTapWallpaperAdvance(enabled)
            showMessage(R.string.advanced_saved)
        }
    }

    fun setPromptSuffix(task: AiTask, suffix: String) {
        viewModelScope.launch {
            settingsRepository.setCustomTaskPromptSuffix(task, suffix.trim().ifBlank { null })
            showMessage(R.string.advanced_suffix_saved)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            diagnosticsRepository.clearLogs()
            showMessage(R.string.advanced_logs_cleared)
        }
    }

    fun refreshStorage() {
        viewModelScope.launch { storage.value = storageRepository.stats() }
    }

    fun clearRenderCache() {
        viewModelScope.launch {
            when (storageRepository.clearRenderCache()) {
                is AppResult.Success -> {
                    refreshStorage()
                    showMessage(R.string.advanced_cache_cleared)
                }
                is AppResult.Failure -> showMessage(R.string.advanced_cache_failed)
            }
        }
    }

    fun clearDiagramCache() {
        viewModelScope.launch {
            when (storageRepository.clearDiagramCache()) {
                is AppResult.Success -> {
                    refreshStorage()
                    showMessage(R.string.advanced_cache_cleared)
                }
                is AppResult.Failure -> showMessage(R.string.advanced_cache_failed)
            }
        }
    }

    fun backupNotYet() {
        showMessage(R.string.advanced_backup_not_yet)
    }

    fun restoreNotYet() {
        showMessage(R.string.advanced_restore_not_yet)
    }

    fun sqlCipherNotYet() {
        showMessage(R.string.advanced_sqlcipher_not_yet)
    }
}

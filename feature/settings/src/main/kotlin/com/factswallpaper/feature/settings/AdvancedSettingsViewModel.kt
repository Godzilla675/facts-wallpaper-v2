package com.factswallpaper.feature.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.backup.BackupManager
import com.factswallpaper.core.backup.BackupOptions
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.StorageRepository
import com.factswallpaper.core.data.repository.StorageStats
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.UsageSummary
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
        val usage: UsageSummary,
    ) : AdvancedSettingsUiState
}

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val storageRepository: StorageRepository,
    private val backupManager: BackupManager,
) : FeatureViewModel() {

    private val storage = MutableStateFlow(StorageStats())

    init {
        refreshStorage()
    }

    val uiState = combine(
        settingsRepository.observePreferences(),
        diagnosticsRepository.observeRecentLogs(limit = 50),
        storage,
        diagnosticsRepository.observeUsageSummary(),
    ) { preferences, logs, stats, usage ->
        AdvancedSettingsUiState.Content(
            preferences = preferences,
            logs = logs,
            storage = stats,
            usage = usage,
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

    fun compactDatabase() {
        viewModelScope.launch {
            when (storageRepository.compactDatabase()) {
                is AppResult.Success -> {
                    refreshStorage()
                    showMessage(R.string.advanced_database_compacted)
                }
                is AppResult.Failure -> showMessage(R.string.advanced_database_compact_failed)
            }
        }
    }

    fun createBackup(
        passphrase: String,
        options: BackupOptions,
        onSuccess: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val backupsDir = File(context.filesDir, "backups").apply { mkdirs() }
            val timestamp = System.currentTimeMillis()
            val backupFile = File(backupsDir, "facts_wallpaper_backup_$timestamp.fwbk")
            when (val result = backupManager.createBackup(backupFile, passphrase, options)) {
                is AppResult.Success -> {
                    refreshStorage()
                    onSuccess(backupFile.name)
                }
                is AppResult.Failure -> {
                    showMessage(R.string.advanced_backup_failed)
                }
            }
        }
    }

    fun restoreBackup(
        passphrase: String,
        onSuccess: (Int, Int) -> Unit,
    ) {
        viewModelScope.launch {
            val backupsDir = File(context.filesDir, "backups")
            val latestBackup = backupsDir.listFiles()
                ?.filter { it.extension == "fwbk" }
                ?.maxByOrNull { it.lastModified() }

            if (latestBackup == null) {
                showMessage(R.string.advanced_restore_failed)
                return@launch
            }

            when (val result = backupManager.restoreBackup(latestBackup, passphrase)) {
                is AppResult.Success -> {
                    refreshStorage()
                    onSuccess(result.value.documentCount, result.value.factCount)
                }
                is AppResult.Failure -> {
                    showMessage(R.string.advanced_restore_failed)
                }
            }
        }
    }

    fun sqlCipherInfo() {
        showMessage(R.string.advanced_sqlcipher_info)
    }
}

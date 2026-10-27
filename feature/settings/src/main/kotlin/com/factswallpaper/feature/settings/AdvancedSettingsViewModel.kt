package com.factswallpaper.feature.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.backup.AutomaticBackupFrequency
import com.factswallpaper.core.backup.AutomaticBackupScheduler
import com.factswallpaper.core.backup.BackupDestinationStatus
import com.factswallpaper.core.backup.BackupDestinationStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AdvancedSettingsUiState {
    data object Loading : AdvancedSettingsUiState
    data class Error(@StringRes val messageRes: Int) : AdvancedSettingsUiState
    data class Content(
        val preferences: AppPreferences,
        val logs: List<AiRequestLogEntry>,
        val storage: StorageStats,
        val usage: UsageSummary,
        val backupDestination: BackupDestinationStatus,
    ) : AdvancedSettingsUiState
}

@HiltViewModel
class AdvancedSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val diagnosticsRepository: DiagnosticsRepository,
    private val storageRepository: StorageRepository,
    private val backupManager: BackupManager,
    private val backupDestinationStore: BackupDestinationStore,
    private val automaticBackupScheduler: AutomaticBackupScheduler,
) : FeatureViewModel() {

    private val storage = MutableStateFlow(StorageStats())
    private val retrySignal = MutableStateFlow(0)

    init {
        refreshStorage()
    }

    val uiState = retrySignal.flatMapLatest {
        combine(
            settingsRepository.observePreferences(),
            diagnosticsRepository.observeRecentLogs(limit = 50),
            storage,
            diagnosticsRepository.observeUsageSummary(),
            backupDestinationStore.observeStatus(),
        ) { preferences, logs, stats, usage, backupDestination ->
            AdvancedSettingsUiState.Content(
                preferences = preferences,
                logs = logs,
                storage = stats,
                usage = usage,
                backupDestination = backupDestination,
            )
        }
            .map<AdvancedSettingsUiState.Content, AdvancedSettingsUiState> { it }
            .catch { emit(AdvancedSettingsUiState.Error(R.string.advanced_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AdvancedSettingsUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
        refreshStorage()
    }

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
                    showMessage(R.string.advanced_backup_success, backupFile.name)
                    onSuccess(backupFile.name)
                }
                is AppResult.Failure -> {
                    showMessage(R.string.advanced_backup_failed)
                }
            }
        }
    }

    fun configureBackupDestination(
        uri: Uri,
        passphrase: String,
        options: BackupOptions,
        automaticEnabled: Boolean,
        frequency: AutomaticBackupFrequency,
        onComplete: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                backupDestinationStore.configure(uri.toString(), passphrase, options)
                backupDestinationStore.setAutomatic(automaticEnabled, frequency)
                if (automaticEnabled) {
                    automaticBackupScheduler.schedule(frequency)
                } else {
                    automaticBackupScheduler.cancel()
                }
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (exception: Exception) {
                backupDestinationStore.recordResult(
                    false,
                    exception.message ?: "Backup destination unavailable.",
                )
                showMessage(R.string.advanced_backup_permission_failed)
                return@launch
            }

            when (
                val result = backupManager.createBackupToUri(
                    destination = uri,
                    passphrase = passphrase,
                    options = options,
                )
            ) {
                is AppResult.Success -> {
                    backupDestinationStore.recordResult(succeeded = true)
                    refreshStorage()
                    showMessage(R.string.advanced_backup_to_location_success)
                    onComplete()
                }
                is AppResult.Failure -> {
                    backupDestinationStore.recordResult(false, backupErrorDetail(result))
                    showMessage(R.string.advanced_backup_failed)
                }
            }
        }
    }

    fun setAutomaticBackup(
        enabled: Boolean,
        frequency: AutomaticBackupFrequency,
    ) {
        viewModelScope.launch {
            try {
                if (enabled && backupDestinationStore.loadConfiguration() == null) {
                    showMessage(R.string.advanced_backup_choose_location_first)
                    return@launch
                }
                backupDestinationStore.setAutomatic(enabled, frequency)
                if (enabled) automaticBackupScheduler.schedule(frequency) else automaticBackupScheduler.cancel()
                showMessage(R.string.advanced_saved)
            } catch (exception: kotlinx.coroutines.CancellationException) {
                throw exception
            } catch (exception: Exception) {
                try {
                    backupDestinationStore.setAutomatic(false, frequency)
                    automaticBackupScheduler.cancel()
                } catch (cleanupException: Exception) {
                    exception.addSuppressed(cleanupException)
                }
                showMessage(R.string.advanced_backup_schedule_failed)
            }
        }
    }

    fun backupToSavedLocation() {
        viewModelScope.launch {
            val configuration = try {
                backupDestinationStore.loadConfiguration()
            } catch (exception: Exception) {
                backupDestinationStore.recordResult(
                    false,
                    exception.message ?: "Could not unlock backup settings.",
                )
                showMessage(R.string.advanced_backup_failed)
                return@launch
            }
            if (configuration == null) {
                showMessage(R.string.advanced_backup_choose_location_first)
                return@launch
            }
            when (
                val result = backupManager.createBackupToUri(
                    destination = Uri.parse(configuration.uri),
                    passphrase = configuration.passphrase,
                    options = configuration.options,
                )
            ) {
                is AppResult.Success -> {
                    backupDestinationStore.recordResult(succeeded = true)
                    refreshStorage()
                    showMessage(R.string.advanced_backup_to_location_success)
                }
                is AppResult.Failure -> {
                    backupDestinationStore.recordResult(false, backupErrorDetail(result))
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

    fun restoreBackupFromUri(
        uri: Uri,
        passphrase: String,
        onSuccess: (Int, Int) -> Unit,
    ) {
        viewModelScope.launch {
            when (val result = backupManager.restoreBackupFromUri(uri, passphrase)) {
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

private fun backupErrorDetail(result: AppResult.Failure): String =
    result.error.cause?.message ?: result.error.message ?: "Unknown backup error."

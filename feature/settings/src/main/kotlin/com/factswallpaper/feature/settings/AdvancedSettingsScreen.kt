package com.factswallpaper.feature.settings

import com.factswallpaper.core.model.ThemeMode
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.backup.BackupOptions
import com.factswallpaper.core.backup.AutomaticBackupFrequency
import com.factswallpaper.core.backup.BackupDestinationStatus
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SettingSwitchRow
import com.factswallpaper.core.ui.format.MetricsFormatter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class BackupDestinationRequest(
    val passphrase: String,
    val options: BackupOptions,
    val automaticEnabled: Boolean,
    val frequency: AutomaticBackupFrequency,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenWhatsHappening: () -> Unit = {},
    viewModel: AdvancedSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingOcrLimit by remember { mutableStateOf(false) }
    var editingQueueSize by remember { mutableStateOf(false) }
    var editingSuffixTask by remember { mutableStateOf<AiTask?>(null) }
    var inspectedLog by remember { mutableStateOf<AiRequestLogEntry?>(null) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showRestoreDialog by remember { mutableStateOf(false) }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingCacheClear by remember { mutableStateOf<CacheClearTarget?>(null) }
    var pendingBackupDestination by remember { mutableStateOf<BackupDestinationRequest?>(null) }

    val backupDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val request = pendingBackupDestination
        pendingBackupDestination = null
        if (uri != null && request != null) {
            viewModel.configureBackupDestination(
                uri = uri,
                passphrase = request.passphrase,
                options = request.options,
                automaticEnabled = request.automaticEnabled,
                frequency = request.frequency,
            ) {
                showBackupDialog = false
            }
        }
    }

    val restoreDocumentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            showRestoreDialog = true
        }
    }

    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.advanced_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.advanced_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            AdvancedSettingsUiState.Loading -> AdvancedLoading(Modifier.padding(padding))
            is AdvancedSettingsUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is AdvancedSettingsUiState.Content -> AdvancedContent(
                state = value,
                onEditOcrLimit = { editingOcrLimit = true },
                onEditQueueSize = { editingQueueSize = true },
                onAdvancedOcrChange = viewModel::setAllowAdvancedOcrEngines,
                onEnableLogsChange = viewModel::setEnableAiDiagnosticsLog,
                onDoubleTapChange = viewModel::setEnableDoubleTapAdvance,
                onEditSuffix = { editingSuffixTask = it },
                onInspectLog = { inspectedLog = it },
                onClearLogs = viewModel::clearLogs,
                onRefreshStorage = viewModel::refreshStorage,
                onClearRenderCache = { pendingCacheClear = CacheClearTarget.CARDS },
                onClearDiagramCache = { pendingCacheClear = CacheClearTarget.DIAGRAMS },
                onCompactDatabase = viewModel::compactDatabase,
                onOpenBackupDialog = { showBackupDialog = true },
                onOpenRestoreDialog = {
                    selectedRestoreUri = null
                    showRestoreDialog = true
                },
                onRestoreFromFile = {
                    restoreDocumentPicker.launch(arrayOf("*/*"))
                },
                onBackupToSavedLocation = viewModel::backupToSavedLocation,
                onAutomaticBackupChange = {
                    viewModel.setAutomaticBackup(it, value.backupDestination.frequency)
                },
                onBackupFrequencyChange = {
                    viewModel.setAutomaticBackup(value.backupDestination.automaticEnabled, it)
                },
                onSqlCipher = viewModel::sqlCipherInfo,
                onOpenWhatsHappening = onOpenWhatsHappening,
                onThemeModeChange = viewModel::setAppThemeMode,
                onDynamicColorChange = viewModel::setDynamicColorEnabled,
                modifier = Modifier.padding(padding),
            )
        }
    }

    pendingCacheClear?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingCacheClear = null },
            title = { Text(stringResource(R.string.advanced_clear_cache_title)) },
            text = {
                Text(
                    stringResource(
                        if (target == CacheClearTarget.CARDS) {
                            R.string.advanced_clear_cards_body
                        } else {
                            R.string.advanced_clear_diagrams_body
                        },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCacheClear = null
                        when (target) {
                            CacheClearTarget.CARDS -> viewModel.clearRenderCache()
                            CacheClearTarget.DIAGRAMS -> viewModel.clearDiagramCache()
                        }
                    },
                ) {
                    Text(stringResource(R.string.advanced_clear_cache_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCacheClear = null }) {
                    Text(stringResource(R.string.advanced_cancel))
                }
            },
        )
    }

    if (editingOcrLimit) {
        NumberDialog(
            title = stringResource(R.string.advanced_ocr_limit),
            initial = (state as? AdvancedSettingsUiState.Content)
                ?.preferences
                ?.defaultOcrPageLimit
                ?: 20,
            min = 1,
            max = 500,
            onDismiss = { editingOcrLimit = false },
            onSave = {
                viewModel.setDefaultOcrPageLimit(it)
                editingOcrLimit = false
            },
        )
    }

    if (editingQueueSize) {
        NumberDialog(
            title = stringResource(R.string.advanced_queue_size),
            initial = (state as? AdvancedSettingsUiState.Content)
                ?.preferences
                ?.preRenderQueueSize
                ?: 25,
            min = 1,
            max = 100,
            onDismiss = { editingQueueSize = false },
            onSave = {
                viewModel.setPreRenderQueueSize(it)
                editingQueueSize = false
            },
        )
    }

    editingSuffixTask?.let { task ->
        PromptSuffixDialog(
            taskLabel = taskLabel(task),
            initial = (state as? AdvancedSettingsUiState.Content)
                ?.preferences
                ?.customTaskPromptSuffixes
                ?.get(task)
                .orEmpty(),
            onDismiss = { editingSuffixTask = null },
            onSave = {
                viewModel.setPromptSuffix(task, it)
                editingSuffixTask = null
            },
        )
    }

    inspectedLog?.let { entry ->
        LogDetailsDialog(
            entry = entry,
            onDismiss = { inspectedLog = null },
        )
    }

    if (showBackupDialog) {
        BackupDialog(
            onDismiss = { showBackupDialog = false },
            onConfirm = { pass, options ->
                viewModel.createBackup(pass, options) {
                    showBackupDialog = false
                }
            },
            onChooseLocation = { pass, options, automatic, frequency ->
                pendingBackupDestination = BackupDestinationRequest(
                    passphrase = pass,
                    options = options,
                    automaticEnabled = automatic,
                    frequency = frequency,
                )
                backupDocumentPicker.launch("facts-wallpaper-backup.fwbk")
            },
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            selectedUri = selectedRestoreUri,
            onPickFile = { restoreDocumentPicker.launch(arrayOf("*/*")) },
            onDismiss = {
                showRestoreDialog = false
                selectedRestoreUri = null
            },
            onConfirm = { pass ->
                val uri = selectedRestoreUri
                if (uri != null) {
                    viewModel.restoreBackupFromUri(uri, pass) { _, _ ->
                        showRestoreDialog = false
                        selectedRestoreUri = null
                    }
                } else {
                    viewModel.restoreBackup(pass) { _, _ ->
                        showRestoreDialog = false
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedContent(
    state: AdvancedSettingsUiState.Content,
    onEditOcrLimit: () -> Unit,
    onEditQueueSize: () -> Unit,
    onAdvancedOcrChange: (Boolean) -> Unit,
    onEnableLogsChange: (Boolean) -> Unit,
    onDoubleTapChange: (Boolean) -> Unit,
    onEditSuffix: (AiTask) -> Unit,
    onInspectLog: (AiRequestLogEntry) -> Unit,
    onClearLogs: () -> Unit,
    onRefreshStorage: () -> Unit,
    onClearRenderCache: () -> Unit,
    onClearDiagramCache: () -> Unit,
    onCompactDatabase: () -> Unit,
    onOpenBackupDialog: () -> Unit,
    onOpenRestoreDialog: () -> Unit,
    onRestoreFromFile: () -> Unit,
    onBackupToSavedLocation: () -> Unit,
    onAutomaticBackupChange: (Boolean) -> Unit,
    onBackupFrequencyChange: (AutomaticBackupFrequency) -> Unit,
    onSqlCipher: () -> Unit,
    onOpenWhatsHappening: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val prefs = state.preferences

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_theme_title),
                subtitle = stringResource(R.string.advanced_theme_helper),
            ) {
                Text(
                    text = stringResource(R.string.advanced_theme_mode),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    modifier = Modifier.padding(vertical = spacing.xs),
                ) {
                    listOf(
                        ThemeMode.SYSTEM to R.string.advanced_theme_system,
                        ThemeMode.LIGHT to R.string.advanced_theme_light,
                        ThemeMode.DARK to R.string.advanced_theme_dark,
                        ThemeMode.AMOLED to R.string.advanced_theme_amoled,
                    ).forEach { (mode, labelRes) ->
                        FilterChip(
                            selected = prefs.appThemeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_theme_dynamic_color),
                    subtitle = stringResource(R.string.advanced_theme_dynamic_color_helper),
                    checked = prefs.dynamicColorEnabled,
                    onCheckedChange = onDynamicColorChange,
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_pipeline_title),
                subtitle = stringResource(R.string.advanced_pipeline_helper),
            ) {
                ValueRow(
                    title = stringResource(R.string.advanced_whats_happening),
                    value = stringResource(R.string.advanced_whats_happening_action),
                    onClick = onOpenWhatsHappening,
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_ocr_title),
                subtitle = stringResource(R.string.advanced_ocr_helper),
            ) {
                ValueRow(
                    title = stringResource(R.string.advanced_ocr_limit),
                    value = stringResource(
                        R.string.advanced_pages_value,
                        prefs.defaultOcrPageLimit,
                    ),
                    onClick = onEditOcrLimit,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_advanced_engines),
                    subtitle = stringResource(R.string.advanced_advanced_engines_helper),
                    checked = prefs.allowAdvancedOcrEngines,
                    onCheckedChange = onAdvancedOcrChange,
                )
                ValueRow(
                    title = stringResource(R.string.advanced_queue_size),
                    value = stringResource(
                        R.string.advanced_cards_value,
                        prefs.preRenderQueueSize,
                    ),
                    onClick = onEditQueueSize,
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_suffixes_title),
                subtitle = stringResource(R.string.advanced_suffixes_helper),
            ) {
                AiTask.entries.forEach { task ->
                    val custom = prefs.customTaskPromptSuffixes[task]
                    ValueRow(
                        title = taskLabel(task),
                        value = if (custom.isNullOrBlank()) {
                            stringResource(R.string.advanced_suffix_default)
                        } else {
                            stringResource(R.string.advanced_edit_suffix)
                        },
                        onClick = { onEditSuffix(task) },
                    )
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_diagnostics_title),
                subtitle = stringResource(R.string.advanced_diagnostics_helper),
            ) {
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_diagnostics_enable),
                    subtitle = stringResource(R.string.advanced_diagnostics_enable_helper),
                    checked = prefs.enableAiDiagnosticsLog,
                    onCheckedChange = onEnableLogsChange,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_double_tap),
                    subtitle = stringResource(R.string.advanced_double_tap_helper),
                    checked = prefs.enableDoubleTapWallpaperAdvance,
                    onCheckedChange = onDoubleTapChange,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.advanced_logs_count, state.logs.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearLogs, enabled = state.logs.isNotEmpty()) {
                        Text(stringResource(R.string.advanced_logs_clear))
                    }
                }
                if (state.logs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.advanced_logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = spacing.md),
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        state.logs.forEach { entry ->
                            LogItemRow(entry = entry, onClick = { onInspectLog(entry) })
                        }
                    }
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_ledger_title),
                subtitle = stringResource(R.string.advanced_ledger_helper),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    MetricPill(
                        value = MetricsFormatter.compactTokens(state.usage.todayTokens),
                        label = stringResource(R.string.provider_usage_today),
                    )
                    MetricPill(
                        value = MetricsFormatter.costUsd(state.usage.todayCostUsd),
                        label = stringResource(R.string.provider_usage_today_cost),
                    )
                    MetricPill(
                        value = MetricsFormatter.compactTokens(state.usage.last30DaysTokens),
                        label = stringResource(R.string.provider_usage_30d),
                    )
                    MetricPill(
                        value = MetricsFormatter.costUsd(state.usage.last30DaysCostUsd),
                        label = stringResource(R.string.provider_usage_30d_cost),
                    )
                    MetricPill(
                        value = MetricsFormatter.costUsd(state.usage.allTimeCostUsd),
                        label = stringResource(R.string.provider_usage_all_time),
                    )
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_storage_title),
                subtitle = stringResource(R.string.advanced_storage_helper),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    MetricPill(
                        value = MetricsFormatter.bytes(state.storage.pdfBytes),
                        label = stringResource(R.string.advanced_storage_pdfs),
                    )
                    MetricPill(
                        value = MetricsFormatter.bytes(state.storage.cardCacheBytes),
                        label = stringResource(R.string.advanced_storage_cards),
                    )
                    MetricPill(
                        value = MetricsFormatter.bytes(state.storage.diagramCacheBytes),
                        label = stringResource(R.string.advanced_storage_diagrams),
                    )
                    MetricPill(
                        value = MetricsFormatter.bytes(state.storage.databaseBytes),
                        label = stringResource(R.string.advanced_storage_database),
                    )
                    MetricPill(
                        value = MetricsFormatter.bytes(state.storage.totalBytes),
                        label = stringResource(R.string.advanced_storage_total),
                    )
                }
                Row(
                    modifier = Modifier.padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(onClick = onRefreshStorage) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.padding(start = 4.dp))
                        Text(stringResource(R.string.advanced_storage_refresh))
                    }
                    OutlinedButton(onClick = onClearRenderCache) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.padding(start = 4.dp))
                        Text(stringResource(R.string.advanced_storage_clear_cards))
                    }
                    OutlinedButton(onClick = onClearDiagramCache) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Spacer(Modifier.padding(start = 4.dp))
                        Text(stringResource(R.string.advanced_storage_clear_diagrams))
                    }
                }
                OutlinedButton(onClick = onCompactDatabase, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Compress, contentDescription = null)
                    Spacer(Modifier.padding(start = 4.dp))
                    Text(stringResource(R.string.advanced_storage_compact))
                }
                Text(
                    text = stringResource(R.string.advanced_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_data_title),
                subtitle = stringResource(R.string.advanced_data_helper),
            ) {
                Text(
                    text = backupDestinationSummary(state.backupDestination),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_backup_automatic),
                    subtitle = stringResource(
                        R.string.advanced_backup_schedule_helper,
                        if (state.backupDestination.frequency == AutomaticBackupFrequency.WEEKLY) {
                            R.string.advanced_backup_weekly
                        } else {
                            R.string.advanced_backup_daily
                        },
                    ),
                    checked = state.backupDestination.automaticEnabled,
                    enabled = state.backupDestination.hasDestination,
                    onCheckedChange = onAutomaticBackupChange,
                )
                if (state.backupDestination.hasDestination) {
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        FilterChip(
                            selected = state.backupDestination.frequency == AutomaticBackupFrequency.DAILY,
                            onClick = {
                                onBackupFrequencyChange(AutomaticBackupFrequency.DAILY)
                            },
                            label = { Text(stringResource(R.string.advanced_backup_daily)) },
                        )
                        FilterChip(
                            selected = state.backupDestination.frequency == AutomaticBackupFrequency.WEEKLY,
                            onClick = {
                                onBackupFrequencyChange(AutomaticBackupFrequency.WEEKLY)
                            },
                            label = { Text(stringResource(R.string.advanced_backup_weekly)) },
                        )
                    }
                }
                if (state.backupDestination.hasDestination) {
                    OutlinedButton(
                        onClick = onBackupToSavedLocation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.advanced_backup_now))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Button(onClick = onOpenBackupDialog, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_backup))
                    }
                    OutlinedButton(onClick = onOpenRestoreDialog, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_restore))
                    }
                }
                OutlinedButton(
                    onClick = onRestoreFromFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.advanced_restore_from_file))
                }
                OutlinedButton(
                    onClick = onOpenBackupDialog,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.advanced_backup_choose_location))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onSqlCipher)
                        .padding(vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.advanced_sqlcipher),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.advanced_sqlcipher_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.advanced_sqlcipher_state),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogItemRow(entry: AiRequestLogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (entry.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (entry.success) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.error
            },
            modifier = Modifier.padding(end = 8.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.advanced_log_line,
                    entry.taskType.name,
                    entry.modelName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.advanced_log_meta,
                    entry.durationMs,
                    entry.inputTokens,
                    entry.outputTokens,
                    formatEpoch(entry.timestampEpochMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LogDetailsDialog(
    entry: AiRequestLogEntry,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_log_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.advanced_log_detail_meta,
                        entry.durationMs,
                        entry.inputTokens,
                        entry.outputTokens,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.advanced_log_prompt),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = entry.redactedRequestSummary.ifBlank {
                        stringResource(R.string.advanced_log_empty_value)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.advanced_log_response),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = entry.redactedResponseSnippet.ifBlank {
                        stringResource(R.string.advanced_log_empty_value)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                val errorMsg = entry.errorMessage
                if (errorMsg != null) {
                    Text(
                        text = stringResource(R.string.advanced_log_error),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.advanced_log_close))
            }
        },
    )
}

@Composable
private fun BackupDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String, options: BackupOptions) -> Unit,
    onChooseLocation: (
        passphrase: String,
        options: BackupOptions,
        automaticEnabled: Boolean,
        frequency: AutomaticBackupFrequency,
    ) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    var includeAssets by remember { mutableStateOf(false) }
    var includePdfs by remember { mutableStateOf(false) }
    var automaticBackup by remember { mutableStateOf(true) }
    var backupFrequency by remember { mutableStateOf(AutomaticBackupFrequency.DAILY) }
    val isValid = passphrase.length >= 8

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_backup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.advanced_backup_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = passphrase.isNotEmpty() && !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includeAssets = !includeAssets },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includeAssets, onCheckedChange = { includeAssets = it })
                    Text(
                        text = stringResource(R.string.advanced_backup_include_assets),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { includePdfs = !includePdfs },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = includePdfs, onCheckedChange = { includePdfs = it })
                    Text(
                        text = stringResource(R.string.advanced_backup_include_pdfs),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
                SettingSwitchRow(
                    title = stringResource(R.string.advanced_backup_automatic),
                    subtitle = stringResource(R.string.advanced_backup_schedule_helper),
                    checked = automaticBackup,
                    onCheckedChange = { automaticBackup = it },
                )
                if (automaticBackup) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = backupFrequency == AutomaticBackupFrequency.DAILY,
                            onClick = { backupFrequency = AutomaticBackupFrequency.DAILY },
                            label = { Text(stringResource(R.string.advanced_backup_daily)) },
                        )
                        FilterChip(
                            selected = backupFrequency == AutomaticBackupFrequency.WEEKLY,
                            onClick = { backupFrequency = AutomaticBackupFrequency.WEEKLY },
                            label = { Text(stringResource(R.string.advanced_backup_weekly)) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val options = BackupOptions(
                        includeRenderedAssets = includeAssets,
                        includeOriginalPdfs = includePdfs,
                    )
                    onChooseLocation(passphrase, options, automaticBackup, backupFrequency)
                },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.advanced_backup_choose_location))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.advanced_cancel))
                }
                TextButton(
                    onClick = {
                        val options = BackupOptions(
                            includeRenderedAssets = includeAssets,
                            includeOriginalPdfs = includePdfs,
                        )
                        onConfirm(passphrase, options)
                    },
                    enabled = isValid,
                ) {
                    Text(stringResource(R.string.advanced_backup_action))
                }
            }
        },
    )
}

@Composable
private fun backupDestinationSummary(status: BackupDestinationStatus): String {
    val target = if (status.hasDestination) {
        stringResource(R.string.advanced_backup_location_configured)
    } else {
        stringResource(R.string.advanced_backup_location_missing)
    }
    val lastRun = status.lastBackupAtEpochMs?.let(::formatEpoch)
        ?: stringResource(R.string.advanced_backup_never_run)
    val result = when (status.lastBackupSucceeded) {
        true -> stringResource(R.string.advanced_backup_status_success, lastRun)
        false -> stringResource(R.string.advanced_backup_status_failure, lastRun)
        null -> stringResource(R.string.advanced_backup_status_not_run)
    }
    return "$target\n$result"
}

@Composable
private fun RestoreDialog(
    selectedUri: Uri?,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }
    val isValid = passphrase.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.advanced_restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                if (selectedUri != null) {
                    Text(
                        text = stringResource(
                            R.string.advanced_restore_file_selected,
                            selectedUri.lastPathSegment ?: "backup.fwbk",
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedButton(
                    onClick = onPickFile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.advanced_restore_from_file))
                }
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.advanced_restore_passphrase_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(passphrase) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.advanced_restore_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.advanced_cancel))
            }
        },
    )
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun NumberDialog(
    title: String,
    initial: Int,
    min: Int,
    max: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial.toString()) }
    val parsed = text.toIntOrNull()
    val isValid = parsed != null && parsed in min..max

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { char -> char.isDigit() } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.advanced_number_range, min, max))
                    },
                    isError = !isValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onSave) }, enabled = isValid) {
                Text(stringResource(R.string.advanced_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.advanced_cancel))
            }
        },
    )
}

@Composable
private fun PromptSuffixDialog(
    taskLabel: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initial) { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_suffix_title, taskLabel)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.advanced_suffix_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text(stringResource(R.string.advanced_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.advanced_cancel))
            }
        },
    )
}

@Composable
private fun taskLabel(task: AiTask): String = stringResource(
    when (task) {
        AiTask.OUTLINE -> R.string.advanced_task_outline
        AiTask.FACTS -> R.string.advanced_task_facts
        AiTask.JSON_REPAIR -> R.string.advanced_task_repair
        AiTask.FORMULA_VERIFICATION -> R.string.advanced_task_formulas
        AiTask.DIAGRAM_GENERATION -> R.string.advanced_task_diagrams
        AiTask.TEMPLATE_GENERATION -> R.string.advanced_task_templates
    },
)

private fun formatEpoch(epochMs: Long): String = try {
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(epochMs))
} catch (_: Throwable) {
    epochMs.toString()
}

@Composable
private fun AdvancedLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

private enum class CacheClearTarget {
    CARDS,
    DIAGRAMS,
}

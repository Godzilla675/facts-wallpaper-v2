package com.factswallpaper.feature.settings

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                onClearRenderCache = viewModel::clearRenderCache,
                onClearDiagramCache = viewModel::clearDiagramCache,
                onCompactDatabase = viewModel::compactDatabase,
                onOpenBackupDialog = { showBackupDialog = true },
                onOpenRestoreDialog = { showRestoreDialog = true },
                onSqlCipher = viewModel::sqlCipherInfo,
                modifier = Modifier.padding(padding),
            )
        }
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
        val current = (state as? AdvancedSettingsUiState.Content)
            ?.preferences
            ?.customTaskPromptSuffixes
            ?.get(task)
            .orEmpty()
        PromptSuffixDialog(
            taskLabel = taskLabel(task),
            initial = current,
            onDismiss = { editingSuffixTask = null },
            onSave = {
                viewModel.setPromptSuffix(task, it)
                editingSuffixTask = null
            },
        )
    }

    inspectedLog?.let { log ->
        PromptInspectorDialog(
            log = log,
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
        )
    }

    if (showRestoreDialog) {
        RestoreDialog(
            onDismiss = { showRestoreDialog = false },
            onConfirm = { pass ->
                viewModel.restoreBackup(pass) { _, _ ->
                    showRestoreDialog = false
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
    onSqlCipher: () -> Unit,
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
                    val suffix = prefs.customTaskPromptSuffixes[task]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onEditSuffix(task) }
                            .padding(vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = taskLabel(task),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = suffix?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.advanced_suffix_default),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.advanced_edit_suffix),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.advanced_logs_count,
                            state.logs.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = onClearLogs, enabled = state.logs.isNotEmpty()) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Text(
                            text = stringResource(R.string.advanced_logs_clear),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (state.logs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.advanced_logs_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                } else {
                    state.logs.forEach { log ->
                        LogRow(log = log, onClick = { onInspectLog(log) })
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
                        value = MetricsFormatter.compactTokens(
                            state.usage.todayTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ),
                        label = stringResource(R.string.provider_usage_today),
                    )
                    MetricPill(
                        value = MetricsFormatter.costUsd(state.usage.todayCostUsd),
                        label = stringResource(R.string.provider_usage_today_cost),
                    )
                    MetricPill(
                        value = MetricsFormatter.compactTokens(
                            state.usage.last30DaysTokens.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        ),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(onClick = onRefreshStorage, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            text = stringResource(R.string.advanced_storage_refresh),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    OutlinedButton(onClick = onClearRenderCache, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Storage, contentDescription = null)
                        Text(
                            text = stringResource(R.string.advanced_storage_clear_cards),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(onClick = onClearDiagramCache, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_storage_clear_diagrams))
                    }
                    OutlinedButton(onClick = onCompactDatabase, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Compress, contentDescription = null)
                        Text(
                            text = stringResource(R.string.advanced_storage_compact),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.advanced_storage_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.advanced_data_title),
                subtitle = stringResource(R.string.advanced_data_helper),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    Button(onClick = onOpenBackupDialog, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_backup))
                    }
                    OutlinedButton(onClick = onOpenRestoreDialog, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_restore))
                    }
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
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: AiRequestLogEntry, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(vertical = spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        Icon(
            imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (log.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    R.string.advanced_log_line,
                    taskLabel(log.taskType),
                    log.modelName,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.advanced_log_meta,
                    log.durationMs,
                    log.inputTokens,
                    log.outputTokens,
                    formatEpoch(log.timestampEpochMs),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PromptInspectorDialog(
    log: AiRequestLogEntry,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_log_details_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = "${taskLabel(log.taskType)} • ${log.modelName}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Latency: ${log.durationMs}ms | Tokens: ${log.inputTokens} in, ${log.outputTokens} out",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = stringResource(R.string.advanced_log_prompt),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = log.redactedRequestSummary.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = stringResource(R.string.advanced_log_response),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = log.redactedResponseSnippet.ifBlank { "(empty)" },
                    style = MaterialTheme.typography.bodyMedium,
                )
                val errorMsg = log.errorMessage
                if (!errorMsg.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(spacing.xs))
                    Text(
                        text = stringResource(R.string.advanced_log_error),
                        style = MaterialTheme.typography.labelLarge,
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
) {
    var passphrase by remember { mutableStateOf("") }
    var includeAssets by remember { mutableStateOf(false) }
    var includePdfs by remember { mutableStateOf(false) }
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
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { includeAssets = !includeAssets },
                ) {
                    Checkbox(checked = includeAssets, onCheckedChange = { includeAssets = it })
                    Text(
                        text = stringResource(R.string.advanced_backup_include_assets),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clickable { includePdfs = !includePdfs },
                ) {
                    Checkbox(checked = includePdfs, onCheckedChange = { includePdfs = it })
                    Text(
                        text = stringResource(R.string.advanced_backup_include_pdfs),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(passphrase, BackupOptions(includeAssets, includePdfs))
                },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.advanced_backup_action))
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
private fun RestoreDialog(
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

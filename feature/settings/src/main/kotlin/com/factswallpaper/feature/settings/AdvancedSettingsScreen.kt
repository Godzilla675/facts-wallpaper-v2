package com.factswallpaper.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.ui.UiMessageEffect
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
            is AdvancedSettingsUiState.Error -> AdvancedError(
                messageRes = value.messageRes,
                modifier = Modifier.padding(padding),
            )
            is AdvancedSettingsUiState.Content -> AdvancedContent(
                state = value,
                onEditOcrLimit = { editingOcrLimit = true },
                onEditQueueSize = { editingQueueSize = true },
                onAdvancedOcrChange = viewModel::setAllowAdvancedOcrEngines,
                onEnableLogsChange = viewModel::setEnableAiDiagnosticsLog,
                onDoubleTapChange = viewModel::setEnableDoubleTapAdvance,
                onEditSuffix = { editingSuffixTask = it },
                onClearLogs = viewModel::clearLogs,
                onRefreshStorage = viewModel::refreshStorage,
                onClearRenderCache = viewModel::clearRenderCache,
                onClearDiagramCache = viewModel::clearDiagramCache,
                onBackup = viewModel::backupNotYet,
                onRestore = viewModel::restoreNotYet,
                onSqlCipher = viewModel::sqlCipherNotYet,
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
    onClearLogs: () -> Unit,
    onRefreshStorage: () -> Unit,
    onClearRenderCache: () -> Unit,
    onClearDiagramCache: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
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
                        LogRow(log)
                    }
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
                Button(
                    onClick = onClearDiagramCache,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                ) {
                    Text(stringResource(R.string.advanced_storage_clear_diagrams))
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
                    OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_backup))
                    }
                    OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.advanced_restore))
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
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
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
private fun LogRow(log: AiRequestLogEntry) {
    val spacing = LocalSpacing.current
    val errorMessage = log.errorMessage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.sm),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (log.success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (log.success) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = spacing.sm),
        ) {
            Text(
                text = stringResource(
                    R.string.advanced_log_line,
                    taskLabel(log.taskType),
                    log.modelName,
                ),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.advanced_log_meta,
                    log.durationMs,
                    log.inputTokens,
                    log.outputTokens,
                    formatTimestamp(log.timestampEpochMs),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    var value by remember { mutableStateOf(initial.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(stringResource(R.string.advanced_number_range, min, max))
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = value.toIntOrNull() ?: initial
                    onSave(parsed.coerceIn(min, max))
                },
                enabled = value.toIntOrNull() != null,
            ) {
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
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.advanced_suffix_title, taskLabel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.advanced_suffix_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value) }) {
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
    }
)

private fun formatTimestamp(epochMs: Long): String =
    DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

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

@Composable
private fun AdvancedError(messageRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
    }
}

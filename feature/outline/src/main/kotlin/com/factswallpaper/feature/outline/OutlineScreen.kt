package com.factswallpaper.feature.outline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.LessonSelectionRow
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SettingSwitchRow
import com.factswallpaper.core.ui.component.VerificationBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineScreen(
    onBack: () -> Unit,
    onOpenFacts: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OutlineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingLesson by remember { mutableStateOf<Lesson?>(null) }
    var addingLesson by remember { mutableStateOf(false) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val content = state as? OutlineUiState.Content
    LaunchedEffect(content?.factsJobStarted, content?.document?.id) {
        if (content != null && content.factsJobStarted) {
            viewModel.consumeFactsJobStarted()
            onOpenFacts(content.document.id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.outline_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.outline_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            OutlineUiState.Loading -> OutlineLoading(Modifier.padding(padding))
            is OutlineUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is OutlineUiState.Content -> OutlineContent(
                state = value,
                onSetLessonSelected = viewModel::setLessonSelected,
                onEditLesson = { editingLesson = it },
                onAddLesson = { addingLesson = true },
                onSelectAll = { viewModel.setAllSelected(true) },
                onClearAll = { viewModel.setAllSelected(false) },
                onMergeSelected = viewModel::mergeSelectedLessons,
                onDensityChange = viewModel::setDensity,
                onIncludeFormulasChange = viewModel::setIncludeFormulas,
                onIncludeDiagramsChange = viewModel::setIncludeDiagrams,
                onVerifyFormulasChange = viewModel::setAutoVerifyFormulas,
                onReDetect = viewModel::reDetectOutline,
                onCancelWork = viewModel::cancelWork,
                onGenerateFacts = viewModel::generateFacts,
                onOpenFacts = { onOpenFacts(value.document.id) },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (content != null && (addingLesson || editingLesson != null)) {
        LessonEditorDialog(
            initial = editingLesson,
            maxPage = content.document.pageCount.coerceAtLeast(1),
            onDismiss = {
                addingLesson = false
                editingLesson = null
            },
            onSave = { title, summary, startPage, endPage ->
                viewModel.saveLesson(
                    lessonId = editingLesson?.id,
                    title = title,
                    summary = summary,
                    startPage = startPage,
                    endPage = endPage,
                )
                addingLesson = false
                editingLesson = null
            },
            onSplit = editingLesson?.let { lesson ->
                { splitPage ->
                    viewModel.splitLesson(lesson.id, splitPage)
                    editingLesson = null
                }
            },
            onDelete = editingLesson?.let { lesson ->
                {
                    viewModel.deleteLesson(lesson.id)
                    editingLesson = null
                }
            },
        )
    }

    val pendingCostGuard = content?.pendingCostGuard
    if (content != null && pendingCostGuard != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissCostGuard,
            title = { Text(stringResource(R.string.outline_cost_guard_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.outline_cost_guard_body,
                        pendingCostGuard.estimateUsd,
                        content.costGuardThresholdUsd,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmCostGuard) {
                    Text(stringResource(R.string.outline_cost_guard_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissCostGuard) {
                    Text(stringResource(R.string.outline_cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OutlineContent(
    state: OutlineUiState.Content,
    onSetLessonSelected: (String, Boolean) -> Unit,
    onEditLesson: (Lesson) -> Unit,
    onAddLesson: () -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onMergeSelected: () -> Unit,
    onDensityChange: (FactDensity) -> Unit,
    onIncludeFormulasChange: (Boolean) -> Unit,
    onIncludeDiagramsChange: (Boolean) -> Unit,
    onVerifyFormulasChange: (Boolean) -> Unit,
    onReDetect: () -> Unit,
    onCancelWork: () -> Unit,
    onGenerateFacts: () -> Unit,
    onOpenFacts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val selected = state.lessons.filter { it.selected }
    val minimumFacts = selected.size * state.settings.factDensity.minFactsPerLesson
    val maximumFacts = selected.size * state.settings.factDensity.maxFactsPerLesson
    val documentError = state.document.lastErrorMessage

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            DocumentHeader(
                document = state.document,
                lessonCount = state.lessons.size,
                onOpenFacts = onOpenFacts,
            )
        }
        if (!state.online) {
            item {
                InlineBanner(
                    message = stringResource(R.string.outline_offline_banner),
                    severity = BannerSeverity.WARNING,
                )
            }
        }
        if (state.generation.isRunning) {
            item {
                SectionGroupCard(
                    title = if (state.generation.total > 0) {
                        stringResource(
                            R.string.outline_work_running,
                            state.generation.completed,
                            state.generation.total,
                        )
                    } else {
                        stringResource(R.string.outline_work_preparing)
                    },
                ) {
                    if (state.generation.total > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (state.generation.completed.toFloat() / state.generation.total.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.sm),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onCancelWork) {
                            Text(stringResource(R.string.outline_work_cancel))
                        }
                    }
                }
            }
        }
        if (documentError != null) {
            item {
                InlineBanner(
                    message = documentError,
                    severity = BannerSeverity.ERROR,
                    actionLabel = stringResource(R.string.outline_retry),
                    onAction = onReDetect,
                )
            }
        }
        item {
            OutlineLessonActions(
                onSelectAll = onSelectAll,
                onClearAll = onClearAll,
                onAddLesson = onAddLesson,
                onMergeSelected = onMergeSelected,
            )
        }
        if (state.lessons.isEmpty()) {
            item {
                SectionGroupCard(
                    title = stringResource(R.string.outline_empty_title),
                    subtitle = stringResource(R.string.outline_empty_body),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = onAddLesson) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(
                                text = stringResource(R.string.outline_add_lesson),
                                modifier = Modifier.padding(start = spacing.xs),
                            )
                        }
                        OutlinedButton(
                            onClick = onReDetect,
                            enabled = state.online && !state.generation.isRunning,
                        ) {
                            Text(stringResource(R.string.outline_redetect))
                        }
                    }
                }
            }
        }
        items(state.lessons, key = { it.id }) { lesson ->
            LessonSelectionRow(
                lesson = lesson,
                lessonNumber = state.lessons.indexOf(lesson) + 1,
                onSelectedChange = { checked -> onSetLessonSelected(lesson.id, checked) },
                onEdit = { onEditLesson(lesson) },
            )
        }
        item {
            DensitySettingsCard(
                density = state.settings.factDensity,
                includeFormulas = state.settings.includeFormulas,
                includeDiagrams = state.settings.includeDiagrams,
                verifyFormulas = state.settings.autoVerifyFormulas,
                onDensityChange = onDensityChange,
                onIncludeFormulasChange = onIncludeFormulasChange,
                onIncludeDiagramsChange = onIncludeDiagramsChange,
                onVerifyFormulasChange = onVerifyFormulasChange,
            )
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.outline_estimate_title),
                subtitle = stringResource(R.string.outline_estimate_helper),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    MetricPill(
                        value = stringResource(
                            R.string.outline_estimate_facts,
                            minimumFacts,
                            maximumFacts,
                        ),
                    )
                    MetricPill(
                        value = stringResource(
                            R.string.outline_estimate_lessons,
                            selected.size,
                        ),
                    )
                    state.estimate?.let { est ->
                        MetricPill(
                            value = stringResource(
                                R.string.outline_estimate_cost,
                                est.estimatedCostUsd,
                                est.profile.name,
                            ),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onReDetect,
                        modifier = Modifier.weight(1f),
                        enabled = state.online && !state.generation.isRunning,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Text(
                            text = stringResource(R.string.outline_redetect),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                    Button(
                        onClick = onGenerateFacts,
                        modifier = Modifier.weight(1f),
                        enabled = selected.isNotEmpty() && state.online && !state.generation.isRunning,
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Text(
                            text = stringResource(R.string.outline_generate_facts),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentHeader(
    document: Document,
    lessonCount: Int,
    onOpenFacts: () -> Unit,
) {
    val spacing = LocalSpacing.current
    SectionGroupCard(
        title = document.title,
        subtitle = stringResource(
            R.string.outline_document_summary,
            document.pageCount,
            lessonCount,
        ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MetricPill(
                value = stringResource(R.string.outline_pages_value, document.pageCount),
                label = stringResource(R.string.outline_pages_label),
            )
            MetricPill(
                value = stringResource(R.string.outline_facts_value, document.factCount),
                label = stringResource(R.string.outline_facts_label),
            )
            if (!document.includedInRotation) {
                MetricPill(value = stringResource(R.string.outline_not_in_rotation))
            }
        }
        if (document.factCount > 0) {
            Button(
                onClick = onOpenFacts,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Text(
                    text = stringResource(R.string.outline_view_facts, document.factCount),
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
        }
        if (document.scannedPageCount > 0) {
            Row(
                modifier = Modifier.padding(top = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                VerificationBadge(status = VerificationStatus.UNVERIFIED)
                Text(
                    text = stringResource(
                        R.string.outline_scanned_pages,
                        document.scannedPageCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OutlineLessonActions(
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onAddLesson: () -> Unit,
    onMergeSelected: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onSelectAll) {
            Icon(Icons.Filled.DoneAll, contentDescription = null)
            Text(
                text = stringResource(R.string.outline_select_all),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        TextButton(onClick = onClearAll) {
            Text(stringResource(R.string.outline_clear_all))
        }
        TextButton(onClick = onAddLesson) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = stringResource(R.string.outline_add_lesson),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        TextButton(onClick = onMergeSelected) {
            Icon(Icons.Filled.CallMerge, contentDescription = null)
            Text(
                text = stringResource(R.string.outline_merge),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun DensitySettingsCard(
    density: FactDensity,
    includeFormulas: Boolean,
    includeDiagrams: Boolean,
    verifyFormulas: Boolean,
    onDensityChange: (FactDensity) -> Unit,
    onIncludeFormulasChange: (Boolean) -> Unit,
    onIncludeDiagramsChange: (Boolean) -> Unit,
    onVerifyFormulasChange: (Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    SectionGroupCard(
        title = stringResource(R.string.outline_settings_title),
        subtitle = stringResource(R.string.outline_settings_helper),
    ) {
        Text(
            text = stringResource(R.string.outline_density_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            modifier = Modifier.padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            FactDensity.entries.forEach { option ->
                FilterChip(
                    selected = density == option,
                    onClick = { onDensityChange(option) },
                    label = { Text(densityLabel(option)) },
                )
            }
        }
        Text(
            text = stringResource(
                R.string.outline_density_helper,
                density.minFactsPerLesson,
                density.maxFactsPerLesson,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = spacing.sm),
        )
        SettingSwitchRow(
            title = stringResource(R.string.outline_include_formulas),
            subtitle = stringResource(R.string.outline_include_formulas_helper),
            checked = includeFormulas,
            onCheckedChange = onIncludeFormulasChange,
        )
        SettingSwitchRow(
            title = stringResource(R.string.outline_include_diagrams),
            subtitle = stringResource(R.string.outline_include_diagrams_helper),
            checked = includeDiagrams,
            onCheckedChange = onIncludeDiagramsChange,
        )
        SettingSwitchRow(
            title = stringResource(R.string.outline_verify_formulas),
            subtitle = stringResource(R.string.outline_verify_formulas_helper),
            checked = verifyFormulas,
            onCheckedChange = onVerifyFormulasChange,
        )
    }
}

@Composable
private fun LessonEditorDialog(
    initial: Lesson?,
    maxPage: Int,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int) -> Unit,
    onSplit: ((Int) -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var summary by remember(initial) { mutableStateOf(initial?.summary.orEmpty()) }
    var startPage by remember(initial) { mutableStateOf((initial?.startPage ?: 1).toString()) }
    var endPage by remember(initial) {
        mutableStateOf((initial?.endPage ?: maxPage).toString())
    }
    var splitPage by remember(initial) {
        val defaultSplit = initial?.let { ((it.startPage + it.endPage + 1) / 2).coerceAtLeast(it.startPage + 1) }
        mutableStateOf(defaultSplit?.toString().orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.outline_add_lesson_title
                    else R.string.outline_edit_lesson_title,
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.outline_lesson_title_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text(stringResource(R.string.outline_lesson_summary_label)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    OutlinedTextField(
                        value = startPage,
                        onValueChange = { value -> startPage = value.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.outline_start_page)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endPage,
                        onValueChange = { value -> endPage = value.filter(Char::isDigit) },
                        label = { Text(stringResource(R.string.outline_end_page)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.outline_page_limit, maxPage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (initial != null && onSplit != null && initial.endPage > initial.startPage) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = splitPage,
                            onValueChange = { value -> splitPage = value.filter(Char::isDigit) },
                            label = {
                                Text(
                                    stringResource(
                                        R.string.outline_split_at_page_label,
                                        initial.startPage + 1,
                                        initial.endPage,
                                    ),
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = {
                                val target = splitPage.toIntOrNull() ?: return@OutlinedButton
                                onSplit(target)
                            },
                        ) {
                            Icon(Icons.Filled.CallSplit, contentDescription = null)
                            Text(
                                text = stringResource(R.string.outline_split_action),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
                if (confirmDelete) {
                    InlineBanner(
                        message = stringResource(R.string.outline_delete_confirm_body),
                        severity = BannerSeverity.WARNING,
                        actionLabel = stringResource(R.string.outline_delete_confirm),
                        onAction = {
                            confirmDelete = false
                            onDelete?.invoke()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title,
                        summary,
                        startPage.toIntOrNull() ?: 1,
                        endPage.toIntOrNull() ?: maxPage,
                    )
                },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.outline_save))
            }
        },
        dismissButton = {
            Row {
                if (onDelete != null && !confirmDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text(
                            text = stringResource(R.string.outline_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.outline_cancel))
                }
            }
        },
    )
}

@Composable
private fun OutlineLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@Composable
private fun densityLabel(density: FactDensity): String = stringResource(
    when (density) {
        FactDensity.CONCISE -> R.string.outline_density_concise
        FactDensity.BALANCED -> R.string.outline_density_balanced
        FactDensity.DEEP -> R.string.outline_density_deep
    },
)

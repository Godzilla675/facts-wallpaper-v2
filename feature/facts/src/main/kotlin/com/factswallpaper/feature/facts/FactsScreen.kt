package com.factswallpaper.feature.facts

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.export.CardExportFormat
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.rememberHapticFeedback
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.FactListItemCard
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SourceBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactsScreen(
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    onOpenStudy: (documentId: String?) -> Unit = {},
    onOpenPdfViewer: ((documentId: String, page: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: FactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val copiedMessage = stringResource(com.factswallpaper.core.ui.R.string.fact_copied_to_clipboard)
    val deletedMessage = stringResource(R.string.facts_deleted)
    val undoLabel = stringResource(R.string.facts_undo)
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    var editingFact by remember { mutableStateOf<Fact?>(null) }
    var regeneratingFact by remember { mutableStateOf<Fact?>(null) }
    var showExportFormatDialog by remember { mutableStateOf(false) }
    var selectedExportFormat by remember { mutableStateOf(CardExportFormat.MARKDOWN) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            when (selectedExportFormat) {
                CardExportFormat.ZIP -> "application/zip"
                CardExportFormat.PDF -> "application/pdf"
                CardExportFormat.MARKDOWN -> "text/markdown"
            },
        ),
    ) { uri ->
        if (uri != null) {
            viewModel.exportFacts(uri, context, selectedExportFormat)
        }
    }

    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.facts_title)) },
                actions = {
                    val content = state as? FactsUiState.Content
                    if (content != null && content.facts.isNotEmpty()) {
                        IconButton(onClick = { onOpenStudy(content.filter.documentId) }) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = stringResource(R.string.facts_study_mode),
                            )
                        }
                        IconButton(onClick = { showExportFormatDialog = true }) {
                            Icon(
                                imageVector = Icons.Filled.FileDownload,
                                contentDescription = stringResource(R.string.facts_export),
                            )
                        }
                    }
                    IconButton(onClick = onOpenTemplates) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = stringResource(R.string.facts_open_templates),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            FactsUiState.Loading -> CenteredLoading(Modifier.padding(padding))
            is FactsUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is FactsUiState.Content -> FactsContent(
                state = value,
                onQueryChange = viewModel::updateQuery,
                onClearQuery = viewModel::clearQuery,
                onStatusChange = viewModel::setStatus,
                onTagChange = viewModel::setTag,
                onDocumentChange = viewModel::setDocument,
                onClearLesson = { viewModel.setLesson(null) },
                onClearFilters = viewModel::clearFilters,
                onToggleActive = viewModel::toggleActive,
                onToggleFavorite = viewModel::toggleFavorite,
                onEdit = { editingFact = it },
                onRegenerate = { fact -> regeneratingFact = fact },
                onCopyText = { fact ->
                    clipboardManager.setText(AnnotatedString(fact.text))
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(copiedMessage)
                    }
                },
                onShareImage = { fact -> viewModel.shareCardAsImage(fact, context) },
                onJumpToSourcePage = { fact ->
                    val page = fact.sourcePage ?: 1
                    onOpenPdfViewer?.invoke(fact.documentId, page)
                },
                onDelete = { fact ->
                    viewModel.deleteFactWithUndo(fact.id) { onUndo ->
                        coroutineScope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = deletedMessage,
                                actionLabel = undoLabel,
                                duration = SnackbarDuration.Short,
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                onUndo()
                            }
                        }
                    }
                },
                onCancelWork = viewModel::cancelGeneration,
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showExportFormatDialog) {
        AlertDialog(
            onDismissRequest = { showExportFormatDialog = false },
            title = { Text(stringResource(R.string.facts_export_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            selectedExportFormat = CardExportFormat.ZIP
                            showExportFormatDialog = false
                            exportLauncher.launch("facts_export_${System.currentTimeMillis()}.zip")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.facts_export_zip), modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            selectedExportFormat = CardExportFormat.PDF
                            showExportFormatDialog = false
                            exportLauncher.launch("facts_export_${System.currentTimeMillis()}.pdf")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.facts_export_pdf), modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(
                        onClick = {
                            selectedExportFormat = CardExportFormat.MARKDOWN
                            showExportFormatDialog = false
                            exportLauncher.launch("facts_export_${System.currentTimeMillis()}.md")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.facts_export_md), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showExportFormatDialog = false }) {
                    Text(stringResource(R.string.facts_cancel))
                }
            },
        )
    }

    editingFact?.let { fact ->
        FactEditorSheet(
            fact = fact,
            onDismiss = { editingFact = null },
            onSave = { text, latex, tags, template, diagramType, diagramSource, mode, page ->
                viewModel.saveFact(
                    factId = fact.id,
                    text = text,
                    latex = latex,
                    tags = tags,
                    templateType = template,
                    diagramType = diagramType,
                    diagramSource = diagramSource,
                    sourceReferenceModeOverride = mode,
                    sourcePage = page,
                )
                editingFact = null
            },
        )
    }

    regeneratingFact?.let { fact ->
        FactRegenerateDialog(
            fact = fact,
            onDismiss = { regeneratingFact = null },
            onConfirm = { instruction ->
                viewModel.regenerate(fact.id, instruction)
                regeneratingFact = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FactsContent(
    state: FactsUiState.Content,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onStatusChange: (FactStatusFilter) -> Unit,
    onTagChange: (String?) -> Unit,
    onDocumentChange: (String?) -> Unit,
    onClearLesson: () -> Unit,
    onClearFilters: () -> Unit,
    onToggleActive: (String, Boolean) -> Unit,
    onToggleFavorite: (String, Boolean) -> Unit,
    onEdit: (Fact) -> Unit,
    onRegenerate: (Fact) -> Unit,
    onCopyText: (Fact) -> Unit,
    onShareImage: (Fact) -> Unit,
    onJumpToSourcePage: (Fact) -> Unit,
    onDelete: (Fact) -> Unit,
    onCancelWork: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val haptic = rememberHapticFeedback()
    val filter = state.filter
    val documentTitle = filter.documentId?.let { docId ->
        state.documents.firstOrNull { it.id == docId }?.title
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (!state.isOnline) {
            InlineBanner(
                message = stringResource(R.string.facts_offline_banner),
                severity = BannerSeverity.WARNING,
                modifier = Modifier.padding(
                    start = spacing.lg,
                    end = spacing.lg,
                    top = spacing.sm,
                ),
            )
        }

        when (val work = state.generationWork) {
            is FactsWorkUi.Running -> {
                InlineBanner(
                    message = if (work.totalLessons > 0) {
                        stringResource(
                            R.string.facts_work_running,
                            work.completedLessons,
                            work.totalLessons,
                            work.factsCreated,
                        )
                    } else {
                        stringResource(R.string.facts_work_preparing)
                    },
                    severity = BannerSeverity.INFO,
                    actionLabel = stringResource(R.string.facts_work_cancel),
                    onAction = onCancelWork,
                    modifier = Modifier.padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.sm,
                    ),
                )
            }
            is FactsWorkUi.Failed -> {
                InlineBanner(
                    message = work.message ?: stringResource(R.string.facts_work_failed),
                    severity = BannerSeverity.ERROR,
                    modifier = Modifier.padding(
                        start = spacing.lg,
                        end = spacing.lg,
                        top = spacing.sm,
                    ),
                )
            }
            is FactsWorkUi.Succeeded -> {
                work.message?.let {
                    InlineBanner(
                        message = it,
                        severity = BannerSeverity.INFO,
                        modifier = Modifier.padding(
                            start = spacing.lg,
                            end = spacing.lg,
                            top = spacing.sm,
                        ),
                    )
                }
            }
            FactsWorkUi.Idle -> Unit
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Column(
                modifier = Modifier.padding(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = filter.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.facts_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (filter.query.isNotEmpty()) {
                            IconButton(onClick = onClearQuery) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.facts_clear_search),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    item {
                        FilterChip(
                            selected = filter.documentId == null,
                            onClick = { onDocumentChange(null) },
                            label = { Text(stringResource(R.string.facts_all_documents)) },
                        )
                    }
                    items(state.documents, key = { it.id }) { doc ->
                        FilterChip(
                            selected = filter.documentId == doc.id,
                            onClick = { onDocumentChange(doc.id) },
                            label = { Text(doc.title) },
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    items(FactStatusFilter.entries, key = { it.name }) { status ->
                        FilterChip(
                            selected = filter.status == status,
                            onClick = { onStatusChange(status) },
                            label = { Text(statusLabel(status)) },
                        )
                    }
                }

                if (state.tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(state.tags, key = { it.tag }) { tag ->
                            FilterChip(
                                selected = filter.tag == tag.tag,
                                onClick = { onTagChange(if (filter.tag == tag.tag) null else tag.tag) },
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.facts_tag_chip,
                                            tag.tag,
                                            tag.factCount,
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }

                if (filter.lessonId != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.facts_lesson_filter_active),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onClearLesson) {
                            Text(stringResource(R.string.facts_clear_lesson_filter))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (documentTitle != null) {
                            stringResource(
                                R.string.facts_count_in_document,
                                state.facts.size,
                                documentTitle,
                            )
                        } else {
                            stringResource(R.string.facts_count, state.facts.size)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (filter.hasActiveFilters) {
                        TextButton(onClick = onClearFilters) {
                            Text(stringResource(R.string.facts_clear_filters))
                        }
                    }
                }
            }
        }

        if (state.facts.isEmpty()) {
            if (filter.hasActiveFilters) {
                EmptyStatePanel(
                    title = stringResource(R.string.facts_no_matches_title),
                    message = stringResource(R.string.facts_no_matches_body),
                    actionLabel = stringResource(R.string.facts_clear_filters),
                    onAction = onClearFilters,
                    icon = Icons.Filled.FilterAlt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                EmptyStatePanel(
                    title = stringResource(R.string.facts_empty_title),
                    message = stringResource(R.string.facts_empty_body),
                    actionLabel = stringResource(R.string.facts_open_library),
                    onAction = onOpenLibrary,
                    icon = Icons.Filled.Lightbulb,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = spacing.lg,
                    end = spacing.lg,
                    bottom = spacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                items(state.facts, key = { it.id }) { fact ->
                    FactListItemCard(
                        fact = fact,
                        sourceReferenceMode = fact.sourceReferenceModeOverride
                            ?: SourceReferenceMode.COMPACT,
                        onToggleActive = { active ->
                            haptic()
                            onToggleActive(fact.id, active)
                        },
                        onToggleFavorite = { favorite ->
                            haptic()
                            onToggleFavorite(fact.id, favorite)
                        },
                        onEdit = { onEdit(fact) },
                        onRegenerate = { onRegenerate(fact) },
                        onCopyText = { onCopyText(fact) },
                        onShareAsImage = { onShareImage(fact) },
                        onJumpToSource = if (fact.sourcePage != null) {
                            { onJumpToSourcePage(fact) }
                        } else null,
                        onDelete = { onDelete(fact) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FactEditorSheet(
    fact: Fact,
    onDismiss: () -> Unit,
    onSave: (
        text: String,
        latex: String,
        tags: List<String>,
        template: CardTemplateType,
        diagramType: DiagramKind,
        diagramSource: String?,
        sourceReferenceModeOverride: SourceReferenceMode?,
        sourcePage: Int?,
    ) -> Unit,
) {
    val spacing = LocalSpacing.current
    var text by remember(fact.id) { mutableStateOf(fact.text) }
    var latex by remember(fact.id) { mutableStateOf(fact.latex.orEmpty()) }
    var tagsText by remember(fact.id) { mutableStateOf(fact.tags.joinToString(", ")) }
    var templateType by remember(fact.id) { mutableStateOf(fact.templateType) }
    var diagramKind by remember(fact.id) { mutableStateOf(fact.diagramType) }
    var diagramSource by remember(fact.id) { mutableStateOf(fact.diagramSource.orEmpty()) }
    var sourceReferenceMode by remember(fact.id) { mutableStateOf(fact.sourceReferenceModeOverride) }
    var sourcePageText by remember(fact.id) { mutableStateOf(fact.sourcePage?.toString().orEmpty()) }

    var templateExpanded by remember { mutableStateOf(false) }
    var diagramExpanded by remember { mutableStateOf(false) }
    var sourceModeExpanded by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.facts_edit_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.facts_cancel))
                        }
                        Button(
                            onClick = {
                                val parsedTags = tagsText
                                    .split(",")
                                    .map(String::trim)
                                    .filter(String::isNotEmpty)
                                onSave(
                                    text,
                                    latex,
                                    parsedTags,
                                    templateType,
                                    diagramKind,
                                    diagramSource,
                                    sourceReferenceMode,
                                    sourcePageText.toIntOrNull(),
                                )
                            },
                        ) {
                            Text(stringResource(R.string.facts_save))
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.facts_edit_text_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
            }

            item {
                OutlinedTextField(
                    value = latex,
                    onValueChange = { latex = it },
                    label = { Text(stringResource(R.string.facts_edit_latex_label)) },
                    supportingText = { Text(stringResource(R.string.facts_edit_latex_helper)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
            }

            item {
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text(stringResource(R.string.facts_edit_tags_label)) },
                    supportingText = { Text(stringResource(R.string.facts_edit_tags_helper)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = sourcePageText,
                    onValueChange = { sourcePageText = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.facts_edit_source_page_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = templateExpanded,
                    onExpandedChange = { templateExpanded = it },
                ) {
                    OutlinedTextField(
                        value = templateLabel(templateType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.facts_edit_template_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = templateExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = templateExpanded,
                        onDismissRequest = { templateExpanded = false },
                    ) {
                        CardTemplateType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(templateLabel(type)) },
                                onClick = {
                                    templateType = type
                                    templateExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = diagramExpanded,
                    onExpandedChange = { diagramExpanded = it },
                ) {
                    OutlinedTextField(
                        value = diagramLabel(diagramKind),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.facts_edit_diagram_kind_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = diagramExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = diagramExpanded,
                        onDismissRequest = { diagramExpanded = false },
                    ) {
                        DiagramKind.entries.forEach { kind ->
                            DropdownMenuItem(
                                text = { Text(diagramLabel(kind)) },
                                onClick = {
                                    diagramKind = kind
                                    diagramExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (diagramKind != DiagramKind.NONE) {
                item {
                    OutlinedTextField(
                        value = diagramSource,
                        onValueChange = { diagramSource = it },
                        label = { Text(stringResource(R.string.facts_edit_diagram_source_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    )
                }
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = sourceModeExpanded,
                    onExpandedChange = { sourceModeExpanded = it },
                ) {
                    OutlinedTextField(
                        value = sourceModeLabel(sourceReferenceMode),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.facts_edit_source_mode_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceModeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = sourceModeExpanded,
                        onDismissRequest = { sourceModeExpanded = false },
                    ) {
                        listOf(
                            null to stringResource(R.string.facts_source_mode_default),
                            SourceReferenceMode.FULL to stringResource(R.string.facts_source_mode_full),
                            SourceReferenceMode.COMPACT to stringResource(R.string.facts_source_mode_compact),
                            SourceReferenceMode.PAGE_ONLY to stringResource(R.string.facts_source_mode_page_only),
                            SourceReferenceMode.HIDDEN to stringResource(R.string.facts_source_mode_hidden),
                        ).forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    sourceReferenceMode = mode
                                    sourceModeExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(spacing.xl))
            }
        }
    }
}

@Composable
private fun FactRegenerateDialog(
    fact: Fact,
    onDismiss: () -> Unit,
    onConfirm: (instruction: String?) -> Unit,
) {
    var instruction by remember { mutableStateOf("") }
    val spacing = LocalSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.facts_regenerate_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Text(
                    text = stringResource(R.string.facts_regenerate_current_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = fact.text,
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    label = { Text(stringResource(R.string.facts_regenerate_instruction_label)) },
                    supportingText = { Text(stringResource(R.string.facts_regenerate_instruction_helper)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { instruction = "Make shorter & punchier" },
                        label = { Text(stringResource(R.string.facts_regenerate_preset_concise)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { instruction = "Turn into Q&A" },
                        label = { Text(stringResource(R.string.facts_regenerate_preset_qa)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { instruction = "Emphasize formula" },
                        label = { Text(stringResource(R.string.facts_regenerate_preset_formula)) },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { instruction = "Explain intuitively" },
                        label = { Text(stringResource(R.string.facts_regenerate_preset_intuitive)) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(instruction.trim().ifBlank { null }) },
            ) {
                Text(stringResource(R.string.facts_regenerate_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.facts_cancel))
            }
        },
    )
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun statusLabel(status: FactStatusFilter): String = when (status) {
    FactStatusFilter.ALL -> stringResource(R.string.facts_status_all)
    FactStatusFilter.ACTIVE -> stringResource(R.string.facts_status_active)
    FactStatusFilter.IGNORED -> stringResource(R.string.facts_status_ignored)
    FactStatusFilter.FAVORITES -> stringResource(R.string.facts_status_favorites)
    FactStatusFilter.FORMULAS -> stringResource(R.string.facts_status_formulas)
    FactStatusFilter.DIAGRAMS -> stringResource(R.string.facts_status_diagrams)
    FactStatusFilter.DUE_FOR_REVIEW -> stringResource(R.string.facts_status_due_for_review)
}

@Composable
private fun templateLabel(type: CardTemplateType): String = when (type) {
    CardTemplateType.TEXT -> stringResource(R.string.facts_template_text)
    CardTemplateType.QUOTE -> stringResource(R.string.facts_template_quote)
    CardTemplateType.BULLET -> stringResource(R.string.facts_template_bullet)
    CardTemplateType.DEFINITION -> stringResource(R.string.facts_template_definition)
    CardTemplateType.QA -> stringResource(R.string.facts_template_qa)
    CardTemplateType.FORMULA -> stringResource(R.string.facts_template_formula)
    CardTemplateType.DIAGRAM -> stringResource(R.string.facts_template_diagram)
    CardTemplateType.TIMELINE -> stringResource(R.string.facts_template_timeline)
}

@Composable
private fun diagramLabel(kind: DiagramKind): String = when (kind) {
    DiagramKind.NONE -> stringResource(R.string.facts_diagram_none)
    DiagramKind.MERMAID -> stringResource(R.string.facts_diagram_mermaid)
    DiagramKind.TIKZ -> stringResource(R.string.facts_diagram_tikz)
    DiagramKind.SVG -> stringResource(R.string.facts_diagram_svg)
    DiagramKind.HTML_CANVAS -> stringResource(R.string.facts_diagram_html_canvas)
}

@Composable
private fun sourceModeLabel(mode: SourceReferenceMode?): String = when (mode) {
    null -> stringResource(R.string.facts_source_mode_default)
    SourceReferenceMode.FULL -> stringResource(R.string.facts_source_mode_full)
    SourceReferenceMode.COMPACT -> stringResource(R.string.facts_source_mode_compact)
    SourceReferenceMode.PAGE_ONLY -> stringResource(R.string.facts_source_mode_page_only)
    SourceReferenceMode.HIDDEN -> stringResource(R.string.facts_source_mode_hidden)
}

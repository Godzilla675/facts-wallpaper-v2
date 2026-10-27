package com.factswallpaper.feature.facts

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.FactListItemCard
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FactsScreen(
    onOpenLibrary: () -> Unit,
    onOpenTemplates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var editingFact by remember { mutableStateOf<Fact?>(null) }
    var regeneratingFact by remember { mutableStateOf<Fact?>(null) }
    var isStudyModeOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportFacts(uri, context)
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
                        IconButton(onClick = { isStudyModeOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.School,
                                contentDescription = stringResource(R.string.facts_study_mode),
                            )
                        }
                        IconButton(
                            onClick = {
                                exportLauncher.launch("facts_export_${System.currentTimeMillis()}.md")
                            },
                        ) {
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
                onCancelWork = viewModel::cancelGeneration,
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.padding(padding),
            )
        }
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
        val content = state as? FactsUiState.Content
        val isRegeneratingThis = content?.isRegenerating == true && content.regeneratingFactId == fact.id
        RegenerateFactSheet(
            fact = fact,
            isRegenerating = isRegeneratingThis,
            isOnline = content?.isOnline ?: true,
            onDismiss = { if (!isRegeneratingThis) regeneratingFact = null },
            onRegenerate = { instruction ->
                viewModel.regenerate(fact.id, instruction)
                regeneratingFact = null
            },
        )
    }

    if (isStudyModeOpen) {
        val content = state as? FactsUiState.Content
        if (content != null && content.facts.isNotEmpty()) {
            StudyModeSheet(
                facts = content.facts,
                onDismiss = { isStudyModeOpen = false },
                onRecordReview = viewModel::recordReview,
            )
        } else {
            isStudyModeOpen = false
        }
    }
}

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
    onCancelWork: () -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val filter = state.filter
    val documentTitle = state.documents.firstOrNull { it.id == filter.documentId }?.title

    Column(modifier = modifier.fillMaxSize()) {
        if (!state.isOnline) {
            InlineBanner(
                message = stringResource(R.string.facts_offline_banner),
                severity = BannerSeverity.WARNING,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }

        when (val work = state.generationWork) {
            is FactsWorkUi.Running -> {
                SectionGroupCard(
                    title = if (work.totalLessons > 0) {
                        stringResource(
                            R.string.facts_work_running,
                            work.completedLessons,
                            work.totalLessons,
                            work.factsCreated,
                        )
                    } else {
                        stringResource(R.string.facts_work_preparing)
                    },
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                ) {
                    if (work.totalLessons > 0) {
                        LinearProgressIndicator(
                            progress = { (work.completedLessons.toFloat() / work.totalLessons.toFloat()).coerceIn(0f, 1f) },
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
                            Text(stringResource(R.string.facts_work_cancel))
                        }
                    }
                }
            }
            is FactsWorkUi.Failed -> {
                InlineBanner(
                    message = work.message ?: stringResource(R.string.facts_work_failed),
                    severity = BannerSeverity.ERROR,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
            is FactsWorkUi.Succeeded -> {
                InlineBanner(
                    message = work.message ?: stringResource(R.string.facts_work_succeeded),
                    severity = BannerSeverity.INFO,
                    modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
                )
            }
            FactsWorkUi.Idle -> Unit
        }

        OutlinedTextField(
            value = filter.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.facts_search_placeholder)) },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.facts_clear_search),
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.sm),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = spacing.lg),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            items(FactStatusFilter.entries) { status ->
                FilterChip(
                    selected = filter.status == status,
                    onClick = { onStatusChange(status) },
                    label = { Text(statusLabel(status)) },
                )
            }
        }

        if (state.tags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(state.tags, key = { it.tag }) { tag ->
                    FilterChip(
                        selected = filter.tag.equals(tag.tag, ignoreCase = true),
                        onClick = {
                            onTagChange(
                                if (filter.tag.equals(tag.tag, ignoreCase = true)) null else tag.tag,
                            )
                        },
                        label = { Text(stringResource(R.string.facts_tag_chip, tag.tag, tag.factCount)) },
                    )
                }
            }
        }

        if (state.documents.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.lg, vertical = spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                item {
                    FilterChip(
                        selected = filter.documentId == null,
                        onClick = { onDocumentChange(null) },
                        label = { Text(stringResource(R.string.facts_all_documents)) },
                    )
                }
                items(state.documents, key = { it.id }) { document ->
                    FilterChip(
                        selected = filter.documentId == document.id,
                        onClick = { onDocumentChange(document.id) },
                        label = { Text(document.title) },
                    )
                }
            }
        }

        if (filter.lessonId != null) {
            InlineBanner(
                message = stringResource(R.string.facts_lesson_filter_active),
                severity = BannerSeverity.INFO,
                actionLabel = stringResource(R.string.facts_clear_lesson_filter),
                onAction = onClearLesson,
                modifier = Modifier.padding(horizontal = spacing.lg, vertical = spacing.xs),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg, vertical = spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (documentTitle != null) {
                    stringResource(R.string.facts_count_in_document, state.facts.size, documentTitle)
                } else {
                    stringResource(R.string.facts_count, state.facts.size)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (filter.hasActiveFilters) {
                TextButton(onClick = onClearFilters) {
                    Icon(Icons.Filled.FilterAlt, contentDescription = null)
                    Text(
                        text = stringResource(R.string.facts_clear_filters),
                        modifier = Modifier.padding(start = spacing.xs),
                    )
                }
            }
        }

        if (state.facts.isEmpty()) {
            val filtered = filter.hasActiveFilters
            EmptyStatePanel(
                icon = Icons.Filled.Lightbulb,
                title = stringResource(
                    if (filtered) R.string.facts_no_matches_title
                    else R.string.facts_empty_title,
                ),
                message = stringResource(
                    if (filtered) R.string.facts_no_matches_body
                    else R.string.facts_empty_body,
                ),
                actionLabel = if (filtered) {
                    stringResource(R.string.facts_clear_filters)
                } else {
                    stringResource(R.string.facts_open_library)
                },
                onAction = if (filtered) onClearFilters else onOpenLibrary,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
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
                        onToggleActive = { active -> onToggleActive(fact.id, active) },
                        onToggleFavorite = { favorite -> onToggleFavorite(fact.id, favorite) },
                        onEdit = { onEdit(fact) },
                        onRegenerate = { onRegenerate(fact) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudyModeSheet(
    facts: List<Fact>,
    onDismiss: () -> Unit,
    onRecordReview: (String, Boolean) -> Unit,
) {
    val spacing = LocalSpacing.current
    var currentIndex by remember { mutableIntStateOf(0) }
    var sessionGotIt by remember { mutableIntStateOf(0) }
    var sessionAgain by remember { mutableIntStateOf(0) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.facts_study_mode),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.facts_cancel))
                }
            }
            Spacer(modifier = Modifier.height(spacing.md))

            if (currentIndex in facts.indices) {
                val fact = facts[currentIndex]
                Text(
                    text = stringResource(
                        R.string.facts_study_card_counter,
                        currentIndex + 1,
                        facts.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Column(modifier = Modifier.padding(spacing.md)) {
                        fact.sourceLessonTitle?.let { lesson ->
                            Text(
                                text = lesson,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                        }
                        Text(
                            text = fact.text,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        val latex = fact.latex
                        if (!latex.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = latex,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                        if (fact.tags.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = fact.tags.joinToString(", "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(fact.id, false)
                            sessionAgain++
                            currentIndex++
                        },
                    ) {
                        Text(stringResource(R.string.facts_study_again, fact.reviewAgainCount))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onRecordReview(fact.id, true)
                            sessionGotIt++
                            currentIndex++
                        },
                    ) {
                        Text(stringResource(R.string.facts_study_got_it, fact.reviewGotItCount))
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.facts_study_done),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = "$sessionGotIt marked as got it \u00B7 $sessionAgain to review again",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(spacing.lg))
                Button(onClick = onDismiss) {
                    Text(stringResource(R.string.facts_cancel))
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
    var tags by remember(fact.id) { mutableStateOf(fact.tags.joinToString(", ")) }
    var template by remember(fact.id) { mutableStateOf(fact.templateType) }
    var diagramType by remember(fact.id) { mutableStateOf(fact.diagramType) }
    var diagramSource by remember(fact.id) { mutableStateOf(fact.diagramSource.orEmpty()) }
    var sourceMode by remember(fact.id) { mutableStateOf(fact.sourceReferenceModeOverride) }
    var sourcePageStr by remember(fact.id) { mutableStateOf(fact.sourcePage?.toString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Text(
                text = stringResource(R.string.facts_edit_title),
                style = MaterialTheme.typography.titleLarge,
            )
            SourceBadge(
                bookTitle = fact.sourceBookTitle,
                mode = sourceMode ?: SourceReferenceMode.COMPACT,
                lessonTitle = fact.sourceLessonTitle,
                page = sourcePageStr.toIntOrNull() ?: fact.sourcePage,
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.facts_edit_text_label)) },
                minLines = 3,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = latex,
                onValueChange = { latex = it },
                label = { Text(stringResource(R.string.facts_edit_latex_label)) },
                supportingText = { Text(stringResource(R.string.facts_edit_latex_helper)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text(stringResource(R.string.facts_edit_tags_label)) },
                supportingText = { Text(stringResource(R.string.facts_edit_tags_helper)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = sourcePageStr,
                onValueChange = { sourcePageStr = it.filter(Char::isDigit) },
                label = { Text(stringResource(R.string.facts_edit_source_page_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.facts_edit_template_label),
                style = MaterialTheme.typography.labelLarge,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                items(CardTemplateType.entries) { type ->
                    FilterChip(
                        selected = template == type,
                        onClick = { template = type },
                        label = { Text(templateLabel(type)) },
                    )
                }
            }
            Text(
                text = stringResource(R.string.facts_edit_diagram_kind_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                DiagramKind.entries.forEach { kind ->
                    FilterChip(
                        selected = diagramType == kind,
                        onClick = { diagramType = kind },
                        label = { Text(diagramLabel(kind)) },
                    )
                }
            }
            if (diagramType != DiagramKind.NONE) {
                OutlinedTextField(
                    value = diagramSource,
                    onValueChange = { diagramSource = it },
                    label = { Text(stringResource(R.string.facts_edit_diagram_source_label)) },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.facts_edit_source_mode_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                listOf(
                    null to stringResource(R.string.facts_source_mode_default),
                    SourceReferenceMode.FULL to stringResource(R.string.facts_source_mode_full),
                    SourceReferenceMode.COMPACT to stringResource(R.string.facts_source_mode_compact),
                    SourceReferenceMode.PAGE_ONLY to stringResource(R.string.facts_source_mode_page_only),
                    SourceReferenceMode.HIDDEN to stringResource(R.string.facts_source_mode_hidden),
                ).forEach { (mode, label) ->
                    FilterChip(
                        selected = sourceMode == mode,
                        onClick = { sourceMode = mode },
                        label = { Text(label) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.facts_cancel))
                }
                Button(
                    onClick = {
                        val parsedTags = tags.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                        onSave(
                            text,
                            latex,
                            parsedTags,
                            template,
                            diagramType,
                            diagramSource.ifBlank { null },
                            sourceMode,
                            sourcePageStr.toIntOrNull(),
                        )
                    },
                    enabled = text.isNotBlank(),
                ) {
                    Text(stringResource(R.string.facts_save))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RegenerateFactSheet(
    fact: Fact,
    isRegenerating: Boolean,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onRegenerate: (String?) -> Unit,
) {
    val spacing = LocalSpacing.current
    var instruction by remember(fact.id) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .padding(bottom = spacing.xl),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.facts_regenerate_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                if (isRegenerating) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = spacing.sm))
                }
            }

            Text(
                text = stringResource(R.string.facts_regenerate_current_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = fact.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = stringResource(R.string.facts_regenerate_instruction_label),
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                listOf(
                    stringResource(R.string.facts_regenerate_preset_concise),
                    stringResource(R.string.facts_regenerate_preset_qa),
                    stringResource(R.string.facts_regenerate_preset_formula),
                    stringResource(R.string.facts_regenerate_preset_intuitive),
                ).forEach { preset ->
                    SuggestionChip(
                        onClick = { instruction = preset },
                        label = { Text(preset) },
                    )
                }
            }

            OutlinedTextField(
                value = instruction,
                onValueChange = { instruction = it },
                label = { Text(stringResource(R.string.facts_regenerate_instruction_label)) },
                supportingText = { Text(stringResource(R.string.facts_regenerate_instruction_helper)) },
                minLines = 2,
                maxLines = 4,
                enabled = !isRegenerating,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss, enabled = !isRegenerating) {
                    Text(stringResource(R.string.facts_cancel))
                }
                Button(
                    onClick = { onRegenerate(instruction.trim().ifBlank { null }) },
                    enabled = isOnline && !isRegenerating,
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                    Text(stringResource(R.string.facts_regenerate_action))
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: FactStatusFilter): String = stringResource(
    when (status) {
        FactStatusFilter.ALL -> R.string.facts_status_all
        FactStatusFilter.ACTIVE -> R.string.facts_status_active
        FactStatusFilter.IGNORED -> R.string.facts_status_ignored
        FactStatusFilter.FAVORITES -> R.string.facts_status_favorites
        FactStatusFilter.FORMULAS -> R.string.facts_status_formulas
        FactStatusFilter.DIAGRAMS -> R.string.facts_status_diagrams
    },
)

@Composable
private fun templateLabel(type: CardTemplateType): String = stringResource(
    when (type) {
        CardTemplateType.TEXT -> R.string.facts_template_text
        CardTemplateType.QUOTE -> R.string.facts_template_quote
        CardTemplateType.BULLET -> R.string.facts_template_bullet
        CardTemplateType.DEFINITION -> R.string.facts_template_definition
        CardTemplateType.QA -> R.string.facts_template_qa
        CardTemplateType.FORMULA -> R.string.facts_template_formula
        CardTemplateType.DIAGRAM -> R.string.facts_template_diagram
        CardTemplateType.TIMELINE -> R.string.facts_template_timeline
    },
)

@Composable
private fun diagramLabel(kind: DiagramKind): String = stringResource(
    when (kind) {
        DiagramKind.NONE -> R.string.facts_diagram_none
        DiagramKind.MERMAID -> R.string.facts_diagram_mermaid
        DiagramKind.TIKZ -> R.string.facts_diagram_tikz
        DiagramKind.SVG -> R.string.facts_diagram_svg
        DiagramKind.HTML_CANVAS -> R.string.facts_diagram_html_canvas
    },
)

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

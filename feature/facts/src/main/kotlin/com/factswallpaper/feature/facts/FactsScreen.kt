package com.factswallpaper.feature.facts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.FactListItemCard
import com.factswallpaper.core.ui.component.InlineBanner
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
    var editingFact by remember { mutableStateOf<Fact?>(null) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.facts_title)) },
                actions = {
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
            is FactsUiState.Error -> CenteredMessage(
                text = stringResource(value.messageRes),
                modifier = Modifier.padding(padding),
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
                onRegenerate = { viewModel.regenerate(it) },
                onOpenLibrary = onOpenLibrary,
                modifier = Modifier.padding(padding),
            )
        }
    }

    editingFact?.let { fact ->
        FactEditorSheet(
            fact = fact,
            onDismiss = { editingFact = null },
            onSave = { text, latex, tags, template ->
                viewModel.saveFact(fact.id, text, latex, tags, template)
                editingFact = null
            },
        )
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
    onRegenerate: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val filter = state.filter
    val documentTitle = state.documents.firstOrNull { it.id == filter.documentId }?.title

    Column(modifier = modifier.fillMaxSize()) {
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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                        onRegenerate = { onRegenerate(fact.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactEditorSheet(
    fact: Fact,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, CardTemplateType) -> Unit,
) {
    val spacing = LocalSpacing.current
    var text by remember(fact.id) { mutableStateOf(fact.text) }
    var latex by remember(fact.id) { mutableStateOf(fact.latex.orEmpty()) }
    var tags by remember(fact.id) { mutableStateOf(fact.tags.joinToString(", ")) }
    var template by remember(fact.id) { mutableStateOf(fact.templateType) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                mode = fact.sourceReferenceModeOverride ?: SourceReferenceMode.COMPACT,
                lessonTitle = fact.sourceLessonTitle,
                page = fact.sourcePage,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.facts_cancel))
                }
                Button(
                    onClick = {
                        onSave(
                            text,
                            latex,
                            tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                            template,
                        )
                    },
                    enabled = text.isNotBlank(),
                    modifier = Modifier.padding(start = spacing.sm),
                ) {
                    Text(stringResource(R.string.facts_save))
                }
            }
        }
    }
}

@Composable
private fun CenteredLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
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
    }
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
    }
)

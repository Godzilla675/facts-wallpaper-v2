package com.factswallpaper.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.DocumentCard
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.MetricPill
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onImportPdf: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onOpenFacts: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val deletedMessage = stringResource(R.string.library_deleted)
    val undoLabel = stringResource(R.string.library_undo)
    val coroutineScope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<Document?>(null) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val contentState = state as? LibraryUiState.Content
    val inSelectionMode = contentState?.selectedDocumentIds?.isNotEmpty() == true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (inSelectionMode && contentState != null) {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(
                                R.string.library_selected_count,
                                contentState.selectedDocumentIds.size,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.library_clear_selection),
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                viewModel.selectAll(contentState.documents.map { it.id })
                            },
                        ) {
                            Text(stringResource(R.string.library_select_all))
                        }
                        IconButton(onClick = { showBatchDeleteConfirm = true }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.library_multi_delete_confirm),
                            )
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library_title)) },
                    actions = {
                        if (contentState != null && contentState.documents.isNotEmpty()) {
                            TextButton(
                                onClick = {
                                    viewModel.selectAll(contentState.documents.map { it.id })
                                },
                            ) {
                                Text(stringResource(R.string.library_select_all))
                            }
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onImportPdf,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_import)) },
            )
        },
    ) { padding ->
        when (val value = state) {
            LibraryUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is LibraryUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is LibraryUiState.Empty -> EmptyStatePanel(
                icon = Icons.Filled.LibraryBooks,
                title = stringResource(
                    if (value.query.isBlank()) {
                        R.string.library_empty_title
                    } else {
                        R.string.library_no_matches_title
                    },
                ),
                message = if (value.query.isBlank()) {
                    stringResource(R.string.library_empty_body)
                } else {
                    stringResource(R.string.library_no_matches_body, value.query)
                },
                actionLabel = stringResource(
                    if (value.query.isBlank()) R.string.library_import else R.string.library_clear_search,
                ),
                onAction = if (value.query.isBlank()) {
                    onImportPdf
                } else {
                    viewModel::clearQuery
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is LibraryUiState.Content -> LibraryContent(
                state = value,
                onQueryChange = viewModel::updateQuery,
                onClearQuery = viewModel::clearQuery,
                onSortChange = viewModel::setSortOrder,
                onToggleSelect = viewModel::toggleDocumentSelection,
                onOpenOutline = onOpenDocument,
                onOpenFacts = onOpenFacts,
                onToggleRotation = viewModel::setIncludedInRotation,
                onPauseOcr = viewModel::pauseOcr,
                onResumeOcr = viewModel::resumeOcr,
                onRetry = viewModel::retryProcessing,
                onDelete = { pendingDelete = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    pendingDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.library_delete_title)) },
            text = { Text(stringResource(R.string.library_delete_body, document.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val docToDelete = document
                        pendingDelete = null
                        viewModel.deleteDocumentWithUndo(docToDelete) { onUndo ->
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
                ) {
                    Text(stringResource(R.string.library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }

    if (showBatchDeleteConfirm && contentState != null) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = {
                Text(
                    stringResource(
                        R.string.library_multi_delete_title,
                        contentState.selectedDocumentIds.size,
                    ),
                )
            },
            text = { Text(stringResource(R.string.library_multi_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.batchDelete()
                        showBatchDeleteConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.library_multi_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.library_cancel))
                }
            },
        )
    }
}

@Composable
private fun LibraryContent(
    state: LibraryUiState.Content,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSortChange: (DocumentSortOrder) -> Unit,
    onToggleSelect: (String) -> Unit,
    onOpenOutline: (String) -> Unit,
    onOpenFacts: (String) -> Unit,
    onToggleRotation: (String, Boolean) -> Unit,
    onPauseOcr: (String) -> Unit,
    onResumeOcr: (String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.sm,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            PoolSummaryBanner(summary = state.poolSummary)
        }
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.library_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = onClearQuery) {
                            Icon(
                                imageVector = Icons.Filled.Clear,
                                contentDescription = stringResource(R.string.library_search_clear),
                            )
                        }
                    }
                },
                singleLine = true,
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.library_sort_by),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilterChip(
                    selected = state.sortOrder == DocumentSortOrder.DATE_DESC,
                    onClick = { onSortChange(DocumentSortOrder.DATE_DESC) },
                    label = { Text(stringResource(R.string.library_sort_recent)) },
                )
                FilterChip(
                    selected = state.sortOrder == DocumentSortOrder.TITLE_ASC,
                    onClick = { onSortChange(DocumentSortOrder.TITLE_ASC) },
                    label = { Text(stringResource(R.string.library_sort_name)) },
                )
                FilterChip(
                    selected = state.sortOrder == DocumentSortOrder.PROGRESS,
                    onClick = { onSortChange(DocumentSortOrder.PROGRESS) },
                    label = { Text(stringResource(R.string.library_sort_progress)) },
                )
            }
        }
        items(state.documents, key = { it.id }) { document ->
            val settings: DocumentSettings? = state.settings[document.id]
            val isSelected = document.id in state.selectedDocumentIds
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (state.selectedDocumentIds.isNotEmpty()) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelect(document.id) },
                        modifier = Modifier.padding(end = spacing.xs),
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    DocumentCard(
                        document = document,
                        pageRangeStart = settings?.pageRangeStart ?: 1,
                        pageRangeEnd = settings?.pageRangeEnd ?: document.pageCount,
                        onOpenOutline = { onOpenOutline(document.id) },
                        onOpenFacts = { onOpenFacts(document.id) },
                        onToggleRotation = { included -> onToggleRotation(document.id, included) },
                        onPauseOcr = { onPauseOcr(document.id) },
                        onResumeOcr = { onResumeOcr(document.id) },
                        onRetry = { onRetry(document.id) },
                        onDelete = { onDelete(document) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PoolSummaryBanner(summary: RotationPoolSummary) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = stringResource(R.string.library_pool_summary, summary.factCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MetricPill(
                    value = stringResource(
                        R.string.library_pool_sources,
                        summary.collectionCount,
                        summary.folderCount,
                    ),
                )
            }
        }
    }
}

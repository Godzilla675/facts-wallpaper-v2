package com.factswallpaper.feature.collections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.UserFolder
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    onOpenTag: (String) -> Unit,
    onOpenFacts: (FactStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val collectionDeletedMessage = stringResource(R.string.collections_collection_deleted)
    val folderDeletedMessage = stringResource(R.string.collections_folder_deleted)
    val undoLabel = stringResource(R.string.collections_undo)
    var creatingCollection by remember { mutableStateOf(false) }
    var creatingFolder by remember { mutableStateOf(false) }
    var renamingCollection by remember { mutableStateOf<Collection?>(null) }
    var renamingFolder by remember { mutableStateOf<UserFolder?>(null) }
    var deletingCollection by remember { mutableStateOf<Collection?>(null) }
    var deletingFolder by remember { mutableStateOf<UserFolder?>(null) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collections_title)) },
                actions = {
                    IconButton(onClick = { creatingCollection = true }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.collections_new_collection),
                        )
                    }
                    IconButton(onClick = { creatingFolder = true }) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = stringResource(R.string.collections_new_folder),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            CollectionsUiState.Loading -> CollectionsLoading(Modifier.padding(padding))
            is CollectionsUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is CollectionsUiState.Content -> CollectionsContent(
                state = value,
                onNewCollection = { creatingCollection = true },
                onNewFolder = { creatingFolder = true },
                onGenerateAiCollections = viewModel::generateAiCollections,
                onOpenTag = onOpenTag,
                onOpenFacts = onOpenFacts,
                onCollectionRotationChange = viewModel::setCollectionInRotation,
                onFolderRotationChange = viewModel::setFolderInRotation,
                onAcceptCollection = viewModel::acceptCollection,
                onDismissCollection = viewModel::dismissCollection,
                onRenameCollection = { renamingCollection = it },
                onRenameFolder = { renamingFolder = it },
                onManageCollectionFacts = { collection ->
                    viewModel.openMembership(
                        MembershipTargetType.COLLECTION,
                        collection.id,
                        collection.name,
                    )
                },
                onManageFolderFacts = { folder ->
                    viewModel.openMembership(
                        MembershipTargetType.FOLDER,
                        folder.id,
                        folder.name,
                    )
                },
                onDeleteCollection = { deletingCollection = it },
                onDeleteFolder = { deletingFolder = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (creatingCollection) {
        CreateDialog(
            title = stringResource(R.string.collections_new_collection),
            nameLabel = stringResource(R.string.collections_name_label),
            descriptionLabel = stringResource(R.string.collections_description_label),
            showDescription = true,
            onDismiss = { creatingCollection = false },
            onConfirm = { name, description ->
                viewModel.createCollection(name, description)
                creatingCollection = false
            },
        )
    }

    if (creatingFolder) {
        CreateDialog(
            title = stringResource(R.string.collections_new_folder),
            nameLabel = stringResource(R.string.collections_folder_name_label),
            descriptionLabel = null,
            showDescription = false,
            onDismiss = { creatingFolder = false },
            onConfirm = { name, _ ->
                viewModel.createFolder(name)
                creatingFolder = false
            },
        )
    }

    renamingCollection?.let { collection ->
        CreateDialog(
            title = stringResource(R.string.collections_rename_collection),
            nameLabel = stringResource(R.string.collections_name_label),
            descriptionLabel = null,
            showDescription = false,
            initialName = collection.name,
            onDismiss = { renamingCollection = null },
            onConfirm = { name, _ ->
                viewModel.renameCollection(collection, name)
                renamingCollection = null
            },
        )
    }

    renamingFolder?.let { folder ->
        CreateDialog(
            title = stringResource(R.string.collections_rename_folder),
            nameLabel = stringResource(R.string.collections_folder_name_label),
            descriptionLabel = null,
            showDescription = false,
            initialName = folder.name,
            onDismiss = { renamingFolder = null },
            onConfirm = { name, _ ->
                viewModel.renameFolder(folder, name)
                renamingFolder = null
            },
        )
    }

    deletingCollection?.let { collection ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.collections_delete_title),
            body = stringResource(R.string.collections_delete_body, collection.name),
            onDismiss = { deletingCollection = null },
            onConfirm = {
                viewModel.deleteCollectionWithUndo(collection.id) { onUndo ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = collectionDeletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndo()
                        }
                    }
                }
                deletingCollection = null
            },
        )
    }

    deletingFolder?.let { folder ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.collections_delete_title),
            body = stringResource(R.string.collections_delete_body, folder.name),
            onDismiss = { deletingFolder = null },
            onConfirm = {
                viewModel.deleteFolderWithUndo(folder.id) { onUndo ->
                    scope.launch {
                        val result = snackbarHostState.showSnackbar(
                            message = folderDeletedMessage,
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onUndo()
                        }
                    }
                }
                deletingFolder = null
            },
        )
    }

    (state as? CollectionsUiState.Content)?.membership?.let { editor ->
        MembershipDialog(
            state = editor,
            onToggle = viewModel::toggleMembershipFact,
            onDismiss = viewModel::closeMembership,
            onSave = viewModel::saveMembership,
        )
    }
}

@Composable
private fun CollectionsContent(
    state: CollectionsUiState.Content,
    onNewCollection: () -> Unit,
    onNewFolder: () -> Unit,
    onGenerateAiCollections: () -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenFacts: (FactStatusFilter) -> Unit,
    onCollectionRotationChange: (String, Boolean) -> Unit,
    onFolderRotationChange: (String, Boolean) -> Unit,
    onAcceptCollection: (String) -> Unit,
    onDismissCollection: (String) -> Unit,
    onRenameCollection: (Collection) -> Unit,
    onRenameFolder: (UserFolder) -> Unit,
    onManageCollectionFacts: (Collection) -> Unit,
    onManageFolderFacts: (UserFolder) -> Unit,
    onDeleteCollection: (Collection) -> Unit,
    onDeleteFolder: (UserFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = spacing.lg,
            end = spacing.lg,
            top = spacing.md,
            bottom = spacing.xl,
        ),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            SectionGroupCard(
                title = stringResource(R.string.collections_pool_title),
                subtitle = stringResource(
                    R.string.collections_pool_summary,
                    state.poolSummary.factCount,
                    state.poolSummary.collectionCount,
                    state.poolSummary.folderCount,
                ),
            ) {
                if (state.poolSummary.factCount == 0) {
                    InlineBanner(
                        message = stringResource(R.string.collections_pool_empty),
                        severity = BannerSeverity.WARNING,
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    item {
                        MetricPill(
                            value = stringResource(
                                R.string.collections_smart_total,
                                state.counts.total,
                            ),
                            label = stringResource(R.string.collections_smart_all_label),
                            onClick = { onOpenFacts(FactStatusFilter.ALL) },
                        )
                    }
                    item {
                        MetricPill(
                            value = stringResource(
                                R.string.collections_smart_count,
                                state.counts.active,
                            ),
                            label = stringResource(R.string.collections_smart_active),
                            onClick = { onOpenFacts(FactStatusFilter.ACTIVE) },
                        )
                    }
                    item {
                        MetricPill(
                            value = stringResource(
                                R.string.collections_smart_count,
                                state.counts.favorites,
                            ),
                            label = stringResource(R.string.collections_smart_favorites),
                            onClick = { onOpenFacts(FactStatusFilter.FAVORITES) },
                        )
                    }
                    item {
                        MetricPill(
                            value = stringResource(
                                R.string.collections_smart_count,
                                state.counts.formulas,
                            ),
                            label = stringResource(R.string.collections_smart_formulas),
                            onClick = { onOpenFacts(FactStatusFilter.FORMULAS) },
                        )
                    }
                    item {
                        MetricPill(
                            value = stringResource(
                                R.string.collections_smart_count,
                                state.counts.diagrams,
                            ),
                            label = stringResource(R.string.collections_smart_diagrams),
                            onClick = { onOpenFacts(FactStatusFilter.DIAGRAMS) },
                        )
                    }
                }
            }
        }

        if (state.suggestions.isNotEmpty()) {
            item {
                SectionGroupCard(
                    title = stringResource(R.string.collections_suggestions_title),
                    subtitle = stringResource(R.string.collections_suggestions_helper),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        state.suggestions.forEach { collection ->
                            SuggestionCard(
                                collection = collection,
                                onAccept = { onAcceptCollection(collection.id) },
                                onDismiss = { onDismissCollection(collection.id) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.collections_section_collections),
                actionLabel = stringResource(R.string.collections_new_collection),
                onAction = onNewCollection,
            )
        }
        if (state.activeCollections.isEmpty()) {
            item {
                EmptySection(
                    message = stringResource(R.string.collections_empty_collections),
                    actionLabel = stringResource(R.string.collections_new_collection),
                    onAction = onNewCollection,
                )
            }
        } else {
            items(state.activeCollections, key = { it.id }) { collection ->
                CollectionRow(
                    collection = collection,
                    onRotationChange = { included ->
                        onCollectionRotationChange(collection.id, included)
                    },
                    onRename = { onRenameCollection(collection) },
                    onManageFacts = { onManageCollectionFacts(collection) },
                    onDelete = { onDeleteCollection(collection) },
                )
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.collections_section_folders),
                actionLabel = stringResource(R.string.collections_new_folder),
                onAction = onNewFolder,
            )
        }
        if (state.folders.isEmpty()) {
            item {
                EmptySection(
                    message = stringResource(R.string.collections_empty_folders),
                    actionLabel = stringResource(R.string.collections_new_folder),
                    onAction = onNewFolder,
                )
            }
        } else {
            items(state.folders, key = { it.id }) { folder ->
                FolderRow(
                    folder = folder,
                    onRotationChange = { included ->
                        onFolderRotationChange(folder.id, included)
                    },
                    onRename = { onRenameFolder(folder) },
                    onManageFacts = { onManageFolderFacts(folder) },
                    onDelete = { onDeleteFolder(folder) },
                )
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.collections_tags_title),
                subtitle = stringResource(R.string.collections_tags_helper),
            ) {
                if (state.tags.isEmpty()) {
                    Text(
                        text = stringResource(R.string.collections_tags_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(state.tags, key = { it.tag }) { tag ->
                            OutlinedButton(onClick = { onOpenTag(tag.tag) }) {
                                Icon(Icons.Filled.Sell, contentDescription = null)
                                Text(
                                    text = stringResource(
                                        R.string.collections_tag_with_count,
                                        tag.tag,
                                        tag.factCount,
                                    ),
                                    modifier = Modifier.padding(start = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Text(
                            text = stringResource(R.string.collections_ai_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = spacing.sm),
                        )
                    }
                    Text(
                        text = stringResource(R.string.collections_ai_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onGenerateAiCollections) {
                        Text(stringResource(R.string.collections_ai_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionRow(
    collection: Collection,
    onRotationChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onManageFacts: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.collections_fact_count,
                            collection.factCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (collection.source == CollectionSource.AI_SUGGESTED) {
                    MetricPill(value = stringResource(R.string.collections_ai_badge))
                }
                IconButton(onClick = onRename) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(
                            R.string.collections_rename_description,
                            collection.name,
                        ),
                    )
                }
                IconButton(onClick = onManageFacts) {
                    Icon(
                        imageVector = Icons.Filled.Checklist,
                        contentDescription = stringResource(
                            R.string.collections_manage_facts_description,
                            collection.name,
                        ),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(
                            R.string.collections_delete_description,
                            collection.name,
                        ),
                    )
                }
            }
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = spacing.xs),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.collections_include_in_rotation),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.collections_collection_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = collection.includeInRotation,
                    onCheckedChange = onRotationChange,
                )
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: UserFolder,
    onRotationChange: (Boolean) -> Unit,
    onRename: () -> Unit,
    onManageFacts: () -> Unit,
    onDelete: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                start = spacing.lg,
                top = spacing.lg,
                bottom = spacing.md,
                end = spacing.sm,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Folder, contentDescription = null)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = spacing.sm),
            ) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.collections_fact_count, folder.factCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onRename) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(
                        R.string.collections_rename_description,
                        folder.name,
                    ),
                )
            }
            IconButton(onClick = onManageFacts) {
                Icon(
                    imageVector = Icons.Filled.Checklist,
                    contentDescription = stringResource(
                        R.string.collections_manage_facts_description,
                        folder.name,
                    ),
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(
                        R.string.collections_delete_description,
                        folder.name,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.collections_include_in_rotation),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.collections_folder_scope),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = folder.includeInRotation,
                onCheckedChange = onRotationChange,
            )
        }
    }
}

@Composable
private fun MembershipDialog(
    state: MembershipEditorState,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val spacing = LocalSpacing.current
    var query by remember { mutableStateOf("") }
    val filtered = state.facts.filter { fact ->
        query.isBlank() ||
            fact.text.contains(query, ignoreCase = true) ||
            fact.tags.any { it.contains(query, ignoreCase = true) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.collections_members_title, state.targetName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (state.loading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    TextFieldSmall(
                        value = query,
                        onValueChange = { query = it },
                        label = stringResource(R.string.collections_members_search),
                    )
                    Text(
                        text = stringResource(
                            R.string.collections_members_selected,
                            state.selectedFactIds.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (filtered.isEmpty()) {
                        Text(
                            text = stringResource(R.string.collections_members_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 340.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(filtered, key = { it.id }) { fact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .clickable { onToggle(fact.id) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = fact.id in state.selectedFactIds,
                                        onCheckedChange = { onToggle(fact.id) },
                                    )
                                    Text(
                                        text = fact.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = spacing.xs),
                                    )
                                }
                            }
                        }
                    }
                }
                state.errorRes?.let { errorRes ->
                    Text(
                        text = stringResource(errorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                enabled = !state.loading && !state.saving,
            ) {
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.collections_members_save))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Composable
private fun TextFieldSmall(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SuggestionCard(
    collection: Collection,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.titleSmall,
            )
            if (collection.description.isNotBlank()) {
                Text(
                    text = collection.description,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(
                    R.string.collections_suggestion_fact_count,
                    collection.factCount,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                Button(onClick = onAccept) {
                    Text(stringResource(R.string.collections_accept))
                }
                OutlinedButton(onClick = onDismiss) {
                    Text(stringResource(R.string.collections_dismiss))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text(
                text = actionLabel,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptySection(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null && onAction != null) {
                OutlinedButton(onClick = onAction) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = actionLabel,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateDialog(
    title: String,
    nameLabel: String,
    descriptionLabel: String?,
    showDescription: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
    initialName: String = "",
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var description by remember { mutableStateOf("") }
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(nameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showDescription && descriptionLabel != null) {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(descriptionLabel) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, description) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.collections_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(
    title: String,
    body: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.collections_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.collections_cancel))
            }
        },
    )
}

@Composable
private fun CollectionsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

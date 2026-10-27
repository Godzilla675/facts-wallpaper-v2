package com.factswallpaper.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.template.BackgroundStyle
import com.factswallpaper.core.render.template.TemplateAlignment
import com.factswallpaper.core.render.template.TemplateFontFamily
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.TemplatePreviewCard
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onPreviewTemplate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deletingTemplate by remember { mutableStateOf<CardTemplate?>(null) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val content = state as? TemplatesUiState.Content

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.templates_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openAiDialog) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.templates_generate_ai),
                        )
                    }
                    IconButton(onClick = viewModel::startCreate) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.templates_create),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            TemplatesUiState.Loading -> TemplatesLoading(Modifier.padding(padding))
            is TemplatesUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is TemplatesUiState.Content -> {
                if (value.templates.isEmpty()) {
                    Column(modifier = Modifier.padding(padding)) {
                        EmptyStatePanel(
                            icon = Icons.Filled.Wallpaper,
                            title = stringResource(R.string.templates_empty_title),
                            message = stringResource(R.string.templates_empty_body),
                            actionLabel = stringResource(R.string.templates_create),
                            onAction = viewModel::startCreate,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    TemplatesGrid(
                        state = value,
                        onPreviewTemplate = onPreviewTemplate,
                        onSetDefault = viewModel::setDefault,
                        onEdit = viewModel::startEdit,
                        onDelete = { deletingTemplate = it },
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }

    content?.editor?.let { editor ->
        TemplateEditorDialog(
            editor = editor,
            onDraftChange = { draft -> viewModel.updateDraft { draft } },
            onDismiss = viewModel::cancelEditor,
            onSave = viewModel::saveEditor,
        )
    }

    if (content?.ai?.visible == true) {
        AiTemplateDialog(
            state = content.ai,
            onPromptChange = viewModel::updateAiPrompt,
            onDismiss = viewModel::closeAiDialog,
            onGenerate = viewModel::generateTemplate,
        )
    }

    deletingTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { deletingTemplate = null },
            title = { Text(stringResource(R.string.templates_delete_title)) },
            text = { Text(stringResource(R.string.templates_delete_body, template.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustom(template.id)
                        deletingTemplate = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.templates_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTemplate = null }) {
                    Text(stringResource(R.string.templates_cancel))
                }
            },
        )
    }
}

@Composable
private fun TemplatesGrid(
    state: TemplatesUiState.Content,
    onPreviewTemplate: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onEdit: (CardTemplate) -> Unit,
    onDelete: (CardTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(bottom = spacing.sm)) {
                Text(
                    text = stringResource(R.string.templates_helper),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(state.templates, key = { it.id }) { template ->
            Column {
                TemplatePreviewCard(
                    template = template,
                    selected = template.isDefault,
                    onSelect = { onPreviewTemplate(template.id) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!template.isDefault) {
                        TextButton(
                            onClick = { onSetDefault(template.id) },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.templates_set_default),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.templates_default_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 6.dp),
                            maxLines = 1,
                        )
                    }
                    Row {
                        IconButton(onClick = { onEdit(template) }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.templates_edit),
                            )
                        }
                        if (!template.builtIn) {
                            IconButton(onClick = { onDelete(template) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.templates_delete),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun TemplateEditorDialog(
    editor: TemplateEditorState,
    onDraftChange: (TemplateDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    val draft = editor.draft
    val spacing = LocalSpacing.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                stringResource(
                                    if (editor.templateId == null) {
                                        R.string.templates_editor_new_title
                                    } else {
                                        R.string.templates_editor_edit_title
                                    },
                                ),
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.templates_cancel),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = onSave, enabled = !editor.saving) {
                                if (editor.saving) {
                                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                } else {
                                    Text(stringResource(R.string.templates_save))
                                }
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    editor.errorRes?.let { errorRes ->
                        Text(
                            text = stringResource(errorRes),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { value -> onDraftChange(draft.copy(name = value)) },
                        label = { Text(stringResource(R.string.templates_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    EditorSection(title = stringResource(R.string.templates_section_background)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            BackgroundStyle.entries.forEach { style ->
                                FilterChip(
                                    selected = draft.backgroundStyle == style,
                                    onClick = { onDraftChange(draft.copy(backgroundStyle = style)) },
                                    label = { Text(backgroundStyleLabel(style)) },
                                )
                            }
                        }
                    }

                    EditorSection(title = stringResource(R.string.templates_section_accent)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            AccentRole.all.forEach { accent ->
                                FilterChip(
                                    selected = draft.accentHexDark.equals(accent.darkHex, ignoreCase = true) &&
                                        draft.accentHexLight.equals(accent.lightHex, ignoreCase = true),
                                    onClick = {
                                        onDraftChange(
                                            draft.copy(
                                                accentHexLight = accent.lightHex,
                                                accentHexDark = accent.darkHex,
                                            ),
                                        )
                                    },
                                    label = { Text(stringResource(accent.labelRes)) },
                                )
                            }
                        }
                    }

                    EditorSection(title = stringResource(R.string.templates_section_heading_font)) {
                        FontFamilyChips(
                            selected = draft.titleFontFamily,
                            onSelect = { onDraftChange(draft.copy(titleFontFamily = it)) },
                        )
                    }

                    EditorSection(title = stringResource(R.string.templates_section_body_font)) {
                        FontFamilyChips(
                            selected = draft.bodyFontFamily,
                            onSelect = { onDraftChange(draft.copy(bodyFontFamily = it)) },
                        )
                    }

                    LabeledSlider(
                        label = stringResource(
                            R.string.templates_scale_value,
                            draft.bodyFontScale,
                        ),
                        value = draft.bodyFontScale,
                        valueRange = 0.8f..1.4f,
                        steps = 5,
                        onChange = { onDraftChange(draft.copy(bodyFontScale = (it * 100).roundToInt() / 100f)) },
                    )

                    EditorSection(title = stringResource(R.string.templates_section_alignment)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            TemplateAlignment.entries.forEach { alignment ->
                                FilterChip(
                                    selected = draft.textAlignment == alignment,
                                    onClick = { onDraftChange(draft.copy(textAlignment = alignment)) },
                                    label = { Text(alignmentLabel(alignment)) },
                                )
                            }
                        }
                    }

                    LabeledSlider(
                        label = stringResource(
                            R.string.templates_horizontal_padding_value,
                            draft.horizontalPaddingDp,
                        ),
                        value = draft.horizontalPaddingDp.toFloat(),
                        valueRange = 8f..96f,
                        steps = 10,
                        onChange = { onDraftChange(draft.copy(horizontalPaddingDp = it.roundToInt())) },
                    )
                    LabeledSlider(
                        label = stringResource(
                            R.string.templates_vertical_padding_value,
                            draft.verticalPaddingDp,
                        ),
                        value = draft.verticalPaddingDp.toFloat(),
                        valueRange = 8f..96f,
                        steps = 10,
                        onChange = { onDraftChange(draft.copy(verticalPaddingDp = it.roundToInt())) },
                    )
                    LabeledSlider(
                        label = stringResource(
                            R.string.templates_corner_value,
                            draft.cornerRadiusDp,
                        ),
                        value = draft.cornerRadiusDp.toFloat(),
                        valueRange = 0f..48f,
                        steps = 11,
                        onChange = { onDraftChange(draft.copy(cornerRadiusDp = it.roundToInt())) },
                    )
                    LabeledSlider(
                        label = stringResource(
                            R.string.templates_border_value,
                            draft.borderWidthDp,
                        ),
                        value = draft.borderWidthDp.toFloat(),
                        valueRange = 0f..8f,
                        steps = 7,
                        onChange = { onDraftChange(draft.copy(borderWidthDp = it.roundToInt())) },
                    )

                    EditorSection(title = stringResource(R.string.templates_section_footer)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            SourceReferenceMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = draft.defaultSourceRefMode == mode,
                                    onClick = { onDraftChange(draft.copy(defaultSourceRefMode = mode)) },
                                    label = { Text(sourceModeLabel(mode)) },
                                )
                            }
                        }
                    }

                    ToggleRow(
                        title = stringResource(R.string.templates_toggle_category),
                        checked = draft.showCategoryPill,
                        onChange = { onDraftChange(draft.copy(showCategoryPill = it)) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.templates_toggle_quote),
                        checked = draft.showQuoteMark,
                        onChange = { onDraftChange(draft.copy(showQuoteMark = it)) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.templates_toggle_split_header),
                        checked = draft.splitHeaderBand,
                        onChange = { onDraftChange(draft.copy(splitHeaderBand = it)) },
                    )
                    ToggleRow(
                        title = stringResource(R.string.templates_toggle_formula_frame),
                        checked = draft.formulaStageFramed,
                        onChange = { onDraftChange(draft.copy(formulaStageFramed = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontFamilyChips(
    selected: TemplateFontFamily,
    onSelect: (TemplateFontFamily) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TemplateFontFamily.entries.forEach { family ->
            FilterChip(
                selected = selected == family,
                onClick = { onSelect(family) },
                label = { Text(fontFamilyLabel(family)) },
            )
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onChange,
            valueRange = valueRange,
            steps = steps,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun AiTemplateDialog(
    state: AiTemplateUiState,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.templates_ai_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.templates_ai_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = state.prompt,
                    onValueChange = onPromptChange,
                    label = { Text(stringResource(R.string.templates_ai_prompt_label)) },
                    placeholder = { Text(stringResource(R.string.templates_ai_prompt_example)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
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
            TextButton(onClick = onGenerate, enabled = !state.generating) {
                if (state.generating) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                } else {
                    Text(stringResource(R.string.templates_ai_generate))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.generating) {
                Text(stringResource(R.string.templates_cancel))
            }
        },
    )
}

@Composable
private fun backgroundStyleLabel(style: BackgroundStyle): String = stringResource(
    when (style) {
        BackgroundStyle.SOLID -> R.string.templates_background_solid
        BackgroundStyle.SUBTLE_GRADIENT -> R.string.templates_background_gradient
        BackgroundStyle.AMOLED_PURE_BLACK -> R.string.templates_background_amoled
    },
)

@Composable
private fun fontFamilyLabel(family: TemplateFontFamily): String = stringResource(
    when (family) {
        TemplateFontFamily.SERIF -> R.string.templates_font_serif
        TemplateFontFamily.SANS -> R.string.templates_font_sans
        TemplateFontFamily.MONOSPACE -> R.string.templates_font_monospace
    },
)

@Composable
private fun alignmentLabel(alignment: TemplateAlignment): String = stringResource(
    when (alignment) {
        TemplateAlignment.START -> R.string.templates_alignment_start
        TemplateAlignment.CENTER -> R.string.templates_alignment_center
    },
)

@Composable
private fun sourceModeLabel(mode: SourceReferenceMode): String = stringResource(
    when (mode) {
        SourceReferenceMode.FULL -> R.string.preview_source_full
        SourceReferenceMode.COMPACT -> R.string.preview_source_compact
        SourceReferenceMode.PAGE_ONLY -> R.string.preview_source_page
        SourceReferenceMode.HIDDEN -> R.string.preview_source_hidden
    },
)

@Composable
private fun TemplatesLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

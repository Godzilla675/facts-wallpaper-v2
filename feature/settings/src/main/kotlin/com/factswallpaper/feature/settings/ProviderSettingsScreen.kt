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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.ProviderProfileCard
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.format.MetricsFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderSettingsScreen(
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProviderSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var editingProfile by remember { mutableStateOf<ProviderProfile?>(null) }
    var addingProfile by remember { mutableStateOf(false) }
    var deletingProfile by remember { mutableStateOf<ProviderProfile?>(null) }
    var overrideTask by remember { mutableStateOf<AiTask?>(null) }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.provider_title)) },
                actions = {
                    IconButton(onClick = onOpenAdvanced) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = stringResource(R.string.provider_advanced),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            ProviderSettingsUiState.Loading -> SettingsLoading(Modifier.padding(padding))
            is ProviderSettingsUiState.Error -> SettingsError(
                messageRes = value.messageRes,
                modifier = Modifier.padding(padding),
            )
            is ProviderSettingsUiState.Content -> ProviderContent(
                state = value,
                onRoutingChange = viewModel::setRoutingStrategy,
                onEditOverride = { overrideTask = it },
                onAddProfile = { addingProfile = true },
                onEditProfile = { editingProfile = it },
                onDeleteProfile = { deletingProfile = it },
                onSetDefault = viewModel::setDefaultProfile,
                onApiKeysNotYet = viewModel::apiKeysNotYet,
                onTestConnection = viewModel::testConnection,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (addingProfile || editingProfile != null) {
        ProfileEditorDialog(
            existing = editingProfile,
            onDismiss = {
                addingProfile = false
                editingProfile = null
            },
            onSave = { name, type, baseUrl, model, context ->
                viewModel.saveProfile(editingProfile, name, type, baseUrl, model, context)
                addingProfile = false
                editingProfile = null
            },
        )
    }

    deletingProfile?.let { profile ->
        AlertDialog(
            onDismissRequest = { deletingProfile = null },
            title = { Text(stringResource(R.string.provider_delete_title)) },
            text = { Text(stringResource(R.string.provider_delete_body, profile.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile)
                        deletingProfile = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.provider_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProfile = null }) {
                    Text(stringResource(R.string.provider_cancel))
                }
            },
        )
    }

    val content = state as? ProviderSettingsUiState.Content
    overrideTask?.let { task ->
        TaskOverrideDialog(
            task = task,
            profiles = content?.profiles.orEmpty(),
            selectedProfileId = content?.preferences?.taskProfileOverrides?.get(task),
            onDismiss = { overrideTask = null },
            onSelect = { profileId ->
                viewModel.setTaskOverride(task, profileId)
                overrideTask = null
            },
        )
    }
}

@Composable
private fun ProviderContent(
    state: ProviderSettingsUiState.Content,
    onRoutingChange: (RoutingStrategy) -> Unit,
    onEditOverride: (AiTask) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (ProviderProfile) -> Unit,
    onDeleteProfile: (ProviderProfile) -> Unit,
    onSetDefault: (String) -> Unit,
    onApiKeysNotYet: () -> Unit,
    onTestConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            InlineBanner(
                message = stringResource(R.string.provider_keys_banner),
                severity = BannerSeverity.INFO,
                actionLabel = stringResource(R.string.provider_keys_action),
                onAction = onApiKeysNotYet,
            )
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.provider_routing_title),
                subtitle = stringResource(R.string.provider_routing_helper),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    RoutingStrategy.entries.forEach { strategy ->
                        FilterChip(
                            selected = state.preferences.routingStrategy == strategy,
                            onClick = { onRoutingChange(strategy) },
                            label = { Text(routingLabel(strategy)) },
                        )
                    }
                }
            }
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.provider_overrides_title),
                subtitle = stringResource(R.string.provider_overrides_helper),
            ) {
                AiTask.entries.forEach { task ->
                    val profileName = state.preferences.taskProfileOverrides[task]?.let { id ->
                        state.profiles.firstOrNull { it.id == id }?.name
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditOverride(task) }
                            .padding(vertical = spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = taskLabel(task),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = profileName
                                    ?: stringResource(R.string.provider_override_automatic),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(
                                R.string.provider_edit_override,
                                taskLabel(task),
                            ),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.provider_usage_title),
                subtitle = stringResource(R.string.provider_usage_helper),
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.provider_profiles_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = onAddProfile) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(
                        text = stringResource(R.string.provider_add),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
        if (state.profiles.isEmpty()) {
            item {
                SectionGroupCard(
                    title = stringResource(R.string.provider_empty_title),
                    subtitle = stringResource(R.string.provider_empty_body),
                ) {
                    Button(onClick = onAddProfile) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.provider_add),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
            }
        } else {
            items(state.profiles, key = { it.id }) { profile ->
                Column {
                    ProviderProfileCard(
                        profile = profile,
                        hasApiKey = false,
                        onSetDefault = { onSetDefault(profile.id) },
                        onEdit = { onEditProfile(profile) },
                        onDelete = { onDeleteProfile(profile) },
                    )
                    if (profile.isDefault) {
                        TextButton(onClick = onTestConnection) {
                            Text(stringResource(R.string.provider_test_connection))
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onAddProfile,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.provider_add),
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorDialog(
    existing: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (String, ProviderType, String, String, Int) -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember(existing) { mutableStateOf(existing?.providerType ?: ProviderType.GEMINI) }
    var baseUrl by remember(existing) {
        mutableStateOf(existing?.baseUrl ?: defaultBaseUrl(ProviderType.GEMINI))
    }
    var model by remember(existing) { mutableStateOf(existing?.modelName.orEmpty()) }
    var context by remember(existing) { mutableStateOf((existing?.maxContextTokens ?: 128_000).toString()) }
    val spacing = LocalSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.provider_add_title
                    else R.string.provider_edit_title,
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.provider_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.provider_type_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    ProviderType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = {
                                val previousDefault = defaultBaseUrl(type)
                                type = option
                                if (baseUrl.isBlank() || baseUrl == previousDefault) {
                                    baseUrl = defaultBaseUrl(option)
                                }
                            },
                            label = { Text(providerTypeLabel(option)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.provider_base_url_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text(stringResource(R.string.provider_model_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = context,
                    onValueChange = { context = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.provider_context_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.provider_keys_dialog_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        name,
                        type,
                        baseUrl,
                        model,
                        context.toIntOrNull() ?: 128_000,
                    )
                },
                enabled = name.isNotBlank() && baseUrl.isNotBlank() && model.isNotBlank(),
            ) {
                Text(stringResource(R.string.provider_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.provider_cancel))
            }
        },
    )
}

@Composable
private fun TaskOverrideDialog(
    task: AiTask,
    profiles: List<ProviderProfile>,
    selectedProfileId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_override_title, taskLabel(task))) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(null) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedProfileId == null,
                        onClick = { onSelect(null) },
                    )
                    Text(stringResource(R.string.provider_override_automatic))
                }
                profiles.forEach { profile ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(profile.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedProfileId == profile.id,
                            onClick = { onSelect(profile.id) },
                        )
                        Column {
                            Text(profile.name)
                            Text(
                                text = profile.modelName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (profiles.isEmpty()) {
                    Text(
                        text = stringResource(R.string.provider_override_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.provider_cancel))
            }
        },
    )
}

@Composable
private fun routingLabel(strategy: RoutingStrategy): String = stringResource(
    when (strategy) {
        RoutingStrategy.CHEAPEST -> R.string.provider_routing_cheapest
        RoutingStrategy.BALANCED -> R.string.provider_routing_balanced
        RoutingStrategy.HIGHEST_QUALITY -> R.string.provider_routing_quality
        RoutingStrategy.MANUAL -> R.string.provider_routing_manual
    }
)

@Composable
private fun taskLabel(task: AiTask): String = stringResource(
    when (task) {
        AiTask.OUTLINE -> R.string.provider_task_outline
        AiTask.FACTS -> R.string.provider_task_facts
        AiTask.JSON_REPAIR -> R.string.provider_task_repair
        AiTask.FORMULA_VERIFICATION -> R.string.provider_task_formulas
        AiTask.DIAGRAM_GENERATION -> R.string.provider_task_diagrams
        AiTask.TEMPLATE_GENERATION -> R.string.provider_task_templates
    }
)

@Composable
private fun providerTypeLabel(type: ProviderType): String = stringResource(
    when (type) {
        ProviderType.GEMINI -> R.string.provider_type_gemini
        ProviderType.OPENAI -> R.string.provider_type_openai
        ProviderType.ANTHROPIC -> R.string.provider_type_anthropic
        ProviderType.AZURE_OPENAI -> R.string.provider_type_azure
        ProviderType.OPENROUTER -> R.string.provider_type_openrouter
        ProviderType.DEEPSEEK -> R.string.provider_type_deepseek
        ProviderType.QWEN -> R.string.provider_type_qwen
        ProviderType.OPENAI_COMPATIBLE -> R.string.provider_type_compatible
    }
)

private fun defaultBaseUrl(type: ProviderType): String = when (type) {
    ProviderType.GEMINI -> "https://generativelanguage.googleapis.com"
    ProviderType.OPENAI -> "https://api.openai.com/v1"
    ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1"
    ProviderType.AZURE_OPENAI -> "https://your-resource.openai.azure.com"
    ProviderType.OPENROUTER -> "https://openrouter.ai/api/v1"
    ProviderType.DEEPSEEK -> "https://api.deepseek.com"
    ProviderType.QWEN -> "https://dashscope.aliyuncs.com/compatible-mode/v1"
    ProviderType.OPENAI_COMPATIBLE -> "https://example.com/v1"
}

@Composable
private fun SettingsLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SettingsError(messageRes: Int, modifier: Modifier = Modifier) {
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

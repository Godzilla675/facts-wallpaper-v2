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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.KeyOff
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.ai.AiModelInfo
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.ProviderProfileCard
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.format.MetricsFormatter
import kotlinx.coroutines.launch

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
    var editingKeyProfile by remember { mutableStateOf<ProviderProfile?>(null) }
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
            is ProviderSettingsUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is ProviderSettingsUiState.Content -> ProviderContent(
                state = value,
                onRoutingChange = viewModel::setRoutingStrategy,
                onEditOverride = { overrideTask = it },
                onAddProfile = { addingProfile = true },
                onEditProfile = { editingProfile = it },
                onDeleteProfile = { deletingProfile = it },
                onEditKey = { editingKeyProfile = it },
                onSetDefault = viewModel::setDefaultProfile,
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
            onSave = { name, type, baseUrl, model, context, outputTokens, apiKey ->
                viewModel.saveProfile(
                    existing = editingProfile,
                    name = name,
                    providerType = type,
                    baseUrl = baseUrl,
                    modelName = model,
                    maxContextTokens = context,
                    maxOutputTokens = outputTokens,
                    apiKey = apiKey,
                )
                addingProfile = false
                editingProfile = null
            },
        )
    }

    editingKeyProfile?.let { profile ->
        val content = state as? ProviderSettingsUiState.Content
        val hasKey = content?.configuredKeyProfileIds?.contains(profile.id) == true
        ApiKeyDialog(
            profile = profile,
            hasKey = hasKey,
            onDismiss = { editingKeyProfile = null },
            onSave = { apiKey ->
                viewModel.saveApiKey(profile.id, apiKey)
                editingKeyProfile = null
            },
            onClear = {
                viewModel.deleteApiKey(profile.id)
                editingKeyProfile = null
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProviderContent(
    state: ProviderSettingsUiState.Content,
    onRoutingChange: (RoutingStrategy) -> Unit,
    onEditOverride: (AiTask) -> Unit,
    onAddProfile: () -> Unit,
    onEditProfile: (ProviderProfile) -> Unit,
    onDeleteProfile: (ProviderProfile) -> Unit,
    onEditKey: (ProviderProfile) -> Unit,
    onSetDefault: (String) -> Unit,
    onTestConnection: (ProviderProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val hasMissingKeys = state.profiles.isNotEmpty() &&
        state.profiles.any { !state.configuredKeyProfileIds.contains(it.id) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        if (hasMissingKeys) {
            item {
                InlineBanner(
                    message = stringResource(R.string.provider_keys_banner),
                    severity = BannerSeverity.WARNING,
                    actionLabel = stringResource(R.string.provider_keys_action),
                    onAction = {
                        state.profiles.firstOrNull { !state.configuredKeyProfileIds.contains(it.id) }
                            ?.let { onEditKey(it) }
                    },
                )
            }
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
                            .heightIn(min = 48.dp)
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
                val hasKey = state.configuredKeyProfileIds.contains(profile.id)
                Column {
                    ProviderProfileCard(
                        profile = profile,
                        hasApiKey = hasKey,
                        onSetDefault = { onSetDefault(profile.id) },
                        onEdit = { onEditProfile(profile) },
                        onDelete = { onDeleteProfile(profile) },
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.xs, vertical = spacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onEditKey(profile) }) {
                            Icon(
                                imageVector = if (hasKey) Icons.Filled.Key else Icons.Filled.KeyOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                stringResource(
                                    if (hasKey) R.string.provider_update_key else R.string.provider_set_key
                                )
                            )
                        }
                        if (state.testingProfileId == profile.id) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            TextButton(
                                onClick = { onTestConnection(profile) },
                                enabled = hasKey,
                            ) {
                                Text(stringResource(R.string.provider_test_connection))
                            }
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

@Composable
private fun ApiKeyDialog(
    profile: ProviderProfile,
    hasKey: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.provider_key_dialog_title, profile.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.provider_key_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.provider_api_key_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(key) },
                enabled = key.isNotBlank(),
            ) {
                Text(stringResource(R.string.provider_save))
            }
        },
        dismissButton = {
            Row {
                if (hasKey) {
                    TextButton(onClick = onClear) {
                        Text(
                            text = stringResource(R.string.provider_clear_key),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.provider_cancel))
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditorDialog(
    existing: ProviderProfile?,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        type: ProviderType,
        baseUrl: String,
        model: String,
        context: Int,
        outputTokens: Int,
        apiKey: String?,
    ) -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var type by remember { mutableStateOf(existing?.providerType ?: ProviderType.OPENAI) }
    var baseUrl by remember {
        mutableStateOf(existing?.baseUrl ?: defaultBaseUrl(ProviderType.OPENAI))
    }
    var model by remember { mutableStateOf(existing?.modelName.orEmpty()) }
    var context by remember {
        mutableStateOf(existing?.maxContextTokens?.toString() ?: "128000")
    }
    var maxOutputTokens by remember {
        mutableStateOf(existing?.maxOutputTokens?.toString() ?: "8192")
    }
    var apiKey by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (existing == null) R.string.provider_add_title else R.string.provider_edit_title,
                ),
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                    style = MaterialTheme.typography.labelMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                OutlinedTextField(
                    value = maxOutputTokens,
                    onValueChange = { maxOutputTokens = it.filter(Char::isDigit) },
                    label = { Text(stringResource(R.string.provider_output_tokens_label)) },
                    supportingText = {
                        Text(stringResource(R.string.provider_output_tokens_helper))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            stringResource(
                                if (existing == null) {
                                    R.string.provider_api_key_optional
                                } else {
                                    R.string.provider_new_api_key_optional
                                },
                            ),
                        )
                    },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.provider_key_dialog_body),
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
                        maxOutputTokens.toIntOrNull() ?: 8_192,
                        apiKey.takeIf { it.isNotBlank() },
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
                        .heightIn(min = 48.dp)
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
                            .heightIn(min = 48.dp)
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

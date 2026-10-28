package com.factswallpaper.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.ErrorStatePanel
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.PhoneWallpaperMockup
import com.factswallpaper.core.ui.component.StepProgressHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onOpenPdfImport: () -> Unit,
    onLaunchLiveWallpaper: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val finished = (state as? OnboardingUiState.Content)?.finished == true
    LaunchedEffect(finished) {
        if (finished) onFinished()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.onboarding_title)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            OnboardingUiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            is OnboardingUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )

            is OnboardingUiState.Content -> OnboardingContent(
                state = value,
                onBack = viewModel::previousStep,
                onNext = viewModel::nextStep,
                onSelectPreset = viewModel::selectPreset,
                onApiKeyChange = viewModel::updateApiKey,
                onToggleKeyVisibility = viewModel::toggleApiKeyVisibility,
                onSaveKey = viewModel::saveApiKey,
                onTestConnection = viewModel::testConnection,
                onOpenImport = onOpenPdfImport,
                onSelectTheme = viewModel::selectTheme,
                onSelectTemplate = viewModel::selectTemplate,
                onLaunchWallpaper = onLaunchLiveWallpaper,
                onFinish = viewModel::finish,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingContent(
    state: OnboardingUiState.Content,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSelectPreset: (ProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onTestConnection: () -> Unit,
    onOpenImport: () -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectTemplate: (String) -> Unit,
    onLaunchWallpaper: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val title = stepTitle(state.step)
    Column(modifier = modifier.fillMaxSize()) {
        StepProgressHeader(
            step = state.step,
            totalSteps = OnboardingViewModel.LAST_STEP,
            title = title,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.lg)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            when (state.step) {
                1 -> StepOne()
                2 -> StepTwo(
                    state = state,
                    onSelectPreset = onSelectPreset,
                    onApiKeyChange = onApiKeyChange,
                    onToggleKeyVisibility = onToggleKeyVisibility,
                    onSaveKey = onSaveKey,
                    onTestConnection = onTestConnection,
                )
                3 -> StepThree(onOpenImport = onOpenImport, onNext = onNext)
                4 -> StepFour(
                    state = state,
                    onSelectTheme = onSelectTheme,
                    onSelectTemplate = onSelectTemplate,
                )
                5 -> StepFive(
                    onLaunchWallpaper = onLaunchWallpaper,
                    onFinish = onFinish,
                    isSaving = state.isSaving,
                )
            }
            Spacer(modifier = Modifier.height(spacing.sm))
        }
        if (state.step != 3 && state.step != 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                if (state.step > 1) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                }
                Button(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_next))
                }
            }
        }
        if (state.step == 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_back))
                }
                TextButton(onClick = onNext, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_skip_import))
                }
            }
        }
        if (state.step == 5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_back))
                }
            }
        }
    }
}

@Composable
private fun StepOne() {
    val spacing = LocalSpacing.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PhoneWallpaperMockup(
            fact = null,
            templateName = stringResource(R.string.onboarding_preview_template),
            themeMode = ThemeMode.AMOLED,
            sourceReferenceMode = SourceReferenceMode.COMPACT,
            modifier = Modifier
                .widthIn(max = 220.dp)
                .width(190.dp),
        )
        Spacer(modifier = Modifier.height(spacing.lg))
        Text(
            text = stringResource(R.string.onboarding_step1_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = stringResource(R.string.onboarding_step1_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(spacing.md))
        PointRow(Icons.Filled.CloudOff, stringResource(R.string.onboarding_step1_point_offline))
        PointRow(Icons.Filled.Description, stringResource(R.string.onboarding_step1_point_source))
        PointRow(Icons.Filled.Lock, stringResource(R.string.onboarding_step1_point_private))
    }
}

@Composable
private fun PointRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepTwo(
    state: OnboardingUiState.Content,
    onSelectPreset: (ProviderType) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onToggleKeyVisibility: () -> Unit,
    onSaveKey: () -> Unit,
    onTestConnection: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_step2_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_step2_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ProviderType.entries.filter { preset -> preset in PRESET_TYPES }.forEach { preset ->
                FilterChip(
                    selected = state.selectedPreset == preset,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(presetLabel(preset)) },
                )
            }
        }
        OutlinedTextField(
            value = state.apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.onboarding_api_key_label)) },
            placeholder = { Text(stringResource(R.string.onboarding_api_key_placeholder)) },
            singleLine = true,
            visualTransformation = if (state.apiKeyVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = onToggleKeyVisibility) {
                    Icon(
                        imageVector = if (state.apiKeyVisible) {
                            Icons.Filled.VisibilityOff
                        } else {
                            Icons.Filled.Visibility
                        },
                        contentDescription = stringResource(
                            if (state.apiKeyVisible) {
                                R.string.onboarding_api_key_hide
                            } else {
                                R.string.onboarding_api_key_show
                            },
                        ),
                    )
                }
            },
            supportingText = {
                Text(stringResource(R.string.onboarding_api_key_helper))
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedButton(onClick = onSaveKey) {
                Text(stringResource(R.string.onboarding_save_key))
            }
            TextButton(onClick = onTestConnection) {
                Icon(Icons.Filled.Verified, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.onboarding_test_connection))
            }
        }
        InlineBanner(
            message = stringResource(R.string.onboarding_skip_provider),
            severity = BannerSeverity.INFO,
        )
    }
}

@Composable
private fun StepThree(onOpenImport: () -> Unit, onNext: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_step3_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_step3_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Card {
            Column(modifier = Modifier.padding(spacing.lg)) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Button(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_open_import))
                }
                OutlinedButton(onClick = onOpenImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_open_import_url))
                }
                Text(
                    text = stringResource(R.string.onboarding_open_import_url_helper),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_skip_import))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepFour(
    state: OnboardingUiState.Content,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectTemplate: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_step4_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_step4_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.onboarding_theme_label),
            style = MaterialTheme.typography.titleSmall,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.themeMode == mode,
                    onClick = { onSelectTheme(mode) },
                    label = { Text(themeLabel(mode)) },
                )
            }
        }
        Text(
            text = stringResource(R.string.onboarding_template_label),
            style = MaterialTheme.typography.titleSmall,
        )
        if (state.templates.isEmpty()) {
            InlineBanner(
                message = stringResource(R.string.onboarding_template_empty),
                severity = BannerSeverity.INFO,
            )
        } else {
            state.templates.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = state.selectedTemplateId == template.id,
                        onClick = { onSelectTemplate(template.id) },
                    )
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
            }
        }
    }
}

@Composable
private fun StepFive(
    onLaunchWallpaper: () -> Unit,
    onFinish: () -> Unit,
    isSaving: Boolean,
) {
    val spacing = LocalSpacing.current
    Column(verticalArrangement = Arrangement.spacedBy(spacing.md)) {
        Text(
            text = stringResource(R.string.onboarding_step5_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.onboarding_step5_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLaunchWallpaper, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_launch_wallpaper))
        }
        Button(
            onClick = onFinish,
            enabled = !isSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_finish))
        }
        Text(
            text = stringResource(R.string.onboarding_finish_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun stepTitle(step: Int): String = stringResource(
    when (step) {
        2 -> R.string.onboarding_step2_title
        3 -> R.string.onboarding_step3_title
        4 -> R.string.onboarding_step4_title
        5 -> R.string.onboarding_step5_title
        else -> R.string.onboarding_step1_title
    }
)

@Composable
private fun presetLabel(type: ProviderType): String = stringResource(
    when (type) {
        ProviderType.GEMINI -> R.string.onboarding_preset_gemini
        ProviderType.OPENAI -> R.string.onboarding_preset_openai
        ProviderType.ANTHROPIC -> R.string.onboarding_preset_anthropic
        else -> R.string.onboarding_preset_openai
    }
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.onboarding_theme_system
        ThemeMode.LIGHT -> R.string.onboarding_theme_light
        ThemeMode.DARK -> R.string.onboarding_theme_dark
        ThemeMode.AMOLED -> R.string.onboarding_theme_amoled
        ThemeMode.DYNAMIC -> R.string.onboarding_theme_dynamic
    }
)

private val PRESET_TYPES = setOf(
    ProviderType.GEMINI,
    ProviderType.OPENAI,
    ProviderType.ANTHROPIC,
    ProviderType.AZURE_OPENAI,
    ProviderType.OPENROUTER,
    ProviderType.DEEPSEEK,
    ProviderType.QWEN,
    ProviderType.OPENAI_COMPATIBLE,
)

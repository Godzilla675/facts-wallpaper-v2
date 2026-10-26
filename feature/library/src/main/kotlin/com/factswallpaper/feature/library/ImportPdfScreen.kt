package com.factswallpaper.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.OcrExecutionMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SettingSwitchRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPdfScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportPdfViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.onLocalPdfSelected(uri.toString(), queryDisplayName(context, uri))
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.import_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.import_choose_local),
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_choose_helper),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!state.sourceUri.isNullOrBlank()) {
                            Text(
                                text = stringResource(
                                    R.string.import_selected_file,
                                    state.sourceName ?: state.sourceUri.orEmpty(),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(onClick = { picker.launch(arrayOf("application/pdf")) }) {
                            Icon(Icons.Filled.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.import_choose_local))
                        }
                    }
                }
            }
            item {
                SectionGroupCard(title = stringResource(R.string.import_title)) {
                    OutlinedTextField(
                        value = state.url,
                        onValueChange = viewModel::updateUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.import_url_label)) },
                        placeholder = { Text(stringResource(R.string.import_url_placeholder)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::download) {
                        Text(stringResource(R.string.import_download))
                    }
                }
            }
            item {
                SectionGroupCard(
                    title = stringResource(R.string.import_options_title),
                    subtitle = stringResource(R.string.import_analyze_helper),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.pageStart,
                            onValueChange = viewModel::updatePageStart,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.import_page_start)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = state.pageEnd,
                            onValueChange = viewModel::updatePageEnd,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.import_page_end)) },
                            singleLine = true,
                            isError = state.hasPageRangeError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    if (state.hasPageRangeError) {
                        Text(
                            text = stringResource(R.string.import_page_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    SettingSwitchRow(
                        title = stringResource(R.string.import_prefer_direct),
                        subtitle = stringResource(R.string.import_prefer_direct_helper),
                        checked = state.preferDirectPdf,
                        onCheckedChange = viewModel::setPreferDirectPdf,
                    )
                    SettingSwitchRow(
                        title = stringResource(R.string.import_override_ocr),
                        subtitle = stringResource(R.string.import_override_ocr_helper),
                        checked = state.overrideOcrLimit,
                        onCheckedChange = viewModel::setOverrideOcrLimit,
                    )
                    OcrEngineSelector(
                        selected = state.ocrEngine,
                        onSelected = viewModel::setOcrEngine,
                    )
                    ExecutionModeSelector(
                        selected = state.executionMode,
                        onSelected = viewModel::setExecutionMode,
                    )
                }
            }
            item {
                SectionGroupCard(title = stringResource(R.string.import_estimate_title)) {
                    if (state.sourceUri.isNullOrBlank()) {
                        Text(
                            text = stringResource(R.string.import_estimate_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricPill(value = state.ocrEngine.name.replace('_', ' '))
                            MetricPill(value = state.executionMode.name)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.sourceName ?: state.sourceUri.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                InlineBanner(
                    message = stringResource(R.string.import_analyze_helper),
                    severity = BannerSeverity.INFO,
                )
            }
            item {
                Button(
                    onClick = viewModel::analyze,
                    enabled = state.hasSource,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.import_analyze))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OcrEngineSelector(
    selected: OcrEngineType,
    onSelected: (OcrEngineType) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.import_ocr_engine),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcrEngineType.entries.forEach { engine ->
                FilterChip(
                    selected = selected == engine,
                    onClick = { onSelected(engine) },
                    label = {
                        Text(
                            stringResource(
                                when (engine) {
                                    OcrEngineType.ML_KIT -> R.string.import_ocr_mlkit
                                    OcrEngineType.TESSERACT -> R.string.import_ocr_tesseract
                                    OcrEngineType.PADDLE_PPOCR -> R.string.import_ocr_paddle
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExecutionModeSelector(
    selected: OcrExecutionMode,
    onSelected: (OcrExecutionMode) -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.import_execution_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OcrExecutionMode.entries.forEach { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelected(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    OcrExecutionMode.FOREGROUND -> R.string.import_execution_foreground
                                    OcrExecutionMode.BACKGROUND -> R.string.import_execution_background
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
    return context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
    }
}

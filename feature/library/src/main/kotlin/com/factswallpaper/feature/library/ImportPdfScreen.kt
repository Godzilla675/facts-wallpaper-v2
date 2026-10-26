package com.factswallpaper.feature.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.ocr.OcrLanguageCatalog
import com.factswallpaper.core.ocr.OcrModelState
import com.factswallpaper.core.ocr.OcrModelStatus
import com.factswallpaper.core.pdf.DirectPdfBlockReason
import com.factswallpaper.core.pdf.DuplicatePolicy
import com.factswallpaper.core.pdf.PdfCostEstimate
import com.factswallpaper.core.pdf.PdfImportErrorCode
import com.factswallpaper.core.pdf.PdfPageRange
import com.factswallpaper.core.pdf.PdfRouteDecision
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SettingSwitchRow
import kotlin.math.roundToInt

/**
 * P2 import flow: multi-source staging, local inspection, duplicate resolution,
 * page-range filtering, OCR language packs, route/cost preview and live
 * processing progress for the committed document.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPdfScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
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
            viewModel.selectLocalPdf(uri.toString(), queryDisplayName(context, uri))
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(state.openDocumentId) {
        val documentId = state.openDocumentId
        if (documentId != null) {
            viewModel.clearOpenDocumentNavigation()
            onOpenDocument(documentId)
        }
    }

    state.duplicatePrompt?.let { prompt ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDuplicate,
            title = { Text(stringResource(R.string.import_duplicate_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.import_duplicate_body, prompt.existingTitle))
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.resolveDuplicate(DuplicatePolicy.OPEN_EXISTING) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_duplicate_open))
                    }
                    TextButton(
                        onClick = { viewModel.resolveDuplicate(DuplicatePolicy.REPLACE_EXISTING) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_duplicate_replace))
                    }
                    TextButton(
                        onClick = { viewModel.resolveDuplicate(DuplicatePolicy.IMPORT_AS_COPY) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_duplicate_copy))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDuplicate) {
                    Text(stringResource(R.string.import_cancel))
                }
            },
        )
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
            state.failure?.let { failure ->
                item {
                    ImportFailureBanner(
                        failure = failure,
                        onDismiss = viewModel::clearFailure,
                    )
                }
            }

            if (state.hasCommittedSession) {
                item {
                    ImportSessionCard(
                        state = state,
                        onPause = viewModel::pauseProcessing,
                        onResume = viewModel::resumeProcessing,
                        onRetry = viewModel::retryProcessing,
                    )
                }
                item {
                    Button(
                        onClick = viewModel::openCommittedDocument,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_open_document))
                    }
                }
                item {
                    OutlinedButton(
                        onClick = viewModel::startAnotherImport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.import_another))
                    }
                }
            } else {
                item {
                    SourceCard(
                        state = state,
                        onChooseLocal = { picker.launch(arrayOf("application/pdf")) },
                        onUrlChange = viewModel::updateUrl,
                        onDownload = viewModel::downloadUrl,
                    )
                }

                if (state.isWorking) {
                    item {
                        WorkingCard(state = state)
                    }
                }

                if (state.staged != null) {
                    item {
                        InspectionCard(state = state)
                    }
                    item {
                        ProcessingOptionsCard(
                            state = state,
                            onPageStartChange = viewModel::updatePageStart,
                            onPageEndChange = viewModel::updatePageEnd,
                            onForceOcrChange = viewModel::setForceLocalOcr,
                            onDirectChange = viewModel::setPreferDirectPdf,
                            onOverrideChange = viewModel::setOverrideOcrLimit,
                            onLanguageSelected = viewModel::setLanguage,
                            onDownloadLanguage = viewModel::downloadLanguage,
                        )
                    }
                    if (state.decision != null && state.cost != null && state.pageRange != null) {
                        item {
                            ProcessingPlanCard(state = state)
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                if (state.decision?.usesOcr == true &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(
                                        Manifest.permission.POST_NOTIFICATIONS,
                                    )
                                }
                                viewModel.startImport()
                            },
                            enabled = state.canStart,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (state.isCommitting) R.string.import_starting else R.string.import_start,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    state: ImportPdfUiState,
    onChooseLocal: () -> Unit,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
) {
    val spacing = LocalSpacing.current
    SectionGroupCard(
        title = stringResource(R.string.import_source_title),
        subtitle = stringResource(R.string.import_choose_helper),
    ) {
        Button(onClick = onChooseLocal, enabled = !state.isWorking) {
            Icon(Icons.Filled.UploadFile, contentDescription = null)
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(stringResource(R.string.import_choose_local))
        }
        state.staged?.displayName?.takeIf { it.isNotBlank() }?.let { name ->
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(R.string.import_selected_file, name),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(spacing.md))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(
                text = stringResource(R.string.import_or_url),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(spacing.sm))
        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.import_url_label)) },
            placeholder = { Text(stringResource(R.string.import_url_placeholder)) },
            singleLine = true,
            enabled = !state.isWorking,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        OutlinedButton(
            onClick = onDownload,
            enabled = !state.isWorking && state.url.isNotBlank(),
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Spacer(modifier = Modifier.width(spacing.sm))
            Text(stringResource(R.string.import_download))
        }
    }
}

@Composable
private fun WorkingCard(state: ImportPdfUiState) {
    val spacing = LocalSpacing.current
    val progress = state.downloadProgress
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Text(
                text = stringResource(
                    if (progress != null) R.string.import_downloading else R.string.import_staging,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = stringResource(
                        R.string.import_download_progress,
                        (progress * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InspectionCard(state: ImportPdfUiState) {
    val staged = state.staged ?: return
    val spacing = LocalSpacing.current
    SectionGroupCard(
        title = stringResource(R.string.import_inspection_title),
        subtitle = stringResource(R.string.import_inspection_helper),
    ) {
        Text(
            text = staged.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MetricPill(
                value = stringResource(R.string.import_pages, staged.metadata.pageCount),
            )
            MetricPill(
                value = stringResource(
                    R.string.import_text_pages,
                    staged.metadata.pagesWithExtractableText,
                ),
            )
            MetricPill(
                value = stringResource(
                    R.string.import_scanned_pages,
                    (staged.metadata.pageCount - staged.metadata.pagesWithExtractableText)
                        .coerceAtLeast(0),
                ),
            )
            MetricPill(
                value = stringResource(
                    R.string.import_size,
                    staged.metadata.fileSizeBytes / (1024.0 * 1024.0),
                ),
            )
        }
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = stringResource(R.string.import_hash, staged.metadata.fileHash),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessingOptionsCard(
    state: ImportPdfUiState,
    onPageStartChange: (String) -> Unit,
    onPageEndChange: (String) -> Unit,
    onForceOcrChange: (Boolean) -> Unit,
    onDirectChange: (Boolean) -> Unit,
    onOverrideChange: (Boolean) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onDownloadLanguage: (String) -> Unit,
) {
    val spacing = LocalSpacing.current
    val staged = state.staged ?: return
    SectionGroupCard(
        title = stringResource(R.string.import_options_title),
        subtitle = stringResource(R.string.import_page_helper),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            OutlinedTextField(
                value = state.pageStart,
                onValueChange = onPageStartChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.import_page_start)) },
                singleLine = true,
                isError = state.pageRangeInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = state.pageEnd,
                onValueChange = onPageEndChange,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.import_page_end)) },
                singleLine = true,
                isError = state.pageRangeInvalid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        if (state.pageRangeInvalid) {
            Text(
                text = stringResource(R.string.import_page_error, staged.pageCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = spacing.xs),
            )
        }
        SettingSwitchRow(
            title = stringResource(R.string.import_force_ocr),
            subtitle = stringResource(R.string.import_force_ocr_helper),
            checked = state.forceLocalOcr,
            onCheckedChange = onForceOcrChange,
        )
        SettingSwitchRow(
            title = stringResource(R.string.import_prefer_direct),
            subtitle = stringResource(R.string.import_prefer_direct_helper),
            checked = state.preferDirectPdf,
            enabled = state.supportsDirectPdf,
            onCheckedChange = onDirectChange,
        )
        SettingSwitchRow(
            title = stringResource(R.string.import_override_ocr, state.ocrPageLimit),
            subtitle = stringResource(R.string.import_override_ocr_helper),
            checked = state.overrideOcrLimit,
            onCheckedChange = onOverrideChange,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = spacing.sm))
        Text(
            text = stringResource(R.string.import_language_title),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(R.string.import_language_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xxs),
        )
        Spacer(modifier = Modifier.height(spacing.sm))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            OcrLanguageCatalog.languages.forEach { language ->
                FilterChip(
                    selected = state.languageCode == language.code,
                    onClick = { onLanguageSelected(language.code) },
                    label = { Text(stringResource(language.displayNameRes)) },
                )
            }
        }
        val language = state.selectedLanguage
        if (language.requiresDownload) {
            Spacer(modifier = Modifier.height(spacing.sm))
            val modelState = state.selectedLanguageState
            LanguagePackStatus(
                modelState = modelState,
                onDownload = { onDownloadLanguage(language.code) },
            )
        }
    }
}

@Composable
private fun LanguagePackStatus(
    modelState: OcrModelState?,
    onDownload: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val status = modelState?.status ?: OcrModelStatus.NOT_INSTALLED
    when (status) {
        OcrModelStatus.INSTALLED -> {
            Text(
                text = stringResource(R.string.import_language_installed),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        OcrModelStatus.DOWNLOADING -> {
            LinearProgressIndicator(
                progress = { modelState?.progress ?: 0f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.import_language_downloading,
                    ((modelState?.progress ?: 0f) * 100).roundToInt(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xxs),
            )
        }

        OcrModelStatus.VERIFYING -> {
            Text(
                text = stringResource(R.string.import_model_verifying),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }

        OcrModelStatus.ERROR -> {
            Text(
                text = stringResource(
                    R.string.import_language_download_failed_detail,
                    modelState?.errorDetail
                        ?: stringResource(R.string.import_language_download_failed),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = onDownload) {
                Text(stringResource(R.string.import_language_retry_download))
            }
        }

        OcrModelStatus.NOT_INSTALLED -> {
            OutlinedButton(onClick = onDownload) {
                Icon(Icons.Filled.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(stringResource(R.string.import_language_download))
            }
            val sizeMb = modelState?.totalBytes?.takeIf { it > 0L }?.let {
                it / (1024.0 * 1024.0)
            }
            if (sizeMb != null) {
                Text(
                    text = stringResource(R.string.import_language_pack_size, sizeMb),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.xxs),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProcessingPlanCard(state: ImportPdfUiState) {
    val decision = state.decision ?: return
    val cost = state.cost ?: return
    val range = state.pageRange ?: return
    val spacing = LocalSpacing.current
    SectionGroupCard(
        title = stringResource(R.string.import_plan_title),
        subtitle = stringResource(R.string.import_plan_helper),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            MetricPill(value = routeLabel(decision.route))
            MetricPill(
                value = stringResource(R.string.import_pages, range.pageCount),
            )
        }
        Spacer(modifier = Modifier.height(spacing.sm))
        Text(
            text = routeExplanation(decision),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val blockReason = decision.directPdfBlockReason
        if (state.preferDirectPdf && blockReason != null) {
            Spacer(modifier = Modifier.height(spacing.sm))
            InlineBanner(
                message = stringResource(
                    when (blockReason) {
                        DirectPdfBlockReason.PROVIDER_UNSUPPORTED ->
                            R.string.import_direct_blocked_provider
                        DirectPdfBlockReason.FILE_TOO_LARGE ->
                            R.string.import_direct_blocked_size
                        DirectPdfBlockReason.TOO_MANY_PAGES ->
                            R.string.import_direct_blocked_pages
                    },
                ),
                severity = BannerSeverity.WARNING,
            )
        }
        if (decision.requiresOcrOverride) {
            Spacer(modifier = Modifier.height(spacing.sm))
            InlineBanner(
                message = stringResource(R.string.import_override_required),
                severity = BannerSeverity.WARNING,
            )
        }
        Spacer(modifier = Modifier.height(spacing.md))
        CostPreview(cost = cost, range = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CostPreview(cost: PdfCostEstimate, range: PdfPageRange) {
    val spacing = LocalSpacing.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        MetricPill(
            value = stringResource(
                R.string.import_cost_tokens,
                cost.inputTokens,
                cost.outputTokens,
            ),
        )
        MetricPill(value = stringResource(R.string.import_cost_amount, cost.estimatedCostUsd))
    }
    Spacer(modifier = Modifier.height(spacing.xs))
    Text(
        text = if (cost.pricing.isBaseline) {
            stringResource(R.string.import_cost_baseline)
        } else {
            stringResource(R.string.import_cost_source, cost.pricing.sourceLabel)
        },
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(spacing.xs))
    Text(
        text = stringResource(R.string.import_cost_range, range.startPage, range.endPage),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.import_cost_note),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = spacing.xxs),
    )
}

@Composable
private fun ImportSessionCard(
    state: ImportPdfUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val document = state.activeDocument
    SectionGroupCard(title = stringResource(R.string.import_session_title)) {
        Text(
            text = document?.title ?: stringResource(R.string.import_session_title),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(spacing.sm))

        if (document == null) {
            Text(
                text = stringResource(R.string.import_session_queued),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val status = document.extractionStatus
            val progress = remember(document, state.activeSettings) {
                OcrRangeProgress.of(document, state.activeSettings)
            }
            val isOcrRoute = document.processingRoute == PdfProcessingRoute.LOCAL_OCR ||
                document.processingRoute == PdfProcessingRoute.OCR_REQUIRES_OVERRIDE

            when (status) {
                ExtractionStatus.OCR_IN_PROGRESS -> {
                    if (isOcrRoute) {
                        LinearProgressIndicator(
                            progress = { progress.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(spacing.xs))
                        Text(
                            text = stringResource(
                                R.string.import_session_ocr_running,
                                progress.nextPage,
                                progress.rangeEnd,
                                progress.completedPages,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.import_session_text_running),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }

                ExtractionStatus.OCR_PAUSED -> {
                    Text(
                        text = stringResource(
                            R.string.import_session_ocr_paused,
                            progress.nextPage,
                            progress.rangeEnd,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = if (progress.completedPages > 0) {
                            stringResource(
                                R.string.import_session_saved_pages,
                                progress.rangeStart,
                                document.ocrLastCompletedPage.coerceAtMost(progress.rangeEnd),
                            )
                        } else {
                            stringResource(R.string.import_session_no_saved_pages)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ExtractionStatus.ERROR -> {
                    val error = document.lastErrorMessage
                        ?: stringResource(R.string.import_session_error_generic)
                    Text(
                        text = stringResource(R.string.import_session_error, error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                ExtractionStatus.OCR_DONE -> {
                    Text(
                        text = stringResource(
                            R.string.import_session_ocr_done,
                            progress.completedPages,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }

                ExtractionStatus.ANALYZED -> {
                    Text(
                        text = stringResource(
                            if (document.processingRoute == PdfProcessingRoute.DIRECT_PDF) {
                                R.string.import_session_direct_ready
                            } else {
                                R.string.import_session_ready
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ExtractionStatus.IMPORTED -> {
                    Text(
                        text = stringResource(
                            if (document.processingRoute == PdfProcessingRoute.EXTRACT_TEXT) {
                                R.string.import_session_text_running
                            } else {
                                R.string.import_session_queued
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                ExtractionStatus.OUTLINE_READY,
                ExtractionStatus.FACTS_READY,
                -> Text(
                    text = stringResource(R.string.import_session_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                when (status) {
                    ExtractionStatus.OCR_IN_PROGRESS -> Button(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(stringResource(R.string.import_pause))
                    }

                    ExtractionStatus.OCR_PAUSED -> Button(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(stringResource(R.string.import_resume))
                    }

                    ExtractionStatus.ERROR -> Button(onClick = onRetry) {
                        Icon(Icons.Filled.Replay, contentDescription = null)
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(stringResource(R.string.import_retry))
                    }

                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ImportFailureBanner(
    failure: ImportFailure,
    onDismiss: () -> Unit,
) {
    val message = failure.detail?.takeIf { it.isNotBlank() }?.let { detail ->
        stringResource(
            R.string.import_failure_detail,
            stringResource(failure.code.messageRes()),
            detail,
        )
    } ?: stringResource(failure.code.messageRes())
    InlineBanner(
        message = message,
        severity = BannerSeverity.ERROR,
        actionLabel = stringResource(R.string.import_dismiss),
        onAction = onDismiss,
    )
}

@Composable
private fun routeLabel(route: PdfProcessingRoute): String = stringResource(
    when (route) {
        PdfProcessingRoute.DIRECT_PDF -> R.string.import_route_direct
        PdfProcessingRoute.EXTRACT_TEXT -> R.string.import_route_text
        PdfProcessingRoute.LOCAL_OCR -> R.string.import_route_local_ocr
        PdfProcessingRoute.OCR_REQUIRES_OVERRIDE -> R.string.import_route_override
    },
)

@Composable
private fun routeExplanation(decision: PdfRouteDecision): String = when (decision.route) {
    PdfProcessingRoute.DIRECT_PDF -> stringResource(
        R.string.import_route_explanation_direct,
        decision.pageRange.startPage,
        decision.pageRange.endPage,
    )

    PdfProcessingRoute.EXTRACT_TEXT -> stringResource(
        R.string.import_route_explanation_text,
        decision.filteredPageCount,
    )

    PdfProcessingRoute.LOCAL_OCR -> stringResource(
        R.string.import_route_explanation_ocr,
        decision.scannedPagesInRange,
    )

    PdfProcessingRoute.OCR_REQUIRES_OVERRIDE -> stringResource(
        R.string.import_route_explanation_override,
        decision.scannedPagesInRange,
        decision.ocrPageLimit,
    )
}

@StringRes
private fun PdfImportErrorCode.messageRes(): Int = when (this) {
    PdfImportErrorCode.UNREADABLE_SOURCE -> R.string.import_error_unreadable
    PdfImportErrorCode.NOT_A_PDF -> R.string.import_error_not_pdf
    PdfImportErrorCode.ENCRYPTED_PDF -> R.string.import_error_encrypted
    PdfImportErrorCode.CORRUPT_PDF -> R.string.import_error_corrupt
    PdfImportErrorCode.HTTPS_REQUIRED -> R.string.import_error_https
    PdfImportErrorCode.INVALID_URL -> R.string.import_error_invalid_url
    PdfImportErrorCode.DOWNLOAD_FAILED -> R.string.import_error_download
    PdfImportErrorCode.CONTENT_TYPE_REJECTED -> R.string.import_error_content_type
    PdfImportErrorCode.FILE_TOO_LARGE -> R.string.import_error_too_large
    PdfImportErrorCode.INSPECTION_FAILED -> R.string.import_error_inspection
    PdfImportErrorCode.STORAGE_FAILED -> R.string.import_error_storage
}

/** Page-range-aware OCR checkpoint math shared by the session card. */
private data class OcrRangeProgress(
    val rangeStart: Int,
    val rangeEnd: Int,
    val completedPages: Int,
    val nextPage: Int,
) {
    val totalPages: Int get() = (rangeEnd - rangeStart + 1).coerceAtLeast(1)
    val fraction: Float get() = (completedPages.toFloat() / totalPages.toFloat()).coerceIn(0f, 1f)

    companion object {
        fun of(document: Document, settings: com.factswallpaper.core.model.DocumentSettings?): OcrRangeProgress {
            val pageCount = document.pageCount.coerceAtLeast(1)
            val start = (settings?.pageRangeStart ?: 1).coerceIn(1, pageCount)
            val end = (settings?.pageRangeEnd ?: pageCount).coerceIn(start, pageCount)
            val total = (end - start + 1).coerceAtLeast(1)
            val completed = (document.ocrLastCompletedPage.coerceAtLeast(start - 1) - start + 1)
                .coerceIn(0, total)
            val next = when {
                document.ocrLastCompletedPage < start -> start
                document.ocrLastStartedPage > document.ocrLastCompletedPage ->
                    document.ocrLastStartedPage
                else -> document.ocrLastCompletedPage + 1
            }.coerceIn(start, end)
            return OcrRangeProgress(
                rangeStart = start,
                rangeEnd = end,
                completedPages = completed,
                nextPage = next,
            )
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

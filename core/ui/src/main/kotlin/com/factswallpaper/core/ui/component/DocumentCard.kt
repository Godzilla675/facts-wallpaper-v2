package com.factswallpaper.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.ui.R

/**
 * Library card with route badge, counts, rotation switch, live pipeline/OCR
 * progress and the resume/retry controls.
 */
@Composable
fun DocumentCard(
    document: Document,
    pageRangeStart: Int,
    pageRangeEnd: Int,
    onOpenOutline: () -> Unit,
    onOpenFacts: () -> Unit,
    onToggleRotation: (Boolean) -> Unit,
    onPauseOcr: () -> Unit,
    onResumeOcr: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    sourceMissing: Boolean = false,
    onRelink: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val safeStart = pageRangeStart.coerceIn(1, document.pageCount.coerceAtLeast(1))
    val safeEnd = pageRangeEnd.coerceIn(safeStart, document.pageCount.coerceAtLeast(1))
    val totalPages = (safeEnd - safeStart + 1).coerceAtLeast(1)
    val completedInRange = (document.ocrLastCompletedPage.coerceAtLeast(safeStart - 1) - safeStart + 1)
        .coerceIn(0, totalPages)
    val nextPage = when {
        document.ocrLastCompletedPage < safeStart -> safeStart
        document.ocrLastStartedPage > document.ocrLastCompletedPage ->
            document.ocrLastStartedPage
        else -> document.ocrLastCompletedPage + 1
    }.coerceIn(safeStart, safeEnd)

    Card(
        onClick = onOpenOutline,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.md),
                ) {
                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = document.sourceUri,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.document_delete_description, document.title),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                MetricPill(value = stringResource(R.string.document_pages, document.pageCount))
                MetricPill(value = stringResource(R.string.document_lessons, document.lessonCount))
                MetricPill(value = stringResource(R.string.document_facts, document.factCount))
                MetricPill(value = routeLabel(document.processingRoute))
                stageChipLabel(document.extractionStatus)?.let { stage ->
                    MetricPill(value = stage)
                }
            }

            if (sourceMissing) {
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = stringResource(R.string.document_source_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRelink) {
                    Text(stringResource(R.string.document_source_relink))
                }
            }

            if (document.extractionStatus.isPipelineActive) {
                Spacer(modifier = Modifier.height(spacing.sm))
                if (document.pipelineProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { document.pipelineProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (document.extractionStatus == ExtractionStatus.OCR_IN_PROGRESS) {
                    LinearProgressIndicator(
                        progress = { completedInRange.toFloat() / totalPages.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    text = when (document.extractionStatus) {
                        ExtractionStatus.IMPORTED -> stringResource(R.string.document_status_processing)
                        ExtractionStatus.ANALYZED,
                        ExtractionStatus.EXTRACTING_TEXT -> stringResource(R.string.stage_extracting_text)
                        ExtractionStatus.OCR_IN_PROGRESS -> stringResource(
                            R.string.document_ocr_active,
                            nextPage,
                            safeEnd,
                            completedInRange,
                        )
                        ExtractionStatus.OUTLINE_IN_PROGRESS -> stringResource(R.string.stage_outline_generating)
                        ExtractionStatus.FACTS_IN_PROGRESS -> stringResource(R.string.stage_facts_generating)
                        else -> stringResource(R.string.document_status_processing)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                if (document.extractionStatus == ExtractionStatus.OCR_IN_PROGRESS) {
                    TextButton(onClick = onPauseOcr) {
                        Text(stringResource(R.string.document_ocr_pause))
                    }
                }
            } else {
                when (document.extractionStatus) {
                    ExtractionStatus.OCR_PAUSED -> {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Text(
                            text = stringResource(
                                R.string.document_ocr_paused,
                                nextPage,
                                safeEnd,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = if (completedInRange > 0) {
                                stringResource(
                                    R.string.document_ocr_saved_pages,
                                    safeStart,
                                    document.ocrLastCompletedPage.coerceAtMost(safeEnd),
                                )
                            } else {
                                stringResource(R.string.document_ocr_no_saved_pages)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onResumeOcr) {
                            Text(stringResource(R.string.document_ocr_resume, nextPage))
                        }
                    }

                    ExtractionStatus.ERROR -> {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Text(
                            text = document.lastErrorMessage
                                ?: stringResource(R.string.document_status_error),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.document_retry))
                        }
                    }

                    ExtractionStatus.OCR_DONE -> {
                        Spacer(modifier = Modifier.height(spacing.sm))
                        Text(
                            text = stringResource(R.string.document_status_ocr_done, completedInRange),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    else -> Unit
                }
            }

            Spacer(modifier = Modifier.height(spacing.xs))
            SettingSwitchRow(
                title = stringResource(R.string.document_use_for_wallpaper),
                checked = document.includedInRotation,
                onCheckedChange = onToggleRotation,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenOutline) {
                    Text(stringResource(R.string.document_open_outline))
                }
                TextButton(onClick = onOpenFacts) {
                    Text(stringResource(R.string.document_open_facts))
                }
            }
        }
    }
}

@Composable
private fun stageChipLabel(status: ExtractionStatus): String? = when (status) {
    ExtractionStatus.EXTRACTING_TEXT -> stringResource(R.string.stage_extracting_text)
    ExtractionStatus.OCR_IN_PROGRESS -> "OCR"
    ExtractionStatus.OUTLINE_IN_PROGRESS -> stringResource(R.string.stage_outline_generating)
    ExtractionStatus.OUTLINE_READY -> stringResource(R.string.stage_outline_ready)
    ExtractionStatus.FACTS_IN_PROGRESS -> stringResource(R.string.stage_facts_generating)
    ExtractionStatus.COMPLETED -> stringResource(R.string.stage_completed)
    else -> null
}

@Composable
private fun routeLabel(route: PdfProcessingRoute): String = stringResource(
    when (route) {
        PdfProcessingRoute.DIRECT_PDF -> R.string.document_route_direct_pdf
        PdfProcessingRoute.EXTRACT_TEXT -> R.string.document_route_extract_text
        PdfProcessingRoute.LOCAL_OCR -> R.string.document_route_local_ocr
        PdfProcessingRoute.OCR_REQUIRES_OVERRIDE -> R.string.document_route_ocr_override
    },
)

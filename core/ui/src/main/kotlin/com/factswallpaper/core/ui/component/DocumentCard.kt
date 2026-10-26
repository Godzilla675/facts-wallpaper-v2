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

/** Library card with route badge, counts, rotation switch and quick actions. */
@Composable
fun DocumentCard(
    document: Document,
    onOpenOutline: () -> Unit,
    onOpenFacts: () -> Unit,
    onToggleRotation: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
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
            }
            if (document.extractionStatus == ExtractionStatus.OCR_IN_PROGRESS ||
                document.extractionStatus == ExtractionStatus.OCR_PAUSED
            ) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = stringResource(
                        R.string.document_ocr_progress,
                        document.ocrLastCompletedPage,
                        document.pageCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            if (document.extractionStatus == ExtractionStatus.ERROR) {
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    text = document.lastErrorMessage
                        ?: stringResource(R.string.document_status_error),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
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
private fun routeLabel(route: PdfProcessingRoute): String = stringResource(
    when (route) {
        PdfProcessingRoute.DIRECT_PDF -> R.string.document_route_direct_pdf
        PdfProcessingRoute.EXTRACT_TEXT -> R.string.document_route_extract_text
        PdfProcessingRoute.LOCAL_OCR -> R.string.document_route_local_ocr
        PdfProcessingRoute.OCR_REQUIRES_OVERRIDE -> R.string.document_route_ocr_override
    }
)

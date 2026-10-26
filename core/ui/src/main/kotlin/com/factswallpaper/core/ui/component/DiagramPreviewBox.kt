package com.factswallpaper.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.factswallpaper.core.designsystem.theme.LocalFactColors
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.ui.R

/**
 * Framed diagram preview. Until `:core:render` ships in P4, the box shows the
 * real diagram source plus a "render pending" state; tapping opens the same
 * source in a zoomable modal so long Mermaid/TikZ code stays readable.
 */
@Composable
fun DiagramPreviewBox(
    diagramKind: DiagramKind,
    source: String?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val factColors = LocalFactColors.current
    var expanded by remember { mutableStateOf(false) }
    val hasDiagram = diagramKind != DiagramKind.NONE
    val expandedDescription = stringResource(R.string.diagram_preview_expand)
    val closeDescription = stringResource(R.string.diagram_preview_close)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = hasDiagram && !source.isNullOrBlank()) { expanded = true },
        shape = MaterialTheme.shapes.medium,
        color = factColors.diagramContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, factColors.diagramAccent.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountTree,
                        contentDescription = null,
                        tint = factColors.diagramAccent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.diagram_preview_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = factColors.diagramAccent,
                    )
                }
                if (hasDiagram && !source.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Filled.OpenInFull,
                        contentDescription = expandedDescription,
                        tint = factColors.diagramAccent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            when {
                !hasDiagram -> Text(
                    text = stringResource(R.string.diagram_preview_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                source.isNullOrBlank() -> Text(
                    text = stringResource(R.string.diagram_preview_pending),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
                else -> Text(
                    text = source,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 4,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            }
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(spacing.lg)) {
                    Text(
                        text = stringResource(R.string.diagram_preview_source_label),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = source.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .padding(vertical = spacing.md)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { expanded = false }) {
                            Text(closeDescription)
                        }
                    }
                }
            }
        }
    }
}

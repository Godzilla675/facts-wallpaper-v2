package com.factswallpaper.core.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.ui.R

/** Fact curation card: badges, body, optional formula/diagram, tags and actions. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun FactListItemCard(
    fact: Fact,
    sourceReferenceMode: SourceReferenceMode,
    onToggleActive: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
    onCopyText: (() -> Unit)? = null,
    onShareAsImage: (() -> Unit)? = null,
    onJumpToSource: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { menuExpanded = true },
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(spacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetricPill(value = templateLabel(fact.templateType))
                if (fact.verificationStatus == VerificationStatus.FAILED) {
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(
                        text = stringResource(R.string.fact_needs_review),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (fact.confidence < 1f) {
                    Spacer(modifier = Modifier.width(spacing.xs))
                    MetricPill(
                        value = stringResource(
                            R.string.fact_confidence,
                            (fact.confidence.coerceIn(0f, 1f) * 100).toInt(),
                        ),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                IconToggleButton(
                    checked = fact.favorite,
                    onCheckedChange = onToggleFavorite,
                ) {
                    Icon(
                        imageVector = if (fact.favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = stringResource(
                            if (fact.favorite) R.string.fact_unfavorite else R.string.fact_favorite,
                        ),
                        tint = if (fact.favorite) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.fact_card_menu),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (fact.favorite) R.string.fact_action_unfavorite else R.string.fact_action_favorite,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (fact.favorite) Icons.Outlined.StarBorder else Icons.Filled.Star,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleFavorite(!fact.favorite)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (fact.active) R.string.fact_action_hide_from_rotation else R.string.fact_action_show_in_rotation,
                                    ),
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (fact.active) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleActive(!fact.active)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fact_action_edit)) },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEdit()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.fact_action_regenerate)) },
                            leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRegenerate()
                            },
                        )
                        onCopyText?.let { copyAction ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fact_action_copy_text)) },
                                leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    copyAction()
                                },
                            )
                        }
                        onShareAsImage?.let { shareAction ->
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fact_action_share_image)) },
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    shareAction()
                                },
                            )
                        }
                        if (fact.sourcePage != null && onJumpToSource != null) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.fact_action_jump_to_source)) },
                                leadingIcon = { Icon(Icons.Filled.MenuBook, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onJumpToSource()
                                },
                            )
                        }
                        onDelete?.let { deleteAction ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.fact_delete),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    deleteAction()
                                },
                            )
                        }
                    }
                }
            }
            Text(
                text = fact.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = spacing.sm),
            )
            if (fact.hasFormula) {
                Spacer(modifier = Modifier.height(spacing.sm))
                FormulaPreviewBox(
                    latex = fact.latex,
                    verificationStatus = fact.verificationStatus,
                )
            }
            if (fact.hasDiagram) {
                Spacer(modifier = Modifier.height(spacing.sm))
                DiagramPreviewBox(
                    diagramKind = fact.diagramType,
                    source = fact.diagramSource,
                )
            }
            if (fact.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(spacing.sm))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    fact.tags.forEach { tag ->
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                text = "#$tag",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(spacing.sm))
            SourceBadge(
                bookTitle = fact.sourceBookTitle,
                lessonTitle = fact.sourceLessonTitle,
                page = fact.sourcePage,
                mode = fact.sourceReferenceModeOverride ?: sourceReferenceMode,
            )
            if (fact.active) {
                Spacer(modifier = Modifier.height(spacing.xs))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.fact_active),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(spacing.xs))
                Switch(
                    checked = fact.active,
                    onCheckedChange = onToggleActive,
                )
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.fact_edit))
                }
                TextButton(onClick = onRegenerate) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.fact_regenerate))
                }
            }
            if (fact.diagramSource.isNullOrBlank() && fact.hasDiagram) {
                Text(
                    text = stringResource(R.string.diagram_preview_pending),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun templateLabel(type: CardTemplateType): String = stringResource(
    when (type) {
        CardTemplateType.TEXT -> R.string.fact_template_text
        CardTemplateType.QUOTE -> R.string.fact_template_quote
        CardTemplateType.BULLET -> R.string.fact_template_bullet
        CardTemplateType.DEFINITION -> R.string.fact_template_definition
        CardTemplateType.QA -> R.string.fact_template_qa
        CardTemplateType.FORMULA -> R.string.fact_template_formula
        CardTemplateType.DIAGRAM -> R.string.fact_template_diagram
        CardTemplateType.TIMELINE -> R.string.fact_template_timeline
    }
)

@Composable
fun diagramKindLabel(kind: DiagramKind): String = stringResource(
    when (kind) {
        DiagramKind.MERMAID -> R.string.diagram_kind_mermaid
        DiagramKind.TIKZ -> R.string.diagram_kind_tikz
        DiagramKind.SVG -> R.string.diagram_kind_svg
        DiagramKind.HTML_CANVAS -> R.string.diagram_kind_html_canvas
        DiagramKind.NONE -> R.string.diagram_preview_empty
    }
)

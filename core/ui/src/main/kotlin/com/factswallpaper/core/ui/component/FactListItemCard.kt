package com.factswallpaper.core.ui.component

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.factswallpaper.core.ui.R

/** Fact curation card: badges, body, optional formula/diagram, tags and actions. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FactListItemCard(
    fact: Fact,
    sourceReferenceMode: SourceReferenceMode,
    onToggleActive: (Boolean) -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = modifier.fillMaxWidth(),
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

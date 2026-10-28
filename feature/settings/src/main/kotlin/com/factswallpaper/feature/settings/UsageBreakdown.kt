package com.factswallpaper.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.ui.format.MetricsFormatter

/** One row in the usage ledger breakdown (F19). A null label means the PDF is gone. */
data class UsageBreakdownRow(
    val label: String?,
    val tokens: Long,
    val costUsd: Double,
)

data class UsageBreakdown(
    val byProvider: List<UsageBreakdownRow> = emptyList(),
    val byDocument: List<UsageBreakdownRow> = emptyList(),
)

/** Groups ledger entries by provider profile and by source PDF, cost-descending. */
fun buildUsageBreakdown(
    usage: List<UsageEntry>,
    profiles: List<ProviderProfile>,
    documents: List<Document>,
): UsageBreakdown {
    val profileNames = profiles.associate { it.id to it.name }
    val documentTitles = documents.associate { it.id to it.title }
    return UsageBreakdown(
        byProvider = usage
            .groupBy { it.providerProfileId }
            .map { (profileId, rows) ->
                UsageBreakdownRow(
                    label = profileNames[profileId] ?: profileId,
                    tokens = rows.sumOf { it.totalTokens.toLong() },
                    costUsd = rows.sumOf { it.estimatedCostUsd },
                )
            }
            .sortedByDescending { it.costUsd },
        byDocument = usage
            .filter { it.documentId != null }
            .groupBy { it.documentId.orEmpty() }
            .map { (documentId, rows) ->
                UsageBreakdownRow(
                    label = documentTitles[documentId],
                    tokens = rows.sumOf { it.totalTokens.toLong() },
                    costUsd = rows.sumOf { it.estimatedCostUsd },
                )
            }
            .sortedByDescending { it.costUsd },
    )
}

@Composable
fun UsageBreakdownSection(
    title: String,
    rows: List<UsageBreakdownRow>,
) {
    val spacing = LocalSpacing.current
    Column(modifier = Modifier.padding(top = spacing.sm)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (rows.isEmpty()) {
            Text(
                text = stringResource(R.string.advanced_usage_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = spacing.xs),
            )
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.label
                            ?: stringResource(R.string.advanced_usage_removed_document),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.advanced_usage_row,
                            MetricsFormatter.compactTokens(row.tokens),
                            MetricsFormatter.costUsd(row.costUsd),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = spacing.sm),
                    )
                }
            }
        }
    }
}

package com.factswallpaper.core.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.designsystem.theme.FactsMotion
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.ui.R

/** Numbered progress header shared by onboarding and the PDF pipeline. */
@Composable
fun StepProgressHeader(
    step: Int,
    totalSteps: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    val spacing = LocalSpacing.current
    val safeTotal = totalSteps.coerceAtLeast(1)
    val safeStep = step.coerceIn(1, safeTotal)
    val target = safeStep.toFloat() / safeTotal.toFloat()
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = FactsMotion.mediumTween,
        label = "stepProgress",
    )
    val description = stringResource(R.string.step_progress_description)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.lg, vertical = spacing.md)
            .semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.padding(horizontal = spacing.xs))
            Text(
                text = stringResource(R.string.step_progress, safeStep, safeTotal),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(spacing.sm))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

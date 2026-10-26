package com.factswallpaper.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.designsystem.theme.LocalFactColors
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.ui.R

/**
 * Framed formula preview. P1 shows syntax-highlighted LaTeX source; the
 * `:core:render` phase swaps in the rendered KaTeX bitmap without changing this
 * component's contract.
 */
@Composable
fun FormulaPreviewBox(
    latex: String?,
    modifier: Modifier = Modifier,
    verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
) {
    val spacing = LocalSpacing.current
    val factColors = LocalFactColors.current
    val description = stringResource(R.string.formula_preview_description)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = description },
        shape = MaterialTheme.shapes.medium,
        color = factColors.formulaContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, factColors.formulaAccent.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.formula_preview_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = factColors.formulaAccent,
                )
                VerificationBadge(status = verificationStatus)
            }
            if (latex.isNullOrBlank()) {
                Text(
                    text = stringResource(R.string.formula_preview_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = spacing.sm),
                )
            } else {
                Text(
                    text = latex,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = spacing.sm)
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}

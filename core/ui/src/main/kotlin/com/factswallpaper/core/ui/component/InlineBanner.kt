package com.factswallpaper.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.ui.R

enum class BannerSeverity { INFO, WARNING, ERROR, SUCCESS }

/** Contextual alert banner with the four spec variants and an optional action. */
@Composable
fun InlineBanner(
    message: String,
    severity: BannerSeverity,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val accent: Color
    val container: Color
    val icon: ImageVector
    when (severity) {
        BannerSeverity.INFO -> {
            accent = colors.primary
            container = colors.primaryContainer
            icon = Icons.Filled.Info
        }
        BannerSeverity.WARNING -> {
            accent = colors.tertiary
            container = colors.tertiaryContainer
            icon = Icons.Filled.Warning
        }
        BannerSeverity.ERROR -> {
            accent = colors.error
            container = colors.errorContainer
            icon = Icons.Filled.Error
        }
        BannerSeverity.SUCCESS -> {
            accent = colors.primary
            container = colors.primaryContainer
            icon = Icons.Filled.CheckCircle
        }
    }
    val spokenLabel = when (severity) {
        BannerSeverity.INFO -> stringResource(R.string.banner_info)
        BannerSeverity.WARNING -> stringResource(R.string.banner_warning)
        BannerSeverity.ERROR -> stringResource(R.string.banner_error)
        BannerSeverity.SUCCESS -> stringResource(R.string.banner_success)
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
    ) {
        Row(
            modifier = Modifier.padding(spacing.md),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = spokenLabel,
                tint = accent,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = message, style = MaterialTheme.typography.bodyMedium)
                if (actionLabel != null && onAction != null) {
                    TextButton(
                        onClick = onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = spacing.xs,
                        ),
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

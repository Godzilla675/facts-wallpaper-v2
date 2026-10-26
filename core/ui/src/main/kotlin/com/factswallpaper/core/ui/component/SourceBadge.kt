package com.factswallpaper.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.ui.R
import com.factswallpaper.core.ui.format.SourceReferenceFormatter

/** Provenance pill: `Book · Lesson · p. 42` per [SourceReferenceMode]. */
@Composable
fun SourceBadge(
    bookTitle: String?,
    mode: SourceReferenceMode,
    modifier: Modifier = Modifier,
    lessonTitle: String? = null,
    page: Int? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val text = SourceReferenceFormatter.format(
        bookTitle = bookTitle,
        lessonTitle = lessonTitle,
        page = page,
        mode = mode,
        unknownBook = stringResource(R.string.source_unknown_book),
        pagePrefix = stringResource(R.string.source_page_prefix),
    )
    if (text.isBlank()) return

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun VerificationBadge(
    status: VerificationStatus,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (icon, label, tint) = when (status) {
        VerificationStatus.VERIFIED ->
            Triple(Icons.Filled.CheckCircle, R.string.verification_verified, colors.primary)
        VerificationStatus.UNVERIFIED ->
            Triple(Icons.Filled.HelpOutline, R.string.verification_unverified, colors.onSurfaceVariant)
        VerificationStatus.FAILED ->
            Triple(Icons.Filled.Warning, R.string.verification_failed, colors.error)
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = tint,
            )
        }
    }
}

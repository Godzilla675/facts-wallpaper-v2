package com.factswallpaper.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.ui.R

/** Compact monospace badge for page counts, token estimates and USD costs. */
@Composable
fun MetricPill(
    value: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    icon: ImageVector? = null,
    onClick: (() -> Unit)? = null,
) {
    val actionDescription = if (onClick != null) {
        val metric = if (label.isNullOrBlank()) value else "$value $label"
        stringResource(R.string.metric_open_description, metric)
    } else {
        null
    }
    val interactiveModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = actionDescription.orEmpty() }
    } else {
        Modifier
    }
    Surface(
        modifier = modifier.then(interactiveModifier),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(text = value, style = MaterialTheme.typography.labelSmall)
            if (label != null) {
                Text(text = label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

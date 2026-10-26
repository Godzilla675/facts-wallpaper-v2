package com.factswallpaper.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.factswallpaper.core.ui.TopLevelDestination

/**
 * Adaptive navigation shell from spec 9.3.1: a bottom navigation bar on compact
 * windows (< 600.dp) and a navigation rail on tablets, foldables and landscape.
 *
 * Passing a null [selectedDestination] renders no navigation chrome, which is
 * how detail destinations (import, outline, previews) are hosted.
 */
@Composable
fun FactsAppScaffold(
    selectedDestination: TopLevelDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val compact = maxWidth < 600.dp
        if (compact) {
            Scaffold(
                bottomBar = {
                    if (selectedDestination != null) {
                        FactsBottomNavigationBar(
                            selectedDestination = selectedDestination,
                            onNavigate = onNavigate,
                        )
                    }
                },
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = padding.calculateBottomPadding()),
                ) {
                    content()
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxSize()) {
                if (selectedDestination != null) {
                    FactsNavigationRail(
                        selectedDestination = selectedDestination,
                        onNavigate = onNavigate,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun FactsBottomNavigationBar(
    selectedDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == selectedDestination
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

@Composable
private fun FactsNavigationRail(
    selectedDestination: TopLevelDestination,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationRail {
        TopLevelDestination.entries.forEach { destination ->
            val selected = destination == selectedDestination
            NavigationRailItem(
                selected = selected,
                onClick = { if (!selected) onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null,
                    )
                },
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}

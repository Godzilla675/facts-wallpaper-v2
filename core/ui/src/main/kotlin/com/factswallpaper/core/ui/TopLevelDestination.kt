package com.factswallpaper.core.ui

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.ui.graphics.vector.ImageVector
import com.factswallpaper.core.ui.R

/**
 * The five top-level tabs rendered by `FactsAppScaffold`.
 *
 * The [route] values are the canonical destinations from spec 11.11. They are
 * plain strings so `:core:ui` does not depend on the app's Navigation graph;
 * `:app` maps each tab to its type-safe route.
 */
enum class TopLevelDestination(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    LIBRARY(
        route = "library",
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Filled.LibraryBooks,
        unselectedIcon = Icons.Outlined.LibraryBooks,
    ),
    FACTS(
        route = "facts",
        labelRes = R.string.nav_facts,
        selectedIcon = Icons.Filled.Lightbulb,
        unselectedIcon = Icons.Outlined.Lightbulb,
    ),
    COLLECTIONS(
        route = "collections",
        labelRes = R.string.nav_collections,
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
    ),
    WALLPAPER(
        route = "wallpaper_settings",
        labelRes = R.string.nav_wallpaper,
        selectedIcon = Icons.Filled.Wallpaper,
        unselectedIcon = Icons.Outlined.Wallpaper,
    ),
    SETTINGS(
        route = "provider_settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? =
            entries.firstOrNull { it.route == route }
    }
}

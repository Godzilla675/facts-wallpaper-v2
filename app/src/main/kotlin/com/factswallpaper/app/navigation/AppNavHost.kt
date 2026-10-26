package com.factswallpaper.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.factswallpaper.app.StartDestination
import com.factswallpaper.core.ui.TopLevelDestination
import com.factswallpaper.core.ui.component.FactsAppScaffold
import com.factswallpaper.feature.collections.CollectionsScreen
import com.factswallpaper.feature.facts.FactsScreen
import com.factswallpaper.feature.library.ImportPdfScreen
import com.factswallpaper.feature.library.LibraryScreen
import com.factswallpaper.feature.onboarding.OnboardingScreen
import com.factswallpaper.feature.outline.OutlineScreen
import com.factswallpaper.feature.settings.AdvancedSettingsScreen
import com.factswallpaper.feature.settings.ProviderSettingsScreen
import com.factswallpaper.feature.templates.CardPreviewScreen
import com.factswallpaper.feature.templates.TemplatesScreen
import com.factswallpaper.feature.wallpaper.WallpaperScreen

@Composable
fun AppNavHost(
    startDestination: StartDestination,
    sharedPdfUri: String?,
    onSharedPdfConsumed: () -> Unit,
    onLaunchLiveWallpaper: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(sharedPdfUri) {
        val uri = sharedPdfUri
        if (uri != null) {
            navController.navigate(AppRoute.ImportPdf(sharedUri = uri))
            onSharedPdfConsumed()
        }
    }

    val startRoute: AppRoute = when (startDestination) {
        StartDestination.ONBOARDING -> AppRoute.Onboarding
        StartDestination.LIBRARY -> AppRoute.Library
    }

    NavHost(navController = navController, startDestination = startRoute) {
        composable<AppRoute.Onboarding> {
            OnboardingScreen(
                onOpenPdfImport = { navController.navigate(AppRoute.ImportPdf()) },
                onLaunchLiveWallpaper = onLaunchLiveWallpaper,
                onFinished = {
                    navController.navigate(AppRoute.Library) {
                        popUpTo(AppRoute.Onboarding) { inclusive = true }
                    }
                },
            )
        }

        composable<AppRoute.Library> {
            TabFrame(TopLevelDestination.LIBRARY, navController) {
                LibraryScreen(
                    onImportPdf = { navController.navigate(AppRoute.ImportPdf()) },
                    onOpenDocument = { documentId ->
                        navController.navigate(AppRoute.Outline(documentId))
                    },
                    onOpenFacts = { documentId ->
                        navController.navigateTopLevel(TopLevelDestination.FACTS, documentId)
                    },
                )
            }
        }

        composable<AppRoute.ImportPdf> {
            DetailFrame {
                ImportPdfScreen(
                    onBack = { navController.popBackStack() },
                    onOpenDocument = { documentId ->
                        navController.navigate(AppRoute.Outline(documentId)) {
                            launchSingleTop = true
                        }
                    },
                )
            }
        }

        composable<AppRoute.Outline> {
            DetailFrame {
                OutlineScreen(onBack = { navController.popBackStack() })
            }
        }

        composable<AppRoute.Facts> {
            TabFrame(TopLevelDestination.FACTS, navController) {
                FactsScreen(
                    onOpenLibrary = { navController.navigateTopLevel(TopLevelDestination.LIBRARY) },
                    onOpenTemplates = { navController.navigate(AppRoute.TemplateGallery) },
                )
            }
        }

        composable<AppRoute.Collections> {
            TabFrame(TopLevelDestination.COLLECTIONS, navController) {
                CollectionsScreen(
                    onOpenTag = { tag ->
                        navController.navigate(AppRoute.Facts(tag = tag))
                    },
                    onOpenFacts = { status ->
                        navController.navigate(AppRoute.Facts(status = status.name))
                    },
                )
            }
        }

        composable<AppRoute.WallpaperSettings> {
            TabFrame(TopLevelDestination.WALLPAPER, navController) {
                WallpaperScreen(
                    onLaunchLiveWallpaper = onLaunchLiveWallpaper,
                    onPreviewTemplate = { templateId ->
                        navController.navigate(AppRoute.CardPreview(templateId = templateId))
                    },
                )
            }
        }

        composable<AppRoute.ProviderSettings> {
            TabFrame(TopLevelDestination.SETTINGS, navController) {
                ProviderSettingsScreen(
                    onOpenAdvanced = { navController.navigate(AppRoute.AdvancedSettings) },
                )
            }
        }

        composable<AppRoute.AdvancedSettings> {
            DetailFrame {
                AdvancedSettingsScreen(onBack = { navController.popBackStack() })
            }
        }

        composable<AppRoute.TemplateGallery> {
            DetailFrame {
                TemplatesScreen(
                    onBack = { navController.popBackStack() },
                    onPreviewTemplate = { templateId ->
                        navController.navigate(AppRoute.CardPreview(templateId = templateId))
                    },
                )
            }
        }

        composable<AppRoute.CardPreview> {
            DetailFrame {
                CardPreviewScreen(
                    onBack = { navController.popBackStack() },
                    onOpenFacts = {
                        navController.navigateTopLevel(TopLevelDestination.FACTS)
                    },
                    onOpenWallpaperSettings = {
                        navController.navigate(AppRoute.WallpaperSettings)
                    },
                )
            }
        }
    }
}

@Composable
private fun TabFrame(
    selected: TopLevelDestination,
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    FactsAppScaffold(
        selectedDestination = selected,
        onNavigate = { destination -> navController.navigateTopLevel(destination) },
        content = content,
    )
}

@Composable
private fun DetailFrame(content: @Composable () -> Unit) {
    FactsAppScaffold(
        selectedDestination = null,
        onNavigate = {},
        content = content,
    )
}

private fun NavHostController.navigateTopLevel(
    destination: TopLevelDestination,
    documentId: String? = null,
) {
    val route: AppRoute = when (destination) {
        TopLevelDestination.LIBRARY -> AppRoute.Library
        TopLevelDestination.FACTS -> AppRoute.Facts(documentId = documentId)
        TopLevelDestination.COLLECTIONS -> AppRoute.Collections
        TopLevelDestination.WALLPAPER -> AppRoute.WallpaperSettings
        TopLevelDestination.SETTINGS -> AppRoute.ProviderSettings
    }
    navigate(route) {
        popUpTo(AppRoute.Library) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

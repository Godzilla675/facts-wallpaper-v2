package com.factswallpaper.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.app.navigation.AppNavHost
import com.factswallpaper.core.designsystem.theme.FactsTheme
import com.factswallpaper.core.designsystem.theme.LocalSpacing

@Composable
fun FactsWallpaperApp(
    sharedPdfUri: String?,
    onSharedPdfConsumed: () -> Unit,
    onLaunchLiveWallpaper: () -> Unit,
    viewModel: AppViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()

    FactsTheme(themeMode = themeMode) {
        val start = startDestination
        if (start == null) {
            StartLoading()
        } else {
            AppNavHost(
                startDestination = start,
                sharedPdfUri = sharedPdfUri,
                onSharedPdfConsumed = onSharedPdfConsumed,
                onLaunchLiveWallpaper = onLaunchLiveWallpaper,
            )
        }
    }
}

@Composable
private fun StartLoading() {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.md),
            modifier = Modifier.padding(spacing.xl),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.app_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
        }
    }
}

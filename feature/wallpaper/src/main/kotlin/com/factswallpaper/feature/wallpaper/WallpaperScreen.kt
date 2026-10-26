package com.factswallpaper.feature.wallpaper

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.MetricPill
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SettingSwitchRow
import com.factswallpaper.core.wallpaper.FactsLiveWallpaperService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WallpaperScreen(
    onLaunchLiveWallpaper: () -> Unit,
    onPreviewTemplate: (String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WallpaperViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var liveActive by remember(context) { mutableStateOf(isFactsWallpaperActive(context)) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                liveActive = isFactsWallpaperActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text(stringResource(R.string.wallpaper_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            WallpaperUiState.Loading -> WallpaperLoading(Modifier.padding(padding))
            is WallpaperUiState.Error -> WallpaperError(
                messageRes = value.messageRes,
                modifier = Modifier.padding(padding),
            )
            is WallpaperUiState.Content -> WallpaperContent(
                state = value,
                liveActive = liveActive,
                onLaunchLiveWallpaper = onLaunchLiveWallpaper,
                onPreviewTemplate = onPreviewTemplate,
                onScheduleModeChange = viewModel::setScheduleMode,
                onIntervalChange = viewModel::setIntervalMinutes,
                onRotationOrderChange = viewModel::setRotationOrder,
                onOnlyFavoritesChange = viewModel::setOnlyFavorites,
                onSourceModeChange = viewModel::setSourceReferenceMode,
                onThemeModeChange = viewModel::setThemeMode,
                onTemplateChange = viewModel::selectTemplate,
                onNextCard = viewModel::showNextCard,
                onApplyStatic = viewModel::applyStaticWallpaper,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun WallpaperContent(
    state: WallpaperUiState.Content,
    liveActive: Boolean,
    onLaunchLiveWallpaper: () -> Unit,
    onPreviewTemplate: (String?) -> Unit,
    onScheduleModeChange: (WallpaperScheduleMode) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRotationOrderChange: (RotationOrder) -> Unit,
    onOnlyFavoritesChange: (Boolean) -> Unit,
    onSourceModeChange: (SourceReferenceMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onTemplateChange: (String) -> Unit,
    onNextCard: () -> Unit,
    onApplyStatic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val wallpaper = state.wallpaper

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_status_title),
                subtitle = if (liveActive) {
                    stringResource(R.string.wallpaper_status_active_body)
                } else {
                    stringResource(R.string.wallpaper_status_inactive_body)
                },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Icon(
                        imageVector = if (liveActive) Icons.Filled.CheckCircle
                        else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (liveActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = stringResource(
                            if (liveActive) R.string.wallpaper_status_active
                            else R.string.wallpaper_status_inactive,
                        ),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    Button(
                        onClick = onLaunchLiveWallpaper,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(
                            text = stringResource(R.string.wallpaper_launch),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                    OutlinedButton(
                        onClick = { onPreviewTemplate(wallpaper.activeTemplateId) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null)
                        Text(
                            text = stringResource(R.string.wallpaper_preview),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
                if (!liveActive) {
                    InlineBanner(
                        message = stringResource(R.string.wallpaper_inactive_banner),
                        severity = BannerSeverity.INFO,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_pool_title),
                subtitle = stringResource(
                    R.string.wallpaper_pool_summary,
                    state.poolSummary.factCount,
                    state.poolSummary.collectionCount,
                    state.poolSummary.folderCount,
                ),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MetricPill(
                        value = stringResource(R.string.wallpaper_pool_facts, state.pool.size),
                    )
                    if (wallpaper.currentFactId != null) {
                        MetricPill(value = stringResource(R.string.wallpaper_current_ready))
                    }
                }
                SettingSwitchRow(
                    title = stringResource(R.string.wallpaper_only_favorites),
                    subtitle = stringResource(R.string.wallpaper_only_favorites_helper),
                    checked = wallpaper.onlyFavoritesInPool,
                    onCheckedChange = onOnlyFavoritesChange,
                )
                OutlinedButton(
                    onClick = onNextCard,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.sm),
                    enabled = state.pool.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Text(
                        text = stringResource(R.string.wallpaper_next_card),
                        modifier = Modifier.padding(start = spacing.xs),
                    )
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_schedule_title),
                subtitle = stringResource(R.string.wallpaper_schedule_helper),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    WallpaperScheduleMode.entries.forEach { mode ->
                        FilterChip(
                            selected = wallpaper.scheduleMode == mode,
                            onClick = { onScheduleModeChange(mode) },
                            label = { Text(scheduleLabel(mode)) },
                        )
                    }
                }
                if (wallpaper.scheduleMode == WallpaperScheduleMode.INTERVAL) {
                    Text(
                        text = stringResource(R.string.wallpaper_interval_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = spacing.md),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(INTERVAL_OPTIONS) { minutes ->
                            FilterChip(
                                selected = wallpaper.intervalMinutes == minutes,
                                onClick = { onIntervalChange(minutes) },
                                label = { Text(stringResource(R.string.wallpaper_minutes, minutes)) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_order_title),
                subtitle = stringResource(R.string.wallpaper_order_helper),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    RotationOrder.entries.forEach { order ->
                        FilterChip(
                            selected = wallpaper.rotationOrder == order,
                            onClick = { onRotationOrderChange(order) },
                            label = { Text(rotationLabel(order)) },
                        )
                    }
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_look_title),
                subtitle = stringResource(R.string.wallpaper_look_helper),
            ) {
                Text(
                    text = stringResource(R.string.wallpaper_theme_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(
                            selected = wallpaper.wallpaperThemeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.wallpaper_source_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = spacing.md),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(SourceReferenceMode.entries) { mode ->
                        FilterChip(
                            selected = wallpaper.sourceReferenceMode == mode,
                            onClick = { onSourceModeChange(mode) },
                            label = { Text(sourceLabel(mode)) },
                        )
                    }
                }
                if (state.templates.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.wallpaper_template_label),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = spacing.md),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(state.templates, key = { it.id }) { template ->
                            FilterChip(
                                selected = wallpaper.activeTemplateId == template.id,
                                onClick = { onTemplateChange(template.id) },
                                label = { Text(template.name) },
                            )
                        }
                    }
                } else {
                    InlineBanner(
                        message = stringResource(R.string.wallpaper_templates_empty),
                        severity = BannerSeverity.INFO,
                        modifier = Modifier.padding(top = spacing.md),
                    )
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_static_title),
                subtitle = stringResource(R.string.wallpaper_static_helper),
            ) {
                OutlinedButton(
                    onClick = onApplyStatic,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Wallpaper, contentDescription = null)
                    Text(
                        text = stringResource(R.string.wallpaper_apply_static),
                        modifier = Modifier.padding(start = spacing.xs),
                    )
                }
            }
        }
    }
}

private fun isFactsWallpaperActive(context: Context): Boolean {
    val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
    return info.component == ComponentName(context, FactsLiveWallpaperService::class.java)
}

private val INTERVAL_OPTIONS = listOf(15, 30, 60, 120, 360, 720)

@Composable
private fun scheduleLabel(mode: WallpaperScheduleMode): String = stringResource(
    when (mode) {
        WallpaperScheduleMode.ON_UNLOCK -> R.string.wallpaper_schedule_unlock
        WallpaperScheduleMode.INTERVAL -> R.string.wallpaper_schedule_interval
        WallpaperScheduleMode.MANUAL -> R.string.wallpaper_schedule_manual
    }
)

@Composable
private fun rotationLabel(order: RotationOrder): String = stringResource(
    when (order) {
        RotationOrder.SEQUENTIAL -> R.string.wallpaper_order_sequential
        RotationOrder.RANDOM -> R.string.wallpaper_order_random
    }
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.wallpaper_theme_light
        ThemeMode.DARK -> R.string.wallpaper_theme_dark
        ThemeMode.AMOLED -> R.string.wallpaper_theme_amoled
        ThemeMode.DYNAMIC -> R.string.wallpaper_theme_dynamic
    }
)

@Composable
private fun sourceLabel(mode: SourceReferenceMode): String = stringResource(
    when (mode) {
        SourceReferenceMode.FULL -> R.string.wallpaper_source_full
        SourceReferenceMode.COMPACT -> R.string.wallpaper_source_compact
        SourceReferenceMode.PAGE_ONLY -> R.string.wallpaper_source_page
        SourceReferenceMode.HIDDEN -> R.string.wallpaper_source_hidden
    }
)

@Composable
private fun WallpaperLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun WallpaperError(messageRes: Int, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = stringResource(messageRes), style = MaterialTheme.typography.bodyLarge)
    }
}

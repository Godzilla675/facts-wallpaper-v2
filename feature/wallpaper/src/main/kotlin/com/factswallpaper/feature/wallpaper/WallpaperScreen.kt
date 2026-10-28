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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.ErrorStatePanel
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
            is WallpaperUiState.Error -> ErrorStatePanel(
                message = stringResource(value.messageRes),
                onRetry = viewModel::retry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            is WallpaperUiState.Content -> WallpaperContent(
                state = value,
                liveActive = liveActive,
                onLaunchLiveWallpaper = onLaunchLiveWallpaper,
                onPreviewTemplate = onPreviewTemplate,
                onScheduleModeChange = viewModel::setScheduleMode,
                onIntervalChange = viewModel::setIntervalMinutes,
                onTargetChange = viewModel::setWallpaperTarget,
                onRotationOrderChange = viewModel::setRotationOrder,
                onOnlyFavoritesChange = viewModel::setOnlyFavorites,
                onSourceModeChange = viewModel::setSourceReferenceMode,
                onThemeModeChange = viewModel::setThemeMode,
                onTemplateChange = viewModel::selectTemplate,
                onRotationSourceChange = viewModel::setRotationSource,
                onRotationPausedChange = viewModel::setRotationPaused,
                onQuietHoursEnabledChange = viewModel::setQuietHoursEnabled,
                onQuietHoursWindowChange = viewModel::setQuietHoursWindow,
                onBatterySaverSlowdownChange = viewModel::setBatterySaverSlowdown,
                onDailyNotificationEnabledChange = viewModel::setDailyFactNotificationEnabled,
                onDailyNotificationTimeChange = viewModel::setDailyFactNotificationTime,
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
    onTargetChange: (WallpaperTarget) -> Unit,
    onRotationOrderChange: (RotationOrder) -> Unit,
    onOnlyFavoritesChange: (Boolean) -> Unit,
    onSourceModeChange: (SourceReferenceMode) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onTemplateChange: (String) -> Unit,
    onRotationSourceChange: (RotationSource) -> Unit,
    onRotationPausedChange: (Boolean) -> Unit,
    onQuietHoursEnabledChange: (Boolean) -> Unit,
    onQuietHoursWindowChange: (Int, Int) -> Unit,
    onBatterySaverSlowdownChange: (Boolean) -> Unit,
    onDailyNotificationEnabledChange: (Boolean) -> Unit,
    onDailyNotificationTimeChange: (Int) -> Unit,
    onNextCard: () -> Unit,
    onApplyStatic: (WallpaperTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val wallpaper = state.wallpaper
    val prefs = state.preferences

    var showQuietHoursDialog by remember { mutableStateOf(false) }
    var showNotificationTimeDialog by remember { mutableStateOf(false) }

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
                title = stringResource(R.string.wallpaper_source_picker_title),
                subtitle = stringResource(R.string.wallpaper_source_picker_helper),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(RotationSource.entries) { source ->
                        FilterChip(
                            selected = prefs.rotationSource == source,
                            onClick = { onRotationSourceChange(source) },
                            label = { Text(rotationSourceLabel(source)) },
                        )
                    }
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_controls_title),
                subtitle = stringResource(R.string.wallpaper_controls_helper),
            ) {
                SettingSwitchRow(
                    title = stringResource(R.string.wallpaper_pause_rotation),
                    subtitle = stringResource(R.string.wallpaper_pause_rotation_helper),
                    checked = prefs.rotationPaused,
                    onCheckedChange = onRotationPausedChange,
                )

                SettingSwitchRow(
                    title = stringResource(R.string.wallpaper_battery_saver),
                    subtitle = stringResource(R.string.wallpaper_battery_saver_helper),
                    checked = prefs.batterySaverSlowdownEnabled,
                    onCheckedChange = onBatterySaverSlowdownChange,
                )

                val startH = prefs.quietHoursStartMinute / 60
                val startM = prefs.quietHoursStartMinute % 60
                val endH = prefs.quietHoursEndMinute / 60
                val endM = prefs.quietHoursEndMinute % 60

                SettingSwitchRow(
                    title = stringResource(R.string.wallpaper_quiet_hours),
                    subtitle = stringResource(R.string.wallpaper_quiet_hours_helper, startH, startM, endH, endM),
                    checked = prefs.quietHoursEnabled,
                    onCheckedChange = onQuietHoursEnabledChange,
                )

                if (prefs.quietHoursEnabled) {
                    OutlinedButton(
                        onClick = { showQuietHoursDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs),
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                        Text(
                            text = stringResource(R.string.wallpaper_quiet_hours_dialog_title),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
                }
            }
        }

        item {
            SectionGroupCard(
                title = stringResource(R.string.wallpaper_notification_title),
                subtitle = stringResource(R.string.wallpaper_notification_helper),
            ) {
                val notifH = prefs.dailyFactNotificationTimeMinutes / 60
                val notifM = prefs.dailyFactNotificationTimeMinutes % 60

                SettingSwitchRow(
                    title = stringResource(R.string.wallpaper_notification_toggle),
                    subtitle = stringResource(R.string.wallpaper_notification_time, notifH, notifM),
                    checked = prefs.dailyFactNotificationEnabled,
                    onCheckedChange = onDailyNotificationEnabledChange,
                )

                if (prefs.dailyFactNotificationEnabled) {
                    OutlinedButton(
                        onClick = { showNotificationTimeDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.xs),
                    ) {
                        Icon(Icons.Filled.Notifications, contentDescription = null)
                        Text(
                            text = stringResource(R.string.wallpaper_notification_dialog_title),
                            modifier = Modifier.padding(start = spacing.xs),
                        )
                    }
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
                Text(
                    text = stringResource(R.string.wallpaper_target_label),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    modifier = Modifier.padding(top = spacing.xs),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    listOf(
                        WallpaperTarget.HOME to R.string.wallpaper_target_home,
                        WallpaperTarget.LOCK to R.string.wallpaper_target_lock,
                        WallpaperTarget.BOTH to R.string.wallpaper_target_both,
                    ).forEach { (target, labelRes) ->
                        FilterChip(
                            selected = wallpaper.target == target,
                            onClick = { onTargetChange(target) },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }

                if (wallpaper.target != WallpaperTarget.LIVE && wallpaper.scheduleMode == WallpaperScheduleMode.ON_UNLOCK) {
                    InlineBanner(
                        message = stringResource(R.string.wallpaper_static_unlock_disabled),
                        severity = BannerSeverity.WARNING,
                        modifier = Modifier.padding(top = spacing.sm),
                    )
                }

                InlineBanner(
                    message = stringResource(R.string.wallpaper_lock_oem_note),
                    severity = BannerSeverity.INFO,
                    modifier = Modifier.padding(top = spacing.sm),
                )

                OutlinedButton(
                    onClick = {
                        val target = if (wallpaper.target == WallpaperTarget.LIVE) {
                            WallpaperTarget.BOTH
                        } else {
                            wallpaper.target
                        }
                        onApplyStatic(target)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = spacing.md),
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

    if (showQuietHoursDialog) {
        var startHour by remember { mutableStateOf((prefs.quietHoursStartMinute / 60).toString()) }
        var endHour by remember { mutableStateOf((prefs.quietHoursEndMinute / 60).toString()) }

        AlertDialog(
            onDismissRequest = { showQuietHoursDialog = false },
            title = { Text(stringResource(R.string.wallpaper_quiet_hours_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = startHour,
                        onValueChange = { startHour = it },
                        label = { Text(stringResource(R.string.wallpaper_quiet_hours_start_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = endHour,
                        onValueChange = { endHour = it },
                        label = { Text(stringResource(R.string.wallpaper_quiet_hours_end_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val s = (startHour.toIntOrNull() ?: 22).coerceIn(0, 23) * 60
                        val e = (endHour.toIntOrNull() ?: 7).coerceIn(0, 23) * 60
                        onQuietHoursWindowChange(s, e)
                        showQuietHoursDialog = false
                    },
                ) {
                    Text(stringResource(R.string.wallpaper_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuietHoursDialog = false }) {
                    Text(stringResource(R.string.wallpaper_dialog_cancel))
                }
            },
        )
    }

    if (showNotificationTimeDialog) {
        var hourStr by remember { mutableStateOf((prefs.dailyFactNotificationTimeMinutes / 60).toString()) }
        var minuteStr by remember { mutableStateOf((prefs.dailyFactNotificationTimeMinutes % 60).toString()) }

        AlertDialog(
            onDismissRequest = { showNotificationTimeDialog = false },
            title = { Text(stringResource(R.string.wallpaper_notification_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hourStr,
                        onValueChange = { hourStr = it },
                        label = { Text(stringResource(R.string.wallpaper_time_hour_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = minuteStr,
                        onValueChange = { minuteStr = it },
                        label = { Text(stringResource(R.string.wallpaper_time_minute_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = (hourStr.toIntOrNull() ?: 8).coerceIn(0, 23)
                        val m = (minuteStr.toIntOrNull() ?: 0).coerceIn(0, 59)
                        onDailyNotificationTimeChange(h * 60 + m)
                        showNotificationTimeDialog = false
                    },
                ) {
                    Text(stringResource(R.string.wallpaper_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationTimeDialog = false }) {
                    Text(stringResource(R.string.wallpaper_dialog_cancel))
                }
            },
        )
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
private fun rotationSourceLabel(source: RotationSource): String = stringResource(
    when (source) {
        RotationSource.ALL -> R.string.wallpaper_source_picker_all
        RotationSource.COLLECTIONS -> R.string.wallpaper_source_picker_collections
        RotationSource.FAVORITES -> R.string.wallpaper_source_picker_favorites
        RotationSource.DUE_FOR_REVIEW -> R.string.wallpaper_source_picker_due
    }
)

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.SYSTEM -> R.string.wallpaper_theme_system
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

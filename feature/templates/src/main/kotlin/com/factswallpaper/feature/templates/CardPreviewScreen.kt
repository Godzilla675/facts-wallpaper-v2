package com.factswallpaper.feature.templates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.component.BannerSeverity
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.InlineBanner
import com.factswallpaper.core.ui.component.PhoneWallpaperMockup
import com.factswallpaper.core.ui.component.SectionGroupCard
import com.factswallpaper.core.ui.component.SourceBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPreviewScreen(
    onBack: () -> Unit,
    onOpenFacts: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CardPreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.preview_back),
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = viewModel::applyWallpaper,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Filled.Wallpaper, contentDescription = null)
                        Text(
                            text = stringResource(R.string.preview_apply),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val value = state) {
            CardPreviewUiState.Loading -> PreviewLoading(Modifier.padding(padding))
            is CardPreviewUiState.Error -> PreviewError(
                messageRes = value.messageRes,
                modifier = Modifier.padding(padding),
            )
            is CardPreviewUiState.Content -> {
                if (value.selectedFact == null) {
                    EmptyStatePanel(
                        icon = Icons.Filled.Lightbulb,
                        title = stringResource(R.string.preview_empty_title),
                        message = stringResource(R.string.preview_empty_body),
                        actionLabel = stringResource(R.string.preview_open_facts),
                        onAction = onOpenFacts,
                        modifier = Modifier
                            .padding(padding)
                            .fillMaxSize(),
                    )
                } else {
                    CardPreviewContent(
                        state = value,
                        onPrevious = viewModel::previousFact,
                        onNext = viewModel::nextFact,
                        onSelectTemplate = viewModel::selectTemplate,
                        onSelectTheme = viewModel::setThemeMode,
                        onSelectSourceMode = viewModel::setSourceReferenceMode,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
    }
}

@Composable
private fun CardPreviewContent(
    state: CardPreviewUiState.Content,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectTemplate: (String) -> Unit,
    onSelectTheme: (ThemeMode) -> Unit,
    onSelectSourceMode: (SourceReferenceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val fact = state.selectedFact
    val templateName = state.templates.firstOrNull { it.id == state.selectedTemplateId }?.name
        ?: stringResource(R.string.preview_default_template)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                PhoneWallpaperMockup(
                    fact = fact,
                    templateName = templateName,
                    themeMode = state.wallpaper.wallpaperThemeMode,
                    sourceReferenceMode = state.wallpaper.sourceReferenceMode,
                    topSafeInsetFraction = state.wallpaper.topClockSafeInsetFraction,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrevious, enabled = state.pool.size > 1) {
                    Icon(
                        imageVector = Icons.Filled.ChevronLeft,
                        contentDescription = stringResource(R.string.preview_previous),
                    )
                }
                Text(
                    text = stringResource(
                        R.string.preview_fact_counter,
                        state.selectedIndex + 1,
                        state.pool.size,
                    ),
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = onNext, enabled = state.pool.size > 1) {
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = stringResource(R.string.preview_next),
                    )
                }
            }
        }
        if (fact != null) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.xs),
                ) {
                    SourceBadge(
                        bookTitle = fact.sourceBookTitle,
                        mode = state.wallpaper.sourceReferenceMode,
                        lessonTitle = fact.sourceLessonTitle,
                        page = fact.sourcePage,
                    )
                    Text(
                        text = fact.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.preview_template_section),
                subtitle = stringResource(R.string.preview_template_helper),
            ) {
                if (state.templates.isEmpty()) {
                    InlineBanner(
                        message = stringResource(R.string.preview_templates_empty),
                        severity = BannerSeverity.INFO,
                    )
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        items(state.templates, key = { it.id }) { template ->
                            FilterChip(
                                selected = template.id == state.selectedTemplateId,
                                onClick = { onSelectTemplate(template.id) },
                                label = { Text(template.name) },
                                leadingIcon = if (template.id == state.selectedTemplateId) {
                                    {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                        )
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.preview_theme_section),
                subtitle = stringResource(R.string.preview_theme_helper),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(ThemeMode.entries) { mode ->
                        FilterChip(
                            selected = state.wallpaper.wallpaperThemeMode == mode,
                            onClick = { onSelectTheme(mode) },
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
            }
        }
        item {
            SectionGroupCard(
                title = stringResource(R.string.preview_source_section),
                subtitle = stringResource(R.string.preview_source_helper),
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    items(SourceReferenceMode.entries) { mode ->
                        FilterChip(
                            selected = state.wallpaper.sourceReferenceMode == mode,
                            onClick = { onSelectSourceMode(mode) },
                            label = { Text(sourceModeLabel(mode)) },
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onNext,
                enabled = state.pool.size > 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
                Text(
                    text = stringResource(R.string.preview_next_fact),
                    modifier = Modifier.padding(start = spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.preview_theme_light
        ThemeMode.DARK -> R.string.preview_theme_dark
        ThemeMode.AMOLED -> R.string.preview_theme_amoled
        ThemeMode.DYNAMIC -> R.string.preview_theme_dynamic
    }
)

@Composable
private fun sourceModeLabel(mode: SourceReferenceMode): String = stringResource(
    when (mode) {
        SourceReferenceMode.FULL -> R.string.preview_source_full
        SourceReferenceMode.COMPACT -> R.string.preview_source_compact
        SourceReferenceMode.PAGE_ONLY -> R.string.preview_source_page
        SourceReferenceMode.HIDDEN -> R.string.preview_source_hidden
    }
)

@Composable
private fun PreviewLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun PreviewError(messageRes: Int, modifier: Modifier = Modifier) {
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

package com.factswallpaper.feature.facts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.ui.UiMessageEffect
import com.factswallpaper.core.ui.rememberHapticFeedback
import com.factswallpaper.core.ui.component.EmptyStatePanel
import com.factswallpaper.core.ui.component.SourceBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val bitmaps by viewModel.renderedBitmaps.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    UiMessageEffect(message, snackbarHostState, viewModel::onMessageShown)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.facts_study_mode)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.facts_cancel),
                        )
                    }
                },
                actions = {
                    when (val content = state) {
                        is StudyUiState.Content -> {
                            FilterChip(
                                selected = content.isDueOnly,
                                onClick = viewModel::toggleDueOnly,
                                label = {
                                    Text(
                                        stringResource(
                                            R.string.facts_study_due_only,
                                            content.totalDueCount,
                                        ),
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.FilterList,
                                        contentDescription = null,
                                    )
                                },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                        is StudyUiState.Empty -> {
                            if (content.isDueOnly) {
                                FilterChip(
                                    selected = true,
                                    onClick = viewModel::toggleDueOnly,
                                    label = { Text(stringResource(R.string.facts_study_show_all)) },
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        StudyUiState.Loading -> Unit
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (val ui = state) {
            StudyUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is StudyUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    if (ui.isDueOnly) {
                        EmptyStatePanel(
                            icon = Icons.Filled.School,
                            title = stringResource(R.string.facts_study_empty_due),
                            message = stringResource(
                                R.string.facts_count,
                                ui.totalAvailableCount,
                            ),
                            actionLabel = stringResource(R.string.facts_study_empty_due_action),
                            onAction = { viewModel.setDueOnly(false) },
                        )
                    } else {
                        EmptyStatePanel(
                            icon = Icons.Filled.School,
                            title = stringResource(R.string.facts_study_empty_all),
                            message = stringResource(R.string.facts_empty_body),
                            actionLabel = stringResource(R.string.facts_cancel),
                            onAction = onBack,
                        )
                    }
                }
            }
            is StudyUiState.Content -> {
                StudyContentView(
                    facts = ui.facts,
                    bitmaps = bitmaps,
                    sessionStats = ui.sessionStats,
                    onRecordReview = viewModel::recordReview,
                    onRestart = viewModel::resetSession,
                    onFinish = onBack,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun StudyContentView(
    facts: List<Fact>,
    bitmaps: Map<String, android.graphics.Bitmap>,
    sessionStats: StudySessionStats,
    onRecordReview: (String, Boolean) -> Unit,
    onRestart: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val haptic = rememberHapticFeedback()
    val pagerState = rememberPagerState { facts.size }
    var sessionDone by remember { mutableStateOf(false) }

    if (sessionDone) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(bottom = spacing.lg)
                    .height(64.dp)
                    .width(64.dp),
            )
            Text(
                text = stringResource(R.string.facts_study_done),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                text = stringResource(
                    R.string.facts_study_session_summary,
                    sessionStats.gotItCount,
                    sessionStats.againCount,
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                OutlinedButton(
                    onClick = {
                        sessionDone = false
                        onRestart()
                        scope.launch { pagerState.scrollToPage(0) }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(stringResource(R.string.facts_study_restart))
                }
                Button(
                    onClick = onFinish,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.facts_study_finish))
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(vertical = spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top Progress Indicator
            val progress = (pagerState.currentPage + 1f) / facts.size.toFloat()
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(
                        R.string.facts_study_card_counter,
                        pagerState.currentPage + 1,
                        facts.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.facts_study_tap_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(spacing.sm))

            // Swipeable HorizontalPager
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                val fact = facts[page]
                StudyCardItem(
                    fact = fact,
                    bitmap = bitmaps[fact.id],
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.lg),
                )
            }

            Spacer(modifier = Modifier.height(spacing.md))

            // Action Buttons
            val currentFact = facts.getOrNull(pagerState.currentPage)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg),
                horizontalArrangement = Arrangement.spacedBy(spacing.md),
            ) {
                OutlinedButton(
                    onClick = {
                        haptic()
                        if (currentFact != null) {
                            onRecordReview(currentFact.id, false)
                            if (pagerState.currentPage < facts.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                sessionDone = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(stringResource(R.string.facts_study_again))
                }

                Button(
                    onClick = {
                        haptic()
                        if (currentFact != null) {
                            onRecordReview(currentFact.id, true)
                            if (pagerState.currentPage < facts.size - 1) {
                                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                sessionDone = true
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(spacing.xs))
                    Text(stringResource(R.string.facts_study_got_it))
                }
            }
        }
    }
}

@Composable
private fun StudyCardItem(
    fact: Fact,
    bitmap: android.graphics.Bitmap?,
    modifier: Modifier = Modifier,
) {
    val spacing = LocalSpacing.current
    var isFlipped by remember(fact.id) { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlip",
    )

    val density = LocalDensity.current.density

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { isFlipped = !isFlipped }
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            },
        contentAlignment = Alignment.Center,
    ) {
        if (rotation <= 90f) {
            // FRONT: Rendered Card Image
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = fact.text,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(spacing.xl),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.padding(bottom = spacing.md))
                            Text(
                                text = fact.text,
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        } else {
            // BACK: Card Details & Spaced Repetition stats
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .graphicsLayer { rotationY = 180f },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(spacing.md),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.facts_study_details_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        SourceBadge(
                            bookTitle = fact.sourceBookTitle,
                            mode = SourceReferenceMode.COMPACT,
                            lessonTitle = fact.sourceLessonTitle,
                            page = fact.sourcePage,
                        )
                    }

                    Text(
                        text = fact.text,
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    if (!fact.latex.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = fact.latex!!,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = FontFamily.Monospace,
                                ),
                                modifier = Modifier.padding(spacing.md),
                            )
                        }
                    }

                    if (fact.tags.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            fact.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag) },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f, fill = false))

                    // Spaced Repetition Section
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(spacing.md)) {
                            Text(
                                text = "Spaced Repetition",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                            val dueText = when {
                                fact.nextReviewDueEpochMs == null -> stringResource(R.string.facts_study_not_scheduled)
                                fact.isDueForReview -> stringResource(R.string.facts_study_due_now)
                                else -> {
                                    val dateStr = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                        .format(Date(fact.nextReviewDueEpochMs!!))
                                    stringResource(R.string.facts_study_next_due_label, dateStr)
                                }
                            }
                            Text(
                                text = dueText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (fact.isDueForReview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                text = "Reviews: ${fact.reviewGotItCount} got it \u00B7 ${fact.reviewAgainCount} review again",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

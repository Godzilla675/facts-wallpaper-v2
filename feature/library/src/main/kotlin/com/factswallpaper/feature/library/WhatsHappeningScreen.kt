package com.factswallpaper.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsHappeningScreen(
    onBack: () -> Unit,
    onOpenDocument: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: WhatsHappeningViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spacing = LocalSpacing.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.import_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = viewModel::pauseAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null)
                        Spacer(Modifier.padding(spacing.xxs))
                        Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_pause_all))
                    }
                    OutlinedButton(
                        onClick = viewModel::resumeAll,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.padding(spacing.xxs))
                        Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_resume_all))
                    }
                }
            }

            if (!state.anyActive) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = spacing.xl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(spacing.sm),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = spacing.sm),
                        )
                        Text(
                            text = stringResource(com.factswallpaper.core.ui.R.string.whats_happening_no_jobs_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(com.factswallpaper.core.ui.R.string.whats_happening_no_jobs_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (state.runningJobs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(com.factswallpaper.core.ui.R.string.whats_happening_running_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                items(state.runningJobs, key = { it.documentId }) { job ->
                    JobCard(
                        job = job,
                        onRetry = { viewModel.retry(job.documentId) },
                        onCancel = { viewModel.cancel(job.documentId) },
                        onOpen = onOpenDocument?.let { { it(job.documentId) } },
                    )
                }
            }

            if (state.failedJobs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(com.factswallpaper.core.ui.R.string.whats_happening_failed_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                items(state.failedJobs, key = { it.documentId }) { job ->
                    JobCard(
                        job = job,
                        onRetry = { viewModel.retry(job.documentId) },
                        onCancel = { viewModel.cancel(job.documentId) },
                        onOpen = onOpenDocument?.let { { it(job.documentId) } },
                    )
                }
            }

            item {
                Spacer(Modifier.height(spacing.xl))
            }
        }
    }
}

@Composable
private fun JobCard(
    job: ActiveJobItem,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onOpen: (() -> Unit)? = null,
) {
    val spacing = LocalSpacing.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (job.isFailed) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(
                text = job.title,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = job.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (job.isFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (job.progress != null && job.progress in 0f..1f) {
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xs),
                )
            } else if (job.isRunning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xs),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onOpen != null) {
                    TextButton(onClick = onOpen) {
                        Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_open_document))
                    }
                }
                if (job.isFailed || job.isPaused) {
                    TextButton(onClick = onRetry) {
                        Icon(Icons.Filled.Refresh, contentDescription = null)
                        Spacer(Modifier.padding(spacing.xxs))
                        Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_retry))
                    }
                }
                TextButton(onClick = onCancel) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Spacer(Modifier.padding(spacing.xxs))
                    Text(stringResource(com.factswallpaper.core.ui.R.string.whats_happening_cancel))
                }
            }
        }
    }
}

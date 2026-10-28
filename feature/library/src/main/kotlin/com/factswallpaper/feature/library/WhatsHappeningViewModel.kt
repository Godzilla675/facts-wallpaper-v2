package com.factswallpaper.feature.library

import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.pdf.DocumentProcessingScheduler
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ActiveJobItem(
    val documentId: String,
    val title: String,
    val statusText: String,
    val progress: Float?,
    val isRunning: Boolean,
    val isFailed: Boolean,
    val isPaused: Boolean,
)

data class WhatsHappeningUiState(
    val runningJobs: List<ActiveJobItem> = emptyList(),
    val failedJobs: List<ActiveJobItem> = emptyList(),
    val anyActive: Boolean = false,
)

@HiltViewModel
class WhatsHappeningViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val scheduler: DocumentProcessingScheduler,
    private val workManager: WorkManager,
) : FeatureViewModel() {

    private val workQuery = WorkQuery.Builder.fromStates(
        listOf(
            WorkInfo.State.RUNNING,
            WorkInfo.State.ENQUEUED,
            WorkInfo.State.BLOCKED,
            WorkInfo.State.FAILED,
        ),
    ).build()

    val uiState = combine(
        documentRepository.observeDocuments(),
        workManager.getWorkInfosFlow(workQuery),
    ) { documents, _ ->
        val running = mutableListOf<ActiveJobItem>()
        val failed = mutableListOf<ActiveJobItem>()

        documents.forEach { doc ->
            when (doc.extractionStatus) {
                ExtractionStatus.OCR_IN_PROGRESS -> {
                    running.add(
                        ActiveJobItem(
                            documentId = doc.id,
                            title = doc.title,
                            statusText = "Local OCR extraction in progress",
                            progress = doc.pipelineProgress,
                            isRunning = true,
                            isFailed = false,
                            isPaused = false,
                        ),
                    )
                }
                ExtractionStatus.OUTLINE_IN_PROGRESS -> {
                    running.add(
                        ActiveJobItem(
                            documentId = doc.id,
                            title = doc.title,
                            statusText = "Analyzing outline with AI",
                            progress = doc.pipelineProgress,
                            isRunning = true,
                            isFailed = false,
                            isPaused = false,
                        ),
                    )
                }
                ExtractionStatus.FACTS_IN_PROGRESS -> {
                    running.add(
                        ActiveJobItem(
                            documentId = doc.id,
                            title = doc.title,
                            statusText = "Generating study facts with AI",
                            progress = doc.pipelineProgress,
                            isRunning = true,
                            isFailed = false,
                            isPaused = false,
                        ),
                    )
                }
                ExtractionStatus.OCR_PAUSED -> {
                    running.add(
                        ActiveJobItem(
                            documentId = doc.id,
                            title = doc.title,
                            statusText = "OCR paused at checkpoint",
                            progress = doc.pipelineProgress,
                            isRunning = false,
                            isFailed = false,
                            isPaused = true,
                        ),
                    )
                }
                ExtractionStatus.ERROR -> {
                    failed.add(
                        ActiveJobItem(
                            documentId = doc.id,
                            title = doc.title,
                            statusText = doc.lastErrorMessage ?: "Processing failed",
                            progress = null,
                            isRunning = false,
                            isFailed = true,
                            isPaused = false,
                        ),
                    )
                }
                else -> {
                    if (doc.lastErrorMessage != null) {
                        failed.add(
                            ActiveJobItem(
                                documentId = doc.id,
                                title = doc.title,
                                statusText = doc.lastErrorMessage ?: "Error",
                                progress = null,
                                isRunning = false,
                                isFailed = true,
                                isPaused = false,
                            ),
                        )
                    }
                }
            }
        }

        WhatsHappeningUiState(
            runningJobs = running,
            failedJobs = failed,
            anyActive = running.isNotEmpty() || failed.isNotEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WhatsHappeningUiState(),
    )

    fun pauseAll() {
        viewModelScope.launch {
            scheduler.pauseAll()
        }
    }

    fun resumeAll() {
        viewModelScope.launch {
            scheduler.resumeAll()
        }
    }

    fun retry(documentId: String) {
        viewModelScope.launch {
            scheduler.resume(documentId)
        }
    }

    fun cancel(documentId: String) {
        viewModelScope.launch {
            scheduler.cancel(documentId)
        }
    }
}

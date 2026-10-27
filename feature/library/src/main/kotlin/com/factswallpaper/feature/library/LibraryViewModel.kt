package com.factswallpaper.feature.library

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.pdf.DocumentProcessingScheduler
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Error(@StringRes val messageRes: Int) : LibraryUiState
    data class Empty(val query: String) : LibraryUiState
    data class Content(
        val documents: List<Document>,
        val settings: Map<String, DocumentSettings>,
        val poolSummary: RotationPoolSummary,
        val query: String,
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    rotationPoolRepository: RotationPoolRepository,
    private val scheduler: DocumentProcessingScheduler,
) : FeatureViewModel() {

    private val query = MutableStateFlow("")
    private val retrySignal = MutableStateFlow(0)

    val uiState = retrySignal.flatMapLatest {
        combine(
            documentRepository.observeDocuments(),
            documentRepository.observeAllSettings(),
            rotationPoolRepository.observePoolSummary(),
            query,
        ) { documents, settings, summary, search ->
            val filtered = filterDocuments(documents, search)
            if (filtered.isEmpty()) {
                LibraryUiState.Empty(query = search)
            } else {
                LibraryUiState.Content(
                    documents = filtered,
                    settings = settings.associateBy { it.documentId },
                    poolSummary = summary,
                    query = search,
                )
            }
        }
            .catch { emit(LibraryUiState.Error(R.string.library_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun updateQuery(value: String) {
        query.value = value
    }

    fun clearQuery() {
        query.value = ""
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            scheduler.cancel(document.id)
            when (documentRepository.deleteDocument(document.id)) {
                is AppResult.Success -> showMessage(R.string.library_delete_success)
                is AppResult.Failure -> showMessage(R.string.library_delete_failed)
            }
        }
    }

    fun setIncludedInRotation(documentId: String, included: Boolean) {
        viewModelScope.launch {
            documentRepository.setIncludedInRotation(documentId, included)
            showMessage(R.string.library_rotation_saved)
        }
    }

    fun pauseOcr(documentId: String) {
        viewModelScope.launch {
            scheduler.pause(documentId)
            showMessage(R.string.library_ocr_paused)
        }
    }

    fun resumeOcr(documentId: String) {
        scheduler.resume(documentId)
        showMessage(R.string.library_ocr_resumed)
    }

    fun retryProcessing(documentId: String) {
        scheduler.resume(documentId)
        showMessage(R.string.library_retry_started)
    }

    private fun filterDocuments(documents: List<Document>, query: String): List<Document> {
        val needle = query.trim()
        if (needle.isEmpty()) return documents
        return documents.filter { document ->
            document.title.contains(needle, ignoreCase = true) ||
                document.sourceUri.contains(needle, ignoreCase = true)
        }
    }
}

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

enum class DocumentSortOrder {
    DATE_DESC,
    DATE_ASC,
    TITLE_ASC,
    PROGRESS,
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Error(@StringRes val messageRes: Int) : LibraryUiState
    data class Empty(val query: String) : LibraryUiState
    data class Content(
        val documents: List<Document>,
        val settings: Map<String, DocumentSettings>,
        val poolSummary: RotationPoolSummary,
        val query: String,
        val sortOrder: DocumentSortOrder = DocumentSortOrder.DATE_DESC,
        val selectedDocumentIds: Set<String> = emptySet(),
    ) : LibraryUiState
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    rotationPoolRepository: RotationPoolRepository,
    private val scheduler: DocumentProcessingScheduler,
) : FeatureViewModel() {

    private data class FilterState(
        val query: String,
        val sortOrder: DocumentSortOrder,
        val selectedIds: Set<String>,
    )

    private val query = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(DocumentSortOrder.DATE_DESC)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val retrySignal = MutableStateFlow(0)

    private val filterState = combine(query, sortOrder, selectedIds) { q, s, sel ->
        FilterState(q, s, sel)
    }

    val uiState = retrySignal.flatMapLatest {
        combine(
            documentRepository.observeDocuments(),
            documentRepository.observeAllSettings(),
            rotationPoolRepository.observePoolSummary(),
            filterState,
        ) { documents, settings, summary, filters ->
            val filtered = filterAndSortDocuments(documents, filters.query, filters.sortOrder)
            if (filtered.isEmpty() && filters.query.isNotBlank()) {
                LibraryUiState.Empty(query = filters.query)
            } else if (documents.isEmpty()) {
                LibraryUiState.Empty(query = "")
            } else {
                LibraryUiState.Content(
                    documents = filtered,
                    settings = settings.associateBy { it.documentId },
                    poolSummary = summary,
                    query = filters.query,
                    sortOrder = filters.sortOrder,
                    selectedDocumentIds = filters.selectedIds,
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

    fun setSortOrder(order: DocumentSortOrder) {
        sortOrder.value = order
    }

    fun toggleDocumentSelection(documentId: String) {
        selectedIds.update { current ->
            if (documentId in current) current - documentId else current + documentId
        }
    }

    fun selectAll(documentIds: Collection<String>) {
        selectedIds.value = documentIds.toSet()
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun batchDelete(documentIds: Set<String> = selectedIds.value) {
        if (documentIds.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            documentIds.forEach { id ->
                scheduler.cancel(id)
                if (documentRepository.deleteDocument(id) is AppResult.Failure) {
                    failed++
                }
            }
            selectedIds.value = emptySet()
            if (failed == 0) {
                showMessage(R.string.library_delete_success)
            } else {
                showMessage(R.string.library_delete_failed)
            }
        }
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

    private fun filterAndSortDocuments(
        documents: List<Document>,
        query: String,
        sort: DocumentSortOrder,
    ): List<Document> {
        val needle = query.trim()
        val filtered = if (needle.isEmpty()) {
            documents
        } else {
            documents.filter { document ->
                document.title.contains(needle, ignoreCase = true) ||
                    document.sourceUri.contains(needle, ignoreCase = true)
            }
        }
        return when (sort) {
            DocumentSortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAtEpochMs }
            DocumentSortOrder.DATE_ASC -> filtered.sortedBy { it.createdAtEpochMs }
            DocumentSortOrder.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
            DocumentSortOrder.PROGRESS -> filtered.sortedByDescending { it.pipelineProgress }
        }
    }
}

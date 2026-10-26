package com.factswallpaper.feature.facts

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FactsUiState {
    data object Loading : FactsUiState
    data class Error(@StringRes val messageRes: Int) : FactsUiState
    data class Content(
        val facts: List<Fact>,
        val documents: List<Document>,
        val tags: List<FactTag>,
        val filter: FactFilter,
    ) : FactsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FactsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val factRepository: FactRepository,
    documentRepository: DocumentRepository,
) : FeatureViewModel() {

    private val initialDocumentId: String? = savedStateHandle["documentId"]
    private val initialLessonId: String? = savedStateHandle["lessonId"]
    private val initialTag: String? = savedStateHandle["tag"]

    private val filter = MutableStateFlow(
        FactFilter(
            documentId = initialDocumentId,
            lessonId = initialLessonId,
            tag = initialTag,
        ),
    )

    val uiState = combine(
        filter.flatMapLatest { current -> factRepository.observeFacts(current) },
        documentRepository.observeDocuments(),
        factRepository.observeTags(),
        filter,
    ) { facts, documents, tags, current ->
        FactsUiState.Content(
            facts = facts,
            documents = documents,
            tags = tags,
            filter = current,
        )
    }
        .map<FactsUiState.Content, FactsUiState> { it }
        .catch { emit(FactsUiState.Error(R.string.facts_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FactsUiState.Loading,
        )

    fun updateQuery(value: String) {
        filter.update { it.copy(query = value) }
    }

    fun clearQuery() {
        filter.update { it.copy(query = "") }
    }

    fun setStatus(status: FactStatusFilter) {
        filter.update { it.copy(status = status) }
    }

    fun setTag(tag: String?) {
        filter.update { it.copy(tag = tag) }
    }

    fun setDocument(documentId: String?) {
        filter.update { it.copy(documentId = documentId) }
    }

    fun setLesson(lessonId: String?) {
        filter.update { it.copy(lessonId = lessonId) }
    }

    fun clearFilters() {
        filter.update { FactFilter() }
    }

    fun toggleActive(factId: String, active: Boolean) {
        viewModelScope.launch {
            factRepository.setActive(factId, active)
        }
    }

    fun toggleFavorite(factId: String, favorite: Boolean) {
        viewModelScope.launch {
            factRepository.setFavorite(factId, favorite)
        }
    }

    fun saveFact(
        factId: String,
        text: String,
        latex: String,
        tags: List<String>,
        templateType: CardTemplateType,
    ) {
        val content = uiState.value as? FactsUiState.Content ?: return
        val existing = content.facts.firstOrNull { it.id == factId } ?: return
        if (text.isBlank()) {
            showMessage(R.string.facts_text_required)
            return
        }
        viewModelScope.launch {
            factRepository.saveFact(
                existing.copy(
                    text = text.trim(),
                    latex = latex.trim().ifBlank { null },
                    requiresLatex = latex.isNotBlank() ||
                        templateType == CardTemplateType.FORMULA,
                    tags = tags,
                    templateType = templateType,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            showMessage(R.string.facts_saved)
        }
    }

    fun regenerate(factId: String) {
        showMessage(R.string.facts_regenerate_not_yet)
    }

    fun templateNotYet() {
        showMessage(R.string.facts_templates_not_yet)
    }
}

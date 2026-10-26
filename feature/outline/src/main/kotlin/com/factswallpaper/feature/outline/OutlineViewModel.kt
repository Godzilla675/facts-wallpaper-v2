package com.factswallpaper.feature.outline

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface OutlineUiState {
    data object Loading : OutlineUiState
    data class Error(@StringRes val messageRes: Int) : OutlineUiState
    data class Content(
        val document: Document,
        val lessons: List<Lesson>,
        val settings: DocumentSettings,
    ) : OutlineUiState
}

@HiltViewModel
class OutlineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val lessonRepository: LessonRepository,
    private val factRepository: FactRepository,
) : FeatureViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    val uiState = combine(
        documentRepository.observeDocument(documentId),
        lessonRepository.observeLessons(documentId),
        documentRepository.observeSettings(documentId),
    ) { document, lessons, settings ->
        if (document == null) {
            OutlineUiState.Error(R.string.outline_document_missing)
        } else {
            OutlineUiState.Content(
                document = document,
                lessons = lessons.sortedBy { it.sortOrder },
                settings = settings ?: defaultSettings(document),
            )
        }
    }
        .catch { emit(OutlineUiState.Error(R.string.outline_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OutlineUiState.Loading,
        )

    fun setLessonSelected(lessonId: String, selected: Boolean) {
        viewModelScope.launch {
            lessonRepository.setSelected(lessonId, selected)
        }
    }

    fun setAllSelected(selected: Boolean) {
        viewModelScope.launch {
            lessonRepository.setAllSelected(documentId, selected)
        }
    }

    fun saveLesson(lessonId: String?, title: String, summary: String, startPage: Int, endPage: Int) {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            showMessage(R.string.outline_title_required)
            return
        }
        val maxPage = content.document.pageCount.coerceAtLeast(1)
        val safeStart = startPage.coerceIn(1, maxPage)
        val safeEnd = endPage.coerceIn(safeStart, maxPage)
        viewModelScope.launch {
            val existing = lessonId?.let { id -> content.lessons.firstOrNull { it.id == id } }
            val lesson = existing?.copy(
                title = trimmedTitle,
                summary = summary.trim(),
                startPage = safeStart,
                endPage = safeEnd,
            ) ?: Lesson(
                id = UUID.randomUUID().toString(),
                documentId = documentId,
                title = trimmedTitle,
                summary = summary.trim(),
                startPage = safeStart,
                endPage = safeEnd,
                selected = true,
                sortOrder = (content.lessons.maxOfOrNull { it.sortOrder } ?: 0) + 1,
            )
            lessonRepository.upsert(lesson)
            showMessage(R.string.outline_lesson_saved)
        }
    }

    fun deleteLesson(lessonId: String) {
        viewModelScope.launch {
            lessonRepository.delete(lessonId)
            showMessage(R.string.outline_lesson_deleted)
        }
    }

    fun mergeSelectedLessons() {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val selected = content.lessons.filter { it.selected }.sortedBy { it.sortOrder }
        if (selected.size < 2) {
            showMessage(R.string.outline_merge_needs_two)
            return
        }
        viewModelScope.launch {
            val target = selected.first()
            val mergedIds = selected.drop(1).map { it.id }.toSet()
            val merged = target.copy(
                startPage = selected.minOf { it.startPage },
                endPage = selected.maxOf { it.endPage },
                summary = selected.map { it.summary }.filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" "),
                factCount = selected.sumOf { it.factCount },
            )
            lessonRepository.upsert(merged)
            factRepository.observeFacts(FactFilter(documentId = documentId)).first()
                .filter { it.lessonId in mergedIds }
                .forEach { fact ->
                    factRepository.saveFact(
                        fact.copy(lessonId = merged.id, updatedAtEpochMs = System.currentTimeMillis()),
                    )
                }
            mergedIds.forEach { id -> lessonRepository.delete(id) }
            showMessage(R.string.outline_lessons_merged)
        }
    }

    fun setDensity(density: FactDensity) {
        updateSettings { it.copy(factDensity = density) }
    }

    fun setIncludeFormulas(enabled: Boolean) {
        updateSettings { it.copy(includeFormulas = enabled) }
    }

    fun setIncludeDiagrams(enabled: Boolean) {
        updateSettings { it.copy(includeDiagrams = enabled) }
    }

    fun setAutoVerifyFormulas(enabled: Boolean) {
        updateSettings { it.copy(autoVerifyFormulas = enabled) }
    }

    fun reDetectOutline() {
        showMessage(R.string.outline_redetect_not_yet)
    }

    fun generateFacts() {
        showMessage(R.string.outline_generate_not_yet)
    }

    private fun updateSettings(transform: (DocumentSettings) -> DocumentSettings) {
        val content = uiState.value as? OutlineUiState.Content ?: return
        viewModelScope.launch {
            documentRepository.saveSettings(transform(content.settings))
            showMessage(R.string.outline_settings_saved)
        }
    }

    private fun defaultSettings(document: Document) = DocumentSettings(
        documentId = document.id,
        pageRangeEnd = document.pageCount,
    )
}

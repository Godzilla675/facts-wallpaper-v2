package com.factswallpaper.feature.outline

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import com.factswallpaper.core.ai.AiCostEstimate
import com.factswallpaper.core.ai.NetworkMonitor
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.data.ai.FactsGenerationWorker
import com.factswallpaper.core.data.ai.FactsWork
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.DocumentTextRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OutlineGenerationUi(
    val isRunning: Boolean = false,
    val phase: String = "",
    val completed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
)

enum class OutlineAction { RE_DETECT, GENERATE_FACTS }

data class CostGuardRequest(
    val action: OutlineAction,
    val estimateUsd: Double,
)

sealed interface OutlineUiState {
    data object Loading : OutlineUiState
    data class Error(@StringRes val messageRes: Int) : OutlineUiState
    data class Content(
        val document: Document,
        val lessons: List<Lesson>,
        val settings: DocumentSettings,
        val generation: OutlineGenerationUi,
        val online: Boolean,
        val estimate: AiCostEstimate?,
        val costGuardThresholdUsd: Double,
        val pendingCostGuard: CostGuardRequest?,
        val factsJobStarted: Boolean,
    ) : OutlineUiState
}

private data class OutlineBase(
    val document: Document?,
    val lessons: List<Lesson>,
    val settings: DocumentSettings?,
    val costGuardThresholdUsd: Double,
)

@HiltViewModel
class OutlineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val documentTextRepository: DocumentTextRepository,
    private val lessonRepository: LessonRepository,
    private val factRepository: FactRepository,
    private val structuredAiService: StructuredAiService,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository,
    private val workManager: WorkManager,
) : FeatureViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])

    private val estimate = MutableStateFlow<AiCostEstimate?>(null)
    private val pendingCostGuard = MutableStateFlow<CostGuardRequest?>(null)
    private val factsJobStarted = MutableStateFlow(false)

    val showCoachHint = settingsRepository.observePreferences()
        .map { !it.outlineCoachHintDismissed }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    private val workInfos = workManager.getWorkInfosForUniqueWorkFlow(
        OutlineWork.uniqueName(documentId),
    )

    private val base = combine(
        documentRepository.observeDocument(documentId),
        lessonRepository.observeLessons(documentId),
        documentRepository.observeSettings(documentId),
        settingsRepository.observePreferences(),
    ) { document, lessons, settings, preferences ->
        OutlineBase(
            document = document,
            lessons = lessons.sortedBy { it.sortOrder },
            settings = settings,
            costGuardThresholdUsd = preferences.aiCostGuardThresholdUsd,
        )
    }

    private val retrySignal = MutableStateFlow(0)

    val uiState = retrySignal.flatMapLatest {
        combine(
            base,
            workInfos,
            networkMonitor.observeOnline(),
            estimate,
            combine(pendingCostGuard, factsJobStarted) { pending, started -> pending to started },
        ) { base, infos, online, estimate, guardAndFacts ->
            val document = base.document
            if (document == null) {
                OutlineUiState.Error(R.string.outline_document_missing)
            } else {
                OutlineUiState.Content(
                    document = document,
                    lessons = base.lessons,
                    settings = base.settings ?: defaultSettings(document),
                    generation = infos.toGenerationUi(),
                    online = online,
                    estimate = estimate,
                    costGuardThresholdUsd = base.costGuardThresholdUsd,
                    pendingCostGuard = guardAndFacts.first,
                    factsJobStarted = guardAndFacts.second,
                )
            }
        }
            .catch { emit(OutlineUiState.Error(R.string.outline_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = OutlineUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    init {
        viewModelScope.launch {
            uiState
                .filterIsInstance<OutlineUiState.Content>()
                .map { content ->
                    content.lessons.filter { it.selected }
                        .map { listOf(it.id, it.startPage, it.endPage) }
                        .plus(
                            listOf(
                                listOf(
                                    content.settings.factDensity.name,
                                    content.settings.includeFormulas.toString(),
                                    content.settings.includeDiagrams.toString(),
                                    content.document.pageCount.toString(),
                                ),
                            ),
                        )
                }
                .distinctUntilChanged()
                .collectLatest { refreshEstimate() }
        }
        viewModelScope.launch {
            var lastWorkId: UUID? = null
            var lastFinished: WorkInfo.State? = null
            workInfos.collect { infos ->
                val info = infos.firstOrNull() ?: return@collect
                if (info.id != lastWorkId) {
                    lastWorkId = info.id
                    lastFinished = null
                }
                if (!info.state.isFinished) return@collect
                if (info.state == lastFinished) return@collect
                lastFinished = info.state
                when (info.state) {
                    WorkInfo.State.SUCCEEDED -> showMessage(R.string.outline_work_succeeded)
                    WorkInfo.State.FAILED -> showMessage(R.string.outline_work_failed)
                    WorkInfo.State.CANCELLED -> showMessage(R.string.outline_work_cancelled)
                    else -> Unit
                }
            }
        }
    }

    fun setLessonSelected(lessonId: String, selected: Boolean) {
        viewModelScope.launch { lessonRepository.setSelected(lessonId, selected) }
    }

    fun setAllSelected(selected: Boolean) {
        viewModelScope.launch { lessonRepository.setAllSelected(documentId, selected) }
    }

    fun setChapterSelected(chapterTitle: String, selected: Boolean) {
        val content = uiState.value as? OutlineUiState.Content ?: return
        viewModelScope.launch {
            content.lessons
                .filter { it.chapterTitle == chapterTitle }
                .forEach { lessonRepository.setSelected(it.id, selected) }
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

    fun splitLesson(lessonId: String, atPage: Int) {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val lesson = content.lessons.firstOrNull { it.id == lessonId } ?: return
        val split = OutlineLessonPolicy.split(
            lesson = lesson,
            atPage = atPage,
            firstId = lesson.id,
            secondId = UUID.randomUUID().toString(),
        )
        if (split == null) {
            showMessage(R.string.outline_split_invalid)
            return
        }
        val (first, second) = split
        viewModelScope.launch {
            lessonRepository.upsert(first)
            lessonRepository.upsert(second)
            content.lessons
                .filter { it.id != lesson.id && it.sortOrder > lesson.sortOrder }
                .sortedBy { it.sortOrder }
                .forEach { later ->
                    lessonRepository.upsert(later.copy(sortOrder = later.sortOrder + 1))
                }
            val facts = factRepository.observeFacts(FactFilter(documentId = documentId)).first()
                .filter { it.lessonId == lesson.id }
            facts.forEach { fact ->
                val target = if ((fact.sourcePage ?: first.startPage) >= atPage) second else first
                factRepository.saveFact(
                    fact.copy(lessonId = target.id, updatedAtEpochMs = System.currentTimeMillis()),
                )
            }
            showMessage(R.string.outline_lesson_split)
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
        val selected = content.lessons.filter { it.selected }
        val merged = OutlineLessonPolicy.merge(selected)
        if (merged == null) {
            showMessage(R.string.outline_merge_needs_adjacent)
            return
        }
        val mergedIds = selected.map { it.id }.toSet() - merged.id
        viewModelScope.launch {
            lessonRepository.upsert(merged)
            val facts = factRepository.observeFacts(FactFilter(documentId = documentId)).first()
            facts.filter { it.lessonId in mergedIds }.forEach { fact ->
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
        val content = uiState.value as? OutlineUiState.Content ?: return
        if (!content.online) {
            showMessage(R.string.outline_offline)
            return
        }
        viewModelScope.launch {
            val pages = pagesFor(content)
            val estimate = runCatching {
                structuredAiService.estimateOutline(
                    pages = pages,
                    pdfPageCount = content.document.pageCount,
                    manualProfileId = content.settings.preferredProviderProfileId,
                )
            }.getOrNull()
            val cost = estimate?.estimatedCostUsd ?: 0.0
            if (cost > content.costGuardThresholdUsd) {
                pendingCostGuard.value = CostGuardRequest(OutlineAction.RE_DETECT, cost)
            } else {
                enqueueOutline(content)
            }
        }
    }

    fun generateFacts() {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val selected = content.lessons.filter { it.selected }
        if (selected.isEmpty()) {
            showMessage(R.string.outline_select_one)
            return
        }
        if (!content.online) {
            showMessage(R.string.outline_offline)
            return
        }
        viewModelScope.launch {
            val total = selected.sumOf { lesson ->
                runCatching {
                    structuredAiService.estimateFacts(
                        lessonTitle = lesson.title,
                        pages = documentTextRepository.getPages(
                            documentId,
                            lesson.startPage,
                            lesson.endPage,
                        ),
                        density = content.settings.factDensity,
                        includeFormulas = content.settings.includeFormulas,
                        includeDiagrams = content.settings.includeDiagrams,
                        manualProfileId = content.settings.preferredProviderProfileId,
                    ).estimatedCostUsd
                }.getOrDefault(0.0)
            }
            if (total > content.costGuardThresholdUsd) {
                pendingCostGuard.value = CostGuardRequest(OutlineAction.GENERATE_FACTS, total)
            } else {
                enqueueFacts(content, selected)
            }
        }
    }

    fun regenerateFailedLessons() {
        viewModelScope.launch {
            val allFacts = factRepository.observeFacts(FactFilter(documentId = documentId)).first()
            val lessonsWithFacts = allFacts.mapNotNull { it.lessonId }.toSet()
            val content = uiState.value as? OutlineUiState.Content ?: return@launch
            val failedLessons = content.lessons.filter { it.id !in lessonsWithFacts }
            if (failedLessons.isEmpty()) {
                showMessage(R.string.outline_no_failed_lessons)
                return@launch
            }
            if (!content.online) {
                showMessage(R.string.outline_offline)
                return@launch
            }
            enqueueFacts(content, failedLessons)
            showMessage(R.string.outline_regenerating_failed)
        }
    }

    fun confirmCostGuard() {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val request = pendingCostGuard.value ?: return
        pendingCostGuard.value = null
        viewModelScope.launch {
            when (request.action) {
                OutlineAction.RE_DETECT -> enqueueOutline(content)
                OutlineAction.GENERATE_FACTS ->
                    enqueueFacts(content, content.lessons.filter { it.selected })
            }
        }
    }

    fun dismissCostGuard() {
        pendingCostGuard.value = null
    }

    fun dismissCoachHint() {
        viewModelScope.launch {
            settingsRepository.setOutlineCoachHintDismissed(true)
        }
    }

    fun cancelWork() {
        workManager.cancelUniqueWork(OutlineWork.uniqueName(documentId))
    }

    fun consumeFactsJobStarted() {
        factsJobStarted.value = false
    }

    private fun enqueueOutline(content: OutlineUiState.Content) {
        val request = OneTimeWorkRequestBuilder<OutlineGenerationWorker>()
            .setInputData(
                workDataOf(
                    OutlineWork.KEY_DOCUMENT_ID to documentId,
                    OutlineWork.KEY_PROFILE_ID to content.settings.preferredProviderProfileId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            OutlineWork.uniqueName(documentId),
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
        showMessage(R.string.outline_work_enqueued)
    }

    private fun enqueueFacts(content: OutlineUiState.Content, selected: List<Lesson>) {
        if (selected.isEmpty()) {
            showMessage(R.string.outline_select_one)
            return
        }
        val request = OneTimeWorkRequestBuilder<FactsGenerationWorker>()
            .setInputData(
                workDataOf(
                    FactsWork.KEY_DOCUMENT_ID to documentId,
                    FactsWork.KEY_LESSON_IDS to selected.map { it.id }.toTypedArray(),
                    FactsWork.KEY_PROFILE_ID to content.settings.preferredProviderProfileId,
                ),
            )
            .build()
        workManager.enqueueUniqueWork(
            FactsWork.uniqueName(documentId),
            androidx.work.ExistingWorkPolicy.KEEP,
            request,
        )
        factsJobStarted.value = true
    }

    private suspend fun pagesFor(content: OutlineUiState.Content) = documentTextRepository.getPages(
        documentId,
        content.settings.pageRangeStart,
        content.settings.pageRangeEnd,
    )

    private suspend fun refreshEstimate() {
        val content = uiState.value as? OutlineUiState.Content ?: return
        val selected = content.lessons.filter { it.selected }
        if (selected.isEmpty()) {
            estimate.value = null
            return
        }
        val estimates = selected.mapNotNull { lesson ->
            runCatching {
                structuredAiService.estimateFacts(
                    lessonTitle = lesson.title,
                    pages = documentTextRepository.getPages(
                        documentId,
                        lesson.startPage,
                        lesson.endPage,
                    ),
                    density = content.settings.factDensity,
                    includeFormulas = content.settings.includeFormulas,
                    includeDiagrams = content.settings.includeDiagrams,
                    manualProfileId = content.settings.preferredProviderProfileId,
                )
            }.getOrNull()
        }
        estimate.value = estimates.firstOrNull()?.copy(
            estimatedInputTokens = estimates.sumOf { it.estimatedInputTokens },
            estimatedOutputTokens = estimates.sumOf { it.estimatedOutputTokens },
            estimatedCostUsd = estimates.sumOf { it.estimatedCostUsd },
        )
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

    private fun List<WorkInfo>.toGenerationUi(): OutlineGenerationUi {
        val info = firstOrNull() ?: return OutlineGenerationUi()
        val progress = info.progress
        return OutlineGenerationUi(
            isRunning = info.state == WorkInfo.State.RUNNING ||
                info.state == WorkInfo.State.ENQUEUED ||
                info.state == WorkInfo.State.BLOCKED,
            phase = progress.getString(OutlineWork.KEY_PHASE).orEmpty(),
            completed = progress.getInt(OutlineWork.KEY_COMPLETED, 0),
            total = progress.getInt(OutlineWork.KEY_TOTAL, 0),
            message = info.outputData.getString(OutlineWork.KEY_MESSAGE),
        )
    }
}

package com.factswallpaper.feature.facts

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.factswallpaper.core.ai.NetworkMonitor
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.data.ai.FactsWork
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.DocumentTextRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.export.CardExportFormat
import com.factswallpaper.core.render.export.CardExportService
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.OutputStreamWriter
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FactsWorkUi {
    data object Idle : FactsWorkUi
    data class Running(
        val completedLessons: Int,
        val totalLessons: Int,
        val factsCreated: Int,
        val currentLesson: String?,
    ) : FactsWorkUi
    data class Failed(val message: String?) : FactsWorkUi
    data class Succeeded(val message: String?) : FactsWorkUi
}

sealed interface FactsUiState {
    data object Loading : FactsUiState
    data class Error(@StringRes val messageRes: Int) : FactsUiState
    data class Content(
        val facts: List<Fact>,
        val documents: List<Document>,
        val tags: List<FactTag>,
        val filter: FactFilter,
        val isOnline: Boolean,
        val isRegenerating: Boolean,
        val regeneratingFactId: String?,
        val generationWork: FactsWorkUi = FactsWorkUi.Idle,
    ) : FactsUiState
}

private data class FactDataTuple(
    val facts: List<Fact>,
    val documents: List<Document>,
    val tags: List<FactTag>,
    val filter: FactFilter,
)

@HiltViewModel
class FactsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val factRepository: FactRepository,
    private val documentRepository: DocumentRepository,
    private val documentTextRepository: DocumentTextRepository,
    private val structuredAiService: StructuredAiService,
    private val networkMonitor: NetworkMonitor,
    private val workManager: WorkManager,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val cardExportService: CardExportService? = null,
) : FeatureViewModel() {

    private val initialDocumentId: String? = savedStateHandle["documentId"]
    private val initialLessonId: String? = savedStateHandle["lessonId"]
    private val initialTag: String? = savedStateHandle["tag"]
    private val initialStatus: FactStatusFilter = (savedStateHandle.get<String>("status"))
        ?.let { raw -> FactStatusFilter.entries.firstOrNull { it.name == raw } }
        ?: FactStatusFilter.ALL

    private val filter = MutableStateFlow(
        FactFilter(
            documentId = initialDocumentId,
            lessonId = initialLessonId,
            tag = initialTag,
            status = initialStatus,
        ),
    )

    private val retrySignal = MutableStateFlow(0)
    private val regeneratingFactId = MutableStateFlow<String?>(null)
    private val pendingDeletedFactIds = MutableStateFlow<Set<String>>(emptySet())
    private val deleteJobs = mutableMapOf<String, Job>()

    private val workUi = filter
        .map { it.documentId }
        .distinctUntilChanged()
        .flatMapLatest { docId ->
            if (docId == null) {
                flowOf(FactsWorkUi.Idle)
            } else {
                workManager.getWorkInfosForUniqueWorkFlow(FactsWork.uniqueName(docId))
                    .map { infos ->
                        val info = infos.firstOrNull() ?: return@map FactsWorkUi.Idle
                        when (info.state) {
                            WorkInfo.State.RUNNING -> FactsWorkUi.Running(
                                completedLessons = info.progress.getInt(FactsWork.KEY_COMPLETED, 0),
                                totalLessons = info.progress.getInt(FactsWork.KEY_TOTAL, 0),
                                factsCreated = info.progress.getInt(FactsWork.KEY_FACTS_CREATED, 0),
                                currentLesson = info.progress.getString(FactsWork.KEY_CURRENT_LESSON),
                            )
                            WorkInfo.State.FAILED -> FactsWorkUi.Failed(
                                info.outputData.getString(FactsWork.KEY_MESSAGE),
                            )
                            WorkInfo.State.SUCCEEDED -> FactsWorkUi.Succeeded(
                                info.outputData.getString(FactsWork.KEY_MESSAGE),
                            )
                            else -> FactsWorkUi.Idle
                        }
                    }
            }
        }

    val uiState = retrySignal.flatMapLatest {
        combine(
            combine(
                filter.flatMapLatest { factRepository.observeFacts(it) },
                documentRepository.observeDocuments(),
                factRepository.observeTags(),
                filter,
                pendingDeletedFactIds,
            ) { facts, documents, tags, currentFilter, pendingDeleted ->
                FactDataTuple(facts.filter { it.id !in pendingDeleted }, documents, tags, currentFilter)
            },
            networkMonitor.observeOnline(),
            regeneratingFactId,
            workUi,
        ) { data, online, regenId, work ->
            FactsUiState.Content(
                facts = data.facts,
                documents = data.documents,
                tags = data.tags,
                filter = data.filter,
                isOnline = online,
                isRegenerating = regenId != null,
                regeneratingFactId = regenId,
                generationWork = work,
            )
        }
            .map<FactsUiState.Content, FactsUiState> { it }
            .catch { emit(FactsUiState.Error(R.string.facts_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FactsUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

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
        filter.update { it.copy(documentId = documentId, lessonId = null) }
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
            preRenderScheduler.enqueue()
        }
    }

    fun toggleFavorite(factId: String, favorite: Boolean) {
        viewModelScope.launch {
            factRepository.setFavorite(factId, favorite)
            preRenderScheduler.enqueue()
        }
    }

    fun recordReview(factId: String, gotIt: Boolean) {
        viewModelScope.launch {
            factRepository.recordReview(factId, gotIt)
        }
    }

    fun deleteFactWithUndo(factId: String, onUndoAvailable: (onUndo: () -> Unit) -> Unit) {
        pendingDeletedFactIds.update { it + factId }
        val job = viewModelScope.launch {
            delay(5_000)
            pendingDeletedFactIds.update { it - factId }
            factRepository.deleteFact(factId)
            deleteJobs.remove(factId)
        }
        deleteJobs[factId] = job
        onUndoAvailable {
            job.cancel()
            deleteJobs.remove(factId)
            pendingDeletedFactIds.update { it - factId }
        }
    }

    fun shareCardAsImage(fact: Fact, context: Context) {
        viewModelScope.launch {
            try {
                val sharesDir = java.io.File(context.cacheDir, "shares").apply { mkdirs() }
                val shareFile = java.io.File(sharesDir, "card_${fact.id.take(8)}.png")
                if (cardExportService != null) {
                    val bitmap = cardExportService.renderCard(fact)
                    java.io.FileOutputStream(shareFile).use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        shareFile,
                    )
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        putExtra(android.content.Intent.EXTRA_TEXT, fact.text)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                } else {
                    val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_TEXT, fact.text)
                    }
                    context.startActivity(android.content.Intent.createChooser(sendIntent, null))
                }
            } catch (e: Exception) {
                showMessage(R.string.facts_export_failed)
            }
        }
    }

    fun saveFact(
        factId: String,
        text: String,
        latex: String,
        tags: List<String>,
        templateType: CardTemplateType,
        diagramType: DiagramKind = DiagramKind.NONE,
        diagramSource: String? = null,
        sourceReferenceModeOverride: SourceReferenceMode? = null,
        sourcePage: Int? = null,
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
                    requiresLatex = latex.isNotBlank() || templateType == CardTemplateType.FORMULA,
                    tags = tags,
                    templateType = templateType,
                    diagramType = diagramType,
                    diagramSource = diagramSource?.trim()?.ifBlank { null },
                    sourceReferenceModeOverride = sourceReferenceModeOverride,
                    sourcePage = sourcePage,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
            preRenderScheduler.enqueue()
            showMessage(R.string.facts_saved)
        }
    }

    fun regenerate(factId: String, instruction: String? = null) {
        val content = uiState.value as? FactsUiState.Content ?: return
        val existing = content.facts.firstOrNull { it.id == factId } ?: return
        if (!networkMonitor.isOnline()) {
            showMessage(R.string.facts_offline)
            return
        }
        regeneratingFactId.value = factId
        viewModelScope.launch {
            try {
                val pageText = if (existing.sourcePage != null) {
                    runCatching {
                        documentTextRepository.getPages(
                            documentId = existing.documentId,
                            startPage = existing.sourcePage!!,
                            endPage = existing.sourcePage!!,
                        ).firstOrNull()?.text
                    }.getOrNull()
                } else null

                val draft = structuredAiService.regenerateFact(
                    originalText = existing.text,
                    pageContent = pageText,
                    lessonTitle = existing.sourceLessonTitle,
                    extraInstruction = instruction,
                )

                val template = runCatching {
                    CardTemplateType.valueOf(draft.templateType.uppercase())
                }.getOrDefault(existing.templateType)

                val diagram = runCatching {
                    DiagramKind.valueOf(draft.diagramType.uppercase())
                }.getOrDefault(existing.diagramType)

                factRepository.saveFact(
                    existing.copy(
                        previousText = existing.text,
                        text = draft.text,
                        latex = draft.latex ?: existing.latex,
                        requiresLatex = draft.requiresLatex || (draft.latex != null),
                        templateType = template,
                        diagramType = diagram,
                        diagramSource = draft.diagramSource ?: existing.diagramSource,
                        regenerationInstruction = instruction,
                        tags = if (draft.tags.isNotEmpty()) draft.tags else existing.tags,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    ),
                )
                preRenderScheduler.enqueue()
                showMessage(R.string.facts_regenerated)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                showMessage(R.string.facts_regenerate_failed)
            } finally {
                regeneratingFactId.value = null
            }
        }
    }

    fun exportFacts(
        destinationUri: Uri,
        context: Context,
        format: CardExportFormat = CardExportFormat.MARKDOWN,
    ) {
        val content = uiState.value as? FactsUiState.Content ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openOutputStream(destinationUri, "wt")?.use { out ->
                    if (cardExportService != null) {
                        cardExportService.export(content.facts, format, out)
                    } else {
                        OutputStreamWriter(out).use { writer ->
                            writer.write("# Facts Study Export\n\n")
                            writer.write("Exported ${content.facts.size} study cards.\n\n")
                            content.facts.forEachIndexed { idx, fact ->
                                writer.write("## ${idx + 1}. [${fact.templateType}] ${fact.sourceLessonTitle ?: "Study Card"}\n\n")
                                writer.write("${fact.text}\n\n")
                                if (!fact.latex.isNullOrBlank()) {
                                    writer.write("```latex\n${fact.latex}\n```\n\n")
                                }
                                if (fact.tags.isNotEmpty()) {
                                    writer.write("Tags: ${fact.tags.joinToString(", ")}\n\n")
                                }
                                writer.write("Reviews: ${fact.reviewGotItCount} got it / ${fact.reviewAgainCount} again\n\n---\n\n")
                            }
                        }
                    }
                } ?: error("Failed to open output stream")
            }
            if (result.isSuccess) {
                showMessage(R.string.facts_export_success)
            } else {
                showMessage(R.string.facts_export_failed)
            }
        }
    }

    fun cancelGeneration() {
        val docId = filter.value.documentId ?: return
        workManager.cancelUniqueWork(FactsWork.uniqueName(docId))
        showMessage(R.string.facts_work_cancelled)
    }
}

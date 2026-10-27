package com.factswallpaper.feature.facts

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.render.card.CardBitmapRenderer
import com.factswallpaper.core.render.card.CardRenderOptions
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSeeder
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudySessionStats(
    val gotItCount: Int = 0,
    val againCount: Int = 0,
)

sealed interface StudyUiState {
    data object Loading : StudyUiState
    data class Empty(
        val isDueOnly: Boolean,
        val totalAvailableCount: Int,
    ) : StudyUiState
    data class Content(
        val facts: List<Fact>,
        val isDueOnly: Boolean,
        val totalDueCount: Int,
        val totalAvailableCount: Int,
        val sessionStats: StudySessionStats = StudySessionStats(),
    ) : StudyUiState
}

@HiltViewModel
class StudyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val factRepository: FactRepository,
    private val collectionRepository: CollectionRepository,
    private val templateRepository: TemplateRepository,
    private val templateSeeder: CardTemplateSeeder,
    private val renderer: CardBitmapRenderer,
    private val dispatchers: FactsDispatchers,
) : FeatureViewModel() {

    private val documentId: String? = savedStateHandle["documentId"]
    private val collectionId: String? = savedStateHandle["collectionId"]
    private val tag: String? = savedStateHandle["tag"]
    private val initialDueOnly: Boolean = savedStateHandle["dueOnly"] ?: false

    private val isDueOnly = MutableStateFlow(initialDueOnly)
    private val sessionStats = MutableStateFlow(StudySessionStats())
    private val _renderedBitmaps = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val renderedBitmaps: StateFlow<Map<String, Bitmap>> = _renderedBitmaps.asStateFlow()

    val uiState: StateFlow<StudyUiState> = combine(
        factRepository.observeFacts(FactFilter()),
        collectionRepository.observeCollections(),
        isDueOnly,
        sessionStats,
    ) { allFacts, _, dueOnly, stats ->
        val candidateFacts = when {
            collectionId != null -> {
                val memberIds = collectionRepository.observeCollectionFactIds(collectionId).first()
                allFacts.filter { it.id in memberIds }
            }
            documentId != null -> allFacts.filter { it.documentId == documentId }
            tag != null -> allFacts.filter { it.tags.contains(tag) }
            else -> allFacts
        }

        val totalAvailable = candidateFacts.size
        val totalDue = candidateFacts.count { it.isDueForReview }

        val activeFacts = if (dueOnly) {
            candidateFacts.filter { it.isDueForReview }
        } else {
            candidateFacts
        }

        if (activeFacts.isEmpty()) {
            StudyUiState.Empty(
                isDueOnly = dueOnly,
                totalAvailableCount = totalAvailable,
            )
        } else {
            preloadBitmaps(activeFacts)
            StudyUiState.Content(
                facts = activeFacts,
                isDueOnly = dueOnly,
                totalDueCount = totalDue,
                totalAvailableCount = totalAvailable,
                sessionStats = stats,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StudyUiState.Loading,
    )

    private fun preloadBitmaps(facts: List<Fact>) {
        viewModelScope.launch(dispatchers.io) {
            templateSeeder.ensureSeeded()
            val allTemplates = templateRepository.getTemplates()
            val defaultTemplate = allTemplates.firstOrNull { it.isDefault }
                ?: BuiltInTemplates.byId(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
                ?: allTemplates.firstOrNull()
                ?: return@launch
            val defaultSpec = CardTemplateCodec.fromCardTemplate(defaultTemplate)
                ?: BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
                ?: return@launch

            val options = CardRenderOptions(width = 720, height = 1280)

            for (fact in facts) {
                if (_renderedBitmaps.value.containsKey(fact.id)) continue
                val template = fact.preferredTemplateId?.let { templateRepository.getTemplate(it) }
                    ?: defaultTemplate
                val spec = CardTemplateCodec.fromCardTemplate(template) ?: defaultSpec
                runCatching {
                    val outcome = renderer.render(fact, template, spec, options)
                    _renderedBitmaps.update { it + (fact.id to outcome.bitmap) }
                }
            }
        }
    }

    fun toggleDueOnly() {
        isDueOnly.update { !it }
    }

    fun setDueOnly(due: Boolean) {
        isDueOnly.value = due
    }

    fun recordReview(factId: String, gotIt: Boolean) {
        viewModelScope.launch {
            factRepository.recordReview(factId, gotIt)
            sessionStats.update { current ->
                if (gotIt) {
                    current.copy(gotItCount = current.gotItCount + 1)
                } else {
                    current.copy(againCount = current.againCount + 1)
                }
            }
        }
    }

    fun resetSession() {
        sessionStats.value = StudySessionStats()
    }
}

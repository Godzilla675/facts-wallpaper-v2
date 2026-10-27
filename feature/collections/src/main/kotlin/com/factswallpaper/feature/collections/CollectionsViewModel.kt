package com.factswallpaper.feature.collections

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import com.factswallpaper.core.ai.FactSummaryInput
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.model.UserFolder
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MembershipTargetType { COLLECTION, FOLDER }

data class MembershipEditorState(
    val targetType: MembershipTargetType,
    val targetId: String,
    val targetName: String,
    val facts: List<Fact> = emptyList(),
    val selectedFactIds: Set<String> = emptySet(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Error(@StringRes val messageRes: Int) : CollectionsUiState
    data class Content(
        val counts: SmartViewCounts,
        val poolSummary: RotationPoolSummary,
        val collections: List<Collection>,
        val folders: List<UserFolder>,
        val tags: List<FactTag>,
        val membership: MembershipEditorState? = null,
    ) : CollectionsUiState {
        val suggestions: List<Collection>
            get() = collections.filter {
                it.source == CollectionSource.AI_SUGGESTED && !it.accepted
            }
        val activeCollections: List<Collection>
            get() = collections.filter {
                it.accepted && !(it.source == CollectionSource.AI_SUGGESTED && !it.accepted)
            }
    }
}

@HiltViewModel
class CollectionsViewModel @Inject constructor(
    private val collectionRepository: CollectionRepository,
    private val factRepository: FactRepository,
    rotationPoolRepository: RotationPoolRepository,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val structuredAiService: StructuredAiService,
) : FeatureViewModel() {

    private val membership = MutableStateFlow<MembershipEditorState?>(null)
    private val retrySignal = MutableStateFlow(0)

    val uiState = retrySignal.flatMapLatest {
        combine(
            factRepository.observeSmartViewCounts(),
            rotationPoolRepository.observePoolSummary(),
            collectionRepository.observeCollections(),
            collectionRepository.observeFolders(),
            factRepository.observeTags(),
        ) { counts, pool, collections, folders, tags ->
            CollectionsUiState.Content(
                counts = counts,
                poolSummary = pool,
                collections = collections,
                folders = folders,
                tags = tags,
            )
        }
            .combine(membership) { content, editor ->
                content.copy(membership = editor)
            }
            .map<CollectionsUiState.Content, CollectionsUiState> { it }
            .catch { emit(CollectionsUiState.Error(R.string.collections_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionsUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun createCollection(name: String, description: String) {
        if (name.isBlank()) {
            showMessage(R.string.collections_name_required)
            return
        }
        viewModelScope.launch {
            collectionRepository.createCollection(name, description)
            preRenderScheduler.enqueue()
            showMessage(R.string.collections_created)
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) {
            showMessage(R.string.collections_name_required)
            return
        }
        viewModelScope.launch {
            collectionRepository.createFolder(name)
            showMessage(R.string.collections_folder_created)
        }
    }

    fun renameCollection(collection: Collection, name: String) {
        if (name.isBlank()) {
            showMessage(R.string.collections_name_required)
            return
        }
        viewModelScope.launch {
            collectionRepository.renameCollection(collection.id, name, collection.description)
            showMessage(R.string.collections_renamed)
        }
    }

    fun renameFolder(folder: UserFolder, name: String) {
        if (name.isBlank()) {
            showMessage(R.string.collections_name_required)
            return
        }
        viewModelScope.launch {
            collectionRepository.renameFolder(folder.id, name)
            showMessage(R.string.collections_renamed)
        }
    }

    fun setCollectionInRotation(collectionId: String, included: Boolean) {
        viewModelScope.launch {
            collectionRepository.setCollectionInRotation(collectionId, included)
            preRenderScheduler.enqueue()
            showMessage(R.string.collections_rotation_saved)
        }
    }

    fun setFolderInRotation(folderId: String, included: Boolean) {
        viewModelScope.launch {
            collectionRepository.setFolderInRotation(folderId, included)
            preRenderScheduler.enqueue()
            showMessage(R.string.collections_rotation_saved)
        }
    }

    fun acceptCollection(collectionId: String) {
        viewModelScope.launch {
            collectionRepository.setCollectionAccepted(collectionId, true)
            showMessage(R.string.collections_accepted)
        }
    }

    fun dismissCollection(collectionId: String) {
        viewModelScope.launch {
            when (collectionRepository.deleteCollection(collectionId)) {
                is AppResult.Success -> showMessage(R.string.collections_dismissed)
                is AppResult.Failure -> showMessage(R.string.collections_delete_failed)
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch {
            when (collectionRepository.deleteCollection(collectionId)) {
                is AppResult.Success -> {
                    preRenderScheduler.enqueue()
                    showMessage(R.string.collections_deleted)
                }
                is AppResult.Failure -> showMessage(R.string.collections_delete_failed)
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            when (collectionRepository.deleteFolder(folderId)) {
                is AppResult.Success -> {
                    preRenderScheduler.enqueue()
                    showMessage(R.string.collections_deleted)
                }
                is AppResult.Failure -> showMessage(R.string.collections_delete_failed)
            }
        }
    }

    fun openMembership(targetType: MembershipTargetType, targetId: String, targetName: String) {
        membership.value = MembershipEditorState(
            targetType = targetType,
            targetId = targetId,
            targetName = targetName,
            loading = true,
        )
        viewModelScope.launch {
            val facts = factRepository.observeFacts(FactFilter(status = FactStatusFilter.ACTIVE)).first()
            val selected = when (targetType) {
                MembershipTargetType.COLLECTION ->
                    collectionRepository.observeCollectionFactIds(targetId).first()
                MembershipTargetType.FOLDER ->
                    collectionRepository.observeFolderFactIds(targetId).first()
            }
            membership.update { current ->
                if (current?.targetId != targetId) {
                    current
                } else {
                    current.copy(
                        facts = facts,
                        selectedFactIds = selected.toSet(),
                        loading = false,
                    )
                }
            }
        }
    }

    fun toggleMembershipFact(factId: String) {
        membership.update { current ->
            current ?: return@update null
            val selected = current.selectedFactIds.toMutableSet()
            if (!selected.add(factId)) selected.remove(factId)
            current.copy(selectedFactIds = selected)
        }
    }

    fun closeMembership() {
        membership.value = null
    }

    fun saveMembership() {
        val editor = membership.value ?: return
        membership.update { it?.copy(saving = true, errorRes = null) }
        viewModelScope.launch {
            try {
                when (editor.targetType) {
                    MembershipTargetType.COLLECTION ->
                        collectionRepository.setCollectionFacts(editor.targetId, editor.selectedFactIds.toList())
                    MembershipTargetType.FOLDER ->
                        collectionRepository.setFolderFacts(editor.targetId, editor.selectedFactIds.toList())
                }
                preRenderScheduler.enqueue()
                membership.value = null
                showMessage(R.string.collections_members_saved)
            } catch (_: Throwable) {
                membership.update { it?.copy(saving = false, errorRes = R.string.collections_members_failed) }
            }
        }
    }

    fun generateAiCollections() {
        viewModelScope.launch {
            try {
                val facts = factRepository.observeFacts(FactFilter(status = FactStatusFilter.ACTIVE)).first()
                if (facts.size < 2) {
                    showMessage(R.string.collections_not_enough_facts)
                    return@launch
                }
                val content = uiState.value as? CollectionsUiState.Content
                val existingNames = content?.collections.orEmpty().map { it.name.lowercase() }.toSet()

                val summaries = facts.map { fact ->
                    FactSummaryInput(
                        id = fact.id,
                        text = fact.text,
                        tags = fact.tags,
                        lessonTitle = fact.sourceLessonTitle,
                    )
                }

                val suggestions = structuredAiService.suggestCollections(summaries)
                var created = 0
                for (item in suggestions) {
                    val name = item.name.trim()
                    if (name.lowercase() in existingNames) continue
                    collectionRepository.createSuggestedCollection(
                        name = name,
                        description = item.description,
                        factIds = item.factIds,
                    )
                    created++
                }
                if (created == 0) {
                    showMessage(R.string.collections_no_suggestions)
                } else {
                    showMessage(R.string.collections_generated, created)
                }
            } catch (e: AiException) {
                if (e.kind == AiErrorKind.INVALID_REQUEST || e.kind == AiErrorKind.AUTHENTICATION) {
                    showMessage(R.string.collections_ai_no_provider)
                } else {
                    showMessage(R.string.collections_generate_failed)
                }
            } catch (_: Throwable) {
                showMessage(R.string.collections_generate_failed)
            }
        }
    }
}

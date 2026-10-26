package com.factswallpaper.feature.collections

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.model.UserFolder
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CollectionsUiState {
    data object Loading : CollectionsUiState
    data class Error(@StringRes val messageRes: Int) : CollectionsUiState
    data class Content(
        val counts: SmartViewCounts,
        val poolSummary: RotationPoolSummary,
        val collections: List<Collection>,
        val folders: List<UserFolder>,
        val tags: List<FactTag>,
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
    factRepository: FactRepository,
    rotationPoolRepository: RotationPoolRepository,
) : FeatureViewModel() {

    val uiState = combine(
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
        .map<CollectionsUiState.Content, CollectionsUiState> { it }
        .catch { emit(CollectionsUiState.Error(R.string.collections_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CollectionsUiState.Loading,
        )

    fun createCollection(name: String, description: String) {
        if (name.isBlank()) {
            showMessage(R.string.collections_name_required)
            return
        }
        viewModelScope.launch {
            collectionRepository.createCollection(name, description)
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

    fun setCollectionInRotation(collectionId: String, included: Boolean) {
        viewModelScope.launch {
            collectionRepository.setCollectionInRotation(collectionId, included)
            showMessage(R.string.collections_rotation_saved)
        }
    }

    fun setFolderInRotation(folderId: String, included: Boolean) {
        viewModelScope.launch {
            collectionRepository.setFolderInRotation(folderId, included)
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
                is AppResult.Success -> showMessage(R.string.collections_deleted)
                is AppResult.Failure -> showMessage(R.string.collections_delete_failed)
            }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            when (collectionRepository.deleteFolder(folderId)) {
                is AppResult.Success -> showMessage(R.string.collections_deleted)
                is AppResult.Failure -> showMessage(R.string.collections_delete_failed)
            }
        }
    }

    fun generateAiCollections() {
        showMessage(R.string.collections_generate_not_yet)
    }
}

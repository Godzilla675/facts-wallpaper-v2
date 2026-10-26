package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.UserFolder
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun observeCollections(): Flow<List<Collection>>
    fun observeFolders(): Flow<List<UserFolder>>
    suspend fun createCollection(name: String, description: String = ""): Collection
    suspend fun createFolder(name: String): UserFolder
    suspend fun renameCollection(collectionId: String, name: String, description: String = "")
    suspend fun renameFolder(folderId: String, name: String)
    suspend fun createSuggestedCollection(
        name: String,
        description: String,
        factIds: List<String>,
    ): Collection
    fun observeCollectionFactIds(collectionId: String): Flow<List<String>>
    fun observeFolderFactIds(folderId: String): Flow<List<String>>
    suspend fun setCollectionFacts(collectionId: String, factIds: List<String>)
    suspend fun setFolderFacts(folderId: String, factIds: List<String>)
    suspend fun setCollectionInRotation(collectionId: String, included: Boolean)
    suspend fun setCollectionAccepted(collectionId: String, accepted: Boolean)
    suspend fun deleteCollection(collectionId: String): AppResult<Unit>
    suspend fun setFolderInRotation(folderId: String, included: Boolean)
    suspend fun deleteFolder(folderId: String): AppResult<Unit>
    suspend fun addFactsToCollection(collectionId: String, factIds: List<String>)
    suspend fun removeFactFromCollection(collectionId: String, factId: String)
    suspend fun addFactsToFolder(folderId: String, factIds: List<String>)
    suspend fun removeFactFromFolder(folderId: String, factId: String)
}

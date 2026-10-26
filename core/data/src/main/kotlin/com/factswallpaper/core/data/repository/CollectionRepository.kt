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

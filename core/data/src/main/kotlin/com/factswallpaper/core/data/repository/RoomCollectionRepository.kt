package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.database.dao.CollectionDao
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.CollectionFactCrossRef
import com.factswallpaper.core.database.entity.FolderFactCrossRef
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.UserFolder
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

@Singleton
class RoomCollectionRepository @Inject constructor(
    private val collectionDao: CollectionDao,
    private val dispatchers: FactsDispatchers,
) : CollectionRepository {

    override fun observeCollections(): Flow<List<Collection>> = combine(
        collectionDao.observeAllCollections(),
        collectionDao.observeCollectionFactCounts(),
    ) { collections, counts ->
        val countById = counts.associate { it.collectionId to it.count }
        collections.map { it.toDomain(factCount = countById[it.id] ?: 0) }
    }

    override fun observeFolders(): Flow<List<UserFolder>> = combine(
        collectionDao.observeAllFolders(),
        collectionDao.observeFolderFactCounts(),
    ) { folders, counts ->
        val countById = counts.associate { it.folderId to it.count }
        folders.map { it.toDomain(factCount = countById[it.id] ?: 0) }
    }

    override suspend fun createCollection(name: String, description: String): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name must not be blank" }
        val now = System.currentTimeMillis()
        val entity = CollectionEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            description = description.trim(),
            source = CollectionSource.USER.name,
            accepted = true,
            includeInRotation = true,
            createdAtEpochMs = now,
        )
        withContext(dispatchers.io) { collectionDao.upsertCollection(entity) }
        return entity.toDomain()
    }

    override suspend fun createFolder(name: String): UserFolder {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Folder name must not be blank" }
        val now = System.currentTimeMillis()
        val entity = UserFolderEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            createdAtEpochMs = now,
        )
        withContext(dispatchers.io) { collectionDao.upsertFolder(entity) }
        return entity.toDomain()
    }

    override suspend fun renameCollection(collectionId: String, name: String, description: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name must not be blank" }
        withContext(dispatchers.io) {
            collectionDao.renameCollection(collectionId, trimmed, description.trim())
        }
    }

    override suspend fun renameFolder(folderId: String, name: String) {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Folder name must not be blank" }
        withContext(dispatchers.io) { collectionDao.renameFolder(folderId, trimmed) }
    }

    override suspend fun createSuggestedCollection(
        name: String,
        description: String,
        factIds: List<String>,
    ): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name must not be blank" }
        val entity = CollectionEntity(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            description = description.trim(),
            source = CollectionSource.AI_SUGGESTED.name,
            accepted = false,
            includeInRotation = false,
            createdAtEpochMs = System.currentTimeMillis(),
        )
        withContext(dispatchers.io) {
            collectionDao.upsertCollection(entity)
            collectionDao.addFactsToCollection(
                factIds.mapIndexed { index, factId ->
                    CollectionFactCrossRef(collectionId = entity.id, factId = factId, sortOrder = index)
                },
            )
        }
        return entity.toDomain(factCount = factIds.size)
    }

    override fun observeCollectionFactIds(collectionId: String): Flow<List<String>> =
        collectionDao.observeFactIdsForCollection(collectionId)

    override fun observeFolderFactIds(folderId: String): Flow<List<String>> =
        collectionDao.observeFactIdsForFolder(folderId)

    override suspend fun setCollectionFacts(collectionId: String, factIds: List<String>) {
        withContext(dispatchers.io) {
            collectionDao.clearCollectionFacts(collectionId)
            if (factIds.isNotEmpty()) {
                collectionDao.addFactsToCollection(
                    factIds.mapIndexed { index, factId ->
                        CollectionFactCrossRef(
                            collectionId = collectionId,
                            factId = factId,
                            sortOrder = index,
                        )
                    },
                )
            }
        }
    }

    override suspend fun setFolderFacts(folderId: String, factIds: List<String>) {
        withContext(dispatchers.io) {
            collectionDao.clearFolderFacts(folderId)
            if (factIds.isNotEmpty()) {
                collectionDao.addFactsToFolder(
                    factIds.map { FolderFactCrossRef(folderId = folderId, factId = it) },
                )
            }
        }
    }

    override suspend fun setCollectionInRotation(collectionId: String, included: Boolean) {
        withContext(dispatchers.io) { collectionDao.setCollectionInRotation(collectionId, included) }
    }

    override suspend fun setCollectionAccepted(collectionId: String, accepted: Boolean) {
        withContext(dispatchers.io) { collectionDao.setCollectionAccepted(collectionId, accepted) }
    }

    override suspend fun deleteCollection(collectionId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                collectionDao.deleteCollection(collectionId)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t))
            }
        }

    override suspend fun setFolderInRotation(folderId: String, included: Boolean) {
        withContext(dispatchers.io) { collectionDao.setFolderInRotation(folderId, included) }
    }

    override suspend fun deleteFolder(folderId: String): AppResult<Unit> =
        withContext(dispatchers.io) {
            try {
                collectionDao.deleteFolder(folderId)
                AppResult.Success(Unit)
            } catch (t: Throwable) {
                AppResult.Failure(AppError.Storage(t))
            }
        }

    override suspend fun addFactsToCollection(collectionId: String, factIds: List<String>) {
        if (factIds.isEmpty()) return
        withContext(dispatchers.io) {
            collectionDao.addFactsToCollection(
                factIds.mapIndexed { index, factId ->
                    CollectionFactCrossRef(collectionId = collectionId, factId = factId, sortOrder = index)
                }
            )
        }
    }

    override suspend fun removeFactFromCollection(collectionId: String, factId: String) {
        withContext(dispatchers.io) { collectionDao.removeFactFromCollection(collectionId, factId) }
    }

    override suspend fun addFactsToFolder(folderId: String, factIds: List<String>) {
        if (factIds.isEmpty()) return
        withContext(dispatchers.io) {
            collectionDao.addFactsToFolder(factIds.map { FolderFactCrossRef(folderId = folderId, factId = it) })
        }
    }

    override suspend fun removeFactFromFolder(folderId: String, factId: String) {
        withContext(dispatchers.io) { collectionDao.removeFactFromFolder(folderId, factId) }
    }
}

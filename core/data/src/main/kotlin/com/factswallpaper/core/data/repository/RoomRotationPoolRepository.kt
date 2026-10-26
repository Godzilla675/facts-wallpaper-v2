package com.factswallpaper.core.data.repository

import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.database.dao.CollectionDao
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.database.dao.PdfDocumentDao
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationPoolSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class RoomRotationPoolRepository @Inject constructor(
    private val factDao: FactDao,
    private val documentDao: PdfDocumentDao,
    private val collectionDao: CollectionDao,
    private val wallpaperRepository: WallpaperRepository,
) : RotationPoolRepository {

    private data class PoolSources(
        val hasExplicitSources: Boolean,
        val allowedFactIds: Set<String>,
        val includedCollectionCount: Int,
        val includedFolderCount: Int,
    )

    override fun observePool(): Flow<List<Fact>> = combine(
        factDao.observeAllActiveOrdered(),
        documentDao.observeAll(),
        observeSources(),
        wallpaperRepository.observeState(),
    ) { activeFacts, documents, sources, wallpaperState ->
        val includedDocumentIds = documents.filter { it.includedInRotation }.map { it.id }.toSet()
        var pool = activeFacts.asSequence()
        if (sources.hasExplicitSources) {
            val allowed = sources.allowedFactIds
            pool = pool.filter { it.id in allowed }
        } else {
            pool = pool.filter { it.documentId in includedDocumentIds }
        }
        if (wallpaperState.onlyFavoritesInPool) {
            pool = pool.filter { it.favorite }
        }
        pool.map { it.toDomain() }.toList()
    }

    override fun observePoolSummary(): Flow<RotationPoolSummary> = combine(
        observePool(),
        collectionDao.observeAllCollections(),
        collectionDao.observeAllFolders(),
    ) { pool, collections, folders ->
        RotationPoolSummary(
            factCount = pool.size,
            collectionCount = collections.count { it.accepted && it.includeInRotation },
            folderCount = folders.count { it.includeInRotation },
        )
    }

    private fun observeSources(): Flow<PoolSources> = combine(
        collectionDao.observeAllCollections(),
        collectionDao.observeAllFolders(),
        collectionDao.observeFactIdsForIncludedCollections(),
        collectionDao.observeFactIdsForIncludedFolders(),
    ) { collections, folders, collectionFactIds, folderFactIds ->
        val includedCollections = collections.filter { it.accepted && it.includeInRotation }
        val includedFolders = folders.filter { it.includeInRotation }
        PoolSources(
            hasExplicitSources = includedCollections.isNotEmpty() || includedFolders.isNotEmpty(),
            allowedFactIds = (collectionFactIds + folderFactIds).toSet(),
            includedCollectionCount = includedCollections.size,
            includedFolderCount = includedFolders.size,
        )
    }
}

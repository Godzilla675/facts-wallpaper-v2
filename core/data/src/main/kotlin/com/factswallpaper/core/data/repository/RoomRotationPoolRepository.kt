package com.factswallpaper.core.data.repository

import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.database.dao.CollectionDao
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.database.dao.PdfDocumentDao
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.RotationSource
import java.security.MessageDigest
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
    private val settingsRepository: SettingsRepository,
) : RotationPoolRepository {

    private data class PoolSources(
        val hasExplicitSources: Boolean,
        val allowedFactIds: Set<String>,
        val includedCollectionCount: Int,
        val includedFolderCount: Int,
    )

    override fun observePool(): Flow<List<Fact>> = combine(
        combine(
            factDao.observeAllActiveOrdered(),
            documentDao.observeAll(),
            observeSources(),
        ) { activeFacts, documents, sources ->
            Triple(activeFacts, documents, sources)
        },
        wallpaperRepository.observeState(),
        settingsRepository.observePreferences(),
    ) { (activeFacts, documents, sources), wallpaperState, preferences ->
        val includedDocumentIds = documents.filter { it.includedInRotation }.map { it.id }.toSet()
        var pool = activeFacts.asSequence()
        when (preferences.rotationSource) {
            RotationSource.ALL -> {
                pool = pool.filter { it.documentId in includedDocumentIds }
            }
            RotationSource.COLLECTIONS -> {
                if (sources.hasExplicitSources) {
                    val allowed = sources.allowedFactIds
                    pool = pool.filter { it.id in allowed }
                } else {
                    pool = pool.filter { it.documentId in includedDocumentIds }
                }
            }
            RotationSource.FAVORITES -> {
                pool = pool.filter { it.documentId in includedDocumentIds && it.favorite }
            }
            RotationSource.DUE_FOR_REVIEW -> {
                val now = System.currentTimeMillis()
                pool = pool.filter {
                    val due = it.nextReviewDueEpochMs
                    it.documentId in includedDocumentIds && (due == null || due <= now)
                }
            }
        }
        if (wallpaperState.onlyFavoritesInPool && preferences.rotationSource != RotationSource.FAVORITES) {
            pool = pool.filter { it.favorite }
        }
        val ordered = pool.map { it.toDomain() }.toList()
        if (wallpaperState.rotationOrder == RotationOrder.RANDOM) {
            // Stable SHA-256 permutation: every fact appears exactly once per cycle and the
            // order never flickers while other flows re-emit. The wallpaper controller owns
            // advancing/wrapping the index (P5).
            ordered.sortedWith(
                compareBy<Fact>({ sha256Hex(it.id) }, { it.id }),
            )
        } else {
            ordered
        }
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

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

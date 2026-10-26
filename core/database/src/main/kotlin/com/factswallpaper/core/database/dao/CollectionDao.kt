package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.CollectionFactCrossRef
import com.factswallpaper.core.database.entity.FolderFactCrossRef
import com.factswallpaper.core.database.entity.UserFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {

    @Query("SELECT * FROM collections ORDER BY createdAtEpochMs DESC")
    abstract fun observeAllCollections(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :collectionId")
    abstract suspend fun getCollection(collectionId: String): CollectionEntity?

    @Query("SELECT * FROM user_folders ORDER BY createdAtEpochMs DESC")
    abstract fun observeAllFolders(): Flow<List<UserFolderEntity>>

    @Query("SELECT * FROM user_folders WHERE id = :folderId")
    abstract suspend fun getFolder(folderId: String): UserFolderEntity?

    @Query("SELECT factId FROM collection_fact_cross_ref WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    abstract fun observeFactIdsForCollection(collectionId: String): Flow<List<String>>

    @Query("SELECT factId FROM folder_fact_cross_ref WHERE folderId = :folderId")
    abstract fun observeFactIdsForFolder(folderId: String): Flow<List<String>>

    @Query(
        "SELECT cf.factId FROM collection_fact_cross_ref cf " +
            "JOIN collections c ON c.id = cf.collectionId " +
            "WHERE c.includeInRotation = 1 AND c.accepted = 1"
    )
    abstract fun observeFactIdsForIncludedCollections(): Flow<List<String>>

    @Query(
        "SELECT ff.factId FROM folder_fact_cross_ref ff " +
            "JOIN user_folders f ON f.id = ff.folderId " +
            "WHERE f.includeInRotation = 1"
    )
    abstract fun observeFactIdsForIncludedFolders(): Flow<List<String>>

    @Query(
        "SELECT c.id AS collectionId, COUNT(cf.factId) AS count FROM collections c " +
            "LEFT JOIN collection_fact_cross_ref cf ON cf.collectionId = c.id " +
            "GROUP BY c.id"
    )
    abstract fun observeCollectionFactCounts(): Flow<List<CollectionFactCount>>

    @Query(
        "SELECT f.id AS folderId, COUNT(ff.factId) AS count FROM user_folders f " +
            "LEFT JOIN folder_fact_cross_ref ff ON ff.folderId = f.id " +
            "GROUP BY f.id"
    )
    abstract fun observeFolderFactCounts(): Flow<List<FolderFactCount>>

    @Upsert
    abstract suspend fun upsertCollection(collection: CollectionEntity)

    @Query(
        "UPDATE collections SET includeInRotation = :included WHERE id = :collectionId"
    )
    abstract suspend fun setCollectionInRotation(collectionId: String, included: Boolean)

    @Query("UPDATE collections SET accepted = :accepted WHERE id = :collectionId")
    abstract suspend fun setCollectionAccepted(collectionId: String, accepted: Boolean)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    abstract suspend fun deleteCollection(collectionId: String)

    @Upsert
    abstract suspend fun upsertFolder(folder: UserFolderEntity)

    @Query("UPDATE user_folders SET includeInRotation = :included WHERE id = :folderId")
    abstract suspend fun setFolderInRotation(folderId: String, included: Boolean)

    @Query("DELETE FROM user_folders WHERE id = :folderId")
    abstract suspend fun deleteFolder(folderId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addFactsToCollection(refs: List<CollectionFactCrossRef>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun addFactsToFolder(refs: List<FolderFactCrossRef>)

    @Query("DELETE FROM collection_fact_cross_ref WHERE collectionId = :collectionId AND factId = :factId")
    abstract suspend fun removeFactFromCollection(collectionId: String, factId: String)

    @Query("DELETE FROM folder_fact_cross_ref WHERE folderId = :folderId AND factId = :factId")
    abstract suspend fun removeFactFromFolder(folderId: String, factId: String)
}

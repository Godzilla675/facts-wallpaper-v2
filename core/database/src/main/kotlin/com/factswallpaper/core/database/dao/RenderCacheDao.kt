package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.DiagramAssetEntity
import com.factswallpaper.core.database.entity.RenderCacheEntity

@Dao
abstract class RenderCacheDao {

    @Query("SELECT * FROM render_cache WHERE cacheKey = :cacheKey")
    abstract suspend fun getByKey(cacheKey: String): RenderCacheEntity?

    @Query("SELECT * FROM render_cache WHERE isLastKnownGood = 1 LIMIT 1")
    abstract suspend fun getLastKnownGood(): RenderCacheEntity?

    @Upsert
    abstract suspend fun upsert(entry: RenderCacheEntity)

    @Query("UPDATE render_cache SET isLastKnownGood = 0")
    abstract suspend fun clearLastKnownGood()

    @Query("UPDATE render_cache SET isLastKnownGood = 1 WHERE cacheKey = :cacheKey")
    abstract suspend fun markLastKnownGoodRow(cacheKey: String)

    @Transaction
    open suspend fun markLastKnownGood(cacheKey: String) {
        clearLastKnownGood()
        markLastKnownGoodRow(cacheKey)
    }

    @Query("DELETE FROM render_cache WHERE cacheKey = :cacheKey")
    abstract suspend fun deleteByKey(cacheKey: String)

    @Query("DELETE FROM render_cache")
    abstract suspend fun deleteAll()

    @Query("DELETE FROM diagram_assets")
    abstract suspend fun deleteAllDiagramAssets()

    @Upsert
    abstract suspend fun upsertDiagramAsset(asset: DiagramAssetEntity)

    @Query("SELECT * FROM diagram_assets WHERE contentHash = :contentHash LIMIT 1")
    abstract suspend fun getDiagramByHash(contentHash: String): DiagramAssetEntity?
}

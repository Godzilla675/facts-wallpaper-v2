package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.factswallpaper.core.database.entity.WallpaperStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WallpaperStateDao {

    @Query("SELECT * FROM wallpaper_state WHERE id = 1")
    fun observe(): Flow<WallpaperStateEntity?>

    @Query("SELECT * FROM wallpaper_state WHERE id = 1")
    suspend fun get(): WallpaperStateEntity?

    @Upsert
    suspend fun upsert(state: WallpaperStateEntity)

    @Query(
        "UPDATE wallpaper_state SET currentFactId = :factId, currentIndex = :index, " +
            "lastRotatedAtEpochMs = :nowEpochMs WHERE id = 1"
    )
    suspend fun setCurrentFact(factId: String?, index: Int, nowEpochMs: Long)
}

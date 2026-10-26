package com.factswallpaper.core.data.repository

import com.factswallpaper.core.model.WallpaperState
import kotlinx.coroutines.flow.Flow

interface WallpaperRepository {
    fun observeState(): Flow<WallpaperState>
    suspend fun getState(): WallpaperState
    suspend fun ensureInitialized()
    suspend fun updateState(transform: (WallpaperState) -> WallpaperState)
    suspend fun setCurrentFact(factId: String?, index: Int, nowEpochMs: Long = System.currentTimeMillis())
}

package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.data.mapper.toEntity
import com.factswallpaper.core.database.dao.WallpaperStateDao
import com.factswallpaper.core.model.WallpaperState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RoomWallpaperRepository @Inject constructor(
    private val wallpaperStateDao: WallpaperStateDao,
    private val dispatchers: FactsDispatchers,
) : WallpaperRepository {

    private val writeMutex = Mutex()

    override fun observeState(): Flow<WallpaperState> =
        wallpaperStateDao.observe().map { it?.toDomain() ?: WallpaperState() }

    override suspend fun getState(): WallpaperState =
        withContext(dispatchers.io) { wallpaperStateDao.get()?.toDomain() ?: WallpaperState() }

    override suspend fun ensureInitialized() {
        writeMutex.withLock {
            withContext(dispatchers.io) {
                if (wallpaperStateDao.get() == null) {
                    wallpaperStateDao.upsert(WallpaperState().toEntity())
                }
            }
        }
    }

    override suspend fun updateState(transform: (WallpaperState) -> WallpaperState) {
        writeMutex.withLock {
            withContext(dispatchers.io) {
                val current = wallpaperStateDao.get()?.toDomain() ?: WallpaperState()
                wallpaperStateDao.upsert(transform(current).copy(id = 1).toEntity())
            }
        }
    }

    override suspend fun setCurrentFact(factId: String?, index: Int, nowEpochMs: Long) {
        writeMutex.withLock {
            withContext(dispatchers.io) {
                if (wallpaperStateDao.get() == null) {
                    wallpaperStateDao.upsert(WallpaperState().toEntity())
                }
                wallpaperStateDao.setCurrentFact(factId, index, nowEpochMs)
            }
        }
    }
}

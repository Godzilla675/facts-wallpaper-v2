package com.factswallpaper.core.wallpaper

import android.content.Context
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.cache.CardRenderCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class WallpaperRotationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository,
    private val rotationPoolRepository: RotationPoolRepository,
    private val renderCache: CardRenderCache,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val staticWallpaperExporter: StaticWallpaperExporter,
    private val scheduler: WallpaperRotationScheduler,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun onDeviceUnlocked(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        withContext(dispatchers.io) {
            val state = wallpaperRepository.getState()
            if (state.scheduleMode != WallpaperScheduleMode.ON_UNLOCK) return@withContext false
            if (RotationReducer.isUnlockCooldownActive(state.lastRotatedAtEpochMs, nowEpochMs)) {
                return@withContext false
            }
            advance(nowEpochMs)
            true
        }

    suspend fun onVisibilityChanged(
        visible: Boolean,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = withContext(dispatchers.io) {
        if (!visible) return@withContext false
        val state = wallpaperRepository.getState()
        if (state.scheduleMode != WallpaperScheduleMode.INTERVAL) return@withContext false
        if (RotationReducer.isIntervalElapsed(state.lastRotatedAtEpochMs, state.intervalMinutes, nowEpochMs)) {
            advance(nowEpochMs)
            true
        } else {
            false
        }
    }

    suspend fun advance(nowEpochMs: Long = System.currentTimeMillis()): WallpaperState =
        withContext(dispatchers.io) {
            val currentState = wallpaperRepository.getState()
            val pool = rotationPoolRepository.observePool().first()
            val newState = RotationReducer.advance(currentState, pool, nowEpochMs)
            wallpaperRepository.setCurrentFact(
                factId = newState.currentFactId,
                index = newState.currentIndex,
                nowEpochMs = newState.lastRotatedAtEpochMs,
            )
            preRenderScheduler.enqueue()

            if (newState.target != WallpaperTarget.LIVE && newState.currentFactId != null) {
                getCurrentOrFallbackPngPath()?.let { pngPath ->
                    staticWallpaperExporter.exportPngFile(File(pngPath), newState.target)
                }
            }
            newState
        }

    suspend fun getCurrentOrFallbackPngPath(): String? = withContext(dispatchers.io) {
        val state = wallpaperRepository.getState()
        val currentKey = state.currentCacheKey
        if (currentKey != null) {
            val card = renderCache.find(currentKey)
            if (card != null && card.pngFile.isFile && card.pngFile.length() > 0L) {
                return@withContext card.pngFile.absolutePath
            }
        }

        // Current card is not cached: request pre-render asynchronously
        preRenderScheduler.enqueue()

        // Fall back to last known good bitmap
        val lastGood = renderCache.getLastKnownGood()
        if (lastGood != null && lastGood.pngFile.isFile && lastGood.pngFile.length() > 0L) {
            return@withContext lastGood.pngFile.absolutePath
        }

        null
    }
}

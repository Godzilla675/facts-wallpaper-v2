package com.factswallpaper.core.wallpaper

import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.cache.CardRenderCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

@Singleton
class WallpaperRotationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val wallpaperRepository: WallpaperRepository,
    private val rotationPoolRepository: RotationPoolRepository,
    private val settingsRepository: SettingsRepository,
    private val renderCache: CardRenderCache,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val staticWallpaperExporter: StaticWallpaperExporter,
    private val scheduler: WallpaperRotationScheduler,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun onDeviceUnlocked(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        withContext(dispatchers.io) {
            val prefs = settingsRepository.observePreferences().first()
            if (prefs.rotationPaused) return@withContext false
            if (isQuietHours(nowEpochMs, prefs.quietHoursEnabled, prefs.quietHoursStartMinute, prefs.quietHoursEndMinute)) {
                return@withContext false
            }

            val state = wallpaperRepository.getState()
            if (state.scheduleMode != WallpaperScheduleMode.ON_UNLOCK) return@withContext false

            val powerManager = context.getSystemService<PowerManager>()
            val isPowerSave = powerManager?.isPowerSaveMode == true
            val cooldown = if (isPowerSave && prefs.batterySaverSlowdownEnabled) {
                RotationReducer.UNLOCK_COOLDOWN_MS * 4
            } else {
                RotationReducer.UNLOCK_COOLDOWN_MS
            }

            if (nowEpochMs - state.lastRotatedAtEpochMs < cooldown) {
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
        val prefs = settingsRepository.observePreferences().first()
        if (prefs.rotationPaused) return@withContext false
        if (isQuietHours(nowEpochMs, prefs.quietHoursEnabled, prefs.quietHoursStartMinute, prefs.quietHoursEndMinute)) {
            return@withContext false
        }

        val state = wallpaperRepository.getState()
        if (state.scheduleMode != WallpaperScheduleMode.INTERVAL) return@withContext false

        val powerManager = context.getSystemService<PowerManager>()
        val isPowerSave = powerManager?.isPowerSaveMode == true
        val intervalMinutes = if (isPowerSave && prefs.batterySaverSlowdownEnabled) {
            state.intervalMinutes * 2
        } else {
            state.intervalMinutes
        }

        if (RotationReducer.isIntervalElapsed(state.lastRotatedAtEpochMs, intervalMinutes, nowEpochMs)) {
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

            // Notify home screen widgets that the current fact updated
            val updateIntent = Intent("com.factswallpaper.app.ACTION_FACT_UPDATED").apply {
                setPackage(context.packageName)
            }
            context.sendBroadcast(updateIntent)

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

    suspend fun getCurrentFact(): Fact? = withContext(dispatchers.io) {
        val state = wallpaperRepository.getState()
        val pool = rotationPoolRepository.observePool().first()
        val currentId = state.currentFactId
        if (currentId != null) {
            val fact = pool.firstOrNull { it.id == currentId }
            if (fact != null) return@withContext fact
        }
        pool.firstOrNull()
    }

    companion object {
        fun isQuietHours(
            nowEpochMs: Long,
            enabled: Boolean,
            startMinute: Int,
            endMinute: Int,
        ): Boolean {
            if (!enabled) return false
            val cal = Calendar.getInstance().apply { timeInMillis = nowEpochMs }
            val minuteOfDay = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            return if (startMinute <= endMinute) {
                minuteOfDay in startMinute..endMinute
            } else {
                // Window spans midnight (e.g., 22:00 to 07:00)
                minuteOfDay >= startMinute || minuteOfDay <= endMinute
            }
        }
    }
}

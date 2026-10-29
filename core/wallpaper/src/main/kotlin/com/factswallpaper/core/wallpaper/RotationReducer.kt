package com.factswallpaper.core.wallpaper

import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.WallpaperState
import java.util.Calendar

object RotationReducer {
    const val UNLOCK_COOLDOWN_MS = 15_000L

    /** On-unlock cooldown, quadrupled by the optional battery-saver slowdown (F16). */
    fun effectiveUnlockCooldownMs(
        batterySaver: Boolean,
        batterySaverSlowdownEnabled: Boolean,
    ): Long = if (batterySaver && batterySaverSlowdownEnabled) {
        UNLOCK_COOLDOWN_MS * 4
    } else {
        UNLOCK_COOLDOWN_MS
    }

    /** Interval rotation period, doubled by the optional battery-saver slowdown (F16). */
    fun effectiveIntervalMinutes(
        intervalMinutes: Int,
        batterySaver: Boolean,
        batterySaverSlowdownEnabled: Boolean,
    ): Int = if (batterySaver && batterySaverSlowdownEnabled) {
        intervalMinutes * 2
    } else {
        intervalMinutes
    }

    /**
     * Quiet-hours window in local time. Windows may span midnight
     * (for example 22:00 to 07:00), in which case both ends are inclusive.
     */
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
            minuteOfDay >= startMinute || minuteOfDay <= endMinute
        }
    }

    fun advance(
        currentState: WallpaperState,
        pool: List<Fact>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): WallpaperState {
        if (pool.isEmpty()) {
            return currentState.copy(
                currentFactId = null,
                currentIndex = 0,
                lastRotatedAtEpochMs = nowEpochMs,
            )
        }
        val nextIndex = when (currentState.rotationOrder) {
            RotationOrder.SEQUENTIAL -> {
                val currentPoolIndex = pool.indexOfFirst { it.id == currentState.currentFactId }
                if (currentPoolIndex >= 0) {
                    (currentPoolIndex + 1).mod(pool.size)
                } else {
                    (currentState.currentIndex + 1).mod(pool.size)
                }
            }
            RotationOrder.RANDOM -> {
                if (pool.size <= 1) {
                    0
                } else {
                    val currentPoolIndex = pool.indexOfFirst { it.id == currentState.currentFactId }
                    if (currentPoolIndex >= 0) {
                        (currentPoolIndex + 1).mod(pool.size)
                    } else {
                        (currentState.currentIndex + 1).mod(pool.size)
                    }
                }
            }
        }
        val nextFact = pool[nextIndex]
        return currentState.copy(
            currentFactId = nextFact.id,
            currentIndex = nextIndex,
            lastRotatedAtEpochMs = nowEpochMs,
        )
    }

    fun isUnlockCooldownActive(
        lastRotatedAtEpochMs: Long,
        nowEpochMs: Long = System.currentTimeMillis(),
        cooldownMs: Long = UNLOCK_COOLDOWN_MS,
    ): Boolean {
        if (lastRotatedAtEpochMs <= 0L) return false
        return (nowEpochMs - lastRotatedAtEpochMs) < cooldownMs
    }

    fun isIntervalElapsed(
        lastRotatedAtEpochMs: Long,
        intervalMinutes: Int,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val intervalMs = intervalMinutes.toLong() * 60_000L
        return (nowEpochMs - lastRotatedAtEpochMs) >= intervalMs
    }
}

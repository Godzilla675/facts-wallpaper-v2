package com.factswallpaper.core.wallpaper

import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.WallpaperState

object RotationReducer {
    const val UNLOCK_COOLDOWN_MS = 15_000L

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

package com.factswallpaper.core.data.repository

import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationPoolSummary
import kotlinx.coroutines.flow.Flow

/**
 * Single source of truth for the active wallpaper rotation pool (spec §11.6).
 * Consumed by the wallpaper engine, the pre-render worker and the pool counters.
 */
interface RotationPoolRepository {
    fun observePool(): Flow<List<Fact>>
    fun observePoolSummary(): Flow<RotationPoolSummary>
}

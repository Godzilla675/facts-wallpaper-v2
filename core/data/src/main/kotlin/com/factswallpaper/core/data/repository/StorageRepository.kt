package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.AppResult

data class StorageStats(
    val pdfBytes: Long = 0L,
    val cardCacheBytes: Long = 0L,
    val diagramCacheBytes: Long = 0L,
    val databaseBytes: Long = 0L,
) {
    val totalBytes: Long get() = pdfBytes + cardCacheBytes + diagramCacheBytes + databaseBytes
}

interface StorageRepository {
    suspend fun stats(): StorageStats
    suspend fun clearRenderCache(): AppResult<Unit>
    suspend fun clearDiagramCache(): AppResult<Unit>
}

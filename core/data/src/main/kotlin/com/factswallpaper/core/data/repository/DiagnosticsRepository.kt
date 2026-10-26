package com.factswallpaper.core.data.repository

import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.model.UsageSummary
import kotlinx.coroutines.flow.Flow

interface DiagnosticsRepository {
    fun observeUsage(): Flow<List<UsageEntry>>
    fun observeUsageSummary(): Flow<UsageSummary>
    fun observeRecentLogs(limit: Int = 50): Flow<List<AiRequestLogEntry>>
    suspend fun insertUsage(entry: UsageEntry)
    suspend fun insertLog(log: AiRequestLogEntry)
    suspend fun clearLogs()
}

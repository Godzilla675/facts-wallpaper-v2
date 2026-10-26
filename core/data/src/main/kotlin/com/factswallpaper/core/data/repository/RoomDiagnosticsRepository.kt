package com.factswallpaper.core.data.repository

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.mapper.toDomain
import com.factswallpaper.core.database.dao.DiagnosticsAndUsageDao
import com.factswallpaper.core.database.entity.AiRequestLogEntity
import com.factswallpaper.core.database.entity.UsageLedgerEntity
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.model.UsageSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class RoomDiagnosticsRepository @Inject constructor(
    private val diagnosticsDao: DiagnosticsAndUsageDao,
    private val dispatchers: FactsDispatchers,
) : DiagnosticsRepository {

    override fun observeUsage(): Flow<List<UsageEntry>> =
        diagnosticsDao.observeUsageLedger().map { entries -> entries.map { it.toDomain() } }

    override fun observeUsageSummary(): Flow<UsageSummary> =
        diagnosticsDao.observeUsageLedger().map { entries ->
            val zone = ZoneId.systemDefault()
            val now = Instant.now()
            val todayStart = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            val windowStart = now.minusSeconds(30L * 24 * 60 * 60)
                .atZone(zone)
                .toLocalDate()
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
            val today = entries.filter { it.timestampEpochMs >= todayStart }
            val recent = entries.filter { it.timestampEpochMs >= windowStart }
            UsageSummary(
                todayTokens = today.sumOf { it.inputTokens.toLong() + it.outputTokens.toLong() },
                todayCostUsd = today.sumOf { it.estimatedCostUsd },
                last30DaysTokens = recent.sumOf { it.inputTokens.toLong() + it.outputTokens.toLong() },
                last30DaysCostUsd = recent.sumOf { it.estimatedCostUsd },
                allTimeTokens = entries.sumOf { it.inputTokens.toLong() + it.outputTokens.toLong() },
                allTimeCostUsd = entries.sumOf { it.estimatedCostUsd },
            )
        }

    override fun observeRecentLogs(limit: Int): Flow<List<AiRequestLogEntry>> =
        diagnosticsDao.observeRecentLogs(limit).map { logs -> logs.map { it.toDomain() } }

    override suspend fun insertUsage(entry: UsageEntry) {
        withContext(dispatchers.io) {
            diagnosticsDao.insertUsage(
                UsageLedgerEntity(
                    id = entry.id,
                    providerProfileId = entry.providerProfileId,
                    providerType = entry.providerType.name,
                    modelName = entry.modelName,
                    documentId = entry.documentId,
                    taskType = entry.taskType.name,
                    inputTokens = entry.inputTokens,
                    outputTokens = entry.outputTokens,
                    estimatedCostUsd = entry.estimatedCostUsd,
                    timestampEpochMs = entry.timestampEpochMs,
                )
            )
        }
    }

    override suspend fun insertLog(log: AiRequestLogEntry) {
        withContext(dispatchers.io) {
            diagnosticsDao.insertLogAndPrune(
                AiRequestLogEntity(
                    id = log.id,
                    taskType = log.taskType.name,
                    providerProfileId = log.providerProfileId,
                    modelName = log.modelName,
                    httpStatusCode = log.httpStatusCode,
                    durationMs = log.durationMs,
                    inputTokens = log.inputTokens,
                    outputTokens = log.outputTokens,
                    repairTriggered = log.repairTriggered,
                    success = log.success,
                    redactedRequestSummary = log.redactedRequestSummary,
                    redactedResponseSnippet = log.redactedResponseSnippet,
                    errorMessage = log.errorMessage,
                    timestampEpochMs = log.timestampEpochMs,
                )
            )
        }
    }

    override suspend fun clearLogs() {
        withContext(dispatchers.io) { diagnosticsDao.clearLogs() }
    }
}

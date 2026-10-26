package com.factswallpaper.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.factswallpaper.core.database.entity.AiRequestLogEntity
import com.factswallpaper.core.database.entity.UsageLedgerEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DiagnosticsAndUsageDao {

    @Query("SELECT * FROM usage_ledger ORDER BY timestampEpochMs DESC")
    abstract fun observeUsageLedger(): Flow<List<UsageLedgerEntity>>

    @Insert
    abstract suspend fun insertUsage(entry: UsageLedgerEntity)

    @Query("SELECT * FROM ai_request_logs ORDER BY timestampEpochMs DESC LIMIT :limit")
    abstract fun observeRecentLogs(limit: Int = 50): Flow<List<AiRequestLogEntity>>

    @Insert
    abstract suspend fun insertLog(log: AiRequestLogEntity)

    @Query("DELETE FROM ai_request_logs")
    abstract suspend fun deleteAllLogs()

    @Query(
        "DELETE FROM ai_request_logs WHERE id NOT IN " +
            "(SELECT id FROM ai_request_logs ORDER BY timestampEpochMs DESC LIMIT 50)"
    )
    abstract suspend fun pruneLogs()

    @Transaction
    open suspend fun insertLogAndPrune(log: AiRequestLogEntity) {
        insertLog(log)
        pruneLogs()
    }

    @Query("DELETE FROM ai_request_logs")
    abstract suspend fun clearLogs()
}

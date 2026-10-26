package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 16. Local token and cost usage ledger. */
@Entity(
    tableName = "usage_ledger",
    indices = [Index("providerProfileId"), Index("documentId"), Index("timestampEpochMs")]
)
data class UsageLedgerEntity(
    @PrimaryKey val id: String,
    val providerProfileId: String,
    val providerType: String,
    val modelName: String,
    val documentId: String?,
    val taskType: String,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val timestampEpochMs: Long,
)

/** 17. Redacted AI diagnostic request log (capped at the most recent 50 rows). */
@Entity(
    tableName = "ai_request_logs",
    indices = [Index("timestampEpochMs")]
)
data class AiRequestLogEntity(
    @PrimaryKey val id: String,
    val taskType: String,
    val providerProfileId: String,
    val modelName: String,
    val httpStatusCode: Int?,
    val durationMs: Long,
    val inputTokens: Int,
    val outputTokens: Int,
    val repairTriggered: Boolean = false,
    val success: Boolean,
    val redactedRequestSummary: String,
    val redactedResponseSnippet: String,
    val errorMessage: String? = null,
    val timestampEpochMs: Long,
)

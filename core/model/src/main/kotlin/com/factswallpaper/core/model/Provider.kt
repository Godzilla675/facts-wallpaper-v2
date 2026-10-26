package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

@Serializable
data class CardTemplate(
    val id: String,
    val name: String,
    val engineKind: TemplateEngineKind,
    val schemaOrMarkup: String,
    val version: Int = 1,
    val builtIn: Boolean,
    val isDefault: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

@Serializable
data class ProviderProfile(
    val id: String,
    val name: String,
    val providerType: ProviderType,
    val baseUrl: String,
    val modelName: String,
    val apiVersion: String? = null,
    val deploymentName: String? = null,
    val customHeadersJson: String = "{}",
    val supportsPdfInput: Boolean = false,
    val supportsImageInput: Boolean = false,
    val supportsJsonMode: Boolean = true,
    val supportsJsonSchema: Boolean = true,
    val maxContextTokens: Int = 128_000,
    val inputUsdPerMillion: Double = 0.15,
    val outputUsdPerMillion: Double = 0.60,
    val speedTier: Int = 2,
    val qualityTier: Int = 2,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
)

@Serializable
data class WallpaperState(
    val id: Int = 1,
    val currentFactId: String? = null,
    val currentCacheKey: String? = null,
    val currentIndex: Int = 0,
    val target: WallpaperTarget = WallpaperTarget.LIVE,
    val scheduleMode: WallpaperScheduleMode = WallpaperScheduleMode.ON_UNLOCK,
    val intervalMinutes: Int = 60,
    val rotationOrder: RotationOrder = RotationOrder.SEQUENTIAL,
    val activeTemplateId: String = "builtin_minimal_text",
    val wallpaperThemeMode: ThemeMode = ThemeMode.AMOLED,
    val sourceReferenceMode: SourceReferenceMode = SourceReferenceMode.COMPACT,
    val topClockSafeInsetFraction: Float = 0.24f,
    val onlyFavoritesInPool: Boolean = false,
    val lastRotatedAtEpochMs: Long = 0L,
)

@Serializable
data class UsageEntry(
    val id: String,
    val providerProfileId: String,
    val providerType: ProviderType,
    val modelName: String,
    val documentId: String?,
    val taskType: AiTask,
    val inputTokens: Int,
    val outputTokens: Int,
    val estimatedCostUsd: Double,
    val timestampEpochMs: Long,
) {
    val totalTokens: Int get() = inputTokens + outputTokens
}

@Serializable
data class AiRequestLogEntry(
    val id: String,
    val taskType: AiTask,
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

@Serializable
data class UsageSummary(
    val todayTokens: Long = 0L,
    val todayCostUsd: Double = 0.0,
    val last30DaysTokens: Long = 0L,
    val last30DaysCostUsd: Double = 0.0,
    val allTimeTokens: Long = 0L,
    val allTimeCostUsd: Double = 0.0,
)

package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 14. AI provider profiles. Secrets live only in the Android Keystore, never here. */
@Entity(tableName = "provider_profiles")
data class ProviderProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val providerType: String,
    val baseUrl: String,
    val modelName: String,
    val apiVersion: String? = null,
    val deploymentName: String? = null,
    val customHeadersJson: String = "{}",
    val supportsPdfInput: Boolean,
    val supportsImageInput: Boolean,
    val supportsJsonMode: Boolean,
    val supportsJsonSchema: Boolean,
    val maxContextTokens: Int,
    val maxOutputTokens: Int = 8_192,
    val temperature: Double? = null,
    val topP: Double? = null,
    val reasoningEffort: String? = null,
    val rawRequestJson: String = "{}",
    val inputUsdPerMillion: Double = 0.15,
    val outputUsdPerMillion: Double = 0.60,
    val speedTier: Int = 2,
    val qualityTier: Int = 2,
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val supportsTemperature: Boolean = true,
    val maxInlinePdfBytes: Long = 20_000_000L,
    val maxDirectPdfPages: Int = 100,
    val keyStatus: String = "UNKNOWN",
)

/** 15. Wallpaper state singleton row (id = 1). */
@Entity(tableName = "wallpaper_state")
data class WallpaperStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentFactId: String?,
    val currentCacheKey: String? = null,
    val currentIndex: Int = 0,
    val target: String = "LIVE",
    val scheduleMode: String = "ON_UNLOCK",
    val intervalMinutes: Int = 60,
    val rotationOrder: String = "SEQUENTIAL",
    val activeTemplateId: String = "builtin_minimal_text",
    val wallpaperThemeMode: String = "AMOLED",
    val sourceReferenceMode: String = "COMPACT",
    val topClockSafeInsetFraction: Float = 0.24f,
    val onlyFavoritesInPool: Boolean = false,
    val lastRotatedAtEpochMs: Long = 0L,
)

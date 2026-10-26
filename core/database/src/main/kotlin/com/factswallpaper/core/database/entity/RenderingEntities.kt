package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 11. Card templates (8 built-in + user/AI custom templates). */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey val id: String,
    val name: String,
    val engineKind: String,
    val schemaOrMarkup: String,
    val version: Int = 1,
    val builtIn: Boolean,
    val isDefault: Boolean = false,
    val createdAtEpochMs: Long = 0L,
)

/** 12. Rendered diagram / formula assets. */
@Entity(
    tableName = "diagram_assets",
    indices = [Index("contentHash", unique = true)]
)
data class DiagramAssetEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val sourceMarkup: String,
    val contentHash: String,
    val renderedPngPath: String,
    val widthPx: Int,
    val heightPx: Int,
    val verified: Boolean,
    val createdAtEpochMs: Long,
)

/** 13. Pre-rendered full-card bitmap cache index. */
@Entity(
    tableName = "render_cache",
    indices = [Index("factId"), Index("createdAtEpochMs")]
)
data class RenderCacheEntity(
    @PrimaryKey val cacheKey: String,
    val factId: String,
    val templateId: String,
    val templateVersion: Int,
    val themeKey: String,
    val targetSurface: String,
    val pngPath: String,
    val widthPx: Int,
    val heightPx: Int,
    val fileSizeBytes: Long = 0L,
    val isLastKnownGood: Boolean = false,
    val createdAtEpochMs: Long,
)

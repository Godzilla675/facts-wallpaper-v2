package com.factswallpaper.core.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupManifest(
    val version: Int = 1,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "2.0.0",
    val schemaVersion: Int = 1,
    val includesRenderedAssets: Boolean = false,
    val includesOriginalPdfs: Boolean = false,
    val documentCount: Int = 0,
    val factCount: Int = 0,
)

package com.factswallpaper.core.backup

data class BackupOptions(
    val includeRenderedAssets: Boolean = false,
    val includeOriginalPdfs: Boolean = false,
)

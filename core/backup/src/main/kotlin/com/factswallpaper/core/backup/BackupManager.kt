package com.factswallpaper.core.backup

import com.factswallpaper.core.common.AppResult
import java.io.File

interface BackupManager {
    suspend fun createBackup(
        destinationFile: File,
        passphrase: String,
        options: BackupOptions = BackupOptions(),
    ): AppResult<BackupManifest>

    suspend fun validateBackup(
        sourceFile: File,
        passphrase: String,
    ): AppResult<BackupManifest>

    suspend fun restoreBackup(
        sourceFile: File,
        passphrase: String,
    ): AppResult<BackupManifest>
}

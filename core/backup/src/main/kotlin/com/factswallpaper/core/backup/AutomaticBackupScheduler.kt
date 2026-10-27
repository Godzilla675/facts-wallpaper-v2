package com.factswallpaper.core.backup

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomaticBackupScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    fun schedule(frequency: AutomaticBackupFrequency) {
        val interval = when (frequency) {
            AutomaticBackupFrequency.DAILY -> 1L
            AutomaticBackupFrequency.WEEKLY -> 7L
        }
        val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(
            interval,
            TimeUnit.DAYS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "automatic_encrypted_backup"
    }
}

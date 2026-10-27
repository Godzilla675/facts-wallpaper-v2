package com.factswallpaper.core.wallpaper

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.factswallpaper.core.datastore.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WallpaperRotationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val rotationController: WallpaperRotationController,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = settingsRepository.observePreferences().first()
            if (prefs.rotationPaused) {
                return Result.success()
            }
            if (WallpaperRotationController.isQuietHours(
                    nowEpochMs = System.currentTimeMillis(),
                    enabled = prefs.quietHoursEnabled,
                    startMinute = prefs.quietHoursStartMinute,
                    endMinute = prefs.quietHoursEndMinute,
                )
            ) {
                return Result.success()
            }

            rotationController.advance()
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

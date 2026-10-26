package com.factswallpaper.core.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.factswallpaper.core.model.WallpaperScheduleMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WallpaperRotationScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun updateSchedule(scheduleMode: WallpaperScheduleMode, intervalMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (scheduleMode == WallpaperScheduleMode.INTERVAL) {
            val minutes = intervalMinutes.coerceAtLeast(MIN_INTERVAL_MINUTES).toLong()
            val request = PeriodicWorkRequestBuilder<WallpaperRotationWorker>(minutes, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        } else {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }

    companion object {
        const val WORK_NAME = "facts_wallpaper_interval_rotation"
        const val MIN_INTERVAL_MINUTES = 15
    }
}

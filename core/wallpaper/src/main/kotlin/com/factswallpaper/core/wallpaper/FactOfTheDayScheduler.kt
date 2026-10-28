package com.factswallpaper.core.wallpaper

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FactOfTheDayScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    fun updateSchedule(enabled: Boolean, timeMinutes: Int) {
        val workManager = WorkManager.getInstance(context)
        if (!enabled) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val targetHour = (timeMinutes / 60).coerceIn(0, 23)
        val targetMinute = (timeMinutes % 60).coerceIn(0, 59)

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val initialDelayMs = (target.timeInMillis - now.timeInMillis).coerceAtLeast(0L)

        val request = PeriodicWorkRequestBuilder<FactOfTheDayWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "fact_of_the_day_periodic"
        const val TAG = "fact_of_the_day"
    }
}

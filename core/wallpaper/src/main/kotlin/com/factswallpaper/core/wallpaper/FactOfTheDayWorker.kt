package com.factswallpaper.core.wallpaper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.datastore.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class FactOfTheDayWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val rotationPoolRepository: RotationPoolRepository,
    private val settingsRepository: SettingsRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = settingsRepository.observePreferences().first()
            if (!prefs.dailyFactNotificationEnabled) {
                return Result.success()
            }

            val pool = rotationPoolRepository.observePool().first()
            if (pool.isEmpty()) {
                return Result.success()
            }

            val fact = pool.first()

            val notificationManager = appContext.getSystemService<NotificationManager>() ?: return Result.success()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    appContext.getString(R.string.notification_channel_daily_fact),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = appContext.getString(R.string.notification_channel_daily_fact_desc)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val openIntent = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_FACT_ID, fact.id)
            }
            val openPendingIntent = PendingIntent.getActivity(
                appContext,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // "Next" action pending intent
            val nextIntent = Intent(ACTION_NOTIFICATION_NEXT).apply {
                setPackage(appContext.packageName)
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                appContext,
                1,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // "Got it" action pending intent
            val gotItIntent = Intent(ACTION_NOTIFICATION_GOT_IT).apply {
                setPackage(appContext.packageName)
                putExtra(EXTRA_FACT_ID, fact.id)
            }
            val gotItPendingIntent = PendingIntent.getBroadcast(
                appContext,
                2,
                gotItIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val title = fact.sourceLessonTitle ?: fact.sourceBookTitle ?: appContext.getString(R.string.notification_daily_fact_title)

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(fact.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(fact.text))
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    android.R.drawable.ic_media_next,
                    appContext.getString(R.string.notification_action_next),
                    nextPendingIntent,
                )
                .addAction(
                    android.R.drawable.checkbox_on_background,
                    appContext.getString(R.string.notification_action_got_it),
                    gotItPendingIntent,
                )
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }

    companion object {
        const val CHANNEL_ID = "facts_daily_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_NOTIFICATION_NEXT = "com.factswallpaper.app.ACTION_NOTIFICATION_NEXT_FACT"
        const val ACTION_NOTIFICATION_GOT_IT = "com.factswallpaper.app.ACTION_NOTIFICATION_GOT_IT"
        const val EXTRA_FACT_ID = "fact_id"
    }
}

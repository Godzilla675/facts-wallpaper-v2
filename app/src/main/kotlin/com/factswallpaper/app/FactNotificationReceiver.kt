package com.factswallpaper.app

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.wallpaper.FactOfTheDayWorker
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FactNotificationReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun wallpaperRotationController(): WallpaperRotationController
        fun factRepository(): FactRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ReceiverEntryPoint::class.java,
        )

        when (intent.action) {
            FactOfTheDayWorker.ACTION_NOTIFICATION_NEXT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        entryPoint.wallpaperRotationController().advance()
                        // Re-issue notification with the new fact
                        val request = OneTimeWorkRequestBuilder<FactOfTheDayWorker>().build()
                        WorkManager.getInstance(context).enqueue(request)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            FactOfTheDayWorker.ACTION_NOTIFICATION_GOT_IT -> {
                val factId = intent.getStringExtra(FactOfTheDayWorker.EXTRA_FACT_ID)
                val notificationManager = context.getSystemService<NotificationManager>()
                notificationManager?.cancel(FactOfTheDayWorker.NOTIFICATION_ID)

                if (!factId.isNullOrBlank()) {
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            entryPoint.factRepository().recordReview(factId, gotIt = true)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
        }
    }
}

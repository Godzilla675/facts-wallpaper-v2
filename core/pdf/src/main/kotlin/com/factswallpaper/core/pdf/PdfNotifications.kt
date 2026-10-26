package com.factswallpaper.core.pdf

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.ocr.OcrPageProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the ongoing foreground notification shown for durable OCR work. */
@Singleton
class PdfNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.pdf_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.pdf_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    fun ocrForeground(
        document: Document,
        progress: OcrPageProgress? = null,
    ): ForegroundInfo {
        ensureChannel()
        val notification = ocrNotification(document, progress)
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    fun notifyOcrProgress(document: Document, progress: OcrPageProgress) {
        ensureChannel()
        manager.notify(NOTIFICATION_ID, ocrNotification(document, progress))
    }

    private fun ocrNotification(
        document: Document,
        progress: OcrPageProgress?,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pdf_processing)
            .setContentTitle(context.getString(R.string.pdf_notification_title))
            .setContentText(
                if (progress == null) {
                    context.getString(R.string.pdf_notification_preparing, document.title)
                } else {
                    context.getString(
                        R.string.pdf_notification_ocr_progress,
                        progress.page,
                        progress.rangeEnd,
                        progress.completedPages,
                    )
                },
            )
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                context.getString(R.string.pdf_notification_pause),
                controlIntent(OcrControlReceiver.ACTION_PAUSE, document.id),
            )
            .addAction(
                0,
                context.getString(R.string.pdf_notification_cancel),
                controlIntent(OcrControlReceiver.ACTION_CANCEL, document.id),
            )

        if (progress != null && progress.totalPages > 0) {
            builder.setProgress(
                progress.totalPages,
                progress.completedPages.coerceIn(0, progress.totalPages),
                false,
            )
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun contentIntent(): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun controlIntent(action: String, documentId: String): PendingIntent {
        val intent = Intent(context, OcrControlReceiver::class.java)
            .setAction(action)
            .putExtra(OcrControlReceiver.EXTRA_DOCUMENT_ID, documentId)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode() xor documentId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val CHANNEL_ID = "pdf_processing"
        const val NOTIFICATION_ID = 4101
    }
}

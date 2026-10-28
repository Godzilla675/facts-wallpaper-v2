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
import com.factswallpaper.core.data.pipeline.PipelineNotifier
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.ocr.OcrPageProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Builds durable progress, completion and error notifications for background
 * document processing jobs (spec 11.5).
 */
@Singleton
class PdfNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) : PipelineNotifier {
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
        val stageText = if (progress == null) {
            context.getString(R.string.pdf_notification_preparing, document.title)
        } else {
            context.getString(
                R.string.pdf_notification_ocr_progress,
                progress.page,
                progress.rangeEnd,
                progress.completedPages,
            )
        }
        val fraction = if (progress != null && progress.totalPages > 0) {
            progress.completedPages.toFloat() / progress.totalPages.toFloat()
        } else {
            null
        }
        return pipelineForeground(document.id, document.title, stageText, fraction)
    }

    fun notifyOcrProgress(document: Document, progress: OcrPageProgress) {
        val stageText = context.getString(
            R.string.pdf_notification_ocr_progress,
            progress.page,
            progress.rangeEnd,
            progress.completedPages,
        )
        val fraction = if (progress.totalPages > 0) {
            progress.completedPages.toFloat() / progress.totalPages.toFloat()
        } else {
            null
        }
        notifyProgress(document.id, document.title, stageText, fraction)
    }

    override fun pipelineForeground(
        documentId: String,
        documentTitle: String,
        stageText: String,
        progressFraction: Float?,
    ): ForegroundInfo {
        ensureChannel()
        val notification = buildOngoingNotification(documentId, documentTitle, stageText, progressFraction)
        return ForegroundInfo(
            notificationId(documentId),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    override fun notifyProgress(
        documentId: String,
        documentTitle: String,
        stageText: String,
        progressFraction: Float?,
    ) {
        ensureChannel()
        manager.notify(
            notificationId(documentId),
            buildOngoingNotification(documentId, documentTitle, stageText, progressFraction),
        )
    }

    override fun notifyCompletion(
        documentId: String,
        documentTitle: String,
        cardCount: Int,
    ) {
        ensureChannel()
        val id = notificationId(documentId)
        manager.cancel(id)

        val text = if (cardCount > 0) {
            context.getString(R.string.pdf_notification_cards_ready, cardCount)
        } else {
            context.getString(R.string.pdf_notification_outline_ready, 0)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pdf_processing)
            .setContentTitle(documentTitle)
            .setContentText(text)
            .setContentIntent(contentIntent(documentId))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                0,
                context.getString(R.string.pdf_notification_open),
                contentIntent(documentId),
            )
            .addAction(
                0,
                context.getString(R.string.pdf_notification_set_wallpaper),
                contentIntent(documentId, "action_wallpaper"),
            )

        manager.notify(id, builder.build())
    }

    override fun notifyError(
        documentId: String,
        documentTitle: String,
        errorMessage: String,
    ) {
        ensureChannel()
        val id = notificationId(documentId)
        manager.cancel(id)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pdf_processing)
            .setContentTitle(context.getString(R.string.pdf_notification_failed_title, documentTitle))
            .setContentText(errorMessage)
            .setContentIntent(contentIntent(documentId))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(
                0,
                context.getString(R.string.pdf_notification_retry),
                controlIntent(OcrControlReceiver.ACTION_RETRY, documentId),
            )

        manager.notify(id, builder.build())
    }

    override fun cancelNotification(documentId: String) {
        manager.cancel(notificationId(documentId))
    }

    private fun buildOngoingNotification(
        documentId: String,
        documentTitle: String,
        stageText: String,
        progressFraction: Float?,
    ): Notification {
        val whatsHappeningIntent = Intent().apply {
            setClassName(context.packageName, "com.factswallpaper.app.WhatsHappeningActivity")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val whatsHappeningPendingIntent = PendingIntent.getActivity(
            context,
            9999,
            whatsHappeningIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pdf_processing)
            .setContentTitle(documentTitle)
            .setContentText(stageText)
            .setContentIntent(contentIntent(documentId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0,
                context.getString(R.string.pdf_notification_cancel),
                controlIntent(OcrControlReceiver.ACTION_CANCEL, documentId),
            )
            .addAction(
                0,
                context.getString(R.string.whats_happening_title),
                whatsHappeningPendingIntent,
            )

        if (progressFraction != null && progressFraction in 0f..1f) {
            builder.setProgress(100, (progressFraction * 100).roundToInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun contentIntent(documentId: String, action: String? = null): PendingIntent? {
        val launch = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("document_id", documentId)
                if (action != null) setAction(action)
            }
            ?: return null
        return PendingIntent.getActivity(
            context,
            documentId.hashCode(),
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

    fun notificationId(documentId: String): Int =
        4000 + (documentId.hashCode() and 0x7FFF)

    companion object {
        const val CHANNEL_ID = "pdf_processing"
        const val NOTIFICATION_ID = 4101
    }
}

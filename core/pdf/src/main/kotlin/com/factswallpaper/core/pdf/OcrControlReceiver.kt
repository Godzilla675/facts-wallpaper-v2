package com.factswallpaper.core.pdf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the Pause and Cancel actions on the OCR foreground notification. The
 * persisted checkpoint is updated before the job is cancelled, so an interrupted
 * page is restarted from scratch on the next run.
 */
@AndroidEntryPoint
class OcrControlReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: DocumentProcessingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID) ?: return
        val action = intent.action ?: return
        if (action != ACTION_PAUSE && action != ACTION_CANCEL) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (action == ACTION_PAUSE) {
                    scheduler.pause(documentId)
                } else {
                    scheduler.pause(documentId)
                    WorkManager.getInstance(context).cancelUniqueWork(
                        DocumentProcessingScheduler.workName(documentId),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.factswallpaper.core.pdf.action.PAUSE_OCR"
        const val ACTION_CANCEL = "com.factswallpaper.core.pdf.action.CANCEL_OCR"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }
}

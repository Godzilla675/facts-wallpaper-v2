package com.factswallpaper.core.pdf

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Handles the Pause, Cancel and Retry actions on pipeline notifications.
 */
@AndroidEntryPoint
class OcrControlReceiver : BroadcastReceiver() {

    @Inject
    lateinit var scheduler: DocumentProcessingScheduler

    override fun onReceive(context: Context, intent: Intent) {
        val documentId = intent.getStringExtra(EXTRA_DOCUMENT_ID) ?: return
        val action = intent.action ?: return
        if (action != ACTION_PAUSE && action != ACTION_CANCEL && action != ACTION_RETRY) return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_PAUSE -> scheduler.pause(documentId)
                    ACTION_CANCEL -> scheduler.cancel(documentId)
                    ACTION_RETRY -> scheduler.resume(documentId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.factswallpaper.core.pdf.action.PAUSE_OCR"
        const val ACTION_CANCEL = "com.factswallpaper.core.pdf.action.CANCEL_OCR"
        const val ACTION_RETRY = "com.factswallpaper.core.pdf.action.RETRY"
        const val EXTRA_DOCUMENT_ID = "document_id"
    }
}

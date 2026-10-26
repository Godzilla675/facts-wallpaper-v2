package com.factswallpaper.core.render

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Enqueues the proactive pre-render queue whenever the pool, template or theme changes.
 * Calls are coalesced under a single unique work name so a burst of edits does not spawn
 * several parallel renderers.
 */
@Singleton
class CardPreRenderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun enqueue() {
        runCatching {
            val request = OneTimeWorkRequestBuilder<CardPreRenderWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                CardPreRenderWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
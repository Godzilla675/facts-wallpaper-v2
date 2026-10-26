package com.factswallpaper.core.render

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background pre-render job (§F14). Uses [CardPreRenderEngine], which composites only cached
 * diagram PNGs; WebView rendering is forbidden on worker threads (§11.8).
 */
@HiltWorker
class CardPreRenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: CardPreRenderEngine,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val report = engine.preRenderNext()
        if (report.failed > 0 && report.rendered == 0 && report.cacheHits == 0) {
            Result.retry()
        } else {
            Result.success()
        }
    } catch (_: Throwable) {
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "card_pre_render"
    }
}

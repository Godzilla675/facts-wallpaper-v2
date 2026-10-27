package com.factswallpaper.core.render

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.factswallpaper.core.data.pipeline.PipelineNotifier
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background pre-render job (§F14). Uses [CardPreRenderEngine], which composites only cached
 * diagram PNGs; WebView rendering is forbidden on worker threads (§11.8).
 * Promoted to a dataSync foreground service.
 */
@HiltWorker
class CardPreRenderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: CardPreRenderEngine,
    private val pipelineNotifier: PipelineNotifier,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        runCatching {
            setForeground(
                pipelineNotifier.pipelineForeground(
                    documentId = "card_pre_render",
                    documentTitle = "Facts Wallpaper",
                    stageText = "Rendering wallpaper cards…",
                    progressFraction = null,
                ),
            )
        }
        val report = engine.preRenderNext()
        pipelineNotifier.cancelNotification("card_pre_render")
        if (report.failed > 0 && report.rendered == 0 && report.cacheHits == 0) {
            Result.retry()
        } else {
            Result.success()
        }
    } catch (_: Throwable) {
        pipelineNotifier.cancelNotification("card_pre_render")
        Result.retry()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "card_pre_render"
    }
}

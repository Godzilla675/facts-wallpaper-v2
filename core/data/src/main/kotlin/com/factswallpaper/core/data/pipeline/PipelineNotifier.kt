package com.factswallpaper.core.data.pipeline

import androidx.work.ForegroundInfo

/**
 * Port for posting user-visible pipeline notifications and foreground service info.
 */
interface PipelineNotifier {
    fun pipelineForeground(
        documentId: String,
        documentTitle: String,
        stageText: String,
        progressFraction: Float? = null,
    ): ForegroundInfo

    fun notifyProgress(
        documentId: String,
        documentTitle: String,
        stageText: String,
        progressFraction: Float? = null,
    )

    fun notifyCompletion(
        documentId: String,
        documentTitle: String,
        cardCount: Int = 0,
    )

    fun notifyError(
        documentId: String,
        documentTitle: String,
        errorMessage: String,
    )

    fun cancelNotification(documentId: String)
}

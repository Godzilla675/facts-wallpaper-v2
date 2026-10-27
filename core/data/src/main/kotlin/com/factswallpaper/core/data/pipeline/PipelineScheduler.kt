package com.factswallpaper.core.data.pipeline

/**
 * Port for chaining pipeline stages across workers (import -> extraction -> outline -> facts -> render).
 */
interface PipelineScheduler {
    fun enqueueOutline(documentId: String)
    fun enqueueFacts(documentId: String, lessonIds: List<String>)
    fun onExtractionCompleted(documentId: String)
    fun onOutlineCompleted(documentId: String)
    fun onFactsCompleted(documentId: String, createdFactsCount: Int)
}

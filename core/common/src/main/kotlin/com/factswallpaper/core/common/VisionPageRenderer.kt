package com.factswallpaper.core.common

/**
 * Port for rasterizing PDF pages at 150 DPI for multimodal Vision AI input (Milestone P8.2).
 */
interface VisionPageRenderer {
    suspend fun getPageImage(
        documentId: String,
        localFilePath: String,
        pageNumber: Int,
    ): ByteArray

    suspend fun getPageImages(
        documentId: String,
        localFilePath: String,
        startPage: Int,
        endPage: Int,
        maxPages: Int = 20,
    ): List<ByteArray>
}

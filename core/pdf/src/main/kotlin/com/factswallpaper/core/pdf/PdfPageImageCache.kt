package com.factswallpaper.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.common.VisionPageRenderer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Manages 150 DPI (~1600px max long edge) page rendering and on-disk LRU caching
 * for multimodal AI vision mode (Milestone P8.2).
 */
@Singleton
class PdfPageImageCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: FactsDispatchers,
) : VisionPageRenderer {
    private val cacheDir: File get() = File(context.cacheDir, "vision_pages").apply { mkdirs() }

    companion object {
        const val TARGET_DPI = 150
        const val BASE_PDF_DPI = 72
        const val MAX_LONG_EDGE_PX = 1600
        const val JPEG_QUALITY = 85
        const val MAX_CACHE_ENTRIES = 100
        const val MAX_CACHE_BYTES = 50L * 1024L * 1024L // 50 MB
    }

    override suspend fun getPageImage(
        documentId: String,
        localFilePath: String,
        pageNumber: Int,
    ): ByteArray = withContext(dispatchers.io) {
        val cachedFile = cacheFile(documentId, pageNumber)
        if (cachedFile.exists() && cachedFile.length() > 0) {
            cachedFile.setLastModified(System.currentTimeMillis())
            return@withContext cachedFile.readBytes()
        }

        val bytes = renderPageBytes(localFilePath, pageNumber)
        runCatching {
            cachedFile.parentFile?.mkdirs()
            FileOutputStream(cachedFile).use { it.write(bytes) }
            enforceLruLimits()
        }
        bytes
    }

    override suspend fun getPageImages(
        documentId: String,
        localFilePath: String,
        startPage: Int,
        endPage: Int,
        maxPages: Int,
    ): List<ByteArray> = withContext(dispatchers.io) {
        val safeStart = startPage.coerceAtLeast(1)
        val safeEnd = endPage.coerceAtLeast(safeStart)
        val pagesToRender = (safeStart..safeEnd).take(maxPages)
        pagesToRender.map { page ->
            getPageImage(documentId, localFilePath, page)
        }
    }

    fun clearCache(documentId: String? = null) {
        val files = cacheDir.listFiles() ?: return
        files.forEach { file ->
            if (documentId == null || file.name.startsWith("${documentId}_")) {
                runCatching { file.delete() }
            }
        }
    }

    private fun renderPageBytes(localFilePath: String, pageNumber: Int): ByteArray {
        val file = File(localFilePath)
        check(file.isFile) { "PDF file missing: $localFilePath" }

        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                check(pageNumber in 1..renderer.pageCount) {
                    "Page $pageNumber is outside bounds 1..${renderer.pageCount}"
                }
                renderer.openPage(pageNumber - 1).use { page ->
                    val longestPt = max(page.width, page.height).coerceAtLeast(1)
                    val dpiScale = TARGET_DPI.toFloat() / BASE_PDF_DPI.toFloat() // ~2.0833
                    val maxAllowedScale = MAX_LONG_EDGE_PX.toFloat() / longestPt.toFloat()
                    val effectiveScale = minOf(dpiScale, maxAllowedScale)

                    val widthPx = (page.width * effectiveScale).roundToInt().coerceAtLeast(1)
                    val heightPx = (page.height * effectiveScale).roundToInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, Matrix(), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        ByteArrayOutputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                            out.toByteArray()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
    }

    private fun cacheFile(documentId: String, pageNumber: Int): File =
        File(cacheDir, "${documentId}_p${pageNumber}_150dpi.jpg")

    private fun enforceLruLimits() {
        val files = cacheDir.listFiles() ?: return
        if (files.size <= MAX_CACHE_ENTRIES && files.sumOf { it.length() } <= MAX_CACHE_BYTES) {
            return
        }

        val sorted = files.sortedBy { it.lastModified() }
        var currentBytes = files.sumOf { it.length() }
        var currentCount = files.size

        for (f in sorted) {
            if (currentCount <= MAX_CACHE_ENTRIES && currentBytes <= MAX_CACHE_BYTES) break
            val length = f.length()
            if (f.delete()) {
                currentBytes -= length
                currentCount--
            }
        }
    }
}

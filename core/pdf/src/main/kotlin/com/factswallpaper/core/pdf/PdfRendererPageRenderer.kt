package com.factswallpaper.core.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.ocr.OcrPageImage
import com.factswallpaper.core.ocr.OcrPageRenderer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.withContext

/**
 * Rasterizes one PDF page at a time with platform [PdfRenderer]. Files are opened
 * read-only and every native resource is closed before the bitmap is returned,
 * so the caller only owns the recycled-after-OCR bitmap.
 */
@Singleton
class PdfRendererPageRenderer @Inject constructor(
    private val dispatchers: FactsDispatchers,
) : OcrPageRenderer {

    override suspend fun render(
        localFilePath: String,
        pageNumber: Int,
        scale: Float,
        maxLongestEdgePx: Int,
    ): OcrPageImage = withContext(dispatchers.io) {
        val file = File(localFilePath)
        check(file.isFile) { "PDF is missing: $localFilePath" }

        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                check(pageNumber in 1..renderer.pageCount) {
                    "Page $pageNumber is outside 1..${renderer.pageCount}"
                }
                renderer.openPage(pageNumber - 1).use { page ->
                    val longestPt = max(page.width, page.height).coerceAtLeast(1)
                    val requestedScale = scale.coerceAtLeast(0.1f)
                    val effectiveScale = minOf(
                        requestedScale,
                        maxLongestEdgePx.toFloat() / longestPt.toFloat(),
                    )
                    val widthPx = (page.width * effectiveScale).roundToInt().coerceAtLeast(1)
                    val heightPx = (page.height * effectiveScale).roundToInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(
                        widthPx,
                        heightPx,
                        Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        Matrix(),
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY,
                    )
                    OcrPageImage(bitmap = bitmap, pageNumber = pageNumber)
                }
            }
        }
    }
}

package com.factswallpaper.core.ocr

import android.graphics.Bitmap
import com.factswallpaper.core.model.OcrEngineType
import java.io.Closeable

/**
 * A single rasterized page. Callers MUST use `use { }` so the bitmap is
 * recycled immediately after recognition (spec F5, step C).
 */
class OcrPageImage private constructor(
    val pageNumber: Int,
    private val rawBitmap: Bitmap?,
) : Closeable {
    constructor(bitmap: Bitmap, pageNumber: Int) : this(pageNumber = pageNumber, rawBitmap = bitmap)

    val bitmap: Bitmap
        get() = checkNotNull(rawBitmap) { "OcrPageImage created via forTesting() has no Android Bitmap" }

    var isClosed: Boolean = false
        private set

    override fun close() {
        isClosed = true
        val bmp = rawBitmap ?: return
        if (!bmp.isRecycled) bmp.recycle()
    }

    companion object {
        fun forTesting(pageNumber: Int): OcrPageImage =
            OcrPageImage(pageNumber = pageNumber, rawBitmap = null)
    }
}

data class OcrResult(
    val text: String,
    /**
     * ML Kit does not expose page-level confidence; Tesseract reports mean
     * confidence. Null means "not reported by this engine" (never a fake 1.0).
     */
    val confidence: Float?,
)

interface OcrEngine : Closeable {
    val engineType: OcrEngineType

    suspend fun recognize(image: OcrPageImage, language: OcrLanguage): OcrResult
}

class OcrEngineUnavailableException(message: String) : Exception(message)

class OcrRecognitionException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OcrIncompleteException(
    val missingPages: List<Int>,
) : Exception("OCR text is incomplete: missing pages $missingPages")

/** Rasterizes a page of a locally stored PDF for OCR (implemented in :core:pdf). */
interface OcrPageRenderer {
    suspend fun render(
        localFilePath: String,
        pageNumber: Int,
        scale: Float = DEFAULT_SCALE,
        maxLongestEdgePx: Int = DEFAULT_MAX_EDGE_PX,
    ): OcrPageImage

    companion object {
        const val DEFAULT_SCALE = 2.5f
        const val DEFAULT_MAX_EDGE_PX = 2560
    }
}

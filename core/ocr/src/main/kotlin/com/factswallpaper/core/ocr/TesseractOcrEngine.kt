package com.factswallpaper.core.ocr

import com.factswallpaper.core.model.OcrEngineType
import com.googlecode.tesseract.android.TessBaseAPI

/**
 * Tesseract 5 LSTM engine backed by a downloaded `tessdata_best` pack. The pack
 * is installed under `noBackupFilesDir/ocr_models/{code}/tessdata/` and is
 * verified with SHA-256 before it can be selected.
 */
class TesseractOcrEngine(
    private val modelRootDir: String,
    private val languageCode: String,
) : OcrEngine {

    override val engineType: OcrEngineType = OcrEngineType.TESSERACT

    override suspend fun recognize(image: OcrPageImage, language: OcrLanguage): OcrResult {
        val api = TessBaseAPI()
        return try {
            val initialized = api.init(modelRootDir, languageCode)
            if (!initialized) {
                throw OcrEngineUnavailableException(
                    "Could not initialise Tesseract pack $languageCode",
                )
            }
            api.setImage(image.bitmap)
            val text = api.utF8Text?.trim().orEmpty()
            val confidence = runCatching { api.meanConfidence() }
                .getOrNull()
                ?.takeIf { it >= 0 }
                ?.div(100f)
            OcrResult(text = text, confidence = confidence)
        } catch (error: Throwable) {
            if (error is OcrEngineUnavailableException) throw error
            throw OcrRecognitionException("Tesseract recognition failed for $languageCode", error)
        } finally {
            runCatching { api.recycle() }
        }
    }

    override fun close() = Unit
}

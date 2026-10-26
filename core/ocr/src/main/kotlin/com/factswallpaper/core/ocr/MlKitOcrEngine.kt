package com.factswallpaper.core.ocr

import com.factswallpaper.core.model.OcrEngineType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Bundled ML Kit Text Recognition v2 script recognizer. */
class MlKitOcrEngine(
    script: OcrScript,
) : OcrEngine {
    private val recognizer: TextRecognizer = createRecognizer(script)

    override val engineType: OcrEngineType = OcrEngineType.ML_KIT

    override suspend fun recognize(image: OcrPageImage, language: OcrLanguage): OcrResult {
        val result = try {
            recognizer.process(InputImage.fromBitmap(image.bitmap, 0)).await()
        } catch (error: Throwable) {
            throw OcrRecognitionException("ML Kit recognition failed for ${language.code}", error)
        }
        return OcrResult(text = result.text.trim(), confidence = null)
    }

    override fun close() {
        recognizer.close()
    }

    private fun createRecognizer(script: OcrScript): TextRecognizer = when (script) {
        OcrScript.LATIN -> TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        OcrScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        OcrScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
        OcrScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        OcrScript.DEVANAGARI ->
            TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())

        OcrScript.ARABIC, OcrScript.CYRILLIC -> throw OcrEngineUnavailableException(
            "ML Kit has no ${script.name.lowercase()} recognizer; install the downloadable pack",
        )
    }
}

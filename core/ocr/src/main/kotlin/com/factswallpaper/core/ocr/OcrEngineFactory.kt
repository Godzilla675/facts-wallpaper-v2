package com.factswallpaper.core.ocr

import javax.inject.Inject

interface OcrEngineFactory {
    /** Creates an engine for [language], throwing if its model pack is not installed. */
    fun create(language: OcrLanguage): OcrEngine
}

class DefaultOcrEngineFactory @Inject constructor(
    private val modelManager: OcrModelManager,
) : OcrEngineFactory {

    override fun create(language: OcrLanguage): OcrEngine = when (language.modelSource) {
        OcrModelSource.ML_KIT_BUNDLED -> MlKitOcrEngine(language.script)
        OcrModelSource.TESSERACT_DOWNLOAD -> {
            if (!modelManager.isInstalled(language.code)) {
                throw OcrEngineUnavailableException(
                    "Download the ${language.code} OCR language pack first",
                )
            }
            TesseractOcrEngine(
                modelRootDir = modelManager.modelRoot(language.code).absolutePath,
                languageCode = language.code,
            )
        }
    }
}

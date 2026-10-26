package com.factswallpaper.core.ocr

import androidx.annotation.StringRes
import com.factswallpaper.core.model.OcrEngineType

enum class OcrScript {
    LATIN,
    ARABIC,
    CHINESE,
    JAPANESE,
    KOREAN,
    DEVANAGARI,
    CYRILLIC,
}

enum class OcrModelSource {
    /** Bundled ML Kit script recognizer; works offline immediately. */
    ML_KIT_BUNDLED,

    /** High-accuracy Tesseract LSTM pack downloaded and SHA-256 verified on device. */
    TESSERACT_DOWNLOAD,
}

data class OcrLanguage(
    val code: String,
    @StringRes val displayNameRes: Int,
    val script: OcrScript,
    val modelSource: OcrModelSource,
    val engineType: OcrEngineType,
    val downloadUrl: String? = null,
    val expectedSizeBytes: Long = 0L,
    val expectedSha256: String? = null,
) {
    val requiresDownload: Boolean get() = modelSource == OcrModelSource.TESSERACT_DOWNLOAD
}

/**
 * Local OCR language catalogue (F5). Latin/Chinese/Japanese/Korean/Devanagari
 * use bundled ML Kit recognizers. Arabic and Cyrillic have no ML Kit
 * recognizer, so they use downloadable, checksum-verified Tesseract 5
 * `tessdata_best` packs that run fully offline once installed.
 */
object OcrLanguageCatalog {

    private const val TESSDATA_ROOT =
        "https://raw.githubusercontent.com/tesseract-ocr/tessdata_best/main"

    val languages: List<OcrLanguage> = listOf(
        mlKit("eng", R.string.ocr_language_eng, OcrScript.LATIN),
        mlKit("fra", R.string.ocr_language_fra, OcrScript.LATIN),
        mlKit("deu", R.string.ocr_language_deu, OcrScript.LATIN),
        mlKit("spa", R.string.ocr_language_spa, OcrScript.LATIN),
        mlKit("tur", R.string.ocr_language_tur, OcrScript.LATIN),
        mlKit("por", R.string.ocr_language_por, OcrScript.LATIN),
        mlKit("ita", R.string.ocr_language_ita, OcrScript.LATIN),
        mlKit("nld", R.string.ocr_language_nld, OcrScript.LATIN),
        mlKit("zho", R.string.ocr_language_zho, OcrScript.CHINESE),
        mlKit("jpn", R.string.ocr_language_jpn, OcrScript.JAPANESE),
        mlKit("kor", R.string.ocr_language_kor, OcrScript.KOREAN),
        mlKit("hin", R.string.ocr_language_hin, OcrScript.DEVANAGARI),
        tesseract(
            code = "ara",
            displayName = R.string.ocr_language_ara,
            script = OcrScript.ARABIC,
            size = 12_603_724L,
            sha256 = "ab9d157d8e38ca00e7e39c7d5363a5239e053f5b0dbdb3167dde9d8124335896",
        ),
        tesseract(
            code = "rus",
            displayName = R.string.ocr_language_rus,
            script = OcrScript.CYRILLIC,
            size = 15_301_764L,
            sha256 = "b617eb6830ffabaaa795dd87ea7fd251adfe9cf0efe05eb9a2e8128b7728d6b6",
        ),
        tesseract(
            code = "ukr",
            displayName = R.string.ocr_language_ukr,
            script = OcrScript.CYRILLIC,
            size = 10_859_081L,
            sha256 = "1277f6e3b6f707063a92d40e7678e7f57154e8414e328e340be9ee9275eea9c8",
        ),
    )

    val byCode: Map<String, OcrLanguage> = languages.associateBy { it.code }
    val default: OcrLanguage = byCode.getValue("eng")

    fun forCode(code: String): OcrLanguage = byCode[code] ?: default

    fun mlKitForScript(script: OcrScript): Boolean = script in setOf(
        OcrScript.LATIN,
        OcrScript.CHINESE,
        OcrScript.JAPANESE,
        OcrScript.KOREAN,
        OcrScript.DEVANAGARI,
    )

    private fun mlKit(
        code: String,
        @StringRes displayName: Int,
        script: OcrScript,
    ): OcrLanguage = OcrLanguage(
        code = code,
        displayNameRes = displayName,
        script = script,
        modelSource = OcrModelSource.ML_KIT_BUNDLED,
        engineType = OcrEngineType.ML_KIT,
    )

    private fun tesseract(
        code: String,
        @StringRes displayName: Int,
        script: OcrScript,
        size: Long,
        sha256: String,
    ): OcrLanguage = OcrLanguage(
        code = code,
        displayNameRes = displayName,
        script = script,
        modelSource = OcrModelSource.TESSERACT_DOWNLOAD,
        engineType = OcrEngineType.TESSERACT,
        downloadUrl = "$TESSDATA_ROOT/$code.traineddata",
        expectedSizeBytes = size,
        expectedSha256 = sha256,
    )
}

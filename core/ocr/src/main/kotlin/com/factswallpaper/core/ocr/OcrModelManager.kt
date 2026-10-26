package com.factswallpaper.core.ocr

import android.content.Context
import com.factswallpaper.core.common.FactsDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

enum class OcrModelStatus {
    INSTALLED,
    NOT_INSTALLED,
    DOWNLOADING,
    VERIFYING,
    ERROR,
}

data class OcrModelState(
    val language: OcrLanguage,
    val status: OcrModelStatus,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val errorDetail: String? = null,
) {
    val progress: Float
        get() = if (totalBytes <= 0L) 0f
        else (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    val isInstalled: Boolean get() = status == OcrModelStatus.INSTALLED
}

interface OcrModelManager {
    val states: StateFlow<List<OcrModelState>>

    fun stateFor(languageCode: String): OcrModelState

    fun isInstalled(languageCode: String): Boolean

    fun modelRoot(languageCode: String): File

    suspend fun download(languageCode: String): Result<Unit>

    suspend fun delete(languageCode: String)
}

@Singleton
class DefaultOcrModelManager @Inject constructor(
    @ApplicationContext context: Context,
    @OcrHttpClient private val client: OkHttpClient,
    private val dispatchers: FactsDispatchers,
) : OcrModelManager {

    private val rootDir = File(context.noBackupFilesDir, "ocr_models")
    private val mutex = Mutex()
    private val stateMap = OcrLanguageCatalog.languages.associate { language ->
        language.code to initialState(language)
    }.toMutableMap()
    private val _states = MutableStateFlow(stateMap.values.sortedBy { it.language.code })
    override val states: StateFlow<List<OcrModelState>> = _states.asStateFlow()

    override fun stateFor(languageCode: String): OcrModelState {
        val language = OcrLanguageCatalog.forCode(languageCode)
        return stateMap[language.code] ?: initialState(language)
    }

    override fun isInstalled(languageCode: String): Boolean = stateFor(languageCode).isInstalled

    override fun modelRoot(languageCode: String): File =
        File(rootDir, OcrLanguageCatalog.forCode(languageCode).code)

    override suspend fun download(languageCode: String): Result<Unit> = withContext(dispatchers.io) {
        val language = OcrLanguageCatalog.forCode(languageCode)
        if (!language.requiresDownload) return@withContext Result.success(Unit)

        mutex.withLock {
            if (stateFor(language.code).isInstalled) return@withLock Result.success(Unit)
            val temp = File(modelRoot(language.code).apply { mkdirs() }, "download.tmp")
            temp.delete()
            try {
                update(language, OcrModelStatus.DOWNLOADING, 0L, language.expectedSizeBytes)
                val url = checkNotNull(language.downloadUrl) {
                    "Downloadable language ${language.code} is missing its URL"
                }
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw OcrModelDownloadException("HTTP ${response.code}")
                    }
                    val declared = response.body.contentLength()
                        .takeIf { it > 0L } ?: language.expectedSizeBytes
                    val digest = MessageDigest.getInstance("SHA-256")
                    var downloaded = 0L
                    var lastReported = 0L
                    FileOutputStream(temp).use { output ->
                        response.body.byteStream().use { input ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                downloaded += read
                                digest.update(buffer, 0, read)
                                output.write(buffer, 0, read)
                                if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                    lastReported = downloaded
                                    update(
                                        language,
                                        OcrModelStatus.DOWNLOADING,
                                        downloaded,
                                        declared,
                                    )
                                }
                            }
                        }
                        output.fd.sync()
                    }
                    update(language, OcrModelStatus.VERIFYING, downloaded, declared)

                    val actualHash = digest.digest().toHex()
                    if (downloaded != language.expectedSizeBytes) {
                        throw OcrModelDownloadException(
                            "Size mismatch: expected ${language.expectedSizeBytes}, got $downloaded",
                        )
                    }
                    if (!actualHash.equals(language.expectedSha256, ignoreCase = true)) {
                        throw OcrModelDownloadException("SHA-256 mismatch for ${language.code}")
                    }

                    val tessdata = File(modelRoot(language.code), "tessdata").apply { mkdirs() }
                    val target = File(tessdata, "${language.code}.traineddata")
                    if (target.exists()) target.delete()
                    if (!temp.renameTo(target)) {
                        temp.inputStream().use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        temp.delete()
                    }
                    File(modelRoot(language.code), MANIFEST).writeText(
                        "sha256=$actualHash\nsize=$downloaded\nurl=${language.downloadUrl}\n",
                    )
                    update(language, OcrModelStatus.INSTALLED, downloaded, downloaded)
                    Result.success(Unit)
                }
            } catch (error: Throwable) {
                temp.delete()
                update(
                    language,
                    OcrModelStatus.ERROR,
                    0L,
                    language.expectedSizeBytes,
                    error.message,
                )
                Result.failure(error)
            }
        }
    }

    override suspend fun delete(languageCode: String) = withContext(dispatchers.io) {
        val language = OcrLanguageCatalog.forCode(languageCode)
        if (!language.requiresDownload) return@withContext
        mutex.withLock {
            modelRoot(language.code).deleteRecursively()
            update(language, OcrModelStatus.NOT_INSTALLED, 0L, language.expectedSizeBytes)
        }
    }

    private fun initialState(language: OcrLanguage): OcrModelState {
        if (!language.requiresDownload) {
            return OcrModelState(language, OcrModelStatus.INSTALLED)
        }
        val root = File(rootDir, language.code)
        val target = File(root, "tessdata/${language.code}.traineddata")
        val manifest = File(root, MANIFEST)
        val installed = target.isFile &&
            target.length() == language.expectedSizeBytes &&
            manifest.isFile
        return OcrModelState(
            language = language,
            status = if (installed) OcrModelStatus.INSTALLED else OcrModelStatus.NOT_INSTALLED,
            downloadedBytes = if (installed) target.length() else 0L,
            totalBytes = language.expectedSizeBytes,
        )
    }

    private fun update(
        language: OcrLanguage,
        status: OcrModelStatus,
        downloadedBytes: Long,
        totalBytes: Long,
        errorDetail: String? = null,
    ) {
        stateMap[language.code] = OcrModelState(
            language = language,
            status = status,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            errorDetail = errorDetail,
        )
        _states.value = stateMap.values.sortedBy { it.language.code }
    }

    private fun ByteArray.toHex(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }

    private companion object {
        const val MANIFEST = "manifest.properties"
        const val PROGRESS_STEP_BYTES = 256L * 1024L
    }
}

class OcrModelDownloadException(message: String) : Exception(message)

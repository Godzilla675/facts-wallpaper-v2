package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.FactsDispatchers
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class DownloadedPdf(
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val contentType: String?,
    val finalUrl: String,
)

class PdfDownloadException(
    val code: PdfImportErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

interface PdfUrlDownloader {
    suspend fun download(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): DownloadedPdf
}

/**
 * Strict HTTPS-only PDF downloader with content-type, magic-byte and 100 MB
 * streaming cap enforcement (spec F2).
 */
@Singleton
class HttpPdfUrlDownloader @Inject constructor(
    @PdfHttpClient private val client: OkHttpClient,
    private val dispatchers: FactsDispatchers,
) : PdfUrlDownloader {

    override suspend fun download(
        url: String,
        destination: File,
        onProgress: (Long, Long?) -> Unit,
    ): DownloadedPdf = withContext(dispatchers.io) {
        val validated = when (val check = PdfUrlPolicy.validate(url)) {
            is PdfUrlCheck.Valid -> check.normalizedUrl
            is PdfUrlCheck.Invalid -> throw PdfDownloadException(
                code = when (check.error) {
                    PdfUrlError.HTTPS_REQUIRED -> PdfImportErrorCode.HTTPS_REQUIRED
                    else -> PdfImportErrorCode.INVALID_URL
                },
                message = "Rejected PDF URL: ${check.error}",
            )
        }

        destination.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(validated)
            .header("Accept", "application/pdf,application/octet-stream;q=0.9")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw PdfDownloadException(
                        code = PdfImportErrorCode.DOWNLOAD_FAILED,
                        message = "HTTP ${response.code}",
                    )
                }
                val finalUrl = response.request.url.toString()
                if (!finalUrl.startsWith("https://", ignoreCase = true)) {
                    throw PdfDownloadException(
                        code = PdfImportErrorCode.HTTPS_REQUIRED,
                        message = "Redirected to a non-HTTPS URL",
                    )
                }
                val contentType = response.header("Content-Type")
                if (!PdfUrlPolicy.isAcceptedContentType(contentType)) {
                    throw PdfDownloadException(
                        code = PdfImportErrorCode.CONTENT_TYPE_REJECTED,
                        message = "Unsupported content type: $contentType",
                    )
                }
                val declaredLength = response.body.contentLength().takeIf { it >= 0L }
                if (declaredLength != null && declaredLength > PdfUrlPolicy.MAX_PDF_BYTES) {
                    throw PdfDownloadException(
                        code = PdfImportErrorCode.FILE_TOO_LARGE,
                        message = "Declared size ${formatMegabytes(declaredLength)} exceeds 100 MB",
                    )
                }

                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                var header = ByteArray(0)
                FileOutputStream(destination).use { output ->
                    response.body.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            total += read
                            if (total > PdfUrlPolicy.MAX_PDF_BYTES) {
                                throw PdfDownloadException(
                                    code = PdfImportErrorCode.FILE_TOO_LARGE,
                                    message = "Downloaded stream exceeded 100 MB",
                                )
                            }
                            if (header.size < PdfUrlPolicy.MAGIC_HEADER.length) {
                                val needed = PdfUrlPolicy.MAGIC_HEADER.length - header.size
                                header += buffer.copyOfRange(0, minOf(needed, read))
                            }
                            digest.update(buffer, 0, read)
                            output.write(buffer, 0, read)
                            onProgress(total, declaredLength)
                        }
                    }
                    output.fd.sync()
                }

                if (!PdfUrlPolicy.hasPdfMagicHeader(header)) {
                    throw PdfDownloadException(
                        code = PdfImportErrorCode.NOT_A_PDF,
                        message = "Downloaded content is not a PDF",
                    )
                }

                DownloadedPdf(
                    file = destination,
                    sha256 = digest.digest().toHexString(),
                    sizeBytes = total,
                    contentType = contentType?.substringBefore(';')?.trim(),
                    finalUrl = finalUrl,
                )
            }
        } catch (error: Throwable) {
            destination.delete()
            if (error is PdfDownloadException) throw error
            throw PdfDownloadException(
                code = PdfImportErrorCode.DOWNLOAD_FAILED,
                message = "PDF download failed",
                cause = error,
            )
        }
    }

    private fun ByteArray.toHexString(): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            chars[index * 2] = digits[value ushr 4]
            chars[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(chars)
    }
}

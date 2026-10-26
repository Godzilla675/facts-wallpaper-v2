package com.factswallpaper.core.pdf

import java.net.URI

enum class PdfUrlError {
    MALFORMED,
    HTTPS_REQUIRED,
    HOST_MISSING,
    CREDENTIALS_NOT_ALLOWED,
    TOO_LONG,
}

sealed interface PdfUrlCheck {
    data class Valid(val normalizedUrl: String) : PdfUrlCheck
    data class Invalid(val error: PdfUrlError) : PdfUrlCheck
}

/**
 * Strict HTTPS-only policy for remote PDFs (spec F2). No `http`, `file`,
 * `content` or `ftp` URLs are accepted, credentials in the URL are rejected,
 * and fragments are stripped before the request is made.
 */
object PdfUrlPolicy {

    const val MAX_URL_LENGTH = 2_048
    const val MAX_PDF_BYTES: Long = 100L * 1024L * 1024L
    const val MAGIC_HEADER = "%PDF-"
    val ACCEPTED_CONTENT_TYPES = setOf(
        "application/pdf",
        "application/x-pdf",
        "application/octet-stream",
    )

    fun validate(rawUrl: String): PdfUrlCheck {
        if (rawUrl.length > MAX_URL_LENGTH) return PdfUrlCheck.Invalid(PdfUrlError.TOO_LONG)
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }
            .getOrElse { return PdfUrlCheck.Invalid(PdfUrlError.MALFORMED) }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED)
        }
        if (uri.host.isNullOrBlank()) return PdfUrlCheck.Invalid(PdfUrlError.HOST_MISSING)
        if (uri.userInfo != null) return PdfUrlCheck.Invalid(PdfUrlError.CREDENTIALS_NOT_ALLOWED)
        return PdfUrlCheck.Valid(trimmed)
    }

    fun isAcceptedContentType(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return true // verified by magic bytes
        val mime = contentType.substringBefore(';').trim().lowercase()
        return mime in ACCEPTED_CONTENT_TYPES
    }

    fun hasPdfMagicHeader(header: ByteArray): Boolean {
        if (header.size < MAGIC_HEADER.length) return false
        return MAGIC_HEADER.indices.all { index ->
            header[index].toInt() == MAGIC_HEADER[index].code
        }
    }
}

internal fun formatMegabytes(bytes: Long): String =
    String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))

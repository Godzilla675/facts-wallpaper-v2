package com.factswallpaper.core.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfUrlPolicyTest {

    @Test
    fun `https URLs are accepted and trimmed`() {
        val check = PdfUrlPolicy.validate("  https://example.com/notes.pdf  ")

        assertThat(check).isEqualTo(PdfUrlCheck.Valid("https://example.com/notes.pdf"))
    }

    @Test
    fun `scheme comparison is case insensitive`() {
        assertThat(PdfUrlPolicy.validate("HTTPS://example.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Valid("HTTPS://example.com/notes.pdf"))
    }

    @Test
    fun `plain http is rejected`() {
        assertThat(PdfUrlPolicy.validate("http://example.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED))
    }

    @Test
    fun `file content and ftp schemes are rejected`() {
        assertThat(PdfUrlPolicy.validate("file:///sdcard/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED))
        assertThat(PdfUrlPolicy.validate("content://media/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED))
        assertThat(PdfUrlPolicy.validate("ftp://example.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED))
    }

    @Test
    fun `credentials in the URL are rejected`() {
        assertThat(PdfUrlPolicy.validate("https://user:secret@example.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.CREDENTIALS_NOT_ALLOWED))
    }

    @Test
    fun `a missing host is rejected`() {
        assertThat(PdfUrlPolicy.validate("https:///notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HOST_MISSING))
    }

    @Test
    fun `malformed URLs and overlong URLs are rejected`() {
        assertThat(PdfUrlPolicy.validate("example.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.HTTPS_REQUIRED))
        assertThat(PdfUrlPolicy.validate("not a url"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.MALFORMED))
        assertThat(PdfUrlPolicy.validate("https://exa mple.com/notes.pdf"))
            .isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.MALFORMED))
        assertThat(
            PdfUrlPolicy.validate("https://example.com/" + "a".repeat(PdfUrlPolicy.MAX_URL_LENGTH)),
        ).isEqualTo(PdfUrlCheck.Invalid(PdfUrlError.TOO_LONG))
    }

    @Test
    fun `accepted content types tolerate parameters and casing`() {
        assertThat(PdfUrlPolicy.isAcceptedContentType(null)).isTrue()
        assertThat(PdfUrlPolicy.isAcceptedContentType("")).isTrue()
        assertThat(PdfUrlPolicy.isAcceptedContentType("application/pdf")).isTrue()
        assertThat(PdfUrlPolicy.isAcceptedContentType("Application/X-PDF")).isTrue()
        assertThat(PdfUrlPolicy.isAcceptedContentType("application/octet-stream; charset=binary"))
            .isTrue()
        assertThat(PdfUrlPolicy.isAcceptedContentType("text/html")).isFalse()
        assertThat(PdfUrlPolicy.isAcceptedContentType("image/png")).isFalse()
    }

    @Test
    fun `magic header must match exactly at the start of the stream`() {
        assertThat(PdfUrlPolicy.hasPdfMagicHeader("%PDF-".toByteArray())).isTrue()
        assertThat(PdfUrlPolicy.hasPdfMagicHeader("%PDF-1.7".toByteArray())).isTrue()
        assertThat(PdfUrlPolicy.hasPdfMagicHeader(" %PDF-".toByteArray())).isFalse()
        assertThat(PdfUrlPolicy.hasPdfMagicHeader("%PD".toByteArray())).isFalse()
        assertThat(PdfUrlPolicy.hasPdfMagicHeader(ByteArray(0))).isFalse()
    }
}

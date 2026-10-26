package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.FactsDispatchers
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfUrlDownloaderTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatchers = FactsDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )

    @Test
    fun `downloads valid HTTPS PDF and computes SHA-256 and progress`() = runTest {
        val pdfBytes = "%PDF-1.7\nvalid-pdf-stream-body\n%%EOF".toByteArray()
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/pdf")
                    .body(pdfBytes.toResponseBody("application/pdf".toMediaType()))
                    .build()
            })
            .build()
        val downloader = HttpPdfUrlDownloader(client, testDispatchers)
        val dest = File(temporaryFolder.root, "downloaded.pdf")
        val progressEvents = mutableListOf<Pair<Long, Long?>>()

        val downloaded = downloader.download(
            url = "https://example.org/paper.pdf",
            destination = dest,
            onProgress = { cur, total -> progressEvents += cur to total },
        )

        assertThat(downloaded.file.exists()).isTrue()
        assertThat(downloaded.sizeBytes).isEqualTo(pdfBytes.size.toLong())
        assertThat(downloaded.sha256).isEqualTo(PdfHashing.sha256(pdfBytes))
        assertThat(downloaded.contentType).isEqualTo("application/pdf")
        assertThat(progressEvents).isNotEmpty()
        assertThat(progressEvents.last().first).isEqualTo(pdfBytes.size.toLong())
    }

    @Test
    fun `rejects plain HTTP URLs before issuing network call`() = runTest {
        var networkCalled = false
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor {
                networkCalled = true
                error("Should not execute network call")
            })
            .build()
        val downloader = HttpPdfUrlDownloader(client, testDispatchers)
        val dest = File(temporaryFolder.root, "http.pdf")

        try {
            downloader.download("http://example.org/paper.pdf", dest)
            fail("Expected PdfDownloadException")
        } catch (error: PdfDownloadException) {
            assertThat(error.code).isEqualTo(PdfImportErrorCode.HTTPS_REQUIRED)
            assertThat(networkCalled).isFalse()
            assertThat(dest.exists()).isFalse()
        }
    }

    @Test
    fun `rejects HTML content-type and deletes destination file`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "text/html; charset=utf-8")
                    .body("<html>not a pdf</html>".toResponseBody("text/html".toMediaType()))
                    .build()
            })
            .build()
        val downloader = HttpPdfUrlDownloader(client, testDispatchers)
        val dest = File(temporaryFolder.root, "html.pdf")

        try {
            downloader.download("https://example.org/login", dest)
            fail("Expected PdfDownloadException")
        } catch (error: PdfDownloadException) {
            assertThat(error.code).isEqualTo(PdfImportErrorCode.CONTENT_TYPE_REJECTED)
            assertThat(dest.exists()).isFalse()
        }
    }

    @Test
    fun `rejects binary stream lacking PDF magic bytes and deletes file`() = runTest {
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/octet-stream")
                    .body("PK\u0003\u0004not-a-pdf".toByteArray().toResponseBody("application/octet-stream".toMediaType()))
                    .build()
            })
            .build()
        val downloader = HttpPdfUrlDownloader(client, testDispatchers)
        val dest = File(temporaryFolder.root, "fake.pdf")

        try {
            downloader.download("https://example.org/archive.bin", dest)
            fail("Expected PdfDownloadException")
        } catch (error: PdfDownloadException) {
            assertThat(error.code).isEqualTo(PdfImportErrorCode.NOT_A_PDF)
            assertThat(dest.exists()).isFalse()
        }
    }

    @Test
    fun `rejects declared Content-Length exceeding 100 MB`() = runTest {
        val oversizedBody = object : okhttp3.ResponseBody() {
            override fun contentType() = "application/pdf".toMediaType()
            override fun contentLength(): Long = PdfUrlPolicy.MAX_PDF_BYTES + 1L
            override fun source(): BufferedSource = Buffer()
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .header("Content-Type", "application/pdf")
                    .body(oversizedBody)
                    .build()
            })
            .build()
        val downloader = HttpPdfUrlDownloader(client, testDispatchers)
        val dest = File(temporaryFolder.root, "huge.pdf")

        try {
            downloader.download("https://example.org/huge.pdf", dest)
            fail("Expected PdfDownloadException")
        } catch (error: PdfDownloadException) {
            assertThat(error.code).isEqualTo(PdfImportErrorCode.FILE_TOO_LARGE)
            assertThat(dest.exists()).isFalse()
        }
    }
}

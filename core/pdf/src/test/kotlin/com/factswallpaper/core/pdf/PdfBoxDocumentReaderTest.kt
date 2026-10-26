package com.factswallpaper.core.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies that PdfBox's single-pass page collector stays aligned with the
 * one-based page numbers used everywhere else (F3/F4), and that range
 * extraction and sub-PDF construction preserve the requested pages.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfBoxDocumentReaderTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dispatchers = FactsDispatchers(Dispatchers.IO, Dispatchers.Default, Dispatchers.Unconfined)
    private val reader by lazy { PdfBoxDocumentReader(context, PdfBoxRuntime(context), dispatchers) }
    private val fixture by lazy { createFixture() }

    @Before
    fun setUp() {
        PdfBoxRuntime(context).initialize()
    }

    @After
    fun tearDown() {
        fixture.delete()
        File(context.cacheDir, "direct_pdf").deleteRecursively()
    }

    @Test
    fun `metadata counts page text against the correct page numbers`() = runBlocking {
        val metadata = reader.readMetadata(
            file = fixture,
            sourceUri = "content://fixture",
            displayName = "physics-notes.pdf",
            fileHash = "abc123",
        )

        assertThat(metadata.pageCount).isEqualTo(4)
        assertThat(metadata.title).isEqualTo("physics-notes")
        assertThat(metadata.pageTextStats.map { it.pageNumber }).containsExactly(1, 2, 3, 4).inOrder()
        assertThat(metadata.hasTextOnPage(1)).isTrue()
        assertThat(metadata.hasTextOnPage(2)).isFalse()
        assertThat(metadata.hasTextOnPage(3)).isTrue()
        assertThat(metadata.hasTextOnPage(4)).isTrue()
        assertThat(metadata.pagesWithExtractableText).isEqualTo(3)
    }

    @Test
    fun `range extraction keeps source page numbers and skips pages before the range`() = runBlocking {
        val extraction = reader.extractRange(fixture, PdfPageRange(2, 3))

        assertThat(extraction.range).isEqualTo(PdfPageRange(2, 3))
        assertThat(extraction.pages.map { it.pageNumber }).containsExactly(2, 3).inOrder()
        assertThat(extraction.pages[0].text).isEmpty()
        assertThat(extraction.pages[1].text).startsWith("PAGE THREE")
        assertThat(extraction.pages[1].text).doesNotContain("PAGE TWO")
    }

    @Test
    fun `range extraction clamps to the document and keeps the last page aligned`() = runBlocking {
        val extraction = reader.extractRange(fixture, PdfPageRange(3, 99))

        assertThat(extraction.range).isEqualTo(PdfPageRange(3, 4))
        assertThat(extraction.pages.map { it.pageNumber }).containsExactly(3, 4).inOrder()
        assertThat(extraction.pages[0].text).startsWith("PAGE THREE")
        assertThat(extraction.pages[1].text).startsWith("PAGE FOUR")
    }

    @Test
    fun `a filtered sub-pdf contains only the requested pages in order`() = runBlocking {
        val store = PdfFileStore(context, dispatchers)
        val builder = PdfSubPdfBuilder(PdfBoxRuntime(context), store, dispatchers)
        val document = document(localFilePath = fixture.absolutePath)

        val built = builder.build(document, PdfPageRange(1, 3))

        assertThat(built.absolutePath).isNotEqualTo(fixture.absolutePath)
        PDDocument.load(built).use { pdf ->
            assertThat(pdf.numberOfPages).isEqualTo(3)
        }
        val extraction = reader.extractRange(built, PdfPageRange.all(3))
        assertThat(extraction.pages[0].text).startsWith("PAGE ONE")
        assertThat(extraction.pages[1].text).isEmpty()
        assertThat(extraction.pages[2].text).startsWith("PAGE THREE")
    }

    @Test
    fun `a full-range sub-pdf reuses the stored original`() = runBlocking {
        val store = PdfFileStore(context, dispatchers)
        val builder = PdfSubPdfBuilder(PdfBoxRuntime(context), store, dispatchers)
        val document = document(localFilePath = fixture.absolutePath)

        val built = builder.build(document, PdfPageRange(1, 99))

        assertThat(built.absolutePath).isEqualTo(fixture.absolutePath)
    }

    private fun createFixture(): File {
        val file = File(context.cacheDir, "page-alignment-fixture.pdf")
        PDDocument().use { pdf ->
            for (pageNumber in 1..4) {
                val page = PDPage()
                pdf.addPage(page)
                if (pageNumber == 2) continue
                PDPageContentStream(pdf, page).use { content ->
                    content.beginText()
                    content.setFont(PDType1Font.HELVETICA, 12f)
                    content.newLineAtOffset(72f, 700f)
                    content.showText("PAGE ${WORD[pageNumber]} opens this chapter of the physics notes.")
                    content.endText()
                }
            }
            pdf.save(file)
        }
        return file
    }

    private fun document(localFilePath: String) = Document(
        id = DOCUMENT_ID,
        title = "Physics",
        sourceUri = "content://fixture",
        localFilePath = localFilePath,
        fileHash = "abc123",
        fileSizeBytes = fixture.length(),
        pageCount = 4,
        pagesWithText = 3,
        processingRoute = PdfProcessingRoute.LOCAL_OCR,
        extractionStatus = ExtractionStatus.ANALYZED,
        createdAtEpochMs = 1L,
    )

    private companion object {
        const val DOCUMENT_ID = "document-1"
        val WORD = mapOf(1 to "ONE", 2 to "TWO", 3 to "THREE", 4 to "FOUR")
    }
}

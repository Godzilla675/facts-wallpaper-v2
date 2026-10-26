package com.factswallpaper.core.ocr

import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class OcrTextRendererTest {

    @Test
    fun `pages are rendered in ascending order with stable page markers`() {
        val text = OcrTextRenderer.render(
            document = document(),
            chunks = listOf(chunk(3, "Gamma"), chunk(1, "Alpha"), chunk(2, "Beta")),
            rangeStart = 1,
            rangeEnd = 3,
        )

        assertThat(text).isEqualTo(
            """
            # Document: Physics
            # Pages: 1 to 3
            # OCR Model: TESSERACT (ara)

            --- [PAGE 1] ---
            Alpha

            --- [PAGE 2] ---
            Beta

            --- [PAGE 3] ---
            Gamma
            """.trimIndent() + "\n",
        )
        assertThat(text.indexOf("--- [PAGE 1] ---")).isLessThan(text.indexOf("--- [PAGE 2] ---"))
        assertThat(text.indexOf("--- [PAGE 2] ---")).isLessThan(text.indexOf("--- [PAGE 3] ---"))
    }

    @Test
    fun `trailing whitespace in extracted text is trimmed but inner lines are kept`() {
        val text = OcrTextRenderer.render(
            document = document(),
            chunks = listOf(chunk(1, "First line\n\nSecond line\n   \n\n")),
            rangeStart = 1,
            rangeEnd = 1,
        )

        assertThat(text).contains("--- [PAGE 1] ---\nFirst line\n\nSecond line\n")
        assertThat(text).doesNotContain("Second line\n   ")
    }

    @Test
    fun `pages outside the requested range are excluded`() {
        val text = OcrTextRenderer.render(
            document = document(),
            chunks = listOf(chunk(1, "Cover"), chunk(2, "Chapter"), chunk(3, "Notes")),
            rangeStart = 2,
            rangeEnd = 2,
        )

        assertThat(text).contains("# Pages: 2 to 2")
        assertThat(text).contains("--- [PAGE 2] ---\nChapter\n")
        assertThat(text).doesNotContain("Cover")
        assertThat(text).doesNotContain("Notes")
        assertThat(text).doesNotContain("PAGE 1]")
        assertThat(text).doesNotContain("PAGE 3]")
    }

    @Test
    fun `an in-progress chunk counts as missing and is reported`() {
        val exception = assertThrows(OcrIncompleteException::class.java) {
            OcrTextRenderer.render(
                document = document(),
                chunks = listOf(
                    chunk(1, "Alpha"),
                    chunk(2, "half written", status = "IN_PROGRESS"),
                ),
                rangeStart = 1,
                rangeEnd = 2,
            )
        }

        assertThat(exception.missingPages).containsExactly(2)
    }

    @Test
    fun `all missing pages are reported in range order`() {
        val exception = assertThrows(OcrIncompleteException::class.java) {
            OcrTextRenderer.render(
                document = document(),
                chunks = listOf(chunk(2, "Beta")),
                rangeStart = 1,
                rangeEnd = 4,
            )
        }

        assertThat(exception.missingPages).containsExactly(1, 3, 4).inOrder()
    }

    @Test
    fun `the header falls back to ML Kit when no engine was persisted`() {
        val text = OcrTextRenderer.render(
            document = document(ocrEngine = null, language = "eng"),
            chunks = listOf(chunk(1, "Text")),
            rangeStart = 1,
            rangeEnd = 1,
        )

        assertThat(text).contains("# OCR Model: ML_KIT (eng)")
    }

    private fun chunk(
        page: Int,
        text: String,
        status: String = "COMPLETED",
    ) = DocumentChunk(
        documentId = DOCUMENT_ID,
        pageNumber = page,
        extractedText = text,
        status = status,
        isFromOcr = true,
        updatedAtEpochMs = 1L,
    )

    private fun document(
        ocrEngine: OcrEngineType? = OcrEngineType.TESSERACT,
        language: String = "ara",
    ) = Document(
        id = DOCUMENT_ID,
        title = "Physics",
        sourceUri = "content://fixture",
        localFilePath = "/tmp/fixture.pdf",
        fileHash = "hash",
        fileSizeBytes = 1_000L,
        pageCount = 3,
        pagesWithText = 0,
        processingRoute = PdfProcessingRoute.LOCAL_OCR,
        ocrEngine = ocrEngine,
        ocrLanguageCode = language,
        extractionStatus = ExtractionStatus.OCR_DONE,
        createdAtEpochMs = 1L,
    )

    private companion object {
        const val DOCUMENT_ID = "document-1"
    }
}

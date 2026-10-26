package com.factswallpaper.core.ocr

import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcrCheckpointResolverTest {

    @Test
    fun `a page started but not committed is restarted from scratch`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 89, lastCompleted = 88),
            settings = settings(1, 240),
        )

        assertThat(resume.resumeFromPage).isEqualTo(89)
        assertThat(resume.restartsInterruptedPage).isTrue()
        assertThat(resume.completedPages).isEqualTo(88)
        assertThat(resume.totalPages).isEqualTo(240)
        assertThat(resume.alreadyComplete).isFalse()
    }

    @Test
    fun `the page after the last committed page is resumed when nothing was interrupted`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 88, lastCompleted = 88),
            settings = settings(1, 240),
        )

        assertThat(resume.resumeFromPage).isEqualTo(89)
        assertThat(resume.restartsInterruptedPage).isFalse()
        assertThat(resume.completedPages).isEqualTo(88)
    }

    @Test
    fun `a fresh document starts at the first page of the range`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 0, lastCompleted = 0),
            settings = settings(12, 48),
        )

        assertThat(resume.resumeFromPage).isEqualTo(12)
        assertThat(resume.completedPages).isEqualTo(0)
        assertThat(resume.totalPages).isEqualTo(37)
        assertThat(resume.restartsInterruptedPage).isFalse()
    }

    @Test
    fun `completion before the range start still resumes at the range start`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 11, lastCompleted = 11),
            settings = settings(12, 48),
        )

        assertThat(resume.resumeFromPage).isEqualTo(12)
        assertThat(resume.completedPages).isEqualTo(0)
    }

    @Test
    fun `an interrupted page inside the range is restarted`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 13, lastCompleted = 12),
            settings = settings(12, 48),
        )

        assertThat(resume.resumeFromPage).isEqualTo(13)
        assertThat(resume.restartsInterruptedPage).isTrue()
        assertThat(resume.completedPages).isEqualTo(1)
    }

    @Test
    fun `a fully completed range is not processed again`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 48, lastCompleted = 48),
            settings = settings(12, 48),
        )

        assertThat(resume.alreadyComplete).isTrue()
        assertThat(resume.resumeFromPage).isEqualTo(49)
        assertThat(resume.restartsInterruptedPage).isFalse()
        assertThat(resume.completedPages).isEqualTo(37)
    }

    @Test
    fun `a stale marker beyond the range is treated as complete`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 60, lastCompleted = 60),
            settings = settings(12, 48),
        )

        assertThat(resume.alreadyComplete).isTrue()
        assertThat(resume.completedPages).isEqualTo(37)
    }

    @Test
    fun `invalid persisted ranges are clamped to the document`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 0, lastCompleted = 0, pageCount = 5),
            settings = settings(0, 999),
        )

        assertThat(resume.rangeStart).isEqualTo(1)
        assertThat(resume.rangeEnd).isEqualTo(5)
        assertThat(resume.resumeFromPage).isEqualTo(1)
        assertThat(resume.totalPages).isEqualTo(5)
    }

    @Test
    fun `a document with no pages still yields a safe one-page range`() {
        val resume = OcrCheckpointResolver.resolve(
            document = document(lastStarted = 0, lastCompleted = 0, pageCount = 0),
            settings = settings(1, 10),
        )

        assertThat(resume.rangeStart).isEqualTo(1)
        assertThat(resume.rangeEnd).isEqualTo(1)
        assertThat(resume.totalPages).isEqualTo(1)
    }

    private fun settings(start: Int, end: Int) = DocumentSettings(
        documentId = DOCUMENT_ID,
        pageRangeStart = start,
        pageRangeEnd = end,
    )

    private fun document(
        lastStarted: Int,
        lastCompleted: Int,
        pageCount: Int = 300,
    ) = Document(
        id = DOCUMENT_ID,
        title = "OCR fixture",
        sourceUri = "content://fixture",
        localFilePath = "/tmp/fixture.pdf",
        fileHash = "hash",
        fileSizeBytes = 1_000L,
        pageCount = pageCount,
        pagesWithText = 0,
        processingRoute = PdfProcessingRoute.LOCAL_OCR,
        ocrLastStartedPage = lastStarted,
        ocrLastCompletedPage = lastCompleted,
        extractionStatus = ExtractionStatus.OCR_PAUSED,
        createdAtEpochMs = 1L,
    )

    private companion object {
        const val DOCUMENT_ID = "document-1"
    }
}

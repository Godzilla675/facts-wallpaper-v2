package com.factswallpaper.core.pdf

import android.content.Context
import com.factswallpaper.core.common.FactsDispatchers
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.text.PDFTextStripper
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Low-level PDFBox access shared by inspection and text extraction. */
@Singleton
class PdfBoxDocumentReader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtime: PdfBoxRuntime,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun readMetadata(
        file: File,
        sourceUri: String,
        displayName: String?,
        fileHash: String,
    ): PdfMetadata = withContext(dispatchers.io) {
        runtime.initialize()
        open(file).use { document ->
            val pageCount = document.numberOfPages
            if (pageCount < 1) {
                throw PdfOpenException(
                    PdfImportErrorCode.CORRUPT_PDF,
                    IllegalStateException("PDF contains no pages"),
                )
            }
            val pages = readPages(document, PdfPageRange.all(pageCount), includeText = false)
            val stats = pages.map { PdfPageTextStat(pageNumber = it.pageNumber, charCount = it.charCount) }
            val embeddedTitle = document.documentInformation?.title?.trim()?.takeIf { it.isNotEmpty() }
            PdfMetadata(
                title = deriveTitle(embeddedTitle, displayName),
                embeddedTitle = embeddedTitle,
                sourceUri = sourceUri,
                localFilePath = file.absolutePath,
                fileHash = fileHash,
                fileSizeBytes = file.length(),
                pageCount = pageCount,
                pageTextStats = stats,
            )
        }
    }

    suspend fun extractRange(file: File, range: PdfPageRange): PdfExtractionResult =
        withContext(dispatchers.io) {
            runtime.initialize()
            open(file).use { document ->
                val safeRange = range.coerceTo(document.numberOfPages)
                val pages = readPages(document, safeRange, includeText = true)
                PdfExtractionResult(pages = pages, range = safeRange)
            }
        }

    private fun open(file: File): PDDocument = try {
        PDDocument.load(file)
    } catch (password: InvalidPasswordException) {
        throw PdfOpenException(PdfImportErrorCode.ENCRYPTED_PDF, password)
    } catch (error: Throwable) {
        throw PdfOpenException(PdfImportErrorCode.CORRUPT_PDF, error)
    }

    /**
     * Extracts text per 1-indexed page in [range] using a single [PDFTextStripper]
     * instance configured with `startPage = pageNumber` and `endPage = pageNumber`
     * (spec D3), skipping pages that have no content stream.
     */
    private fun readPages(
        document: PDDocument,
        range: PdfPageRange,
        includeText: Boolean,
    ): List<PdfPageText> {
        val stripper = PDFTextStripper().apply {
            sortByPosition = true
        }
        return (range.startPage..range.endPage).map { pageNumber ->
            val page = document.getPage(pageNumber - 1)
            val raw = if (page.hasContents()) {
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                stripper.getText(document)
            } else {
                ""
            }
            PdfPageText(
                pageNumber = pageNumber,
                text = if (includeText) raw.trim() else raw,
            )
        }
    }

    private fun deriveTitle(embeddedTitle: String?, displayName: String?): String {
        if (!embeddedTitle.isNullOrBlank() && !embeddedTitle.equals("untitled", ignoreCase = true)) {
            return embeddedTitle
        }
        val fromName = displayName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return fromName ?: context.getString(R.string.pdf_default_title)
    }
}

class PdfOpenException(
    val code: PdfImportErrorCode,
    cause: Throwable,
) : Exception(code.name, cause)

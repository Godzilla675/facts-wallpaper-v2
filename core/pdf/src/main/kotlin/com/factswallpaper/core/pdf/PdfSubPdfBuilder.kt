package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.model.Document
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Builds the page-range-filtered sub-PDF that the direct-PDF route uploads
 * (spec 11.4). Unfiltered documents reuse their original copy so no bytes are
 * duplicated; filtered ranges are written to deterministic cache paths.
 */
@Singleton
class PdfSubPdfBuilder @Inject constructor(
    private val runtime: PdfBoxRuntime,
    private val fileStore: PdfFileStore,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun build(document: Document, range: PdfPageRange): File = withContext(dispatchers.io) {
        runtime.initialize()
        val source = File(document.localFilePath)
        check(source.isFile) { "Stored PDF is missing for ${document.id}" }

        PDDocument.load(source).use { pdf ->
            val safeRange = range.coerceTo(pdf.numberOfPages)
            if (safeRange.startPage == 1 && safeRange.endPage == pdf.numberOfPages) {
                return@withContext source
            }

            val target = fileStore.directPdfFile(document.id, safeRange)
            target.parentFile?.mkdirs()
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.delete()
            PDDocument().use { output ->
                for (pageNumber in safeRange.startPage..safeRange.endPage) {
                    output.importPage(pdf.getPage(pageNumber - 1))
                }
                output.save(temp)
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                temp.delete()
            }
            fileStore.deleteOtherDirectPdfs(document.id, keep = target)
            target
        }
    }
}

package com.factswallpaper.core.ocr

import android.content.Context
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/** Minimal chunk source so the deterministic text renderer is unit-testable. */
object OcrTextRenderer {

    fun render(
        document: Document,
        chunks: List<DocumentChunk>,
        rangeStart: Int,
        rangeEnd: Int,
    ): String {
        val completed = chunks
            .filter { it.pageNumber in rangeStart..rangeEnd && it.status == STATUS_COMPLETED }
            .associateBy { it.pageNumber }
        val missing = (rangeStart..rangeEnd).filter { it !in completed }
        if (missing.isNotEmpty()) throw OcrIncompleteException(missing)

        val engine = document.ocrEngine?.name ?: "ML_KIT"
        val builder = StringBuilder()
        builder.append("# Document: ").append(document.title).append('\n')
        builder.append("# Pages: ").append(rangeStart).append(" to ").append(rangeEnd).append('\n')
        builder.append("# OCR Model: ").append(engine)
            .append(" (").append(document.ocrLanguageCode).append(")\n")
        builder.append('\n')
        for (page in rangeStart..rangeEnd) {
            val chunk = completed.getValue(page)
            builder.append("--- [PAGE ").append(page).append("] ---\n")
            builder.append(chunk.extractedText.trimEnd()).append('\n')
            if (page != rangeEnd) builder.append('\n')
        }
        return builder.toString()
    }

    private const val STATUS_COMPLETED = "COMPLETED"
}

interface OcrTextFileCompiler {
    fun renderText(
        document: Document,
        chunks: List<DocumentChunk>,
        rangeStart: Int,
        rangeEnd: Int,
    ): String

    suspend fun compile(document: Document, rangeStart: Int, rangeEnd: Int): File
}

/**
 * Writes the compiled page-indexed `.txt` uploaded to the LLM after OCR (F5).
 * Headers and page markers are stable so the AI layer can cite page numbers.
 */
@Singleton
class DefaultOcrTextFileCompiler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepository: DocumentRepository,
    private val dispatchers: FactsDispatchers,
) : OcrTextFileCompiler {

    override fun renderText(
        document: Document,
        chunks: List<DocumentChunk>,
        rangeStart: Int,
        rangeEnd: Int,
    ): String = OcrTextRenderer.render(document, chunks, rangeStart, rangeEnd)

    override suspend fun compile(
        document: Document,
        rangeStart: Int,
        rangeEnd: Int,
    ): File = withContext(dispatchers.io) {
        val chunks = documentRepository.getChunks(document.id, rangeStart, rangeEnd)
        val text = renderText(document, chunks, rangeStart, rangeEnd)
        val directory = File(context.filesDir, "ocr_text").apply { mkdirs() }
        val target = File(directory, "${document.id}.txt")
        val temp = File(directory, "${document.id}.txt.tmp")
        temp.writeText(text, Charsets.UTF_8)
        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            temp.delete()
        }
        target
    }
}

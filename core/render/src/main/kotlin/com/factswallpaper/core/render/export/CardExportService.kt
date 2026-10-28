package com.factswallpaper.core.render.export

import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.render.card.CardBitmapRenderer
import com.factswallpaper.core.render.card.CardRenderOptions
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSeeder
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

enum class CardExportFormat {
    ZIP,
    PDF,
    MARKDOWN,
}

@Singleton
class CardExportService @Inject constructor(
    private val renderer: CardBitmapRenderer,
    private val templateRepository: TemplateRepository,
    private val templateSeeder: CardTemplateSeeder,
    private val dispatchers: FactsDispatchers,
) {
    suspend fun renderCard(fact: Fact): Bitmap = withContext(dispatchers.io) {
        renderCards(listOf(fact)).first().second
    }

    suspend fun export(
        facts: List<Fact>,
        format: CardExportFormat,
        outputStream: OutputStream,
    ) = withContext(dispatchers.io) {
        when (format) {
            CardExportFormat.ZIP -> exportZip(facts, outputStream)
            CardExportFormat.PDF -> exportPdf(facts, outputStream)
            CardExportFormat.MARKDOWN -> exportMarkdown(facts, outputStream)
        }
    }

    private suspend fun renderCards(facts: List<Fact>): List<Pair<Fact, Bitmap>> {
        templateSeeder.ensureSeeded()
        val allTemplates = templateRepository.getTemplates()
        val defaultTemplate = allTemplates.firstOrNull { it.isDefault }
            ?: BuiltInTemplates.byId(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: allTemplates.firstOrNull()
            ?: error("No card template available")
        val defaultSpec = CardTemplateCodec.fromCardTemplate(defaultTemplate)
            ?: BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: error("No card template spec available")

        val options = CardRenderOptions(width = 1080, height = 1920)

        return facts.map { fact ->
            val template = fact.preferredTemplateId?.let { templateRepository.getTemplate(it) }
                ?: defaultTemplate
            val spec = CardTemplateCodec.fromCardTemplate(template) ?: defaultSpec
            val outcome = renderer.render(fact, template, spec, options)
            fact to outcome.bitmap
        }
    }

    private suspend fun exportZip(facts: List<Fact>, outputStream: OutputStream) {
        val rendered = renderCards(facts)
        ZipOutputStream(outputStream.buffered()).use { zipOut ->
            rendered.forEachIndexed { index, (fact, bitmap) ->
                val entryName = "card_%03d_%s.png".format(index + 1, fact.id.take(8))
                zipOut.putNextEntry(ZipEntry(entryName))
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                zipOut.closeEntry()
            }
            zipOut.finish()
        }
    }

    private suspend fun exportPdf(facts: List<Fact>, outputStream: OutputStream) {
        val rendered = renderCards(facts)
        val pdfDocument = PdfDocument()
        try {
            rendered.forEachIndexed { index, (_, bitmap) ->
                val pageInfo = PdfDocument.PageInfo.Builder(1080, 1920, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                pdfDocument.finishPage(page)
            }
            pdfDocument.writeTo(outputStream)
        } finally {
            pdfDocument.close()
        }
    }

    private fun exportMarkdown(facts: List<Fact>, outputStream: OutputStream) {
        OutputStreamWriter(outputStream).use { writer ->
            writer.write("# Facts Study Export\n\n")
            writer.write("Exported ${facts.size} study cards.\n\n")
            facts.forEachIndexed { idx, fact ->
                writer.write("## ${idx + 1}. [${fact.templateType}] ${fact.sourceLessonTitle ?: "Study Card"}\n\n")
                writer.write("${fact.text}\n\n")
                if (!fact.latex.isNullOrBlank()) {
                    writer.write("```latex\n${fact.latex}\n```\n\n")
                }
                if (fact.tags.isNotEmpty()) {
                    writer.write("Tags: ${fact.tags.joinToString(", ")}\n\n")
                }
                writer.write("Reviews: ${fact.reviewGotItCount} got it / ${fact.reviewAgainCount} again\n\n---\n\n")
            }
        }
    }
}

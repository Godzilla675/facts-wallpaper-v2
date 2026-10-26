package com.factswallpaper.core.ocr

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OcrPipelineTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val testDispatchers = FactsDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )

    @Test
    fun `restarts interrupted page 89 from scratch and compiles txt without re-OCRing pages 1 to 88`() = runTest {
        val initialChunks = (1..88).map { page ->
            DocumentChunk(
                documentId = "doc-resume",
                pageNumber = page,
                extractedText = "Already committed page $page",
                status = "COMPLETED",
                isFromOcr = true,
                ocrConfidence = 0.92f,
                updatedAtEpochMs = 1L,
            )
        } + DocumentChunk(
            documentId = "doc-resume",
            pageNumber = 89,
            extractedText = "Half-written stale text from killed process",
            status = "IN_PROGRESS",
            isFromOcr = true,
            ocrConfidence = null,
            updatedAtEpochMs = 2L,
        )

        val repo = FakeOcrDocumentRepository(
            initialDocument = Document(
                id = "doc-resume",
                title = "Neuroscience Scan",
                sourceUri = "content://scans/neuro.pdf",
                localFilePath = "/data/pdfs/doc-resume.pdf",
                fileHash = "hash-resume",
                fileSizeBytes = 5_000_000L,
                pageCount = 90,
                pagesWithText = 0,
                processingRoute = PdfProcessingRoute.LOCAL_OCR,
                ocrEngine = OcrEngineType.ML_KIT,
                ocrLanguageCode = "en",
                ocrLastStartedPage = 89,
                ocrLastCompletedPage = 88,
                extractionStatus = ExtractionStatus.OCR_PAUSED,
                createdAtEpochMs = 1L,
            ),
            initialSettings = DocumentSettings(
                documentId = "doc-resume",
                pageRangeStart = 1,
                pageRangeEnd = 90,
                overrideOcrPageLimit = true,
                ocrEngine = OcrEngineType.ML_KIT,
                ocrLanguageCode = "en",
            ),
            initialChunks = initialChunks,
        )

        val renderedImages = mutableListOf<OcrPageImage>()
        val pageRenderer = object : OcrPageRenderer {
            override suspend fun render(
                localFilePath: String,
                pageNumber: Int,
                scale: Float,
                maxLongestEdgePx: Int,
            ): OcrPageImage = OcrPageImage.forTesting(pageNumber).also { renderedImages += it }
        }
        val recognizedPages = mutableListOf<Int>()
        val engine = object : OcrEngine {
            override val engineType: OcrEngineType = OcrEngineType.ML_KIT
            override suspend fun recognize(image: OcrPageImage, language: OcrLanguage): OcrResult {
                recognizedPages += image.pageNumber
                return OcrResult(text = "Fresh OCR text for page ${image.pageNumber}", confidence = null)
            }
            override fun close() = Unit
        }
        val compiler = FakeOcrTextFileCompiler(temporaryFolder.root, repo)
        val pipeline = DefaultOcrPipeline(
            documentRepository = repo,
            pageRenderer = pageRenderer,
            engineFactory = object : OcrEngineFactory {
                override fun create(language: OcrLanguage): OcrEngine = engine
            },
            textFileCompiler = compiler,
            dispatchers = testDispatchers,
        )

        val outcome = pipeline.process("doc-resume")

        assertThat(outcome).isInstanceOf(OcrPipelineOutcome.Completed::class.java)
        assertThat(recognizedPages).containsExactly(89, 90).inOrder()
        assertThat(renderedImages).hasSize(2)
        assertThat(renderedImages.all { it.isClosed }).isTrue()

        val updatedDoc = repo.getDocument("doc-resume")!!
        assertThat(updatedDoc.ocrLastStartedPage).isEqualTo(90)
        assertThat(updatedDoc.ocrLastCompletedPage).isEqualTo(90)
        assertThat(updatedDoc.extractionStatus).isEqualTo(ExtractionStatus.OCR_DONE)
        assertThat(updatedDoc.compiledOcrTextPath).isNotNull()

        val compiledFile = File(updatedDoc.compiledOcrTextPath!!)
        assertThat(compiledFile.exists()).isTrue()
        val compiledText = compiledFile.readText()
        assertThat(compiledText).contains("# Document: Neuroscience Scan")
        assertThat(compiledText).contains("--- [PAGE 89] ---\nFresh OCR text for page 89")
        assertThat(compiledText).contains("--- [PAGE 90] ---\nFresh OCR text for page 90")
        assertThat(compiledText).doesNotContain("Half-written stale text")
    }

    @Test
    fun `preserves digital text pages with at least 40 chars unless forceFullLocalOcr is enabled`() = runTest {
        val digitalPage1 = "This digital page already has well over forty non-whitespace characters extracted via PDFBox."
        val initialChunks = listOf(
            DocumentChunk(
                documentId = "doc-mixed",
                pageNumber = 1,
                extractedText = digitalPage1,
                status = "COMPLETED",
                isFromOcr = false,
                ocrConfidence = null,
                updatedAtEpochMs = 1L,
            ),
            DocumentChunk(
                documentId = "doc-mixed",
                pageNumber = 2,
                extractedText = "tiny",
                status = "COMPLETED",
                isFromOcr = false,
                ocrConfidence = null,
                updatedAtEpochMs = 1L,
            ),
        )
        val repo = FakeOcrDocumentRepository(
            initialDocument = Document(
                id = "doc-mixed",
                title = "Mixed Document",
                sourceUri = "content://scans/mixed.pdf",
                localFilePath = "/data/pdfs/doc-mixed.pdf",
                fileHash = "hash-mixed",
                fileSizeBytes = 100_000L,
                pageCount = 2,
                pagesWithText = 1,
                processingRoute = PdfProcessingRoute.LOCAL_OCR,
                ocrEngine = OcrEngineType.ML_KIT,
                ocrLanguageCode = "en",
                ocrLastStartedPage = 0,
                ocrLastCompletedPage = 0,
                extractionStatus = ExtractionStatus.IMPORTED,
                createdAtEpochMs = 1L,
            ),
            initialSettings = DocumentSettings(
                documentId = "doc-mixed",
                pageRangeStart = 1,
                pageRangeEnd = 2,
                forceFullLocalOcr = false,
                ocrEngine = OcrEngineType.ML_KIT,
                ocrLanguageCode = "en",
            ),
            initialChunks = initialChunks,
        )
        val recognizedPages = mutableListOf<Int>()
        val pipeline = DefaultOcrPipeline(
            documentRepository = repo,
            pageRenderer = object : OcrPageRenderer {
                override suspend fun render(
                    localFilePath: String,
                    pageNumber: Int,
                    scale: Float,
                    maxLongestEdgePx: Int,
                ): OcrPageImage = OcrPageImage.forTesting(pageNumber)
            },
            engineFactory = object : OcrEngineFactory {
                override fun create(language: OcrLanguage): OcrEngine = object : OcrEngine {
                    override val engineType: OcrEngineType = OcrEngineType.ML_KIT
                    override suspend fun recognize(image: OcrPageImage, language: OcrLanguage): OcrResult {
                        recognizedPages += image.pageNumber
                        return OcrResult(text = "OCRed page ${image.pageNumber}", confidence = null)
                    }
                    override fun close() = Unit
                }
            },
            textFileCompiler = FakeOcrTextFileCompiler(temporaryFolder.root, repo),
            dispatchers = testDispatchers,
        )

        val outcome = pipeline.process("doc-mixed")

        assertThat(outcome).isInstanceOf(OcrPipelineOutcome.Completed::class.java)
        // Page 1 has >= 40 chars of digital text so only page 2 is rasterized & OCRed
        assertThat(recognizedPages).containsExactly(2)
        val chunks = repo.getChunks("doc-mixed", 1, 2)
        assertThat(chunks.first { it.pageNumber == 1 }.isFromOcr).isFalse()
        assertThat(chunks.first { it.pageNumber == 1 }.extractedText).isEqualTo(digitalPage1)
        assertThat(chunks.first { it.pageNumber == 2 }.isFromOcr).isTrue()
        assertThat(chunks.first { it.pageNumber == 2 }.extractedText).isEqualTo("OCRed page 2")
    }

    @Test
    fun `blocks OCR_REQUIRES_OVERRIDE when user override is false`() = runTest {
        val repo = FakeOcrDocumentRepository(
            initialDocument = Document(
                id = "doc-blocked",
                title = "Large Scan",
                sourceUri = "content://scans/large.pdf",
                localFilePath = "/data/pdfs/doc-blocked.pdf",
                fileHash = "hash-blocked",
                fileSizeBytes = 10_000_000L,
                pageCount = 45,
                pagesWithText = 0,
                processingRoute = PdfProcessingRoute.OCR_REQUIRES_OVERRIDE,
                ocrEngine = OcrEngineType.ML_KIT,
                ocrLanguageCode = "en",
                ocrLastStartedPage = 0,
                ocrLastCompletedPage = 0,
                extractionStatus = ExtractionStatus.IMPORTED,
                createdAtEpochMs = 1L,
            ),
            initialSettings = DocumentSettings(
                documentId = "doc-blocked",
                pageRangeStart = 1,
                pageRangeEnd = 45,
                overrideOcrPageLimit = false,
                forceFullLocalOcr = false,
            ),
            initialChunks = emptyList(),
        )
        val pipeline = DefaultOcrPipeline(
            documentRepository = repo,
            pageRenderer = object : OcrPageRenderer {
                override suspend fun render(
                    localFilePath: String,
                    pageNumber: Int,
                    scale: Float,
                    maxLongestEdgePx: Int,
                ): OcrPageImage = error("Should not render when override is not granted")
            },
            engineFactory = object : OcrEngineFactory {
                override fun create(language: OcrLanguage): OcrEngine =
                    error("Should not create engine when override is not granted")
            },
            textFileCompiler = FakeOcrTextFileCompiler(temporaryFolder.root, repo),
            dispatchers = testDispatchers,
        )

        val outcome = pipeline.process("doc-blocked")

        assertThat(outcome).isInstanceOf(OcrPipelineOutcome.Failed::class.java)
        val doc = repo.getDocument("doc-blocked")!!
        assertThat(doc.extractionStatus).isEqualTo(ExtractionStatus.ERROR)
        assertThat(doc.lastErrorMessage).contains("20 pages")
    }
}

private class FakeOcrTextFileCompiler(
    private val rootDir: File,
    private val repository: DocumentRepository,
) : OcrTextFileCompiler {
    override fun renderText(
        document: Document,
        chunks: List<DocumentChunk>,
        rangeStart: Int,
        rangeEnd: Int,
    ): String = OcrTextRenderer.render(document, chunks, rangeStart, rangeEnd)

    override suspend fun compile(document: Document, rangeStart: Int, rangeEnd: Int): File {
        val chunks = repository.getChunks(document.id, rangeStart, rangeEnd)
        val text = renderText(document, chunks, rangeStart, rangeEnd)
        val outDir = File(rootDir, "ocr_text").apply { mkdirs() }
        return File(outDir, "${document.id}.txt").apply { writeText(text) }
    }
}

private class FakeOcrDocumentRepository(
    initialDocument: Document,
    initialSettings: DocumentSettings,
    initialChunks: List<DocumentChunk>,
) : DocumentRepository {
    private var document: Document? = initialDocument
    private var settings: DocumentSettings? = initialSettings
    private val chunks = initialChunks.associateBy { it.pageNumber }.toMutableMap()

    override fun observeDocuments(): Flow<List<Document>> = flowOf(listOfNotNull(document))
    override fun observeDocument(documentId: String): Flow<Document?> = flowOf(document)
    override suspend fun getDocument(documentId: String): Document? =
        document?.takeIf { it.id == documentId }
    override suspend fun findByHash(fileHash: String): Document? =
        document?.takeIf { it.fileHash == fileHash }
    override suspend fun getLocalFilePath(documentId: String): String? =
        document?.takeIf { it.id == documentId }?.localFilePath
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = flowOf(settings)
    override suspend fun getSettings(documentId: String): DocumentSettings? = settings
    override fun observeAllSettings(): Flow<List<DocumentSettings>> = flowOf(listOfNotNull(settings))
    override suspend fun saveDocument(document: Document) {
        this.document = document
    }
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {
        this.document = document
        this.settings = settings
    }
    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) {
        this.document = document
        this.settings = settings
        chunks.clear()
    }
    override suspend fun saveSettings(settings: DocumentSettings) {
        this.settings = settings
    }
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {
        document = document?.copy(includedInRotation = included)
    }
    override suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String?,
    ) {
        document = document?.copy(extractionStatus = status, lastErrorMessage = errorMessage)
    }
    override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
        document = null
        return AppResult.Success(Unit)
    }
    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = flowOf(emptyMap())
    override fun observeChunks(documentId: String): Flow<List<DocumentChunk>> =
        flowOf(chunks.values.sortedBy { it.pageNumber })
    override suspend fun getChunks(documentId: String, startPage: Int, endPage: Int): List<DocumentChunk> =
        chunks.values.filter { it.pageNumber in startPage..endPage }.sortedBy { it.pageNumber }
    override suspend fun upsertChunk(chunk: DocumentChunk) {
        chunks[chunk.pageNumber] = chunk
    }
    override suspend fun clearChunks(documentId: String) {
        chunks.clear()
    }
    override suspend fun markOcrPageStarted(documentId: String, page: Int) {
        document = document?.copy(
            ocrLastStartedPage = page,
            extractionStatus = ExtractionStatus.OCR_IN_PROGRESS,
        )
    }
    override suspend fun commitOcrPage(
        documentId: String,
        page: Int,
        text: String,
        confidence: Float?,
    ) {
        chunks[page] = DocumentChunk(
            documentId = documentId,
            pageNumber = page,
            extractedText = text,
            status = "COMPLETED",
            isFromOcr = true,
            ocrConfidence = confidence,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        document = document?.copy(
            ocrLastStartedPage = maxOf(document?.ocrLastStartedPage ?: 0, page),
            ocrLastCompletedPage = maxOf(document?.ocrLastCompletedPage ?: 0, page),
            extractionStatus = ExtractionStatus.OCR_IN_PROGRESS,
        )
    }
    override suspend fun updateCompiledText(
        documentId: String,
        compiledTextPath: String,
        status: ExtractionStatus,
    ) {
        document = document?.copy(
            compiledOcrTextPath = compiledTextPath,
            extractionStatus = status,
        )
    }
}

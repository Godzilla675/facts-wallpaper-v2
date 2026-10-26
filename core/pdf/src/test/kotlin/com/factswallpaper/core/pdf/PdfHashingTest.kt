package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfHashingTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `sha256 matches the known FIPS test vector`() {
        assertThat(PdfHashing.sha256("hello".toByteArray()))
            .isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824")
    }

    @Test
    fun `identical bytes produce identical hashes and different bytes do not`() {
        val first = "%PDF-1.7\nsynthetic document".toByteArray()
        val same = first.copyOf()
        val changed = "%PDF-1.7\nsynthetic documenT".toByteArray()

        assertThat(PdfHashing.sha256(first)).isEqualTo(PdfHashing.sha256(same))
        assertThat(PdfHashing.sha256(first)).isNotEqualTo(PdfHashing.sha256(changed))
    }

    @Test
    fun `copyAndHash writes every byte while hashing the copied content`() {
        val content = ByteArray(32_768) { index -> (index % 251).toByte() }
        val output = ByteArrayOutputStream()

        val result = PdfHashing.copyAndHash(ByteArrayInputStream(content), output)

        assertThat(result.bytesCopied).isEqualTo(content.size.toLong())
        assertThat(output.toByteArray()).isEqualTo(content)
        assertThat(result.sha256).isEqualTo(PdfHashing.sha256(content))
    }

    @Test
    fun `file hashing streams the same digest as the byte vector`() {
        val file = temporaryFolder.newFile("document.pdf")
        val content = "%PDF-1.4\nfile-backed content\n%%EOF".toByteArray()
        file.writeBytes(content)

        assertThat(PdfHashing.sha256(file)).isEqualTo(PdfHashing.sha256(content))
        assertThat(PdfHashing.sha256(file).length).isEqualTo(64)
    }

    @Test
    fun `an empty stream still hashes deterministically`() {
        val output = ByteArrayOutputStream()

        val result = PdfHashing.copyAndHash(ByteArrayInputStream(ByteArray(0)), output)

        assertThat(result.bytesCopied).isEqualTo(0L)
        assertThat(output.size()).isEqualTo(0)
        assertThat(result.sha256)
            .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
    }

    @Test
    fun `hashes are lowercase fixed width hex strings`() {
        val hash = PdfHashing.sha256(File.createTempFile("facts", ".bin").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        })

        assertThat(hash).hasLength(64)
        assertThat(hash).matches("[0-9a-f]{64}")
    }

    @Test
    fun `DuplicateHashDetector matches existing document by content hash regardless of filename`() = runTest {
        val content = "%PDF-1.7\nduplicate payload\n%%EOF".toByteArray()
        val expectedHash = PdfHashing.sha256(content)
        val existingDoc = Document(
            id = "doc-existing",
            title = "Original Lecture Notes",
            sourceUri = "content://downloads/lecture.pdf",
            localFilePath = "/data/files/pdfs/doc-existing.pdf",
            compiledOcrTextPath = null,
            fileHash = expectedHash,
            fileSizeBytes = content.size.toLong(),
            pageCount = 12,
            pagesWithText = 12,
            processingRoute = PdfProcessingRoute.EXTRACT_TEXT,
            ocrEngine = null,
            ocrLanguageCode = "en",
            ocrLastStartedPage = 0,
            ocrLastCompletedPage = 0,
            extractionStatus = ExtractionStatus.OCR_DONE,
            lastErrorMessage = null,
            includedInRotation = true,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 1_000L,
        )
        val repository = FakeHashDocumentRepository(listOf(existingDoc))
        val detector = DuplicateHashDetector(repository)

        val renamedCopy = temporaryFolder.newFile("completely-different-name.pdf").apply {
            writeBytes(content)
        }
        val uniqueFile = temporaryFolder.newFile("unique.pdf").apply {
            writeBytes("%PDF-1.7\ndifferent payload\n%%EOF".toByteArray())
        }

        val duplicateMatch = detector.findDuplicateForFile(renamedCopy)
        val uniqueMatch = detector.findDuplicateForFile(uniqueFile)

        assertThat(duplicateMatch).isNotNull()
        assertThat(duplicateMatch?.sha256).isEqualTo(expectedHash)
        assertThat(duplicateMatch?.existingDocument?.id).isEqualTo("doc-existing")
        assertThat(uniqueMatch).isNull()
    }
}

private class FakeHashDocumentRepository(
    private val documents: List<Document>,
) : DocumentRepository {
    override fun observeDocuments(): Flow<List<Document>> = flowOf(documents)
    override fun observeDocument(documentId: String): Flow<Document?> =
        flowOf(documents.firstOrNull { it.id == documentId })
    override suspend fun getDocument(documentId: String): Document? =
        documents.firstOrNull { it.id == documentId }
    override suspend fun findByHash(fileHash: String): Document? =
        documents.firstOrNull { it.fileHash == fileHash }
    override suspend fun getLocalFilePath(documentId: String): String? =
        documents.firstOrNull { it.id == documentId }?.localFilePath
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = flowOf(null)
    override suspend fun getSettings(documentId: String): DocumentSettings? = null
    override fun observeAllSettings(): Flow<List<DocumentSettings>> = flowOf(emptyList())
    override suspend fun saveDocument(document: Document) = Unit
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) = Unit
    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) = Unit
    override suspend fun saveSettings(settings: DocumentSettings) = Unit
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) = Unit
    override suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String?,
    ) = Unit
    override suspend fun deleteDocument(documentId: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = flowOf(emptyMap())
    override fun observeChunks(documentId: String): Flow<List<DocumentChunk>> = flowOf(emptyList())
    override suspend fun getChunks(documentId: String, startPage: Int, endPage: Int): List<DocumentChunk> =
        emptyList()
    override suspend fun upsertChunk(chunk: DocumentChunk) = Unit
    override suspend fun clearChunks(documentId: String) = Unit
    override suspend fun markOcrPageStarted(documentId: String, page: Int) = Unit
    override suspend fun commitOcrPage(
        documentId: String,
        page: Int,
        text: String,
        confidence: Float?,
    ) = Unit
    override suspend fun updateCompiledText(
        documentId: String,
        compiledTextPath: String,
        status: ExtractionStatus,
    ) = Unit
}

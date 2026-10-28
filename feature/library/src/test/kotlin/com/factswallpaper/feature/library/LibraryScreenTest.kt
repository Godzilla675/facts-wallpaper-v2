package com.factswallpaper.feature.library

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.pdf.DocumentProcessingScheduler
import com.factswallpaper.core.pdf.PdfRelinkResult
import com.factswallpaper.core.pdf.PdfRelinkService
import com.factswallpaper.core.ui.R as CoreUiR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Section 10.2 Compose UI coverage for the Library screen: content, empty and
 * error-with-retry states are rendered by the real ViewModel and screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `content state shows the document and wallpaper pool summary`() {
        val repository = FakeLibraryRepository(listOf(document("doc-1", "Biochemistry Chapter 4")))
        showLibrary(repository) { _, _ -> }

        composeRule.onNodeWithText("Biochemistry Chapter 4").assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.library_pool_summary, 12),
        ).assertIsDisplayed()
    }

    @Test
    fun `empty library shows the import call to action`() {
        showLibrary(FakeLibraryRepository(emptyList())) { _, _ -> }

        composeRule.onNodeWithText(context.getString(R.string.library_empty_title))
            .assertIsDisplayed()
    }

    @Test
    fun `error state exposes retry which reloads the collection`() {
        val repository = FakeLibraryRepository(listOf(document("doc-1", "Linear Algebra Notes")))
        repository.failReads = true
        showLibrary(repository) { _, _ -> }

        composeRule.onNodeWithText(context.getString(R.string.library_load_error))
            .assertIsDisplayed()

        repository.failReads = false
        composeRule.onNodeWithText(context.getString(CoreUiR.string.error_state_retry))
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Linear Algebra Notes").assertIsDisplayed()
    }

    private fun showLibrary(
        repository: FakeLibraryRepository,
        onOpen: (String, String) -> Unit,
    ) {
        val viewModel = LibraryViewModel(
            documentRepository = repository,
            rotationPoolRepository = ScreenFakeRotationPoolRepository(),
            scheduler = FakeScheduler(repository),
            settingsRepository = FakeSettingsRepository(),
            pdfRelinkService = ScreenFakePdfRelinkService,
        )
        composeRule.setContent {
            LibraryScreen(
                onImportPdf = {},
                onOpenDocument = { onOpen("outline", it) },
                onOpenFacts = { onOpen("facts", it) },
                viewModel = viewModel,
            )
        }
    }

    private fun document(id: String, title: String) = Document(
        id = id,
        title = title,
        sourceUri = "content://docs/$id.pdf",
        localFilePath = "/data/pdfs/$id.pdf",
        fileHash = "hash-$id",
        fileSizeBytes = 120_000L,
        pageCount = 10,
        pagesWithText = 10,
        processingRoute = PdfProcessingRoute.EXTRACT_TEXT,
        ocrEngine = null,
        ocrLanguageCode = "en",
        ocrLastStartedPage = 0,
        ocrLastCompletedPage = 0,
        extractionStatus = ExtractionStatus.OCR_DONE,
        lastErrorMessage = null,
        includedInRotation = true,
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}

private class FakeScheduler(
    repository: DocumentRepository,
) : DocumentProcessingScheduler(repository)

private class ScreenFakeRotationPoolRepository : RotationPoolRepository {
    override fun observePool(): Flow<List<Fact>> = flowOf(emptyList())

    override fun observePoolSummary(): Flow<RotationPoolSummary> = flowOf(
        RotationPoolSummary(factCount = 12, collectionCount = 2, folderCount = 1),
    )
}

private class FakeLibraryRepository(
    initialDocuments: List<Document>,
) : DocumentRepository {

    private val documents = MutableStateFlow(initialDocuments)
    var failReads: Boolean = false

    override fun observeDocuments(): Flow<List<Document>> =
        if (failReads) flow { throw IllegalStateException("Simulated library failure") } else documents

    override fun observeDocument(documentId: String): Flow<Document?> =
        flowOf(documents.value.firstOrNull { it.id == documentId })

    override suspend fun getDocument(documentId: String): Document? =
        documents.value.firstOrNull { it.id == documentId }

    override suspend fun findByHash(fileHash: String): Document? =
        documents.value.firstOrNull { it.fileHash == fileHash }

    override suspend fun getLocalFilePath(documentId: String): String? =
        documents.value.firstOrNull { it.id == documentId }?.localFilePath

    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = flowOf(null)

    override suspend fun getSettings(documentId: String): DocumentSettings? = null

    override fun observeAllSettings(): Flow<List<DocumentSettings>> = flowOf(emptyList())

    override suspend fun saveDocument(document: Document) {
        documents.value = documents.value.filterNot { it.id == document.id } + document
    }

    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {
        saveDocument(document)
    }

    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) {
        saveDocument(document)
    }

    override suspend fun saveSettings(settings: DocumentSettings) = Unit

    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {
        documents.value = documents.value.map {
            if (it.id == documentId) it.copy(includedInRotation = included) else it
        }
    }

    override suspend fun updateExtractionStatus(
        documentId: String,
        status: ExtractionStatus,
        errorMessage: String?,
    ) = Unit

    override suspend fun updatePipelineProgress(documentId: String, progress: Float) = Unit

    override suspend fun updatePipelineStatus(
        documentId: String,
        status: ExtractionStatus,
        progress: Float,
        errorMessage: String?,
    ) = Unit

    override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
        documents.value = documents.value.filterNot { it.id == documentId }
        return AppResult.Success(Unit)
    }

    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = flowOf(emptyMap())

    override fun observeChunks(documentId: String): Flow<List<DocumentChunk>> = flowOf(emptyList())

    override suspend fun getChunks(
        documentId: String,
        startPage: Int,
        endPage: Int,
    ): List<DocumentChunk> = emptyList()

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

private object ScreenFakePdfRelinkService : PdfRelinkService {
    override suspend fun relink(
        documentId: String,
        expectedSha256: String,
        uriString: String,
    ): PdfRelinkResult = PdfRelinkResult.Failure("not used")
}

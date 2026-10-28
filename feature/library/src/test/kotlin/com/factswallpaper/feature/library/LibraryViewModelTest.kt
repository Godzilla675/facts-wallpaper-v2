package com.factswallpaper.feature.library

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.DocumentInputMode
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.ocr.OcrLanguageCatalog
import com.factswallpaper.core.ocr.OcrModelState
import com.factswallpaper.core.ocr.OcrModelStatus
import com.factswallpaper.core.pdf.DocumentProcessingScheduler
import com.factswallpaper.core.pdf.PdfMetadata
import com.factswallpaper.core.pdf.PdfPageRange
import com.factswallpaper.core.pdf.PdfPageTextStat
import com.factswallpaper.core.pdf.PdfProcessingOptions
import com.factswallpaper.core.pdf.PdfProviderCapabilities
import com.factswallpaper.core.pdf.PdfRouteClassifier
import com.factswallpaper.core.pdf.StagedPdf
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

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
    fun `filters documents by query and cancels scheduled work on delete`() = runTest {
        val doc1 = sampleDocument("doc-1", "Biochemistry Chapter 4")
        val doc2 = sampleDocument("doc-2", "Linear Algebra Notes")
        val repo = FakeLibraryDocumentRepository(listOf(doc1, doc2))
        val scheduler = FakeDocumentProcessingScheduler(repo)
        val viewModel = LibraryViewModel(
            documentRepository = repo,
            rotationPoolRepository = FakeRotationPoolRepository(),
            scheduler = scheduler,
            settingsRepository = FakeSettingsRepository(),
        )

        val collectJob = backgroundScope.launch(testDispatcher) {
            viewModel.uiState.collect()
        }

        val initial = viewModel.uiState.value as LibraryUiState.Content
        assertThat(initial.documents).hasSize(2)

        viewModel.updateQuery("Linear")
        val filtered = viewModel.uiState.value as LibraryUiState.Content
        assertThat(filtered.documents.map { it.id }).containsExactly("doc-2")

        viewModel.clearQuery()
        viewModel.deleteDocument(doc1)
        assertThat(scheduler.cancelledIds).containsExactly("doc-1")
        val afterDelete = viewModel.uiState.value as LibraryUiState.Content
        assertThat(afterDelete.documents.map { it.id }).containsExactly("doc-2")

        collectJob.cancel()
    }

    @Test
    fun `pauseOcr and resumeOcr delegate to DocumentProcessingScheduler`() = runTest {
        val doc = sampleDocument("doc-ocr", "Scanned Treatise")
        val repo = FakeLibraryDocumentRepository(listOf(doc))
        val scheduler = FakeDocumentProcessingScheduler(repo)
        val viewModel = LibraryViewModel(
            documentRepository = repo,
            rotationPoolRepository = FakeRotationPoolRepository(),
            scheduler = scheduler,
            settingsRepository = FakeSettingsRepository(),
        )

        viewModel.pauseOcr("doc-ocr")
        assertThat(scheduler.pausedIds).containsExactly("doc-ocr")

        viewModel.resumeOcr("doc-ocr")
        assertThat(scheduler.resumedIds).containsExactly("doc-ocr")
    }

    @Test
    fun `ImportPdfUiState enforces OCR override and downloadable language pack readiness`() {
        val metadata = PdfMetadata(
            title = "Scanned Arabic Book",
            embeddedTitle = null,
            sourceUri = "content://arabic.pdf",
            localFilePath = "/data/staging/staged-1.pdf",
            fileHash = "hash-ara",
            fileSizeBytes = 4_000_000L,
            pageCount = 30,
            pageTextStats = (1..30).map { PdfPageTextStat(pageNumber = it, charCount = 0) },
        )
        val staged = StagedPdf(
            id = "staged-1",
            filePath = "/data/staging/staged-1.pdf",
            displayName = "arabic.pdf",
            metadata = metadata,
        )
        val range = PdfPageRange(1, 30)
        val classifier = PdfRouteClassifier()

        val blockedDecision = classifier.classify(
            metadata = metadata,
            options = PdfProcessingOptions(
                pageRange = range,
                overrideOcrPageLimit = false,
                ocrEngine = OcrEngineType.TESSERACT,
                ocrLanguageCode = "ara",
            ),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = false),
            ocrPageLimit = 20,
        )
        val blockedState = ImportPdfUiState(
            staged = staged,
            pageRange = range,
            decision = blockedDecision,
            languageCode = "ara",
            modelStates = listOf(
                OcrModelState(
                    language = OcrLanguageCatalog.forCode("ara"),
                    status = OcrModelStatus.INSTALLED,
                ),
            ),
        )
        assertThat(blockedState.requiresOcrOverride).isTrue()
        assertThat(blockedState.canStart).isFalse()

        val overriddenDecision = classifier.classify(
            metadata = metadata,
            options = PdfProcessingOptions(
                pageRange = range,
                overrideOcrPageLimit = true,
                ocrEngine = OcrEngineType.TESSERACT,
                ocrLanguageCode = "ara",
            ),
            capabilities = PdfProviderCapabilities(supportsDirectPdfInput = false),
            ocrPageLimit = 20,
        )
        val missingPackState = blockedState.copy(
            decision = overriddenDecision,
            overrideOcrLimit = true,
            modelStates = listOf(
                OcrModelState(
                    language = OcrLanguageCatalog.forCode("ara"),
                    status = OcrModelStatus.NOT_INSTALLED,
                ),
            ),
        )
        assertThat(missingPackState.selectedLanguageInstalled).isFalse()
        assertThat(missingPackState.canStart).isFalse()

        val readyState = missingPackState.copy(
            modelStates = listOf(
                OcrModelState(
                    language = OcrLanguageCatalog.forCode("ara"),
                    status = OcrModelStatus.INSTALLED,
                ),
            ),
        )
        assertThat(readyState.selectedLanguageInstalled).isTrue()
        assertThat(readyState.canStart).isTrue()
    }

    private fun sampleDocument(id: String, title: String) = Document(
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

private class FakeDocumentProcessingScheduler(
    repository: DocumentRepository,
) : DocumentProcessingScheduler(repository) {
    val enqueuedIds = mutableListOf<String>()
    val resumedIds = mutableListOf<String>()
    val pausedIds = mutableListOf<String>()
    val cancelledIds = mutableListOf<String>()

    override fun enqueue(documentId: String) {
        enqueuedIds += documentId
    }

    override fun resume(documentId: String) {
        resumedIds += documentId
    }

    override suspend fun pause(documentId: String) {
        pausedIds += documentId
    }

    override fun cancel(documentId: String) {
        cancelledIds += documentId
    }
}

private class FakeRotationPoolRepository : RotationPoolRepository {
    override fun observePool(): Flow<List<Fact>> = flowOf(emptyList())
    override fun observePoolSummary(): Flow<RotationPoolSummary> = flowOf(
        RotationPoolSummary(
            factCount = 0,
            collectionCount = 0,
            folderCount = 0,
        ),
    )
}

private class FakeLibraryDocumentRepository(
    initialDocuments: List<Document>,
) : DocumentRepository {
    private val docsFlow = MutableStateFlow(initialDocuments)

    override fun observeDocuments(): Flow<List<Document>> = docsFlow
    override fun observeDocument(documentId: String): Flow<Document?> =
        flowOf(docsFlow.value.firstOrNull { it.id == documentId })
    override suspend fun getDocument(documentId: String): Document? =
        docsFlow.value.firstOrNull { it.id == documentId }
    override suspend fun findByHash(fileHash: String): Document? =
        docsFlow.value.firstOrNull { it.fileHash == fileHash }
    override suspend fun getLocalFilePath(documentId: String): String? =
        docsFlow.value.firstOrNull { it.id == documentId }?.localFilePath
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = flowOf(null)
    override suspend fun getSettings(documentId: String): DocumentSettings? = null
    override fun observeAllSettings(): Flow<List<DocumentSettings>> = flowOf(emptyList())
    override suspend fun saveDocument(document: Document) {
        docsFlow.value = docsFlow.value.filterNot { it.id == document.id } + document
    }
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {
        saveDocument(document)
    }
    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) {
        saveDocument(document)
    }
    override suspend fun saveSettings(settings: DocumentSettings) = Unit
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {
        docsFlow.value = docsFlow.value.map {
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
        docsFlow.value = docsFlow.value.filterNot { it.id == documentId }
        return AppResult.Success(Unit)
    }
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

internal class FakeSettingsRepository : SettingsRepository {
    private val preferences = MutableStateFlow(AppPreferences())
    override fun observePreferences(): Flow<AppPreferences> = preferences
    override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
    override suspend fun setAppThemeMode(mode: ThemeMode) = Unit
    override suspend fun setWallpaperThemeMode(mode: ThemeMode) = Unit
    override suspend fun setRoutingStrategy(strategy: RoutingStrategy) = Unit
    override suspend fun setTaskProfileOverrides(overrides: Map<AiTask, String>) = Unit
    override suspend fun setTaskProfileOverride(task: AiTask, profileId: String?) = Unit
    override suspend fun setCustomTaskPromptSuffixes(suffixes: Map<AiTask, String>) = Unit
    override suspend fun setCustomTaskPromptSuffix(task: AiTask, instruction: String?) = Unit
    override suspend fun setDefaultOcrPageLimit(limit: Int) = Unit
    override suspend fun setAllowAdvancedOcrEngines(allowed: Boolean) = Unit
    override suspend fun setPreRenderQueueSize(size: Int) = Unit
    override suspend fun setEncryptedDatabaseEnabled(enabled: Boolean) = Unit
    override suspend fun setEnableAiDiagnosticsLog(enabled: Boolean) = Unit
    override suspend fun setEnableDoubleTapWallpaperAdvance(enabled: Boolean) = Unit
    override suspend fun setAiCostGuardThresholdUsd(thresholdUsd: Double) = Unit
    override suspend fun setDefaultAutoProcessMode(mode: AutoProcessMode) = Unit
    override suspend fun setDefaultDocumentInputMode(mode: DocumentInputMode) = Unit
    override suspend fun setMaxImagesPerRequest(maxImages: Int) = Unit
    override suspend fun setRotationSource(source: RotationSource) = Unit
    override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit
    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) = Unit
    override suspend fun setRotationPaused(paused: Boolean) = Unit
    override suspend fun setBatterySaverSlowdownEnabled(enabled: Boolean) = Unit
    override suspend fun setDailyFactNotificationEnabled(enabled: Boolean) = Unit
    override suspend fun setDailyFactNotificationTime(timeMinutes: Int) = Unit
    override suspend fun setDynamicColorEnabled(enabled: Boolean) = Unit
    override suspend fun setHapticsEnabled(enabled: Boolean) = Unit
    override suspend fun setLibraryCoachHintDismissed(dismissed: Boolean) = Unit
    override suspend fun setOutlineCoachHintDismissed(dismissed: Boolean) = Unit
}
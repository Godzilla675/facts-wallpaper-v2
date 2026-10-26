package com.factswallpaper.feature.facts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.factswallpaper.core.ai.AiCostEstimate
import com.factswallpaper.core.ai.DiagramGenerationResult
import com.factswallpaper.core.ai.FactDraft
import com.factswallpaper.core.ai.FactsGenerationResult
import com.factswallpaper.core.ai.NetworkMonitor
import com.factswallpaper.core.ai.OutlineGenerationResult
import com.factswallpaper.core.ai.PageText
import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.ai.TemplateGenerationResult
import com.factswallpaper.core.ai.ValidationResult
import com.factswallpaper.core.ai.VerificationEnvelope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.DocumentTextRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactStatusFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class FactsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeFactRepo = FakeFactRepository()
    private val fakeDocRepo = FakeDocumentRepository()
    private val fakeDocTextRepo = FakeDocumentTextRepository()
    private val fakeAiService = FakeStructuredAiService()
    private val fakeNetworkMonitor = FakeNetworkMonitor()
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: FactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        val handle = SavedStateHandle(mapOf("documentId" to "doc-1"))
        viewModel = FactsViewModel(
            savedStateHandle = handle,
            factRepository = fakeFactRepo,
            documentRepository = fakeDocRepo,
            documentTextRepository = fakeDocTextRepo,
            structuredAiService = fakeAiService,
            networkMonitor = fakeNetworkMonitor,
            workManager = workManager,
            preRenderScheduler = CardPreRenderScheduler(context),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `toggleActive flips active flag`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val fact = Fact(
            id = "fact-1",
            documentId = "doc-1",
            lessonId = "lesson-1",
            text = "Test Fact Content",
            active = true,
            sourceBookTitle = "Physics 101",
            createdAtEpochMs = 1000L,
        )
        fakeFactRepo.factsFlow.value = listOf(fact)
        advanceUntilIdle()

        viewModel.toggleActive("fact-1", false)
        advanceUntilIdle()

        assertThat(fakeFactRepo.factsFlow.value.first().active).isFalse()
    }

    @Test
    fun `updateQuery updates filter query`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.updateQuery("gravity")
        advanceUntilIdle()

        val state = viewModel.uiState.value as FactsUiState.Content
        assertThat(state.filter.query).isEqualTo("gravity")
    }

    @Test
    fun `setStatus updates status filter`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.setStatus(FactStatusFilter.FAVORITES)
        advanceUntilIdle()

        val state = viewModel.uiState.value as FactsUiState.Content
        assertThat(state.filter.status).isEqualTo(FactStatusFilter.FAVORITES)
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val onlineFlow = MutableStateFlow(true)
    override fun isOnline(): Boolean = onlineFlow.value
    override fun observeOnline(): Flow<Boolean> = onlineFlow
}

private class FakeFactRepository : FactRepository {
    val factsFlow = MutableStateFlow<List<Fact>>(emptyList())

    override fun observeFacts(filter: FactFilter): Flow<List<Fact>> = factsFlow
    override fun observeFact(factId: String): Flow<Fact?> =
        MutableStateFlow(factsFlow.value.firstOrNull { it.id == factId })
    override suspend fun getFact(factId: String): Fact? =
        factsFlow.value.firstOrNull { it.id == factId }
    override suspend fun saveFact(fact: Fact) {
        factsFlow.value = factsFlow.value.filter { it.id != fact.id } + fact
    }
    override suspend fun saveFacts(facts: List<Fact>) {
        val ids = facts.map { it.id }.toSet()
        factsFlow.value = factsFlow.value.filter { it.id !in ids } + facts
    }
    override suspend fun setActive(factId: String, active: Boolean) {
        factsFlow.value = factsFlow.value.map { if (it.id == factId) it.copy(active = active) else it }
    }
    override suspend fun setFavorite(factId: String, favorite: Boolean) {
        factsFlow.value = factsFlow.value.map { if (it.id == factId) it.copy(favorite = favorite) else it }
    }
    override suspend fun deleteFact(factId: String): AppResult<Unit> {
        factsFlow.value = factsFlow.value.filter { it.id != factId }
        return AppResult.Success(Unit)
    }
    override fun observeTags(): Flow<List<FactTag>> = MutableStateFlow(emptyList())
    override fun observeSmartViewCounts(): Flow<SmartViewCounts> = MutableStateFlow(SmartViewCounts())
    override fun observeTotalCount(): Flow<Int> = MutableStateFlow(factsFlow.value.size)
}

private class FakeDocumentRepository : DocumentRepository {
    val docsFlow = MutableStateFlow<List<Document>>(emptyList())
    override fun observeDocuments(): Flow<List<Document>> = docsFlow
    override fun observeDocument(documentId: String): Flow<Document?> =
        MutableStateFlow(docsFlow.value.firstOrNull { it.id == documentId })
    override suspend fun getDocument(documentId: String): Document? =
        docsFlow.value.firstOrNull { it.id == documentId }
    override suspend fun findByHash(fileHash: String): Document? = null
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = MutableStateFlow(null)
    override suspend fun getSettings(documentId: String): DocumentSettings? = null
    override suspend fun saveDocument(document: Document) {
        docsFlow.value = docsFlow.value + document
    }
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {}
    override suspend fun saveSettings(settings: DocumentSettings) {}
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {}
    override suspend fun updateExtractionStatus(documentId: String, status: ExtractionStatus, errorMessage: String?) {}
    override suspend fun deleteDocument(documentId: String): AppResult<Unit> {
        docsFlow.value = docsFlow.value.filter { it.id != documentId }
        return AppResult.Success(Unit)
    }
    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = MutableStateFlow(emptyMap())
    override suspend fun getLocalFilePath(documentId: String): String? = null
    override fun observeAllSettings(): Flow<List<DocumentSettings>> = MutableStateFlow(emptyList())
    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) {}
    override fun observeChunks(documentId: String): Flow<List<com.factswallpaper.core.model.DocumentChunk>> = MutableStateFlow(emptyList())
    override suspend fun getChunks(documentId: String, startPage: Int, endPage: Int): List<com.factswallpaper.core.model.DocumentChunk> = emptyList()
    override suspend fun upsertChunk(chunk: com.factswallpaper.core.model.DocumentChunk) {}
    override suspend fun clearChunks(documentId: String) {}
    override suspend fun markOcrPageStarted(documentId: String, page: Int) {}
    override suspend fun commitOcrPage(documentId: String, page: Int, text: String, confidence: Float?) {}
    override suspend fun updateCompiledText(documentId: String, compiledTextPath: String, status: ExtractionStatus) {}
}

private class FakeDocumentTextRepository : DocumentTextRepository {
    override suspend fun getPages(documentId: String, startPage: Int, endPage: Int): List<PageText> = emptyList()
    override suspend fun getPageCount(documentId: String): Int = 0
}

private class FakeStructuredAiService : StructuredAiService {
    override suspend fun generateOutline(
        documentId: String?,
        pages: List<PageText>,
        pdfBytes: ByteArray?,
        pdfFileName: String?,
        pdfPageCount: Int?,
        manualProfileId: String?,
        progress: ((completed: Int, total: Int) -> Unit)?,
    ): OutlineGenerationResult = throw UnsupportedOperationException()

    override suspend fun generateFacts(
        documentId: String?,
        lessonTitle: String,
        pages: List<PageText>,
        documentPageCount: Int,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String?,
        existingFactTexts: List<String>,
        extraInstruction: String?,
        progress: ((completed: Int, total: Int) -> Unit)?,
    ): FactsGenerationResult = throw UnsupportedOperationException()

    override suspend fun estimateOutline(
        pages: List<PageText>,
        pdfPageCount: Int?,
        manualProfileId: String?,
    ): AiCostEstimate = throw UnsupportedOperationException()

    override suspend fun estimateFacts(
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String?,
    ): AiCostEstimate = throw UnsupportedOperationException()

    override suspend fun verifyFormula(
        source: String,
        manualProfileId: String?,
    ): ValidationResult<VerificationEnvelope> = throw UnsupportedOperationException()

    override suspend fun generateDiagram(
        description: String,
        kindHint: String?,
        manualProfileId: String?,
    ): DiagramGenerationResult = throw UnsupportedOperationException()

    override suspend fun generateTemplate(
        description: String,
        manualProfileId: String?,
    ): TemplateGenerationResult = throw UnsupportedOperationException()

    override suspend fun suggestCollections(
        facts: List<com.factswallpaper.core.ai.FactSummaryInput>,
        manualProfileId: String?,
    ): List<com.factswallpaper.core.ai.SuggestedCollectionItem> = emptyList()

    override suspend fun regenerateFact(
        originalText: String,
        pageContent: String?,
        lessonTitle: String?,
        extraInstruction: String?,
        manualProfileId: String?,
    ): FactDraft = FactDraft(text = "Regenerated fact text")
}

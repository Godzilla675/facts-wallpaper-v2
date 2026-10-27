package com.factswallpaper.feature.outline

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
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentInputMode
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.model.ThemeMode
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
class OutlineViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeDocRepo = FakeDocumentRepository()
    private val fakeDocTextRepo = FakeDocumentTextRepository()
    private val fakeLessonRepo = FakeLessonRepository()
    private val fakeFactRepo = FakeFactRepository()
    private val fakeAiService = FakeStructuredAiService()
    private val fakeNetworkMonitor = FakeNetworkMonitor()
    private val fakeSettingsRepo = FakeSettingsRepository()
    private lateinit var workManager: WorkManager
    private lateinit var viewModel: OutlineViewModel

    private val testDoc = Document(
        id = "doc-1",
        title = "Physics Textbook",
        sourceUri = "content://docs/physics.pdf",
        localFilePath = "/data/docs/physics.pdf",
        fileHash = "hash123",
        fileSizeBytes = 1024L,
        pageCount = 100,
        pagesWithText = 100,
        processingRoute = PdfProcessingRoute.EXTRACT_TEXT,
        extractionStatus = ExtractionStatus.OUTLINE_READY,
        createdAtEpochMs = 1000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        fakeDocRepo.docsFlow.value = listOf(testDoc)
        fakeDocRepo.settingsFlow.value = DocumentSettings(documentId = "doc-1", pageRangeEnd = 100)

        val handle = SavedStateHandle(mapOf("documentId" to "doc-1"))
        viewModel = OutlineViewModel(
            savedStateHandle = handle,
            documentRepository = fakeDocRepo,
            documentTextRepository = fakeDocTextRepo,
            lessonRepository = fakeLessonRepo,
            factRepository = fakeFactRepo,
            structuredAiService = fakeAiService,
            networkMonitor = fakeNetworkMonitor,
            settingsRepository = fakeSettingsRepo,
            workManager = workManager,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads document and lessons`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val lesson = Lesson(
            id = "lesson-1",
            documentId = "doc-1",
            title = "Mechanics",
            summary = "Newton's laws of motion",
            startPage = 1,
            endPage = 20,
            sortOrder = 1,
            selected = true,
        )
        fakeLessonRepo.lessonsFlow.value = listOf(lesson)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state).isInstanceOf(OutlineUiState.Content::class.java)
        val content = state as OutlineUiState.Content
        assertThat(content.document.title).isEqualTo("Physics Textbook")
        assertThat(content.lessons).containsExactly(lesson)
    }

    @Test
    fun `setLessonSelected updates lesson in repository`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val lesson = Lesson(
            id = "lesson-1",
            documentId = "doc-1",
            title = "Mechanics",
            summary = "Newton's laws of motion",
            startPage = 1,
            endPage = 20,
            sortOrder = 1,
            selected = true,
        )
        fakeLessonRepo.lessonsFlow.value = listOf(lesson)
        advanceUntilIdle()

        viewModel.setLessonSelected("lesson-1", false)
        advanceUntilIdle()

        assertThat(fakeLessonRepo.lessonsFlow.value.first().selected).isFalse()
    }

    @Test
    fun `deleteLesson removes lesson from repository`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val lesson = Lesson(
            id = "lesson-1",
            documentId = "doc-1",
            title = "Mechanics",
            summary = "Newton's laws of motion",
            startPage = 1,
            endPage = 20,
            sortOrder = 1,
            selected = true,
        )
        fakeLessonRepo.lessonsFlow.value = listOf(lesson)
        advanceUntilIdle()

        viewModel.deleteLesson("lesson-1")
        advanceUntilIdle()

        assertThat(fakeLessonRepo.lessonsFlow.value).isEmpty()
    }

    @Test
    fun `setDensity updates document settings`() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.setDensity(FactDensity.DEEP)
        advanceUntilIdle()

        assertThat(fakeDocRepo.settingsFlow.value?.factDensity).isEqualTo(FactDensity.DEEP)
    }
}

private class FakeNetworkMonitor : NetworkMonitor {
    val onlineFlow = MutableStateFlow(true)
    override fun isOnline(): Boolean = onlineFlow.value
    override fun observeOnline(): Flow<Boolean> = onlineFlow
}

private class FakeLessonRepository : LessonRepository {
    val lessonsFlow = MutableStateFlow<List<Lesson>>(emptyList())

    override fun observeLessons(documentId: String): Flow<List<Lesson>> = lessonsFlow
    override fun observeLesson(lessonId: String): Flow<Lesson?> =
        MutableStateFlow(lessonsFlow.value.firstOrNull { it.id == lessonId })
    override suspend fun getLessons(documentId: String): List<Lesson> = lessonsFlow.value
    override suspend fun replaceLessons(documentId: String, lessons: List<Lesson>) {
        lessonsFlow.value = lessons
    }
    override suspend fun upsert(lesson: Lesson) {
        lessonsFlow.value = lessonsFlow.value.filter { it.id != lesson.id } + lesson
    }
    override suspend fun delete(lessonId: String) {
        lessonsFlow.value = lessonsFlow.value.filter { it.id != lessonId }
    }
    override suspend fun setSelected(lessonId: String, selected: Boolean) {
        lessonsFlow.value = lessonsFlow.value.map { if (it.id == lessonId) it.copy(selected = selected) else it }
    }
    override suspend fun setAllSelected(documentId: String, selected: Boolean) {
        lessonsFlow.value = lessonsFlow.value.map { it.copy(selected = selected) }
    }
}

private class FakeFactRepository : FactRepository {
    val factsFlow = MutableStateFlow<List<Fact>>(emptyList())
    override fun observeFacts(filter: FactFilter): Flow<List<Fact>> = factsFlow
    override fun observeFact(factId: String): Flow<Fact?> = MutableStateFlow(null)
    override suspend fun getFact(factId: String): Fact? = null
    override suspend fun saveFact(fact: Fact) {}
    override suspend fun saveFacts(facts: List<Fact>) {}
    override suspend fun setActive(factId: String, active: Boolean) {}
    override suspend fun setFavorite(factId: String, favorite: Boolean) {}
    override suspend fun deleteFact(factId: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeTags(): Flow<List<FactTag>> = MutableStateFlow(emptyList())
    override fun observeSmartViewCounts(): Flow<SmartViewCounts> = MutableStateFlow(SmartViewCounts())
    override fun observeTotalCount(): Flow<Int> = MutableStateFlow(0)
}

private class FakeDocumentRepository : DocumentRepository {
    val docsFlow = MutableStateFlow<List<Document>>(emptyList())
    val settingsFlow = MutableStateFlow<DocumentSettings?>(null)

    override fun observeDocuments(): Flow<List<Document>> = docsFlow
    override fun observeDocument(documentId: String): Flow<Document?> =
        MutableStateFlow(docsFlow.value.firstOrNull { it.id == documentId })
    override suspend fun getDocument(documentId: String): Document? =
        docsFlow.value.firstOrNull { it.id == documentId }
    override suspend fun findByHash(fileHash: String): Document? = null
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = settingsFlow
    override suspend fun getSettings(documentId: String): DocumentSettings? = settingsFlow.value
    override suspend fun saveDocument(document: Document) {
        docsFlow.value = docsFlow.value + document
    }
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {
        docsFlow.value = docsFlow.value + document
        settingsFlow.value = settings
    }
    override suspend fun saveSettings(settings: DocumentSettings) {
        settingsFlow.value = settings
    }
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {}
    override suspend fun updateExtractionStatus(documentId: String, status: ExtractionStatus, errorMessage: String?) {}
    override suspend fun updatePipelineProgress(documentId: String, progress: Float) {}
    override suspend fun updatePipelineStatus(
        documentId: String,
        status: ExtractionStatus,
        progress: Float,
        errorMessage: String?,
    ) {}
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

private class FakeSettingsRepository : SettingsRepository {
    val preferencesFlow = MutableStateFlow(AppPreferences())
    override fun observePreferences(): Flow<AppPreferences> = preferencesFlow
    override suspend fun setOnboardingCompleted(completed: Boolean) {}
    override suspend fun setAppThemeMode(mode: ThemeMode) {}
    override suspend fun setWallpaperThemeMode(mode: ThemeMode) {}
    override suspend fun setRoutingStrategy(strategy: RoutingStrategy) {}
    override suspend fun setTaskProfileOverrides(overrides: Map<AiTask, String>) {}
    override suspend fun setTaskProfileOverride(task: AiTask, profileId: String?) {}
    override suspend fun setCustomTaskPromptSuffixes(suffixes: Map<AiTask, String>) {}
    override suspend fun setCustomTaskPromptSuffix(task: AiTask, instruction: String?) {}
    override suspend fun setDefaultOcrPageLimit(limit: Int) {}
    override suspend fun setAllowAdvancedOcrEngines(allowed: Boolean) {}
    override suspend fun setPreRenderQueueSize(size: Int) {}
    override suspend fun setEncryptedDatabaseEnabled(enabled: Boolean) {}
    override suspend fun setEnableAiDiagnosticsLog(enabled: Boolean) {}
    override suspend fun setEnableDoubleTapWallpaperAdvance(enabled: Boolean) {}
    override suspend fun setAiCostGuardThresholdUsd(thresholdUsd: Double) {}
    override suspend fun setDefaultAutoProcessMode(mode: AutoProcessMode) {}
    override suspend fun setDefaultDocumentInputMode(mode: DocumentInputMode) {}
    override suspend fun setMaxImagesPerRequest(maxImages: Int) {}
    override suspend fun setRotationSource(source: RotationSource) {}
    override suspend fun setQuietHoursEnabled(enabled: Boolean) {}
    override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {}
    override suspend fun setRotationPaused(paused: Boolean) {}
    override suspend fun setBatterySaverSlowdownEnabled(enabled: Boolean) {}
    override suspend fun setDailyFactNotificationEnabled(enabled: Boolean) {}
    override suspend fun setDailyFactNotificationTime(timeMinutes: Int) {}
    override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
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
    ): AiCostEstimate = AiCostEstimate(
        profile = com.factswallpaper.core.model.ProviderProfile(
            id = "test-profile",
            name = "Test",
            providerType = com.factswallpaper.core.model.ProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-4o",
            maxContextTokens = 128000,
        ),
        estimatedInputTokens = 100,
        estimatedOutputTokens = 100,
        estimatedCostUsd = 0.001,
    )

    override suspend fun estimateFacts(
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String?,
    ): AiCostEstimate = AiCostEstimate(
        profile = com.factswallpaper.core.model.ProviderProfile(
            id = "test-profile",
            name = "Test",
            providerType = com.factswallpaper.core.model.ProviderType.OPENAI,
            baseUrl = "https://api.openai.com/v1",
            modelName = "gpt-4o",
            maxContextTokens = 128000,
        ),
        estimatedInputTokens = 200,
        estimatedOutputTokens = 200,
        estimatedCostUsd = 0.002,
    )

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

package com.factswallpaper.core.pdf

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentChunk
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.model.PdfProcessingRoute
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentProcessingSchedulerTest {

    private lateinit var context: Context
    private val fakeDocRepo = FakeSchedulerDocumentRepository()
    private val fakeLessonRepo = FakeSchedulerLessonRepository()
    private val testDispatchers = FactsDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )
    private lateinit var scheduler: DocumentProcessingScheduler

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        scheduler = DocumentProcessingScheduler(
            context = context,
            documentRepository = fakeDocRepo,
            lessonRepository = fakeLessonRepo,
            dispatchers = testDispatchers,
        )
    }

    @Test
    fun `onExtractionCompleted with ASK_ME sets status to ANALYZED without enqueueing outline`() = runTest {
        val documentId = "doc-1"
        fakeDocRepo.settings = DocumentSettings(
            documentId = documentId,
            pageRangeEnd = 10,
            autoProcessMode = AutoProcessMode.ASK_ME,
        )

        scheduler.onExtractionCompleted(documentId)

        assertThat(fakeDocRepo.lastUpdatedStatus).isEqualTo(ExtractionStatus.ANALYZED)
        assertThat(fakeDocRepo.lastUpdatedProgress).isEqualTo(1.0f)
    }

    @Test
    fun `onExtractionCompleted with OUTLINE_ONLY updates status to OUTLINE_IN_PROGRESS`() = runTest {
        val documentId = "doc-2"
        fakeDocRepo.settings = DocumentSettings(
            documentId = documentId,
            pageRangeEnd = 10,
            autoProcessMode = AutoProcessMode.OUTLINE_ONLY,
        )

        scheduler.onExtractionCompleted(documentId)

        assertThat(fakeDocRepo.lastUpdatedStatus).isEqualTo(ExtractionStatus.OUTLINE_IN_PROGRESS)
        assertThat(fakeDocRepo.lastUpdatedProgress).isEqualTo(0.4f)
    }

    @Test
    fun `onOutlineCompleted with OUTLINE_ONLY updates status to OUTLINE_READY without chaining facts`() = runTest {
        val documentId = "doc-3"
        fakeDocRepo.settings = DocumentSettings(
            documentId = documentId,
            pageRangeEnd = 10,
            autoProcessMode = AutoProcessMode.OUTLINE_ONLY,
        )

        scheduler.onOutlineCompleted(documentId)

        assertThat(fakeDocRepo.lastUpdatedStatus).isEqualTo(ExtractionStatus.OUTLINE_READY)
        assertThat(fakeDocRepo.lastUpdatedProgress).isEqualTo(0.6f)
    }

    @Test
    fun `onOutlineCompleted with OUTLINE_AND_GENERATE_ALL enqueues facts work for selected lessons`() = runTest {
        val documentId = "doc-4"
        fakeDocRepo.settings = DocumentSettings(
            documentId = documentId,
            pageRangeEnd = 10,
            autoProcessMode = AutoProcessMode.OUTLINE_AND_GENERATE_ALL,
        )
        fakeLessonRepo.lessons = listOf(
            Lesson(
                id = "lesson-1",
                documentId = documentId,
                title = "Chapter 1",
                summary = "Summary",
                startPage = 1,
                endPage = 10,
                selected = true,
                sortOrder = 0,
                factCount = 0,
            ),
        )

        scheduler.onOutlineCompleted(documentId)

        assertThat(fakeDocRepo.lastUpdatedStatus).isEqualTo(ExtractionStatus.FACTS_IN_PROGRESS)
        assertThat(fakeDocRepo.lastUpdatedProgress).isEqualTo(0.65f)
    }

    @Test
    fun `onFactsCompleted updates status to COMPLETED and 100 percent progress`() = runTest {
        val documentId = "doc-5"

        scheduler.onFactsCompleted(documentId, 5)

        assertThat(fakeDocRepo.lastUpdatedStatus).isEqualTo(ExtractionStatus.COMPLETED)
        assertThat(fakeDocRepo.lastUpdatedProgress).isEqualTo(1.0f)
    }
}

private class FakeSchedulerDocumentRepository : DocumentRepository {
    var settings: DocumentSettings? = null
    var lastUpdatedStatus: ExtractionStatus? = null
    var lastUpdatedProgress: Float? = null

    override fun observeDocuments(): Flow<List<Document>> = MutableStateFlow(emptyList())
    override fun observeDocument(documentId: String): Flow<Document?> = MutableStateFlow(null)
    override suspend fun getDocument(documentId: String): Document? = null
    override suspend fun findByHash(fileHash: String): Document? = null
    override suspend fun getLocalFilePath(documentId: String): String? = null
    override fun observeSettings(documentId: String): Flow<DocumentSettings?> = MutableStateFlow(settings)
    override suspend fun getSettings(documentId: String): DocumentSettings? = settings
    override fun observeAllSettings(): Flow<List<DocumentSettings>> = MutableStateFlow(emptyList())
    override suspend fun saveDocument(document: Document) {}
    override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) {}
    override suspend fun replaceDocumentWithSettings(document: Document, settings: DocumentSettings) {}
    override suspend fun saveSettings(settings: DocumentSettings) {}
    override suspend fun setIncludedInRotation(documentId: String, included: Boolean) {}
    override suspend fun updateExtractionStatus(documentId: String, status: ExtractionStatus, errorMessage: String?) {
        lastUpdatedStatus = status
    }
    override suspend fun updatePipelineProgress(documentId: String, progress: Float) {
        lastUpdatedProgress = progress
    }
    override suspend fun updatePipelineStatus(
        documentId: String,
        status: ExtractionStatus,
        progress: Float,
        errorMessage: String?,
    ) {
        lastUpdatedStatus = status
        lastUpdatedProgress = progress
    }
    override suspend fun deleteDocument(documentId: String): AppResult<Unit> = AppResult.Success(Unit)
    override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = MutableStateFlow(emptyMap())
    override fun observeChunks(documentId: String): Flow<List<DocumentChunk>> = MutableStateFlow(emptyList())
    override suspend fun getChunks(documentId: String, startPage: Int, endPage: Int): List<DocumentChunk> = emptyList()
    override suspend fun upsertChunk(chunk: DocumentChunk) {}
    override suspend fun clearChunks(documentId: String) {}
    override suspend fun markOcrPageStarted(documentId: String, page: Int) {}
    override suspend fun commitOcrPage(documentId: String, page: Int, text: String, confidence: Float?) {}
    override suspend fun updateCompiledText(documentId: String, compiledTextPath: String, status: ExtractionStatus) {}
}

private class FakeSchedulerLessonRepository : LessonRepository {
    var lessons: List<Lesson> = emptyList()

    override fun observeLessons(documentId: String): Flow<List<Lesson>> = MutableStateFlow(lessons)
    override fun observeLesson(lessonId: String): Flow<Lesson?> = MutableStateFlow(lessons.firstOrNull { it.id == lessonId })
    override suspend fun getLessons(documentId: String): List<Lesson> = lessons
    override suspend fun replaceLessons(documentId: String, lessons: List<Lesson>) {
        this.lessons = lessons
    }
    override suspend fun upsert(lesson: Lesson) {}
    override suspend fun delete(lessonId: String) {}
    override suspend fun setSelected(lessonId: String, selected: Boolean) {}
    override suspend fun setAllSelected(documentId: String, selected: Boolean) {}
}

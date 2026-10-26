package com.factswallpaper.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.CollectionFactCrossRef
import com.factswallpaper.core.database.entity.DocumentChunkEntity
import com.factswallpaper.core.database.entity.DocumentSettingsEntity
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.LessonEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.factswallpaper.core.database.entity.FolderFactCrossRef
import com.factswallpaper.core.database.entity.WallpaperStateEntity
import com.factswallpaper.core.database.entity.AiRequestLogEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FactsDatabaseTest {

    private lateinit var database: FactsDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun document(id: String, createdAt: Long = 1_000L): PdfDocumentEntity =
        PdfDocumentEntity(
            id = id,
            title = "Book $id",
            sourceUri = "content://book/$id",
            localFilePath = "/data/books/$id.pdf",
            fileHash = "hash-$id",
            fileSizeBytes = 1_024L,
            pageCount = 10,
            pagesWithText = 9,
            processingRoute = "DIRECT_PDF",
            ocrEngine = null,
            extractionStatus = "ANALYZED",
            createdAtEpochMs = createdAt,
        )

    private fun lesson(
        id: String,
        documentId: String,
        sortOrder: Int = 0,
    ): LessonEntity = LessonEntity(
        id = id,
        documentId = documentId,
        title = "Lesson $id",
        summary = "Summary",
        startPage = 1,
        endPage = 5,
        sortOrder = sortOrder,
    )

    private fun fact(
        id: String,
        documentId: String,
        lessonId: String? = null,
        createdAt: Long = 1_000L,
        active: Boolean = true,
        favorite: Boolean = false,
        tagsCsv: String = "",
    ): FactItemEntity = FactItemEntity(
        id = id,
        documentId = documentId,
        lessonId = lessonId,
        text = "Fact $id",
        sourceBookTitle = "Book",
        sourceLessonTitle = null,
        sourcePage = 1,
        tagsCsv = tagsCsv,
        active = active,
        favorite = favorite,
        createdAtEpochMs = createdAt,
    )

    @Test
    fun `insert document with settings round trips settings and hash lookup`() = runBlocking<Unit> {
        val dao = database.pdfDocumentDao()
        val doc = document("doc-1")
        val settings = DocumentSettingsEntity(
            documentId = doc.id,
            pageRangeEnd = 10,
            factDensity = "DEEP",
            includeDiagrams = false,
        )

        dao.insertDocumentWithSettings(doc, settings)

        assertThat(dao.getById(doc.id)).isEqualTo(doc)
        assertThat(dao.getSettings(doc.id)).isEqualTo(settings)
        assertThat(dao.findByHash("hash-doc-1")).isEqualTo(doc)
        assertThat(dao.observeAll().first()).containsExactly(doc)
    }

    @Test
    fun `ocr page checkpoint writes chunk and resume marker atomically`() = runBlocking<Unit> {
        val dao = database.pdfDocumentDao()
        dao.upsert(document("doc-1"))

        dao.commitOcrPage(
            documentId = "doc-1",
            pageNumber = 3,
            text = "Extracted page three",
            confidence = 0.93f,
            nowEpochMs = 2_000L,
        )

        val chunks = dao.getChunks("doc-1")
        assertThat(chunks).hasSize(1)
        assertThat(chunks.single().pageNumber).isEqualTo(3)
        assertThat(chunks.single().extractedText).isEqualTo("Extracted page three")
        assertThat(chunks.single().isFromOcr).isTrue()
        assertThat(dao.countCompletedChunks("doc-1")).isEqualTo(1)
        assertThat(dao.getById("doc-1")?.ocrLastCompletedPage).isEqualTo(3)
        assertThat(dao.getById("doc-1")?.updatedAtEpochMs).isEqualTo(2_000L)
    }

    @Test
    fun `upserting a fact replaces its normalized tags`() = runBlocking<Unit> {
        val documentDao = database.pdfDocumentDao()
        val factDao = database.factDao()
        documentDao.upsert(document("doc-1"))

        factDao.upsertFactWithTags(
            fact("fact-1", "doc-1"),
            tags = listOf("Physics", " mechanics ", "PHYSICS", ""),
        )
        assertThat(factDao.observeAllDistinctTags().first())
            .containsExactly("mechanics", "physics")
            .inOrder()

        factDao.upsertFactWithTags(fact("fact-1", "doc-1"), tags = listOf("biology"))
        assertThat(factDao.observeAllDistinctTags().first()).containsExactly("biology")
        assertThat(factDao.observeTagCounts().first().single().count).isEqualTo(1)
    }

    @Test
    fun `deleting a fact cascades its tags`() = runBlocking<Unit> {
        database.pdfDocumentDao().upsert(document("doc-1"))
        val factDao = database.factDao()
        factDao.upsertFactWithTags(fact("fact-1", "doc-1"), listOf("physics"))

        factDao.deleteById("fact-1")

        assertThat(factDao.observeAllDistinctTags().first()).isEmpty()
        assertThat(factDao.getById("fact-1")).isNull()
    }

    @Test
    fun `deleting a document cascades settings lessons facts and tags`() = runBlocking<Unit> {
        val documentDao = database.pdfDocumentDao()
        documentDao.insertDocumentWithSettings(
            document("doc-1"),
            DocumentSettingsEntity(documentId = "doc-1", pageRangeEnd = 10),
        )
        database.lessonDao().upsert(lesson("lesson-1", "doc-1"))
        database.factDao().upsertFactWithTags(fact("fact-1", "doc-1", "lesson-1"), listOf("physics"))
        documentDao.upsertChunks(
            listOf(
                DocumentChunkEntity(
                    documentId = "doc-1",
                    pageNumber = 1,
                    extractedText = "Page one",
                    isFromOcr = false,
                )
            )
        )

        documentDao.deleteById("doc-1")

        assertThat(documentDao.getById("doc-1")).isNull()
        assertThat(documentDao.getSettings("doc-1")).isNull()
        assertThat(documentDao.getChunks("doc-1")).isEmpty()
        assertThat(database.lessonDao().getByDocument("doc-1")).isEmpty()
        assertThat(database.factDao().getById("fact-1")).isNull()
        assertThat(database.factDao().observeAllDistinctTags().first()).isEmpty()
    }

    @Test
    fun `deleting a lesson keeps its facts and clears the lesson link`() = runBlocking<Unit> {
        database.pdfDocumentDao().upsert(document("doc-1"))
        database.lessonDao().upsert(lesson("lesson-1", "doc-1"))
        database.factDao().upsert(fact("fact-1", "doc-1", "lesson-1"))

        database.lessonDao().deleteById("lesson-1")

        val remaining = database.factDao().getById("fact-1")
        assertThat(remaining).isNotNull()
        assertThat(remaining?.lessonId).isNull()
    }

    @Test
    fun `wallpaper singleton state upserts and updates its current fact`() = runBlocking<Unit> {
        val dao = database.wallpaperStateDao()
        assertThat(dao.get()).isNull()

        dao.upsert(WallpaperStateEntity(currentFactId = null))
        dao.setCurrentFact("fact-7", index = 7, nowEpochMs = 9_000L)

        val state = dao.get()
        assertThat(state?.currentFactId).isEqualTo("fact-7")
        assertThat(state?.currentIndex).isEqualTo(7)
        assertThat(state?.lastRotatedAtEpochMs).isEqualTo(9_000L)
        assertThat(state?.activeTemplateId).isEqualTo("builtin_minimal_text")
    }

    @Test
    fun `diagnostics log insertion prunes to the newest fifty entries`() = runBlocking<Unit> {
        val dao = database.diagnosticsAndUsageDao()
        repeat(55) { index ->
            dao.insertLogAndPrune(log("log-$index", timestamp = index.toLong()))
        }

        val logs = dao.observeRecentLogs(limit = 100).first()
        assertThat(logs).hasSize(50)
        assertThat(logs.first().timestampEpochMs).isEqualTo(54L)
        assertThat(logs.last().timestampEpochMs).isEqualTo(5L)

        dao.clearLogs()
        assertThat(dao.observeRecentLogs(limit = 100).first()).isEmpty()
    }

    @Test
    fun `collection and folder rotation queries only include accepted enabled sources`() = runBlocking<Unit> {
        val dao = database.collectionDao()
        dao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        dao.upsertCollection(
            CollectionEntity(
                id = "col-2",
                name = "Suggested",
                accepted = false,
                createdAtEpochMs = 2L,
            )
        )
        dao.upsertFolder(UserFolderEntity(id = "folder-1", name = "Faves", createdAtEpochMs = 1L))
        dao.upsertFolder(
            UserFolderEntity(
                id = "folder-2",
                name = "Off",
                includeInRotation = false,
                createdAtEpochMs = 2L,
            )
        )

        assertThat(dao.getCollection("col-1")).isNotNull()
        assertThat(dao.getFolder("folder-1")).isNotNull()

        assertThat(dao.observeFactIdsForIncludedCollections().first()).isEmpty()
        assertThat(dao.observeFactIdsForIncludedFolders().first()).isEmpty()

        dao.setCollectionAccepted("col-2", accepted = true)
        assertThat(dao.getCollection("col-2")?.accepted).isTrue()
        dao.setCollectionInRotation("col-1", included = false)
        assertThat(dao.getCollection("col-1")?.includeInRotation).isFalse()
        dao.setFolderInRotation("folder-1", included = false)
        assertThat(dao.getFolder("folder-1")?.includeInRotation).isFalse()
    }

    @Test
    fun `collection and folder membership cascades when the fact or parent is deleted`() = runBlocking<Unit> {
        database.pdfDocumentDao().upsert(document("doc-1"))
        database.factDao().upsert(fact("fact-1", "doc-1"))
        val dao = database.collectionDao()
        dao.upsertCollection(CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L))
        dao.upsertFolder(UserFolderEntity(id = "folder-1", name = "Faves", createdAtEpochMs = 1L))
        dao.addFactsToCollection(listOf(CollectionFactCrossRef("col-1", "fact-1")))
        dao.addFactsToFolder(listOf(FolderFactCrossRef("folder-1", "fact-1")))
        assertThat(dao.observeFactIdsForCollection("col-1").first()).containsExactly("fact-1")
        assertThat(dao.observeFactIdsForFolder("folder-1").first()).containsExactly("fact-1")
        assertThat(dao.observeCollectionFactCounts().first().single().count).isEqualTo(1)

        database.factDao().deleteById("fact-1")
        assertThat(dao.observeFactIdsForCollection("col-1").first()).isEmpty()
        assertThat(dao.observeFactIdsForFolder("folder-1").first()).isEmpty()

        dao.upsertCollection(CollectionEntity(id = "col-2", name = "Other", createdAtEpochMs = 2L))
        database.pdfDocumentDao().upsert(document("doc-2"))
        database.factDao().upsert(fact("fact-2", "doc-2"))
        dao.addFactsToCollection(listOf(CollectionFactCrossRef("col-2", "fact-2")))
        dao.deleteCollection("col-2")
        assertThat(dao.getCollection("col-2")).isNull()
        assertThat(dao.observeFactIdsForCollection("col-2").first()).isEmpty()
    }

    @Test
    fun `active facts are ordered by document then lesson then creation time`() = runBlocking<Unit> {
        val documentDao = database.pdfDocumentDao()
        documentDao.upsert(document("doc-new", createdAt = 2_000L))
        documentDao.upsert(document("doc-old", createdAt = 1_000L))
        database.lessonDao().upsert(lesson("lesson-late", "doc-old", sortOrder = 2))
        database.lessonDao().upsert(lesson("lesson-early", "doc-old", sortOrder = 1))
        val factDao = database.factDao()
        factDao.upsert(fact("new-doc-fact", "doc-new", createdAt = 1L))
        factDao.upsert(fact("late-lesson", "doc-old", "lesson-late", createdAt = 1L))
        factDao.upsert(fact("early-lesson", "doc-old", "lesson-early", createdAt = 9L))
        factDao.upsert(fact("no-lesson", "doc-old", null, createdAt = 1L))
        factDao.upsert(fact("inactive", "doc-old", null, createdAt = 0L, active = false))

        val ordered = factDao.observeAllActiveOrdered().first().map { it.id }

        assertThat(ordered).containsExactly(
            "early-lesson",
            "late-lesson",
            "no-lesson",
            "new-doc-fact",
        ).inOrder()
    }

    private fun log(id: String, timestamp: Long): AiRequestLogEntity = AiRequestLogEntity(
        id = id,
        taskType = "FACTS",
        providerProfileId = "profile-1",
        modelName = "model",
        httpStatusCode = 200,
        durationMs = 10L,
        inputTokens = 10,
        outputTokens = 20,
        success = true,
        redactedRequestSummary = "request",
        redactedResponseSnippet = "response",
        timestampEpochMs = timestamp,
    )
}

package com.factswallpaper.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.CollectionFactCrossRef
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.FolderFactCrossRef
import com.factswallpaper.core.database.entity.LessonEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
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
class RoomRotationPoolRepositoryTest {

    private lateinit var database: FactsDatabase
    private lateinit var wallpaperRepository: WallpaperRepository
    private lateinit var repository: RoomRotationPoolRepository

    @Before
    fun setUp() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dispatchers = FactsDispatchers(
            io = Dispatchers.Unconfined,
            default = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
        )
        wallpaperRepository = RoomWallpaperRepository(
            wallpaperStateDao = database.wallpaperStateDao(),
            dispatchers = dispatchers,
        )
        repository = RoomRotationPoolRepository(
            factDao = database.factDao(),
            documentDao = database.pdfDocumentDao(),
            collectionDao = database.collectionDao(),
            wallpaperRepository = wallpaperRepository,
        )
        wallpaperRepository.ensureInitialized()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun document(id: String, createdAt: Long, included: Boolean = true) =
        PdfDocumentEntity(
            id = id,
            title = "Book $id",
            sourceUri = "content://$id",
            localFilePath = "/$id.pdf",
            fileHash = "hash-$id",
            fileSizeBytes = 100L,
            pageCount = 10,
            pagesWithText = 8,
            processingRoute = "DIRECT_PDF",
            ocrEngine = null,
            extractionStatus = "FACTS_READY",
            includedInRotation = included,
            createdAtEpochMs = createdAt,
        )

    private fun fact(
        id: String,
        documentId: String,
        lessonId: String? = null,
        createdAt: Long,
        active: Boolean = true,
        favorite: Boolean = false,
    ) = FactItemEntity(
        id = id,
        documentId = documentId,
        lessonId = lessonId,
        text = "Fact $id",
        sourceBookTitle = "Book $documentId",
        sourceLessonTitle = null,
        sourcePage = 1,
        active = active,
        favorite = favorite,
        createdAtEpochMs = createdAt,
    )

    private suspend fun seed() {
        val documentDao = database.pdfDocumentDao()
        documentDao.upsert(document("doc-1", createdAt = 1_000L, included = true))
        documentDao.upsert(document("doc-2", createdAt = 2_000L, included = false))
        database.lessonDao().upsert(
            LessonEntity(
                id = "lesson-1",
                documentId = "doc-1",
                title = "Lesson",
                summary = "",
                startPage = 1,
                endPage = 5,
                sortOrder = 0,
            )
        )
        val factDao = database.factDao()
        factDao.upsert(fact("f1", "doc-1", "lesson-1", createdAt = 1_000L))
        factDao.upsert(fact("f2", "doc-1", createdAt = 2_000L, active = false))
        factDao.upsert(fact("f3", "doc-2", createdAt = 3_000L))
        factDao.upsert(fact("f4", "doc-2", createdAt = 4_000L))
    }

    private suspend fun poolIds(): List<String> = repository.observePool().first().map { it.id }

    @Test
    fun `default pool contains active facts from rotation-enabled documents only`() = runBlocking<Unit> {
        seed()

        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `favourites-only toggle narrows the pool and can be disabled again`() = runBlocking<Unit> {
        seed()
        wallpaperRepository.updateState { it.copy(onlyFavoritesInPool = true) }
        assertThat(poolIds()).isEmpty()

        database.factDao().upsert(fact("f1", "doc-1", "lesson-1", createdAt = 1_000L, favorite = true))
        assertThat(poolIds()).containsExactly("f1")

        wallpaperRepository.updateState { it.copy(onlyFavoritesInPool = false) }
        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `explicit collection sources bypass document rotation flags but not active state`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToCollection(
            listOf(
                CollectionFactCrossRef("col-1", "f1"),
                CollectionFactCrossRef("col-1", "f2"),
                CollectionFactCrossRef("col-1", "f3"),
                CollectionFactCrossRef("col-1", "f4"),
            )
        )

        assertThat(poolIds()).containsExactly("f1", "f3", "f4").inOrder()
    }

    @Test
    fun `excluding a collection from rotation falls back to document sources`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToCollection(
            listOf(CollectionFactCrossRef("col-1", "f3"), CollectionFactCrossRef("col-1", "f4"))
        )
        assertThat(poolIds()).containsExactly("f3", "f4").inOrder()

        collectionDao.setCollectionInRotation("col-1", included = false)

        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `unaccepted ai collections never become explicit sources`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(
                id = "ai-1",
                name = "Suggested",
                source = "AI_SUGGESTED",
                accepted = false,
                createdAtEpochMs = 1L,
            )
        )
        collectionDao.addFactsToCollection(
            listOf(CollectionFactCrossRef("ai-1", "f3"), CollectionFactCrossRef("ai-1", "f4"))
        )

        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `folder sources join with collection sources`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertFolder(
            UserFolderEntity(id = "folder-1", name = "Favourites", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToFolder(listOf(FolderFactCrossRef("folder-1", "f3")))

        assertThat(poolIds()).containsExactly("f3").inOrder()

        val summary = repository.observePoolSummary().first()
        assertThat(summary.factCount).isEqualTo(1)
        assertThat(summary.folderCount).isEqualTo(1)
        assertThat(summary.collectionCount).isEqualTo(0)
    }

    @Test
    fun `summary counts only accepted and rotation-enabled sources`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        collectionDao.upsertCollection(
            CollectionEntity(
                id = "col-2",
                name = "Suggested",
                source = "AI_SUGGESTED",
                accepted = false,
                createdAtEpochMs = 2L,
            )
        )
        collectionDao.upsertFolder(
            UserFolderEntity(id = "folder-1", name = "Favourites", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToCollection(listOf(CollectionFactCrossRef("col-1", "f3")))

        val summary = repository.observePoolSummary().first()

        assertThat(summary.factCount).isEqualTo(1)
        assertThat(summary.collectionCount).isEqualTo(1)
        assertThat(summary.folderCount).isEqualTo(1)
    }

    @Test
    fun `random order is a stable hash permutation of the resolved pool`() = runBlocking<Unit> {
        seed()
        val factDao = database.factDao()
        factDao.upsert(fact("f5", "doc-1", createdAt = 5_000L))
        factDao.upsert(fact("f6", "doc-1", createdAt = 6_000L))
        wallpaperRepository.updateState { it.copy(rotationOrder = RotationOrder.RANDOM) }

        val first = poolIds()
        val second = poolIds()
        val expected = listOf("f1", "f5", "f6")
            .sortedWith(compareBy({ sha256Hex(it) }, { it }))

        assertThat(first).containsExactly("f1", "f5", "f6")
        assertThat(first).isEqualTo(expected)
        assertThat(second).isEqualTo(first)
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}

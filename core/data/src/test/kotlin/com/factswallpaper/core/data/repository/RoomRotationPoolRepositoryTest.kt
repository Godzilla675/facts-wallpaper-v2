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
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.factswallpaper.core.datastore.AppPreferences
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.AutoProcessMode
import com.factswallpaper.core.model.DocumentInputMode
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.RotationSource
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private lateinit var fakeSettingsRepository: FakeSettingsRepository
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
        fakeSettingsRepository = FakeSettingsRepository()
        repository = RoomRotationPoolRepository(
            factDao = database.factDao(),
            documentDao = database.pdfDocumentDao(),
            collectionDao = database.collectionDao(),
            wallpaperRepository = wallpaperRepository,
            settingsRepository = fakeSettingsRepository,
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
        active: Boolean = true,
        favorite: Boolean = false,
        createdAt: Long = 1_000L,
        nextReviewDueEpochMs: Long? = null,
    ) = FactItemEntity(
        id = id,
        documentId = documentId,
        lessonId = lessonId,
        text = "Fact $id",
        active = active,
        favorite = favorite,
        confidence = 0.9f,
        templateType = "TEXT",
        diagramType = "NONE",
        diagramSource = null,
        latex = null,
        verificationStatus = "UNVERIFIED",
        sourceBookTitle = "Doc $documentId",
        sourceLessonTitle = null,
        sourcePage = 1,
        sourceReferenceModeOverride = null,
        nextReviewDueEpochMs = nextReviewDueEpochMs,
        createdAtEpochMs = createdAt,
    )

    private suspend fun seed() {
        val docDao = database.pdfDocumentDao()
        val lessonDao = database.lessonDao()
        val factDao = database.factDao()

        docDao.upsert(document("doc-1", createdAt = 100L, included = true))
        docDao.upsert(document("doc-2", createdAt = 200L, included = false))

        lessonDao.upsert(
            LessonEntity(
                id = "lesson-1",
                documentId = "doc-1",
                title = "Overview",
                summary = "Intro summary",
                startPage = 1,
                endPage = 5,
                sortOrder = 0,
                chapterTitle = "Intro",
            )
        )

        factDao.upsert(fact("f1", "doc-1", "lesson-1", createdAt = 1_000L))
        factDao.upsert(fact("f2", "doc-1", active = false, createdAt = 2_000L))
        factDao.upsert(fact("f3", "doc-2", createdAt = 3_000L))
        factDao.upsert(fact("f4", "doc-2", createdAt = 4_000L))
    }

    private suspend fun poolIds(): List<String> = repository.observePool().first().map { it.id }

    @Test
    fun `default pool yields active facts from included documents in canonical order`() =
        runBlocking<Unit> {
            seed()
            assertThat(poolIds()).containsExactly("f1").inOrder()
        }

    @Test
    fun `rotation source favorites filters pool to favorites only`() = runBlocking<Unit> {
        seed()
        database.factDao().upsert(fact("f1", "doc-1", "lesson-1", createdAt = 1_000L, favorite = true))
        fakeSettingsRepository.setRotationSource(RotationSource.FAVORITES)
        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `rotation source due for review filters pool to due facts only`() = runBlocking<Unit> {
        seed()
        val factDao = database.factDao()
        // f1 has null due date (due immediately)
        // f5 is scheduled in the future (+1 hour)
        factDao.upsert(fact("f5", "doc-1", createdAt = 5_000L, nextReviewDueEpochMs = System.currentTimeMillis() + 3_600_000L))
        fakeSettingsRepository.setRotationSource(RotationSource.DUE_FOR_REVIEW)
        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `rotation source all cards includes all active facts even when collections are selected`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToCollection(listOf(CollectionFactCrossRef("col-1", "f3")))
        // Default (COLLECTIONS) yields f3 because explicit collection is selected
        assertThat(poolIds()).containsExactly("f3")

        fakeSettingsRepository.setRotationSource(RotationSource.ALL)
        // ALL yields f1 from included doc-1
        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `only favorites flag restricts pool even when document is included`() = runBlocking<Unit> {
        seed()
        wallpaperRepository.updateState { it.copy(onlyFavoritesInPool = true) }
        assertThat(poolIds()).isEmpty()

        database.factDao().upsert(fact("f1", "doc-1", "lesson-1", createdAt = 1_000L, favorite = true))
        assertThat(poolIds()).containsExactly("f1")
    }

    @Test
    fun `explicit collection selection overrides document rotation flags`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(id = "col-1", name = "Deck", createdAtEpochMs = 1L)
        )
        collectionDao.addFactsToCollection(
            listOf(CollectionFactCrossRef("col-1", "f4"), CollectionFactCrossRef("col-1", "f2"))
        )

        assertThat(poolIds()).containsExactly("f4")
    }

    @Test
    fun `unaccepted AI suggestions are never included in the pool`() = runBlocking<Unit> {
        seed()
        val collectionDao = database.collectionDao()
        collectionDao.upsertCollection(
            CollectionEntity(
                id = "ai-1",
                name = "AI",
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
        override suspend fun setRotationSource(source: RotationSource) {
            preferencesFlow.value = preferencesFlow.value.copy(rotationSource = source)
        }
        override suspend fun setQuietHoursEnabled(enabled: Boolean) {}
        override suspend fun setQuietHoursWindow(startMinute: Int, endMinute: Int) {}
        override suspend fun setRotationPaused(paused: Boolean) {}
        override suspend fun setBatterySaverSlowdownEnabled(enabled: Boolean) {}
        override suspend fun setDailyFactNotificationEnabled(enabled: Boolean) {}
        override suspend fun setDailyFactNotificationTime(timeMinutes: Int) {}
        override suspend fun setDynamicColorEnabled(enabled: Boolean) {}
    }
}

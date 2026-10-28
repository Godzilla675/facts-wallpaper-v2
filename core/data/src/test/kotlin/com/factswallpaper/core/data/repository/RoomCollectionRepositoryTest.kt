package com.factswallpaper.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.model.CollectionSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Collection acceptance state decides what the user sees as an accepted deck versus a
 * pending AI suggestion, and whether the deck can feed the wallpaper pool. Verified
 * against a real Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomCollectionRepositoryTest {

    private lateinit var database: FactsDatabase
    private lateinit var repository: RoomCollectionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomCollectionRepository(
            collectionDao = database.collectionDao(),
            dispatchers = FactsDispatchers(
                io = Dispatchers.Unconfined,
                default = Dispatchers.Unconfined,
                main = Dispatchers.Unconfined,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `user collections are accepted and rotate while ai suggestions stay pending`() =
        runBlocking<Unit> {
            seedFacts()

            val accepted = repository.createCollection("Midterm Prep", "Exam study deck")
            assertThat(accepted.source).isEqualTo(CollectionSource.USER)
            assertThat(accepted.accepted).isTrue()
            assertThat(accepted.includeInRotation).isTrue()

            val suggestion = repository.createSuggestedCollection(
                name = "Thermodynamics concepts",
                description = "Grouped from tags",
                factIds = listOf("fact-1", "fact-2"),
            )
            assertThat(suggestion.source).isEqualTo(CollectionSource.AI_SUGGESTED)
            assertThat(suggestion.accepted).isFalse()
            assertThat(suggestion.includeInRotation).isFalse()
            assertThat(suggestion.factCount).isEqualTo(2)

            val stored = repository.observeCollections().first()
            assertThat(stored.map { it.id }).containsExactly(accepted.id, suggestion.id)
            assertThat(stored.filter { it.source == CollectionSource.AI_SUGGESTED && !it.accepted })
                .hasSize(1)
            assertThat(stored.first { it.id == accepted.id }.factCount).isEqualTo(0)
            // The fact count is joined from the cross-reference table, not from the insert call.
            assertThat(stored.first { it.id == suggestion.id }.factCount).isEqualTo(2)
        }

    @Test
    fun `accepting a suggestion flips the stored state and keeps its facts`() = runBlocking<Unit> {
        seedFacts()
        val suggestion = repository.createSuggestedCollection(
            name = "Electromagnetism concepts",
            description = "Accepted AI deck",
            factIds = listOf("fact-1"),
        )

        repository.setCollectionAccepted(suggestion.id, accepted = true)
        repository.setCollectionInRotation(suggestion.id, included = true)

        val accepted = repository.observeCollections().first().single { it.id == suggestion.id }
        assertThat(accepted.accepted).isTrue()
        assertThat(accepted.includeInRotation).isTrue()
        assertThat(accepted.factCount).isEqualTo(1)
        assertThat(repository.observeCollectionFactIds(suggestion.id).first())
            .containsExactly("fact-1")
    }

    @Test
    fun `replacing collection facts removes the previous membership`() = runBlocking<Unit> {
        seedFacts()
        val collection = repository.createCollection("Deck", "")

        repository.setCollectionFacts(collection.id, listOf("fact-1", "fact-2"))
        assertThat(repository.observeCollectionFactIds(collection.id).first())
            .containsExactly("fact-1", "fact-2")

        repository.setCollectionFacts(collection.id, listOf("fact-3"))
        assertThat(repository.observeCollectionFactIds(collection.id).first())
            .containsExactly("fact-3")
        assertThat(repository.observeCollections().first().single().factCount).isEqualTo(1)
    }

    @Test
    fun `blank collection and folder names are rejected before touching the database`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createCollection("   ", "description") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.createFolder("   ") }
        }
    }

    private suspend fun seedFacts() {
        database.pdfDocumentDao().upsert(
            PdfDocumentEntity(
                id = "doc-1",
                title = "Physics",
                sourceUri = "content://physics",
                localFilePath = "/physics.pdf",
                fileHash = "hash-physics",
                fileSizeBytes = 1_000L,
                pageCount = 10,
                pagesWithText = 10,
                processingRoute = "EXTRACT_TEXT",
                ocrEngine = null,
                extractionStatus = "FACTS_READY",
                createdAtEpochMs = 1_000L,
            ),
        )
        listOf("fact-1", "fact-2", "fact-3").forEachIndexed { index, id ->
            database.factDao().upsert(
                FactItemEntity(
                    id = id,
                    documentId = "doc-1",
                    lessonId = null,
                    text = "Fact $id",
                    sourceBookTitle = "Physics",
                    sourceLessonTitle = null,
                    sourcePage = index + 1,
                    createdAtEpochMs = index.toLong(),
                ),
            )
        }
    }
}

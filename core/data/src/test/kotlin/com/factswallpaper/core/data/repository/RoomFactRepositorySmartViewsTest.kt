package com.factswallpaper.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.model.SmartViewCounts
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Smart-view counters power the "Active / Favourites / Formulas / Diagrams" pills,
 * so they are verified against a real Room database rather than a hand-built list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomFactRepositorySmartViewsTest {

    private lateinit var database: FactsDatabase
    private lateinit var repository: RoomFactRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomFactRepository(
            factDao = database.factDao(),
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
    fun `counts partition the stored facts by active favourite formula and diagram`() =
        runBlocking<Unit> {
            seedDocument()
            seedFacts()

            val counts = repository.observeSmartViewCounts().first()

            assertThat(counts.total).isEqualTo(4)
            assertThat(counts.active).isEqualTo(3)
            assertThat(counts.favorites).isEqualTo(2)
            assertThat(counts.formulas).isEqualTo(1)
            assertThat(counts.diagrams).isEqualTo(1)
        }

    @Test
    fun `counts react to database mutations instead of caching the first result`() =
        runBlocking<Unit> {
            seedDocument()
            seedFacts()
            val before = repository.observeSmartViewCounts().first()
            assertThat(before.active).isEqualTo(3)
            assertThat(before.diagrams).isEqualTo(1)

            database.factDao().setActive("fact-plain", active = false, updatedAtEpochMs = 9_000L)
            database.factDao().upsert(
                fact("fact-extra-diagram", diagramType = "MERMAID", createdAt = 7_000L),
            )

            val after = repository.observeSmartViewCounts().first()

            assertThat(after.total).isEqualTo(5)
            assertThat(after.active).isEqualTo(3)
            assertThat(after.diagrams).isEqualTo(2)
        }

    @Test
    fun `empty database reports zeroed counters rather than null`() = runBlocking<Unit> {
        assertThat(repository.observeSmartViewCounts().first()).isEqualTo(
            SmartViewCounts(total = 0, active = 0, favorites = 0, formulas = 0, diagrams = 0),
        )
        assertThat(repository.observeTotalCount().first()).isEqualTo(0)
        assertThat(repository.observeTags().first()).isEmpty()
    }

    @Test
    fun `total count and tag aggregation reflect stored rows`() = runBlocking<Unit> {
        seedDocument()
        seedFacts()

        assertThat(repository.observeTotalCount().first()).isEqualTo(4)
        assertThat(repository.observeTags().first().map { it.tag })
            .containsExactly("entropy", "physics", "thermodynamics")
            .inOrder()
    }

    private suspend fun seedDocument() {
        database.pdfDocumentDao().upsert(
            PdfDocumentEntity(
                id = DOCUMENT_ID,
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
    }

    private suspend fun seedFacts() {
        val dao = database.factDao()
        dao.upsertFactWithTags(fact("fact-plain", favorite = true), tags = listOf("physics"))
        dao.upsertFactWithTags(
            fact(
                id = "fact-formula",
                latex = "S = k_B \\ln \\Omega",
                requiresLatex = true,
                templateType = "FORMULA",
            ),
            tags = listOf("physics", "entropy"),
        )
        dao.upsertFactWithTags(
            fact("fact-diagram", diagramType = "MERMAID", diagramSource = "graph TD; A-->B"),
            tags = listOf("thermodynamics"),
        )
        dao.upsertFactWithTags(
            fact("fact-ignored", active = false, favorite = true),
            tags = listOf("physics"),
        )
    }

    private fun fact(
        id: String,
        createdAt: Long = 1_000L,
        active: Boolean = true,
        favorite: Boolean = false,
        latex: String? = null,
        requiresLatex: Boolean = false,
        templateType: String = "TEXT",
        diagramType: String = "NONE",
        diagramSource: String? = null,
    ) = FactItemEntity(
        id = id,
        documentId = DOCUMENT_ID,
        lessonId = null,
        text = "Fact $id",
        latex = latex,
        requiresLatex = requiresLatex,
        templateType = templateType,
        diagramType = diagramType,
        diagramSource = diagramSource,
        sourceBookTitle = "Physics",
        sourceLessonTitle = null,
        sourcePage = 1,
        active = active,
        favorite = favorite,
        createdAtEpochMs = createdAt,
    )

    private companion object {
        const val DOCUMENT_ID = "doc-1"
    }
}

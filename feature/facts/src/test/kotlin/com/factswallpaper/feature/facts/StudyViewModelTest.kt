package com.factswallpaper.feature.facts

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.UserFolder
import com.factswallpaper.core.render.card.CardBitmapRenderer
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateSeeder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
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
class StudyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val factRepository = FakeFactRepository()
    private val collectionRepository = FakeStudyCollectionRepository()
    private val templateRepository = FakeStudyTemplateRepository()
    private lateinit var renderer: CardBitmapRenderer

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dispatchers = FactsDispatchers(testDispatcher, testDispatcher, testDispatcher)
        renderer = CardBitmapRenderer(context, dispatchers)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle(mapOf("documentId" to "doc-1"))) =
        StudyViewModel(
            savedStateHandle = handle,
            factRepository = factRepository,
            collectionRepository = collectionRepository,
            templateRepository = templateRepository,
            templateSeeder = CardTemplateSeeder(templateRepository),
            renderer = renderer,
            dispatchers = FactsDispatchers(testDispatcher, testDispatcher, testDispatcher),
        )

    @Test
    fun `document scope keeps the full pool but counts only active due facts`() = runTest {
        val viewModel = viewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        factRepository.factsFlow.value = listOf(
            fact("due", dueEpochMs = now() - 1_000L),
            fact("future", dueEpochMs = now() + 86_400_000L),
            fact("inactive", dueEpochMs = now() - 1_000L, active = false),
            fact("other-doc", documentId = "doc-2", dueEpochMs = now() - 1_000L),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as StudyUiState.Content
        assertThat(state.facts.map { it.id }).containsExactly("due", "future", "inactive").inOrder()
        assertThat(state.totalDueCount).isEqualTo(1)
        assertThat(state.totalAvailableCount).isEqualTo(3)
        assertThat(state.isDueOnly).isFalse()
        collectJob.cancel()
    }

    @Test
    fun `due-only filter keeps only facts whose review is due`() = runTest {
        val viewModel = viewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        factRepository.factsFlow.value = listOf(
            fact("due", dueEpochMs = now() - 1_000L),
            fact("future", dueEpochMs = now() + 86_400_000L),
            fact("never-reviewed", dueEpochMs = null),
        )
        advanceUntilIdle()

        viewModel.setDueOnly(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value as StudyUiState.Content
        assertThat(state.facts.map { it.id }).containsExactly("due", "never-reviewed")
        assertThat(state.isDueOnly).isTrue()
        assertThat(state.totalDueCount).isEqualTo(2)
        assertThat(state.totalAvailableCount).isEqualTo(3)

        viewModel.toggleDueOnly()
        advanceUntilIdle()
        assertThat((viewModel.uiState.value as StudyUiState.Content).facts).hasSize(3)
        collectJob.cancel()
    }

    @Test
    fun `empty due-only session reports the available backlog`() = runTest {
        val viewModel = viewModel(
            SavedStateHandle(mapOf("documentId" to "doc-1", "dueOnly" to true)),
        )
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        factRepository.factsFlow.value = listOf(
            fact("future-1", dueEpochMs = now() + 86_400_000L),
            fact("future-2", dueEpochMs = now() + 172_800_000L),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value as StudyUiState.Empty
        assertThat(state.isDueOnly).isTrue()
        assertThat(state.totalAvailableCount).isEqualTo(2)

        viewModel.setDueOnly(false)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value).isInstanceOf(StudyUiState.Content::class.java)
        collectJob.cancel()
    }

    @Test
    fun `recordReview delegates to the repository and updates session counters`() = runTest {
        val viewModel = viewModel()
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        factRepository.factsFlow.value = listOf(
            fact("due-1", dueEpochMs = now() - 1_000L),
            fact("due-2", dueEpochMs = now() - 1_000L),
        )
        advanceUntilIdle()

        viewModel.recordReview("due-1", gotIt = true)
        viewModel.recordReview("due-2", gotIt = false)
        advanceUntilIdle()

        assertThat(factRepository.recordedReviews)
            .containsExactly("due-1" to true, "due-2" to false)
        val state = viewModel.uiState.value as StudyUiState.Content
        assertThat(state.sessionStats.gotItCount).isEqualTo(1)
        assertThat(state.sessionStats.againCount).isEqualTo(1)

        viewModel.resetSession()
        advanceUntilIdle()
        val reset = viewModel.uiState.value as StudyUiState.Content
        assertThat(reset.sessionStats.gotItCount).isEqualTo(0)
        assertThat(reset.sessionStats.againCount).isEqualTo(0)
        collectJob.cancel()
    }

    @Test
    fun `collection and tag scopes restrict the study pool`() = runTest {
        factRepository.factsFlow.value = listOf(
            fact("member", tags = listOf("physics")),
            fact("outside", tags = listOf("biology")),
        )
        collectionRepository.memberIds.value = listOf("member")

        val collectionViewModel = viewModel(SavedStateHandle(mapOf("collectionId" to "col-1")))
        val collectionJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            collectionViewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertThat((collectionViewModel.uiState.value as StudyUiState.Content).facts.map { it.id })
            .containsExactly("member")
        collectionJob.cancel()

        val tagViewModel = viewModel(SavedStateHandle(mapOf("tag" to "biology")))
        val tagJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            tagViewModel.uiState.collect {}
        }
        advanceUntilIdle()
        assertThat((tagViewModel.uiState.value as StudyUiState.Content).facts.map { it.id })
            .containsExactly("outside")
        tagJob.cancel()
    }

    private fun now() = System.currentTimeMillis()

    private fun fact(
        id: String,
        documentId: String = "doc-1",
        dueEpochMs: Long? = null,
        active: Boolean = true,
        tags: List<String> = emptyList(),
    ) = Fact(
        id = id,
        documentId = documentId,
        lessonId = "lesson-1",
        text = "Fact $id",
        sourceBookTitle = "Test Book",
        active = active,
        tags = tags,
        createdAtEpochMs = 1_000L,
        nextReviewDueEpochMs = dueEpochMs,
    )
}

private class FakeStudyTemplateRepository : TemplateRepository {
    private val defaultTemplate: CardTemplate =
        requireNotNull(BuiltInTemplates.byId(BuiltInTemplates.DEFAULT_TEMPLATE_ID))
    private val templates = MutableStateFlow(listOf(defaultTemplate))

    override fun observeTemplates(): Flow<List<CardTemplate>> = templates
    override fun observeDefaultTemplate(): Flow<CardTemplate?> =
        MutableStateFlow(templates.value.firstOrNull { it.isDefault })
    override suspend fun getTemplates(): List<CardTemplate> = templates.value
    override suspend fun getTemplate(templateId: String): CardTemplate? =
        templates.value.firstOrNull { it.id == templateId }
    override suspend fun saveTemplate(template: CardTemplate) {
        templates.value = templates.value.filter { it.id != template.id } + template
    }
    override suspend fun saveTemplates(templates: List<CardTemplate>) {
        this.templates.value = this.templates.value.filter { existing ->
            templates.none { it.id == existing.id }
        } + templates
    }
    override suspend fun deleteCustomTemplate(templateId: String): AppResult<Unit> {
        templates.value = templates.value.filter { it.id != templateId }
        return AppResult.Success(Unit)
    }
    override suspend fun setDefaultTemplate(templateId: String) {
        templates.value = templates.value.map { it.copy(isDefault = it.id == templateId) }
    }
}

private class FakeStudyCollectionRepository : CollectionRepository {
    val memberIds = MutableStateFlow<List<String>>(emptyList())

    override fun observeCollections(): Flow<List<Collection>> = flowOf(emptyList())
    override fun observeFolders(): Flow<List<UserFolder>> = flowOf(emptyList())
    override suspend fun createCollection(name: String, description: String): Collection =
        throw UnsupportedOperationException()
    override suspend fun createFolder(name: String): UserFolder = throw UnsupportedOperationException()
    override suspend fun renameCollection(collectionId: String, name: String, description: String) = Unit
    override suspend fun renameFolder(folderId: String, name: String) = Unit
    override suspend fun createSuggestedCollection(
        name: String,
        description: String,
        factIds: List<String>,
    ): Collection = throw UnsupportedOperationException()
    override fun observeCollectionFactIds(collectionId: String): Flow<List<String>> = memberIds
    override fun observeFolderFactIds(folderId: String): Flow<List<String>> = flowOf(emptyList())
    override suspend fun setCollectionFacts(collectionId: String, factIds: List<String>) {
        memberIds.value = factIds
    }
    override suspend fun setFolderFacts(folderId: String, factIds: List<String>) = Unit
    override suspend fun setCollectionInRotation(collectionId: String, included: Boolean) = Unit
    override suspend fun setCollectionAccepted(collectionId: String, accepted: Boolean) = Unit
    override suspend fun deleteCollection(collectionId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun setFolderInRotation(folderId: String, included: Boolean) = Unit
    override suspend fun deleteFolder(folderId: String): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun addFactsToCollection(collectionId: String, factIds: List<String>) = Unit
    override suspend fun removeFactFromCollection(collectionId: String, factId: String) = Unit
    override suspend fun addFactsToFolder(folderId: String, factIds: List<String>) = Unit
    override suspend fun removeFactFromFolder(folderId: String, factId: String) = Unit
}

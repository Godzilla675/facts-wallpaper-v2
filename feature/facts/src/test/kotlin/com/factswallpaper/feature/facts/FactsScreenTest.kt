package com.factswallpaper.feature.facts

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.ui.R as CoreUiR
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Section 10.2 Compose UI coverage for the fact-curation flow: content,
 * empty state and persistence of the favorite toggle through the real
 * ViewModel and repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FactsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = UnconfinedTestDispatcher()

    private val factRepository = FakeFactRepository()
    private val documentRepository = FakeDocumentRepository()
    private val documentTextRepository = FakeDocumentTextRepository()
    private val aiService = FakeStructuredAiService()
    private val networkMonitor = FakeNetworkMonitor()
    private lateinit var viewModel: FactsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        viewModel = FactsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("documentId" to "doc-1")),
            factRepository = factRepository,
            documentRepository = documentRepository,
            documentTextRepository = documentTextRepository,
            structuredAiService = aiService,
            networkMonitor = networkMonitor,
            workManager = WorkManager.getInstance(context),
            preRenderScheduler = CardPreRenderScheduler(context),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `content state renders fact text and source badge`() {
        factRepository.factsFlow.value = listOf(fact())
        showFacts()

        composeRule.onNodeWithText("Entropy never decreases in an isolated system.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Physics 101", substring = true).assertIsDisplayed()
    }

    @Test
    fun `document filter with no facts shows the clear-filters empty state`() {
        showFacts()

        composeRule.onNodeWithText(context.getString(R.string.facts_no_matches_title))
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(context.getString(R.string.facts_clear_filters))
            .onFirst()
            .assertIsDisplayed()
    }

    @Test
    fun `favorite action persists through the repository`() {
        factRepository.factsFlow.value = listOf(fact())
        showFacts()

        composeRule.onNodeWithContentDescription(context.getString(CoreUiR.string.fact_favorite))
            .performClick()
        composeRule.waitForIdle()

        assertThat(factRepository.factsFlow.value.single().favorite).isTrue()
    }

    private fun showFacts() {
        composeRule.setContent {
            FactsScreen(
                onOpenLibrary = {},
                onOpenTemplates = {},
                viewModel = viewModel,
            )
        }
    }

    private fun fact() = Fact(
        id = "fact-1",
        documentId = "doc-1",
        lessonId = "lesson-1",
        text = "Entropy never decreases in an isolated system.",
        active = true,
        sourceBookTitle = "Physics 101",
        sourceLessonTitle = "Thermodynamics",
        sourcePage = 4,
        createdAtEpochMs = 1_000L,
    )
}

package com.factswallpaper.feature.collections

import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.RotationPoolSummary
import com.factswallpaper.core.model.SmartViewCounts
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The collections screen renders two different lists from one query: decks the user
 * accepted and AI suggestions still awaiting a decision. That partition lives in
 * [CollectionsUiState.Content] and is asserted here directly.
 */
class CollectionsUiStateTest {

    private val userCollection = collection(
        id = "c-user",
        name = "Midterm Prep",
        source = CollectionSource.USER,
        accepted = true,
    )
    private val pendingSuggestion = collection(
        id = "c-ai-pending",
        name = "Thermodynamics concepts",
        source = CollectionSource.AI_SUGGESTED,
        accepted = false,
    )
    private val acceptedSuggestion = collection(
        id = "c-ai-accepted",
        name = "Electromagnetism concepts",
        source = CollectionSource.AI_SUGGESTED,
        accepted = true,
    )

    private val content = CollectionsUiState.Content(
        counts = SmartViewCounts(
            total = 25,
            active = 18,
            favorites = 4,
            formulas = 3,
            diagrams = 2,
            dueForReview = 6,
        ),
        poolSummary = RotationPoolSummary(factCount = 18, collectionCount = 2, folderCount = 1),
        collections = listOf(userCollection, pendingSuggestion, acceptedSuggestion),
        folders = emptyList(),
        tags = emptyList(),
    )

    @Test
    fun `pending ai suggestions are separated from accepted decks`() {
        assertThat(content.suggestions.map { it.id }).containsExactly("c-ai-pending")
        assertThat(content.activeCollections.map { it.id })
            .containsExactly("c-user", "c-ai-accepted")
            .inOrder()
    }

    @Test
    fun `accepting a suggestion moves it from pending to active without refetching`() {
        val afterAccept = content.copy(
            collections = content.collections.map {
                if (it.id == "c-ai-pending") it.copy(accepted = true) else it
            },
        )

        assertThat(afterAccept.suggestions).isEmpty()
        // activeCollections keeps the source list order: user, the now-accepted suggestion,
        // then the suggestion that was already accepted.
        assertThat(afterAccept.activeCollections.map { it.id })
            .containsExactly("c-user", "c-ai-pending", "c-ai-accepted")
            .inOrder()
    }

    @Test
    fun `an unaccepted user collection is never surfaced as an ai suggestion`() {
        val unacceptedUser = collection(
            id = "c-user-draft",
            name = "Draft deck",
            source = CollectionSource.USER,
            accepted = false,
        )
        val withDraft = content.copy(collections = content.collections + unacceptedUser)

        assertThat(withDraft.suggestions.map { it.id }).containsExactly("c-ai-pending")
        assertThat(withDraft.activeCollections.map { it.id })
            .doesNotContain("c-user-draft")
    }

    @Test
    fun `the ui state carries the counters rendered by the smart view pills`() {
        assertThat(content.counts.total).isEqualTo(25)
        assertThat(content.counts.active).isEqualTo(18)
        assertThat(content.counts.favorites).isEqualTo(4)
        assertThat(content.counts.formulas).isEqualTo(3)
        assertThat(content.counts.diagrams).isEqualTo(2)
        assertThat(content.counts.dueForReview).isEqualTo(6)
        assertThat(content.poolSummary.factCount).isEqualTo(18)
    }

    private fun collection(
        id: String,
        name: String,
        source: CollectionSource,
        accepted: Boolean,
    ) = Collection(
        id = id,
        name = name,
        description = "Deck $id",
        source = source,
        accepted = accepted,
        includeInRotation = accepted,
        factCount = if (accepted) 8 else 5,
        createdAtEpochMs = 1L,
    )
}

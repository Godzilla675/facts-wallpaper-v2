package com.factswallpaper.core.wallpaper

import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RotationReducerTest {

    private fun createTestFact(id: String): Fact = Fact(
        id = id,
        documentId = "doc1",
        lessonId = "lesson1",
        text = "Fact text for $id",
        sourceBookTitle = "Test Book",
        createdAtEpochMs = 1000L,
    )

    @Test
    fun `sequential rotation advances to next index and wraps around`() {
        val pool = listOf(createTestFact("f1"), createTestFact("f2"), createTestFact("f3"))
        val state0 = WallpaperState(
            currentFactId = "f1",
            currentIndex = 0,
            rotationOrder = RotationOrder.SEQUENTIAL,
        )

        val state1 = RotationReducer.advance(state0, pool, nowEpochMs = 1000L)
        assertThat(state1.currentFactId).isEqualTo("f2")
        assertThat(state1.currentIndex).isEqualTo(1)
        assertThat(state1.lastRotatedAtEpochMs).isEqualTo(1000L)

        val state2 = RotationReducer.advance(state1, pool, nowEpochMs = 2000L)
        assertThat(state2.currentFactId).isEqualTo("f3")
        assertThat(state2.currentIndex).isEqualTo(2)

        val state3 = RotationReducer.advance(state2, pool, nowEpochMs = 3000L)
        assertThat(state3.currentFactId).isEqualTo("f1")
        assertThat(state3.currentIndex).isEqualTo(0)
    }

    @Test
    fun `empty pool returns null factId and index 0`() {
        val state = WallpaperState(currentFactId = "f1", currentIndex = 2)
        val result = RotationReducer.advance(state, emptyList(), nowEpochMs = 5000L)

        assertThat(result.currentFactId).isNull()
        assertThat(result.currentIndex).isEqualTo(0)
        assertThat(result.lastRotatedAtEpochMs).isEqualTo(5000L)
    }

    @Test
    fun `single item pool stays on single fact`() {
        val pool = listOf(createTestFact("only_one"))
        val state = WallpaperState(currentFactId = "only_one", currentIndex = 0)
        val result = RotationReducer.advance(state, pool, nowEpochMs = 6000L)

        assertThat(result.currentFactId).isEqualTo("only_one")
        assertThat(result.currentIndex).isEqualTo(0)
    }

    @Test
    fun `random rotation advances without back-to-back repeat when pool has multiple items`() {
        val pool = listOf(createTestFact("f1"), createTestFact("f2"), createTestFact("f3"))
        val state = WallpaperState(
            currentFactId = "f1",
            currentIndex = 0,
            rotationOrder = RotationOrder.RANDOM,
        )

        val next = RotationReducer.advance(state, pool, nowEpochMs = 1000L)
        assertThat(next.currentFactId).isNotEqualTo("f1")
    }

    @Test
    fun `unlock cooldown enforces 15 second minimum between rotations`() {
        val lastRotated = 100_000L
        // 5 seconds later: cooldown should still be active
        assertThat(RotationReducer.isUnlockCooldownActive(lastRotated, nowEpochMs = 105_000L)).isTrue()
        // 14.9 seconds later: cooldown should still be active
        assertThat(RotationReducer.isUnlockCooldownActive(lastRotated, nowEpochMs = 114_900L)).isTrue()
        // 15 seconds later: cooldown should expire
        assertThat(RotationReducer.isUnlockCooldownActive(lastRotated, nowEpochMs = 115_000L)).isFalse()
        // 30 seconds later: cooldown expired
        assertThat(RotationReducer.isUnlockCooldownActive(lastRotated, nowEpochMs = 130_000L)).isFalse()
    }

    @Test
    fun `interval elapsed checks configured interval minutes correctly`() {
        val lastRotated = 1_000_000L
        val intervalMinutes = 30
        val intervalMs = 30 * 60_000L

        // 10 minutes later: not elapsed
        assertThat(RotationReducer.isIntervalElapsed(lastRotated, intervalMinutes, nowEpochMs = lastRotated + 10 * 60_000L)).isFalse()
        // 30 minutes later: elapsed
        assertThat(RotationReducer.isIntervalElapsed(lastRotated, intervalMinutes, nowEpochMs = lastRotated + intervalMs)).isTrue()
        // 45 minutes later: elapsed
        assertThat(RotationReducer.isIntervalElapsed(lastRotated, intervalMinutes, nowEpochMs = lastRotated + 45 * 60_000L)).isTrue()
    }
}

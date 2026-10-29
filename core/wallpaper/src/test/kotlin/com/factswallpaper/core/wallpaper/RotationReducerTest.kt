package com.factswallpaper.core.wallpaper

import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.google.common.truth.Truth.assertThat
import java.util.Calendar
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

    @Test
    fun `quiet hours disabled never blocks rotation`() {
        val noon = localEpochMs(hour = 12, minute = 0)
        assertThat(RotationReducer.isQuietHours(noon, enabled = false, startMinute = 0, endMinute = 1439))
            .isFalse()
    }

    @Test
    fun `same-day quiet hours include both boundaries and exclude outside times`() {
        val start = 9 * 60
        val end = 17 * 60
        assertThat(RotationReducer.isQuietHours(localEpochMs(8, 59), true, start, end)).isFalse()
        assertThat(RotationReducer.isQuietHours(localEpochMs(9, 0), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(12, 30), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(17, 0), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(17, 1), true, start, end)).isFalse()
    }

    @Test
    fun `quiet hours window can span midnight`() {
        val start = 22 * 60
        val end = 7 * 60
        assertThat(RotationReducer.isQuietHours(localEpochMs(21, 59), true, start, end)).isFalse()
        assertThat(RotationReducer.isQuietHours(localEpochMs(22, 0), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(23, 30), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(0, 15), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(6, 59), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(7, 0), true, start, end)).isTrue()
        assertThat(RotationReducer.isQuietHours(localEpochMs(7, 1), true, start, end)).isFalse()
        assertThat(RotationReducer.isQuietHours(localEpochMs(12, 0), true, start, end)).isFalse()
    }

    @Test
    fun `battery saver quadruples unlock cooldown only when slowdown is enabled`() {
        assertThat(RotationReducer.effectiveUnlockCooldownMs(batterySaver = false, batterySaverSlowdownEnabled = true))
            .isEqualTo(RotationReducer.UNLOCK_COOLDOWN_MS)
        assertThat(RotationReducer.effectiveUnlockCooldownMs(batterySaver = true, batterySaverSlowdownEnabled = false))
            .isEqualTo(RotationReducer.UNLOCK_COOLDOWN_MS)
        assertThat(RotationReducer.effectiveUnlockCooldownMs(batterySaver = true, batterySaverSlowdownEnabled = true))
            .isEqualTo(RotationReducer.UNLOCK_COOLDOWN_MS * 4)
    }

    @Test
    fun `battery saver doubles interval only when slowdown is enabled`() {
        assertThat(
            RotationReducer.effectiveIntervalMinutes(
                intervalMinutes = 30,
                batterySaver = false,
                batterySaverSlowdownEnabled = true,
            ),
        ).isEqualTo(30)
        assertThat(
            RotationReducer.effectiveIntervalMinutes(
                intervalMinutes = 30,
                batterySaver = true,
                batterySaverSlowdownEnabled = false,
            ),
        ).isEqualTo(30)
        assertThat(
            RotationReducer.effectiveIntervalMinutes(
                intervalMinutes = 30,
                batterySaver = true,
                batterySaverSlowdownEnabled = true,
            ),
        ).isEqualTo(60)
    }

    private fun localEpochMs(hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(2026, Calendar.OCTOBER, 29, hour, minute, 0)
        }.timeInMillis
}

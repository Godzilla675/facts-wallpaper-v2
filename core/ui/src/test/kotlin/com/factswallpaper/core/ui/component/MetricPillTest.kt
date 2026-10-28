package com.factswallpaper.core.ui.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.factswallpaper.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Accessibility contract for the tappable metric pills used by smart views. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MetricPillTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `clickable pill is a 48dp button with an action description`() {
        var clicks = 0
        composeRule.setContent {
            MetricPill(value = "12", label = "Active", onClick = { clicks++ })
        }

        val description = context.getString(R.string.metric_open_description, "12 Active")
        composeRule.onNodeWithContentDescription(description)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals(1, clicks)
    }

    @Test
    fun `non clickable pill stays decorative`() {
        composeRule.setContent {
            MetricPill(value = "3", label = "Formulas")
        }

        // Assert the pill really rendered, so the absence check below cannot pass on a blank screen.
        composeRule.onNodeWithText("3")
            .assertIsDisplayed()
            .assertHasNoClickAction()
        composeRule.onNodeWithText("Formulas").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.metric_open_description, "3 Formulas"),
        ).assertDoesNotExist()
    }
}

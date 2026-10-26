package com.factswallpaper.app

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartDestinationTest {

    @Test
    fun `new installs start at onboarding`() {
        assertThat(startDestinationFor(onboardingCompleted = false))
            .isEqualTo(StartDestination.ONBOARDING)
    }

    @Test
    fun `completed onboarding starts at the library`() {
        assertThat(startDestinationFor(onboardingCompleted = true))
            .isEqualTo(StartDestination.LIBRARY)
    }
}

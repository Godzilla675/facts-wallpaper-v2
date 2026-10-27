package com.factswallpaper.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CostEstimatorTest {

    @Test
    fun `text token estimate is ceil of chars over four with a floor of one`() {
        assertThat(CostEstimator.estimateTextTokens("")).isEqualTo(1)
        assertThat(CostEstimator.estimateTextTokens("abc")).isEqualTo(1)
        assertThat(CostEstimator.estimateTextTokens("abcde")).isEqualTo(2)
        assertThat(CostEstimator.estimateTextTokens("a".repeat(400))).isEqualTo(100)
    }

    @Test
    fun `pdf token estimate is 300 per page with a floor of 300`() {
        assertThat(CostEstimator.estimatePdfTokens(0)).isEqualTo(300)
        assertThat(CostEstimator.estimatePdfTokens(1)).isEqualTo(300)
        assertThat(CostEstimator.estimatePdfTokens(10)).isEqualTo(3_000)
    }

    @Test
    fun `vision token estimate is 1200 per image`() {
        assertThat(CostEstimator.estimateVisionTokens(0)).isEqualTo(0)
        assertThat(CostEstimator.estimateVisionTokens(1)).isEqualTo(1_200)
        assertThat(CostEstimator.estimateVisionTokens(5)).isEqualTo(6_000)
    }

    @Test
    fun `vision task estimate combines vision tokens and expected output`() {
        val profile = testProfile(inputPrice = 1.0, outputPrice = 2.0)
        val cost = CostEstimator.estimateVisionTask(
            profile = profile,
            imageCount = 5,
            expectedOutputTokens = 1_000,
        )
        // 5 * 1200 = 6000 input tokens ($0.006) + 1000 output tokens ($0.002) = $0.008
        assertThat(cost).isWithin(1e-9).of((6_000 * 1.0 + 1_000 * 2.0) / 1_000_000.0)
    }

    @Test
    fun `cost formula matches per million rates`() {
        val cost = CostEstimator.estimateCostUsd(
            inputTokens = 1_000_000,
            outputTokens = 500_000,
            inputUsdPerMillion = 3.0,
            outputUsdPerMillion = 15.0,
        )

        assertThat(cost).isWithin(1e-9).of(3.0 + 7.5)
    }

    @Test
    fun `profile overload uses the profile rates`() {
        val profile = testProfile(inputPrice = 0.15, outputPrice = 0.60)

        val cost = CostEstimator.estimateCostUsd(profile, inputTokens = 2_000_000, outputTokens = 1_000_000)

        assertThat(cost).isWithin(1e-9).of(0.30 + 0.60)
    }

    @Test
    fun `text task estimate combines input and expected output`() {
        val profile = testProfile(inputPrice = 1.0, outputPrice = 2.0)

        val cost = CostEstimator.estimateTextTask(
            profile = profile,
            inputText = "a".repeat(4_000),
            expectedOutputTokens = 1_000,
        )

        assertThat(cost).isWithin(1e-9).of((1_000 * 1.0 + 1_000 * 2.0) / 1_000_000.0)
    }

    @Test
    fun `zero priced local model costs nothing`() {
        val profile = testProfile(inputPrice = 0.0, outputPrice = 0.0)

        assertThat(CostEstimator.estimateCostUsd(profile, 100_000, 100_000)).isEqualTo(0.0)
    }
}

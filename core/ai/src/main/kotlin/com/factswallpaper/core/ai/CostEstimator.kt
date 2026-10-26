package com.factswallpaper.core.ai

import com.factswallpaper.core.model.ProviderProfile
import kotlin.math.ceil

/** Token/cost estimation formulas from spec §6.5 and F19. */
object CostEstimator {

    fun estimateTextTokens(text: String): Int =
        ceil(text.length / 4.0).toInt().coerceAtLeast(1)

    fun estimatePdfTokens(pageCount: Int): Int = (pageCount * 300).coerceAtLeast(300)

    fun estimateCostUsd(
        inputTokens: Int,
        outputTokens: Int,
        inputUsdPerMillion: Double,
        outputUsdPerMillion: Double,
    ): Double =
        ((inputTokens * inputUsdPerMillion) + (outputTokens * outputUsdPerMillion)) / 1_000_000.0

    fun estimateCostUsd(profile: ProviderProfile, inputTokens: Int, outputTokens: Int): Double =
        estimateCostUsd(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            inputUsdPerMillion = profile.inputUsdPerMillion,
            outputUsdPerMillion = profile.outputUsdPerMillion,
        )

    /** Pre-flight estimate for a text task: prompt characters + expected output. */
    fun estimateTextTask(
        profile: ProviderProfile,
        inputText: String,
        expectedOutputTokens: Int,
    ): Double = estimateCostUsd(
        profile = profile,
        inputTokens = estimateTextTokens(inputText),
        outputTokens = expectedOutputTokens.coerceAtLeast(1),
    )
}

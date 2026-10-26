package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.RoutingStrategy
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiRouterTest {

    private val cheap = testProfile(
        id = "cheap",
        name = "Cheap",
        inputPrice = 0.1,
        outputPrice = 0.2,
        quality = 1,
        speed = 3,
        context = 128_000,
    )
    private val flagship = testProfile(
        id = "flagship",
        name = "Flagship",
        inputPrice = 10.0,
        outputPrice = 30.0,
        quality = 3,
        speed = 1,
        context = 2_000_000,
    )
    private val balanced = testProfile(
        id = "balanced",
        name = "Balanced",
        inputPrice = 1.0,
        outputPrice = 2.0,
        quality = 2,
        speed = 2,
        context = 400_000,
    )
    private val profiles = listOf(cheap, flagship, balanced)
    private val keys = setOf("cheap", "flagship", "balanced")

    @Test
    fun `cheapest minimizes blended price`() {
        val selected = AiRouter.selectProfile(
            profiles = profiles,
            configuredProfileIdsWithKeys = keys,
            task = AiTask.FACTS,
            requireDirectPdf = false,
            strategy = RoutingStrategy.CHEAPEST,
        )

        assertThat(selected.id).isEqualTo("cheap")
    }

    @Test
    fun `highest quality prioritizes quality tier then schema and context`() {
        val selected = AiRouter.selectProfile(
            profiles = profiles,
            configuredProfileIdsWithKeys = keys,
            task = AiTask.FACTS,
            requireDirectPdf = false,
            strategy = RoutingStrategy.HIGHEST_QUALITY,
        )

        assertThat(selected.id).isEqualTo("flagship")
    }

    @Test
    fun `balanced scores capability against cost`() {
        val selected = AiRouter.selectProfile(
            profiles = profiles,
            configuredProfileIdsWithKeys = keys,
            task = AiTask.FACTS,
            requireDirectPdf = false,
            strategy = RoutingStrategy.BALANCED,
        )

        // balanced: 60+40+25+10-16 = 119; flagship: 90+20+25+50-200 = -15; cheap: 30+60+25+3.2-2.4 = 115.8
        assertThat(selected.id).isEqualTo("balanced")
    }

    @Test
    fun `manual strategy returns only the pinned profile`() {
        val ranked = AiRouter.rankProfiles(
            profiles = profiles,
            configuredProfileIdsWithKeys = keys,
            task = AiTask.OUTLINE,
            requireDirectPdf = false,
            strategy = RoutingStrategy.MANUAL,
            manualOverrideId = "flagship",
        )

        assertThat(ranked.map { it.id }).containsExactly("flagship")
    }

    @Test
    fun `manual strategy without a pin fails loudly`() {
        val error = runCatching {
            AiRouter.selectProfile(
                profiles = profiles,
                configuredProfileIdsWithKeys = keys,
                task = AiTask.OUTLINE,
                requireDirectPdf = false,
                strategy = RoutingStrategy.MANUAL,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
        assertThat((error as AiException).kind).isEqualTo(AiErrorKind.INVALID_REQUEST)
    }

    @Test
    fun `profiles without keys or disabled profiles are never eligible`() {
        val error = runCatching {
            AiRouter.selectProfile(
                profiles = profiles.map { it.copy(enabled = false) },
                configuredProfileIdsWithKeys = keys,
                task = AiTask.OUTLINE,
                requireDirectPdf = false,
                strategy = RoutingStrategy.BALANCED,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
    }

    @Test
    fun `direct PDF requires pdf capability`() {
        val noPdf = profiles.map { it.copy(supportsPdfInput = false) }
        val error = runCatching {
            AiRouter.selectProfile(
                profiles = noPdf,
                configuredProfileIdsWithKeys = keys,
                task = AiTask.OUTLINE,
                requireDirectPdf = true,
                strategy = RoutingStrategy.BALANCED,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
    }

    @Test
    fun `structured tasks require schema or json mode`() {
        val noStructured = profiles.map {
            it.copy(supportsJsonSchema = false, supportsJsonMode = false)
        }
        val error = runCatching {
            AiRouter.selectProfile(
                profiles = noStructured,
                configuredProfileIdsWithKeys = keys,
                task = AiTask.FACTS,
                requireDirectPdf = false,
                strategy = RoutingStrategy.BALANCED,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
    }

    @Test
    fun `profiles without json support are excluded from every structured task`() {
        val textOnly = testProfile(
            id = "text-only",
            supportsSchema = false,
            supportsJsonMode = false,
            inputPrice = 0.01,
            outputPrice = 0.01,
        )

        val error = runCatching {
            AiRouter.selectProfile(
                profiles = listOf(textOnly),
                configuredProfileIdsWithKeys = setOf("text-only"),
                task = AiTask.FORMULA_VERIFICATION,
                requireDirectPdf = false,
                strategy = RoutingStrategy.CHEAPEST,
            )
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
    }
}
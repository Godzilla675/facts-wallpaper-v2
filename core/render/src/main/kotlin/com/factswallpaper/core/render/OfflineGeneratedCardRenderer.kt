package com.factswallpaper.core.render

import com.factswallpaper.core.common.GeneratedCardRenderer
import com.factswallpaper.core.common.GeneratedCardRenderResult
import com.factswallpaper.core.model.Fact
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineGeneratedCardRenderer @Inject constructor(
    private val preRenderEngine: CardPreRenderEngine,
) : GeneratedCardRenderer {

    override suspend fun render(fact: Fact): GeneratedCardRenderResult =
        preRenderEngine.renderGeneratedFact(fact)
}

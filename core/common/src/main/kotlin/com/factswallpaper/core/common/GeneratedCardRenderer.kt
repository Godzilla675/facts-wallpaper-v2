package com.factswallpaper.core.common

import com.factswallpaper.core.model.Fact

data class GeneratedCardRenderResult(
    val rendered: Boolean,
    val missingStage: Boolean = false,
    val error: String? = null,
)

interface GeneratedCardRenderer {
    suspend fun render(fact: Fact): GeneratedCardRenderResult
}

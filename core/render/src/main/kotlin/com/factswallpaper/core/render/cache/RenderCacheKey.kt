package com.factswallpaper.core.render.cache

import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.render.RenderHash

/**
 * Deterministic content-addressed key from specification §7.5.
 *
 * All ten inputs are length-prefixed with `"${value.length}:$value"`, joined by `|`, and
 * hashed with SHA-256. The 64-character lowercase digest is the cache identity.
 */
object RenderCacheKey {

    const val RENDER_ENGINE_VERSION = "2"

    fun create(
        factId: String,
        templateId: String,
        templateVersion: Int,
        contentHashInput: String,
        width: Int,
        height: Int,
        targetSurface: String,
        themeKey: String,
        sourceReferenceMode: String,
        renderEngineVersion: String = RENDER_ENGINE_VERSION,
    ): String {
        val fields = listOf(
            factId,
            templateId,
            templateVersion.toString(),
            contentHashInput,
            width.toString(),
            height.toString(),
            targetSurface,
            themeKey,
            renderEngineVersion,
            sourceReferenceMode,
        )
        val joined = fields.joinToString(separator = "|") { value -> "${value.length}:$value" }
        return RenderHash.sha256Hex(joined)
    }

    fun forFact(
        fact: Fact,
        template: CardTemplate,
        width: Int,
        height: Int,
        targetSurface: WallpaperTarget,
        themeKey: String,
        sourceReferenceMode: SourceReferenceMode,
    ): String = create(
        factId = fact.id,
        templateId = template.id,
        templateVersion = template.version,
        contentHashInput = RenderHash.factContentHash(
            text = fact.text,
            latex = fact.latex,
            diagramAssetId = fact.diagramAssetId,
            sourceBookTitle = fact.sourceBookTitle,
            sourceLessonTitle = fact.sourceLessonTitle,
            sourcePage = fact.sourcePage,
        ),
        width = width,
        height = height,
        targetSurface = targetSurface.name,
        themeKey = themeKey,
        sourceReferenceMode = sourceReferenceMode.name,
    )
}

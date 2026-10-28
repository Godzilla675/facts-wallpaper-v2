package com.factswallpaper.core.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.database.dao.RenderCacheDao
import com.factswallpaper.core.database.entity.DiagramAssetEntity
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.render.cache.CachedCard
import com.factswallpaper.core.render.cache.CardCacheWrite
import com.factswallpaper.core.render.cache.CardRenderCache
import com.factswallpaper.core.render.cache.RenderCacheKey
import com.factswallpaper.core.render.card.CardBitmapRenderer
import com.factswallpaper.core.render.card.CardRenderOptions
import com.factswallpaper.core.render.diagram.DiagramRenderRequest
import com.factswallpaper.core.render.diagram.MarkupKind
import com.factswallpaper.core.render.diagram.OfflineWebViewDiagramRenderer
import com.factswallpaper.core.render.diagram.StageMarkup
import com.factswallpaper.core.render.template.CardTemplateSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class CardRenderRequest(
    val fact: Fact,
    val template: CardTemplate,
    val spec: CardTemplateSpec,
    val options: CardRenderOptions = CardRenderOptions(),
    /**
     * Foreground previews may render missing formulas/diagrams through the offline WebView.
     * Background workers must pass `false` and use the monospace pending fallback (§11.8).
     */
    val allowWebViewStage: Boolean = false,
)

data class CardRenderOutcome(
    val card: CachedCard,
    val missingStage: Boolean,
    val fromCache: Boolean,
)

/**
 * Orchestrates the stage asset lookup, optional foreground WebView render, card composition
 * and content-addressed PNG cache write. One implementation serves the preview screen, the
 * pre-render worker and (later) the wallpaper service.
 */
@Singleton
class CardRenderCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderer: CardBitmapRenderer,
    private val diagramRenderer: OfflineWebViewDiagramRenderer,
    private val renderCacheDao: RenderCacheDao,
    private val cardRenderCache: CardRenderCache,
    private val factRepository: FactRepository,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun render(request: CardRenderRequest): CardRenderOutcome? {
        var fact = request.fact
        val stage = StageMarkup.of(fact)
        var stageAsset: DiagramAssetEntity? = null
        var missingStage = false

        if (stage != null) {
            stageAsset = findExistingStage(stage, request.options.themeMode)
            if (stageAsset == null && request.allowWebViewStage) {
                val rendered = diagramRenderer.render(
                    DiagramRenderRequest(
                        kind = stage.kind,
                        source = stage.source,
                        foregroundColorHex = stageForeground(request.options.themeMode),
                        backgroundColorHex = "#00000000",
                        accentColorHex = accent(request.spec, request.options.themeMode),
                        themeKey = request.options.themeMode.name,
                        maxWidthPx = (request.options.width * 0.82f).toInt().coerceAtLeast(240),
                        maxHeightPx = (request.options.height * 0.34f).toInt().coerceAtLeast(240),
                    ),
                )
                if (rendered is com.factswallpaper.core.render.diagram.DiagramRenderResult.Success) {
                    stageAsset = rendered.asset
                    val updated = fact.copy(diagramAssetId = rendered.asset.id)
                    if (updated.diagramAssetId != fact.diagramAssetId) {
                        fact = updated
                        factRepository.saveFact(updated)
                    }
                }
            }
            missingStage = stageAsset == null
        }

        val effectiveMode = fact.sourceReferenceModeOverride ?: request.options.sourceReferenceMode
        val cacheKey = RenderCacheKey.forFact(
            fact = fact,
            template = request.template,
            width = request.options.width,
            height = request.options.height,
            targetSurface = request.options.targetSurface,
            themeKey = request.options.themeMode.name,
            sourceReferenceMode = effectiveMode,
        )
        cardRenderCache.find(cacheKey)?.let { cached ->
            return CardRenderOutcome(cached, missingStage = stageAsset == null && stage != null, fromCache = true)
        }

        val stageBitmap = stageAsset?.let { asset ->
            withContext(dispatchers.io) { decodeStage(asset) }
        }
        val rendered = try {
            renderer.render(
                fact = fact,
                template = request.template,
                spec = request.spec,
                options = request.options,
                stageBitmap = stageBitmap,
            )
        } finally {
            stageBitmap?.recycle()
        }

        val cached = cardRenderCache.store(
            request = CardCacheWrite(
                cacheKey = rendered.cacheKey,
                factId = fact.id,
                templateId = request.template.id,
                templateVersion = request.template.version,
                themeKey = request.options.themeMode.name,
                targetSurface = request.options.targetSurface.name,
                widthPx = rendered.bitmap.width,
                heightPx = rendered.bitmap.height,
                markAsLastKnownGood = !rendered.missingStage,
            ),
            bitmap = rendered.bitmap,
        )
        rendered.bitmap.recycle()
        return cached?.let {
            CardRenderOutcome(card = it, missingStage = rendered.missingStage, fromCache = false)
        }
    }

    private suspend fun findExistingStage(
        stage: StageMarkup,
        themeMode: ThemeMode,
    ): DiagramAssetEntity? = withContext(dispatchers.io) {
        val hash = RenderHash.diagramHash(stage.kind.name, stage.source, themeMode.name)
        renderCacheDao.getDiagramByHash(hash)?.takeIf { File(it.renderedPngPath).isFile }
    }

    private fun decodeStage(asset: DiagramAssetEntity): Bitmap? =
        File(asset.renderedPngPath)
            .takeIf { it.isFile }
            ?.let { file -> BitmapFactory.decodeFile(file.absolutePath) }

    private fun stageForeground(themeMode: ThemeMode): String = when (themeMode) {
        ThemeMode.LIGHT -> "#1A1D21"
        ThemeMode.SYSTEM, ThemeMode.DARK, ThemeMode.DYNAMIC, ThemeMode.AMOLED -> "#E8EEF5"
    }

    private fun accent(spec: CardTemplateSpec, themeMode: ThemeMode): String =
        if (themeMode == ThemeMode.LIGHT) spec.accentHexLight else spec.accentHexDark
}

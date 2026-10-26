package com.factswallpaper.core.render

import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.render.card.CardRenderOptions
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSeeder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class PreRenderReport(
    val poolSize: Int = 0,
    val requested: Int = 0,
    val rendered: Int = 0,
    val cacheHits: Int = 0,
    val failed: Int = 0,
    val missingStage: Int = 0,
    val templateId: String = "",
)

/**
 * Pre-renders the next N cards of the rotation queue (§F14) without ever touching a WebView.
 *
 * Cards whose formula/diagram has no cached PNG are still composited with the monospace
 * "Diagram pending" fallback; the coordinator refuses to mark those rows last-known-good.
 */
@Singleton
class CardPreRenderEngine @Inject constructor(
    private val rotationPoolRepository: RotationPoolRepository,
    private val wallpaperRepository: WallpaperRepository,
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val templateSeeder: CardTemplateSeeder,
    private val renderCoordinator: CardRenderCoordinator,
    private val renderSizeStore: RenderSizeStore,
) {

    suspend fun preRenderNext(limitOverride: Int? = null): PreRenderReport {
        templateSeeder.ensureSeeded()
        val preferences = settingsRepository.observePreferences().first()
        val queueSize = (limitOverride ?: preferences.preRenderQueueSize).coerceIn(1, 100)
        val wallpaperState = wallpaperRepository.getState()
        val pool = rotationPoolRepository.observePool().first()
        if (pool.isEmpty()) return PreRenderReport(templateId = wallpaperState.activeTemplateId)

        val template = resolveTemplate(wallpaperState.activeTemplateId)
        val spec = CardTemplateCodec.fromCardTemplate(template)
            ?: BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: return PreRenderReport(poolSize = pool.size, templateId = template.id)
        val size = renderSizeStore.current()
        val queue = nextQueue(pool, wallpaperState.currentIndex, queueSize)

        var rendered = 0
        var cacheHits = 0
        var failed = 0
        var missingStage = 0

        for (fact in queue) {
            val outcome = try {
                renderCoordinator.render(
                    CardRenderRequest(
                        fact = fact,
                        template = template,
                        spec = spec,
                        options = CardRenderOptions(
                            width = size.widthPx,
                            height = size.heightPx,
                            targetSurface = wallpaperState.target,
                            themeMode = wallpaperState.wallpaperThemeMode,
                            sourceReferenceMode = wallpaperState.sourceReferenceMode,
                        ),
                        allowWebViewStage = false,
                    ),
                )
            } catch (_: Throwable) {
                null
            }
            if (outcome == null) {
                failed++
            } else {
                if (outcome.fromCache) cacheHits++ else rendered++
                if (outcome.missingStage) missingStage++
            }
        }

        return PreRenderReport(
            poolSize = pool.size,
            requested = queue.size,
            rendered = rendered,
            cacheHits = cacheHits,
            failed = failed,
            missingStage = missingStage,
            templateId = template.id,
        )
    }

    private suspend fun resolveTemplate(activeTemplateId: String): CardTemplate {
        val templates = templateRepository.getTemplates()
        return templates.firstOrNull { it.id == activeTemplateId }
            ?: templates.firstOrNull { it.isDefault }
            ?: templates.firstOrNull()
            ?: BuiltInTemplates.byId(BuiltInTemplates.DEFAULT_TEMPLATE_ID)!!
    }

    /** Queue starts at the currently displayed fact, wrapping around the pool exactly once. */
    private fun nextQueue(pool: List<Fact>, startIndex: Int, limit: Int): List<Fact> {
        val size = pool.size
        if (size == 0) return emptyList()
        val start = ((startIndex % size) + size) % size
        val count = minOf(limit, size)
        return List(count) { offset -> pool[(start + offset) % size] }
    }
}

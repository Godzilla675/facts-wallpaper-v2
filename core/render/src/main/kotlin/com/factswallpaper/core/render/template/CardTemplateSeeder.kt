package com.factswallpaper.core.render.template

import com.factswallpaper.core.data.repository.TemplateRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Idempotently installs the eight built-in templates (§7.2).
 *
 * Only missing rows are inserted, so a user who edited a built-in's name/colors or picked a
 * different default keeps that state across launches. If the database contains templates but
 * no default (for example after a partial restore), Minimal Text becomes the default.
 */
@Singleton
class CardTemplateSeeder @Inject constructor(
    private val templateRepository: TemplateRepository,
) {

    private val mutex = Mutex()

    @Volatile
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            val existing = templateRepository.getTemplates()
            val existingIds = existing.mapTo(mutableSetOf()) { it.id }
            val missing = BuiltInTemplates.specs.filter { it.id !in existingIds }
            if (missing.isNotEmpty()) {
                val insertDefault = existing.isEmpty() && existing.none { it.isDefault }
                templateRepository.saveTemplates(
                    missing.map { spec ->
                        CardTemplateCodec.toCardTemplate(
                            spec = spec,
                            builtIn = true,
                            isDefault = insertDefault && spec.id == BuiltInTemplates.DEFAULT_TEMPLATE_ID,
                        )
                    },
                )
            }
            val hasDefault = existing.any { it.isDefault } ||
                (missing.any { it.id == BuiltInTemplates.DEFAULT_TEMPLATE_ID } && existing.isEmpty())
            if (!hasDefault) {
                templateRepository.setDefaultTemplate(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            }
            seeded = true
        }
    }
}

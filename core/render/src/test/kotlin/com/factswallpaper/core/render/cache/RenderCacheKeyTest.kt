package com.factswallpaper.core.render.cache

import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.TemplateEngineKind
import com.factswallpaper.core.model.WallpaperTarget
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RenderCacheKeyTest {

    @Test
    fun `identical inputs produce the same lowercase SHA-256 digest`() {
        assertThat(createKey()).isEqualTo(createKey())
        assertThat(createKey()).hasLength(64)
        assertThat(createKey()).matches("[0-9a-f]{64}")
    }

    @Test
    fun `every cache input participates in the key`() {
        val baseline = createKey()
        val variants = listOf(
            "factId" to createKey(factId = "fact-2"),
            "templateId" to createKey(templateId = "template-2"),
            "templateVersion" to createKey(templateVersion = 2),
            "contentHashInput" to createKey(contentHashInput = "content-hash-2"),
            "width" to createKey(width = 1440),
            "height" to createKey(height = 3120),
            "targetSurface" to createKey(targetSurface = "LOCK"),
            "themeKey" to createKey(themeKey = "LIGHT"),
            "sourceReferenceMode" to createKey(sourceReferenceMode = "FULL"),
            "renderEngineVersion" to createKey(renderEngineVersion = "3"),
        )

        variants.forEach { (label, key) ->
            assertThat("$label changed the cache key: $key").isNotEmpty()
        }
        variants.forEach { (_, key) ->
            assertThat(key).isNotEqualTo(baseline)
            assertThat(key).hasLength(64)
        }
        assertThat(variants.map { it.second } + baseline).containsNoDuplicates()
    }

    @Test
    fun `length prefixing prevents field-boundary ambiguity`() {
        val left = createKey(factId = "ab", templateId = "c")
        val right = createKey(factId = "a", templateId = "bc")

        assertThat(left).isNotEqualTo(right)
    }

    @Test
    fun `forFact folds fact content, template version and display options into the key`() {
        val fact = sampleFact()
        val template = sampleTemplate()

        val home = RenderCacheKey.forFact(
            fact = fact,
            template = template,
            width = 1080,
            height = 2400,
            targetSurface = WallpaperTarget.HOME,
            themeKey = "DARK",
            sourceReferenceMode = SourceReferenceMode.COMPACT,
        )
        val lock = RenderCacheKey.forFact(
            fact = fact,
            template = template,
            width = 1080,
            height = 2400,
            targetSurface = WallpaperTarget.LOCK,
            themeKey = "DARK",
            sourceReferenceMode = SourceReferenceMode.COMPACT,
        )
        val editedFact = RenderCacheKey.forFact(
            fact = fact.copy(text = "Different fact text"),
            template = template,
            width = 1080,
            height = 2400,
            targetSurface = WallpaperTarget.HOME,
            themeKey = "DARK",
            sourceReferenceMode = SourceReferenceMode.COMPACT,
        )
        val newVersion = RenderCacheKey.forFact(
            fact = fact,
            template = template.copy(version = 2),
            width = 1080,
            height = 2400,
            targetSurface = WallpaperTarget.HOME,
            themeKey = "DARK",
            sourceReferenceMode = SourceReferenceMode.COMPACT,
        )

        assertThat(listOf(home, lock, editedFact, newVersion))
            .containsNoDuplicates()
        assertThat(home).hasLength(64)
    }

    private fun createKey(
        factId: String = "fact-1",
        templateId: String = "template-1",
        templateVersion: Int = 1,
        contentHashInput: String = "content-hash",
        width: Int = 1080,
        height: Int = 2400,
        targetSurface: String = "HOME",
        themeKey: String = "DARK",
        sourceReferenceMode: String = "COMPACT",
        renderEngineVersion: String = RenderCacheKey.RENDER_ENGINE_VERSION,
    ): String = RenderCacheKey.create(
        factId = factId,
        templateId = templateId,
        templateVersion = templateVersion,
        contentHashInput = contentHashInput,
        width = width,
        height = height,
        targetSurface = targetSurface,
        themeKey = themeKey,
        sourceReferenceMode = sourceReferenceMode,
        renderEngineVersion = renderEngineVersion,
    )

    private fun sampleFact() = Fact(
        id = "fact-1",
        documentId = "doc-1",
        lessonId = null,
        text = "The mitochondria is the powerhouse of the cell.",
        latex = null,
        sourceBookTitle = "Biology",
        sourceLessonTitle = "Cells",
        sourcePage = 12,
        templateType = CardTemplateType.TEXT,
        createdAtEpochMs = 1L,
    )

    private fun sampleTemplate() = CardTemplate(
        id = "template-1",
        name = "Template",
        engineKind = TemplateEngineKind.NATIVE_JSON,
        schemaOrMarkup = "{}",
        version = 1,
        builtIn = true,
    )
}

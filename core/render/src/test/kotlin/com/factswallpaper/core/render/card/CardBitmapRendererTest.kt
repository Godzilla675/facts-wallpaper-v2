package com.factswallpaper.core.render.card

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.TemplateEntity
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.model.WallpaperTarget
import com.factswallpaper.core.render.cache.CardCacheWrite
import com.factswallpaper.core.render.cache.CardRenderCache
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardBitmapRendererTest {

    private lateinit var context: Context
    private lateinit var renderer: CardBitmapRenderer
    private lateinit var database: FactsDatabase
    private lateinit var cache: CardRenderCache

    private val dispatchers = FactsDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        renderer = CardBitmapRenderer(context, dispatchers)
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cache = CardRenderCache(context, database.renderCacheDao(), dispatchers)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `renders all eight built-in templates to non-empty PNG bitmaps and preserves AMOLED black ratio`() = runBlocking {
        val sampleFact = sampleFact()
        val options = CardRenderOptions(
            width = 360,
            height = 800,
            targetSurface = WallpaperTarget.LIVE,
            themeMode = ThemeMode.DARK,
            sourceReferenceMode = SourceReferenceMode.COMPACT,
        )

        val templates = BuiltInTemplates.all()
        assertThat(templates).hasSize(8)

        for (template in templates) {
            val spec = CardTemplateCodec.fromCardTemplate(template)
            assertThat(spec).isNotNull()

            val rendered = renderer.render(
                fact = sampleFact,
                template = template,
                spec = spec!!,
                options = options,
            )

            assertThat(rendered.bitmap.width).isEqualTo(options.width)
            assertThat(rendered.bitmap.height).isEqualTo(options.height)
            assertThat(rendered.cacheKey).hasLength(64)

            val pngBytes = rendered.toPngBytes()
            assertThat(pngBytes.size).isGreaterThan(512)
            assertThat(pngBytes[0]).isEqualTo(0x89.toByte())
            assertThat(pngBytes[1]).isEqualTo('P'.code.toByte())
            assertThat(pngBytes[2]).isEqualTo('N'.code.toByte())
            assertThat(pngBytes[3]).isEqualTo('G'.code.toByte())

            if (template.id == BuiltInTemplates.MIDNIGHT_AMOLED_ID) {
                val blackRatio = pureBlackPixelRatio(rendered.bitmap)
                assertThat(blackRatio).isGreaterThan(0.85)
            }
            rendered.bitmap.recycle()
        }
    }

    @Test
    fun `formula and diagram facts mark missingStage when no stage bitmap is supplied and clear it when supplied`() = runBlocking {
        val formulaFact = sampleFact().copy(
            latex = "\\int_0^\\infty e^{-x^2} dx = \\frac{\\sqrt{\\pi}}{2}",
            requiresLatex = true,
            templateType = CardTemplateType.FORMULA,
        )
        val template = BuiltInTemplates.byId(BuiltInTemplates.FORMULA_SPOTLIGHT_ID)!!
        val spec = CardTemplateCodec.fromCardTemplate(template)!!
        val options = CardRenderOptions(width = 360, height = 800)

        val withoutStage = renderer.render(
            fact = formulaFact,
            template = template,
            spec = spec,
            options = options,
            stageBitmap = null,
        )
        assertThat(withoutStage.missingStage).isTrue()
        withoutStage.bitmap.recycle()

        val stageBitmap = Bitmap.createBitmap(200, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val withStage = renderer.render(
            fact = formulaFact,
            template = template,
            spec = spec,
            options = options,
            stageBitmap = stageBitmap,
        )
        assertThat(withStage.missingStage).isFalse()
        stageBitmap.recycle()
        withStage.bitmap.recycle()
    }

    @Test
    fun `on-disk PNG cache stores, finds and tracks last-known-good cards`() = runBlocking {
        val fact = sampleFact()
        val template = BuiltInTemplates.byId(BuiltInTemplates.MINIMAL_TEXT_ID)!!
        val spec = CardTemplateCodec.fromCardTemplate(template)!!

        database.pdfDocumentDao().upsert(
            PdfDocumentEntity(
                id = fact.documentId,
                title = fact.sourceBookTitle,
                sourceUri = "content://doc",
                localFilePath = "/doc.pdf",
                fileHash = "hash",
                fileSizeBytes = 100L,
                pageCount = 10,
                pagesWithText = 10,
                processingRoute = "EXTRACT_TEXT",
                ocrEngine = null,
                extractionStatus = "FACTS_READY",
                includedInRotation = true,
                createdAtEpochMs = 1L,
            ),
        )
        database.factDao().upsert(
            FactItemEntity(
                id = fact.id,
                documentId = fact.documentId,
                lessonId = null,
                text = fact.text,
                sourceBookTitle = fact.sourceBookTitle,
                sourceLessonTitle = fact.sourceLessonTitle,
                sourcePage = fact.sourcePage,
                createdAtEpochMs = 1L,
            ),
        )
        database.templateDao().upsert(
            TemplateEntity(
                id = template.id,
                name = template.name,
                engineKind = template.engineKind.name,
                schemaOrMarkup = template.schemaOrMarkup,
                version = template.version,
                builtIn = true,
                isDefault = true,
                createdAtEpochMs = 1L,
            ),
        )

        val rendered = renderer.render(
            fact = fact,
            template = template,
            spec = spec,
            options = CardRenderOptions(width = 360, height = 800),
        )
        val stored = cache.store(
            request = CardCacheWrite(
                cacheKey = rendered.cacheKey,
                factId = fact.id,
                templateId = template.id,
                templateVersion = template.version,
                themeKey = ThemeMode.AMOLED.name,
                targetSurface = WallpaperTarget.LIVE.name,
                widthPx = rendered.bitmap.width,
                heightPx = rendered.bitmap.height,
                markAsLastKnownGood = true,
            ),
            bitmap = rendered.bitmap,
        )
        rendered.bitmap.recycle()

        assertThat(stored).isNotNull()
        assertThat(stored!!.pngFile.exists()).isTrue()
        assertThat(stored.pngFile.length()).isGreaterThan(512L)
        assertThat(cache.find(rendered.cacheKey)?.cacheKey).isEqualTo(rendered.cacheKey)
        assertThat(cache.getLastKnownGood()?.cacheKey).isEqualTo(rendered.cacheKey)
    }

    private fun pureBlackPixelRatio(bitmap: Bitmap): Double {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val blackCount = pixels.count { pixel ->
            Color.red(pixel) == 0 && Color.green(pixel) == 0 && Color.blue(pixel) == 0
        }
        return blackCount.toDouble() / pixels.size.toDouble()
    }

    private fun sampleFact(): Fact = Fact(
        id = "fact-1",
        documentId = "doc-1",
        lessonId = "lesson-1",
        text = "Noether's theorem states that every differentiable symmetry of the action of a physical system has a corresponding conservation law.",
        latex = null,
        requiresLatex = false,
        templateType = CardTemplateType.TEXT,
        diagramType = DiagramKind.NONE,
        diagramSource = null,
        diagramAssetId = null,
        verificationStatus = VerificationStatus.VERIFIED,
        sourceBookTitle = "Classical Mechanics",
        sourceLessonTitle = "Symmetries and Conservation",
        sourcePage = 42,
        sourceReferenceModeOverride = null,
        active = true,
        favorite = true,
        tags = listOf("physics", "symmetry"),
        createdAtEpochMs = 1_000L,
        updatedAtEpochMs = 1_000L,
    )
}
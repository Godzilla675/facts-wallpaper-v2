package com.factswallpaper.core.data.mapper

import com.factswallpaper.core.database.entity.DocumentSettingsEntity
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.ProviderProfileEntity
import com.factswallpaper.core.database.entity.WallpaperStateEntity
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperTarget
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MappersTest {

    @Test
    fun `parseTags trims lowercases deduplicates and drops blanks`() {
        assertThat(parseTags(" Physics, mechanics ,PHYSICS,, ")).containsExactly(
            "physics",
            "mechanics",
        ).inOrder()
        assertThat(parseTags("")).isEmpty()
        assertThat(parseTags(" , , ")).isEmpty()
    }

    @Test
    fun `toTagsCsv round trips through parseTags`() {
        val csv = listOf(" Physics ", "mechanics", "PHYSICS", "").toTagsCsv()
        assertThat(csv).isEqualTo("physics,mechanics")
        assertThat(parseTags(csv)).containsExactly("physics", "mechanics").inOrder()
    }

    @Test
    fun `fact entity round trips every stored field`() {
        val fact = Fact(
            id = "fact-1",
            documentId = "doc-1",
            lessonId = "lesson-1",
            text = "Energy is conserved",
            latex = "E = mc^2",
            requiresLatex = true,
            templateType = CardTemplateType.FORMULA,
            preferredTemplateId = "template-1",
            diagramType = DiagramKind.MERMAID,
            diagramSource = "graph TD; A-->B",
            diagramAssetId = "asset-1",
            sourceReferenceModeOverride = SourceReferenceMode.FULL,
            regenerationInstruction = "Shorter",
            previousText = "Old text",
            sourceBookTitle = "Physics",
            sourceLessonTitle = "Relativity",
            sourcePage = 42,
            tags = listOf("physics", "energy"),
            confidence = 0.87f,
            verificationStatus = VerificationStatus.VERIFIED,
            active = false,
            favorite = true,
            createdAtEpochMs = 1_000L,
            updatedAtEpochMs = 2_000L,
        )

        val entity = fact.toEntity()
        assertThat(entity.tagsCsv).isEqualTo("physics,energy")
        assertThat(entity.toDomain()).isEqualTo(fact)
    }

    @Test
    fun `fact mapping falls back for unknown stored enum names`() {
        val entity = entity(
            templateType = "SOMETHING_NEW",
            diagramType = "",
            verificationStatus = "MAYBE",
            sourceReferenceModeOverride = "NOPE",
            tagsCsv = "Math, MATH, geometry",
        )

        val fact = entity.toDomain()

        assertThat(fact.templateType).isEqualTo(CardTemplateType.TEXT)
        assertThat(fact.diagramType).isEqualTo(DiagramKind.NONE)
        assertThat(fact.verificationStatus).isEqualTo(VerificationStatus.UNVERIFIED)
        assertThat(fact.sourceReferenceModeOverride).isNull()
        assertThat(fact.tags).containsExactly("math", "geometry").inOrder()
    }

    @Test
    fun `document settings round trip typed enums`() {
        val settings = DocumentSettingsEntity(
            documentId = "doc-1",
            pageRangeEnd = 120,
            forceFullLocalOcr = true,
            ocrEngine = "PADDLE_PPOCR",
            factDensity = "DEEP",
            includeDiagrams = false,
        )

        val domain = settings.toDomain()
        assertThat(domain.factDensity).isEqualTo(FactDensity.DEEP)
        assertThat(domain.includeDiagrams).isFalse()
        assertThat(domain.toEntity()).isEqualTo(settings)
    }

    @Test
    fun `document mapper defaults unknown status and counts`() {
        val entity = PdfDocumentEntity(
            id = "doc-1",
            title = "Book",
            sourceUri = "content://book",
            localFilePath = "/book.pdf",
            fileHash = "hash",
            fileSizeBytes = 10L,
            pageCount = 4,
            pagesWithText = 4,
            processingRoute = "FUTURE_ROUTE",
            ocrEngine = null,
            extractionStatus = "NOT_A_STATUS",
            createdAtEpochMs = 1_000L,
        )

        val document = entity.toDomain(lessonCount = 3, factCount = 12)

        assertThat(document.processingRoute).isEqualTo(PdfProcessingRoute.EXTRACT_TEXT)
        assertThat(document.extractionStatus).isEqualTo(ExtractionStatus.IMPORTED)
        assertThat(document.lessonCount).isEqualTo(3)
        assertThat(document.factCount).isEqualTo(12)
        assertThat(document.isFullyTextExtractable).isTrue()
    }

    @Test
    fun `document derived counts cannot be negative`() {
        val document = Document(
            id = "doc-1",
            title = "Scanned",
            sourceUri = "uri",
            localFilePath = "/book.pdf",
            fileHash = "hash",
            fileSizeBytes = 10L,
            pageCount = 2,
            pagesWithText = 5,
            processingRoute = PdfProcessingRoute.LOCAL_OCR,
            extractionStatus = ExtractionStatus.OCR_DONE,
            createdAtEpochMs = 1L,
        )

        assertThat(document.scannedPageCount).isEqualTo(0)
        assertThat(document.isFullyTextExtractable).isTrue()
    }

    @Test
    fun `provider profile output budget round trips through room entity`() {
        val profile = ProviderProfile(
            id = "profile-1",
            name = "Reasoning provider",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            baseUrl = "https://api.example.com/v1",
            modelName = "reasoning-model",
            maxOutputTokens = 16_384,
        )

        assertThat(profile.toEntity().toDomain()).isEqualTo(profile)
        assertThat(
            ProviderProfileEntity(
                id = "profile-2",
                name = "Default profile",
                providerType = "OPENAI",
                baseUrl = "https://api.example.com/v1",
                modelName = "model",
                supportsPdfInput = false,
                supportsImageInput = false,
                supportsJsonMode = true,
                supportsJsonSchema = true,
                maxContextTokens = 128_000,
            ).toDomain().maxOutputTokens,
        ).isEqualTo(8_192)
    }

    @Test
    fun `wallpaper state round trips and defaults unknown enums`() {
        val entity = WallpaperStateEntity(
            currentFactId = "fact-1",
            currentCacheKey = "cache-1",
            currentIndex = 9,
            target = "LOCK",
            scheduleMode = "INTERVAL",
            intervalMinutes = 30,
            rotationOrder = "RANDOM",
            activeTemplateId = "template-1",
            wallpaperThemeMode = "LIGHT",
            sourceReferenceMode = "PAGE_ONLY",
            topClockSafeInsetFraction = 0.3f,
            onlyFavoritesInPool = true,
            lastRotatedAtEpochMs = 7_000L,
        )

        val state = entity.toDomain()
        assertThat(state.target).isEqualTo(WallpaperTarget.LOCK)
        assertThat(state.scheduleMode).isEqualTo(WallpaperScheduleMode.INTERVAL)
        assertThat(state.rotationOrder).isEqualTo(RotationOrder.RANDOM)
        assertThat(state.wallpaperThemeMode).isEqualTo(ThemeMode.LIGHT)
        assertThat(state.sourceReferenceMode).isEqualTo(SourceReferenceMode.PAGE_ONLY)
        assertThat(state.toEntity()).isEqualTo(entity)

        val fallback = entity.copy(
            target = "BOTH_SIDES",
            scheduleMode = "NEVER",
            rotationOrder = "SHUFFLE",
            wallpaperThemeMode = "NEON",
            sourceReferenceMode = "NONE",
        ).toDomain()
        assertThat(fallback.target).isEqualTo(WallpaperTarget.LIVE)
        assertThat(fallback.scheduleMode).isEqualTo(WallpaperScheduleMode.ON_UNLOCK)
        assertThat(fallback.rotationOrder).isEqualTo(RotationOrder.SEQUENTIAL)
        assertThat(fallback.wallpaperThemeMode).isEqualTo(ThemeMode.AMOLED)
        assertThat(fallback.sourceReferenceMode).isEqualTo(SourceReferenceMode.COMPACT)
    }

    private fun entity(
        templateType: String = "TEXT",
        diagramType: String = "NONE",
        verificationStatus: String = "UNVERIFIED",
        sourceReferenceModeOverride: String? = null,
        tagsCsv: String = "",
    ): FactItemEntity = FactItemEntity(
        id = "fact-1",
        documentId = "doc-1",
        lessonId = null,
        text = "Fact",
        templateType = templateType,
        diagramType = diagramType,
        sourceReferenceModeOverride = sourceReferenceModeOverride,
        sourceBookTitle = "Book",
        sourceLessonTitle = null,
        sourcePage = null,
        tagsCsv = tagsCsv,
        verificationStatus = verificationStatus,
        createdAtEpochMs = 1L,
    )
}

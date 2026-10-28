package com.factswallpaper.app.navigation

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class AppRouteTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun roundTrip(route: AppRoute): AppRoute =
        json.decodeFromString(AppRoute.serializer(), json.encodeToString(AppRoute.serializer(), route))

    @Test
    fun `all eleven top-level routes round trip`() {
        val routes: List<AppRoute> = listOf(
            AppRoute.Onboarding,
            AppRoute.Library,
            AppRoute.ImportPdf(),
            AppRoute.Outline("doc-1"),
            AppRoute.Facts(),
            AppRoute.Collections,
            AppRoute.WallpaperSettings,
            AppRoute.ProviderSettings,
            AppRoute.AdvancedSettings(),
            AppRoute.TemplateGallery,
            AppRoute.CardPreview(),
        )

        routes.forEach { route ->
            val decoded = json.decodeFromString(AppRoute.serializer(), json.encodeToString(route))
            assertThat(decoded).isEqualTo(route)
        }
        assertThat(routes.map { it::class }.distinct()).hasSize(11)
    }

    @Test
    fun `facts route preserves document lesson and tag context`() {
        val route = AppRoute.Facts(documentId = "doc-1", lessonId = "lesson-2", tag = "physics")

        assertThat(roundTrip(route)).isEqualTo(route)
    }

    @Test
    fun `optional arguments stay optional`() {
        val decoded = json.decodeFromString(
            AppRoute.serializer(),
            json.encodeToString(AppRoute.serializer(), AppRoute.Facts()),
        )

        assertThat(decoded).isEqualTo(AppRoute.Facts())
        assertThat(roundTrip(AppRoute.ImportPdf(sharedUri = "content://pdf/1")))
            .isEqualTo(AppRoute.ImportPdf(sharedUri = "content://pdf/1"))
        assertThat(roundTrip(AppRoute.CardPreview(factId = "fact-1", templateId = "template-1")))
            .isEqualTo(AppRoute.CardPreview(factId = "fact-1", templateId = "template-1"))
        assertThat(roundTrip(AppRoute.AdvancedSettings(section = "OCR")))
            .isEqualTo(AppRoute.AdvancedSettings(section = "OCR"))
    }

    @Test
    fun `routes are distinct per destination`() {
        assertThat(AppRoute.Outline("a")).isNotEqualTo(AppRoute.Outline("b"))
        assertThat(AppRoute.Facts(documentId = "a")).isNotEqualTo(AppRoute.Facts(documentId = "b"))
        assertThat(AppRoute.Facts(tag = "a")).isNotEqualTo(AppRoute.Facts(tag = "b"))
        assertThat(AppRoute.Library).isNotEqualTo(AppRoute.Collections)
    }
}

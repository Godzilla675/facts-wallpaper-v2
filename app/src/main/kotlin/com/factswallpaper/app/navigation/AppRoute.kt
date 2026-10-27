package com.factswallpaper.app.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation graph. Route names mirror the canonical destinations in
 * spec 11.11. Optional query values are nullable data-class properties so a
 * destination can also be opened without context (for example the Facts tab).
 */
@Serializable
sealed interface AppRoute {

    @Serializable
    data object Onboarding : AppRoute

    @Serializable
    data object Library : AppRoute

    @Serializable
    data class ImportPdf(val sharedUri: String? = null) : AppRoute

    @Serializable
    data class Outline(val documentId: String) : AppRoute

    @Serializable
    data class Facts(
        val documentId: String? = null,
        val lessonId: String? = null,
        val tag: String? = null,
        val status: String? = null,
    ) : AppRoute

    @Serializable
    data class Study(
        val collectionId: String? = null,
        val documentId: String? = null,
        val tag: String? = null,
        val dueOnly: Boolean = false,
    ) : AppRoute

    @Serializable
    data object Collections : AppRoute

    @Serializable
    data object WallpaperSettings : AppRoute

    @Serializable
    data object ProviderSettings : AppRoute

    @Serializable
    data object AdvancedSettings : AppRoute

    @Serializable
    data object TemplateGallery : AppRoute

    @Serializable
    data class CardPreview(
        val factId: String? = null,
        val templateId: String? = null,
    ) : AppRoute
}

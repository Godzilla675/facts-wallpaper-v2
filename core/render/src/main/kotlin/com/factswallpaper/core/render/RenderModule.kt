package com.factswallpaper.core.render

import com.factswallpaper.core.render.diagram.AndroidWebViewDiagramRenderer
import com.factswallpaper.core.render.diagram.OfflineWebViewDiagramRenderer
import com.factswallpaper.core.render.verify.FormulaAiVerifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Declares the optional Tier-2 verifier set. `:core:ai` (P3) contributes bindings via
 * `@IntoSet`; declaring it as a multibinding here keeps the render module self-contained
 * and injectable even before any AI provider exists.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RenderModule {

    @Multibinds
    abstract fun formulaAiVerifiers(): Set<FormulaAiVerifier>

    @Binds
    @Singleton
    abstract fun bindOfflineWebViewDiagramRenderer(
        renderer: AndroidWebViewDiagramRenderer,
    ): OfflineWebViewDiagramRenderer
}

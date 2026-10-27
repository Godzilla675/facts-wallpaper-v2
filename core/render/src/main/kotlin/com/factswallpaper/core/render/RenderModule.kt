package com.factswallpaper.core.render

import com.factswallpaper.core.common.CardPreRenderNotifier
import com.factswallpaper.core.common.GeneratedCardRenderer
import com.factswallpaper.core.render.diagram.AndroidWebViewDiagramRenderer
import com.factswallpaper.core.render.diagram.OfflineWebViewDiagramRenderer
import com.factswallpaper.core.render.verify.FormulaAiVerifier
import com.factswallpaper.core.render.verify.StructuredAiFormulaVerifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Declares render bindings, pre-render notifier, and Tier-2 AI formula/diagram verifiers.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RenderModule {

    @Multibinds
    abstract fun formulaAiVerifiers(): Set<FormulaAiVerifier>

    @Binds
    @IntoSet
    abstract fun bindStructuredAiFormulaVerifier(
        verifier: StructuredAiFormulaVerifier,
    ): FormulaAiVerifier

    @Binds
    @Singleton
    abstract fun bindOfflineWebViewDiagramRenderer(
        renderer: AndroidWebViewDiagramRenderer,
    ): OfflineWebViewDiagramRenderer

    @Binds
    @Singleton
    abstract fun bindCardPreRenderNotifier(
        scheduler: CardPreRenderScheduler,
    ): CardPreRenderNotifier

    @Binds
    @Singleton
    abstract fun bindGeneratedCardRenderer(
        renderer: OfflineGeneratedCardRenderer,
    ): GeneratedCardRenderer
}

package com.factswallpaper.core.data.ai

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class GenerationModule {

    @Binds
    abstract fun bindGenerationAiClient(
        client: StructuredGenerationAiClient,
    ): GenerationAiClient
}

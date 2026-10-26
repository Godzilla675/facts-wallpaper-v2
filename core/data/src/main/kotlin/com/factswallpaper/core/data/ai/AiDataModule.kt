package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.AiLogRecorder
import com.factswallpaper.core.ai.AiPreferencesSource
import com.factswallpaper.core.ai.AiProfileProvider
import com.factswallpaper.core.ai.AiUsageRecorder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiDataModule {

    @Binds
    @Singleton
    abstract fun bindAiProfileProvider(impl: RepositoryAiProfileProvider): AiProfileProvider

    @Binds
    @Singleton
    abstract fun bindAiPreferencesSource(impl: SettingsAiPreferencesSource): AiPreferencesSource

    @Binds
    @Singleton
    abstract fun bindAiUsageRecorder(impl: RepositoryAiTelemetry): AiUsageRecorder

    @Binds
    @Singleton
    abstract fun bindAiLogRecorder(impl: RepositoryAiTelemetry): AiLogRecorder
}

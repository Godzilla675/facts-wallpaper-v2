package com.factswallpaper.core.ai

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @Singleton
    abstract fun bindRetrySleeper(impl: DelayRetrySleeper): RetrySleeper

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindProviderDiagnosticsService(impl: DefaultProviderDiagnosticsService): ProviderDiagnosticsService

    @Binds
    @Singleton
    abstract fun bindStructuredAiService(impl: DefaultStructuredAiService): StructuredAiService

    companion object {
        @Provides
        @Singleton
        fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 180_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis = 120_000
            }
            engine {
                config {
                    connectTimeout(30, TimeUnit.SECONDS)
                    readTimeout(120, TimeUnit.SECONDS)
                    writeTimeout(120, TimeUnit.SECONDS)
                }
            }
        }

        @Provides
        @Singleton
        fun provideSchemaValidator(): SchemaValidator = SchemaValidator()
    }
}

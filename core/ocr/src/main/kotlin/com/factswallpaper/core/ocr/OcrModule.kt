package com.factswallpaper.core.ocr

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Dedicated HTTP client for checksum-verified OCR language pack downloads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OcrHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrBindingsModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngineFactory(impl: DefaultOcrEngineFactory): OcrEngineFactory

    @Binds
    @Singleton
    abstract fun bindOcrModelManager(impl: DefaultOcrModelManager): OcrModelManager

    @Binds
    @Singleton
    abstract fun bindOcrTextFileCompiler(impl: DefaultOcrTextFileCompiler): OcrTextFileCompiler

    @Binds
    @Singleton
    abstract fun bindOcrPipeline(impl: DefaultOcrPipeline): OcrPipeline
}

@Module
@InstallIn(SingletonComponent::class)
object OcrNetworkModule {

    @Provides
    @Singleton
    @OcrHttpClient
    fun provideOcrHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .build()
}

package com.factswallpaper.core.pdf

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Dedicated client for HTTPS PDF downloads. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PdfHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPdfUrlDownloader(impl: HttpPdfUrlDownloader): PdfUrlDownloader

    @Binds
    @Singleton
    abstract fun bindPdfMetadataReader(impl: PdfBoxMetadataReader): PdfMetadataReader

    @Binds
    @Singleton
    abstract fun bindPdfTextExtractor(impl: PdfBoxTextExtractor): PdfTextExtractor

    @Binds
    @Singleton
    abstract fun bindPdfImportRepository(impl: DefaultPdfImportRepository): PdfImportRepository

    @Binds
    @Singleton
    abstract fun bindOcrPageRenderer(impl: PdfRendererPageRenderer): com.factswallpaper.core.ocr.OcrPageRenderer
}

@Module
@InstallIn(SingletonComponent::class)
object PdfNetworkModule {

    @Provides
    @Singleton
    @PdfHttpClient
    fun providePdfHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .retryOnConnectionFailure(true)
        .build()
}

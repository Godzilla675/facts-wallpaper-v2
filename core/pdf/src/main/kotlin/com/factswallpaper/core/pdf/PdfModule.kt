package com.factswallpaper.core.pdf

import com.factswallpaper.core.common.VisionPageRenderer
import com.factswallpaper.core.data.pipeline.PipelineNotifier
import com.factswallpaper.core.data.pipeline.PipelineScheduler
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
    abstract fun bindPdfRelinkService(impl: DefaultPdfRelinkService): PdfRelinkService

    @Binds
    @Singleton
    abstract fun bindOcrPageRenderer(impl: PdfRendererPageRenderer): com.factswallpaper.core.ocr.OcrPageRenderer

    @Binds
    @Singleton
    abstract fun bindVisionPageRenderer(impl: PdfPageImageCache): VisionPageRenderer

    @Binds
    @Singleton
    abstract fun bindPipelineNotifier(impl: PdfNotifications): PipelineNotifier

    @Binds
    @Singleton
    abstract fun bindPipelineScheduler(impl: DocumentProcessingScheduler): PipelineScheduler
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

package com.factswallpaper.core.database

import android.content.Context
import androidx.room.Room
import com.factswallpaper.core.database.dao.CollectionDao
import com.factswallpaper.core.database.dao.DiagnosticsAndUsageDao
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.database.dao.LessonDao
import com.factswallpaper.core.database.dao.PdfDocumentDao
import com.factswallpaper.core.database.dao.ProviderProfileDao
import com.factswallpaper.core.database.dao.RenderCacheDao
import com.factswallpaper.core.database.dao.TemplateDao
import com.factswallpaper.core.database.dao.WallpaperStateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFactsDatabase(@ApplicationContext context: Context): FactsDatabase =
        Room.databaseBuilder(context, FactsDatabase::class.java, FactsDatabase.NAME)
            .addMigrations(*FactsDatabase.MIGRATIONS)
            .build()

    @Provides
    fun providePdfDocumentDao(database: FactsDatabase): PdfDocumentDao = database.pdfDocumentDao()

    @Provides
    fun provideLessonDao(database: FactsDatabase): LessonDao = database.lessonDao()

    @Provides
    fun provideFactDao(database: FactsDatabase): FactDao = database.factDao()

    @Provides
    fun provideCollectionDao(database: FactsDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideTemplateDao(database: FactsDatabase): TemplateDao = database.templateDao()

    @Provides
    fun provideRenderCacheDao(database: FactsDatabase): RenderCacheDao = database.renderCacheDao()

    @Provides
    fun provideProviderProfileDao(database: FactsDatabase): ProviderProfileDao = database.providerProfileDao()

    @Provides
    fun provideWallpaperStateDao(database: FactsDatabase): WallpaperStateDao = database.wallpaperStateDao()

    @Provides
    fun provideDiagnosticsAndUsageDao(database: FactsDatabase): DiagnosticsAndUsageDao =
        database.diagnosticsAndUsageDao()
}

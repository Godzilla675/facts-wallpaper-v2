package com.factswallpaper.core.data

import com.factswallpaper.core.data.repository.CollectionRepository
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.DiskStorageRepository
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.LessonRepository
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.data.repository.RoomCollectionRepository
import com.factswallpaper.core.data.repository.RoomDiagnosticsRepository
import com.factswallpaper.core.data.repository.RoomDocumentRepository
import com.factswallpaper.core.data.repository.RoomFactRepository
import com.factswallpaper.core.data.repository.RoomLessonRepository
import com.factswallpaper.core.data.repository.RoomProviderProfileRepository
import com.factswallpaper.core.data.repository.RoomRotationPoolRepository
import com.factswallpaper.core.data.repository.RoomTemplateRepository
import com.factswallpaper.core.data.repository.RoomWallpaperRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.StorageRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: RoomDocumentRepository): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindLessonRepository(impl: RoomLessonRepository): LessonRepository

    @Binds
    @Singleton
    abstract fun bindFactRepository(impl: RoomFactRepository): FactRepository

    @Binds
    @Singleton
    abstract fun bindCollectionRepository(impl: RoomCollectionRepository): CollectionRepository

    @Binds
    @Singleton
    abstract fun bindTemplateRepository(impl: RoomTemplateRepository): TemplateRepository

    @Binds
    @Singleton
    abstract fun bindProviderProfileRepository(impl: RoomProviderProfileRepository): ProviderProfileRepository

    @Binds
    @Singleton
    abstract fun bindWallpaperRepository(impl: RoomWallpaperRepository): WallpaperRepository

    @Binds
    @Singleton
    abstract fun bindRotationPoolRepository(impl: RoomRotationPoolRepository): RotationPoolRepository

    @Binds
    @Singleton
    abstract fun bindDiagnosticsRepository(impl: RoomDiagnosticsRepository): DiagnosticsRepository

    @Binds
    @Singleton
    abstract fun bindStorageRepository(impl: DiskStorageRepository): StorageRepository
}

package com.factswallpaper.core.backup

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {

    @Binds
    @Singleton
    abstract fun bindBackupManager(impl: BackupManagerImpl): BackupManager

    @Binds
    @Singleton
    abstract fun bindBackupDestinationStore(
        impl: SharedPreferencesBackupDestinationStore,
    ): BackupDestinationStore
}

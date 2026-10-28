package com.factswallpaper.core.database

import android.content.Context
import android.util.Log
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
import com.factswallpaper.core.security.DatabaseEncryptionStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideFactsDatabase(
        @ApplicationContext context: Context,
        encryptionStore: DatabaseEncryptionStore,
    ): FactsDatabase {
        val requestedEncryption = encryptionStore.isEncryptionRequested()
        val databaseFile = context.getDatabasePath(FactsDatabase.NAME)
        val needsPassphrase = requestedEncryption ||
            DatabaseEncryptionMigrator.isEncrypted(databaseFile)
        val passphrase = if (needsPassphrase) encryptionStore.passphrase() else ByteArray(0)
        val encrypted = DatabaseEncryptionMigrator.reconcile(
            context = context,
            targetEncrypted = requestedEncryption,
            passphrase = passphrase,
        )
        if (encrypted != requestedEncryption) {
            // The migration could not be applied; keep the switch honest about what is
            // actually on disk instead of retrying (and failing) on every launch.
            encryptionStore.setEncryptionRequested(encrypted)
            Log.w(
                DatabaseEncryptionMigrator.TAG,
                "Database stays ${if (encrypted) "encrypted" else "plaintext"}: " +
                    DatabaseEncryptionMigrator.lastError,
            )
        }
        val builder = Room.databaseBuilder(context, FactsDatabase::class.java, FactsDatabase.NAME)
            .addMigrations(*FactsDatabase.MIGRATIONS)
        if (encrypted) {
            builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
        }
        return builder.build().also { encryptionStore.setAppliedEncryption(encrypted) }
    }

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

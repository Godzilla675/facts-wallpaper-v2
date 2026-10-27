package com.factswallpaper.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.factswallpaper.core.database.dao.CollectionDao
import com.factswallpaper.core.database.dao.DiagnosticsAndUsageDao
import com.factswallpaper.core.database.dao.FactDao
import com.factswallpaper.core.database.dao.LessonDao
import com.factswallpaper.core.database.dao.PdfDocumentDao
import com.factswallpaper.core.database.dao.ProviderProfileDao
import com.factswallpaper.core.database.dao.RenderCacheDao
import com.factswallpaper.core.database.dao.TemplateDao
import com.factswallpaper.core.database.dao.WallpaperStateDao
import com.factswallpaper.core.database.entity.AiRequestLogEntity
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.CollectionFactCrossRef
import com.factswallpaper.core.database.entity.DiagramAssetEntity
import com.factswallpaper.core.database.entity.DocumentChunkEntity
import com.factswallpaper.core.database.entity.DocumentSettingsEntity
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.FactTagEntity
import com.factswallpaper.core.database.entity.FolderFactCrossRef
import com.factswallpaper.core.database.entity.LessonEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.ProviderProfileEntity
import com.factswallpaper.core.database.entity.RenderCacheEntity
import com.factswallpaper.core.database.entity.TemplateEntity
import com.factswallpaper.core.database.entity.UsageLedgerEntity
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.factswallpaper.core.database.entity.WallpaperStateEntity

@Database(
    entities = [
        PdfDocumentEntity::class,
        DocumentSettingsEntity::class,
        DocumentChunkEntity::class,
        LessonEntity::class,
        FactItemEntity::class,
        FactTagEntity::class,
        CollectionEntity::class,
        CollectionFactCrossRef::class,
        UserFolderEntity::class,
        FolderFactCrossRef::class,
        TemplateEntity::class,
        DiagramAssetEntity::class,
        RenderCacheEntity::class,
        ProviderProfileEntity::class,
        WallpaperStateEntity::class,
        UsageLedgerEntity::class,
        AiRequestLogEntity::class,
    ],
    version = FactsDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(FactsConverters::class)
abstract class FactsDatabase : RoomDatabase() {

    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun lessonDao(): LessonDao
    abstract fun factDao(): FactDao
    abstract fun collectionDao(): CollectionDao
    abstract fun templateDao(): TemplateDao
    abstract fun renderCacheDao(): RenderCacheDao
    abstract fun providerProfileDao(): ProviderProfileDao
    abstract fun wallpaperStateDao(): WallpaperStateDao
    abstract fun diagnosticsAndUsageDao(): DiagnosticsAndUsageDao

    open fun compact() {
        openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL);")
        openHelper.writableDatabase.execSQL("VACUUM;")
    }

    companion object {
        const val NAME = "facts_wallpaper.db"
        const val VERSION = 4

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE provider_profiles ADD COLUMN maxOutputTokens INTEGER NOT NULL DEFAULT 8192",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE provider_profiles ADD COLUMN temperature REAL")
                db.execSQL("ALTER TABLE provider_profiles ADD COLUMN topP REAL")
                db.execSQL("ALTER TABLE provider_profiles ADD COLUMN reasoningEffort TEXT")
                db.execSQL(
                    "ALTER TABLE provider_profiles ADD COLUMN rawRequestJson TEXT NOT NULL DEFAULT '{}'",
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE lessons ADD COLUMN chapterTitle TEXT")
            }
        }

        /**
         * Registered migrations. Version 1 is the initial schema; every future
         * schema change must append a Migration here and bump [VERSION]. There is
         * deliberately no destructive fallback: local study data is precious.
         */
        val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
    }
}

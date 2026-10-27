package com.factswallpaper.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FactsDatabaseMigrationTest {

    @Test
    fun `version one profiles receive default output budget`() {
        val migration = FactsDatabase.MIGRATIONS.singleOrNull {
            it.startVersion == 1 && it.endVersion == 2
        } ?: throw AssertionError("Expected a version 1 to 2 migration.")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE provider_profiles (id TEXT NOT NULL PRIMARY KEY)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO provider_profiles (id) VALUES ('profile-1')")
            migration.migrate(database)
            database.query(
                "SELECT maxOutputTokens FROM provider_profiles WHERE id = 'profile-1'",
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(8192)
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun `version two profiles receive advanced request settings defaults`() {
        val migration = FactsDatabase.MIGRATIONS.singleOrNull {
            it.startVersion == 2 && it.endVersion == 3
        }
        assertThat(migration?.endVersion).isEqualTo(3)
        if (migration == null) return

        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(2) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE provider_profiles (
                                    id TEXT NOT NULL PRIMARY KEY,
                                    customHeadersJson TEXT NOT NULL DEFAULT '{}',
                                    maxOutputTokens INTEGER NOT NULL DEFAULT 8192
                                )
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL(
                "INSERT INTO provider_profiles (id, customHeadersJson) VALUES ('profile-2', '{\"x\":\"y\"}')",
            )
            migration.migrate(database)
            database.query(
                """
                SELECT temperature, topP, reasoningEffort, rawRequestJson, customHeadersJson
                FROM provider_profiles
                WHERE id = 'profile-2'
                """.trimIndent(),
            ).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.isNull(0)).isTrue()
                assertThat(cursor.isNull(1)).isTrue()
                assertThat(cursor.isNull(2)).isTrue()
                assertThat(cursor.getString(3)).isEqualTo("{}")
                assertThat(cursor.getString(4)).isEqualTo("{\"x\":\"y\"}")
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun `version three lessons receive an optional chapter title`() {
        val migration = FactsDatabase.MIGRATIONS.singleOrNull {
            it.startVersion == 3 && it.endVersion == 4
        } ?: throw AssertionError("Expected a version 3 to 4 migration.")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(3) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                    CREATE TABLE lessons (
                                        id TEXT NOT NULL PRIMARY KEY,
                                        documentId TEXT NOT NULL,
                                        title TEXT NOT NULL,
                                        summary TEXT NOT NULL,
                                        startPage INTEGER NOT NULL,
                                        endPage INTEGER NOT NULL,
                                        selected INTEGER NOT NULL,
                                        sortOrder INTEGER NOT NULL,
                                        factCount INTEGER NOT NULL
                                    )
                                    """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL(
                """
                    INSERT INTO lessons (
                        id, documentId, title, summary, startPage, endPage, selected, sortOrder, factCount
                    ) VALUES ('lesson-1', 'doc-1', 'Lesson', 'Summary', 1, 2, 1, 1, 0)
                    """.trimIndent(),
            )
            migration.migrate(database)
            database.query("SELECT chapterTitle FROM lessons WHERE id = 'lesson-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.isNull(0)).isTrue()
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun versionFourToFiveMigrationAddsNewColumns() {
        val migration = FactsDatabase.MIGRATIONS.singleOrNull {
            it.startVersion == 4 && it.endVersion == 5
        } ?: throw AssertionError("Expected a version 4 to 5 migration.")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(4) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE pdf_documents (id TEXT NOT NULL PRIMARY KEY)")
                            db.execSQL("CREATE TABLE document_settings (documentId TEXT NOT NULL PRIMARY KEY)")
                            db.execSQL("CREATE TABLE facts (id TEXT NOT NULL PRIMARY KEY)")
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        try {
            val database = helper.writableDatabase
            database.execSQL("INSERT INTO pdf_documents (id) VALUES ('doc-1')")
            database.execSQL("INSERT INTO document_settings (documentId) VALUES ('doc-1')")
            database.execSQL("INSERT INTO facts (id) VALUES ('fact-1')")
            migration.migrate(database)

            database.query("SELECT pipelineProgress FROM pdf_documents WHERE id = 'doc-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getFloat(0)).isEqualTo(0f)
            }
            database.query("SELECT autoProcessMode, documentInputMode FROM document_settings WHERE documentId = 'doc-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("OUTLINE_ONLY")
                assertThat(cursor.getString(1)).isEqualTo("AUTO")
            }
            database.query("SELECT reviewGotItCount, reviewAgainCount FROM facts WHERE id = 'fact-1'").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(0)
                assertThat(cursor.getInt(1)).isEqualTo(0)
            }
        } finally {
            helper.close()
        }
    }
}

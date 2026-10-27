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
}

package com.factswallpaper.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real Room migrations against the device's SQLite engine and
 * validates each result against the exported schema identity hash.
 *
 * The Robolectric [FactsDatabaseMigrationTest] covers column-level defaults;
 * this suite proves the same migrations execute on Android, not just on the
 * JVM's bundled SQLite.
 */
@RunWith(AndroidJUnit4::class)
class FactsDatabaseMigrationInstrumentedTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun newHelper() = MigrationTestHelper(
        instrumentation,
        FactsDatabase::class.java,
        listOf<AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun everySchemaVersionMigratesToCurrentOnDevice() {
        for (version in 1 until FactsDatabase.VERSION) {
            val databaseName = "migration-$version-${System.nanoTime()}.db"
            val helper = newHelper()
            helper.createDatabase(databaseName, version).close()

            helper.runMigrationsAndValidate(
                databaseName,
                FactsDatabase.VERSION,
                true,
                *FactsDatabase.MIGRATIONS,
            ).close()

            instrumentation.targetContext.deleteDatabase(databaseName)
        }
    }

    @Test
    fun migrationFiveToSixPreservesFactsAndDefaultsTheReviewSchedule() {
        val databaseName = "migration-data-${System.nanoTime()}.db"
        val helper = newHelper()
        helper.createDatabase(databaseName, 5).apply {
            execSQL(
                """
                INSERT INTO pdf_documents (
                    id, title, sourceUri, localFilePath, fileHash, fileSizeBytes,
                    pageCount, pagesWithText, processingRoute, ocrLanguageCode,
                    ocrLastStartedPage, ocrLastCompletedPage, extractionStatus,
                    includedInRotation, createdAtEpochMs, updatedAtEpochMs,
                    pipelineProgress
                ) VALUES (
                    'doc-1', 'Book', 'content://book', '/tmp/book.pdf', 'hash-1', 100,
                    10, 10, 'EXTRACT_TEXT', 'eng', 0, 0, 'COMPLETED',
                    1, 1000, 1000, 1.0
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO facts (
                    id, documentId, text, requiresLatex, templateType, diagramType,
                    sourceBookTitle, tagsCsv, confidence, verificationStatus, active,
                    favorite, createdAtEpochMs, updatedAtEpochMs, reviewGotItCount,
                    reviewAgainCount
                ) VALUES (
                    'fact-1', 'doc-1', 'Persisted fact', 0, 'TEXT', 'NONE',
                    'Book', '', 1.0, 'UNVERIFIED', 1, 0, 1000, 1000, 2, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            FactsDatabase.MIGRATION_5_6,
        )
        migrated.query(
            "SELECT text, reviewGotItCount, nextReviewDueEpochMs FROM facts WHERE id = 'fact-1'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("Persisted fact")
            assertThat(cursor.getInt(1)).isEqualTo(2)
            assertThat(cursor.isNull(2)).isTrue()
        }
        migrated.close()
        instrumentation.targetContext.deleteDatabase(databaseName)
    }
}

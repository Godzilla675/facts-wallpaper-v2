package com.factswallpaper.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.TemplateEntity
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.cache.CardRenderCache
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the real [BackupManagerImpl] against a real Room database and real
 * zip4j archives. Every test would fail if the production backup logic changed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var database: FactsDatabase
    private lateinit var manager: BackupManagerImpl

    private val dispatchers = FactsDispatchers(
        io = Dispatchers.Unconfined,
        default = Dispatchers.Unconfined,
        main = Dispatchers.Unconfined,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        database = Room.inMemoryDatabaseBuilder(context, FactsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        manager = BackupManagerImpl(
            context = context,
            database = database,
            cardRenderCache = CardRenderCache(context, database.renderCacheDao(), dispatchers),
            preRenderScheduler = CardPreRenderScheduler(context),
            dispatchers = dispatchers,
        )
    }

    @After
    fun tearDown() {
        database.close()
        File(context.filesDir, "pdfs").deleteRecursively()
    }

    @Test
    fun `a passphrase shorter than the documented minimum is rejected`() = runBlocking<Unit> {
        seed()
        val destination = File(tempFolder.root, "short-passphrase.fwbk")

        val result = manager.createBackup(destination, "1234567", BackupOptions())

        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
        val error = (result as AppResult.Failure).error
        assertThat(error).isInstanceOf(AppError.InvalidOperation::class.java)
        assertThat(error.message).contains(BackupManagerImpl.MIN_PASSPHRASE_LENGTH.toString())
        assertThat(destination.exists()).isFalse()
    }

    @Test
    fun `creating a backup writes an encrypted archive with a manifest and custom tables`() =
        runBlocking<Unit> {
            seed()
            val destination = File(tempFolder.root, "round-trip.fwbk")
            val passphrase = "StrongPassphrase123"

            val created = manager.createBackup(destination, passphrase, BackupOptions())

            assertThat(created).isInstanceOf(AppResult.Success::class.java)
            val manifest = requireNotNull(created.getOrNull())
            assertThat(manifest.documentCount).isEqualTo(1)
            assertThat(manifest.factCount).isEqualTo(1)
            assertThat(manifest.schemaVersion).isEqualTo(FactsDatabase.VERSION)
            assertThat(destination.exists()).isTrue()

            val zip = ZipFile(destination, passphrase.toCharArray())
            assertThat(zip.isValidZipFile).isTrue()
            assertThat(zip.isEncrypted).isTrue()
            val names = zip.fileHeaders.map { it.fileName }
            assertThat(names).containsAtLeast(
                "manifest.json",
                "tables/pdf_documents.json",
                "tables/facts.json",
                "tables/templates.json",
                "tables/collections.json",
            )

            // Built-in templates are code-owned and must never travel in a backup.
            val templatesJson = readEntry(zip, "tables/templates.json")
            assertThat(templatesJson).contains("template-custom")
            assertThat(templatesJson).doesNotContain("builtin_minimal_text")

            val factsJson = readEntry(zip, "tables/facts.json")
            assertThat(factsJson).contains("fact-1")
            assertThat(factsJson).contains("Entropy never decreases in an isolated system.")

            val validated = manager.validateBackup(destination, passphrase)
            assertThat(validated).isInstanceOf(AppResult.Success::class.java)
            assertThat(validated.getOrNull()).isEqualTo(manifest)
        }

    @Test
    fun `restore rebuilds wiped rows from the archive`() = runBlocking<Unit> {
        seed()
        val destination = File(tempFolder.root, "restore.fwbk")
        val passphrase = "StrongPassphrase123"
        manager.createBackup(destination, passphrase, BackupOptions())

        database.pdfDocumentDao().deleteById("doc-1")
        database.collectionDao().deleteCollection("col-1")
        assertThat(database.factDao().getById("fact-1")).isNull()
        assertThat(database.pdfDocumentDao().getById("doc-1")).isNull()

        val restored = manager.restoreBackup(destination, passphrase)

        assertThat(restored).isInstanceOf(AppResult.Success::class.java)
        val document = database.pdfDocumentDao().getById("doc-1")
        assertThat(document).isNotNull()
        assertThat(document!!.title).isEqualTo("Physics Notes")
        val fact = database.factDao().getById("fact-1")
        assertThat(fact).isNotNull()
        assertThat(fact!!.text).isEqualTo("Entropy never decreases in an isolated system.")
        assertThat(database.collectionDao().getCollection("col-1")).isNotNull()
        assertThat(database.templateDao().getById("template-custom")).isNotNull()
    }

    @Test
    fun `original pdfs are only archived and restored when requested`() = runBlocking<Unit> {
        seed()
        val pdfDirectory = File(context.filesDir, "pdfs").apply { mkdirs() }
        val original = File(pdfDirectory, "doc-1.pdf")
        original.writeBytes("%PDF-1.7 original bytes".toByteArray())
        val passphrase = "StrongPassphrase123"

        val withoutPdfs = File(tempFolder.root, "without-pdfs.fwbk")
        manager.createBackup(withoutPdfs, passphrase, BackupOptions(includeOriginalPdfs = false))
        assertThat(ZipFile(withoutPdfs, passphrase.toCharArray()).getFileHeader("pdfs/doc-1.pdf"))
            .isNull()

        val withPdfs = File(tempFolder.root, "with-pdfs.fwbk")
        val created = manager.createBackup(
            withPdfs,
            passphrase,
            BackupOptions(includeOriginalPdfs = true),
        )
        assertThat(requireNotNull(created.getOrNull()).includesOriginalPdfs).isTrue()
        assertThat(ZipFile(withPdfs, passphrase.toCharArray()).getFileHeader("pdfs/doc-1.pdf"))
            .isNotNull()

        original.delete()
        manager.restoreBackup(withPdfs, passphrase)

        assertThat(original.exists()).isTrue()
        assertThat(original.readText()).isEqualTo("%PDF-1.7 original bytes")
    }

    @Test
    fun `a wrong passphrase and missing files are rejected`() = runBlocking<Unit> {
        seed()
        val destination = File(tempFolder.root, "locked.fwbk")
        manager.createBackup(destination, "StrongPassphrase123", BackupOptions())

        val wrongPassphrase = manager.validateBackup(destination, "WrongPassword123")
        assertThat(wrongPassphrase).isInstanceOf(AppResult.Failure::class.java)
        assertThat((wrongPassphrase as AppResult.Failure).error)
            .isInstanceOf(AppError.InvalidOperation::class.java)

        val missing = manager.validateBackup(File(tempFolder.root, "absent.fwbk"), "StrongPassphrase123")
        assertThat(missing).isInstanceOf(AppResult.Failure::class.java)

        val empty = tempFolder.newFile("empty.fwbk")
        val emptyResult = manager.validateBackup(empty, "StrongPassphrase123")
        assertThat(emptyResult).isInstanceOf(AppResult.Failure::class.java)

        // Restoring an unreadable archive must fail without touching the database.
        val restore = manager.restoreBackup(destination, "WrongPassword123")
        assertThat(restore).isInstanceOf(AppResult.Failure::class.java)
        assertThat(database.factDao().getById("fact-1")).isNotNull()
    }

    @Test
    fun `archives with traversal entries or a newer manifest version are rejected`() =
        runBlocking<Unit> {
            val traversal = File(tempFolder.root, "traversal.fwbk")
            ZipOutputStream(traversal.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("../outside.txt"))
                zip.write("escaped".toByteArray())
                zip.closeEntry()
            }

            val rejectedPath = manager.validateBackup(traversal, "StrongPassphrase123")
            assertThat(rejectedPath).isInstanceOf(AppResult.Failure::class.java)
            assertThat((rejectedPath as AppResult.Failure).error.message).contains("Suspicious path")

            val futureVersion = File(tempFolder.root, "future.fwbk")
            val password = "StrongPassphrase123"
            val zip = ZipFile(futureVersion, password.toCharArray())
            val params = ZipParameters().apply {
                fileNameInZip = "manifest.json"
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
            val manifestJson = """
                {"version":2,"createdAtEpochMs":1,"appVersion":"9.9.9","schemaVersion":99}
            """.trimIndent()
            zip.addStream(manifestJson.byteInputStream(), params)

            val rejectedVersion = manager.validateBackup(futureVersion, password)
            assertThat(rejectedVersion).isInstanceOf(AppResult.Failure::class.java)
            assertThat((rejectedVersion as AppResult.Failure).error.message)
                .contains("Unsupported backup version")
        }

    private fun readEntry(zip: ZipFile, name: String): String {
        val header = requireNotNull(zip.getFileHeader(name)) { "Missing zip entry $name" }
        return zip.getInputStream(header).bufferedReader().readText()
    }

    private suspend fun seed() {
        val now = 1_000L
        database.pdfDocumentDao().upsert(
            PdfDocumentEntity(
                id = "doc-1",
                title = "Physics Notes",
                sourceUri = "content://docs/physics.pdf",
                localFilePath = "/data/pdfs/doc-1.pdf",
                fileHash = "hash-doc-1",
                fileSizeBytes = 120_000L,
                pageCount = 10,
                pagesWithText = 10,
                processingRoute = "EXTRACT_TEXT",
                ocrEngine = null,
                extractionStatus = "FACTS_READY",
                includedInRotation = true,
                createdAtEpochMs = now,
            ),
        )
        database.factDao().upsertFactWithTags(
            FactItemEntity(
                id = "fact-1",
                documentId = "doc-1",
                lessonId = null,
                text = "Entropy never decreases in an isolated system.",
                sourceBookTitle = "Physics Notes",
                sourceLessonTitle = "Thermodynamics",
                sourcePage = 4,
                tagsCsv = "physics,entropy",
                createdAtEpochMs = now,
            ),
            tags = listOf("physics", "entropy"),
        )
        database.templateDao().upsert(
            TemplateEntity(
                id = "template-custom",
                name = "Custom Neon",
                engineKind = "NATIVE_JSON",
                schemaOrMarkup = "{}",
                builtIn = false,
                createdAtEpochMs = now,
            ),
        )
        database.templateDao().upsert(
            TemplateEntity(
                id = "builtin_minimal_text",
                name = "Minimal Text",
                engineKind = "NATIVE_JSON",
                schemaOrMarkup = "{}",
                builtIn = true,
                isDefault = true,
                createdAtEpochMs = now,
            ),
        )
        database.collectionDao().upsertCollection(
            CollectionEntity(id = "col-1", name = "Midterm Deck", createdAtEpochMs = now),
        )
    }
}

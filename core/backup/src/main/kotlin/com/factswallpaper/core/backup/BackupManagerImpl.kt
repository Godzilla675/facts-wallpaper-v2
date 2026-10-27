package com.factswallpaper.core.backup

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.cache.CardRenderCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod

@Singleton
class BackupManagerImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: FactsDatabase,
    private val cardRenderCache: CardRenderCache,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val dispatchers: FactsDispatchers,
) : BackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    override suspend fun createBackupToUri(
        destination: Uri,
        passphrase: String,
        options: BackupOptions,
    ): AppResult<BackupManifest> = withContext(dispatchers.io) {
        var temporaryFile: File? = null
        try {
            val backupFile = File.createTempFile("backup-export-", ".fwbk", context.cacheDir)
            temporaryFile = backupFile
            when (val created = createBackup(backupFile, passphrase, options)) {
                is AppResult.Failure -> created
                is AppResult.Success -> {
                    val output = context.contentResolver.openOutputStream(destination, "wt")
                        ?: throw IOException("The selected backup destination is no longer writable.")
                    output.use { stream ->
                        backupFile.inputStream().use { input -> input.copyTo(stream) }
                    }
                    created
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            AppResult.Failure(AppError.Storage(error))
        } finally {
            temporaryFile?.delete()
        }
    }

    override suspend fun createBackup(
        destinationFile: File,
        passphrase: String,
        options: BackupOptions,
    ): AppResult<BackupManifest> = withContext(dispatchers.io) {
        if (passphrase.length < MIN_PASSPHRASE_LENGTH) {
            return@withContext AppResult.Failure(
                AppError.InvalidOperation("Passphrase must be at least $MIN_PASSPHRASE_LENGTH characters long"),
            )
        }
        try {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            destinationFile.parentFile?.mkdirs()

            val zipParameters = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }

            val zipFile = ZipFile(destinationFile, passphrase.toCharArray())
            val db = database.openHelper.readableDatabase

            // 1. Export tables
            var totalDocs = 0
            var totalFacts = 0

            EXPORT_TABLES.forEach { table ->
                val rows = exportTableToJsonArray(db, table)
                if (table == "pdf_documents") {
                    totalDocs = rows.size
                } else if (table == "facts") {
                    totalFacts = rows.size
                }

                val tableJson = rows.toString().toByteArray(Charsets.UTF_8)
                val params = ZipParameters(zipParameters).apply {
                    fileNameInZip = "tables/$table.json"
                }
                zipFile.addStream(ByteArrayInputStream(tableJson), params)
            }

            // 2. Export optional PDFs
            if (options.includeOriginalPdfs) {
                val pdfDir = File(context.filesDir, "pdfs")
                if (pdfDir.exists() && pdfDir.isDirectory) {
                    pdfDir.listFiles()?.filter { it.isFile }?.forEach { pdfFile ->
                        val params = ZipParameters(zipParameters).apply {
                            fileNameInZip = "pdfs/${pdfFile.name}"
                        }
                        zipFile.addFile(pdfFile, params)
                    }
                }
            }

            // 3. Export optional pre-rendered assets
            if (options.includeRenderedAssets) {
                val cardDir = File(context.filesDir, "rendered_cards")
                if (cardDir.exists() && cardDir.isDirectory) {
                    cardDir.listFiles()?.filter { it.isFile }?.forEach { cardFile ->
                        val params = ZipParameters(zipParameters).apply {
                            fileNameInZip = "assets/cards/${cardFile.name}"
                        }
                        zipFile.addFile(cardFile, params)
                    }
                }
                val diagramDir = File(context.filesDir, "diagrams")
                if (diagramDir.exists() && diagramDir.isDirectory) {
                    diagramDir.listFiles()?.filter { it.isFile }?.forEach { diagramFile ->
                        val params = ZipParameters(zipParameters).apply {
                            fileNameInZip = "assets/diagrams/${diagramFile.name}"
                        }
                        zipFile.addFile(diagramFile, params)
                    }
                }
            }

            // 4. Export manifest
            val manifest = BackupManifest(
                version = 1,
                createdAtEpochMs = System.currentTimeMillis(),
                appVersion = "2.0.0",
                schemaVersion = FactsDatabase.VERSION,
                includesRenderedAssets = options.includeRenderedAssets,
                includesOriginalPdfs = options.includeOriginalPdfs,
                documentCount = totalDocs,
                factCount = totalFacts,
            )

            val manifestBytes = json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8)
            val manifestParams = ZipParameters(zipParameters).apply {
                fileNameInZip = "manifest.json"
            }
            zipFile.addStream(ByteArrayInputStream(manifestBytes), manifestParams)

            AppResult.Success(manifest)
        } catch (t: Throwable) {
            destinationFile.delete()
            AppResult.Failure(AppError.Storage(t))
        }
    }

    override suspend fun validateBackup(
        sourceFile: File,
        passphrase: String,
    ): AppResult<BackupManifest> = withContext(dispatchers.io) {
        if (!sourceFile.isFile || sourceFile.length() <= 0L) {
            return@withContext AppResult.Failure(AppError.InvalidOperation("Backup file not found or empty"))
        }

        try {
            val zipFile = ZipFile(sourceFile, passphrase.toCharArray())
            if (!zipFile.isValidZipFile) {
                return@withContext AppResult.Failure(AppError.InvalidOperation("Invalid backup archive format"))
            }

            for (header in zipFile.fileHeaders) {
                if (SecretScrubber.isSuspiciousPath(header.fileName)) {
                    return@withContext AppResult.Failure(
                        AppError.InvalidOperation("Suspicious path detected in archive: ${header.fileName}"),
                    )
                }
            }

            val manifestHeader = zipFile.getFileHeader("manifest.json")
                ?: return@withContext AppResult.Failure(AppError.InvalidOperation("manifest.json missing from backup"))

            val manifestJson = zipFile.getInputStream(manifestHeader).bufferedReader().readText()
            val manifest = json.decodeFromString(BackupManifest.serializer(), manifestJson)

            if (manifest.version > 1) {
                return@withContext AppResult.Failure(
                    AppError.InvalidOperation("Unsupported backup version: ${manifest.version}"),
                )
            }

            AppResult.Success(manifest)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.InvalidOperation("Failed to unlock or validate backup: ${t.message}"))
        }
    }

    override suspend fun restoreBackup(
        sourceFile: File,
        passphrase: String,
    ): AppResult<BackupManifest> = withContext(dispatchers.io) {
        val validationResult = validateBackup(sourceFile, passphrase)
        if (validationResult is AppResult.Failure) {
            return@withContext validationResult
        }
        val manifest = (validationResult as AppResult.Success).value

        val zipFile = ZipFile(sourceFile, passphrase.toCharArray())
        val db = database.openHelper.writableDatabase

        try {
            db.beginTransaction()
            db.execSQL("PRAGMA foreign_keys = OFF;")

            // 1. Delete all existing records in child-first order
            CHILD_FIRST_TABLES.forEach { table ->
                db.execSQL("DELETE FROM $table")
            }

            // 2. Insert records in parent-first order
            PARENT_FIRST_TABLES.forEach { table ->
                val entry = zipFile.getFileHeader("tables/$table.json")
                if (entry != null) {
                    val content = zipFile.getInputStream(entry).bufferedReader().readText()
                    val jsonArray = json.parseToJsonElement(content).jsonArray
                    jsonArray.forEach { element ->
                        val rowObj = element.jsonObject
                        val cv = ContentValues()
                        rowObj.forEach { (key, value) ->
                            if (!SecretScrubber.containsSecretKeyMarker(key)) {
                                putJsonPrimitive(cv, key, value)
                            }
                        }
                        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, cv)
                    }
                }
            }

            db.execSQL("PRAGMA foreign_keys = ON;")
            db.setTransactionSuccessful()
        } catch (t: Throwable) {
            return@withContext AppResult.Failure(AppError.Storage(t))
        } finally {
            db.endTransaction()
        }

        // 3. Restore PDF files if included in archive
        runCatching {
            val pdfDir = File(context.filesDir, "pdfs").apply { mkdirs() }
            zipFile.fileHeaders.filter { it.fileName.startsWith("pdfs/") && !it.isDirectory }.forEach { header ->
                val fileName = header.fileName.removePrefix("pdfs/")
                if (!SecretScrubber.isSuspiciousPath(fileName)) {
                    val targetFile = File(pdfDir, fileName)
                    zipFile.getInputStream(header).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }

        // 4. Restore rendered assets if included
        runCatching {
            val cardDir = File(context.filesDir, "rendered_cards").apply { mkdirs() }
            val diagramDir = File(context.filesDir, "diagrams").apply { mkdirs() }
            zipFile.fileHeaders.forEach { header ->
                if (header.fileName.startsWith("assets/cards/") && !header.isDirectory) {
                    val name = header.fileName.removePrefix("assets/cards/")
                    if (!SecretScrubber.isSuspiciousPath(name)) {
                        File(cardDir, name).outputStream().use { out ->
                            zipFile.getInputStream(header).use { it.copyTo(out) }
                        }
                    }
                } else if (header.fileName.startsWith("assets/diagrams/") && !header.isDirectory) {
                    val name = header.fileName.removePrefix("assets/diagrams/")
                    if (!SecretScrubber.isSuspiciousPath(name)) {
                        File(diagramDir, name).outputStream().use { out ->
                            zipFile.getInputStream(header).use { it.copyTo(out) }
                        }
                    }
                }
            }
        }

        // 5. Post-restore: clear render cache and enqueue pre-render worker
        cardRenderCache.clear()
        preRenderScheduler.enqueue()

        AppResult.Success(manifest)
    }

    private fun exportTableToJsonArray(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String,
    ): JsonArray {
        val query = if (tableName == "templates") {
            // F21: custom templates only (isDefault == 0)
            "SELECT * FROM templates WHERE isDefault = 0"
        } else {
            "SELECT * FROM $tableName"
        }

        val rows = mutableListOf<JsonObject>()
        val cursor: Cursor = db.query(query)
        cursor.use { c ->
            val columnNames = c.columnNames
            while (c.moveToNext()) {
                val rowMap = mutableMapOf<String, JsonElement>()
                for (i in columnNames.indices) {
                    val col = columnNames[i]
                    if (SecretScrubber.containsSecretKeyMarker(col)) {
                        continue
                    }
                    val type = c.getType(i)
                    val jsonElem: JsonElement = when (type) {
                        Cursor.FIELD_TYPE_NULL -> JsonNull
                        Cursor.FIELD_TYPE_INTEGER -> JsonPrimitive(c.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> JsonPrimitive(c.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> {
                            val text = c.getString(i)
                            if (col == "customHeadersJson") {
                                sanitizeCustomHeaders(text)
                            } else {
                                JsonPrimitive(text)
                            }
                        }
                        Cursor.FIELD_TYPE_BLOB -> JsonPrimitive(c.getBlob(i).joinToString(",") { it.toString() })
                        else -> JsonNull
                    }
                    rowMap[col] = jsonElem
                }
                rows.add(JsonObject(rowMap))
            }
        }
        return JsonArray(rows)
    }

    private fun sanitizeCustomHeaders(headersJson: String): JsonPrimitive {
        return try {
            val element = json.parseToJsonElement(headersJson).jsonObject
            val sanitized = element.filterKeys { !SecretScrubber.containsSecretKeyMarker(it) }
            JsonPrimitive(JsonObject(sanitized).toString())
        } catch (_: Throwable) {
            JsonPrimitive("{}")
        }
    }

    private fun putJsonPrimitive(cv: ContentValues, key: String, value: JsonElement) {
        when {
            value is JsonNull -> cv.putNull(key)
            value is JsonPrimitive && value.isString -> cv.put(key, value.content)
            value is JsonPrimitive && value.booleanOrNull != null -> cv.put(key, if (value.boolean) 1 else 0)
            value is JsonPrimitive && value.longOrNull != null -> cv.put(key, value.long)
            value is JsonPrimitive && value.doubleOrNull != null -> cv.put(key, value.double)
            else -> cv.put(key, value.toString())
        }
    }

    companion object {
        const val MIN_PASSPHRASE_LENGTH = 8

        val EXPORT_TABLES = listOf(
            "pdf_documents",
            "document_settings",
            "document_chunks",
            "lessons",
            "facts",
            "fact_tags",
            "collections",
            "collection_fact_cross_ref",
            "user_folders",
            "folder_fact_cross_ref",
            "templates",
            "diagram_assets",
            "provider_profiles",
            "wallpaper_state",
            "usage_ledger",
        )

        val CHILD_FIRST_TABLES = listOf(
            "collection_fact_cross_ref",
            "folder_fact_cross_ref",
            "fact_tags",
            "facts",
            "lessons",
            "document_chunks",
            "document_settings",
            "pdf_documents",
            "collections",
            "user_folders",
            "templates",
            "diagram_assets",
            "provider_profiles",
            "wallpaper_state",
            "usage_ledger",
        )

        val PARENT_FIRST_TABLES = listOf(
            "pdf_documents",
            "document_settings",
            "document_chunks",
            "lessons",
            "facts",
            "fact_tags",
            "collections",
            "user_folders",
            "collection_fact_cross_ref",
            "folder_fact_cross_ref",
            "templates",
            "diagram_assets",
            "provider_profiles",
            "wallpaper_state",
            "usage_ledger",
        )
    }
}

package com.factswallpaper.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase as FrameworkDatabase
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import net.zetetic.database.sqlcipher.SQLiteDatabase as SqlCipherDatabase

/**
 * Converts the app database between plain SQLite and SQLCipher in place, per spec D4.
 *
 * The migrator must run *before* Room opens the database, because SQLite cannot swap
 * an encrypted file underneath an open connection. [DatabaseModule] calls
 * [reconcile] synchronously while providing the singleton.
 *
 * Every conversion follows the same safety sequence:
 *  1. checkpoint/recover the source database so its `-wal` contents are in the file;
 *  2. export into a temporary database using SQLCipher's `sqlcipher_export`;
 *  3. copy `PRAGMA user_version` (Room's schema version) which `sqlcipher_export`
 *     does not carry over;
 *  4. verify the temporary database opens with the expected format and passes
 *     `PRAGMA integrity_check`;
 *  5. atomically swap it into place and remove the previous file plus stale
 *     journal/WAL sidecars.
 *
 * On any failure the original file is left untouched, the temporary files are
 * deleted, and [reconcile] returns the format that is actually on disk so the app
 * can still start. Failures are recorded in [lastError] for diagnostics.
 */
object DatabaseEncryptionMigrator {

    const val TAG = "DatabaseEncryption"

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    private const val HEADER_BYTES = 16

    /** Human-readable detail of the most recent failed migration, if any. */
    @Volatile
    var lastError: String? = null
        private set

    /** True when [databaseFile] exists and is not a plain SQLite file. */
    fun isEncrypted(databaseFile: File): Boolean {
        if (!databaseFile.exists() || databaseFile.length() == 0L) return false
        val header = ByteArray(HEADER_BYTES)
        RandomAccessFile(databaseFile, "r").use { file ->
            if (file.read(header) < HEADER_BYTES) return false
        }
        return !header.contentEquals(SQLITE_HEADER)
    }

    /**
     * Brings the database file in line with [targetEncrypted] and returns the format
     * that is actually used on disk afterwards.
     */
    fun reconcile(
        context: Context,
        targetEncrypted: Boolean,
        passphrase: ByteArray,
    ): Boolean {
        val databaseFile = context.getDatabasePath(FactsDatabase.NAME)
        lastError = null
        if (!databaseFile.exists()) {
            // Room creates the file on first access; it will honour the chosen factory.
            return targetEncrypted
        }
        val currentlyEncrypted = isEncrypted(databaseFile)
        if (currentlyEncrypted == targetEncrypted) return targetEncrypted

        return try {
            if (targetEncrypted) {
                encrypt(databaseFile, passphrase)
            } else {
                decrypt(databaseFile, passphrase)
            }
            val result = isEncrypted(databaseFile)
            if (result != targetEncrypted) {
                lastError = "Migration finished but the database is still " +
                    (if (result) "encrypted" else "plaintext") + "."
            }
            result
        } catch (throwable: Throwable) {
            lastError = throwable.message ?: throwable::class.java.simpleName
            Log.e(TAG, "Database encryption migration failed; keeping the existing file.", throwable)
            deleteTemporaryFiles(databaseFile)
            isEncrypted(databaseFile)
        }
    }

    private fun encrypt(databaseFile: File, passphrase: ByteArray) {
        checkpointPlaintext(databaseFile)
        val temporary = temporaryFile(databaseFile, "encrypted")
        temporary.delete()
        try {
            SqlCipherDatabase.openOrCreateDatabase(temporary, passphrase, null, null).use { target ->
                target.rawExecSQL(
                    "ATTACH DATABASE ? AS plaintext KEY ''",
                    databaseFile.absolutePath,
                )
                val version = readUserVersion(target, "plaintext")
                target.rawExecSQL("SELECT sqlcipher_export('main', 'plaintext')")
                target.rawExecSQL("PRAGMA main.user_version = $version")
                target.rawExecSQL("DETACH DATABASE plaintext")
            }
            verify(temporary, passphrase, expectEncrypted = true)
            swap(databaseFile, temporary)
        } finally {
            deleteIfExists(temporary)
        }
    }

    private fun decrypt(databaseFile: File, passphrase: ByteArray) {
        val temporary = temporaryFile(databaseFile, "plaintext")
        temporary.delete()
        try {
            SqlCipherDatabase.openDatabase(
                databaseFile.absolutePath,
                passphrase,
                null,
                SqlCipherDatabase.OPEN_READWRITE,
                null,
            ).use { source ->
                source.rawExecSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                source.rawExecSQL(
                    "ATTACH DATABASE ? AS plaintext KEY ''",
                    temporary.absolutePath,
                )
                val version = source.rawQuery("PRAGMA main.user_version", emptyArray<String>()).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
                source.rawExecSQL("SELECT sqlcipher_export('plaintext', 'main')")
                source.rawExecSQL("PRAGMA plaintext.user_version = $version")
                source.rawExecSQL("DETACH DATABASE plaintext")
            }
            verify(temporary, passphrase, expectEncrypted = false)
            swap(databaseFile, temporary)
        } finally {
            deleteIfExists(temporary)
        }
    }

    /**
     * Opens the plaintext database once to recover and truncate any hot WAL file, so
     * `ATTACH` sees every committed row.
     */
    private fun checkpointPlaintext(databaseFile: File) {
        try {
            FrameworkDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                FrameworkDatabase.OPEN_READWRITE,
            ).use { database ->
                database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use { cursor ->
                    cursor.moveToFirst()
                }
            }
        } catch (throwable: Throwable) {
            // A missing/invalid WAL is not fatal: SQLite will recover it when the
            // migrator attaches the file. Log and continue.
            Log.w(TAG, "Could not checkpoint plaintext database before export.", throwable)
        }
    }

    private fun readUserVersion(database: SqlCipherDatabase, schema: String): Int =
        database.rawQuery("PRAGMA $schema.user_version", emptyArray<String>()).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    private fun verify(file: File, passphrase: ByteArray, expectEncrypted: Boolean) {
        check(file.exists() && file.length() > 0) { "Migrated database is empty" }
        check(isEncrypted(file) == expectEncrypted) {
            "Migrated database has the wrong format"
        }
        if (expectEncrypted) {
            SqlCipherDatabase.openDatabase(
                file.absolutePath,
                passphrase,
                null,
                SqlCipherDatabase.OPEN_READONLY,
                null,
            ).use { database -> assertIntegrity(database) }
        } else {
            FrameworkDatabase.openDatabase(
                file.absolutePath,
                null,
                FrameworkDatabase.OPEN_READONLY,
            ).use { database -> assertIntegrity(database) }
        }
    }

    private fun assertIntegrity(database: android.database.sqlite.SQLiteDatabase) {
        database.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                "Migrated database failed integrity_check"
            }
        }
    }

    private fun assertIntegrity(database: SqlCipherDatabase) {
        database.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { cursor ->
            check(cursor.moveToFirst() && cursor.getString(0) == "ok") {
                "Migrated database failed integrity_check"
            }
        }
    }

    /**
     * Renames the current database out of the way, puts the verified replacement in
     * place, then deletes the old file and stale sidecars. If the second rename fails
     * the original file is restored.
     */
    private fun swap(current: File, replacement: File) {
        val previous = File(current.parentFile, current.name + ".pre-migration")
        deleteIfExists(previous)
        check(current.renameTo(previous)) { "Could not move the current database aside" }
        // The export has already read these; they belong to the old file and must not
        // be left next to the replacement.
        deleteSidecars(current)
        if (!replacement.renameTo(current)) {
            check(previous.renameTo(current)) { "Could not restore the database after a failed swap" }
            error("Could not move the migrated database into place")
        }
        // Carry over any sidecar the replacement produced while it was verified.
        for (suffix in SIDECAR_SUFFIXES) {
            val source = File(replacement.path + suffix)
            if (source.exists()) {
                val target = File(current.path + suffix)
                deleteIfExists(target)
                source.renameTo(target)
            }
        }
        deleteIfExists(previous)
    }

    private fun temporaryFile(databaseFile: File, suffix: String): File =
        File(databaseFile.parentFile, "${databaseFile.name}.$suffix.tmp")

    private fun deleteTemporaryFiles(databaseFile: File) {
        deleteIfExists(temporaryFile(databaseFile, "encrypted"))
        deleteIfExists(temporaryFile(databaseFile, "plaintext"))
        deleteIfExists(File(databaseFile.parentFile, databaseFile.name + ".pre-migration"))
    }

    private fun deleteSidecars(file: File) {
        for (suffix in SIDECAR_SUFFIXES) {
            deleteIfExists(File(file.path + suffix))
        }
    }

    private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal")

    private fun deleteIfExists(file: File) {
        runCatching { if (file.exists()) file.delete() }
    }
}

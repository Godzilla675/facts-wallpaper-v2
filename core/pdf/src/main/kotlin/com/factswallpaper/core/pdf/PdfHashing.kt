package com.factswallpaper.core.pdf

import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.model.Document
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** SHA-256 helpers used for import deduplication and OCR model verification. */
object PdfHashing {

    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).toHex()
    }

    fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().toHex()
    }

    /**
     * Streams [input] into [output] while hashing exactly the bytes written.
     * The caller owns both streams.
     */
    fun copyAndHash(input: InputStream, output: OutputStream): CopyHashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            total += read
        }
        output.flush()
        return CopyHashResult(sha256 = digest.digest().toHex(), bytesCopied = total)
    }

    private fun ByteArray.toHex(): String {
        val out = CharArray(size * 2)
        val digits = "0123456789abcdef"
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            out[index * 2] = digits[value ushr 4]
            out[index * 2 + 1] = digits[value and 0x0F]
        }
        return String(out)
    }
}

data class CopyHashResult(
    val sha256: String,
    val bytesCopied: Long,
)

/**
 * Computes and compares SHA-256 file hashes against imported library documents (F2, 10.1).
 */
@Singleton
class DuplicateHashDetector @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    fun computeHash(bytes: ByteArray): String = PdfHashing.sha256(bytes)

    fun computeHash(file: File): String = PdfHashing.sha256(file)

    suspend fun findDuplicateByHash(sha256: String): Document? =
        documentRepository.findByHash(sha256.lowercase())

    suspend fun findDuplicateForFile(file: File): DuplicateMatch? {
        val hash = computeHash(file)
        val existing = findDuplicateByHash(hash) ?: return null
        return DuplicateMatch(sha256 = hash, existingDocument = existing)
    }
}

data class DuplicateMatch(
    val sha256: String,
    val existingDocument: Document,
)

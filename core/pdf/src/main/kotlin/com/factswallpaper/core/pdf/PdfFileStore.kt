package com.factswallpaper.core.pdf

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.factswallpaper.core.common.FactsDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class StagedCopy(
    val file: File,
    val sha256: String,
    val sizeBytes: Long,
    val displayName: String?,
    val contentType: String?,
)

/**
 * Owns every app-private PDF path. Sources are always copied out of SAF /
 * share-intent storage because URI grants can be revoked at any time (F2).
 */
@Singleton
class PdfFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: FactsDispatchers,
) {
    private val stagingDir: File get() = File(context.cacheDir, "pdf_import_staging")
    private val documentsDir: File get() = File(context.filesDir, "pdfs")
    private val compiledTextDir: File get() = File(context.filesDir, "ocr_text")
    private val directPdfDir: File get() = File(context.cacheDir, "direct_pdf")

    fun newStagingFile(): File {
        stagingDir.mkdirs()
        return File(stagingDir, "staged-${java.util.UUID.randomUUID()}.pdf")
    }

    fun documentFile(documentId: String): File = File(documentsDir, "$documentId.pdf")

    fun replacementDocumentFile(documentId: String): File =
        File(documentsDir, "$documentId-${System.currentTimeMillis()}.pdf")

    fun compiledTextFile(documentId: String): File {
        compiledTextDir.mkdirs()
        return File(compiledTextDir, "$documentId.txt")
    }

    fun directPdfFile(documentId: String, range: PdfPageRange): File {
        directPdfDir.mkdirs()
        return File(directPdfDir, "$documentId-${range.startPage}-${range.endPage}.pdf")
    }

    fun deleteOtherDirectPdfs(documentId: String, keep: File) {
        val prefix = "$documentId-"
        directPdfDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix) && file.absolutePath != keep.absolutePath) {
                runCatching { file.delete() }
            }
        }
    }

    fun deleteDirectPdfs(documentId: String) {
        val prefix = "$documentId-"
        directPdfDir.listFiles()?.forEach { file ->
            if (file.name.startsWith(prefix)) runCatching { file.delete() }
        }
    }

    suspend fun copyFromUri(
        uriString: String,
        displayName: String?,
        onProgress: (copiedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): StagedCopy =
        withContext(dispatchers.io) {
            val uri = Uri.parse(uriString)
            val destination = newStagingFile()
            try {
                val source = openSource(uri)
                    ?: throw PdfImportException(
                        PdfImportErrorCode.UNREADABLE_SOURCE,
                        "Could not open $uriString",
                    )
                val totalBytes = querySize(uri)
                val result = source.use { input ->
                    destination.outputStream().use { output ->
                        PdfHashing.copyAndHash(input, output, totalBytes, onProgress)
                    }
                }
                if (!hasMagicHeader(destination)) {
                    destination.delete()
                    throw PdfImportException(
                        PdfImportErrorCode.NOT_A_PDF,
                        "Source does not start with %PDF-",
                    )
                }
                StagedCopy(
                    file = destination,
                    sha256 = result.sha256,
                    sizeBytes = result.bytesCopied,
                    displayName = displayName ?: queryDisplayName(uri),
                    contentType = context.contentResolver.getType(uri),
                )
            } catch (error: Throwable) {
                destination.delete()
                if (error is PdfImportException) throw error
                throw PdfImportException(
                    PdfImportErrorCode.STORAGE_FAILED,
                    "Could not copy PDF into app storage",
                    error,
                )
            }
        }

    suspend fun commitStaged(staged: StagedPdf, documentId: String): File =
        moveStaged(staged, documentFile(documentId))

    suspend fun moveStaged(staged: StagedPdf, target: File): File =
        moveFile(File(staged.filePath), target)

    /** Moves [source] to [target], replacing an existing file atomically when possible. */
    suspend fun moveFile(source: File, target: File): File =
        withContext(dispatchers.io) {
            documentsDir.mkdirs()
            target.parentFile?.mkdirs()
            if (target.exists() && !target.delete()) {
                throw PdfImportException(
                    PdfImportErrorCode.STORAGE_FAILED,
                    "Could not replace ${target.name}",
                )
            }
            if (!source.exists()) {
                throw PdfImportException(
                    PdfImportErrorCode.STORAGE_FAILED,
                    "Staged PDF disappeared",
                )
            }
            if (!source.renameTo(target)) {
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                source.delete()
            }
            target
        }

    suspend fun deleteStaged(path: String) = withContext(dispatchers.io) {
        runCatching { File(path).delete() }
        Unit
    }

    suspend fun deleteCompiledText(path: String?) = withContext(dispatchers.io) {
        if (!path.isNullOrBlank()) runCatching { File(path).delete() }
        Unit
    }

    suspend fun pruneStaging(olderThanMs: Long = 24L * 60L * 60L * 1_000L) =
        withContext(dispatchers.io) {
            val cutoff = System.currentTimeMillis() - olderThanMs
            stagingDir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }

    fun queryDisplayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun querySize(uri: Uri): Long? = runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst()) cursor.getLong(index).takeIf { it > 0L } else null
        }
    }.getOrNull()

    private fun openSource(uri: Uri): InputStream? = when (uri.scheme?.lowercase()) {
        ContentResolver.SCHEME_FILE -> uri.path?.let { path -> FileInputStream(File(path)) }
        else -> context.contentResolver.openInputStream(uri)
    }

    private fun hasMagicHeader(file: File): Boolean =
        file.inputStream().use { input ->
            val header = ByteArray(PdfUrlPolicy.MAGIC_HEADER.length)
            var offset = 0
            while (offset < header.size) {
                val read = input.read(header, offset, header.size - offset)
                if (read <= 0) break
                offset += read
            }
            PdfUrlPolicy.hasPdfMagicHeader(header)
        }
}

class PdfImportException(
    val code: PdfImportErrorCode,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

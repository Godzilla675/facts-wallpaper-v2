package com.factswallpaper.core.pdf

import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of re-linking a restored document to its original PDF (F21). */
sealed interface PdfRelinkResult {
    data class Success(
        val localFilePath: String,
        val sizeBytes: Long,
    ) : PdfRelinkResult

    /** The chosen file's SHA-256 does not match the document's recorded hash. */
    data object HashMismatch : PdfRelinkResult

    data class Failure(val message: String?) : PdfRelinkResult
}

/**
 * Restores the source PDF of a document whose app-private copy was lost (for
 * example after restoring a backup that excluded original PDFs).
 */
interface PdfRelinkService {
    suspend fun relink(
        documentId: String,
        expectedSha256: String,
        uriString: String,
    ): PdfRelinkResult
}

@Singleton
class DefaultPdfRelinkService @Inject constructor(
    private val fileStore: PdfFileStore,
) : PdfRelinkService {

    override suspend fun relink(
        documentId: String,
        expectedSha256: String,
        uriString: String,
    ): PdfRelinkResult = try {
        val staged = fileStore.copyFromUri(uriString, displayName = null)
        if (!staged.sha256.equals(expectedSha256, ignoreCase = true)) {
            fileStore.deleteStaged(staged.file.path)
            PdfRelinkResult.HashMismatch
        } else {
            val target = fileStore.moveFile(
                source = staged.file,
                target = fileStore.documentFile(documentId),
            )
            PdfRelinkResult.Success(
                localFilePath = target.path,
                sizeBytes = staged.sizeBytes,
            )
        }
    } catch (exception: PdfImportException) {
        PdfRelinkResult.Failure(exception.message)
    } catch (exception: Exception) {
        PdfRelinkResult.Failure(exception.message)
    }
}

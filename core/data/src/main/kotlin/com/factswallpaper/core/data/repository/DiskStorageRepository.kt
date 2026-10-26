package com.factswallpaper.core.data.repository

import android.content.Context
import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.FactsDatabase
import com.factswallpaper.core.database.dao.RenderCacheDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class DiskStorageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: FactsDatabase,
    private val renderCacheDao: RenderCacheDao,
    private val dispatchers: FactsDispatchers,
) : StorageRepository {

    override suspend fun stats(): StorageStats = withContext(dispatchers.io) {
        StorageStats(
            pdfBytes = directorySize(File(context.filesDir, PDF_DIR)),
            cardCacheBytes = directorySize(File(context.filesDir, CARD_CACHE_DIR)),
            diagramCacheBytes = directorySize(File(context.filesDir, DIAGRAM_CACHE_DIR)),
            databaseBytes = databaseSize(),
        )
    }

    override suspend fun clearRenderCache(): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            renderCacheDao.deleteAll()
            File(context.filesDir, CARD_CACHE_DIR).deleteRecursively()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage(t))
        }
    }

    override suspend fun clearDiagramCache(): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            renderCacheDao.deleteAllDiagramAssets()
            File(context.filesDir, DIAGRAM_CACHE_DIR).deleteRecursively()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage(t))
        }
    }

    override suspend fun compactDatabase(): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            database.compact()
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage(t))
        }
    }

    private fun directorySize(directory: File): Long {
        if (!directory.exists()) return 0L
        return directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }

    private fun databaseSize(): Long {
        val databaseFile = context.getDatabasePath(FactsDatabase.NAME)
        val wal = File("${databaseFile.path}-wal").takeIf { it.exists() }?.length() ?: 0L
        val shm = File("${databaseFile.path}-shm").takeIf { it.exists() }?.length() ?: 0L
        return databaseFile.length() + wal + shm
    }

    companion object {
        const val PDF_DIR = "pdfs"
        const val CARD_CACHE_DIR = "rendered_cards"
        const val DIAGRAM_CACHE_DIR = "diagrams"
    }
}

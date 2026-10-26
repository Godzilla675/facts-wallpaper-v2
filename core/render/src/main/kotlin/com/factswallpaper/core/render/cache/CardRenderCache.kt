package com.factswallpaper.core.render.cache

import android.content.Context
import android.graphics.Bitmap
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.dao.RenderCacheDao
import com.factswallpaper.core.database.entity.RenderCacheEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

data class CachedCard(
    val cacheKey: String,
    val factId: String,
    val templateId: String,
    val templateVersion: Int,
    val themeKey: String,
    val targetSurface: String,
    val pngFile: File,
    val widthPx: Int,
    val heightPx: Int,
    val isLastKnownGood: Boolean,
)

data class CardCacheWrite(
    val cacheKey: String,
    val factId: String,
    val templateId: String,
    val templateVersion: Int,
    val themeKey: String,
    val targetSurface: String,
    val widthPx: Int,
    val heightPx: Int,
    val markAsLastKnownGood: Boolean,
)

/**
 * Disk + Room index for pre-rendered card bitmaps (§7.5, F14).
 *
 * PNGs live at `filesDir/rendered_cards/{cacheKey}.png`; the Room row is written only after
 * the PNG is safely on disk, so the wallpaper service can never see a row without pixels.
 */
@Singleton
class CardRenderCache @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderCacheDao: RenderCacheDao,
    private val dispatchers: FactsDispatchers,
) {

    suspend fun find(cacheKey: String): CachedCard? = withContext(dispatchers.io) {
        val entry = renderCacheDao.getByKey(cacheKey) ?: return@withContext null
        val file = File(entry.pngPath)
        if (!file.isFile || file.length() <= 0L) {
            renderCacheDao.deleteByKey(cacheKey)
            return@withContext null
        }
        entry.toCachedCard(file)
    }

    suspend fun getLastKnownGood(): CachedCard? = withContext(dispatchers.io) {
        val entry = renderCacheDao.getLastKnownGood() ?: return@withContext null
        val file = File(entry.pngPath)
        if (!file.isFile || file.length() <= 0L) {
            renderCacheDao.deleteByKey(entry.cacheKey)
            return@withContext null
        }
        entry.toCachedCard(file)
    }

    suspend fun store(
        request: CardCacheWrite,
        bitmap: Bitmap,
    ): CachedCard? = withContext(dispatchers.io) {
        val directory = File(context.filesDir, "rendered_cards").apply { mkdirs() }
        val file = File(directory, "${request.cacheKey}.png")
        try {
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    return@withContext null
                }
            }
            val entity = RenderCacheEntity(
                cacheKey = request.cacheKey,
                factId = request.factId,
                templateId = request.templateId,
                templateVersion = request.templateVersion,
                themeKey = request.themeKey,
                targetSurface = request.targetSurface,
                pngPath = file.absolutePath,
                widthPx = bitmap.width,
                heightPx = bitmap.height,
                fileSizeBytes = file.length(),
                isLastKnownGood = false,
                createdAtEpochMs = System.currentTimeMillis(),
            )
            renderCacheDao.upsert(entity)
            if (request.markAsLastKnownGood) {
                renderCacheDao.markLastKnownGood(request.cacheKey)
            }
            (renderCacheDao.getByKey(request.cacheKey) ?: entity).toCachedCard(file)
        } catch (_: Throwable) {
            file.delete()
            null
        }
    }

    suspend fun clear() = withContext(dispatchers.io) {
        File(context.filesDir, "rendered_cards").listFiles()?.forEach { it.delete() }
        renderCacheDao.deleteAll()
    }

    private fun RenderCacheEntity.toCachedCard(file: File): CachedCard = CachedCard(
        cacheKey = cacheKey,
        factId = factId,
        templateId = templateId,
        templateVersion = templateVersion,
        themeKey = themeKey,
        targetSurface = targetSurface,
        pngFile = file,
        widthPx = widthPx,
        heightPx = heightPx,
        isLastKnownGood = isLastKnownGood,
    )
}

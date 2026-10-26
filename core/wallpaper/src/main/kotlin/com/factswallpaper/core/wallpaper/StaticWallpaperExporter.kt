package com.factswallpaper.core.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import com.factswallpaper.core.common.AppError
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.model.WallpaperTarget
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class StaticWallpaperExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: FactsDispatchers,
) {
    suspend fun exportBitmap(
        bitmap: Bitmap,
        target: WallpaperTarget,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flags = when (target) {
                WallpaperTarget.HOME -> WallpaperManager.FLAG_SYSTEM
                WallpaperTarget.LOCK -> WallpaperManager.FLAG_LOCK
                WallpaperTarget.BOTH -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                WallpaperTarget.LIVE -> WallpaperManager.FLAG_SYSTEM
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(bitmap, null, true, flags)
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
            AppResult.Success(Unit)
        } catch (t: Throwable) {
            AppResult.Failure(AppError.Storage(t))
        }
    }

    suspend fun exportPngFile(
        pngFile: File,
        target: WallpaperTarget,
    ): AppResult<Unit> = withContext(dispatchers.io) {
        if (!pngFile.isFile || pngFile.length() <= 0L) {
            return@withContext AppResult.Failure(
                AppError.Storage(IllegalStateException("PNG file not found: ${pngFile.path}")),
            )
        }
        val bitmap = BitmapFactory.decodeFile(pngFile.absolutePath)
            ?: return@withContext AppResult.Failure(
                AppError.Storage(IllegalStateException("Could not decode bitmap from ${pngFile.path}")),
            )
        exportBitmap(bitmap, target)
    }
}

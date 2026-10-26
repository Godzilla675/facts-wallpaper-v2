package com.factswallpaper.core.render

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

data class RenderSize(
    val widthPx: Int,
    val heightPx: Int,
)

/**
 * Persists the last wallpaper surface size reported by the live wallpaper engine (§11.8).
 *
 * The pre-render worker runs without a Surface, so it must render at exactly the size the
 * engine will later request. The engine calls [saveReportedSize] on every `onSurfaceChanged`;
 * before the first report we fall back to the display bounds in portrait orientation.
 */
@Singleton
class RenderSizeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun current(): RenderSize {
        val storedWidth = preferences.getInt(KEY_WIDTH, 0)
        val storedHeight = preferences.getInt(KEY_HEIGHT, 0)
        if (storedWidth > 0 && storedHeight > 0) {
            return RenderSize(storedWidth, storedHeight)
        }
        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.takeIf { it > 0 } ?: DEFAULT_WIDTH
        val height = metrics.heightPixels.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        return RenderSize(min(width, height), max(width, height))
    }

    fun saveReportedSize(widthPx: Int, heightPx: Int) {
        if (widthPx <= 0 || heightPx <= 0) return
        preferences.edit()
            .putInt(KEY_WIDTH, widthPx)
            .putInt(KEY_HEIGHT, heightPx)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "render_surface"
        const val KEY_WIDTH = "width_px"
        const val KEY_HEIGHT = "height_px"
        const val DEFAULT_WIDTH = 1080
        const val DEFAULT_HEIGHT = 2400
    }
}

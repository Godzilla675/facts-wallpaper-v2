package com.factswallpaper.core.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

/**
 * Entry point for the live wallpaper.
 *
 * Phase P1 ships the real Android wallpaper service so the picker entry and
 * activation flow work end to end. Card rendering, rotation scheduling and the
 * pre-render pipeline are owned by the wallpaper phase and will draw on this
 * engine surface through the shared renderer.
 */
class FactsLiveWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = FactsEngine()

    private inner class FactsEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private val backgroundPaint = Paint().apply {
            color = Color.rgb(9, 10, 12)
            style = Paint.Style.FILL
        }
        private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(196, 161, 96)
            style = Paint.Style.FILL
        }
        private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(238, 236, 230)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("serif", Typeface.NORMAL)
        }
        private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(155, 158, 165)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        private var visible = true

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                draw()
            } else {
                handler.removeCallbacksAndMessages(null)
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            draw()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            draw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            handler.removeCallbacksAndMessages(null)
        }

        private fun draw() {
            if (!visible) return
            val holder = surfaceHolder
            val canvas: Canvas = holder.lockCanvas() ?: return
            try {
                drawBackground(canvas)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
        }

        private fun drawBackground(canvas: Canvas) {
            val width = canvas.width.toFloat().coerceAtLeast(1f)
            val height = canvas.height.toFloat().coerceAtLeast(1f)
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)
            canvas.drawCircle(
                width * 0.78f,
                height * 0.18f,
                width * 0.36f,
                accentPaint.apply { alpha = 26 },
            )
            canvas.drawCircle(
                width * 0.16f,
                height * 0.86f,
                width * 0.30f,
                accentPaint.apply { alpha = 18 },
            )
            accentPaint.alpha = 255
            canvas.drawCircle(width / 2f, height * 0.40f, width * 0.012f, accentPaint)
            titlePaint.textSize = width * 0.085f
            captionPaint.textSize = width * 0.036f
            canvas.drawText(
                getString(R.string.facts_wallpaper_service_title),
                width / 2f,
                height * 0.50f,
                titlePaint,
            )
            canvas.drawText(
                getString(R.string.facts_wallpaper_service_caption),
                width / 2f,
                height * 0.55f,
                captionPaint,
            )
        }
    }
}

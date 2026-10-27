package com.factswallpaper.core.wallpaper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.RenderSizeStore
import com.factswallpaper.core.render.cache.CardRenderCache
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Live Wallpaper Service for Facts Wallpaper (spec §8.1).
 *
 * NON-NEGOTIABLE INVARIANT (Invariant 6):
 * This service NEVER invokes network calls, AI clients, PDF parsers, or WebView instances.
 * It reads exclusively pre-rendered PNG bitmaps from CardRenderCache (with a lightweight
 * Canvas text fallback if the cache is empty).
 */
@AndroidEntryPoint
class FactsLiveWallpaperService : WallpaperService() {

    @Inject lateinit var rotationController: WallpaperRotationController
    @Inject lateinit var renderCache: CardRenderCache
    @Inject lateinit var renderSizeStore: RenderSizeStore
    @Inject lateinit var wallpaperRepository: WallpaperRepository
    @Inject lateinit var preRenderScheduler: CardPreRenderScheduler

    override fun onCreateEngine(): Engine = FactsWallpaperEngine()

    inner class FactsWallpaperEngine : Engine() {
        private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var isVisibleState = false
        private var unlockReceiverRegistered = false
        private var stateObserverJob: Job? = null

        private val unlockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_USER_PRESENT) {
                    engineScope.launch {
                        if (rotationController.onDeviceUnlocked()) {
                            drawCurrentCard()
                        }
                    }
                }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            registerUnlockReceiver()
            startObservingWallpaperState()
        }

        override fun onSurfaceCreated(holder: SurfaceHolder?) {
            super.onSurfaceCreated(holder)
            drawCurrentCard()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            if (width > 0 && height > 0) {
                renderSizeStore.saveReportedSize(width, height)
                preRenderScheduler.enqueue()
            }
            drawCurrentCard()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible
            if (visible) {
                engineScope.launch {
                    rotationController.onVisibilityChanged(true)
                    drawCurrentCard()
                }
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            unregisterUnlockReceiver()
            stateObserverJob?.cancel()
            engineScope.cancel()
            super.onDestroy()
        }

        private fun registerUnlockReceiver() {
            if (!unlockReceiverRegistered) {
                try {
                    val filter = IntentFilter(Intent.ACTION_USER_PRESENT)
                    ContextCompat.registerReceiver(
                        this@FactsLiveWallpaperService,
                        unlockReceiver,
                        filter,
                        ContextCompat.RECEIVER_NOT_EXPORTED,
                    )
                    unlockReceiverRegistered = true
                } catch (_: Throwable) {
                    // Ignore registration errors on platforms without intent dispatch
                }
            }
        }

        private fun unregisterUnlockReceiver() {
            if (unlockReceiverRegistered) {
                try {
                    unregisterReceiver(unlockReceiver)
                } catch (_: Throwable) {}
                unlockReceiverRegistered = false
            }
        }

        private fun startObservingWallpaperState() {
            stateObserverJob?.cancel()
            stateObserverJob = engineScope.launch {
                wallpaperRepository.observeState().collectLatest {
                    if (isVisibleState) {
                        drawCurrentCard()
                    }
                }
            }
        }

        private fun drawCurrentCard() {
            engineScope.launch {
                val pngPath = rotationController.getCurrentOrFallbackPngPath()
                val bitmap = if (pngPath != null) {
                    withContext(Dispatchers.IO) {
                        runCatching { BitmapFactory.decodeFile(pngPath) }.getOrNull()
                    }
                } else {
                    null
                }

                val currentFact = if (bitmap == null) {
                    rotationController.getCurrentFact()
                } else {
                    null
                }

                drawBitmapOrFallback(bitmap, currentFact)
            }
        }

        private fun drawBitmapOrFallback(bitmap: Bitmap?, currentFact: Fact?) {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching { holder.lockHardwareCanvas() }.getOrNull() ?: holder.lockCanvas()
                } else {
                    holder.lockCanvas()
                }
                if (canvas != null) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        drawBitmapFitted(canvas, bitmap)
                    } else if (currentFact != null) {
                        drawFactTextFallback(canvas, currentFact)
                    } else {
                        drawFallbackTypography(canvas)
                    }
                }
            } catch (_: Throwable) {
                // Protect wallpaper service against canvas lock exceptions
            } finally {
                if (canvas != null) {
                    runCatching { holder.unlockCanvasAndPost(canvas) }
                }
            }
        }

        private fun drawBitmapFitted(canvas: Canvas, bitmap: Bitmap) {
            val canvasWidth = canvas.width
            val canvasHeight = canvas.height
            val bitmapWidth = bitmap.width
            val bitmapHeight = bitmap.height

            if (canvasWidth <= 0 || canvasHeight <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) return

            val scale = max(
                canvasWidth.toFloat() / bitmapWidth.toFloat(),
                canvasHeight.toFloat() / bitmapHeight.toFloat(),
            )
            val scaledWidth = (bitmapWidth * scale).toInt()
            val scaledHeight = (bitmapHeight * scale).toInt()
            val left = (canvasWidth - scaledWidth) / 2
            val top = (canvasHeight - scaledHeight) / 2

            val srcRect = Rect(0, 0, bitmapWidth, bitmapHeight)
            val destRect = Rect(left, top, left + scaledWidth, top + scaledHeight)
            canvas.drawColor(Color.BLACK)
            canvas.drawBitmap(bitmap, srcRect, destRect, null)
        }

        private fun drawFactTextFallback(canvas: Canvas, fact: Fact) {
            val width = canvas.width
            val height = canvas.height
            if (width <= 0 || height <= 0) return

            // Scholar's Slate deep background
            canvas.drawColor(Color.rgb(15, 20, 25))

            val clockSafeTop = height * 0.26f
            val bottomSafe = height * 0.92f
            val horizontalPadding = width * 0.08f
            val contentWidth = (width - (2 * horizontalPadding)).toInt().coerceAtLeast(100)

            // Accent category / source lesson tag
            val headerText = fact.tags.firstOrNull()?.uppercase()
                ?: fact.sourceLessonTitle?.uppercase()?.take(28)
                ?: getString(R.string.wallpaper_fallback_fact_label)
            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(196, 161, 96) // Warm gold
                textSize = (width * 0.034f).coerceIn(12f, 22f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                letterSpacing = 0.06f
            }
            var currentY = clockSafeTop + (width * 0.04f)
            canvas.drawText(headerText, horizontalPadding, currentY, headerPaint)
            currentY += width * 0.05f

            // Fact body text wrapped with StaticLayout
            val bodyText = fact.text
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(240, 238, 233)
                textSize = (width * 0.046f).coerceIn(16f, 32f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            }
            val bodyLayout = StaticLayout.Builder.obtain(
                bodyText,
                0,
                bodyText.length,
                bodyPaint,
                contentWidth,
            ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .setMaxLines(10)
                .build()

            canvas.save()
            canvas.translate(horizontalPadding, currentY)
            bodyLayout.draw(canvas)
            canvas.restore()

            // Source reference at bottom safe area
            val sourceText = buildString {
                if (fact.sourceBookTitle.isNotBlank()) append(fact.sourceBookTitle)
                if (fact.sourcePage != null) {
                    if (isNotEmpty()) append(" · ")
                    append(getString(R.string.wallpaper_fallback_page_short, fact.sourcePage))
                }
            }
            if (sourceText.isNotBlank()) {
                val sourcePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.rgb(140, 146, 158)
                    textSize = (width * 0.032f).coerceIn(11f, 20f)
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
                canvas.drawText(sourceText, horizontalPadding, bottomSafe - (width * 0.02f), sourcePaint)
            }
        }

        private fun drawFallbackTypography(canvas: Canvas) {
            val width = canvas.width
            val height = canvas.height
            if (width <= 0 || height <= 0) return

            // Scholar's Slate deep background
            canvas.drawColor(Color.rgb(15, 20, 25))

            val clockSafeTop = height * 0.26f
            val bottomSafe = height * 0.92f

            // Accent bar
            val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(196, 161, 96) // Warm gold
                style = Paint.Style.FILL
            }
            val barWidth = width * 0.16f
            val barHeight = 6f
            val barLeft = (width - barWidth) / 2f
            canvas.drawRect(barLeft, clockSafeTop, barLeft + barWidth, clockSafeTop + barHeight, accentPaint)

            // Title: "Facts Wallpaper"
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(240, 238, 233)
                textAlign = Paint.Align.CENTER
                textSize = (width * 0.065f).coerceIn(24f, 72f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            val titleY = clockSafeTop + (width * 0.12f)
            canvas.drawText(getString(R.string.facts_wallpaper_service_title), width / 2f, titleY, titlePaint)

            // Subtitle: "Import a PDF to start studying"
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(160, 165, 175)
                textAlign = Paint.Align.CENTER
                textSize = (width * 0.040f).coerceIn(16f, 36f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val subtitleY = titleY + (width * 0.08f)
            canvas.drawText(
                getString(R.string.wallpaper_fallback_subtitle),
                width / 2f,
                subtitleY,
                subtitlePaint,
            )

            // Gesture-safe bottom hint
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(100, 105, 115)
                textAlign = Paint.Align.CENTER
                textSize = (width * 0.032f).coerceIn(12f, 24f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            canvas.drawText(
                getString(R.string.wallpaper_fallback_hint),
                width / 2f,
                bottomSafe - 24f,
                hintPaint,
            )
        }
    }
}

package com.factswallpaper.core.render.diagram

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.util.Base64
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.database.dao.RenderCacheDao
import com.factswallpaper.core.database.entity.DiagramAssetEntity
import com.factswallpaper.core.render.RenderHash
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

data class DiagramRenderRequest(
    val kind: MarkupKind,
    val source: String,
    val foregroundColorHex: String = "#E2E8F0",
    val backgroundColorHex: String = "#00000000",
    val accentColorHex: String = "#76D1FF",
    val themeKey: String,
    val maxWidthPx: Int = 900,
    val maxHeightPx: Int = 1600,
)

sealed interface DiagramRenderResult {
    data class Success(val asset: DiagramAssetEntity) : DiagramRenderResult
    data class Failure(val reason: String) : DiagramRenderResult
}

interface OfflineWebViewDiagramRenderer {
    suspend fun render(request: DiagramRenderRequest): DiagramRenderResult
}

/**
 * Offline WebView rasterizer from §7.3 / §11.8.
 *
 * The WebView is created per render on the main looper, is never attached to the activity,
 * has network file/content access disabled and can only load the bundled
 * `appassets.androidplatform.net` assets. Every other URL receives a 403 response. A shared
 * mutex serializes WebView usage because Android WebView is not thread-safe.
 */
@Singleton
class AndroidWebViewDiagramRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val renderCacheDao: RenderCacheDao,
    private val dispatchers: FactsDispatchers,
) : OfflineWebViewDiagramRenderer {

    private val rendererMutex = Mutex()

    override suspend fun render(request: DiagramRenderRequest): DiagramRenderResult {
        val validation = DiagramValidator.validate(request.kind, request.source)
        if (validation is DiagramValidator.Result.Invalid) {
            return DiagramRenderResult.Failure(validation.reason)
        }
        val source = DiagramValidator.normalize(request.source)
        return withContext(dispatchers.main) {
            rendererMutex.withLock {
                withTimeoutOrNull(RENDER_TIMEOUT_MS) {
                    renderOnMainThread(request.copy(source = source))
                } ?: DiagramRenderResult.Failure("Diagram rendering timed out")
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun renderOnMainThread(request: DiagramRenderRequest): DiagramRenderResult {
        val contentHash = RenderHash.diagramHash(request.kind.name, request.source, request.themeKey)
        val targetFile = File(diagramsDir(), "$contentHash.png")

        val pageReady = CompletableDeferred<Unit>()
        val bridgeResult = CompletableDeferred<String>()
        val bridge = RenderBridge(bridgeResult)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()

        val webView = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                blockNetworkLoads = true
                blockNetworkImage = true
                allowFileAccess = false
                allowContentAccess = false
                domStorageEnabled = false
                cacheMode = WebSettings.LOAD_NO_CACHE
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = true
            }
            addJavascriptInterface(bridge, BRIDGE_NAME)
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? = interceptAssetRequest(assetLoader, request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    pageReady.complete(Unit)
                }
            }
            layout(0, 0, request.maxWidthPx, request.maxHeightPx)
            measure(
                View.MeasureSpec.makeMeasureSpec(request.maxWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(request.maxHeightPx, View.MeasureSpec.AT_MOST),
            )
        }

        return try {
            val indexUrl = "$ASSET_BASE/index.html"
            if (request.kind == MarkupKind.TIKZ) {
                val encoded = Uri.encode(request.source)
                webView.loadUrl("$ASSET_BASE/tikz.html?src=$encoded")
            } else {
                webView.loadUrl(indexUrl)
            }
            pageReady.await()
            if (request.kind != MarkupKind.TIKZ) {
                val script = buildString {
                    append("window.renderStudyMarkup(")
                    append(JSONObject.quote(request.kind.name))
                    append(", ")
                    append(JSONObject.quote(request.source))
                    append(", ")
                    append(JSONObject.quote(request.foregroundColorHex))
                    append(", ")
                    append(JSONObject.quote(request.backgroundColorHex))
                    append(", ")
                    append(JSONObject.quote(request.accentColorHex))
                    append(");")
                }
                webView.evaluateJavascript(script, null)
            }

            val json = bridgeResult.await()
            val payload = JSONObject(json)
            if (!payload.optBoolean("ok", false)) {
                return DiagramRenderResult.Failure(
                    payload.optString("error").ifBlank { "Renderer rejected the markup" },
                )
            }
            val stageWidth = payload.optInt("width", 0).coerceIn(1, MAX_STAGE_PX)
            val stageHeight = payload.optInt("height", 0).coerceIn(1, MAX_STAGE_PX)
            if (payload.optInt("width", 0) <= 0 || payload.optInt("height", 0) <= 0) {
                return DiagramRenderResult.Failure("Renderer produced an empty stage")
            }

            val bitmap = drawWebView(webView, stageWidth, stageHeight)
                ?: return DiagramRenderResult.Failure("Could not rasterize the diagram")
            val scaled = scaleToFit(bitmap, request.maxWidthPx, request.maxHeightPx)
            if (scaled !== bitmap) bitmap.recycle()

            withContext(dispatchers.io) {
                targetFile.parentFile?.mkdirs()
                FileOutputStream(targetFile).use { output ->
                    if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        return@withContext null
                    }
                }
                val asset = DiagramAssetEntity(
                    id = contentHash,
                    kind = request.kind.name,
                    sourceMarkup = request.source,
                    contentHash = contentHash,
                    renderedPngPath = targetFile.absolutePath,
                    widthPx = scaled.width,
                    heightPx = scaled.height,
                    verified = false,
                    createdAtEpochMs = System.currentTimeMillis(),
                )
                renderCacheDao.upsertDiagramAsset(asset)
                asset
            }?.let { asset ->
                scaled.recycle()
                DiagramRenderResult.Success(asset)
            } ?: run {
                scaled.recycle()
                DiagramRenderResult.Failure("Could not write the diagram PNG")
            }
        } catch (t: Throwable) {
            DiagramRenderResult.Failure(t.message ?: "Diagram rendering failed")
        } finally {
            webView.removeJavascriptInterface(BRIDGE_NAME)
            webView.stopLoading()
            webView.destroy()
        }
    }

    private fun drawWebView(webView: WebView, width: Int, height: Int): Bitmap? {
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, width, height)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        webView.draw(canvas)
        return bitmap.takeIf { !it.isRecycled }
    }

    private fun scaleToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width.toFloat(),
            maxHeight.toFloat() / bitmap.height.toFloat(),
        )
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun interceptAssetRequest(
        assetLoader: WebViewAssetLoader,
        url: Uri,
    ): WebResourceResponse? {
        if (url.scheme == "about" || url.scheme == "data" || url.scheme == "blob") return null
        if (url.host != ASSET_HOST) return blockedResponse()
        return assetLoader.shouldInterceptRequest(url) ?: blockedResponse()
    }

    private fun blockedResponse(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        "UTF-8",
        403,
        "Blocked",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

    private fun diagramsDir(): File = File(context.filesDir, "diagrams").apply { mkdirs() }

    private class RenderBridge(
        private val result: CompletableDeferred<String>,
    ) {
        @JavascriptInterface
        fun postResult(json: String) {
            if (!result.isCompleted) result.complete(json)
        }
    }

    private companion object {
        const val ASSET_BASE = "https://appassets.androidplatform.net/assets/renderer"
        const val ASSET_HOST = "appassets.androidplatform.net"
        const val BRIDGE_NAME = "AndroidRenderBridge"
        const val RENDER_TIMEOUT_MS = 10_000L
        const val MAX_STAGE_PX = 4096
    }
}

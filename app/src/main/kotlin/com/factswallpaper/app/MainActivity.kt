package com.factswallpaper.app

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.factswallpaper.core.wallpaper.FactsLiveWallpaperService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val sharedPdfUri = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedPdfUri.value = extractPdfUri(intent)
        setContent {
            val sharedUri by sharedPdfUri.collectAsStateWithLifecycle()
            FactsWallpaperApp(
                sharedPdfUri = sharedUri,
                onSharedPdfConsumed = { sharedPdfUri.value = null },
                onLaunchLiveWallpaper = ::openLiveWallpaperPicker,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedPdfUri.value = extractPdfUri(intent)
    }

    private fun extractPdfUri(intent: Intent?): String? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(
                    intent,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                )
                uri?.takeIf { looksLikePdf(intent.type, it) }?.toString()
            }
            Intent.ACTION_VIEW -> intent.data?.takeIf { looksLikePdf(intent.type, it) }?.toString()
            else -> null
        }
    }

    private fun looksLikePdf(mimeType: String?, uri: Uri): Boolean =
        mimeType == PDF_MIME_TYPE ||
            uri.toString().endsWith(".pdf", ignoreCase = true) ||
            contentResolver.getType(uri) == PDF_MIME_TYPE

    private fun openLiveWallpaperPicker() {
        val component = ComponentName(this, FactsLiveWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        val openedDirectly = runCatching {
            startActivity(direct)
            true
        }.getOrDefault(false)
        if (openedDirectly) return

        val chooser = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
        val openedChooser = runCatching {
            startActivity(chooser)
            true
        }.getOrDefault(false)
        if (!openedChooser) {
            Toast.makeText(
                this,
                getString(R.string.live_wallpaper_unavailable),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private companion object {
        const val PDF_MIME_TYPE = "application/pdf"
    }
}

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
import androidx.lifecycle.lifecycleScope
import com.factswallpaper.core.wallpaper.FactsLiveWallpaperService
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var rotationController: WallpaperRotationController

    private val sharedPdfUri = MutableStateFlow<String?>(null)
    private val openImportPdf = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val sharedUri by sharedPdfUri.collectAsStateWithLifecycle()
            val initialImport by openImportPdf.collectAsStateWithLifecycle()
            FactsWallpaperApp(
                sharedPdfUri = sharedUri,
                initialOpenImport = initialImport,
                onImportConsumed = { openImportPdf.value = false },
                onSharedPdfConsumed = { sharedPdfUri.value = null },
                onLaunchLiveWallpaper = ::openLiveWallpaperPicker,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            ACTION_NEXT_FACT -> {
                lifecycleScope.launch {
                    rotationController.advance()
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_next_fact_advanced,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            ACTION_IMPORT_PDF -> {
                openImportPdf.value = true
            }
            else -> {
                sharedPdfUri.value = extractPdfUri(intent)
            }
        }
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
        const val ACTION_NEXT_FACT = "com.factswallpaper.action.NEXT_FACT"
        const val ACTION_IMPORT_PDF = "com.factswallpaper.action.IMPORT_PDF"
    }
}

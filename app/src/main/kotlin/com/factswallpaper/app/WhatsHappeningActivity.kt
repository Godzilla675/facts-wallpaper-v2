package com.factswallpaper.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.factswallpaper.core.designsystem.theme.FactsTheme
import com.factswallpaper.feature.library.WhatsHappeningScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class WhatsHappeningActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FactsTheme {
                WhatsHappeningScreen(
                    onBack = { finish() },
                    onOpenDocument = { _ -> finish() },
                )
            }
        }
    }
}

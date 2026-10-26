package com.factswallpaper.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.factswallpaper.core.pdf.PdfBoxRuntime
import com.factswallpaper.core.pdf.PdfNotifications
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class FactsWallpaperApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var pdfBoxRuntime: PdfBoxRuntime

    @Inject
    lateinit var pdfNotifications: PdfNotifications

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        pdfBoxRuntime.initialize()
        pdfNotifications.ensureChannel()
    }
}

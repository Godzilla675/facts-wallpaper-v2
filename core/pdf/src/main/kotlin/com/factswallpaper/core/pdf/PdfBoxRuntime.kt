package com.factswallpaper.core.pdf

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Initialises PdfBox-Android's asset/resource loader exactly once. The app
 * Application injects this type and calls [initialize] before any import can
 * run (spec D3).
 */
@Singleton
class PdfBoxRuntime @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    @Volatile
    private var ready = false

    fun initialize() {
        if (ready) return
        synchronized(this) {
            if (ready) return
            PDFBoxResourceLoader.init(context.applicationContext)
            ready = true
        }
    }
}

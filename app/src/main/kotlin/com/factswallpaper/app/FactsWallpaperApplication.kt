package com.factswallpaper.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.template.CardTemplateSeeder
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class FactsWallpaperApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var templateSeeder: CardTemplateSeeder

    @Inject
    lateinit var preRenderScheduler: CardPreRenderScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            runCatching { templateSeeder.ensureSeeded() }
            runCatching { preRenderScheduler.enqueue() }
        }
    }
}

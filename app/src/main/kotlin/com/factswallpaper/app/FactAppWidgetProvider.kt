package com.factswallpaper.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.ui.format.SourceReferenceFormatter
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FactAppWidgetProvider : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun wallpaperRotationController(): WallpaperRotationController
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_WIDGET_NEXT_FACT -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val entryPoint = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            WidgetEntryPoint::class.java,
                        )
                        entryPoint.wallpaperRotationController().advance()
                        updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ACTION_FACT_UPDATED, Intent.ACTION_BOOT_COMPLETED -> {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateAllWidgets(context)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_WIDGET_NEXT_FACT = "com.factswallpaper.app.ACTION_WIDGET_NEXT_FACT"
        const val ACTION_FACT_UPDATED = "com.factswallpaper.app.ACTION_FACT_UPDATED"

        suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
        ) {
            if (appWidgetIds.isEmpty()) return

            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            val rotationController = entryPoint.wallpaperRotationController()
            val fact = rotationController.getCurrentFact()

            val factText = fact?.text ?: context.getString(R.string.widget_empty_fact)
            val sourceText = if (fact != null) {
                SourceReferenceFormatter.format(
                    bookTitle = fact.sourceBookTitle,
                    lessonTitle = fact.sourceLessonTitle,
                    page = fact.sourcePage,
                    mode = SourceReferenceMode.COMPACT,
                )
            } else {
                context.getString(R.string.app_name)
            }

            // Click body to open MainActivity
            val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            // Click Next to advance fact
            val nextIntent = Intent(context, FactAppWidgetProvider::class.java).apply {
                action = ACTION_WIDGET_NEXT_FACT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context,
                100,
                nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_fact).apply {
                    setTextViewText(R.id.widget_fact_text, factText)
                    setTextViewText(R.id.widget_fact_source, sourceText)
                    setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
                    setOnClickPendingIntent(R.id.widget_btn_next, nextPendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        suspend fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, FactAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            }
        }
    }
}

package com.factswallpaper.app

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.factswallpaper.core.wallpaper.WallpaperRotationController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NextFactTileService : TileService() {

    @Inject
    lateinit var rotationController: WallpaperRotationController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = getString(R.string.quick_settings_next_fact)
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            rotationController.advance()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

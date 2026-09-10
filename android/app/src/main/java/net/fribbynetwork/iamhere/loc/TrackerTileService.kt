package net.fribbynetwork.iamhere.loc

import android.content.Context
import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.SettingsStore
import net.fribbynetwork.iamhere.util.withLocale

/** Riquadro nelle Impostazioni Rapide: avvia e ferma senza aprire l'app. */
class TrackerTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    /** L'etichetta segue la lingua scelta nell'app, non quella di sistema. */
    private fun refresh() {
        scope.launch {
            val lang = runCatching { SettingsStore(applicationContext).current().language }.getOrNull()
            val ctx: Context = lang?.let { applicationContext.withLocale(it) } ?: applicationContext
            val running = TrackerState.state.value.running
            withContext(Dispatchers.Main) {
                val t: Tile = qsTile ?: return@withContext
                t.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                t.label = ctx.getString(if (running) R.string.sharing_active else R.string.tile_idle)
                t.updateTile()
            }
        }
    }

    override fun onClick() {
        val running = TrackerState.state.value.running
        val i = Intent(this, TrackingService::class.java).apply {
            action = if (running) TrackingService.ACTION_STOP else TrackingService.ACTION_START
        }
        runCatching {
            if (running) startService(i) else ContextCompat.startForegroundService(this, i)
        }
        refresh()
    }
}

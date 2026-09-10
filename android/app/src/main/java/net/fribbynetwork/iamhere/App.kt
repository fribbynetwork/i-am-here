package net.fribbynetwork.iamhere

import android.app.Application
import net.fribbynetwork.iamhere.work.FlushWorker
import org.maplibre.android.MapLibre

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // MapLibre non richiede chiave qui: la chiave sta nell'URL dello stile.
        MapLibre.getInstance(this)
        FlushWorker.schedule(this)
    }
}

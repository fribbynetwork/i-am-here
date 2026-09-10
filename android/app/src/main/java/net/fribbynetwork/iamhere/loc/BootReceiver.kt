package net.fribbynetwork.iamhere.loc

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.fribbynetwork.iamhere.data.Db

/**
 * Se il telefono si riavvia a meta viaggio, il viaggio aperto viene ripreso
 * mantenendo lo stesso id, cosi il server continua ad accodare sullo stesso file.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val open = Db.get(app).trips().openTrip() ?: return@launch
                val i = Intent(app, TrackingService::class.java).apply {
                    this.action = TrackingService.ACTION_RESUME
                    putExtra(TrackingService.EXTRA_TRIP_ID, open.id)
                }
                ContextCompat.startForegroundService(app, i)
            } finally {
                pending.finish()
            }
        }
    }
}

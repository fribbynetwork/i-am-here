package net.fribbynetwork.iamhere.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.Db
import net.fribbynetwork.iamhere.data.SettingsStore
import net.fribbynetwork.iamhere.net.Sender
import net.fribbynetwork.iamhere.util.withLocale

/**
 * Riprova la coda quando la rete torna, anche a viaggio finito.
 * WorkManager funziona senza Play Services.
 */
class FlushWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val db = Db.get(applicationContext)
        val prefs = SettingsStore(applicationContext).current()
        if (!prefs.endpointEnabled) return Result.success()
        val loc = applicationContext.withLocale(prefs.language)

        val pending = db.samples().pending(200)
        if (pending.isEmpty()) return Result.success()

        for (s in pending) {
            val r = Sender.sendHttp(loc, prefs, s, s.event, loc.getString(R.string.dest_free_endpoint))
            if (r.ok) {
                db.samples().update(
                    s.copy(sentAt = System.currentTimeMillis(), attempts = s.attempts + 1, lastError = null)
                )
            } else {
                db.samples().update(s.copy(attempts = s.attempts + 1, lastError = r.detail))
                // Ci fermiamo al primo errore: l'ordine cronologico sul
                // server vale piu di qualche invio in piu.
                return Result.retry()
            }
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "flush-queue"

        fun schedule(context: Context) {
            val req = OneTimeWorkRequestBuilder<FlushWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, req)
        }
    }
}

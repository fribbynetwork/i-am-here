package net.fribbynetwork.iamhere.loc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.fribbynetwork.iamhere.MainActivity
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.Db
import net.fribbynetwork.iamhere.data.Prefs
import net.fribbynetwork.iamhere.data.Sample
import net.fribbynetwork.iamhere.data.SendMode
import net.fribbynetwork.iamhere.data.SettingsStore
import net.fribbynetwork.iamhere.data.Trip
import net.fribbynetwork.iamhere.net.Sender
import net.fribbynetwork.iamhere.util.batteryInfo
import net.fribbynetwork.iamhere.util.fmtElapsed
import net.fribbynetwork.iamhere.util.fmtMeters
import net.fribbynetwork.iamhere.util.hasNetwork
import net.fribbynetwork.iamhere.util.networkType
import net.fribbynetwork.iamhere.util.withLocale
import java.util.concurrent.Executors

class TrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sendLock = Mutex()
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var engine: LocationEngine
    private lateinit var settings: SettingsStore
    private lateinit var db: Db

    private var prefs: Prefs = Prefs()
    /** Il servizio non passa da Compose: le stringhe della notifica
     *  vanno lette da un contesto con la lingua scelta dall'utente.
     *  Non chiamarlo "loc": in onFix e sendSample quel nome e gia il
     *  parametro Location, e verrebbe ombreggiato. */
    private var i18n: Context = this
    private var tripId: Long = 0
    private var startedAtMs: Long = 0
    private var wakeLock: PowerManager.WakeLock? = null

    private var lastAcceptedAt: Long = 0
    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLon: Double? = null
    private var lastSmsAt: Long = 0
    /** Link accorciato del viaggio. Con YOURLS impostato su "solo alla
     *  partenza" si calcola una volta e vale per tutti i messaggi. */
    private var linkBreve: String? = null
    private var started = false          // il primo punto e gia partito?
    private var armed = false            // uscito almeno una volta dal raggio?
    private var finishing = false
    private var sentCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = LocationEngine(this)
        settings = SettingsStore(this)
        db = Db.get(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                scope.launch { finalizeAndStop(getString(R.string.reason_manual)) }
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                val id = intent.getLongExtra(EXTRA_TRIP_ID, 0L)
                if (!startForegroundNow()) return START_NOT_STICKY
                scope.launch { begin(resumeTripId = id) }
            }
            else -> {
                if (!startForegroundNow()) return START_NOT_STICKY
                scope.launch { begin(resumeTripId = null) }
            }
        }
        return START_STICKY
    }

    /**
     * Da Android 14 avviare un servizio di tipo location senza il permesso
     * gia concesso solleva SecurityException e l'app muore. Meglio fallire
     * in modo pulito con una notifica leggibile.
     */
    private fun startForegroundNow(): Boolean {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        return try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(i18n.getString(R.string.notif_starting), null), type)
            true
        } catch (e: Exception) {
            TrackerState.reset(getString(R.string.reason_permissions))
            stopSelf()
            false
        }
    }

    private suspend fun begin(resumeTripId: Long?) {
        prefs = settings.current()
        i18n = withLocale(prefs.language)

        // L'id del viaggio e il timestamp unix di inizio: lo conosciamo
        // subito, non serve nessuna risposta dal server.
        tripId = resumeTripId ?: (System.currentTimeMillis() / 1000L)
        startedAtMs = tripId * 1000L

        if (resumeTripId == null) {
            db.trips().insert(
                Trip(
                    id = tripId,
                    startedAt = startedAtMs,
                    destName = prefs.destName.ifBlank { null },
                    destLat = if (prefs.hasDestination) prefs.destLat else null,
                    destLon = if (prefs.hasDestination) prefs.destLon else null,
                    radius = if (prefs.hasDestination) prefs.destRadius else null
                )
            )
        } else {
            started = true
        }

        linkBreve = null   // viaggio nuovo, link nuovo
        acquireWakeLock()

        TrackerState.update {
            it.copy(
                running = true,
                tripId = tripId,
                startedAt = startedAtMs,
                destName = prefs.destName.ifBlank { null },
                destRadius = prefs.destRadius,
                sentCount = 0,
                endReason = null
            )
        }

        engine.start(minTimeMs = (prefs.intervalSec * 1000L) / 2, executor = executor) { loc ->
            scope.launch { onFix(loc) }
        }

        engine.lastKnown()?.let { last ->
            if (System.currentTimeMillis() - last.time < 120_000L) {
                scope.launch { onFix(last) }
            }
        }
        updateNotification()
    }

    private suspend fun onFix(loc: Location) {
        if (finishing) return

        val destDist: Double? = if (prefs.hasDestination)
            LocationEngine.distance(loc.latitude, loc.longitude, prefs.destLat, prefs.destLon)
        else null

        TrackerState.update {
            it.copy(
                lat = loc.latitude, lon = loc.longitude,
                accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                speed = if (loc.hasSpeed()) loc.speed else null,
                altitude = LocationEngine.altitudeOf(loc),
                satTotal = engine.satTotal, satUsed = engine.satUsed,
                distToDest = destDist, fixAt = System.currentTimeMillis()
            )
        }

        // --- arrivo -------------------------------------------------------
        // Basta essere dentro l'area con un errore piu piccolo dell'area
        // stessa. L'armamento serve solo a non dichiarare arrivo se la
        // partenza avviene gia dentro il raggio.
        if (destDist != null) {
            val radius = prefs.destRadius.toDouble()
            if (destDist > radius) armed = true
            val accOk = !loc.hasAccuracy() || loc.accuracy <= radius
            if (armed && destDist <= radius && accOk) {
                sendSample(loc, destDist, "end")
                finish(i18n.getString(R.string.reason_arrival))
                return
            }
        }

        // --- soglia batteria ---------------------------------------------
        val bat = batteryInfo().percent
        if (prefs.batteryStopPercent > 0 && bat != null && bat <= prefs.batteryStopPercent) {
            sendSample(loc, destDist, "end")
            finish(i18n.getString(R.string.reason_battery))
            return
        }

        // --- durata massima ----------------------------------------------
        if (prefs.maxTripMinutes > 0 &&
            System.currentTimeMillis() - startedAtMs > prefs.maxTripMinutes * 60_000L
        ) {
            sendSample(loc, destDist, "end")
            finish(i18n.getString(R.string.reason_timeout))
            return
        }

        // --- cadenza ------------------------------------------------------
        val now = System.currentTimeMillis()
        if (!started) {
            sendSample(loc, destDist, "start")
            started = true
            lastAcceptedAt = now
            lastAcceptedLat = loc.latitude
            lastAcceptedLon = loc.longitude
            updateNotification()
            return
        }

        val interval = effectiveInterval(destDist, if (loc.hasSpeed()) loc.speed else null)
        val elapsed = now - lastAcceptedAt
        if (elapsed < interval) return

        val moved = if (lastAcceptedLat != null && lastAcceptedLon != null)
            LocationEngine.distance(lastAcceptedLat!!, lastAcceptedLon!!, loc.latitude, loc.longitude)
        else Double.MAX_VALUE

        // Ibrido: serve il tempo minimo E lo spostamento minimo, ma dopo
        // quattro intervalli fermi si trasmette lo stesso, per non far
        // sparire dal tracciato chi e in coda o in sosta.
        val heartbeat = elapsed >= interval * 4
        if (moved < prefs.minDistanceM && !heartbeat) return

        sendSample(loc, destDist, "track")
        lastAcceptedAt = now
        lastAcceptedLat = loc.latitude
        lastAcceptedLon = loc.longitude
        updateNotification()
    }

    /** Intervallo adattivo: si stringe in prossimita della meta, si allarga da fermi. */
    private fun effectiveInterval(dist: Double?, speed: Float?): Long {
        val base = prefs.intervalSec * 1000L
        if (!prefs.adaptive) return base
        var v = base
        if (dist != null) {
            v = when {
                dist > 5000 -> base * 2
                dist > 1000 -> base
                dist > 300 -> base / 2
                else -> minOf(base / 4, 15_000L)
            }
        }
        if (speed != null && speed < 0.5f) v = maxOf(v, base * 2)
        return v.coerceAtLeast(5_000L)
    }

    private suspend fun sendSample(loc: Location, destDist: Double?, event: String) {
        val b = batteryInfo()
        val sample = Sample(
            tripId = tripId,
            event = event,
            fixTime = loc.time / 1000L,
            lat = loc.latitude,
            lon = loc.longitude,
            alt = LocationEngine.altitudeOf(loc),
            acc = if (loc.hasAccuracy()) loc.accuracy else null,
            vacc = LocationEngine.verticalAccuracyOf(loc),
            speed = if (loc.hasSpeed()) loc.speed else null,
            bearing = if (loc.hasBearing()) loc.bearing else null,
            provider = loc.provider,
            satTotal = engine.satTotal,
            satUsed = engine.satUsed,
            battery = b.percent,
            charging = b.charging,
            network = networkType(),
            pressure = engine.pressure,
            distToDest = destDist,
            destName = prefs.destName.ifBlank { null }
        )

        val wantEndpoint = prefs.endpointEnabled &&
            (prefs.endpointMode == SendMode.ALWAYS || event != "track")
        val wantSms = prefs.smsEnabled &&
            (prefs.smsMode == SendMode.ALWAYS || event != "track")

        if (wantEndpoint) {
            db.samples().insert(sample)
            flushQueue()
        }

        if (wantSms) {
            val now = System.currentTimeMillis()
            val throttled = event == "track" &&
                now - lastSmsAt < prefs.smsMinIntervalSec * 1000L
            if (!throttled) {
                if (prefs.yourlsTemplate.isNotBlank() &&
                    (linkBreve == null || prefs.yourlsOgniMessaggio)
                ) {
                    linkBreve = Sender.linkPerSms(prefs, sample)
                }
                val r = Sender.sendSms(this, prefs, sample, event, linkBreve.orEmpty())
                lastSmsAt = now
                TrackerState.update { it.copy(lastSmsAt = now) }
                if (!r.ok) {
                    TrackerState.update { it.copy(lastResult = "SMS: " + r.detail, lastResultOk = false) }
                }
            }
        }
    }

    /**
     * Svuota la coda in ordine cronologico. Se un invio fallisce ci si ferma:
     * mandare avanti i successivi rovinerebbe l'ordine sul server.
     */
    private suspend fun flushQueue() = sendLock.withLock {
        if (!hasNetwork()) {
            TrackerState.update { it.copy(lastResult = i18n.getString(R.string.queued_no_network), lastResultOk = false) }
            return@withLock
        }
        val pending = db.samples().pending(200)
        for (s in pending) {
            val r = Sender.sendHttp(i18n, prefs, s, s.event)
            if (r.ok) {
                db.samples().update(s.copy(sentAt = System.currentTimeMillis(), attempts = s.attempts + 1, lastError = null))
                sentCount++
                TrackerState.update { it.copy(sentCount = sentCount, lastResult = r.detail, lastResultOk = true) }
            } else {
                db.samples().update(s.copy(attempts = s.attempts + 1, lastError = r.detail))
                TrackerState.update { it.copy(lastResult = r.detail, lastResultOk = false) }
                break
            }
        }
    }

    /**
     * Chiusura con ultimo invio. Se il service e stato ricreato solo per
     * ricevere lo stop, le preferenze e il viaggio aperto vengono
     * recuperati prima di comporre il punto finale.
     */
    private suspend fun finalizeAndStop(reason: String) {
        if (finishing) return
        runCatching {
            if (tripId == 0L) {
                prefs = settings.current()
                i18n = withLocale(prefs.language)
                tripId = db.trips().openTrip()?.id ?: (System.currentTimeMillis() / 1000L)
            }
            val loc = engine.lastKnown()
            if (loc != null) {
                val d = if (prefs.hasDestination)
                    LocationEngine.distance(loc.latitude, loc.longitude, prefs.destLat, prefs.destLon)
                else null
                sendSample(loc, d, "end")
            }
        }
        finish(reason)
    }

    private fun finish(reason: String) {
        if (finishing) return
        finishing = true
        scope.launch {
            runCatching {
                val t = db.trips().openTrip()
                if (t != null) {
                    db.trips().update(t.copy(endedAt = System.currentTimeMillis(), endReason = reason))
                }
                flushQueue()
            }
            TrackerState.reset(reason)
            stopSelfSafely()
        }
    }

    private fun stopSelfSafely() {
        engine.stop()
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---------------------------------------------------------------- notifica

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL, getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW)
        ch.description = getString(R.string.notif_channel_desc)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    private fun buildNotification(line1: String, line2: String?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_tracker)
            .setContentTitle(line1)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, i18n.getString(R.string.notif_stop), stop)
        if (line2 != null) b.setContentText(line2).setStyle(NotificationCompat.BigTextStyle().bigText(line2))
        return b.build()
    }

    private fun updateNotification() {
        val s = TrackerState.state.value
        val elapsed = fmtElapsed(System.currentTimeMillis() - startedAtMs)
        val title = if (s.destName != null) i18n.getString(R.string.notif_heading_to, s.destName)
        else i18n.getString(R.string.notif_free)
        val parts = mutableListOf(
            i18n.getString(R.string.notif_active_for, elapsed),
            i18n.getString(R.string.notif_sends, s.sentCount)
        )
        s.distToDest?.let { parts.add(i18n.getString(R.string.notif_left, fmtMeters(it))) }
        s.lastResult?.let { parts.add(it) }
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(title, parts.joinToString(" · ")))
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "tracker:trip").apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        engine.stop()
        releaseWakeLock()
        executor.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "tracking"
        const val NOTIF_ID = 1001
        const val ACTION_START = "net.fribbynetwork.iamhere.START"
        const val ACTION_STOP = "net.fribbynetwork.iamhere.STOP"
        const val ACTION_RESUME = "net.fribbynetwork.iamhere.RESUME"
        const val EXTRA_TRIP_ID = "tripId"
    }
}

package net.fribbynetwork.iamhere.loc

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.Executor

/**
 * Sorgente di posizione basata solo su AOSP: niente Play Services,
 * niente FusedLocationProvider. Funziona su qualsiasi dispositivo,
 * anche senza servizi Google.
 */
class LocationEngine(private val context: Context) : SensorEventListener {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    var satTotal: Int? = null; private set
    var satUsed: Int? = null; private set
    var pressure: Float? = null; private set

    private var onFix: ((Location) -> Unit)? = null
    private var gnssCallback: GnssStatusCompat.Callback? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix?.invoke(location)
        }
        // Necessari su API vecchie: senza queste implementazioni alcune
        // ROM sollevano AbstractMethodError.
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun gpsEnabled(): Boolean = try {
        lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (e: Exception) {
        false
    }

    @SuppressLint("MissingPermission")
    fun start(minTimeMs: Long, executor: Executor, onFix: (Location) -> Unit) {
        this.onFix = onFix

        // Chiediamo aggiornamenti piu fitti dell'intervallo di invio e
        // filtriamo noi: cosi il controllo su tempo + distanza minima resta
        // nostro e non dipende dall'interpretazione della singola ROM.
        val t = minTimeMs.coerceIn(2000L, 30_000L)

        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, t, 0f, listener, context.mainLooper)
        }
        if (lm.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
            runCatching {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, t * 2, 0f, listener, context.mainLooper)
            }
        }

        val cb = object : GnssStatusCompat.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
                var used = 0
                for (i in 0 until status.satelliteCount) {
                    if (status.usedInFix(i)) used++
                }
                satTotal = status.satelliteCount
                satUsed = used
            }
        }
        gnssCallback = cb
        runCatching { LocationManagerCompat.registerGnssStatusCallback(lm, executor, cb) }

        sm?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
        gnssCallback?.let { runCatching { LocationManagerCompat.unregisterGnssStatusCallback(lm, it) } }
        gnssCallback = null
        sm?.unregisterListener(this)
        onFix = null
    }

    /**
     * Un solo fix, senza avviare il tracciamento. Serve a centrare la
     * mappa sulla posizione dell'utente quando si crea un punto.
     */
    /**
     * Richiesta a singolo colpo, scritta a mano invece di usare
     * LocationManagerCompat.getCurrentLocation: quella esiste in due
     * varianti che differiscono solo per il tipo del CancellationSignal,
     * e passando null il compilatore non sa quale scegliere.
     *
     * Il risultato arriva sul thread principale. Se entro il timeout non
     * si aggancia nulla, si risponde comunque con null invece di lasciare
     * un ascoltatore acceso a consumare batteria.
     */
    @SuppressLint("MissingPermission")
    fun currentFix(onResult: (Location?) -> Unit) {
        val provider = when {
            runCatching { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            lm.allProviders.contains(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            else -> null
        } ?: run { onResult(null); return }

        var consegnato = false
        val handler = Handler(context.mainLooper)

        val unaVolta = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (consegnato) return
                consegnato = true
                runCatching { lm.removeUpdates(this) }
                onResult(location)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val avviato = runCatching {
            lm.requestLocationUpdates(provider, 0L, 0f, unaVolta, context.mainLooper)
        }.isSuccess

        if (!avviato) {
            onResult(null)
            return
        }

        handler.postDelayed({
            if (!consegnato) {
                consegnato = true
                runCatching { lm.removeUpdates(unaVolta) }
                onResult(null)
            }
        }, FIX_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    fun lastKnown(): Location? {
        val candidates = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        )
        return candidates.maxByOrNull { it.time }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) pressure = event.values.firstOrNull()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val FIX_TIMEOUT_MS = 20_000L

        /** Altitudine sul livello del mare, disponibile da Android 14. */
        fun altitudeOf(l: Location): Double? = when {
            Build.VERSION.SDK_INT >= 34 && l.hasMslAltitude() -> l.mslAltitudeMeters
            l.hasAltitude() -> l.altitude
            else -> null
        }

        fun verticalAccuracyOf(l: Location): Float? =
            if (l.hasVerticalAccuracy()) l.verticalAccuracyMeters else null

        fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = FloatArray(1)
            Location.distanceBetween(lat1, lon1, lat2, lon2, r)
            return r[0].toDouble()
        }
    }
}

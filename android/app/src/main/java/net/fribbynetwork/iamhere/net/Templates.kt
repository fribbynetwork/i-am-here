package net.fribbynetwork.iamhere.net

import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.Sample
import java.net.URLEncoder
import java.util.Locale

/**
 * Motore dei segnaposto.
 *
 * Un template e una stringa qualunque in cui {nome} viene sostituito.
 * I segnaposto sconosciuti restano nel testo come sono scritti, cosi
 * un errore di battitura si vede subito invece di sparire.
 */
object Templates {

    enum class Mode { URL, RAW, JSON }

    /** Elenco mostrato nella schermata impostazioni: chiave e id della descrizione. */
    val CATALOG: List<Pair<String, Int>> = listOf(
        "lat" to R.string.ph_lat,
        "lon" to R.string.ph_lon,
        "timestamp" to R.string.ph_timestamp,
        "tempo" to R.string.ph_tempo,
        "tempoiso" to R.string.ph_tempoiso,
        "evento" to R.string.ph_evento,
        "alt" to R.string.ph_alt,
        "acc" to R.string.ph_acc,
        "vacc" to R.string.ph_vacc,
        "vel" to R.string.ph_vel,
        "velkmh" to R.string.ph_velkmh,
        "dir" to R.string.ph_dir,
        "prov" to R.string.ph_prov,
        "sat" to R.string.ph_sat,
        "satuso" to R.string.ph_satuso,
        "bat" to R.string.ph_bat,
        "carica" to R.string.ph_carica,
        "rete" to R.string.ph_rete,
        "press" to R.string.ph_press,
        "dist" to R.string.ph_dist,
        "dest" to R.string.ph_dest,
        "ritardo" to R.string.ph_ritardo,
        "auth" to R.string.ph_auth,
        "yourls" to R.string.ph_yourls
    )

    /**
     * Il codice che apre un singolo viaggio sul server di I am here.
     * Deriva dall'id del viaggio e dal segreto condiviso con il server,
     * che lo ricalcola allo stesso modo per verificarlo.
     */
    fun codiceViaggio(tripId: Long, segreto: String): String {
        if (segreto.isBlank()) return ""
        return try {
            val mac = javax.crypto.Mac.getInstance("HmacSHA256")
            mac.init(javax.crypto.spec.SecretKeySpec(segreto.toByteArray(), "HmacSHA256"))
            mac.doFinal(tripId.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(16)
        } catch (e: Exception) {
            ""
        }
    }

    /** Le prime cifre dell'impronta del segreto: le stesse che mostra il
     *  server, per controllare di aver incollato lo stesso valore senza
     *  doverlo rivelare. */
    fun impronta(segreto: String): String {
        if (segreto.isBlank()) return ""
        return try {
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(segreto.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(6)
        } catch (e: Exception) {
            ""
        }
    }

    fun render(template: String, values: Map<String, String>, mode: Mode): String {
        val out = StringBuilder(template.length + 64)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{') {
                val close = template.indexOf('}', i + 1)
                if (close > i) {
                    val key = template.substring(i + 1, close).trim().lowercase(Locale.US)
                    val v = values[key]
                    if (v != null) {
                        out.append(escape(v, mode))
                        i = close + 1
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun escape(s: String, mode: Mode): String = when (mode) {
        Mode.URL -> URLEncoder.encode(s, "UTF-8")
        Mode.RAW -> s
        Mode.JSON -> s.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    }

    /**
     * Costruisce la mappa dei valori.
     * Locale.US e obbligatorio: con la locale italiana il separatore
     * decimale diventa la virgola e il server riceve coordinate rotte.
     */
    fun valuesOf(
        s: Sample,
        decimals: Int,
        sentAt: Long = System.currentTimeMillis(),
        segreto: String = "",
        yourls: String = ""
    ): Map<String, String> {
        val f = "%.${decimals.coerceIn(0, 9)}f"
        val m = HashMap<String, String>(32)
        m["lat"] = String.format(Locale.US, f, s.lat)
        m["lon"] = String.format(Locale.US, f, s.lon)
        m["timestamp"] = s.tripId.toString()
        m["tempo"] = s.fixTime.toString()
        m["tempoiso"] = iso8601(s.fixTime)
        m["evento"] = s.event
        m["alt"] = s.alt?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        m["acc"] = s.acc?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        m["vacc"] = s.vacc?.let { String.format(Locale.US, "%.1f", it) } ?: ""
        m["vel"] = s.speed?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        m["velkmh"] = s.speed?.let { String.format(Locale.US, "%.1f", it * 3.6f) } ?: ""
        m["dir"] = s.bearing?.let { String.format(Locale.US, "%.0f", it) } ?: ""
        m["prov"] = s.provider ?: ""
        m["sat"] = s.satTotal?.toString() ?: ""
        m["satuso"] = s.satUsed?.toString() ?: ""
        m["bat"] = s.battery?.toString() ?: ""
        m["carica"] = when (s.charging) { true -> "1"; false -> "0"; null -> "" }
        m["rete"] = s.network ?: ""
        m["press"] = s.pressure?.let { String.format(Locale.US, "%.2f", it) } ?: ""
        m["dist"] = s.distToDest?.let { String.format(Locale.US, "%.0f", it) } ?: ""
        m["dest"] = s.destName ?: ""
        m["ritardo"] = ((sentAt / 1000L) - s.fixTime).coerceAtLeast(0L).toString()
        m["auth"] = codiceViaggio(s.tripId, segreto)
        m["yourls"] = yourls
        return m
    }

    private fun iso8601(unixSeconds: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return sdf.format(java.util.Date(unixSeconds * 1000L))
    }

    /** Rilevazione fittizia per il pulsante "Prova invio". */
    fun demoSample(event: String = "start") = Sample(
        tripId = System.currentTimeMillis() / 1000L,
        event = event,
        fixTime = System.currentTimeMillis() / 1000L,
        lat = 44.493772, lon = 11.343093,
        alt = 54.0, acc = 8.0f, vacc = 12.0f,
        speed = 13.4f, bearing = 271f, provider = "gps",
        satTotal = 22, satUsed = 11,
        battery = 76, charging = false, network = "mobile",
        pressure = 1013.25f, distToDest = 4210.0, destName = "Test"
    )
}

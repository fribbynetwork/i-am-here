package net.fribbynetwork.iamhere.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.fribbynetwork.iamhere.ui.theme.ThemeMode

enum class HttpMethod { GET, POST_FORM, POST_JSON }

enum class LangMode { SYSTEM, EN, IT }

/** Quando trasmettere: solo agli estremi del viaggio, oppure anche lungo il tragitto. */
enum class SendMode { START_END, ALWAYS }

val Context.dataStore by preferencesDataStore("prefs")

object Defaults {
    const val URL =
        "https://example.org/track.php?key=YOUR_KEY&lat={lat}&lon={lon}&id={timestamp}&t={tempo}"
    const val SMS_START =
        "{evento} https://www.openstreetmap.org/?mlat={lat}&mlon={lon}#map=16/{lat}/{lon}"
    const val SMS_END =
        "{evento} https://www.openstreetmap.org/?mlat={lat}&mlon={lon}#map=16/{lat}/{lon}"
}

data class Prefs(
    // --- canali ---
    val endpointEnabled: Boolean = true,
    val smsEnabled: Boolean = false,
    val endpointMode: SendMode = SendMode.ALWAYS,
    val smsMode: SendMode = SendMode.START_END,

    // --- endpoint ---
    val method: HttpMethod = HttpMethod.GET,
    val urlStart: String = Defaults.URL,
    val urlTrack: String = "",
    val urlEnd: String = "",
    val headers: String = "",
    /** Consente endpoint http:// in chiaro. Spento di default. */
    val allowCleartext: Boolean = false,
    /** Accetta certificati TLS non validi o autofirmati. Spento di default. */
    val allowInsecureTls: Boolean = false,

    // --- sms ---
    val smsRecipients: String = "",
    val smsSubscriptionId: Int = -1,
    val smsStart: String = Defaults.SMS_START,
    val smsTrack: String = "",
    val smsEnd: String = Defaults.SMS_END,
    val smsMinIntervalSec: Int = 300,

    // --- server I am here (sezione avanzata) ---
    /** Lo stesso segreto impostato nel settings.php del server. Serve a
     *  calcolare {auth}, il codice che apre un singolo viaggio. */
    val tokenSecret: String = "",

    // --- YOURLS (sezione avanzata) ---
    val yourlsEnabled: Boolean = false,
    val yourlsApi: String = "",
    val yourlsToken: String = "",
    val yourlsTemplate: String = "",
    /** false = un solo link per viaggio, calcolato alla partenza.
     *  true  = un link nuovo a ogni messaggio, con la posizione di quel momento. */
    val yourlsOgniMessaggio: Boolean = false,

    // --- aggiornamenti ---
    /** Ogni quanti giorni controllare. Zero significa mai, ed e il valore
     *  di partenza: il controllo esce in rete, quindi lo si accende. */
    val controlloGiorni: Int = 0,
    val ultimoControllo: Long = 0L,
    /** L'ultima versione vista, per poterla mostrare senza ricontrollare. */
    val versioneTrovata: String = "",
    val codiceTrovato: Int = 0,
    val novitaTrovate: String = "",

    // --- cadenza ---
    val intervalSec: Int = 60,
    val minDistanceM: Int = 100,
    val adaptive: Boolean = true,

    // --- viaggio ---
    val defaultRadius: Int = 100,
    val maxTripMinutes: Int = 0,          // 0 = nessun limite
    val batteryStopPercent: Int = 0,      // 0 = trasmetti fino allo spegnimento
    val coordDecimals: Int = 6,

    // --- mappa ---
    val mapStyleUrl: String = "",

    // --- aspetto ---
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val language: LangMode = LangMode.SYSTEM,

    // --- destinazione attiva (nessuna = condivisione libera) ---
    val destPoiId: Long = -1L,
    val destName: String = "",
    val destLat: Double = 0.0,
    val destLon: Double = 0.0,
    val destRadius: Int = 0
) {
    val hasDestination: Boolean get() = destRadius > 0 && (destLat != 0.0 || destLon != 0.0)

    fun urlFor(event: String): String = when (event) {
        "track" -> urlTrack.ifBlank { urlStart }
        "end" -> urlEnd.ifBlank { urlStart }
        else -> urlStart
    }

    fun smsFor(event: String): String = when (event) {
        "track" -> smsTrack.ifBlank { smsStart }
        "end" -> smsEnd.ifBlank { smsStart }
        else -> smsStart
    }
}

private object K {
    val endpointEnabled = booleanPreferencesKey("endpointEnabled")
    val smsEnabled = booleanPreferencesKey("smsEnabled")
    val endpointMode = stringPreferencesKey("endpointMode")
    val smsMode = stringPreferencesKey("smsMode")
    val method = stringPreferencesKey("method")
    val urlStart = stringPreferencesKey("urlStart")
    val urlTrack = stringPreferencesKey("urlTrack")
    val urlEnd = stringPreferencesKey("urlEnd")
    val headers = stringPreferencesKey("headers")
    val allowCleartext = booleanPreferencesKey("allowCleartext")
    val allowInsecureTls = booleanPreferencesKey("allowInsecureTls")
    val smsRecipients = stringPreferencesKey("smsRecipients")
    val smsSubscriptionId = intPreferencesKey("smsSubscriptionId")
    val smsStart = stringPreferencesKey("smsStart")
    val smsTrack = stringPreferencesKey("smsTrack")
    val smsEnd = stringPreferencesKey("smsEnd")
    val smsMinIntervalSec = intPreferencesKey("smsMinIntervalSec")
    val tokenSecret = stringPreferencesKey("tokenSecret")
    val yourlsEnabled = booleanPreferencesKey("yourlsEnabled")
    val yourlsApi = stringPreferencesKey("yourlsApi")
    val yourlsToken = stringPreferencesKey("yourlsToken")
    val yourlsTemplate = stringPreferencesKey("yourlsTemplate")
    val yourlsOgniMessaggio = booleanPreferencesKey("yourlsOgniMessaggio")
    val controlloGiorni = intPreferencesKey("controlloGiorni")
    val ultimoControllo = longPreferencesKey("ultimoControllo")
    val versioneTrovata = stringPreferencesKey("versioneTrovata")
    val codiceTrovato = intPreferencesKey("codiceTrovato")
    val novitaTrovate = stringPreferencesKey("novitaTrovate")
    val intervalSec = intPreferencesKey("intervalSec")
    val minDistanceM = intPreferencesKey("minDistanceM")
    val adaptive = booleanPreferencesKey("adaptive")
    val defaultRadius = intPreferencesKey("defaultRadius")
    val maxTripMinutes = intPreferencesKey("maxTripMinutes")
    val batteryStopPercent = intPreferencesKey("batteryStopPercent")
    val coordDecimals = intPreferencesKey("coordDecimals")
    val mapStyleUrl = stringPreferencesKey("mapStyleUrl")
    val theme = stringPreferencesKey("theme")
    val language = stringPreferencesKey("language")
    val destPoiId = longPreferencesKey("destPoiId")
    val destName = stringPreferencesKey("destName")
    val destLat = stringPreferencesKey("destLat")
    val destLon = stringPreferencesKey("destLon")
    val destRadius = intPreferencesKey("destRadius")
}

class SettingsStore(private val context: Context) {

    val flow: Flow<Prefs> = context.dataStore.data.map { p -> read(p) }

    private fun read(p: Preferences): Prefs {
        val d = Prefs()
        return Prefs(
            endpointEnabled = p[K.endpointEnabled] ?: d.endpointEnabled,
            smsEnabled = p[K.smsEnabled] ?: d.smsEnabled,
            endpointMode = enumOr(p[K.endpointMode], d.endpointMode),
            smsMode = enumOr(p[K.smsMode], d.smsMode),
            method = methodOr(p[K.method], d.method),
            urlStart = p[K.urlStart] ?: d.urlStart,
            urlTrack = p[K.urlTrack] ?: d.urlTrack,
            urlEnd = p[K.urlEnd] ?: d.urlEnd,
            headers = p[K.headers] ?: d.headers,
            allowCleartext = p[K.allowCleartext] ?: d.allowCleartext,
            allowInsecureTls = p[K.allowInsecureTls] ?: d.allowInsecureTls,
            smsRecipients = p[K.smsRecipients] ?: d.smsRecipients,
            smsSubscriptionId = p[K.smsSubscriptionId] ?: d.smsSubscriptionId,
            smsStart = p[K.smsStart] ?: d.smsStart,
            smsTrack = p[K.smsTrack] ?: d.smsTrack,
            smsEnd = p[K.smsEnd] ?: d.smsEnd,
            smsMinIntervalSec = p[K.smsMinIntervalSec] ?: d.smsMinIntervalSec,
            tokenSecret = p[K.tokenSecret] ?: d.tokenSecret,
            yourlsEnabled = p[K.yourlsEnabled] ?: d.yourlsEnabled,
            yourlsApi = p[K.yourlsApi] ?: d.yourlsApi,
            yourlsToken = p[K.yourlsToken] ?: d.yourlsToken,
            yourlsTemplate = p[K.yourlsTemplate] ?: d.yourlsTemplate,
            yourlsOgniMessaggio = p[K.yourlsOgniMessaggio] ?: d.yourlsOgniMessaggio,
            controlloGiorni = p[K.controlloGiorni] ?: d.controlloGiorni,
            ultimoControllo = p[K.ultimoControllo] ?: d.ultimoControllo,
            versioneTrovata = p[K.versioneTrovata] ?: d.versioneTrovata,
            codiceTrovato = p[K.codiceTrovato] ?: d.codiceTrovato,
            novitaTrovate = p[K.novitaTrovate] ?: d.novitaTrovate,
            intervalSec = p[K.intervalSec] ?: d.intervalSec,
            minDistanceM = p[K.minDistanceM] ?: d.minDistanceM,
            adaptive = p[K.adaptive] ?: d.adaptive,
            defaultRadius = p[K.defaultRadius] ?: d.defaultRadius,
            maxTripMinutes = p[K.maxTripMinutes] ?: d.maxTripMinutes,
            batteryStopPercent = p[K.batteryStopPercent] ?: d.batteryStopPercent,
            coordDecimals = p[K.coordDecimals] ?: d.coordDecimals,
            mapStyleUrl = p[K.mapStyleUrl] ?: d.mapStyleUrl,
            theme = themeOr(p[K.theme], d.theme),
            language = langOr(p[K.language], d.language),
            destPoiId = p[K.destPoiId] ?: d.destPoiId,
            destName = p[K.destName] ?: d.destName,
            destLat = p[K.destLat]?.toDoubleOrNull() ?: d.destLat,
            destLon = p[K.destLon]?.toDoubleOrNull() ?: d.destLon,
            destRadius = p[K.destRadius] ?: d.destRadius
        )
    }

    private fun enumOr(v: String?, def: SendMode) =
        runCatching { SendMode.valueOf(v!!) }.getOrDefault(def)

    private fun methodOr(v: String?, def: HttpMethod) =
        runCatching { HttpMethod.valueOf(v!!) }.getOrDefault(def)

    private fun themeOr(v: String?, def: ThemeMode) =
        runCatching { ThemeMode.valueOf(v!!) }.getOrDefault(def)

    private fun langOr(v: String?, def: LangMode) =
        runCatching { LangMode.valueOf(v!!) }.getOrDefault(def)

    /** Lettura sincrona una tantum, per il service. */
    suspend fun current(): Prefs = flow.first()

    suspend fun save(p: Prefs) {
        context.dataStore.edit { e ->
            e[K.endpointEnabled] = p.endpointEnabled
            e[K.smsEnabled] = p.smsEnabled
            e[K.endpointMode] = p.endpointMode.name
            e[K.smsMode] = p.smsMode.name
            e[K.method] = p.method.name
            e[K.urlStart] = p.urlStart
            e[K.urlTrack] = p.urlTrack
            e[K.urlEnd] = p.urlEnd
            e[K.headers] = p.headers
            e[K.allowCleartext] = p.allowCleartext
            e[K.allowInsecureTls] = p.allowInsecureTls
            e[K.smsRecipients] = p.smsRecipients
            e[K.smsSubscriptionId] = p.smsSubscriptionId
            e[K.smsStart] = p.smsStart
            e[K.smsTrack] = p.smsTrack
            e[K.smsEnd] = p.smsEnd
            e[K.smsMinIntervalSec] = p.smsMinIntervalSec
            e[K.tokenSecret] = p.tokenSecret
            e[K.yourlsEnabled] = p.yourlsEnabled
            e[K.yourlsApi] = p.yourlsApi
            e[K.yourlsToken] = p.yourlsToken
            e[K.yourlsTemplate] = p.yourlsTemplate
            e[K.yourlsOgniMessaggio] = p.yourlsOgniMessaggio
            e[K.controlloGiorni] = p.controlloGiorni
            e[K.ultimoControllo] = p.ultimoControllo
            e[K.versioneTrovata] = p.versioneTrovata
            e[K.codiceTrovato] = p.codiceTrovato
            e[K.novitaTrovate] = p.novitaTrovate
            e[K.intervalSec] = p.intervalSec
            e[K.minDistanceM] = p.minDistanceM
            e[K.adaptive] = p.adaptive
            e[K.defaultRadius] = p.defaultRadius
            e[K.maxTripMinutes] = p.maxTripMinutes
            e[K.batteryStopPercent] = p.batteryStopPercent
            e[K.coordDecimals] = p.coordDecimals
            e[K.mapStyleUrl] = p.mapStyleUrl
            e[K.theme] = p.theme.name
            e[K.language] = p.language.name
            e[K.destPoiId] = p.destPoiId
            e[K.destName] = p.destName
            e[K.destLat] = p.destLat.toString()
            e[K.destLon] = p.destLon.toString()
            e[K.destRadius] = p.destRadius
        }
    }
}

package net.fribbynetwork.iamhere.data

import net.fribbynetwork.iamhere.ui.theme.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64

/**
 * Salvataggio e ripristino della configurazione.
 *
 * Il file contiene chiavi del server, segreti e token: vale quanto la
 * password del server, per questo il contenuto viaggia cifrato con una
 * password scelta al momento del salvataggio. L'involucro esterno resta
 * in chiaro, cosi si capisce di che file si tratta anche senza aprirlo.
 *
 * Senza la password non c'e recupero possibile: e il rovescio del fatto
 * che nemmeno chi trova il file possa leggerlo.
 */
object Backup {

    /** Cambia solo se il formato del file cambia in modo incompatibile. */
    private const val FORMATO = 1

    private const val ITERAZIONI = 120_000
    private const val LUNGHEZZA_CHIAVE = 256

    /** Quello che c'era dentro un file di salvataggio letto correttamente. */
    data class Contenuto(
        val impostazioni: Prefs?,
        val punti: List<Poi>
    )

    sealed interface Esito {
        data class Ok(val contenuto: Contenuto) : Esito
        /** La password non apre il file. */
        data object PasswordSbagliata : Esito
        /** Non e un file di salvataggio di questa app, o e rovinato. */
        data object FileNonValido : Esito
    }

    // ------------------------------------------------------------ cifratura

    private fun chiaveDa(password: String, sale: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), sale, ITERAZIONI, LUNGHEZZA_CHIAVE)
        val k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec)
        return SecretKeySpec(k.encoded, "AES")
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun deB64(s: String) = Base64.decode(s, Base64.NO_WRAP)

    // -------------------------------------------------------------- scrittura

    /**
     * Costruisce il file. Passare null a una delle due parti la esclude:
     * chi vuole spostare solo i punti non porta con se le chiavi.
     */
    fun crea(prefs: Prefs?, punti: List<Poi>?, password: String): String {
        val dentro = JSONObject()
        if (prefs != null) dentro.put("impostazioni", prefsToJson(prefs))
        if (punti != null) {
            val a = JSONArray()
            for (p in punti) {
                a.put(JSONObject().apply {
                    put("nome", p.name)
                    put("lat", p.lat)
                    put("lon", p.lon)
                    if (p.radius != null) put("raggio", p.radius)
                })
            }
            dentro.put("punti", a)
        }

        val sale = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, chiaveDa(password, sale), GCMParameterSpec(128, iv))
        val cifrato = cipher.doFinal(dentro.toString().toByteArray(Charsets.UTF_8))

        return JSONObject().apply {
            put("app", "I am here")
            put("formato", FORMATO)
            put("creato", System.currentTimeMillis() / 1000L)
            put("sale", b64(sale))
            put("iv", b64(iv))
            put("dati", b64(cifrato))
        }.toString(2)
    }

    // --------------------------------------------------------------- lettura

    fun leggi(testo: String, password: String): Esito {
        val fuori = try {
            JSONObject(testo)
        } catch (e: Exception) {
            return Esito.FileNonValido
        }
        if (fuori.optString("app") != "I am here" || fuori.optInt("formato") > FORMATO) {
            return Esito.FileNonValido
        }

        val chiaro = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                chiaveDa(password, deB64(fuori.getString("sale"))),
                GCMParameterSpec(128, deB64(fuori.getString("iv")))
            )
            String(cipher.doFinal(deB64(fuori.getString("dati"))), Charsets.UTF_8)
        } catch (e: Exception) {
            // GCM verifica l'integrita: con la password sbagliata il
            // controllo fallisce prima ancora di restituire dati.
            return Esito.PasswordSbagliata
        }

        return try {
            val dentro = JSONObject(chiaro)
            val prefs = if (dentro.has("impostazioni"))
                jsonToPrefs(dentro.getJSONObject("impostazioni")) else null

            val punti = mutableListOf<Poi>()
            val a = dentro.optJSONArray("punti")
            if (a != null) {
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    punti.add(
                        Poi(
                            name = o.optString("nome", "?"),
                            lat = o.getDouble("lat"),
                            lon = o.getDouble("lon"),
                            radius = if (o.has("raggio")) o.getInt("raggio") else null
                        )
                    )
                }
            }
            Esito.Ok(Contenuto(prefs, punti))
        } catch (e: Exception) {
            Esito.FileNonValido
        }
    }

    // ------------------------------------------------- conversione delle preferenze

    /**
     * La destinazione attiva non si esporta: riguarda il viaggio in corso
     * su quel telefono, non la configurazione.
     */
    private fun prefsToJson(p: Prefs) = JSONObject().apply {
        put("endpointEnabled", p.endpointEnabled)
        put("smsEnabled", p.smsEnabled)
        put("endpointMode", p.endpointMode.name)
        put("smsMode", p.smsMode.name)
        put("method", p.method.name)
        put("urlStart", p.urlStart)
        put("urlTrack", p.urlTrack)
        put("urlEnd", p.urlEnd)
        put("headers", p.headers)
        put("allowCleartext", p.allowCleartext)
        put("allowInsecureTls", p.allowInsecureTls)
        put("smsRecipients", p.smsRecipients)
        put("smsSubscriptionId", p.smsSubscriptionId)
        put("smsStart", p.smsStart)
        put("smsTrack", p.smsTrack)
        put("smsEnd", p.smsEnd)
        put("smsMinIntervalSec", p.smsMinIntervalSec)
        put("tokenSecret", p.tokenSecret)
        put("yourlsEnabled", p.yourlsEnabled)
        put("yourlsApi", p.yourlsApi)
        put("yourlsToken", p.yourlsToken)
        put("yourlsTemplate", p.yourlsTemplate)
        put("yourlsOgniMessaggio", p.yourlsOgniMessaggio)
        put("intervalSec", p.intervalSec)
        put("minDistanceM", p.minDistanceM)
        put("adaptive", p.adaptive)
        put("defaultRadius", p.defaultRadius)
        put("maxTripMinutes", p.maxTripMinutes)
        put("batteryStopPercent", p.batteryStopPercent)
        put("coordDecimals", p.coordDecimals)
        put("mapStyleUrl", p.mapStyleUrl)
        put("theme", p.theme.name)
        put("language", p.language.name)
    }

    /**
     * Ogni campo assente resta al valore di partenza: un file scritto da
     * una versione precedente si legge lo stesso.
     */
    private fun jsonToPrefs(o: JSONObject): Prefs {
        val d = Prefs()
        fun <T : Enum<T>> enumOr(nome: String, valori: Array<T>, def: T): T =
            valori.firstOrNull { it.name == o.optString(nome) } ?: def

        return Prefs(
            endpointEnabled = o.optBoolean("endpointEnabled", d.endpointEnabled),
            smsEnabled = o.optBoolean("smsEnabled", d.smsEnabled),
            endpointMode = enumOr("endpointMode", SendMode.values(), d.endpointMode),
            smsMode = enumOr("smsMode", SendMode.values(), d.smsMode),
            method = enumOr("method", HttpMethod.values(), d.method),
            urlStart = o.optString("urlStart", d.urlStart),
            urlTrack = o.optString("urlTrack", d.urlTrack),
            urlEnd = o.optString("urlEnd", d.urlEnd),
            headers = o.optString("headers", d.headers),
            allowCleartext = o.optBoolean("allowCleartext", d.allowCleartext),
            allowInsecureTls = o.optBoolean("allowInsecureTls", d.allowInsecureTls),
            smsRecipients = o.optString("smsRecipients", d.smsRecipients),
            smsSubscriptionId = o.optInt("smsSubscriptionId", d.smsSubscriptionId),
            smsStart = o.optString("smsStart", d.smsStart),
            smsTrack = o.optString("smsTrack", d.smsTrack),
            smsEnd = o.optString("smsEnd", d.smsEnd),
            smsMinIntervalSec = o.optInt("smsMinIntervalSec", d.smsMinIntervalSec),
            tokenSecret = o.optString("tokenSecret", d.tokenSecret),
            yourlsEnabled = o.optBoolean("yourlsEnabled", d.yourlsEnabled),
            yourlsApi = o.optString("yourlsApi", d.yourlsApi),
            yourlsToken = o.optString("yourlsToken", d.yourlsToken),
            yourlsTemplate = o.optString("yourlsTemplate", d.yourlsTemplate),
            yourlsOgniMessaggio = o.optBoolean("yourlsOgniMessaggio", d.yourlsOgniMessaggio),
            intervalSec = o.optInt("intervalSec", d.intervalSec),
            minDistanceM = o.optInt("minDistanceM", d.minDistanceM),
            adaptive = o.optBoolean("adaptive", d.adaptive),
            defaultRadius = o.optInt("defaultRadius", d.defaultRadius),
            maxTripMinutes = o.optInt("maxTripMinutes", d.maxTripMinutes),
            batteryStopPercent = o.optInt("batteryStopPercent", d.batteryStopPercent),
            coordDecimals = o.optInt("coordDecimals", d.coordDecimals),
            mapStyleUrl = o.optString("mapStyleUrl", d.mapStyleUrl),
            theme = enumOr("theme", ThemeMode.values(), d.theme),
            language = enumOr("language", LangMode.values(), d.language)
        )
    }
}

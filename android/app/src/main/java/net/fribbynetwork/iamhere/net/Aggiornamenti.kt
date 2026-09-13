package net.fribbynetwork.iamhere.net

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import net.fribbynetwork.iamhere.data.Prefs
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Controllo delle nuove versioni.
 *
 * Legge un file piccolo pubblicato accanto al codice, che dice a che
 * punto sono app e server. E' solo un avviso: non scarica e non installa
 * niente, e il download resta un gesto volontario.
 *
 * Spento di partenza, e la scelta e voluta. Un controllo automatico fa
 * sapere a GitHub l'indirizzo IP del telefono, a intervalli regolari: per
 * un'app che promette di non contattare nessuno, accenderlo deve essere
 * una decisione di chi la usa.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */
object Aggiornamenti {

    /**
     * HEAD al posto del nome del ramo: cosi l'indirizzo continua a
     * funzionare anche se il ramo principale un giorno cambia nome.
     */
    private const val URL =
        "https://raw.githubusercontent.com/fribbynetwork/i-am-here/HEAD/versioni.json"

    const val PAGINA_RILASCI = "https://github.com/fribbynetwork/i-am-here/releases"

    /** Quello che si e trovato: versione, novita e se e piu recente. */
    data class Esito(val codice: Int, val versione: String, val novita: String)

    private val client by lazy {
        OkHttpClient.Builder()
            // Nessuno sta aspettando questa risposta: meglio rinunciare
            // in fretta che tenere aperta una connessione a vuoto.
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    /** Il numero di rilascio di questa copia, preso dal pacchetto. */
    fun rilascioInstallato(context: Context): Int = try {
        val p: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode.toInt()
        else @Suppress("DEPRECATION") p.versionCode
    } catch (e: Exception) {
        0
    }

    /**
     * Interroga il file e restituisce la versione pubblicata, oppure null
     * se non si e potuto sapere. Va chiamata fuori dal thread principale.
     */
    fun controlla(lingua: String): Esito? = try {
        val req = Request.Builder().url(URL)
            .header("User-Agent", "I am here")
            .build()
        client.newCall(req).execute().use { r ->
            val corpo = r.body?.string()
            if (!r.isSuccessful || corpo.isNullOrBlank()) null
            else {
                val app = JSONObject(corpo).getJSONObject("app")
                val novita = app.optJSONObject("novita")
                Esito(
                    codice = app.optInt("codice", 0),
                    versione = app.optString("versione", ""),
                    novita = novita?.optString(lingua)?.takeIf { it.isNotBlank() }
                        ?: novita?.optString("en").orEmpty()
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    /** Vero se e il momento di ricontrollare. */
    fun eOra(prefs: Prefs, adesso: Long = System.currentTimeMillis()): Boolean {
        val giorni = prefs.controlloGiorni
        if (giorni <= 0) return false
        return adesso - prefs.ultimoControllo > giorni * 86_400_000L
    }
}

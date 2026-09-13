package net.fribbynetwork.iamhere.net

import net.fribbynetwork.iamhere.data.Prefs
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Accorciatore di link YOURLS.
 *
 * Serve solo a chi ha una propria istanza di YOURLS. L'app chiama
 * l'API con il token di firma e usa il link corto negli SMS.
 *
 * Se qualcosa non funziona, per rete assente, server spento o token
 * sbagliato, si ripiega sul link lungo: meglio un messaggio con un
 * indirizzo brutto che nessun messaggio.
 */
object Yourls {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // L'SMS aspetta questa risposta, quindi i tempi sono corti.
            // callTimeout mette un tetto al giro completo, DNS compreso:
            // senza, connessione e lettura si sommerebbero.
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private fun base(prefs: Prefs): String {
        var u = prefs.yourlsApi.trim()
        if (u.isEmpty()) return ""
        // Accetta sia l'indirizzo del sito sia quello dell'API.
        if (!u.contains("yourls-api.php")) {
            u = u.trimEnd('/') + "/yourls-api.php"
        }
        return u
    }

    /** Restituisce il link corto, oppure null se non ci riesce. */
    fun accorcia(prefs: Prefs, linkLungo: String): String? {
        val api = base(prefs)
        if (api.isEmpty() || prefs.yourlsToken.isBlank() || linkLungo.isBlank()) return null

        val url = (api.toHttpUrlOrNull() ?: return null).newBuilder()
            .addQueryParameter("signature", prefs.yourlsToken.trim())
            .addQueryParameter("action", "shorturl")
            .addQueryParameter("format", "json")
            .addQueryParameter("url", linkLungo)
            .build()

        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                val corpo = r.body?.string().orEmpty()
                if (!r.isSuccessful) return null
                val j = JSONObject(corpo)
                // YOURLS risponde con shorturl sia per un link nuovo sia
                // per uno gia accorciato in precedenza.
                j.optString("shorturl").takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Prova la connessione senza creare niente: db-stats restituisce il
     * numero di link e di clic. Alcuni servizi compatibili implementano
     * solo shorturl, per quelli si ripiega accorciando l'indirizzo
     * stesso di YOURLS, che di norma esiste gia e non ne crea uno nuovo.
     */
    fun prova(prefs: Prefs): SendResult {
        val api = base(prefs)
        if (api.isEmpty()) return SendResult(false, "Indirizzo API mancante")
        if (prefs.yourlsToken.isBlank()) return SendResult(false, "Token mancante")

        val url = (api.toHttpUrlOrNull() ?: return SendResult(false, "Indirizzo non valido"))
            .newBuilder()
            .addQueryParameter("signature", prefs.yourlsToken.trim())
            .addQueryParameter("action", "db-stats")
            .addQueryParameter("format", "json")
            .build()

        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { r ->
                val corpo = r.body?.string().orEmpty().take(300)
                if (r.isSuccessful && corpo.contains("db-stats", true)) {
                    SendResult(true, "HTTP ${r.code}  $corpo")
                } else {
                    val corto = accorcia(prefs, api.substringBefore("/yourls-api.php"))
                    if (corto != null) SendResult(true, "OK  $corto")
                    else SendResult(false, "HTTP ${r.code}  $corpo")
                }
            }
        } catch (e: Exception) {
            SendResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }
}

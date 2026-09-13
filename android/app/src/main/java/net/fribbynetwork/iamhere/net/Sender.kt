package net.fribbynetwork.iamhere.net

import android.app.PendingIntent
import android.content.Context
import android.telephony.SmsManager
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.data.HttpMethod
import net.fribbynetwork.iamhere.data.Prefs
import net.fribbynetwork.iamhere.data.Sample
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class SendResult(val ok: Boolean, val detail: String)

object Sender {

    private fun base() = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)

    /** Client normale: verifica il certificato del server come si deve. */
    private val client: OkHttpClient by lazy { base().build() }

    /**
     * Client che accetta qualunque certificato e qualunque nome host.
     *
     * Serve per un server personale con certificato autofirmato, dove non
     * c'e nessuna autorita a garantire. Il prezzo e che cade l'unica difesa
     * contro un intermediario che si spacci per il tuo server: chiunque
     * controlli la rete puo leggere e alterare quello che spedisci, chiave
     * compresa. Per questo l'oggetto viene costruito solo se l'utente ha
     * acceso l'interruttore apposta, e mai per impostazione predefinita.
     */
    private val clientPermissivo: OkHttpClient by lazy {
        val accettaTutto = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<X509Certificate>?, a: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(accettaTutto), SecureRandom())
        }
        base()
            .sslSocketFactory(ssl.socketFactory, accettaTutto)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    /**
     * L'utente configura un solo template di URL completo di query string.
     * Il metodo scelto decide poi come i parametri viaggiano:
     * in query (GET), nel corpo come form (POST form) o come oggetto
     * JSON (POST JSON). Cosi non serve configurare due volte le stesse cose.
     */
    fun buildRequest(
        prefs: Prefs,
        sample: Sample,
        event: String = sample.event,
        destLibera: String = ""
    ): Request {
        val values = Templates.valuesOf(sample, prefs.coordDecimals, segreto = prefs.tokenSecret,
            destLibera = destLibera)
        val rendered = Templates.render(prefs.urlFor(event), values, Templates.Mode.URL)

        val qIndex = rendered.indexOf('?')
        val base = if (qIndex >= 0) rendered.substring(0, qIndex) else rendered
        val query = if (qIndex >= 0) rendered.substring(qIndex + 1) else ""

        val b = Request.Builder()
        parseHeaders(prefs.headers).forEach { (k, v) -> b.addHeader(k, v) }

        when (prefs.method) {
            HttpMethod.GET -> b.url(rendered).get()

            HttpMethod.POST_FORM -> {
                val form = FormBody.Builder()
                pairs(query).forEach { (k, v) -> form.add(k, v) }
                b.url(base).post(form.build())
            }

            HttpMethod.POST_JSON -> {
                val json = JSONObject()
                pairs(query).forEach { (k, v) -> json.put(k, v) }
                b.url(base).post(
                    json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                )
            }
        }
        return b.build()
    }

    fun sendHttp(
        context: Context,
        prefs: Prefs,
        sample: Sample,
        event: String = sample.event,
        destLibera: String = ""
    ): SendResult {
        // Un template malformato solleva gia qui, prima di toccare la rete.
        val req = try {
            buildRequest(prefs, sample, event, destLibera)
        } catch (e: Exception) {
            return SendResult(false, descrivi(e))
        }

        // Il controllo sull'HTTP in chiaro sta nel codice e non nella
        // configurazione di rete: quel file e statico e non si puo cambiare
        // mentre l'app gira, quindi un interruttore vero puo vivere solo qui.
        if (!req.url.isHttps && !prefs.allowCleartext) {
            return SendResult(false, context.getString(R.string.cleartext_blocked))
        }

        val c = if (prefs.allowInsecureTls) clientPermissivo else client
        return try {
            c.newCall(req).execute().use { r ->
                val body = r.body?.string()?.take(200)?.trim().orEmpty()
                SendResult(r.isSuccessful, "HTTP ${r.code} $body")
            }
        } catch (e: Exception) {
            SendResult(false, descrivi(e))
        }
    }

    private fun descrivi(e: Exception) =
        e.javaClass.simpleName + (e.message?.let { ": $it" } ?: "")

    /** Testo e URL composti, per il pulsante di prova. */
    fun preview(prefs: Prefs, sample: Sample, event: String, destLibera: String = ""): String {
        val values = Templates.valuesOf(sample, prefs.coordDecimals, segreto = prefs.tokenSecret,
            destLibera = destLibera)
        val url = Templates.render(prefs.urlFor(event), values, Templates.Mode.URL)
        return when (prefs.method) {
            HttpMethod.GET -> "GET $url"
            HttpMethod.POST_FORM -> {
                val q = url.substringAfter('?', "")
                "POST ${url.substringBefore('?')}\n\n" +
                    pairs(q).joinToString("\n") { "${it.first}=${it.second}" }
            }
            HttpMethod.POST_JSON -> {
                val q = url.substringAfter('?', "")
                val json = JSONObject()
                pairs(q).forEach { (k, v) -> json.put(k, v) }
                "POST ${url.substringBefore('?')}\n\n" + json.toString(2)
            }
        }
    }

    fun smsText(
        prefs: Prefs,
        sample: Sample,
        event: String,
        yourls: String = "",
        destLibera: String = ""
    ): String {
        val values = Templates.valuesOf(
            sample, prefs.coordDecimals, segreto = prefs.tokenSecret, yourls = yourls,
            destLibera = destLibera
        )
        return Templates.render(prefs.smsFor(event), values, Templates.Mode.RAW)
    }

    /** Il link da mandare a YOURLS, con i segnaposto gia sostituiti. */
    fun linkLungo(prefs: Prefs, sample: Sample, destLibera: String = ""): String {
        if (prefs.yourlsTemplate.isBlank()) return ""
        val values = Templates.valuesOf(sample, prefs.coordDecimals, segreto = prefs.tokenSecret,
            destLibera = destLibera)
        return Templates.render(prefs.yourlsTemplate, values, Templates.Mode.RAW)
    }

    /**
     * Il link accorciato, oppure null se non si e potuto accorciare.
     *
     * Restituire null invece del link lungo serve a chi chiama: un
     * fallimento temporaneo non deve essere memorizzato come se fosse il
     * link buono, altrimenti tutto il resto del viaggio userebbe quello
     * lungo anche quando YOURLS torna disponibile.
     */
    fun linkAccorciato(prefs: Prefs, sample: Sample, destLibera: String = ""): String? {
        if (!prefs.yourlsEnabled) return null
        val lungo = linkLungo(prefs, sample, destLibera)
        if (lungo.isBlank()) return null
        return Yourls.accorcia(prefs, lungo)
    }

    /**
     * Rimanda un testo gia composto, senza ricomporlo: un messaggio di
     * partenza deve conservare la posizione che aveva allora, non quella
     * del momento in cui si riprova.
     */
    fun reinviaSms(
        context: Context,
        prefs: Prefs,
        destinatario: String,
        testo: String,
        esito: PendingIntent? = null
    ): SendResult = try {
        val mgr = smsManager(context, prefs.smsSubscriptionId)
        val parts = mgr.divideMessage(testo)
        if (parts.size > 1) {
            val intenti = ArrayList<PendingIntent?>(parts.size)
            repeat(parts.size - 1) { intenti.add(null) }
            intenti.add(esito)
            mgr.sendMultipartTextMessage(destinatario, null, parts, intenti, null)
        } else {
            mgr.sendTextMessage(destinatario, null, testo, esito, null)
        }
        SendResult(true, context.getString(R.string.sms_sent_to, 1))
    } catch (e: Exception) {
        SendResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
    }

    /** Vero se il messaggio di questo evento contiene davvero {yourls}. */
    fun serveYourls(prefs: Prefs, event: String): Boolean =
        prefs.smsFor(event).contains("{yourls}")

    /**
     * Manda il messaggio a ogni destinatario.
     *
     * Consegnare un SMS al sistema riesce quasi sempre: vuol dire che
     * Android lo ha preso in carico, non che sia partito. Senza campo
     * resta nella radio e fallisce piu tardi, in silenzio.
     *
     * Per saperlo davvero si passa 'esito': una funzione che, dato il
     * destinatario, restituisce l'intento da richiamare quando il
     * messaggio parte o fallisce. Chi chiama riceve cosi l'esito vero,
     * con il motivo, invece di una promessa.
     *
     * Con un messaggio lungo Android lo spezza: l'intento si attacca solo
     * all'ultimo pezzo, che e quello che dice se e uscito tutto.
     */
    fun sendSms(
        context: Context,
        prefs: Prefs,
        sample: Sample,
        event: String,
        yourls: String = "",
        destLibera: String = "",
        esito: ((destinatario: String) -> PendingIntent?)? = null
    ): SendResult {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.SEND_SMS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return SendResult(false, context.getString(R.string.sms_permission_missing))
        }

        val recipients = prefs.smsRecipients
            .split(',', ';', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (recipients.isEmpty()) return SendResult(false, context.getString(R.string.no_recipients))

        val text = smsText(prefs, sample, event, yourls, destLibera)
        if (text.isBlank()) return SendResult(false, context.getString(R.string.empty_message))

        return try {
            val mgr = smsManager(context, prefs.smsSubscriptionId)
            var sent = 0
            for (n in recipients) {
                val pi = esito?.invoke(n)
                val parts = mgr.divideMessage(text)
                if (parts.size > 1) {
                    val intenti = ArrayList<PendingIntent?>(parts.size)
                    repeat(parts.size - 1) { intenti.add(null) }
                    intenti.add(pi)
                    mgr.sendMultipartTextMessage(n, null, parts, intenti, null)
                } else {
                    mgr.sendTextMessage(n, null, text, pi, null)
                }
                sent++
            }
            SendResult(true, context.getString(R.string.sms_sent_to, sent))
        } catch (e: Exception) {
            SendResult(false, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(context: Context, subId: Int): SmsManager {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId >= 0) base.createForSubscriptionId(subId) else base
        } else {
            if (subId >= 0) SmsManager.getSmsManagerForSubscriptionId(subId)
            else SmsManager.getDefault()
        }
    }

    private fun pairs(query: String): List<Pair<String, String>> =
        query.split('&').filter { it.isNotBlank() }.map { chunk ->
            val i = chunk.indexOf('=')
            if (i < 0) dec(chunk) to ""
            else dec(chunk.substring(0, i)) to dec(chunk.substring(i + 1))
        }

    private fun dec(s: String): String = try {
        URLDecoder.decode(s, "UTF-8")
    } catch (e: Exception) {
        s
    }

    /** Una riga per header, formato "Nome: valore". */
    fun parseHeaders(raw: String): List<Pair<String, String>> =
        raw.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || !t.contains(':')) null
            else t.substringBefore(':').trim() to t.substringAfter(':').trim()
        }.filter { it.first.isNotEmpty() }
}

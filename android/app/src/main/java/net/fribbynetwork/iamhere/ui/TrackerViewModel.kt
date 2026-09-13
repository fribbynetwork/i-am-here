package net.fribbynetwork.iamhere.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri
import net.fribbynetwork.iamhere.R
import net.fribbynetwork.iamhere.net.Aggiornamenti
import net.fribbynetwork.iamhere.data.Backup
import net.fribbynetwork.iamhere.data.Db
import net.fribbynetwork.iamhere.data.Poi
import net.fribbynetwork.iamhere.data.Prefs
import net.fribbynetwork.iamhere.data.Sample
import net.fribbynetwork.iamhere.data.SettingsStore
import net.fribbynetwork.iamhere.data.Trip
import kotlinx.coroutines.flow.MutableStateFlow
import net.fribbynetwork.iamhere.loc.LocationEngine
import net.fribbynetwork.iamhere.loc.TrackerState
import net.fribbynetwork.iamhere.loc.TrackingService
import net.fribbynetwork.iamhere.net.Sender
import net.fribbynetwork.iamhere.util.withLocale
import net.fribbynetwork.iamhere.work.FlushWorker

class TrackerViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SettingsStore(app)
    private val db = Db.get(app)

    /** null finche DataStore non ha letto da disco: evita che le schermate
     *  si inizializzino con i valori di default sovrascrivendo i tuoi. */
    val prefsOrNull: StateFlow<Prefs?> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val prefs: StateFlow<Prefs> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Prefs())

    val pois: StateFlow<List<Poi>> =
        db.poi().all().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trips: StateFlow<List<Trip>> =
        db.trips().recent().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val samples: StateFlow<List<Sample>> =
        db.samples().latest(300).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingCount: StateFlow<Int> =
        db.samples().pendingCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val live = TrackerState.state

    private val engine by lazy { LocationEngine(getApplication<Application>()) }

    /** Posizione con cui centrare la mappa quando si crea un punto. */
    private val _pickerLocation = MutableStateFlow<Pair<Double, Double>?>(null)
    val pickerLocation: StateFlow<Pair<Double, Double>?> = _pickerLocation

    /**
     * Prima l'ultima posizione nota, che e immediata, poi un fix fresco
     * quando arriva. Cosi la mappa si apre gia nel posto giusto invece di
     * partire da un ripiego generico.
     */
    fun refreshPickerLocation() {
        runCatching {
            engine.lastKnown()?.let { _pickerLocation.value = it.latitude to it.longitude }
            engine.currentFix { loc ->
                loc?.let { _pickerLocation.value = it.latitude to it.longitude }
            }
        }
    }

    fun save(p: Prefs) = viewModelScope.launch { store.save(p) }

    fun addPoi(name: String, lat: Double, lon: Double, radius: Int?) = viewModelScope.launch {
        db.poi().insert(Poi(name = name, lat = lat, lon = lon, radius = radius))
    }

    fun updatePoi(poi: Poi) = viewModelScope.launch { db.poi().update(poi) }

    fun deletePoi(id: Long) = viewModelScope.launch {
        db.poi().delete(id)
        val p = prefs.value
        if (p.destPoiId == id) clearDestination()
    }

    fun chooseDestination(poi: Poi) = viewModelScope.launch {
        val p = prefs.value
        store.save(
            p.copy(
                destPoiId = poi.id,
                destName = poi.name,
                destLat = poi.lat,
                destLon = poi.lon,
                destRadius = poi.radius ?: p.defaultRadius
            )
        )
    }

    /** Destinazione al volo, senza salvarla in elenco. */
    fun setAdHocDestination(lat: Double, lon: Double, radius: Int, name: String = "Map point") =
        viewModelScope.launch {
            store.save(prefs.value.copy(destPoiId = -1, destName = name, destLat = lat, destLon = lon, destRadius = radius))
        }

    fun clearDestination() = viewModelScope.launch {
        store.save(prefs.value.copy(destPoiId = -1, destName = "", destLat = 0.0, destLon = 0.0, destRadius = 0))
    }

    fun start() {
        val i = Intent(getApplication(), TrackingService::class.java)
            .setAction(TrackingService.ACTION_START)
        ContextCompat.startForegroundService(getApplication<Application>(), i)
    }

    fun stop() {
        val i = Intent(getApplication(), TrackingService::class.java)
            .setAction(TrackingService.ACTION_STOP)
        getApplication<Application>().startService(i)
    }

    fun retryQueue() = FlushWorker.schedule(getApplication<Application>())

    /**
     * Controlla se esiste una versione piu recente.
     *
     * Con forzato = false parte solo se l'utente ha acceso il controllo e
     * sono passati i giorni impostati; il pulsante nelle impostazioni
     * passa true e chiede subito, anche a controllo spento.
     *
     * Il risultato resta nelle preferenze: cosi l'avviso si rivede senza
     * uscire di nuovo in rete.
     */
    fun controllaAggiornamenti(forzato: Boolean = false, esito: ((String?) -> Unit)? = null) =
        viewModelScope.launch(Dispatchers.IO) {
            val p = store.current()
            if (!forzato && !Aggiornamenti.eOra(p)) { esito?.invoke(null); return@launch }

            /*
             * language e una scelta a tre valori, non un codice: con
             * "sistema" la lingua vera la sa solo il contesto, quindi si
             * legge da li invece di darla per scontata.
             */
            val loc = getApplication<Application>().withLocale(p.language)
                .resources.configuration.locales[0].language
            val r = Aggiornamenti.controlla(if (loc == "it") "it" else "en")
            val adesso = System.currentTimeMillis()
            if (r == null) {
                // Si segna comunque il tentativo: senza, un GitHub
                // irraggiungibile farebbe riprovare a ogni apertura.
                store.save(p.copy(ultimoControllo = adesso))
                esito?.invoke(null)
                return@launch
            }
            store.save(
                p.copy(
                    ultimoControllo = adesso,
                    versioneTrovata = r.versione,
                    codiceTrovato = r.codice,
                    novitaTrovate = r.novita
                )
            )
            val installato = Aggiornamenti.rilascioInstallato(getApplication<Application>())
            esito?.invoke(if (r.codice > installato) r.versione else "")
        }

    /** Vero se quello che sappiamo e piu recente di quello installato. */
    fun aggiornamentoPronto(p: Prefs): Boolean =
        p.codiceTrovato > Aggiornamenti.rilascioInstallato(getApplication<Application>())

    fun clearHistory() = viewModelScope.launch { db.samples().clearAll() }

    /** Prova di invio con una posizione fittizia. */
    fun testSend(event: String, onResult: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val p = prefs.value
        val sample = net.fribbynetwork.iamhere.net.Templates.demoSample(event)
        val preview = Sender.preview(p, sample, event,
            getApplication<Application>().withLocale(p.language).getString(R.string.dest_free_endpoint))
        val ctx = getApplication<Application>().withLocale(p.language)
        val r = Sender.sendHttp(ctx, p, sample, event, ctx.getString(R.string.dest_free_endpoint))
        val text = preview + "\n\n---\n" + (if (r.ok) "OK  " else "ERRORE  ") + r.detail
        withContext(Dispatchers.Main) { onResult(text) }
    }

    // ------------------------------------------------- salvataggio e ripristino

    /** Scrive il file cifrato nel documento scelto dall'utente. */
    fun esporta(
        uri: Uri,
        password: String,
        conImpostazioni: Boolean,
        conPunti: Boolean,
        esito: (Boolean) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val ok = runCatching {
            val testo = Backup.crea(
                if (conImpostazioni) store.current() else null,
                if (conPunti) db.poi().tutti() else null,
                password
            )
            getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use {
                it.write(testo.toByteArray())
            } ?: error("documento non scrivibile")
            true
        }.getOrDefault(false)
        withContext(Dispatchers.Main) { esito(ok) }
    }

    /** Legge e decifra, senza applicare niente. */
    fun leggiBackup(uri: Uri, password: String, esito: (Backup.Esito) -> Unit) =
        viewModelScope.launch(Dispatchers.IO) {
            val e = runCatching {
                val testo = getApplication<Application>().contentResolver
                    .openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("documento non leggibile")
                Backup.leggi(testo, password)
            }.getOrElse { Backup.Esito.FileNonValido }
            withContext(Dispatchers.Main) { esito(e) }
        }

    /**
     * Applica un salvataggio gia letto. La destinazione attiva viene
     * azzerata: apparteneva all'altro telefono, non a questo.
     */
    fun applicaBackup(c: Backup.Contenuto, sostituisciPunti: Boolean, esito: (Int) -> Unit) =
        viewModelScope.launch {
            c.impostazioni?.let { store.save(it) }
            var quanti = 0
            if (c.punti.isNotEmpty()) {
                if (sostituisciPunti) db.poi().cancellaTutti()
                for (p in c.punti) {
                    db.poi().insert(p.copy(id = 0))
                    quanti++
                }
            }
            esito(quanti)
        }

    fun testYourls(onResult: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val p = prefs.value
        val sample = net.fribbynetwork.iamhere.net.Templates.demoSample("start")
        val lungo = Sender.linkLungo(p, sample)
        val r = net.fribbynetwork.iamhere.net.Yourls.prova(p)
        val out = (if (lungo.isBlank()) "" else "$lungo\n\n---\n") +
            (if (r.ok) "OK  " else "ERRORE  ") + r.detail
        withContext(Dispatchers.Main) { onResult(out) }
    }

    fun testSms(event: String, onResult: (String) -> Unit) = viewModelScope.launch(Dispatchers.IO) {
        val p = prefs.value
        val sample = net.fribbynetwork.iamhere.net.Templates.demoSample(event)
        val ctxSms = getApplication<Application>().withLocale(p.language)
        val libera = ctxSms.getString(R.string.dest_free_sms)
        val text = Sender.smsText(p, sample, event, destLibera = libera)
        val r = Sender.sendSms(ctxSms, p, sample, event, destLibera = libera)
        val out = text + "\n\n---\n" + (if (r.ok) "OK  " else "ERRORE  ") + r.detail
        withContext(Dispatchers.Main) { onResult(out) }
    }
}

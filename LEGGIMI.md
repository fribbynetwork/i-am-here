# I am here

Condividi la posizione in tempo reale e tieni il registro di dove sei stato,
senza Google e su un server tuo se lo vuoi.

Sito: **[iamhere.it](https://iamhere.it)** · Dimostrazione dal vivo:
**[demo.iamhere.it](https://demo.iamhere.it)**

*This guide is also available [in English](README.md).*

---

## Cosa fa

Premi avvia. L'app rileva la posizione alla cadenza che hai scelto e la manda
dove decidi tu: un endpoint HTTP, un SMS, o entrambi. Scegli una destinazione
sulla mappa e la condivisione si ferma da sola quando arrivi.

**Decidi tu dove finiscono i dati.** Nessun account, nessuna statistica,
nessuna telemetria. L'URL lo scrivi tu, con i nomi e l'ordine dei parametri
che il tuo destinatario si aspetta.

**Funziona senza Google Play Services.** La posizione arriva dal gestore di
localizzazione di AOSP, le mappe da MapLibre, i reinvii da WorkManager. Si
installa su qualsiasi dispositivo, anche senza nessuna app Google.

**Offline non si perde niente.** Le posizioni restano in coda sul telefono e
partono in ordine quando torna la rete.

Il progetto ha due metà, indipendenti fra loro: puoi usarne anche una sola.

| | |
|---|---|
| **[`android/`](android/)** | l'app Android: tracciamento, destinazioni, SMS, coda offline |
| **[`server/`](server/)** | un endpoint PHP facoltativo e un visualizzatore con lo storico dei viaggi |

---

## L'app

Invio a un endpoint, via SMS o entrambi, ognuno con la sua cadenza.
Destinazioni scelte sulla mappa con un raggio di arrivo. Cadenza adattiva, più
fitta vicino alla meta e più larga da fermo. Insieme alle coordinate viaggiano
precisione, quota, velocità, direzione, satelliti, batteria e pressione
barometrica.

Una notifica persistente con tempo trascorso, distanza residua ed esito
dell'ultimo invio. Un riquadro nelle Impostazioni Rapide, una schermata di
diagnostica che dice cosa manca, il salvataggio cifrato dell'intera
configurazione, temi chiaro e scuro, italiano e inglese.

Parla anche **[YOURLS](https://yourls.org)**, se ne hai un'istanza: il link
dentro un SMS può essere accorciato prima di partire.

→ **[Ogni schermata e ogni impostazione](android/LEGGIMI.md)**

## Il server

Raccoglie le posizioni e disegna il viaggio: il percorso colorato per
velocità, punti cliccabili, il profilo altimetrico, un riepilogo e l'elenco dei
viaggi passati, sfogliabile per giorno o per mese.

Ogni viaggio ha un suo codice di accesso, così un link che condividi apre
quel viaggio e nessun altro. Servono PHP 8 e una cartella scrivibile: niente
database, niente framework, nessuna chiave API.

→ **[Installazione e configurazione](server/LEGGIMI.md)**

---

## Mandare i dati altrove

L'endpoint è un URL che scrivi tu, quindi va bene qualunque cosa parli HTTP.
L'impostazione **Metodo** decide come viaggiano i parametri: nella query string
(GET), come corpo di un form, o come oggetto JSON.

**[Traccar](https://www.traccar.org)**, con il protocollo OsmAnd sulla porta
5055:

```
http://tuo-traccar:5055/?id=TUO_DISPOSITIVO&lat={lat}&lon={lon}&timestamp={tempo}&altitude={alt}&accuracy={acc}&batt={bat}
```

**Home Assistant**, **Node-RED**, **n8n** e qualsiasi altra cosa con un
webhook: punti l'URL al gancio e chiami i parametri come se li aspetta il
flusso.

**Nextcloud PhoneTrack** e altri tracciatori self-hosted che accettano un URL
di registrazione: controlla nella loro documentazione i nomi esatti dei
parametri.

Se il destinatario vuole una forma che l'app non sa produrre, di solito basta
uno script PHP di cinque righe in mezzo.

→ **[Come si costruisce l'URL, nel dettaglio](android/LEGGIMI.md)**

---

## Come si installa l'app

Gli APK sono nella pagina [Releases](../../releases). Android chiederà di
consentire l'installazione da quella origine e Play Protect avviserà di uno
sviluppatore sconosciuto: sono cose normali fuori dagli store, e il LEGGIMI
dell'app spiega passo per passo cosa fare.

→ **[Tutti i rilasci](../../releases)**

---

## Privacy

Condividere un link significa condividere dove sei stato: strade percorse,
soste, orari, e con il tempo casa e luogo di lavoro. Un messaggio inoltrato per
sbaglio in un gruppo non si può richiamare indietro.

L'app non manda niente da nessuna parte tranne all'indirizzo che configuri tu.
Il server, se lo usi, tiene i percorsi fuori dai motori di ricerca e dietro un
codice di accesso. Nessuna delle due metà telefona a casa.

---

## Licenza

**GNU General Public License v3.0 o successiva**: vedi [LICENSE](LICENSE).
Puoi usarla, studiarla, modificarla e ridistribuirla, a patto che quello che
distribuisci resti sotto la stessa licenza e sia accompagnato dai sorgenti.

| Componente | Licenza |
|---|---|
| [Leaflet](https://leafletjs.com), incluso in `server/leaflet/` | BSD 2-Clause |
| [MapLibre GL Native](https://maplibre.org) | BSD 2-Clause |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 |
| AndroidX, Kotlin, Compose | Apache 2.0 |
| Mattonelle | [OpenFreeMap](https://openfreemap.org) nell'app, OpenStreetMap sul web |
| Dati cartografici | © contributori OpenStreetMap, ODbL |

Tutte compatibili con la GPLv3 e ognuna conserva la propria licenza dentro
l'opera combinata. Un riepilogo in buona fede, non un parere legale.

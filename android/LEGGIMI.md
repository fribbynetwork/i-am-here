# I am here

Condivide la posizione in tempo reale verso un endpoint HTTP e via SMS, e
interrompe la condivisione da sola quando raggiungi una destinazione scelta
sulla mappa.

Funziona **senza Google Play Services**: la posizione arriva dal
`LocationManager` di AOSP, le mappe da MapLibre, i reinvii da WorkManager. Si
installa su qualsiasi dispositivo, anche senza nessuna app Google.

*This guide is also available [in English](README.md).*

---

## Cosa fa

Premi **Inizia condivisione**. Da quel momento l'app rileva la posizione con la
cadenza che hai impostato e la trasmette: all'endpoint, via SMS, o entrambi.
Quando arrivi a destinazione, o quando premi **Ferma**, parte un ultimo punto e
la condivisione si chiude.

Ogni viaggio ha un id: il timestamp unix dell'istante in cui hai iniziato.
Resta lo stesso per tutto il viaggio, così il server può raggruppare tutti i
punti di un tragitto senza bisogno di alcuno scambio preliminare.

Senza destinazione l'app è in **condivisione libera**: continua a trasmettere
finché non la fermi tu.

## Schermata principale

Il pannello in alto diventa arancione mentre la condivisione è attiva, e mostra
tempo trascorso e distanza residua. Sotto ci sono le tre cose che servono:
avvio, stop e la scelta della destinazione. Più giù, l'ultima rilevazione
(coordinate, precisione, velocità, quota, satelliti) e i contatori degli invii.

Mentre la condivisione è in corso resta una notifica fissa con tempo trascorso,
distanza residua, esito dell'ultimo invio e un pulsante Ferma.

Il sito del progetto è **[iamhere.it](https://iamhere.it)**.

## Installazione

L'APK si scarica dalla pagina [Releases](../../releases). Aprendolo, Android
ti porterà davanti a tre cose che vale la pena spiegare, perché nessuna
significa che ci sia qualcosa che non va.

### "Sviluppatore sconosciuto"

Play Protect avvisa per qualunque app che non arrivi dal Play Store. Tocca
**Maggiori dettagli** e poi **Installa comunque**. Prima ancora, il browser o
il gestore file possono chiederti di *consentire l'installazione da questa
origine*: è l'app con cui hai scaricato a chiedertelo, non questa.

L'avviso non dice che l'app sia pericolosa. Dice che Google non l'ha
analizzata, cosa vera per tutto ciò che si distribuisce fuori dal suo store.
Se vuoi verificare che l'APK arrivi davvero da chi lo ha pubblicato, confronta
l'impronta del certificato con quella indicata nelle note di rilascio.

### Il permesso SMS non si attiva

Se usi il canale SMS, Android può rifiutare il permesso e mostrare uno scudo
con scritto che **l'accesso è stato negato**. È una protezione riservata alle
app installate aprendo direttamente un file APK, e blocca proprio i permessi
di cui il malware abusa di più.

Per sbloccarlo:

1. **Impostazioni → App → I am here**
2. Il menu **⋮** in alto a destra
3. La voce sulle **impostazioni con restrizioni**, e conferma con PIN o impronta

Da lì il permesso si concede normalmente, dalla Diagnostica dell'app o dalla
schermata Autorizzazioni.

Il blocco dipende da come l'app è stata installata, non da chi l'ha firmata:
installando da F-Droid, da Obtainium o con `adb install` non compare.

### Batteria

In **Impostazioni → App → I am here → Batteria** deve essere attivo
**Consenti l'utilizzo in background**. È quello l'interruttore che conta: se è
spento il sistema considera l'app limitata e può chiudere il servizio a metà
viaggio.

Vedere l'app indicata come *Ottimizzata* è normale e non è un problema: un
servizio in primo piano con notifica persistente continua a lavorare lo
stesso. Togliere del tutto l'ottimizzazione è un miglioramento, non un
requisito: aiuta nelle soste lunghe a schermo spento e alla ripresa dopo un
riavvio. Per arrivare all'opzione *Senza restrizioni* tocca la riga, non
l'interruttore.

La schermata **Diagnostica** dentro l'app riassume tutto questo. Le righe
rosse impediscono alla condivisione di funzionare, quelle ambra la rendono
solo più solida.

### Aggiornare

Scarica il nuovo APK e aprilo sopra il vecchio. Essendo firmato con la stessa
chiave, si installa mantenendo punti salvati e impostazioni. Non disinstallare
prima, a meno che tu non voglia ripartire da zero.

## Prima configurazione

Apri **Diagnostica** dalla barra in alto e sistema tutte le righe rosse.

- **Posizione sempre attiva** va concessa a mano dalle impostazioni di sistema
  scegliendo *Consenti sempre*. Android non permette di chiederla con una
  finestra.
- **Batteria non ottimizzata**: senza esenzione il sistema sospende gli invii a
  schermo spento.
- Su Xiaomi, Huawei, Oppo, Vivo e Samsung cerca **Avvio automatico** nelle
  impostazioni del telefono e abilitalo. Quei firmware chiudono i servizi in
  background a prescindere dai permessi concessi.

La mappa funziona subito, senza chiave e senza account: usa i tile vettoriali
di OpenFreeMap, generati da dati OpenStreetMap. Non c'è niente da configurare.

---

## Destinazioni

Una destinazione è un punto più un raggio di arrivo. Puoi crearla trascinando
la mappa sotto il mirino, oppure scrivendo le coordinate. Il cerchio arancione
sulla mappa è l'area di arrivo vera e si ridimensiona con lo zoom: quello che
vedi è quello che ottieni.

I punti salvati si rinominano, si spostano e si cancellano. Ognuno può avere il
suo raggio, che ha la precedenza su quello globale: casa in un centro storico
stretto e un casello autostradale vogliono numeri diversi.

Puoi anche usare un punto **come destinazione senza salvarlo**, per un viaggio
occasionale.

L'**arrivo** viene dichiarato quando sei dentro il raggio *e* l'errore del fix è
più piccolo del raggio stesso. È questa seconda condizione a impedire che una
rilevazione sporca chiuda il viaggio in anticipo. L'arrivo si arma solo dopo
essere usciti dal raggio almeno una volta, così partire da dentro l'area non
conta subito come arrivo.

---

## Impostazioni

### Come trasmettere

Endpoint e SMS sono indipendenti. Ognuno può essere spento, oppure impostato su
**partenza e arrivo** o **anche in viaggio**.

### Endpoint

Scrivi l'URL completo di parametri. Nomi e ordine li decidi tu: l'app sostituisce
i segnaposto e invia esattamente quello che hai scritto.

```
https://tuo-server.tld/track.php?key=SEGRETO&lat={lat}&lon={lon}&id={timestamp}&t={tempo}
```

Il **metodo** decide come viaggiano quei parametri: nella query string (GET),
nel corpo come form (POST form) o come oggetto JSON (POST JSON). L'URL lo
configuri una volta sola; cambiare metodo non richiede altro.

Tre template, partenza, tragitto e arrivo, così il server sa in quale fase si
trova. Lasciando vuoti gli ultimi due, ereditano dal primo.

**Header HTTP**, uno per riga nel formato `Nome: valore`. Utili per spostare una
chiave fuori dall'URL, dove finirebbe nei log di accesso del server.

### Connessione

Due interruttori che abbassano la protezione, spenti di default.

**Consenti HTTP in chiaro** permette di usare un endpoint `http://`, per un
server senza certificato. Da quel momento viaggia tutto leggibile, coordinate
e chiave nell'URL, quindi chiunque sia sulla stessa rete può vederlo.

**Accetta certificati non validi** serve per un server personale con
certificato autofirmato. Senza il controllo del certificato l'app non sa più
distinguere il tuo server da chi si spaccia per lui, quindi chi controlla la
rete può leggere e alterare quello che invii. Usalo solo verso un server che
gestisci tu.

Quando uno dei due è acceso, nelle impostazioni compare un riquadro di
avviso.

**Prova invio** compone l'URL con una posizione fittizia, te lo mostra per
esteso, lo spedisce e riporta codice HTTP e corpo della risposta. Usalo prima di
partire.

### SMS

Destinatari (più di uno, separati da virgola), la SIM da cui inviare sui
telefoni dual SIM, e un messaggio diverso per ogni fase.

Sotto ogni messaggio compare il conteggio dei caratteri e quanti SMS
serviranno, calcolato su una posizione di esempio. Attenzione a virgolette
curve, trattini lunghi ed emoji: fanno passare il messaggio alla codifica
Unicode e il limite crolla da 160 a 70 caratteri. Le lettere accentate
italiane invece non lo fanno.

I due pulsanti **Messaggi già pronti** riempiono il campo con un link a
OpenStreetMap, che funziona senza nessun server, o con un link a I am here,
il cui indirizzo viene ricavato dall'endpoint che hai già configurato.

L'**intervallo minimo tra SMS** vale solo per i messaggi lungo il tragitto:
partenza e arrivo passano sempre. Tienilo largo, perché Android limita gli
invii automatici e mostra una finestra di conferma che a schermo spento non
vede nessuno.

### Salvataggio e ripristino

Porta l'intera configurazione su un altro telefono. Spunti cosa includere, impostazioni,
punti di arrivo salvati o entrambi, scegli una password e scrivi
il file dove preferisci; al ripristino viene chiesta la stessa password.

Il file è cifrato perché contiene le chiavi del tuo server, il segreto dei
codici e il token di YOURLS. **Se perdi la password il file non si apre più**:
non c'è recupero.

Se il file porta dei punti e tu ne hai già, l'app chiede se mantenerli entrambi
o sostituire i tuoi. Una password sbagliata non cambia assolutamente niente.
Lo storico dei viaggi non viene incluso: è un registro, non configurazione.

### Avanzate

Chiuse in fondo alle impostazioni, perché servono solo con un server tuo o un
accorciatore di link tuo.

**Server I am here.** Qui va lo stesso `token_secret` impostato nel
`settings.php` del server. Da quel momento `{auth}` funziona, e un SMS può
portare il link al viaggio in corso. Il campo resta mascherato e mostra
un'impronta di sei caratteri: se coincide con quella che mostra il server, hai
incollato il valore giusto.

**YOURLS.** L'indirizzo della tua istanza e il token di firma preso dal suo
pannello, più il link da accorciare, scritto con i segnaposto, esattamente
come l'URL dell'endpoint. Il risultato finisce in `{yourls}`, che usi nel
messaggio.

L'accorciamento avviene **una volta per viaggio** oppure **a ogni messaggio**:
il primo basta quando il link segue tutto il tragitto, il secondo serve se
ogni messaggio deve puntare a dove ti trovi in quel momento. Se manca la rete
o YOURLS non risponde, `{yourls}` ripiega sul link lungo invece di bloccare il
messaggio.

**Prova connessione** verifica indirizzo e token senza creare niente.

### Cadenza

Un punto parte quando è passato l'**intervallo** *e* ti sei spostato almeno
della **distanza minima**. Da fermo ne parte comunque uno ogni quattro
intervalli, così una coda in tangenziale o una sosta al bar non lasciano un
buco nel tracciato.

La **cadenza adattiva** allarga l'intervallo quando sei lontano dalla meta e lo
stringe negli ultimi centinaia di metri, dove la precisione serve per
riconoscere l'arrivo. Si allenta anche quando sei fermo.

### Viaggio

- **Raggio di arrivo predefinito**: vale per i punti che non ne hanno uno proprio.
- **Durata massima**: zero significa nessun limite; altrimenti il viaggio si
  chiude da solo dopo quel tempo, così un tragitto che non finisce mai non ti
  prosciuga la batteria per tutta la notte.
- **Ferma sotto batteria**: zero significa trasmetti fino allo spegnimento,
  sfruttando ogni invio possibile. Qualsiasi altro valore fa partire un ultimo
  punto a quella soglia e chiude.
- **Decimali delle coordinate**: sei sono circa 11 cm. Oltre è solo rumore.

### Mappa

Di default l'app disegna i tile vettoriali di **OpenFreeMap**: nessuna chiave,
nessuna registrazione, nessun limite di richieste, licenza MIT, dati da
OpenStreetMap.

Due sostituzioni facoltative. Una **chiave MapTiler**, se preferisci quel
fornitore. Un **URL di stile personalizzato**, che ha la precedenza su tutto e
permette di puntare a uno stile ospitato da te.

Nota per chi volesse partire da questo progetto: il tile server
`tile.openstreetmap.org` **non** è un'opzione. La sua policy indica proprio
"distribuire un'app che usa i tile di openstreetmap.org" come esempio di uso
pesante vietato senza permesso preventivo. I dati sono liberi, i server sono
donati e non lo sono. L'attribuzione a OpenStreetMap e al fornitore dei tile è
obbligatoria e resta visibile sulla mappa.

### Aspetto

Tema (sistema, chiaro, scuro) e lingua (sistema, English, Italiano). La lingua
scelta qui sovrascrive quella del telefono solo per questa app.

---

## Segnaposto

Si usano in qualunque URL o messaggio. Quelli scritti male restano visibili
esattamente come li hai scritti, così gli errori si vedono invece di sparire in
silenzio.

| Segnaposto | Significato |
|---|---|
| `{lat}` `{lon}` | coordinate |
| `{timestamp}` | id del viaggio: unix di inizio condivisione |
| `{tempo}` `{tempoiso}` | ora del rilevamento |
| `{evento}` | `start`, `track` o `end` |
| `{acc}` `{vacc}` | accuratezza orizzontale e verticale, in metri |
| `{alt}` | altitudine, in metri |
| `{vel}` `{velkmh}` `{dir}` | velocità e direzione di marcia |
| `{sat}` `{satuso}` | satelliti in vista e usati nel fix |
| `{bat}` `{carica}` | percentuale batteria e stato di carica |
| `{rete}` | `wifi`, `mobile`, `ethernet`, `other` o `none` |
| `{press}` | pressione barometrica in hPa, se il telefono ha il sensore |
| `{dist}` `{dest}` | metri residui e nome della destinazione |
| `{prov}` | provider che ha prodotto il fix |
| `{ritardo}` | secondi tra rilevamento e invio |
| `{auth}` | codice che apre questo viaggio su un server I am here |
| `{yourls}` | il link accorciato, se hai impostato YOURLS |

`{timestamp}` è costante per tutto il viaggio, `{tempo}` cambia a ogni punto.
**Metti sempre `{tempo}`.** Quando la coda offline si svuota, il server riceve
un blocco di punti tutto insieme, e senza quel valore non può sapere a quando si
riferisce ciascuno.

---

## Comportamento offline

Ogni punto viene scritto in una coda locale prima di qualsiasi tentativo di
rete. Se un invio fallisce la coda si ferma invece di andare avanti: mandare
prima i punti successivi scombinerebbe l'ordine sul server. Riparte tutto in
sequenza quando la rete torna, anche a viaggio già concluso.

La schermata principale mostra quanti punti sono in attesa, con un pulsante
**Riprova ora**. Lo **Storico** elenca ogni punto col suo esito: è da lì che si
capisce se un punto mancante è colpa dell'app, della rete o del server.

---

## Buono a sapersi

**HTTPS di default.** L'HTTP in chiaro viene rifiutato finché non lo attivi in
*Impostazioni → Connessione*. Il controllo sta nell'app e non nella
configurazione di rete, che è statica e non si può cambiare mentre l'app gira.

**Limiti sugli SMS.** Android blocca l'invio automatico oltre circa 30 messaggi
in 30 minuti. L'intervallo separato per gli SMS esiste per questo.

**Le chiavi negli URL** finiscono nei log di accesso del server. Se la cosa ti
interessa, spostale in un header.

---

## Licenza

GNU General Public License v3.0 o successiva: vedi [LICENSE](../LICENSE).
Per una panoramica dell'intero progetto parti dalla [guida principale](../LEGGIMI.md).

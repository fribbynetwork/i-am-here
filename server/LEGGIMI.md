# Il server di I am here

Raccoglie le posizioni inviate dall'app e le mostra su mappa: percorso
colorato per velocità, punti cliccabili, profilo altimetrico, riepilogo ed
elenco dei viaggi.

Serve PHP 8.0 o superiore e una cartella scrivibile. Niente database, niente
framework, nessuna chiave API.

*This guide is also available [in English](README.md).*

---

Puoi vederlo in funzione, senza installare niente, su
**[demo.iamhere.it](https://demo.iamhere.it)**: stesso codice, con percorsi
generati al posto dei tuoi.

## Installazione

1. Copia tutti i file su un host con PHP 8.0 o superiore.
2. Rendi scrivibile la cartella `dati/`.
3. Rinomina `settings-default.php` in **`settings.php`** e compila le voci
   contrassegnate **CAMBIAMI**: `write_key`, `master_key` e `token_secret`.
   Devono essere **diverse fra loro** e lunghe almeno 24 caratteri casuali.

   Il pacchetto non contiene `settings.php`: le tue chiavi restano quindi
   fuori dagli aggiornamenti, e sovrascrivere i file non le tocca.
4. Apri `verifica.php` nel browser: controlla permessi, sessioni e chiavi e
   dice cosa manca. Mostra anche l'impronta di `write_key` e `token_secret`,
   sei cifre da confrontare con quelle dell'app senza scrivere le chiavi da
   nessuna parte. Quando è tutto verde, cancellalo dal server.

Poi nell'app, in *Impostazioni → Endpoint*:

```
https://tuo-server.tld/dati.php?key=LA_TUA_WRITE_KEY&id={timestamp}&gps={lat},{lon}&t={tempo}&evento={evento}&acc={acc}&alt={alt}&vel={vel}&dir={dir}&sat={sat}&satuso={satuso}&bat={bat}&rete={rete}&press={press}&dist={dist}
```

Lascia vuoti gli altri due campi URL: ereditano da questo.

---

## Le pagine

| | |
|---|---|
| `index.php?id=…&auth=…` | un viaggio sulla mappa; senza id, la pagina di presentazione |
| `viaggi.php` | l'elenco dei viaggi, protetto dalla chiave principale |
| `verifica.php` | controllo dell'installazione, da cancellare a fine configurazione |
| `dati.php` | riceve le posizioni dall'app, non si apre a mano |

Accanto al conteggio di un periodo, un anello che gira attorno al numero
segnala che lì dentro c'è una condivisione ancora aperta: si vede che qualcosa
sta arrivando adesso senza bisogno di aprire l'elenco.

In fondo al pannello dei periodi, accanto all'uscita, compare la versione dei
file caricati: serve a vedere a colpo d'occhio se un aggiornamento è arrivato
davvero o se il browser sta ancora usando la copia in cache.

**L'elenco dei viaggi.** Si entra con la chiave principale e si resta dentro
finché non si esce o non si chiude il browser; dopo cinque tentativi falliti
il modulo si blocca per dieci minuti. Le viste sono ultime 24 ore, 7 giorni,
30 giorni e poi mese per mese, e compaiono solo i periodi in cui c'è qualcosa.
Da ogni riga si apre il percorso, si copia il link da condividere o si elimina
il viaggio.

**I file.** I percorsi stanno in `dati/<anno>/<mese>/`. Per ogni viaggio ci
sono `<id>.json`, il tracciato completo, e `<id>.info.json`, il riassunto che
legge l'elenco.

---

## Importare ed esportare

Nell'elenco dei viaggi, l'icona con la freccia in alto a destra apre il
pannello degli strumenti; sulla riga di ogni viaggio la stessa icona esporta
quel percorso soltanto.

**Formati in uscita.** GPX per parlare con tutto il resto: Strava, OsmAnd,
Garmin, QGIS. JSON per spostare i viaggi fra due installazioni di I am here
senza perdere niente. GeoJSON per gli strumenti cartografici. CSV per i fogli
di calcolo.

I campi che il GPX standard non prevede, come batteria, satelliti e stato
della rete, viaggiano dentro `<extensions>`: i programmi che non li capiscono
li ignorano, e un viaggio esportato e reimportato qui non perde niente. Anche
GeoJSON e CSV conservano tutto.

**In entrata** si leggono gli stessi quattro formati, e si possono scegliere
più file insieme. L'id di un viaggio è il suo orario di partenza, quindi viene
preso dal primo punto datato del file; un tracciato senza orari finisce
datato al momento dell'importazione, perché non c'è modo di ricavarlo.

Se un viaggio del file ha la stessa data di uno già presente, viene chiesto
cosa fare: saltarlo, sostituire quello vecchio, o tenerli entrambi spostando
il nuovo di qualche secondo.

Il lettore GPX è scritto senza usare le estensioni XML di PHP. Funziona quindi
su qualunque hosting, e soprattutto non interpreta entità esterne: un file
caricato non può farsi restituire `settings.php`.

In `leaflet/` c'è già [Leaflet](https://leafletjs.com), la libreria che disegna
la mappa, distribuita con licenza BSD 2-Clause: il testo è nel file `LICENSE`
lì accanto.

## Aggiornare

Copi i file nuovi sopra i vecchi. `settings.php` non è nel pacchetto, quindi
resta dov'è con le tue chiavi. Se una versione aggiunge impostazioni, le trovi
in `settings-default.php` e le riporti a mano in `settings.php`: quelle assenti
restano al valore di partenza.

## Le impostazioni

Tutte in `settings.php`, dove ogni voce è spiegata sopra sé stessa.

### Sicurezza

**`write_key`**: la chiave che l'app deve inviare per salvare una posizione.
Chi la conosce può aggiungere punti ai tuoi percorsi.

**`require_auth`**: se serve una chiave anche per *guardare* un percorso.
Con `false` basta indovinare l'indirizzo, che contiene solo un orario: usalo
solo per le prove.

**`master_key`**: apre qualsiasi percorso ed è la password dell'elenco dei
viaggi. Non metterla nei link che condividi.

**`per_trip_tokens`**: con `true` ogni viaggio ha un codice suo e chi riceve
il link vede solo quello. Con `false` l'unica chiave è quella principale, e
chi riceve un link può cambiare l'orario nell'indirizzo e vedere gli altri
percorsi.

**`token_secret`**: il valore da cui nascono i codici dei singoli viaggi.
Serve solo con `per_trip_tokens` attivo. Lo stesso valore va incollato
nell'app, nelle impostazioni avanzate, perché possa mettere il link negli SMS.
Cambiandolo, i link già condivisi smettono di funzionare; i dati restano.

### Dati

**`data_dir`**: dove finiscono i percorsi, relativo a questa cartella oppure
assoluto. Una cartella fuori dalla radice del sito è la sistemazione migliore:
nessuno può scaricare i file scrivendone l'indirizzo. Se l'hosting non lo
permette, lascia `dati` e verifica che il `.htaccess` là dentro resti al suo
posto.

**`encrypt`** e **`encryption_key`**: cifrano i file sul disco. Servono
quando non puoi né spostare la cartella né proteggerla. **Se perdi la chiave i
percorsi cifrati sono persi**: non c'è recupero. I file già in chiaro restano
leggibili.

**`auto_delete`** e **`keep_days`**: cancellano i percorsi più vecchi del
numero di giorni indicato. La pulizia gira quando arriva una posizione nuova,
al massimo una volta all'ora.

**`timezone`**: il fuso con cui datare i viaggi. Vuoto usa quello del server,
che spesso è UTC anche quando il server sta altrove.

Conta più di quanto sembri: da qui dipendono la cartella in cui finisce un
viaggio, il giorno sotto cui compare nell'elenco e gli orari mostrati. Con il
valore sbagliato, un viaggio iniziato all'una di notte del primo del mese
risulta datato all'ultimo giorno del mese precedente. Metti il fuso di chi
porta il telefono, per esempio `Europe/Rome`: gli orari mostrati sono quelli
che erano per chi ha percorso il viaggio, non per chi guarda. L'ora legale è
gestita da sola.

**`stale_hours`**: dopo quante ore senza nuove posizioni un viaggio è
considerato concluso. Serve per i viaggi che l'app non ha chiuso, per esempio
perché il telefono si è spento.

### Mappa e aspetto

**`center_lat`**, **`center_lon`**, **`zoom`**: dove si apre la mappa quando
non c'è un percorso da mostrare. Zoom: 10 una provincia, 13 una città, 16 un
quartiere.

**`btn_fit`**, **`btn_summary`**, **`btn_elevation`**, **`btn_points`**,
**`btn_theme`**, **`btn_language`**: i pulsanti sulla mappa. `false` li
nasconde.

**`default_language`**: `auto` segue il browser di chi guarda, oppure `it` o
`en`.

**`default_theme`**: `auto` segue il dispositivo, oppure `chiaro` o `scuro`.

**`refresh_seconds`**: ogni quanto la pagina cerca nuove posizioni durante un
viaggio. Sotto i 10 secondi carichi il server per niente.

Il link del pulsante "Scarica I am here" non è fra le impostazioni: è la
costante `IAH_SITO` in cima a `lib.php`.

---

## Privacy

Condividere un link significa condividere dove sei stato: strade, soste,
orari, e con il tempo casa e luogo di lavoro. Un messaggio inoltrato per
sbaglio in un gruppo non si può richiamare indietro.

Il server tiene i percorsi fuori dai motori di ricerca (`robots.txt` e
`noindex`), impedisce che il codice del viaggio finisca ai siti raggiunti dai
link della mappa, blocca il download diretto dei file con il `.htaccess` in
`dati/` e usa un cookie di sessione `HttpOnly`, `Secure` in HTTPS.

Restano due cose da fare a te: attivare `require_auth` e usare HTTPS. Se
l'hosting non è Apache il `.htaccess` non ha effetto: sposta la cartella dei
dati fuori dalla radice, oppure attiva la cifratura.

---

## Problemi frequenti

**Entro nell'elenco ma torna il modulo, senza errori**: la sessione non viene
conservata. Apri `verifica.php` e guarda la riga "Sessioni".

**"ERRORE: chiave non valida"**: la `write_key` nell'app non corrisponde a
quella in `settings.php`. Controlla gli spazi in fondo.

**"ERRORE: cartella dati non creabile"**: il server web non può scrivere in
`data_dir`.

**Il percorso non è accessibile**: il link è vecchio, o hai cambiato
`token_secret` dopo averlo generato.

**Ho aggiornato i file e non cambia niente**: svuota la cache del browser, e
apri `verifica.php`: la riga "File aggiornati" dice quali file sono rimasti
indietro.

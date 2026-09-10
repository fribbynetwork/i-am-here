<?php
/**
 * I am here, impostazioni del server / server settings.
 *
 * È l'unico file che devi modificare. Ogni voce ha sopra la spiegazione
 * in italiano e in inglese. Le voci contrassegnate CAMBIAMI vanno
 * compilate prima di usare il server.
 *
 * This is the only file you need to edit. Every entry is explained below
 * in Italian and English. Entries marked CAMBIAMI must be filled in
 * before the server is used.
 */

return [

// =====================================================================
//  SICUREZZA / SECURITY
// =====================================================================

    /*
     * CAMBIAMI. Chiave che l'app deve inviare per salvare una posizione.
     * Va incollata anche nell'URL configurato nell'app. Trattala come una
     * password: chi la conosce può aggiungere punti ai tuoi percorsi.
     * Usa almeno 24 caratteri a caso.
     *
     * CHANGE ME. The key the app must send in order to store a position.
     * Paste the same value into the URL you configure in the app. Treat it
     * as a password: anyone holding it can add points to your journeys.
     * Use at least 24 random characters.
     */
    'write_key' => '!!!!CHANGE ME!!!!',

    /*
     * Chiede una chiave anche per GUARDARE un percorso.
     *
     * false = chiunque conosca l'indirizzo vede il percorso. L'indirizzo
     *         contiene solo un orario, quindi è facile da indovinare:
     *         lascia false solo per fare delle prove.
     * true  = serve una chiave, come spiegato nelle due voci qui sotto.
     *
     * Require a key to VIEW a journey as well.
     * false = anyone who knows the address sees the journey. The address
     *         only contains a time, so it is easy to guess: leave this on
     *         false for testing only.
     * true  = a key is needed, as described in the two entries below.
     */
    'require_auth' => true,

    /*
     * CAMBIAMI. La chiave principale. Apre qualsiasi percorso ed è la
     * password con cui entri nell'elenco dei viaggi. È la chiave da
     * proteggere di più: non metterla nei link che condividi.
     *
     * CHANGE ME. The master key. It opens any journey and is the password
     * you use to sign in to the journey list. This is the key to protect
     * most: never put it in the links you share.
     */
    'master_key' => '!!!!CHANGE ME!!!!',

    /*
     * Un indirizzo diverso per ogni viaggio.
     *
     * true  = ogni viaggio ha il suo codice. Chi riceve il link vede
     *         quel viaggio e nessun altro. Consigliato se condividi.
     * false = la chiave principale è l'unica che apre i percorsi.
     *         Più semplice se guardi solo tu.
     *
     * A different address for each journey.
     * true  = every journey has its own code. Whoever receives the link
     *         sees that journey and no other. Recommended if you share.
     * false = the master key is the only one that opens journeys.
     *         Simpler if you are the only viewer.
     */
    'per_trip_tokens' => true,

    /*
     * CAMBIAMI se la voce sopra è true. Da questo valore nascono i codici
     * dei singoli viaggi. Lo stesso valore va incollato nell'app, nelle
     * impostazioni avanzate, perché possa mettere il link negli SMS.
     *
     * Cambiarlo invalida tutti i link già condivisi, ma non i dati: i
     * percorsi restano e i nuovi link funzionano.
     *
     * CHANGE ME if the entry above is true. The per-journey codes are
     * derived from this value. Paste the same value into the app, under
     * advanced settings, so it can put the link inside text messages.
     * Changing it invalidates every link already shared, but no data: the
     * journeys stay and new links work.
     */
    'token_secret' => '!!!!CHANGE ME!!!!',

// =====================================================================
//  DATI / DATA
// =====================================================================

    /*
     * Dove finiscono i percorsi. Il percorso è relativo a questa cartella,
     * oppure assoluto.
     *
     * La scelta migliore è una cartella FUORI dalla radice del sito, per
     * esempio '/home/utente/dati-iamhere': così nessuno può scaricare i
     * file scrivendone l'indirizzo nel browser. Se il tuo hosting non te
     * lo permette, lascia 'dati' e assicurati che il file .htaccess che
     * trovi lì dentro sia rimasto al suo posto.
     *
     * Where journeys are stored. The path is relative to this folder, or
     * absolute. The best choice is a folder OUTSIDE the website root, for
     * example '/home/user/iamhere-data': that way nobody can download the
     * files by typing their address into a browser. If your hosting does
     * not allow it, leave 'dati' and make sure the .htaccess file you find
     * in there stays where it is.
     */
    'data_dir' => 'dati',

    /*
     * Cifra i file dei percorsi sul disco.
     *
     * Serve se non puoi mettere la cartella fuori dalla radice del sito
     * né proteggerla: i file diventano illeggibili anche a chi li scarica.
     *
     * ATTENZIONE: se perdi la chiave qui sotto, i percorsi cifrati sono
     * persi per sempre. Non esiste modo di recuperarli.
     *
     * I file già salvati in chiaro restano leggibili: la cifratura si
     * applica da qui in avanti, viaggio per viaggio.
     *
     * Encrypt the journey files on disk. Useful when you cannot move the
     * folder outside the website root nor protect it: the files become
     * unreadable even to someone who downloads them.
     * WARNING: if you lose the key below, encrypted journeys are lost for
     * good. There is no recovery. Files already stored in the clear stay
     * readable: encryption applies from now on, journey by journey.
     */
    'encrypt' => false,

    /*
     * CAMBIAMI se la voce sopra è true. Conservane una copia altrove.
     * CHANGE ME if the entry above is true. Keep a copy somewhere safe.
     */
    'encryption_key' => '!!!!CHANGE ME!!!!',

    /*
     * Cancella da solo i percorsi vecchi. La pulizia avviene quando
     * arriva una nuova posizione, quindi non serve configurare niente
     * sull'hosting.
     *
     * Automatically delete old journeys. The cleanup runs when a new
     * position arrives, so there is nothing to configure on your hosting.
     */
    'auto_delete' => false,

    /*
     * Dopo quanti giorni cancellare. Vale solo se la voce sopra è true.
     * How many days to keep. Only applies if the entry above is true.
     */
    'keep_days' => 30,

    /*
     * Dopo quante ore senza nuove posizioni un viaggio è considerato
     * concluso. Serve per i viaggi che l'app non ha chiuso, per esempio
     * perché il telefono si è spento.
     *
     * After how many hours without new positions a journey counts as
     * finished. This covers journeys the app never closed, for instance
     * because the phone ran out of battery.
     */
    'stale_hours' => 6,

    /*
     * Il fuso orario con cui datare i viaggi. Vuoto usa quello del
     * server, che spesso e UTC anche quando il server sta altrove.
     *
     * Conta piu di quanto sembri: da qui dipendono la cartella in cui
     * finisce un viaggio, il giorno sotto cui compare nell'elenco e gli
     * orari mostrati. Con il valore sbagliato, un viaggio iniziato all'una
     * di notte del primo del mese risulta datato all'ultimo giorno del
     * mese precedente.
     *
     * Metti il fuso di chi porta il telefono, non di chi guarda: un
     * viaggio appartiene a chi l'ha percorso. Per esempio 'Europe/Rome'.
     * L'ora legale e gestita da sola.
     *
     * The time zone journeys are dated with. Empty uses the server's own,
     * which is often UTC even when the server sits elsewhere. It decides
     * the folder a journey is filed under, the day it appears on in the
     * list, and the times shown. Use the time zone of whoever carries the
     * phone, not of whoever is looking: a journey belongs to the person
     * who travelled it. Daylight saving is handled for you.
     */
    'timezone' => '',

// =====================================================================
//  MAPPA / MAP
// =====================================================================

    /*
     * Dove si apre la mappa quando non c'è nessun percorso da mostrare.
     * Di base è il centro di Bologna. Cerca le coordinate del posto che
     * preferisci su openstreetmap.org e incollale qui.
     *
     * Where the map opens when there is no journey to show. Defaults to
     * the centre of Bologna. Look up the coordinates of the place you
     * prefer on openstreetmap.org and paste them here.
     */
    'center_lat' => 44.493772,
    'center_lon' => 11.343093,

    /*
     * Quanto è avvicinata la mappa: 10 mostra una provincia, 13 una
     * città, 16 un quartiere.
     * How close the map is: 10 shows a province, 13 a city, 16 a district.
     */
    'zoom' => 13,

// =====================================================================
//  ASPETTO / APPEARANCE
// =====================================================================

    /*
     * I pulsanti nella barra a destra della mappa. Metti false per
     * nascondere quelli che non ti servono.
     * The buttons in the bar to the right of the map. Set false to hide
     * the ones you do not need.
     */
    'btn_fit'       => true,   // inquadra tutto / segui l'ultimo punto
    'btn_summary'   => true,   // riepilogo del viaggio / journey summary
    'btn_elevation' => true,   // profilo altimetrico / elevation profile
    'btn_points'    => true,   // mostra o nascondi i punti / show waypoints
    'btn_theme'     => true,   // tema chiaro e scuro / light and dark theme
    'btn_language'  => true,   // lingua / language

    /*
     * Lingua di partenza: 'auto' segue il browser di chi guarda.
     * Starting language: 'auto' follows the visitor's browser.
     * 'auto' | 'it' | 'en'
     */
    'default_language' => 'auto',

    /*
     * Tema di partenza: 'auto' segue le impostazioni del dispositivo.
     * Starting theme: 'auto' follows the device settings.
     * 'auto' | 'chiaro' | 'scuro'
     */
    'default_theme' => 'auto',

    /*
     * Ogni quanti secondi la pagina cerca nuove posizioni mentre un
     * viaggio è in corso. Sotto i 10 secondi si carica il server senza
     * guadagnare nulla: la posizione nuova arriva comunque alla cadenza
     * impostata nell'app.
     *
     * How often the page looks for new positions while a journey is
     * running. Below 10 seconds you load the server for nothing: new
     * positions still arrive at the rate set in the app.
     */
    'refresh_seconds' => 30,

];

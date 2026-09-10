<?php
/* IAH-VERSION 1.0.6.2 */
/**
 * I am here, elenco dei viaggi / journey list.
 *
 * Area riservata: si entra con la chiave principale di settings.php e si
 * resta dentro finché non si esce o non si chiude il browser.
 *
 * La pagina è la stessa applicazione di index.php, mappa a tutto schermo
 * con i pannelli sopra, più un elenco da cui scegliere il percorso. I
 * dati arrivano dalle richieste ?api=..., quindi cambiare viaggio non
 * ricarica niente.
 *
 * Dentro la sessione il codice del viaggio non serve: è la sessione a
 * fare da chiave. Il codice resta per i link che si condividono.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

header('X-Robots-Tag: noindex, nofollow');
header('Referrer-Policy: strict-origin');

session_set_cookie_params([
    'path'     => '/',
    'httponly' => true,          // il cookie non è leggibile dal JavaScript
    'samesite' => 'Lax',         // non viene inviato da siti terzi
    'secure'   => sito_https(),  // in HTTPS non viaggia mai in chiaro
]);
session_start();

// ================================================================ accesso

/**
 * Limite ai tentativi: dopo cinque errori il modulo si blocca per dieci
 * minuti. È questo a rendere inutile un attacco a forza bruta, molto più
 * di quanto farebbe un nome utente in aggiunta alla chiave.
 */
const MAX_TENTATIVI = 5;
const BLOCCO_SEC = 600;

function file_tentativi(): string { return cartella_dati() . '/.tentativi.json'; }

function tentativi_ip(): array
{
    $t = leggi_json(file_tentativi()) ?? [];
    $ip = $_SERVER['REMOTE_ADDR'] ?? '?';
    return [$t, $ip, $t[$ip] ?? ['n' => 0, 'fino' => 0]];
}

function bloccato(): int
{
    [, , $v] = tentativi_ip();
    return max(0, ((int) $v['fino']) - time());
}

function segna_tentativo(bool $ok): void
{
    [$t, $ip, $v] = tentativi_ip();
    if ($ok) {
        unset($t[$ip]);
    } else {
        $v['n'] = ((int) $v['n']) + 1;
        if ($v['n'] >= MAX_TENTATIVI) { $v['fino'] = time() + BLOCCO_SEC; $v['n'] = 0; }
        $t[$ip] = $v;
    }
    // Ripulisce le voci vecchie, altrimenti il file cresce all'infinito.
    foreach ($t as $k => $x) {
        if ((int) ($x['fino'] ?? 0) < time() - 86400 && (int) ($x['n'] ?? 0) === 0) { unset($t[$k]); }
    }
    scrivi_json(file_tentativi(), $t);
}

$errore = null;

if (isset($_GET['esci'])) {
    session_destroy();
    header('Location: ' . strtok((string) $_SERVER['REQUEST_URI'], '?'));
    exit;
}

if (($_POST['azione'] ?? '') === 'entra') {
    if (bloccato() > 0) {
        $errore = 'bloccato';
    } elseif (hash_equals((string) cfg('master_key'), (string) ($_POST['chiave'] ?? ''))) {
        session_regenerate_id(true);   // impedisce il riuso di una sessione preesistente
        $_SESSION['dentro'] = true;
        $_SESSION['csrf'] = bin2hex(random_bytes(16));
        segna_tentativo(true);
        // ?ok=1 distingue chi arriva per la prima volta da chi ha inserito
        // la chiave giusta e si è visto perdere la sessione.
        header('Location: ' . strtok((string) $_SERVER['REQUEST_URI'], '?') . '?ok=1');
        exit;
    } else {
        segna_tentativo(false);
        $errore = 'chiave';
    }
}

$dentro = !empty($_SESSION['dentro']) || !cfg('require_auth');

if (!$dentro && isset($_GET['ok'])) {
    $errore = 'sessione';
}

// ==================================================================== api

/**
 * Tutte le risposte in JSON per il JavaScript. Ogni richiesta ricontrolla
 * la sessione: senza, basterebbe conoscere l'indirizzo di un'api.
 */
$api = $_GET['api'] ?? null;

if ($api !== null) {
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    if (!$dentro) {
        http_response_code(403);
        exit(json_encode(['errore' => 'sessione']));
    }

    /** Gli anni e i mesi in cui ci sono viaggi, con quanti ce ne sono. */
    if ($api === 'periodi') {
        $conteggio = [];   // 'AAAA-MM' => numero di viaggi

        foreach (glob(cartella_dati() . '/[0-9][0-9][0-9][0-9]/[0-9][0-9]', GLOB_ONLYDIR) ?: [] as $d) {
            $k = basename(dirname($d)) . '-' . basename($d);
            // I tracciati, non i riassunti: questi ultimi nascono quando
            // si apre il mese, e prima di allora il conteggio sarebbe zero.
            $n = 0;
            foreach (glob("$d/*.json") ?: [] as $f) {
                if (!str_ends_with($f, '.info.json')) { $n++; }
            }
            $conteggio[$k] = $n;
        }
        // Anche i file mai migrati, che stanno nella vecchia cartella piatta.
        foreach (glob(cartella_dati() . '/*.json') ?: [] as $f) {
            if (str_ends_with($f, '.info.json')) { continue; }
            $vid = id_valido(basename($f, '.json'));
            if ($vid !== null) {
                $k = date('Y-m', $vid);
                $conteggio[$k] = ($conteggio[$k] ?? 0) + 1;
            }
        }
        $conteggio = array_filter($conteggio, static fn($n) => $n > 0);
        krsort($conteggio);

        $anni = [];
        foreach (array_keys($conteggio) as $k) { $anni[substr($k, 0, 4)] = true; }
        // Le chiavi numeriche di PHP diventano interi: il JavaScript
        // confronta con una stringa, quindi vanno riportate a stringa.
        $anni = array_map('strval', array_keys($anni));
        rsort($anni);

        $anno = (string) ($_GET['anno'] ?? ($anni[0] ?? date('Y')));
        $mesi = [];
        $nomi = ['', 'gennaio','febbraio','marzo','aprile','maggio','giugno',
                 'luglio','agosto','settembre','ottobre','novembre','dicembre'];
        $nomiEn = ['', 'January','February','March','April','May','June',
                   'July','August','September','October','November','December'];
        foreach ($conteggio as $k => $n) {
            if (substr($k, 0, 4) !== $anno) { continue; }
            $m = (int) substr($k, 5, 2);
            $mesi[] = ['k' => $k, 'it' => $nomi[$m], 'en' => $nomiEn[$m], 'n' => $n];
        }
        // Quanti viaggi nei periodi rapidi: lo stesso numero che compare
        // accanto ai mesi, per non lasciare quelle tre voci senza.
        // Insieme si segnala dove c'e una condivisione ancora aperta, cosi
        // il pannello puo dirlo senza che si debba aprire l'elenco.
        $rapidi = [];
        $corso = [];
        foreach (['24h' => 1, '7g' => 7, '30g' => 30] as $k => $gg) {
            $trovati = viaggi_fra(time() - $gg * 86400, time());
            $rapidi[$k] = count($trovati);
            foreach ($trovati as $r) {
                if (!viaggio_concluso($r)) { $corso[] = $k; break; }
            }
        }
        // Un viaggio ancora aperto e per forza recente, quindi basta
        // guardare il mese in corso.
        $meseOra = date('Y-m');
        foreach (viaggi_del_periodo($meseOra) as $r) {
            if (!viaggio_concluso($r)) { $corso[] = $meseOra; break; }
        }

        exit(json_encode([
            'anni' => $anni, 'anno' => $anno, 'mesi' => $mesi,
            'rapidi' => $rapidi, 'corso' => $corso,
        ]));
    }

    /** I viaggi di un periodo, dal più recente. */
    if ($api === 'viaggi') {
        $out = [];
        foreach (viaggi_del_periodo((string) ($_GET['v'] ?? '7g')) as $id => $r) {
            $inizio = (int) ($r['inizio'] ?? $id);
            $ultimo = (int) ($r['ultimo'] ?? $inizio);
            $out[] = [
                'id'       => $id,
                'inizio'   => $inizio,
                'ultimo'   => $ultimo,
                'giorno'   => date('Y-m-d', $inizio),
                // Gia formattati qui: il browser di chi guarda potrebbe
                // stare in un altro fuso, e un viaggio va mostrato con
                // l'ora che era per chi l'ha percorso.
                'oraInizio' => date('H:i', $inizio),
                'oraFine'   => date('H:i', $ultimo),
                'punti'    => (int) ($r['punti'] ?? 0),
                'distanza' => isset($r['distanza']) ? (float) $r['distanza'] : null,
                'durata'   => $ultimo > $inizio ? $ultimo - $inizio : null,
                'dest'     => $r['dest'] ?? null,
                'corso'    => !viaggio_concluso($r),
                'link'     => link_viaggio($id),   // con il codice: è quello da condividere
            ];
        }
        exit(json_encode(['viaggi' => $out]));
    }

    /** Il tracciato di un viaggio, nella forma che si aspetta la mappa. */
    if ($api === 'tracciato') {
        $vid = id_valido($_GET['id'] ?? '');
        $f = $vid !== null ? trova_tracciato($vid) : null;
        exit(json_encode(['id' => $vid, 'punti' => $f !== null ? normalizza_punti(leggi_json($f)) : []]));
    }

    /** Cancellazione. Il token impedisce che lo faccia un altro sito
     *  sfruttando la sessione aperta nel browser. */
    if ($api === 'elimina' && $_SERVER['REQUEST_METHOD'] === 'POST') {
        if (!hash_equals((string) ($_SESSION['csrf'] ?? ''), (string) ($_POST['csrf'] ?? ''))) {
            http_response_code(403);
            exit(json_encode(['errore' => 'csrf']));
        }
        $vid = id_valido($_POST['id'] ?? '');
        exit(json_encode(['ok' => $vid !== null && elimina_viaggio($vid)]));
    }

    /* ---------------------------------------------------- esportazione */
    if ($api === 'esporta') {
        require_once __DIR__ . '/formati.php';

        $formato = (string) ($_GET['formato'] ?? 'gpx');
        if (!isset(formati_uscita()[$formato])) {
            http_response_code(400);
            exit(json_encode(['errore' => 'formato']));
        }

        // Che cosa esportare: un viaggio, un periodo, o tutto l'archivio.
        $ambito = (string) ($_GET['ambito'] ?? 'viaggio');
        $ids = [];
        if ($ambito === 'viaggio') {
            $vid = id_valido($_GET['id'] ?? '');
            if ($vid !== null) { $ids = [$vid]; }
        } elseif ($ambito === 'tutto') {
            $ids = tutti_gli_id();
        } else {
            foreach (array_keys(viaggi_del_periodo((string) ($_GET['v'] ?? '7g'))) as $k) {
                $ids[] = (int) $k;
            }
        }
        if ($ids === []) {
            http_response_code(404);
            exit(json_encode(['errore' => 'vuoto']));
        }

        $viaggi = [];
        foreach ($ids as $vid) {
            $f = trova_tracciato($vid);
            if ($f === null) { continue; }
            $punti = normalizza_punti(leggi_json($f));
            if ($punti !== []) { $viaggi[] = ['id' => $vid, 'punti' => $punti]; }
        }

        $info = formati_uscita()[$formato];
        $nome = 'iamhere-' . ($ambito === 'viaggio' ? (string) $ids[0] : $ambito)
              . '-' . date('Ymd') . '.' . $info['ext'];

        header('Content-Type: ' . $info['mime'] . '; charset=utf-8');
        header('Content-Disposition: attachment; filename="' . $nome . '"');
        header('X-Content-Type-Options: nosniff');
        echo esporta($viaggi, $formato);
        exit;
    }

    /* ---------------------------------------------------- importazione */
    if ($api === 'importa' && $_SERVER['REQUEST_METHOD'] === 'POST') {
        require_once __DIR__ . '/formati.php';

        if (!hash_equals((string) ($_SESSION['csrf'] ?? ''), (string) ($_POST['csrf'] ?? ''))) {
            http_response_code(403);
            exit(json_encode(['errore' => 'csrf']));
        }

        $trovati = [];
        $problemi = [];
        foreach (($_FILES['file']['tmp_name'] ?? []) as $i => $tmp) {
            $nome = basename((string) ($_FILES['file']['name'][$i] ?? '?'));
            if (!is_uploaded_file((string) $tmp)) { $problemi[] = $nome; continue; }
            // Un tetto alla dimensione: senza, un file enorme esaurisce
            // la memoria prima ancora di essere riconosciuto.
            if (filesize((string) $tmp) > 30 * 1024 * 1024) { $problemi[] = $nome; continue; }

            $r = importa((string) file_get_contents((string) $tmp));
            if ($r['errore'] !== null && $r['viaggi'] === []) { $problemi[] = $nome; continue; }
            foreach ($r['viaggi'] as $v) { $trovati[$v['id']] = $v; }
        }

        if ($trovati === []) {
            exit(json_encode(['viaggi' => 0, 'conflitti' => 0, 'problemi' => $problemi]));
        }

        // I viaggi restano in un file temporaneo fino alla conferma: la
        // domanda sui conflitti va posta prima di toccare l'archivio.
        $token = bin2hex(random_bytes(8));
        scrivi_json(cartella_dati() . "/.import-$token.json", array_values($trovati));

        $conflitti = 0;
        foreach ($trovati as $id => $v) {
            if (trova_tracciato((int) $id) !== null) { $conflitti++; }
        }
        exit(json_encode([
            'token' => $token,
            'viaggi' => count($trovati),
            'conflitti' => $conflitti,
            'problemi' => $problemi,
        ]));
    }

    /* Seconda fase: si applica quello che era stato letto. */
    if ($api === 'importa-conferma' && $_SERVER['REQUEST_METHOD'] === 'POST') {
        if (!hash_equals((string) ($_SESSION['csrf'] ?? ''), (string) ($_POST['csrf'] ?? ''))) {
            http_response_code(403);
            exit(json_encode(['errore' => 'csrf']));
        }
        $token = preg_replace('/[^a-f0-9]/', '', (string) ($_POST['token'] ?? ''));
        $f = cartella_dati() . "/.import-$token.json";
        $viaggi = $token !== '' ? leggi_json($f) : null;
        if (!is_array($viaggi)) {
            http_response_code(404);
            exit(json_encode(['errore' => 'scaduto']));
        }

        // salta | sostituisci | affianca
        $scelta = (string) ($_POST['scelta'] ?? 'salta');
        $fatti = $saltati = 0;

        foreach ($viaggi as $v) {
            $id = (int) $v['id'];
            $punti = is_array($v['punti'] ?? null) ? $v['punti'] : [];
            if ($punti === []) { continue; }

            if (trova_tracciato($id) !== null) {
                if ($scelta === 'salta') { $saltati++; continue; }
                if ($scelta === 'affianca') {
                    // Si sposta in avanti finche non trova un posto libero.
                    while (trova_tracciato($id) !== null) { $id++; }
                } else {
                    elimina_viaggio($id);
                }
            }
            if (scrivi_json(file_tracciato($id), $punti)) {
                @unlink(file_riassunto($id));   // lo ricostruisce il server
                $fatti++;
            }
        }
        @unlink($f);
        exit(json_encode(['importati' => $fatti, 'saltati' => $saltati]));
    }

    http_response_code(404);
    exit(json_encode(['errore' => 'sconosciuta']));
}

// ================================================================ pagina

$bootstrap = [
    'modo'      => 'viaggi',
    'punti'     => [],
    'refreshMs' => max(5, (int) cfg('refresh_seconds')) * 1000,
    'centro'    => [(float) cfg('center_lat'), (float) cfg('center_lon')],
    'zoom'      => (int) cfg('zoom'),
    'lingua'    => cfg('default_language'),
    'csrf'      => $_SESSION['csrf'] ?? '',
    'versione'  => IAH_VERSION,
    'pulsanti'  => [
        'fit'       => (bool) cfg('btn_fit'),
        'riepilogo' => (bool) cfg('btn_summary'),
        'quota'     => (bool) cfg('btn_elevation'),
        'punti'     => (bool) cfg('btn_points'),
        'tema'      => (bool) cfg('btn_theme'),
        'lingua'    => (bool) cfg('btn_language'),
    ],
];

/** Il logo, usato in più punti della pagina. */
function logo(string $id): string
{
    return '<svg class="mark" viewBox="18 18 72 72" aria-hidden="true">'
        . "<defs><clipPath id=\"$id\"><rect x=\"18\" y=\"18\" width=\"72\" height=\"72\" rx=\"16\"/></clipPath></defs>"
        . "<g clip-path=\"url(#$id)\">"
        . '<rect x="18" y="18" width="72" height="72" fill="#0E3B43"/>'
        . '<path d="M-8,86 C20,86 30,58 52,54 C74,50 84,26 114,24" fill="none" stroke="#164F5A" stroke-width="9" stroke-linecap="round"/>'
        . '<path d="M-8,28 C20,28 30,66 52,72 C74,78 84,100 114,100" fill="none" stroke="#1C6E7E" stroke-width="7" stroke-linecap="round"/>'
        . '<g stroke="#1C6E7E" stroke-width="3" opacity=".45" fill="none">'
        . '<path d="M-8,44 L114,44"/><path d="M-8,84 L114,84"/><path d="M44,-8 L44,114"/><path d="M78,-8 L78,114"/></g>'
        . '<g transform="translate(54,54) scale(.75) translate(-54,-54)">'
        . '<path d="M43.5,30 A10.5,10.5 0 0 1 64.5,30 L62.5,66 A8.5,8.5 0 0 1 45.5,66 Z" fill="#E4572E"/>'
        . '<circle cx="54" cy="86" r="7.5" fill="#E4572E"/></g></g></svg>';
}
?>
<!DOCTYPE html>
<html lang="it">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="robots" content="noindex, nofollow">
<meta name="referrer" content="strict-origin">
<title>I tuoi viaggi · I am here</title>
<link rel="icon" href="favicon.ico" sizes="32x32">
<link rel="icon" href="favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<link rel="manifest" href="site.webmanifest">
<!-- La barra del browser si intona al tema in uso. -->
<meta name="theme-color" media="(prefers-color-scheme: light)" content="#F2F5F4">
<meta name="theme-color" media="(prefers-color-scheme: dark)" content="#0B1416">
<script>
(function () {
  var pref = <?= json_encode(cfg('default_theme')) ?>;
  var s = 'auto';
  try { s = localStorage.getItem('iamhere-tema') || (pref === 'auto' ? 'auto' : pref); } catch (e) {}
  var d = document.documentElement;
  d.dataset.tema = s;
  d.dataset.attivo = s === 'auto'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'scuro' : 'chiaro')
    : s;
})();
</script>
<link rel="stylesheet" href="leaflet/leaflet.css">
<script src="leaflet/leaflet.js"></script>
<link rel="stylesheet" href="style.css?v=<?= IAH_VERSION ?>">
</head>
<body>

<div id="map"<?= $dentro ? '' : ' class="sfondo-statico"' ?>></div>

<?php if (!$dentro): ?>

<div id="invite">
  <div class="card surface">
    <div class="intestazione"><?= logo('lgA') ?><span>I am here</span></div>
    <h1>I tuoi viaggi</h1>
    <p>Inserisci la chiave principale per vedere l'elenco dei percorsi.</p>
    <?php if ($errore === 'sessione'): ?>
      <p class="errore">La chiave è corretta, ma la sessione non viene conservata.
        Apri <a href="verifica.php">verifica.php</a> per capire perché.</p>
    <?php elseif ($errore === 'bloccato'): ?>
      <p class="errore">Troppi tentativi. Riprova fra <?= (int) ceil(bloccato() / 60) ?> minuti.</p>
    <?php elseif ($errore === 'chiave'): ?>
      <p class="errore">Chiave non valida.</p>
    <?php endif; ?>
    <form method="post" autocomplete="off">
      <input type="hidden" name="azione" value="entra">
      <input type="password" name="chiave" placeholder="Chiave principale" autofocus required>
      <button type="submit" class="principale">Entra</button>
    </form>
    <a class="secondario" href="index.php">Torna alla pagina iniziale</a>
  </div>
</div>

<?php // Senza Leaflet non c'e il suo riquadro a portare l'attribuzione,
      // che pero e dovuta lo stesso. ?>
<p class="credito-mappa">
  Map data &copy; <a href="https://www.openstreetmap.org/copyright" rel="noopener">OpenStreetMap</a>
  contributors, SRTM &middot; style <a href="https://opentopomap.org" rel="noopener">OpenTopoMap</a> (CC-BY-SA)
</p>

<?php else: ?>

<div id="brand" class="surface">
  <?= logo('lg') ?>
  <div>
    <div class="name">I am here</div>
    <div class="state" id="brandSub"></div>
  </div>
</div>

<div id="tools"></div>

<div id="legend" class="surface">
  <div class="title" id="legTitolo"></div>
  <div class="sub" id="legSotto"></div>
  <div class="bar"></div>
  <div class="ends"><span id="legLo"></span><span id="legMid"></span><span id="legHi"></span></div>
</div>

<div id="sheet" class="surface"></div>

<!-- Due pannelli affiancati su schermo largo, uno alla volta sul
     telefono. Quale sia visibile lo decide data-livello sul body, cosi
     la logica sta nel CSS e il JavaScript cambia un solo attributo. -->
<aside id="pp" class="pannello surface"></aside>
<aside id="pv" class="pannello surface"></aside>
<button id="riapri" class="tool" type="button" hidden aria-label="Mostra l'elenco">
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"
       stroke-linecap="round" stroke-linejoin="round">
    <path d="M4 7h16M4 12h16M4 17h10"/>
  </svg>
</button>

<script>
const DATI = <?= json_encode($bootstrap, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE) ?>;
</script>
<script src="app.js?v=<?= IAH_VERSION ?>"></script>
<script src="viaggi.js?v=<?= IAH_VERSION ?>"></script>

<?php endif; ?>
</body>
</html>

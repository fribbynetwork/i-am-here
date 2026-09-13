<?php
/* IAH-VERSION 1.1 */
/**
 * I am here, visualizzazione del percorso / journey viewer.
 *
 * Legge il tracciato di un viaggio e lo disegna sulla mappa. Senza id
 * mostra la pagina di presentazione con i pulsanti per scaricare l'app e
 * per entrare nell'elenco dei viaggi.
 *
 * Con ?id=<id>&format=json restituisce solo i dati: è così che la pagina
 * si aggiorna da sola senza ricaricarsi, mantenendo zoom e posizione.
 *
 * Non c'è niente da configurare qui: tutto sta in settings.php.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

$id   = id_valido($_GET['id'] ?? '');
$auth = isset($_GET['auth']) ? (string) $_GET['auth'] : null;

$punti = [];
$negato = false;

if ($id !== null) {
    if (!auth_ok($id, $auth)) {
        $negato = true;
    } else {
        $f = trova_tracciato($id);
        $punti = $f !== null ? normalizza_punti(leggi_json($f)) : [];
    }
}

// Endpoint dati per l'aggiornamento automatico senza ricaricare la pagina.
if (($_GET['format'] ?? '') === 'json') {
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    header('X-Robots-Tag: noindex, nofollow');
    if ($negato) {
        http_response_code(403);
        echo json_encode(['errore' => 'auth']);
        exit;
    }
    echo json_encode(['id' => $id, 'punti' => $punti], JSON_UNESCAPED_SLASHES);
    exit;
}

header('X-Robots-Tag: noindex, nofollow');
// strict-origin manda al server delle mappe solo il dominio, mai
// l'indirizzo completo: OpenStreetMap rifiuta le richieste prive di
// referrer, ma il codice del viaggio non deve uscire di qui.
header('Referrer-Policy: strict-origin');

$bootstrap = [
    'modo'      => 'index',
    'id'        => $id,
    'auth'      => $auth,
    'punti'     => $punti,
    'negato'    => $negato,
    'refreshMs' => max(5, (int) cfg('refresh_seconds')) * 1000,
    'download'  => IAH_SITO,
    'centro'    => [(float) cfg('center_lat'), (float) cfg('center_lon')],
    'zoom'      => (int) cfg('zoom'),
    'lingua'    => cfg('default_language'),
    'tema'      => cfg('default_theme'),
    'pulsanti'  => [
        'fit'       => (bool) cfg('btn_fit'),
        'riepilogo' => (bool) cfg('btn_summary'),
        'quota'     => (bool) cfg('btn_elevation'),
        'punti'     => (bool) cfg('btn_points'),
        'tema'      => (bool) cfg('btn_theme'),
        'lingua'    => (bool) cfg('btn_language'),
    ],
];
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<meta name="robots" content="noindex, nofollow">
<!-- Manda solo il dominio, mai l'indirizzo completo: cosi il codice del
     viaggio non esce, e il server delle mappe riceve il referrer che
     pretende per servire le tile. -->
<meta name="referrer" content="strict-origin">
<title>I am here</title>
<link rel="icon" href="favicon.ico" sizes="32x32">
<link rel="icon" href="favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<link rel="manifest" href="site.webmanifest">
<!-- La barra del browser si intona al tema in uso. -->
<meta name="theme-color" media="(prefers-color-scheme: light)" content="#F2F5F4">
<meta name="theme-color" media="(prefers-color-scheme: dark)" content="#0B1416">
<script>
(function () {
  // Il tema va risolto prima che il browser dipinga, altrimenti al
  // caricamento si vede un lampo del tema sbagliato.
  var pref = <?= json_encode(cfg('default_theme')) ?>;
  var scelta = 'auto';
  try { scelta = localStorage.getItem('iamhere-tema') || (pref === 'auto' ? 'auto' : pref); } catch (e) {}
  var d = document.documentElement;
  d.dataset.tema = scelta;
  d.dataset.attivo = scelta === 'auto'
    ? (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'scuro' : 'chiaro')
    : scelta;
})();
</script>
<link rel="stylesheet" href="leaflet/leaflet.css">
<script src="leaflet/leaflet.js"></script>
<link rel="stylesheet" href="style.css?v=<?= IAH_VERSION ?>">
</head>
<body>

<div id="map"></div>

<div id="brand" class="surface">
  <svg class="mark" viewBox="18 18 72 72" aria-hidden="true">
    <defs><clipPath id="lg"><rect x="18" y="18" width="72" height="72" rx="16"/></clipPath></defs>
    <g clip-path="url(#lg)">
      <rect x="18" y="18" width="72" height="72" fill="#0E3B43"/>
      <path d="M-8,86 C20,86 30,58 52,54 C74,50 84,26 114,24" fill="none" stroke="#164F5A" stroke-width="9" stroke-linecap="round"/>
      <path d="M-8,28 C20,28 30,66 52,72 C74,78 84,100 114,100" fill="none" stroke="#1C6E7E" stroke-width="7" stroke-linecap="round"/>
      <g stroke="#1C6E7E" stroke-width="3" opacity=".45" fill="none">
        <path d="M-8,44 L114,44"/><path d="M-8,84 L114,84"/><path d="M44,-8 L44,114"/><path d="M78,-8 L78,114"/>
      </g>
      <g transform="translate(54,54) scale(.75) translate(-54,-54)">
        <path d="M43.5,30 A10.5,10.5 0 0 1 64.5,30 L62.5,66 A8.5,8.5 0 0 1 45.5,66 Z" fill="#E4572E"/>
        <circle cx="54" cy="86" r="7.5" fill="#E4572E"/>
      </g>
    </g>
  </svg>
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

<script>
const DATI = <?= json_encode($bootstrap, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE) ?>;
</script>
<script src="app.js?v=<?= IAH_VERSION ?>"></script>
</body>
</html>

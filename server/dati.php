<?php
/* IAH-VERSION 1.0.6.2 */
/**
 * I am here, endpoint di raccolta / collection endpoint.
 *
 * Riceve una posizione dall'app e la accoda al tracciato del viaggio,
 * aggiornando il riassunto. Accetta GET, POST form e POST JSON: i
 * parametri arrivano allo stesso modo in tutti e tre i casi, perché è
 * l'app a spostare la query string nel corpo quando serve.
 *
 * Non c'è niente da configurare qui: tutto sta in settings.php.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);
require __DIR__ . '/lib.php';

header('Content-Type: text/plain; charset=utf-8');

// POST JSON: i campi arrivano nel corpo, non fra i parametri.
if (stripos($_SERVER['CONTENT_TYPE'] ?? '', 'application/json') !== false) {
    $corpo = json_decode((string) file_get_contents('php://input'), true);
    if (is_array($corpo)) {
        $_REQUEST = array_merge($_REQUEST, $corpo);
    }
}

/** Legge un parametro trattando la stringa vuota come assente. */
function param(string $n): ?string
{
    return isset($_REQUEST[$n]) && $_REQUEST[$n] !== '' ? (string) $_REQUEST[$n] : null;
}
function num(string $n): ?float
{
    $v = param($n);
    return $v !== null && is_numeric($v) ? (float) $v : null;
}
function intero(string $n): ?int
{
    $v = param($n);
    return $v !== null && is_numeric($v) ? (int) $v : null;
}

// --- chiave di scrittura ---------------------------------------------------
// Confronto in tempo costante, per le stesse ragioni spiegate in lib.php.
if (!hash_equals((string) cfg('write_key'), (string) param('key'))) {
    http_response_code(403);
    exit('ERRORE: chiave non valida');
}

// --- id del viaggio --------------------------------------------------------
// Ripulirlo non è una formalità: senza, un id come "../config" permette
// di scrivere fuori dalla cartella dei dati.
$id = id_valido(param('id'));
if ($id === null) {
    http_response_code(400);
    exit('ERRORE: id mancante o non valido');
}

// --- posizione -------------------------------------------------------------
// Accetta sia "gps=lat,lon" sia lat e lon come parametri separati.
$lat = num('lat');
$lon = num('lon');
if ($lat === null || $lon === null) {
    $parti = array_map('trim', explode(',', (string) param('gps')));
    if (count($parti) === 2 && is_numeric($parti[0]) && is_numeric($parti[1])) {
        $lat = (float) $parti[0];
        $lon = (float) $parti[1];
    }
}
if ($lat === null || $lon === null || abs($lat) > 90 || abs($lon) > 180) {
    http_response_code(400);
    exit('ERRORE: coordinate non valide');
}

$punto = [
    'lat'      => $lat,
    'lon'      => $lon,
    't'        => intero('t') ?? time(),   // ora del rilevamento
    'ricevuto' => time(),                  // ora di arrivo al server
    'evento'   => param('evento'),
    'acc'      => num('acc'),
    'vacc'     => num('vacc'),
    'alt'      => num('alt'),
    'vel'      => num('vel'),
    'dir'      => num('dir'),
    'sat'      => intero('sat'),
    'satuso'   => intero('satuso'),
    'bat'      => intero('bat'),
    'carica'   => intero('carica'),
    'rete'     => param('rete'),
    'press'    => num('press'),
    'dist'     => num('dist'),
    'prov'     => param('prov'),
    'dest'     => param('dest'),
    'ritardo'  => intero('ritardo'),
];
// I campi che l'app non ha inviato spariscono invece di finire come null.
$punto = array_filter($punto, static fn($v) => $v !== null);

// --- salvataggio -----------------------------------------------------------
$file = file_tracciato($id);
$dir  = dirname($file);
if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
    http_response_code(500);
    exit('ERRORE: cartella dati non creabile');
}

/*
 * Lock su un file dedicato invece che sul tracciato stesso: la scrittura
 * avviene per rinomina, quindi bloccare il file di destinazione non
 * servirebbe. Quando la coda offline dell'app si svuota arrivano più
 * richieste ravvicinate, e senza lock due di esse si sovrascriverebbero.
 */
$lock = fopen($file . '.lock', 'c');
if ($lock === false) {
    http_response_code(500);
    exit('ERRORE: file non apribile');
}
flock($lock, LOCK_EX);

try {
    $punti = leggi_json($file) ?? [];
    $punti[] = $punto;

    // I punti arretrati arrivano tutti insieme quando torna la rete:
    // riordinare per ora del rilevamento tiene il tracciato coerente.
    usort($punti, static fn($a, $b) => ($a['t'] ?? 0) <=> ($b['t'] ?? 0));

    if (!scrivi_json($file, $punti)) {
        http_response_code(500);
        exit('ERRORE: scrittura fallita');
    }
    aggiorna_riassunto($id, $punto);
} finally {
    flock($lock, LOCK_UN);
    fclose($lock);
    @unlink($file . '.lock');
}

pulizia_automatica();

echo $id;

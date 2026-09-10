<?php
/* IAH-VERSION 1.0.6.2 */
/**
 * I am here, funzioni condivise fra dati.php, index.php e viaggi.php.
 *
 * Se devi mettere mano al progetto, questo è il file da leggere per
 * primo: qui stanno i percorsi dei file, la cifratura, il controllo
 * delle chiavi e il riassunto dei viaggi. Gli altri file usano solo
 * queste funzioni e non toccano mai il disco direttamente.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);

/** Versione dei file del server. Serve a verifica.php per accorgersi se
 *  un file e rimasto indietro durante un aggiornamento parziale. */
const IAH_VERSION = '1.0.6.2';

/**
 * Pagina ufficiale del progetto, dove si scarica l'app, e il repository
 * dove sta il codice.
 *
 * Non stanno in settings.php di proposito: cosi restano raggiungibili da
 * qualunque installazione. Chi vuole cambiarli modifica queste righe.
 */
const IAH_SITO = 'https://iamhere.it';
const IAH_CODICE = 'https://github.com/fribbynetwork/i-am-here';

if (PHP_VERSION_ID < 80000) {
    http_response_code(500);
    exit('I am here richiede PHP 8.0 o superiore. Versione attuale: ' . PHP_VERSION
       . '. I am here needs PHP 8.0 or later.');
}

/** Impostazioni, caricate una volta sola. */
function cfg(?string $chiave = null)
{
    static $c = null;
    if ($c === null) {
        $f = __DIR__ . '/settings.php';
        if (!is_file($f)) {
            // Il pacchetto contiene settings-default.php: si copia in
            // settings.php e si compila. In questo modo un aggiornamento
            // sovrascrive il codice ma non le impostazioni.
            http_response_code(500);
            exit(
                "I am here non e ancora configurato.\n\n" .
                "Rinomina settings-default.php in settings.php e compila le chiavi.\n\n" .
                "I am here is not configured yet.\n" .
                "Rename settings-default.php to settings.php and fill in the keys."
            );
        }
        $c = require $f;

        // Il fuso va impostato subito: da qui in poi ogni data, ogni
        // cartella e ogni confine di mese ne dipendono.
        $tz = (string) ($c['timezone'] ?? '');
        if ($tz !== '' && in_array($tz, timezone_identifiers_list(), true)) {
            date_default_timezone_set($tz);
        }
    }
    return $chiave === null ? $c : ($c[$chiave] ?? null);
}

// ---------------------------------------------------------------- percorsi

/** Cartella dei dati, assoluta. Il percorso relativo parte da qui. */
function cartella_dati(): string
{
    $d = (string) cfg('data_dir');
    $abs = (str_starts_with($d, '/') || preg_match('#^[A-Za-z]:[\\\\/]#', $d))
        ? $d
        : __DIR__ . '/' . $d;
    return rtrim($abs, '/\\');
}

/**
 * I file stanno in <dati>/<anno>/<mese>/, ricavati dall'id del viaggio.
 * L'id è l'orario di partenza, quindi tutti i punti di uno stesso
 * viaggio finiscono nella stessa cartella anche se si passa la mezzanotte.
 */
function cartella_viaggio(int $id): string
{
    return cartella_dati() . '/' . date('Y/m', $id);
}

function file_tracciato(int $id): string
{
    return cartella_viaggio($id) . "/$id.json";
}

function file_riassunto(int $id): string
{
    return cartella_viaggio($id) . "/$id.info.json";
}

/**
 * Cerca un viaggio anche nella vecchia cartella piatta, dove finivano i
 * file prima che esistessero le sottocartelle per anno e mese.
 */
function trova_tracciato(int $id): ?string
{
    foreach ([file_tracciato($id), cartella_dati() . "/$id.json"] as $f) {
        if (is_file($f)) {
            return $f;
        }
    }
    return null;
}

function id_valido($grezzo): ?int
{
    $s = preg_replace('/[^0-9]/', '', (string) $grezzo);
    return ($s === '' || strlen($s) > 12) ? null : (int) $s;
}

// -------------------------------------------------------------- cifratura

/**
 * I file cifrati cominciano con questo marcatore. Serve a distinguerli
 * da quelli in chiaro: così accendere la cifratura non rende illeggibili
 * i percorsi già salvati.
 */
const MARCATORE = 'IAH1:';

function chiave_cifratura(): string
{
    return hash('sha256', (string) cfg('encryption_key'), true);
}

/** AES-256-GCM. Il tag di autenticazione impedisce modifiche silenziose. */
function cifra(string $testo): string
{
    $iv = random_bytes(12);
    $tag = '';
    $c = openssl_encrypt($testo, 'aes-256-gcm', chiave_cifratura(), OPENSSL_RAW_DATA, $iv, $tag);
    if ($c === false) {
        throw new RuntimeException('cifratura fallita');
    }
    return MARCATORE . base64_encode($iv . $tag . $c);
}

/** Decifra se serve; un contenuto in chiaro torna indietro tale e quale. */
function decifra(string $contenuto): ?string
{
    if (!str_starts_with($contenuto, MARCATORE)) {
        return $contenuto;
    }
    $raw = base64_decode(substr($contenuto, strlen(MARCATORE)), true);
    if ($raw === false || strlen($raw) < 28) {
        return null;
    }
    $out = openssl_decrypt(
        substr($raw, 28), 'aes-256-gcm', chiave_cifratura(),
        OPENSSL_RAW_DATA, substr($raw, 0, 12), substr($raw, 12, 16)
    );
    return $out === false ? null : $out;
}

function leggi_json(string $file): ?array
{
    if (!is_file($file)) {
        return null;
    }
    $grezzo = file_get_contents($file);
    if ($grezzo === false || $grezzo === '') {
        return null;
    }
    $testo = decifra($grezzo);
    if ($testo === null) {
        return null;   // cifrato con un'altra chiave, o file corrotto
    }
    $d = json_decode($testo, true);
    return is_array($d) ? $d : null;
}

function scrivi_json(string $file, array $dati): bool
{
    $testo = json_encode($dati, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE);
    if ($testo === false) {
        return false;
    }
    if (cfg('encrypt')) {
        $testo = cifra($testo);
    }
    $dir = dirname($file);
    if (!is_dir($dir) && !mkdir($dir, 0755, true) && !is_dir($dir)) {
        return false;
    }
    // Scrittura su file temporaneo e poi rinomina: se il processo muore a
    // metà, il file originale resta intatto invece di troncarsi.
    $tmp = $file . '.tmp';
    if (file_put_contents($tmp, $testo, LOCK_EX) === false) {
        return false;
    }
    return rename($tmp, $file);
}

// ------------------------------------------------------------------ chiavi

/**
 * Le prime cifre dell'impronta di un valore. Serve a confrontare una
 * chiave fra server e app senza scriverla da nessuna parte: se le sei
 * cifre coincidono, il valore e lo stesso.
 */
function impronta(string $valore): string
{
    return $valore === '' ? '' : substr(hash('sha256', $valore), 0, 6);
}

/** Il codice di un singolo viaggio, ricavato dall'id e dal segreto. */
function token_viaggio(int $id): string
{
    return substr(hash_hmac('sha256', (string) $id, (string) cfg('token_secret')), 0, 16);
}

/**
 * Chi può vedere questo viaggio.
 * hash_equals confronta in tempo costante: il == di PHP si ferma al primo
 * carattere diverso, e quella differenza di tempo, ripetuta su migliaia di
 * richieste, rivela la chiave un carattere alla volta.
 */
function auth_ok(int $id, ?string $auth): bool
{
    if (!cfg('require_auth')) {
        return true;
    }
    $auth = (string) $auth;
    if ($auth === '') {
        return false;
    }
    if (hash_equals((string) cfg('master_key'), $auth)) {
        return true;
    }
    if (cfg('per_trip_tokens') && hash_equals(token_viaggio($id), $auth)) {
        return true;
    }
    return false;
}

/**
 * Il sito e servito in HTTPS? Controlla anche le intestazioni dei proxy:
 * dietro un bilanciatore o una CDN, PHP vede una connessione in chiaro
 * anche quando il visitatore e in HTTPS.
 */
function sito_https(): bool
{
    if (!empty($_SERVER['HTTPS']) && strtolower((string) $_SERVER['HTTPS']) !== 'off') {
        return true;
    }
    if (strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https') {
        return true;
    }
    return ((int) ($_SERVER['SERVER_PORT'] ?? 0)) === 443;
}

/** L'indirizzo da condividere per un viaggio. */
function link_viaggio(int $id, bool $assoluto = true): string
{
    $q = "?id=$id";
    if (cfg('require_auth')) {
        $q .= '&auth=' . (cfg('per_trip_tokens') ? token_viaggio($id) : cfg('master_key'));
    }
    if (!$assoluto) {
        return $q;
    }
    $schema = sito_https() ? 'https' : 'http';
    $host = $_SERVER['HTTP_HOST'] ?? 'localhost';
    $base = rtrim(dirname($_SERVER['SCRIPT_NAME'] ?? '/'), '/');
    return "$schema://$host$base/$q";
}

// --------------------------------------------------------------- riassunto

const R = 6371000;

function distanza(float $la1, float $lo1, float $la2, float $lo2): float
{
    $dLat = deg2rad($la2 - $la1);
    $dLon = deg2rad($lo2 - $lo1);
    $h = sin($dLat / 2) ** 2 + cos(deg2rad($la1)) * cos(deg2rad($la2)) * sin($dLon / 2) ** 2;
    return 2 * R * asin(min(1.0, sqrt($h)));
}

/**
 * Aggiorna il riassunto con un punto nuovo, senza rileggere il tracciato.
 * È il motivo per cui l'elenco dei viaggi resta veloce anche con migliaia
 * di percorsi: legge solo questi file, che pesano poche centinaia di byte.
 */
function aggiorna_riassunto(int $id, array $punto): void
{
    $f = file_riassunto($id);
    $r = leggi_json($f) ?? [
        'id' => $id, 'inizio' => $punto['t'] ?? $id, 'punti' => 0,
        'distanza' => 0.0, 'salita' => 0.0, 'discesa' => 0.0, 'chiuso' => false,
    ];

    if (isset($r['lat'], $r['lon'])) {
        $d = distanza((float) $r['lat'], (float) $r['lon'], (float) $punto['lat'], (float) $punto['lon']);
        // Un salto del GPS puo generare chilometri inesistenti: oltre i
        // 2 km fra due punti consecutivi non e uno spostamento credibile.
        if ($d < 2000) {
            $r['distanza'] += $d;
        }
        if (isset($r['alt'], $punto['alt'])) {
            $dq = (float) $punto['alt'] - (float) $r['alt'];
            if (abs($dq) < 200) {
                if ($dq > 0) { $r['salita'] += $dq; } else { $r['discesa'] -= $dq; }
            }
        }
    }

    $r['punti']  = ((int) $r['punti']) + 1;
    $r['ultimo'] = $punto['t'] ?? time();
    $r['lat']    = $punto['lat'];
    $r['lon']    = $punto['lon'];
    if (isset($punto['alt']))  { $r['alt'] = $punto['alt']; }
    if (isset($punto['bat']))  { $r['bat'] = $punto['bat']; }
    if (!empty($punto['dest'])) { $r['dest'] = $punto['dest']; }
    if (($punto['evento'] ?? '') === 'end') { $r['chiuso'] = true; }

    scrivi_json($f, $r);
}

/** Un viaggio senza nuove posizioni da troppo tempo è finito comunque. */
function viaggio_concluso(array $r): bool
{
    if (!empty($r['chiuso'])) {
        return true;
    }
    $ore = (int) cfg('stale_hours');
    return $ore > 0 && (time() - (int) ($r['ultimo'] ?? 0)) > $ore * 3600;
}

/**
 * Ricostruisce il riassunto leggendo il tracciato. Serve solo per i
 * viaggi salvati prima che i riassunti esistessero.
 */
function riassunto_da_tracciato(int $id): ?array
{
    $f = trova_tracciato($id);
    if ($f === null) {
        return null;
    }
    $punti = leggi_json($f);
    if (!is_array($punti) || $punti === []) {
        return null;
    }

    $r = ['id' => $id, 'inizio' => $id, 'punti' => 0, 'distanza' => 0.0,
          'salita' => 0.0, 'discesa' => 0.0, 'chiuso' => true, 'ricostruito' => true];
    $pl = $pn = null;
    foreach ($punti as $p) {
        if (is_string($p)) {                   // vecchio formato "lat,lon"
            $parti = explode(',', $p);
            if (count($parti) !== 2) { continue; }
            $la = (float) $parti[0]; $lo = (float) $parti[1];
        } elseif (is_array($p) && isset($p['lat'], $p['lon'])) {
            $la = (float) $p['lat']; $lo = (float) $p['lon'];
            if (isset($p['t'])) {
                $r['ultimo'] = (int) $p['t'];
                if ($r['punti'] === 0) { $r['inizio'] = (int) $p['t']; }
            }
            if (!empty($p['dest'])) { $r['dest'] = $p['dest']; }
            if (isset($p['bat']))   { $r['bat'] = $p['bat']; }
        } else {
            continue;
        }
        if ($pl !== null) {
            $d = distanza($pl, $pn, $la, $lo);
            if ($d < 2000) { $r['distanza'] += $d; }
        }
        $pl = $la; $pn = $lo;
        $r['punti']++;
        $r['lat'] = $la; $r['lon'] = $lo;
    }
    return $r['punti'] > 0 ? $r : null;
}

/**
 * Porta i punti alla forma che si aspetta la mappa. I file vecchi
 * contengono stringhe "lat,lon", quelli nuovi oggetti completi: qui
 * diventano la stessa cosa, cosi i tracciati storici restano leggibili.
 */
function normalizza_punti(?array $grezzi): array
{
    $out = [];
    foreach ($grezzi ?? [] as $p) {
        if (is_string($p)) {
            $parti = array_map('trim', explode(',', $p));
            if (count($parti) === 2 && is_numeric($parti[0]) && is_numeric($parti[1])) {
                $out[] = ['lat' => (float) $parti[0], 'lon' => (float) $parti[1]];
            }
            continue;
        }
        if (is_array($p) && isset($p['lat'], $p['lon'])) {
            $q = ['lat' => (float) $p['lat'], 'lon' => (float) $p['lon']];
            foreach (['t', 'sat', 'satuso', 'bat', 'carica', 'ritardo'] as $k) {
                if (isset($p[$k])) { $q[$k] = (int) $p[$k]; }
            }
            foreach (['acc', 'vacc', 'alt', 'vel', 'dir', 'press', 'dist'] as $k) {
                if (isset($p[$k])) { $q[$k] = (float) $p[$k]; }
            }
            foreach (['evento', 'rete', 'dest', 'prov'] as $k) {
                if (isset($p[$k]) && $p[$k] !== '') { $q[$k] = (string) $p[$k]; }
            }
            $out[] = $q;
        }
    }
    usort($out, static fn($a, $b) => ($a['t'] ?? 0) <=> ($b['t'] ?? 0));
    return $out;
}

/**
 * I riassunti dei viaggi iniziati fra due istanti. Legge solo le cartelle
 * dei mesi interessati, non tutto l'archivio: e per questo che l'elenco
 * resta veloce anche con migliaia di percorsi.
 */
function viaggi_fra(int $da, int $a): array
{
    $cartelle = [];
    for ($t = $da; $t <= $a + 86400; $t += 86400) {
        $cartelle[date('Y/m', $t)] = true;
    }
    $cartelle[date('Y/m', $a)] = true;
    // Anche la vecchia cartella piatta, per chi non ha ancora migrato.
    $cartelle[''] = true;

    $out = [];
    foreach (array_keys($cartelle) as $c) {
        $base = cartella_dati() . ($c === '' ? '' : "/$c");

        foreach (glob("$base/*.info.json") ?: [] as $f) {
            $r = leggi_json($f);
            if (is_array($r) && isset($r['id'])) {
                $id = (int) $r['id'];
                if ($id >= $da && $id <= $a) { $out[$id] = $r; }
            }
        }
        // Tracciati senza riassunto: ricostruito al volo e salvato, cosi
        // la volta dopo si legge come tutti gli altri.
        foreach (glob("$base/*.json") ?: [] as $f) {
            if (str_ends_with($f, '.info.json')) { continue; }
            $id = (int) basename($f, '.json');
            if ($id < $da || $id > $a || isset($out[$id])) { continue; }
            $r = riassunto_da_tracciato($id);
            if ($r !== null) { scrivi_json(file_riassunto($id), $r); $out[$id] = $r; }
        }
    }
    krsort($out);
    return $out;
}

/** I viaggi di un periodo: 24h, 7g, 30g oppure AAAA-MM. */
function viaggi_del_periodo(string $v): array
{
    if (preg_match('/^(\d{4})-(\d{2})$/', $v, $m)) {
        $da = (int) mktime(0, 0, 0, (int) $m[2], 1, (int) $m[1]);
        $a  = (int) mktime(23, 59, 59, (int) $m[2] + 1, 0, (int) $m[1]);
    } else {
        $giorni = ['24h' => 1, '7g' => 7, '30g' => 30][$v] ?? 7;
        $da = time() - $giorni * 86400;
        $a  = time();
    }
    return viaggi_fra($da, $a);
}

/**
 * Gli id di tutti i tracciati presenti, dal piu recente. Non legge i
 * file: bastano i nomi, e su un archivio grosso la differenza si sente.
 */
function tutti_gli_id(): array
{
    $ids = [];
    foreach (['/*.json', '/*/*/*.json'] as $schema) {
        foreach (glob(cartella_dati() . $schema) ?: [] as $f) {
            if (str_ends_with($f, '.info.json')) { continue; }
            $id = id_valido(basename($f, '.json'));
            if ($id !== null) { $ids[$id] = true; }
        }
    }
    $ids = array_keys($ids);
    rsort($ids);
    return $ids;
}

// ------------------------------------------------------------- manutenzione

/** Cancella un viaggio: tracciato e riassunto. */
function elimina_viaggio(int $id): bool
{
    $ok = false;
    foreach ([trova_tracciato($id), file_riassunto($id), cartella_dati() . "/$id.info.json"] as $f) {
        if ($f !== null && is_file($f)) {
            $ok = unlink($f) || $ok;
        }
    }
    return $ok;
}

/**
 * Cancella i viaggi più vecchi del limite impostato. Gira quando arriva
 * una posizione nuova, non più di una volta all'ora: cosi la pulizia non
 * pesa su ogni singola richiesta.
 */
function pulizia_automatica(): void
{
    if (!cfg('auto_delete')) {
        return;
    }
    $giorni = (int) cfg('keep_days');
    if ($giorni <= 0) {
        return;
    }

    $sentinella = cartella_dati() . '/.ultima-pulizia';
    if (is_file($sentinella) && (time() - (int) filemtime($sentinella)) < 3600) {
        return;
    }
    @touch($sentinella);

    $limite = time() - $giorni * 86400;
    foreach (glob(cartella_dati() . '/{*.json,*/*/*.json}', GLOB_BRACE) ?: [] as $f) {
        if (preg_match('/(\d{9,12})(\.info)?\.json$/', basename($f), $m) && (int) $m[1] < $limite) {
            @unlink($f);
        }
    }
}

/*
 * Le impostazioni si leggono subito, non alla prima occorrenza.
 *
 * Serve al fuso orario: viene applicato dentro cfg(), e se quella lettura
 * avvenisse tardi, un mktime eseguito prima calcolerebbe i confini del
 * mese con il fuso sbagliato. E' successo davvero, con la sessione gia
 * aperta: il controllo sulla chiave veniva saltato, cfg() non partiva, e
 * la vista di un mese non trovava i viaggi delle sue prime ore.
 */
cfg();

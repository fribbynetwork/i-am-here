<?php
/* IAH-VERSION 1.0.6.2 */
/**
 * I am here, verifica dell'installazione / installation check.
 *
 * Apri questa pagina nel browser quando qualcosa non funziona: controlla
 * versione di PHP, permessi, sessioni, chiavi e cartelle, e dice cosa
 * sistemare. Non mostra mai le chiavi, solo se sono state cambiate.
 *
 * Quando tutto è a posto, cancella pure questo file.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);

// Prima di tutto: senza settings.php non si va da nessuna parte, e questa
// e proprio la pagina che deve spiegarlo invece di limitarsi a fallire.
if (!is_file(__DIR__ . '/settings.php')) {
    header('Content-Type: text/plain; charset=utf-8');
    exit(
        "Manca settings.php.\n\n" .
        "Nel pacchetto trovi settings-default.php: rinominalo in settings.php\n" .
        "e compila le voci contrassegnate CAMBIAMI. Il file con le tue chiavi\n" .
        "resta cosi fuori dal pacchetto, e un aggiornamento non lo tocca.\n\n" .
        "settings.php is missing.\n\n" .
        "The package contains settings-default.php: rename it to settings.php\n" .
        "and fill in the entries marked CAMBIAMI. Your keys then stay out of\n" .
        "the package, and an update cannot overwrite them."
    );
}

require __DIR__ . '/lib.php';

header('X-Robots-Tag: noindex, nofollow');

/*
 * Questa pagina deve funzionare anche quando l'installazione e rotta:
 * e il suo mestiere. Se lib.php e rimasto a una versione precedente,
 * qui manca qualche funzione, quindi la definiamo al volo invece di
 * fermarci con un errore fatale.
 */
if (!function_exists('sito_https')) {
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
}

// Le sessioni si provano davvero, non si suppongono: si scrive un valore,
// si ricarica la pagina e si guarda se è ancora lì.
session_set_cookie_params(['path' => '/', 'httponly' => true, 'samesite' => 'Lax', 'secure' => sito_https()]);
@session_start();

$giro = isset($_GET['sessione']);
$sessione_ok = $giro && (($_SESSION['prova'] ?? '') === 'ok');
if (!$giro) {
    $_SESSION['prova'] = 'ok';
}

$esiti = [];
function esito(string $nome, bool $ok, string $dettaglio, bool $grave = true): void
{
    global $esiti;
    $esiti[] = compact('nome', 'ok', 'dettaglio', 'grave');
}

// --- file aggiornati -------------------------------------------------------
// Un aggiornamento parziale e la causa piu frequente di errori strani:
// due file che si aspettano cose diverse l'uno dall'altro.
$attesa = defined('IAH_VERSION') ? IAH_VERSION : '?';
$indietro = [];
foreach (['lib.php', 'dati.php', 'index.php', 'viaggi.php', 'formati.php', 'app.js', 'viaggi.js', 'style.css'] as $f) {
    $p = __DIR__ . '/' . $f;
    if (!is_file($p)) { $indietro[] = "$f (manca)"; continue; }
    // Il marcatore sta nelle prime righe: non serve leggere tutto il file.
    $testa = (string) file_get_contents($p, false, null, 0, 400);
    if (!preg_match('/IAH-VERSION\s+([0-9.]+)/', $testa, $m)) {
        $indietro[] = "$f (senza versione)";
    } elseif ($m[1] !== $attesa) {
        $indietro[] = "$f ({$m[1]})";
    }
}
esito('File aggiornati', $indietro === [],
    $indietro === []
        ? "Tutti i file sono alla versione $attesa."
        : 'Questi file non sono alla versione ' . $attesa . ': ' . implode(', ', $indietro)
          . '. Ricaricali sul server dal pacchetto, poi svuota la cache del browser.');

// --- PHP -------------------------------------------------------------------
esito(
    'Versione di PHP',
    PHP_VERSION_ID >= 80000,
    PHP_VERSION_ID >= 80000
        ? 'PHP ' . PHP_VERSION . ', va bene.'
        : 'PHP ' . PHP_VERSION . '. Serve almeno PHP 8.0: chiedi al tuo hosting di cambiare versione.'
);

esito(
    'Estensione OpenSSL',
    extension_loaded('openssl'),
    extension_loaded('openssl')
        ? 'Presente. La cifratura dei percorsi è utilizzabile.'
        : 'Assente. Tutto funziona, ma non puoi attivare "encrypt" in settings.php.',
    (bool) cfg('encrypt')
);

// --- cartella dei dati -----------------------------------------------------
$dir = cartella_dati();
$esiste = is_dir($dir) || @mkdir($dir, 0755, true);
esito('Cartella dei dati', $esiste && is_writable($dir),
    $esiste
        ? (is_writable($dir) ? "Scrivibile: $dir" : "Esiste ma non è scrivibile: $dir. Cambia i permessi a 755 o 775.")
        : "Non esiste e non si riesce a crearla: $dir");

// Sta dentro la radice del sito? In quel caso i file sono raggiungibili
// dal browser, a meno che .htaccess non li blocchi.
$radice = realpath($_SERVER['DOCUMENT_ROOT'] ?? '') ?: '';
$dentro_radice = $radice !== '' && str_starts_with((string) realpath($dir), $radice);
$ht = is_file($dir . '/.htaccess');
esito('Percorsi non scaricabili',
    !$dentro_radice || $ht || (bool) cfg('encrypt'),
    !$dentro_radice
        ? 'La cartella è fuori dalla radice del sito: nessuno può scaricare i file. È la sistemazione migliore.'
        : ($ht
            ? 'La cartella è dentro il sito, ma c\'è il file .htaccess che blocca il download. Funziona solo su Apache: se usi nginx o altro, sposta la cartella fuori dalla radice oppure attiva "encrypt".'
            : 'La cartella è dentro il sito e non c\'è nessun .htaccess: chiunque indovini un indirizzo può scaricare i tuoi percorsi.'),
    true);

// --- sessioni --------------------------------------------------------------
$salva = session_save_path();
$salva_ok = $salva === '' || is_writable($salva);
esito('Sessioni',
    $giro ? $sessione_ok : true,
    !$giro
        ? 'Non ancora provate. Ricarica con il collegamento in fondo alla pagina.'
        : ($sessione_ok
            ? 'Funzionano: l\'accesso all\'elenco dei viaggi si mantiene.'
            : 'NON funzionano: l\'accesso non si mantiene e il modulo si ripresenta senza errori. '
              . 'Cartella delle sessioni: "' . ($salva ?: 'predefinita') . '"'
              . ($salva_ok ? '. È scrivibile, quindi il problema è il cookie: se il sito è raggiungibile sia in http sia in https, usa sempre lo stesso indirizzo.'
                           : '. NON è scrivibile: è questa la causa. Chiedi al tuo hosting di sistemare i permessi, oppure imposta session.save_path in un php.ini tuo.')));

// --- chiavi ----------------------------------------------------------------
$difetto = '!!!!CHANGE ME!!!!';
$chiavi = [
    'write_key'     => 'Chiave di scrittura',
    'master_key'    => 'Chiave principale',
    'token_secret'  => 'Segreto dei codici',
];
// A quale campo dell'app corrisponde ciascuna impronta.
$confronto = [
    'write_key'    => ' Confrontala con la chiave che hai messo nell\'URL dell\'endpoint.',
    'token_secret' => ' Deve coincidere con quella mostrata nell\'app, in Impostazioni → Avanzate.',
];

foreach ($chiavi as $k => $et) {
    $v = (string) cfg($k);
    $serve = $k !== 'token_secret' || cfg('per_trip_tokens');
    if (!$serve) { continue; }

    $ok = $v !== $difetto && $v !== '';
    $testo = $v === $difetto ? 'È ancora quella di esempio: cambiala in settings.php.'
        : ($v === '' ? 'È vuota: compilala in settings.php.'
        : (strlen($v) < 16 ? 'Impostata, ma corta: ' . strlen($v) . ' caratteri. Meglio almeno 24.'
        : 'Impostata, ' . strlen($v) . ' caratteri.'));

    // L'impronta si mostra solo per le chiavi che hanno un corrispettivo
    // nell'app, e solo se la chiave e stata davvero impostata.
    if ($ok && isset($confronto[$k])) {
        $testo .= '  Impronta: ' . impronta($v) . '.' . $confronto[$k];
    }
    esito($et, $ok, $testo);
}
$tutte = [(string) cfg('write_key'), (string) cfg('master_key'), (string) cfg('token_secret')];
esito('Chiavi diverse fra loro', count(array_unique($tutte)) === 3,
    count(array_unique($tutte)) === 3
        ? 'Le tre chiavi sono diverse, come devono essere.'
        : 'Due chiavi coincidono. Chi riceve un link da guardare potrebbe scrivere nei tuoi percorsi.');

if (cfg('encrypt')) {
    $ek = (string) cfg('encryption_key');
    esito('Chiave di cifratura', $ek !== $difetto && $ek !== '',
        $ek === $difetto || $ek === ''
            ? 'La cifratura è attiva ma la chiave non è impostata.'
            : 'Impostata. Conservane una copia altrove: se la perdi, i percorsi cifrati non si recuperano.');
}

esito('Chiave richiesta per guardare', (bool) cfg('require_auth'),
    cfg('require_auth')
        ? 'Attiva: serve un codice per aprire un percorso.'
        : 'SPENTA: chiunque indovini un orario vede i tuoi percorsi. Metti require_auth a true.');

// --- fuso orario -----------------------------------------------------------
$tz = (string) cfg('timezone');
$attivo = date_default_timezone_get();
esito('Fuso orario',
    $tz !== '' || $attivo !== 'UTC',
    $tz !== ''
        ? "Impostato su $attivo. Viaggi, cartelle e orari seguono questo fuso."
        : ($attivo === 'UTC'
            ? "Non impostato, e il server usa UTC. Se non e il tuo fuso, i viaggi iniziati a "
              . "notte fonda possono finire datati al giorno prima: compila timezone in settings.php."
            : "Non impostato: si usa quello del server, $attivo."),
    false);

// --- collegamento ----------------------------------------------------------
esito('Connessione cifrata', sito_https(),
    sito_https()
        ? 'Il sito è servito in HTTPS.'
        : 'Il sito è in HTTP: chiavi e percorsi viaggiano leggibili sulla rete.', false);

// --- percorsi presenti -----------------------------------------------------
$n = count(glob("$dir/*.json") ?: []) + count(glob("$dir/*/*/*.json") ?: []);
esito('Percorsi salvati', true, $n === 0 ? 'Nessuno ancora.' : "$n file nella cartella dei dati.", false);

$gravi = array_filter($esiti, static fn($e) => !$e['ok'] && $e['grave']);
$h = static fn($s) => htmlspecialchars((string) $s, ENT_QUOTES, 'UTF-8');
?>
<!DOCTYPE html>
<html lang="it" class="pagina">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex, nofollow">
<title>Verifica · I am here</title>
<link rel="icon" href="favicon.ico" sizes="32x32">
<link rel="icon" href="favicon.svg" type="image/svg+xml">
<link rel="apple-touch-icon" href="apple-touch-icon.png">
<link rel="manifest" href="site.webmanifest">
<!-- La barra del browser si intona al tema in uso. -->
<meta name="theme-color" media="(prefers-color-scheme: light)" content="#F2F5F4">
<meta name="theme-color" media="(prefers-color-scheme: dark)" content="#0B1416">
<link rel="stylesheet" href="style.css?v=<?= IAH_VERSION ?>">
</head>
<body class="pagina">
<main class="contenuto">
  <h1>Verifica dell'installazione</h1>
  <p class="note intro">
    <?= $gravi === [] ? 'Nessun problema bloccante.' : count($gravi) . ' cosa da sistemare.' ?>
    Quando tutto è a posto, cancella questo file dal server.
  </p>

  <?php foreach ($esiti as $e): ?>
    <article class="riga surface <?= $e['ok'] ? 'si' : ($e['grave'] ? 'no' : 'forse') ?>">
      <span class="segno"><?= $e['ok'] ? '✓' : ($e['grave'] ? '✕' : '!') ?></span>
      <div>
        <strong><?= $h($e['nome']) ?></strong>
        <div class="note"><?= $h($e['dettaglio']) ?></div>
      </div>
    </article>
  <?php endforeach; ?>

  <p class="azioni-testo">
    <?php if (!$giro): ?>
      <a href="?sessione=1">Prova le sessioni →</a>
    <?php else: ?>
      <a href="verifica.php">Ricomincia</a> ·
    <?php endif; ?>
    <a href="viaggi.php">Elenco dei viaggi</a> ·
    <a href="index.php">Pagina iniziale</a>
  </p>
</main>
</body>
</html>

<?php
/* IAH-VERSION 1.0.6.2 */
/**
 * I am here, conversione dei tracciati.
 *
 * Da qui passano tutte le esportazioni e le importazioni. Il formato
 * nativo conserva ogni campo; GPX, GeoJSON e CSV sono per parlare con il
 * resto del mondo.
 *
 * Un viaggio, qui dentro, e sempre un array ['id' => int, 'punti' => []],
 * dove i punti hanno la forma che usa il server.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

declare(strict_types=1);

/** I formati che si possono produrre. */
function formati_uscita(): array
{
    return [
        'gpx'     => ['ext' => 'gpx',     'mime' => 'application/gpx+xml'],
        'json'    => ['ext' => 'json',    'mime' => 'application/json'],
        'geojson' => ['ext' => 'geojson', 'mime' => 'application/geo+json'],
        'csv'     => ['ext' => 'csv',     'mime' => 'text/csv'],
    ];
}

// =====================================================================
//  USCITA
// =====================================================================

function esporta(array $viaggi, string $formato): string
{
    return match ($formato) {
        'gpx'     => esporta_gpx($viaggi),
        'geojson' => esporta_geojson($viaggi),
        'csv'     => esporta_csv($viaggi),
        default   => esporta_json($viaggi),
    };
}

/** Il formato nativo: nessuna perdita, per spostare fra installazioni. */
function esporta_json(array $viaggi): string
{
    return json_encode([
        'app' => 'I am here',
        'formato' => 1,
        'creato' => time(),
        'viaggi' => array_map(
            static fn($v) => ['id' => (int) $v['id'], 'punti' => $v['punti']],
            array_values($viaggi)
        ),
    ], JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT);
}

/** I campi nostri che non hanno un posto nel GPX standard. */
const EXTRA_GPX = ['acc', 'vacc', 'vel', 'dir', 'sat', 'satuso', 'bat', 'carica',
                   'rete', 'press', 'dist', 'evento', 'dest', 'prov', 'ritardo'];

/**
 * GPX 1.1, il formato che importano tutti.
 *
 * I dati in piu stanno in <extensions>, che lo standard prevede: i
 * programmi che non li capiscono li ignorano, e un viaggio esportato e
 * poi reimportato in I am here non perde niente.
 */
function esporta_gpx(array $viaggi): string
{
    $x = static fn($s) => htmlspecialchars((string) $s, ENT_XML1 | ENT_QUOTES, 'UTF-8');
    $iso = static fn($t) => gmdate('Y-m-d\TH:i:s\Z', (int) $t);

    $out = '<?xml version="1.0" encoding="UTF-8"?>' . "\n"
         . '<gpx version="1.1" creator="I am here" '
         . 'xmlns="http://www.topografix.com/GPX/1/1" '
         . 'xmlns:iah="https://iamhere.it/gpx/1">' . "\n"
         . "  <metadata><time>" . $iso(time()) . "</time></metadata>\n";

    foreach ($viaggi as $v) {
        $punti = $v['punti'];
        if ($punti === []) { continue; }
        $inizio = (int) ($punti[0]['t'] ?? $v['id']);
        $out .= "  <trk>\n    <name>" . $x(date('Y-m-d H:i', $inizio)) . "</name>\n"
              . "    <type>" . $x($punti[0]['dest'] ?? 'I am here') . "</type>\n"
              . "    <trkseg>\n";

        foreach ($punti as $p) {
            $out .= '      <trkpt lat="' . $x($p['lat']) . '" lon="' . $x($p['lon']) . "\">\n";
            if (isset($p['alt'])) { $out .= "        <ele>" . $x($p['alt']) . "</ele>\n"; }
            if (isset($p['t']))   { $out .= "        <time>" . $iso($p['t']) . "</time>\n"; }
            if (isset($p['sat'])) { $out .= "        <sat>" . (int) $p['sat'] . "</sat>\n"; }

            $extra = '';
            foreach (EXTRA_GPX as $k) {
                if (isset($p[$k]) && $p[$k] !== '') {
                    $extra .= "          <iah:$k>" . $x($p[$k]) . "</iah:$k>\n";
                }
            }
            if ($extra !== '') { $out .= "        <extensions>\n$extra        </extensions>\n"; }
            $out .= "      </trkpt>\n";
        }
        $out .= "    </trkseg>\n  </trk>\n";
    }
    return $out . "</gpx>\n";
}

/**
 * GeoJSON: una linea per viaggio, cosi si vede subito negli strumenti
 * cartografici, con l'elenco completo dei punti nelle proprieta perche
 * una LineString non puo portare dati punto per punto.
 */
function esporta_geojson(array $viaggi): string
{
    $features = [];
    foreach ($viaggi as $v) {
        $punti = $v['punti'];
        if ($punti === []) { continue; }
        $coord = [];
        foreach ($punti as $p) {
            $c = [(float) $p['lon'], (float) $p['lat']];
            if (isset($p['alt'])) { $c[] = (float) $p['alt']; }
            $coord[] = $c;
        }
        $inizio = (int) ($punti[0]['t'] ?? $v['id']);
        $fine = (int) ($punti[count($punti) - 1]['t'] ?? $inizio);
        $features[] = [
            'type' => 'Feature',
            'geometry' => ['type' => 'LineString', 'coordinates' => $coord],
            'properties' => [
                'id' => (int) $v['id'],
                'name' => date('Y-m-d H:i', $inizio),
                'inizio' => $inizio,
                'fine' => $fine,
                'punti' => $punti,   // per non perdere niente al ritorno
            ],
        ];
    }
    return json_encode(
        ['type' => 'FeatureCollection', 'features' => $features],
        JSON_UNESCAPED_SLASHES | JSON_PRETTY_PRINT
    );
}

/** CSV: una riga per punto, per chi vuole guardarli in un foglio. */
function esporta_csv(array $viaggi): string
{
    $colonne = ['viaggio', 't', 'ora', 'lat', 'lon', 'alt', 'acc', 'vacc', 'vel',
                'dir', 'sat', 'satuso', 'bat', 'carica', 'rete', 'press', 'dist',
                'evento', 'dest', 'prov', 'ritardo'];

    $f = fopen('php://temp', 'r+');
    fputcsv($f, $colonne);
    foreach ($viaggi as $v) {
        foreach ($v['punti'] as $p) {
            $riga = [(int) $v['id']];
            $riga[] = $p['t'] ?? '';
            $riga[] = isset($p['t']) ? gmdate('Y-m-d\TH:i:s\Z', (int) $p['t']) : '';
            foreach (array_slice($colonne, 3) as $k) { $riga[] = $p[$k] ?? ''; }
            fputcsv($f, $riga);
        }
    }
    rewind($f);
    $csv = (string) stream_get_contents($f);
    fclose($f);
    // Il segnabyte iniziale serve a Excel per capire che e UTF-8.
    return "\xEF\xBB\xBF" . $csv;
}

// =====================================================================
//  INGRESSO
// =====================================================================

/**
 * Riconosce il formato e restituisce i viaggi trovati.
 *
 * @return array{viaggi: array, errore: ?string}
 */
function importa(string $contenuto): array
{
    $t = ltrim($contenuto, "\xEF\xBB\xBF \t\r\n");
    if ($t === '') { return ['viaggi' => [], 'errore' => 'vuoto']; }

    if (str_starts_with($t, '<')) { return importa_gpx($t); }
    if (str_starts_with($t, '{')) {
        $j = json_decode($t, true);
        if (!is_array($j)) { return ['viaggi' => [], 'errore' => 'json']; }
        if (($j['type'] ?? '') === 'FeatureCollection') { return importa_geojson($j); }
        return importa_json($j);
    }
    return importa_csv($t);
}

/** Il formato nativo, o comunque un JSON con una lista di viaggi. */
function importa_json(array $j): array
{
    $grezzi = $j['viaggi'] ?? null;
    if (!is_array($grezzi)) { return ['viaggi' => [], 'errore' => 'sconosciuto']; }

    $out = [];
    foreach ($grezzi as $v) {
        $punti = normalizza_punti($v['punti'] ?? null);
        if ($punti === []) { continue; }
        $out[] = ['id' => id_da_punti($v['id'] ?? null, $punti), 'punti' => $punti];
    }
    return ['viaggi' => $out, 'errore' => null];
}

function importa_geojson(array $j): array
{
    $out = [];
    foreach ($j['features'] ?? [] as $f) {
        $prop = $f['properties'] ?? [];

        // Se il file viene da noi, i punti completi sono nelle proprieta.
        $punti = normalizza_punti($prop['punti'] ?? null);

        // Altrimenti si ricostruiscono dalle coordinate della linea.
        if ($punti === [] && ($f['geometry']['type'] ?? '') === 'LineString') {
            foreach ($f['geometry']['coordinates'] ?? [] as $c) {
                if (!is_array($c) || count($c) < 2) { continue; }
                $p = ['lat' => (float) $c[1], 'lon' => (float) $c[0]];
                if (isset($c[2])) { $p['alt'] = (float) $c[2]; }
                $punti[] = $p;
            }
        }
        if ($punti === []) { continue; }
        $out[] = ['id' => id_da_punti($prop['id'] ?? null, $punti), 'punti' => $punti];
    }
    return ['viaggi' => $out, 'errore' => null];
}

/**
 * GPX. Ogni <trk> diventa un viaggio, ogni <trkpt> un punto.
 *
 * Il lettore e scritto a mano invece di usare SimpleXML: non dipende da
 * nessuna estensione, quindi funziona anche sugli hosting essenziali, e
 * soprattutto non interpreta entita esterne. Un file caricato da un
 * utente non puo quindi farsi restituire settings.php, che e il modo
 * classico in cui un lettore XML ingenuo viene sfruttato.
 *
 * Il GPX e un formato regolare prodotto da programmi, non scritto a
 * mano: le forme che si incontrano davvero sono poche.
 */
function importa_gpx(string $xml): array
{
    // Via i commenti, che potrebbero contenere tracciati di esempio.
    $xml = (string) preg_replace('/<!--.*?-->/s', '', $xml);

    $blocchi = [];
    if (preg_match_all('#<trk\b[^>]*>(.*?)</trk\s*>#is', $xml, $m)) {
        $blocchi = $m[1];
    } elseif (preg_match_all('#<rte\b[^>]*>(.*?)</rte\s*>#is', $xml, $m)) {
        $blocchi = $m[1];
    }
    if ($blocchi === []) { return ['viaggi' => [], 'errore' => 'nessun tracciato']; }

    $out = [];
    foreach ($blocchi as $blocco) {
        $punti = [];
        // Un punto puo essere <trkpt .../> oppure <trkpt ...>...</trkpt>.
        $trovati = preg_match_all(
            '#<(?:trkpt|rtept|wpt)\b([^>]*?)(?:/>|>(.*?)</(?:trkpt|rtept|wpt)\s*>)#is',
            $blocco, $m, PREG_SET_ORDER
        );
        if (!$trovati) { continue; }

        foreach ($m as $g) {
            $attr = $g[1];
            $dentro = $g[2] ?? '';

            $lat = gpx_attributo($attr, 'lat');
            $lon = gpx_attributo($attr, 'lon');
            if ($lat === null || $lon === null) { continue; }

            $p = ['lat' => (float) $lat, 'lon' => (float) $lon];

            $ele = gpx_figlio($dentro, 'ele');
            if ($ele !== null && is_numeric($ele)) { $p['alt'] = (float) $ele; }

            $ora = gpx_figlio($dentro, 'time');
            if ($ora !== null) {
                $t = strtotime($ora);
                if ($t !== false) { $p['t'] = $t; }
            }

            $sat = gpx_figlio($dentro, 'sat');
            if ($sat !== null && is_numeric($sat)) { $p['sat'] = (int) $sat; }

            // I nostri campi, quando il file era stato esportato da qui.
            foreach (EXTRA_GPX as $k) {
                $v = gpx_figlio($dentro, "iah:$k");
                if ($v === null) { $v = gpx_figlio($dentro, $k); }
                if ($v !== null && $v !== '') {
                    $p[$k] = is_numeric($v) ? (float) $v + 0 : $v;
                }
            }
            $punti[] = $p;
        }

        $punti = normalizza_punti($punti);
        if ($punti !== []) {
            $out[] = ['id' => id_da_punti(null, $punti), 'punti' => $punti];
        }
    }
    return ['viaggi' => $out, 'errore' => $out === [] ? 'nessun tracciato' : null];
}

/** Il valore di un attributo, con apici singoli o doppi. */
function gpx_attributo(string $attr, string $nome): ?string
{
    if (preg_match('#\b' . preg_quote($nome, '#') . '\s*=\s*("([^"]*)"|\'([^\']*)\')#i', $attr, $m)) {
        return gpx_testo($m[2] !== '' ? $m[2] : ($m[3] ?? ''));
    }
    return null;
}

/** Il contenuto di un elemento figlio, ignorando il prefisso di spazio. */
function gpx_figlio(string $dentro, string $nome): ?string
{
    $n = preg_quote($nome, '#');
    // Con i due punti nel nome si cerca esatto, altrimenti anche con un
    // prefisso qualsiasi: <ele> e <gpx:ele> sono la stessa cosa.
    $schema = str_contains($nome, ':')
        ? '#<' . $n . '\b[^>]*>(.*?)</' . $n . '\s*>#is'
        : '#<(?:[\w.-]+:)?' . $n . '\b[^>]*>(.*?)</(?:[\w.-]+:)?' . $n . '\s*>#is';
    return preg_match($schema, $dentro, $m) ? gpx_testo($m[1]) : null;
}

/** Scioglie le entita di base. Le esterne non vengono nemmeno guardate. */
function gpx_testo(string $s): string
{
    $s = trim($s);
    if (str_contains($s, '<![CDATA[')) {
        $s = (string) preg_replace('/<!\[CDATA\[(.*?)\]\]>/s', '$1', $s);
    }
    return html_entity_decode($s, ENT_QUOTES | ENT_XML1, 'UTF-8');
}

/** CSV con l'intestazione, quello che produciamo noi o uno simile. */
function importa_csv(string $testo): array
{
    $righe = preg_split('/\r\n|\n|\r/', trim($testo)) ?: [];
    if (count($righe) < 2) { return ['viaggi' => [], 'errore' => 'csv'] ; }

    $intest = str_getcsv(array_shift($righe));
    $col = array_flip(array_map(static fn($c) => strtolower(trim($c)), $intest));
    if (!isset($col['lat'], $col['lon'])) { return ['viaggi' => [], 'errore' => 'csv']; }

    $gruppi = [];
    foreach ($righe as $r) {
        if (trim($r) === '') { continue; }
        $c = str_getcsv($r);
        $val = static fn($k) => isset($col[$k], $c[$col[$k]]) && $c[$col[$k]] !== '' ? $c[$col[$k]] : null;
        if ($val('lat') === null || $val('lon') === null) { continue; }

        $p = ['lat' => (float) $val('lat'), 'lon' => (float) $val('lon')];
        foreach (['t', 'sat', 'satuso', 'bat', 'carica', 'ritardo'] as $k) {
            if ($val($k) !== null) { $p[$k] = (int) $val($k); }
        }
        foreach (['alt', 'acc', 'vacc', 'vel', 'dir', 'press', 'dist'] as $k) {
            if ($val($k) !== null) { $p[$k] = (float) $val($k); }
        }
        foreach (['evento', 'rete', 'dest', 'prov'] as $k) {
            if ($val($k) !== null) { $p[$k] = (string) $val($k); }
        }
        if (!isset($p['t']) && $val('ora') !== null) {
            $t = strtotime((string) $val('ora'));
            if ($t !== false) { $p['t'] = $t; }
        }
        $gruppi[(string) ($val('viaggio') ?? '0')][] = $p;
    }

    $out = [];
    foreach ($gruppi as $chiave => $punti) {
        $punti = normalizza_punti($punti);
        if ($punti === []) { continue; }
        $out[] = ['id' => id_da_punti(ctype_digit((string) $chiave) ? (int) $chiave : null, $punti),
                  'punti' => $punti];
    }
    return ['viaggi' => $out, 'errore' => null];
}

/**
 * L'id di un viaggio e l'orario di partenza. Se il file non porta orari
 * non c'e modo di ricavarlo: si usa l'ora di adesso, e chi importa vedra
 * il viaggio datato oggi. Meglio questo che rifiutare il file.
 */
function id_da_punti($idProposto, array $punti): int
{
    $id = id_valido($idProposto);
    if ($id !== null && $id > 0) { return $id; }
    $t = (int) ($punti[0]['t'] ?? 0);
    return $t > 0 ? $t : time();
}

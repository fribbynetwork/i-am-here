/* IAH-VERSION 1.1 */
/*
 * I am here, map viewer / visualizzatore del percorso.
 *
 * Copyright (C) 2026  I am here contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version. See the LICENSE file at the root of this repository.
 */

(() => {
  'use strict';

  // ------------------------------------------------------------- lingue
  const TESTI = {
    it: {
      locale: 'it-IT',
      altraLingua: 'Switch to English',
      inViaggio: 'In viaggio', arrivato: 'Arrivato',
      sottotitolo: 'Percorsi in tempo reale',

      legTitolo: 'Velocità del tratto',
      legSotto: 'scala adattata a questo percorso',

      inquadra: 'Inquadra tutto il percorso',
      segui: 'Segui l\u2019ultimo punto',
      riepilogoTit: 'Riepilogo del percorso',
      quotaTit: 'Profilo altimetrico',
      puntiTit: 'Mostra o nascondi i punti di transito',
      temaAuto: 'Tema di sistema', temaChiaro: 'Tema chiaro', temaScuro: 'Tema scuro',

      distanza: 'Distanza', durata: 'Durata', media: 'Media', massima: 'Massima',
      dislivelloSu: 'Dislivello +', dislivelloGiu: 'Dislivello −',
      scartati: 'Tratti scartati', dubbio: 'dato dubbio',
      scartatiNota: 'Alcuni tratti hanno una velocità impossibile per questo viaggio: quasi certamente un salto del GPS. Sono in grigio sulla mappa ed esclusi da media, massima e scala dei colori. La distanza li comprende ancora, perché il percorso reale fra quei due punti resta sconosciuto.',
      punti: 'Punti', batteria: 'Batteria',
      partenza: 'Partenza', ultimoDato: 'Ultimo dato',

      ora: 'Ora', coordinate: 'Coordinate', velocita: 'Velocità', quota: 'Quota',
      precisione: 'Precisione', direzione: 'Direzione', satelliti: 'Satelliti',
      rete: 'Rete', pressione: 'Pressione', allaMeta: 'Alla meta',
      arrivo: 'Arrivo', puntoDi: 'Punto %1 di %2',
      rosa: ['N', 'NE', 'E', 'SE', 'S', 'SO', 'O', 'NO'],

      quotaVuota: 'Servono almeno due punti con la quota. Aggiungi {alt} all\u2019URL nelle impostazioni dell\u2019app.',
      quotaNota: 'Quota in metri sull\u2019asse verticale, distanza percorsa su quello orizzontale. Da %1 a %2 m.',

      invitoTit: 'Nessun percorso da mostrare',
      invitoTesto: 'Questa pagina mostra un viaggio condiviso in tempo reale. Per crearne uno serve l\u2019app.',
      invitoAzione: 'Scarica I am here',
      invitoAccedi: 'Accedi',
      negatoTit: 'Percorso non accessibile',
      negatoTesto: 'Il link non è valido o non è più attivo. Chiedi a chi te lo ha inviato di ripeterlo.'
    },
    en: {
      locale: 'en-GB',
      altraLingua: 'Passa all\u2019italiano',
      inViaggio: 'On the move', arrivato: 'Arrived',
      sottotitolo: 'Live journeys',

      legTitolo: 'Segment speed',
      legSotto: 'scale fitted to this journey',

      inquadra: 'Fit the whole route',
      segui: 'Follow the last point',
      riepilogoTit: 'Journey summary',
      quotaTit: 'Elevation profile',
      puntiTit: 'Show or hide the waypoints',
      temaAuto: 'System theme', temaChiaro: 'Light theme', temaScuro: 'Dark theme',

      distanza: 'Distance', durata: 'Duration', media: 'Average', massima: 'Top speed',
      dislivelloSu: 'Ascent +', dislivelloGiu: 'Descent −',
      scartati: 'Discarded legs', dubbio: 'unreliable',
      scartatiNota: 'Some legs show a speed impossible for this journey, almost certainly a GPS jump. They are grey on the map and left out of the average, the top speed and the colour scale. The distance still includes them, because the real path between those two points remains unknown.',
      punti: 'Points', batteria: 'Battery',
      partenza: 'Start', ultimoDato: 'Last fix',

      ora: 'Time', coordinate: 'Coordinates', velocita: 'Speed', quota: 'Altitude',
      precisione: 'Accuracy', direzione: 'Heading', satelliti: 'Satellites',
      rete: 'Network', pressione: 'Pressure', allaMeta: 'To destination',
      arrivo: 'Arrival', puntoDi: 'Point %1 of %2',
      rosa: ['N', 'NE', 'E', 'SE', 'S', 'SW', 'W', 'NW'],

      quotaVuota: 'At least two points need an altitude. Add {alt} to the URL in the app settings.',
      quotaNota: 'Altitude in metres on the vertical axis, distance covered on the horizontal one. From %1 to %2 m.',

      invitoTit: 'No journey to show',
      invitoTesto: 'This page shows a journey shared live. Creating one takes the app.',
      invitoAzione: 'Get I am here',
      invitoAccedi: 'Sign in',
      negatoTit: 'Journey not available',
      negatoTesto: 'The link is not valid or no longer active. Ask whoever sent it to you for a new one.'
    }
  };

  const linguaSalvata = () => {
    let v = null;
    try { v = localStorage.getItem('iamhere-lingua'); } catch (e) {}
    if (v === 'it' || v === 'en') return v;
    // Il server puo imporre una lingua; 'auto' lascia decidere al browser.
    const dal = DATI.lingua;
    if (dal === 'it' || dal === 'en') return dal;
    return (navigator.language || 'en').toLowerCase().startsWith('it') ? 'it' : 'en';
  };

  let lingua = linguaSalvata();
  let T = TESTI[lingua];
  document.documentElement.lang = lingua;

  /** Sostituisce %1, %2... nell'ordine degli argomenti. */
  const tr = (chiave, ...arg) =>
    String(T[chiave]).replace(/%(\d)/g, (_, i) => arg[i - 1] ?? '');

  function cambiaLingua() {
    lingua = lingua === 'it' ? 'en' : 'it';
    T = TESTI[lingua];
    document.documentElement.lang = lingua;
    try { localStorage.setItem('iamhere-lingua', lingua); } catch (e) {}
  }

  /*
   * Leaflet non e incluso nel pacchetto e va scaricato a parte: se manca,
   * senza questo controllo la pagina resterebbe nera senza dire perche,
   * ed e il primo inciampo di chiunque installi il server.
   */
  if (typeof L === 'undefined') {
    const avviso = document.createElement('div');
    avviso.id = 'invite';
    avviso.innerHTML =
      '<div class="card surface"><h1>Leaflet</h1>' +
      '<p>La libreria della mappa non e stata trovata in <code>leaflet/</code>. ' +
      'Dovrebbe esserci gia: se e stata rimossa, scaricala da leafletjs.com e ' +
      'rimetti <code>leaflet.js</code> e <code>leaflet.css</code> in quella cartella.</p>' +
      '<p>The map library was not found in <code>leaflet/</code>. It should already ' +
      'be there: if it was removed, download it from leafletjs.com and put ' +
      '<code>leaflet.js</code> and <code>leaflet.css</code> back in.</p></div>';
    document.body.appendChild(avviso);
    return;
  }

  const P = DATI.punti || [];
  const HA_DATI = P.length > 0;

  // ------------------------------------------------------------- geometria
  const R = 6371000, RAD = Math.PI / 180;

  /** Distanza in metri fra due punti (formula dell'emisenoverso). */
  function distanza(a, b) {
    const dLat = (b.lat - a.lat) * RAD, dLon = (b.lon - a.lon) * RAD;
    const h = Math.sin(dLat / 2) ** 2 +
      Math.cos(a.lat * RAD) * Math.cos(b.lat * RAD) * Math.sin(dLon / 2) ** 2;
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }

  /**
   * Velocità media del tratto: spazio diviso tempo fra i due rilevamenti.
   * È più onesta della velocità istantanea del GPS, che è quella di un
   * singolo istante. Se manca il tempo si ripiega sulla media delle due
   * letture istantanee, e se mancano anche quelle il tratto resta grigio.
   */
  function velocitaTratto(a, b) {
    const dt = (b.t || 0) - (a.t || 0);
    const ds = distanza(a, b);
    if (dt > 0 && dt < 3600) return (ds / dt) * 3.6;
    if (a.vel != null && b.vel != null) return ((a.vel + b.vel) / 2) * 3.6;
    return null;
  }

  /**
   * Rampa dal rosso (il tratto più lento del percorso) al blu (il più
   * veloce), passando per arancio, giallo e verde.
   */
  const RAMPA = [
    [0.00, [214, 58, 42]],    // rosso
    [0.25, [240, 124, 30]],   // arancio
    [0.50, [232, 190, 30]],   // giallo
    [0.75, [76, 175, 80]],    // verde
    [1.00, [45, 125, 235]]    // blu
  ];

  function rampa(f) {
    f = Math.max(0, Math.min(1, f));
    for (let i = 1; i < RAMPA.length; i++) {
      const [p0, c0] = RAMPA[i - 1], [p1, c1] = RAMPA[i];
      if (f <= p1) {
        const k = (f - p0) / (p1 - p0);
        return `rgb(${c0.map((c, j) => Math.round(c + (c1[j] - c) * k)).join(',')})`;
      }
    }
    return `rgb(${RAMPA[RAMPA.length - 1][1].join(',')})`;
  }

  const gradienteCss = () =>
    'linear-gradient(90deg,' +
    RAMPA.map(([p, c]) => `rgb(${c.join(',')}) ${p * 100}%`).join(',') + ')';

  /**
   * Il colore è relativo al viaggio, non a una scala assoluta: a piedi,
   * in bici o in treno "veloce" vuol dire cose diverse. Gli estremi sono
   * il 5° e il 95° percentile dei tratti, così un singolo salto del GPS
   * non si prende tutta la scala.
   */
  /** Grigio: velocità sconosciuta oppure non credibile. */
  const IGNOTO = '#8A9A9C';

  function coloreVel(v, sc, sospetto) {
    if (v == null || sospetto) return IGNOTO;
    return rampa((v - sc.lo) / (sc.hi - sc.lo));
  }

  function percentile(ordinati, p) {
    if (!ordinati.length) return null;
    const i = (ordinati.length - 1) * p;
    const lo = Math.floor(i), hi = Math.ceil(i);
    return ordinati[lo] + (ordinati[hi] - ordinati[lo]) * (i - lo);
  }

  /**
   * Un fix del GPS finito lontano, per una riflessione fra i palazzi o
   * all'uscita da una galleria, produce un tratto a velocità assurda, e
   * di solito due:
   * l'andata verso il punto sbagliato e il ritorno.
   *
   * La soglia è relativa al viaggio, come la scala dei colori: si parte
   * dal 90° percentile, che descrive l'andatura veloce ma reale, e si
   * lascia un margine largo. Una discesa in bici a 45 km/h passa, un
   * salto a 230 no. In treno la soglia si alza da sola. Con pochi punti
   * il 90° percentile è vicino al massimo e non scarta nulla: giusto,
   * perché con tre rilevamenti un picco non si distingue da un dato vero.
   */
  function sogliaPicchi(tratti) {
    const v = tratti.map(t => t.v).filter(x => x != null).sort((a, b) => a - b);
    if (v.length < 8) return Infinity;
    const p90 = percentile(v, 0.9);
    return Math.max(p90 * 3, p90 + 20);
  }

  function scalaVelocita(tratti) {
    const v = tratti.map(t => t.v).filter(x => x != null).sort((a, b) => a - b);
    if (!v.length) return { lo: 0, mid: 0, hi: 1, vuota: true };

    let lo = percentile(v, 0.05), hi = percentile(v, 0.95);
    const mid = percentile(v, 0.5);

    // Andatura costante (un treno in linea, una passeggiata regolare):
    // senza un minimo di apertura il tracciato verrebbe monocromo.
    const minimo = Math.max(1.5, mid * 0.4);
    if (hi - lo < minimo) {
      const c = (hi + lo) / 2;
      lo = Math.max(0, c - minimo / 2);
      hi = c + minimo / 2;
    }
    return { lo, mid, hi, vuota: false };
  }

  // ------------------------------------------------------------- formattazione
  const nf = (v, d = 0) => v == null ? '--' :
    v.toLocaleString(T.locale, { minimumFractionDigits: d, maximumFractionDigits: d });

  const metri = m => m == null ? '--' : m < 1000 ? nf(m) + ' m' : nf(m / 1000, 2) + ' km';

  // Attenzione al nome: t() è la traduzione, qui il parametro è un istante.
  const orario = ts => !ts ? '--' :
    new Date(ts * 1000).toLocaleTimeString(T.locale, { hour: '2-digit', minute: '2-digit', second: '2-digit' });

  const dataOra = ts => !ts ? '--' :
    new Date(ts * 1000).toLocaleString(T.locale, { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' });

  function durata(s) {
    if (s == null || s < 0) return '--';
    const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60;
    return h > 0 ? `${h} h ${String(m).padStart(2, '0')} min` : `${m} min`;
  }

  const bussola = g => g == null ? '--' : T.rosa[Math.round(g / 45) % 8] + ' (' + nf(g) + '°)';

  // ------------------------------------------------------------- mappa
  // Senza percorso si parte da dove dicono le impostazioni del server.
  const centro = HA_DATI
    ? [P[P.length - 1].lat, P[P.length - 1].lon]
    : (DATI.centro || [44.493772, 11.343093]);

  // preferCanvas disegna linee e cerchi in un unico canvas invece di
  // creare un nodo SVG per ognuno: con qualche centinaio di punti è la
  // differenza fra una mappa fluida e una a scatti sul telefono.
  const map = L.map('map', {
    zoomControl: false,          // riposizionato qui sotto
    attributionControl: true,
    preferCanvas: true
  }).setView(centro, HA_DATI ? 16 : (DATI.zoom || 13));

  /*
   * Senza un viaggio da mostrare la mappa resta un semplice sfondo: al
   * posto delle mattonelle si usa un'immagine gia scaricata. In questo
   * modo la pagina di presentazione non contatta nessun server esterno,
   * e chi arriva senza aprire un percorso non manda il proprio indirizzo
   * IP da nessuna parte.
   *
   * L'elenco dei viaggi fa eccezione: chi ci entra la mappa la vuole.
   */
  const SOLO_SFONDO = !HA_DATI && DATI.modo !== 'viaggi';

  if (SOLO_SFONDO) {
    document.getElementById('map').classList.add('sfondo-statico');
    map.attributionControl.addAttribution(
      'Map data &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> ' +
      'contributors, SRTM &middot; style <a href="https://opentopomap.org">OpenTopoMap</a> (CC-BY-SA)'
    );
  } else {
    // In basso a destra: l'angolo in alto a sinistra serve al pannello dei
    // viaggi e al pulsante che lo riapre.
    L.control.zoom({ position: 'bottomright' }).addTo(map);
  }

  if (!SOLO_SFONDO) {
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 19,
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
    }).addTo(map);
  }

  const stratoTratti = L.layerGroup().addTo(map);
  const stratoPunti = L.layerGroup().addTo(map);
  const stratoEstremi = L.layerGroup().addTo(map);

  /**
   * Su schermo stretto la legenda si apre con un pulsante: lasciata
   * sempre aperta coprirebbe una fetta di mappa e finirebbe sotto agli
   * altri comandi in basso. L'icona e la rampa stessa dei colori.
   */
  const legToggle = document.createElement('button');
  legToggle.id = 'legToggle';
  legToggle.className = 'tool';
  legToggle.type = 'button';
  legToggle.setAttribute('aria-pressed', 'false');
  legToggle.innerHTML =
    '<svg viewBox="0 0 24 24"><defs><linearGradient id="rampa" x1="0" y1="0" x2="1" y2="0">' +
    RAMPA.map(([p, c]) => `<stop offset="${p * 100}%" stop-color="rgb(${c.join(',')})"/>`).join('') +
    '</linearGradient></defs><rect x="3" y="9" width="18" height="6" rx="3" fill="url(#rampa)"/></svg>';
  legToggle.onclick = () => {
    const aperta = document.body.dataset.legenda === 'aperta';
    document.body.dataset.legenda = aperta ? 'chiusa' : 'aperta';
    legToggle.setAttribute('aria-pressed', String(!aperta));
  };
  document.getElementById('legend').after(legToggle);

  // ------------------------------------------------------------- disegno
  let stato = null;

  // Da dove si leggono i punti. In viaggi.php la sessione fa da chiave,
  // quindi non serve il codice nell'indirizzo.
  function urlDati(id) {
    return DATI.modo === 'viaggi'
      ? `viaggi.php?api=tracciato&id=${encodeURIComponent(id)}`
      : `?id=${encodeURIComponent(id)}&format=json` +
        (DATI.auth ? `&auth=${encodeURIComponent(DATI.auth)}` : '');
  }

  let idCorrente = null;
  let timerAggiorna = null;

  /**
   * Ricontrolla il viaggio finché non è concluso. Il confronto su
   * idCorrente ferma il ciclo precedente quando si cambia viaggio: senza,
   * due cicli si sovrascriverebbero a vicenda.
   */
  function avviaAggiornamento(id) {
    clearTimeout(timerAggiorna);
    idCorrente = id;
    const tick = async () => {
      if (idCorrente !== id || !stato || stato.finito) return;
      try {
        const r = await fetch(urlDati(id), { cache: 'no-store' });
        const j = await r.json();
        const noto = stato.punti[stato.punti.length - 1];
        const nuovo = Array.isArray(j.punti) ? j.punti[j.punti.length - 1] : null;
        const cambiato = nuovo && (
          j.punti.length !== stato.punti.length ||
          nuovo.lat !== noto.lat || nuovo.lon !== noto.lon
        );
        if (cambiato && idCorrente === id) {
          const aperto = apert;
          stato = calcola(j.punti);
          disegna(stato, false);
          if (aperto) { apert = null; pannello(aperto); }
        }
      } catch (e) { /* rete assente: riproviamo al giro dopo */ }
      timerAggiorna = setTimeout(tick, DATI.refreshMs);
    };
    timerAggiorna = setTimeout(tick, DATI.refreshMs);
  }

  // Due modi di guardare il viaggio: tutto il percorso, oppure l'ultimo
  // punto da vicino per vedere dove si trova adesso la persona.
  let inseguimento = false;
  let btnInquadra = null;

  function inquadraTutto(s) {
    if (!s || !s.punti.length) return;
    if (s.punti.length > 1) {
      map.fitBounds(L.latLngBounds(s.punti.map(p => [p.lat, p.lon])), { padding: [70, 70] });
    } else {
      map.setView([s.punti[0].lat, s.punti[0].lon], 17);
    }
  }

  function seguiUltimo(s, avvicina) {
    if (!s || !s.punti.length) return;
    const u = s.punti[s.punti.length - 1];
    if (avvicina) {
      map.setView([u.lat, u.lon], Math.max(map.getZoom(), 17), { animate: true });
    } else {
      // Durante l'aggiornamento spostiamo senza toccare lo zoom: chi sta
      // guardando ha gia scelto quanto vedere attorno al punto.
      map.panTo([u.lat, u.lon], { animate: true });
    }
  }

  function aggiornaInquadra() {
    if (!btnInquadra) return;
    const etichetta = inseguimento ? T.segui : T.inquadra;
    btnInquadra.title = etichetta;
    btnInquadra.setAttribute('aria-label', etichetta);
    btnInquadra.setAttribute('aria-pressed', String(inseguimento));
    btnInquadra.querySelector('svg').innerHTML = ICONE[inseguimento ? 'segui' : 'inquadra'];
  }

  // Se l'utente trascina la mappa a mano, l'inseguimento si spegne: e il
  // comportamento di qualunque navigatore, e senza di esso la mappa gli
  // toglierebbe di sotto la vista appena scelta. panTo non genera questo
  // evento, quindi l'aggiornamento automatico non si autodisattiva.
  map.on('dragstart', () => {
    if (!inseguimento) return;
    inseguimento = false;
    aggiornaInquadra();
  });

  function calcola(punti) {
    const tratti = [];
    let percorso = 0, salita = 0, discesa = 0;

    for (let i = 1; i < punti.length; i++) {
      const a = punti[i - 1], b = punti[i];
      const d = distanza(a, b);
      percorso += d;
      if (a.alt != null && b.alt != null) {
        const dq = b.alt - a.alt;
        if (dq > 0) salita += dq; else discesa -= dq;
      }
      tratti.push({ a, b, d, v: velocitaTratto(a, b), cum: percorso });
    }

    // I tratti sospetti vengono marcati prima di ogni statistica: non
    // devono decidere né la velocità massima né la scala dei colori.
    const soglia = sogliaPicchi(tratti);
    let scartati = 0;
    for (const t of tratti) {
      t.sospetto = t.v != null && t.v > soglia;
      if (t.sospetto) scartati++;
    }

    const buoni = tratti.filter(t => !t.sospetto);
    const vmaxTratto = buoni.reduce((m, t) => (t.v != null && t.v > m ? t.v : m), 0);

    const t0 = punti[0]?.t, t1 = punti[punti.length - 1]?.t;
    const secondi = (t0 && t1) ? t1 - t0 : null;

    return {
      punti, tratti, percorso, salita, discesa, secondi, scartati,
      // Ricalcolata a ogni aggiornamento: la scala segue i dati arrivati
      // fino a quel momento.
      scala: scalaVelocita(buoni),
      vmedia: (secondi > 0) ? (percorso / secondi) * 3.6 : null,
      vmax: vmaxTratto || null,
      finito: punti[punti.length - 1]?.evento === 'end'
    };
  }

  function popup(p, i, s) {
    const dopo = i < s.tratti.length ? s.tratti[i] : null;
    const prima = i > 0 ? s.tratti[i - 1] : null;
    const seg = dopo || prima;
    const v = seg ? seg.v : null;
    const dubbio = seg ? seg.sospetto : false;

    // I primi due restano anche se vuoti: sono l'identità del punto.
    const righe = [
      [T.ora, orario(p.t), true],
      [T.coordinate, p.lat.toFixed(6) + ', ' + p.lon.toFixed(6), true],
      [T.velocita, v == null ? '--'
        : nf(v, 1) + ' km/h' + (dubbio ? ' · ' + T.dubbio : '')],
      [T.quota, p.alt == null ? '--' : nf(p.alt) + ' m'],
      [T.precisione, p.acc == null ? '--' : '±' + nf(p.acc) + ' m'],
      [T.direzione, bussola(p.dir)],
      [T.satelliti, p.sat == null ? '--' : (p.satuso ?? '?') + '/' + p.sat],
      [T.batteria, p.bat == null ? '--' : p.bat + '%'],
      [T.rete, p.rete || '--'],
      [T.pressione, p.press == null ? '--' : nf(p.press, 1) + ' hPa'],
      [T.allaMeta, p.dist == null ? '--' : metri(p.dist)]
    ].filter(r => r[2] || r[1] !== '--');

    const etichetta = p.evento === 'start' ? T.partenza
      : p.evento === 'end' ? T.arrivo
      : tr('puntoDi', i + 1, s.punti.length);

    // Il pallino riprende il colore del tratto: collega il popup alla linea.
    const dot = coloreVel(v, s.scala, dubbio);
    return `<div class="head"><span class="dot" style="background:${dot}"></span>${etichetta}</div><dl>` +
      righe.map(r => `<dt>${r[0]}</dt><dd>${r[1]}</dd>`).join('') + '</dl>';
  }

  function disegna(s, primaVolta) {
    stratoTratti.clearLayers();
    stratoPunti.clearLayers();
    stratoEstremi.clearLayers();

    // Un tratto per segmento: è così che si ottiene il colore variabile.
    // L.polyline disegna una spezzata aperta, quindi non serve nessun
    // ritorno all'origine come con L.polygon.
    for (const t of s.tratti) {
      L.polyline([[t.a.lat, t.a.lon], [t.b.lat, t.b.lon]], {
        color: coloreVel(t.v, s.scala, t.sospetto),
        weight: 6, opacity: .9, lineCap: 'round', lineJoin: 'round'
      }).addTo(stratoTratti);
    }

    // Cerchi cliccabili: molto più facili da centrare col dito di una linea.
    // Il contorno scuro è lo stesso per tutti, estremi compresi: è quello
    // che li fa staccare dalla mappa in entrambi i temi.
    const bordo = varCss('--punto-bordo') || '#0B1416';

    s.punti.forEach((p, i) => {
      const estremo = i === 0 || i === s.punti.length - 1;

      // I punti di transito prendono il colore del tratto che ne esce.
      // Quando sono fitti finiscono per accavallarsi sulla linea: se
      // fossero neutri coprirebbero proprio l'informazione che conta,
      // mentre così restano parte del tracciato.
      const seg = i < s.tratti.length ? s.tratti[i] : s.tratti[i - 1];
      const vSeg = seg ? seg.v : null;
      const segSospetto = seg ? seg.sospetto : false;

      L.circleMarker([p.lat, p.lon], {
        radius: estremo ? 9 : 5,
        color: bordo, weight: 2, opacity: 1,
        fillColor: i === 0 ? '#7CC5D2'
          : estremo ? '#E4572E'
          : coloreVel(vSeg, s.scala, segSospetto),
        fillOpacity: 1
      }).bindPopup(popup(p, i, s)).addTo(estremo ? stratoEstremi : stratoPunti);

      // Il raggio di precisione dell'ultimo punto: dice quanto fidarsi.
      // Va disegnato sopra il tracciato ma non deve raccogliere i clic:
      // altrimenti copre il punto e le sue informazioni non si aprono.
      if (i === s.punti.length - 1 && p.acc) {
        L.circle([p.lat, p.lon], {
          radius: p.acc, color: '#E4572E', weight: 1,
          fillColor: '#E4572E', fillOpacity: .12,
          interactive: false
        }).addTo(stratoEstremi);
      }
    });

    if (primaVolta && s.punti.length > 1) {
      inquadraTutto(s);
    } else if (inseguimento) {
      // Arrivato un punto nuovo, la mappa lo raggiunge da sola.
      seguiUltimo(s, false);
    }

    const sc = s.scala;
    document.getElementById('legend').classList.toggle('show', !sc.vuota);
    document.querySelector('#legend .bar').style.background = gradienteCss();
    document.getElementById('legLo').textContent = nf(sc.lo, 1);
    document.getElementById('legMid').textContent = nf(sc.mid, 1);
    document.getElementById('legHi').textContent = nf(sc.hi, 1) + ' km/h';

    const ultimo = s.punti[s.punti.length - 1];
    document.getElementById('brandSub').innerHTML = s.finito
      ? T.arrivato + ' · ' + dataOra(ultimo?.t)
      : '<span class="live">' + T.inViaggio + '</span> · ' + dataOra(ultimo?.t);

    document.getElementById('legTitolo').textContent = T.legTitolo;
    document.getElementById('legSotto').textContent = T.legSotto;
    legToggle.title = T.legTitolo;
    legToggle.setAttribute('aria-label', T.legTitolo);
  }

  // ------------------------------------------------------------- pannelli
  const sheet = document.getElementById('sheet');
  let apert = null;

  function riepilogo(s) {
    const c = (v, k) => `<div class="cell"><div class="v">${v}</div><div class="k">${k}</div></div>`;
    const ultimo = s.punti[s.punti.length - 1];
    return `<h2>${T.riepilogoTit}</h2><div class="grid">` +
      c(metri(s.percorso), T.distanza) +
      c(durata(s.secondi), T.durata) +
      c(s.vmedia == null ? '--' : nf(s.vmedia, 1) + ' km/h', T.media) +
      c(s.vmax == null ? '--' : nf(s.vmax, 1) + ' km/h', T.massima) +
      c(nf(s.salita) + ' m', T.dislivelloSu) +
      c(nf(s.discesa) + ' m', T.dislivelloGiu) +
      c(s.punti.length, T.punti) +
      c(ultimo?.bat == null ? '--' : ultimo.bat + '%', T.batteria) +
      c(orario(s.punti[0]?.t), T.partenza) +
      c(orario(ultimo?.t), T.ultimoDato) +
      (s.scartati > 0 ? c(s.scartati, T.scartati) : '') +
      '</div>' +
      (s.scartati > 0 ? `<p class="note" style="margin-top:12px">${T.scartatiNota}</p>` : '');
  }

  /** Profilo altimetrico in SVG: nessuna libreria in più da caricare. */
  function altimetria(s) {
    const q = s.tratti.filter(t => t.a.alt != null && t.b.alt != null);
    if (q.length < 2) {
      return `<h2>${T.quotaTit}</h2><p class="note">${T.quotaVuota}</p>`;
    }

    const W = 1000, H = 150, ML = 46, MR = 12, MT = 12, MB = 24;
    const xs = [0, ...q.map(t => t.cum)];
    const ys = [q[0].a.alt, ...q.map(t => t.b.alt)];
    const xMax = Math.max(...xs) || 1;
    let yMin = Math.min(...ys), yMax = Math.max(...ys);
    if (yMax - yMin < 10) { const m = (yMax + yMin) / 2; yMin = m - 5; yMax = m + 5; }

    const px = v => ML + (v / xMax) * (W - ML - MR);
    const py = v => MT + (1 - (v - yMin) / (yMax - yMin)) * (H - MT - MB);
    const pts = xs.map((x, i) => `${px(x).toFixed(1)},${py(ys[i]).toFixed(1)}`).join(' ');
    const area = `${ML},${H - MB} ${pts} ${px(xMax).toFixed(1)},${H - MB}`;

    const cGriglia = varCss('--griglia') || '#2A3A3C';
    const cMuted = varCss('--muted') || '#93A7A9';
    const cLinea = varCss('--grafico') || '#7CC5D2';
    const cArea = varCss('--grafico-a') || 'rgba(124,197,210,.16)';

    const griglia = [0, .5, 1].map(f => {
      const v = yMin + (yMax - yMin) * f, y = py(v);
      return `<line x1="${ML}" y1="${y}" x2="${W - MR}" y2="${y}" stroke="${cGriglia}"/>` +
        `<text x="${ML - 6}" y="${y + 4}" fill="${cMuted}" font-size="12" text-anchor="end">${nf(v)}</text>`;
    }).join('');

    return `<h2>${T.quotaTit}</h2>` +
      `<svg id="chart" viewBox="0 0 ${W} ${H}" preserveAspectRatio="none">` +
      griglia +
      `<polygon points="${area}" fill="${cArea}"/>` +
      `<polyline points="${pts}" fill="none" stroke="${cLinea}" stroke-width="2.5" ` +
      `stroke-linejoin="round" vector-effect="non-scaling-stroke"/>` +
      `<text x="${ML}" y="${H - 6}" fill="${cMuted}" font-size="12">0</text>` +
      `<text x="${W - MR}" y="${H - 6}" fill="${cMuted}" font-size="12" text-anchor="end">${metri(xMax)}</text>` +
      '</svg>' +
      `<p class="note">${tr('quotaNota', nf(Math.min(...ys)), nf(Math.max(...ys)))}</p>`;
  }

  function pannello(nome) {
    if (!stato) { return; }
    if (apert === nome) {
      apert = null;
      sheet.classList.remove('open');
    } else {
      apert = nome;
      sheet.innerHTML = nome === 'stat' ? riepilogo(stato) : altimetria(stato);
      sheet.classList.add('open');
    }
    document.querySelectorAll('.tool[data-p]').forEach(b =>
      b.setAttribute('aria-pressed', String(b.dataset.p === apert)));
  }

  const ICONE = {
    inquadra: '<path d="M4 9V5.5A1.5 1.5 0 0 1 5.5 4H9M20 9V5.5A1.5 1.5 0 0 0 18.5 4H15M4 15v3.5A1.5 1.5 0 0 0 5.5 20H9M20 15v3.5a1.5 1.5 0 0 1-1.5 1.5H15"/>',
    segui:     '<path d="M4 9V5.5A1.5 1.5 0 0 1 5.5 4H9M20 9V5.5A1.5 1.5 0 0 0 18.5 4H15M4 15v3.5A1.5 1.5 0 0 0 5.5 20H9M20 15v3.5a1.5 1.5 0 0 1-1.5 1.5H15"/><circle cx="12" cy="12" r="2.7" fill="currentColor" stroke="none"/>',
    riepilogo: '<path d="M5 20V12M12 20V5M19 20v-5M3 20h18"/>',
    quota:     '<path d="M3 18l5.5-7.5L12 15l3.5-5L21 18z"/>',
    punti:     '<circle cx="5.5" cy="12" r="1.9" fill="currentColor" stroke="none"/><circle cx="12" cy="12" r="1.9" fill="currentColor" stroke="none"/><circle cx="18.5" cy="12" r="1.9" fill="currentColor" stroke="none"/>',
    auto:      '<circle cx="12" cy="12" r="8.2"/><path d="M12 3.8a8.2 8.2 0 0 0 0 16.4z" fill="currentColor" stroke="none"/>',
    chiaro:    '<circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.2M12 19.8V22M4.2 4.2l1.6 1.6M18.2 18.2l1.6 1.6M2 12h2.2M19.8 12H22M4.2 19.8l1.6-1.6M18.2 5.8l1.6-1.6"/>',
    scuro:     '<path d="M20.5 14.6A8.6 8.6 0 0 1 9.4 3.5a8.6 8.6 0 1 0 11.1 11.1z"/>'
  };

  // ------------------------------------------------------------- tema
  const TEMI = ['auto', 'chiaro', 'scuro'];
  const etichettaTema = k =>
    k === 'auto' ? T.temaAuto : k === 'chiaro' ? T.temaChiaro : T.temaScuro;
  const scuroDiSistema = window.matchMedia('(prefers-color-scheme: dark)');

  const temaScelto = () => document.documentElement.dataset.tema || 'auto';

  function applicaTema(scelta) {
    const d = document.documentElement;
    d.dataset.tema = scelta;
    d.dataset.attivo = scelta === 'auto'
      ? (scuroDiSistema.matches ? 'scuro' : 'chiaro')
      : scelta;
    try { localStorage.setItem('iamhere-tema', scelta); } catch (e) {}
  }

  /** Il grafico è disegnato a mano: i colori vanno letti dal foglio di stile. */
  const varCss = nome =>
    getComputedStyle(document.documentElement).getPropertyValue(nome).trim();

  function pulsanti() {
    const barra = document.getElementById('tools');
    barra.innerHTML = '';
    const mk = (icona, titolo, azione, p) => {
      const b = document.createElement('button');
      b.className = 'tool';
      b.type = 'button';
      b.title = titolo;
      b.setAttribute('aria-label', titolo);
      b.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
        'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' + ICONE[icona] + '</svg>';
      if (p) { b.dataset.p = p; b.setAttribute('aria-pressed', 'false'); }
      b.onclick = azione;
      barra.appendChild(b);
      return b;
    };
    const mostra = DATI.pulsanti || {};

    if (mostra.fit !== false) {
      btnInquadra = mk('inquadra', T.inquadra, () => {
        inseguimento = !inseguimento;
        if (inseguimento) seguiUltimo(stato, true); else inquadraTutto(stato);
        aggiornaInquadra();
      });
      aggiornaInquadra();
    }
    if (mostra.riepilogo !== false) mk('riepilogo', T.riepilogoTit, () => pannello('stat'), 'stat');
    if (mostra.quota !== false) mk('quota', T.quotaTit, () => pannello('alt'), 'alt');
    if (mostra.punti !== false) {
    const occhio = mk('punti', T.puntiTit, () => {
      const visibili = map.hasLayer(stratoPunti);
      if (visibili) map.removeLayer(stratoPunti); else map.addLayer(stratoPunti);
      occhio.setAttribute('aria-pressed', String(!visibili));
    });
    occhio.setAttribute('aria-pressed', String(map.hasLayer(stratoPunti)));
    }

    let tema = null;
    if (mostra.tema !== false) {
    tema = mk(temaScelto(), etichettaTema(temaScelto()), () => {
      const prossimo = TEMI[(TEMI.indexOf(temaScelto()) + 1) % TEMI.length];
      applicaTema(prossimo);
      aggiornaTema(tema);
    });
    // Se l'utente è su "auto", seguiamo il sistema quando cambia in corsa.
    scuroDiSistema.addEventListener('change', () => {
      if (temaScelto() === 'auto') { applicaTema('auto'); aggiornaTema(tema); }
    });
    }

    if (mostra.lingua === false) return;

    // Il pulsante mostra la lingua attiva; il titolo dice dove si va.
    const lang = document.createElement('button');
    lang.className = 'tool lang';
    lang.type = 'button';
    lang.textContent = lingua.toUpperCase();
    lang.title = T.altraLingua;
    lang.setAttribute('aria-label', T.altraLingua);
    lang.onclick = () => {
      cambiaLingua();
      const aperto = apert;
      apert = null;
      pulsanti();              // titoli e pulsante lingua rifatti
      disegna(stato, false);   // popup, legenda e intestazione rifatti
      if (aperto) pannello(aperto);
    };
    barra.appendChild(lang);
  }

  function aggiornaTema(bottone) {
    if (!bottone) return;
    const scelta = temaScelto();
    bottone.title = etichettaTema(scelta);
    bottone.setAttribute('aria-label', etichettaTema(scelta));
    bottone.querySelector('svg').innerHTML = ICONE[scelta];
    // Il pannello aperto va ridisegnato: i colori del grafico cambiano.
    if (apert) { const a = apert; apert = null; pannello(a); }
  }

  /**
   * Mostra un viaggio sulla mappa al posto di quello attuale.
   * La usa viaggi.php quando si sceglie un percorso dall'elenco.
   */
  function caricaViaggio(punti, id) {
    inseguimento = false;
    apert = null;
    sheet.classList.remove('open');
    stato = calcola(punti || []);
    disegna(stato, true);
    aggiornaInquadra();
    avviaAggiornamento(id);
  }

  /** Toglie il tracciato dalla mappa e ferma gli aggiornamenti. */
  function svuotaMappa() {
    clearTimeout(timerAggiorna);
    idCorrente = null;
    stato = null;
    apert = null;
    sheet.classList.remove('open');
    stratoTratti.clearLayers();
    stratoPunti.clearLayers();
    stratoEstremi.clearLayers();
    document.getElementById('legend').classList.remove('show');
    document.getElementById('brandSub').textContent = '';
    map.setView(DATI.centro || [44.493772, 11.343093], DATI.zoom || 13);
  }

  // Interfaccia usata da viaggi.js.
  window.IAH = {
    caricaViaggio,
    svuotaMappa,
    testi: () => T,
    pulsanti: () => pulsanti()
  };

  // ------------------------------------------------------------- avvio
  if (DATI.modo === 'viaggi') {
    // Qui la mappa parte vuota: il viaggio lo sceglie l'elenco.
    document.getElementById('brandSub').textContent = '';
    pulsanti();
  } else if (!HA_DATI) {
    const inv = document.createElement('div');
    inv.id = 'invite';
    document.body.appendChild(inv);

    // Anche senza percorso servono tema e lingua, quindi la barra si
    // costruisce lo stesso, solo con questi due pulsanti.
    function invito() {
      const logo = document.querySelector('#brand svg').outerHTML
        .replace('id="lg"', 'id="lg2"').replace('url(#lg)', 'url(#lg2)');
      const negato = !!DATI.negato;

      inv.innerHTML =
        '<div class="card surface">' +
        `<div class="intestazione">${logo}<span>I am here</span></div>` +
        `<h1>${negato ? T.negatoTit : T.invitoTit}</h1>` +
        `<p>${negato ? T.negatoTesto : T.invitoTesto}</p>` +
        `<a class="principale" href="${DATI.download}" rel="noopener">${T.invitoAzione}</a>` +
        `<a class="secondario" href="viaggi.php">${T.invitoAccedi}</a>` +
        '</div>';
      document.getElementById('brandSub').textContent = T.sottotitolo;
      document.getElementById('legTitolo').textContent = T.legTitolo;
      document.getElementById('legSotto').textContent = T.legSotto;

      const barra = document.getElementById('tools');
      barra.innerHTML = '';

      const tema = document.createElement('button');
      tema.className = 'tool';
      tema.type = 'button';
      tema.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
        'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">' +
        ICONE[temaScelto()] + '</svg>';
      tema.title = etichettaTema(temaScelto());
      tema.onclick = () => {
        applicaTema(TEMI[(TEMI.indexOf(temaScelto()) + 1) % TEMI.length]);
        tema.querySelector('svg').innerHTML = ICONE[temaScelto()];
        tema.title = etichettaTema(temaScelto());
      };
      barra.appendChild(tema);

      const lang = document.createElement('button');
      lang.className = 'tool lang';
      lang.type = 'button';
      lang.textContent = lingua.toUpperCase();
      lang.title = T.altraLingua;
      lang.onclick = () => { cambiaLingua(); invito(); };
      barra.appendChild(lang);
    }

    invito();
    scuroDiSistema.addEventListener('change', () => {
      if (temaScelto() === 'auto') applicaTema('auto');
    });
  } else {
    stato = calcola(P);
    disegna(stato, true);
    pulsanti();

    avviaAggiornamento(DATI.id);
  }
})();

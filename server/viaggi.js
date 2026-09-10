/* IAH-VERSION 1.0.6.2 */
/*
 * I am here, pannelli dell'elenco viaggi.
 *
 * app.js disegna la mappa; questo file gestisce solo i riquadri che le
 * stanno sopra. Un pannello alla volta è visibile: periodi, poi viaggi,
 * poi la sola mappa. Il tracciato lo carica app.js tramite window.IAH.
 *
 * Copyright (C) 2026  I am here contributors
 * Licensed under the GNU General Public License v3 or later.
 */

(() => {
  'use strict';

  const T = {
    it: {
      titolo: 'I tuoi viaggi', h24: 'Ultime 24 ore', g7: '7 giorni', g30: '30 giorni',
      indietro: 'Indietro', vuoto: 'Nessun viaggio in questo periodo.',
      caricamento: 'Un momento…', punti: 'punti', inCorso: 'in corso',
      copia: 'Copia link', copiato: 'Copiato', elimina: 'Elimina',
      confermaElimina: 'Eliminare questo viaggio? L\u2019operazione non si può annullare.',
      server: 'Server v',
      strumenti: 'Importa ed esporta', esporta: 'Esporta', importa: 'Importa',
      espViaggio: 'Questo viaggio', espPeriodo: 'Il periodo che stai guardando',
      espTutto: 'Tutto l\u2019archivio', formato: 'Formato',
      scegliFile: 'Scegli i file', impLegge: 'GPX, JSON di I am here, GeoJSON e CSV.',
      impVuoto: 'Nessun tracciato riconosciuto nei file scelti.',
      impLetti: 'Trovati %n viaggi.', impConflitti: '%c erano gia presenti.',
      impFatti: 'Importati %n viaggi.', impSaltati: '%n saltati.',
      impProblemi: 'File non letti: %f',
      confTitolo: 'Viaggi gia presenti',
      confTesto: 'Alcuni viaggi del file hanno la stessa data di altri gia salvati. Cosa faccio?',
      confSalta: 'Salta', confSost: 'Sostituisci', confAff: 'Tieni entrambi',
      attendi: 'Un momento\u2026', espVuoto: 'Non c\u2019e niente da esportare.',
      esci: 'Esci dalla sessione', mostra: 'Mostra l\u2019elenco',
      chiudi: 'Chiudi l\u2019elenco', chiudiTutto: 'Chiudi',
      errore: 'Non riesco a leggere i dati.',
      giorni: ['domenica','lunedì','martedì','mercoledì','giovedì','venerdì','sabato']
    },
    en: {
      titolo: 'Your journeys', h24: 'Last 24 hours', g7: '7 days', g30: '30 days',
      indietro: 'Back', vuoto: 'No journeys in this period.',
      caricamento: 'One moment…', punti: 'points', inCorso: 'running',
      copia: 'Copy link', copiato: 'Copied', elimina: 'Delete',
      confermaElimina: 'Delete this journey? This cannot be undone.',
      server: 'Server v',
      strumenti: 'Import and export', esporta: 'Export', importa: 'Import',
      espViaggio: 'This journey', espPeriodo: 'The period you are viewing',
      espTutto: 'The whole archive', formato: 'Format',
      scegliFile: 'Choose the files', impLegge: 'GPX, I am here JSON, GeoJSON and CSV.',
      impVuoto: 'No track recognised in the chosen files.',
      impLetti: 'Found %n journeys.', impConflitti: '%c were already there.',
      impFatti: 'Imported %n journeys.', impSaltati: '%n skipped.',
      impProblemi: 'Files not read: %f',
      confTitolo: 'Journeys already there',
      confTesto: 'Some journeys in the file share a date with ones already saved. What should I do?',
      confSalta: 'Skip', confSost: 'Replace', confAff: 'Keep both',
      attendi: 'One moment\u2026', espVuoto: 'There is nothing to export.',
      esci: 'Sign out of the session', mostra: 'Show the list',
      chiudi: 'Close the list', chiudiTutto: 'Close',
      errore: 'Cannot read the data.',
      giorni: ['Sunday','Monday','Tuesday','Wednesday','Thursday','Friday','Saturday']
    }
  };

  const lingua = () => (document.documentElement.lang === 'it' ? 'it' : 'en');
  const t = () => T[lingua()];

  const pp = document.getElementById('pp');   // periodi
  const pv = document.getElementById('pv');   // viaggi del periodo scelto
  const riapri = document.getElementById('riapri');

  /**
   * Quali pannelli sono visibili lo decide il CSS leggendo data-livello:
   *   periodi  solo i periodi
   *   viaggi   periodi e viaggi affiancati (su telefono solo i viaggi)
   *   mappa    solo i viaggi, spostati a sinistra
   *   chiuso   niente, resta il pulsante per riaprire
   */
  function livelloA(v) {
    livello = v;
    document.body.dataset.livello = v;
    riapri.setAttribute('aria-label', t().mostra);
    riapri.title = t().mostra;
  }

  let periodi = null;          // { anni, anno, mesi }
  let vista = '7g';            // periodo scelto
  let etichettaVista = '';
  let livello = 'periodi';     // vedi livelloA()
  let idAperto = null;
  let ultimaLista = [];
  // Il periodo evidenziato e quello di cui e aperto l'elenco, non
  // l'ultimo su cui si e cliccato: chiuso l'elenco, non resta acceso nulla.
  let vistaAperta = null;
  /** Quando e valorizzato, il pannello mostra la scelta del formato. */
  let chiedeFormato = null;
  let statoImport = null;

  const esc = s => String(s ?? '').replace(/[&<>"']/g,
    c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

  // ------------------------------------------------------------ formati
  const nf = (v, d = 0) => v == null ? '--'
    : v.toLocaleString(lingua() === 'it' ? 'it-IT' : 'en-GB',
        { minimumFractionDigits: d, maximumFractionDigits: d });

  const km = m => m == null ? '--' : (m < 1000 ? nf(m) + ' m' : nf(m / 1000, 2) + ' km');

  function durata(s) {
    if (!s || s < 0) return '--';
    const h = Math.floor(s / 3600), m = Math.floor(s / 60) % 60;
    return h > 0 ? `${h} h ${String(m).padStart(2, '0')} min` : `${m} min`;
  }

  /* Ripiego: gli orari arrivano gia formattati dal server, che conosce il
     fuso del viaggiatore. Questa serve solo se mancassero. */
  const ora = ts => new Date(ts * 1000)
    .toLocaleTimeString(lingua() === 'it' ? 'it-IT' : 'en-GB', { hour: '2-digit', minute: '2-digit' });

  function giornoLungo(iso) {
    const d = new Date(iso + 'T12:00:00');
    const g = t().giorni[d.getDay()];
    return g.charAt(0).toUpperCase() + g.slice(1) + ' ' +
      d.toLocaleDateString(lingua() === 'it' ? 'it-IT' : 'en-GB',
        { day: '2-digit', month: '2-digit', year: 'numeric' });
  }

  // --------------------------------------------------------------- rete
  async function chiedi(qs, opzioni) {
    const r = await fetch('viaggi.php?' + qs, Object.assign({ cache: 'no-store' }, opzioni || {}));
    if (r.status === 403) { location.reload(); throw new Error('sessione'); }
    return r.json();
  }

  // ------------------------------------------------------- disegno pannelli

  /** Primo livello: titolo, periodi rapidi, mesi scorrevoli, cambio anno. */
  function disegnaPeriodi(cambiaLivello = true) {
    if (cambiaLivello) livelloA('periodi');
    const p = periodi;
    const conta = (p && p.rapidi) || {};
    /** I periodi che contengono una condivisione ancora aperta. */
    const aperti = (p && p.corso) || [];
    /* A destra della voce: l'etichetta arancione quando in quel periodo
       c'e una condivisione aperta, e il conteggio. */
    const coda = (k, n) => {
      if (!n && !aperti.includes(k)) return '';
      return `<span class="coda">` +
        (aperti.includes(k) ? `<span class="vivo">${esc(t().inCorso)}</span>` : '') +
        (n ? `<em>${n}</em>` : '') + `</span>`;
    };
    const rapidi = [['24h', t().h24], ['7g', t().g7], ['30g', t().g30]];

    const indice = p ? p.anni.indexOf(p.anno) : -1;
    const piuRecente = indice > 0 ? p.anni[indice - 1] : null;
    const piuVecchio = indice >= 0 && indice < p.anni.length - 1 ? p.anni[indice + 1] : null;

    pp.innerHTML =
      `<header class="pt"><h1>${esc(t().titolo)}</h1>
         <button type="button" class="strumenti" aria-label="${esc(t().strumenti)}" title="${esc(t().strumenti)}">
           <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
             <path d="M12 3v10M8.5 9.5L12 13l3.5-3.5M5 16v3.5h14V16"/>
           </svg>
         </button>
         <button type="button" class="chiudi" aria-label="${esc(t().chiudiTutto)}" title="${esc(t().chiudiTutto)}">
           <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
         </button>
       </header>

       <div class="fissi">
         ${rapidi.map(([k, et]) =>
           `<button type="button" class="voce${vistaAperta === k ? ' attiva' : ''}" data-v="${k}" data-et="${esc(et)}">
              <span>${esc(et)}</span>${coda(k, conta[k])}
            </button>`
         ).join('')}
       </div>

       ${p ? `<div class="scorre">
         ${p.mesi.length === 0
            ? `<p class="nota">${esc(t().vuoto)}</p>`
            : p.mesi.map(m =>
                `<button type="button" class="voce${vistaAperta === m.k ? ' attiva' : ''}" data-v="${m.k}" data-et="${esc(m[lingua()])} ${esc(p.anno)}">
                   <span>${esc(m[lingua()])}</span>${coda(m.k, m.n)}
                 </button>`).join('')}
       </div>

       <footer class="anni">
         ${piuRecente ? `<button type="button" class="anno" data-anno="${esc(piuRecente)}">↑ ${esc(piuRecente)}</button>` : '<span></span>'}
         <strong>${esc(p.anno)}</strong>
         ${piuVecchio ? `<button type="button" class="anno" data-anno="${esc(piuVecchio)}">${esc(piuVecchio)} ↓</button>` : '<span></span>'}
       </footer>` : `<p class="nota">${esc(t().caricamento)}</p>`}
       <div class="fondo">
         <a class="esci" href="?esci=1">${esc(t().esci)}</a>
         <span class="versione">${esc(t().server)} ${esc(DATI.versione || '')}</span>
       </div>`;

    pp.querySelectorAll('.voce').forEach(b => b.onclick = () => {
      vista = b.dataset.v;
      // Sempre da data-et: il testo del pulsante contiene anche il
      // conteggio, e finirebbe nel titolo come "30 giorni13".
      etichettaVista = b.dataset.et || b.querySelector('span').textContent.trim();
      apriViaggi();
    });
    pp.querySelectorAll('.anno').forEach(b => b.onclick = () => caricaPeriodi(b.dataset.anno));
    // La X del pannello principale chiude tutto: si riapre dal pulsante
    // in alto a sinistra.
    pp.querySelector('.chiudi').onclick = () => livelloA('chiuso');
    pp.querySelector('.strumenti').onclick = () => disegnaStrumenti();
  }

  /** Secondo livello: i viaggi del periodo scelto. */
  function disegnaViaggi(lista) {

    let corpo = '';
    if (!lista || lista.length === 0) {
      corpo = `<p class="nota">${esc(t().vuoto)}</p>`;
    } else {
      let giornoPrec = null;
      corpo = lista.map(v => {
        const intestazione = v.giorno !== giornoPrec
          ? `<h2 class="giorno">${esc(giornoLungo(v.giorno))}</h2>` : '';
        giornoPrec = v.giorno;
        return intestazione + `
          <article class="viaggio${idAperto === v.id ? ' scelto' : ''}" data-id="${v.id}">
            <button type="button" class="apri">
              <div class="quando">
                <strong>${esc(v.oraInizio || ora(v.inizio))}</strong>
                ${!v.corso && v.durata ? `<span class="sep">→</span>${esc(v.oraFine || ora(v.ultimo))}` : ''}
                ${v.corso ? `<span class="vivo">${esc(t().inCorso)}</span>` : ''}
              </div>
              <div class="numeri">
                <span>${esc(km(v.distanza))}</span>
                <span>${esc(durata(v.durata))}</span>
                <span>${v.punti} ${esc(t().punti)}</span>
                ${v.dest ? `<span class="dest">${esc(v.dest)}</span>` : ''}
              </div>
            </button>
            <div class="azioni">
              <button type="button" class="ic scarica" data-id="${v.id}" title="${esc(t().esporta)}" aria-label="${esc(t().esporta)}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M12 4v10M8.5 10.5L12 14l3.5-3.5M5 17.5v2h14v-2"/>
                </svg>
              </button>
              <button type="button" class="ic copia" data-link="${esc(v.link)}" title="${esc(t().copia)}" aria-label="${esc(t().copia)}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                  <rect x="9" y="9" width="12" height="12" rx="2.5"/><path d="M6 15H4.5A1.5 1.5 0 0 1 3 13.5v-9A1.5 1.5 0 0 1 4.5 3h9A1.5 1.5 0 0 1 15 4.5V6"/>
                </svg>
              </button>
              <button type="button" class="ic elimina" data-id="${v.id}" title="${esc(t().elimina)}" aria-label="${esc(t().elimina)}">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M4 7h16M9 7V5.5A1.5 1.5 0 0 1 10.5 4h3A1.5 1.5 0 0 1 15 5.5V7M6.5 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4L17.5 7"/>
                </svg>
              </button>
            </div>
          </article>`;
      }).join('');
    }

    if (chiedeFormato) {
      const scelto = chiedeFormato;
      chiedeFormato = null;
      pv.innerHTML =
        `<header class="pt">
           <button type="button" class="back" aria-label="${esc(t().indietro)}">
             <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>
           </button>
           <h1>${esc(t().esporta)}</h1>
         </header>
         <div class="scorre">
           <p class="etichetta">${esc(t().espViaggio)}</p>
           ${bloccoFormati('viaggio', scelto.id)}
         </div>`;
      pv.querySelector('.back').onclick = () => disegnaViaggi(ultimaLista);
      return;
    }

    pv.innerHTML =
      `<header class="pt">
         <button type="button" class="back" aria-label="${esc(t().indietro)}">
           <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>
         </button>
         <h1>${esc(etichettaVista)}</h1>
         <button type="button" class="chiudi" aria-label="${esc(t().chiudi)}" title="${esc(t().chiudi)}">
           <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round"><path d="M6 6l12 12M18 6L6 18"/></svg>
         </button>
       </header>
       <div class="scorre lista">${corpo}</div>`;

    // Indietro: da "solo elenco" si torna ad avere anche i periodi
    // accanto; da li si torna ai soli periodi. Su schermo largo, quando i
    // due pannelli sono gia affiancati, la freccia resta nascosta.
    pv.querySelector('.back').onclick = () => {
      if (livello === 'mappa') livelloA('viaggi'); else disegnaPeriodi();
    };
    pv.querySelector('.chiudi').onclick = chiudiViaggi;

    pv.querySelectorAll('.viaggio .apri').forEach(b => b.onclick = () => {
      scegliViaggio(Number(b.closest('.viaggio').dataset.id), true);
    });

    pv.querySelectorAll('.copia').forEach(b => b.onclick = async () => {
      const testo = b.dataset.link;
      try { await navigator.clipboard.writeText(testo); } catch (e) { window.prompt(t().copia, testo); }
      b.classList.add('fatto');
      b.title = t().copiato;
      setTimeout(() => { b.classList.remove('fatto'); b.title = t().copia; }, 1500);
    });

    pv.querySelectorAll('.scarica').forEach(b => b.onclick = () => {
      chiedeFormato = { ambito: 'viaggio', id: b.dataset.id };
      disegnaViaggi(ultimaLista);
    });

    pv.querySelectorAll('.elimina').forEach(b => b.onclick = async () => {
      if (!confirm(t().confermaElimina)) return;
      const corpoDati = new FormData();
      corpoDati.append('csrf', DATI.csrf);
      corpoDati.append('id', b.dataset.id);
      await chiedi('api=elimina', { method: 'POST', body: corpoDati });
      if (Number(b.dataset.id) === idAperto) { idAperto = null; window.IAH.svuotaMappa(); }
      apriViaggi();
      caricaPeriodi(periodi ? periodi.anno : null);   // i conteggi cambiano
    });
  }

  // ------------------------------------------------------------ azioni

  async function caricaPeriodi(anno) {
    try {
      periodi = await chiedi('api=periodi' + (anno ? '&anno=' + encodeURIComponent(anno) : ''));
      disegnaPeriodi(livello === 'periodi');
    } catch (e) { /* la sessione scaduta ricarica da sé */ }
  }

  // ------------------------------------------------- importa ed esporta

  const FORMATI = [
    ['gpx', 'GPX', 'Strava, OsmAnd, Garmin, QGIS'],
    ['json', 'JSON', 'I am here'],
    ['geojson', 'GeoJSON', 'QGIS, Leaflet, Mapbox'],
    ['csv', 'CSV', 'Excel, LibreOffice'],
  ];

  /** L'indirizzo di un'esportazione: il browser la scarica da solo. */
  function urlEsporta(ambito, formato, id) {
    let q = `viaggi.php?api=esporta&ambito=${ambito}&formato=${formato}`;
    if (ambito === 'viaggio') q += `&id=${encodeURIComponent(id)}`;
    if (ambito === 'periodo') q += `&v=${encodeURIComponent(vista)}`;
    return q;
  }

  function bloccoFormati(ambito, id) {
    return `<div class="formati">` + FORMATI.map(([k, nome, dove]) =>
      `<a class="formato" href="${urlEsporta(ambito, k, id)}" download>
         <strong>${esc(nome)}</strong><span>${esc(dove)}</span>
       </a>`).join('') + `</div>`;
  }

  /** Il pannello con esportazione in blocco e importazione. */
  function disegnaStrumenti() {
    livelloA('strumenti');
    const importabile = !DATI.soloEsporta;

    pv.innerHTML =
      `<header class="pt">
         <button type="button" class="back" aria-label="${esc(t().indietro)}">
           <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M15 5l-7 7 7 7"/></svg>
         </button>
         <h1>${esc(t().strumenti)}</h1>
       </header>
       <div class="scorre">
         <h2 class="sez">${esc(t().esporta)}</h2>
         ${vistaAperta ? `<p class="etichetta">${esc(t().espPeriodo)}: ${esc(etichettaVista)}</p>
            ${bloccoFormati('periodo')}` : ''}
         <p class="etichetta">${esc(t().espTutto)}</p>
         ${bloccoFormati('tutto')}

         ${importabile ? `
           <h2 class="sez">${esc(t().importa)}</h2>
           <p class="etichetta">${esc(t().impLegge)}</p>
           <label class="scegli">
             <input type="file" multiple accept=".gpx,.json,.geojson,.csv,application/gpx+xml,application/json,text/csv">
             <span>${esc(t().scegliFile)}</span>
           </label>
           <p class="esito" hidden></p>` : ''}
       </div>`;

    pv.querySelector('.back').onclick = () => disegnaPeriodi();
    const scelta = pv.querySelector('input[type=file]');
    if (scelta) scelta.onchange = () => caricaFile(scelta.files);
  }

  async function caricaFile(files) {
    if (!files || !files.length) return;
    const esito = pv.querySelector('.esito');
    esito.hidden = false;
    esito.textContent = t().attendi;

    const corpo = new FormData();
    corpo.append('csrf', DATI.csrf);
    for (const f of files) corpo.append('file[]', f);

    try {
      const j = await chiedi('api=importa', { method: 'POST', body: corpo });
      if (!j.viaggi) {
        esito.textContent = t().impVuoto +
          (j.problemi && j.problemi.length ? ' ' + t().impProblemi.replace('%f', j.problemi.join(', ')) : '');
        return;
      }
      esito.textContent = t().impLetti.replace('%n', j.viaggi) +
        (j.conflitti ? ' ' + t().impConflitti.replace('%c', j.conflitti) : '');

      if (j.conflitti) { statoImport = j; disegnaStrumenti(); mostraConflitti(); }
      else await confermaImport(j.token, 'salta');
    } catch (e) {
      esito.textContent = t().impVuoto;
    }
  }

  async function confermaImport(token, sceltaConflitti) {
    const corpo = new FormData();
    corpo.append('csrf', DATI.csrf);
    corpo.append('token', token);
    corpo.append('scelta', sceltaConflitti);
    const r = await chiedi('api=importa-conferma', { method: 'POST', body: corpo });
    const esito = pv.querySelector('.esito');
    if (esito) {
      esito.hidden = false;
      esito.textContent = t().impFatti.replace('%n', r.importati || 0) +
        (r.saltati ? ' ' + t().impSaltati.replace('%n', r.saltati) : '');
    }
    caricaPeriodi(periodi ? periodi.anno : null);
  }

  /** La domanda sui conflitti: sostituire, saltare o tenere entrambi. */
  function mostraConflitti() {
    const j = statoImport;
    if (!j) return;
    const d = document.createElement('div');
    d.className = 'domanda-modale';
    d.innerHTML =
      `<div class="riquadro surface">
         <h2>${esc(t().confTitolo)}</h2>
         <p>${esc(t().confTesto)}</p>
         <div class="scelte">
           <button type="button" data-s="salta">${esc(t().confSalta)}</button>
           <button type="button" data-s="affianca">${esc(t().confAff)}</button>
           <button type="button" data-s="sostituisci">${esc(t().confSost)}</button>
         </div>
       </div>`;
    d.querySelectorAll('button').forEach(b => b.onclick = async () => {
      d.remove();
      await confermaImport(j.token, b.dataset.s);
      statoImport = null;
    });
    document.body.appendChild(d);
  }

  /**
   * Chiude il solo elenco dei viaggi. Se il pannello dei periodi era
   * accanto resta aperto; se non c'era, non rimane niente.
   */
  function chiudiViaggi() {
    const restanoIPeriodi = livello === 'viaggi';
    vistaAperta = null;
    idAperto = null;
    disegnaPeriodi(false);
    livelloA(restanoIPeriodi ? 'periodi' : 'chiuso');
  }

  async function apriViaggi(livelloDaUsare) {
    vistaAperta = vista;
    disegnaPeriodi(false);          // aggiorna l'evidenziazione
    livelloA(livelloDaUsare || 'viaggi');
    pv.innerHTML = `<header class="pt"><h1>${esc(etichettaVista || t().titolo)}</h1></header>
                    <p class="nota">${esc(t().caricamento)}</p>`;
    try {
      const j = await chiedi('api=viaggi&v=' + encodeURIComponent(vista));
      ultimaLista = j.viaggi || [];
      disegnaViaggi(ultimaLista);
    } catch (e) {
      pv.innerHTML = `<p class="nota">${esc(t().errore)}</p>`;
    }
  }

  /**
   * daElenco distingue la scelta fatta cliccando una riga dall'arrivo
   * diretto con ?viaggio= nell'indirizzo: nel secondo caso non c'e un
   * elenco da tenere aperto, e si mostra la sola mappa.
   */
  async function scegliViaggio(id, daElenco = false) {
    idAperto = id;
    try {
      const j = await chiedi('api=tracciato&id=' + encodeURIComponent(id));
      window.IAH.caricaViaggio(j.punti, id);
      // L'indirizzo segue la selezione: ricaricando resti dove sei, e il
      // tasto indietro del browser riporta all'elenco.
      history.pushState({ id }, '', '?viaggio=' + id);
      // Su schermo largo l'elenco resta, spostato a sinistra, cosi si
      // passa da un viaggio all'altro senza riaprirlo ogni volta.
      // Su telefono sparisce del tutto per lasciare la mappa libera.
      const stretto = window.matchMedia('(max-width: 720px)').matches;
      livelloA(daElenco && !stretto ? 'mappa' : 'chiuso');
      if (daElenco) disegnaViaggi(ultimaLista);
    } catch (e) { /* niente da fare */ }
  }

  // ------------------------------------------------------------ avvio

  riapri.onclick = () => {
    if (vistaAperta) apriViaggi(idAperto ? 'mappa' : 'viaggi');
    else disegnaPeriodi();
  };

  window.addEventListener('popstate', () => {
    const id = new URLSearchParams(location.search).get('viaggio');
    if (id) {
      scegliViaggio(Number(id), !!vistaAperta);
    } else {
      idAperto = null;
      window.IAH.svuotaMappa();
      if (vistaAperta) apriViaggi(); else disegnaPeriodi();
    }
  });

  // Il pulsante della lingua di app.js ridisegna la sua barra: ridisegniamo
  // anche i pannelli, altrimenti resterebbero nella lingua precedente.
  new MutationObserver(() => {
    disegnaPeriodi(false);
    if (livello !== 'periodi') disegnaViaggi(ultimaLista);
  }).observe(document.documentElement, { attributes: true, attributeFilter: ['lang'] });

  livelloA('periodi');
  disegnaPeriodi();
  caricaPeriodi(null);

  const iniziale = new URLSearchParams(location.search).get('viaggio');
  if (iniziale) { scegliViaggio(Number(iniziale)); }
})();

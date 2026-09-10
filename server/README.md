# The I am here server

Collects the positions sent by the app and shows them on a map: track coloured
by speed, tappable waypoints, elevation profile, summary and a list of your
journeys.

Needs PHP 8.0 or later and a writable folder. No database, no framework, no
API keys.

*Questa guida è disponibile anche [in italiano](LEGGIMI.md).*

---

You can see it running, with nothing to install, at
**[demo.iamhere.it](https://demo.iamhere.it)**: the same code, with generated
journeys instead of yours.

## Installing

1. Copy every file to a host with PHP 8.0 or later.
2. Make the `dati/` folder writable.
3. Rename `settings-default.php` to **`settings.php`** and fill in the entries
   marked **CAMBIAMI**: `write_key`, `master_key` and `token_secret`. They must
   be **different from one another** and at least 24 random characters long.

   The package holds no `settings.php`: your keys therefore stay out of
   updates, and overwriting the files cannot touch them.
4. Open `verifica.php` in a browser: it checks permissions, sessions and keys
   and tells you what is missing. It also shows the fingerprint of `write_key`
   and `token_secret`, six digits to compare with the app's without writing
   the keys down anywhere. Delete it from the server once all green.

Then in the app, under *Settings → Endpoint*:

```
https://your-server.tld/dati.php?key=YOUR_WRITE_KEY&id={timestamp}&gps={lat},{lon}&t={tempo}&evento={evento}&acc={acc}&alt={alt}&vel={vel}&dir={dir}&sat={sat}&satuso={satuso}&bat={bat}&rete={rete}&press={press}&dist={dist}
```

Leave the other two URL fields empty: they inherit from this one.

---

## The pages

| | |
|---|---|
| `index.php?id=…&auth=…` | one journey on the map; with no id, the landing page |
| `viaggi.php` | the journey list, protected by the master key |
| `verifica.php` | installation check, delete it once set up |
| `dati.php` | receives positions from the app, not meant to be opened |

Next to a period's count, a ring turning around the number means there is a
share still open in there: you can see something is arriving right now without
opening the list.

At the bottom of the periods panel, next to the sign-out link, you can see the
version of the files in use: it tells you at a glance whether an update really
landed or the browser is still serving its cached copy.

**The journey list.** You sign in with the master key and stay in until you
sign out or close the browser; after five failed attempts the form locks for
ten minutes. The views are last 24 hours, 7 days, 30 days, then month by
month, and only periods with something in them appear. From each row you can
open the journey, copy the link to share, or delete it.

**The files.** Journeys live in `dati/<year>/<month>/`. Each one has
`<id>.json`, the full track, and `<id>.info.json`, the summary the list reads.

---

## Importing and exporting

In the journey list, the arrow icon at the top right opens the tools panel; the
same icon on a journey row exports that one journey.

**Outgoing formats.** GPX to talk to everything else: Strava, OsmAnd, Garmin,
QGIS. JSON to move journeys between two I am here installations losing
nothing. GeoJSON for mapping tools. CSV for spreadsheets.

The fields plain GPX has no room for, such as battery, satellites and network
state, travel inside `<extensions>`: programs that do not understand them
ignore them, and a journey exported and re-imported here loses nothing.
GeoJSON and CSV keep everything too.

**Incoming**, the same four formats are read, and several files can be chosen
at once. A journey's id is its start time, so it is taken from the first dated
point in the file; a track with no times ends up dated at the moment of
import, because there is no way to work it out.

If a journey in the file shares its date with one already there, you are asked
what to do: skip it, replace the old one, or keep both by shifting the new one
a few seconds.

The GPX reader is written without PHP's XML extensions. It therefore works on
any hosting, and more importantly it does not interpret external entities: an
uploaded file cannot make it hand back `settings.php`.

`leaflet/` already holds [Leaflet](https://leafletjs.com), the library that
draws the map, under the BSD 2-Clause licence: the text is in the `LICENSE`
file next to it.

## Updating

Copy the new files over the old ones. `settings.php` is not in the package, so
it stays where it is with your keys. If a version adds settings, you will find
them in `settings-default.php` and can carry them over by hand: anything
missing keeps its default.

## The settings

All in `settings.php`, where every entry is explained above itself.

### Security

**`write_key`**: the key the app must send to store a position. Anyone
holding it can add points to your journeys.

**`require_auth`**: whether a key is needed to *view* a journey too. With
`false`, guessing the address is enough, and the address only contains a time:
use it for testing only.

**`master_key`**: opens any journey and is the password for the journey list.
Never put it in the links you share.

**`per_trip_tokens`**: with `true` every journey has its own code and whoever
receives the link sees only that one. With `false` the master key is the only
one, and whoever receives a link can change the time in the address and see
your other journeys.

**`token_secret`**: the value the per-journey codes are derived from. Only
needed with `per_trip_tokens` on. Paste the same value into the app, under
advanced settings, so it can put the link inside text messages. Changing it
stops the links already shared from working; the data stays.

### Data

**`data_dir`**: where journeys are stored, relative to this folder or
absolute. A folder outside the website root is the best arrangement: nobody
can download the files by typing their address. If your hosting does not allow
it, leave `dati` and make sure the `.htaccess` in there stays put.

**`encrypt`** and **`encryption_key`**: encrypt the files on disk. Useful
when you can neither move the folder nor protect it. **Lose the key and the
encrypted journeys are gone**: there is no recovery. Files already stored in
the clear stay readable.

**`auto_delete`** and **`keep_days`**: delete journeys older than the given
number of days. The cleanup runs when a new position arrives, at most once an
hour.

**`timezone`**: the time zone journeys are dated with. Empty uses the
server's own, which is often UTC even when the server sits elsewhere.

It matters more than it looks: it decides the folder a journey is filed under,
the day it appears on in the list, and the times shown. With the wrong value, a
journey starting at one in the morning on the first of the month ends up dated
to the last day of the month before. Use the time zone of whoever carries the
phone, for instance `Europe/Rome`: the times shown are the ones it was for the
person who travelled, not for whoever is looking. Daylight saving is handled
for you.

**`stale_hours`**: after how many hours without new positions a journey
counts as finished. This covers journeys the app never closed, for instance
because the phone ran out of battery.

### Map and appearance

**`center_lat`**, **`center_lon`**, **`zoom`**: where the map opens when
there is no journey to show. Zoom: 10 a province, 13 a city, 16 a district.

**`btn_fit`**, **`btn_summary`**, **`btn_elevation`**, **`btn_points`**,
**`btn_theme`**, **`btn_language`**: the buttons on the map. `false` hides
them.

**`default_language`**: `auto` follows the visitor's browser, or `it` or `en`.

**`default_theme`**: `auto` follows the device, or `chiaro` or `scuro`.

**`refresh_seconds`**: how often the page looks for new positions during a
journey. Below 10 seconds you load the server for nothing.

The "Get I am here" button link is not among the settings: it is the
`IAH_SITO` constant at the top of `lib.php`.

---

## Privacy

Sharing a link means sharing where you have been: roads, stops, times, and
over time your home and your workplace. A message forwarded by mistake into a
group cannot be called back.

The server keeps journeys out of search engines (`robots.txt` and `noindex`),
stops the journey code reaching the sites opened from the map's links, blocks
direct download of the files with the `.htaccess` in `dati/`, and uses an
`HttpOnly` session cookie, `Secure` over HTTPS.

Two things are left to you: turn `require_auth` on, and use HTTPS. If your
hosting is not Apache the `.htaccess` has no effect: move the data folder
outside the website root, or turn encryption on.

---

## Common problems

**I sign in but the form comes back with no error**: the session is not being
kept. Open `verifica.php` and look at the "Sessioni" row.

**"ERRORE: chiave non valida"**: the `write_key` in the app does not match
the one in `settings.php`. Check for trailing spaces.

**"ERRORE: cartella dati non creabile"**: the web server cannot write to
`data_dir`.

**The journey is not available**: the link is old, or you changed
`token_secret` after generating it.

**I updated the files and nothing changed**: clear the browser cache, and
open `verifica.php`: the "File aggiornati" row names the files left behind.

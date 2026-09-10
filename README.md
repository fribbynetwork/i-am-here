# I am here

Share your live location, and keep the record of where you have been.
without Google, and on a server of your own if you want one.

*Questa guida è disponibile anche [in italiano](LEGGIMI.md).*

---

## What it does

You press start. The app takes a GPS fix at the cadence you set and sends it
where you decide: an HTTP endpoint, a text message, or both. Pick a
destination on the map and sharing stops by itself when you get there.

**You choose where the data goes.** There is no account, no analytics, no
telemetry. You write the URL yourself, with the parameter names and order your
receiver expects.

**It works without Google Play Services.** Location comes from the AOSP
location manager, maps from MapLibre, retries from WorkManager. It installs on
any device, including phones with no Google apps at all.

**Nothing is lost offline.** Positions are queued locally and sent in order
when the network comes back.

The project has two halves, and they are independent: you can use either one
on its own.

| | |
|---|---|
| **[`android/`](android/)** | the Android app: tracking, destinations, text messages, offline queue |
| **[`server/`](server/)** | an optional PHP endpoint plus a map viewer with journey history |

---

## The app

Sharing to an endpoint, by SMS, or both, each on its own schedule. Destinations
picked on a map with an arrival radius. Adaptive cadence, tighter near the
destination and looser when parked. Rich data alongside the coordinates:
accuracy, altitude, speed, heading, satellites, battery, barometric pressure.

A persistent notification with elapsed time, distance left and the outcome of
the last send. A Quick Settings tile, a diagnostics screen that tells you what
is missing, encrypted backup of the whole setup, light and dark themes, English
and Italian.

It also speaks **[YOURLS](https://yourls.org)**, if you run one: the link
inside a text message can be shortened before it goes out.

→ **[Every screen and setting](android/README.md)**

## The server

Collects the positions and draws the journey: the track coloured by speed,
tappable waypoints, an elevation profile, a summary, and a list of past
journeys you can browse by day or by month.

Each journey gets its own access code, so a link you share opens that journey
and nothing else. PHP 8 and a writable folder are all it needs. No database,
no framework, no API keys.

→ **[Installing and configuring](server/README.md)**

---

## Sending to something else

The endpoint is a URL you write, so anything that speaks HTTP will do. The
**Method** setting decides how the parameters travel: in the query string
(GET), as a form body, or as a JSON object.

**[Traccar](https://www.traccar.org)**, with its OsmAnd protocol on port 5055:

```
http://your-traccar:5055/?id=YOUR_DEVICE&lat={lat}&lon={lon}&timestamp={tempo}&altitude={alt}&accuracy={acc}&batt={bat}
```

**Home Assistant**, **Node-RED**, **n8n** and anything else with a webhook:
point the URL at the hook and name the parameters the way the flow expects.

**Nextcloud PhoneTrack** and other self-hosted trackers that accept a logging
URL: check their documentation for the exact parameter names.

If your receiver wants a shape the app cannot produce, a five-line PHP script
in the middle is usually enough.

→ **[How to build the URL, in detail](android/README.md)**

---

## Getting the app

APKs are on the [Releases](../../releases) page. Android will ask you to allow
installation from that source and Play Protect will warn you about an unknown
developer: both are normal outside the stores, and the app's README walks
through it.

→ **[All the releases](../../releases)**

---

## Privacy

Sharing a link means sharing where you have been: the roads you took, where you
stopped, at what times, and over time your home and your workplace. A message
forwarded by mistake into a group cannot be called back.

The app sends nothing anywhere except to the address you configure. The server,
if you use one, keeps journeys out of search engines and behind an access code.
Neither half phones home.

---

## Licence

**GNU General Public License v3.0 or later**: see [LICENSE](LICENSE). You may
use, study, modify and redistribute it, provided that anything you distribute
stays under the same licence and ships with its source.

| Component | Licence |
|---|---|
| [Leaflet](https://leafletjs.com), bundled in `server/leaflet/` | BSD 2-Clause |
| [MapLibre GL Native](https://maplibre.org) | BSD 2-Clause |
| [OkHttp](https://square.github.io/okhttp/) | Apache 2.0 |
| AndroidX, Kotlin, Compose | Apache 2.0 |
| Map tiles | [OpenFreeMap](https://openfreemap.org) in the app, OpenStreetMap on the web |
| Map data | © OpenStreetMap contributors, ODbL |

All compatible with the GPLv3 and keeping their own licences inside the
combined work. A summary offered in good faith, not legal advice.

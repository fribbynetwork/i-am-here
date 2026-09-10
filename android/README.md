# I am here

Shares your live location to an HTTP endpoint and by SMS, and stops on its own
once you reach a destination you picked on the map.

Works **without Google Play Services**: location comes from the AOSP
`LocationManager`, maps from MapLibre, retries from WorkManager. It installs on
any device, including ones with no Google apps at all.

*Questa guida è disponibile anche [in italiano](LEGGIMI.md).*

---

## What it does

You press **Start sharing**. From that moment the app takes a GPS fix at the
cadence you configured and transmits it, to your endpoint, by text message, or
both. When you reach the destination, or when you press **Stop**, one final
point goes out and sharing ends.

Every trip gets an id: the unix timestamp of the moment you started. It stays
the same for the whole trip, so your server can group all the points of one
journey without any handshake.

Without a destination the app is in **open-ended sharing**: it keeps
transmitting until you stop it.

## Main screen

The panel at the top turns orange while sharing is active, and shows elapsed
time and distance left. Below it are the three things you need: start, stop,
and the destination picker. Further down, the last fix (coordinates, accuracy,
speed, altitude, satellites) and the transmission counters.

A persistent notification stays in the shade while sharing runs, with elapsed
time, distance left, the outcome of the last send and a Stop button.

## Installing

The APK is on the [Releases](../../releases) page. Download it and open it:
Android will walk you through three things worth explaining, because none of
them means anything is wrong.

### "Unknown developer"

Play Protect shows a warning for any app that did not come from the Play
Store. Tap **More details** and then **Install anyway**. Before that, the
browser or file manager may ask you to *allow installation from this source*:
that is the app you downloaded with, not this one.

The warning is not about the app being unsafe. It only means Google has not
scanned it, which is true of everything distributed outside its store. If you
want to check the APK really came from the person who published it, compare
its certificate fingerprint with the one in the release notes.

### The SMS permission cannot be turned on

If you use the SMS channel, Android may refuse the permission and show a
shield saying **access was denied**. This is a protection applied to apps
installed straight from an APK file, and it blocks exactly the permissions
malware abuses most.

To unblock it:

1. **Settings → Apps → I am here**
2. The **⋮** menu at the top right
3. The entry about **restricted settings**, and confirm with PIN or fingerprint

The permission can then be granted normally, from the app's Diagnostics screen
or from Permissions.

This depends on how the app was installed, not on who signed it: installing
from F-Droid, Obtainium or `adb install` never triggers it.

### Battery

**Settings → Apps → I am here → Battery** must have **Allow background usage**
switched on. That is the setting that matters: with it off, the system treats
the app as restricted and can kill the service mid-journey.

Seeing the app listed as *Optimised* is normal and not a problem. A foreground
service with a persistent notification keeps running regardless. Turning
optimisation fully off is an improvement rather than a requirement: it helps
on long stops with the screen off and when resuming after a reboot. Tap the
row itself, not the switch, to reach the *Unrestricted* option.

The **Diagnostics** screen inside the app shows all of this at a glance. Red
rows stop sharing from working; amber rows only make it sturdier.

### Updating

Download the new APK and open it over the old one. Signed with the same key,
it installs keeping your saved points and settings. Never uninstall first
unless you want to start over.

## First run

Open **Diagnostics** from the top bar and clear every red row.

- **Location all the time** must be granted by hand in the system settings,
  choosing *Allow all the time*. Android does not let an app ask for it with a
  dialog.
- **Battery not optimised**: without the exemption the system suspends
  transmissions while the screen is off.
- On Xiaomi, Huawei, Oppo, Vivo and Samsung, look for **Autostart** in the
  phone settings and enable it. Those firmwares kill background services no
  matter which permissions you granted.

The map works immediately, with no key and no account: it uses OpenFreeMap
vector tiles built from OpenStreetMap data. Nothing to configure.

---

## Destinations

A destination is a point plus an arrival radius. You can create one by dragging
the map under the crosshair, or by typing coordinates. The orange circle on the
map is the real arrival area and resizes as you zoom, so what you see is what
you get.

Saved points can be renamed, moved and deleted. Each one can carry its own
radius, overriding the global default. A house in a dense old town and a
motorway exit want different numbers.

You can also use a point **as a destination without saving it**, for a one-off
trip.

**Arrival** is declared when you are inside the radius *and* the accuracy of
the fix is better than the radius itself. That second condition is what stops a
bad fix from ending your trip early. Arrival only arms once you have left the
radius at least once, so starting from inside the area does not immediately
count as arriving.

---

## Settings

### How to transmit

Endpoint and SMS are independent. Each can be off, or set to fire only at
**departure and arrival**, or **along the way too**.

### Endpoint

Write the full URL with its parameters. Names and order are entirely yours,
the app substitutes the placeholders and sends whatever you wrote.

```
https://your-server.tld/track.php?key=SECRET&lat={lat}&lon={lon}&id={timestamp}&t={tempo}
```

**Method** decides how those parameters travel: in the query string (GET), as a
form body (POST form), or as a JSON object (POST JSON). You configure the URL
once; switching method needs no other change.

Three templates, departure, along the way and arrival, so the server can be told
which phase it is looking at. Leave the last two empty and they inherit from the
first.

**HTTP headers**, one per line in `Name: value` form. Useful for moving an API
key out of the URL, where it would otherwise end up in the server access logs.

### Connection

Two switches that lower protection, both off by default.

**Allow plain HTTP** lets you use an `http://` endpoint, for a server that has
no certificate. Everything then travels readable, coordinates and the key in
the URL, so anyone on the same network can see it.

**Accept invalid certificates** is for a personal server with a self-signed
certificate. With certificate checking off the app can no longer tell your
server apart from anyone pretending to be it, so whoever controls the network
can read and alter what you send. Use it only towards a server you run
yourself.

A warning panel appears in the settings whenever either is on.

**Test send** builds the URL with a dummy position, shows it to you in full,
sends it, and reports the HTTP status and response body. Use it before you go
anywhere.

### SMS

Recipients (several, comma separated), the SIM to send from on dual-SIM
phones, and a separate message template for each phase.

Under each message you get the character count and how many texts it will
take, worked out on a sample position. Watch out for curly quotes, em dashes
and emoji: they switch the message to Unicode and the limit drops from 160
characters to 70. Ordinary accented letters do not.

The two **ready-made messages** buttons fill the field with an OpenStreetMap
link, which works with no server at all, or with an I am here link whose
address is taken from the endpoint you already configured.

The **minimum gap between texts** applies only to messages along the way;
departure and arrival always go through. Keep it generous: Android throttles
automatic sending and shows a confirmation dialog that nobody sees with the
screen off.

### Save and restore

Moves your whole setup to another phone. Tick what to include, settings,
saved destinations or both, choose a password and write the file wherever
you like; restoring asks for the same password.

The file is encrypted because it holds your server keys, your token secret and
your YOURLS token. **If you lose the password the file cannot be opened
again**: there is no recovery.

If the file brings destinations and you already have some, the app asks
whether to keep both or replace yours. A wrong password changes nothing at
all. The journey history is not included: it is a log, not configuration.

### Advanced

Collapsed at the bottom of the settings, because it only matters with your own
server or your own URL shortener.

**I am here server.** Paste here the same `token_secret` you set in the
server's `settings.php`. From then on `{auth}` works, and a text message can
carry the link to the live journey. The field stays masked and shows a
six-character fingerprint: if it matches the one the server shows, you pasted
the right value.

**YOURLS.** Address of your instance and the signature token from its admin
panel, plus the link to shorten, written with placeholders, exactly like the
endpoint URL. The result lands in `{yourls}`, which you use in the message.

Shortening happens **once per journey** or **on every message**: the first is
enough when the link follows the whole journey, the second is needed when each
message should point at where you are in that moment. If the network is down
or YOURLS does not answer, `{yourls}` falls back to the long link rather than
holding up the message.

**Test connection** checks the address and the token without creating
anything.

### Cadence

A point goes out when the **interval** has passed *and* you have moved at least
the **minimum distance**. Standing still, one goes out every four intervals
anyway, so a traffic jam or a coffee stop does not leave a hole in the track.

**Adaptive cadence** widens the interval when you are far from the destination
and tightens it in the last few hundred metres, where precision matters for
detecting arrival. It also relaxes when you are stationary.

### Trip

- **Default arrival radius**: the fallback for points that do not set their own.
- **Maximum duration**: zero means no limit; otherwise the trip closes itself
  after that long, so a journey that never really ends does not drain the
  battery all night.
- **Stop below battery**: zero means keep transmitting until the phone dies,
  using every send available. Any other value sends one last point at that
  level and closes.
- **Coordinate decimals**: six is about 11 cm. Beyond that is noise.

### Map

By default the app draws **OpenFreeMap** vector tiles: no API key, no
registration, no request limits, MIT licensed, data from OpenStreetMap.

Two optional overrides. A **MapTiler key**, if you prefer that provider. A
**custom style URL**, which wins over everything else and lets you point at a
style you host yourself.

Note for anyone forking this: the tile server at `tile.openstreetmap.org` is
**not** an option. Its usage policy names "distributing an app that uses tiles
from openstreetmap.org" as the very example of heavy use that is forbidden
without prior permission. The data is free; the servers are donated and are
not. Attribution to OpenStreetMap and the tile provider is required and stays
visible on the map.

### Appearance

Theme (system, light, dark) and language (system, English, Italiano). The
language setting overrides the phone's language for this app only.

---

## Placeholders

Use them in any URL or message. Anything misspelled stays visible exactly as
you typed it, so mistakes show up instead of silently vanishing.

| Placeholder | Meaning |
|---|---|
| `{lat}` `{lon}` | coordinates |
| `{timestamp}` | trip id: unix time when sharing started |
| `{tempo}` `{tempoiso}` | time of the fix |
| `{evento}` | `start`, `track` or `end` |
| `{acc}` `{vacc}` | horizontal and vertical accuracy, metres |
| `{alt}` | altitude, metres |
| `{vel}` `{velkmh}` `{dir}` | speed and direction of travel |
| `{sat}` `{satuso}` | satellites in view and used in the fix |
| `{bat}` `{carica}` | battery percentage and charging flag |
| `{rete}` | `wifi`, `mobile`, `ethernet`, `other` or `none` |
| `{press}` | barometric pressure, hPa, if the phone has the sensor |
| `{dist}` `{dest}` | metres left and destination name |
| `{prov}` | which provider produced the fix |
| `{ritardo}` | seconds between the fix and the send |
| `{auth}` | code that opens this journey on an I am here server |
| `{yourls}` | the shortened link, when YOURLS is set up |

`{timestamp}` is constant for the whole trip; `{tempo}` changes with every
point. **Always include `{tempo}`.** When the offline queue drains, your server
receives a batch of points at once, and without that value it cannot tell when
each of them was actually taken.

---

## Offline behaviour

Every point is written to a local queue before any network attempt. If a send
fails the queue stops rather than skipping ahead: sending later points first
would scramble the order on your server. Everything goes out in sequence when
the network returns, including after the trip has ended.

The main screen shows how many points are waiting, with a **Retry now** button.
**History** lists each point with its outcome, which is how you tell whether a
missing point was the app, the network or the server.

---

## Good to know

**HTTPS by default.** Plain HTTP is refused unless you turn it on under
*Settings → Connection*. The check lives in the app rather than in the static
network security config, which cannot be changed while the app runs.

**Text-message limits.** Android blocks automatic sending past roughly 30
messages in 30 minutes. The separate SMS interval exists for this reason.

**Keys in URLs** end up in server access logs. Move them to a header if that
matters to you.

---

## Licence

GNU General Public License v3.0 or later: see [LICENSE](../LICENSE).
For an overview of the whole project, start from the [main guide](../README.md).

# Warivo Companion — the owner's app

Where the scooter is, where it has been, what alerts have fired, and the remote lock.
A separate app on the **owner's own phone** — not the one bolted to the scooter.

Built by [enjoys.in](https://enjoys.in) · maintainer Mulayam Singh

Protocol and the owner API it calls: [../docs/FLEET.md](../docs/FLEET.md).

> **Not yet compiled.** Like the launcher, this has been reviewed but never built. See
> *Known first-build risks* below — one of them is near-certain.

---

## What it does

| Tab | |
| --- | --- |
| **Where** | Last reported position on a map with the day's trail, battery, speed, odometer, and the owner controls |
| **History** | Rides, newest first, with totals |
| **Alerts** | Speed/battery thresholds, one geofence, and the log of what has fired |
| **Setup** | Server address, owner token, which scooter |

Controls are **lock display**, **unlock**, **sound alarm** and **report now**.

## What "lock" is, and is not

It sends a full-screen owner message to the scooter's display and keeps it reporting
position. **It does not stop the scooter.** The Warivo node reads sensors and drives
nothing, so there is no electrical path from this app to the motor, and cutting power to a
moving vehicle is a crash rather than a feature. The app says this on the Setup tab too,
because an owner who believes they have immobilised a stolen scooter will act differently
from one who knows they have not.

What it does achieve is still worth having: a thief gets the owner's phone number instead
of a working dashboard, and the scooter keeps saying where it is.

## You need a server

Warivo does not run a service. This app and the head unit both talk to **your** server, and
[../docs/FLEET.md](../docs/FLEET.md) is the whole contract — one ingest endpoint for the
scooter, seven read/write endpoints for this app. It is deliberately not stubbed here: a
fake server would be worse than none.

Two tokens, and do not mix them:

- a **device token** goes on the scooter and can only upload,
- an **owner token** goes in this app and can read history and issue commands.

A device token is sitting on a vehicle that might be stolen. Treat it as public.

## Build

```bash
cd companion
cp local.properties.example local.properties   # then edit sdk.dir
gradle wrapper                                 # one-time
./gradlew assembleDebug
```

Or open `companion/` in Android Studio. It is its **own Gradle project**, not a module of
`launcher/` — the two apps ship separately, target different Android versions
(`minSdk 26` here against the head unit's 28), and should not be able to break each
other's build.

Static check, same as the launcher:

```bash
python3 ../tools/check_kotlin.py app/src/main/java
```

## Known first-build risks

1. **`addMarker` / `MarkerOptions` in `ui/TrackMap.kt`.** MapLibre inherited the
   annotations API from Mapbox and has been deprecating it across 10.x/11.x. If it is gone
   in the version that resolves, swap to the `maplibre-android-plugin-annotation` artifact
   or a symbol layer over a GeoJSON source. The camera logic is independent, so the map
   still centres on the scooter with the markers removed.
2. The usual version archaeology — AGP vs your Studio, the MapLibre pin, Kotlin and the
   Compose plugin having to match, Material icon names.

## Deliberate limitations

- **No push notifications.** An alert reaches the app when it next polls. Real push means
  FCM, which means Play Services, which the head unit deliberately avoids — and it would
  only ever be needed here, not there. Worth adding for this app alone if alerts need to
  arrive while it is closed.
- **No map-picker for zones.** A zone is centred on the scooter's last known position at
  one of two radii. There is no geocoder or draggable pin yet, and "where it is" is a
  better default than a guess.
- **The palette is duplicated** in `ui/theme/Theme.kt` rather than shared with the
  launcher. A shared module would couple two independent builds for a dozen colour tokens.
  Keep it in step with `launcher/.../ui/theme/Theme.kt` and
  `branding/mockups/warivo.css` by hand.
- **The owner token is in SharedPreferences.** Adequate for one owner's phone; not
  hardened against a rooted device. A fleet wants the Keystore or a real login.

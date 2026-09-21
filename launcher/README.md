# Warivo Launcher

Built by [enjoys.in](https://enjoys.in) · maintainer Mulayam Singh

The Android half of Warivo OS: a single-purpose home/launcher that turns a spare phone
into the scooter's head unit. Native Kotlin + Jetpack Compose, `minSdk 28` (Android 9),
`targetSdk 29` (Android 10).

This is **Path A** from [../docs/PROJECT_GUIDE.md](../docs/PROJECT_GUIDE.md) — a
kiosk on stock Android. The same APK is what Path B's custom ROM ships as its default
home, so nothing here is throwaway.

> **Not yet compiled.** There is no JDK or Android SDK on the machine this was written on,
> so the code has been reviewed but never built. Expect to fix a dependency version or two
> on the first `assembleDebug`.

---

## 1. Build

Needs **JDK 17** and the **Android SDK** (platform 34 to compile against; the app still
runs on 9/10).

```bash
brew install --cask temurin@17 android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools"

cd launcher
cp local.properties.example local.properties   # then edit sdk.dir
gradle wrapper                                 # one-time: creates ./gradlew
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Opening `launcher` in Android Studio does all of the above for you: it
writes `local.properties`, downloads the Gradle distribution named in
`gradle/wrapper/gradle-wrapper.properties`, and fetches the dependencies. No `gradlew`
is committed, which is fine for Studio — run `gradle wrapper` once if you want the
command line too.

### Before you build

There is no compiler on the machine this was written on, so a static check stands in:

```bash
python3 ../tools/check_kotlin.py
```

It catches unbalanced braces, identifiers used without an import, missing Compose
extension imports, and `com.warivo.os.*` imports pointing at nothing. It **cannot** catch
wrong overloads, a Material icon that moved between icon-set releases, a MapLibre API
rename, or type errors — which is exactly what the first real build will surface.

### If the first sync fails

The code has never been compiled, so start here rather than assuming a code bug:

| Symptom | Fix |
| --- | --- |
| *"Android Gradle Plugin requires a newer Studio"* | Lower `com.android.application` in [build.gradle.kts](build.gradle.kts) to the AGP your Studio ships, and `distributionUrl` to its matching Gradle. |
| *Unresolved `org.maplibre.gl:android-sdk:11.0.0`* | Pin a published 11.x from [Maven Central](https://central.sonatype.com/artifact/org.maplibre.gl/android-sdk). The `org.maplibre.android.*` imports in [MapPanel.kt](app/src/main/java/com/warivo/os/ui/MapPanel.kt) are 10.x-and-later names. |
| *Compose compiler / Kotlin mismatch* | `org.jetbrains.kotlin.android` and `org.jetbrains.kotlin.plugin.compose` must be the **same** version (both 2.0.21 here). |
| *Missing `Icons.Filled.*`* | An icon name moved between icon-set releases; swap it for a neighbour. |

Everything else — the BLE client, kiosk policies, trip maths — is plain Kotlin against
stable platform APIs and should not need version archaeology.

## 2. Run it as a normal app first

Install and open it. Without Device Owner it is an ordinary, exitable app — which is the
right way to develop. You should see:

- the rail on the left with a link dot (`SCAN` amber → `LINK` green),
- `Looking for Warivo-Node…` until the ESP32-C6 is powered,
- live gauges the moment the node connects.

Grant the location permission when asked. Android ties **BLE discovery** to location
permission, so without it the scan silently finds nothing.

## 3. Provision the kiosk

Device Owner can only be granted on a **factory-reset phone with no accounts added**.

```bash
# after factory reset, skip account setup, enable developer options + USB debugging
adb install -r app-debug.apk
adb shell dpm set-device-owner com.warivo.os/.kiosk.AdminReceiver
```

Then in **Setup**, turn on *Lock to the dashboard on boot*. From that point the phone
boots straight into Warivo (boot animation → PIN unlock → home) with no status bar, no nav
bar, no recents and no way out.

**The way back out** is in the same panel: *Unlock now* drops Lock Task for this session,
*Release phone* undoes every policy and gives up Device Owner for good. Keep that in mind
before flashing a build you have not tested unlocked.

## 4. What it does

Landscape, dark, laid out like a car head unit rather than a phone app: a floating top
status strip and a floating **bottom dock**, over gradient cards with a hairline edge.

It implements the hi-fi prototype in [../branding/mockups/](../branding/mockups/) — open
`index.html` for the boot intro and the screen map, or a `png/` render beside the app.
`warivo.css` and [theme/Theme.kt](app/src/main/java/com/warivo/os/ui/theme/Theme.kt) hold
the same palette and geometry and **must be changed together**.

**Flow:** power on → boot animation → PIN unlock → Home → dock.

| Panel | Notes |
| --- | --- |
| **Drive** | A fixed 560dp speed cluster (270° gradient ring, big numeral, `KM / H`, gear mode pill) with a telltale tile strip beneath it; then battery — SoC ring plus an estimated-range card with a segmented meter — and a 3×2 grid of tiles: power, trip, odometer, pack temp, current, avg speed. Gear, throttle and the indicator/headlight/high-beam/parking/reverse lamps appear automatically once the node sends those fields, see [../audit.md](../audit.md). |
| **Map** | Full-bleed MapLibre + raster OpenStreetMap following the phone's GPS, with a floating speed/position card and a recentre button. Tiles you have ridden through stay available offline via MapLibre's disk cache. The mockup's turn-by-turn and ETA cards are **not** built — they need a routing engine and a destination, which this has neither of. |
| **Music** | Player column (gradient artwork tile, title/artist, progress, five transport controls) beside an output card and the queue, so changing track never hides what is playing. Local files from MediaStore through `MediaPlayer`; output routes to the paired Bluetooth speaker over A2DP. Shuffle and repeat are drawn dimmed and not wired — `MediaPlayer` has no queue model to shuffle yet. |
| **Search** | The mockup's landing page: wordmark, a tall pill field with a round accent button, shortcut chips and recent searches. Searching swaps in a Google-only WebView; off-Google hosts are refused, so a tapped result cannot turn the head unit into a browser. |
| **Setup** | Link state, proximity beep (off by default), kiosk toggle, escape hatch. No mockup exists for this one, so it reuses the same card system. |

### The flow

```
power on ─▶ boot animation ─▶ PIN unlock ─▶ Home ─▶ dock: Drive · Map · Music · Search · Settings
                                                              Settings ─▶ About
```

All of it is built. `MainActivity` owns the three states (booting → locked → root); the
dock's lock button returns to the PIN screen.

**The PIN is the head unit's ignition, not the scooter's.** It keeps a stranger out of the
dashboard, the trip log and the tracking settings. The scooter still rides — the node is a
read-only tap and drives nothing.

Two deliberate departures from the mockup here:

- **No fingerprint key.** `BiometricPrompt` needs a `FragmentActivity` host plus the
  androidx.biometric dependency, and on a phone cradled to a handlebar the sensor is
  usually behind the mount. The slot is left empty rather than half-built.
- **The default PIN hint only shows while the PIN is still the default.** It disappears
  once the owner changes it in Settings, so the hint cannot become a password printed on
  the dashboard.

### Known deviations from the mockups

- The **script wordmark** on Search uses the platform cursive face. The mockup loads
  Kaushan Script from `branding/.fonts/`; bundling that TTF would match it exactly, at the
  cost of a font in the APK.
- The ring gauge's **glow** is a wider faint arc beneath the stroke, not a real blur —
  Compose's Canvas has no cheap blur, and the difference does not survive a glance.
- **Album art** is a gradient tile with the Warivo mark. Real artwork means an
  image-loading dependency.

## 5. Design notes worth knowing before you change things

- **MTU is not optional.** A telemetry frame is ~140 bytes; the default BLE MTU of 23
  truncates a notification to 20. `WarivoNodeClient` requests 247 and only then discovers
  services. Remove that and every frame arrives unparseable.
- **One GATT operation at a time.** Android drops a write issued while another is
  outstanding, so writes and the CCCD subscribe go through a queue.
- **No Play Services, anywhere.** Location is `LocationManager`, not
  FusedLocationProvider, because Path B's ROM ships without GApps.
- **A rail, not a pager.** The map and the search WebView both consume horizontal drags,
  so swipeable panels would fight them on exactly the two panels where it matters. The
  rail is on the left because a landscape head unit has width to spare and height to
  protect — a bottom bar would cost the speed gauge its diameter. *(The revised mockups
  move this to a bottom **dock** — see §4 “Revised flow & screens”; revisit when you
  rebuild the shell.)*
- **One card container, everywhere.** Every panel is built from `WarivoCard`, and the
  radius, padding and gap are single tokens in `theme/Theme.kt`. That is what makes the
  panels read as one system instead of five screens.
- **Distance comes from the node's odometer delta**, not from integrating speed, so a
  dropped BLE link costs no trip distance. A negative delta is read as a node reboot.
- **Locale matters in JSON.** GPS writes use `Locale.US`; a comma decimal separator would
  hand the firmware invalid JSON.
- **Brand tokens live in one place.** The palette and the launcher icon are taken from
  [../branding/warivo-mark.svg](../branding/warivo-mark.svg) — Deep Space Blue
  (`#05102A` / `#0B1E45`) with soft pink (`#FFC0CB`) as the only accent. Amber and
  red are reserved for warnings, so nothing decorative should use them.

## 6. Known gaps

- Never compiled (see above).
- `tile.openstreetmap.org` is fine for one personal phone but its tile policy forbids
  heavy use — swap in your own source or a MapTiler key before a second scooter.
- No boot animation yet (needs root on stock Android; it is Phase 5 / Path B).
- No offline map *pre-download*, only the opportunistic cache. MapLibre's `OfflineManager`
  would add "download this city".
- Music has no seek bar or media session; `MusicPlayer.positionMs()` is there for it.
- No fingerprint unlock (see *The flow* for why).
- No update channel: About says so rather than showing a button that cannot check.

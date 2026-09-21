# Warivo Launcher

Built by [enjoys.in](https://enjoys.in) · maintainer Mulayam Singh

The Android half of Warivo OS: a single-purpose home/launcher that turns a spare phone
into the scooter's head unit. Native Kotlin + Jetpack Compose, `minSdk 28` (Android 9),
`targetSdk 29` (Android 10).

This is **Path A** from [../../docs/PROJECT_GUIDE.md](../../docs/PROJECT_GUIDE.md) — a
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

cd android/warivo-launcher
cp local.properties.example local.properties   # then edit sdk.dir
gradle wrapper                                 # one-time: creates ./gradlew
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Opening `android/warivo-launcher` in Android Studio does all of the above for you: it
writes `local.properties`, downloads the Gradle distribution named in
`gradle/wrapper/gradle-wrapper.properties`, and fetches the dependencies. No `gradlew`
is committed, which is fine for Studio — run `gradle wrapper` once if you want the
command line too.

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
boots straight into the dashboard with no status bar, no nav bar, no recents and no way
out.

**The way back out** is in the same panel: *Unlock now* drops Lock Task for this session,
*Release phone* undoes every policy and gives up Device Owner for good. Keep that in mind
before flashing a build you have not tested unlocked.

## 4. What it does

Landscape, dark, laid out like a car head unit rather than a phone app: near-black page,
rounded cards with hairline edges, and a **bottom dock of five unlabelled monochrome
icons**. The design language follows the reference head units below — card grid, ring
gauges with tick marks, tiny uppercase labels over large values, stats split by thin
vertical rules — rendered in the Warivo brand rather than their blue.

| Panel | Notes |
| --- | --- |
| **Drive** | Three-column card grid. Speed gets the big tick-marked ring gauge because it is the only value read while moving; battery gets a second ring; power, current, odometer, consumption and temps are small cards, because those are checked at a stop. Gear, throttle and the indicator/headlight/high-beam/parking/reverse lamps appear automatically once the node sends those fields — see [../../audit.md](../../audit.md). |
| **Map** | MapLibre + raster OpenStreetMap, following the phone's GPS. Tiles you have ridden through stay available offline via MapLibre's disk cache. |
| **Music** | Artwork and big round transport controls on one side, the library list on the other, so changing track never hides what is playing. Local files from MediaStore through `MediaPlayer`; output routes to the paired Bluetooth speaker over A2DP. |
| **Search** | A Google-only WebView behind a pill field with a round accent button. Off-Google hosts are refused, so a tapped result cannot turn the head unit into a browser. |
| **Setup** | Link state, proximity beep (off by default), kiosk toggle, escape hatch. |

### UI references

The layout is modelled on these, not on a phone launcher:

- [car-lab.app dark home dashboard](https://car-lab.app/images/theme_dark_home_dashboard.png)
  — the closest match: card grid, bottom dock, ring gauge with a two-stat footer, media
  card with centred art and round transport controls.
- [Next-generation Android Auto concept](https://androidayuda.com/wp-content/uploads/2023/08/this-concept-envisions-the-next-generation-android-auto-196062_1.jpg)
  — the card row with a dim label top-left and an icon top-right, and the corner status
  pill.

What was taken: the card container, the dock, the ring gauge with ticks and a glowing
leading cap, the label/value hierarchy, and the divider-split stat footer. What was not:
their blue accent and app-drawer grid. Warivo has no app drawer by design, and the accent
is Celestial Aqua from [../../branding/warivo-mark.svg](../../branding/warivo-mark.svg).

## 5. Design notes worth knowing before you change things

- **MTU is not optional.** A telemetry frame is ~140 bytes; the default BLE MTU of 23
  truncates a notification to 20. `WarivoNodeClient` requests 247 and only then discovers
  services. Remove that and every frame arrives unparseable.
- **One GATT operation at a time.** Android drops a write issued while another is
  outstanding, so writes and the CCCD subscribe go through a queue.
- **No Play Services, anywhere.** Location is `LocationManager`, not
  FusedLocationProvider, because Path B's ROM ships without GApps.
- **A dock, not a pager.** The map and the search WebView both consume horizontal drags,
  so swipeable panels would fight them on exactly the two panels where it matters. The
  dock sits at the bottom because that is the edge a thumb reaches on a bar-mounted phone.
- **One card container, everywhere.** Every panel is built from `WarivoCard`, and the
  radius, padding and gap are single tokens in `theme/Theme.kt`. That is what makes the
  panels read as one system instead of five screens.
- **Distance comes from the node's odometer delta**, not from integrating speed, so a
  dropped BLE link costs no trip distance. A negative delta is read as a node reboot.
- **Locale matters in JSON.** GPS writes use `Locale.US`; a comma decimal separator would
  hand the firmware invalid JSON.
- **Brand tokens live in one place.** The palette and the launcher icon are taken from
  [../../branding/warivo-mark.svg](../../branding/warivo-mark.svg) — Deep Space Blue
  (`#05102A` / `#0B1E45`) with Celestial Aqua (`#5FF0DE`) as the only accent. Amber and
  red are reserved for warnings, so nothing decorative should use them.

## 6. Known gaps

- Never compiled (see above).
- `tile.openstreetmap.org` is fine for one personal phone but its tile policy forbids
  heavy use — swap in your own source or a MapTiler key before a second scooter.
- No boot animation yet (needs root on stock Android; it is Phase 5 / Path B).
- No offline map *pre-download*, only the opportunistic cache. MapLibre's `OfflineManager`
  would add "download this city".
- Music has no seek bar or media session; `MusicPlayer.positionMs()` is there for it.

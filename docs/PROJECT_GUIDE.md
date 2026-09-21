# Warivo OS — Project Guide

A single-purpose "car" head-unit for a Warivo 60V e-scooter. This guide is the source of
truth for the whole build: hardware, firmware, the network link, and the Android side
(kiosk **or** full custom ROM), plus a phased roadmap and the decisions still open.

---

## 1. Vision

Turn a spare Android phone into a **permanent, locked Warivo dashboard** — power on → a
**Warivo boot animation** → a **PIN unlock** (Ola-style) → a car-style home with live
speed/battery/range, a GPS map, music to a Bluetooth speaker, and Google search. **No other
apps, no home screen, no notifications shade** — a pure appliance. A small ESP32-C6 on the
scooter feeds it real telemetry over Bluetooth LE.

Design goals:

- **Single purpose** — the phone can only ever show the Warivo dashboard + Google search.
- **On by default** — GPS, WiFi and Bluetooth are always enabled at boot.
- **Works offline** — gauges, trip log and cached maps work with no signal.
- **Branded** — custom Warivo boot animation and theme.
- **Two-way sync** — scooter → phone (telemetry) and phone → scooter (GPS coordinates).

---

## 2. Why the scooter is NOT the computer

The Warivo scooter has only a motor controller and a 7-segment display. It has no
processor, no screen, and no data bus we can rely on. So there is nothing on the scooter
to "install AOSP onto".

**The head unit is the phone.** "AOSP for Warivo" therefore means one of:

- **Path A — Kiosk on stock Android** (fast, reversible): lock an existing phone into a
  single launcher app using Android's *Device Owner* + *Lock Task* mode.
- **Path B — Custom ROM** (the real thing): build **LineageOS/AOSP** for that specific
  phone model, bake in the Warivo defaults (boot animation, GPS/WiFi/BT on, our app as the
  launcher, minimal Google search) and flash it.

**We keep both paths** — they share the same core app, the **Warivo Launcher**:
- **Path A ships first** (days, any phone): the Launcher runs as a kiosk home on stock Android.
- **Path B follows** (the "Warivo OS" dream): the *same* Launcher is baked into a custom
  LineageOS/AOSP build as the default home. It needs an **unlockable bootloader** + device
  support.

Nothing is wasted: everything we build for Path A is the launcher Path B ships.

---

## 3. System architecture

```
                        Bluetooth LE (GATT notify + write)
  ┌──────────────────┐    telemetry JSON  ┌───────────────────────────┐
  │  WARIVO NODE      │ ─────────────────▶ │  WARIVO OS (Android phone) │
  │  ESP32-C6         │ ◀───────────────── │  - Dashboard gauges         │
  │  - battery V      │    phone GPS coords│  - GPS map (phone GPS)      │
  │  - wheel speed    │                    │  - Music → BT speaker       │
  │  - temp / current │                    │  - Google search only       │
  └──────────────────┘                    │  - Kiosk / custom ROM        │
        ▲   ▲                              └───────────────────────────┘
        │   │              phone keeps its own WiFi/data │  Bluetooth (A2DP)
   60V pack  wheel magnet                                ▼
                                              ┌──────────────┐
                                              │ BT speaker    │
                                              └──────────────┘
```

- **Scooter → phone:** battery %, speed, odometer, current/power, range.
- **Phone → scooter:** the phone's live GPS latitude/longitude (for logging / anti-theft /
  future features). The node stores the latest fix.

---

## 4. Phone ↔ scooter link (Bluetooth LE)

The ESP32-C6 has **Bluetooth LE**, so the scooter link is **BLE** — the clean choice:

- The node advertises as **`Warivo-Node`** with a GATT service (UUID `fff0`).
- The phone connects and **subscribes** to the telemetry characteristic (`fff1`) to receive
  a JSON packet ~5×/sec, and **writes** its GPS fix to the GPS characteristic (`fff2`).
- **Result:** the phone keeps its **own WiFi/cellular internet** (maps, search, music) *and*
  its **Bluetooth speaker** (A2DP) at the same time as the scooter link. No hotspot, no
  losing internet — exactly how a real car head unit behaves.

> The C6 also has WiFi if you ever want OTA firmware updates or a local web debug page, but
> the phone link itself stays on BLE.

---

## 5. Hardware — bill of materials

| Part | Purpose | Notes |
| --- | --- | --- |
| **Waveshare ESP32-C6** ([buy](https://robu.in/product/waveshare-esp32-c6-microcontroller-wifi-6-development-board-with-esp32-c6-wroom-1-n8/)) | Telemetry brain | WiFi 6 + **BLE**; multi-channel ADC; USB-C |
| Resistors **R1 = 270 kΩ**, **R2 = 10 kΩ** | Battery voltage divider | R1 = **two 135 kΩ / 150 kΩ in series** so each sees < 45 V |
| **ACS758** (e.g. 100B) hall current sensor | Motor/battery current | Inline on the main pack cable |
| **ADS1115** I²C ADC *(optional)* | Higher-precision voltage/current | The C6's own ADC is usually enough |
| **A3144** hall sensor + magnet | Wheel speed | Or a reed switch |
| **DS18B20** (waterproof) ×1–2 | **Outside/ambient temp** (+ optional battery temp) | 1-Wire; 4.7 kΩ pull-up. Battery temp may instead come from the BMS |
| **IR distance sensor** (Sharp GP2Y0A21) *(optional)* | **Obstacle distance** | Analog out → ADC. Short range (~10–80 cm), weak in bright sun — see note |
| **Active buzzer** *(optional)* | Proximity beep on the node | Only sounds when enabled from the phone's settings |
| **3.3 V zener** + 100 nF cap | ADC input protection/filtering | Across the divider tap |
| Buck converter **60 V → 5 V** *(or your 5 V rail)* | Power the node | You already have a 5 V outlet |
| Inline **fuse** on the divider tap | Safety | Low value, e.g. 100 mA |

### 5.1 Wiring (60 V pack)

All pins below are ESP32-C6 GPIO numbers and match the firmware constants.

**Battery voltage → GPIO1 (ADC):**
- Divider `R1 = 270 kΩ` (as 2× series), `R2 = 10 kΩ` → ratio **28**.
- Full pack ≈ 84 V → ≈ 3.0 V at the tap (safe).
- Tap → GPIO1, with the 3.3 V zener + 100 nF cap to GND.

**Speed → GPIO10:** A3144 out → GPIO10, VCC 3.3 V, 10 kΩ pull-up; magnet on the wheel/disc.

**Current → GPIO2 (ADC):** ACS758 inline on the main cable, analog out → GPIO2 (its own
divider to stay < 3.3 V). Or read it from the ADS1115 / the BMS instead.

**Temperature → GPIO11:** DS18B20 data → GPIO11 with a **4.7 kΩ pull-up to 3.3 V**. On one
1-Wire bus, **index 0 = outside/ambient**, **index 1 = battery pack** (optional). If the
BMS is smart, battery temp comes from it and you only need the outside sensor.

**Obstacle distance → GPIO3 (ADC):** IR sensor (Sharp GP2Y0A21) analog out → GPIO3. It's
short-range (~10–80 cm) and struggles in direct sunlight — for reliable outdoor obstacle
range an **ultrasonic HC-SR04** is often better; either way it feeds the same `dist` field.

**Buzzer → GPIO20 (optional):** active buzzer for the node-side proximity beep. It only
sounds when the phone enables the beep in settings (via the config characteristic).

Pin summary: battery `GPIO1`, current `GPIO2`, distance `GPIO3`, speed `GPIO10`,
temp `GPIO11`, buzzer `GPIO20`.

> ⚠️ **Safety:** 60 V packs push large current. Size R1 across two resistors so no single
> part sees the full voltage, fuse the tap, and **tie the ESP32-C6 GND to battery negative**
> (common ground) or every reading is garbage. Insulate all high-voltage joints.

### 5.2 BMS integration (read the battery the smart way)

**This scooter likely has no smart BMS** — its 78 kg dry weight points to a **lead-acid 60V
pack (5 × 12V)**, which is just batteries in series with no data port. Use the external
divider + ACS758 below; a **smart BMS can be added later** for per-cell and temperature data.

If a BMS *is* fitted, which kind decides how much you even need to sense:

- **Smart BMS** (Daly / JBD-Xiaoxiang / ANT etc. — has a **UART/CAN/BLE** port): it already
  reports **pack voltage, current, SoC, per-cell voltages and battery temperature**. Read it
  directly and you can drop the external divider *and* the current sensor.
  - Wire the BMS **UART TX/RX** to a spare ESP32-C6 UART (e.g. `Serial1` on two free GPIOs),
    share **GND**, and level-shift to **3.3 V** if the BMS is 5 V.
  - Implement the read in `readBmsBatteryTemp()` (and extend it to return V/SoC/current) —
    the firmware already prefers a valid BMS reading over the DS18B20 for battery temp.
  - **Confirm the BMS model first** — the byte protocol differs per vendor (Daly and JBD are
    the common, well-documented ones).
- **Dumb BMS** (protection + balancing only, no data port): keep the **external divider**
  (voltage) and **ACS758** (current), and use a **DS18B20 on the pack** for battery temp.

> Do **not** tap between individual cells for telemetry — read the whole pack at the BMS
> output or over its data port. Cell-level taps are a fire risk.

### 5.3 Controller

The controller drives the motor and the 7-segment display. On this scooter the display is
dumb, so the controller likely exposes **no useful data protocol** we can trust. Treat any
controller data line as **optional, best-effort reverse engineering** — battery, speed and
temperature already come from the BMS + our sensors, so the controller is not required for
telemetry.

---

## 6. Telemetry protocol (BLE GATT)

The node exposes one GATT service (UUID `fff0`) with two characteristics.

### `fff1` — telemetry (READ + NOTIFY) → scooter state
Subscribe for notifications; the node pushes this JSON ~5×/sec:
```json
{
  "v":    72.4,     // pack volts
  "soc":  63,       // battery %
  "spd":  24.8,     // km/h
  "odo":  1043.20,  // odometer km
  "a":    11.2,     // amps
  "w":    811,      // watts
  "rng":  38.5,     // estimated range km
  "tout": 31.5,     // outside/ambient temp °C
  "tbat": 34.2,     // battery temp °C (BMS or DS18B20; -127 = no sensor)
  "dist": 55,       // obstacle distance cm (-1 = no sensor / out of range)
  "gear": 2,        // selected gear 1/2/3 (0 = no switch fitted)
  "glim": 36,       // gear speed cap km/h (gear1=27, gear2=36, 0 = full/no cap)
  "avg":  18.4,     // trip average speed km/h (moving)
  "whkm": 24,       // consumption Wh/km (measured, or from SoC used)
  "mil":  74.2,     // mileage: projected km on a full charge at this consumption
  "cyc":  37,       // battery charge cycles (equivalent full charges, persisted)
  "up":   84213     // node uptime ms
}
```

### `fff2` — gps (WRITE) ← phone sends its coordinates
The phone writes its latest fix as JSON:
```json
{ "lat": 26.8467, "lon": 80.9462, "spd": 24.1, "ts": 1758150000 }
```
Used for trip logging and a future "find my scooter" feature.

### `fff3` — config (WRITE) ← phone pushes settings
The phone writes the proximity-beep setting (from the Settings screen):
```json
{ "beep": 1, "beep_cm": 40 }
```
`beep` (0/1) enables the node buzzer; `beep_cm` is the trigger distance in cm. When disabled,
the node never beeps — the feature is **opt-in**. (The phone may instead beep through the
speaker itself and leave the node buzzer off.)

Advertised name: **`Warivo-Node`**. Verify with the **nRF Connect** app before the
dashboard exists.

Firmware: [../firmware/warivo-node/warivo-node.ino](../firmware/warivo-node/warivo-node.ino)

---

## 7. Warivo OS — the Android side

**We keep both delivery paths, and both run the same app — the Warivo Launcher.** The
Launcher is the heart of the product: a purpose-built Android **home/launcher replacement**
that *is* the car UI. Building it well once means it drops into either path unchanged.

- **Path A (now):** install the Launcher on the spare phone, set it as HOME + Device Owner +
  Lock Task → instant kiosk car system on stock Android.
- **Path B (later):** bake the *same* Launcher into a custom LineageOS/AOSP ROM as the
  default home, with GPS/WiFi/BT defaults and the boot animation.

### 7.0 The Warivo Launcher (shared core)

A real launcher, not just a full-screen app — it registers as `CATEGORY_HOME` so the system
treats it as the home screen, and it owns the whole session:

- **Boot flow.** Power-on → Warivo **boot animation** → **PIN unlock** (Ola-style) → **Home**.
- **Home.** A glanceable **widget home** (map peek + Home/Work shortcuts, drive summary,
  now-playing); pressing home always returns here.
- **Surfaces:** home, dashboard, map, music, Google search, settings, about — reached from a
  persistent **bottom dock** (with vehicle quick-controls: headlight, horn, lock,
  speaker/volume). No app drawer, no access to other apps.
  > **Headlight and horn are display-only.** The node is a read-only tap (see `audit.md`)
  > and drives nothing, so a headlight *button* would do nothing at all. Once the switch
  > taps are wired the dock can **show** headlight state; switching it stays the
  > handlebar's job. The dock as built carries proximity beep, volume and screen lock.
- **Kiosk:** Lock Task + Device Owner hide the status/nav bars and block exits.
- **Radios:** keeps GPS/WiFi/BT on; auto-connects the BT speaker and the `Warivo-Node` (BLE).
- **Boot:** power-on → boot animation → PIN unlock → Home (as HOME); reconnects the node automatically.
- **Portable:** identical APK for Path A and Path B — the ROM just pre-installs it as home.

> **UI & flow reference:** the clickable hi-fi prototype in
> [../branding/mockups/](../branding/mockups/) (open `index.html`) is the design source of
> truth for every screen and the boot → PIN → home flow.

### Launcher references (open-source bases)

Two existing Kotlin/Compose car launchers to fork or learn from:

- **Open Launcher** (MIT) — https://github.com/dw2lam/openlauncher — offline-first, widget-grid
  instrument panel (speedo, trip, vitals, soundboard). **Fork candidate** — permissive licence;
  a Warivo BLE telemetry widget slots into its grid. *(Confirm its minSdk supports Android 10.)*
- **Femto Car Launcher** — https://github.com/seijikohara/femto-car-launcher — polished
  full-bleed map + trip + panels. **Design reference only** (requires Android 13+, so it can't
  run on this phone; also verify its licence before reusing code).

### 7.1 Feature set (the only things the system does)
0. **Boot & unlock** — Warivo boot animation, then an **Ola-style PIN unlock** before the
   dashboard is usable (a locked scooter stays locked to Warivo either way).
1. **Dashboard** — speed, battery %, range, power, odometer (from the BLE telemetry stream).
2. **Map** — full-screen GPS map using the **phone's** GPS.
3. **Music** — local files or a media panel, output to the **Bluetooth speaker**.
4. **Google search** — a single search surface, nothing else.
5. **Trip log** — distance, avg speed, consumption (works offline).
6. **Proximity beep** *(optional, off by default)* — warns with a beep when an obstacle is
   very close; toggled in **Settings** with an adjustable trigger distance. The beep can
   sound on the phone's speaker or on the node buzzer (via the `fff3` config write).

### 7.2 Path A — Kiosk app on stock Android (build this first)

> **Built:** [launcher](../launcher/) — see its README for
> the build, the `dpm` provisioning step and the way back out of the kiosk.

- Build the **Warivo Launcher** (**native Kotlin + Compose**, see §9) that hosts all
  the surfaces as panels and registers as `CATEGORY_HOME`.
- Make it a **Device Owner** via ADB provisioning (factory-reset phone, then):
  ```
  adb shell dpm set-device-owner com.warivo.os/.kiosk.AdminReceiver
  ```
- Use **Lock Task Mode** (`startLockTask()`) so the app can't be exited — no nav bar, no
  status bar pull-down, no recents.
- Set the app as the **HOME launcher** so boot lands straight on the dashboard.
- Force **GPS/WiFi/Bluetooth on at launch**; as Device Owner you can keep radios enabled
  and block the settings that would turn them off.
- **Google-search-only:** embed a search box that deep-links to Google, and block all other
  intents/apps via Lock Task allowlist.

**Radios:**
- **GPS** — request location permission, keep a foreground location service running.
- **Bluetooth** — auto-connect the paired **speaker** (A2DP) *and* the **Warivo-Node** (BLE)
  on boot.
- **WiFi** — kept on for maps/search over the phone's own network/data.

### 7.3 Path B — Custom AOSP / LineageOS ROM (the "Warivo OS" build)
Only viable if the phone has an **unlockable bootloader** and a LineageOS/AOSP device tree.

Steps:
1. **Confirm device support** — check the phone's codename on the LineageOS device list.
2. **Set up the build** — sync AOSP/LineageOS source, add the device tree + vendor blobs.
3. **Bake Warivo defaults:**
   - Ship the Warivo app as the **default Launcher** (remove Trebuchet/Quickstep).
   - Default **GPS/WiFi/BT = on** via `frameworks` overlays / `config.xml` defaults.
   - Preinstall **minimal Google search** only; **omit full GApps** (no Play Store, etc.).
   - Replace the **boot animation** (see §7.4).
   - Set it as **Device Owner** at first boot for permanent kiosk.
4. **Flash & test** — `fastboot flash`, verify radios, telemetry link, boot flow.

> Path B is a large, device-specific effort. Ship Path A first; the same **Warivo Launcher**
> becomes the ROM's home, so nothing is wasted.

### 7.4 Custom Warivo boot animation
Android boot animation = `/system/media/bootanimation.zip` (needs root on stock, or built
into the ROM in Path B).

Structure:
```
bootanimation.zip  (stored uncompressed / "store", not deflate)
├── desc.txt
├── part0/   (PNG frames — logo intro, plays once)
└── part1/   (PNG frames — loops until boot completes)
```
`desc.txt`:
```
1080 1920 30          # width height fps
p 1 0 part0           # play part0 once
p 0 0 part1           # play part1, loop forever (0) until boot done
```
Design the Warivo logo animation as numbered PNG frames (`0001.png`, `0002.png`, …) at the
phone's resolution.

---

## 8. Phased roadmap

- **Phase 0 — Bench proof:** flash `warivo-node.ino`, power the ESP32-C6, and confirm it
  advertises as **Warivo-Node** and streams telemetry JSON (check with **nRF Connect**). No
  scooter yet.
- **Phase 1 — Wire the scooter:** add the 60 V divider (→ GPIO1) + speed hall (→ GPIO10);
  verify real voltage and speed. Add the **ACS758** for current/power, the **DS18B20
  outside-temp sensor** (→ GPIO11), and — if the BMS is smart — the **BMS UART link** (which
  can replace the divider + current sensor).
- **Phase 2 — Warivo Launcher (Path A):** ✅ **written** —
  [launcher](../launcher/). Kotlin + Compose, registers as
  `CATEGORY_HOME`, subscribes to `fff1` and renders the gauges. Runs as a normal
  (exitable) app until it is provisioned. **Not yet compiled or run on hardware.**
- **Phase 3 — Map + music + search:** ✅ **written** — MapLibre/OSM map on the phone's GPS,
  MediaStore + `MediaPlayer` media panel to the BT speaker, Google-only WebView.
- **Phase 4 — Kiosk lockdown:** ✅ **written** — Device Owner policies, Lock Task with
  `LOCK_TASK_FEATURE_NONE`, persistent HOME binding, radios forced on, silent
  self-permission grants, and an escape hatch back out.
- **Phase 5 — Branding:** Warivo boot animation + theme.
- **Phase 6 — Custom ROM (Path B):** if/when the phone supports it, bake it all into
  LineageOS/AOSP.

---

## 9. Decisions

**Answered:**
- **Android version = 10** (API 29). Kiosk / Device-Owner / Lock-Task all supported. Note:
  this rules out **Femto Car Launcher** as a runtime (needs Android 13+) — use it as design
  reference only. Base the launcher on **Open Launcher (MIT)** or a fresh app with minSdk 29.
- **Maps = both** — online + offline. Use a **MapLibre / OpenStreetMap** base with an
  on-device tile cache; optional online Google layer with a user key.
- **Wheel = 90/90-12** → circumference ≈ **1.47 m** (set in firmware).
- **Battery = lead-acid 60V, 5 × 12V 30Ah (≈ 1800 Wh nominal), no BMS.** SoC range ~65 V
  full / ~52.5 V empty. Use the external divider + ACS758; a smart BMS can be **added later**.

- **App framework = native Kotlin + Jetpack Compose, built fresh** (`minSdk 28`,
  `targetSdk 29`). Rejected the alternatives: **Open Launcher**'s minSdk is unconfirmed for
  API 29 and its widget-grid architecture is not what we want, and **Flutter** would still
  need hand-written Kotlin for Device Owner, Lock Task and forcing the radios on — native
  code plus a bridge instead of just native code. Nothing here depends on Play Services,
  so the same APK drops into the Path B ROM which ships without GApps.
- **Music = local files** (MediaStore + `MediaPlayer`), output to the BT speaker via A2DP.
  Controlling a streaming app is out: Lock Task blocks launching other apps, and a stream
  needs signal the scooter often will not have.

**Still open:**
- **Offline maps** — the opportunistic MapLibre disk cache (shipped) vs adding
  `OfflineManager` to pre-download a city by name.

---

## 10. Glossary

- **Device Owner** — an Android provisioning mode granting an app deep control (kiosk,
  radio state, disabling other apps). Set once on a factory-reset device.
- **Lock Task Mode** — pins the device to one app; the user can't leave it.
- **GATT** — the BLE data model of services + characteristics the node exposes.
- **NOTIFY** — a BLE characteristic property that lets the node push updates to the phone.
- **A2DP** — the Bluetooth profile used for streaming audio to a speaker.

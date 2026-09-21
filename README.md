# Warivo OS

**An open head unit for ordinary electric scooters.**

Warivo OS turns a spare Android phone into a car-style dashboard for *any* e-scooter that
runs a plain DC controller — live speed, battery, range, power, a GPS map, music and
search, in a phone locked to that one job.

Built by **[enjoys.in](https://enjoys.in)** · maintainer **Mulayam Singh**

It is two parts:

1. **Warivo Node** — a **Waveshare ESP32-C6** board
   ([product page](https://robu.in/product/waveshare-esp32-c6-microcontroller-wifi-6-development-board-with-esp32-c6-wroom-1-n8/))
   bolted to the scooter that measures battery, speed, temperature and range, and streams
   telemetry over **Bluetooth LE**.
2. **Warivo OS** — a spare **Android phone** running the **Warivo Launcher**: a custom
   home/launcher that locks the phone to a single car-like dashboard (telemetry gauges,
   GPS map, music, Google search) — nothing else. Power-on runs a **boot animation → PIN
   unlock (Ola-style) → home** ([design prototype](branding/mockups/index.html)). The *same*
   launcher ships two ways and we keep **both**: a kiosk on stock Android now (fast), and a
   full custom **AOSP/LineageOS ROM** later (the "real" build).

> The scooter is a dumb machine (controller + 7-segment display only). It is **not** an
> Android device. The Android system runs on the **phone**, which acts as the head unit.

---

## Who this is for

This is for the **dumb** electric scooter — the kind sold with a motor controller, a
7-segment display and no data port. The ones that will never get a software update.

**It is not for Ola, Ather, TVS iQube, Bajaj Chetak or Simple One.** Those already ship a
connected TFT head unit, their own app and a locked internal bus; there is nothing here
they need, and their CAN buses are not ours to touch. If your scooter already has a
touchscreen, stop reading.

The reference build is a **Warivo 60 V** scooter, but nothing in the design is specific to
it. Everything the node reads, it reads from **physics, not from a protocol** — a voltage
divider across the pack, a hall sensor at the wheel, a current sensor on the main cable.
That is why the same build works across brands.

### Controller compatibility

**Any DC controller, because we never talk to it.** We do not decode a controller
protocol, so there is no per-model reverse engineering and no firmware to match:

| Controller | Works? | How |
| --- | --- | --- |
| Generic 48 V / 60 V / 72 V BLDC (square or sine wave) — Kunray, YIYUN, ZhiFu, unbranded | ✅ | Sensor set below. This is most scooters. |
| Brushed DC controllers | ✅ | Same sensor set. |
| Votol EM-series, Fardriver / ND, Sabvoton, Kelly (have a UART/CAN port) | ✅ | Sensor set works as-is; their data port is an *optional* upgrade for richer data. |
| Any controller driving a dumb 7-segment or LCD display | ✅ | The display is ignored entirely. |
| Ola / Ather / iQube / Chetak (integrated smart ECU) | ❌ | Out of scope on purpose — see above. |

The **optional** signal taps — throttle %, gear, headlight, high beam, parking light,
indicators, reverse — are read as raw voltages and switch states at the loom connector, so
they are also controller-agnostic. See [audit.md](audit.md) for the feasibility study and
the multimeter checklist. All taps are **read-only**: Warivo OS displays scooter state and
never commands the scooter.

The one **optional** exception is the immobiliser — a relay in series with the key
switch's low-current ignition line, which lets the app stop the scooter being ridden. It
engages only while the wheel is stopped, never touches motor current, brakes or steering,
and is off unless you fit the relay. See [docs/IMMOBILIZER.md](docs/IMMOBILIZER.md), which
covers the failure modes before the feature.

### Anti-theft, in layers

The reference scooter already has a **factory anti-theft alarm** — the usual remote-fob
siren and tilt/motion sensor. Warivo does not replace it, interfere with it, or need it:
it is a separate system on its own wiring, and it keeps working exactly as it does today.

Warivo adds three layers that the stock alarm does not have, and they are independent, so
you can fit any subset:

| Layer | What it does | Needs |
| --- | --- | --- |
| **Stock alarm** | Loud siren on tilt/motion, fob-controlled | Already on the scooter |
| **PIN unlock** | The head unit will not open without the PIN | Nothing — built in |
| **Immobiliser** *(optional)* | Scooter will not ride until the head unit unlocks it | One relay — [docs/IMMOBILIZER.md](docs/IMMOBILIZER.md) |
| **Live tracking** *(optional)* | Position every 5–30 s, zone alerts, remote lock | A server — [docs/FLEET.md](docs/FLEET.md) |

They answer different questions. The stock alarm makes noise *where the scooter is*. The
immobiliser means a thief who ignores the noise still cannot ride it. Tracking tells you
*where it went* if they take it anyway. The stock alarm alone handles the opportunist; the
combination handles someone with a van.

> **Note on the app's "sound alarm" command.** That sounds the **phone's** speaker, not the
> scooter's siren — the two are unconnected. The scooter's siren is far louder and already
> has a fob. Driving it from Warivo would mean a second relay on the alarm's trigger line;
> it is not wired, and the stock fob does the job. The alarm's *trigger state* could also
> be tapped as a read-only input like the other switch lines in
> [audit.md](audit.md) — that would let a triggered alarm raise a tracking alert — but that
> is unbuilt and needs the same multimeter session as the rest of the taps.

### The sensor set (the same on every scooter)

| What | Part | Reads |
| --- | --- | --- |
| Pack voltage | Resistor divider, R1 = 270 kΩ / R2 = 10 kΩ (ratio 28) | Volts → state of charge |
| Current | ACS758 hall sensor, inline on the main cable | Amps → watts, consumption |
| Wheel speed | A3144 hall sensor + magnet (or a reed switch) | Speed, distance, odometer |
| Temperature | DS18B20 ×1–2, 1-Wire | Ambient, and pack temp if no BMS |
| Obstacle *(optional)* | Sharp GP2Y0A21 IR, or HC-SR04 ultrasonic | Proximity warning |

Only two numbers change between scooters: the **pack voltage window** (full/empty) and the
**wheel circumference**. Both are constants at the top of
[firmware/warivo-node/warivo-node.ino](firmware/warivo-node/warivo-node.ino). A **smart
BMS**, if fitted, can replace the divider and the current sensor over UART.

---

## Reality check (read first)

| Assumption | Reality | Consequence |
| --- | --- | --- |
| "Build AOSP for the scooter" | The scooter has no SoC/screen to run Android | AOSP/kiosk runs on the **phone** |
| "How does the phone reach the scooter?" | ESP32-C6 has **BLE** | Link is **Bluetooth LE**; phone keeps its own internet + BT speaker |
| "One board reads voltage + current" | ESP32-C6 has a **multi-channel ADC** | Reads both directly (ADS1115 optional for precision) |
| "We must sense the battery ourselves" | A **smart BMS** may expose V/SoC/temp/current | Read the BMS over UART; skip extra sensors |
| "We must decode the controller" | The controller has nothing we can trust | Sense the physics instead — that is what makes it universal |

## Layout

```
warivo-os/
├── README.md                     ← you are here
├── GUIDE.me                      ← what is built, and how to start from zero
├── launcher/                     ← the Warivo Launcher: the Android head unit (Path A)
│   └── README.md                 ← build, provision the kiosk, escape hatch
├── os/                           ← Warivo OS ROM source: kernel + Android userspace (Path B)
│   └── README.md                 ← the tree, and how to build it
├── companion/                    ← the owner's app: track, history, alerts, remote lock
│   └── README.md                 ← build, and what "lock" really means
├── firmware/
│   └── warivo-node/
│       └── warivo-node.ino       ← ESP32-C6 BLE telemetry firmware
├── docs/
│   ├── PROJECT_GUIDE.md          ← full architecture, hardware, roadmap
│   ├── ROM_BUILD.md              ← Path B: why AOSP/GSI, prerequisites, build steps
│   ├── FLEET.md                  ← tracking protocol, owner API, alerts, remote lock
│   └── IMMOBILIZER.md            ← locking the scooter: wiring, interlock, failure modes
├── audit.md                      ← throttle / gear / lights signal-tap audit
├── branding/                     ← Warivo mark, logo, boot animation, mockups
└── tools/
    └── check_kotlin.py           ← stands in for a compiler until one is available
```

**New here?** Read [GUIDE.me](GUIDE.me) — it says what exists today and the exact order to
get it running. Then [docs/PROJECT_GUIDE.md](docs/PROJECT_GUIDE.md) for the architecture.

## Launcher references

- **Open Launcher** (MIT) — https://github.com/dw2lam/openlauncher
- **Femto Car Launcher** (design reference, Android 13+) — https://github.com/seijikohara/femto-car-launcher

## Licence

No licence file yet — see [GUIDE.me](GUIDE.me) under *Open questions*.

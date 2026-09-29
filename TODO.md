# Warivo OS — What's left to do

A living checklist of remaining work. The launcher and both firmwares are written and
**build clean**; the honest gap is that **nothing has run on real hardware yet**.

## Status snapshot (built & verified in software)

- **Launcher** (`launcher/`, Kotlin/Compose) — panels, kiosk/Device-Owner lock, boot +
  PIN, Google Maps + turn-by-turn nav, native-Android Settings, Bluetooth + node pickers,
  debloat. Compiles; verified on the emulator.
- **Firmware (Arduino)** (`firmware/warivo-node/`) — NimBLE telemetry node. Written, never flashed.
- **Firmware (Zephyr)** (`firmware/warivo-node-zephyr/`) — **compiles to a 512 KB ESP32-C6
  image** (5.81% of 8 MB flash) via `build-docker.sh`. Never flashed.
- **ROM tooling** (`os/`) — AOSP GSI scaffold (never built), a Magisk no-ROM route
  (`os/magisk/`, built), `provision.sh` (Device Owner over adb).
- **Companion app** (`companion/`) — owner's app scaffold.

---

## 1. Hardware bring-up  ← the real next milestone
- [ ] Get an **ESP32-C6** board on USB; `west flash` the Zephyr image (add `flash-docker.sh`).
- [ ] Confirm it boots + advertises `Warivo-Node` (nRF Connect / phone scan).
- [ ] Wire sensors per the pin map and calibrate:
  - [ ] Battery divider on GPIO1 — calibrate `DIVIDER_RATIO` against a multimeter.
  - [ ] Wheel hall/reed on GPIO10 — confirm pulses, tune debounce.
  - [ ] Confirm the ESP32-C6 **ADC channel↔GPIO** mapping in the devicetree overlay.
  - [ ] (later) DS18B20 pack temps, ACS758 current, IR distance, gear switch decode.
- [ ] Pair the **phone app ↔ node**, verify live telemetry end-to-end (speed/SoC/range).

## 2. Firmware hardening (from the audit)
- [ ] **BLE bonding/auth on the `fff4` immobiliser lock** — right now any BLE central can
      send `{"lock":0}`. Highest-value security fix.
- [ ] Task watchdog (auto-reboot on hang).
- [ ] OTA updates (dual-slot partition table; fits in 4 MB).
- [ ] Zephyr port: finish the sensor hooks (temps/current/distance) and **persist
      odometer/cycles** (Zephyr Settings/NVS — currently RAM only; the Arduino build persists).

## 3. Phone / launcher
- [ ] Add a real **Google Maps API key** to `launcher/local.properties` (`MAPS_API_KEY=`);
      the map is blank without it.
- [ ] Test navigation on a real GPS fix (not just the emulator).
- [ ] End-to-end kiosk test on a real phone: boot → dashboard → radios on → node connects →
      audio to a Bluetooth speaker.
- [ ] (nice-to-have) Music output card shows the connected BT device name.

## 4. ROM / device (pick a path)
- [ ] **Decide the phone** — model + LineageOS codename + bootloader unlockable. Blocks a
      device port and the exact flash steps.
- [ ] **No-ROM route:** on a rooted phone, flash `os/warivo-os-magisk.zip` (boot animation +
      radios-on + no setup wizard) + run `os/build/provision.sh` → single-app car UI. Test it.
- [ ] **Full ROM route:** build the AOSP GSI (or a LineageOS device port) on a Linux host;
      the Docker/volume build pattern from the Zephyr firmware applies.

## 5. Fleet / companion
- [ ] Stand up the owner's server per `docs/FLEET.md`; test the position/alert uplink.
- [ ] Companion app: wire Where/History/Alerts/Setup to the server.

## 6. Calibration & safety
- [ ] Immobiliser: fit the NC relay in series with the controller key-switch (KSI) line;
      test the node-side speed interlock on hardware (see `docs/IMMOBILIZER.md`).
- [ ] `audit.md` signal taps (throttle/gear/lights) — blocked on a multimeter session.

---

## Immediate next three
1. `flash-docker.sh` + flash the Zephyr firmware to a real ESP32-C6, confirm BLE advertise.
2. BLE bonding/auth on the immobiliser lock characteristic.
3. Drop in the Google Maps API key and do one real on-phone kiosk run.

## Known blockers
- **No confirmed ESP32-C6 in hand** → hardware bring-up can't start.
- **No chosen phone / codename** → ROM device port can't start.
- **No Google Maps API key** → map renders blank.

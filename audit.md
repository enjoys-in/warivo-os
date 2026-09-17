# Warivo Node — Signal Tap Audit

Feasibility audit for reading the scooter's **accelerator (throttle)** and **gear/mode
switch** into the ESP32-C6, so the dashboard can show live throttle % and the selected gear.

**Scope:** audit only — no firmware changes made yet. Everything below is **read-only**
tapping in parallel at the existing connectors; **nothing is cut and no line is driven**.

> ⚠️ **Safety first:** never *drive* or inject voltage onto the throttle signal wire — that
> could command unintended acceleration. We only *listen* with a high-impedance ADC tap, and
> we always share a common ground with the controller.

---

## 1. Accelerator / throttle (3 wires) — ✅ very feasible

### What it almost certainly is
An e-scooter throttle is a **hall-effect sensor** with three wires:

| Wire (typical colour) | Meaning |
| --- | --- |
| Red | **+5 V** supply from the controller (sometimes 3.3 V) |
| Black | **GND** |
| Green / White | **Signal** — analog voltage that rises with throttle |

The signal is a smooth analog voltage, typically:
- **~0.8–1.0 V** at rest (throttle released)
- **~3.6–4.2 V** at full throttle

So throttle position is just a voltage we can read on an ADC.

### How to read it
- Tap the **signal** wire (parallel, high-impedance) → ESP32-C6 **ADC** pin.
- The signal can exceed the C6's 3.3 V ADC limit, so add a small **divider** first:
  `R_top = 6.8 kΩ`, `R_bottom = 10 kΩ` → ratio ≈ 0.6 → 4.2 V maps to ≈ 2.5 V (safe).
- Tie **ESP32 GND ↔ controller GND** (mandatory reference).
- Suggested pin: **GPIO6** (ADC1), currently free.

### Calibration (needed, easy)
1. With a multimeter on signal↔GND, record **V_rest** (throttle released).
2. Record **V_full** (throttle fully pressed).
3. In firmware: `throttle% = (V - V_rest) / (V_full - V_rest) * 100`, clamped 0–100.
4. Add a small **dead-zone** near rest (e.g. ignore < 5 %) so noise doesn't show movement.

### Telemetry
Add `thr` (0–100 %) to the telemetry JSON.

### Verdict
**Feasible and low-risk.** One divider + one ADC pin + a 2-point calibration.

---

## 2. Gear / mode switch (3 wires) — ✅ feasible (likely digital)

### What it almost certainly is
The 3-speed / mode switch is usually a **combination switch**, not an analog sensor. It has
one common wire and two signal wires, and it selects a gear by grounding a **combination** of
the two signal lines:

| Gear | Signal A | Signal B |
| --- | --- | --- |
| 1 (eco) | GND | open |
| 2 (normal) | open | open |
| 3 (sport) | open | GND |

(The exact mapping varies by controller — confirm with a multimeter; see checklist.)

### How to read it
- Tap the two **signal** wires → two ESP32-C6 GPIOs set as **INPUT_PULLUP**.
- Read them as digital HIGH/LOW and decode the gear from the pair.
- Tie **grounds** common.
- Suggested pins: **GPIO18** and **GPIO19** (free digital).

### If it turns out to be analog instead
Some cheaper controllers encode the 3 positions as **3 distinct voltages** on a single wire
(a resistor ladder). If the multimeter shows three different steady voltages instead of
open/GND, treat it exactly like the throttle: one ADC pin + threshold bands.

### Calibration
- **Digital case:** record the A/B logic pair in each of the 3 positions → build the decode
  table above.
- **Analog case:** record the 3 voltages → pick midpoint thresholds between them.

### Telemetry
Add `gear` (1 / 2 / 3) to the telemetry JSON.

### Verdict
**Feasible.** Most likely 2 digital pins; worst case 1 ADC pin. Low-risk, read-only.

---

## 3. Measurement checklist (do this before wiring)

With a multimeter (black probe on controller GND), key ON, motor OFF, wheel off the ground:

**Throttle:**
- [ ] Identify GND, +V, signal on the 3-pin throttle connector.
- [ ] Record **V_rest** (released) and **V_full** (pressed).
- [ ] Confirm signal is smooth/continuous as you press (→ analog hall, as expected).

**Gear switch:**
- [ ] Identify the common wire and the two signal wires.
- [ ] For each of the 3 positions, record each signal wire as **GND / open / a voltage**.
- [ ] Decide: digital combination (GND/open) **or** analog (3 voltages)?

Send me these numbers and I'll finalise the calibration constants and wire it into the
firmware.

---

## 4. Proposed firmware additions (not yet implemented)

Once measurements are in, the plan is:

- New config constants: `PIN_THROTTLE` (ADC), `THR_REST_V`, `THR_FULL_V`; `PIN_GEAR_A`,
  `PIN_GEAR_B` (digital) — or `PIN_GEAR` (ADC) for the analog case.
- New readers: `readThrottlePct()` and `readGear()`.
- Two new telemetry fields:
  ```json
  { "thr": 42, "gear": 2 }
  ```
- No new BLE characteristic needed — they ride along in the existing `fff1` telemetry notify.

Pin budget after this (all suggestions, confirm against the WROOM-1 pinout):

| Signal | Pin | Type |
| --- | --- | --- |
| Battery voltage | GPIO1 | ADC |
| Current | GPIO2 | ADC |
| Obstacle distance | GPIO3 | ADC |
| **Throttle** | **GPIO6** | **ADC** |
| Speed | GPIO10 | interrupt |
| Temp (DS18B20) | GPIO11 | 1-Wire |
| **Gear A / B** | **GPIO18 / GPIO19** | **digital in** |
| Buzzer | GPIO20 | digital out |

---

## 5. Risks & notes

- **Read-only, always.** Tap in parallel; never source current into the throttle/gear lines.
- **Common ground is mandatory** — without it, ADC readings are meaningless.
- **Divide before the ADC** for anything that can exceed 3.3 V (the throttle does).
- **Warranty/tamper:** you're probing the loom at the connector, not modifying the
  controller, but be aware some warranties dislike any loom taps.
- These signals are for **display only** — Warivo OS shows throttle % and gear; it does not
  and must not control the scooter.

**Overall verdict:** both are worth adding. Throttle is a clean analog read; gear is a simple
digital decode. The only blocker is the 5-minute multimeter measurement in §3.

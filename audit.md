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

## 3. Lights, reverse & indicators (digital switches) — ✅ feasible

The reverse button, headlight, high-beam ("big light") and left/right indicators are all
**on/off switches**. Each drives a wire between a control line and GND (or +V). We read each
as a **digital state** — read-only, in parallel, never driving the line.

| Control | What we read | Telemetry field |
| --- | --- | --- |
| Reverse | button engaged? | `rev` (0/1) |
| Headlight | light on? | `head` (0/1) |
| High beam ("big light") | high beam on? | `high` (0/1) |
| Left indicator | left switch active? | `left` (0/1) |
| Right indicator | right switch active? | `right` (0/1) |

### Two catches to handle
- **Voltage level.** These lines may sit at 5 V, 12 V, or even the 60 V rail (headlights are
  often high-voltage). Anything above ~3.3 V must go through an **opto-isolator (PC817)** or a
  divider before the input. Optos are preferred — they read on/off at *any* line voltage and
  electrically isolate the ESP32 from the scooter's power.
- **Indicators blink.** Tap the **switch side** (before the flasher) for a steady
  left/right/off. If only the blinking lamp line is reachable, treat a recent pulse
  (within ~1 s) as "active".

### Pin strategy — use an I²C expander
That's 5+ new digital inputs on top of gear/throttle. Rather than burn ESP32 GPIOs, feed the
opto outputs into an **MCP23017 I²C port expander** (16 inputs on just SDA/SCL). It shares the
same I²C bus as the (optional) ADS1115, so the whole switch panel costs **2 pins total**.

```
[reverse/head/high/left/right] → PC817 optos → MCP23017 → I²C → ESP32-C6
```

### Verdict
**Feasible and clean.** Read-only opto taps into an MCP23017; booleans added to telemetry.

---

## 4. Measurement checklist (do this before wiring)

With a multimeter (black probe on controller GND), key ON, motor OFF, wheel off the ground:

**Throttle:**
- [ ] Identify GND, +V, signal on the 3-pin throttle connector.
- [ ] Record **V_rest** (released) and **V_full** (pressed).
- [ ] Confirm signal is smooth/continuous as you press (→ analog hall, as expected).

**Gear switch:**
- [ ] Identify the common wire and the two signal wires.
- [ ] For each of the 3 positions, record each signal wire as **GND / open / a voltage**.
- [ ] Decide: digital combination (GND/open) **or** analog (3 voltages)?

**Lights / reverse / indicators (for each switch):**
- [ ] Find the switch signal wire and its **voltage when ON** and **when OFF**.
- [ ] Note whether ON = connected to **GND** or to **+V**.
- [ ] If ON voltage > 3.3 V → that line needs an **opto/divider** (expected for lights).
- [ ] For indicators, find the **switch-side** wire (steady) vs the **lamp** wire (blinks).

Send me these numbers and I'll finalise the calibration constants and wire it into the
firmware.

---

## 5. Proposed firmware additions (not yet implemented)

Once measurements are in, the plan is:

- New config constants: `PIN_THROTTLE` (ADC), `THR_REST_V`, `THR_FULL_V`; `PIN_GEAR_A`,
  `PIN_GEAR_B` (digital) — or `PIN_GEAR` (ADC) for the analog case.
- **MCP23017** (I²C) for the switch panel — reverse, headlight, high beam, left, right.
- New readers: `readThrottlePct()`, `readGear()`, `readSwitches()`.
- New telemetry fields (all ride along in the existing `fff1` notify — no new characteristic):
  ```json
  { "thr": 42, "gear": 2, "rev": 0, "head": 1, "high": 0, "left": 0, "right": 1 }
  ```

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
| **I²C bus (ADS1115 + MCP23017)** | **GPIO22 (SDA) / GPIO23 (SCL)** | **I²C** |
| **Switch panel (rev/head/high/left/right)** | via **MCP23017** | I²C expander |

---

## 6. Risks & notes

- **Read-only, always.** Tap in parallel; never source current into any scooter line.
- **Common ground is mandatory** — without it, ADC readings are meaningless.
- **Divide before the ADC** for anything that can exceed 3.3 V (the throttle does).
- **Opto-isolate the light/reverse lines** — they may be at 12 V or 60 V; a PC817 reads them
  safely at any voltage and isolates the ESP32 from the scooter's power.
- **Warranty/tamper:** you're probing the loom at the connector, not modifying the
  controller, but be aware some warranties dislike any loom taps.
- These signals are for **display only** — Warivo OS shows state; it does not and must not
  control the scooter.

**Overall verdict:** all of it is trackable and worth adding. Throttle is a clean analog
read; gear is a digital decode; lights/reverse/indicators are opto taps into an MCP23017. The
only blocker is the 5-minute multimeter measurement in §4.

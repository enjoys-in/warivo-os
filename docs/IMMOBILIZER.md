# Warivo Immobiliser — locking the scooter, safely

**Yes, the app can genuinely stop the scooter from being ridden.** Not by touching the
motor or the brakes, and not while it is moving — by opening the controller's
**ignition-enable line**, in series with the key you already have, only when the wheel is
stopped.

This document is the design, the safety rules and the wiring. Read all of it before
buying a relay: the failure modes matter more than the feature.

---

## 1. What makes it possible

The key switch on an e-scooter does **not** carry motor current. It completes a thin
low-current line into the controller — usually labelled **KSI** (key switch input), or
"ignition", "enable" or "lock" — drawing tens of milliamps. Turning the key off drops that
line and the controller shuts down; the 60 V pack stays where it is.

So an electronic immobiliser is one relay in series with a signal wire:

```
  pack + ──▶ key switch ──▶ [ Warivo relay ] ──▶ controller KSI
                              (opens to immobilise)
```

Both must be closed for the scooter to run:

| Key | App | Scooter |
| --- | --- | --- |
| off | either | dead, as always |
| on | unlocked | rides normally |
| on | **locked** | controller disabled — throttle does nothing |

**The key stays the master switch.** The relay is added in series, never in place of it, so
nothing about the key's behaviour changes and a failed relay cannot turn the scooter *on*.

Motor current, brakes and steering are never in the circuit. The most an immobiliser
failure can do is refuse to let the scooter start — which is exactly the failure you want.

## 2. What this will not do, and why

**It will not cut power while the scooter is moving.** Losing drive at speed means no
throttle and, on many controllers, no regen braking — mid-overtake or on a hill that is how
someone comes off. The brakes are mechanical and unaffected, but "the rider still has
brakes" is not a good enough reason to disable a vehicle under them.

So the firmware **refuses to engage above walking pace** and reports the refusal. This is a
hardware-adjacent interlock in the node, not a check in the phone app: the phone is the
thing most likely to be broken, out of range or stolen.

There is deliberately **no verb anywhere in the protocol for "kill now"**. `immobilise`
means *"engage as soon as it is safe"*, and the node decides when that is.

Releasing the lock is always allowed, at any speed. The asymmetry is the whole point: it is
never unsafe to give the rider their scooter back.

## 3. Choose your failure mode before you wire it

This is the decision that matters, and it is a genuine trade — there is no option that is
both un-defeatable and incapable of stranding you.

### Option A — Normally closed, non-latching *(recommended for a personal scooter)*

The relay is closed when unpowered. The node energises the coil to immobilise.

- **Dead node, flat phone, crashed firmware, cut wire → the scooter rides.** You are never
  stranded by your own electronics.
- A thief who finds and cuts the node's power defeats it.
- The coil draws current the whole time the lock is engaged (~30–70 mA), so a scooter left
  locked for weeks is a slow drain on the pack.

Pick this unless you have a specific reason not to. Being stranded by a software bug is a
much more likely event than a thief who knows what a Warivo node is.

### Option B — Latching (bistable) relay

A pulse sets it, a pulse resets it, and it holds state with no current.

- Survives the node losing power: still locked.
- No standing drain.
- **A dead node means a locked scooter**, so a physical override is mandatory, not
  optional.

### The physical override

With Option B, and sensibly with Option A too: bring the relay's contacts out to a
connector you can reach without tools, hidden but not sealed. Unplug it, the KSI line is
bridged, the scooter rides. That is the mechanical escape hatch for a dead phone, a dead
node, or a firmware bug at the far end of a ride.

Do not solder the immobiliser in as the only path through the KSI line with no way to
bypass it. That turns a software fault into a recovery vehicle.

## 4. How it unlocks — and why that is the anti-theft

The lock state lives in the **node**, persisted to flash, and is re-applied at boot.
Power-cycling the scooter does not release it; otherwise the lock would be worth nothing.

Three ways out:

1. **The head unit, over BLE.** The PIN screen already gates the launcher, so a stolen
   scooter needs the phone *and* the PIN before it will move. No internet required — this
   works in a basement car park.
2. **The owner app**, via the fleet server, when the scooter is online. Useful for
   unlocking remotely; useless as the only path, because a thief will not leave it online.
3. **The physical override** (§3), for when the electronics are the problem.

That first path is the actual product: *the scooter does not ride unless the head unit says
so, and the head unit does not unlock without the PIN.* The key alone stops being enough.

## 5. Wiring

| Part | Notes |
| --- | --- |
| Automotive relay, 12 V or 5 V coil, **SPST-NC** (or a bistable latching relay for Option B) | Contact rating only needs to exceed the KSI current — an amp is plenty. Do not put a relay rated for signal loads in the motor path; that is not what this is. |
| Logic-level MOSFET or an ULN2003 / opto + transistor | The ESP32 cannot drive a relay coil directly. |
| **10 kΩ pull-down** on the gate/base | Not optional. ESP32 GPIOs float during boot and reset; without it the coil can energise for a few hundred milliseconds at power-on, which on Option A means the scooter refuses to start intermittently. |
| Flyback diode across the coil | A collapsing coil field will otherwise take the transistor with it. |

Relay drive on **`GPIO7`**, with `LOCK_ACTIVE_HIGH` in the firmware matching your
transistor's polarity.

> `audit.md` §5 lists GPIO0 / 7 / 21 as candidates for direct-GPIO switch inputs. If the
> relay takes 7, the switch taps use 0 and 21. Confirm every pin against the WROOM-1
> pinout before wiring — the audit says the same thing, for the same reason.

Wire the relay **after** the key switch, so the key is still the first thing in the chain.

## 6. Protocol

A new characteristic, deliberately separate from the passive settings on `fff3`: writing an
actuator is a different kind of act from changing a beep threshold, and they should not
share a code path.

### `fff4` — lock (WRITE)

```json
{ "lock": 1 }     // engage as soon as the wheel is stopped
{ "lock": 0 }     // release now
```

### Telemetry gains two fields on `fff1`

```json
{ "lock": 1, "lockq": 0 }
```

| Field | Meaning |
| --- | --- |
| `lock` | 1 = immobilised, 0 = free. The *actual* relay state, not what was asked for. |
| `lockq` | 1 = engage requested but queued, waiting for the scooter to stop. 0 = nothing pending. |

`lock` reports reality and `lockq` reports intent, because those differ for as long as the
scooter is still rolling — and a UI that says "locked" while the wheel is turning is
lying about a safety-relevant state.

### Fleet commands

`immobilise` and `release`, documented in [FLEET.md](FLEET.md). The server can queue an
immobilise; the node still decides when to act on it.

## 7. Before you rely on it

- **Test the interlock on a stand, wheel off the ground.** Spin the wheel, send
  `{"lock":1}`, confirm nothing happens until the wheel stops.
- **Test the override** with the node unplugged, before you need it in traffic.
- **Confirm the KSI line with a multimeter** — it should read pack voltage with the key on
  and near zero with it off, and carry milliamps, not amps. If the wire you found carries
  motor current, it is the wrong wire; stop.

## 8. The scooter's own alarm

The reference scooter has a **factory anti-theft alarm** — fob, siren, tilt sensor — on its
own wiring. The immobiliser is independent of it and does not interfere with it. They cover
different failures: the alarm makes noise where the scooter is, the immobiliser means
someone who ignores the noise still cannot ride away.

Two things you could add later, neither of them wired:

- **Read the alarm's trigger line** as a read-only input, exactly like the switch taps in
  [../audit.md](../audit.md). A triggered alarm could then raise a tracking alert, so you
  learn about it from anywhere rather than only within earshot.
- **Drive the siren** from a second relay on the alarm's trigger. Possible, and safe —
  noise is not motion — but the stock fob already does this, and it is one more thing in
  the loom for little gain.

The fleet `alarm` command sounds the **phone's** speaker, not the siren. See
[FLEET.md](FLEET.md).

## 9. Legal and practical

Immobilising your own vehicle is ordinary — this is what every aftermarket bike alarm
does. Two things worth knowing anyway:

- **Do not immobilise a vehicle someone else is using** without telling them. Remotely
  disabling a scooter a person is relying on to get home is not a software decision.
- If the scooter is financed, rented out or shared, check what your agreement says about
  modifying the ignition circuit. An immobiliser in the KSI line is a modification to the
  vehicle, not just an accessory bolted on.

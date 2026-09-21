# Warivo Fleet — tracking, alerts and remote control

Three pieces, one contract:

| Piece | Where | What it does |
| --- | --- | --- |
| **Head unit** | [../launcher/](../launcher/) | Uploads position + telemetry, evaluates alerts, applies remote config and commands |
| **Server** | you host it | Stores the track, holds the config, queues commands |
| **Owner app** | [../companion/](../companion/) | Shows where the scooter is and has been, sets alerts, sends commands |

---

## 1. Two constraints that shape everything below

### Warivo cannot immobilise the scooter, and should not try

The node is a **read-only** tap: it senses voltage, a wheel hall and (optionally) throttle
and switch lines. It drives nothing. So "lock the scooter" from the app means **lock the
head unit** — kiosk it to a full-screen owner message, sound the alarm, keep reporting
position. It does not and cannot cut the motor.

Adding motor cut-off would mean a relay in the scooter's power or throttle path, and
**cutting power to a moving vehicle can cause a crash**. If that is ever wanted it needs,
at minimum: a hardware interlock that only opens below walking pace, a mechanical override
the rider can always reach, and the rider being told it exists. That is a vehicle-safety
project with its own review, not a feature flag. Nothing in this document does it, and the
command vocabulary below deliberately has no verb for it.

What remote lock *does* achieve is real: a stolen scooter keeps reporting its position, and
the thief gets a screen with the owner's phone number instead of a working dashboard.

### Continuous location tracking has to be visible

A sample every 5–10 seconds is a complete record of where someone went. That is
unobjectionable when the owner and the rider are the same person — it is your scooter and
your phone. It stops being unobjectionable the moment someone else rides it.

So the head unit **shows** that uplink is on (a dot in the status bar and a row in
Settings), and the feature is **off until an endpoint is configured**. If you rent these
out or run a fleet, telling riders is your job and in most places your legal obligation;
the app is built so you can, not so you can avoid it.

---

## 2. One endpoint, both directions

```
POST  {endpoint}/v1/ingest
Authorization: Bearer {device_token}
Content-Type: application/json
```

Telemetry goes up and config and commands come down **in the same round trip**. A scooter
moves through patchy mobile coverage, so every avoidable request is a battery and
reliability cost; one call per interval is the whole protocol.

HTTPS only — the launcher ships with `usesCleartextTraffic="false"`, so an `http://`
endpoint will fail to connect rather than silently downgrade.

### Request

```json
{
  "device_id": "warivo-3f9a21",
  "sent_at": 1758150000,
  "app": "0.1",
  "config_version": 7,
  "samples": [
    { "ts": 1758149990, "lat": 26.8467, "lon": 80.9462, "acc": 8.0,
      "spd": 24.1, "soc": 63, "v": 61.4, "odo": 1043.2, "w": 811 }
  ],
  "events": [
    { "ts": 1758149991, "type": "speed", "detail": { "kmh": 67, "limit": 65 } }
  ],
  "rides": [
    { "started": 1758140000, "ended": 1758145000, "km": 12.4,
      "avg": 21.0, "max": 44.0, "wh": 310.0 }
  ],
  "acks": ["cmd-8821"]
}
```

`samples` is a **batch**, not one reading. The head unit spools while offline and sends the
backlog when coverage returns, so a ride through a dead zone is still on the map
afterwards. Servers must therefore treat `ts` as authoritative and accept out-of-order and
duplicate samples idempotently — `(device_id, ts)` is the natural key.

`config_version` is what the device currently has. The server replies with config only when
it has something newer, which keeps the steady-state response nearly empty.

### Response

```json
{
  "config_version": 8,
  "config": {
    "interval_s": 10,
    "speed_alert_kmh": 65,
    "battery_alert_pct": 20,
    "zones": [
      { "id": "home", "lat": 26.8467, "lon": 80.9462, "radius_m": 500 }
    ]
  },
  "commands": [
    { "id": "cmd-8821", "type": "lock_head_unit",
      "message": "Reported stolen. Call +91 00000 00000." }
  ]
}
```

Both keys are optional. `{}` is a valid, healthy response.

### Commands

| `type` | Effect on the head unit |
| --- | --- |
| `lock_head_unit` | Full-screen owner message, kiosk enforced, uplink continues |
| `unlock_head_unit` | Return to the dashboard |
| `alarm` | Sound the phone speaker (and the node buzzer if enabled) |
| `ping` | Upload a sample immediately |
| `apply_config` | Take the `config` in this response now rather than on next poll |

Commands are acknowledged by id in the next request, and a server should keep re-sending an
unacknowledged command — a scooter is frequently offline, and "the command was delivered"
is not knowable until the device says so.

There is intentionally **no** command that touches the scooter's motor, throttle or brakes.

---

## 3. Alerts

Evaluated **on the device**, not on the server, so they still fire with no coverage:

| Alert | Fires when | Notes |
| --- | --- | --- |
| `speed` | speed ≥ `speed_alert_kmh` | Edge-triggered with a 3 km/h hysteresis band |
| `battery` | SoC ≤ `battery_alert_pct` | Edge-triggered; rearms 5 pp above the threshold |
| `zone_exit` | outside every zone by `radius_m` | Needs a GPS fix; unknown ≠ outside |
| `zone_enter` | back inside a zone | |
| `power_on` | head unit started | Useful as a theft signal at 3am |

Hysteresis matters more than it sounds: a level-triggered speed alert hovering at 65 km/h
would emit an event every sample and turn the owner's phone into a slot machine. Every
alert here fires once on crossing and rearms only after the value comes back.

Events are spooled and uploaded like samples, so an alert raised in a dead zone still
arrives — late, with its original timestamp.

`zone_exit` deliberately does not fire without a fix. No GPS is *unknown*, not *outside*,
and treating it as outside means an alert every time the scooter parks in a basement.

---

## 4. What the device stores

Nothing is lost to a dropped connection:

- **Spool**: up to 2,000 samples and 500 events on disk, oldest dropped first. At 10 s that
  is about 5½ hours of continuous riding offline.
- **Rides**: the existing on-device log (`trip/RideHistory.kt`) is uploaded too, so
  "where has it been" survives a reinstall of the server.
- **Config**: last applied config and its version, so a device that boots offline still
  enforces the right thresholds.

## 5. Server

Not included in this repo, and deliberately not stubbed: it is a dozen lines of any web
framework, and a fake one would be worse than none. To be compatible it needs to

1. accept `POST /v1/ingest` with a bearer token per device,
2. store samples idempotently on `(device_id, ts)`,
3. return `config`/`config_version` when the owner has changed something,
4. queue commands and drop them once acknowledged.

## 6. The owner API

The head unit uses exactly one endpoint. The owner app needs a few more, and they are
specified here so the app is not guessing. Same server, different token: a **device token**
is scoped to one scooter and can only ingest; an **owner token** can read history and issue
commands. Do not reuse one as the other — a device token is sitting on a scooter that may
be stolen.

```
Authorization: Bearer {owner_token}
```

| Method | Path | Returns / takes |
| --- | --- | --- |
| `GET` | `/v1/devices` | `{"devices":[{"device_id","name","last_seen","online"}]}` |
| `GET` | `/v1/devices/{id}/latest` | the most recent sample |
| `GET` | `/v1/devices/{id}/track?from=&to=` | `{"samples":[…]}`, oldest first |
| `GET` | `/v1/devices/{id}/rides?limit=` | `{"rides":[…]}`, newest first |
| `GET` | `/v1/devices/{id}/events?limit=` | `{"events":[…]}`, newest first |
| `PUT` | `/v1/devices/{id}/config` | the `config` object; the server bumps `config_version` |
| `POST` | `/v1/devices/{id}/commands` | `{"type":"lock_head_unit","message":"…"}` |

Sample and event shapes are the ones the device sends in §2 — there is deliberately one
schema, so a sample the device wrote is the same object the app reads.

`POST /commands` returns the queued command's `id`. It is **queued, not delivered**: the
scooter may be offline for hours, and the app should show a command as pending until it
appears acknowledged. Telling an owner their scooter is locked when the command is still
sitting in a queue is the one lie this system must not tell.

See [../companion/README.md](../companion/README.md).

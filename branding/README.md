# Warivo branding & boot animation

Launch/boot visuals for **Warivo OS** (Nova‑S), shown when the car UI starts. Kept in this
folder so nothing mixes with the firmware or docs.

## Files

| File | What it is | Use |
| --- | --- | --- |
| `mockups/` | Clickable hi‑fi prototype of **every OS screen** + `index.html` (boot intro → screen map) | The **design source of truth** for the launcher UI and the boot → PIN → home flow |
| `boot-animation.html` | Self‑contained animated splash (CSS/SVG) | In‑app launch screen, kiosk splash, or the source for rendering ROM boot frames |
| `boot-animation.svg` | Same sequence as a single animated SVG | Embed in the launcher (WebView/`SVG`) or preview in a browser |
| `warivo-logo.svg` | Wordmark lockup (mark + `Warivo` signature + `NOVA-S`) | Headers, about screen |
| `warivo-mark.svg` | `W` monogram tile | App icon, small badge |
| `fonts/KaushanScript-Regular.ttf` | Signature typeface for the `Warivo` wordmark | Ships beside the HTML/SVG (referenced via `@font-face`) |
| `bootanimation/desc.txt` | Android `bootanimation.zip` descriptor (1920×1080 @ 30 fps) | Path B custom ROM boot |

> **Orientation: landscape (1920×1080).** The car UI is always landscape.

Preview: open `boot-animation.html` in a browser (append `?loop` to replay while tuning).

## Brand palette

| Token | Hex | Source |
| --- | --- | --- |
| Deep Space Blue | `#0A1B40` → `#03081C` | background gradient |
| Vintage Blush (soft pink) | `#FFC0CB` / `#FF8FA8` | accent, ring, `W` |
| Ink / Muted / Dim | `#EAF2FF` / `#8FB0D8` / `#5E7BA6` | text tiers |

Type: **Kaushan Script** for the handwritten `Warivo` signature; geometric sans (system
stack) for `NOVA-S` / `WARIVO OS`. Tagline (brand, not shown on boot): *Effortless Elegance
· Practical Luxury*.

## Sequence (~4 s, then the progress bar loops)

1. Blush orbit ring draws in around the emblem.
2. `W` monogram strokes on and **keeps a soft pulsing glow** for the whole boot.
3. `Warivo` signature **writes on** left→right (a pen dot rides the stroke).
4. `NOVA-S` fades in.
5. An **indeterminate OS progress bar** + small `WARIVO OS` run until the UI is ready —
   system-boot style (like Android/One UI/Zenfone), not a web spinner.
6. A small **`Built by Enjoys`** credit sits at the bottom.

When the boot completes it hands off to the **PIN unlock** screen (see below) — it does
**not** drop straight onto the dashboard.

## Two ways this is used

**A · In‑app / kiosk splash (Path A — now).** Point the launcher's splash at
`boot-animation.html` (WebView) or `boot-animation.svg`. No root needed.

**B · Android boot animation (Path B — custom ROM).** Android plays `bootanimation.zip`
(see `docs/PROJECT_GUIDE.md` §7.4). Render the HTML to numbered PNG frames, drop them into
`bootanimation/part0` (intro, plays once) and `bootanimation/part1` (loop), then zip **stored,
not deflated**:

```bash
# 1) render frames from the HTML (Chrome headless + a tiny capture script, or a screen
#    recording → ffmpeg). Example splitting an mp4 into 30 fps frames:
ffmpeg -i boot.mp4 -vf fps=30 bootanimation/part1/%04d.png

# 2) zip WITHOUT compression (Android requires "store")
cd bootanimation && zip -r -0 ../bootanimation.zip desc.txt part0 part1
```

Then place `bootanimation.zip` at `/system/media/` (or `/product/media/`) in the ROM.
Match `desc.txt` (`1920 1080 30`) to the target phone's landscape resolution.

## Screen set & flow (`mockups/`)

The interactive prototype in [`mockups/`](mockups/) is the **design source of truth** for
the whole car OS. Open [`mockups/index.html`](mockups/index.html): it plays the boot intro,
then reveals a **screen map** linking every screen. Each screen scales to fit the window
(`fit.js`), is landscape‑only, and its controls are clickable (`actions.js`). Palette and
geometry live in `mockups/warivo.css` and must stay in sync with
[`../launcher/app/src/main/java/com/warivo/os/ui/theme/Theme.kt`](../launcher/app/src/main/java/com/warivo/os/ui/theme/Theme.kt).

**Power‑on flow (build the OS to follow this):**

```
power on ─▶ 01 boot animation ─▶ 08 lock / PIN unlock ─▶ 00 home ─▶ (dashboard · map · music · search · settings · about)
              glowing W, progress        enter PIN (demo 1234),        widget home           reached from the bottom dock
              "Built by Enjoys"          Ola‑style, unlocks the ride
```

| # | Screen | File | Purpose |
| --- | --- | --- | --- |
| 01 | **Boot** | `01-boot.html` | Animated splash; glowing `W`; hands off to the PIN screen. |
| 08 | **Lock / PIN** | `08-lock.html` | Ola‑style unlock — 4‑digit PIN pad (demo `1234`), fingerprint shortcut; correct PIN rides into Home. |
| 00 | **Home** | `00-home.html` | Widget home — map peek + Home/Work shortcuts, drive summary (SoC ring, range, PARKED), now‑playing. |
| 02 | **Dashboard** | `02-dashboard.html` | Driver cluster — speed ring, ride‑mode (ECO/CITY/SPORT), battery + range, stat tiles, telltales. |
| 03 | **Navigation** | `03-map.html` | Map‑forward with a Google search bar, maneuver + ETA cards, speed puck. |
| 04 | **Media** | `04-music.html` | Now‑playing + transport, Bluetooth output, up‑next queue. |
| 05 | **Google search** | `05-search.html` | Google‑only search — coloured logo, pill field + voice, chips, recents. |
| 09 | **Control Center** | `09-controls.html` | Bento grid of vehicle functions — ride‑mode hero, immobiliser, alarm, underglow, find‑my‑scooter, seat/charge/horn/hazard tiles, mini player. State is the tile *fill*: dark off, white engaged, blush accent. |
| 06 | **Settings** | `06-settings.html` | Quick‑settings tiles (Wi‑Fi/BT/GPS/Kiosk), node status, brightness → **About**. |
| 07 | **About** | `07-about.html` | Device/OS/node info, version, licences. |

**Shell (every screen):** a persistent **bottom dock** — brand `W` (home) + app shortcuts
`Home · Dashboard · Nav · Media · Search · Controls · Settings` on the left, and **vehicle
quick‑controls** (headlight, horn, lock, speaker/volume) on the right — plus a floating top
status strip. Glassy cards, Vintage‑Blush accent, and the pulsing‑glow `W`.

## Font licence

`Warivo` is set in **Kaushan Script** by Pablo Impallari (SIL Open Font License 1.1). The
licence travels with the font in `fonts/OFL.txt`. Swap the `@font-face` `src` in
`boot-animation.html` / `.svg` to restyle the signature.

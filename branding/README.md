# Warivo branding & boot animation

Launch/boot visuals for **Warivo OS** (Nova‑S), shown when the car UI starts. Kept in this
folder so nothing mixes with the firmware or docs.

## Files

| File | What it is | Use |
| --- | --- | --- |
| `boot-animation.html` | Self‑contained animated splash (CSS/SVG) | In‑app launch screen, kiosk splash, or the source for rendering ROM boot frames |
| `boot-animation.svg` | Same sequence as a single animated SVG | Embed in the launcher (WebView/`SVG`) or preview in a browser |
| `warivo-logo.svg` | Wordmark lockup (mark + `WARIVO` + `NOVA-S`) | Headers, about screen |
| `warivo-mark.svg` | `W` monogram tile | App icon, small badge |
| `bootanimation/desc.txt` | Android `bootanimation.zip` descriptor (1080×1920 @ 30 fps) | Path B custom ROM boot |

Preview: open `boot-animation.html` in a browser (append `?loop` to replay while tuning).

## Brand palette

| Token | Hex | Source |
| --- | --- | --- |
| Deep Space Blue | `#0A1B40` → `#03081C` | background gradient |
| Celestial Aqua | `#37E0D0` / `#1FB6C9` | accent, ring, `W` |
| Ink / Muted / Dim | `#EAF2FF` / `#8FB0D8` / `#5E7BA6` | text tiers |

Type: geometric sans (system stack). Tagline: *Effortless Elegance · Practical Luxury*.

## Sequence (~3.7 s, then holds)

1. Aqua orbit ring draws in around the emblem.
2. `W` monogram strokes on with a soft glow.
3. `WARIVO` rises in; aqua underline sweeps.
4. `NOVA-S` + tagline fade in.
5. `WARIVO OS` loader dots pulse until the UI is ready.

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
Match `desc.txt` (`1080 1920 30`) to the target phone's resolution.

#!/usr/bin/env python3
"""Render the Warivo OS boot animation frames.

A Chrome-free stand-in for rendering branding/boot-animation.html: it draws the same
sequence (blush orbit ring → W monogram → "Warivo" signature → NOVA-S → looping progress)
straight to PNG frames with Pillow, so a bootanimation.zip can be built on a machine with
no headless browser. build-bootanimation.sh calls this, then packages the frames STORED.

Writes numbered frames into <out>/part0 (intro, plays once) and <out>/part1 (loops).
"""

import argparse
import math
import os
import sys

try:
    from PIL import Image, ImageDraw, ImageFont, ImageFilter
except ImportError:
    sys.exit("Pillow is required: `py -m pip install pillow` (or pip install pillow)")

# --- brand palette (branding/README.md) ---
BG_CENTER = (0x0A, 0x1B, 0x40)   # Deep Space Blue, centre
BG_EDGE = (0x03, 0x08, 0x1C)     # near-black, edge
BLUSH = (0xFF, 0xC0, 0xCB)       # Vintage Blush
BLUSH_DEEP = (0xFF, 0x8F, 0xA8)
INK = (0xEA, 0xF2, 0xFF)
MUTED = (0x8F, 0xB0, 0xD8)
DIM = (0x5E, 0x7B, 0xA6)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ease(t):
    """Smoothstep, clamped to 0..1."""
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def find_font(size, kaushan_path, sans=False):
    if not sans and kaushan_path and os.path.isfile(kaushan_path):
        return ImageFont.truetype(kaushan_path, size)
    for p in (
        "C:/Windows/Fonts/segoeui.ttf",
        "C:/Windows/Fonts/arial.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
    ):
        if os.path.isfile(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def make_background(w, h):
    """Radial deep-space gradient, rendered small then scaled for a smooth falloff."""
    sw, sh = w // 4, h // 4
    lo = Image.new("RGB", (sw, sh))
    px = lo.load()
    cx, cy = sw / 2.0, sh * 0.44
    maxd = math.hypot(max(cx, sw - cx), max(cy, sh - cy))
    for y in range(sh):
        for x in range(sw):
            d = min(1.0, math.hypot(x - cx, y - cy) / maxd)
            px[x, y] = lerp(BG_CENTER, BG_EDGE, d ** 1.15)
    return lo.resize((w, h), Image.BICUBIC)


def tracked_text(draw, cx, y, text, font, fill, tracking, anchor_top=True):
    """Draw letter-spaced, horizontally centred text."""
    widths = [draw.textlength(ch, font=font) for ch in text]
    total = sum(widths) + tracking * (len(text) - 1)
    x = cx - total / 2.0
    for ch, wch in zip(text, widths):
        draw.text((x, y), ch, font=font, fill=fill, anchor="la" if anchor_top else "la")
        x += wch + tracking


def emblem_layer(size, ring_frac, w_alpha, font_w):
    """A transparent SxS layer with the orbit ring and the W monogram."""
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    pad = int(size * 0.14)
    box = (pad, pad, size - pad, size - pad)
    stroke = max(3, size // 44)
    if ring_frac >= 0.999:
        d.ellipse(box, outline=BLUSH + (255,), width=stroke)
    elif ring_frac > 0:
        start = -90.0
        end = start + 360.0 * ring_frac
        d.arc(box, start=start, end=end, fill=BLUSH + (255,), width=stroke)
    if w_alpha > 0:
        a = int(255 * max(0.0, min(1.0, w_alpha)))
        d.text((size / 2, size * 0.52), "W", font=font_w, fill=BLUSH + (a,), anchor="mm")
    return layer


def progress_layer(w, track_w, y, p, pulse):
    """Indeterminate progress: a bright segment sweeping across a dim track."""
    h = 8
    layer = Image.new("RGBA", (w, 40), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    x0 = (w - track_w) / 2
    d.rounded_rectangle((x0, 20 - h / 2, x0 + track_w, 20 + h / 2),
                        radius=h / 2, fill=DIM + (90,))
    seg = track_w * 0.34
    # p in [0,1): segment centre travels a bit beyond both ends so it slides off-screen.
    centre = x0 - seg / 2 + (track_w + seg) * p
    a = int(210 + 45 * pulse)
    d.rounded_rectangle((centre - seg / 2, 20 - h / 2, centre + seg / 2, 20 + h / 2),
                        radius=h / 2, fill=BLUSH + (a,))
    return layer, y


def compose(bg, size, ring_frac, w_alpha, word_alpha, nova_alpha, prog_p,
            pulse, fonts):
    w, h = bg.size
    frame = bg.copy()
    em_size = int(h * 0.30)
    em = emblem_layer(em_size, ring_frac, w_alpha, fonts["w"])
    # Soft blush glow: a blurred copy of the emblem behind the crisp one.
    glow = em.filter(ImageFilter.GaussianBlur(em_size // 18))
    glow = Image.eval(glow, lambda v: v)
    ex = (w - em_size) // 2
    ey = int(h * 0.16)
    gl = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    gl.paste(glow, (ex, ey), glow)
    frame = Image.alpha_composite(frame.convert("RGBA"), gl)
    frame = Image.alpha_composite(frame, _placed(em, frame.size, ex, ey))

    txt = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(txt)
    cx = w / 2
    if word_alpha > 0:
        a = int(255 * ease(word_alpha))
        d.text((cx, ey + em_size + h * 0.02), "Warivo", font=fonts["word"],
               fill=INK + (a,), anchor="ma")
    if nova_alpha > 0:
        a = int(255 * ease(nova_alpha))
        tracked_text(d, cx, ey + em_size + h * 0.185, "NOVA-S", fonts["nova"],
                     MUTED + (a,), tracking=10)
    # Bottom furniture: label, progress, credit.
    tracked_text(d, cx, h * 0.845, "WARIVO OS", fonts["label"], DIM + (255,), tracking=6)
    tracked_text(d, cx, h * 0.955, "BUILT BY ENJOYS", fonts["credit"], DIM + (200,),
                 tracking=5)
    frame = Image.alpha_composite(frame, txt)

    prog, py = progress_layer(w, int(w * 0.28), int(h * 0.885), prog_p, pulse)
    frame = Image.alpha_composite(frame, _placed(prog, frame.size, 0, py))
    return frame.convert("RGB")


def _placed(layer, size, x, y):
    canvas = Image.new("RGBA", size, (0, 0, 0, 0))
    canvas.paste(layer, (x, y), layer)
    return canvas


def clear_part(path):
    os.makedirs(path, exist_ok=True)
    for f in os.listdir(path):
        if f.lower().endswith(".png"):
            os.remove(os.path.join(path, f))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", required=True, help="repo root")
    ap.add_argument("--out", required=True, help="branding/bootanimation dir")
    ap.add_argument("--width", type=int, default=1920)
    ap.add_argument("--height", type=int, default=1080)
    ap.add_argument("--fps", type=int, default=30)
    a = ap.parse_args()

    w, h, fps = a.width, a.height, a.fps
    kaushan = os.path.join(a.root, "branding", "fonts", "KaushanScript-Regular.ttf")
    fonts = {
        "w": find_font(int(h * 0.16), kaushan),
        "word": find_font(int(h * 0.11), kaushan),
        "nova": find_font(int(h * 0.028), kaushan, sans=True),
        "label": find_font(int(h * 0.019), kaushan, sans=True),
        "credit": find_font(int(h * 0.015), kaushan, sans=True),
    }

    print(f"rendering {w}x{h} @ {fps}fps  (font: "
          f"{'Kaushan' if os.path.isfile(kaushan) else 'system sans'})")
    bg = make_background(w, h)

    n0 = round(fps * 1.7)   # intro, plays once
    n1 = round(fps * 1.4)   # loop
    p0 = os.path.join(a.out, "part0")
    p1 = os.path.join(a.out, "part1")
    clear_part(p0)
    clear_part(p1)

    em_size = int(h * 0.30)

    # part0 — the reveal.
    for i in range(n0):
        t = i / (n0 - 1)
        ring = ease(t / 0.55)
        w_a = ease((t - 0.30) / 0.35)
        word = ease((t - 0.45) / 0.35)
        nova = ease((t - 0.65) / 0.30)
        pulse = 0.5 + 0.5 * math.sin(t * math.pi * 2)
        prog = (t * 0.5) % 1.0
        frame = compose(bg, em_size, ring, w_a, word, nova, prog, pulse, fonts)
        frame.save(os.path.join(p0, f"{i + 1:04d}.png"))
        sys.stdout.write(f"\r part0 {i + 1}/{n0}")
        sys.stdout.flush()
    print()

    # part1 — the loop: everything shown, progress sweeps, W pulses.
    for i in range(n1):
        t = i / n1
        pulse = 0.5 + 0.5 * math.sin(t * math.pi * 2)
        prog = t
        frame = compose(bg, em_size, 1.0, 0.85 + 0.15 * pulse, 1.0, 1.0, prog, pulse, fonts)
        frame.save(os.path.join(p1, f"{i + 1:04d}.png"))
        sys.stdout.write(f"\r part1 {i + 1}/{n1}")
        sys.stdout.flush()
    print()
    print(f"done: {n0} intro + {n1} loop frames")


if __name__ == "__main__":
    main()

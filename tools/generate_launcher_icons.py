#!/usr/bin/env python3
"""Generate legacy (pre-API-26) launcher PNGs that match the adaptive vector icon.

Adaptive icons (mipmap-anydpi-v26) cover API 26+; API 24-25 needs rasters, and
this script keeps them pixel-consistent with `ic_launcher_foreground.xml`.

Rendering notes:
  * background is a true per-pixel diagonal linear gradient (no seams/banding)
  * rings are filled annulus sectors + round caps rather than stroked polylines,
    so the edges stay clean; everything is supersampled then LANCZOS-downscaled.

Usage:  python3 tools/generate_launcher_icons.py
Requires: pillow
"""
from PIL import Image, ImageDraw
import math
import os

SS = 6  # supersample factor
RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "app/src/main/res")


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(len(a)))


def gradient_image(size, stops):
    """Per-pixel diagonal (top-left -> bottom-right) linear gradient."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    ramp_n = size * 2 - 1
    ramp = []
    for i in range(ramp_n):
        t = i / (ramp_n - 1)
        colour = stops[-1][1]
        for j in range(len(stops) - 1):
            (p0, c0), (p1, c1) = stops[j], stops[j + 1]
            if p0 <= t <= p1:
                colour = lerp(c0, c1, (t - p0) / (p1 - p0) if p1 > p0 else 0.0)
                break
        ramp.append(colour)
    for y in range(size):
        for x in range(size):
            px[x, y] = ramp[x + y]
    return img


def annulus_sector(draw, cx, cy, radius, width, start_deg, end_deg, colour, steps=720):
    """Filled annulus sector with round caps."""
    r_out, r_in = radius + width / 2.0, radius - width / 2.0
    pts_out, pts_in = [], []
    for i in range(steps + 1):
        a = math.radians(start_deg + (end_deg - start_deg) * i / steps)
        ca, sa = math.cos(a), math.sin(a)
        pts_out.append((cx + r_out * ca, cy + r_out * sa))
        pts_in.append((cx + r_in * ca, cy + r_in * sa))
    draw.polygon(pts_out + pts_in[::-1], fill=colour)
    for a_deg in (start_deg, end_deg):
        a = math.radians(a_deg)
        x, y = cx + radius * math.cos(a), cy + radius * math.sin(a)
        draw.ellipse([x - width / 2, y - width / 2, x + width / 2, y + width / 2], fill=colour)


def draw_icon(size, round_icon=False):
    s = size * SS
    u = s / 108.0          # the vector viewport is 108 units
    cx = cy = 54 * u
    radius = 27 * u
    stroke = 7 * u

    background = gradient_image(
        s, [(0.0, (0x15, 0x24, 0x3B)), (0.55, (0x0E, 0x1A, 0x2B)), (1.0, (0x0A, 0x14, 0x22))]
    ).convert("RGBA")

    mask = Image.new("L", (s, s), 0)
    mask_draw = ImageDraw.Draw(mask)
    if round_icon:
        mask_draw.ellipse([0, 0, s - 1, s - 1], fill=255)
    else:
        mask_draw.rounded_rectangle([0, 0, s - 1, s - 1], radius=int(s * 0.22), fill=255)

    img = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    img.paste(background, (0, 0), mask)

    # NOTE: ImageDraw writes pixels directly (it does not alpha-blend), so every
    # translucent element is drawn on its own layer and alpha-composited. Drawing
    # them straight onto `img` would punch transparent holes through the icon.
    def overlay(paint):
        layer = Image.new("RGBA", (s, s), (0, 0, 0, 0))
        paint(ImageDraw.Draw(layer))
        return Image.alpha_composite(img, layer)

    # Track ring.
    img = overlay(lambda d: annulus_sector(d, cx, cy, radius, stroke, 0, 359.999, (255, 255, 255, 46)))

    # Progress ring (255 degrees) painted through an emerald gradient.
    ring_mask = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    annulus_sector(ImageDraw.Draw(ring_mask), cx, cy, radius, stroke, -90, 165, (255, 255, 255, 255))
    emerald = gradient_image(s, [(0.0, (0x6E, 0xE7, 0xB7)), (1.0, (0x10, 0xB9, 0x81))]).convert("RGBA")
    emerald.putalpha(ring_mask.split()[3])
    img = Image.alpha_composite(img, emerald)

    # Ledger lines.
    def bars(d):
        def bar(x0, y0, x1, height, colour):
            y, h = y0 * u, height * u
            d.rounded_rectangle([x0 * u, y, x1 * u, y + h], radius=h / 2, fill=colour)

        bar(42.25, 42.0, 67.75, 4.5, (255, 255, 255, 255))
        bar(42.25, 51.75, 63.75, 4.5, (255, 255, 255, 230))
        bar(42.25, 61.5, 57.75, 4.5, (0x6E, 0xE7, 0xB7, 255))

    img = overlay(bars)

    return img.resize((size, size), Image.LANCZOS)


def main():
    for density, px in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        out_dir = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(out_dir, exist_ok=True)
        draw_icon(px, False).save(os.path.join(out_dir, "ic_launcher.png"))
        draw_icon(px, True).save(os.path.join(out_dir, "ic_launcher_round.png"))
        print(f"wrote mipmap-{density} ({px}px)")
    draw_icon(512, False).save("/tmp/ic_launcher_512.png")
    print("wrote /tmp/ic_launcher_512.png (store listing preview)")


if __name__ == "__main__":
    main()

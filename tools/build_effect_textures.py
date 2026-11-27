#!/usr/bin/env python3
"""
Procedural pixel-sharp + soft-feathered VFX texture generator for YumisFight.

Produces white-tinted alpha sprites under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/

All sprites use pure white/bright luminance with rich alpha so runtime vertex
colors can tint them into any career or elemental palette under additive or
translucent pipelines.
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect"


def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else (hi if v > hi else v)


def smoothstep(e0: float, e1: float, x: float) -> float:
    if e1 == e0:
        return 1.0 if x >= e1 else 0.0
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3.0 - 2.0 * t)


def hash2(x: int, y: int, seed: int = 0) -> float:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = n ^ (n >> 16)
    return (n & 0xFFFF) / 65535.0


def pixel_blend(core: float, glow: float, pixel_grid: int = 2) -> tuple[int, int, int, int]:
    """Mix sharp pixel core and soft feathered glow into RGBA."""
    alpha = clamp(core + glow * (1.0 - core * 0.55))
    if alpha <= 0.004:
        return (255, 255, 255, 0)
    # Keep high-energy core pure white, slight cool/soft luminance ramp at edges
    lum = int(round(clamp(0.80 + 0.20 * core) * 255))
    return (lum, lum, 255 if lum > 220 else lum, int(round(alpha * 255)))


def quantize_coord(v: float, steps: int) -> float:
    return (math.floor(v * steps) + 0.5) / steps


def make_slash_arc() -> Image.Image:
    """512x64 (8 frames of 64x64) sweeping crescent blade slash."""
    frames = 8
    size = 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Head leads across the arc; tail catches up and dissipates
        head_angle = -2.25 + 4.25 * smoothstep(0.0, 0.72, t)
        tail_angle = -2.45 + 4.35 * smoothstep(0.18, 1.0, t)
        envelope = math.sin(clamp(t, 0.0, 1.0) * math.pi) ** 0.55
        if f == 0:
            envelope = 0.55
        elif f == frames - 1:
            envelope = 0.28

        for y in range(size):
            for x in range(size):
                # Snap to 2px pixel-art cells for crisp core, raw coord for glow
                qx = (quantize_coord(x / size, 32) - 0.5) * 2.0
                qy = (quantize_coord(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                def eval_slash(ux: float, uy: float, sharp: bool) -> float:
                    r = math.hypot(ux, uy)
                    ang = math.atan2(uy, ux)
                    span = max(0.25, head_angle - tail_angle)
                    arc_u = (ang - tail_angle) / span
                    if sharp:
                        if arc_u <= 0.0 or arc_u >= 1.0:
                            return 0.0
                        end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.88, arc_u)
                        thickness = (0.05 + 0.22 * (arc_u ** 0.72) * (1.0 - 0.32 * arc_u)) * (0.65 + 0.35 * envelope)
                        center_r = 0.68 - 0.08 * (1.0 - arc_u) + 0.04 * t
                        dist = abs(r - center_r)
                        edge = (1.0 - smoothstep(thickness * 0.32, thickness * 0.92, dist)) * end_fade
                        noise = hash2(int((ux + 1) * 16), int((uy + 1) * 16), f * 17 + 3)
                        if arc_u < 0.45 and noise > 0.35 + arc_u * 1.4:
                            edge *= 0.35
                        crest = math.exp(-((r - (center_r + thickness * 0.28)) / 0.055) ** 2) * (arc_u ** 1.35) * end_fade
                        return clamp((edge * 0.88 + crest * 0.65) * envelope)
                    else:
                        # Smooth angular distance so the glow feathers cleanly past arc endpoints
                        clamped_u = clamp(arc_u, 0.0, 1.0)
                        ang_dist = 0.0 if 0.0 <= arc_u <= 1.0 else (tail_angle - ang if arc_u < 0.0 else ang - head_angle)
                        thickness = (0.05 + 0.22 * (clamped_u ** 0.72) * (1.0 - 0.32 * clamped_u)) * (0.65 + 0.35 * envelope)
                        center_r = 0.68 - 0.08 * (1.0 - clamped_u) + 0.04 * t
                        radial_d = abs(r - center_r)
                        glow_w = thickness * 1.65 + 0.07
                        ang_fade = math.exp(-(ang_dist / 0.22) ** 2) * smoothstep(0.0, 0.12, clamped_u + 0.04)
                        g = math.exp(-(radial_d / max(0.03, glow_w)) ** 2) * (0.25 + 0.75 * (clamped_u ** 0.6)) * ang_fade
                        return clamp(g * 0.62 * envelope * smoothstep(1.0, 0.88, r))

                core = eval_slash(qx, qy, True)
                glow = eval_slash(rx, ry, False)

                # Pixel sparks trailing outside the arc in mid-to-late frames
                if 2 <= f <= 6:
                    r_raw = math.hypot(qx, qy)
                    ang_raw = math.atan2(qy, qx)
                    if 0.45 < r_raw < 0.94 and tail_angle - 0.35 < ang_raw < head_angle + 0.1:
                        sp = hash2(x // 2, y // 2, f * 31 + 7)
                        if sp > 0.972:
                            core = max(core, 0.85 * envelope)

                px[f * size + x, y] = pixel_blend(core, glow)

    return img


def make_impact_ring() -> Image.Image:
    """512x64 (8 frames of 64x64) pixel-burst radial explosion / shock ring."""
    frames = 8
    size = 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        radius = 0.16 + 0.72 * (1.0 - (1.0 - t) ** 2.1)
        width = 0.24 * (1.0 - 0.62 * t)
        fade = (1.0 - 0.72 * smoothstep(0.50, 1.0, t)) * (0.78 + 0.22 * smoothstep(0.0, 0.15, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize_coord(x / size, 32) - 0.5) * 2.0
                qy = (quantize_coord(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                qa = math.atan2(qy, qx)
                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)

                # Starburst spikes on early frames
                spike = 0.075 * math.cos(qa * 8.0 + f * 0.35) + 0.04 * math.cos(qa * 16.0 - f * 0.5)
                ring_d = abs(qr - (radius + spike * (1.0 - 0.5 * t)))
                core = (1.0 - smoothstep(width * 0.25, width * 0.9, ring_d)) * fade

                # Bright flash core on frames 0..2
                if f <= 2:
                    center_flash = (1.0 - smoothstep(0.05, 0.42 - f * 0.1, qr)) * (1.0 - f * 0.3)
                    core = max(core, center_flash)

                # Break up ring into crisp pixel embers in late frames
                if f >= 4:
                    n = hash2(x // 2, y // 2, f * 19 + 11)
                    if n < (t - 0.45) * 1.15:
                        core *= 0.25

                glow_d = abs(rr - radius)
                glow = math.exp(-(glow_d / (width * 1.6 + 0.08)) ** 2) * 0.62 * fade
                if f <= 2:
                    glow = max(glow, math.exp(-(rr / 0.38) ** 2) * 0.7 * (1.0 - f * 0.28))

                # Radial streaks
                ray = (max(0.0, math.cos(ra * 8.0)) ** 6) * smoothstep(0.95, 0.15, rr) * (1.0 - t) * 0.5
                px[f * size + x, y] = pixel_blend(clamp(core), clamp(glow + ray))

    return img


def seg_dist(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> float:
    dx, dy = bx - ax, by - ay
    denom = dx * dx + dy * dy
    if denom < 1e-8:
        return math.hypot(px - ax, py - ay)
    t = clamp(((px - ax) * dx + (py - ay) * dy) / denom)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t))


def make_magic_sigil() -> Image.Image:
    """128x128 crisp pixel-fantasy arcane circle with soft outer halo."""
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # Precompute 8-point double square (octagram) segments
    oct_pts = [(math.cos(i * math.pi / 4.0) * 0.72, math.sin(i * math.pi / 4.0) * 0.72) for i in range(8)]
    segments = []
    for i in range(4):
        segments.append((oct_pts[i * 2], oct_pts[((i + 1) % 4) * 2]))
        segments.append((oct_pts[i * 2 + 1], oct_pts[((i + 1) % 4) * 2 + 1]))

    for y in range(size):
        for x in range(size):
            # 64-grid pixel crispness
            qx = (quantize_coord(x / size, 64) - 0.5) * 2.0
            qy = (quantize_coord(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            core = 0.0
            # Outer double ring + inner ring
            for rad, thick in ((0.90, 0.026), (0.76, 0.022), (0.44, 0.020), (0.16, 0.018)):
                core = max(core, 1.0 - smoothstep(thick * 0.4, thick * 1.15, abs(qr - rad)))

            # Octagram geometry lines
            d_star = min(seg_dist(qx, qy, a[0], a[1], b[0], b[1]) for a, b in segments)
            core = max(core, (1.0 - smoothstep(0.010, 0.026, d_star)) * 0.92)

            # Rune ticks in outer ring band (0.78..0.88)
            if 0.785 < qr < 0.875:
                sector = ((qa + math.pi) / (2.0 * math.pi)) * 24.0
                frac = sector - math.floor(sector)
                idx = int(math.floor(sector))
                if 0.25 < frac < 0.75:
                    bit = hash2(idx, int((qr - 0.78) * 40), 91)
                    if bit > 0.32:
                        core = max(core, 0.82)

            # Cardinal diamonds at radius 0.90
            for i in range(8):
                cx = math.cos(i * math.pi / 4.0) * 0.90
                cy = math.sin(i * math.pi / 4.0) * 0.90
                d_dia = abs(qx - cx) + abs(qy - cy)
                core = max(core, (1.0 - smoothstep(0.045, 0.070, d_dia)) * 0.95)

            # Soft ambient glow + feathered inner fill
            glow = (
                math.exp(-((rr - 0.90) / 0.075) ** 2) * 0.42
                + math.exp(-((rr - 0.76) / 0.065) ** 2) * 0.30
                + math.exp(-((rr - 0.44) / 0.07) ** 2) * 0.25
                + math.exp(-(rr / 0.34) ** 2) * 0.28
            )
            if rr < 0.90:
                glow += 0.09 * smoothstep(0.90, 0.2, rr)
            glow *= smoothstep(1.0, 0.92, rr)

            px[x, y] = pixel_blend(clamp(core), clamp(glow))

    return img


def make_warning_sigil() -> Image.Image:
    """128x128 clean tactical telegraph ring for enemy/boss AoE indicators."""
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize_coord(x / size, 64) - 0.5) * 2.0
            qy = (quantize_coord(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            core = 0.0
            # Crisp boundary ring
            core = max(core, 1.0 - smoothstep(0.015, 0.036, abs(qr - 0.93)))
            # Dashed inner caution ring
            ang_slot = ((qa + math.pi) / (2.0 * math.pi)) * 16.0
            if (ang_slot - math.floor(ang_slot)) < 0.55:
                core = max(core, (1.0 - smoothstep(0.010, 0.025, abs(qr - 0.80))) * 0.78)
            # Four directional inward chevrons/ticks
            for i in range(4):
                ang = i * math.pi * 0.5
                ax, ay = math.cos(ang) * 0.62, math.sin(ang) * 0.62
                bx, by = math.cos(ang) * 0.90, math.sin(ang) * 0.90
                core = max(core, (1.0 - smoothstep(0.012, 0.028, seg_dist(qx, qy, ax, ay, bx, by))) * 0.85)
            # Subtle center crosshair
            if qr < 0.16:
                cross = min(abs(qx), abs(qy))
                core = max(core, (1.0 - smoothstep(0.012, 0.026, cross)) * 0.65)

            # Soft danger fill inside radius 0.93
            fill = 0.24 * smoothstep(0.95, 0.82, rr) * (0.55 + 0.45 * smoothstep(0.25, 0.92, rr))
            halo = math.exp(-((rr - 0.93) / 0.065) ** 2) * 0.45 * smoothstep(1.0, 0.94, rr)
            px[x, y] = pixel_blend(clamp(core), clamp(fill + halo))

    return img


def make_beam_sheet() -> Image.Image:
    """
    256x64 sprite sheet:
      Top half (y 0..31): 8 frames of 32x32 starburst/impact flare sprites
      Bottom half (y 32..63): 2 seamless horizontal beam strips (each 256x16)
        Strip 0 (y 32..47): crisp energy beam with pixel core & pulsing waves
        Strip 1 (y 48..63): arcane/electric helical beam texture
    """
    w, h = 256, 64
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    # Top half: 8 frames of 32x32 flares
    for f in range(8):
        phase = f / 8.0 * math.pi * 2.0
        pulse = 0.82 + 0.18 * math.sin(phase)
        for y in range(32):
            for x in range(32):
                qx = (quantize_coord(x / 32.0, 16) - 0.5) * 2.0
                qy = (quantize_coord(y / 32.0, 16) - 0.5) * 2.0
                rx = (x + 0.5) / 32.0 * 2.0 - 1.0
                ry = (y + 0.5) / 32.0 * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                qa = math.atan2(qy, qx)
                rr = math.hypot(rx, ry)

                # Rotating 4-point star + core orb
                rot_a = qa + phase * 0.25
                star = (max(0.0, abs(math.cos(rot_a * 2.0))) ** 6) * smoothstep(0.95 * pulse, 0.1, qr)
                orb = 1.0 - smoothstep(0.18 * pulse, 0.42 * pulse, qr)
                core = clamp(max(orb, star * 0.92))
                glow = math.exp(-(rr / (0.48 * pulse)) ** 2) * 0.72 * smoothstep(1.0, 0.8, rr)
                px[f * 32 + x, y] = pixel_blend(core, glow)

    # Bottom strip 0 (y 32..47): pulsing energy beam
    for y in range(16):
        vy = (y + 0.5) / 16.0 * 2.0 - 1.0  # -1..1 across beam thickness
        qvy = (quantize_coord(y / 16.0, 8) - 0.5) * 2.0
        for x in range(w):
            u = x / w * math.pi * 2.0
            wave = 0.10 * math.sin(u * 4.0) + 0.06 * math.cos(u * 9.0)
            core = 1.0 - smoothstep(0.18, 0.42, abs(qvy + wave * 0.4))
            # Energy streaks along length
            streak = 0.28 * (0.5 + 0.5 * math.sin(u * 6.0 + qvy * 5.0)) * (1.0 - smoothstep(0.35, 0.78, abs(qvy)))
            glow = math.exp(-(abs(vy) / 0.52) ** 2) * 0.78 * smoothstep(1.0, 0.72, abs(vy))
            px[x, 32 + y] = pixel_blend(clamp(core + streak), clamp(glow))

    # Bottom strip 1 (y 48..63): twin-helix / crackle beam
    for y in range(16):
        vy = (y + 0.5) / 16.0 * 2.0 - 1.0
        qvy = (quantize_coord(y / 16.0, 8) - 0.5) * 2.0
        for x in range(w):
            u = x / w * math.pi * 2.0
            helix1 = 0.48 * math.sin(u * 3.0)
            helix2 = -0.48 * math.sin(u * 3.0)
            c_main = 1.0 - smoothstep(0.10, 0.26, abs(qvy))
            c_h1 = (1.0 - smoothstep(0.08, 0.24, abs(qvy - helix1))) * 0.85
            c_h2 = (1.0 - smoothstep(0.08, 0.24, abs(qvy - helix2))) * 0.85
            core = max(c_main, c_h1, c_h2)
            glow = math.exp(-(abs(vy) / 0.56) ** 2) * 0.72 * smoothstep(1.0, 0.76, abs(vy))
            px[x, 48 + y] = pixel_blend(clamp(core), clamp(glow))

    return img


def make_ribbon_trail() -> Image.Image:
    """128x32 tapered glowing ribbon texture (u=0 head, u=1 dissipated tail)."""
    w, h = 128, 32
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    for y in range(h):
        vy = (y + 0.5) / h * 2.0 - 1.0
        qvy = (quantize_coord(y / h, 16) - 0.5) * 2.0
        for x in range(w):
            u = x / (w - 1)  # 0 at head, 1 at tail
            head_ramp = smoothstep(0.0, 0.05, u)
            tail_fade = (1.0 - u) ** 1.35
            env = head_ramp * tail_fade

            width = 0.36 * (1.0 - 0.55 * u) + 0.06
            core = (1.0 - smoothstep(width * 0.28, width, abs(qvy))) * env
            # Pixelated wisps along tail
            if u > 0.35 and abs(qvy) > width * 0.4:
                n = hash2(x // 2, y // 2, 53)
                if n > 0.75:
                    core = max(core, 0.45 * env * (1.0 - abs(qvy)))
            glow = math.exp(-(abs(vy) / (width * 1.9 + 0.14)) ** 2) * 0.72 * env * smoothstep(1.0, 0.78, abs(vy))
            px[x, y] = pixel_blend(clamp(core), clamp(glow))

    return img


def make_radial_glow() -> Image.Image:
    """64x64 pixel star core + soft radial bloom."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize_coord(x / size, 32) - 0.5) * 2.0
            qy = (quantize_coord(y / size, 32) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            rr = math.hypot(rx, ry)

            # Crisp diamond-star core
            diamond = abs(qx) + abs(qy)
            core = max(
                1.0 - smoothstep(0.14, 0.34, qr),
                (1.0 - smoothstep(0.18, 0.46, diamond)) * 0.75,
            )
            glow = math.exp(-(rr / 0.46) ** 2) * 0.85 * smoothstep(1.0, 0.78, rr)
            px[x, y] = pixel_blend(clamp(core), clamp(glow))

    return img


def make_shockwave_ring() -> Image.Image:
    """128x128 crisp expanding shockwave ring with pixel wind trails and soft glow."""
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize_coord(x / size, 64) - 0.5) * 2.0
            qy = (quantize_coord(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            ripple = 0.025 * math.sin(qa * 12.0) + 0.015 * math.cos(qa * 20.0)
            # Crisp outer crest at r=0.84, fading trail inward to 0.48
            crest = 1.0 - smoothstep(0.02, 0.065, abs(qr - (0.84 + ripple)))
            inward = smoothstep(0.46, 0.84, qr) * smoothstep(0.90, 0.83, qr) * 0.55
            # Pixel streaks in inward wake
            wake_n = hash2(int((qa + math.pi) * 14), int(qr * 24), 109)
            inward *= 0.55 + 0.45 * wake_n

            core = clamp(max(crest, inward * 0.72))
            glow = (
                math.exp(-((rr - 0.84) / 0.095) ** 2) * 0.65
                + smoothstep(0.40, 0.84, rr) * smoothstep(0.96, 0.84, rr) * 0.25
            ) * smoothstep(1.0, 0.90, rr)
            px[x, y] = pixel_blend(core, clamp(glow))

    return img


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "slash_arc.png": make_slash_arc(),
        "impact_ring.png": make_impact_ring(),
        "magic_sigil.png": make_magic_sigil(),
        "warning_sigil.png": make_warning_sigil(),
        "beam_sheet.png": make_beam_sheet(),
        "ribbon_trail.png": make_ribbon_trail(),
        "radial_glow.png": make_radial_glow(),
        "shockwave_ring.png": make_shockwave_ring(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")


if __name__ == "__main__":
    main()

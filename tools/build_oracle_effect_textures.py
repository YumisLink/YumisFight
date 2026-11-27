#!/usr/bin/env python3
"""
Dedicated Oracle Executor (神谕代行者 / 代行者) dual-state pixel-sharp + soft-feathered
VFX texture generator.

Outputs white/luminance-ramped RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/oracle/

Designed for the approved dual-state aesthetic:
  - Normal casts ("Black Silence" monochrome ink):
      Blend.TRANSLUCENT tinted pitch-black/charcoal (#0E1016) for bold calligraphic ink weight
      + Blend.ADDITIVE tinted cold silver-white (#E4ECF6) for razor-sharp cut edges!
  - Instruction-triggered casts & Furioso-Replica Awakening ("Oracle Sacred Gold"):
      Blend.TRANSLUCENT tinted deep obsidian-bronze (#261906) for dramatic contrast
      + Blend.ADDITIVE tinted radiant Oracle Gold (#F5CE67) & ivory-white (#FFF8DC) core!
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/oracle"
PREVIEW_DIR = ROOT / "temp/vfx-preview"


# --- Math & Noise Helpers ----------------------------------------------------

def clamp(v: float, lo: float = 0.0, hi: float = 1.0) -> float:
    return lo if v < lo else (hi if v > hi else v)


def lerp(a: float, b: float, t: float) -> float:
    return a + (b - a) * t


def smoothstep(e0: float, e1: float, x: float) -> float:
    if e1 == e0:
        return 1.0 if x >= e1 else 0.0
    t = clamp((x - e0) / (e1 - e0))
    return t * t * (3.0 - 2.0 * t)


def ease_out_cubic(t: float) -> float:
    u = 1.0 - clamp(t)
    return 1.0 - u * u * u


def hash2(x: int, y: int, seed: int = 0) -> float:
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = n ^ (n >> 16)
    return (n & 0xFFFF) / 65535.0


def value_noise(x: float, y: float, seed: int = 0) -> float:
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    ux = fx * fx * (3.0 - 2.0 * fx)
    uy = fy * fy * (3.0 - 2.0 * fy)
    a = hash2(ix, iy, seed)
    b = hash2(ix + 1, iy, seed)
    c = hash2(ix, iy + 1, seed)
    d = hash2(ix + 1, iy + 1, seed)
    return lerp(lerp(a, b, ux), lerp(c, d, ux), uy)


def fbm(x: float, y: float, seed: int = 0, octaves: int = 3) -> float:
    val = 0.0
    amp = 0.55
    freq = 1.0
    norm = 0.0
    for i in range(octaves):
        val += amp * value_noise(x * freq, y * freq, seed + i * 37)
        norm += amp
        amp *= 0.5
        freq *= 2.05
    return val / max(1e-6, norm)


def quantize(v: float, steps: int) -> float:
    return (math.floor(v * steps) + 0.5) / steps


def seg_dist(px: float, py: float, ax: float, ay: float, bx: float, by: float) -> tuple[float, float]:
    dx, dy = bx - ax, by - ay
    denom = dx * dx + dy * dy
    if denom < 1e-8:
        return math.hypot(px - ax, py - ay), 0.0
    t = clamp(((px - ax) * dx + (py - ay) * dy) / denom)
    return math.hypot(px - (ax + dx * t), py - (ay + dy * t)), t


def pack_dual_pixel(heat: float, core_a: float, glow_heat: float = 0.58,
                    glow_a: float = 0.0) -> tuple[int, int, int, int]:
    """
    Neutral high-contrast luminance-ramped pixel pack (pure neutral grayscale -> white)
    so runtime vertex colors tint accurately into BOTH monochrome ink-black/silver AND radiant Oracle gold!
    """
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (255, 255, 255, 0)

    c_lum = lerp(112.0, 255.0, clamp(heat) ** 0.85)
    g_lum = lerp(112.0, 255.0, clamp(glow_heat) ** 0.85)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    v = int(round(clamp(c_lum * wc + g_lum * wg, 0, 255)))
    return (v, v, v, int(round(clamp(out_a) * 255)))


# --- 1. ink_slash.png (1024x128, 8 frames of 128x128) ------------------------

def make_ink_slash() -> Image.Image:
    """
    Oracle signature sweeping calligraphic blade stroke (沉重横批 / Furioso-Replica sweeps):
    Wide bold dry-brush arc with crisp razor front edge, splintered ink-bristle trails & flecks.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        head_ang = -2.35 + 4.65 * (1.0 - (1.0 - clamp(t / 0.56)) ** 2.3) if t < 0.56 else 2.30 + 0.28 * ((t - 0.56) / 0.44)
        tail_ang = -2.50 + 4.88 * smoothstep(0.22, 1.0, t)
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.70 * smoothstep(0.60, 1.0, t))
        span = max(0.50, head_ang - tail_ang)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                arc_u = (ang - tail_ang) / span
                n = fbm(qx * 6.5, qy * 6.5, f * 17 + 13, 3)

                core_a = 0.0
                core_h = 0.20

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.18, arc_u) * smoothstep(1.0, 0.90, arc_u)
                    # Calligraphic thick-to-sharp bristle taper
                    thick = (0.09 + 0.35 * (arc_u ** 0.62) * (1.0 - 0.24 * arc_u)) * (0.80 + 0.24 * env)
                    center_r = 0.69 - 0.06 * (1.0 - arc_u) + 0.04 * t
                    outer_r = min(0.96, center_r + thick * 0.46)
                    inner_r = max(0.16, center_r - thick * 0.72 - 0.05 * (n - 0.5))

                    if inner_r <= r <= outer_r:
                        ru = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        # Dry-brush calligraphic split bristles in the inner/trailing wake!
                        bristle = 0.55 + 0.45 * math.sin(ru * math.pi * 7.0 + n * 4.5)
                        if arc_u < 0.42 and bristle < 0.72 - arc_u * 0.8:
                            bristle = 0.14
                        if t > 0.62 and n < (t - 0.55) * 1.45:
                            bristle *= 0.18
                        core_a = clamp(end_fade * env * smoothstep(0.0, 0.16, ru) * smoothstep(1.0, 0.88, ru) * (0.45 + 0.85 * bristle) * 1.32)
                        # High-contrast dark ink body -> razor bright leading cut line!
                        razor = smoothstep(0.70, 0.96, ru) * (0.50 + 0.50 * arc_u)
                        core_h = clamp(0.10 + 0.40 * ru + 0.55 * razor + 0.25 * smoothstep(0.78, 0.97, arc_u))

                # Calligraphic ink/gold splatter droplets along outer sweep
                if 0.40 < r < 0.98 and tail_ang - 0.35 < ang < head_ang + 0.18:
                    sp = hash2(x // 2, y // 2, f * 43 + 7)
                    if sp > 0.968:
                        core_a = max(core_a, 0.92 * env)
                        core_h = 0.95 if sp > 0.986 else 0.22

                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.69 - 0.06 * (1.0 - cu) + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.18) ** 2) * math.exp(-(ang_d / 0.19) ** 2) * 0.56 * env * smoothstep(0.99, 0.87, rr)
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.72, glow_a)

    return img


# --- 2. heavy_crush.png (1024x128, 8 frames of 128x128) ----------------------

def make_heavy_crush() -> Image.Image:
    """
    重量下压 (oracle_down, 钝器匹配):
    Crushing overhead vertical downward arc slamming into a compressed shock anvil at bottom!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Sweeps violently from top (-1.75 rad) down across front (0 rad) into ground (+1.45 rad)
        head_ang = -1.75 + 3.25 * (1.0 - (1.0 - clamp(t / 0.52)) ** 2.4) if t < 0.52 else 1.50
        tail_ang = -1.90 + 3.45 * smoothstep(0.24, 1.0, t)
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.72 * smoothstep(0.60, 1.0, t))
        span = max(0.55, head_ang - tail_ang)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                arc_u = (ang - tail_ang) / span
                n = fbm(qx * 5.0, qy * 5.0, f * 23 + 31, 2)

                core_a = 0.0
                core_h = 0.16

                # Heavy thick blunt-pressure downward smash crescent
                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.90, arc_u)
                    thick = (0.16 + 0.40 * (arc_u ** 0.55) * (1.0 - 0.16 * arc_u)) * (0.84 + 0.20 * env)
                    center_r = 0.62 + 0.06 * arc_u
                    outer_r = min(0.96, center_r + thick * 0.48)
                    inner_r = max(0.12, center_r - thick * 0.68)
                    if inner_r <= r <= outer_r:
                        ru = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        core_a = clamp(end_fade * env * smoothstep(0.0, 0.18, ru) * smoothstep(1.0, 0.86, ru) * 1.40)
                        # Dense heavy momentum block + crushing bright leading hammer face
                        core_h = clamp(0.08 + 0.44 * ru + 0.50 * smoothstep(0.66, 0.95, ru) * (0.45 + 0.55 * arc_u))

                # Impact ground-anvil pressure splash erupting at bottom (+qy near 0.72) on frames 2..7!
                if f >= 2:
                    it = (f - 2) / 5.0
                    imp_fade = 1.0 - 0.80 * it
                    ax = qx - 0.12
                    ay = qy - 0.76
                    # Compressed horizontal shock-bar spraying left & right
                    bar_w = 0.35 + 0.56 * ease_out_cubic(it)
                    bar_h = 0.18 * (1.0 - 0.55 * it)
                    if abs(ax) < bar_w and abs(ay) < bar_h:
                        val = (1.0 - abs(ay) / max(0.02, bar_h)) * smoothstep(bar_w, bar_w * 0.35, abs(ax)) * imp_fade
                        core_a = max(core_a, clamp(val * 1.3))
                        core_h = max(core_h, clamp(0.45 + 0.55 * val))
                    # Upward shattered debris wedges
                    for sx in (-0.42, -0.18, 0.20, 0.46):
                        d_s, proj = seg_dist(qx, qy, 0.12 + sx * 0.4, 0.78, 0.12 + sx * (1.1 + 0.5 * it), 0.78 - (0.35 + 0.32 * it))
                        w_s = 0.055 * (1.0 - 0.65 * proj) * imp_fade
                        if d_s < w_s and w_s > 0.008:
                            core_a = max(core_a, (1.0 - d_s / w_s) * imp_fade * 1.1)
                            core_h = max(core_h, 0.82)

                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.62 + 0.06 * cu
                glow_a = math.exp(-((rr - g_r) / 0.22) ** 2) * math.exp(-(ang_d / 0.22) ** 2) * 0.56 * env * smoothstep(0.99, 0.86, rr)
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.68, glow_a)

    return img


# --- 3. pierce_lance.png (512x128, 8 frames of 64x128) -----------------------

def make_pierce_lance() -> Image.Image:
    """
    直线突刺 (oracle_thrust, 太刀匹配):
    Needle-sharp high-velocity piercing thrust lance (`v=0` tip -> `v=1` tail)
    with twin radial Mach shock-rings and razor-fine velocity streaks!
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        env = smoothstep(0.0, 0.10, t + 0.12) * (1.0 - 0.72 * smoothstep(0.58, 1.0, t))
        scroll = t * 4.5

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = quantize(y / h, 64)  # 0 = spear tip, 1 = tail
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = (y + 0.5) / h

                core_a = 0.0
                core_h = 0.25

                # Ultra-sleek razor piercing central lance spear (needle tip at v=0)
                if 0.015 < qy < 0.98:
                    lance_w = (0.018 + 0.22 * (qy ** 0.92)) * (1.0 - 0.62 * smoothstep(0.45, 0.98, qy)) * (1.0 - 0.30 * t)
                    if abs(qx) < lance_w:
                        in_l = 1.0 - abs(qx) / max(0.012, lance_w)
                        tip_f = smoothstep(0.015, 0.06, qy)
                        tail_f = smoothstep(0.98, 0.66, qy)
                        core_a = max(core_a, clamp(smoothstep(0.0, 0.26, in_l) * tip_f * tail_f * env * 1.48))
                        core_h = max(core_h, clamp(0.28 + 0.72 * in_l))

                # Twin flat swept hypersonic Mach-break shock-collars (crisp flattened discs, NOT round 8!)
                for r_idx, (ry0, r_max) in enumerate(((0.22, 0.88), (0.50, 0.66))):
                    cy = ry0 + 0.14 * t + 0.075 * abs(qx)
                    ring_r = 0.26 + (r_max - 0.26) * ease_out_cubic(min(1.0, t * 1.4 + r_idx * 0.14))
                    dy_e = (qy - cy) * 14.0
                    d_ring = abs(math.hypot(qx / max(0.14, ring_r), dy_e) - 1.0)
                    if d_ring < 0.28 and abs(qx) < 0.95:
                        rv = (1.0 - d_ring / 0.28) * env * (0.96 - 0.18 * r_idx)
                        if rv > core_a:
                            core_a = clamp(rv * 1.25)
                            core_h = clamp(0.58 + 0.42 * rv)

                # Sharp swept sonic chevrons & razor-fine parallel speed needles
                for sx in (-0.36, 0.36, -0.64, 0.64):
                    y_start = 0.16 + abs(sx) * 0.42 + 0.06 * math.sin(scroll + sx * 5.0)
                    y_end = min(0.95, y_start + 0.42)
                    if y_start < qy < y_end and abs(qx - sx) < 0.038:
                        sv = (1.0 - abs(qx - sx) / 0.038) * math.sin((qy - y_start) / max(0.05, y_end - y_start) * math.pi) * env * 0.90
                        if sv > core_a:
                            core_a = sv
                            core_h = 0.92

                glow_a = (
                    math.exp(-(abs(rx) / 0.24) ** 2) * smoothstep(0.0, 0.08, ry) * smoothstep(1.0, 0.65, ry) * 0.62
                    + math.exp(-(math.hypot(rx, (ry - 0.14) * 2.5) / 0.35) ** 2) * 0.42
                ) * env * smoothstep(0.99, 0.84, abs(rx))
                px[f * w + x, y] = pack_dual_pixel(core_h, core_a, 0.82, clamp(glow_a))

    return img


# --- 4. cross_smile.png (1024x128, 8 frames of 128x128) ----------------------

def make_cross_smile() -> Image.Image:
    """
    举刃对笑 (oracle_smile, 短剑匹配):
    Twin intersecting lethal X-cross dagger slashes with asymmetrical razor extensions & heart starflash!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Two intersecting curved-razor diagonals (like an assassin's twin cross-cut!)
    for f in range(frames):
        t = f / (frames - 1)
        reach = 0.38 + 0.58 * ease_out_cubic(min(1.0, t * 1.55))
        env = smoothstep(0.0, 0.10, t + 0.14) * (1.0 - 0.74 * smoothstep(0.55, 1.0, t))
        w_base = 0.15 * (1.0 - 0.56 * t) + 0.025

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                rr = math.hypot(rx, ry)
                n = fbm(qx * 6.5, qy * 6.5, f * 19 + 41, 2)

                core_a = 0.0
                core_h = 0.22
                min_d = 9.9

                # Slash 1 (top-left -> bottom-right, slightly arched) & Slash 2 (top-right -> bottom-left)
                for sign, ang_deg in ((1.0, 38.0), (-1.0, -38.0)):
                    rad = math.radians(ang_deg)
                    c, s = math.cos(rad), math.sin(rad)
                    # Local coords along slash (lx) and perpendicular (ly)
                    lx = qx * c + qy * s
                    ly = -qx * s + qy * c + 0.08 * sign * (lx * lx)
                    if abs(lx) < reach:
                        u = 1.0 - abs(lx) / max(0.05, reach)
                        w_cur = w_base * (u ** 0.72) + 0.012
                        d_slash = abs(ly)
                        min_d = min(min_d, max(0.0, d_slash - w_cur))
                        if d_slash < w_cur:
                            in_s = 1.0 - d_slash / max(0.008, w_cur)
                            breakup = 1.0 if (t < 0.58 or n > (t - 0.52) * 1.4) else 0.20
                            a_val = clamp(smoothstep(0.0, 0.26, in_s) * env * breakup * 1.42)
                            h_val = clamp(0.14 + 0.50 * in_s + 0.42 * smoothstep(0.55, 0.96, in_s))
                            if a_val >= core_a * 0.85:
                                core_a = max(core_a, a_val)
                                core_h = max(core_h, h_val)

                # Sharp central diamond flash on early frames 0..3
                if f <= 3:
                    cf = 1.0 - f / 4.0
                    dia = abs(qx) + abs(qy)
                    if dia < 0.34 * cf + 0.08:
                        cv = (1.0 - dia / (0.34 * cf + 0.08)) * cf
                        core_a = max(core_a, clamp(cv * 1.5))
                        core_h = 1.0

                # Crisp flecks bursting off intersection
                if 0.16 < qr < 0.94 and hash2(x // 2, y // 2, f * 53 + 11) > 0.972:
                    core_a = max(core_a, 0.90 * env)
                    core_h = 0.96

                glow_a = (
                    math.exp(-(min_d / 0.16) ** 2) * 0.56
                    + math.exp(-(rr / 0.36) ** 2) * 0.36 * (1.0 - 0.5 * t)
                ) * env * smoothstep(0.99, 0.86, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.80, clamp(glow_a))

    return img


# --- 5. space_rift.png (1024x128, 8 frames of 128x128) -----------------------

def make_space_rift() -> Image.Image:
    """
    空间斩裂 (oracle_rift, 光剑匹配) & Furioso-Replica Dimensional Tear!
    Jagged reality-slicing fissure splitting open along X-axis with sharp fractured glass-like
    spatial shards, lightning-cracked void interior, and bright luminous rift borders!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Deterministic razor-jagged dimensional tear spine from (-0.96, 0) to (+0.96, 0)
    spine = [(-0.96, 0.0)]
    for i in range(1, 12):
        tx = -0.96 + (1.92 * i / 12.0)
        zig = (1.0 if i % 2 == 0 else -1.0) * (0.04 + hash2(i, 7, 907) * 0.09)
        ty = zig * math.sin((i / 12.0) * math.pi)
        spine.append((tx, ty))
    spine.append((0.96, 0.0))

    # Sharp shattered-glass reality fracture rays jutting off the tear!
    branches = []
    for i in range(2, 11):
        sx, sy = spine[i]
        side_s = 1.0 if i % 2 == 0 else -1.0
        ang = (0.62 + hash2(i, 13, 907) * 0.72) * side_s
        blen = 0.24 + hash2(i, 19, 907) * 0.30
        mid = (sx + math.cos(ang) * blen * 0.55, sy + math.sin(ang) * blen * 0.55)
        ang2 = ang + (0.35 if i % 3 == 0 else -0.35)
        end = (mid[0] + math.cos(ang2) * blen * 0.55, mid[1] + math.sin(ang2) * blen * 0.55)
        branches.append(((sx, sy), mid, 0.028))
        branches.append((mid, end, 0.016))

    for f in range(frames):
        t = f / (frames - 1)
        # Razor-slit opening: sleek angular sword-slash fissure (NOT a cloud!)
        if t < 0.22:
            open_w = 0.03 + 0.17 * ease_out_cubic(t / 0.22)
        elif t < 0.62:
            open_w = 0.20 - 0.04 * ((t - 0.22) / 0.40)
        else:
            open_w = 0.16 * (1.0 - (t - 0.62) / 0.38) ** 1.5
        env = smoothstep(0.0, 0.08, t + 0.12) * (1.0 - 0.70 * smoothstep(0.66, 1.0, t))
        len_prog = smoothstep(0.0, 0.22, t + 0.16)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 9.0, qy * 9.0, f * 13 + 77, 2)
                core_a = 0.0
                core_h = 0.04

                # Interpolate vertical spine height y_spine(qx) for razor-sharp angular tear profile!
                x_lim = 0.96 * len_prog
                best_d = 9.9
                for i in range(len(spine) - 1):
                    d, _ = seg_dist(qx, qy, spine[i][0], spine[i][1], spine[i + 1][0], spine[i + 1][1])
                    if d < best_d:
                        best_d = d

                if abs(qx) < x_lim:
                    # Find piecewise linear spine Y at qx
                    u_x = (qx + 0.96) / 1.92 * (len(spine) - 1)
                    idx = max(0, min(len(spine) - 2, int(math.floor(u_x))))
                    frac = u_x - idx
                    y_sp = lerp(spine[idx][1], spine[idx + 1][1], frac)
                    # Sharp diamond/katana-slit envelope: linear triangular pinch to needle tips!
                    x_env = max(0.0, 1.0 - abs(qx) / max(0.05, x_lim)) ** 1.15
                    saw = 0.032 * abs(math.sin(qx * 19.0 + f * 0.5)) * x_env
                    local_w = max(0.008, open_w * x_env + saw)
                    rim_w = local_w + 0.048 * (0.45 + 0.55 * x_env)
                    dy = abs(qy - y_sp)
                    if dy < local_w:
                        in_v = dy / max(0.008, local_w)
                        core_a = clamp(0.98 * env)
                        # Pitch-dark dimensional void interior + crackling seam flash right on inner lip
                        core_h = clamp(0.01 + 0.08 * (in_v ** 2) + 0.85 * smoothstep(0.76, 1.0, in_v))
                    elif dy < rim_w:
                        u_rim = 1.0 - (dy - local_w) / max(0.008, rim_w - local_w)
                        core_a = clamp(smoothstep(0.0, 0.30, u_rim) * env * 1.42)
                        core_h = clamp(0.78 + 0.22 * u_rim)

                # Sharp glass-fracture branch cracks radiating off the spatial tear
                if f >= 1:
                    for a, b, bw in branches:
                        d_b, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                        wb = bw * (1.0 - 0.65 * proj) * (1.0 - 0.42 * t)
                        if d_b < wb and wb > 0.005:
                            bv = (1.0 - d_b / wb) * env
                            if bv > core_a * 0.65:
                                core_a = max(core_a, clamp(bv * 1.25))
                                core_h = max(core_h, 0.90 + 0.10 * bv)

                # Angular shattered reality shards drifting away from tear
                if f >= 2 and abs(qy) < 0.80 and abs(qx) < 0.90:
                    sx = hash2(x // 4, y // 4, f * 31 + 99)
                    if sx > 0.962 and best_d < 0.48:
                        core_a = max(core_a, 0.92 * env)
                        core_h = 0.98 if sx > 0.980 else 0.08

                glow_a = math.exp(-(best_d / (open_w + 0.13)) ** 2) * smoothstep(0.99, 0.72, abs(rx)) * 0.56 * env * smoothstep(0.99, 0.80, abs(ry))
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.86, clamp(glow_a))

    return img


# --- 6. oracle_sigil.png (256x256) -------------------------------------------

def make_oracle_sigil() -> Image.Image:
    """
    神谕代行者 Prescript / Celestial Astrolabe Sigil (256x256):
    Sleek judicial geometric compass, 9-point Hermes resonance notches, clockwork degree ticks,
    and razor-sharp interlocking triangles & celestial index needles!
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # 9-point Hermes geometric star chords (step 4 -> crisp nonagram!)
    nonagram = []
    pts9 = [(math.cos(i * math.tau / 9.0 - math.pi * 0.5) * 0.74, math.sin(i * math.tau / 9.0 - math.pi * 0.5) * 0.74) for i in range(9)]
    for i in range(9):
        nonagram.append((pts9[i], pts9[(i + 4) % 9]))

    # 4 cardinal celestial index pointers
    needles = []
    for i in range(4):
        a = i * math.pi * 0.5
        needles.append(((math.cos(a) * 0.24, math.sin(a) * 0.24), (math.cos(a) * 0.95, math.sin(a) * 0.95)))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            core_a = 0.0
            core_h = 0.25

            # Razor-sharp precision concentric astrolabe rings
            for rad, thick, heat in ((0.92, 0.020, 0.90), (0.86, 0.011, 0.72), (0.74, 0.016, 0.86), (0.44, 0.014, 0.82), (0.18, 0.014, 0.94)):
                d = abs(qr - rad)
                if d < thick * 1.6:
                    if d < thick:
                        v = 1.0 - smoothstep(thick * 0.35, thick, d)
                        core_a = max(core_a, v)
                        core_h = max(core_h, heat + 0.10 * v)
                    else:
                        oa = (1.0 - (d - thick) / (thick * 0.6)) * 0.75
                        if oa > core_a:
                            core_a = oa
                            core_h = 0.12

            # 36 fine clockwork degree ticks in outer band (0.76..0.85)
            if 0.76 < qr < 0.85:
                sec = ((qa + math.pi) / math.tau) * 36.0
                frac = sec - math.floor(sec)
                idx = int(math.floor(sec))
                major = (idx % 4 == 0)
                if (0.42 < frac < 0.58) and (major or qr > 0.80):
                    core_a = max(core_a, 0.92 if major else 0.72)
                    core_h = max(core_h, 0.92 if major else 0.70)

            # 9-point Hermes resonance diamond nodes at radius 0.92
            for i in range(9):
                ang = i * math.tau / 9.0 - math.pi * 0.5
                nx, ny = math.cos(ang) * 0.92, math.sin(ang) * 0.92
                dd = abs(qx - nx) + abs(qy - ny)
                if dd < 0.055:
                    v = 1.0 - smoothstep(0.025, 0.055, dd)
                    core_a = max(core_a, v)
                    core_h = max(core_h, 0.94)

            # Nonagram geometry lines
            for a, b in nonagram:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                if d < 0.015:
                    v = 1.0 - smoothstep(0.005, 0.015, d)
                    core_a = max(core_a, v * 0.90)
                    core_h = max(core_h, 0.68 + 0.28 * v)

            # 4 cardinal index needles
            for a, b in needles:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                wn = 0.024 * (1.0 - 0.60 * proj)
                if d < wn and wn > 0.004:
                    v = 1.0 - d / wn
                    core_a = max(core_a, clamp(v * 1.4))
                    core_h = max(core_h, 0.85 + 0.15 * v)

            # Central prescript eye / diamond
            dia = abs(qx) + abs(qy)
            if dia < 0.14:
                v = 1.0 - smoothstep(0.06, 0.14, dia)
                core_a = max(core_a, v * 0.96)
                core_h = 0.96

            glow_a = (
                math.exp(-((rr - 0.92) / 0.055) ** 2) * 0.44
                + math.exp(-((rr - 0.74) / 0.055) ** 2) * 0.34
                + math.exp(-((rr - 0.44) / 0.055) ** 2) * 0.26
                + math.exp(-(rr / 0.24) ** 2) * 0.32
            ) * smoothstep(0.99, 0.90, rr)
            px[x, y] = pack_dual_pixel(core_h, core_a, 0.80, clamp(glow_a))

    return img


# --- 7. oracle_halo.png (256x256) --------------------------------------------

def make_oracle_halo() -> Image.Image:
    """
    代行者赫尔墨斯常驻背轮/光环 (256x256):
    Clean ornate golden celestial halo with segmented arcs, 9 slender spire rays & subtle filigree.
    Open center so it frames the player cleanly without obstructing visibility!
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    rays = []
    for i in range(9):
        a = i * math.tau / 9.0 - math.pi * 0.5
        rays.append(((math.cos(a) * 0.70, math.sin(a) * 0.70), (math.cos(a) * 0.96, math.sin(a) * 0.96)))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            core_a = 0.0
            core_h = 0.35

            # Outer & inner slim halo rings (hollow center < 0.55 keeps silhouette crisp!)
            for rad, thick, heat in ((0.78, 0.022, 0.92), (0.68, 0.014, 0.80)):
                d = abs(qr - rad)
                if d < thick * 1.5:
                    if d < thick:
                        v = 1.0 - smoothstep(thick * 0.35, thick, d)
                        core_a = max(core_a, v)
                        core_h = max(core_h, heat + 0.08 * v)
                    else:
                        oa = (1.0 - (d - thick) / (thick * 0.5)) * 0.65
                        if oa > core_a:
                            core_a = oa
                            core_h = 0.16

            # Delicate segmented arc crown at r=0.86
            if abs(qr - 0.86) < 0.016:
                sec = ((qa + math.pi) / math.tau) * 9.0
                frac = sec - math.floor(sec)
                if 0.16 < frac < 0.84:
                    v = 1.0 - abs(qr - 0.86) / 0.016
                    core_a = max(core_a, v * 0.86)
                    core_h = max(core_h, 0.86)

            # 9 slender crown spire rays
            for a, b in rays:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w_r = 0.032 * (1.0 - 0.78 * proj)
                if d < w_r and w_r > 0.004:
                    v = 1.0 - d / w_r
                    core_a = max(core_a, clamp(v * 1.45))
                    core_h = max(core_h, clamp(0.70 + 0.30 * v))

            glow_a = (
                math.exp(-((rr - 0.76) / 0.075) ** 2) * 0.52
                + math.exp(-((rr - 0.86) / 0.055) ** 2) * 0.25
            ) * smoothstep(0.99, 0.90, rr)
            px[x, y] = pack_dual_pixel(core_h, core_a, 0.84, clamp(glow_a))

    return img


# --- 8. oracle_impact.png (1024x128, 8 frames of 128x128) --------------------

def make_oracle_impact() -> Image.Image:
    """
    Oracle sharp geometric starburst & calligraphic shatter impact (重量下压 / 沉重横批 / Furioso终结):
    Crisp 8-needle judicial star detonation + jagged ink/gold radial shards & expanding shock ring.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ring_r = 0.16 + 0.74 * ease_out_cubic(t)
        env = smoothstep(0.0, 0.08, t + 0.10) * (1.0 - 0.74 * smoothstep(0.54, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                qa = math.atan2(qy, qx)
                rr = math.hypot(rx, ry)
                n = fbm(qx * 6.5, qy * 6.5, f * 29 + 17, 2)

                core_a = 0.0
                core_h = 0.24

                # Expanding razor geometric shock ring
                rw = 0.14 * (1.0 - 0.60 * t) + 0.02
                rd = abs(qr - ring_r)
                if rd < rw and (f < 4 or n > (t - 0.42) * 1.25):
                    in_r = 1.0 - rd / max(0.015, rw)
                    core_a = max(core_a, clamp(smoothstep(0.0, 0.30, in_r) * env * 1.28))
                    core_h = max(core_h, clamp(0.20 + 0.78 * in_r))

                # 8 razor judicial star-needles bursting outward
                needle_len = 0.32 + 0.62 * ease_out_cubic(min(1.0, t * 1.35))
                if qr < needle_len:
                    ang_mod = abs(math.cos(qa * 4.0)) ** 10
                    ang_sub = abs(math.cos(qa * 4.0 + math.pi * 0.5)) ** 14
                    needle = max(ang_mod, ang_sub * 0.72) * (1.0 - qr / max(0.08, needle_len))
                    na = clamp(needle * 1.8 * env * (1.0 - 0.35 * t))
                    if na > core_a:
                        core_a = na
                        core_h = clamp(0.55 + 0.45 * na)

                # Early-frame blinding diamond core
                if f <= 2:
                    cf = 1.0 - f * 0.30
                    dia = abs(qx) + abs(qy)
                    if dia < 0.42 * cf:
                        core_a = max(core_a, clamp((1.0 - dia / (0.42 * cf)) * 1.35))
                        core_h = 1.0

                # Calligraphic splatter dots flying outward
                if 0.20 < qr < 0.96 and hash2(x // 2, y // 2, f * 37 + 23) > 0.965:
                    core_a = max(core_a, 0.90 * env)
                    core_h = 0.94 if hash2(x, y, f) > 0.5 else 0.16

                glow_a = (
                    math.exp(-((rr - ring_r) / (rw * 1.8 + 0.08)) ** 2) * 0.54
                    + math.exp(-(rr / 0.36) ** 2) * 0.45 * (1.0 - 0.6 * t)
                ) * env * smoothstep(0.99, 0.85, rr)
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.80, clamp(glow_a))

    return img


# --- 9. tele_shatter.png (512x64, 8 frames of 64x64) -------------------------

def make_tele_shatter() -> Image.Image:
    """
    举刃对笑瞬移起落点 / Furioso-Replica 切武器闪现空间菱形波纹!
    Nested expanding/contracting diamond portal frames + vertical sword-cut line.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        env = smoothstep(0.0, 0.12, t + 0.12) * (1.0 - 0.75 * smoothstep(0.56, 1.0, t))
        r1 = 0.24 + 0.68 * ease_out_cubic(t)
        r2 = 0.12 + 0.46 * ease_out_cubic(t)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                dia = abs(qx) * 1.15 + abs(qy) * 0.85
                core_a = 0.0
                core_h = 0.45

                for rad, w_d, h_d in ((r1, 0.08 * (1.0 - 0.45 * t), 0.90), (r2, 0.055, 0.72)):
                    dd = abs(dia - rad)
                    if dd < w_d:
                        v = (1.0 - dd / max(0.01, w_d)) * env
                        core_a = max(core_a, clamp(v * 1.35))
                        core_h = max(core_h, h_d)

                # Sharp vertical space-cut flash needle
                if abs(qy) < 0.88:
                    vw = 0.065 * (1.0 - 0.65 * t) * (1.0 - abs(qy) / 0.88)
                    if abs(qx) < vw:
                        v = (1.0 - abs(qx) / max(0.008, vw)) * env
                        core_a = max(core_a, clamp(v * 1.4))
                        core_h = 1.0

                rdia = abs(rx) * 1.15 + abs(ry) * 0.85
                glow_a = math.exp(-((rdia - r1) / 0.14) ** 2) * 0.52 * env * smoothstep(0.99, 0.84, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_dual_pixel(core_h, core_a, 0.82, glow_a)

    return img


# --- Contact Sheet Preview Generator -----------------------------------------

def save_contact_sheet(assets: dict[str, Image.Image], out_path: Path, bg=(18, 20, 26, 255)) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    pad = 16
    label_h = 18
    max_w = max(min(img.width, 1024) for img in assets.values())
    row_heights = [min(img.height, 256) + label_h + pad for img in assets.values()]
    total_h = sum(row_heights) + pad
    sheet = Image.new("RGBA", (max_w + pad * 2, total_h), bg)
    draw = ImageDraw.Draw(sheet)

    y_cur = pad
    for name, img in assets.items():
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(245, 206, 103, 255))
        y_cur += label_h
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(76, 68, 52, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "ink_slash.png": make_ink_slash(),
        "heavy_crush.png": make_heavy_crush(),
        "pierce_lance.png": make_pierce_lance(),
        "cross_smile.png": make_cross_smile(),
        "space_rift.png": make_space_rift(),
        "oracle_sigil.png": make_oracle_sigil(),
        "oracle_halo.png": make_oracle_halo(),
        "oracle_impact.png": make_oracle_impact(),
        "tele_shatter.png": make_tele_shatter(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "oracle_contact_sheet.png")


if __name__ == "__main__":
    main()

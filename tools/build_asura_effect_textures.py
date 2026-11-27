#!/usr/bin/env python3
"""
Dedicated DNF-inspired Asura / Indra / Dark Sky Wave Eye (阿修罗 / 大暗黑天)
high-contrast pixel-sharp + soft-feathered VFX texture generator.

Outputs pre-baked multi-tone RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/asura/
and a labeled contact sheet preview under:
  temp/vfx-preview/asura_contact_sheet.png

Design direction (Classic DNF High-Contrast Per-Skill Palettes, Dual-Layer Ready):
  - Wave Brands & Agni (波动刻印 / 不动明王阵): Sacred bronze-gold -> amber flame -> ivory-white core
  - Ghost Orb (鬼印珠): Abyssal indigo -> thunder-violet -> high-voltage cyan-white lightning
  - Spirit Crescent (邪光斩): Shura dark-gold outline -> pale gold-cyan crescent blade -> blazing white ridge
  - Ice Wave (冰刃·波动剑): Deep glacial blue -> crystalline cyan -> frosted pure white facets
  - Fire Wave (爆炎·波动剑): Charred obsidian -> molten crimson -> roaring magma-orange -> white-hot heart
  - Wave Eye & Variants (暗天波动眼 / 旋刃齿轮 / 光翼 / 穿云刺轮 / 闪枪):
      Heavy abyssal dark-violet shroud + deep purple plasma + imperial golden slit-pupil + violet-silver bolts
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/asura"
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


def angle_wrap(a: float) -> float:
    return (a + math.pi) % (2.0 * math.pi) - math.pi


def point_in_tri(px: float, py: float, ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> bool:
    d1 = (px - bx) * (ay - by) - (ax - bx) * (py - by)
    d2 = (px - cx) * (by - cy) - (bx - cx) * (py - cy)
    d3 = (px - ax) * (cy - ay) - (cx - ax) * (py - ay)
    has_neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
    has_pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
    return not (has_neg and has_pos)


def tri_edge_dist(px: float, py: float, ax: float, ay: float, bx: float, by: float, cx: float, cy: float) -> float:
    d1, _ = seg_dist(px, py, ax, ay, bx, by)
    d2, _ = seg_dist(px, py, bx, by, cx, cy)
    d3, _ = seg_dist(px, py, cx, cy, ax, ay)
    return min(d1, d2, d3)


# --- Classic DNF Asura Multi-Tone Color Ramps --------------------------------

GOLD_RUNE_RAMP = (
    (0.00, (34, 16, 4)),     # deep temple bronze shadow
    (0.18, (108, 52, 10)),   # burnished dark amber
    (0.42, (206, 126, 26)),  # warm buddhist gold
    (0.68, (250, 192, 66)),  # radiant Asura brand gold
    (0.86, (255, 228, 134)), # blazing golden shimmer
    (1.00, (255, 250, 224)), # ivory-white core
)

LIGHTNING_PURPLE_RAMP = (
    (0.00, (16, 6, 36)),     # abyssal dark indigo
    (0.20, (54, 18, 118)),   # deep thunder violet
    (0.44, (118, 52, 206)),  # charged ghost-orb purple
    (0.68, (176, 118, 255)), # electric lavender body
    (0.85, (152, 228, 255)), # high-voltage cyan-white arc
    (1.00, (248, 252, 255)), # pure lightning flash
)

CRESCENT_GOLD_RAMP = (
    (0.00, (24, 22, 12)),    # dark shura bronze edge
    (0.18, (88, 80, 36)),    # olive-gold blade shadow
    (0.42, (192, 174, 88)),  # pale wave gold
    (0.66, (238, 228, 148)), # classic DNF spirit crescent gold
    (0.84, (208, 248, 252)), # shura cyan-gold razor gleam
    (1.00, (255, 255, 244)), # incandescent cutting spine
)

ICE_CRYSTAL_RAMP = (
    (0.00, (8, 22, 52)),     # abyssal glacial navy
    (0.20, (22, 74, 146)),   # deep sapphire ice shadow
    (0.44, (54, 152, 228)),  # crisp arctic blue
    (0.68, (124, 218, 255)), # luminous frost cyan
    (0.86, (212, 246, 255)), # crystalline white-blue edge
    (1.00, (252, 255, 255)), # pure frost specular
)

FIRE_MAGMA_RAMP = (
    (0.00, (24, 7, 4)),      # charred obsidian soot
    (0.18, (108, 20, 8)),    # deep volcanic crimson
    (0.42, (206, 56, 14)),   # blazing flame vermilion
    (0.66, (250, 124, 24)),  # roaring magma orange
    (0.86, (255, 196, 68)),  # scorching golden flame
    (1.00, (255, 246, 204)), # white-hot fire heart
)

AGNI_FLAME_RAMP = (
    (0.00, (32, 10, 4)),     # scorched dark bronze
    (0.18, (132, 34, 10)),   # deep vermilion fire
    (0.42, (224, 96, 20)),   # fierce Agni flame-orange
    (0.68, (252, 176, 46)),  # blazing imperial gold-flame
    (0.86, (255, 224, 118)), # radiant lotus gold
    (1.00, (255, 252, 226)), # incandescent white-gold core
)

EYE_VOID_RAMP = (
    (0.00, (8, 3, 18)),      # pitch dark sky void
    (0.18, (32, 10, 66)),    # heavy abyssal purple
    (0.40, (86, 28, 154)),   # dark sky wave-eye violet
    (0.64, (152, 78, 232)),  # luminous royal violet
    (0.82, (214, 166, 255)), # lilac lightning rim
    (0.93, (255, 208, 86)),  # imperial gold eye highlight
    (1.00, (255, 248, 218)), # blinding slit-pupil core
)

EYE_WING_RAMP = (
    (0.00, (14, 6, 30)),     # dark violet wing shadow
    (0.20, (66, 28, 138)),   # deep spectral indigo
    (0.45, (142, 82, 236)),  # radiant wave-eye purple
    (0.70, (186, 214, 255)), # luminous lavender-cyan feather
    (0.88, (230, 244, 255)), # pale silver-blue wingtip
    (1.00, (255, 255, 255)), # pure white light edge
)


def sample_ramp(ramp: tuple[tuple[float, tuple[int, int, int]], ...], t: float) -> tuple[int, int, int]:
    t = clamp(t)
    for i in range(len(ramp) - 1):
        t0, c0 = ramp[i]
        t1, c1 = ramp[i + 1]
        if t <= t1:
            u = (t - t0) / max(1e-6, t1 - t0)
            return (
                int(round(lerp(c0[0], c1[0], u))),
                int(round(lerp(c0[1], c1[1], u))),
                int(round(lerp(c0[2], c1[2], u))),
            )
    return ramp[-1][1]


def pack_ramp_pixel(
    ramp: tuple[tuple[float, tuple[int, int, int]], ...],
    heat: float, core_a: float,
    glow_heat: float = 0.55, glow_a: float = 0.0,
) -> tuple[int, int, int, int]:
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (0, 0, 0, 0)
    cr, cg, cb = sample_ramp(ramp, heat)
    gr, gg, gb = sample_ramp(ramp, glow_heat)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    return (
        int(round(clamp(cr * wc + gr * wg, 0, 255))),
        int(round(clamp(cg * wc + gg * wg, 0, 255))),
        int(round(clamp(cb * wc + gb * wg, 0, 255))),
        int(round(clamp(out_a) * 255)),
    )


def pack_rgb_blend(
    c_rgb: tuple[float, float, float], core_a: float,
    g_rgb: tuple[float, float, float], glow_a: float,
) -> tuple[int, int, int, int]:
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (0, 0, 0, 0)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    return (
        int(round(clamp(c_rgb[0] * wc + g_rgb[0] * wg, 0, 255))),
        int(round(clamp(c_rgb[1] * wc + g_rgb[1] * wg, 0, 255))),
        int(round(clamp(c_rgb[2] * wc + g_rgb[2] * wg, 0, 255))),
        int(round(clamp(out_a) * 255)),
    )


# --- 1. wave_brand_rune.png (512x128, 4 frames of 128x128) -------------------

def make_wave_brand_rune() -> Image.Image:
    """
    Classic DNF Wave Manifestation Brand (波动刻印):
    Luminous rotating golden brand orb with outer bronze-rimmed chakra ring,
    8-fold sacred ticks, stylized wave-seal glyph heart, and shimmering breath.
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        pulse = 0.5 + 0.5 * math.sin(phase)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)

                core_a = 0.0
                core_h = 0.25

                # Outer spinning sacred ring with 4 carved wave notches
                dr_out = abs(r - 0.72)
                if dr_out < 0.085:
                    rot_a = ang - phase * 0.5
                    notch = 0.55 + 0.45 * math.cos(rot_a * 4.0)
                    rim_w = 0.048 + 0.026 * notch
                    if dr_out < rim_w:
                        u = 1.0 - dr_out / rim_w
                        core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.96)
                        core_h = max(core_h, 0.14 + 0.70 * u + 0.10 * pulse)

                # 8 outward sacred brand spikes around ring
                spoke_a = abs(angle_wrap((ang - phase * 0.5) * 8.0)) / 8.0
                if 0.68 < r < 0.90:
                    w = 0.095 * (1.0 - (r - 0.68) / 0.24)
                    if spoke_a < w:
                        u = 1.0 - spoke_a / max(0.01, w)
                        a_sp = smoothstep(0.0, 0.4, u) * 0.92
                        if a_sp > core_a:
                            core_a = a_sp
                            core_h = 0.30 + 0.62 * u

                # Inner warm translucent golden orb body
                if r < 0.54:
                    u = 1.0 - r / 0.54
                    body_a = smoothstep(0.0, 0.28, u) * 0.88
                    body_h = 0.22 + 0.58 * (u ** 0.75) + 0.12 * pulse
                    if body_a > core_a:
                        core_a = body_a
                        core_h = body_h

                # Stylized sacred wave-brand glyph inside core (triple wave bar + vertical axis + diamond heart)
                ca = math.cos(phase * 0.25)
                sa = math.sin(phase * 0.25)
                gx = qx * ca - qy * sa
                gy = qx * sa + qy * ca
                if r < 0.46:
                    # Central vertical stroke + twin curved wave horns + horizontal bar
                    d_v, _ = seg_dist(gx, gy, 0.0, -0.32, 0.0, 0.32)
                    d_h, _ = seg_dist(gx, gy, -0.26, -0.02, 0.26, -0.02)
                    d_l, _ = seg_dist(gx, gy, -0.22, -0.24, -0.10, 0.24)
                    d_r, _ = seg_dist(gx, gy, 0.22, -0.24, 0.10, 0.24)
                    d_glyph = min(d_v, d_h, d_l, d_r)
                    if d_glyph < 0.055:
                        gu = 1.0 - d_glyph / 0.055
                        core_a = max(core_a, 0.98)
                        core_h = max(core_h, 0.78 + 0.22 * gu)

                # Soft radial golden aura halo
                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr - 0.72) / 0.14) ** 2) * 0.38
                    + math.exp(-(rr / 0.55) ** 2) * (0.46 + 0.10 * pulse)
                ) * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(GOLD_RUNE_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 2. ghost_orb_core.png (1024x128, 8 frames of 128x128) --------------------

def make_ghost_orb_core() -> Image.Image:
    """
    Classic DNF Ghost Orb (鬼印珠):
    Dense volatile dark-violet plasma sphere with spiraling purple-indigo vortex bands,
    blazing cyan-white lightning core, and erratic asymmetric high-voltage arcs crackling
    across the orb interior and outer rune ring!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        # Asymmetric chaotic jagged lightning bolts darting across the orb interior & rim
        bolts: list[list[tuple[float, float]]] = []
        for b in range(5):
            pts: list[tuple[float, float]] = []
            # Each bolt starts from a pseudo-random spot near the core/mid-ring and zags outward/across
            a0 = hash2(b, f * 7 + 3, 101) * math.tau + phase * (1.0 if b % 2 == 0 else -1.0)
            a1 = a0 + lerp(0.9, 2.5, hash2(b, f * 13 + 5, 211))
            r0 = lerp(0.08, 0.38, hash2(b, f * 19 + 1, 307))
            r1 = lerp(0.62, 0.90, hash2(b, f * 23 + 9, 401))
            segs = 5
            for s in range(segs + 1):
                u = s / segs
                ang = lerp(a0, a1, u) + (hash2(s, b * 11 + f, 509) - 0.5) * 0.42 * math.sin(u * math.pi)
                rad = lerp(r0, r1, u) + (hash2(s * 5, b + f * 3, 617) - 0.5) * 0.24 * math.sin(u * math.pi)
                pts.append((math.cos(ang) * rad, math.sin(ang) * rad))
            bolts.append(pts)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.02:
                    continue
                ang = math.atan2(qy, qx)

                core_a = 0.0
                core_h = 0.08

                # Dense turbulent thunder-purple sphere with 3-arm inward vortex
                if r < 0.72:
                    n1 = fbm(qx * 5.2 + math.cos(phase), qy * 5.2 + math.sin(phase), f * 11 + 3, 3)
                    n2 = fbm(qx * 8.5 - math.sin(phase), qy * 8.5 + math.cos(phase), f * 17 + 9, 2)
                    spiral = 0.5 + 0.5 * math.sin(ang * 3.0 - r * 8.5 - phase * 2.0 + (n1 - 0.5) * 2.0)
                    rim_shell = smoothstep(0.72, 0.54, r)
                    core_a = clamp(rim_shell * (0.78 + 0.35 * n1))
                    # Dark indigo outer shell -> bright violet energy bands -> blazing cyan-white heart!
                    rim_dark = smoothstep(0.46, 0.70, r)
                    hot_center = math.exp(-(r / 0.28) ** 2)
                    core_h = clamp(0.10 + 0.44 * spiral * (1.0 - 0.45 * rim_dark) + 0.18 * n2 + 0.62 * hot_center)

                # Thin broken orbiting electric rune ring (r = 0.80)
                dr_ring = abs(r - 0.80)
                if dr_ring < 0.055:
                    seg_mask = math.cos((ang + phase) * 3.0)
                    if seg_mask > 0.05:
                        u = 1.0 - dr_ring / 0.055
                        ra = smoothstep(0.0, 0.35, u) * smoothstep(0.05, 0.35, seg_mask) * 0.88
                        if ra > core_a:
                            core_a = ra
                            core_h = 0.28 + 0.52 * u

                # Crackling asymmetric cyan-white lightning arcs!
                if r < 0.94:
                    min_bd = 9.0
                    for pts in bolts:
                        for i in range(len(pts) - 1):
                            d, _ = seg_dist(qx, qy, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
                            if d < min_bd:
                                min_bd = d
                    if min_bd < 0.044:
                        bu = 1.0 - min_bd / 0.044
                        ba = smoothstep(0.0, 0.35, bu) * 0.98
                        if ba > core_a * 0.7:
                            core_a = max(core_a, ba)
                            core_h = max(core_h, 0.78 + 0.22 * bu)

                # Stray electric sparks popping off perimeter
                if 0.55 < r < 0.95:
                    sp = hash2(x // 2, y // 2, f * 43 + 19)
                    if sp > 0.982:
                        core_a = max(core_a, 0.94)
                        core_h = max(core_h, 0.95)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-(rr / 0.56) ** 2) * 0.55
                    + math.exp(-((rr - 0.78) / 0.14) ** 2) * 0.28
                ) * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(LIGHTNING_PURPLE_RAMP, core_h, core_a, 0.62, glow_a)

    return img


# --- 3. spirit_crescent_wave.png (1024x128, 8 frames of 128x128) -------------

def make_spirit_crescent_wave() -> Image.Image:
    """
    Shura Spirit Crescent (邪光斩 / 修罗邪光斩):
    Ultra-sleek, sweeping forward-arching DNF crescent energy blade!
    Leading crescent razor bows aggressively toward +qx (+forward), tapering cleanly into
    sharp swept horns at ±qy, with internal shura wave striations and trailing energy sparks.
    Rendered in 3D as twin crossed planes (±38 deg roll) to form a sharp volumetric X-crescent wave!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        shimmer = 0.5 + 0.5 * math.sin(phase)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                core_a = 0.0
                core_h = 0.14
                ay = abs(qy)

                if ay < 0.94:
                    # Clean razor crescent silhouette: convex front edge bows to +0.84, concave inner belly tapers to horns
                    front_x = 0.84 - 1.24 * (ay ** 1.75)
                    belly_w = (0.58 + 0.06 * shimmer) * ((1.0 - (ay / 0.94) ** 1.45) ** 0.85)
                    back_x = front_x - belly_w

                    if back_x <= qx <= front_x and belly_w > 0.02:
                        u = clamp((qx - back_x) / belly_w)  # 0 = trailing wispy edge, 1 = razor front edge
                        n = fbm(qx * 5.5 - phase * 0.9, qy * 5.5, f * 17 + 5, 2)
                        # Trailing flame-like wave streamers peeling off the back belly
                        wisp = 1.0 if (u > 0.34 or n > 0.44 - u * 0.85) else 0.22
                        grooves = 0.72 + 0.28 * math.sin(u * math.pi * 5.0 + ay * 6.0 - phase * 1.5)
                        tip_fade = smoothstep(0.94, 0.76, ay)
                        core_a = clamp(
                            smoothstep(0.0, 0.22, u) * smoothstep(1.0, 0.90, u)
                            * tip_fade * wisp * (0.78 + 0.30 * grooves) * 1.32
                        )
                        # Dark bronze shadow tail -> bright Shura gold belly -> luminous cyan-white razor edge!
                        razor = smoothstep(0.68, 0.96, u)
                        nose_hot = smoothstep(0.42, 0.0, ay) * smoothstep(0.55, 0.96, u) * 0.30
                        core_h = clamp(0.12 + 0.48 * u + 0.42 * razor + nose_hot)

                # Secondary thin inner crescent echo arc trailing behind
                if ay < 0.78:
                    echo_x = 0.22 - 0.92 * (ay ** 1.70)
                    de = abs(qx - echo_x)
                    if de < 0.048:
                        eu = 1.0 - de / 0.048
                        ea = smoothstep(0.0, 0.35, eu) * smoothstep(0.78, 0.50, ay) * 0.78
                        if ea > core_a:
                            core_a = ea
                            core_h = 0.62 + 0.32 * eu

                # Streaming wave-sparks peeling off behind the crescent
                if -0.92 < qx < 0.58 and ay < 0.88:
                    sp = hash2((x + f * 3) // 2, y // 2, f * 41 + 9)
                    if sp > 0.978:
                        core_a = max(core_a, 0.90)
                        core_h = max(core_h, 0.95 if sp > 0.988 else 0.55)

                ray = abs(ry)
                glow_a = 0.0
                if ray < 0.96:
                    g_x = 0.66 - 1.18 * (ray ** 1.75)
                    glow_a = math.exp(-((rx - g_x) / 0.24) ** 2) * smoothstep(0.96, 0.70, ray) * 0.52
                glow_a *= smoothstep(0.99, 0.88, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(CRESCENT_GOLD_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 4. ice_wave_spike.png (1024x128, 8 frames of 128x128) -------------------

def make_ice_wave_spike() -> Image.Image:
    """
    Classic DNF Ice Wave Sword (冰刃·波动剑):
    Sharp multi-faceted crystalline ice spike cluster (NEVER blocky cubes!):
      - Frames 0..1: violent eruption upward from ground (y = bottom)
      - Frames 2..4: towering razor-faceted twin-flanked ice spire peak
      - Frames 5..7: crystalline fracture cracks + explosive shattering ice shards & frost mist!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Define 3 sharp asymmetrical crystalline spikes: (base_x, half_w, apex_x, apex_y_top)
    # Coordinates in [-1, 1] where y=-1 is sky top, y=+1 is ground base!
    spires = [
        (0.00, 0.26, 0.04, -0.88),   # dominant center jagged ice blade
        (-0.34, 0.20, -0.58, -0.44), # left outward-angled flank crystal
        (0.32, 0.21, 0.62, -0.52),   # right outward-angled flank crystal
        (-0.14, 0.14, -0.26, -0.22), # low foreground shard
        (0.18, 0.13, 0.34, -0.18),   # low right shard
    ]

    for f in range(frames):
        # Rise fast (0->1), hold crisp (2->4), shatter & scatter (5->7)
        if f <= 1:
            rise = 0.42 + 0.58 * (f / 1.0)
            shatter = 0.0
            env = 0.85 + 0.15 * f
        elif f <= 4:
            rise = 1.0
            shatter = max(0.0, (f - 3) * 0.18)
            env = 1.0
        else:
            rise = 1.0 - 0.18 * (f - 4)
            shatter = (f - 4) / 3.0
            env = clamp(1.0 - 0.62 * shatter)

        # Precompute flying ice shards for shatter frames
        shards: list[tuple[float, float, float, float]] = []
        if shatter > 0.05:
            for k in range(14):
                ang = -math.pi * 0.12 - (k / 13.0) * math.pi * 0.76
                spd = 0.35 + 0.55 * hash2(k, 7, 101)
                sx = (hash2(k, 2, 19) - 0.5) * 0.55 + math.cos(ang) * spd * shatter
                sy = 0.35 - hash2(k, 5, 37) * 0.85 + math.sin(ang) * spd * shatter * 0.85
                rad = (0.055 + 0.045 * hash2(k, 9, 53)) * (1.0 - 0.45 * shatter)
                shards.append((sx, sy, rad, ang))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                core_a = 0.0
                core_h = 0.12
                glow_a = 0.0

                # Evaluate crystalline jagged ice spires anchored at ground (y = 0.94)
                base_y = 0.94
                if shatter < 0.88:
                    for idx, (bx, hw, ax_t, ay_t) in enumerate(spires):
                        cur_ay = lerp(base_y, ay_t, rise)
                        cur_ax = lerp(bx, ax_t, rise)
                        lx, rx_b = bx - hw, bx + hw
                        # Slight crystal jitter along silhouette
                        if point_in_tri(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay):
                            height_u = clamp((base_y - qy) / max(0.05, base_y - cur_ay))
                            # Compute faceted lighting: left facet darker glacial blue, right facet icy cyan, central spine pure white!
                            spine_x = lerp(bx, cur_ax, height_u)
                            half_at_y = max(0.02, hw * (1.0 - height_u))
                            side_u = (qx - spine_x) / half_at_y  # [-1, +1]
                            edge_d = tri_edge_dist(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay)
                            facet_h = 0.24 if side_u < -0.05 else 0.58
                            ridge_glint = math.exp(-((side_u - 0.04) / 0.16) ** 2) * (0.35 + 0.45 * height_u)
                            edge_rim = smoothstep(0.07, 0.0, edge_d) * 0.36
                            tip_hot = smoothstep(0.65, 1.0, height_u) * 0.30
                            # Dark glacial shadow at very base, crisp translucent body above
                            base_fade = smoothstep(0.95, 0.82, qy)
                            h = clamp(facet_h + ridge_glint + edge_rim + tip_hot - 0.16 * (1.0 - height_u))

                            # Shatter fractures cutting gaps out of the crystal in frames 5..7
                            a_c = clamp(0.96 * env * (0.55 + 0.45 * base_fade))
                            if shatter > 0.02:
                                n = fbm(qx * 7.0, qy * 7.0, idx * 23 + 11, 2)
                                if n < shatter * 0.92:
                                    a_c = 0.0
                                elif n < shatter * 0.92 + 0.12:
                                    h = 0.96  # bright white crack edge!

                            if a_c > core_a:
                                core_a = a_c
                                core_h = h

                # Flying shattered crystal diamond shards
                for sx, sy, srad, sang in shards:
                    dx, dy = qx - sx, qy - sy
                    if abs(dx) < srad * 1.4 and abs(dy) < srad * 1.4:
                        ca, sa = math.cos(sang), math.sin(sang)
                        lx = dx * ca + dy * sa
                        ly = -dx * sa + dy * ca
                        d_dia = abs(lx) * 0.75 + abs(ly) * 1.45
                        if d_dia < srad:
                            u = 1.0 - d_dia / srad
                            sa_val = smoothstep(0.0, 0.35, u) * env * 0.95
                            if sa_val > core_a:
                                core_a = sa_val
                                core_h = 0.62 + 0.38 * u

                # Frost burst mist cloud clinging to the base & enveloping spikes
                base_mist = math.exp(-((rx / 0.62) ** 2 + ((ry - 0.72) / 0.28) ** 2)) * (0.48 + 0.25 * (1.0 - shatter))
                body_glow = math.exp(-((rx / 0.42) ** 2 + ((ry - 0.15) / 0.72) ** 2)) * 0.34 * env
                glow_a = clamp(base_mist + body_glow) * smoothstep(0.99, 0.85, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ICE_CRYSTAL_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 5. ice_frost_trail.png (256x256, 1 frame) -------------------------------

def make_ice_frost_trail() -> Image.Image:
    """Ground frost-cracked snowflake fissure decal beneath Ice Wave spikes."""
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 96) - 0.5) * 2.0
            qy = (quantize(y / size, 96) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            r = math.hypot(qx, qy)
            if r > 1.02:
                continue
            ang = math.atan2(qy, qx)
            n = fbm(qx * 5.0, qy * 5.0, 211, 3)

            # 6-fold sharp frost crystal spokes + jagged radial ice cracks
            spoke = abs(math.sin(ang * 3.0))
            branch = abs(math.sin(ang * 6.0 + r * 4.5))
            crack_line = smoothstep(0.14, 0.02, spoke) * smoothstep(0.92, 0.18, r)
            sub_crack = smoothstep(0.11, 0.02, branch) * smoothstep(0.84, 0.25, r) * 0.75
            frost_bed = smoothstep(0.92, 0.22, r + (n - 0.5) * 0.34) * (0.45 + 0.55 * n)

            core_a = clamp(max(frost_bed * 0.72, crack_line * 0.95, sub_crack * 0.82))
            core_h = clamp(0.18 + 0.36 * frost_bed + 0.48 * max(crack_line, sub_crack * 0.85))
            glow_a = math.exp(-(math.hypot(rx, ry) / 0.62) ** 2) * 0.32 * smoothstep(0.98, 0.80, math.hypot(rx, ry))
            px[x, y] = pack_ramp_pixel(ICE_CRYSTAL_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 6. fire_wave_orb.png (1024x128, 8 frames of 128x128) --------------------

def make_fire_wave_orb() -> Image.Image:
    """
    Fire Wave Sword flying orb (爆炎·波动剑飞行炎核):
    Roaring inward-pulling magma vortex orb (4 swirling flame arms visually conveying its
    5-block capture pull!) with charred ember outer halo and incandescent molten heart.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.02:
                    continue
                ang = math.atan2(qy, qx)

                # Swirling 4-arm fiery vortex pulling inward
                n = fbm(qx * 4.8 - math.cos(phase), qy * 4.8 - math.sin(phase), f * 13 + 7, 3)
                swirl = math.sin(ang * 4.0 + r * 6.8 - phase * 2.0)
                arm_r = 0.78 + 0.14 * swirl + 0.16 * (n - 0.5)

                core_a = 0.0
                core_h = 0.10
                if r < arm_r:
                    u = clamp(1.0 - r / max(0.05, arm_r))
                    lick = clamp(0.55 + 0.45 * swirl + 0.35 * (n - 0.45))
                    core_a = clamp(smoothstep(0.0, 0.32, u) * (0.55 + 0.65 * lick))
                    hot_core = math.exp(-(r / 0.30) ** 2)
                    core_h = clamp(0.10 + 0.56 * u + 0.24 * lick + 0.48 * hot_core)

                # Orbiting molten ember sparks
                if 0.55 < r < 0.96:
                    sp = hash2(x // 2, y // 2, f * 37 + 17)
                    if sp > 0.974:
                        core_a = max(core_a, 0.94)
                        core_h = max(core_h, 0.92 if sp > 0.986 else 0.55)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.60) ** 2) * 0.56 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(FIRE_MAGMA_RAMP, core_h, core_a, 0.66, glow_a)

    return img


# --- 7. fire_pillar_eruption.png (1024x128, 8 frames of 128x128) -------------

def make_fire_pillar_eruption() -> Image.Image:
    """
    Classic DNF Fire Wave Sword towering volcanic lava-flame eruption pillar (爆炎冲天火柱):
    8 frames erupting skyward from ground (y=+1 bottom -> y=-1 top), billowing mushroom crown,
    spiral roaring lava core, charred smoke edges, and scattering fiery cinders.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Pillar shoots up rapidly in frames 0..2, swells & roars 3..5, shreds into sparks 6..7
        height = clamp(0.45 + 0.55 * ease_out_cubic(min(1.0, t * 2.6)))
        env = smoothstep(0.0, 0.14, t + 0.10) * (1.0 - 0.72 * smoothstep(0.62, 1.0, t))
        width_mult = 0.75 + 0.40 * math.sin(min(1.0, t * 1.4) * math.pi * 0.7)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                # Vertical progress from base (y=+0.95 -> v=0) to top (y=-0.95 -> v=1)
                vy = clamp((0.96 - qy) / 1.92)
                if vy > height + 0.16:
                    continue

                n = fbm(qx * 4.5, qy * 4.5 + t * 5.2, f * 19 + 5, 3)
                # Flared fiery base + sculpted column + billowing volcanic top crown
                crown = 0.26 * math.exp(-((vy - height * 0.82) / 0.22) ** 2)
                base_flare = 0.28 * math.exp(-(vy / 0.24) ** 2)
                col_w = (0.34 + crown + base_flare) * width_mult * (0.82 + 0.36 * n)

                # Spiral twisting flame tongues rising up the column
                twist = 0.10 * math.sin(vy * 9.0 - t * 10.0)
                dx = abs(qx - twist)

                core_a = 0.0
                core_h = 0.12
                if dx < col_w and vy < height + 0.06:
                    wu = clamp(1.0 - dx / max(0.03, col_w))
                    top_fade = smoothstep(height + 0.06, max(0.05, height - 0.22), vy)
                    base_fade = smoothstep(0.0, 0.08, vy)
                    breakup = 1.0
                    if t > 0.55 and n < (t - 0.45) * 1.35:
                        breakup = 0.15
                    core_a = clamp(smoothstep(0.0, 0.26, wu) * top_fade * base_fade * env * breakup * 1.32)
                    # Charred dark crimson rim -> roaring orange body -> blinding yellow-white vertical core!
                    flame_vein = 0.5 + 0.5 * math.sin(qx * 10.0 + vy * 8.0 - t * 12.0)
                    core_h = clamp(0.08 + 0.62 * (wu ** 0.72) + 0.22 * flame_vein * wu + 0.25 * math.exp(-(vy / 0.35) ** 2) * wu)

                # Rising fiery sparks flanking the eruption column
                if abs(qx) < 0.88 and vy < height + 0.14:
                    sp = hash2(x // 2, (y + f * 7) // 2, f * 29 + 13)
                    if sp > 0.976:
                        core_a = max(core_a, 0.92 * env)
                        core_h = max(core_h, 0.94 if sp > 0.988 else 0.58)

                rvy = clamp((0.96 - ry) / 1.92)
                glow_a = (
                    math.exp(-((rx / (0.48 * width_mult)) ** 2))
                    * smoothstep(height + 0.15, max(0.0, height - 0.25), rvy)
                    * 0.46 * env * smoothstep(0.99, 0.85, max(abs(rx), abs(ry)))
                )
                px[f * size + x, y] = pack_ramp_pixel(FIRE_MAGMA_RAMP, core_h, core_a, 0.64, glow_a)

    return img


# --- 8. fire_scorch_crater.png (256x256, 1 frame) ----------------------------

def make_fire_scorch_crater() -> Image.Image:
    """Charred obsidian blast crater with glowing molten lava fissures."""
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 96) - 0.5) * 2.0
            qy = (quantize(y / size, 96) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            r = math.hypot(qx, qy)
            if r > 1.02:
                continue
            ang = math.atan2(qy, qx)
            n = fbm(qx * 5.2, qy * 5.2, 313, 3)

            # Irregular scorched caldera perimeter
            edge = 0.88 + 0.14 * (n - 0.5)
            if r > edge:
                continue
            u = clamp(1.0 - r / edge)

            # 9 jagged radial lava cracks splitting outward from scorched crater center
            crack_a = abs(math.sin(ang * 4.5 + (n - 0.5) * 2.2))
            crack_b = abs(math.sin(ang * 7.0 - r * 3.8 + n * 2.0))
            vein = max(smoothstep(0.16, 0.02, crack_a), smoothstep(0.11, 0.02, crack_b) * 0.78) * smoothstep(0.92, 0.15, r)
            molten_pool = math.exp(-(r / 0.34) ** 2) * (0.65 + 0.35 * n)

            core_a = clamp(smoothstep(0.0, 0.30, u) * (0.65 + 0.35 * n))
            core_h = clamp(0.06 + 0.18 * u + 0.74 * vein + 0.55 * molten_pool)
            glow_a = clamp((vein * 0.45 + molten_pool * 0.52) * smoothstep(0.98, 0.78, math.hypot(rx, ry)))
            px[x, y] = pack_ramp_pixel(FIRE_MAGMA_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 9. agni_mandala.png (256x256, 1 frame) ----------------------------------

def make_agni_mandala() -> Image.Image:
    """
    Classic DNF Agni Pentacle / Immovable King Mandala (不动明王阵法阵):
    Sacred outer flame-petal wheel, carved ancient rune band, 8-petal blooming
    lotus crest, and fiery bronze-gold inner mandala ring.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            r = math.hypot(qx, qy)
            if r > 1.02:
                continue
            ang = math.atan2(qy, qx)

            core_a = 0.0
            core_h = 0.15

            # Outer sacred ring + 16 flame-petal tips
            dr1 = abs(r - 0.88)
            if dr1 < 0.036:
                u = 1.0 - dr1 / 0.036
                core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.96)
                core_h = max(core_h, 0.25 + 0.65 * u)

            petal16 = 0.86 + 0.11 * (abs(math.cos(ang * 8.0)) ** 1.4)
            if 0.84 < r < petal16:
                pu = 1.0 - (r - 0.84) / max(0.02, petal16 - 0.84)
                core_a = max(core_a, smoothstep(0.0, 0.3, pu) * 0.85)
                core_h = max(core_h, 0.32 + 0.50 * (1.0 - pu))

            # Second ring framing the ancient rune band (r in 0.68..0.85)
            dr2 = abs(r - 0.68)
            if dr2 < 0.030:
                u = 1.0 - dr2 / 0.030
                core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.95)
                core_h = max(core_h, 0.28 + 0.66 * u)

            if 0.70 < r < 0.85:
                # 12 symmetrical carved temple rune glyphs around the band
                slot = (ang + math.pi) / math.tau * 12.0
                si = int(math.floor(slot))
                su = (slot - si) * 2.0 - 1.0   # [-1, 1] angular within glyph cell
                sv = (r - 0.775) / 0.075       # [-1, 1] radial within glyph cell
                if abs(su) < 0.68 and abs(sv) < 0.72:
                    # Stylized sacred glyph strokes (crossbars + diagonal diamond + center dot)
                    bar1 = abs(sv) < 0.16 and abs(su) < 0.56
                    bar2 = abs(su) < 0.16 and abs(sv) < 0.60
                    diag = abs(abs(su) + abs(sv) - 0.56) < 0.14
                    if bar1 or bar2 or diag:
                        core_a = max(core_a, 0.94)
                        core_h = max(core_h, 0.74)

            # 8-petal blooming Immovable King Lotus (r in 0.22..0.67)!
            lotus_r = 0.34 + 0.32 * (abs(math.cos(ang * 4.0)) ** 1.25)
            lotus_edge = abs(r - lotus_r)
            if lotus_edge < 0.038:
                lu = 1.0 - lotus_edge / 0.038
                core_a = max(core_a, smoothstep(0.0, 0.3, lu) * 0.96)
                core_h = max(core_h, 0.34 + 0.62 * lu)
            elif 0.22 < r < lotus_r:
                # Warm glowing translucent golden lotus petal fill + center vein
                vein = smoothstep(0.08, 0.01, abs(math.sin(ang * 4.0)))
                fill_a = 0.34 + 0.38 * (r / lotus_r) + 0.35 * vein
                if fill_a > core_a:
                    core_a = fill_a
                    core_h = 0.22 + 0.45 * (r / lotus_r) + 0.32 * vein

            # Inner blazing lotus heart circle & sacred cross-star
            dr3 = abs(r - 0.22)
            if dr3 < 0.028:
                u = 1.0 - dr3 / 0.028
                core_a = max(core_a, 0.96)
                core_h = max(core_h, 0.55 + 0.45 * u)
            if r < 0.21:
                star = math.exp(-(r / 0.18) ** 2) * (0.65 + 0.35 * abs(math.cos(ang * 4.0)))
                core_a = max(core_a, clamp(star * 1.1))
                core_h = max(core_h, clamp(0.45 + 0.55 * star))

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-((rr - 0.78) / 0.14) ** 2) * 0.38
                + math.exp(-(rr / 0.42) ** 2) * 0.36
            ) * smoothstep(0.99, 0.86, rr)
            px[x, y] = pack_ramp_pixel(AGNI_FLAME_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 10. agni_flame_orb.png (512x128, 4 frames of 128x128) -------------------

def make_agni_flame_orb() -> Image.Image:
    """
    Orbiting Agni Flame Bead (不动明王炎珠):
    Blazing golden-vermilion flame sphere with rotating flame corona and glowing temple-seal core.
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.02:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.0 + math.cos(phase), qy * 5.0 + math.sin(phase), f * 17 + 23, 2)

                # Blazing corona with 6 swirling flame licks
                corona_r = 0.72 + 0.16 * math.sin(ang * 6.0 - phase * 2.0) + 0.12 * (n - 0.5)
                core_a = 0.0
                core_h = 0.12
                if r < corona_r:
                    u = clamp(1.0 - r / max(0.05, corona_r))
                    core_a = clamp(smoothstep(0.0, 0.32, u) * 1.12)
                    core_h = clamp(0.14 + 0.58 * (u ** 0.72) + 0.38 * math.exp(-(r / 0.32) ** 2))

                # Crisp white-gold inner brand cross inside the flame bead
                if r < 0.36:
                    rot_a = ang + phase * 0.5
                    cross = max(
                        smoothstep(0.14, 0.02, abs(math.sin(rot_a * 2.0))),
                        smoothstep(0.06, 0.01, abs(r - 0.24)),
                    )
                    if cross > 0.2:
                        core_a = max(core_a, 0.98)
                        core_h = max(core_h, 0.78 + 0.22 * cross)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.58) ** 2) * 0.58 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(AGNI_FLAME_RAMP, core_h, core_a, 0.70, glow_a)

    return img


# --- 11. agni_lotus_burst.png (1024x128, 8 frames of 128x128) ----------------

def make_agni_lotus_burst() -> Image.Image:
    """
    Agni Finisher Blazing Lotus Explosion (不动明王阵终结·明王莲华大爆):
    8 frames: blinding collapse core -> gigantic 8-petal blazing golden lotus shockwave
    erupting outward with radiant flame spears -> scattering gold-crimson petals & embers.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        exp_r = 0.26 + 0.70 * ease_out_cubic(t)
        env = smoothstep(0.0, 0.10, t + 0.08) * (1.0 - 0.78 * smoothstep(0.55, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.02:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.2, qy * 5.2, f * 23 + 31, 2)

                # Expanding 8-petal golden lotus ring
                petal = exp_r * (0.72 + 0.30 * (abs(math.cos(ang * 4.0 + t * 0.4)) ** 1.35))
                inner_hole = max(0.0, exp_r - 0.36 * (1.0 - 0.55 * t))

                core_a = 0.0
                core_h = 0.15
                if inner_hole <= r <= petal:
                    ru = clamp((r - inner_hole) / max(0.03, petal - inner_hole))
                    shred = 1.0 if (t < 0.55 or n > (t - 0.45) * 1.35) else 0.16
                    core_a = clamp(smoothstep(0.0, 0.24, ru) * smoothstep(1.0, 0.80, ru) * env * shred * 1.35)
                    core_h = clamp(0.18 + 0.58 * ru + 0.30 * smoothstep(0.65, 0.96, ru))

                # Radiant 8-way solar spear rays bursting from center in early frames
                if t < 0.65 and r < exp_r * 1.08:
                    ray = smoothstep(0.16, 0.02, abs(math.sin(ang * 4.0)))
                    ra = ray * (1.0 - r / max(0.1, exp_r * 1.08)) * (1.0 - t / 0.65) * 0.95
                    if ra > core_a:
                        core_a = ra
                        core_h = max(core_h, 0.85)

                # White-hot flash core in frames 0..2
                if t < 0.42:
                    flash = math.exp(-(r / (0.34 * (1.0 - 0.5 * t))) ** 2) * (1.0 - t / 0.42)
                    if flash > 0.15:
                        core_a = max(core_a, clamp(flash * 1.2))
                        core_h = max(core_h, 0.96)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr - exp_r * 0.82) / 0.18) ** 2) * 0.52 * env
                    + math.exp(-(rr / 0.48) ** 2) * 0.48 * max(0.0, 1.0 - t * 1.3)
                ) * smoothstep(0.99, 0.86, rr)
                px[f * size + x, y] = pack_ramp_pixel(AGNI_FLAME_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 12. wave_eye_iris.png (1024x128, 8 frames of 128x128) -------------------

def make_wave_eye_iris() -> Image.Image:
    """
    Signature DNF Dark Sky Wave Eye (大暗黑天·暗天波动眼巨眼):
      - Frame 0: Dark closed purple-lightning eyelid slit leaking golden radiance
      - Frames 1..2: Majestic eye opening smoothly
      - Frames 3..5: Fully open imperial Wave Eye (sharp swept dark-violet contour,
                     luminous lilac sclera, royal-purple arcane iris, imperial GOLDEN slit pupil!)
      - Frame 6: Finisher overcharge — slit pupil constricts razor-thin, golden-violet shock halo
      - Frame 7: Shattering detonation — web of blazing violet-gold lightning cracks bursting open!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    open_curve = (0.08, 0.42, 0.78, 1.00, 1.04, 0.98, 1.08, 1.12)

    for f in range(frames):
        openness = open_curve[f]
        overcharge = 1.0 if f == 6 else 0.0
        shatter = 1.0 if f == 7 else 0.0
        breathe = 0.5 + 0.5 * math.sin(f * 1.35)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                ax = abs(qx)
                # Sleek swept demonic almond eye contour with sharp tapered outer corners cleanly inside frame!
                t_eye = clamp(ax / 0.84)
                max_h = max(0.015, 0.48 * openness * ((1.0 - t_eye * t_eye) ** 1.05))
                # Subtle fierce upward flare toward outer corners
                corner_lift = -0.07 * (t_eye ** 2.0)
                ey = qy - corner_lift
                aey = abs(ey)

                c_rgb = (16.0, 6.0, 34.0)
                core_a = 0.0
                g_rgb = (156.0, 82.0, 240.0)
                glow_a = 0.0

                # 1. Sleek shadowy dark-violet eyelid outline & swept corner wings (zero frame-edge clipping!)
                lash_pad = (0.12 * (1.0 - 0.55 * t_eye) + 0.03 * math.sin(ax * 9.0 + f * 0.7)) * smoothstep(0.92, 0.72, ax)
                lash_w = max_h + lash_pad
                if ax < 0.90 and aey < lash_w:
                    if aey >= max_h:
                        lu = clamp(1.0 - (aey - max_h) / max(0.025, lash_w - max_h))
                        core_a = smoothstep(0.0, 0.35, lu) * smoothstep(0.90, 0.78, ax) * 0.96
                        c_rgb = (
                            lerp(18.0, 152.0, lu ** 1.35),
                            lerp(5.0, 70.0, lu ** 1.35),
                            lerp(36.0, 236.0, lu ** 1.35),
                        )
                    else:
                        # INSIDE THE OPEN EYE!
                        core_a = 0.98
                        r_iris = math.hypot(qx, ey)
                        ang = math.atan2(ey, qx)

                        # Sclera (pale spectral lilac-white with shadowy dark-violet lid shading)
                        lid_shade = clamp(aey / max(0.03, max_h))
                        sc_r = lerp(228.0, 96.0, lid_shade ** 1.8)
                        sc_g = lerp(208.0, 44.0, lid_shade ** 1.8)
                        sc_b = lerp(255.0, 168.0, lid_shade ** 1.8)
                        c_rgb = (sc_r, sc_g, sc_b)

                        # Arcane Royal-Purple & Gold-Rimmed Iris (radius ~0.38)
                        iris_r = 0.39 if f < 6 else 0.42
                        if r_iris < iris_r:
                            iu = r_iris / iris_r
                            rays = 0.5 + 0.5 * math.sin(ang * 16.0 + f * 0.5)
                            if iu > 0.84:
                                c_rgb = (246.0, 188.0, 74.0) if (f >= 3) else (132.0, 58.0, 218.0)
                            else:
                                c_rgb = (
                                    lerp(136.0, 62.0, iu) + 38.0 * rays,
                                    lerp(56.0, 18.0, iu) + 18.0 * rays,
                                    lerp(232.0, 130.0, iu) + 25.0 * rays,
                                )

                        # Imperial GOLDEN Vertical Slit Pupil (signature DNF Asura Wave Eye!)
                        slit_rx = 0.125 if f < 6 else (0.062 if f == 6 else 0.15)
                        slit_ry = 0.32 if f < 6 else 0.36
                        slit_d = math.hypot(qx / slit_rx, ey / slit_ry)
                        if slit_d < 1.12:
                            if slit_d > 0.88:
                                c_rgb = (22.0, 7.0, 42.0)
                            else:
                                su = 1.0 - slit_d / 0.88
                                c_rgb = (
                                    255.0,
                                    lerp(186.0, 250.0, su ** 0.7),
                                    lerp(42.0, 214.0, su ** 1.3),
                                )

                        # Crisp white-gold specular catchlight in upper-left of iris
                        spec_d = math.hypot(qx + 0.13, ey + 0.12)
                        if spec_d < 0.075 and openness > 0.4:
                            c_rgb = (255.0, 252.0, 238.0)

                # Frame 0 closed slit golden light leak
                if f == 0 and ax < 0.82 and aey < 0.042:
                    su = (1.0 - aey / 0.042) * (1.0 - ax / 0.82)
                    core_a = max(core_a, 0.96)
                    c_rgb = (255.0, lerp(165.0, 245.0, su), lerp(68.0, 210.0, su))

                # Frame 6 overcharge golden-violet cross star flare cleanly bounded inside frame
                if overcharge > 0.5:
                    edge_guard = smoothstep(0.92, 0.65, max(ax, aey))
                    star_h = math.exp(-abs(ey) / 0.050) * math.exp(-ax / 0.60) * edge_guard
                    star_v = math.exp(-ax / 0.050) * math.exp(-abs(ey) / 0.52) * edge_guard
                    sf = clamp(max(star_h, star_v))
                    if sf > 0.16:
                        core_a = max(core_a, sf)
                        c_rgb = (
                            lerp(c_rgb[0], 255.0, sf),
                            lerp(c_rgb[1], 238.0, sf),
                            lerp(c_rgb[2], 172.0, sf),
                        )

                # Frame 7 shattering detonation lightning fractures tearing across the whole eye!
                if shatter > 0.5:
                    ang_s = math.atan2(ey, qx)
                    r_s = math.hypot(qx, ey)
                    n_s = fbm(qx * 7.5, ey * 7.5, 907, 2)
                    crack = smoothstep(0.11, 0.015, abs(math.sin(ang_s * 4.5 + (n_s - 0.5) * 2.4))) * smoothstep(0.88, 0.10, r_s)
                    if crack > 0.20:
                        core_a = max(core_a, 0.99)
                        c_rgb = (255.0, lerp(214.0, 255.0, crack), lerp(112.0, 255.0, crack))
                    elif core_a > 0.5 and n_s < 0.36:
                        core_a *= 0.22

                # Outer arcane purple-gold glow aura framing the eye cleanly inside frame
                er = math.hypot(rx / 0.78, (ry - corner_lift) / max(0.26, 0.52 * openness))
                glow_a = math.exp(-(er / 0.92) ** 2) * (0.44 + 0.18 * overcharge + 0.26 * shatter + 0.06 * breathe)
                glow_a *= smoothstep(0.96, 0.80, max(abs(rx), abs(ry)))
                if overcharge > 0.5 or shatter > 0.5:
                    g_rgb = (232.0, 178.0, 255.0)
                px[f * size + x, y] = pack_rgb_blend(c_rgb, core_a, g_rgb, glow_a)

    return img


# --- 13. wave_eye_domain.png (256x256, 1 frame) ------------------------------

def make_wave_eye_domain() -> Image.Image:
    """
    Heavy Abyssal Dark Sky Ground Domain (暗天波动眼重度暗黑覆地结界):
    Deep pitch-violet void ground shroud recreating DNF's iconic 'sky goes dark'
    oppression across the 16-block blind zone, framed by arcane purple runes,
    8 radiating thunder-veins, and swirling shadow tendrils.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            r = math.hypot(qx, qy)
            if r > 1.01:
                continue
            ang = math.atan2(qy, qx)
            n = fbm(qx * 4.5, qy * 4.5, 409, 3)

            # Heavy abyssal dark-violet shroud covering the entire domain (thick dark clouding!)
            swirl = 0.5 + 0.5 * math.sin(ang * 4.0 + r * 5.6 + (n - 0.5) * 2.6)
            edge_fade = smoothstep(1.00, 0.89, r)
            shroud_a = clamp(edge_fade * (0.72 + 0.18 * r + 0.12 * swirl + 0.12 * (n - 0.5)))
            core_a = shroud_a
            core_h = clamp(0.02 + 0.15 * swirl * (0.45 + 0.55 * r) + 0.08 * n)

            # Bold glowing dark-violet arcane barrier ring at r = 0.90 and inner 8-block Murderous Wave ring at r = 0.50!
            dr_out = abs(r - 0.90)
            if dr_out < 0.036:
                u = 1.0 - dr_out / 0.036
                core_a = max(core_a, 0.98)
                core_h = max(core_h, 0.34 + 0.52 * u)

            dr_mid = abs(r - 0.50)
            if dr_mid < 0.024:
                u = 1.0 - dr_mid / 0.024
                core_a = max(core_a, 0.94)
                core_h = max(core_h, 0.38 + 0.44 * u)

            # 16 outer domain rune notches between r = 0.78 and 0.88
            if 0.78 < r < 0.88:
                slot = (ang + math.pi) / math.tau * 16.0
                su = (slot - math.floor(slot)) * 2.0 - 1.0
                sv = (r - 0.83) / 0.05
                if abs(su) < 0.55 and abs(sv) < 0.65:
                    if abs(su) < 0.16 or abs(sv) < 0.20:
                        core_a = max(core_a, 0.96)
                        core_h = max(core_h, 0.68)

            # 8 radiating void thunder-veins from center to outer barrier
            vein = smoothstep(0.055, 0.01, abs(math.sin(ang * 4.0 + (n - 0.5) * 0.45))) * smoothstep(0.90, 0.12, r) * smoothstep(0.05, 0.18, r)
            if vein > 0.2:
                core_a = max(core_a, 0.90)
                core_h = max(core_h, 0.42 + 0.44 * vein)

            # Subtle central golden-purple eye sigil
            if r < 0.20:
                almond = abs(qy) < 0.11 * max(0.0, 1.0 - (abs(qx) / 0.19) ** 1.5)
                if almond and abs(abs(qy) - 0.10 * max(0.0, 1.0 - (abs(qx) / 0.19) ** 1.5)) < 0.025:
                    core_a = max(core_a, 0.96)
                    core_h = max(core_h, 0.90)

            rr = math.hypot(rx, ry)
            glow_a = math.exp(-((rr - 0.90) / 0.09) ** 2) * 0.38 * smoothstep(1.0, 0.93, rr)
            px[x, y] = pack_ramp_pixel(EYE_VOID_RAMP, core_h, core_a, 0.64, glow_a)

    return img


# --- 14. dark_void_mist.png (512x128, 4 frames of 128x128) -------------------

def make_dark_void_mist() -> Image.Image:
    """4-frame heavy billowing abyssal dark-violet void cloud with eerie glowing violet wisps."""
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        ox, oy = math.cos(phase) * 0.7, math.sin(phase) * 0.7
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                r = math.hypot(qx, qy)
                if r > 0.98:
                    continue
                n1 = fbm(qx * 2.6 + ox, qy * 2.6 + oy, 503 + f * 7, 3)
                n2 = fbm(qx * 5.0 - oy, qy * 5.0 + ox, 619 + f * 11, 2)
                billow = smoothstep(0.96, 0.20, r + (n1 - 0.5) * 0.34) * (0.68 + 0.55 * n2)
                core_a = clamp(billow * 0.94)
                core_h = clamp(0.04 + 0.32 * n2 * smoothstep(0.90, 0.12, r) + 0.14 * (1.0 - r))

                # Glowing violet void motes inside the dense mist
                if r < 0.78 and hash2(x // 2, y // 2, f * 53 + 17) > 0.984:
                    core_a = max(core_a, 0.92)
                    core_h = 0.84

                px[f * size + x, y] = pack_ramp_pixel(EYE_VOID_RAMP, core_h, core_a, 0.38, 0.0)

    return img


# --- 15. wave_eye_gear.png (512x128, 4 frames of 128x128) --------------------

def make_wave_eye_gear() -> Image.Image:
    """
    Wave Eye Basic Attack Spinning Scythe-Wheel (暗天波动眼·旋刃车轮/光轮):
    6 curved razor scythe blades orbiting an arcane violet gear ring with a glowing
    golden slit-eye hub — replacing the old 20-line zigzag wireframe!
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / (6.0 * frames)) * 2.0
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.02:
                    continue
                ang = math.atan2(qy, qx)

                core_a = 0.0
                core_h = 0.15

                # 6 curved forward-sweeping razor scythe teeth (r in 0.48..0.94)
                if 0.42 < r < 0.95:
                    # Twist angle radially so blades sweep backward like a wicked saw-chakram
                    tooth_phase = ((ang - phase - (r - 0.42) * 1.45) * 6.0 / math.tau) % 1.0
                    # Sharp asymmetrical scythe profile: razor leading edge near tooth_phase=0.1, tapered tail
                    taper = clamp((0.95 - r) / 0.53)
                    blade_w = 0.12 + 0.52 * (taper ** 0.85)
                    if tooth_phase < blade_w:
                        u = 1.0 - tooth_phase / max(0.02, blade_w)  # 1 = razor front edge, 0 = trailing back
                        core_a = max(core_a, smoothstep(0.0, 0.25, u) * smoothstep(0.95, 0.85, r) * 0.96)
                        core_h = max(core_h, 0.14 + 0.68 * (u ** 0.75) + 0.18 * smoothstep(0.75, 0.94, r))

                # Inner structural chakra ring (r = 0.46)
                dr = abs(r - 0.46)
                if dr < 0.08:
                    u = 1.0 - dr / 0.08
                    core_a = max(core_a, smoothstep(0.0, 0.3, u) * 0.98)
                    core_h = max(core_h, 0.26 + 0.62 * u)

                # 3 crossed energy spokes inside hub
                if r < 0.45:
                    sp_d = abs(math.sin((ang - phase) * 3.0))
                    if sp_d < 0.16:
                        su = 1.0 - sp_d / 0.16
                        core_a = max(core_a, 0.92)
                        core_h = max(core_h, 0.42 + 0.45 * su)

                # Mini golden slit-eye core at center (r < 0.22)
                if r < 0.22:
                    core_a = max(core_a, 0.98)
                    slit = math.hypot(qx / 0.075, qy / 0.17)
                    core_h = max(core_h, 0.96 if slit < 0.85 else 0.32)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr - 0.68) / 0.20) ** 2) * 0.44
                    + math.exp(-(rr / 0.32) ** 2) * 0.42
                ) * smoothstep(0.99, 0.86, rr)
                px[f * size + x, y] = pack_ramp_pixel(EYE_VOID_RAMP, core_h, core_a, 0.66, glow_a)

    return img


# --- 16. wave_eye_wing.png (1024x128, 8 frames of 128x128) -------------------

def make_wave_eye_wing() -> Image.Image:
    """
    Wave Eye Variant — Wings of Light (暗天波动眼·光翼, 地裂波动剑变招):
    Pair of sweeping razor-sharp lavender-cyan spectral wings arcing forward (+qx)
    with layered feather blades, dark-violet outer edge, and blazing white light spines.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        flap = 0.08 * math.sin(phase)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                ay = abs(qy)
                core_a = 0.0
                core_h = 0.14
                glow_a = 0.0

                # Twin symmetrical swept wing blades bowing forward at nose (+0.78) and fanning to |qy|=0.95
                if ay < 0.96:
                    # 4 overlapping feather-blade tiers along each wing
                    lead_x = 0.72 - 0.92 * (ay ** 1.35) + flap * ay
                    feather_serration = 0.14 * abs(math.sin(ay * 10.5 - phase * 0.5)) * smoothstep(0.10, 0.50, ay)
                    trail_x = lead_x - (0.36 + 0.24 * (1.0 - ay * 0.55) + feather_serration)
                    if trail_x <= qx <= lead_x:
                        u = clamp((qx - trail_x) / max(0.03, lead_x - trail_x))
                        # Feather rib striations running lengthwise
                        ribs = 0.65 + 0.35 * math.sin(ay * 22.0 - u * 4.0)
                        tip_fade = smoothstep(0.96, 0.72, ay)
                        core_a = clamp(smoothstep(0.0, 0.24, u) * smoothstep(1.0, 0.88, u) * tip_fade * (0.76 + 0.32 * ribs) * 1.25)
                        razor = smoothstep(0.68, 0.96, u)
                        core_h = clamp(0.14 + 0.50 * u + 0.42 * razor + 0.18 * math.exp(-(ay / 0.22) ** 2))

                # Bright central V-crest at the nose of the Light Wings
                r_nose = math.hypot((qx - 0.58) * 1.4, qy)
                if r_nose < 0.30:
                    nf = math.exp(-(r_nose / 0.18) ** 2)
                    core_a = max(core_a, clamp(nf * 1.15))
                    core_h = max(core_h, clamp(0.75 + 0.25 * nf))

                ray = abs(ry)
                if ray < 0.98:
                    g_lead = 0.64 - 0.88 * (ray ** 1.35)
                    glow_a = math.exp(-((rx - g_lead) / 0.24) ** 2) * smoothstep(0.98, 0.72, ray) * 0.48
                px[f * size + x, y] = pack_ramp_pixel(EYE_WING_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 17. wave_eye_spear.png (1024x128, 8 frames of 128x128) ------------------

def make_wave_eye_spear() -> Image.Image:
    """
    Wave Eye Variant — Piercing Thorns & Flashing Spear (暗天波动眼·穿云刺轮 / 天照闪枪):
    8 frames of a razor-sharp dark-violet & incandescent white-gold lightning spear
    erupting upward along Y (base y=+0.95 -> tip y=-0.92), wrapped in thorny barbs
    and helical lightning arcs before shattering into violet-gold sparks!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        thrust = clamp(0.40 + 0.60 * ease_out_cubic(min(1.0, t * 2.8)))
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.74 * smoothstep(0.58, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                vy = clamp((0.95 - qy) / 1.88)  # 0=base, 1=tip
                if vy > thrust + 0.10:
                    continue

                core_a = 0.0
                core_h = 0.14

                # Razor diamond spear shaft + broad barbed spearhead near upper half
                head_flare = 0.22 * math.exp(-((vy - thrust * 0.68) / 0.16) ** 2)
                taper_tip = clamp((thrust - vy) / max(0.05, thrust * 0.38))
                shaft_w = (0.14 + head_flare) * min(1.0, taper_tip * 1.65) * (0.75 + 0.25 * smoothstep(0.0, 0.18, vy))
                ax = abs(qx)

                if ax < shaft_w and vy <= thrust:
                    wu = 1.0 - ax / max(0.02, shaft_w)
                    n = fbm(qx * 6.0, qy * 6.0, f * 17 + 41, 2)
                    breakup = 1.0 if (t < 0.58 or n > (t - 0.48) * 1.35) else 0.15
                    core_a = clamp(smoothstep(0.0, 0.25, wu) * env * breakup * 1.25)
                    # Dark violet sharp bevel edges -> glowing royal purple -> blinding gold-white core spine!
                    core_h = clamp(0.14 + 0.62 * (wu ** 0.70) + 0.28 * smoothstep(0.60, 0.95, wu))

                # Twin thorny cross-barbs jutting out from mid-shaft (iconic Piercing Wheel / Flashing Spear look)
                for barb_y, barb_span in ((thrust * 0.38, 0.48), (thrust * 0.60, 0.36)):
                    by_world = 0.95 - barb_y * 1.88
                    for sgn in (-1.0, 1.0):
                        d_b, bt = seg_dist(qx, qy, 0.0, by_world + 0.10, sgn * barb_span, by_world - 0.16)
                        bw = 0.075 * (1.0 - 0.72 * bt)
                        if d_b < bw:
                            bu = 1.0 - d_b / max(0.015, bw)
                            ba = smoothstep(0.0, 0.35, bu) * env * 0.94
                            if ba > core_a:
                                core_a = ba
                                core_h = 0.28 + 0.66 * bu

                # Spiral electric coil wrapping the spear shaft
                helix_x = math.sin(vy * 14.0 - t * 9.0) * 0.26 * (1.0 - 0.5 * vy)
                dh = abs(qx - helix_x)
                if dh < 0.045 and vy < thrust * 0.94:
                    hu = 1.0 - dh / 0.045
                    ha = smoothstep(0.0, 0.3, hu) * env * 0.92
                    if ha > core_a:
                        core_a = ha
                        core_h = max(core_h, 0.80 + 0.20 * hu)

                rvy = clamp((0.95 - ry) / 1.88)
                glow_a = (
                    math.exp(-((rx / 0.34) ** 2))
                    * smoothstep(thrust + 0.10, max(0.0, thrust - 0.20), rvy)
                    * 0.48 * env * smoothstep(0.99, 0.86, max(abs(rx), abs(ry)))
                )
                px[f * size + x, y] = pack_ramp_pixel(EYE_VOID_RAMP, core_h, core_a, 0.70, glow_a)

    return img


# --- Contact Sheet Preview Generator -----------------------------------------

def save_contact_sheet(assets: dict[str, Image.Image], out_path: Path, bg=(12, 14, 24, 255)) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    pad = 14
    label_h = 18
    max_w = max(min(img.width, 1024) for img in assets.values())
    row_heights = [min(img.height, 256) + label_h + pad for img in assets.values()]
    total_h = sum(row_heights) + pad
    sheet = Image.new("RGBA", (max_w + pad * 2, total_h), bg)
    draw = ImageDraw.Draw(sheet)

    y_cur = pad
    for name, img in assets.items():
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(214, 198, 255, 255))
        y_cur += label_h
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(58, 52, 88, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "wave_brand_rune.png": make_wave_brand_rune(),
        "ghost_orb_core.png": make_ghost_orb_core(),
        "spirit_crescent_wave.png": make_spirit_crescent_wave(),
        "ice_wave_spike.png": make_ice_wave_spike(),
        "ice_frost_trail.png": make_ice_frost_trail(),
        "fire_wave_orb.png": make_fire_wave_orb(),
        "fire_pillar_eruption.png": make_fire_pillar_eruption(),
        "fire_scorch_crater.png": make_fire_scorch_crater(),
        "agni_mandala.png": make_agni_mandala(),
        "agni_flame_orb.png": make_agni_flame_orb(),
        "agni_lotus_burst.png": make_agni_lotus_burst(),
        "wave_eye_iris.png": make_wave_eye_iris(),
        "wave_eye_domain.png": make_wave_eye_domain(),
        "dark_void_mist.png": make_dark_void_mist(),
        "wave_eye_gear.png": make_wave_eye_gear(),
        "wave_eye_wing.png": make_wave_eye_wing(),
        "wave_eye_spear.png": make_wave_eye_spear(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "asura_contact_sheet.png")


if __name__ == "__main__":
    main()

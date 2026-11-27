#!/usr/bin/env python3
"""
Dedicated DNF-inspired Elementalist / Archmage (元素师 / 大魔导师)
ultra-spectacle pixel-sharp + soft-feathered VFX texture generator.

Outputs pre-baked multi-tone RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/elementalist/
and a labeled contact sheet preview under:
  temp/vfx-preview/elementalist_contact_sheet.png

Design direction (Classic DNF Grand Archmage High-Contrast Multi-Tone, Dual-Layer Ready):
  - Elemental Burn Lamps (圣灵符文四灯): 4 distinct ornate elemental medallions (Light/Fire/Ice/Dark)
  - Moving Cast Charge Circle (移动施法蓄力/瞄准阵): Crisp tintable arcane hexagram circle
  - Ice Wall & Arctic Feast (冰墙 / 极冰盛宴): Deep glacial blue -> faceted crystal cyan -> frosted pure white
  - Lightning Vortex & Lightning Strike (雷旋 / 天雷): Abyssal storm indigo -> electric cyan-gold -> blinding white-gold thunder
  - Void Sphere & Black Hole (虚无之球 / 湮灭黑洞): True pitch-black singularity -> deep abyssal violet -> neon magenta-lilac lensing
  - Flame Pillar & Jack-O'-Lantern (烈焰冲击 / 杰克爆弹 / 杰克降临):
      Charred volcanic obsidian -> molten crimson -> roaring flame orange -> white-hot heart
      + Iconic DNF Flaming Laughing Jack-O'-Lantern (orange ribbed pumpkin + emerald stem + blazing evil eyes & sawtooth grin)
  - Astral Storm Awakening (陨星幻灭):
      4-element grand starlight circle, overhead cosmic astrolabe rift, 4 bespoke elemental meteors,
      colossal 4-element Chaotic Main Star Core, and prismatic celestial supernova burst!
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/elementalist"
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


# --- Classic DNF Elementalist High-Contrast Multi-Tone Ramps -----------------

LIGHT_THUNDER_RAMP = (
    (0.00, (34, 22, 6)),      # dark storm bronze
    (0.18, (118, 76, 12)),    # deep amber gold
    (0.42, (226, 168, 28)),   # radiant thunder gold
    (0.66, (255, 220, 92)),   # luminous DNF lightning gold
    (0.84, (214, 246, 255)),  # high-voltage electric cyan-white
    (1.00, (255, 255, 252)),  # blinding thunder core
)

FIRE_VOLCANO_RAMP = (
    (0.00, (24, 6, 4)),       # charred obsidian soot
    (0.18, (116, 18, 8)),     # deep volcanic crimson
    (0.42, (216, 56, 12)),    # roaring flame vermilion
    (0.66, (255, 130, 24)),   # incandescent magma orange
    (0.86, (255, 206, 72)),   # scorching golden flame
    (1.00, (255, 248, 214)),  # white-hot plasma heart
)

ICE_GLACIAL_RAMP = (
    (0.00, (8, 22, 56)),      # abyssal glacial navy
    (0.18, (22, 76, 158)),    # deep sapphire frost shadow
    (0.44, (52, 158, 238)),   # vivid arctic crystal blue
    (0.68, (128, 224, 255)),  # luminous ice cyan
    (0.86, (214, 248, 255)),  # frosted crystal highlight
    (1.00, (252, 255, 255)),  # pure ice specular edge
)

DARK_VOID_RAMP = (
    (0.00, (5, 2, 12)),       # absolute abyssal pitch black
    (0.16, (24, 8, 52)),      # deep dark-void indigo
    (0.38, (78, 24, 150)),    # heavy shadow violet
    (0.62, (148, 66, 236)),   # radiant void purple
    (0.82, (214, 150, 255)),  # neon lilac lensing rim
    (1.00, (250, 240, 255)),  # blinding gravity flash
)

ASTRAL_COSMIC_RAMP = (
    (0.00, (10, 6, 28)),      # deep midnight cosmos
    (0.20, (44, 20, 108)),    # royal star-nebula indigo
    (0.44, (118, 68, 216)),   # luminous astral amethyst
    (0.66, (186, 146, 255)),  # prismatic starlight lilac
    (0.84, (255, 218, 132)),  # celestial astrolabe gold
    (1.00, (255, 252, 242)),  # blazing star heart
)

LUMINANCE_RAMP = (
    (0.00, (42, 44, 52)),     # crisp dark rim
    (0.22, (112, 118, 128)),  # shadowed body
    (0.50, (192, 198, 208)),  # mid body
    (0.78, (238, 242, 248)),  # bright rim
    (1.00, (255, 255, 255)),  # pure white core
)

PUMPKIN_STEM_RAMP = (
    (0.00, (14, 34, 12)),
    (0.35, (36, 98, 28)),
    (0.70, (82, 168, 48)),
    (1.00, (164, 232, 96)),
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
    glow_heat: float = 0.58, glow_a: float = 0.0,
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


# --- 1. rune_lamp_orb.png (512x128, 4 frames: 0=Light, 1=Fire, 2=Ice, 3=Dark) --

def make_rune_lamp_orb() -> Image.Image:
    """
    Classic DNF Elemental Burn (元素点燃 / 圣灵符文四灯):
    4 distinct ornate elemental rune medallions:
      Frame 0 (Light): Radiant golden sun-bolt crest
      Frame 1 (Fire):  Blazing crimson-orange triple flame crest
      Frame 2 (Ice):   Crystalline azure 6-point snowflake crest
      Frame 3 (Dark):  Abyssal royal-violet crescent eclipse crest
    """
    frames, size = 4, 128
    ramps = (LIGHT_THUNDER_RAMP, FIRE_VOLCANO_RAMP, ICE_GLACIAL_RAMP, DARK_VOID_RAMP)
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        ramp = ramps[f]
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)

                core_a = 0.0
                core_h = 0.20

                # Ornate elemental outer medallion frame (r = 0.72)
                dr = abs(r - 0.72)
                if dr < 0.09:
                    notch = 0.55 + 0.45 * math.cos(ang * 4.0)
                    rw = 0.052 + 0.028 * notch
                    if dr < rw:
                        u = 1.0 - dr / rw
                        core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.96)
                        core_h = max(core_h, 0.16 + 0.72 * u)

                # 4 cardinal filigree points extending to r = 0.94
                card = abs(angle_wrap(ang * 4.0)) / 4.0
                if 0.68 < r < 0.94:
                    w = 0.14 * (1.0 - (r - 0.68) / 0.27)
                    if card < w:
                        u = 1.0 - card / max(0.01, w)
                        a_sp = smoothstep(0.0, 0.35, u) * 0.94
                        if a_sp > core_a:
                            core_a = a_sp
                            core_h = 0.26 + 0.68 * u

                # Luminous translucent orb core backdrop (r < 0.60)
                if r < 0.60:
                    u = 1.0 - r / 0.60
                    ba = smoothstep(0.0, 0.26, u) * 0.88
                    bh = 0.14 + 0.56 * (u ** 0.78)
                    if ba > core_a:
                        core_a = ba
                        core_h = bh

                # Distinct elemental interior emblem (r < 0.50)
                emblem_a = 0.0
                emblem_h = 0.0
                if r < 0.52:
                    if f == 0:
                        # Light: crisp zigzag lightning bolt + slim 4-point star flare
                        d1, _ = seg_dist(qx, qy, 0.10, -0.38, -0.14, 0.01)
                        d2, _ = seg_dist(qx, qy, -0.14, 0.01, 0.14, -0.01)
                        d3, _ = seg_dist(qx, qy, 0.14, -0.01, -0.10, 0.38)
                        d_bolt = min(d1, d2, d3)
                        if d_bolt < 0.085:
                            bu = 1.0 - d_bolt / 0.085
                            emblem_a = max(emblem_a, smoothstep(0.0, 0.35, bu))
                            emblem_h = max(emblem_h, 0.72 + 0.28 * bu)
                        # Slim 4-point starburst behind bolt
                        star = abs(qx) * abs(qy)
                        if star < 0.014 and r < 0.44:
                            su = (1.0 - star / 0.014) * smoothstep(0.44, 0.06, r)
                            emblem_a = max(emblem_a, su * 0.86)
                            emblem_h = max(emblem_h, 0.66 + 0.34 * su)
                    elif f == 1:
                        # Fire: sleek pointed DNF flame teardrop with twin curling side tongues + hollow hot heart
                        fy = (0.36 - qy) / 0.76  # 0 at bottom, 1 at pointed tip
                        if 0.0 <= fy <= 1.0:
                            sway = 0.07 * math.sin(fy * 4.5) * fy
                            half_w = 0.32 * (math.sin(fy * math.pi) ** 0.72) * (1.0 - 0.52 * fy)
                            dx_f = abs(qx - sway)
                            if dx_f < half_w:
                                fu = 1.0 - dx_f / max(0.01, half_w)
                                # Inner darker ember pocket near lower center for iconic hollow-flame silhouette
                                inner_cut = math.hypot((qx - sway * 0.4) / 0.11, (qy - 0.16) / 0.14)
                                if inner_cut > 0.85:
                                    emblem_a = max(emblem_a, smoothstep(0.0, 0.30, fu))
                                    emblem_h = max(emblem_h, 0.74 + 0.26 * fu)
                                else:
                                    emblem_a = max(emblem_a, 0.92)
                                    emblem_h = max(emblem_h, 0.36 + 0.25 * inner_cut)
                        for sgn in (-1.0, 1.0):
                            d_t, tt = seg_dist(qx, qy, sgn * 0.18, 0.26, sgn * 0.34, -0.08)
                            tw = 0.072 * (1.0 - 0.75 * tt)
                            if d_t < tw:
                                tu = 1.0 - d_t / max(0.01, tw)
                                emblem_a = max(emblem_a, smoothstep(0.0, 0.35, tu) * 0.95)
                                emblem_h = max(emblem_h, 0.70 + 0.30 * tu)
                    elif f == 2:
                        # Ice: razor-sharp 6-point snowflake crystal with V-chevron barbs (no wheel rim!)
                        a6 = abs(angle_wrap(ang * 6.0)) / 6.0
                        spoke = r * math.sin(a6)
                        if spoke < 0.036 and r < 0.45:
                            su = 1.0 - spoke / 0.036
                            emblem_a = max(emblem_a, smoothstep(0.0, 0.35, su))
                            emblem_h = max(emblem_h, 0.76 + 0.24 * su)
                        # V-shaped frost barbs branching off each of the 6 arms at r = 0.22 and r = 0.33
                        sector = round(ang / (math.tau / 6.0)) * (math.tau / 6.0)
                        ca, sa = math.cos(-sector), math.sin(-sector)
                        lx = qx * ca - qy * sa
                        ly = qx * sa + qy * ca
                        for br in (0.18, 0.30):
                            for sgn in (-1.0, 1.0):
                                d_b, _ = seg_dist(lx, ly, br, 0.0, br + 0.11, sgn * 0.10)
                                if d_b < 0.028:
                                    bu = 1.0 - d_b / 0.028
                                    emblem_a = max(emblem_a, smoothstep(0.0, 0.35, bu))
                                    emblem_h = max(emblem_h, 0.74 + 0.26 * bu)
                        # Center crystal diamond
                        if abs(qx) + abs(qy) < 0.13:
                            emblem_a = 0.98
                            emblem_h = 0.96
                    else:
                        # Dark: clean sharp mystic crescent moon + 4-point dark-violet star!
                        d_outer = math.hypot(qx + 0.06, qy)
                        d_cut = math.hypot(qx - 0.14, qy + 0.03)
                        if d_outer < 0.38 and d_cut > 0.28:
                            cu = min(clamp((0.38 - d_outer) / 0.08), clamp((d_cut - 0.28) / 0.08))
                            emblem_a = max(emblem_a, smoothstep(0.0, 0.35, cu))
                            emblem_h = max(emblem_h, 0.76 + 0.24 * cu)
                        # Small 4-point star nestled inside the crescent hollow
                        sx, sy = qx - 0.18, qy + 0.02
                        d_star = (abs(sx) ** 0.65) + (abs(sy) ** 0.65)
                        if d_star < 0.22:
                            su = 1.0 - d_star / 0.22
                            emblem_a = max(emblem_a, smoothstep(0.0, 0.35, su))
                            emblem_h = max(emblem_h, 0.80 + 0.20 * su)

                if emblem_a > 0.02:
                    core_a = max(core_a, emblem_a * 0.98)
                    core_h = max(core_h, emblem_h)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr - 0.72) / 0.14) ** 2) * 0.42
                    + math.exp(-(rr / 0.56) ** 2) * 0.52
                ) * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(ramp, core_h, core_a, 0.68, glow_a)

    return img


# --- 2. charge_arcane_circle.png (256x256, 1 frame, tintable luminance) ------

def make_charge_arcane_circle() -> Image.Image:
    """
    Tintable high-contrast arcane hexagram circle for Moving Cast charge aura & ground reticle.
    Dark shadowed outer backing + crisp white-hot hexagram lines and rune ticks.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    tri_verts_a = [(math.cos(i * math.tau / 3.0 - math.pi / 2), math.sin(i * math.tau / 3.0 - math.pi / 2)) for i in range(3)]
    tri_verts_b = [(math.cos(i * math.tau / 3.0 + math.pi / 2), math.sin(i * math.tau / 3.0 + math.pi / 2)) for i in range(3)]
    hex_r = 0.74

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

            core_a = 0.0
            core_h = 0.15

            # Outer dual arcane rings
            for ring_r, ring_w, h_boost in ((0.90, 0.042, 0.92), (0.76, 0.032, 0.88), (0.42, 0.026, 0.85)):
                dr = abs(r - ring_r)
                if dr < ring_w:
                    u = 1.0 - dr / ring_w
                    core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.96)
                    core_h = max(core_h, 0.25 + h_boost * 0.75 * u)

            # Rune script band between 0.78 and 0.88
            if 0.78 < r < 0.88:
                cell = int(math.floor(((ang + math.pi) / math.tau) * 36.0))
                local_a = (((ang + math.pi) / math.tau) * 36.0) - cell
                if 0.18 < local_a < 0.82:
                    glyph_h = hash2(cell, int((r - 0.78) * 30), 71)
                    if glyph_h > 0.38:
                        core_a = max(core_a, 0.88)
                        core_h = max(core_h, 0.65 + 0.35 * glyph_h)

            # Inscribed crisp hexagram (Star of Solomon)
            if r < hex_r + 0.04:
                min_d = 9.0
                for verts in (tri_verts_a, tri_verts_b):
                    for i in range(3):
                        ax, ay = verts[i][0] * hex_r, verts[i][1] * hex_r
                        bx, by = verts[(i + 1) % 3][0] * hex_r, verts[(i + 1) % 3][1] * hex_r
                        d, _ = seg_dist(qx, qy, ax, ay, bx, by)
                        if d < min_d:
                            min_d = d
                if min_d < 0.028:
                    u = 1.0 - min_d / 0.028
                    core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.96)
                    core_h = max(core_h, 0.45 + 0.55 * u)

            # 6 outer vertex diamond nodes
            if 0.70 < r < 0.96:
                a6 = abs(angle_wrap((ang + math.pi / 2) * 6.0)) / 6.0
                if a6 < 0.065:
                    u = 1.0 - a6 / 0.065
                    core_a = max(core_a, smoothstep(0.0, 0.35, u) * 0.94)
                    core_h = max(core_h, 0.70 + 0.30 * u)

            # Subtle dark magic field fill inside circle for crisp daytime contrast
            if r < 0.89 and core_a < 0.35:
                core_a = max(core_a, 0.24 * smoothstep(0.90, 0.35, r))
                core_h = min(core_h, 0.16)

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-((rr - 0.83) / 0.09) ** 2) * 0.44
                + math.exp(-((rr - 0.42) / 0.08) ** 2) * 0.28
            ) * smoothstep(0.99, 0.90, rr)
            px[x, y] = pack_ramp_pixel(LUMINANCE_RAMP, core_h, core_a, 0.82, glow_a)

    return img


# --- 3. ice_hexagram_circle.png (256x256, 1 frame) ---------------------------

def make_ice_hexagram_circle() -> Image.Image:
    """
    Classic DNF Arctic Feast / Ice Wall Frost Hexagram Magic Circle (极冰六芒霜阵):
    Deep sapphire frost bed + jagged frozen cracks + crisp white-cyan 6-point snowflake hexagram!
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    tri_a = [(math.cos(i * math.tau / 3.0 - math.pi / 2), math.sin(i * math.tau / 3.0 - math.pi / 2)) for i in range(3)]
    tri_b = [(math.cos(i * math.tau / 3.0 + math.pi / 2), math.sin(i * math.tau / 3.0 + math.pi / 2)) for i in range(3)]

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
            n = fbm(qx * 5.2, qy * 5.2, 311, 3)

            # Deep frozen frost bed + jagged perimeter spikes
            ice_edge = 0.91 + 0.05 * math.cos(ang * 12.0) + 0.07 * (n - 0.5)
            bed = smoothstep(ice_edge, 0.28, r) * (0.48 + 0.48 * n)
            core_a = bed * 0.68
            core_h = 0.14 + 0.34 * bed

            # Dual concentric frost-crystal rune rings
            for rr_t, rw in ((0.88, 0.044), (0.72, 0.030), (0.36, 0.025)):
                dr = abs(r - rr_t)
                if dr < rw:
                    u = 1.0 - dr / rw
                    core_a = max(core_a, smoothstep(0.0, 0.3, u) * 0.96)
                    core_h = max(core_h, 0.42 + 0.58 * u)

            # 6-point razor snowflake spokes + feathered frost branches
            a6 = abs(angle_wrap(ang * 6.0)) / 6.0
            spoke = r * math.sin(a6)
            if spoke < 0.026 and r < 0.90:
                u = 1.0 - spoke / 0.026
                core_a = max(core_a, 0.96)
                core_h = max(core_h, 0.62 + 0.38 * u)

            # Hexagram star inscribed inside r = 0.72
            if r < 0.76:
                min_d = 9.0
                for verts in (tri_a, tri_b):
                    for i in range(3):
                        ax, ay = verts[i][0] * 0.72, verts[i][1] * 0.72
                        bx, by = verts[(i + 1) % 3][0] * 0.72, verts[(i + 1) % 3][1] * 0.72
                        d, _ = seg_dist(qx, qy, ax, ay, bx, by)
                        if d < min_d:
                            min_d = d
                if min_d < 0.026:
                    u = 1.0 - min_d / 0.026
                    core_a = max(core_a, 0.98)
                    core_h = max(core_h, 0.58 + 0.42 * u)

            # Diagonal frost cracks radiating from center
            crack = abs(math.sin(ang * 6.0 + r * 5.2 + (n - 0.5) * 1.4))
            if crack < 0.09 and 0.14 < r < 0.86:
                cu = 1.0 - crack / 0.09
                core_a = max(core_a, 0.84 * cu)
                core_h = max(core_h, 0.74 + 0.24 * cu)

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-((rr - 0.84) / 0.11) ** 2) * 0.48
                + math.exp(-(rr / 0.52) ** 2) * 0.34
            ) * smoothstep(0.99, 0.88, rr)
            px[x, y] = pack_ramp_pixel(ICE_GLACIAL_RAMP, core_h, core_a, 0.76, glow_a)

    return img


# --- 4. thunder_reticle_circle.png (256x256, 1 frame) ------------------------

def make_thunder_reticle_circle() -> Image.Image:
    """
    Classic DNF Lightning Strike / Lightning Vortex Golden Sky-Judgment Circle (天雷黄金落雷法阵):
    Dark storm-gold backing + blazing golden-cyan octagonal thunder seal + crosshair lock ticks.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    oct_v = [(math.cos(i * math.tau / 8.0), math.sin(i * math.tau / 8.0)) for i in range(8)]

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

            core_a = 0.26 * smoothstep(0.92, 0.30, r)
            core_h = 0.14

            # Triple golden thunder rings
            for rr_t, rw in ((0.90, 0.046), (0.74, 0.032), (0.34, 0.028)):
                dr = abs(r - rr_t)
                if dr < rw:
                    u = 1.0 - dr / rw
                    core_a = max(core_a, smoothstep(0.0, 0.32, u) * 0.98)
                    core_h = max(core_h, 0.35 + 0.65 * u)

            # Two rotated squares forming an 8-point DNF thunder star
            min_d = 9.0
            for offset in (0, 1):
                for i in range(4):
                    a_idx = (i * 2 + offset) % 8
                    b_idx = ((i + 1) * 2 + offset) % 8
                    d, _ = seg_dist(qx, qy, oct_v[a_idx][0] * 0.74, oct_v[a_idx][1] * 0.74,
                                    oct_v[b_idx][0] * 0.74, oct_v[b_idx][1] * 0.74)
                    if d < min_d:
                        min_d = d
            if min_d < 0.028:
                u = 1.0 - min_d / 0.028
                core_a = max(core_a, 0.96)
                core_h = max(core_h, 0.54 + 0.46 * u)

            # 4 bold crosshair lightning spears pointing inward
            a4 = abs(angle_wrap(ang * 4.0)) / 4.0
            if 0.36 < r < 0.94 and r * math.sin(a4) < 0.034:
                u = 1.0 - (r * math.sin(a4)) / 0.034
                core_a = max(core_a, 0.98)
                core_h = max(core_h, 0.72 + 0.28 * u)

            # Crackling lightning notches in outer band
            if 0.76 < r < 0.88:
                zig = math.sin(ang * 16.0) * 0.045
                if abs(r - (0.82 + zig)) < 0.020:
                    core_a = max(core_a, 0.94)
                    core_h = max(core_h, 0.88)

            # Glowing bullseye heart
            if r < 0.14:
                hu = 1.0 - r / 0.14
                core_a = max(core_a, 0.96 * smoothstep(0.0, 0.35, hu))
                core_h = max(core_h, 0.70 + 0.30 * hu)

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-((rr - 0.84) / 0.11) ** 2) * 0.50
                + math.exp(-(rr / 0.34) ** 2) * 0.42
            ) * smoothstep(0.99, 0.89, rr)
            px[x, y] = pack_ramp_pixel(LIGHT_THUNDER_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 5. volcano_magma_crater.png (256x256, 1 frame) --------------------------

def make_volcano_magma_crater() -> Image.Image:
    """
    Classic DNF Flame Pillar / Halloween Buster Volcanic Fissure Crater (火山熔岩裂隙阵):
    Charred obsidian blast bed + searing molten magma cracks + fiery arcane rim ring!
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
            n1 = fbm(qx * 4.6, qy * 4.6, 409, 3)
            n2 = fbm(qx * 9.0, qy * 9.0, 521, 2)

            # Charred volcanic crust
            edge = 0.90 + 0.12 * (n1 - 0.5)
            crust = smoothstep(edge, 0.32, r)
            core_a = crust * 0.88
            core_h = 0.04 + 0.14 * n1

            # Deep incandescent molten lava fissures radiating from caldera center
            fissure_a = abs(math.sin(ang * 5.0 + (n1 - 0.5) * 2.6 + r * 2.2))
            fissure_b = abs(math.sin(ang * 9.0 - (n2 - 0.5) * 2.2 - r * 3.0))
            cr1 = smoothstep(0.18, 0.02, fissure_a) * smoothstep(0.88, 0.12, r)
            cr2 = smoothstep(0.14, 0.02, fissure_b) * smoothstep(0.82, 0.20, r) * 0.82
            caldera = math.exp(-(r / 0.36) ** 2) * (0.65 + 0.35 * n2)
            lava = clamp(max(cr1, cr2, caldera))
            if lava > 0.06:
                core_a = max(core_a, 0.96 * smoothstep(0.06, 0.35, lava))
                core_h = max(core_h, 0.28 + 0.72 * lava)

            # Outer fiery jagged magma shock rim
            rim_j = 0.82 + 0.05 * math.sin(ang * 10.0) + 0.07 * (n1 - 0.5)
            dr = abs(r - rim_j)
            if dr < 0.050:
                u = 1.0 - dr / 0.050
                core_a = max(core_a, smoothstep(0.0, 0.35, u) * (0.72 + 0.28 * n2))
                core_h = max(core_h, 0.36 + 0.56 * u)

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-(rr / 0.52) ** 2) * 0.55
                + math.exp(-((rr - 0.82) / 0.11) ** 2) * 0.36
            ) * smoothstep(0.99, 0.88, rr)
            px[x, y] = pack_ramp_pixel(FIRE_VOLCANO_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 6. astral_star_circle.png (256x256, 1 frame, 4-element cosmic grand circle) -

def make_astral_star_circle() -> Image.Image:
    """
    Classic DNF Archmage Awakening Astral Storm Grand Circle (陨星幻灭四元素星界大法阵):
    Deep swirling cosmic nebula field + seamlessly gradient-blended 4-element zodiac ring +
    razor-sharp 8-point celestial star + ornate bezeled 4-element cardinal orbs!
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    quad_colors = (
        (255, 224, 96),   # 0: Light Gold (+X)
        (255, 114, 36),   # 1: Fire Vermilion (+Y)
        (104, 226, 255),  # 2: Ice Glacial Cyan (-X)
        (184, 98, 255),   # 3: Dark Void Violet (-Y)
    )
    # Sharp 8-point star outer vertices (r=0.71) alternating with inner valleys (r=0.28)
    star_pts: list[tuple[float, float]] = []
    for i in range(16):
        a = i * (math.tau / 16.0)
        rad = 0.71 if i % 2 == 0 else 0.30
        star_pts.append((math.cos(a) * rad, math.sin(a) * rad))

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
            n = fbm(qx * 4.5, qy * 4.5, 613, 3)

            # Seamlessly interpolate 4 elemental colors around the circle (zero hard seams!)
            q_float = (((ang % math.tau) / math.tau) * 4.0)
            q0 = int(math.floor(q_float)) & 3
            q1 = (q0 + 1) & 3
            qf = smoothstep(0.0, 1.0, q_float - math.floor(q_float))
            c0, c1 = quad_colors[q0], quad_colors[q1]
            q_col = (lerp(c0[0], c1[0], qf), lerp(c0[1], c1[1], qf), lerp(c0[2], c1[2], qf))

            # Deep starry cosmic nebula backing
            swirl = 0.5 + 0.5 * math.sin(ang * 4.0 + r * 5.0 + (n - 0.5) * 2.0)
            field_a = smoothstep(0.95, 0.35, r) * (0.56 + 0.30 * n)
            base_rgb = sample_ramp(ASTRAL_COSMIC_RAMP, 0.08 + 0.24 * swirl + 0.12 * n)
            core_a = field_a
            col = list(base_rgb)

            # Smooth 4-element energy channel ring + rune glyph band between r=0.73..0.89
            dr_q = abs(r - 0.81)
            if dr_q < 0.085:
                u = 1.0 - dr_q / 0.085
                blend = smoothstep(0.0, 0.42, u)
                core_a = max(core_a, 0.90 * blend)
                col = [lerp(col[i], q_col[i], 0.82 * blend) for i in range(3)]
                # Golden rune script engraved along the elemental ring
                cell = int(math.floor(((ang + math.pi) / math.tau) * 40.0))
                local_a = (((ang + math.pi) / math.tau) * 40.0) - cell
                if 0.22 < local_a < 0.78 and dr_q < 0.052:
                    if hash2(cell, int((r - 0.75) * 32), 619) > 0.42:
                        col = [lerp(col[i], 255.0, 0.72) for i in range(3)]
                        core_a = max(core_a, 0.96)

            # Crisp celestial golden rings (0.93, 0.71, 0.44, 0.22)
            for rr_t, rw in ((0.93, 0.036), (0.71, 0.028), (0.44, 0.022), (0.22, 0.020)):
                dr = abs(r - rr_t)
                if dr < rw:
                    u = 1.0 - dr / rw
                    g_rgb = sample_ramp(ASTRAL_COSMIC_RAMP, 0.68 + 0.32 * u)
                    core_a = max(core_a, 0.98 * smoothstep(0.0, 0.3, u))
                    col = [lerp(col[i], g_rgb[i], 0.95) for i in range(3)]

            # Razor-sharp 8-point Grand Celestial Star polygon
            if r < 0.74:
                min_d = 9.0
                for i in range(16):
                    ax, ay = star_pts[i]
                    bx, by = star_pts[(i + 1) % 16]
                    d, _ = seg_dist(qx, qy, ax, ay, bx, by)
                    if d < min_d:
                        min_d = d
                if min_d < 0.024:
                    u = 1.0 - min_d / 0.024
                    s_rgb = sample_ramp(ASTRAL_COSMIC_RAMP, 0.74 + 0.26 * u)
                    core_a = max(core_a, 0.98)
                    col = [lerp(col[i], s_rgb[i], 0.94) for i in range(3)]

            # 4 bezeled cardinal elemental jewels at (0.71,0), (0,0.71), (-0.71,0), (0,-0.71)
            for idx_c, (cx, cy) in enumerate(((0.71, 0.0), (0.0, 0.71), (-0.71, 0.0), (0.0, -0.71))):
                dc = math.hypot(qx - cx, qy - cy)
                if dc < 0.135:
                    nu = 1.0 - dc / 0.135
                    if dc > 0.105:
                        # Ornate dark-gold bezel ring
                        core_a = 0.98
                        col = [235.0, 192.0, 88.0]
                    else:
                        nc = quad_colors[idx_c]
                        iu = 1.0 - dc / 0.105
                        core_a = 0.99
                        col = [lerp(nc[i], 255.0, (iu ** 1.4) * 0.68) for i in range(3)]

            # Twinkling starlight specks inside field
            if r < 0.90:
                st = hash2(x // 2, y // 2, 733)
                if st > 0.986:
                    core_a = max(core_a, 0.98)
                    col = [255.0, 250.0, 236.0]

            rr = math.hypot(rx, ry)
            glow_a = (
                math.exp(-((rr - 0.82) / 0.12) ** 2) * 0.50
                + math.exp(-(rr / 0.42) ** 2) * 0.36
            ) * smoothstep(0.99, 0.90, rr)
            glow_rgb = (lerp(q_col[0], 225, 0.30), lerp(q_col[1], 195, 0.30), lerp(q_col[2], 255, 0.30))
            px[x, y] = pack_rgb_blend((col[0], col[1], col[2]), core_a, glow_rgb, glow_a)

    return img


# --- 7. ice_wall_pillar.png (1024x128, 8 frames of 128x128) ------------------

def make_ice_wall_pillar() -> Image.Image:
    """
    Classic DNF Ice Wall (冰墙):
    Towering razor-faceted translucent glacial monolith slab with crystal side teeth!
      Frames 0..1: rapid emergence from frozen ground
      Frames 2..4: gleaming standing crystalline barrier with internal frost shimmer
      Frames 5..7: deep fracture lines + violent shattering ice diamonds
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Main central crystal spire + twin tall crystal wings (x_base, half_w, apex_x, apex_y)
    facets = [
        (0.00, 0.34, 0.02, -0.90),
        (-0.34, 0.24, -0.52, -0.58),
        (0.34, 0.24, 0.54, -0.62),
        (-0.18, 0.18, -0.26, -0.32),
        (0.20, 0.18, 0.28, -0.28),
    ]

    for f in range(frames):
        if f <= 1:
            rise = 0.45 + 0.55 * f
            shatter = 0.0
            env = 0.88 + 0.12 * f
        elif f <= 4:
            rise = 1.0
            shatter = 0.0
            env = 1.0
        else:
            rise = 1.0 - 0.14 * (f - 4)
            shatter = (f - 4) / 3.0
            env = clamp(1.0 - 0.58 * shatter)

        shimmer_y = -0.7 + ((f - 2) * 0.55) if 2 <= f <= 4 else -9.0

        shards: list[tuple[float, float, float, float]] = []
        if shatter > 0.05:
            for k in range(16):
                ang = -math.pi * 0.08 - (k / 15.0) * math.pi * 0.84
                spd = 0.35 + 0.62 * hash2(k, 11, 211)
                sx = (hash2(k, 3, 19) - 0.5) * 0.65 + math.cos(ang) * spd * shatter
                sy = 0.28 - hash2(k, 7, 43) * 0.88 + math.sin(ang) * spd * shatter * 0.82
                rad = (0.06 + 0.05 * hash2(k, 13, 61)) * (1.0 - 0.42 * shatter)
                shards.append((sx, sy, rad, ang))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                base_y = 0.95

                core_a = 0.0
                core_h = 0.15

                if shatter < 0.90:
                    for idx, (bx, hw, ax_t, ay_t) in enumerate(facets):
                        cur_ay = lerp(base_y, ay_t, rise)
                        cur_ax = lerp(bx, ax_t, rise)
                        lx, rx_b = bx - hw, bx + hw
                        if point_in_tri(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay):
                            hu = clamp((base_y - qy) / max(0.05, base_y - cur_ay))
                            spine_x = lerp(bx, cur_ax, hu)
                            hw_y = max(0.02, hw * (1.0 - hu))
                            side_u = (qx - spine_x) / hw_y
                            ed = tri_edge_dist(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay)
                            facet_h = 0.26 if side_u < -0.04 else 0.58
                            ridge = math.exp(-((side_u - 0.03) / 0.15) ** 2) * (0.34 + 0.44 * hu)
                            rim = smoothstep(0.075, 0.0, ed) * 0.38
                            glint = math.exp(-((qy - shimmer_y) / 0.18) ** 2) * 0.28
                            h = clamp(facet_h + ridge + rim + 0.26 * smoothstep(0.65, 1.0, hu) + glint - 0.14 * (1.0 - hu))
                            a_c = clamp(0.95 * env * (0.62 + 0.38 * smoothstep(0.96, 0.80, qy)))
                            if shatter > 0.02:
                                n = fbm(qx * 7.2, qy * 7.2, idx * 29 + 7, 2)
                                if n < shatter * 0.92:
                                    a_c = 0.0
                                elif n < shatter * 0.92 + 0.13:
                                    h = 0.98
                            if a_c > core_a:
                                core_a = a_c
                                core_h = h

                for sx, sy, srad, sang in shards:
                    dx, dy = qx - sx, qy - sy
                    if abs(dx) < srad * 1.4 and abs(dy) < srad * 1.4:
                        ca, sa = math.cos(sang), math.sin(sang)
                        lx = dx * ca + dy * sa
                        ly = -dx * sa + dy * ca
                        if abs(lx) * 0.72 + abs(ly) * 1.45 < srad:
                            u = 1.0 - (abs(lx) * 0.72 + abs(ly) * 1.45) / srad
                            sa_val = smoothstep(0.0, 0.35, u) * env * 0.96
                            if sa_val > core_a:
                                core_a = sa_val
                                core_h = 0.66 + 0.34 * u

                base_mist = math.exp(-((rx / 0.68) ** 2 + ((ry - 0.76) / 0.25) ** 2)) * 0.50 * env
                body_glow = math.exp(-((rx / 0.48) ** 2 + ((ry - 0.08) / 0.78) ** 2)) * 0.36 * env
                glow_a = clamp(base_mist + body_glow) * smoothstep(0.99, 0.86, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ICE_GLACIAL_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 8. lightning_vortex_orb.png (1024x128, 8 frames of 128x128) -------------

def make_lightning_vortex_orb() -> Image.Image:
    """
    Classic DNF Lightning Vortex (雷旋):
    High-voltage blazing golden-cyan ball lightning with snapping asymmetric plasma bolts!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        bolts: list[list[tuple[float, float]]] = []
        for b in range(6):
            pts: list[tuple[float, float]] = []
            a0 = hash2(b, f * 7 + 5, 131) * math.tau + phase * (1.0 if b % 2 == 0 else -1.0)
            a1 = a0 + lerp(0.8, 2.4, hash2(b, f * 13 + 3, 257))
            r0 = lerp(0.06, 0.32, hash2(b, f * 17 + 1, 313))
            r1 = lerp(0.68, 0.94, hash2(b, f * 23 + 7, 419))
            for s in range(6):
                u = s / 5.0
                ang = lerp(a0, a1, u) + (hash2(s, b * 11 + f, 503) - 0.5) * 0.48 * math.sin(u * math.pi)
                rad = lerp(r0, r1, u) + (hash2(s * 5, b + f * 3, 607) - 0.5) * 0.25 * math.sin(u * math.pi)
                pts.append((math.cos(ang) * rad, math.sin(ang) * rad))
            bolts.append(pts)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)

                core_a = 0.0
                core_h = 0.12

                # Swirling electric core sphere
                if r < 0.68:
                    n = fbm(qx * 5.0 + math.cos(phase), qy * 5.0 + math.sin(phase), f * 13 + 5, 3)
                    swirl = 0.5 + 0.5 * math.sin(ang * 4.0 - r * 9.0 + phase * 2.0 + (n - 0.5) * 2.2)
                    core_a = clamp(smoothstep(0.68, 0.44, r) * (0.82 + 0.30 * n))
                    hot = math.exp(-(r / 0.30) ** 2)
                    core_h = clamp(0.18 + 0.42 * swirl + 0.58 * hot)

                # Asymmetric high-voltage bolts
                if r < 0.96:
                    min_bd = 9.0
                    for pts in bolts:
                        for i in range(len(pts) - 1):
                            d, _ = seg_dist(qx, qy, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
                            if d < min_bd:
                                min_bd = d
                    if min_bd < 0.048:
                        bu = 1.0 - min_bd / 0.048
                        ba = smoothstep(0.0, 0.32, bu) * 0.98
                        if ba > core_a * 0.65:
                            core_a = max(core_a, ba)
                            core_h = max(core_h, 0.78 + 0.22 * bu)

                # Electric snap sparks
                if 0.45 < r < 0.96:
                    sp = hash2(x // 2, y // 2, f * 37 + 11)
                    if sp > 0.980:
                        core_a = max(core_a, 0.95)
                        core_h = max(core_h, 0.96)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.58) ** 2) * 0.62 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(LIGHT_THUNDER_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 9. void_sphere_orb.png (1024x128, 8 frames of 128x128) ------------------

def make_void_sphere_orb() -> Image.Image:
    """
    Classic DNF Void Sphere (虚无之球):
    Pitch-black void heart + swirling dark-violet shadow tentacles + broken arcane outer ring!
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
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.0 - math.cos(phase), qy * 5.0 - math.sin(phase), f * 17 + 3, 3)

                core_a = 0.0
                core_h = 0.04

                # Pitch-black inner void core (r < 0.44) with blazing violet photon edge!
                if r < 0.78:
                    swirl = 0.5 + 0.5 * math.sin(ang * 3.0 + r * 8.5 - phase * 2.0 + (n - 0.5) * 2.2)
                    arm_r = 0.72 + 0.10 * swirl + 0.10 * (n - 0.5)
                    if r < arm_r:
                        rim_ring = math.exp(-((r - 0.44) / 0.07) ** 2)
                        outer_fade = smoothstep(arm_r, 0.46, r)
                        if r < 0.42:
                            core_a = 0.98
                            core_h = 0.02 + 0.14 * swirl * (r / 0.42)
                        else:
                            core_a = clamp(outer_fade * (0.72 + 0.35 * swirl))
                            core_h = clamp(0.16 + 0.52 * swirl + 0.42 * rim_ring)
                        if rim_ring > 0.35:
                            core_a = max(core_a, 0.96)
                            core_h = max(core_h, 0.72 + 0.28 * rim_ring)

                # Broken spinning dark rune ring at r = 0.84
                dr = abs(r - 0.84)
                if dr < 0.048:
                    seg = math.cos((ang - phase) * 4.0)
                    if seg > 0.10:
                        u = 1.0 - dr / 0.048
                        ra = smoothstep(0.0, 0.35, u) * smoothstep(0.10, 0.40, seg) * 0.92
                        if ra > core_a:
                            core_a = ra
                            core_h = 0.55 + 0.40 * u

                if 0.50 < r < 0.95:
                    sp = hash2(x // 2, y // 2, f * 41 + 19)
                    if sp > 0.980:
                        core_a = max(core_a, 0.92)
                        core_h = max(core_h, 0.92)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - 0.52) / 0.24) ** 2) * 0.54 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(DARK_VOID_RAMP, core_h, core_a, 0.64, glow_a)

    return img


# --- 10. flame_pillar_column.png (1024x128, 8 frames of 128x128) -------------

def make_flame_pillar_column() -> Image.Image:
    """
    Classic DNF Flame Pillar / Volcano Eruption (烈焰冲击 冲天熔岩炎柱):
    8 frames of a massive roaring spiral flame column surging from ground (y=+1) to sky (y=-1),
    with flared fiery base, spiral lava tongues, billowing mushroom crown, and erupting sparks!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        height = clamp(0.48 + 0.52 * ease_out_cubic(min(1.0, t * 2.5)))
        env = smoothstep(0.0, 0.12, t + 0.10) * (1.0 - 0.68 * smoothstep(0.62, 1.0, t))
        width_mult = 0.82 + 0.42 * math.sin(min(1.0, t * 1.4) * math.pi * 0.72)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                vy = clamp((0.96 - qy) / 1.92)
                if vy > height + 0.16:
                    continue

                n = fbm(qx * 4.6, qy * 4.6 + t * 5.6, f * 23 + 7, 3)
                crown = 0.30 * math.exp(-((vy - height * 0.84) / 0.22) ** 2)
                base_flare = 0.32 * math.exp(-(vy / 0.24) ** 2)
                col_w = (0.38 + crown + base_flare) * width_mult * (0.82 + 0.36 * n)

                twist = 0.11 * math.sin(vy * 9.5 - t * 11.0)
                dx = abs(qx - twist)

                core_a = 0.0
                core_h = 0.10
                if dx < col_w and vy < height + 0.06:
                    wu = clamp(1.0 - dx / max(0.03, col_w))
                    top_fade = smoothstep(height + 0.06, max(0.05, height - 0.22), vy)
                    base_fade = smoothstep(0.0, 0.07, vy)
                    breakup = 1.0 if (t < 0.55 or n > (t - 0.46) * 1.32) else 0.16
                    core_a = clamp(smoothstep(0.0, 0.25, wu) * top_fade * base_fade * env * breakup * 1.35)
                    vein = 0.5 + 0.5 * math.sin(qx * 10.0 + vy * 8.5 - t * 12.5)
                    core_h = clamp(0.08 + 0.64 * (wu ** 0.70) + 0.22 * vein * wu + 0.26 * math.exp(-(vy / 0.36) ** 2) * wu)

                if abs(qx) < 0.90 and vy < height + 0.14:
                    sp = hash2(x // 2, (y + f * 7) // 2, f * 31 + 17)
                    if sp > 0.974:
                        core_a = max(core_a, 0.94 * env)
                        core_h = max(core_h, 0.95 if sp > 0.986 else 0.60)

                rvy = clamp((0.96 - ry) / 1.92)
                glow_a = (
                    math.exp(-((rx / (0.52 * width_mult)) ** 2))
                    * smoothstep(height + 0.14, max(0.0, height - 0.24), rvy)
                    * 0.50 * env * smoothstep(0.99, 0.85, max(abs(rx), abs(ry)))
                )
                px[f * size + x, y] = pack_ramp_pixel(FIRE_VOLCANO_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 11. jack_pumpkin_head.png (1024x128, 8 frames of 128x128) ---------------

def make_jack_pumpkin_head() -> Image.Image:
    """
    THE Iconic Classic DNF Flaming Jack-O'-Lantern (杰克爆弹 & 杰克降临 邪笑烈焰南瓜头):
      - Ribbed fiery orange pumpkin body with shaded vertical grooves & dark charred outline
      - Crooked emerald-green stem sprouting from the crown with curled vine highlight
      - Fierce glowing incandescent yellow-white slanted triangular eyes, nose, and wide sawtooth grin!
      - Roaring animated flame aura licking upward and outward across all 8 frames!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * (math.tau / frames)
        bob = 0.025 * math.sin(phase)
        flicker = 0.5 + 0.5 * math.sin(phase * 2.0)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                py = qy - 0.08 - bob
                n = fbm(qx * 4.8, qy * 4.8 + phase * 0.9, f * 19 + 11, 3)

                col: tuple[float, float, float] = (0.0, 0.0, 0.0)
                core_a = 0.0

                # 1. Roaring upward-whipping flame mane & corona engulfing the pumpkin!
                flame_y = clamp((0.82 - qy) / 1.76)  # 0 at bottom, 1 at top flame crown
                flame_w = 0.84 * (1.0 - 0.30 * flame_y) + 0.10 * math.sin(qy * 7.5 + phase * 2.0)
                dist_f = math.hypot(qx / max(0.24, flame_w), (qy + 0.06) / 0.92)
                tongue = 0.22 * math.sin(math.atan2(qy + 0.06, qx) * 7.0 - phase * 2.0) + 0.26 * (n - 0.44)
                if dist_f < 0.90 + tongue and math.hypot(qx, qy) < 0.96:
                    fu = clamp(1.0 - dist_f / max(0.2, 0.90 + tongue))
                    fa = smoothstep(0.0, 0.34, fu) * (0.78 + 0.24 * n) * smoothstep(0.97, 0.84, math.hypot(qx, qy))
                    fh = clamp(0.20 + 0.64 * fu + 0.14 * flicker)
                    if fa > 0.04:
                        col = sample_ramp(FIRE_VOLCANO_RAMP, fh)
                        core_a = fa * 0.94

                # 2. Ribbed fiery pumpkin body (clean plump oval, subtle top/bottom dimple, zero beard bump)
                nx = qx / 0.62
                dimple = -0.04 * math.exp(-(nx / 0.22) ** 2)
                ny = py / (0.51 + dimple)
                pr = math.hypot(nx, ny)
                if pr < 1.0:
                    # Pumpkin vertical rib grooves (5 classic lobes)
                    rib_groove = abs(math.sin(nx * math.pi * 2.4))
                    edge_dark = smoothstep(0.70, 1.0, pr)
                    # Top-left warm lighting + deep crimson-brown outline & grooves
                    light = clamp(0.68 - 0.26 * nx - 0.22 * ny - 0.24 * (1.0 - rib_groove) - 0.42 * edge_dark)
                    # Pumpkin rich fiery orange palette
                    p_heat = clamp(0.22 + 0.56 * light + 0.08 * flicker)
                    p_rgb = sample_ramp(FIRE_VOLCANO_RAMP, p_heat)
                    # Boost saturated pumpkin orange in midtones
                    p_rgb = (
                        min(255, int(p_rgb[0] * 1.06)),
                        min(255, int(p_rgb[1] * 1.05 + 14 * (1.0 - edge_dark))),
                        max(0, int(p_rgb[2] * 0.72)),
                    )
                    core_a = 0.99
                    col = p_rgb

                    # 3. Iconic DNF Evil Laughing Face carved out with blazing white-hot inner fire!
                    face_lit = False
                    face_core = 0.0

                    # Left & Right fierce slanted triangular eyes
                    for sgn in (-1.0, 1.0):
                        ex = qx * sgn
                        # Outer corner higher & sharper, inner corner angled down menacingly
                        if point_in_tri(ex, py, 0.10, -0.05, 0.40, -0.22, 0.34, 0.04):
                            ed = tri_edge_dist(ex, py, 0.10, -0.05, 0.40, -0.22, 0.34, 0.04)
                            face_lit = True
                            face_core = max(face_core, clamp(ed / 0.08))

                    # Sharp triangular nose
                    if point_in_tri(qx, py, 0.0, -0.02, -0.07, 0.11, 0.07, 0.11):
                        face_lit = True
                        face_core = max(face_core, 0.75)

                    # Wide wicked grinning sawtooth mouth!
                    if abs(qx) < 0.44 and 0.12 < py < 0.39:
                        mx = qx / 0.44
                        # Smile curves upward at the corners!
                        smile_top = 0.18 - 0.07 * (mx * mx)
                        smile_bot = 0.35 - 0.14 * (mx * mx)
                        # Jagged pumpkin teeth jutting from top & bottom of grin
                        tooth_top = 0.052 * max(0.0, math.cos(qx * 24.0))
                        tooth_bot = 0.048 * max(0.0, -math.cos(qx * 24.0 + 0.5))
                        if (smile_top + tooth_top) < py < (smile_bot - tooth_bot):
                            md = min(py - (smile_top + tooth_top), (smile_bot - tooth_bot) - py)
                            face_lit = True
                            face_core = max(face_core, clamp(md / 0.06))

                    if face_lit:
                        # Deep carved dark rim -> blazing incandescent golden-white inner blaze!
                        fh_face = clamp(0.76 + 0.24 * face_core + 0.06 * flicker)
                        col = sample_ramp(FIRE_VOLCANO_RAMP, fh_face)
                        core_a = 1.0

                # 4. Crooked Emerald-Green Pumpkin Stem sprouting from the crown!
                # Curved bezier-like 2-segment stem from (0.0, -0.40+bob) -> (0.04, -0.62+bob) -> (0.18, -0.74+bob)
                d_s1, t_s1 = seg_dist(qx, py, 0.0, -0.44, 0.03, -0.64)
                d_s2, t_s2 = seg_dist(qx, py, 0.03, -0.64, 0.17, -0.75)
                sw1 = lerp(0.105, 0.075, t_s1)
                sw2 = lerp(0.075, 0.042, t_s2)
                stem_u = 0.0
                if d_s1 < sw1:
                    stem_u = max(stem_u, 1.0 - d_s1 / sw1)
                if d_s2 < sw2:
                    stem_u = max(stem_u, 1.0 - d_s2 / sw2)
                if stem_u > 0.02 and py < -0.38:
                    # Crisp dark outline -> vivid emerald stem -> bright lime highlight
                    sh = clamp(0.12 + 0.82 * (stem_u ** 0.72) - 0.14 * qx)
                    col = sample_ramp(PUMPKIN_STEM_RAMP, sh)
                    core_a = 0.99

                # Stray flying ember sparks strictly inside circular envelope (zero corner dots)
                if 0.64 < math.hypot(qx, qy) < 0.91:
                    sp = hash2(x // 2, (y + f * 5) // 2, f * 43 + 7)
                    if sp > 0.984:
                        col = sample_ramp(FIRE_VOLCANO_RAMP, 0.96)
                        core_a = max(core_a, 0.92)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.68) ** 2) * (0.52 + 0.10 * flicker) * smoothstep(0.99, 0.85, rr)
                glow_rgb = sample_ramp(FIRE_VOLCANO_RAMP, 0.66)
                px[f * size + x, y] = pack_rgb_blend(col, core_a, glow_rgb, glow_a)

    return img


# --- 12. jack_lava_burst.png (1024x128, 8 frames of 128x128) -----------------

def make_jack_lava_burst() -> Image.Image:
    """
    8-frame fiery volcanic sunburst / magma explosion ring for Halloween Buster,
    Jack Bomb, Flame Pillar impacts, and Fire Meteors!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        exp_r = 0.28 + 0.68 * ease_out_cubic(t)
        ring_w = lerp(0.32, 0.14, t)
        env = 1.0 - 0.70 * smoothstep(0.55, 1.0, t)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.0, qy * 5.0, f * 29 + 3, 3)

                # Spiky 12-lobe volcanic shock flame front
                lobe = 0.11 * math.sin(ang * 12.0 + t * 2.5) + 0.14 * (n - 0.5)
                dr = abs(r - (exp_r + lobe))
                core_a = 0.0
                core_h = 0.15

                if dr < ring_w:
                    u = 1.0 - dr / ring_w
                    breakup = 1.0 if (t < 0.58 or n > (t - 0.45) * 1.4) else 0.15
                    core_a = clamp(smoothstep(0.0, 0.28, u) * env * breakup * 1.25)
                    core_h = clamp(0.14 + 0.78 * u + 0.18 * (1.0 - t))

                # Early blazing blast core (frames 0..3)
                if t < 0.55 and r < exp_r:
                    cu = clamp(1.0 - r / max(0.05, exp_r))
                    ca = smoothstep(0.0, 0.35, cu) * (1.0 - t / 0.55) * 0.96
                    if ca > core_a:
                        core_a = ca
                        core_h = 0.55 + 0.45 * cu

                # Radial fiery burst rays
                ray = abs(math.sin(ang * 8.0 + 0.4))
                if ray < 0.16 and r < exp_r + 0.16:
                    ru = (1.0 - ray / 0.16) * smoothstep(exp_r + 0.16, 0.1, r)
                    ra = ru * env * 0.88
                    if ra > core_a:
                        core_a = ra
                        core_h = 0.62 + 0.38 * ru

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - exp_r * 0.85) / 0.26) ** 2) * 0.52 * env * smoothstep(0.99, 0.85, rr)
                px[f * size + x, y] = pack_ramp_pixel(FIRE_VOLCANO_RAMP, core_h, core_a, 0.70, glow_a)

    return img


# --- 14. arctic_ice_spire.png (1024x128, 8 frames of 128x128) ----------------

def make_arctic_ice_spire() -> Image.Image:
    """
    Classic DNF Arctic Feast Ground Crystal Cluster (极冰盛宴 爆起寒冰晶簇):
    5 asymmetrical razor-faceted ice spires erupting upward & shattering explosively in frames 5..7!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    spires = [
        (0.00, 0.28, 0.05, -0.92),
        (-0.36, 0.22, -0.66, -0.52),
        (0.34, 0.23, 0.68, -0.58),
        (-0.16, 0.16, -0.34, -0.28),
        (0.18, 0.15, 0.36, -0.22),
    ]

    for f in range(frames):
        if f <= 1:
            rise = 0.44 + 0.56 * f
            shatter = 0.0
            env = 0.88 + 0.12 * f
        elif f <= 4:
            rise = 1.0
            shatter = max(0.0, (f - 3) * 0.18)
            env = 1.0
        else:
            rise = 1.0 - 0.18 * (f - 4)
            shatter = (f - 4) / 3.0
            env = clamp(1.0 - 0.60 * shatter)

        shards: list[tuple[float, float, float, float]] = []
        if shatter > 0.05:
            for k in range(16):
                ang = -math.pi * 0.10 - (k / 15.0) * math.pi * 0.80
                spd = 0.38 + 0.58 * hash2(k, 7, 307)
                sx = (hash2(k, 2, 23) - 0.5) * 0.58 + math.cos(ang) * spd * shatter
                sy = 0.32 - hash2(k, 5, 41) * 0.85 + math.sin(ang) * spd * shatter * 0.85
                rad = (0.06 + 0.05 * hash2(k, 9, 59)) * (1.0 - 0.42 * shatter)
                shards.append((sx, sy, rad, ang))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                base_y = 0.94

                core_a = 0.0
                core_h = 0.12

                if shatter < 0.88:
                    for idx, (bx, hw, ax_t, ay_t) in enumerate(spires):
                        cur_ay = lerp(base_y, ay_t, rise)
                        cur_ax = lerp(bx, ax_t, rise)
                        lx, rx_b = bx - hw, bx + hw
                        if point_in_tri(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay):
                            hu = clamp((base_y - qy) / max(0.05, base_y - cur_ay))
                            spine_x = lerp(bx, cur_ax, hu)
                            hw_y = max(0.02, hw * (1.0 - hu))
                            side_u = (qx - spine_x) / hw_y
                            ed = tri_edge_dist(qx, qy, lx, base_y, rx_b, base_y, cur_ax, cur_ay)
                            h = clamp((0.24 if side_u < -0.05 else 0.58)
                                      + math.exp(-((side_u - 0.04) / 0.16) ** 2) * (0.36 + 0.44 * hu)
                                      + smoothstep(0.07, 0.0, ed) * 0.36 + smoothstep(0.65, 1.0, hu) * 0.30
                                      - 0.15 * (1.0 - hu))
                            a_c = clamp(0.96 * env * (0.58 + 0.42 * smoothstep(0.95, 0.82, qy)))
                            if shatter > 0.02:
                                n = fbm(qx * 7.0, qy * 7.0, idx * 23 + 11, 2)
                                if n < shatter * 0.92:
                                    a_c = 0.0
                                elif n < shatter * 0.92 + 0.13:
                                    h = 0.98
                            if a_c > core_a:
                                core_a = a_c
                                core_h = h

                for sx, sy, srad, sang in shards:
                    dx, dy = qx - sx, qy - sy
                    if abs(dx) < srad * 1.4 and abs(dy) < srad * 1.4:
                        ca, sa = math.cos(sang), math.sin(sang)
                        lx = dx * ca + dy * sa
                        ly = -dx * sa + dy * ca
                        d_dia = abs(lx) * 0.72 + abs(ly) * 1.45
                        if d_dia < srad:
                            u = 1.0 - d_dia / srad
                            sa_val = smoothstep(0.0, 0.35, u) * env * 0.96
                            if sa_val > core_a:
                                core_a = sa_val
                                core_h = 0.66 + 0.34 * u

                base_mist = math.exp(-((rx / 0.66) ** 2 + ((ry - 0.72) / 0.28) ** 2)) * (0.50 + 0.24 * (1.0 - shatter))
                body_glow = math.exp(-((rx / 0.44) ** 2 + ((ry - 0.14) / 0.72) ** 2)) * 0.36 * env
                glow_a = clamp(base_mist + body_glow) * smoothstep(0.99, 0.85, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ICE_GLACIAL_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 15. black_hole_core.png (1024x128, 8 frames of 128x128) -----------------

def make_black_hole_core() -> Image.Image:
    """
    Classic DNF Black Hole Event Horizon Core (湮灭黑洞 漆黑事件视界核):
    True pitch-black abyssal singularity center + razor-sharp violet-white gravitational lensing ring +
    violent inward space-tearing shadow wisps!
    """
    frames, size = 8, 128
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
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.2 + math.cos(phase), qy * 5.2 + math.sin(phase), f * 19 + 7, 3)

                core_a = 0.0
                core_h = 0.02

                # 1. Pitch-black singularity disk (r < 0.50)
                if r < 0.50:
                    core_a = 0.99
                    # Faint distorted violet gravity rim near edge of event horizon
                    rim = smoothstep(0.36, 0.50, r)
                    core_h = 0.01 + 0.22 * rim * rim

                # 2. Blinding thin gravitational photon ring at r = 0.52!
                dr_ph = abs(r - (0.52 + 0.02 * pulse))
                if dr_ph < 0.075:
                    pu = 1.0 - dr_ph / 0.075
                    flick = 0.78 + 0.22 * math.sin(ang * 5.0 - phase * 2.0)
                    pa = smoothstep(0.0, 0.30, pu) * 0.99
                    if pa >= core_a * 0.9:
                        core_a = max(core_a, pa)
                        core_h = max(core_h, 0.52 + 0.48 * pu * flick)

                # 3. Inward swirling dark-violet gravity tendrils (0.52 < r < 0.95)
                if 0.48 < r < 0.96:
                    spiral = 0.5 + 0.5 * math.sin(ang * 4.0 + r * 10.5 - phase * 2.0 + (n - 0.5) * 2.5)
                    fade = smoothstep(0.96, 0.54, r)
                    sa = fade * smoothstep(0.30, 0.85, spiral) * (0.72 + 0.28 * n)
                    if sa > core_a:
                        core_a = sa
                        core_h = 0.22 + 0.54 * spiral * fade

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - 0.54) / 0.20) ** 2) * 0.62 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(DARK_VOID_RAMP, core_h, core_a, 0.66, glow_a)

    return img


# --- 16. black_hole_accretion.png (512x128, 4 frames of 128x128) -------------

def make_black_hole_accretion() -> Image.Image:
    """
    Classic DNF Black Hole Swirling Accretion Vortex Disk (湮灭黑洞 扭曲星云吸积盘):
    Multi-arm dark-violet galactic vortex disk rendered on 3D tilted planes around the singularity!
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
                if r > 1.01 or r < 0.22:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 4.8, qy * 4.8, f * 17 + 13, 3)

                # Logarithmic 4-arm inward pulling galaxy vortex
                spiral_a = math.sin(ang * 4.0 + math.log(max(0.12, r)) * 5.8 - phase * 2.0 + (n - 0.5) * 1.8)
                spiral_b = math.sin(ang * 2.0 + math.log(max(0.12, r)) * 4.2 - phase * 2.0)
                arm = clamp(0.45 + 0.40 * spiral_a + 0.25 * spiral_b)
                band = smoothstep(0.22, 0.34, r) * smoothstep(0.98, 0.42, r)
                core_a = clamp(band * (0.28 + 0.85 * (arm ** 1.35)))

                # Blinding violet-white inner rim near r=0.34 -> deep abyssal indigo outer arms
                inner_hot = math.exp(-((r - 0.34) / 0.11) ** 2)
                core_h = clamp(0.10 + 0.52 * arm * smoothstep(0.96, 0.34, r) + 0.48 * inner_hot)

                # Siphoned starlight debris specks spiraling in
                sp = hash2(x // 2, y // 2, f * 37 + 5)
                if sp > 0.982 and 0.30 < r < 0.92:
                    core_a = max(core_a, 0.92)
                    core_h = max(core_h, 0.92)

                rr = math.hypot(rx, ry)
                glow_a = smoothstep(0.20, 0.36, rr) * smoothstep(0.99, 0.45, rr) * 0.42
                px[f * size + x, y] = pack_ramp_pixel(DARK_VOID_RAMP, core_h, core_a, 0.62, glow_a)

    return img


# --- 17. sky_lightning_pillar.png (1024x128, 8 frames of 128x128) ------------

def make_sky_lightning_pillar() -> Image.Image:
    """
    Classic DNF Heavenly Thunder Pillar (天雷 贯天苍穹神雷柱):
    8 frames of a massive sky-to-earth golden-cyan-white thunder column with jagged high-voltage
    contour, helical electric coils, branching outer arcs, and scorching base flash!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Instant blinding drop frames 0..1, roaring peak 2..4, crackling shatter 5..7
        env = 1.0 if f <= 4 else clamp(1.0 - 0.72 * ((f - 4) / 3.0))
        width_scale = (0.78 + 0.32 * math.sin(min(1.0, t * 2.2) * math.pi * 0.65)) * (1.0 - 0.25 * max(0.0, t - 0.5))

        # Jagged side lightning tendrils snapping out from the main column
        side_arcs: list[list[tuple[float, float]]] = []
        for b in range(5):
            sy0 = lerp(-0.82, 0.78, hash2(b, f * 7 + 1, 809))
            sgn = 1.0 if (b + f) % 2 == 0 else -1.0
            pts = [(0.0, sy0)]
            for s in range(1, 5):
                u = s / 4.0
                px_b = sgn * (0.18 + 0.72 * u)
                py_b = sy0 + (hash2(s, b * 13 + f, 821) - 0.35) * 0.40 * u
                pts.append((px_b, py_b))
            side_arcs.append(pts)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 5.0, qy * 6.5 - t * 6.0, f * 17 + 3, 2)
                jag = 0.08 * math.sin(qy * 15.0 + f * 2.1) + 0.07 * (n - 0.5)
                base_flare = 0.34 * math.exp(-((qy - 0.92) / 0.20) ** 2)
                col_w = (0.32 + base_flare) * width_scale + 0.08 * (n - 0.4)
                dx = abs(qx - jag)

                core_a = 0.0
                core_h = 0.18

                if dx < col_w:
                    wu = 1.0 - dx / max(0.03, col_w)
                    breakup = 1.0 if (f <= 4 or n > (f - 4) * 0.28) else 0.16
                    core_a = clamp(smoothstep(0.0, 0.24, wu) * env * breakup * 1.32)
                    # Golden outer voltage sheath -> electric cyan-white blinding core spine!
                    core_h = clamp(0.28 + 0.52 * (wu ** 0.65) + 0.32 * smoothstep(0.50, 0.92, wu))

                # Twin high-voltage helical coils wrapping the thunder column
                for sgn in (-1.0, 1.0):
                    hx = sgn * 0.38 * math.sin(qy * 9.0 - t * 14.0 + (0.0 if sgn > 0 else math.pi))
                    dh = abs(qx - hx)
                    if dh < 0.055:
                        hu = 1.0 - dh / 0.055
                        ha = smoothstep(0.0, 0.3, hu) * env * 0.94
                        if ha > core_a * 0.8:
                            core_a = max(core_a, ha)
                            core_h = max(core_h, 0.78 + 0.22 * hu)

                # Jagged side arcs branching outward
                min_ad = 9.0
                for pts in side_arcs:
                    for i in range(len(pts) - 1):
                        d, _ = seg_dist(qx, qy, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
                        if d < min_ad:
                            min_ad = d
                if min_ad < 0.038:
                    au = 1.0 - min_ad / 0.038
                    aa = smoothstep(0.0, 0.32, au) * env * 0.95
                    if aa > core_a:
                        core_a = aa
                        core_h = 0.74 + 0.26 * au

                glow_a = (
                    math.exp(-((rx / (0.48 * width_scale)) ** 2)) * 0.56
                    + math.exp(-((rx / 0.72) ** 2 + ((ry - 0.88) / 0.24) ** 2)) * 0.42
                ) * env * smoothstep(0.99, 0.88, abs(rx))
                px[f * size + x, y] = pack_ramp_pixel(LIGHT_THUNDER_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 18. astral_sky_rift.png (256x256, 1 frame) ------------------------------

def make_astral_sky_rift() -> Image.Image:
    """
    Astral Storm Overhead Celestial Rift / Star Portal (陨星幻灭 天穹星空星盘):
    Torn jagged cosmic nebula rift spilling starlight past a glowing golden astrolabe ring!
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
            n1 = fbm(qx * 4.2, qy * 4.2, 907, 3)
            n2 = fbm(qx * 7.8, qy * 7.8, 919, 2)

            # Swirling cosmic nebula tearing irregularly outward to r=0.98
            swirl = 0.5 + 0.5 * math.sin(ang * 3.0 + r * 6.2 + (n1 - 0.5) * 2.8)
            rift_edge = 0.86 + 0.14 * (n1 - 0.45) + 0.08 * math.sin(ang * 5.0)
            neb = smoothstep(rift_edge, 0.18, r) * (0.50 + 0.44 * swirl + 0.24 * n2)
            core_a = clamp(neb * 0.95)
            core_h = clamp(0.06 + 0.48 * swirl * smoothstep(rift_edge, 0.22, r) + 0.48 * math.exp(-(r / 0.30) ** 2))

            # Subtle inner astrolabe rings (broken by cosmic turbulence so it feels ethereal!)
            for rr_t, rw in ((0.76, 0.028), (0.50, 0.022)):
                dr = abs(r - rr_t)
                if dr < rw and n2 > 0.26:
                    u = 1.0 - dr / rw
                    core_a = max(core_a, 0.92 * smoothstep(0.0, 0.32, u))
                    core_h = max(core_h, 0.72 + 0.28 * u)

            # 12 delicate golden astrolabe ticks inside outer nebula
            a12 = abs(angle_wrap(ang * 12.0)) / 12.0
            if 0.50 < r < 0.76 and r * math.sin(a12) < 0.015 and n2 > 0.32:
                core_a = max(core_a, 0.84)
                core_h = max(core_h, 0.82)

            # Bright twinkling starfield specks inside portal
            if r < rift_edge * 0.94:
                st = hash2(x // 2, y // 2, 941)
                if st > 0.978:
                    core_a = max(core_a, 0.98)
                    core_h = 0.98

            rr = math.hypot(rx, ry)
            glow_a = math.exp(-(rr / 0.68) ** 2) * 0.58 * smoothstep(0.99, 0.84, rr)
            px[x, y] = pack_ramp_pixel(ASTRAL_COSMIC_RAMP, core_h, core_a, 0.64, glow_a)

    return img


# --- 19. astral_meteor_rock.png (512x128, 4 frames: 0=Light, 1=Fire, 2=Ice, 3=Dark) -

def make_astral_meteor_rock() -> Image.Image:
    """
    Classic DNF Astral Storm 4-Element Meteors (陨星幻灭 四元素专属陨星):
    Rugged, asymmetrical craggy celestial rock boulders (NOT symmetric starfish!)
    blazing with elemental magma/crystal/lightning/void cracks & atmospheric reentry corona:
      Frame 0 (Light): Radiant golden astral monolith meteor + crackling solar core
      Frame 1 (Fire):  Charred craggy volcanic meteorite blazing with molten fissures
      Frame 2 (Ice):   Jagged asymmetric glacial comet rock with sharp crystal facets
      Frame 3 (Dark):  Fractured obsidian void boulder glowing with neon-violet rifts
    """
    frames, size = 4, 128
    ramps = (LIGHT_THUNDER_RAMP, FIRE_VOLCANO_RAMP, ICE_GLACIAL_RAMP, DARK_VOID_RAMP)
    # Asymmetric irregular boulder profiles per element (low-amplitude multi-harmonic rock facets)
    harmonics = (
        ((2, 0.06, 0.4), (3, 0.05, 1.8), (5, 0.04, 0.9)),
        ((2, 0.07, 1.1), (3, 0.06, 2.5), (5, 0.05, 0.2)),
        ((2, 0.09, -0.5), (3, 0.06, 0.8), (4, 0.05, 2.1)),
        ((2, 0.08, 2.0), (3, 0.07, 0.3), (5, 0.04, 1.5)),
    )
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        ramp = ramps[f]
        h_spec = harmonics[f]
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n1 = fbm(qx * 4.8, qy * 4.8, f * 31 + 101, 3)
                n2 = fbm(qx * 9.2, qy * 9.2, f * 47 + 211, 2)

                # Rugged solid boulder silhouette (mean radius ~0.68, irregular rocky facets)
                shape_r = 0.68 + 0.10 * (n1 - 0.5)
                for freq, amp, ph in h_spec:
                    shape_r += amp * math.cos(ang * freq + ph)

                core_a = 0.0
                core_h = 0.10

                if r < shape_r:
                    u = clamp(1.0 - r / max(0.1, shape_r))
                    core_a = clamp(smoothstep(0.0, 0.18, u) * 0.99)
                    # Directional rocky crater lighting + sharp glowing elemental vein fractures!
                    shade = clamp(0.55 - 0.32 * qx - 0.28 * qy + 0.25 * (n1 - 0.5))
                    vein = smoothstep(0.09, 0.0, abs(n2 - 0.52))
                    rim_burn = smoothstep(0.28, 0.0, u) * 0.72
                    if f == 0:
                        core_h = clamp(0.32 + 0.44 * shade + 0.42 * vein + 0.35 * rim_burn)
                    elif f == 1:
                        # Dark volcanic basalt boulder + blazing magma vein network + white-hot reentry rim!
                        core_h = clamp(0.06 + 0.22 * shade + 0.78 * vein + 0.56 * rim_burn)
                    elif f == 2:
                        # Sharp faceted glacial ice boulder
                        facet = 0.22 if (qx * 0.8 + qy * 0.6 + (n1 - 0.5) * 0.4) < 0.0 else 0.56
                        core_h = clamp(facet + 0.28 * shade + 0.44 * vein + 0.34 * rim_burn)
                    else:
                        # Pitch-black obsidian void asteroid + neon-violet glowing fracture veins!
                        core_h = clamp(0.03 + 0.16 * shade + 0.84 * vein + 0.58 * rim_burn)

                # Swirling atmospheric plasma reentry flame hugging boulder edge
                if shape_r - 0.06 < r < 0.95:
                    corona = math.exp(-((r - shape_r) / 0.13) ** 2) * (0.50 + 0.50 * n1)
                    if corona > core_a:
                        core_a = clamp(corona * 0.88)
                        core_h = 0.70 + 0.26 * n2

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.68) ** 2) * 0.64 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(ramp, core_h, core_a, 0.72, glow_a)

    return img


# --- 20. astral_chaos_core.png (1024x128, 8 frames of 128x128) ---------------

def make_astral_chaos_core() -> Image.Image:
    """
    Classic DNF Astral Storm Grand Finisher — Chaotic Main Star Core (混沌主星核):
    Colossal blazing 4-element celestial star-planet fusing Light Gold, Fire Orange,
    Ice Cyan, and Dark Violet spiraling into a blinding incandescent star heart,
    encircled by complete intersecting glowing planetary rings and razor-sharp starlight flares!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    elem_rgb = (
        (255, 224, 92),   # Light
        (255, 114, 32),   # Fire
        (104, 228, 255),  # Ice
        (182, 92, 255),   # Dark
    )

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
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.0 + math.cos(phase), qy * 5.0 + math.sin(phase), f * 23 + 7, 3)

                arm_angle = (ang + r * 4.8 - phase) % math.tau
                q_float = (arm_angle / math.tau) * 4.0
                q0 = int(math.floor(q_float)) & 3
                q1 = (q0 + 1) & 3
                qf = smoothstep(0.0, 1.0, q_float - math.floor(q_float))
                c0, c1 = elem_rgb[q0], elem_rgb[q1]
                e_col = (lerp(c0[0], c1[0], qf), lerp(c0[1], c1[1], qf), lerp(c0[2], c1[2], qf))

                core_a = 0.0
                col = e_col

                # Back-half of crossed elliptical planetary rings (behind planet sphere)
                for tilt in (0.48, -0.48):
                    ca, sa = math.cos(tilt), math.sin(tilt)
                    lx = qx * ca - qy * sa
                    ly = qx * sa + qy * ca
                    if ly < 0.0:
                        er = math.hypot(lx / 0.90, ly / 0.28)
                        dr = abs(er - 1.0)
                        if dr < 0.13:
                            ru = 1.0 - dr / 0.13
                            ra = smoothstep(0.0, 0.35, ru) * 0.85
                            if ra > core_a:
                                core_a = ra
                                col = (lerp(e_col[0] * 0.85, 255.0, 0.40 * ru),
                                       lerp(e_col[1] * 0.85, 240.0, 0.40 * ru),
                                       lerp(e_col[2] * 0.85, 255.0, 0.40 * ru))

                # Main blazing 4-element star-planet sphere (r < 0.64)
                if r < 0.64:
                    u = clamp(1.0 - r / 0.64)
                    swirl = 0.5 + 0.5 * math.sin(ang * 4.0 + r * 10.0 - phase * 2.0 + (n - 0.5) * 2.2)
                    core_a = clamp(smoothstep(0.0, 0.22, u) * 0.99)
                    white_hot = math.exp(-(r / (0.30 + 0.03 * pulse)) ** 2)
                    dark_edge = smoothstep(0.46, 0.64, r)
                    col = (
                        lerp(e_col[0] * (0.48 + 0.52 * swirl) * (1.0 - 0.58 * dark_edge), 255.0, white_hot),
                        lerp(e_col[1] * (0.48 + 0.52 * swirl) * (1.0 - 0.58 * dark_edge), 252.0, white_hot),
                        lerp(e_col[2] * (0.48 + 0.52 * swirl) * (1.0 - 0.58 * dark_edge), 240.0, white_hot),
                    )

                # Front-half of crossed elliptical planetary rings looping cleanly IN FRONT of the sphere!
                for tilt in (0.48, -0.48):
                    ca, sa = math.cos(tilt), math.sin(tilt)
                    lx = qx * ca - qy * sa
                    ly = qx * sa + qy * ca
                    if ly >= -0.02:
                        er = math.hypot(lx / 0.90, ly / 0.28)
                        dr = abs(er - 1.0)
                        if dr < 0.13:
                            ru = 1.0 - dr / 0.13
                            ra = smoothstep(0.0, 0.35, ru) * 0.96
                            core_a = max(core_a, ra)
                            col = (lerp(e_col[0], 255.0, 0.72 * ru),
                                   lerp(e_col[1], 248.0, 0.72 * ru),
                                   lerp(e_col[2], 228.0, 0.72 * ru))

                # Razor-sharp tapering 4-point celestial starlight glare (never a boxy cross!)
                ax_s, ay_s = abs(qx), abs(qy)
                h_spike = math.exp(-(ay_s / max(0.012, 0.065 * (1.0 - ax_s * 0.92))) ** 2) * smoothstep(0.95, 0.08, ax_s)
                v_spike = math.exp(-(ax_s / max(0.012, 0.065 * (1.0 - ay_s * 0.92))) ** 2) * smoothstep(0.95, 0.08, ay_s)
                spike = max(h_spike, v_spike)
                if spike > 0.28:
                    su = clamp((spike - 0.28) / 0.72)
                    core_a = max(core_a, su * 0.96)
                    col = (lerp(col[0], 255.0, su), lerp(col[1], 253.0, su), lerp(col[2], 240.0, su))

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.66) ** 2) * (0.68 + 0.10 * pulse) * smoothstep(0.99, 0.85, rr)
                px[f * size + x, y] = pack_rgb_blend(col, core_a, e_col, glow_a)

    return img


# --- 21. astral_supernova_burst.png (1024x128, 8 frames of 128x128) ----------

def make_astral_supernova_burst() -> Image.Image:
    """
    8-frame colossal razor-sharp Supernova Starburst Explosion (超新星大爆):
    Spiky jagged energy shock ring + razor-thin 12-ray cosmic blast spikes (zero puffy flower look!).
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        exp_r = 0.22 + 0.74 * ease_out_cubic(t)
        ring_w = lerp(0.24, 0.11, t)
        env = 1.0 - 0.72 * smoothstep(0.55, 1.0, t)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                r = math.hypot(qx, qy)
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.8, qy * 5.8, f * 37 + 11, 3)

                # Jagged turbulent shockwave front (sharp noise disruption, not daisy petals)
                jag = 0.16 * (n - 0.5) + 0.05 * math.sin(ang * 13.0 + t * 2.0)
                dr = abs(r - (exp_r + jag))
                core_a = 0.0
                core_h = 0.20

                if dr < ring_w:
                    u = 1.0 - dr / ring_w
                    breakup = 1.0 if (t < 0.56 or n > (t - 0.44) * 1.42) else 0.14
                    core_a = clamp(smoothstep(0.0, 0.28, u) * env * breakup * 1.28)
                    core_h = clamp(0.24 + 0.76 * u)

                # 12 needle-sharp asymmetric blast rays piercing past the shockwave!
                a12 = abs(angle_wrap((ang + t * 0.25) * 12.0)) / 12.0
                ray_len = exp_r + 0.14 + 0.14 * math.sin(ang * 4.0)
                ray_w = 0.052 * (1.0 - 0.75 * clamp(r / max(0.1, ray_len)))
                if a12 < ray_w and r < min(0.96, ray_len):
                    ru = (1.0 - a12 / max(0.008, ray_w)) * smoothstep(min(0.96, ray_len), 0.06, r)
                    ra = ru * env * 0.98
                    if ra > core_a:
                        core_a = ra
                        core_h = 0.72 + 0.28 * ru

                # Early blinding white-hot collapse flash (frames 0..3)
                if t < 0.50 and r < exp_r * 0.92:
                    hu = clamp(1.0 - r / max(0.05, exp_r * 0.92))
                    ha = smoothstep(0.0, 0.30, hu) * (1.0 - t / 0.50) * 0.98
                    if ha > core_a:
                        core_a = ha
                        core_h = 0.68 + 0.32 * hu

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - exp_r * 0.70) / 0.26) ** 2) * 0.56 * env * smoothstep(0.99, 0.85, rr)
                px[f * size + x, y] = pack_ramp_pixel(LUMINANCE_RAMP, core_h, core_a, 0.85, glow_a)

    return img


# --- 22. frost_blizzard_mist.png (512x128, 4 frames of 128x128) --------------

def make_frost_blizzard_mist() -> Image.Image:
    """
    Swirling icy blizzard mist + crisp crystal snowflakes for Arctic Feast, Ice Wall,
    and (dark-tinted) Lightning Strike Storm Cloud!
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
                if r > 1.01:
                    continue
                ang = math.atan2(qy, qx)
                n1 = fbm(qx * 3.8 + math.cos(phase) * 0.6, qy * 3.8 + math.sin(phase) * 0.6, f * 23 + 31, 3)
                n2 = fbm(qx * 7.2 - math.sin(phase) * 0.5, qy * 7.2 + math.cos(phase) * 0.5, f * 37 + 53, 2)

                swirl = 0.5 + 0.5 * math.sin(ang * 2.0 + r * 4.5 - phase)
                cloud = smoothstep(0.95, 0.18, r) * clamp(0.32 + 0.56 * n1 + 0.22 * swirl)
                core_a = clamp(cloud * 0.86)
                core_h = clamp(0.28 + 0.52 * n2 + 0.20 * swirl)

                # Sparkling crisp frost flakes embedded in mist
                sp = hash2(x // 2, y // 2, f * 59 + 17)
                if sp > 0.980 and r < 0.88:
                    core_a = max(core_a, 0.95)
                    core_h = 0.98

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-(rr / 0.66) ** 2) * 0.34 * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(ICE_GLACIAL_RAMP, core_h, core_a, 0.78, glow_a)

    return img


# --- Contact Sheet Preview Generator -----------------------------------------

def save_contact_sheet(assets: dict[str, Image.Image], out_path: Path, bg=(10, 12, 22, 255)) -> None:
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
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(214, 212, 255, 255))
        y_cur += label_h
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(58, 56, 92, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "rune_lamp_orb.png": make_rune_lamp_orb(),
        "charge_arcane_circle.png": make_charge_arcane_circle(),
        "ice_hexagram_circle.png": make_ice_hexagram_circle(),
        "thunder_reticle_circle.png": make_thunder_reticle_circle(),
        "volcano_magma_crater.png": make_volcano_magma_crater(),
        "astral_star_circle.png": make_astral_star_circle(),
        "ice_wall_pillar.png": make_ice_wall_pillar(),
        "lightning_vortex_orb.png": make_lightning_vortex_orb(),
        "void_sphere_orb.png": make_void_sphere_orb(),
        "flame_pillar_column.png": make_flame_pillar_column(),
        "jack_pumpkin_head.png": make_jack_pumpkin_head(),
        "jack_lava_burst.png": make_jack_lava_burst(),
        "arctic_ice_spire.png": make_arctic_ice_spire(),
        "black_hole_core.png": make_black_hole_core(),
        "black_hole_accretion.png": make_black_hole_accretion(),
        "sky_lightning_pillar.png": make_sky_lightning_pillar(),
        "astral_sky_rift.png": make_astral_sky_rift(),
        "astral_meteor_rock.png": make_astral_meteor_rock(),
        "astral_chaos_core.png": make_astral_chaos_core(),
        "astral_supernova_burst.png": make_astral_supernova_burst(),
        "frost_blizzard_mist.png": make_frost_blizzard_mist(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "elementalist_contact_sheet.png")


if __name__ == "__main__":
    main()

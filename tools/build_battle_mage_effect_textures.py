#!/usr/bin/env python3
"""
Dedicated DNF-inspired Battle Mage / Bellatrix / Teana (战斗法师 / 贝亚娜斗神)
pixel-sharp + soft-feathered VFX sprite sheet & held-weapon texture generator.

Outputs pre-baked multi-tone RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/battle_mage/
Custom 32x32 held effect weapon textures & JSONs under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect/{teana_spear,dragon_halberd}.png
  src/main/resources/assets/yumis_dungeon_and_fight/models/item/effect/{teana_spear,dragon_halberd}.json
  src/main/resources/assets/yumis_dungeon_and_fight/items/effect/{teana_spear,dragon_halberd}.json
And dark/light checkerboard contact sheets for visual inspection under:
  temp/battle-mage-vfx/contact_sheet_dark.png
  temp/battle-mage-vfx/contact_sheet_light.png
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT_VFX = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/battle_mage"
OUT_ITEM_TEX = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect"
OUT_ITEM_MODEL = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/models/item/effect"
OUT_ITEM_DEF = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/items/effect"
PREVIEW_DIR = ROOT / "temp/battle-mage-vfx"


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


def corner_mask(nx: float, ny: float, start: float = 0.86, end: float = 0.99) -> float:
    r = math.hypot(nx, ny)
    return 1.0 - smoothstep(start, end, r)


# --- Classic DNF Battle Mage High-Contrast 7-Stop Multi-Tone Ramps -----------

NEUTRAL_CHASER_RAMP = (
    (0.00, (16, 24, 44)),     # deep steel indigo shadow
    (0.18, (48, 72, 118)),    # dark silver-blue rim
    (0.40, (112, 154, 212)),  # crisp astral silver-blue
    (0.62, (186, 214, 245)),  # radiant pearl silver
    (0.82, (228, 244, 255)),  # luminous star-silver
    (1.00, (255, 255, 255)),  # white-hot star core
)

LIGHT_CHASER_RAMP = (
    (0.00, (36, 22, 4)),      # dark storm bronze
    (0.18, (124, 78, 12)),    # deep amber gold
    (0.42, (230, 174, 28)),   # radiant thunder gold
    (0.66, (255, 224, 86)),   # vivid DNF light chaser yellow
    (0.86, (218, 248, 255)),  # electric cyan-white arc
    (1.00, (255, 255, 252)),  # blinding flash core
)

FIRE_CHASER_RAMP = (
    (0.00, (28, 6, 4)),       # charred obsidian ember
    (0.18, (122, 18, 10)),    # deep crimson shadow
    (0.42, (222, 58, 16)),    # roaring flame vermilion
    (0.66, (255, 134, 30)),   # vivid fire chaser orange
    (0.86, (255, 212, 82)),   # incandescent flame gold
    (1.00, (255, 250, 220)),  # white-hot flame core
)

ICE_CHASER_RAMP = (
    (0.00, (8, 20, 54)),      # abyssal glacial navy
    (0.18, (20, 74, 158)),    # deep frost sapphire
    (0.44, (50, 162, 242)),   # vivid DNF ice chaser blue
    (0.68, (128, 228, 255)),  # luminous crystal cyan
    (0.86, (218, 250, 255)),  # frosted crystal highlight
    (1.00, (252, 255, 255)),  # pure specular ice edge
)

DARK_CHASER_RAMP = (
    (0.00, (8, 2, 18)),       # pitch shadow black-violet
    (0.18, (42, 10, 82)),     # abyssal shadow indigo
    (0.42, (112, 34, 194)),   # rich DNF shadow violet
    (0.66, (176, 86, 252)),   # vivid neon amethyst
    (0.86, (226, 172, 255)),  # luminous lilac eclipse
    (1.00, (252, 242, 255)),  # blinding dark-core flash
)

TEANA_GOLD_RAMP = (
    (0.00, (42, 22, 4)),      # antique temple bronze shadow
    (0.16, (118, 64, 10)),    # burnished deep imperial gold
    (0.38, (214, 136, 18)),   # warm royal amber-gold
    (0.60, (255, 198, 42)),   # iconic DNF Teana radiant gold
    (0.78, (255, 232, 128)),  # sacred luminous sun-gold
    (0.92, (255, 248, 206)),  # ivory-gold feather glow
    (1.00, (255, 255, 248)),  # white-hot divine core
)

DRAGON_HALBERD_RAMP = (
    (0.00, (38, 12, 4)),      # dark dragon-scale bronze
    (0.16, (124, 34, 8)),     # imperial crimson-bronze
    (0.36, (208, 94, 14)),    # molten dragon amber
    (0.58, (255, 172, 32)),   # blazing imperial dragon gold
    (0.78, (255, 224, 104)),  # golden dragon-fire edge
    (0.92, (255, 246, 196)),  # incandescent dragon spirit
    (1.00, (255, 255, 244)),  # white-hot roar core
)

SAKURA_MARTIAL_RAMP = (
    (0.00, (42, 6, 18)),      # dark crimson-plum silhouette
    (0.16, (124, 18, 46)),    # deep martial crimson-rose
    (0.36, (218, 48, 86)),    # vivid DNF sakura-crimson
    (0.58, (255, 106, 134)),  # blooming cherry-blossom rose
    (0.78, (255, 178, 186)),  # luminous sakura-peach glow
    (0.92, (255, 230, 222)),  # warm cherry-white sheen
    (1.00, (255, 252, 248)),  # white-hot impact core
)

METEOR_STRIKE_RAMP = (
    (0.00, (36, 6, 8)),       # dark scorched crimson
    (0.18, (132, 20, 14)),    # deep meteor red
    (0.38, (232, 64, 24)),    # roaring comet scarlet-orange
    (0.60, (255, 138, 38)),   # scorching meteor amber
    (0.80, (255, 212, 96)),   # blazing solar gold tip
    (0.93, (255, 244, 198)),  # white-gold plasma cone
    (1.00, (255, 255, 250)),  # blinding comet starhead
)

TINTABLE_RAMP = (
    (0.00, (22, 20, 30)),     # deep dark silhouette backing (pops on daytime grass/sky!)
    (0.18, (72, 70, 84)),     # strong shadowed contour body
    (0.42, (158, 156, 170)),  # crisp mid tint body
    (0.68, (222, 222, 232)),  # bright highlight
    (0.88, (246, 246, 250)),  # hot inner rim
    (1.00, (255, 255, 255)),  # pure white core
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


def pack_ramp(
    ramp: tuple[tuple[float, tuple[int, int, int]], ...],
    heat: float, core_a: float,
    glow_heat: float = 0.56, glow_a: float = 0.0,
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


def pack_dual_rgb(
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


# --- 1. chaser_orb.png (768x128, 6 frames of 128x128) ------------------------

def make_chaser_orb() -> Image.Image:
    """
    Iconic DNF Battle Mage 5-color Chaser Orbs + Teana Golden Chaser!
    Each frame (128x128) is a complete luminous arcane orb framed by a rotating
    inscribed rune ring, crisp dark backing rim, and bespoke element crest:
      0 = Neutral (银白星芒六芒纹)
      1 = Light   (雷金锯齿电弧纹)
      2 = Fire    (炽红三叉炎纹)
      3 = Ice     (霜蓝六瓣冰晶纹)
      4 = Dark    (暗紫蚀月漩涡纹)
      5 = Teana   (贝亚娜炽金双翼圣徽)
    """
    frames, size = 6, 128
    ramps = (
        NEUTRAL_CHASER_RAMP,
        LIGHT_CHASER_RAMP,
        FIRE_CHASER_RAMP,
        ICE_CHASER_RAMP,
        DARK_CHASER_RAMP,
        TEANA_GOLD_RAMP,
    )
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        ramp = ramps[f]
        ox = f * size
        for y in range(size):
            ny = (y + 0.5) / size * 2.0 - 1.0
            for x in range(size):
                nx = (x + 0.5) / size * 2.0 - 1.0
                r = math.hypot(nx, ny)
                if r >= 0.96:
                    continue
                ang = math.atan2(ny, nx)
                n = fbm(nx * 4.5 + f * 3.1, ny * 4.5 - f * 2.3, seed=100 + f * 17, octaves=3)

                # Outer arcane glyph halo ring (radius 0.68..0.82)
                ring_d = abs(r - 0.74)
                ring_outline = 1.0 - smoothstep(0.052, 0.088, ring_d)
                ring_body = 1.0 - smoothstep(0.022, 0.048, ring_d)
                # Inscribed rune dashes & cardinal nodes around outer ring
                ticks = 12 if f == 5 else 8
                tick_ang = abs(angle_wrap(ang * ticks))
                rune_notch = (1.0 - smoothstep(0.32, 0.72, tick_ang)) * (1.0 - smoothstep(0.025, 0.075, abs(r - 0.74)))
                # 4 orbital diamond studs on the ring
                stud_ang = abs(angle_wrap((ang - (math.pi * 0.25 if f % 2 == 1 else 0.0)) * 4.0))
                stud = (1.0 - smoothstep(0.18, 0.36, stud_ang)) * (1.0 - smoothstep(0.03, 0.11, abs(r - 0.74)))

                # Inner chaser energy sphere (radius 0.54)
                orb_r = 0.55 if f == 5 else 0.50
                orb_rim = 1.0 - smoothstep(orb_r - 0.02, orb_r + 0.055, r)
                orb_fill = 1.0 - smoothstep(orb_r - 0.07, orb_r + 0.01, r)
                # Subtle 3D spherical shading (top-left highlight, bottom-right deep shadow)
                sphere_z = math.sqrt(max(0.0, 1.0 - min(1.0, (r / max(0.01, orb_r)) ** 2)))
                spec = clamp(1.0 - math.hypot(nx + 0.16, ny + 0.18) / 0.34)
                # Swirling plasma veins inside orb
                swirl_ang = ang + r * 3.6 + f * 0.7
                swirl = 0.5 + 0.5 * math.sin(swirl_ang * 3.0 + n * 4.2)

                # Element-specific inner crest emblem
                crest = 0.0
                if f == 0:
                    # Neutral: sharp 6-point martial star
                    star_r = 0.22 + 0.16 * (0.5 + 0.5 * math.cos(ang * 6.0))
                    crest = (1.0 - smoothstep(0.0, 0.055, r - star_r)) * smoothstep(0.08, 0.16, r)
                    crest = max(crest, 1.0 - smoothstep(0.08, 0.15, r))
                elif f == 1:
                    # Light: crackling double-zag lightning bolt
                    d1, _ = seg_dist(nx, ny, -0.06, -0.32, 0.12, -0.04)
                    d2, _ = seg_dist(nx, ny, 0.12, -0.04, -0.10, 0.04)
                    d3, _ = seg_dist(nx, ny, -0.10, 0.04, 0.08, 0.32)
                    crest = 1.0 - smoothstep(0.035, 0.075, min(d1, d2, d3))
                elif f == 2:
                    # Fire: roaring triple flame crest rising inside orb
                    f1 = 1.0 - smoothstep(0.0, 0.16, math.hypot(nx * 1.4, (ny + 0.04) * 0.95) - 0.10 * (1.0 - ny))
                    f2 = 1.0 - smoothstep(0.0, 0.11, math.hypot((nx - 0.16) * 1.5, (ny - 0.04) * 1.1) - 0.05)
                    f3 = 1.0 - smoothstep(0.0, 0.11, math.hypot((nx + 0.16) * 1.5, (ny - 0.04) * 1.1) - 0.05)
                    crest = max(f1, f2 * 0.85, f3 * 0.85)
                elif f == 3:
                    # Ice: crisp 6-spoke crystal snowflake
                    spoke = abs(math.sin(ang * 3.0)) * r
                    bar = (1.0 - smoothstep(0.022, 0.052, spoke)) * (1.0 - smoothstep(0.32, 0.38, r))
                    hex_d = abs(r * math.cos(angle_wrap(ang * 6.0) / 6.0) - 0.18)
                    hex_ring = 1.0 - smoothstep(0.018, 0.048, hex_d)
                    crest = max(bar, hex_ring * 0.92)
                elif f == 4:
                    # Dark: menacing crescent eclipse + dark void pupil
                    outer_c = 1.0 - smoothstep(0.24, 0.30, r)
                    cut_c = 1.0 - smoothstep(0.18, 0.25, math.hypot(nx - 0.11, ny + 0.06))
                    crest = clamp(outer_c - cut_c * 0.95)
                    # Add orbiting dark spiral wisps
                    sp_d = abs(r - 0.34 - 0.05 * math.sin(ang * 2.0))
                    crest = max(crest, (1.0 - smoothstep(0.018, 0.045, sp_d)) * 0.72)
                else:
                    # Teana: sacred angelic twin wings + radiant 8-ray sun cross!
                    star = (1.0 - smoothstep(0.025, 0.062, abs(nx) * (1.0 + abs(ny) * 2.2))) * (1.0 - smoothstep(0.36, 0.44, abs(ny)))
                    star = max(star, (1.0 - smoothstep(0.025, 0.062, abs(ny) * (1.0 + abs(nx) * 2.2))) * (1.0 - smoothstep(0.36, 0.44, abs(nx))))
                    # Mini angel wing arches flanking the inner orb
                    ax = abs(nx)
                    wing_curve = -0.18 + 0.42 * (ax - 0.28) ** 2 - 0.18 * (ax - 0.28)
                    wing = (1.0 - smoothstep(0.04, 0.10, abs(ny - wing_curve))) * smoothstep(0.24, 0.34, ax) * (1.0 - smoothstep(0.68, 0.80, ax))
                    crest = max(star, wing * 0.95)

                # Combine layers with crisp dark outer outline + bright core
                core_a = max(orb_rim * 0.96, ring_outline * 0.85, stud * 0.95)
                if f == 5:
                    core_a = max(core_a, crest * 0.92)

                # Heat gradient: dark silhouette on outer borders -> saturated body -> white-hot crest/core
                heat = 0.10
                if ring_outline > 0.05:
                    heat = lerp(0.08, 0.68 + 0.24 * rune_notch, ring_body)
                    if stud > 0.3:
                        heat = max(heat, 0.86)
                if orb_rim > 0.05:
                    inner_h = 0.16 + 0.34 * sphere_z + 0.20 * swirl + 0.28 * (spec ** 1.6)
                    if f == 4:
                        # Dark chaser has true deep black-violet interior with blazing lilac eclipse crest
                        inner_h = 0.06 + 0.28 * (1.0 - sphere_z) + 0.18 * swirl
                    heat = lerp(0.08, inner_h, orb_fill)
                if crest > 0.05:
                    heat = max(heat, 0.72 + 0.28 * crest)
                    core_a = max(core_a, crest * 0.96)

                # Soft atmospheric aura glow around the orb (fades to 0 before edges)
                cm = corner_mask(nx, ny, 0.78, 0.95)
                glow_a = clamp((1.0 - smoothstep(0.32, 0.94, r)) * (0.48 if f == 5 else 0.38)) * cm
                core_a *= cm
                px[ox + x, y] = pack_ramp(ramp, heat, core_a, glow_heat=0.62, glow_a=glow_a)

    return img


# --- 2. palm_blossom_burst.png (512x64, 8 frames of 64x64) -------------------

def make_palm_blossom_burst() -> Image.Image:
    """
    Classic DNF 落花掌 (Palm Blast):
    Explosive sakura-crimson martial palm qi burst + unmistakable fluttering sakura blossoms!
      Frames 0-2: Crisp blooming 5-petal sakura crest + expanding dark-rose/sakura qi shock ring
      Frames 3-7: Bursts into large, sculpted notched cherry-blossom petals (落花) spiraling outward!
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # 8 prominent fluttering cherry-blossom petals bursting radially with clear silhouettes
    petals = []
    for i in range(8):
        ang0 = i * (math.tau / 8.0) + (i % 2) * 0.22
        speed = 0.34 + 0.36 * hash2(i, 7, 21)
        spin = (1.8 if i % 2 == 0 else -2.1) * (0.85 + 0.35 * hash2(i, 13, 21))
        psize = 0.175 + 0.055 * hash2(i, 19, 21)
        rad0 = 0.12 if i % 2 == 0 else 0.22
        petals.append((ang0, speed, spin, psize, rad0))

    for f in range(frames):
        t = f / (frames - 1)
        eu = ease_out_cubic(t)
        ox = f * size
        env = smoothstep(0.0, 0.06, t + 0.08) * (1.0 - 0.80 * smoothstep(0.58, 1.0, t))
        ring_r = 0.20 + 0.64 * eu
        ring_w = lerp(0.15, 0.05, t)

        for y in range(size):
            ny = (y + 0.5) / size * 2.0 - 1.0
            for x in range(size):
                nx = (x + 0.5) / size * 2.0 - 1.0
                r = math.hypot(nx, ny)
                if r >= 0.98:
                    continue
                ang = math.atan2(ny, nx)
                cm = corner_mask(nx, ny, 0.84, 0.98)
                n = fbm(nx * 5.0 + t * 2.2, ny * 5.0 - t * 1.8, seed=220 + f * 11, octaves=3)

                # Primary martial qi shock ring (fades faster so fluttering petals shine on frames 3-7)
                ring_env = env * (1.0 - 0.55 * smoothstep(0.35, 0.90, t))
                rd = abs(r - ring_r + (n - 0.5) * 0.06)
                ring_out = (1.0 - smoothstep(ring_w * 0.58, ring_w * 1.40, rd)) * ring_env
                ring_core = (1.0 - smoothstep(ring_w * 0.12, ring_w * 0.66, rd)) * ring_env

                # Central blooming 5-petal sakura martial seal (Frames 0..3)
                flash_env = max(0.0, 1.0 - t * 1.45)
                blossom_r = (0.24 + 0.22 * eu) * (0.64 + 0.36 * abs(math.cos((ang + t * 0.5) * 2.5)))
                blossom_out = (1.0 - smoothstep(blossom_r * 0.72, blossom_r + 0.07, r)) * flash_env
                blossom_fill = (1.0 - smoothstep(blossom_r * 0.18, blossom_r * 0.82, r)) * flash_env

                # Large sculpted notched sakura petals spiraling outward across the whole canvas
                petal_out = 0.0
                petal_fill = 0.0
                petal_h = 0.0
                p_env = smoothstep(0.0, 0.16, t + 0.06) * (1.0 - 0.72 * smoothstep(0.68, 1.0, t))
                for p_ang, p_spd, p_spin, p_sz, rad0 in petals:
                    dist = rad0 + p_spd * eu * 1.02
                    ca = p_ang + 0.42 * t
                    cx = math.cos(ca) * dist
                    cy = math.sin(ca) * dist
                    dx, dy = nx - cx, ny - cy
                    if dx * dx + dy * dy > (p_sz * 1.7) ** 2:
                        continue
                    rot = ca + p_spin * t
                    cr, sr = math.cos(rot), math.sin(rot)
                    lx = dx * cr + dy * sr
                    ly = -dx * sr + dy * cr
                    px_s = lx / p_sz
                    py_s = ly / (p_sz * 0.70)
                    pd = math.hypot(px_s, py_s)
                    # Classic cherry-blossom apical V-notch
                    notch = smoothstep(0.20, 0.85, px_s) * (1.0 - smoothstep(0.0, 0.42, abs(py_s)))
                    p_metric = pd + notch * 0.44
                    po = (1.0 - smoothstep(0.76, 1.08, p_metric)) * p_env
                    pf = (1.0 - smoothstep(0.52, 0.82, p_metric)) * p_env
                    if po > petal_out:
                        petal_out = po
                        petal_fill = pf
                        petal_h = 0.56 + 0.36 * (1.0 - clamp(pd))

                core_a = max(ring_out * 0.88, blossom_out * 0.96, petal_out * 0.98) * cm
                heat = 0.10
                if ring_out > 0.05:
                    heat = lerp(0.08, 0.76 + 0.16 * n, ring_core / max(1e-4, ring_out))
                if blossom_out > 0.05:
                    heat = max(heat, lerp(0.10, 0.72 + 0.28 * (1.0 - clamp(r / 0.32)), blossom_fill / max(1e-4, blossom_out)))
                if petal_out > 0.05:
                    # Crisp deep crimson-plum outline around large sakura petals, rosy pink inside!
                    heat = lerp(0.08, petal_h, clamp(petal_fill / max(1e-4, petal_out)))

                glow_a = (ring_out * 0.32 + blossom_out * 0.48 + petal_fill * 0.30) * cm
                px[ox + x, y] = pack_ramp(SAKURA_MARTIAL_RAMP, heat, core_a, glow_heat=0.58, glow_a=glow_a)

    return img


# --- 3. spear_thrust.png (512x64, 8 frames of 64x64) -------------------------

def make_spear_thrust() -> Image.Image:
    """
    Razor-sleek high-velocity Battle Mage spear / polearm piercing thrust cone (v=0 tip, v=1 root).
    Features needle spearhead + swept-back 45-degree Mach shock chevrons + bold dark silhouette edge.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ox = f * size
        tip_y = lerp(0.22, 0.03, ease_out_cubic(clamp(t / 0.32)))
        env = smoothstep(0.0, 0.05, t + 0.07) * (1.0 - 0.82 * smoothstep(0.50, 1.0, t))

        for y in range(size):
            v = (y + 0.5) / size
            for x in range(size):
                u = (x + 0.5) / size * 2.0 - 1.0
                ax = abs(u)
                if v < tip_y - 0.03 or v > 0.98:
                    continue
                dv = max(0.0, v - tip_y)
                n = fbm(u * 6.5, v * 9.0 - t * 6.5, seed=310 + f * 13, octaves=3)

                # Razor needle spearhead widening gracefully into a supersonic lance wake
                half_w = clamp((dv / 0.84) ** 0.78 * 0.42) * (1.0 - 0.48 * smoothstep(0.62, 0.98, v))
                half_w *= (0.90 + 0.20 * n)
                cone_out = (1.0 - smoothstep(half_w * 0.60, half_w + 0.09, ax)) * smoothstep(0.98, 0.74, v)
                cone_fill = (1.0 - smoothstep(half_w * 0.32, half_w * 0.82, ax)) * smoothstep(0.95, 0.65, v)
                cone_core = (1.0 - smoothstep(0.015, max(0.035, half_w * 0.36), ax)) * smoothstep(0.90, 0.50, v)

                # Two sharp swept-back V-shaped Mach shock-wave chevrons (swept backward, NOT flat bars!)
                chev_out = 0.0
                chev_fill = 0.0
                for ci in range(2):
                    cv = tip_y + 0.14 + ci * 0.28 + t * 0.22
                    if cv > 0.86:
                        continue
                    cw = 0.48 + ci * 0.22 + t * 0.14
                    # V-chevron sweeps sharply backward (+0.48 * ax) as ax increases!
                    v_target = cv + ax * 0.52
                    cd = abs(v - v_target)
                    w_mask = 1.0 - smoothstep(cw * 0.72, cw + 0.06, ax)
                    c_o = (1.0 - smoothstep(0.032, 0.075, cd)) * w_mask
                    c_f = (1.0 - smoothstep(0.010, 0.038, cd)) * w_mask
                    chev_out = max(chev_out, c_o)
                    chev_fill = max(chev_fill, c_f)

                # Incandescent spear-tip flash star
                tip_flash = (1.0 - smoothstep(0.0, 0.18, math.hypot(ax * 2.1, (v - tip_y - 0.05) * 0.95))) * (1.0 - 0.50 * t)

                edge_fade = (1.0 - smoothstep(0.84, 0.98, ax)) * smoothstep(0.01, 0.04, v) * smoothstep(0.98, 0.90, v)
                core_a = max(cone_out * 0.96, chev_out * 0.94, tip_flash * 0.98) * env * edge_fade

                heat = 0.06
                if cone_out > 0.05:
                    heat = lerp(0.06, 0.58 + 0.40 * cone_core, clamp(cone_fill / max(1e-4, cone_out)))
                if chev_out > 0.05:
                    heat = max(heat, lerp(0.08, 0.84, clamp(chev_fill / max(1e-4, chev_out))))
                if tip_flash > 0.05:
                    heat = max(heat, 0.86 + 0.14 * tip_flash)

                glow_a = (cone_fill * 0.34 + chev_fill * 0.38 + tip_flash * 0.55) * env * edge_fade
                px[ox + x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.68, glow_a=glow_a)

    return img


# --- 4. martial_slash.png (512x64, 8 frames of 64x64) ------------------------

def make_martial_slash() -> Image.Image:
    """
    Bold, dramatic sweeping Battle Mage polearm crescent arc (碎霸 / 天地碎霸 / 普攻横扫).
    Convention: arc crest faces +x (right), sweep unfolds along y axis.
    Frame 0 already opens aggressively; Frames 1-5 are thick sweeping crescents; Frames 6-7 dissipate cleanly.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ox = f * size
        # Aggressive early snap so Frame 0 is immediately impactful and Frames 6-7 still show crisp trailing embers
        head_ang = -1.45 + 3.85 * (1.0 - (1.0 - clamp(t / 0.55)) ** 2.1) if t < 0.55 else 2.40 + 0.30 * ((t - 0.55) / 0.45)
        tail_ang = -2.45 + 4.35 * smoothstep(0.18, 1.0, t)
        env = smoothstep(0.0, 0.04, t + 0.10) * (1.0 - 0.72 * smoothstep(0.62, 1.0, t))

        for y in range(size):
            ny = (y + 0.5) / size * 2.0 - 1.0
            for x in range(size):
                nx = (x + 0.5) / size * 2.0 - 1.0
                r = math.hypot(nx, ny)
                if r < 0.14 or r > 0.97:
                    continue
                ang = math.atan2(ny, nx)
                if ang < tail_ang - 0.20 or ang > head_ang + 0.16:
                    continue
                span = max(0.35, head_ang - tail_ang)
                arc_u = clamp((ang - tail_ang) / span)
                if ang > head_ang:
                    arc_fade = 1.0 - smoothstep(0.0, 0.16, ang - head_ang)
                elif ang < tail_ang:
                    arc_fade = 1.0 - smoothstep(0.0, 0.20, tail_ang - ang)
                else:
                    arc_fade = smoothstep(0.0, 0.16, arc_u) * (1.0 - 0.10 * smoothstep(0.90, 1.0, arc_u))

                n = fbm(nx * 5.5 + t * 2.6, ny * 5.5 - t * 2.1, seed=410 + f * 19, octaves=3)
                outer_r = 0.90 - 0.05 * (1.0 - arc_u) ** 2 + (n - 0.5) * 0.05
                thick = lerp(0.16, 0.46, arc_u ** 0.68) * (1.0 - 0.26 * t)
                inner_r = max(0.20, outer_r - thick)

                band_out = smoothstep(inner_r - 0.07, inner_r + 0.02, r) * (1.0 - smoothstep(outer_r - 0.02, outer_r + 0.06, r))
                lead_edge = (1.0 - smoothstep(0.0, 0.085, abs(r - (outer_r - 0.055)))) * smoothstep(0.15, 0.72, arc_u)
                # Inner kinetic secondary arc trail
                mid_r = lerp(inner_r, outer_r, 0.45)
                mid_streak = (1.0 - smoothstep(0.0, 0.07, abs(r - mid_r))) * (0.55 + 0.45 * n) * arc_u

                cm = corner_mask(nx, ny, 0.86, 0.97)
                core_a = max(band_out, lead_edge) * arc_fade * env * cm
                if core_a <= 0.01:
                    continue
                rad_u = clamp((r - inner_r) / max(0.04, outer_r - inner_r))
                # Strong dark outline on BOTH inner and outer rim so daytime contrast is razor sharp!
                interior = smoothstep(0.0, 0.24, rad_u) * (1.0 - 0.78 * smoothstep(0.84, 1.0, rad_u))
                heat = lerp(0.04, 0.46 + 0.28 * mid_streak + 0.20 * arc_u, interior)
                if lead_edge > 0.35:
                    heat = max(heat, 0.84 + 0.16 * arc_u)
                glow_a = (band_out * 0.34 + lead_edge * 0.62) * arc_fade * env * cm
                px[ox + x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.70, glow_a=glow_a)

    return img


# --- 5. meteor_drill.png (512x128, 8 frames of 64x128) -----------------------

def make_meteor_drill() -> Image.Image:
    """
    Iconic DNF 强袭流星打 (Dragon Strike) blazing pointed comet-spear drill!
    v=0 is razor-pointed incandescent meteor spearhead, v=0.35..0.55 is wide Mach plasma skirt,
    v=0.55..0.99 shreds into blazing asymmetric comet flames & ejected fiery sparks!
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ox = f * w
        phase = t * math.tau
        for y in range(h):
            v = (y + 0.5) / h
            for x in range(w):
                u = (x + 0.5) / w * 2.0 - 1.0
                ax = abs(u)
                if v < 0.02 or v > 0.99:
                    continue
                n = fbm(u * 5.5, v * 11.0 - t * 9.5, seed=510 + f * 7, octaves=3)
                n2 = fbm(u * 9.0 + 3.1, v * 14.0 - t * 13.0, seed=555 + f * 5, octaves=2)

                # Asymmetric razor-pointed spearhead cone -> flared Mach shroud -> tattered comet flame tongues!
                if v < 0.36:
                    cone_w = ((v - 0.02) / 0.34) ** 0.86 * 0.68
                else:
                    tail_u = (v - 0.36) / 0.64
                    # Shred into flickering flame tongues trailing backward
                    tongue = 0.65 + 0.55 * n + 0.25 * math.sin(u * 9.0 + phase)
                    cone_w = lerp(0.68, 0.14, tail_u ** 0.85) * tongue
                cone_w = max(0.015, cone_w * (0.90 + 0.18 * n))

                tail_dissolve = 1.0 - smoothstep(0.54, 0.98, v + (n - 0.5) * 0.32)
                body_out = (1.0 - smoothstep(cone_w * 0.64, cone_w + 0.10, ax)) * tail_dissolve
                body_fill = (1.0 - smoothstep(cone_w * 0.35, cone_w * 0.82, ax)) * tail_dissolve
                body_core = (1.0 - smoothstep(0.02, max(0.04, cone_w * 0.42), ax)) * (1.0 - smoothstep(0.45, 0.88, v))

                # 3 sharp swept-back spiral drill Mach rings twisting down the spear cone
                helix = math.sin(v * 20.0 - phase * 2.0 + u * 3.8 + ax * 4.5)
                spiral_band = smoothstep(0.55, 0.94, helix) * (1.0 - smoothstep(cone_w * 0.82, cone_w + 0.16, ax)) * smoothstep(0.07, 0.18, v) * smoothstep(0.76, 0.46, v)

                # Blazing sharp comet spear-tip star at v=0.06
                nose = 1.0 - smoothstep(0.0, 0.20, math.hypot(ax * 2.0, (v - 0.08) * 1.15))

                # Detached fiery comet embers shooting off the tail
                embers = smoothstep(0.76, 0.91, n2) * smoothstep(0.32, 0.56, v) * (1.0 - smoothstep(0.92, 0.99, v)) * (1.0 - smoothstep(0.60, 0.94, ax))

                edge_fade = (1.0 - smoothstep(0.84, 0.98, ax)) * smoothstep(0.015, 0.04, v) * smoothstep(0.99, 0.92, v)
                core_a = max(body_out * 0.96, spiral_band * 0.92, nose * 0.98, embers * 0.90) * edge_fade

                heat = 0.08
                if body_out > 0.05:
                    heat = lerp(0.06, 0.46 + 0.44 * body_core + 0.14 * (1.0 - v), clamp(body_fill / max(1e-4, body_out)))
                if spiral_band > 0.05:
                    heat = max(heat, 0.74 + 0.24 * spiral_band)
                if nose > 0.05:
                    heat = max(heat, 0.88 + 0.12 * nose)
                if embers > 0.05:
                    heat = max(heat, 0.78 + 0.18 * embers)

                glow_a = (body_fill * 0.36 + spiral_band * 0.36 + nose * 0.65 + embers * 0.35) * edge_fade
                px[ox + x, y] = pack_ramp(METEOR_STRIKE_RAMP, heat, core_a, glow_heat=0.62, glow_a=glow_a)

    return img


# --- 6. chaser_nova.png (512x64, 8 frames of 64x64) --------------------------

def make_chaser_nova() -> Image.Image:
    """
    Tintable high-contrast DNF Chaser Explosion / Fusion / Press arcane star-nova (8 frames).
    Corners strictly alpha=0; bold dark shock rim + razor 8-point star burst + sparking fragments.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        eu = ease_out_cubic(t)
        ox = f * size
        env = smoothstep(0.0, 0.05, t + 0.06) * (1.0 - 0.88 * smoothstep(0.55, 1.0, t))
        shock_r = 0.16 + 0.74 * eu
        shock_w = lerp(0.18, 0.05, t)

        for y in range(size):
            ny = (y + 0.5) / size * 2.0 - 1.0
            for x in range(size):
                nx = (x + 0.5) / size * 2.0 - 1.0
                r = math.hypot(nx, ny)
                if r >= 0.98:
                    continue
                ang = math.atan2(ny, nx)
                cm = corner_mask(nx, ny, 0.82, 0.97)
                n = fbm(nx * 5.2 + t * 2.8, ny * 5.2 - t * 2.4, seed=610 + f * 17, octaves=3)

                # Outer arcane shock shell
                sd = abs(r - shock_r + (n - 0.5) * 0.08)
                ring_out = (1.0 - smoothstep(shock_w * 0.6, shock_w * 1.45, sd)) * env
                ring_core = (1.0 - smoothstep(shock_w * 0.15, shock_w * 0.70, sd)) * env

                # Sharp 8-point radial arcane star spikes
                ray = (0.5 + 0.5 * math.cos(ang * 8.0 + t * 0.8)) ** 2.6
                spike_r = (0.28 + 0.56 * eu) * (0.42 + 0.58 * ray)
                spikes = (1.0 - smoothstep(spike_r * 0.55, spike_r + 0.06, r)) * (1.0 - 0.75 * smoothstep(0.25, 0.90, t))

                # Inner implosion/detonation core
                core_r = lerp(0.42, 0.14, t)
                core = (1.0 - smoothstep(0.0, core_r, r)) * (1.0 - smoothstep(0.32, 0.82, t))

                # Outer flying star shards on later frames
                shard_n = fbm(math.cos(ang) * 7.0 + r * 6.0 - t * 5.0, math.sin(ang) * 7.0, seed=680 + f, octaves=2)
                shards = smoothstep(0.72, 0.88, shard_n) * smoothstep(0.22, 0.52, r) * (1.0 - smoothstep(0.72, 0.94, r)) * smoothstep(0.18, 0.45, t) * (1.0 - smoothstep(0.70, 1.0, t))

                core_a = max(ring_out * 0.92, spikes * 0.90, core * 0.98, shards * 0.88) * cm
                heat = 0.10
                if ring_out > 0.05:
                    heat = lerp(0.10, 0.76 + 0.20 * n, ring_core / max(1e-4, ring_out))
                if spikes > 0.05:
                    heat = max(heat, 0.48 + 0.44 * spikes)
                if core > 0.05:
                    heat = max(heat, 0.82 + 0.18 * core)
                if shards > 0.05:
                    heat = max(heat, 0.78)

                glow_a = (ring_out * 0.34 + spikes * 0.36 + core * 0.55) * cm
                px[ox + x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.68, glow_a=glow_a)

    return img


# --- 7. martial_crater.png (128x128, 1 frame) --------------------------------

def make_martial_crater() -> Image.Image:
    """
    Shattered ground battle-impact crater decal (强压 / 强袭 / 煌龙 / 终极女皇 / 使徒穿刺).
    Dark obsidian multi-kink tectonic fractures + luminous energy magma veins + shattered ring rubble.
    Corners strictly alpha=0.
    """
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # 9 jagged 3-segment fracture chains + forked sub-cracks
    chains = []
    for i in range(9):
        a0 = i * (math.tau / 9.0) + (hash2(i, 3, 701) - 0.5) * 0.25
        a1 = a0 + (hash2(i, 11, 701) - 0.5) * 0.42
        a2 = a1 + (hash2(i, 19, 701) - 0.5) * 0.48
        r_end = 0.72 + 0.19 * hash2(i, 23, 701)
        p0 = (0.0, 0.0)
        p1 = (math.cos(a0) * 0.24, math.sin(a0) * 0.24)
        p2 = (math.cos(a1) * 0.52, math.sin(a1) * 0.52)
        p3 = (math.cos(a2) * r_end, math.sin(a2) * r_end)
        # Branch fork off p2
        af = a1 + (0.48 if i % 2 == 0 else -0.48)
        pf = (p2[0] + math.cos(af) * 0.26, p2[1] + math.sin(af) * 0.26)
        chains.append((p0, p1, p2, p3, pf))

    for y in range(size):
        ny = (y + 0.5) / size * 2.0 - 1.0
        for x in range(size):
            nx = (x + 0.5) / size * 2.0 - 1.0
            r = math.hypot(nx, ny)
            if r >= 0.97:
                continue
            cm = corner_mask(nx, ny, 0.80, 0.96)
            n = fbm(nx * 6.5, ny * 6.5, seed=710, octaves=3)
            n_rock = fbm(nx * 11.0, ny * 11.0, seed=733, octaves=2)

            # Dark crushed stone crater basin & jagged upturned rim slabs
            basin = (1.0 - smoothstep(0.16, 0.78, r + (n - 0.5) * 0.18)) * 0.84
            rim_d = abs(r - 0.52 + (n - 0.5) * 0.14)
            rim_slabs = (1.0 - smoothstep(0.02, 0.13, rim_d)) * smoothstep(0.32, 0.74, n_rock) * 0.88

            # Jagged 3-segment cracks with thick obsidian trench & glowing razor vein
            crack_trench = 0.0
            crack_vein = 0.0
            for p0, p1, p2, p3, pf in chains:
                for (ax, ay), (bx, by), w_scale in (
                    (p0, p1, 1.00), (p1, p2, 0.78), (p2, p3, 0.52), (p2, pf, 0.44)
                ):
                    d, st = seg_dist(nx, ny, ax, ay, bx, by)
                    wt = 0.068 * w_scale * (1.0 - 0.35 * st)
                    wv = 0.024 * w_scale * (1.0 - 0.40 * st)
                    crack_trench = max(crack_trench, 1.0 - smoothstep(wt * 0.55, wt, d))
                    crack_vein = max(crack_vein, 1.0 - smoothstep(wv * 0.35, wv, d))

            # Scorched blast center with glowing fracture ring (not a flat white dot!)
            pit_dark = 1.0 - smoothstep(0.08, 0.24, r + (n - 0.5) * 0.08)
            pit_ring = 1.0 - smoothstep(0.012, 0.055, abs(r - 0.16 + (n - 0.5) * 0.06))

            core_a = max(basin * 0.78, rim_slabs * 0.86, crack_trench * 0.96, pit_dark * 0.92) * cm
            # Dark scorched stone body, glowing veins & inner crack ring
            heat = 0.04 + 0.12 * n
            if rim_slabs > 0.15:
                heat = max(heat, 0.14 + 0.20 * n_rock)
            if crack_trench > 0.05:
                heat = max(heat, lerp(0.04, 0.88, crack_vein))
            if pit_ring > 0.10:
                heat = max(heat, 0.72 + 0.24 * pit_ring)

            glow_a = (crack_vein * 0.56 + pit_ring * 0.44) * cm
            px[x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.72, glow_a=glow_a)

    return img


# --- 8. chaser_sigil.png (128x128, 1 frame) ----------------------------------

def make_chaser_sigil() -> Image.Image:
    """
    Intricate DNF Battle Mage Arcane Magic Circle (炫纹聚能阵 / 蓄力阵 / 斗神战阵).
    Double rune ring + 5 outer Chaser nodal sockets + interlocking geometric star!
    Corners strictly alpha=0.
    """
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # Precompute pentagram & 6-point hexagram line segments
    hex_segs = []
    for offset in (0.0, math.pi / 3.0):
        pts = [(math.cos(offset + k * math.tau / 3.0) * 0.66, math.sin(offset + k * math.tau / 3.0) * 0.66) for k in range(3)]
        for k in range(3):
            hex_segs.append((pts[k], pts[(k + 1) % 3]))

    nodes = [(math.cos(k * math.tau / 5.0 - math.pi * 0.5) * 0.76, math.sin(k * math.tau / 5.0 - math.pi * 0.5) * 0.76) for k in range(5)]

    for y in range(size):
        ny = (y + 0.5) / size * 2.0 - 1.0
        for x in range(size):
            nx = (x + 0.5) / size * 2.0 - 1.0
            r = math.hypot(nx, ny)
            if r >= 0.96:
                continue
            ang = math.atan2(ny, nx)
            cm = corner_mask(nx, ny, 0.86, 0.96)

            # Outer double concentric rune rings
            r1 = 1.0 - smoothstep(0.014, 0.042, abs(r - 0.86))
            r2 = 1.0 - smoothstep(0.012, 0.036, abs(r - 0.67))
            r3 = 1.0 - smoothstep(0.012, 0.034, abs(r - 0.34))
            # Rune glyph ticks between r=0.69 and r=0.84
            glyph_band = smoothstep(0.69, 0.72, r) * (1.0 - smoothstep(0.81, 0.84, r))
            rune_tick = glyph_band * (1.0 - smoothstep(0.28, 0.65, abs(math.sin(ang * 15.0))))

            # Interlocking hexagram lines
            hex_line = 0.0
            for (ax, ay), (bx, by) in hex_segs:
                d, _ = seg_dist(nx, ny, ax, ay, bx, by)
                hex_line = max(hex_line, 1.0 - smoothstep(0.012, 0.036, d))

            # 5 Chaser nodal circles around perimeter
            node_hit = 0.0
            node_core = 0.0
            for cx, cy in nodes:
                nd = math.hypot(nx - cx, ny - cy)
                node_hit = max(node_hit, 1.0 - smoothstep(0.068, 0.105, nd))
                node_core = max(node_core, 1.0 - smoothstep(0.025, 0.062, nd))

            # Central sharp 5-point chaser star
            c_star = (1.0 - smoothstep(0.0, 0.035, r - (0.14 + 0.12 * (0.5 + 0.5 * math.cos(ang * 5.0 - math.pi * 0.5)))))

            line_out = max(r1, r2, r3, rune_tick * 0.88, hex_line * 0.90, node_hit, c_star)
            line_bright = max(
                (1.0 - smoothstep(0.006, 0.020, abs(r - 0.86))),
                (1.0 - smoothstep(0.006, 0.018, abs(r - 0.67))),
                (1.0 - smoothstep(0.006, 0.018, abs(r - 0.34))),
                rune_tick * 0.75,
                hex_line * 0.80,
                node_core,
                c_star * 0.95,
            )

            # Subtle dark translucent arcane disc backing
            disc_back = (1.0 - smoothstep(0.84, 0.90, r)) * 0.22
            core_a = max(line_out * 0.95, disc_back) * cm
            heat = lerp(0.08, 0.86, clamp(line_bright / max(1e-4, max(line_out, 0.25))))
            if node_core > 0.3 or c_star > 0.5:
                heat = max(heat, 0.95)
            glow_a = line_bright * 0.46 * cm
            px[x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.72, glow_a=glow_a)

    return img


# --- 9. dragon_halberd_aura.png (512x128, 8 frames of 64x128) ----------------

def make_dragon_halberd_aura() -> Image.Image:
    """
    Iconic DNF 煌龙偃月 (Dragon Spear) Golden Eastern Dragon Crescent Halberd!
    v=0 is spearhead & roaring golden dragon head, v=1 is blazing shaft tail.
    Combines:
      - Sleek imperial golden halberd shaft + diamond piercing spear tip
      - Wide sweeping Crescent Moon Halberd blade (偃月刀锋) on right side
      - Ornate dragon-fang counterweight barb on left side
      - Animated coiled Eastern Golden Dragon spirit spiraling up the shaft,
        terminating in a horned roaring dragon head & billowing dragon mane!
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ox = f * w
        phase = t * math.tau

        for y in range(h):
            v = (y + 0.5) / h
            for x in range(w):
                u = (x + 0.5) / w * 2.0 - 1.0
                if v < 0.015 or v > 0.985:
                    continue
                n = fbm(u * 6.0, v * 10.0 - t * 6.5, seed=910 + f * 13, octaves=3)

                # 1. Central imperial halberd shaft (v=0.14..0.95) + razor diamond lance tip (v=0.03..0.24)
                shaft_w = 0.085 if v > 0.24 else lerp(0.015, 0.14, clamp((v - 0.03) / 0.21))
                shaft_out = (1.0 - smoothstep(shaft_w * 0.65, shaft_w + 0.055, abs(u))) * smoothstep(0.02, 0.05, v) * smoothstep(0.97, 0.88, v)
                shaft_core = (1.0 - smoothstep(shaft_w * 0.12, shaft_w * 0.62, abs(u))) * smoothstep(0.03, 0.07, v) * smoothstep(0.95, 0.82, v)

                # 2. Wide sweeping Crescent Moon Halberd Blade (偃月大刀锋) on right side (u > 0, v in 0.12..0.58)
                crescent_out = 0.0
                crescent_edge = 0.0
                if 0.11 <= v <= 0.60 and u > -0.04:
                    cv = (v - 0.12) / 0.46  # 0 at top horn, 1 at bottom barb
                    # Outer convex crescent curve arching far right to u=0.84
                    outer_u = 0.12 + 0.74 * math.sin(clamp(cv) * math.pi) ** 0.72
                    # Inner concave crescent cutout near shaft
                    inner_u = 0.08 + 0.28 * math.sin(clamp(cv) * math.pi) ** 1.6
                    if u >= inner_u - 0.06 and u <= outer_u + 0.08:
                        in_b = smoothstep(inner_u - 0.04, inner_u + 0.04, u) * (1.0 - smoothstep(outer_u - 0.03, outer_u + 0.06, u))
                        crescent_out = in_b * smoothstep(0.11, 0.15, v) * smoothstep(0.60, 0.54, v)
                        crescent_edge = (1.0 - smoothstep(0.0, 0.075, abs(u - (outer_u - 0.03)))) * crescent_out

                # 3. Dragon-fang ear hook on left side (u < 0, v in 0.20..0.42)
                fang_out = 0.0
                if 0.19 <= v <= 0.44 and u < 0.04:
                    d_f1, _ = seg_dist(u, v, -0.06, 0.32, -0.56, 0.22)
                    d_f2, _ = seg_dist(u, v, -0.06, 0.36, -0.44, 0.40)
                    fang_out = max(
                        1.0 - smoothstep(0.032, 0.078, d_f1),
                        1.0 - smoothstep(0.028, 0.068, d_f2),
                    )

                # 4. Coiled Eastern Golden Dragon Spirit spiraling up the halberd!
                dragon_cx = math.sin(v * 11.2 - phase) * lerp(0.24, 0.46, smoothstep(0.22, 0.82, v))
                dragon_r = lerp(0.16, 0.075, clamp((v - 0.20) / 0.72))
                d_body_dist = abs(u - dragon_cx)
                dragon_body = (1.0 - smoothstep(dragon_r * 0.52, dragon_r + 0.075, d_body_dist)) * smoothstep(0.20, 0.28, v) * smoothstep(0.92, 0.78, v)
                dragon_fill = (1.0 - smoothstep(dragon_r * 0.18, dragon_r * 0.74, d_body_dist)) * dragon_body
                scales = 0.5 + 0.5 * math.sin(v * 46.0 - phase * 2.0 + (u - dragon_cx) * 16.0)
                dragon_spine = (1.0 - smoothstep(0.012, 0.048, d_body_dist)) * dragon_body

                # Sculpted Fierce Roaring Eastern Dragon Head lunging UP-LEFT along the halberd tip (v=0.05..0.28)!
                hx = -0.18 + math.sin(phase) * 0.03
                hy = 0.20
                # Tapered dragon neck smoothly joining coiled spine into cranium
                d_neck, neck_t = seg_dist(u, v, math.sin(0.28 * 11.2 - phase) * 0.25, 0.28, hx + 0.04, hy + 0.01)
                neck_w = lerp(0.11, 0.085, neck_t)
                neck_out = 1.0 - smoothstep(neck_w * 0.55, neck_w + 0.055, d_neck)
                neck_fill = 1.0 - smoothstep(neck_w * 0.15, neck_w * 0.72, d_neck)

                # Sleek tapered upper snout lunging diagonally up-left + slender lower roaring jaw
                d_upper, up_t = seg_dist(u, v, hx + 0.06, hy, hx - 0.42, hy - 0.095)
                up_w = lerp(0.088, 0.032, up_t)
                d_lower, lo_t = seg_dist(u, v, hx + 0.04, hy + 0.025, hx - 0.34, hy + 0.012)
                lo_w = lerp(0.055, 0.022, lo_t)
                # Swept flaming dragon mane & forked regal antlers trailing backward-right
                d_horn1, h1_t = seg_dist(u, v, hx + 0.04, hy - 0.02, hx + 0.34, hy - 0.145)
                d_horn2, h2_t = seg_dist(u, v, hx - 0.02, hy - 0.03, hx + 0.22, hy - 0.155)
                d_mane, mn_t = seg_dist(u, v, hx + 0.08, hy + 0.01, hx + 0.40, hy - 0.04 + 0.02 * math.sin(phase))
                d_whisker, _ = seg_dist(u, v, hx - 0.28, hy - 0.055, hx - 0.62, hy - 0.01 + 0.03 * math.sin(phase))

                head_out = max(
                    neck_out,
                    1.0 - smoothstep(up_w * 0.55, up_w + 0.048, d_upper),
                    1.0 - smoothstep(lo_w * 0.55, lo_w + 0.038, d_lower),
                )
                head_fill = max(
                    neck_fill,
                    1.0 - smoothstep(up_w * 0.18, up_w * 0.74, d_upper),
                    1.0 - smoothstep(lo_w * 0.18, lo_w * 0.72, d_lower),
                )
                horns_out = max(
                    1.0 - smoothstep(lerp(0.032, 0.012, h1_t), lerp(0.058, 0.030, h1_t), d_horn1),
                    1.0 - smoothstep(lerp(0.028, 0.010, h2_t), lerp(0.052, 0.026, h2_t), d_horn2),
                    (1.0 - smoothstep(lerp(0.042, 0.014, mn_t), lerp(0.072, 0.035, mn_t), d_mane)) * 0.88,
                    (1.0 - smoothstep(0.012, 0.034, d_whisker)) * 0.85,
                )
                # Fierce narrow glowing slitted dragon eye (angled aggressively)
                ex, ey = u - (hx - 0.08), v - (hy - 0.032)
                d_eye_slit = math.hypot(ex * 1.1 + ey * 0.5, (ey * 2.8 - ex * 0.5))
                eye_brow = 1.0 - smoothstep(0.038, 0.068, d_eye_slit)
                eye = 1.0 - smoothstep(0.015, 0.036, d_eye_slit)
                # Blazing golden-white roar fire erupting between open jaws
                d_roar, rt = seg_dist(u, v, hx - 0.18, hy - 0.018, hx - 0.50, hy - 0.055)
                roar = (1.0 - smoothstep(lerp(0.022, 0.042, rt), lerp(0.058, 0.095, rt), d_roar)) * (1.0 - 0.35 * rt)

                flame_mask = max(crescent_out, dragon_body, head_out)
                flame_aura = smoothstep(0.36, 0.80, n) * (1.0 - smoothstep(0.0, 0.24, abs(u - dragon_cx) - dragon_r)) * smoothstep(0.08, 0.24, v) * smoothstep(0.95, 0.76, v)

                edge_fade = (1.0 - smoothstep(0.86, 0.99, abs(u))) * smoothstep(0.015, 0.045, v) * smoothstep(0.985, 0.92, v)
                core_a = max(shaft_out * 0.97, crescent_out * 0.97, fang_out * 0.92, dragon_body * 0.96, head_out * 0.98, horns_out * 0.92, flame_aura * 0.50) * edge_fade

                heat = 0.06
                if flame_aura > 0.05:
                    heat = max(heat, 0.30 + 0.28 * n)
                if shaft_out > 0.05:
                    heat = max(heat, lerp(0.06, 0.74 + 0.24 * shaft_core, smoothstep(0.24, 0.76, shaft_out)))
                if fang_out > 0.05:
                    heat = max(heat, lerp(0.08, 0.70, smoothstep(0.26, 0.76, fang_out)))
                if crescent_out > 0.05:
                    heat = max(heat, lerp(0.06, 0.56 + 0.16 * n, smoothstep(0.22, 0.74, crescent_out)))
                    if crescent_edge > 0.25:
                        heat = max(heat, 0.88 + 0.12 * crescent_edge)
                if dragon_body > 0.05:
                    # Deep bronze-crimson dragon contour so coiled dragon separates cleanly over the blade!
                    heat = lerp(0.04, 0.50 + 0.24 * scales + 0.22 * dragon_spine, clamp(dragon_fill / max(1e-4, dragon_body)))
                if horns_out > 0.05:
                    heat = max(heat, lerp(0.08, 0.82, smoothstep(0.25, 0.75, horns_out)))
                if head_out > 0.05:
                    heat = lerp(0.04, 0.62 + 0.24 * head_fill, clamp(head_fill / max(1e-4, head_out)))
                    if eye_brow > 0.3:
                        heat = min(heat, 0.12)
                if roar > 0.18:
                    heat = max(heat, 0.86 + 0.14 * roar)
                    core_a = max(core_a, roar * 0.90 * edge_fade)
                if eye > 0.22:
                    heat = 1.00

                glow_a = (crescent_edge * 0.66 + shaft_core * 0.45 + dragon_spine * 0.48 + eye * 0.85 + roar * 0.60 + flame_mask * 0.24) * edge_fade
                px[ox + x, y] = pack_ramp(DRAGON_HALBERD_RAMP, heat, core_a, glow_heat=0.66, glow_a=glow_a)

    return img


# --- 10. dragon_pillar.png (512x128, 8 frames of 64x128) ---------------------

def make_dragon_pillar() -> Image.Image:
    """
    Towering vertical energy eruption pillar (煌龙终爆 / 炫纹强压冲天核柱 / 贝亚娜降临圣柱).
    v=0 skyward top, v=1 ground-anchored base.
    Tintable high-contrast dark vortex edge + ascending double-helix ribbons + white-hot core.
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        ox = f * w
        phase = t * math.tau
        rise_h = smoothstep(0.0, 0.30, t + 0.12)
        env = smoothstep(0.0, 0.06, t + 0.08) * (1.0 - 0.82 * smoothstep(0.58, 1.0, t))

        for y in range(h):
            v = (y + 0.5) / h
            top_cut = lerp(0.58, 0.03, rise_h)
            if v < top_cut - 0.05:
                continue
            top_fade = smoothstep(top_cut - 0.04, top_cut + 0.12, v)
            bot_flare = 1.0 + 0.42 * smoothstep(0.74, 1.0, v)
            for x in range(w):
                u = (x + 0.5) / w * 2.0 - 1.0
                ax = abs(u)
                n = fbm(u * 4.8, v * 8.0 + t * 8.5, seed=1010 + f * 9, octaves=3)

                col_w = 0.42 * bot_flare * (0.82 + 0.34 * n)
                col_out = (1.0 - smoothstep(col_w * 0.65, col_w + 0.14, ax)) * top_fade
                col_core = (1.0 - smoothstep(col_w * 0.12, col_w * 0.48, ax)) * top_fade

                # Twin ascending spiral energy ribbons twisting around the column
                h1 = abs(u - math.sin(v * 13.0 + phase * 2.0) * 0.54)
                h2 = abs(u - math.sin(v * 13.0 + phase * 2.0 + math.pi) * 0.54)
                helix = max(
                    1.0 - smoothstep(0.04, 0.11, h1),
                    1.0 - smoothstep(0.04, 0.11, h2),
                ) * top_fade * smoothstep(0.99, 0.86, v)

                # Rising fiery motes
                motes = smoothstep(0.75, 0.90, fbm(u * 10.0, v * 14.0 + t * 14.0, seed=1077, octaves=2))
                motes *= top_fade * (1.0 - smoothstep(0.50, 0.88, ax))

                edge_fade = (1.0 - smoothstep(0.84, 0.98, ax)) * smoothstep(0.01, 0.05, v) * smoothstep(0.995, 0.96, v)
                core_a = max(col_out * 0.92, helix * 0.90, motes * 0.84) * env * edge_fade
                heat = 0.10
                if col_out > 0.05:
                    heat = lerp(0.10, 0.56 + 0.40 * col_core, smoothstep(0.18, 0.72, col_out))
                if helix > 0.05:
                    heat = max(heat, 0.72 + 0.24 * helix)
                if motes > 0.05:
                    heat = max(heat, 0.84)

                glow_a = (col_out * 0.38 + helix * 0.42 + col_core * 0.46) * env * edge_fade
                px[ox + x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.68, glow_a=glow_a)

    return img


# --- 11. teana_wing.png (256x128, 4 frames of 64x128) ------------------------

def make_teana_wing() -> Image.Image:
    """
    Iconic DNF 贝亚娜斗神 (Teana Battle Goddess) Sculpted Seraphic Golden Angel Wing!
    Convention: wing root at u=0 (left), outstretched feather tips at u=1 (right),
    sweeping angelic crest at v=0 (top), cascading graceful lower plumes at v=1 (bottom).
    Overlapping plush covert plumage arching smoothly into sleek separated flight tips!
    """
    frames, w, h = 4, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    # 8 overlapping layered seraphic feathers arching gracefully from shoulder (u=0.06, v=0.38)
    base_feathers = (
        ((0.05, 0.36), (0.36, 0.09), (0.95, 0.07), 0.086),
        ((0.06, 0.39), (0.42, 0.16), (0.93, 0.20), 0.082),
        ((0.06, 0.43), (0.42, 0.25), (0.87, 0.34), 0.078),
        ((0.06, 0.47), (0.38, 0.35), (0.79, 0.48), 0.074),
        ((0.07, 0.51), (0.34, 0.46), (0.69, 0.61), 0.070),
        ((0.07, 0.55), (0.29, 0.57), (0.58, 0.73), 0.065),
        ((0.08, 0.59), (0.24, 0.68), (0.46, 0.84), 0.058),
        ((0.08, 0.63), (0.19, 0.78), (0.34, 0.93), 0.050),
    )

    for f in range(frames):
        ox = f * w
        phase = f * (math.tau / frames)
        feathers = []
        for idx, (p0, p1, p2, thick) in enumerate(base_feathers):
            flutter = math.sin(phase - idx * 0.45) * (0.018 + idx * 0.003)
            q1 = (p1[0], p1[1] + flutter * 0.5)
            q2 = (p2[0], clamp(p2[1] + flutter, 0.04, 0.95))
            feathers.append((p0, q1, q2, thick, idx))

        for y in range(h):
            v = (y + 0.5) / h
            for x in range(w):
                u = (x + 0.5) / w
                if u < 0.015 or u > 0.985 or v < 0.015 or v > 0.985:
                    continue
                n = fbm(u * 8.0, v * 12.0 - f * 0.8, seed=1110 + f * 5, octaves=2)

                # Iterate bottom-to-top so upper overlapping feathers cast clean depth shadows on lower ones!
                win_a = 0.0
                win_heat = 0.08
                win_glow = 0.0
                for p0, p1, p2, thick, idx in reversed(feathers):
                    d_min = 9.0
                    s_min = 0.0
                    ax, ay = p0
                    for step in range(1, 7):
                        st = step / 6.0
                        inv = 1.0 - st
                        bx = inv * inv * p0[0] + 2 * inv * st * p1[0] + st * st * p2[0]
                        by = inv * inv * p0[1] + 2 * inv * st * p1[1] + st * st * p2[1]
                        d, seg_t = seg_dist(u, v, ax, ay, bx, by)
                        if d < d_min:
                            d_min = d
                            s_min = (step - 1 + seg_t) / 6.0
                        ax, ay = bx, by

                    profile = math.sin(clamp(s_min) * math.pi) ** 0.52
                    fw = thick * (0.48 + 0.92 * profile) * (1.0 - 0.65 * smoothstep(0.68, 1.0, s_min))
                    f_out = 1.0 - smoothstep(fw * 0.68, fw + 0.020, d_min)
                    if f_out > 0.04:
                        f_fill = 1.0 - smoothstep(fw * 0.34, fw * 0.78, d_min)
                        f_quill = 1.0 - smoothstep(0.004, 0.017, d_min)
                        tip_glow = smoothstep(0.50, 1.0, s_min)
                        # Upper feathers are brighter ivory-gold; lower layers warmer imperial gold
                        layer_lift = (7 - idx) / 7.0 * 0.14
                        fh = lerp(0.06, 0.50 + layer_lift + 0.24 * tip_glow + 0.08 * n, f_fill)
                        if f_quill > 0.22:
                            fh = max(fh, 0.86 + 0.14 * f_quill)
                        fg = f_out * 0.28 + f_quill * 0.48 + tip_glow * f_out * 0.42
                        # Painter's over-composite of feather layers
                        win_heat = lerp(win_heat, fh, f_out)
                        win_a = max(win_a, f_out * 0.97)
                        win_glow = max(win_glow, fg)

                # Sculpted arched golden wing-mantle crest along the upper shoulder edge
                d_arch, arch_t = seg_dist(u, v, 0.07, 0.40, 0.44, 0.13)
                mantle_w = lerp(0.14, 0.06, arch_t)
                mantle_out = 1.0 - smoothstep(mantle_w * 0.65, mantle_w + 0.025, d_arch)
                mantle_fill = 1.0 - smoothstep(mantle_w * 0.25, mantle_w * 0.72, d_arch)
                if mantle_out > 0.08:
                    mh = lerp(0.10, 0.78 + 0.18 * (1.0 - arch_t), mantle_fill)
                    win_heat = lerp(win_heat, mh, mantle_out * 0.85)
                    win_a = max(win_a, mantle_out * 0.96)
                    win_glow = max(win_glow, mantle_fill * 0.45)

                # Subtle radiant dust motes drifting off wing tips
                mote_n = fbm(u * 13.0 - math.cos(phase) * 0.4, v * 15.0 + math.sin(phase) * 0.4, seed=1188, octaves=2)
                motes = smoothstep(0.82, 0.94, mote_n) * smoothstep(0.32, 0.80, u) * (1.0 - smoothstep(0.88, 0.98, u))
                if motes > 0.05:
                    win_a = max(win_a, motes * 0.82)
                    win_heat = max(win_heat, 0.94)
                    win_glow = max(win_glow, motes * 0.50)

                edge_fade = smoothstep(0.01, 0.045, u) * smoothstep(0.99, 0.95, u) * smoothstep(0.01, 0.045, v) * smoothstep(0.99, 0.95, v)
                px[ox + x, y] = pack_ramp(TEANA_GOLD_RAMP, win_heat, win_a * edge_fade, glow_heat=0.72, glow_a=win_glow * edge_fade)

    return img


# --- 12. teana_halo_crest.png (128x128, 1 frame) -----------------------------

def make_teana_halo_crest() -> Image.Image:
    """
    Iconic DNF 贝亚娜斗神 Sacred Winged Sun-Crest Halo & Ground Battle Seal!
    12-ray golden sunburst + double angelic wing-arcs + inner divine star. Corners alpha=0.
    """
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        ny = (y + 0.5) / size * 2.0 - 1.0
        for x in range(size):
            nx = (x + 0.5) / size * 2.0 - 1.0
            r = math.hypot(nx, ny)
            if r >= 0.96:
                continue
            ang = math.atan2(ny, nx)
            cm = corner_mask(nx, ny, 0.84, 0.96)

            # Outer sacred halo ring + inner divine ring
            ring_out = max(
                1.0 - smoothstep(0.016, 0.048, abs(r - 0.80)),
                1.0 - smoothstep(0.014, 0.040, abs(r - 0.56)),
                1.0 - smoothstep(0.012, 0.032, abs(r - 0.28)),
            )
            ring_core = max(
                1.0 - smoothstep(0.006, 0.022, abs(r - 0.80)),
                1.0 - smoothstep(0.006, 0.020, abs(r - 0.56)),
                1.0 - smoothstep(0.005, 0.016, abs(r - 0.28)),
            )

            # 12 alternating major/minor sunburst rays projecting between r=0.28..0.90
            ray_major = (0.5 + 0.5 * math.cos(ang * 4.0)) ** 6.0
            ray_minor = (0.5 + 0.5 * math.cos(ang * 12.0)) ** 4.0
            ray_reach = 0.42 + 0.48 * max(ray_major, ray_minor * 0.72)
            rays = (1.0 - smoothstep(ray_reach - 0.06, ray_reach + 0.03, r)) * smoothstep(0.22, 0.34, r) * max(ray_major, ray_minor * 0.82)

            # Stylized twin golden wing-arches embracing left and right edges
            ax = abs(nx)
            wing_arc = (1.0 - smoothstep(0.025, 0.070, abs(r - 0.70 - 0.10 * math.cos(ny * 2.4)))) * smoothstep(0.32, 0.56, ax)
            feather_teeth = 0.55 + 0.45 * math.sin(ny * 18.0)
            wing_arc *= feather_teeth

            # Sacred 8-point central solar diamond
            star_r = 0.16 + 0.14 * ((0.5 + 0.5 * math.cos(ang * 4.0)) ** 2.2)
            c_star = 1.0 - smoothstep(0.0, 0.04, r - star_r)

            shape_out = max(ring_out, rays * 0.94, wing_arc * 0.90, c_star * 0.96)
            shape_bright = max(ring_core, rays * 0.92, wing_arc * 0.78, c_star * 0.96)
            disc_tint = (1.0 - smoothstep(0.76, 0.86, r)) * 0.16

            core_a = max(shape_out * 0.95, disc_tint) * cm
            heat = lerp(0.08, 0.88, clamp(shape_bright / max(1e-4, max(shape_out, 0.22))))
            if c_star > 0.5 or ray_major > 0.75 and r < 0.86:
                heat = max(heat, 0.95)
            glow_a = (shape_bright * 0.52 + disc_tint * 0.35) * cm
            px[x, y] = pack_ramp(TEANA_GOLD_RAMP, heat, core_a, glow_heat=0.72, glow_a=glow_a)

    return img


# --- 13. teana_feathers_mist.png (256x64, 4 frames of 64x64) -----------------

def make_teana_feathers_mist() -> Image.Image:
    """
    Looping 4-frame curved drifting sacred feathers & swirling martial battle-qi clouds.
    Tintable (radiant gold for Teana feather-fall, fiery sakura-amber for Battle Groove 4-6).
    """
    frames, size = 4, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    feather_specs = (
        (-0.32, -0.20, 0.55, 0.30),
        (0.30, 0.16, -0.68, 0.28),
        (-0.04, 0.34, 1.05, 0.25),
    )

    for f in range(frames):
        ox = f * size
        phase = f * (math.tau / frames)
        for y in range(size):
            ny = (y + 0.5) / size * 2.0 - 1.0
            for x in range(size):
                nx = (x + 0.5) / size * 2.0 - 1.0
                r = math.hypot(nx, ny)
                if r >= 0.96:
                    continue
                cm = corner_mask(nx, ny, 0.74, 0.95)
                ang = math.atan2(ny, nx) + r * 1.4
                sx = math.cos(ang + phase * 0.25) * r * 3.2
                sy = math.sin(ang + phase * 0.25) * r * 3.2
                cloud_n = fbm(sx + math.cos(phase) * 0.5, sy + math.sin(phase) * 0.5, seed=1310, octaves=3)
                cloud = smoothstep(0.36, 0.80, cloud_n) * (1.0 - smoothstep(0.24, 0.88, r)) * 0.62

                # 3 gracefully arched feathers with visible curved quill & dark contour
                f_out = 0.0
                f_fill = 0.0
                f_quill = 0.0
                for fx0, fy0, rot0, flen in feather_specs:
                    fx = fx0 + 0.07 * math.sin(phase + rot0)
                    fy = fy0 + 0.07 * math.cos(phase + rot0)
                    dx, dy = nx - fx, ny - fy
                    if dx * dx + dy * dy > (flen * 1.6) ** 2:
                        continue
                    rot = rot0 + 0.30 * math.sin(phase)
                    cr, sr = math.cos(rot), math.sin(rot)
                    lx = dx * cr + dy * sr
                    ly = -dx * sr + dy * cr
                    # Curved feather spine: bends gracefully in y as x^2
                    tx = lx / flen
                    if tx < -1.15 or tx > 1.15:
                        continue
                    curve_y = ly - 0.14 * flen * (tx * tx - 0.3)
                    # Tapered plume profile: stem at tx=-1, plush vane at tx=0.1, sharp tip at tx=1
                    if tx < -0.55:
                        half_w = 0.04 * flen
                    else:
                        u_v = clamp((tx + 0.55) / 1.55)
                        half_w = flen * 0.26 * (math.sin(u_v * math.pi) ** 0.65)
                        # Classic tiny feather barb notch on one edge
                        if abs(u_v - 0.62) < 0.08 and curve_y > 0:
                            half_w *= 0.45
                    fo = (1.0 - smoothstep(half_w * 0.65, half_w + 0.032, abs(curve_y))) * smoothstep(-1.05, -0.92, tx) * (1.0 - smoothstep(0.92, 1.05, tx))
                    ff = (1.0 - smoothstep(half_w * 0.22, half_w * 0.78, abs(curve_y))) * smoothstep(-0.98, -0.82, tx) * (1.0 - smoothstep(0.84, 1.0, tx))
                    fq = (1.0 - smoothstep(0.008, 0.026, abs(curve_y))) * fo
                    if fo > f_out:
                        f_out = fo
                        f_fill = ff
                        f_quill = fq

                core_a = max(cloud, f_out * 0.96) * cm
                heat = 0.14 + 0.42 * cloud
                if f_out > 0.05:
                    heat = lerp(0.06, 0.76 + 0.24 * f_quill, clamp(f_fill / max(1e-4, f_out)))
                glow_a = (cloud * 0.42 + f_fill * 0.48) * cm
                px[ox + x, y] = pack_ramp(TINTABLE_RAMP, heat, core_a, glow_heat=0.72, glow_a=glow_a)

    return img


# --- 14. apostle_spear_lance.png (256x128, 4 frames of 64x128) ---------------

def make_apostle_spear_lance() -> Image.Image:
    """
    Iconic DNF 太古贝亚娜神枪「使徒穿刺」Divine Lance Aura (4 looping frames).
    v=0 is blinding divine spear tip, v=1 is winged solar crossguard & energy grip.
    """
    frames, w, h = 4, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        ox = f * w
        phase = f * (math.tau / frames)
        for y in range(h):
            v = (y + 0.5) / h
            for x in range(w):
                u = (x + 0.5) / w * 2.0 - 1.0
                ax = abs(u)
                if v < 0.015 or v > 0.985:
                    continue
                n = fbm(u * 5.5, v * 10.0 - f * 1.4, seed=1410 + f * 7, octaves=3)

                # Long fluted diamond divine lance blade (v=0.02..0.72) + slender shaft (v=0.72..0.96)
                if v < 0.72:
                    bt = (v - 0.02) / 0.70
                    # Broadest at 0.62, razor spear point at 0.02
                    blade_w = 0.28 * math.sin(clamp(bt) * math.pi * 0.85) ** 0.78
                    blade_w = max(0.02, blade_w)
                else:
                    blade_w = 0.075
                blade_out = (1.0 - smoothstep(blade_w * 0.68, blade_w + 0.055, ax)) * smoothstep(0.015, 0.04, v) * smoothstep(0.98, 0.92, v)
                blade_ridge = (1.0 - smoothstep(0.01, max(0.03, blade_w * 0.42), ax)) * smoothstep(0.02, 0.06, v) * smoothstep(0.94, 0.78, v)

                # Twin angelic wing crossguard sweeping upward at v=0.68..0.82
                d_w1, _ = seg_dist(ax, v, 0.06, 0.74, 0.78, 0.58)
                d_w2, _ = seg_dist(ax, v, 0.06, 0.78, 0.62, 0.68)
                guard_out = max(
                    1.0 - smoothstep(0.032, 0.072, d_w1),
                    1.0 - smoothstep(0.024, 0.058, d_w2),
                )
                # Central solar gem at guard junction
                gem = 1.0 - smoothstep(0.05, 0.11, math.hypot(u * 1.1, (v - 0.74) * 1.7))

                # Twin divine golden ribbons spiraling up from guard to spear tip
                r1 = abs(u - math.sin(v * 14.0 - phase) * lerp(0.14, 0.44, v))
                r2 = abs(u - math.sin(v * 14.0 - phase + math.pi) * lerp(0.14, 0.44, v))
                ribbon = max(1.0 - smoothstep(0.024, 0.065, r1), 1.0 - smoothstep(0.024, 0.065, r2))
                ribbon *= smoothstep(0.04, 0.14, v) * smoothstep(0.92, 0.74, v)

                # Tip divine star flare
                tip_star = 1.0 - smoothstep(0.0, 0.22, math.hypot(ax * 1.5, (v - 0.07) * 1.1))

                edge_fade = (1.0 - smoothstep(0.86, 0.99, ax)) * smoothstep(0.015, 0.04, v) * smoothstep(0.985, 0.93, v)
                core_a = max(blade_out * 0.97, guard_out * 0.95, gem * 0.98, ribbon * 0.85, tip_star * 0.92) * edge_fade

                heat = 0.06
                if blade_out > 0.05:
                    blade_fill = (1.0 - smoothstep(blade_w * 0.32, blade_w * 0.82, ax)) * smoothstep(0.02, 0.05, v) * smoothstep(0.96, 0.88, v)
                    heat = lerp(0.05, 0.56 + 0.42 * blade_ridge + 0.08 * n, clamp(blade_fill / max(1e-4, blade_out)))
                if guard_out > 0.05:
                    guard_fill = max(
                        1.0 - smoothstep(0.012, 0.044, d_w1),
                        1.0 - smoothstep(0.008, 0.034, d_w2),
                    )
                    heat = max(heat, lerp(0.06, 0.78, clamp(guard_fill / max(1e-4, guard_out))))
                if ribbon > 0.05:
                    heat = max(heat, 0.74 + 0.22 * ribbon)
                if gem > 0.2 or tip_star > 0.25:
                    heat = max(heat, 0.94 + 0.06 * max(gem, tip_star))

                glow_a = (blade_ridge * 0.56 + guard_out * 0.38 + ribbon * 0.44 + tip_star * 0.65 + gem * 0.55) * edge_fade
                px[ox + x, y] = pack_ramp(TEANA_GOLD_RAMP, heat, core_a, glow_heat=0.72, glow_a=glow_a)

    return img


# --- 15 & 16. 32x32 Custom Held Effect Weapons (teana_spear & dragon_halberd) ---

def make_teana_spear_item() -> Image.Image:
    """
    32x32 pixel-art DNF 贝亚娜黄金神矛 (effect/teana_spear).
    Oriented diagonally from bottom-left pommel (2,29) to top-right lance tip (30,2)
    for minecraft:item/handheld!
    """
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    ax, ay = 2.5, 29.5
    bx, by = 29.5, 2.5
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length       # along shaft toward tip
    nx, ny = -uy, ux                        # perpendicular

    for y in range(size):
        for x in range(size):
            rx, ry = (x + 0.5) - ax, (y + 0.5) - ay
            along = rx * ux + ry * uy
            perp = rx * nx + ry * ny
            t = along / length
            ap = abs(perp)
            if t < -0.04 or t > 1.04:
                continue

            hit = False
            heat = 0.0
            # Pommel finial (t in 0.00..0.10)
            if 0.0 <= t <= 0.10 and ap <= 1.65:
                hit = True
                heat = 0.78 if ap < 0.75 else 0.24
            # Slender imperial gold shaft (t in 0.08..0.60)
            elif 0.08 <= t <= 0.60 and ap <= 1.10:
                hit = True
                heat = 0.68 if ap < 0.48 else 0.16
                if int(t * 24) % 3 == 0 and ap < 0.7:
                    heat = 0.88
            # Twin Angelic Wing Crossguard (t in 0.52..0.72)
            wing_t = (t - 0.56) * 4.5
            if 0.50 <= t <= 0.74:
                # Swept forward wing arms curving toward the tip
                d_w1, _ = seg_dist(along, ap, length * 0.58, 0.8, length * 0.72, 6.2)
                d_w2, _ = seg_dist(along, ap, length * 0.54, 0.8, length * 0.62, 4.4)
                if d_w1 <= 1.35 or d_w2 <= 1.15:
                    hit = True
                    heat = 0.90 if min(d_w1, d_w2) < 0.65 else 0.22
            # Central radiant solar gem at guard
            if math.hypot(along - length * 0.59, perp) <= 2.0:
                hit = True
                heat = 1.00 if math.hypot(along - length * 0.59, perp) <= 1.05 else 0.45
            # Fluted Diamond Divine Spearhead (t in 0.60..1.00)
            if 0.60 <= t <= 1.00:
                bt = (t - 0.60) / 0.40
                bw = 3.2 * (math.sin(clamp(bt) * math.pi * 0.85) ** 0.72)
                if bt > 0.85:
                    bw = lerp(bw, 0.55, (bt - 0.85) / 0.15)
                if ap <= bw:
                    hit = True
                    if ap < bw * 0.34:
                        heat = 0.98
                    elif ap < bw * 0.72:
                        heat = 0.72
                    else:
                        heat = 0.14

            if hit:
                r, g, b = sample_ramp(TEANA_GOLD_RAMP, heat)
                px[x, y] = (r, g, b, 255)

    return img


def make_dragon_halberd_item() -> Image.Image:
    """
    32x32 pixel-art DNF 煌龙偃月光戟 (effect/dragon_halberd).
    Oriented diagonally from bottom-left pommel (2,29) to top-right dragon crescent blade (30,2)!
    """
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    ax, ay = 2.5, 29.5
    bx, by = 29.5, 2.5
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux   # +perp points down-right (crescent blade side), -perp points up-left (fang hook side)

    for y in range(size):
        for x in range(size):
            rx, ry = (x + 0.5) - ax, (y + 0.5) - ay
            along = rx * ux + ry * uy
            perp = rx * nx + ry * ny
            t = along / length
            ap = abs(perp)
            if t < -0.04 or t > 1.04:
                continue

            hit = False
            heat = 0.0

            # Ornate dragon pommel (t in 0.00..0.09)
            if 0.0 <= t <= 0.09 and ap <= 1.7:
                hit = True
                heat = 0.76 if ap < 0.85 else 0.18
            # Halberd pole shaft (t in 0.08..0.72)
            elif 0.08 <= t <= 0.72 and ap <= 1.15:
                hit = True
                heat = 0.62 if ap < 0.52 else 0.14
                if int(t * 20) % 2 == 0 and ap < 0.75:
                    heat = 0.80
            # Piercing dragon lance spearhead (t in 0.70..1.00)
            if 0.70 <= t <= 1.00:
                bt = (t - 0.70) / 0.30
                bw = 2.2 * (1.0 - bt * 0.82)
                if ap <= bw:
                    hit = True
                    heat = 0.98 if ap < bw * 0.38 else (0.72 if ap < bw * 0.74 else 0.14)

            # Sweeping Crescent Moon Halberd Blade (偃月刃) on +perp side (t in 0.52..0.90)
            if 0.50 <= t <= 0.90 and perp >= 0.4:
                ct = (t - 0.50) / 0.40
                outer_p = 1.6 + 6.4 * (math.sin(clamp(ct) * math.pi) ** 0.72)
                inner_p = 1.1 + 2.2 * (math.sin(clamp(ct) * math.pi) ** 1.5)
                if inner_p <= perp <= outer_p:
                    hit = True
                    u_b = (perp - inner_p) / max(0.5, outer_p - inner_p)
                    if u_b > 0.78:
                        heat = 0.96   # blazing white-gold razor edge
                    elif u_b > 0.25:
                        heat = 0.64   # imperial amber crescent body
                    else:
                        heat = 0.18   # dark crimson-bronze inner spine

            # Dragon-fang reverse barb on -perp side (t in 0.56..0.78)
            if 0.54 <= t <= 0.80 and perp <= -0.4:
                d_barb, _ = seg_dist(along, perp, length * 0.64, -0.8, length * 0.74, -4.6)
                if d_barb <= 1.25:
                    hit = True
                    heat = 0.86 if d_barb < 0.58 else 0.18

            # Dragon-collar glowing ruby/gold jewel at blade base
            if math.hypot(along - length * 0.65, perp) <= 2.1:
                hit = True
                heat = 1.00 if math.hypot(along - length * 0.65, perp) <= 1.05 else 0.42

            if hit:
                r, g, b = sample_ramp(DRAGON_HALBERD_RAMP, heat)
                px[x, y] = (r, g, b, 255)

    return img


def write_held_weapon_jsons(weapon_id: str, third_scale: float, first_scale: float) -> None:
    model_doc = {
        "parent": "minecraft:item/handheld",
        "textures": {
            "layer0": f"yumis_dungeon_and_fight:item/effect/{weapon_id}"
        },
        "display": {
            "thirdperson_righthand": {
                "rotation": [0, -90, 55],
                "translation": [0, 6.5, 1.5],
                "scale": [third_scale, third_scale, 1.1]
            },
            "thirdperson_lefthand": {
                "rotation": [0, 90, -55],
                "translation": [0, 6.5, 1.5],
                "scale": [third_scale, third_scale, 1.1]
            },
            "firstperson_righthand": {
                "rotation": [0, -90, 25],
                "translation": [1.13, 4.4, 1.13],
                "scale": [first_scale, first_scale, 0.9]
            },
            "firstperson_lefthand": {
                "rotation": [0, 90, -25],
                "translation": [1.13, 4.4, 1.13],
                "scale": [first_scale, first_scale, 0.9]
            }
        }
    }
    item_doc = {
        "model": {
            "type": "minecraft:model",
            "model": f"yumis_dungeon_and_fight:item/effect/{weapon_id}"
        }
    }
    OUT_ITEM_MODEL.mkdir(parents=True, exist_ok=True)
    OUT_ITEM_DEF.mkdir(parents=True, exist_ok=True)
    (OUT_ITEM_MODEL / f"{weapon_id}.json").write_text(
        json.dumps(model_doc, indent=2) + "\n", encoding="utf-8"
    )
    (OUT_ITEM_DEF / f"{weapon_id}.json").write_text(
        json.dumps(item_doc, indent=2) + "\n", encoding="utf-8"
    )


# --- Contact Sheet Export (Dark & Light Checkerboards) -----------------------

def make_checker(w: int, h: int, c1: tuple[int, int, int], c2: tuple[int, int, int], cell: int = 12) -> Image.Image:
    bg = Image.new("RGBA", (w, h), (*c1, 255))
    draw = ImageDraw.Draw(bg)
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if ((x // cell) + (y // cell)) % 2 == 1:
                draw.rectangle([x, y, x + cell - 1, y + cell - 1], fill=(*c2, 255))
    return bg


def build_contact_sheets(assets: list[tuple[str, Image.Image]]) -> None:
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pad = 16
    label_h = 20
    rows = []
    max_w = 0
    total_h = pad
    for name, img in assets:
        scale = 4 if img.width <= 64 else (2 if img.width <= 512 else 1)
        if img.width == 768:
            scale = 1
        sw, sh = img.width * scale, img.height * scale
        rows.append((name, img, sw, sh))
        max_w = max(max_w, sw + pad * 2)
        total_h += label_h + sh + pad

    for mode, c1, c2, text_col in (
        ("dark", (24, 26, 34), (36, 40, 52), (235, 238, 245, 255)),
        ("light", (212, 216, 224), (234, 238, 244), (22, 24, 32, 255)),
    ):
        sheet = make_checker(max_w, total_h, c1, c2)
        draw = ImageDraw.Draw(sheet)
        cy = pad
        for name, img, sw, sh in rows:
            draw.text((pad, cy + 2), f"{name} ({img.width}x{img.height})", fill=text_col)
            cy += label_h
            resized = img.resize((sw, sh), Image.Resampling.NEAREST)
            sheet.alpha_composite(resized, (pad, cy))
            draw.rectangle([pad - 1, cy - 1, pad + sw, cy + sh], outline=(120, 128, 144, 160))
            cy += sh + pad
        out_path = PREVIEW_DIR / f"contact_sheet_{mode}.png"
        sheet.save(out_path, format="PNG")
        print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT_VFX.mkdir(parents=True, exist_ok=True)
    OUT_ITEM_TEX.mkdir(parents=True, exist_ok=True)

    vfx_assets = [
        ("chaser_orb.png", make_chaser_orb()),
        ("palm_blossom_burst.png", make_palm_blossom_burst()),
        ("spear_thrust.png", make_spear_thrust()),
        ("martial_slash.png", make_martial_slash()),
        ("meteor_drill.png", make_meteor_drill()),
        ("chaser_nova.png", make_chaser_nova()),
        ("martial_crater.png", make_martial_crater()),
        ("chaser_sigil.png", make_chaser_sigil()),
        ("dragon_halberd_aura.png", make_dragon_halberd_aura()),
        ("dragon_pillar.png", make_dragon_pillar()),
        ("teana_wing.png", make_teana_wing()),
        ("teana_halo_crest.png", make_teana_halo_crest()),
        ("teana_feathers_mist.png", make_teana_feathers_mist()),
        ("apostle_spear_lance.png", make_apostle_spear_lance()),
    ]
    for name, img in vfx_assets:
        path = OUT_VFX / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    teana_spear = make_teana_spear_item()
    teana_spear.save(OUT_ITEM_TEX / "teana_spear.png", format="PNG")
    write_held_weapon_jsons("teana_spear", third_scale=2.15, first_scale=1.35)
    print(f"wrote {(OUT_ITEM_TEX / 'teana_spear.png').relative_to(ROOT)} (32x32) + model/item JSONs")

    dragon_halberd = make_dragon_halberd_item()
    dragon_halberd.save(OUT_ITEM_TEX / "dragon_halberd.png", format="PNG")
    write_held_weapon_jsons("dragon_halberd", third_scale=2.25, first_scale=1.38)
    print(f"wrote {(OUT_ITEM_TEX / 'dragon_halberd.png').relative_to(ROOT)} (32x32) + model/item JSONs")

    preview_list = vfx_assets + [
        ("item/effect/teana_spear.png", teana_spear),
        ("item/effect/dragon_halberd.png", dragon_halberd),
    ]
    build_contact_sheets(preview_list)


if __name__ == "__main__":
    main()

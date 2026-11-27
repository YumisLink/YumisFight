#!/usr/bin/env python3
"""
Dedicated FF14 Black Mage-inspired Sun & Moon Mage (日月魔法师) high-contrast
pixel-sharp + soft-feathered VFX texture generator.

Outputs pre-baked multi-tone RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/sunmoon/
and a labeled dark-backdrop contact sheet preview under:
  temp/vfx-preview/sunmoon_contact_sheet.png

Design direction (FF14 Black Mage High-Contrast Arcane Astral/Umbral/Paradox):
  - Sun / Astral Fire (日炎 Fire I / 烈日爆炎 Fire III / 日冕 Fire IV):
      Charred obsidian-crimson silhouette rim -> molten flame vermilion -> solar gold -> white-hot fusion core
  - Moon / Umbral Ice (月流 Blizzard I/III / 月辰回流 Umbral Soul / 月见星辉 Blizzard IV × Freeze):
      Midnight indigo-navy rim -> deep glacial sapphire -> luminous azure frost -> silver-white specular
  - Paradox Awakening (悖论 Paradox):
      Abyssal violet-black rim -> clashing glacial ice-cyan + scorching crimson-magenta -> blinding eclipse flash
  - Ley Lines Sigil (黑魔纹):
      Neutral high-contrast luminance ramp with deep shadow framing so Sun gold / Moon azure / Paradox tint cleanly.
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/sunmoon"
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


def regular_poly_edge_dist(x: float, y: float, sides: int, radius: float, rot: float = 0.0) -> tuple[float, float]:
    """Returns (unsigned distance to polygon border, signed inside distance >0 inside)."""
    ang = math.atan2(y, x) - rot
    sector = 2.0 * math.pi / sides
    local = ((ang + sector * 0.5) % sector) - sector * 0.5
    apothem = radius * math.cos(sector * 0.5)
    r = math.hypot(x, y)
    proj = r * math.cos(local)
    signed = apothem - proj
    return abs(signed), signed


# --- FF14 Black Mage Multi-Tone High-Contrast Ramps --------------------------

# Sun / Astral Fire (Fire I / Fire III): charred obsidian-crimson -> molten orange -> solar gold -> white-hot
SUN_FIRE_RAMP = (
    (0.00, (22, 6, 8)),      # charred obsidian ember shadow
    (0.18, (96, 16, 14)),    # deep burnt crimson rim
    (0.40, (196, 48, 16)),   # roaring astral vermilion
    (0.64, (248, 124, 28)),  # blazing molten fire orange
    (0.84, (255, 206, 92)),  # radiant solar gold
    (0.95, (255, 242, 186)), # blinding sun-flare rim
    (1.00, (255, 253, 244)), # pure fusion white-gold
)

# Corona / Fire IV: heavier scorched black-crimson outer shadow + superheated solar gold-white core
CORONA_RAMP = (
    (0.00, (18, 4, 10)),     # abyssal scorched eclipse rim
    (0.16, (88, 12, 18)),    # deep prominence crimson
    (0.38, (188, 38, 14)),   # intense Fire IV scarlet
    (0.60, (250, 118, 22)),  # blazing plasma orange
    (0.80, (255, 198, 72)),  # blinding golden corona
    (0.93, (255, 244, 194)), # white-gold stellar heart
    (1.00, (255, 255, 250)), # supernova flash
)

# Moon / Umbral Ice (Blizzard I/III/IV, Freeze, Umbral Soul): midnight navy -> glacial azure -> silver-white
MOON_ICE_RAMP = (
    (0.00, (8, 14, 38)),     # midnight arcane indigo shadow
    (0.18, (20, 48, 112)),   # deep glacial sapphire
    (0.42, (46, 124, 212)),  # crisp umbral azure
    (0.66, (108, 204, 255)), # luminous moonlit cyan-blue
    (0.85, (196, 240, 255)), # crystalline frost silver
    (0.95, (234, 250, 255)), # pure starlight gleam
    (1.00, (252, 255, 255)), # diamond specular white
)

# Paradox Crimson-Magenta Fire side (FF14 Paradox signature scarlet-rose)
PARADOX_CRIMSON_RAMP = (
    (0.00, (22, 4, 24)),     # abyssal violet-black rim
    (0.20, (98, 10, 56)),    # deep eclipse wine-crimson
    (0.44, (196, 26, 92)),   # FF14 Paradox vivid crimson-rose
    (0.68, (255, 76, 128)),  # blazing scarlet-magenta plasma
    (0.86, (255, 168, 198)), # incandescent rose-pink flare
    (1.00, (255, 246, 252)), # white-hot collision flash
)

# Paradox Violet-Void core (eclipse pinch & shockwave center)
PARADOX_VOID_RAMP = (
    (0.00, (12, 4, 26)),     # deep void obsidian
    (0.24, (58, 18, 114)),   # abyssal indigo-violet
    (0.52, (138, 66, 224)),  # radiant arcane violet
    (0.78, (202, 162, 255)), # pale prismatic lavender
    (1.00, (250, 248, 255)), # blinding white-violet core
)


def sample_ramp(ramp: tuple[tuple[float, tuple[int, int, int]], ...], t: float) -> tuple[float, float, float]:
    u = clamp(t)
    for i in range(len(ramp) - 1):
        t0, c0 = ramp[i]
        t1, c1 = ramp[i + 1]
        if u <= t1:
            f = (u - t0) / max(1e-6, t1 - t0)
            return (
                lerp(c0[0], c1[0], f),
                lerp(c0[1], c1[1], f),
                lerp(c0[2], c1[2], f),
            )
    c = ramp[-1][1]
    return (float(c[0]), float(c[1]), float(c[2]))


def pack_ramp_pixel(
    ramp: tuple[tuple[float, tuple[int, int, int]], ...],
    core_h: float, core_a: float,
    glow_h: float = 0.60, glow_a: float = 0.0,
) -> tuple[int, int, int, int]:
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (0, 0, 0, 0)
    cr, cg, cb = sample_ramp(ramp, core_h)
    gr, gg, gb = sample_ramp(ramp, glow_h)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    return (
        int(round(clamp(cr * wc + gr * wg, 0.0, 255.0))),
        int(round(clamp(cg * wc + gg * wg, 0.0, 255.0))),
        int(round(clamp(cb * wc + gb * wg, 0.0, 255.0))),
        int(round(clamp(out_a) * 255.0)),
    )


def pack_rgb_dual(
    cr: float, cg: float, cb: float, core_a: float,
    gr: float, gg: float, gb: float, glow_a: float,
) -> tuple[int, int, int, int]:
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (0, 0, 0, 0)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    return (
        int(round(clamp(cr * wc + gr * wg, 0.0, 255.0))),
        int(round(clamp(cg * wc + gg * wg, 0.0, 255.0))),
        int(round(clamp(cb * wc + gb * wg, 0.0, 255.0))),
        int(round(clamp(out_a) * 255.0)),
    )


def pack_neutral_sigil(lum: float, core_a: float, glow_lum: float = 0.65, glow_a: float = 0.0) -> tuple[int, int, int, int]:
    """High-contrast neutral ramp (dark obsidian 42 -> crisp white 255) for tintable Ley Lines."""
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (255, 255, 255, 0)
    cl = lerp(42.0, 255.0, clamp(lum) ** 0.85)
    gl = lerp(64.0, 255.0, clamp(glow_lum) ** 0.85)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    v = int(round(clamp(cl * wc + gl * wg, 0.0, 255.0)))
    return (v, v, v, int(round(clamp(out_a) * 255.0)))


# --- 1. ley_lines_sigil.png (256x256, 1 frame — FF14 Black Mage Ley Lines) ---

def make_ley_lines_sigil() -> Image.Image:
    """
    FF14 Black Mage Ley Lines (黑魔纹) ornate astrolabe magic circle:
    Double-bordered outer zodiac ring + 12 arcane ticks + interlaced twin hexagrams
    + inner celestial compass ring + 8-point cardinal star medallions.
    Neutral high-contrast luminance so Sun gold, Moon azure, and Paradox tint cleanly!
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
            rr = math.hypot(rx, ry)
            if rr > 1.0:
                continue
            ang = math.atan2(qy, qx)

            core_a = 0.0
            core_l = 0.10

            def add_line(dist: float, width: float, lum: float, strength: float = 1.0) -> None:
                nonlocal core_a, core_l
                if dist < width + 0.024:
                    # Crisp dark outer stroke for strong daytime readability!
                    if dist < width:
                        u = 1.0 - dist / max(1e-4, width)
                        a = smoothstep(0.0, 0.35, u) * strength
                        l = lerp(0.45, lum, u ** 0.7)
                        if a > core_a:
                            core_a = a
                            core_l = l
                        elif a > 0.3:
                            core_l = max(core_l, l)
                    else:
                        rim_u = 1.0 - (dist - width) / 0.024
                        ra = smoothstep(0.0, 0.5, rim_u) * 0.72 * strength
                        if ra > core_a:
                            core_a = ra
                            core_l = min(core_l, 0.14)

            # Outer double Ley-Lines rings
            add_line(abs(r - 0.91), 0.022, 0.96, 1.0)
            add_line(abs(r - 0.79), 0.016, 0.88, 0.95)
            add_line(abs(r - 0.54), 0.015, 0.86, 0.92)
            add_line(abs(r - 0.24), 0.013, 0.92, 0.90)

            # 12 astrological rune band ticks between r=0.79 and r=0.91
            if 0.785 <= r <= 0.915:
                sec12 = ((ang + math.pi) / (2.0 * math.pi)) * 12.0
                frac = abs((sec12 % 1.0) - 0.5)
                # Major divider spikes
                if frac < 0.08:
                    add_line(frac * 0.22, 0.016, 0.94, 0.92)
                # Calligraphic arcane glyph dashes inside each sector
                elif 0.14 < frac < 0.38:
                    g_pat = math.sin(sec12 * 18.0 + r * 35.0)
                    if g_pat > 0.25 and abs(r - 0.85) < 0.032:
                        add_line(abs(r - 0.85), 0.020, 0.82, 0.86)

            # Interlaced FF14 Ley Lines twin triangles (Star of Hexagram) spanning r=0.79
            for rot_tri in (0.0, math.pi / 3.0):
                dt, sgn = regular_poly_edge_dist(qx, qy, 3, 0.79, rot_tri)
                if r <= 0.80:
                    add_line(dt, 0.016, 0.94, 0.96)

            # Secondary rotated square frame inside r=0.79 (classic BLM astrolabe geometry)
            for rot_sq in (math.pi / 4.0,):
                ds, _ = regular_poly_edge_dist(qx, qy, 4, 0.77, rot_sq)
                if r <= 0.78:
                    add_line(ds, 0.012, 0.82, 0.82)

            # 6 cardinal arcane medallions / jewel nodes on the hexagram vertices
            for i in range(6):
                va = i * math.pi / 3.0
                vx, vy = math.cos(va) * 0.79, math.sin(va) * 0.79
                dv = math.hypot(qx - vx, qy - vy)
                add_line(abs(dv - 0.068), 0.014, 0.96, 0.95)
                if dv < 0.042:
                    add_line(dv, 0.042, 1.0, 0.98)

            # Central 8-point compass star
            if r < 0.30:
                sp_a = abs(math.cos(ang * 4.0))
                star_r = 0.065 + 0.195 * (sp_a ** 3.2)
                if r <= star_r:
                    su = 1.0 - r / max(0.01, star_r)
                    a = smoothstep(0.0, 0.30, su) * 0.95
                    if a > core_a:
                        core_a = a
                        core_l = 0.65 + 0.35 * su

            # Soft glowing radial aura on the rings (smooth unquantized coordinates)
            ring_glow = (
                math.exp(-((rr - 0.85) / 0.085) ** 2) * 0.34
                + math.exp(-((rr - 0.54) / 0.065) ** 2) * 0.24
                + math.exp(-((rr / 0.24) ** 2)) * 0.28
            ) * smoothstep(0.99, 0.88, rr)

            px[x, y] = pack_neutral_sigil(core_l, core_a, 0.76, ring_glow)

    return img


# --- 2. astral_fire_wisp.png (512x128, 4 frames — Orbiting Astral Fire) ------

def make_astral_fire_wisp() -> Image.Image:
    """
    FF14 Astral Fire (星极火 / 日曜心) orbiting elemental flame spirit:
    Sculpted rising flame teardrop with dark crimson edge, flickering solar licks,
    tiny orbiting embers, and blazing white-gold heart.
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

                # Shift flame body slightly downward so upper flickering tip fits cleanly
                fy = qy - 0.14
                sway = 0.14 * math.sin(phase + fy * 3.2) * clamp(-fy + 0.35, 0.0, 1.0)
                fx = qx - sway
                # Teardrop taper: round lower bulb, sharp dancing upper tongue
                taper = 1.0 if fy > 0.0 else clamp(1.0 + fy * 0.92, 0.12, 1.0)
                n = fbm(fx * 5.2, fy * 4.5 - f * 1.1, 101 + f * 17, 3)
                flame_d = math.hypot(fx / max(0.14, 0.48 * taper), (fy + 0.06) / 0.64) - 0.20 * (n - 0.45)

                core_a = 0.0
                core_h = 0.10
                if flame_d < 1.0:
                    u = 1.0 - flame_d
                    core_a = smoothstep(0.0, 0.22, u)
                    core_h = clamp(0.14 + 0.72 * (u ** 0.72) + 0.26 * smoothstep(0.55, 0.92, u))
                    # Boost solar core near bulb center
                    bulb_r = math.hypot(fx, fy - 0.08)
                    core_h = max(core_h, clamp(1.08 - bulb_r * 2.4))

                # Crisp 4-point solar glint at center + orbiting ember sparks
                star_d = min(abs(fx) + abs(fy - 0.08) * 3.8, abs(fy - 0.08) + abs(fx) * 3.8)
                if star_d < 0.22:
                    su = 1.0 - star_d / 0.22
                    if su > core_a:
                        core_a = su
                    core_h = max(core_h, 0.86 + 0.14 * su)

                for i in range(4):
                    ea = phase + i * 1.57 + 0.4
                    ex = math.cos(ea) * (0.62 + 0.08 * math.sin(phase + i))
                    ey = math.sin(ea) * 0.56 - 0.08
                    de = math.hypot(qx - ex, qy - ey)
                    if de < 0.085:
                        eu = 1.0 - de / 0.085
                        core_a = max(core_a, smoothstep(0.0, 0.4, eu) * 0.92)
                        core_h = max(core_h, 0.70 + 0.30 * eu)

                rr = math.hypot(rx, ry - 0.06)
                glow_a = math.exp(-((rr / 0.58) ** 2)) * 0.56 * smoothstep(0.98, 0.76, rr)
                px[f * size + x, y] = pack_ramp_pixel(SUN_FIRE_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 3. umbral_ice_crystal.png (512x128, 4 frames — Orbiting Umbral Ice) -----

def make_umbral_ice_crystal() -> Image.Image:
    """
    FF14 Umbral Ice (灵极冰 / 月弦心 & 灵极魂) orbiting faceted crystal:
    Sharp elongated hexagonal sapphire-diamond crystal with rotating bevel glints
    and delicate frost star halo.
    """
    frames, size = 4, 128
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

                # Elongated crystal diamond polygon (narrow waist, pointed top & bottom)
                ax, ay = abs(qx), abs(qy)
                # Hexagonal faceted silhouette
                body_d = max(ax / 0.42, (ax * 0.72 + ay * 0.52) / 0.44, ay / 0.80)

                core_a = 0.0
                core_h = 0.12
                if body_d < 1.0:
                    edge_u = 1.0 - body_d
                    core_a = smoothstep(0.0, 0.14, edge_u)
                    # Faceted crystal plane shading: crisp dark bevel on right/bottom, bright specular on top-left
                    facet = 0.55 - 0.34 * qx - 0.22 * qy
                    ridge = math.exp(-((abs(qx - 0.06 * math.sin(phase)) / 0.07) ** 2))
                    rim = smoothstep(0.18, 0.02, edge_u)
                    core_h = clamp(0.18 + 0.42 * facet + 0.32 * ridge + 0.20 * shimmer * (1.0 - ay) - 0.25 * rim)
                    # Crisp bright edge highlights on upper facets
                    if qy < 0.0 and edge_u < 0.14:
                        core_h = max(core_h, 0.82)

                # Thin crystalline orbit ring around waist
                ring_d = abs(math.hypot(qx, qy * 2.4) - 0.62)
                if ring_d < 0.045 and qy > -0.15:
                    ru = 1.0 - ring_d / 0.045
                    a = smoothstep(0.0, 0.4, ru) * 0.82
                    if a > core_a:
                        core_a = a
                        core_h = 0.74 + 0.26 * ru

                # 4 sparkling frost flecks
                for i in range(4):
                    ea = phase + i * 1.57 + 0.75
                    ex = math.cos(ea) * 0.68
                    ey = math.sin(ea) * 0.62
                    de = abs(qx - ex) + abs(qy - ey)
                    if de < 0.10:
                        eu = 1.0 - de / 0.10
                        core_a = max(core_a, smoothstep(0.0, 0.35, eu) * 0.90)
                        core_h = max(core_h, 0.80 + 0.20 * eu)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr / 0.56) ** 2)) * 0.50 * smoothstep(0.98, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.70, glow_a)

    return img


# --- 4. sun_orb.png (512x128, 4 frames — Fire I Projectile & Sun Chant Orb) --

def make_sun_orb() -> Image.Image:
    """
    FF14 Fire I (日炎) fireball & Sun chanting hand orb:
    Full-bodied blazing spherical fireball with charred-crimson rim, turbulent swirling
    fire corona tongues, flying ember sparks, and blinding white-gold center.
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        spin = f * (math.tau / frames)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                # Irregular organic swirling turbulence (no pinwheel symmetry!)
                n1 = fbm(qx * 3.8 + math.cos(spin) * 0.7, qy * 3.8 + math.sin(spin) * 0.7, 211 + f * 7, 4)
                n2 = fbm(math.cos(ang - spin) * 2.4 + r * 2.2, math.sin(ang - spin) * 2.4 + r * 2.2, 263, 3)
                corona_r = 0.56 + 0.24 * (n1 - 0.36) + 0.14 * (n2 - 0.45)

                core_a = 0.0
                core_h = 0.08
                if r < corona_r:
                    u = 1.0 - r / max(0.05, corona_r)
                    core_a = smoothstep(0.0, 0.20, u)
                    sphere_z = math.sqrt(max(0.0, 1.0 - min(1.0, (r / 0.54) ** 2)))
                    core_h = clamp(0.10 + 0.54 * sphere_z + 0.32 * (u ** 0.70) + 0.22 * (n1 - 0.42))
                    if r < 0.32:
                        core_h = max(core_h, clamp(1.05 - (r / 0.36) ** 1.35))

                # Orbiting crisp fire embers around the fireball
                for i in range(5):
                    ea = spin + i * (math.tau / 5.0) + 0.4 * (i % 2)
                    er = 0.68 + 0.12 * math.sin(spin * 2.0 + i * 2.1)
                    ex, ey = math.cos(ea) * er, math.sin(ea) * er
                    de = math.hypot(qx - ex, qy - ey)
                    if de < 0.075:
                        eu = 1.0 - de / 0.075
                        core_a = max(core_a, smoothstep(0.0, 0.35, eu) * 0.94)
                        core_h = max(core_h, 0.72 + 0.28 * eu)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr / 0.62) ** 2)) * 0.56 * smoothstep(0.99, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(SUN_FIRE_RAMP, core_h, core_a, 0.68, glow_a)

    return img


# --- 5. fire1_burst.png (1024x128, 8 frames — Fire I Impact Explosion) -------

def make_fire1_burst() -> Image.Image:
    """
    FF14 Fire I (日炎) crisp flame-petal ring burst:
    Snappy white-gold detonation opening into serrated crimson-gold flame ring & flying embers.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        exp_r = 0.24 + 0.68 * ease_out_cubic(t)
        inner_r = max(0.0, exp_r - (0.36 * (1.0 - 0.62 * t)))
        env = smoothstep(0.0, 0.12, t + 0.12) * (1.0 - 0.82 * smoothstep(0.48, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.0, qy * 5.0, 307 + f * 19, 3)

                petal = 1.0 + 0.18 * math.cos(ang * 7.0 + t * 2.4 + n * 2.2) + 0.12 * (n - 0.5)
                ro = exp_r * petal
                ri = inner_r * (0.94 + 0.10 * n)

                core_a = 0.0
                core_h = 0.12

                if ri <= r <= ro:
                    ru = (r - ri) / max(0.04, ro - ri)
                    band = math.sin(ru * math.pi)
                    breakup = 1.0 if (t < 0.52 or n > (t - 0.40) * 1.25) else 0.14
                    core_a = clamp(smoothstep(0.0, 0.28, band) * env * breakup * 1.2)
                    core_h = clamp(0.16 + 0.62 * band + 0.30 * (1.0 - t) + 0.14 * n)

                # Initial white-gold star flash in frames 0..2
                if t < 0.38:
                    flash_env = 1.0 - t / 0.38
                    rays = (abs(math.cos(ang * 4.0)) ** 5.0) * 0.72 * flash_env
                    if r < 0.32 * flash_env + rays:
                        su = 1.0 - r / max(0.05, 0.32 * flash_env + rays)
                        core_a = max(core_a, smoothstep(0.0, 0.3, su) * flash_env)
                        core_h = max(core_h, 0.80 + 0.20 * su)

                # Outward flying sparks in later frames
                if t > 0.25:
                    for i in range(8):
                        sa = i * (math.tau / 8.0) + 0.35 * (i % 3)
                        sr = 0.35 + 0.58 * t * (0.82 + 0.18 * (i % 2))
                        sx, sy = math.cos(sa) * sr, math.sin(sa) * sr
                        ds = math.hypot(qx - sx, qy - sy)
                        if ds < 0.065 * (1.0 - 0.4 * t):
                            su = 1.0 - ds / max(0.01, 0.065 * (1.0 - 0.4 * t))
                            core_a = max(core_a, smoothstep(0.0, 0.4, su) * env * 0.95)
                            core_h = max(core_h, 0.76 + 0.24 * su)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - exp_r * 0.75) / 0.24) ** 2) * 0.45 * env * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(SUN_FIRE_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 6. fire3_vortex.png (1024x128, 8 frames — FF14 Fire III Flame Vortex) ---

def make_fire3_vortex() -> Image.Image:
    """
    FF14 Fire III (烈日爆炎 / 爆炎) signature roaring flame vortex & blooming fire eruption!
    Vertical orientation: v=0 (top of image) is sky crown, v=1 (bottom) is ground base.
      - Frame 0..1: Converging crimson-gold flame swirl igniting wide at ground base!
      - Frame 2..5: Wild asymmetric roaring Fire III flame tongues & sweeping flame cyclone tearing skyward!
      - Frame 6..7: Charred crimson-gold flame plumes breaking apart into rising sparks!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        rise = clamp(0.34 + 0.66 * ease_out_cubic(min(1.0, t * 2.0)))
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.78 * smoothstep(0.56, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                vy = clamp((0.96 - qy) / 1.92)  # 0=ground, 1=sky top
                if vy > rise + 0.16:
                    continue

                # Turbulent rising flame-noise warped diagonally to evoke a ferocious spinning cyclone!
                swirl_x = qx + 0.22 * math.sin(vy * 5.5 - t * 6.5)
                n1 = fbm(swirl_x * 4.2, vy * 4.8 - t * 4.2, 419 + f * 17, 4)
                n2 = fbm(qx * 6.5 - vy * 2.4, vy * 6.0 - t * 5.6, 467 + f * 13, 3)

                # Roaring fire-vortex profile: broad turbulent base, sweeping fiery torso, ragged licking crown!
                base_flare = 0.34 * math.exp(-((vy / 0.24) ** 2))
                crown_lick = 0.22 * (n2 - 0.35) * smoothstep(0.25, 0.90, vy)
                local_top = rise + crown_lick
                top_fade = smoothstep(local_top + 0.04, max(0.06, local_top - 0.26), vy)
                half_w = (0.36 + base_flare + 0.26 * (n1 - 0.38)) * top_fade * (1.0 - 0.20 * smoothstep(0.60, 1.0, t))

                core_a = 0.0
                core_h = 0.08

                # Offset centerline slightly by rising turbulence for organic wild flame feel
                center_x = 0.08 * math.sin(vy * 4.8 - t * 5.2 + n1 * 2.0) * smoothstep(0.05, 0.45, vy)
                ax = abs(qx - center_x)
                if ax < half_w and vy <= local_top + 0.02:
                    wu = 1.0 - ax / max(0.03, half_w)
                    # Carve smoky dark flame fissures & ragged tongues
                    lick = wu + 0.34 * (n2 - 0.48)
                    breakup = 1.0 if (t < 0.50 or n1 > (t - 0.38) * 1.38) else 0.10
                    core_a = clamp(smoothstep(0.02, 0.32, lick) * env * breakup * 1.25)
                    # Strong dark obsidian-crimson outer edge -> scorching orange -> golden-white roaring spine!
                    core_h = clamp(0.10 + 0.58 * (wu ** 0.78) + 0.28 * (n2 - 0.35) + 0.22 * (1.0 - vy * 0.60))

                # Broad sweeping diagonal Fire III flame slash band wrapping across the column (smoothly tapered!)
                for band_idx in range(2):
                    band_y = ((vy * 1.35 - t * 1.1 + band_idx * 0.52) % 1.0)
                    band_env = math.sin(band_y * math.pi)
                    diag = (qx + 0.65 * (band_y - 0.5)) * 1.4
                    if abs(diag) < 0.22 and vy < rise * 0.92 and n1 > 0.36 and band_env > 0.05:
                        bu = 1.0 - abs(diag) / 0.22
                        ba = smoothstep(0.0, 0.35, bu) * smoothstep(0.36, 0.68, n1) * band_env * env * 0.85 * top_fade
                        if ba > core_a:
                            core_a = ba
                        core_h = max(core_h, 0.56 + 0.42 * bu * band_env)

                # Wide fiery shock-petal skirt around ground impact on frames 0..4
                if vy < 0.28 and t < 0.68:
                    petal_w = (0.88 - 0.22 * t) * (1.0 - vy / 0.28) * (0.85 + 0.28 * n2)
                    if abs(qx) < petal_w:
                        pu = 1.0 - abs(qx) / max(0.04, petal_w)
                        pa = smoothstep(0.0, 0.30, pu) * (1.0 - t / 0.68) * 0.90
                        if pa > core_a:
                            core_a = pa
                            core_h = max(core_h, 0.34 + 0.58 * pu)

                rvy = clamp((0.96 - ry) / 1.92)
                glow_a = (
                    math.exp(-((rx / 0.52) ** 2))
                    * smoothstep(rise + 0.12, max(0.0, rise - 0.25), rvy)
                    * 0.42 * env * smoothstep(0.99, 0.85, max(abs(rx), abs(ry)))
                )
                px[f * size + x, y] = pack_ramp_pixel(SUN_FIRE_RAMP, core_h, core_a, 0.70, glow_a)

    return img


# --- 7. corona_sun.png (1024x256, 4 frames of 256x256 — FF14 Fire IV Sun) ----

def make_corona_sun() -> Image.Image:
    """
    FF14 Fire IV (日冕 / 炽炎) colossal rising solar flare star orb (256x256 per frame!):
    Full-bodied spherical boiling sun with rich dark-crimson convection veins & sunspots,
    turbulent irregular coronal flame-corona around the limb, arched solar prominences,
    and an incandescent white-gold stellar core!
    """
    frames, size = 4, 256
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        spin = f * (math.tau / frames)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 128) - 0.5) * 2.0
                qy = (quantize(y / size, 128) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                # Spherical surface granulation + turbulent plasma convection
                n1 = fbm(qx * 4.8 + math.cos(spin) * 0.9, qy * 4.8 + math.sin(spin) * 0.9, 521, 4)
                n2 = fbm(qx * 9.2 - math.sin(spin) * 1.1, qy * 9.2 + math.cos(spin) * 1.1, 577, 3)
                # Irregular organic coronal flames licking softly around the true spherical limb (r=0.58)
                n_limb = fbm(math.cos(ang - spin * 0.5) * 3.8 + f * 0.6, math.sin(ang - spin * 0.5) * 3.8 - f * 0.5, 599, 4)
                corona_r = 0.60 + 0.22 * (n_limb - 0.35)

                core_a = 0.0
                core_h = 0.08

                # 1. Outer turbulent solar corona flame skirt (r between 0.54 and corona_r)
                if r < corona_r:
                    u = 1.0 - r / max(0.05, corona_r)
                    core_a = smoothstep(0.0, 0.24, u) * (0.85 if r > 0.57 else 1.0)
                    core_h = clamp(0.12 + 0.44 * (u ** 0.72) + 0.22 * (n_limb - 0.4))

                # 2. Crisp boiling spherical photosphere (r <= 0.58) — unmistakable fiery sun!
                if r <= 0.58:
                    u_sph = 1.0 - r / 0.58
                    sphere_z = math.sqrt(max(0.0, 1.0 - (r / 0.58) ** 2))
                    # Dark-crimson limb darkening + golden convective solar plasma + bright fusion core!
                    granules = 0.26 * (n1 - 0.46) + 0.18 * (n2 - 0.50)
                    limb_dark = smoothstep(0.26, 0.0, u_sph) * 0.34
                    sph_h = clamp(0.24 + 0.68 * (sphere_z ** 0.68) + granules - limb_dark)
                    if r < 0.26:
                        sph_h = max(sph_h, clamp(1.04 - (r / 0.30) ** 1.4))
                    core_a = max(core_a, smoothstep(0.0, 0.08, u_sph))
                    core_h = sph_h

                # 3. Thin sweeping solar prominence plasma loops arching off the sun's limb
                for i in range(3):
                    la = spin + i * (math.tau / 3.0) + 0.3 * i
                    da = abs(angle_wrap(ang - la))
                    if da < 0.58:
                        arch_r = 0.57 + 0.24 * math.cos(da / 0.58 * math.pi * 0.5)
                        d_arch = abs(r - arch_r)
                        if d_arch < 0.036:
                            au = 1.0 - d_arch / 0.036
                            aa = smoothstep(0.0, 0.35, au) * smoothstep(0.58, 0.12, da) * 0.90
                            if aa > core_a:
                                core_a = aa
                                core_h = max(core_h, 0.52 + 0.44 * au)

                # 4. Crisp celestial 4-ray solar gleam across the heart
                star_h = min(abs(qx) + abs(qy) * 6.5, abs(qy) + abs(qx) * 6.5)
                if star_h < 0.28 and r < 0.86:
                    su = (1.0 - star_h / 0.28) * smoothstep(0.86, 0.12, r)
                    if su > 0.14:
                        core_a = max(core_a, su * 0.90)
                        core_h = max(core_h, 0.84 + 0.16 * su)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr / 0.66) ** 2)) * 0.48
                    + math.exp(-((rr - 0.58) / 0.12) ** 2) * 0.26
                ) * smoothstep(0.99, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(CORONA_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 8. corona_ring.png (256x256, 1 frame — Tilted 3D Fire IV Coronal Ring) --

def make_corona_ring() -> Image.Image:
    """
    Tilted 3D orbiting solar-flare flame ring for Fire IV (日冕) & Fire III (烈日爆炎):
    Serrated solar prominence corona ring with dark charred rim and glowing golden arcane arc segments.
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
            rr = math.hypot(rx, ry)
            if rr > 1.0:
                continue
            ang = math.atan2(qy, qx)
            n = fbm(math.cos(ang) * 4.0, math.sin(ang) * 4.0, 613, 3)

            # Main turbulent solar ring at r=0.74 with serrated flame flares
            flare = 0.08 * math.sin(ang * 9.0 + n * 3.0) + 0.05 * math.cos(ang * 15.0)
            ring_center = 0.72 + flare * 0.45
            thick = 0.105 + 0.055 * (n - 0.3)
            d = abs(r - ring_center)

            core_a = 0.0
            core_h = 0.10
            if d < thick:
                u = 1.0 - d / max(0.01, thick)
                core_a = smoothstep(0.0, 0.25, u)
                core_h = clamp(0.14 + 0.74 * (u ** 0.70) + 0.16 * n)

            # Crisp inner arcane double-hairline ring (r=0.58)
            d_in = abs(r - 0.58)
            if d_in < 0.022:
                iu = 1.0 - d_in / 0.022
                arc_gate = smoothstep(-0.2, 0.35, math.sin(ang * 6.0))
                ia = smoothstep(0.0, 0.35, iu) * (0.45 + 0.55 * arc_gate)
                if ia > core_a:
                    core_a = ia
                    core_h = max(core_h, 0.72 + 0.28 * iu)

            glow_a = math.exp(-((rr - 0.72) / 0.14) ** 2) * 0.48 * smoothstep(0.99, 0.85, rr)
            px[x, y] = pack_ramp_pixel(CORONA_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 9. corona_nova.png (1024x128, 8 frames — FF14 Fire IV Supernova) --------

def make_corona_nova() -> Image.Image:
    """
    FF14 Fire IV (日冕 / 炽炎) cataclysmic supernova detonation:
      - Frame 0: Violent dark-rimmed implosion pinch + blinding white-gold cross flash
      - Frame 1..4: Massive 8-ray solar-flare explosion + roaring plasma blast ring
      - Frame 5..7: Shredded crimson-gold corona rings & floating ember arcs dispersing
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.82 * smoothstep(0.50, 1.0, t))
        ring_r = 0.18 + 0.76 * ease_out_cubic(t)
        ring_w = 0.28 * (1.0 - 0.60 * t)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.2, qy * 5.2, 709 + f * 29, 3)

                core_a = 0.0
                core_h = 0.08

                # Frame 0..1: High-drama Fire IV implosion dark ring + white-gold cross star!
                if f <= 2:
                    pinch = 1.0 - f * 0.34
                    d_imp = abs(r - 0.44 * pinch)
                    if d_imp < 0.10:
                        iu = 1.0 - d_imp / 0.10
                        core_a = max(core_a, smoothstep(0.0, 0.3, iu) * 0.92 * pinch)
                        core_h = max(core_h, 0.12 + 0.55 * iu)
                    star8 = (abs(math.cos(ang * 4.0)) ** 7.0) * 0.94 * pinch + (abs(math.cos(ang * 4.0 + math.pi / 4.0)) ** 9.0) * 0.62 * pinch
                    if r < 0.30 * pinch + star8:
                        su = 1.0 - r / max(0.05, 0.30 * pinch + star8)
                        core_a = max(core_a, smoothstep(0.0, 0.25, su) * 1.0)
                        core_h = max(core_h, 0.82 + 0.18 * su)

                # Expanding roaring coronal blast ring
                ro = ring_r * (1.0 + 0.14 * math.sin(ang * 8.0 + n * 2.5) + 0.10 * (n - 0.5))
                ri = max(0.0, ro - ring_w)
                if ri <= r <= ro:
                    ru = (r - ri) / max(0.04, ro - ri)
                    band = math.sin(ru * math.pi)
                    breakup = 1.0 if (t < 0.55 or n > (t - 0.44) * 1.35) else 0.14
                    ba = clamp(smoothstep(0.0, 0.26, band) * env * breakup * 1.25)
                    if ba > core_a:
                        core_a = ba
                        core_h = clamp(0.14 + 0.66 * band + 0.24 * (1.0 - t))

                # 8 piercing solar flare rays shooting outward on frames 1..5
                if 1 <= f <= 5:
                    ray_env = 1.0 - (f - 1) / 5.0
                    ray_l = 0.28 + 0.66 * (abs(math.cos(ang * 4.0 + 0.12 * f)) ** 6.0) * (0.75 + 0.25 * n)
                    if r < ray_l and r > ri * 0.45:
                        ru = (1.0 - r / max(0.05, ray_l)) * ray_env
                        ra = smoothstep(0.0, 0.32, ru) * 0.95
                        if ra > core_a:
                            core_a = ra
                            core_h = max(core_h, 0.62 + 0.38 * ru)

                rr = math.hypot(rx, ry)
                glow_a = (
                    math.exp(-((rr - ring_r * 0.75) / 0.24) ** 2) * 0.48
                    + math.exp(-((rr / 0.45) ** 2)) * 0.42 * (1.0 - t)
                ) * env * smoothstep(0.99, 0.84, rr)
                px[f * size + x, y] = pack_ramp_pixel(CORONA_RAMP, core_h, core_a, 0.76, glow_a)

    return img


# --- 10. solar_crater.png (256x256, 1 frame — Scorched Solar Rune Crater) ----

def make_solar_crater() -> Image.Image:
    """
    Lingering volcanic-obsidian scorched crater with glowing molten solar cracks
    for Fire I / Fire III / Fire IV ground impacts!
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # Deterministic radiating crack directions
    crack_angles = [i * (math.tau / 10.0) + 0.18 * math.sin(i * 2.7) for i in range(10)]

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            r = math.hypot(qx, qy)
            rr = math.hypot(rx, ry)
            if rr > 1.0:
                continue
            ang = math.atan2(qy, qx)
            n = fbm(qx * 4.5, qy * 4.5, 811, 3)

            # Charred dark scorched earth disc base (strong daytime anchor!)
            edge = 0.86 + 0.10 * (n - 0.5)
            core_a = 0.0
            core_h = 0.04
            if r < edge:
                u = 1.0 - r / edge
                core_a = smoothstep(0.0, 0.35, u) * (0.70 + 0.18 * n)
                core_h = 0.05 + 0.16 * (u ** 1.4) + 0.08 * n

            # Glowing molten cracks radiating from center
            for idx, ca in enumerate(crack_angles):
                bend = 0.16 * math.sin(r * 6.5 + idx * 1.9)
                da = abs(angle_wrap(ang - (ca + bend)))
                arc_dist = da * max(0.06, r)
                w = (0.036 - 0.022 * r) * (0.85 + 0.30 * n)
                if r < 0.88 and arc_dist < w:
                    cu = 1.0 - arc_dist / max(0.005, w)
                    ca_a = smoothstep(0.0, 0.35, cu) * smoothstep(0.88, 0.50, r)
                    core_a = max(core_a, ca_a * 0.96)
                    core_h = max(core_h, 0.45 + 0.55 * cu * (1.0 - 0.35 * r))

            # Glowing fractured concentric ring at r=0.46
            d_ring = abs(r - (0.46 + 0.05 * (n - 0.5)))
            if d_ring < 0.032 and n > 0.34:
                ru = 1.0 - d_ring / 0.032
                core_a = max(core_a, smoothstep(0.0, 0.35, ru) * 0.90)
                core_h = max(core_h, 0.55 + 0.42 * ru)

            glow_a = math.exp(-((rr / 0.52) ** 2)) * 0.38 * smoothstep(0.98, 0.72, rr)
            px[x, y] = pack_ramp_pixel(CORONA_RAMP, core_h, core_a, 0.65, glow_a)

    return img


# --- 11. moon_disc.png (512x128, 4 frames — Moon Flow Lunar Phantom Disc) ----

def make_moon_disc() -> Image.Image:
    """
    Moon Flow (月流) signature radiant Moon Phantom & projectile:
    Luminous full/crescent moon with carved dark-indigo lunar maria details,
    blazing silver-cyan crescent highlight rim, and delicate rotating star-orbit ring!
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
                ang = math.atan2(qy, qx)
                n = fbm(qx * 3.6 + 0.25 * math.cos(phase), qy * 3.6 + 0.25 * math.sin(phase), 907, 3)

                core_a = 0.0
                core_h = 0.12

                # Luminous lunar disc (r <= 0.60)
                if r <= 0.60:
                    u = 1.0 - r / 0.60
                    core_a = smoothstep(0.0, 0.10, u)
                    # Subtle dark-indigo lunar maria craters on top-right, bright crescent rim on bottom-left
                    crescent_light = clamp(0.52 - 0.52 * qx + 0.24 * qy)
                    maria = smoothstep(0.32, 0.68, n)
                    rim_bright = smoothstep(0.22, 0.02, u) * clamp(0.35 - 0.75 * qx + 0.35 * qy)
                    # Crisp dark outer outline for daytime visibility
                    outline = smoothstep(0.09, 0.01, u)
                    core_h = clamp(0.30 + 0.44 * crescent_light - 0.18 * maria + 0.34 * rim_bright - 0.36 * outline)

                # Inner etched crescent moon emblem glowing brightly
                c_outer = math.hypot(qx + 0.04, qy)
                c_inner = math.hypot(qx - 0.12, qy + 0.05)
                if c_outer < 0.54 and c_inner > 0.41:
                    cu = smoothstep(0.54, 0.46, c_outer) * smoothstep(0.41, 0.49, c_inner)
                    core_h = max(core_h, 0.76 + 0.24 * cu)

                # Delicate celestial orbital halo ring around the moon (r = 0.78)
                d_orb = abs(r - 0.78)
                if d_orb < 0.032:
                    ou = 1.0 - d_orb / 0.032
                    # Slight shimmer modulation around ring
                    sh = 0.55 + 0.45 * math.sin(ang * 3.0 - phase)
                    oa = smoothstep(0.0, 0.35, ou) * sh * 0.88
                    if oa > core_a:
                        core_a = oa
                        core_h = 0.62 + 0.34 * ou

                # 2 tiny orbiting moon-stars on the halo ring
                for i in range(2):
                    sa = phase + i * math.pi + 0.5
                    sx, sy = math.cos(sa) * 0.78, math.sin(sa) * 0.78
                    ds = min(abs(qx - sx) + abs(qy - sy) * 3.2, abs(qy - sy) + abs(qx - sx) * 3.2)
                    if ds < 0.18:
                        su = 1.0 - ds / 0.18
                        core_a = max(core_a, smoothstep(0.0, 0.35, su) * 0.96)
                        core_h = max(core_h, 0.85 + 0.15 * su)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr / 0.66) ** 2)) * 0.52 * smoothstep(0.99, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 12. frost_crystal_spike.png (1024x128, 8 frames — Blizzard III/IV Spire) -

def make_frost_crystal_spike() -> Image.Image:
    """
    FF14 Blizzard III / Blizzard IV / Freeze (月流 / 月见星辉) faceted glacial ice crystal cluster!
    Vertical orientation: v=0 tip, v=1 ground base.
      - Frame 0..2: Jagged multi-pronged diamond ice spire erupting sharply from frost base
      - Frame 3..5: Peak crystalline radiance with deep sapphire bevels & silver specular edges
      - Frame 6..7: Crisp shattering into floating angular ice fragments
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Main central crystal spire + 2 angled side crystal shards
    shards = (
        (0.00, 0.92, 0.30, 0.00),   # center tall obelisk
        (-0.32, 0.58, 0.19, -0.26), # left flanking prism
        (0.30, 0.52, 0.18, 0.28),   # right flanking prism
    )

    for f in range(frames):
        t = f / (frames - 1)
        grow = 0.28 + 0.72 * ease_out_cubic(min(1.0, t / 0.34))
        shatter = smoothstep(0.62, 1.0, t)
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.84 * smoothstep(0.68, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                vy = clamp((0.95 - qy) / 1.90)  # 0=base, 1=top
                core_a = 0.0
                core_h = 0.10

                for idx, (bx, max_h, base_w, lean) in enumerate(shards):
                    h = max_h * grow
                    if vy > h + 0.18 * shatter:
                        continue
                    # Shatter lifts & cracks fragments outward
                    sh_dx = (0.14 * (idx - 1) + 0.08 * math.sin(vy * 15.0 + idx)) * shatter
                    sh_dy = 0.16 * shatter * (0.4 + vy)
                    lvy = vy - sh_dy
                    if lvy < 0.0 or lvy > h:
                        continue
                    u_h = lvy / max(0.05, h)
                    cx = bx + lean * lvy + sh_dx
                    # Sharp diamond spire taper (broad lower shoulder -> razor spearhead tip)
                    w = base_w * (1.0 - 0.72 * (u_h ** 1.35)) * smoothstep(1.0, 0.88, u_h)
                    dx = qx - cx
                    if abs(dx) < w:
                        wu = 1.0 - abs(dx) / max(0.015, w)
                        n = fbm(qx * 7.0, qy * 7.0, 1009 + idx * 31 + f * 13, 2)
                        if shatter > 0.15 and n < shatter * 0.88:
                            continue
                        a = smoothstep(0.0, 0.18, wu) * env
                        # Faceted crystal shading: bright left bevel, deep sapphire right bevel, white ridge!
                        side = dx / max(0.02, w)
                        ridge = math.exp(-(((side + 0.12) / 0.16) ** 2))
                        facet_h = clamp(0.16 + 0.44 * (0.5 - 0.48 * side) + 0.36 * ridge + 0.18 * u_h - 0.20 * smoothstep(0.20, 0.02, wu))
                        if a > core_a:
                            core_a = a
                            core_h = facet_h

                rvy = clamp((0.95 - ry) / 1.90)
                glow_a = (
                    math.exp(-((rx / 0.42) ** 2))
                    * smoothstep(grow + 0.10, max(0.0, grow - 0.30), rvy)
                    * 0.40 * env * smoothstep(0.99, 0.84, max(abs(rx), abs(ry)))
                )
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 13. frost_burst.png (1024x128, 8 frames — Hexagonal Ice Star Burst) -----

def make_frost_burst() -> Image.Image:
    """
    FF14 Blizzard III / Freeze (月流 / 月见星辉) hexagonal snowflake crystal burst:
    Razor-sharp 6-fold geometric ice star bursting outward with faceted needles & diamond dust!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        exp_r = 0.22 + 0.72 * ease_out_cubic(t)
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.84 * smoothstep(0.50, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                n = fbm(qx * 6.0, qy * 6.0, 1103 + f * 17, 2)

                core_a = 0.0
                core_h = 0.10

                # Crisp expanding hexagonal frost ring
                dh, _ = regular_poly_edge_dist(qx, qy, 6, exp_r, t * 0.4)
                rw = 0.095 * (1.0 - 0.45 * t)
                if dh < rw and r <= exp_r * 1.06:
                    ru = 1.0 - dh / max(0.015, rw)
                    breakup = 1.0 if (t < 0.56 or n > (t - 0.44) * 1.35) else 0.12
                    core_a = clamp(smoothstep(0.0, 0.28, ru) * env * breakup * 1.15)
                    core_h = clamp(0.18 + 0.74 * (ru ** 0.75) + 0.14 * (1.0 - t))

                # 6 piercing crystalline snowflake needles + chevron side-barbs
                sec6 = abs(angle_wrap(((ang + math.pi / 6.0) % (math.pi / 3.0)) - math.pi / 6.0))
                needle_w = 0.048 * (1.0 - 0.5 * t) / max(0.18, r)
                if sec6 < needle_w and r < exp_r * 1.04 and r > exp_r * 0.18 * t:
                    nu = 1.0 - sec6 / max(0.005, needle_w)
                    na = smoothstep(0.0, 0.30, nu) * smoothstep(exp_r * 1.04, exp_r * 0.55, r) * env
                    if na > core_a:
                        core_a = na
                        core_h = max(core_h, 0.66 + 0.34 * nu)

                # Sparkling diamond dust flecks scattered along hex diagonals
                for i in range(6):
                    da = i * (math.pi / 3.0) + math.pi / 6.0
                    dr = exp_r * (0.62 + 0.28 * t)
                    dx, dy = math.cos(da) * dr, math.sin(da) * dr
                    dd = abs(qx - dx) + abs(qy - dy)
                    if dd < 0.09 * (1.0 - 0.35 * t):
                        du = 1.0 - dd / max(0.01, 0.09 * (1.0 - 0.35 * t))
                        core_a = max(core_a, smoothstep(0.0, 0.35, du) * env * 0.95)
                        core_h = max(core_h, 0.80 + 0.20 * du)

                rr = math.hypot(rx, ry)
                glow_a = math.exp(-((rr - exp_r * 0.72) / 0.20) ** 2) * 0.42 * env * smoothstep(0.99, 0.85, rr)
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.74, glow_a)

    return img


# --- 14. moon_aurora_pillar.png (512x128, 4 frames — Starfall Moonlight Beam)

def make_moon_aurora_pillar() -> Image.Image:
    """
    Moon Starfall (月见星辉 / Blizzard IV × Freeze) descending celestial moonlight aurora column:
    Silky vertical ribbons of silver-azure moonlight + delicate cascading starlight streaks.
    Graceful mid-density alpha so multiple overlapping casts stack cleanly without blinding whiteout!
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        shift = f * 0.25
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                ax = abs(qx)
                if ax > 0.94:
                    continue
                # Vertical aurora curtain fades softly at top & touches ground crisply at bottom
                v_env = smoothstep(-1.0, -0.62, qy) * smoothstep(1.0, 0.82, qy)
                # Silky vertical light flutes scrolling gently downward
                flute1 = 0.5 + 0.5 * math.sin(qx * 10.5 + math.sin(qy * 2.2 + shift * math.tau) * 0.8)
                flute2 = 0.5 + 0.5 * math.cos(qx * 18.0 - shift * math.tau)
                n = fbm(qx * 3.5, (qy + shift * 2.0) * 2.4, 1213, 3)

                radial = smoothstep(0.92, 0.14, ax)
                core_a = radial * v_env * (0.34 + 0.34 * flute1 + 0.16 * flute2) * (0.72 + 0.28 * n)
                # Center razor moonlight beam spine
                spine = math.exp(-((ax / 0.16) ** 2)) * v_env * 0.52
                core_a = clamp(max(core_a, spine + core_a * 0.45))

                # Crisp dark-sapphire outer veil edge + luminous cyan-silver center!
                core_h = clamp(0.18 + 0.48 * radial + 0.24 * flute1 + 0.22 * math.exp(-((ax / 0.20) ** 2)))

                # Descending starlight sparkles inside the aurora curtain
                for i in range(6):
                    sx = -0.62 + i * 0.248 + 0.04 * math.sin(i * 3.1)
                    sy = (((i * 0.37 + shift) % 1.0) * 1.72) - 0.86
                    ds = abs(qx - sx) * 2.6 + abs(qy - sy) * 0.9
                    if ds < 0.14:
                        su = 1.0 - ds / 0.14
                        core_a = max(core_a, smoothstep(0.0, 0.4, su) * 0.88 * v_env)
                        core_h = max(core_h, 0.82 + 0.18 * su)

                glow_a = math.exp(-((rx / 0.48) ** 2)) * smoothstep(-1.0, -0.5, ry) * smoothstep(1.0, 0.78, ry) * 0.26
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.72, glow_a)

    return img


# --- 15. star_meteor.png (512x128, 4 frames — Falling Starlight Comet) -------

def make_star_meteor() -> Image.Image:
    """
    Moon Starfall (月见星辉) falling celestial star-crystal comet:
    4-point dazzling starlight head at bottom (v=0.78) with slender icy-silver comet tail rising to v=0!
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        pulse = 0.88 + 0.12 * math.sin(f * math.pi * 0.5)
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                hy = qy - 0.44  # star head near lower third (falling downward)
                core_a = 0.0
                core_h = 0.14

                # 4-point celestial diamond star head
                star_d = min(abs(qx) + abs(hy) * 3.4, abs(hy) + abs(qx) * 3.4)
                head_r = math.hypot(qx, hy)
                if star_d < 0.42 * pulse:
                    su = 1.0 - star_d / (0.42 * pulse)
                    core_a = smoothstep(0.0, 0.24, su)
                    core_h = clamp(0.45 + 0.55 * su)
                if head_r < 0.24 * pulse:
                    hu = 1.0 - head_r / (0.24 * pulse)
                    core_a = max(core_a, smoothstep(0.0, 0.25, hu))
                    core_h = max(core_h, 0.72 + 0.28 * hu)

                # Slender tapering crystalline comet tail stretching upward (qy from 0.44 up to -0.92)
                if -0.94 < qy < 0.44:
                    tu = clamp((0.44 - qy) / 1.36)  # 0 at head, 1 at tail end
                    tw = 0.16 * ((1.0 - tu) ** 1.15)
                    if abs(qx) < tw:
                        wu = 1.0 - abs(qx) / max(0.01, tw)
                        ta = smoothstep(0.0, 0.35, wu) * (1.0 - tu * 0.88) * 0.92
                        if ta > core_a:
                            core_a = ta
                            core_h = max(core_h, 0.24 + 0.58 * wu * (1.0 - 0.5 * tu))

                rr = math.hypot(rx, ry - 0.44)
                glow_a = math.exp(-((rr / 0.46) ** 2)) * 0.52 * smoothstep(0.98, 0.72, rr)
                px[f * size + x, y] = pack_ramp_pixel(MOON_ICE_RAMP, core_h, core_a, 0.76, glow_a)

    return img


# --- 16. paradox_eclipse_sigil.png (256x256, 1 frame — Sun-Moon Eclipse) -----

def make_paradox_eclipse_sigil() -> Image.Image:
    """
    FF14 Paradox (悖论) signature Sun-Moon Eclipse Ground Sigil:
    Interlocked Ice-Cyan Lunar Crescent & Crimson-Magenta Solar Corona inside a dark obsidian-violet
    arcane astrolabe ring!
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
            rr = math.hypot(rx, ry)
            if rr > 1.0:
                continue
            ang = math.atan2(qy, qx)

            # Dual polarity mix: smooth transition between Ice-Cyan (left/top) & Crimson-Magenta (right/bottom)
            pol = clamp(0.5 + 0.5 * math.sin(ang + r * 1.4))  # 0=Ice, 1=Crimson-Fire
            core_a = 0.0
            core_h = 0.12
            use_void = False

            def stroke(dist: float, width: float, h_val: float, strength: float = 1.0, void_mode: bool = False) -> None:
                nonlocal core_a, core_h, use_void
                if dist < width + 0.022:
                    if dist < width:
                        u = 1.0 - dist / max(1e-4, width)
                        a = smoothstep(0.0, 0.32, u) * strength
                        if a > core_a:
                            core_a = a
                            core_h = lerp(0.42, h_val, u ** 0.7)
                            use_void = void_mode
                    else:
                        ru = 1.0 - (dist - width) / 0.022
                        ra = smoothstep(0.0, 0.5, ru) * 0.76 * strength
                        if ra > core_a:
                            core_a = ra
                            core_h = 0.08
                            use_void = True

            # Outer eclipse astrolabe rings
            stroke(abs(r - 0.90), 0.024, 0.94, 1.0, False)
            stroke(abs(r - 0.76), 0.015, 0.88, 0.92, True)
            stroke(abs(r - 0.44), 0.018, 0.92, 0.95, False)

            # 8 alternating sun-ray spikes & moon-crystal chevrons around r=0.76..0.90
            sec8 = ((ang + math.pi) / math.tau) * 8.0
            frac8 = abs((sec8 % 1.0) - 0.5)
            if 0.74 <= r <= 0.92 and frac8 < 0.09:
                stroke(frac8 * 0.24, 0.018, 0.95, 0.92, False)

            # Interlaced twin diamonds (8-point Paradox star)
            for rot_d in (0.0, math.pi / 4.0):
                dd, _ = regular_poly_edge_dist(qx, qy, 4, 0.76, rot_d)
                if r <= 0.77:
                    stroke(dd, 0.016, 0.92, 0.95, False)

            # Center yin-yang / eclipse crescent divider
            d_c1 = abs(math.hypot(qx - 0.14, qy) - 0.30)
            d_c2 = abs(math.hypot(qx + 0.14, qy) - 0.30)
            if r < 0.45:
                stroke(min(d_c1, d_c2), 0.020, 0.96, 0.96, False)
            # Eclipse core eye
            if r < 0.15:
                eu = 1.0 - r / 0.15
                core_a = max(core_a, smoothstep(0.0, 0.35, eu))
                core_h = max(core_h, 0.78 + 0.22 * eu)
                use_void = True

            if use_void:
                cr, cg, cb = sample_ramp(PARADOX_VOID_RAMP, core_h)
            else:
                ir, ig, ib = sample_ramp(MOON_ICE_RAMP, core_h)
                fr, fg, fb = sample_ramp(PARADOX_CRIMSON_RAMP, core_h)
                cr = lerp(ir, fr, pol)
                cg = lerp(ig, fg, pol)
                cb = lerp(ib, fb, pol)

            # Dual-tone glow halo
            g_pol = clamp(0.5 + 0.5 * math.sin(math.atan2(ry, rx) + rr * 1.4))
            gir, gig, gib = sample_ramp(MOON_ICE_RAMP, 0.72)
            gfr, gfg, gfb = sample_ramp(PARADOX_CRIMSON_RAMP, 0.72)
            gr = lerp(gir, gfr, g_pol)
            gg = lerp(gig, gfg, g_pol)
            gb = lerp(gib, gfb, g_pol)
            glow_a = (
                math.exp(-((rr - 0.83) / 0.09) ** 2) * 0.38
                + math.exp(-((rr - 0.44) / 0.08) ** 2) * 0.32
                + math.exp(-((rr / 0.22) ** 2)) * 0.36
            ) * smoothstep(0.99, 0.86, rr)

            px[x, y] = pack_rgb_dual(cr, cg, cb, core_a, gr, gg, gb, glow_a)

    return img


# --- 17. paradox_burst.png (1024x128, 8 frames — FF14 Paradox Supernova) -----

def make_paradox_burst() -> Image.Image:
    """
    FF14 Paradox (悖论) 50-level Awakening signature dual-polarity supernova!
      - Frame 0: Abyssal dark-violet eclipse implosion pinch + blinding pinpoint flash
      - Frame 1..4: Razor-sharp asymmetric Ice-Cyan × Crimson-Magenta Cross-Star Supernova
                    (diamond frost spears on one diagonal clashing with blazing crimson flare blades
                     on the perpendicular diagonal, enclosed in a shattering violet-white shock ring!)
      - Frame 5..7: Swirling twin-color ice shards & crimson flame sparks dispersing cleanly!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.82 * smoothstep(0.52, 1.0, t))
        ring_r = 0.20 + 0.74 * ease_out_cubic(t)
        ring_w = 0.20 * (1.0 - 0.55 * t)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                n = fbm(qx * 5.5, qy * 5.5, 1307 + f * 31, 3)

                # Rotate 45 deg for crisp X-cross dual-polarity blades:
                # Axis A (u_ice): Ice-Cyan crystalline spear rays
                # Axis B (u_fire): Crimson-Magenta scorching flare blades
                c45 = 0.70710678
                ux = qx * c45 - qy * c45
                uy = qx * c45 + qy * c45

                ice_a = 0.0
                ice_h = 0.12
                fire_a = 0.0
                fire_h = 0.12
                void_a = 0.0
                void_h = 0.12

                # Frame 0..1: Dark abyssal eclipse pinch + pinpoint flash
                if f <= 1:
                    pinch = 1.0 - f * 0.42
                    d_p = abs(r - 0.46 * pinch)
                    if d_p < 0.11:
                        pu = 1.0 - d_p / 0.11
                        void_a = max(void_a, smoothstep(0.0, 0.3, pu) * 0.96)
                        void_h = max(void_h, 0.10 + 0.48 * pu)
                    if r < 0.24:
                        cu = 1.0 - r / 0.24
                        void_a = max(void_a, smoothstep(0.0, 0.25, cu))
                        void_h = max(void_h, 0.80 + 0.20 * cu)

                # Clashing Cross-Star Supernova blades (Frames 1..6) — piercing past the shock ring!
                if f >= 1:
                    blade_env = 1.0 - max(0.0, (t - 0.14) / 0.86) ** 1.20
                    span = 0.46 + 0.52 * ease_out_cubic(min(1.0, t * 2.1))

                    # Ice-Cyan crystalline spear along UX axis + vertical piercing crystal lance
                    d_ice = min(abs(uy) + abs(ux) * 0.13, abs(qx) * 1.15 + abs(qy) * 0.12)
                    ice_w = (0.16 + 0.09 * (1.0 - t)) * smoothstep(span + 0.06, 0.0, r)
                    if d_ice < ice_w and r < span:
                        iu = 1.0 - d_ice / max(0.014, ice_w)
                        brk = 1.0 if (t < 0.56 or n > (t - 0.44) * 1.35) else 0.14
                        ice_a = max(ice_a, smoothstep(0.0, 0.22, iu) * blade_env * brk)
                        ice_h = max(ice_h, 0.26 + 0.74 * (iu ** 0.65))

                    # Crimson-Magenta scorching flare along UY axis + horizontal piercing flare blade
                    d_fire = min(abs(ux) + abs(uy) * 0.13, abs(qy) * 1.15 + abs(qx) * 0.12)
                    fire_w = (0.17 + 0.09 * (1.0 - t)) * smoothstep(span + 0.06, 0.0, r)
                    if d_fire < fire_w and r < span:
                        fu = 1.0 - d_fire / max(0.014, fire_w)
                        brk = 1.0 if (t < 0.56 or n > (t - 0.44) * 1.35) else 0.14
                        fire_a = max(fire_a, smoothstep(0.0, 0.22, fu) * blade_env * brk)
                        fire_h = max(fire_h, 0.26 + 0.74 * (fu ** 0.65))

                # Shattering circular dual-polarity eclipse shockwave ring (organic turbulence, crisp circle!)
                ro = ring_r * (1.0 + 0.07 * (n - 0.48))
                ri = max(0.0, ro - ring_w)
                if ri <= r <= ro:
                    ru = (r - ri) / max(0.03, ro - ri)
                    band = math.sin(ru * math.pi)
                    brk = 1.0 if (t < 0.56 or n > (t - 0.42) * 1.35) else 0.14
                    ra = clamp(smoothstep(0.0, 0.26, band) * env * brk * 1.15)
                    pol_ring = clamp(0.5 + 0.5 * math.sin(ang * 2.0 + t * 2.4))
                    if pol_ring < 0.5:
                        ice_a = max(ice_a, ra)
                        ice_h = max(ice_h, 0.22 + 0.72 * band)
                    else:
                        fire_a = max(fire_a, ra)
                        fire_h = max(fire_h, 0.22 + 0.72 * band)

                # Flying dual-polarity crystal & crimson shrapnel flecks in frames 2..6
                if 2 <= f <= 6:
                    for i in range(8):
                        sa = i * (math.tau / 8.0) + 0.22 * (i % 3)
                        sr = 0.28 + 0.66 * ease_out_cubic(t) * (0.85 + 0.15 * (i % 2))
                        sx, sy = math.cos(sa) * sr, math.sin(sa) * sr
                        ds = abs(qx - sx) + abs(qy - sy)
                        if ds < 0.095 * (1.0 - 0.35 * t):
                            su = 1.0 - ds / max(0.01, 0.095 * (1.0 - 0.35 * t))
                            sa_val = smoothstep(0.0, 0.35, su) * env * 0.96
                            if i % 2 == 0:
                                ice_a = max(ice_a, sa_val)
                                ice_h = max(ice_h, 0.78 + 0.22 * su)
                            else:
                                fire_a = max(fire_a, sa_val)
                                fire_h = max(fire_h, 0.78 + 0.22 * su)

                # Central blinding violet-white collision core on frames 1..3
                if 1 <= f <= 3:
                    c_env = 1.0 - (f - 1) / 3.0
                    if r < 0.36 * c_env + 0.08:
                        cu = 1.0 - r / (0.36 * c_env + 0.08)
                        void_a = max(void_a, smoothstep(0.0, 0.25, cu) * c_env)
                        void_h = max(void_h, 0.78 + 0.22 * cu)

                # Composite dominant channel (Ice vs Fire vs Void) cleanly so colors never turn muddy
                total_w = ice_a + fire_a + void_a
                if total_w > 0.008:
                    ir, ig, ib = sample_ramp(MOON_ICE_RAMP, ice_h)
                    fr, fg, fb = sample_ramp(PARADOX_CRIMSON_RAMP, fire_h)
                    vr, vg, vb = sample_ramp(PARADOX_VOID_RAMP, void_h)
                    cr = (ir * ice_a + fr * fire_a + vr * void_a) / total_w
                    cg = (ig * ice_a + fg * fire_a + vg * void_a) / total_w
                    cb = (ib * ice_a + fb * fire_a + vb * void_a) / total_w
                    core_a = clamp(max(ice_a, fire_a, void_a))
                else:
                    cr = cg = cb = 0.0
                    core_a = 0.0

                rr = math.hypot(rx, ry)
                g_pol = clamp(0.5 + 0.5 * math.sin(math.atan2(ry, rx) * 2.0 + t * 2.0))
                gir, gig, gib = sample_ramp(MOON_ICE_RAMP, 0.74)
                gfr, gfg, gfb = sample_ramp(PARADOX_CRIMSON_RAMP, 0.74)
                gr = lerp(gir, gfr, g_pol)
                gg = lerp(gig, gfg, g_pol)
                gb = lerp(gib, gfb, g_pol)
                glow_a = (
                    math.exp(-((rr - ring_r * 0.74) / 0.22) ** 2) * 0.45
                    + math.exp(-((rr / 0.42) ** 2)) * 0.40 * (1.0 - t)
                ) * env * smoothstep(0.99, 0.84, rr)

                px[f * size + x, y] = pack_rgb_dual(cr, cg, cb, core_a, gr, gg, gb, glow_a)

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
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(214, 224, 255, 255))
        y_cur += label_h
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(62, 68, 104, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "ley_lines_sigil.png": make_ley_lines_sigil(),
        "astral_fire_wisp.png": make_astral_fire_wisp(),
        "umbral_ice_crystal.png": make_umbral_ice_crystal(),
        "sun_orb.png": make_sun_orb(),
        "fire1_burst.png": make_fire1_burst(),
        "fire3_vortex.png": make_fire3_vortex(),
        "corona_sun.png": make_corona_sun(),
        "corona_ring.png": make_corona_ring(),
        "corona_nova.png": make_corona_nova(),
        "solar_crater.png": make_solar_crater(),
        "moon_disc.png": make_moon_disc(),
        "frost_crystal_spike.png": make_frost_crystal_spike(),
        "frost_burst.png": make_frost_burst(),
        "moon_aurora_pillar.png": make_moon_aurora_pillar(),
        "star_meteor.png": make_star_meteor(),
        "paradox_eclipse_sigil.png": make_paradox_eclipse_sigil(),
        "paradox_burst.png": make_paradox_burst(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "sunmoon_contact_sheet.png")


if __name__ == "__main__":
    main()

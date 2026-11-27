#!/usr/bin/env python3
"""
Dedicated DNF-inspired Soul Bender (鬼泣 / 弑魂) pixel-sharp + soft-feathered VFX texture generator.

Outputs rich pixel-art + soft-feathered RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/soulbender/
and a labeled dark-backdrop contact sheet preview under:
  temp/vfx-preview/soulbender_contact_sheet.png

Color direction: Classic DNF Remastered Dark-Violet Netherworld
  - Deep abyssal indigo-black shadow rims (strong daytime readability under Blend.TRANSLUCENT)
  - Vivid spectral violet / nether purple plasma bodies
  - Eerie pale ghost-cyan & white-hot core highlights (radiant pop under Blend.ADDITIVE)
  - Neutral luminance-ramped Ghost Field sigils so Kazan(red)/Bremen(green)/Saya(ice)/Rhasa(violet)
    each tint crisply with authentic DNF identity.
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/soulbender"
SKILL_ICON_OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/gui/skills"
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


def quad_bezier(p0: tuple[float, float], p1: tuple[float, float], p2: tuple[float, float],
                t: float) -> tuple[float, float]:
    u = 1.0 - t
    return (
        u * u * p0[0] + 2.0 * u * t * p1[0] + t * t * p2[0],
        u * u * p0[1] + 2.0 * u * t * p1[1] + t * t * p2[1],
    )


# --- Classic DNF Remastered Soul Bender Color Ramps --------------------------

# Primary netherworld dark-violet + eerie ghost-cyan core ramp
SOUL_RAMP = (
    (0.00, (10, 4, 22)),     # abyssal pitch-indigo ink
    (0.18, (32, 11, 64)),    # deep shadowed purple
    (0.40, (86, 28, 158)),   # classic DNF nether violet
    (0.64, (146, 68, 236)),  # vivid phantom purple plasma
    (0.82, (190, 148, 255)), # luminous ghost-lavender rim
    (0.93, (176, 232, 255)), # eerie pale ghost-cyan flare
    (1.00, (242, 250, 255)), # white-hot spectral razor core
)

# Dark-flame Kalla ramp: heavier smoky black-purple edge, roaring nether fire core
KALLA_RAMP = (
    (0.00, (8, 3, 16)),      # charred abyssal soot
    (0.20, (28, 8, 52)),     # dark nether coal
    (0.42, (92, 20, 162)),   # deep Kalla violet flame
    (0.66, (158, 52, 245)),  # blazing amethyst ghost-fire
    (0.84, (216, 134, 255)), # incandescent magenta-lavender
    (0.94, (175, 238, 255)), # pale phantom cyan tongue
    (1.00, (246, 252, 255)), # blinding nether heart
)

# Weathered ancient dark-stone + glowing violet ghost-rune ramp (Tombstone)
STONE_RAMP = (
    (0.00, (14, 11, 22)),    # deep carved shadow crack
    (0.22, (36, 30, 48)),    # weathered obsidian-slate stone
    (0.42, (66, 58, 84)),    # ancient gray-purple tombstone slab
    (0.58, (104, 94, 126)),  # chipped stone bevel highlight
    (0.74, (142, 72, 236)),  # glowing cursed rune violet
    (0.90, (198, 170, 255)), # radiant rune script
    (1.00, (210, 248, 255)), # spectral cyan rune flash
)

# Individual DNF Ghost Field spirit palettes (Kazan / Bremen / Saya / Rhasa)
KAZAN_RAMP = (
    (0.00, (24, 4, 10)),
    (0.25, (92, 12, 24)),
    (0.52, (192, 30, 46)),
    (0.76, (246, 86, 78)),
    (0.92, (255, 182, 148)),
    (1.00, (255, 244, 230)),
)

BREMEN_RAMP = (
    (0.00, (6, 20, 16)),
    (0.25, (18, 68, 46)),
    (0.52, (46, 152, 92)),
    (0.76, (102, 226, 142)),
    (0.92, (188, 255, 204)),
    (1.00, (238, 255, 244)),
)

SAYA_RAMP = (
    (0.00, (8, 18, 36)),
    (0.25, (24, 68, 124)),
    (0.52, (68, 158, 226)),
    (0.76, (132, 224, 255)),
    (0.92, (206, 246, 255)),
    (1.00, (246, 253, 255)),
)

RHASA_RAMP = (
    (0.00, (14, 5, 24)),
    (0.25, (56, 18, 96)),
    (0.52, (128, 48, 198)),
    (0.76, (184, 104, 248)),
    (0.92, (226, 186, 255)),
    (1.00, (248, 240, 255)),
)

# Abyssal Blache swamp / beast ramp (heavier ink-black abyss + pale ghost-cyan eyes/fangs)
BLACHE_RAMP = (
    (0.00, (6, 2, 14)),      # bottomless abyss void
    (0.18, (22, 8, 46)),     # deep demonic scale shadow
    (0.38, (58, 20, 112)),   # dark phantom hide
    (0.58, (112, 42, 192)),  # pulsing abyssal violet ridge
    (0.76, (166, 98, 242)),  # spectral aura crest
    (0.90, (144, 236, 255)), # piercing ghost-cyan eye / fang glow
    (1.00, (236, 252, 255)), # bone-white / cyan flash
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


def pack_ramp_pixel(heat: float, core_a: float, glow_heat: float = 0.50, glow_a: float = 0.0,
                    ramp=SOUL_RAMP) -> tuple[int, int, int, int]:
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


def pack_tint_pixel(heat: float, core_a: float, glow_heat: float = 0.60,
                    glow_a: float = 0.0) -> tuple[int, int, int, int]:
    """Neutral luminance-ramped pixel for runtime multi-color tinting (4-color Ghost Fields)."""
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (255, 255, 255, 0)
    c_lum = lerp(108.0, 255.0, clamp(heat) ** 0.85)
    g_lum = lerp(108.0, 255.0, clamp(glow_heat) ** 0.85)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    v = int(round(clamp(c_lum * wc + g_lum * wg, 0, 255)))
    return (v, v, v, int(round(clamp(out_a) * 255)))


# --- 1. soul_slash.png (1024x128, 8 frames of 128x128) -----------------------

def make_soul_slash() -> Image.Image:
    """
    DNF Keiga dark-sword & Kalla finisher sweeping dark-violet ghostly flame crescent.
    Deep pitch-indigo trailing shadow -> vivid nether violet body -> eerie pale-cyan razor edge
    with curling phantom flame tongues licking off the outer curve.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Sweep smoothly across [-2.15 .. +2.15] centered cleanly around +X (0 rad) with zero +-pi wrap artifacts
        head_ang = -1.95 + 4.10 * (1.0 - (1.0 - clamp(t / 0.56)) ** 2.1) if t < 0.56 else 2.15 + 0.28 * ((t - 0.56) / 0.44)
        tail_ang = -2.25 + 4.35 * smoothstep(0.18, 1.0, t)
        env = smoothstep(0.0, 0.12, t + 0.12) * (1.0 - 0.65 * smoothstep(0.64, 1.0, t))
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
                n = fbm(qx * 5.6, qy * 5.6, f * 23 + 11, 3)

                core_a = 0.0
                core_h = 0.12

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.88, arc_u)
                    flame_lick = (0.095 * math.sin(ang * 10.0 - f * 0.9) + 0.06 * (n - 0.46)) * arc_u
                    thick = (0.12 + 0.35 * (arc_u ** 0.64) * (1.0 - 0.20 * arc_u)) * (0.80 + 0.24 * env)
                    center_r = 0.66 - 0.06 * (1.0 - arc_u) + 0.04 * t
                    outer_r = min(0.96, center_r + thick * 0.50 + flame_lick)
                    inner_r = max(0.15, center_r - thick * 0.68 - 0.06 * (n - 0.45))

                    if inner_r <= r <= outer_r:
                        ru = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        # Ghostly shredded smoky trailing tail
                        wisp = 1.0 if (arc_u > 0.36 or n > 0.46 - arc_u * 0.9) else 0.22
                        if t > 0.64 and n < (t - 0.56) * 1.45:
                            wisp *= 0.18
                        grooves = 0.74 + 0.26 * math.sin(ru * math.pi * 5.0 + n * 3.5)
                        core_a = clamp(end_fade * env * wisp * smoothstep(0.0, 0.18, ru) * smoothstep(1.0, 0.86, ru) * grooves * 1.38)
                        # Heavy abyssal ink outer edge -> bright violet body -> eerie cyan-white leading edge
                        razor = smoothstep(0.62, 0.94, ru) * (0.45 + 0.55 * arc_u)
                        tip_flash = smoothstep(0.76, 0.97, arc_u) * smoothstep(0.50, 0.95, ru) * 0.32
                        dark_rim = smoothstep(0.88, 1.0, ru) * 0.22
                        core_h = clamp(0.08 + 0.46 * ru + 0.48 * razor + tip_flash - dark_rim)

                    # Secondary ghostly dark-flame echo ribbon inside the arc
                    echo_r = center_r - thick * 0.80
                    if echo_r > 0.16 and arc_u > 0.18:
                        de = abs(r - echo_r)
                        if de < 0.032:
                            ea = (1.0 - de / 0.032) * end_fade * env * 0.70 * smoothstep(0.18, 0.55, arc_u)
                            if ea > core_a:
                                core_a = ea
                                core_h = 0.76

                # Trailing spectral embers & dark flame flecks
                if 0.34 < r < 0.97 and tail_ang - 0.35 < ang < head_ang + 0.20:
                    sp = hash2(x // 2, y // 2, f * 47 + 19)
                    if sp > 0.968:
                        core_a = max(core_a, 0.92 * env)
                        core_h = max(core_h, 0.94 if sp > 0.986 else 0.32)

                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.66 - 0.06 * (1.0 - cu) + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.20) ** 2) * math.exp(-(ang_d / 0.21) ** 2) * 0.60 * env * smoothstep(0.99, 0.86, rr)
                px[f * size + x, y] = pack_ramp_pixel(core_h, core_a, 0.62 + 0.24 * cu, glow_a, ramp=SOUL_RAMP)

    return img


# --- 2. ghost_field_sigil.png (256x256, tintable outer occult seal) ----------

def make_ghost_field_sigil() -> Image.Image:
    """
    Ornate DNF Ghost Field outer demonic rune seal (tintable for Kazan red / Bremen green / Saya ice / Rhasa violet):
    Concentric jagged spirit bands, occult script glyphs, 12 thorny outer ward-spikes, and 6-point geometry.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    hex_segs = []
    for phase in (0.0, math.pi / 3.0):
        pts = [(math.cos(phase + i * math.tau / 3.0) * 0.72, math.sin(phase + i * math.tau / 3.0) * 0.72) for i in range(3)]
        for i in range(3):
            hex_segs.append((pts[i], pts[(i + 1) % 3]))

    spike_segs = []
    for i in range(12):
        a = i * math.tau / 12.0
        tip_r = 0.96 if i % 2 == 0 else 0.89
        a_tip = a + 0.09
        spike_segs.append(((math.cos(a) * 0.78, math.sin(a) * 0.78), (math.cos(a_tip) * tip_r, math.sin(a_tip) * tip_r)))
        spike_segs.append(((math.cos(a + 0.18) * 0.78, math.sin(a + 0.18) * 0.78), (math.cos(a_tip) * tip_r, math.sin(a_tip) * tip_r)))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)
            n = fbm(qx * 5.0, qy * 5.0, 223, 2)

            core_a = 0.0
            core_h = 0.20

            # Triple concentric occult rings with dark outline contrast
            for rad, thick, heat in ((0.81, 0.024, 0.86), (0.72, 0.017, 0.72), (0.50, 0.016, 0.78)):
                wob = 0.008 * math.sin(qa * 12.0) + 0.007 * (n - 0.5)
                d = abs(qr - (rad + wob))
                if d < thick * 1.7:
                    if d < thick:
                        v = 1.0 - smoothstep(thick * 0.35, thick, d)
                        core_a = max(core_a, v)
                        core_h = max(core_h, heat + 0.14 * v)
                    else:
                        oa = (1.0 - (d - thick) / (thick * 0.7)) * 0.70
                        if oa > core_a:
                            core_a = oa
                            core_h = 0.12

            # Dense arcane ghost-script band between 0.735 and 0.795
            if 0.735 < qr < 0.795:
                sec = ((qa + math.pi) / math.tau) * 28.0
                frac = sec - math.floor(sec)
                idx = int(math.floor(sec))
                row = int((qr - 0.735) / 0.06 * 4.0)
                if 0.18 < frac < 0.82 and hash2(idx, row, 511) > 0.35:
                    core_a = max(core_a, 0.90)
                    core_h = max(core_h, 0.88)

            # Intersecting hexagram lines
            for a, b in hex_segs:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                if d < 0.018:
                    v = 1.0 - smoothstep(0.006, 0.018, d)
                    core_a = max(core_a, v * 0.88)
                    core_h = max(core_h, 0.62 + 0.34 * v)

            # 12 outer thorny ghost-fangs pointing outward
            for a, b in spike_segs:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w = 0.022 * (1.0 - 0.62 * proj)
                if d < w and w > 0.004:
                    v = 1.0 - d / w
                    core_a = max(core_a, clamp(v * 1.45))
                    core_h = max(core_h, clamp(0.32 + 0.56 * proj + 0.16 * v))

            # Subtle swirling spirit floor haze inside ring
            if rr < 0.80:
                swirl = 0.5 + 0.5 * math.sin(qa * 4.0 + rr * 9.0 + n * 3.0)
                haze = (0.14 + 0.14 * swirl * n) * smoothstep(0.80, 0.32, rr)
                if haze > core_a:
                    core_a = haze
                    core_h = 0.24

            glow_a = (
                math.exp(-((rr - 0.81) / 0.065) ** 2) * 0.46
                + math.exp(-((rr - 0.50) / 0.060) ** 2) * 0.28
            ) * smoothstep(0.99, 0.90, rr)
            px[x, y] = pack_tint_pixel(core_h, core_a, 0.74, clamp(glow_a))

    return img


# --- 3. ghost_field_inner.png (256x256, tintable counter-rotating inner seal)

def make_ghost_field_inner() -> Image.Image:
    """
    Distinct inner counter-rotating DNF ghost mandala (ice-crystal / plague-vortex spokes,
    4 cardinal ghost anchor crests, and abyssal eye center) so stacked fields stay readable & lush.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    spokes = []
    for i in range(8):
        a = i * math.tau / 8.0
        r_out = 0.88 if i % 2 == 0 else 0.72
        spokes.append(((math.cos(a) * 0.24, math.sin(a) * 0.24), (math.cos(a) * r_out, math.sin(a) * r_out), 0.022 if i % 2 == 0 else 0.015))
        # Crystalline / thorny cross-barbs along cardinal spokes
        if i % 2 == 0:
            for rb in (0.48, 0.68):
                cx, cy = math.cos(a) * rb, math.sin(a) * rb
                for side in (-1.0, 1.0):
                    ba = a + side * 0.72
                    spokes.append(((cx, cy), (cx + math.cos(ba) * 0.14, cy + math.sin(ba) * 0.14), 0.014))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 128) - 0.5) * 2.0
            qy = (quantize(y / size, 128) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)
            n = fbm(qx * 6.0, qy * 6.0, 337, 2)

            core_a = 0.0
            core_h = 0.24

            for rad, thick, heat in ((0.64, 0.019, 0.82), (0.34, 0.020, 0.88), (0.18, 0.015, 0.94)):
                d = abs(qr - rad)
                if d < thick:
                    v = 1.0 - smoothstep(thick * 0.3, thick, d)
                    core_a = max(core_a, v * 0.95)
                    core_h = max(core_h, heat)

            # 4 cardinal ghost-anchor diamonds at r=0.64
            for i in range(4):
                a = i * math.pi * 0.5 + math.pi * 0.25
                cx, cy = math.cos(a) * 0.64, math.sin(a) * 0.64
                dia = abs(qx - cx) + abs(qy - cy)
                if dia < 0.13:
                    v = 1.0 - smoothstep(0.04, 0.13, dia)
                    ring = 1.0 - smoothstep(0.012, 0.032, abs(dia - 0.095))
                    core_a = max(core_a, max(v * 0.82, ring))
                    core_h = max(core_h, 0.92 if ring > 0.4 else 0.55)

            for a, b, wb in spokes:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w = wb * (1.0 - 0.45 * proj)
                if d < w and w > 0.004:
                    v = 1.0 - d / w
                    core_a = max(core_a, clamp(v * 1.4))
                    core_h = max(core_h, clamp(0.45 + 0.50 * v))

            # Swirling phantom vortex ribbons between 0.22 and 0.62
            if 0.22 < qr < 0.62:
                spiral = math.sin(qa * 4.0 - qr * 12.0 + n * 2.2)
                if spiral > 0.72:
                    sa = smoothstep(0.72, 0.96, spiral) * smoothstep(0.62, 0.46, qr) * smoothstep(0.22, 0.34, qr) * 0.72
                    if sa > core_a:
                        core_a = sa
                        core_h = 0.72

            # Central demonic eye crest
            eye_d = math.hypot(qx * 0.85, qy * 1.65)
            if eye_d < 0.13:
                core_a = max(core_a, 1.0 - smoothstep(0.08, 0.13, eye_d))
                core_h = max(core_h, 0.18 if abs(qx) < 0.028 else 0.94)

            glow_a = (
                math.exp(-((rr - 0.64) / 0.07) ** 2) * 0.38
                + math.exp(-(rr / 0.24) ** 2) * 0.36
            ) * smoothstep(0.98, 0.84, rr)
            px[x, y] = pack_tint_pixel(core_h, core_a, 0.76, clamp(glow_a))

    return img


# --- 4. ghost_spirits.png (512x128, 4 frames of 128x128: Kazan/Bremen/Saya/Rhasa)

def _draw_kazan(px, ox: int, size: int) -> None:
    """Frame 0: 刀魂之卡赞 (Kazan) — Towering horned crimson berserker ghost silhouette."""
    horns = []
    for sign in (-1.0, 1.0):
        p0, p1, p2 = (sign * 0.18, -0.44), (sign * 0.48, -0.56), (sign * 0.34, -0.90)
        prev = p0
        for s in range(1, 7):
            u = s / 6.0
            cur = quad_bezier(p0, p1, p2, u)
            horns.append((prev, cur, 0.085 * (1.0 - 0.72 * u)))
            prev = cur
    # Broad demonic clawed arms flaring outward
    arms = []
    for sign in (-1.0, 1.0):
        arms.append(((sign * 0.24, -0.14), (sign * 0.56, -0.02), 0.15))
        arms.append(((sign * 0.56, -0.02), (sign * 0.72, 0.24), 0.11))
        for k, ang_o in enumerate((-0.22, 0.0, 0.22)):
            arms.append(((sign * 0.72, 0.24), (sign * (0.82 + 0.04 * (k == 1)), 0.42 + ang_o * 0.35), 0.036))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            n = fbm(qx * 5.0, qy * 5.0, 101, 3)

            ca, ch, md = 0.0, 0.18, 9.9
            # Muscular upper torso tapering into fiery spectral tail at bottom (+qy)
            if -0.26 < qy < 0.88:
                u_v = (qy + 0.26) / 1.14
                sway = 0.08 * math.sin(qy * 7.0)
                tw = (0.44 * (1.0 - 0.65 * (u_v ** 1.15)) + 0.08 * math.sin(u_v * math.pi)) + (n - 0.5) * 0.12
                dx = abs(qx - sway)
                md = min(md, max(0.0, dx - tw))
                if dx < tw:
                    in_t = 1.0 - dx / max(0.04, tw)
                    bf = smoothstep(0.88, 0.54, qy + (n - 0.5) * 0.18)
                    a = clamp(smoothstep(0.0, 0.28, in_t) * bf * 1.12)
                    ca = max(ca, a)
                    ch = max(ch, clamp(0.14 + 0.52 * in_t + 0.20 * (1.0 - u_v)))
            # Broad grim skull/head
            dh = math.hypot(qx * 1.15, (qy + 0.36) * 1.05)
            rh = 0.25 + 0.03 * (n - 0.5)
            md = min(md, max(0.0, dh - rh))
            if dh < rh:
                in_h = 1.0 - dh / rh
                ca = max(ca, smoothstep(0.0, 0.26, in_h))
                ch = max(ch, clamp(0.16 + 0.48 * in_h))
            for a, b, rad in horns + arms:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                md = min(md, max(0.0, d - rad))
                if d < rad:
                    in_s = 1.0 - d / max(0.01, rad)
                    ca = max(ca, clamp(smoothstep(0.0, 0.28, in_s) * 1.15))
                    ch = max(ch, clamp(0.22 + 0.62 * in_s + 0.16 * proj))
            # Fierce blazing eyes + jagged battle-aura roar mouth
            for sign in (-1.0, 1.0):
                ex = (qx - sign * 0.095) + (qy + 0.37) * sign * 0.35
                ey = (qy + 0.37) * 2.4
                if math.hypot(ex, ey) < 0.075:
                    ca, ch = 1.0, 0.98
            if abs(qx) < 0.12 and abs(qy + 0.24) < 0.035:
                ca, ch = 1.0, 0.90
            glow_a = math.exp(-(md / 0.20) ** 2) * 0.56 * smoothstep(0.98, 0.80, max(abs(rx), abs(ry)))
            px[ox + x, y] = pack_ramp_pixel(ch, ca, 0.65, glow_a, ramp=KAZAN_RAMP)


def _draw_bremen(px, ox: int, size: int) -> None:
    """Frame 1: 侵蚀之普戾蒙 (Bremen) — Sinister hunched hooded spectral reaper rising from corrosive jade fog."""
    arms = []
    for sign in (-1.0, 1.0):
        p0, p1, p2 = (sign * 0.18, -0.06), (sign * 0.50, 0.04), (sign * 0.78, -0.18)
        prev = p0
        for s in range(1, 7):
            u = s / 6.0
            cur = quad_bezier(p0, p1, p2, u)
            arms.append((prev, cur, 0.105 * (1.0 - 0.55 * u)))
            prev = cur
        # Gaunt gnarled claws off each spectral hand
        for k, dy in enumerate((-0.10, 0.0, 0.10)):
            arms.append(((sign * 0.78, -0.18), (sign * 0.90, -0.26 + dy), 0.028))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            n = fbm(qx * 5.2, qy * 5.2, 202, 3)

            ca, ch, md = 0.0, 0.14, 9.9
            # Tall pointed sinister wraith cowl & tattered wispy robes dissolving into jade mist
            if -0.88 < qy < 0.90:
                u_v = (qy + 0.88) / 1.78
                # Sharp peaked hood tapering at top, hunched wide shoulders, tattered fluttering shreds at bottom
                hood_peak = smoothstep(0.0, 0.24, u_v)
                shoulder_w = 0.44 * hood_peak * (1.0 - 0.18 * (u_v ** 1.6))
                shred = (0.14 * math.sin(qx * 15.0 + n * 4.0) + (n - 0.48) * 0.20) * smoothstep(0.42, 1.0, u_v)
                sway = 0.06 * math.sin(qy * 6.5)
                dx = abs(qx - sway)
                md = min(md, max(0.0, dx - shoulder_w))
                if dx < shoulder_w + shred * 0.35:
                    in_c = 1.0 - dx / max(0.04, shoulder_w + shred * 0.35)
                    bf = smoothstep(0.90, 0.52, qy + shred)
                    ca = max(ca, clamp(smoothstep(0.0, 0.26, in_c) * bf * 1.12))
                    # Pitch-dark hollow hood cavity framing the menacing face
                    face_d = math.hypot(qx * 1.30, (qy + 0.36) * 1.05)
                    if face_d < 0.24:
                        ch = max(ch, 0.04 + 0.12 * (face_d / 0.24))
                    else:
                        rim = smoothstep(0.35, 0.05, in_c) * 0.38
                        ch = max(ch, clamp(0.14 + 0.44 * in_c + rim + 0.16 * (1.0 - u_v)))
            for a, b, rad in arms:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                md = min(md, max(0.0, d - rad))
                if d < rad:
                    in_s = 1.0 - d / max(0.008, rad)
                    ca = max(ca, clamp(smoothstep(0.0, 0.26, in_s) * 1.12))
                    ch = max(ch, clamp(0.22 + 0.64 * in_s))
            # Piercing slanted ghostly emerald eyes & jagged shadowy jaw slit inside dark cowl
            for sign in (-1.0, 1.0):
                ex = (qx - sign * 0.085) + (qy + 0.38) * sign * 0.42
                ey = (qy + 0.38) * 2.5
                if math.hypot(ex, ey) < 0.068:
                    ca, ch = 1.0, 0.99
            if abs(qx) < 0.11 and abs(qy + 0.22) < 0.036:
                if abs(math.sin(qx * 36.0)) > 0.35:
                    ca, ch = 0.95, 0.88
            # Swirling toxic corrosive mist wisps rising from bottom
            for ox_b, oy_b, r_b in ((-0.54, 0.38, 0.10), (0.52, 0.30, 0.11), (-0.34, 0.66, 0.09), (0.38, 0.60, 0.09)):
                db = math.hypot(qx - ox_b, qy - oy_b)
                if db < r_b:
                    ca = max(ca, (1.0 - db / r_b) * 0.85)
                    ch = max(ch, 0.78)
            glow_a = math.exp(-(md / 0.22) ** 2) * 0.56 * smoothstep(0.98, 0.80, max(abs(rx), abs(ry)))
            px[ox + x, y] = pack_ramp_pixel(ch, ca, 0.66, glow_a, ramp=BREMEN_RAMP)


def _draw_saya(px, ox: int, size: int) -> None:
    """Frame 2: 冰霜之萨亚 (Saya) — Regal ethereal frozen phantom princess with ice crown & flowing hair."""
    ribbons = []
    # 5-point crystalline frost crown spires above head
    for ang_o, h_c in ((-0.34, 0.18), (-0.17, 0.25), (0.0, 0.33), (0.17, 0.25), (0.34, 0.18)):
        ribbons.append(((ang_o * 0.42, -0.56), (ang_o * 0.72, -0.56 - h_c), 0.036))
    # Flowing icy hair & long regal spectral sleeves sweeping dramatically left & right
    for sign in (-1.0, 1.0):
        for p0, p1, p2, bw in (
            ((sign * 0.14, -0.44), (sign * 0.46, -0.22), (sign * 0.62, 0.38), 0.10),
            ((sign * 0.20, -0.10), (sign * 0.56, 0.06), (sign * 0.78, 0.56), 0.11),
        ):
            prev = p0
            for s in range(1, 7):
                u = s / 6.0
                cur = quad_bezier(p0, p1, p2, u)
                ribbons.append((prev, cur, bw * (1.0 - 0.58 * u)))
                prev = cur

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            n = fbm(qx * 5.0, qy * 5.0, 303, 3)

            ca, ch, md = 0.0, 0.22, 9.9
            # Graceful flaring ice-gown silhouette
            if -0.26 < qy < 0.90:
                u_v = (qy + 0.26) / 1.16
                gw = (0.18 + 0.30 * (u_v ** 0.9) - 0.14 * (u_v ** 2.4)) + 0.06 * math.sin(qy * 10.0)
                dx = abs(qx)
                md = min(md, max(0.0, dx - gw))
                if dx < gw:
                    in_g = 1.0 - dx / max(0.04, gw)
                    bf = smoothstep(0.90, 0.58, qy + (n - 0.5) * 0.16)
                    ca = max(ca, clamp(smoothstep(0.0, 0.26, in_g) * bf * 1.12))
                    ch = max(ch, clamp(0.24 + 0.56 * in_g + 0.16 * (1.0 - u_v)))
            # Delicate oval head
            dh = math.hypot(qx * 1.25, (qy + 0.42) * 1.05)
            if dh < 0.20:
                in_h = 1.0 - dh / 0.20
                md = min(md, 0.0)
                ca = max(ca, smoothstep(0.0, 0.25, in_h))
                ch = max(ch, clamp(0.36 + 0.54 * in_h))
            for a, b, rad in ribbons:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                md = min(md, max(0.0, d - rad))
                if d < rad:
                    in_s = 1.0 - d / max(0.008, rad)
                    ca = max(ca, clamp(smoothstep(0.0, 0.28, in_s) * 1.15))
                    ch = max(ch, clamp(0.36 + 0.58 * in_s))
            # Cool glowing frost eyes
            for sign in (-1.0, 1.0):
                if math.hypot((qx - sign * 0.065) * 1.4, (qy + 0.43) * 2.2) < 0.045:
                    ca, ch = 1.0, 1.0
            # Orbiting frost diamond crystals
            for cx, cy, cr in ((-0.64, -0.44, 0.10), (0.64, -0.42, 0.10), (-0.76, 0.08, 0.09), (0.76, 0.06, 0.09)):
                dia = abs(qx - cx) * 1.2 + abs(qy - cy) * 0.8
                if dia < cr:
                    ca = max(ca, (1.0 - dia / cr) * 1.1)
                    ch = max(ch, 0.94)
            glow_a = math.exp(-(md / 0.20) ** 2) * 0.58 * smoothstep(0.98, 0.80, max(abs(rx), abs(ry)))
            px[ox + x, y] = pack_ramp_pixel(ch, ca, 0.72, glow_a, ramp=SAYA_RAMP)


def _draw_rhasa(px, ox: int, size: int) -> None:
    """Frame 3: 瘟疫之罗刹 (Rhasa) — Hunched curving plague-oni fiend with sweeping dark horns & clinging wisps."""
    curves = []
    # Curved demonic plague horns arcing cleanly backward & up
    for sign in (-1.0, 1.0):
        for p0, p1, p2, bw in (
            ((sign * 0.18, -0.36), (sign * 0.44, -0.58), (sign * 0.36, -0.86), 0.088),
            ((sign * 0.28, -0.18), (sign * 0.62, -0.26), (sign * 0.78, -0.50), 0.070),
            # Curved hunched claws reaching forward-downward
            ((sign * 0.26, -0.02), (sign * 0.60, 0.06), (sign * 0.54, 0.42), 0.115),
        ):
            prev = p0
            for s in range(1, 7):
                u = s / 6.0
                cur = quad_bezier(p0, p1, p2, u)
                curves.append((prev, cur, bw * (1.0 - 0.62 * u)))
                prev = cur

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0
            n = fbm(qx * 5.6, qy * 5.6, 404, 3)

            ca, ch, md = 0.0, 0.12, 9.9
            # Organic hunched plague-beast torso & swirling miasma lower body
            if -0.46 < qy < 0.88:
                u_v = (qy + 0.46) / 1.34
                body_w = (0.48 * math.sin(min(1.0, u_v * 1.15) * math.pi * 0.75) + 0.08 * (1.0 - u_v)) * (1.0 - 0.42 * (u_v ** 1.4)) + (n - 0.46) * 0.16
                dx = abs(qx - 0.06 * math.sin(qy * 7.5))
                md = min(md, max(0.0, dx - body_w))
                if dx < body_w:
                    in_b = 1.0 - dx / max(0.04, body_w)
                    bf = smoothstep(0.88, 0.54, qy + (n - 0.5) * 0.18)
                    ca = max(ca, clamp(smoothstep(0.0, 0.26, in_b) * bf * 1.14))
                    ch = max(ch, clamp(0.10 + 0.48 * in_b + 0.18 * smoothstep(0.4, 0.05, in_b)))
            for a, b, rad in curves:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                re = rad + 0.015 * (n - 0.5)
                md = min(md, max(0.0, d - re))
                if d < re and re > 0.008:
                    in_s = 1.0 - d / re
                    ca = max(ca, clamp(smoothstep(0.0, 0.28, in_s) * 1.15))
                    ch = max(ch, clamp(0.18 + 0.62 * in_s))
            # Sinister slanted glowing violet-white plague eyes & grinning fanged mouth
            for sign in (-1.0, 1.0):
                ex = (qx - sign * 0.12) + (qy + 0.20) * sign * 0.40
                ey = (qy + 0.20) * 2.4
                if math.hypot(ex, ey) < 0.072:
                    ca, ch = 1.0, 0.99
            # Curved fanged grin
            grin_y = -0.04 + 0.25 * (qx * qx)
            if abs(qx) < 0.18 and abs(qy - grin_y) < 0.042:
                if abs(math.sin(qx * 34.0)) > 0.30:
                    ca, ch = 1.0, 0.90
            # Orbiting organic plague-miasma orbs
            for cx, cy in ((-0.68, -0.02), (0.68, -0.04), (-0.54, 0.58), (0.56, 0.54)):
                dw = math.hypot(qx - cx, qy - cy)
                if dw < 0.11:
                    ca = max(ca, (1.0 - dw / 0.11) * 0.96)
                    ch = max(ch, 0.84)
            glow_a = math.exp(-(md / 0.21) ** 2) * 0.58 * smoothstep(0.98, 0.80, max(abs(rx), abs(ry)))
            px[ox + x, y] = pack_ramp_pixel(ch, ca, 0.64, glow_a, ramp=RHASA_RAMP)


def make_ghost_spirits() -> Image.Image:
    size = 128
    img = Image.new("RGBA", (size * 4, size), (0, 0, 0, 0))
    px = img.load()
    _draw_kazan(px, 0 * size, size)
    _draw_bremen(px, 1 * size, size)
    _draw_saya(px, 2 * size, size)
    _draw_rhasa(px, 3 * size, size)
    return img


# --- 5. soul_devour_head.png (1024x128, 8 frames of 128x128) -----------------

def make_soul_devour_head() -> Image.Image:
    """
    DNF 噬灵鬼斩 (Soul Slash) lunging phantom oni-wolf demon head devouring forward (+X)!
      Frames 0-3: Solid sculpted dark-indigo beast cranium & roaring open fanged jaws with billowing spectral mane
      Frames 4-5: Brutal devouring bite crunching shut with blinding cyan-violet shockwave
      Frames 6-7: Dissolving into streaming nether flames
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        bite = smoothstep(0.36, 0.64, t)
        jaw_open = lerp(0.26, 0.01, bite)
        bite_flash = math.sin(clamp((t - 0.36) / 0.32) * math.pi) if 0.36 <= t <= 0.68 else 0.0
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.72 * smoothstep(0.66, 1.0, t))

        # Sculpted thick curved upper snout & arched brow
        upper_segs = [
            ((0.84, -0.06 - jaw_open * 0.72), (0.54, -0.26 - jaw_open * 0.35), 0.14),
            ((0.54, -0.26 - jaw_open * 0.35), (0.14, -0.34), 0.20),
            ((0.14, -0.34), (-0.22, -0.14), 0.24),
            # Fierce hooked snout tip
            ((0.84, -0.06 - jaw_open * 0.72), (0.72, 0.04 - jaw_open * 0.52), 0.075),
        ]
        # Sculpted heavy lower jaw with demonic chin spike
        lower_segs = [
            ((0.72, 0.16 + jaw_open * 0.88), (0.36, 0.30 + jaw_open * 0.45), 0.125),
            ((0.36, 0.30 + jaw_open * 0.45), (-0.10, 0.22), 0.165),
            ((0.36, 0.30 + jaw_open * 0.45), (0.14, 0.46 + jaw_open * 0.35), 0.065),
        ]
        # Twin sweeping curved oni horns
        horns = []
        for p0, p1, p2, bw in (
            ((0.14, -0.34), (-0.18, -0.74), (-0.68, -0.78), 0.115),
            ((-0.02, -0.24), (-0.36, -0.52), (-0.82, -0.44), 0.085),
        ):
            prev = p0
            for s in range(1, 7):
                u = s / 6.0
                cur = quad_bezier(p0, p1, p2, u)
                horns.append((prev, cur, bw * (1.0 - 0.76 * u)))
                prev = cur

        fangs = [
            ((0.72, -0.02 - jaw_open * 0.62), (0.64, 0.16 - jaw_open * 0.30), 0.052),
            ((0.46, -0.10 - jaw_open * 0.40), (0.42, 0.09 - jaw_open * 0.12), 0.044),
            ((0.60, 0.16 + jaw_open * 0.78), (0.54, -0.02 + jaw_open * 0.40), 0.048),
            ((0.36, 0.22 + jaw_open * 0.55), (0.32, 0.04 + jaw_open * 0.26), 0.040),
        ]

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                n = fbm(qx * 4.8 - t * 4.2, qy * 4.8, 521, 3)
                n2 = fbm(qx * 8.5 - t * 6.0, qy * 8.5, 541, 2)

                ca, ch, md = 0.0, 0.10, 9.9
                dissolve = 1.0 if (t < 0.64 or n > (t - 0.54) * 1.45) else 0.16

                # Billowing organic ghostly flame mane trailing behind head (qx in -0.95..+0.08)
                if -0.96 < qx < 0.15:
                    u_x = clamp((0.15 - qx) / 1.11)  # 0 at skull -> 1 at flame tail tip
                    mane_h = (0.50 * (1.0 - 0.48 * u_x) + 0.18 * math.sin(qx * 9.0 - t * 6.0)) + (n - 0.46) * 0.34
                    flame_tongue = math.sin(qy * 11.0 - qx * 5.0 - t * 8.0)
                    if abs(qy) < mane_h:
                        in_m = 1.0 - abs(qy) / max(0.05, mane_h)
                        tail_taper = smoothstep(1.0, 0.28, u_x + (n2 - 0.45) * 0.32)
                        md = min(md, max(0.0, abs(qy) - mane_h))
                        a_m = clamp(smoothstep(0.0, 0.32, in_m) * tail_taper * env * dissolve * (0.76 + 0.34 * max(0.0, flame_tongue)))
                        if a_m > ca:
                            ca = a_m
                            ch = clamp(0.10 + 0.46 * in_m + 0.26 * (1.0 - u_x) + 0.18 * max(0.0, flame_tongue))

                # Heavy solid cranium core
                d_sk = math.hypot((qx - 0.06) * 1.05, (qy + 0.02) * 1.15)
                r_sk = 0.38 + 0.05 * (n - 0.5)
                md = min(md, max(0.0, d_sk - r_sk))
                if d_sk < r_sk:
                    in_s = 1.0 - d_sk / r_sk
                    ca = max(ca, smoothstep(0.0, 0.24, in_s) * env * dissolve * 1.15)
                    ch = max(ch, clamp(0.12 + 0.48 * in_s + 0.14 * (1.0 - in_s)))

                # Sculpted jaws & horns
                for group, base_h, hi_h in ((upper_segs, 0.14, 0.54), (lower_segs, 0.12, 0.48), (horns, 0.22, 0.62)):
                    for a, b, rad in group:
                        d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                        re = rad + 0.02 * (n - 0.5)
                        md = min(md, max(0.0, d - re))
                        if d < re and re > 0.008:
                            in_t = 1.0 - d / re
                            av = clamp(smoothstep(0.0, 0.26, in_t) * env * dissolve * 1.22)
                            hv = clamp(base_h + hi_h * in_t + 0.20 * smoothstep(0.55, 1.0, in_t))
                            if av >= ca * 0.85:
                                ca = max(ca, av)
                                ch = max(ch, hv)

                # Incandescent white-cyan fangs
                for a, b, rad in fangs:
                    d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    rf = rad * (1.0 - 0.72 * proj)
                    if d < rf and rf > 0.006:
                        in_t = 1.0 - d / rf
                        ca = max(ca, clamp(smoothstep(0.0, 0.22, in_t) * env * 1.35))
                        ch = max(ch, 0.88 + 0.12 * in_t)

                # Devouring energy vortex & crunch shock flash inside jaws
                mouth_d = math.hypot((qx - 0.54) * 1.15, (qy - 0.04) * 1.55)
                if mouth_d < 0.28 + 0.18 * bite_flash:
                    ma = (1.0 - mouth_d / (0.28 + 0.18 * bite_flash)) * env * (0.68 + 0.62 * bite_flash)
                    ca = max(ca, ma)
                    ch = max(ch, clamp(0.74 + 0.26 * bite_flash))

                # Fierce piercing cyan-white glowing demon eye slit
                ex = (qx - 0.28) + (qy + 0.16) * 0.45
                ey = -(qx - 0.28) * 0.45 + (qy + 0.16)
                if math.hypot(ex * 1.0, ey * 2.8) < 0.115:
                    ca = max(ca, env)
                    ch = 0.99

                glow_a = math.exp(-(md / 0.19) ** 2) * 0.56 * env * smoothstep(0.99, 0.84, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.66, glow_a, ramp=SOUL_RAMP)

    return img


# --- 6. tombstone_pillar.png (512x128, 8 frames of 64x128) -------------------

def make_tombstone_pillar() -> Image.Image:
    """
    DNF 死亡墓碑 (Tombstone) ancient chipped dark-stone gothic monolith with refined glowing violet runes!
      v=0 top chipped pointed gable, v=1 planted rough base.
      Frames 0-3: Falling & planted trembling stone monolith with pulsing carved violet ghost runes
      Frames 4-7: Fracturing along glowing purple fissures & shattering cleanly into stone shards + ghost dust (zero box halo!)
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    # Crisp etched demonic seal runes centered cleanly inside the stone face
    rune_segs = [
        ((0.0, 0.26), (0.0, 0.72), 0.032),
        ((-0.20, 0.34), (0.20, 0.34), 0.028),
        ((-0.15, 0.46), (0.17, 0.42), 0.026),
        ((-0.18, 0.57), (0.18, 0.60), 0.026),
        ((-0.13, 0.69), (0.13, 0.69), 0.026),
        ((-0.20, 0.34), (-0.13, 0.48), 0.024),
        ((0.20, 0.34), (0.14, 0.50), 0.024),
    ]
    crack_segs = [
        ((0.0, 0.48), (-0.58, 0.28), 0.032),
        ((0.0, 0.48), (0.56, 0.36), 0.032),
        ((0.0, 0.54), (-0.54, 0.76), 0.032),
        ((0.0, 0.54), (0.58, 0.70), 0.032),
    ]

    for f in range(frames):
        shatter = smoothstep(3.0, 7.0, float(f))
        pulse = 0.5 + 0.5 * math.sin(f * 1.4)
        fade = 1.0 - 0.92 * smoothstep(4.2, 7.0, float(f))

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = quantize(y / h, 64)
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = (y + 0.5) / h

                sx, sy = qx, qy
                if shatter > 0.01:
                    cell_x = math.floor((qx + 1.0) * 3.0)
                    cell_y = math.floor(qy * 5.0)
                    vx = (hash2(cell_x, cell_y, 701) - 0.5) * 0.85
                    vy = (hash2(cell_x, cell_y, 709) - 0.65) * 0.35
                    sx = qx - vx * shatter
                    sy = qy - vy * shatter

                n = fbm(sx * 6.0, sy * 8.0, 613, 3)
                # Asymmetric weathered gothic gravestone: jagged broken top slant + rough chipped edges
                top_edge = 0.12 + 0.19 * abs(sx + 0.08) + 0.05 * math.sin(sx * 8.0) + (n - 0.5) * 0.05
                chip = 0.08 * math.sin(sy * 20.0) + 0.07 * (n - 0.5)
                half_w = (0.56 + 0.10 * smoothstep(0.20, 0.86, sy)) * smoothstep(0.08, 0.24, sy) + chip
                if sy > 0.84:
                    half_w += 0.11 * smoothstep(0.84, 0.94, sy)

                core_a = 0.0
                core_h = 0.18
                in_slab = (sy >= top_edge) and (sy <= 0.95) and (abs(sx) <= half_w)

                if in_slab:
                    keep = 1.0
                    if shatter > 0.05:
                        gap = fbm(qx * 7.5, qy * 9.5, 811, 2)
                        if gap < shatter * 0.92:
                            keep = 0.0
                    if keep > 0.0:
                        edge_x = 1.0 - abs(sx) / max(0.05, half_w)
                        edge_top = clamp((sy - top_edge) / 0.10)
                        bevel = min(edge_x, edge_top)
                        core_a = clamp(smoothstep(0.0, 0.15, bevel) * fade * 1.15)
                        # Chiseled dark obsidian-slate shading with deep edge shadow & stone grain
                        lit = 0.36 + 0.16 * (-sx) + 0.16 * (n - 0.5)
                        if bevel < 0.22:
                            lit -= 0.20
                        # Inner recessed frame border carved into slab face
                        if 0.18 < edge_x < 0.25 and 0.26 < sy < 0.86:
                            lit -= 0.16
                        core_h = clamp(lit, 0.04, 0.58)

                        # Refined glowing violet runes etched inside stone face
                        for a, b, rw in rune_segs:
                            d, _ = seg_dist(sx, sy, a[0], a[1], b[0], b[1])
                            if d < rw:
                                iv = 1.0 - d / rw
                                core_h = max(core_h, clamp(0.72 + 0.26 * iv + 0.05 * pulse))

                # Glowing purple fracture cracks tearing open on frames 3..6
                if 3 <= f <= 6:
                    cf = smoothstep(2.5, 4.2, float(f)) * (1.0 - 0.78 * smoothstep(4.8, 6.8, float(f)))
                    for a, b, cw in crack_segs:
                        d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                        w_eff = cw * (1.0 + 0.5 * shatter)
                        if d < w_eff:
                            iv = 1.0 - d / max(0.01, w_eff)
                            core_a = max(core_a, clamp(iv * cf * 1.25))
                            core_h = max(core_h, clamp(0.78 + 0.22 * iv))

                # Flying rock & violet spark fragments on shatter frames 4..7
                if f >= 4 and 0.12 < qy < 0.94 and hash2((x + f * 5) // 2, (y - f * 3) // 2, 829) > 0.968:
                    core_a = max(core_a, 0.90 * fade)
                    core_h = 0.86 if hash2(x, y, f) > 0.5 else 0.38

                # Tight silhouette-hugging glow that cleanly vanishes as the stone shatters!
                g_hw = 0.54 * smoothstep(0.10, 0.26, ry)
                dx_g = max(0.0, abs(rx) - g_hw)
                dy_g = max(0.0, 0.14 - ry) + max(0.0, ry - 0.95)
                g_dist = math.hypot(dx_g, dy_g * 2.0)
                glow_a = math.exp(-(g_dist / 0.18) ** 2) * 0.36 * (1.0 - shatter) * fade * smoothstep(0.98, 0.82, abs(rx))
                px[f * w + x, y] = pack_ramp_pixel(core_h, core_a, 0.78, glow_a, ramp=STONE_RAMP)

    return img


# --- 7. phantom_rift.png (1024x128, 8 frames of 128x128) ---------------------

def make_phantom_rift() -> Image.Image:
    """
    DNF 鬼影闪 (Phantom Slash) delayed dimensional iaido cut rift!
      Frames 0-1: Razor-thin dark-violet & pale-cyan hairline cut freezing across space
      Frames 2-5: Violently ripping open into an abyssal pitch-black tear with jagged electric-violet edges
      Frames 6-7: Snapping shut into drifting dark-violet shards & sparks
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Hairline thin at f=0..1, bursts wide open at f=2..4, collapses f=5..7
        open_w = 0.045 if f == 0 else (0.09 if f == 1 else (0.34 * math.sin(clamp((t - 0.14) / 0.86) * math.pi) ** 0.75 + 0.04))
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.74 * smoothstep(0.68, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 6.5 - f * 0.4, qy * 6.5, 733, 3)
                # Tapered horizontal slash spanning qx in [-0.96..+0.96]
                span = max(0.0, 1.0 - (qx / 0.96) ** 2) ** 0.72
                jagged = 0.06 * math.sin(qx * 17.0 + f) + (n - 0.48) * 0.10
                local_y = qy - jagged * span
                half_h = open_w * span + 0.015 * span

                core_a = 0.0
                core_h = 0.06

                if abs(local_y) < half_h + 0.08 and span > 0.02:
                    d = abs(local_y)
                    if d < half_h:
                        # Pitch-black abyssal void interior -> blazing pale-cyan/violet torn rim!
                        rim_u = d / max(0.012, half_h)
                        dissolve = 1.0 if (f < 5 or n > (t - 0.55) * 1.4) else 0.18
                        core_a = clamp(smoothstep(0.02, 0.18, span) * env * dissolve * 1.25)
                        if f <= 1:
                            # Early hairline flash is pure blinding pale-cyan/violet blade gleam!
                            core_h = clamp(0.82 + 0.18 * (1.0 - rim_u))
                        else:
                            # Torn wide open: dark abyssal core -> bright razor rim!
                            core_h = clamp(0.03 + 0.95 * smoothstep(0.42, 0.96, rim_u))
                    else:
                        # Jagged outer violet crackle rim
                        out_u = 1.0 - (d - half_h) / 0.08
                        if out_u > 0.0:
                            ca_o = smoothstep(0.0, 0.65, out_u) * span * env * 0.85
                            if ca_o > core_a:
                                core_a = ca_o
                                core_h = clamp(0.55 + 0.40 * out_u)

                # Secondary razor diagonal slash-sparks ripping off on burst frames 2..5
                if 2 <= f <= 5 and abs(qx) < 0.88 and abs(qy) < 0.75:
                    sp = hash2(x // 2, y // 2, f * 59 + 13)
                    if sp > 0.972 and abs(local_y) < half_h + 0.32:
                        core_a = max(core_a, 0.94 * env)
                        core_h = 0.96 if sp > 0.986 else 0.25

                r_span = max(0.0, 1.0 - (rx / 0.98) ** 2)
                glow_a = math.exp(-(ry / (open_w + 0.18)) ** 2) * r_span * 0.64 * env * smoothstep(0.99, 0.86, abs(rx))
                px[f * size + x, y] = pack_ramp_pixel(core_h, core_a, 0.72, glow_a, ramp=SOUL_RAMP)

    return img


# --- 8. ghost_flash.png (512x64, 8 frames of 64x64) --------------------------

def make_ghost_flash() -> Image.Image:
    """Asymmetrical DNF Phantom Slash / Ghost Slash 4-point spectral iaido star-glint & shock diamond."""
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        flash = math.sin(clamp(t * 0.86 + 0.08) * math.pi) ** 0.65
        h_len = 0.45 + 0.52 * smoothstep(0.0, 0.34, t + 0.12)
        v_len = 0.24 + 0.46 * smoothstep(0.0, 0.38, t + 0.10)
        thick = 0.12 * (1.0 - 0.60 * t) + 0.028

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr, rr = math.hypot(qx, qy), math.hypot(rx, ry)
                ca, ch = 0.0, 0.55

                if abs(qx) < h_len:
                    hw = thick * (1.0 - abs(qx) / max(0.05, h_len)) ** 1.4
                    if abs(qy) < hw:
                        v = (1.0 - abs(qy) / max(0.01, hw)) * flash
                        ca = max(ca, clamp(v * 1.5))
                        ch = max(ch, clamp(0.55 + 0.45 * v))
                if abs(qy) < v_len:
                    vw = thick * 0.82 * (1.0 - abs(qy) / max(0.05, v_len)) ** 1.4
                    if abs(qx) < vw:
                        v = (1.0 - abs(qx) / max(0.01, vw)) * flash
                        ca = max(ca, clamp(v * 1.4))
                        ch = max(ch, clamp(0.55 + 0.45 * v))
                dia = abs(qx) + abs(qy)
                hr = 0.24 * (1.0 - 0.52 * t)
                if dia < hr:
                    ca = max(ca, clamp((1.0 - dia / max(0.04, hr)) * 1.35 * flash))
                    ch = 1.0
                if f >= 2:
                    rt = (f - 2) / 5.0
                    ring_r = 0.22 + 0.66 * ease_out_cubic(rt)
                    dr = abs(qr - ring_r)
                    if dr < 0.036:
                        ca = max(ca, (1.0 - dr / 0.036) * (1.0 - rt) * 0.88)
                        ch = max(ch, 0.90)

                glow_a = (
                    math.exp(-(rr / (0.32 + 0.12 * flash)) ** 2) * 0.68
                    + math.exp(-(abs(ry) / 0.11) ** 2) * math.exp(-(abs(rx) / 0.52) ** 2) * 0.50
                ) * flash * smoothstep(0.98, 0.78, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.82, glow_a, ramp=SOUL_RAMP)

    return img


# --- 9. kalla_spirit.png (512x128, 4 looping frames of 128x128) --------------

def make_kalla_spirit() -> Image.Image:
    """
    DNF 冥炎之卡洛 (Kalla of Dark Flame) hovering dark-flame phantom cloak/wraith behind the Soul Bender!
    4 seamless looping frames: royal hooded abyssal reaper mantle with roaring flickering black-violet
    flame plumes rising asymmetrically off the shoulders, piercing pale ghost-cyan eyes under deep hood.
    """
    frames, size = 4, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * math.tau / frames
        # Sweeping flame-mantle cape flares & upward-licking shoulder flame tongues
        plumes = []
        for sign in (-1.0, 1.0):
            w_dy = 0.08 * math.sin(phase + sign * 1.1)
            # Sweeping tattered spectral mantle flaring wide-downward
            for p0, p1, p2, bw in (
                ((sign * 0.24, -0.22), (sign * 0.62, -0.14 + w_dy), (sign * 0.84, 0.44 - w_dy), 0.16),
                # Upward licking dark-flame shoulder horns!
                ((sign * 0.30, -0.26), (sign * 0.58, -0.52 + w_dy), (sign * 0.70, -0.82 - w_dy), 0.10),
            ):
                prev = p0
                for s in range(1, 7):
                    u = s / 6.0
                    cur = quad_bezier(p0, p1, p2, u)
                    plumes.append((prev, cur, bw * (1.0 - 0.66 * u)))
                    prev = cur

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                wave_n = (
                    0.50 * fbm(qx * 4.5, qy * 4.5 + math.sin(phase) * 0.8, 801, 3)
                    + 0.50 * fbm(qx * 5.2 + 3.0, qy * 5.2 + math.cos(phase) * 0.8, 809, 2)
                )

                ca, ch, md = 0.0, 0.08, 9.9
                if -0.84 < qy < 0.90:
                    u_v = (qy + 0.84) / 1.74
                    shred = (0.14 * math.sin(qx * 13.0 + phase) + (wave_n - 0.46) * 0.20) * smoothstep(0.45, 1.0, u_v)
                    cloak_w = 0.46 * smoothstep(0.0, 0.26, u_v) * (1.0 - 0.26 * (u_v ** 1.3)) + (wave_n - 0.46) * 0.16
                    dx = abs(qx - 0.05 * math.sin(qy * 6.0 + phase))
                    md = min(md, max(0.0, dx - cloak_w))
                    if dx < cloak_w:
                        in_c = 1.0 - dx / max(0.04, cloak_w)
                        bf = smoothstep(0.90, 0.54, qy + shred)
                        ca = max(ca, clamp(smoothstep(0.0, 0.24, in_c) * bf * 1.15))
                        face_d = math.hypot(qx * 1.25, (qy + 0.36) * 1.15)
                        if face_d < 0.22:
                            ch = 0.04
                        else:
                            rim_fire = smoothstep(0.42, 0.06, in_c) * 0.58
                            ch = clamp(0.06 + 0.30 * in_c + rim_fire + 0.20 * wave_n)

                for a, b, rad in plumes:
                    d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    re = rad + 0.045 * (wave_n - 0.44)
                    md = min(md, max(0.0, d - re))
                    if d < re and re > 0.01:
                        in_w = 1.0 - d / re
                        ca = max(ca, clamp(smoothstep(0.0, 0.26, in_w) * 1.15))
                        ch = max(ch, clamp(0.12 + 0.62 * (1.0 - in_w * 0.35) * (0.40 + 0.60 * proj)))

                for sign in (-1.0, 1.0):
                    ex = (qx - sign * 0.085) + (qy + 0.38) * sign * 0.36
                    ey = (qy + 0.38) * 2.3
                    if math.hypot(ex, ey) < 0.070:
                        ca, ch = 1.0, 0.99

                glow_a = math.exp(-(md / 0.22) ** 2) * 0.56 * smoothstep(0.99, 0.82, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.64, glow_a, ramp=KALLA_RAMP)

    return img


# --- 10. kalla_fireball.png (512x64, 8 looping frames of 64x64) --------------

def make_kalla_fireball() -> Image.Image:
    """
    Organic roaring DNF Kalla dark-flame orb (hovering shoulder orbs + flying Kalla bolts):
    Turbulent rising teardrop dark-fire silhouette with dense pitch-black core, blazing violet flame crown,
    and pale ghost-cyan tongues! (Zero pinwheel symmetry!)
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * math.tau / frames
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                # Slight teardrop center shifted lower (+0.10) so flame crown rises naturally toward -qy
                cx, cy = qx, qy - 0.10
                qr = math.hypot(cx * (1.0 + 0.35 * max(0.0, -cy)), cy)
                qa = math.atan2(cy, cx)
                n1 = fbm(qx * 4.2 + math.cos(phase) * 0.85, qy * 4.2 + math.sin(phase) * 0.85, 907, 3)
                n2 = fbm(qx * 7.5 - math.sin(phase) * 1.1, qy * 7.5 + math.cos(phase) * 1.1, 929, 2)

                # Organic flickering flames rising upward (-qy) with multi-frequency turbulence
                rise_crown = 0.30 * max(0.0, -cy) * (0.65 + 0.70 * n1)
                lick = 0.09 * math.sin(qa * 3.0 - phase) + 0.06 * math.cos(qa * 5.0 + phase * 2.0) + (n1 - 0.46) * 0.24
                orb_r = 0.52 + rise_crown + lick

                ca, ch = 0.0, 0.08
                if qr < orb_r:
                    u = qr / max(0.05, orb_r)
                    ca = clamp(smoothstep(1.0, 0.66, u) * 1.38)
                    # Classic DNF Kalla dark fire: pitch-black abyssal core -> roaring violet body -> pale-cyan corona!
                    if u < 0.44:
                        ch = clamp(0.03 + 0.22 * (u / 0.44) + 0.14 * n2)
                    else:
                        ru = (u - 0.44) / 0.56
                        ch = clamp(0.46 + 0.52 * math.sin(ru * math.pi * 0.88) + 0.18 * (n2 - 0.45))

                # Orbiting spectral sparks rising around fireball
                if 0.44 < qr < 0.94 and hash2((x + f * 3) // 2, (y + f * 4) // 2, 919) > 0.968:
                    ca = max(ca, 0.92)
                    ch = 0.95

                rr = math.hypot(rx, ry - 0.08)
                glow_a = math.exp(-(rr / 0.56) ** 2) * 0.64 * smoothstep(0.99, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.68, glow_a, ramp=KALLA_RAMP)

    return img


# --- 11. kalla_explosion.png (1024x128, 8 frames of 128x128) -----------------

def make_kalla_explosion() -> Image.Image:
    """
    DNF Kalla dark-flame small AoE impact burst (2-block enemy/terrain explosion & finisher impacts):
    Blinding pale-cyan/violet flash -> violent jagged black-violet flame detonation petals -> smoking embers!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    petals = []
    for i in range(12):
        ang = i * (math.tau / 12.0) + (hash2(i, 3, 941) - 0.5) * 0.30
        lm = 0.66 + hash2(i, 7, 941) * 0.32
        wm = 0.08 + hash2(i, 11, 941) * 0.06
        petals.append((ang, lm, wm))

    for f in range(frames):
        t = f / (frames - 1)
        reach = 0.26 + 0.72 * ease_out_cubic(t)
        fade = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.76 * smoothstep(0.54, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr, rr = math.hypot(qx, qy), math.hypot(rx, ry)
                n = fbm(qx * 6.0, qy * 6.0, f * 19 + 47, 3)

                ca, ch = 0.0, 0.12

                # Expanding dark-flame blast tongues radiating outward
                for ang, lm, wm in petals:
                    r0 = max(0.0, reach * lm - 0.46 * (1.0 - 0.35 * t))
                    r1 = min(0.96, reach * lm)
                    d, proj = seg_dist(qx, qy, math.cos(ang) * r0, math.sin(ang) * r0, math.cos(ang) * r1, math.sin(ang) * r1)
                    pw = wm * (0.45 + 0.90 * math.sin(proj * math.pi)) * (1.0 - 0.35 * t)
                    if d < pw and pw > 0.01:
                        in_p = 1.0 - d / pw
                        av = clamp(smoothstep(0.0, 0.30, in_p) * fade * 1.32)
                        hv = clamp(0.10 + 0.58 * in_p + 0.34 * (1.0 - proj) * (1.0 - 0.35 * t))
                        if av >= ca * 0.85:
                            ca = max(ca, av)
                            ch = max(ch, hv)

                # Turbulent black-purple shock ring
                ring_r = 0.18 + 0.66 * ease_out_cubic(t)
                ring_w = 0.24 * (1.0 - 0.52 * t)
                rd = abs(qr - (ring_r + (n - 0.5) * 0.16))
                if rd < ring_w and (f < 4 or n > (t - 0.38) * 1.2):
                    in_r = 1.0 - rd / max(0.02, ring_w)
                    ra = clamp(smoothstep(0.0, 0.32, in_r) * fade * 1.05)
                    if ra > ca:
                        ca = ra
                        ch = clamp(0.16 + 0.68 * in_r * (1.0 - 0.28 * t))

                # Blinding cyan-violet impact flash core on frames 0..2
                if f <= 2:
                    fr = 0.44 - f * 0.11
                    if qr < fr:
                        in_c = 1.0 - qr / max(0.04, fr)
                        ca = max(ca, clamp(smoothstep(0.0, 0.32, in_c) * (1.0 - f * 0.24)))
                        ch = max(ch, clamp(0.70 + 0.30 * in_c))

                # Flying dark-flame embers
                if f >= 1 and 0.22 < qr < 0.97 and hash2(x // 2, y // 2, f * 37 + 9) > 0.960:
                    ca = max(ca, 0.92 * fade)
                    ch = max(ch, 0.92 if hash2(x, y, f) > 0.45 else 0.26)

                glow_a = math.exp(-(rr / (0.34 + 0.36 * t)) ** 2) * 0.64 * fade * smoothstep(0.98, 0.78, rr)
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.70 - 0.22 * t, glow_a, ramp=KALLA_RAMP)

    return img


# --- 12. blache_swamp.png (256x256) ------------------------------------------

def make_blache_swamp() -> Image.Image:
    """
    DNF 第七鬼神：邪神之怖拉修 colossal 8-block abyssal ghost swamp!
    Pitch-black devouring liquid abyss with jagged encroaching dark-purple shoreline tendrils,
    submerged forbidden summoning rings, and churning eerie violet/cyan miasma ripples.
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

            qr, rr = math.hypot(qx, qy), math.hypot(rx, ry)
            qa = math.atan2(qy, qx)
            n_big = fbm(qx * 3.8 + 2.0, qy * 3.8 + 5.0, 1009, 3)
            n_fine = fbm(qx * 9.0, qy * 9.0, 1019, 2)

            # Jagged encroaching abyssal shoreline
            shore = 0.78 + 0.12 * (n_big - 0.45) + 0.06 * math.sin(qa * 9.0) + 0.03 * math.cos(qa * 17.0)
            ca, ch = 0.0, 0.04

            if qr < shore:
                edge_fade = smoothstep(shore, shore - 0.08, qr)
                ca = clamp(edge_fade * 0.96)
                # Deep bottomless black core -> churning swirling purple miasma ridges -> bright violet rim
                vortex = 0.5 + 0.5 * math.sin(qa * 5.0 - qr * 12.0 + n_big * 4.5)
                rim = math.exp(-((qr - (shore - 0.05)) / 0.055) ** 2)
                ch = clamp(0.03 + 0.24 * vortex * smoothstep(0.18, 0.72, qr) + 0.42 * rim + 0.14 * n_fine)

                # Submerged concentric forbidden rune rings pulsing beneath the black swamp
                for rad, thick in ((0.64, 0.018), (0.40, 0.016)):
                    d = abs(qr - (rad + 0.015 * math.sin(qa * 6.0)))
                    if d < thick:
                        v = 1.0 - d / thick
                        ch = max(ch, clamp(0.55 + 0.36 * v))

                # Glowing submerged rune notches along outer swamp ring
                if 0.66 < qr < 0.72:
                    sec = ((qa + math.pi) / math.tau) * 24.0
                    if 0.22 < (sec - math.floor(sec)) < 0.78 and hash2(int(sec), 3, 1031) > 0.38:
                        ch = max(ch, 0.84)

            glow_a = (
                math.exp(-((rr - 0.78) / 0.11) ** 2) * 0.48
                + math.exp(-(rr / 0.55) ** 2) * 0.30
            ) * smoothstep(1.0, 0.88, rr)
            px[x, y] = pack_ramp_pixel(ch, ca, 0.58, glow_a, ramp=BLACHE_RAMP)

    return img


# --- 13. blache_head.png (1024x128, 8 frames of 128x128) ---------------------

def make_blache_head() -> Image.Image:
    """
    Iconic DNF 第七鬼神：邪神之怖拉修 colossal rising abyssal demon-dragon head & devouring jaws!
    All anatomy sits cleanly inside [-0.90..+0.90] (zero border clipping!), curved predatory maw,
    dramatic canine tusks, arched horned dragon cranium, blazing cyan predator eyes, & crunch flash!
      Frames 0-2: Massive horned dragon-demon head rising & gaping jaws roaring wide open
      Frames 3-5: VIOLENT DEVOURING SNAP-BITE clamping shut with cyan-violet shock-cross!
      Frames 6-7: Roaring abyssal flame crest dissolving into dark smoke!
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        if f <= 2:
            jaw_gap = lerp(0.16, 0.34, f / 2.0)
            bite_flash = 0.0
        elif f <= 5:
            u_b = (f - 2) / 3.0
            jaw_gap = lerp(0.34, 0.02, ease_out_cubic(u_b))
            bite_flash = math.sin(u_b * math.pi)
        else:
            jaw_gap = 0.08 + 0.05 * (f - 5)
            bite_flash = 0.0

        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.72 * smoothstep(0.68, 1.0, t))
        # Centered safely lower so tall horns NEVER touch the top frame edge
        upper_cy = -0.10 - jaw_gap * 0.52
        lower_cy = 0.24 + jaw_gap * 0.60

        # Massive swept demonic dragon horns kept cleanly inside [-0.88..+0.88]
        horns = []
        for sign in (-1.0, 1.0):
            for p0, p1, p2, bw in (
                ((sign * 0.24, upper_cy - 0.22), (sign * 0.58, upper_cy - 0.36), (sign * 0.80, max(-0.88, upper_cy - 0.56)), 0.115),
                ((sign * 0.36, upper_cy - 0.06), (sign * 0.70, upper_cy - 0.12), (sign * 0.88, max(-0.84, upper_cy - 0.30)), 0.085),
                # Cheekbone armor spikes flaring out-downward
                ((sign * 0.44, upper_cy + 0.02), (sign * 0.72, upper_cy + 0.16), (sign * 0.82, upper_cy + 0.32), 0.072),
            ):
                prev = p0
                for s in range(1, 7):
                    u = s / 6.0
                    cur = quad_bezier(p0, p1, p2, u)
                    horns.append((prev, cur, bw * (1.0 - 0.75 * u)))
                    prev = cur

        # Curved crescent jaws: arch follows parabola so jaws look fiercely organic, never a flat bar!
        fangs = []
        for fx_pos, f_len, fw in (
            (-0.40, 0.22, 0.055), (-0.24, 0.14, 0.038), (-0.11, 0.12, 0.034),
            (0.0, 0.10, 0.030),
            (0.11, 0.12, 0.034), (0.24, 0.14, 0.038), (0.40, 0.22, 0.055),
        ):
            u_arch_y = upper_cy + 0.08 - 0.22 * (fx_pos * fx_pos)
            l_arch_y = lower_cy - 0.06 + 0.20 * (fx_pos * fx_pos)
            # Upper curved canine fangs pointing downward-inward
            fangs.append(((fx_pos, u_arch_y), (fx_pos * 0.90, u_arch_y + f_len), fw))
            # Lower menacing tusks pointing upward-inward
            if abs(fx_pos) <= 0.36:
                fangs.append(((fx_pos * 0.92, l_arch_y), (fx_pos * 0.84, l_arch_y - f_len * 0.86), fw * 0.90))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0
                n = fbm(qx * 5.2, qy * 5.2 - t * 2.5, 1103, 3)

                ca, ch, md = 0.0, 0.06, 9.9
                dissolve = 1.0 if (f < 6 or n > (t - 0.60) * 1.4) else 0.18

                u_lip = upper_cy + 0.08 - 0.22 * (qx * qx)
                l_lip = lower_cy - 0.06 + 0.20 * (qx * qx)

                # 1. Pitch-black abyssal devouring maw cavity between curved upper & lower jaws
                maw_mid = (u_lip + l_lip) * 0.5
                maw_h = max(0.05, (l_lip - u_lip) * 0.54)
                d_maw = math.hypot(qx / 0.54, (qy - maw_mid) / maw_h)
                if d_maw < 1.0:
                    ca = max(ca, smoothstep(1.0, 0.70, d_maw) * env * dissolve * 0.98)
                    throat_glow = math.exp(-d_maw * 2.2) * (0.28 + 0.70 * bite_flash)
                    ch = max(ch, clamp(0.02 + throat_glow))

                # 2. Arched armored demonic dragon brow & nasal snout crest
                d_brow = math.hypot(qx / 0.56, (qy - (upper_cy - 0.14)) / 0.34)
                r_brow = 1.0 + 0.09 * (n - 0.5) - 0.10 * max(0.0, abs(qx) - 0.35)
                md = min(md, max(0.0, (d_brow - r_brow) * 0.45))
                if d_brow < r_brow and qy <= u_lip + 0.04:
                    in_b = 1.0 - d_brow / r_brow
                    ca = max(ca, smoothstep(0.0, 0.22, in_b) * env * dissolve * 1.18)
                    # Sculpted forehead brow ridges & central demonic nasal bridge
                    ridge = 0.5 + 0.5 * math.cos(abs(qx) * 14.0 - (qy - upper_cy) * 10.0)
                    bridge = math.exp(-(qx / 0.09) ** 2) * 0.28
                    ch = max(ch, clamp(0.08 + 0.40 * in_b + 0.20 * ridge * in_b + bridge))

                # 3. Curved armored lower jaw mandible tapering down into swirling abyssal smoke neck
                if qy >= l_lip - 0.04 and qy <= 0.95:
                    u_neck = clamp((qy - l_lip) / max(0.12, 0.95 - l_lip))
                    # Curved V-chin mandible tapering neatly into misty neck!
                    jaw_w = (0.50 * (1.0 - 0.18 * u_neck) + 0.14 * math.sin(u_neck * math.pi * 0.6)) + (n - 0.46) * 0.16
                    dx = abs(qx)
                    md = min(md, max(0.0, dx - jaw_w))
                    if dx < jaw_w:
                        in_j = 1.0 - dx / max(0.04, jaw_w)
                        bot_fade = smoothstep(0.96, 0.74, qy + (n - 0.5) * 0.14)
                        ca = max(ca, clamp(smoothstep(0.0, 0.24, in_j) * bot_fade * env * dissolve * 1.16))
                        chin_ridge = 0.22 * smoothstep(0.35, 0.05, u_neck) * in_j
                        ch = max(ch, clamp(0.06 + 0.38 * in_j + chin_ridge))

                # 4. Swept horned dragon crown & cheek armor spikes
                for a, b, rad in horns:
                    d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    re = rad + 0.015 * (n - 0.5)
                    md = min(md, max(0.0, d - re))
                    if d < re and re > 0.008:
                        in_h = 1.0 - d / re
                        ca = max(ca, clamp(smoothstep(0.0, 0.24, in_h) * env * dissolve * 1.22))
                        ch = max(ch, clamp(0.16 + 0.52 * in_h + 0.26 * proj))

                # 5. Blazing eerie ghost-cyan predator eyes (slanted furious twin eyes!)
                for sign in (-1.0, 1.0):
                    ex = (qx - sign * 0.23) * 1.0 + (qy - (upper_cy - 0.14)) * sign * 0.55
                    ey = -(qx - sign * 0.23) * sign * 0.45 + (qy - (upper_cy - 0.14)) * 1.0
                    ed = math.hypot(ex * 1.15, ey * 2.9)
                    if ed < 0.135:
                        in_e = 1.0 - ed / 0.135
                        ca = max(ca, env)
                        ch = max(ch, clamp(0.84 + 0.16 * in_e))

                # 6. Jagged curved rows of razor bone-white/cyan canine tusks & teeth
                for a, b, rad in fangs:
                    d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    rf = rad * (1.0 - 0.76 * proj)
                    if d < rf and rf > 0.006:
                        in_f = 1.0 - d / rf
                        ca = max(ca, clamp(smoothstep(0.0, 0.20, in_f) * env * dissolve * 1.38))
                        ch = max(ch, clamp(0.84 + 0.16 * in_f))

                # 7. Violent horizontal + star devour-crunch shock flash on frames 3..5!
                if bite_flash > 0.05:
                    bd = abs(qy - maw_mid)
                    if bd < 0.15 * bite_flash and abs(qx) < 0.90:
                        bf = (1.0 - bd / (0.15 * bite_flash)) * smoothstep(0.90, 0.12, abs(qx)) * bite_flash
                        ca = max(ca, clamp(bf * 1.4))
                        ch = max(ch, clamp(0.86 + 0.14 * bf))

                glow_a = math.exp(-(md / 0.18) ** 2) * 0.56 * env * smoothstep(0.98, 0.84, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.68, glow_a, ramp=BLACHE_RAMP)

    return img


# --- 14. blache_hand.png (512x128, 8 frames of 64x128) -----------------------

def make_blache_hand() -> Image.Image:
    """
    Gaunt clawed abyssal shadow hands clawing skyward out of Blache's swamp perimeter!
    v=0 top reaching claws, v=1 swamp emergence base.
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        reach = smoothstep(0.0, 0.35, t)
        curl = smoothstep(0.45, 0.85, t)
        env = smoothstep(0.0, 0.10, t + 0.08) * (1.0 - 0.72 * smoothstep(0.70, 1.0, t))
        palm_y = lerp(0.82, 0.40, reach)

        fingers = []
        specs = [
            ((-0.26, palm_y + 0.06), (-0.64, palm_y - 0.06), (-0.54, palm_y - 0.24), (-0.28, palm_y - 0.02), 0.10),
            ((-0.18, palm_y - 0.02), (-0.42, palm_y - 0.24), (-0.24, palm_y - 0.36), (-0.14, palm_y - 0.08), 0.095),
            ((0.0, palm_y - 0.05),   (0.02, palm_y - 0.28),  (0.08, palm_y - 0.38),  (0.02, palm_y - 0.08),  0.105),
            ((0.18, palm_y - 0.02),  (0.42, palm_y - 0.22),  (0.36, palm_y - 0.34),  (0.16, palm_y - 0.06),  0.095),
            ((0.28, palm_y + 0.04),  (0.60, palm_y - 0.08),  (0.58, palm_y - 0.20),  (0.28, palm_y - 0.01),  0.085),
        ]
        for kn, mo, to, tc, bw in specs:
            tip = (lerp(to[0], tc[0], curl), lerp(to[1], tc[1], curl))
            mid = (lerp(mo[0], mo[0] * 0.82, curl), lerp(mo[1], mo[1] - 0.04, curl))
            prev = kn
            for s in range(1, 6):
                u = s / 5.0
                cur = quad_bezier(kn, mid, tip, u)
                fingers.append((prev, cur, bw * (1.0 - 0.68 * u), u))
                prev = cur

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = quantize(y / h, 64)  # 0 top -> 1 base
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = (y + 0.5) / h
                n = fbm(qx * 5.0, qy * 6.0 - t * 2.0, 1201, 2)

                ca, ch, md = 0.0, 0.08, 9.9
                # Gaunt emerging shadow forearm
                if qy >= palm_y:
                    da, pa = seg_dist(qx, qy, 0.0, 1.0, 0.0, palm_y)
                    aw = lerp(0.34, 0.22, pa) + 0.06 * (n - 0.5)
                    md = min(md, max(0.0, da - aw))
                    if da < aw:
                        in_a = 1.0 - da / aw
                        ca = max(ca, smoothstep(0.0, 0.28, in_a) * smoothstep(1.0, 0.86, qy) * env)
                        ch = max(ch, clamp(0.06 + 0.44 * in_a))
                # Palm
                dp = math.hypot(qx, (qy - palm_y) * 2.2)
                if dp < 0.30:
                    in_p = 1.0 - dp / 0.30
                    md = min(md, 0.0)
                    ca = max(ca, smoothstep(0.0, 0.26, in_p) * env)
                    ch = max(ch, clamp(0.10 + 0.46 * in_p))
                # Skeletal grasping talons
                for a, b, rad, fu in fingers:
                    d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    md = min(md, max(0.0, d - rad))
                    if d < rad and rad > 0.008:
                        in_f = 1.0 - d / rad
                        ca = max(ca, clamp(smoothstep(0.0, 0.28, in_f) * env * 1.2))
                        ch = max(ch, clamp(0.12 + 0.50 * in_f + 0.32 * fu))

                glow_a = math.exp(-(md / 0.20) ** 2) * 0.50 * env * smoothstep(0.98, 0.80, abs(rx)) * smoothstep(1.0, 0.86, ry)
                px[f * w + x, y] = pack_ramp_pixel(ch, ca, 0.62, glow_a, ramp=BLACHE_RAMP)

    return img


# --- 15. abyss_flame_pillar.png (512x128, 8 frames of 64x128) ----------------

def make_abyss_flame_pillar() -> Image.Image:
    """
    Soaring multi-tongue DNF dark-violet abyssal flame pillar (Blache eruption, Tombstone impact plumes, Soul Slash):
    v=1 anchored to ground, v=0 blasts skyward with thick dark-indigo borders, blazing violet core & pale-cyan veins.
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        rise_h = smoothstep(0.0, 0.24, t) * 0.84 + 0.16
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.72 * smoothstep(0.64, 1.0, t))
        scroll = t * 5.0

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = 1.0 - quantize(y / h, 64)  # 0 ground -> 1 sky
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = 1.0 - (y + 0.5) / h

                v_norm = qy / max(0.16, rise_h)
                n1 = fbm(qx * 3.8, qy * 5.4 - scroll, f * 7 + 31, 3)
                n2 = fbm(qx * 7.8 + 2.5, qy * 8.4 - scroll * 1.4, 83, 2)

                ca, ch = 0.0, 0.08
                if v_norm <= 1.08:
                    base_flare = 0.48 * math.exp(-((qy / 0.18) ** 2))
                    main_w = (0.42 + base_flare) * (1.0 - 0.30 * (v_norm ** 1.45))
                    sway = 0.10 * math.sin(qy * 10.0 - scroll * 2.1)
                    s1x = -0.28 - 0.18 * v_norm + 0.08 * math.sin(qy * 13.0 - scroll * 2.5)
                    s2x = 0.28 + 0.18 * v_norm + 0.08 * math.cos(qy * 12.0 - scroll * 2.3)
                    sw1 = 0.23 * smoothstep(0.80, 0.12, v_norm)
                    sw2 = 0.22 * smoothstep(0.86, 0.16, v_norm)

                    jag = (n1 - 0.46) * 0.38 + (n2 - 0.50) * 0.16
                    in_f = max(
                        1.0 - abs(qx - sway) / max(0.05, main_w + jag),
                        (1.0 - abs(qx - s1x) / max(0.04, sw1 + jag * 0.6)) * 0.88,
                        (1.0 - abs(qx - s2x) / max(0.04, sw2 + jag * 0.6)) * 0.88,
                    )
                    if in_f > 0.0 and v_norm < 1.0 + (n1 - 0.44) * 0.22:
                        dissolve = 0.0 if (t > 0.58 and n2 < ((t - 0.58) / 0.42) * 0.72) else 1.0
                        ca = clamp(smoothstep(0.0, 0.24, in_f) * smoothstep(0.0, 0.04, qy) * smoothstep(1.06, 0.78, v_norm) * env * dissolve * 1.35)
                        vein = smoothstep(0.40, 0.84, in_f) * (0.55 + 0.45 * n1)
                        hot_base = math.exp(-((qy / 0.22) ** 2)) * in_f * 0.46
                        ch = clamp(0.08 + 0.56 * in_f + 0.38 * vein + hot_base - 0.16 * t)

                if 0.06 < qy < min(0.99, rise_h + 0.16):
                    sp = hash2(x // 2, (y + int(t * 28)) // 2, f * 29 + 17)
                    if sp > 0.964 and abs(qx) < 0.94:
                        ca = max(ca, 0.92 * env)
                        ch = max(ch, 0.92 if sp > 0.984 else 0.40)

                g_env = smoothstep(1.10, 0.78, ry / max(0.2, rise_h)) * smoothstep(0.0, 0.05, ry)
                glow_a = math.exp(-(abs(rx) / 0.54) ** 2) * g_env * 0.56 * env * smoothstep(1.0, 0.82, abs(rx))
                px[f * w + x, y] = pack_ramp_pixel(ch, ca, 0.64, glow_a, ramp=SOUL_RAMP)

    return img


# --- 16. ghost_mist.png (256x64, 4 looping frames of 64x64) ------------------

def make_ghost_mist() -> Image.Image:
    """Churning 4-frame dark-indigo & violet shadow-mist cloud for Keiga aura, Tombstone smoke & Swamp fog."""
    frames, size = 4, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f * math.tau / frames
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr, rr = math.hypot(qx, qy), math.hypot(rx, ry)
                n1 = fbm(qx * 3.2 + math.cos(phase) * 0.7, qy * 3.2 + math.sin(phase) * 0.7, 1301, 3)
                n2 = fbm(qx * 6.0 - math.sin(phase) * 0.8, qy * 6.0 + math.cos(phase) * 0.8, 1319, 2)

                mask = smoothstep(0.92, 0.24, qr + (n1 - 0.5) * 0.34)
                wisp = smoothstep(0.32, 0.76, n1 * 0.65 + n2 * 0.35)
                ca = clamp(mask * wisp * 1.15)
                ch = clamp(0.10 + 0.52 * n2 * mask + 0.16 * (1.0 - qr))
                glow_a = math.exp(-(rr / 0.62) ** 2) * 0.28 * smoothstep(0.98, 0.72, rr)
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.45, glow_a, ramp=SOUL_RAMP)

    return img


# --- 17. moonlight_arc.png (1024x128, 8 frames of 128x128) -------------------

MOON_RAMP = (
    (0.00, (10, 6, 26)),      # abyssal midnight indigo shadow
    (0.20, (34, 16, 78)),     # shadowed lunar violet
    (0.44, (98, 46, 186)),    # classic DNF Moonlight amethyst
    (0.68, (164, 108, 250)),  # luminous moon-lavender blade
    (0.84, (214, 198, 255)),  # silver-moon shimmer
    (0.94, (192, 240, 255)),  # pale spectral moon-cyan edge
    (1.00, (250, 253, 255)),  # white-hot lunar razor highlight
)


def make_moonlight_arc() -> Image.Image:
    """
    DNF Moonlight Slash / Full Moonlight Slash (满月斩) sweeping lunar crescent arc.
    Peak faces +x (forward) for standard emitPlane forward-slash orientation:
    halfU = dir * scale, halfV = side * scale.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        sweep_lead = lerp(-0.55, 1.65, ease_out_cubic(t))
        sweep_tail = lerp(-1.72, 0.62, t ** 1.08)
        fade = lerp(0.72, 1.0, smoothstep(0.0, 0.16, t)) * (1.0 - smoothstep(0.72, 1.0, t) * 0.68)
        expand = lerp(0.76, 0.94, ease_out_cubic(t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                # Crescent center offset slightly backward so apex projects boldly forward (+x)
                cx = -0.14
                dx, dy = qx - cx, qy
                rdx, rdy = rx - cx, ry
                rad = math.hypot(dx, dy)
                rrad = math.hypot(rdx, rdy)
                ang = math.atan2(dy, dx)

                # Angular envelope spanning front-sweeping crescent
                in_lead = smoothstep(sweep_lead + 0.20, sweep_lead - 0.10, ang)
                in_tail = smoothstep(sweep_tail - 0.34, sweep_tail + 0.24, ang)
                front_cap = smoothstep(-2.05, -1.35, ang) * smoothstep(2.05, 1.35, ang)
                arc_env = in_lead * in_tail * front_cap

                # Crisp waxing moon crescent thickness: widest near apex (ang ~ 0), razor-thin tips!
                taper = clamp(math.cos(ang * 0.62)) ** 0.75
                outer_r = expand
                inner_r = expand - (0.34 * taper + 0.05)

                n = fbm(qx * 5.2 + t * 2.4, qy * 5.2 - t * 1.8, 1709, 3)
                outer_edge = smoothstep(outer_r + 0.04, outer_r - 0.025, rad + (n - 0.5) * 0.03)
                inner_edge = smoothstep(inner_r - 0.06, inner_r + 0.06, rad + (n - 0.5) * 0.06)
                crescent = outer_edge * inner_edge * arc_env

                # Razor silver rim along outer crescent curve
                rim_dist = abs(rad - (outer_r - 0.045))
                rim = math.exp(-(rim_dist / 0.038) ** 2) * arc_env * smoothstep(0.15, 0.55, taper)

                # Trailing stardust wisps behind inner curve
                sparkle_n = value_noise(qx * 14.0 - t * 6.0, qy * 14.0, 1733)
                sparkle = smoothstep(0.80, 0.95, sparkle_n) * smoothstep(inner_r - 0.26, inner_r, rad) * smoothstep(outer_r, inner_r - 0.02, rad) * arc_env

                ca = clamp((crescent * 0.96 + rim * 0.85 + sparkle * 0.72) * fade)
                radial_t = clamp((rad - inner_r) / max(0.05, outer_r - inner_r))
                ch = clamp(0.14 + 0.54 * radial_t + 0.34 * rim + 0.24 * sparkle + (n - 0.5) * 0.14)

                # Soft outer lunar halo (vanishes before border)
                edge_safe = smoothstep(0.98, 0.78, max(abs(rx), abs(ry)))
                glow_band = math.exp(-((rrad - (outer_r - 0.08)) / 0.16) ** 2) * arc_env
                glow_a = clamp(glow_band * 0.44 * fade * edge_safe)
                px[f * size + x, y] = pack_ramp_pixel(ch, ca, 0.78, glow_a, ramp=MOON_RAMP)

    return img


# --- 18. full_moon_ring.png (256x256, 1 frame ground 360° eclipse ring) ------

def make_full_moon_ring() -> Image.Image:
    """
    360° Full Moonlight Slash (满月回旋斩) & Ghost Step counter lunar eclipse ring.
    Strictly zero alpha at all four corners for artifact-free ground quads.
    """
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
            rr = math.hypot(rx, ry)
            if rr >= 0.97:
                continue

            ang = math.atan2(qy, qx)
            n = fbm(qx * 4.5, qy * 4.5, 1811, 3)

            # Bold outer razor moon ring + eccentric eclipse inner crescent cutout
            outer_band = math.exp(-((r - 0.80) / 0.085) ** 2)
            razor_ring = math.exp(-((r - 0.84) / 0.022) ** 2)
            inner_ring = math.exp(-((r - 0.52) / 0.032) ** 2) * (0.55 + 0.45 * math.sin(ang * 4.0 + 0.6))

            # Waxing/waning crescent rune arcs around the ring
            rune_wave = smoothstep(0.35, 0.85, math.sin(ang * 8.0 + n * 1.4))
            rune_band = math.exp(-((r - 0.66) / 0.048) ** 2) * rune_wave * 0.72

            # Swirling slash streaks radiating along circumference
            swirl = smoothstep(0.42, 0.84, fbm(math.cos(ang * 3.0) * 2.4 + r * 3.0, math.sin(ang * 3.0) * 2.4, 1847, 2))
            swirl_band = smoothstep(0.92, 0.72, r) * smoothstep(0.36, 0.64, r) * swirl * 0.58

            edge_mask = smoothstep(0.96, 0.86, rr)
            ca = clamp((outer_band * 0.84 + razor_ring * 0.96 + inner_ring * 0.64 + rune_band + swirl_band) * edge_mask)
            ch = clamp(0.16 + 0.44 * outer_band + 0.46 * razor_ring + 0.25 * inner_ring + 0.22 * rune_band)
            glow_a = clamp(math.exp(-((rr - 0.78) / 0.16) ** 2) * 0.42 * edge_mask)
            px[x, y] = pack_ramp_pixel(ch, ca, 0.76, glow_a, ramp=MOON_RAMP)

    return img


# --- 19. moonlight_slash.png (64x64 skill GUI icon) --------------------------

def make_moonlight_icon() -> Image.Image:
    """64x64 crisp pixel-art skill icon for 满月斩 (Moonlight Slash)."""
    size = 64
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    for y in range(size):
        for x in range(size):
            nx = (x + 0.5) / size * 2.0 - 1.0
            ny = (y + 0.5) / size * 2.0 - 1.0
            border = max(abs(nx), abs(ny))

            # Framed dark-abyssal icon card
            if border > 0.94:
                px[x, y] = (14, 8, 28, 255)
                continue
            if border > 0.86:
                rim_t = clamp(0.45 + 0.35 * (1.0 - ny * 0.5))
                r, g, b = sample_ramp(MOON_RAMP, rim_t)
                px[x, y] = (r, g, b, 255)
                continue

            # Deep night-sky gradient background
            bg_r = math.hypot(nx, ny)
            n = fbm(nx * 3.8, ny * 3.8, 1907, 2)
            bg_h = clamp(0.05 + 0.20 * (1.0 - bg_r * 0.75) + n * 0.08)
            br, bg_g, bb = sample_ramp(MOON_RAMP, bg_h)

            # Iconic glowing full-moon crescent slash sweeping from bottom-left to top-right
            d_outer = math.hypot(nx - 0.04, ny + 0.02)
            d_inner = math.hypot(nx + 0.20, ny - 0.16)
            moon = smoothstep(0.68, 0.60, d_outer) * smoothstep(0.44, 0.54, d_inner)

            # Diagonal silver-cyan slash streak slicing across the moon
            sd, st = seg_dist(nx, ny, -0.68, 0.56, 0.68, -0.58)
            slash_w = 0.055 + 0.065 * math.sin(st * math.pi)
            slash = smoothstep(slash_w + 0.03, slash_w * 0.3, sd)
            slash_core = smoothstep(slash_w * 0.55, 0.0, sd)

            # Subtle background moonlight halo
            halo = math.exp(-((d_outer - 0.56) / 0.22) ** 2) * 0.38

            fg_a = clamp(max(moon * 0.96, slash * 0.98))
            fg_h = clamp(0.56 * moon + 0.32 * smoothstep(0.54, 0.65, d_outer) * moon + 0.42 * slash + 0.48 * slash_core)
            fr, fg_g, fb = sample_ramp(MOON_RAMP, fg_h)
            hr, hg, hb = sample_ramp(MOON_RAMP, 0.64)

            cr = br * (1.0 - halo) + hr * halo
            cg = bg_g * (1.0 - halo) + hg * halo
            cb = bb * (1.0 - halo) + hb * halo

            out_r = int(round(clamp(cr * (1.0 - fg_a) + fr * fg_a, 0, 255)))
            out_g = int(round(clamp(cg * (1.0 - fg_a) + fg_g * fg_a, 0, 255)))
            out_b = int(round(clamp(cb * (1.0 - fg_a) + fb * fg_a, 0, 255)))
            px[x, y] = (out_r, out_g, out_b, 255)

    return img


# --- Contact Sheet Preview Generator -----------------------------------------

def save_contact_sheet(assets: dict[str, Image.Image], out_path: Path, bg=(14, 10, 24, 255)) -> None:
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
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(198, 174, 255, 255))
        y_cur += label_h
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(62, 42, 98, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "soul_slash.png": make_soul_slash(),
        "ghost_field_sigil.png": make_ghost_field_sigil(),
        "ghost_field_inner.png": make_ghost_field_inner(),
        "ghost_spirits.png": make_ghost_spirits(),
        "soul_devour_head.png": make_soul_devour_head(),
        "tombstone_pillar.png": make_tombstone_pillar(),
        "phantom_rift.png": make_phantom_rift(),
        "ghost_flash.png": make_ghost_flash(),
        "kalla_spirit.png": make_kalla_spirit(),
        "kalla_fireball.png": make_kalla_fireball(),
        "kalla_explosion.png": make_kalla_explosion(),
        "blache_swamp.png": make_blache_swamp(),
        "blache_head.png": make_blache_head(),
        "blache_hand.png": make_blache_hand(),
        "abyss_flame_pillar.png": make_abyss_flame_pillar(),
        "ghost_mist.png": make_ghost_mist(),
        "moonlight_arc.png": make_moonlight_arc(),
        "full_moon_ring.png": make_full_moon_ring(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    SKILL_ICON_OUT.mkdir(parents=True, exist_ok=True)
    icon_img = make_moonlight_icon()
    icon_path = SKILL_ICON_OUT / "moonlight_slash.png"
    icon_img.save(icon_path, format="PNG")
    print(f"wrote {icon_path.relative_to(ROOT)} ({icon_img.width}x{icon_img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "soulbender_contact_sheet.png")


if __name__ == "__main__":
    main()

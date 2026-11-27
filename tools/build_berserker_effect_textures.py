#!/usr/bin/env python3
"""
Dedicated DNF-inspired Hellblood Demon (狱血魔神 / Berserker) pixel-art VFX texture generator.

Outputs rich multi-tone pixel-art + soft-feathered RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/berserker/
and the custom 32x32 demonic serrated blood greatsword held item texture at:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect/blood_sword.png

Color direction: Classic DNF Blood Evil Remastered
  - Deep black-crimson / coagulated blood shadows (preserves dark demonic weight under TRANSLUCENT)
  - High-saturation arterial crimson plasma body
  - Scorching scarlet / blood-magma / white-hot core highlights (pops under ADDITIVE)
"""

from __future__ import annotations

import math
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT_VFX = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/berserker"
OUT_ITEM = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect"


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
    """Returns (distance, projection t in [0,1]) from point (px,py) to segment AB."""
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
        u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
        u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
    )


# --- DNF Classic Blood Evil Palette ------------------------------------------

BLOOD_RAMP = (
    (0.00, (18, 2, 8)),     # abyssal black-blood shadow
    (0.18, (64, 5, 17)),    # thick dark clotted blood
    (0.38, (148, 12, 28)),  # deep arterial crimson
    (0.60, (218, 22, 42)),  # classic DNF crimson blood plasma
    (0.78, (250, 56, 54)),  # scorching scarlet rim
    (0.91, (255, 134, 88)), # fiery blood-magma highlight
    (1.00, (255, 236, 218)),# white-hot razor core flash
)

MAGMA_BLOOD_RAMP = (
    (0.00, (14, 3, 7)),     # charred obsidian crust
    (0.16, (40, 6, 13)),    # dark blood-stone
    (0.34, (124, 11, 24)),  # deep magma-blood pool
    (0.56, (210, 22, 36)),  # boiling crimson lava
    (0.76, (252, 66, 38)),  # glowing fissure scarlet-orange
    (0.90, (255, 152, 72)), # molten fissure vein
    (1.00, (255, 238, 196)),# incandescent heart
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


def pack_blood_pixel(heat: float, alpha: float, glow_heat: float = 0.0, glow_alpha: float = 0.0,
                     ramp=BLOOD_RAMP) -> tuple[int, int, int, int]:
    """Composite crisp pixel-art core over soft outer crimson glow."""
    a_core = clamp(alpha)
    a_glow = clamp(glow_alpha)
    out_a = a_core + a_glow * (1.0 - a_core)
    if out_a <= 0.008:
        return (0, 0, 0, 0)
    cr, cg, cb = sample_ramp(ramp, heat)
    gr, gg, gb = sample_ramp(ramp, glow_heat)
    w_core = a_core / out_a
    w_glow = (a_glow * (1.0 - a_core)) / out_a
    r = int(round(clamp(cr * w_core + gr * w_glow, 0, 255)))
    g = int(round(clamp(cg * w_core + gg * w_glow, 0, 255)))
    b = int(round(clamp(cb * w_core + gb * w_glow, 0, 255)))
    return (r, g, b, int(round(clamp(out_a) * 255)))


# --- 1. blood_slash.png (512x64, 8 frames of 64x64) --------------------------

def make_blood_slash() -> Image.Image:
    """Wide, brutal 210-degree sweeping DNF crimson blood-blade arc with serrated edges & spray."""
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Head races fast across ~245 deg while tail lags far behind -> huge sweeping crescent!
        head_ang = -2.30 + 4.55 * (1.0 - (1.0 - clamp(t / 0.62)) ** 2.1) if t < 0.62 else 2.25 + 0.35 * ((t - 0.62) / 0.38)
        tail_ang = -2.45 + 4.75 * smoothstep(0.24, 1.0, t)
        env = smoothstep(0.0, 0.14, t + 0.10) * (1.0 - 0.65 * smoothstep(0.65, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                # Handle angular span cleanly around [-pi, pi]
                span = max(0.55, head_ang - tail_ang)
                arc_u = (ang - tail_ang) / span

                core_a = 0.0
                core_h = 0.25

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.90, arc_u)
                    n = fbm(qx * 5.0, qy * 5.0, f * 13 + 7, 2)
                    # Aggressive wide crescent: thick belly in front 2/3, jagged outer blood barbs
                    teeth = (0.07 * math.sin(ang * 11.0 + f * 0.8) + 0.04 * math.cos(ang * 19.0 - f * 1.1)) * arc_u
                    thick = (0.14 + 0.34 * (arc_u ** 0.65) * (1.0 - 0.20 * arc_u)) * (0.78 + 0.25 * env)
                    center_r = 0.66 - 0.06 * (1.0 - arc_u) + 0.04 * t
                    outer_r = min(0.95, center_r + thick * 0.54 + teeth)
                    inner_r = max(0.16, center_r - thick * 0.62 - 0.05 * (n - 0.45))

                    if inner_r <= r <= outer_r:
                        rad_u = clamp((r - inner_r) / max(0.03, outer_r - inner_r))
                        tear = 1.0
                        if arc_u < 0.38 and n < (0.50 - arc_u * 1.0):
                            tear = 0.22
                        if t > 0.68 and n < (t - 0.60) * 1.4:
                            tear = 0.15
                        core_a = clamp(end_fade * env * tear * smoothstep(0.0, 0.16, rad_u) * smoothstep(1.0, 0.88, rad_u) * 1.35)
                        # Heavy black-crimson trailing shadow -> vivid crimson blade -> blinding scarlet-white razor front!
                        razor = smoothstep(0.62, 0.95, rad_u) * (0.40 + 0.60 * (arc_u ** 0.85))
                        lead_flash = smoothstep(0.72, 0.96, arc_u) * smoothstep(0.45, 0.95, rad_u) * 0.35
                        core_h = clamp(0.10 + 0.54 * rad_u + 0.42 * razor + lead_flash)

                # Visceral flying pixel blood droplets spraying off the outer curve
                if 0.36 < r < 0.97 and tail_ang - 0.35 < ang < head_ang + 0.22:
                    sp = hash2(x // 2, y // 2, f * 41 + 9)
                    if sp > 0.964:
                        core_a = max(core_a, 0.94 * env)
                        core_h = max(core_h, 0.84 if sp > 0.984 else 0.48)

                # Tight glow following ONLY the arc (no boxy/circular clipping)
                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                clamped_u = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.66 - 0.06 * (1.0 - clamped_u) + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.20) ** 2) * math.exp(-(ang_d / 0.20) ** 2)
                glow_a *= 0.60 * env * smoothstep(0.99, 0.86, rr) * (0.25 + 0.75 * clamped_u)
                glow_h = clamp(0.55 + 0.35 * clamped_u)

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 2. blood_claw.png (512x64, 8 frames of 64x64) ---------------------------

def make_blood_claw() -> Image.Image:
    """
    Monstrous asymmetrical DNF 嗜魂之手 demonic crimson beast-claw:
      Frames 0-1: Wide feral clawed hand lunging from lower-left with side thumb + 4 hooked talons
      Frames 2-3: Vicious predatory clench crushing inward toward palm core
      Frame  4  : Crushed demonic fist bursting with scorching white-crimson blood pressure
      Frames 5-7: Ripping open into 4 sweeping curved talon-gouge rends & flying blood spray
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        clench = smoothstep(0.0, 4.0, float(f)) if f <= 4 else 1.0
        burst = 0.0 if f <= 4 else (f - 4) / 3.0

        # Build curved asymmetrical demonic claw skeleton via bezier chains
        claw_chains: list[list[tuple[tuple[float, float], tuple[float, float], float, float]]] = []
        palm = (-0.10 + 0.10 * clench, 0.26 - 0.14 * clench)
        wrist = (-0.42, 0.88)

        if burst <= 0.01:
            # Side-jutting opposable thumb (left-bottom) + 4 aggressive hooked talons arching up & right!
            # Each tuple: (knuckle, mid_ctrl, tip_open, tip_closed, base_w)
            talons = [
                ((-0.38, 0.16), (-0.78, -0.02), (-0.64, -0.34), (-0.16, 0.06), 0.13),  # beast thumb
                ((-0.24, -0.04), (-0.54, -0.52), (-0.20, -0.84), (-0.06, 0.02), 0.13), # index talon
                ((-0.02, -0.10), (-0.06, -0.66), (0.28, -0.86),  (0.04, 0.04), 0.14),  # long middle talon
                ((0.18, -0.04),  (0.46, -0.52),  (0.66, -0.60),  (0.10, 0.08), 0.13),  # ring talon
                ((0.30, 0.12),   (0.68, -0.18),  (0.76, -0.22),  (0.14, 0.14), 0.11),  # pinky talon
            ]
            for kn, mid_o, tip_o, tip_c, bw in talons:
                # As clench approaches 1, mid curls outward like a tense fist while tips hook fiercely inward
                k = (lerp(kn[0], kn[0] * 0.72, clench), lerp(kn[1], kn[1] - 0.04, clench))
                m = (lerp(mid_o[0], mid_o[0] * 0.78, clench), lerp(mid_o[1], mid_o[1] * 0.45 - 0.12, clench))
                tip = (lerp(tip_o[0], tip_c[0], clench), lerp(tip_o[1], tip_c[1], clench))
                chain = []
                prev = k
                steps = 5
                for s in range(1, steps + 1):
                    u = s / steps
                    cur = quad_bezier(k, m, tip, u)
                    w = bw * (1.0 - 0.72 * (u ** 1.15))
                    heat = 0.25 + 0.70 * u
                    chain.append((prev, cur, w, heat))
                    prev = cur
                claw_chains.append(chain)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 5.5, qy * 5.5, f * 23 + 11, 2)
                core_a = 0.0
                core_h = 0.12
                min_shape_d = 9.9

                if burst <= 0.01:
                    # Muscular surging blood-forearm from bottom-left into palm
                    dw, pw = seg_dist(qx, qy, wrist[0], wrist[1], palm[0], palm[1])
                    arm_w = lerp(0.16, 0.30, pw) + 0.06 * (n - 0.45)
                    min_shape_d = min(min_shape_d, max(0.0, dw - arm_w))
                    if dw < arm_w:
                        in_arm = 1.0 - dw / max(0.02, arm_w)
                        wfade = smoothstep(0.0, 0.22, pw)
                        a_val = clamp(smoothstep(0.0, 0.30, in_arm) * wfade * 0.96)
                        if a_val > core_a:
                            core_a = a_val
                            core_h = clamp(0.08 + 0.42 * in_arm + 0.16 * pw)

                    # Thick muscular palm center
                    dp = math.hypot((qx - palm[0]) * 1.05, (qy - palm[1]) * 1.25)
                    pr = 0.36 - 0.03 * clench + 0.04 * (n - 0.5)
                    min_shape_d = min(min_shape_d, max(0.0, dp - pr))
                    if dp < pr:
                        in_p = 1.0 - dp / max(0.05, pr)
                        core_a = max(core_a, smoothstep(0.0, 0.28, in_p) * 0.98)
                        core_h = max(core_h, clamp(0.14 + 0.50 * in_p + 0.24 * clench))

                    # 5 hooked razor demonic talons
                    for chain in claw_chains:
                        for a, b, rad, th in chain:
                            d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                            r_eff = rad + 0.02 * (n - 0.5)
                            min_shape_d = min(min_shape_d, max(0.0, d - r_eff))
                            if d < r_eff:
                                in_t = 1.0 - d / max(0.015, r_eff)
                                fa = clamp(smoothstep(0.0, 0.32, in_t) * 1.15)
                                # Dark coagulated border -> vivid crimson finger -> white-hot razor claw tip!
                                fh = clamp(0.08 + 0.46 * in_t + 0.46 * th * smoothstep(0.15, 0.85, in_t))
                                if fa >= core_a * 0.82:
                                    core_a = max(core_a, fa)
                                    core_h = max(core_h, fh)

                    # Frame 3-4 violent crushing blood-implosion spark inside the clenched fist!
                    if clench > 0.60:
                        pf = (clench - 0.60) / 0.40
                        cr = math.hypot(qx - palm[0], qy - (palm[1] - 0.10))
                        ca = math.atan2(qy - (palm[1] - 0.10), qx - palm[0])
                        spikes = (max(0.0, abs(math.cos(ca * 3.5 + f))) ** 4) * smoothstep(0.68, 0.05, cr)
                        orb = 1.0 - smoothstep(0.06, 0.28, cr)
                        crush = clamp(max(orb, spikes * 0.92)) * pf
                        if crush > 0.06:
                            core_a = max(core_a, crush)
                            core_h = max(core_h, 0.72 + 0.28 * crush)

                    # Silhouette-hugging crimson bloom (NO round disc boundary!)
                    glow_a = math.exp(-(min_shape_d / 0.18) ** 2) * (0.45 + 0.28 * clench) * smoothstep(0.98, 0.84, max(abs(rx), abs(ry)))
                    glow_h = 0.54 + 0.26 * clench
                else:
                    # Frames 5-7: 4 vicious curved beast-claw gouge slashes ripping diagonally + blood burst!
                    fade = 1.0 - 0.66 * burst
                    for i, off in enumerate((-0.44, -0.15, 0.15, 0.42)):
                        sp_o = off * (0.95 + 0.55 * burst)
                        p0 = (sp_o - 0.44, 0.66 - i * 0.04)
                        p1 = (sp_o - 0.06, -0.08)
                        p2 = (sp_o + 0.46, -0.72 + i * 0.05)
                        prev = p0
                        for s in range(1, 7):
                            u = s / 6.0
                            cur = quad_bezier(p0, p1, p2, u)
                            d, _ = seg_dist(qx, qy, prev[0], prev[1], cur[0], cur[1])
                            w = (0.12 - 0.045 * burst) * (math.sin(u * math.pi) ** 0.75)
                            min_shape_d = min(min_shape_d, max(0.0, d - w))
                            if w > 0.01 and d < w:
                                in_t = 1.0 - d / w
                                core_a = max(core_a, clamp(smoothstep(0.0, 0.28, in_t) * fade * 1.25))
                                core_h = max(core_h, clamp(0.24 + 0.74 * in_t * (1.0 - 0.25 * burst)))
                            prev = cur

                    # Spraying blood droplets bursting from the rip
                    qr = math.hypot(qx, qy)
                    if hash2(x // 2, y // 2, f * 61 + 17) > 0.945 and 0.18 < qr < 0.95:
                        core_a = max(core_a, 0.92 * fade)
                        core_h = max(core_h, 0.78 if hash2(x, y, f) > 0.5 else 0.36)

                    glow_a = math.exp(-(min_shape_d / 0.20) ** 2) * 0.58 * fade * smoothstep(0.98, 0.84, max(abs(rx), abs(ry)))
                    glow_h = clamp(0.72 - 0.28 * burst)

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 3. blood_geyser.png (512x128, 8 frames of 64x128) -----------------------

def make_blood_geyser() -> Image.Image:
    """
    Thick, volcanic, multi-plume DNF lava-blood geyser pillar (怒气爆发 / 崩山裂地斩 / 魔狱血刹).
    v=1 anchored to ground, v=0 blasts skyward with thick boiling base, triple churning crimson-black
    blood tongues, splashing mushroom crest, and ejected molten blood clots.
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        rise_h = smoothstep(0.0, 0.24, t) * 0.84 + 0.16
        env = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.70 * smoothstep(0.66, 1.0, t))
        scroll = t * 4.8

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = 1.0 - quantize(y / h, 64)
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = 1.0 - (y + 0.5) / h

                v_norm = qy / max(0.16, rise_h)
                n1 = fbm(qx * 3.6, qy * 5.2 - scroll, f * 7 + 19, 3)
                n2 = fbm(qx * 7.8 + 2.7, qy * 8.5 - scroll * 1.4, 73, 2)

                core_a = 0.0
                core_h = 0.14

                if v_norm <= 1.08:
                    # Three braided eruptive plumes (thick central shaft + 2 violent side blood-tongues!)
                    base_flare = 0.52 * math.exp(-((qy / 0.17) ** 2))
                    crown_flare = 0.34 * math.exp(-(((v_norm - 0.85) / 0.15) ** 2)) * smoothstep(0.22, 0.72, rise_h)
                    main_w = (0.44 + base_flare + crown_flare) * (1.0 - 0.26 * (v_norm ** 1.5))
                    sway_main = 0.09 * math.sin(qy * 10.0 - scroll * 2.0)

                    # Side erupting blood jets shooting out at ~65% and ~78% height
                    side1_x = -0.30 - 0.18 * v_norm + 0.07 * math.sin(qy * 13.0 - scroll * 2.5)
                    side2_x = 0.30 + 0.18 * v_norm + 0.07 * math.cos(qy * 12.0 - scroll * 2.3)
                    side_w1 = 0.24 * smoothstep(0.78, 0.12, v_norm)
                    side_w2 = 0.22 * smoothstep(0.86, 0.16, v_norm)

                    jagged = (n1 - 0.46) * 0.40 + (n2 - 0.50) * 0.18
                    in_main = 1.0 - abs(qx - sway_main) / max(0.05, main_w + jagged)
                    in_s1 = 1.0 - abs(qx - side1_x) / max(0.04, side_w1 + jagged * 0.65)
                    in_s2 = 1.0 - abs(qx - side2_x) / max(0.04, side_w2 + jagged * 0.65)
                    in_frac = max(in_main, in_s1 * 0.88, in_s2 * 0.88)

                    if in_frac > 0.0 and v_norm < 1.0 + (n1 - 0.44) * 0.24:
                        dissolve = 1.0
                        if t > 0.58:
                            cut = (t - 0.58) / 0.42
                            if n2 < cut * 0.70:
                                dissolve = 0.0
                        bottom_fade = smoothstep(0.0, 0.04, qy)
                        top_fade = smoothstep(1.06, 0.80, v_norm)
                        core_a = clamp(smoothstep(0.0, 0.26, in_frac) * bottom_fade * top_fade * env * dissolve * 1.35)

                        # Thick dark-blood crust on outer edges -> rich crimson lava -> scorching magma core!
                        vein = smoothstep(0.36, 0.82, in_frac) * (0.55 + 0.45 * n1)
                        hot_base = math.exp(-((qy / 0.24) ** 2)) * in_frac * 0.48
                        core_h = clamp(0.08 + 0.56 * in_frac + 0.38 * vein + hot_base - 0.18 * t)

                # Thick ejected magma-blood clots & droplets spraying upward and outward
                if 0.05 < qy < min(0.99, rise_h + 0.16):
                    sp = hash2(x // 2, (y + int(t * 28)) // 2, f * 29 + 11)
                    if sp > 0.962 and abs(qx) < 0.94:
                        core_a = max(core_a, 0.94 * env)
                        core_h = max(core_h, 0.84 if sp > 0.982 else 0.42)

                g_y = clamp(ry / max(0.2, rise_h))
                g_env = smoothstep(1.10, 0.78, ry / max(0.2, rise_h)) * smoothstep(0.0, 0.05, ry)
                glow_a = math.exp(-(abs(rx) / 0.54) ** 2) * g_env * 0.56 * env * smoothstep(1.0, 0.82, abs(rx))
                glow_h = clamp(0.60 - 0.16 * g_y)

                px[f * w + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a, ramp=MAGMA_BLOOD_RAMP)

    return img


# --- 4. blood_crater.png (128x128) -------------------------------------------

def make_blood_crater() -> Image.Image:
    """
    Iconic DNF 崩山裂地斩 (大崩) & 崩山击 shattered lava-blood ground crater:
    Charred obsidian-black shattered earth slabs floating over a boiling crimson magma-blood lake
    with jagged glowing molten fracture fault lines radiating outward.
    """
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    cracks: list[tuple[tuple[float, float], tuple[float, float], float]] = []
    for i in range(12):
        ang = i * (math.tau / 12.0) + (hash2(i, 1, 88) - 0.5) * 0.26
        r0 = 0.18
        r1 = 0.54 + hash2(i, 2, 88) * 0.12
        r2 = 0.84 + hash2(i, 3, 88) * 0.11
        a1 = ang + (hash2(i, 4, 88) - 0.5) * 0.24
        a2 = a1 + (hash2(i, 5, 88) - 0.5) * 0.30
        p0 = (math.cos(ang) * r0, math.sin(ang) * r0)
        p1 = (math.cos(a1) * r1, math.sin(a1) * r1)
        p2 = (math.cos(a2) * r2, math.sin(a2) * r2)
        cracks.append((p0, p1, 0.048))
        cracks.append((p1, p2, 0.028))
        if i % 2 == 0:
            af = a1 + (0.45 if i % 4 == 0 else -0.45)
            pf = (p1[0] + math.cos(af) * 0.25, p1[1] + math.sin(af) * 0.25)
            cracks.append((p1, pf, 0.022))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            n_big = fbm(qx * 3.8 + 4.0, qy * 3.8 + 2.0, 101, 3)
            n_fine = fbm(qx * 9.5, qy * 9.5, 211, 2)

            pool_r = 0.70 + 0.11 * (n_big - 0.5) + 0.05 * math.cos(qa * 7.0)
            core_a = 0.0
            core_h = 0.08

            min_cd = 9.9
            best_cw = 0.03
            for a, b, cw in cracks:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w_cur = cw * (1.0 - 0.45 * proj)
                if d / max(0.005, w_cur) < min_cd / max(0.005, best_cw):
                    min_cd = d
                    best_cw = w_cur

            if qr < pool_r:
                in_edge = smoothstep(pool_r, pool_r - 0.09, qr)
                # Stronger charred obsidian cracked earth slabs around mid/outer rim!
                crust_mask = smoothstep(0.28, 0.54, qr) * smoothstep(0.42, 0.60, n_big)
                boil = smoothstep(0.38, 0.78, n_fine) * (1.0 - 0.30 * qr)
                center_heat = math.exp(-(qr / 0.36) ** 2) * 0.45

                core_a = clamp(in_edge * 0.98)
                if crust_mask > 0.46:
                    core_h = clamp(0.03 + 0.10 * (1.0 - crust_mask) + 0.12 * n_fine)
                else:
                    core_h = clamp(0.34 + 0.38 * boil + center_heat)

                # Dark charred crater rim ridge
                rim_band = math.exp(-((qr - (pool_r - 0.05)) / 0.055) ** 2)
                if rim_band > 0.38 and min_cd > best_cw:
                    core_h = lerp(core_h, 0.05, rim_band * 0.85)

            if min_cd < best_cw * 2.4 and qr < 0.96:
                fissure_fade = smoothstep(0.96, 0.78, qr)
                if min_cd < best_cw:
                    in_f = 1.0 - min_cd / max(0.005, best_cw)
                    core_a = max(core_a, clamp(0.95 * fissure_fade))
                    core_h = max(core_h, clamp(0.58 + 0.42 * in_f))
                else:
                    scorch = (1.0 - (min_cd - best_cw) / (best_cw * 1.4)) * fissure_fade
                    if scorch > core_a:
                        core_a = max(core_a, scorch * 0.90)
                        core_h = 0.06

            glow_a = (
                math.exp(-(rr / 0.60) ** 2) * 0.50
                + math.exp(-((rr - 0.68) / 0.12) ** 2) * 0.30
            ) * smoothstep(1.0, 0.88, rr)
            glow_h = clamp(0.66 - 0.24 * rr)

            px[x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a, ramp=MAGMA_BLOOD_RAMP)

    return img


# --- 5. blood_sigil.png (128x128) --------------------------------------------

def make_blood_sigil() -> Image.Image:
    """Demonic Hellblood Demon sacrificial rune seal: thorny blood-fangs, broken rings & hexagram crest."""
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    tri_segs = []
    for phase in (0.0, math.pi / 3.0):
        pts = [(math.cos(phase + i * math.tau / 3.0) * 0.68, math.sin(phase + i * math.tau / 3.0) * 0.68) for i in range(3)]
        for i in range(3):
            tri_segs.append((pts[i], pts[(i + 1) % 3]))

    fang_segs = []
    for i in range(12):
        a = i * math.tau / 12.0
        tip_r = 0.96 if i % 2 == 0 else 0.88
        a_tip = a + 0.15
        fang_segs.append((
            (math.cos(a) * 0.74, math.sin(a) * 0.74),
            (math.cos(a_tip) * tip_r, math.sin(a_tip) * tip_r),
        ))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            qa = math.atan2(qy, qx)
            rr = math.hypot(rx, ry)

            n = fbm(qx * 5.0, qy * 5.0, 313, 2)
            core_a = 0.0
            core_h = 0.20

            for rad, thick, heat in ((0.76, 0.036, 0.74), (0.64, 0.024, 0.60), (0.36, 0.026, 0.80)):
                wobble = 0.015 * math.sin(qa * 9.0) + 0.012 * (n - 0.5)
                d = abs(qr - (rad + wobble))
                if d < thick * 1.8:
                    if d < thick:
                        core_a = max(core_a, 1.0 - smoothstep(thick * 0.35, thick, d))
                        core_h = max(core_h, heat + 0.22 * (1.0 - d / thick))
                    else:
                        oa = (1.0 - (d - thick) / (thick * 0.8)) * 0.82
                        if oa > core_a:
                            core_a = oa
                            core_h = 0.10

            if 0.655 < qr < 0.745:
                sec = ((qa + math.pi) / math.tau) * 18.0
                frac = sec - math.floor(sec)
                idx = int(math.floor(sec))
                if 0.20 < frac < 0.80 and hash2(idx, int((qr - 0.65) * 50), 177) > 0.36:
                    core_a = max(core_a, 0.90)
                    core_h = max(core_h, 0.84)

            for a, b in tri_segs:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                if d < 0.028:
                    val = 1.0 - smoothstep(0.010, 0.028, d)
                    core_a = max(core_a, val * 0.96)
                    core_h = max(core_h, 0.58 + 0.36 * val)

            for a, b in fang_segs:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w = 0.044 * (1.0 - 0.75 * proj)
                if d < w:
                    val = 1.0 - d / max(0.006, w)
                    core_a = max(core_a, clamp(val * 1.45))
                    core_h = max(core_h, clamp(0.24 + 0.58 * proj + 0.20 * val))

            dia = abs(qx) * 1.35 + abs(qy) * 0.75
            if dia < 0.22:
                core_a = max(core_a, 1.0 - smoothstep(0.14, 0.22, dia))
                core_h = max(core_h, 0.14 if abs(qx) < 0.045 else 0.86)

            if rr < 0.78:
                fog = (0.32 + 0.22 * n) * smoothstep(0.78, 0.26, rr)
                if fog > core_a:
                    core_a = max(core_a, fog)
                    core_h = lerp(core_h, 0.16, 0.65)

            glow_a = (
                math.exp(-((rr - 0.76) / 0.085) ** 2) * 0.50
                + math.exp(-((rr - 0.36) / 0.08) ** 2) * 0.34
                + math.exp(-(rr / 0.26) ** 2) * 0.38
            ) * smoothstep(1.0, 0.90, rr)
            glow_h = 0.64

            px[x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 6. blood_burst.png (512x64, 8 frames of 64x64) --------------------------

def make_blood_burst() -> Image.Image:
    """
    Brutal visceral blood-splatter detonation (NOT a regular geometric flower!):
    Asymmetrical exploding blood spears, jagged splatter tendrils, heavy dark-crimson clots & flash core.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    # Precompute 14 chaotic asymmetrical radial blood splatter spears
    spears = []
    for i in range(14):
        ang = i * (math.tau / 14.0) + (hash2(i, 3, 601) - 0.5) * 0.36
        length_mult = 0.62 + hash2(i, 7, 601) * 0.38
        width_mult = 0.065 + hash2(i, 11, 601) * 0.065
        spears.append((ang, length_mult, width_mult))

    for f in range(frames):
        t = f / (frames - 1)
        reach = 0.24 + 0.74 * (1.0 - (1.0 - t) ** 2.4)
        fade = smoothstep(0.0, 0.08, t + 0.08) * (1.0 - 0.74 * smoothstep(0.55, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                rr = math.hypot(rx, ry)
                n = fbm(qx * 6.0, qy * 6.0, f * 17 + 41, 2)

                core_a = 0.0
                core_h = 0.16

                # Jagged radiating blood-spear tendrils shooting outward
                for ang, lm, wm in spears:
                    r_start = max(0.0, reach * lm - 0.42 * (1.0 - 0.35 * t))
                    r_end = min(0.95, reach * lm)
                    ax, ay = math.cos(ang) * r_start, math.sin(ang) * r_start
                    bx, by = math.cos(ang) * r_end, math.sin(ang) * r_end
                    d, proj = seg_dist(qx, qy, ax, ay, bx, by)
                    # Bulbous droplet head at tip + thin stretched blood-string behind!
                    drop_w = wm * (0.45 + 0.85 * math.sin(proj * math.pi) + 0.40 * smoothstep(0.75, 1.0, proj)) * (1.0 - 0.40 * t)
                    if d < drop_w and drop_w > 0.01:
                        in_t = 1.0 - d / drop_w
                        a_val = clamp(smoothstep(0.0, 0.30, in_t) * fade * 1.30)
                        h_val = clamp(0.14 + 0.56 * in_t + 0.28 * (1.0 - proj) * (1.0 - 0.4 * t))
                        if a_val >= core_a * 0.85:
                            core_a = max(core_a, a_val)
                            core_h = max(core_h, h_val)

                # Inner turbulent crimson shock-ring ripping open
                ring_r = 0.18 + 0.64 * (1.0 - (1.0 - t) ** 2.0)
                ring_w = 0.22 * (1.0 - 0.55 * t)
                rd = abs(qr - (ring_r + (n - 0.5) * 0.16))
                if rd < ring_w and (f < 4 or n > (t - 0.38) * 1.15):
                    in_r = 1.0 - rd / max(0.02, ring_w)
                    ra = clamp(smoothstep(0.0, 0.35, in_r) * fade * 0.95)
                    if ra > core_a:
                        core_a = ra
                        core_h = clamp(0.18 + 0.62 * in_r * (1.0 - 0.30 * t))

                # Blinding jagged impact star-flash on frames 0..2
                if f <= 2:
                    fr = 0.44 - f * 0.10
                    if qr < fr:
                        in_c = 1.0 - qr / max(0.05, fr)
                        ca = clamp(smoothstep(0.0, 0.35, in_c) * (1.0 - f * 0.26))
                        core_a = max(core_a, ca)
                        core_h = max(core_h, clamp(0.62 + 0.38 * in_c))

                # Scattered heavy crimson blood-droplets flying outward
                if f >= 1 and 0.20 < qr < 0.97:
                    sp = hash2(x // 2, y // 2, f * 37 + 5)
                    if sp > 0.952:
                        core_a = max(core_a, 0.94 * fade)
                        core_h = max(core_h, 0.76 if sp > 0.980 else 0.34)

                # Smooth unclipped radial bloom
                glow_a = math.exp(-(rr / (0.34 + 0.34 * t)) ** 2) * 0.62 * fade * smoothstep(0.98, 0.76, rr)
                glow_h = clamp(0.70 - 0.28 * t)

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 7. blood_wave.png (512x64, 8 frames of 64x64) ---------------------------

def make_blood_wave() -> Image.Image:
    """Surging directional blood-tide crest with jagged crimson spray & dark-blood underbelly."""
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        crest_h = 0.30 + 0.62 * math.sin(clamp(t, 0.0, 1.0) * math.pi * 0.82)
        env = smoothstep(0.0, 0.12, t + 0.08) * (1.0 - 0.72 * smoothstep(0.65, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = 1.0 - quantize(y / size, 32)
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = 1.0 - (y + 0.5) / size

                arch = max(0.0, 1.0 - (qx * 0.96) ** 2) ** 0.68
                spikes = (
                    0.18 * abs(math.sin(qx * 6.5 + f * 0.7))
                    + 0.10 * math.cos(qx * 13.0 - f * 1.2)
                ) * arch
                n = fbm(qx * 5.0 + t * 2.2, qy * 5.0 - t * 2.5, 409, 2)
                local_top = crest_h * arch + spikes + (n - 0.45) * 0.16

                core_a = 0.0
                core_h = 0.18
                if qy < local_top and local_top > 0.04:
                    v_frac = qy / max(0.04, local_top)
                    dissolve = 1.0 if (t < 0.55 or n > (t - 0.50) * 1.1) else 0.18
                    base_fade = smoothstep(0.0, 0.08, qy)
                    core_a = clamp(base_fade * smoothstep(1.0, 0.82, v_frac) * env * dissolve * 1.32)
                    core_h = clamp(0.10 + 0.52 * v_frac + 0.40 * smoothstep(0.66, 0.98, v_frac))

                if local_top <= qy < min(0.96, local_top + 0.24) and abs(qx) < 0.88:
                    sp = hash2(x // 2, y // 2, f * 53 + 17)
                    if sp > 0.958:
                        core_a = max(core_a, 0.92 * env)
                        core_h = max(core_h, 0.82 if sp > 0.980 else 0.44)

                g_top = crest_h * max(0.0, 1.0 - rx * rx)
                glow_a = math.exp(-((ry - g_top * 0.6) / 0.26) ** 2) * smoothstep(1.0, 0.75, abs(rx)) * 0.48 * env
                glow_h = 0.60

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 8. blood_mist.png (256x64, 4 frames of 64x64) ---------------------------

def make_blood_mist() -> Image.Image:
    """Rich, thick billowing dark-crimson demonic blood aura clouds, rising wisps & glowing embers."""
    frames, size = 4, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f / float(frames) * math.tau
        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                qa = math.atan2(qy, qx)
                rr = math.hypot(rx, ry)

                sx = qx * 2.4 + math.cos(phase) * 0.65
                sy = qy * 2.4 + math.sin(phase) * 0.65
                n1 = fbm(sx + 5.0, sy + 5.0, 503, 3)
                n2 = fbm(qx * 4.5 - math.sin(phase) * 0.7, qy * 4.5 + math.cos(phase) * 0.7, 541, 2)
                swirl = 0.5 + 0.5 * math.sin(qa * 3.0 - qr * 3.8 + phase)
                billow = clamp(n1 * 0.62 + n2 * 0.28 + swirl * 0.24)

                radial_env = smoothstep(0.96, 0.24, qr)
                cloud = smoothstep(0.22, 0.72, billow) * radial_env

                core_a = clamp(cloud * 0.94)
                # Thick dark demonic crimson cloud with fiery scarlet veins
                core_h = clamp(0.07 + 0.52 * smoothstep(0.38, 0.84, billow) * (1.0 - 0.25 * qr))

                sp = hash2((x + f * 7) // 2, (y + f * 11) // 2, 577)
                if sp > 0.976 and qr < 0.85:
                    core_a = max(core_a, 0.96 * radial_env)
                    core_h = 0.88

                glow_a = math.exp(-(rr / 0.58) ** 2) * 0.45 * smoothstep(1.0, 0.82, rr)
                glow_h = 0.48

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 9. blood_sword_aura.png (256x128, 4 frames of 64x128) -------------------

def make_blood_sword_aura() -> Image.Image:
    """
    Iconic DNF 魔狱血刹 demonic serrated blood-greatsword sprite (4 looping flame-aura frames).
    Upward-swept horned demonic blood-fang base (no metal crossguard), brutal serrated crystalline blade,
    obsidian-black cracked spine, and billowing animated crimson blood-flames licking off the edges.
    """
    frames, w, h = 4, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f / float(frames) * math.tau
        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = 1.0 - quantize(y / h, 64)
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = 1.0 - (y + 0.5) / h

                fn = fbm(qx * 4.5, qy * 7.2 - f * 1.45, 613, 3)

                blade_w = 0.0
                is_blade = False
                is_fang = False
                if 0.03 <= qy < 0.18:
                    # Condensed dark blood-tendril grip with barbed pommel
                    blade_w = 0.16 if qy < 0.07 else 0.10 + 0.025 * math.sin(qy * 44.0)
                    is_blade = True
                elif 0.18 <= qy < 0.30:
                    # Upward-swept horned demonic blood-fang collar!
                    u = (qy - 0.18) / 0.12
                    blade_w = 0.18 + 0.48 * (u ** 0.72) * (1.0 - 0.15 * u)
                    is_fang = True
                    is_blade = True
                elif 0.30 <= qy <= 0.97:
                    u = (qy - 0.30) / 0.67
                    base_profile = (0.36 - 0.05 * u) * (1.0 - smoothstep(0.72, 1.0, u))
                    saw_l = 0.12 * max(0.0, math.sin(u * 21.0 + 0.5)) ** 1.3
                    saw_r = 0.11 * max(0.0, math.sin(u * 18.0 + 2.3)) ** 1.3
                    saw = (saw_l if qx < 0 else saw_r) * (1.0 - smoothstep(0.78, 1.0, u))
                    blade_w = base_profile + saw
                    is_blade = True

                # Carve hollow inner arch under the upward-swept horns to make them look like fierce fangs!
                if is_fang and abs(qx) > 0.24 and qy < 0.21 + (abs(qx) - 0.24) * 0.24:
                    is_blade = False

                core_a = 0.0
                core_h = 0.15

                if is_blade and abs(qx) <= blade_w and blade_w > 0.02:
                    edge_u = abs(qx) / max(0.02, blade_w)
                    core_a = 0.99
                    if qy < 0.18:
                        core_h = 0.12 + 0.28 * (1.0 - edge_u)
                    elif is_fang:
                        core_h = clamp(0.16 + 0.62 * edge_u + 0.18 * fn)
                    else:
                        spine_crack = math.sin(qy * 26.0 + qx * 11.0 + math.sin(phase) * 0.9)
                        if edge_u < 0.28:
                            core_h = 0.82 if spine_crack > 0.56 else 0.08
                        elif edge_u < 0.74:
                            core_h = clamp(0.32 + 0.38 * edge_u + 0.18 * fn)
                        else:
                            core_h = clamp(0.74 + 0.26 * ((edge_u - 0.74) / 0.26))

                # Dynamic billowing animated blood-flames licking aggressively off the blade!
                if 0.14 <= qy <= 0.99:
                    wave_lick = 0.10 * math.sin(qy * 14.0 - phase * 2.0 + (1.2 if qx > 0 else 0.0))
                    aura_reach = blade_w + (0.34 * fn + wave_lick) * smoothstep(0.14, 0.34, qy)
                    if blade_w < abs(qx) < aura_reach:
                        f_in = 1.0 - (abs(qx) - blade_w) / max(0.04, aura_reach - blade_w)
                        lick = smoothstep(0.28, 0.72, fn + f_in * 0.42)
                        fa = clamp(lick * f_in * 0.95)
                        if fa > core_a:
                            core_a = fa
                            core_h = clamp(0.18 + 0.62 * f_in)

                g_w = 0.40 * (1.0 - 0.55 * max(0.0, ry - 0.65) / 0.35)
                glow_a = math.exp(-(abs(rx) / max(0.10, g_w)) ** 2) * smoothstep(0.03, 0.20, ry) * smoothstep(1.0, 0.90, ry) * 0.58
                glow_h = 0.58

                px[f * w + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 10. blood_cross_rend.png (512x64, 8 frames of 64x64) --------------------

def make_blood_cross_rend() -> Image.Image:
    """Dramatic X-shaped demonic crimson blood cross-tear & impale flash."""
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        reach = 0.38 + 0.60 * smoothstep(0.0, 0.35, t)
        env = smoothstep(0.0, 0.10, t + 0.08) * (1.0 - 0.78 * smoothstep(0.45, 1.0, t))

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 6.0, qy * 6.0, f * 29 + 71, 2)
                core_a = 0.0
                core_h = 0.20

                for sx, sy in ((1.0, 1.0), (1.0, -1.0)):
                    d, proj = seg_dist(qx, qy, -sx * reach, -sy * reach, sx * reach, sy * reach)
                    mid_bulge = math.sin(proj * math.pi) ** 0.62
                    w = (0.20 * (1.0 - 0.45 * t) * mid_bulge) + 0.045 * (n - 0.45)
                    if d < w and w > 0.01:
                        in_t = 1.0 - d / w
                        a_val = clamp(smoothstep(0.0, 0.28, in_t) * env * 1.30)
                        h_val = clamp(0.16 + 0.64 * in_t + 0.28 * mid_bulge * (1.0 - 0.5 * t))
                        if a_val >= core_a * 0.8:
                            core_a = max(core_a, a_val)
                            core_h = max(core_h, h_val)

                qr = math.hypot(qx, qy)
                if f <= 3 and qr < 0.40:
                    cf = (1.0 - qr / 0.40) * (1.0 - f * 0.24)
                    core_a = max(core_a, clamp(cf * 1.25))
                    core_h = max(core_h, clamp(0.76 + 0.24 * cf))

                if f >= 2 and 0.22 < qr < 0.94:
                    sp = hash2(x // 2, y // 2, f * 43 + 19)
                    if sp > 0.964:
                        core_a = max(core_a, 0.90 * env)
                        core_h = max(core_h, 0.82)

                d1 = abs(rx - ry) * 0.7071
                d2 = abs(rx + ry) * 0.7071
                rr = math.hypot(rx, ry)
                glow_a = (
                    (math.exp(-(d1 / 0.16) ** 2) + math.exp(-(d2 / 0.16) ** 2)) * 0.45
                    + math.exp(-(rr / 0.34) ** 2) * 0.55 * (1.0 - 0.5 * t)
                ) * env * smoothstep(1.0, 0.82, rr)
                glow_h = clamp(0.76 - 0.24 * t)

                px[f * size + x, y] = pack_blood_pixel(core_h, core_a, glow_h, glow_a)

    return img


# --- 11. 32x32 Custom Demonic Blood Greatsword Held Item Texture -------------

def make_blood_sword_item() -> Image.Image:
    """
    32x32 pixel-art DNF Hellblood Demon condensed serrated blood greatsword (effect/blood_sword).
    Oriented diagonally from bottom-left hilt (3,28) to top-right razor tip (30,2) for item/handheld.
    """
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    ax, ay = 3.0, 29.0
    bx, by = 30.0, 2.0
    dx, dy = bx - ax, by - ay
    length = math.hypot(dx, dy)
    ux, uy = dx / length, dy / length
    nx, ny = -uy, ux

    for y in range(size):
        for x in range(size):
            px_c, py_c = x + 0.5, y + 0.5
            vx, vy = px_c - ax, py_c - ay
            along = (vx * ux + vy * uy) / length
            across = vx * nx + vy * ny

            if along < -0.04 or along > 1.05:
                continue

            half_w = 0.0
            role = "none"
            if -0.02 <= along < 0.18:
                pommel = 1.85 if along < 0.055 else 1.05
                half_w = pommel
                role = "grip"
            elif 0.18 <= along < 0.30:
                # Upward-swept horned demonic blood-fang collar!
                u = (along - 0.18) / 0.12
                fang = 5.0 * (u ** 0.72)
                half_w = 1.4 + fang * (1.10 if across > 0 else 0.94)
                role = "fang"
                # Carve hollow notch behind the swept horns
                if abs(across) > 2.4 and u < (abs(across) - 2.4) * 0.24:
                    role = "none"
            elif 0.30 <= along <= 1.00:
                u = (along - 0.30) / 0.70
                taper = 1.0 - smoothstep(0.70, 1.0, u)
                base_w = (3.6 - 0.45 * u) * taper
                tooth_pos = 1.45 * max(0.0, math.sin(u * 21.0 + 0.4)) ** 1.3
                tooth_neg = 1.35 * max(0.0, math.sin(u * 17.5 + 2.2)) ** 1.3
                tooth = (tooth_pos if across > 0 else tooth_neg) * (1.0 - smoothstep(0.75, 1.0, u))
                half_w = base_w + tooth
                role = "blade"

            if role != "none" and abs(across) <= half_w and half_w >= 0.45:
                edge_u = abs(across) / max(0.5, half_w)
                n = hash2(x, y, 809)

                if role == "grip":
                    c = sample_ramp(BLOOD_RAMP, 0.10 if edge_u > 0.55 else 0.36)
                    px[x, y] = (c[0], c[1], c[2], 255)
                elif role == "fang":
                    if edge_u > 0.80:
                        c = sample_ramp(BLOOD_RAMP, 0.88 if across > 0 else 0.76)
                    elif edge_u < 0.24:
                        c = sample_ramp(BLOOD_RAMP, 0.94)
                    else:
                        c = sample_ramp(BLOOD_RAMP, 0.24 + 0.46 * edge_u)
                    px[x, y] = (c[0], c[1], c[2], 255)
                else:
                    u = (along - 0.30) / 0.70
                    crack_pulse = math.sin(u * 18.0 + across * 0.9)
                    if edge_u < 0.26:
                        heat = 0.86 if crack_pulse > 0.44 else 0.08
                    elif edge_u < 0.70:
                        heat = clamp(0.34 + 0.38 * edge_u + (0.12 if n > 0.62 else -0.04))
                    else:
                        heat = clamp(0.78 + 0.22 * u + (0.08 if across > 0 else -0.03))
                    c = sample_ramp(BLOOD_RAMP, heat)
                    px[x, y] = (c[0], c[1], c[2], 255)
            else:
                if 0.24 <= along <= 1.03 and half_w + 0.7 < abs(across) < half_w + 2.5:
                    sp = hash2(x, y, 919)
                    if sp > 0.92:
                        c = sample_ramp(BLOOD_RAMP, 0.84 if sp > 0.96 else 0.58)
                        px[x, y] = (c[0], c[1], c[2], 225 if sp > 0.955 else 170)

    return img


def main() -> None:
    OUT_VFX.mkdir(parents=True, exist_ok=True)
    OUT_ITEM.mkdir(parents=True, exist_ok=True)

    vfx_assets = {
        "blood_slash.png": make_blood_slash(),
        "blood_claw.png": make_blood_claw(),
        "blood_geyser.png": make_blood_geyser(),
        "blood_crater.png": make_blood_crater(),
        "blood_sigil.png": make_blood_sigil(),
        "blood_burst.png": make_blood_burst(),
        "blood_wave.png": make_blood_wave(),
        "blood_mist.png": make_blood_mist(),
        "blood_sword_aura.png": make_blood_sword_aura(),
        "blood_cross_rend.png": make_blood_cross_rend(),
    }
    for name, img in vfx_assets.items():
        path = OUT_VFX / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    sword_img = make_blood_sword_item()
    sword_path = OUT_ITEM / "blood_sword.png"
    sword_img.save(sword_path, format="PNG")
    print(f"wrote {sword_path.relative_to(ROOT)} ({sword_img.width}x{sword_img.height})")


if __name__ == "__main__":
    main()

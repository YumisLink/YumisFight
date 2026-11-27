#!/usr/bin/env python3
"""
Dedicated DNF-inspired Weapon Master / Swordmaster (剑魂 / 剑圣) & Ghost Swordsman
pixel-sharp + soft-feathered VFX texture generator.

Outputs white/luminance-ramped RGBA sprite sheets under:
  src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/swordmaster/

All textures encode rich luminance contrast (darker outer silhouette -> bright plasma body ->
white-hot razor core) with high-alpha pixel-quantized cores and silhouette-hugging feathered glow.
This lets runtime renderers draw crisp dual-layer passes:
  - Blend.TRANSLUCENT tinted dark/deep for sharp daytime silhouette weight
  - Blend.ADDITIVE tinted with live skill/overdrive/elemental color + hot core boost!
"""

from __future__ import annotations

import math
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/swordmaster"
ICON_OUT = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/gui/skills"
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


def pack_tint_pixel(heat: float, core_a: float, glow_heat: float = 0.55,
                    glow_a: float = 0.0) -> tuple[int, int, int, int]:
    """
    White/steel luminance-ramped pixel pack for universal runtime vertex tinting:
      heat 0.0 -> crisp mid-dark metallic tone (128,136,150) for edge/shadow definition
      heat 0.5 -> clean bright luminance (212,218,228)
      heat 1.0 -> pure incandescent white (255,255,255)
    """
    ac = clamp(core_a)
    ag = clamp(glow_a)
    out_a = ac + ag * (1.0 - ac)
    if out_a <= 0.008:
        return (255, 255, 255, 0)

    def tone(h: float) -> tuple[float, float, float]:
        h = clamp(h)
        # Subtle cool steel tint in lower luminance, pure white at top so any color pops cleanly
        r = lerp(120.0, 255.0, h ** 0.85)
        g = lerp(132.0, 255.0, h ** 0.82)
        b = lerp(152.0, 255.0, h ** 0.72)
        return (r, g, b)

    cr, cg, cb = tone(heat)
    gr, gg, gb = tone(glow_heat)
    wc = ac / out_a
    wg = (ag * (1.0 - ac)) / out_a
    return (
        int(round(clamp(cr * wc + gr * wg, 0, 255))),
        int(round(clamp(cg * wc + gg * wg, 0, 255))),
        int(round(clamp(cb * wc + gb * wg, 0, 255))),
        int(round(clamp(out_a) * 255)),
    )


# --- 1. sword_slash.png (1024x128, 8 frames of 128x128) ----------------------

def make_sword_slash() -> Image.Image:
    """
    DNF Weapon Master crisp 230-degree sweeping blade crescent with razor leading edge,
    parallel wind-cut inner striations, and trailing pixel sword-qi sparks.
    Peak faces +qx (+forward), sweeps from -qy to +qy.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        head_ang = -2.25 + 4.45 * (1.0 - (1.0 - clamp(t / 0.58)) ** 2.2) if t < 0.58 else 2.20 + 0.32 * ((t - 0.58) / 0.42)
        tail_ang = -2.42 + 4.72 * smoothstep(0.20, 1.0, t)
        env = smoothstep(0.0, 0.12, t + 0.10) * (1.0 - 0.70 * smoothstep(0.62, 1.0, t))
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

                core_a = 0.0
                core_h = 0.35

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.18, arc_u) * smoothstep(1.0, 0.90, arc_u)
                    n = fbm(qx * 6.0, qy * 6.0, f * 17 + 5, 2)
                    # Sleek razor blade belly: sharper and faster-tapering than Berserker's heavy blood arc
                    thick = (0.06 + 0.30 * (arc_u ** 0.72) * (1.0 - 0.28 * arc_u)) * (0.78 + 0.25 * env)
                    center_r = 0.72 - 0.07 * (1.0 - arc_u) + 0.04 * t
                    outer_r = min(0.96, center_r + thick * 0.45)
                    inner_r = max(0.20, center_r - thick * 0.68)

                    if inner_r <= r <= outer_r:
                        rad_u = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        # Parallel wind-cut grooves inside trailing half of the blade crescent
                        grooves = 0.72 + 0.28 * math.sin(rad_u * math.pi * 5.0 + arc_u * 4.0)
                        wisps = 1.0 if (arc_u > 0.35 or n > 0.42 - arc_u * 0.8) else 0.28
                        if t > 0.65 and n < (t - 0.58) * 1.45:
                            wisps *= 0.22
                        profile = smoothstep(0.0, 0.18, rad_u) * smoothstep(1.0, 0.90, rad_u)
                        core_a = clamp(end_fade * env * wisps * profile * (0.78 + 0.35 * grooves) * 1.35)
                        razor = smoothstep(0.68, 0.96, rad_u) * (0.45 + 0.55 * (arc_u ** 0.8))
                        tip_flash = smoothstep(0.76, 0.97, arc_u) * smoothstep(0.50, 0.96, rad_u) * 0.45
                        core_h = clamp(0.18 + 0.44 * rad_u + 0.42 * razor + tip_flash)

                    # Secondary thin inner wind-cut echo line
                    echo_r = center_r - thick * 0.82
                    if echo_r > 0.18 and arc_u > 0.15:
                        de = abs(r - echo_r)
                        if de < 0.028:
                            ea = (1.0 - de / 0.028) * end_fade * env * 0.65 * smoothstep(0.15, 0.55, arc_u)
                            if ea > core_a:
                                core_a = ea
                                core_h = 0.78

                # Crisp flying sword-qi sparks along outer sweep
                if 0.44 < r < 0.97 and tail_ang - 0.30 < ang < head_ang + 0.15:
                    sp = hash2(x // 2, y // 2, f * 37 + 13)
                    if sp > 0.978:
                        core_a = max(core_a, 0.92 * env)
                        core_h = max(core_h, 0.96)

                # Tight arc-following glow
                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.72 - 0.07 * (1.0 - cu) + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.16) ** 2) * math.exp(-(ang_d / 0.18) ** 2)
                glow_a *= 0.62 * env * smoothstep(0.99, 0.88, rr) * (0.20 + 0.80 * cu)

                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.65 + 0.30 * cu, glow_a)

    return img


# --- 2. draw_ring.png (1024x128, 8 frames of 128x128) ------------------------

def make_draw_ring() -> Image.Image:
    """
    DNF 拔刀斩 (Draw Sword) full 360-degree horizontal iaido ring slash!
      Frames 0-1: Blinding high-speed whip sweeping full 360 deg around caster
      Frames 2-4: Razor-sharp dual concentric blade-ring with tangential wind-cut streaks
      Frames 5-7: Expanding and shattering into sleek razor wind lines & sparks
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        sweep_span = math.tau * smoothstep(0.0, 0.34, t + 0.12)
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.74 * smoothstep(0.56, 1.0, t))
        base_r = 0.68 + 0.22 * (1.0 - (1.0 - t) ** 2.1)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                qa = (math.atan2(qy, qx) + math.pi) % math.tau  # 0..2pi
                rr = math.hypot(rx, ry)
                ra = (math.atan2(ry, rx) + math.pi) % math.tau

                core_a = 0.0
                core_h = 0.30
                n = fbm(qx * 7.0, qy * 7.0, f * 19 + 3, 2)

                # Angular progress along full 360 sweep
                in_sweep = qa <= sweep_span + 0.15
                ang_fade = 1.0 if sweep_span >= math.tau * 0.98 else smoothstep(sweep_span + 0.15, max(0.0, sweep_span - 0.35), qa)

                if in_sweep:
                    # Primary wide razor outer blade ring
                    thick = (0.19 - 0.09 * t) * (0.85 + 0.15 * math.sin(qa * 3.0 + f))
                    outer_r = min(0.96, base_r + thick * 0.45)
                    inner_r = max(0.28, base_r - thick * 0.75)
                    if inner_r <= qr <= outer_r:
                        ru = clamp((qr - inner_r) / max(0.02, outer_r - inner_r))
                        breakup = 1.0 if (t < 0.55 or n > (t - 0.48) * 1.45) else 0.16
                        # Tangential speed streaks
                        streak = 0.76 + 0.24 * math.sin(qa * 18.0 + qr * 16.0 - t * 9.0)
                        core_a = clamp(smoothstep(0.0, 0.18, ru) * smoothstep(1.0, 0.88, ru) * ang_fade * env * breakup * streak * 1.38)
                        core_h = clamp(0.16 + 0.48 * ru + 0.42 * smoothstep(0.66, 0.96, ru))

                    # Secondary inner razor cut-ring
                    r2 = base_r - 0.18 - 0.04 * t
                    d2 = abs(qr - r2)
                    w2 = 0.042 * (1.0 - 0.50 * t)
                    if r2 > 0.15 and d2 < w2:
                        a2 = (1.0 - d2 / max(0.008, w2)) * ang_fade * env * 0.82
                        if a2 > core_a:
                            core_a = a2
                            core_h = 0.84

                    # Outer razor-thin shock edge whipping slightly ahead
                    r3 = min(0.97, base_r + 0.08 + 0.03 * t)
                    d3 = abs(qr - r3)
                    if d3 < 0.022 and (qa * 6.0 + f) % 2.0 < 1.35:
                        a3 = (1.0 - d3 / 0.022) * ang_fade * env * 0.90
                        if a3 > core_a:
                            core_a = a3
                            core_h = 0.96

                # High-velocity tangential sparks
                if 0.38 < qr < 0.98 and hash2(x // 2, y // 2, f * 43 + 17) > 0.976:
                    core_a = max(core_a, 0.90 * env * ang_fade)
                    core_h = 0.98

                g_fade = 1.0 if sweep_span >= math.tau * 0.98 else smoothstep(sweep_span + 0.3, max(0.0, sweep_span - 0.4), ra)
                glow_a = math.exp(-((rr - base_r) / 0.14) ** 2) * 0.62 * env * g_fade * smoothstep(0.99, 0.88, rr)
                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.75, glow_a)

    return img


# --- 3. iaido_flash.png (512x64, 8 frames of 64x64) --------------------------

def make_iaido_flash() -> Image.Image:
    """
    DNF 拔刀斩居合收刀星芒 & 光太二段光斩闪光 & 巨剑蓄满瞬闪!
    Crisp asymmetrical horizontal-dominant 4-point blade glint star + expanding diamond ring.
    """
    frames, size = 8, 64
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Rapid blinding flash peak at frames 2..4, crisp lingering horizontal gleam through frame 7
        flash = math.sin(clamp(t * 0.86 + 0.08) * math.pi) ** 0.65
        h_len = 0.42 + 0.56 * smoothstep(0.0, 0.36, t + 0.12)
        v_len = 0.22 + 0.46 * smoothstep(0.0, 0.40, t + 0.10)
        thick = 0.12 * (1.0 - 0.62 * t) + 0.028

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 32) - 0.5) * 2.0
                qy = (quantize(y / size, 32) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                qr = math.hypot(qx, qy)
                rr = math.hypot(rx, ry)
                core_a = 0.0
                core_h = 0.60

                # Dominant horizontal iaido blade gleam
                if abs(qx) < h_len:
                    hw = thick * (1.0 - abs(qx) / max(0.05, h_len)) ** 1.45
                    if abs(qy) < hw:
                        val = (1.0 - abs(qy) / max(0.01, hw)) * flash
                        core_a = max(core_a, clamp(val * 1.5))
                        core_h = max(core_h, clamp(0.55 + 0.45 * val))

                # Crisp vertical cross gleam
                if abs(qy) < v_len:
                    vw = thick * 0.82 * (1.0 - abs(qy) / max(0.05, v_len)) ** 1.45
                    if abs(qx) < vw:
                        val = (1.0 - abs(qx) / max(0.01, vw)) * flash
                        core_a = max(core_a, clamp(val * 1.4))
                        core_h = max(core_h, clamp(0.55 + 0.45 * val))

                # Subtle diagonal star needles on peak frames 1..4
                if 1 <= f <= 4:
                    d1 = abs(qx - qy) * 0.7071
                    d2 = abs(qx + qy) * 0.7071
                    diag_r = 0.36 * flash
                    if qr < diag_r:
                        dw = 0.035 * (1.0 - qr / max(0.05, diag_r))
                        dm = min(d1, d2)
                        if dm < dw:
                            core_a = max(core_a, (1.0 - dm / max(0.008, dw)) * flash * 0.9)
                            core_h = 0.96

                # Blinding diamond heart
                dia = abs(qx) + abs(qy)
                hr = 0.22 * (1.0 - 0.55 * t)
                if dia < hr:
                    core_a = max(core_a, clamp((1.0 - dia / max(0.04, hr)) * 1.3 * flash))
                    core_h = 1.0

                # Expanding crisp thin ring on frames 2..6
                if f >= 2:
                    rt = (f - 2) / 5.0
                    ring_r = 0.22 + 0.64 * EffectEaseOut(rt)
                    dr = abs(qr - ring_r)
                    if dr < 0.032:
                        core_a = max(core_a, (1.0 - dr / 0.032) * (1.0 - rt) * 0.85)
                        core_h = max(core_h, 0.88)

                glow_a = (
                    math.exp(-(rr / (0.32 + 0.12 * flash)) ** 2) * 0.70
                    + math.exp(-(abs(ry) / 0.11) ** 2) * math.exp(-(abs(rx) / 0.52) ** 2) * 0.52
                ) * flash * smoothstep(0.98, 0.78, max(abs(rx), abs(ry)))

                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.88, glow_a)

    return img


def EffectEaseOut(t: float) -> float:
    u = 1.0 - clamp(t)
    return 1.0 - u * u * u


# --- 4. dragon_head.png (1024x128, 8 frames of 128x128) ----------------------

def make_dragon_head() -> Image.Image:
    """
    DNF 猛龙断空斩 (Raging Dragon Slash) iconic roaring energy dragon head & wind-blade mane!
    Head faces +X (right), jaws open wide roaring forward, swept horns & streaming serrated
    wind-dragon mane whipping backward toward -X across 8 roaring frames.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        roar = 0.5 + 0.5 * math.sin(t * math.tau)
        jaw_open = 0.10 + 0.06 * roar

        # Dragon anatomy in [-1, 1]^2 (snout at +X, flowing wind mane trailing to -X)
        # Upper snout ridge -> brow -> swept horn
        upper_jaw = [
            ((0.86, -0.08 - jaw_open * 0.4), (0.58, -0.22), 0.11),
            ((0.58, -0.22), (0.22, -0.26), 0.14),
            ((0.22, -0.26), (-0.12, -0.18), 0.16),
            # Curved snout tip hook
            ((0.86, -0.08 - jaw_open * 0.4), (0.76, 0.04 - jaw_open * 0.3), 0.065),
        ]
        # Lower roaring jaw with chin beard
        lower_jaw = [
            ((0.72, 0.18 + jaw_open), (0.34, 0.28 + jaw_open * 0.6), 0.095),
            ((0.34, 0.28 + jaw_open * 0.6), (-0.06, 0.18), 0.12),
            ((0.72, 0.18 + jaw_open), (0.62, 0.06 + jaw_open * 0.8), 0.05),
        ]
        # Swept-back fierce dragon horns (twin antler curves!)
        horn_chains: list[list[tuple[tuple[float, float], tuple[float, float], float]]] = []
        for p0, p1, p2, bw in (
            ((0.18, -0.26), (-0.20, -0.68), (-0.72, -0.78 + 0.06 * math.sin(t * math.tau)), 0.09),
            ((0.04, -0.18), (-0.30, -0.48), (-0.82, -0.44 + 0.05 * math.cos(t * math.tau)), 0.07),
        ):
            chain = []
            prev = p0
            for s in range(1, 7):
                u = s / 6.0
                cur = quad_bezier(p0, p1, p2, u)
                chain.append((prev, cur, bw * (1.0 - 0.78 * u)))
                prev = cur
            horn_chains.append(chain)

        # Flowing serpentine wind-blade mane & energy whiskers trailing backward
        mane_chains: list[list[tuple[tuple[float, float], tuple[float, float], float]]] = []
        for i, y_off in enumerate((-0.24, -0.08, 0.10, 0.28, 0.44)):
            wave = 0.09 * math.sin(t * math.tau + i * 1.4)
            p0 = (-0.04, y_off * 0.72)
            p1 = (-0.48, y_off + wave)
            p2 = (-0.94, y_off * 1.15 - wave * 0.8)
            chain = []
            prev = p0
            for s in range(1, 7):
                u = s / 6.0
                cur = quad_bezier(p0, p1, p2, u)
                bw = (0.14 if i in (1, 2) else 0.095) * (1.0 - 0.76 * u)
                chain.append((prev, cur, bw))
                prev = cur
            mane_chains.append(chain)

        # Long whipping eastern-dragon mustache whisker
        whisker = []
        wp0 = (0.64, -0.04)
        wp1 = (0.18, 0.56 + 0.10 * math.sin(t * math.tau))
        wp2 = (-0.66, 0.72 - 0.10 * math.cos(t * math.tau))
        prev = wp0
        for s in range(1, 8):
            u = s / 7.0
            cur = quad_bezier(wp0, wp1, wp2, u)
            whisker.append((prev, cur, 0.042 * (1.0 - 0.65 * u)))
            prev = cur

        # Razor fangs inside jaws
        fangs = [
            ((0.68, -0.06 - jaw_open * 0.3), (0.63, 0.08 - jaw_open * 0.1), 0.036),
            ((0.46, -0.10 - jaw_open * 0.2), (0.42, 0.05), 0.034),
            ((0.56, 0.18 + jaw_open * 0.8), (0.52, 0.04 + jaw_open * 0.5), 0.034),
            ((0.36, 0.22 + jaw_open * 0.5), (0.33, 0.08 + jaw_open * 0.3), 0.030),
        ]

        eye_pos = (0.34, -0.14)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 5.5 - t * 3.0, qy * 5.5, 421, 2)
                core_a = 0.0
                core_h = 0.22
                min_d = 9.9

                # Cranium & neck core mass
                d_skull = math.hypot((qx - 0.08) * 1.15, (qy - 0.0) * 1.35)
                r_skull = 0.32 + 0.04 * (n - 0.5)
                min_d = min(min_d, max(0.0, d_skull - r_skull))
                if d_skull < r_skull:
                    in_s = 1.0 - d_skull / max(0.05, r_skull)
                    core_a = max(core_a, smoothstep(0.0, 0.28, in_s) * 0.96)
                    core_h = max(core_h, 0.24 + 0.52 * in_s)

                # Jaws & snout
                for segs, base_h in ((upper_jaw, 0.42), (lower_jaw, 0.36)):
                    for a, b, rad in segs:
                        d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                        re = rad + 0.02 * (n - 0.5)
                        min_d = min(min_d, max(0.0, d - re))
                        if d < re:
                            in_t = 1.0 - d / max(0.015, re)
                            a_val = clamp(smoothstep(0.0, 0.30, in_t) * 1.25)
                            h_val = clamp(base_h + 0.48 * in_t + 0.14 * (1.0 - proj))
                            if a_val >= core_a * 0.85:
                                core_a = max(core_a, a_val)
                                core_h = max(core_h, h_val)

                # Horns & wind mane & whisker
                for group, h_boost in ((horn_chains, 0.45), (mane_chains, 0.22), ([whisker], 0.55)):
                    for chain in group:
                        for idx, (a, b, rad) in enumerate(chain):
                            d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                            re = rad + 0.015 * (n - 0.5)
                            min_d = min(min_d, max(0.0, d - re))
                            if d < re and re > 0.008:
                                in_t = 1.0 - d / re
                                tail_fade = 1.0 - 0.45 * (idx / max(1, len(chain) - 1))
                                a_val = clamp(smoothstep(0.0, 0.30, in_t) * tail_fade * 1.2)
                                h_val = clamp(0.18 + h_boost * in_t + 0.35 * smoothstep(0.5, 1.0, in_t))
                                if a_val >= core_a * 0.82:
                                    core_a = max(core_a, a_val)
                                    core_h = max(core_h, h_val)

                # Razor fangs (incandescent white-hot!)
                for a, b, rad in fangs:
                    d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                    if d < rad:
                        in_t = 1.0 - d / rad
                        core_a = max(core_a, clamp(smoothstep(0.0, 0.25, in_t) * 1.3))
                        core_h = max(core_h, 0.90 + 0.10 * in_t)

                # Roaring energy breath core inside open jaws
                mouth_d = math.hypot((qx - 0.48) * 1.3, (qy - 0.04) * 1.8)
                if mouth_d < 0.22:
                    ma = (1.0 - mouth_d / 0.22) * (0.75 + 0.25 * roar)
                    core_a = max(core_a, ma)
                    core_h = max(core_h, 0.82 + 0.18 * ma)

                # Piercing fierce glowing dragon eye slit
                ex = (qx - eye_pos[0]) * 1.0 + (qy - eye_pos[1]) * 0.45
                ey = -(qx - eye_pos[0]) * 0.45 + (qy - eye_pos[1]) * 1.0
                ed = math.hypot(ex * 1.1, ey * 3.0)
                if ed < 0.10:
                    core_a = max(core_a, 1.0)
                    core_h = 1.0

                # Wind-cut sparks trailing off mane
                if qx < 0.4 and hash2((x + int(t * 16)) // 2, y // 2, f * 31 + 7) > 0.974 and min_d < 0.26:
                    core_a = max(core_a, 0.88)
                    core_h = 0.95

                glow_a = math.exp(-(min_d / 0.18) ** 2) * 0.58 * smoothstep(0.99, 0.84, max(abs(rx), abs(ry)))
                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.72, glow_a)

    return img


# --- 5. gale_cone.png (512x128, 8 frames of 64x128) --------------------------

def make_gale_cone() -> Image.Image:
    """
    DNF 破军升龙击 (Charge Crash) & 三段斩/猛龙 aerodynamic forward Mach wind-cone!
    v=0 is spear tip (+forward), v=1 is wide trailing wind skirt (-forward).
    """
    frames, w, h = 8, 64, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        scroll = t * 5.0
        env = smoothstep(0.0, 0.12, t + 0.12) * (1.0 - 0.62 * smoothstep(0.66, 1.0, t))

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 32) - 0.5) * 2.0
                qy = quantize(y / h, 64)  # 0 at front spear tip, 1 at trailing skirt
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = (y + 0.5) / h

                # Sharp needle-pointed hypersonic wind-lance cone (no flat top!)
                cone_w = 0.015 + 0.88 * (qy ** 0.82)
                n = fbm(qx * 5.5, qy * 7.5 - scroll, f * 11 + 23, 2)
                core_a = 0.0
                core_h = 0.28

                # Swept parabolic Mach-cone shell + spiraling hurricane wind rings
                if abs(qx) <= cone_w + 0.08 and qy > 0.02:
                    edge_d = abs(abs(qx) - cone_w)
                    shell_w = 0.15 * (0.45 + 0.55 * qy)
                    shell = (1.0 - smoothstep(0.015, shell_w, edge_d)) * smoothstep(0.02, 0.12, qy)
                    # Curved backward-sweeping hurricane wind-collar arcs wrapping the cone!
                    collar_phase = qy * 13.0 - abs(qx) * 3.8 - scroll * 2.6
                    collar = (max(0.0, math.sin(collar_phase)) ** 4) * smoothstep(cone_w + 0.06, cone_w * 0.35, abs(qx)) * smoothstep(0.12, 0.30, qy) * 0.92
                    # Fierce central spear spine cutting forward
                    spine_w = 0.12 * (1.0 - 0.65 * qy) + 0.025
                    spine = (1.0 - smoothstep(0.01, spine_w, abs(qx))) * smoothstep(0.92, 0.06, qy) * 0.95
                    # Aerodynamic slipstream interior mist
                    mist = smoothstep(cone_w + 0.02, cone_w * 0.15, abs(qx)) * (0.28 + 0.24 * n) * smoothstep(0.98, 0.18, qy)

                    tail_fade = smoothstep(1.0, 0.58, qy + (n - 0.5) * 0.24)
                    tip_fade = smoothstep(0.015, 0.07, qy)
                    val = max(shell * 0.95, collar, spine, mist)
                    core_a = clamp(val * tail_fade * tip_fade * env * 1.32)
                    core_h = clamp(0.18 + 0.45 * shell + 0.48 * spine + 0.34 * collar + 0.26 * smoothstep(0.24, 0.03, qy))

                # Blinding piercing spearhead tip flare
                nose_d = math.hypot(qx * 2.2, (qy - 0.07) * 4.2)
                if nose_d < 0.22:
                    na = (1.0 - nose_d / 0.22) * env
                    core_a = max(core_a, na)
                    core_h = max(core_h, 0.94 + 0.06 * na)

                # Smooth slipstream bloom
                g_w = 0.03 + 0.88 * (ry ** 0.82)
                glow_a = (
                    math.exp(-((abs(rx) - g_w * 0.82) / 0.22) ** 2) * 0.45
                    + math.exp(-(abs(rx) / 0.18) ** 2) * smoothstep(0.75, 0.05, ry) * 0.40
                ) * smoothstep(1.0, 0.62, ry) * smoothstep(0.0, 0.07, ry) * env * smoothstep(1.0, 0.86, abs(rx))

                px[f * w + x, y] = pack_tint_pixel(core_h, core_a, 0.76, clamp(glow_a))

    return img


# --- 6. rising_arc.png (1024x128, 8 frames of 128x128) -----------------------

def make_rising_arc() -> Image.Image:
    """
    DNF 破军升龙击 (Rising Dragon Uppercut) towering upward-sweeping crescent arc!
    Sweeps violently from lower-left upward to top-right sky crest with ascending wind gusts.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        # Sweeps from bottom (+1.7 rad) counterclockwise across front (+X, 0 rad) up to sky (-1.9 rad)
        head_ang = 1.65 - 3.65 * (1.0 - (1.0 - clamp(t / 0.62)) ** 2.1) if t < 0.62 else -2.00 - 0.25 * ((t - 0.62) / 0.38)
        tail_ang = 1.85 - 3.90 * smoothstep(0.22, 1.0, t)
        env = smoothstep(0.0, 0.10, t + 0.10) * (1.0 - 0.68 * smoothstep(0.62, 1.0, t))
        span = max(0.55, tail_ang - head_ang)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                r = math.hypot(qx, qy)
                ang = math.atan2(qy, qx)
                arc_u = (tail_ang - ang) / span  # 0 at bottom tail, 1 at soaring top head

                core_a = 0.0
                core_h = 0.28
                n = fbm(qx * 5.5, qy * 5.5, f * 23 + 17, 2)

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.88, arc_u)
                    # Expanding rising spiral radius: flares outward as it soars upward!
                    center_r = 0.56 + 0.22 * arc_u + 0.04 * t
                    thick = (0.10 + 0.32 * (arc_u ** 0.68) * (1.0 - 0.18 * arc_u)) * (0.82 + 0.22 * env)
                    outer_r = min(0.96, center_r + thick * 0.48)
                    inner_r = max(0.14, center_r - thick * 0.64)
                    if inner_r <= r <= outer_r:
                        ru = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        wisps = 1.0 if (arc_u > 0.30 or n > 0.45 - arc_u) else 0.24
                        if t > 0.64 and n < (t - 0.56) * 1.45:
                            wisps *= 0.20
                        stripes = 0.78 + 0.22 * math.sin(ru * math.pi * 6.0 - arc_u * 5.0)
                        core_a = clamp(end_fade * env * wisps * smoothstep(0.0, 0.18, ru) * smoothstep(1.0, 0.88, ru) * stripes * 1.38)
                        core_h = clamp(0.16 + 0.46 * ru + 0.44 * smoothstep(0.64, 0.96, ru) + 0.22 * smoothstep(0.75, 0.96, arc_u))

                if 0.38 < r < 0.96 and head_ang - 0.20 < ang < tail_ang + 0.20:
                    if hash2(x // 2, y // 2, f * 41 + 3) > 0.975:
                        core_a = max(core_a, 0.92 * env)
                        core_h = 0.98

                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (tail_ang - ra) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (ra - tail_ang if r_arc < 0.0 else head_ang - ra)
                g_r = 0.56 + 0.22 * cu + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.18) ** 2) * math.exp(-(ang_d / 0.20) ** 2) * 0.60 * env * smoothstep(0.99, 0.86, rr)

                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.72, glow_a)

    return img


# --- 7. sword_wave.png (1024x128, 8 frames of 128x128) -----------------------

def make_sword_wave() -> Image.Image:
    """
    DNF 幻影剑舞终结巨型剑气浪 & 短剑剑气 (MASTERY_WAVE) & 地裂波动剑 flying crescent wave!
    Faces +X (forward), thick razor-sharp stacked triple energy wave crests with trailing slipstream.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        pulse = math.sin(t * math.tau)

        for y in range(size):
            for x in range(size):
                qx = (quantize(x / size, 64) - 0.5) * 2.0
                qy = (quantize(y / size, 64) - 0.5) * 2.0
                rx = (x + 0.5) / size * 2.0 - 1.0
                ry = (y + 0.5) / size * 2.0 - 1.0

                n = fbm(qx * 6.0 - t * 4.0, qy * 6.0, 509, 2)
                core_a = 0.0
                core_h = 0.25

                # Parabolic crescent bow facing +X: front crest at x = 0.78 - 1.05 * y^2
                y_span = abs(qy)
                if y_span < 0.94:
                    bow = max(0.0, 1.0 - (y_span / 0.94) ** 2)
                    front_x = 0.80 - 1.12 * (y_span ** 2) + 0.03 * pulse * bow
                    thick = 0.42 * (bow ** 0.72) + 0.06
                    back_x = front_x - thick

                    if back_x <= qx <= front_x:
                        u = clamp((qx - back_x) / max(0.02, thick))  # 0 at trailing wake, 1 at razor leading edge
                        # Stacked double-crest sword-qi wave texture
                        rib = 0.75 + 0.25 * math.sin(u * math.pi * 4.0 - t * math.tau)
                        tail_break = 1.0 if (u > 0.38 or n > 0.44 - u * 0.9) else 0.26
                        a_val = smoothstep(0.0, 0.22, u) * smoothstep(1.0, 0.88, u) * smoothstep(0.94, 0.72, y_span) * tail_break * rib * 1.38
                        core_a = clamp(a_val)
                        razor = smoothstep(0.66, 0.97, u)
                        core_h = clamp(0.18 + 0.48 * u + 0.44 * razor)

                    # Secondary smaller trailing shock crescent behind main wave
                    front2 = front_x - thick * 0.92
                    thick2 = thick * 0.42
                    if front2 - thick2 <= qx <= front2 and y_span < 0.76:
                        u2 = clamp((qx - (front2 - thick2)) / max(0.02, thick2))
                        a2 = smoothstep(0.0, 0.25, u2) * smoothstep(1.0, 0.85, u2) * smoothstep(0.76, 0.50, y_span) * 0.72
                        if a2 > core_a:
                            core_a = a2
                            core_h = clamp(0.32 + 0.54 * u2)

                    # Trailing horizontal speed streaks
                    if back_x - 0.48 < qx < back_x + 0.05 and y_span < 0.82:
                        st = max(0.0, math.sin(qy * 28.0 + f * 1.3)) ** 8
                        fade_x = smoothstep(back_x - 0.48, back_x, qx)
                        sa = st * fade_x * 0.65 * (0.5 + 0.5 * n)
                        if sa > core_a:
                            core_a = sa
                            core_h = 0.82

                # Sparks trailing off wing tips
                if abs(qy) > 0.35 and qx < 0.45 and hash2((x + f * 5) // 2, y // 2, 613) > 0.978:
                    core_a = max(core_a, 0.88)
                    core_h = 0.96

                # Smooth bow-hugging glow
                rbow = 0.80 - 1.12 * (ry ** 2)
                g_dx = rx - (rbow - 0.16 * max(0.0, 1.0 - ry * ry))
                glow_a = math.exp(-(g_dx / 0.24) ** 2) * smoothstep(0.98, 0.74, abs(ry)) * 0.60 * smoothstep(0.99, 0.86, abs(rx))
                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.74, glow_a)

    return img


# --- 8. ground_fissure.png (128x128) -----------------------------------------

def make_ground_fissure() -> Image.Image:
    """
    DNF 破军/地裂波动剑/钝器冲击地面裂纹贴图:
    Radiating jagged fracture faults + crisp central impact crater ring, white/luminance tinted.
    """
    size = 128
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    cracks: list[tuple[tuple[float, float], tuple[float, float], float]] = []
    for i in range(10):
        ang = i * (math.tau / 10.0) + (hash2(i, 2, 701) - 0.5) * 0.28
        r0 = 0.10
        r1 = 0.48 + hash2(i, 3, 701) * 0.14
        r2 = 0.82 + hash2(i, 4, 701) * 0.12
        a1 = ang + (hash2(i, 5, 701) - 0.5) * 0.26
        a2 = a1 + (hash2(i, 6, 701) - 0.5) * 0.34
        p0 = (math.cos(ang) * r0, math.sin(ang) * r0)
        p1 = (math.cos(a1) * r1, math.sin(a1) * r1)
        p2 = (math.cos(a2) * r2, math.sin(a2) * r2)
        cracks.append((p0, p1, 0.044))
        cracks.append((p1, p2, 0.025))
        if i % 2 == 0:
            af = a1 + (0.48 if i % 4 == 0 else -0.48)
            cracks.append((p1, (p1[0] + math.cos(af) * 0.24, p1[1] + math.sin(af) * 0.24), 0.020))

    for y in range(size):
        for x in range(size):
            qx = (quantize(x / size, 64) - 0.5) * 2.0
            qy = (quantize(y / size, 64) - 0.5) * 2.0
            rx = (x + 0.5) / size * 2.0 - 1.0
            ry = (y + 0.5) / size * 2.0 - 1.0

            qr = math.hypot(qx, qy)
            rr = math.hypot(rx, ry)
            n = fbm(qx * 6.0, qy * 6.0, 733, 2)

            core_a = 0.0
            core_h = 0.18

            # Jagged cracked impact ring
            ring_d = abs(qr - (0.36 + 0.06 * (n - 0.5)))
            if ring_d < 0.055:
                in_r = 1.0 - ring_d / 0.055
                core_a = max(core_a, smoothstep(0.0, 0.35, in_r) * 0.85)
                core_h = max(core_h, 0.38 + 0.52 * in_r)

            # Radiating fracture faults with dark rim + glowing center seam
            min_ratio = 9.9
            best_d = 9.9
            best_w = 0.03
            for a, b, cw in cracks:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w_cur = cw * (1.0 - 0.45 * proj)
                ratio = d / max(0.005, w_cur)
                if ratio < min_ratio:
                    min_ratio = ratio
                    best_d = d
                    best_w = w_cur

            if best_d < best_w * 2.1 and qr < 0.95:
                fade = smoothstep(0.95, 0.74, qr)
                if best_d < best_w:
                    in_f = 1.0 - best_d / max(0.004, best_w)
                    core_a = max(core_a, clamp(0.96 * fade))
                    core_h = max(core_h, clamp(0.45 + 0.55 * in_f))
                else:
                    rim = (1.0 - (best_d - best_w) / (best_w * 1.1)) * fade
                    if rim > core_a:
                        core_a = rim * 0.82
                        core_h = 0.10

            # Center impact burst core
            if qr < 0.24:
                c_val = 1.0 - qr / 0.24
                core_a = max(core_a, smoothstep(0.0, 0.4, c_val) * 0.90)
                core_h = max(core_h, 0.50 + 0.50 * c_val)

            glow_a = (
                math.exp(-(rr / 0.48) ** 2) * 0.46
                + math.exp(-((rr - 0.36) / 0.12) ** 2) * 0.24
            ) * smoothstep(0.99, 0.84, rr)
            px[x, y] = pack_tint_pixel(core_h, core_a, 0.70, glow_a)

    return img


# --- 9. overdrive_aura.png (512x128, 4 looping frames of 128x128) ------------

def make_overdrive_aura() -> Image.Image:
    """
    DNF 破极兵刃 (Overdrive) blazing golden sword-aura flame sheath & foot-vortex plume!
    Seamlessly loops across 4 frames. v=1 base/hilt -> v=0 sharp flickering sword-qi tip.
    """
    frames, w, h = 4, 128, 128
    img = Image.new("RGBA", (w * frames, h), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        phase = f / frames * math.tau

        for y in range(h):
            for x in range(w):
                qx = (quantize(x / w, 48) - 0.5) * 2.0
                qy = 1.0 - quantize(y / h, 48)  # 0 at hilt/ground base, 1 at soaring tip
                rx = (x + 0.5) / w * 2.0 - 1.0
                ry = 1.0 - (y + 0.5) / h

                # Periodic scrolling noise via circular phase sampling so frames 0..3 loop seamlessly!
                n1 = fbm(qx * 4.2 + math.cos(phase) * 0.55, qy * 5.0 - f * 1.25, 811, 3)
                n2 = fbm(qx * 8.0 + math.sin(phase) * 0.55, qy * 8.5 - f * 2.0, 829, 2)

                # Main sharp flame-blade silhouette + two twin licking aura tongues
                sway = 0.08 * math.sin(qy * 7.5 - phase)
                body_w = (0.48 * (1.0 - 0.72 * (qy ** 1.25)) + 0.14 * math.exp(-((qy / 0.22) ** 2)))
                lick1_x = -0.26 * (1.0 - 0.5 * qy) + 0.07 * math.sin(qy * 10.0 - phase + 1.1)
                lick2_x = 0.26 * (1.0 - 0.5 * qy) + 0.07 * math.cos(qy * 10.0 - phase - 0.8)
                lick_w = 0.19 * smoothstep(0.86, 0.08, qy)

                jag = (n1 - 0.46) * 0.26 + (n2 - 0.50) * 0.12
                in_main = 1.0 - abs(qx - sway) / max(0.04, body_w + jag)
                in_l1 = 1.0 - abs(qx - lick1_x) / max(0.03, lick_w + jag * 0.6)
                in_l2 = 1.0 - abs(qx - lick2_x) / max(0.03, lick_w + jag * 0.6)
                in_f = max(in_main, in_l1 * 0.88, in_l2 * 0.88)

                core_a = 0.0
                core_h = 0.25
                if in_f > 0.0 and qy < 0.96:
                    bf = smoothstep(0.0, 0.08, qy)
                    tf = smoothstep(0.96, 0.72, qy)
                    # Sharp vertical sword-qi striations inside flame
                    striation = 0.82 + 0.18 * math.sin(qx * 20.0 + qy * 8.0 - phase)
                    core_a = clamp(smoothstep(0.0, 0.28, in_f) * bf * tf * striation * 1.32)
                    core_h = clamp(0.20 + 0.52 * in_f + 0.34 * smoothstep(0.42, 0.92, in_f) + 0.18 * math.exp(-((qy / 0.26) ** 2)))

                # Rising golden sparks
                if 0.10 < qy < 0.96 and abs(qx) < 0.82:
                    sp = hash2(x // 2, (y + f * 12) // 2, 853)
                    if sp > 0.978:
                        core_a = max(core_a, 0.94)
                        core_h = 1.0

                glow_a = math.exp(-(abs(rx) / (0.46 * (1.0 - 0.45 * ry) + 0.12)) ** 2) * smoothstep(0.0, 0.08, ry) * smoothstep(0.98, 0.72, ry) * 0.56 * smoothstep(0.99, 0.82, abs(rx))
                px[f * w + x, y] = pack_tint_pixel(core_h, core_a, 0.78, glow_a)

    return img


# --- 10. sword_formation.png (256x256) ---------------------------------------

def make_sword_formation() -> Image.Image:
    """
    DNF 极鬼剑术·暴风式 (Tempest / Storm) & 破极/拔刀 Swordmaster 256x256 Sword-Array Magic Seal!
    12 radial sword emblems, sharp beveled concentric rings, wind-rune arc segments & 4 cardinal blades.
    """
    size = 256
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()

    # 12 radial sword-blade silhouettes pointing outward around the ring
    blades: list[tuple[tuple[float, float], tuple[float, float], float]] = []
    for i in range(12):
        a = i * math.tau / 12.0
        cardinal = (i % 3 == 0)
        r_in = 0.42 if cardinal else 0.54
        r_out = 0.95 if cardinal else 0.86
        w_b = 0.036 if cardinal else 0.024
        blades.append(((math.cos(a) * r_in, math.sin(a) * r_in), (math.cos(a) * r_out, math.sin(a) * r_out), w_b))
        # Tiny crossguard on the 4 cardinal blades
        if cardinal:
            ga = a + math.pi * 0.5
            gc = (math.cos(a) * 0.52, math.sin(a) * 0.52)
            blades.append((
                (gc[0] - math.cos(ga) * 0.08, gc[1] - math.sin(ga) * 0.08),
                (gc[0] + math.cos(ga) * 0.08, gc[1] + math.sin(ga) * 0.08),
                0.022,
            ))

    # Octagonal intersecting blade-frame lines
    frame_segs = []
    for phase in (0.0, math.pi / 4.0):
        pts = [(math.cos(phase + i * math.pi * 0.5) * 0.74, math.sin(phase + i * math.pi * 0.5) * 0.74) for i in range(4)]
        for i in range(4):
            frame_segs.append((pts[i], pts[(i + 1) % 4]))

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
            core_h = 0.30

            # Concentric precision rings
            for rad, thick, heat in ((0.90, 0.022, 0.88), (0.76, 0.018, 0.78), (0.50, 0.016, 0.82), (0.20, 0.014, 0.90)):
                d = abs(qr - rad)
                if d < thick * 1.5:
                    if d < thick:
                        v = 1.0 - smoothstep(thick * 0.35, thick, d)
                        core_a = max(core_a, v)
                        core_h = max(core_h, heat + 0.14 * v)
                    else:
                        oa = (1.0 - (d - thick) / (thick * 0.5)) * 0.65
                        if oa > core_a:
                            core_a = oa
                            core_h = 0.16

            # Outer dashed wind-arc segments between 0.79 and 0.87
            if 0.79 < qr < 0.87:
                sec = ((qa + math.pi) / math.tau) * 24.0
                frac = sec - math.floor(sec)
                idx = int(math.floor(sec))
                if 0.18 < frac < 0.82 and (idx % 2 == 0 or abs(qr - 0.83) < 0.014):
                    core_a = max(core_a, 0.86)
                    core_h = max(core_h, 0.82)

            # Intersecting octagonal sword-frame lines
            for a, b in frame_segs:
                d, _ = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                if d < 0.018:
                    v = 1.0 - smoothstep(0.006, 0.018, d)
                    core_a = max(core_a, v * 0.92)
                    core_h = max(core_h, 0.66 + 0.30 * v)

            # 12 radial sword blades
            for a, b, wb in blades:
                d, proj = seg_dist(qx, qy, a[0], a[1], b[0], b[1])
                w_cur = wb * (1.0 - 0.65 * (proj ** 1.4))
                if d < w_cur and w_cur > 0.004:
                    v = 1.0 - d / w_cur
                    core_a = max(core_a, clamp(v * 1.5))
                    core_h = max(core_h, clamp(0.45 + 0.55 * v))

            # Central 4-point sword-crest diamond
            dia = abs(qx) + abs(qy)
            if dia < 0.16:
                v = 1.0 - smoothstep(0.08, 0.16, dia)
                core_a = max(core_a, v * 0.94)
                core_h = max(core_h, 0.88 + 0.12 * v)

            glow_a = (
                math.exp(-((rr - 0.90) / 0.065) ** 2) * 0.46
                + math.exp(-((rr - 0.76) / 0.055) ** 2) * 0.34
                + math.exp(-((rr - 0.50) / 0.060) ** 2) * 0.28
                + math.exp(-(rr / 0.30) ** 2) * 0.28
            ) * smoothstep(0.99, 0.90, rr)
            if rr < 0.90:
                glow_a += 0.08 * smoothstep(0.90, 0.22, rr)

            px[x, y] = pack_tint_pixel(core_h, core_a, 0.75, clamp(glow_a))

    return img


# --- 11. ghost_crescent.png (1024x128, 8 frames of 128x128) ------------------

def make_ghost_crescent() -> Image.Image:
    """
    DNF 鬼斩 (Ghost Slash) sinister ragged spectral-flame sweeping crescent!
    Wider, heavier dark-aura crescent with curling ghostly flame wisps licking off the outer rim.
    """
    frames, size = 8, 128
    img = Image.new("RGBA", (size * frames, size), (0, 0, 0, 0))
    px = img.load()

    for f in range(frames):
        t = f / (frames - 1)
        head_ang = -2.15 + 4.30 * (1.0 - (1.0 - clamp(t / 0.62)) ** 2.0) if t < 0.62 else 2.15 + 0.30 * ((t - 0.62) / 0.38)
        tail_ang = -2.35 + 4.55 * smoothstep(0.22, 1.0, t)
        env = smoothstep(0.0, 0.12, t + 0.10) * (1.0 - 0.68 * smoothstep(0.62, 1.0, t))
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
                n = fbm(qx * 5.2, qy * 5.2, f * 29 + 11, 3)

                core_a = 0.0
                core_h = 0.14

                if 0.0 < arc_u < 1.0:
                    end_fade = smoothstep(0.0, 0.16, arc_u) * smoothstep(1.0, 0.88, arc_u)
                    flame_lick = (0.10 * math.sin(ang * 9.0 - f * 0.9) + 0.06 * (n - 0.45)) * arc_u
                    thick = (0.14 + 0.36 * (arc_u ** 0.65) * (1.0 - 0.18 * arc_u)) * (0.80 + 0.22 * env)
                    center_r = 0.64 - 0.05 * (1.0 - arc_u) + 0.04 * t
                    outer_r = min(0.96, center_r + thick * 0.52 + flame_lick)
                    inner_r = max(0.14, center_r - thick * 0.66 - 0.06 * (n - 0.45))

                    if inner_r <= r <= outer_r:
                        ru = clamp((r - inner_r) / max(0.02, outer_r - inner_r))
                        tear = 1.0 if (arc_u > 0.38 or n > 0.48 - arc_u * 0.9) else 0.22
                        if t > 0.66 and n < (t - 0.58) * 1.4:
                            tear *= 0.18
                        core_a = clamp(end_fade * env * tear * smoothstep(0.0, 0.18, ru) * smoothstep(1.0, 0.86, ru) * 1.38)
                        # Heavy shadowy spectral outer rim + vivid razor middle + blinding front core!
                        razor = smoothstep(0.56, 0.90, ru) * (0.45 + 0.55 * arc_u)
                        core_h = clamp(0.06 + 0.44 * ru + 0.50 * razor - 0.15 * smoothstep(0.88, 1.0, ru))

                if 0.34 < r < 0.97 and tail_ang - 0.35 < ang < head_ang + 0.20:
                    sp = hash2(x // 2, y // 2, f * 47 + 19)
                    if sp > 0.968:
                        core_a = max(core_a, 0.90 * env)
                        core_h = max(core_h, 0.88 if sp > 0.985 else 0.30)

                rr = math.hypot(rx, ry)
                ra = math.atan2(ry, rx)
                r_arc = (ra - tail_ang) / span
                cu = clamp(r_arc, 0.0, 1.0)
                ang_d = 0.0 if 0.0 <= r_arc <= 1.0 else (tail_ang - ra if r_arc < 0.0 else ra - head_ang)
                g_r = 0.64 - 0.05 * (1.0 - cu) + 0.04 * t
                glow_a = math.exp(-((rr - g_r) / 0.22) ** 2) * math.exp(-(ang_d / 0.22) ** 2) * 0.60 * env * smoothstep(0.99, 0.86, rr)
                px[f * size + x, y] = pack_tint_pixel(core_h, core_a, 0.64, glow_a)

    return img


# --- Contact Sheet Preview Generator -----------------------------------------

def save_contact_sheet(assets: dict[str, Image.Image], out_path: Path, bg=(14, 18, 28, 255)) -> None:
    """Render a clean labeled dark-backdrop contact sheet in temp/ for visual verification."""
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
        draw.text((pad, y_cur), f"{name} ({img.width}x{img.height})", fill=(180, 232, 255, 255))
        y_cur += label_h
        # Draw subtle frame boundary box
        disp = img if img.width <= 1024 else img.resize((1024, int(img.height * 1024 / img.width)), Image.NEAREST)
        draw.rectangle([pad - 1, y_cur - 1, pad + disp.width, y_cur + disp.height], outline=(48, 64, 92, 255))
        sheet.alpha_composite(disp, (pad, y_cur))
        y_cur += disp.height + pad

    sheet.save(out_path, format="PNG")
    print(f"wrote preview {out_path.relative_to(ROOT)} ({sheet.width}x{sheet.height})")


# --- Meteor / Flowing Stance Skill Icons (64x64 crisp 32x32-grid pixel art) --

def _draw_meteor_icon(kind: str) -> Image.Image:
    """Draw a crisp DNF-style 32x32 pixel-art Flowing Stance icon scaled cleanly to 64x64."""
    g = 32
    img = Image.new("RGBA", (g, g), (0, 0, 0, 255))
    px = img.load()

    # Palette tailored to Swordmaster's Flowing Stance (流心):
    # rise = ice-cyan & sky-white soaring crescent
    # leap = royal-azure & bright-gold plunging meteor arc
    # pierce = electric-aqua & white-hot triple spear thrusts
    palettes = {
        "rise":   ((7, 15, 30), (14, 42, 74), (36, 128, 188), (118, 224, 255), (242, 252, 255)),
        "leap":   ((10, 14, 30), (26, 36, 78), (74, 118, 206), (255, 198, 86), (255, 250, 224)),
        "pierce": ((6, 18, 28), (14, 52, 76), (34, 156, 196), (138, 244, 255), (248, 255, 255)),
    }
    bg_dark, bg_mid, rim_col, bright_col, hot_col = palettes[kind]

    # Deep navy gradient backdrop + subtle border frame
    for y in range(g):
        for x in range(g):
            dx = (x - 15.5) / 16.0
            dy = (y - 15.5) / 16.0
            r = math.hypot(dx, dy)
            t = clamp(1.0 - r * 0.85)
            col = tuple(int(lerp(bg_dark[i], bg_mid[i], t * 0.72)) for i in range(3))
            px[x, y] = (*col, 255)

    def blend(x: int, y: int, col: tuple[int, int, int], a: float) -> None:
        if x < 0 or y < 0 or x >= g or y >= g or a <= 0.0:
            return
        a = clamp(a)
        r0, g0, b0, _ = px[x, y]
        px[x, y] = (
            int(lerp(r0, col[0], a)),
            int(lerp(g0, col[1], a)),
            int(lerp(b0, col[2], a)),
            255,
        )

    def draw_blade_arc(cx: float, cy: float, radius: float, ang0: float, ang1: float, thick: float, peak_col, core_col) -> None:
        for y in range(g):
            for x in range(g):
                dx = x - cx
                dy = y - cy
                rr = math.hypot(dx, dy)
                ang = math.atan2(dy, dx)
                # Normalize angle difference relative to [ang0, ang1]
                span = ang1 - ang0
                rel = (ang - ang0) % (2.0 * math.pi)
                if span < 0:
                    rel = (ang0 - ang) % (2.0 * math.pi)
                    span = -span
                if rel > span:
                    continue
                u = rel / max(1e-5, span)
                width_u = thick * math.sin(u * math.pi) ** 0.75 * (0.55 + 0.55 * u)
                d = abs(rr - radius)
                if d < width_u * 1.9:
                    glow = math.exp(-((d / max(0.35, width_u * 1.15)) ** 2))
                    blend(x, y, rim_col, glow * 0.85)
                if d < width_u:
                    core = clamp(1.0 - d / max(0.25, width_u))
                    blend(x, y, peak_col, min(1.0, core * 1.25))
                    if d < width_u * 0.45 and u > 0.25:
                        blend(x, y, core_col, clamp(1.0 - d / max(0.2, width_u * 0.45)))

    def draw_segment(x0: float, y0: float, x1: float, y1: float, thick: float, c_outer, c_inner) -> None:
        vx, vy = x1 - x0, y1 - y0
        l2 = vx * vx + vy * vy
        if l2 < 1e-6:
            return
        for y in range(g):
            for x in range(g):
                t = clamp(((x - x0) * vx + (y - y0) * vy) / l2)
                px0, py0 = x0 + vx * t, y0 + vy * t
                d = math.hypot(x - px0, y - py0)
                w = thick * (0.40 + 0.80 * t) * (1.0 - 0.35 * max(0.0, t - 0.75) / 0.25)
                if d < w * 2.1:
                    blend(x, y, rim_col, math.exp(-((d / max(0.3, w * 1.2)) ** 2)) * 0.80)
                if d < w:
                    blend(x, y, c_outer, clamp(1.0 - d / max(0.25, w)) * 1.15)
                if d < w * 0.48:
                    blend(x, y, c_inner, clamp(1.0 - d / max(0.2, w * 0.48)))

    if kind == "rise":
        # Soaring upward spiral crescent slash (流星·升) + dark swordsman silhouette leaping up
        draw_blade_arc(14.5, 16.5, 10.5, math.radians(135), math.radians(-85), 3.6, bright_col, hot_col)
        draw_blade_arc(15.5, 17.5, 7.2, math.radians(150), math.radians(-55), 2.1, bright_col, hot_col)
        # Sparkling meteor star at the soaring apex
        for dx, dy, a in [(0, 0, 1.0), (-1, 0, 0.9), (1, 0, 0.9), (0, -1, 0.9), (0, 1, 0.9), (-2, 0, 0.5), (2, 0, 0.5), (0, -2, 0.5), (0, 2, 0.5)]:
            blend(16 + dx, 6 + dy, hot_col, a)
        # Compact dark swordsman silhouette at lower-left launching upward
        for sx, sy in [(9, 25), (10, 24), (10, 25), (11, 23), (11, 24), (11, 25), (12, 22), (12, 23), (12, 24), (13, 21), (13, 22), (14, 24), (14, 25), (9, 26), (14, 26)]:
            px[sx, sy] = (8, 12, 22, 255)
    elif kind == "leap":
        # High-speed plunging meteor arc from upper-left slamming down to lower-right (流星·跃)
        draw_blade_arc(13.0, 20.0, 12.2, math.radians(-128), math.radians(-8), 3.6, bright_col, hot_col)
        draw_segment(7.5, 11.0, 24.0, 24.0, 2.3, bright_col, hot_col)
        # Impact ground shockwave ring at lower-right landing spot
        for x in range(11, 30):
            d = abs(x - 21.5) / 8.5
            if d <= 1.0:
                blend(x, 26, bright_col, (1.0 - d * d) * 0.95)
                blend(x, 25, hot_col, (1.0 - d * d) * 0.65)
                blend(x, 27, rim_col, (1.0 - d * d) * 0.75)
        for dx, dy, a in [(0, 0, 1.0), (-1, 0, 0.85), (1, 0, 0.85), (0, -1, 0.85), (0, 1, 0.85), (-2, 0, 0.45), (2, 0, 0.45)]:
            blend(23 + dx, 24 + dy, hot_col, a)
    else:
        # Lightning-fast triple piercing meteor thrusts shooting diagonally/horizontally right (流星·刺)
        draw_segment(4.0, 10.5, 25.0, 8.5, 1.9, bright_col, hot_col)
        draw_segment(3.0, 16.5, 28.5, 16.0, 2.7, bright_col, hot_col)
        draw_segment(5.0, 23.0, 25.0, 23.5, 1.9, bright_col, hot_col)
        # Piercing impact starburst at the main central spear tip
        for dx, dy, a in [(0, 0, 1.0), (-1, 0, 0.95), (1, 0, 0.95), (0, -1, 0.95), (0, 1, 0.95), (-1, -1, 0.65), (1, -1, 0.65), (-1, 1, 0.65), (1, 1, 0.65), (0, -2, 0.55), (0, 2, 0.55), (2, 0, 0.60)]:
            blend(27 + dx, 16 + dy, hot_col, a)

    # Crisp subtle 1px inner border matching modern 64x64 skill icons
    border = tuple(int(lerp(rim_col[i], bright_col[i], 0.35)) for i in range(3))
    for i in range(g):
        blend(i, 0, border, 0.65)
        blend(i, g - 1, border, 0.65)
        blend(0, i, border, 0.65)
        blend(g - 1, i, border, 0.65)

    return img.resize((64, 64), Image.NEAREST)


def build_meteor_icons() -> dict[str, Image.Image]:
    ICON_OUT.mkdir(parents=True, exist_ok=True)
    icons = {
        "meteor_rise.png": _draw_meteor_icon("rise"),
        "meteor_leap.png": _draw_meteor_icon("leap"),
        "meteor_pierce.png": _draw_meteor_icon("pierce"),
    }
    for name, img in icons.items():
        path = ICON_OUT / name
        img.save(path, format="PNG")
        print(f"wrote icon {path.relative_to(ROOT)} ({img.width}x{img.height})")
    return icons


def main() -> None:
    icons_only = "--icons-only" in sys.argv[1:]
    build_meteor_icons()
    if icons_only:
        return
    OUT.mkdir(parents=True, exist_ok=True)
    assets = {
        "sword_slash.png": make_sword_slash(),
        "draw_ring.png": make_draw_ring(),
        "iaido_flash.png": make_iaido_flash(),
        "dragon_head.png": make_dragon_head(),
        "gale_cone.png": make_gale_cone(),
        "rising_arc.png": make_rising_arc(),
        "sword_wave.png": make_sword_wave(),
        "ground_fissure.png": make_ground_fissure(),
        "overdrive_aura.png": make_overdrive_aura(),
        "sword_formation.png": make_sword_formation(),
        "ghost_crescent.png": make_ghost_crescent(),
    }
    for name, img in assets.items():
        path = OUT / name
        img.save(path, format="PNG")
        print(f"wrote {path.relative_to(ROOT)} ({img.width}x{img.height})")

    save_contact_sheet(assets, PREVIEW_DIR / "swordmaster_contact_sheet.png")


if __name__ == "__main__":
    main()

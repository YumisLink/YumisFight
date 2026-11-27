#!/usr/bin/env python3
"""
Dedicated DNF-inspired Witch / Trickster (魔道学者 / 魔术师)
pixel-sharp + soft-feathered VFX sprite sheets, 32x32 held flyswatter weapon,
and 64x64 GUI skill icon generator.

Outputs:
  - VFX atlases: src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/witch/*.png
  - Held effect weapon:
      textures/item/effect/witch_flyswatter.png
      models/item/effect/witch_flyswatter.json
      items/effect/witch_flyswatter.json
  - GUI skill icons: src/main/resources/assets/yumis_dungeon_and_fight/textures/gui/skills/<id>.png
  - Contact sheets for visual QA: temp/witch-vfx/{contact_sheet_dark,contact_sheet_light,icons_sheet}.png
"""

from __future__ import annotations

import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT_VFX = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/witch"
OUT_ITEM_TEX = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect"
OUT_ITEM_MODEL = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/models/item/effect"
OUT_ITEM_DEF = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/items/effect"
OUT_ICONS = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/gui/skills"
PREVIEW_DIR = ROOT / "temp/witch-vfx"


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
    val, amp, freq, norm = 0.0, 0.55, 1.0, 0.0
    for i in range(octaves):
        val += amp * value_noise(x * freq, y * freq, seed + i * 37)
        norm += amp
        amp *= 0.5
        freq *= 2.05
    return val / max(1e-6, norm)


def quantize(v: float, steps: int = 7) -> float:
    return (math.floor(clamp(v) * steps) + 0.5) / steps


def corner_mask(nx: float, ny: float, start: float = 0.84, end: float = 0.98) -> float:
    return 1.0 - smoothstep(start, end, math.hypot(nx, ny))


def sample_ramp(ramp, t: float) -> tuple[int, int, int]:
    t = clamp(t)
    for i in range(len(ramp) - 1):
        t0, c0 = ramp[i]
        t1, c1 = ramp[i + 1]
        if t <= t1:
            u = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return (
                int(round(lerp(c0[0], c1[0], u))),
                int(round(lerp(c0[1], c1[1], u))),
                int(round(lerp(c0[2], c1[2], u))),
            )
    return ramp[-1][1]


# --- 7-stop High-Contrast Multi-Tone Ramps ---

WITCH_CANDY_RAMP = (
    (0.00, (28, 10, 42)),
    (0.18, (88, 28, 128)),
    (0.40, (178, 66, 224)),
    (0.62, (238, 118, 226)),
    (0.82, (255, 192, 236)),
    (0.94, (255, 236, 200)),
    (1.00, (255, 255, 255)),
)

DARK_PLUM_RAMP = (
    (0.00, (16, 6, 28)),
    (0.18, (52, 18, 86)),
    (0.42, (112, 42, 184)),
    (0.65, (176, 92, 245)),
    (0.84, (224, 168, 255)),
    (0.95, (246, 222, 255)),
    (1.00, (255, 255, 255)),
)

FIRE_MAGMA_RAMP = (
    (0.00, (34, 8, 6)),
    (0.18, (118, 22, 14)),
    (0.40, (214, 62, 18)),
    (0.62, (255, 128, 28)),
    (0.82, (255, 202, 74)),
    (0.95, (255, 244, 178)),
    (1.00, (255, 255, 255)),
)

ICE_FROST_RAMP = (
    (0.00, (10, 26, 48)),
    (0.18, (22, 74, 126)),
    (0.40, (54, 152, 224)),
    (0.64, (116, 214, 255)),
    (0.84, (192, 244, 255)),
    (0.95, (232, 252, 255)),
    (1.00, (255, 255, 255)),
)

LIGHT_ELECTRIC_RAMP = (
    (0.00, (36, 24, 6)),
    (0.18, (118, 78, 12)),
    (0.40, (214, 164, 28)),
    (0.64, (255, 222, 78)),
    (0.84, (255, 246, 164)),
    (0.95, (240, 252, 255)),
    (1.00, (255, 255, 255)),
)

ACID_CLOUD_RAMP = (
    (0.00, (12, 24, 38)),
    (0.20, (28, 56, 86)),
    (0.44, (56, 108, 148)),
    (0.68, (106, 174, 212)),
    (0.86, (176, 226, 248)),
    (1.00, (240, 252, 255)),
)

IRON_FURNACE_RAMP = (
    (0.00, (26, 14, 18)),
    (0.22, (74, 34, 40)),
    (0.46, (148, 48, 46)),
    (0.68, (220, 86, 42)),
    (0.85, (255, 176, 68)),
    (0.96, (255, 238, 168)),
    (1.00, (255, 255, 255)),
)


def make_strip(frames: int, fw: int, fh: int, fn) -> Image.Image:
    img = Image.new("RGBA", (frames * fw, fh), (0, 0, 0, 0))
    px = img.load()
    for f in range(frames):
        ox = f * fw
        for y in range(fh):
            for x in range(fw):
                r, g, b, a = fn(f, frames, x, y, fw, fh)
                if a > 0:
                    px[ox + x, y] = (int(clamp(r, 0, 255)), int(clamp(g, 0, 255)), int(clamp(b, 0, 255)), int(clamp(a, 0, 255)))
    return img


def star_sdf(nx: float, ny: float, points: int = 5, inner: float = 0.42) -> float:
    ang = math.atan2(ny, nx)
    r = math.hypot(nx, ny)
    sector = math.tau / points
    a = abs(((ang + sector * 0.5) % sector) - sector * 0.5)
    t = a / (sector * 0.5)
    rim = lerp(1.0, inner, t)
    return r / max(1e-5, rim)


# --- Individual Sprite Generators ---

def gen_broom_sweep() -> Image.Image:
    # 512x64, 8 frames, crescent arc pointing +x (right)
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx + 0.35, ny)
        ang = math.atan2(ny, nx + 0.35)
        span = lerp(0.95, 1.38, smoothstep(0.0, 0.45, prog))
        ang_fade = 1.0 - smoothstep(span * 0.65, span, abs(ang))
        rad_center = lerp(0.75, 1.15, prog)
        thick = lerp(0.32, 0.14, prog)
        band = 1.0 - smoothstep(0.0, thick, abs(r - rad_center))
        nz = fbm(nx * 5.0 + f * 1.1, ny * 5.0, seed=11)
        val = band * ang_fade * (0.72 + 0.42 * nz)
        fade = (1.0 - 0.78 * smoothstep(0.58, 1.0, prog)) * smoothstep(0.0, 0.12, prog + 0.08)
        # trailing sparkles
        sp = hash2(x // 2, y // 2, 70 + f)
        spark = 1.0 if (sp > 0.965 and abs(r - (rad_center - 0.18)) < 0.22 and abs(ang) < span) else 0.0
        alpha = clamp(max(val * fade, spark * fade * 0.9))
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(alpha * 0.92 + (0.35 if spark else 0.0)), 7)
        c = sample_ramp(WITCH_CANDY_RAMP if spark == 0 else LIGHT_ELECTRIC_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 255)
    return make_strip(8, 64, 64, pixel)


def gen_stardust_star() -> Image.Image:
    # 512x64, 8 frames, rotating magic star + sparkle burst
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        rot = f * 0.45
        rx = nx * math.cos(rot) - ny * math.sin(rot)
        ry = nx * math.sin(rot) + ny * math.cos(rot)
        sd = star_sdf(rx, ry, 5, 0.42)
        scale = lerp(0.45, 0.88, smoothstep(0.0, 0.5, prog))
        body = 1.0 - smoothstep(scale * 0.72, scale, sd)
        ring = 1.0 - smoothstep(0.0, 0.13, abs(math.hypot(nx, ny) - lerp(0.28, 0.88, prog)))
        fade = 1.0 - 0.78 * smoothstep(0.65, 1.0, prog)
        alpha = clamp(max(body, ring * 0.65) * fade * corner_mask(nx, ny, 0.86, 0.99))
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(1.0 - sd / max(0.2, scale) * 0.6 + 0.25), 7)
        c = sample_ramp(LIGHT_ELECTRIC_RAMP if sd < scale * 0.82 else WITCH_CANDY_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 255)
    return make_strip(8, 64, 64, pixel)


def gen_shadow_cloak() -> Image.Image:
    # 512x64, 8 frames, swirling dark phantom cloak tendrils contracting then exploding
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx, ny)
        ang = math.atan2(ny, nx)
        swirl = ang + r * (3.6 - 2.2 * prog) + f * 0.55
        ribs = 0.5 + 0.5 * math.sin(swirl * 5.0)
        nz = fbm(nx * 4.5 + f * 0.7, ny * 4.5 - f * 0.5, seed=23)
        outer = lerp(0.92, 0.56, smoothstep(0.0, 0.5, prog)) if prog < 0.55 else lerp(0.56, 0.96, (prog - 0.55) / 0.45)
        inner = outer * 0.25
        shell = smoothstep(inner * 0.6, inner + 0.12, r) * (1.0 - smoothstep(outer - 0.16, outer, r))
        core = 1.0 - smoothstep(0.15, 0.48, r) if prog >= 0.5 else 0.0
        alpha = clamp((shell * (0.45 + 0.65 * ribs) * (0.65 + 0.45 * nz) + core * 0.9) * (1.0 - 0.76 * smoothstep(0.76, 1.0, prog)) * corner_mask(nx, ny))
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(alpha * 0.78 + core * 0.45), 7)
        c = sample_ramp(DARK_PLUM_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 255)
    return make_strip(8, 64, 64, pixel)


def gen_flyswatter_slam() -> Image.Image:
    # 512x64, 8 frames, comic starburst + candy-purple slam splat
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx, ny)
        sd = star_sdf(nx, ny, 8, 0.52)
        rad = lerp(0.32, 0.95, smoothstep(0.0, 0.45, prog))
        star = 1.0 - smoothstep(rad * 0.65, rad, sd)
        ring = 1.0 - smoothstep(0.0, 0.12, abs(r - lerp(0.25, 0.92, prog)))
        # subtle flyswatter grid imprint in early frames
        grid = 1.0 if (prog < 0.55 and r < rad * 0.55 and ((x % 6 == 0) or (y % 6 == 0))) else 0.0
        fade = 1.0 - 0.78 * smoothstep(0.55, 1.0, prog)
        alpha = clamp(max(star * 0.92, ring * 0.85, grid * 0.5) * fade * corner_mask(nx, ny))
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(alpha * 0.85 + (0.28 if grid else 0.0) + (0.25 if r < 0.25 else 0.0)), 7)
        c = sample_ramp(WITCH_CANDY_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 255)
    return make_strip(8, 64, 64, pixel)


def gen_familiar_puppets() -> Image.Image:
    # 256x64, 4 frames: 0=Florae(light), 1=Jack-O-Lantern(fire), 2=Frost Head(ice), 3=Pluto(dark cat)
    img = Image.new("RGBA", (256, 64), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Frame 0: Florae (光电鳗 / golden glowing electric sprite with cute antennae)
    ox = 0
    draw.ellipse((ox + 12, 16, ox + 52, 54), fill=(118, 78, 12, 230), outline=(40, 26, 6, 255), width=2)
    draw.ellipse((ox + 15, 19, ox + 49, 51), fill=(255, 214, 62, 255))
    draw.ellipse((ox + 20, 23, ox + 44, 44), fill=(255, 246, 170, 255))
    # lightning antennae
    draw.line([(ox + 22, 18), (ox + 16, 8), (ox + 24, 12)], fill=(255, 242, 128, 255), width=3)
    draw.line([(ox + 42, 18), (ox + 48, 8), (ox + 40, 12)], fill=(255, 242, 128, 255), width=3)
    # cute eyes
    draw.ellipse((ox + 21, 30, ox + 27, 38), fill=(36, 22, 6, 255))
    draw.ellipse((ox + 37, 30, ox + 43, 38), fill=(36, 22, 6, 255))
    draw.point((ox + 23, 32), fill=(255, 255, 255, 255))
    draw.point((ox + 39, 32), fill=(255, 255, 255, 255))

    # Frame 1: Jack-O'-Lantern (杰克爆弹 / glowing pumpkin with fiery tuft)
    ox = 64
    draw.polygon([(ox + 32, 6), (ox + 26, 16), (ox + 38, 16)], fill=(66, 148, 42, 255))
    draw.ellipse((ox + 10, 16, ox + 54, 56), fill=(132, 32, 12, 255), outline=(42, 10, 6, 255), width=2)
    draw.ellipse((ox + 13, 18, ox + 51, 54), fill=(242, 98, 24, 255))
    draw.ellipse((ox + 20, 20, ox + 44, 52), fill=(255, 148, 42, 255))
    # glowing carved eyes & grin
    draw.polygon([(ox + 20, 32), (ox + 28, 29), (ox + 25, 37)], fill=(255, 248, 172, 255))
    draw.polygon([(ox + 44, 32), (ox + 36, 29), (ox + 39, 37)], fill=(255, 248, 172, 255))
    draw.arc((ox + 19, 36, ox + 45, 50), 15, 165, fill=(255, 248, 172, 255), width=3)

    # Frame 2: Frost Head (冰霜雪人 / round snowman with red scarf & icy crown)
    ox = 128
    draw.ellipse((ox + 12, 16, ox + 52, 56), fill=(34, 96, 154, 255), outline=(14, 34, 62, 255), width=2)
    draw.ellipse((ox + 15, 19, ox + 49, 53), fill=(166, 232, 255, 255))
    draw.ellipse((ox + 20, 22, ox + 44, 45), fill=(234, 252, 255, 255))
    draw.rectangle((ox + 16, 44, ox + 48, 50), fill=(224, 64, 88, 255))
    draw.polygon([(ox + 32, 8), (ox + 24, 18), (ox + 40, 18)], fill=(96, 204, 255, 255))
    draw.ellipse((ox + 22, 30, ox + 27, 36), fill=(20, 42, 74, 255))
    draw.ellipse((ox + 37, 30, ox + 42, 36), fill=(20, 42, 74, 255))
    draw.polygon([(ox + 32, 35), (ox + 39, 38), (ox + 32, 40)], fill=(255, 138, 42, 255))

    # Frame 3: Pluto (暗影夜猫 / cute purple-black shadow cat with glowing magenta eyes)
    ox = 192
    draw.polygon([(ox + 14, 28), (ox + 14, 10), (ox + 28, 20)], fill=(48, 18, 82, 255))
    draw.polygon([(ox + 50, 28), (ox + 50, 10), (ox + 36, 20)], fill=(48, 18, 82, 255))
    draw.ellipse((ox + 12, 18, ox + 52, 55), fill=(32, 12, 54, 255), outline=(142, 68, 218, 255), width=2)
    draw.ellipse((ox + 16, 22, ox + 48, 52), fill=(76, 30, 132, 255))
    draw.ellipse((ox + 20, 31, ox + 28, 39), fill=(255, 218, 92, 255))
    draw.ellipse((ox + 36, 31, ox + 44, 39), fill=(255, 218, 92, 255))
    draw.line([(ox + 24, 32), (ox + 24, 38)], fill=(30, 10, 46, 255), width=2)
    draw.line([(ox + 40, 32), (ox + 40, 38)], fill=(30, 10, 46, 255), width=2)
    return img


def gen_lava_pool() -> Image.Image:
    # 128x128 single decal, corners strictly alpha=0
    def pixel(f, n, x, y, fw, fh):
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx, ny)
        nz = fbm(nx * 4.5, ny * 4.5, seed=31, octaves=4)
        cracks = abs(fbm(nx * 7.5 + 3.0, ny * 7.5 - 2.0, seed=41) - 0.5) * 2.0
        edge = r + (nz - 0.5) * 0.16
        mask = (1.0 - smoothstep(0.76, 0.94, edge)) * corner_mask(nx, ny, 0.84, 0.98)
        if mask < 0.03:
            return 0, 0, 0, 0
        # bright magma veins + bubbling pockets
        vein = 1.0 - smoothstep(0.04, 0.24, cracks)
        bubble = smoothstep(0.72, 0.88, fbm(nx * 9.0, ny * 9.0, seed=53))
        tone = quantize(clamp(0.32 + 0.45 * vein + 0.35 * bubble + (1.0 - r) * 0.22), 7)
        c = sample_ramp(FIRE_MAGMA_RAMP, tone)
        return c[0], c[1], c[2], int(mask * 235)
    return make_strip(1, 128, 128, pixel)


def gen_lava_geyser() -> Image.Image:
    # 512x128, 8 frames, vertical erupting column (v=0 top, v=1 bottom)
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh  # 0=top, 1=bottom
        height = lerp(0.25, 0.95, smoothstep(0.0, 0.38, prog))
        top_y = 1.0 - height
        if ny < top_y - 0.06:
            return 0, 0, 0, 0
        u = clamp((ny - top_y) / max(0.1, height))
        half_w = lerp(0.55, 0.28, 1.0 - u) * lerp(1.1, 0.65, smoothstep(0.55, 1.0, prog))
        nz = fbm(nx * 4.5, ny * 8.0 - prog * 5.0, seed=61)
        dx = abs(nx) / max(0.08, half_w + (nz - 0.5) * 0.22)
        body = (1.0 - smoothstep(0.65, 1.05, dx)) * smoothstep(top_y - 0.04, top_y + 0.12, ny)
        fade = 1.0 - 0.76 * smoothstep(0.65, 1.0, prog)
        alpha = clamp(body * fade)
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp((1.0 - dx * 0.6) * 0.85 + nz * 0.32), 7)
        c = sample_ramp(FIRE_MAGMA_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 245)
    return make_strip(8, 64, 128, pixel)


def gen_acid_cloud() -> Image.Image:
    # 256x64, 4 looping cloud frames
    def pixel(f, n, x, y, fw, fh):
        phase = f * math.tau / n
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        # 3 overlapping puffy cloud lobes
        d1 = math.hypot((nx + 0.36) / 0.52, (ny - 0.05) / 0.48)
        d2 = math.hypot(nx / 0.62, (ny + 0.14) / 0.56)
        d3 = math.hypot((nx - 0.36) / 0.52, (ny - 0.06) / 0.48)
        d = min(d1, d2, d3)
        nz = fbm(nx * 4.0 + math.cos(phase) * 0.6, ny * 4.0 + math.sin(phase) * 0.6, seed=71)
        alpha = clamp((1.0 - smoothstep(0.76, 1.02, d + (nz - 0.5) * 0.24)) * corner_mask(nx, ny, 0.86, 0.99))
        if alpha < 0.04:
            return 0, 0, 0, 0
        # upper light cyan highlight, lower dark storm belly + subtle electric flash on frame 2
        flash = 0.25 if f == 2 else 0.0
        tone = quantize(clamp(0.55 - ny * 0.35 + (nz - 0.5) * 0.28 + flash), 7)
        c = sample_ramp(ACID_CLOUD_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 238)
    return make_strip(4, 64, 64, pixel)


def gen_acid_rain_sheet() -> Image.Image:
    # 256x128, 4 looping rain streak curtains (v=0 top, v=1 bottom)
    def pixel(f, n, x, y, fw, fh):
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh
        # slanted streaks scrolling downward per frame
        shift_y = (y - f * 32) % fh
        col = (x + shift_y // 8) % 8
        streak_seed = hash2((x + shift_y // 8) // 8, shift_y // 20, 83)
        in_drop = 1.0 if (col in (2, 3) and streak_seed > 0.38) else 0.0
        edge = (1.0 - smoothstep(0.72, 1.0, abs(nx))) * smoothstep(0.0, 0.12, ny) * (1.0 - smoothstep(0.85, 1.0, ny))
        mist = fbm(nx * 5.0, ny * 5.0 - f * 0.6, seed=89) * 0.22
        alpha = clamp((in_drop * 0.88 + mist) * edge)
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(0.45 + in_drop * 0.45), 7)
        c = sample_ramp(ICE_FROST_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 225)
    return make_strip(4, 64, 128, pixel)


def gen_sigil(ramp, seed: int, spokes: int = 6) -> Image.Image:
    # 128x128 circular arcane sigil with alpha=0 corners
    def pixel(f, n, x, y, fw, fh):
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx, ny)
        ang = math.atan2(ny, nx)
        outer_ring = 1.0 - smoothstep(0.0, 0.055, abs(r - 0.82))
        mid_ring = 1.0 - smoothstep(0.0, 0.045, abs(r - 0.58))
        inner_ring = 1.0 - smoothstep(0.0, 0.04, abs(r - 0.26))
        star = 1.0 - smoothstep(0.0, 0.06, abs(star_sdf(nx, ny, spokes, 0.52) - 0.72))
        ticks = (1.0 - smoothstep(0.0, 0.08, abs(r - 0.70))) * smoothstep(0.4, 0.9, math.sin(ang * spokes * 4.0))
        glow = (1.0 - smoothstep(0.0, 0.86, r)) * 0.26
        val = max(outer_ring, mid_ring * 0.9, inner_ring * 0.85, star * 0.95, ticks * 0.8, glow)
        alpha = clamp(val * corner_mask(nx, ny, 0.86, 0.98))
        if alpha < 0.03:
            return 0, 0, 0, 0
        tone = quantize(clamp(alpha * 0.92 + (0.2 if star > 0.5 else 0.0)), 7)
        c = sample_ramp(ramp, tone)
        return c[0], c[1], c[2], int(alpha * 245)
    return make_strip(1, 128, 128, pixel)


def gen_anti_gravity_beam() -> Image.Image:
    # 512x128, 8 frames, upward purple anti-gravity rings then downward slam shock
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh  # 0=top, 1=bottom
        col_w = lerp(0.56, 0.82, prog)
        core = 1.0 - smoothstep(col_w * 0.55, col_w, abs(nx))
        ring_wave = 0.5 + 0.5 * math.sin((ny * 18.0) + (prog * 14.0 if prog < 0.6 else -prog * 22.0))
        nz = fbm(nx * 5.0, ny * 7.0 + f * 0.8, seed=97)
        vert_mask = smoothstep(0.0, 0.14, ny) * (1.0 - smoothstep(0.88, 1.0, ny))
        fade = 1.0 - 0.76 * smoothstep(0.72, 1.0, prog)
        alpha = clamp(core * (0.45 + 0.55 * ring_wave) * (0.7 + 0.35 * nz) * vert_mask * fade)
        if alpha < 0.04:
            return 0, 0, 0, 0
        tone = quantize(clamp(alpha * 0.85 + (0.3 if abs(nx) < 0.22 else 0.0)), 7)
        c = sample_ramp(DARK_PLUM_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 240)
    return make_strip(8, 64, 128, pixel)


def gen_furnace_body() -> Image.Image:
    # 256x128, 4 frames, DNF Raging Flame Furnace (红炉子) throbbing with molten vents
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for f in range(4):
        ox = f * 64
        bounce = (0, -3, 0, 2)[f]
        # top chimney & flames
        draw.rectangle((ox + 22, 16 + bounce, ox + 42, 40 + bounce), fill=(86, 30, 32, 255), outline=(28, 10, 14, 255), width=2)
        draw.ellipse((ox + 18, 6 + bounce, ox + 46, 28 + bounce), fill=(255, 134, 28, 220))
        draw.ellipse((ox + 24, 10 + bounce, ox + 40, 24 + bounce), fill=(255, 242, 162, 255))
        # side exhaust pipes
        draw.rectangle((ox + 6, 46 + bounce, ox + 16, 78 + bounce), fill=(116, 40, 38, 255), outline=(30, 12, 16, 255), width=2)
        draw.rectangle((ox + 48, 46 + bounce, ox + 58, 78 + bounce), fill=(116, 40, 38, 255), outline=(30, 12, 16, 255), width=2)
        # main cast-iron furnace belly
        draw.rounded_rectangle((ox + 12, 34 + bounce, ox + 52, 108 + bounce), radius=12, fill=(146, 38, 36, 255), outline=(34, 10, 14, 255), width=3)
        # glowing furnace grate
        glow = 255 if f % 2 == 0 else 218
        draw.rounded_rectangle((ox + 19, 54 + bounce, ox + 45, 90 + bounce), radius=6, fill=(glow, 118, 24, 255), outline=(56, 18, 14, 255), width=2)
        draw.rectangle((ox + 22, 60 + bounce, ox + 42, 84 + bounce), fill=(255, 232, 126, 255))
        for gx in (26, 32, 38):
            draw.line([(ox + gx, 56 + bounce), (ox + gx, 88 + bounce)], fill=(54, 18, 14, 255), width=2)
        # stubby iron legs
        draw.rectangle((ox + 16, 106 + bounce, ox + 25, 122), fill=(62, 24, 28, 255))
        draw.rectangle((ox + 39, 106 + bounce, ox + 48, 122), fill=(62, 24, 28, 255))
    return img


def gen_mega_drill_body() -> Image.Image:
    # 256x128, 4 frames, DNF Mega Drill (冰霜钻孔车) spinning frost auger
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for f in range(4):
        ox = f * 64
        # spinning crystalline auger cone (top=tip v=0, bottom=chassis v=1)
        draw.polygon([(ox + 32, 8), (ox + 12, 68), (ox + 52, 68)], fill=(64, 168, 236, 255), outline=(14, 42, 78, 255))
        # helical drill ridges scrolling per frame
        for k in range(5):
            y0 = 18 + k * 10 + (f * 2)
            half = max(3, int((y0 - 8) * 0.34))
            draw.line([(ox + 32 - half, y0), (ox + 32 + half, y0 + 5)], fill=(232, 252, 255, 255), width=3)
        # chassis cab & frost exhausts
        draw.rounded_rectangle((ox + 10, 66, ox + 54, 114), radius=10, fill=(38, 112, 182, 255), outline=(12, 30, 56, 255), width=3)
        draw.rounded_rectangle((ox + 18, 74, ox + 46, 98), radius=6, fill=(142, 224, 255, 255))
        draw.ellipse((ox + 4, 88, ox + 16, 112), fill=(24, 52, 84, 255))
        draw.ellipse((ox + 48, 88, ox + 60, 112), fill=(24, 52, 84, 255))
    return img


def gen_collider_body() -> Image.Image:
    # 256x128, 4 frames, DNF Florae Collider (电鳗碰撞机) Tesla tower
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for f in range(4):
        ox = f * 64
        pulse = (0, 2, 0, -2)[f]
        # top Tesla orb
        draw.ellipse((ox + 18 - pulse, 8 - pulse, ox + 46 + pulse, 36 + pulse), fill=(255, 224, 82, 255), outline=(112, 72, 10, 255), width=2)
        draw.ellipse((ox + 24, 14, ox + 40, 30), fill=(255, 255, 240, 255))
        # copper coil rings
        draw.rectangle((ox + 26, 36, ox + 38, 96), fill=(96, 74, 42, 255), outline=(36, 24, 10, 255), width=2)
        for ry in (44, 58, 72):
            draw.rounded_rectangle((ox + 14, ry, ox + 50, ry + 8), radius=4, fill=(238, 182, 46, 255), outline=(72, 44, 12, 255), width=2)
            if (ry // 14 + f) % 2 == 0:
                draw.line([(ox + 12, ry + 4), (ox + 52, ry + 4)], fill=(230, 250, 255, 255), width=2)
        # generator pedestal
        draw.rounded_rectangle((ox + 10, 90, ox + 54, 120), radius=8, fill=(58, 92, 148, 255), outline=(18, 30, 56, 255), width=3)
        draw.ellipse((ox + 24, 98, ox + 40, 114), fill=(255, 232, 104, 255))
    return img


def gen_fusion_roller() -> Image.Image:
    # 256x128, 4 frames, DNF Awakening Fusion Craft (技艺融合) 4-element juggernaut roller
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    colors = [(255, 110, 42, 255), (96, 212, 255, 255), (255, 228, 78, 255), (186, 98, 255, 255)]
    for f in range(4):
        ox = f * 64
        # giant candy-alchemy roller drum at bottom
        draw.rounded_rectangle((ox + 6, 76, ox + 58, 120), radius=10, fill=(138, 52, 192, 255), outline=(32, 10, 48, 255), width=3)
        for s in range(4):
            c = colors[(s + f) % 4]
            sx = ox + 10 + s * 11
            draw.rectangle((sx, 82, sx + 9, 114), fill=c)
        # upper 4-familiar engine cockpit
        draw.rounded_rectangle((ox + 12, 28, ox + 52, 78), radius=10, fill=(214, 88, 186, 255), outline=(42, 12, 54, 255), width=3)
        # 4 glowing familiar portholes
        draw.ellipse((ox + 16, 36, ox + 30, 50), fill=colors[0])
        draw.ellipse((ox + 34, 36, ox + 48, 50), fill=colors[1])
        draw.ellipse((ox + 16, 54, ox + 30, 68), fill=colors[2])
        draw.ellipse((ox + 34, 54, ox + 48, 68), fill=colors[3])
        # crown star on top
        draw.polygon([(ox + 32, 6), (ox + 24, 26), (ox + 40, 26)], fill=(255, 232, 92, 255), outline=(112, 66, 10, 255))
    return img


def gen_bitter_explosion() -> Image.Image:
    # 512x64, 8 frames, comic Bitter Lollipop (苦涩炸炉) smoke puff -> magenta-gold starburst boom!
    def pixel(f, n, x, y, fw, fh):
        prog = f / max(1, n - 1)
        nx = (x + 0.5) / fw * 2.0 - 1.0
        ny = (y + 0.5) / fh * 2.0 - 1.0
        r = math.hypot(nx, ny)
        sd = star_sdf(nx, ny, 7, 0.48)
        nz = fbm(nx * 5.0 + f * 0.9, ny * 5.0 - f * 0.7, seed=107)
        rad = lerp(0.28, 0.96, smoothstep(0.0, 0.42, prog))
        boom = 1.0 - smoothstep(rad * 0.60, rad, sd + (nz - 0.5) * 0.22)
        ring = 1.0 - smoothstep(0.0, 0.14, abs(r - lerp(0.20, 0.94, prog)))
        smoke_ring = (1.0 - smoothstep(0.0, 0.22, abs(r - lerp(0.42, 0.92, prog)))) * (0.5 + 0.5 * nz)
        fade = 1.0 - 0.78 * smoothstep(0.62, 1.0, prog)
        alpha = clamp(max(boom, ring * 0.9, smoke_ring * 0.65) * fade * corner_mask(nx, ny))
        if alpha < 0.04:
            return 0, 0, 0, 0
        if smoke_ring > boom and prog > 0.35:
            tone = quantize(clamp(0.18 + 0.35 * nz), 7)
            c = sample_ramp(DARK_PLUM_RAMP, tone)
        else:
            tone = quantize(clamp(alpha * 0.9 + (0.28 if r < 0.35 else 0.0)), 7)
            c = sample_ramp(WITCH_CANDY_RAMP if f % 2 == 0 else FIRE_MAGMA_RAMP, tone)
        return c[0], c[1], c[2], int(alpha * 255)
    return make_strip(8, 64, 64, pixel)


# --- Custom 32x32 Held Flyswatter Weapon ---

def gen_held_flyswatter():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # diagonal handle (bottom-left to mid)
    draw.line([(3, 28), (15, 16)], fill=(68, 22, 98, 255), width=4)
    draw.line([(4, 27), (15, 16)], fill=(196, 98, 242, 255), width=2)
    # golden pommel star
    draw.ellipse((1, 25, 6, 30), fill=(255, 222, 82, 255), outline=(92, 48, 12, 255))
    # oversized paddle (top-right)
    paddle = [(13, 13), (22, 2), (30, 10), (20, 19)]
    draw.polygon(paddle, fill=(132, 36, 188, 245), outline=(44, 12, 68, 255))
    inner = [(15, 13), (22, 5), (27, 10), (20, 17)]
    draw.polygon(inner, fill=(238, 114, 232, 240))
    # mesh lines & candy shine
    draw.line([(17, 10), (24, 13)], fill=(78, 24, 116, 255), width=1)
    draw.line([(19, 8), (26, 11)], fill=(78, 24, 116, 255), width=1)
    draw.line([(19, 15), (24, 7)], fill=(78, 24, 116, 255), width=1)
    draw.point((22, 7), fill=(255, 255, 255, 255))
    draw.point((25, 10), fill=(255, 246, 192, 255))

    OUT_ITEM_TEX.mkdir(parents=True, exist_ok=True)
    OUT_ITEM_MODEL.mkdir(parents=True, exist_ok=True)
    OUT_ITEM_DEF.mkdir(parents=True, exist_ok=True)

    img.save(OUT_ITEM_TEX / "witch_flyswatter.png")
    model = {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "yumis_dungeon_and_fight:item/effect/witch_flyswatter"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 50], "translation": [0, 6.0, 1.5], "scale": [1.85, 1.85, 1.15]},
            "thirdperson_lefthand": {"rotation": [0, 90, -50], "translation": [0, 6.0, 1.5], "scale": [1.85, 1.85, 1.15]},
            "firstperson_righthand": {"rotation": [0, -90, 35], "translation": [1.5, 4.0, 1.5], "scale": [1.35, 1.35, 0.95]},
            "firstperson_lefthand": {"rotation": [0, 90, -35], "translation": [1.5, 4.0, 1.5], "scale": [1.35, 1.35, 0.95]},
        },
    }
    (OUT_ITEM_MODEL / "witch_flyswatter.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    item_def = {"model": {"type": "minecraft:model", "model": "yumis_dungeon_and_fight:item/effect/witch_flyswatter"}}
    (OUT_ITEM_DEF / "witch_flyswatter.json").write_text(json.dumps(item_def, indent=2) + "\n", encoding="utf-8")

    giant_model = {
        "parent": "minecraft:item/handheld",
        "textures": {"layer0": "yumis_dungeon_and_fight:item/effect/witch_flyswatter"},
        "display": {
            "thirdperson_righthand": {"rotation": [0, -90, 50], "translation": [0, 9.5, 2.0], "scale": [2.78, 2.78, 1.72]},
            "thirdperson_lefthand": {"rotation": [0, 90, -50], "translation": [0, 9.5, 2.0], "scale": [2.78, 2.78, 1.72]},
            "firstperson_righthand": {"rotation": [0, -90, 35], "translation": [2.0, 5.5, 2.0], "scale": [2.02, 2.02, 1.42]},
            "firstperson_lefthand": {"rotation": [0, 90, -35], "translation": [2.0, 5.5, 2.0], "scale": [2.02, 2.02, 1.42]},
        },
    }
    (OUT_ITEM_MODEL / "witch_flyswatter_giant.json").write_text(json.dumps(giant_model, indent=2) + "\n", encoding="utf-8")
    giant_def = {"model": {"type": "minecraft:model", "model": "yumis_dungeon_and_fight:item/effect/witch_flyswatter_giant"}}
    (OUT_ITEM_DEF / "witch_flyswatter_giant.json").write_text(json.dumps(giant_def, indent=2) + "\n", encoding="utf-8")


# --- 64x64 GUI Skill Icons ---

def icon_frame(bg_top, bg_bot, border) -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 255))
    px = img.load()
    for y in range(64):
        t = y / 63.0
        r = int(lerp(bg_top[0], bg_bot[0], t))
        g = int(lerp(bg_top[1], bg_bot[1], t))
        b = int(lerp(bg_top[2], bg_bot[2], t))
        for x in range(64):
            if x < 2 or x >= 62 or y < 2 or y >= 62:
                px[x, y] = (*border, 255)
            elif x == 2 or x == 61 or y == 2 or y == 61:
                px[x, y] = (border[0] // 2, border[1] // 2, border[2] // 2, 255)
            else:
                px[x, y] = (r, g, b, 255)
    return img


def gen_skill_icons() -> dict[str, Image.Image]:
    icons: dict[str, Image.Image] = {}

    # 1. witchcraft_mastery (broom + 4 familiar orbs + witch hat star)
    im = icon_frame((54, 22, 86), (22, 10, 38), (224, 148, 255))
    d = ImageDraw.Draw(im)
    d.line([(12, 52), (50, 14)], fill=(198, 128, 66, 255), width=5)
    d.polygon([(8, 56), (14, 40), (24, 50)], fill=(238, 192, 92, 255), outline=(112, 68, 22, 255))
    for cx, cy, col in [(18, 18, (255, 118, 42)), (48, 36, (92, 214, 255)), (28, 46, (255, 228, 78)), (42, 50, (186, 98, 255))]:
        d.ellipse((cx - 6, cy - 6, cx + 6, cy + 6), fill=(*col, 255), outline=(255, 255, 255, 220))
    icons["witchcraft_mastery"] = im

    # 2. shadow_cloak
    im = icon_frame((44, 14, 72), (14, 6, 26), (186, 104, 255))
    d = ImageDraw.Draw(im)
    d.polygon([(32, 10), (10, 52), (24, 46), (32, 56), (40, 46), (54, 52)], fill=(78, 28, 138, 255), outline=(228, 166, 255, 255))
    d.ellipse((22, 20, 42, 38), fill=(26, 8, 46, 255))
    d.ellipse((25, 26, 30, 31), fill=(255, 230, 108, 255))
    d.ellipse((34, 26, 39, 31), fill=(255, 230, 108, 255))
    icons["shadow_cloak"] = im

    # 3. giant_flyswatter
    im = icon_frame((72, 18, 92), (26, 8, 42), (255, 138, 232))
    d = ImageDraw.Draw(im)
    d.line([(12, 54), (34, 32)], fill=(196, 108, 242, 255), width=5)
    d.polygon([(26, 24), (42, 8), (56, 22), (40, 38)], fill=(238, 112, 226, 255), outline=(255, 236, 255, 255))
    d.line([(33, 18), (47, 30)], fill=(96, 26, 138, 255), width=2)
    d.line([(36, 30), (48, 16)], fill=(96, 26, 138, 255), width=2)
    d.ellipse((46, 44, 56, 54), fill=(255, 222, 78, 255))
    icons["giant_flyswatter"] = im

    # 4. lava_potion
    im = icon_frame((78, 22, 14), (30, 8, 8), (255, 148, 54))
    d = ImageDraw.Draw(im)
    d.rectangle((26, 10, 38, 22), fill=(186, 228, 246, 255), outline=(74, 96, 122, 255), width=2)
    d.ellipse((14, 20, 50, 56), fill=(224, 62, 18, 255), outline=(255, 214, 134, 255), width=2)
    d.chord((16, 24, 48, 54), 10, 170, fill=(255, 178, 48, 255))
    d.ellipse((24, 34, 32, 42), fill=(255, 246, 182, 255))
    icons["lava_potion"] = im

    # 5. acid_rain
    im = icon_frame((16, 42, 74), (10, 20, 42), (116, 220, 255))
    d = ImageDraw.Draw(im)
    d.ellipse((10, 12, 34, 32), fill=(76, 148, 198, 255))
    d.ellipse((22, 8, 48, 32), fill=(112, 188, 232, 255))
    d.ellipse((34, 14, 56, 32), fill=(76, 148, 198, 255))
    for rx in (16, 26, 44, 52):
        d.line([(rx, 36), (rx - 4, 54)], fill=(142, 234, 255, 255), width=3)
    d.line([(34, 32), (28, 44), (36, 44), (30, 56)], fill=(255, 232, 84, 255), width=3)
    icons["acid_rain"] = im

    # 6. anti_gravity
    im = icon_frame((52, 16, 92), (18, 6, 38), (206, 132, 255))
    d = ImageDraw.Draw(im)
    d.ellipse((10, 38, 54, 56), fill=(116, 46, 198, 255), outline=(228, 178, 255, 255), width=2)
    d.polygon([(32, 10), (18, 28), (27, 28), (27, 44), (37, 44), (37, 28), (46, 28)], fill=(224, 162, 255, 255), outline=(255, 255, 255, 255))
    icons["anti_gravity"] = im

    # 7. raging_flame_furnace
    im = icon_frame((86, 20, 18), (34, 8, 10), (255, 118, 52))
    d = ImageDraw.Draw(im)
    d.rectangle((24, 8, 40, 22), fill=(112, 34, 34, 255))
    d.rounded_rectangle((14, 20, 50, 54), radius=8, fill=(176, 46, 36, 255), outline=(255, 174, 78, 255), width=2)
    d.rounded_rectangle((20, 28, 44, 46), radius=4, fill=(255, 212, 72, 255))
    d.line([(28, 28), (28, 46)], fill=(78, 20, 14, 255), width=2)
    d.line([(36, 28), (36, 46)], fill=(78, 20, 14, 255), width=2)
    icons["raging_flame_furnace"] = im

    # 8. mega_drill
    im = icon_frame((14, 44, 82), (8, 18, 42), (126, 232, 255))
    d = ImageDraw.Draw(im)
    d.polygon([(32, 8), (12, 42), (52, 42)], fill=(96, 204, 255, 255), outline=(232, 252, 255, 255))
    d.line([(24, 22), (40, 26)], fill=(255, 255, 255, 255), width=3)
    d.line([(18, 32), (46, 36)], fill=(255, 255, 255, 255), width=3)
    d.rounded_rectangle((14, 42, 50, 56), radius=5, fill=(46, 126, 198, 255), outline=(196, 244, 255, 255), width=2)
    icons["mega_drill"] = im

    # 9. florae_collider
    im = icon_frame((66, 48, 12), (24, 18, 8), (255, 226, 92))
    d = ImageDraw.Draw(im)
    d.ellipse((20, 10, 44, 34), fill=(255, 234, 102, 255), outline=(255, 255, 255, 255), width=2)
    d.rectangle((27, 34, 37, 54), fill=(198, 142, 34, 255))
    d.line([(10, 20), (20, 26), (12, 34)], fill=(156, 236, 255, 255), width=3)
    d.line([(54, 20), (44, 26), (52, 34)], fill=(156, 236, 255, 255), width=3)
    d.rounded_rectangle((14, 46, 50, 56), radius=4, fill=(68, 114, 178, 255), outline=(255, 236, 138, 255), width=2)
    icons["florae_collider"] = im

    # 10. fusion_craft
    im = icon_frame((72, 22, 92), (24, 10, 44), (255, 224, 104))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((10, 34, 54, 54), radius=6, fill=(142, 58, 196, 255), outline=(255, 232, 122, 255), width=2)
    d.rectangle((14, 38, 23, 50), fill=(255, 110, 42, 255))
    d.rectangle((24, 38, 32, 50), fill=(96, 214, 255, 255))
    d.rectangle((33, 38, 41, 50), fill=(255, 228, 78, 255))
    d.rectangle((42, 38, 50, 50), fill=(192, 104, 255, 255))
    d.polygon([(32, 8), (22, 32), (42, 32)], fill=(255, 232, 98, 255), outline=(255, 255, 255, 255))
    icons["fusion_craft"] = im

    OUT_ICONS.mkdir(parents=True, exist_ok=True)
    for name, icon in icons.items():
        icon.save(OUT_ICONS / f"{name}.png")
    return icons


def checker_bg(w: int, h: int, dark: bool) -> Image.Image:
    c1 = (30, 32, 40) if dark else (204, 208, 216)
    c2 = (44, 48, 58) if dark else (228, 232, 240)
    bg = Image.new("RGBA", (w, h), (*c1, 255))
    draw = ImageDraw.Draw(bg)
    cell = 16
    for y in range(0, h, cell):
        for x in range(0, w, cell):
            if ((x // cell) + (y // cell)) % 2 == 1:
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(*c2, 255))
    return bg


def write_contact_sheets(sheets: dict[str, Image.Image], icons: dict[str, Image.Image]):
    PREVIEW_DIR.mkdir(parents=True, exist_ok=True)
    pad = 12
    label_h = 18
    scale = 2
    max_w = max(im.width * scale for im in sheets.values()) + pad * 2
    total_h = pad + sum(im.height * scale + label_h + pad for im in sheets.values())

    for dark, fname in ((True, "contact_sheet_dark.png"), (False, "contact_sheet_light.png")):
        canvas = checker_bg(max_w, total_h, dark)
        draw = ImageDraw.Draw(canvas)
        y = pad
        fg = (240, 244, 255, 255) if dark else (24, 28, 36, 255)
        for name, im in sheets.items():
            draw.text((pad, y), f"{name} ({im.width}x{im.height})", fill=fg)
            y += label_h
            up = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
            canvas.alpha_composite(up, (pad, y))
            y += up.height + pad
        canvas.save(PREVIEW_DIR / fname)

    # Icons contact sheet (2x5 grid at 2x scale)
    cols, rows = 5, 2
    iw, ih = 128, 128
    ic = checker_bg(cols * (iw + 16) + 16, rows * (ih + 32) + 16, True)
    draw = ImageDraw.Draw(ic)
    for idx, (name, im) in enumerate(icons.items()):
        cx = 16 + (idx % cols) * (iw + 16)
        cy = 16 + (idx // cols) * (ih + 32)
        draw.text((cx, cy), name, fill=(240, 244, 255, 255))
        ic.alpha_composite(im.resize((iw, ih), Image.NEAREST), (cx, cy + 16))
    ic.save(PREVIEW_DIR / "icons_sheet.png")


def main():
    OUT_VFX.mkdir(parents=True, exist_ok=True)
    sheets = {
        "broom_sweep.png": gen_broom_sweep(),
        "stardust_star.png": gen_stardust_star(),
        "shadow_cloak.png": gen_shadow_cloak(),
        "flyswatter_slam.png": gen_flyswatter_slam(),
        "familiar_puppets.png": gen_familiar_puppets(),
        "lava_pool.png": gen_lava_pool(),
        "lava_geyser.png": gen_lava_geyser(),
        "acid_cloud.png": gen_acid_cloud(),
        "acid_rain_sheet.png": gen_acid_rain_sheet(),
        "anti_gravity_sigil.png": gen_sigil(DARK_PLUM_RAMP, 111, 6),
        "anti_gravity_beam.png": gen_anti_gravity_beam(),
        "furnace_body.png": gen_furnace_body(),
        "mega_drill_body.png": gen_mega_drill_body(),
        "collider_body.png": gen_collider_body(),
        "fusion_roller.png": gen_fusion_roller(),
        "bitter_explosion.png": gen_bitter_explosion(),
        "witch_sigil.png": gen_sigil(WITCH_CANDY_RAMP, 222, 5),
    }
    for name, im in sheets.items():
        im.save(OUT_VFX / name)
    gen_held_flyswatter()
    icons = gen_skill_icons()
    write_contact_sheets(sheets, icons)
    print(f"Generated {len(sheets)} Witch VFX atlases, 1 held flyswatter weapon, and {len(icons)} 64x64 skill icons.")


if __name__ == "__main__":
    main()

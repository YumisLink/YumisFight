#!/usr/bin/env python3
"""Generate pixel-art textures for Sluice Tower 20F boss Roxy and her 3D hat & sapphire staff."""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT_DIR = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/entity"


def fill(img: Image.Image, x0: int, y0: int, w: int, h: int, color: tuple[int, int, int, int]) -> None:
    px = img.load()
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + w):
            px[x, y] = color


def put(img: Image.Image, x: int, y: int, color: tuple[int, int, int, int]) -> None:
    img.putpixel((x, y), color)


def fill_box_uv(
    img: Image.Image,
    u: int,
    v: int,
    w: int,
    h: int,
    d: int,
    top: tuple[int, int, int, int],
    bottom: tuple[int, int, int, int],
    front: tuple[int, int, int, int],
    back: tuple[int, int, int, int],
    side: tuple[int, int, int, int],
) -> None:
    """Fill a standard Minecraft box UV layout at (u, v) with dimensions (w, h, d)."""
    fill(img, u + d, v, w, d, top)
    fill(img, u + d + w, v, w, d, bottom)
    fill(img, u, v + d, d, h, side)
    fill(img, u + d, v + d, w, h, front)
    fill(img, u + d + w, v + d, d, h, side)
    fill(img, u + d + w + d, v + d, w, h, back)


def build_roxy_skin() -> Image.Image:
    """Build 64x64 PLAYER_SLIM skin for Roxy Migurdia (blue hair twin braids, mage cloak & tunic)."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # Palette
    skin = (252, 232, 219, 255)
    skin_shade = (238, 206, 189, 255)
    skin_blush = (247, 187, 184, 255)
    mouth = (224, 152, 148, 255)

    hair_base = (76, 121, 186, 255)
    hair_hi = (114, 162, 226, 255)
    hair_mid = (62, 102, 162, 255)
    hair_dark = (43, 73, 124, 255)
    hair_deep = (31, 52, 92, 255)

    eye_dark = (34, 62, 118, 255)
    eye_mid = (68, 128, 208, 255)
    eye_hi = (158, 218, 255, 255)
    eye_white = (248, 252, 255, 255)

    robe_light = (230, 236, 246, 255)
    robe_mid = (204, 214, 232, 255)
    robe_shade = (170, 184, 208, 255)

    cloak_hi = (104, 118, 148, 255)
    cloak_base = (76, 89, 118, 255)
    cloak_dark = (55, 65, 90, 255)
    cloak_trim = (164, 186, 216, 255)

    gold = (236, 206, 124, 255)
    sapphire = (74, 182, 255, 255)
    belt = (102, 74, 58, 255)
    belt_dark = (74, 52, 40, 255)
    ribbon = (228, 240, 255, 255)

    tights = (52, 58, 76, 255)
    tights_dark = (38, 42, 58, 255)
    boot = (78, 60, 58, 255)
    boot_dark = (52, 39, 39, 255)
    boot_hi = (106, 84, 78, 255)

    # ===== 1. HEAD BASE (8x8x8 at 0,0) =====
    fill_box_uv(img, 0, 0, 8, 8, 8, hair_base, skin_shade, skin, hair_base, hair_base)

    # Hair crown highlights (top: 8..15, 0..7)
    for x in range(8, 16):
        put(img, x, 1, hair_hi if x % 2 == 0 else hair_base)
        put(img, x, 2, hair_hi)
        put(img, x, 6, hair_mid)
        put(img, x, 7, hair_dark)
    # Back hair flowing streaks (24..31, 8..15)
    for x in range(24, 32):
        col = hair_hi if x in (25, 28, 30) else (hair_dark if x in (24, 27, 31) else hair_base)
        for y in range(8, 16):
            put(img, x, y, hair_dark if y == 15 else (hair_mid if y >= 13 else col))
    # Side hair (right: 0..7, 8..15; left: 16..23, 8..15)
    for ox in (0, 16):
        for y in range(8, 16):
            for x in range(ox, ox + 8):
                is_front_edge = (ox == 0 and x >= 6) or (ox == 16 and x <= 17)
                if is_front_edge and y >= 11:
                    put(img, x, y, skin if y <= 14 else skin_shade)
                else:
                    put(img, x, y, hair_hi if y in (9, 10) else (hair_dark if y == 15 else hair_base))

    # Face (8..15, 8..15)
    # Bangs across top of forehead
    fill(img, 8, 8, 8, 2, hair_base)
    put(img, 8, 10, hair_mid)
    put(img, 9, 10, hair_hi)
    put(img, 11, 10, hair_mid)
    put(img, 12, 10, hair_hi)
    put(img, 14, 10, hair_mid)
    put(img, 15, 10, hair_dark)
    # Side face-framing hair locks
    for y in range(10, 16):
        put(img, 8, y, hair_mid if y < 14 else hair_dark)
        put(img, 15, y, hair_mid if y < 14 else hair_dark)
    # Eyes (large gentle blue anime eyes at rows 12-13)
    # Left eye (viewer left: x=9..10)
    put(img, 9, 11, hair_dark)
    put(img, 10, 11, eye_dark)
    put(img, 9, 12, eye_white)
    put(img, 10, 12, eye_dark)
    put(img, 9, 13, eye_hi)
    put(img, 10, 13, eye_mid)
    # Right eye (viewer right: x=13..14)
    put(img, 13, 11, eye_dark)
    put(img, 14, 11, hair_dark)
    put(img, 13, 12, eye_dark)
    put(img, 14, 12, eye_white)
    put(img, 13, 13, eye_mid)
    put(img, 14, 13, eye_hi)
    # Soft blush & gentle mouth
    put(img, 9, 14, skin_blush)
    put(img, 14, 14, skin_blush)
    put(img, 11, 14, mouth)
    put(img, 12, 14, mouth)
    put(img, 10, 15, skin_shade)
    put(img, 13, 15, skin_shade)

    # ===== 2. HEAD OVERLAY (32..63, 0..15): bangs depth & twin braid roots =====
    # Front bangs overlay (40..47, 8..15)
    for x in range(40, 48):
        put(img, x, 8, hair_hi if x in (41, 44, 46) else hair_base)
    put(img, 40, 9, hair_dark)
    put(img, 41, 9, hair_hi)
    put(img, 43, 9, hair_base)
    put(img, 44, 9, hair_hi)
    put(img, 46, 9, hair_base)
    put(img, 47, 9, hair_dark)
    # Twin braid side starts on head overlay
    for y in range(10, 16):
        put(img, 40, y, hair_hi if y % 2 == 0 else hair_mid)
        put(img, 47, y, hair_hi if y % 2 == 0 else hair_mid)
    # Side braid ribbons at bottom of head sides
    put(img, 38, 14, ribbon)
    put(img, 39, 14, hair_hi)
    put(img, 38, 15, hair_dark)
    put(img, 39, 15, hair_base)
    put(img, 48, 14, hair_hi)
    put(img, 49, 14, ribbon)
    put(img, 48, 15, hair_base)
    put(img, 49, 15, hair_dark)
    # Back hair volume overlay (56..63, 8..15)
    for x in range(56, 64):
        put(img, x, 8, hair_hi if x % 2 == 0 else hair_base)
        put(img, x, 14, hair_mid)
        put(img, x, 15, hair_dark)

    # ===== 3. TORSO BASE (8x12x4 at 16,16) =====
    fill_box_uv(img, 16, 16, 8, 12, 4, cloak_dark, robe_shade, robe_light, cloak_base, cloak_dark)
    # Front torso (20..27, 20..31): white-lavender mage blouse + slate bodice + belt + skirt top
    fill(img, 20, 20, 8, 2, robe_light)
    put(img, 23, 20, skin)
    put(img, 24, 20, skin)
    put(img, 22, 21, cloak_trim)
    put(img, 23, 21, sapphire)
    put(img, 24, 21, sapphire)
    put(img, 25, 21, cloak_trim)
    # Corset/vest section (rows 22..26)
    fill(img, 20, 22, 8, 5, cloak_base)
    fill(img, 22, 22, 4, 5, robe_light)
    for y in range(22, 27):
        put(img, 21, y, cloak_hi)
        put(img, 26, y, cloak_hi)
        put(img, 23, y, robe_mid if y % 2 == 0 else robe_light)
        put(img, 24, y, robe_mid if y % 2 == 0 else robe_light)
    # Belt (row 27)
    fill(img, 20, 27, 8, 1, belt)
    put(img, 23, 27, gold)
    put(img, 24, 27, gold)
    # Skirt flare (rows 28..31)
    for y in range(28, 32):
        for x in range(20, 28):
            pleat = robe_shade if (x - 20) % 2 == 0 else robe_light
            if y == 31:
                pleat = cloak_trim if (x - 20) % 2 == 1 else cloak_base
            put(img, x, y, pleat)
    # Back torso (32..39, 20..31)
    fill(img, 32, 20, 8, 8, cloak_base)
    for x in range(32, 40):
        put(img, x, 27, belt_dark)
        for y in range(28, 32):
            put(img, x, y, robe_mid if x % 2 == 0 else robe_shade)
            if y == 31:
                put(img, x, y, cloak_base)

    # ===== 4. TORSO OVERLAY (16..40, 32..47): Signature mage capelet & twin blue braids =====
    # Front jacket overlay (20..27, 36..47)
    # Shoulder capelet opening in V shape
    for x in range(20, 28):
        put(img, x, 36, cloak_hi)
        put(img, x, 37, cloak_base)
    fill(img, 20, 38, 2, 3, cloak_base)
    fill(img, 26, 38, 2, 3, cloak_base)
    put(img, 22, 38, cloak_trim)
    put(img, 25, 38, cloak_trim)
    put(img, 23, 37, gold)
    put(img, 24, 37, gold)
    # Twin long blue braids cascading down front chest! (x=21 and x=26, rows 36..43)
    for y in range(36, 44):
        c1 = hair_hi if y % 2 == 0 else hair_base
        c2 = hair_dark if y % 2 == 0 else hair_mid
        put(img, 21, y, c1)
        put(img, 26, y, c1)
        if y == 42:
            put(img, 21, y, ribbon)
            put(img, 26, y, ribbon)
        elif y == 43:
            put(img, 21, y, c2)
            put(img, 26, y, c2)
    # Back capelet & flowing blue hair down the back (32..39, 36..47)
    fill(img, 32, 36, 8, 6, cloak_base)
    for x in range(32, 40):
        put(img, x, 36, cloak_hi)
        put(img, x, 41, cloak_trim)
    # Back hair ends peeking below capelet
    for x in range(33, 39):
        put(img, x, 42, hair_base if x % 2 == 0 else hair_mid)
        put(img, x, 43, hair_dark)
    # Side capelet drape (16..19 & 28..31, 36..41)
    for ox in (16, 28):
        fill(img, ox, 36, 4, 5, cloak_base)
        fill(img, ox, 41, 4, 1, cloak_trim)

    # ===== 5. ARMS (SLIM 3x12x4) =====
    # Right arm base at (40,16), Left arm base at (32,48)
    for ax, ay in ((40, 16), (32, 48)):
        fill_box_uv(img, ax, ay, 3, 12, 4, cloak_base, skin, robe_light, robe_mid, robe_mid)
        # Upper shoulder covered by cloak
        fill(img, ax, ay + 4, 14, 3, cloak_base)
        fill(img, ax, ay + 7, 14, 1, cloak_trim)
        # Lower ivory sleeve
        fill(img, ax, ay + 8, 14, 4, robe_light)
        # Sleeve cuff accent
        fill(img, ax, ay + 12, 14, 1, cloak_base)
        fill(img, ax, ay + 13, 14, 1, cloak_trim)
        # Delicate skin hand at bottom 2 rows
        fill(img, ax, ay + 14, 14, 2, skin)
        fill(img, ax + 4, ay + 15, 3, 1, skin_shade)

    # Arm overlays (Right sleeve at 40,32; Left sleeve at 48,48): draped mage cuff & cape shoulder
    for ax, ay in ((40, 32), (48, 48)):
        fill(img, ax, ay + 4, 14, 2, cloak_hi)
        fill(img, ax, ay + 6, 14, 1, cloak_trim)
        fill(img, ax, ay + 12, 14, 2, cloak_dark)
        fill(img, ax, ay + 13, 14, 1, sapphire)

    # ===== 6. LEGS (4x12x4) =====
    # Right leg base at (0,16), Left leg base at (16,48)
    for lx, ly in ((0, 16), (16, 48)):
        fill_box_uv(img, lx, ly, 4, 12, 4, robe_mid, boot_dark, tights, tights_dark, tights)
        # Skirt continuation top 2 rows
        fill(img, lx, ly + 4, 16, 2, robe_light)
        fill(img, lx, ly + 6, 16, 1, cloak_trim)
        # Dark stockings middle
        fill(img, lx, ly + 7, 16, 4, tights)
        for x in range(lx, lx + 16, 4):
            fill(img, x, ly + 7, 1, 4, tights_dark)
        # Traveling mage boots bottom 5 rows
        fill(img, lx, ly + 11, 16, 1, boot_hi)
        fill(img, lx, ly + 12, 16, 3, boot)
        fill(img, lx, ly + 15, 16, 1, boot_dark)
        # Front boot lacing / gold clasp
        put(img, lx + 5, ly + 11, gold)
        put(img, lx + 6, ly + 11, gold)
        put(img, lx + 5, ly + 13, boot_hi)
        put(img, lx + 6, ly + 13, boot_hi)

    # Leg overlays: flowing flared skirt hem (Right at 0,32; Left at 0,48)
    for lx, ly in ((0, 32), (0, 48)):
        fill(img, lx, ly + 4, 16, 3, robe_mid)
        for x in range(lx, lx + 16):
            if x % 2 == 0:
                put(img, x, ly + 4, robe_light)
                put(img, x, ly + 5, robe_light)
            put(img, x, ly + 6, cloak_base if x % 2 == 0 else cloak_trim)

    return img


def build_roxy_gear() -> Image.Image:
    """Build 64x64 texture atlas for Roxy's custom 3D Witch Hat and Sapphire Water-Dragon Staff."""
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # Witch hat palette (slate-indigo felt, royal blue band, pale silver trim, gold clasp)
    hat_hi = (98, 112, 146, 255)
    hat_base = (72, 84, 116, 255)
    hat_mid = (58, 69, 98, 255)
    hat_dark = (43, 51, 76, 255)
    hat_under = (34, 41, 62, 255)
    hat_rim = (156, 184, 222, 255)
    band_blue = (54, 108, 198, 255)
    band_hi = (96, 158, 240, 255)
    gold = (242, 212, 126, 255)
    gold_dark = (188, 152, 74, 255)

    # Staff palette (dark walnut wood, silver-aqua dragon prongs, radiant sapphire gem)
    wood_hi = (128, 92, 66, 255)
    wood_base = (96, 66, 46, 255)
    wood_dark = (68, 46, 32, 255)
    silver_hi = (212, 236, 252, 255)
    silver_base = (158, 194, 224, 255)
    silver_dark = (104, 138, 174, 255)

    gem_core = (224, 250, 255, 255)
    gem_bright = (118, 216, 255, 255)
    gem_mid = (52, 162, 248, 255)
    gem_deep = (28, 104, 212, 255)
    aura_glow = (128, 226, 255, 130)

    # 1. Hat brim: 15x1x15 at texOffs(0, 0) -> top(15..29,0..14), bot(30..44,0..14), sides(0..59,15)
    fill_box_uv(img, 0, 0, 15, 1, 15, hat_base, hat_under, hat_rim, hat_rim, hat_rim)
    # Subtle woven shading & silver-blue border on brim top
    for y in range(0, 15):
        for x in range(15, 30):
            dx = abs(x - 22)
            dy = abs(y - 7)
            r = max(dx, dy)
            if r == 7:
                put(img, x, y, hat_rim if (x + y) % 2 == 0 else hat_hi)
            elif r == 6:
                put(img, x, y, hat_mid)
            elif r <= 3:
                put(img, x, y, hat_dark)
            elif (x + y) % 3 == 0:
                put(img, x, y, hat_hi)

    # 2. Crown base: 9x4x9 at texOffs(0, 16) -> w=9, h=4, d=9 -> sides rows 25..28
    fill_box_uv(img, 0, 16, 9, 4, 9, hat_hi, hat_under, hat_base, hat_mid, hat_mid)
    # Ribbon band along bottom row of crown_base (y=27..28 across x=0..35)
    for x in range(0, 36):
        put(img, x, 27, band_hi if x % 2 == 0 else band_blue)
        put(img, x, 28, band_blue)
        if x % 3 == 0:
            put(img, x, 25, hat_hi)
    # Front golden sapphire buckle at center of front face (x=9..17, center=13)
    put(img, 12, 27, gold)
    put(img, 13, 27, gem_bright)
    put(img, 14, 27, gold)
    put(img, 12, 28, gold_dark)
    put(img, 13, 28, gem_mid)
    put(img, 14, 28, gold_dark)

    # 3. Crown mid: 6x4x6 at texOffs(36, 16) -> w=6, h=4, d=6 -> x:36..59, y:16..25
    fill_box_uv(img, 36, 16, 6, 4, 6, hat_hi, hat_dark, hat_base, hat_dark, hat_mid)
    for x in range(36, 60, 2):
        put(img, x, 22, hat_hi)
        put(img, x, 25, hat_dark)

    # 4. Crown tip: 3x5x3 at texOffs(36, 26) -> w=3, h=5, d=3 -> x:36..47, y:26..33
    fill_box_uv(img, 36, 26, 3, 5, 3, hat_hi, hat_dark, hat_hi, hat_mid, hat_base)
    for x in range(36, 48):
        put(img, x, 29, hat_rim)

    # 5. Hat ribbon streamer: 1x4x1 at texOffs(48, 26) -> w=1, h=4, d=1 -> x:48..51, y:26..30
    fill_box_uv(img, 48, 26, 1, 4, 1, band_hi, band_blue, band_hi, band_blue, band_blue)
    for x in range(48, 52):
        put(img, x, 30, gold)

    # 6. Staff shaft: 2x24x2 at texOffs(0, 32) -> w=2, h=24, d=2 -> x:0..7, y:32..57
    fill_box_uv(img, 0, 32, 2, 24, 2, silver_hi, silver_dark, wood_base, wood_dark, wood_hi)
    for y in range(34, 58):
        for x in range(0, 8):
            if y in (34, 35, 42, 50, 56, 57):
                put(img, x, y, silver_hi if y in (34, 42, 56) else silver_base)
            elif y == 43:
                put(img, x, y, gem_bright)
            else:
                put(img, x, y, wood_hi if (x + y) % 3 == 0 else (wood_dark if x % 2 == 0 else wood_base))

    # 7. Staff collar: 3x2x3 at texOffs(8, 32) -> w=3, h=2, d=3 -> x:8..19, y:32..36
    fill_box_uv(img, 8, 32, 3, 2, 3, silver_hi, silver_dark, silver_base, silver_dark, silver_hi)
    for x in range(8, 20, 2):
        put(img, x, 35, gold)
        put(img, x, 36, silver_dark)

    # 8. Dragon-horn prongs lower: 1x4x1 at texOffs(20, 32) -> x:20..23, y:32..36
    fill_box_uv(img, 20, 32, 1, 4, 1, silver_hi, silver_dark, silver_hi, silver_base, silver_base)
    for x in range(20, 24):
        put(img, x, 33, gem_bright)

    # 9. Dragon-horn prongs upper: 1x3x1 at texOffs(24, 32) -> x:24..27, y:32..35
    fill_box_uv(img, 24, 32, 1, 3, 1, gem_core, silver_base, silver_hi, silver_base, silver_hi)
    for x in range(24, 28):
        put(img, x, 33, gem_bright)

    # 10. Staff tassel: 1x4x1 at texOffs(28, 32) -> x:28..31, y:32..36
    fill_box_uv(img, 28, 32, 1, 4, 1, band_hi, band_blue, band_hi, band_blue, band_blue)
    for x in range(28, 32):
        put(img, x, 36, gem_bright)

    # 11. Sapphire core gem: 3x3x3 at texOffs(8, 40) -> w=3, h=3, d=3 -> x:8..19, y:40..45
    fill_box_uv(img, 8, 40, 3, 3, 3, gem_core, gem_deep, gem_bright, gem_mid, gem_mid)
    for face_x in (8, 11, 14, 17):
        put(img, face_x + 1, 44, gem_core)
        put(img, face_x, 43, gem_bright)
        put(img, face_x + 2, 45, gem_deep)

    # 12. Sapphire translucent aura: 4x4x4 at texOffs(8, 48) -> w=4, h=4, d=4 -> x:8..23, y:48..55
    fill_box_uv(img, 8, 48, 4, 4, 4, aura_glow, aura_glow, aura_glow, aura_glow, aura_glow)

    return img


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    skin = build_roxy_skin()
    skin_path = OUT_DIR / "sluice_tower_roxy.png"
    skin.save(skin_path)
    gear = build_roxy_gear()
    gear_path = OUT_DIR / "sluice_tower_roxy_gear.png"
    gear.save(gear_path)
    print(f"Generated {skin_path} ({skin.size}) and {gear_path} ({gear.size})")


if __name__ == "__main__":
    main()

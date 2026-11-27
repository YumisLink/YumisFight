#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""天空城 · 龙人之塔首领「鲁卡斯」精细骨骼模型与贴图制作源（正式源）。

产出：
- src/main/resources/assets/yumis_dungeon_and_fight/dungeons/dragon_tower/lucas.json
- src/main/resources/assets/yumis_dungeon_and_fight/textures/entity/dragon_tower/lucas.png
- src/main/resources/assets/yumis_dungeon_and_fight/textures/entity/dragon_tower/lucas_clone.png
- temp/lucas-model/preview.png（本体与幻影双贴图 3× 预览）

规格对齐 GblModel.java：
- Y 向上、+Z 正面、16 单位 = 1 格；pivot 相对父级，box 相对骨骼 pivot；
- bones[0] 固定为 root（parent=""，pivot=[0,0,0]）；所有 effect=false；
- 贴图 128x128 RGBA PNG，光照左上亮右下暗；本体深绿龙鳞 + 青铜金重甲 + 紫晶雷核 + 龙冠雷枪；
  幻影贴图共享同一套 UV，转换为半透明苍青紫雷影色调，让玩家一眼区分本体与分身。
"""

import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'yumis_dungeon_and_fight')
DUNGEON_DIR = os.path.join(ASSET, 'dungeons', 'dragon_tower')
TEXTURE_DIR = os.path.join(ASSET, 'textures', 'entity', 'dragon_tower')
PREVIEW_DIR = os.path.join(ROOT, 'temp', 'lucas-model')

MODEL_ID = 'lucas'
MODEL_NAME = '鲁卡斯'
MODEL_ROLE = '龙人之塔首领 · 雷光龙枪王'
COORDINATES = 'Y up, +Z front, 16 units/block; local XYZ degrees'
TEXTURE_SIZE = 128
EPS = 1e-6
HEIGHT_MIN, HEIGHT_MAX = 43.0, 52.0


class AssetError(Exception):
    pass


def fail(message):
    raise AssetError(message)


class Canvas:
    def __init__(self, width, height):
        self.w = width
        self.h = height
        self.px = [[(0, 0, 0, 0)] * width for _ in range(height)]

    def set(self, x, y, rgba):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = rgba

    def rows(self):
        out = []
        for row in self.px:
            buf = bytearray()
            for r, g, b, a in row:
                buf.append(r)
                buf.append(g)
                buf.append(b)
                buf.append(a)
            out.append(bytes(buf))
        return out


def clamp_byte(v):
    return max(0, min(255, int(round(v))))


def shade(rgb, factor, alpha=255):
    return (
        clamp_byte(rgb[0] * factor),
        clamp_byte(rgb[1] * factor),
        clamp_byte(rgb[2] * factor),
        alpha,
    )


def mix_rgb(a, b, t):
    return (
        clamp_byte(a[0] + (b[0] - a[0]) * t),
        clamp_byte(a[1] + (b[1] - a[1]) * t),
        clamp_byte(a[2] + (b[2] - a[2]) * t),
    )


def rect(cv, x, y, w, h, rgba):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            cv.set(xx, yy, rgba)


def png_chunk(kind, payload):
    crc = zlib.crc32(kind + payload) & 0xFFFFFFFF
    return struct.pack('>I', len(payload)) + kind + payload + struct.pack('>I', crc)


def write_png(path, width, height, rows):
    raw = b''.join(b'\x00' + r for r in rows)
    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    data = b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', ihdr) + png_chunk(b'IDAT', zlib.compress(raw, 9)) + png_chunk(b'IEND', b'')
    with open(path, 'wb') as fh:
        fh.write(data)


# ---------------------------------------------------------------- 调色板

SCALE_BASE = (42, 92, 68)
SCALE_LIGHT = (64, 128, 92)
SCALE_DARK = (24, 58, 44)
BELLY_BASE = (74, 124, 96)

BRONZE_BASE = (122, 82, 46)
BRONZE_DARK = (74, 48, 28)
BRONZE_LIGHT = (168, 122, 68)

GOLD_BASE = (232, 188, 86)
GOLD_LIGHT = (255, 232, 146)
GOLD_DARK = (166, 122, 44)

IRON_DARK = (54, 60, 74)
IRON_LIGHT = (110, 124, 146)

CAPE_BASE = (46, 44, 86)
CAPE_DARK = (28, 26, 56)
CAPE_TRIM = (196, 152, 68)

CRYSTAL_CORE = (168, 124, 255)
CRYSTAL_BRIGHT = (146, 232, 255)
CRYSTAL_HOT = (246, 248, 255)
EYE_GOLD = (255, 220, 88)
HORN_BONE = (214, 200, 166)


# ---------------------------------------------------------------- UV 展开与绘制

def box_faces(u, v, w, h, d):
    w, h, d = int(round(w)), int(round(h)), int(round(d))
    return {
        'top':    (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right':  (u, v + d, d, h),
        'front':  (u + d, v + d, w, h),
        'left':   (u + d + w, v + d, d, h),
        'back':   (u + d + w + d, v + d, w, h),
    }


def paint_shaded_box(cv, faces, base_rgb, hi_rgb=None, lo_rgb=None, pattern='none', accent=None):
    face_mul = {'top': 1.15, 'front': 1.05, 'left': 0.96, 'right': 0.88, 'back': 0.82, 'bottom': 0.72}
    hi_rgb = hi_rgb or shade(base_rgb, 1.22)[:3]
    lo_rgb = lo_rgb or shade(base_rgb, 0.72)[:3]
    for fname, (fx, fy, fw, fh) in faces.items():
        mul = face_mul[fname]
        for ly in range(fh):
            for lx in range(fw):
                grad = 1.0 + (0.10 if ly < max(1, fh // 3) else -0.08 if ly >= fh - max(1, fh // 3) else 0.0)
                grad += 0.05 if lx == 0 else (-0.05 if lx == fw - 1 else 0.0)
                rgb = shade(base_rgb, mul * grad)[:3]
                if pattern == 'scales':
                    if ((lx + (ly % 2)) % 2 == 0) and ly % 2 == 0:
                        rgb = mix_rgb(rgb, hi_rgb, 0.32)
                    elif (lx + ly) % 3 == 0:
                        rgb = mix_rgb(rgb, lo_rgb, 0.25)
                elif pattern == 'armor':
                    if lx == 0 or ly == 0:
                        rgb = mix_rgb(rgb, hi_rgb, 0.42)
                    elif lx == fw - 1 or ly == fh - 1:
                        rgb = mix_rgb(rgb, lo_rgb, 0.45)
                    elif accent and (ly == fh // 2 or lx == fw // 2):
                        rgb = mix_rgb(rgb, accent, 0.35)
                elif pattern == 'crystal':
                    dist = abs(lx - (fw - 1) / 2.0) + abs(ly - (fh - 1) / 2.0)
                    span = max(1.0, (fw + fh) * 0.45)
                    t = max(0.0, 1.0 - dist / span)
                    rgb = mix_rgb(base_rgb, hi_rgb, t)
                    if t > 0.72:
                        rgb = mix_rgb(rgb, CRYSTAL_HOT, (t - 0.72) / 0.28)
                elif pattern == 'cape':
                    if ly >= fh - 2:
                        rgb = mix_rgb(CAPE_TRIM, GOLD_LIGHT, 0.3 if lx % 2 == 0 else 0.0)
                    elif lx == 0 or lx == fw - 1:
                        rgb = mix_rgb(rgb, CAPE_TRIM, 0.55)
                    elif (lx + ly) % 4 == 0:
                        rgb = mix_rgb(rgb, lo_rgb, 0.28)
                cv.set(fx + lx, fy + ly, (rgb[0], rgb[1], rgb[2], 255))


def paint_head(cv, faces, _cube):
    paint_shaded_box(cv, faces, SCALE_BASE, SCALE_LIGHT, SCALE_DARK, 'scales')
    fx, fy, fw, fh = faces['front']
    # 龙眉与金色竖瞳
    for x in (1, 2, fw - 3, fw - 2):
        cv.set(fx + x, fy + 2, (SCALE_DARK[0], SCALE_DARK[1], SCALE_DARK[2], 255))
    for ex in (1, fw - 3):
        rect(cv, fx + ex, fy + 3, 2, 2, (EYE_GOLD[0], EYE_GOLD[1], EYE_GOLD[2], 255))
        cv.set(fx + ex + (1 if ex == 1 else 0), fy + 3, (255, 252, 220, 255))
        cv.set(fx + ex + (0 if ex == 1 else 1), fy + 4, (58, 32, 12, 255))
    # 额间紫电印记
    mid = fw // 2
    cv.set(fx + mid - 1, fy + 1, (CRYSTAL_BRIGHT[0], CRYSTAL_BRIGHT[1], CRYSTAL_BRIGHT[2], 255))
    cv.set(fx + mid, fy + 1, (CRYSTAL_CORE[0], CRYSTAL_CORE[1], CRYSTAL_CORE[2], 255))


def paint_chestplate(cv, faces, _cube):
    paint_shaded_box(cv, faces, BRONZE_BASE, BRONZE_LIGHT, BRONZE_DARK, 'armor', GOLD_BASE)
    fx, fy, fw, fh = faces['front']
    # 金边胸纹与中央紫蓝雷核镶嵌
    rect(cv, fx + 1, fy + 1, fw - 2, 1, (GOLD_BASE[0], GOLD_BASE[1], GOLD_BASE[2], 255))
    rect(cv, fx + 1, fy + fh - 2, fw - 2, 1, (GOLD_DARK[0], GOLD_DARK[1], GOLD_DARK[2], 255))
    cx, cy = fx + fw // 2 - 2, fy + fh // 2 - 2
    rect(cv, cx, cy, 4, 4, (GOLD_LIGHT[0], GOLD_LIGHT[1], GOLD_LIGHT[2], 255))
    rect(cv, cx + 1, cy + 1, 2, 2, (CRYSTAL_CORE[0], CRYSTAL_CORE[1], CRYSTAL_CORE[2], 255))
    cv.set(cx + 1, cy + 1, (CRYSTAL_HOT[0], CRYSTAL_HOT[1], CRYSTAL_HOT[2], 255))
    cv.set(cx + 2, cy + 2, (CRYSTAL_BRIGHT[0], CRYSTAL_BRIGHT[1], CRYSTAL_BRIGHT[2], 255))


def paint_scale(cv, faces, _cube):
    paint_shaded_box(cv, faces, SCALE_BASE, SCALE_LIGHT, SCALE_DARK, 'scales')


def paint_belly(cv, faces, _cube):
    paint_shaded_box(cv, faces, BELLY_BASE, SCALE_LIGHT, SCALE_DARK, 'scales')


def paint_bronze(cv, faces, _cube):
    paint_shaded_box(cv, faces, BRONZE_BASE, BRONZE_LIGHT, BRONZE_DARK, 'armor', GOLD_BASE)


def paint_gold(cv, faces, _cube):
    paint_shaded_box(cv, faces, GOLD_BASE, GOLD_LIGHT, GOLD_DARK, 'armor', CRYSTAL_BRIGHT)


def paint_iron(cv, faces, _cube):
    paint_shaded_box(cv, faces, IRON_DARK, IRON_LIGHT, (32, 36, 48), 'armor', CRYSTAL_BRIGHT)


def paint_crystal(cv, faces, _cube):
    paint_shaded_box(cv, faces, CRYSTAL_CORE, CRYSTAL_BRIGHT, (96, 62, 180), 'crystal')


def paint_horn(cv, faces, _cube):
    paint_shaded_box(cv, faces, HORN_BONE, (244, 236, 206), (132, 118, 92), 'armor')


def paint_cape(cv, faces, _cube):
    paint_shaded_box(cv, faces, CAPE_BASE, (78, 72, 134), CAPE_DARK, 'cape')


# ---------------------------------------------------------------- 骨骼与方体定义

BONE_ORDER = [
    'root', 'body', 'cape', 'tail', 'tail_tip',
    'head', 'crown', 'arm_l', 'arm_r', 'spear',
    'leg_l', 'leg_r',
]


def cube(name, material, box, paint, rotation=(0, 0, 0)):
    return {
        'name': name,
        'material': material,
        'box': list(box),
        'rotation': list(rotation),
        'uv': [0, 0],
        'paint': paint,
    }


def bone(name, parent, pivot, cubes, rotation=(0, 0, 0)):
    return {
        'name': name,
        'parent': parent,
        'pivot': list(pivot),
        'rotation': list(rotation),
        'effect': False,
        'cubes': cubes,
    }


def build_bones():
    return [
        bone('root', '', [0, 0, 0], []),
        bone('body', 'root', [0, 18, 0], [
            cube('torso_scales', 'scale', [-5, 0, -3, 10, 14, 6], paint_scale),
            cube('chest_armor', 'bronze', [-5.5, 5, -3.5, 11, 9, 7], paint_chestplate),
            cube('chest_core', 'crystal', [-1.5, 8, -4.3, 3, 3, 1], paint_crystal),
            cube('belt', 'gold', [-5.5, 0, -3.5, 11, 3, 7], paint_gold),
            cube('tabard_front', 'cape', [-3, -7, -3.6, 6, 8, 1], paint_cape),
            cube('dorsal_fin', 'gold', [-1, 3, 3, 2, 10, 2], paint_gold),
        ]),
        bone('cape', 'body', [0, 13, 3.2], [
            cube('cape_main', 'cape', [-6, -18, 0, 12, 18, 1], paint_cape, rotation=[10, 0, 0]),
        ]),
        bone('tail', 'body', [0, 2, 3], [
            cube('tail_base', 'scale', [-2, -2, 0, 4, 4, 10], paint_scale, rotation=[-14, 0, 0]),
            cube('tail_ridge', 'gold', [-0.5, 2, 2, 1, 2, 7], paint_gold, rotation=[-14, 0, 0]),
        ]),
        bone('tail_tip', 'tail', [0, -2, 9.5], [
            cube('tail_end', 'scale', [-1.5, -1.5, 0, 3, 3, 8], paint_scale, rotation=[-6, 0, 0]),
            cube('tail_crystal', 'crystal', [-1, -1, 7, 2, 2, 3], paint_crystal),
        ]),
        bone('head', 'body', [0, 14.5, 0], [
            cube('skull', 'scale', [-4, 0, -4, 8, 8, 8], paint_head),
            cube('snout', 'scale', [-3, 0.5, -8, 6, 4, 5], paint_belly),
            cube('jaw', 'bronze', [-2.5, -0.5, -7, 5, 1.5, 4], paint_bronze),
            cube('horn_l', 'horn', [3, 6, 0, 2, 6, 2], paint_horn, rotation=[-18, 0, 16]),
            cube('horn_r', 'horn', [-5, 6, 0, 2, 6, 2], paint_horn, rotation=[-18, 0, -16]),
        ]),
        bone('crown', 'head', [0, 8, 0], [
            cube('crown_band', 'gold', [-4.5, 0, -4.5, 9, 2.5, 9], paint_gold),
            cube('crown_spire', 'crystal', [-1.5, 2.5, -4.2, 3, 5, 2], paint_crystal),
            cube('crown_wing_l', 'gold', [3, 2, -2, 2, 4, 4], paint_gold, rotation=[0, 0, 14]),
            cube('crown_wing_r', 'gold', [-5, 2, -2, 2, 4, 4], paint_gold, rotation=[0, 0, -14]),
        ]),
        bone('arm_l', 'body', [6, 12.5, 0], [
            cube('pauldron_l', 'gold', [-0.5, -1, -3, 5.5, 5, 6], paint_gold, rotation=[0, 0, -8]),
            cube('upper_arm_l', 'scale', [0, -7, -2, 4, 7, 4], paint_scale),
            cube('gauntlet_l', 'bronze', [-0.5, -13, -2.5, 5, 6, 5], paint_bronze),
        ]),
        bone('arm_r', 'body', [-6, 12.5, 0], [
            cube('pauldron_r', 'gold', [-5, -1, -3, 5.5, 5, 6], paint_gold, rotation=[0, 0, 8]),
            cube('upper_arm_r', 'scale', [-4, -7, -2, 4, 7, 4], paint_scale),
            cube('gauntlet_r', 'bronze', [-4.5, -13, -2.5, 5, 6, 5], paint_bronze),
        ]),
        bone('spear', 'arm_r', [-2, -11, -2], [
            cube('spear_shaft', 'iron', [-1, -12, -1, 2, 34, 2], paint_iron, rotation=[90, 0, 0]),
            cube('spear_guard', 'gold', [-3.5, 19, -1.5, 7, 2.5, 3], paint_gold, rotation=[90, 0, 0]),
            cube('spear_core', 'crystal', [-1.5, 21.5, -1.5, 3, 4, 3], paint_crystal, rotation=[90, 0, 0]),
            cube('spear_blade', 'iron', [-2, 25.5, -1, 4, 8, 2], paint_iron, rotation=[90, 0, 0]),
            cube('spear_tip', 'crystal', [-1, 33.5, -0.75, 2, 5, 1.5], paint_crystal, rotation=[90, 0, 0]),
            cube('spear_pommel', 'gold', [-1.5, -14.5, -1.5, 3, 3, 3], paint_gold, rotation=[90, 0, 0]),
        ]),
        bone('leg_l', 'root', [3, 18, 0], [
            cube('thigh_l', 'scale', [-2, -9, -2.5, 4.5, 9, 5], paint_scale),
            cube('greave_l', 'bronze', [-2.5, -16, -3, 5, 8, 6], paint_bronze),
            cube('foot_l', 'gold', [-2.5, -18, -5, 5, 2.5, 7], paint_gold),
        ]),
        bone('leg_r', 'root', [-3, 18, 0], [
            cube('thigh_r', 'scale', [-2.5, -9, -2.5, 4.5, 9, 5], paint_scale),
            cube('greave_r', 'bronze', [-2.5, -16, -3, 5, 8, 6], paint_bronze),
            cube('foot_r', 'gold', [-2.5, -18, -5, 5, 2.5, 7], paint_gold),
        ]),
    ]


def pack_uv(bones):
    """按高降序货架排布 UV，同尺寸同材质左右对称件可自动共享或独立无重叠。"""
    items = []
    for b in bones:
        for c in b['cubes']:
            w, h, d = int(math.ceil(c['box'][3])), int(math.ceil(c['box'][4])), int(math.ceil(c['box'][5]))
            items.append((2 * (w + d), h + d, c))
    items.sort(key=lambda it: (-it[1], -it[0]))
    x, y, row_h = 0, 0, 0
    for uw, uh, c in items:
        if x + uw > TEXTURE_SIZE:
            x = 0
            y += row_h + 1
            row_h = 0
        if y + uh > TEXTURE_SIZE:
            fail('UV 排布超出 %dx%d（卡在 %s）' % (TEXTURE_SIZE, TEXTURE_SIZE, c['name']))
        c['uv'] = [x, y]
        x += uw + 1
        row_h = max(row_h, uh)


def model_height(bones):
    """计算站立静姿最高点（不含水平前指的武器）。"""
    by_name = {b['name']: b for b in bones}
    max_y = 0.0
    for b in bones:
        if b['name'] == 'spear':
            continue
        py = 0.0
        cur = b
        while cur is not None:
            py += cur['pivot'][1]
            cur = by_name.get(cur['parent'])
        for c in b['cubes']:
            max_y = max(max_y, py + c['box'][1] + c['box'][4])
    return max_y


# ---------------------------------------------------------------- 动作关键帧

def P(name, tx=0, ty=0, tz=0, rx=0, ry=0, rz=0, sx=1, sy=1, sz=1):
    return (name, [float(tx), float(ty), float(tz), float(rx), float(ry), float(rz), float(sx), float(sy), float(sz)])


def merged(*entries):
    out = {name: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0] for name in BONE_ORDER}
    for name, vals in entries:
        out[name] = vals
    return out


def sample(kfs, count):
    frames = []
    for i in range(count):
        t = i / float(max(1, count - 1))
        lo, hi = kfs[0], kfs[-1]
        for j in range(len(kfs) - 1):
            if kfs[j][0] <= t <= kfs[j + 1][0]:
                lo, hi = kfs[j], kfs[j + 1]
                break
        span = hi[0] - lo[0]
        f = 0.0 if span <= 1e-6 else (t - lo[0]) / span
        # smoothstep easing
        f = f * f * (3.0 - 2.0 * f)
        frame = []
        for bname in BONE_ORDER:
            a = lo[1][bname]
            b = hi[1][bname]
            frame.append([round(a[k] + (b[k] - a[k]) * f, 3) for k in range(9)])
        frames.append(frame)
    return frames


def clip(seconds, frames):
    return {'seconds': float(seconds), 'frames': frames}


def build_clips():
    clips = {}

    # idle：呼吸起伏 + 尾摆 + 持枪戒备
    clips['idle'] = clip(2.0, sample([
        (0.0, merged(P('arm_r', 0, 0, 0, -24, 0, -6), P('arm_l', 0, 0, 0, -10, 0, 8),
                     P('tail', 0, 0, 0, 0, -8, 0), P('tail_tip', 0, 0, 0, 0, -10, 0),
                     P('cape', 0, 0, 0, 4, 0, 0))),
        (0.5, merged(P('body', 0, 0.6, 0, -2), P('arm_r', 0, 0, 0, -28, 0, -8),
                     P('arm_l', 0, 0, 0, -14, 0, 10),
                     P('tail', 0, 0, 0, 0, 8, 0), P('tail_tip', 0, 0, 0, 0, 12, 0),
                     P('cape', 0, 0, 0, 10, 0, 0))),
        (1.0, merged(P('arm_r', 0, 0, 0, -24, 0, -6), P('arm_l', 0, 0, 0, -10, 0, 8),
                     P('tail', 0, 0, 0, 0, -8, 0), P('tail_tip', 0, 0, 0, 0, -10, 0),
                     P('cape', 0, 0, 0, 4, 0, 0))),
    ], 13))

    # walk：沉稳龙步 + 披风甩动 + 长枪斜握
    clips['walk'] = clip(0.9, sample([
        (0.0, merged(P('leg_l', 0, 0, 0, 26), P('leg_r', 0, 0, 0, -26),
                     P('arm_l', 0, 0, 0, -18, 0, 10), P('arm_r', 0, 0, 0, -20, 0, -8),
                     P('body', 0, 0.5, 0, 6, 4), P('tail', 0, 0, 0, 0, 12), P('cape', 0, 0, 0, 14))),
        (0.25, merged(P('body', 0, -0.4, 0, 8, 0), P('arm_r', 0, 0, 0, -26, 0, -8), P('cape', 0, 0, 0, 18))),
        (0.5, merged(P('leg_l', 0, 0, 0, -26), P('leg_r', 0, 0, 0, 26),
                     P('arm_l', 0, 0, 0, 14, 0, 10), P('arm_r', 0, 0, 0, -34, 0, -8),
                     P('body', 0, 0.5, 0, 6, -4), P('tail', 0, 0, 0, 0, -12), P('cape', 0, 0, 0, 14))),
        (0.75, merged(P('body', 0, -0.4, 0, 8, 0), P('arm_r', 0, 0, 0, -26, 0, -8), P('cape', 0, 0, 0, 18))),
        (1.0, merged(P('leg_l', 0, 0, 0, 26), P('leg_r', 0, 0, 0, -26),
                     P('arm_l', 0, 0, 0, -18, 0, 10), P('arm_r', 0, 0, 0, -20, 0, -8),
                     P('body', 0, 0.5, 0, 6, 4), P('tail', 0, 0, 0, 0, 12), P('cape', 0, 0, 0, 14))),
    ], 13))

    # hurt
    clips['hurt'] = clip(0.35, sample([
        (0.0, merged()),
        (0.4, merged(P('body', 0, -1, 1.5, -14), P('head', 0, 0, 0, -18),
                     P('arm_l', 0, 0, 0, -25, 0, 22), P('arm_r', 0, 0, 0, -35, 0, -22))),
        (1.0, merged()),
    ], 7))

    # death：单膝跪倒再仰倒
    clips['death'] = clip(1.1, sample([
        (0.0, merged()),
        (0.4, merged(P('root', 0, -6, 0), P('body', 0, 0, 0, 24), P('head', 0, 0, 0, 20),
                     P('leg_l', 0, 0, 0, -50), P('leg_r', 0, 0, 0, 30),
                     P('arm_r', 0, 0, 0, -10, 0, -35))),
        (1.0, merged(P('root', 0, -15, 2, -84, 0, 8), P('head', 0, 0, 0, 18),
                     P('arm_l', 0, 0, 0, -30, 0, 55), P('arm_r', 0, 0, 0, -20, 0, -50))),
    ], 11))

    # spear_combo：龙枪连突（横扫 -> 蓄力直线穿刺）
    clips['spear_combo'] = clip(1.4, sample([
        (0.0, merged()),
        (0.22, merged(P('body', 0, 0, 0, 6, -38), P('arm_r', 0, 0, 0, -68, -28, -32),
                      P('arm_l', 0, 0, 0, -30, 20, 22), P('spear', 0, 0, 0, 0, 0, -25, 1.15, 1.15, 1.15))),
        (0.42, merged(P('body', 0, 0, 1.5, 14, 42), P('arm_r', 0, 0, 0, -82, 34, 14),
                      P('arm_l', 0, 0, 0, 16, -24, 18), P('cape', 0, 0, 0, 26, -16),
                      P('spear', 0, 0, 0, 0, 0, 20, 1.25, 1.25, 1.25))),
        (0.62, merged(P('body', 0, -1.5, -1, 10, -24), P('arm_r', 0, 0, 2, -32, 0, -18),
                      P('arm_l', 0, 0, 0, -48, 0, 18), P('leg_r', 0, 0, 0, -24))),
        (0.78, merged(P('body', 0, -1.0, 3.5, 22, 18), P('arm_r', 0, 0, -3, -95, 0, -6),
                      P('arm_l', 0, 0, 0, 24, 0, 24), P('leg_l', 0, 0, 0, 26), P('leg_r', 0, 0, 0, -30),
                      P('spear', 0, 0, -3, 0, 0, 0, 1.32, 1.32, 1.32), P('cape', 0, 0, 0, 34))),
        (1.0, merged()),
    ], 17))

    # dragon_charge：破空冲锋（伏低挺枪极速突刺 + 终结横扫）
    clips['dragon_charge'] = clip(1.6, sample([
        (0.0, merged()),
        (0.28, merged(P('root', 0, -3.5, 0), P('body', 0, 0, 0, 30, -18), P('head', 0, 0, 0, -18),
                      P('arm_r', 0, 0, 2, -78, 0, -14), P('arm_l', 0, 0, 0, 28, 0, 28),
                      P('leg_l', 0, 0, 0, 28), P('leg_r', 0, 0, 0, -38), P('cape', 0, 0, 0, 38),
                      P('spear', 0, 0, -2, 0, 0, 0, 1.28, 1.28, 1.28))),
        (0.68, merged(P('root', 0, -3.0, 1.5), P('body', 0, 0, 0, 36, 6), P('head', 0, 0, 0, -24),
                      P('arm_r', 0, 0, -3, -96, 0, -4), P('arm_l', 0, 0, 0, 42, 0, 36),
                      P('leg_l', 0, 0, 0, -24), P('leg_r', 0, 0, 0, 32), P('cape', 0, 0, 0, 56),
                      P('spear', 0, 0, -4, 0, 0, 0, 1.42, 1.42, 1.42))),
        (0.84, merged(P('body', 0, 0, 0, 10, 46), P('arm_r', 0, 0, 0, -84, 38, 16),
                      P('cape', 0, 0, 0, 32, -20), P('spear', 0, 0, 0, 0, 0, 18, 1.25, 1.25, 1.25))),
        (1.0, merged()),
    ], 17))

    # thunder_discharge：雷电震荡环（缩身蓄雷 -> 猛插龙枪爆出震荡环）
    clips['thunder_discharge'] = clip(1.5, sample([
        (0.0, merged()),
        (0.35, merged(P('root', 0, -3.0, 0), P('body', 0, 0, 0, 22), P('head', 0, 0, 0, 12),
                      P('arm_r', 0, 0, 0, -148, 0, -16), P('arm_l', 0, 0, 0, -125, 0, 26),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.22, 1.22, 1.22),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.3, 1.3, 1.3))),
        (0.62, merged(P('root', 0, -4.5, 0), P('body', 0, 0, 0, 26), P('head', 0, 0, 0, -14),
                      P('arm_r', 0, -2, 0, -32, 0, -24), P('arm_l', 0, 0, 0, -20, 0, 62),
                      P('cape', 0, 0, 0, 48), P('tail', 0, 0, 0, 18, 0),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.35, 1.35, 1.35),
                      P('spear', 0, -4, 0, -90, 0, 0, 1.45, 1.45, 1.45))),
        (1.0, merged()),
    ], 15))

    # thunder_cage：雷霆枪牢（单手举枪敕令 -> 前指落牢）
    clips['thunder_cage'] = clip(1.4, sample([
        (0.0, merged()),
        (0.38, merged(P('body', 0, 0.5, 0, -10, -12), P('head', 0, 0, 0, -14),
                      P('arm_r', 0, 0, 0, -164, 0, -12), P('arm_l', 0, 0, 0, -78, 0, 24),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.25, 1.25, 1.25),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.32, 1.32, 1.32))),
        (0.72, merged(P('body', 0, 0, 1, 14, 14), P('arm_r', 0, 0, 0, -92, 0, -8),
                      P('arm_l', 0, 0, 0, -98, 0, 12), P('cape', 0, 0, 0, 22),
                      P('spear', 0, 0, -2, 0, 0, 0, 1.25, 1.25, 1.25))),
        (1.0, merged()),
    ], 15))

    # sky_spear_rain：天穹雷枪阵（高举龙枪引天雷 -> 连斩敕令）
    clips['sky_spear_rain'] = clip(2.0, sample([
        (0.0, merged()),
        (0.28, merged(P('root', 0, 1.5, 0), P('body', 0, 0, 0, -14), P('head', 0, 0, 0, -26),
                      P('arm_r', 0, 0, 0, -172, 0, -8), P('arm_l', 0, 0, 0, -145, 0, 22),
                      P('cape', 0, 0, 0, 34), P('crown', 0, 0, 0, 0, 0, 0, 1.36, 1.36, 1.36),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.5, 1.5, 1.5))),
        (0.62, merged(P('root', 0, 1.0, 0), P('body', 0, 0, 0, -10, 12), P('head', 0, 0, 0, -22),
                      P('arm_r', 0, 0, 0, -166, 0, -10), P('arm_l', 0, 0, 0, -135, 0, 28),
                      P('cape', 0, 0, 0, 42), P('crown', 0, 0, 0, 0, 0, 0, 1.4, 1.4, 1.4),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.6, 1.6, 1.6))),
        (0.84, merged(P('body', 0, 0, 1, 22), P('arm_r', 0, 0, 0, -56, 0, -12),
                      P('arm_l', 0, 0, 0, -35, 0, 30), P('cape', 0, 0, 0, 30),
                      P('spear', 0, 0, 0, 0, 0, 0, 1.25, 1.25, 1.25))),
        (1.0, merged()),
    ], 17))

    # jump_slam：跃空龙枪俯冲（GblModel 忽略 jump_slam 的 root 位移，实际腾空高度由服务端驱动）
    clips['jump_slam'] = clip(2.0, sample([
        (0.0, merged()),
        (0.18, merged(P('body', 0, -2, 0, 26), P('leg_l', 0, 0, 0, -40), P('leg_r', 0, 0, 0, -40),
                      P('arm_r', 0, 0, 0, -45, 0, -18), P('arm_l', 0, 0, 0, 30, 0, 22))),
        (0.45, merged(P('body', 0, 0, 0, -18), P('head', 0, 0, 0, 24),
                      P('leg_l', 0, 0, 0, 24), P('leg_r', 0, 0, 0, -36),
                      P('arm_r', 0, 0, 0, -168, 0, -12), P('arm_l', 0, 0, 0, -120, 0, 40),
                      P('cape', 0, 0, 0, 62), P('spear', 0, 0, 0, -90, 0, 0, 1.45, 1.45, 1.45))),
        (0.72, merged(P('body', 0, 0, 0, 34), P('head', 0, 0, 0, -12),
                      P('leg_l', 0, 0, 0, -24), P('leg_r', 0, 0, 0, 18),
                      P('arm_r', 0, 0, 0, -110, 0, -6), P('arm_l', 0, 0, 0, 38, 0, 36),
                      P('cape', 0, 0, 0, 74), P('spear', 0, 0, -3, 0, 0, 0, 1.55, 1.55, 1.55))),
        (0.84, merged(P('body', 0, -4, 0, 36), P('leg_l', 0, 0, 0, -52), P('leg_r', 0, 0, 0, 22),
                      P('arm_r', 0, -2, 0, -38, 0, -18), P('arm_l', 0, 0, 0, 18, 0, 48),
                      P('cape', 0, 0, 0, 44), P('spear', 0, -3, 0, -90, 0, 0, 1.35, 1.35, 1.35))),
        (1.0, merged()),
    ], 19))

    # phantom_awakening：30% 低血狂暴转阶段（伏低蓄威 -> 展臂咆哮唤影）
    clips['phantom_awakening'] = clip(2.2, sample([
        (0.0, merged()),
        (0.32, merged(P('root', 0, -3, 0), P('body', 0, 0, 0, 24), P('head', 0, 0, 0, 22),
                      P('arm_r', 0, 0, 0, -24, 0, -44), P('arm_l', 0, 0, 0, -24, 0, 44),
                      P('cape', 0, 0, 0, 22), P('spear', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3))),
        (0.68, merged(P('root', 0, 1.5, 0), P('body', 0, 0, 0, -16), P('head', 0, 0, 0, -32),
                      P('arm_r', 0, 0, 0, -48, 0, -82), P('arm_l', 0, 0, 0, -48, 0, 82),
                      P('cape', 0, 0, 0, 58), P('crown', 0, 0, 0, 0, 0, 0, 1.48, 1.48, 1.48),
                      P('spear', 0, 0, 0, 0, 0, 0, 1.65, 1.65, 1.65))),
        (1.0, merged()),
    ], 17))

    # crown_tempest：终结技 断冠天雷暴（聚雷 -> 双段横空斩裂）
    clips['crown_tempest'] = clip(2.6, sample([
        (0.0, merged()),
        (0.26, merged(P('root', 0, 2, 0), P('body', 0, 0, 0, -16), P('head', 0, 0, 0, -30),
                      P('arm_r', 0, 0, 0, -174, 0, -6), P('arm_l', 0, 0, 0, -162, 0, 14),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.5, 1.5, 1.5),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.68, 1.68, 1.68), P('cape', 0, 0, 0, 52))),
        (0.58, merged(P('root', 0, 2, 0), P('body', 0, 0, 0, -14, -22), P('head', 0, 0, 0, -24),
                      P('arm_r', 0, 0, 0, -170, 0, -14), P('arm_l', 0, 0, 0, -152, 0, 20),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.55, 1.55, 1.55),
                      P('spear', 0, 0, 0, -90, 0, 0, 1.75, 1.75, 1.75), P('cape', 0, 0, 0, 62))),
        (0.80, merged(P('root', 0, -2, 1), P('body', 0, 0, 0, 26, 36), P('head', 0, 0, 0, 6),
                      P('arm_r', 0, 0, 0, -76, 32, 12), P('arm_l', 0, 0, 0, 26, -18, 34),
                      P('spear', 0, 0, -2, 0, 0, 16, 1.5, 1.5, 1.5), P('cape', 0, 0, 0, 48))),
        (1.0, merged()),
    ], 19))

    return clips


# ---------------------------------------------------------------- 幻影贴图变换

def make_clone_canvas(src):
    """基于本体贴图生成半透明苍青紫雷影贴图：提亮雷纹与棱角、统一冷青紫光感。"""
    out = Canvas(src.w, src.h)
    for y in range(src.h):
        for x in range(src.w):
            r, g, b, a = src.px[y][x]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            # 低亮度映射深蓝紫，高亮度映射苍青白电光
            cr = clamp_byte(52 + 138 * lum)
            cg = clamp_byte(118 + 132 * lum)
            cb = clamp_byte(196 + 59 * lum)
            if lum > 0.72:
                t = (lum - 0.72) / 0.28
                cr = clamp_byte(cr + (235 - cr) * t)
                cg = clamp_byte(cg + (248 - cg) * t)
                cb = 255
            alpha = clamp_byte(155 + 75 * lum)
            out.set(x, y, (cr, cg, cb, alpha))
    return out


# ---------------------------------------------------------------- 组装、校验与预览

def rects_overlap(a, b):
    return a[0] < b[0] + b[2] and b[0] < a[0] + a[2] and a[1] < b[1] + b[3] and b[1] < a[1] + a[3]


def build_model_json(bones, clips):
    return {
        'id': MODEL_ID,
        'name': MODEL_NAME,
        'role': MODEL_ROLE,
        'coordinates': COORDINATES,
        'texture': 'textures/entity/dragon_tower/%s.png' % MODEL_ID,
        'texture_size': TEXTURE_SIZE,
        'bones': [{
            'name': b['name'],
            'parent': b['parent'],
            'pivot': b['pivot'],
            'rotation': b['rotation'],
            'effect': b['effect'],
            'cubes': [{
                'name': c['name'],
                'material': c['material'],
                'box': c['box'],
                'rotation': c['rotation'],
                'uv': c['uv'],
            } for c in b['cubes']],
        } for b in bones],
        'clips': clips,
    }


def validate_model(bones, data, cv):
    if (cv.w, cv.h) != (TEXTURE_SIZE, TEXTURE_SIZE):
        fail('画布尺寸与 texture_size 不一致')
    height = model_height(bones)
    if not (HEIGHT_MIN <= height <= HEIGHT_MAX):
        fail('模型总高 %.1f 超出容差 %.0f~%.0f' % (height, HEIGHT_MIN, HEIGHT_MAX))
    bones_j = data['bones']
    if bones_j[0]['name'] != 'root' or bones_j[0]['parent'] != '' or bones_j[0]['pivot'] != [0, 0, 0]:
        fail('bones[0] 必须为 root/空父级/pivot[0,0,0]')
    seen = set()
    regions = []
    for i, b in enumerate(bones_j):
        if b['name'] in seen:
            fail('骨骼重名 %s' % b['name'])
        if i == 0 and b['parent'] != '':
            fail('仅首骨骼可挂 ground')
        if i > 0 and b['parent'] not in seen:
            fail('骨骼 %s 父级 %r 非法' % (b['name'], b['parent']))
        if b['effect'] is not False:
            fail('effect 必须为 false')
        seen.add(b['name'])
        for c in b['cubes']:
            box = c['box']
            if len(box) != 6 or box[3] <= 0 or box[4] <= 0 or box[5] <= 0:
                fail('方体 %s 尺寸非法' % c['name'])
            u, v = c['uv']
            w, h, d = int(math.ceil(box[3])), int(math.ceil(box[4])), int(math.ceil(box[5]))
            if u < 0 or v < 0 or u + 2 * (w + d) > TEXTURE_SIZE + EPS or v + (h + d) > TEXTURE_SIZE + EPS:
                fail('方体 %s UV 越界' % c['name'])
            regions.append((c, (u, v, 2 * (w + d), h + d)))
    for i in range(len(regions)):
        for j in range(i + 1, len(regions)):
            if rects_overlap(regions[i][1], regions[j][1]):
                fail('UV 重叠：%s 与 %s' % (regions[i][0]['name'], regions[j][0]['name']))
    for req in ('idle', 'walk', 'hurt', 'death', 'spear_combo', 'dragon_charge', 'thunder_discharge',
                'thunder_cage', 'sky_spear_rain', 'jump_slam', 'phantom_awakening', 'crown_tempest'):
        if req not in data['clips']:
            fail('缺少 clip：%s' % req)
    for cname, c in data['clips'].items():
        if c['seconds'] <= 0 or not c['frames']:
            fail('clip %s 非法' % cname)
        for frame in c['frames']:
            if len(frame) != len(bones_j):
                fail('clip %s 帧骨骼数不匹配' % cname)
            for row in frame:
                if len(row) != 9 or row[6] <= 0 or row[7] <= 0 or row[8] <= 0:
                    fail('clip %s 帧行非法' % cname)


def build_preview(main_cv, clone_cv):
    scale = 3
    cw, ch = main_cv.w * scale, main_cv.h * scale
    margin = 12
    sheet = Canvas(cw * 2 + margin * 3, ch + margin * 2)
    rect(sheet, 0, 0, sheet.w, sheet.h, (28, 30, 40, 255))
    for idx, cv in enumerate((main_cv, clone_cv)):
        ox = margin + idx * (cw + margin)
        oy = margin
        for qy in range(0, ch, 8):
            for qx in range(0, cw, 8):
                tone = (54, 56, 68, 255) if ((qx // 8 + qy // 8) % 2 == 0) else (40, 42, 54, 255)
                rect(sheet, ox + qx, oy + qy, 8, 8, tone)
        for sy in range(cv.h):
            for sx in range(cv.w):
                r, g, b, a = cv.px[sy][sx]
                if a == 0:
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        bg = sheet.px[oy + sy * scale + dy][ox + sx * scale + dx]
                        f = a / 255.0
                        sheet.set(ox + sx * scale + dx, oy + sy * scale + dy, (
                            clamp_byte(bg[0] * (1 - f) + r * f),
                            clamp_byte(bg[1] * (1 - f) + g * f),
                            clamp_byte(bg[2] * (1 - f) + b * f),
                            255,
                        ))
    path = os.path.join(PREVIEW_DIR, 'preview.png')
    write_png(path, sheet.w, sheet.h, sheet.rows())
    return path


def rel(path):
    return os.path.relpath(path, ROOT).replace(os.sep, '/')


def main():
    for stream in (sys.stdout, sys.stderr):
        try:
            stream.reconfigure(encoding='utf-8', errors='replace')
        except Exception:
            pass
    os.makedirs(DUNGEON_DIR, exist_ok=True)
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    try:
        bones = build_bones()
        if [b['name'] for b in bones] != BONE_ORDER:
            fail('骨骼序表不一致')
        pack_uv(bones)
        clips = build_clips()
        data = build_model_json(bones, clips)

        cv = Canvas(TEXTURE_SIZE, TEXTURE_SIZE)
        for b in bones:
            for c in b['cubes']:
                faces = box_faces(c['uv'][0], c['uv'][1], c['box'][3], c['box'][4], c['box'][5])
                c['paint'](cv, faces, c)
        validate_model(bones, data, cv)
        clone_cv = make_clone_canvas(cv)

        json_path = os.path.join(DUNGEON_DIR, MODEL_ID + '.json')
        png_path = os.path.join(TEXTURE_DIR, MODEL_ID + '.png')
        clone_png_path = os.path.join(TEXTURE_DIR, MODEL_ID + '_clone.png')

        text = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
        json.loads(text)
        with open(json_path, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(text)
        write_png(png_path, TEXTURE_SIZE, TEXTURE_SIZE, cv.rows())
        write_png(clone_png_path, TEXTURE_SIZE, TEXTURE_SIZE, clone_cv.rows())

        for p in (png_path, clone_png_path):
            with open(p, 'rb') as fh:
                blob = fh.read()
            if blob[:8] != b'\x89PNG\r\n\x1a\n' or struct.unpack('>II', blob[16:24]) != (TEXTURE_SIZE, TEXTURE_SIZE):
                fail('PNG 校验失败：%s' % p)

        preview_path = build_preview(cv, clone_cv)
        cubes_count = sum(len(b['cubes']) for b in bones)
        print('[OK] %s（%s）' % (MODEL_NAME, MODEL_ROLE))
        print('     骨骼 %d / 方体 %d / clips %d：%s' % (len(bones), cubes_count, len(clips), ', '.join(sorted(clips))))
        print('     模型总高 %.1f 单位（≈%.2f 格）' % (model_height(bones), model_height(bones) / 16.0))
        print('     %s' % rel(json_path))
        print('     %s' % rel(png_path))
        print('     %s' % rel(clone_png_path))
        print('     预览：%s' % rel(preview_path))
    except AssetError as error:
        print('[FAIL] %s' % error)
        sys.exit(1)


if __name__ == '__main__':
    main()

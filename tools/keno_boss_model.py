#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""落雷凯诺 · 全新精细骨骼模型与贴图制作源（正式源，提交用）。

哥布林营地 Boss 落雷凯诺（goblin_shaman）重置第一步：产出
GblModel JSON 骨骼模型（dungeons/goblin_camp/keno.json）与程序绘制贴图
（textures/entity/goblin_camp/keno.png），供 GblEnemyRenderer 消费。

- 模型格式对齐 GblModel.java：
  Data{texture_size, bones[{name,parent,pivot,rotation,effect,cubes[...]}], clips}；
  cube 为 {name,material,box:[minx,miny,minz,w,h,d],rotation,uv:[u,v]}。
  坐标 Y 向上、+Z 正面、一格 16 单位；pivot 相对父级 pivot，box 相对骨骼 pivot；
  cube.uv 是原版 addBox 箱式展开区域左上角；GblModel 将 root 挂到 ground，
  实体原点在脚底。clip 帧 = 每骨骼 9 值 [tx,ty,tz,rx,ry,rz,sx,sy,sz]（相对静姿增量）。
- bones[0] 固定 name="root"、parent=""、pivot=[0,0,0]；clips 必含 "idle"
  （缺失 GblModel 会抛错）。保留名：walk（移动）、hurt（受击）、death（死亡）。
- 体型目标约 1.5 倍玩家：站立冠角约 45 单位（≈2.8 格）。
- 贴图 128x128 透明 PNG（纯 stdlib zlib+struct 写出，写法参考
  tools/altar_boss_mechanisms.py），程序色块绘制，光照统一左上亮右下暗。
  家族风+雷元素：哥布林绿皮、骨饰、皮布衣；金冠、雷纹刺青、风暴宝石发光。
- 自带校验：UV 区域不越界、方体间区域不重叠（共用同一贴图块除外）、
  骨骼链、帧行数==骨骼数、9 值、缩放为正、idle 存在、模型总高在容差内。
- 运行后输出落位路径清单，并出 3 倍贴图预览到 temp/keno-model/preview.png。

用法：python tools/keno_boss_model.py
仅写入 dungeons/goblin_camp/keno.json、textures/entity/goblin_camp/keno.png 与 temp 预览。
"""

import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'yumis_dungeon_and_fight')
DUNGEON_DIR = os.path.join(ASSET, 'dungeons', 'goblin_camp')
TEXTURE_DIR = os.path.join(ASSET, 'textures', 'entity', 'goblin_camp')
PREVIEW_DIR = os.path.join(ROOT, 'temp', 'keno-model')

MODEL_ID = 'keno'
MODEL_NAME = '落雷凯诺'
MODEL_ROLE = '哥布林营地首领 · 雷萨满'
COORDINATES = 'Y up, +Z front, 16 units/block; local XYZ degrees'
TEXTURE_SIZE = 128
EPS = 1e-6

# 模型总高（含冠角）容差：目标约 2.8 格（45 单位附近）。
HEIGHT_MIN, HEIGHT_MAX = 42.0, 50.0


class AssetError(Exception):
    """资产规格或网格校验失败。"""


def fail(message):
    raise AssetError(message)


# ---------------------------------------------------------------- 画布与颜色

class Canvas:
    """RGBA 像素画布，越界写入自动忽略。"""

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
            for (r, g, b, a) in row:
                buf.append(r); buf.append(g); buf.append(b); buf.append(a)
            out.append(bytes(buf))
        return out


def clamp8(v):
    return 0 if v < 0 else (255 if v > 255 else int(v))


def rgba(base, mult=1.0, alpha=255):
    return (clamp8(base[0] * mult), clamp8(base[1] * mult), clamp8(base[2] * mult), alpha)


def jitter(x, y, seed):
    """确定性伪随机抖动（同一输入永远同值，保证贴图可复现）。"""
    h = (x * 73856093) ^ (y * 19349663) ^ (seed * 83492791)
    h &= 0xFFFFFFFF
    return (h % 2009) / 2009.0 - 0.5


def irect(x, y, w, h):
    """浮点面矩形 -> 覆盖它的整数像素矩形（左上角与宽高）。"""
    x0 = math.floor(x + EPS)
    y0 = math.floor(y + EPS)
    x1 = math.ceil(x + w - EPS)
    y1 = math.ceil(y + h - EPS)
    return x0, y0, x1 - x0, y1 - y0


def rect(cv, x, y, w, h, color):
    """纯色覆盖（无光照，用于预览底/边框）。"""
    for iy in range(int(y), int(y + h)):
        for ix in range(int(x), int(x + w)):
            cv.set(ix, iy, color)


def fill(cv, x, y, w, h, base, seed=0, jit=0.0, bias=0.0):
    """程序色块填充：统一左上亮右下暗光照，可叠加整体偏置与确定性抖动。"""
    if w <= 0 or h <= 0:
        return
    x0, y0, pw, ph = irect(x, y, w, h)
    for iy in range(y0, y0 + ph):
        fy = (iy + 0.5 - y) / h
        fy = -0.05 if fy < -0.05 else (1.05 if fy > 1.05 else fy)
        for ix in range(x0, x0 + pw):
            fx = (ix + 0.5 - x) / w
            fx = -0.05 if fx < -0.05 else (1.05 if fx > 1.05 else fx)
            mult = 1.0 + 0.30 * (0.5 - fx) + 0.16 * (0.5 - fy) + bias
            if jit:
                mult += jitter(ix, iy, seed) * jit
            cv.set(ix, iy, rgba(base, mult))


def dot(cv, x, y, color):
    cv.set(int(x), int(y), color)


# ---------------------------------------------------------------- PNG 写出（纯 stdlib）

def png_chunk(key, data):
    return struct.pack('>I', len(data)) + key + data + struct.pack('>I', zlib.crc32(key + data) & 0xFFFFFFFF)


def write_png(path, width, height, rows):
    header = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    payload = b''.join(b'\x00' + row for row in rows)
    blob = (b'\x89PNG\r\n\x1a\n' + png_chunk(b'IHDR', header)
            + png_chunk(b'IDAT', zlib.compress(payload, 9)) + png_chunk(b'IEND', b''))
    with open(path, 'wb') as fh:
        fh.write(blob)
    return blob


# ---------------------------------------------------------------- 箱式展开

def box_faces(u, v, w, h, d):
    """原版 addBox 箱式展开：uv 为区域左上角，返回各面 (x,y,w,h)。"""
    return {
        'up':    (u + d, v, w, d),
        'down':  (u + d + w, v, w, d),
        'east':  (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'west':  (u + d + w, v + d, d, h),
        'back':  (u + 2 * d + w, v + d, w, h),
    }


def region_of(cube):
    w, h, d = cube['box'][3], cube['box'][4], cube['box'][5]
    u, v = cube['uv']
    return (u, v, 2.0 * (w + d), h + d)


def fill_sides(cv, faces, base, **kw):
    for key in ('east', 'front', 'west', 'back'):
        fill(cv, *faces[key], base, **kw)


def side_list(faces):
    return [faces[k] for k in ('east', 'front', 'west', 'back')]


# ---------------------------------------------------------------- 调色板（家族风+雷元素）

SKIN = (94, 132, 62); SKIN_DK = (72, 104, 48); SKIN_LT = (118, 158, 80)
CLOTH = (112, 80, 46); CLOTH_DK = (86, 60, 34); CLOTH_LT = (132, 98, 58)
LEATHER = (76, 52, 30); LEATHER_DK = (56, 38, 21)
IVORY = (236, 230, 212); IVORY_DK = (196, 188, 166)
GOLD = (222, 172, 74); GOLD_DK = (166, 122, 44); GOLD_HI = (244, 208, 120)
WOOD = (94, 64, 36); WOOD_DK = (70, 46, 26)
GEM = (110, 190, 255); GEM_DK = (66, 138, 214); GEM_HI = (206, 240, 255)
EYE = (185, 230, 255)
TATTOO = (255, 222, 130)


# ---------------------------------------------------------------- 面片绘制

def paint_skin(cv, f, c):
    fill_sides(cv, f, SKIN, seed=1, jit=0.06)
    fill(cv, *f['up'], SKIN_LT, seed=2, jit=0.05)
    fill(cv, *f['down'], SKIN_DK, bias=-0.05)


def paint_skin_dark(cv, f, c):
    fill_sides(cv, f, SKIN_DK, seed=3, jit=0.06)
    fill(cv, *f['up'], SKIN, bias=0.04)
    fill(cv, *f['down'], SKIN_DK, bias=-0.10)


def paint_cloth(cv, f, c):
    fill_sides(cv, f, CLOTH, seed=4, jit=0.08)
    for x, y, w, h in side_list(f):
        if h >= 6:
            fill(cv, x, y + h - 2, w, 2, CLOTH_DK, bias=-0.04)     # 下摆磨损沉色
        for ix in range(int(x) + 1, int(x + w), 4):               # 缝线
            if h >= 4:
                dot(cv, ix, y + 1, rgba(CLOTH_DK, 0.9))
    fill(cv, *f['up'], CLOTH_LT, seed=5, jit=0.06)
    fill(cv, *f['down'], CLOTH_DK, bias=-0.08)


def paint_tunic_front(cv, f, c):
    """躯干正面：布衣 + 两道雷纹刺青（金色锯齿）。"""
    paint_cloth(cv, f, c)
    x, y, w, h = f['front']
    if w >= 8 and h >= 8:
        for col in range(2):
            cx = int(x) + 2 + col * (max(int(w) - 5, 3))
            for iy in range(int(y) + 1, int(y + h) - 1):
                step = (iy - int(y)) % 4
                dx = 0 if step < 2 else 1
                dot(cv, cx + dx, iy, rgba(TATTOO, 0.95 if step % 2 == 0 else 0.8))
                dot(cv, cx + dx + 1, iy + 1, rgba(TATTOO, 0.6))


def paint_leather(cv, f, c):
    fill_sides(cv, f, LEATHER, seed=6, jit=0.07)
    for x, y, w, h in side_list(f):
        if w >= 4:
            dot(cv, x + w // 2, y + h // 2, rgba(GOLD, 0.9))      # 铆钉
    fill(cv, *f['up'], (92, 64, 38), seed=7, jit=0.06)
    fill(cv, *f['down'], LEATHER_DK, bias=-0.08)


def paint_ivory(cv, f, c):
    fill_sides(cv, f, IVORY, seed=8, jit=0.05)
    for x, y, w, h in side_list(f):
        if h >= 4:
            fill(cv, x, y, w, 1, IVORY, bias=0.10)                # 上棱受光
            fill(cv, x, y + h - 1, w, 1, IVORY_DK, bias=-0.06)   # 下端沉色
    fill(cv, *f['up'], (248, 244, 232))
    fill(cv, *f['down'], IVORY_DK, bias=-0.10)


def paint_gold(cv, f, c):
    fill_sides(cv, f, GOLD, seed=9, jit=0.04)
    for x, y, w, h in side_list(f):
        fill(cv, x, y, w, 1, GOLD, bias=0.14)                    # 金属亮棱
        if h >= 3:
            fill(cv, x, y + 1, w, 1, GOLD_DK, bias=-0.05)        # 錾刻暗线
    fill(cv, *f['up'], GOLD_HI, seed=10, jit=0.03)
    fill(cv, *f['down'], GOLD_DK, bias=-0.10)


def paint_wood(cv, f, c):
    fill_sides(cv, f, WOOD, seed=11, jit=0.07)
    for x, y, w, h in side_list(f):
        for ix in range(int(x), int(x + w)):
            if (ix - int(x)) % 3 == 0 and h >= 3:                 # 竖向木纹
                fill(cv, ix, y, 1, h, WOOD, bias=-0.16)
    fill(cv, *f['up'], (114, 80, 48), seed=12, jit=0.05)
    fill(cv, *f['down'], WOOD_DK, bias=-0.10)


def paint_gem(cv, f, c):
    """风暴宝石：整体高亮，顶面亮芯。"""
    fill_sides(cv, f, GEM, seed=13, jit=0.03, bias=0.18)
    for x, y, w, h in side_list(f):
        fill(cv, x, y, w, 1, GEM_HI)                             # 亮棱
    fill(cv, *f['up'], GEM_HI)
    x, y, w, h = f['up']
    if w >= 2 and h >= 2:
        dot(cv, x + w // 2, y + h // 2, rgba((240, 250, 255)))
    fill(cv, *f['down'], GEM_DK, bias=0.02)


def paint_eye(cv, f, c):
    fill_sides(cv, f, EYE, bias=0.22)
    fill(cv, *f['up'], (230, 248, 255))
    fill(cv, *f['down'], GEM, bias=0.1)


PAINTERS = {
    'skin': paint_skin, 'skin_dark': paint_skin_dark, 'cloth': paint_cloth,
    'tunic_front': paint_tunic_front, 'leather': paint_leather, 'ivory': paint_ivory,
    'gold': paint_gold, 'wood': paint_wood, 'gem': paint_gem, 'eye': paint_eye,
}


def cube(name, material, box, rotation=(0, 0, 0)):
    return {'name': name, 'material': material, 'box': list(box),
            'rotation': list(rotation), 'uv': None, 'paint': PAINTERS[material]}


def bone(name, parent, pivot, cubes, rotation=(0, 0, 0)):
    return {'name': name, 'parent': parent, 'pivot': list(pivot),
            'rotation': list(rotation), 'effect': False, 'cubes': cubes}


# ---------------------------------------------------------------- 骨骼定义（Y 向上，pivot 相对父级）

def build_bones():
    return [
        bone('root', '', [0, 0, 0], []),
        bone('hips', 'root', [0, 20, 0], [
            cube('pelvis', 'cloth', [-4.5, -3, -3, 9, 6, 6]),
            cube('belt', 'leather', [-4.8, 1.5, -3.3, 9.6, 2, 6.6]),
        ]),
        bone('body', 'hips', [0, 3, 0], [
            cube('torso', 'tunic_front', [-5, -1, -3.5, 10, 11, 7]),
            cube('shoulder_r', 'leather', [3.5, 8.5, -3.8, 3.5, 3, 7.6]),
            cube('shoulder_l', 'leather', [-7, 8.5, -3.8, 3.5, 3, 7.6]),
            cube('collar', 'gold', [-2.5, 9.8, 3.2, 5, 1.5, 1.2]),
        ]),
        bone('cape', 'body', [0, 9.5, -3.5], [
            cube('cloak', 'cloth', [-5.5, -14, -2, 11, 15, 3], rotation=(8, 0, 0)),
            cube('hem_charm', 'ivory', [-2, -15.2, -2, 4, 1.6, 3.2]),
        ]),
        bone('beads', 'body', [0, 8.5, 3.4], [
            cube('strand', 'ivory', [-0.75, -6, -0.75, 1.5, 6, 1.5]),
            cube('bead_knot', 'gold', [-1, -7.2, -1, 2, 1.6, 2]),
        ]),
        bone('head', 'body', [0, 10, 0], [
            cube('skull', 'skin', [-3.5, 0, -3.5, 7, 6.5, 7]),
            cube('snout', 'skin', [-2, 0.5, 3.4, 4, 3, 2.5]),
            cube('jaw', 'skin_dark', [-2, -1.5, 3.4, 4, 1.8, 2.5]),
            cube('brow', 'skin_dark', [-3.6, 4.8, 3.2, 7.2, 1.4, 1.6]),
            cube('tusk_l', 'ivory', [-1.6, -1.4, 5.2, 1, 2.6, 1]),
            cube('tusk_r', 'ivory', [0.6, -1.4, 5.2, 1, 2.6, 1]),
            cube('eye_l', 'eye', [-2.6, 3, 3.55, 1.5, 1.2, 0.4]),
            cube('eye_r', 'eye', [1.1, 3, 3.55, 1.5, 1.2, 0.4]),
        ]),
        bone('crown', 'head', [0, 6.5, 0], [
            cube('band', 'gold', [-4, 0, -4, 8, 1.5, 8]),
            cube('horn_l', 'gold', [-4.6, 1.2, -0.8, 1.6, 4.5, 1.6], rotation=(0, 0, 25)),
            cube('horn_r', 'gold', [3.0, 1.2, -0.8, 1.6, 4.5, 1.6], rotation=(0, 0, -25)),
            cube('crown_gem', 'gem', [-1, 1.5, 3.9, 2, 2, 1.2]),
        ]),
        bone('ear_l', 'head', [-3.4, 4.5, -0.5], [
            cube('ear', 'skin_dark', [-1.2, 0, -1, 2.4, 9, 2]),
        ], rotation=(0, 0, -35)),
        bone('ear_r', 'head', [3.4, 4.5, -0.5], [
            cube('ear', 'skin_dark', [-1.2, 0, -1, 2.4, 9, 2]),
        ], rotation=(0, 0, 35)),
        bone('arm_r', 'body', [6, 9, 0], [
            cube('upper_arm', 'skin', [-2, -11.5, -2, 4, 11.5, 4]),
            cube('bracer', 'leather', [-2.4, -9, -2.4, 4.8, 4, 4.8]),
        ]),
        bone('staff', 'arm_r', [0, -10.5, 0], [
            cube('shaft', 'wood', [-1, -6, -1, 2, 26, 2]),
            cube('ring_low', 'gold', [-1.8, 2, -1.8, 3.6, 1.5, 3.6]),
            cube('ring_high', 'gold', [-1.8, 6, -1.8, 3.6, 1.5, 3.6]),
            cube('prong_l', 'ivory', [-2.4, 19, -2.4, 1.5, 3.5, 1.5]),
            cube('prong_r', 'ivory', [0.9, 19, -2.4, 1.5, 3.5, 1.5]),
            cube('storm_gem', 'gem', [-2, 21, -2, 4, 4.5, 4]),
        ]),
        bone('arm_l', 'body', [-6, 9, 0], [
            cube('upper_arm', 'skin', [-2, -11.5, -2, 4, 11.5, 4]),
            cube('bracer', 'leather', [-2.4, -9, -2.4, 4.8, 4, 4.8]),
            cube('bracelet', 'ivory', [-2.7, -5.2, -2.7, 5.4, 1.4, 5.4]),
        ]),
        bone('leg_r', 'hips', [2.2, 0, 0], [
            cube('thigh', 'skin', [-2, -20, -2.5, 4, 20, 5]),
            cube('foot', 'leather', [-2.1, -20, -4.6, 4.2, 4, 7]),
        ]),
        bone('leg_l', 'hips', [-2.2, 0, 0], [
            cube('thigh', 'skin', [-2, -20, -2.5, 4, 20, 5]),
            cube('foot', 'leather', [-2.1, -20, -4.6, 4.2, 4, 7]),
        ]),
    ]


BONE_ORDER = [b['name'] for b in build_bones()]
BONE_INDEX = {name: i for i, name in enumerate(BONE_ORDER)}


def world_pivot(bones):
    """计算每块骨骼的世界 pivot 高度（校验模型总高用）。"""
    world = {'root': [0, 0, 0]}
    for b in bones:
        if b['name'] == 'root':
            continue
        px, py, pz = world[b['parent']]
        world[b['name']] = [px + b['pivot'][0], py + b['pivot'][1], pz + b['pivot'][2]]
    return world


def model_height(bones):
    world = world_pivot(bones)
    top = 0.0
    for b in bones:
        if b['name'] == 'root':
            continue
        by = world[b['name']][1]
        for c in b['cubes']:
            top = max(top, by + c['box'][1] + c['box'][4])
    return top


# ---------------------------------------------------------------- UV 装箱

def pack_uv(bones):
    """按行高装箱：返回每块方体的 uv，保证区域不越界、不重叠。"""
    flat = []
    for b in bones:
        for c in b['cubes']:
            flat.append((b['name'], c))
    flat.sort(key=lambda item: (-(item[1]['box'][4] + item[1]['box'][5]), item[0]))
    x = y = row_h = 0
    limit = TEXTURE_SIZE
    for _, c in flat:
        w, h, d = c['box'][3], c['box'][4], c['box'][5]
        rw = int(math.ceil(2.0 * (w + d) - EPS))
        rh = int(math.ceil(h + d - EPS))
        if rw <= 0:
            rw = 1
        if rh <= 0:
            rh = 1
        if rw > limit:
            fail('%s: UV 区域宽 %d 超过贴图宽 %d' % (c['name'], rw, limit))
        if x + rw > limit:
            x = 0
            y += row_h
            row_h = 0
        if y + rh > limit:
            fail('%s: UV 装箱超出贴图高度（v=%d 需高 %d > %d）' % (c['name'], y, rh, limit))
        c['uv'] = [x, y]
        x += rw
        row_h = max(row_h, rh)
    return flat


# ---------------------------------------------------------------- 姿态与 clip 采样

def zero_pose():
    return {name: [0, 0, 0, 0, 0, 0, 1, 1, 1] for name in BONE_ORDER}


def pose(base=None, **kw):
    """以 base 为底（默认全零静姿增量），按骨骼名覆盖 9 值。"""
    rows = zero_pose() if base is None else {k: list(v) for k, v in base.items()}
    for name, vals in kw.items():
        if name not in rows:
            fail('未知骨骼 %s' % name)
        rows[name] = list(vals)
    return rows


def _sub(a, b):
    return [b[i] - a[i] for i in range(9)]


def lerp_pose(a, b, f):
    out = {}
    for name in BONE_ORDER:
        pa, pb = a[name], b[name]
        out[name] = [pa[i] + (pb[i] - pa[i]) * f for i in range(9)]
    return out


def sample(keyframes, frames):
    """keyframes = [(t0~1, pose_dict), ...] 按 t 升序；均匀采样 frames 帧（含首尾）。"""
    if not keyframes:
        fail('关键帧为空')
    if keyframes[0][0] != 0.0:
        fail('首关键帧 t 必须为 0')
    if keyframes[-1][0] != 1.0:
        fail('末关键帧 t 必须为 1')
    out = []
    for i in range(frames):
        t = i / (frames - 1) if frames > 1 else 0.0
        while len(keyframes) > 1 and t > keyframes[1][0]:
            keyframes = keyframes[1:]
        (t0, p0), (t1, p1) = keyframes[0], keyframes[1]
        span = t1 - t0
        f = 0.0 if span <= 0 else min(1.0, max(0.0, (t - t0) / span))
        out.append(lerp_pose(p0, p1, f))
    return out


def rows_of(poses):
    """pose 列表 -> GblModel frames：[frame][boneIndex][9]。"""
    return [[poses[i][name] for name in BONE_ORDER] for i in range(len(poses))]


def clip(seconds, poses):
    return {'seconds': seconds, 'frames': rows_of(poses)}


def P(name, *vals):
    """便捷：单骨骼 9 值；缺省位补 0，缩放缺省补 1。"""
    row = [0, 0, 0, 0, 0, 0, 1, 1, 1]
    for i, v in enumerate(vals):
        row[i] = v
    return (name, row)


def merged(*items):
    """合并若干 P(...) 为一个 pose dict。"""
    rows = zero_pose()
    for name, row in items:
        rows[name] = list(row)
    return rows


# ---------------------------------------------------------------- clips

def build_clips():
    clips = {}

    def flat(name, items):
        merged(*items)

    # idle：呼吸、披风与骨饰轻摆、耳廓偶动、宝石微光。
    idle_kfs = [
        (0.0, merged()),
        (0.25, merged(P('body', 0, 0, 0, 0, 2, 0), P('head', 0, 0, 0, 0, -4, 0),
                      P('cape', 0, 0, 0, 3, 1, 0), P('beads', 0, 0, 0, 0, 4, 0),
                      P('arm_r', 0, 0, 0, -3, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.08, 1.08, 1.08))),
        (0.5, merged(P('body', 0, 0, 0, 1, 0, 0), P('ear_l', 0, 0, 0, 0, 0, -4),
                     P('ear_r', 0, 0, 0, 0, 0, 4), P('staff', 0, 0, 0, 0, 0, 0, 1.15, 1.15, 1.15))),
        (0.75, merged(P('body', 0, 0, 0, 0, -2, 0), P('head', 0, 0, 0, 0, 4, 0),
                      P('cape', 0, 0, 0, 3, -1, 0), P('beads', 0, 0, 0, 0, -4, 0),
                      P('arm_r', 0, 0, 0, 3, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.08, 1.08, 1.08))),
        (1.0, merged()),
    ]
    clips['idle'] = clip(4.8, sample(idle_kfs, 17))

    # walk：交替迈步、手臂反摆、身体起伏、披风后扬。
    swing = 26
    walk_kfs = [
        (0.0, merged(P('leg_r', 0, 0, 0, -swing), P('leg_l', 0, 0, 0, swing),
                     P('arm_r', 0, 0, 0, swing * .7), P('arm_l', 0, 0, 0, -swing * .7),
                     P('body', 0, -0.7, 0, 3), P('cape', 0, 0, 0, 10, 2, 0))),
        (0.25, merged(P('leg_r', 0, 0, 0, 0), P('leg_l', 0, 0, 0, 0),
                      P('arm_r', 0, 0, 0, 0), P('arm_l', 0, 0, 0, 0),
                      P('body', 0, 0, 0, 3), P('cape', 0, 0, 0, 8, 0, 0))),
        (0.5, merged(P('leg_r', 0, 0, 0, swing), P('leg_l', 0, 0, 0, -swing),
                     P('arm_r', 0, 0, 0, -swing * .7), P('arm_l', 0, 0, 0, swing * .7),
                     P('body', 0, -0.7, 0, 3), P('cape', 0, 0, 0, 10, -2, 0))),
        (0.75, merged(P('leg_r', 0, 0, 0, 0), P('leg_l', 0, 0, 0, 0),
                      P('arm_r', 0, 0, 0, 0), P('arm_l', 0, 0, 0, 0),
                      P('body', 0, 0, 0, 3), P('cape', 0, 0, 0, 8, 0, 0))),
        (1.0, merged(P('leg_r', 0, 0, 0, -swing), P('leg_l', 0, 0, 0, swing),
                     P('arm_r', 0, 0, 0, swing * .7), P('arm_l', 0, 0, 0, -swing * .7),
                     P('body', 0, -0.7, 0, 3), P('cape', 0, 0, 0, 10, 2, 0))),
    ]
    clips['walk'] = clip(0.96, sample(walk_kfs, 13))

    # hurt：受击后仰、双臂张开缓冲、迅速回位。
    hurt_kfs = [
        (0.0, merged()),
        (0.2, merged(P('body', 0, 0, 0, -8), P('head', 0, 0, 0, -14),
                     P('arm_r', 0, 0, 0, -35, 0, -20), P('arm_l', 0, 0, 0, -35, 0, 20),
                     P('cape', 0, 0, 0, -8), P('beads', 0, 0, 0, 0, -8, 0))),
        (0.55, merged(P('body', 0, 0, 0, -4), P('head', 0, 0, 0, -6),
                      P('arm_r', 0, 0, 0, -18, 0, -10), P('arm_l', 0, 0, 0, -18, 0, 10),
                      P('cape', 0, 0, 0, -3))),
        (1.0, merged()),
    ]
    clips['hurt'] = clip(0.45, sample(hurt_kfs, 9))

    # death：踉跄下沉、前倾倒地、披风盖身。
    death_kfs = [
        (0.0, merged()),
        (0.2, merged(P('body', 0, 0, 0, -10), P('head', 0, 0, 0, -12),
                     P('arm_r', 0, 0, 0, -40), P('arm_l', 0, 0, 0, -40),
                     P('cape', 0, 0, 0, -12))),
        (0.55, merged(P('root', 0, -8, 0), P('body', 0, 0, 0, 35), P('head', 0, 0, 0, 25),
                      P('arm_r', 0, 0, 0, -55, 0, -30), P('arm_l', 0, 0, 0, -55, 0, 30),
                      P('leg_r', 0, 0, 0, -20), P('leg_l', 0, 0, 0, -25),
                      P('cape', 0, 0, 0, 20), P('staff', 0, 0, 0, 0, 0, 0, 0.9, 0.9, 0.9))),
        (1.0, merged(P('root', 0, -14, 0), P('body', 0, 0, 0, 78), P('head', 0, 0, 0, 32),
                      P('arm_r', 0, 0, 0, -70, 0, -45), P('arm_l', 0, 0, 0, -70, 0, 45),
                      P('leg_r', 0, 0, 0, -28), P('leg_l', 0, 0, 0, -32),
                      P('cape', 0, 0, 0, 26), P('staff', 0, 0, 0, 0, 0, 0, 0.85, 0.85, 0.85),
                      P('crown', 0, 0, 0, 40))),
    ]
    clips['death'] = clip(1.6, sample(death_kfs, 13))

    # lightning_cast（P1 精修 · 落雷）：举杖引雷、宝石充能脉冲、落定。
    lc_kfs = [
        (0.0, merged()),
        (0.3, merged(P('arm_r', 0, 0, 0, -150, 0, -8), P('arm_l', 0, 0, 0, -55, 0, 18),
                     P('head', 0, 0, 0, -18), P('body', 0, 0, 0, 0, -6, 0),
                     P('staff', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3), P('cape', 0, 0, 0, -8, 0, 0))),
        (0.62, merged(P('arm_r', 0, 0, 0, -168, 0, -6), P('arm_l', 0, 0, 0, -80, 0, 22),
                      P('head', 0, 0, 0, -24), P('body', 0, 0, 0, -4, -8, 0),
                      P('staff', 0, 0, 0, 0, 0, 0, 1.55, 1.55, 1.55), P('cape', 0, 0, 0, -14, 0, 0),
                      P('beads', 0, 0, 0, 0, -10, 0))),
        (0.78, merged(P('arm_r', 0, 0, 0, -120, 0, -4), P('arm_l', 0, 0, 0, -60, 0, 15),
                      P('head', 0, 0, 0, -10), P('body', 0, 0, 0, 8, -4, 0),
                      P('staff', 0, 0, 0, 0, 0, 0, 1.2, 1.2, 1.2), P('cape', 0, 0, 0, -4, 0, 0))),
        (1.0, merged()),
    ]
    clips['lightning_cast'] = clip(1.5, sample(lc_kfs, 15))

    # fan_swipe（P1 精修 · 扇击）：横杖大幅扫击、躯干拧转。
    fs_kfs = [
        (0.0, merged()),
        (0.28, merged(P('arm_r', 0, 0, 0, -85, -55, -25), P('body', 0, 0, 0, 0, -22, 0),
                      P('head', 0, 0, 0, 0, -14, 0), P('arm_l', 0, 0, 0, -30, 0, 25),
                      P('cape', 0, 0, 0, 6, -12, 0))),
        (0.7, merged(P('arm_r', 0, 0, 0, -92, 70, -25), P('body', 0, 0, 0, 0, 24, 0),
                     P('head', 0, 0, 0, 0, 16, 0), P('arm_l', 0, 0, 0, -30, 0, 25),
                     P('cape', 0, 0, 0, 6, 12, 0), P('beads', 0, 0, 0, 0, 14, 0))),
        (1.0, merged()),
    ]
    clips['fan_swipe'] = clip(0.9, sample(fs_kfs, 12))

    # basic_strike（P1 精修 · 普攻）：短促前刺。
    bs_kfs = [
        (0.0, merged()),
        (0.35, merged(P('arm_r', 0, 0, 2, -75, 0, 0), P('body', 0, 0, 0, 8),
                      P('head', 0, 0, 0, 4), P('arm_l', 0, 0, 0, -25, 0, 12))),
        (0.65, merged(P('arm_r', 0, 0, 1.5, -70, 0, 0), P('body', 0, 0, 0, 6))),
        (1.0, merged()),
    ]
    clips['basic_strike'] = clip(0.6, sample(bs_kfs, 9))

    # ring_burst（新招 · 雷环冲击波）：下蹲蓄力→爆起双臂外扬。
    rb_kfs = [
        (0.0, merged()),
        (0.3, merged(P('root', 0, -3, 0), P('body', 0, 0, 0, 16), P('head', 0, 0, 0, -8),
                     P('leg_r', 0, 0, 0, -28), P('leg_l', 0, 0, 0, -28),
                     P('arm_r', 0, 0, 0, -35, 0, -30), P('arm_l', 0, 0, 0, -35, 0, 30),
                     P('staff', 0, 0, 0, 0, 0, 0, 1.25, 1.25, 1.25))),
        (0.62, merged(P('root', 0, 0, 0), P('body', 0, 0, 0, -12), P('head', 0, 0, 0, -16),
                      P('arm_r', 0, 0, 0, -20, 0, -85), P('arm_l', 0, 0, 0, -20, 0, 85),
                      P('leg_r', 0, 0, 0, 5), P('leg_l', 0, 0, 0, 5),
                      P('cape', 0, 0, 0, -18, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.4, 1.4, 1.4))),
        (1.0, merged()),
    ]
    clips['ring_burst'] = clip(1.0, sample(rb_kfs, 13))

    # claw_combo（新招 · 雷爪三连）：左爪→右杖→双手下压三段。
    cc_kfs = [
        (0.0, merged()),
        (0.14, merged(P('arm_l', 0, 0, 1, -70, 0, 18), P('body', 0, 0, 0, 0, -14, 0),
                      P('head', 0, 0, 0, 0, -8, 0))),
        (0.24, merged()),
        (0.38, merged(P('arm_r', 0, 0, 1, -80, 0, -14), P('body', 0, 0, 0, 0, 16, 0),
                      P('head', 0, 0, 0, 0, 10, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.12, 1.12, 1.12))),
        (0.48, merged()),
        (0.62, merged(P('arm_l', 0, 0, 0, -125, 0, 10), P('arm_r', 0, 0, 0, -125, 0, -10),
                      P('body', 0, 0, 0, -10), P('head', 0, 0, 0, -18),
                      P('cape', 0, 0, 0, -10, 0, 0))),
        (0.78, merged(P('arm_l', 0, 0, 1, -15, 0, 8), P('arm_r', 0, 0, 1, -15, 0, -8),
                      P('body', 0, 0, 0, 22), P('head', 0, 0, 0, 10),
                      P('cape', 0, 0, 0, 14, 0, 0), P('beads', 0, 0, 0, 0, -12, 0))),
        (1.0, merged()),
    ]
    clips['claw_combo'] = clip(1.3, sample(cc_kfs, 17))

    # blink_step（新招 · 雷闪位移）：伏低前冲姿态→回位。
    bl_kfs = [
        (0.0, merged()),
        (0.2, merged(P('root', 0, -2.5, 0), P('body', 0, 0, 0, 28), P('head', 0, 0, 0, 10),
                     P('leg_r', 0, 0, 0, -40), P('leg_l', 0, 0, 0, -12),
                     P('arm_r', 0, 0, 0, 35, 0, -18), P('arm_l', 0, 0, 0, 35, 0, 18),
                     P('cape', 0, 0, 0, 24, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.2, 1.2, 1.2))),
        (0.6, merged(P('root', 0, -1.5, 0), P('body', 0, 0, 0, 22), P('head', 0, 0, 0, 6),
                     P('leg_r', 0, 0, 0, -32), P('leg_l', 0, 0, 0, -8),
                     P('arm_r', 0, 0, 0, 28, 0, -15), P('arm_l', 0, 0, 0, 28, 0, 15),
                     P('cape', 0, 0, 0, 18, 0, 0))),
        (1.0, merged()),
    ]
    clips['blink_step'] = clip(0.55, sample(bl_kfs, 9))

    # summon_ritual（P2 · 唤雷仪式/召唤）：跪地插杖、左手向天。
    sr_kfs = [
        (0.0, merged()),
        (0.2, merged(P('root', 0, -5, 0), P('leg_r', 0, 0, 0, -75), P('leg_l', 0, 0, 0, -25),
                     P('body', 0, 0, 0, 6), P('arm_r', 0, 0, 0, 15),
                     P('arm_l', 0, 0, 0, -60, 0, 15), P('head', 0, 0, 0, 8))),
        (0.5, merged(P('root', 0, -5, 0), P('leg_r', 0, 0, 0, -75), P('leg_l', 0, 0, 0, -25),
                     P('body', 0, 0, 0, 2), P('arm_r', 0, 0, 0, 15),
                     P('arm_l', 0, 0, 0, -145, 0, 20), P('head', 0, 0, 0, -22),
                     P('staff', 0, 0, 0, 0, 0, 0, 1.35, 1.35, 1.35),
                     P('cape', 0, 0, 0, -14, 0, 0), P('beads', 0, 0, 0, 0, -6, 0))),
        (0.68, merged(P('root', 0, -5, 0), P('leg_r', 0, 0, 0, -75), P('leg_l', 0, 0, 0, -25),
                      P('body', 0, 0, 0, 2), P('arm_r', 0, 0, 0, 15),
                      P('arm_l', 0, 0, 0, -135, 0, 18), P('head', 0, 0, 0, -18),
                      P('staff', 0, 0, 0, 0, 0, 0, 1.6, 1.6, 1.6),
                      P('cape', 0, 0, 0, -16, 0, 0))),
        (1.0, merged()),
    ]
    clips['summon_ritual'] = clip(1.8, sample(sr_kfs, 17))

    # phase2_transition（转阶段 P2）：举杖唤雷、冠角宝石亮起。
    p2_kfs = [
        (0.0, merged()),
        (0.22, merged(P('body', 0, 0, 0, -8), P('head', 0, 0, 0, -16),
                      P('arm_r', 0, 0, 0, -60, 0, -10), P('arm_l', 0, 0, 0, -60, 0, 10))),
        (0.5, merged(P('root', 0, 1.5, 0), P('body', 0, 0, 0, -14), P('head', 0, 0, 0, -30),
                     P('arm_r', 0, 0, 0, -172, 0, -6), P('arm_l', 0, 0, 0, -150, 0, 18),
                     P('cape', 0, 0, 0, -22, 0, 0), P('ear_l', 0, 0, 0, 0, 0, -10),
                     P('ear_r', 0, 0, 0, 0, 0, 10),
                     P('staff', 0, 0, 0, 0, 0, 0, 1.7, 1.7, 1.7),
                     P('crown', 0, 0, 0, 0, 0, 0, 1.25, 1.25, 1.25))),
        (0.72, merged(P('root', 0, 1, 0), P('body', 0, 0, 0, -12), P('head', 0, 0, 0, -26),
                      P('arm_r', 0, 0, 0, -170, 0, -6), P('arm_l', 0, 0, 0, -148, 0, 18),
                      P('cape', 0, 0, 0, -20, 0, 0),
                      P('staff', 0, 0, 0, 0, 0, 0, 1.5, 1.5, 1.5),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.2, 1.2, 1.2))),
        (1.0, merged()),
    ]
    clips['phase2_transition'] = clip(2.2, sample(p2_kfs, 19))

    # phase3_transition（转阶段 P3）：伏身蓄怒→暴起嘶吼。
    p3_kfs = [
        (0.0, merged()),
        (0.3, merged(P('body', 0, 0, 0, 26), P('head', 0, 0, 0, 26),
                     P('arm_r', 0, 0, 0, -20, 0, -55), P('arm_l', 0, 0, 0, -20, 0, 55),
                     P('root', 0, -2, 0), P('leg_r', 0, 0, 0, -20), P('leg_l', 0, 0, 0, -20),
                     P('cape', 0, 0, 0, 16, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3))),
        (0.42, merged(P('body', 0, 0, 0, 24, 4, 0), P('head', 0, 0, 0, 24),
                      P('arm_r', 0, 0, 0, -20, 0, -50), P('arm_l', 0, 0, 0, -20, 0, 50),
                      P('root', 0, -2, 0))),
        (0.54, merged(P('body', 0, 0, 0, 26, -4, 0), P('head', 0, 0, 0, 26),
                      P('arm_r', 0, 0, 0, -20, 0, -55), P('arm_l', 0, 0, 0, -20, 0, 55),
                      P('root', 0, -2, 0))),
        (0.7, merged(P('body', 0, 0, 0, -8), P('head', 0, 0, 0, -28),
                     P('arm_r', 0, 0, 0, -30, 0, -85), P('arm_l', 0, 0, 0, -30, 0, 85),
                     P('root', 0, 1, 0), P('leg_r', 0, 0, 0, 8), P('leg_l', 0, 0, 0, 8),
                     P('cape', 0, 0, 0, -24, 0, 0), P('ear_l', 0, 0, 0, 0, 0, -12),
                     P('ear_r', 0, 0, 0, 0, 0, 12),
                     P('staff', 0, 0, 0, 0, 0, 0, 2.0, 2.0, 2.0),
                     P('crown', 0, 0, 0, 0, 0, 0, 1.4, 1.4, 1.4))),
        (1.0, merged()),
    ]
    clips['phase3_transition'] = clip(2.4, sample(p3_kfs, 19))

    # ultimate_storm（怒气大招 · 风暴之眼）：双手举杖聚能→前压爆发。
    us_kfs = [
        (0.0, merged()),
        (0.18, merged(P('arm_r', 0, 0, 0, -140, 0, -8), P('arm_l', 0, 0, 0, -100, 0, 14),
                      P('body', 0, 0, 0, -6), P('head', 0, 0, 0, -14))),
        (0.5, merged(P('arm_r', 0, 0, 0, -176, 0, -5), P('arm_l', 0, 0, 0, -160, 0, 12),
                     P('body', 0, 0, 0, -14), P('head', 0, 0, 0, -30),
                     P('root', 0, 2, 0), P('cape', 0, 0, 0, -18, 0, 0),
                     P('staff', 0, 0, 0, 0, 0, 0, 1.5, 1.5, 1.5),
                     P('crown', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3))),
        (0.68, merged(P('arm_r', 0, 0, 0, -176, 0, -5), P('arm_l', 0, 0, 0, -160, 0, 12),
                      P('body', 0, 0, 0, -14), P('head', 0, 0, 0, -30),
                      P('root', 0, 2, 0), P('cape', 0, 0, 0, -18, 0, 0),
                      P('staff', 0, 0, 0, 0, 0, 0, 2.2, 2.2, 2.2),
                      P('crown', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3),
                      P('beads', 0, 0, 0, 0, -14, 0))),
        (0.84, merged(P('arm_r', 0, 0, 0, -40, 0, -4), P('arm_l', 0, 0, 0, -30, 0, 8),
                      P('body', 0, 0, 0, 26), P('head', 0, 0, 0, 8),
                      P('root', 0, 0, 0), P('leg_r', 0, 0, 0, -14), P('leg_l', 0, 0, 0, -14),
                      P('cape', 0, 0, 0, 22, 0, 0), P('staff', 0, 0, 0, 0, 0, 0, 1.3, 1.3, 1.3))),
        (1.0, merged()),
    ]
    clips['ultimate_storm'] = clip(2.0, sample(us_kfs, 17))

    return clips


# ---------------------------------------------------------------- 组装与校验

def rects_overlap(a, b):
    ax, ay, aw, ah = a
    bx, by, bw, bh = b
    return ax < bx + bw and bx < ax + aw and ay < by + bh and by < ay + ah


def build_model_json(bones, clips):
    data = {
        'id': MODEL_ID, 'name': MODEL_NAME, 'role': MODEL_ROLE,
        'coordinates': COORDINATES,
        'texture': 'textures/entity/goblin_camp/%s.png' % MODEL_ID,
        'texture_size': TEXTURE_SIZE,
        'bones': [{'name': b['name'], 'parent': b['parent'], 'pivot': b['pivot'],
                   'rotation': b['rotation'], 'effect': b['effect'],
                   'cubes': [{'name': c['name'], 'material': c['material'], 'box': c['box'],
                              'rotation': c['rotation'], 'uv': c['uv']} for c in b['cubes']]}
                  for b in bones],
        'clips': clips,
    }
    return data


def validate_model(bones, data, cv):
    if (cv.w, cv.h) != (TEXTURE_SIZE, TEXTURE_SIZE):
        fail('画布尺寸 %dx%d 与 texture_size=%d 不一致' % (cv.w, cv.h, TEXTURE_SIZE))
    if data['texture_size'] != TEXTURE_SIZE or data['id'] != MODEL_ID:
        fail('顶层字段与规格不符')
    if data['texture'] != 'textures/entity/goblin_camp/%s.png' % MODEL_ID:
        fail('texture 路径字段不符')
    height = model_height(bones)
    if not (HEIGHT_MIN <= height <= HEIGHT_MAX):
        fail('模型总高 %.1f 超出容差 %.0f~%.0f' % (height, HEIGHT_MIN, HEIGHT_MAX))
    bones_j = data['bones']
    if bones_j[0]['name'] != 'root' or bones_j[0]['parent'] != '' or bones_j[0]['pivot'] != [0, 0, 0]:
        fail('bones[0] 必须 root/父名空/pivot[0,0,0]')
    seen = set()
    regions = []
    for index, bone in enumerate(bones_j):
        if bone['name'] in seen:
            fail('骨骼重名 %s' % bone['name'])
        if index == 0:
            if bone['parent'] != '':
                fail('仅首块骨骼可挂 ground')
        elif bone['parent'] not in seen:
            fail('骨骼 %s 的父级 %r 不存在或未在前' % (bone['name'], bone['parent']))
        if bone['effect'] is not False:
            fail('effect=true 骨骼会被 GblModel 隐藏，必须为 false')
        seen.add(bone['name'])
        for c in bone['cubes']:
            box = c['box']
            if len(box) != 6 or box[3] <= 0 or box[4] <= 0 or box[5] <= 0:
                fail('方体 %s 尺寸非法 %r' % (c['name'], box))
            u, v = c['uv']
            if not (isinstance(u, int) and isinstance(v, int)) or u < 0 or v < 0:
                fail('方体 %s uv 非法 %r' % (c['name'], c['uv']))
            w, h, d = box[3], box[4], box[5]
            if u + 2 * (w + d) > TEXTURE_SIZE + EPS:
                fail('方体 %s UV 区域宽越界' % c['name'])
            if v + (h + d) > TEXTURE_SIZE + EPS:
                fail('方体 %s UV 区域高越界' % c['name'])
            regions.append((c, (u, v, 2 * (w + d), h + d)))
    for i in range(len(regions)):
        for j in range(i + 1, len(regions)):
            ca, ra = regions[i]
            cb, rb = regions[j]
            shared = (ca['uv'] == cb['uv'] and ca['box'][3:] == cb['box'][3:]
                      and ca['material'] == cb['material'])
            if shared:
                continue
            if rects_overlap(ra, rb):
                fail('UV 区域重叠：%s 与 %s' % (ca['name'], cb['name']))
    for name, c in data['clips'].items():
        if not (isinstance(c['seconds'], (int, float)) and c['seconds'] > 0):
            fail('clip %s.seconds 必须为正' % name)
        if not c['frames']:
            fail('clip %s.frames 为空' % name)
        for frame in c['frames']:
            if len(frame) != len(bones_j):
                fail('clip %s 帧行数 %d != 骨骼数 %d' % (name, len(frame), len(bones_j)))
            for row in frame:
                if len(row) != 9:
                    fail('clip %s 帧行必须为 9 值' % name)
                if row[6] <= 0 or row[7] <= 0 or row[8] <= 0:
                    fail('clip %s 缩放必须为正' % name)
    if 'idle' not in data['clips']:
        fail('clips 缺少 idle（GblModel 回退必需）')
    for required in ('walk', 'hurt', 'death'):
        if required not in data['clips']:
            fail('clips 缺少保留名 %s' % required)


# ---------------------------------------------------------------- 预览

def build_preview(cv):
    scale = 3
    cell_w, cell_h = cv.w * scale, cv.h * scale
    margin = 12
    sheet = Canvas(cell_w + margin * 2, cell_h + margin * 2)
    rect(sheet, 0, 0, sheet.w, sheet.h, (36, 36, 44, 255))
    for qy in range(0, cell_h, 8):
        for qx in range(0, cell_w, 8):
            tone = (58, 58, 68, 255) if ((qx // 8 + qy // 8) % 2 == 0) else (44, 44, 54, 255)
            rect(sheet, margin + qx, margin + qy, 8, 8, tone)
    for sy in range(cv.h):
        for sx in range(cv.w):
            color = cv.px[sy][sx]
            if color[3] == 0:
                continue
            for dy in range(scale):
                for dx in range(scale):
                    sheet.set(margin + sx * scale + dx, margin + sy * scale + dy,
                              (color[0], color[1], color[2], 255))
    path = os.path.join(PREVIEW_DIR, 'preview.png')
    write_png(path, sheet.w, sheet.h, sheet.rows())
    return path


# ---------------------------------------------------------------- 主流程

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
            fail('骨骼清单与序表不一致')
        pack_uv(bones)
        clips = build_clips()
        data = build_model_json(bones, clips)

        cv = Canvas(TEXTURE_SIZE, TEXTURE_SIZE)
        for b in bones:
            for c in b['cubes']:
                faces = box_faces(c['uv'][0], c['uv'][1], c['box'][3], c['box'][4], c['box'][5])
                c['paint'](cv, faces, c)
        validate_model(bones, data, cv)

        json_path = os.path.join(DUNGEON_DIR, MODEL_ID + '.json')
        png_path = os.path.join(TEXTURE_DIR, MODEL_ID + '.png')
        text = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
        json.loads(text)
        with open(json_path, 'w', encoding='utf-8', newline='\n') as fh:
            fh.write(text)
        write_png(png_path, TEXTURE_SIZE, TEXTURE_SIZE, cv.rows())

        # 落盘后复核：JSON 可解析、PNG 头与 IHDR 尺寸正确。
        with open(json_path, 'rb') as fh:
            back = json.loads(fh.read().decode('utf-8'))
        if back['id'] != MODEL_ID or 'idle' not in back['clips']:
            fail('落盘 JSON 复核失败')
        with open(png_path, 'rb') as fh:
            blob = fh.read()
        if blob[:8] != b'\x89PNG\r\n\x1a\n':
            fail('PNG 头非法')
        ih = struct.unpack('>II', blob[16:24])
        if ih != (TEXTURE_SIZE, TEXTURE_SIZE):
            fail('IHDR 尺寸 %r 与 texture_size 不符' % (ih,))

        preview_path = build_preview(cv)
        cubes_count = sum(len(b['cubes']) for b in bones)
        clip_names = ', '.join(sorted(clips))
        print('[OK] %s（%s）' % (MODEL_NAME, MODEL_ROLE))
        print('     骨骼 %d / 方体 %d / clips %d：%s' % (len(bones), cubes_count, len(clips), clip_names))
        print('     模型总高 %.1f 单位（≈%.2f 格）' % (model_height(bones), model_height(bones) / 16.0))
        print('     %s' % rel(json_path))
        print('     %s' % rel(png_path))
        print('     预览：%s' % rel(preview_path))
        print('校验通过：json.loads 往返 OK；PNG 头/IHDR OK；UV 无越界、无重叠；'
              '骨骼链 OK；帧行==骨骼数；9 值；缩放为正；idle/walk/hurt/death 保留名齐全。')
    except AssetError as error:
        print('[FAIL] %s' % error)
        sys.exit(1)


if __name__ == '__main__':
    main()

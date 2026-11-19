#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""魔王祭坛Boss · 战斗机关运行模型与贴图制作源（正式源，提交用）。

生成 14 种 altar_mech 机关实体的小尺寸方体模型 JSON 与贴图 PNG：

- 模型格式对齐 GblModel.java（src/client/java/cn/yumis/fight/modules/dungeons/client/GblModel.java）：
  Data{texture_size, bones[{name,parent,pivot,rotation,effect,cubes[...]}], clips}；
  cube 为 {name,material,box:[minx,miny,minz,w,h,d],rotation,uv:[u,v]}。
  坐标 Y 向上、+Z 正面、一格 16 单位；cube.uv 是原版 addBox 箱式展开区域左上角，
  顶/底/四侧面按展开位取样；GblModel 将 root 挂到 ground，实体原点在脚底。
- bones[0] 固定 name="root"、parent=""、pivot=[0,0,0]；全部方体 miny>=0 贴地；
  clips 必含 "idle"（缺失 GblModel 会抛错），本批为静止造型：单帧、位移旋转全 0、缩放全 1。
- 贴图 16/32 正方形透明 PNG（纯 stdlib zlib+struct 写出，png_chunk 写法参考
  tools/industrial_technology.py），程序色块绘制，光照统一左上亮右下暗；
  材质参考原版（木/石/铜/麻线）。
- 自带尺寸与网格校验：UV 区域不越界、方体间区域不重叠（共用同一贴图块除外）、
  box/miny/骨骼链/clip 行数等约束；校验失败打印后以非零码退出。
- 运行后输出落位路径清单，并拼 7x2 预览图到 temp/altar-mech-icons/sheet.png。

用法：python tools/altar_boss_mechanisms.py
仅写入 dungeons/altar_mech/<id>.json、textures/entity/altar_mech/<id>.png 与 temp 预览。
"""

import json
import math
import os
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSET = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'yumis_dungeon_and_fight')
DUNGEON_DIR = os.path.join(ASSET, 'dungeons', 'altar_mech')
TEXTURE_DIR = os.path.join(ASSET, 'textures', 'entity', 'altar_mech')
PREVIEW_DIR = os.path.join(ROOT, 'temp', 'altar-mech-icons')

COORDINATES = 'Y up, +Z front, 16 units/block'
EPS = 1e-6


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


def cube(name, material, box, uv, rotation=(0, 0, 0), paint=None):
    return {'name': name, 'material': material, 'box': list(box),
            'rotation': list(rotation), 'uv': list(uv), 'paint': paint}


# ---------------------------------------------------------------- 调色板（参考原版木/石/铜/麻线）

BARK = (123, 106, 85); BARK_DK = (90, 76, 60); TIP = (169, 212, 119); TIP_HI = (205, 235, 150)
MOSS = (92, 125, 54); MOSS_T = (99, 134, 57); MOSS_DK = (70, 97, 42); MOSS_DK2 = (63, 84, 36); MOSS_LT = (121, 162, 68)
ROOTC = (74, 53, 36); ROOT_DK = (51, 36, 24); ROOT_HI = (107, 77, 51)
STEM = (185, 225, 127); LEAF = (214, 242, 162); GLOW_HI = (244, 255, 214)
COPPER = (168, 106, 52); COPPER_DK = (125, 74, 34); COPPER_LT = (192, 124, 64)
BRIGHT = (221, 154, 82); BRIGHT_HI = (240, 184, 120)
LINEN = (230, 221, 194)
PLATE = (87, 80, 107); PLATE_DK = (73, 66, 92); PLATE_LT = (109, 100, 134)
RUNE = (201, 166, 242); RUNE_HI = (220, 188, 247); RUNE_DK = (168, 132, 216)
CALC = (232, 228, 218); CALC_DK = (208, 203, 190); VEIN = (147, 167, 201); VEIN_C = (116, 207, 224)
GOLD = (217, 164, 65); GOLD_DK = (166, 120, 42); FACE = (242, 234, 216); CRACK = (58, 52, 45)
BRASS = (201, 144, 59); BRASS_DK = (150, 104, 42); BRASS_LT = (224, 178, 92)
WOOD = (110, 74, 42); WOOD_DK = (84, 54, 30); WOOD_LT = (134, 94, 56)
FLAME = (226, 88, 34); FLAME_TIP = (196, 58, 26); FLAME_BASE = (255, 217, 128)
STONE = (90, 90, 96); STONE_DK = (63, 63, 69); STONE_LT = (122, 122, 130); CARVE = (58, 58, 64); CHIP = (154, 158, 168)
OAK = (58, 42, 28); OAK_DK = (40, 28, 18); OAK_LT = (78, 58, 38); SLIT = (24, 15, 9)
OBS = (34, 24, 52); OBS_DK = (24, 16, 38); OBS_HI = (54, 42, 82); ETCH = (79, 146, 168); ETCH_HI = (112, 182, 202)


# ---------------------------------------------------------------- 各机关面片绘制

def paint_fake_branch(cv, f, c):
    fill_sides(cv, f, BARK, seed=11, jit=0.08)
    for key in ('east', 'front', 'west', 'back'):
        x, y, w, h = f[key]
        fill(cv, x, y + h - 2, w, 2, BARK_DK, bias=-0.10)      # 贴地沉色
        fill(cv, x, y, w, 1, TIP)                               # 末端浅绿
    fill(cv, *f['up'], TIP_HI, seed=2, jit=0.06)
    fill(cv, *f['down'], (78, 66, 52), bias=-0.10)
    x, y, w, h = f['front']
    cv.set(x, y + 2, rgba(BARK_DK, 0.9))                       # 节疤


def paint_hedge_base(cv, f, c):
    fill_sides(cv, f, MOSS, seed=21, jit=0.10)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, w, 1, MOSS_DK2, bias=-0.06)      # 底缘深绿暗部
    fill(cv, *f['up'], MOSS_LT, seed=22, jit=0.08)
    x, y, w, h = f['up']
    fill(cv, x + 1, y + 1, 2, 2, MOSS_DK2, bias=-0.12)         # 顶丛落影
    for x, y, w, h in side_list(f):
        cv.set(x, y + 1, rgba(MOSS_DK))                        # 深绿碎斑
        cv.set(x + w - 1, y, rgba(MOSS_DK, 0.85))
    fill(cv, *f['down'], MOSS_DK2, bias=-0.15)


def paint_hedge_top(cv, f, c):
    fill_sides(cv, f, MOSS_T, seed=23, jit=0.09)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, w, 1, MOSS_DK, bias=-0.04)
    fill(cv, *f['up'], (133, 172, 76), seed=24, jit=0.08)
    fill(cv, *f['down'], MOSS_DK2, bias=-0.10)


def paint_knot_main(cv, f, c):
    fill_sides(cv, f, ROOTC, seed=31, jit=0.10)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, w, 1, ROOT_DK, bias=-0.05)
    fill(cv, *f['up'], (86, 62, 42), seed=32, jit=0.09)
    x, y, w, h = f['up']
    cv.set(x + 1, y + 1, rgba(ROOT_DK))                        # 中央根缝
    cv.set(x, y + 1, rgba(ROOT_HI, 0.9))
    cv.set(x + 1, y, rgba(ROOT_HI, 0.9))
    fill(cv, *f['down'], ROOT_DK, bias=-0.10)


def paint_knot_bump(cv, f, c):
    fill_sides(cv, f, ROOTC, seed=33, jit=0.10)
    fill(cv, *f['up'], (96, 69, 46), seed=34, jit=0.08)
    fill(cv, *f['down'], ROOT_DK, bias=-0.10)


def paint_sprout_stem(cv, f, c):
    fill_sides(cv, f, STEM, seed=41, jit=0.05)
    fill(cv, *f['up'], LEAF, bias=0.05)
    fill(cv, *f['down'], (140, 180, 96), bias=-0.05)


def paint_sprout_leaf(cv, f, c):
    fill_sides(cv, f, LEAF, seed=42, jit=0.06, bias=0.06)      # 浅黄绿发光感
    fill(cv, *f['up'], GLOW_HI)
    fill(cv, *f['down'], (166, 200, 120), bias=-0.05)


def paint_spring_seg(cv, f, c):
    fill_sides(cv, f, COPPER, seed=51, jit=0.07)
    fill(cv, *f['up'], COPPER_LT, bias=0.04)
    fill(cv, *f['down'], COPPER_DK, bias=-0.05)


def paint_spring_top(cv, f, c):
    fill_sides(cv, f, BRIGHT, seed=52, jit=0.06)
    fill(cv, *f['up'], BRIGHT_HI)
    x, y, w, h = f['up']
    cv.set(x, y, rgba((255, 220, 160)))                        # 顶段亮铜高光
    fill(cv, *f['down'], COPPER_DK, bias=-0.05)


def paint_pull_thread(cv, f, c):
    u, v = c['uv']
    w, h, d = c['box'][3], c['box'][4], c['box'][5]
    fill(cv, u, v, 2 * (w + d), h + d, LINEN, seed=61, jit=0.05)
    for ix in range(1, int(2 * (w + d)) - 1, 3):              # 沿 +Z 捻纹
        fill(cv, u + ix, v, 1, h + d, LINEN, bias=-0.14)
    x, y, w2, h2 = f['up']
    fill(cv, x, y, w2, max(h2, 1), LINEN, bias=0.08)           # 上棱受光


def paint_pull_knot(cv, f, c):
    u, v = c['uv']
    w, h, d = c['box'][3], c['box'][4], c['box'][5]
    fill(cv, u, v, 2 * (w + d), h + d, (214, 199, 163), seed=62, jit=0.06)
    x, y, w2, h2 = f['front']
    fill(cv, x, y, max(w2, 1), max(h2, 1), (193, 177, 138))    # 两端小结缠带


def paint_anti_platform(cv, f, c):
    fill_sides(cv, f, PLATE_DK, seed=71, jit=0.06)
    fill(cv, *f['up'], PLATE, seed=72, jit=0.06)
    x, y, w, h = f['up']
    fill(cv, x + 1, y + 1, 2, 2, RUNE)                         # 中央法阵
    for (dx, dy) in ((0, 0), (3, 0), (0, 3), (3, 3)):
        cv.set(x + dx, y + dy, rgba(RUNE_DK))                  # 四角阵点
    for (dx, dy) in ((2, 0), (0, 2), (3, 2), (2, 3)):
        cv.set(x + dx, y + dy, rgba(RUNE_DK, 0.8))
    cv.set(x + 1, y + 1, rgba(RUNE_HI))
    fill(cv, *f['down'], (58, 53, 74), bias=-0.06)


def paint_anti_core(cv, f, c):
    fill_sides(cv, f, (177, 140, 224), seed=73, jit=0.05)
    fill(cv, *f['up'], (233, 213, 255))
    x, y, w, h = f['up']
    cv.set(x, y, rgba((247, 236, 255)))                        # 淡紫发光芯
    fill(cv, *f['down'], (140, 105, 196), bias=-0.05)


def paint_res_base(cv, f, c):
    fill_sides(cv, f, CALC, seed=81, jit=0.05)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, w, 1, CALC_DK, bias=-0.04)
    fill(cv, *f['up'], CALC, seed=82, jit=0.05)
    x, y, w, h = f['up']
    for (dx, dy) in ((1, 0), (0, 2), (2, 2)):                 # 方解石蓝纹
        cv.set(x + dx, y + dy, rgba(VEIN))
    fill(cv, *f['down'], CALC_DK, bias=-0.06)


def paint_res_pillar(cv, f, c):
    fill_sides(cv, f, CALC, seed=83, jit=0.05)
    for x, y, w, h in side_list(f):
        cv.set(x, y + 1, rgba(VEIN, 0.9))                      # 竖向蓝纹
    fill(cv, *f['up'], VEIN_C)                                # 顶面微光青
    fill(cv, *f['down'], CALC_DK, bias=-0.06)


def paint_watch_dial(cv, f, c):
    fill_sides(cv, f, GOLD, seed=91, jit=0.06)
    fill(cv, *f['down'], GOLD_DK, bias=-0.05)
    x, y, w, h = f['up']
    fill(cv, x, y, w, h, GOLD)
    cv.set(x, y, rgba((240, 196, 96)))                         # 金壳左上亮缘
    cv.set(x + 1, y, rgba(FACE))                              # 白表面上半
    cv.set(x, y + 1, rgba((206, 196, 174)))                   # 裂纹延伸阴影
    cv.set(x + 1, y + 1, rgba(CRACK))                         # 深色裂纹


def paint_watch_crown(cv, f, c):
    u, v = c['uv']
    w, h, d = c['box'][3], c['box'][4], c['box'][5]
    fill(cv, u, v, 2 * (w + d), h + d, GOLD, seed=92, jit=0.06)
    x, y, w2, h2 = f['up']
    fill(cv, x, y, max(w2, 1), max(h2, 1), (240, 196, 96))


def paint_pan_rod(cv, f, c):
    fill_sides(cv, f, BRASS, seed=101, jit=0.06)
    fill(cv, *f['up'], BRASS_LT)
    fill(cv, *f['down'], BRASS_DK, bias=-0.05)


def paint_pan_dish(cv, f, c):
    fill_sides(cv, f, BRASS_DK, seed=102, jit=0.06)
    fill(cv, *f['up'], BRASS_LT, seed=103, jit=0.05)
    x, y, w, h = f['up']
    cv.set(x, y, rgba((236, 196, 116)))                        # 盘心高光
    cv.set(x + 1, y + 1, rgba((186, 130, 54)))                 # 对角沉色
    fill(cv, *f['down'], BRASS_DK, bias=-0.06)


def paint_log(cv, f, c):
    fill_sides(cv, f, WOOD, seed=111, jit=0.09)
    for key in ('east', 'west'):                              # 端面年轮沉色
        x, y, w, h = f[key]
        fill(cv, x, y, max(w, 1), max(h, 1), WOOD_DK)
    for key in ('front', 'back'):
        x, y, w, h = f[key]
        fill(cv, x, y, 1, max(h, 1), WOOD_DK, bias=-0.02)     # 柴纹
    fill(cv, *f['up'], WOOD_LT)
    fill(cv, *f['down'], WOOD_DK, bias=-0.06)


def paint_flame(cv, f, c):
    fill_sides(cv, f, FLAME, seed=121, jit=0.05, bias=0.04)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, max(w, 1), 1, (255, 196, 110))  # 火根更亮
    fill(cv, *f['up'], FLAME_TIP)                              # 火尖偏红
    fill(cv, *f['down'], FLAME_BASE)


def paint_stele_base(cv, f, c):
    fill_sides(cv, f, STONE, seed=131, jit=0.08)
    for x, y, w, h in side_list(f):
        fill(cv, x, y + h - 1, w, 1, STONE_DK, bias=-0.04)
    fill(cv, *f['up'], STONE_LT, seed=132, jit=0.06)
    fill(cv, *f['down'], STONE_DK, bias=-0.06)


def paint_stele_body(cv, f, c):
    fill_sides(cv, f, STONE, seed=133, jit=0.08)
    for x, y, w, h in side_list(f):
        fill(cv, x, y, max(w, 1), 1, STONE_LT, bias=0.02)     # 顶缘受光
        cv.set(x, y + 1, rgba(STONE_DK))                      # 横向刻痕
    fill(cv, *f['up'], STONE_LT)
    fill(cv, *f['down'], STONE_DK, bias=-0.06)
    x, y, w, h = f['front']
    cv.set(x, y + 1, rgba(CARVE))                             # 正面刻痕色点
    cv.set(x, y + 2, rgba(CARVE, 0.85))
    cv.set(x, y, rgba(CHIP, 0.8))


def paint_booth_body(cv, f, c):
    fill_sides(cv, f, OAK, seed=141, jit=0.08)
    for x, y, w, h in side_list(f):
        fill(cv, x, y, max(w, 1), 1, OAK_LT)                   # 顶压条
        fill(cv, x, y + h - 1, max(w, 1), 1, OAK_DK)           # 底座裙
        fill(cv, x, y + h - 2, max(w, 1), 1, OAK_DK, bias=-0.04)
    fill(cv, *f['up'], OAK_DK, bias=-0.02)
    fill(cv, *f['down'], OAK_DK, bias=-0.08)
    x, y, w, h = f['front']
    for dy in range(int(h)):
        cv.set(x + 1, y + dy, rgba(SLIT))                       # 正面竖缝（贴图绘制）
    cv.set(x, y + 1, rgba(OAK_LT, 0.9))                        # 缝侧亮线


def paint_booth_eave(cv, f, c):
    fill_sides(cv, f, OAK_DK, seed=142, jit=0.07)
    fill(cv, *f['up'], OAK_LT, seed=143, jit=0.06)
    fill(cv, *f['down'], OAK_DK, bias=-0.06)


def paint_shard(cv, f, c):
    u, v = c['uv']
    w, h, d = c['box'][3], c['box'][4], c['box'][5]
    fill(cv, u, v, 2 * (w + d), h + d, OBS, seed=161, jit=0.07)  # 深紫黑底
    x, y, w2, h2 = f['up']
    fill(cv, x, y, w2, h2, OBS_HI, bias=0.04)                   # 顶面微光
    bx, by, bw, bh = irect(*f['front'])
    cv.set(bx, by + 1, rgba(ETCH))                             # 哑光青蓝刻痕
    cv.set(bx + 1, by, rgba(ETCH, 0.9))
    cv.set(bx + 1, by + 1, rgba(ETCH_HI, 0.85))
    fill(cv, *f['down'], OBS_DK, bias=-0.06)


# ---------------------------------------------------------------- 14 种机关清单

MODELS = [
    {'id': 'ritual_fake_branch', 'name': '假枝诱饵', 'size': 16, 'bones': [
        ('branch', [cube('枯枝', 'wood', [-0.5, 0, -0.5, 1, 5, 1], (0, 0), (10, 0, -8), paint_fake_branch)])]},
    {'id': 'ritual_hedge', 'name': '树篱分身', 'size': 16, 'bones': [
        ('hedge', [cube('树篱底丛', 'leaf', [-1.5, 0, -1.5, 3, 2, 3], (0, 0), (0, 0, 0), paint_hedge_base),
                   cube('树篱顶丛', 'leaf', [-1, 2, -1, 2, 1, 2], (0, 6), (0, 0, 0), paint_hedge_top)])]},
    {'id': 'ritual_root_knot', 'name': '根结', 'size': 16, 'bones': [
        ('knot', [cube('缠根主团', 'root', [-1, 0, -1, 2, 1, 2], (0, 0), (0, 0, 0), paint_knot_main),
                  cube('翘根突起·顶', 'root', [-0.75, 1, -0.75, 1, 0.5, 1], (0, 4), (0, 6, 0), paint_knot_bump),
                  cube('翘根突起·侧', 'root', [0.6, 0, -0.25, 1, 0.5, 1], (5, 4), (0, 0, 10), paint_knot_bump)])]},
    {'id': 'ritual_sprout', 'name': '活芽', 'size': 16, 'bones': [
        ('sprout', [cube('发光芽茎', 'leaf_light', [-0.25, 0, -0.25, 0.5, 2, 0.5], (0, 0), (0, 0, 0), paint_sprout_stem),
                    cube('嫩芽叶团', 'leaf_light', [-0.5, 2, -0.5, 1, 1, 1], (0, 4), (0, 0, 0), paint_sprout_leaf)])]},
    {'id': 'ritual_spring', 'name': '发条', 'size': 16, 'bones': [
        ('spring', [cube('铜簧底段', 'copper', [-0.5, 0, -0.5, 1, 1, 1], (0, 0), (0, 12, 0), paint_spring_seg),
                    cube('铜簧中段', 'copper', [-0.5, 1, -0.5, 1, 1, 1], (0, 0), (0, -12, 0), paint_spring_seg),
                    cube('亮铜顶段', 'copper', [-0.5, 2, -0.5, 1, 1, 1], (0, 3), (0, 8, 0), paint_spring_top)])]},
    {'id': 'ritual_pull_line', 'name': '拉线', 'size': 32, 'bones': [
        ('line', [cube('麻线长绳', 'linen', [-0.125, 0.5, -4, 0.25, 0.25, 8], (0, 0), (0, 0, 0), paint_pull_thread),
                  cube('线结·前', 'linen', [-0.25, 0.35, -4.5, 0.5, 0.5, 0.5], (0, 10), (0, 0, 0), paint_pull_knot),
                  cube('线结·后', 'linen', [-0.25, 0.35, 4, 0.5, 0.5, 0.5], (0, 12), (0, 0, 0), paint_pull_knot)])]},
    {'id': 'ritual_anti_magic', 'name': '反魔力阵', 'size': 16, 'bones': [
        ('platform', [cube('紫纹阵台', 'stone', [-2, 0, -2, 4, 0.4, 4], (0, 0), (0, 0, 0), paint_anti_platform)]),
        ('core', [cube('淡紫法芯', 'amethyst', [-0.5, 0.4, -0.5, 1, 0.6, 1], (0, 5), (0, 0, 0), paint_anti_core)])]},
    {'id': 'ritual_resonance', 'name': '共振台', 'size': 16, 'bones': [
        ('resonator', [cube('方解石台', 'calcite', [-1.5, 0, -1.5, 3, 1, 3], (0, 0), (0, 0, 0), paint_res_base),
                       cube('共振顶柱', 'calcite', [-0.5, 1, -0.5, 1, 1.5, 1], (0, 5), (0, 0, 0), paint_res_pillar)])]},
    {'id': 'ritual_broken_watch', 'name': '裂表', 'size': 16, 'bones': [
        ('watch', [cube('金壳表盘', 'gold', [-0.75, 0, -0.75, 1.5, 0.4, 1.5], (0, 0), (0, 0, 0), paint_watch_dial),
                   cube('侧向表冠', 'gold', [0.6, 0.05, -0.2, 0.3, 0.3, 0.3], (0, 3), (0, 0, 0), paint_watch_crown)])]},
    {'id': 'ritual_empty_pan', 'name': '空秤盘', 'size': 16, 'bones': [
        ('scale', [cube('黄铜秤杆', 'brass', [-0.15, 0, -0.15, 0.3, 2, 0.3], (0, 0), (0, 0, 0), paint_pan_rod),
                   cube('黄铜秤盘', 'brass', [-1, 2, -1, 2, 0.3, 2], (0, 3), (0, 0, 0), paint_pan_dish)])]},
    {'id': 'ritual_campfire', 'name': '临时篝火', 'size': 16, 'bones': [
        ('logs', [cube('交叉柴·一', 'wood', [-0.5, 0, -0.25, 1, 0.5, 0.5], (0, 0), (0, 40, 0), paint_log),
                  cube('交叉柴·二', 'wood', [-0.5, 0.5, -0.25, 1, 0.5, 0.5], (0, 0), (0, -40, 0), paint_log)]),
        ('flame', [cube('中心火苗', 'flame', [-0.25, 0.6, -0.25, 0.5, 1, 0.5], (0, 2), (0, 0, 0), paint_flame)])]},
    {'id': 'ritual_stele', 'name': '战绩碑', 'size': 16, 'bones': [
        ('stele', [cube('碎石碑座', 'stone', [-0.75, 0, -0.5, 1.5, 0.5, 1], (0, 0), (0, 0, 0), paint_stele_base),
                   cube('灰石碑身', 'stone', [-0.5, 0.5, -0.25, 1, 3, 0.5], (0, 2), (0, 0, 0), paint_stele_body)])]},
    {'id': 'ritual_confessional', 'name': '告解台', 'size': 16, 'bones': [
        ('booth', [cube('橡木亭身', 'dark_oak', [-0.75, 0, -0.75, 1.5, 3, 1.5], (0, 0), (0, 0, 0), paint_booth_body),
                   cube('橡木顶檐', 'dark_oak', [-1, 3, -1, 2, 0.5, 2], (0, 5), (0, 0, 0), paint_booth_eave)])]},
    {'id': 'ritual_shard', 'name': '晶片', 'size': 16, 'bones': [
        ('shard', [cube('黑曜晶片', 'obsidian', [-0.3, 0, -0.3, 0.6, 1, 0.6], (0, 0), (9, 0, -5), paint_shard)])]},
]


# ---------------------------------------------------------------- JSON 组装

def build_model_json(spec):
    bones = [{'name': 'root', 'parent': '', 'pivot': [0, 0, 0], 'rotation': [0, 0, 0],
              'effect': False, 'cubes': []}]
    for bone_name, cubes in spec['bones']:
        bones.append({'name': bone_name, 'parent': 'root', 'pivot': [0, 0, 0],
                      'rotation': [0, 0, 0], 'effect': False,
                      'cubes': [{'name': c['name'], 'material': c['material'], 'box': c['box'],
                                 'rotation': c['rotation'], 'uv': c['uv']} for c in cubes]})
    row = [0, 0, 0, 0, 0, 0, 1, 1, 1]
    idle = {'seconds': 1.0, 'frames': [[list(row) for _ in bones]]}
    return {'id': spec['id'], 'name': spec['name'], 'coordinates': COORDINATES,
            'texture': 'textures/entity/altar_mech/%s.png' % spec['id'],
            'texture_size': spec['size'], 'bones': bones, 'clips': {'idle': idle}}


# ---------------------------------------------------------------- 尺寸/网格校验

def rects_overlap(a, b):
    return (a[0] < b[0] + b[2] - EPS and b[0] < a[0] + a[2] - EPS
            and a[1] < b[1] + b[3] - EPS and b[1] < a[1] + a[3] - EPS)


def validate_model(spec, data, cv):
    mid, size = spec['id'], spec['size']
    if size not in (16, 32):
        fail('%s: texture_size 只允许 16/32，当前 %r' % (mid, size))
    if (cv.w, cv.h) != (size, size):
        fail('%s: 画布尺寸 %dx%d 与 texture_size=%d 不一致' % (mid, cv.w, cv.h, size))
    if data['texture_size'] != size or data['id'] != mid:
        fail('%s: 顶层字段与规格不符' % mid)
    if data['texture'] != 'textures/entity/altar_mech/%s.png' % mid:
        fail('%s: texture 路径字段不符' % mid)
    bones = data['bones']
    head = bones[0]
    if head['name'] != 'root' or head['parent'] != '' or head['pivot'] != [0, 0, 0]:
        fail('%s: bones[0] 必须 root/父名空/pivot[0,0,0]' % mid)
    seen = set()
    regions = []
    for index, bone in enumerate(bones):
        if bone['name'] in seen:
            fail('%s: 骨骼重名 %s' % (mid, bone['name']))
        if index == 0:
            if bone['parent'] != '':
                fail('%s: 仅首块骨骼可挂 ground' % mid)
        elif bone['parent'] not in seen:
            fail('%s: 骨骼 %s 的父级 %r 不存在或未在前' % (mid, bone['name'], bone['parent']))
        if bone['effect'] is not False:
            fail('%s: effect=true 骨骼会被 GblModel 隐藏，静止机关必须为 false' % mid)
        seen.add(bone['name'])
        for c in bone['cubes']:
            box = c['box']
            if len(box) != 6 or box[3] <= 0 or box[4] <= 0 or box[5] <= 0:
                fail('%s: 方体 %s 尺寸非法 %r' % (mid, c['name'], box))
            if box[1] < 0:
                fail('%s: 方体 %s miny=%r 违反贴地约束' % (mid, c['name'], box[1]))
            u, v = c['uv']
            if not (isinstance(u, int) and isinstance(v, int)) or u < 0 or v < 0:
                fail('%s: 方体 %s uv 非法 %r' % (mid, c['name'], c['uv']))
            w, h, d = box[3], box[4], box[5]
            if u + 2 * (w + d) > size + EPS:
                fail('%s: 方体 %s UV 区域宽越界 (u=%d 需宽 %.2f > %d)' % (mid, c['name'], u, 2 * (w + d), size))
            if v + (h + d) > size + EPS:
                fail('%s: 方体 %s UV 区域高越界 (v=%d 需高 %.2f > %d)' % (mid, c['name'], v, h + d, size))
            regions.append((c, (u, v, 2 * (w + d), h + d)))
    for i in range(len(regions)):
        for j in range(i + 1, len(regions)):
            ca, ra = regions[i]
            cb, rb = regions[j]
            shared = (ca['uv'] == cb['uv'] and ca['box'][3:] == cb['box'][3:]
                      and ca['paint'] is cb['paint'])
            if shared:
                continue
            if rects_overlap(ra, rb):
                fail('%s: UV 区域重叠：%s 与 %s' % (mid, ca['name'], cb['name']))
    clip = data['clips'].get('idle')
    if clip is None:
        fail('%s: clips 缺少 idle（GblModel 回退必需）' % mid)
    if not (isinstance(clip['seconds'], (int, float)) and clip['seconds'] > 0):
        fail('%s: idle.seconds 必须为正' % mid)
    if not clip['frames']:
        fail('%s: idle.frames 为空' % mid)
    for frame in clip['frames']:
        if len(frame) != len(bones):
            fail('%s: 帧行数 %d != 骨骼数 %d' % (mid, len(frame), len(bones)))
        for row in frame:
            if len(row) != 9:
                fail('%s: 帧行必须为 9 值 [tx,ty,tz,rx,ry,rz,sx,sy,sz]' % mid)
            if row[6] <= 0 or row[7] <= 0 or row[8] <= 0:
                fail('%s: 缩放必须为正' % mid)


# ---------------------------------------------------------------- 预览拼图

def build_sheet(records, cols=7, sheet_rows=2, cell=96, gap=6, margin=10):
    scale_for = lambda s: cell // s
    width = margin * 2 + cols * cell + (cols - 1) * gap
    height = margin * 2 + sheet_rows * cell + (sheet_rows - 1) * gap
    sheet = Canvas(width, height)
    rect(sheet, 0, 0, width, height, (36, 36, 44, 255))
    for index, rec in enumerate(records):
        cx = margin + (index % cols) * (cell + gap)
        cy = margin + (index // cols) * (cell + gap)
        for qy in range(0, cell, 8):
            for qx in range(0, cell, 8):
                tone = (58, 58, 68, 255) if ((qx // 8 + qy // 8) % 2 == 0) else (44, 44, 54, 255)
                rect(sheet, cx + qx, cy + qy, 8, 8, tone)
        tex = rec['cv']
        scale = scale_for(tex.w)
        for sy in range(tex.h):
            for sx in range(tex.w):
                color = tex.px[sy][sx]
                if color[3] == 0:
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        sheet.set(cx + sx * scale + dx, cy + sy * scale + dy,
                                  (color[0], color[1], color[2], 255))
        for bx, by, bw, bh in ((cx - 1, cy - 1, cell + 2, 1), (cx - 1, cy + cell, cell + 2, 1),
                               (cx - 1, cy, 1, cell), (cx + cell, cy, 1, cell)):
            rect(sheet, bx, by, bw, bh, (96, 96, 110, 255))
    path = os.path.join(PREVIEW_DIR, 'sheet.png')
    write_png(path, width, height, sheet.rows())
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
    if len(MODELS) != 14:
        fail('清单应为 14 种机关，当前 %d 种' % len(MODELS))
    ids = [m['id'] for m in MODELS]
    if len(set(ids)) != len(ids):
        fail('机关 id 重复')
    os.makedirs(DUNGEON_DIR, exist_ok=True)
    os.makedirs(TEXTURE_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    records = []
    total_cubes = 0
    try:
        for index, spec in enumerate(MODELS, 1):
            data = build_model_json(spec)
            cv = Canvas(spec['size'], spec['size'])
            for _, cubes in spec['bones']:
                for c in cubes:
                    w, h, d = c['box'][3], c['box'][4], c['box'][5]
                    faces = box_faces(c['uv'][0], c['uv'][1], w, h, d)
                    c['paint'](cv, faces, c)
            validate_model(spec, data, cv)
            json_path = os.path.join(DUNGEON_DIR, spec['id'] + '.json')
            png_path = os.path.join(TEXTURE_DIR, spec['id'] + '.png')
            text = json.dumps(data, ensure_ascii=False, separators=(',', ':'))
            json.loads(text)
            with open(json_path, 'w', encoding='utf-8', newline='\n') as fh:
                fh.write(text)
            write_png(png_path, spec['size'], spec['size'], cv.rows())
            # 落盘后复核：JSON 可解析、PNG 头与 IHDR 尺寸正确
            with open(json_path, 'rb') as fh:
                back = json.loads(fh.read().decode('utf-8'))
            if back['id'] != spec['id'] or 'idle' not in back['clips']:
                fail('%s: 落盘 JSON 复核失败' % spec['id'])
            with open(png_path, 'rb') as fh:
                blob = fh.read()
            if blob[:8] != b'\x89PNG\r\n\x1a\n':
                fail('%s: PNG 头非法' % spec['id'])
            ih = struct.unpack('>II', blob[16:24])
            if ih != (spec['size'], spec['size']):
                fail('%s: IHDR 尺寸 %r 与 texture_size 不符' % (spec['id'], ih))
            cubes_count = sum(len(cs) for _, cs in spec['bones'])
            total_cubes += cubes_count
            records.append({'id': spec['id'], 'cv': cv})
            print('[%2d/14] %-22s %-6s tex=%d 方体=%d' % (index, spec['id'], spec['name'], spec['size'], cubes_count))
            print('        %s' % rel(json_path))
            print('        %s' % rel(png_path))
        sheet_path = build_sheet(records)
        print('落位清单：dungeons/altar_mech/<id>.json 与 textures/entity/altar_mech/<id>.png 各 14 份')
        print('预览图（7x2）：%s' % rel(sheet_path))
        print('校验通过：json.loads 往返 OK；PNG 头/IHDR OK；UV 无越界、无重叠；miny>=0；'
              'clips 含单帧 idle。共 14 模型 / %d 方体。' % total_cubes)
    except AssetError as error:
        print('[FAIL] %s' % error)
        sys.exit(1)


if __name__ == '__main__':
    main()

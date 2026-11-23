"""Author the Goblin Kingdom siege map as a native Minecraft structure.

Run with Python 3. The template's minimum corner is (-104, -5, -20)
relative to the entrance road. Positive Z leads from the forest to the king.
"""
from __future__ import annotations

import argparse
import gzip
import math
from pathlib import Path
from random import Random

from build_tower_structures import (
    DIRECTIONS, Structure, canopy_mass, corridor, ellipse, leaf, polygon, rect,
    slab, speckle, stairs, state,
)
from build_world_decorations import nbt_payload


OUTPUT = (Path(__file__).resolve().parents[1]
          / "src/main/resources/data/yumis_dungeon_and_fight/structure/dungeons/goblin_kingdom/map.nbt")
BOTTOM = -5
TREE = state("dark_oak_log", axis="y")
OAK = state("oak_log", axis="y")
BEAM = state("stripped_dark_oak_log", axis="x")
JOIST = state("stripped_spruce_log", axis="z")
POST = state("stripped_dark_oak_log", axis="y")
FOUNDATION = ("cobblestone", "mossy_cobblestone", "andesite", "tuff")
PAVING = ("packed_mud", "coarse_dirt", "andesite", "mud_bricks")
TREE_FLOOR = ("grass_block", "podzol", "rooted_dirt", "coarse_dirt", "moss_block")
PALISADE_LEFT = ((-8, 38), (-21, 37), (-45, 43), (-58, 49), (-65, 64), (-69, 85),
                 (-63, 108), (-54, 116), (-32, 119), (-8, 112))
PALISADE_RIGHT = ((8, 38), (19, 37), (43, 39), (60, 51), (69, 68), (69, 88),
                  (64, 105), (55, 113), (32, 118), (8, 112))
ROYAL_WALL = ((-8, 112), (-24, 116), (-29, 145), (-27, 166), (-20, 179),
              (-5, 185), (13, 188), (31, 180), (34, 162), (29, 136), (24, 116), (8, 112))


def material(x, z, salt, palette):
    if speckle(x // 3, z // 3, salt) < 40:
        return palette[0]
    return palette[speckle(x, z, salt + 1) % len(palette)]


def make_terrain(scene):
    town = polygon(((-75, 55), (-28, 28), (26, 27), (76, 52), (78, 92),
                    (66, 122), (38, 132), (43, 181), (12, 196), (-33, 185),
                    (-36, 132), (-67, 122), (-77, 91)))
    court = polygon(((-16, 40), (16, 40), (20, 50), (18, 82), (7, 87),
                     (-8, 87), (-19, 79), (-18, 53)))
    forge = polygon(((-60, 52), (-49, 48), (-24, 49), (-19, 54), (-20, 99),
                     (-27, 105), (-55, 106), (-61, 97)))
    guards = polygon(((21, 53), (42, 48), (59, 52), (63, 70), (63, 97),
                      (55, 106), (26, 106), (20, 95), (19, 62)))
    terrace = polygon(((-14, 106), (13, 106), (24, 115), (30, 140),
                       (31, 179), (16, 189), (-16, 185), (-26, 173), (-26, 127)))

    forest_road = corridor(((0, -7), (0, 6), (-4, 17), (-4, 25), (0, 32), (0, 45)), 7)
    forge_road = corridor(((-9, 58), (-27, 58), (-37, 58), (-38, 64), (-38, 96)), 6)
    guards_road = corridor(((10, 71), (23, 71), (31, 72), (38, 78), (47, 87)), 6)
    royal_road = rect(-4, 80, 4, 122)
    approach = forest_road | rect(-4, 33, 4, 44)
    stair_faces = {}
    road_grade = {}
    for x, z in forge_road:
        road_grade[x, z] = int(x <= -19)
        if x == -19:
            stair_faces[x, z] = "west"
    for x, z in guards_road:
        road_grade[x, z] = int(x >= 17) + int(x >= 21)
        if x in (17, 21):
            stair_faces[x, z] = "east"
    inner_steps = (87, 91, 95, 99, 103, 107)
    for x, z in royal_road:
        road_grade[x, z] = sum(step <= z for step in inner_steps)
        if z in inner_steps:
            stair_faces[x, z] = "south"

    forge_pad = rect(-60, 57, -22, 101)
    guards_pad = rect(24, 52, 61, 104)
    royal_yard = rect(-18, 118, 17, 149)
    work_yards = rect(-55, 50, -27, 60) | rect(-55, 79, -24, 103) | rect(27, 68, 54, 92)
    paved = court | work_yards | forge_road | guards_road | royal_road | royal_yard
    heights = {}
    for x, z in sorted(scene.footprint):
        position = (x, z)
        rolling = 1.5 * math.sin(x / 17 + z / 23) + 1.2 * math.cos(z / 11 - x / 25)
        height = round(rolling + max(0, z - 80) * .037)
        if position in town:
            height = min(6, max(0, (z - 82) // 6))
        if position in forge or position in forge_pad:
            height = 1
        if position in guards or position in guards_pad:
            height = 2
        if position in court or position in approach:
            height = 0
        if position in terrace or position in royal_yard:
            height = 6
        height = road_grade.get(position, height)
        heights[position] = height
        for y in range(BOTTOM, height - 1):
            block = "stone" if y <= height - 4 else "dirt"
            scene.put(x, y, z, block)
        if position in stair_faces:
            surface = stairs("cobblestone_stairs", stair_faces[position])
        elif position in royal_yard:
            surface = material(x, z, 29, ("andesite", "cobblestone", "tuff", "stone_bricks"))
        elif position in paved:
            surface = material(x, z, 23, PAVING)
        elif position in approach:
            surface = material(x, z, 18, ("coarse_dirt", "rooted_dirt", "packed_mud"))
        else:
            surface = material(x, z, 17, TREE_FLOOR)
        scene.put(x, height - 1, z, surface)
    return heights, town, approach


def forest_tree(scene, x, z, floor, height, rng):
    trunk = TREE if rng.randrange(5) else OAK
    thick = height >= 17
    lean_x, lean_z = rng.choice(tuple(DIRECTIONS.values()))
    upper_x, upper_z = x + lean_x * 2, z + lean_z * 2
    for y in range(floor, floor + height + 1):
        shift = int(y > floor + height // 2)
        tx, tz = x + lean_x * shift, z + lean_z * shift
        scene.put(tx, y, tz, trunk)
        if thick and y < floor + height - 5:
            scene.put(tx + 1, y, tz, trunk)
            scene.put(tx, y, tz + 1, trunk)
    for dx, dz in ((-1, 0), (1, 0), (0, 1), (0, -1)):
        axis = "x" if dx else "z"
        root = state(trunk[0], axis=axis)
        scene.line((x, floor, z), (x + 2 * dx, floor - 1, z + 2 * dz), root)
    tip_y = floor + height
    scene.line((x, tip_y - 5, z), (upper_x, tip_y, upper_z), trunk)
    canopy_mass(scene, (upper_x, tip_y, upper_z), (5 + thick, 3 + thick, 5 + thick), height)
    for dx, dz, raise_by in ((1, -1, -3), (-1, 0, -2), (1, 1, -4)):
        bx, bz, by = x + 3 * dx, z + 3 * dz, tip_y + raise_by
        scene.line((x, by - 3, z), (bx, by, bz), state(trunk[0], axis="x"))
        canopy_mass(scene, (bx, by + 1, bz), (4, 3, 4), height + bx)


def woodland(scene, heights, town, approach):
    rng = Random(48612)
    for cell_x in range(-100, 105, 10):
        for cell_z in range(-16, 209, 10):
            x, z = cell_x + rng.randrange(-3, 4), cell_z + rng.randrange(-3, 4)
            if (x, z) not in heights or (x, z) in town or rng.random() < .13:
                continue
            if any((x + dx, z + dz) in approach for dx, dz in ((0, 0), (-4, 0), (4, 0), (0, 4), (0, -4))):
                continue
            forest_tree(scene, x, z, heights[x, z], rng.randrange(11, 23), rng)
    for x, z in sorted(scene.footprint - town - approach):
        y = heights[x, z]
        if (x, y, z) in scene.blocks:
            continue
        patch = speckle(x // 5, z // 5, 9)
        fine = speckle(x, z, 36)
        if patch < 42 and fine < 24:
            scene.put(x, y, z, "fern" if fine < 9 else "short_grass")
        elif patch < 25 and fine > 96:
            scene.put(x, y, z, "brown_mushroom")
        elif patch > 86 and fine < 9:
            scene.put(x, y, z, leaf("azalea_leaves"))


def palisade(scene, heights, points, *, stone_base=1):
    for x, z in sorted(corridor(points, 3)):
        floor = heights[x, z]
        base = material(x, z, 8, FOUNDATION)
        scene.box((x, floor - 1, z), (x, floor + stone_base - 1, z), base)
        upper = floor + 4 + speckle(x, z, 12) % 3
        upright = TREE if speckle(x, z, 25) < 62 else state("spruce_log", axis="y")
        scene.box((x, floor + stone_base, z), (x, upper - 1, z), upright)
        face = "south" if x % 2 else "north"
        scene.put(x, upper, z, stairs("dark_oak_stairs", face))


def floor_pad(scene, bounds, floor, surface):
    left, front, right, back = bounds
    scene.box((left, floor - 1, front), (right, floor - 1, back), surface)


def hall(scene, bounds, floor, wall_height, *, door_width=3, openings=("front",), roof="spruce"):
    left, front, right, back = bounds
    eaves = floor + wall_height
    middle = (left + right) // 2
    floor_pad(scene, bounds, floor, "spruce_planks")
    for x, z in rect(left, front, right, back) - rect(left + 1, front + 1, right - 1, back - 1):
        scene.put(x, floor - 1, z, material(x, z, 35, FOUNDATION))
        for y in range(floor, eaves):
            boards = material(x, z, y, ("spruce_planks", "dark_oak_planks", "spruce_planks"))
            scene.put(x, y, z, boards)
    for z in sorted(set(range(front, back + 1, 4)) | {back}):
        for x in (left, right):
            scene.box((x, floor, z), (x, eaves - 1, z), POST)
        scene.box((left, eaves - 1, z), (right, eaves - 1, z), BEAM)
    for z in (front, back):
        for x in (middle - door_width // 2 - 1, middle + door_width // 2 + 1):
            scene.box((x, floor, z), (x, eaves - 1, z), POST)
    for side in openings:
        z = front if side == "front" else back
        for x in range(middle - door_width // 2, middle + door_width // 2 + 1):
            for y in range(floor, eaves - 1):
                scene.blocks.pop((x, y, z), None)

    for x in range(left - 1, right + 2):
        rise = min(x - left + 1, right + 1 - x) // 2
        roof_y = eaves + rise
        for z in range(front - 1, back + 2):
            deck = f"{roof}_planks" if speckle(x, z, 42) < 83 else "dark_oak_planks"
            scene.put(x, roof_y, z, deck)
            neighbor_rise = (min(x - left + 2, right + 2 - x) // 2)
            if z in (front, back) and left <= x <= right:
                scene.box((x, eaves, z), (x, roof_y - 1, z), "stripped_spruce_wood")
            if x in (left - 1, right + 1) or abs(x - middle) <= 1:
                scene.put(x, roof_y + 1, z, slab(f"{roof}_slab"))
            elif neighbor_rise > rise and (x - left) % 2 == 0:
                scene.put(x, roof_y + 1, z, slab(f"{roof}_slab"))
    for x in (left, right):
        for z in range(front + 2, back - 1, 4):
            scene.put(x, floor + 2, z, "iron_bars")
            scene.put(x, floor + 3, z, "iron_bars")


def flag(scene, x, y, z, *, wide=False):
    if wide:
        for dx in (-1, 0, 1):
            for dy in range(4 - int(dx != 0)):
                cloth = "orange_terracotta" if dy == 1 and dx == 0 else "red_wool"
                scene.put(x + dx, y - dy, z, cloth)
        scene.box((x - 2, y + 1, z), (x + 2, y + 1, z), BEAM)
    else:
        scene.put(x, y, z, state("red_wall_banner", facing="north"))


def watchtower(scene, x, z, floor, deck):
    spacing = (-3, 3)
    for dx in spacing:
        for dz in spacing:
            scene.put(x + dx, floor - 1, z + dz, "mossy_cobblestone")
            scene.box((x + dx, floor, z + dz), (x + dx, deck + 3, z + dz), POST)
    scene.box((x - 4, deck - 1, z - 4), (x + 4, deck - 1, z + 4), "dark_oak_planks")
    for dx in range(-4, 5):
        for dz in (-4, 4):
            scene.put(x + dx, deck, z + dz, "dark_oak_fence")
    for dz in range(-3, 4):
        for dx in (-4, 4):
            scene.put(x + dx, deck, z + dz, "dark_oak_fence")
    for size, rise in ((4, 4), (3, 5), (2, 6), (1, 7)):
        scene.box((x - size, deck + rise, z - size), (x + size, deck + rise, z + size), "spruce_planks")
    scene.box((x, floor, z + 3), (x, deck - 1, z + 3), POST)
    for y in range(floor, deck):
        scene.put(x, y, z + 4, state("ladder", facing="south", waterlogged=False))
    scene.blocks.pop((x, deck, z + 4))
    scene.lamp(x, deck + 3, z, drop=1)


def gatehouse(scene, z, floor, *, main=False):
    depth = 6 if main else 4
    for side in (-1, 1):
        for dx in (4, 5, 6):
            for dz in range(depth + 1):
                x = side * dx
                scene.put(x, floor, z + dz, "mossy_cobblestone")
                scene.box((x, floor + 1, z + dz), (x, floor + 6, z + dz), POST if dx == 4 else TREE)
        scene.lamp(side * 5, floor + 4, z - 1, drop=1)
        scene.put(side * 5, floor + 5, z - 1, state("spruce_log", axis="z"))
        flag(scene, side * 5, floor + (8 if main else 6), z - 1, wide=main)
    for dz in range(depth + 1):
        scene.box((-6, floor + 6, z + dz), (6, floor + 7, z + dz), BEAM)
        for x in range(-6, 7):
            scene.put(x, floor + 8, z + dz, slab("dark_oak_slab"))
    if main:
        for x in (-5, 5):
            scene.put(x, floor + 8, z, POST)
            scene.put(x, floor + 9, z, JOIST)
    scene.box((-3, floor + 4, z + 1), (3, floor + 5, z + 1), "iron_bars")
    for x in (-3, 0, 3):
        scene.put(x, floor + 3, z + 1, "iron_bars")
    if main:
        watchtower(scene, -11, z + 4, floor, floor + 9)
        watchtower(scene, 11, z + 3, floor, floor + 7)


def bed(scene, x, y, z, facing, color):
    dx, dz = DIRECTIONS[facing]
    scene.put(x, y, z, state(f"{color}_bed", part="foot", facing=facing, occupied=False))
    scene.put(x + dx, y, z + dz, state(f"{color}_bed", part="head", facing=facing, occupied=False))


def stack(scene, x, floor, z, width, layers, length, wood="oak"):
    for row in range(layers):
        scene.box((x + row, floor + row, z), (x + width - 1, floor + row, z + length - 1),
                  state(f"{wood}_log", axis="z"))


def lantern_post(scene, x, floor, z, *, wood="dark_oak"):
    scene.put(x, floor, z, "mossy_cobblestone")
    scene.box((x, floor + 1, z), (x, floor + 3, z), state(f"stripped_{wood}_log", axis="y"))
    scene.lamp(x, floor + 4, z)


def smithy(scene):
    floor = 1
    hall(scene, (-51, 61, -26, 79), floor, 7, door_width=7, openings=("front", "rear"))
    # The wagon lane runs through both ends of the shed.
    floor_pad(scene, (-41, 60, -35, 80), floor, "andesite")
    for x in (-41, -35):
        for z in range(62, 79):
            scene.put(x, floor - 1, z, "polished_andesite" if z % 3 else "tuff")
    for z in range(65, 74, 4):
        scene.barrels(-49, floor, z, "east")
        scene.put(-48, floor, z, "raw_iron_block")
    scene.box((-47, floor, 67), (-44, floor + 2, 71), "polished_blackstone_bricks")
    for z in (68, 70):
        scene.put(-44, floor + 1, z, state("blast_furnace", facing="east", lit=True))
        scene.put(-45, floor + 3, z, "magma_block")
    scene.box((-46, floor + 3, 68), (-45, floor + 14, 70), "deepslate_bricks")
    scene.box((-46, floor + 14, 68), (-45, floor + 14, 70), "deepslate_tiles")
    scene.put(-43, floor, 72, state("anvil", facing="north"))
    scene.put(-43, floor, 67, "water_cauldron")
    scene.put(-49, floor, 75, "smithing_table")
    for z in (65, 73, 77):
        scene.lamp(-45, floor + 5, z, drop=1)

    # A fixed winding drum handles loads between the ore bins and the lane.
    scene.box((-33, floor, 66), (-30, floor, 72), "polished_deepslate")
    scene.box((-33, floor + 1, 67), (-30, floor + 2, 71), "waxed_exposed_cut_copper")
    scene.box((-32, floor + 3, 68), (-31, floor + 3, 70), "waxed_cut_copper")
    for z in (67, 71):
        scene.put(-34, floor + 1, z, "polished_blackstone")
        scene.put(-34, floor + 2, z, "waxed_copper_block")
        scene.put(-29, floor + 2, z, "polished_blackstone")
    scene.box((-32, floor, 64), (-32, floor + 6, 64), POST)
    scene.box((-32, floor, 74), (-32, floor + 6, 74), POST)
    scene.box((-32, floor + 6, 64), (-32, floor + 6, 74), JOIST)
    for y in range(floor + 3, floor + 6):
        scene.put(-32, y, 68, state("iron_chain", axis="y", waterlogged=False))
    for z in (64, 75):
        scene.barrels(-28, floor, z, "west")
        scene.box((-28, floor + 6, z), (-26, floor + 6, z), BEAM)
        scene.lamp(-28, floor + 5, z, drop=1)

    # The wood store is beside the unloading yard so the through lane stays clear.
    for x in (-56, -48):
        for z in (51, 57):
            scene.box((x, floor, z), (x, floor + 4, z), POST)
    scene.box((-57, floor + 4, 50), (-47, floor + 4, 58), "dark_oak_planks")
    scene.box((-57, floor + 5, 50), (-47, floor + 5, 58), slab("spruce_slab"))
    stack(scene, -55, floor, 52, 4, 3, 3)
    stack(scene, -51, floor, 54, 3, 2, 3, wood="spruce")
    scene.put(-49, floor, 52, "crafting_table")
    scene.lamp(-49, floor + 3, 52, drop=1)
    for x in (-46, -42):
        for z in (53, 57):
            scene.box((x, floor, z), (x, floor + 1, z), "polished_blackstone")
    for z in (53, 57):
        scene.box((-45, floor + 1, z), (-43, floor + 1, z), BEAM)
    scene.box((-45, floor + 1, 53), (-43, floor + 1, 57), "dark_oak_planks")
    stack(scene, -45, floor + 2, 54, 3, 2, 3)
    for x in (-45, -43):
        scene.box((x, floor + 1, 51), (x, floor + 1, 52), JOIST)

    hall(scene, (-60, 63, -53, 79), floor, 4, roof="dark_oak")
    for z in (65, 68, 71, 74):
        bed(scene, -58, floor, z, "west", "brown")
        bed(scene, -55, floor, z, "east", "brown")
    for x in (-59, -54):
        scene.barrels(x, floor, 77, "north")
    scene.lamp(-58, floor + 2, 75, drop=1)

    # Trial equipment sits on a stone bed at the side of the open exercise yard.
    floor_pad(scene, (-52, 85, -45, 91), floor, "tuff")
    for x in (-52, -46):
        for z in (86, 90):
            scene.put(x, floor, z, "polished_blackstone")
            scene.put(x, floor + 1, z, "polished_blackstone")
            scene.put(x, floor + 1, z + (1 if z == 86 else -1), "waxed_copper_block")
    scene.box((-51, floor + 1, 86), (-47, floor + 1, 90), "dark_oak_planks")
    scene.box((-50, floor + 2, 87), (-48, floor + 3, 90), "waxed_exposed_copper")
    scene.box((-49, floor + 3, 90), (-49, floor + 3, 93), "deepslate_tiles")
    for z in range(88, 101):
        scene.put(-45, floor - 1, z, "chiseled_tuff_bricks")
    for z in (95, 98, 101):
        scene.put(-51, floor, z, state("hay_block", axis="x"))
        scene.put(-49, floor, z, state("hay_block", axis="z"))
    for x in range(-55, -43):
        for z in range(102, 105):
            height = 2 if z == 103 else 1
            scene.box((x, floor, z), (x, floor + height, z), material(x, z, 45, FOUNDATION))
    for x, z in ((-43, 80), (-26, 83), (-44, 98), (-25, 97)):
        lantern_post(scene, x, floor, z)
    scene.barrels(-29, floor, 51)
    scene.barrels(-27, floor, 50)


def bunkhouse(scene, bounds):
    floor = 2
    left, front, right, back = bounds
    hall(scene, bounds, floor, 4, openings=("rear",))
    for z in (front + 2, front + 5, front + 8):
        bed(scene, left + 2, floor, z, "west", "gray")
        bed(scene, right - 2, floor, z, "east", "red")
    for x in (left + 1, right - 1):
        scene.barrels(x, floor, back - 2, "south")
    scene.lamp(left + 2, floor + 2, front + 4, drop=1)
    scene.lamp(right - 2, floor + 2, front + 8, drop=1)


def cookhouse(scene):
    floor = 2
    hall(scene, (26, 93, 40, 104), floor, 5, door_width=5)
    for x in (27, 28, 29):
        scene.barrels(x, floor, 95, "south")
    scene.put(27, floor, 97, state("smoker", facing="east", lit=True))
    scene.put(27, floor, 98, "water_cauldron")
    scene.put(28, floor, 99, "crafting_table")
    scene.box((31, floor, 96), (35, floor, 96), "spruce_planks")
    scene.box((31, floor, 95), (35, floor, 95), slab("spruce_slab"))
    scene.box((31, floor, 97), (35, floor, 97), slab("spruce_slab"))
    for x in range(29, 38):
        if x != 34:
            scene.put(x, floor, 100, "spruce_planks")
            scene.put(x, floor + 1, 100, "spruce_planks")
    for x in (28, 30, 36, 38):
        bed(scene, x, floor, 102, "south", "brown")
    scene.lamp(34, floor + 3, 97, drop=1)
    scene.lamp(33, floor + 3, 101, drop=1)


def herb_yard(scene):
    floor = 2
    for x, z in rect(44, 93, 55, 103) - rect(45, 94, 54, 102) - rect(44, 96, 44, 98):
        scene.put(x, floor, z, "spruce_fence")
    for x in (44, 55):
        for z in (93, 103):
            scene.put(x, floor, z, POST)
    for x in (47, 51):
        for z in range(95, 101):
            scene.put(x, floor - 1, z, "podzol")
            flower = ("fern", "allium", "blue_orchid", "brown_mushroom")[speckle(x, z, 9) % 4]
            scene.put(x, floor, z, flower)
    for x in (46, 48, 50, 52):
        scene.box((x, floor, 102), (x, floor + 2, 102), POST)
    scene.box((46, floor + 2, 102), (52, floor + 2, 102), BEAM)
    scene.box((46, floor + 3, 101), (52, floor + 3, 103), slab("spruce_slab", "top"))
    scene.put(53, floor, 99, "water_cauldron")
    scene.barrels(54, floor, 102, "north")
    scene.lamp(49, floor + 1, 102, drop=1)


def branch_lookout(scene):
    floor = 2
    deck = 7
    forest_tree(scene, 56, 84, floor, 15, Random(4109))
    scene.box((51, deck - 1, 81), (60, deck - 1, 87), "spruce_planks")
    scene.put(56, deck - 1, 84, TREE)
    for x, z in ((52, 82), (52, 86), (59, 86)):
        scene.put(x, floor - 1, z, "mossy_cobblestone")
        scene.box((x, floor, z), (x, deck - 1, z), POST)
    for x, z in rect(51, 81, 60, 87) - rect(52, 82, 59, 86) - rect(57, 81, 59, 81):
        scene.put(x, deck, z, "dark_oak_fence")
    for z in range(71, 81):
        rise = (z - 71) // 2
        level = floor + rise
        climbing = z % 2 == 0
        for x in range(57, 60):
            surface = stairs("spruce_stairs", "south") if climbing else "spruce_planks"
            scene.put(x, level if climbing else level - 1, z, surface)
        for x in (56, 60):
            scene.put(x, level if climbing else level - 1, z, "spruce_planks")
            scene.put(x, level + int(climbing), z, "spruce_fence")
            if z % 3 == 2:
                scene.box((x, floor, z), (x, level - 1, z), POST)
    scene.barrels(52, deck, 86, "east")
    scene.lamp(59, deck, 86)
    scene.lamp(52, deck, 82)


def near_guards(scene):
    bunkhouse(scene, (25, 54, 37, 66))
    bunkhouse(scene, (41, 54, 53, 66))
    for x in (31, 47):
        floor_pad(scene, (x - 2, 67, x + 2, 70), 2, "andesite")
        scene.barrels(x - 3, 2, 67, "south")
    for x in (29, 37, 45, 51):
        scene.put(x, 2, 90, "spruce_fence")
        scene.put(x, 3, 90, state("hay_block", axis="x"))
        scene.put(x, 4, 90, "target")
    for x in range(29, 53):
        if x % 3 != 0:
            scene.put(x, 1, 74, "smooth_stone")
    cookhouse(scene)
    herb_yard(scene)
    branch_lookout(scene)
    for x, z in ((25, 73), (52, 69), (38, 88), (42, 93)):
        lantern_post(scene, x, 2, z, wood="spruce")


def lead_road(scene, heights):
    for z in (11, 25, 48, 67, 83):
        for x in (-7, 7) if z >= 48 else (4 if z != 25 else 3,):
            lantern_post(scene, x, heights[x, z], z)
    for side in (-1, 1):
        for z in (91, 99, 107):
            x = side * 5
            rise = {91: 2, 99: 4, 107: 6}[z]
            scene.box((x, min(heights[x, z] - 1, rise - 1), z), (x, rise - 1, z), "mossy_cobblestone")
            if z != 107:
                lantern_post(scene, x, rise, z)
        for z in range(87, 109):
            rise = sum(step <= z for step in (87, 91, 95, 99, 103, 107))
            x = side * 6
            scene.box((x, min(heights[x, z] - 1, rise - 1), z), (x, rise - 1, z),
                      material(x, z, 11, FOUNDATION))
            if z not in (90, 94, 98, 102, 106):
                scene.put(x, rise, z, slab("cobblestone_slab"))

    for x, z in ((-12, 48), (11, 49)):
        floor = heights[x, z]
        scene.box((x - 2, floor, z), (x + 2, floor, z), slab("spruce_slab"))
        scene.barrels(x - 2, floor + 1, z, "south")
    for x, z in ((-14, 68), (14, 62)):
        floor = heights[x, z]
        scene.put(x, floor, z, "water_cauldron")
        scene.barrels(x + 1, floor, z)
    stack(scene, -11, heights[-11, 74], 74, 3, 2, 2)


def old_tree(scene, heights):
    x, z = 10, 181
    base = heights[x, z]
    for y in range(base, 34):
        rise = y - base
        radius = max(2.1, 4.8 - rise * .115)
        offset = min(2.3, rise * .085)
        core = ellipse(x + offset, z + offset * .4, radius, radius - .25)
        for tx, tz in core:
            trunk = "dark_oak_wood" if speckle(tx, tz, y) < 16 else "dark_oak_log"
            scene.put(tx, y, tz, state(trunk, axis="y"))
    for end_x, end_z in ((-12, 178), (24, 173), (26, 183), (0, 190), (19, 196)):
        end_y = heights[end_x, end_z] - 1
        elbow_x, elbow_z = (x + end_x) // 2, (z + end_z) // 2
        scene.line((x, base + 4, z), (elbow_x, base + 1, elbow_z), state("dark_oak_log", axis="x"), girth=1)
        scene.line((elbow_x, base + 1, elbow_z), (end_x, end_y, end_z), state("dark_oak_log", axis="x"))
    for tip, crown in (((-1, 35, 176), (8, 4, 7)), ((18, 37, 172), (8, 5, 7)),
                       ((26, 33, 183), (8, 4, 7)), ((11, 41, 183), (9, 5, 8)),
                       ((1, 34, 189), (8, 4, 7)), ((17, 32, 194), (7, 4, 7))):
        scene.line((x + 1, tip[1] - 8, z), tip, state("dark_oak_log", axis="x"), girth=1)
        canopy_mass(scene, tip, crown, tip[1] + tip[0])


def royal_hall(scene, heights):
    old_tree(scene, heights)
    hall(scene, (-12, 155, 12, 174), 8, 7, door_width=7, roof="dark_oak")
    scene.box((-13, 6, 154), (13, 7, 175), "stone_bricks")
    floor_pad(scene, (-11, 155, 11, 174), 8, "dark_oak_planks")
    floor_pad(scene, (-8, 152, 8, 152), 7, "stone_bricks")
    floor_pad(scene, (-8, 154, 8, 154), 8, "stone_bricks")
    for x in range(-8, 9):
        scene.put(x, 6, 151, stairs("stone_brick_stairs", "south"))
        scene.put(x, 7, 153, stairs("stone_brick_stairs", "south"))
    for x in (-9, 9):
        scene.box((x, 6, 151), (x, 7, 154), "chiseled_stone_bricks")
        scene.box((x, 8, 152), (x, 12, 152), POST)
        scene.box((x, 12, 152), (x, 12, 155), JOIST)
        scene.lamp(x, 11, 152, drop=1)
        flag(scene, x, 14, 154, wide=True)
    scene.box((-10, 15, 154), (10, 15, 154), BEAM)
    scene.box((-4, 8, 167), (4, 8, 171), "polished_blackstone_bricks")
    for x in range(-3, 4):
        scene.put(x, 8, 166, stairs("polished_blackstone_brick_stairs", "south"))
    scene.box((-1, 9, 169), (1, 10, 170), "dark_oak_planks")
    scene.box((-1, 11, 170), (1, 11, 170), "red_terracotta")
    scene.put(0, 10, 169, "red_carpet")
    for x in range(-1, 2):
        for z in range(157, 166):
            scene.put(x, 8, z, "red_carpet")
    for x in (-8, 8):
        scene.box((x - 1, 8, 160), (x + 1, 8, 161), "spruce_planks")
        scene.box((x - 1, 8, 159), (x + 1, 8, 159), slab("spruce_slab"))
        scene.lamp(x, 13, 159, drop=1)
        scene.lamp(x, 13, 167, drop=1)
    scene.box((6, 8, 165), (6, 10, 173), "dark_oak_planks")
    for y in (8, 9, 10):
        scene.blocks.pop((6, y, 166), None)
    bed(scene, 9, 8, 171, "south", "red")
    scene.barrels(10, 8, 172, "west")
    scene.put(9, 8, 170, "bookshelf")

    # Covered flank entries give the guard a route between hall and rank lines.
    for side in (-1, 1):
        near, far = sorted((side * 19, side * 24))
        floor_pad(scene, (near, 144, far, 152), 6, "andesite")
        for x in (near, far):
            for z in (145, 152):
                scene.box((x, 6, z), (x, 10, z), POST)
        scene.box((near - 1, 10, 144), (far + 1, 10, 153), "spruce_planks")
        scene.box((near - 1, 11, 144), (far + 1, 11, 153), slab("dark_oak_slab"))
        scene.lamp(side * 22, 9, 147, drop=1)
        scene.barrels(side * 23, 6, 150, "north")
    for x, z in ((-20, 124), (20, 124), (-21, 138), (21, 138)):
        lantern_post(scene, x, heights[x, z], z)
    for x in (-14, 14):
        for z in (122, 143):
            scene.put(x, 5, z, "chiseled_tuff")
    # Rank markings are inlaid into the floor to keep the fighting area open.
    for x in list(range(-14, -6)) + list(range(7, 15)):
        for z in (130, 137, 143):
            scene.put(x, 5, z, "polished_andesite")


def orient_to_entry(scene):
    # Facing south from the entry, the player's left is east.
    flip = {"east": "west", "west": "east"}
    mirrored = {}
    for (x, y, z), block in scene.blocks.items():
        name, values = block
        properties = dict(values)
        facing = properties.get("facing")
        if facing in flip:
            properties["facing"] = flip[facing]
            block = state(name, **properties)
        mirrored[-x, y, z] = block
    scene.blocks = mirrored
    scene.footprint = {(-x, z) for x, z in scene.footprint}


def join_rails(scene):
    rails = {"spruce_fence", "dark_oak_fence", "iron_bars"}
    solid_ends = {"mossy_cobblestone", "stone_bricks", "deepslate_bricks"}
    for (x, y, z), (name, _) in scene.blocks.items():
        if name not in rails:
            continue
        connections = {}
        for facing, (dx, dz) in DIRECTIONS.items():
            neighbor = scene.blocks.get((x + dx, y, z), ("air", ()))[0]
            same_kind = neighbor == "iron_bars" if name == "iron_bars" else neighbor.endswith("_fence")
            connections[facing] = same_kind or neighbor in solid_ends or neighbor.endswith(("_planks", "_log"))
        scene.put(x, y, z, state(name, **connections, waterlogged=False))


def build():
    footprint = polygon(((-105, -10), (-92, -20), (-55, -20), (-24, -16), (8, -20),
                         (70, -20), (94, -13), (105, 11), (105, 105), (99, 141),
                         (105, 174), (88, 204), (49, 210), (-16, 210), (-45, 202),
                         (-87, 207), (-104, 187), (-105, 139), (-99, 106),
                         (-105, 70), (-103, 30)))
    scene = Structure(footprint)
    heights, town, approach = make_terrain(scene)
    woodland(scene, heights, town, approach)
    for wall in (PALISADE_LEFT, PALISADE_RIGHT):
        palisade(scene, heights, wall)
    palisade(scene, heights, ROYAL_WALL, stone_base=2)
    gatehouse(scene, 35, 0, main=True)
    gatehouse(scene, 110, 6)
    lead_road(scene, heights)
    smithy(scene)
    near_guards(scene)
    royal_hall(scene, heights)
    orient_to_entry(scene)
    join_rails(scene)
    return scene


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    output = parser.parse_args().output
    template = build().template()
    _, payload = nbt_payload(template)
    compressed = gzip.compress(b"\x0a\x00\x00" + payload, mtime=0)
    output.parent.mkdir(parents=True, exist_ok=True)
    if not output.exists() or output.read_bytes() != compressed:
        output.write_bytes(compressed)
    size = " x ".join(str(side) for side in template["size"])
    print(f"{output.name}: {size}, {len(template['blocks']):,} blocks")


if __name__ == "__main__":
    main()

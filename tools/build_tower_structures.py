"""Build the eight tower gates and single-floor arenas as native structure NBT.

The southern edge is the entrance side. Each arena uses the same 100-block
circle, but its floor plan, circulation, perimeter and overhead work are authored
separately. Run this script with Python 3, optionally passing --output.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from functools import cache
import gzip
import math
from pathlib import Path
from random import Random

from build_world_decorations import nbt_payload


OUTPUT = (Path(__file__).resolve().parents[1]
          / "src/main/resources/data/yumis_dungeon_and_fight/structure/towers")
DATA_VERSION = 4903
GROUND = 3
DIAMETER = 100
CENTER = 50
Point = tuple[int, int]
Position = tuple[int, int, int]
State = tuple[str, tuple[tuple[str, str], ...]]


@cache
def state(block: str, **properties: str | bool | int) -> State:
    return block, tuple(sorted((key, str(value).lower()) for key, value in properties.items()))


AIR = state("air")
DIRECTIONS = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def stairs(block: str, facing: str, half: str = "bottom") -> State:
    return state(block, facing=facing, half=half, shape="straight", waterlogged=False)


def slab(block: str, half: str = "bottom") -> State:
    return state(block, type=half, waterlogged=False)


def leaf(block: str = "dark_oak_leaves") -> State:
    return state(block, distance=7, persistent=True, waterlogged=False)


def leaves(x: int, z: int, scale: int) -> State:
    return leaf("azalea_leaves" if (x + 2*z) % scale == 0 else "dark_oak_leaves")


def rect(x1: int, z1: int, x2: int, z2: int) -> set[Point]:
    return {(x, z) for x in range(x1, x2 + 1) for z in range(z1, z2 + 1)}


def ellipse(cx: float, cz: float, rx: float, rz: float) -> set[Point]:
    return {
        (x, z)
        for z in range(math.floor(cz - rz), math.ceil(cz + rz))
        for x in range(math.floor(cx - rx), math.ceil(cx + rx))
        if ((x + .5 - cx) / rx)**2 + ((z + .5 - cz) / rz)**2 <= 1
    }


ARENA = ellipse(CENTER, CENTER, 50, 50)


def polygon(vertices: tuple[Point, ...]) -> set[Point]:
    edges = tuple(zip(vertices, vertices[1:] + vertices[:1]))
    return {
        (x, z)
        for x in range(min(p[0] for p in vertices), max(p[0] for p in vertices))
        for z in range(min(p[1] for p in vertices), max(p[1] for p in vertices))
        if sum(
            ((az > z + .5) != (bz > z + .5))
            and (x + .5 < ax + (z + .5 - az) * (bx - ax) / (bz - az))
            for (ax, az), (bx, bz) in edges
        ) % 2
    }


def corridor(points: tuple[Point, ...], width: int) -> set[Point]:
    cells = set()
    radius = width / 2
    for (ax, az), (bx, bz) in zip(points, points[1:]):
        dx, dz = bx - ax, bz - az
        length = dx*dx + dz*dz
        for x in range(min(ax, bx) - width, max(ax, bx) + width):
            for z in range(min(az, bz) - width, max(az, bz) + width):
                px, pz = x + .5 - ax, z + .5 - az
                fraction = max(0, min(1, (px*dx + pz*dz) / length))
                if (px - fraction*dx)**2 + (pz - fraction*dz)**2 <= radius**2:
                    cells.add((x, z))
    return cells


def edge(area: set[Point]) -> set[Point]:
    return {
        (x, z) for x, z in area
        if any((x + dx, z + dz) not in area for dx, dz in DIRECTIONS.values())
    }


class Structure:
    def __init__(self, footprint: set[Point] | None = None):
        self.footprint = footprint
        self.blocks: dict[Position, State] = {}

    def put(self, x: int, y: int, z: int, block: State | str):
        if self.footprint is None or (x, z) in self.footprint:
            self.blocks[x, y, z] = state(block) if isinstance(block, str) else block

    def box(self, first: Position, last: Position, block: State | str):
        x1, y1, z1 = first
        x2, y2, z2 = last
        for y in range(y1, y2 + 1):
            for z in range(z1, z2 + 1):
                for x in range(x1, x2 + 1):
                    self.put(x, y, z, block)

    def layer(self, cells: set[Point], y: int, block: State | str):
        for x, z in cells:
            self.put(x, y, z, block)

    def line(self, first: Position, last: Position, block: State | str, girth: int = 0):
        delta = tuple(end - start for start, end in zip(first, last))
        length = max(1, *(abs(component) for component in delta))
        previous = first
        for index in range(length + 1):
            point = tuple(round(start + change*index/length) for start, change in zip(first, delta))
            lower = tuple(min(a, b) - girth for a, b in zip(previous, point))
            upper = tuple(max(a, b) + girth for a, b in zip(previous, point))
            self.box(lower, upper, block)
            previous = point

    def lamp(self, x: int, y: int, z: int, *, drop: int = 0, soul: bool = False):
        name = "soul_lantern" if soul else "lantern"
        for offset in range(drop):
            self.put(x, y - offset, z, state("iron_chain", axis="y", waterlogged=False))
        self.put(x, y - drop, z, state(name, hanging=drop > 0, waterlogged=False))

    def barrels(self, x: int, y: int, z: int, facing: str = "south"):
        self.put(x, y, z, state("barrel", facing=facing, open=False))

    def template(self) -> dict:
        minimum = tuple(min(pos[i] for pos in self.blocks) for i in range(3))
        maximum = tuple(max(pos[i] for pos in self.blocks) for i in range(3))
        palette = []
        index = {}
        blocks = []
        for position, block in sorted(self.blocks.items(), key=placement_order):
            if block not in index:
                name, values = block
                index[block] = len(palette)
                entry = {"Name": f"minecraft:{name}"}
                if values:
                    entry["Properties"] = dict(values)
                palette.append(entry)
            blocks.append({
                "pos": [value - low for value, low in zip(position, minimum)],
                "state": index[block],
            })
        return {
            "DataVersion": DATA_VERSION,
            "author": "YumisFight",
            "size": [high - low + 1 for low, high in zip(minimum, maximum)],
            "palette": palette,
            "blocks": blocks,
            "entities": [],
        }


def placement_order(item: tuple[Position, State]):
    (x, y, z), (name, _) = item
    if name == "air":
        phase = 0
    elif name in {"water", "lava"}:
        phase = 3
    elif name in {"lantern", "soul_lantern", "iron_chain"}:
        phase = 2
    else:
        phase = 1
    return phase, -y if phase == 2 else y, z, x


def speckle(x: int, z: int, salt: int) -> int:
    return (x*15731 ^ z*789221 ^ salt*1376312589) % 101


def textured(scene: Structure, cells: set[Point], y: int, palette: tuple[str, ...], salt: int = 0):
    for x, z in cells:
        macro = speckle(x//4, z//4, salt)
        fine = speckle(x, z, salt + 1)
        index = 0 if macro < 43 or fine < 40 else (macro + fine) % len(palette)
        scene.put(x, y, z, palette[index])


def terrain_height(x: int, z: int) -> float:
    return math.hypot(x + .5 - CENTER, z + .5 - CENTER)


def rim(scene: Structure, opening: set[Point], palette: tuple[str, ...],
        *, inner: int, height: int, variation: int, cap: str):
    for x, z in ARENA - opening:
        r = terrain_height(x, z)
        if r < inner:
            continue
        theta = math.atan2(z + .5 - CENTER, x + .5 - CENTER)
        wave = .55 + .23*math.sin(7*theta) + .2*math.cos(11*theta + .4)
        high = GROUND + height + round(variation*wave)
        for y in range(GROUND + 1, high + 1):
            scene.put(x, y, z, palette[speckle(x + y, z - y, 4) % len(palette)])
        if r > inner + 1:
            scene.put(x, high + 1, z, slab(cap))


def pier(scene: Structure, x: int, z: int, y: int, height: int,
         base: str, shaft: State | str, head: str):
    scene.box((x - 1, y, z - 1), (x + 1, y, z + 1), base)
    scene.box((x, y + 1, z), (x, y + height - 2, z), shaft)
    scene.box((x - 1, y + height - 1, z - 1), (x + 1, y + height - 1, z + 1), head)


def bracket(scene: Structure, x: int, y: int, z: int, block: str, bearing: str):
    scene.put(x, y, z, stairs(block, bearing, "top"))


def rail(scene: Structure, cells: set[Point], y: int, block: str):
    for x, z in cells:
        connections = {name: (x + dx, z + dz) in cells
                       for name, (dx, dz) in DIRECTIONS.items()}
        scene.put(x, y, z, state(block, **connections, waterlogged=False))


def trough(scene: Structure, x: int, y: int, z: int, width: int, length: int, block: str):
    base = rect(x, z, x + width - 1, z + length - 1)
    lip = edge(base)
    scene.layer(base, y, block)
    scene.layer(lip, y + 1, block)
    scene.layer(base - lip, y + 1, state("water", level=0))


def decking(scene: Structure, x: int, z: int, width: int, length: int, rise: int,
            boards: str, stair_block: str, approach: str):
    pad = rect(x, z, x + width - 1, z + length - 1)
    for layer in range(1, rise + 1):
        scene.layer(pad, GROUND + layer, boards)
    # Every level has a wide tread, running out from the pad on its approach side.
    dx, dz = DIRECTIONS[approach]
    face = {"north": "south", "east": "west", "south": "north", "west": "east"}[approach]
    if dx:
        shoulder = {(x if dx < 0 else x + width - 1, pz) for pz in range(z + 1, z + length - 1)}
    else:
        shoulder = {(px, z if dz < 0 else z + length - 1) for px in range(x + 1, x + width - 1)}
    for offset in range(1, rise + 1):
        step = {(px + dx*offset, pz + dz*offset) for px, pz in shoulder}
        for layer in range(1, rise - offset + 1):
            scene.layer(step, GROUND + layer, boards)
        scene.layer(step, GROUND + rise - offset + 1, stairs(stair_block, face))


@dataclass(frozen=True)
class GatePlan:
    depth: int
    opening: int
    rows: tuple[str, ...]
    palette: dict[str, State | str]
    ground: tuple[str, ...]

    @property
    def width(self) -> int:
        return len(self.rows[0])


def gate_body(plan: GatePlan) -> Structure:
    scene = Structure()
    depth, width, height = plan.depth, plan.width, len(plan.rows)
    left = (width - plan.opening) // 2
    middle = width // 2
    pad = rect(0, 0, width - 1, depth - 1)
    forecourt = rect(middle - 3, depth, middle + 3, depth + 4)
    apron = polygon(((-5, 0), (width + 5, 0), (width + 5, depth + 2),
                     (middle + 4, depth + 5), (middle - 3, depth + 5), (-5, depth + 3)))
    for y in range(1, GROUND):
        scene.layer(apron | pad | forecourt, y, plan.ground[0])
    textured(scene, apron | pad | forecourt, GROUND, plan.ground, 27)
    footing = plan.palette["r"]
    for x, z in edge(apron) - forecourt:
        scene.put(x, GROUND - 1, z, footing)
    for offset, row in enumerate(plan.rows):
        y = GROUND + height - offset
        for x, code in enumerate(row):
            if code == ".":
                continue
            block = plan.palette[code]
            if code == "u":
                scene.put(x, y, depth - 1, block)
            elif code in {"e", "k", "l", "m"}:
                scene.box((x, y, depth - 2), (x, y, depth - 1), block)
                if code == "l":
                    scene.put(x, y, depth - 3, block)
            else:
                scene.box((x, y, 0), (x, y, depth - 1), block)
    scene.box((left, GROUND + 1, 0), (left + plan.opening - 1, GROUND + 4, depth - 1), AIR)
    for x, z in forecourt:
        scene.box((x, GROUND + 1, z), (x, GROUND + 3, z), AIR)
    return scene


ROOT_GATE = GatePlan(
    5, 3,
    ("...eee...", ".eeeetee.", "eetttttee", ".ttttttt.",
     ".ut...tu.", ".tt...tt.", "stt...tts", "sst...tss"),
    {"s": "mossy_stone_bricks", "r": "mossy_cobblestone",
     "t": state("dark_oak_log", axis="y"), "e": leaf(),
     "u": state("lantern", hanging=True, waterlogged=False)},
    ("coarse_dirt", "rooted_dirt", "moss_block", "cobbled_deepslate", "andesite"),
)
FORGE_GATE = GatePlan(
    6, 3,
    ("........ss.", "........rs.", ".rrrrrrrrss", ".ssmmmmmsrs", "rssssmsssss",
     "sssu...usss", "ssms...smss", "ssss...ssss", "rsss...sssr"),
    {"s": "polished_blackstone_bricks", "r": "smooth_basalt", "m": "waxed_cut_copper",
     "u": state("lantern", hanging=True, waterlogged=False)},
    ("polished_blackstone", "basalt", "blackstone", "cracked_polished_blackstone_bricks"),
)
SLUICE_GATE = GatePlan(
    5, 5,
    (".t.......t.", ".ttttttttt.", "sts..m..sts", "ssstttttsss",
     "ssu.....uss", "sss.....sss", "rss.....ssr", "sss.....sss"),
    {"s": "stone_bricks", "r": "prismarine_bricks", "m": "polished_andesite",
     "t": state("spruce_log", axis="x"), "u": "sea_lantern"},
    ("stone_bricks", "andesite", "prismarine_bricks", "mossy_stone_bricks"),
)
MARKET_GATE = GatePlan(
    4, 3,
    ("..ss.....", ".sss.ss..", ".sssssss.", "sssmmmsst",
     "sts...ss.", "sss...ss.", ".ss...sss", "sss...sss"),
    {"s": "cut_sandstone", "r": "sandstone", "m": "chiseled_sandstone",
     "t": state("spruce_log", axis="x")},
    ("sandstone", "smooth_sandstone", "cut_sandstone", "sand"),
)
FROST_GATE = GatePlan(
    5, 3,
    (".....l.", ".l...ll", ".ll.lsl", "lslslss", "sssssss",
     "ls...sl", "ls...sl", "ss...ss", "ss...ss"),
    {"s": "calcite", "r": "tuff", "l": "blue_ice"},
    ("smooth_stone", "calcite", "andesite", "polished_andesite"),
)
ORRERY_GATE = GatePlan(
    6, 5,
    (".....m.....", "....mmm....", "..mmlllmm..", ".mslllllsm.", "msmlllllmsm",
     "ssstttttsss", "ssu.....uss", "sss.....sss", "rss.....ssr", "sss.....sss"),
    {"s": "stone_bricks", "r": "polished_deepslate", "t": state("dark_oak_log", axis="x"),
     "m": "waxed_cut_copper", "l": "blue_stained_glass",
     "u": state("lantern", hanging=True, waterlogged=False)},
    ("stone_bricks", "polished_andesite", "deepslate_tiles", "smooth_stone"),
)
SEAL_GATE = GatePlan(
    6, 3,
    ("...rrr...", "..rsssr..", ".rssmssr.", "sssmrmsss",
     "sts...sts", "sss...sss", "urs...sru", "rss...ssr"),
    {"s": "deepslate_bricks", "r": state("polished_basalt", axis="y"),
     "m": "chiseled_deepslate", "t": "polished_deepslate",
     "u": state("soul_lantern", hanging=False, waterlogged=False)},
    ("deepslate_tiles", "cracked_deepslate_bricks", "polished_deepslate", "tuff_bricks"),
)
THEATER_GATE = GatePlan(
    5, 3,
    ("...mmm...", "..mmmmm..", ".rrrrrrr.", "ttrkkkrtt",
     "tku...ukt", "tkk...kkt", "ttt...ttt", "stt...tts"),
    {"s": "stone_bricks", "r": "dark_oak_planks", "t": state("stripped_dark_oak_log", axis="y"),
     "k": "red_wool", "m": "crimson_planks", "u": state("lantern", hanging=True, waterlogged=False)},
    ("dark_oak_planks", "spruce_planks", "tuff_bricks", "polished_andesite"),
)


def rocks(scene: Structure, x: int, y: int, z: int, width: int, depth: int, height: int,
          palette: tuple[str, ...]):
    for layer in range(height):
        scale = 1 - layer / (height + 1)
        for px, pz in ellipse(x + .4*layer, z, max(1, width*scale), max(1, depth*scale)):
            scene.put(px, y + layer, pz, palette[speckle(px, pz, layer) % len(palette)])


def shrubs(scene: Structure, x: int, y: int, z: int, radius: int):
    for px, pz in ellipse(x, z, radius, max(1, radius - 1)):
        scene.put(px, y, pz, leaves(px, pz, 5))
        if abs(px + .5 - x) + abs(pz + .5 - z) < radius - 1:
            scene.put(px, y + 1, pz, leaf())


def canopy(scene: Structure, x: int, z: int, floor: int,
           width: int, depth: int, colors: tuple[str, str]):
    right, rear = x + width - 1, z + depth - 1
    for px, pz in ((x, z), (right, z), (x, rear), (right, rear)):
        scene.box((px, floor + 1, pz), (px, floor + 3, pz), state("spruce_log", axis="y"))
    for pz in (z, rear):
        scene.box((x, floor + 4, pz), (right, floor + 4, pz), state("spruce_log", axis="x"))
    for px in (x, right):
        scene.box((px, floor + 4, z + 1), (px, floor + 4, rear - 1), state("spruce_log", axis="z"))
    for px in range(x, right + 1):
        for pz in range(z, rear + 1):
            rise = int(z + 1 < pz < rear - 1)
            scene.put(px, floor + 4 + rise, pz, colors[(px - x)//2 % len(colors)])
    scene.lamp(x + 1, floor + 3, rear, drop=1)


def root_gate() -> Structure:
    scene = gate_body(ROOT_GATE)
    log = state("dark_oak_log", axis="x")
    scene.box((1, 8, 0), (7, 8, 4), log)
    scene.box((2, 9, 0), (6, 9, 3), log)
    for start, finish in [((1, 6, 4), (-4, 4, 4)), ((7, 6, 4), (12, 4, 3)),
                          ((2, 9, 1), (-2, 10, 1)), ((6, 9, 1), (10, 11, 1))]:
        scene.line(start, finish, log)
    for x, z, radius in [(-2, 1, 3), (2, 2, 3), (6, 2, 3), (10, 1, 2)]:
        shrubs(scene, x, 11, z, radius)
    rocks(scene, -3, 3, 2, 2, 2, 2, ("mossy_cobblestone", "moss_block"))
    scene.box((-4, 4, 2), (-3, 5, 2), state("dark_oak_log", axis="y"))
    scene.put(-5, 3, 2, "podzol")
    scene.put(-2, 4, 3, "podzol")
    scene.put(-5, 4, 2, "brown_mushroom")
    scene.put(-2, 5, 3, "red_mushroom")
    rocks(scene, 11, 3, 2, 3, 2, 2, ("mossy_cobblestone", "andesite", "moss_block"))
    shrubs(scene, 12, 5, 2, 2)
    scene.put(10, 4, 2, "moss_block")
    scene.put(10, 5, 2, "fern")
    return scene


def forge_gate() -> Structure:
    scene = gate_body(FORGE_GATE)
    scene.box((8, 10, 1), (9, 12, 3), "smooth_basalt")
    scene.box((7, 12, 1), (10, 12, 3), "polished_blackstone_bricks")
    scene.box((8, 12, 2), (9, 12, 2), "blackstone")
    for x, bearing in [(2, "west"), (8, "east")]:
        bracket(scene, x, 8, 5, "polished_blackstone_brick_stairs", bearing)
    trough(scene, -5, 3, 2, 4, 3, "polished_blackstone")
    scene.box((12, 3, 1), (16, 3, 4), "smooth_basalt")
    scene.put(13, 4, 2, "cauldron")
    scene.put(14, 4, 2, state("anvil", facing="east"))
    scene.put(15, 4, 2, "raw_iron_block")
    scene.put(16, 4, 3, "coal_block")
    scene.barrels(13, 4, 4)
    return scene


def sluice_gate() -> Structure:
    scene = gate_body(SLUICE_GATE)
    for x in (1, 9):
        scene.box((x, 8, 2), (x, 11, 3), state("spruce_log", axis="y"))
        scene.put(x, 10, 4, "prismarine_bricks")
    scene.box((1, 10, 3), (9, 10, 3), state("spruce_log", axis="x"))
    scene.put(5, 9, 3, state("iron_chain", axis="y", waterlogged=False))
    scene.put(5, 8, 3, "polished_andesite")
    trough(scene, -6, 3, 1, 5, 3, "prismarine_bricks")
    scene.put(-6, 4, 2, "sea_lantern")
    scene.box((12, 3, 0), (16, 3, 3), "stone_bricks")
    for x in (12, 16):
        scene.box((x, 4, 1), (x, 6, 1), state("spruce_log", axis="y"))
    scene.box((12, 7, 1), (16, 7, 1), state("spruce_log", axis="x"))
    scene.put(14, 6, 1, state("iron_chain", axis="y", waterlogged=False))
    scene.barrels(14, 4, 1, "east")
    scene.put(15, 4, 2, "prismarine_slab")
    return scene


def market_gate() -> Structure:
    scene = gate_body(MARKET_GATE)
    for x, facing in [(2, "west"), (6, "east")]:
        bracket(scene, x, 8, 3, "sandstone_stairs", facing)
    scene.layer(ellipse(-4, 1, 2, 2), 2, "sandstone")
    rocks(scene, -4, 3, 1, 2, 2, 2, ("sandstone", "cut_sandstone", "sand"))
    scene.box((-5, 4, 1), (-5, 6, 1), "chiseled_sandstone")
    canopy(scene, 10, 0, 3, 5, 5, ("orange_wool", "white_wool"))
    scene.box((10, 4, 1), (11, 4, 3), "smooth_sandstone")
    scene.barrels(11, 5, 2)
    scene.put(13, 4, 1, "terracotta")
    scene.put(13, 4, 3, "cut_sandstone")
    return scene


def crystal_cluster(scene: Structure, x: int, y: int, z: int, height: int):
    rocks(scene, x, y - 1, z, 3, 2, 2, ("calcite", "tuff", "packed_ice"))
    for offset, lateral, tall in [(-1, 0, height - 1), (0, 0, height),
                                  (1, 1, height - 2), (-2, 1, 2)]:
        if tall > 0:
            scene.box((x + offset, y, z + lateral), (x + offset, y + tall - 1, z + lateral),
                      "blue_ice" if lateral == 0 else "packed_ice")
    scene.put(x + 1, y - 1, z - 1, "sea_lantern")


def frost_gate() -> Structure:
    scene = gate_body(FROST_GATE)
    crystal_cluster(scene, -3, 4, 2, 3)
    crystal_cluster(scene, 10, 4, 1, 5)
    scene.box((1, 8, 0), (5, 8, 4), "calcite")
    for x in (1, 5):
        scene.put(x, 6, 4, "sea_lantern")
        scene.put(x, 10, 1, "packed_ice")
    return scene


def scope_stand(scene: Structure, x: int, y: int, z: int, azimuth: int):
    scene.box((x - 1, y, z - 1), (x + 1, y, z + 1), "stone_bricks")
    scene.put(x, y + 1, z, state("polished_basalt", axis="y"))
    scene.line((x - azimuth, y + 2, z), (x + 2*azimuth, y + 3, z),
               "waxed_cut_copper")
    scene.put(x + 2*azimuth, y + 3, z, "blue_stained_glass")
    scene.put(x, y, z + 1, "chiseled_stone_bricks")


def orrery_gate() -> Structure:
    scene = gate_body(ORRERY_GATE)
    for x in (1, 9):
        scene.put(x, 9, 4, stairs("waxed_cut_copper_stairs", "east" if x == 1 else "west"))
    scene.box((2, 11, 1), (8, 11, 4), "waxed_cut_copper")
    scene.box((4, 12, 2), (6, 12, 3), "waxed_cut_copper")
    scene.put(5, 13, 3, slab("waxed_cut_copper_slab"))
    scope_stand(scene, -4, 3, 2, 1)
    scene.box((12, 3, 2), (16, 3, 4), "stone_bricks")
    scene.box((13, 4, 2), (15, 4, 3), "smooth_stone")
    scene.put(14, 4, 3, "chiseled_stone_bricks")
    scene.put(14, 5, 3, state("lightning_rod", facing="up", powered=False, waterlogged=False))
    return scene


def standing_stone(scene: Structure, x: int, y: int, z: int, height: int):
    scene.box((x - 1, y, z - 1), (x + 1, y, z + 1), "polished_deepslate")
    scene.box((x, y + 1, z), (x, y + height, z), "deepslate_bricks")
    scene.put(x, y + height, z, "chiseled_deepslate")
    scene.put(x, y + height + 1, z, slab("deepslate_brick_slab"))


def seal_gate() -> Structure:
    scene = gate_body(SEAL_GATE)
    for x in (2, 6):
        bracket(scene, x, 8, 5, "deepslate_brick_stairs", "west" if x == 2 else "east")
    standing_stone(scene, -4, 3, 1, 3)
    for x, z, h in [(11, 2, 2), (13, 1, 3), (14, 4, 1)]:
        standing_stone(scene, x, 3, z, h)
    scene.lamp(12, 4, 4, soul=True)
    return scene


def shutters(scene: Structure, x: int, y: int, z: int, facing: str):
    for dy in (0, 1):
        scene.put(x, y + dy, z, state("dark_oak_trapdoor", facing=facing,
                                     half="bottom", open=True, powered=False, waterlogged=False))


def theater_gate() -> Structure:
    scene = gate_body(THEATER_GATE)
    scene.box((1, 9, 1), (7, 9, 4), state("stripped_dark_oak_log", axis="x"))
    canopy(scene, -6, 0, 3, 6, 5, ("red_wool", "crimson_planks"))
    scene.box((-5, 4, 1), (-2, 4, 3), "dark_oak_planks")
    scene.box((-5, 5, 2), (-2, 5, 2), slab("spruce_slab", "top"))
    scene.barrels(-3, 4, 1)
    shutters(scene, -1, 4, 2, "east")
    scene.box((10, 3, 1), (14, 3, 4), "tuff_bricks")
    scene.box((11, 4, 2), (13, 4, 3), "dark_oak_planks")
    scene.barrels(11, 5, 2)
    scene.barrels(12, 5, 2, "east")
    scene.put(13, 5, 3, "gray_wool")
    scene.box((14, 4, 4), (14, 6, 4), state("dark_oak_log", axis="y"))
    scene.box((13, 6, 4), (13, 7, 4), "red_wool")
    return scene


class Arena(Structure):
    def __init__(self, field: set[Point], paths: tuple[tuple[Point, ...], ...],
                 terrain: tuple[str, ...], road: tuple[str, ...],
                 paving: tuple[str, ...], seed: int, *, ring_road: tuple[int, int] | None = None):
        super().__init__(ARENA)
        self.field = field
        self.paths = paths
        self.routes = set().union(*(corridor(path, 6) for path in paths)) & ARENA
        if ring_road:
            inside, outside = ring_road
            self.routes |= ellipse(50, 50, outside, outside) - ellipse(50, 50, inside, inside)
        self.clear = self.field | self.routes
        self.seed = seed
        fill = "dirt" if terrain[0] == "grass_block" else terrain[0]
        self.layer(ARENA, 0, "stone")
        self.layer(ARENA, 1, fill)
        self.layer(ARENA, 2, fill)
        textured(self, ARENA, GROUND, terrain, seed)
        textured(self, self.routes, GROUND, road, seed + 1)
        textured(self, field, GROUND, paving, seed + 2)

    def inlays(self, points: tuple[Point, ...], block: str):
        for x, z in points:
            self.put(x, GROUND, z, block)


def threshold(scene: Arena, x: int, z: int, base: str, shaft: State | str,
              lintel: State | str, bracket_block: str, *, soul: bool = False):
    for px, inward in ((x - 5, "west"), (x + 5, "east")):
        pier(scene, px, z, GROUND, 7, base, shaft, base)
        # Two exterior ribs make the head pack into the columns instead of floating.
        scene.box((px, GROUND + 1, z - 1), (px, GROUND + 5, z + 1), shaft)
        bracket(scene, px + (1 if inward == "west" else -1), GROUND + 4, z + 1,
                bracket_block, inward)
        scene.lamp(px + (1 if inward == "west" else -1), GROUND + 5, z, drop=1, soul=soul)
    scene.box((x - 5, GROUND + 6, z - 1), (x + 5, GROUND + 6, z + 1), lintel)
    scene.box((x - 3, GROUND + 7, z), (x + 3, GROUND + 7, z), lintel)


def inset_pool(scene: Arena, pool: set[Point], bottom: str, liquid: str, rail_block: str):
    pool = pool - scene.clear
    bank = edge(pool)
    source = pool - bank
    scene.layer(pool, 0, bottom)
    scene.layer(bank, 1, bottom)
    scene.layer(bank, 2, bottom)
    scene.layer(bank, 3, bottom)
    for y in (1, 2):
        scene.layer(source, y, state(liquid, level=0))
    scene.layer(source, GROUND, AIR)
    rail(scene, bank, GROUND + 1, rail_block)


def thick_branch(scene: Structure, points: tuple[Position, ...], axis: str, girth: int):
    timber = state("dark_oak_log", axis=axis)
    for start, end in zip(points, points[1:]):
        scene.line(start, end, timber, girth)


def canopy_mass(scene: Structure, center: Position, radius: tuple[int, int, int], salt: int):
    x, y, z = center
    dx, dy, dz = radius
    for offset in range(-dy, dy + 1):
        size = math.sqrt(max(.16, 1 - (offset / (dy + .5))**2))
        cells = ellipse(x + .5 + .2*offset, z + .5, dx*size, dz*size)
        for px, pz in cells:
            if (px, y + offset, pz) in scene.blocks:
                continue
            if speckle(px, pz + offset*5, salt) < 89 or offset <= 0:
                scene.put(px, y + offset, pz, leaves(px, pz, 6))


def understory(scene: Arena):
    rng = Random(scene.seed)
    for x, z in sorted(ARENA - scene.clear):
        if (x, GROUND + 1, z) in scene.blocks or rng.randrange(100) >= 33:
            continue
        if scene.blocks[x, GROUND, z][0] not in {"grass_block", "rooted_dirt", "coarse_dirt", "moss_block", "podzol"}:
            continue
        if speckle(x//3, z//3, 10) > 71:
            continue
        choice = rng.randrange(100)
        if choice < 12 and (x, GROUND + 2, z) not in scene.blocks:
            scene.put(x, GROUND + 1, z, state("tall_grass", half="lower"))
            scene.put(x, GROUND + 2, z, state("tall_grass", half="upper"))
        elif choice < 60:
            scene.put(x, GROUND + 1, z, "short_grass")
        elif choice < 84:
            scene.put(x, GROUND + 1, z, "fern")
        elif choice < 96:
            scene.put(x, GROUND + 1, z, "moss_carpet")
        else:
            scene.put(x, GROUND + 1, z, "azure_bluet")


def root_arena() -> Arena:
    scene = Arena(
        polygon(((32, 32), (64, 32), (76, 43), (76, 65), (63, 76), (37, 76), (24, 65), (24, 47))),
        (((50, 100), (50, 74)),
         ((30, 67), (23, 72), (17, 62), (17, 50), (21, 38), (29, 35), (35, 39)),
         ((72, 64), (81, 72), (88, 61), (88, 47), (81, 36), (72, 32), (65, 35))),
        ("grass_block", "moss_block", "podzol", "rooted_dirt", "coarse_dirt"),
        ("coarse_dirt", "rooted_dirt", "andesite", "mossy_cobblestone", "gravel"),
        ("rooted_dirt", "coarse_dirt", "andesite", "grass_block"),
        19,
    )
    stone = ("mossy_cobblestone", "cobblestone", "andesite", "mossy_stone_bricks")
    rim(scene, scene.routes, stone, inner=47, height=2, variation=3, cap="mossy_cobblestone_slab")
    threshold(scene, 50, 94, "mossy_stone_bricks", state("dark_oak_log", axis="y"),
              state("dark_oak_log", axis="x"), "dark_oak_stairs")

    for y in range(4, 26):
        offset = .16*(y - 4)
        size = max(1.7, 5.1 - .135*(y - 4))
        for x, z in ellipse(50 + offset, 21 - offset*.3, size, size*.87):
            timber = "stripped_dark_oak_log" if speckle(x, z, 7) > 88 else "dark_oak_log"
            scene.put(x, y, z, state(timber, axis="y"))
    for points, axis, girth in (
        (((49, 8, 23), (44, 6, 24), (37, 5, 24), (32, 4, 27), (27, 4, 28)), "x", 1),
        (((51, 8, 21), (57, 6, 20), (65, 5, 21), (73, 4, 26), (78, 4, 29)), "x", 1),
        (((49, 8, 20), (47, 6, 15), (47, 5, 9), (43, 4, 7)), "z", 1),
        (((50, 8, 22), (51, 6, 26), (51, 4, 30)), "z", 1),
        (((51, 17, 20), (45, 21, 21), (38, 25, 22), (28, 28, 22)), "x", 1),
        (((52, 18, 20), (60, 23, 21), (67, 27, 25), (77, 29, 27)), "x", 1),
        (((52, 22, 20), (52, 27, 16), (58, 31, 10), (60, 32, 7)), "z", 1),
        (((54, 20, 21), (51, 25, 28), (51, 29, 36), (48, 31, 42)), "z", 1),
        (((44, 22, 21), (42, 26, 14), (34, 30, 10)), "z", 0),
        (((61, 24, 21), (69, 28, 16), (73, 29, 14)), "x", 0),
    ):
        thick_branch(scene, points, axis, girth)
    for center, size in (
        ((29, 28, 22), (13, 5, 10)), ((39, 30, 18), (13, 6, 10)),
        ((54, 34, 13), (16, 6, 12)), ((49, 33, 37), (15, 6, 11)),
        ((68, 30, 27), (15, 6, 12)), ((77, 31, 19), (11, 5, 10)),
        ((49, 29, 25), (14, 7, 12)), ((61, 34, 31), (10, 4, 8)),
    ):
        canopy_mass(scene, center, size, 42)
    for x, z, branch_y, length in [(34, 22, 25, 5), (45, 21, 21, 5), (59, 21, 22, 5),
                                   (68, 25, 27, 6), (51, 32, 27, 5), (52, 15, 28, 6)]:
        # Find the actual bough within this column so every chain meets solid wood.
        timber_y = next((y for y in range(branch_y + 3, branch_y - 4, -1)
                         if scene.blocks.get((x, y, z), AIR)[0].endswith("_log")), None)
        if timber_y is not None:
            scene.lamp(x, timber_y - 1, z, drop=length)

    decking(scene, 11, 34, 6, 6, 1, "mossy_stone_bricks", "mossy_stone_brick_stairs", "east")
    scene.box((12, 5, 35), (12, 5, 38), state("stripped_dark_oak_log", axis="z"))
    scene.put(11, 5, 34, "moss_block")
    scene.lamp(11, 6, 34)
    for x, z, size in [(9, 57, 3), (14, 73, 4), (30, 81, 5), (68, 83, 5),
                       (89, 75, 3), (95, 53, 3), (23, 21, 4), (78, 22, 3)]:
        rocks(scene, x, 4, z, size, max(2, size - 1), 2 + size % 2,
              ("mossy_cobblestone", "moss_block", "andesite"))
        shrubs(scene, x + 1, 4, z - 2, 2)
    for x, z in [(42, 26), (55, 27), (60, 22), (46, 13), (34, 25), (71, 25),
                 (33, 84), (66, 86), (92, 65), (19, 76)]:
        if (x, z) not in scene.clear:
            scene.put(x, GROUND, z, "podzol")
            if (x, GROUND + 1, z) not in scene.blocks:
                scene.put(x, GROUND + 1, z, "brown_mushroom")
    for x, z in [(32, 80), (67, 81), (12, 50), (90, 62), (47, 87), (56, 89)]:
        if (x, GROUND + 1, z) not in scene.blocks and (x, z) not in scene.clear:
            scene.put(x, GROUND + 1, z, "mossy_cobblestone")
            scene.lamp(x, GROUND + 2, z)
    scene.inlays(((42, 56), (58, 57), (47, 67), (52, 83), (16, 55), (85, 40)), "ochre_froglight")
    understory(scene)
    return scene


def crane(scene: Structure, x: int, front: int, rear: int, outward: int):
    iron = "polished_blackstone_bricks"
    for z in (front, rear):
        pier(scene, x, z, GROUND, 12, "smooth_basalt", iron, "chiseled_polished_blackstone")
        scene.line((x, 14, z), (x + 3*outward, 10, z), "waxed_cut_copper")
        scene.box((x + 3*outward, 4, z), (x + 3*outward, 10, z), "smooth_basalt")
    scene.box((x, 14, front), (x, 15, rear), iron)
    for z in range(front, rear + 1, 4):
        scene.box((x - 1, 16, z), (x + 1, 16, z), "waxed_cut_copper")
    for z, length in [(front + 4, 3), (rear - 4, 2)]:
        scene.lamp(x, 13, z, drop=length)
    center = (front + rear) // 2
    scene.box((x - 1, 13, center - 1), (x + 1, 13, center + 1), "waxed_cut_copper")
    scene.box((x, 8, center), (x, 12, center), state("iron_chain", axis="y", waterlogged=False))
    scene.put(x, 7, center, state("iron_bars", east=outward > 0, west=outward < 0,
                                  north=False, south=False, waterlogged=False))
    scene.put(x + outward, 7, center, state("iron_bars", east=outward < 0, west=outward > 0,
                                            north=False, south=False, waterlogged=False))


def forge_arena() -> Arena:
    scene = Arena(
        polygon(((32, 28), (68, 28), (68, 39), (75, 39), (75, 68), (25, 68), (25, 39), (32, 39))),
        (((50, 100), (50, 67)),
         ((32, 63), (28, 58), (10, 58), (10, 44), (17, 36), (33, 36)),
         ((68, 63), (72, 58), (90, 58), (90, 44), (83, 36), (67, 36))),
        ("basalt", "smooth_basalt", "blackstone", "polished_blackstone"),
        ("polished_blackstone_bricks", "polished_blackstone", "polished_basalt"),
        ("polished_blackstone_bricks", "cracked_polished_blackstone_bricks", "smooth_basalt"),
        7,
    )
    rim(scene, scene.routes, ("polished_blackstone_bricks", "blackstone", "smooth_basalt"),
        inner=47, height=5, variation=4, cap="polished_blackstone_brick_slab")
    threshold(scene, 50, 94, "smooth_basalt", "polished_blackstone_bricks",
              "waxed_cut_copper", "polished_blackstone_brick_stairs")
    for begin, end in ((13, 22), (77, 86)):
        for lo, hi in ((39, 54), (62, 72)):
            inset_pool(scene, rect(begin, lo, end, hi), "polished_blackstone", "lava", "nether_brick_fence")
        scene.layer(rect(begin, 55, end, 61), GROUND, "polished_basalt")
        for z in (55, 61):
            scene.layer(rect(begin, z, end, z), GROUND, "waxed_cut_copper")
    for x in (22, 78):
        crane(scene, x, 29, 76, -1 if x == 22 else 1)
    decking(scene, 25, 25, 7, 6, 1, "polished_blackstone_bricks",
            "polished_blackstone_brick_stairs", "south")
    decking(scene, 68, 25, 7, 6, 1, "polished_blackstone_bricks",
            "polished_blackstone_brick_stairs", "south")

    dark = "polished_blackstone_bricks"
    scene.box((32, 4, 13), (67, 5, 26), "smooth_basalt")
    scene.box((34, 6, 14), (65, 12, 25), dark)
    scene.box((38, 13, 16), (61, 15, 23), dark)
    scene.box((42, 16, 17), (57, 17, 22), "smooth_basalt")
    for x, height in [(40, 23), (55, 25)]:
        scene.box((x, 16, 17), (x + 4, height, 21), "smooth_basalt")
        scene.box((x - 1, height, 16), (x + 5, height, 22), dark)
        scene.box((x + 1, height, 18), (x + 3, height, 20), "blackstone")
    for x in (36, 43, 56, 63):
        scene.box((x, 5, 26), (x, 11, 26), "chiseled_polished_blackstone")
    for x1, x2 in [(38, 41), (45, 54), (58, 61)]:
        scene.box((x1, 6, 25), (x2, 8, 25), "shroomlight")
        scene.box((x1, 6, 26), (x2, 8, 26), "orange_stained_glass")
        scene.box((x1, 9, 26), (x2, 9, 26), "waxed_cut_copper")
    scene.box((33, 13, 25), (66, 13, 26), "waxed_cut_copper")
    for x in (39, 46, 53, 60):
        scene.lamp(x, 12, 26, drop=1)

    for x, z in [(27, 77), (64, 79)]:
        trough(scene, x, 3, z, 6, 4, "polished_blackstone")
        scene.box((x + 1, 4, z + 5), (x + 5, 4, z + 5), "smooth_basalt")
        scene.put(x + 1, 5, z + 5, "cauldron")
        scene.put(x + 3, 5, z + 5, state("anvil", facing="east"))
        scene.barrels(x + 5, 5, z + 5, "north")
        scene.put(x, 4, z + 5, "raw_iron_block")
        scene.put(x, 4, z + 6, "coal_block")
        scene.put(x + 2, 4, z + 7, "chiseled_polished_blackstone")
        scene.lamp(x + 2, 5, z + 7)
    scene.inlays(((32, 45), (50, 42), (67, 45), (33, 61), (50, 60), (66, 61),
                  (49, 77), (50, 87), (12, 57), (88, 57)), "ochre_froglight")
    return scene


def sluice_arena() -> Arena:
    scene = Arena(
        polygon(((42, 29), (58, 29), (71, 42), (71, 58), (58, 71), (42, 71), (29, 58), (29, 42))),
        (((50, 100), (50, 70)), ((50, 30), (50, 6)),
         ((31, 50), (6, 50)), ((69, 50), (94, 50))),
        ("stone_bricks", "prismarine", "mossy_stone_bricks", "andesite"),
        ("stone_bricks", "prismarine_bricks", "polished_andesite"),
        ("stone_bricks", "polished_andesite", "prismarine_bricks"),
        49, ring_road=(41, 47),
    )
    rim(scene, scene.routes, ("stone_bricks", "mossy_stone_bricks", "prismarine_bricks"),
        inner=47, height=3, variation=1, cap="stone_brick_slab")
    threshold(scene, 50, 88, "prismarine_bricks", "stone_bricks",
              state("spruce_log", axis="x"), "stone_brick_stairs")
    corner = ((43, 10), (43, 25), (33, 30), (25, 43), (10, 43), (13, 33), (20, 23), (30, 15))
    for flip_x, flip_z in [(False, False), (True, False), (False, True), (True, True)]:
        pool = polygon(tuple((100 - x if flip_x else x, 100 - z if flip_z else z)
                             for x, z in corner))
        inset_pool(scene, pool, "prismarine_bricks", "water", "dark_oak_fence")
        for x, z in sorted(edge(pool - scene.clear)):
            if (x + 3*z) % 9 == 0:
                scene.put(x, 2, z, "sea_lantern")
    for x in (41, 58):
        scene.box((x - 2, 0, 12), (x + 2, 7, 16), "prismarine_bricks")
        scene.box((x - 1, 8, 13), (x + 1, 12, 15), "stone_bricks")
        scene.box((x - 1, 13, 12), (x + 1, 13, 16), state("spruce_log", axis="z"))
        scene.put(x, 6, 16, "sea_lantern")
        scene.put(x, 9, 15, "chiseled_stone_bricks")
    scene.box((39, 13, 14), (60, 13, 14), state("spruce_log", axis="x"))
    for x in (44, 55):
        for y in range(9, 13):
            scene.put(x, y, 14, state("iron_chain", axis="y", waterlogged=False))
        scene.put(x, 8, 14, "polished_andesite")
    scene.box((48, 9, 14), (51, 12, 14), "spruce_planks")
    for x in (40, 59):
        bracket(scene, x, 11, 16, "stone_brick_stairs", "north")

    for x, z, approach in [(15, 42, "south"), (73, 54, "north")]:
        for post in (x + 1, x + 10):
            scene.box((post, 1, z + 1), (post, GROUND, z + 1),
                      state("spruce_log", axis="y"))
        scene.box((x, GROUND, z + 1), (x + 11, GROUND, z + 1),
                  state("spruce_log", axis="x"))
        decking(scene, x, z, 12, 4, 1, "spruce_planks", "spruce_stairs", approach)
        outer_z = z if approach == "south" else z + 3
        rail(scene, rect(x, outer_z, x + 11, outer_z), GROUND + 2, "dark_oak_fence")
        scene.put(x + 1, GROUND + 1, z + 1, "sea_lantern")
        scene.barrels(x + 9, GROUND + 2, z + 1, approach)
    scene.layer(ellipse(50, 50, 18, 18) - ellipse(50, 50, 16, 16), GROUND, "dark_prismarine")
    for x, z in [(39, 39), (60, 39), (39, 60), (60, 60), (49, 19), (50, 80),
                 (49, 89), (19, 49), (80, 50), (13, 70), (85, 28)]:
        if scene.blocks.get((x, GROUND, z), AIR) != AIR:
            scene.put(x, GROUND, z, "sea_lantern")
    return scene


def sand_stall(scene: Structure, x: int, z: int, floor: int, width: int,
               fabric: tuple[str, str], abandoned: bool):
    scene.layer(rect(x, z, x + width - 1, z + 5), floor, "smooth_sandstone")
    canopy(scene, x, z, floor, width, 6, fabric)
    scene.box((x + 1, floor + 1, z + 1), (x + width - 2, floor + 1, z + 2), "cut_sandstone")
    scene.box((x + 1, floor + 2, z + 1), (x + width - 2, floor + 2, z + 1), slab("spruce_slab"))
    scene.barrels(x + 1, floor + 1, z + 4)
    scene.put(x + 2, floor + 1, z + 4, "terracotta")
    scene.put(x + width - 2, floor + 2, z + 2, "decorated_pot")
    if abandoned:
        scene.put(x + width - 2, floor + 5, z + 2, AIR)
        scene.put(x + width - 3, floor + 5, z + 2, AIR)


def broken_pier(scene: Structure, x: int, z: int, height: int, lean: int):
    scene.box((x - 2, 4, z - 1), (x + 2, 5, z + 1), "sandstone")
    scene.box((x - 1, 6, z - 1), (x + 1, height + 3, z + 1), "cut_sandstone")
    for y in range(7, height + 4, 3):
        scene.put(x, y, z + 1, "chiseled_sandstone")
    for offset in range(1, 4):
        y = height + 3 + (offset + 1)//2
        scene.put(x + lean*offset, y - 1, z, "cut_sandstone")
        scene.put(x + lean*offset, y, z, "cut_sandstone")
    bracket(scene, x + 2*lean, height + 3, z + 1, "sandstone_stairs", "west" if lean > 0 else "east")


def market_arena() -> Arena:
    scene = Arena(
        polygon(((29, 30), (49, 23), (77, 53), (68, 72), (54, 77), (25, 52))),
        (((68, 96), (61, 81), (55, 74)), ((35, 31), (29, 18)),
         ((29, 54), (20, 55), (24, 69), (35, 79), (48, 82), (54, 76)),
         ((56, 32), (64, 28), (72, 36), (80, 44), (77, 53))),
        ("sandstone", "sand", "smooth_sandstone", "cut_sandstone", "terracotta"),
        ("sandstone", "cut_sandstone", "smooth_sandstone"),
        ("smooth_sandstone", "sandstone", "cut_sandstone", "polished_andesite"),
        10,
    )
    rim(scene, scene.routes, ("sandstone", "cut_sandstone", "smooth_sandstone", "terracotta"),
        inner=47, height=1, variation=5, cap="sandstone_slab")
    threshold(scene, 66, 91, "sandstone", "cut_sandstone", "chiseled_sandstone", "sandstone_stairs")
    for data in ((20, 17, 7, 1), (41, 15, 9, -1), (20, 32, 6, 1),
                 (57, 21, 7, -1), (37, 70, 5, 1), (78, 67, 8, -1)):
        broken_pier(scene, *data)
    decking(scene, 66, 17, 12, 8, 1, "smooth_sandstone", "sandstone_stairs", "south")
    sand_stall(scene, 68, 18, GROUND + 1, 8, ("orange_wool", "white_wool"), True)
    sand_stall(scene, 10, 38, GROUND, 6, ("terracotta", "yellow_wool"), False)
    sand_stall(scene, 12, 66, GROUND, 6, ("brown_wool", "white_wool"), True)
    sand_stall(scene, 79, 56, GROUND, 7, ("orange_wool", "brown_wool"), True)
    for x, z, radius in [(13, 54, 4), (28, 86, 5), (80, 76, 4), (89, 36, 3), (32, 11, 5)]:
        dune = ellipse(x, z, radius, max(2, radius - 2)) - scene.clear
        scene.layer(dune, GROUND + 1, "sandstone")
        scene.layer(ellipse(x, z, max(1, radius - 2), 2) & dune, GROUND + 2, "sand")
        for px, pz in sorted(dune):
            if speckle(px, pz, 8) > 94 and (px, GROUND + 2, pz) not in scene.blocks:
                scene.put(px, GROUND + 2, pz, "dead_bush")
    for x, z in [(19, 35), (45, 16), (52, 29), (37, 73), (72, 74), (24, 58)]:
        if (x, z) not in scene.clear:
            scene.put(x, 4, z, "chiseled_sandstone")
            scene.lamp(x, 5, z)
    scene.inlays(((35, 50), (46, 36), (64, 52), (57, 64), (60, 78)), "ochre_froglight")
    return scene


def cover(scene: Structure, heights: dict[Point, int], material, support=None):
    for (x, z), high in heights.items():
        neighbors = (heights.get((x + dx, z + dz), high) for dx, dz in DIRECTIONS.values())
        low = min(high, *neighbors)
        if support is not None:
            low = min(low, support(x, z, high))
        for y in range(low, high + 1):
            scene.put(x, y, z, material(x, y, z))


def ice_ridge(scene: Arena, outline: tuple[Point, ...], height: int, phase: float):
    footprint = polygon(outline) - scene.clear
    rim_cells = edge(footprint)
    for x, z in footprint:
        thickness = int((math.sin((z - x)*.23 + phase) + 1) * 2)
        stack = height - 3 + thickness if (x, z) not in rim_cells else max(2, height - 7 + thickness)
        for y in range(GROUND + 1, GROUND + 1 + stack):
            selector = speckle(x + y//3, z, 68)
            if y < GROUND + 3 and selector < 57:
                block = "calcite"
            else:
                block = "blue_ice" if selector < 23 else "packed_ice"
            scene.put(x, y, z, block)
        if (x, z) in rim_cells and (x + z) % 6 == 0:
            scene.put(x, GROUND + 1, z, "sea_lantern")


def frost_arena() -> Arena:
    scene = Arena(
        polygon(((37, 21), (63, 21), (68, 40), (68, 62), (62, 76), (38, 76), (32, 62), (32, 43))),
        (((50, 100), (50, 75)),
         ((39, 75), (31, 82), (19, 73), (15, 63), (15, 45), (21, 43), (34, 43)),
         ((61, 75), (68, 84), (82, 75), (88, 63), (88, 52), (82, 50), (67, 54))),
        ("tuff", "calcite", "andesite", "packed_ice"),
        ("polished_andesite", "smooth_stone", "calcite"),
        ("smooth_stone", "andesite", "polished_andesite", "tuff"),
        37,
    )
    rim(scene, scene.routes, ("tuff", "calcite", "andesite", "packed_ice"),
        inner=46, height=6, variation=5, cap="polished_tuff_slab")
    threshold(scene, 50, 94, "tuff", "calcite", "packed_ice", "polished_tuff_stairs")
    ice_ridge(scene, ((26, 19), (31, 19), (34, 25), (33, 38), (28, 41), (24, 34)), 13, .7)
    ice_ridge(scene, ((26, 48), (31, 46), (31, 61), (27, 71), (23, 68), (22, 60)), 11, 1.7)
    ice_ridge(scene, ((66, 22), (71, 20), (77, 31), (75, 43), (70, 48), (66, 39)), 14, 2.6)
    ice_ridge(scene, ((72, 59), (78, 55), (80, 68), (74, 77), (67, 78), (68, 70)), 10, .1)
    for x, z, h in [(37, 13, 8), (47, 12, 13), (56, 10, 10), (63, 14, 9),
                    (21, 82, 5), (78, 84, 6), (12, 37, 5), (91, 36, 5)]:
        crystal_cluster(scene, x, GROUND + 1, z, h)
    decking(scene, 7, 47, 4, 9, 1, "polished_tuff", "polished_tuff_stairs", "east")
    decking(scene, 88, 37, 5, 7, 2, "polished_andesite", "polished_andesite_stairs", "south")

    ceilings = {
        (x, z): round(14 + 10*math.sqrt(max(0, 1 - (terrain_height(x, z)/51)**2))
                      + 1.7*math.sin(x*.17 + z*.09) + 1.3*math.cos(z*.13 - x*.12))
        for x, z in ARENA
    }

    def ceiling_block(x, y, z):
        vein = abs(x - 37 - .22*(z - 47)) <= 2 or abs(x - 63 + .17*(z - 52)) <= 1.2
        if vein and terrain_height(x, z) < 41:
            return "light_blue_stained_glass" if (x + z) % 5 != 0 else "packed_ice"
        return ("calcite", "tuff", "andesite", "packed_ice")[speckle(x//2, z//2 + y, 15) % 4]

    cover(scene, ceilings, ceiling_block,
          lambda x, z, y: GROUND + 6 if terrain_height(x, z) >= 47 else y)
    for x, z, length in [(22, 24, 6), (33, 32, 6), (51, 14, 7), (64, 22, 5),
                         (79, 31, 7), (26, 53, 5), (66, 64, 6), (49, 66, 4),
                         (16, 63, 6), (85, 67, 5), (38, 83, 6), (59, 85, 5)]:
        ceiling = ceilings[x, z]
        scene.box((x - 1, ceiling - 2, z - 1), (x + 1, ceiling, z + 1), "calcite")
        scene.box((x, ceiling - length, z), (x, ceiling - 1, z), "packed_ice")
        scene.put(x, ceiling - length, z, "blue_ice")
    for x, z in [(44, 25), (56, 25), (39, 33), (60, 34), (48, 40), (41, 47),
                 (58, 49), (50, 56), (40, 66), (60, 68), (49, 79), (50, 90),
                 (16, 52), (21, 72), (86, 54), (83, 70)]:
        scene.put(x, GROUND, z, "sea_lantern")
    return scene


def orbital_ring(scene: Structure, center: Position, radius: float, pitch: float, yaw: float, block: str):
    cx, cy, cz = center
    ring = []
    for index in range(73):
        theta = math.tau * index / 72
        vx = radius*math.cos(theta)
        vz = radius*math.sin(theta)*math.cos(pitch)
        vy = radius*math.sin(theta)*math.sin(pitch)
        ring.append((round(cx + vx*math.cos(yaw) - vz*math.sin(yaw)),
                     round(cy + vy), round(cz + vx*math.sin(yaw) + vz*math.cos(yaw))))
    for start, finish in zip(ring, ring[1:]):
        scene.line(start, finish, block)


def orrery_arena() -> Arena:
    field = ellipse(50, 50, 24, 24)
    scene = Arena(
        field,
        (((50, 100), (50, 73)), ((50, 27), (50, 14)),
         ((27, 50), (14, 50)), ((73, 50), (84, 50))),
        ("stone_bricks", "deepslate_tiles", "tuff_bricks", "polished_andesite"),
        ("stone_bricks", "smooth_stone", "polished_andesite"),
        ("smooth_stone", "polished_andesite", "stone_bricks"),
        24, ring_road=(32, 38),
    )
    for radius, thickness, block in [(8, 1, "waxed_cut_copper"), (16, 1, "tuff_bricks"),
                                     (24, 1, "waxed_cut_copper"), (29, 1, "deepslate_tiles"),
                                     (38, 1, "waxed_exposed_cut_copper")]:
        scene.layer(ellipse(50, 50, radius, radius) - ellipse(50, 50, radius-thickness, radius-thickness),
                    GROUND, block)
    for x, z in field:
        dx, dz = x + .5 - CENTER, z + .5 - CENTER
        radial = math.hypot(dx, dz)
        if (abs(dx) < 1 or abs(dz) < 1 or abs(abs(dx) - abs(dz)) < .51) and radial > 5:
            scene.put(x, GROUND, z, "waxed_cut_copper" if radial < 17 else "deepslate_tiles")
    for points in [((50, 40), (53, 47), (60, 50), (53, 53), (50, 60),
                    (47, 53), (40, 50), (47, 47))]:
        scene.layer(polygon(points), GROUND, "waxed_exposed_cut_copper")

    rim(scene, scene.routes, ("stone_bricks", "tuff_bricks", "polished_deepslate"),
        inner=47, height=11, variation=2, cap="stone_brick_slab")
    threshold(scene, 50, 94, "polished_deepslate", "stone_bricks",
              "waxed_cut_copper", "stone_brick_stairs")
    dome = {(x, z): 14 + round(22 * math.sqrt(max(0, 1 - (terrain_height(x, z)/50)**2)))
            for x, z in ARENA}

    def dome_block(x, y, z):
        theta = math.atan2(z + .5 - CENTER, x + .5 - CENTER)
        radius = terrain_height(x, z)
        rib = abs(math.sin(theta * 8)) < .19 or radius > 45 or radius < 7
        if rib:
            return "waxed_cut_copper"
        if 16 <= radius <= 41:
            return "blue_stained_glass" if (round(theta * 20) + y//3) % 6 else "light_blue_stained_glass"
        return "waxed_exposed_cut_copper"

    cover(scene, dome, dome_block,
          lambda x, z, y: GROUND + 9 if terrain_height(x, z) > 47 else y)
    for index in range(12):
        theta = math.tau*(index + .5)/12
        x = round(49.5 + math.cos(theta)*44)
        z = round(49.5 + math.sin(theta)*44)
        if (x, z) in scene.routes:
            continue
        height = dome[x, z] - GROUND + 1
        pier(scene, x, z, GROUND, height, "polished_deepslate", "stone_bricks", "waxed_cut_copper")
        scene.put(x, GROUND + 1, z + (1 if z < 50 else -1), "chiseled_stone_bricks")
        scene.lamp(x, GROUND + 2, z + (1 if z < 50 else -1))

    for pitch, yaw, radius, material in [(math.pi/3, .38, 9, "waxed_cut_copper"),
                                          (-.79, 1.9, 11, "waxed_exposed_cut_copper"),
                                          (.17, .55, 8, "waxed_weathered_cut_copper")]:
        orbital_ring(scene, (50, 23, 49), radius, pitch, yaw, material)
    scene.box((49, 32, 48), (51, 36, 50), "waxed_cut_copper")
    scene.box((50, 24, 49), (50, 31, 49), state("iron_chain", axis="y", waterlogged=False))
    scene.box((49, 22, 48), (51, 24, 50), "lodestone")
    scene.put(50, 22, 49, "sea_lantern")
    for sign in (-1, 1):
        scene.line((50 + 2*sign, 23, 49), (50 + 8*sign, 23 + sign, 49 + 3*sign),
                   "waxed_cut_copper")
    for x, z, turn in [(31, 12, 1), (68, 13, -1), (12, 64, 1),
                       (87, 65, -1), (32, 85, 1), (67, 85, -1)]:
        scope_stand(scene, x, GROUND, z, turn)
        scene.box((x - 1, 4, z + 2), (x + 1, 4, z + 2), "dark_oak_planks")
        scene.put(x + 1, 5, z + 2, state("lectern", facing="south", has_book=False, powered=False))
        scene.lamp(x - 1, 5, z + 2)
    decking(scene, 89, 46, 5, 9, 1, "stone_bricks", "stone_brick_stairs", "west")
    for x, z in [(50, 29), (29, 49), (50, 70), (70, 50), (50, 50), (37, 37), (63, 37),
                 (36, 63), (64, 63), (50, 81), (25, 74), (76, 74), (25, 25), (75, 25),
                 (14, 50), (50, 15), (86, 50), (50, 90)]:
        scene.put(x, GROUND, z, "sea_lantern")
    return scene


def reliquary(scene: Structure, x: int, z: int, facing: str):
    step = -1 if facing == "west" else 1
    back = x - step*3
    x1, x2 = sorted((x, back))
    scene.box((x1, GROUND + 1, z - 3), (x2, GROUND + 1, z + 3), "polished_deepslate")
    scene.box((back, GROUND + 2, z - 3), (back, 11, z + 3), "deepslate_bricks")
    for offset in (-3, 3):
        scene.box((x, GROUND + 2, z + offset), (x, 10, z + offset),
                  state("polished_basalt", axis="y"))
        scene.box((x1, 10, z + offset), (x2, 11, z + offset), "deepslate_bricks")
    scene.box((x1, 11, z - 3), (x2, 11, z + 3), "deepslate_bricks")
    scene.put(x - step, GROUND + 2, z, "chiseled_deepslate")
    scene.put(x - step, GROUND + 3, z, slab("polished_deepslate_slab"))
    for offset in (-2, 2):
        scene.put(x, GROUND + 2, z + offset, "polished_deepslate")
        scene.lamp(x, GROUND + 3, z + offset, soul=True)


def seal_arena() -> Arena:
    scene = Arena(
        polygon(((39, 24), (61, 24), (73, 35), (73, 65), (27, 65), (27, 35))),
        (((50, 100), (50, 86), (36, 86), (36, 71), (50, 71), (50, 64)),
         ((27, 46), (11, 46), (11, 61), (18, 70), (27, 74), (36, 74)),
         ((73, 46), (89, 46), (89, 60), (82, 68), (74, 74), (50, 71))),
        ("deepslate_bricks", "polished_deepslate", "tuff_bricks", "cracked_deepslate_bricks"),
        ("deepslate_tiles", "polished_deepslate", "cracked_deepslate_tiles"),
        ("deepslate_tiles", "polished_deepslate", "tuff_bricks"),
        32,
    )
    rim(scene, scene.routes, ("deepslate_bricks", "polished_deepslate", "tuff_bricks"),
        inner=46, height=9, variation=3, cap="deepslate_brick_slab")
    threshold(scene, 50, 94, "polished_deepslate", state("polished_basalt", axis="y"),
              "chiseled_deepslate", "deepslate_brick_stairs", soul=True)
    for x1, z1, x2, z2 in [(41, 77, 64, 80), (25, 80, 31, 86), (64, 65, 70, 67)]:
        scene.box((x1, 4, z1), (x2, 11, z2), "deepslate_bricks")
        scene.box((x1, 11, z1), (x2, 11, z2), "chiseled_deepslate")
        for x in range(x1 + 1, x2, 4):
            scene.box((x, 4, z2), (x, 10, z2), state("polished_basalt", axis="y"))
            scene.put(x, 5, z2, "pearlescent_froglight")
    for x, z, face in [(17, 36, "west"), (17, 55, "west"),
                       (82, 36, "east"), (82, 54, "east")]:
        reliquary(scene, x, z, face)
    for z in (31, 40, 57, 64):
        for x in (24, 75):
            pier(scene, x, z, GROUND, 11, "polished_deepslate",
                 state("polished_basalt", axis="y"), "chiseled_deepslate")
            bracket(scene, x + (1 if x < 50 else -1), 12, z, "deepslate_brick_stairs",
                    "west" if x < 50 else "east")
    scene.box((36, 4, 14), (63, 6, 20), "polished_deepslate")
    scene.box((39, 7, 15), (60, 15, 19), "deepslate_bricks")
    scene.box((47, 8, 20), (52, 13, 20), "chiseled_deepslate")
    scene.box((48, 9, 21), (51, 12, 21), "polished_blackstone")
    for x in (44, 55):
        scene.box((x, 7, 20), (x, 13, 20), state("polished_basalt", axis="y"))
        scene.put(x, 8, 21, "polished_deepslate")
        scene.lamp(x, 9, 21, soul=True)
    scene.box((38, 16, 14), (61, 16, 20), "polished_deepslate")

    heights = {}
    for x, z in ARENA:
        if 22 <= x <= 77 and 15 <= z <= 68:
            heights[x, z] = 12 + round(14*math.sqrt(max(0, 1 - ((x + .5 - 50)/28)**2)))
        else:
            heights[x, z] = 13 if z < 72 else 12
    cover(scene, heights,
          lambda x, y, z: "deepslate_bricks" if z % 8 else "polished_deepslate",
          lambda x, z, y: 11 if terrain_height(x, z) > 46 else y)
    for z in (24, 32, 40, 48, 56, 64):
        for x in range(23, 77):
            y = heights[x, z]
            scene.put(x, y - 1, z, "polished_deepslate")
            scene.put(x, y - 2, z, "deepslate_bricks")
        for x in (36, 63):
            y = heights[x, z]
            scene.lamp(x, y - 3, z, drop=3, soul=True)
    for x, z in [(50, 25), (43, 19), (56, 19), (34, 36), (66, 36), (50, 34),
                 (32, 44), (67, 44), (39, 49), (50, 48), (61, 49),
                 (36, 59), (63, 59), (50, 60), (12, 47), (86, 47), (22, 70), (77, 70),
                 (36, 78), (44, 86), (50, 92)]:
        scene.put(x, GROUND, z, "pearlescent_froglight")
    sigil = ((50, 37), (61, 43), (61, 54), (50, 61), (39, 54), (39, 43))
    scene.layer(edge(polygon(sigil)), GROUND, "chiseled_deepslate")
    return scene


def workshop_wing(scene: Structure, x: int, z: int, width: int, length: int, outside: int):
    right, end = x + width - 1, z + length - 1
    scene.layer(rect(x, z, right, end), GROUND, "spruce_planks")
    scene.box((x, GROUND + 1, z), (right, 11, z), "dark_oak_planks")
    wall_x = x if outside < 0 else right
    scene.box((wall_x, GROUND + 1, z), (wall_x, 11, end), "dark_oak_planks")
    for px, pz in [(x, z), (right, z), (x, end), (right, end)]:
        scene.box((px, GROUND + 1, pz), (px, 12, pz), state("stripped_dark_oak_log", axis="y"))
    for pz in range(z, end + 1):
        height = 12 + (1 if z + 1 < pz < end - 1 else 0)
        scene.box((x, 12, pz), (right, height, pz), "dark_oak_planks")
        if pz % 3 == 0:
            scene.box((x, 11, pz), (right, 11, pz), state("dark_oak_log", axis="x"))
    for offset in (1, 2):
        px = x + offset if outside < 0 else right - offset
        scene.box((px, 4, end), (px, 9 + offset, end), "red_wool" if offset == 1 else "crimson_planks")
    for px in range(x + 2, right - 1, 2):
        scene.barrels(px, 4, z + 1)
        scene.put(px, 5, z + 1, slab("dark_oak_slab", "top"))
        scene.put(px, 6, z + 1, "brown_wool" if px % 3 else "red_wool")
    frame_x = wall_x - outside
    shutters(scene, frame_x, 6, z + 4, "east" if outside < 0 else "west")
    scene.lamp(x + width//2, 10, end - 1, drop=2)


def seating(scene: Arena):
    for x, z in ARENA - scene.clear:
        radius = terrain_height(x, z)
        if not (37 <= radius < 47 and z > 59 and abs(x + .5 - 50) > 4):
            continue
        rise = min(3, 1 + int((radius - 37) / 3.2))
        for level in range(1, rise + 1):
            scene.put(x, GROUND + level, z, "dark_oak_planks" if level == rise else "tuff_bricks")
        aisle = 41 <= x <= 44 or 55 <= x <= 58
        lower_radius = terrain_height(x, z - 1)
        lower_tier = min(3, 1 + int((lower_radius - 37) / 3.2)) if lower_radius >= 37 else 0
        if aisle and lower_tier < rise:
            scene.put(x, GROUND + rise, z, stairs("dark_oak_stairs", "south"))
        elif not aisle and int((radius - 37) * 10) % 32 < 9:
            dx = x + .5 - 50
            dz = z + .5 - 50
            direction = "south" if abs(dx) < abs(dz) else "east" if dx > 0 else "west"
            scene.put(x, GROUND + rise + 1, z, stairs("dark_oak_stairs", direction))
    for x, z in [(31, 86), (68, 86), (20, 77), (79, 77)]:
        if (x, z) in scene.clear:
            continue
        top = max(y for px, y, pz in scene.blocks if px == x and pz == z)
        scene.put(x, top + 1, z, "dark_oak_planks")
        scene.lamp(x, top + 2, z)


def theater_arena() -> Arena:
    scene = Arena(
        polygon(((31, 34), (69, 34), (81, 59), (78, 68), (71, 75), (62, 79),
                 (50, 82), (38, 79), (29, 75), (22, 68), (19, 59))),
        (((50, 100), (50, 80)),
         ((24, 65), (16, 60), (13, 50), (18, 44), (25, 44), (32, 45)),
         ((76, 65), (84, 60), (87, 50), (82, 44), (75, 44), (68, 45))),
        ("tuff_bricks", "polished_andesite", "dark_oak_planks", "stone_bricks"),
        ("spruce_planks", "dark_oak_planks", "tuff_bricks"),
        ("spruce_planks", "dark_oak_planks", "stripped_spruce_wood"),
        36,
    )
    rim(scene, scene.routes, ("tuff_bricks", "stone_bricks", "dark_oak_planks"),
        inner=47, height=2, variation=3, cap="dark_oak_slab")
    threshold(scene, 50, 94, "tuff_bricks", state("stripped_dark_oak_log", axis="y"),
              "crimson_planks", "dark_oak_stairs")
    workshop_wing(scene, 14, 25, 14, 16, -1)
    workshop_wing(scene, 72, 25, 14, 16, 1)

    stage = ellipse(50, 33, 23, 17) & rect(23, 16, 76, 32)
    scene.layer(stage, GROUND + 1, "dark_oak_planks")
    scene.layer(stage - edge(stage), GROUND + 1, "crimson_planks")
    scene.layer(rect(44, 33, 55, 33), GROUND + 1, stairs("dark_oak_stairs", "north"))
    for x in (29, 70):
        scene.box((x - 1, 4, 30), (x + 1, 15, 32), state("stripped_dark_oak_log", axis="y"))
        scene.box((x - 2, 14, 29), (x + 2, 16, 33), "dark_oak_planks")
        side = 1 if x == 29 else -1
        for fold in range(1, 5):
            length = 12 - fold*2
            scene.box((x + side*fold, 16 - length, 31),
                      (x + side*fold, 15, 33), "red_wool" if fold % 2 else "red_concrete")
    scene.box((28, 16, 30), (71, 17, 34), state("stripped_dark_oak_log", axis="x"))
    scene.box((31, 18, 31), (68, 18, 33), "crimson_planks")
    for x in range(34, 68):
        fall = 1 + round(2 * abs(math.sin((x - 34) * .38)))
        scene.box((x, 16 - fall, 33), (x, 15, 33), "red_wool")
    for x, length in [(38, 2), (46, 3), (54, 2), (62, 3)]:
        scene.lamp(x, 15, 32, drop=length)

    # Roof and rear scenery follow the stage and side rooms, leaving the seating open above.
    for x in range(26, 74):
        inset = round(abs(x - 49.5) / 8)
        roof_start = 16 + inset
        scene.box((x, 16, roof_start), (x, 18 + (2 if 34 <= x <= 65 else 0), 29), "dark_oak_planks")
        scene.box((x, 5, roof_start), (x, 15, roof_start), "spruce_planks")
        if x % 4 == 0:
            scene.box((x, 5, roof_start), (x, 18, roof_start), state("dark_oak_log", axis="y"))
        if 33 <= x <= 66:
            for y in range(6, 14):
                panel = "blue_terracotta" if y - 6 > round(abs(x - 50) / 3) else "gray_wool"
                scene.put(x, y, roof_start + 1, panel)
    scene.box((31, 13, 25), (68, 13, 25), state("dark_oak_log", axis="x"))
    for x in (32, 67):
        scene.box((x, 5, 25), (x, 12, 25), state("dark_oak_log", axis="y"))
        scene.lamp(x + (1 if x < 50 else -1), 12, 25, drop=2)

    seating(scene)
    for x, z in [(29, 53), (36, 69), (50, 61), (64, 69), (70, 53),
                 (46, 84), (53, 84), (50, 91), (18, 52), (82, 52)]:
        scene.put(x, GROUND, z, "ochre_froglight")
    return scene


TOWERS = {
    "root": (root_gate, root_arena),
    "forge": (forge_gate, forge_arena),
    "sluice": (sluice_gate, sluice_arena),
    "sand_market": (market_gate, market_arena),
    "frost": (frost_gate, frost_arena),
    "orrery": (orrery_gate, orrery_arena),
    "seal": (seal_gate, seal_arena),
    "theater": (theater_gate, theater_arena),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=OUTPUT)
    output = parser.parse_args().output

    for name, (gate, arena) in TOWERS.items():
        for part, build in (("gate", gate), ("arena", arena)):
            template = build().template()
            path = output / name / f"{part}.nbt"
            path.parent.mkdir(parents=True, exist_ok=True)
            _, payload = nbt_payload(template)
            compressed = gzip.compress(b"\x0a\x00\x00" + payload, mtime=0)
            if not path.exists() or compressed != path.read_bytes():
                path.write_bytes(compressed)
            size = " × ".join(str(side) for side in template["size"])
            print(f"{name}/{part}: {size}, {len(template['blocks']):,} cells")


if __name__ == "__main__":
    main()

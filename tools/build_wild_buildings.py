"""Compile authored map blueprints into native, chunk-bounded wild structures.

Install the authoring dependency with:
    python -m pip install --target temp/nbt_vendor nbtlib
The source blueprints and their NBT files are read only.
"""
from __future__ import annotations

import copy
import gzip
import io
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "temp/nbt_vendor"))
try:
    import nbtlib
except ModuleNotFoundError as error:
    raise SystemExit("需要 nbtlib：python -m pip install --target temp/nbt_vendor nbtlib") from error

DATA = ROOT / "src/main/resources/data/yumis_dungeon_and_fight"
SOURCE = ROOT / "tools/wild_buildings.json"
NS = "yumis_dungeon_and_fight"


def json_file(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def ints(values):
    return nbtlib.List[nbtlib.Int]([nbtlib.Int(v) for v in values])


def doubles(values):
    return nbtlib.List[nbtlib.Double]([nbtlib.Double(v) for v in values])


def state(name):
    return nbtlib.Compound({"Name": nbtlib.String(name)})


def block(name, pos, data=None):
    result = nbtlib.Compound({"pos": ints(pos), "state": nbtlib.Int(name)})
    if data is not None:
        result["nbt"] = data
    return result


def source_template(identifier):
    namespace, path = identifier.split(":", 1)
    return nbtlib.load(ROOT / "src/main/resources/data" / namespace / "structure" / (path + ".nbt"), gzipped=True)


def bearing(name):
    path = name.split(":", 1)[-1]
    return path not in {"air", "water", "lava", "short_grass", "tall_grass", "fern", "large_fern", "vine", "lantern", "torch"} \
        and not path.endswith(("_leaves", "_sapling", "_flower", "_torch")) and not path.startswith("potted_")


def road_cells(blueprint):
    blocked = {(f["position"][0] + x, f["position"][1] + z)
               for f in blueprint["facilities"] for x, z in f.get("blocked", [])}
    cells = set()
    for road in blueprint["roads"]:
        for (ax, az), (bx, bz) in zip(road["points"], road["points"][1:]):
            dx, dz = bx - ax, bz - az
            length = dx * dx + dz * dz
            margin = math.ceil(road["width"] / 2)
            for x in range(min(ax, bx) - margin, max(ax, bx) + margin + 1):
                for z in range(min(az, bz) - margin, max(az, bz) + margin + 1):
                    t = max(0, min(1, ((x - ax) * dx + (z - az) * dz) / length)) if length else 0
                    if math.hypot(x - ax - dx * t, z - az - dz * t) <= road["width"] / 2 and (x, z) not in blocked:
                        cells.add((x, z))
    return cells


def compile_template(entry):
    if "blueprint" in entry:
        blueprint = json.loads((DATA / "map_blueprint" / (entry["blueprint"] + ".json")).read_text(encoding="utf-8"))
    else:
        facility = entry["facility"]
        width, _, depth = map(int, source_template(facility["template"])["size"])
        blueprint = {
            "bounds": [0, 0, width - 1, depth - 1],
            "roads": [],
            "facilities": [{"id": entry["id"], "position": [0, 0], **facility}],
        }
    bx0, bz0, bx1, bz1 = blueprint["bounds"]
    palette, indices, cells, entities = [], {}, {}, []
    minimum_y, maximum_y, version = 0, 0, 0
    air = state("minecraft:air")
    road_materials = blueprint.get("settings", {}).get("roadBlocks", [])
    road_weights = blueprint.get("settings", {}).get("roadWeights", [])
    sky = entry.get("altitude", 0) > 0
    barrels = {(b["facility"], tuple(b["pos"])) for b in entry.get("barrels", [])}
    assigned = set()

    def put(pos, material, data=None, replace=True):
        nonlocal minimum_y, maximum_y
        x, y, z = pos
        if not (bx0 <= x <= bx1 and bz0 <= z <= bz1):
            raise ValueError(f"{entry['id']}: 结构超出蓝图范围 {pos}")
        minimum_y, maximum_y = min(minimum_y, y), max(maximum_y, y)
        if replace or pos not in cells:
            cells[pos] = (copy.deepcopy(material), copy.deepcopy(data))

    roads = road_cells(blueprint)
    for x, z in sorted(roads):
        roll = ((x // 3 * 73428767) ^ (z // 3 * 912931)) % sum(road_weights)
        for material, weight in zip(road_materials, road_weights):
            if roll < weight:
                put((x, -1, z), state(material))
                break
            roll -= weight
        for y in range(3):
            put((x, y, z), air, replace=False)

    # Clear only the original ground-contact columns, as the map module does for fitted foundations.
    loaded = []
    for facility in blueprint["facilities"]:
        template = source_template(facility["template"])
        version = max(version, int(template["DataVersion"]))
        x0, z0 = facility["position"]
        reference = facility["referenceY"]
        footprint = set()
        for item in template["blocks"]:
            x, y, z = map(int, item["pos"])
            name = str(template["palette"][int(item["state"])]["Name"])
            if y < reference and bearing(name):
                footprint.add((x0 + x, z0 + z))
        if facility.get("platform", True):
            for x, z in footprint:
                for y in range(0, max(3, int(template["size"][1]) - reference)):
                    put((x, y, z), air, replace=False)
                if not sky:
                    depth = 4 if facility.get("foundation") == "terrain_retaining" else 2
                    for y in range(-depth, -1):
                        put((x, y, z), state("minecraft:cobblestone"), replace=False)
        loaded.append((facility, template))

    for facility, template in loaded:
        x0, z0 = facility["position"]
        reference = facility["referenceY"]
        for item in template["blocks"]:
            local = tuple(map(int, item["pos"]))
            material = template["palette"][int(item["state"])]
            if str(material["Name"]) == "minecraft:air":
                continue
            data = copy.deepcopy(item.get("nbt"))
            if data and "NeoForgeData" in data:
                data["NeoForgeData"].pop("ydnf_container_identity", None)
                if not data["NeoForgeData"]:
                    del data["NeoForgeData"]
            key = (facility["id"], local)
            if key in barrels:
                if str(material["Name"]) != "minecraft:barrel" or data and data.get("Items"):
                    raise ValueError(f"{entry['id']}: 战利品位置不是空木桶 {key}")
                data = data or nbtlib.Compound()
                data["id"] = nbtlib.String("minecraft:barrel")
                data.pop("Items", None)
                data.pop("LootTableSeed", None)
                data["LootTable"] = nbtlib.String(f"{NS}:chests/wild_buildings/{entry['id']}")
                assigned.add(key)
            put((x0 + local[0], local[1] - reference, z0 + local[2]), material, data)
        for entity in template["entities"]:
            copied = copy.deepcopy(entity)
            p = copied["pos"]
            copied["pos"] = doubles([float(p[0]) + x0 - bx0, float(p[1]) - reference, float(p[2]) + z0 - bz0])
            p = copied["blockPos"]
            copied["blockPos"] = ints([int(p[0]) + x0 - bx0, int(p[1]) - reference, int(p[2]) + z0 - bz0])
            entities.append(copied)
        for fixture in facility.get("fixtures", []):
            local_x, local_y, local_z = fixture["pos"]
            pos = (x0 + local_x, local_y - reference, z0 + local_z)
            if cells.get(pos, (air, None))[0] != air:
                raise ValueError(f"{entry['id']}: 设施放置位已被占用 {fixture['pos']}")
            material = state(fixture["block"])
            if "properties" in fixture:
                material["Properties"] = nbtlib.Compound({
                    key: nbtlib.String(value) for key, value in fixture["properties"].items()
                })
            put(pos, material)
    if assigned != barrels:
        raise ValueError(f"{entry['id']}: 未找到战利品木桶 {barrels - assigned}")

    # These plants already belong to the authored ground; never overwrite structures or roads.
    for ground in blueprint.get("grounds", []):
        for plant in ground.get("plants", []):
            if not plant.get("tall", False) and (plant["x"], plant["z"]) not in roads:
                put((plant["x"], 0, plant["z"]), state(plant["block"]), replace=False)

    floor_offset = -minimum_y
    items = []
    for (x, y, z), (material, data) in sorted(cells.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
        key = material.snbt()
        if key not in indices:
            indices[key] = len(palette)
            palette.append(material)
        items.append(block(indices[key], [x - bx0, y - minimum_y, z - bz0], data))
    for entity in entities:
        entity["pos"][1] = nbtlib.Double(float(entity["pos"][1]) - minimum_y)
        entity["blockPos"][1] = nbtlib.Int(int(entity["blockPos"][1]) - minimum_y)
        if str(entity["nbt"].get("id", "")) in ("minecraft:item_frame", "minecraft:glow_item_frame", "minecraft:painting"):
            entity["nbt"]["block_pos"] = nbtlib.IntArray(entity["blockPos"])
    result = nbtlib.File({
        "DataVersion": nbtlib.Int(version),
        "size": ints([bx1 - bx0 + 1, maximum_y - minimum_y + 1, bz1 - bz0 + 1]),
        "palette": nbtlib.List[nbtlib.Compound](palette),
        "blocks": nbtlib.List[nbtlib.Compound](items),
        "entities": nbtlib.List[nbtlib.Compound](entities),
    })
    destination = DATA / "structure/wild_buildings" / (entry["id"] + ".nbt")
    destination.parent.mkdir(parents=True, exist_ok=True)
    raw = io.BytesIO()
    result.write(raw)
    destination.write_bytes(gzip.compress(raw.getvalue(), mtime=0))
    return floor_offset


def build():
    config = json.loads(SOURCE.read_text(encoding="utf-8"))
    structures = []
    for entry in config["buildings"]:
        name = entry["id"]
        floor = compile_template(entry)
        pool = f"{NS}:wild_buildings/{name}"
        structures.append({"structure": pool, "weight": 1})
        json_file(DATA / "worldgen/structure" / "wild_buildings" / (name + ".json"), {
            "type": f"{NS}:wild_building",
            "biomes": [f"minecraft:{biome}" for biome in entry["biomes"]],
            "spawn_overrides": {},
            "step": "surface_structures",
            "terrain_adaptation": "none" if entry.get("altitude", 0) else "beard_thin",
            "start_pool": pool,
            "height_offset": entry.get("altitude", 0) + 1 - floor,
            "floor_offset": floor,
            "max_relief": entry.get("max_relief", 12),
            "ground_clearance": entry.get("ground_clearance", 0),
        })
        json_file(DATA / "worldgen/template_pool" / "wild_buildings" / (name + ".json"), {
            "fallback": "minecraft:empty",
            "elements": [{"weight": 1, "element": {
                "element_type": "minecraft:single_pool_element", "location": pool,
                "processors": {"processors": [{"processor_type": f"{NS}:attached_entities"}]},
                "projection": "rigid",
            }}],
        })
        if entry.get("barrels"):
            json_file(DATA / "loot_table/chests/wild_buildings" / (name + ".json"), {
                "type": "minecraft:chest", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:loot_table", "value": entry["loot"]}]}],
            })
        print(f"{name}: {len(entry.get('barrels', []))} 个战利品木桶，地面偏移 {floor}")
    json_file(DATA / "worldgen/structure_set/wild_buildings.json", {
        "placement": {"type": "minecraft:random_spread", "spacing": config["spacing_chunks"],
                      "separation": config["separation_chunks"], "salt": config["salt"]},
        "structures": structures,
    })


if __name__ == "__main__":
    build()

"""Convert the authored Writhing Sky Castle MC 26.2 world into a compact dungeon map resource.

Run with Python 3:
    python tools/build_writhing_castle.py

Reads the core 24x24 chunks (X/Z: -192..191, Y: -16..317) from the master save,
strips demonstration container items from chests/barrels while preserving custom names,
signs, and banners, and writes a gzip-compressed NBT chunk-section map to:
    src/main/resources/data/yumis_dungeon_and_fight/dungeon_map/writhing_castle.nbt
"""
from __future__ import annotations

import argparse
import gzip
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE = ROOT / "temp/writhing-sky-castle-20261123/saves/WrithingSkyCastle"
DEFAULT_OUTPUT = ROOT / "src/main/resources/data/yumis_dungeon_and_fight/dungeon_map/writhing_castle.nbt"

MIN_CX, MAX_CX = -12, 11
MIN_CZ, MAX_CZ = -12, 11
MIN_CY, MAX_CY = -1, 19  # Y = -16 .. 319 (actual highest block Y = 317)
MIN_X, MAX_X = MIN_CX * 16, MAX_CX * 16 + 15
MIN_Z, MAX_Z = MIN_CZ * 16, MAX_CZ * 16 + 15
MIN_Y, MAX_Y = -16, 317
EXPECTED_CHUNKS = (MAX_CX - MIN_CX + 1) * (MAX_CZ - MIN_CZ + 1)
FORMAT_VERSION = 1

CONTAINER_IDS = frozenset({"minecraft:chest", "minecraft:barrel"})
PASSABLE_BLOCKS = frozenset({
    "minecraft:air",
    "minecraft:cave_air",
    "minecraft:void_air",
    "minecraft:short_grass",
    "minecraft:tall_grass",
    "minecraft:fern",
    "minecraft:moss_carpet",
    "minecraft:red_carpet",
    "minecraft:white_carpet",
    "minecraft:purple_carpet",
    "minecraft:yellow_carpet",
    "minecraft:pale_oak_sign",
    "minecraft:pale_oak_wall_sign",
    "minecraft:white_wall_banner",
    "minecraft:red_wall_banner",
})

# --- Minimal standalone Minecraft NBT reader/writer ---

TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class _Reader:
    __slots__ = ("buf", "pos")

    def __init__(self, data: bytes) -> None:
        self.buf = memoryview(data)
        self.pos = 0

    def take(self, n: int) -> bytes:
        end = self.pos + n
        out = bytes(self.buf[self.pos:end])
        self.pos = end
        return out

    def unpack(self, fmt: str):
        size = struct.calcsize(fmt)
        end = self.pos + size
        val = struct.unpack_from(fmt, self.buf, self.pos)
        self.pos = end
        return val[0]

    def string(self) -> str:
        length = self.unpack(">H")
        return self.take(length).decode("utf-8")

    def payload(self, tag_type: int):
        if tag_type == TAG_BYTE:
            return self.unpack(">b")
        if tag_type == TAG_SHORT:
            return self.unpack(">h")
        if tag_type == TAG_INT:
            return self.unpack(">i")
        if tag_type == TAG_LONG:
            return self.unpack(">q")
        if tag_type == TAG_FLOAT:
            return self.unpack(">f")
        if tag_type == TAG_DOUBLE:
            return self.unpack(">d")
        if tag_type == TAG_BYTE_ARRAY:
            n = self.unpack(">i")
            return self.take(n)
        if tag_type == TAG_STRING:
            return self.string()
        if tag_type == TAG_LIST:
            elem_type = self.unpack(">b")
            n = self.unpack(">i")
            return (elem_type, [self.payload(elem_type) for _ in range(n)])
        if tag_type == TAG_COMPOUND:
            out: dict[str, tuple[int, object]] = {}
            while True:
                t = self.unpack(">b")
                if t == TAG_END:
                    return out
                name = self.string()
                out[name] = (t, self.payload(t))
        if tag_type == TAG_INT_ARRAY:
            n = self.unpack(">i")
            end = self.pos + n * 4
            vals = list(struct.unpack_from(f">{n}i", self.buf, self.pos))
            self.pos = end
            return vals
        if tag_type == TAG_LONG_ARRAY:
            n = self.unpack(">i")
            end = self.pos + n * 8
            vals = list(struct.unpack_from(f">{n}q", self.buf, self.pos))
            self.pos = end
            return vals
        raise ValueError(f"Unsupported NBT tag type: {tag_type}")


def read_nbt(raw: bytes) -> dict[str, tuple[int, object]]:
    reader = _Reader(raw)
    root_type = reader.unpack(">b")
    if root_type != TAG_COMPOUND:
        raise ValueError(f"Expected root compound tag, got {root_type}")
    _ = reader.string()
    return reader.payload(TAG_COMPOUND)


def _write_string(out: bytearray, text: str) -> None:
    encoded = text.encode("utf-8")
    out.extend(struct.pack(">H", len(encoded)))
    out.extend(encoded)


def _write_payload(out: bytearray, tag_type: int, value) -> None:
    if tag_type == TAG_BYTE:
        out.extend(struct.pack(">b", value))
    elif tag_type == TAG_SHORT:
        out.extend(struct.pack(">h", value))
    elif tag_type == TAG_INT:
        out.extend(struct.pack(">i", value))
    elif tag_type == TAG_LONG:
        out.extend(struct.pack(">q", value))
    elif tag_type == TAG_FLOAT:
        out.extend(struct.pack(">f", value))
    elif tag_type == TAG_DOUBLE:
        out.extend(struct.pack(">d", value))
    elif tag_type == TAG_BYTE_ARRAY:
        out.extend(struct.pack(">i", len(value)))
        out.extend(value)
    elif tag_type == TAG_STRING:
        _write_string(out, value)
    elif tag_type == TAG_LIST:
        elem_type, items = value
        out.extend(struct.pack(">bi", elem_type, len(items)))
        for item in items:
            _write_payload(out, elem_type, item)
    elif tag_type == TAG_COMPOUND:
        for k, (t, v) in value.items():
            out.append(t)
            _write_string(out, k)
            _write_payload(out, t, v)
        out.append(TAG_END)
    elif tag_type == TAG_INT_ARRAY:
        out.extend(struct.pack(f">i{len(value)}i", len(value), *value))
    elif tag_type == TAG_LONG_ARRAY:
        out.extend(struct.pack(f">i{len(value)}q", len(value), *value))
    else:
        raise ValueError(f"Unsupported NBT tag type: {tag_type}")


def write_nbt_gzip(path: Path, root: dict[str, tuple[int, object]]) -> None:
    buf = bytearray()
    buf.append(TAG_COMPOUND)
    _write_string(buf, "")
    _write_payload(buf, TAG_COMPOUND, root)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb", compresslevel=9) as fh:
        fh.write(buf)


def state_key(entry: dict[str, tuple[int, object]]) -> tuple[str, tuple[tuple[str, str], ...]]:
    name = str(entry["Name"][1])
    props: tuple[tuple[str, str], ...] = ()
    if "Properties" in entry:
        raw_props = entry["Properties"][1]
        assert isinstance(raw_props, dict)
        props = tuple(sorted((k, str(v[1])) for k, v in raw_props.items()))
    return name, props


def state_to_nbt(key: tuple[str, tuple[tuple[str, str], ...]]) -> dict[str, tuple[int, object]]:
    name, props = key
    tag: dict[str, tuple[int, object]] = {"Name": (TAG_STRING, name)}
    if props:
        tag["Properties"] = (TAG_COMPOUND, {k: (TAG_STRING, v) for k, v in props})
    return tag


def sanitize_block_entity(
    be: dict[str, tuple[int, object]],
    *,
    keep_items: bool,
) -> dict[str, tuple[int, object]]:
    cleaned = dict(be)
    cleaned.pop("keepPacked", None)
    be_id = str(cleaned["id"][1])
    if not keep_items and be_id in CONTAINER_IDS:
        cleaned["Items"] = (TAG_LIST, (TAG_COMPOUND, []))
    return cleaned


def convert(source_world: Path, output_path: Path, *, keep_items: bool = False) -> dict:
    region_dir = source_world / "dimensions/minecraft/overworld/region"
    if not region_dir.is_dir():
        raise FileNotFoundError(f"Region directory not found: {region_dir}")

    palette: list[tuple[str, tuple[tuple[str, str], ...]]] = [("minecraft:air", ())]
    palette_ids: dict[tuple[str, tuple[tuple[str, str], ...]], int] = {("minecraft:air", ()): 0}

    def intern(key: tuple[str, tuple[tuple[str, str], ...]]) -> int:
        idx = palette_ids.get(key)
        if idx is None:
            idx = len(palette)
            palette_ids[key] = idx
            palette.append(key)
        return idx

    seen_chunks: set[tuple[int, int]] = set()
    section_records: list[tuple[int, int, int, int | None, list[int] | None]] = []
    block_entities: list[dict[str, tuple[int, object]]] = []
    block_lookup: dict[tuple[int, int, int], str] = {}
    check_coords = {
        (0, 44, 168), (0, 45, 168), (0, 46, 168),  # spawn
        (0, 88, 28), (0, 89, 28), (0, 90, 28),      # L035 sanctuary
        (0, 132, 28), (0, 133, 28), (0, 134, 28),   # L070 sanctuary
        (0, 176, 28), (0, 177, 28), (0, 178, 28),   # L105 sanctuary
        (0, 220, 28), (0, 221, 28), (0, 222, 28),   # L140 sanctuary
        (0, 264, 28), (0, 265, 28), (0, 266, 28),   # L175 sanctuary
        (0, 296, 28), (0, 297, 28), (0, 298, 28),   # L200 throne sanctuary
    }

    total_non_air = 0
    actual_min_y = 999
    actual_max_y = -999
    stripped_items = 0
    be_counts: dict[str, int] = {}

    for mca in sorted(region_dir.glob("*.mca")):
        data = mca.read_bytes()
        for slot in range(1024):
            loc = struct.unpack_from(">I", data, slot * 4)[0]
            if loc == 0:
                continue
            off = (loc >> 8) * 4096
            length = struct.unpack_from(">I", data, off)[0]
            comp = data[off + 4]
            payload = data[off + 5:off + 4 + length]
            if comp == 2:
                raw = zlib.decompress(payload)
            elif comp == 1:
                raw = gzip.decompress(payload)
            else:
                raise ValueError(f"Unsupported chunk compression {comp} in {mca.name}")

            chunk = read_nbt(raw)
            cx = int(chunk["xPos"][1])
            cz = int(chunk["zPos"][1])
            if not (MIN_CX <= cx <= MAX_CX and MIN_CZ <= cz <= MAX_CZ):
                continue
            seen_chunks.add((cx, cz))

            chunk_non_air_positions: set[tuple[int, int, int]] = set()
            for sec in chunk["sections"][1][1]:
                cy = int(sec["Y"][1])
                if cy < MIN_CY or cy > MAX_CY or "block_states" not in sec:
                    continue
                bs = sec["block_states"][1]
                local_pal = [state_key(entry) for entry in bs["palette"][1][1]]
                if len(local_pal) == 1:
                    only_key = local_pal[0]
                    if only_key[0] == "minecraft:air":
                        continue
                    gid = intern(only_key)
                    total_non_air += 4096
                    actual_min_y = min(actual_min_y, cy * 16)
                    actual_max_y = max(actual_max_y, cy * 16 + 15)
                    section_records.append((cx, cy, cz, gid, None))
                    for ly in range(16):
                        wy = cy * 16 + ly
                        for lz in range(16):
                            wz = cz * 16 + lz
                            for lx in range(16):
                                wx = cx * 16 + lx
                                if (wx, wy, wz) in check_coords:
                                    block_lookup[(wx, wy, wz)] = only_key[0]
                    continue

                local_to_global = [0 if k[0] == "minecraft:air" else intern(k) for k in local_pal]
                bits = max(4, (len(local_pal) - 1).bit_length())
                per_long = 64 // bits
                mask = (1 << bits) - 1
                longs = bs["data"][1]

                indices = [0] * 4096
                non_air_here = 0
                first_id = -1
                uniform = True
                for i in range(4096):
                    li = i // per_long
                    bi = (i % per_long) * bits
                    val = ((longs[li] & 0xFFFFFFFFFFFFFFFF) >> bi) & mask
                    gid = local_to_global[val]
                    indices[i] = gid
                    if first_id < 0:
                        first_id = gid
                    elif gid != first_id:
                        uniform = False
                    if gid != 0:
                        non_air_here += 1
                        ly = i >> 8
                        lz = (i >> 4) & 15
                        lx = i & 15
                        wy = cy * 16 + ly
                        if wy < actual_min_y:
                            actual_min_y = wy
                        if wy > actual_max_y:
                            actual_max_y = wy
                        wx = cx * 16 + lx
                        wz = cz * 16 + lz
                        if (wx, wy, wz) in check_coords:
                            block_lookup[(wx, wy, wz)] = palette[gid][0]
                        chunk_non_air_positions.add((wx, wy, wz))
                if non_air_here == 0:
                    continue
                total_non_air += non_air_here
                if uniform:
                    section_records.append((cx, cy, cz, first_id, None))
                else:
                    section_records.append((cx, cy, cz, None, indices))

            for be in chunk.get("block_entities", (TAG_LIST, (TAG_COMPOUND, [])))[1][1]:
                bx, by, bz = int(be["x"][1]), int(be["y"][1]), int(be["z"][1])
                if not (MIN_X <= bx <= MAX_X and MIN_Y <= by <= MAX_Y and MIN_Z <= bz <= MAX_Z):
                    continue
                if (bx, by, bz) not in chunk_non_air_positions:
                    continue
                be_id = str(be["id"][1])
                if not keep_items and be_id in CONTAINER_IDS and "Items" in be:
                    stripped_items += len(be["Items"][1][1])
                cleaned = sanitize_block_entity(be, keep_items=keep_items)
                block_entities.append(cleaned)
                be_counts[be_id] = be_counts.get(be_id, 0) + 1

    if len(seen_chunks) != EXPECTED_CHUNKS:
        raise RuntimeError(f"Expected {EXPECTED_CHUNKS} core chunks, found {len(seen_chunks)}")
    if actual_min_y < MIN_Y or actual_max_y > MAX_Y:
        raise RuntimeError(f"Y range {actual_min_y}..{actual_max_y} outside declared bounds {MIN_Y}..{MAX_Y}")

    # Verify spawn and sanctuary anchors are intact and walkable
    for label, (ax, ay, az) in (
        ("spawn", (0, 45, 168)),
        ("L035", (0, 89, 28)),
        ("L070", (0, 133, 28)),
        ("L105", (0, 177, 28)),
        ("L140", (0, 221, 28)),
        ("L175", (0, 265, 28)),
        ("L200", (0, 297, 28)),
    ):
        floor_block = block_lookup.get((ax, ay - 1, az), "minecraft:air")
        feet_block = block_lookup.get((ax, ay, az), "minecraft:air")
        head_block = block_lookup.get((ax, ay + 1, az), "minecraft:air")
        if floor_block in PASSABLE_BLOCKS or feet_block not in PASSABLE_BLOCKS or head_block not in PASSABLE_BLOCKS:
            raise RuntimeError(f"Anchor {label} at {(ax, ay, az)} invalid: below={floor_block}, feet={feet_block}, head={head_block}")

    use_byte_encoding = len(palette) <= 256
    section_records.sort(key=lambda item: (item[0], item[2], item[1]))
    encoded_sections: list[dict[str, tuple[int, object]]] = []
    for cx, cy, cz, uniform_id, indices in section_records:
        sec_tag: dict[str, tuple[int, object]] = {
            "cx": (TAG_BYTE, cx),
            "cy": (TAG_BYTE, cy),
            "cz": (TAG_BYTE, cz),
        }
        if uniform_id is not None:
            sec_tag["uniform"] = (TAG_SHORT, uniform_id)
        else:
            assert indices is not None
            if use_byte_encoding:
                sec_tag["blocks"] = (TAG_BYTE_ARRAY, bytes(indices))
            else:
                sec_tag["blocksInt"] = (TAG_INT_ARRAY, indices)
        encoded_sections.append(sec_tag)

    block_entities.sort(key=lambda be: (int(be["x"][1]), int(be["y"][1]), int(be["z"][1])))
    root: dict[str, tuple[int, object]] = {
        "version": (TAG_INT, FORMAT_VERSION),
        "minX": (TAG_INT, MIN_X),
        "minY": (TAG_INT, MIN_Y),
        "minZ": (TAG_INT, MIN_Z),
        "maxX": (TAG_INT, MAX_X),
        "maxY": (TAG_INT, MAX_Y),
        "maxZ": (TAG_INT, MAX_Z),
        "palette": (TAG_LIST, (TAG_COMPOUND, [state_to_nbt(k) for k in palette])),
        "sections": (TAG_LIST, (TAG_COMPOUND, encoded_sections)),
        "blockEntities": (TAG_LIST, (TAG_COMPOUND, block_entities)),
    }
    write_nbt_gzip(output_path, root)

    return {
        "output": str(output_path),
        "bytes": output_path.stat().st_size,
        "chunks": len(seen_chunks),
        "sections": len(encoded_sections),
        "palette": len(palette),
        "non_air_blocks": total_non_air,
        "y_range": [actual_min_y, actual_max_y],
        "block_entities": be_counts,
        "stripped_item_stacks": stripped_items,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Build Writhing Castle dungeon_map NBT resource.")
    parser.add_argument("--source", type=Path, default=DEFAULT_SOURCE, help="Master MC 26.2 world directory")
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT, help="Output gzip NBT path")
    parser.add_argument("--keep-items", action="store_true", help="Keep demonstration items inside chests/barrels")
    args = parser.parse_args()
    summary = convert(args.source, args.output, keep_items=args.keep_items)
    print("Built writhing_castle.nbt:", summary)


if __name__ == "__main__":
    main()

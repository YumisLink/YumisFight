"""Build browser-playable APNG previews from the mod's Minecraft item animations."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/yumis_dungeon_and_fight/textures/item"
SEED = Path(__file__).resolve().parent / "seed/catalog.json"
OUTPUT = Path(__file__).resolve().parent / "public/animations"


def build(output: Path = OUTPUT) -> int:
    catalog = json.loads(SEED.read_text(encoding="utf-8"))
    items_by_path = {item["id"].split(":", 1)[1]: item for item in catalog["items"]
                     if item["id"].startswith("yumis_dungeon_and_fight:")}
    output.mkdir(parents=True, exist_ok=True)
    manifest: dict[str, dict] = {}
    for sprite in sorted(ASSETS.glob("*.png.mcmeta")):
        asset_id = sprite.name.removesuffix(".png.mcmeta")
        item = items_by_path.get(asset_id)
        if item is None:
            continue
        metadata = json.loads(sprite.read_text(encoding="utf-8")).get("animation")
        if not isinstance(metadata, dict):
            raise ValueError(f"Missing animation definition: {sprite.name}")
        sheet = Image.open(ASSETS / f"{asset_id}.png").convert("RGBA")
        default_size = min(sheet.width, sheet.height)
        width = int(metadata.get("width", default_size))
        height = int(metadata.get("height", default_size))
        if width <= 0 or height <= 0 or sheet.width % width or sheet.height % height:
            raise ValueError(f"Invalid frame dimensions: {sprite.name}")
        if metadata.get("interpolate", False):
            raise ValueError(f"Interpolated animation needs explicit preview frames: {sprite.name}")
        columns, rows = sheet.width // width, sheet.height // height
        total_frames = columns * rows
        raw_frames = metadata.get("frames")
        if raw_frames is None:
            raw_frames = list(range(total_frames))
        if not isinstance(raw_frames, list) or not raw_frames:
            raise ValueError(f"Invalid animation frame list: {sprite.name}")
        default_ticks = int(metadata.get("frametime", 1))
        frames: list[Image.Image] = []
        durations: list[int] = []
        for raw in raw_frames:
            index = int(raw.get("index", 0)) if isinstance(raw, dict) else int(raw)
            ticks = int(raw.get("time", default_ticks)) if isinstance(raw, dict) else default_ticks
            if index < 0 or index >= total_frames or ticks < 1:
                raise ValueError(f"Invalid animation frame index or duration: {sprite.name}")
            x, y = (index % columns) * width, (index // columns) * height
            frames.append(sheet.crop((x, y, x + width, y + height))
                          .resize((width * 4, height * 4), Image.Resampling.NEAREST))
            durations.append(ticks * 50)
        if len(frames) < 2:
            continue
        if len(set(durations)) != 1:
            raise ValueError(f"CSS preview requires uniform frame times: {sprite.name}")
        key = hashlib.sha256((item["id"] + "\0" + item["variant"]).encode("utf-8")).hexdigest()
        target = output / f"{key}.png"
        temporary = output / f".{key}.png.tmp"
        frames[0].save(temporary, format="PNG", save_all=True, append_images=frames[1:],
                       duration=durations, loop=0, optimize=False, disposal=0, blend=0)
        temporary.replace(target)
        sheet_target = output / f"{key}-sheet.png"
        sheet_temporary = output / f".{key}-sheet.png.tmp"
        sheet = Image.new("RGBA", (frames[0].width, frames[0].height * len(frames)))
        for index, frame in enumerate(frames):
            sheet.alpha_composite(frame, (0, index * frame.height))
        sheet.save(sheet_temporary, format="PNG", optimize=False)
        sheet_temporary.replace(sheet_target)
        with Image.open(target) as preview:
            encoded_frames = preview.n_frames
        manifest[key] = {
            "src": f"./animations/{key}.png",
            "sheet": f"./animations/{key}-sheet.png",
            "frames": encoded_frames,
            "sourceFrames": len(frames),
            "frameDurationMs": durations[0],
            "durationMs": sum(durations),
            "name": item["name"],
        }
    if not manifest:
        raise ValueError("No catalog items have resolvable texture animations")
    manifest_path = output / "manifest.json"
    temporary_manifest = output / ".manifest.json.tmp"
    temporary_manifest.write_text(json.dumps({"items": manifest}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary_manifest.replace(manifest_path)
    return len(manifest)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=OUTPUT)
    args = parser.parse_args()
    print(f"Generated {build(args.output)} animated item previews.")

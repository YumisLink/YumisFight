"""Create the deployable catalog from an explicitly captured client snapshot.

This reads resource files and the local Minecraft client JAR, never modifies them,
and refuses to produce a catalog if any candidate cannot be resolved safely.
"""
from __future__ import annotations

import argparse
import hashlib
import io
import json
import sys
import zipfile
from pathlib import Path

from PIL import Image, ImageChops

CONTEXTS = ("none", "thirdperson_lefthand", "thirdperson_righthand", "firstperson_lefthand", "firstperson_righthand", "head", "gui", "ground", "fixed", "on_shelf")
PLANAR = {"minecraft:item/generated", "minecraft:item/handheld", "minecraft:item/handheld_rod", "minecraft:builtin/generated"}


class CatalogError(Exception):
    pass


def resource_name(identifier: str, folder: str, suffix: str) -> str:
    namespace, separator, path = identifier.partition(":")
    if not separator:
        namespace, path = "minecraft", namespace
    if not namespace or not path or ".." in path.split("/") or path.startswith("/"):
        raise CatalogError(f"资源标识无效: {identifier}")
    return f"assets/{namespace}/{folder}/{path}{suffix}"


class Assets:
    def __init__(self, mod_jar: Path, client_jar: Path):
        self.mod = zipfile.ZipFile(mod_jar)
        self.client = zipfile.ZipFile(client_jar)

    def read(self, path: str, optional: bool = False) -> bytes | None:
        for archive in (self.mod, self.client):
            try:
                return archive.read(path)
            except KeyError:
                pass
        if optional:
            return None
        raise CatalogError(f"找不到资源: {path}")

    def json(self, path: str) -> dict:
        try:
            value = json.loads(self.read(path).decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError) as exc:
            raise CatalogError(f"资源 JSON 无效: {path}: {exc}") from exc
        if not isinstance(value, dict):
            raise CatalogError(f"资源 JSON 不是对象: {path}")
        return value

    def model(self, identifier: str, seen: frozenset[str] = frozenset()) -> tuple[bool, dict[str, str]]:
        if ":" not in identifier:
            identifier = "minecraft:" + identifier
        if identifier in PLANAR:
            return False, {}
        if identifier in seen:
            raise CatalogError(f"模型继承循环: {identifier}")
        model = self.json(resource_name(identifier, "models", ".json"))
        if model.get("loader") or model.get("elements"):
            return True, {}
        parent = model.get("parent")
        if not isinstance(parent, str):
            raise CatalogError(f"无法判断模型类型: {identifier}")
        is_3d, inherited = self.model(parent, seen | {identifier})
        textures = dict(inherited)
        own = model.get("textures", {})
        if not isinstance(own, dict):
            raise CatalogError(f"模型贴图无效: {identifier}")
        textures.update(own)
        return is_3d, textures

    def texture(self, identifier: str) -> Image.Image:
        path = resource_name(identifier, "textures", ".png")
        try:
            image = Image.open(io.BytesIO(self.read(path))).convert("RGBA")
        except Exception as exc:
            raise CatalogError(f"无法读取贴图 {path}: {exc}") from exc
        metadata = self.read(path + ".mcmeta", optional=True)
        if metadata:
            try:
                animation = json.loads(metadata.decode("utf-8")).get("animation")
                if animation is not None:
                    default_size = min(image.width, image.height)
                    width = int(animation.get("width", default_size))
                    height = int(animation.get("height", default_size))
                    frames = animation.get("frames", [0])
                    first = frames[0] if frames else 0
                    index = int(first.get("index", 0) if isinstance(first, dict) else first)
                    if width <= 0 or height <= 0 or image.width % width or image.height % height or index < 0:
                        raise ValueError("动画帧尺寸无效")
                    columns = image.width // width
                    left, top = index % columns * width, index // columns * height
                    if top + height > image.height:
                        raise ValueError("动画帧超出贴图")
                    image = image.crop((left, top, left + width, top + height))
            except (ValueError, TypeError, json.JSONDecodeError, UnicodeDecodeError) as exc:
                raise CatalogError(f"动画信息无效 {path}: {exc}") from exc
        return image


def select_case(node: dict, context: str, selectors: dict) -> list[dict]:
    property_name = node.get("property")
    if property_name == "minecraft:display_context":
        values = [context]
    elif property_name == "yumis_dungeon_and_fight:material":
        values = [selectors.get("material", "")]
    elif property_name == "yumis_dungeon_and_fight:katana_style":
        style = selectors.get("style", "")
        values = [style]
        if context != "gui" and (style == "skyspine" or style.startswith("lightsaber_")):
            values.append(style + "_local_glow")
    elif property_name == "minecraft:custom_model_data":
        index = node.get("index", 0)
        strings = selectors.get("customModelData", [])
        if not isinstance(index, int) or index < 0 or not isinstance(strings, list):
            raise CatalogError("自定义模型选择器无效")
        values = [strings[index] if index < len(strings) else ""]
    else:
        raise CatalogError(f"无法判断模型选择器: {property_name}")
    result = []
    for value in values:
        choice = None
        for case in node.get("cases", []):
            when = case.get("when")
            if value in (when if isinstance(when, list) else [when]):
                choice = case.get("model")
                break
        choice = choice if choice is not None else node.get("fallback")
        if not isinstance(choice, dict):
            raise CatalogError(f"模型分支不存在: {property_name}={value}")
        result.append(choice)
    return result


def leaves(node: dict, context: str, selectors: dict) -> list[tuple[str, list]]:
    kind = node.get("type")
    if kind == "minecraft:model":
        model = node.get("model")
        if not isinstance(model, str):
            raise CatalogError("模型引用缺失")
        return [(model, node.get("tints", []))]
    if kind == "minecraft:special":
        return [("<special-3d>", [])]
    if kind == "minecraft:select":
        children = select_case(node, context, selectors)
    elif kind == "minecraft:composite":
        children = node.get("models", [])
    else:
        raise CatalogError(f"无法判断物品模型节点: {kind}")
    return [leaf for child in children for leaf in leaves(child, context, selectors)]


def texture_reference(textures: dict, key: str) -> str:
    value = textures.get(key)
    visited = set()
    while isinstance(value, str) and value.startswith("#"):
        if value in visited:
            raise CatalogError(f"贴图引用循环: {value}")
        visited.add(value)
        value = textures.get(value[1:])
    if not isinstance(value, str) or not value:
        raise CatalogError(f"模型贴图缺失: {key}")
    return value


def icon(assets: Assets, gui_leaves: list[tuple[str, list]]) -> tuple[bytes, list[str]]:
    layers: list[Image.Image] = []
    sources: list[str] = []
    for reference, tints in gui_leaves:
        is_3d, textures = assets.model(reference)
        if is_3d:
            raise CatalogError(f"二维图标意外引用了三维模型: {reference}")
        layer_keys = sorted((key for key in textures if key.startswith("layer") and key[5:].isdigit()), key=lambda x: int(x[5:]))
        if not layer_keys:
            raise CatalogError(f"二维模型没有贴图层: {reference}")
        for key in layer_keys:
            texture = texture_reference(textures, key)
            image = assets.texture(texture)
            index = int(key[5:])
            if index < len(tints):
                tint = tints[index]
                if tint.get("type") != "minecraft:constant" or not isinstance(tint.get("value"), int):
                    raise CatalogError(f"无法确定贴图颜色: {reference}")
                color = tint["value"] & 0xffffff
                rgb = ImageChops.multiply(image.convert("RGB"), Image.new("RGB", image.size, (color >> 16, color >> 8 & 255, color & 255)))
                rgb.putalpha(image.getchannel("A"))
                image = rgb
            layers.append(image)
            sources.append(texture)
    if not layers:
        raise CatalogError("物品没有可显示的二维贴图")
    size = (max(image.width for image in layers), max(image.height for image in layers))
    result = Image.new("RGBA", size)
    for image in layers:
        if image.size != size:
            image = image.resize(size, Image.Resampling.NEAREST)
        result.alpha_composite(image)
    output = io.BytesIO()
    result.save(output, format="PNG")
    return output.getvalue(), sources


def build(raw_path: Path, mod_jar: Path, client_jar: Path, output: Path) -> None:
    raw = json.loads(raw_path.read_text(encoding="utf-8"))
    if raw.get("schema") != 1 or not isinstance(raw.get("items"), list) or not raw["items"] or not isinstance(raw.get("categories"), list):
        raise CatalogError("客户端物品快照格式无效")
    if raw.get("errors"):
        raise CatalogError("客户端采集存在未解决错误:\n" + json.dumps(raw["errors"], ensure_ascii=False, indent=2))
    with mod_jar.open("rb") as source:
        actual_hash = hashlib.file_digest(source, "sha256").hexdigest()
    if raw.get("modSha256") != actual_hash:
        raise CatalogError("采集结果与指定模组 JAR 的 SHA-256 不一致")
    if output.exists():
        raise CatalogError(f"输出目录已存在，未覆盖: {output}")
    assets = Assets(mod_jar, client_jar)
    records, icons, problems = [], {}, []
    excluded = 0
    for item in raw["items"]:
        identity = f'{item.get("id", "?")} / {item.get("variant", "")}'
        try:
            if item.get("modelError"):
                raise CatalogError(f'物品模型无法唯一确定: {item["modelError"]}')
            definition = assets.json(resource_name(item["model"], "items", ".json"))["model"]
            selectors = item.get("selectors", {})
            references = [leaf for context in CONTEXTS for leaf in leaves(definition, context, selectors)]
            spatial = False
            for reference, _ in references:
                if reference == "<special-3d>" or assets.model(reference)[0]:
                    spatial = True
                    break
            if spatial:
                excluded += 1
                continue
            if item.get("tooltipError"):
                raise CatalogError(f'游戏文字采集失败: {item["tooltipError"]}')
            image, sources = icon(assets, leaves(definition, "gui", selectors))
            identifier, variant = item["id"], item["variant"]
            key = hashlib.sha256((identifier + "\0" + variant).encode("utf-8")).hexdigest()
            if key in icons:
                raise CatalogError("物品 ID 和变体标识重复")
            icons[key] = image
            records.append({"key": key, "id": identifier, "variant": variant, "name": item["name"], "description": item["description"], "categories": item["categories"], "source": {"itemModel": item["model"], "textures": sources}})
        except (CatalogError, KeyError, TypeError, ValueError) as exc:
            problems.append(f"{identity}: {exc}")
    if problems:
        raise CatalogError("资源尚未完整解析，未生成上线数据:\n" + "\n".join(problems))
    if not records:
        raise CatalogError("没有符合条件的物品，未生成上线数据")
    output.mkdir(parents=True)
    (output / "icons").mkdir()
    for key, image in icons.items():
        (output / "icons" / f"{key}.png").write_bytes(image)
    catalog = {"schema": 1, "categories": raw["categories"], "items": records, "errors": []}
    (output / "catalog.json").write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"已生成 {len(records)} 个二维物品，排除 {excluded} 个三维物品。")


def main() -> None:
    parser = argparse.ArgumentParser(description="从客户端采集结果生成物品网站的正式数据")
    parser.add_argument("--input", type=Path, required=True, help="隔离客户端的 item-catalog-raw.json")
    parser.add_argument("--mod-jar", type=Path, required=True, help="采集客户端实际使用的模组 JAR")
    parser.add_argument("--minecraft-client-jar", type=Path, required=True, help="本机 Minecraft 客户端 JAR")
    parser.add_argument("--output", type=Path, required=True, help="尚不存在的正式网站数据目录")
    args = parser.parse_args()
    try:
        build(args.input, args.mod_jar, args.minecraft_client_jar, args.output)
    except (CatalogError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1) from exc


if __name__ == "__main__":
    main()

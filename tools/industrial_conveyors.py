"""Production source for the two passive belts; run alone without regenerating other industry art.

Texture source: draw_industrial_conveyors.lua / industrial_conveyors.aseprite.
Gray iron and funnel pixels reuse the current Minecraft anvil and hopper textures unchanged.
"""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets' / NS
DATA = RES / 'data' / NS
BELTS = {'conveyor_belt': ('传送带', 'Conveyor Belt'),
         'hopper_conveyor_belt': ('漏斗传送带', 'Hopper Conveyor Belt'),
         'titanium_conveyor_belt': ('钛传送带', 'Titanium Conveyor Belt')}
CONVEYOR_REPLACEABLE = [
    '#minecraft:small_flowers',
    'minecraft:sunflower', 'minecraft:lilac', 'minecraft:peony', 'minecraft:rose_bush',
    'minecraft:pitcher_plant', 'minecraft:pink_petals', 'minecraft:wildflowers',
    'minecraft:short_grass', 'minecraft:tall_grass', 'minecraft:fern', 'minecraft:large_fern',
    'minecraft:short_dry_grass', 'minecraft:tall_dry_grass', 'minecraft:dead_bush',
    'minecraft:bush', 'minecraft:firefly_bush',
    f'{NS}:gray_pebbles', f'{NS}:dark_pebbles', f'{NS}:ground_branches', f'{NS}:magic_grass',
]
BODY_DROP_PIXELS = 3
BELT_TOP_PIXELS = 8 - BODY_DROP_PIXELS
ROLLER_CENTER_PIXELS = 5.5 - BODY_DROP_PIXELS
ITEM_CLEARANCE_PIXELS = 1


def register_catalog(items, content):
    for key, titles in BELTS.items():
        tier = 3 if key == 'titanium_conveyor_belt' else 1
        items[NS + ':' + key] = tier
        content[key] = {'kind': 'block', 'tier': tier, 'title': titles[0]}


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def model(hopper=False, slope='flat', titanium=False):
    def box(lo, hi, texture):
        return {'from': [lo[0], lo[1] - BODY_DROP_PIXELS, lo[2]],
                'to': [hi[0], hi[1] - BODY_DROP_PIXELS, hi[2]],
                'faces': {face: {'texture': '#' + texture}
                for face in ('up', 'down', 'north', 'south', 'east', 'west')}}
    elements = [box([0, 7.5, 0], [16, 8, 16], 'belt'),
                box([0, 3, 0], [1, 7.5, 16], 'metal'),
                box([15, 3, 0], [16, 7.5, 16], 'metal')]
    for x in (1, 12):
        for z in (1, 12):
            elements.append(box([x, 4, z], [x + 3, 6, z + 3], 'bearing'))
    # Narrow shafts remain inside the drums rendered in-world, leaving their rotation visible.
    for z in (2, 14):
        elements.append(box([1, 5, z - .5], [15, 6, z + .5], 'metal'))
    frame_count = len(elements)
    if hopper:
        # Keep the shortened outlet inside the compact frame rather than below its supporting block.
        elements += [box([4, 5, 4], [12, 7.5, 12], 'hopper'),
                     box([5, BODY_DROP_PIXELS + .75, 5], [11, 5, 11], 'hopper'),
                     box([6.5, BODY_DROP_PIXELS, 6.5], [9.5, BODY_DROP_PIXELS + .75, 9.5], 'hopper')]
    if slope != 'flat':
        inclined = []
        for index, element in enumerate(elements):
            lo, hi = element['from'], element['to']
            if hopper and index >= frame_count + 1:
                # The funnel still empties below the block, rather than into the raised belt.
                if index == frame_count + 1:
                    element['to'][1] += 8
                inclined.append(element)
                continue
            if index == 0:
                lo[1], hi[1] = 3.5, 4.4  # Keep the fixed chassis below the smooth moving surface.
            z = lo[2]
            while z < hi[2]:
                end = min(z + 1, hi[2])
                rise = 16 - (z + end) / 2 if slope == 'up' else (z + end) / 2
                # Use the unraised source UVs: implicit UVs above y=16 would leave the atlas sprite.
                uv = {'up': [lo[0], z, hi[0], end],
                      'down': [lo[0], 16 - end, hi[0], 16 - z],
                      'north': [16 - hi[0], 16 - hi[1], 16 - lo[0], 16 - lo[1]],
                      'south': [lo[0], 16 - hi[1], hi[0], 16 - lo[1]],
                      'west': [z, 16 - hi[1], end, 16 - lo[1]],
                      'east': [16 - end, 16 - hi[1], 16 - z, 16 - lo[1]]}
                inclined.append({'from': [lo[0], lo[1] + rise, z],
                                 'to': [hi[0], hi[1] + rise, end],
                                 'faces': {face: {**value, 'uv': uv[face]} for face, value in element['faces'].items()}})
                z = end
        elements = inclined
    return {'ambientocclusion': False, 'render_type': 'minecraft:cutout',
            'textures': {'particle': NS + ':block/conveyor_surface',
                         'belt': NS + ':block/conveyor_surface',
                         'metal': NS + ':block/titanium_conveyor_frame' if titanium else 'minecraft:block/anvil',
                         'bearing': NS + ':block/titanium_conveyor_frame' if titanium else 'minecraft:block/iron_block',
                         'hopper': 'minecraft:block/hopper_outside'},
            'elements': elements,
            'display': {'gui': {'rotation': [30, 225, 0], 'translation': [0, 1, 0], 'scale': [.7, .7, .7]},
                        'ground': {'translation': [0, 3, 0], 'scale': [.3, .3, .3]}}}


def generate(models_only=False):
    write(DATA / 'tags/block/conveyor_replaceable.json', {'replace': False, 'values': CONVEYOR_REPLACEABLE})
    geometry = ROOT / 'src/main/java/cn/yumis/fight/modules/industry/platform/ConveyorGeometry.java'
    geometry.write_text(
        '// Generated by tools/industrial_conveyors.py together with both belt models.\n'
        'package cn.yumis.fight.modules.industry.platform;\n\n'
        '/** Shared model, collision, passenger and cargo placement dimensions. */\n'
        'public final class ConveyorGeometry {\n'
        f'    public static final double HEIGHT={BELT_TOP_PIXELS}/16.0;\n'
        '    public static final double SURFACE_HEIGHT=HEIGHT+.0005;\n'
        f'    public static final double ROLLER_HEIGHT={ROLLER_CENTER_PIXELS}/16.0;\n'
        f'    public static final double ITEM_CLEARANCE={ITEM_CLEARANCE_PIXELS}/16.0;\n'
        '    private ConveyorGeometry(){}\n'
        '}\n', encoding='utf-8')
    if not models_only:
        from industrial_technology import shaped
        shaped('conveyor_belt', ['   ', ' L ', 'KIK'],
               {'L': 'minecraft:leather', 'K': 'minecraft:dried_kelp', 'I': 'minecraft:iron_ingot'}, count=8)
        shaped('hopper_conveyor_belt', ['BBB', 'BHB', 'BBB'],
               {'B': 'conveyor_belt', 'H': 'minecraft:hopper'}, count=8)
        shaped('titanium_conveyor_belt', ['BBB', 'BCB', 'BBB'],
               {'B': 'conveyor_belt', 'C': 'industrial_upgrade_3'}, count=8)
    for key in BELTS:
        write(ASSETS / f'models/block/{key}.json', model(key == 'hopper_conveyor_belt', titanium=key == 'titanium_conveyor_belt'))
        for slope in ('up', 'down'):
            write(ASSETS / f'models/block/{key}_{slope}.json', model(key == 'hopper_conveyor_belt', slope, key == 'titanium_conveyor_belt'))
        write(ASSETS / f'blockstates/{key}.json', {'variants': {
            'facing=' + facing + ',slope=' + slope: {'model': NS + ':block/' + key + ('' if slope == 'flat' else '_' + slope), 'y': angle}
            for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]
            for slope in ('flat', 'up', 'down')}})
        if models_only:
            continue
        write(ASSETS / f'items/{key}.json', {'model': {'type': 'minecraft:model', 'model': NS + ':block/' + key}})
        write(DATA / f'loot_table/blocks/{key}.json', {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + ':' + key}]}]})
    if models_only:
        return
    for index, locale in enumerate(('zh_cn', 'en_us')):
        path = ASSETS / f'lang/{locale}.json'
        language = json.loads(path.read_text(encoding='utf-8'))
        language.update({'block.' + NS + '.' + key: titles[index] for key, titles in BELTS.items()})
        write(path, language)
    path = RES / 'data/minecraft/tags/block/mineable/pickaxe.json'
    tag = json.loads(path.read_text(encoding='utf-8'))
    for key in BELTS:
        if NS + ':' + key not in tag['values']:
            tag['values'].append(NS + ':' + key)
    write(path, tag)
    path = ASSETS / 'industry/technology.json'
    technology = json.loads(path.read_text(encoding='utf-8'))
    register_catalog(technology['items'], technology['content'])
    technology['items']['minecraft:dried_kelp'] = 1
    write(path, technology)


if __name__ == '__main__':
    import sys
    generate('--models-only' in sys.argv)

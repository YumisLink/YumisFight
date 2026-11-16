"""Targeted creative-only industry resources; never generates survival recipes.

Run after the ordinary industrial generators. Artwork is edited/exported with
industrial_creative.lua; --minecraft-client prepares its vanilla barrel base.
"""
from pathlib import Path
import argparse
import copy
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
ASSETS = ROOT / 'src/main/resources/assets' / NS
DATA = ROOT / 'src/main/resources/data' / NS
NAMES = {
    'infinite_power_tower': ('无限电力塔', 'Infinite Power Tower'),
    'infinite_fluid_tank': ('无限液体储罐', 'Infinite Fluid Tank'),
    'infinite_item_barrel': ('无限物品桶', 'Infinite Item Barrel'),
}


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def own(path):
    return NS + ':' + path


def generate():
    for key, template in [('infinite_power_tower', 'mana_beacon_5'),
                          ('infinite_fluid_tank', 'fluid_tank_3')]:
        model = read(ASSETS / 'models/block' / (template + '.json'))
        model['textures'] = {'atlas': own('block/' + key), 'particle': own('block/' + key)}
        write(ASSETS / 'models/block' / (key + '.json'), model)
    write(ASSETS / 'models/block/infinite_item_barrel.json', {
        'parent': 'minecraft:block/barrel',
        'textures': {'side': own('block/infinite_item_barrel')},
    })

    tower_item = read(ASSETS / 'models/item/mana_beacon_t5.json')
    tower_item['textures']['atlas'] = own('block/infinite_power_tower')
    tower_item['textures']['particle'] = own('block/infinite_power_tower')
    write(ASSETS / 'models/item/infinite_power_tower.json', tower_item)
    for key in ['infinite_fluid_tank', 'infinite_item_barrel']:
        write(ASSETS / 'models/item' / (key + '.json'), {'parent': own('block/' + key)})

    for key in NAMES:
        model = {'model': own('block/' + key)}
        if key == 'infinite_fluid_tank':
            state = {'multipart': [{'apply': model}, {'apply': {'model': own('block/fluid_tank_glass')}}]}
            item = {'type': 'minecraft:composite', 'models': [
                {'type': 'minecraft:model', 'model': own('item/' + key)},
                {'type': 'minecraft:model', 'model': own('block/fluid_tank_glass')},
            ]}
        else:
            state = {'variants': {'': model}}
            item = {'type': 'minecraft:model', 'model': own('item/' + key)}
        write(ASSETS / 'blockstates' / (key + '.json'), state)
        write(ASSETS / 'items' / (key + '.json'), {'model': item})
        # No entity-data or inventory contents: every normal drop is a blank source.
        write(DATA / 'loot_table/blocks' / (key + '.json'), {
            'type': 'minecraft:block', 'pools': [{'rolls': 1,
                'conditions': [{'condition': 'minecraft:survives_explosion'}],
                'entries': [{'type': 'minecraft:item', 'name': own(key)}]}],
        })

    path = ASSETS / 'industry/technology.json'
    catalog = read(path)
    tower = copy.deepcopy(catalog['devices']['mana_beacon_t5'])
    tower.update(kind='infinite_power_tower', creative_only=True)
    tower.pop('upgrade_costs', None)
    catalog['devices']['infinite_power_tower'] = tower
    catalog['items'][own('infinite_power_tower')] = tower['technology']
    write(path, catalog)

    for language, index in [('zh_cn', 0), ('en_us', 1)]:
        path = ASSETS / 'lang' / (language + '.json')
        labels = read(path)
        labels.update({f'block.{NS}.{key}': names[index] for key, names in NAMES.items()})
        prefix = f'industry.{NS}.infinite.'
        labels[prefix + 'empty'] = ('尚未设置样本', 'No sample configured')[index]
        labels[prefix + 'sample'] = ('%s × ∞', '%s × ∞')[index]
        write(path, labels)


def prepare_art(client_jar):
    destination = ROOT / 'temp/creative-industry/art'
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(client_jar) as archive:
        (destination / 'barrel_side.png').write_bytes(archive.read('assets/minecraft/textures/block/barrel_side.png'))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--minecraft-client', type=Path)
    args = parser.parse_args()
    if args.minecraft_client:
        prepare_art(args.minecraft_client)
    generate()
    print('Generated the three creative-only industrial devices; no recipes changed.')

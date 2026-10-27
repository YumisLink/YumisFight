"""Authoritative industrial crafting catalog. Also usable without rebuilding unrelated assets."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets' / NS
RECIPES = RES / 'data' / NS / 'recipe/industrial'
MATERIALS = ['#c:ingots/copper', '#c:gems/diamond', '#c:ingots/titanium', '#c:ingots/terra', '#c:ingots/spacetime']
FAMILIES = {'mana_crusher': 'stonecutter', 'mana_furnace': 'furnace', 'mineral_generator': 'iron_pickaxe', 'currency_fabricator': 'dropper', 'mana_forge': 'blast_furnace', 'mana_beacon': 'amethyst_block'}
EXTRAS = {'coal_generator': (1, 'minecraft:smoker', '燃煤'), 'solar_collector': (1, 'minecraft:daylight_detector', '日曜'), 'turbine_generator': (2, 'minecraft:piston', '涡轮'), 'wind_generator': (2, 'minecraft:lightning_rod', '风能'), 'thermal_generator': (3, 'minecraft:blast_furnace', '温差'), 'nuclear_reactor': (4, NS+':nuclear_core', '核能'), 'rtg_generator': (5, NS+':rtg_core', 'RTG'), 'coolant_mixer': (2, 'minecraft:cauldron', None)}
LEGACY = [f'machine_frame_{i}' for i in range(1, 6)] + ['functional_upgrade_2', 'transfer_upgrade_3', 'harvest_upgrade_3']

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')

def shaped(name, pattern, key, output=None, guarded=False):
    write(RECIPES / (name+'.json'), {'type': NS+':machine_upgrade' if guarded else 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern, 'key': key, 'result': {'id': NS+':'+(output or name), 'count': 1}})

def ring(name, stage, center=None, guarded=False):
    key = {'C': NS+':energy_crystal_'+str(stage), 'M': MATERIALS[stage-1]}
    if center:
        key['X'] = center
    shaped(name, ['CMC', 'MXM' if center else 'M M', 'CMC'], key, guarded=guarded)

def generate():
    for old in LEGACY:
        (RECIPES / (old+'.json')).unlink(missing_ok=True)
        for folder in ['items', 'models/item', 'textures/item']:
            (ASSETS / folder / (old+('.png' if folder=='textures/item' else '.json'))).unlink(missing_ok=True)
    # The old zero-energy upgrade pseudo-recipes are superseded by component interactions.
    for family, component in FAMILIES.items():
        for tier in range(1, 6):
            name = f'{family}_t{tier}'
            ring(name, tier, 'minecraft:'+component if tier==1 else NS+f':{family}_t{tier-1}', tier>1)
            (RECIPES / ('upgrade_'+name+'.json')).unlink(missing_ok=True)
    for name, (stage, center, _) in EXTRAS.items():
        ring(name, stage, center)
    for family, center, final_stage in [('transfer_crystal', 'ender_pearl', 5), ('harvest_crystal', 'wheat_seeds', 4)]:
        for tier, stage in [(1, 1), (2, 2), (3, final_stage)]:
            ring(f'{family}_t{tier}', stage, 'minecraft:'+center if tier==1 else NS+f':{family}_t{tier-1}', tier>1)
            (RECIPES / f'upgrade_{family}_t{tier}.json').unlink(missing_ok=True)
    for stage in range(1, 6):
        ring(f'industrial_upgrade_{stage}', stage)
    shaped('nuclear_core', ['LCL'], {'L': '#c:storage_blocks/lead', 'C': 'minecraft:cauldron'})
    shaped('rtg_core', ['R', 'L'], {'R': 'minecraft:redstone_block', 'L': '#c:storage_blocks/lead'})
    shaped('energy_crystal_5', ['CCC', 'C C', 'CCC'], {'C': NS+':energy_crystal_4'})
    new_items = [f'industrial_upgrade_{i}' for i in range(1, 6)]+['nuclear_core', 'rtg_core']
    for name in new_items:
        write(ASSETS / 'items' / (name+'.json'), {'model': {'type': 'minecraft:model', 'model': NS+':item/'+name}})
        write(ASSETS / 'models/item' / (name+'.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': NS+':item/'+name}})
    for locale in ['zh_cn', 'en_us']:
        path = ASSETS / 'lang' / (locale+'.json')
        lang = json.loads(path.read_text(encoding='utf-8'))
        for old in LEGACY:
            lang.pop(f'item.{NS}.{old}', None)
        for name, (_, _, prefix) in EXTRAS.items():
            if prefix:
                lang[f'block.{NS}.{name}'] = prefix+'魔能产生器'
        for stage in range(1, 6):
            lang[f'item.{NS}.industrial_upgrade_{stage}'] = ['铜', '钻石', '钛', '泰拉石', '时空石'][stage-1]+'升级组件'
        lang[f'item.{NS}.nuclear_core'] = '核能核心'
        lang[f'item.{NS}.rtg_core'] = 'RTG核心'
        write(path, lang)
    print('Industrial crafting: 44 devices, five components, two cores and tier-five crystal.')

if __name__ == '__main__':
    generate()

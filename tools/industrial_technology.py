"""Industrial technology content: explicit crafting layouts, tiers and production data.

This is a production resource generator. It does not run tests or launch Minecraft.
"""
from pathlib import Path
import json
import copy
import math
import struct
import zlib
import shutil
from collections import Counter

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
RES = ROOT / 'src/main/resources'
DATA = RES / 'data' / NS
ASSETS = RES / 'assets' / NS
RECIPES = DATA / 'recipe/industrial'
PALETTES = [
    ['593431', 'be7954', 'efba81'], ['124a50', '42c8c1', 'c8ffff'],
    ['303c52', '90a6bd', 'e3ecf2'], ['131e29', '3e596e', '99b8ca'],
    ['291d45', '8b65bc', 'e4c6f4'], ['63420c', 'e5b83c', 'fff1a3'],
]
NAMES = ['普通', '稀有', '神器', '传说', '史诗', '神话']
MINING_ENERGY = dict(cobblestone=1, copper=50, iron=50, coal=50, lead=80,
    orichalcum=100, gold=400, redstone=512, lapis=512, amethyst=400,
    vanadium=800, diamond=1032, magic=1440, titanium=14418, molybdenum=13500,
    zirconium=12150, mithril=28800, niobium_tantalum=256000,
    adamantite=614400, uranium=518400, iridium=648000000)
METAL_TIERS = dict(copper=1, iron=1, lead=1, orichalcum=1, gold=2, vanadium=2,
    titanium=3, molybdenum=3, zirconium=3, mithril=3, niobium_tantalum=4,
    niobium=4, tantalum=4, adamantite=4, uranium=4, terra=4, spacetime=5, iridium=5)
PART_TIERS = dict(zircon_ceramic=3, vanadium_steel=2, molybdenum_vanadium_steel=3,
    tzm_alloy=3, orichalcum_alloy=2, niobium_titanium_alloy=4,
    mithril_niobium_alloy=4, adamantite_titanium_alloy=4, composite_conductor=4,
    henghui_alloy=5, lattice_substrate=4, mana_superconductor=5, basic_frame=1,
    reinforced_frame=2, heatproof_frame=3, precision_frame=4, mana_component=2,
    mithril_mana_component=4, control_component=4, superconducting_control=5,
    industrial_magic_stone=3, industrial_magic_stone_block=3, sulfide=1,
    nuclear_core=5, rtg_core=5, mana_wand=1, mana_storage_bag=1,
    storage_plugin=2, speed_plugin=2, magic_grass=1, magic_ore=2,
    energy_crystal_block_2=1, equipment_fabricator=1)
LIQUID_TIERS = dict(mana_fluid=1, concentrated_mana_fluid=2, coolant=3, alkahest=3, spent_solvent=3, blazing_fluid=3,
    ceruleum=2, shimmer=3, azoth=4, penduliquid=4, heavy_water=3, brine=2)
NEW_CONTENT = {
    'display_stand': ('block', 1, '展示台'),
    'magic_flower': ('block', 1, '魔力花'),
    'mana_fluid': ('fluid', 1, '魔力液'),
    'concentrated_mana_fluid': ('fluid', 2, '浓缩魔力液'),
    'stellar_alloy': ('item', 6, '恒星合金'),
    'confinement_lattice': ('item', 6, '约束晶格'),
    'fusion_ignition_core': ('item', 6, '恒星点火芯'),
    'uranium_concentrate': ('item', 4, '铀精矿'),
    'converted_uranium': ('item', 4, '铀转化料'),
    'enriched_uranium': ('item', 4, '富集铀料'),
    'depleted_uranium': ('item', 4, '贫化铀料'),
    'nuclear_pellet': ('item', 4, '核燃料芯块'),
    'zirconium_casing': ('item', 3, '锆质包壳'),
    'uranium_fuel_rod': ('item', 4, '铀燃料棒'),
    'spent_fuel_rod': ('item', 4, '乏燃料棒'),
    'cooled_spent_fuel': ('item', 4, '冷却乏燃料'),
    'curium_feed': ('item', 4, '含锔处理料'),
    'pu238_precursor': ('item', 4, '钚热源前驱组件'),
    'th228_precursor': ('item', 5, '钍热源前驱组件'),
    'irradiated_pu238': ('item', 4, '钚热源辐照组件'),
    'irradiated_th228': ('item', 5, '钍热源辐照组件'),
    'cooled_pu238': ('item', 4, '冷却钚热源组件'),
    'cooled_th228': ('item', 5, '冷却钍热源组件'),
    'pu238': ('item', 4, '钚-238'), 'cm244': ('item', 5, '锔-244'),
    'th228': ('item', 5, '钍-228'),
    'pu238_heat_source': ('item', 4, 'Pu-238密封热源'),
    'cm244_heat_source': ('item', 5, 'Cm-244密封热源'),
    'th228_heat_source': ('item', 5, 'Th-228密封热源'),
    'empty_fuel_capsule': ('item', 3, '空燃料胶囊'),
    'deuterium_capsule': ('item', 5, '氘燃料胶囊'),
    'tritium_capsule': ('item', 5, '氚燃料胶囊'),
    'fusion_fuel_capsule': ('item', 6, '聚变燃料胶囊'),
    'lithium_salt': ('item', 4, '锂盐'),
    'heavy_water': ('fluid', 3, '重水'), 'brine': ('fluid', 2, '盐水'),
}
for _t in range(1, 7):
    NEW_CONTENT[f'machine_casing_t{_t}'] = ('block', _t, f'T{_t}科技机外壳')
    if _t > 1:
        NEW_CONTENT[f'tech_block_t{_t}'] = ('block', _t, f'T{_t}科技方块')
NEW_CONTENT['industrial_upgrade_6'] = ('item', 6, 'T6科技核心')
NEW_MACHINES = {
    'heat_generator': (2, 2, 2, '热能发电机', 'thermal_generator'),
    'mana_concentrator': (2, 2, 2, '魔力液浓缩器', 'coolant_mixer'),
    'fluid_infuser': (2, 2, 2, '注液器', 'coolant_mixer'),
    'isotopic_separator': (4, 4, 5, '离心分离机', 'mineral_refinery'),
    'isotope_activator': (5, 5, 5, '同位素活化机', 'alchemical_reactor'),
    'forming_assembler': (3, 3, 5, '成型装配机', 'currency_fabricator'),
    'thermal_distiller': (3, 3, 4, '热力蒸馏设备', 'alchemical_reactor'),
    'artificial_star': (5, 6, 6, '人造恒星', 'nuclear_reactor'),
}


def own(key):
    return key if ':' in key or key.startswith('#') else NS + ':' + key


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def png_chunk(key, data):
    return struct.pack('>I', len(data)) + key + data + struct.pack('>I', zlib.crc32(key + data) & 0xffffffff)


def recolor_atlas(path, grade):
    """Recolor code-authored atlas quadrants, preserving functional gems and animation frames."""
    data = path.read_bytes()
    chunks, pos = [], 8
    while pos < len(data):
        size = struct.unpack('>I', data[pos:pos+4])[0]
        chunks.append((data[pos+4:pos+8], data[pos+8:pos+8+size]))
        pos += size + 12
    stamp = ('industrial-palette\0v1-' + str(grade) + '-' + '-'.join(PALETTES[grade-1])).encode()
    if (b'tEXt', stamp) in chunks:
        return
    header = next(value for key, value in chunks if key == b'IHDR')
    width, height, depth, mode, _, _, interlace = struct.unpack('>IIBBBBB', header)
    if depth != 8 or mode != 6 or interlace:
        raise ValueError('RGBA8 atlas required: ' + str(path))
    raw = zlib.decompress(b''.join(value for key, value in chunks if key == b'IDAT'))
    rows, previous, pos = [], [0] * (width*4), 0
    for y in range(height):
        flag, row = raw[pos], list(raw[pos+1:pos+1+width*4]);pos += width*4+1
        for x in range(width*4):
            a = row[x-4] if x >= 4 else 0;b = previous[x];c = previous[x-4] if x >= 4 else 0
            if flag == 1:value = a
            elif flag == 2:value = b
            elif flag == 3:value = (a+b)//2
            elif flag == 4:
                p = a+b-c;distances = [abs(p-a), abs(p-b), abs(p-c)]
                value = [a,b,c][distances.index(min(distances))]
            else:value = 0
            row[x] = (row[x]+value)&255
        rows.append(row);previous = row.copy()
    colors = [tuple(bytes.fromhex(c)) for c in PALETTES[grade-1]]
    for y, row in enumerate(rows):
        for x in range(width):
            r,g,b,a = row[x*4:x*4+4]
            if not a:continue
            local = y % 32;stone = x < 16 and local < 16
            metal = x >= 16 and local < 16
            trim = x >= 16 and local >= 24 and r > g and g > r*.7 and b < g*.8
            if not (stone or metal or trim):continue
            lum = (.2126*r+.7152*g+.0722*b)/255
            if stone:
                if min(r,g,b)>170 and max(r,g,b)-min(r,g,b)<35:continue
                result = [min(255, round(v*(.22+lum*.55))) for v in colors[1]]
            else:
                t = min(1, max(0, (lum-.22)/.70));lo,hi = (colors[0],colors[1]) if t<.6 else (colors[1],colors[2]);f = t/.6 if t<.6 else (t-.6)/.4
                result = [round(v+(w-v)*f) for v,w in zip(lo,hi)]
            row[x*4:x*4+4] = result+[a]
    payload = b''.join(b'\0'+bytes(row) for row in rows)
    extras = b''.join(png_chunk(k,v) for k,v in chunks if k not in [b'IHDR',b'IDAT',b'IEND',b'tEXt'])
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+png_chunk(b'IHDR',header)+extras+png_chunk(b'tEXt',stamp)+png_chunk(b'IDAT',zlib.compress(payload))+png_chunk(b'IEND',b''))


def shaped(name, pattern, keys, count=1, guarded=False, output=None):
    write(RECIPES / (name + '.json'), dict(
        type=own('machine_upgrade') if guarded else 'minecraft:crafting_shaped',
        category='misc', pattern=pattern, key={k: own(v) for k, v in keys.items()},
        result=dict(id=own(output or name), count=count)))


def process(name, machine, solids, outputs, tier, ticks=200, energy=None, fluids=(), fluid_out=None):
    refs = [0, 10, 100, 1000, 10000, 100000, 1000000]
    if energy is None:
        speed = (1 << (tier - 1)) if machine == 'mana_forge' else 1
        energy = math.ceil(refs[tier] * ticks / 20 / speed)
    v = dict(type=own('industrial'), machine=machine, minimum_tier=tier,
             ingredients=[dict(ingredient=own(k), count=n) for k, n in solids],
             results=[dict(id=own(k), count=n) for k, n in outputs],
             fluid_inputs=[dict(ingredient=own(k), amount=n) for k, n in fluids],
             ticks=ticks, energy=energy)
    if fluid_out:
        v['fluid_result'] = dict(id=own(fluid_out[0]), amount=fluid_out[1])
    write(RECIPES / (name + '.json'), v)


def devices():
    result = {}
    for family in ['mana_crusher', 'mana_furnace', 'mineral_generator', 'currency_fabricator', 'mana_forge', 'mana_beacon']:
        for t in range(1, 6):
            result[f'{family}_t{t}'] = dict(kind=family, tier=t, technology=t, processing=t)
    fixed = {'coal_generator': (1, 1, 1), 'solar_collector': (1, 1, 1),
             'turbine_generator': (2, 2, 2), 'wind_generator': (2, 2, 2),
             'thermal_generator': (3, 3, 3), 'nuclear_reactor': (4, 5, 5),
             'rtg_generator': (5, 5, 5), 'coolant_mixer': (2, 3, 3),
             'mineral_refinery': (2, 2, 5), 'alchemical_reactor': (3, 3, 5),
             'water_pump': (1, 1, 1), 'currency_upgrader': (1, 1, 1)}
    for key, (t, technology, limit) in fixed.items():
        result[key] = dict(kind=key, tier=t, technology=technology, processing=limit)
    for family, grades in [('transfer_crystal', [1, 2, 5]), ('harvest_crystal', [1, 2, 4]),
                           ('fluid_crystal', [1, 2, 5]), ('fluid_tank', [1, 2, 4]),
                           ('mana_accumulator', [1, 2, 3, 5])]:
        for t, grade in enumerate(grades, 1):
            result[f'{family}_t{t}'] = dict(kind=family, tier=t, technology=grade, processing=grade)
    for key, (t, technology, limit, title, template) in NEW_MACHINES.items():
        result[key] = dict(kind=key, tier=t, technology=technology, processing=limit)
    bounds = {'mana_crusher':[1,0,1,15,15,15], 'mana_furnace':[2,0,2,14,14,14],
        'coal_generator':[1,0,1,15,13,15], 'coolant_mixer':[1,0,1,15,13,15],
        'solar_collector':[1,0,1,15,15,15], 'thermal_generator':[1,0,1,15,15,15],
        'turbine_generator':[1,0,1,15,15,15], 'water_pump':[1,0,1,15,14,15],
        'rtg_generator':[2,0,2,14,16,14], 'mana_forge':[0,0,0,16,16,16],
        'currency_upgrader':[0,0,0,16,16,16]}
    for d in result.values():
        family = d['kind']
        if family in ['transfer_crystal','fluid_crystal','harvest_crystal','mana_beacon']:
            d['boxes'] = [[2,0,2,14,3,14] if family == 'harvest_crystal' else [1,0,1,15,3,15], [5.5,3,5.5,10.5,18,10.5]]
        else:
            d['boxes'] = [bounds.get(family,[1,0,1,15,16,15])]
    return result


def catalog(specs):
    tiers = {}
    for metal, t in METAL_TIERS.items():
        for form in [metal + '_ore', 'raw_' + metal, 'crushed_' + metal,
                     metal + '_concentrate', metal + '_ingot', metal + '_block',
                     'high_purity_' + metal + '_ingot']:
            tiers[own(form)] = t
    for k, t in PART_TIERS.items():
        tiers[own(k)] = t
    for k, t in LIQUID_TIERS.items():
        tiers[own(k + '_bucket')] = t
    for t in range(1, 9):
        tiers[own(f'energy_crystal_{t}')] = (t + 1) // 2
    for t in range(1, 7):
        tiers[own(f'industrial_upgrade_{t}')] = t
    for t in range(1, 6):
        tiers[own(f'prospector_t{t}')] = t
    vanilla = {
        1: 'cobblestone stone gravel sand red_sand clay_ball iron_ingot raw_iron iron_ore deepslate_iron_ore iron_block copper_ingot raw_copper copper_ore deepslate_copper_ore copper_block coal coal_ore deepslate_coal_ore charcoal coal_block glass bucket water_bucket stick string leather piston furnace smoker stonecutter blast_furnace chest dropper hopper crafting_table wheat_seeds wheat carrots potatoes beetroot beetroot_seeds water cauldron',
        2: 'gold_ingot raw_gold gold_block gold_ore deepslate_gold_ore diamond diamond_block diamond_ore deepslate_diamond_ore redstone redstone_block redstone_ore deepslate_redstone_ore lapis_lazuli lapis_block lapis_ore deepslate_lapis_ore amethyst_shard amethyst_block ender_pearl compass clock daylight_detector lightning_rod',
        3: 'nether_quartz_ore quartz quartz_block ender_eye blaze_powder blaze_rod nether_wart obsidian',
    }
    for t, names in vanilla.items():
        for name in names.split():
            tiers['minecraft:' + name] = t
    for k, (kind, t, title) in NEW_CONTENT.items():
        tiers[own(k + '_bucket' if kind == 'fluid' else k)] = t
    for k, d in specs.items():
        tiers[own(k)] = d['technology']
    # Tool capability is separate from its material label; hand mining still stops at T3.
    tiers['minecraft:iron_pickaxe'] = 1
    tiers['minecraft:diamond_pickaxe'] = 2
    for p in RECIPES.glob('*.json'):
        recipe = json.loads(p.read_text(encoding='utf-8'))
        if recipe.get('type') in ['minecraft:crafting_shaped', own('machine_upgrade')]:
            for value in recipe.get('key', {}).values():
                if isinstance(value, str) and value.startswith('minecraft:'):
                    tiers.setdefault(value, 1)
    content = {k: dict(kind=kind, tier=t, title=title) for k, (kind, t, title) in NEW_CONTENT.items()}
    energy_tiers = {}
    for p in RECIPES.glob('*.json'):
        recipe = json.loads(p.read_text(encoding='utf-8'))
        machine = recipe.get('machine', '')
        if recipe.get('type') != own('industrial') or machine.startswith('fuel_'):
            continue
        speed = 1 << (recipe.get('minimum_tier', 1)-1) if machine in ['mana_forge','mana_crusher','currency_fabricator'] else [4,8,16,32,64][recipe.get('minimum_tier',1)-1] if machine == 'mana_furnace' else 1
        power = recipe['energy'] * 20 * speed / recipe['ticks']
        grade = next((i for i, maximum in enumerate([20,200,2000,30000,300000],1) if power <= maximum),6)
        energy_tiers['industrial/'+p.stem] = grade
    return dict(grades=[dict(tier=t, name=NAMES[t-1], colors=PALETTES[t-1]) for t in range(1, 7)],
                items=tiers, fluids={own(k): t for k, t in LIQUID_TIERS.items()} | {'minecraft:water': 1},
                content=content, devices=specs, recipe_energy_tiers=energy_tiers,
                material_items=[k for k in tiers if any(k==own(form) for metal in METAL_TIERS for form in [metal+'_ore','raw_'+metal,'crushed_'+metal,metal+'_concentrate',metal+'_ingot',metal+'_block','high_purity_'+metal+'_ingot']) or k in {own(key) for key in PART_TIERS if 'alloy' in key or key in ['vanadium_steel','molybdenum_vanadium_steel','zircon_ceramic','industrial_magic_stone','industrial_magic_stone_block','magic_ore','sulfide','energy_crystal_block_2']}],
                mineral_tiers={k:v for k,v in tiers.items() if any(k==own(form) for metal in METAL_TIERS for form in [metal+'_ore','raw_'+metal,'crushed_'+metal,metal+'_concentrate',metal+'_ingot','high_purity_'+metal+'_ingot'])})


def crafting(specs):
    # Every layout is deliberately authored. Never flatten an ingredient list into a grid.
    shaped('basic_frame', ['ICI', 'C C', 'ICI'], {'I': 'minecraft:iron_ingot', 'C': 'minecraft:copper_ingot'})
    shaped('reinforced_frame', [' S ', 'SFS', ' S '], {'S': 'vanadium_steel', 'F': 'basic_frame'})
    shaped('heatproof_frame', ['CSC', 'SFS', 'CSC'], {'C': 'zircon_ceramic', 'S': 'tzm_alloy', 'F': 'reinforced_frame'})
    shaped('precision_frame', ['CSC', 'SFS', 'CSC'], {'C': 'zircon_ceramic', 'S': 'adamantite_titanium_alloy', 'F': 'heatproof_frame'})
    shaped('mana_component', ['RMR', 'C C', 'RMR'], {'R': 'minecraft:redstone', 'M': 'orichalcum_ingot', 'C': 'minecraft:copper_ingot'}, count=2)
    shaped('control_component', ['RNR', 'G G', 'RTR'], {'R': 'minecraft:redstone', 'N': 'niobium_ingot', 'T': 'tantalum_ingot', 'G': 'minecraft:gold_ingot'})
    shaped('mithril_mana_component', [' L ', 'AMA', ' L '], {'L': 'minecraft:lapis_lazuli', 'A': 'mithril_niobium_alloy', 'M': 'mana_component'})
    shaped('superconducting_control', [' S ', 'ACA', ' S '], {'S': 'spacetime_ingot', 'A': 'mana_superconductor', 'C': 'control_component'})
    shaped('nuclear_core', ['LZL', 'CMC', 'LZL'], {'L': '#c:storage_blocks/lead', 'Z': 'zircon_ceramic', 'C': 'control_component', 'M': 'minecraft:cauldron'})
    shaped('rtg_core', ['LTL', 'CSC', 'LTL'], {'L': '#c:storage_blocks/lead', 'T': 'tzm_alloy', 'C': 'mithril_mana_component', 'S': 'superconducting_control'})
    shaped('industrial_upgrade_1', [' C ', 'IMI', ' C '], {'C': 'energy_crystal_1', 'I': 'minecraft:iron_ingot', 'M': 'minecraft:copper_ingot'}, count=2)
    shaped('machine_casing_t1', [' F ', ' C ', '   '], {'F': 'basic_frame', 'C': 'industrial_upgrade_1'})
    tech_inputs = {
        2: [('minecraft:iron_ingot', 4), ('minecraft:copper_ingot', 4), ('orichalcum_ingot', 4)],
        3: [('vanadium_steel', 4), ('minecraft:gold_ingot', 4), ('minecraft:amethyst_shard', 4)],
        4: [('tzm_alloy', 4), ('mithril_ingot', 4), ('zircon_ceramic', 4)],
        5: [('adamantite_ingot', 4), ('terra_ingot', 4), ('uranium_ingot', 4)],
        6: [('stellar_alloy', 4), ('confinement_lattice', 4), ('superconducting_control', 4)],
    }
    frames = ['basic_frame', 'reinforced_frame', 'heatproof_frame', 'precision_frame', 'precision_frame']
    for t, inputs in tech_inputs.items():
        process(f'tech_block_t{t}', 'mana_forge', inputs, [(f'tech_block_t{t}', 1)], min(5, t-1), ticks=400)
        shaped(f'industrial_upgrade_{t}', [' T ', '   ', '   '], {'T': f'tech_block_t{t}'}, count=2)
        shaped(f'machine_casing_t{t}', [' T ', 'FCF', '   '], {'T': f'tech_block_t{t}', 'C': f'industrial_upgrade_{t}', 'F': frames[t-2]})
    functions = dict(mana_crusher='minecraft:stonecutter', mana_furnace='minecraft:furnace', mineral_generator='minecraft:iron_pickaxe',
        currency_fabricator='horadric_cube', mana_forge='minecraft:blast_furnace', mana_beacon='minecraft:amethyst_block',
        coal_generator='minecraft:smoker', solar_collector='minecraft:daylight_detector', turbine_generator='minecraft:piston',
        wind_generator='minecraft:lightning_rod', heat_generator='minecraft:magma_block', mana_concentrator='minecraft:cauldron', fluid_infuser='minecraft:dropper', thermal_generator='minecraft:blast_furnace', nuclear_reactor='nuclear_core',
        rtg_generator='rtg_core', coolant_mixer='minecraft:cauldron', mineral_refinery='minecraft:bucket',
        alchemical_reactor='minecraft:cauldron', water_pump='minecraft:piston', transfer_crystal='minecraft:ender_pearl',
        harvest_crystal='minecraft:wheat_seeds', fluid_crystal='minecraft:bucket', fluid_tank='minecraft:bucket',
        mana_accumulator='minecraft:redstone_block', currency_upgrader='minecraft:hopper', isotopic_separator='control_component',
        isotope_activator='superconducting_control', forming_assembler='minecraft:piston', thermal_distiller='zircon_ceramic')
    stage_metals = ['minecraft:copper_ingot', 'vanadium_steel', 'tzm_alloy', 'adamantite_titanium_alloy', 'henghui_alloy', 'stellar_alloy']
    for key, d in specs.items():
        kind, t, grade = d['kind'], d['tier'], d['technology']
        previous = f'{kind}_t{t-1}'
        if previous in specs:
            component = 'mana_component' if grade <= 4 else 'mithril_mana_component'
            support = ['basic_frame','reinforced_frame','heatproof_frame','precision_frame','precision_frame'][grade-2]
            # Cross section: casing above, conductors on both sides, support below the old machine.
            shaped(key, [' C ', 'FMF', ' S '], {'C': f'machine_casing_t{grade}', 'M': previous, 'F': component, 'S': support}, guarded=True)
            d['upgrade_costs'] = {f'machine_casing_t{grade}': 1, component: 2, support: 1}
            continue
        if kind == 'artificial_star':
            shaped(key, ['LCL', 'ASA', 'LCL'], {'L': 'confinement_lattice', 'C': 'superconducting_control', 'A': 'stellar_alloy', 'S': 'machine_casing_t6'})
        elif kind == 'nuclear_reactor':
            shaped(key, ['TIT', 'CSC', 'TNT'], {'T': 'tzm_alloy', 'I': 'high_purity_iridium_ingot', 'C': 'superconducting_control', 'S': 'machine_casing_t5', 'N': 'nuclear_core'})
        elif kind == 'fluid_tank':
            shaped(key, ['G G', 'GSG', 'IBI'], {'G': 'minecraft:glass', 'S': f'machine_casing_t{grade}', 'I': 'minecraft:iron_ingot', 'B': 'minecraft:bucket'})
        elif kind == 'coolant_mixer':
            # Keep its cauldron below the casing so it cannot collide with the reactor.
            shaped(key, ['MMM', 'CSC', 'MFM'], {'M': stage_metals[grade-1], 'F': functions[kind],
                'C': 'mana_component', 'S': f'machine_casing_t{grade}'})
        elif kind in ['fluid_crystal', 'water_pump']:
            shaped(key, [' C ', 'GSG', 'MBM'], {'C': 'energy_crystal_1', 'G': 'minecraft:glass', 'S': f'machine_casing_t{grade}', 'M': 'minecraft:copper_ingot', 'B': functions[kind]})
        else:
            shaped(key, ['MFM', 'CSC', 'MMM'], {'M': stage_metals[grade-1], 'F': functions.get(kind, 'control_component'),
                'C': 'energy_crystal_1' if grade == 1 else 'mana_component' if grade < 4 else 'mithril_mana_component',
                'S': f'machine_casing_t{grade}'})
    for t in range(1, 6):
        if t == 1:
            shaped('prospector_t1', [' C ', 'MGM', ' C '], {'C': 'energy_crystal_1', 'M': 'minecraft:copper_ingot', 'G': 'minecraft:compass'})
        else:
            shaped(f'prospector_t{t}', [' G ', 'MPM', ' S '], {'G': 'minecraft:glass', 'M': 'mana_component' if t < 4 else 'mithril_mana_component',
                'P': f'prospector_t{t-1}', 'S': f'machine_casing_t{t}'})
    shaped('mana_wand', ['  C', ' S ', 'S  '], {'C': 'energy_crystal_2', 'S': 'minecraft:stick'})
    shaped('sulfide', ['LCS'], {'L': '#c:ingots/lead', 'C': 'minecraft:coal', 'S': '#c:sands'}, count=1)
    for part in ['basic_frame','reinforced_frame','heatproof_frame','precision_frame','mana_component','mithril_mana_component','control_component','superconducting_control']:
        automatic_path = RECIPES / ('automatic_' + part + '.json')
        if not automatic_path.exists():continue
        manual = json.loads((RECIPES / (part + '.json')).read_text(encoding='utf-8'))
        counts = Counter(manual['key'][symbol] for row in manual['pattern'] for symbol in row if symbol != ' ')
        automatic = json.loads(automatic_path.read_text(encoding='utf-8'))
        automatic['ingredients'] = [dict(ingredient=item,count=count) for item,count in counts.items()]
        automatic['results'] = [manual['result']]
        write(automatic_path,automatic)
    # Compressions are squares and unpacking is one occupied cell, including the non-machine recipes.
    for p in RECIPES.glob('*.json'):
        r = json.loads(p.read_text(encoding='utf-8'))
        if r.get('type') != 'minecraft:crafting_shapeless':
            continue
        ingredients = r['ingredients']
        if len(ingredients) == 9 and len(set(ingredients)) == 1:
            r.update(type='minecraft:crafting_shaped', pattern=['XXX', 'XXX', 'XXX'], key={'X': ingredients[0]})
        elif len(ingredients) == 1:
            r.update(type='minecraft:crafting_shaped', pattern=['X'], key={'X': ingredients[0]})
        else:
            raise ValueError('An explicit layout is required for ' + p.name)
        del r['ingredients']
        write(p, r)


def nuclear():
    process('leach_uranium', 'mineral_refinery', [('crushed_uranium', 2)], [('uranium_concentrate', 2)], 4, ticks=400,
            fluids=[('alkahest', 1000)], fluid_out=('spent_solvent', 1000))
    process('convert_uranium', 'alchemical_reactor', [('uranium_concentrate', 2)], [('converted_uranium', 2)], 4,
            ticks=400, fluids=[('ceruleum', 1000)])
    process('separate_uranium', 'isotopic_separator', [('converted_uranium', 2)], [('enriched_uranium', 1), ('depleted_uranium', 1)], 4, ticks=400)
    process('nuclear_pellet', 'forming_assembler', [('enriched_uranium', 1)], [('nuclear_pellet', 4)], 4, ticks=200)
    process('zirconium_casing', 'forming_assembler', [('zirconium_ingot', 2), ('zircon_ceramic', 1)], [('zirconium_casing', 4)], 3)
    process('uranium_fuel_rod', 'forming_assembler', [('nuclear_pellet', 2), ('zirconium_casing', 1)], [('uranium_fuel_rod', 1)], 4)
    process('fuel_nuclear_reactor_0', 'fuel_nuclear_reactor', [('uranium_fuel_rod', 1)], [('spent_fuel_rod', 1)], 1, ticks=16000, energy=86400000)
    process('pu238_precursor', 'mana_forge', [('depleted_uranium', 4), ('lead_ingot', 4), ('high_purity_niobium_ingot', 2), ('zirconium_casing', 1)], [('pu238_precursor', 1)], 4)
    process('th228_precursor', 'mana_forge', [('depleted_uranium', 4), ('high_purity_tantalum_ingot', 2), ('high_purity_iridium_ingot', 1), ('zirconium_casing', 1)], [('th228_precursor', 1)], 5)
    for isotope, grade in [('pu238', 4), ('th228', 5)]:
        process('activate_' + isotope, 'isotope_activator', [(isotope + '_precursor', 1), ('uranium_fuel_rod', 1)],
                [('irradiated_' + isotope, 1), ('spent_fuel_rod', 1)], grade, ticks=1200, fluids=[('minecraft:water', 1000)])
        process('cool_' + isotope, 'mineral_refinery', [('irradiated_' + isotope, 1)], [('cooled_' + isotope, 1)], grade,
                ticks=400, fluids=[('minecraft:water', 1000)])
        process('recover_' + isotope, 'isotopic_separator', [('cooled_' + isotope, 1)], [(isotope, 1)], grade,
                ticks=400, fluids=[('alkahest', 1000)], fluid_out=('spent_solvent', 1000))
    process('cool_spent_fuel', 'mineral_refinery', [('spent_fuel_rod', 1)], [('cooled_spent_fuel', 1)], 4, ticks=400, fluids=[('minecraft:water', 1000)])
    process('reprocess_spent_fuel', 'alchemical_reactor', [('cooled_spent_fuel', 1)], [('curium_feed', 1), ('depleted_uranium', 1)], 4,
            ticks=400, fluids=[('alkahest', 1000)], fluid_out=('spent_solvent', 1000))
    process('recover_cm244', 'isotopic_separator', [('curium_feed', 1)], [('cm244', 1)], 5, ticks=600)
    for i, (isotope, grade, energy) in enumerate([('pu238', 4, 86400000), ('cm244', 5, 432000000), ('th228', 5, 1728000000)]):
        process('seal_' + isotope, 'forming_assembler', [(isotope, 1), ('zirconium_casing', 1)], [(isotope + '_heat_source', 1)], grade)
        process('fuel_rtg_generator_' + str(i), 'fuel_rtg_generator', [(isotope + '_heat_source', 1)], [], 1, ticks=16000000, energy=energy)
    process('empty_fuel_capsule', 'forming_assembler', [('zircon_ceramic', 1), ('minecraft:copper_ingot', 1)], [('empty_fuel_capsule', 4)], 3)
    process('heavy_water', 'isotopic_separator', [], [], 3, fluids=[('minecraft:water', 5000)], fluid_out=('heavy_water', 1000))
    process('deuterium_capsule', 'isotopic_separator', [('empty_fuel_capsule', 1)], [('deuterium_capsule', 1)], 5,
            fluids=[('heavy_water', 1000)])
    process('brine', 'thermal_distiller', [], [], 2, fluids=[('minecraft:water', 1000)], fluid_out=('brine', 1000))
    process('lithium_salt', 'thermal_distiller', [], [('lithium_salt', 1)], 4, ticks=400, fluids=[('brine', 1000)])
    process('tritium_capsule', 'isotope_activator', [('lithium_salt', 1), ('empty_fuel_capsule', 1)], [('tritium_capsule', 1)], 5,
            ticks=1200, fluids=[('minecraft:water', 1000)])
    process('fusion_fuel_capsule', 'forming_assembler', [('deuterium_capsule', 1), ('tritium_capsule', 1)], [('fusion_fuel_capsule', 1), ('empty_fuel_capsule', 1)], 5)
    process('fuel_artificial_star_0', 'fuel_artificial_star', [('fusion_fuel_capsule', 1)], [('empty_fuel_capsule', 1)], 1, ticks=400, energy=1000000000)
    process('stellar_alloy', 'mana_forge', [('high_purity_iridium_ingot', 4), ('mana_superconductor', 4), ('henghui_alloy', 4)], [('stellar_alloy', 4)], 5,
            ticks=1200, energy=6000000, fluids=[('azoth', 1000)])
    process('confinement_lattice', 'mana_forge', [('lattice_substrate', 4), ('high_purity_tantalum_ingot', 4), ('stellar_alloy', 4)], [('confinement_lattice', 4)], 5,
            ticks=1200, energy=6000000, fluids=[('penduliquid', 1000)])
    shaped('fusion_ignition_core', [' S ', 'LCL', ' S '], {'S': 'stellar_alloy', 'L': 'confinement_lattice', 'C': 'industrial_upgrade_6'})


def rules_and_processing():
    p = DATA / 'rules/industrial.json'
    rules = json.loads(p.read_text(encoding='utf-8'))
    rules['version'] = 3
    for name, m in rules['minerals'].items():
        m.pop('power', None)
        m['energy'] = MINING_ENERGY[name]
        q = RECIPES / ('generate_' + name + '.json')
        r = json.loads(q.read_text(encoding='utf-8'))
        r['energy'] = m['energy']
        r['ticks'] = m['ticks'] // m['tier']
        write(q, r)
    rules.update(transferEnergy=[5, 25, 100], fluidTransferOperationEnergy=[5, 25, 100], harvestEnergy=20, pumpEnergy=10)
    rules['generators']['nuclear_reactor'] = dict(fluidPerSecond=100000, fluid='minecraft:water')
    rules['generators']['artificial_star'] = dict(fluidPerSecond=100000, fluid=own('coolant'))
    write(p, rules)
    item_tiers = catalog(devices())['items']
    def input_grade(key):
        if key.startswith('#'):
            return METAL_TIERS.get(key.rsplit('/',1)[-1],3 if 'magic_stone' in key else 1)
        return item_tiers.get(key,1)
    for p in RECIPES.glob('*.json'):
        r = json.loads(p.read_text(encoding='utf-8'))
        if r.get('type') == 'minecraft:smelting':
            source = r['ingredient']
            grade = next((t for metal, t in METAL_TIERS.items() if metal in str(source)), 1)
            if grade >= 3:
                result = r['result']
                duration = r.get('cookingtime', 200)
                energy = math.ceil([0, 0, 0, 750, 7500, 75000][grade] * duration / 20 / [4, 8, 16, 32, 64][grade-1])
                r = dict(type=own('industrial'), machine='mana_furnace', minimum_tier=grade,
                    ingredients=[dict(ingredient=source, count=1)], results=[result], ticks=duration, energy=energy)
                write(p, r)
        elif r.get('type') == own('industrial'):
            if r.get('machine') == 'mana_crusher':
                grade = next((t for metal, t in METAL_TIERS.items() if metal in p.stem), 1)
                r['minimum_tier'] = max(r.get('minimum_tier', 1), grade if grade >= 3 else 1)
                if grade == 1:
                    r['energy'] = 50
                write(p, r)
            elif r.get('machine') == 'coolant_mixer':
                r.update(minimum_tier=3, energy=2500)
                write(p, r)
            elif r.get('machine') == 'currency_fabricator':
                r['energy'] = 600
                write(p, r)
            elif r.get('machine') == 'mana_forge' and r.get('minimum_tier', 1) <= 2:
                speed = 1 << (r.get('minimum_tier', 1)-1)
                r['energy'] = min(r['energy'], math.ceil((10 if r.get('minimum_tier', 1) == 1 else 100)*r['ticks']/20/speed))
                write(p, r)
            if r.get('machine') == 'mana_forge' and any(n.get('ingredient') in ['#magic_stones', '#c:magic_stones', own('industrial_magic_stone')] for n in r.get('ingredients', [])):
                for n in r['ingredients']:
                    if n['ingredient'] in ['#magic_stones', '#c:magic_stones', own('industrial_magic_stone')]:
                        n['ingredient'] = own('lattice_substrate')
                write(p, r)
    # Fixed base budgets; acceleration and parallel lines still apply their existing multipliers.
    references = [0,10,100,1000,10000,100000,1000000]
    for p in RECIPES.glob('*.json'):
        r = json.loads(p.read_text(encoding='utf-8'))
        machine = r.get('machine','')
        if r.get('type') != own('industrial') or machine.startswith('fuel_') or machine in ['mineral_generator','water_pump','coolant_mixer'] or machine.startswith('storage_upgrade_'):
            continue
        source_grade = max([input_grade(n['ingredient']) for n in r.get('ingredients',[])] + [1])
        product_grade = max([item_tiers.get(n['id'],1) for n in r.get('results',[])] + [1])
        liquid_grade = LIQUID_TIERS.get(r.get('fluid_result',{}).get('id','').split(':')[-1],1)
        grade = max(source_grade,product_grade,liquid_grade)
        if source_grade >= 3 and machine in ['mana_crusher','mana_furnace','mana_forge']:
            r['minimum_tier'] = max(r.get('minimum_tier',1), min(5,source_grade))
        if machine == 'currency_fabricator':
            r['energy'] = 600
        elif p.stem.startswith('tech_block_'):
            pass
        elif machine == 'mana_crusher' and grade == 1:
            r['energy'] = 50
        else:
            speed = 1 << (r.get('minimum_tier',1)-1) if machine in ['mana_forge','mana_crusher'] else [4,8,16,32,64][r.get('minimum_tier',1)-1] if machine == 'mana_furnace' else 1
            complexity = 1.25 if len(r.get('fluid_inputs',[])) > 1 or len(r.get('results',[])) > 1 else .75 if len(r.get('ingredients',[])) == 1 and not r.get('fluid_inputs') else 1
            target = math.ceil(references[grade] * complexity * r['ticks'] / 20 / speed)
            r['energy'] = min(r['energy'],target) if grade <= 2 else target
        write(p,r)



def icon(name, grade, block=False):
    palette = [tuple(bytes.fromhex(c)) + (255,) for c in PALETTES[grade-1]]
    pixels = [[(0, 0, 0, 0) for x in range(16)] for y in range(16)]
    def rectangle(x0, y0, x1, y1, color, border=False):
        for y in range(y0, y1+1):
            for x in range(x0, x1+1):
                if not border or x in (x0, x1) or y in (y0, y1):
                    pixels[y][x] = color
    if block:
        rectangle(0, 0, 15, 15, (17, 23, 34, 255))
        rectangle(1, 1, 14, 14, palette[1], True)
        rectangle(2, 2, 13, 13, palette[1], True)
        rectangle(3, 3, 12, 12, palette[0], True)
    else:
        rectangle(2, 2, 13, 13, palette[0])
        rectangle(2, 2, 13, 13, palette[1], True)
    for y in range(3, 13):
        for x in range(4, 13):
            if abs(x-8)+abs(y-8) <= 4:
                pixels[y][x] = palette[1]
    for x, y in [(8,4), (7,5), (6,6), (5,7)]:
        pixels[y][x] = palette[2]
    for i in range(grade):
        pixels[14][3+i*2] = palette[2]
    folder = 'block' if block else 'item'
    def chunk(key, data):
        return struct.pack('>I',len(data))+key+data+struct.pack('>I',zlib.crc32(key+data)&0xffffffff)
    raw=b''.join(b'\0'+bytes(c for pixel in row for c in pixel) for row in pixels)
    png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',16,16,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw))+chunk(b'IEND',b'')
    (ASSETS / 'textures' / folder / (name + '.png')).write_bytes(png)


def resources(specs):
    # One machine owns the visible artificial sun; its reserved cells have no independent model or item.
    write(ASSETS / 'blockstates/artificial_star_part.json', {'variants': {'': {'model': own('block/artificial_star_part')}}})
    write(ASSETS / 'models/block/artificial_star_part.json', {'textures': {'particle': own('block/arcane/artificial_star')}, 'elements': []})
    atlas_dir = ASSETS / 'textures/block/arcane'
    for path in atlas_dir.glob('*.png'):
        stem = path.stem
        if stem == 'rotor':
            grade = 2
        else:
            parts = stem.removesuffix('_active').removesuffix('_linked').rsplit('_',1)
            if len(parts)!=2 or not parts[1].isdigit():continue
            family, tier = parts[0], int(parts[1])
            spec = specs.get(family) or specs.get(family+'_t'+str(tier))
            if family == 'equipment_fabricator':grade = 1
            elif spec is None:continue
            else:grade = spec['technology']
        recolor_atlas(path,grade)
    for key, (kind, grade, title) in NEW_CONTENT.items():
        if kind == 'fluid':
            # Standard liquid rendering reuses water geometry; the registered fluid supplies its tint.
            write(ASSETS / 'items' / (key + '_bucket.json'), {'model': {'type': 'minecraft:model', 'model': own('item/' + key + '_bucket')}})
            write(ASSETS / 'models/item' / (key + '_bucket.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': own('item/coolant_bucket')}})
            write(ASSETS / 'blockstates' / (key + '.json'), {'variants': {'': {'model': own('block/'+key)}}})
            write(ASSETS / 'models/block' / (key + '.json'), {'textures': {'particle':'minecraft:block/water_still'}})
            continue
        icon(key, grade, block=kind == 'block')
        write(ASSETS / 'items' / (key + '.json'), {'model': {'type': 'minecraft:model', 'model': own(('block/' if kind == 'block' else 'item/') + key)}})
        if kind == 'block':
            write(ASSETS / 'blockstates' / (key + '.json'), {'variants': {'': {'model': own('block/' + key)}}})
            write(ASSETS / 'models/block' / (key + '.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': own('block/' + key)}})
            write(DATA / 'loot_table/blocks' / (key + '.json'), {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': own(key)}]}]})
        else:
            write(ASSETS / 'models/item' / (key + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': own('item/' + key)}})
    for t in range(1, 6):
        icon('industrial_upgrade_' + str(t), t)
    for key, (tier, grade, limit, title, template) in NEW_MACHINES.items():
        variants = {}
        for facing, angle in [('north', 0), ('east', 90), ('south', 180), ('west', 270)]:
            for state in range(1, 6):
                for connected in [False, True]:
                    for active in [False, True]:
                        suffix = '_active' if active else '_linked' if connected else ''
                        variants[f'facing={facing},tier={state},connected={str(connected).lower()},active={str(active).lower()}'] = dict(model=own('block/' + key + suffix), y=angle)
        write(ASSETS / 'blockstates' / (key + '.json'), {'variants': variants})
        template_tier = specs.get(template, {}).get('tier', min(tier,5))
        for suffix in ['', '_linked', '_active']:
            model = copy.deepcopy(json.loads((ASSETS / f'models/block/{template}_{template_tier}{suffix}.json').read_text(encoding='utf-8')))
            source = ASSETS / 'textures' / (model['textures']['atlas'].split(':',1)[1] + '.png')
            texture = atlas_dir / (key + suffix + '.png')
            shutil.copyfile(source, texture)
            recolor_atlas(texture,grade)
            if source.with_suffix('.png.mcmeta').exists():
                shutil.copyfile(source.with_suffix('.png.mcmeta'), texture.with_suffix('.png.mcmeta'))
            model['textures'] = {k: own('block/arcane/'+key+suffix) for k in model['textures']}
            write(ASSETS / 'models/block' / (key + suffix + '.json'), model)
        write(ASSETS / 'items' / (key + '.json'), {'model': {'type': 'minecraft:model', 'model': own('block/' + key)}})
        write(ASSETS / 'models/item' / (key + '.json'), {'parent': own('block/' + key)})
    for locale in ['zh_cn', 'en_us']:
        path = ASSETS / 'lang' / (locale + '.json')
        lang = json.loads(path.read_text(encoding='utf-8'))
        for key, (kind, grade, title) in NEW_CONTENT.items():
            if kind == 'fluid':
                lang.pop('fluid.' + NS + '.' + key, None)
                lang['fluid_type.' + NS + '.' + key] = title
                lang['block.' + NS + '.' + key] = title
                lang['item.' + NS + '.' + key + '_bucket'] = title + '桶'
            else:
                lang[('block' if kind == 'block' else 'item') + '.' + NS + '.' + key] = title
        for t in range(1, 7):
            lang[f'item.{NS}.industrial_upgrade_{t}'] = f'T{t}科技核心'
        for key, d in NEW_MACHINES.items():
            lang[f'block.{NS}.{key}'] = d[3]
        write(path, lang)
    tag = RES / 'data/minecraft/tags/block/mineable/pickaxe.json'
    values = json.loads(tag.read_text(encoding='utf-8'))
    for key, (kind, grade, title) in NEW_CONTENT.items():
        if kind == 'block' and key!='magic_flower' and own(key) not in values['values']:
            values['values'].append(own(key))
    for key in NEW_MACHINES:
        if own(key) not in values['values']:
            values['values'].append(own(key))
    write(tag, values)


def display_line():
    """Production source for the display/infusion chain and its blue flora resources."""
    import io
    import os
    import zipfile
    from PIL import Image

    shaped('display_stand', ['MFM','CSC','MMM'], {'M':'minecraft:copper_ingot','F':'minecraft:smooth_stone_slab','C':'energy_crystal_1','S':'machine_casing_t1'})
    for key, part in [('heat_generator','minecraft:magma_block'),('mana_concentrator','minecraft:cauldron'),('fluid_infuser','minecraft:dropper')]:
        shaped(key,['MFM','CSC','MMM'],{'M':'vanadium_steel','F':part,'C':'mana_component','S':'machine_casing_t2'})
    process('make_mana_fluid','mana_forge',[('energy_crystal_1',1)],[],1,ticks=100,energy=50,fluids=[('minecraft:water',1000)],fluid_out=('mana_fluid',1000))
    process('concentrate_mana_fluid','mana_concentrator',[],[],2,ticks=20,energy=100,fluids=[('mana_fluid',100)],fluid_out=('concentrated_mana_fluid',1))
    for name, source, fluid, result in [('infuse_regal','transmutation_stone','mana_fluid','regal_stone'),('infuse_exalted','regal_stone','concentrated_mana_fluid','exalted_stone'),('infuse_advanced_divine','divine_stone','shimmer','advanced_divine_stone')]:
        process(name,'fluid_infuser',[(source,1)],[(result,1)],2,ticks=20,energy=100,fluids=[(fluid,100)])
    (DATA/'recipe/currency_upgrader.json').unlink(missing_ok=True)
    rules_path=DATA/'rules/industrial.json'
    rules=json.loads(rules_path.read_text(encoding='utf-8'))
    rules.update(magicFlowerChance=16,magicFlowerRadius=4,magicFlowerSpreadChance=8,magicFlowerHeight=1)
    write(rules_path,rules)

    def cube(low,high,uv=(0,0,4,4),texture='#atlas'):
        return {'from':low,'to':high,'faces':{face:{'texture':texture,'uv':list(uv)} for face in ['north','south','east','west','up','down']}}
    bodies={
        'display_stand':[cube([1,0,1],[15,2,15]),cube([5,2,5],[11,6,11]),cube([.5,6,.5],[15.5,8,15.5],(4,0,8,4))],
        'heat_generator':[cube([1,0,1],[15,2,15]),cube([3,2,3],[13,13,13]),cube([2,13,2],[14,15,14],(4,0,8,4))]+[cube([x,3,2],[x+1,12,14],(8,0,12,4)) for x in [1,14]],
        'mana_concentrator':[cube([1,0,1],[15,3,15]),cube([2,13,2],[14,16,14]),cube([7,3,7],[9,13,9],(4,0,8,4)),cube([3,4,3],[7,13,13],texture='#glass'),cube([9,4,3],[13,13,13],texture='#glass')],
        'fluid_infuser':[cube([2,8,2],[14,16,14]),cube([6,4,6],[10,8,10],(4,0,8,4)),cube([7,0,7],[9,4,9],(8,0,12,4)),cube([2,4,6],[5,10,10],texture='#glass'),cube([11,4,6],[14,10,10],texture='#glass')],
    }
    for key, elements in bodies.items():
        passive=key=='display_stand'
        for suffix in [''] if passive else ['','_linked','_active']:
            template='mana_accumulator_1' if passive else 'turbine_generator_2'
            texture=own('block/arcane/'+template+suffix)
            model={'ambientocclusion':True,'render_type':'minecraft:cutout','textures':{'atlas':texture,'particle':texture,'glass':'minecraft:block/light_blue_stained_glass'},'elements':elements}
            write(ASSETS/f'models/block/{key}{suffix}.json',model)
        variants={'':{'model':own('block/'+key)}} if passive else {
            f'facing={face},tier={tier},connected={str(connected).lower()},active={str(active).lower()}':
                {'model':own('block/'+key+('_active' if active else '_linked' if connected else '')),'y':rotation}
            for face,rotation in [('north',0),('east',90),('south',180),('west',270)] for tier in range(1,6) for connected in [False,True] for active in [False,True]}
        write(ASSETS/f'blockstates/{key}.json',{'variants':variants})
        write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':own('block/'+key)}})
        write(ASSETS/f'models/item/{key}.json',{'parent':own('block/'+key)})
        write(DATA/f'loot_table/blocks/{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':own(key)}]}]})
    tag_path=RES/'data/minecraft/tags/block/mineable/pickaxe.json'
    tag=json.loads(tag_path.read_text(encoding='utf-8'))
    for key in bodies:
        if own(key) not in tag['values']:tag['values'].append(own(key))
    write(tag_path,tag)
    for key,color in [('mana_fluid',0x58BAFF),('concentrated_mana_fluid',0x3659E8)]:
        write(ASSETS/f'items/{key}_bucket.json',{'model':{'type':'minecraft:model','model':own('item/'+key+'_bucket'),'tints':[{'type':'minecraft:constant','value':color}]}})
        write(ASSETS/f'models/item/{key}_bucket.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('item/coolant_bucket')}})
        write(ASSETS/f'blockstates/{key}.json',{'variants':{'':{'model':own('block/'+key)}}})
        write(ASSETS/f'models/block/{key}.json',{'textures':{'particle':'minecraft:block/water_still'}})
    drop=ASSETS/'textures/particle/infusion_drop.png';drop.parent.mkdir(parents=True,exist_ok=True)
    Image.new('RGBA',(2,2),(255,255,255,255)).save(drop)
    for key in ['mana_fluid','concentrated_mana_fluid','shimmer']:
        write(ASSETS/f'particles/infusion_{key}.json',{'textures':[own('infusion_drop')]})

    cache=Path(os.environ.get('GRADLE_USER_HOME',Path.home()/'.gradle'))/'caches/neoformruntime/artifacts/minecraft_26.2_client.jar'
    with zipfile.ZipFile(cache) as vanilla:
        def image(name):return Image.open(io.BytesIO(vanilla.read('assets/minecraft/textures/block/'+name+'.png'))).convert('RGBA')
        grass=image('firefly_bush')
        for y in range(grass.height):
            for x in range(grass.width):
                r,g,b,a=grass.getpixel((x,y));lum=.3*r+.59*g+.11*b
                if a:grass.putpixel((x,y),(int(lum*.58),int(lum*.86),min(255,int(lum*1.18)),a))
        grass.save(ASSETS/'textures/block/magic_grass.png')
        (ASSETS/'textures/block/magic_grass.png.mcmeta').unlink(missing_ok=True)
        glow=image('firefly_bush_emissive')
        for y in range(glow.height):
            for x in range(glow.width):
                r,g,b,a=glow.getpixel((x,y));lum=max(r,g,b)
                if a:glow.putpixel((x,y),(int(lum*.3),int(lum*.7),lum,a))
        glow.save(ASSETS/'textures/block/magic_grass_emissive.png')
        write(ASSETS/'textures/block/magic_grass_emissive.png.mcmeta',{'animation':{'frametime':3}})
        flower=image('poppy')
        for y in range(flower.height):
            for x in range(flower.width):
                r,g,b,a=flower.getpixel((x,y))
                if not a:continue
                flower.putpixel((x,y),(min(255,70+int(r*.4)),min(255,100+int(r*.5)),min(255,160+int(r*.4)),a) if r>g*1.2 else (int(r*.7),int(g*.9),min(255,b+int(g*.4)),a))
        flower.save(ASSETS/'textures/block/magic_flower.png')
    write(ASSETS/'models/block/magic_grass.json',{'parent':'minecraft:block/cross_emissive','render_type':'minecraft:cutout','textures':{'cross':own('block/magic_grass'),'cross_emissive':own('block/magic_grass_emissive')}})
    write(ASSETS/'models/item/magic_grass.json',{'parent':own('block/magic_grass')})
    write(ASSETS/'items/magic_grass.json',{'model':{'type':'minecraft:model','model':own('item/magic_grass')}})
    write(ASSETS/'models/block/magic_flower.json',{'parent':'minecraft:block/cross','render_type':'minecraft:cutout','textures':{'cross':own('block/magic_flower')}})
    write(ASSETS/'models/item/magic_flower.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('block/magic_flower')}})
    write(ASSETS/'items/magic_flower.json',{'model':{'type':'minecraft:model','model':own('item/magic_flower')}})
    write(ASSETS/'blockstates/magic_flower.json',{'variants':{'':{'model':own('block/magic_flower')}}})
    write(DATA/'loot_table/blocks/magic_flower.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':own('magic_flower')}]}]})
    write(DATA/'worldgen/configured_feature/magic_flower.json',{'type':own('industrial_resources'),'config':{'kind':'flower'}})
    write(DATA/'worldgen/placed_feature/magic_flower.json',{'feature':own('magic_flower'),'placement':[]})
    write(DATA/'neoforge/biome_modifier/magic_flower.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':[own('magic_flower')],'step':'vegetal_decoration'})
    labels={f'block.{NS}.{key}':name for key,name in [('display_stand','展示台'),('heat_generator','热能发电机'),('mana_concentrator','魔力液浓缩器'),('fluid_infuser','注液器'),('magic_flower','魔力花')]}
    for key,name in [('mana_fluid','魔力液'),('concentrated_mana_fluid','浓缩魔力液')]:
        labels.update({f'fluid_type.{NS}.{key}':name,f'block.{NS}.{key}':name,f'item.{NS}.{key}_bucket':name+'桶'})
    for locale in ['zh_cn','en_us']:
        path=ASSETS/f'lang/{locale}.json';lang=json.loads(path.read_text(encoding='utf-8'));lang.update(labels);write(path,lang)


def generate():
    specs = devices()
    crafting(specs)
    nuclear()
    rules_and_processing()
    write(ASSETS / 'industry/technology.json', catalog(specs))
    resources(specs)
    display_line()
    write(ASSETS / 'industry/technology.json', catalog(specs))


if __name__ == '__main__':
    generate()

"""Authoring source for Horadric essences, fixed gems and their native resources."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'src/main/resources'
NS = 'yumis_dungeon_and_fight'
DATA = RES / 'data' / NS
ASSETS = RES / 'assets' / NS


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def build():
    essences, gems, definitions = [], [], {}

    def essence(key, name, operation, stat, slots, material='', paper=False, element=-1, family=''):
        affix = 'crafted/' + key
        definitions[affix] = dict(id=affix, name='工艺之' + name, operation=operation,
                                  group='crafted', stat=stat, element=element)
        if operation == 'SKILL_FAMILY':
            definitions[affix]['fixedRank'] = 2
        if stat == 'MOVEMENT_SPEED':
            definitions[affix].update(minimum=.08, maximum=.12)
        essences.append(dict(id='essence_' + key, name=name + '之精华', affix=affix,
                             slots=slots.split(','), material=material, paper=paper, family=family))

    essence('strength', '力量', 'FLAT', 'STRENGTH', 'RING', 'wheat')
    essence('intelligence', '智识', 'FLAT', 'INTELLIGENCE', 'RING', 'paper')
    essence('spirit', '精神', 'FLAT', 'SPIRIT', 'HEAD,NECKLACE', 'sugar')
    essence('health', '生命', 'FLAT', 'HEALTH', 'CHEST,LEGS,FEET', 'carrot')
    essence('mp', '魔泉', 'FLAT', 'MP', 'HEAD,NECKLACE', 'potato')
    essence('physical_defense_percent', '坚壁', 'PERCENT', 'PHYSICAL_DEFENSE', 'CHEST', 'leather')
    essence('magical_defense_percent', '魔御', 'PERCENT', 'MAGICAL_DEFENSE', 'LEGS', '#wool')
    essence('movement_speed', '疾行', 'CHARACTER_PERCENT', 'MOVEMENT_SPEED', 'FEET', 'feather')
    essence('all_resistance', '庇护', 'ALL_RESIST_FLAT', None, 'BRACELET', 'dried_kelp')
    essence('physical_defense', '护甲', 'FLAT', 'PHYSICAL_DEFENSE', 'BRACELET', 'cobblestone')
    essence('attack_speed', '迅捷', 'FLAT', 'ATTACK_SPEED', 'EARRING', 'bamboo')
    essence('drop_rate', '寻宝', 'FLAT', 'DROP_RATE', 'EARRING', 'melon_slice')
    essence('physical_attack', '锋锐', 'FLAT', 'PHYSICAL_ATTACK', 'WEAPON', 'stick')
    essence('magical_attack', '秘法', 'FLAT', 'MAGICAL_ATTACK', 'WEAPON', 'charcoal')
    essence('physical_attack_percent', '破军', 'PERCENT', 'PHYSICAL_ATTACK', 'SUB_EQUIPMENT', 'stick', True)
    essence('magical_attack_percent', '魔涌', 'PERCENT', 'MAGICAL_ATTACK', 'SUB_EQUIPMENT', 'charcoal', True)
    elements = [('light', '光辉', 'glow_berries'), ('fire', '烈焰', 'beetroot'),
                ('ice', '寒霜', 'snowball'), ('dark', '幽暗', 'sweet_berries')]
    for element, (key, name, material) in enumerate(elements):
        essence('extra_' + key, name + '追击', 'EXTRA', None, 'WEAPON', material, element=element)
        essence('element_' + key, name + '强化', 'FLAT', key.upper(), 'MAGIC_STONE', material, True)
    for key, name in [('sword', '剑术'), ('ghost', '鬼神'), ('wave', '波动'), ('blood', '血气'),
                      ('element', '元素'), ('summon', '召唤'), ('sun_moon', '日月'), ('havoc', '浩劫')]:
        essence('family_' + key, name, 'SKILL_FAMILY', None, 'WEAPON', family=key.upper())

    essence('alaska_wisdom', '阿拉斯加的智慧', 'CHARACTER_PERCENT', 'EXPERIENCE_GAIN', 'HEAD')
    essences[-1].update(name='阿拉斯加的智慧', dropOnly=True)
    definitions['crafted/alaska_wisdom'].update(minimum=0, maximum=1)
    for key, name, stat, element in [('pinocchio', '烈焰皮诺修', 'FIRE', 1),
                                    ('sieghart', '光之城主', 'LIGHT', 0),
                                    ('delezie', '瘟疫狄瑞吉', 'DARK', 3),
                                    ('skasa', '斯卡撒', 'ICE', 2)]:
        essence(key, name, 'WEAPON_ELEMENT', stat, 'WEAPON', element=element)
        essences[-1]['dropOnly'] = True
        definitions['crafted/' + key].update(minimum=18, maximum=25)

    for key, name, operation, stat, group, element, parameter, values in [
        ('strength', '力量', 'FLAT', 'STRENGTH', 'all', -1, '', [11.4, 17.1, 22.8]),
        ('intelligence', '智识', 'FLAT', 'INTELLIGENCE', 'all', -1, '', [7.725, 11.5875, 15.45]),
        ('spirit', '精神', 'FLAT', 'SPIRIT', 'all', -1, '', [7.35, 11.025, 14.7]),
        ('cooldown', '冷却', 'CHARACTER_PERCENT', 'COOLDOWN_RECOVERY', 'CHEST', -1, '', [.05, .075, .1]),
        ('movement', '疾行', 'CHARACTER_PERCENT', 'MOVEMENT_SPEED', 'FEET', -1, '', [.05, .075, .1]),
        ('critical_chance', '会心', 'MODIFIER', None, 'all', -1, 'crit', [None, .03, .05]),
        ('critical_damage', '破势', 'MODIFIER', None, 'all', -1, 'critDamage', [None, .1, .2]),
    ] + [
        ('resist_' + key, name + '抗性', 'FLAT', key.upper() + '_RESIST', 'accessories', element, '', [9.6, 14.4, 19.2])
        for element, (key, name, _) in enumerate(elements)
    ] + [
        ('extra_' + key, name + '追击', 'EXTRA', None, 'WEAPON', element, '', [40.8, 61.2, 81.6])
        for element, (key, name, _) in enumerate(elements)
    ] + [
        ('element_' + key, name + '强化', 'FLAT', key.upper(), 'all', element, '', [None, 7.68, 11.52])
        for element, (key, name, _) in enumerate(elements)
    ]:
        for i, rarity in enumerate(['RARE', 'ARTIFACT', 'LEGENDARY']):
            if values[i] is None:
                continue
            affix = 'gem/' + rarity.lower() + '/' + key
            definitions[affix] = dict(id=affix, name=name, operation=operation, group='gem', stat=stat,
                                      element=element, fixedValue=values[i])
            maximum = values[i] * 2 if operation == 'EXTRA' else 0
            gems.append(dict(id='gem_' + rarity.lower() + '_' + key, name=name + '宝石', affix=affix,
                             rarity=rarity, group=group, parameter=parameter, value=values[i], maximum=maximum))

    definitions['movement_speed'] = dict(id='movement_speed', name='疾行', operation='CHARACTER_PERCENT',
                                        group='random', stat='MOVEMENT_SPEED', element=-1, minimum=.08, maximum=.12)
    for key, name, stat, operation, low, high, curve in [
        ('stamina_capacity', '充沛', 'STAMINA', 'FLAT', 100, 200, 'stamina_capacity'),
        ('stamina_recovery', '精力', 'STAMINA_RECOVERY', 'CHARACTER_PERCENT', 1, 3, None),
        ('shield_wait_recovery', '再启', 'SHIELD_COOLDOWN_RECOVERY', 'CHARACTER_PERCENT', .035, .175, 'shield_wait_recovery'),
        ('shield_regeneration', '复苏', 'SHIELD_REGENERATION', 'CHARACTER_PERCENT', .2, .4, 'shield_regeneration')]:
        definitions[key] = dict(id=key, name=name, operation=operation, group='random', stat=stat,
                                element=-1, minimum=low, maximum=high, levelCurve=curve)
    for key, name, operation, stat, low, high, fixed in [
        ('elaina_shield', '巫师护盾', 'PERCENT', 'SHIELD', 2.5, 3.5, None),
        ('elaina_intelligence', '巫师智慧', 'CHARACTER_TOTAL', 'INTELLIGENCE', .2, .3, None),
        ('elaina_magic', '巫师魔力', 'CHARACTER_TOTAL', 'MAGICAL_ATTACK', .05, .08, None),
        ('collapse_armor', '倾塌壁垒', 'PERCENT', 'PHYSICAL_DEFENSE', 1, 2.5, None),
        ('armor_retaliation', '护甲反击', 'EQUIPMENT_RULE', None, 10, 20, None),
        ('wall_power', '倾塌之墙', 'EQUIPMENT_RULE', None, None, None, .01)]:
        definitions['unique/' + key] = dict(id='unique/' + key, name=name, operation=operation,
                                           group='unique', stat=stat, element=-1,
                                           minimum=low, maximum=high, fixedValue=fixed)
    write(DATA / 'equipment/currency_crafting.json', dict(essences=essences, gems=gems))
    equipment_path = DATA / 'rules/equipment.json'
    equipment = json.loads(equipment_path.read_text(encoding='utf-8'))
    equipment['affixes'].update(definitions)
    write(equipment_path, equipment)

    colors = {'RARE': 0x438EFF, 'ARTIFACT': 0xC777FF, 'LEGENDARY': 0xFFB84D}
    labels = {}
    for item in essences + gems:
        key = item['id']
        labels['item.' + NS + '.' + key] = item['name']
        texture = 'minecraft:item/amethyst_shard' if item in gems else 'minecraft:item/honey_bottle'
        write(ASSETS / ('models/item/' + key + '.json'), dict(parent='minecraft:item/generated', textures={'layer0': texture}))
        model = dict(type='minecraft:model', model=NS + ':item/' + key,
                     tints=[dict(type='minecraft:constant', value=colors.get(item.get('rarity'), 0xFFB84D))])
        write(ASSETS / ('items/' + key + '.json'), dict(model=model))
    for key, name, tint in [('advanced_divine_stone', '高级神圣石', 0x98E8FF),
                            ('perfect_divine_stone', '完美神圣石', 0xFFE9A5)]:
        labels['item.' + NS + '.' + key] = name
        write(ASSETS / ('models/item/' + key + '.json'), dict(parent='minecraft:item/generated',
              textures={'layer0': NS + ':item/divine_stone'}))
        write(ASSETS / ('items/' + key + '.json'), dict(model=dict(type='minecraft:model',
              model=NS + ':item/' + key, tints=[dict(type='minecraft:constant', value=tint)])))
    labels['block.' + NS + '.horadric_cube'] = '赫拉迪姆方块'
    labels['item.' + NS + '.horadric_cube'] = '赫拉迪姆方块'
    for language in ['zh_cn', 'en_us']:
        path = ASSETS / ('lang/' + language + '.json')
        data = json.loads(path.read_text(encoding='utf-8'))
        data.update(labels)
        write(path, data)
    write(ASSETS / 'blockstates/horadric_cube.json', {'variants': {'': {'model': NS + ':block/horadric_cube'}}})
    write(ASSETS / 'models/block/horadric_cube.json', {
        'parent': 'minecraft:block/cube',
        'textures': {'particle': 'minecraft:block/obsidian', 'side': 'minecraft:block/obsidian',
                     'top': 'minecraft:block/crafting_table_top', 'crystal': 'minecraft:block/amethyst_block'},
        'elements': [
            {'from': [0, 0, 0], 'to': [16, 13, 16], 'faces': {face: {'texture': '#top' if face == 'up' else '#side'} for face in ['up', 'down', 'north', 'south', 'east', 'west']}},
            {'from': [5, 13, 5], 'to': [11, 16, 11], 'faces': {face: {'texture': '#crystal'} for face in ['up', 'down', 'north', 'south', 'east', 'west']}}
        ]})
    write(ASSETS / 'models/item/horadric_cube.json', {'parent': NS + ':block/horadric_cube'})
    write(ASSETS / 'items/horadric_cube.json', {'model': {'type': 'minecraft:model', 'model': NS + ':item/horadric_cube'}})
    write(DATA / 'recipe/horadric_cube.json', {'type': 'minecraft:crafting_shaped', 'category': 'misc',
          'pattern': ['OCO', 'OWO', 'OOO'], 'key': {'O': 'minecraft:obsidian', 'C': NS + ':energy_crystal_4', 'W': 'minecraft:crafting_table'},
          'result': {'id': NS + ':horadric_cube', 'count': 1}})
    write(DATA / 'loot_table/blocks/horadric_cube.json', {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': NS + ':horadric_cube'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    named_armor_models()


def named_armor_models():
    # The NPC and equipped player both render this one native head model.
    parts = [
        (0,(-8,-9.25,-8,16,1,16)),(0,(-4.6,-13,-4.6,9.2,4,9.2)),
        (0,(-3.5,-17,-3.5,7,4,7)),(0,(-2.5,-20,-3,5,3,5)),
        (0,(-1,-22,-2.5,4,2,4)),(0,(1,-23.5,-2,4,1.5,3)),
        (1,(-4.75,-11.2,-4.75,9.5,1.5,9.5)),
        (2,(-1.1,-11.3,-4.95,2.2,.4,.25)),(2,(-1.1,-9.8,-4.95,2.2,.4,.25)),
        (2,(-1.1,-10.9,-4.95,.4,1.1,.25)),(2,(.7,-10.9,-4.95,.4,1.1,.25)),
    ]
    elements=[]
    for tint,(x,y,z,w,h,d) in parts:
        elements.append({'from':[x+8,-y-h+8,-z-d+8],'to':[x+w+8,-y+8,-z+8],
                         'faces':{face:{'texture':'#wool','uv':[0,0,16,16],'tintindex':tint} for face in ['up','down','north','south','east','west']}})
    model={'textures':{'wool':'minecraft:block/white_wool','particle':'minecraft:block/white_wool'},'elements':elements,
           'display':{'head':{'rotation':[0,180,0],'translation':[0,-6.4,0],'scale':[1.6,1.6,1.6]},
                      'gui':{'rotation':[20,30,0],'translation':[0,-10,0],'scale':[.7,.7,.7]},
                      'fixed':{'rotation':[0,0,0],'translation':[0,-8,0],'scale':[.6,.6,.6]},
                      'ground':{'translation':[0,-6,0],'scale':[.4,.4,.4]},
                      'firstperson_righthand':{'rotation':[0,-20,0],'translation':[0,-6,0],'scale':[.6,.6,.6]},
                      'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,-8,0],'scale':[.5,.5,.5]}}}
    write(ASSETS/'models/item/named/elaina_witch_hat.json',model)
    write(ASSETS/'models/item/named/pigeon_collapsing_wall.json',{'parent':NS+':item/armor/plate_chest'})
    for slot,appearance in [('head','elaina_witch_hat'),('chest','pigeon_collapsing_wall')]:
        path=ASSETS/f'items/{slot}_armor.json';item=json.loads(path.read_text(encoding='utf-8'))
        cases=item['model']['cases'];cases[:]=[entry for entry in cases if entry['when']!=appearance]
        entry={'type':'minecraft:model','model':NS+':item/named/'+appearance}
        if slot=='head':entry['tints']=[{'type':'minecraft:constant','value':color} for color in [0x332B45,0x977B9D,0xDDBF87]]
        cases.append({'when':appearance,'model':entry});write(path,item)


if __name__ == '__main__':
    build()

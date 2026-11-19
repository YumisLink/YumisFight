"""Production geometry, art and recipes for the buddy generator and oil drill."""
from dataclasses import dataclass
from itertools import product


MACHINES = {
    'buddy_generator_shell': ('发电机空壳', 'Empty Generator'),
    'buddy_generator': ('群友发电机', 'Buddy Generator'),
    'oil_drill': ('石油钻井', 'Oil Drill'),
}
LIQUIDS = {
    'crude_oil': ('原油', 'Crude Oil', (93, 63, 54)),
    'naphtha': ('石脑油', 'Naphtha', (217, 152, 68)),
    'liquid_ethylene': ('低温液态乙烯', 'Cryogenic Liquid Ethylene', (129, 217, 238)),
}
CONTENT = {'plastic': ('item', 2, '塑料')} | {k: ('fluid', 2, v[0]) for k, v in LIQUIDS.items()}
PROCESS_NAMES = {'refine_naphtha': '原油精炼', 'crack_ethylene': '乙烯裂解与冷却', 'polymerize_plastic': '塑料聚合'}
FACINGS = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
FACES = {
    'down': (1, 0, 0, 2, False, True), 'up': (1, 1, 0, 2, False, False),
    'north': (2, 0, 0, 1, True, True), 'south': (2, 1, 0, 1, False, True),
    'west': (0, 0, 2, 1, False, True), 'east': (0, 1, 2, 1, True, True),
}


def recipes(shaped, process):
    shaped('buddy_generator_shell', ['PGP', 'VHV', 'PDP'], {
        'P': 'plastic', 'G': 'minecraft:glass', 'V': 'vanadium_steel',
        'H': 'machine_casing_t2', 'D': 'mana_component',
    })
    shaped('oil_drill', ['VPV', 'DHD', 'VFV'], {
        'V': 'vanadium_steel', 'P': 'minecraft:piston', 'D': 'mana_component',
        'H': 'machine_casing_t2', 'F': 'reinforced_frame',
    })
    process('refine_naphtha', 'mineral_refinery', [], [], 2, ticks=200, energy=1200,
            fluids=[('crude_oil', 1000)], fluid_out=('naphtha', 500))
    process('crack_ethylene', 'mineral_refinery', [], [], 2, ticks=200, energy=1600,
            fluids=[('naphtha', 500)], fluid_out=('liquid_ethylene', 250))
    process('polymerize_plastic', 'mineral_refinery', [], [('plastic', 4)], 2, ticks=200, energy=1600,
            fluids=[('liquid_ethylene', 250)])


@dataclass(frozen=True)
class Box:
    start: tuple[float, float, float]
    end: tuple[float, float, float]
    material: str
    front: str | None = None
    light: int = 0


def buddy_geometry():
    boxes = [
        Box((1, 2, 2), (31, 7, 46), 'steel', 'vent'),
        Box((0, 2, 3), (32, 4, 45), 'dark'),
        Box((3, 7, 4), (29, 8, 34), 'deck'),
        Box((5, 8, 5), (27, 25, 5.5), 'glass'),
        Box((5, 8, 5.5), (5.5, 25, 32), 'glass'),
        Box((26.5, 8, 5.5), (27, 25, 32), 'glass'),
        Box((5.5, 8, 32), (26.5, 25, 35), 'dark', 'deck'),
        Box((3, 8, 35), (29, 24, 45), 'steel', 'deck'),
        Box((4, 10, 44.9), (28, 22, 46), 'vent'),
        Box((2, 24.5, 3), (30, 26, 40), 'dark'),
        Box((1, 26, 2), (31, 30.5, 40), 'steel'),
        Box((6, 25.9, 1), (26, 30.25, 2), 'dark', 'buddy_sign', 6),
        Box((6, 30.5, 9), (26, 31.4, 35), 'dark'),
        Box((9, 31.4, 12), (23, 32, 33), 'ice', light=4),
        Box((.2, 9, 3), (4.8, 20, 9), 'steel', 'canister'),
        Box((.3, 8, 3.2), (4.7, 9, 8.8), 'dark'),
        Box((.3, 20, 3.2), (4.7, 21.25, 8.8), 'dark'),
        Box((1.5, 21.25, 4.75), (3.7, 26.75, 7.25), 'blue', light=3),
        Box((2.7, 24.25, 4.75), (6, 26.75, 7.25), 'blue', light=3),
        Box((28.2, 12, 4.75), (30.7, 24.8, 7.25), 'blue', light=3),
        Box((28.2, 23, 7.25), (30.7, 25.5, 32), 'blue', light=3),
        Box((27.1, 24, 31), (30.7, 27, 34), 'blue', light=3),
        Box((26.7, 16.25, 2), (31.65, 21.4, 4.9), 'dark', 'gauge'),
        Box((26.3, 8.3, 2.1), (31.6, 13.1, 5.3), 'steel', 'power'),
        Box((18.5, 3.25, 1.6), (26.5, 5.5, 2), 'dark', 'signal', 4),
    ]
    for x, z in product((2, 25), (4, 37)):
        boxes.append(Box((x, 0, z), (x + 5, 2, z + 6), 'rubber'))
    for x, z in product((4, 26), (4.5, 31.5)):
        boxes.append(Box((x, 8, z), (x + 2, 24.5, z + 2), 'steel'))
    for z in (12, 18, 26):
        boxes.append(Box((27.8, 22.7, z), (31.1, 25.8, z + 1.3), 'dark'))
    # The viewer faces south when the machine faces north.
    return [Box((32 - box.end[0], *box.start[1:]), (32 - box.start[0], *box.end[1:]),
                box.material, box.front, box.light) for box in boxes]


def drill_geometry():
    boxes = [
        Box((2, 1, 2), (46, 6, 46), 'dark', 'caution'),
        Box((3, 6, 3), (45, 8, 45), 'deck'),
        Box((13, 0, 13), (35, 4, 35), 'steel'),
        Box((19, 0, 19), (29, 1, 29), 'copper'),
        Box((4, 8, 13), (13, 17, 33), 'steel', 'vent'),
        Box((6, 17, 15), (12, 18, 31), 'dark'),
        Box((35, 8, 14), (43, 19, 35), 'dark', 'oil'),
        Box((34, 18, 13), (44, 20, 36), 'steel'),
        Box((18, 8, 18), (30, 9.5, 30), 'caution'),
        Box((20, 9.5, 20), (28, 10.75, 28), 'dark'),
        Box((5, 38, 6), (43, 41, 10), 'caution'),
        Box((5, 38, 38), (43, 41, 42), 'caution'),
        Box((6, 38, 10), (10, 41, 38), 'steel'),
        Box((38, 38, 10), (42, 41, 38), 'steel'),
        Box((11, 39, 12), (37, 45, 36), 'steel', 'vent'),
        Box((16, 45, 16), (32, 48, 32), 'dark', 'power'),
        Box((17, 34, 17), (31, 39, 31), 'dark'),
        Box((19, 32.75, 19), (29, 35, 29), 'copper'),
        Box((9, 13, 30), (19, 16, 33), 'copper'),
        Box((29, 11, 31), (39, 14, 34), 'copper'),
        Box((30, 10, 27), (33, 14, 33), 'copper'),
        Box((14, 3, 1.75), (27, 5, 2), 'dark', 'signal', 4),
    ]
    for x, z in product((4, 40), (4, 40)):
        boxes.extend((
            Box((x - 2, 0, z - 2), (x + 6, 2, z + 6), 'rubber'),
            Box((x, 7.5, z), (x + 4, 42, z + 4), 'steel'),
            Box((x - .5, 10, z - .5), (x + 4.5, 13, z + 4.5), 'caution'),
            Box((x - .5, 29, z - .5), (x + 4.5, 31, z + 4.5), 'dark'),
        ))
    for x in (6, 40):
        for step in range(11):
            height = 13 + step * 2.2
            for depth in (7 + step * 3.15, 39 - step * 3.15):
                boxes.append(Box((x, height, depth), (x + 2, height + 3.6, depth + 3.6), 'dark'))
    return boxes


def cells(size):
    width, height, depth = size
    return ((x, y, z) for y in range(height) for z in range(depth) for x in range(width))


def clipped(box, cell):
    origin = tuple(16 * c for c in cell)
    a = tuple(max(value, offset) for value, offset in zip(box.start, origin))
    b = tuple(min(value, offset + 16) for value, offset in zip(box.end, origin))
    return (a, b) if all(lo < hi for lo, hi in zip(a, b)) else None


def collision_boxes(boxes, cell):
    origin = tuple(16 * c for c in cell)
    result = []
    for box in boxes:
        section = clipped(box, cell)
        if section:
            result.append([round(value - offset, 4) for corner in section for value, offset in zip(corner, origin)])
    return result


ASSEMBLIES = {
    'buddy_generator': ((2, 2, 3), (0, 0, 0), buddy_geometry()),
    'oil_drill': ((3, 3, 3), (1, 0, 1), drill_geometry()),
}


def devices():
    result = {}
    for key, (size, anchor, boxes) in ASSEMBLIES.items():
        result[key] = dict(kind=key, tier=2, technology=2, processing=2, boxes=collision_boxes(boxes, anchor),
                           assembly=dict(size=size, anchor=anchor, parts=[
                               dict(cell=cell, boxes=collision_boxes(boxes, cell)) for cell in cells(size) if cell != anchor]))
    result['buddy_generator_shell'] = dict(kind='buddy_generator_shell', tier=2, technology=2, processing=2,
                                          boxes=result['buddy_generator']['boxes'])
    return result


TILES = {
    name: (i % 8 * 16, i // 8 * 16, 16, 16) for i, name in enumerate((
        'steel', 'dark', 'rubber', 'deck', 'ice', 'blue', 'glass', 'vent',
        'canister', 'gauge', 'power', 'caution', 'copper', 'oil', 'signal', 'lit',
    ))
}
TILES['buddy_sign'] = (0, 32, 48, 20)


def artwork(assets):
    from PIL import Image, ImageDraw
    palette = {
        'steel': ('#444f60', '#8d99aa', '#d7e0e8'), 'dark': ('#111925', '#28384a', '#475d72'),
        'rubber': ('#10161e', '#1c242b', '#373f47'), 'deck': ('#24364a', '#52697b', '#a1becb'),
        'ice': ('#29677d', '#7ebfce', '#d2fbff'), 'blue': ('#143969', '#248ec8', '#82cbf4'),
        'vent': ('#162331', '#566778', '#a7b7c8'), 'canister': ('#3e5664', '#96b1ba', '#e0edf1'),
        'gauge': ('#27354b', '#687480', '#c0d1d7'), 'power': ('#1d2b3c', '#52687d', '#96aab8'),
        'caution': ('#584522', '#a47a33', '#e7bb60'), 'copper': ('#554039', '#a77a58', '#d5b58b'),
        'oil': ('#1d1a24', '#3c3037', '#9e7658'), 'signal': ('#101d2e', '#293c4c', '#3e6178'),
        'lit': ('#0a2440', '#2781a2', '#83e4ec'),
    }
    atlas = Image.new('RGBA', (128, 64))
    for name, (shadow, middle, edge) in palette.items():
        x, y, _, _ = TILES[name]
        tile = Image.new('RGBA', (16, 16), middle)
        pen = ImageDraw.Draw(tile)
        pen.rectangle((0, 0, 15, 15), outline=shadow)
        pen.line((1, 1, 14, 1), fill=edge)
        pen.line((1, 1, 1, 13), fill=edge)
        pen.line((2, 14, 14, 14), fill=shadow)
        if name in ('steel', 'deck', 'copper'):
            for spot in ((2, 3), (13, 3), (2, 12), (13, 12)):
                pen.point(spot, fill=shadow)
            pen.line((4, 12, 11, 12), fill=edge)
        if name == 'vent':
            for row in (4, 7, 10):
                pen.rectangle((3, row, 12, row + 1), fill=shadow)
        if name == 'blue':
            pen.rectangle((2, 4, 13, 9), fill=edge)
            pen.line((2, 10, 13, 10), fill='#348fc1')
        if name == 'caution':
            for column in range(-8, 13, 6):
                pen.polygon([(column, 13), (column + 3, 13), (column + 13, 2), (column + 10, 2)], fill='#30343a')
        if name == 'gauge':
            pen.ellipse((2, 2, 13, 13), fill='#d2deda', outline='#263947')
            pen.line((7, 8, 11, 5), fill='#137eac', width=1)
            pen.point([(5, 5), (4, 8), (8, 4), (11, 7), (8, 11)], fill='#385367')
        if name == 'canister':
            pen.polygon([(8, 3), (5, 8), (8, 8), (6, 12), (12, 6), (8, 6)], fill='#2385aa')
        if name in ('power', 'signal', 'lit', 'oil'):
            pen.rectangle((3, 4, 12, 11), fill=shadow)
            for i in range(4):
                pen.rectangle((4 + i * 2, 9 - i, 4 + i * 2, 10), fill=edge)
        atlas.paste(tile, (x, y))

    x, y, _, _ = TILES['glass']
    glass = Image.new('RGBA', (16, 16), (88, 176, 221, 22))
    pen = ImageDraw.Draw(glass)
    pen.rectangle((0, 0, 15, 15), outline=(69, 132, 169, 150))
    pen.line((2, 6, 6, 2), fill=(181, 232, 244, 105))
    pen.line((2, 9, 5, 6), fill=(149, 213, 235, 65))
    pen.line((10, 13, 13, 10), fill=(149, 213, 235, 65))
    atlas.paste(glass, (x, y))

    sign = Image.new('RGBA', (48, 20), '#101e2b')
    pen = ImageDraw.Draw(sign)
    pen.rectangle((1, 1, 46, 18), outline='#4b7086')
    pen.polygon([(6, 3), (11, 3), (7, 9), (11, 9), (4, 17), (5, 11), (2, 11)], fill='#a3eff6')
    # The two character masks are from Minecraft's standard GNU Unifont glyphs.
    glyphs = (
        '00827E44122812FEFF10121012107E7C201020107E1062FEA21022103E102210',
        '020002000200FFFE040004000FF00A1012101120214040808140062018186006',
    )
    for x, glyph in zip((12, 29), glyphs):
        for y in range(16):
            row = int(glyph[y*4:y*4+4], 16)
            for column in range(16):
                if row & (0x8000 >> column):
                    pen.point((x + column, y + 2), fill='#a3eff6')
    atlas.paste(sign, TILES['buddy_sign'][:2])
    atlas_path = assets / 'textures/block/petrochemistry/atlas.png'
    atlas_path.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(atlas_path)

    plastic = Image.new('RGBA', (16, 16))
    draw = ImageDraw.Draw(plastic)
    draw.polygon([(1, 10), (9, 5), (14, 8), (14, 11), (7, 15), (1, 12)], fill='#183745')
    draw.polygon([(2, 10), (9, 6), (13, 9), (7, 13)], fill='#86b6be')
    draw.polygon([(3, 7), (10, 2), (15, 5), (15, 8), (8, 13), (3, 10)], fill='#2c687b')
    draw.polygon([(4, 7), (10, 3), (14, 5), (8, 10)], fill='#c7edeb')
    draw.line((8, 10, 14, 6), fill='#77b6c4')
    draw.point([(9, 5), (10, 5), (11, 4)], fill='#e9fbf9')
    plastic.save(assets / 'textures/item/plastic.png')

    bucket = Image.open(assets / 'textures/item/coolant_bucket.png').convert('RGBA')
    for name, (_, _, color) in LIQUIDS.items():
        sprite = bucket.copy()
        for y in range(sprite.height):
            for x in range(sprite.width):
                r, g, b, a = bucket.getpixel((x, y))
                if a and g > r + 18 and b > r + 18:
                    shade = (r + g + b) / 525
                    sprite.putpixel((x, y), tuple(min(255, round(c * shade)) for c in color) + (a,))
        sprite.save(assets / f'textures/item/{name}_bucket.png')

    ram = Image.new('RGBA', (64, 64), '#48596a')
    draw = ImageDraw.Draw(ram)
    for col in range(64):
        color = ('#445567', '#8194a5', '#bdc9ce', '#627688')[col % 4]
        draw.line((col, 0, col, 63), fill=color)
    draw.rectangle((0, 38, 23, 42), fill='#af8248')
    draw.rectangle((28, 6, 55, 16), fill='#435063')
    draw.rectangle((28, 7, 55, 9), fill='#7b8d9d')
    ram.save(assets / 'textures/block/petrochemistry/drill_ram.png')


def element(box, section, origin, scale, lit=False):
    lo, hi = section
    faces = {}
    for face, (normal, side, u, v, flip_u, flip_v) in FACES.items():
        if (lo, hi)[side][normal] != (box.start, box.end)[side][normal]:
            continue
        mat = box.front if face == 'north' and box.front else box.material
        if lit and mat == 'signal':
            mat = 'lit'
        x, y, width, height = TILES[mat]
        uv = []
        for axis, flip, offset, length, atlas_size in ((u, flip_u, x, width, 128), (v, flip_v, y, height, 64)):
            size = box.end[axis] - box.start[axis]
            a = (lo[axis] - box.start[axis]) / size
            b = (hi[axis] - box.start[axis]) / size
            if flip:
                a, b = 1 - b, 1 - a
            uv.append((round((offset + a * length) * 16 / atlas_size, 5),
                       round((offset + b * length) * 16 / atlas_size, 5)))
        faces[face] = dict(texture='#atlas', uv=[uv[0][0], uv[1][0], uv[0][1], uv[1][1]])
    if not faces:
        return None
    result = {'from': [round((a - b) * scale, 4) for a, b in zip(lo, origin)],
              'to': [round((a - b) * scale, 4) for a, b in zip(hi, origin)], 'faces': faces}
    if box.light:
        result['light_emission'] = box.light
    return result


def model(boxes, own, cell=None, lit=False, item=False):
    origin = (0, 0, 0) if cell is None else tuple(16 * c for c in cell)
    if item:
        origin = tuple((max(box.end[axis] for box in boxes) - 48) / 2 for axis in range(3))
    elements = []
    for box in boxes:
        section = (box.start, box.end) if cell is None else clipped(box, cell)
        if section and (part := element(box, section, origin, 1 / 3 if item else 1, lit)):
            elements.append(part)
    texture = own('block/petrochemistry/atlas')
    return dict(parent='minecraft:block/block', render_type='minecraft:translucent',
                textures=dict(atlas=texture, particle=texture), elements=elements)


def resources(assets, res, own, write, read):
    artwork(assets)
    part_states = {}
    index = 0
    for key, (size, anchor, boxes) in ASSEMBLIES.items():
        for cell in cells(size):
            if cell == anchor:
                continue
            path = f'block/petrochemistry/part_{index}'
            write(assets / f'models/{path}.json', model(boxes, own, cell))
            for facing, angle in FACINGS.items():
                part_states[f'part={index},facing={facing}'] = dict(model=own(path), y=angle)
            index += 1
        identities = ('buddy_generator_shell', key) if key == 'buddy_generator' else (key,)
        for machine in identities:
            path = f'block/petrochemistry/{machine}'
            states = {}
            for active in (False, True):
                suffix = '_active' if active else ''
                write(assets / f'models/{path}{suffix}.json', model(boxes, own, anchor, lit=active))
                for facing, angle in FACINGS.items():
                    states[f'facing={facing},active={str(active).lower()}'] = dict(model=own(path + suffix), y=angle)
            write(assets / f'blockstates/{machine}.json', dict(variants=states))
            write(assets / f'models/item/{machine}.json', model(boxes, own, lit=machine != 'buddy_generator_shell', item=True))
            write(assets / f'items/{machine}.json', dict(model=dict(type='minecraft:model', model=own(f'item/{machine}'))))
    write(assets / 'blockstates/industrial_assembly_part.json', dict(variants=part_states))
    for name in ['plastic'] + [key + '_bucket' for key in LIQUIDS]:
        write(assets / f'items/{name}.json', dict(model=dict(type='minecraft:model', model=own(f'item/{name}'))))
        write(assets / f'models/item/{name}.json', dict(parent='minecraft:item/generated', textures=dict(layer0=own('item/' + name))))
    for key in LIQUIDS:
        write(assets / f'blockstates/{key}.json', dict(variants={'': dict(model=own('block/' + key))}))
        write(assets / f'models/block/{key}.json', dict(textures=dict(particle='minecraft:block/water_still')))

    for index, locale in enumerate(('zh_cn', 'en_us')):
        path = assets / f'lang/{locale}.json'
        lang = read(path)
        ns = own('plastic').split(':', 1)[0]
        for key, names in MACHINES.items():
            lang[f'block.{ns}.{key}'] = names[index]
        lang[f'block.{ns}.industrial_assembly_part'] = ('工业设备部件', 'Industrial Machine Part')[index]
        lang[f'item.{ns}.plastic'] = ('塑料', 'Plastic')[index]
        for key, names in LIQUIDS.items():
            title = names[index]
            lang[f'block.{ns}.{key}'] = title
            lang[f'fluid_type.{ns}.{key}'] = title
            lang[f'item.{ns}.{key}_bucket'] = title + '桶' if index == 0 else title + ' Bucket'
        for key, name in PROCESS_NAMES.items():
            lang[f'recipe.{ns}.industrial.{key}'] = (name, key.replace('_', ' ').title())[index]
        lang[f'item.{ns}.buddy_generator_shell.hint'] = (
            '手持空壳对猪右键，装入群友', 'Use on a pig to activate the generator')[index]
        lang[f'item.{ns}.buddy_generator_shell.captured'] = ('群友已装载', 'Buddy loaded')[index]
        lang[f'block.{ns}.buddy_generator.panel.empty'] = ('未装载', 'EMPTY')[index]
        lang[f'block.{ns}.buddy_generator.panel.disconnected'] = ('未连接', 'UNLINKED')[index]
        lang[f'block.{ns}.buddy_generator.panel.idle'] = ('已停止', 'STOPPED')[index]
        write(path, lang)

    path = res / 'data/minecraft/tags/block/mineable/pickaxe.json'
    tag = read(path)
    for key in [*MACHINES, 'industrial_assembly_part']:
        if own(key) not in tag['values']:
            tag['values'].append(own(key))
    write(path, tag)

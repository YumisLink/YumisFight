"""Authoring source for the fourteen altar-boss items: six unlock tokens, two offering supplies, six boss materials.

Pixel grids below are the editable art source; each icon is 16x16 with transparency.
Run to (re)generate textures, item models and lang entries (other lang entries are preserved)."""
from pathlib import Path
import json
import struct
import zlib

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/yumis_dungeon_and_fight'
NS = 'yumis_dungeon_and_fight'
PREVIEW = ROOT / 'temp/altar-boss-icons'

PALETTE = {
    'k': (40, 34, 30),        # ink outline
    'b': (110, 96, 82),       # bark
    'd': (82, 70, 58),        # bark dark
    'm': (146, 190, 96),      # moss
    'g': (206, 240, 150),     # moss glow
    'c': (188, 120, 74),      # copper
    'C': (140, 84, 52),       # copper dark
    'u': (216, 150, 98),      # copper bright
    'a': (196, 150, 68),      # brass
    'w': (232, 228, 214),     # bone white
    's': (196, 190, 176),     # bone shade
    'q': (236, 232, 220),     # scale edge light
    'p': (160, 104, 214),     # amethyst
    'P': (118, 72, 168),      # amethyst dark
    'y': (238, 196, 88),      # gold
    'o': (96, 70, 44),        # oak
    'O': (72, 52, 32),        # oak dark
    'e': (238, 120, 42),      # ember
    'E': (150, 58, 28),       # ember deep
    'f': (252, 180, 80),      # ember glow
    'n': (44, 34, 58),        # obsidian
    'N': (88, 70, 120),       # obsidian edge
    'i': (96, 180, 190),      # pale cyan
    'I': (150, 220, 228),     # cyan bright
    'h': (240, 236, 224),     # page white
    't': (228, 222, 206),     # clock face
    'T': (188, 182, 168),     # clock face shade
    'r': (200, 60, 60),       # red
    'x': (150, 150, 158),     # gray metal
    'X': (104, 104, 112),     # gray dark
}

GRIDS = {}
GRIDS['altar_token_uncrowned_gardener'] = [
    '................',
    '...........au...',
    '..........dwu...',
    '.........dwb....',
    '........dwb..m..',
    '......dwb...g...',
    '.....dwb....m...',
    '....dwb.........',
    '...dwb..........',
    '..dwb...........',
    '.dwb............',
    '.db.............',
    '.d..............',
    '................',
    '................',
    '................',
]
GRIDS['altar_token_off_duty_matron'] = [
    '................',
    '.....uuuuuu.....',
    '....u......u....',
    '...u..cccc..u...',
    '...u.c....c.u...',
    '...u.c.uu.c.u...',
    '...u.c.uu.c.u...',
    '...u.c....c.u...',
    '...u..cccc..u...',
    '....u......u....',
    '.....uuuuuu.....',
    '.......CC.......',
    '......CCCC......',
    '................',
    '................',
    '................',
]
GRIDS['altar_token_white_reliquary'] = [
    '................',
    '......PpP.......',
    '.....Pppp.......',
    '.....pppP.......',
    '....wwwwwww.....',
    '...wwsswwwsw....',
    '...wsswwwsww....',
    '...wwwwwwwww..h.',
    '...wwwwwwwww.hh.',
    '...wsswwwssw....',
    '..wsswwwwssw....',
    '..wwwwwwwwww....',
    '..ssssssssss....',
    '................',
    '................',
    '................',
]
GRIDS['altar_token_debt_collector'] = [
    '.......yy.......',
    '.......yy.......',
    '.....yyyyyy.....',
    '....yyttttyy....',
    '...yyttttttyy...',
    '...ytttttrtty...',
    '..ytt.rttttty...',
    '..yttrtttttty...',
    '..yttrtttttty...',
    '...yttrtttty....',
    '...yyttttttyy...',
    '....yyttttyy....',
    '.....yyyyyy.....',
    '................',
    '................',
    '................',
]
GRIDS['altar_token_fallen_scale_king'] = [
    '................',
    '......qqqq......',
    '.....qwwwwq.....',
    '....qwwwwwwq....',
    '...qwwwwwwwwq...',
    '...qwwewwwewq...',
    '..qwwwewwweww...',
    '..qwwewwewwew...',
    '..qwwwwewwwww...',
    '...qwwwwewwwq...',
    '...qqwwwwwwqq...',
    '.....qwwww......',
    '......qww.......',
    '.......q........',
    '................',
    '................',
]
GRIDS['altar_token_thousandfold_confession'] = [
    '................',
    '....OOOOOOOO....',
    '....OooooooO....',
    '....OoiiiooO....',
    '....OoiiiooO....',
    '....OooooooO....',
    '....OoOOOOoO....',
    '....OoOOOOoO....',
    '....OoOOOOoO....',
    '....OoOOOOoO....',
    '....OooooooO....',
    '....OOOOOOOO....',
    '....O.O..O.O....',
    '................',
    '................',
    '................',
]
GRIDS['warm_dragon_scale'] = [
    '................',
    '......ffff......',
    '.....feeeef.....',
    '....feeeeeef....',
    '...feeeeeeeef...',
    '...feeffffeeE...',
    '..feeffffeeEE...',
    '..feeffffeeEE...',
    '..feeeeeeeeEE...',
    '...feeeeeeeE....',
    '...EEeeeeeEE....',
    '.....Eeef.......',
    '......EE........',
    '................',
    '................',
    '................',
]
GRIDS['penitent_shard'] = [
    '................',
    '.......NN.......',
    '......NNnN......',
    '.....NNnnN......',
    '....NNnnnN......',
    '....NnniiN......',
    '...NnniinnN.....',
    '...NnninnN......',
    '...NnnnnnN......',
    '....NnnnN.......',
    '....NnnN........',
    '.....NN.........',
    '................',
    '................',
    '................',
    '................',
]
GRIDS['lightmoss_scion'] = [
    '................',
    '..........gg....',
    '.......g..gmg...',
    '......mg..g.....',
    '.......dwb......',
    '......dwb.......',
    '.....dwb........',
    '....dwb.........',
    '...dwb..........',
    '..dwb...........',
    '.dwb............',
    '..d.............',
    '................',
    '................',
    '................',
    '................',
]
GRIDS['severance_spring'] = [
    '................',
    '................',
    '...uu.uu.uu.....',
    '..uCCuCCuCCu....',
    '..uCCuCCuCCu.y..',
    '..uCCuCCuCCuyy..',
    '..uCCuCCuCCu.y..',
    '...uu.uu.uu..C..',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
    '................',
]
GRIDS['unfinished_chant_core'] = [
    '................',
    '......ppp.......',
    '....ppiiipp.....',
    '...piiiiiip.....',
    '...piiiiiiip....',
    '..piiiiiiiip....',
    '..piiiiiiiip....',
    '..ppiiiiiipp....',
    '...ppiiipp..h...',
    '....pppp..hh....',
    '......h.........',
    '................',
    '................',
    '................',
    '................',
    '................',
]
GRIDS['stopped_clock_gear'] = [
    '................',
    '.......XX.......',
    '...X..xxxx..X...',
    '....XxtttxX.....',
    '....xxtttxx.....',
    '...xxtXtXtxx....',
    '...xxtXtXtxx....',
    '....xxtttxx.....',
    '....XxtttxX.....',
    '...X..xxxx..X...',
    '.......XX.......',
    '................',
    '................',
    '................',
    '................',
    '................',
]
GRIDS['ember_scale'] = [
    '................',
    '......XXX.......',
    '.....XxxxX......',
    '....XxxxxxX.....',
    '...XxxxexxxX....',
    '...xxxefexxx....',
    '..xxxefeefxxx...',
    '..xxfeeffeexx...',
    '..xxxfeeffxxx...',
    '...XxxefexxX....',
    '...XXxxxxxXX....',
    '.....XxxxX......',
    '......XX........',
    '................',
    '................',
    '................',
]
GRIDS['silent_chip'] = [
    '................',
    '................',
    '....nnnnnnnn....',
    '...nNNNNNNNn....',
    '...nNnnnnnNn....',
    '...nNniiinNn....',
    '...nNniIinNn....',
    '...nNniiinNn....',
    '...nNnnnnnNn....',
    '...nNNNNNNNn....',
    '....nnnnnnnn....',
    '................',
    '................',
    '................',
    '................',
    '................',
]

NAMES = {
    'altar_token_uncrowned_gardener': ('枯园冠枝', 'Withered Crown Branch'),
    'altar_token_off_duty_matron': ('末班发条', 'Last-Shift Clockwork'),
    'altar_token_white_reliquary': ('封页圣髑', 'Sealed-Page Reliquary'),
    'altar_token_debt_collector': ('负数裂表', 'Negative Broken Watch'),
    'altar_token_fallen_scale_king': ('续燃龙鳞', 'Kindled Dragon Scale'),
    'altar_token_thousandfold_confession': ('旧告解匣', 'Old Confessional Box'),
    'warm_dragon_scale': ('暖鳞', 'Warm Scale'),
    'penitent_shard': ('残忏', 'Penitent Shard'),
    'lightmoss_scion': ('光苔接穗', 'Lightmoss Scion'),
    'severance_spring': ('圣札铜簧', 'Sacrament Spring'),
    'unfinished_chant_core': ('遗咏晶核', 'Unfinished Chant Core'),
    'stopped_clock_gear': ('停表齿轮', 'Stopped-Clock Gear'),
    'ember_scale': ('余火硬鳞', 'Ember Scale'),
    'silent_chip': ('无声晶片', 'Silent Chip'),
}


def check_grids():
    for key, grid in GRIDS.items():
        if len(grid) != 16:
            raise SystemExit(f'{key}: needs 16 rows, has {len(grid)}')
        for row in grid:
            if len(row) != 16:
                raise SystemExit(f'{key}: row "{row}" has {len(row)} columns, needs 16')
            for char in row:
                if char != '.' and char not in PALETTE:
                    raise SystemExit(f'{key}: unknown palette char "{char}"')
        if key not in NAMES:
            raise SystemExit(f'{key}: missing display name')


def render(key):
    grid = GRIDS[key]
    return [[(0, 0, 0, 0) if char == '.' else PALETTE[char] + (255,) for char in row] for row in grid]


def encode_png(pixels):
    def chunk(key, data):
        return struct.pack('>I', len(data)) + key + data + struct.pack('>I', zlib.crc32(key + data) & 0xffffffff)
    height = len(pixels)
    width = len(pixels[0])
    raw = b''.join(b'\0' + bytes(c for pixel in row for c in pixel) for row in pixels)
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def generate():
    check_grids()
    for locale, index in (('zh_cn', 0), ('en_us', 1)):
        path = ASSETS / f'lang/{locale}.json'
        lang = json.loads(path.read_text(encoding='utf-8'))
        for key in GRIDS:
            lang[f'item.{NS}.{key}'] = NAMES[key][index]
        write(path, lang)
    for key in GRIDS:
        (ASSETS / f'textures/item/{key}.png').write_bytes(encode_png(render(key)))
        write(ASSETS / f'items/{key}.json', {'model': {'type': 'minecraft:model', 'model': f'{NS}:item/{key}'}})
        write(ASSETS / f'models/item/{key}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/{key}'}})
    sheet = [[(24, 24, 28, 255)] * (16 * 7) for _ in range(16 * 2)]
    for index, key in enumerate(GRIDS):
        art = render(key)
        for y, row in enumerate(art):
            for x, pixel in enumerate(row):
                sheet[(index // 7) * 16 + y][(index % 7) * 16 + x] = pixel
    PREVIEW.mkdir(parents=True, exist_ok=True)
    (PREVIEW / 'sheet.png').write_bytes(encode_png(sheet))
    print(f'altar boss items: wrote {len(GRIDS)} icons, models and lang entries; preview at {PREVIEW / "sheet.png"}')


if __name__ == '__main__':
    generate()

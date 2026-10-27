"""Check the shipped artifact as well as source assets, catching stale/broken JAR resources."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import argparse, json
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
PREFIX='assets/yumis_dungeon_and_fight/'
parser=argparse.ArgumentParser()
parser.add_argument('--jar',type=Path)
args=parser.parse_args()
archive=ZipFile(args.jar) if args.jar else None
def read(path):
    return archive.read(path) if archive else (ROOT/'src/main/resources'/path).read_bytes()

active=sorted((ROOT/'src/main/resources'/PREFIX/'textures/block/arcane').glob('*_active.png'))
assert len(active)==85
for path in active:
    key=PREFIX+'textures/block/arcane/'+path.name
    image=Image.open(BytesIO(read(key))).convert('RGBA')
    assert image.size==(32,384),key
    base=image.crop((0,0,32,16))
    assert base.getchannel('A').getextrema()==(255,255),key+' missing structure'
    for frame in range(12):
        assert image.crop((0,frame*32,32,frame*32+16)).tobytes()==base.tobytes(),key+' flashing structure'
    assert json.loads(read(key+'.mcmeta'))['animation']['height']==32

recipes=ROOT/'src/main/resources/data/yumis_dungeon_and_fight/recipe/industrial'
seen={}
for path in recipes.glob('*.json'):
    data=json.loads(path.read_text(encoding='utf-8'))
    assert not any(old in path.stem for old in ['machine_frame','functional_upgrade','transfer_upgrade','harvest_upgrade']),path
    if data['type'].endswith('crafting_shaped') or data['type'].endswith('machine_upgrade'):
        pattern=data['pattern'];key=data['key']
        layout=tuple(tuple(key.get(c,'') for c in row) for row in pattern)
        canonical=min(layout,tuple(tuple(reversed(row)) for row in layout))
        assert canonical not in seen,(path.name,seen.get(canonical))
        seen[canonical]=path.name
        if archive:
            assert json.loads(read('data/yumis_dungeon_and_fight/recipe/industrial/'+path.name))==data
names=[f'{n}_ingot' for n in ['lead','uranium','titanium','terra','spacetime']]+['raw_'+n for n in ['lead','uranium','titanium']]+['coolant_bucket']+[f'industrial_upgrade_{i}' for i in range(1,6)]+['nuclear_core','rtg_core']
for name in names:
    image=Image.open(BytesIO(read(PREFIX+'textures/item/'+name+'.png'))).convert('RGBA')
    assert image.size==(16,16),name
    assert image.getchannel('A').getextrema()==(0,255),name
for name in ['lead_ore','uranium_ore','titanium_ore','magic_ore']:
    image=Image.open(BytesIO(read(PREFIX+'textures/block/'+name+'.png')))
    assert image.size==(16,16),name
if archive:
    for name in archive.namelist():
        assert '/recipe/industrial/machine_frame_' not in name,name
print(json.dumps({'artifact':str(args.jar or 'source'),'active_atlases':85,'verified_frames':1020,'unique_shaped_recipes':len(seen),'material_icons':len(names)},ensure_ascii=False))

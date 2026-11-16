"""Display model generation and ownership of Aseprite-authored material art."""
from pathlib import Path
import json
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/yumis_dungeon_and_fight'
MATERIALS=frozenset(json.loads((ROOT/'tools/industrial_materials.json').read_text(encoding='utf-8'))['items'])

def stand_atlas():
    im=Image.new('RGBA',(32,32),(0,0,0,0));d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15),fill='#30293e');d.rectangle((1,1,14,14),outline='#5d4b66')
    d.line((2,3,13,3),fill='#796881');d.line((2,12,13,12),fill='#211d2d')
    d.rectangle((16,0,31,15),fill='#795b3c');d.rectangle((17,1,30,14),outline='#bf9c65',width=2)
    d.line((19,3,28,3),fill='#e2c696');d.line((19,12,28,12),fill='#544332')
    d.rectangle((0,16,15,31),fill='#494452');d.rectangle((1,17,14,30),outline='#8c8490')
    d.line((2,19,13,19),fill='#b4a8ad');d.line((2,28,13,28),fill='#302d38')
    d.rectangle((16,16,31,31),fill='#21383d');d.rectangle((17,17,30,30),outline='#487a7d')
    d.line((19,24,28,24),fill='#65b8b4');d.line((24,19,24,28),fill='#65b8b4')
    d.rectangle((23,23,25,25),fill='#b4e7d8');return im

def stand_model():
    def box(lo,hi,uv):return {'from':lo,'to':hi,'faces':{f:{'texture':'#atlas','uv':uv} for f in ['north','south','east','west','up','down']}}
    dark=[0,0,8,8];copper=[8,0,16,8];glass=[8,8,16,16];lamp=[11.5,11.5,12.5,12.5]
    # The whole silhouette fits the existing half-block collision shape.
    e=[box([1,0,1],[15,1,15],dark),box([3,1,3],[13,4,13],dark),
       box([2,4,2],[14,4.6,14],copper),box([3,4.6,3],[13,5,13],dark),
       box([4,5,4],[12,5.1,12],glass)]
    for x,z in [(1,1),(12,1),(1,12),(12,12)]:
        e.append(box([x,0,z],[x+3,1,z+3],copper))
        e.append(box([x+.5,1,z+.5],[x+2.5,7,z+2.5],dark))
        e.append(box([x,3.5,z],[x+3,4.5,z+3],copper))
        e.append(box([x,7,z],[x+3,8,z+3],copper))
    e.extend([box([5,2,2.95],[11,3,3],lamp),box([5,2,13],[11,3,13.05],lamp),
              box([2.95,2,5],[3,3,11],lamp),box([13,2,5],[13.05,3,11],lamp)])
    return {'ambientocclusion':True,'render_type':'minecraft:cutout',
      'textures':{'atlas':'yumis_dungeon_and_fight:block/display_stand_atlas','particle':'yumis_dungeon_and_fight:block/display_stand_atlas'},'elements':e}

def generate():
    # Material textures are exported by export_industrial_materials.lua.
    stand_atlas().save(ASSETS/'textures/block/display_stand_atlas.png')
    (ASSETS/'models/block/display_stand.json').write_text(json.dumps(stand_model(),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    from industrial_visual_models import generate_energy_crystals
    generate_energy_crystals(update_source=True)

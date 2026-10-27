"""Refresh presentation assets only; never change recipes, registries or saved data."""
from pathlib import Path
import json
import math

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
ASSETS = ROOT / 'src/main/resources/assets' / NS
SOURCE = ROOT / 'art/industry-refresh'
FAMILIES = ('mana_crusher mana_furnace mineral_generator currency_fabricator '
            'mana_forge mana_beacon coal_generator solar_collector turbine_generator '
            'wind_generator thermal_generator nuclear_reactor rtg_generator '
            'coolant_mixer transfer_crystal harvest_crystal').split()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def mesh():
    return {'vertices': [], 'faces': [], 'bands': []}


def crystal(data, center=(.5, .5, .5), radius=.19, height=.8, sides=8):
    x, y, z = center
    offset = len(data['vertices'])
    points = [(x, y-height*.5, z)]
    for level in (-.28, .25):
        points += [(x+radius*math.cos(i*math.tau/sides), y+height*level,
                    z+radius*math.sin(i*math.tau/sides)) for i in range(sides)]
    points += [(x, y+height*.5, z)]
    data['vertices'].extend(points)
    for i in range(sides):
        j = (i+1) % sides
        # Counter-clockwise when seen from outside, Y up in Minecraft.
        for face in ((0, 1+i, 1+j), (1+i, 1+sides+i, 1+sides+j, 1+j),
                     (1+sides+i, 1+2*sides, 1+sides+j)):
            data['faces'].append([offset+n for n in face])
            data['bands'].append(i)


def cube(data, lo, hi):
    x,y,z = lo; X,Y,Z = hi
    offset = len(data['vertices'])
    data['vertices'] += [(x,y,z),(X,y,z),(X,Y,z),(x,Y,z),
                         (x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)]
    for face in ((0,3,2,1),(4,5,6,7),(0,4,7,3),(1,2,6,5),(3,7,6,2),(0,1,5,4)):
        data['faces'].append([offset+n for n in face]); data['bands'].append(-1)


def export_obj(name, data):
    folder = ASSETS / 'models/industry'
    folder.mkdir(parents=True, exist_ok=True)
    lines = [f'mtllib {name}.mtl', f'o {name}']
    lines += ['v ' + ' '.join(f'{x:.6f}' for x in vertex) for vertex in data['vertices']]
    # One isolated UV island per face. Inset into each band to avoid atlas bleeding.
    for face, band in zip(data['faces'], data['bands']):
        u0,u1 = ((band+.18)/8, (band+.82)/8) if band>=0 else (.08,.92)
        uv = [(u0,.1),(u0,.9),(u1,.9),(u1,.1)] if len(face)==4 else [(u0,.15),((u0+u1)/2,.9),(u1,.15)]
        lines += [f'vt {u:.6f} {v:.6f}' for u,v in uv]
    vt = 1
    for face, band in zip(data['faces'], data['bands']):
        lines.append('usemtl ' + ('crystal' if band>=0 else 'base'))
        lines.append('f ' + ' '.join(f'{v+1}/{vt+i}' for i,v in enumerate(face)))
        vt += len(face)
    (folder / f'{name}.obj').write_text('\n'.join(lines)+'\n', encoding='utf-8')
    (folder / f'{name}.mtl').write_text(
        'newmtl crystal\nKd 1 1 1\nmap_Kd #crystal\n'
        'newmtl base\nKd 1 1 1\nmap_Kd #base\n', encoding='utf-8')


DISPLAY = {
    'gui': {'rotation': [16,35,0], 'translation': [0,0,0], 'scale': [.95,.95,.95]},
    'ground': {'translation': [0,2,0], 'scale': [.5,.5,.5]},
    'fixed': {'rotation': [0,0,0], 'scale': [1,1,1]},
    'thirdperson_righthand': {'rotation': [0,0,0], 'translation': [0,2,1], 'scale': [.65,.65,.65]},
    'thirdperson_lefthand': {'rotation': [0,0,0], 'translation': [0,2,1], 'scale': [.65,.65,.65]},
    'firstperson_righthand': {'rotation': [0,25,-8], 'translation': [1,2,0], 'scale': [.75,.75,.75]},
    'firstperson_lefthand': {'rotation': [0,-25,8], 'translation': [1,2,0], 'scale': [.75,.75,.75]}}


def device_model(family, tier, suffix=''):
    textures = {'particle':f'{NS}:block/machine_side_{tier}',
        'side':f'{NS}:block/machine_side_{tier}', 'top':f'{NS}:block/{family}_top',
        'front':f'{NS}:block/{family}{suffix}', 'trim':f'{NS}:block/industrial_trim_{tier}'}
    elements=[]
    def box(lo,hi,material='body',rotation=None):
        faces={}
        for face in ['north','south','east','west','up','down']:
            if material=='body':
                texture='#top' if face=='up' else '#front' if face=='north' else '#side';uv=[0,0,16,16]
            else:
                texture='#trim';band={'stone':0,'metal':1,'core':2,'dark':3}[material]
                uv=[0,band*4,16,(band+1)*4]
            faces[face]={'texture':texture,'uv':uv}
        e={'from':list(lo),'to':list(hi),'faces':faces}
        if rotation:e['rotation']=rotation
        elements.append(e)
    def plinth():
        box((1,0,1),(15,2,15),'stone');box((2,2,2),(14,3,14),'metal')
    def posts(y0,y1):
        for x,z in ((2,2),(12,2),(2,12),(12,12)):box((x,y0,z),(x+2,y1,z+2),'metal')
    if family=='mana_crusher':
        plinth();box((2,3,7),(14,12,14));box((3,5,2),(6,11,8),'metal');box((10,5,2),(13,11,8),'metal')
        box((4,11,4),(12,13,13),'dark');box((2,12,3),(14,14,14),'metal');box((4,14,5),(12,16,12),'stone')
        box((7,5,3),(9,9,6),'core')
    elif family=='mana_furnace':
        plinth();box((2,3,3),(14,12,14));box((1,12,2),(15,14,15),'stone')
        box((10,14,10),(14,16,14),'metal');box((5,4,2),(11,9,3),'core');box((4,3,1),(12,4,5),'metal')
    elif family=='mineral_generator':
        plinth();box((3,3,10),(13,13,14));box((5,3,4),(11,12,10),'core');posts(3,13)
        box((3,12,3),(13,14,13),'metal');box((6,14,6),(10,16,10),'stone')
    elif family=='equipment_fabricator':
        textures.update(side=f'{NS}:block/fabricator_side',front=f'{NS}:block/fabricator_side',top=f'{NS}:block/fabricator_top')
        posts(0,9);box((0,9,0),(16,12,16));box((2,12,10),(5,16,13),'metal');box((11,12,10),(14,16,13),'metal')
        box((5,12,11),(11,14,14),'core');box((5,3,5),(11,5,11),'stone')
    elif family=='currency_fabricator':
        posts(0,6);box((0,6,1),(16,8,15));box((2,8,8),(4,15,11),'metal');box((12,8,8),(14,15,11),'metal')
        box((2,14,8),(14,16,12),'stone');box((6,10,6),(10,14,10),'metal');box((6,8,4),(10,9,8),'core')
        box((3,3,7),(13,5,14),'dark')
    elif family=='mana_forge':
        # Full-footprint casing: retains the existing 3x3x3 multiblock appearance.
        box((0,0,0),(16,16,16));box((0,0,0),(16,2,16),'metal');box((0,14,0),(16,16,16),'metal')
        for x in [0,14]:box((x,2,0),(x+2,14,2),'stone')
        box((4,5,0),(12,11,1),'core');box((6,3,0),(10,13,.5),'metal')
    elif family=='mana_beacon':
        plinth();box((5,3,5),(11,5,11),'core');posts(3,7)
        for x,z in ((1,1),(13,1),(1,13),(13,13)):box((x,5,z),(x+2,9,z+2),'stone')
    elif family=='coal_generator':
        plinth();box((2,3,2),(13,11,14));box((10,11,10),(14,16,14),'stone');box((9,14,9),(15,16,15),'metal')
        box((4,5,1),(10,9,2),'core');box((2,11,3),(12,13,10),'metal')
    elif family=='solar_collector':
        box((3,0,3),(13,2,13),'stone');box((6,2,6),(10,8,10),'metal')
        box((0,8,1),(16,9,15),rotation={'origin':[8,8,8],'axis':'x','angle':22.5})
        box((0,7,1),(16,8,15),'metal',{'origin':[8,8,8],'axis':'x','angle':22.5})
    elif family=='turbine_generator':
        plinth();box((3,3,7),(13,13,14));box((6,5,1),(10,10,12),'metal')
        box((1,6,2),(15,9,4),'stone');box((6,2,2),(9,15,4),'stone');box((6,6,1),(10,10,2),'core')
    elif family=='wind_generator':
        box((3,0,4),(13,2,12),'stone');box((7,2,8),(9,14,10),'metal')
        box((1,10,6),(15,12,8),'stone',{'origin':[8,11,7],'axis':'z','angle':45})
        box((7,4,6),(9,18,8),'stone',{'origin':[8,11,7],'axis':'z','angle':45})
        box((6,9,5),(10,13,9),'core')
    elif family=='thermal_generator':
        plinth();box((2,3,3),(7,12,13));box((9,3,3),(14,16,13));box((2,10,3),(7,12,13),'core')
        box((9,14,3),(14,16,13),'metal');box((6,5,6),(10,8,10),'metal')
    elif family=='nuclear_reactor':
        plinth();box((3,3,3),(13,11,13));posts(3,13);box((2,11,2),(14,13,14),'metal')
        box((5,13,5),(11,16,11),'core',{'origin':[8,14,8],'axis':'y','angle':45})
    elif family=='rtg_generator':
        box((2,0,3),(14,2,13),'metal');box((5,2,5),(11,13,11),'core')
        for y in range(3,14,2):box((1,y,2),(15,y+1,14),'stone')
        box((5,13,5),(11,15,11),'metal')
    elif family=='coolant_mixer':
        plinth()
        for x in [2,10]:
            box((x,4,4),(x+4,13,12),'core');box((x-1,3,3),(x+5,5,13),'metal');box((x-1,13,3),(x+5,15,13),'metal')
        box((6,7,7),(10,10,9),'stone')
    elif family=='transfer_crystal':
        plinth();box((2,3,6),(4,12,10),'metal');box((12,3,6),(14,12,10),'metal');box((3,11,6),(13,13,10),'stone')
    elif family=='harvest_crystal':
        box((2,0,2),(14,2,14),'stone');box((4,2,4),(12,3,12),'metal')
        for x,z in [(1,5),(12,5),(6,12)]:box((x,2,z),(x+3,6,z+3),'core')
    if family not in ('mana_forge','solar_collector'):
        for i in range(tier):box((3+i*2,1,0),(4+i*2,2,1),'core')
    return {'ambientocclusion':True,'textures':textures,'elements':elements,
            'display':{'gui':{'rotation':[28,225,0],'scale':[.8,.8,.8]},
                       'ground':{'translation':[0,3,0],'scale':[.3,.3,.3]},
                       'fixed':{'scale':[.65,.65,.65]},
                       'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
                       'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}}


def obj_model(name, crystal_texture, tier=1):
    return {'loader': 'neoforge:obj', 'model': f'{NS}:models/industry/{name}.obj',
            'automatic_culling': False, 'shade_quads': True, 'flip_v': False,
            'ambientocclusion': False,
            'textures': {'particle': crystal_texture, 'crystal': crystal_texture,
                         'base': f'{NS}:block/machine_side_{tier}'}, 'display': DISPLAY}


def generate():
    meshes = {}
    gem = mesh(); crystal(gem); meshes['energy_crystal'] = gem; export_obj('energy_crystal', gem)
    for tier in range(1,11):
        write(ASSETS/f'models/item/energy_crystal_{tier}.json',
              obj_model('energy_crystal', f'{NS}:block/energy_crystal_facets_{tier}'))
    # Static item meshes include the plinth; placed blocks retain the existing animated BER.
    for family in ('transfer_crystal','harvest_crystal','mana_beacon'):
        write(ASSETS/f'models/item/{family}_icon.json',
              {'parent':'minecraft:item/generated','textures':{'layer0':f'{NS}:block/{family}'}})
        for tier in range(1,6 if family=='mana_beacon' else 4):
            name=f'{family}_t{tier}'
            data=mesh();cube(data,(.125,0,.125),(.875,.125,.875))
            cube(data,(.25,.125,.25),(.75,.22,.75))
            crystal(data,(.5,.57,.5),.17,.65)
            if family=='transfer_crystal':
                cube(data,(.12,.22,.37),(.23,.7,.63));cube(data,(.77,.22,.37),(.88,.7,.63))
                cube(data,(.18,.7,.37),(.82,.78,.63))
            if family=='harvest_crystal':
                for x,z in [(.2,.35),(.8,.35),(.5,.82)]:crystal(data,(x,.3,z),.06,.28,6)
            if family=='mana_beacon':
                for x,z in [(.12,.12),(.78,.12),(.12,.78),(.78,.78)]:cube(data,(x,.125,z),(x+.1,.55,z+.1))
            if tier>1 and family!='harvest_crystal':
                crystal(data,(.23,.37,.5),.065,.3,6)
                crystal(data,(.77,.37,.5),.065,.3,6)
            meshes[name]=data;export_obj(name,data)
            write(ASSETS/f'models/item/{name}.json',obj_model(name,f'{NS}:block/energy_crystal_facets_{tier*2}',tier))
            # The functional billboard uses FIXED. Keep its symbol separate from held 3D geometry.
            write(ASSETS/f'items/{name}.json',{'model':{'type':'minecraft:select',
                'property':'minecraft:display_context','cases':[{'when':'fixed','model':{
                    'type':'minecraft:model','model':f'{NS}:item/{family}_icon'}}],
                'fallback':{'type':'minecraft:model','model':f'{NS}:item/{name}'}}})
    for family in FAMILIES:
        for tier in range(1,6):
            for suffix in ('','_linked','_active'):
                path=ASSETS/f'models/block/{family}_{tier}{suffix}.json'
                write(path,device_model(family,tier,suffix))
    SOURCE.mkdir(parents=True,exist_ok=True)
    write(SOURCE/'meshes.json',meshes)
    write(ASSETS/'models/block/equipment_fabricator.json',device_model('equipment_fabricator',1))
    print('Refreshed 240 device variants, equipment fabricator, 10 crystal items and 11 crystal plinth items.')


if __name__=='__main__':
    generate()

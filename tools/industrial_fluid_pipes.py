"""Production models, recipes and catalog entries for the shared-buffer fluid pipes.

Geometry and locked pixel bases: Create Team / Creators of Create, pinned in
industrial_fluid_pipes_create.json. Editable texture source: draw_industrial_fluid_pipes.lua / .aseprite.
Run this source alone to preserve unrelated industrial assets. --preview renders its actual JSON
and PNG assets offline into temp/fluid-pipes; this is an art export, not a game/test runner.
"""
from pathlib import Path
import json
import math
import argparse
import copy
from functools import lru_cache

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
ASSETS = ROOT / 'src/main/resources/assets' / NS
DATA = ROOT / 'src/main/resources/data' / NS
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
VECTORS = ((0,-1,0),(0,1,0),(0,0,-1),(0,0,1),(-1,0,0),(1,0,0))
NAMES = (('铜制液体管道', 'Copper Fluid Pipe'), ('钒钢液体管道', 'Vanadium Steel Fluid Pipe'), ('铌钛合金液体管道', 'Niobium Titanium Fluid Pipe'))
TIERS = (1, 2, 4)


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def register_catalog(items, content):
    for grade, (titles, tier) in enumerate(zip(NAMES, TIERS), 1):
        key = f'fluid_pipe_t{grade}'
        items[f'{NS}:{key}'] = tier
        content[key] = dict(kind='block', tier=tier, title=titles[0])


def transform(p, direction):
    x,y,z = (v-8 for v in p)
    return {'north': (x,y,z), 'south': (-x,y,-z), 'west': (z,y,-x),
            'east': (-z,y,x), 'up': (x,-z,y), 'down': (x,z,-y)}[direction]


def box(lo, hi, texture, direction='north', only=None):
    points = [transform([x,y,z], direction) for x in (lo[0],hi[0]) for y in (lo[1],hi[1]) for z in (lo[2],hi[2])]
    return {'from': [min(p[i] for p in points)+8 for i in range(3)],
            'to': [max(p[i] for p in points)+8 for i in range(3)],
            'faces': {side: {'texture': '#'+texture, **({'uv':[0,0,16,16]} if texture in ('glass','port') else {})} for side in (only or SIDES)}}


@lru_cache
def create_source():
    return json.loads((ROOT/'tools/industrial_fluid_pipes_create.json').read_text(encoding='utf-8'))


def source_model(key):
    source=create_source()['models'][key]
    parent=source.get('parent','')
    result=source_model(parent.split('fluid_pipe/')[1]+'.json') if 'fluid_pipe/' in parent else {}
    result=copy.deepcopy(result);result.update(copy.deepcopy(source))
    if 'textures' in source:result['textures']={**(result.get('textures') or {}),**source['textures']}
    return result


def create_piece(key,grade):
    raw=source_model(key);elements=copy.deepcopy(raw['elements']);textures={}
    for element in elements:
        element.pop('rotation',None)  # Upstream pieces only contain zero-angle element rotations.
        for face in element['faces'].values():
            source=raw['textures'][face['texture'][1:]].split('/')[-1]
            if source=='glass_fluid_pipe':
                tile=f'glass_t{grade}'
            else:
                uv=face['uv'];xs=[uv[0]*2,uv[2]*2];ys=[uv[1]*2,uv[3]*2]
                col=min(1,int(min(xs)//16));row=min(1,int(min(ys)//16))
                if max(xs)>col*16+16 or max(ys)>row*16+16:raise ValueError('Create UV crosses a 16px tile')
                tile=f'connected_{col+row*2}_t{grade}' if source=='pipes_connected' else f'{ {(0,0):"metal",(1,1):"rim",(0,1):"junction"}[(col,row)]}_t{grade}'
                face['uv']=[uv[0]*2-col*16,uv[1]*2-row*16,uv[2]*2-col*16,uv[3]*2-row*16]
            textures[tile]=f'{NS}:block/fluid_pipe_{tile}';face['texture']='#'+tile
    return elements,textures


def cased(mask):
    return any(sum(bool(mask&(1<<i)) for i in range(6) if i//2!=axis)>2 for axis in range(3))


def model(grade, mask):
    elements=[];textures={}
    def add(key):
        pieces,maps=create_piece(key,grade);elements.extend(pieces);textures.update(maps)
    if mask in (3,12,48):
        # Keep Create's actual window geometry/UVs. Blockstate rotation supplies the three axes.
        add('window.json');add('rim/up.json');add('rim/down.json')
    else:
        if mask==0 or cased(mask):add('casing.json')
        else:
            matched=set()
            for part in create_source()['core_selectors']:
                if all(bool(mask&(1<<SIDES.index(side)))==(value=='true') for side,value in part['when'].items()):
                    key=part['apply']['model'].split('fluid_pipe/')[1]+'.json';add(key);matched.add(key[-6])
            for axis in ('x','y','z'):
                if axis not in matched:
                    # Create fills a lone connection's opposite end automatically. Our saved links
                    # may have just one end, so use its plain copper panel instead of the unused
                    # debug/palette UV region in Create's default core.
                    element=copy.deepcopy(source_model('core_'+axis+'.json')['elements'][0]);tile=f'junction_t{grade}'
                    textures[tile]=f'{NS}:block/fluid_pipe_{tile}'
                    for face in element['faces'].values():face.update(texture='#'+tile,uv=[2,6,12,16]);face.pop('rotation',None)
                    elements.append(element)
        for i,side in enumerate(SIDES):
            if mask&(1<<i):add('connection/'+side+'.json');add('rim/'+side+'.json')
    textures['particle']=f'{NS}:block/fluid_pipe_metal_t{grade}'
    return dict(credit='Create Team / Creators of Create; 16px atlas split and grade recolors by this project',ambientocclusion=True,render_type='minecraft:cutout',textures=textures,elements=elements,
                display={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
                         'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]}})


def marker_model(mode):
    # Same pixel coordinates and UV orientation as FluidPipeRenderer.marker.
    elements=[box([3,13.02,0],[13,13.03,2],'port',only=['up']),
              box([3,2.97,0],[13,2.98,2],'port',only=['down']),
              box([2.97,3,0],[2.98,13,2],'port',only=['west']),
              box([13.02,3,0],[13.03,13,2],'port',only=['east'])]
    return dict(render_type='minecraft:cutout',textures={'port':f'{NS}:block/fluid_pipe_{mode}'},elements=elements)


def generate():
    from industrial_technology import shaped
    shaped('fluid_pipe_t1',['CCC','G G','CCC'],{'C':'minecraft:copper_ingot','G':'minecraft:glass'},count=8)
    for grade, block_tier in ((2,2),(3,4)):
        shaped(f'fluid_pipe_t{grade}',['PPP','PTP','PPP'],{'P':f'fluid_pipe_t{grade-1}','T':f'tech_block_t{block_tier}'},count=8,guarded=True)
    for grade in range(1,4):
        variants={}
        for mask in range(64):
            key=f'fluid_pipe_t{grade}/{mask:02d}'
            write(ASSETS/f'models/block/{key}.json',model(grade,mask))
            variant={'model':f'{NS}:block/{key}'}
            if mask==12:variant['x']=90
            if mask==48:variant.update(x=90,y=90)
            variants[','.join(f'{side}={str(bool(mask&(1<<i))).lower()}' for i,side in enumerate(SIDES))]=variant
        key=f'fluid_pipe_t{grade}'
        write(ASSETS/f'blockstates/{key}.json',{'variants':variants})
        item_elements,item_textures=create_piece('item.json',grade);item_textures['particle']=f'{NS}:block/fluid_pipe_metal_t{grade}'
        write(ASSETS/f'models/item/{key}.json',dict(parent='minecraft:block/block',credit='Create Team / Creators of Create',render_type='minecraft:cutout',textures=item_textures,elements=item_elements,display=source_model('item.json')['display']))
        write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':f'{NS}:item/{key}'}})
        write(DATA/f'loot_table/blocks/{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':f'{NS}:{key}'}]}]})
    for mode in ('input','extract'):
        write(ASSETS/f'models/block/fluid_pipe_port_{mode}.json',marker_model(mode))
    for index,locale in enumerate(('zh_cn','en_us')):
        path=ASSETS/f'lang/{locale}.json';labels=json.loads(path.read_text(encoding='utf-8'))
        for grade,titles in enumerate(NAMES,1):labels[f'block.{NS}.fluid_pipe_t{grade}']=titles[index]
        write(path,labels)
    path=ASSETS/'industry/technology.json';catalog=json.loads(path.read_text(encoding='utf-8'));register_catalog(catalog['items'],catalog['content']);write(path,catalog)
    path=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json';tag=json.loads(path.read_text(encoding='utf-8'))
    for grade in range(1,4):
        key=f'{NS}:fluid_pipe_t{grade}'
        if key not in tag['values']:tag['values'].append(key)
    write(path,tag)


def render_all():
    """Texture-faithful two-view contact sheets. Uses no game window or synthetic input."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    target=ROOT/'temp/fluid-pipes';target.mkdir(parents=True,exist_ok=True)
    font_path='C:/Windows/Fonts/msyh.ttc'
    def font(size):return ImageFont.truetype(font_path,size)
    def load_model(grade,mask):
        asset=json.loads((ASSETS/f'models/block/fluid_pipe_t{grade}/{mask:02d}.json').read_text(encoding='utf-8'))
        angle_x=-math.pi/2 if mask in (12,48) else 0;angle_y=-math.pi/2 if mask==48 else 0
        cx,sx,cy,sy=math.cos(angle_x),math.sin(angle_x),math.cos(angle_y),math.sin(angle_y)
        asset['_rotation']=np.array([[cy,0,sy],[0,1,0],[-sy,0,cy]])@np.array([[1,0,0],[0,cx,-sx],[0,sx,cx]])
        return asset
    textures={}
    for path in (ASSETS/'textures/block').glob('fluid_pipe_*.png'):
        textures[f'{NS}:block/{path.stem}']=np.asarray(Image.open(path).convert('RGBA'))
    def render(asset,size=200,reverse=False):
        resolution=size*2
        camera_world=np.array([-1.,-.85,-1.25] if reverse else [1.,.85,1.25]);camera_world/=np.linalg.norm(camera_world)
        right=np.cross(np.array([0.,1.,0.]),camera_world);right/=np.linalg.norm(right);up=np.cross(camera_world,right)
        rotation=asset.get('_rotation',np.eye(3));camera=camera_world@rotation
        yy,xx=np.mgrid[0:resolution,0:resolution]
        span=23.;u=(xx-resolution/2)/resolution*span;v=(resolution/2-yy)/resolution*span
        origin=8+(u[...,None]*right+v[...,None]*up+camera_world*40)@rotation;ray=-camera
        depth=np.full((resolution,resolution),np.inf);pixels=np.zeros((resolution,resolution,4),dtype=np.uint8)
        for element in asset['elements']:
            low=np.array(element['from'],dtype=float);high=np.array(element['to'],dtype=float)
            bounds_low=np.minimum(low,high);bounds_high=np.maximum(low,high)
            for side,face in element['faces'].items():
                axis=1 if side in ('up','down') else 0 if side in ('east','west') else 2
                sign=1 if side in ('up','east','south') else -1
                if sign*camera[axis]<=0:continue
                plane=high[axis] if sign>0 else low[axis]
                a,b=[i for i in range(3) if i!=axis]
                corners=[]
                for av in (bounds_low[a],bounds_high[a]):
                    for bv in (bounds_low[b],bounds_high[b]):
                        p=low.copy();p[axis]=plane;p[a]=av;p[b]=bv;corners.append(p-8)
                corners=np.array(corners)@rotation.T
                px=resolution/2+(corners@right)/span*resolution;py=resolution/2-(corners@up)/span*resolution
                left=max(0,int(np.floor(px.min())));right_edge=min(resolution,int(np.ceil(px.max()))+1)
                top=max(0,int(np.floor(py.min())));bottom=min(resolution,int(np.ceil(py.max()))+1)
                if right_edge<=left or bottom<=top:continue
                area=(slice(top,bottom),slice(left,right_edge));orig=origin[area];zbuffer=depth[area];raster=pixels[area]
                distance=(plane-orig[...,axis])/ray[axis]
                point=orig+distance[...,None]*ray
                visible=(distance<zbuffer)&(point[...,a]>=bounds_low[a]-1e-5)&(point[...,a]<=bounds_high[a]+1e-5)&(point[...,b]>=bounds_low[b]-1e-5)&(point[...,b]<=bounds_high[b]+1e-5)
                if not visible.any():continue
                if side in ('up','down'):tu=point[...,0];tv=point[...,2] if side=='up' else 16-point[...,2]
                elif side in ('north','south'):tu=16-point[...,0] if side=='north' else point[...,0];tv=16-point[...,1]
                else:tu=point[...,2] if side=='west' else 16-point[...,2];tv=16-point[...,1]
                if '_marker_direction' in element:
                    inverse={'north':'north','south':'south','east':'west','west':'east','up':'down','down':'up'}[element['_marker_direction']]
                    centered=point-8;px0,py0,pz0=centered[...,0],centered[...,1],centered[...,2]
                    base={'north':(px0,py0,pz0),'south':(-px0,py0,-pz0),'west':(pz0,py0,-px0),'east':(-pz0,py0,px0),'up':(px0,-pz0,py0),'down':(px0,pz0,-py0)}[inverse]
                    bx,by,bz=(value+8 for value in base);base_face=element['_marker_face']
                    if base_face=='north':tu=(bx-3)/10*16;tv=(by-3)/10*16
                    else:
                        tu=((bx-3) if base_face=='up' else (13-bx) if base_face=='down' else (by-3) if base_face=='west' else (13-by))/10*16
                        tv=bz/2*16
                elif 'uv' in face:
                    if side in ('up','down'):s=(point[...,0]-low[0])/(high[0]-low[0]);t=((point[...,2]-low[2]) if side=='up' else (high[2]-point[...,2]))/(high[2]-low[2])
                    elif side in ('north','south'):s=((high[0]-point[...,0]) if side=='north' else (point[...,0]-low[0]))/(high[0]-low[0]);t=(high[1]-point[...,1])/(high[1]-low[1])
                    else:s=((point[...,2]-low[2]) if side=='west' else (high[2]-point[...,2]))/(high[2]-low[2]);t=(high[1]-point[...,1])/(high[1]-low[1])
                    for _ in range(face.get('rotation',0)//90):s,t=t,1-s
                    uv=face['uv'];tu=uv[0]+(uv[2]-uv[0])*s;tv=uv[1]+(uv[3]-uv[1])*t
                reference=face['texture'][1:];texture=textures[asset['textures'][reference]]
                color=texture[np.clip(tv.astype(int),0,15),np.clip(tu.astype(int),0,15)].copy()
                visible &= color[...,3]>0
                normal=rotation@np.array(VECTORS[SIDES.index(side)]);world_side=SIDES[VECTORS.index(tuple(round(value) for value in normal))]
                shade={'up':1.,'down':.68,'east':.85,'west':.72,'north':.78,'south':.87}[world_side]
                color[...,:3]=(color[...,:3]*shade).astype(np.uint8)
                zbuffer[visible]=distance[visible];raster[visible]=color[visible]
        return Image.fromarray(pixels).resize((size,size),Image.Resampling.LANCZOS)
    def paste(canvas,asset,x,y,size,reverse=False):
        image=render(asset,size,reverse);canvas.paste(image,(x,y),image)
    background=(24,30,38);tile=(34,42,51);muted=(162,179,190);white=(235,239,241)
    titles=['铜制液体管道 · T1','钒钢液体管道 · T2','铌钛合金液体管道 · T4']
    axes='U上  D下  N北  S南  W西  E东'
    ordered=sorted(range(64),key=lambda mask:(mask.bit_count(),mask))
    hero=Image.new('RGB',(1920,590),background);draw=ImageDraw.Draw(hero)
    draw.text((26,15),'按 Create 原管道重做外观',font=font(38),fill=white)
    draw.text((26,67),'贴图16×16，保持原像素结构；模型正常清晰度展示。断开直接收回连接，不显示X或封盖。',font=font(23),fill=muted)
    for col,(mask,label) in enumerate(((12,'玻璃直管'),(36,'90°弯头'),(56,'T形三通'),(0,'断开 · 没有连接臂'))):
        x=col*480;draw.rounded_rectangle((x+7,112,x+473,580),radius=14,fill=tile);draw.text((x+28,130),label,font=font(28),fill=white)
        paste(hero,load_model(1,mask),x+20,155,432)
    hero.save(target/'create_style_overview.png');print(target/'create_style_overview.png',flush=True)
    materials=Image.new('RGB',(1650,585),background);draw=ImageDraw.Draw(materials)
    draw.text((26,15),'三档共用 Create 管道结构，按金属材质换色',font=font(32),fill=white)
    for col in range(3):
        x=col*550;draw.rounded_rectangle((x+7,75,x+543,575),radius=14,fill=tile);draw.text((x+28,95),titles[col],font=font(27),fill=white)
        paste(materials,load_model(col+1,36),x+45,145,448)
    materials.save(target/'create_style_materials.png');print(target/'create_style_materials.png',flush=True)
    port_sheets=[]
    for grade in range(1,4):
        originals=target/'16x16'/f't{grade}';originals.mkdir(parents=True,exist_ok=True)
        canvas=Image.new('RGB',(2560,2090),background);draw=ImageDraw.Draw(canvas)
        draw.text((28,15),titles[grade-1]+' — 六方向全部64种连接',font=font(32),fill=white)
        draw.text((28,61),axes+'     贴图16×16；模型以正常清晰度展示。大图东南上视角，小图西北下视角。',font=font(20),fill=muted)
        draw.text((28,94),'编号是连接掩码；大图东南上视角，小图西北下视角。直管玻璃，弯头及分支金属；展示空管外观。',font=font(20),fill=muted)
        for cell,mask in enumerate(ordered):
            x=(cell%8)*320;y=145+(cell//8)*240
            draw.rounded_rectangle((x+5,y+3,x+315,y+235),radius=12,fill=tile)
            draw.text((x+15,y+9),f'#{mask:02d}  ·  {mask.bit_count()}向连接',font=font(18),fill=white)
            asset=load_model(grade,mask)
            paste(canvas,asset,x+8,y+30,192);paste(canvas,asset,x+213,y+78,96,True)
            render(asset,16).save(originals/f'{mask:02d}_front.png');render(asset,16,True).save(originals/f'{mask:02d}_back.png')
            label=' '.join(('D','U','N','S','W','E')[i] for i in range(6) if mask&(1<<i)) or '无连接'
            draw.text((x+15,y+207),label,font=font(18),fill=muted)
        path=target/f'connections_t{grade}.png';canvas.save(path);print(path,flush=True)
        ports=Image.new('RGB',(1440,880),background);draw=ImageDraw.Draw(ports)
        draw.text((22,12),titles[grade-1]+' — 机器端口六方向 × 三态',font=font(28),fill=white)
        draw.text((22,52),'贴图16×16，模型正常清晰度。蓝：送入；橙：抽取；断开收回连接臂，没有X或封盖。每格有正反视角。',font=font(17),fill=muted)
        for row,(mode,label) in enumerate((('input','送入机器'),('extract','从机器抽取'),('closed','断开'))):
            for col,side in enumerate(SIDES):
                x=col*240;y=90+row*260
                draw.rounded_rectangle((x+4,y+3,x+236,y+255),radius=12,fill=tile)
                draw.text((x+12,y+10),f'{("D下","U上","N北","S南","W西","E东")[col]} · {label}',font=font(17),fill=white)
                mask=0 if mode=='closed' else 1<<col
                asset=load_model(grade,mask)
                marker={'textures':{},'elements':[]} if mode=='closed' else json.loads((ASSETS/f'models/block/fluid_pipe_port_{mode}.json').read_text(encoding='utf-8'))
                asset['textures'].update(marker['textures'])
                for element in marker['elements']:
                    # Transform each face normal together with the box, preserving the arrow decal.
                    lo,hi=element['from'],element['to'];new=box(lo,hi,'port',side,only=[]);faces={}
                    for face,definition in element['faces'].items():
                        normal=VECTORS[SIDES.index(face)];turned=transform([8+v for v in normal],side)
                        rotated=SIDES[VECTORS.index(tuple(round(v) for v in turned))];faces[rotated]=definition
                    new['faces']=faces;new['_marker_direction']=side;new['_marker_face']=next(iter(element['faces']));asset['elements'].append(new)
                paste(ports,asset,x,y+35,176);paste(ports,asset,x+140,y+130,96,True)
                render(asset,16).save(originals/f'port_{side}_{mode}_front.png');render(asset,16,True).save(originals/f'port_{side}_{mode}_back.png')
        ports.save(target/f'ports_t{grade}.png');port_sheets.append(ports)
    combined=Image.new('RGB',(1440,2640),background)
    for i,sheet in enumerate(port_sheets):combined.paste(sheet,(0,i*880))
    combined.save(target/'ports_all.png');print(target/'ports_all.png',flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--preview',action='store_true');args=parser.parse_args()
    if args.preview:
        import sys
        sys.path.insert(0,str(ROOT/'temp/pipe-python'))
        render_all()
    else:generate()

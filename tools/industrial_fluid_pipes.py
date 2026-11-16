"""Production models, recipes and catalog entries for the shared-buffer fluid pipes.

Glass shells and pixel-matched metal trim are authored here; the original metal pixel bases remain
credited in industrial_fluid_pipes_create.json and locked in the editable Aseprite source.
Run this source alone to preserve unrelated industrial assets. --preview renders its actual JSON
and PNG assets offline into temp/fluid-pipes; this is an art export, not a game/test runner.
"""
from pathlib import Path
import json
import math
import argparse

ROOT = Path(__file__).resolve().parents[1]
NS = 'yumis_dungeon_and_fight'
ASSETS = ROOT / 'src/main/resources/assets' / NS
DATA = ROOT / 'src/main/resources/data' / NS
SIDES = ('down', 'up', 'north', 'south', 'west', 'east')
VECTORS = ((0,-1,0),(0,1,0),(0,0,-1),(0,0,1),(-1,0,0),(1,0,0))
NAMES = (('铜制液体管道', 'Copper Fluid Pipe'), ('钒钢液体管道', 'Vanadium Steel Fluid Pipe'), ('铌钛合金液体管道', 'Niobium Titanium Fluid Pipe'))
TIERS = (1, 2, 4)
GLASS_WALL = .4  # Model pixels; liquid stays another .1px inside in FluidPipeRenderer.
BODY_MIN, BODY_MAX = 2, 14
RIM_MIN, RIM_MAX, PORT_DEPTH = 1, 15, 2


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    if 'elements' in value:
        # Keep generated geometry reviewable without thousands of lines of scalar coordinates.
        header=json.dumps({k:v for k,v in value.items() if k!='elements'},ensure_ascii=False,indent=2)
        elements=',\n'.join('    '+json.dumps(e,separators=(',',':')) for e in value['elements'])
        text=header[:-2]+',\n  "elements": [\n'+elements+'\n  ]\n}'
    else:text=json.dumps(value,ensure_ascii=False,indent=2)
    path.write_text(text+'\n', encoding='utf-8')


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
            'faces': {side: {'texture': '#'+texture, **({'uv':[0,0,16,16]} if texture=='port' else {})} for side in (SIDES if only is None else only)}}


def atlas_layout():
    data=json.loads((ROOT/'tools/fluid_pipe_materials_12x14_layout.json').read_text(encoding='utf-8'))
    return {part['id']:part for part in data['parts']}


def region(part,crop=None):
    entry=atlas_layout()[part]
    u,v,U,V=crop or (0,0,entry['width'],entry['height'])
    return [(entry['x']+u)/4,(entry['y']+v)/4,(entry['x']+U)/4,(entry['y']+V)/4]


def face_spec(part,turn=0,crop=None):
    return {'texture':'#atlas','uv':region(part,crop),**({'rotation':turn*90} if turn else {})}


def add_panel(elements,lo,hi,side,face,back=True):
    elements.append({'from':list(lo),'to':list(hi),'faces':{side:face}})
    if not back:return
    axis=1 if side in ('up','down') else 0 if side in ('east','west') else 2
    positive=side in ('up','east','south')
    plane=hi[axis]-GLASS_WALL if positive else lo[axis]+GLASS_WALL
    inner_lo=list(lo);inner_hi=list(hi);inner_lo[axis]=inner_hi[axis]=plane
    inside={**face,'uv':list(face['uv'])}
    # Reverse the face without mirroring its coverage in world space.
    flip_u=(axis!=1)^bool(face.get('rotation',0)//90%2)
    if flip_u:inside['uv'][0],inside['uv'][2]=inside['uv'][2],inside['uv'][0]
    else:inside['uv'][1],inside['uv'][3]=inside['uv'][3],inside['uv'][1]
    elements.append({'from':inner_lo,'to':inner_hi,'faces':{SIDES[SIDES.index(side)^1]:inside}})


FACE_DIRECTIONS={
    'up':('north','east','south','west'),'down':('south','east','north','west'),
    'north':('up','west','down','east'),'south':('up','east','down','west'),
    'west':('up','south','down','north'),'east':('up','north','down','south'),
}


def projected_face(mask,side):
    local=sum(1<<i for i,direction in enumerate(FACE_DIRECTIONS[side]) if mask&(1<<SIDES.index(direction)))
    if not local:return face_spec('casing',crop=(1,1,13,13))
    for canonical,part in ((1,'single'),(5,'straight'),(3,'elbow'),(11,'tee'),(15,'cross')):
        for turn in range(4):
            rotated=((canonical<<turn)|(canonical>>(4-turn)))&15
            if rotated==local:return face_spec(part,turn,(0,2,12,14) if part=='straight' else None)
    raise ValueError('Missing canonical pipe face')


def side_strip(port_axis,side,part='rim_side',crop=None):
    normal_axis=1 if side in ('up','down') else 0 if side in ('east','west') else 2
    horizontal=0 if normal_axis in (1,2) else 2
    return face_spec(part,0 if port_axis==horizontal else 1,crop)


def collar(elements,direction):
    axis=next(i for i,v in enumerate(VECTORS[SIDES.index(direction)]) if v)
    outer=box([RIM_MIN,RIM_MIN,0],[RIM_MAX,RIM_MAX,PORT_DEPTH],'atlas',direction,only=[])
    faces={}
    for side in SIDES:
        normal=1 if side in ('up','down') else 0 if side in ('east','west') else 2
        faces[side]=face_spec('rim') if normal==axis else side_strip(axis,side)
    outer['faces']=faces;elements.append(outer)
    for lo,hi,side in (([3,3,0],[3,13,2],'east'),([13,3,0],[13,13,2],'west'),
                       ([3,3,0],[13,3,2],'up'),([3,13,0],[13,13,2],'down')):
        inner=box(lo,hi,'atlas',direction,only=[])
        normal=VECTORS[SIDES.index(side)]
        turned=transform([8+v for v in normal],direction)
        facing=SIDES[VECTORS.index(tuple(round(v) for v in turned))]
        inner['faces']={facing:side_strip(axis,facing,crop=(0,2,2,12))};elements.append(inner)


def model(grade,mask):
    elements=[]
    if mask==0:
        for side in SIDES:
            add_panel(elements,[BODY_MIN]*3,[BODY_MAX]*3,side,face_spec('casing',crop=(1,1,13,13)))
            add_panel(elements,[BODY_MIN]*3,[BODY_MAX]*3,side,face_spec('glass_reserved'))
    else:
        for index,side in enumerate(SIDES):
            if mask&(1<<index):continue
            add_panel(elements,[BODY_MIN]*3,[BODY_MAX]*3,side,projected_face(mask,side))
            add_panel(elements,[BODY_MIN]*3,[BODY_MAX]*3,side,face_spec('glass_reserved'))
        for index,direction in enumerate(SIDES):
            if not mask&(1<<index):continue
            arm=box([BODY_MIN,BODY_MIN,0],[BODY_MAX,BODY_MAX,BODY_MIN],'atlas',direction,only=[])
            axis=next(i for i,v in enumerate(VECTORS[index]) if v)
            for side in SIDES:
                normal=1 if side in ('up','down') else 0 if side in ('east','west') else 2
                if normal==axis:continue
                # Connector pixels are 12 across the pipe and 2 along the arm.
                horizontal=0 if normal in (1,2) else 2
                turn=1 if axis==horizontal else 0
                add_panel(elements,arm['from'],arm['to'],side,face_spec('connector',turn))
                add_panel(elements,arm['from'],arm['to'],side,face_spec('glass_reserved',turn,crop=(0,0,12,2)))
    return dict(credit='12x14 Aseprite pipe materials; complete user fourth-frame design, canonical faces reused by rotation',
                ambientocclusion=False,render_type='minecraft:cutout',
                textures={'atlas':f'{NS}:block/fluid_pipe_12x14_t{grade}','particle':f'{NS}:block/fluid_pipe_metal_t{grade}'},
                elements=elements,display={'gui':{'rotation':[30,225,0],'translation':[0,0,0],'scale':[.85,.85,.85]},
                                           'ground':{'translation':[0,3,0],'scale':[.4,.4,.4]}})


def marker_model(mode):
    low,high=(BODY_MIN,BODY_MAX) if mode=='input' else (RIM_MIN,RIM_MAX)
    elements=[box([low,high+.02,0],[high,high+.03,PORT_DEPTH],'port',only=['up']),
              box([low,low-.03,0],[high,low-.02,PORT_DEPTH],'port',only=['down']),
              box([low-.03,low,0],[low-.02,high,PORT_DEPTH],'port',only=['west']),
              box([high+.02,low,0],[high+.03,high,PORT_DEPTH],'port',only=['east'])]
    return dict(render_type='minecraft:cutout',textures={'port':f'{NS}:block/fluid_pipe_{mode}'},elements=elements)


def collar_quads():
    """Canonical north collar: native positions/UVs used by the dynamic game renderer."""
    elements=[];collar(elements,'north');quads=[]
    for element in elements:
        x,y,z=element['from'];X,Y,Z=element['to']
        corners={
            'down':((x,y,Z),(x,y,z),(X,y,z),(X,y,Z)),
            'up':((x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)),
            'north':((x,y,z),(x,Y,z),(X,Y,z),(X,y,z)),
            'south':((X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)),
            'west':((x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)),
            'east':((X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z))}
        for side,face in element['faces'].items():
            quad=[]
            for px,py,pz in corners[side]:
                if side in ('up','down'):s=(px-x)/(X-x);t=((pz-z) if side=='up' else (Z-pz))/(Z-z)
                elif side in ('north','south'):s=((X-px) if side=='north' else (px-x))/(X-x);t=(Y-py)/(Y-y)
                else:s=((pz-z) if side=='west' else (Z-pz))/(Z-z);t=(Y-py)/(Y-y)
                for _ in range(face.get('rotation',0)//90):s,t=t,1-s
                u,v,U,V=face['uv']
                quad.extend((px/16,py/16,pz/16,(u+(U-u)*s)/16,(v+(V-v)*t)/16))
            quads.append(quad+list(VECTORS[SIDES.index(side)]))
    return quads


def generate_geometry():
    path=ROOT/'src/main/java/cn/yumis/fight/modules/industry/platform/FluidPipeGeometry.java'
    quads=',\n'.join('        {'+','.join(f'{value:g}f' for value in quad)+'}' for quad in collar_quads())
    path.write_text('package cn.yumis.fight.modules.industry.platform;\n\n'
                    '/** Generated from tools/industrial_fluid_pipes.py; model, picking and visuals share dimensions. */\n'
                    'public final class FluidPipeGeometry {\n'
                    f'    public static final int BODY_MIN={BODY_MIN},BODY_MAX={BODY_MAX},RIM_MIN={RIM_MIN},RIM_MAX={RIM_MAX},PORT_DEPTH={PORT_DEPTH};\n'
                    f'    public static final float WALL={GLASS_WALL}f;\n'
                    '    /** Four vertices (xyzuv), then the face normal; positions and UVs are normalized. */\n'
                    '    public static final float[][] EXTRACT_COLLAR={\n'+quads+'\n    };\n'
                    '    private FluidPipeGeometry(){}\n}\n',encoding='utf-8')


def generate():
    generate_geometry()
    from industrial_technology import shaped
    shaped('fluid_pipe_t1',['CCC','G G','CCC'],{'C':'minecraft:copper_ingot','G':'minecraft:glass'},count=8)
    for grade, block_tier in ((2,2),(3,4)):
        shaped(f'fluid_pipe_t{grade}',['PPP','PTP','PPP'],{'P':f'fluid_pipe_t{grade-1}','T':f'tech_block_t{block_tier}'},count=8,guarded=True)
    for grade in range(1,4):
        variants={}
        for mask in range(64):
            key=f'fluid_pipe_t{grade}/{mask:02d}'
            asset=model(grade,mask) if grade==1 else dict(parent=f'{NS}:block/fluid_pipe_t1/{mask:02d}',textures={'atlas':f'{NS}:block/fluid_pipe_12x14_t{grade}'})
            if grade>1:asset['textures']['particle']=f'{NS}:block/fluid_pipe_metal_t{grade}'
            write(ASSETS/f'models/block/{key}.json',asset)
            variant={'model':f'{NS}:block/{key}'}
            variants[','.join(f'{side}={str(bool(mask&(1<<i))).lower()}' for i,side in enumerate(SIDES))]=variant
        key=f'fluid_pipe_t{grade}'
        write(ASSETS/f'blockstates/{key}.json',{'variants':variants})
        write(ASSETS/f'models/item/{key}.json',dict(parent=f'{NS}:block/{key}/12',display={'fixed':{'rotation':[0,90,0],'scale':[.5,.5,.5]}}))
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


def render_all(overview_only=False):
    """Texture-faithful two-view contact sheets. Uses no game window or synthetic input."""
    import numpy as np
    from PIL import Image, ImageDraw, ImageFont
    target=ROOT/'temp/fluid-pipes';target.mkdir(parents=True,exist_ok=True)
    font_path='C:/Windows/Fonts/msyh.ttc'
    def font(size):return ImageFont.truetype(font_path,size)
    def load_model(grade,mask):
        asset=json.loads((ASSETS/f'models/block/fluid_pipe_t{grade}/{mask:02d}.json').read_text(encoding='utf-8'))
        if 'parent' in asset:
            parent=json.loads((ASSETS/'models'/f'{asset["parent"].split(":")[1]}.json').read_text(encoding='utf-8'))
            asset={**parent,**asset,'textures':{**parent['textures'],**asset['textures']}}
        state=json.loads((ASSETS/f'blockstates/fluid_pipe_t{grade}.json').read_text(encoding='utf-8'))
        key=','.join(f'{side}={str(bool(mask&(1<<i))).lower()}' for i,side in enumerate(SIDES))
        variant=state['variants'][key]
        angle_x=-math.radians(variant.get('x',0));angle_y=-math.radians(variant.get('y',0))
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
                    marker_min=element['_marker_min'];marker_max=element['_marker_max'];marker_width=marker_max-marker_min
                    if base_face=='north':tu=(bx-marker_min)/marker_width*16;tv=(by-marker_min)/marker_width*16
                    else:
                        tu=((bx-marker_min) if base_face=='up' else (marker_max-bx) if base_face=='down' else (by-marker_min) if base_face=='west' else (marker_max-by))/marker_width*16
                        tv=bz/PORT_DEPTH*16
                elif 'uv' in face:
                    if side in ('up','down'):s=(point[...,0]-low[0])/(high[0]-low[0]);t=((point[...,2]-low[2]) if side=='up' else (high[2]-point[...,2]))/(high[2]-low[2])
                    elif side in ('north','south'):s=((high[0]-point[...,0]) if side=='north' else (point[...,0]-low[0]))/(high[0]-low[0]);t=(high[1]-point[...,1])/(high[1]-low[1])
                    else:s=((point[...,2]-low[2]) if side=='west' else (high[2]-point[...,2]))/(high[2]-low[2]);t=(high[1]-point[...,1])/(high[1]-low[1])
                    for _ in range(face.get('rotation',0)//90):s,t=t,1-s
                    uv=face['uv'];tu=uv[0]+(uv[2]-uv[0])*s;tv=uv[1]+(uv[3]-uv[1])*t
                reference=face['texture'][1:];texture=textures[asset['textures'][reference]]
                height,width=texture.shape[:2]
                color=texture[np.clip((tv*height/16).astype(int),0,height-1),np.clip((tu*width/16).astype(int),0,width-1)].copy()
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
    draw.text((26,15),'12×12液体管道 · 用户第4帧材质',font=font(38),fill=white)
    draw.text((26,67),'管身12×12，沿连接方向接满16像素；相连处直接续接，仅从机器抽出时放大接口。',font=font(23),fill=muted)
    for col,(mask,label) in enumerate(((12,'玻璃直管'),(36,'90°弯头'),(56,'T形三通'),(0,'断开 · 没有连接臂'))):
        x=col*480;draw.rounded_rectangle((x+7,112,x+473,580),radius=14,fill=tile);draw.text((x+28,130),label,font=font(28),fill=white)
        paste(hero,load_model(3,mask),x+20,155,432)
    hero.save(target/'glass_overview.png');print(target/'glass_overview.png',flush=True)
    materials=Image.new('RGB',(1650,585),background);draw=ImageDraw.Draw(materials)
    draw.text((26,15),'用户第4帧母稿 · 12×12管身／相连处直接续接',font=font(32),fill=white)
    for col in range(3):
        x=col*550;draw.rounded_rectangle((x+7,75,x+543,575),radius=14,fill=tile);draw.text((x+28,95),titles[col],font=font(27),fill=white)
        paste(materials,load_model(col+1,36),x+45,145,448)
    materials.save(target/'glass_materials.png');print(target/'glass_materials.png',flush=True)
    if overview_only:return
    port_sheets=[]
    for grade in range(1,4):
        canvas=Image.new('RGB',(2560,2090),background);draw=ImageDraw.Draw(canvas)
        draw.text((28,15),titles[grade-1]+' — 六方向全部64种连接',font=font(32),fill=white)
        draw.text((28,61),axes+'     64×64原尺寸图集；直管／转角通过UV旋转复用。大图东南上，小图西北下。',font=font(20),fill=muted)
        draw.text((28,94),'编号是连接掩码；大图东南上视角，小图西北下视角。所有连接共用玻璃管体；展示空管外观。',font=font(20),fill=muted)
        for cell,mask in enumerate(ordered):
            x=(cell%8)*320;y=145+(cell//8)*240
            draw.rounded_rectangle((x+5,y+3,x+315,y+235),radius=12,fill=tile)
            draw.text((x+15,y+9),f'#{mask:02d}  ·  {mask.bit_count()}向连接',font=font(18),fill=white)
            asset=load_model(grade,mask)
            paste(canvas,asset,x+8,y+30,192);paste(canvas,asset,x+213,y+78,96,True)
            label=' '.join(('D','U','N','S','W','E')[i] for i in range(6) if mask&(1<<i)) or '无连接'
            draw.text((x+15,y+207),label,font=font(18),fill=muted)
        path=target/f'connections_t{grade}.png';canvas.save(path);print(path,flush=True)
        ports=Image.new('RGB',(1440,880),background);draw=ImageDraw.Draw(ports)
        draw.text((22,12),titles[grade-1]+' — 机器端口六方向 × 三态',font=font(28),fill=white)
        draw.text((22,52),'蓝：送入机器时平接；橙：从机器抽取时放大一圈；断开收回连接臂。每格有正反视角。',font=font(17),fill=muted)
        for row,(mode,label) in enumerate((('input','送入机器'),('extract','从机器抽取'),('closed','断开'))):
            for col,side in enumerate(SIDES):
                x=col*240;y=90+row*260
                draw.rounded_rectangle((x+4,y+3,x+236,y+255),radius=12,fill=tile)
                draw.text((x+12,y+10),f'{("D下","U上","N北","S南","W西","E东")[col]} · {label}',font=font(17),fill=white)
                mask=0 if mode=='closed' else 1<<col
                asset=load_model(grade,mask)
                if mode=='extract':collar(asset['elements'],side)
                marker={'textures':{},'elements':[]} if mode=='closed' else json.loads((ASSETS/f'models/block/fluid_pipe_port_{mode}.json').read_text(encoding='utf-8'))
                asset['textures'].update(marker['textures'])
                for element in marker['elements']:
                    # Transform each face normal together with the box, preserving the arrow decal.
                    lo,hi=element['from'],element['to'];new=box(lo,hi,'port',side,only=[]);faces={}
                    for face,definition in element['faces'].items():
                        normal=VECTORS[SIDES.index(face)];turned=transform([8+v for v in normal],side)
                        rotated=SIDES[VECTORS.index(tuple(round(v) for v in turned))];faces[rotated]=definition
                    new['faces']=faces;new['_marker_direction']=side;new['_marker_face']=next(iter(element['faces']))
                    new['_marker_min']=BODY_MIN if mode=='input' else RIM_MIN;new['_marker_max']=BODY_MAX if mode=='input' else RIM_MAX
                    asset['elements'].append(new)
                paste(ports,asset,x,y+35,176);paste(ports,asset,x+140,y+130,96,True)
        ports.save(target/f'ports_t{grade}.png');port_sheets.append(ports)
    combined=Image.new('RGB',(1440,2640),background)
    for i,sheet in enumerate(port_sheets):combined.paste(sheet,(0,i*880))
    combined.save(target/'ports_all.png');print(target/'ports_all.png',flush=True)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--preview',action='store_true');parser.add_argument('--overview-only',action='store_true');args=parser.parse_args()
    if args.preview:
        import sys
        sys.path.insert(0,str(ROOT/'temp/pipe-python'))
        render_all(args.overview_only)
    else:generate()

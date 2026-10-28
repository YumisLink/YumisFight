"""Build the approved nine weapon models with Blender's Python runtime.

Run with Blender --background --factory-startup --python tools/build_weapon_models.py.
The editable library and original artwork previews stay in art/weapon-models;
only the OBJ/MTL/atlas and item model definitions go into game resources.
This is an art production tool, not a game acceptance test.
"""
from pathlib import Path
import bpy
import bmesh
import html
import json
import math
import sys
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / 'art/weapon-models'
ASSETS = ROOT / 'src/main/resources/assets/yumis_dungeon_and_fight'
NS = 'yumis_dungeon_and_fight'
for directory in [ART, ART/'exports', ART/'textures', ART/'renders', ASSETS/'models/weapons', ASSETS/'textures/item/weapon3d']:
    directory.mkdir(parents=True, exist_ok=True)

# The atlas is authored here; no downloaded DNF pixels are used as model textures.
PALETTE = [
 ('Obsidian','#17202b',.38,0), ('Steel','#4d667b',.42,0), ('Silver','#b5cbd6',.3,0), ('Edge','#ecf5f6',.22,0),
 ('Wine leather','#40232a',.7,0), ('Red lacquer','#ac2924',.42,0), ('Ember','#e56022',.32,.15), ('Sun edge','#ffca67',.25,.25),
 ('Old gold','#8e592a',.43,0), ('Gold','#d6a04e',.3,0), ('Gold edge','#f9d991',.25,0), ('Blue steel','#263b65',.4,0),
 ('Azure','#247ac6',.32,.15), ('Ice','#57d8f3',.25,.35), ('Light core','#ccf8ff',.23,.65), ('Sapphire','#3467d7',.22,.15),
 ('Violet','#6638a5',.4,0), ('Amethyst','#b77ce9',.27,.15), ('Purple edge','#e6beff',.22,.2), ('Deep purple','#30243e',.65,0),
 ('Wood','#5e3425',.75,0), ('Wood light','#9e6538',.65,0), ('Jade','#398a78',.4,0), ('Ivory','#eddeb2',.45,0),
 ('Crimson','#c53b37',.45,0), ('Dark red','#662530',.5,0), ('Warm face','#f6b96a',.48,0), ('Eye','#151423',.5,0),
 ('Copper','#ae7148',.45,0), ('Mint','#94dfd1',.35,.1), ('White','#f4efdf',.55,0), ('Stage','#182330',.8,0),
]
# Keep the opaque atlas separate so metal and the other eight weapons do not
# enter the game's translucent render pass. Only the blue energy swatch fades.
ENERGY_ALPHA={13:.38}
def rgb(code): return tuple(int(code[i:i+2],16)/255 for i in (1,3,5))
def linear(x):return x/12.92 if x<=.04045 else ((x+.055)/1.055)**2.4
def enum_set(owner, key, preferred):
    values=[i.identifier for i in owner.bl_rna.properties[key].enum_items]
    if preferred not in values:raise RuntimeError(f'{key}: {preferred} not available: {values}')
    setattr(owner,key,preferred)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
for material in list(bpy.data.materials):
    if material.users==0:bpy.data.materials.remove(material)
scene=bpy.context.scene
enum_set(scene.render,'engine','BLENDER_EEVEE')
scene.render.resolution_percentage=100
enum_set(scene.render.image_settings,'file_format','PNG')
enum_set(scene.render.image_settings,'color_mode','RGBA')
scene.render.image_settings.color_depth='8'
scene.world.color=(.15,.15,.15)
scene.world.use_nodes=True
bg=next(n for n in scene.world.node_tree.nodes if n.type=='BACKGROUND')
bg.inputs['Color'].default_value=(.07,.09,.13,1)
bg.inputs['Strength'].default_value=.42
scene.render.film_transparent=False
scene.render.image_settings.compression=30

atlas=bpy.data.images.new('Weapon_Palette_Authored',width=128,height=64,alpha=True)
pixels=[]
for y in range(64):
    for x in range(128):
        idx=(y//16)*8+x//16
        base=rgb(PALETTE[idx][1])
        grain=1+(((x*17+y*31)%13)-6)*.003+(y%16-7.5)*.002
        pixels.extend([min(1,max(0,c*grain))for c in base]+[1])
atlas.pixels=pixels
atlas.filepath_raw=str(ART/'textures/weapon_atlas.png')
atlas.file_format='PNG';atlas.save()
(ASSETS/'textures/item/weapon3d/weapon_atlas.png').write_bytes((ART/'textures/weapon_atlas.png').read_bytes())
energy_atlas=bpy.data.images.new('Weapon_Energy_Authored',width=128,height=64,alpha=True)
energy_pixels=pixels.copy()
for y in range(64):
    for x in range(128):
        energy_pixels[(y*128+x)*4+3]=ENERGY_ALPHA.get((y//16)*8+x//16,1)
energy_atlas.pixels=energy_pixels
energy_atlas.filepath_raw=str(ART/'textures/weapon_energy.png')
energy_atlas.file_format='PNG';energy_atlas.save()
(ASSETS/'textures/item/weapon3d/weapon_energy.png').write_bytes((ART/'textures/weapon_energy.png').read_bytes())
materials=[]
for i,(name,color,rough,emission)in enumerate(PALETTE):
    m=bpy.data.materials.new(name);m.use_nodes=True
    bsdf=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=energy_atlas if i in ENERGY_ALPHA else atlas;tex.interpolation='Closest'
    m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    bsdf.inputs['Roughness'].default_value=rough
    bsdf.inputs['Metallic'].default_value=0 if i in [4,19,20,21,23,26,27,30,31]else .58
    if i in ENERGY_ALPHA:
        m.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
        bsdf.inputs['Metallic'].default_value=0
        enum_set(m,'surface_render_method','DITHERED')
    if emission:
        m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Emission Color'])
        bsdf.inputs['Emission Strength'].default_value=emission
    m.diffuse_color=(*rgb(color),ENERGY_ALPHA.get(i,1))
    materials.append(m)

SPECS=[
 ('common_katana','流光星陨刀','RYUUSEI','COMMON / KATANA','katana_40_base','katana',False),
 ('common_lightsaber','泰拉石光剑','TERRA LIGHT','COMMON / LIGHTSABER','lightsaber_30_base','lightsaber',False),
 ('common_greatsword','天选之剑－格朗','GRAM','COMMON / GREATSWORD','greatsword_40_base','greatsword',False),
 ('common_shortsword','卢克西的紫炎波刃剑','ROXY FLAME','COMMON / SHORTSWORD','shortsword_10_base','shortsword',False),
 ('common_blunt','雷神之怒','THUNDER FURY','COMMON / BLUNT','blunt_40_base','blunt',False),
 ('skyspine','天脊乾坤剑','SKYSPINE','EPIC / CLASSIC 50','skyspine','lightsaber',False),
 ('bylle','别云剑-无用','BYLLE','EPIC / GREATSWORD','bylle','greatsword',False),
 ('willy_staff','威利的戒严法杖','WILLY','MYTHIC / STAFF','willy_staff','staff',False),
 ('niwu','尼巫的职责','NIWU','MYTHIC / APPROXIMATE','niwu','club',True),
]
refs=json.loads((ROOT/'art/weapon-references/index.json').read_text(encoding='utf-8'))
ref_by_id={r['appearance_id']:r for r in refs['weapons']}
# The primary reference archive also contains the later crafted version. This
# model deliberately uses the orange classic level-50 weapon in the right panel.
REF_OVERRIDES={'skyspine':'lightsaber/skyspine/equipped_reference-36f4d20ea3.jpg'}
MODEL_NOTES={
    'common_lightsaber':'蓝色能量外层采用38%不透明度，亮色内芯、金属护手和剑柄保持不透明。',
    'skyspine':'采用旧50级橙金色版本；参考合图右栏，不采用90级制作版。',
    'willy_staff':'依据图标及小幅手持图制作低模；法杖头部的侧面、背面和表情细节为简化补全。',
    'niwu':'未找到可靠同名实物图；采用万波息笛轮廓作近似参考，竹节、孔位与装饰为本项目低模设计。',
}
groups={};roots={};current=None;part_number=0
def mesh(name,verts,faces,color=1,face_colors=None):
    global part_number
    part_number+=1
    d=bpy.data.meshes.new(name);d.from_pydata(verts,[],faces);d.update()
    bm=bmesh.new();bm.from_mesh(d);bmesh.ops.recalc_face_normals(bm,faces=list(bm.faces));bm.to_mesh(d);bm.free()
    o=bpy.data.objects.new(f'{part_number:04d}_{name}',d);current.objects.link(o)
    o.parent=roots[current.name]
    for m in materials:d.materials.append(m)
    uv=d.uv_layers.new(name='PaletteUV')
    for p in d.polygons:
        c=face_colors[p.index]if face_colors and p.index<len(face_colors)else color
        p.material_index=c
        u=(c%8+.5)/8;v=(c//8+.5)/4
        for k,li in enumerate(p.loop_indices):
            # Keep every face inside its swatch; preserve a subtle authored grain.
            a=2*math.pi*k/max(3,len(p.loop_indices))
            uv.data[li].uv=(u+math.cos(a)*.036,v+math.sin(a)*.072)
        p.use_smooth=False
    return o

def prism(name,profile,depth,color,y=0,edge=None):
    n=len(profile);vs=[(x,y-depth/2,z)for x,z in profile]+[(x,y+depth/2,z)for x,z in profile]
    fs=[tuple(range(n-1,-1,-1)),tuple(range(n,2*n))]
    fs += [(i,(i+1)%n,(i+1)%n+n,i+n)for i in range(n)]
    return mesh(name,vs,fs,color,[color,color]+[edge if edge is not None else color]*n)

def tube(name,points,radius,color,sides=8,other=None):
    ps=[Vector(p)for p in points];vs=[]
    for j,p in enumerate(ps):
        t=(ps[min(j+1,len(ps)-1)]-ps[max(0,j-1)]).normalized()
        b=t.cross(Vector((0,1,0)))
        if b.length<.01:b=t.cross(Vector((1,0,0)))
        b.normalize();c=t.cross(b).normalized()
        r=radius[j]if isinstance(radius,list)else radius
        for k in range(sides):
            a=2*math.pi*k/sides;v=p+r*(b*math.cos(a)+c*math.sin(a));vs.append(tuple(v))
    fs=[tuple(range(sides-1,-1,-1))]
    for j in range(len(ps)-1):
        fs += [(j*sides+k,j*sides+(k+1)%sides,(j+1)*sides+(k+1)%sides,(j+1)*sides+k)for k in range(sides)]
    fs.append(tuple(range((len(ps)-1)*sides,len(ps)*sides)))
    cs=[color]*len(fs)
    if other is not None:
        cs=[other if j%5==0 else color for j in range(len(fs))]
    return mesh(name,vs,fs,color,cs)

def ring(name,z,radius,width,color,x=0,y=0):
    return tube(name,[(x,y,z-width/2),(x,y,z+width/2)],radius,color,12,other=10 if color==9 else None)

def gem(name,xyz,size,color):
    x,y,z=xyz;sx,sy,sz=size
    vs=[(x,y-sy,z),(x-sx,y,z),(x,y,z+sz),(x+sx,y,z),(x,y,z-sz),(x,y+sy,z)]
    fs=[(0,1,2),(0,2,3),(0,3,4),(0,4,1),(5,2,1),(5,3,2),(5,4,3),(5,1,4)]
    return mesh(name,vs,fs,color,[color,color,min(color+1,31),color,color,color,color,color])

def grip(bottom=-.28,top=.14,color=0,wrap_color=4,r=.045,turns=7):
    tube('Grip',[(0,0,bottom),(0,0,top)],r,color,10)
    points=[]
    for j in range(turns*12+1):
        a=j/12*2*math.pi;z=bottom+(top-bottom)*j/(turns*12)
        points.append((math.cos(a)*r*1.02,math.sin(a)*r*1.02,z))
    tube('Woven grip',points,.007,wrap_color,4)
    ring('Grip collar',bottom,r*1.25,.035,9)
    ring('Grip collar',top,r*1.23,.035,9)
    gem('Pommel',(0,0,bottom-.05),(r*1.25,r*1.1,.065),8)

def blade(name,sections,body=1,edge=3):
    # Each section: height, horizontal centre, total width, half-thickness.
    vs=[]
    for z,x,w,t in sections:
        vs.extend([(x-w/2,0,z),(x-w*.34,-t*.65,z),(x,-t,z),(x+w*.34,-t*.65,z),(x+w/2,0,z),(x+w*.34,t*.65,z),(x,t,z),(x-w*.34,t*.65,z)])
    fs=[tuple(range(7,-1,-1))];cs=[body]
    for j in range(len(sections)-1):
        for k in range(8):
            fs.append((j*8+k,j*8+(k+1)%8,(j+1)*8+(k+1)%8,(j+1)*8+k))
            cs.append(edge if k in [0,3,4,7]else body)
    fs.append(tuple(range((len(sections)-1)*8,len(sections)*8)));cs.append(edge)
    return mesh(name,vs,fs,body,cs)

def rune(name,x,z,s,color=9,y=-.06):
    prism(name,[(x,z+s),(x+s*.5,z),(x,z-s),(x-s*.5,z)],.007,color,y)

def katana():
    grip(-.30,.15,0,25,.040,8)
    ring('Tsuba',.18,.125,.035,8)
    for i in range(4):
        a=i*math.pi/2;gem('Tsuba rivet',(.08*math.cos(a),.08*math.sin(a),.21),(.018,.018,.014),10)
    ring('Habaki',.245,.052,.08,9)
    blade('Curved ember blade',[(.28,0,.105,.020),(.55,.008,.105,.021),(.85,.025,.099,.020),(1.15,.055,.091,.018),(1.43,.092,.078,.016),(1.64,.14,.059,.013),(1.77,.17,.002,.001)],5,7)
    tube('Back spine',[(.04,0,.32),(.065,0,.85),(.10,0,1.4),(.16,0,1.65)],.007,6,4)
    gem('Pommel ruby',(0,-.05,-.35),(.025,.014,.037),24)

def light_blade(fire=False):
    grip(-.25,.12,11 if not fire else 25,0,.048,6)
    energy=6 if fire else 13;core=7 if fire else 14
    metal=9 if fire else 2
    tube('Hilt',[(0,0,.1),(0,0,.35)],[.062,.048],8 if fire else 1,10)
    for s in [-1,1]:
        tube('Wing guard',[(0,0,.21),(s*.13,0,.28),(s*.22,0,.41),(s*.19,0,.49)],[.055,.035,.018,.002],metal,6)
        # Two flowing strands follow the old Terra/skyspine family, leaving actual gaps.
        tube('Spiral energy',[(s*.05,0,.33),(s*.14,0,.50),(s*.12,0,.73),(-s*.09,0,.98),(-s*.10,0,1.19),(s*.045,0,1.43),(0,0,1.56)],[.035,.042,.045,.045,.035,.020,.002],energy,6,other=core)
    blade('Energy core',[(.35,0,.065,.025),(.65,0,.09,.027),(1.12,0,.07,.022),(1.56,0,.002,.001)],core,energy)
    gem('Emitter jewel',(0,-.055,.3),(.065,.025,.08),22 if fire else 15)
    for side in [-1,1]:
        prism('Emitter fin',[(side*.035,.30),(side*.12,.36),(side*.17,.25),(side*.11,.15)],.018,metal,edge=10 if fire else 3)

def greatsword():
    grip(-.40,.12,0,11,.052,8)
    prism('Golden crossguard',[(-.39,.16),(-.43,.25),(-.37,.31),(-.11,.25),(0,.29),(.11,.25),(.37,.31),(.43,.25),(.39,.16),(.11,.19),(0,.15),(-.11,.19)],.09,9,edge=8)
    gem('Guard sapphire',(0,-.080,.24),(.087,.047,.105),15)
    blade('Broad steel blade',[(.29,0,.23,.047),(.40,0,.36,.052),(1.40,0,.31,.042),(1.61,0,.25,.034),(1.82,0,.002,.002)],2,3)
    prism('Inset fuller',[(-.027,.44),(-.027,1.49),(0,1.64),(.027,1.49),(.027,.44)],.007,8,y=-.056)
    for z in [.51,.65,.79]:rune('Blade gold inlay',0,z,.043,9,y=-.063)

def shortsword():
    grip(-.25,.11,19,4,.043,6)
    for side in [-1,1]:
        tube('Swept bronze guard',[(0,0,.18),(side*.14,0,.20),(side*.21,0,.32),(side*.18,0,.40)],[.045,.036,.022,.005],8,8,other=9)
    gem('Guard amethyst',(0,-.058,.20),(.062,.035,.075),17)
    blade('Purple wave blade',[(.28,0,.17,.036),(.41,-.025,.22,.036),(.55,.025,.18,.033),(.70,-.025,.20,.030),(.85,.035,.14,.026),(.98,.055,.11,.022),(1.13,.12,.001,.001)],16,18)
    for x,z in [(-.01,.45),(.005,.67),(.045,.86)]:rune('Amethyst fuller',x,z,.048,17,y=-.04)

def blunt():
    grip(-.32,.20,25,0,.049,7)
    tube('Lacquered shaft',[(0,0,.16),(0,0,.74)],.053,25,10,other=24)
    for z in [.25,.38,.51,.64]:ring('Shaft ferrule',z,.061,.036,9)
    # A red, horned club head follows the old icon; it is not a blue rectangular hammer.
    blade('Heavy crimson head',[(.69,0,.16,.075),(.78,0,.28,.095),(1.03,0,.32,.11),(1.23,0,.24,.085),(1.38,0,.08,.04)],24,8)
    for s in [-1,1]:
        tube('Crown horn',[(s*.10,0,1.10),(s*.24,0,1.28),(s*.21,0,1.47)],[.06,.04,.002],9,6,other=10)
        for z in [.83,1.02]:
            tube('Side spur',[(s*.10,0,z),(s*.23,0,z+.09)],[.052,.002],8,5)
    gem('Lightning heart',(0,-.12,1.00),(.065,.035,.105),7)
    rune('Lightning mark',0,.88,.045,10,y=-.125)
    ring('Heavy neck collar',.73,.105,.065,8)

def bylle():
    grip(-.44,.13,0,25,.042,11)
    # Long single-edged blade and a deep swept tip, deliberately slimmer than Gram.
    prism('Bylle blade',[(-.065,.28),(-.060,1.73),(-.020,1.93),(.075,1.75),(.068,.35)],.055,2,edge=3)
    tube('Dark blade spine',[(-.055,-.032,.35),(-.051,-.032,1.70),(-.015,-.018,1.88)],.009,1,4)
    prism('Bylle sculpted guard',[(-.25,.12),(-.28,.22),(-.17,.24),(-.08,.19),(0,.27),(.08,.19),(.20,.25),(.27,.22),(.23,.12),(.09,.16),(0,.10),(-.09,.16)],.065,8,edge=9)
    gem('Bylle centre',(0,-.052,.205),(.043,.025,.055),24)
    ring('Bylle habaki',.30,.055,.085,9)
    # Suspended red sword knot: actual separated cords and two thick ribbon tails.
    loop=[(.055+.075*math.cos(j*2*math.pi/20),-.018,-.45+.10*math.sin(j*2*math.pi/20))for j in range(21)]
    tube('Sword knot loop',loop,.012,24,6)
    for s in [-1,1]:
        x=.12+s*.035
        prism('Silk tassel',[(x,-.49),(x+.03,-.50),(x+.025,-.80),(x+.055,-.94),(x+.018,-.90),(x-.008,-.96),(x-.025,-.78)],.014,25 if s<0 else 24,y=-.02)
    ring('Knot fitting',-.50,.038,.04,9,x=.12)

def willy():
    tube('Twisted wooden staff',[(0,0,-.42),(-.012,0,-.1),(.012,0,.3),(-.018,0,.7),(.015,0,1.08)],[.035,.035,.04,.043,.055],20,9,other=21)
    for z in [-.36,-.04,.37,.77,1.04]:ring('Staff binding',z,.052,.047,9)
    gem('Staff foot',(0,0,-.47),(.046,.046,.07),8)
    # The archived item icon supplies the red/gold imp motif. Back/side details are simplified.
    blade('Willy collar',[(1.03,0,.10,.07),(1.13,0,.24,.10),(1.23,0,.24,.10)],9,8)
    blade('Willy imp head',[(1.17,0,.18,.08),(1.28,0,.31,.13),(1.43,0,.29,.12),(1.53,0,.17,.07)],26,24)
    for s in [-1,1]:
        tube('Swept gold horn',[(s*.10,0,1.40),(s*.24,0,1.52),(s*.23,0,1.68),(s*.14,0,1.66)],[.06,.047,.021,.002],9,7,other=10)
        prism('Ruby ear',[(s*.10,1.36),(s*.26,1.38),(s*.24,1.26),(s*.13,1.25)],.045,24)
        gem('Eye white',(s*.066,-.119,1.39),(.038,.017,.035),30)
        gem('Imp pupil',(s*.066,-.139,1.387),(.014,.007,.023),27)
        tube('Expressive eyebrow',[(s*.025,-.13,1.45),(s*.1,-.13,1.47)],.012,25,5)
    tube('Mischievous smile',[(-.065,-.123,1.30),(-.02,-.137,1.28),(.027,-.137,1.29),(.063,-.123,1.33)],.009,25,5)
    gem('Brow jewel',(0,-.079,1.51),(.028,.018,.04),22)
    for s in [-1,1]:
        tube('Ribbon tie',[(0,0,1.17),(s*.11,-.01,1.05),(s*.12,0,.84)],.012,24,5)

def niwu():
    # Approximate staff based on the archived Manpa flute silhouette, explicitly labelled in metadata.
    tube('Long redwood baton',[(0,0,-.52),(0,0,1.44)],[.031,.034],20,10,other=21)
    for z in [-.48,-.35,-.03,.13,.44,.74,1.04,1.36]:ring('Gold bamboo joint',z,.043,.035,9)
    tube('Grip wrap',[(0,0,-.16),(0,0,.08)],.038,25,10)
    for z in [-.12,-.06,0,.06]:ring('Grip thread',z,.040,.012,4)
    for z in [.31,.50,.69,.88,1.07]:
        gem('Flute hole',(0,-.037,z),(.013,.007,.019),27)
        for s in [-1,1]:rune('Gold flute detail',s*.025,z,.013,9,y=-.027)
    gem('Jade foot',(0,0,-.56),(.044,.043,.06),22)
    gem('Gold crown',(0,0,1.49),(.06,.05,.09),9)
    gem('Top jewel',(0,-.035,1.50),(.022,.017,.034),24)
    tube('Crown tie',[(.025,0,1.40),(.11,0,1.30),(.13,0,1.06)],.009,25,5)

builders={'common_katana':katana,'common_lightsaber':lambda:light_blade(False),'common_greatsword':greatsword,'common_shortsword':shortsword,'common_blunt':blunt,'skyspine':lambda:light_blade(True),'bylle':bylle,'willy_staff':willy,'niwu':niwu}

def export(spec):
    ident,cn,short,quality,refid,kind,approx=spec
    objs=[o for o in groups[ident].objects if o.type=='MESH']
    vertices=[];normals=[];uvs=[];faces=[];face_materials=[]
    for o in objs:
        d=o.data
        # Resolve concave caps before writing; retain ordinary quads for the OBJ loader.
        bm=bmesh.new();bm.from_mesh(d)
        bmesh.ops.triangulate(bm,faces=[f for f in bm.faces if len(f.verts)>4])
        bm.to_mesh(d);bm.free();d.update()
        start=len(vertices)
        vertices.extend(tuple(v.co)for v in d.vertices)
        layer=d.uv_layers.active
        for p in d.polygons:
            fi=[];ni=len(normals)+1;normals.append(tuple(p.normal))
            for li in p.loop_indices:
                uvs.append(tuple(layer.data[li].uv))
                fi.append((start+d.loops[li].vertex_index+1,len(uvs),ni))
            faces.append(fi)
            face_materials.append('energy'if ident=='common_lightsaber'and p.material_index in ENERGY_ALPHA else 'atlas')
    # Triangulation did not add vertices; all parts remain in the canonical grip coordinate system.
    for game in [False,True]:
        folder=ASSETS/'models/weapons'if game else ART/'exports'
        lines=[f'# {cn}; authored in Blender; grip origin is zero',f'mtllib {ident}.mtl',f'o {ident}']
        if game:
            lines += [f'v {x+.5:.6f} {z+.5:.6f} {.5-y:.6f}'for x,y,z in vertices]
            lines += [f'vn {x:.6f} {z:.6f} {-y:.6f}'for x,y,z in normals]
        else:
            lines += [f'v {x:.6f} {y:.6f} {z:.6f}'for x,y,z in vertices]
            lines += [f'vn {x:.6f} {y:.6f} {z:.6f}'for x,y,z in normals]
        lines += [f'vt {u:.6f} {v:.6f}'for u,v in uvs]
        for mat in ['atlas','energy']:
            selected=[f for f,m in zip(faces,face_materials)if m==mat]
            if selected:
                lines.append('usemtl '+mat)
                lines += ['f '+' '.join(f'{v}/{uv}/{n}'for v,uv,n in f)for f in selected]
        (folder/f'{ident}.obj').write_text('\n'.join(lines)+'\n',encoding='utf-8')
        texture='#atlas'if game else '../textures/weapon_atlas.png'
        mtl='newmtl atlas\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd '+texture+'\n'
        if 'energy'in face_materials:
            texture='#energy'if game else '../textures/weapon_energy.png'
            mtl+='\nnewmtl energy\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd '+texture+'\n'
        (folder/f'{ident}.mtl').write_text(mtl,encoding='utf-8')
    lo=[min(p[i]for p in vertices)for i in range(3)];hi=[max(p[i]for p in vertices)for i in range(3)]
    reference_file=REF_OVERRIDES.get(ident,ref_by_id[refid]['primary_reference'])
    return {'id':ident,'name':cn,'kind':kind,'scope':quality,'approximate':approx,'notes':MODEL_NOTES.get(ident,'按已收集轮廓与配色制作的简化低模。'),'reference_id':refid,'reference_file':'../weapon-references/'+reference_file,'reference_sources':list(dict.fromkeys(a['source_url']for a in ref_by_id[refid]['assets'])),'mesh_parts':len(objs),'vertices':len(vertices),'polygons':len(faces),'bounds_blender':{'min':lo,'max':hi},'editable_collection':ident,'runtime_obj':f'models/weapons/{ident}.obj','runtime_texture':'textures/item/weapon3d/weapon_atlas.png'}

report=[]
for spec in SPECS:
    ident=spec[0]
    current=bpy.data.collections.new(ident);scene.collection.children.link(current);groups[ident]=current
    root=bpy.data.objects.new(ident+'_grip',None);current.objects.link(root);roots[ident]=root
    root['weapon_name']=spec[1];root['reference_id']=spec[4];root['approximate']=spec[6]
    root['grip_origin']='0,0,0; Blender Z is blade length; game transform maps to Y'
    builders[ident]()
    item=export(spec);report.append(item)
    print('CREATED',ident,item['vertices'],item['polygons'],flush=True)

def item_definition(ident,icon):
    # Display transforms are provisional until user-authorized in-game verification.
    # Blender UV v starts at the PNG bottom; NeoForge's sprite v starts at its top.
    textures={'atlas':f'{NS}:item/weapon3d/weapon_atlas','particle':f'{NS}:{icon}'}
    if ident=='common_lightsaber':textures['energy']=f'{NS}:item/weapon3d/weapon_energy'
    return {'loader':'neoforge:obj','model':f'{NS}:models/weapons/{ident}.obj','automatic_culling':False,'shade_quads':True,'flip_v':True,'ambientocclusion':False,'gui_light':'front','textures':textures,'display':{
      'gui':{'rotation':[0,-25,-32],'translation':[0,-3,0],'scale':[.53,.53,.53]},
      'ground':{'rotation':[0,0,82],'translation':[0,3,0],'scale':[.42,.42,.42]},
      'fixed':{'rotation':[0,180,-30],'translation':[0,-3,0],'scale':[.56,.56,.56]},
      'thirdperson_righthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
      'thirdperson_lefthand':{'rotation':[0,0,0],'translation':[0,0,0],'scale':[.8,.8,.8]},
      'firstperson_righthand':{'rotation':[0,-25,-25],'translation':[-2,0,0],'scale':[.6,.6,.6]},
      'firstperson_lefthand':{'rotation':[0,25,25],'translation':[0,0,0],'scale':[.6,.6,.6]},
      'head':{'rotation':[0,0,0],'translation':[0,4,0],'scale':[.5,.5,.5]}}}

bindings=[]
for kind in ['katana','lightsaber','greatsword','shortsword','blunt']:
    for band in [10,20,30,40,50]:
        appearance=f'{kind}_{band}_base';ident='common_'+kind
        file=ASSETS/f'models/item/expansion/{appearance}.json'
        file.write_text(json.dumps(item_definition(ident,f'item/expansion/{appearance}'),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        bindings.append({'appearance':appearance,'mesh':ident,'file':str(file.relative_to(ROOT)).replace('\\','/')})
for ident in ['skyspine','bylle','willy_staff','niwu']:
    file=ASSETS/f'models/item/named/{ident}.json'
    file.write_text(json.dumps(item_definition(ident,f'item/named/{ident}'),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    bindings.append({'appearance':ident,'mesh':ident,'file':str(file.relative_to(ROOT)).replace('\\','/')})

# An isolated presentation collection is never exported into the weapon meshes.
stage=bpy.data.collections.new('Presentation_only');scene.collection.children.link(stage)
stage_materials={}
for key,color,rough in [('panel','#192737',.82),('copper','#be8d54',.5),('paper','#e8edf1',.65),('epic','#ba93e7',.6),('mythic','#e8ba67',.6)]:
    mat=bpy.data.materials.new('Presentation_'+key)
    mat.use_nodes=True
    node=next(n for n in mat.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    node.inputs['Base Color'].default_value=(*[linear(c)for c in rgb(color)],1)
    node.inputs['Roughness'].default_value=rough
    stage_materials[key]=mat
def stage_box(name,location,scale,color):
    bpy.ops.mesh.primitive_cube_add(size=1,location=location)
    o=bpy.context.object;o.name=name;o.scale=scale
    for c in list(o.users_collection):c.objects.unlink(o)
    stage.objects.link(o);o.data.materials.append(stage_materials[color]);return o
def label(body,loc,size,color='paper'):
    curve=bpy.data.curves.new('Label','FONT');curve.body=body;curve.size=size;curve.align_x='CENTER';curve.extrude=0
    o=bpy.data.objects.new(body,curve);stage.objects.link(o);o.location=loc;o.rotation_euler=(math.pi/2,0,0);curve.materials.append(stage_materials[color]);return o
for i,spec in enumerate(SPECS):
    col=i%3;row=i//3;x=(col-1)*2.65;z=(2-row)*3.3+1.85
    bounds=report[i]['bounds_blender'];lo=bounds['min'][2];hi=bounds['max'][2]
    scale=min(1,2.15/(hi-lo))
    roots[spec[0]].location=(x,0,z+.15-(lo+hi)*.5*scale)
    roots[spec[0]].scale=(scale,scale,scale)
    accent='copper'if i<5 else 'epic'if i<7 else 'mythic'
    stage_box('Backdrop',(x,.30,z),(2.48,.10,3.04),'panel')
    stage_box('Copper accent',(x,.23,z-1.05),(2.0,.015,.013),accent)
    label(spec[2],(x,-.04,z-1.23),.15)
    label(spec[3],(x,-.03,z-1.41),.070,accent)
label('YUMIS / WEAPON FORMS',(0,0,10.80),.32)
label('FIVE SHARED FORMS   /   FOUR NAMED WEAPONS',(0,0,10.39),.105,'copper')

def area(name,loc,power,size,color):
    d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size;d.color=color
    o=bpy.data.objects.new(name,d);scene.collection.objects.link(o);o.location=loc
    o.rotation_euler=(Vector((0,0,3.8))-o.location).to_track_quat('-Z','Y').to_euler();return o
lights=[area('Warm key',(-4,-5,10),1700,7,(1,.85,.67)),area('Cool fill',(5,-4,5),1300,6,(.63,.82,1)),area('Top rim',(1,3,9),2000,5,(.75,.86,1))]
camdata=bpy.data.cameras.new('Weapon presentation');camdata.type='ORTHO'
camera=bpy.data.objects.new('Weapon presentation',camdata);scene.collection.objects.link(camera);scene.camera=camera
def camera_at(loc,target,scale):
    camera.location=loc;camera.rotation_euler=(Vector(target)-camera.location).to_track_quat('-Z','Y').to_euler();camera.data.ortho_scale=scale
camera_at((.5,-26,6.4),(0,0,5.6),11.8)
scene.render.resolution_x=1800;scene.render.resolution_y=2200
scene.render.filepath=str(ART/'renders/nine-weapons-overview.png')
scene['library_notes']='Nine approved stylized low-poly weapons. Niwu is an approximate design. Game-scale/hand offsets await in-game verification.'
atlas.pack()
energy_atlas.pack()
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(ART/'weapons-v1.blend'))
(ART/'manifest.json').write_text(json.dumps({'source':'tools/build_weapon_models.py','blender_version':bpy.app.version_string,'weapons':report,'bindings':bindings,'runtime_status':'resource_definitions_written; in-game verification pending','texture_note':'New procedural colour atlas authored in Blender; reference screenshots are not used as UV maps.'},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def delivery_index():
    lines=['# 首批9把武器低模','',
      '2026-10-28 · 5种标准版共用造型 + 4把具名武器；蓝色光剑增加半透明能量层。游戏内预览状态见制作说明。','',
      '[离线参考对照页](index.html) · [Blender源稿](weapons-v1.blend) · [完整索引](manifest.json) · [九把总览](renders/nine-weapons-overview.png)','',
      '![九把总览](renders/nine-weapons-overview.png)','',
      '| 名称／造型参考 | 模型ID | 顶点／面 | 单独预览 | 参考 |','|---|---|---:|---|---|']
    cards=[]
    for w in report:
        ident=w['id'];name=html.escape(w['name']);note=html.escape(w['notes'])
        quality='标准版共用'if ident.startswith('common_')else '具名史诗'if ident in ['skyspine','bylle']else '具名神话'
        lines.append(f"| {w['name']} | {ident} | {w['vertices']}／{w['polygons']} | [PNG](renders/{ident}.png) | [原图]({w['reference_file']}) |")
        source_links=' / '.join(f'<a href="{html.escape(url,quote=True)}" target="_blank" rel="noreferrer">来源{i+1}</a>'for i,url in enumerate(w['reference_sources']))
        cards.append(f'''<article class="card">
<div class="card-top"><span>{quality}</span><span>{w['vertices']} 顶点 · {w['polygons']} 面</span></div>
<h2>{name}</h2><div class="pair"><figure><a href="{w['reference_file']}"><img class="reference" src="{w['reference_file']}" alt="{name}参考原图"></a><figcaption>归档参考原图</figcaption></figure><figure><a href="renders/{ident}.png"><img src="renders/{ident}.png" alt="{name}低模渲染"></a><figcaption>Blender 低模初版</figcaption></figure></div>
<p>{note}</p><details><summary>源文件与来源</summary><p><code>{ident}</code> · <a href="exports/{ident}.obj">OBJ</a> · <a href="exports/{ident}.mtl">MTL</a></p><p>{source_links}</p></details></article>''')
    lines += ['', '## 文件与使用', '',
      '- `weapons-v1.blend` 内含9个可编辑武器集合，握持点空对象与独立展示布景；调色板贴图已打包。',
      '- `exports/` 保存Blender坐标的9组OBJ／MTL；实体贴图为 `textures/weapon_atlas.png`，蓝色光剑能量层为 `textures/weapon_energy.png`。',
      '- `renders/` 保存总览和9张透明背景单独预览。',
      '- 游戏资源位于 `../../src/main/resources/assets/yumis_dungeon_and_fight/models/weapons/`；29项外观引用见 `manifest.json`。',
      '- 重制入口：仓库根目录的 `tools/build_weapon_models.py`，由Blender Python运行；详细说明与待测项目见 [制作说明](../../doc/武器外形制作说明.md)。','',
      '## 造型与状态', '',
      '所有模型按参考轮廓与配色简化制作，具有实体厚度；贴图为本次新制调色板，参考截图没有直接作为UV贴图。标准版5个造型覆盖25个base外观，装备显示名称与属性保持原值，其余50个常规史诗／神话外观不改。','',
      '天脊采用经典旧50级橙金色造型（合图右栏）。威利的侧背面和表情为简化补全。尼巫采用万波息笛近似轮廓，未找到可靠同名参考。','',
      '本工具负责Blender生成、保存与渲染，不执行游戏或验收脚本。最新游戏内预览结果及尚未覆盖的视角见制作说明；重新生成资源后需要复看。Blender预览包含PBR材质，普通OBJ游戏材质不保证相同的金属反射或自发光。','']
    (ART/'README.md').write_text('\n'.join(lines),encoding='utf-8')
    document='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>首批9把武器 · 参考与低模</title><style>
*{box-sizing:border-box}body{margin:0;background:#111c28;color:#e9e7df;font-family:"Microsoft YaHei",system-ui,sans-serif;line-height:1.65}main{max-width:1440px;margin:auto;padding:52px 32px 64px}a{color:#dfb778;text-underline-offset:4px}header{display:flex;justify-content:space-between;gap:36px;align-items:flex-end;border-bottom:1px solid #354456;padding-bottom:30px;margin-bottom:32px}.eyebrow{color:#c6a16d;letter-spacing:.2em;font-size:12px}h1{font-size:38px;margin:9px 0 14px;line-height:1.25}header p{max-width:760px;color:#aeb9c6;margin:8px 0}.status{white-space:nowrap;color:#d9c398;font-size:13px;padding:8px 14px;border:1px solid #725d39;border-radius:4px}.links{display:flex;flex-wrap:wrap;gap:18px;font-size:14px;margin-top:19px}.grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:22px}.card{min-width:0;background:#1c2b3b;border:1px solid #354456;padding:22px}.card-top{display:flex;justify-content:space-between;gap:12px;color:#b8a585;font-size:11px}h2{font-size:21px;font-weight:600;line-height:1.4;margin:14px 0 20px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:10px}figure{margin:0;min-width:0;background:#14202d;border:1px solid #2d3e50}figure a{display:block;height:262px;padding:9px}figure img{width:100%;height:100%;object-fit:contain}figure .reference{image-rendering:pixelated}figcaption{padding:8px 6px;text-align:center;border-top:1px solid #2d3e50;color:#99acbf;font-size:11px}.card>p{font-size:12px;color:#afbdc9;min-height:60px;margin:15px 0}details{font-size:12px;color:#aab6c2;border-top:1px solid #344658;padding-top:12px}summary{cursor:pointer}code{font-size:11px}footer{margin-top:32px;padding:24px 26px;background:#182635;border-left:2px solid #b18a55;color:#b4c0ce;font-size:13px}footer p{margin:6px 0}@media(max-width:1120px){.grid{grid-template-columns:repeat(2,minmax(0,1fr))}header{display:block}.status{display:inline-block;margin-top:18px}}@media(max-width:700px){main{padding:28px 16px}.grid{grid-template-columns:1fr}h1{font-size:29px}.card{padding:18px}figure a{height:250px}.card>p{min-height:0}}
</style><main><header><div><div class="eyebrow">YUMIS · WEAPON ART / 01</div><h1>首批9把武器：参考与低模</h1><p>五种标准版共用造型，四把具名武器独立制作。点击图片可查看原图或透明背景渲染；源稿保留所有可编辑部件。</p><nav class="links"><a href="weapons-v1.blend">Blender源稿</a><a href="renders/nine-weapons-overview.png">九把总览</a><a href="textures/weapon_atlas.png">新制贴图</a><a href="manifest.json">完整索引</a></nav></div><span class="status">初版已制作 · 游戏内待验证</span></header><section class="grid">'''+''.join(cards)+'''</section><footer><p>造型与配色依据归档参考简化；截图、图标仅作参考，未直接用于模型UV。</p><p>天脊选用旧50级橙金色版本；威利细节有简化补全；尼巫采用万波息笛近似轮廓。Blender的反射与自发光不代表游戏实际效果。</p><p>29项现有外观已写入资源引用，名称、数值、词条、套装和获取途径保持原设计。手持、GUI、落地与物品展示框仍待游戏内确认。</p></footer></main></html>'''
    (ART/'index.html').write_text(document,encoding='utf-8')

delivery_index()

if '--no-render'not in sys.argv:
    bpy.ops.render.render(write_still=True)
    stage.hide_render=True
    for spec in SPECS:
        ident=spec[0]
        for key,collection in groups.items():collection.hide_render=key!=ident
        old=roots[ident].location.copy();old_scale=roots[ident].scale.copy()
        roots[ident].location=(0,0,0);roots[ident].scale=(1,1,1)
        bounds=next(w['bounds_blender']for w in report if w['id']==ident)
        center=Vector([(a+b)*.5 for a,b in zip(bounds['min'],bounds['max'])])
        camera_at(center+Vector((2.4,-7,1.15)),center,1)
        scene.render.resolution_x=700;scene.render.resolution_y=1000
        # Fit the complete volume, including long tassels, to a portrait preview.
        inv=camera.rotation_euler.to_matrix().transposed()
        corners=[inv@Vector((x,y,z))for x in (bounds['min'][0],bounds['max'][0])for y in (bounds['min'][1],bounds['max'][1])for z in (bounds['min'][2],bounds['max'][2])]
        frame=camera.data.view_frame(scene=scene)
        camera.data.ortho_scale=1.14*max((max(p[i]for p in corners)-min(p[i]for p in corners))/(max(p[i]for p in frame)-min(p[i]for p in frame))for i in (0,1))
        for light in lights:
            light.location.z-=3.0;light.rotation_euler=(Vector((0,0,.7))-light.location).to_track_quat('-Z','Y').to_euler()
        scene.render.film_transparent=True
        scene.render.filepath=str(ART/f'renders/{ident}.png')
        bpy.ops.render.render(write_still=True)
        roots[ident].location=old;roots[ident].scale=old_scale
        for light in lights:light.location.z+=3.0
        print('RENDERED',ident,flush=True)
    for c in groups.values():c.hide_render=False
    stage.hide_render=False;scene.render.film_transparent=False
    camera_at((.5,-26,6.4),(0,0,5.6),11.8)
    scene.render.resolution_x=1800;scene.render.resolution_y=2200
    scene.render.filepath=str(ART/'renders/nine-weapons-overview.png')
    # Save the restored overview layout, not the last isolated preview.
    bpy.context.preferences.filepaths.save_version=0
    bpy.ops.wm.save_as_mainfile(filepath=str(ART/'weapons-v1.blend'))
print('FINISHED',ART,flush=True)

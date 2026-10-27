"""Run inside Blender in background mode. Uses the actual exported game models/textures."""
import bpy, json, math, sys
from pathlib import Path
from mathutils import Vector, Matrix
from bpy_extras.object_utils import world_to_camera_view

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/yumis_dungeon_and_fight'
ARCANE='--arcane' in sys.argv
VERSION='v2' if ARCANE else 'v1'
SOURCE=ROOT/('art/industry-arcane' if ARCANE else 'art/industry-refresh')
PREVIEW=ROOT/'art/previews';PREVIEW.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
meshes=json.loads((SOURCE/'meshes.json').read_text(encoding='utf-8'))
materials={};roots=[]
def material(texture):
    if texture in materials:return materials[texture]
    path=ASSETS/'textures'/(texture.split(':')[-1]+'.png')
    mat=bpy.data.materials.new(texture.split(':')[-1]);mat.use_nodes=True
    nodes=mat.node_tree.nodes;bsdf=next(n for n in nodes if n.type=='BSDF_PRINCIPLED')
    bsdf.inputs['Roughness'].default_value=.85
    image=bpy.data.images.load(str(path),check_existing=True)
    tex=nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
    mat.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    mat.node_tree.links.new(tex.outputs['Alpha'],bsdf.inputs['Alpha'])
    materials[texture]=mat;return mat

def model_cube(element,textures,parent):
    x,y,z=element['from'];X,Y,Z=element['to']
    face_vertices={
        'north':[(x,y,z),(x,Y,z),(X,Y,z),(X,y,z)],
        'south':[(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],
        'east':[(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],
        'west':[(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],
        'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],
        'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]}
    rotation=element.get('rotation');transform=Matrix.Identity(4)
    if rotation:
        origin=Vector(rotation['origin']);axis={'x':Vector((1,0,0)),'y':Vector((0,1,0)),'z':Vector((0,0,1))}[rotation['axis']]
        transform=Matrix.Translation(origin)@Matrix.Rotation(math.radians(rotation['angle']),4,axis)@Matrix.Translation(-origin)
    vertices=[];faces=[];uvs=[];mats=[]
    for face,desc in element['faces'].items():
        texture=desc['texture']
        while texture.startswith('#'):texture=textures[texture[1:]]
        mats.append(material(texture));face_points=[]
        for p in face_vertices[face]:
            v=transform@Vector(p);face_points.append((v.x/16-.5,-v.z/16+.5,v.y/16-.5))
        start=len(vertices);vertices+=face_points;faces.append(tuple(range(start,start+4)))
        u,v,U,V=desc.get('uv',[0,0,16,16]);uvs.append([(u/16,1-V/16),(u/16,1-v/16),(U/16,1-v/16),(U/16,1-V/16)])
    data=bpy.data.meshes.new('model_element');data.from_pydata(vertices,[],faces);data.update()
    obj=bpy.data.objects.new('element',data);bpy.context.collection.objects.link(obj);obj.parent=parent
    uv=data.uv_layers.new()
    for i,p in enumerate(data.polygons):
        data.materials.append(mats[i]);p.material_index=i
        for loop,xy in zip(p.loop_indices,uvs[i]):uv.data[loop].uv=xy

def obj_mesh(data,textures,parent):
    vertices=[(v[0]-.5,-v[2]+.5,v[1]-.5) for v in data['vertices']]
    m=bpy.data.meshes.new('crystal_geometry');m.from_pydata(vertices,[],data['faces']);m.update()
    o=bpy.data.objects.new('crystal_and_plinth',m);bpy.context.collection.objects.link(o);o.parent=parent
    if 'uvs' in data:
        names=list(dict.fromkeys(data.get('materials',['atlas'])));names=[n for n in names if n in textures]
        for name in names:m.materials.append(material(textures[name]))
        uv=m.uv_layers.new()
        for i,face in enumerate(m.polygons):
            face.material_index=names.index(data.get('materials',['atlas']*len(m.polygons))[i])
            for loop,xy in zip(face.loop_indices,data['uvs'][i]):uv.data[loop].uv=xy
        return
    m.materials.append(material(textures['crystal']));m.materials.append(material(textures['base']))
    uv=m.uv_layers.new()
    for face,band in zip(m.polygons,data['bands']):
        face.material_index=0 if band>=0 else 1
        u,U=((band+.18)/8,(band+.82)/8) if band>=0 else (.08,.92)
        coords=[(u,.1),(u,.9),(U,.9),(U,.1)] if len(face.vertices)==4 else [(u,.15),((u+U)/2,.9),(U,.15)]
        for loop,xy in zip(face.loop_indices,coords):uv.data[loop].uv=xy

def load_model(path,parent):
    data=json.loads(path.read_text(encoding='utf-8'))
    if data.get('loader')=='neoforge:obj':
        key=Path(data['model'].split(':')[-1]).stem;obj_mesh(meshes[key],data['textures'],parent)
    elif 'elements' in data:
        for element in data['elements']:model_cube(element,data['textures'],parent)
    elif data.get('parent','').startswith('yumis_dungeon_and_fight:'):
        load_model(ASSETS/'models'/(data['parent'].split(':')[-1]+'.json'),parent)
    elif data.get('parent')=='minecraft:block/cube_all':
        tex=data['textures']['all'];model_cube({'from':[0,0,0],'to':[16,16,16],
            'faces':{f:{'texture':'#all'} for f in ['north','south','east','west','up','down']}},{'all':tex},parent)

catalog=json.loads((ROOT/'temp/industrial-art-manifest.json').read_text())
labels=json.loads((ASSETS/'lang/zh_cn.json').read_text(encoding='utf-8'))
entries=list(catalog['devices'])+['terra_block','spacetime_block','industrial_magic_stone_block','energy_crystal_block_2','lead_block','uranium_block','titanium_block','equipment_fabricator']
scene=bpy.context.scene
engines=scene.render.bl_rna.properties['engine'].enum_items
print('Render engines:',[x.identifier for x in engines])
selected=next((x.identifier for x in engines if 'EEVEE' in x.identifier),None)
if selected:scene.render.engine=selected
scene.render.resolution_x=1600;scene.render.resolution_y=1400;scene.render.resolution_percentage=100
scene.render.film_transparent=True
formats=scene.render.image_settings.bl_rna.properties['file_format'].enum_items
scene.render.image_settings.file_format=next(x.identifier for x in formats if x.identifier=='PNG')
try:scene.view_settings.view_transform='Standard'
except TypeError:pass
world=scene.world;world.use_nodes=True
next(n for n in world.node_tree.nodes if n.type=='BACKGROUND').inputs['Strength'].default_value=.6
bpy.ops.object.camera_add(location=(7,10,8))
cam=bpy.context.object;cam.name='ReviewCamera';cam.rotation_euler=(-cam.location).to_track_quat('-Z','Y').to_euler()
cam.data.type='ORTHO';cam.data.ortho_scale=13.7;scene.camera=cam
orientation=cam.rotation_euler.to_matrix();right=orientation@Vector((1,0,0));up=orientation@Vector((0,1,0))
for i,key in enumerate(entries):
    parent=bpy.data.objects.new(key,None);bpy.context.collection.objects.link(parent)
    load_model(ASSETS/f'models/{"block" if key=="equipment_fabricator" else "item"}'/f'{key}.json',parent)
    parent.location=right*((i%8-3.5)*1.65)+up*((3-i//8)*1.65)
    if ARCANE and key=='wind_generator':parent.scale=(.38,.38,.38);parent.location.z-=.38
    roots.append((key,parent))
for index,(loc,energy) in enumerate([((5,8,10),2.0),((-4,-6,7),.8)]):
    bpy.ops.object.light_add(type='SUN',location=loc);light=bpy.context.object
    light.rotation_euler=(-light.location).to_track_quat('-Z','Y').to_euler();light.data.energy=energy;light.data.angle=.25
for image in bpy.data.images:
    if image.source=='FILE':image.pack()
scene.render.filepath=str(PREVIEW/f'industrial-{VERSION}-render.png')
bpy.ops.wm.save_as_mainfile(filepath=str(SOURCE/f'industrial-{VERSION}.blend'))
bpy.ops.render.render(write_still=True)
positions=[]
for key,obj in roots:
    v=world_to_camera_view(scene,cam,obj.location)
    positions.append({'key':key,'label':labels.get('block.yumis_dungeon_and_fight.'+key,key),
                      'x':round(v.x*1600),'y':round((1-v.y)*1400)})
(PREVIEW/f'industrial-{VERSION}-layout.json').write_text(json.dumps(positions,ensure_ascii=False,indent=2),encoding='utf-8')
if ARCANE:
    for key,obj in roots:
        for child in obj.children:child.hide_render=key!='wind_generator'
        if key=='wind_generator':obj.location=(0,0,-1);obj.scale=(1,1,1)
    scene.render.resolution_x=700;scene.render.resolution_y=1000
    bpy.context.view_layer.update()
    wind=next(obj for key,obj in roots if key=='wind_generator')
    points=[child.matrix_world@Vector(p) for child in wind.children for p in child.bound_box]
    xs=[right.dot(p) for p in points];ys=[up.dot(p) for p in points]
    cam.location+=right*((min(xs)+max(xs))/2)+up*((min(ys)+max(ys))/2)
    cam.data.ortho_scale=max((max(ys)-min(ys))*1.15,(max(xs)-min(xs))*1.15/.7)
    scene.render.filepath=str(PREVIEW/'industrial-v2-wind.png');bpy.ops.render.render(write_still=True)
print('Saved textured Blender scene and first-version preview:',len(roots),'assets')

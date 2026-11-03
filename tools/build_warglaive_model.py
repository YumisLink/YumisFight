"""Author the default silver warglaive with Blender; editable scene and previews stay in temp.

Run: blender --background --factory-startup --python tools/build_warglaive_model.py
Silhouette reference: https://worldofwarcraft.blizzard.com/en-us/news/19940945
The mesh, palette and icons are authored here; no downloaded model or texture is shipped.
"""
import json
import math
from pathlib import Path

import bpy
import bmesh
from mathutils import Vector

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'src/main/resources/assets/yumis_dungeon_and_fight'
WORK = ROOT / 'temp/warglaive-remodel-20261103'
NS = 'yumis_dungeon_and_fight'
IDENT = 'common_warglaive'
for folder in (WORK, ASSETS / 'models/weapons', ASSETS / 'textures/item/weapon3d'):
    folder.mkdir(parents=True, exist_ok=True)

bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)
scene = bpy.context.scene
scene.render.engine = 'CYCLES'
scene.cycles.samples = 40
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.view_settings.view_transform = 'Standard'
scene.view_settings.look = 'None'
scene.world.color = (.13, .13, .13)

PALETTE = [
    ('cutting_edge', 'f3f7fc', .65, .28),
    ('silver_face', 'cbd6e2', .65, .34),
    ('raised_rib', 'e4edf6', .65, .29),
    ('steel_side', '8191a4', .60, .40),
    ('recess', '485867', .48, .47),
    ('dark_grip', '303b47', .12, .74),
    ('frosted_crest', 'e4f3ff', .40, .27),
    ('rivet', 'b7c7d7', .65, .33),
]


def rgb(value):
    return tuple(int(value[i:i+2], 16) / 255 for i in (0, 2, 4))


def linear(value):
    return value / 12.92 if value < .04045 else ((value + .055) / 1.055) ** 2.4


materials = []
for name, color, metallic, roughness in PALETTE:
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    shader = mat.node_tree.nodes.get('Principled BSDF')
    shader.inputs['Base Color'].default_value = (*map(linear, rgb(color)), 1)
    shader.inputs['Metallic'].default_value = metallic
    shader.inputs['Roughness'].default_value = roughness
    materials.append(mat)

atlas = bpy.data.images.new('warglaive_silver', width=64, height=32, alpha=False)
pixels = []
for y in range(32):
    for x in range(64):
        color = rgb(PALETTE[(y // 16) * 4 + x // 16][1])
        pixels.extend((*color, 1))
atlas.pixels[:] = pixels
atlas.filepath_raw = str(ASSETS / 'textures/item/weapon3d/warglaive_silver.png')
atlas.file_format = 'PNG'
atlas.save()
atlas.pack()

weapon = bpy.data.collections.new('Silver warglaive')
scene.collection.children.link(weapon)
objects = []


def mesh(name, vertices, faces, indices):
    data = bpy.data.meshes.new(name)
    data.from_pydata(vertices, [], faces)
    data.update()
    obj = bpy.data.objects.new(name, data)
    weapon.objects.link(obj)
    for mat in materials:
        data.materials.append(mat)
    for face, index in zip(data.polygons, indices):
        face.material_index = index
    objects.append(obj)
    return obj


def plate(name, points, depth, center_y, face=1, side=3, bevel=.012):
    count = len(points)
    vertices = [(x, center_y + s * depth / 2, z) for s in (-1, 1) for x, z in points]
    faces = [tuple(range(count - 1, -1, -1)), tuple(range(count, count * 2))]
    indices = [face, face]
    for i in range(count):
        j = (i + 1) % count
        faces.append((i, j, j + count, i + count))
        indices.append(side)
    obj = mesh(name, vertices, faces, indices)
    bm = bmesh.new()
    bm.from_mesh(obj.data)
    bmesh.ops.recalc_face_normals(bm, faces=list(bm.faces))
    bm.to_mesh(obj.data)
    bm.free()
    if bevel:
        mod = obj.modifiers.new('Honed silver edges', 'BEVEL')
        mod.width = bevel
        mod.segments = 1
        mod.affect = 'EDGES'
        mod.material = 0
    return obj


def ribbon(name, points, width, y, material):
    left, right = [], []
    for i, point in enumerate(points):
        previous = Vector(points[max(0, i - 1)])
        following = Vector(points[min(len(points) - 1, i + 1)])
        direction = (following - previous).normalized()
        normal = Vector((-direction.y, direction.x)) * width / 2
        left.append(tuple(Vector(point) + normal))
        right.append(tuple(Vector(point) - normal))
    return plate(name, left + right[::-1], .008, y, material, material, .001)


# A continuous swept crescent with separate root hooks and a toothed concave edge.
outline = [
    (.18, .10), (.28, .20), (.35, .34), (.47, .25), (.62, .21),
    (.80, .22), (.98, .28), (1.14, .39), (1.29, .55), (1.40, .77),
    (1.46, 1.05), (1.49, 1.20), (1.50, .99), (1.47, .72),
    (1.39, .45), (1.29, .20), (1.22, .25), (1.18, .02),
    (1.09, .12), (.99, -.11), (.92, .02), (.78, -.23),
    (.70, -.08), (.55, -.30), (.49, -.13), (.32, -.29),
    (.35, -.10), (.23, -.08),
]
spine = [(.32, .12), (.47, .13), (.65, .12), (.82, .15), (1, .22),
         (1.16, .33), (1.30, .51), (1.40, .74), (1.46, 1.02)]
for sign, name in ((-1, 'Left'), (1, 'Right')):
    plate(name + ' crescent blade', [(sign*x, z) for x, z in outline], .09, 0)
    for front in (-1, 1):
        ribbon(name + ' inset groove', [(sign*x, z-.045) for x, z in spine], .046, front*.051, 4)
        ribbon(name + ' silver ridge', [(sign*x, z+.018) for x, z in spine], .026, front*.061, 2)
    plate(name + ' root horn', [(sign*x, z) for x, z in
          [(.15, .14), (.22, .35), (.18, .51), (.33, .44), (.38, .27), (.29, .08)]], .12, 0, 2)

# The grip is behind the shield, leaving real space for the hand.
shield = [(-.29, .20), (-.20, .39), (0, .28), (.20, .39), (.29, .20),
          (.23, -.23), (0, -.43), (-.23, -.23)]
plate('Central shield rim', shield, .13, -.082, 2)
plate('Recessed shield face', [(x*.79, z*.79) for x, z in shield], .023, -.158, 4, 3, .008)
plate('Silver chevron crest', [(-.14, .14), (0, .04), (.14, .14), (.10, -.12),
      (0, -.26), (-.10, -.12)], .022, -.180, 1)
plate('White faceted seal', [(0, .15), (.075, .035), (0, -.09), (-.075, .035)], .025, -.205, 6, 2, .008)


def cylinder(name, radius, depth, location, material, vertices=10):
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=depth, location=location)
    obj = bpy.context.object
    obj.name = name
    for collection in list(obj.users_collection):
        collection.objects.unlink(obj)
    weapon.objects.link(obj)
    for mat in materials:
        obj.data.materials.append(mat)
    for face in obj.data.polygons:
        face.material_index = material
    objects.append(obj)
    return obj


cylinder('Rear central grip', .062, .39, (0, .125, -.02), 5)
for i in range(8):
    cylinder('Grip wrap %02d' % i, .066, .012, (0, .125, -.19+i*.047), 4)
for z in (-.24, .22):
    cylinder('Grip collar', .083, .042, (0, .125, z), 2)
    plate('Grip attachment', [(-.075, z-.025), (.075, z-.025), (.075, z+.025), (-.075, z+.025)], .24, .035, 3)
for x in (-.19, .19):
    for z in (-.12, .17):
        rivet = cylinder('Shield rivet', .023, .026, (x, -.167, z), 7, 8)
        rivet.rotation_euler[0] = math.pi/2

# Export evaluated bevels, world-space grip coordinates and flat face normals for the existing OBJ loader.
vertices, normals, uvs, faces = [], [], [], []
depsgraph = bpy.context.evaluated_depsgraph_get()
for obj in objects:
    evaluated = obj.evaluated_get(depsgraph)
    data = evaluated.to_mesh()
    bm = bmesh.new()
    bm.from_mesh(data)
    bmesh.ops.triangulate(bm, faces=[face for face in bm.faces if len(face.verts)>4])
    bm.to_mesh(data)
    bm.free()
    data.update()
    offset = len(vertices)
    vertices.extend(tuple(evaluated.matrix_world @ vertex.co) for vertex in data.vertices)
    normal_matrix = evaluated.matrix_world.to_3x3().inverted().transposed()
    for polygon in data.polygons:
        normals.append(tuple((normal_matrix @ polygon.normal).normalized()))
        material = polygon.material_index
        u, v = ((material%4+.5)/4, (material//4+.5)/2)
        uvs.append((u, v))
        faces.append([(offset+index+1, len(uvs), len(normals)) for index in polygon.vertices])
    evaluated.to_mesh_clear()

lines = ['# Original silver crescent warglaive; grip origin is zero', 'mtllib '+IDENT+'.mtl', 'o '+IDENT]
lines += ['v %.6f %.6f %.6f' % (x+.5, z+.5, .5-y) for x, y, z in vertices]
lines += ['vn %.6f %.6f %.6f' % (x, z, -y) for x, y, z in normals]
lines += ['vt %.6f %.6f' % uv for uv in uvs]
lines += ['usemtl atlas']
lines += ['f '+' '.join('%s/%s/%s' % item for item in face) for face in faces]
(ASSETS / ('models/weapons/'+IDENT+'.obj')).write_text('\n'.join(lines)+'\n', encoding='utf-8')
(ASSETS / ('models/weapons/'+IDENT+'.mtl')).write_text('newmtl atlas\nKd 1 1 1\nKa 0 0 0\nd 1\nmap_Kd #atlas\n', encoding='utf-8')

for band in (10, 20, 30, 40, 50):
    path = ASSETS / ('models/item/standard/warglaive_%s_base.json' % band)
    previous = json.loads(path.read_text(encoding='utf-8'))
    model = {'loader': 'neoforge:obj', 'model': NS+':models/weapons/'+IDENT+'.obj',
             'automatic_culling': False, 'shade_quads': True, 'flip_v': True,
             'ambientocclusion': False, 'gui_light': 'front',
             'textures': {'atlas': NS+':item/weapon3d/warglaive_silver',
                          'particle': NS+':item/standard/warglaive_%s_base' % band},
             'display': previous['display']}
    path.write_text(json.dumps(model, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')


def area(name, position, energy, size, color):
    data = bpy.data.lights.new(name, 'AREA')
    data.energy, data.shape, data.size, data.color = energy, 'DISK', size, color
    obj = bpy.data.objects.new(name, data)
    scene.collection.objects.link(obj)
    obj.location = position
    obj.rotation_euler = (Vector((0, 0, .3))-obj.location).to_track_quat('-Z', 'Y').to_euler()


area('Broad white key', (-3, -4, 6), 800, 4, (1, .97, .94))
area('Cool silver fill', (3, -2, 2), 550, 3, (.80, .89, 1))
area('Edge rim', (0, 3, 3), 1000, 3, (.88, .94, 1))
camera_data = bpy.data.cameras.new('Warglaive camera')
camera_data.type = 'ORTHO'
camera = bpy.data.objects.new('Warglaive camera', camera_data)
scene.collection.objects.link(camera)
scene.camera = camera


def camera_at(position, target, scale):
    camera.location = position
    camera.rotation_euler = (Vector(target)-camera.location).to_track_quat('-Z', 'Y').to_euler()
    camera.data.ortho_scale = scale


scene.render.film_transparent = True
camera_at((.30, -7, 1.60), (0, 0, .36), 3.30)
camera.rotation_euler.rotate_axis('Z', math.radians(-32))
scene.render.resolution_x, scene.render.resolution_y = 32, 32
scene.render.resolution_percentage = 100
scene.cycles.samples = 64
icon = ASSETS / 'textures/item/standard/warglaive_10_base.png'
scene.render.filepath = str(icon)
bpy.ops.render.render(write_still=True)
for band in (20, 30, 40, 50):
    (ASSETS / ('textures/item/standard/warglaive_%s_base.png' % band)).write_bytes(icon.read_bytes())

# A clean presentation render is an art preview, not an in-game test.
scene.render.film_transparent = False
scene.world.color = (.055, .067, .085)
camera_at((.65, -7, 2.35), (0, 0, .36), 3.80)
scene.render.resolution_x, scene.render.resolution_y = 1400, 850
scene.cycles.samples = 48
scene.render.filepath = str(WORK / 'silver-warglaive-front.png')
bpy.ops.render.render(write_still=True)
camera_at((1.9, 6, 2.1), (0, .03, .34), 3.8)
scene.render.filepath = str(WORK / 'silver-warglaive-back.png')
bpy.ops.render.render(write_still=True)
camera_at((.65, -7, 2.35), (0, 0, .36), 3.8)
scene.render.filepath = str(WORK / 'silver-warglaive-front.png')
bpy.ops.wm.save_as_mainfile(filepath=str(WORK / 'silver-warglaive.blend'))
print(json.dumps({'mesh': IDENT, 'vertices': len(vertices), 'faces': len(faces),
                  'bands': [10, 20, 30, 40, 50], 'preview': str(WORK / 'silver-warglaive-front.png')}))

"""Author world-dressing models and their matching Java shapes; no world placement.

All textures reference vanilla assets. Geometry, names, layouts and selection/collision
shapes originate here. Run from any directory with Python 3; no external dependencies.
"""
from __future__ import annotations

import json
import math
import gzip
import struct
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NS = "yumis_dungeon_and_fight"
ASSETS = ROOT / "src/main/resources/assets" / NS
DATA = ROOT / "src/main/resources/data" / NS
MODEL = "block/decorations/"
DEFINITIONS = []
MATERIALS = set()
GALLERY = []
LEAF_LAYOUTS = 4
SCATTER_LAYOUTS = 8
NATIVE_WOODS = ("oak","birch","spruce","jungle","acacia","dark_oak","mangrove","pale_oak","cherry")
MINI_STRUCTURES = (
    ("fallen_tree",64,True,False),
    ("log_bundle",96,False,True),
    ("log_cart",192,False,True),
)
RESOURCE_DROPS = {
    "gray_pebbles": f"{NS}:pebble",
    "dark_pebbles": f"{NS}:pebble",
    "goblin_sling_stones": f"{NS}:pebble",
    "dragon_white_stone_chips": f"{NS}:pebble",
    "ground_branches": "minecraft:stick",
}


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def nbt_string(value):
    encoded=value.encode("utf-8")
    return struct.pack(">H",len(encoded))+encoded


def nbt_payload(value):
    """Only the native NBT types needed by these entity-free structure templates."""
    if isinstance(value,int):
        return 3,struct.pack(">i",value)
    if isinstance(value,str):
        return 8,nbt_string(value)
    if isinstance(value,list):
        children=[nbt_payload(child) for child in value]
        kind=children[0][0] if children else 0
        if any(child[0]!=kind for child in children):
            raise ValueError("NBT lists must have a single element type")
        return 9,bytes([kind])+struct.pack(">i",len(children))+b"".join(child[1] for child in children)
    if isinstance(value,dict):
        fields=[]
        for name,child in value.items():
            kind,payload=nbt_payload(child)
            fields.append(bytes([kind])+nbt_string(name)+payload)
        return 10,b"".join(fields)+b"\x00"
    raise TypeError(type(value))


def native_miniature(kind,wood):
    cells={}
    def block(x,y,z,name,**properties):
        if (x,y,z) in cells:
            raise ValueError(f"Overlapping miniature blocks: {kind} {(x,y,z)}")
        cells[x,y,z]={"Name":"minecraft:"+name,
                      **({"Properties":{key:str(value).lower() for key,value in properties.items()}} if properties else {})}
    def log(x,y,z,axis="z"):
        block(x,y,z,wood+"_log",axis=axis)
    def trapdoor(x,y,z,name,facing,opened):
        block(x,y,z,name+"_trapdoor",facing=facing,half="bottom",open=opened,powered=False,waterlogged=False)
    if kind=="fallen_tree":
        log(0,0,0,"y")
        for z in range(1,5):
            log(1,0,z)
        block(2,0,2,wood+"_fence",north=False,south=False,east=False,west=True,waterlogged=False)
        block(1,1,2,"moss_carpet")
        for x,y,z in [(0,0,4),(2,0,4),(1,0,5),(1,1,4)]:
            block(x,y,z,wood+"_leaves",distance="1",persistent=True,waterlogged=False)
    elif kind=="log_bundle":
        for x in [1,2]:
            for z in range(3):
                log(x,0,z)
        # A compact native iron-trapdoor band touches both sides and the upper log faces.
        trapdoor(0,0,1,"iron","west",True)
        trapdoor(3,0,1,"iron","east",True)
        for x in [1,2]:
            trapdoor(x,1,1,"iron","north",False)
    elif kind=="log_cart":
        for x in [1,2]:
            for z in [1,3]:
                block(x,0,z,wood+"_planks")
            block(x,0,2,"iron_chain",axis="x",waterlogged=False)
            for z in range(1,4):
                log(x,1,z)
            block(x,0,0,wood+"_fence",north=False,south=True,east=x==1,west=x==2,waterlogged=False)
            trapdoor(x,0,4,wood,"south",True)
            trapdoor(x,2,2,"iron","north",False)
        wheel="dark_oak" if wood!="dark_oak" else "spruce"
        trapdoor(0,0,2,wheel,"west",True)
        trapdoor(3,0,2,wheel,"east",True)
        trapdoor(0,1,2,"iron","west",True)
        trapdoor(3,1,2,"iron","east",True)
    else:
        raise ValueError(kind)
    return cells


def write_mini_structures():
    for kind,_,_,_ in MINI_STRUCTURES:
        for wood in NATIVE_WOODS:
            cells=native_miniature(kind,wood)
            palette=[];indices={};blocks=[]
            for position,state in sorted(cells.items(),key=lambda entry:(entry[0][1],entry[0][2],entry[0][0])):
                signature=json.dumps(state,sort_keys=True)
                if signature not in indices:
                    indices[signature]=len(palette);palette.append(state)
                blocks.append({"pos":list(position),"state":indices[signature]})
            data={"DataVersion":4903,"author":"YumisFight",
                  "size":[max(p[i] for p in cells)+1 for i in range(3)],
                  "palette":palette,"blocks":blocks,"entities":[]}
            _,payload=nbt_payload(data)
            path=DATA / "structure/micro_decorations" / kind / (wood+".nbt")
            path.parent.mkdir(parents=True,exist_ok=True)
            path.write_bytes(gzip.compress(b"\x0a\x00\x00"+payload,mtime=0))


def texture(name):
    return name if ":" in name else "minecraft:block/" + name


def material(name):
    MATERIALS.add(texture(name))
    return texture(name).replace(":", "_").replace("/", "_")


def normal(points):
    a, b, c = points[:3]
    u, v = [b[i] - a[i] for i in range(3)], [c[i] - a[i] for i in range(3)]
    n = [u[1]*v[2]-u[2]*v[1], u[2]*v[0]-u[0]*v[2], u[0]*v[1]-u[1]*v[0]]
    length = math.sqrt(sum(x*x for x in n))
    return tuple(x / length for x in n)


@dataclass
class Mesh:
    faces: list = field(default_factory=list)
    bounds: list = field(default_factory=list)

    def face(self, points, tex):
        self.faces.append((tuple(tuple(p) for p in points), tex))

    def box(self, x0, y0, z0, x1, y1, z1, tex, end=None, angle=0, axis="z"):
        points = [(x0,y0,z0),(x1,y0,z0),(x1,y1,z0),(x0,y1,z0),
                  (x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)]
        if angle:
            center = [(x0+x1)/2,(y0+y1)/2,(z0+z1)/2]
            a, b = {"x": (1,2), "y": (0,2), "z": (0,1)}[axis]
            co, si = math.cos(math.radians(angle)), math.sin(math.radians(angle))
            transformed = []
            for point in points:
                p = list(point)
                u, v = p[a]-center[a], p[b]-center[b]
                p[a], p[b] = center[a]+u*co-v*si, center[b]+u*si+v*co
                transformed.append(tuple(p))
            points = transformed
        for indices, side in [((1,0,3,2),end or tex),((4,5,6,7),end or tex),
                              ((0,4,7,3),tex),((5,1,2,6),tex),
                              ((7,6,2,3),tex),((0,1,5,4),tex)]:
            self.face([points[i] for i in indices], side)
        self.bounds.append(tuple(min(p[i] for p in points) for i in range(3)) +
                           tuple(max(p[i] for p in points) for i in range(3)))
        return self

    def poly(self, vertices, faces, textures, collision=True):
        center = [sum(p[i] for p in vertices)/len(vertices) for i in range(3)]
        for index, indices in enumerate(faces):
            points = [vertices[i] for i in indices]
            n = normal(points)
            if sum(n[i]*(sum(p[i] for p in points)/len(points)-center[i]) for i in range(3)) < 0:
                points.reverse()
            self.face(points, textures[index % len(textures)])
        if collision:
            self.bounds.append(tuple(min(p[i] for p in vertices) for i in range(3)) +
                               tuple(max(p[i] for p in vertices) for i in range(3)))

    def pebble(self, x, z, rx, rz, height, tex="stone", seed=0):
        vertices = []
        sides = 7
        for level, y in [(0,.04),(1,height*.38),(2,height*.87)]:
            radius = [.70,1,.64][level]
            for i in range(sides):
                angle = (i+.13*seed)*math.tau/sides
                uneven = 1+.11*math.sin(i*3.1+seed)
                vertices.append((x+math.cos(angle)*rx*radius*uneven,y,z+math.sin(angle)*rz*radius*uneven))
        vertices += [(x-.2,height,z+.15),(x,.04,z)]
        faces = []
        for i in range(sides):
            j = (i+1) % sides
            faces += [(i,j,sides+j,sides+i),(sides+i,sides+j,2*sides+j,2*sides+i),
                      (2*sides+i,2*sides+j,3*sides),(j,i,3*sides+1)]
        self.poly(vertices,faces,[tex])
        return self

    def export(self, path):
        lines = [f"# Generated by tools/build_world_decorations.py; coordinates in blocks.",
                 f"mtllib {NS}:models/{MODEL}materials.mtl", "o decoration"]
        vertex_index = uv_index = normal_index = 1
        for points, tex in self.faces:
            lines.append("usemtl " + material(tex))
            n = normal(points)
            for p in points:
                lines.append("v " + " ".join(f"{v/16:.6f}" for v in p))
            # Project UVs onto the dominant plane at vanilla pixel density.
            axis = max(range(3),key=lambda i:abs(n[i]))
            u, v = [(2,1),(0,2),(0,1)][axis]
            for p in points:
                lines.append(f"vt {p[u]/16:.6f} {p[v]/16:.6f}")
            lines.append("vn " + " ".join(f"{v:.6f}" for v in n))
            refs = [f"{vertex_index+i}/{uv_index+i}/{normal_index}" for i in range(len(points))]
            lines.append("f " + " ".join(refs))
            vertex_index += len(points)
            uv_index += len(points)
            normal_index += 1
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text("\n".join(lines)+"\n",encoding="utf-8")

    def placed(self, x, z, yaw=0, roll=0):
        """Place an authored pose on its lowest point; selection boxes follow the pose."""
        co,si=math.cos(math.radians(roll)),math.sin(math.radians(roll))
        cy,sy=math.cos(math.radians(yaw)),math.sin(math.radians(yaw))
        def rotate(p):
            px,py=p[0]*co-p[1]*si,p[0]*si+p[1]*co
            return (px*cy-p[2]*sy,py,px*sy+p[2]*cy)
        floor=min(rotate(p)[1] for points,_ in self.faces for p in points)
        def transform(p):
            px,py,pz=rotate(p)
            return (px+x,py-floor+.04,pz+z)
        result=Mesh()
        for points,tex in self.faces:
            result.face([transform(p) for p in points],tex)
        for x0,y0,z0,x1,y1,z1 in self.bounds:
            points=[transform((a,b,c)) for a in [x0,x1] for b in [y0,y1] for c in [z0,z1]]
            result.bounds.append(tuple(min(p[i] for p in points) for i in range(3))+
                                 tuple(max(p[i] for p in points) for i in range(3)))
        return result


DISPLAY = {
    "gui": {"rotation": [30,225,0], "translation": [0,2,0], "scale": [.85,.85,.85]},
    "ground": {"translation": [0,3,0], "scale": [.5,.5,.5]},
    "fixed": {"rotation": [0,180,0], "scale": [.75,.75,.75]},
}


def loot(id, leaf=False):
    functions = []
    if id in RESOURCE_DROPS:
        functions.append({"function":"minecraft:set_count","count":1})
    if leaf:
        for amount in range(1,5):
            functions.append({"function":"minecraft:set_count","count":amount,
                              "conditions":[{"condition":"minecraft:block_state_property",
                                             "block":f"{NS}:{id}","properties":{"segment_amount":str(amount)}}]})
    functions.append({"function":"minecraft:explosion_decay"})
    write_json(DATA / "loot_table/blocks" / (id+".json"), {
        "type":"minecraft:block","pools":[{"rolls":1,"entries":[{
            "type":"minecraft:item","name":RESOURCE_DROPS.get(id,f"{NS}:{id}"),"functions":functions}]}]})


def add(id, zh, en, meshes, *, sound="WOOD", collides=True, floating=False, hardness=.6, food=None):
    if isinstance(meshes,Mesh):
        meshes = [meshes]
    form = "FLOATING" if floating else "SCATTERED" if len(meshes)>1 else "PROP"
    if form == "SCATTERED" and len(meshes) != SCATTER_LAYOUTS:
        raise ValueError(f"{id}: provide {SCATTER_LAYOUTS} scatter layouts, got {len(meshes)}")
    if len(meshes)>1:
        # A content item may acquire layouts; remove its superseded generated static model.
        for suffix in [".json", ".obj"]:
            (ASSETS / "models" / MODEL / (id+suffix)).unlink(missing_ok=True)
    DEFINITIONS.append(dict(id=id,zh=zh,en=en,form=form,sound=sound,collides=collides,tint=-1,
                            hardness=hardness,food=food,
                            shapes=[mesh.bounds for mesh in meshes]))
    names = []
    for i, mesh in enumerate(meshes):
        name = id + (f"_{i}" if len(meshes)>1 else "")
        names.append(name)
        mesh.export(ASSETS / "models" / MODEL / (name+".obj"))
        write_json(ASSETS / "models" / MODEL / (name+".json"), {
            "parent":"minecraft:block/block","loader":"neoforge:obj",
            "model":f"{NS}:models/{MODEL}{name}.obj","automatic_culling":False,"flip_v":True,
            "emissive_ambient":False,"textures":{"particle":texture(mesh.faces[0][1]),
                **{material(tex):texture(tex) for _,tex in mesh.faces}},"display":DISPLAY})
        GALLERY.append((name,mesh))
    variants = {}
    for facing, angle in [("north",0),("east",90),("south",180),("west",270)]:
        for i, name in enumerate(names):
            key = "facing="+facing
            if len(meshes)>1:
                key += ",variant="+str(i)
            if floating:
                for mode in ["false","true"]:
                    variants[key+",floating="+mode] = {"model":f"{NS}:{MODEL}{name}","y":angle}
            else:
                variants[key] = {"model":f"{NS}:{MODEL}{name}","y":angle}
    write_json(ASSETS / "blockstates" / (id+".json"),{"variants":variants})
    write_json(ASSETS / "items" / (id+".json"),{"model":{"type":"minecraft:model","model":f"{NS}:{MODEL}{names[0]}"}})
    loot(id)


def leaf(id, zh, en, tint):
    DEFINITIONS.append(dict(id=id,zh=zh,en=en,form="LEAVES",sound="LEAF_LITTER",collides=False,tint=tint,
                            hardness=.1,food=None,shapes=[[]]))
    variants = {}
    for layout in range(LEAF_LAYOUTS):
        for amount in range(1,5):
            elements = []
            # Flat transparent quadrants, as used by vanilla pink petals and leaf litter.
            # Each layout stays inside the native SegmentableBlock selection quadrants.
            for quadrant, (dx,dz) in enumerate([(0,0),(0,8),(8,8),(8,0)][:amount]):
                patches = [
                    [(0,0,8,8,.25)],
                    [(1,1,6,6,.25)],
                    [(0,0,8,5,.25)],
                    [(0,0,8,8,.25),(1.5,1.5,5,5,.29)],
                ][layout]
                for patch, (x,z,width,depth,y) in enumerate(patches):
                    u,v=[(0,0),(0,8),(8,8),(8,0)][(quadrant+layout+patch)%4]
                    rotation=((layout+patch)%4)*90
                    faces={
                        "up":{"uv":[u,v,u+8,v+8],"texture":"#leaf","tintindex":0,"rotation":rotation},
                        "down":{"uv":[u,v+8,u+8,v],"texture":"#leaf","tintindex":0,"rotation":rotation},
                    }
                    elements.append({"from":[x+dx,y,z+dz],"to":[x+dx+width,y,z+dz+depth],
                                     "faces":faces,"shade":False})
            name = id+(f"_layout_{layout}" if layout else "")+"_"+str(amount)
            write_json(ASSETS / "models" / MODEL / (name+".json"), {
                "parent":"minecraft:block/block","ambientocclusion":False,"render_type":"minecraft:cutout",
                "textures":{"leaf":texture("leaf_litter"),"particle":texture("leaf_litter")},"elements":elements,
                "display":{**DISPLAY,"gui":{"rotation":[35,225,0],"translation":[0,4,0],"scale":[1,1,1]}}})
            for facing, angle in [("north",0),("east",90),("south",180),("west",270)]:
                variants[f"facing={facing},layout={layout},segment_amount={amount}"] = {"model":f"{NS}:{MODEL}{name}","y":angle}
    write_json(ASSETS / "blockstates" / (id+".json"),{"variants":variants})
    write_json(ASSETS / "items" / (id+".json"),{"model":{"type":"minecraft:model","model":f"{NS}:{MODEL}{id}_4",
               "tints":[{"type":"minecraft:constant","value":tint}]}})
    loot(id,True)


def log(mesh,x,y,z,width=3,length=10):
    # Stepped cross section, bark on the length and pale cut end grain.
    mesh.box(x+.35,y,z,x+width-.35,y+width,z+length,"oak_log","oak_log_top")
    mesh.box(x,y+.5,z,x+width,y+width-.5,z+length,"oak_log","oak_log_top")


def band(mesh,x0,y,z0,x1,height,z1):
    tex = "stripped_oak_log"
    mesh.box(x0,y,z0,x1,y+.45,z0+.45,tex)
    mesh.box(x0,y,z1-.45,x1,y+.45,z1,tex)
    mesh.box(x0,y,z0,x0+.45,y+height,z1,tex)
    mesh.box(x1-.45,y,z0,x1,y+height,z1,tex)
    mesh.box(x0,y+height-.45,z0,x1,y+height,z1,tex)


def spear(mesh,x,z,height=13,bronze=False):
    mesh.box(x-.28,.5,z-.28,x+.28,height-2,z+.28,"spruce_log")
    metal = "oxidized_copper" if bronze else "iron_block"
    mesh.box(x-.75,height-3.2,z-.25,x+.75,height-1.7,z+.25,metal,angle=45)
    mesh.box(x-.5,height-3.7,z-.45,x+.5,height-3.1,z+.45,"stripped_oak_log")


def stone_layouts(tex, piled=False):
    # Each entry is center X/Z, footprint size and yaw. All loose stones are one pixel tall.
    layouts=[
        [(8,8,3.8,0)],
        [(4.5,6,3,0),(10.5,10,2.2,22.5)],
        [(3.5,4,2.5,-22.5),(11,6.5,3.1,15),(6.5,12,2,0)],
        [(3,5,2,0),(6,4,2.6,0),(10,5,2.2,0),(12.5,8,1.8,0)],
        [(4.5,5.5,4.2,22.5),(11,11,2.5,-22.5),(4.5,12,1.6,0)],
        [(3,3,2,0),(6,6,2.4,22.5),(9,9,2.1,0),(12,12,2.6,0)],
        [(4,6,3,0),(8,6,3,15),(11,6,2.5,-15),(6,10,3,0),(10,10,2.3,22.5)],
        [(4,4,2,22.5),(12,4,2.6,0),(4,12,2.4,0),(11.5,11.5,3,-22.5)],
    ]
    meshes=[]
    for index,layout in enumerate(layouts):
        mesh=Mesh()
        stones=layout+([(7,12.5,2.2,0),(10,2.5,2.2,-22.5)] if piled else [])
        for piece,(x,z,size,yaw) in enumerate(stones):
            depth=size*[.65,.85,.55,.75][(index+piece)%4]
            # Three joined strips give a low angular outline with small missing corners.
            prototype=Mesh()
            for strip,(width,offset) in enumerate([(.70,-.04),(1,0),(.82,.06)]):
                z0=-depth/2+strip*depth/3
                prototype.box(-size*width/2+size*offset,0,z0,
                              size*width/2+size*offset,1,z0+depth/3,tex)
            stone=prototype.placed(x,z,yaw)
            mesh.faces.extend(stone.faces)
            mesh.bounds.extend(stone.bounds)
        if piled:
            mesh.box(2,.04,12,5,.7,13,"spruce_planks",angle=12+index*3,axis="y")
        meshes.append(mesh)
    return meshes


def branch_layouts():
    # The native stick's 13-pixel diagonal uses a 0.5 ground-display scale.
    stick_length=13*math.sqrt(2)*.5
    stick_width=2*math.sqrt(2)*.5
    stick_height=.5
    layouts=[
        [(8,8,9,.7,0)],
        [(7,8,9,.7,-15),(9,6,4,.55,55)],
        [(8,8,8,.7,8)],
        [(8,8,9,.65,30),(8,8,8,.6,-35)],
        [(5,5,5,.6,10),(10,8,6,.75,-15),(6,12,4,.5,35)],
        [(6,8,7,.7,-25),(11,7,4,.6,20)],
        [(6,7,8,.65,10),(8,5,4,.5,65),(11,12,4,.55,-30)],
        [(6,4,7,.7,15),(9,7,8,.65,15),(6,10,7,.5,15),(10,13,4,.5,15)],
    ]
    meshes=[]
    for index,layout in enumerate(layouts):
        mesh=Mesh()
        for x,z,length,width,yaw in layout:
            length=length/9*stick_length
            width=width/.7*stick_width
            mesh.box(x-length/2,.04,z-width/2,x+length/2,.04+stick_height,z+width/2,
                     "oak_log" if index==2 else "spruce_log",angle=yaw,axis="y")
        if index==2:
            mesh.box(8-stick_length*7/18,.04+stick_height,7.6,
                     8+stick_length*7/18,.09+stick_height,8.4,"oak_log_top",angle=8,axis="y")
        meshes.append(mesh)
    return meshes


def cone_layouts():
    prototype=Mesh().box(-.4,0,-.4,.4,3.3,.4,"spruce_log")
    for layer in range(3):
        r=1.2-layer*.25
        prototype.box(-r,.25+layer*.8,-r,r,.9+layer*.8,r,"spruce_planks",angle=layer*25,axis="y")
    layouts=[
        [(8,8,0,0)],
        [(5,5,10,0),(11,10,-10,0)],
        [(4,5,0,0),(10,7,20,0),(6,12,-15,0)],
        [(7,8,45,90)],
        [(5,6,0,0),(10,10,-30,90)],
        [(5,6,45,90),(10,11,-30,90)],
        [(4,4,0,0),(11,5,20,0),(5,11,-15,0),(11,12,35,0)],
        [(4,5,20,0),(10,7,-30,90),(7,12,20,35)],
    ]
    meshes=[]
    for layout in layouts:
        mesh=Mesh()
        for x,z,yaw,roll in layout:
            cone=prototype.placed(x,z,yaw,roll)
            mesh.faces.extend(cone.faces)
            mesh.bounds.extend(cone.bounds)
        meshes.append(mesh)
    return meshes


def make_nature():
    for data in [
        ("oak","橡木","Oak",0xFFB4944D),
        ("birch","白桦","Birch",0xFFD7C35A),
        ("spruce","云杉","Spruce",0xFF74834C),
        ("jungle","丛林","Jungle",0xFF658752),
        ("acacia","金合欢","Acacia",0xFFBD9149),
        ("dark_oak","深色橡木","Dark Oak",0xFF957547),
        ("mangrove","红树","Mangrove",0xFF8D9B56),
        ("pale_oak","苍白橡木","Pale Oak",0xFFCCC5B4),
    ]:
        kind,zh,en,color = data
        leaf(kind+"_leaf_litter",zh+"落叶",en+" Leaf Litter",color)
    for id,zh,en,tex in [("gray_pebbles","灰色零散小石","Gray Pebbles","stone"),
                         ("dark_pebbles","深色零散小石","Dark Pebbles","cobbled_deepslate")]:
        add(id,zh,en,stone_layouts(tex),sound="STONE",collides=False,hardness=0)
    add("ground_branches","地面断枝","Fallen Branches",branch_layouts(),collides=False,hardness=0)
    add("pine_cones","散落松果","Pine Cones",cone_layouts(),collides=False,hardness=0,food=(2,0))
    mesh = Mesh()
    for x,y in [(3,0),(6,0),(9,0),(4.5,2.5),(7.5,2.5)]:
        log(mesh,x,y,3)
    band(mesh,2.8,.4,5,12.2,5.1,5.8)
    band(mesh,2.8,.4,10,12.2,5.1,10.8)
    add("firewood_bundle","捆扎柴薪","Bound Firewood",mesh)
    mesh = Mesh()
    for x,y in [(2.5,0),(5.7,0),(8.9,0),(4.1,2.6),(7.3,2.6),(5.7,5.2)]:
        log(mesh,x,y,2.5,length=11)
    add("short_log_stack","短原木堆","Short Log Stack",mesh)
    for rope in [False,True]:
        mesh = Mesh().box(6,0,6,10,10,10,"spruce_log")
        mesh.box(6.4,10,6.2,9.2,12,9.6,"spruce_log_top")
        mesh.box(8.2,10,7,9.5,13,9,"spruce_planks")
        if rope:
            band(mesh,5.7,6,5.7,10.3,1.1,10.3)
            band(mesh,5.7,8,5.7,10.3,.8,10.3)
        add("rope_waypost" if rope else "old_waypost","绑绳旧木桩" if rope else "旧木路桩",
            "Rope-bound Waypost" if rope else "Old Waypost",mesh)


def make_goblin():
    add("goblin_sling_stones","哥布林投石料堆","Goblin Sling Stone Pile",stone_layouts("cobblestone",True),sound="STONE",collides=False)
    mesh = Mesh().box(3,.05,4,12,.55,12,"brown_wool")
    for x,z in [(2,5),(1,8),(4,12),(9,3),(12,6),(12,10)]:
        mesh.box(x,.05,z,x+2,.5,z+2,"brown_wool")
    mesh.box(4,.55,5,5,.65,11,"white_wool")
    mesh.box(9,.55,5,10,.65,11,"white_wool")
    mesh.box(6,.55,6,8,.65,9,"black_wool")
    add("goblin_hide_mat","哥布林兽皮坐垫","Goblin Hide Mat",mesh,sound="WOOL",collides=False)
    mesh = Mesh().box(6,0,7,8,12,9,"spruce_log")
    mesh.box(3,7,6,12,11,7,"oak_planks",angle=-10)
    mesh.box(4,4.5,6,11,7,7,"spruce_planks",angle=8)
    for x,y in [(4,8),(8,8),(9,6)]:
        mesh.box(x,y,5.8,x+.6,y+1.8,6,"red_terracotta",angle=-20)
    add("goblin_tribal_sign","哥布林部落木牌","Goblin Tribal Sign",mesh)
    mesh = Mesh()
    for x in [3,12]:
        mesh.box(x,0,5,x+1.1,10,9,"spruce_log")
        mesh.box(x-1,0,4,x+2,1,11,"spruce_planks")
    mesh.box(3,7,7,13,8,8,"oak_planks")
    for x,z,h in [(5,6,12),(8,7,14),(11,6,11)]:
        spear(mesh,x,z,h)
    add("goblin_weapon_rack","哥布林粗木武器架","Goblin Rough Weapon Rack",mesh)


def make_dragon():
    mesh = Mesh()
    for x,z,yaw in [(4,5,10),(10,8,35),(5,11,-20)]:
        co,si=math.cos(math.radians(yaw)),math.sin(math.radians(yaw))
        ring=[(-1.5,0),(0,-2.1),(1.5,0),(0,2.1)]
        vertices=[(x+u*co-v*si,.17,z+u*si+v*co) for u,v in ring]
        vertices += [(x,.8,z),(x,.04,z)]
        faces=[(4,i,(i+1)%4) for i in range(4)]+[(5,(i+1)%4,i) for i in range(4)]
        mesh.poly(vertices,faces,["cyan_concrete","light_blue_concrete"])
    add("dragon_scale_scatter","青蓝龙鳞簇","Scattered Azure Dragon Scales",mesh,sound="STONE",collides=False)
    mesh = Mesh().pebble(8,8,5.8,5.8,1.4,"smooth_stone")
    for box in [(5,1.45,7,11,1.65,8),(8,1.45,5,9,1.65,10),(6,1.45,4,10,1.65,5),
                (5,1.45,8,6,1.65,11),(5,1.45,10,8,1.65,11)]:
        mesh.box(*box,"oxidized_copper")
    add("dragon_rune_disc","龙纹石盘","Dragon Rune Stone Disc",mesh,sound="STONE",collides=False)
    mesh = Mesh()
    for x in [3,12]:
        mesh.box(x,0,5,x+1,10,9,"weathered_copper")
        mesh.box(x-1,0,4,x+2,1,11,"cut_copper")
    mesh.box(3,7,7,13,8,8,"oxidized_copper")
    for x,z,h in [(5,6,13),(8,7,14),(11,6,13)]:
        spear(mesh,x,z,h,True)
    add("dragon_bronze_spear_rack","青铜长矛架","Bronze Spear Rack",mesh,sound="METAL")
    add("dragon_white_stone_chips","塔楼白石碎块","Tower White Stone Chips",stone_layouts("diorite"),sound="STONE",collides=False)


def make_palace():
    mesh = Mesh().box(3,0,3.5,13,.35,9,"cyan_wool")
    mesh.box(3,.35,3.5,3.5,.5,9,"yellow_wool")
    mesh.box(12.5,.35,3.5,13,.5,9,"yellow_wool")
    # Faceted horizontal roll with two narrow woven gold rims.
    for left,right,radius,gold in [(3,3.5,2.1,True),(3.5,12.5,2,False),(12.5,13,2.1,True)]:
        vertices=[]
        for x in [left,right]:
            vertices.extend((x,2.1+math.cos(i*math.tau/8)*radius,9+math.sin(i*math.tau/8)*radius) for i in range(8))
        vertices += [(left,2.1,9),(right,2.1,9)]
        faces=[(i,(i+1)%8,8+(i+1)%8,8+i) for i in range(8)]
        faces += [(16,(i+1)%8,i) for i in range(8)]+[(17,8+i,8+(i+1)%8) for i in range(8)]
        mesh.poly(vertices,faces,["yellow_wool"] if gold else ["cyan_wool","light_blue_wool","blue_wool"])
    add("palace_rolled_carpet","宫殿金边卷毯","Palace Gold-edged Rolled Carpet",mesh,sound="WOOL")
    mesh = Mesh().box(5,0,6,11,1,11,"polished_andesite")
    mesh.box(7,1,8,9,6,10,"iron_block")
    mesh.box(4,4,6,12,12,7,"gold_block")
    mesh.box(5,3,6,11,4,7,"gold_block")
    mesh.box(5,12,6,11,13,7,"gold_block")
    mesh.box(5,5,5.7,11,11,6,"cyan_terracotta")
    mesh.box(7.3,5,5.4,8.7,11,5.7,"gold_block")
    mesh.box(6,7.4,5.4,10,8.6,5.7,"gold_block")
    add("palace_ceremonial_shield","宫殿仪仗盾","Palace Ceremonial Shield",mesh,sound="METAL")
    mesh = Mesh().box(4,0,4,12,1,12,"quartz_block_bottom")
    mesh.box(4.5,1,4.5,11.5,2,11.5,"gold_block")
    mesh.box(5.5,2,5.5,10.5,8,10.5,"quartz_pillar_side")
    mesh.box(4.7,8,4.7,11.3,9,11.3,"gold_block")
    mesh.box(4,9,4,12,10,12,"quartz_block_top")
    mesh.box(7,10,7,9,11,9,"cyan_terracotta")
    add("palace_ornamental_column","宫殿白金矮饰柱","Palace White-gold Ornament Column",mesh,sound="STONE")
    mesh = Mesh().box(4,0,4,12,1,12,"gold_block")
    mesh.box(5,1,5,11,3,11,"quartz_block_side")
    vertices = [(8,12,8),(5.5,5,6),(10.5,5,6),(11,5,9),(8,4,11),(5,5,9),(8,3,8)]
    faces = [(0,1,2),(0,2,3),(0,3,4),(0,4,5),(0,5,1),(6,2,1),(6,3,2),(6,4,3),(6,5,4),(6,1,5)]
    mesh.poly(vertices,faces,["light_blue_concrete","cyan_concrete","light_blue_concrete"])
    add("palace_crystal_stand","宫殿青蓝晶石摆台","Palace Azure Crystal Stand",mesh,sound="STONE")
    # Asymmetric pointed rubble, with a chipped seven-sided belt rather than a perfect crystal.
    vertices = [(6.1,14.2,8.8),(9.1,0,6.3),(2.8,6.8,6.1),(4.5,8.6,3.4),
                (9.5,7.4,3.1),(12.2,5.9,6.2),(10.7,6.7,11.3),(6.5,7.9,12.1),(3.4,5.7,9.8)]
    faces = []
    for i in range(7):
        a,b = 2+i,2+(i+1)%7
        faces.extend([(0,a,b),(1,b,a)])
    mesh = Mesh()
    mesh.poly(vertices,faces,["andesite","polished_andesite","diorite","andesite","smooth_stone"],False)
    # Slice the same hull to keep its physical profile pointed as well.
    edges = {(min(a,b),max(a,b)) for face in faces for a,b in zip(face,face[1:]+face[:1])}
    levels = [0,2.2,4.5,6.8,9,11.5,14.2]
    for lo,hi in zip(levels,levels[1:]):
        points = [v for v in vertices if lo <= v[1] <= hi]
        for a,b in edges:
            p,q = vertices[a],vertices[b]
            for y in [lo,hi]:
                if p[1] != q[1] and min(p[1],q[1]) <= y <= max(p[1],q[1]):
                    t = (y-p[1])/(q[1]-p[1])
                    points.append(tuple(p[i]+t*(q[i]-p[i]) for i in range(3)))
        mesh.bounds.append((min(p[0] for p in points),lo,min(p[2] for p in points),
                            max(p[0] for p in points),hi,max(p[2] for p in points)))
    add("palace_floating_shard","宫殿悬空碎石","Palace Floating Stone Shard",mesh,sound="STONE",floating=True)


def write_definitions():
    lines = ["package cn.yumis.fight.modules.mapgeneration.platform;", "",
             "import net.minecraft.world.level.block.Block;", "import net.minecraft.world.level.block.SoundType;",
             "import net.minecraft.world.food.FoodProperties;",
             "import net.minecraft.world.phys.shapes.*;", "import java.util.*;", "",
             "/** Generated geometry and content: edit tools/build_world_decorations.py. */",
             "public final class DecorationDefinitions {",
             f"    public static final int LEAF_LAYOUTS={LEAF_LAYOUTS},SCATTER_LAYOUTS={SCATTER_LAYOUTS};",
             "    public enum Form { LEAVES, SCATTERED, PROP, FLOATING }",
             "    public record Entry(String id,Form form,SoundType sound,boolean collides,int tint,float hardness,FoodProperties food,List<VoxelShape> shapes){}",
             "    public static final List<Entry> ENTRIES=List.of("]
    for index,entry in enumerate(DEFINITIONS):
        food=entry["food"]
        food_java=(f"new FoodProperties.Builder().nutrition({food[0]}).saturationModifier({float(food[1])}f).build()"
                   if food is not None else "null")
        shapes = []
        for boxes in entry["shapes"]:
            cubes = ["Block.box("+",".join(f"{n:.4f}" for n in bounds)+")" for bounds in boxes]
            shapes.append("shape("+",".join(cubes)+")")
        lines.append(f'        new Entry("{entry["id"]}",Form.{entry["form"]},SoundType.{entry["sound"]},'
                     f'{str(entry["collides"]).lower()},0x{entry["tint"] & 0xffffffff:08x},'
                     f'{float(entry["hardness"])}f,{food_java},List.of('+",".join(shapes)+"))"+
                     ("," if index+1<len(DEFINITIONS) else ""))
    lines += ["    );", "    private static final Map<String,Entry> BY_ID;", "    static {",
              "        Map<String,Entry> entries=new LinkedHashMap<>();",
              "        for(var entry:ENTRIES)entries.put(entry.id(),entry);",
              "        BY_ID=Map.copyOf(entries);", "    }", "    private DecorationDefinitions(){}",
              "    public static Entry find(String id){return Objects.requireNonNull(BY_ID.get(id),id);}",
              "    private static VoxelShape shape(VoxelShape... parts){return Shapes.or(Shapes.empty(),parts);}", "}"]
    path = ROOT / "src/main/java/cn/yumis/fight/modules/mapgeneration/platform/DecorationDefinitions.java"
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text("\n".join(lines)+"\n",encoding="utf-8")


def write_materials_and_names():
    lines = ["# Vanilla textures; shared by the generated OBJ models."]
    for tex in sorted(MATERIALS):
        slot = tex.replace(":","_").replace("/","_")
        lines += ["newmtl "+slot,"Kd 1 1 1","map_Kd #"+slot,""]
    (ASSETS / "models" / MODEL / "materials.mtl").write_text("\n".join(lines),encoding="utf-8")
    for language,field_name,title in [("zh_cn","zh","AYD-世界装饰"),("en_us","en","AYD - World Decorations")]:
        path = ASSETS / "lang" / (language+".json")
        names = json.loads(path.read_text(encoding="utf-8-sig"))
        names[f"itemGroup.{NS}.world_decorations"] = title
        for entry in DEFINITIONS:
            names[f'block.{NS}.{entry["id"]}'] = entry[field_name]
            if entry["food"] is not None:
                names[f'item.{NS}.{entry["id"]}.usage'] = ("右键食用；潜行右键摆放。" if language=="zh_cn" else
                                                            "Eat with use; sneak-use to place.")
        names[f"item.{NS}.pebble"] = "石子" if language=="zh_cn" else "Pebble"
        names[f"item.{NS}.pebble.usage"] = ("右键摆放灰色小石；潜行右键摆放深色小石。" if language=="zh_cn" else
                                              "Place gray stones; sneak-use to place dark stones.")
        write_json(path,names)


def write_resource_cycle_and_worldgen():
    # Both placed colors resolve to the same resource item; no duplicate material items.
    for id in ["gray_pebbles","dark_pebbles"]:
        (ASSETS / "items" / (id+".json")).unlink(missing_ok=True)
    write_json(ASSETS / "items/pebble.json",{"model":{"type":"minecraft:model","model":f"{NS}:{MODEL}gray_pebbles_0"}})
    write_json(DATA / "recipe/cobblestone_from_pebbles.json",{
        "type":"minecraft:crafting_shaped","category":"building","pattern":["PP","PP"],
        "key":{"P":f"{NS}:pebble"},"result":{"id":"minecraft:cobblestone","count":1}})
    write_json(DATA / "recipe/pebbles_from_cobblestone.json",{
        "type":"minecraft:stonecutting","ingredient":"minecraft:cobblestone","result":{"id":f"{NS}:pebble","count":4}})
    write_json(DATA / "advancement/recipes/building_blocks/pebble_cycle.json",{
        "parent":"minecraft:recipes/root",
        "criteria":{
            "has_pebble":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":f"{NS}:pebble"}]}},
            "has_cobblestone":{"trigger":"minecraft:inventory_changed","conditions":{"items":[{"items":"minecraft:cobblestone"}]}}
        },"requirements":[["has_pebble","has_cobblestone"]],
        "rewards":{"recipes":[f"{NS}:cobblestone_from_pebbles",f"{NS}:pebbles_from_cobblestone"]}})
    write_json(DATA / "worldgen/configured_feature/world_decorations.json",{
        "type":f"{NS}:world_decorations","config":{
            "leaf_clusters_min":1,"leaf_clusters_max":3,"leaf_patch_min":3,"leaf_patch_max":6,
            "loose_min":3,"loose_max":6,"road_chunk_rarity":3,"camp_chunk_rarity":64,"camp_props_min":3,"camp_props_max":5,
            "mini_structures":[{
                "templates":{wood:f"{NS}:micro_decorations/{kind}/{wood}" for wood in NATIVE_WOODS},
                "chunk_rarity":rarity,"forest_only":forest,"prefer_roadside":roads,
                "replaces":["firewood_bundle","short_log_stack"] if kind=="log_bundle" else []
            } for kind,rarity,forest,roads in MINI_STRUCTURES]}})
    write_json(DATA / "worldgen/placed_feature/world_decorations.json",{
        "feature":f"{NS}:world_decorations","placement":[{"type":"minecraft:biome"}]})
    write_json(DATA / "neoforge/biome_modifier/world_decorations.json",{
        "type":"neoforge:add_features","biomes":"#minecraft:is_overworld",
        "features":[f"{NS}:world_decorations"],"step":"top_layer_modification"})


if __name__ == "__main__":
    make_nature()
    make_goblin()
    make_dragon()
    make_palace()
    write_definitions()
    write_materials_and_names()
    write_resource_cycle_and_worldgen()
    write_mini_structures()
    print(f"Authored {len(DEFINITIONS)} decorations, {len(GALLERY)} OBJ layouts, resource recipes and new-chunk world generation.")

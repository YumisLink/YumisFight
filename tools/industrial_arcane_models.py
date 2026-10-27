"""Arcane device geometry, exposed-face cleanup, and the shared animated rotor meshes."""
from pathlib import Path
from math import cos,sin,radians
import json,copy
from industrial_visual_models import ROOT,ASSETS,NS,FAMILIES,write
SOURCE=ROOT/'art/industry-arcane'
UV={'stone':(0,0),'gold':(16,0),'gem':(0,16),'rune':(16,16)}


def subtract(rect,cover):
    a,b,c,d=rect;x,y,X,Y=cover
    x,y,X,Y=max(a,x),max(b,y),min(c,X),min(d,Y)
    if x>=X or y>=Y:return [rect]
    return [r for r in [(a,b,x,d),(X,b,c,d),(x,b,X,y),(x,Y,X,d)] if r[0]<r[2] and r[1]<r[3]]


def exposed(boxes):
    """Clip hidden and coplanar overlaps; each visible surface is emitted only once."""
    faces={'west':(0,0,2,1),'east':(0,1,2,1),'down':(1,0,0,2),'up':(1,1,0,2),'north':(2,0,0,1),'south':(2,1,0,1)}
    result=[]
    for i,(lo,hi,mat) in enumerate(boxes):
        for face,(axis,positive,u,v) in faces.items():
            plane=(hi if positive else lo)[axis];rect=(lo[u],lo[v],hi[u],hi[v]);pieces=[rect]
            for j,(L,H,_) in enumerate(boxes):
                if i==j:continue
                # Remove an internal face, or give one owner to an outer coplanar overlap.
                beyond=(L[axis]<=plane<H[axis]) if positive else (L[axis]<plane<=H[axis])
                same=abs((H if positive else L)[axis]-plane)<1e-7 and j<i
                if not (beyond or same):continue
                pieces=[p for r in pieces for p in subtract(r,(L[u],L[v],H[u],H[v]))]
                if not pieces:break
            for a,b,c,d in pieces:
                low=list(lo);high=list(hi);low[axis]=high[axis]=plane;low[u]=a;high[u]=c;low[v]=b;high[v]=d
                U,V=UV[mat]
                # Physical density is one texel per model unit on the 32x32 atlas.
                uv=[(U+a-lo[u])/2,(V+b-lo[v])/2,(U+c-lo[u])/2,(V+d-lo[v])/2]
                result.append({'from':low,'to':high,'faces':{face:{'texture':'#atlas','uv':uv}}})
    return result


def shape(family):
    boxes=[]
    def b(lo,hi,m='stone'):boxes.append((list(lo),list(hi),m))
    def base(w=14):
        a=(16-w)/2;b((a,0,a),(16-a,2,16-a));b((a+1,2,a+1),(15-a,3,15-a),'gold')
    def posts(y,h=2):
        for x,z in [(2,2),(12,2),(2,12),(12,12)]:b((x,3,z),(x+h,y,z+h),'gold')
    if family=='mana_crusher':
        base();b((3,3,5),(5,12,11),'gold');b((11,3,5),(13,12,11),'gold')
        b((5,11,5),(11,13,11));b((6,8,6),(10,10,10),'gem');b((5,4,5),(11,5,11),'rune')
        b((1,9,6),(3,11,10));b((13,9,6),(15,11,10));b((7,13,7),(9,15,9),'gold')
    elif family=='mana_furnace':
        base(12);b((4,3,4),(12,5,12));b((2,5,2),(14,6,14),'gold')
        for x,z in [(2,2),(11,2),(2,11),(11,11)]:b((x,6,z),(x+3,10,z+3),'gold')
        b((5,6,5),(11,9,11),'gem');b((6,9,6),(10,12,10),'gem');b((7,12,7),(9,14,9),'gem')
    elif family=='mineral_generator':
        base();posts(12);b((2,12,2),(14,14,14));b((3,11,3),(13,12,13),'gold')
        b((6,5,6),(10,10,10),'gem');b((5,3,5),(11,4,11),'rune');b((6,14,6),(10,16,10),'gold')
    elif family in ('currency_fabricator','equipment_fabricator'):
        base();b((5,3,5),(11,8,11));b((4,6,4),(12,7,12),'gold');b((2,8,2),(14,10,14))
        b((3,10,3),(13,11,13),'gold');b((4,11,4),(12,12,12),'rune')
        if family=='currency_fabricator':b((6,14,6),(10,16,10),'gem');b((4,13,7),(5,15,9),'gold');b((11,13,7),(12,15,9),'gold')
        else:b((2,12,10),(4,15,13),'gold');b((12,12,10),(14,15,13),'gold')
    elif family=='mana_forge':
        b((0,0,0),(16,16,16));b((0,0,0),(16,2,16),'gold');b((0,14,0),(16,16,16),'gold')
        for x,z in [(0,0),(14,0),(0,14),(14,14)]:b((x,2,z),(x+2,14,z+2),'gold')
        b((4,4,-.15),(12,12,0),'rune');b((4,16,4),(12,16.15,12),'rune')
    elif family=='mana_beacon':
        base();b((5,3,5),(11,5,11),'rune')
        for x,z in [(1,1),(13,1),(1,13),(13,13)]:b((x,3,z),(x+2,7,z+2),'gold');b((x,7,z),(x+2,9,z+2),'stone')
    elif family=='coal_generator':
        base(14);b((3,3,3),(13,5,13));b((2,5,2),(14,7,14),'gold')
        for x,z in [(2,2),(11,2),(2,11),(11,11)]:b((x,7,z),(x+3,11,z+3),'gold')
        b((5,7,5),(11,9,11),'gem');b((6,9,6),(10,11,10),'gem');b((7,11,7),(9,13,9),'gem');b((6,4,2),(10,6,3),'rune')
    elif family=='solar_collector':
        base(10);b((6,3,6),(10,7,10),'gold');b((2,7,2),(14,8,14),'gold');b((3,8,3),(13,9,13),'rune')
        for x,z in [(1,1),(13,1),(1,13),(13,13)]:b((x,8,z),(x+2,10,z+2),'gold')
        b((6,11,6),(10,14,10),'gem');b((7,14,7),(9,15,9),'gem')
    elif family=='turbine_generator':
        base();b((6,3,7),(10,6,12),'gold');b((4,6,10),(12,12,13));b((6,6,6),(10,10,10),'gold')
        for x,y in [(1,4),(13,4),(1,11),(13,11)]:b((x,y,5),(x+2,y+2,8),'gold')
        b((3,13,5),(13,15,9));b((4,12,5),(12,13,9),'gold')
    elif family=='wind_generator':
        base();b((5,3,5),(11,6,11));b((4,6,4),(12,7,12),'gold');b((6,7,6),(10,16,10))
        b((6,8,5.8),(10,12,6),'rune')
    elif family=='thermal_generator':
        base()
        for x in [2,10]:b((x,3,4),(x+4,5,12),'gold');b((x,5,5),(x+4,11,11));b((x-1,11,4),(x+5,12,12),'gold')
        b((3,12,6),(5,15,10),'gem');b((11,12,6),(13,15,10),'gold');b((6,7,6),(10,9,10),'rune')
    elif family=='nuclear_reactor':
        base();posts(12);b((4,12,4),(12,14,12),'gold');b((5,5,5),(11,11,11),'gem')
        b((3,6,3),(5,10,5));b((11,6,3),(13,10,5));b((3,6,11),(5,10,13));b((11,6,11),(13,10,13))
        b((6,14,6),(10,16,10),'rune')
    elif family=='rtg_generator':
        base(12);b((5,3,5),(11,13,11),'gem')
        for y in [3,6,9,12]:b((2,y,2),(14,y+1,14),'gold')
        b((4,13,4),(12,15,12));b((6,15,6),(10,16,10),'gold')
    elif family=='coolant_mixer':
        base()
        for x in [2,10]:b((x,4,5),(x+4,12,11),'gem');b((x-1,3,4),(x+5,4,12),'gold');b((x-1,12,4),(x+5,13,12),'gold')
        b((6,6,7),(10,8,9),'gold');b((6,10,6),(10,11,10),'rune')
    elif family=='transfer_crystal':
        base();b((2,3,6),(4,12,10),'gold');b((12,3,6),(14,12,10),'gold');b((4,11,6),(12,13,10))
        b((3,13,7),(6,14,9),'gold');b((10,13,7),(13,14,9),'gold')
    elif family=='harvest_crystal':
        base(12);b((5,3,5),(11,4,11),'rune')
        for x,z in [(1,5),(12,5),(6,12)]:b((x,3,z),(x+3,5,z+3),'gold');b((x+1,5,z+1),(x+2,8,z+2),'gem')
    elif family=='wind_middle':
        b((6,0,6),(10,16,10));b((5,2,5),(11,3,11),'gold');b((5,12,5),(11,13,11),'gold');b((6,6,5.8),(10,10,6),'rune')
    elif family=='wind_upper':
        b((6,0,6),(10,11,10));b((5,5,5),(11,7,11),'gold');b((6,6,3),(10,9,7),'gold')
        b((4,11,4),(12,13,12));b((5,13,5),(11,14,11),'gold');b((7,14,7),(9,16,9),'gem')
    return boxes


def model(family,tier,state='',boxes=None):
    texture=f'arcane/{family}_{tier}{state}'
    if family.startswith('wind_') and family not in FAMILIES:texture=f'arcane/wind_generator_2{state}'
    parts=shape(family) if boxes is None else boxes
    if boxes is None and family in FAMILIES and not any(mat in ('gem','rune') for _,_,mat in parts):
        parts.append(([6,2.9,.9],[10,4,2.9],'gem'))
    if boxes is None and family in FAMILIES[:6] and tier>1:
        for i in range(min(tier,4)):
            x=3+i*3;parts.append(([x,2.5,.7],[x+2,4,2.7],'gem'))
    elements=exposed(parts)
    if state=='_active':
        for e in elements:
            uv=next(iter(e['faces'].values()))['uv']
            if min(uv[1],uv[3])>=8:
                e['light_emission']=12;e['shade']=False
    return {'ambientocclusion':True,'textures':{'atlas':f'{NS}:block/{texture}','particle':f'{NS}:block/{texture}'},
            'elements':elements,
            'display':{'gui':{'rotation':[28,225,0],'scale':[.8,.8,.8]},
                       'ground':{'translation':[0,3,0],'scale':[.3,.3,.3]},
                       'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.375,.375,.375]},
                       'firstperson_righthand':{'rotation':[0,45,0],'scale':[.4,.4,.4]}}}


def quad_points(e,face):
    x,y,z=e['from'];X,Y,Z=e['to']
    return {'north':[(x,y,z),(x,Y,z),(X,Y,z),(X,y,z)],'south':[(X,y,Z),(X,Y,Z),(x,Y,Z),(x,y,Z)],
       'east':[(X,y,z),(X,Y,z),(X,Y,Z),(X,y,Z)],'west':[(x,y,Z),(x,Y,Z),(x,Y,z),(x,y,z)],
       'up':[(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],'down':[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]}[face]


def to_mesh(elements):
    d={'vertices':[],'faces':[],'uvs':[]}
    for e in elements:
        for face,f in e['faces'].items():
            offset=len(d['vertices']);d['vertices'] += [[v/16 for v in p] for p in quad_points(e,face)];d['faces'].append(list(range(offset,offset+4)))
            u,v,U,V=f['uv'];d['uvs'].append([[u/16,1-V/16],[u/16,1-v/16],[U/16,1-v/16],[U/16,1-V/16]])
    return d


def rotor(wind):
    boxes=[([-1.5,-1.5,-1.2],[1.5,1.5,1.2],'gold')]
    d=to_mesh(exposed(boxes))
    for i in range(3 if wind else 6):
        a=radians(i*(120 if wind else 60));C,S=cos(a),sin(a)
        boxes=[([-.5,1.5,-.4],[.5,5.5,.4],'gold'),([.5,2.5,-.35],[2.5,6 if wind else 5,.35],'gold'),([.75,3,-.42],[2,5,.42],'gem')]
        if wind:
            # Extend before UV mapping to retain 16 texels/block on the larger blades.
            boxes=[([lo[0]*1.8,1.5+(lo[1]-1.5)*3.3,lo[2]],[hi[0]*1.8,1.5+(hi[1]-1.5)*3.3,hi[2]],mat) for lo,hi,mat in boxes]
        blade=to_mesh(exposed(boxes))
        start=len(d['vertices']);d['vertices'] += [[x*C-y*S,x*S+y*C,z] for x,y,z in blade['vertices']]
        d['faces'] += [[v+start for v in f] for f in blade['faces']];d['uvs']+=blade['uvs']
    return d


def export_mesh(name,d):
    folder=ASSETS/'models/industry';lines=[f'mtllib {name}.mtl',f'o {name}']
    lines += ['v '+' '.join(f'{v:.6f}' for v in p) for p in d['vertices']]
    lines += [f'vt {u:.6f} {v:.6f}' for face in d['uvs'] for u,v in face]
    offset=1
    for index,face in enumerate(d['faces']):
        lines.append('usemtl '+d.get('materials',['atlas']*len(d['faces']))[index]);lines.append('f '+' '.join(f'{v+1}/{offset+i}' for i,v in enumerate(face)));offset+=len(face)
    (folder/f'{name}.obj').write_text('\n'.join(lines)+'\n');(folder/f'{name}.mtl').write_text('newmtl atlas\nKd 1 1 1\nmap_Kd #atlas\nnewmtl crystal\nKd 1 1 1\nmap_Kd #crystal\n')


def generate():
    for family in FAMILIES:
        for tier in range(1,6):
            for state in ['','_linked','_active']:write(ASSETS/f'models/block/{family}_{tier}{state}.json',model(family,tier,state))
    write(ASSETS/'models/block/equipment_fabricator.json',model('equipment_fabricator',1))
    for part,name in [(1,'wind_middle'),(2,'wind_upper')]:write(ASSETS/f'models/block/{name}.json',model(name,2))
    write(ASSETS/'blockstates/wind_generator_part.json',{'variants':{f'part={part},facing={f}':{'model':f'{NS}:block/{name}','y':angle} for part,name in [(1,'wind_middle'),(2,'wind_upper')] for f,angle in [('north',0),('east',90),('south',180),('west',270)]}})
    tag=ROOT/'src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json'
    values=json.loads(tag.read_text());key=f'{NS}:wind_generator_part'
    if key not in values['values']:values['values'].append(key);write(tag,values)
    meshes=json.loads((ROOT/'art/industry-refresh/meshes.json').read_text())
    # Shared rotor definition: preview, item and runtime Java vertices use exactly the same quads.
    generated={True:rotor(True),False:rotor(False)}
    java='''// Generated by tools/industrial_arcane_models.py from the shared rotor geometry.
package cn.yumis.fight.client;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
final class ArcaneRotorMesh {
    static void submit(boolean wind,PoseStack pose,SubmitNodeCollector nodes,int light){
        float[][] data=wind?WIND:TURBINE;
        nodes.submitCustomGeometry(pose,RenderTypes.entityCutout(Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/block/arcane/rotor.png")),(matrix,vertices)->{
            for(float[] v:data)vertices.addVertex(matrix,v[0],v[1],v[2]).setColor(255,255,255,255).setUv(v[3],v[4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,v[5],v[6],v[7]);
        });
    }
'''
    for is_wind,name in [(True,'WIND'),(False,'TURBINE')]:
        d=generated[is_wind];rows=[]
        for face,uvs in zip(d['faces'],d['uvs']):
            a,b,c=[d['vertices'][i] for i in face[:3]];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
            n=[u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]];length=sum(q*q for q in n)**.5;n=[q/length for q in n]
            # OBJ/Blender UVs start at the bottom; entity rendering starts at the top.
            rows += [d['vertices'][i]+[uv[0],1-uv[1]]+n for i,uv in zip(face,uvs)]
        java+='    private static final float[][] '+name+'={\n'+',\n'.join('        {'+','.join(f'{x:.6f}f' for x in row)+'}' for row in rows)+'\n    };\n'
    java+='}\n';(ROOT/'src/client/java/cn/yumis/fight/client/ArcaneRotorMesh.java').write_text(java)
    # Static full-height item representation; placed rotor is emitted by the BER only.
    wind=to_mesh(model('wind_generator',2)['elements'])
    for name,height in [('wind_middle',1),('wind_upper',2)]:
        d=to_mesh(model(name,2)['elements']);start=len(wind['vertices']);wind['vertices'] += [[x,y+height,z] for x,y,z in d['vertices']];wind['faces'] += [[v+start for v in f] for f in d['faces']];wind['uvs']+=d['uvs']
    for family,is_wind,tier in [('wind_generator',True,2),('turbine_generator',False,2)]:
        data=copy.deepcopy(wind) if is_wind else to_mesh(model(family,tier)['elements']);r=generated[is_wind];start=len(data['vertices']);data['vertices'] += [[x+.5,y+(2.45 if is_wind else .57),z+.19] for x,y,z in r['vertices']];data['faces'] += [[i+start for i in f] for f in r['faces']];data['uvs']+=r['uvs']
        name=family+'_arcane';export_mesh(name,data);meshes[name]=data
        display=model(family,tier)['display']
        if is_wind:display['gui']={'rotation':[18,225,0],'translation':[0,-5.8,0],'scale':[.31,.31,.31]};display['ground']={'translation':[0,3,0],'scale':[.14,.14,.14]}
        write(ASSETS/f'models/item/{family}.json',{'loader':'neoforge:obj','model':f'{NS}:models/industry/{name}.obj','automatic_culling':False,'textures':{'atlas':f'{NS}:block/arcane/{family}_{tier}','particle':f'{NS}:block/arcane/{family}_{tier}'},'display':display})
    # Static crystal items match their new plinths, with the existing true crystal mesh retained.
    for family in ['mana_beacon','transfer_crystal','harvest_crystal']:
        for tier in range(1,6 if family=='mana_beacon' else 4):
            name=f'{family}_t{tier}';base_mesh=to_mesh(model(family,tier)['elements']);gem=copy.deepcopy(meshes[name])
            base_mesh['materials']=['atlas']*len(base_mesh['faces'])
            for face,band in zip(gem['faces'],gem['bands']):
                if band<0:continue
                start=len(base_mesh['vertices']);base_mesh['vertices'] += [gem['vertices'][i] for i in face];base_mesh['faces'].append(list(range(start,start+len(face))))
                u,U=(band+.18)/8,(band+.82)/8;base_mesh['uvs'].append([[u,.1],[u,.9],[U,.9],[U,.1]] if len(face)==4 else [[u,.15],[(u+U)/2,.9],[U,.15]])
                base_mesh['materials'].append('crystal')
            meshes[name]=base_mesh;export_mesh(name,base_mesh)
            p=ASSETS/f'models/item/{name}.json';d=json.loads(p.read_text());d['textures']={'atlas':f'{NS}:block/arcane/{family}_{tier}','crystal':f'{NS}:block/energy_crystal_facets_{tier*2}','particle':f'{NS}:block/arcane/{family}_{tier}'};write(p,d)
    SOURCE.mkdir(parents=True,exist_ok=True);write(SOURCE/'meshes.json',meshes);write(SOURCE/'rotors.json',{'wind':generated[True],'turbine':generated[False]})
    print('Arcane V2: 240 device states, two upper wind cells, matching static/animated rotors; hidden coplanar faces clipped.')


if __name__=='__main__':generate()

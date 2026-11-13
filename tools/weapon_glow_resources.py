"""Build local held-light variants from the existing authored light-sword meshes."""
from pathlib import Path
import copy
import json

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/yumis_dungeon_and_fight'
NS='yumis_dungeon_and_fight'


def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')


def material(name,texture,ambient):
    return f'newmtl {name}\nKd 1 1 1\nKa {ambient} {ambient} {ambient}\nd 1\nmap_Kd #{texture}\n\n'


def generate():
    for name in ['common_lightsaber','skyspine']:
        path=ASSETS/f'models/weapons/{name}.obj'
        lines=path.read_text(encoding='utf-8').splitlines();uvs=[];result=[];current=''
        for line in lines:
            if line.startswith('vt '):uvs.append(tuple(map(float,line.split()[1:3])))
            if line.startswith('usemtl '):continue
            if line.startswith('f '):
                token=line.split()[1];uv=uvs[int(token.split('/')[1])-1]
                palette=int(uv[0]*8)+int(uv[1]*4)*8
                group='energy' if name=='common_lightsaber' and palette==13 else 'blade' if palette in ([14] if name=='common_lightsaber' else [6,7]) else 'atlas'
                if current!=group:result.append('usemtl '+group);current=group
            result.append(line)
        path.write_text('\n'.join(result)+'\n',encoding='utf-8')
        base=material('atlas','atlas',0)+material('blade','atlas',0)
        glow=material('atlas','atlas',0)+material('blade','atlas',.4)
        if name=='common_lightsaber':base+=material('energy','energy',0);glow+=material('energy','energy',.4)
        (ASSETS/f'models/weapons/{name}.mtl').write_text(base,encoding='utf-8')
        (ASSETS/f'models/weapons/{name}_local_glow.mtl').write_text(glow,encoding='utf-8')
    path=ASSETS/'items/lightsaber.json';item=json.loads(path.read_text(encoding='utf-8'))
    cases=item['model']['cases'];cases[:]=[case for case in cases if not case['when'].endswith('_local_glow')]
    for case in list(cases):
        appearance=case['when']
        if appearance not in [f'lightsaber_{band}_base' for band in [10,20,30,40,50]]+['skyspine']:continue
        key=case['model']['model'].split(':',1)[1]
        model=json.loads((ASSETS/'models'/(key+'.json')).read_text(encoding='utf-8'))
        mesh='skyspine' if appearance=='skyspine' else 'common_lightsaber'
        model['mtl_override']=f'{NS}:models/weapons/{mesh}_local_glow.mtl';model['emissive_ambient']=True
        write(ASSETS/'models'/(key+'_local_glow.json'),model)
        entry=copy.deepcopy(case);entry['when']=appearance+'_local_glow';entry['model']['model']=f'{NS}:{key}_local_glow';cases.append(entry)
    write(path,item)


if __name__=='__main__':generate()

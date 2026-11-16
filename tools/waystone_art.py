"""Source for the waystone pedestal and its four quality-specific stone icons."""
from pathlib import Path
import json
from PIL import Image, ImageDraw

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/yumis_dungeon_and_fight'
BLOCK=ASSETS/'textures/block'
MODEL=ASSETS/'models/block'
NS='yumis_dungeon_and_fight'
QUALITIES=[('common','#f3f4ef','#b1bdc5'),('rare','#b36bff','#e0bdff'),
           ('artifact','#ff00f0','#ffd0fb'),('legendary','#ff8000','#ffe1a2')]

def save(im,name):
    BLOCK.mkdir(parents=True,exist_ok=True)
    im.save(BLOCK/(name+'.png'))

def stone(quality,index):
    """A small doorway remains recognisable as its frame grows more elaborate."""
    _,color,highlight=quality
    im=Image.new('RGBA',(32,32)); d=ImageDraw.Draw(im)
    mask=Image.new('RGBA',(32,32)); m=ImageDraw.Draw(mask)
    outer=[(4,10),(9,4),(21,3),(27,8),(29,18),(26,25),(21,29),(10,29),(4,24),(2,17)]
    base=['#77777a','#554568','#6e426b','#75583d'][index]
    facet=['#b4b2ac','#89769d','#a26c9d','#ad895c'][index]
    d.polygon(outer,fill='#292635')
    d.polygon([(5,11),(10,6),(20,5),(25,9),(27,18),(24,24),(20,27),(11,27),(6,23),(4,17)],fill=base)
    d.polygon([(6,11),(10,7),(20,6),(24,9),(25,12),(21,10),(17,8),(11,10),(8,15),(6,20),(5,17)],fill=facet)
    d.line([(7,23),(11,26),(20,26),(24,23)],fill='#363341',width=2)
    d.polygon([(10,24),(10,14),(16,8),(22,14),(22,24)],fill='#29273a')
    # Both the painted line and a matching emissive mask use the same doorway.
    path=[(10,24),(10,14),(16,8),(22,14),(22,24)]
    d.line(path,fill=color,width=2);m.line(path,fill='white',width=2)
    d.line([(12,24),(12,15),(16,11),(20,15),(20,24)],fill=highlight,width=1)
    m.line([(12,24),(12,15),(16,11),(20,15),(20,24)],fill=(255,255,255,210),width=1)
    d.rectangle((9,24,23,25),fill=color);m.rectangle((9,24,23,25),fill='white')
    d.rectangle((14,15,18,23),fill='#171c2c')
    for x in [3,25]:
        d.polygon([(x,16),(x+3,15),(x+4,17),(x+4,21),(x+1,22),(x,20)],fill='#b49462')
        d.line((x+1,16,x+3,16),fill='#e2c69a')
    if index>=1:
        d.line([(7,11),(9,8),(12,7)],fill=highlight,width=1)
        d.line([(23,8),(25,12),(26,15)],fill=highlight,width=1)
        d.polygon([(16,3),(19,6),(16,9),(13,6)],fill='#aa885b')
        d.polygon([(16,4),(18,6),(16,8),(14,6)],fill='#8ddbd7')
    if index>=2:
        for x in [5,25]:
            d.polygon([(x,13),(x+2,11),(x+3,18),(x+1,21)],fill=color)
            m.line([(x+1,13),(x+2,18)],fill='white',width=1)
        d.line([(12,4),(10,2),(7,3)],fill='#d4c9dc',width=1)
        d.line([(20,4),(22,2),(25,3)],fill='#d4c9dc',width=1)
    if index>=3:
        d.polygon([(16,0),(19,4),(16,7),(13,4)],fill='#b67d38')
        d.polygon([(16,1),(17,4),(16,5),(15,4)],fill=highlight)
        m.polygon([(16,1),(17,4),(16,5),(15,4)],fill='white')
        for x in [4,26]:
            d.line([(x,9),(x-1 if x==4 else x+1,5),(x+1 if x==4 else x-1,3)],fill='#e1ba78',width=2)
        d.line([(11,28),(16,30),(21,28)],fill=highlight,width=2)
        m.line([(11,28),(16,30),(21,28)],fill='white',width=1)
    return im,mask

def atlas():
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    d.rectangle((0,0,15,15),fill='#302b3b');d.rectangle((1,1,14,14),outline='#51495b',width=1)
    d.line((2,3,13,3),fill='#686070');d.line((2,12,13,12),fill='#1c1b28')
    for x,y in [(2,2),(12,2),(2,12),(12,12)]:d.rectangle((x,y,x+1,y+1),fill='#827987')
    d.rectangle((16,0,31,15),fill='#51445a');d.rectangle((17,1,30,14),outline='#817083',width=2)
    d.line((19,3,28,3),fill='#b3a0aa');d.line((19,12,28,12),fill='#302a3a')
    d.rectangle((0,16,15,31),fill='#262233');d.rectangle((1,17,14,30),outline='#5f546d',width=1)
    d.ellipse((3,19,12,28),fill='#44592f',outline='#7f9360',width=1)
    d.ellipse((5,21,10,26),fill='#9db468',outline='#c4d592',width=1)
    d.rectangle((7,21,8,26),fill='#253226')
    d.rectangle((16,16,31,31),fill='#171722');d.rectangle((17,17,30,30),outline='#484050',width=1)
    d.line((18,18,29,29),fill='#322e40');d.line((20,28,29,19),fill='#3c3549')
    return im

def ring():
    im=Image.new('RGBA',(32,32));d=ImageDraw.Draw(im)
    d.ellipse((4,4,27,27),outline=(255,255,255,220),width=1)
    d.ellipse((8,8,23,23),outline=(255,255,255,155),width=1)
    for x,y in [(15,2),(15,28),(2,15),(28,15)]:d.rectangle((x,y,x+2,y+2),fill=(255,255,255,210))
    d.line((16,5,16,9),fill=(255,255,255,220),width=1)
    d.line((16,23,16,27),fill=(255,255,255,220),width=1)
    d.line((5,16,9,16),fill=(255,255,255,220),width=1)
    d.line((23,16,27,16),fill=(255,255,255,220),width=1)
    return im

def model():
    def box(lo,hi,uv):
        return {'from':lo,'to':hi,'faces':{face:{'texture':'#atlas','uv':uv}
            for face in ['north','south','east','west','up','down']}}
    stone=[0,0,8,8];rim=[8,0,16,8];top=[0,8,8,16];obsidian=[8,8,16,16]
    elements=[box([1,0,1],[15,2,15],stone),box([3,2,3],[13,10,13],obsidian),
        box([2,9,2],[14,11,14],stone),box([3,11,3],[13,12,13],obsidian),
        box([4,12,4],[12,12.2,12],top)]
    for x,z in [(1,1),(12,1),(1,12),(12,12)]:
        elements.append(box([x,0,z],[x+3,2,z+3],stone))
        elements.append(box([x,2,z],[x+3,13,z+3],obsidian))
        elements.append(box([x,13,z],[x+3,14,z+3],stone))
    # Recessed dark side panels and a restrained central inlay echo the concept.
    elements.extend([box([5,3,2.95],[11,8.5,3],stone),box([5,3,13],[11,8.5,13.05],stone),
        box([2.95,3,5],[3,8.5,11],stone),box([13,3,5],[13.05,8.5,11],stone),
        box([7.5,4,2.9],[8.5,8,2.95],rim),box([7.5,4,13.05],[8.5,8,13.1],rim),
        box([2.9,4,7.5],[2.95,8,8.5],rim),box([13.05,4,7.5],[13.1,8,8.5],rim)])
    return {'ambientocclusion':True,'render_type':'minecraft:cutout',
        'textures':{'atlas':NS+':block/waystone_pedestal_atlas','particle':NS+':block/waystone_pedestal_atlas'},
        'elements':elements}

def generate():
    for i,quality in enumerate(QUALITIES):
        art,glow=stone(quality,i)
        save(art,'waystone_'+quality[0]);save(glow,'waystone_'+quality[0]+'_glow')
    save(atlas(),'waystone_pedestal_atlas');save(ring(),'waystone_pedestal_glow')
    (MODEL/'waystone_pedestal_empty.json').write_text(
        json.dumps(model(),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    states={'variants':{f'stone={value}':{'model':NS+':block/waystone_pedestal_empty'}
                        for value in range(5)}}
    (ASSETS/'blockstates/waystone_pedestal.json').write_text(
        json.dumps(states,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

if __name__=='__main__':
    generate()

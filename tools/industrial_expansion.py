"""Authoritative deposit, liquid, alloy and storage content. No test or preview side effects."""
from pathlib import Path
import json, math, random
from PIL import Image, ImageDraw
from industrial_arcane_models import model

ROOT=Path(__file__).resolve().parents[1]
NS='yumis_dungeon_and_fight'
RES=ROOT/'src/main/resources'
DATA=RES/'data'/NS
ASSETS=RES/'assets'/NS
RECIPES=DATA/'recipe/industrial'

def write(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def own(key):return key if ':' in key or key.startswith('#') else NS+':'+key
def recipe(key,value):write(RECIPES/(key+'.json'),value)
def process(key,machine,solids=(),outputs=(),fluids=(),fluid_out=None,tier=1,ticks=200,energy=1000):
    v={'type':own('industrial'),'machine':machine,'minimum_tier':tier,'ingredients':[{'ingredient':own(k),'count':n} for k,n in solids],'results':[{'id':own(k),'count':n} for k,n in outputs],'fluid_inputs':[{'ingredient':own(k),'amount':n} for k,n in fluids],'ticks':ticks,'energy':energy}
    if fluid_out:v['fluid_result']={'id':own(fluid_out[0]),'amount':fluid_out[1]}
    recipe(key,v)
def tag(registry,key,values,namespace='c'):
    p=RES/'data'/namespace/'tags'/registry/(key+'.json')
    d=json.loads(p.read_text(encoding='utf-8')) if p.exists() else {'replace':False,'values':[]}
    for v in values:
        if v not in d['values']:d['values'].append(v)
    write(p,d)
def smelt(key,source,target):recipe(key,{'type':'minecraft:smelting','category':'misc','ingredient':own(source),'result':{'id':own(target),'count':1},'experience':.2,'cookingtime':200})

# Ore IDs are stable; molybdenite/zircon are the named mineral forms of Mo/Zr.
METALS={'vanadium':('钒','8e98ad'),'molybdenum':('钼','6c83a2'),'zirconium':('锆','bd9276'),'mithril':('秘银','8de1da'),'adamantite':('精金','dc9d43'),'orichalcum':('山铜','d18367'),'iridium':('铱','d4d4f0')}
MINERALS={
 'cobblestone':(1,100,400,'minecraft:cobblestone',1,'圆石'),'amethyst':(2,15,400,'minecraft:amethyst_shard',2,'紫水晶'),
 'copper':(1,35,300,'minecraft:raw_copper',1,'铜'), 'coal':(1,30,400,'minecraft:coal',1,'煤'),
 'iron':(1,15,400,'minecraft:raw_iron',1,'铁'),'lead':(1,12,400,'raw_lead',1,'铅'),'orichalcum':(1,12,500,'raw_orichalcum',1,'山铜'),
 'gold':(2,8,600,'minecraft:raw_gold',1,'金'),'redstone':(2,12,400,'minecraft:redstone',4,'红石'),'lapis':(2,10,400,'minecraft:lapis_lazuli',4,'青金石'),
 'diamond':(2,4,1200,'minecraft:diamond',1,'钻石'),'vanadium':(2,10,600,'raw_vanadium',1,'钒'),'magic':(2,10,800,'energy_crystal_3',2,'魔力矿'),
 'titanium':(3,6,1200,'raw_titanium',1,'钛'),'molybdenum':(3,8,1000,'raw_molybdenum',1,'辉钼矿'),'zirconium':(3,8,1000,'raw_zirconium',1,'锆石矿'),'mithril':(3,5,1200,'raw_mithril',1,'秘银'),
 'niobium_tantalum':(4,3,1600,'raw_niobium_tantalum',1,'铌钽矿'),'adamantite':(4,2,2000,'raw_adamantite',1,'精金'),'uranium':(4,2,2000,'raw_uranium',1,'铀'),'iridium':(5,1,2400,'raw_iridium',1,'铱')}
from industrial_technology import MINING_ENERGY
MINERALS={key:(value[0],value[1],value[2],MINING_ENERGY[key],*value[3:]) for key,value in MINERALS.items()}
ORE_RULES={'titanium_overworld':(-48,16,3,6,1,7),'orichalcum':(-16,64,4,8,1,15),'vanadium':(-32,32,3,6,1,13),'molybdenum':(-48,16,3,5,1,8),'zirconium':(-32,32,3,6,1,10),'mithril':(-48,0,2,5,1,4),'niobium_tantalum':(-64,-16,2,6,1,1),'adamantite':(-64,-32,1,3,1,1),'iridium':(-64,-40,1,3,2,1)}
LIQUIDS={'alkahest':('万溶剂','963d63'),'blazing_fluid':('炽焰液','ff722c'),'ceruleum':('青磷水','38d8bb'),'shimmer':('微光','ac93ef'),'azoth':('阿佐特','5a89e8'),'penduliquid':('悬凝液','b5e9ef'),'spent_solvent':('浊溶剂','62584b')}
ALLOYS={
 'vanadium_steel':('钒钢',[('minecraft:iron_ingot',6),('vanadium_ingot',1),('minecraft:coal',1)],None,8,1,300,3200,'718299'),
 'molybdenum_vanadium_steel':('钼钒钢',[('vanadium_steel',6),('molybdenum_ingot',2)],None,8,2,400,12800,'687a93'),
 'tzm_alloy':('钛锆钼合金',[('molybdenum_ingot',5),('titanium_ingot',2),('zirconium_ingot',1)],None,8,2,500,25600,'aab9bc'),
 'orichalcum_alloy':('山铜导魔合金',[('orichalcum_ingot',4),('minecraft:copper_ingot',3),('minecraft:gold_ingot',1)],None,8,1,300,6400,'d8a56d'),
 'niobium_titanium_alloy':('铌钛合金',[('high_purity_niobium_ingot',4),('titanium_ingot',4)],None,8,3,600,102400,'9aabc8'),
 'mithril_niobium_alloy':('秘银铌钛合金',[('niobium_titanium_alloy',4),('high_purity_mithril_ingot',3),('high_purity_tantalum_ingot',1)],None,8,3,800,409600,'94e3e0'),
 'adamantite_titanium_alloy':('精金钛合金',[('adamantite_ingot',4),('titanium_ingot',3),('vanadium_steel',1)],('blazing_fluid',500),8,4,1000,1638400,'eab275'),
 'composite_conductor':('复合导体坯',[('mithril_niobium_alloy',4),('minecraft:copper_ingot',4)],('ceruleum',250),8,3,800,819200,'7dc5d9'),
 'henghui_alloy':('恒辉合金',[('composite_conductor',4),('orichalcum_alloy',2),('high_purity_iridium_ingot',1),('high_purity_tantalum_ingot',1)],('azoth',500),8,4,1200,6553600,'d9b6f4'),
 'lattice_substrate':('晶格基体',[('zircon_ceramic',4),('high_purity_tantalum_ingot',2),('energy_crystal_4',2)],('shimmer',500),4,4,1000,3276800,'a0a9e2'),
 'mana_superconductor':('魔力超导材',[('henghui_alloy',4),('high_purity_mithril_ingot',2),('lattice_substrate',2)],('penduliquid',500),4,5,1600,26214400,'b7f4ef')}
PARTS={
 'basic_frame':('基础机架',[('minecraft:iron_ingot',4),('minecraft:copper_ingot',4)],1,1),
 'reinforced_frame':('强化机架',[('basic_frame',1),('vanadium_steel',4)],1,1),
 'heatproof_frame':('耐热机架',[('reinforced_frame',1),('tzm_alloy',4),('zircon_ceramic',4)],1,3),
 'precision_frame':('精密机架',[('heatproof_frame',1),('adamantite_titanium_alloy',4),('zircon_ceramic',4)],1,4),
 'mana_component':('导魔组件',[('orichalcum_ingot',2),('minecraft:copper_ingot',2),('minecraft:redstone',4)],2,1),
 'mithril_mana_component':('秘银导魔组件',[('mana_component',1),('mithril_niobium_alloy',2),('minecraft:lapis_lazuli',2)],1,4),
 'control_component':('控制组件',[('niobium_ingot',1),('tantalum_ingot',1),('minecraft:gold_ingot',2),('minecraft:redstone',4)],1,3),
 'superconducting_control':('超导控制组件',[('control_component',1),('mana_superconductor',2),('spacetime_ingot',2)],1,5)}

def content():
    for key,(tier,chance,ticks,energy,item,count,title) in MINERALS.items():process('generate_'+key,'mineral_generator',outputs=[(item,count)],tier=tier,ticks=ticks//tier,energy=energy)
    for key,source,target in [('stone_to_cobblestone','stone','cobblestone'),('cobblestone_to_gravel','cobblestone','gravel'),('sand','gravel','sand')]:
        process(key,'mana_crusher',[(f'minecraft:{source}',1)],[(f'minecraft:{target}',1)],ticks=100,energy=100)
    for tier in range(1,6):process('currency_t'+str(tier),'currency_fabricator',tier=tier,ticks=1200,energy=600)
    process('pump_water','water_pump',fluids=(),fluid_out=('minecraft:water',1000),ticks=20,energy=10)
    for metal in [*METALS,'niobium_tantalum']:
        tag('item','ores/'+metal,[own(metal+'_ore')]);tag('item','raw_materials/'+metal,[own('raw_'+metal)])
        tag('block','ores/'+metal,[own(metal+'_ore')]);tag('item','ores',['#c:ores/'+metal]);tag('item','raw_materials',['#c:raw_materials/'+metal])
        for form in ['raw','ore']:process('crush_'+metal+'_'+form,'mana_crusher',[(('raw_'+metal) if form=='raw' else (metal+'_ore'),1)],[("crushed_"+metal,2)],ticks=100,energy=200)
    for metal in [*METALS,'titanium','niobium','tantalum']:
        tag('item','ingots/'+metal,[own(metal+'_ingot')]);tag('item','ingots',['#c:ingots/'+metal])
        if metal not in ['niobium','tantalum']:
            smelt('direct_'+metal+'_raw','raw_'+metal,metal+'_ingot');smelt('direct_'+metal+'_ore',metal+'_ore',metal+'_ingot')
            if metal!='titanium':smelt('smelt_crushed_'+metal,'crushed_'+metal,metal+'_ingot')
            process('leach_'+metal,'mineral_refinery',[("crushed_"+metal,2)],[(metal+'_concentrate',3)],[('alkahest',1000)],('spent_solvent',1000),tier=2,ticks=300,energy=6400)
        smelt('smelt_'+metal+'_concentrate',metal+'_concentrate',metal+'_ingot')
    for source in ['raw_niobium_tantalum','crushed_niobium_tantalum']:process('separate_'+source,'mineral_refinery',[(source,2)],[('niobium_concentrate',1),('tantalum_concentrate',1)],[('minecraft:water',1000)],tier=2,ticks=300,energy=3200)
    process('separate_niobium_tantalum_solvent','mineral_refinery',[('crushed_niobium_tantalum',2)],[('niobium_concentrate',2),('tantalum_concentrate',2)],[('alkahest',1000)],('spent_solvent',1000),tier=2,ticks=400,energy=12800)
    for metal in ['niobium','tantalum','iridium','mithril']:
        fluid=('shimmer',250) if metal=='mithril' else ('alkahest',1000)
        process('purify_'+metal,'mineral_refinery',[(metal+'_ingot',4)],[("high_purity_"+metal+"_ingot",4)],[fluid],None if metal=='mithril' else ('spent_solvent',1000),tier=2,ticks=400,energy=25600)
    process('zircon_ceramic','mana_forge',[('crushed_zirconium',4),('minecraft:clay_ball',4)],[('zircon_ceramic',4)],ticks=200,energy=3200)
    for key,(title,solids,fluid,count,tier,ticks,energy,color) in ALLOYS.items():process('alloy_'+key,'mana_forge',solids,[(key,count)],[] if fluid is None else [fluid],tier=tier,ticks=ticks,energy=energy)
    process('alloy_vanadium_steel_assisted','mana_forge',[('minecraft:iron_ingot',4),('vanadium_ingot',1),('minecraft:coal',1),('minecraft:copper_ingot',1)],[('vanadium_steel',8)],[('blazing_fluid',250)],ticks=200,energy=6400)
    for key,(title,solids,count,tier) in PARTS.items():
        process('automatic_'+key,'mana_forge',solids,[(key,count)],tier=tier,ticks=200+100*(tier-1),energy=1600*(4**(tier-1)))
    farms=[('minecraft:wheat',2),('minecraft:beetroot',2),('minecraft:wheat_seeds',8),('minecraft:beetroot_seeds',8)]
    for i,(farm,n) in enumerate(farms):
        for stage,volume,shards,alloy in [('basic',1000,1,None),('orichalcum',4000,2,'orichalcum_alloy'),('tzm',8000,2,'tzm_alloy')]:
            solids=[('minecraft:amethyst_shard',shards),(farm,n*(1 if stage=='basic' else 2))]+([] if alloy is None else [(alloy,1)])
            process(f'alkahest_{stage}_{i}','alchemical_reactor',solids,fluids=[('minecraft:water',volume)],fluid_out=('alkahest',volume),tier=3,ticks=200,energy={'basic':1600,'orichalcum':4800,'tzm':8000}[stage])
        process('regenerate_alkahest_'+str(i),'alchemical_reactor',[('minecraft:amethyst_shard',1),(farm,n)],fluids=[('spent_solvent',10000),('minecraft:water',1000)],fluid_out=('alkahest',8000),tier=3,ticks=400,energy=6400)
    fluids=[
      ('blazing_fluid',[('sulfide',2),('minecraft:coal',2),('crushed_vanadium',1)],[('minecraft:water',1000)],1000,200,6400),
      ('ceruleum',[('orichalcum_alloy',2),('energy_crystal_3',1)],[('minecraft:water',1000)],1000,200,12800),
      ('shimmer',[('crushed_zirconium',2),('mithril_ingot',1),('energy_crystal_3',1)],[('alkahest',1000)],1000,300,25600),
      ('azoth',[('adamantite_ingot',1),('mithril_ingot',2),('minecraft:gold_ingot',1)],[('shimmer',1000)],1000,400,102400),
      ('penduliquid',[('zircon_ceramic',2),('high_purity_niobium_ingot',1)],[('coolant',1000),('shimmer',250)],1000,500,204800)]
    for key,solids,inputs,amount,ticks,energy in fluids:process('react_'+key,'alchemical_reactor',solids,fluids=inputs,fluid_out=(key,amount),tier=3,ticks=ticks,energy=energy)
    # Machine and component crafting is owned by industrial_technology.
    p=DATA/'rules/industrial.json';rules=json.loads(p.read_text(encoding='utf-8'))
    rules.update(tankCapacities=[64000,256000,1024000],accumulatorCapacities=[100000,1000000,100000000,1000000000],scanTicks=40,scanCooldown=100,miningWaterRate=100,miningCoolantRate=40,pumpAmount=1000,pumpInterval=20,pumpEnergy=10,fluidTransferAmounts=[1000,4000,16000],fluidTransferOperationEnergy=[5,25,100],processingLines=[1,1,2,8,16],furnaceSpeeds=[4,8,16,32,64],transferEnergy=[5,25,100],version=3)
    rules.pop('fluidTransferEnergyPerBucket',None)
    rules['minerals']={k:dict(zip(['tier','chance','ticks','energy','item','count'],[v[0],v[1],v[2],v[3],own(v[4]),v[5]])) for k,v in MINERALS.items()}
    for key,v in ORE_RULES.items():
        rules['ores'][key]=dict(zip(['minY','maxY','minSize','maxSize','chance','attempts'],v))
        write(DATA/f'worldgen/configured_feature/{key}.json',{'type':own('industrial_resources'),'config':{'kind':key}})
        write(DATA/f'worldgen/placed_feature/{key}.json',{'feature':own(key),'placement':[]})
        write(DATA/f'neoforge/biome_modifier/{key}.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_overworld','features':[own(key)],'step':'underground_ores'})
    write(p,rules)

def rgb(value):return tuple(bytes.fromhex(value))
def tint(template,color):
    image=Image.open(ASSETS/'textures'/template).convert('RGBA');target=rgb(color)
    for y in range(image.height):
        for x in range(image.width):
            r,g,b,a=image.getpixel((x,y));lum=(r*.2126+g*.7152+b*.0722)/150
            if a:image.putpixel((x,y),(*[min(255,int(c*lum)) for c in target],a))
    return image
def save_image(image,category,key):
    path=ASSETS/'textures'/category/(key+'.png');path.parent.mkdir(parents=True,exist_ok=True);image.save(path)
def item_asset(key,color='9dd5da',template='item/titanium_ingot.png',mark=False):
    image=tint(template,color)
    if mark:ImageDraw.Draw(image).line([(5,8),(9,4),(11,5)],fill=(235,255,255,255),width=1)
    save_image(image,'item',key)
    write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':own('item/'+key)}})
    write(ASSETS/f'models/item/{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('item/'+key)}})
def ore_asset(key,color):
    image=Image.open(ASSETS/'textures/block/lead_ore.png').convert('RGBA');target=rgb(color)
    for y in range(image.height):
        for x in range(image.width):
            r,g,b,a=image.getpixel((x,y))
            if max(r,g,b)-min(r,g,b)>8:
                lum=(r+g+b)/360;image.putpixel((x,y),(*[min(255,int(c*lum)) for c in target],a))
    save_image(image,'block',key)
    write(ASSETS/f'models/block/{key}.json',{'parent':'minecraft:block/cube_all','textures':{'all':own('block/'+key)}})
    write(ASSETS/f'blockstates/{key}.json',{'variants':{'':{'model':own('block/'+key)}}})
    write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':own('block/'+key)}})

def boxes(family,tier):
    b=[([1,0,1],[15,2,15],'stone'),([2,2,2],[14,3,14],'gold')]
    def box(a,c,m='stone'):b.append((a,c,m))
    if family=='mineral_refinery':
        for x in [2,10]:box([x,3,4],[x+4,12,12]);box([x,5,3.8],[x+4,10,4],'rune');box([x-1,12,3],[x+5,13,13],'gold')
        box([6,6,6],[10,8,10],'gold');box([6,13,6],[10,16,10],'gem')
    elif family=='alchemical_reactor':
        box([3,3,3],[13,5,13]);box([4,5,4],[12,11,12],'gem')
        for x,z in [(2,2),(12,2),(2,12),(12,12)]:box([x,4,z],[x+2,13,z+2],'gold')
        box([2,11,2],[14,13,14]);box([3,13,3],[13,14,13],'gold');box([7,14,7],[9,16,9],'rune')
    elif family=='water_pump':
        box([3,3,4],[9,10,12]);box([4,5,3.8],[8,9,4],'rune');box([9,6,6],[13,9,10],'gold');box([11,9,6],[14,13,10],'gold');box([5,10,6],[7,14,10],'gem')
    elif family=='fluid_crystal':
        box([2,3,6],[4,12,10],'gold');box([12,3,6],[14,12,10],'gold');box([4,11,6],[12,13,10]);box([6,3,6],[10,4,10],'rune')
    elif family=='fluid_tank':
        for x,z in [(2,2),(12,2),(2,12),(12,12)]:box([x,3,z],[x+2,14,z+2],'gold')
        for lower,upper in [([2,13,2],[14,15,4]),([2,13,12],[14,15,14]),([2,13,4],[4,15,12]),([12,13,4],[14,15,12])]:box(lower,upper)
        box([6,15,2],[10,16,4],'rune')
        for y in range(tier):box([2.02,4+y*1.8,1.85],[3.98,5+y*1.8,2.02],'rune')
    elif family=='mana_accumulator':
        for x in [3,9]:
            for z in [3,9]:box([x,3,z],[x+4,12,z+4],'gem');box([x,12,z],[x+4,13,z+4],'gold')
        box([2,13,2],[14,15,14]);box([5,15,5],[11,16,11],'rune')
        for i in range(tier):box([2+i*3,5,1.8],[4+i*3,10,2],'rune')
    return b

def assets():
    names={};ore_blocks=[]
    for metal,(title,color) in {**METALS,'niobium_tantalum':('铌钽','9b92bb')}.items():
        ore=metal+'_ore';ore_blocks.append(ore);ore_asset(ore,color)
        names['block.'+NS+'.'+ore]={'molybdenum':'辉钼矿','zirconium':'锆石矿'}.get(metal,title+'矿石')
        for prefix,template,label in [('raw_','item/raw_titanium.png','粗'),('crushed_','item/crushed_titanium.png','粉碎')]:
            item_asset(prefix+metal,color,template);names[f'item.{NS}.{prefix}{metal}']=label+({'molybdenum':'辉钼矿','zirconium':'锆石矿'}.get(metal,title+'矿'))
        if metal!='niobium_tantalum':item_asset(metal+'_ingot',color);names[f'item.{NS}.{metal}_ingot']=title+'锭'
    for metal,title,color in [(m,v[0],v[1]) for m,v in METALS.items()]+[('titanium','钛','90a6bd'),('niobium','铌','a9a2c7'),('tantalum','钽','7182a8')]:
        item_asset(metal+'_concentrate',color,'item/crushed_titanium.png',True);names[f'item.{NS}.{metal}_concentrate']=title+'精矿'
        if metal in ['niobium','tantalum']:item_asset(metal+'_ingot',color);names[f'item.{NS}.{metal}_ingot']=title+'锭'
    for metal,title,color in [('niobium','铌','c6bfea'),('tantalum','钽','acbbdf'),('iridium','铱','ebdefe'),('mithril','秘银','b3fff0')]:
        key='high_purity_'+metal+'_ingot';item_asset(key,color,mark=True);names[f'item.{NS}.{key}']='高纯'+title+'锭'
    for key,v in ALLOYS.items():item_asset(key,v[-1],mark=True);names[f'item.{NS}.{key}']=v[0]
    for index,(key,v) in enumerate(PARTS.items()):
        color=['a7b6c9','66819c','b9b8ad','dcb278','d29b65','93e4e0','a4aad1','c0eefd'][index]
        image=Image.new('RGBA',(16,16));d=ImageDraw.Draw(image);c=rgb(color);dark=tuple(int(x*.35) for x in c)
        if key.endswith('frame'):
            d.rectangle((2,2,13,13),outline=dark,width=3);d.rectangle((3,3,12,12),outline=c,width=1)
            for x,y in [(2,2),(11,2),(2,11),(11,11)]:d.rectangle((x,y,x+2,y+2),fill=c);d.point((x,y),fill='white')
        else:
            d.rectangle((3,3,12,12),fill=dark,outline=c);d.line((4,7,11,7),fill=c);d.line((7,4,7,11),fill=c)
            d.polygon([(8,4),(11,8),(8,11),(5,8)],fill=c);d.line((7,6,7,8),fill='white')
            for x in [1,13]:d.line((x,5,x+1,5),fill=c);d.line((x,10,x+1,10),fill=c)
        save_image(image,'item',key);item_asset_model(key);names[f'item.{NS}.{key}']=v[0]
    image=Image.new('RGBA',(16,16));d=ImageDraw.Draw(image);d.polygon([(3,4),(11,2),(14,5),(14,12),(6,14),(3,11)],fill='#c8b49b',outline='#625b70');d.line((4,7,13,5),fill='#f0e9d7');d.line((7,6,7,13),fill='#8f7896');d.line((10,5,10,12),fill='#8f7896');save_image(image,'item','zircon_ceramic');item_asset_model('zircon_ceramic');names[f'item.{NS}.zircon_ceramic']='锆质陶瓷'
    for tier in range(1,6):
        key='prospector_t'+str(tier);image=tint('item/industrial_upgrade_'+str(tier)+'.png',['c5d6e6','6bdbdd','afbadd','9ee7cd','ddbef4'][tier-1]);d=ImageDraw.Draw(image);d.ellipse((4,4,12,12),fill='#162e38',outline='#e9dba6');d.line((8,5,8,10),fill='#6cf4dc');d.line((5,8,11,8),fill='#6cf4dc');save_image(image,'item',key);item_asset_model(key);names[f'item.{NS}.{key}']=str(tier)+'级探矿器'
    devices={}
    for key,family,tier,title in [('water_pump','water_pump',1,'抽水机'),('mineral_refinery','mineral_refinery',2,'精炼机'),('alchemical_reactor','alchemical_reactor',3,'反应釜')]:devices[key]=(family,tier,title)
    for family,titles in [('fluid_crystal',['一阶供液水晶','二阶供液水晶','三阶供液水晶']),('fluid_tank',['基础储液罐','合金储液罐','魔导储液罐']),('mana_accumulator',['简易魔力蓄能器','基础魔力蓄能器','复合魔力蓄能器','超导魔力蓄能器'])]:
        for tier,title in enumerate(titles,1):devices[f'{family}_t{tier}']=(family,tier,title)
    for key,(family,tier,title) in devices.items():
        variants={}
        # An upgraded block retains its registered block ID and changes its tier property.
        for grade in range(1,6):
            for state in ['', '_linked','_active']:
                geometry=boxes(family,grade);data=model(family,grade,state,geometry)
                atlas=own(f'block/arcane/mana_crusher_{grade}{state}');data['textures']={'atlas':atlas,'particle':atlas}
                write(ASSETS/f'models/block/{family}_{grade}{state}.json',data)
            for facing,angle in [('north',0),('east',90),('south',180),('west',270)]:
                for connected in [False,True]:
                    for active in [False,True]:
                        suffix='_active' if active else '_linked' if connected else ''
                        variants[f'facing={facing},tier={grade},connected={str(connected).lower()},active={str(active).lower()}']={'model':own(f'block/{family}_{grade}{suffix}'),'y':angle}
        if family=='fluid_tank':
            glass={'ambientocclusion':False,'render_type':'minecraft:translucent','textures':{'glass':'minecraft:block/glass','particle':'minecraft:block/glass'},'display':model(family,tier)['display'],'elements':[
                {'from':[3,3,3],'to':[13,13,13],'faces':{side:{'texture':'#glass','uv':[0,0,16,16]} for side in ['north','east','south','west']}},
                {'from':[4,13.02,4],'to':[12,13.02,12],'faces':{'up':{'texture':'#glass','uv':[0,0,16,16]}}}]}
            write(ASSETS/'models/block/fluid_tank_glass.json',glass)
            state={'multipart':[{'when':dict(pair.split('=') for pair in condition.split(',')),'apply':value} for condition,value in variants.items()]+[{'apply':{'model':own('block/fluid_tank_glass')}}]}
            item={'type':'minecraft:composite','models':[{'type':'minecraft:model','model':own('item/'+key)},{'type':'minecraft:model','model':own('block/fluid_tank_glass')}]}
        else:state={'variants':variants};item={'type':'minecraft:model','model':own('item/'+key)}
        write(ASSETS/f'blockstates/{key}.json',state);write(ASSETS/f'items/{key}.json',{'model':item})
        write(ASSETS/f'models/item/{key}.json',{'parent':own(f'block/{family}_{tier}')})
        names[f'block.{NS}.{key}']=title
    for key,(title,color) in LIQUIDS.items():
        write(ASSETS/f'models/block/{key}.json',{'textures':{'particle':'minecraft:block/water_still'}});write(ASSETS/f'blockstates/{key}.json',{'variants':{'':{'model':own('block/'+key)}}})
        image=Image.open(ASSETS/'textures/item/coolant_bucket.png').convert('RGBA');target=rgb(color)
        for y in range(image.height):
            for x in range(image.width):
                r,g,b,a=image.getpixel((x,y))
                if a and g>r*1.12 and b>r*1.12:
                    lum=(r+g+b)/450;image.putpixel((x,y),(*[min(255,int(c*lum)) for c in target],a))
        save_image(image,'item',key+'_bucket');item_asset_model(key+'_bucket');names[f'item.{NS}.{key}_bucket']=title+'桶';names[f'fluid_type.{NS}.{key}']=title
    for key,v in MINERALS.items():names[f'mineral.{NS}.{key}']=v[6]
    farms=['小麦','甜菜根','小麦种子','甜菜种子']
    for i,farm in enumerate(farms):
        for stage,title in [('basic','基础制备'),('orichalcum','山铜活化'),('tzm','耐热精制')]:names[f'recipe.{NS}.industrial.alkahest_{stage}_{i}']=f'{title}·{farm}'
        names[f'recipe.{NS}.industrial.regenerate_alkahest_{i}']='溶剂再生·'+farm
    for key,title in [('separate_raw_niobium_tantalum','粗矿水洗分离'),('separate_crushed_niobium_tantalum','粉碎矿水洗分离'),('separate_niobium_tantalum_solvent','铌钽溶剂分离'),('alloy_vanadium_steel','钒钢基础熔合'),('alloy_vanadium_steel_assisted','钒钢炽焰辅助'),('stone_to_cobblestone','石头粉碎为圆石'),('cobblestone_to_gravel','圆石粉碎为砂砾'),('sand','砂砾粉碎为沙子')]:names[f'recipe.{NS}.industrial.{key}']=title
    for tier in range(1,6):names[f'block.{NS}.mineral_generator_t{tier}']=str(tier)+'级区块采矿机'
    for locale in ['zh_cn','en_us']:
        p=ASSETS/'lang'/(locale+'.json');d=json.loads(p.read_text(encoding='utf-8'));d.update(names);write(p,d)
    tag('block','mineable/pickaxe',[own(k) for k in [*ore_blocks,*devices]],'minecraft')
    tag('block','needs_iron_tool',[own(k+'_ore') for k in ['orichalcum','vanadium','molybdenum','zirconium']],'minecraft')
    tag('block','needs_diamond_tool',[own(k+'_ore') for k in ['mithril','niobium_tantalum','adamantite','iridium']],'minecraft')
    manifest={'version':1,'generator':'tools/industrial_expansion.py','devices':{k:{'family':v[0],'tier':v[1],'title':v[2]} for k,v in devices.items()},'palette':{k:v[1] for k,v in METALS.items()},'liquids':LIQUIDS}
    write(ROOT/'art/industry-expansion/catalog.json',manifest)

def item_asset_model(key):
    write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':own('item/'+key)}})
    write(ASSETS/f'models/item/{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('item/'+key)}})

def generate():
    content();assets();
    from industrial_technology import generate as technology_content
    technology_content()
    print('Industrial expansion: deposits, 7 fluids, alloys, components, scanners and 13 devices generated.')

if __name__=='__main__':generate()

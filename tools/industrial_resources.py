"""Generate the checked-in v1 recipes, tags, models and localized industrial catalog."""
from pathlib import Path
import json,re

ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src/main/resources'
NS='yumis_dungeon_and_fight'
DATA=RES/'data'/NS
ASSETS=RES/'assets'/NS

def write(path, value):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

def recipe(name,value): write(DATA/'recipe'/'industrial'/f'{name}.json',value)
def own(name):return f'{NS}:{name}'
def result(item,count=1):return {'id':item,'count':count}
def need(item,count=1):return {'ingredient':item,'count':count}
def process(name,machine,ingredients,out=None,count=1,ticks=100,energy=100,tier=1,**extra):
    value={'type':own('industrial'),'machine':machine,'minimum_tier':tier,'ingredients':[need(*x) if isinstance(x,tuple) else need(x) for x in ingredients],'ticks':ticks,'energy':energy,**extra}
    if out:value['result']=result(out,count)
    recipe(name,value)
def craft(name,ingredients,out,count=1):recipe(name,{'type':'minecraft:crafting_shapeless','category':'misc','ingredients':ingredients,'result':result(own(out),count)})
def tag(name,values,registry='item',namespace='c'):
    path=RES/'data'/namespace/'tags'/registry/f'{name}.json'
    existing=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'replace':False,'values':[]}
    for value in values:
        if value not in existing['values']:existing['values'].append(value)
    write(path,existing)

names={};blocks=[];items=[]
families={'mana_crusher':'魔能碎石机','mana_furnace':'魔能熔炉','mineral_generator':'矿物生成器','currency_fabricator':'通货制造机','mana_forge':'魔能锻炉','mana_beacon':'魔法能量信标塔'}
prefixes=['','钻石','钛','泰拉石','时空石']
components={'mana_crusher':'stonecutter','mana_furnace':'furnace','mineral_generator':'iron_pickaxe','currency_fabricator':'dropper','mana_forge':'blast_furnace','mana_beacon':'amethyst_block'}
devices={}
for family,title in families.items():
    for tier in range(1,6):
        key=f'{family}_t{tier}';devices[key]=(family,tier);names[f'block.{NS}.{key}']=prefixes[tier-1]+title
from industrial_crafting import EXTRAS as CRAFT_EXTRAS
extras={key:((title+'魔能产生器') if title else '冷却液混合器',stage,[center]) for key,(stage,center,title) in CRAFT_EXTRAS.items()}
for key,(title,tier,parts) in extras.items():devices[key]=(key,tier);names[f'block.{NS}.{key}']=title
for family,title,component,third in [('transfer_crystal','物品传送水晶','ender_pearl','spacetime_ingot'),('harvest_crystal','作物收种水晶','wheat_seeds','terra_ingot')]:
    for tier in range(1,4):
        key=f'{family}_t{tier}';devices[key]=(family,tier);names[f'block.{NS}.{key}']=f'{title} · {tier}档'

items += ['nuclear_core','rtg_core']
for tier in range(1,11):
    key=f'energy_crystal_{tier}';items.append(key);names[f'item.{NS}.{key}']=f'{tier}阶魔能水晶'
for tier in range(1,6):
    key=f'industrial_upgrade_{tier}';items.append(key);names[f'item.{NS}.{key}']=f'{tier}级工业升级组件'

metal_names={'iron':'铁','copper':'铜','gold':'金','lead':'铅','uranium':'铀','titanium':'钛'}
for metal in ['lead','uranium','titanium']:
    items += ['raw_'+metal,metal+'_ingot'];blocks += [metal+'_ore',metal+'_block']
    names[f'item.{NS}.raw_{metal}']='粗'+metal_names[metal];names[f'item.{NS}.{metal}_ingot']=metal_names[metal]+'锭'
    names[f'block.{NS}.{metal}_ore']=metal_names[metal]+'矿石';names[f'block.{NS}.{metal}_block']=metal_names[metal]+'块'
    for category,value in [('ingots',metal+'_ingot'),('raw_materials','raw_'+metal),('ores',metal+'_ore'),('storage_blocks',metal+'_block')]:
        tag(category+'/'+metal,[own(value)]);tag(category,['#c:'+category+'/'+metal])
    tag('ores/'+metal,[own(metal+'_ore')],'block');tag('storage_blocks/'+metal,[own(metal+'_block')],'block')
    craft(metal+'_block',['#c:ingots/'+metal]*9,metal+'_block');craft(metal+'_unpack',[own(metal+'_block')],metal+'_ingot',9)
    if metal!='titanium':
        for source in ['raw_materials','ores']:
            recipe(metal+'_smelt_'+source,{'type':'minecraft:smelting','category':'misc','ingredient':'#c:'+source+'/'+metal,'result':{'id':own(metal+'_ingot')},'experience':.7,'cookingtime':200})
for metal,title in [('terra','泰拉石'),('spacetime','时空石')]:
    items.append(metal+'_ingot');blocks.append(metal+'_block');names[f'item.{NS}.{metal}_ingot']=title+'锭';names[f'block.{NS}.{metal}_block']=title+'块'
    tag('ingots/'+metal,[own(metal+'_ingot')]);tag('ingots',['#c:ingots/'+metal]);tag('storage_blocks/'+metal,[own(metal+'_block')]);tag('storage_blocks/'+metal,[own(metal+'_block')],'block')
    craft(metal+'_block',[own(metal+'_ingot')]*9,metal+'_block');craft(metal+'_unpack',[own(metal+'_block')],metal+'_ingot',9)
for key,title in [('industrial_magic_stone','魔刹石'),('sulfide','硫化物'),('mana_wand','魔力传输权杖'),('coolant_bucket','冷却液桶')]:items.append(key);names[f'item.{NS}.{key}']=title
tag('magic_stones',[own('industrial_magic_stone')],namespace=NS)
blocks += ['energy_crystal_block_2','industrial_magic_stone_block','magic_ore','magic_grass'];names[f'block.{NS}.energy_crystal_block_2']='2阶魔能水晶块';names[f'block.{NS}.industrial_magic_stone_block']='魔刹石块';names[f'block.{NS}.magic_ore']='魔力矿';names[f'block.{NS}.magic_grass']='魔力草';names[f'fluid_type.{NS}.coolant']='冷却液'
craft('industrial_magic_stone_block',[own('industrial_magic_stone')]*9,'industrial_magic_stone_block');craft('industrial_magic_stone_unpack',[own('industrial_magic_stone_block')],'industrial_magic_stone',9)
tag('storage_blocks/industrial_magic_stone',[own('industrial_magic_stone_block')]);tag('storage_blocks/industrial_magic_stone',[own('industrial_magic_stone_block')],'block');tag('storage_blocks',['#c:storage_blocks/industrial_magic_stone'])
craft('energy_crystal_block_2',[own('energy_crystal_2')]*9,'energy_crystal_block_2');craft('energy_crystal_unpack_2',[own('energy_crystal_block_2')],'energy_crystal_2',9)
craft('mana_wand',['minecraft:stick']*2+[own('energy_crystal_2')],'mana_wand')
craft('sulfide',['#c:ingots/lead','#c:sands','minecraft:coal'],'sulfide')
process('sulfide_mix','mana_forge',['#c:ingots/lead','#c:sands','minecraft:coal'],own('sulfide'))
for metal,title in metal_names.items():
    key='crushed_'+metal;items.append(key);names[f'item.{NS}.{key}']='粉碎'+title+'矿'
    for source in ['raw_materials','ores']:process(f'crush_{metal}_{source}','mana_crusher',['#c:'+source+'/'+metal],own(key),2,tier=3 if metal=='titanium' else 1)
    process('smelt_'+key,'mana_furnace',[own(key)],own(metal+'_ingot') if metal in ['lead','uranium','titanium'] else 'minecraft:'+metal+'_ingot',ticks=200,tier=2 if metal=='titanium' else 1)
    if metal in ['lead','uranium','titanium']:
        for source in ['raw_materials','ores']:process('mana_smelt_'+metal+'_'+source,'mana_furnace',['#c:'+source+'/'+metal],own(metal+'_ingot'),ticks=200,tier=2 if metal=='titanium' else 1)
process('sand','mana_crusher',['minecraft:cobblestone'],'minecraft:sand')
for key,out,count,ticks,energy,tier in [
    ('coal','minecraft:coal',1,400,40000,1),('iron','minecraft:raw_iron',1,400,1600,1),('copper','minecraft:raw_copper',1,300,1200,1),('cobblestone','minecraft:cobblestone',1,100,100,1),
    ('diamond','minecraft:diamond',1,1200,51200,2),('redstone','minecraft:redstone',4,400,12800,2),('lapis','minecraft:lapis_lazuli',4,400,12800,2),('gold','minecraft:raw_gold',1,600,12800,2),('lead',own('raw_lead'),1,400,6400,2),('titanium',own('raw_titanium'),1,1200,128000,3)]:process('generate_'+key,'mineral_generator',[],out,count,ticks,energy,tier)
for tier,ticks in enumerate([1200,600,300,150,75],1):process('currency_t'+str(tier),'currency_fabricator',[],ticks=ticks,energy=6400,tier=tier)
process('terra','mana_forge',[('#c:ingots/titanium',8),('#c:ingots/iron',4),('minecraft:amethyst_shard',4),'minecraft:ender_pearl'],own('terra_ingot'),8,1200,3276800,3)
process('magic_stone','mana_forge',[('minecraft:obsidian',4),('minecraft:amethyst_shard',4),'minecraft:blaze_powder'],own('industrial_magic_stone'),4,800,819200,3)
process('spacetime','mana_forge',[('#c:ingots/terra',8),('#c:ingots/titanium',4),(own('industrial_magic_stone'),4),'minecraft:ender_eye'],own('spacetime_ingot'),8,2400,209715200,4)
process('coolant','coolant_mixer',['minecraft:lapis_lazuli'],ticks=100,energy=1000,tier=2,fluid_input={'ingredient':'minecraft:water','amount':1000},fluid_result={'id':own('coolant'),'amount':1000})
for machine,power,duration,fuels in [
    ('coal_generator',60,640,['minecraft:coal','minecraft:charcoal']),('turbine_generator',1080,640,['minecraft:coal','minecraft:charcoal',own('sulfide')]),('thermal_generator',6400,640,[own('sulfide')]),('nuclear_reactor',108000,16000,['#c:ingots/uranium']),('rtg_generator',10800,160000,['#c:ingots/uranium'])]:
    for i,fuel in enumerate(fuels):process(f'fuel_{machine}_{i}','fuel_'+machine,[fuel],ticks=duration,energy=power*duration//20)
    if machine in ['coal_generator','turbine_generator']:process(f'fuel_{machine}_block','fuel_'+machine,['minecraft:coal_block'],ticks=duration*9,energy=power*duration*9//20)

capacities=[8,16,32,48,64,88,112,136,160,192,224,256,296,336,376,416]
for tier in range(2,17):
    material='#c:ingots/copper' if tier<=3 else '#c:ingots/iron' if tier<=5 else '#c:ingots/gold' if tier<=8 else '#c:gems/diamond' if tier<=10 else '#c:ingots/terra' if tier<=12 else '#'+NS+':magic_stones' if tier<=14 else '#c:ingots/spacetime'
    ingredients=[(material,capacities[tier-1]-capacities[tier-2])]
    if tier in [4,5]:ingredients.append(('minecraft:quartz',8))
    if tier in [7,8,10,12,14,16]:ingredients.append(('minecraft:amethyst_shard',4))
    process('storage_upgrade_'+str(tier),'storage_upgrade_'+str(tier),ingredients,ticks=1,energy=0)

rules={'version':1,'beaconCapacity':1000,'beaconRadii':[3,5,9,18,32],'furnaceSpeeds':[4,16,32,64,128],'solarPeriods':[0,2000,10000,12000],'transferIntervals':[20,10,1],'transferAmounts':[8,64,64],'transferRanges':[24,128,2147483647],'transferEnergy':[100,200,400],'harvestSizes':[3,9,27],'harvestPeriod':500,'harvestEnergy':200,'harvestCrops':['minecraft:wheat','minecraft:carrots','minecraft:potatoes','minecraft:beetroots','minecraft:nether_wart'],'fluidCapacity':20000,'magicGrassChance':16,'crystalDropMin':2,'crystalDropMax':8,'highCrystalWeight':20,'leadWorldgen':True,'uraniumWorldgen':True,'titaniumWorldgen':True,'magicWorldgen':True,'ores':{},'generators':{}}
for k,vals in {'magic':(-32,32,3,6,1),'lead':(-32,48,4,8,1),'uranium':(-64,0,2,4,4),'titanium':(0,80,3,6,1)}.items():rules['ores'][k]=dict(zip(['minY','maxY','minSize','maxSize','chance','attempts'],(*vals,1)))
for k,power,ticks,fluid,rate in [('coal_generator',60,640,'',0),('turbine_generator',1080,640,'minecraft:water',1000),('thermal_generator',6400,640,own('coolant'),100),('nuclear_reactor',108000,16000,own('coolant'),1000),('rtg_generator',10800,160000,'',0)]:rules['generators'][k]={'fluidPerSecond':rate,'fluid':fluid}
rules.update(windBaseHeight=60,windBasePower=50,windHeightStep=5,windPowerStep=1,solarDayPower=2,solarEdgePower=1,solarRainPower=1)
code=(ROOT/'src/main/java/cn/yumis/fight/rules/CurrencyRules.java').read_text(encoding='utf-8')
rules['currencyItemWeights']={name:1 for name,rarity in re.findall(r'new Currency\("([^"]+)",Rarity\.(\w+),true\)',code)}
rules['currencyRarityWeights']={rarity:float(weight) for rarity,weight in re.findall(r'tier\(Rarity\.(\w+),(\d+)\)',code)}
write(DATA/'rules/industrial.json',rules)
for kind in ['grass','magic','lead','uranium','titanium']:
    write(DATA/f'worldgen/configured_feature/{kind}.json',{'type':own('industrial_resources'),'config':{'kind':kind}})
    write(DATA/f'worldgen/placed_feature/{kind}.json',{'feature':own(kind),'placement':[]})
    write(DATA/f'neoforge/biome_modifier/{kind}.json',{'type':'neoforge:add_features','biomes':'#minecraft:is_end' if kind=='titanium' else '#minecraft:is_overworld','features':[own(kind)],'step':'top_layer_modification' if kind=='grass' else 'underground_ores'})

all_blocks=list(devices)+blocks
tag('mineable/pickaxe',[own(b) for b in all_blocks if b!='magic_grass'],'block','minecraft')
tag('needs_iron_tool',[own(b) for b in ['lead_ore','magic_ore']],'block','minecraft');tag('needs_diamond_tool',[own(b) for b in ['uranium_ore','titanium_ore']],'block','minecraft')
for key in blocks:
    if key=='magic_grass':model={'parent':'minecraft:block/cross','render_type':'minecraft:cutout','textures':{'cross':own('block/magic_grass')}}
    else:model={'parent':'minecraft:block/cube_all','textures':{'all':own('block/'+key)}}
    write(ASSETS/f'models/block/{key}.json',model);write(ASSETS/f'blockstates/{key}.json',{'variants':{'':{'model':own('block/'+key)}}})
    if key not in ['magic_ore','magic_grass','lead_ore','uranium_ore','titanium_ore']:write(DATA/f'loot_table/blocks/{key}.json',{'type':'minecraft:block','pools':[{'rolls':1,'entries':[{'type':'minecraft:item','name':own(key)}]}]})
for key,(family,tier) in devices.items():
    variants={}
    for visual in range(1,6):
        for facing,rot in [('north',0),('east',90),('south',180),('west',270)]:
            for connected in [False,True]:
                for active in [False,True]:
                    suffix='_active' if active else '_linked' if connected else ''
                    model=f'{family}_{visual}{suffix}'
                    block_model={'parent':'minecraft:block/orientable','textures':{'side':own('block/machine_side_'+str(visual)),'top':own('block/machine_top_'+str(visual)),'front':own('block/'+family+suffix)}}
                    if family in ['transfer_crystal','harvest_crystal','mana_beacon']:
                        block_model={'textures':{'particle':own('block/machine_side_'+str(visual)),'all':own('block/machine_side_'+str(visual))},'elements':[{'from':[2,0,2],'to':[14,2,14],'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}},{'from':[5,2,5],'to':[11,4,11],'faces':{f:{'texture':'#all'} for f in ['up','down','north','south','east','west']}}]}
                    write(ASSETS/f'models/block/{model}.json',block_model)
                    variants[f'facing={facing},tier={visual},connected={str(connected).lower()},active={str(active).lower()}']={'model':own('block/'+model),'y':rot}
    write(ASSETS/f'blockstates/{key}.json',{'variants':variants});write(ASSETS/f'models/item/{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('block/'+family)}} if family in ['transfer_crystal','harvest_crystal'] else {'parent':own(f'block/{family}_{tier}')})
for key in items+all_blocks:
    write(ASSETS/f'items/{key}.json',{'model':{'type':'minecraft:model','model':own('item/'+key)}})
    if key in blocks:write(ASSETS/f'models/item/{key}.json',{'parent':own('block/'+key)})
    elif key not in devices:write(ASSETS/f'models/item/{key}.json',{'parent':'minecraft:item/generated','textures':{'layer0':own('item/'+key)}})
for locale in ['zh_cn','en_us']:
    path=ASSETS/f'lang/{locale}.json';dictionary=json.loads(path.read_text(encoding='utf-8'));dictionary.update(names);write(path,dictionary)
manifest={'devices':devices,'blocks':blocks,'items':items,'families':list(families)+list(extras)+['transfer_crystal','harvest_crystal']}
write(ROOT/'temp/industrial-art-manifest.json',manifest)
print(f'Industrial resources: {len(devices)} devices, {len(items)} items, {len(blocks)} material blocks; recipes and tags generated.')

# Preserve the V1 visual authoring layer when regenerating the industrial data catalog.
from industrial_visual_models import generate as refresh_visual_models
refresh_visual_models()
from industrial_arcane_models import generate as refresh_arcane_models
refresh_arcane_models()

# Apply the single authoritative shaped catalog after legacy data generation.
from industrial_crafting import generate as refresh_crafting
refresh_crafting()

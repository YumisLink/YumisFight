-- Export named frames from the native material, fuel-rod and crystal sources.
-- Run with Aseprite --batch --script-param root=<checkout> --script this-file.
-- only=bases exports the 78 material variants without rewriting other PNGs.
-- only=ingots exports the 29 ingot variants, including animations.
-- item=<registered-id> restricts the selection to one material.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/'
local sources={{'industrial_materials',32,false},{'industrial_material_bases',16,true},
  {'industrial_component_bases',16,false},{'nuclear_fuel_rods',16,false},{'energy_crystals',16,false}}
local records,order={},{}
for _,entry in ipairs(sources) do
  local source=Sprite{fromFile=root..'/tools/'..entry[1]..'.aseprite'}
  assert(source.width==entry[2] and source.height==entry[2],'Unexpected native material canvas')
  for _,tag in ipairs(source.tags) do
    local name=tag.name
    assert(name:match('^[a-z0-9_]+$'),'Invalid material tag: '..name)
    assert(not records[name],'Duplicate material source: '..name)
    local first,last=tag.fromFrame.frameNumber,tag.toFrame.frameNumber
    local frames=last-first+1
    assert(frames==1 or entry[3],'Only native 16px material tags can animate')
    local spec=ImageSpec(source.spec);spec.height=source.height*frames
    local image=Image(spec)
    local ticks
    for i=first,last do
      local frame=Image(source.spec);frame:drawSprite(source,i)
      image:drawImage(frame,Point(0,(i-first)*source.height))
      local duration=math.max(1,math.floor(source.frames[i].duration*20+.5))
      assert(not ticks or ticks==duration,'Material animation frames must use a uniform duration')
      ticks=duration
    end
    records[name]={image=image,based=entry[3],frames=frames,size=entry[2],ticks=ticks};order[#order+1]=name
  end
  source:close()
end
local names,seen={},{}
local previous=assert(io.open(root..'/tools/industrial_materials.json','r'))
for name in previous:read('*a'):gmatch('"([a-z0-9_]+)"') do
  if name~='items' then
    assert(records[name],'Missing material source: '..name)
    assert(not seen[name],'Duplicate manifest entry: '..name)
    names[#names+1]=name;seen[name]=true
  end
end
previous:close()
for _,name in ipairs(order) do if not seen[name] then names[#names+1]=name;seen[name]=true end end
assert(#names==140,'Expected 128 industrial items and twelve bucket variants')
local count=0
assert(not app.params.item or records[app.params.item],'Unknown material item selection')
for _,name in ipairs(names) do
  local record=records[name]
  local ingot=record.based and (name:match('_ingot$') or name:match('_alloy$') or name:match('_steel$'))
  if (not app.params.item or name==app.params.item) and ((app.params.only~='bases' and app.params.only~='ingots') or
      (app.params.only=='bases' and record.based) or (app.params.only=='ingots' and ingot)) then
    record.image:saveAs(output..name..'.png');count=count+1
    if record.frames>1 then
      local meta=assert(io.open(output..name..'.png.mcmeta','w'))
      meta:write(string.format('{\n  "animation": {\n    "width": %d,\n    "height": %d,\n    "frametime": %d,\n    "interpolate": false\n  }\n}\n',record.size,record.size,record.ticks));meta:close()
    else
      -- A material changed to a static tag must lose its former animation.
      -- Only the selected, validated material filename is considered here.
      local metaPath=output..name..'.png.mcmeta'
      local existing=io.open(metaPath,'r')
      if existing then existing:close();assert(os.remove(metaPath),'Cannot remove obsolete material animation: '..name) end
    end
  end
end
if not app.params.item then
  local manifest=assert(io.open(root..'/tools/industrial_materials.json','w'))
  manifest:write('{\n  "items": [\n')
  for i,name in ipairs(names) do
    manifest:write('    "'..name..'"'..(i<#names and ',' or '')..'\n')
  end
  manifest:write('  ]\n}\n');manifest:close()
end
print('Exported '..count..' material textures.')

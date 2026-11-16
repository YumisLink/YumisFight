-- Native 16px outer glass and inner luminous matrix, in the item rarity colours.
-- References: doc/工业材料、魔法材料与符文贴图参考清单.md#ref-r53
-- Run Aseprite --batch --script-param root=<checkout> --script this-file.
-- Later manual source edits can be exported with export_technology_blocks.lua.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local sprite=Sprite(16,16,ColorMode.RGB)
local pc=app.pixelColor
local glass=sprite.layers[1];glass.name='01 / outer glass'
local reflection=sprite:newLayer();reflection.name='02 / outer reflections'
local edges=sprite:newLayer();edges.name='03 / outer emissive edges'
local core=sprite:newLayer();core.name='04 / inner luminous core (hidden)';core.isVisible=false
-- Match the same public rarity palette used by IndustrialItemDisplay.
local rarityFile=assert(io.open(root..'/src/main/java/cn/yumis/fight/modules/equipment/api/Rarity.java','r'))
local rarityColors=assert(rarityFile:read('*a'):match('int color%(%)(.*)'),'Missing rarity colours');rarityFile:close()
local palettes={}
for hex in rarityColors:gmatch('0x(%x%x%x%x%x%x)') do palettes[#palettes+1]=hex end
assert(#palettes==6,'Expected the six current rarity colours')
local mythicFile=assert(io.open(root..'/src/client/java/cn/yumis/fight/modules/equipment/api/client/MythicDisplay.java','r'))
local mythicSource=mythicFile:read('*a');mythicFile:close()
local mythicGold=assert(mythicSource:match('GOLD%s*=%s*0x(%x%x%x%x%x%x)'),'Missing mythic gold')
local mythicPink=assert(mythicSource:match('PINK%s*=%s*0x(%x%x%x%x%x%x)'),'Missing mythic pink')
local function color(hex,alpha)
  return pc.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),alpha or 255)
end
local function rect(image,x0,y0,x1,y1,c)
  for y=y0,y1 do for x=x0,x1 do image:drawPixel(x,y,c) end end
end
local function tint(hex,white,alpha)
  local channels={}
  for channel in hex:gmatch('%x%x') do
    local value=tonumber(channel,16)
    channels[#channels+1]=math.floor(value+(255-value)*white+.5)
  end
  return pc.rgba(channels[1],channels[2],channels[3],alpha or 255)
end
local function mythicGradient(image)
  local gold,pink=color(mythicGold),color(mythicPink)
  for y=0,15 do for x=0,15 do
    local pixel=image:getPixel(x,y)
    if pc.rgbaA(pixel)>0 then
      local white=(pc.rgbaB(pixel)-pc.rgbaB(gold))/(255-pc.rgbaB(gold))
      local t=y/15
      local function channel(component)
        local value=component(gold)*(1-t)+component(pink)*t
        return math.floor(value+(255-value)*white+.5)
      end
      image:drawPixel(x,y,pc.rgba(channel(pc.rgbaR),channel(pc.rgbaG),channel(pc.rgbaB),pc.rgbaA(pixel)))
    end
  end end
end
for index=1,5 do
  local p=palettes[index+1]
  if index==5 then p=mythicGold end
  if index>1 then sprite:newEmptyFrame() end
  local body,rim,shine,inside=Image(sprite.spec),Image(sprite.spec),Image(sprite.spec),Image(sprite.spec)
  -- The glass and emissive rim occupy disjoint pixels on the same outer shell.
  rect(body,1,1,14,14,color(p,30))
  for y=2,13 do for x=2,13 do
    if x+y>=18 then body:drawPixel(x,y,color(p,46)) end
  end end
  rect(rim,0,0,15,0,tint(p,.62,225))
  rect(rim,0,1,0,15,tint(p,.52,210))
  rect(rim,15,1,15,15,tint(p,.24,175))
  rect(rim,1,15,14,15,tint(p,.24,175))
  rect(rim,0,0,2,0,tint(p,.90))
  rect(rim,0,1,0,2,tint(p,.90))
  rim:drawPixel(15,0,tint(p,.85))
  rim:drawPixel(0,15,tint(p,.85,235))
  rim:drawPixel(15,15,tint(p,.62,225))
  rect(shine,10,3,11,4,tint(p,.65,64))
  rect(shine,8,5,9,6,tint(p,.65,64))
  -- A separate smaller cube is visible through the low-opacity outer shell.
  rect(inside,0,0,15,15,color(p,205))
  rect(inside,1,1,14,5,tint(p,.20,205))
  rect(inside,0,0,15,0,tint(p,.72))
  rect(inside,0,1,0,15,tint(p,.55))
  rect(inside,15,1,15,15,tint(p,.15,240))
  rect(inside,1,15,14,15,tint(p,.15,240))
  rect(inside,2,2,5,2,tint(p,.65,235))
  inside:drawPixel(2,3,tint(p,.65,235))
  if index==5 then
    for _,image in ipairs({body,rim,shine,inside}) do mythicGradient(image) end
  end
  if index==1 then glass:cel(1).image=body else sprite:newCel(glass,index,body,Point(0,0)) end
  sprite:newCel(edges,index,rim,Point(0,0))
  sprite:newCel(reflection,index,shine,Point(0,0))
  sprite:newCel(core,index,inside,Point(0,0))
end
for index=1,5 do
  local tag=sprite:newTag(index,index);tag.name='tech_block_t'..(index+1)
end
sprite:saveAs(root..'/tools/technology_blocks.aseprite')
sprite:close()
dofile(root..'/tools/export_technology_blocks.lua')

-- Run with --script-param root=<checkout> after industrial_creative.py --minecraft-client <jar>.
-- Keep the reference pixels locked on their own layer; export only these three new assets.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local assets=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block/'
local work=root..'/temp/creative-industry/art/'
app.fs.makeAllDirectories(work)
local pc=app.pixelColor
local specs={
  {id='infinite_power_tower',base=assets..'arcane/mana_beacon_5.png',size=32},
  {id='infinite_fluid_tank',base=assets..'arcane/mana_crusher_3.png',size=32},
  {id='infinite_item_barrel',base=work..'barrel_side.png',size=16},
}
local sprite=Sprite(32,32,ColorMode.RGB)
local baseLayer=sprite.layers[1];baseLayer.name='Locked reference pixels'
local edits=sprite:newLayer();edits.name='Gold material and sample sign'
local palette={{25,18,14},{88,56,22},{172,116,37},{234,185,76},{255,242,187}}
local function gold(pixel)
  local luminance=(pc.rgbaR(pixel)*.2126+pc.rgbaG(pixel)*.7152+pc.rgbaB(pixel)*.0722)/255
  local position=math.min(3.999,luminance*4)
  local index=math.floor(position)+1;local t=position-math.floor(position)
  local a,b=palette[index],palette[index+1]
  return pc.rgba(math.floor(a[1]+(b[1]-a[1])*t),math.floor(a[2]+(b[2]-a[2])*t),math.floor(a[3]+(b[3]-a[3])*t),pc.rgbaA(pixel))
end
for index,spec in ipairs(specs) do
  local original=Sprite{fromFile=spec.base}
  assert(original.width==spec.size and original.height==spec.size,'Unexpected reference dimensions: '..spec.id)
  local base=Image(original.spec);base:drawSprite(original,1);original:close()
  local overlay=Image(spec.size,spec.size,ColorMode.RGB)
  if spec.size==32 then
    for y=0,spec.size-1 do for x=0,spec.size-1 do
      local pixel=base:getPixel(x,y)
      if pc.rgbaA(pixel)>0 then overlay:drawPixel(x,y,gold(pixel)) end
    end end
  else
    -- The original wood, hoops, scale and UVs remain outside this 8x8 sign.
    for y=4,11 do for x=4,11 do
      local edge=x==4 or x==11 or y==4 or y==11
      local color=edge and (y==4 or x==4) and pc.rgba(255,224,130,255)
        or edge and pc.rgba(151,102,32,255) or pc.rgba(35,29,24,255)
      overlay:drawPixel(x,y,color)
    end end
    -- A small infinity mark beneath the separately rendered sample item.
    for _,point in ipairs({{5,9},{6,9},{9,9},{10,9},{5,10},{7,10},{8,10},{10,10}}) do
      overlay:drawPixel(point[1],point[2],pc.rgba(255,218,108,255))
    end
  end
  local frame=index==1 and sprite.frames[1] or sprite:newEmptyFrame(index)
  if index==1 then baseLayer:cel(1).image=base else sprite:newCel(baseLayer,frame,base,Point(0,0)) end
  sprite:newCel(edits,frame,overlay,Point(0,0))
  local tag=sprite:newTag(index,index);tag.name=spec.id
  local composite=Image(spec.size,spec.size,ColorMode.RGB);composite:drawSprite(sprite,index)
  composite:saveAs(assets..spec.id..'.png')
  base:saveAs(work..spec.id..'-before.png');composite:saveAs(work..spec.id..'-after.png')
end
baseLayer.isEditable=false
sprite:saveAs(root..'/tools/industrial_creative.aseprite')
sprite:close()
print('Authored two gold material variants and the four-sided barrel sign from unchanged reference pixels.')

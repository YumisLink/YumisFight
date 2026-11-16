-- Black asphalt-like belt surface; native metal/funnel textures remain unchanged.
-- Run Aseprite --batch --script-param root=<checkout> --script tools/draw_industrial_conveyors.lua
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block'
if not app.params.rimOnly then
local sprite=Sprite(16,16,ColorMode.RGB)
local pc=app.pixelColor
local base=sprite.layers[1];base.name='Locked black belt base'
local image=Image(16,16,ColorMode.RGB)
for y=0,15 do for x=0,15 do image:drawPixel(x,y,pc.rgba(18,19,21,255)) end end
base:cel(1).image=image;base.isEditable=false
local grain=sprite:newLayer();grain.name='Asphalt grain'
local details=Image(16,16,ColorMode.RGB)
for y=0,15 do for x=0,15 do
  local n=(x*37+y*71+x*y*13)%17
  if n<6 then details:drawPixel(x,y,pc.rgba(22+n,23+n,25+n,255)) end
end end
sprite:newCel(grain,1,details,Point(0,0))
local seams=sprite:newLayer();seams.name='Belt joints and direction'
local marks=Image(16,16,ColorMode.RGB)
for x=0,15 do marks:drawPixel(x,7,pc.rgba(10,11,13,255));marks:drawPixel(x,8,pc.rgba(34,35,37,255)) end
for i=0,3 do marks:drawPixel(4+i,4-i,pc.rgba(57,59,61,255));marks:drawPixel(11-i,4-i,pc.rgba(57,59,61,255)) end
sprite:newCel(seams,1,marks,Point(0,0))
sprite:saveAs(root..'/tools/industrial_conveyors.aseprite')
-- The caller creates output directories, as required by the local batch workflow.
sprite:saveAs(output..'/conveyor_surface.png')
sprite:close()
end
local pc=app.pixelColor
-- Titanium is the same native metal pixel structure, recolored on a separate editable layer.
if app.params.metalBase then
  local metal=app.open(app.params.metalBase)
  if metal.colorMode~=ColorMode.RGB then app.command.ChangePixelFormat{format='rgb'} end
  local source=Image(metal.cels[1].image)
  local locked=metal.layers[1];locked.name='Locked native anvil base';locked.isEditable=false;locked.isVisible=false
  local tint=metal:newLayer();tint.name='Titanium sky blue metal'
  local mapped=Image(source.width,source.height,ColorMode.RGB)
  for y=0,source.height-1 do for x=0,source.width-1 do
    local p=source:getPixel(x,y);local a=pc.rgbaA(p)
    if a>0 then
      local v=(pc.rgbaR(p)*.2126+pc.rgbaG(p)*.7152+pc.rgbaB(p)*.0722)/255
      v=math.sqrt(v)
      mapped:drawPixel(x,y,pc.rgba(math.floor(55+140*v),math.floor(140+110*v),math.floor(190+65*v),a))
    end
  end end
  metal:newCel(tint,1,mapped,Point(0,0))
  metal:saveAs(root..'/tools/titanium_conveyor_frame.aseprite')
  metal:saveAs(output..'/titanium_conveyor_frame.png')
  metal:close()
end

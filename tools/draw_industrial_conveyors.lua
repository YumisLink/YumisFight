-- Black asphalt-like belt surface; native metal/funnel textures remain unchanged.
-- Run Aseprite --batch --script-param root=<checkout> --script tools/draw_industrial_conveyors.lua
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
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
local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block'
app.fs.makeAllDirectories(output)
sprite:saveAs(output..'/conveyor_surface.png')
sprite:close()

-- Slow powered-state crystal/rune pulse. Idle and connected atlases stay static.
local root=assert(app.params.root):gsub("\\","/").."/"
local source=root.."art/industry-arcane/arcane/"
local target=root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/block/arcane/"
local families={"mana_crusher","mana_furnace","mineral_generator","currency_fabricator","mana_forge","mana_beacon","coal_generator","solar_collector","turbine_generator","wind_generator","thermal_generator","nuclear_reactor","rtg_generator","coolant_mixer","transfer_crystal","harvest_crystal","equipment_fabricator"}
for _,family in ipairs(families) do for tier=1,5 do
 local name=family.."_"..tier.."_active"
 local spr=app.open(source..name..".aseprite")
 for _,l in ipairs(spr.layers) do if l.name=="05 / powered breathing" then spr:deleteLayer(l);break end end
 while #spr.frames>1 do spr:deleteFrame(#spr.frames) end
 while #spr.tags>0 do spr:deleteTag(spr.tags[#spr.tags]) end
 local base=Image(32,32,ColorMode.RGB);base:drawSprite(spr,1)
 for i=2,12 do spr:newFrame(#spr.frames) end
 local lights=spr:newLayer();lights.name="05 / powered breathing"
 local strip=Image(32,384,ColorMode.RGB)
 for i=1,12 do
  local factor=.38+.62*(1-math.cos((i-1)*math.pi/6))/2
  local overlay=Image(32,32,ColorMode.RGB)
  for y=16,31 do for x=0,31 do
   local p=base:getPixel(x,y)
   local r,g,b=app.pixelColor.rgbaR(p),app.pixelColor.rgbaG(p),app.pixelColor.rgbaB(p)
   if x<16 or math.max(r,g,b)>90 then
    overlay:drawPixel(x,y,app.pixelColor.rgba(math.floor(r*factor+.5),math.floor(g*factor+.5),math.floor(b*factor+.5),255))
   end
  end end
  spr:newCel(lights,i,overlay,Point(0,0));spr.frames[i].duration=.25
  local frame=base:clone()
  for y=16,31 do for x=0,31 do local p=overlay:getPixel(x,y);if app.pixelColor.rgbaA(p)>0 then frame:drawPixel(x,y,p) end end end
  strip:drawImage(frame,Point(0,(i-1)*32))
 end
 local tag=spr:newTag(1,12);tag.name="Powered / 3 second breathing cycle"
 spr:saveAs(source..name..".aseprite");spr:close()
 local sheet=Sprite(32,384,ColorMode.RGB);sheet.cels[1].image=strip;sheet:saveAs(target..name..".png");sheet:close()
 local meta=assert(io.open(target..name..".png.mcmeta","w"));meta:write('{"animation":{"width":32,"height":32,"frametime":5,"interpolate":true}}\n');meta:close()
end end
print("85 powered atlases: 12 editable frames, 3-second smooth pulse; idle/gold/stone unchanged.")

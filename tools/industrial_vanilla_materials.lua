-- Preserve Minecraft's exact 16px silhouettes; recolor materials and animate grass tips.
local root=assert(app.params.root):gsub("\\","/").."/"
local vanilla=(app.params.vanilla or (root.."temp/industrial-vanilla")):gsub("\\","/").."/"
local output=root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/"
local source=root.."art/industry-refresh/"
local function rgb(h)return tonumber(h:sub(1,2),16),tonumber(h:sub(3,4),16),tonumber(h:sub(5,6),16)end
local palettes={copper={"593431","be7954","efba81"},diamond={"124a50","42c8c1","c8ffff"},lead={"292d40","697288","c0c8d4"},uranium={"2b3929","8da752","dfed98"},titanium={"303c52","90a6bd","e3ecf2"},terra={"131e29","3e596e","99b8ca"},spacetime={"291d45","8b65bc","e4c6f4"},magic={"342348","9873c2","e2cafa"},coolant={"28566a","70cbd2","c6f4f3"}}
local function tone(p,l,a)
 local t=math.max(0,math.min(1,(l-35)/220));local idx=t<.5 and 1 or 2;local f=t<.5 and t*2 or (t-.5)*2
 local r,g,b=rgb(p[idx]);local R,G,B=rgb(p[idx+1]);return app.pixelColor.rgba(math.floor(r+(R-r)*f+.5),math.floor(g+(G-g)*f+.5),math.floor(b+(B-b)*f+.5),a)
end
local function magic_grass()
 local spr=app.open(vanilla.."short_grass.png");app.activeSprite=spr
 assert(spr.width==16 and spr.height==16,"Magic grass requires the vanilla 16x16 short_grass.png")
 app.command.ChangePixelFormat{format="rgb"};spr.layers[1].name="01 / vanilla short grass"
 local base=Image(16,16,ColorMode.RGB);base:drawSprite(spr,1)
 for i=2,12 do spr:newFrame(#spr.frames) end
 local lights=spr:newLayer();lights.name="02 / blue tip breathing"
 local strip=Image(16,192,ColorMode.RGB)
 for i=1,12 do
  local overlay=Image(16,16,ColorMode.RGB)
  for y=0,5 do for x=0,15 do
   local p=base:getPixel(x,y);local a=app.pixelColor.rgbaA(p)
   if a>0 then
    local shade=.72+.28*app.pixelColor.rgbaR(p)/255
    local pulse=(1-math.cos((i-1)*math.pi/6+x*.18-y*.3))/2
    overlay:drawPixel(x,y,app.pixelColor.rgba(math.floor((38+132*pulse)*shade+.5),math.floor((132+110*pulse)*shade+.5),math.floor((214+41*pulse)*shade+.5),a))
   end
  end end
  spr:newCel(lights,i,overlay,Point(0,0));spr.frames[i].duration=.25
  local frame=base:clone();frame:drawImage(overlay,Point(0,0));strip:drawImage(frame,Point(0,(i-1)*16))
 end
 local tag=spr:newTag(1,12);tag.name="Blue tips / 3 second breathing cycle"
 spr:saveAs(source.."block/magic_grass.aseprite");spr:close()
 local sheet=Sprite(16,192,ColorMode.RGB);sheet.cels[1].image=strip
 sheet:saveAs(output.."block/magic_grass.png");sheet:close()
 local meta=assert(io.open(output.."block/magic_grass.png.mcmeta","w"))
 meta:write('{"animation":{"width":16,"height":16,"frametime":5,"interpolate":true}}\n');meta:close()
 print("Magic grass: 12 frames at 16x16; blue upper four visible pixel rows; 3-second breathing cycle.")
end
magic_grass()
if app.params.only=="magic_grass" then return end
local function recolor(category,name,template,palette,mask)
 local spr=app.open(vanilla..template..".png");app.activeSprite=spr;app.command.ChangePixelFormat{format="rgb"};spr.layers[1].name="01 / vanilla silhouette"
 local base=Image(spr.width,spr.height,ColorMode.RGB);base:drawSprite(spr,1)
 local overlay=Image(spr.width,spr.height,ColorMode.RGB)
 for y=0,spr.height-1 do for x=0,spr.width-1 do
  local p=base:getPixel(x,y);local r,g,b,a=app.pixelColor.rgbaR(p),app.pixelColor.rgbaG(p),app.pixelColor.rgbaB(p),app.pixelColor.rgbaA(p)
  if a>0 and (not mask or mask(r,g,b)) then overlay:drawPixel(x,y,tone(palette,(name=="nuclear_core" or name=="rtg_core") and math.min(255,80+(.2126*r+.7152*g+.0722*b)*1.8) or .2126*r+.7152*g+.0722*b,a)) end
 end end
 local layer=spr:newLayer();layer.name="02 / material tint";spr:newCel(layer,1,overlay,Point(0,0))
 spr:saveAs(source..category.."/"..name..".aseprite");spr:saveCopyAs(output..category.."/"..name..".png");spr:close()
end
for _,name in ipairs({"lead","uranium","titanium","terra","spacetime"}) do recolor("item",name.."_ingot","iron_ingot",palettes[name]) end
for _,name in ipairs({"lead","uranium","titanium"}) do recolor("item","raw_"..name,"raw_iron",palettes[name]) end
for _,name in ipairs({"lead","uranium","titanium","magic"}) do
 recolor("block",name=="magic" and "magic_ore" or name.."_ore","iron_ore",palettes[name],function(r,g,b)return r>g+5 and g>b+3 end)
end
recolor("item","coolant_bucket","water_bucket",palettes.coolant,function(r,g,b)return b>r*1.25 and b>g*1.05 end)
-- Components retain the established tier crystal palette; cores have functional centers.
for tier=1,5 do
 local spr=Sprite(16,16,ColorMode.RGB);spr.layers[1].name="01 / tier component"
 local image=spr.cels[1].image:clone();local palette=palettes[({"copper","diamond","titanium","terra","spacetime"})[tier]]
 for y=3,12 do for x=3,12 do if x<=4 or x>=11 or y<=4 or y>=11 then image:drawPixel(x,y,tone(palette,160,255)) end end end
 for y=5,10 do for x=5,10 do if math.abs(x-7.5)+math.abs(y-7.5)<3.8 then image:drawPixel(x,y,tone(palette,220-math.abs(x-6)*18,255)) end end end
 spr.cels[1].image=image;spr:saveAs(source.."item/industrial_upgrade_"..tier..".aseprite");spr:saveCopyAs(output.."item/industrial_upgrade_"..tier..".png");spr:close()
end
for _,entry in ipairs({{"nuclear_core","cauldron"},{"rtg_core","redstone"}}) do
 recolor("item",entry[1],entry[2],entry[1]=="nuclear_core" and palettes.uranium or palettes.lead)
end
print("Vanilla-shaped resources: five ingots, three raw ores, four ore blocks, coolant bucket and seven components/cores.")

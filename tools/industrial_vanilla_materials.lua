-- Preserve Minecraft's exact 16px silhouettes; change only material/ore/liquid pixels.
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

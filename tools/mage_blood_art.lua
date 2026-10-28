-- Original 32px equipment art. Run with loadfile(...)(projectRoot).
local root=(... or ".").."/"
local spr=Sprite(32,32,ColorMode.RGB)
local layer=spr.layers[1];layer.name="暗紫法师布料与轮廓"
local img=spr.cels[1].image:clone()
local function color(hex) return app.pixelColor.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),255) end
local function pixel(x,y,c) img:drawPixel(x,y,color(c)) end
local function rect(x1,y1,x2,y2,c) for y=y1,y2 do for x=x1,x2 do pixel(x,y,c) end end end
-- Preserve the established two-leg silhouette, with a split tabard and high cuffs.
for y=5,27 do
 local left=y<10 and 9 or y<18 and 8 or 7
 local right=31-left
 for x=left,right do
  local gap=y>14 and x>=14 and x<=17
  if not gap then
   local edge=x==left or x==right or y==27 or y==5 or y>14 and (x==13 or x==18)
   pixel(x,y,edge and "21172d" or x<15 and "4c316b" or "30213f")
  end
 end
end
rect(10,7,12,13,"745693");rect(9,14,11,21,"674782");rect(8,22,10,25,"5d3e77")
rect(19,8,21,12,"50395f");rect(20,14,22,20,"4c355d");rect(21,21,23,25,"443052")
rect(12,11,13,14,"3b2655");rect(18,12,19,16,"251b36")
spr.cels[1].image=img
layer=spr:newLayer();layer.name="金色条纹与法师符文"
local cel=spr:newCel(layer,1,Image(32,32,ColorMode.RGB),Point(0,0));img=cel.image:clone()
rect(9,5,22,5,"f4d27d");rect(9,6,22,6,"86602d");rect(13,5,17,7,"c49b4e");pixel(15,6,"ffe9ae")
-- Long parallel woven stripes and gold-banded cuffs, rather than plate armor.
for y=9,24 do
 local x=y<17 and 10 or 9
 pixel(x,y,"c49b4e");pixel(x+11+(y>=17 and 2 or 0),y,"a57b3e")
 if y%4==1 then pixel(x,y,"f4d27d") end
end
rect(8,25,13,25,"e6bb66");rect(18,25,23,25,"c49b4e")
rect(8,26,13,26,"86602d");rect(18,26,23,26,"71532f")
-- Small angular runes at each knee; amethyst set into the wide belt.
pixel(12,17,"f4d27d");pixel(11,18,"c49b4e");pixel(12,19,"f4d27d");pixel(11,20,"c49b4e")
pixel(19,17,"c49b4e");pixel(20,18,"d9b365");pixel(19,19,"e6bb66");pixel(20,20,"a57b3e")
pixel(15,7,"e8daff");pixel(14,8,"8661b9");pixel(15,8,"b58fe9");pixel(16,8,"69468f");pixel(15,9,"4c306a")
cel.image=img
spr:saveAs(root.."art/new/equipment/mage_blood_legs.aseprite")
spr:saveCopyAs(root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/item/armor/mage_blood_legs.png")
print("Saved mage_blood_legs: 32x32, 2 layers, PNG and Aseprite")
spr:close()

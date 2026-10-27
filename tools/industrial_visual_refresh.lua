-- Aseprite batch redraw; run with --script-param root=<checkout> --script this-file.
-- Original pixel art. Online assets are visual references only (see art source README).
local root=assert(app.params.root,"Pass --script-param root=<checkout>"):gsub("\\","/").."/"
local out=root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/"
local src=root.."art/industry-refresh/"
local S=32
local palettes={
 {"262335","494351","a47c52","ead0a0","eeb76e"},
 {"193344","365b6c","81b9cd","d9f5f4","6adbed"},
 {"303444","626e80","a4b7c7","e3eaf1","b6cfe8"},
 {"131f2b","293f50","536c80","b5d1dc","6896b3"},
 {"241b3b","4b3466","9182cf","e6c6f3","9ddfba"}}
local sprite,img,cel
local function c(h) return app.pixelColor.rgba(tonumber(h:sub(1,2),16),tonumber(h:sub(3,4),16),tonumber(h:sub(5,6),16),255) end
local function px(x,y,h) if x>=0 and y>=0 and x<S and y<S then img:drawPixel(x,y,c(h)) end end
local function box(x,y,w,h,col) for yy=y,y+h-1 do for xx=x,x+w-1 do px(xx,yy,col) end end end
local function line(x,y,X,Y,col)
 local n=math.max(math.abs(X-x),math.abs(Y-y));if n==0 then px(x,y,col);return end
 for i=0,n do px(math.floor(x+(X-x)*i/n+.5),math.floor(y+(Y-y)*i/n+.5),col) end
end
local function ring(x,y,r,col)
 for yy=-r,r do for xx=-r,r do local d=xx*xx+yy*yy;if d<=r*r and d>=(r-1.6)*(r-1.6) then px(x+xx,y+yy,col) end end end
end
local function layer(name)
 cel.image=img
 local l=sprite:newLayer();l.name=name
 cel=sprite:newCel(l,1,Image(S,S,ColorMode.RGB),Point(0,0));img=cel.image:clone()
end
local function start()
 sprite=Sprite(S,S,ColorMode.RGB);sprite.layers[1].name="01 / silhouette"
 cel=sprite.cels[1];img=cel.image:clone()
end
local function finish(category,name)
 cel.image=img
 sprite:saveAs(src..category.."/"..name..".aseprite")
 sprite:saveCopyAs(out..category.."/"..name..".png");sprite:close()
end
local function plate(t)
 local p=palettes[t];box(0,0,32,32,p[1]);box(1,1,30,30,p[2]);box(3,3,26,26,p[1]);box(4,4,24,24,p[2])
 line(1,1,30,1,p[3]);line(1,2,1,30,p[3]);line(4,4,27,4,p[3]);line(4,5,4,27,p[3])
 line(2,30,30,30,"121b27");line(30,2,30,29,"121b27")
 layer("02 / rivets and panel seams")
 for _,v in ipairs({{2,2},{28,2},{2,28},{28,28}}) do box(v[1],v[2],2,2,p[1]);px(v[1],v[2],p[4]) end
 -- Short seams and intentional sparse wear; no random all-over noise.
 line(6,25,11,25,p[3]);line(23,6,26,6,p[3]);px(25,7,p[1])
end
for t=1,5 do
 local p=palettes[t]
 start();plate(t);layer("03 / recessed heat sink")
 box(7,8,18,14,p[1]);for y=9,20,3 do line(8,y,22,y,"101b28");line(9,y+1,23,y+1,p[3]) end
 box(5,23,4,3,"b17b4f");box(22,23,4,3,"b17b4f");line(9,24,21,24,p[1])
 layer("04 / tier inlays");for i=1,t do box(7+(i-1)*4,5,2,1,p[5]) end
 finish("block","machine_side_"..t)
 start();plate(t);layer("03 / service hatch")
 box(8,8,16,16,p[1]);box(10,10,12,12,p[2]);ring(15,15,6,p[3]);box(14,13,3,5,p[5])
 for i=1,t do box(6+(i-1)*4,26,2,1,p[5]) end
 finish("block","machine_top_"..t)
end
local families={"mana_crusher","mana_furnace","mineral_generator","currency_fabricator","mana_forge","mana_beacon","coal_generator","solar_collector","turbine_generator","wind_generator","thermal_generator","nuclear_reactor","rtg_generator","coolant_mixer","transfer_crystal","harvest_crystal"}
local accents={mana_furnace="eea060",mana_forge="e4ac74",coal_generator="e7a567",thermal_generator="e3a261",nuclear_reactor="a7dc73",rtg_generator="b8cb79",coolant_mixer="6edce7",harvest_crystal="7de4a1",currency_fabricator="e5c47b",transfer_crystal="ba9aea"}
local function mechanics(name,glow,top)
 local dark="16212f";local steel="91a6b6";local hi="c8dce5";local copper="b87c55"
 if name=="mana_crusher" then
  box(6,6,20,5,dark);line(7,6,24,6,steel)
  for x=7,23,4 do box(x,12,2,10,steel);box(x+1,13,2,8,"4c6375");px(x,12,hi) end
  box(7,23,18,3,dark);for x=10,22,5 do px(x,24,glow) end
 elseif name=="mana_furnace" or name=="coal_generator" or name=="mana_forge" then
  box(7,6,18,19,dark);box(9,8,14,14,"624c43");box(10,12,12,9,glow)
  for x=10,22,4 do line(x,9,x,21,steel) end
  line(8,7,23,7,hi);box(7,23,18,3,"1b2632");for x=8,22,3 do px(x,24,copper) end
  if name=="mana_forge" then box(12,6,8,4,steel);line(13,6,18,6,hi) end
  if name=="coal_generator" then box(6,5,4,8,steel);box(7,6,2,5,dark) end
 elseif name=="mineral_generator" then
  ring(15,15,10,dark);ring(15,15,8,steel);ring(15,15,6,glow)
  line(10,10,20,20,hi);line(10,20,20,10,steel);box(13,13,5,5,dark);box(14,14,3,3,glow)
 elseif name=="currency_fabricator" then
  box(7,6,18,19,dark);ring(15,13,6,copper);ring(15,13,4,glow);line(15,9,15,16,hi)
  box(10,21,13,3,steel);line(11,21,21,21,hi);box(12,23,9,1,dark)
 elseif name=="solar_collector" then
  box(5,5,22,21,dark)
  for y=6,22,5 do for x=6,22,5 do box(x,y,4,4,"28527b");line(x,y,x+3,y,"589ac3");px(x,y+1,"88c8df") end end
  line(6,26,26,26,copper)
 elseif name=="turbine_generator" or name=="wind_generator" then
  ring(15,15,10,dark);ring(15,15,9,steel)
  for a=0,3 do local r=a*math.pi/2;local x=math.floor(15+math.cos(r)*7);local y=math.floor(15+math.sin(r)*7);line(15,15,x,y,hi);line(16,15,x+1,y,steel) end
  ring(15,15,3,copper);box(14,14,3,3,glow)
  if name=="wind_generator" then line(6,26,25,26,steel);box(13,24,5,3,dark) end
 elseif name=="thermal_generator" then
  for x=7,22,5 do box(x,6,3,18,steel);line(x,7,x,22,hi) end
  line(7,9,24,9,copper);line(7,21,24,21,"5595b7");box(13,12,6,6,dark);box(15,13,2,4,glow)
 elseif name=="nuclear_reactor" then
  box(7,6,18,19,dark);ring(15,15,9,steel);ring(15,15,6,glow);ring(15,15,3,hi)
  for y=8,23,5 do box(6,y,2,2,copper);box(24,y,2,2,copper) end
 elseif name=="rtg_generator" then
  box(10,6,12,19,dark);box(13,8,6,15,glow)
  for y=7,24,3 do line(7,y,24,y,steel);line(9,y+1,22,y+1,"50667a") end
  line(14,7,14,23,hi)
 elseif name=="coolant_mixer" then
  for _,x in ipairs({7,19}) do box(x,6,6,18,dark);box(x+1,9,4,13,glow);line(x+1,8,x+3,8,hi);box(x,6,6,2,steel);box(x,23,6,2,steel) end
  line(10,26,22,26,copper);box(14,13,4,6,steel);px(15,14,hi)
 elseif name=="mana_beacon" then
  ring(15,15,10,dark);ring(15,15,8,copper);ring(15,15,5,glow)
  for _,v in ipairs({{6,6},{22,6},{6,22},{22,22}}) do box(v[1],v[2],3,3,steel);px(v[1],v[2],hi) end
 else
  ring(15,15,10,dark);ring(15,15,8,steel);ring(15,15,5,glow)
  if name=="transfer_crystal" then line(9,15,22,15,hi);line(18,11,22,15,hi);line(18,19,22,15,hi)
  else line(15,22,15,9,hi);line(15,15,10,10,hi);line(15,17,21,11,hi) end
 end
end
for _,name in ipairs(families) do
 for _,state in ipairs({"","_linked","_active"}) do
  start();plate(1);layer("03 / "..name.." mechanism")
  local accent=accents[name] or "6bcdd9"
  local glow=state=="_active" and accent or state=="_linked" and "5e9ca7" or "405565"
  mechanics(name,glow,false)
  layer("04 / power and activity indicators")
  box(26,9,2,7,"16212f");box(26,10,2,2,state=="" and "606c77" or "7de6ac")
  box(26,14,2,2,state=="_active" and "fff0bb" or "354653")
  finish("block",name..state)
 end
 start();plate(1);layer("03 / top mechanism");mechanics(name,accents[name] or "6bcdd9",true);finish("block",name.."_top")
end
-- Facet atlas: ten genuinely distinct colours; face UVs pick one of eight strips.
local gems={"8ad8f1","49c5eb","82a7f2","a283ed","d2b2ef","dd8eda","71dcbb","50c9a5","e3b96a","df83c1"}
local function shade(h,k)
 local r=math.min(255,math.floor(tonumber(h:sub(1,2),16)*k));local g=math.min(255,math.floor(tonumber(h:sub(3,4),16)*k));local b=math.min(255,math.floor(tonumber(h:sub(5,6),16)*k));return string.format("%02x%02x%02x",r,g,b)
end
for t,h in ipairs(gems) do
 start();layer("02 / eight facet bands")
 for i=0,7 do
  local base=shade(h,({1.12,.88,.67,.78,1.04,.8,.62,.94})[i+1]);box(i*4,0,4,32,base)
  line(i*4,0,i*4,31,shade(h,math.min(1.24,1.12-i*.035)))
  line(i*4+1,5,i*4+1,11,shade(h,1.3));line(i*4+2,22,i*4+2,25,shade(h,.7))
 end
 finish("block","energy_crystal_facets_"..t)
end
-- Material blocks: broad crystalline facets, independently drawn from the DNF identity
-- references (Terranite slate-blue, Magtonium scarlet, Terranium violet/blue/green).
local function poly(points,col)
 for y=0,31 do
  local hits={}
  for i,a in ipairs(points) do local b=points[i%#points+1]
   if (a[2]<=y and b[2]>y) or (b[2]<=y and a[2]>y) then hits[#hits+1]=a[1]+(y-a[2])*(b[1]-a[1])/(b[2]-a[2]) end
  end
  table.sort(hits);for i=1,#hits-1,2 do for x=math.ceil(hits[i]),math.floor(hits[i+1]) do px(x,y,col) end end
 end
end
for t=1,5 do
 local p=palettes[t];start();box(0,0,32,8,p[2]);box(0,8,32,8,p[3]);box(0,16,32,8,p[5]);box(0,24,32,8,p[1])
 layer("02 / bevel and chased metal");line(0,1,31,1,p[3]);line(0,9,31,9,p[4]);line(0,17,31,17,p[4]);line(0,25,31,25,p[2])
 for x=3,31,8 do px(x,12,p[4]);px(x+1,13,p[1]);line(x,20,x+3,20,p[4]) end
 finish("block","industrial_trim_"..t)
end
local stones={
 terra_block={"17232d","344d60","59788e","a0bcca","243a49","6c94a8"},
 spacetime_block={"201735","50366f","8463b5","e9cafa","398b9b","62c89d"},
 industrial_magic_stone_block={"351623","791e29","cb342d","ffe08a","f16f28","a01f32"},
 energy_crystal_block_2={"152c48","235f99","49a8d0","d1f4fc","637dce","80dce7"}}
local facets={{{0,0},{18,0},{12,10},{0,15}},{{18,0},{31,0},{31,16},{22,12},{12,10}},{{0,15},{12,10},{15,21},{4,31},{0,31}},{{12,10},{22,12},{27,25},{15,21}},{{22,12},{31,16},{31,31},{27,25}},{{15,21},{27,25},{31,31},{4,31}}}
for name,p in pairs(stones) do
 start();box(0,0,32,32,p[1]);layer("02 / large crystal facets")
 for i,points in ipairs(facets) do poly(points,p[i]) end
 layer("03 / mineral cleavages");line(1,14,12,10,p[4]);line(13,11,16,20,p[3]);line(22,13,26,24,p[4]);line(5,30,14,22,p[2])
 line(1,1,17,1,p[3]);line(1,2,1,12,p[3]);px(11,9,p[4]);px(12,8,p[4]);px(12,9,p[4]);px(13,9,p[4])
 if name=="spacetime_block" then poly({{23,2},{30,4},{29,12},{25,10}},"58bdaf");line(25,3,28,4,"a2f4cf");poly({{2,20},{6,17},{8,23},{4,26}},"3f73b8") end
 if name=="industrial_magic_stone_block" then line(14,13,18,18,"ffb94c");line(18,18,20,17,"ffe5a0");line(19,19,20,23,"f67e35") end
 layer("04 / polished crystal heart")
 if name~="terra_block" then
  poly({{8,4},{23,4},{28,12},{27,23},{20,28},{8,26},{3,18},{4,10}},p[1])
  poly({{8,5},{22,5},{17,12},{10,13},{4,17},{5,10}},p[3])
  poly({{22,5},{27,12},{23,20},{17,16},{17,12}},p[2])
  poly({{4,17},{10,13},{17,16},{15,23},{8,25}},p[2])
  poly({{17,16},{23,20},{20,27},{15,23}},p[5])
  poly({{10,13},{17,12},{20,16},{15,21},{11,19}},p[3])
  line(8,5,20,5,p[4]);line(5,10,5,15,p[4]);line(11,13,16,12,p[4]);line(16,13,18,15,p[4]);line(23,21,21,25,p[3])
  if name=="spacetime_block" then poly({{20,7},{24,10},{21,14},{18,12}},"5ba8c4");poly({{18,18},{23,21},{20,25},{16,22}},"5dc8a4");line(19,19,22,21,"a0efd1") end
  if name=="industrial_magic_stone_block" then poly({{12,18},{16,8},{19,17},{22,12},{21,22},{15,26}},"ee7032");poly({{14,19},{16,13},{18,19},{17,23}},"ffd66b");line(15,17,16,14,"fff1b3") end
 else
  for _,v in ipairs({{5,3,13,16},{15,1,21,12},{13,17,24,28}}) do line(v[1],v[2],v[3],v[4],p[4]);line(v[1]+1,v[2],v[3]+1,v[4],p[2]) end
 end
 finish("block",name)
end
local metals={lead={"252a39","586174","8995a9"},uranium={"273025","6b8245","c4dc7b"},titanium={"30394c","7d93ab","d9e4ed"}}
for name,p in pairs(metals) do
 start();box(0,0,32,32,p[1]);box(1,1,30,30,p[2]);layer("02 / cast plate seams");line(1,1,30,1,p[3]);line(1,2,1,30,p[3]);line(4,6,25,6,p[1]);line(6,25,27,25,p[3]);line(25,6,25,23,p[1]);finish("block",name.."_block")
 start();box(0,0,32,32,"686778");for y=0,31,5 do line(0,y,31,y+3,"565766") end;layer("02 / ore in stone")
 for _,v in ipairs({{3,5},{17,2},{10,19},{23,22}}) do poly({{v[1],v[2]+2},{v[1]+4,v[2]},{v[1]+7,v[2]+4},{v[1]+4,v[2]+7},{v[1],v[2]+5}},p[2]);line(v[1]+1,v[2]+2,v[1]+4,v[2]+1,p[3]);line(v[1]+5,v[2]+3,v[1]+5,v[2]+5,p[1]) end
 finish("block",name.."_ore")
end
start();box(0,0,32,32,"49434f");for y=0,31,6 do line(0,y,31,y+3,"36343f") end;layer("02 / magenta crystal inclusions")
for _,v in ipairs({{3,4},{16,1},{10,17},{23,22}}) do poly({{v[1],v[2]+5},{v[1]+3,v[2]},{v[1]+6,v[2]+3},{v[1]+5,v[2]+8}},"8d6bb8");line(v[1]+2,v[2]+4,v[1]+3,v[2]+1,"c5b2eb");px(v[1]+4,v[2]+5,"729ec9") end
finish("block","magic_ore")
start();layer("02 / arcane grass")
for _,v in ipairs({{5,30,2,13},{9,30,11,5},{16,30,14,2},{22,30,27,8},{26,30,30,17}}) do line(v[1],v[2],v[3],v[4],"356c6b");line(v[1]+1,v[2],v[3]+1,v[4],"77b6a2");box(v[3],v[4],2,3,"b7a4de") end
finish("block","magic_grass")
local resourceColors={lead={"292d40","697288","c0c8d4"},uranium={"2b3929","8da752","dfed98"},titanium={"303c52","90a6bd","e3ecf2"},terra={"131e29","3e596e","99b8ca"},spacetime={"291d45","8b65bc","e4c6f4"},iron={"3e4656","97a8b6","deeaed"},copper={"593431","be7954","efba81"},gold={"66421e","d5a641","fff0a0"}}
local function chunk(p,cx,cy,w)
 poly({{cx-w,cy},{cx-1,cy-w},{cx+w,cy-2},{cx+w-1,cy+w},{cx-w+2,cy+w+1}},p[1]);poly({{cx-w+1,cy},{cx-1,cy-w+1},{cx+2,cy-1},{cx,cy+w}},p[2]);line(cx-w+2,cy,cx-1,cy-w+2,p[3])
end
for name,p in pairs(resourceColors) do
 if name~="iron" and name~="copper" and name~="gold" then
  start();layer("02 / refined ingot")
  poly({{5,13},{21,9},{28,15},{27,22},{11,27},{4,21}},p[1]);poly({{5,13},{21,9},{28,15},{11,20}},p[2]);line(6,13,21,10,p[3]);line(6,14,11,18,p[3]);line(12,21,26,17,p[2])
  if name=="spacetime" then line(16,13,22,12,"75c7bc");line(18,16,25,14,"628ecb") end
  finish("item",name.."_ingot")
  if metals[name] then start();layer("02 / raw ore");chunk(p,12,16,8);chunk(p,23,22,5);finish("item","raw_"..name) end
 end
 if name~="terra" and name~="spacetime" then
  start();layer("02 / crushed fragments");chunk(p,9,13,5);chunk(p,22,10,4);chunk(p,18,23,6);px(5,25,p[2]);px(26,19,p[3]);finish("item","crushed_"..name)
 end
end
for tier=1,10 do
 start();local h=gems[tier];layer("02 / crystal silhouette");poly({{16,1},{24,10},{23,23},{15,30},{7,23},{8,9}},shade(h,.48));poly({{16,3},{16,26},{9,21},{10,10}},shade(h,1.12));poly({{17,4},{22,11},{21,22},{16,27}},shade(h,.8));line(12,8,11,16,shade(h,1.4));layer("03 / rank notches");for i=1,tier do px(5+(i-1)%5*5,29-math.floor((i-1)/5)*2,h) end;finish("item","energy_crystal_"..tier)
end
for t=1,5 do
 local p=palettes[t];start();layer("02 / hollow frame")
 for _,v in ipairs({{4,5,24,4},{4,23,24,4},{4,9,4,14},{24,9,4,14}}) do box(v[1],v[2],v[3],v[4],p[2]);line(v[1],v[2],v[1]+v[3]-1,v[2],p[3]) end
 layer("03 / distinct tier crystal");poly({{16,9},{20,15},{16,22},{12,15}},p[5]);px(15,13,p[4]);for i=1,t do box(7+(i-1)*4,25,2,1,p[4]) end;finish("item","industrial_upgrade_"..t)
end
start();layer("02 / scarlet Magtonium cluster")
poly({{4,21},{6,8},{11,4},{15,17},{21,3},{26,9},{24,24},{15,29}},"68202a");poly({{5,20},{7,9},{10,6},{13,19},{12,25}},"d8452f");poly({{14,22},{21,5},{24,9},{21,24}},"ef7936");poly({{10,22},{15,15},{19,24},{15,27}},"ffb94b");line(8,10,8,17,"ffe6a0");line(20,9,17,16,"ffe5a0");finish("item","industrial_magic_stone")
start();layer("02 / sulphide chunks");chunk({"484323","a69c4f","f2d97b"},12,17,8);chunk({"484323","a69c4f","f2d97b"},24,23,5);finish("item","sulfide")
start();layer("02 / brass wand");line(5,29,24,9,"6b493d");line(6,29,25,9,"c89861");line(8,26,23,11,"f1d4a0");layer("03 / violet focus");poly({{21,2},{28,5},{28,13},{22,16},{17,10}},"50356d");poly({{22,3},{25,6},{22,13},{19,9}},"b49be3");line(21,5,20,9,"ece4fa");finish("item","mana_wand")
start();layer("02 / bucket and handle");line(6,10,8,3,"73869a");line(8,3,23,3,"bacbd5");line(23,3,26,10,"73869a");poly({{5,11},{27,11},{24,28},{9,28}},"43566a");poly({{7,12},{25,12},{22,26},{11,26}},"9cb6c4");layer("03 / liquid surface");box(7,11,18,5,"4cabc2");line(9,11,23,11,"b8efec");line(10,19,11,24,"d2e6e8");finish("item","coolant_bucket")
start();plate(1);layer("03 / equipment manufacturing tooling");mechanics("currency_fabricator","bf9bdc",false);finish("block","fabricator_side")
start();plate(1);layer("03 / crafting surface");box(7,7,18,18,"302c40");for x=9,23,6 do line(x,8,x,23,"9a7856") end;for y=9,23,6 do line(8,y,23,y,"9a7856") end;finish("block","fabricator_top")
print("Industrial V1: original 32px layered sources exported.")

-- Vanilla silhouettes are the final resource layer, including on full rebuilds.
dofile(root.."tools/industrial_vanilla_materials.lua")

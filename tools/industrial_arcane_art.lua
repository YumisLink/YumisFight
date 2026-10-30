-- V2: original 32px device UV atlases at 16 texels/world block; material blocks 16px.
local root=assert(app.params.root):gsub("\\","/").."/"
local source=root.."art/industry-arcane/"
local target=root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/block/"
local spr,cel,img,S
local function rgba(h) return app.pixelColor.rgba(tonumber(h:sub(1,2),16),tonumber(h:sub(3,4),16),tonumber(h:sub(5,6),16),255) end
local function px(x,y,h) if x>=0 and y>=0 and x<S and y<S then img:drawPixel(x,y,rgba(h)) end end
local function rect(x,y,w,h,col) for yy=y,y+h-1 do for xx=x,x+w-1 do px(xx,yy,col) end end end
local function line(x,y,X,Y,col) local n=math.max(math.abs(X-x),math.abs(Y-y));if n==0 then px(x,y,col);return end;for i=0,n do px(math.floor(x+(X-x)*i/n+.5),math.floor(y+(Y-y)*i/n+.5),col) end end
local function layer(name) cel.image=img;local l=spr:newLayer();l.name=name;cel=spr:newCel(l,1,Image(S,S,ColorMode.RGB),Point(0,0));img=cel.image:clone() end
local function begin(size) S=size;spr=Sprite(S,S,ColorMode.RGB);spr.layers[1].name="01 / silhouette";cel=spr.cels[1];img=cel.image:clone() end
local function finish(name) cel.image=img;spr:saveAs(source..name..".aseprite");spr:saveCopyAs(target..name..".png");spr:close() end
local tierStone={
 {"0b1018","151c26","232d39","465261","87929e"},
 {"19494e","236c72","36989a","70cfcf","c4f6ed"},
 {"0a2157","123475","1d4a98","3167c5","6799ee"},
 {"081328","101e3b","182c50","253c65","4c6287"},
 {"312b4d","382d50","413356","4b3b5d","684b6d"}
}
local gold={"8e6c30","b49c3b","dcc545","e2d35e","ede574","fffd9c"}
local familyNames={"mana_crusher","mana_furnace","mineral_generator","currency_fabricator","mana_forge","mana_beacon","coal_generator","solar_collector","turbine_generator","wind_generator","thermal_generator","nuclear_reactor","rtg_generator","coolant_mixer","transfer_crystal","harvest_crystal","equipment_fabricator"}
local coreColors={mana_furnace={"713344","b55a42","f2a565","ffe2a6"},coal_generator={"603244","a34e43","e49a63","f5d599"},thermal_generator={"494278","8e7dc5","c5b0ed","eee1ff"},nuclear_reactor={"385749","789e67","bbd88a","f0f5bb"},coolant_mixer={"34566d","6099bb","9bdbde","e8fcf3"},harvest_crystal={"375e54","6eaa83","b7dca4","f0f2c0"}}
local defaultCore={"5d3a9a","8d6acc","a678f1","e2bfff"}
local tierCores={defaultCore,{"285770","599daf","8ed7e0","e3fbf5"},{"4d516c","9299b7","c2c9dc","f1ebf4"},{"23384b","496179","7e9fb3","b9d0da"},{"4b3577","8f6abc","c39ee7","c9efd9"}}
local glyphs={
 mana_crusher={"#...#",".#.#.","..#..",".#.#.","#...#"},mana_furnace={"..#..",".##..",".###.","#####",".###."},
 mineral_generator={"..#..",".###.","#####",".###.","..#.."},currency_fabricator={".###.","##..#","#.#.#","#..##",".###."},
 mana_forge={"#####","..#..",".###.","#####",".###."},mana_beacon={"..#..",".#.#.","#.#.#",".#.#.","..#.."},
 wind_generator={"..#..","..#..",".###.","#.#.#","..#.."},turbine_generator={"..##.","#.#..",".###.","..#.#",".##.."}}
for _,family in ipairs(familyNames) do for tier=1,5 do for _,state in ipairs({"","_linked","_active"}) do
 local stone=tierStone[tier]
 begin(32);layer("02 / tier stone and white stripe inlay")
 for y=0,15 do for x=0,15 do local band=2+((math.floor(x/4)+math.floor(y/3))%3);px(x,y,stone[band]) end end
 line(0,0,15,0,stone[4]);line(0,1,0,15,stone[4]);line(1,15,15,15,stone[1]);line(15,1,15,15,stone[1])
 line(3,5,9,5,stone[1]);line(9,5,12,2,stone[1]);line(7,10,12,10,stone[5]);px(13,9,stone[5])
 if tier==1 then for y=2,13 do for x=2,13 do local stripe=(x+y)%12;if stripe==0 then px(x,y,"e5eaf0") elseif stripe==1 then px(x,y,"aebac7") end end end end
 -- Atlas quadrant 2: gold, quadrant 3: cut crystal, quadrant 4: small rune.
 layer("03 / warm gilded edges")
 for y=0,15 do for x=16,31 do px(x,y,gold[2+((x+math.floor(y/4))%3)]) end end
 line(16,0,31,0,gold[6]);line(16,1,16,15,gold[5]);line(17,15,31,15,gold[1]);line(31,1,31,15,gold[1])
 for _,p in ipairs({{19,3},{26,7},{22,12}}) do px(p[1],p[2],gold[6]);px(p[1]+1,p[2]+1,gold[1]) end
 layer("04 / crystal and state rune")
 local core=coreColors[family] or tierCores[tier]
 for y=16,31 do for x=0,15 do local index=(x<5 and 3 or x<10 and 2 or 1);if y<20 then index=math.min(4,index+1) end;px(x,y,core[index]) end end
 line(1,18,4,16,core[4]);line(1,19,1,25,core[4]);line(6,23,8,21,core[3]);line(11,29,14,26,core[2])
 rect(16,16,16,16,stone[1]);local ink=state=="_active" and core[4] or state=="_linked" and core[3] or core[2]
 local glyph=glyphs[family] or {"..#..",".#.#.","#...#",".#.#.","..#.."}
 for y,row in ipairs(glyph) do for x=1,5 do if row:sub(x,x)=="#" then px(16+x,16+y,ink) end end end
 line(16,24,31,24,gold[3]);line(16,25,16,31,gold[4]);for i=1,tier do px(17+(i-1)*3,27,gold[6]);px(17+(i-1)*3,28,core[3]) end
 if tier==2 then rect(26,18,4,4,"84cbd8");px(26,18,"e7fcf8")
 elseif tier==3 then rect(26,18,4,4,"b1b7c9");px(26,18,"f0e6e5")
 elseif tier==4 then rect(26,18,4,4,"36495c");line(26,18,29,21,"8eacbd")
 elseif tier==5 then rect(26,18,4,4,"9a74c6");px(26,18,"d7c9fc");px(29,21,"78c7a6") end
 finish("arcane/"..family.."_"..tier..state)
end end end
-- The moving mesh uses the same atlas layout and colour family.
begin(32);local im=Image{fromFile=target.."arcane/wind_generator_2_active.png"};img:drawImage(im,Point(0,0));finish("arcane/rotor")

-- 16x16 resource blocks: restrained bevel, concentric cleavages and a readable heart.
local materials={
 terra_block={"18232f","304555","4e6579","7992a4","a8c0cb"},
 spacetime_block={"251b40","4a3168","7954a1","b287d6","e2c5f7"},
 industrial_magic_stone_block={"4a1921","83262b","bd3d2d","ed7a35","ffdb7b"},
 energy_crystal_block_2={"203047","365d88","518ebc","86c6e1","d2f4f5"},
 lead_block={"292c3b","45485c","656b80","9299ac","c0c6d1"},
 uranium_block={"2c3529","4b5d3a","7b9350","b0c47a","dfe7a6"},
 titanium_block={"313a4d","526175","7a90a4","b0c1cf","e5e9ed"}}
for name,p in pairs(materials) do
 begin(16);rect(0,0,16,16,p[2]);layer("02 / bevel and crystalline planes")
 for i=0,3 do line(i,i,15-i,i,p[4-i//2]);line(i,i,i,15-i,p[3]);line(i,15-i,15-i,15-i,p[1]);line(15-i,i,15-i,15-i,p[2]) end
 rect(4,4,8,8,p[3]);line(4,4,11,4,p[4]);line(4,5,4,10,p[4]);line(5,11,11,11,p[2]);line(11,5,11,10,p[1])
 layer("03 / individual mineral identity")
 if name=="terra_block" then
  line(4,3,11,11,p[5]);line(5,3,12,11,p[2]);line(3,6,7,13,p[4]);line(9,2,13,6,p[3])
 elseif name=="spacetime_block" then
  rect(6,6,4,4,"9675d0");line(5,8,8,5,"729acb");line(8,10,11,7,"75bcae");px(6,6,p[5]);px(10,9,"abe5c6")
 elseif name=="industrial_magic_stone_block" then
  line(6,11,7,5,p[4]);line(7,5,9,10,p[4]);line(9,10,11,6,p[4]);line(7,8,8,10,p[5]);line(5,10,6,8,p[5])
 elseif name=="energy_crystal_block_2" then
  line(5,8,8,5,p[5]);line(8,5,10,8,p[4]);line(5,8,8,11,p[4]);line(8,11,10,8,p[2]);px(7,7,p[5])
 else
  -- Vanilla-like metal blocks retain broad cast panels, not a camouflage pattern.
  rect(3,3,10,10,p[3]);line(3,3,12,3,p[4]);line(3,4,3,12,p[4]);line(4,12,12,12,p[2]);line(12,4,12,11,p[2]);line(5,5,8,5,p[4])
 end
 finish(name)
end
print("Arcane V2: 32px device atlases, 16px material blocks, original layered Aseprite sources.")

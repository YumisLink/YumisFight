-- Original 16px industrial art. Saved sources retain separate metal, mechanism and rune layers.
local root="C:/GameApp/YumisFight/"
local source=root.."art/new/industry/"
local texture=root.."src/main/resources/assets/yumis_dungeon_and_fight/textures/"
local palette={
 {"242d39","566173","a2b3c5","70d9ed"},{"173b50","337b96","a1dfed","4ad8ff"},
 {"30323e","777d8d","d3dce2","b9a7ee"},{"193d3e","3d9290","b3f4da","6ef4c5"},{"332c50","7664a0","eee1ff","d3afff"}}
local entries={}
math.randomseed(20261027)
local function color(hex,a) return app.pixelColor.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),a or 255) end
local image,spr,cel
local function pixel(x,y,c) if x>=0 and y>=0 and x<16 and y<16 then image:drawPixel(x,y,color(c)) end end
local function rect(x1,y1,x2,y2,c) for y=y1,y2 do for x=x1,x2 do pixel(x,y,c) end end end
local function line(x1,y1,x2,y2,c) local dx,dy=x2-x1,y2-y1;local n=math.max(math.abs(dx),math.abs(dy));if n==0 then pixel(x1,y1,c);return end;for t=0,n do pixel(math.floor(x1+dx*t/n+.5),math.floor(y1+dy*t/n+.5),c) end end
local function layer(name) if cel then cel.image=image end;local l=spr:newLayer();l.name=name;cel=spr:newCel(l,1,Image(16,16,ColorMode.RGB),Point(0,0));image=cel.image:clone() end
local function start() spr=Sprite(16,16,ColorMode.RGB);spr.layers[1].name="底色";cel=spr.cels[1];image=cel.image:clone() end
local function finish(category,name)
 cel.image=image
 spr:saveAs(source..category.."/"..name..".aseprite");spr:saveCopyAs(texture..category.."/"..name..".png")
 entries[#entries+1]={category,name};spr:close()
end
local function metal(tier)
 local p=palette[tier];rect(0,0,15,15,p[1]);rect(1,1,14,14,p[2]);rect(2,2,13,13,p[1]);rect(3,3,12,12,p[2]);line(1,1,14,1,p[3]);line(1,2,1,14,p[3]);line(2,14,14,14,p[1]);line(14,2,14,14,p[1]);
 for _,xy in ipairs({{2,2},{13,2},{2,13},{13,13}}) do pixel(xy[1],xy[2],p[3]) end
end
for tier=1,5 do
 start();metal(tier);layer("接缝与铆钉");local p=palette[tier];for x=5,11,3 do line(x,4,x,11,p[1]);pixel(x,5,p[3]) end;finish("block","machine_side_"..tier)
 start();metal(tier);layer("嵌入水晶");for y=4,11 do local w=math.min(y-3,12-y);for x=8-w,7+w do pixel(x,y,p[4]) end end;line(7,5,7,10,p[3]);pixel(8,4,p[3]);finish("block","machine_top_"..tier)
end
local glyphs={
 mana_crusher={"##...##..","###.###..",".#####...","..###....",".#####...","###.###..","##...##..","...#.....","..###...."},
 mana_furnace={"....#....","...##....","...###...","..####...",".######..",".##.###..",".##..##..","..####...","...##...."},
 mineral_generator={"..#####..",".#######.","##..#..##","....#....","...##....","...#.....","..##.....","..#......",".##......"},
 currency_fabricator={"..#####..",".##...##.","##.###.##","##.#...##","##.###.##","##...#.##","##.###.##",".##...##.","..#####.."},
 mana_forge={".#####...",".#####...","...#.....","...#...#.","...#..###",".#######.","..#####..","...###...","..#####.."},
 mana_beacon={"....#....","...###...","..#####..","...###...","....#....",".........",".#######.","##..#..##","#########"},
 coal_generator={"....#....","...##....","..####...",".######..",".##..##..","..####...",".........",".##..##..","..####..."},
 solar_collector={"....#....",".#..#..#.","..#####..",".##.#.##.",".#######.",".##.#.##.","..#####..",".#..#..#.","....#...."},
 turbine_generator={"..###....","..####...","...###.##","....#####","###.#.###","#####....","##.###...","...####..","....###.."},
 wind_generator={"....#....","...##....","...##....","....#....",".#######.","###.#.###","#...#...#","....#....","....#...."},
 thermal_generator={".#....#..",".##...#..",".###..#..",".##...##.",".##..###.",".###..##.",".##...##.","..#...#..",".###.###."},
 nuclear_reactor={"..#...#..",".#.#.#.#.","#...#...#",".#.###.#.","..#####..",".#.###.#.","#...#...#",".#.#.#.#.","..#...#.."},
 rtg_generator={".#.#.#.#.",".#######.",".#.###.#.",".#.###.#.",".#.###.#.",".#.###.#.",".#.###.#.",".#######.",".#.#.#.#."},
 coolant_mixer={"..#...#..",".###.###.","..#...#..",".........",".#######.",".#.....#.",".#.###.#.","..#####..","...###..."},
 transfer_crystal={".....#...",".....##..","########.",".....##..",".....#...",".........",".######..",".#.##.#..",".######.."},
 harvest_crystal={"....#....",".#..#..#.",".##.#.##.","..#####..",".#.###.#.",".#######.","...###...","....#....","...###..."}}
for name,glyph in pairs(glyphs) do for _,state in ipairs({"","_linked","_active"}) do
 start();metal(1);layer("功能图案");local ink=state=="_active" and "f8e3a4" or state=="_linked" and "76dfdc" or "adbccc";for y,row in ipairs(glyph) do for x=1,#row do if row:sub(x,x)=="#" then pixel(x+2,y+2,ink) end end end
 layer("状态符文");pixel(12,12,state=="" and "394a5b" or "8cffb3");if state=="_active" then pixel(3,12,"ffcc6d");pixel(12,3,"fff2c6") end;finish("block",name..state)
end end
local colors={iron={"39464f","9cabb1","e1eef0"},copper={"603c32","c98058","f2bb83"},gold={"6e4a1d","d6a93c","fff1a1"},lead={"333344","71778b","b6bfd1"},uranium={"35452b","8da752","d5f588"},titanium={"404255","949eaf","e5e8f7"},terra={"1c5350","53b99f","b9ffe0"},spacetime={"423260","9d76c4","f0d9ff"}}
local function ingot(p) rect(3,6,12,11,p[1]);rect(4,5,11,10,p[2]);rect(5,4,10,5,p[3]);line(3,7,7,10,p[3]);line(8,5,12,7,p[3]);line(8,10,12,8,p[2]);pixel(11,11,p[3]) end
local function rock(p) for _,v in ipairs({{3,5,6,9},{8,3,11,7},{7,9,12,12}}) do rect(v[1],v[2],v[3],v[4],p[1]);rect(v[1]+1,v[2],v[3],v[4]-1,p[2]);pixel(v[1]+1,v[2],p[3]) end end
for _,name in ipairs({"lead","uranium","titanium","terra","spacetime"}) do
 local p=colors[name];start();layer("金属结构");ingot(p);finish("item",name.."_ingot")
 start();metal(name=="terra" and 4 or name=="spacetime" and 5 or 3);layer("金属压纹");rect(3,3,12,12,p[2]);line(3,3,12,3,p[3]);line(3,4,3,12,p[3]);for x=5,11,3 do line(x,5,x,11,p[1]) end;finish("block",name.."_block")
 if name~="terra" and name~="spacetime" then
  start();layer("粗矿");rock(p);finish("item","raw_"..name)
  start();rect(0,0,15,15,"747a80");for i=1,90 do pixel(math.random(0,15),math.random(0,15),i%2==0 and "626971" or "8b9094") end;layer("矿脉");for _,v in ipairs({{3,3},{9,2},{6,8},{12,11},{2,12}}) do rect(v[1],v[2],v[1]+2,v[2]+2,p[1]);pixel(v[1]+1,v[2],p[3]);pixel(v[1]+2,v[2]+1,p[2]) end;finish("block",name.."_ore")
 end
end
for _,name in ipairs({"iron","copper","gold","lead","uranium","titanium"}) do start();layer("粉碎矿粒");rock(colors[name]);for i=1,8 do pixel(math.random(2,13),math.random(4,13),colors[name][3]) end;finish("item","crushed_"..name) end
for tier=1,10 do
 start();layer("晶体轮廓");local p=palette[math.min(5,math.floor((tier+1)/2))];for y=1,14 do local w=math.min(4,math.min(y,15-y));for x=8-w,7+w do pixel(x,y,p[1]) end end
 layer("晶面");for y=2,12 do local w=math.min(3,math.min(y-1,14-y));for x=8-w,7+w do pixel(x,y,x<8 and p[3] or p[4]) end end;line(7,3,6,10,p[3]);line(8,7,9,12,p[2]);layer("阶位标记");for i=1,tier do pixel(2+(i-1)%5*2,14-math.floor((i-1)/5),p[4]) end;finish("item","energy_crystal_"..tier)
end
for tier=1,5 do start();local p=palette[tier];rect(2,2,13,13,p[1]);rect(3,3,12,12,p[2]);rect(5,5,10,10,p[1]);line(3,3,12,3,p[3]);line(3,4,3,12,p[3]);layer("核心与铆钉");rect(6,6,9,9,p[4]);pixel(6,6,p[3]);for _,v in ipairs({{4,4},{11,4},{4,11},{11,11}}) do pixel(v[1],v[2],p[3]) end;finish("item","machine_frame_"..tier) end
start();rect(0,0,15,15,"343953");for x=0,12,4 do for y=0,12,4 do rect(x+1,y+1,x+3,y+3,"58a3df");pixel(x+1,y+1,"c4f3ff");pixel(x+3,y+3,"7465b9") end end;finish("block","energy_crystal_block_2")
start();rect(0,0,15,15,"53586e");for i=1,75 do pixel(math.random(0,15),math.random(0,15),"777d8c") end;layer("能源晶脉");for _,v in ipairs({{2,3},{9,2},{6,8},{11,11}}) do rect(v[1],v[2],v[1]+2,v[2]+3,"7763b0");line(v[1],v[2],v[1]+1,v[2]+1,"b6ceff");pixel(v[1]+2,v[2]+3,"6cb5d8") end;finish("block","magic_ore")
start();layer("魔力草叶");for _,v in ipairs({{3,14,1,7},{4,14,5,3},{7,14,8,1},{10,14,13,4},{11,14,15,10}}) do line(v[1],v[2],v[3],v[4],"468d7e");line(v[1]+1,v[2],v[3]+1,v[4],"7ac8a4");pixel(v[3],v[4],"b5a6fa");pixel(v[3]+1,v[4]+1,"d4ddff") end;finish("block","magic_grass")
start();layer("凝聚晶体");rock({"2e2448","654c86","df717e"});line(8,4,7,7,"f5b4ca");line(8,9,10,11,"e67b9a");finish("item","industrial_magic_stone")
start();layer("硫化矿料");rock({"3c3f2c","a3a55a","ecec9a"});pixel(4,7,"b7bd73");pixel(9,5,"f3d877");finish("item","sulfide")
start();layer("权杖柄");line(3,14,11,6,"362b42");line(4,14,12,6,"987e61");line(5,12,9,8,"dfbd7f");layer("杖首魔晶");rect(9,2,12,6,"3f4b8a");rect(10,1,11,6,"97caf6");pixel(10,2,"e9f7ff");pixel(8,5,"c2addf");pixel(13,3,"c2addf");finish("item","mana_wand")
start();layer("储液桶");rect(3,5,12,12,"434d60");rect(4,6,11,12,"98a8ba");rect(5,12,10,13,"647489");line(3,4,12,4,"d4dde8");line(4,2,11,2,"8a9bab");line(3,3,3,4,"8a9bab");line(12,3,12,4,"8a9bab");layer("冷却液");rect(4,5,11,7,"4bafc6");line(5,5,10,5,"a8f1ed");finish("item","coolant_bucket")
-- Contact sheet is an inspection artifact; individual layered sources remain untouched.
local cols=16;local rows=math.ceil(#entries/cols);local sheet=Sprite(cols*16,rows*16,ColorMode.RGB);sheet.layers[1].name="工业资源总览";local img=Image(cols*16,rows*16,ColorMode.RGB)
for i,entry in ipairs(entries) do local tile=Image{fromFile=texture..entry[1].."/"..entry[2]..".png"};img:drawImage(tile,Point(((i-1)%cols)*16,math.floor((i-1)/cols)*16)) end
sheet.cels[1].image=img;sheet:saveAs(root.."art/new/industry/contact-sheet.aseprite");sheet:saveCopyAs(root.."art/new/industry/contact-sheet.png");sheet:close();print("Created "..#entries.." original PNG textures and layered Aseprite sources.")

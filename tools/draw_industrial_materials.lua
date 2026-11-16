-- Aseprite coordinate drawings, interpreted from the approved concept art.
-- This is an art source: coordinates describe individual pixel clusters/facets.
-- No material imports, image sampling, downscaling, dithering, or random noise.
-- Run: aseprite --batch --script-param root=<checkout> --script this-file
-- industrial_materials.aseprite remains the editable/exportable source.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
app.fs.makeAllDirectories(root..'/temp/material-redraw')
local manifestFile=assert(io.open(root..'/tools/industrial_materials.json','r'))
local names={}
for name in manifestFile:read('*a'):gmatch('"([a-z0-9_]+)"') do
  if name~='items' then names[#names+1]=name end
end
manifestFile:close()

-- Materials maintained on reference bases have separate editable sources.
local baseNames={}
for _,sourceName in ipairs({'industrial_material_bases','industrial_component_bases','nuclear_fuel_rods','energy_crystals'}) do
  local source=Sprite{fromFile=root..'/tools/'..sourceName..'.aseprite'}
  for _,tag in ipairs(source.tags) do baseNames[tag.name]=true end
  source:close()
end
local coordinates={}
for _,name in ipairs(names) do if not baseNames[name] then coordinates[#coordinates+1]=name end end
names=coordinates

-- Prospectors are outside this material redraw; retain only their five frames.
local retained={}
local previous=Sprite{fromFile=root..'/tools/industrial_materials.aseprite'}
for _,tag in ipairs(previous.tags) do
  if tag.name:match('^prospector_t[1-5]$') then
    local image=Image(previous.spec)
    image:drawSprite(previous,tag.fromFrame.frameNumber)
    retained[tag.name]=image
  end
end
previous:close()

local pc=app.pixelColor
local colors={}
local function color(h)
  if not colors[h] then
    colors[h]=pc.rgba(tonumber(h:sub(1,2),16),tonumber(h:sub(3,4),16),tonumber(h:sub(5,6),16),255)
  end
  return colors[h]
end
local canvas,plane
local function ink(n) plane=canvas[n] end
local function px(x,y,h)
  assert(x>=0 and x<32 and y>=0 and y<32,('Pixel outside 32px: %d,%d'):format(x,y))
  plane:drawPixel(x,y,color(h))
end
local function line(x0,y0,x1,y1,h)
  local dx,dy=math.abs(x1-x0),-math.abs(y1-y0)
  local sx,sy=x0<x1 and 1 or -1,y0<y1 and 1 or -1
  local err=dx+dy
  while true do
    px(x0,y0,h)
    if x0==x1 and y0==y1 then break end
    local e=2*err
    if e>=dy then err=err+dy;x0=x0+sx end
    if e<=dx then err=err+dx;y0=y0+sy end
  end
end
local function path(points,h)
  for i=1,#points-1 do line(points[i][1],points[i][2],points[i+1][1],points[i+1][2],h) end
end
local function poly(points,h)
  local minx,miny,maxx,maxy=31,31,0,0
  for _,p in ipairs(points) do minx=math.min(minx,p[1]);maxx=math.max(maxx,p[1]);miny=math.min(miny,p[2]);maxy=math.max(maxy,p[2]) end
  for y=miny,maxy do for x=minx,maxx do
    local inside=false
    local j=#points
    for i=1,#points do
      local a,b=points[i],points[j]
      if (a[2]>y+.5)~=(b[2]>y+.5) and x+.5<(b[1]-a[1])*(y+.5-a[2])/(b[2]-a[2])+a[1] then inside=not inside end
      j=i
    end
    if inside then px(x,y,h) end
  end end
  for i=1,#points do local a,b=points[i],points[i%#points+1];line(a[1],a[2],b[1],b[2],h) end
end
local function rect(x0,y0,x1,y1,h)
  for y=y0,y1 do for x=x0,x1 do px(x,y,h) end end
end
local function dots(points,h) for _,p in ipairs(points) do px(p[1],p[2],h) end end
local function offset(points,x,y)
  local t={};for _,p in ipairs(points) do t[#t+1]={p[1]+x,p[2]+y} end;return t
end
local function palette(values)
  local p={};for h in values:gmatch('%x%x%x%x%x%x') do p[#p+1]=h end
  return p
end
local P={
 iron=palette('252735 3e4153 606578 858c9c b6bdc9 d9dfe6 f5f4ef'),
 stone=palette('29272d 454148 625f64 817d7c a6a099 c7bfb1 e4dccb'),
 warm=palette('322a2b 55443c 796350 9f8468 c6ab87 e3d2af f5ecd3'),
 lead=palette('222233 3b394e 57556e 77768e a19fb7 c8c7d6 e3e2ec'),
 copper=palette('43282b 743a31 a5533c cb7850 eca371 ffd09a fff0ce'),
 vanadium=palette('272435 454057 65627d 8887a2 b4b3cc d7d6e8 f2f0fa'),
 titanium=palette('263245 3d506c 607b99 89a8c3 b6d3e5 dceef5 f7fcfa'),
 molybdenum=palette('282635 484358 68647d 928da5 bcb8cc dddae8 f8f6fc'),
 zirconium=palette('302c2e 51494a 796e6a a99c90 d2c9ba eee8da fffaf0'),
 zircon=palette('452e2c 703f35 a15d45 c18b68 e0b99a f4d9b8 fff0d2'),
 mithril=palette('183841 285b6b 3e8fa1 6fc5d0 a5e9ed d3ffff f6fffb'),
 niobium=palette('2c253d 49405e 706184 9e8db4 c3b1d3 e6d8f2 fff3ff'),
 tantalum=palette('312a2f 554b52 817176 ae9b9d d2c1bf f1e3d7 fff7ec'),
 adamantite=palette('3f2928 6a3f2e 996037 c28a4e e9b66e ffdb9b fff2ce'),
 uranium=palette('28301f 414b24 657733 90a446 bacb6b dbe6a0 f7f7ce'),
 iridium=palette('292538 494058 736783 9f92b4 cbbfdf e8e1f5 fcf8ff'),
 gold=palette('49301d 795226 ac8432 d2af50 f7d878 fff0b0 fffbdc'),
 purple=palette('211d2d 382b49 51415f 735c84 a08ab3 c9b9d7 e9dfec'),
 brass=palette('493426 72553b a38050 c5a36a e7cc92 f6e6bc fff5db'),
 ceramic=palette('403b43 645e65 90848b b7a9ac d9ccc8 f0e5db fff7e8'),
 cyan=palette('153c48 1c6678 29a0af 53d7dc 9ff6eb d9fff8 f7fffb'),
 blue=palette('1d244e 253d81 3e61b1 5c91e3 8ec8fa c8eaff f5fcff'),
 violet=palette('33204e 562d87 8046ba ad6ae8 d39afb efccff fff2ff'),
 green=palette('193728 256344 39a875 63d89d a0f2c0 d0ffe4 f2fff5'),
 fire=palette('5b2426 963d28 cf662d f29e3e ffd36d fff2b3 ffffed'),
}
local draw={}
local function register(id,fn) assert(not draw[id],id);draw[id]=fn end

-- Large ore fragments: individually drawn rock outlines and mineral inclusions.
local rockShapes={
 lead={{{2,15},{4,10},{4,7},{10,5},{12,2},{20,2},{21,5},{26,6},{26,11},{29,13},{28,21},{25,23},{25,26},{18,27},{16,30},{8,29},{7,27},{3,26},{3,23},{1,20}},
  {{{5,12},{12,5},{19,4},{16,11},{8,17}},5},{{{17,6},{24,8},{26,15},{21,18},{17,13}},3},{{{3,20},{9,16},{11,24},{7,27},{4,24}},3},{{{13,23},{21,18},{25,21},{19,28},{12,28}},4}},
 orichalcum={{{3,15},{5,10},{8,9},{8,6},{16,2},{20,4},{24,4},{25,8},{28,11},{28,20},{26,21},{26,25},{21,28},{16,28},{11,30},{5,27},{5,24},{2,21}},
  {{{4,14},{9,8},{16,4},{18,8},{11,13}},5},{{{20,5},{26,10},{24,16},{19,13}},3},{{{4,20},{10,17},{14,24},{9,28},{5,25}},3},{{{17,22},{24,19},{25,24},{20,28},{15,29}},4}},
 vanadium={{{2,18},{4,12},{7,11},{7,8},{12,5},{21,2},{24,4},{24,6},{28,7},{27,12},{29,14},{27,20},{25,21},{24,27},{19,28},{15,30},{8,28},{7,26},{3,26}},
  {{{5,11},{13,6},{21,3},{22,7},{13,12}},5},{{{24,7},{28,12},{25,20},{21,19}},3},{{{3,18},{10,15},{12,20},{7,26},{4,24}},3},{{{14,23},{23,21},{23,27},{16,29},{11,27}},4}},
 titanium={{{3,15},{5,12},{5,8},{11,5},{14,2},{22,2},{23,5},{27,6},{27,11},{29,14},{28,20},{26,22},{27,25},{19,29},{14,28},{10,30},{4,27},{4,24},{1,21}},
  {{{5,13},{9,8},{17,4},{21,3},{18,9},{11,15}},5},{{{23,5},{27,9},{28,15},{22,19},{20,12}},3},{{{3,20},{10,16},{13,23},{8,28},{4,24}},3},{{{14,24},{22,20},{26,23},{19,28},{11,29}},4}},
 molybdenum={{{2,14},{5,10},{5,7},{10,5},{17,2},{23,2},{23,5},{27,5},{27,9},{29,12},{27,17},{28,21},{24,23},{24,27},{16,29},{10,30},{7,27},{3,26},{2,22}},
  {{{4,12},{10,7},{18,3},{21,5},{14,12},{6,17}},5},{{{24,6},{27,10},{26,18},{21,20},{20,14}},3},{{{3,20},{10,18},{13,24},{7,27},{4,25}},3},{{{15,25},{23,23},{23,27},{16,29},{11,28}},4}},
 zirconium={{{3,14},{4,10},{8,8},{9,5},{15,4},{18,2},{23,4},{24,7},{28,8},{27,13},{30,17},{27,23},{24,24},{23,28},{18,29},{12,28},{10,30},{5,27},{4,23},{2,21}},
  {{{4,14},{7,9},{15,5},{21,4},{18,10},{10,16}},5},{{{24,6},{28,13},{25,20},{20,17}},3},{{{3,21},{9,18},{13,23},{9,28},{5,25}},3},{{{15,24},{23,21},{25,24},{19,28},{12,29}},4}},
 mithril={{{2,17},{4,13},{4,9},{10,7},{13,3},{21,2},{23,4},{27,5},{27,10},{29,13},{28,21},{26,22},{26,26},{21,28},{15,28},{12,30},{7,28},{7,25},{3,25},{2,23}},
  {{{5,13},{8,9},{16,4},{21,3},{18,10},{11,16}},5},{{{24,5},{28,11},{25,17},{21,18},{20,12}},3},{{{3,21},{11,17},{14,22},{8,27},{5,25}},3},{{{15,25},{23,21},{26,22},{21,27},{13,29}},4}},
 niobium_tantalum={{{2,15},{5,12},{5,8},{10,5},{13,2},{19,3},{22,2},{24,6},{27,7},{27,12},{29,16},{27,22},{24,23},{25,26},{20,29},{14,28},{11,30},{6,27},{3,26},{2,21}},
  {{{3,14},{9,8},{14,4},{18,4},{15,11},{8,17}},5},{{{23,6},{27,11},{25,17},{21,16}},3},{{{3,20},{10,16},{12,22},{7,27},{4,24}},3},{{{14,24},{22,20},{25,23},{20,28},{11,28}},4}},
 adamantite={{{3,15},{5,11},{5,8},{10,6},{14,2},{20,3},{22,5},{26,5},{26,9},{29,12},{28,18},{29,21},{25,25},{23,25},{21,29},{14,28},{11,30},{6,27},{4,27},{4,23},{2,20}},
  {{{5,13},{9,8},{16,4},{20,5},{16,11},{9,16}},5},{{{24,7},{28,13},{26,18},{21,17}},3},{{{3,21},{9,17},{13,23},{8,28},{4,25}},3},{{{15,25},{23,22},{25,25},{19,28},{12,29}},4}},
 uranium={{{2,16},{5,12},{5,9},{10,6},{13,3},{20,2},{22,5},{26,6},{26,10},{29,13},{28,18},{26,20},{27,24},{22,28},{18,27},{13,30},{8,28},{5,28},{5,24},{2,22}},
  {{{5,14},{8,9},{15,5},{20,4},{18,10},{11,16}},5},{{{23,6},{28,12},{25,18},{20,15}},3},{{{3,21},{9,17},{13,23},{8,27},{5,25}},3},{{{15,24},{23,21},{26,23},{21,27},{13,29}},4}},
 iridium={{{2,15},{4,11},{4,8},{11,5},{14,2},{20,3},{23,2},{24,6},{28,7},{27,12},{30,15},{27,22},{27,25},{22,26},{20,29},{15,28},{11,30},{6,27},{4,26},{4,23},{2,21}},
  {{{5,13},{9,8},{15,4},{20,4},{17,11},{10,16}},5},{{{24,6},{28,12},{25,19},{21,16}},3},{{{3,21},{9,18},{13,23},{8,28},{5,25}},3},{{{15,24},{23,21},{26,23},{21,28},{12,29}},4}},
}
local inclusions={
 lead={{{6,21},{8,15},{13,13},{14,17},{10,23}},{{15,15},{18,10},{24,9},{23,14},{18,18}},{{13,25},{15,22},{19,21},{19,24},{15,27}}},
 orichalcum={{{6,23},{8,18},{11,17},{13,21},{10,25}},{{13,17},{15,11},{20,8},{24,10},{24,15},{20,19},{16,21}},{{19,26},{21,23},{24,22},{24,25},{21,27}}},
 vanadium={{{5,21},{8,17},{19,12},{21,14},{10,22}},{{10,14},{13,10},{24,6},{25,9},{15,15}},{{12,26},{15,22},{23,18},{24,21},{18,26}}},
 titanium={{{7,23},{9,18},{13,15},{15,18},{12,24}},{{13,16},{15,10},{20,7},{23,10},{21,16},{17,20}},{{20,25},{21,21},{25,18},{26,21},{23,25}}},
 molybdenum={{{5,20},{8,15},{16,12},{17,15},{9,22}},{{12,12},{15,8},{22,6},{24,9},{17,13}},{{11,26},{14,22},{22,19},{24,21},{17,27}}},
 zirconium={{{6,23},{7,18},{10,16},{12,19},{11,23},{8,26}},{{13,18},{15,10},{20,7},{23,10},{22,16},{17,22}}},
 mithril={{{3,19},{10,15},{13,14},{18,9},{22,7},{21,10},{17,13},{14,17},{10,18},{4,22}},{{8,28},{13,23},{17,22},{22,17},{27,15},{27,18},{23,20},{19,24},{14,25},{11,28}}},
 niobium_tantalum={{{5,23},{6,17},{10,15},{13,17},{11,22},{7,26}},{{12,14},{14,9},{18,7},{21,10},{19,14},{15,17}}},
 adamantite={{{5,22},{8,17},{11,16},{13,19},{10,24},{7,25}},{{14,19},{15,12},{20,8},{24,10},{23,16},{18,22}},{{19,27},{21,24},{25,22},{25,25},{22,27}}},
 uranium={{{5,23},{7,18},{11,17},{13,20},{11,24},{7,26}},{{14,18},{15,12},{20,8},{24,11},{23,16},{18,21}},{{18,27},{20,23},{24,22},{25,25},{22,27}}},
 iridium={{{5,21},{7,16},{11,13},{14,16},{12,21},{8,24}},{{14,13},{17,8},{22,8},{24,12},{21,16},{17,17}},{{15,26},{17,21},{22,18},{25,21},{23,25},{19,28}}},
}
-- Separate authored rock faces keep the host mineral volumetric at 32px.
-- Slaty cleavage, fractured matrix and coarse crystalline matrix use different cuts.
local rockFaces={
 fractured={
  {{{3,15},{5,11},{8,10},{7,15},{4,18}},4},
  {{{5,9},{10,6},{14,5},{11,9},{7,11}},5},
  {{{12,4},{18,2},{21,3},{17,5},{14,8}},6},
  {{{18,6},{22,4},{25,7},{24,10},{21,10}},4},
  {{{25,10},{27,12},{28,16},{25,19},{23,16}},3},
  {{{25,19},{27,18},{26,23},{23,25},{22,22}},4},
  {{{18,25},{21,22},{24,23},{22,26},{19,28}},3},
  {{{11,28},{14,24},{18,23},{17,26},{14,29}},4},
  {{{5,25},{7,20},{10,20},{9,24},{7,27}},3},
  {{{3,18},{6,17},{6,20},{4,23},{2,21}},5},
  {{{9,15},{11,11},{14,8},{16,8},{15,11},{12,14}},2},
  {{{9,23},{11,18},{14,17},{13,21},{11,24}},5},
  {{{14,22},{18,18},{22,18},{19,21},{17,25}},4},
  {{{20,13},{23,12},{24,15},{21,19},{18,18}},3},
 },
 slate={
  {{{4,13},{9,9},{17,5},{18,7},{11,11},{5,15}},5},
  {{{11,7},{16,3},{23,3},{21,5},{15,8}},6},
  {{{18,9},{24,5},{26,8},{23,11},{19,12}},3},
  {{{3,18},{9,14},{16,11},{16,13},{9,17},{3,21}},4},
  {{{6,21},{13,17},{22,13},{24,14},{17,19},{8,24}},3},
  {{{9,27},{16,22},{25,18},{25,21},{17,25},{11,29}},5},
  {{{21,26},{24,22},{27,22},{24,26},{20,28}},3},
  {{{25,11},{27,10},{28,14},{26,18},{24,17}},4},
  {{{3,23},{5,21},{8,25},{7,27},{4,26}},3},
  {{{13,27},{18,25},{20,26},{17,29},{12,29}},3},
  {{{6,12},{10,10},{12,11},{8,14}},6},
 },
 crystalline={
  {{{3,15},{7,9},{10,9},{9,14},{5,18}},4},
  {{{8,7},{13,4},{18,4},{16,7},{10,10}},6},
  {{{18,5},{23,4},{25,7},{22,10},{18,9}},4},
  {{{24,9},{27,10},{28,14},{25,17},{23,14}},3},
  {{{3,20},{6,17},{9,19},{8,23},{5,25},{3,23}},5},
  {{{10,15},{13,12},{16,13},{16,17},{12,19}},3},
  {{{11,23},{14,20},{18,21},{17,25},{12,27},{9,26}},5},
  {{{19,16},{23,14},{25,17},{23,21},{19,23},{17,20}},3},
  {{{23,24},{27,21},{26,25},{22,28},{19,28}},4},
  {{{8,27},{11,26},{14,28},{12,30},{8,29}},3},
  {{{19,10},{22,9},{23,12},{20,14},{18,13}},5},
 },
 veined={
  {{{3,16},{7,10},{11,9},{10,12},{6,16},{4,20}},4},
  {{{9,8},{14,4},{19,3},{21,5},{16,8},{12,9}},6},
  {{{22,5},{25,7},{26,11},{23,13},{21,10}},4},
  {{{3,22},{7,18},{10,18},{11,21},{7,25},{4,25}},3},
  {{{9,16},{13,11},{17,10},{16,14},{12,18}},4},
  {{{15,20},{19,16},{23,14},{24,17},{20,21},{16,23}},3},
  {{{10,26},{14,23},{18,24},{16,27},{12,29},{8,28}},5},
  {{{21,25},{25,21},{27,21},{25,25},{21,28},{18,28}},4},
  {{{25,12},{27,11},{28,15},{26,18},{24,17}},3},
  {{{5,15},{8,12},{10,12},{9,14},{6,17}},5},
 },
}
local rockProfile={lead='slate',orichalcum='veined',vanadium='slate',titanium='fractured',
 molybdenum='slate',zirconium='crystalline',mithril='veined',niobium_tantalum='crystalline',
 adamantite='fractured',uranium='crystalline',iridium='crystalline'}
local function mineralFace(points,p)
  poly(points,p[3])
  local a,b,c=points[1],points[2],points[3]
  local center={math.floor((a[1]+c[1])/2)+1,math.floor((a[2]+c[2])/2)+1}
  poly({a,b,c,center},p[5])
  if #points>=5 then poly({center,c,points[4],points[5]},p[2]) end
  path({a,b,c},p[6])
  line(b[1]+1,b[2]+1,c[1],c[2]+1,p[5])
end
for metal,shape in pairs(rockShapes) do
  register('raw_'..metal,function()
    local host=metal=='zirconium' and P.warm or P.stone
    ink(1);poly(shape[1],host[2]);ink(2)
    poly({{4,16},{13,8},{25,8},{27,19},{22,26},{13,29},{5,24}},host[3])
    poly({{23,8},{27,14},{25,23},{21,26},{18,25},{21,16}},host[2])
    for i=2,#shape do poly(shape[i][1],host[shape[i][2]]) end
    poly({{11,23},{16,20},{20,23},{18,27},{12,29},{9,27}},host[4])
    poly({{24,11},{27,14},{26,20},{23,23},{21,20}},host[3])
    poly({{4,18},{7,16},{8,21},{6,24},{4,23}},host[4])
    poly({{8,8},{12,5},{16,4},{14,7},{10,10}},host[6])
    poly({{20,8},{23,7},{26,10},{24,12},{21,11}},host[4])
    poly({{5,24},{8,22},{11,25},{8,28},{6,27}},host[3])
    poly({{18,27},{21,24},{24,24},{22,27},{19,29}},host[3])
    path({{12,25},{15,23},{17,23}},host[4])
    path({{25,14},{26,16},{25,18}},host[4])
    for _,patch in ipairs(rockFaces[rockProfile[metal]]) do poly(patch[1],host[patch[2]]) end
    path({{5,15},{9,14},{10,10},{14,8}},host[6])
    path({{7,25},{12,22},{13,19}},host[1])
    path({{21,27},{23,23},{26,21}},host[1])
    path({{11,12},{14,10},{16,10}},host[2])
    path({{6,17},{8,14},{10,14}},host[2])
    local p=P[metal] or P.niobium
    if metal=='orichalcum' then p=P.copper end
    if metal=='zirconium' then p=P.zircon end
    for _,v in ipairs(inclusions[metal]) do mineralFace(v,p) end
    if metal=='niobium_tantalum' then mineralFace({{16,25},{18,19},{22,16},{25,18},{23,23},{19,27}},P.tantalum) end
    ink(3)
    path({{8,10},{11,8},{13,8}},host[7])
    if metal=='mithril' then dots({{6,19},{12,16},{19,11},{14,24},{24,18}},p[7])
    elseif metal=='titanium' then line(16,11,19,9,p[7]);line(8,20,10,18,p[5])
    elseif metal=='zirconium' then line(16,13,19,10,p[7])
    elseif metal=='iridium' then line(8,17,10,15,p[7]);line(18,10,21,10,p[7]) end
  end)
end

-- Crushed ore is a handful of angular chips with visible fracture planes.
local chips={
 {{7,5,9,9,1},{18,3,6,6,0},{20,11,8,8,1},{3,15,9,8,0},{12,14,9,10,1},{21,23,7,6,0},{5,24,8,6,1}},
 {{5,7,9,8,1},{17,3,7,6,0},{21,10,8,9,1},{2,17,8,7,1},{11,15,9,9,0},{16,24,8,6,1},{25,23,5,5,0}},
 {{10,3,9,8,1},{3,11,8,7,0},{20,9,9,7,1},{9,14,9,10,1},{22,20,7,8,0},{3,23,8,6,1},{13,25,6,5,0}},
 {{6,4,8,9,1},{18,5,9,8,1},{2,16,9,8,0},{11,13,9,10,1},{23,16,6,8,0},{5,24,8,6,1},{18,24,8,6,0}},
}
local function chip(x,y,w,h,p,style)
  local q={{x,y+2},{x+w-3,y},{x+w-1,y+2},{x+w-2,y+h-2},{x+2,y+h-1},{x,y+h-3}}
  poly(q,p[2])
  poly({q[1],q[2],q[3],{x+math.floor(w/2),y+math.floor(h/2)}},p[5])
  poly({q[1],{x+math.floor(w/2),y+math.floor(h/2)},q[5],q[6]},p[3])
  path({q[1],q[2]},p[6])
  if style==1 then line(x+1,y+3,x+math.floor(w/2),y+2,p[4]) end
end
local crushedMetals={'lead','copper','orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium_tantalum','gold','adamantite','uranium','iron','iridium'}
for i,metal in ipairs(crushedMetals) do
  register('crushed_'..metal,function()
    ink(2)
    local p=P[metal] or P.niobium
    if metal=='orichalcum' then p=P.copper elseif metal=='zirconium' then p=P.zircon end
    chip(14,8,5,5,P.stone,0);chip(16,23,5,5,P.stone,1)
    for n,c in ipairs(chips[(i-1)%#chips+1]) do
      local q=c[5]==1 and p or P.stone
      if metal=='niobium_tantalum' and n%3==0 then q=P.tantalum end
      chip(c[1],c[2],c[3],c[4],q,n%2)
    end
    ink(3)
    dots({{4,10},{27,7},{28,20},{10,29},{18,28}},p[4])
    dots({{3,25},{16,11},{15,28}},P.stone[4])
  end)
end

-- Fine grains are placed in rows of small clusters, not image/noise samples.
local heaps={
 {{3,24},{6,20},{9,18},{12,12},{15,7},{18,9},{20,14},{24,17},{26,21},{29,24},{25,26},{8,27}},
 {{2,24},{6,20},{7,17},{11,15},{14,9},{17,7},{20,12},{22,16},{26,21},{29,24},{24,27},{8,27}},
 {{3,25},{5,20},{9,17},{11,13},{15,7},{19,11},{21,16},{25,19},{28,24},{24,27},{10,27}},
 {{2,24},{6,21},{9,15},{13,13},{16,7},{20,10},{21,15},{25,19},{28,25},{22,27},{9,27}},
}
local grainClusters={
 {15,9,5},{14,11,4},{17,12,4},{11,14,5},{14,15,4},{18,15,3},{20,17,4},
 {8,18,5},{11,18,4},{15,18,5},{18,19,3},{23,20,3},{6,21,4},{10,21,3},
 {13,21,4},{16,22,4},{20,22,3},{25,23,3},{5,24,3},{8,24,4},{11,25,3},
 {14,24,5},{18,25,3},{22,25,4},{25,26,3},
}
local function heap(p,variant,coarse)
  ink(1);poly(heaps[variant],p[1]);ink(2);poly(heaps[variant],p[3])
  poly({{16,9},{19,12},{21,17},{25,20},{27,24},{22,26},{17,25},{15,21},{17,17}},p[2])
  local function grain(x,y,shade)
    if coarse then
      poly({{x-1,y},{x,y-2},{x+2,y-1},{x+2,y+1},{x,y+2},{x-1,y+1}},p[shade])
      line(x,y-1,x+1,y-1,p[math.min(7,shade+1)])
    else
      rect(x,y,x+1,y+1,p[shade]);px(x,y,p[math.min(7,shade+1)])
      line(x+1,y+2,x+2,y+1,p[math.max(2,shade-1)])
    end
  end
  for n,g in ipairs(grainClusters) do
    local x,y=g[1],g[2]
    if variant==2 and n<5 then x=x+1 end
    if variant==3 and n%4==0 then x=x-1 end
    if variant==4 and n<4 then x=x+1 end
    grain(x,y,g[3])
  end
  ink(3)
  line(3,27,4,27,p[3]);line(23,28,24,28,p[3]);px(29,25,p[3])
  line(7,28,8,28,p[4]);px(12,28,p[5]);px(27,27,p[4])
end
for i,metal in ipairs({'orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium','tantalum','adamantite','iridium'}) do
  register(metal..'_concentrate',function() heap(P[metal] or P.copper,(i-1)%4+1,false) end)
end

-- Cast ingots: three lit planes, explicitly designed bevels and machining.
local function ingot(p,kind)
  ink(1)
  local A,B,C,D={3,14},{22,4},{29,9},{10,20}
  if kind=='long' then A={2,15};B={23,4};C={29,8};D={8,20} end
  if kind=='wide' then A={2,13};B={21,3};C={29,9};D={10,20} end
  local E,F,G={A[1],A[2]+8},{D[1],D[2]+8},{C[1],C[2]+8}
  poly({A,B,C,G,F,E},p[1])
  ink(2);poly({A,D,F,E},p[3]);poly({D,C,G,F},p[2]);poly({A,B,C,D},p[5])
  poly({{A[1]+2,A[2]},{B[1]-1,B[2]+1},{B[1]+1,B[2]+3},{D[1]+1,D[2]-1}},p[4])
  poly({{D[1]+2,D[2]+1},{C[1]-1,C[2]+2},{C[1]-1,C[2]+4},{D[1]+2,D[2]+5}},p[3])
  poly({{A[1]+2,A[2]},{A[1]+5,A[2]-1},{D[1]+3,D[2]-4},{D[1]+1,D[2]-2}},p[6])
  line(B[1]-7,B[2]+5,B[1]-3,B[2]+3,p[5])
  ink(3);path({A,D,C},p[6]);line(D[1],D[2]+1,F[1],F[2]-1,p[4])
  line(A[1]+1,A[2],B[1]-1,B[2]+1,p[5]);line(B[1],B[2],C[1],C[2],p[4])
  path({{A[1]+2,A[2]+1},{D[1],D[2]-1},{D[1]+4,D[2]-3}},p[7])
  if kind=='soft' then
    line(3,17,4,22,p[4]);line(12,27,25,20,p[3]);px(29,17,p[2])
  elseif kind=='stamp' then
    poly({{18,18},{21,16},{23,17},{20,19}},p[2]);line(19,18,21,17,p[4])
  elseif kind=='polished' then
    poly({{6,13},{12,10},{16,11},{10,15}},p[6]);line(7,13,12,11,p[7])
    line(19,23,23,21,p[4]);line(20,24,23,22,p[4])
  elseif kind=='cut' then
    line(17,10,24,6,p[3]);line(18,11,25,7,p[6])
  elseif kind=='long' then
    line(11,15,23,9,p[5]);line(11,16,21,11,p[6])
  end
end
local ingotSpecs={
 {'lead_ingot','lead','soft'},{'orichalcum_ingot','copper','wide'},{'vanadium_ingot','vanadium','cut'},
 {'titanium_ingot','titanium','stamp'},{'molybdenum_ingot','molybdenum','long'},
 {'zirconium_ingot','zirconium','wide'},{'mithril_ingot','mithril','polished'},
 {'niobium_ingot','niobium','cut'},{'tantalum_ingot','tantalum','soft'},
 {'adamantite_ingot','adamantite','stamp'},{'uranium_ingot','uranium','wide'},{'iridium_ingot','iridium','polished'},
 {'high_purity_mithril_ingot','mithril','long'},{'high_purity_niobium_ingot','iron','polished'},
 {'high_purity_tantalum_ingot','tantalum','cut'},{'high_purity_iridium_ingot','iridium','wide'},
}
for _,v in ipairs(ingotSpecs) do register(v[1],function()
  ingot(P[v[2]],v[3])
  if v[1]:match('^high_purity') then
    ink(3);line(15,25,25,20,P[v[2]][4]);line(16,25,19,23,P[v[2]][5])
    path({{6,15},{10,18},{17,14}},P[v[2]][7])
  end
end) end

-- Alloys retain a cast-metal family, but tooling and construction differ.
register('terra_ingot',function()
  local p=palette('151e31 26334b 3d516b 597490 829fbb b4d4e6 e7f4fa')
  ingot(p,'wide');ink(3)
  path({{13,20},{17,18},{17,21},{14,23}},p[2]);line(13,20,16,18,p[4])
  poly({{19,8},{23,6},{25,8},{21,10}},p[3])
end)
register('spacetime_ingot',function()
  ingot(P.violet,'cut');ink(2)
  poly({{4,14},{12,10},{17,11},{10,18}},P.blue[4])
  poly({{19,7},{22,5},{27,9},{23,11}},P.cyan[3])
  poly({{24,13},{28,11},{28,16},{24,19}},P.cyan[2]);ink(3)
  path({{5,14},{10,18},{16,15}},P.cyan[6]);line(20,7,24,7,P.violet[5])
end)
register('vanadium_steel',function() ingot(P.iron,'polished') end)
register('molybdenum_vanadium_steel',function()
  ingot(P.iron,'long');ink(2)
  poly({{7,14},{23,6},{25,7},{10,16}},P.iron[1])
  ink(3);line(8,13,23,5,P.iron[6]);line(11,17,26,9,P.iron[5])
end)
register('tzm_alloy',function()
  ingot(P.tantalum,'wide');ink(3)
  -- Open tool reliefs are part of the silhouette, not painted dark marks.
  for i=1,3 do
    local x,y=11+i*4,25-i*2
    for layer=1,3 do
      for yy=y,y+2 do for xx=x,x+1 do canvas[layer]:drawPixel(xx,yy,0) end end
    end
    line(x-1,y-1,x+1,y-2,P.tantalum[2])
  end
  line(7,15,10,17,P.tantalum[7])
end)
register('orichalcum_alloy',function()
  ingot(P.copper,'wide');ink(2)
  poly({{6,13},{22,5},{26,8},{11,17}},P.gold[4])
  poly({{10,19},{27,10},{27,14},{12,22}},P.gold[2]);ink(3)
  path({{7,13},{11,16},{25,9}},P.gold[7]);line(8,14,21,7,P.gold[5])
  line(5,18,7,20,P.copper[6]);line(26,15,27,14,P.copper[6])
end)
register('niobium_titanium_alloy',function()
  ingot(P.vanadium,'polished');ink(3)
  path({{4,16},{8,19},{9,25}},P.titanium[4]);line(13,25,16,23,P.vanadium[5])
end)
register('mithril_niobium_alloy',function()
  ingot(P.titanium,'wide');ink(2)
  poly({{6,12},{12,9},{16,11},{10,15}},P.mithril[5]);ink(3)
  path({{5,13},{10,18},{25,10}},P.mithril[7]);line(11,22,14,20,P.mithril[4])
end)
register('adamantite_titanium_alloy',function()
  local p=palette('171f34 29344e 424f6d 657998 92aac9 c0d4eb e8f3fa')
  ingot(p,'long');ink(2)
  poly({{5,14},{21,5},{24,6},{8,16}},p[1])
  poly({{9,20},{25,11},{27,12},{11,22}},p[1]);ink(3)
  line(7,13,21,5,p[5]);line(12,20,26,12,p[5]);line(10,23,10,27,p[4])
end)
register('henghui_alloy',function()
  ingot(P.iridium,'wide');ink(2)
  poly({{5,13},{11,10},{16,12},{10,17}},P.violet[4])
  poly({{9,11},{14,9},{19,10},{13,15}},P.blue[5])
  poly({{14,8},{18,6},{22,8},{17,11}},P.cyan[5]);ink(3)
  path({{5,14},{10,18},{15,15}},P.iridium[7]);line(8,14,10,15,P.cyan[7])
  line(18,22,24,19,P.violet[4])
end)
register('stellar_alloy',function()
  ingot(P.titanium,'wide');ink(2)
  poly({{5,14},{15,8},{18,10},{8,17}},P.blue[3])
  poly({{12,18},{23,12},{27,12},{16,20}},P.iron[1])
  poly({{11,10},{22,4},{24,6},{14,12}},P.blue[5]);ink(3)
  path({{7,14},{10,17},{19,12}},P.titanium[7]);line(14,17,24,12,P.titanium[5])
  line(15,23,20,20,P.blue[4])
end)
register('zircon_ceramic',function()
  ingot(P.ceramic,'wide');ink(2)
  poly({{5,13},{21,5},{27,9},{10,18}},P.ceramic[6]);ink(3)
  path({{4,14},{10,19},{28,10}},P.ceramic[7]);line(10,20,10,27,P.ceramic[5])
  line(23,16,26,14,P.ceramic[3])
end)

-- Nuclear processing: powder, pellets, hollow cladding and sealed fuel differ.
register('uranium_concentrate',function()
  heap(palette('45391c 706022 9d862e c3ac45 e9d46a f8e9a0 fff5ca'),2,true)
end)
register('converted_uranium',function()
  heap(palette('35411b 56631e 809627 aabd39 d0e568 e9f698 f8ffca'),4,true)
end)
register('enriched_uranium',function()
  heap(palette('274326 457135 6da546 a1d569 ccf29a e9ffd0 f8ffed'),1,true)
end)
register('depleted_uranium',function() heap(P.iron,3,true) end)

local function pellet(x,y,p)
  ink(1);poly(offset({{0,2},{2,0},{6,0},{8,2},{8,9},{6,11},{2,11},{0,9}},x,y),p[1])
  ink(2);rect(x+1,y+3,x+7,y+9,p[3]);rect(x+2,y+3,x+3,y+10,p[4]);rect(x+6,y+3,x+7,y+9,p[2])
  poly(offset({{0,2},{2,0},{6,0},{8,2},{6,4},{2,4}},x,y),p[5])
  ink(3);path(offset({{0,2},{2,4},{6,4},{8,2}},x,y),p[6]);line(x+2,y+1,x+5,y+1,p[4])
end
register('nuclear_pellet',function()
  pellet(13,3,P.tantalum);pellet(3,15,P.tantalum);pellet(20,17,P.tantalum)
end)

register('curium_feed',function()
  ink(2)
  local feed={{12,6,7,7},{5,13,7,7},{17,12,8,8},{10,20,7,7},{22,22,6,6},{3,23,5,5},{21,4,5,5},{24,14,6,6}}
  for n,c in ipairs(feed) do chip(c[1],c[2],c[3],c[4],n%3==0 and P.tantalum or P.niobium,n%2) end
  ink(3);dots({{5,10},{15,28},{29,24},{9,27}},P.tantalum[4])
end)
register('lithium_salt',function()
  heap(palette('404d64 62758d 91a6c0 b7cce2 dcebf5 f2f9ff ffffff'),2,true)
end)

local function capsule(p,mode)
  local steel=P.iron
  ink(1);poly({{3,20},{20,3},{25,2},{29,5},{30,10},{27,15},{14,28},{10,30},{5,29},{2,25}},steel[1])
  ink(2)
  poly({{8,17},{21,4},{27,7},{27,13},{13,27},{7,25}},p[2])
  poly({{8,17},{21,5},{25,7},{12,21}},p[4])
  poly({{12,21},{25,8},{27,10},{25,14},{13,26}},p[3])
  if mode~='empty' then
    poly({{10,17},{22,5},{24,7},{12,21}},p[6])
    poly({{14,21},{24,11},{25,13},{15,24}},p[4])
    line(12,17,22,7,p[5])
  else
    poly({{8,19},{21,6},{24,8},{11,22}},steel[2])
    line(13,15,23,5,steel[4])
  end
  -- Distinct rear cap, shoulder clamp and the open/closed front face.
  poly({{20,3},{25,2},{29,5},{30,10},{27,13},{25,10},{26,7},{23,5},{20,6}},steel[3])
  path({{20,3},{25,2},{29,5}},steel[6]);line(27,7,28,10,steel[2])
  poly({{5,18},{8,16},{14,19},{16,23},{14,26},{12,22},{8,20},{5,21}},steel[4])
  path({{5,18},{8,16},{14,19}},steel[7])
  poly({{3,22},{5,19},{9,19},{13,21},{15,25},{13,28},{9,30},{5,29},{2,25}},steel[3])
  path({{3,22},{5,19},{9,19},{13,21}},steel[6]);line(3,23,3,26,steel[5])
  if mode=='empty' then
    poly({{5,23},{7,21},{10,22},{12,24},{12,27},{9,28},{6,27},{4,25}},steel[1])
    line(5,23,5,25,steel[2]);line(7,27,10,27,steel[2])
  else
    poly({{5,23},{7,21},{10,22},{12,24},{12,27},{9,28},{6,27},{4,25}},steel[4])
    line(6,23,8,22,steel[5])
  end
  ink(3)
  if mode=='blue' then
    path({{7,18},{13,20},{15,24}},p[5]);line(7,24,9,25,steel[3])
  elseif mode=='violet' then
    path({{7,18},{13,20},{15,24}},p[4])
    line(6,24,7,23,steel[1]);line(8,26,9,25,steel[1]);px(10,27,steel[1])
  elseif mode=='fusion' then
    poly({{18,11},{22,8},{25,10},{22,14}},P.violet[4])
    line(19,11,21,9,P.violet[6])
    path({{7,18},{13,20},{15,24}},P.gold[5]);path({{22,5},{26,7},{28,10}},P.gold[5])
    rect(6,23,7,24,steel[2]);rect(9,24,10,25,steel[2]);rect(7,26,8,27,steel[2])
  end
end

-- Shared mechanical pixel strokes; corners and braces remain explicit designs.
local function beam(a,b,p)
  ink(2)
  line(a[1]-1,a[2],b[1]-1,b[2],p[1]);line(a[1]+1,a[2],b[1]+1,b[2],p[1])
  line(a[1],a[2]+1,b[1],b[2]+1,p[2]);line(a[1],a[2],b[1],b[2],p[4])
  line(a[1],a[2]-1,b[1],b[2]-1,p[6])
end
local function joint(x,y,p,heavy)
  local r=heavy and 2 or 1
  ink(3);rect(x-r,y-r,x+r,y+r,p[2]);rect(x-r,y-r,x+r-1,y+r-1,p[5])
  line(x-r,y-r,x+r-1,y-r,p[7]);px(x,y,p[3]);px(x-1,y-1,p[6])
end
local function frame(kind)
  local p=kind=='confinement' and P.iron or P.purple
  local cap=kind=='basic' and P.copper or kind=='reinforced' and P.brass or kind=='confinement' and P.iron or P.ceramic
  local A,B,C,D={4,9},{22,12},{28,6},{11,3}
  local E,F,G,H={4,26},{22,29},{28,23},{11,20}
  -- Rear rails first, leaving the empty volume genuinely transparent.
  beam(D,H,p);beam(H,G,p);beam(H,E,p);beam(C,G,p)
  if kind=='reinforced' or kind=='confinement' then
    beam(D,G,p);beam(C,H,p)
  end
  beam(A,E,p);beam(B,F,p);beam(E,F,p);beam(F,G,p)
  if kind=='reinforced' or kind=='confinement' then
    beam({6,12},{20,26},p);beam({6,24},{20,15},p)
    if kind=='confinement' then beam({24,15},{27,20},p);beam({24,26},{27,11},p) end
  end
  beam(D,C,p);beam(C,B,p);beam(A,D,p);beam(A,B,p)
  for _,v in ipairs({D,C,H,G,A,B,E,F}) do joint(v[1],v[2],cap,kind=='heatproof' or kind=='precision') end
  if kind=='heatproof' then
    ink(3);rect(3,15,5,16,P.copper[4]);rect(21,18,23,19,P.copper[4]);line(27,11,28,10,P.copper[5])
  elseif kind=='precision' then
    ink(3)
    path({{7,12},{7,17},{11,14}},P.brass[5]);path({{18,15},{18,19},{15,17}},P.brass[5])
    path({{7,23},{7,19},{11,24}},P.brass[4]);path({{18,25},{18,21},{15,24}},P.brass[4])
    line(4,17,4,20,P.cyan[4]);line(22,20,22,23,P.cyan[5]);line(28,12,28,15,P.cyan[4])
  end
end
register('basic_frame',function() frame('basic') end)
register('reinforced_frame',function() frame('reinforced') end)
register('heatproof_frame',function() frame('heatproof') end)
register('precision_frame',function() frame('precision') end)
register('confinement_lattice',function() frame('confinement') end)

local function circuitBase()
  ink(1);poly({{2,17},{17,8},{29,13},{29,19},{14,28},{2,22}},P.purple[1])
  ink(2);poly({{2,17},{17,8},{29,13},{14,23}},P.purple[3])
  poly({{2,18},{14,24},{14,28},{2,22}},P.purple[2])
  poly({{15,24},{29,15},{29,19},{15,28}},P.purple[2])
  path({{3,17},{14,23},{28,14}},P.purple[5])
  for i=0,3 do
    rect(4+i*3,20+i,5+i*3,22+i,P.brass[4])
    line(17+i*3,24-i*2,18+i*3,23-i*2,P.brass[5])
  end
end
local function manaComponent(mithril)
  local p=mithril and P.blue or P.fire
  circuitBase();ink(2)
  poly({{5,14},{19,6},{24,8},{10,16}},P.purple[4])
  poly({{8,17},{20,10},{24,12},{11,20}},p[3])
  path({{9,16},{20,10},{23,11}},p[6])
  for i=0,3 do line(11+i*3,15-i*2,13+i*3,18-i*2,P.brass[3]);line(10+i*3,15-i*2,12+i*3,18-i*2,P.brass[5]) end
  poly({{3,14},{6,12},{9,14},{9,22},{6,24},{3,22}},mithril and P.iron[3] or P.copper[3])
  poly({{3,14},{6,12},{9,14},{6,16}},mithril and P.iron[6] or P.copper[5])
  rect(5,17,6,20,P.cyan[4]);line(5,17,5,19,P.cyan[6])
  poly({{24,8},{27,6},{30,8},{30,16},{27,18},{24,16}},P.brass[4])
  poly({{24,8},{27,6},{30,8},{27,10}},P.brass[6]);rect(27,11,28,14,P.cyan[3]);line(27,11,27,13,P.cyan[6])
  -- Upright binding blocks distinguish the inductive assembly from a board.
  poly({{11,9},{13,6},{16,7},{16,12},{13,14},{11,12}},p[3]);line(12,9,13,7,p[6])
  ink(3);line(8,15,8,20,P.purple[5]);line(18,8,22,6,P.purple[5])
end
register('mana_component',function() manaComponent(false) end)
register('mithril_mana_component',function() manaComponent(true) end)
local function controlComponent()
  circuitBase();ink(2)
  local wire=P.cyan
  path({{4,17},{8,14},{12,16},{16,13},{21,16},{27,12}},wire[4])
  path({{7,19},{11,17},{16,19},{23,14}},wire[2])
  poly({{9,13},{16,9},{23,12},{16,17}},P.brass[2])
  poly({{11,12},{16,9},{21,11},{16,14}},P.brass[4])
  poly({{12,12},{16,10},{20,11},{16,13}},P.purple[2])
  poly({{14,11},{16,10},{18,11},{16,12}},P.cyan[5])
  path({{4,14},{7,12},{10,14},{7,17},{7,19}},P.cyan[5])
  path({{22,9},{25,10},{27,13},{24,16}},P.cyan[5])
  poly({{18,19},{22,16},{25,17},{21,20}},P.iron[4]);line(20,18,22,17,P.cyan[5])
  ink(3)
  for i=0,3 do px(10+i*3,14+i%2,P.brass[6]) end
  line(5,21,5,22,P.brass[6]);line(22,21,23,20,P.brass[6])
end
register('superconducting_control',controlComponent)

-- Tier cores: changing enclosures, supports and energy chambers, not a tint ramp.
local function cabinet(tier)
  local p=P.purple
  local cap=tier==1 and P.copper or tier==2 and P.brass or tier==3 and P.iron or tier==4 and P.ceramic or P.brass
  ink(1);poly({{4,9},{10,3},{27,5},{29,9},{29,24},{22,30},{5,27},{3,23}},p[1])
  ink(2)
  poly({{5,10},{22,13},{22,28},{5,25}},p[3])
  poly({{23,12},{28,8},{28,23},{23,28}},p[2])
  poly({{5,8},{11,3},{27,6},{21,11}},p[4])
  poly({{9,8},{12,5},{23,7},{20,9}},p[2])
  path({{6,8},{21,11},{27,6}},p[6]);line(6,25,21,28,p[5])
  line(24,14,24,23,p[3]);line(27,12,27,21,p[4])
  if tier==1 then
    -- Open copper corner cage with a suspended first-stage crystal.
    poly({{8,13},{19,15},{19,25},{8,23}},p[1])
    poly({{12,16},{15,14},{17,17},{16,22},{13,24},{11,21}},P.cyan[3])
    poly({{12,16},{15,14},{14,20},{12,22}},P.cyan[5]);line(13,16,14,15,P.cyan[7])
    beam({7,13},{7,23},p);beam({20,15},{20,26},p)
    beam({8,24},{20,26},p);line(24,16,24,20,P.cyan[3])
  elseif tier==2 then
    poly({{8,13},{18,15},{18,25},{8,23}},p[2])
    line(8,15,18,17,P.brass[3]);line(8,16,18,18,P.brass[5])
    rect(10,19,11,22,P.cyan[2]);rect(11,19,11,21,P.cyan[5])
    line(16,23,18,23,p[4]);line(8,25,21,28,P.brass[4])
    line(24,15,24,23,P.brass[4]);line(26,14,26,22,P.brass[2])
    line(12,5,17,6,P.brass[5])
  elseif tier==3 then
    poly({{7,13},{19,15},{19,27},{7,25}},P.iron[1])
    poly({{10,16},{14,15},{17,17},{17,24},{14,26},{10,24}},P.violet[2])
    rect(12,18,15,23,P.violet[4]);line(12,18,12,22,P.violet[6])
    rect(13,17,15,17,P.violet[5]);rect(12,24,15,24,P.violet[5])
    line(8,15,8,23,p[6]);line(18,17,18,25,P.iron[4])
    line(24,15,24,23,P.iron[5]);line(26,14,26,22,P.iron[2])
  elseif tier==4 then
    poly({{8,13},{19,15},{19,27},{8,25}},P.ceramic[4])
    poly({{9,14},{18,16},{18,26},{9,24}},P.ceramic[5])
    for i=0,3 do line(12,17+i*2,16,18+i*2,P.purple[2]);line(12,18+i*2,16,19+i*2,P.ceramic[3]) end
    rect(6,15,7,22,P.cyan[3]);line(6,15,6,20,P.cyan[5])
    poly({{23,13},{27,11},{27,16},{23,18}},P.blue[3]);line(24,13,26,12,P.blue[6])
    line(24,20,27,18,P.ceramic[4]);line(24,23,27,21,P.ceramic[3])
  else
    -- Tier six has a faceted energy cell and a separate brass containment frame.
    poly({{7,13},{20,15},{20,27},{7,25}},P.blue[1])
    poly({{10,16},{17,17},{18,23},{13,26},{9,23}},P.blue[3])
    poly({{12,17},{15,19},{14,24},{11,22}},P.blue[4])
    line(8,16,8,23,P.cyan[4]);line(19,17,19,25,P.cyan[4])
    path({{8,13},{13,20},{19,27}},P.brass[5]);path({{8,25},{13,21},{19,16}},P.brass[4])
    path({{9,13},{14,20},{20,27}},P.brass[2])
    rect(12,20,15,21,P.blue[6]);line(14,18,14,24,P.cyan[6]);px(14,20,P.blue[7])
    line(24,15,24,23,P.cyan[4]);line(26,13,26,21,P.blue[4])
    line(13,6,20,7,P.cyan[5]);px(17,6,P.cyan[7])
    line(6,26,21,29,P.brass[4])
  end
  for _,v in ipairs({{6,9},{22,12},{27,7},{6,25},{22,28},{27,23}}) do joint(v[1],v[2],cap,tier>=4) end
  ink(3);line(8,10,19,12,p[5]);line(7,26,18,28,p[5])
  if tier==6 then rect(4,16,5,19,P.cyan[4]);rect(22,19,23,22,P.cyan[5]) end
end
for _,tier in ipairs({1,2,3,4,6}) do register('industrial_upgrade_'..tier,function() cabinet(tier) end) end
register('industrial_upgrade_5',function()
  local p=P.purple
  ink(1);poly({{4,7},{11,2},{23,3},{29,9},{29,24},{23,29},{9,30},{2,24},{2,12}},p[1])
  ink(2)
  poly({{4,9},{10,6},{23,6},{27,10},{24,14},{8,14}},p[4])
  poly({{3,14},{9,17},{9,26},{3,23}},p[2])
  poly({{10,17},{23,17},{23,28},{10,28}},p[3])
  poly({{24,16},{28,12},{28,23},{24,27}},p[2])
  poly({{5,7},{12,3},{23,4},{27,8},{23,11},{10,11}},p[4])
  poly({{10,6},{14,4},{22,5},{24,7},{21,9},{12,9}},P.uranium[3])
  poly({{13,6},{15,5},{21,6},{22,7},{20,8},{14,8}},p[1])
  rect(11,17,22,25,P.green[1]);rect(12,18,14,24,P.green[4]);rect(17,18,20,24,P.green[3])
  line(12,18,12,23,P.green[6]);line(18,18,18,24,P.green[5])
  rect(15,18,16,25,p[2]);line(15,18,15,24,P.iron[4])
  line(5,16,5,23,P.green[3]);line(26,17,26,22,P.green[4])
  beam({4,14},{26,14},p);beam({6,26},{24,28},p)
  for _,v in ipairs({{4,10},{11,13},{24,13},{28,10},{4,25},{11,28},{24,28},{28,23}}) do joint(v[1],v[2],P.iron,false) end
  ink(3);line(10,13,10,16,P.uranium[4]);line(23,14,23,17,P.uranium[4]);line(6,7,11,4,P.iron[5])
end)

register('nuclear_core',function()
  local p=P.iron
  ink(1);poly({{7,5},{14,2},{23,4},{27,8},{27,26},{21,30},{9,29},{4,25},{4,10}},p[1])
  ink(2)
  poly({{6,11},{10,9},{23,10},{26,12},{26,24},{21,27},{9,26},{6,23}},p[2])
  rect(8,12,23,24,P.cyan[1])
  for _,x in ipairs({9,15,21}) do
    rect(x,13,x+1,23,P.cyan[3]);line(x,14,x,21,P.cyan[6])
    rect(x-1,11,x+2,12,p[3]);rect(x-1,23,x+2,25,p[4])
  end
  for _,x in ipairs({6,12,24}) do rect(x,10,x+1,25,p[3]);line(x,11,x,23,p[5]) end
  poly({{5,7},{9,4},{21,4},{26,7},{23,10},{9,10}},p[4])
  poly({{9,4},{14,2},{22,4},{22,6},{13,7},{9,6}},p[5])
  poly({{13,4},{16,3},{20,4},{20,5},{16,6},{13,5}},p[1])
  poly({{5,25},{10,27},{22,27},{27,24},{27,27},{21,30},{9,29},{5,27}},p[3])
  line(8,26,22,28,p[6])
  ink(3)
  for i=0,2 do line(5,14+i*3,7,16+i*3,P.gold[4]);line(24,15+i*3,26,13+i*3,P.gold[4]) end
  dots({{8,7},{23,8},{8,27},{23,27}},p[7])
end)
register('rtg_core',function()
  local p=P.iron
  ink(1);poly({{4,8},{13,3},{25,5},{29,10},{29,26},{22,30},{4,26},{2,21}},p[1])
  ink(2);poly({{4,9},{22,13},{22,29},{4,25}},P.purple[2])
  poly({{23,13},{28,9},{28,25},{23,29}},P.purple[1])
  poly({{4,8},{13,3},{27,6},{21,12}},P.purple[3])
  for i=0,4 do
    local x,y=4+i*4,10+i
    rect(x,y,x+1,y+14,p[2]);line(x,y,x,y+13,p[5]);line(x+2,y+1,x+2,y+13,p[1])
  end
  for i=0,3 do line(24,15+i*3,28,12+i*3,p[4]);line(24,16+i*3,28,13+i*3,p[2]) end
  poly({{11,5},{16,3},{23,4},{21,7},{15,9},{11,8}},P.brass[4])
  for i=0,2 do line(14,5+i,20,4+i,P.brass[2]) end
  path({{13,11},{16,12},{16,27},{20,28}},P.brass[4])
  path({{14,11},{17,12},{17,26},{21,27}},P.brass[6])
  rect(12,17,14,20,P.purple[3]);rect(19,21,21,24,P.purple[3])
  ink(3);line(4,8,20,12,p[6]);line(4,25,21,29,p[4]);px(13,18,p[6]);px(20,22,p[6])
end)

-- End of native-resolution material drawings.

-- Assemble editable, separately inspectable structure/facet/detail layers.
local partial=app.params.partial=='true'
local master=Sprite(32,32,ColorMode.RGB)
local layers={master.layers[1],master:newLayer(),master:newLayer(),master:newLayer()}
layers[1].name='01 / pixel silhouettes and structure'
layers[2].name='02 / hand-placed facets and material'
layers[3].name='03 / bevels joints and highlights'
layers[4].name='04 / retained prospectors'
local drawn,ordered,composites=0,{},{}
for _,id in ipairs(names) do
  if draw[id] or retained[id] then
    canvas={Image(32,32,ColorMode.RGB),Image(32,32,ColorMode.RGB),Image(32,32,ColorMode.RGB)}
    if draw[id] then
      draw[id]();drawn=drawn+1
      if id:match('^raw_') then
        for y=0,31 do for x=0,31 do
          if pc.rgbaA(canvas[1]:getPixel(x,y))==0 then
            canvas[2]:drawPixel(x,y,0);canvas[3]:drawPixel(x,y,0)
          end
        end end
      end
    end
    local index=#ordered+1
    local frame=index==1 and master.frames[1] or master:newEmptyFrame(index)
    for i=1,3 do
      if index==1 and i==1 then layers[1]:cel(1).image=canvas[1]
      else master:newCel(layers[i],frame,canvas[i],Point(0,0)) end
    end
    local composite=Image(32,32,ColorMode.RGB)
    for _,im in ipairs(canvas) do composite:drawImage(im,Point(0,0)) end
    if retained[id] then
      master:newCel(layers[4],frame,retained[id],Point(0,0));composite:drawImage(retained[id],Point(0,0))
    end
    ordered[#ordered+1]=id;composites[#composites+1]=composite
  else assert(partial,'Material has not been redrawn: '..id) end
end
for i,id in ipairs(ordered) do local tag=master:newTag(i,i);tag.name=id end
if not partial then assert(#ordered==#names and drawn+5==#ordered,'Expected all remaining materials and five retained tools') end
master:saveAs(root..(partial and '/temp/material-redraw/partial.aseprite' or '/tools/industrial_materials.aseprite'))
for page=1,math.ceil(#ordered/32) do
  local sheet=Image(768,384,ColorMode.RGB)
  sheet:clear(pc.rgba(62,62,68,255))
  for n=1,32 do
    local i=(page-1)*32+n
    if composites[i] then
      local tile=composites[i]:clone();tile:resize(96,96)
      sheet:drawImage(tile,Point(((n-1)%8)*96,math.floor((n-1)/8)*96))
    end
  end
  sheet:saveAs(root..'/temp/material-redraw/drawn-'..page..'.png')
end
local list=assert(io.open(root..'/temp/material-redraw/drawn-order.txt','w'))
for i,id in ipairs(ordered) do list:write(i..' '..id..'\n') end
list:close()
master:close()
print('Coordinate-drawn materials: '..drawn)

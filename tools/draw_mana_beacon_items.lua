-- Aseprite: --batch --script-param root=<checkout> --script tools/draw_mana_beacon_items.lua
-- exportOnly=true preserves hand edits and only exports the saved native source.
local root=assert(app.params.root,'Pass root=<checkout>'):gsub('\\','/')
local assets=root..'/src/main/resources/assets/yumis_dungeon_and_fight/'
local sourcePath=root..'/tools/mana_beacon_items.aseprite'
local previewPath=root..'/temp/beacon-items'
local pc=app.pixelColor
local function color(hex)
  return pc.rgba(tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16),255)
end
local function shade(hex,factor)
  local values={}
  for i=1,5,2 do values[#values+1]=math.min(255,math.floor(tonumber(hex:sub(i,i+1),16)*factor+.5)) end
  return string.format('%02x%02x%02x',table.unpack(values))
end
local function pixel(image,x,y,hex) image:drawPixel(x,y,color(hex)) end
local function rect(image,x,y,w,h,hex)
  for yy=y,y+h-1 do for xx=x,x+w-1 do pixel(image,xx,yy,hex) end end
end
local function line(image,x,y,X,Y,hex)
  local steps=math.max(math.abs(X-x),math.abs(Y-y))
  if steps==0 then pixel(image,x,y,hex);return end
  for i=0,steps do pixel(image,math.floor(x+(X-x)*i/steps+.5),math.floor(y+(Y-y)*i/steps+.5),hex) end
end
local function polygon(image,points,hex)
  for y=0,31 do for x=0,31 do
    local inside=false;local j=#points
    for i=1,#points do
      local a,b=points[i],points[j]
      if (a[2]>y)~=(b[2]>y) and x<(b[1]-a[1])*(y-a[2])/(b[2]-a[2])+a[1] then inside=not inside end
      j=i
    end
    if inside then pixel(image,x,y,hex) end
  end end
  for i,a in ipairs(points) do local b=points[i%#points+1];line(image,a[1],a[2],b[1],b[2],hex) end
end
local function layer(sprite,name)
  local result=sprite:newLayer();result.name=name;return result
end

local sprite
if app.params.exportOnly=='true' then
  sprite=Sprite{fromFile=sourcePath}
else
  -- Match the existing five technology materials; do not maintain a second palette table.
  local file=assert(io.open(assets..'industry/technology.json','r'))
  local catalog=file:read('*a');file:close()
  local palettes={}
  for values in catalog:gmatch('"colors"%s*:%s*%[(.-)%]') do
    local palette={};for hex in values:gmatch('"(%x%x%x%x%x%x)"') do palette[#palette+1]=hex end
    palettes[#palettes+1]=palette
  end
  assert(#palettes>=5,'Missing technology palettes')
  sprite=Sprite(32,32,ColorMode.RGB)
  local reference=sprite.layers[1];reference.name='00 / previous shared icon reference'
  reference.isVisible=false
  local old=Image{fromFile=assets..'textures/block/mana_beacon.png'}
  local orbit=layer(sprite,'01 / broken rune orbit')
  local pedestal=layer(sprite,'02 / tier material pedestal')
  local crystal=layer(sprite,'03 / crystal facets')
  local accents=layer(sprite,'04 / rune marks and highlights')
  for tier=1,5 do
    local frame=tier==1 and sprite.frames[1] or sprite:newEmptyFrame(tier)
    if tier==1 then reference:cel(1).image=old else sprite:newCel(reference,frame,old,Point(0,0)) end
    local palette=palettes[tier]
    local dark,main,light=table.unpack(palette)
    local edge=shade(dark,.52);local stone=shade(dark,.78)
    local ring=Image(32,32,ColorMode.RGB)
    -- Leave the orbit open around the crystal; discrete marks remain legible at item size.
    line(ring,4,11,8,8,dark);line(ring,8,8,11,8,main)
    line(ring,21,8,24,9,main);line(ring,24,9,28,13,dark)
    line(ring,3,17,6,21,dark);line(ring,6,21,10,22,main)
    line(ring,22,22,26,20,main);line(ring,26,20,28,17,dark)
    sprite:newCel(orbit,frame,ring,Point(0,0))
    local base=Image(32,32,ColorMode.RGB)
    polygon(base,{{9,23},{22,23},{27,26},{27,28},{23,30},{8,30},{4,28},{4,26}},edge)
    polygon(base,{{9,24},{22,24},{25,26},{22,27},{9,27},{6,26}},main)
    line(base,9,24,21,24,light);line(base,6,26,9,27,shade(main,1.18))
    rect(base,8,28,16,1,stone);line(base,9,29,22,29,dark)
    line(base,24,27,24,28,dark)
    rect(base,11,25,9,1,shade(main,.72))
    sprite:newCel(pedestal,frame,base,Point(0,0))
    local gem=Image(32,32,ColorMode.RGB)
    polygon(gem,{{15,2},{16,2},{21,9},{21,16},{16,22},{15,22},{10,16},{10,9}},edge)
    polygon(gem,{{15,4},{11,10},{11,15},{15,20},{15,11}},main)
    polygon(gem,{{16,4},{20,10},{16,11}},light)
    polygon(gem,{{16,12},{20,11},{20,15},{16,20}},shade(main,.69))
    polygon(gem,{{15,5},{12,10},{15,11}},shade(light,.87))
    line(gem,15,12,15,19,shade(main,1.12))
    line(gem,11,10,11,14,light);pixel(gem,12,16,shade(light,.83))
    line(gem,17,18,19,16,main)
    sprite:newCel(crystal,frame,gem,Point(0,0))
    local marks=Image(32,32,ColorMode.RGB)
    -- Two small rune diamonds and a levitating cap link the icon to its placed tower.
    line(marks,3,12,1,15,main);line(marks,1,15,3,17,main)
    line(marks,3,17,5,15,light);line(marks,5,15,3,12,main)
    line(marks,28,12,26,15,main);line(marks,26,15,28,17,main)
    line(marks,28,17,30,15,light);line(marks,30,15,28,12,main)
    pixel(marks,3,14,light);pixel(marks,28,14,light)
    rect(marks,14,23,4,1,light)
    pixel(marks,14,7,'f6fcff');line(marks,13,8,13,10,light)
    pixel(marks,7,5,main);pixel(marks,7,4,light);pixel(marks,8,5,light)
    pixel(marks,24,5,main);pixel(marks,24,6,light)
    pixel(marks,12,28,light);line(marks,15,27,15,28,light);pixel(marks,18,28,light)
    sprite:newCel(accents,frame,marks,Point(0,0))
    local tag=sprite:newTag(tier,tier);tag.name='mana_beacon_t'..tier
  end
  reference.isEditable=false
  sprite:saveAs(sourcePath)
end

app.fs.makeAllDirectories(assets..'textures/item')
app.fs.makeAllDirectories(previewPath)
local overview=Image(1080,256,ColorMode.RGB)
overview:clear(color('202431'))
local glyphs={T={'111','010','010','010','010'},
  ['1']={'010','110','010','010','111'},['2']={'110','001','010','100','111'},
  ['3']={'110','001','010','001','110'},['4']={'101','101','111','001','001'},
  ['5']={'111','100','110','001','110'}}
for _,tag in ipairs(sprite.tags) do
  local tier=tonumber(tag.name:match('^mana_beacon_t([1-5])$'))
  if tier then
    local image=Image(sprite.spec);image:drawSprite(sprite,tag.fromFrame.frameNumber)
    image:saveAs(assets..'textures/item/'..tag.name..'.png')
    local left=(tier-1)*216+12
    for y=0,31 do for x=0,31 do
      local value=image:getPixel(x,y)
      if pc.rgbaA(value)>0 then
        for yy=0,5 do for xx=0,5 do overview:drawPixel(left+x*6+xx,8+y*6+yy,value) end end
      end
    end end
    overview:drawImage(image,Point(left+80,210))
    for i,char in ipairs({'T',tostring(tier)}) do
      for y,row in ipairs(glyphs[char]) do for x=1,3 do
        if row:sub(x,x)=='1' then rect(overview,left+66+(i-1)*12+x*2,246+y-1,2,1,'b9c3d8') end
      end end
    end
  end
end
overview:saveAs(previewPath..'/overview.png')
sprite:close()

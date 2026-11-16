-- Create Team / Creators of Create pixel bases. Pinned source and upstream asset license:
-- industrial_fluid_pipes_create.json; 32px atlases are split into 16px tiles, never downscaled.
-- Aseprite --batch --script-param root=<checkout> --script tools/draw_industrial_fluid_pipes.lua
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block/'
local reference=root..'/temp/create-pipe-reference/'
local sourcePath=root..'/tools/industrial_fluid_pipes.aseprite'
local pc=app.pixelColor
local references={}
local old=nil
local function referenceCrop(entry)
  local path=reference..'src__main__resources__assets__create__textures__block__'..entry.source..'.png'
  if app.fs.isFile(path) then
    if not references[path] then
      local doc=app.open(path);local flat=Image(doc.width,doc.height,ColorMode.RGB);flat:drawSprite(doc,1);references[path]={doc=doc,image=flat}
    end
    local image=Image(16,16,ColorMode.RGB);local original=references[path].image
    for y=0,15 do for x=0,15 do image:drawPixel(x,y,original:getPixel(x+entry.x,y+entry.y)) end end
    return image
  end
  if not old then old=app.open(sourcePath) end
  for _,tag in ipairs(old.tags) do if tag.name==entry.name then return Image(old.layers[1]:cel(tag.fromFrame.frameNumber).image) end end
  error('Missing Create pixel base: '..entry.name)
end
local entries={}
for grade=1,3 do
  for _,kind in ipairs({'metal','rim','junction'}) do
    table.insert(entries,{name='fluid_pipe_'..kind..'_t'..grade,grade=grade,source='pipes',x=kind=='rim' and 16 or 0,y=kind=='metal' and 0 or 16})
  end
  for tile=0,3 do table.insert(entries,{name='fluid_pipe_connected_'..tile..'_t'..grade,grade=grade,source='pipes_connected',x=(tile%2)*16,y=math.floor(tile/2)*16}) end
  table.insert(entries,{name='fluid_pipe_glass_t'..grade,grade=grade,source='glass_fluid_pipe',x=0,y=0})
end
table.insert(entries,{name='fluid_pipe_input',arrow=1,color={60,169,255}})
table.insert(entries,{name='fluid_pipe_extract',arrow=-1,color={255,165,62}})
local sprite=Sprite(16,16,ColorMode.RGB)
local base=sprite.layers[1];base.name='Create Team original bases (locked)'
local recolor=sprite:newLayer();recolor.name='Vanadium steel and niobium titanium recolors'
local marks=sprite:newLayer();marks.name='Machine port direction arrows'
for index,entry in ipairs(entries) do
  if index>1 then sprite:newEmptyFrame() end
  local original=entry.arrow and Image(16,16,ColorMode.RGB) or referenceCrop(entry)
  local changes=Image(16,16,ColorMode.RGB);local arrow=Image(16,16,ColorMode.RGB)
  if entry.grade and entry.grade>1 then
    local target=entry.grade==2 and {120,141,146} or {143,145,188}
    for y=0,15 do for x=0,15 do
      local pixel=original:getPixel(x,y);local r,g,b,a=pc.rgbaR(pixel),pc.rgbaG(pixel),pc.rgbaB(pixel),pc.rgbaA(pixel)
      if a>0 and r>g*1.12 and r>b*1.15 then
        local offset=(r*.299+g*.587+b*.114-124)*.95
        local function channel(c) return math.max(0,math.min(255,math.floor(c+offset+.5))) end
        changes:drawPixel(x,y,pc.rgba(channel(target[1]),channel(target[2]),channel(target[3]),a))
      end
    end end
  end
  if entry.arrow then
    for y=2,13 do for x=2,13 do
      local yy=entry.arrow==1 and y or 15-y
      if (yy>=3 and yy<=7 and math.abs(x-7.5)<=yy-2) or (yy>=7 and yy<=12 and x>=6 and x<=9) then
        local delta=x<=7 and 27 or -15
        arrow:drawPixel(x,y,pc.rgba(math.min(255,entry.color[1]+delta),math.min(255,entry.color[2]+delta),math.min(255,entry.color[3]+delta),255))
      end
    end end
  end
  sprite:newCel(base,index,original,Point(0,0));sprite:newCel(recolor,index,changes,Point(0,0));sprite:newCel(marks,index,arrow,Point(0,0))
  local tag=sprite:newTag(index,index);tag.name=entry.name
  local flat=Image(sprite.spec);flat:drawSprite(sprite,index);flat:saveAs(output..entry.name..'.png')
end
base.isEditable=false
if old then old:close() end
for _,entry in pairs(references) do entry.doc:close() end
sprite:saveAs(sourcePath);sprite:close()

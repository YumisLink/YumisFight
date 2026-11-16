-- Export a saved 12x14 pipe material draft, preserving hand edits and native pixel sizes.
-- Aseprite --batch --script-param root=<checkout> --script this-file
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local source=Sprite{fromFile=root..'/tools/fluid_pipe_materials_12x14.aseprite'}
assert(source.width==64 and source.height==64,'Keep the 64x64 source atlas')
local masterFrame=tonumber(app.params.masterFrame) or (#source.frames>=4 and 4 or nil)
if masterFrame then assert(source.frames[masterFrame],'Missing requested master frame');app.frame=source.frames[masterFrame] end
local output=root..'/temp/fluid-pipes/materials_12x14/'
local production=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block/'
app.fs.makeAllDirectories(output)
local entries={}
for _,slice in ipairs(source.slices) do
  local b=slice.bounds
  assert(slice.name:match('^[a-z0-9_]+$'),'Unexpected part name')
  entries[#entries+1]=string.format('{"id":"%s","x":%d,"y":%d,"width":%d,"height":%d}',slice.name,b.x,b.y,b.width,b.height)
end
local manifest=assert(io.open(output..'layout.json','w'))
local layoutText='{"canvas":[64,64],"parts":['..table.concat(entries,',')..']}\n'
manifest:write(layoutText);manifest:close()
local modelLayout=assert(io.open(root..'/tools/fluid_pipe_materials_12x14_layout.json','w'))
modelLayout:write(layoutText);modelLayout:close()
local count=0
local original={}
for grade=1,3 do original[grade]=Image(source.spec);original[grade]:drawSprite(source,grade) end
local master
if masterFrame then master=Image(source.spec);master:drawSprite(source,masterFrame) end
for grade=1,3 do
  local tagName='t'..grade
  local flat=master and Image(master) or Image(original[grade])
  if master and grade~=3 then
    -- Map the original purple metal palette to each grade; preserve the user's new
    -- cyan glass edges, custom colors and alpha from the complete fourth-frame design.
    local palette={}
    for y=0,63 do for x=0,63 do
      local from=original[3]:getPixel(x,y);local to=original[grade]:getPixel(x,y)
      if app.pixelColor.rgbaA(from)>0 and app.pixelColor.rgbaA(to)>0 then palette[from]=to end
    end end
    for y=0,63 do for x=0,63 do
      local pixel=flat:getPixel(x,y)
      if palette[pixel] then flat:drawPixel(x,y,palette[pixel]) end
    end end
  end
  flat:saveAs(output..'atlas_'..tagName..'.png')
  flat:saveAs(production..'fluid_pipe_12x14_'..tagName..'.png')
  for _,slice in ipairs(source.slices) do
    assert(slice.name:match('^[a-z0-9_]+$'),'Unexpected part name')
    local b=slice.bounds
    local image=Image(b.width,b.height,ColorMode.RGB)
    image:drawImage(flat,Point(-b.x,-b.y))
    image:saveAs(output..slice.name..'_'..tagName..'.png')
    count=count+1
  end
end
source:close()
-- Native 14x2 port decals match the new collar's physical surface without squeezing 16px art.
local portPath=root..'/tools/fluid_pipe_ports_14x2.aseprite'
if not app.fs.isFile(portPath) then
  local ports=Sprite(14,2,ColorMode.RGB);ports.layers[1].name='Native direction marks - editable'
  for i,entry in ipairs({{'input',{60,169,255}},{'extract',{255,165,62}}}) do
    if i>1 then ports:newEmptyFrame() end
    local mark=Image(ports.spec);local color=entry[2]
    for y=0,1 do for x=5,8 do
      local row=i==1 and y or 1-y
      if row==0 or x==6 or x==7 then mark:drawPixel(x,y,app.pixelColor.rgba(color[1],color[2],color[3],255)) end
    end end
    ports:newCel(ports.layers[1],i,mark,Point(0,0));local tag=ports:newTag(i,i);tag.name=entry[1]
  end
  for _,tag in ipairs(ports.tags) do tag.toFrame=tag.fromFrame end
  ports:saveAs(portPath);ports:close()
end
local ports=Sprite{fromFile=portPath}
assert(ports.width==14 and ports.height==2,'Port decals must stay 14x2')
for _,tag in ipairs(ports.tags) do
  assert(tag.name=='input' or tag.name=='extract','Unexpected port direction')
  local mark=Image(ports.spec);mark:drawSprite(ports,tag.fromFrame.frameNumber)
  if tag.name=='input' then
    -- Flat machine input is 12px wide: discard one transparent margin pixel per end.
    local flat=Image(12,2,ColorMode.RGB);flat:drawImage(mark,Point(-1,0));mark=flat
  end
  mark:saveAs(production..'fluid_pipe_'..tag.name..'.png')
end
ports:close()
print('Exported 3 production atlases, '..count..' native-size parts and native 12x2 input / 14x2 extract marks; sources preserved.')

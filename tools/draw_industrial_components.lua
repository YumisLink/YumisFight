-- Reference-based industrial components, kept at each original pixel size.
-- Initial bases come from the local reference library; subsequent runs can
-- recover the unchanged base layer from the editable Aseprite documents.
-- Run with --script-param root=<checkout>; preview=true writes only to temp.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local preview=app.params.preview=='true'
local output=root..'/temp/industrial-component-bases'
app.fs.makeAllDirectories(output)
local pc=app.pixelColor

local function colors(values)
  local mapped={}
  for old,new in values:gmatch('(%x%x%x%x%x%x):(%x%x%x%x%x%x)') do
    mapped[old:lower()]=pc.rgba(tonumber(new:sub(1,2),16),tonumber(new:sub(3,4),16),tonumber(new:sub(5,6),16),255)
  end
  return mapped
end

local specs={
  {
    id='composite_conductor',size=16,
    input=root..'/temp/industrial-vanilla/raw_copper.png',
    map=colors([[
      243b34:373344 2a5447:514d65 3a7463:747689 4d8d79:a4b6c4
      4fba98:75cad3 84e2c5:c3eeeb c9faec:edfff9
      6d3421:56382d 883f26:7d4b32 a54926:ac6538 c95e35:cb8b4e
      e97a52:e7b36d fc9982:f4d29b fec3b6:fff0c4
      946d3d:ae8c58 635b40:776c58
    ]]),
  },
  {
    id='lattice_substrate',size=16,
    input=root..'/temp/material-texture-references-20261114/IC2/textures/items/crafting/carbon_mesh.png',
    map=colors('000000:3b3444 191919:766e7d 2d2d2d:b6b6c1 2e2e2e:d9e1e0'),
    -- Short crystalline highlights follow the existing diagonal weave.
    surface={['6,3']='a3dedc',['7,4']='b7ece7',['8,5']='8fcbd0',
      ['10,7']='98d2d5',['11,8']='b1e8e3',['9,10']='9bd8d7',['10,11']='b4ece6'},
  },
  {
    id='control_component',size=16,
    input=root..'/temp/nuclear-magic-material-references-20261114/Mekanism/mekanism/textures/item/basic_control_circuit.png',
    map=colors([[
      245a3b:403650 247847:6b596b 1ea05c:a47753 1ea05d:a47753
      0bc775:d19b5e 07f799:f0d28e a4ffd9:a8e9e2 d9ffff:e3fffa
    ]]),
  },
  {
    id='mana_superconductor',size=16,
    input=root..'/temp/industrial-vanilla/iron_ingot.png',
    map=colors([[
      353535:292338 585858:3e365a 5e5e5e:504777 727272:6965a4
      828282:797cb4 a8a8a8:9b9bd5 d8d8d8:c5e3eb ffffff:f3ffff
    ]]),
  },
  {
    id='ignition_core_shell',size=16,glass=true,
    input=root..'/temp/nuclear-magic-material-references-20261114/RuneCraft/runecraft/textures/item/orb.png',
  },
  {
    id='fusion_ignition_core',size=16,glass=true,sun=true,
    input=root..'/temp/nuclear-magic-material-references-20261114/RuneCraft/runecraft/textures/item/orb.png',
  },
}

local function baseFromFile(path,size)
  local original=Sprite{fromFile=path}
  app.activeSprite=original
  app.command.ChangePixelFormat{format='rgb'}
  assert(original.width==size and original.height==size,'Unexpected reference size: '..path)
  local image=Image(original.spec);image:drawSprite(original,1)
  original:close()
  return image
end

local function baseFromPrevious(previous,id)
  if not previous then return nil end
  for _,tag in ipairs(previous.tags) do
    if tag.name==id then
      local cel=previous.layers[1]:cel(tag.fromFrame.frameNumber)
      assert(cel,'Missing locked reference base: '..id)
      local image=Image(previous.spec)
      image:drawImage(cel.image,cel.position)
      return image
    end
  end
  return nil
end

local function makeDocument(filename,size,selected)
  local sourcePath=root..'/tools/'..filename..'.aseprite'
  local file=io.open(sourcePath,'rb')
  local previous
  if file then file:close();previous=Sprite{fromFile=sourcePath} end
  local sprite=Sprite(size,size,ColorMode.RGB)
  sprite.colorSpace=ColorSpace{sRGB=true}
  local original=sprite.layers[1];original.name='01 / locked original reference';original.isEditable=false;original.isVisible=false
  local sunLayer=sprite:newLayer();sunLayer.name='02 / solar core'
  local changes=sprite:newLayer();changes.name='03 / material and glass surface'
  for index,s in ipairs(selected) do
    local base=baseFromPrevious(previous,s.id) or baseFromFile(s.input,size)
    local overlay=Image(size,size,ColorMode.RGB)
    local sun=Image(size,size,ColorMode.RGB)
    local function inside(x,y)
      return x>=0 and y>=0 and x<size and y<size and pc.rgbaA(base:getPixel(x,y))>0
    end
    for y=0,size-1 do for x=0,size-1 do
      local c=base:getPixel(x,y)
      local alpha=pc.rgbaA(c)
      if alpha>0 then
        if s.glass then
          local edge=not (inside(x-1,y) and inside(x+1,y) and inside(x,y-1) and inside(x,y+1))
          local inner=not (inside(x-2,y) and inside(x+2,y) and inside(x,y-2) and inside(x,y+2))
          local glass=edge and pc.rgba(120,172,195,222) or inner and pc.rgba(164,214,230,98) or pc.rgba(179,222,232,30)
          overlay:drawPixel(x,y,glass)
        else
          local key=('%02x%02x%02x'):format(pc.rgbaR(c),pc.rgbaG(c),pc.rgbaB(c))
          local mapped=assert(s.map[key],('Unmapped reference pixel %s in %s'):format(key,s.id))
          -- Cool reflection across the existing top facet, keeping its pixel role.
          if s.id=='mana_superconductor' and key=='a8a8a8' and x>=6 and x<=11 and y>=4 and y<=8 then
            mapped=pc.rgba(142,205,224,255)
          end
          local detail=s.surface and s.surface[x..','..y]
          if detail then
            mapped=pc.rgba(tonumber(detail:sub(1,2),16),tonumber(detail:sub(3,4),16),tonumber(detail:sub(5,6),16),255)
          end
          overlay:drawPixel(x,y,pc.rgba(pc.rgbaR(mapped),pc.rgbaG(mapped),pc.rgbaB(mapped),alpha))
        end
      end
    end end
    if s.glass then
      local function mark(x,y,r,g,b,a) if inside(x,y) then overlay:drawPixel(x,y,pc.rgba(r,g,b,a)) end end
      mark(5,4,224,250,255,255);mark(6,3,233,252,255,245);mark(7,3,224,249,255,220)
      mark(4,5,224,250,255,246);mark(4,6,211,245,253,215);mark(3,7,198,237,249,200)
      mark(11,9,153,217,236,160);mark(10,11,179,229,243,190);mark(9,12,191,236,248,180)
    end
    if s.sun then
      for y=3,12 do for x=3,12 do
        local distance=(x-7.5)^2+(y-7.5)^2
        if distance<=24 and inside(x,y) then
          local c=distance>18 and pc.rgba(218,88,26,255) or distance>11 and pc.rgba(251,151,35,255)
            or distance>5 and pc.rgba(255,206,75,255) or pc.rgba(255,245,173,255)
          sun:drawPixel(x,y,c)
        end
      end end
      sun:drawPixel(6,6,pc.rgba(255,249,204,255))
      sun:drawPixel(9,9,pc.rgba(255,185,51,255))
      sun:saveAs(preview and (output..'/fusion_ignition_core-emissive.png') or
        (root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/fusion_ignition_core_emissive.png'))
    end
    local frame=index==1 and sprite.frames[1] or sprite:newEmptyFrame(index)
    if index==1 then original:cel(1).image=base else sprite:newCel(original,frame,base,Point(0,0)) end
    if s.sun then sprite:newCel(sunLayer,frame,sun,Point(0,0)) end
    sprite:newCel(changes,frame,overlay,Point(0,0))
    if preview then
      base:saveAs(output..'/'..s.id..'-base.png')
      local composite=Image(sprite.spec);composite:drawSprite(sprite,index)
      composite:saveAs(output..'/'..s.id..'-edited.png')
    end
  end
  for i,s in ipairs(selected) do local tag=sprite:newTag(i,i);tag.name=s.id end
  sprite:saveAs(preview and (output..'/'..filename..'.aseprite') or sourcePath)
  sprite:close()
  if previous then previous:close() end
end

makeDocument('industrial_component_bases',16,specs)

if not preview then
  -- Move only the selected tags out of the old document; keep all other cels.
  local moved={}
  for _,s in ipairs(specs) do moved[s.id]=true end
  local old=Sprite{fromFile=root..'/tools/industrial_materials.aseprite'}
  local tags,frames={},{}
  for _,tag in ipairs(old.tags) do
    if moved[tag.name] then
      assert(tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Expected one frame: '..tag.name)
      tags[#tags+1]=tag;frames[#frames+1]=tag.fromFrame.frameNumber
    end
  end
  for _,tag in ipairs(tags) do old:deleteTag(tag) end
  table.sort(frames,function(a,b)return a>b end)
  for _,frame in ipairs(frames) do old:deleteFrame(old.frames[frame]) end
  if #frames>0 then old:saveAs(root..'/tools/industrial_materials.aseprite') end
  old:close()
end
print((preview and 'Previewed ' or 'Authored ')..#specs..' reference-based components.')

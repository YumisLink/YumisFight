-- Export the editable ore frames, separate glow masks, and same-size cube models.
-- Aseprite --batch --script-param root=<checkout> --script this-file
-- source=<file> and output=<directory> can be used for a temporary art preview.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local definitions=dofile(root..'/tools/industrial_ore_definitions.lua')
local sourcePath=app.params.source or root..'/tools/industrial_ores.aseprite'
local preview=app.params.output~=nil
local assets=root..'/src/main/resources/assets/yumis_dungeon_and_fight'
local textures=app.params.output or assets..'/textures/block'
app.fs.makeAllDirectories(textures)

local source=Sprite{fromFile=sourcePath}
assert(source.width==16 and source.height==16,'Expected native 16x16 ore source')
local maskLayer
for _,layer in ipairs(source.layers) do
  if layer.name=='03 / emissive mask (hidden)' then maskLayer=layer end
end
assert(maskLayer and not maskLayer.isVisible,'The emissive mask layer must exist and remain hidden')
local tags={}
for _,tag in ipairs(source.tags) do
  assert(tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Ore tags must select one frame')
  assert(not tags[tag.name],'Duplicate ore tag: '..tag.name)
  tags[tag.name]=tag.fromFrame.frameNumber
end

local pc=app.pixelColor
local emission={[3]=6,[4]=10,[5]=14}
local directions={'down','up','north','south','west','east'}
local function faces(texture)
  local entries={}
  for _,side in ipairs(directions) do
    entries[#entries+1]=string.format('        "%s": {"texture": "#%s", "cullface": "%s"}',side,texture,side)
  end
  return table.concat(entries,',\n')
end

for _,ore in ipairs(definitions) do
  local name=ore.id..'_ore'
  local frame=assert(tags[name],'Missing ore frame: '..name)
  local image=Image(source.spec);image:drawSprite(source,frame)
  image:saveAs(textures..'/'..name..'.png')
  if ore.tier>=3 then
    local cel=assert(maskLayer:cel(frame),'Missing emissive mask: '..name)
    local mask=cel.image
    local rock=Image(source.spec)
    local glow=Image(source.spec)
    for y=0,15 do for x=0,15 do
      local p=image:getPixel(x,y)
      local mx,my=x-cel.position.x,y-cel.position.y
      local selected=mx>=0 and my>=0 and mx<mask.width and my<mask.height and pc.rgbaA(mask:getPixel(mx,my))>0
      if selected then glow:drawPixel(x,y,p)
      else rock:drawPixel(x,y,p) end
    end end
    rock:saveAs(textures..'/'..name..'_base.png')
    glow:saveAs(textures..'/'..name..'_emissive.png')
    if not preview then
      -- The two disjoint textures cover the same cube faces. They never overlap,
      -- so only the mineral pixels receive light emission and no Z-fighting occurs.
      local output=assert(io.open(assets..'/models/block/'..name..'.json','w'))
      output:write(string.format([[{
  "parent": "minecraft:block/cube_all",
  "render_type": "minecraft:cutout",
  "textures": {
    "particle": "yumis_dungeon_and_fight:block/%s",
    "all": "yumis_dungeon_and_fight:block/%s_base",
    "glow": "yumis_dungeon_and_fight:block/%s_emissive"
  },
  "elements": [
    {
      "from": [0, 0, 0], "to": [16, 16, 16],
      "faces": {
%s
      }
    },
    {
      "from": [0, 0, 0], "to": [16, 16, 16],
      "light_emission": %d,
      "shade": false,
      "faces": {
%s
      }
    }
  ]
}
]],name,name,name,faces('all'),emission[ore.tier],faces('glow')))
      output:close()
    end
  elseif not preview then
    local output=assert(io.open(assets..'/models/block/'..name..'.json','w'))
    output:write(string.format([[{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "yumis_dungeon_and_fight:block/%s"
  }
}
]],name))
    output:close()
  end
end
source:close()
print('Exported '..#definitions..' industrial ore textures.')

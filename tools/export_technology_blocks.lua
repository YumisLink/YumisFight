-- Export separate glass, emissive edges and cores for five nested cube models.
-- Run Aseprite --batch --script-param root=<checkout> --script this-file.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local assets=root..'/src/main/resources/assets/yumis_dungeon_and_fight'
local sprite=Sprite{fromFile=root..'/tools/technology_blocks.aseprite'}
assert(sprite.width==16 and sprite.height==16,'Technology block faces must stay 16px')
local frames={}
for _,tag in ipairs(sprite.tags) do frames[tag.name]=tag end
local layers={}
for _,layer in ipairs(sprite.layers) do layers[layer.name]=layer end
local function imageFromLayer(name,frame)
  local layer=assert(layers[name],'Missing matrix layer: '..name)
  local cel=assert(layer:cel(frame),'Missing matrix cel: '..name)
  local image=Image(sprite.spec);image:drawImage(cel.image,cel.position);return image
end
-- Use the actual block outline as the model bounds, keeping picking/collision aligned.
local shapeFile=assert(io.open(root..'/src/main/java/cn/yumis/fight/modules/industry/platform/TechnologyBlock.java','r'))
local shape=assert(shapeFile:read('*a'):match('SHAPE%s*=%s*Block%.box%((.-)%)'),'Missing matrix outline');shapeFile:close()
local bounds={}
for value in shape:gmatch('[%d.]+') do bounds[#bounds+1]=tonumber(value) end
assert(#bounds==6,'Expected six matrix outline coordinates')
local directions={'down','up','north','south','west','east'}
local function element(texture,inset,emission,shade)
  local faces={}
  for _,side in ipairs(directions) do
    faces[#faces+1]='        "'..side..'": {"texture": "#'..texture..'", "uv": [0, 0, 16, 16]}'
  end
  return string.format([[    {
      "from": [%g, %g, %g], "to": [%g, %g, %g],
      "light_emission": %d,
      "shade": %s,
      "faces": {
%s
      }
    }]],bounds[1]+inset,bounds[2]+inset,bounds[3]+inset,
    bounds[4]-inset,bounds[5]-inset,bounds[6]-inset,emission,tostring(shade),table.concat(faces,',\n'))
end
for tier=2,6 do
  local name='tech_block_t'..tier
  local tag=assert(frames[name],'Missing matrix face: '..name)
  assert(tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Matrix faces are static')
  local frame=tag.fromFrame.frameNumber
  local glass=imageFromLayer('01 / outer glass',frame)
  glass:drawImage(imageFromLayer('02 / outer reflections',frame),Point(0,0))
  glass:saveAs(assets..'/textures/block/'..name..'.png')
  imageFromLayer('03 / outer emissive edges',frame):saveAs(assets..'/textures/block/'..name..'_emissive.png')
  imageFromLayer('04 / inner luminous core (hidden)',frame):saveAs(assets..'/textures/block/'..name..'_core.png')
  local model=assert(io.open(assets..'/models/block/'..name..'.json','w'))
  model:write(string.format([[{
  "parent": "minecraft:block/cube_all",
  "render_type": "minecraft:translucent",
  "ambientocclusion": false,
  "textures": {
    "particle": "yumis_dungeon_and_fight:block/%s_core",
    "all": "yumis_dungeon_and_fight:block/%s",
    "glow": "yumis_dungeon_and_fight:block/%s_emissive",
    "core": "yumis_dungeon_and_fight:block/%s_core"
  },
  "elements": [
%s,
%s,
%s
  ]
}
]],name,name,name,name,element('core',2,15,false),element('all',0,0,true),element('glow',0,15,false)))
  model:close()
end
sprite:close()
print('Exported five smaller double-layer matrices, with separate emissive edges and cores.')

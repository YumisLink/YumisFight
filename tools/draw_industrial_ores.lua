-- Recolour only the original mineral pixels of the vanilla 16x16 iron ore.
-- Aseprite --batch --script-param root=<checkout> --script this-file
-- preview=true writes an editable sample under temp instead of the source file.
-- vanilla=<directory> may point to the matching Minecraft version's iron_ore.png.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local vanilla=(app.params.vanilla or root..'/temp/industrial-vanilla'):gsub('\\','/'):gsub('/+$','')
local preview=app.params.preview=='true'
local definitions=dofile(root..'/tools/industrial_ore_definitions.lua')
local pc=app.pixelColor

local template=Sprite{fromFile=vanilla..'/iron_ore.png'}
app.activeSprite=template
app.command.ChangePixelFormat{format='rgb'}
assert(template.width==16 and template.height==16,'Expected the native 16x16 iron ore texture')
local base=Image(template.spec);base:drawSprite(template,1);template:close()

local sprite=Sprite(16,16,ColorMode.RGB)
sprite.colorSpace=ColorSpace{sRGB=true}
local original=sprite.layers[1];original.name='01 / vanilla iron ore';original.isEditable=false
local material=sprite:newLayer();material.name='02 / mineral colour'
local emissive=sprite:newLayer();emissive.name='03 / emissive mask (hidden)';emissive.isVisible=false

local function rgb(hex)
  return tonumber(hex:sub(1,2),16),tonumber(hex:sub(3,4),16),tonumber(hex:sub(5,6),16)
end

for i,ore in ipairs(definitions) do
  local frame=i==1 and sprite.frames[1] or sprite:newEmptyFrame(i)
  if i==1 then original:cel(1).image=base else sprite:newCel(original,frame,base,Point(0,0)) end
  local colour=Image(16,16,ColorMode.RGB)
  local mask=Image(16,16,ColorMode.RGB)
  for y=0,15 do for x=0,15 do
    local p=base:getPixel(x,y)
    local r,g,b,a=pc.rgbaR(p),pc.rgbaG(p),pc.rgbaB(p),pc.rgbaA(p)
    if a>0 and r>g+5 and g>b+3 then
      local light=.2126*r+.7152*g+.0722*b
      local tone=light<111 and 1 or light<130 and 2 or light<165 and 3 or light<190 and 4 or 5
      local red,green,blue=rgb(ore.colors[tone])
      colour:drawPixel(x,y,pc.rgba(red,green,blue,a))
      if ore.tier>=3 then mask:drawPixel(x,y,pc.rgba(255,255,255,a)) end
    end
  end end
  sprite:newCel(material,frame,colour,Point(0,0))
  sprite:newCel(emissive,frame,mask,Point(0,0))
end
for i,ore in ipairs(definitions) do
  local tag=sprite:newTag(i,i);tag.name=ore.id..'_ore'
end
if preview then app.fs.makeAllDirectories(root..'/temp/ore-art') end
sprite:saveAs(root..(preview and '/temp/ore-art/industrial_ores.aseprite' or '/tools/industrial_ores.aseprite'))
sprite:close()
print('Authored '..#definitions..' native ore textures with the original mineral layout.')

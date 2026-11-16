-- Rebuild the item sprites from the supplied pixel-art reference sheet.
-- Run Aseprite in batch mode with --script-param root=<project> --script tools/industrial_item_art.lua.
local root = assert(app.params.root):gsub('\\', '/') .. '/'
local source = assert(app.open(root .. 'tools/assets/industrial_item_reference.png'))
local reference = source.cels[1].image
local target = root .. 'src/main/resources/assets/yumis_dungeon_and_fight/textures/item/'
local pc = app.pixelColor
local selected = nil
if app.params.items then
  selected = {}
  for name in app.params.items:gmatch('[^,]+') do selected[name] = true end
end

local function color(hex)
  return {tonumber(hex:sub(1,2),16), tonumber(hex:sub(3,4),16), tonumber(hex:sub(5,6),16)}
end

-- Maintain the reference's shading while replacing the colored content.
local function tint(r, g, b, destination)
  local peak = math.max(r,g,b) / 255
  local c = color(destination)
  return math.floor(c[1]*peak+.5), math.floor(c[2]*peak+.5), math.floor(c[3]*peak+.5)
end

local function sprite(name, row, col, options)
  if selected and not selected[name] then return end
  options = options or {}
  local sx, sy = 2 + (col-1)*22, 2 + (row-1)*22
  local x0, y0, x1, y1 = 14, 14, -1, -1
  for y=0,13 do for x=0,13 do
    if pc.rgbaA(reference:getPixel(sx+x,sy+y)) > 0 then
      x0, y0, x1, y1 = math.min(x0,x), math.min(y0,y), math.max(x1,x), math.max(y1,y)
    end
  end end
  assert(x1 >= 0, 'Empty reference cell ' .. row .. '-' .. col)
  local ox, oy = math.floor((16-(x1-x0+1))/2), math.floor((16-(y1-y0+1))/2)
  local spr = Sprite(16,16,ColorMode.RGB)
  local image = spr.cels[1].image
  image:clear()
  for y=y0,y1 do for x=x0,x1 do
    local pixel = reference:getPixel(sx+x,sy+y)
    local a = pc.rgbaA(pixel)
    if a > 0 then
      local r,g,b = pc.rgbaR(pixel),pc.rgbaG(pixel),pc.rgbaB(pixel)
      local selected = options.filter and options.filter(r,g,b,x-x0,y-y0)
      if selected then
        local dest = options.tint
        if options.split and y-y0 >= options.split then dest = options.tint2 end
        r,g,b = tint(r,g,b,dest)
      end
      image:drawPixel(ox+x-x0,oy+y-y0,pc.rgba(r,g,b,a))
    end
  end end
  if options.cleanInterior then
    local fill = color(options.cleanInterior)
    for y=5,13 do for x=4,11 do
      local pixel = image:getPixel(x,y)
      local r,g,b = pc.rgbaR(pixel),pc.rgbaG(pixel),pc.rgbaB(pixel)
      if pc.rgbaA(pixel) > 0 and r+g+b < 155 then
        -- Replace isolated near-black reference markings with a shaded canister fill.
        local scale = y >= 12 and .48 or .65
        image:drawPixel(x,y,pc.rgba(math.floor(fill[1]*scale),math.floor(fill[2]*scale),math.floor(fill[3]*scale),255))
      end
    end end
  end
  if options.outline then
    local original = image:clone()
    local function transparent(x,y)
      return x < 0 or y < 0 or x >= 16 or y >= 16 or pc.rgbaA(original:getPixel(x,y)) == 0
    end
    for y=0,15 do for x=0,15 do
      local pixel = original:getPixel(x,y)
      if pc.rgbaA(pixel) > 0 and (transparent(x-1,y) or transparent(x+1,y) or transparent(x,y-1) or transparent(x,y+1)) then
        -- Darken only existing edge pixels, so the original silhouette stays intact.
        image:drawPixel(x,y,pc.rgba(math.floor(pc.rgbaR(pixel)*.48),math.floor(pc.rgbaG(pixel)*.48),math.floor(pc.rgbaB(pixel)*.48),pc.rgbaA(pixel)))
      end
    end end
  end
  if options.mark then
    local c = color(options.mark)
    local mark = pc.rgba(c[1],c[2],c[3],255)
    -- Stage and isotope markings are integral parts of the container, not text.
    for _,point in ipairs(options.points or {{7,8},{8,8}}) do image:drawPixel(point[1],point[2],mark) end
  end
  spr:saveCopyAs(target .. name .. '.png')
  spr:close()
end

local function green(r,g,b) return g > r*1.12 and g > b*1.08 end
local function warm(r,g,b) return r > g*1.05 and r > b*1.12 and r > 60 end
local function inner(r,g,b,x,y) return x>=2 and x<=5 and y>=3 and y<=10 and math.max(r,g,b)>55 end
local function lightMetal(r,g,b) return math.max(r,g,b)>100 and math.max(r,g,b)-math.min(r,g,b)<45 end

-- The four components retain the four selected reference silhouettes.
sprite('control_component',2,4)
sprite('superconducting_control',2,5,{filter=green,tint='81b7ff'})
sprite('mana_component',3,4)
sprite('mithril_mana_component',3,5,{filter=lightMetal,tint='9fe6f1'})

-- Glass vial family: same vessel and distinct isotope colors.
sprite('pu238',6,3,{filter=green,tint='91f075'})
sprite('cm244',6,3,{filter=green,tint='a9a3ff'})
sprite('th228',6,3,{filter=green,tint='f5d75b'})
sprite('lithium_salt',6,3,{filter=green,tint='f0e7cd',mark='b8a88a',points={{7,9},{9,10}}})

-- Slanted rod family: fresh, spent, then cooled.
sprite('uranium_fuel_rod',1,4,{outline=true})
sprite('spent_fuel_rod',1,4,{filter=green,tint='a66553',outline=true})
sprite('cooled_spent_fuel',1,4,{filter=green,tint='8cb3c5',outline=true})
sprite('nuclear_pellet',4,2)
sprite('zirconium_casing',1,2)

-- Fuel capsules use the same upright housing; fusion has both colors.
sprite('empty_fuel_capsule',2,3,{filter=green,tint='8796a2'})
sprite('deuterium_capsule',2,3,{filter=green,tint='64b7ff'})
sprite('tritium_capsule',2,3,{filter=green,tint='69e3ac'})
sprite('fusion_fuel_capsule',2,3,{filter=green,tint='64b7ff',tint2='69e3ac',split=7})

-- Processed materials: a related canister with distinct fills and markings.
sprite('uranium_concentrate',4,3,{filter=warm,tint='8ca76b',cleanInterior='8ca76b',mark='d3cb7c',points={{7,9}}})
sprite('converted_uranium',4,4,{filter=warm,tint='75b976',cleanInterior='75b976',mark='d3cb7c',points={{7,9},{8,9}}})
sprite('enriched_uranium',4,5,{filter=warm,tint='a7f463',cleanInterior='a7f463',mark='d3cb7c',points={{7,9},{8,9},{9,9}}})
sprite('depleted_uranium',4,6,{filter=warm,tint='718676',cleanInterior='718676',mark='a9ba9a',points={{7,9}}})
sprite('curium_feed',4,3,{filter=warm,tint='b69bd7',cleanInterior='b69bd7',mark='e1c5f3',points={{7,9},{8,9}}})

-- Heat-source intermediates stay together as sealed processing components.
local intermediates = {
  {'pu238_precursor','8da682','b5e99a',{{7,7}}},
  {'irradiated_pu238','afd36f','d3ff8b',{{7,7},{8,7}}},
  {'cooled_pu238','829fa2','b1e9d4',{{7,7},{8,7},{9,7}}},
  {'th228_precursor','bda372','ead39a',{{7,7}}},
  {'irradiated_th228','e5ba68','ffe294',{{7,7},{8,7}}},
  {'cooled_th228','9baab5','c5e3fa',{{7,7},{8,7},{9,7}}},
}
for _,v in ipairs(intermediates) do
  sprite(v[1],1,5,{filter=inner,tint=v[2],mark=v[3],points=v[4]})
end
source:close()

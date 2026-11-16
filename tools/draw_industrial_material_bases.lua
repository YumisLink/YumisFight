-- Native 16px edits of Minecraft and Mekanism material bases.
-- No shape drawing, resizing, resampling or changes to transparent pixels.
-- Run with Aseprite --batch --script-param root=<checkout> --script this-file.
-- Add --script-param preview=true to produce only five temporary comparisons.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local vanilla=(app.params.vanilla or root..'/temp/industrial-vanilla'):gsub('\\','/'):gsub('/+$','')
local mek=(app.params.mek or root..'/temp/nuclear-magic-material-references-20261114/Mekanism/mekanism/textures/item'):gsub('\\','/'):gsub('/+$','')
local preview=app.params.preview=='true'
local output=root..'/temp/material-bases-20261115/samples'
app.fs.makeAllDirectories(output)
local pc=app.pixelColor
local function palette(text)
  local p={}
  for h in text:gmatch('%x%x%x%x%x%x') do p[#p+1]={tonumber(h:sub(1,2),16),tonumber(h:sub(3,4),16),tonumber(h:sub(5,6),16)} end
  return p
end
local P={
 lead=palette('292536 433e56 625c76 85809b aca6be d0cbdd eeeaf5'),
 copper=palette('46292a 753b30 a2573e c17b54 dfa573 f4cea0 fff0d4'),
 orichalcum=palette('492831 793a36 ab5847 d87b5e edaa80 f9d3aa fff0d9'),
 vanadium=palette('232d3c 3b4b60 5b7088 7d95ab a7bdcd d0dfe8 f0f5f7'),
 titanium=palette('28374a 415976 6483a1 8baac4 b9d3e4 deeef5 f9fcfa'),
 magnesium=palette('1d2027 333943 46505c 66727f 8996a1 c5d0d7 f5f9fa'),
 molybdenum=palette('2c2939 484458 69657f 928ba5 bcb6cb ded9e7 f6f2fa'),
 zirconium=palette('3c302d 5a4b42 817062 aa9580 cfbea3 e8dcc5 fff3dc'),
 mithril=palette('183b46 2a6070 4198aa 74c8d3 a8e8e9 d6faf6 f7fffc'),
 niobium=palette('30253f 4d3e60 77628d a18ab7 c3add6 e4d3f0 faf0ff'),
 tantalum=palette('362b30 594950 817075 ac989a d1bebb edded5 fff4e9'),
 adamantite=palette('4b2d23 76482b a46e3b cf984f edc273 ffdfa1 fff3d2'),
 uranium=palette('2c3622 485b2c 718a40 9db957 c5d87f e3edb1 f8f9d9'),
 iridium=palette('302b40 51485f 7d718e aa9dc2 d3c8e7 eee6f9 fdf7ff'),
 terra=palette('182533 2b4152 486479 69899e 96b0c1 c4d7df eaf3f3'),
 spacetime=palette('291d40 493063 71509b 9d79c7 c7a5e6 e6cff6 f9edff'),
 iron=palette('282c35 444b58 657180 8b9ba9 b5c2cd dbe3e9 f9fbfa'),
 gold=palette('594023 87632b b18c3a d5b252 f0d476 fceaa9 fff7d6'),
 vanadium_steel=palette('28303b 404f61 61768a 889fb1 b2c6d3 d6e4eb f5faf9'),
 molybdenum_vanadium_steel=palette('282c38 41495f 62708b 8997ae b3c0d2 d9e1ef f5f8ff'),
 tzm_alloy=palette('31363c 505b65 758895 a0b4bf c6d6db e8eff0 fffff7'),
 orichalcum_alloy=palette('513525 805936 b18751 d8b075 f0d49b fce9be fff7df'),
 niobium_titanium_alloy=palette('2b2d44 484e6d 6f7c9e 9baac5 c6d0e4 e7ebf4 f9faff'),
 mithril_niobium_alloy=palette('233e4a 3a6677 5d9ca9 8ad0d6 bbf0ed e2fffa fafffc'),
 adamantite_titanium_alloy=palette('4b3429 79553b a98052 d1ab76 edcfa1 fbe8c7 fff8e6'),
 henghui_alloy=palette('382b49 5b4577 8b68ac b996d6 dcc2ef f2e3fc fff7ff'),
 stellar_alloy=palette('26374d 3e5c80 608bb8 8db6db bfdef3 e5f5ff f9ffff'),
 niobium_titanium_magnesium_alloy=palette('30253f 4d3e60 77628d a18ab7 c3add6 e4d3f0 faf0ff'),
 coolant=palette('173d4b 286275 3f91a5 68bac8 9cdee0 d0f5ed f3fffb'),
 alkahest=palette('452236 6b304d 963d63 ba6281 d78e9f efbfca ffe9eb'),
 blazing_fluid=palette('582322 953a22 cc5725 f37e39 ffa85b ffd89a fff0cf'),
 ceruleum=palette('153d37 1f6555 289a80 38d8bb 7cebd5 b9f8e9 ecfff6'),
 shimmer=palette('35244d 554075 7d65a7 ac93ef cab8f8 e6dbff faf1ff'),
 azoth=palette('1d2c55 2e467f 4066b3 5a89e8 92b9f7 cadfff eff6ff'),
 penduliquid=palette('304850 4c6e78 759da8 a3d0d9 c8eef0 e7fcf9 f7fffd'),
 spent_solvent=palette('292523 423b31 62584b 8b7c66 b0a18a d1c6ae efe7d4'),
 mana_fluid=palette('18375c 235c8f 3289c2 58baff 9ddcff d0f1ff f1fcff'),
 concentrated_mana_fluid=palette('191d50 252c83 3659b9 547ce7 90b3fa c8dbff eff4ff'),
 heavy_water=palette('283c55 435e7c 6889aa 92b4d0 bed8e9 deeff5 f6fcff'),
 brine=palette('37413d 58675d 84988a b3c8b6 d7e5d4 f0f6e7 fffff3'),
}
local specs={}
local function add(id,kind,material,bright)
  specs[#specs+1]={id=id,kind=kind,material=material,bright=bright}
end
for _,m in ipairs({'lead','orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium_tantalum','adamantite','uranium','iridium'}) do add('raw_'..m,'raw',m=='niobium_tantalum' and 'niobium' or m) end
for _,m in ipairs({'lead','copper','orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium_tantalum','gold','adamantite','uranium','iron','iridium'}) do add('crushed_'..m,'crushed',m=='niobium_tantalum' and 'niobium' or m) end
for _,m in ipairs({'orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium','tantalum','adamantite','iridium','uranium'}) do add(m..'_concentrate','dust',m) end
for _,m in ipairs({'lead','orichalcum','vanadium','titanium','molybdenum','zirconium','mithril','niobium','tantalum','terra','spacetime','adamantite','uranium','iridium'}) do add(m..'_ingot','ingot',m) end
for _,m in ipairs({'mithril','niobium','tantalum','iridium'}) do add('high_purity_'..m..'_ingot','ingot',m,true) end
for _,m in ipairs({'vanadium_steel','molybdenum_vanadium_steel','tzm_alloy','orichalcum_alloy','niobium_titanium_alloy','mithril_niobium_alloy','adamantite_titanium_alloy','henghui_alloy','stellar_alloy'}) do add(m,'ingot',m) end
add('magnesium_dust','dust','magnesium')
add('magnesium_ingot','ingot','magnesium')
add('niobium_titanium_magnesium_alloy','ingot','niobium_titanium_magnesium_alloy')
for _,m in ipairs({'coolant','alkahest','blazing_fluid','ceruleum','shimmer','azoth','penduliquid','spent_solvent','mana_fluid','concentrated_mana_fluid','heavy_water','brine'}) do add(m..'_bucket','bucket',m) end
assert(#specs==78,'Expected 78 material variants')
local samples={raw_lead=true,titanium_ingot=true,crushed_iron=true,vanadium_concentrate=true,coolant_bucket=true}
local templates={raw=vanilla..'/raw_iron.png',ingot=vanilla..'/iron_ingot.png',bucket=vanilla..'/water_bucket.png',crushed=mek..'/clump_iron.png',dust=mek..'/dust_iron.png'}
local function shade(p,t,a)
  t=math.max(0,math.min(1,t));local index=math.min(6,math.floor(t*6)+1)
  local f=t*6-(index-1);local A,B=p[index],p[index+1]
  return pc.rgba(math.floor(A[1]+(B[1]-A[1])*f+.5),math.floor(A[2]+(B[2]-A[2])*f+.5),math.floor(A[3]+(B[3]-A[3])*f+.5),a)
end
local sprite=Sprite(16,16,ColorMode.RGB)
sprite.colorSpace=ColorSpace{sRGB=true}
local original=sprite.layers[1];original.name='01 / original Minecraft or Mekanism base';original.isEditable=false
local changes=sprite:newLayer();changes.name='02 / material colour only'
local authored,ordered={},{};local count=0
for _,s in ipairs(specs) do
  if not preview or samples[s.id] then
    -- These PNGs can be indexed. Resolve their embedded palettes in a sprite
    -- before reading pixels as RGBA or copying them into the RGB source.
    local template=Sprite{fromFile=assert(templates[s.kind])}
    app.activeSprite=template
    app.command.ChangePixelFormat{format='rgb'}
    local base=Image(template.spec);base:drawSprite(template,1)
    template:close();app.activeSprite=sprite
    assert(base.width==16 and base.height==16,'The source base must be native 16px')
    local overlay=Image(16,16,ColorMode.RGB)
    for y=0,15 do for x=0,15 do
      local pixel=base:getPixel(x,y);local r,g,b,a=pc.rgbaR(pixel),pc.rgbaG(pixel),pc.rgbaB(pixel),pc.rgbaA(pixel)
      local selected=a>0
      if s.kind=='bucket' then selected=selected and b>r*1.35 and b>g*1.2
      elseif s.kind=='raw' then selected=selected and r>160
      elseif s.kind=='crushed' then selected=selected and math.max(r,g,b)-math.min(r,g,b)>14 end
      if selected then
        local l=.2126*r+.7152*g+.0722*b
        local t=s.kind=='raw' and .22+.78*(l-144)/100 or s.kind=='bucket' and .27+.65*(l-76)/75 or l/255
        if s.bright then t=t^.82 end
        local p=P[s.material]
        -- The mixed ore keeps both metals inside the original mineral pixels.
        if s.id:find('niobium_tantalum',1,true) and x>=8 then p=P.tantalum end
        overlay:drawPixel(x,y,shade(assert(p,s.material),t,a))
      end
    end end
    count=count+1;authored[s.id]=true
    local frame=count==1 and sprite.frames[1] or sprite:newEmptyFrame(count)
    if count==1 then original:cel(1).image=base else sprite:newCel(original,frame,base,Point(0,0)) end
    sprite:newCel(changes,frame,overlay,Point(0,0))
    ordered[#ordered+1]=s.id
    if preview then
      base:saveAs(output..'/'..s.id..'.base.png')
      local edited=Image(sprite.spec);edited:drawSprite(sprite,count);edited:saveAs(output..'/'..s.id..'.png')
    end
  end
end
-- Add tags after frame insertion so Aseprite cannot extend earlier tag ranges.
for i,id in ipairs(ordered) do local tag=sprite:newTag(i,i);tag.name=id end
sprite:saveAs(root..(preview and '/temp/material-bases-20261115/sample.aseprite' or '/tools/industrial_material_bases.aseprite'))
sprite:close()
if not preview then
  -- Move these existing named frames out of the old 32px document. Every other
  -- frame, cel, layer, tag and prospector stays in its current editable form.
  local old=Sprite{fromFile=root..'/tools/industrial_materials.aseprite'}
  local tags,frames={},{}
  for _,tag in ipairs(old.tags) do
    if authored[tag.name] then
      assert(tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Material tags must select one frame')
      tags[#tags+1]=tag;frames[#frames+1]=tag.fromFrame.frameNumber
    end
  end
  if #tags>0 then
    for _,tag in ipairs(tags) do old:deleteTag(tag) end
    table.sort(frames,function(a,b)return a>b end)
    for _,i in ipairs(frames) do old:deleteFrame(old.frames[i]) end
    old:saveAs(root..'/tools/industrial_materials.aseprite')
  end
  old:close()
end
print((preview and 'Compared ' or 'Authored ')..count..' native 16px material variants without changing source shapes.')
-- Material details stay on their own editable layers and reuse these bases.
if not preview then dofile(root..'/tools/draw_industrial_ingot_details.lua') end

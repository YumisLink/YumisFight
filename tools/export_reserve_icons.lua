-- Export the unused 16x16 sprite library from its editable Aseprite sources.
-- The r1c1...r6c6 tags retain each tile's row and column in the supplied sheet.
-- Sources supplied by the user on 2026-11-15 (original authorship unspecified):
-- materials: ]0~0U@D$[`_1UT%{~C@SGDE.png
--   SHA-256 c45a26f744faa5adc0993c46598faba9b2c9dd739818a6ad049e581252068fe3
-- armor: F141TTB@8X]UA@OC0V@VIGH.png
--   SHA-256 17f7cbe412be94250465bf9c4933820b4f1655fa2a3927ded860250e103d3c11
-- Run with Aseprite --batch --script-param root=<checkout> --script this-file.
-- Optional: group=materials|armor and tile=r1c1 select a group or a single tile.
-- Armor groups follow the original sheet's reading order, four pieces per set.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local groups={materials='reserve_materials',armor='reserve_armor'}
local armorSets={'heavy','unassigned/set_02','unassigned/set_03','plate','light',
  'leather','unassigned/set_07','cloth','unassigned/set_09'}
local armorPieces={'helmet','chestplate','leggings','boots'}
local group=app.params.group
local tile=app.params.tile
assert(not group or groups[group], 'Unknown group: '..tostring(group))
assert(not tile or (group and tile:match('^r[1-6]c[1-6]$')), 'Select a group and valid tile')
local count=0
for _,name in ipairs({'materials','armor'}) do
  if not group or name==group then
    local source=Sprite{fromFile=root..'/tools/'..groups[name]..'.aseprite'}
    assert(source.width==16 and source.height==16 and #source.frames==36,'Expected 36 16x16 frames in '..name)
    assert(source.layers[1].name=='01 / supplied reference' and not source.layers[1].isVisible,
      'Keep the supplied reference layer hidden during export')
    local frames={}
    for _,tag in ipairs(source.tags) do
      local id=tag.name
      assert(id:match('^r[1-6]c[1-6]$') and tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Invalid tile tag: '..id)
      assert(not frames[id],'Duplicate tile tag: '..id)
      frames[id]=tag.fromFrame.frameNumber
    end
    local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/library/'..name..'/'
    app.fs.makeAllDirectories(output)
    for row=1,6 do for col=1,6 do
      local id=string.format('r%dc%d',row,col)
      local frame=assert(frames[id],'Missing tile tag: '..id)
      if not tile or tile==id then
        local destination=output..id..'.png'
        if name=='armor' then
          local index=(row-1)*6+(col-1)
          local folder=armorSets[math.floor(index/4)+1]
          app.fs.makeAllDirectories(output..folder)
          destination=output..folder..'/'..armorPieces[index%4+1]..'.png'
        end
        local image=Image(source.spec)
        image:drawSprite(source,frame)
        image:saveAs(destination)
        count=count+1
      end
    end end
    source:close()
  end
end
print('Exported '..count..' reserved 16x16 textures.')

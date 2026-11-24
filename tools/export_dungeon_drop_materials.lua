-- Export 32x32 dungeon monster drop material textures from the editable Aseprite source.
-- Source: tools/dungeon_drop_materials.aseprite (18 frames, one tagged frame per material ID).
-- Run with:
--   aseprite.exe --batch --script-param root=<checkout> --script tools/export_dungeon_drop_materials.lua
-- Optional:
--   --script-param item=<id>   Export a single material ID.

local root = assert(app.params.root, 'Pass --script-param root=<checkout>'):gsub('\\', '/'):gsub('/+$', '')
local sourcePath = root .. '/tools/dungeon_drop_materials.aseprite'
local outDir = root .. '/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/materials/'

local sprite = Sprite{ fromFile = sourcePath }
assert(sprite.width == 32 and sprite.height == 32, 'Expected 32x32 sprite')

local targetItem = app.params.item
local count = 0
local seen = {}

for _, tag in ipairs(sprite.tags) do
  assert(tag.fromFrame.frameNumber == tag.toFrame.frameNumber, 'Material tag must span 1 frame: ' .. tag.name)
  assert(not seen[tag.name], 'Duplicate material tag: ' .. tag.name)
  seen[tag.name] = true
  if not targetItem or targetItem == tag.name then
    local image = Image(sprite.spec)
    image:drawSprite(sprite, tag.fromFrame.frameNumber)
    image:saveAs(outDir .. tag.name .. '.png')
    count = count + 1
  end
end

sprite:close()
assert(count > 0, 'No matching material tag exported' .. (targetItem and (': ' .. targetItem) or ''))
print('Exported ' .. count .. ' 32x32 dungeon drop material texture(s).')

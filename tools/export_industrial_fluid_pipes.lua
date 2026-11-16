-- Export saved, hand-edited pipe textures without redrawing or modifying the Aseprite source.
-- Aseprite --batch --script-param root=<checkout> --script tools/export_industrial_fluid_pipes.lua
-- Optional: --script-param item=fluid_pipe_rim_t1 exports only that named tag.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local output=root..'/src/main/resources/assets/yumis_dungeon_and_fight/textures/block/'
local sprite=Sprite{fromFile=root..'/tools/industrial_fluid_pipes.aseprite'}
assert(sprite.width==16 and sprite.height==16,'Pipe textures must stay 16x16')
local exported=0
for _,tag in ipairs(sprite.tags) do
  local name=tag.name
  assert(name:match('^fluid_pipe_[a-z0-9_]+$'),'Invalid pipe texture tag: '..name)
  if not app.params.item or app.params.item==name then
    assert(tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Pipe texture tags must be static')
    local flat=Image(sprite.spec)
    flat:drawSprite(sprite,tag.fromFrame.frameNumber)
    flat:saveAs(output..name..'.png')
    exported=exported+1
  end
end
sprite:close()
assert(exported>0,'No matching pipe texture tag')
print('Exported '..exported..' pipe textures; source artwork was preserved.')

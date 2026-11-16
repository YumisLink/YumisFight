-- Export 16x16 fish textures from the editable Aseprite source.
-- Source: the user-supplied QQ图片20261115165011.png, 2026-11-15.
-- The source contains all 36 tiles, tagged by row and column, with the
-- unmodified tile hidden on a locked layer and the cutout on a visible layer.
-- Run with Aseprite --batch --script-param root=<checkout> --script this-file.
-- Optional: item=<id> exports one fish; allTiles=true exports every cutout
-- into temp/fish-art/tiles without changing the game textures.
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local source=Sprite{fromFile=root..'/tools/fishing_catches.aseprite'}
assert(source.width==16 and source.height==16 and #source.frames==36,'Expected 36 native 16x16 tiles')
assert(source.layers[1].name=='01 / original supplied tile' and not source.layers[1].isVisible,
  'Keep the original white-backed layer hidden during export')
local frames={}
for _,tag in ipairs(source.tags) do
  assert(tag.name:match('^r[1-6]c[1-6]$') and tag.fromFrame.frameNumber==tag.toFrame.frameNumber,
    'Invalid tile tag: '..tag.name)
  assert(not frames[tag.name],'Duplicate tile tag: '..tag.name)
  frames[tag.name]=tag.fromFrame.frameNumber
end
local assigned={
  {'fish_common','r2c6'},
  {'fish_puffer_polluted','r2c3'},
  {'fish_salmon','r4c3'},
  {'fish_eel','r3c2'},
  {'fish_cod','r5c1'},
  {'fish_rare','r3c5'},
  {'fish_angelfish_spore','r2c5'},
  {'fish_goldscale','r1c2'},
  {'fish_glacier_salmon','r3c1'},
  {'fish_artifact','r5c2'},
  {'fish_phoenix_tail','r1c4'},
  {'fish_koi','r5c5'},
  {'fish_king_salmon','r3c6'},
  {'fish_prism','r5c4'},
}
local all=app.params.allTiles=='true'
assert(not (all and app.params.item),'Choose allTiles or a single item')
local output=root..(all and '/temp/fish-art/tiles/' or '/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/')
if all then app.fs.makeAllDirectories(output) end
local count=0
local function save(name,tag)
  local frame=assert(frames[tag],'Missing tile tag: '..tag)
  local image=Image(source.spec);image:drawSprite(source,frame)
  image:saveAs(output..name..'.png');count=count+1
end
if all then
  for row=1,6 do for col=1,6 do
    local tag=string.format('r%dc%d',row,col);save(tag,tag)
  end end
else
  for _,entry in ipairs(assigned) do
    if not app.params.item or app.params.item==entry[1] then save(entry[1],entry[2]) end
  end
  assert(count>0,'Unknown fishing item selection')
end
source:close()
print('Exported '..count..' 16x16 fishing '..(all and 'tiles.' or 'textures.'))

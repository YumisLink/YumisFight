-- Export the 32x32 mask and bust icons from the editable Aseprite source.
-- sheet_01, sheet_02 and sheet_03 follow the three user-supplied images;
-- r and c identify the original row and column. Original authorship unspecified.
-- sheet_01: XYK{RXNKUL@9ZIJ@)82Y__8.png
-- SHA-256 dd22d853c2a2877f49f54e2141b85ced248c1b4ebab96ba6fda50c7bb4da6e28
-- sheet_02: @8XZO}Q6ZO_{%X`YHT]WI@L.png
-- SHA-256 2f178b8654f74514b8c7b7c421714be78f72f66333a4512ec9f1cb8bf75ce33b
-- sheet_03: L$~X8A(OD{%I7F94I2`NP_D.png
-- SHA-256 a1490991279531f0b9273b1a7bc72a03141a2786cc6088e430fdac4ac64a03ce
-- Run: aseprite --batch --script-param root=<checkout> --script tools/export_reserve_masks.lua
local root=assert(app.params.root,'Pass --script-param root=<checkout>'):gsub('\\','/'):gsub('/+$','')
local source=Sprite{fromFile=root..'/tools/reserve_masks.aseprite'}
assert(source.width==32 and source.height==32 and #source.frames==45,'Expected 45 32x32 frames')
assert(source.layers[1].name=='01 / supplied reference' and not source.layers[1].isVisible,'Reference layer must remain hidden')
local frames={}
for _,tag in ipairs(source.tags) do
 local id=tag.name
 assert(id:match('^sheet_0[1-3]_r[1-4]c[1-4]$') and tag.fromFrame.frameNumber==tag.toFrame.frameNumber,'Invalid tag: '..id)
 assert(not frames[id],'Duplicate tag: '..id)
 frames[id]=tag.fromFrame.frameNumber
end
local count=0
for group=1,3 do
 local folder=string.format('%s/src/main/resources/assets/yumis_dungeon_and_fight/textures/item/library/masks/sheet_%02d/',root,group)
 app.fs.makeAllDirectories(folder)
 for row=1,4 do for col=1,4 do
  if row~=4 or col~=4 then
   local id=string.format('sheet_%02d_r%dc%d',group,row,col)
   local frame=assert(frames[id],'Missing tag: '..id)
   local img=Image(source.spec); img:drawSprite(source,frame)
   img:saveAs(folder..string.format('r%dc%d.png',row,col))
   count=count+1
  end
 end end
end
source:close()
print('Exported '..count..' reserved mask textures.')

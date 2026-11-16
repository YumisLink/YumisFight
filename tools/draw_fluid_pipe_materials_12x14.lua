-- Editable 12-wide pipe / 14-wide rim material draft, drawn natively in Aseprite.
-- Initial creation only. After hand edits use export_fluid_pipe_materials_12x14.lua.
-- Aseprite --batch --script-param root=<checkout> --script this-file
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local sourcePath=root..'/tools/fluid_pipe_materials_12x14.aseprite'
assert(not app.fs.isFile(sourcePath) or app.params.redraw=='true',
       'Source already exists; preserve hand edits and use the export-only entry')
local reference=root..'/temp/fluid-pipes/create_original_reference/'
local parts={
  {id='straight',name='B2 straight 12x16 - rotate 90',x=1,y=1,w=12,h=16,ports=5},
  {id='elbow',name='B3 elbow 12x12 - rotate 0 90 180 270',x=15,y=1,w=12,h=12,ports=3},
  {id='single',name='B1 single 12x12 - rotate 0 90 180 270',x=29,y=1,w=12,h=12,ports=1},
  {id='tee',name='B4 tee 12x12 - rotate 0 90 180 270',x=43,y=1,w=12,h=12,ports=11},
  {id='cross',name='B5 cross 12x12',x=1,y=19,w=12,h=12,ports=15},
  {id='casing',name='A3 casing 14x14 - solid material',x=15,y=19,w=14,h=14},
  {id='rim',name='A4 rim 14x14 - transparent 10x10 center',x=31,y=19,w=14,h=14},
  {id='glass_reserved',name='C own glass 12x12 - reserved transparent',x=47,y=19,w=12,h=12},
  {id='connector',name='A2 connector 12x2',x=1,y=35,w=12,h=2},
  {id='rim_side',name='A5 rim side 2x14 - rotate 90',x=15,y=35,w=2,h=14},
}
local palettes={
  {id='t1',name='copper T1',base={185,121,78},groove={146,82,35}},
  {id='t2',name='vanadium steel T2',base={126,151,159},groove={87,112,116}},
  {id='t3',name='niobium titanium T4',base={157,149,193},groove={118,110,150}},
}
local sprite=Sprite(64,64,ColorMode.RGB)
local refs=sprite.layers[1];refs.name='00 Create references 32px - hidden locked';refs.isVisible=false;refs.isEditable=false
local metal=sprite:newLayer();metal.name='01 material color - editable'
local relief=sprite:newLayer();relief.name='02 highlights and raised lips - editable'
local groove=sprite:newLayer();groove.name='03 symmetric material-colored grooves - editable'
local clear=sprite:newLayer();clear.name='04 own glass and transparency - draw here'
local pc=app.pixelColor
local function color(rgb,delta)
  delta=delta or 0
  return pc.rgba(math.max(0,math.min(255,rgb[1]+delta)),math.max(0,math.min(255,rgb[2]+delta)),math.max(0,math.min(255,rgb[3]+delta)),255)
end
local referenceImage=Image(64,64,ColorMode.RGB)
for i,name in ipairs({'pipes','pipes_connected'}) do
  local path=reference..name..'.png'
  if app.fs.isFile(path) then
    local doc=app.open(path);local image=Image(doc.spec);image:drawSprite(doc,1)
    referenceImage:drawImage(image,Point((i-1)*32,0));doc:close()
  end
end
local function has(mask,bit) return math.floor(mask/bit)%2==1 end
local function window(part,x,y)
  if part.id=='glass_reserved' then return true end
  if part.id=='casing' or part.id=='rim_side' then return false end
  if part.id=='connector' then return x>=2 and x<10 end
  if part.id=='rim' then return x>=2 and x<12 and y>=2 and y<12 end
  -- Canonical face: N=1 E=2 S=4 W=8. Other orientations reuse these pixels by rotation.
  local middle=x>=2 and x<10 and y>=2 and y<part.h-2
  return middle or has(part.ports,1) and x>=2 and x<10 and y<2
    or has(part.ports,2) and y>=2 and y<part.h-2 and x>=10
    or has(part.ports,4) and x>=2 and x<10 and y>=part.h-2
    or has(part.ports,8) and y>=2 and y<part.h-2 and x<2
end
local function solid(part,x,y)
  return x>=0 and y>=0 and x<part.w and y<part.h and not window(part,x,y)
end
for frame,palette in ipairs(palettes) do
  if frame>1 then sprite:newEmptyFrame() end
  local m=Image(sprite.spec);local r=Image(sprite.spec);local g=Image(sprite.spec);local c=Image(sprite.spec)
  for _,part in ipairs(parts) do
    for y=0,part.h-1 do for x=0,part.w-1 do
      if solid(part,x,y) then
        local X,Y=part.x+x,part.y+y
        -- Individual pixel variation and broad asymmetric reflection patches, not resized artwork.
        local grain=((x*7+y*11+math.floor(x/3)*3)%7)-3
        local patch=(x>=math.floor(part.w*.55) and y>=3 and y<=math.floor(part.h*.65)) and 5 or 0
        m:drawPixel(X,Y,color(palette.base,grain+patch))
        local edge=math.min(x,y,part.w-1-x,part.h-1-y)
        if part.id=='casing' then
          if edge==0 then r:drawPixel(X,Y,color(palette.base,(y==0 or x==0) and 28 or -18))
          elseif edge==1 then g:drawPixel(X,Y,color(palette.groove,5))
          elseif edge==2 then r:drawPixel(X,Y,color(palette.base,(y==2 or x==2) and 13 or -9)) end
          if x>=4 and x<=6 and y>=4 and y<=6 then r:drawPixel(X,Y,color(palette.base,16)) end
          if x>=8 and x<=10 and y>=8 and y<=10 then r:drawPixel(X,Y,color(palette.base,-10)) end
        elseif part.id=='rim_side' then
          r:drawPixel(X,Y,color(palette.base,x==0 and 24+grain or -15+grain))
          if y==0 or y==part.h-1 then g:drawPixel(X,Y,color(palette.groove,8)) end
        else
          if not solid(part,x,y-1) then r:drawPixel(X,Y,color(palette.base,25))
          elseif not solid(part,x-1,y) then r:drawPixel(X,Y,color(palette.base,14))
          elseif not solid(part,x,y+1) then r:drawPixel(X,Y,color(palette.base,-17)) end
          local inner=false
          for dy=-1,1 do for dx=-1,1 do
            local a,b=x+dx,y+dy
            if a>=0 and b>=0 and a<part.w and b<part.h and window(part,a,b) then inner=true end
          end end
          if inner then g:drawPixel(X,Y,color(palette.groove)) end
        end
      end
    end end
  end
  sprite:newCel(refs,frame,Image(referenceImage),Point(0,0))
  sprite:newCel(metal,frame,m,Point(0,0));sprite:newCel(relief,frame,r,Point(0,0))
  sprite:newCel(groove,frame,g,Point(0,0));sprite:newCel(clear,frame,c,Point(0,0))
  local tag=sprite:newTag(frame,frame);tag.name=palette.id
end
for _,tag in ipairs(sprite.tags) do tag.toFrame=tag.fromFrame end
for _,part in ipairs(parts) do
  local slice=sprite:newSlice(Rectangle(part.x,part.y,part.w,part.h))
  slice.name=part.id;slice.data=part.name
end
sprite:saveAs(sourcePath);sprite:close()
print('Created 64x64 editable atlases: 3 material frames, 10 native-size parts, no scaling.')

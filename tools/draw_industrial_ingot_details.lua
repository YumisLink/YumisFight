-- Original-shaped 16px ingots: clean facets, diffuse glow, whole-body colour.
-- Aseprite --batch --script-param root=<checkout> --script this-file
-- preview=true writes the current material-style samples into temp only.
local root=assert(app.params.root):gsub('\\','/'):gsub('/+$','')
local preview=app.params.preview=='true'
local output=app.params.output or root..'/temp/spacetime-coating-glint-20261115'
app.fs.makeAllDirectories(output..'/samples')
local pc=app.pixelColor
local BODY='03 / clean metal colour'
local DETAILS='04 / authored material details'
local DYNAMIC='05 / diffuse fluorescence'
local SPECULAR='06 / coating specular'
-- Remove the prior authored effects when rebuilding; keep the original and
-- material layers, including any independently authored non-effect layers.
local owned={[BODY]=true,[DETAILS]=true,[DYNAMIC]=true,[SPECULAR]=true,
 ['04 / diffuse fluorescence']=true,
 ['03 / animated metal colour']=true,['04 / distinct material inlays']=true,
 ['05 / fluorescence and energy light']=true,
 ['03 / subtle ingot material details']=true,['04 / local ingot light animation']=true}
local styles={
 titanium_ingot={tier=3,accent='58baff'},
 molybdenum_ingot={tier=3,accent='a968e3'},
 zirconium_ingot={tier=3,accent='e9ba64'},
 mithril_ingot={tier=3,accent='29ecff'},
 high_purity_mithril_ingot={tier=3,accent='38f5fa'},
 molybdenum_vanadium_steel={tier=3,accent='8287da'},
 tzm_alloy={tier=3,accent='d1a84e'},
 niobium_ingot={tier=4,animation='breathe',glow='6bafff'},
 tantalum_ingot={tier=4,animation='breathe',glow='ff729e'},
 high_purity_niobium_ingot={tier=4,animation='breathe',glow='82edff'},
 high_purity_tantalum_ingot={tier=4,animation='breathe',glow='ffc1b2'},
 terra_ingot={tier=4,animation='breathe',surface='striped'},
 adamantite_ingot={tier=4,animation='breathe',surface='adamantite'},
 uranium_ingot={tier=4,animation='breathe',glow='ceff27'},
 niobium_titanium_alloy={tier=4,animation='breathe',surface='crystal'},
 mithril_niobium_alloy={tier=4,animation='breathe',glow='ff65eb'},
 adamantite_titanium_alloy={tier=4,surface='laminated'},
 spacetime_ingot={tier=5,animation='colour',surface='flowing_film'},
 iridium_ingot={tier=5,animation='colour',
  body={{150,107,216},{77,178,231},{196,132,225},{150,107,216}}},
 high_purity_iridium_ingot={tier=5,animation='colour',
  body={{163,131,231},{97,213,250},{221,156,237},{163,131,231}}},
 henghui_alloy={tier=5,animation='iridescent',
  body={{37,235,246},{235,52,217},{255,196,67},{37,235,246}}},
 stellar_alloy={tier=6,animation='cosmilite'},
 niobium_titanium_magnesium_alloy={tier=6,surface='triple_laminate'},
}
for _,style in pairs(styles) do
 if style.animation then style.frames=24;style.duration=.1 end
end
local representatives={terra_ingot=true,spacetime_ingot=true,niobium_titanium_alloy=true,
 adamantite_ingot=true,adamantite_titanium_alloy=true}
local function clamp(value) return math.max(0,math.min(1,value)) end
local function smooth(value) value=clamp(value);return value*value*(3-2*value) end
local function rgba(colour,alpha)
 return pc.rgba(math.floor(colour[1]+.5),math.floor(colour[2]+.5),
  math.floor(colour[3]+.5),math.floor(alpha+.5))
end
local function rgb(value)
 return {tonumber(value:sub(1,2),16),tonumber(value:sub(3,4),16),tonumber(value:sub(5,6),16)}
end
local function mix(A,B,t)
 t=clamp(t)
 return {A[1]+(B[1]-A[1])*t,A[2]+(B[2]-A[2])*t,A[3]+(B[3]-A[3])*t}
end
local function scale(A,t) return {A[1]*t,A[2]*t,A[3]*t} end
local function colourCycle(colours,phase)
 local position=phase*(#colours-1);local index=math.floor(position)+1
 return mix(colours[index],colours[index+1],position-math.floor(position))
end
local function metalShade(colour,brightness,flare)
 local dark=scale(colour,.13);local middle=scale(colour,.72)
 local light=mix(colour,{255,255,255},.60+.20*(flare or 0))
 if brightness<.5 then return mix(dark,middle,brightness*2) end
 return mix(middle,light,(brightness-.5)*2)
end
-- One symmetric envelope drives all stellar colours and light: ten rising
-- frames (1s), four peak frames (.4s), ten falling frames (1s). Sample each
-- frame at its midpoint to avoid counting a peak endpoint twice. The colour
-- field stays fixed so opposite phases are exact reverse images.
local function stellarPulse(frame)
 local sample=frame-.5
 if sample<10 then return smooth(sample/10) end
 if sample<14 then return 1 end
 return smooth((24-sample)/10)
end
local function stellarTint(x,pulse)
 local across=clamp((x-2)/11)
 return mix({89,55,145},mix({255,35,223},{20,173,255},across),pulse)
end
-- Principal colours sampled from the original Terraria ingot references:
-- https://terraria.arcenserv.info/w/images/Adamantite_Bar.png
-- https://terraria.arcenserv.info/w/images/Titanium_Bar.png
-- Intermediate reds retain the eight shade roles of our vanilla ingot.
local adamantite={
 [53]={55,10,38},[88]={67,17,54},[94]={100,8,43},[114]={128,11,28},
 [130]={173,15,13},[168]={198,36,43},[216]={221,61,74},[255]={255,209,202},
}
local titanium={
 [53]={7,26,43},[88]={9,31,50},[94]={29,61,72},[114]={70,77,75},
 [130]={91,90,119},[168]={144,173,174},[216]={190,187,220},[255]={212,255,255},
}
local niobiumLaminate={
 [53]={39,30,54},[88]={63,50,79},[94]={78,64,96},[114]={100,82,118},
 [130]={127,107,146},[168]={163,143,183},[216]={205,185,224},[255]={245,231,255},
}
local magnesiumLaminate={
 [53]={29,32,39},[88]={44,48,55},[94]={51,56,65},[114]={70,77,88},
 [130]={93,102,113},[168]={137,148,158},[216]={207,217,223},[255]={245,249,250},
}
local graphite={
 [53]={14,18,25},[88]={21,26,34},[94]={25,30,39},[114]={31,37,47},
 [130]={39,46,57},[168]={51,60,72},[216]={69,78,92},[255]={127,145,163},
}
local cyanBands={
 [53]={{5,42,52},{8,76,91}},[88]={{8,61,74},{10,109,127}},
 [94]={{9,72,86},{11,129,147}},[114]={{8,98,116},{8,159,185}},
 [130]={{9,124,148},{11,184,216}},[168]={{12,154,182},{30,207,235}},
 [216]={{27,192,220},{75,234,255}},[255]={{159,244,255},{215,255,255}},
}
local crystal={
 [53]={{24,20,51},{39,25,75}},[88]={{35,28,77},{54,34,110}},
 [94]={{41,31,88},{64,41,127}},[114]={{51,33,111},{87,47,165}},
 [130]={{67,44,141},{118,65,204}},[168]={{90,66,175},{158,112,241}},
 [216]={{33,111,223},{66,207,255}},[255]={{164,219,255},{226,252,255}},
}
-- Existing front bevel height at each x, including the visible left end.
-- Surface rings and lamination follow this geometry, never the canvas axes.
local frontEdge={[0]=6,6,7,8,9,9,8,8,8,7,7,7,6,6,6,6}
local function bandRole(x,y)
 if y<=frontEdge[x] then
  local coordinate=x-y
  if coordinate==-1 or coordinate==4 then return 'core' end
  if coordinate==0 or coordinate==3 then return 'edge' end
 else
  if x==7 or x==11 then return 'core' end
  if x==8 or x==10 then return 'edge' end
 end
end
-- Purple metal and the paler blue upper coating keep separate shade ramps.
-- The original ingot selects every face, shadow and bevel position.
local spacetimeMetal={
 [53]={22,17,38},[88]={35,27,55},[94]={43,33,66},[114]={56,43,80},
 [130]={75,55,106},[168]={105,76,143},[216]={144,103,190},[255]={225,212,246},
}
local spacetimeCoating={
 [53]={16,35,55},[88]={27,54,78},[94]={35,67,95},[114]={47,88,121},
 [130]={65,112,148},[168]={98,151,190},[216]={147,203,237},[255]={232,251,255},
}
-- Unfold the existing top, front and left end without moving any pixels.
-- Angular order on those connected faces gives a circulating surface flow.
-- Uniform ranks make the blue film cover half the visible surface throughout
-- the cycle, including when it crosses the top/side bevel.
local flowRanks
local function coatingCoordinates(original)
 if flowRanks then return flowRanks end
 local points={}
 for y=0,15 do for x=0,15 do
  if pc.rgbaA(original:getPixel(x,y))>0 then
   local u,v
   if y<=frontEdge[x] then
    u=(x-y+5)/13
    v=(3*(x-1)+10*(y-6))/39
   elseif x>=4 then
    u=(x-4)/10
    local edge=9-3*u
    v=1+math.max(0,y-edge)/4*.70
   else
    v=(x-1)/3
    local edge=6+3*v
    u=-math.max(0,y-edge)/4*.70
   end
   points[#points+1]={key=y*16+x,angle=math.atan(v-.5,(u-.5)*1.45)}
  end
 end end
 table.sort(points,function(A,B)
  if A.angle==B.angle then return A.key<B.key end
  return A.angle<B.angle
 end)
 flowRanks={}
 for i,point in ipairs(points) do flowRanks[point.key]=(i-.5)/#points end
 return flowRanks
end
local function coatingField(original,x,y,phase)
 local coordinate=assert(coatingCoordinates(original)[y*16+x])
 local angle=(coordinate-phase)*math.pi*2
 local wave=math.cos(angle)
 local coverage=smooth(.5+wave/.50)
 local leading=math.max(0,math.sin(angle))*math.exp(-(wave/.25)^2)
 local trailing=math.max(0,-math.sin(angle))*math.exp(-(wave/.25)^2)
 return coverage,leading,trailing
end
local function surfaceImage(style,original,frame)
 local image=Image(16,16,ColorMode.RGB)
 local phase=(frame-1)/(style.frames or 1)
 local pulse=(1-math.cos(phase*math.pi*2))*.5
 for y=0,15 do for x=0,15 do
  local source=original:getPixel(x,y);local alpha=pc.rgbaA(source)
  if alpha>0 then
   local shade=pc.rgbaR(source)
   local colour
   if style.surface=='laminated' then
    -- Full titanium top/cap, a broad red layer in the existing side thickness,
    -- and the original lower bevel. No gaps, separated plates or extra rims.
    local depth=y-frontEdge[x]
    local redLayer=depth>=2 and depth<=3 and shade<216
    colour=assert((redLayer and adamantite or titanium)[shade])
   elseif style.surface=='adamantite' then
    colour=scale(assert(adamantite[shade]),.88+.12*pulse)
   elseif style.surface=='striped' then
    colour=assert(graphite[shade])
   elseif style.surface=='triple_laminate' then
    local depth=y-frontEdge[x]
    if depth<=0 or shade>=216 then colour=assert(niobiumLaminate[shade])
    elseif depth<=1 then colour=assert(titanium[shade])
    else colour=assert(magnesiumLaminate[shade]) end
   elseif style.surface=='flowing_film' then
    -- Purple metal is constant underneath the independently moving blue film.
    colour=assert(spacetimeMetal[shade])
    if shade==216 then
     local reflection=math.exp(-((x-8.2)/4.6)^2-((y-5.2)/2.4)^2)
     colour=mix(colour,{193,171,222},reflection*.12)
    end
   else
    local pair=assert(crystal[shade],'Unknown original ingot shade: '..shade)
    colour=mix(pair[1],pair[2],pulse)
    local across=clamp(.10+.55*(x-3)/10)
    if shade==216 then
     colour=mix(mix({31,109,225},{62,210,255},pulse),
      mix({118,70,221},{179,118,255},pulse),across)
    elseif shade==255 then
     colour=mix(colour,mix({209,177,255},{250,222,255},pulse),across*.48)
    end
   end
   image:drawPixel(x,y,rgba(colour,alpha))
  end
 end end
 return image
end
local function materialDetails(style,original,frame)
 local image=Image(16,16,ColorMode.RGB)
 if not style or not style.surface then return image end
 local pulse=(1-math.cos((frame-1)/(style.frames or 1)*math.pi*2))*.5
 if style.surface=='striped' then
  for y=0,15 do for x=0,15 do
   local source=original:getPixel(x,y)
   if pc.rgbaA(source)>0 then
    local role=bandRole(x,y)
    if role then
     local shade=pc.rgbaR(source);local pair=assert(cyanBands[shade])
     local colour=mix(pair[1],pair[2],pulse)
     if role=='edge' then colour=mix(graphite[shade],colour,.48) end
     image:drawPixel(x,y,rgba(colour,pc.rgbaA(source)))
    end
   end
  end end
 elseif style.surface=='crystal' then
  -- Two small raised crystal clusters entirely inside the original top.
  -- Adjacent lit faces, violet faces and cast shadows imply height.
  local colours={
   lit=mix({91,190,255},{148,232,255},pulse),
   peak=mix({189,224,255},{235,251,255},pulse),
   shade=mix({111,65,198},{164,100,239},pulse),
   shadow=mix({35,28,69},{49,33,95},pulse),
  }
  local points={{6,5,'peak'},{5,6,'lit'},{6,6,'lit'},{7,6,'shade'},
   {6,7,'shade'},{7,7,'shadow'},{10,4,'peak'},{9,5,'lit'},
   {10,5,'lit'},{11,5,'shade'},{10,6,'shade'},{11,6,'shadow'}}
  for _,point in ipairs(points) do
   local source=original:getPixel(point[1],point[2])
   assert(pc.rgbaA(source)>0 and pc.rgbaR(source)==216,'Keep crystals within the original top plane')
   image:drawPixel(point[1],point[2],rgba(colours[point[3]],255))
  end
 end
 return image
end
local function surfaceGlow(style,original,frame)
 local image=Image(16,16,ColorMode.RGB)
 if style.surface~='crystal' and style.surface~='flowing_film' then return image end
 local phase=(frame-1)/style.frames
 local pulse=(1-math.cos(phase*math.pi*2))*.5
 for y=0,15 do for x=0,15 do
  local source=original:getPixel(x,y)
  if pc.rgbaA(source)>0 then
   local shade=pc.rgbaR(source)
   local colour,alpha
   if style.surface=='flowing_film' then
    local coverage,leading,trailing=coatingField(original,x,y,phase)
    local blue=assert(spacetimeCoating[shade])
    local rim=mix(blue,{241,252,255},shade>=168 and .45 or .18)
    colour=mix(blue,rim,leading*.35)
    colour=mix(colour,scale(blue,.76),trailing*.30)
    alpha=255*.94*coverage
   else
    local spread=math.exp(-((x-8)/5)^2-((y-6)/3.6)^2)
    colour=mix({123,224,255},{210,161,255},clamp((x-3)/10))
    alpha=255*(.025+.17*pulse)*(shade>=216 and 1 or .12)*spread
   end
   if alpha>=.5 then image:drawPixel(x,y,rgba(colour,alpha)) end
  end
 end end
 return image
end
local function coatingSpecular(style,original,frame)
 local image=Image(16,16,ColorMode.RGB)
 if not style or style.surface~='flowing_film' then return image end
 local phase=(frame-1)/style.frames
 local total,count=0,0
 for y=0,15 do for x=0,15 do
  local source=original:getPixel(x,y)
  if pc.rgbaA(source)>0 and pc.rgbaR(source)==216 then
   local coverage=coatingField(original,x,y,phase)
   total=total+coverage;count=count+1
  end
 end end
 local topCoverage=total/count
 local focus=assert(coatingCoordinates(original)[5*16+8])
 local distance=(phase-focus+.5)%1-.5
 -- One small reflection as the coating passes the lit top. It follows the
 -- film, fades smoothly, and is clipped inside the original top/bevel pixels.
 local flash=math.exp(-(distance/.11)^2)*smooth((topCoverage-.50)/.18)
 for y=0,15 do for x=0,15 do
  local source=original:getPixel(x,y)
  if pc.rgbaA(source)>0 and pc.rgbaR(source)>=216 and y<=frontEdge[x] then
   local coverage=coatingField(original,x,y,phase)
   local broad=math.exp(-((x-8)/3.4)^2-((y-5.2)/1.8)^2)
   local sparkle=0
   if x==8 and y==5 then sparkle=.52
   elseif math.abs(x-8)+math.abs(y-5)==1 then sparkle=.13 end
   local alpha=255*flash*coverage*(.13*broad+sparkle)
   if alpha>=.5 then image:drawPixel(x,y,rgba({237,252,255},alpha)) end
  end
 end end
 return image
end
local function bodyImage(style,original,material,frame)
 local image=Image(16,16,ColorMode.RGB)
 if not style then return image end
 if style.surface then return surfaceImage(style,original,frame) end
 local phase=(frame-1)/(style.frames or 1)
 local pulse=(1-math.cos(phase*math.pi*2))*.5
 local tint=style.accent and rgb(style.accent) or style.glow and rgb(style.glow)
  or style.body and colourCycle(style.body,phase)
 local cosmic=style.animation=='cosmilite' and stellarPulse(frame) or 0
 for y=0,15 do for x=0,15 do
  local source=original:getPixel(x,y);local alpha=pc.rgbaA(source)
  if alpha>0 then
   local pixel=material:getPixel(x,y)
   local base={pc.rgbaR(pixel),pc.rgbaG(pixel),pc.rgbaB(pixel)}
   local brightness=pc.rgbaR(source)/255
   local colour,strength
   if style.animation=='cosmilite' then
    colour=metalShade(stellarTint(x,cosmic),brightness,smooth((cosmic-.72)/.28))
    strength=1
   else
    colour=metalShade(tint,brightness)
    if style.tier==3 then strength=.09+.31*(1-brightness)
    elseif style.tier==4 then strength=.12+.30*pulse
    elseif style.animation=='iridescent' then strength=.70+.18*pulse
    else strength=.44+.25*pulse end
   end
   image:drawPixel(x,y,rgba(mix(base,colour,strength),alpha))
  end
 end end
 return image
end
local function animatedImage(style,original,frame)
 if style.surface then return surfaceGlow(style,original,frame) end
 local image=Image(16,16,ColorMode.RGB)
 local phase=(frame-1)/style.frames
 local pulse=(1-math.cos(phase*math.pi*2))*.5
 local cosmic=style.animation=='cosmilite' and stellarPulse(frame) or 0
 local tint=style.glow and rgb(style.glow) or style.body and colourCycle(style.body,phase)
 -- Broad light across existing faces, without fixed paths, incisions, dark
 -- grooves or extra outlines. Native face brightness retains the bevels.
 for y=0,15 do for x=0,15 do
  local pixel=original:getPixel(x,y)
  if pc.rgbaA(pixel)>0 then
   local brightness=pc.rgbaR(pixel)/255
   local face=clamp((brightness-.35)/.65)
   local spread=math.exp(-((x-8)/5)^2-((y-6)/3.6)^2)
   local level,colour
   if style.animation=='cosmilite' then
    level=smooth((cosmic-.72)/.28)*.13
    colour=mix(stellarTint(x,cosmic),{255,255,255},.60)
   else
    level=style.tier==4 and pulse*.24 or .03+.13*pulse
    colour=mix(tint,{255,255,255},style.tier==4 and .35 or .40)
   end
   local alpha=255*level*face*spread
   if alpha>=.5 then image:drawPixel(x,y,rgba(colour,alpha)) end
  end
 end end
 return image
end
local previous=Sprite{fromFile=root..'/tools/industrial_material_bases.aseprite'}
assert(previous.width==16 and previous.height==16,'Ingot details require the existing native 16px source')
local contentLayers={}
for _,layer in ipairs(previous.layers) do
  assert(not layer.isGroup,'Keep the material source as flat editable layers')
  if not owned[layer.name] then contentLayers[#contentLayers+1]=layer end
end
assert(#contentLayers>=2,'Original base and material-colour layers are required')
local function layerImage(layer,frame)
  local image=Image(16,16,ColorMode.RGB);local cel=layer:cel(frame)
  if cel then image:drawImage(cel.image,cel.position) end
  return image
end
local result=Sprite(previous.spec)
result.colorSpace=ColorSpace{sRGB=true}
local layers={}
for i,layer in ipairs(contentLayers) do
  local copy=i==1 and result.layers[1] or result:newLayer()
  copy.name=layer.name;copy.opacity=layer.opacity;copy.blendMode=layer.blendMode;copy.isVisible=layer.isVisible
  layers[i]=copy
end
local body=result:newLayer();body.name=BODY
local details=result:newLayer();details.name=DETAILS
local lights=result:newLayer();lights.name=DYNAMIC
local specular=result:newLayer();specular.name=SPECULAR
local ranges={};local frameCount,changed,animated=0,0,0
for _,tag in ipairs(previous.tags) do
  local id=tag.name;local style=styles[id]
  if not preview or (app.params.item and id==app.params.item) or (not app.params.item and representatives[id]) then
    local sourceFrame=tag.fromFrame.frameNumber
    local images={}
    for i,layer in ipairs(contentLayers) do images[i]=layerImage(layer,sourceFrame) end
    local original=images[1]
    local material=Image(16,16,ColorMode.RGB)
    for i,image in ipairs(images) do
      if contentLayers[i].isVisible then material:drawImage(image,Point(0,0),contentLayers[i].opacity,contentLayers[i].blendMode) end
    end
    local count=style and style.animation and (style.frames or 12) or 1
    local first=frameCount+1
    if style then changed=changed+1;if style.animation then animated=animated+1 end end
    for f=1,count do
      frameCount=frameCount+1
      local frame=frameCount==1 and result.frames[1] or result:newEmptyFrame(frameCount)
      frame.duration=style and style.duration or .2
      for i,image in ipairs(images) do
        if frameCount==1 and i==1 then layers[1]:cel(1).image=image else result:newCel(layers[i],frame,image,Point(0,0)) end
      end
      local surface=bodyImage(style,original,material,f)
      local detail=materialDetails(style,original,f)
      local light=style and style.animation and animatedImage(style,original,f) or Image(16,16,ColorMode.RGB)
      local shine=coatingSpecular(style,original,f)
      result:newCel(body,frame,surface,Point(0,0));result:newCel(details,frame,detail,Point(0,0));result:newCel(lights,frame,light,Point(0,0))
      result:newCel(specular,frame,shine,Point(0,0))
      if style and (preview or representatives[id]) then
        local base=material:clone();base:drawImage(surface,Point(0,0));base:drawImage(detail,Point(0,0));base:drawImage(light,Point(0,0))
        base:drawImage(shine,Point(0,0))
        base:saveAs(output..'/samples/'..id..'-'..f..'.png')
        if style.surface=='flowing_film' then
          light:saveAs(output..'/samples/'..id..'-'..f..'-film.png')
          shine:saveAs(output..'/samples/'..id..'-'..f..'-shine.png')
          if f==1 then surface:saveAs(output..'/samples/'..id..'-purple.png') end
        end
        if f==1 then material:saveAs(output..'/samples/'..id..'-before.png') end
      end
    end
    ranges[#ranges+1]={id=id,first=first,last=frameCount}
  end
end
for _,range in ipairs(ranges) do local tag=result:newTag(range.first,range.last);tag.name=range.id end
for i,layer in ipairs(contentLayers) do layers[i].isEditable=layer.isEditable end
assert(preview or (changed==23 and animated==14 and #ranges==78 and frameCount==400),'Unexpected approved ingot coverage')
result:saveAs(preview and output..'/samples.aseprite' or root..'/tools/industrial_material_bases.aseprite')
result:close();previous:close()
print('Ingot details: '..changed..' materials, '..animated..' animated, '..frameCount..' native 16px frames.')

-- Hand-crafted 32x32 pixel art for the 18 main dungeon monster drop materials.
-- Designed from the generated concept sheets with:
--   * Clean, vibrant, hue-shifted limited palettes (zero muddy downscale noise)
--   * Crisp, continuous 1px dark colored outlines (true pixel art readability)
--   * Separate editable layers: 01 / outline, 02 / body, 03 / highlights
--
-- Run with:
--   aseprite.exe --batch --script-param root=<checkout> --script tools/build_dungeon_drop_materials.lua

local root = assert(app.params.root, 'Pass --script-param root=<checkout>'):gsub('\\', '/'):gsub('/+$', '')
local outFile = root .. '/tools/dungeon_drop_materials.aseprite'

local function C(hex, a)
  local r = tonumber(hex:sub(1, 2), 16)
  local g = tonumber(hex:sub(3, 4), 16)
  local b = tonumber(hex:sub(5, 6), 16)
  return Color{ r = r, g = g, b = b, a = a or 255 }
end

local function newCanvas()
  local g = {}
  for y = 0, 31 do g[y] = {} end
  return g
end

local function put(g, x, y, col)
  x = math.floor(x + 0.5)
  y = math.floor(y + 0.5)
  if x >= 0 and x < 32 and y >= 0 and y < 32 then
    g[y][x] = col
  end
end

local function get(g, x, y)
  if x >= 0 and x < 32 and y >= 0 and y < 32 then
    return g[y][x]
  end
  return nil
end

local function line(g, x0, y0, x1, y1, col)
  x0 = math.floor(x0 + 0.5); y0 = math.floor(y0 + 0.5)
  x1 = math.floor(x1 + 0.5); y1 = math.floor(y1 + 0.5)
  local dx = math.abs(x1 - x0)
  local sx = x0 < x1 and 1 or -1
  local dy = -math.abs(y1 - y0)
  local sy = y0 < y1 and 1 or -1
  local err = dx + dy
  while true do
    put(g, x0, y0, col)
    if x0 == x1 and y0 == y1 then break end
    local e2 = 2 * err
    if e2 >= dy then err = err + dy; x0 = x0 + sx end
    if e2 <= dx then err = err + dx; y0 = y0 + sy end
  end
end

local function poly(g, pts, col)
  local minY, maxY = 31, 0
  for _, p in ipairs(pts) do
    if p[2] < minY then minY = p[2] end
    if p[2] > maxY then maxY = p[2] end
  end
  minY = math.max(0, math.floor(minY))
  maxY = math.min(31, math.ceil(maxY))
  local n = #pts
  for y = minY, maxY do
    local cy = y + 0.5
    local xs = {}
    for i = 1, n do
      local a = pts[i]
      local b = pts[(i % n) + 1]
      local y1, y2 = a[2], b[2]
      if (y1 <= cy and y2 > cy) or (y2 <= cy and y1 > cy) then
        local t = (cy - y1) / (y2 - y1)
        table.insert(xs, a[1] + t * (b[1] - a[1]))
      end
    end
    table.sort(xs)
    for i = 1, #xs - 1, 2 do
      local x0 = math.max(0, math.floor(xs[i] + 0.2))
      local x1 = math.min(31, math.floor(xs[i + 1] - 0.2))
      for x = x0, x1 do
        g[y][x] = col
      end
    end
  end
end

local function ellipse(g, cx, cy, rx, ry, col)
  for y = math.max(0, math.floor(cy - ry - 1)), math.min(31, math.ceil(cy + ry + 1)) do
    for x = math.max(0, math.floor(cx - rx - 1)), math.min(31, math.ceil(cx + rx + 1)) do
      local nx = (x - cx) / rx
      local ny = (y - cy) / ry
      if nx * nx + ny * ny <= 1.0 then
        g[y][x] = col
      end
    end
  end
end

local function ellipseRot(g, cx, cy, rx, ry, deg, col)
  local rad = math.rad(deg)
  local co, si = math.cos(rad), math.sin(rad)
  local rmax = math.max(rx, ry) + 1
  for y = math.max(0, math.floor(cy - rmax)), math.min(31, math.ceil(cy + rmax)) do
    for x = math.max(0, math.floor(cx - rmax)), math.min(31, math.ceil(cx + rmax)) do
      local dx, dy = x - cx, y - cy
      local lx = dx * co + dy * si
      local ly = -dx * si + dy * co
      if (lx * lx) / (rx * rx) + (ly * ly) / (ry * ry) <= 1.0 then
        g[y][x] = col
      end
    end
  end
end

local function star(g, x, y, core, arm)
  put(g, x, y, core)
  put(g, x - 1, y, arm)
  put(g, x + 1, y, arm)
  put(g, x, y - 1, arm)
  put(g, x, y + 1, arm)
end

local function buildOutline(body, outlineCol)
  local out = newCanvas()
  for y = 0, 31 do
    for x = 0, 31 do
      if not body[y][x] then
        if get(body, x, y - 1) or get(body, x, y + 1) or get(body, x - 1, y) or get(body, x + 1, y) then
          out[y][x] = outlineCol
        end
      end
    end
  end
  return out
end

local function writeCanvasToImage(canvas, img)
  for y = 0, 31 do
    local row = canvas[y]
    for x = 0, 31 do
      local col = row[x]
      if col then img:drawPixel(x, y, col) end
    end
  end
end

local painters = {}

-- 1. goblin_ingot (哥布林锭)
painters.goblin_ingot = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('1D2011')
  local sh     = C('494B26')
  local side   = C('686A35')
  local mid    = C('8F924A')
  local top    = C('B3B55E')
  local lit    = C('DAE086')
  local spec   = C('F7F8C4')
  local rust   = C('368E75')
  local rustH  = C('5EC29E')
  local groove = C('5C5E2D')

  poly(b, {{6,15},{18,5},{26,9},{13,20}}, top)
  poly(b, {{3,20},{6,15},{13,20},{12,26}}, side)
  poly(b, {{13,20},{26,9},{28,15},{12,26}}, sh)

  poly(b, {{7,15},{18,6},{23,8},{12,18}}, top)
  line(b, 7, 15, 18, 6, lit)
  line(b, 8, 16, 19, 7, lit)
  line(b, 13, 20, 26, 9, mid)
  poly(b, {{4,20},{6,16},{12,20},{11,24}}, mid)
  line(b, 13, 25, 27, 14, C('36381B'))
  line(b, 4, 21, 12, 26, sh)

  line(b, 13, 14, 18, 10, groove)
  line(b, 14, 14, 19, 10, sh)
  line(b, 16, 8, 20, 11, groove)
  put(b, 17, 9, sh); put(b, 18, 10, sh)
  put(b, 15, 14, lit); put(b, 19, 11, lit)

  put(b, 17, 19, rust); put(b, 18, 18, rustH); put(b, 18, 19, rust); put(b, 19, 17, rust)
  put(b, 22, 15, rust); put(b, 23, 14, rustH); put(b, 23, 15, rust)
  put(b, 9, 22, sh); put(b, 10, 23, groove)

  line(h, 9, 17, 12, 19, lit)
  put(h, 12, 19, spec); put(h, 13, 19, spec); put(h, 11, 18, spec)
  put(h, 18, 6, spec); put(h, 17, 7, spec)
  put(h, 6, 15, lit)

  return b, buildOutline(b, outC), h
end

-- 2. goblin_cloth (哥布林布)
painters.goblin_cloth = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('26140C')
  local dark  = C('592E18')
  local sh    = C('7D4322')
  local mid   = C('A85E2F')
  local lit   = C('CC7D43')
  local hi    = C('E39F62')
  local ropeD = C('8A6438')
  local ropeM = C('C49B5E')
  local ropeH = C('EBCA88')

  poly(b, {{4,18},{14,12},{28,17},{17,26}}, sh)
  poly(b, {{4,18},{17,26},{17,23},{4,15}}, dark)
  poly(b, {{4,14},{15,5},{27,12},{16,21}}, mid)
  poly(b, {{4,14},{16,21},{16,24},{4,17}}, sh)
  line(b, 16, 22, 27, 14, dark)
  line(b, 16, 23, 27, 15, C('401E10'))

  poly(b, {{6,13},{15,6},{24,11},{15,18}}, lit)
  poly(b, {{13,15},{19,11},{24,13},{18,18}}, mid)
  put(b, 15, 13, dark); put(b, 17, 12, dark); put(b, 19, 13, dark); put(b, 21, 14, dark)
  line(b, 6, 16, 14, 21, dark)
  line(b, 7, 17, 13, 20, lit)
  line(b, 6, 19, 14, 24, dark)

  line(b, 9, 10, 22, 17, ropeM)
  line(b, 9, 9, 22, 16, ropeH)
  line(b, 9, 11, 22, 18, ropeD)
  line(b, 11, 18, 21, 8, ropeM)
  line(b, 10, 18, 20, 8, ropeH)
  line(b, 12, 18, 22, 8, ropeD)
  line(b, 10, 18, 10, 22, ropeM); line(b, 11, 19, 11, 23, ropeD)
  line(b, 22, 17, 22, 20, ropeD); line(b, 21, 17, 21, 20, ropeM)

  ellipse(h, 16, 12, 2, 1.5, ropeM)
  put(h, 15, 11, ropeH); put(h, 16, 11, C('FFF0C2')); put(h, 17, 12, ropeH)
  put(h, 16, 13, ropeD)
  line(h, 14, 6, 17, 8, hi)

  return b, buildOutline(b, outC), h
end

-- 3. spinel (尖晶石): off-center perspective octahedral ruby with brilliant refractive facets
painters.spinel = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('2E0614')
  local deep   = C('6E0A2B')
  local dark   = C('9E0E3E')
  local bright = C('E81E63')
  local glow   = C('FF4D88')
  local pink   = C('FFA8C8')
  local white  = C('FFFFFF')

  -- Perspective-shifted octahedral facets (front ridge slightly left-of-center)
  -- Upper-left steep facet
  poly(b, {{16,3},{5,16},{11,17}}, bright)
  -- Upper-center main hero facet (brightest ruby)
  poly(b, {{16,3},{11,17},{21,16}}, glow)
  -- Upper-right deep facet
  poly(b, {{16,3},{21,16},{27,16}}, dark)
  -- Lower-left facet
  poly(b, {{5,16},{11,17},{16,29}}, dark)
  -- Lower-center translucent facet
  poly(b, {{11,17},{21,16},{16,29}}, bright)
  -- Lower-right deepest wine facet
  poly(b, {{21,16},{27,16},{16,29}}, deep)

  -- Inner glowing ruby refraction triangle near bottom tip
  poly(b, {{13,20},{18,19},{16,27}}, glow)

  -- Razor-crisp facet edges
  line(b, 16, 4, 11, 17, pink)
  line(b, 16, 4, 21, 16, bright)
  line(b, 6, 16, 11, 17, glow)
  line(b, 11, 17, 21, 16, pink)
  line(b, 21, 16, 26, 16, bright)
  line(b, 11, 17, 16, 28, glow)
  line(b, 21, 16, 16, 28, dark)

  -- Pure specular stars on facet junctions
  star(h, 13, 10, white, pink)
  star(h, 20, 16, white, pink)
  put(h, 16, 4, white); put(h, 15, 5, pink)
  put(h, 15, 25, pink); put(h, 16, 26, pink)

  return b, buildOutline(b, outC), h
end

-- 4. stigma_silk (圣痕丝绸)
painters.stigma_silk = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('4A331C')
  local sh    = C('B39570')
  local mid   = C('DBC39E')
  local cream = C('F5E8C8')
  local silk  = C('FFF9E6')
  local white = C('FFFFFF')
  local goldD = C('B87A18')
  local gold  = C('E8AC2C')
  local goldH = C('FFE373')

  poly(b, {{4,18},{15,12},{28,16},{17,27}}, mid)
  poly(b, {{4,18},{17,27},{17,24},{4,15}}, sh)
  poly(b, {{4,15},{16,4},{28,11},{16,22}}, cream)
  poly(b, {{4,15},{16,22},{16,25},{4,18}}, mid)
  line(b, 16, 22, 27, 13, sh)
  line(b, 16, 23, 27, 14, C('8C6E4B'))
  line(b, 6, 17, 14, 22, sh)
  line(b, 7, 18, 13, 21, silk)
  line(b, 6, 20, 14, 25, sh)
  line(b, 17, 25, 27, 16, cream)

  poly(b, {{6,14},{16,5},{26,11},{16,20}}, silk)
  line(b, 7, 14, 16, 6, white)

  line(b, 15, 5, 26, 11, gold)
  line(b, 15, 6, 25, 11, goldH)
  line(b, 6, 14, 16, 20, gold)
  line(b, 7, 14, 16, 19, goldH)
  line(b, 17, 24, 26, 16, gold)

  put(h, 20, 9, goldH); put(h, 21, 9, gold); put(h, 19, 9, gold)
  put(h, 20, 8, gold); put(h, 20, 10, goldD); put(h, 20, 11, gold)
  put(h, 13, 14, goldH); put(h, 14, 14, gold); put(h, 12, 14, gold)
  put(h, 13, 13, gold); put(h, 13, 15, goldD); put(h, 13, 16, gold)
  line(h, 11, 10, 15, 7, white)

  return b, buildOutline(b, outC), h
end

-- 5. fluorite (萤石): smooth, translucent emerald-jade crystal cluster with gentle stepped shading
painters.fluorite = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('0E2B28')
  -- Smooth, unified 6-step emerald-mint hue-shifted ramp (no jarring jumps)
  local c1    = C('165952') -- deepest jade shadow
  local c2    = C('1E7D6E') -- rich emerald shade
  local c3    = C('2CA88B') -- core emerald body
  local c4    = C('52CFA6') -- bright translucent mint-green
  local c5    = C('8CEBC2') -- soft luminous mint
  local c6    = C('C8FAE0') -- pale jade highlight
  local white = C('F0FFF7')

  -- Helper to shade a crystal prism smoothly from top-left bright to bottom-right shadow
  local function paintPrism(pts, tipX, tipY, baseY, shift)
    poly(b, pts, c3)
    for y = 0, 31 do
      for x = 0, 31 do
        if get(b, x, y) == c3 then
          -- Smooth normalized vertical + gentle horizontal shading
          local ty = (y - tipY) / math.max(1, (baseY - tipY))
          local tx = (x - tipX) / 8.0
          local v = ty * 0.72 + tx * 0.32 + (shift or 0)
          if v < 0.14 then put(b, x, y, c6)
          elseif v < 0.34 then put(b, x, y, c5)
          elseif v < 0.56 then put(b, x, y, c4)
          elseif v < 0.78 then put(b, x, y, c3)
          elseif v < 0.96 then put(b, x, y, c2)
          else put(b, x, y, c1)
          end
        end
      end
    end
  end

  -- Back-left leaning slim prism
  paintPrism({{7,9},{11,13},{13,26},{8,26},{5,15}}, 7, 9, 26, 0.08)
  -- Back-right angled prism
  paintPrism({{25,11},{27,16},{22,26},{18,26},{20,14}}, 25, 11, 26, 0.22)

  -- Slight dark separation line behind main crystal
  line(b, 11, 13, 12, 24, c2)
  line(b, 20, 14, 19, 24, c1)

  -- Tall slightly right-leaning central master prism
  local mainPts = {{16,3},{21,8},{20,27},{12,27},{11,9}}
  -- Fill with temporary marker then shade cleanly across left/right faces with gentle 1-step contrast
  poly(b, mainPts, c3)
  for y = 3, 27 do
    for x = 11, 21 do
      if get(b, x, y) == c3 then
        local ty = (y - 3) / 24.0
        local rightFace = x > 16
        local v = ty * 0.78 + (rightFace and 0.16 or -0.06)
        if v < 0.12 then put(b, x, y, c6)
        elseif v < 0.30 then put(b, x, y, c5)
        elseif v < 0.52 then put(b, x, y, c4)
        elseif v < 0.74 then put(b, x, y, c3)
        elseif v < 0.92 then put(b, x, y, c2)
        else put(b, x, y, c1)
        end
      end
    end
  end
  -- Soft translucent inner center ridge
  line(b, 16, 5, 16, 22, c5)
  line(b, 16, 14, 16, 25, c4)

  -- Small foreground left crystal (smooth step)
  poly(b, {{12,19},{15,22},{14,28},{10,27},{10,22}}, c4)
  poly(b, {{12,19},{15,22},{14,28},{12,27}}, c3)
  line(b, 10, 22, 12, 19, c2); line(b, 12, 19, 15, 22, c2)
  put(b, 12, 20, c6); put(b, 11, 22, c5)

  -- Small foreground right crystal
  poly(b, {{20,20},{23,22},{22,28},{18,28},{18,23}}, c3)
  poly(b, {{20,20},{23,22},{22,28},{20,27}}, c2)
  line(b, 18, 23, 20, 20, c1); line(b, 20, 20, 23, 22, c1)
  put(b, 20, 21, c5); put(b, 19, 23, c4)

  -- Subtle clean edge highlights
  put(h, 16, 4, white); put(h, 15, 5, c6); put(h, 16, 5, c6)
  put(h, 7, 10, c6); put(h, 25, 12, c5)
  put(h, 12, 20, white)

  return b, buildOutline(b, outC), h
end

-- 6. behemoth_scale (天帷巨兽的鳞片): dramatic pointed shield-shaped giant leviathan scale with V-ridges
painters.behemoth_scale = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('0C1F2E')
  local rimD  = C('3E7F94')
  local rimM  = C('75C7D9')
  local rimH  = C('C4F6FF')
  local white = C('FFFFFF')
  local deep  = C('1A4259')
  local mid   = C('2C6D8A')
  local lit   = C('499BB8')

  -- Distinct pointed-shield dragon scale silhouette (notches at top, sweeping to bold bottom-left point)
  local outer = {
    {13,6},{17,4},{21,4},{25,8},{27,13},{26,18},{21,24},{13,28},{6,28},
    {4,23},{4,15},{7,9},{10,6}
  }
  poly(b, outer, rimM)

  -- Sculpted bright aqua outer bevel on top and bottom-left crest
  poly(b, {{13,6},{21,4},{25,8},{22,9},{15,8},{8,12},{6,21},{8,26},{13,28},{6,28},{4,23},{4,15},{7,9}}, rimH)
  -- Shaded lower-right rim bevel
  poly(b, {{27,13},{26,18},{21,24},{13,28},{16,25},{23,19},{24,13}}, rimD)

  -- Recessed inner sea-blue shield plate
  local inner = {
    {13,9},{19,8},{23,11},{23,17},{18,22},{10,25},{7,22},{7,15},{10,10}
  }
  poly(b, inner, mid)
  -- Deep shadow along recessed inner border
  line(b, 7, 15, 10, 10, deep); line(b, 10, 10, 19, 8, deep); line(b, 19, 8, 23, 11, deep)

  -- Bold concentric V-shaped scale growth ridges pointing down-left
  -- Ridge 1 (outer)
  poly(b, {{12,11},{18,10},{21,13},{20,17},{15,21},{9,23},{9,17}}, deep)
  poly(b, {{12,12},{18,11},{20,13},{19,16},{15,20},{10,22},{10,17}}, lit)
  -- Ridge 2 (inner)
  poly(b, {{13,13},{17,13},{18,16},{14,19},{11,20},{11,16}}, deep)
  poly(b, {{14,14},{16,14},{16,16},{13,18},{12,18},{12,16}}, mid)

  -- Signature V-notch split at top-center of scale
  poly(b, {{16,5},{20,5},{17,11},{15,10}}, rimH)
  line(b, 19, 6, 17, 11, deep)
  line(b, 16, 6, 15, 10, rimD)

  -- Crisp rim speculars
  put(h, 20, 5, white); put(h, 21, 5, white); put(h, 19, 5, rimH)
  put(h, 6, 27, white); put(h, 7, 27, white); put(h, 5, 25, rimH)
  put(h, 5, 14, white); put(h, 26, 13, rimH)

  return b, buildOutline(b, outC), h
end

-- 7. behemoth_tear (天帷巨兽的眼泪): crystal clear ocean teardrop with rounded bottom & swirl
painters.behemoth_tear = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('0A285C')
  local deep   = C('1A65DB')
  local mid    = C('2DA3FF')
  local bright = C('63DBFF')
  local aqua   = C('A8FAFF')
  local white  = C('FFFFFF')

  for y = 3, 29 do
    local halfW
    if y <= 19 then
      local t = (y - 3) / 16.0
      halfW = 0.5 + 9.5 * (t ^ 1.18)
    else
      local t = (y - 19) / 10.0
      halfW = 10.0 * math.sqrt(math.max(0, 1.0 - t * t))
    end
    if halfW >= 0.4 then
      local x0 = math.floor(15.5 - halfW + 0.5)
      local x1 = math.floor(15.5 + halfW - 0.5)
      for x = x0, x1 do
        local dx = (x - 14.5) / 10.5
        local dy = (y - 19.5) / 11.5
        local d = math.sqrt(dx * dx + dy * dy)
        local col
        if d < 0.42 then col = aqua
        elseif d < 0.68 then col = bright
        elseif d < 0.88 then col = mid
        else col = deep
        end
        put(b, x, y, col)
      end
    end
  end

  poly(b, {{16,4},{13,10},{16,12},{18,9}}, bright)
  poly(b, {{16,4},{14,9},{16,10}}, aqua)
  -- Luminous oceanic swirl ribbon inside belly
  line(b, 9, 20, 14, 18, aqua); line(b, 14, 18, 19, 21, aqua); line(b, 19, 21, 22, 19, bright)
  line(b, 10, 21, 15, 20, white); line(b, 15, 20, 18, 22, aqua)
  -- Bottom refractive caustic curve
  line(b, 12, 27, 19, 27, bright)
  line(b, 13, 26, 18, 26, aqua)

  -- Glossy curved specular highlight on upper-left & trapped stars
  put(h, 12, 10, white); put(h, 11, 11, white); put(h, 11, 12, white); put(h, 10, 13, aqua)
  put(h, 12, 11, aqua)
  put(h, 15, 5, white)
  star(h, 18, 15, white, aqua)
  put(h, 19, 23, white); put(h, 11, 23, aqua)

  return b, buildOutline(b, outC), h
end

-- 8. dark_elf_bone (暗精灵的遗骨): thicker curved lavender-ivory bone with unmistakable rune glyphs
painters.dark_elf_bone = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('28143D')
  local sh    = C('886FA8')
  local mid   = C('B8A7D1')
  local bone  = C('E8DFF2')
  local lit   = C('FFF9F2')
  local white = C('FFFFFF')
  local runeD = C('6F1BC4')
  local runeM = C('B442FF')
  local runeH = C('F0ADFF')

  local spine = {
    {21, 6, 3.3}, {20, 9, 3.2}, {19, 12, 3.1}, {17, 15, 2.9},
    {15, 18, 2.7}, {13, 21, 2.3}, {10, 24, 1.9}, {7, 26, 1.4}, {4, 28, 0.9}
  }
  ellipse(b, 20, 5, 3.2, 2.7, bone)
  ellipse(b, 25, 8, 2.7, 2.3, mid)
  for i = 1, #spine - 1 do
    local p0, p1 = spine[i], spine[i + 1]
    for s = 0, 8 do
      local t = s / 8
      local cx = p0[1] + (p1[1] - p0[1]) * t
      local cy = p0[2] + (p1[2] - p0[2]) * t
      local r  = p0[3] + (p1[3] - p0[3]) * t
      ellipse(b, cx, cy, r, r, bone)
    end
  end

  for y = 2, 29 do
    for x = 3, 28 do
      if get(b, x, y) then
        local noUL = not get(b, x - 1, y) or not get(b, x, y - 1)
        local noDR = not get(b, x + 1, y) or not get(b, x, y + 1)
        local noDR2 = not get(b, x + 2, y + 1) or not get(b, x + 1, y + 2)
        if noDR then put(b, x, y, sh)
        elseif noDR2 then put(b, x, y, mid)
        elseif noUL then put(b, x, y, lit)
        end
      end
    end
  end
  put(b, 19, 4, white); put(b, 20, 4, white); put(b, 21, 4, lit)
  put(b, 24, 7, bone); put(b, 25, 7, lit)

  -- Distinct crisp angular necromantic glyphs carved along bone shaft
  -- Glyph 1 (caret/diamond rune)
  put(h, 20, 9, runeH); put(h, 21, 10, runeM); put(h, 19, 10, runeM); put(h, 20, 11, runeD)
  -- Glyph 2 (angled Z rune)
  put(h, 17, 13, runeH); put(h, 18, 13, runeM); put(h, 17, 14, runeM); put(h, 16, 15, runeH); put(h, 17, 15, runeD)
  -- Glyph 3 (chevron rune)
  put(h, 14, 18, runeH); put(h, 15, 18, runeM); put(h, 14, 19, runeM); put(h, 13, 19, runeD)
  -- Glyph 4 (tip rune)
  put(h, 10, 23, runeH); put(h, 11, 23, runeM); put(h, 10, 24, runeD)

  put(h, 19, 4, white); put(h, 16, 11, white)

  return b, buildOutline(b, outC), h
end

-- 9. soul_crystal (灵魂晶石): sharp translucent amethyst prism with taller, dancing ethereal soul flame
painters.soul_crystal = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('240D47')
  local deep   = C('5D24A8')
  local mid    = C('8F42E6')
  local bright = C('BD73FF')
  local pale   = C('E6BAFF')
  local wispD  = C('2BB5FF')
  local wispB  = C('6CE8FF')
  local wispC  = C('C4FAFF')
  local white  = C('FFFFFF')

  poly(b, {{16,2},{9,10},{10,22},{16,29},{16,10}}, bright)
  poly(b, {{16,2},{23,10},{22,22},{16,29},{16,10}}, mid)
  poly(b, {{16,21},{22,22},{16,29}}, deep)
  poly(b, {{20,10},{23,10},{22,22},{19,21}}, deep)
  poly(b, {{16,2},{9,10},{13,11}}, pale)
  poly(b, {{16,7},{20,12},{19,22},{16,25},{13,22},{12,12}}, mid)
  poly(b, {{16,7},{12,12},{13,22},{16,25}}, bright)

  line(b, 16, 3, 13, 11, pale)
  line(b, 16, 3, 19, 11, bright)
  line(b, 10, 10, 13, 12, pale)
  line(b, 22, 10, 19, 12, deep)

  -- Larger, graceful dancing soul flame core inside crystal
  ellipse(h, 16, 19, 2.8, 3.2, wispD)
  ellipse(h, 16, 19, 2.0, 2.5, wispB)
  ellipse(h, 16, 19, 1.3, 1.8, wispC)
  put(h, 16, 19, white); put(h, 16, 18, white); put(h, 15, 19, white)
  -- Sweeping S-curved flame tongue reaching upward
  put(h, 15, 16, wispC); put(h, 16, 16, wispC); put(h, 17, 16, wispB)
  put(h, 16, 15, wispC); put(h, 17, 15, wispB)
  put(h, 17, 14, wispC); put(h, 16, 14, wispB)
  put(h, 16, 13, wispB); put(h, 15, 12, wispC); put(h, 16, 11, wispD)
  -- Crystal highlights
  put(h, 16, 3, white); put(h, 15, 4, pale); put(h, 10, 11, white)

  return b, buildOutline(b, outC), h
end

-- 10. wolf_fang (锋利的狼牙)
painters.wolf_fang = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('1A1E2B')
  local rootD = C('3B3836')
  local rootM = C('59544F')
  local rootH = C('78716A')
  local sh    = C('9EA8B8')
  local ivory = C('E3DEC8')
  local cream = C('FAF5E3')
  local white = C('FFFFFF')
  local iceB  = C('67C5F5')
  local iceH  = C('C4F2FF')

  local spine = {
    {20, 9, 4.0}, {19, 13, 3.6}, {18, 17, 3.1}, {16, 20, 2.6},
    {13, 23, 2.1}, {10, 25, 1.6}, {7, 27, 1.1}, {4, 28, 0.5}
  }
  for i = 1, #spine - 1 do
    local p0, p1 = spine[i], spine[i + 1]
    for s = 0, 8 do
      local t = s / 8
      local cx = p0[1] + (p1[1] - p0[1]) * t
      local cy = p0[2] + (p1[2] - p0[2]) * t
      local r  = p0[3] + (p1[3] - p0[3]) * t
      ellipse(b, cx, cy, r, r, ivory)
    end
  end

  for y = 8, 29 do
    for x = 3, 25 do
      if get(b, x, y) then
        if y >= 24 or x <= 11 then
          local edgeR = not get(b, x + 1, y) or not get(b, x, y + 1)
          put(b, x, y, edgeR and iceB or iceH)
        else
          local edgeL = not get(b, x - 1, y) or not get(b, x - 2, y)
          local edgeR = not get(b, x + 1, y) or not get(b, x + 2, y)
          if edgeR then put(b, x, y, sh)
          elseif edgeL then put(b, x, y, cream)
          end
        end
      end
    end
  end

  poly(b, {{16,5},{23,3},{25,6},{24,10},{20,8},{16,10},{15,8}}, rootM)
  poly(b, {{17,5},{23,4},{22,7},{17,7}}, rootH)
  line(b, 16, 10, 20, 8, rootD); line(b, 20, 8, 24, 10, rootD)
  line(b, 16, 9, 20, 7, rootD)

  line(h, 16, 11, 14, 19, white)
  line(h, 13, 20, 8, 26, white)
  star(h, 10, 25, white, iceH)
  put(h, 5, 28, white); put(h, 6, 27, iceH)

  return b, buildOutline(b, outC), h
end

-- 11. disaster_sign (灾难的征兆): chunkier jagged obsidian shard cracking with molten apocalyptic flame
painters.disaster_sign = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('14070E')
  local rockD = C('262033')
  local rockM = C('433A57')
  local rockH = C('6F638A')
  local redD  = C('BD1319')
  local red   = C('F53318')
  local org   = C('FF8814')
  local yel   = C('FFE03B')
  local white = C('FFFCE0')

  -- Chunkier angular obsidian relic shard
  poly(b, {{27,4},{25,15},{21,21},{15,25},{6,29},{6,20},{9,13},{15,9}}, rockM)
  -- Bold angular rock facets
  poly(b, {{27,4},{25,15},{19,15},{20,9}}, rockD)
  poly(b, {{18,17},{24,16},{21,21},{15,25},{12,22}}, rockD)
  poly(b, {{12,22},{15,25},{6,29},{8,22}}, rockD)
  -- Crisp cool obsidian edge bevels
  line(b, 26, 5, 20, 9, rockH)
  line(b, 9, 14, 14, 10, rockH)
  line(b, 7, 20, 10, 15, rockH)
  line(b, 7, 27, 10, 22, rockH)

  -- Vivid branching magma cracks splitting the stone
  line(b, 16, 15, 19, 19, org); line(b, 19, 19, 23, 17, red); line(b, 23, 17, 24, 15, redD)
  line(b, 16, 15, 12, 19, org); line(b, 12, 19, 9, 23, red); line(b, 9, 23, 7, 26, redD)
  line(b, 16, 15, 16, 22, org); line(b, 16, 22, 15, 24, red)

  -- Dynamic curved apocalyptic fire bursting from center
  poly(b, {{16,2},{18,6},{21,10},{20,15},{12,16},{10,12},{12,8},{14,5}}, red)
  poly(b, {{16,5},{19,10},{18,15},{13,15},{12,11}}, org)
  poly(b, {{16,9},{18,13},{16,16},{14,14}}, yel)
  -- Flickering fire wisps & sparks
  put(b, 15, 3, red); put(b, 14, 4, red); put(b, 19, 7, org)
  put(h, 10, 7, org); put(h, 10, 6, red)
  put(h, 21, 6, red)

  -- Incandescent core & molten crack junction glow
  put(h, 16, 14, white); put(h, 15, 14, yel); put(h, 16, 13, yel)
  put(h, 16, 15, white); put(h, 14, 16, yel); put(h, 18, 18, yel)

  return b, buildOutline(b, outC), h
end

-- 12. thief_badge (疯狂盗贼团的徽章): crisp antique-gold gear emblem, crimson gems & sharp crossed daggers
painters.thief_badge = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('26170A')
  local brD    = C('69451D')
  local brM    = C('9E6D2E')
  local gold   = C('D6A240')
  local goldH  = C('FFE07A')
  local crimD  = C('8F1426')
  local crim   = C('E0283E')
  local crimH  = C('FF6B7D')
  local steelD = C('6F7A8F')
  local steelM = C('C2CEE0')
  local white  = C('FFFFFF')

  ellipse(b, 15.5, 15.5, 11.2, 11.2, brM)
  poly(b, {{15,2},{18,6},{13,6}}, gold)
  poly(b, {{15,29},{18,25},{13,25}}, brM)
  poly(b, {{2,15},{6,13},{6,18}}, gold)
  poly(b, {{29,15},{25,13},{25,18}}, brM)
  poly(b, {{6,6},{10,7},{7,10}}, gold)
  poly(b, {{25,6},{21,7},{24,10}}, gold)
  poly(b, {{6,25},{10,24},{7,21}}, brM)
  poly(b, {{25,25},{21,24},{24,21}}, brD)

  ellipse(b, 15.5, 15.5, 9.6, 9.6, gold)
  ellipse(b, 15.5, 15.5, 7.6, 7.6, brD)
  ellipse(b, 15.5, 15.5, 6.4, 6.4, C('7A5323'))
  -- Subtle recessed cast shadow inside top of medallion
  for x = 11, 20 do put(b, x, 10, C('543615')) end

  for _, pt in ipairs({{15,5},{15,25},{5,15},{25,15}}) do
    local cx, cy = pt[1], pt[2]
    put(b, cx, cy, crim); put(b, cx + 1, cy, crim)
    put(b, cx, cy + 1, crimD); put(b, cx + 1, cy + 1, crimD)
    put(h, cx, cy, crimH)
  end

  -- Crossed gleaming silver daggers with bold golden guards & pommels
  line(b, 11, 11, 21, 21, steelM)
  line(b, 12, 11, 22, 21, white)
  line(b, 11, 12, 21, 22, steelD)
  line(b, 9, 13, 13, 9, goldH); line(b, 10, 13, 13, 10, brD)
  put(b, 9, 9, gold); put(b, 8, 8, goldH); put(b, 10, 10, brD)

  line(b, 20, 11, 10, 21, steelM)
  line(b, 19, 11, 9, 21, white)
  line(b, 20, 12, 10, 22, steelD)
  line(b, 18, 9, 22, 13, goldH); line(b, 18, 10, 21, 13, brD)
  put(b, 22, 9, gold); put(b, 23, 8, goldH); put(b, 21, 10, brD)

  put(h, 15, 15, white); put(h, 16, 15, white); put(h, 15, 16, steelM)
  put(h, 15, 3, goldH); put(h, 3, 15, goldH)

  return b, buildOutline(b, outC), h
end

-- 13. silver_meteorite (银色陨石): rugged angular metallic meteorite with crisp rim-lit craters
painters.silver_meteorite = function()
  local b, h = newCanvas(), newCanvas()
  local outC   = C('1D283D')
  local deep   = C('495E80')
  local mid    = C('819AC2')
  local silver = C('BED0EB')
  local bright = C('E8F2FF')
  local white  = C('FFFFFF')

  -- Rugged asymmetric asteroid silhouette (not too egg-like)
  poly(b, {
    {10,6},{17,4},{23,5},{27,9},{28,16},{25,21},{22,26},{15,28},{8,27},{4,22},{4,15},{7,10}
  }, silver)

  for y = 4, 28 do
    for x = 4, 28 do
      if get(b, x, y) then
        local dx = (x - 12.5) / 13.0
        local dy = (y - 11.5) / 13.0
        local d = math.sqrt(dx * dx + dy * dy)
        if d < 0.48 then put(b, x, y, bright)
        elseif d < 0.86 then put(b, x, y, silver)
        elseif d < 1.15 then put(b, x, y, mid)
        else put(b, x, y, deep)
        end
      end
    end
  end

  local craters = {
    {10, 12, 3.0, 2.5},
    {21, 10, 2.7, 2.3},
    {19, 20, 2.9, 2.5},
    {10, 22, 2.2, 1.8},
    {15, 16, 1.4, 1.2},
  }
  for _, cr in ipairs(craters) do
    local cx, cy, rx, ry = cr[1], cr[2], cr[3], cr[4]
    ellipse(b, cx, cy, rx, ry, mid)
    ellipse(b, cx - 0.5, cy - 0.5, math.max(1, rx - 0.8), math.max(1, ry - 0.8), deep)
    -- Clean bright crescent lip on bottom-right edge of each crater
    put(h, cx + math.floor(rx), cy, bright)
    put(h, cx + math.floor(rx), cy + 1, white)
    put(h, cx + math.floor(rx) - 1, cy + math.floor(ry), bright)
  end

  put(h, 14, 9, white); put(h, 15, 9, white); put(h, 14, 10, white)
  put(h, 16, 5, white); put(h, 17, 5, bright)
  put(h, 23, 6, white); put(h, 7, 17, bright)

  return b, buildOutline(b, outC), h
end

-- 14. hero_fate (英雄的宿命): sweeping winged golden crest with vivid royal sapphire center
painters.hero_fate = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('381E09')
  local goldD = C('8C5214')
  local goldM = C('C98520')
  local gold  = C('F5BA36')
  local goldH = C('FFEA80')
  local white = C('FFFFFF')
  local sapD  = C('1235AD')
  local sapM  = C('246BFF')
  local sapH  = C('7AD0FF')

  -- Sweeping stylized multi-feather golden wings (left & right mirror)
  poly(b, {{3,4},{7,9},{11,13},{10,18},{5,16},{3,11},{2,8}}, gold)
  poly(b, {{3,4},{6,9},{9,12},{4,9}}, goldH)
  line(b, 4, 11, 10, 14, goldM); line(b, 5, 15, 10, 17, goldD)

  -- Right wing (mirror)
  poly(b, {{28,4},{24,9},{20,13},{21,18},{26,16},{28,11},{29,8}}, gold)
  poly(b, {{28,4},{25,9},{22,12},{27,9}}, goldH)
  line(b, 27, 11, 21, 14, goldM); line(b, 26, 15, 21, 17, goldD)

  -- Central destiny compass medallion
  ellipse(b, 15.5, 16.5, 7.6, 7.6, goldM)
  ellipse(b, 15.5, 16.5, 6.2, 6.2, gold)
  poly(b, {{15,6},{18,10},{13,10}}, goldH)
  poly(b, {{15,27},{19,22},{12,22}}, goldM)
  put(b, 15, 9, goldH); put(b, 16, 9, goldH)
  put(b, 15, 24, goldD); put(b, 16, 24, goldD)
  put(b, 8, 16, goldH); put(b, 23, 16, goldM)
  ellipse(b, 15.5, 16.5, 4.7, 4.7, goldD)

  -- Pure royal-blue faceted sapphire center
  ellipse(b, 15.5, 16.5, 3.6, 3.6, sapM)
  for y = 16, 20 do
    for x = 15, 19 do
      if (x - 15.5)^2 + (y - 16.5)^2 <= 13 and (x + y >= 33) then
        put(b, x, y, sapD)
      end
    end
  end
  put(h, 14, 14, sapH); put(h, 15, 14, sapH); put(h, 13, 15, sapH); put(h, 14, 15, white)
  put(h, 15, 15, sapH); put(h, 13, 16, sapH)
  put(h, 3, 5, white); put(h, 28, 5, white); put(h, 15, 7, white)

  return b, buildOutline(b, outC), h
end

-- 15. mirage_crystal (迷幻晶石): harmonious translucent lilac-rose-periwinkle crystal with silky gradients
painters.mirage_crystal = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('2E1D4D')
  -- Harmonious, closely stepped pastel lavender-periwinkle-rose ramp (no harsh split)
  local c1    = C('5D4696') -- deep periwinkle-violet shadow
  local c2    = C('8266BF') -- medium twilight lilac
  local c3    = C('A98BE0') -- soft lavender body
  local c4    = C('CCAFF2') -- airy pastel lilac
  local c5    = C('E8C8FA') -- delicate rosy-lilac sheen
  local c6    = C('C8E6FF') -- gentle dreamy sky-ice tint (same luminance as c5!)
  local white = C('FFF7FF')

  local function shadeMirage(pts, tipX, tipY, baseY, bias, coolTint)
    local tmp = newCanvas()
    poly(tmp, pts, c3)
    for y = 0, 31 do
      for x = 0, 31 do
        if tmp[y][x] then
          local ty = (y - tipY) / math.max(1, (baseY - tipY))
          local tx = (x - tipX) / 9.0
          local v = ty * 0.72 + tx * 0.28 + bias
          local col
          if v < 0.16 then col = coolTint and c6 or c5
          elseif v < 0.36 then col = c4
          elseif v < 0.58 then col = c3
          elseif v < 0.80 then col = c2
          else col = c1
          end
          put(b, x, y, col)
        end
      end
    end
  end

  -- Graceful splayed silhouette: slender left & right companion spires framing a broad hero spire
  shadeMirage({{6,13},{10,16},{12,26},{8,26},{4,19}}, 6, 13, 26, 0.06, false)
  shadeMirage({{26,13},{28,19},{23,26},{19,26},{21,16}}, 26, 13, 26, 0.14, true)

  -- Subtle depth separation behind hero spire
  line(b, 10, 16, 11, 25, c2)
  line(b, 21, 16, 20, 25, c1)

  -- Hero central faceted crystal spire (gentle top-left pink-lilac blending softly into right sky-lilac)
  local tmp = newCanvas()
  poly(tmp, {{16,2},{22,9},{21,27},{11,27},{10,9}}, c3)
  for y = 2, 27 do
    for x = 10, 22 do
      if tmp[y][x] then
        local ty = (y - 2) / 25.0
        local isRight = x > 16
        local v = ty * 0.76 + (isRight and 0.12 or -0.05)
        if v < 0.15 then put(b, x, y, isRight and c6 or c5)
        elseif v < 0.34 then put(b, x, y, isRight and c6 or c4)
        elseif v < 0.56 then put(b, x, y, c3)
        elseif v < 0.78 then put(b, x, y, c2)
        else put(b, x, y, c1)
        end
      end
    end
  end
  -- Soft bevel ridges inside hero spire
  line(b, 16, 4, 16, 20, c5)
  line(b, 11, 9, 16, 12, c5)
  line(b, 16, 12, 21, 9, c6)

  -- Front low crystal cluster base (subtle 1-step separation)
  shadeMirage({{13,20},{16,23},{15,28},{10,27},{11,22}}, 13, 20, 28, 0.02, false)
  line(b, 11, 22, 13, 20, c2); line(b, 13, 20, 16, 23, c2)
  shadeMirage({{20,20},{23,23},{22,28},{17,28},{18,23}}, 20, 20, 28, 0.10, true)
  line(b, 18, 23, 20, 20, c1); line(b, 20, 20, 23, 23, c1)

  -- Delicate crisp highlights
  put(h, 16, 3, white); put(h, 15, 4, c5); put(h, 16, 4, white)
  put(h, 6, 14, white); put(h, 26, 14, c6)
  put(h, 13, 21, white); put(h, 16, 12, white)

  return b, buildOutline(b, outC), h
end

-- 16. endless_eternity (无尽的永恒): sleek, pierced 3/4 jade-and-gold infinity ring with smooth tonal flow
painters.endless_eternity = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('162B2B')
  -- Refined, balanced celadon-jade ramp (smooth & luminous, never harsh neon)
  local j1    = C('1D5E63') -- deep sea-jade shadow
  local j2    = C('2C8787') -- rich celadon jade
  local j3    = C('49B5AC') -- clear translucent jade
  local j4    = C('7ED9CC') -- soft lit jade
  local j5    = C('B8F2E6') -- pale jade specular
  -- Refined, cohesive antique-gold ramp (harmonious value range with jade)
  local g1    = C('6E4B1E') -- warm bronze shadow
  local g2    = C('9E722E') -- antique gold
  local g3    = C('CFA046') -- soft satin gold
  local g4    = C('F2CE74') -- pale gold highlight
  local white = C('FFFDF0')

  -- 1. Sweeping 3/4 tilted gold-and-jade ring band (outer rim)
  ellipseRot(b, 17.0, 18.2, 11.2, 9.2, -26, g2)
  for y = 9, 28 do
    for x = 5, 29 do
      if get(b, x, y) == g2 then
        if x < 12 and y < 22 then put(b, x, y, g3)
        elseif y >= 24 or x >= 24 then put(b, x, y, g1)
        end
      end
    end
  end
  -- Inlaid jade band stripe
  ellipseRot(b, 17.0, 18.2, 9.6, 7.6, -26, j2)
  for y = 10, 27 do
    for x = 6, 28 do
      if get(b, x, y) == j2 then
        if x + y < 35 then put(b, x, y, j3)
        elseif x > 23 or y > 24 then put(b, x, y, j1)
        end
      end
    end
  end
  -- Inner band rim (glowing jade inner wall on lower-right, antique gold inner edge)
  ellipseRot(b, 17.0, 18.2, 7.6, 5.6, -26, g1)
  ellipseRot(b, 17.6, 18.8, 6.4, 4.5, -26, j3)
  line(b, 15, 23, 21, 21, j4)
  line(b, 16, 22, 20, 20, j4)

  -- Truly pierce the center ring hole (transparent!) so the ring silhouette reads unmistakably
  for y = 14, 22 do
    for x = 11, 22 do
      local dx, dy = x - 16.5, y - 18.2
      local rad = math.rad(-26)
      local co, si = math.cos(rad), math.sin(rad)
      local lx = dx * co + dy * si
      local ly = -dx * si + dy * co
      if (lx * lx) / (4.1 * 4.1) + (ly * ly) / (2.5 * 2.5) <= 1.0 then
        b[y][x] = nil
      end
    end
  end

  -- 2. Seamless top circular signet bezel resting on upper-left crown of the band
  ellipse(b, 13.0, 10.6, 7.8, 6.4, g2)
  for y = 4, 18 do
    for x = 5, 21 do
      if get(b, x, y) == g2 and ((x - 13.0)/7.8)^2 + ((y - 10.6)/6.4)^2 <= 1.0 then
        if (x - 13.0) + (y - 10.6) < -2.0 then put(b, x, y, g3)
        elseif (x - 13.0) + (y - 10.6) > 3.5 then put(b, x, y, g1)
        end
      end
    end
  end
  -- Smooth domed jade cabochon signet stone
  ellipse(b, 13.0, 10.3, 6.4, 5.0, j2)
  ellipse(b, 12.7, 9.9, 5.4, 4.1, j3)
  ellipse(b, 12.2, 9.4, 3.8, 2.8, j4)
  -- Clean gold inner bezel ring
  for y = 4, 16 do
    for x = 5, 21 do
      local d = ((x - 13.0)/6.4)^2 + ((y - 10.3)/5.0)^2
      if d >= 0.85 and d <= 1.04 then
        put(b, x, y, (x + y < 23) and g4 or g1)
      end
    end
  end

  -- 3. Crisp antique-gold infinity (∞) emblem centered on the jade signet
  put(b, 10, 12, j1); put(b, 11, 12, j1); put(b, 15, 12, j1); put(b, 16, 12, j1)
  put(h, 10, 9, g4);  put(h, 11, 9, g4)
  put(h, 9, 10, g3);  put(h, 12, 10, g4)
  put(h, 10, 11, g3); put(h, 11, 11, g3)
  put(h, 13, 10, white)
  put(h, 14, 10, g4); put(h, 17, 10, g3)
  put(h, 15, 9, g4);  put(h, 16, 9, g3)
  put(h, 15, 11, g3); put(h, 16, 11, g2)

  -- Gentle polished jade & gold highlights
  put(h, 9, 6, j5); put(h, 10, 5, white); put(h, 11, 5, j5)
  put(h, 7, 17, g4); put(h, 19, 22, j5)

  return b, buildOutline(b, outC), h
end

-- 17. magtonium (魔刹石): faceted dark purple stone plates split apart by glowing neon-magenta energy cracks
painters.magtonium = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('170A29')
  local crack = C('251440')
  local rockD = C('3E325C')
  local rockM = C('5D4E82')
  local rockH = C('8A79B5')
  local magD  = C('A81CD4')
  local mag   = C('F23BFF')
  local magH  = C('FFA8FF')
  local white = C('FFFFFF')

  -- Chunky faceted ore silhouette filled with dark fissure base
  poly(b, {
    {17,3},{23,6},{26,12},{27,20},{22,26},{15,28},{8,26},{5,19},{6,12},{11,6}
  }, crack)

  -- 6 distinct raised polygonal rock plates separated by glowing fissures
  -- Plate 1 (top-center)
  poly(b, {{17,4},{22,6},{21,11},{16,12},{13,8}}, rockH)
  line(b, 17, 4, 13, 8, C('AB9AD6'))
  -- Plate 2 (upper-left)
  poly(b, {{10,7},{14,12},{9,15},{6,12}}, rockM)
  line(b, 10, 7, 7, 11, rockH)
  -- Plate 3 (upper-right)
  poly(b, {{23,8},{26,13},{25,18},{20,15},{21,12}}, rockD)
  line(b, 23, 8, 21, 12, rockM)
  -- Plate 4 (lower-left)
  poly(b, {{6,15},{12,18},{10,24},{6,20}}, rockD)
  line(b, 7, 16, 11, 18, rockM)
  -- Plate 5 (bottom-center)
  poly(b, {{14,18},{19,17},{20,25},{15,27},{11,25}}, rockM)
  line(b, 14, 18, 18, 17, rockH)
  -- Plate 6 (lower-right)
  poly(b, {{21,17},{26,19},{22,25},{20,24}}, rockD)

  -- Vivid neon-magenta arcane veins running cleanly through the cracks between plates
  line(b, 16, 14, 15, 7, magD); line(b, 16, 14, 24, 12, magD)
  line(b, 16, 14, 8, 16, magD); line(b, 16, 14, 21, 21, magD)
  line(b, 16, 14, 11, 24, magD)

  line(b, 16, 14, 15, 8, mag); line(b, 16, 14, 23, 13, mag)
  line(b, 16, 14, 9, 16, mag); line(b, 16, 14, 20, 21, mag)
  line(b, 16, 14, 12, 23, mag)
  line(b, 9, 16, 6, 14, mag)

  -- White-hot arcane core nexus
  put(h, 16, 14, white); put(h, 17, 14, white); put(h, 16, 15, magH); put(h, 15, 14, magH)
  put(h, 15, 12, magH); put(h, 13, 15, magH); put(h, 19, 13, magH)
  put(h, 12, 18, magH); put(h, 18, 18, magH)

  return b, buildOutline(b, outC), h
end

-- 18. terranium (时空石): luminous warm-gold & honey-amber gemstone cluster with silky cohesive shading
painters.terranium = function()
  local b, h = newCanvas(), newCanvas()
  local outC  = C('422610')
  -- Smooth, unified 6-step warm amber-champagne-gold ramp (zero harsh cyan/pink clash)
  local c1    = C('8F5319') -- deep warm amber shadow
  local c2    = C('BD7822') -- honey amber
  local c3    = C('E6A432') -- rich luminous gold
  local c4    = C('FCD056') -- sunlit crystal gold
  local c5    = C('FFE88C') -- translucent pale champagne
  local c6    = C('FFF7C9') -- soft cream-gold highlight
  local white = C('FFFFF0')

  local function shadeGold(pts, tipX, tipY, baseY, bias)
    local tmp = newCanvas()
    poly(tmp, pts, c3)
    for y = 0, 31 do
      for x = 0, 31 do
        if tmp[y][x] then
          local ty = (y - tipY) / math.max(1, (baseY - tipY))
          local tx = (x - tipX) / 9.0
          local v = ty * 0.72 + tx * 0.28 + bias
          if v < 0.14 then put(b, x, y, c6)
          elseif v < 0.34 then put(b, x, y, c5)
          elseif v < 0.56 then put(b, x, y, c4)
          elseif v < 0.78 then put(b, x, y, c3)
          elseif v < 0.94 then put(b, x, y, c2)
          else put(b, x, y, c1)
          end
        end
      end
    end
  end

  -- Distinct wide regal golden crystal formation (broader diamond-faceted hero gem + flanking shards)
  -- Left flanking golden shard
  shadeGold({{6,12},{11,14},{12,26},{7,25},{4,18}}, 6, 12, 26, 0.06)
  -- Right flanking golden shard
  shadeGold({{26,12},{28,18},{24,25},{19,26},{20,14}}, 26, 12, 26, 0.20)

  -- Gentle separation lines behind hero crystal
  line(b, 10, 14, 11, 25, c2)
  line(b, 21, 14, 20, 25, c1)

  -- Broad central multi-faceted golden space-time crystal
  local tmp = newCanvas()
  poly(tmp, {{16,3},{22,10},{21,26},{16,28},{11,26},{10,10}}, c3)
  for y = 3, 28 do
    for x = 10, 22 do
      if tmp[y][x] then
        local ty = (y - 3) / 25.0
        local isRight = x > 16
        local v = ty * 0.74 + (isRight and 0.14 or -0.05)
        if v < 0.14 then put(b, x, y, c6)
        elseif v < 0.34 then put(b, x, y, c5)
        elseif v < 0.56 then put(b, x, y, c4)
        elseif v < 0.78 then put(b, x, y, c3)
        elseif v < 0.94 then put(b, x, y, c2)
        else put(b, x, y, c1)
        end
      end
    end
  end
  -- Clean internal crystal facet ridges (only 1 step brighter, silky transition)
  line(b, 16, 4, 16, 21, c5)
  line(b, 11, 10, 16, 13, c6)
  line(b, 16, 13, 21, 10, c4)

  -- Low front central facet crystal
  shadeGold({{16,19},{20,23},{18,28},{14,28},{12,23}}, 16, 19, 28, 0.04)
  line(b, 12, 23, 16, 19, c2); line(b, 16, 19, 20, 23, c2)

  -- Subtle starlight glints
  star(h, 13, 10, white, c6)
  put(h, 16, 4, white); put(h, 6, 13, c6); put(h, 26, 13, c5); put(h, 16, 20, white)

  return b, buildOutline(b, outC), h
end

local order = {
  'goblin_ingot',
  'goblin_cloth',
  'spinel',
  'stigma_silk',
  'fluorite',
  'behemoth_scale',
  'behemoth_tear',
  'dark_elf_bone',
  'soul_crystal',
  'wolf_fang',
  'disaster_sign',
  'thief_badge',
  'silver_meteorite',
  'hero_fate',
  'mirage_crystal',
  'endless_eternity',
  'magtonium',
  'terranium',
}

local sprite = Sprite(32, 32, ColorMode.RGB)
sprite.filename = outFile

local outlineLayer = sprite.layers[1]
outlineLayer.name = '01 / outline'
local bodyLayer = sprite:newLayer()
bodyLayer.name = '02 / body'
local highlightLayer = sprite:newLayer()
highlightLayer.name = '03 / highlights'

for i, name in ipairs(order) do
  local frame = (i == 1) and sprite.frames[1] or sprite:newEmptyFrame()
  local body, outline, highlights = painters[name]()

  local oImg = Image(32, 32, ColorMode.RGB)
  writeCanvasToImage(outline, oImg)
  sprite:newCel(outlineLayer, frame, oImg, Point(0, 0))

  local bImg = Image(32, 32, ColorMode.RGB)
  writeCanvasToImage(body, bImg)
  sprite:newCel(bodyLayer, frame, bImg, Point(0, 0))

  local hImg = Image(32, 32, ColorMode.RGB)
  writeCanvasToImage(highlights, hImg)
  sprite:newCel(highlightLayer, frame, hImg, Point(0, 0))
end

for i, name in ipairs(order) do
  local tag = sprite:newTag(i, i)
  tag.name = name
end

sprite:saveAs(outFile)
sprite:close()
print('Hand-painted 18 crisp 32x32 pixel art sprites (v3 refined) into ' .. outFile)

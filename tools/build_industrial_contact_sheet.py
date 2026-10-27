"""Label the Blender review render, and check recognition at small inventory sizes."""
from pathlib import Path
import json
import sys
from PIL import Image, ImageDraw, ImageFont
ROOT=Path(__file__).resolve().parents[1]
BASE=ROOT/'art/previews'
ARCANE='--arcane' in sys.argv
VERSION='v2' if ARCANE else 'v1'
render=Image.open(BASE/f'industrial-{VERSION}-render.png').convert('RGBA')
positions=json.loads((BASE/f'industrial-{VERSION}-layout.json').read_text(encoding='utf-8'))
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',31)
canvas=Image.new('RGB',(1600,1535),'#171c2c');canvas.paste(render,(0,90),render)
d=ImageDraw.Draw(canvas)
d.text((28,14),'魔能工业 · 第二版奥术装置' if ARCANE else '魔能工业 · 第一版外观总览',font=title,fill='#eddfbe')
d.text((29,56),'32×32 装置图集 / 16 像素每格  ·  深紫石材、金色框架与晶核  ·  Blender 预览' if ARCANE else '实际游戏模型 / Blender 材质预览  ·  五档材质 + 设备独立轮廓  ·  2026-10-27',font=font,fill='#a3afc1')
for p in positions:
    label=p['label'].replace('魔法能量信标塔','信标塔').replace('魔能','')
    width=d.textbbox((0,0),label,font=font)[2]
    d.text((p['x']-width/2,p['y']+163),label,font=font,fill='#c4cbd8')
d.text((28,1500),'水晶为真正的立体切面模型；材质参考 DNF 原图特征，结构参考 Ars Nouveau，贴图为 Aseprite 原创重绘。',font=font,fill='#8f9cb0')
canvas.save(BASE/f'industrial-{VERSION}-overview.png')
sheet=Image.new('RGB',(1000,760),'#181c29');d=ImageDraw.Draw(sheet)
d.text((24,14),'背包缩略辨识预览',font=title,fill='#eddfbe')
for i,p in enumerate(positions[:44]):
    crop=render.crop((p['x']-92,p['y']-92,p['x']+92,p['y']+92));crop.thumbnail((48,48),Image.Resampling.LANCZOS)
    x=24+(i%8)*122;y=70+(i//8)*110
    d.rounded_rectangle((x-3,y-3,x+53,y+53),radius=4,fill='#242c3c');sheet.paste(crop,(x,y),crop)
    name=p['label'].replace('魔能','').replace('魔法能量信标塔','信标').replace('物品传送水晶','传送晶').replace('作物收种水晶','收种晶')
    for j in range(0,len(name),7):d.text((x-5,y+55+(j//7)*18),name[j:j+7],font=font,fill='#bcc5d5')
sheet.save(BASE/f'industrial-{VERSION}-inventory-scale.png')
print('Labeled',len(positions),'models, including the 44-device recognition sheet.')

# 贴图管线规范

适用于步骤 2（编写 `tools/build_<career>_effect_textures.py` 生成职业专属像素帧图集与临时手持武器素材）。

## 1. 输出目录与命名

- 技能特效图集：`src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/<career>/<name>.png`
- 临时手持特效武器（配合 `PlayerPoseAnimation` 的 `heldModel`）：
  - 贴图：`src/main/resources/assets/yumis_dungeon_and_fight/textures/item/effect/<id>.png`
  - 模型：`src/main/resources/assets/yumis_dungeon_and_fight/models/item/effect/<id>.json`
  - 物品定义：`src/main/resources/assets/yumis_dungeon_and_fight/items/effect/<id>.json`

## 2. 推荐图集尺寸与帧布局

所有序列帧均采用**横向单行帧条**（帧索引 `f` 对应 `u0 = f / frames`, `u1 = (f + 1) / frames`, `v0 = 0`, `v1 = 1`）：

| 用途 | 尺寸 | 帧数 | 关键绘图方向约定（配合 Java `emitPlane`） |
| --- | --- | --- | --- |
| 斩击弧光（`*_slash.png`） | `512×64` | 8 | 弧峰朝向帧内 `+x`（右侧），扫掠轨迹沿 `y` 轴展开 |
| 抓取/突刺前端（`*_claw.png`） | `512×64` | 8 | 爪尖/前端在顶部（`v=0`），腕部/根部在底部（`v=1`） |
| 爆发/十字裂斩（`*_burst.png` / `*_cross_rend.png`） | `512×64` | 8 | 中心对称爆发，首帧极速炸开，尾帧飞溅消散 |
| 推进浪墙（`*_wave.png`） | `512×64` | 8 | 浪峰在顶部（`v=0`），贴地基座在底部（`v=1`） |
| 垂直喷发柱/光柱（`*_geyser.png` / `*_pillar.png`） | `512×128` | 8 | 冲天顶部在 `v=0`，地面底座在 `v=1`（高宽比 2:1） |
| 循环环绕雾/烟气（`*_mist.png`） | `256×64` | 4 | 首尾相位闭合的周期噪声云团 |
| 武器/剑身循环焰鞘（`*_sword_aura.png`） | `256×128` | 4 | 剑尖在顶部（`v=0`），剑柄在底部（`v=1`），4 帧循环火焰 |
| 地面法阵/裂坑贴花（`*_sigil.png` / `*_crater.png`） | `128×128` | 1 | 居中圆形构图，外圈软羽化至四角 `alpha = 0` |

## 3. 双层着色色阶原则

单一纯亮色在白天草地/雪地会糊成白片，纯暗色在洞穴里看不清。每套职业调色盘应设计为**7 档高对比渐变**，直接烘焙进 PNG：

1. **0.00–0.20（极暗剪影/焦边）**：深墨/暗血/暗紫外壳，赋予 `Blend.TRANSLUCENT` 实体重量与轮廓剪影；
2. **0.35–0.65（高饱和职业主色）**：决定职业一眼辨识度（如狱血魔神的动脉猩红、阿修罗的金紫冰火、剑圣的青白鎏金）；
3. **0.75–0.92（炽热外缘/能量脉络）**：高亮暖色或电弧边缘；
4. **0.95–1.00（白热锋核）**：极细高光核，在 `Blend.ADDITIVE` 叠加层下产生锐利打击闪光。

像素风格与柔和羽化结合：主形状用带值噪声（`fbm`）与适度阶梯量化（`quantize`）保持像素质感，外缘保留平滑 Alpha 衰减避免生硬锯齿。地面法阵、裂坑、爆发图的四角像素必须 `alpha == 0`，杜绝四边形贴图边界。

## 4. 32×32 专属特效武器模型

当骨骼动画通过 `heldModel` 临时切换武器外观（如血剑、鬼神剑等），且原有资产只是指向原版剑占位时：
- 在生成源内同步绘制 `32×32` 专属手持武器 PNG（对角朝向：左下柄、右上尖，符合 `minecraft:item/handheld` 约定）；
- 创建 `models/item/effect/<id>.json`（`parent: "minecraft:item/handheld"`，配置 `thirdperson_righthand` / `thirdperson_lefthand` / `firstperson_*` 缩放与偏移）；
- 更新 `items/effect/<id>.json` 指向新模型路径。

## 5. Contact Sheet 目视自检

生成正式 PNG 后，在 `temp/<career>-vfx/` 写出预览拼图（每张图集放大 2×–4×，分别叠加暗灰与浅灰棋盘格背景），用 `read_file` 查看所有拼图，重点检查：
- 序列帧动作节奏是否从蓄势/爆发到飞溅消散自然连贯；
- 顶部/底部与正反朝向是否严格符合上表约定；
- 暗部剪影在浅底上清晰、炽亮核心在暗底上锐利；
- 圆形贴花和法阵四角完全透明，无截断方边。

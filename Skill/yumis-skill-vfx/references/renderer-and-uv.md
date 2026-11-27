# 渲染器与 UV 朝向合同

适用于步骤 3（编写 `<Career>Visuals.java` 并接入 `SkillVisualRegistry` / `SkillVfx` / `Effects`）。

## 1. 客户端接线点

| 接入位置 | 职责与做法 |
| --- | --- |
| `SkillVisualRegistry.java` | 在静态块注册 `<Career>Visuals.install()`；同步从 `CareerSkillVisuals.java` 删除已迁移技能 ID，防止重复注册抛 `IllegalArgumentException`。 |
| `SkillVfx.extract(...)` | 若有常驻态（Buff 环、背剑、刻印、蓄力圈等），调用 `<Career>Visuals.actors(quads, sprites, camera, serverNow, time)`。 |
| `SkillVfx.clear()` | 调用 `<Career>Visuals.clear()` 清理全部纯客户端瞬态 `Map`（如上次层数、闪光时间、残影阶段）。 |
| `Effects.java` / `SkillVisualRegistry.Handler` | 若某技能“开启 Buff 态”与“终结技态”共用同一技能 ID（如魔狱血刹），覆写带参 `cinematic(SkillVisualEvent)` 与 `persistentEnd(SkillVisualEvent)` 按 `event.sweep()` 等字段分流。 |
| `BuiltinSkillVisuals.java` / 对应普攻渲染 | 职业专属普攻覆写（如血之狂暴双刀普攻）通过判定方法（`isFrenzyBasic`）委托到 `<Career>Visuals`，保留其方法签名兼容（不覆盖并发任务的改动）。 |

## 2. 关键陷阱：`EffectSprites.emitPlane` 顶点与 UV 方向

`EffectSprites.emitPlane(out, tex, blend, center, halfU, halfV, u0, v0, u1, v1, rgb, alpha)` 的顶点布局固定为：

```text
a = center - halfU - halfV  -> (u0, v0)   // PNG 左上角
b = center + halfU - halfV  -> (u1, v0)   // PNG 右上角
c = center + halfU + halfV  -> (u1, v1)   // PNG 右下角
d = center - halfU + halfV  -> (u0, v1)   // PNG 左下角
```

因为 `v0 = 0`（PNG 顶部）位于 `center - halfV`，而 `v1 = 1`（PNG 底部）位于 `center + halfV`：

- **直立面片（喷泉/光柱/浪墙/竖直剑焰）**：`halfV` 必须朝 **下**（`new Vec3(0, -height * 0.5, 0)`），否则贴图会上下颠倒！
- **面向镜头的直立序列帧公告板**：`EffectSprites.emitBillboard` 内部传 `+up` 会让非对称竖图倒置；应自写 `emitFramedBillboard`，传入 `halfU = right.scale(radius)` 与 **`halfV = up.scale(-radius)`**，确保 `v=0` 始终在屏幕上方。
- **沿任意轴伸展的武器焰鞘（支持向上漂浮与向下插地）**：统一取 `halfV = axis.scale(-length * 0.54)`（`axis` 从柄指向尖），向上时尖端在高处、下插时尖端在低处，一套公式两边正确。
- **水平前伸面片（爪击等，`v=0` 为前端）**：`halfU = right.scale(scale)`，`halfV = dir.scale(-scale)`。
- **前向斩击弧光（弧峰在 `+x`）**：`halfU = dir.scale(scale)`，`halfV = side.scale(scale)`（`side` 按 `rollDeg` 倾斜，镜像时取反）。

## 3. 双层渲染与体积造型

- **双层贴图发射**：先发 `Blend.TRANSLUCENT`（`rgb = 0xFFFFFF`，完整呈现 PNG 烘焙的暗色剪影与饱和主色），再叠一层轻微缩放或沿视线/法向偏移 `0.02–0.05` 格的 `Blend.ADDITIVE`（主色或白热偏色，乘 `0.50–0.85` 透明系数），兼顾白天对比度与暗处辉光。
- **去方块化的立体感**：
  - 垂直柱体（血柱/火柱/冰刺）：用 `0° / 60° / 120°` 三交叉轴面，保证任意视角都饱满；
  - 推进波浪：主浪墙略向前倾（`halfV = (0, -h/2, 0) - dir * 0.14h`），并补一张沿 `dir` 方向的纵向切面，避免侧看变成纸片；
  - 巨型实体能量武器（如背负魔剑）：若需 3D 实心骨架，使用 4 菱形斜切分段（`emitDiamondSegment`）配合护手尖刺，再以正交双面循环焰鞘 `SpriteQuad` 包裹，禁用无贴图矩形大长方体。

## 4. 多感官分层、第一人称与觉醒镜头

- **多感官组合**：贴图主体之外，按阶段叠加 `RibbonTrail`（武器尖端丝带，锚点优先取 `PlayerBoneAnchor.hand`）、`LightningBolt`（确定性能量电弧）、`PlayerAfterimages`（位移/起跳/收招定格残影）、`WorldEffects`（短时冲击波/辉光）、分层 `SkillSound` 与仅对本机施法者生效的 `CameraShake.local(...)`。
- **第一人称视野保护**：贴身环绕雾气、副手武器光效、近身光环对本机第一人称用 `isOwnFirstPerson(casterId)` 降透明度、外推或隐藏，确保视野清晰。
- **条件第三人称觉醒镜头**：
  - 仅在终结/大招阶段触发，开启 `cinematicFromFirstPerson() = true` 与 `restoreCamera() = true`；
  - `camera(Track, long)` 用 `mc.level.clip(new ClipContext(target, pos, COLLIDER, NONE, mc.player))` 做防穿墙回拉（命中墙壁时向目标回缩 `0.25–0.35` 格）。

## 5. 可测性设计

将无头验收需要的核心逻辑抽为包级静态方法（如 `renderSkill(...)`、`isCinematic(...)`、`isPersistentEnd(...)`、`computeFinishedUntil(...)`、立体构件方法），且在渲染分支中对 `Minecraft.getInstance()` 做好判空回退，使无头测试无需启动客户端渲染器或 FML 即可完整驱动所有技能相位。

---
name: yumis-skill-vfx
description: >-
  为 YumisFight 制作或重做职业技能特效（程序化像素帧图集 + 双层渲染器）、接入专属手持特效武器模型、
  同步技能实际判定范围与特效覆盖，并执行无头资源与渲染几何验证。
  当用户要求用最新方案制作/重做某职业特效、参考原版 DNF 设计演出、制作技能贴图集，或对齐技能特效与实际判定范围时使用。
---

# 职业技能特效制作与验收

本 skill 随项目保存在 `Skill/yumis-skill-vfx/`，项目根目录为本文件上两级。先读根目录 [AGENTS.md](../../AGENTS.md)、[创作指南](../../doc/创作指南/README.md)（关注 `EffectSprites` 与 `PlayerPoseAnimation` 设施行）及 [本机环境](../../doc/本机环境.md)。参考已落地的现代范例：[BerserkerVisuals.java](../../src/client/java/cn/yumis/fight/modules/skills/client/BerserkerVisuals.java)、[AsuraVisuals.java](../../src/client/java/cn/yumis/fight/modules/skills/client/AsuraVisuals.java)、[SwordmasterVisuals.java](../../src/client/java/cn/yumis/fight/modules/skills/client/SwordmasterVisuals.java)。

## 硬性边界

- **纯客户端表现**：特效层只消费事件与只读快照，保持战斗权威、冷却、消耗、位移和骨骼时序由既有规则决定；涉及受保护文件（`architecture/protected-files.json`）须先获用户授权并按 AGENTS 流程确认 fetch。
- **单一数据源**：贴图一律由可复现的 Python 生成源产出，不手改导出 PNG；已有职业重做时从 `CareerSkillVisuals.java` 移除旧分支，避免 `SkillVisualRegistry` 重复注册。
- **不覆盖他人改动**：提交与构建前只触碰本任务范围文件；临时验证产物一律放入 `temp/<career>-vfx/`。

## 制作流程

### 1. 盘点技能事件合同并对齐视觉方案

读取目标职业的技能定义（`CareerSkills.java` / 对应职业 `*Skills.java`）、客户端行为发包（`CareerSkillBehaviors.java` 等）、骨骼动作与 `heldModel` 配置（`tools/build_player_animations.py`）、持久状态快照（`CareerClient.INSTANCE.view.actors`）以及实际判定范围（`rules/balance/skills/<career>.json`）。

明确以下设计分叉后再动手（需求模糊时用 `ask_question` 或 `/grill-me` 一次性对齐）：
- 色阶与材质基调（暗底厚重层 + 炽亮核心层）、是否参考原版 DNF 对应技能标志轮廓；
- 持久 Buff / 叠层 / 低血等随状态演化的常驻演出与第一人称可见性；
- 觉醒或终结技镜头策略（激活态 vs 终结态是否条件触发第三人称镜头）；
- 是否需要定制 `effect/` 手持临时武器模型（替换原版道具占位）；
- 实际判定范围保持不变还是同步微调以贴合特效。

**完成标准**：列出该职业全部技能的事件相位表（`START` / `RELEASE` / `HIT` / `FINISH` / `END` / `CANCEL`、`sweep`/`dual`/`charge`、持久态字段、当前判定范围）及每一项对应的视觉分层清单。

### 2. 编写贴图生成源并目视自检

按 [贴图管线规范](references/texture-pipeline.md) 编写 `tools/build_<career>_effect_textures.py`，生成 `src/main/resources/assets/yumis_dungeon_and_fight/textures/effect/<career>/` 下的多色阶像素帧图集（及按需生成的 32×32 `effect/` 手持武器贴图与模型 JSON）。

运行生成脚本后，在 `temp/<career>-vfx/` 导出深/浅棋盘格底色的放大合集图（contact sheet），用 `read_file` 逐张目视审查帧动画连贯性、暗部轮廓、亮部层次与地面贴花四角全透明边界，不满意则改脚本重生成，直到视觉过关。同时将新生成源登记进 [创作指南](../../doc/创作指南/README.md) 的 `EffectSprites` 行。

**完成标准**：全部正式贴图（及按需新增的 `items/effect/` + `models/item/effect/`）均由脚本生成完毕，且每张图集的 contact sheet 已目视确认无方框硬边、无残帧或发灰塌陷。

### 3. 实现 `<Career>Visuals.java` 并接入客户端注册

按 [渲染器与 UV 朝向合同](references/renderer-and-uv.md) 新建 `src/client/java/cn/yumis/fight/modules/skills/client/<Career>Visuals.java`，使用**双层贴图渲染（`TRANSLUCENT` 暗底实体 + `ADDITIVE` 炽亮高光）** 替代老旧纯色方块 `WorldQuads`，并组合 `RibbonTrail`、`LightningBolt`、`PlayerAfterimages`、`PlayerBoneAnchor`、`WorldEffects`、`SkillSound` 与 `CameraShake`。

完成以下接入与清理：
- 在 `SkillVisualRegistry.java` 注册 `<Career>Visuals.install()`，并从 `CareerSkillVisuals.java` 移除已迁移技能；
- 若有常驻态（Buff 环、背剑、刻印、蓄力圈等），接入 `SkillVfx.extract` 的 `actors(...)` 与 `SkillVfx.clear()` 的 `clear()`；
- 若有普攻覆写（如狂战双刀普攻、波动眼普攻），在对应委托入口分流而不改调度层；
- 若有激活/终结二段不同镜头或收尾逻辑，覆写 `Handler.cinematic(SkillVisualEvent)` 与 `Handler.persistentEnd(SkillVisualEvent)`。

**完成标准**：全技能相位、常驻态、第一人称遮挡保护、声音震屏、条件镜头及 `clear()` 生命周期清理均已实现，且暴露包级可测渲染方法供无头验证调用。

### 4. 按需同步实际技能判定范围（可选分支）

仅当用户明确要求“调整/提高实际技能范围”或“同步判定与特效”时执行；若用户明确不改特效大小，保持 `<Career>Visuals.java` 视觉半径不变。具体五处同步清单与共享常量保护见 [范围同步与无头验收](references/range-and-verification.md#同步技能实际判定范围)。

**完成标准**：平衡 JSON、技能默认定义、客户端行为判定（无残留硬编码）、通用动作蓝图、技能说明文案五处数值完全一致，且未改动其他职业共享常量。

### 5. 编译、架构检查与双轨无头验证

按 [范围同步与无头验收](references/range-and-verification.md#无头验证流程) 执行全部校验：
1. Gradle 单源集编译与架构边界检查（`compileJava processResources verifyArchitecture --offline`）；
2. Python 资源一致性校验（`temp/<career>-vfx/verify_assets.py`）；
3. 无头 Java 几何与规则校验（`temp/<career>-vfx/Verify<Career>Visuals.java`，绕开 FML/SoundEvents 引导限制，逐帧断言顶点有限、UV/Alpha 合法、贴图文件存在、直立/下插/前向朝向正确，以及调整范围时的 `SkillBalanceRules` 与 `SkillBlueprints` 断言）。

**完成标准**：Gradle 编译与架构检查退出码 0，Python 资源脚本与 Java 无头校验全部通过；回复中列明改动文件链接、前后对照（如有范围变更）与具体通过项，不擅自暂存、提交或导出 JAR。

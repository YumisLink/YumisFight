# 范围同步与无头验收

适用于步骤 4（按需同步实际技能判定范围）与步骤 5（编译、架构校验及双轨无头测试）。

## 同步技能实际判定范围

仅在用户要求调整技能实际范围时执行。若用户明确“不要放大特效”，保持 `<Career>Visuals.java` 内所有视觉半径不变。修改后须保证以下 **5 处完全同步**（不改动伤害倍率、冷却、消耗、位移与骨骼时长）：

1. **平衡数据表**：`src/main/resources/data/yumis_dungeon_and_fight/rules/balance/skills/<career>.json`
   - 更新各技能 `range`；对线性宽判定或二次爆炸技能，显式补充 `width`、`blastRadius` 等参数。
2. **技能默认定义**：`src/main/java/cn/yumis/fight/modules/skills/api/CareerSkills.java`（或对应职业 `*Skills.java`）
   - 同步 `add(...)` 中的默认 `range`，并用 `.formParameter("width", ...)` / `.formParameter("blastRadius", ...)` 登记新增形态参数。
3. **客户端实际判定**：`src/client/java/cn/yumis/fight/modules/skills/client/CareerSkillBehaviors.java`（或对应职业行为类）
   - 更新参数读取的回退默认值；将原硬编码的判定长度、宽度、爆炸半径全部改为从 `p.get(...)` 读取。
4. **通用动作蓝图**：`src/main/java/cn/yumis/fight/modules/skills/api/SkillBlueprints.java`
   - 同步对应技能在 `ActionRegistry` 中的 `slash` / `area` / `wave` 尺寸（供深渊人形怪或无源世界动作复用）。
5. **技能描述文案**：`src/client/java/cn/yumis/fight/modules/skills/client/CareerSkillDescriptions.java`（或对应描述类）
   - 同步面板显示的半径、长度、宽度数值，避免文案与实判脱节。

**跨职业常量检查**：确认不会顺手改到他职业共享常量（例如 `StormChoreography.RADIUS` 归属剑圣暴风式，必须保持 `5.0`）。

---

## 无头验证流程

开始前先读 `doc/本机环境.md` 获取本机 JDK、Gradle 与 Python 路径。所有临时测试脚本、编译输出与预览图均放入 `temp/<career>-vfx/`。

### 1. 编译与架构边界门禁

本项目客户端与主源码共用单一 `sourceSet`（无 `compileClientJava` 任务），PowerShell 5.1 不支持 `&&`，使用 `$LASTEXITCODE` 串接：

```powershell
$env:JAVA_HOME = '<本机环境中的 JDK 目录>'
& '<本机环境中的 gradle.bat>' --project-cache-dir temp/gradle --offline compileJava processResources verifyArchitecture
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
```

确认输出 `BUILD SUCCESSFUL` 且 `Architecture checks passed`。注：当前 `src/test` 无活跃 JUnit 用例，直接跑 `:test` 会报 `did not discover any tests to execute`，须由下方双轨无头验证完成实际覆盖。

### 2. Python 资源一致性校验（`temp/<career>-vfx/verify_assets.py`）

编写并运行校验脚本，覆盖：
- 解析 `<Career>Visuals.java` 中全部 `tex("...")` 引用，与预期图集字典完全相等；
- 校验每张 PNG 存在、宽高与帧数匹配、每一帧有效非透明像素数与目标色通道峰值达标；
- 校验地面圆形贴花/法阵四角像素 `alpha == 0`；
- 若新增了 `effect/` 手持武器，校验 `items/effect/<id>.json`、`models/item/effect/<id>.json` 引用链与 `32×32` PNG 像素覆盖。

### 3. 无头 Java 几何与规则校验（`temp/<career>-vfx/Verify<Career>Visuals.java`）

将验证类声明在 `package cn.yumis.fight.modules.skills.client;` 下，先从 Gradle 导出一次运行时 classpath 到 `temp/<career>-vfx/classpath.txt`（包含 `temp/build/classes/java/main` 与 `temp/build/resources/main`），再用本机 `javac` + `java` 直接执行。

**关键避坑（FML 未引导错误）**：
- 无头 JVM 中触发 `SkillVisualRegistry.<clinit>` 会连带初始化 `MageSkillVisuals` -> `SoundEvents` -> `Not bootstrapped` / `No current FML Loader` 崩溃；
- 直接调用 `<Career>Visuals` 的包级可测静态方法（`renderSkill`、`isCinematic`、`isPersistentEnd`、`computeFinishedUntil`、立体构件与普攻渲染方法），不要在无头环境中初始化 `SkillVisualRegistry`。

**必测断言清单**：
- **几何与贴图合法性**：遍历每一帧产出的全部 `Quad` 与 `SpriteQuad`，断言 4 个顶点 `Double.isFinite`、`alpha ∈ [0, 255]`、`u/v ∈ [0, 1]`、且每个 `sq.texture()` 对应的 PNG 文件真实存在于 `src/main/resources/assets/yumis_dungeon_and_fight/`；
- **朝向防倒置断言**：
  - 直立喷泉/光柱/浪墙/向上剑焰：`0.5 * (a.y + b.y) > 0.5 * (c.y + d.y)`（`v=0` 在上）；
  - 下插剑焰：`0.5 * (a.y + b.y) < 0.5 * (c.y + d.y)`（剑尖朝下）；
  - 前向斩击（施法朝向 `+Z`）：弧前缘 `z >` 后缘 `z`；
- **状态与镜头合同**：逐一覆盖所有技能相位、激活态 vs 终结态镜头/持续收尾判定、普攻各模式及 `clear()` 清理；
- **规则与蓝图断言（同步范围时必加）**：执行 `SkillBalanceRules.defaults().validate()`，逐项断言 `rules.parameters(SkillCatalog.require(id), 1)` 返回更新后的 `range`/`width`/`blastRadius`，执行 `SkillBlueprints.install()` 并断言 `ActionRegistry.find(id) != null`，同时断言共享常量未被误改。

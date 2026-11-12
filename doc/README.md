# 项目索引

先读 [AGENTS](../AGENTS.md)，再按任务查下表；未完成事项只看根目录 [TODO](../TODO.md)。本文件只定位入口，当前行为和数值以正式源码、资源为准。

新增内容先看 [创作指南](创作指南/README.md)：已有设施用在哪里、制作需哪些参数；调数另看 [数值平衡评估](创作指南/数值平衡评估.md)。

## 目录与装配

| 查什么 | 在哪里 |
|---|---|
| 主源码 | `src/main/java/cn/yumis/fight/` |
| 客户端源码 | `src/client/java/cn/yumis/fight/` |
| 模块登记与依赖 | 主源码 `bootstrap/ApplicationModules.java` |
| 服务端装配 | 主源码 `bootstrap/ServerApplication.java` |
| 客户端装配 | 客户端源码 `bootstrap/ClientBootstrap.java`、`ClientSession.java` |
| 底层边界与事务 | 主源码 `architecture/`、`foundation/Journal.java` |
| 底层保护清单 | [architecture/protected-files.json](../architecture/protected-files.json) |

## 业务入口

以下路径相对主源码根，同一行省略的前缀沿用所属目录。模块内部按 `api`、`application`、`platform` 分工，客户端实现位于客户端源码的同名模块。跨模块只消费公开 API，实际依赖查 `ApplicationModules`。

| 任务 | 模块与优先入口 |
|---|---|
| 属性、乘区、来源 | `modules/attributes/api/Stats.java`、`Stat.java`、`AttributeSources.java`；统一算式在 `architecture/AttributeMath.java` |
| 词条定义、资格、效果与同步 | `modules/affixes/api/AffixDefinitions.java`、`AffixGeneration.java`、`AffixEffects.java`、`AffixResolver.java` |
| 装备、具名模板、成长与解析 | `modules/equipment/api/EquipmentRules.java`、`EquipmentChanges.java`、`NamedEquipment.java`、`EquipmentGrowth.java`、`EquipmentResolution.java`；本机操作在客户端 `equipment/client/EquipmentActions.java` |
| 伤害、护盾、命中与回执 | `modules/combat/api/CombatRules.java`、`CombatPorts.java`、`LifeRules.java`；本机普攻与命中在客户端 `skills/api/client/SkillController.java`，服务器回执在 `combat/application/CombatApplication.java` |
| 元素积累与异常 | `modules/combat/api/ElementAccumulation.java`、`Life.java`、`CombatContentTypes.java`；状态随生命同步，异常结算复用战斗回执 |
| 技能内容、学习、费用与伤害 | `modules/skills/api/SkillCatalog.java`、`SkillLearning.java`、`SkillCosts.java`、`SkillDamageRules.java`；职业内容在同目录，本机施放从客户端 `skills/client/CareerClient.java`、`skills/api/client/SkillController.java` 进入；服务器世界实体在 `skills/application/` |
| 角色、职业栏与存档 | `modules/character/api/JobCatalog.java`、`CareerProfiles.java`、`CharacterChanges.java`；本机判定在客户端 `character/client/CharacterClient.java`，存档端口 `CharacterPersistence.java`，编解码在 `application/CharacterStore.java` |
| 副本、敌人与难度 | `modules/dungeons/api/DungeonCatalog.java`、`Target.java`、`EncounterScaling.java`；经典后续副本的敌人／动作入口为 `ClassicEnemyKind.java`、`ClassicMove.java`；地图与编队在 `content/`，专属机制在 `application/*EncounterService.java`，本机接触与演出在客户端 `dungeons/client/` |
| 引路石、引路台与地狱+ | `modules/dungeons/api/Waystone.java`、`WaystoneRules.java`；流程在 `application/WaystoneService.java`，参数在 `rules/catalog.json` 的 `waystones`；分阶宝箱在 `rules/chests.json` |
| 工业设备、配方、科技与结算 | `modules/industry/api/DeviceType.java`、`IndustrialRules.java`、`IndustrialRecipe.java`、`IndustrialTechnology.java`；机器／产线／物流在 `platform/` |
| 副职业、经验与奖励 | `modules/professions/api/ProfessionCatalog.java`、`ProfessionRules.java`、`ProfessionRewards.java`；本机判定在客户端 `professions/client/ProfessionClient.java`，事实保存与管理奖励在 `application/ProfessionService.java` |
| 库存、容器、接收与事务 | `modules/inventory/api/InventoryOperations.java`、`InventoryEdits.java`、`InventoryPacking.java`、`InventoryIntake.java`；事务在 `application/InventoryTransactions.java` |
| 通货、掉落、出售与分解 | `modules/economy/api/CurrencyRules.java`、`RewardRules.java`、`ChestRewards.java`、`RewardPorts.java`；本机操作在客户端 `economy/client/EconomyActions.java`，事实保存与世界投影在 `application/` |
| 单抽罐子、奖池与演出 | `modules/economy/api/JarDefinitions.java`、`LootJarItem.java`；配置 `rules/jars.json`，本机决定奖品在客户端 `economy/client/EconomyActions.java`，演出在 `JarClient.java`、`JarScreen.java`，保存与交付在主源码 `application/JarService.java` |
| 队伍与邀请 | `modules/party/api/Team.java`、`PartyInvitation.java` |
| 页面、主题、输入与通用演出 | `modules/presentation/api/`；客户端绘制及页面合同查其 `client/` 和 `api/client/` |
| 野外建筑、配置地图与出生村庄 | [野外建筑生成规则](创作指南/野外建筑生成.md)；公开入口 `modules/mapgeneration/api/MapGeneration.java`、`MapBlueprint.java`；启动开关在游戏目录 `config/ydcore.toml` |

## 规则、资源与工具

| 查什么 | 在哪里 |
|---|---|
| 规则整包与发布 | 主源码 `bootstrap/ApplicationRules.java`、`architecture/RuleStore.java` |
| 装备、技能、工业、副本及宝箱参数 | `src/main/resources/data/yumis_dungeon_and_fight/rules/`；装备模板在相邻 `equipment/` |
| 配方、标签、掉落与世界生成 | `src/main/resources/data/yumis_dungeon_and_fight/{recipe,tags,loot_table,worldgen}/` |
| 语言、模型、图标、着色器与动作 | `src/main/resources/assets/yumis_dungeon_and_fight/`，按业务目录查找 |
| 工业资源生成源 | `tools/industrial_resources.py`、`industrial_expansion.py`、`industrial_technology.py`、`industrial_arcane_art.lua`、`industrial_arcane_breathing.lua` |
| 武器模型生成源 | `tools/build_weapon_models.py`、`tools/build_warglaive_model.py` |
| 构建与依赖 | `build.gradle`、`gradle.properties`、`gradle/local-mods/`；本机入口见 [本机环境](本机环境.md) |
| 正式测试与提交检查 | `src/test/java/`、`tools/check-submission.ps1`；运行和修改须遵守 AGENTS |
| 模块边界检查 | `gradle/architecture.gradle`，属于受保护文件 |
| Mods 同步工具 | `ModSyncTemp/`，独立于游戏运行模块 |
| 既有玩家资料与演出术语 | `playerread/`，按需参考，不作为开发后自动维护项 |
| 临时文件与验证产物 | `temp/`，不提交、不上传 |

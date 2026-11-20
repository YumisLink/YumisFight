# 项目索引

先读 [AGENTS](../AGENTS.md)，再按任务查下表；未完成事项只看根目录 [TODO](../TODO.md)。本文件只定位入口，当前行为和数值以正式源码、资源为准。

新增内容先看 [创作指南](创作指南/README.md)：已有设施用在哪里、制作需哪些参数；调数另看 [数值平衡评估](创作指南/数值平衡评估.md)。

## 目录与装配

| 查什么 | 在哪里 |
|---|---|
| 全模块位置与制作归属 | [模块地图](模块地图.md) |
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
| 天赋盘、出生点与条件／召唤键位 | `modules/talents/api/TalentCatalog.java`、`TalentLedger.java`、`TalentChanges.java`、`TalentEffects.java`；本人客户端在 `talents/client/TalentPage.java`（I 键菜单页）、`TalentCombat.java`（结算求值），召唤消耗见 `modules/skills/api/SkillCosts.java` 与 `modules/skills/application/MageSkillService.java` |
| 角色、职业栏与存档 | `modules/character/api/JobCatalog.java`、`CareerProfiles.java`、`CharacterChanges.java`；本机判定在客户端 `character/client/CharacterClient.java`，存档端口 `CharacterPersistence.java`，编解码在 `application/CharacterStore.java` |
| 副本、敌人与难度 | `modules/dungeons/api/DungeonCatalog.java`、`Target.java`、`EncounterScaling.java`；经典后续副本的敌人／动作入口为 `ClassicEnemyKind.java`、`ClassicMove.java`；地图与编队在 `content/`，专属机制在 `application/*EncounterService.java`，本机接触与演出在客户端 `dungeons/client/` |
| 觉醒塔与任务通关 | `modules/dungeons/api/AwakeningTower.java`、`api/AwakeningTowerStele.java`、`content/AwakeningTowerLayout.java`；塔门与战场素材为 `structure/towers/`，制作源 `tools/build_tower_structures.py`，野外塔门和石碑投放见 `tools/wild_buildings.json`，觉醒进度在 `modules/npc/application/AwakeningQuestService.java` |
| 练功房、材料来源与循环刷新 | `modules/dungeons/api/PracticeRoom.java`、`content/PracticeLayouts.java`、`application/PracticeEncounterService.java`；客户端入口为 `dungeons/client/DungeonPage.java`，金币与公共掉落由 `modules/economy/api/RewardRules.java` 结算 |
| 引路石、引路台与地狱+ | `modules/dungeons/api/Waystone.java`、`WaystoneRules.java`；流程在 `application/WaystoneService.java`，参数在 `rules/catalog.json` 的 `waystones`；分阶宝箱在 `rules/chests.json` |
| 工业设备、配方、科技与结算 | `modules/industry/api/DeviceType.java`、`IndustrialRules.java`、`IndustrialRecipe.java`、`IndustrialTechnology.java`；机器／产线／传送带物流在 `platform/`；传送带放置与连接为 `platform/ConveyorConnections.java`、`ConveyorRoute.java`，客户端选点／寻路为 `industry/client/ConveyorPlacementClient.java`、`ConveyorPathfinder.java`，扣料与批量建造为 `application/ConveyorConstructionService.java` |
| 副职业、经验与奖励 | `modules/professions/api/ProfessionCatalog.java`、`ProfessionRules.java`、`ProfessionRewards.java`；炼金配方与效果定义为 `AlchemyPotions.java`；道具品质接入在 `bootstrap/ProfessionItemDisplay.java`，统一消费 `economy/api/ItemRarity.java`；本机判定在客户端 `professions/client/ProfessionClient.java`、`AlchemyClient.java`，事实保存与管理奖励在 `application/ProfessionService.java` |
| 库存、容器、接收与事务 | `modules/inventory/api/InventoryOperations.java`、`InventoryEdits.java`、`InventoryPacking.java`、`InventoryIntake.java`；事务在 `application/InventoryTransactions.java` |
| 通货、掉落、出售与分解 | `modules/economy/api/CurrencyRules.java`、`RewardRules.java`、`ChestRewards.java`、`RewardPorts.java`；无人值守世界物品收取／抛出用 `WorldItemTransfers.java`；本机操作在客户端 `economy/client/EconomyActions.java`，事实保存与世界投影在 `application/` |
| 赫拉迪姆方块、工艺精华与固定宝石 | `modules/equipment/api/CraftingEssences.java`、`Gemstones.java`、`EquipmentChanges.java`；资源制作源 `tools/equipment_currency_resources.py`，效果定义见装备规则，通货登记与击败投放仍经 `CurrencyRules` |
| NPC、对白、新手任务、商店与集市投放 | `modules/npc/api/NpcInteractionComponent.java`、`NpcDefinitions.java`、`NpcSpawns.java`、`NpcTutorial.java`；客户端 `npc/client/NpcClient.java`（交互与头顶气泡）、`NpcDialogueScreen.java`、`TutorialPage.java`；商店合同 `modules/economy/api/ShopDefinitions.java`、`ShopMenu.java`；规则 `rules/npcs.json`、`rules/shops.json`，蓝图挂载见野外建筑生成规则 |
| 单抽罐子、奖池与演出 | `modules/economy/api/JarDefinitions.java`、`LootJarItem.java`；配置 `rules/jars.json`，本机决定奖品在客户端 `economy/client/EconomyActions.java`，演出在 `JarClient.java`、`JarScreen.java`，保存与交付在主源码 `application/JarService.java` |
| 队伍与邀请 | `modules/party/api/Team.java`、`PartyInvitation.java` |
| 方舟战争 | `modules/arkguard/api/ArkWar.java`、`ArkRules.java`、`ArkRoster.java`、`ArkTraits.java`；报名与回合在 `application/ArkGuardService.java`、`ArkBattle.java`，单位战斗与投射物命中在 `ArkCombat.java`，提交后的战斗演出在 `ArkEffects.java`，现场展示在 `ArkScene.java`、`ParticipationBoard.java`，客户端交互在 `arkguard/client/ArkGuardClient.java`，血条／技能条及战斗特效在 `ArkVisuals.java`；数值 `rules/ark_war.json`，地图 `map_blueprint/ark_war.json` |
| 页面、主题、输入与通用演出 | `modules/presentation/api/`；分片染色文字 `RichText.java`，客户端 `presentation/api/client/RichTextDrawing.java`；其余绘制及页面合同查 `client/` 和 `api/client/` |
| 连锁采矿 | `modules/mining/api/VeinMining.java`；客户端按键和预览为 `mining/client/VeinMiningClient.java`，数量保存在 `presentation/api/client/ClientSettings.java` |
| 野外建筑、配置地图与出生村庄 | [野外建筑生成规则](创作指南/野外建筑生成.md)；公开入口 `modules/mapgeneration/api/MapGeneration.java`、`MapBlueprint.java`；启动开关在游戏目录 `config/ydcore.toml` |
| 魔力花与魔力草循环 | `modules/industry/api/MagicFlora.java`；植物、液体与展示产线的制作源在 `tools/industrial_technology.py` |
| 液体管道、管网与权杖端口 | `modules/industry/api/FluidPipes.java`；本模块实现 `platform/FluidPipeBlockEntity.java`、`FluidPipeNetwork.java`；模型及共用尺寸源 `tools/industrial_fluid_pipes.py`、`platform/FluidPipeGeometry.java`；贴图源与导出见下方12/14手绘素材入口 |
| 世界微装饰、资源循环与自然生成 | `modules/mapgeneration/api/WorldDecorations.java`；制作源 `tools/build_world_decorations.py`，方法见 [创作指南](创作指南/README.md#世界微装饰) |

## 规则、资源与工具

| 查什么 | 在哪里 |
|---|---|
| 规则整包与发布 | 主源码 `bootstrap/ApplicationRules.java`、`architecture/RuleStore.java` |
| 装备、技能、工业、副本及宝箱参数 | `src/main/resources/data/yumis_dungeon_and_fight/rules/`；装备模板在相邻 `equipment/` |
| 配方、标签、掉落与世界生成 | `src/main/resources/data/yumis_dungeon_and_fight/{recipe,tags,loot_table,worldgen}/` |
| 语言、模型、图标、着色器与动作 | `src/main/resources/assets/yumis_dungeon_and_fight/`，按业务目录查找 |
| 贴图制作、重绘与换色 | [贴图创作](创作指南/贴图创作.md) |
| 哥布林王国藏宝图图标 | 源稿 `tools/goblin_treasure_map.aseprite`，用 Aseprite `-b tools/goblin_treasure_map.aseprite --save-as src/main/resources/assets/yumis_dungeon_and_fight/textures/item/goblin_treasure_map.png` 导出 |
| 管道12/14尺寸手绘素材 | 源稿 `tools/fluid_pipe_materials_12x14.aseprite`、方向标记 `tools/fluid_pipe_ports_14x2.aseprite`；初稿绘制 `tools/draw_fluid_pipe_materials_12x14.lua`；正式导出 `tools/export_fluid_pipe_materials_12x14.lua`；切片布局 `tools/fluid_pipe_materials_12x14_layout.json` |
| 矿石方块换色与自发光 | 可编辑源稿 `tools/industrial_ores.aseprite`，配色与等级 `tools/industrial_ore_definitions.lua`，绘制及导出入口 `tools/draw_industrial_ores.lua`、`tools/export_industrial_ores.lua` |
| 科技方块矩阵外观 | `tools/technology_blocks.aseprite`（五帧，16×16），绘制入口 `tools/draw_technology_blocks.lua`，玻璃／发光边／内核与双层模型导出 `tools/export_technology_blocks.lua`；尺寸与光照在 `modules/industry/platform/TechnologyBlock.java` |
| 信标塔物品图标 | `tools/mana_beacon_items.aseprite`（五帧，32×32），绘制／导出入口 `tools/draw_mana_beacon_items.lua`；`exportOnly=true` 仅导出保存的源稿，物品模型由 `tools/industrial_visual_models.py` 的 `generate_beacon_items()` 生成 |
| 群友发电机与石油工业资源 | 几何、贴图、合成和加工配方的制作源在 `tools/industrial_petrochemistry.py`；运行 `tools/industrial_technology.py --petrochemistry-only` 可单独生成该组资源 |
| 渔获贴图源稿与导出 | `tools/fishing_catches.aseprite`（36 帧，16×16）与 `tools/export_fishing_catches.lua`；`item=<物品ID>` 导出单项，`allTiles=true` 导出全部素材到 `temp/fish-art/tiles/` |
| 工业资源生成源 | `tools/industrial_resources.py`、`industrial_expansion.py`、`industrial_technology.py`；机械几何为 `tools/industrial_arcane_models.py`，贴图为 `industrial_arcane_art.lua`、`industrial_arcane_breathing.lua`；原版／Mekanism 底图的材料换色源为 `tools/draw_industrial_material_bases.lua`，高阶锭细节与动画源为 `tools/draw_industrial_ingot_details.lua`，共用源稿 `tools/industrial_material_bases.aseprite`（每帧16×16）；导体、组件与点火芯源为 `tools/draw_industrial_components.lua`、`tools/industrial_component_bases.aseprite`；燃料棒、胶囊与核素热源源稿为 `tools/nuclear_fuel_rods.aseprite`，魔能水晶物品图标源稿为 `tools/energy_crystals.aseprite`（均为16×16）；其余材料源为 `tools/draw_industrial_materials.lua`、`tools/industrial_materials.aseprite`（32×32，保留探矿器帧）；统一导出为 `tools/export_industrial_materials.lua`，`only=bases` 仅导出基底材料，`only=ingots` 仅导出锭类 |
| 武器模型生成源 | `tools/build_weapon_models.py`、`tools/build_warglaive_model.py`；光剑本机发光材质源 `tools/weapon_glow_resources.py` |
| 构建与依赖 | `build.gradle`、`gradle.properties`、`gradle/local-mods/`；本机入口见 [本机环境](本机环境.md) |
| 正式测试与提交检查 | `src/test/java/`、`tools/check-submission.ps1`；运行和修改须遵守 AGENTS |
| 模块边界检查 | `gradle/architecture.gradle`，属于受保护文件 |
| Mods 同步工具 | `ModSyncTemp/`，独立于游戏运行模块 |
| 物品清单与编辑网站 | `tools/item-catalog/`；客户端采集入口为 `src/client/java/cn/yumis/fight/verification/ItemCatalogExport.java` |
| 既有玩家资料与演出术语 | `playerread/`，按需参考，不作为开发后自动维护项 |
| 临时文件与验证产物 | `temp/`，不提交、不上传 |

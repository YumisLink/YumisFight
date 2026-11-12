# 创作指南

主旨：新增前先查已有架构，优先增加内容登记、配方和参数，复用现有流程。目录根与完整入口见 [项目索引](../README.md)，平衡方法见 [数值评估](数值平衡评估.md)。

## 新增前

1. 找同类内容，确认所属模块、公开接口和可复用设施；在回复中简要说明复用入口及缺少的参数。
2. 先组合已有能力；确有缺口时优先在所属业务模块补充。涉及受保护架构时按 [AGENTS](../../AGENTS.md) 解释必要性并逐批确认 fetch。
3. 定义、预览、实际执行共用参数与时间轴；同时补稳定ID、正式资源及结束／取消／生命周期清理。完成后只回复结果，未完成项放根目录 TODO。

## 已有设施用在哪里

表中路径相对主源码的 `modules/`；客户端使用客户端源码的同名模块。`application`／`platform` 只用于定位本模块实现，其他模块必须经公开 API。

| 设施入口 | 用在哪里／怎样复用 |
|---|---|
| `attributes/api/Stats.java`、`AttributeSources.java` | 角色、装备与技能属性都从正式来源汇总；运算按已有乘区，不在内容里另算一套面板。 |
| `affixes/api/AffixGeneration.java`、`AffixEffects.java`、`AffixResolver.java`；`equipment/api/EquipmentGrowth.java` | 装备／宝石词条的抽取、效果、同步与成长；新定义登记运算和资格，保留当前格式实例。 |
| `skills/api/SkillDefinition.java`、`SkillBalance.java`、`SkillBalanceProfile.java` | 技能定义、等级参数与伤害预算；伤害技能登记权值及命中时间轴，非伤害技能明确登记为非伤害，新职业参考用 `SkillBalanceStyle`。 |
| 客户端 `skills/client/CareerClient.java`、`skills/client/SkillBehaviors.java`；`skills/api/SkillCosts.java`、`SkillTargeting.java`、`SkillDamagePhases.java` | 在所属职业的客户端内容中登记起手、资源支付、冷却、选点和物魔分段，复用本机命中与战斗报告；需要世界实体时通过本模块公开接口请求服务器维护实体。 |
| `combat/api/CombatPorts.java`；`inventory/api/InventoryOperations.java`、`InventoryIntake.java`、`InventoryPacking.java` | 战斗回执、领取、拾取、奖励及工业资产变更复用既有库存／WAL事务；跨模块通过端口提交，失败与重试沿原语义处理。 |
| `combat/api/ElementAccumulation.java`、`Life.java`、`CombatContentTypes.java` | 玩家和敌人共用生命内的元素积累；伤害分量沿正式回执提交。内容通过 `addConversion(stats, damageElement, kind, ratio)` 登记额外积累来源，保留原伤害元素；来源为 −1 无属性、0 光、1 火、2 冰、3 暗，比例用小数。效果期间暂停同种积累，不从描述文字解析转换。控制判断复用 `controlled(entity)`。 |
| `character/api/JobCatalog.java`、`CharacterChanges.java`、`CharacterPersistence.java`、`CareerProfiles.java`；`skills/api/CareerState.java` | 新职业登记资格与内容；成长与学习在本人客户端调用角色／技能规则，服务器用角色端口保存结果，连接／生命／场景内的临时技能状态由技能模块持有。 |
| `dungeons/api/DungeonCatalog.java`、`Target.java`、`EncounterScaling.java`；`content/DungeonLayouts.java`；客户端 `dungeons/client/EncounterClientContacts.java`、`DungeonActions.java` | 新副本复用场次、地图边界／门控、等级难度缩放和清场；个人入场资格、玩家接触和引路石个人奖励由本机判定，AI、敌人和地图留在服务器。 |
| `dungeons/api/ClassicEnemyKind.java`、`ClassicMove.java`；`content/ClassicDungeonRooms.java` 及各副本内容类 | 经典后续副本登记原型、六组编队与首领，精英复用本体；动作稳定ID对应原生模型片段，毫秒时长及接触相位共同驱动动作、预警与本机判定。召唤物／机关在所属副本服务维护，不计入普通编队清场数；中间复活不发布死亡回执，最终死亡沿原奖励入口。 |
| `industry/api/DeviceType.java`、`IndustrialRecipe.java`、`IndustrialTechnology.java`；`platform/IndustrialProcessing.java` | 新设备和工艺复用设备能力、科技目录、批次／产线结算；精度用既有 `EnergyMath`／`Fraction`，资源从正式生成源维护。 |
| `economy/api/CurrencyRules.java`、`RewardRules.java`、`ChestRewards.java`、`RewardPorts.java`；`professions/api/ProfessionRewards.java` | 掉落、出售分解、通货与副职业奖励由本人客户端按原资格计算；消费与确定奖品走既有库存／WAL事务，源快照、操作号和结果一并保存。重试复用已确定结果，不新增第二套余额或发奖模型。 |
| `economy/api/JarDefinitions.java`、`LootJarItem.java` | 单抽罐子登记奖池与权重，物品工厂使用定义ID；开罐与交付沿库存／WAL事务，演出不决定奖品。配置方法见下文。 |
| `npc/api/NpcInteractionComponent.java`、`NpcDefinitions.java`、`NpcSpawns.java`；`economy/api/ShopDefinitions.java`、`ShopMenu.java` | NPC定义登记有序交互动作和日常台词，通过组件挂到生物；动作处理器按类型登记。商品登记单件物品和必须同时满足的费用，预览与本机购买共用定义。地图仅提供完成放置后的世界坐标锚点，调用NPC公开投放入口。 |
| 客户端 `presentation/api/client/WorldQuads.java`、`EffectModels.java`、`EffectParticles.java`、`NearEffectRendering.java` | 技能和副本共用世界绘制、粒子及近身淡化；内容提供形状和时间轴，展示不授予命中、无敌或重力权限。 |
| `mapgeneration/api/MapGeneration.java`、`MapBlueprint.java` | JSON 蓝图与原版 NBT 结构共用道路、其他地面、建筑／设施三类配置；统一规划后放置。管理员用 `/ydnf map plan <蓝图> [x y z]` 预览规划摘要、`/ydnf map generate <蓝图> [x y z]` 生成；控制台必须给坐标。 |

规则整包接主源码 `bootstrap/ApplicationRules.java` 与 `architecture/RuleStore.java` 的既有发布流程；新增内容不另建通用网络、事务或规则运行时。

## 制作前最少确认什么

所有数值注明单位、基础值／成长／范围与来源；百分数、小数倍率和显示文字分清，效果不能靠文案推断。

| 内容 | 必需参数 |
|---|---|
| 技能／职业 | 稳定ID、职业／家族／武器资格、解锁／SP、费用／CD秒、物魔／元素、各段系数／接触时间／目标上限、暴击／附伤／装备触发资格、前后摇／持续时间、射程／范围、权值／成长、资源循环及中断清理；职业另列父职业、转职／觉醒门槛、防护与技能表。 |
| 装备／词条 | ID、部位／品质／等级、基础曲线、词条区间／运算／乘区、生成资格／权重、成长／效果版本／退役、套装、模型图标和投放来源；未定史诗／神话由用户设计。 |
| 敌人／副本 | 等级及既有缩放下的基础值、房间／波次／门控、攻击前摇／接触／后摇、段数／间隔／同目标命中上限、判定形状／范围／移速／CD／元素、控制与取消条件、奖励来源；模型动作与实际接触共用时序。 |
| 设备／配方 | 功能档、科技等级与工艺能力分别确定；输入／输出及批量、配方ticks、单批总魔能、液体mB／流量、产线／容量、制造与升级材料。普通工作台配方还需九宫格图案；维护生成源，不只改生成结果。 |
| 奖励／副职业 | 来源资格、数量／概率权重、经验与等级曲线、操作号和领取上限；消费、发奖、重复请求与失败回滚共用现有事务。 |

资源放既有模块对应目录，入口见项目索引；本指南只补可复用方法，不追加某次实现履历或数值快照。

## 配置地图与结构素材

新增野外建筑、配置地图或出生村庄前，先读 [野外建筑生成规则](野外建筑生成.md)。该文集中说明 `ydcore.toml` 开关、首次出生生成、三类区域配置、NBT 素材、道路高度、三种地基、地形与换材、战利品及验收方法；`starter_village.json` 是正式蓝图示例。

继续复用 `mapgeneration/api/MapGeneration.java` 和 `MapBlueprint.java`；新增地图以配置和结构素材登记，保留用户修改，不在生成器中增加地图坐标或建筑 ID 分支。

## 单抽罐子配置

数据包覆盖 `data/yumis_dungeon_and_fight/rules/jars.json` 后执行服务端 `/reload`。根对象 `jars` 按稳定ID登记定义，每种包含 `name`、`bossDropChance`（0～1）和 `entries`。条目使用有限、非负的 `weight`，每池总权重必须大于0；单次只产出1件。修改单项权重会改变整池归一化概率，不额外抽品质。配置无效时整包保留上次有效规则，已开始的抽取保留原奖与演出快照。

| 条目 `type` | 必需参数与资格 |
|---|---|
| `weapon` | `weapon` 使用现有武器枚举，`rarity` 只允许 `COMMON`、`RARE`、`ARTIFACT`、`LEGENDARY`；统一生成80级，不提供等级字段。 |
| `named` | `template` 使用已设计具名史诗的稳定ID，如 `named/skyspine`；保留模板原等级与效果，不允许神话。 |
| `item` | `item` 使用注册物品ID，如 `minecraft:diamond`；模组普通物品可省略命名空间。需要实例数据的装备和罐子须使用正式工厂，不可用裸注册物品代替。 |

`bossDropChance` 是普通副本、藏宝图、地狱+中有奖励资格的首领击杀独立投放，每位符合既有奖励资格的玩家分别判定，不继承掉落率、难度或引路石数量倍率。使用入口为背包右键，仅副本外且脱战可用；管理命令为 `/ydnf jar give <player> <definition> <count>`，数量1～64。

本人客户端判断开罐资格并一次确定完整奖品，开始事务将扣罐与原奖品一并保存；本机10秒到时请求交付，服务器不再次抽取或审批。断线、死亡、换维度或正常停服交付已保存奖品，崩溃遗留在下次上线交付。背包放不下时使用普通公共掉落语义。演出独立等概率选择陪衬，最终始终停在已保存的原奖品。

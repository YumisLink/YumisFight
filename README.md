# YumisDungeonAndFight

Minecraft **26.2** · NeoForge **26.2.0.93** · Java **25** · 模组 ID `yumis_dungeon_and_fight`。

客户端与专服使用同一个主 JAR，**必须同时安装 Curios API 16.0.0+26.2**。角色、装备、战斗结算与事务逻辑独立编写；六个饰品槽由 Curios 管理。按用户追加要求迁移 YDNF 的视觉表现、地图布局及怪物模型，并适配 NeoForge 26.2。交接包内的装备、技能、菜单和敌人贴图按资源索引复用。新增材料、加工道具和制作器贴图保留 Aseprite 源稿。

## 安装与开始

把 `dist/YumisDungeonAndFight-0.1.0.jar` 和 `curios-neoforge-16.0.0+26.2.jar` 一起放入上述版本的 `mods`，联机双方均需安装。`-sources.jar` 是源码归档，不能放入 `mods`。旧版六个饰品槽的装备在首次进入时迁入 Curios 对应槽位；本工程不提供旧 Fabric 存档或空岛迁移。

- **I**：角色、技能、职业、副职业、队伍、副本、设置、商城、次元背包。商城保持空白占位。
- **G**：打开 Curios 饰品界面；原版 **E** 背包左上角也有 Curios 按钮。六槽为手镯、项链、戒指、辅助装备、魔法石、耳环；后三者分别在 50、60、70 级启用。创造模式从背包标签点击 Curios 按钮。
- 技能页悬浮查看实时伤害、段数及效果；点击已学主动技能行打开8栏选择页，按住 **Ctrl / Alt** 切换，共24栏。选中即替换，Esc取消。原Shift层删除，普通、Ctrl和Alt绑定保留。
- 属性和技能加点时按住 **Shift**，分别最多加5点／升5级；点数不足时尽量加满。
- **C** 梯云纵：向前上方跃起；按住当前跳跃键再按C强力向上跃起，可空中连用。每次消耗20体力，上限100；间隔5游戏刻。消耗20刻后仅落地每刻恢复1点，每次施放刷新200刻免摔伤。登录、重生回满；创造及管理员训练模式免消耗。
- 鬼剑士 20 级可接转职任务，完成后成为剑魂。角色可升到 80 级。
- 副本页开放 **10 级哥布林营地、15 级龙人之塔**。同副本共享当前公开场次，进入已有场次沿用其难度与同步设置。其余副本只显示未来入口。
- 对制作器右键打开制作界面。工作台配方为 `铁 铜 铁 / 铜 工作台 铜 / 铁 铜 铁`，共铁锭 4、铜锭 4、工作台 1。
- 制作器仅制作白装，目标等级不超过角色等级，工匠 0 级可用。区域材料按部位固定消耗；每件成功制作获工匠经验及职业币各 60。
- 把通货拿到鼠标光标上，**右键**真实装备槽加工。支持原版和本模组容器及创造背包；成功只消耗1枚，失败不消耗、不交换物品。持通货悬浮仍显示装备和通货效果，按住Shift查看当前可重掷词条范围。镶嵌选孔页面保持原容器，覆盖损耗旧宝石；套装提取消耗整件来源装备。
- 制作器点击装备名称打开全部15种制作类型的选择页，未解锁项标注等级。选中返回后单独调整等级，再点击制作；Esc返回原制作器。
- 设置页可导入本机 **TTF / OTF** 字体、切换已导入字体和恢复默认 **Inter Frozen Medium＋思源黑体 CN Medium**（Modern UI 同款组合）。菜单、HUD、伤害数字、物品名和模组提示统一使用所选字体。旧内置默认自动迁移，个人导入选择保留；缺字回退内置组合。字体原件被复制到 `config/yumis_dungeon_and_fight/fonts`，之后可移动源文件。

原生攻击、弓弩与三叉戟保留原版命中反馈、盾牌格挡、投射物附魔和碰撞生命周期；实际生命只经模组结算一次。每次有效碰撞分别结算，护盾完全吸收仍有受伤表现。投射物使用发射武器及附魔，其他属性按命中时计算。双端协议为 **3**，更新时客户端与专服须使用匹配主 JAR。

无孔装备隐藏宝石孔区域。打孔石与套装拆解石没有自然掉落；可通过管理员测试命令取得。词条拆卸及宝石产出按方案延期，提供开发样本验证通用镶嵌。史诗、神话整件分解分别给 1 枚升级石，每枚提升 5 级，最高 80；保留特殊效果、套装及宝石。

## 构建

在 PowerShell 中执行：

```powershell
cd C:\GameApp\YumisFight
.\build.ps1 -Tasks clean,build -Offline
```

本机已有依赖缓存；离线构建固定使用官方 26.2 元数据。编译与开发运行使用 `gradle/local-mods/curios-neoforge-16.0.0+26.2.jar`，正式 JAR 不内嵌 Curios。首次在其他电脑构建时去掉 `-Offline`，允许 Gradle 下载官方依赖，设置 Java 25 的 `JAVA_HOME`。脚本优先使用本机 Gradle，其次使用工程 wrapper（9.5.1）。成功后主 JAR、源码 JAR、SHA-256 导出到 `dist`。开发客户端、专服分别使用 `runClient`、`runServer`；运行目录都在 `temp/run`。验证双客户端运行项仅用于本机隔离验收。

## 数据、源码与扩展

- [实现说明](doc/实现说明.md)：目录、数据定义、接口、事务、生命周期。
- [YDNF 迁移对照](doc/YDNF迁移对照.md)：UI、剑气、技能演出、地图和模型的来源与适配。
- [验证记录](doc/验证记录.md)：实际执行项、截图、结果与验证边界。
- [后续内容](doc/TODO.md)：明确延期的内容。
- `src/main/resources/data/yumis_dungeon_and_fight/rules`：区域、材料、配方、词条、装备模板、套装定义。
- `art/new`：新增 Aseprite 源稿；`art/menu`：交接包原菜单源稿；`art/previews`：预览；`art/tools`：新增图形生成脚本。
- `temp`：构建、验证世界、运行日志、临时工具；`dist`：正式交付。

世界的 `yumis_dungeon_and_fight` 目录存放 WAL 与检查点；玩家原版数据存放背包和附件。备份应整体复制存档，包含这两部分。游戏运行时不要单独删除或替换其中一个文件。

## 管理员测试

以下命令需要原版管理员权限：

```text
/ydnf dev level 玩家名 80
/ydnf dev job
/ydnf dev white
/ydnf dev give epic
/ydnf dev give mythic
/ydnf dev give socket_stone
/ydnf dev give set_extraction_stone
/ydnf dev give test_gem
/ydnf dev give spinel
/ydnf dev nocd on
/ydnf dev nocd off
/ydnf profession addxp 玩家名 craftsman 60
/ydnf equipment slots query 玩家名 yumis_dungeon_and_fight:bracelet
/ydnf equipment slots add 玩家名 yumis_dungeon_and_fight:bracelet 1
/ydnf equipment slots remove 玩家名 yumis_dungeon_and_fight:bracelet 1
```

`dev job` 仍要求 20 级；`dev level` 只向上升级；`dev white` 发放当前等级可用部位。训练模式保留开关前 MP 上界，关闭后恢复正常消耗规则。扩槽移除时物品进入等待领取；玩家可执行 `/ydnf equipment claim` 领取。普通副职业奖励给等额职业币，管理员经验奖励不产生职业币。

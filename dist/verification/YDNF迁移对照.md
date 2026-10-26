# YDNF 表现与地图迁移对照

依据用户追加要求：UI、剑气和技能表现参考上一级 `C:\GameApp\YDNF` 还原，地图与怪物模型尽可能迁移。此条优先于此前“仅少量参考”的视觉实现限制。角色成长、装备词条、共享生命、WAL、背包事务和新增玩法继续由新工程实现。

| 参考实现 | 新工程落点 | 适配内容 |
|---|---|---|
| `client/CombatScreen`、`CombatPanels`、Core 的 `UiTheme/UiFrame` | `FightScreen`、`ThemedButton`、`UiTheme` | 原生字号、黑灰半透明卡片、悬停泛白、薄荷选中线、标题/页签/摘要/页脚。保留用户确认的四列 I 菜单及新增入口 |
| `client/CombatHud`、`DamageNumbers` | `Hud`、`ClientState.Pending` | 168 逻辑像素的紧凑技能栏、三行显示、气泡及骑乘行避让、命中世界位置飘字、续段图标与剩余次数 |
| `client/SkillVfx`、`core/SkillVisualEvent` | 同名新包类及 `Effects` 协议适配 | 有厚度的半月网格、32 分之一格像素化顶点、蓄力环、拔刀圆弧、分段斩、剑舞及终结波、音效 |
| `core/StormChoreography` | `rules/StormChoreography` | 上下各 12 剑、种子决定顺序、24 段穿梭、终结斩及相机位置 |
| `client/GhostSwordsmanWeaponVisuals` | 新武器外观池 | 由新物品定义构造，仅渲染，不生成真实可拾取装备 |
| `GoblinRenderer`、`DragonRenderer` | 新包同名渲染器 | 模型几何、UV、角色比例、武器/王冠/翅膀及动画；绑定新 `DungeonEnemy` 及 NeoForge 注册 |
| `CageRenderer` | 同名渲染器与 `CageEntity` | 可见铁笼模型与实际碰撞实体对应 |
| `server/GoblinCamp` | 同名地图类 | 四片森林区域、营地与帐篷、哨塔、路径、门、边界屏障、Boss 祭坛 |
| `server/DragonTower` | 同名地图类 | 四个真实楼层、12 格层高、交替三格宽楼梯、头顶空间、门、柱子、窗与顶层装饰 |

没有引入 Fabric 或外部 YumisCore 运行依赖。渲染改为 NeoForge 26.2 的 `ExtractLevelRenderStateEvent` 提取不可变网格快照，使用 `SubmitCustomGeometryEvent` 提交几何；角色穿梭在原版实体渲染状态提取完成后修改。技能阶段经新协议发送，伤害、MP、CD 不放入视觉事件。

普攻继续保留本次确认的随机斩击平面，伤害、距离和命中几何不受外观随机影响。新技能参数及起手快照保留。地图复用采用新布局版本标记，更新旧开发布局时只清理本模组地下城维度内的固定区域。

迁移源文件及校验值保存在 `doc/YDNF迁移来源.json`，验证归档内也保留副本；交付的源码内同时标明迁移来源。美术贴图仍使用用户提供的对应资源，新增美术保留在 `art`。

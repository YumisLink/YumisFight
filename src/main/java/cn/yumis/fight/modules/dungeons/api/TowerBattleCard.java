package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.ParameterModifier;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerCard.Rarity;
import cn.yumis.fight.modules.equipment.api.EquipmentAttributes;
import cn.yumis.fight.modules.presentation.api.RichText;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static cn.yumis.fight.modules.dungeons.api.TowerBattleCard.Binding.*;
import static cn.yumis.fight.modules.dungeons.api.TowerCard.Rarity.*;

/** Card definitions and parameters for battle effects. */
public enum TowerBattleCard {
    JEWEL("jeweled_gauntlet", "珠光护手", MYTHIC, "命中生命低于 {threshold}% 的敌人时必定暴击，暴伤额外提高等同于暴击率", Map.of("threshold",35)),
    EXECUTE("flying_kick", "飞身踢", MYTHIC, "处决生命低于 {normal}% 的敌人，Boss 阈值为 {boss}%", Map.of("normal",5,"boss",1)),
    ARCHMAGE("archmage", "大法师", MYTHIC, "施放后，使所有技能剩余冷却缩短本次实际冷却的 {reduction}%", Map.of("reduction",10)),
    FUNDAMENTALS("back_to_basics", "回归基本功", MYTHIC, "禁用觉醒技能，伤害总增 {damage}%，获得 {haste} 点冷却加速", Map.of("damage",30,"haste",35)),
    PUNCH("mystic_punch", "秘术冲拳", MYTHIC, "每次普攻使全部技能剩余冷却缩短 {reduction} 秒", Map.of("reduction",.25)),
    MERCY("merciful_strike", "仁慈打击", EPIC, DAMAGE, "{skill} 造成伤害后，下次普攻首个有效命中附加该技能总面板均值 {damage}% 的伤害", Map.of("damage",10)),
    TNT("overextender", "过量延伸者", EPIC, "施放后在 {radius} 格内寻找最近敌人，以 TNT 造成 {damage}% 物理伤害，冷却 {cooldown} 秒", Map.of("radius",16,"damage",1000,"cooldown",14)),
    REFLECT("immovable", "不动如山", EPIC, "受到伤害时返还实际扣血量 {damage}% 的真伤，并回复实际扣血量的 {heal}%", Map.of("damage",1000,"heal",10)),
    NATURE("nature_heals", "自然即是治愈", EPIC, "每秒回复 {recovery}% 最大生命和魔力", Map.of("recovery",1)),
    PORCUPINE("porcupine", "豪猪", EPIC, "受伤时对周围 {radius} 格敌人造成实际扣血量 {damage}% 的伤害，获得 {duration} 秒 {reduction}% 减伤，受伤刷新", Map.of("radius",3,"damage",300,"duration",3,"reduction",65)),
    SOULS("soul_devourer", "吞噬灵魂", EPIC, "持有期间每击败 {kills} 个敌人，增加 {attack} 点最终额外物攻和魔攻", Map.of("kills",10,"attack",1)),
    BANG("bang", "邦！", EPIC, SINGLE, "{skill} 命中后，将实际伤害的 {damage}% 扩散给目标周围 {radius} 格的其他敌人", Map.of("damage",30,"radius",3)),
    COINS("my_coins", "我的硬币！", EPIC, "击败敌人时额外获得 {gold}% 金币", Map.of("gold",300)),
    BRAVADO("bravado", "狂徒豪气", EPIC, "施放后双防提高 {defense}%，最多 {limit} 层，持续 {duration} 秒，获得时刷新", Map.of("defense",10,"limit",10,"duration",5)),
    BRAIN("big_brain", "超强大脑", EPIC, "冷却就绪时获得最大魔力 {shield}% 的护盾，持续 {duration} 秒，冷却 {cooldown} 秒", Map.of("shield",1000,"duration",35,"cooldown",35)),
    DIVINE("divine_intervention", "神圣干预", EPIC, "冷却就绪时获得 {duration} 秒无敌，冷却 {cooldown} 秒", Map.of("duration",5,"cooldown",35)),
    OVERFLOW("overflow", "溢流", EPIC, "技能额外魔力消耗提高 {mana}%，伤害总增（最大魔力 ÷ {divisor} + {base}）%", Map.of("mana",100,"divisor",1000,"base",10)),
    CLEANSE("mind_cleanse", "心灵净化", EPIC, "击败敌人时爆炸，对其周围 {radius} 格其他敌人造成死敌最大生命 {damage}% 的伤害", Map.of("radius",3,"damage",5)),
    HELP("little_help", "小小的额外帮助", EPIC, BUFF, "施放 {skill} 后获得 {speed}% 攻速和 {attack}% 攻击力，持续 {duration} 秒", Map.of("speed",30,"attack",20,"duration",30)),
    HURRY("hurry", "急急小子", EPIC, "每有 {movement}% 装备额外移速，获得 {speed}% 攻速", Map.of("movement",1,"speed",1)),
    KILLING_TIME("killing_time", "杀戮时间到了", EPIC, "施放觉醒后刷新该觉醒技能冷却，卡牌冷却 {cooldown} 秒", Map.of("cooldown",70)),
    THRILL("guilty_pleasure", "罪恶快感", EPIC, "击败敌人获得 {movement}% 移速和 {attack}% 攻击力，每层持续 {duration} 秒", Map.of("movement",100,"attack",40,"duration",3)),
    HASTE("pursuit_of_haste", "急速之追求", EPIC, "每次有效命中获得 {haste} 点冷却加速，最多 {limit} 层，脱战清零", Map.of("haste",1,"limit",100)),
    EVIL("phenomenal_evil", "超凡邪恶", EPIC, "技能造成伤害时，持有期间累计增加 {attack} 点最终额外魔攻，冷却 {cooldown} 秒", Map.of("attack",1,"cooldown",20)),
    SIPHON("soul_siphon", "灵魂虹吸", EPIC, "获得 {crit}% 暴击率，暴击时回复实际伤害的 {heal}%", Map.of("crit",5,"heal",0.01)),
    INFINITY("infinity_edge", "无尽之刃", EPIC, "装备提供的暴击伤害数值提高 {crit}%", Map.of("crit",50)),
    VULNERABLE("vulnerability", "易损", EPIC, "卡牌造成的伤害提高 {damage}%", Map.of("damage",200)),
    TANK("tank_engine", "坦克引擎", EPIC, "持有期间每击败 {kills} 个敌人，最大生命提高 {health}%", Map.of("kills",10,"health",1)),
    JUICE("warlock_juice", "术士果汁盒", EPIC, "施放技能回复 {heal}% 最大生命", Map.of("heal",5)),
    SHRINK("shrink_engine", "缩小引擎", EPIC, "持有期间每击败 {kills} 个敌人，获得 {haste} 点冷却加速", Map.of("kills",100,"haste",1)),
    MYTHIC_GIFT("mythic_upgrade", "质变：神话", EPIC, "获得一张随机可抽取的神话卡", Map.of()),
    FLASH("flashbang", "闪光弹", COMMON, "技能的最后一次位移结束后，在目的地 {radius} 格内造成 {damage}% 物理伤害", Map.of("radius",3,"damage",70)),
    MOUNTAIN("mountain_shield", "山脉护盾", COMMON, "连续脱战 {delay} 秒后获得最大生命 {shield}% 的护盾", Map.of("delay",5,"shield",20)),
    AID("first_aid_kit", "急救用具", COMMON, "本人技能和卡牌施加的治疗提高 {heal}%", Map.of("heal",30)),
    ADAPTIVE("adaptive_guard", "自适应防护", COMMON, DAMAGE, "{skill} 造成伤害时物防提高 {defense}%，持续 {duration} 秒，再次命中刷新", Map.of("defense",20,"duration",8)),
    ADVANCE("time_to_advance", "前进时间到", COMMON, SKILL, "施放 {skill} 时移速提高 {movement}%，持续 {duration} 秒，再次施放刷新", Map.of("movement",80,"duration",5)),
    DOUBLE_GUARD("double_guard", "双重防御", COMMON, DAMAGE, "{skill} 造成伤害时获得等于实际伤害 {shield}% 的护盾，持续 {duration} 秒", Map.of("shield",5,"duration",3)),
    FIRE("twin_flames", "双生火焰", COMMON, "命中施加相当于伤害量 {buildup}% 的火异常积累，已点燃时追加 {damage}% 火属性魔法伤害", Map.of("damage",20,"element",1,"buildup",100)),
    ICE("ice_chill", "冰寒", COMMON, "命中施加相当于伤害量 {buildup}% 的冰异常积累，已冻结时追加 {damage}% 冰属性魔法伤害", Map.of("damage",60,"element",2,"buildup",100)),
    DARK("shadow_dash", "暗影疾奔", COMMON, "命中施加相当于伤害量 {buildup}% 的暗异常积累，已侵蚀时追加 {damage}% 暗属性魔法伤害", Map.of("damage",20,"element",3,"buildup",100)),
    LIGHT("aurora", "极光", COMMON, "命中施加相当于伤害量 {buildup}% 的光异常积累，已感电时追加 {damage}% 光属性魔法伤害", Map.of("damage",10,"element",0,"buildup",100)),
    ESCAPE("escape_plan", "逃跑计划", COMMON, "造成伤害后获得 {movement}% 移速和等于实际伤害 {shield}% 的护盾，持续 {duration} 秒", Map.of("movement",100,"shield",1,"duration",3)),
    HOME("homeguard", "家园卫士", COMMON, "移速提高 {movement}%，首次受伤后持续 {duration} 秒，脱战恢复", Map.of("movement",100,"duration",5)),
    UNSTOPPABLE("ultimate_unstoppable", "终极不可阻挡", COMMON, "技能实际放出时获得 {duration} 秒无敌", Map.of("duration",.35)),
    CRIT_GUARD("critical_guard", "会心防御", COMMON, "以自身暴击率的 {reduction}% 折算减伤", Map.of("reduction",65)),
    SLAP("slap", "扇巴掌", COMMON, "每次有效命中提高 {damage}% 伤害，持续 {duration} 秒，最多 {limit} 层", Map.of("damage",1,"duration",10,"limit",25)),
    SPIN("spin_to_win", "旋转至胜", COMMON, "旋转技能伤害提高 {damage}%，冷却时间总降 {cooldown}%", Map.of("damage",80,"cooldown",50)),
    LEECH("siphon", "虹吸", COMMON, SKILL, "{skill} 命中回复实际伤害的 {heal}%，每次施放上限为基础冷却秒数百分比的最大生命", Map.of("heal",0.01)),
    INFUSE("imbue", "注魔", COMMON, "普攻附带最高攻击属性 {damage}% 的伤害", Map.of("damage",100)),
    // Career Mythic Cards - Witch (魔道学者)
    WITCH_STAR("witch_star_chain", "星弹连环", JobCatalog.WITCH, "改良魔法星弹",
        "{skill}的连锁次数提高 {bounces} 次，且可以弹射到已经弹射过的敌人", Map.of("bounces", 5)),
    WITCH_CLOAK("witch_shadow_veil", "暗影巨幕", JobCatalog.WITCH, "暗影斗篷",
        "{skill}捕获敌人的范围提高 {range}%，无敌持续时间提高 {invuln}%", Map.of("range", 100, "invuln", 100)),
    WITCH_SWAT("witch_mega_swatter", "超级苍蝇拍", JobCatalog.WITCH, "变异苍蝇拍",
        "{skill}的大小提高 {size}%，无论是否命中敌人都会召唤小使魔，使魔数量提高 {count} 个", Map.of("size", 50, "count", 3)),
    WITCH_LAVA("witch_molten_breach", "炽熔蚀抗", JobCatalog.WITCH, "熔岩药瓶",
        "{skill}的范围提高 {range}%，并使受影响的敌人火属性抗性降低 {shred} 点", Map.of("range", 60, "shred", 20)),
    WITCH_ACID("witch_storm_cloud", "雷暴酸云", JobCatalog.WITCH, "魔道酸雨云",
        "{skill}的范围提高 {range}%，闪电数量提高 {bolts} 道", Map.of("range", 80, "bolts", 4)),
    WITCH_ANTIG("witch_gravity_vortex", "引力坍缩", JobCatalog.WITCH, "反重力装置",
        "{skill}不再将小范围敌人击飞，改为在提高 {range}% 的更大范围内聚怪后爆炸", Map.of("range", 80)),
    WITCH_FURNACE("witch_auto_furnace", "自动红炉", JobCatalog.WITCH, "爆炎加热炉",
        "{skill}不再能主动引爆，改为脱手自动执行，喷发轮次增加 {waves} 波且持续时间同步提升", Map.of("waves", 14)),
    WITCH_DRILL("witch_icebreaker_drill", "破冰钻车", JobCatalog.WITCH, "冰霜钻孔车",
        "{skill}累计的冻结异常提高 {buildup}%，命中冻结敌人时会结束冻结并对其造成一次钻孔车爆炸的破冰伤害", Map.of("buildup", 300)),
    WITCH_COLLIDER("witch_auto_collider", "永动电塔", JobCatalog.WITCH, "电鳗碰撞机",
        "{skill}伤害降低 {reduction}%，持续时间提高 {duration}%，改为脱手自动执行", Map.of("reduction", 50, "duration", 300)),
    // Career Mythic Cards - Asura (阿修罗)
    ASURA_WAVE_RING("asura_wave_ring", "十二方波动", JobCatalog.ASURA, "地裂波动剑",
        "{skill}同时向 {angles} 个角度的方向释放", Map.of("angles", 12)),
    ASURA_BRAND_FLOW("asura_brand_flow", "刻印流转", JobCatalog.ASURA, "波动刻印",
        "每枚{skill}提供 {haste}% 冷却恢复速度，魔力回复速度额外提高 {mana}%，普通攻击每 {hits} 段生成 {gain} 枚刻印", Map.of("haste", 20, "mana", 50, "hits", 2, "gain", 1)),
    ASURA_ORB_OVERDRIVE("asura_orb_overdrive", "极速鬼印", JobCatalog.ASURA, "鬼印珠",
        "{skill}伤害提高 {damage}%，命中间隔缩减至 {interval} 秒，只消耗 {cost} 枚刻印即可达到 {effect} 枚刻印的效果", Map.of("damage", 100, "interval", 0.1, "cost", 1, "effect", 5)),
    ASURA_BRAND_CAP("asura_brand_cap", "八印波动", JobCatalog.ASURA, "波动刻印",
        "{skill}数量上限提高至 {cap} 枚，每枚刻印提供 {damage}% 伤害加成", Map.of("cap", 8, "damage", 5)),
    ASURA_CRESCENT_MAX("asura_crescent_max", "瞬发修罗斩", JobCatalog.ASURA, "邪光斩",
        "{skill}无需蓄力即可达到最大形态，大小提高 {size}%，伤害提高 {damage}%", Map.of("size", 70, "damage", 180)),
    ASURA_ICE_BARRAGE("asura_ice_barrage", "极寒连刃", JobCatalog.ASURA, "冰刃·波动剑",
        "{skill}额外增加 {extra} 道冰刃，伤害提高 {damage}%", Map.of("extra", 8, "damage", 80)),
    ASURA_ICE_HOMING("asura_ice_homing", "追魂冰刃", JobCatalog.ASURA, "冰刃·波动剑",
        "{skill}召唤 {count} 道跟随敌人的冰刃，每 {turn} 秒调转方向并刷新攻击计算，持续 {duration} 秒", Map.of("count", 1, "turn", 1, "duration", 7)),
    ASURA_FIRE_TRIPLE("asura_fire_triple", "爆炎三连", JobCatalog.ASURA, "爆炎·波动剑",
        "{skill}形态改为沿前方地面连续爆炸 {blasts} 次推进", Map.of("blasts", 3)),
    ASURA_FIRE_NOVA("asura_fire_nova", "炽炎扩张", JobCatalog.ASURA, "爆炎·波动剑",
        "{skill}的爆炸范围提高 {range}%，伤害提高 {damage}%", Map.of("range", 150, "damage", 80)),
    ASURA_AGNI_DOUBLE("asura_agni_double", "双印明王", JobCatalog.ASURA, "不动明王阵",
        "{skill}每消耗 {cost} 枚刻印提供 {multiplier} 枚刻印的效果", Map.of("cost", 1, "multiplier", 2)),
    // Career Mythic Cards - Swordmaster (剑魂)
    SM_TRIPLE_INFINITE("sm_triple_infinite", "无尽三段", JobCatalog.SWORDMASTER, "三段斩",
        "{skill}的释放次数变为无限，在续段窗口内可无限连斩突进", Map.of()),
    SM_MASTERY_OVERLOAD("sm_mastery_overload", "极意兵刃", JobCatalog.SWORDMASTER, "武器精通",
        "{skill}全面强化：攻击速度提高 {speed}%；光剑普攻变为 {saber} 连击；巨剑普攻攻速提高 {great}%；太刀普攻造成可无限叠加的重度流血（{sec} 秒内受到 {bleed}% 当前生命伤害，对首领仅 1/12）；短剑剑气变为幻影剑舞大小且伤害提高 {short}%；钝器眩晕概率提高至 {stun}%",
        Map.of("speed", 60, "saber", 4, "great", 100, "sec", 3, "bleed", 20, "short", 300, "stun", 50)),
    SM_OVERDRIVE_LIGHT("sm_overdrive_light", "光极兵刃", JobCatalog.SWORDMASTER, "破极兵刃",
        "{skill}生效期间，攻击命中额外附加相当于本次伤害 {light}% 的光属性攻击", Map.of("light", 20)),
    SM_RISING_DRAGON_SLAY("sm_rising_dragon_slay", "破军斩龙", JobCatalog.SWORDMASTER, "破军升龙击",
        "{skill}变为破军斩龙击：高速前冲连斩后纵跃重劈砸地，引发大范围斩龙冲击波，总伤害提高 {bonus}%", Map.of("bonus", 100)),
    SM_FLOWING_STANCE("sm_flowing_stance", "流星三式", JobCatalog.SWORDMASTER, "流星剑术",
        "获得流星三个主动技能：流星·升、流星·跃、流星·刺（无需消耗 SP，等级随角色成长）", Map.of()),
    SM_DRAW_ARC_WAVE("sm_draw_arc_wave", "极意拔刀", JobCatalog.SWORDMASTER, "拔刀斩",
        "{skill}的范围提高 {range}%，并释放向四周扩散至 {distance} 格的弧形扩大剑气，对敌人造成 {damage}% 的拔刀斩伤害",
        Map.of("range", 20, "distance", 20, "damage", 50)),
    SM_DRAW_BARRAGE("sm_draw_barrage", "连闪拔刀", JobCatalog.SWORDMASTER, "拔刀斩",
        "{skill}不再造成弧形伤害，改为朝准心发射 {base} 道幻影剑舞的剑气（光剑、太刀发射 {fast} 道），每道造成 {damage}% 的拔刀斩伤害",
        Map.of("base", 3, "fast", 6, "damage", 50)),
    SM_DRAGON_WHIRLWIND("sm_dragon_whirlwind", "飓风猛龙", JobCatalog.SWORDMASTER, "猛龙断空斩",
        "{skill}的次数提高 {extra} 次，并且使用时发射追踪敌人的旋风，造成 {damage}% 猛龙断空斩伤害",
        Map.of("extra", 5, "damage", 10)),
    SM_DRAGON_SINGLE("sm_dragon_single", "极速单龙", JobCatalog.SWORDMASTER, "猛龙断空斩",
        "{skill}的次数降低至 {slashes} 段，伤害提高 {damage}%，冷却时间降低至原本的 {cooldown}%",
        Map.of("slashes", 1, "damage", 200, "cooldown", 25)),
    SM_DANCE_OMNI("sm_dance_omni", "剑舞随心", JobCatalog.SWORDMASTER, "幻影剑舞",
        "{skill}的斩击次数提高 {extra} 段，并且会随着玩家的面向改变而改变斩击方向",
        Map.of("extra", 24)),
    // Career Mythic Cards - Oracle (代行者)
    ORACLE_HERMES_SURGE("oracle_hermes_surge", "赫尔墨斯盈涌", JobCatalog.ORACLE, "指令之意",
        "{skill}每次执行成功获得 {gain} 层代行-赫尔墨斯；使用非指令技能时消耗 {cost} 层代行-赫尔墨斯，使该技能获得全部指令的伤害加成", Map.of("gain", 3, "cost", 1)),
    ORACLE_WILL_HASTE("oracle_will_haste", "神谕急令", JobCatalog.ORACLE, "指令之意",
        "{skill}的冷却时间受到冷却缩减影响，所有技能的冷却时间额外缩短 {reduction} 秒", Map.of("reduction", 2)),
    ORACLE_CYCLE_RESET("oracle_cycle_reset", "轮回神谕", JobCatalog.ORACLE, "指令之意",
        "五个基础技能释放后使随机一个冷却中的转职小技能冷却结束；若本次执行了{skill}，则下一次指令刷新为该技能", Map.of()),
    ORACLE_REPLICA_UNLEASHED("oracle_replica_unleashed", "无律复现", JobCatalog.ORACLE, "Furioso-Replica",
        "{skill}不需要 {stacks} 层代行-赫尔墨斯即可施放并使用 {stacks} 层效果，但冷却时间增加 {cooldown}%", Map.of("stacks", 9, "cooldown", 30)),
    ORACLE_SHORTSWORD_WAVE("oracle_shortsword_wave", "短剑裁光", JobCatalog.ORACLE, "短剑",
        "使用{skill}施放五个小技能时，附带发射造成 {damage}% 伤害的剑气", Map.of("damage", 20, "range", 10)),
    ORACLE_LIGHTSABER_ECHO("oracle_lightsaber_echo", "光剑残斩", JobCatalog.ORACLE, "光剑",
        "使用{skill}施放五个小技能时，额外斩击一次，造成 {damage}% 伤害", Map.of("damage", 50)),
    ORACLE_BLUNT_QUAKE("oracle_blunt_quake", "钝器震慑", JobCatalog.ORACLE, "钝器",
        "使用{skill}施放五个小技能时，使敌人眩晕 {stun} 秒（对首领为 1/{bossdivisor}，即 {boss} 秒），并引发半径 {radius} 格、造成 {damage}% 伤害的震荡波",
        Map.of("stun", 3, "bossdivisor", 12, "boss", 0.25, "radius", 3, "damage", 25)),
    ORACLE_GREATSWORD_CLEAVE("oracle_greatsword_cleave", "巨剑裂域", JobCatalog.ORACLE, "巨剑",
        "使用{skill}施放五个小技能时，攻击范围扩大 {range}%，并且伤害提高 {damage}%", Map.of("range", 30, "damage", 30)),
    ORACLE_KATANA_FLASH("oracle_katana_flash", "太刀瞬影", JobCatalog.ORACLE, "太刀",
        "使用{skill}施放五个小技能时，释放速度提高 {speed}%，并且伤害提高 {damage}%", Map.of("speed", 50, "damage", 20)),
    // Career Mythic Cards - Summoner (召唤师)
    SUMMONER_HERDER_SQUAD("summoner_herder_squad", "哥布林爆破小队", JobCatalog.SUMMONER, "契约召唤：赫尔德",
        "{skill}同时召唤 {count} 只赫尔德，全部改为仅使用投弹攻击，爆弹爆炸半径提高 {radius}%，伤害提高 {damage}%",
        Map.of("count", 3, "radius", 80, "damage", 30)),
    SUMMONER_HERDER_CANNON("summoner_herder_cannon", "哥布林重炮王", JobCatalog.SUMMONER, "契约召唤：赫尔德",
        "{skill}强化为巨型重炮形态，生命与双防继承提高 {tank}%，取消近战改为每 {interval} 秒轰出半径 {radius} 格的烈焰重炮，伤害提高 {damage}% 并附加 {buildup}% 火焰异常积累",
        Map.of("tank", 100, "interval", 2.5, "radius", 4.5, "damage", 220, "buildup", 150)),
    SUMMONER_SPIRITS_SACRIFICE("summoner_spirits_sacrifice", "四灵献祭", JobCatalog.SUMMONER, "精灵召唤：四灵契约",
        "场上已有四灵时再次施放{skill}，立即引爆所有存活精灵并在原地重召满血新四灵；每只引发半径 {radius} 格对应元素爆炸，造成总面板 {damage}% 伤害并施加 {buildup}% 对应元素异常积累，精灵阵亡时触发 {death}% 威力自爆",
        Map.of("radius", 4.5, "damage", 25, "buildup", 200, "death", 50)),
    SUMMONER_SPIRITS_ASCEND("summoner_spirits_ascend", "元素升格", JobCatalog.SUMMONER, "精灵召唤：四灵契约",
        "{skill}召出的四灵生命与双防继承提高 {tank}%，全员进化为 {range} 格远程范围攻击（半径 {radius} 格），伤害提高 {damage}%",
        Map.of("tank", 100, "range", 14, "radius", 2.5, "damage", 80)),
    SUMMONER_MASTERY_MARK("summoner_mastery_mark", "蚀落印记", JobCatalog.SUMMONER, "召唤兽强化",
        "{skill}附加魔力印记：本人普攻、魔法星弹、龙牙或杰克爆弹命中敌人时标记目标 {duration} 秒；召唤兽对标记目标伤害总增 {damage}%、暴击率提高 {crit}%，且每 {teleport} 秒将超过 {dist} 格的近战召唤兽瞬移至目标身旁并立刻攻击",
        Map.of("duration", 8, "damage", 45, "crit", 25, "teleport", 4, "dist", 6)),
    SUMMONER_MASTERY_PACT("summoner_mastery_pact", "灵魂契约", JobCatalog.SUMMONER, "召唤兽强化",
        "{skill}令所有普通召唤兽持续时间变为无限，攻速额外提高 {speed}%，每次攻击回复自身 {heal}% 最大生命与护盾；玩家受到伤害时，{share}% 伤害由场上存活召唤兽均摊承受",
        Map.of("speed", 50, "heal", 2, "share", 50)),
    SUMMONER_LOUISE_INFERNO("summoner_louise_inferno", "天火路易丝", JobCatalog.SUMMONER, "契约召唤：路易丝",
        "{skill}保留全部原有招式并强化火系：火球改为 {fireballs} 连发齐射；陨石爆炸半径提高 {radius}%、伤害提高 {damage}%，落地留下持续 {duration} 秒的烈焰火海持续灼烧敌人",
        Map.of("fireballs", 3, "radius", 100, "damage", 120, "duration", 4)),
    SUMMONER_LOUISE_FROST("summoner_louise_frost", "冰霜女皇", JobCatalog.SUMMONER, "契约召唤：路易丝",
        "{skill}保留全部原有招式并强化冰系：冰针改为扇形 {needles} 根穿透冰枪且冻结异常积累提高 {buildup}%；寒冰柱连续引爆 {pillars} 次且半径提高 {radius}%，命中冻结或冰缓目标追加 {shatter}% 冰属性碎冰伤害",
        Map.of("needles", 5, "buildup", 300, "pillars", 3, "radius", 100, "shatter", 150)),
    SUMMONER_SANDOR_PALADIN("summoner_sandor_paladin", "誓约骑士王", JobCatalog.SUMMONER, "契约召唤：桑德尔",
        "{skill}常驻半径 {radius} 格守护光环，光环内主人与召唤兽双防提高 {defense}%、受到伤害降低 {reduction}%、每秒回复 {regen}% 最大生命与护盾；施放贯穿灵剑时嘲讽周围 {taunt} 格非首领敌人并使其双防降低 {shred}%，持续 {duration} 秒",
        Map.of("radius", 10, "defense", 60, "reduction", 25, "regen", 3, "taunt", 8, "shred", 20, "duration", 5)),
    SUMMONER_KING_SOVEREIGN("summoner_king_sovereign", "万灵君主", JobCatalog.SUMMONER, "精灵召唤：伊伽贝拉",
        "{skill}在场时，场上每存在 1 只下位精灵，伊伽贝拉伤害总增 {damage}%、行动速度提高 {speed}%；释放落雷或激光时赋予全体在场召唤兽 {duration} 秒「精灵共鸣」（攻速提高 {buffspeed}%，攻击附加 {bonus}% 元素魔法伤害）",
        Map.of("damage", 25, "speed", 15, "duration", 4, "buffspeed", 40, "bonus", 25)),
    SUMMONER_KING_LASER("summoner_king_laser", "极光裁决炮", JobCatalog.SUMMONER, "精灵召唤：伊伽贝拉",
        "{skill}仅释放强化激光（宽 {width} 格、无限穿透、{hits} 段、伤害提高 {damage}%）且激光冷却降低 {cdr}%；在场时再次施放进入 {commandcd} 秒冷却，立即召至召唤师身前、刷新激光冷却并朝准星方向发射一次强化激光",
        Map.of("width", 3.5, "hits", 5, "damage", 100, "cdr", 50, "commandcd", 12)),
    SUMMONER_FRENZY_VOLLEY("summoner_frenzy_volley", "狂乱号令", JobCatalog.SUMMONER, "召唤兽狂化",
        "施放{skill}时，强制场上所有存活召唤兽立即同步释放一次各自的招牌大招",
        Map.of()),
    // Career Mythic Cards - Soul Bender (鬼泣)
    SB_AURA_FIELD("sb_aura_field", "随身鬼域", JobCatalog.SOUL_BENDER, "鬼神之阵",
        "{skill}变为附着于自身的随身光环增益，随玩家移动生效",
        Map.of()),
    SB_INSTANT_SOUL("sb_instant_soul", "瞬发噬灵", JobCatalog.SOUL_BENDER, "鬼斩",
        "{skill}无需蓄力即可直接以满蓄力形态施放，并自动触发噬灵鬼斩",
        Map.of()),
    SB_HELL_SLASH("sb_hell_slash", "鬼斩·炼狱", JobCatalog.SOUL_BENDER, "噬灵鬼斩",
        "触发{skill}时，向前发射射程 {range} 格的穿透鬼神冲击波，造成 {hits} 段暗属性伤害，每段为满蓄力总伤害的 {damage}%",
        Map.of("range", 10, "hits", 3, "damage", 25)),
    SB_GHOST_STEP("sb_ghost_step", "鬼影步", JobCatalog.SOUL_BENDER, "残影之凯贾",
        "{skill}额外提高 {movement}% 移动速度与 {evasion}% 闪避值；成功闪避时获得 {invuln} 秒无敌，并对周围 {radius} 格斩出暗影残影反击（造成 {damage}% 魔攻暗属性伤害，内置冷却 {cooldown} 秒）",
        Map.of("movement", 20, "evasion", 50, "invuln", 0.5, "radius", 4, "damage", 300, "cooldown", 1.5)),
    SB_MOON_WAVE("sb_moon_wave", "圆月剑气", JobCatalog.SOUL_BENDER, "满月斩",
        "{skill}每段斩击额外向前发射射程 {range} 格的穿透暗月剑气，造成该段伤害 {damage}% 的暗属性伤害",
        Map.of("range", 10, "damage", 45)),
    SB_MOON_COMBO("sb_moon_combo", "盈月连斩", JobCatalog.SOUL_BENDER, "满月斩",
        "{skill}可续按段数提高至 {slashes} 段，每段伤害较前一段递增 {ramp}%，第 3 与第 6 段均为大范围满月回旋斩",
        Map.of("slashes", 6, "ramp", 10)),
    SB_TOMB_STORM("sb_tomb_storm", "碑林倾泻", JobCatalog.SOUL_BENDER, "死亡墓碑",
        "{skill}落碑轮次提高至 {slashes} 轮，单轮伤害降低 {reduction}%",
        Map.of("slashes", 20, "reduction", 45)),
    SB_TOMB_DETACH("sb_tomb_detach", "冥碑脱手", JobCatalog.SOUL_BENDER, "死亡墓碑",
        "{skill}冷却时间提高 {cooldown}%，取消站桩引导改为原地脱手执行，伤害提高 {damage}%",
        Map.of("cooldown", 50, "damage", 40)),
    SB_PHANTOM_CHAIN("sb_phantom_chain", "鬼影连闪", JobCatalog.SOUL_BENDER, "鬼影闪",
        "{skill}单段伤害降低 {reduction}%，冷却时间缩短 {cooldown}%，续段窗口内可连续穿闪 {slashes} 次",
        Map.of("reduction", 35, "cooldown", 40, "slashes", 2)),
    SB_PHANTOM_RIFT("sb_phantom_rift", "次元断空", JobCatalog.SOUL_BENDER, "鬼影闪",
        "{skill}穿行距离与路径宽度各提高 {range}%，命中后在目标处留下暗影裂隙，{delay} 秒后爆裂并对周围 {radius} 格敌人造成本次伤害 {damage}% 的暗属性伤害",
        Map.of("range", 30, "delay", 0.6, "radius", 3.5, "damage", 50)),
    SB_KALLA_ETERNAL("sb_kalla_eternal", "永驻冥炎", JobCatalog.SOUL_BENDER, "冥炎之卡洛",
        "{skill}失去再次按键释放终结冥炎剑的能力，附身持续时间延长至 {duration} 秒",
        Map.of("duration", 120)),
    SB_BLACHE_VOID("sb_blache_void", "虚空献祭", JobCatalog.SOUL_BENDER, "第七鬼神：邪神之怖拉修",
        "施放{skill}不再清除场上的鬼神之阵，且场上无阵时默认按自身鬼神之阵当前有效等级计算完整吞阵增伤",
        Map.of()),
    // Career Mythic Cards - Berserker (狂战士)
    BERSERKER_BLOOD_AWAKENING("berserker_blood_awakening", "血气唤醒", JobCatalog.BERSERKER, "血之狂暴",
        "开启{skill}不再降低双防；替换原低血增伤，生命每低于最大生命 {step}%，伤害总增 {damage}%（生命不高于 {floor}% 时上限 {cap}%）且移速提高 {move}%；每次技能消耗生命时，获得等同于本次耗血量 {shield}% 的血气护盾（持续 {duration} 秒，可累加至最大生命 {shieldcap}%）",
        Map.of("step", 1, "damage", 0.8, "floor", 30, "cap", 56, "move", 0.5, "shield", 400, "duration", 6, "shieldcap", 100)),
    BERSERKER_CRASH_BLOOD("berserker_crash_blood", "血气崩山", JobCatalog.BERSERKER, "崩山击",
        "{skill}的冷却时间降低 {cooldown}%，范围提高 {range}%，伤害提高 {damage}%，施放时改为消耗 {hpcost}% 最大生命",
        Map.of("cooldown", 30, "range", 50, "damage", 60, "hpcost", 4)),
    BERSERKER_HAND_SIPHON("berserker_hand_siphon", "嗜血抓魂", JobCatalog.BERSERKER, "嗜魂之手",
        "{skill}的范围提高 {range}%，冷却时间降低 {cooldown}%；命中后获得持续 {duration} 秒的「嗜血之力」（力量提高 {strength}%、伤害提高 {damage}%），并额外获得最大生命 {shield}% 的血气护盾（持续 {shieldsec} 秒）",
        Map.of("range", 50, "cooldown", 25, "duration", 15, "strength", 35, "damage", 25, "shield", 15, "shieldsec", 8)),
    BERSERKER_HAND_CLAW("berserker_hand_claw", "血魔巨爪", JobCatalog.BERSERKER, "嗜魂之手",
        "{skill}改为脱手向前 {range} 格唤出巨型血魔之爪，将沿途宽 {width} 格内的敌人聚拢至终点捏爆，伤害提高 {damage}%；命中生命低于 {threshold}% 的敌人时，本次伤害额外提高 {execute}% 并返还 {refund}% 冷却时间",
        Map.of("range", 10, "width", 5, "damage", 120, "threshold", 50, "execute", 50, "refund", 50)),
    BERSERKER_RAGE_ENDLESS("berserker_rage_endless", "无尽血怒", JobCatalog.BERSERKER, "怒气爆发",
        "{skill}的范围提高 {range}%，喷发段数增加 {extra} 段，单段伤害降低 {reduction}%",
        Map.of("range", 60, "extra", 5, "reduction", 20)),
    BERSERKER_BLADE_THROW("berserker_blade_throw", "飞血巨刃", JobCatalog.BERSERKER, "血气之刃",
        "{skill}改为脱手向前投掷飞行 {range} 格的巨型血剑，穿透沿途敌人并在首个目标或终点必定触发爆炸；爆炸范围提高 {radius}%，爆炸伤害提高 {damage}%",
        Map.of("range", 12, "radius", 50, "damage", 40)),
    BERSERKER_BLADE_ASSAULT("berserker_blade_assault", "血刃突袭", JobCatalog.BERSERKER, "血气之刃",
        "{skill}附带向前 {dash} 格的高速无敌突进穿刺，命中后连续引发 {blasts} 次血气爆炸（每次造成原爆炸 {ratio}% 伤害），冷却时间降低 {cooldown}%",
        Map.of("dash", 8, "blasts", 2, "ratio", 75, "cooldown", 20)),
    BERSERKER_ERUPTION_QUAKE("berserker_eruption_quake", "裂地血崩", JobCatalog.BERSERKER, "崩山裂地斩",
        "{skill}的范围提高 {range}%，伤害提高 {damage}%",
        Map.of("range", 60, "damage", 50)),
    BERSERKER_ERUPTION_PILLARS("berserker_eruption_pillars", "集束血柱", JobCatalog.BERSERKER, "崩山裂地斩",
        "{skill}的血柱喷发段数增加 {extra} 段，范围缩小 {shrink}%",
        Map.of("extra", 6, "shrink", 30)),
    // Career Mythic Cards - Battle Mage (战斗法师)
    BM_GROOVE_UNBOUND("bm_groove_unbound", "斗神无双", JobCatalog.BATTLE_MAGE, "斗神意志",
        "{skill}连击上限提高至 {cap} 阶，各阶连击阈值降低 {threshold}%，每阶伤害与暴击加成提高 {bonus}%，连击保持时间延长至 {hold} 秒，每次命中额外计 {hits} 连击",
        Map.of("cap", 12, "threshold", 50, "bonus", 100, "hold", 20, "hits", 2)),
    BM_NIU_MASTERY("bm_niu_mastery", "尼巫极意", JobCatalog.BATTLE_MAGE, "斗神意志",
        "{skill}武器精通全面强化：矛物攻加成翻倍、体术范围额外提高 {spearrange}%、攻速额外提高 {spearspeed}%；棍棒双攻与冷却效率加成翻倍、自动炫纹间隔缩短 {clubcd}%、所有近战普攻命中 {basicchance}% 生成炫纹；法杖与魔杖魔攻加成翻倍、炫纹系冷却额外缩短 {staffcd}%、每次消耗炫纹回复 {manaregen}% 最大魔力",
        Map.of("spearrange", 35, "spearspeed", 30, "clubcd", 50, "basicchance", 100, "staffcd", 30, "manaregen", 3)),
    BM_FANG_TWIN_SHRED("bm_fang_twin_shred", "破甲双牙", JobCatalog.BATTLE_MAGE, "龙牙",
        "{skill}切换为破甲双牙形态：化作高速 {hits} 连突刺且每段伤害不衰减，每段命中必产 {chasers} 颗炫纹，冷却时间缩短 {cdr}%；命中使敌人双防降低 {shred}%，持续 {duration} 秒",
        Map.of("hits", 2, "chasers", 1, "cdr", 40, "shred", 20, "duration", 6)),
    BM_FANG_WAVE("bm_fang_wave", "炫纹牙刃", JobCatalog.BATTLE_MAGE, "龙牙",
        "{skill}切换为炫纹牙刃形态：向前发射延伸至 {range} 格、宽度提高 {width}% 的穿透炫纹气刃，伤害提高 {damage}%；每穿透 {per} 名敌人额外生成 {chasers} 颗炫纹，经{skill}柔化释放的下个技能伤害总增 {cancel}%",
        Map.of("range", 12, "width", 100, "damage", 80, "per", 1, "chasers", 1, "cancel", 30)),
    BM_CHASER_OVERFLOW("bm_chaser_overflow", "溢流炫纹", JobCatalog.BATTLE_MAGE, "炫纹发射",
        "{skill}的爆炸范围提高 {radius}%，五色炫纹独立增益效果提高 {buff}%、持续时间提高 {duration}%；炫纹队列达到上限后，新生成的溢出炫纹自动零硬直追踪发射且不消耗魔力、不受发射冷却限制",
        Map.of("radius", 35, "buff", 50, "duration", 100)),
    BM_SWING_EMPRESS("bm_swing_empress", "闪击碎霸·终极女皇", JobCatalog.BATTLE_MAGE, "碎霸",
        "{skill}切换为闪击碎霸·终极女皇形态：起手前冲 {dash} 格并以 {arc} 度全周、半径提高至 {radius} 格斩裂敌人，物理伤害提高 {damage}%，命中生成 {chasers} 颗炫纹",
        Map.of("dash", 5, "arc", 360, "radius", 7.5, "damage", 150, "chasers", 3)),
    BM_SWING_ARCANUM("bm_swing_arcanum", "万象魔刃", JobCatalog.BATTLE_MAGE, "碎霸",
        "{skill}挥扫范围提高 {range}%，同时最多消耗 {max} 颗炫纹，每消耗 1 颗朝前方斩出 1 道推进 {distance} 格的同属性魔法月牙剑气（每道造成等同于{skill}基准倍率 {ratio}% 的魔法伤害并触发对应炫纹增益；队列为空时仍斩出 1 道无属性基础魔刃）",
        Map.of("range", 40, "max", 3, "distance", 14, "ratio", 80)),
    BM_FUSION_INSTANT("bm_fusion_instant", "炫纹簇·瞬发核爆", JobCatalog.BATTLE_MAGE, "炫纹融合",
        "{skill}切换为炫纹簇形态：改为 0 硬直脱手释放，在 {range} 格内锁定敌人脚下瞬间凝聚引爆，范围扩大 {radius}%，伤害提高 {damage}%，队列为空时亦可满威力触发",
        Map.of("range", 16, "radius", 60, "damage", 50)),
    BM_FUSION_OVERLOAD("bm_fusion_overload", "双生共鸣·元素超载", JobCatalog.BATTLE_MAGE, "炫纹融合",
        "{skill}授予的融合共鸣持续时间延长至 {duration} 秒，提供的物攻、魔攻与暴击加成提高 {bonus}%；共鸣持续期间，所有炫纹与炫纹系技能自动附带全四元素（光、火、冰、暗）",
        Map.of("duration", 30, "bonus", 150)),
    BM_PRESS_COLLAPSE("bm_press_collapse", "引力坍缩·超压", JobCatalog.BATTLE_MAGE, "炫纹强压",
        "{skill}引爆前先将半径 {pull} 格内的普通敌人强力吸附至阵心，震地控制时间延长至 {control} 秒；每颗炫纹提供的额外伤害与范围加成提高 {bonus}%",
        Map.of("pull", 9, "control", 1.5, "bonus", 100)),
    BM_PUNTO_ENDLESS("bm_punto_endless", "无尽雷连", JobCatalog.BATTLE_MAGE, "流星闪影击",
        "{skill}的连刺段数扩大至 {slashes} 段且持续时间同步提升，伤害降低 {reduction}%，可持续连刺至打满或主动取消结束",
        Map.of("slashes", 180, "reduction", 50)),
    BM_PUNTO_CRESCENDO("bm_punto_crescendo", "千击终响", JobCatalog.BATTLE_MAGE, "流星闪影击",
        "{skill}的终结横扫会根据前置连刺的命中次数提高伤害，每次命中使终结一击伤害提高 {perhit}%",
        Map.of("perhit", 1)),
    BM_STRIKE_TWIN("bm_strike_twin", "双星折返", JobCatalog.BATTLE_MAGE, "强袭流星打",
        "{skill}改为往返 {dashes} 次穿刺与终点流星爆炸（无伤害衰减，两次均触发路径拖拽、爆炸与产球），冷却时间增加 {cooldown}%",
        Map.of("dashes", 2, "cooldown", 20)),
    BM_SPEAR_CLEAVE("bm_spear_cleave", "煌龙天临·纯物龙劈", JobCatalog.BATTLE_MAGE, "煌龙偃月",
        "{skill}切换为煌龙天临形态：拥有 {charges} 层充能，取消站桩与魔法龙球，跃起瞬发纯物理煌龙巨刃重劈（原魔法段全额并入物理重劈），终爆范围扩大 {radius}%，总伤害提高 {damage}%（与煌龙乱舞·脱手龙阵同局互斥）",
        Map.of("charges", 2, "radius", 35, "damage", 30)),
    BM_SPEAR_DETACH("bm_spear_detach", "煌龙乱舞·脱手龙阵", JobCatalog.BATTLE_MAGE, "煌龙偃月",
        "{skill}切换为煌龙乱舞形态：突刺后煌龙虚影原地驻留，本体 0.4 秒后完全脱手自由行动；龙阵倾泻 {balls} 枚全倍率全四元素龙之炫纹并触发完整煌龙终爆，爆炸范围扩大 {radius}%（与煌龙天临·纯物龙劈同局互斥）",
        Map.of("balls", 16, "radius", 30)),
    // Career Mythic Cards - Elementalist (元素师)
    ELEM_BURN_FOCUS("elem_burn_focus", "元素集中", JobCatalog.ELEMENTALIST, "圣灵符文",
        "使用对应元素技能命中敌人时，使敌人的该属性抗性降低 {shred} 点，持续 {duration} 秒",
        Map.of("shred", 36, "duration", 12)),
    ELEM_BURN_PRISM("elem_burn_prism", "圣灵万象", JobCatalog.ELEMENTALIST, "圣灵符文",
        "你的所有技能变为四元素属性，施放任意元素技能同时点亮全部 4 盏符文灯，并且特效进化为圣灵色",
        Map.of()),
    ELEM_JACK_SPLIT("elem_jack_split", "分裂南瓜", JobCatalog.ELEMENTALIST, "杰克爆弹",
        "{skill}爆炸后会向周围分裂出 {count} 颗小型南瓜再次爆炸，每颗造成 {damage}% 伤害",
        Map.of("count", 4, "damage", 35)),
    ELEM_JACK_TRIPLE("elem_jack_triple", "三叉追踪南瓜", JobCatalog.ELEMENTALIST, "杰克爆弹",
        "{skill}改为沿前方三叉方向同时发射 {count} 颗附带追踪效果的杰克南瓜，每颗造成 {damage}% 伤害",
        Map.of("count", 3, "damage", 75)),
    ELEM_ICE_WALL_WAVE("elem_ice_wall_wave", "极寒推进", JobCatalog.ELEMENTALIST, "冰墙",
        "{skill}切换为极寒推进形态：化为宽 {width} 格弧形冰浪向前推进 {range} 格，沿途推怪并合并结算碎冰爆发，伤害提高 {damage}%",
        Map.of("width", 6, "range", 14, "damage", 80)),
    ELEM_ICE_WALL_MOBILE("elem_ice_wall_mobile", "随身冰壁", JobCatalog.ELEMENTALIST, "冰墙",
        "{skill}不再固定原地，改为跟随自身移动（极寒推进形态下改为连续推出 {waves} 道冰浪）；范围提高 {range}%，伤害提高 {damage}%，冻结异常积累提高 {buildup}%",
        Map.of("waves", 2, "range", 50, "damage", 80, "buildup", 200)),
    ELEM_VORTEX_RAILGUN("elem_vortex_railgun", "超导电磁炮", JobCatalog.ELEMENTALIST, "雷旋",
        "{skill}切换为超导电磁炮形态：沿准星发射射程 {range} 格的高速贯通电磁炮，终点爆破范围扩大 {radius}%，伤害提高 {damage}%",
        Map.of("range", 20, "radius", 50, "damage", 80)),
    ELEM_VORTEX_CHAIN("elem_vortex_chain", "连锁高压弧", JobCatalog.ELEMENTALIST, "雷旋",
        "{skill}的感电异常积累提高 {buildup}%，冷却时间总降 {cdr}%；每次命中在目标周围 {range} 格内最多 {targets} 名敌人之间弹射高压电弧，每道造成 {damage}% 伤害",
        Map.of("buildup", 200, "cdr", 30, "range", 5, "targets", 3, "damage", 50)),
    ELEM_VOID_CRYSTAL("elem_void_crystal", "圣灵水晶·排斥坍缩", JobCatalog.ELEMENTALIST, "虚无之球",
        "{skill}切换为圣灵水晶形态：在前方定点凝结巨型排斥水晶且无法手动提前引爆，持续结束时自动触发坍缩核爆，核爆范围提高 {radius}%，总伤害提高 {damage}%",
        Map.of("radius", 60, "damage", 80)),
    ELEM_VOID_MULTICHARGE("elem_void_multicharge", "多星储能", JobCatalog.ELEMENTALIST, "虚无之球",
        "{skill}获得 {charges} 层充能，无需蓄力即可直接按满蓄力效果释放，球体大小与持续时间提高 {bonus}%",
        Map.of("charges", 3, "bonus", 50)),
    ELEM_FLAME_CHAIN("elem_flame_chain", "熔岩连环阵", JobCatalog.ELEMENTALIST, "烈焰冲击",
        "{skill}切换为熔岩连环阵形态：沿准星方向依次向前喷发 {count} 根完整伤害的熔岩火柱，每根火柱范围提高 {radius}%",
        Map.of("count", 5, "radius", 30)),
    ELEM_FLAME_VOLCANO("elem_flame_volcano", "虚无火山", JobCatalog.ELEMENTALIST, "烈焰冲击",
        "{skill}首根火柱喷发后在落点生成持续 {duration} 秒的暗火虚无火山口，每 {interval} 秒喷射火暗双属性熔岩爆弹（造成 {damage}% 伤害）并微弱吸怪；后续连环火柱不再重复生成",
        Map.of("duration", 5, "interval", 0.4, "damage", 35)),
    ELEM_BUSTER_COLOSSUS("elem_buster_colossus", "巨星坠落", JobCatalog.ELEMENTALIST, "杰克降临",
        "{skill}切换为巨星坠落形态：大南瓜体积与爆炸半径扩大 {radius}%，外圈冲击波并入单次超巨型爆炸，总伤害提高 {damage}%",
        Map.of("radius", 80, "damage", 100)),
    ELEM_BUSTER_TRIPLE("elem_buster_triple", "连环杰克轰炸", JobCatalog.ELEMENTALIST, "杰克降临",
        "{skill}改为相隔 {gap} 秒连续砸落 {count} 颗大南瓜（每颗造成 {damage}% 伤害），并在落点留下持续 {ember} 秒的熔岩火海持续灼烧敌人",
        Map.of("gap", 0.25, "count", 3, "damage", 75, "ember", 4)),
    ELEM_ARCTIC_THRONE("elem_arctic_throne", "冰封王座·瞬碎", JobCatalog.ELEMENTALIST, "极冰盛宴",
        "{skill}切换为冰封王座形态：在 {duration} 秒内拔起单根擎天巨型冰晶尖碑瞬间贯穿并碎裂，合并全部伤害且总伤害提高 {damage}%，冰冻时间延长至 {freeze} 秒",
        Map.of("duration", 0.45, "damage", 100, "freeze", 3)),
    ELEM_ARCTIC_VORTEX("elem_arctic_vortex", "极寒暴风眼", JobCatalog.ELEMENTALIST, "极冰盛宴",
        "{skill}的范围提高 {radius}%，中心生成极寒旋涡强力吸附 {pull} 格内敌人（冰封王座形态在尖碑贯穿时瞬时聚怪）；冻结异常积累提高 {buildup}%，终结冰爆命中冻结敌人时额外造成 {shatter}% 破冰伤害",
        Map.of("radius", 50, "pull", 10, "buildup", 300, "shatter", 100)),
    ELEM_BH_SINGULARITY("elem_bh_singularity", "中央宇宙·强引奇点", JobCatalog.ELEMENTALIST, "湮灭黑洞",
        "{skill}切换为中央宇宙形态：持续时间浓缩至 {duration} 秒，聚怪吸附力度提高 {pull}%，总伤害提高 {damage}%；坍缩后在原地残留 {sub} 秒强引力子黑洞继续聚怪",
        Map.of("duration", 1.5, "pull", 200, "damage", 80, "sub", 3)),
    ELEM_BH_RESONANCE("elem_bh_resonance", "奇点共振", JobCatalog.ELEMENTALIST, "湮灭黑洞",
        "{skill}的吸附与爆炸范围提高 {radius}%；黑洞（含残余引力子黑洞）存在期间，每释放 1 个其他元素技能都会使黑洞立刻触发一次 {damage}% 坍缩终爆伤害的共振冲击",
        Map.of("radius", 60, "damage", 40)),
    ELEM_STRIKE_CLOUD("elem_strike_cloud", "怒雷巡弋·脱手雷云", JobCatalog.ELEMENTALIST, "天雷",
        "{skill}切换为怒雷巡弋形态：免除站桩引导，召唤持续 {duration} 秒的自动索敌雷暴云追击敌人劈落 {bolts} 道天雷，每道天雷伤害提高 {damage}%",
        Map.of("duration", 5, "bolts", 9, "damage", 30));

    enum Binding { NONE, SKILL, DAMAGE, SINGLE, BUFF }

    public record SkillChoice(String id, String title, boolean damage, boolean singleTarget, boolean timedBuff) {}

    private static final Pattern PARAMETER = Pattern.compile("\\{([a-z]+)}(?:%| 秒| 格| 点| 层| 次| 个| 只| 根| 道| 波| 枚| 段| 轮| 倍| 连击| 阶| 颗| 度| 名)?");
    private final String id, title, description, job, fixedSkill;
    private final Rarity rarity;
    private final Binding binding;
    private final Map<String, ? extends Number> values;

    TowerBattleCard(String id, String title, Rarity rarity, String description, Map<String, ? extends Number> values) {
        this(id, title, rarity, NONE, "", "", description, values);
    }

    TowerBattleCard(String id, String title, Rarity rarity, Binding binding, String description, Map<String, ? extends Number> values) {
        this(id, title, rarity, binding, "", "", description, values);
    }

    TowerBattleCard(String id, String title, String job, String fixedSkill, String description, Map<String, ? extends Number> values) {
        this(id, title, MYTHIC, NONE, job, fixedSkill, description, values);
    }

    TowerBattleCard(String id, String title, Rarity rarity, Binding binding, String job, String fixedSkill, String description, Map<String, ? extends Number> values) {
        this.id = id;
        this.title = title;
        this.rarity = rarity;
        this.binding = binding;
        this.job = job;
        this.fixedSkill = fixedSkill;
        this.description = description;
        this.values = values;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String job() { return job; }
    public boolean careerMythic() { return rarity == MYTHIC && !job.isEmpty(); }
    public double value(String parameter) { return values.get(parameter).doubleValue(); }
    public double ratio(String parameter) { return value(parameter) / 100; }
    public int count(String parameter) { return values.get(parameter).intValue(); }
    public long millis(String parameter) { return Math.round(value(parameter) * 1000); }
    public boolean held(Stats stats) { return stats != null && stats.modifiers.getOrDefault("towerCard/" + id, 0d) > 0; }

    /** Card IDs that become mutually exclusive with {@code cardId} within the same climb / career deck. */
    public static java.util.Set<String> exclusionsFor(String cardId) {
        if (BM_SPEAR_CLEAVE.id.equals(cardId)) return java.util.Set.of(BM_SPEAR_DETACH.id);
        if (BM_SPEAR_DETACH.id.equals(cardId)) return java.util.Set.of(BM_SPEAR_CLEAVE.id);
        return java.util.Set.of();
    }

    private void param(Stats stats, String skill, String parameter, double additive, double multiplier) {
        String source = "towerCard/" + id;
        if (stats.parameters.stream().noneMatch(p -> p.source().equals(source) && p.skill().equals(skill) && p.parameter().equals(parameter))) {
            stats.parameters.add(new ParameterModifier(source, skill, parameter, additive, multiplier));
        }
    }

    private void apply(Stats stats) {
        stats.modifiers.put("towerCard/" + id, 1d);
        switch (this) {
            case FUNDAMENTALS -> {
                stats.modifiers.merge(TowerCardEffects.ALL_DAMAGE, 1 + ratio("damage"), (a,b) -> a*b);
                stats.mut(Stat.COOLDOWN_RECOVERY, ratio("haste"));
            }
            case OVERFLOW -> stats.modifier(Stats.SKILL_EXTRA_MANA_COST).mut += ratio("mana");
            case SIPHON -> stats.modifiers.merge("crit", ratio("crit"), Double::sum);
            case HURRY -> stats.extra(Stat.ATTACK_SPEED, EquipmentAttributes.extraMovement(stats) * 100 * value("speed") / value("movement"));
            case INFINITY -> stats.modifiers.merge("critDamage", EquipmentAttributes.extraCriticalDamage(stats) * ratio("crit"), Double::sum);
            case WITCH_STAR -> param(stats, "yumis_dungeon_and_fight:magic_star", "witchBounceCount", value("bounces"), 1);
            case WITCH_CLOAK -> {
                param(stats, "yumis_dungeon_and_fight:shadow_cloak", "pullRadius", 0, 1 + ratio("range"));
                param(stats, "yumis_dungeon_and_fight:shadow_cloak", "invulnerableMillis", 0, 1 + ratio("invuln"));
            }
            case WITCH_SWAT -> {
                param(stats, "yumis_dungeon_and_fight:giant_flyswatter", "radius", 0, 1 + ratio("size"));
                param(stats, "yumis_dungeon_and_fight:giant_flyswatter", "centerOffset", 0, 1.35);
                param(stats, "yumis_dungeon_and_fight:giant_flyswatter", "summonBaseCount", value("count"), 1);
            }
            case WITCH_LAVA -> {
                double scale = 1 + ratio("range");
                param(stats, "yumis_dungeon_and_fight:lava_potion", "radius", 0, scale);
                param(stats, "yumis_dungeon_and_fight:lava_potion", "geyserRadius", 0, scale);
            }
            case WITCH_ACID -> {
                double scale = 1 + ratio("range");
                param(stats, "yumis_dungeon_and_fight:acid_rain", "radius", 0, scale);
                param(stats, "yumis_dungeon_and_fight:acid_rain", "boltRadius", 0, scale);
                param(stats, "yumis_dungeon_and_fight:acid_rain", "boltCount", value("bolts"), 1);
            }
            case WITCH_ANTIG -> param(stats, "yumis_dungeon_and_fight:anti_gravity", "radius", 0, 1 + ratio("range"));
            case WITCH_FURNACE -> {
                double waves = value("waves");
                param(stats, "yumis_dungeon_and_fight:raging_flame_furnace", "slashes", waves, 1);
                param(stats, "yumis_dungeon_and_fight:raging_flame_furnace", "duration", waves * 0.20, 1);
            }
            case WITCH_COLLIDER -> {
                double damageFactor = 1 - ratio("reduction");
                double durationFactor = 1 + ratio("duration");
                param(stats, "yumis_dungeon_and_fight:florae_collider", "damage", 0, damageFactor);
                param(stats, "yumis_dungeon_and_fight:florae_collider", "finisher", 0, damageFactor);
                param(stats, "yumis_dungeon_and_fight:florae_collider", "duration", 0, durationFactor);
                param(stats, "yumis_dungeon_and_fight:florae_collider", "slashes", 0, durationFactor);
            }
            case ASURA_ORB_OVERDRIVE -> {
                double factor = 1 + ratio("damage");
                param(stats, "yumis_dungeon_and_fight:ghost_orb", "damage", 0, factor);
                param(stats, "yumis_dungeon_and_fight:ghost_orb", "perBrandDamage", 0, factor);
            }
            case ASURA_BRAND_CAP -> param(stats, "yumis_dungeon_and_fight:wave_brands", "brandCap", value("cap") - 5, 1);
            case ASURA_CRESCENT_MAX -> param(stats, "yumis_dungeon_and_fight:spirit_crescent", "damage", 0, 1 + ratio("damage"));
            case ASURA_ICE_BARRAGE -> {
                double extra = value("extra");
                param(stats, "yumis_dungeon_and_fight:ice_wave", "slashes", extra, 1);
                param(stats, "yumis_dungeon_and_fight:ice_wave", "duration", extra * 0.2, 1);
                param(stats, "yumis_dungeon_and_fight:ice_wave", "damage", 0, 1 + ratio("damage"));
            }
            case ASURA_FIRE_TRIPLE -> param(stats, "yumis_dungeon_and_fight:fire_wave", "slashes", value("blasts") - 1, 1);
            case ASURA_FIRE_NOVA -> {
                param(stats, "yumis_dungeon_and_fight:fire_wave", "blastRadius", 0, 1 + ratio("range"));
                param(stats, "yumis_dungeon_and_fight:fire_wave", "damage", 0, 1 + ratio("damage"));
            }
            case SM_MASTERY_OVERLOAD -> stats.extra(Stat.ATTACK_SPEED, value("speed"));
            case SM_DRAW_ARC_WAVE -> param(stats, "yumis_dungeon_and_fight:draw", "range", 0, 1 + ratio("range"));
            case SM_DRAGON_WHIRLWIND -> param(stats, "yumis_dungeon_and_fight:dragon", "slashes", value("extra"), 1);
            case SM_DRAGON_SINGLE -> {
                param(stats, "yumis_dungeon_and_fight:dragon", "damage", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:dragon", "cooldownMultiplier", 0, ratio("cooldown"));
            }
            case SM_DANCE_OMNI -> param(stats, "yumis_dungeon_and_fight:dance", "slashes", value("extra"), 1);
            case ORACLE_REPLICA_UNLEASHED -> param(stats, "yumis_dungeon_and_fight:furioso_replica", "cooldownMultiplier", 0, 1 + ratio("cooldown"));
            case SB_MOON_COMBO -> param(stats, "yumis_dungeon_and_fight:moonlight_slash", "slashes", value("slashes") - 3, 1);
            case SB_TOMB_STORM -> {
                double rounds = value("slashes");
                param(stats, "yumis_dungeon_and_fight:tombstone", "slashes", rounds - 10, 1);
                param(stats, "yumis_dungeon_and_fight:tombstone", "interval", 0, 10.0 / rounds);
                param(stats, "yumis_dungeon_and_fight:tombstone", "damage", 0, 1 - ratio("reduction"));
            }
            case SB_TOMB_DETACH -> {
                param(stats, "yumis_dungeon_and_fight:tombstone", "cooldownMultiplier", 0, 1 + ratio("cooldown"));
                param(stats, "yumis_dungeon_and_fight:tombstone", "damage", 0, 1 + ratio("damage"));
            }
            case SB_PHANTOM_CHAIN -> {
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "slashes", value("slashes") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "damage", 0, 1 - ratio("reduction"));
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "cooldownMultiplier", 0, 1 - ratio("cooldown"));
            }
            case SB_PHANTOM_RIFT -> {
                double scale = 1 + ratio("range");
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "dashDistance", 0, scale);
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "range", 0, scale);
                param(stats, "yumis_dungeon_and_fight:phantom_slash", "width", 0, scale);
            }
            case SB_KALLA_ETERNAL -> param(stats, "yumis_dungeon_and_fight:kalla", "duration", value("duration") - 20, 1);
            case BERSERKER_CRASH_BLOOD -> {
                param(stats, "yumis_dungeon_and_fight:mountain_crash", "cooldownMultiplier", 0, 1 - ratio("cooldown"));
                param(stats, "yumis_dungeon_and_fight:mountain_crash", "range", 0, 1 + ratio("range"));
                param(stats, "yumis_dungeon_and_fight:mountain_crash", "damage", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:mountain_crash", "finisher", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:mountain_crash", "hpCost", ratio("hpcost"), 1);
            }
            case BERSERKER_HAND_SIPHON -> {
                double scale = 1 + ratio("range");
                param(stats, "yumis_dungeon_and_fight:blood_hand", "range", 0, scale);
                param(stats, "yumis_dungeon_and_fight:blood_hand", "width", 0, scale);
                param(stats, "yumis_dungeon_and_fight:blood_hand", "cooldownMultiplier", 0, 1 - ratio("cooldown"));
            }
            case BERSERKER_HAND_CLAW -> param(stats, "yumis_dungeon_and_fight:blood_hand", "damage", 0, 1 + ratio("damage"));
            case BERSERKER_RAGE_ENDLESS -> {
                double extra = value("extra");
                param(stats, "yumis_dungeon_and_fight:blood_rage", "range", 0, 1 + ratio("range"));
                param(stats, "yumis_dungeon_and_fight:blood_rage", "slashes", extra, 1);
                param(stats, "yumis_dungeon_and_fight:blood_rage", "duration", extra * 0.15, 1);
                param(stats, "yumis_dungeon_and_fight:blood_rage", "damage", 0, 1 - ratio("reduction"));
            }
            case BERSERKER_BLADE_THROW -> {
                param(stats, "yumis_dungeon_and_fight:blood_blade", "blastRadius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:blood_blade", "finisher", 0, 1 + ratio("damage"));
            }
            case BERSERKER_BLADE_ASSAULT -> param(stats, "yumis_dungeon_and_fight:blood_blade", "cooldownMultiplier", 0, 1 - ratio("cooldown"));
            case BERSERKER_ERUPTION_QUAKE -> {
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "range", 0, 1 + ratio("range"));
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "damage", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "finisher", 0, 1 + ratio("damage"));
            }
            case BERSERKER_ERUPTION_PILLARS -> {
                double extra = value("extra");
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "slashes", extra, 1);
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "duration", extra * 0.2, 1);
                param(stats, "yumis_dungeon_and_fight:blood_eruption", "range", 0, 1 - ratio("shrink"));
            }
            case BM_GROOVE_UNBOUND -> {
                double bonusMul = 1 + ratio("bonus");
                param(stats, "yumis_dungeon_and_fight:battle_groove", "comboStageCap", value("cap") - 6, 1);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "comboThresholdScale", 0, 1 - ratio("threshold"));
                param(stats, "yumis_dungeon_and_fight:battle_groove", "grooveBonusScale", 0, bonusMul);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "perStageCrit", 0, bonusMul);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "comboHoldSeconds", value("hold") - 8, 1);
            }
            case BM_NIU_MASTERY -> {
                param(stats, "yumis_dungeon_and_fight:battle_groove", "spearBonusScale", 0, 2);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "spearRangeScale", 0, 1 + ratio("spearrange"));
                param(stats, "yumis_dungeon_and_fight:battle_groove", "clubBonusScale", 0, 2);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "clubAutoChaserScale", 0, 1 - ratio("clubcd"));
                param(stats, "yumis_dungeon_and_fight:battle_groove", "staffBonusScale", 0, 2);
                param(stats, "yumis_dungeon_and_fight:battle_groove", "staffChaserCdScale", 0, 1 - ratio("staffcd"));
            }
            case BM_FANG_TWIN_SHRED -> {
                param(stats, "yumis_dungeon_and_fight:dragon_fang", "slashes", value("hits") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_fang", "cooldownMultiplier", 0, 1 - ratio("cdr"));
            }
            case BM_FANG_WAVE -> {
                param(stats, "yumis_dungeon_and_fight:dragon_fang", "range", value("range") - 5, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_fang", "width", 0, 1 + ratio("width"));
                param(stats, "yumis_dungeon_and_fight:dragon_fang", "damage", 0, 1 + ratio("damage"));
            }
            case BM_CHASER_OVERFLOW -> {
                param(stats, "yumis_dungeon_and_fight:chaser_launcher", "radius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:chaser_launcher", "buffDuration", 0, 1 + ratio("duration"));
            }
            case BM_SWING_EMPRESS -> {
                param(stats, "yumis_dungeon_and_fight:full_swing", "damage", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:full_swing", "radius", value("radius") - 4.5, 1);
                param(stats, "yumis_dungeon_and_fight:full_swing", "arcDegrees", value("arc") - 210, 1);
                param(stats, "yumis_dungeon_and_fight:full_swing", "chaserGen", value("chasers") - 1, 1);
            }
            case BM_SWING_ARCANUM -> param(stats, "yumis_dungeon_and_fight:full_swing", "radius", 0, 1 + ratio("range"));
            case BM_FUSION_INSTANT -> {
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "impactDelay", -0.18, 1);
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "radius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "damage", 0, 1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "autoGenerateOnEmpty", 1, 1);
            }
            case BM_FUSION_OVERLOAD -> {
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "fusionBuffDuration", value("duration") - 15, 1);
                param(stats, "yumis_dungeon_and_fight:chaser_fusion", "fusionCritBonus", 0, 1 + ratio("bonus"));
            }
            case BM_PRESS_COLLAPSE -> {
                double bonusMul = 1 + ratio("bonus");
                param(stats, "yumis_dungeon_and_fight:chaser_press", "pullRadius", value("pull"), 1);
                param(stats, "yumis_dungeon_and_fight:chaser_press", "controlSeconds", value("control") - 0.8, 1);
                param(stats, "yumis_dungeon_and_fight:chaser_press", "perChaserDamage", 0, bonusMul);
                param(stats, "yumis_dungeon_and_fight:chaser_press", "radiusPerChaser", 0, bonusMul);
            }
            case BM_PUNTO_ENDLESS -> {
                double slashes = value("slashes");
                double damageFactor = 1 - ratio("reduction");
                param(stats, "yumis_dungeon_and_fight:punto_wave", "slashes", slashes - 12, 1);
                param(stats, "yumis_dungeon_and_fight:punto_wave", "duration", 0, slashes / 12.0);
                param(stats, "yumis_dungeon_and_fight:punto_wave", "damage", 0, damageFactor);
                param(stats, "yumis_dungeon_and_fight:punto_wave", "finisher", 0, damageFactor);
            }
            case BM_STRIKE_TWIN -> {
                param(stats, "yumis_dungeon_and_fight:dragon_strike", "dashCount", value("dashes") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_strike", "cooldownMultiplier", 0, 1 + ratio("cooldown"));
            }
            case BM_SPEAR_CLEAVE -> {
                double dmgMul = 1 + ratio("damage");
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "maxCharges", value("charges") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "dragonChaserCount", -7, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "dragonChaser", 0, 0);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "finisherMagic", 0, 0);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "thrust", 0, dmgMul);
                // Merge full magic budget (8*5% + 17.5% = 57.5%) into finisherPhys (17.5% -> 75% = *30/7), then apply +30%.
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "finisherPhys", 0, (30.0 / 7.0) * dmgMul);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "blastRadius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "duration", -1.35, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "holdSeconds", 0, 0);
            }
            case BM_SPEAR_DETACH -> {
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "dragonChaserCount", value("balls") - 8, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "interval", -0.05, 1);
                param(stats, "yumis_dungeon_and_fight:dragon_spear", "blastRadius", 0, 1 + ratio("radius"));
            }
            case ELEM_ICE_WALL_WAVE -> {
                double dmgMul = (10.0 / 7.0) * (1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:ice_wall", "damage", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "finisher", 0, 0);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "range", value("range") - 10, 1);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "radius", value("width") - 3.5, 1);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "duration", -5.15, 1);
            }
            case ELEM_ICE_WALL_MOBILE -> {
                double rangeMul = 1 + ratio("range");
                double dmgMul = 1 + ratio("damage");
                param(stats, "yumis_dungeon_and_fight:ice_wall", "radius", 0, rangeMul);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "blastRadius", 0, rangeMul);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "damage", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:ice_wall", "finisher", 0, dmgMul);
            }
            case ELEM_VORTEX_RAILGUN -> {
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "orbCount", -3, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "slashes", -2, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "damage", 0, 3.0 * (1 + ratio("damage")));
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "range", value("range") - 14, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "radius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:lightning_vortex", "duration", -0.20, 1);
            }
            case ELEM_VORTEX_CHAIN -> param(stats, "yumis_dungeon_and_fight:lightning_vortex", "cooldownMultiplier", 0, 1 - ratio("cdr"));
            case ELEM_VOID_CRYSTAL -> {
                param(stats, "yumis_dungeon_and_fight:void_sphere", "radius", 0.6, 1);
                param(stats, "yumis_dungeon_and_fight:void_sphere", "blastRadius", 1.0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:void_sphere", "damage", 0, 1.30);
                param(stats, "yumis_dungeon_and_fight:void_sphere", "manualDetonate", 0, 0);
            }
            case ELEM_VOID_MULTICHARGE -> {
                double bonusMul = 1 + ratio("bonus");
                param(stats, "yumis_dungeon_and_fight:void_sphere", "maxCharges", value("charges") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:void_sphere", "radius", 0, bonusMul);
                param(stats, "yumis_dungeon_and_fight:void_sphere", "blastRadius", 0, bonusMul);
                param(stats, "yumis_dungeon_and_fight:void_sphere", "duration", 0, bonusMul);
            }
            case ELEM_FLAME_CHAIN -> {
                param(stats, "yumis_dungeon_and_fight:flame_pillar", "pillarCount", value("count") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:flame_pillar", "pillarSpread", 0.7, 1);
                param(stats, "yumis_dungeon_and_fight:flame_pillar", "radius", 0, 1 + ratio("radius"));
            }
            case ELEM_BUSTER_COLOSSUS -> {
                double dmgMul = (10.0 / 7.0) * (1 + ratio("damage"));
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "radius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "shockwaveRadius", 0, 1.40);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "damage", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "finisher", 0, 0);
            }
            case ELEM_BUSTER_TRIPLE -> {
                double dmgMul = ratio("damage");
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "meteorCount", value("count") - 1, 1);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "twinGapSeconds", value("gap") - 0.30, 1);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "damage", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "finisher", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:halloween_buster", "emberDuration", value("ember"), 1);
            }
            case ELEM_ARCTIC_THRONE -> {
                double dmgMul = 1 + ratio("damage");
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "slashes", -7, 1);
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "duration", value("duration") - 2.0, 1);
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "damage", 0, 8.0 * dmgMul);
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "finisher", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "freezeSeconds", value("freeze") - 1.5, 1);
            }
            case ELEM_ARCTIC_VORTEX -> {
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "radius", 0, 1 + ratio("radius"));
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "pullRadius", value("pull"), 1);
                param(stats, "yumis_dungeon_and_fight:arctic_feast", "pullStrength", 5.5, 1);
            }
            case ELEM_BH_SINGULARITY -> {
                double dmgMul = 1 + ratio("damage");
                param(stats, "yumis_dungeon_and_fight:black_hole", "duration", 0, value("duration") / 3.0);
                param(stats, "yumis_dungeon_and_fight:black_hole", "interval", 0, value("duration") / 3.0);
                param(stats, "yumis_dungeon_and_fight:black_hole", "pullStrength", 0, 1 + ratio("pull"));
                param(stats, "yumis_dungeon_and_fight:black_hole", "damage", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:black_hole", "finisher", 0, dmgMul);
                param(stats, "yumis_dungeon_and_fight:black_hole", "subHoleDuration", value("sub"), 1);
            }
            case ELEM_BH_RESONANCE -> {
                double radiusMul = 1 + ratio("radius");
                param(stats, "yumis_dungeon_and_fight:black_hole", "pullRadius", 0, radiusMul);
                param(stats, "yumis_dungeon_and_fight:black_hole", "radius", 0, radiusMul);
                param(stats, "yumis_dungeon_and_fight:black_hole", "blastRadius", 0, radiusMul);
            }
            case ELEM_STRIKE_CLOUD -> {
                param(stats, "yumis_dungeon_and_fight:lightning_strike", "channeling", 0, 0);
                param(stats, "yumis_dungeon_and_fight:lightning_strike", "slashes", value("bolts") - 5, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_strike", "duration", value("duration") - 1.75, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_strike", "interval", 0.15, 1);
                param(stats, "yumis_dungeon_and_fight:lightning_strike", "damage", 0, 1 + ratio("damage"));
            }
            default -> {}
        }
    }

    static List<TowerCard> pool(String jobId, List<SkillChoice> skills) {
        var result = new ArrayList<TowerCard>();
        for (var card : values()) {
            if (!card.job.isEmpty() && !card.job.equals(jobId)) continue;
            if (card.binding == NONE) result.add(card.bound("", ""));
            else {
                var eligible = skills.stream().filter(card::eligible).toList();
                if (!eligible.isEmpty()) result.add(new TowerCard(card.id, card.title, card.rarity,
                    card.text("【技能】"), card::apply, random -> {
                        var pick = eligible.get(random.nextInt(eligible.size()));
                        return card.bound(pick.id(), pick.title());
                    }, "", "", card.job));
            }
        }
        return result;
    }

    static List<TowerCard> careerMythics(String jobId) {
        if (jobId == null || jobId.isEmpty()) return List.of();
        var result = new ArrayList<TowerCard>();
        for (var card : values()) {
            if (card.careerMythic() && card.job.equals(jobId)) result.add(card.bound("", ""));
        }
        return List.copyOf(result);
    }

    static TowerCard restore(TowerCard.Reference reference) {
        for (var card : values()) if (card.id.equals(reference.id())) return card.bound(reference.variant(), reference.variantTitle());
        throw new IllegalArgumentException("未知塔卡 " + reference.id());
    }

    private boolean eligible(SkillChoice skill) {
        return switch (binding) {
            case SKILL -> true;
            case DAMAGE -> skill.damage();
            case SINGLE -> skill.damage() && skill.singleTarget();
            case BUFF -> skill.timedBuff();
            default -> false;
        };
    }

    private TowerCard bound(String skill, String skillTitle) {
        return new TowerCard(id, title, rarity, text(skillTitle), this::apply, null, skill, skillTitle, job);
    }

    private RichText text(String skillTitle) {
        var spans = new ArrayList<RichText.Span>();
        var matcher = PARAMETER.matcher(description);
        int at = 0;
        String resolvedSkill = skillTitle.isEmpty() ? fixedSkill : skillTitle;
        while (matcher.find()) {
            if (matcher.start() > at) spans.add(new RichText.Span(description.substring(at, matcher.start()), List.of()));
            String key = matcher.group(1);
            String value = key.equals("skill") ? resolvedSkill : BigDecimal.valueOf(value(key)).stripTrailingZeros().toPlainString();
            spans.add(new RichText.Span(value + matcher.group().substring(key.length() + 2), List.of("#77BDFF")));
            at = matcher.end();
        }
        if (at < description.length()) spans.add(new RichText.Span(description.substring(at), List.of()));
        return new RichText(spans);
    }
}

package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
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
    SIPHON("soul_siphon", "灵魂虹吸", EPIC, "获得 {crit}% 暴击率，暴击时回复实际伤害的 {heal}%", Map.of("crit",5,"heal",1)),
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
    LEECH("siphon", "虹吸", COMMON, SKILL, "{skill} 命中回复实际伤害的 {heal}%，每次施放上限为基础冷却秒数百分比的最大生命", Map.of("heal",1)),
    INFUSE("imbue", "注魔", COMMON, "普攻附带最高攻击属性 {damage}% 的伤害", Map.of("damage",100));

    enum Binding { NONE, SKILL, DAMAGE, SINGLE, BUFF }

    public record SkillChoice(String id, String title, boolean damage, boolean singleTarget, boolean timedBuff) {}

    private static final Pattern PARAMETER = Pattern.compile("\\{([a-z]+)}(?:%| 秒| 格| 点| 层)?");
    private final String id, title, description;
    private final Rarity rarity;
    private final Binding binding;
    private final Map<String, ? extends Number> values;

    TowerBattleCard(String id, String title, Rarity rarity, String description, Map<String, ? extends Number> values) {
        this(id, title, rarity, NONE, description, values);
    }

    TowerBattleCard(String id, String title, Rarity rarity, Binding binding, String description, Map<String, ? extends Number> values) {
        this.id = id;
        this.title = title;
        this.rarity = rarity;
        this.binding = binding;
        this.description = description;
        this.values = values;
    }

    public String id() { return id; }
    public String title() { return title; }
    public double value(String parameter) { return values.get(parameter).doubleValue(); }
    public double ratio(String parameter) { return value(parameter) / 100; }
    public int count(String parameter) { return values.get(parameter).intValue(); }
    public long millis(String parameter) { return Math.round(value(parameter) * 1000); }
    public boolean held(Stats stats) { return stats.modifiers.getOrDefault("towerCard/" + id, 0d) > 0; }

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
            default -> {}
        }
    }

    static List<TowerCard> pool(List<SkillChoice> skills) {
        var result = new ArrayList<TowerCard>();
        for (var card : values()) {
            if (card.binding == NONE) result.add(card.bound("", ""));
            else {
                var eligible = skills.stream().filter(card::eligible).toList();
                if (!eligible.isEmpty()) result.add(new TowerCard(card.id, card.title, card.rarity,
                    card.text("【技能】"), card::apply, random -> {
                        var pick = eligible.get(random.nextInt(eligible.size()));
                        return card.bound(pick.id(), pick.title());
                    }, "", ""));
            }
        }
        return result;
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
        return new TowerCard(id, title, rarity, text(skillTitle), this::apply, null, skill, skillTitle);
    }

    private RichText text(String skillTitle) {
        var spans = new ArrayList<RichText.Span>();
        var matcher = PARAMETER.matcher(description);
        int at = 0;
        while (matcher.find()) {
            if (matcher.start() > at) spans.add(new RichText.Span(description.substring(at, matcher.start()), List.of()));
            String key = matcher.group(1);
            String value = key.equals("skill") ? skillTitle : BigDecimal.valueOf(value(key)).stripTrailingZeros().toPlainString();
            spans.add(new RichText.Span(value + matcher.group().substring(key.length() + 2), List.of("#77BDFF")));
            at = matcher.end();
        }
        if (at < description.length()) spans.add(new RichText.Span(description.substring(at), List.of()));
        return new RichText(spans);
    }
}

package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.presentation.api.RichText;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import static cn.yumis.fight.modules.dungeons.api.TowerCardEffects.*;

/** An individual card offered by the climb. Its passive changes a fresh local snapshot. */
public record TowerCard(String id, String name, Rarity rarity, RichText description, Consumer<Stats> passive,
                        Function<RandomGenerator, TowerCard> binding, String variant, String variantTitle, String job) {
    private static final List<String> VALUE_COLOR = List.of("#77BDFF");
    private static final double RELIABLE_WEAPON_BONUS = 30;
    private static final List<TowerCard> CARDS = List.of(
        strongShield(30, 50),
        strength(20),
        agility(60),
        bloodthirst(1),
        eureka(1),
        modifier("giant_slayer", "巨人杀手", Rarity.MYTHIC, HIGHER_LIFE, 80,
            text("对生命百分比高于自己的敌人，伤害提高 ")),
        goliath(200, 100, 15),
        madScientist(15, 35),
        inventor(50),
        totalDamage("sniper", "狙神", Rarity.EPIC, DISTANT_HIT, 30,
            text("命中超过 "), value(SNIPER_RANGE, " 格"), text(" 的敌人时，伤害总增 ")),
        modifier("start_to_finish", "有始有终", Rarity.EPIC, FULL_LIFE, 100,
            text("命中满血敌人时，伤害提高 ")),
        modifier("critical_hit", "关键暴击", Rarity.EPIC, "crit", 20, text("暴击率提高 ")),
        astralBody(200, 10),
        penetration(18),
        repeatingCycle(60),
        swiftStrike(.5),
        killingIntent(20),
        heavyHitter(1),
        totalDamage("wizard_thinking", "巫师式思考", Rarity.COMMON, SPELL_DAMAGE, 20, text("法术伤害总增 ")),
        modifier("mind_over_matter", "由心及物", Rarity.COMMON, HEALTH_FROM_MANA, 100,
            text("增加最大生命，数值为角色基础魔力的 "))
    );
    static final TowerCard MILK = card("milk", "牛奶", Rarity.COMMON, stats -> stats.mut(Stat.HEALTH, .10),
        text("最大生命提高 "), percent(10));

    public record Reference(String id, String variant, String variantTitle) {}

    public enum Rarity {
        COMMON(20), RARE(30), EPIC(30), MYTHIC(20);

        private final int weight;

        Rarity(int weight) {
            this.weight = weight;
        }

        public int weight() {
            return weight;
        }
    }

    public TowerCard {
        Objects.requireNonNull(id);
        Objects.requireNonNull(name);
        Objects.requireNonNull(rarity);
        Objects.requireNonNull(description);
        Objects.requireNonNull(passive);
        Objects.requireNonNull(variant);
        Objects.requireNonNull(variantTitle);
        Objects.requireNonNull(job);
    }

    public TowerCard(String id, String name, Rarity rarity, RichText description, Consumer<Stats> passive,
                     Function<RandomGenerator, TowerCard> binding, String variant, String variantTitle) {
        this(id, name, rarity, description, passive, binding, variant, variantTitle, "");
    }

    public TowerCard(String id, String name, Rarity rarity, RichText description, Consumer<Stats> passive) {
        this(id, name, rarity, description, passive, null, "", "", "");
    }

    public boolean careerMythic() {
        return rarity == Rarity.MYTHIC && !job.isEmpty();
    }

    public static List<TowerCard> pool(String jobId, List<TowerBattleCard.SkillChoice> skills) {
        var types = JobCatalog.weapons(jobId);
        var weapons = Weapon.standard().stream().filter(weapon -> types.contains(weapon.name())).toList();
        var cards = new ArrayList<>(CARDS);
        if (!weapons.isEmpty()) cards.add(reliableWeapon(weapons));
        cards.addAll(TowerBattleCard.pool(jobId, skills));
        return List.copyOf(cards);
    }

    public static List<TowerCard> careerMythics(String jobId) {
        return TowerBattleCard.careerMythics(jobId);
    }

    TowerCard bind(RandomGenerator random) {
        return binding == null ? this : binding.apply(random);
    }

    public boolean milk() {
        return this == MILK;
    }

    Reference reference() {
        return new Reference(id, variant, variantTitle);
    }

    static TowerCard restore(Reference reference) {
        if (reference.id().equals(MILK.id())) return MILK;
        if (reference.id().equals("reliable_weapon")) return boundWeapon(Weapon.valueOf(reference.variant()));
        for (var card : CARDS) if (card.id().equals(reference.id())) return card;
        return TowerBattleCard.restore(reference);
    }

    private static TowerCard reliableWeapon(List<Weapon> weapons) {
        var description = new RichText(List.of(text("使用"), highlight("【特定武器类型】"), text("时，伤害提高 "), percent(RELIABLE_WEAPON_BONUS)));
        return new TowerCard("reliable_weapon", "可靠武器", Rarity.COMMON, description, stats -> {},
            random -> boundWeapon(weapons.get(random.nextInt(weapons.size()))), "", "");
    }

    private static TowerCard boundWeapon(Weapon weapon) {
        var description = new RichText(List.of(text("使用"), highlight(weapon.title()), text("时，伤害提高 "), percent(RELIABLE_WEAPON_BONUS)));
        return new TowerCard("reliable_weapon", "可靠武器", Rarity.COMMON, description,
            stats -> add(stats, FROM_WEAPON + weapon.name(), RELIABLE_WEAPON_BONUS / 100), null, weapon.name(), weapon.title());
    }

    private static TowerCard eureka(double haste) {
        return card("eureka", "尤里卡", Rarity.MYTHIC, stats -> add(stats, HASTE_FROM_ATTACK, haste / 100),
            text("物理与魔法攻击中较高者每满 "), value(ATTACK_PER_HASTE, " 点"),
            text("，获得 "), value(haste, " 点"), text("技能冷却时间加速"));
    }

    private static TowerCard goliath(double health, double mana, double damage) {
        return card("goliath", "歌利亚巨人", Rarity.MYTHIC, stats -> {
            stats.attribute(Stat.HEALTH).more(health / 100);
            stats.attribute(Stat.MP).more(mana / 100);
            more(stats, ALL_DAMAGE, damage / 100);
        }, text("生命总增 "), percent(health), text("，魔力总增 "), percent(mana),
            text("，所有伤害总增 "), percent(damage));
    }

    private static TowerCard madScientist(double damage, double haste) {
        return card("mad_scientist", "科学狂人", Rarity.MYTHIC, stats -> {
            more(stats, ALL_DAMAGE, damage / 100);
            stats.mut(Stat.COOLDOWN_RECOVERY, haste / 100);
        }, text("所有伤害总增 "), percent(damage), text("，获得 "), value(haste, " 点"), text("技能冷却速度"));
    }

    private static TowerCard inventor(double reduction) {
        return card("cutting_edge_inventor", "尖端发明家", Rarity.EPIC,
            stats -> more(stats, CARD_COOLDOWN, -reduction / 100),
            text("卡牌自身的冷却时间总降 "), percent(reduction));
    }

    private static TowerCard astralBody(double health, double reduction) {
        return card("astral_body", "星界躯体", Rarity.EPIC, stats -> {
            stats.mut(Stat.HEALTH, health / 100);
            more(stats, ALL_DAMAGE, -reduction / 100);
        }, text("最大生命提高 "), percent(health), text("，造成的伤害总降 "), percent(reduction));
    }

    private static TowerCard repeatingCycle(double haste) {
        return card("repeating_cycle", "循环往复", Rarity.EPIC, stats -> stats.mut(Stat.COOLDOWN_RECOVERY, haste / 100),
            text("获得 "), value(haste, " 点"), text("技能冷却时间加速"));
    }

    private static TowerCard penetration(double bonus) {
        return card("thread_the_needle", "穿针引线", Rarity.EPIC, stats -> add(stats, PENETRATION, bonus / 100),
            text("无视敌人 "), percent(bonus), text(" 的物理防御和魔法防御"));
    }

    private static TowerCard swiftStrike(double bonus) {
        return card("swift_strike", "唯快不破", Rarity.COMMON, stats -> add(stats, FROM_MOVEMENT, bonus),
            text("移动速度每高于初始值 "), percent(1), text("，最终伤害提高 "), percent(bonus));
    }

    private static TowerCard heavyHitter(double bonus) {
        return card("heavy_hitter", "重量级打击手", Rarity.COMMON, stats -> add(stats, FROM_HEALTH, bonus / 100),
            text("每满 "), value(HEALTH_PER_DAMAGE, " 点"), text("最大生命，所有伤害提高 "), percent(bonus));
    }

    private static TowerCard killingIntent(double bonus) {
        return totalDamage("killing_intent", "杀意翻涌", Rarity.COMMON, LOW_LIFE_TOTAL, bonus,
            text("对生命低于 "), percent(LOW_HEALTH_PERCENT), text(" 的敌人造成的伤害总增 "));
    }

    private static TowerCard totalDamage(String id, String name, Rarity rarity, String key, double bonus,
                                         RichText.Span... description) {
        var spans = new ArrayList<>(List.of(description));
        spans.add(percent(bonus));
        return new TowerCard(id, name, rarity, new RichText(spans), stats -> more(stats, key, bonus / 100));
    }

    private static TowerCard modifier(String id, String name, Rarity rarity, String key, double bonus,
                                     RichText.Span... description) {
        var spans = new ArrayList<>(List.of(description));
        spans.add(percent(bonus));
        return new TowerCard(id, name, rarity, new RichText(spans), stats -> add(stats, key, bonus / 100));
    }

    private static TowerCard card(String id, String name, Rarity rarity, Consumer<Stats> effect,
                                  RichText.Span... description) {
        return new TowerCard(id, name, rarity, new RichText(List.of(description)), effect);
    }

    private static void add(Stats stats, String key, double ratio) {
        stats.modifiers.merge(key, ratio, Double::sum);
    }

    private static void more(Stats stats, String key, double ratio) {
        stats.modifiers.merge(key, 1 + ratio, (left, right) -> left * right);
    }

    private static TowerCard strongShield(double capacity, double recovery) {
        return new TowerCard("strong_shield", "强力护盾", Rarity.COMMON,
            new RichText(List.of(text("护盾总提升 "), percent(capacity), text("，护盾回复速度提高 "), percent(recovery))),
            stats -> {
                stats.attribute(Stat.SHIELD).more(capacity / 100);
                stats.mut(Stat.SHIELD_REGENERATION, recovery / 100);
            });
    }

    private static TowerCard strength(double attack) {
        return new TowerCard("strength", "大力", Rarity.COMMON,
            new RichText(List.of(text("攻击力提高 "), percent(attack))),
            stats -> {
                stats.mut(Stat.PHYSICAL_ATTACK, attack / 100);
                stats.mut(Stat.MAGICAL_ATTACK, attack / 100);
            });
    }

    private static TowerCard agility(double attackSpeed) {
        return new TowerCard("agility", "灵巧", Rarity.COMMON,
            new RichText(List.of(text("攻击速度提高 "), percent(attackSpeed))),
            stats -> stats.extra(Stat.ATTACK_SPEED, attackSpeed));
    }

    private static TowerCard bloodthirst(double lifeSteal) {
        return new TowerCard("bloodthirst", "渴血", Rarity.COMMON,
            new RichText(List.of(text("获得 "), value(lifeSteal, " 点"), text("吸血值"))),
            stats -> stats.extra(Stat.LIFE_STEAL, lifeSteal));
    }

    private static RichText.Span text(String text) {
        return new RichText.Span(text, List.of());
    }

    private static RichText.Span percent(double value) {
        return value(value, "%");
    }

    private static RichText.Span value(double value, String unit) {
        return highlight(number(value) + unit);
    }

    private static RichText.Span highlight(String value) {
        return new RichText.Span(value, VALUE_COLOR);
    }

    private static String number(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}

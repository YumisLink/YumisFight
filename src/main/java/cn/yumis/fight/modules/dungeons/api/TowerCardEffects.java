package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.CharacterAttributes;
import cn.yumis.fight.modules.character.api.Progress;

/** Card modifiers carried by the player's detached combat snapshot. */
public final class TowerCardEffects {
    static final int ATTACK_PER_HASTE = 1000;
    static final int HEALTH_PER_DAMAGE = 100;
    static final int LOW_HEALTH_PERCENT = 35;
    static final int SNIPER_RANGE = 8;

    static final String ALL_DAMAGE = "towerCard/allDamage";
    static final String SPELL_DAMAGE = "towerCard/spellDamage";
    static final String FROM_MOVEMENT = "towerCard/damageFromMovement";
    static final String FROM_HEALTH = "towerCard/damageFromHealth";
    static final String FROM_WEAPON = "towerCard/damageWithWeapon/";
    static final String HIGHER_LIFE = "towerCard/higherLife";
    static final String FULL_LIFE = "towerCard/fullLife";
    static final String LOW_LIFE_TOTAL = "towerCard/lowLifeTotal";
    static final String DISTANT_HIT = "towerCard/distantHit";
    static final String HASTE_FROM_ATTACK = "towerCard/hasteFromAttack";
    static final String HEALTH_FROM_MANA = "towerCard/healthFromMana";
    static final String CARD_COOLDOWN = "towerCard/cooldown";
    static final String PENETRATION = "towerCard/penetration";
    public static final String SLAP_DAMAGE = "towerCard/slapDamage";

    private TowerCardEffects() {}

    static void applyDerivedStats(Stats stats, Progress progress) {
        double manaRatio = stats.modifiers.getOrDefault(HEALTH_FROM_MANA, 0d);
        if (manaRatio != 0) {
            stats.extra(Stat.HEALTH, CharacterAttributes.baseMana(progress) * manaRatio);
        }
        double hastePerStep = stats.modifiers.getOrDefault(HASTE_FROM_ATTACK, 0d);
        if (hastePerStep != 0) {
            double attack = Math.max(0, Math.max(stats.get(Stat.PHYSICAL_ATTACK), stats.get(Stat.MAGICAL_ATTACK)));
            stats.mut(Stat.COOLDOWN_RECOVERY, Math.floor(attack / ATTACK_PER_HASTE) * hastePerStep);
        }
        if (TowerBattleCard.SM_FLOWING_STANCE.held(stats)) {
            int level = progress == null ? 20 : Math.max(1, progress.level);
            int rank = Math.max(1, 1 + Math.max(0, level - 20) / 5);
            stats.ranks.merge("grant/yumis_dungeon_and_fight:meteor_rise", rank, Math::max);
            stats.ranks.merge("grant/yumis_dungeon_and_fight:meteor_leap", rank, Math::max);
            stats.ranks.merge("grant/yumis_dungeon_and_fight:meteor_pierce", rank, Math::max);
        }
    }

    public static long cooldownMillis(Stats stats, long baseMillis) {
        return Math.round(baseMillis * stats.modifiers.getOrDefault(CARD_COOLDOWN, 1d));
    }

    public static double penetration(Stats stats) {
        return stats.modifiers.getOrDefault(PENETRATION, 0d);
    }

    public static double spellDamage(Stats stats) {
        return stats.modifiers.getOrDefault(SPELL_DAMAGE, 1d);
    }

    public static boolean forceCritical(Stats stats, double targetLife) {
        return TowerBattleCard.JEWEL.held(stats) && targetLife < TowerBattleCard.JEWEL.ratio("threshold");
    }

    public static double criticalMultiplier(Stats stats) {
        return 1.5 + stats.modifiers.getOrDefault("critDamage", 0d) + stats.modifiers.getOrDefault("crit", 0d);
    }

    public static double healingFactor(Stats stats) {
        return TowerBattleCard.AID.held(stats) ? 1 + TowerBattleCard.AID.ratio("heal") : 1;
    }

    public record DamageBonus(double increase, double total) {
        public Stats apply(Stats source) {
            if (increase == 0 && total == 1) return source;
            Stats stats = source.copy();
            stats.modifier("final").mut += increase;
            stats.modifier("skillProduct").alls *= total;
            return stats;
        }

        /** Fixed damage has no attack-panel or skill multiplier of its own. */
        public double fixedMultiplier() {
            return (1 + increase) * total;
        }
    }

    public static DamageBonus panel(Stats stats) {
        return new DamageBonus(damageIncrease(stats), damageProduct(stats));
    }

    public static DamageBonus hit(Stats stats, double ownLife, double targetLife, double distanceSquared) {
        double increase = damageIncrease(stats);
        double total = damageProduct(stats);
        if (targetLife > ownLife) increase += stats.modifiers.getOrDefault(HIGHER_LIFE, 0d);
        if (targetLife == 1) increase += stats.modifiers.getOrDefault(FULL_LIFE, 0d);
        if (targetLife < LOW_HEALTH_PERCENT / 100d) {
            total *= stats.modifiers.getOrDefault(LOW_LIFE_TOTAL, 1d);
        }
        if (distanceSquared > SNIPER_RANGE * SNIPER_RANGE) {
            total *= stats.modifiers.getOrDefault(DISTANT_HIT, 1d);
        }
        return new DamageBonus(increase, total);
    }

    private static double damageIncrease(Stats stats) {
        double movement = Math.max(0, stats.get(Stat.MOVEMENT_SPEED) - 1);
        double lifeSteps = Math.floor(Math.max(0, stats.get(Stat.HEALTH)) / HEALTH_PER_DAMAGE);
        return movement * stats.modifiers.getOrDefault(FROM_MOVEMENT, 0d)
            + lifeSteps * stats.modifiers.getOrDefault(FROM_HEALTH, 0d)
            + stats.modifiers.getOrDefault(SLAP_DAMAGE, 0d)
            + stats.modifiers.getOrDefault(FROM_WEAPON + stats.weaponType, 0d);
    }

    private static double damageProduct(Stats stats) {
        double total = stats.modifiers.getOrDefault(ALL_DAMAGE, 1d);
        if (TowerBattleCard.OVERFLOW.held(stats))
            total *= 1 + (stats.get(Stat.MP) / TowerBattleCard.OVERFLOW.value("divisor") + TowerBattleCard.OVERFLOW.value("base")) / 100;
        return total;
    }
}

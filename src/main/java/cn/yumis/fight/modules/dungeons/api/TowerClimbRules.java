package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;

/** Rules for the 50-floor climb using the awakening tower layouts. */
public final class TowerClimbRules {
    public static final int FLOOR_COUNT = 50;
    public static final Difficulty DIFFICULTY = Difficulty.HELL;
    public static final double MOVEMENT_FACTOR = .5;
    public static final double FIRE_HEALTH_FRACTION = .01;
    public static final double ACTION_SPEED_FACTOR = .5;
    public static final double MANA_RECOVERY_FACTOR = .1;
    public static final double FROST_DAMAGE_FRACTION = .001;
    public static final double COOLDOWN_RECOVERY_FACTOR = .5;

    private TowerClimbRules() {}

    public static boolean bossFloor(int floor) {
        return floor > 0 && floor <= FLOOR_COUNT && floor % 5 == 0;
    }

    public static boolean specialBossFloor(int floor) {
        return floor == 15 || floor == 30 || floor == 45 || floor == FLOOR_COUNT;
    }

    public static int enemyLevel(int climberLevel) {
        return Math.addExact(climberLevel, 5);
    }

    /** Applied once to the player's fresh snapshot after the other speed and recovery sources. */
    public static void applyPlayerField(AwakeningTower tower, Stats stats) {
        switch (tower) {
            case ROOT -> scaleRate(stats, Stat.MOVEMENT_SPEED, MOVEMENT_FACTOR);
            case SLUICE -> {
                scaleRate(stats, Stat.CAST_SPEED, ACTION_SPEED_FACTOR);
                // Attack speed is a bonus in percentage points on top of an implicit 100%.
                scaleRate(stats, Stat.ATTACK_SPEED, ACTION_SPEED_FACTOR);
                stats.attribute(Stat.ATTACK_SPEED).fadd += (ACTION_SPEED_FACTOR - 1) * 100;
            }
            case SAND_MARKET -> scaleRate(stats, Stat.MANA_REGENERATION, MANA_RECOVERY_FACTOR);
            case SEAL -> scaleRate(stats, Stat.COOLDOWN_RECOVERY, COOLDOWN_RECOVERY_FACTOR);
            default -> {}
        }
    }

    public static double enemyHealthFactor(AwakeningTower tower) {
        return tower == AwakeningTower.ORRERY ? 3 : 1;
    }

    public static double enemyAttackFactor(AwakeningTower tower) {
        return tower == AwakeningTower.ORRERY ? 1.5 : 1;
    }

    public static boolean forceElite(AwakeningTower tower) {
        return tower == AwakeningTower.THEATER;
    }

    private static void scaleRate(Stats stats, Stat stat, double factor) {
        var attribute = stats.attribute(stat);
        attribute.more(factor - 1);
        attribute.fadd *= factor;
    }
}

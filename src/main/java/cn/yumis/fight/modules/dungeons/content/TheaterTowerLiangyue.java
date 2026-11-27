package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;
import net.minecraft.world.phys.Vec3;

import java.util.SplittableRandom;

/**
 * 剧场塔第 20 层专属首领「神剑-梁月的幻影」基础面板评级、4 项技能参数与剑圣 1 觉编排集中定义。
 */
public final class TheaterTowerLiangyue {
    public static final String ID = "theater_tower_liangyue";
    public static final String DISPLAY_NAME = "神剑-梁月的幻影";

    // 基础面板评级：HP A (1.8) / 攻击力 B (1.6)
    public static final TowerBossGrade HEALTH_GRADE = TowerBossGrade.A;
    public static final TowerBossGrade ATTACK_GRADE = TowerBossGrade.B;

    // 物理无属性剑技（沿用 ElementAccumulation 编码）
    public static final int ELEMENT_NONE = -1;

    // 伤害系数（乘 Target.attack(level)）
    public static final double COEFF_WHIRLWIND_DOT = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_WHIRLWIND_DOT", 0.20);
    public static final double COEFF_DANCE_SLASH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_DANCE_SLASH", 0.25);
    public static final double COEFF_DANCE_WAVE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_DANCE_WAVE", 0.45);
    public static final double COEFF_STORM_SLASH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_STORM_SLASH", 0.25);
    public static final double COEFF_SWORD_WAVE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_SWORD_WAVE", 0.80);
    public static final double COEFF_DRAGON_DASH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_DRAGON_DASH", 0.90);
    public static final double COEFF_DRAGON_WAVE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_DRAGON_WAVE", 0.85);
    public static final double COEFF_DANCE_FINISHER = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_DANCE_FINISHER", 1.35);
    public static final double COEFF_STORM_FINISHER = BossBalanceRules.current().towerNum("theater_tower_liangyue", "COEFF_STORM_FINISHER", 1.40);

    // 技能 1：三连普通攻击 + 大范围 AOE 直线剑气（冷却 8 秒）
    public static final long SKILL1_CD_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL1_CD_MS", 8_000L);
    public static final int SKILL1_STRIKES = BossBalanceRules.current().towerInt("theater_tower_liangyue", "SKILL1_STRIKES", 3);
    public static final long SKILL1_SWING_INTERVAL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL1_SWING_INTERVAL_MS", 1_015L);
    public static final long SKILL1_WAVE_WARNING_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL1_WAVE_WARNING_MS", 655L);
    public static final long SKILL1_WAVE_TRAVEL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL1_WAVE_TRAVEL_MS", 750L);
    public static final double SKILL1_WAVE_RANGE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL1_WAVE_RANGE", 26.0);
    public static final double SKILL1_WAVE_WIDTH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL1_WAVE_WIDTH", 4.8);
    public static final long SKILL1_RECOVERY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL1_RECOVERY_MS", 500L);
    public static final double[] SKILL1_SLASH_ANGLES = BossBalanceRules.current().towerArray("theater_tower_liangyue", "SKILL1_SLASH_ANGLES", new double[]{-22.0, 22.0, 0.0});

    // 技能 2：一段猛龙断空斩（20 格，中额伤害）+ 三道追击旋风（小额持续伤害）+ 大范围 AOE 直线剑气（冷却 8 秒）
    public static final long SKILL2_CD_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_CD_MS", 8_000L);
    public static final long SKILL2_WINDUP_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WINDUP_MS", 800L);
    public static final double SKILL2_DASH_DISTANCE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_DASH_DISTANCE", 20.0);
    public static final long SKILL2_DASH_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_DASH_MS", 550L);
    public static final double SKILL2_DASH_WIDTH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_DASH_WIDTH", 2.6);
    public static final int SKILL2_WHIRLWIND_COUNT = BossBalanceRules.current().towerInt("theater_tower_liangyue", "SKILL2_WHIRLWIND_COUNT", 3);
    public static final long SKILL2_WHIRLWIND_WARNING_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WHIRLWIND_WARNING_MS", 725L);
    public static final long SKILL2_WHIRLWIND_DURATION_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WHIRLWIND_DURATION_MS", 4_500L);
    public static final long SKILL2_WHIRLWIND_TICK_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WHIRLWIND_TICK_MS", 500L);
    public static final double SKILL2_WHIRLWIND_SPEED = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_WHIRLWIND_SPEED", 4.2);
    public static final double SKILL2_WHIRLWIND_RADIUS = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_WHIRLWIND_RADIUS", 1.8);
    /** 旋风周期性位置同步间隔（>= 250ms / 5 ticks，遵守服务器最多每秒 4 次同步约束）。 */
    public static final long SKILL2_WHIRLWIND_SYNC_INTERVAL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WHIRLWIND_SYNC_INTERVAL_MS", 250L);
    public static final long SKILL2_WAVE_WARNING_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WAVE_WARNING_MS", 510L);
    public static final long SKILL2_WAVE_TRAVEL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_WAVE_TRAVEL_MS", 750L);
    public static final double SKILL2_WAVE_RANGE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_WAVE_RANGE", 28.0);
    public static final double SKILL2_WAVE_WIDTH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL2_WAVE_WIDTH", 5.2);
    public static final long SKILL2_RECOVERY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL2_RECOVERY_MS", 500L);

    // 技能 3：剑圣 1 觉 · 极鬼剑术·暴风式（冷却 65 秒）
    public static final long SKILL3_CD_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_CD_MS", 65_000L);
    public static final int SKILL3_SWORDS = BossBalanceRules.current().towerInt("theater_tower_liangyue", "SKILL3_SWORDS", 24);
    public static final double SKILL3_RADIUS = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL3_RADIUS", 7.5);
    public static final double SKILL3_FINISHER_RADIUS = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL3_FINISHER_RADIUS", 8.5);
    public static final double SKILL3_UPPER_HEIGHT = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL3_UPPER_HEIGHT", 4.0);
    public static final long SKILL3_DEPLOY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_DEPLOY_MS", 1_740L);
    public static final long SKILL3_SLASH_INTERVAL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_SLASH_INTERVAL_MS", 165L);
    public static final long SKILL3_SLASH_PHASE_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_SLASH_PHASE_MS", SKILL3_SWORDS * SKILL3_SLASH_INTERVAL_MS);
    public static final long SKILL3_DAMAGE_TICK_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_DAMAGE_TICK_MS", 450L);
    public static final long SKILL3_FINISHER_DELAY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_FINISHER_DELAY_MS", 800L);
    public static final long SKILL3_FINISHER_LINGER_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_FINISHER_LINGER_MS", 650L);
    public static final long SKILL3_TOTAL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_TOTAL_MS", SKILL3_DEPLOY_MS + SKILL3_SLASH_PHASE_MS + SKILL3_FINISHER_DELAY_MS + SKILL3_FINISHER_LINGER_MS);
    public static final long SKILL3_RECOVERY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL3_RECOVERY_MS", 600L);

    // 技能 4：幻影剑舞（冷却 30 秒，朝玩家所在方向连续发射剑气 + 终结巨型剑气）
    public static final long SKILL4_CD_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_CD_MS", 30_000L);
    public static final long SKILL4_WINDUP_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_WINDUP_MS", 870L);
    public static final long SKILL4_FLURRY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_FLURRY_MS", 2_000L);
    public static final int SKILL4_WAVE_COUNT = BossBalanceRules.current().towerInt("theater_tower_liangyue", "SKILL4_WAVE_COUNT", 5);
    public static final long SKILL4_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_WAVE_INTERVAL_MS", 400L);
    public static final double SKILL4_MELEE_RADIUS = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL4_MELEE_RADIUS", 4.5);
    public static final long SKILL4_MELEE_TICK_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_MELEE_TICK_MS", 250L);
    public static final long SKILL4_WAVE_WARNING_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_WAVE_WARNING_MS", 365L);
    public static final long SKILL4_WAVE_TRAVEL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_WAVE_TRAVEL_MS", 700L);
    public static final double SKILL4_WAVE_RANGE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL4_WAVE_RANGE", 24.0);
    public static final double SKILL4_WAVE_WIDTH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL4_WAVE_WIDTH", 3.0);
    public static final long SKILL4_FINISHER_WARNING_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_FINISHER_WARNING_MS", 655L);
    public static final long SKILL4_FINISHER_TRAVEL_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_FINISHER_TRAVEL_MS", 850L);
    public static final double SKILL4_FINISHER_RANGE = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL4_FINISHER_RANGE", 30.0);
    public static final double SKILL4_FINISHER_WIDTH = BossBalanceRules.current().towerNum("theater_tower_liangyue", "SKILL4_FINISHER_WIDTH", 5.6);
    public static final long SKILL4_RECOVERY_MS = BossBalanceRules.current().towerMs("theater_tower_liangyue", "SKILL4_RECOVERY_MS", 650L);

    public enum SkillId {
        TRIPLE_SLASH_WAVE("triple_slash_wave", SKILL1_CD_MS),
        RAGING_DRAGON("raging_dragon", SKILL2_CD_MS),
        STORM_AWAKENING("storm_awakening", SKILL3_CD_MS),
        PHANTOM_SWORD_DANCE("phantom_sword_dance", SKILL4_CD_MS);

        public final String id;
        public final long cooldownMs;

        SkillId(String id, long cooldownMs) {
            this.id = id;
            this.cooldownMs = cooldownMs;
        }
    }

    /** 暴风式 24 剑穿梭顺序（与剑圣 1 觉一致的上下交替对角穿梭算法）。 */
    public static int[] stormOrder(long seed) {
        int[] result = new int[SKILL3_SWORDS];
        boolean[] used = new boolean[SKILL3_SWORDS];
        SplittableRandom rng = new SplittableRandom(seed);
        result[0] = rng.nextInt(12);
        used[result[0]] = true;
        for (int step = 1; step < SKILL3_SWORDS; step++) {
            int offset = step % 2 == 1 ? 12 : 0;
            int chosen = -1;
            int ties = 0;
            double best = Double.POSITIVE_INFINITY;
            double previous = stormAngle(result[step - 1]);
            int shift = (rng.nextBoolean() ? 1 : -1) * (1 + rng.nextInt(2));
            double desired = previous + Math.PI + shift * Math.PI / 6.0;
            for (int i = offset; i < offset + 12; i++) {
                if (used[i]) continue;
                double delta = Math.abs(Math.atan2(Math.sin(stormAngle(i) - desired), Math.cos(stormAngle(i) - desired)));
                if (delta < best - 1e-9) {
                    best = delta;
                    chosen = i;
                    ties = 1;
                } else if (Math.abs(delta - best) < 1e-9 && rng.nextInt(++ties) == 0) {
                    chosen = i;
                }
            }
            result[step] = chosen;
            used[chosen] = true;
        }
        return result;
    }

    public static double stormAngle(int index) {
        return (index % 12) * Math.PI / 6.0 + (index >= 12 ? Math.PI / 12.0 : 0.0);
    }

    /** 第 index 把神剑相对剑阵中心的局部偏移（0..11 为上环，12..23 为下环）。 */
    public static Vec3 stormSwordOffset(int index) {
        double angle = stormAngle(index);
        return new Vec3(
            Math.cos(angle) * SKILL3_RADIUS,
            index < 12 ? SKILL3_UPPER_HEIGHT : 0.0,
            Math.sin(angle) * SKILL3_RADIUS
        );
    }

    /** 暴风式指定时间点已斩落的剑数（用于服务端与客户端统一进度）。 */
    public static int stormCompletedSwords(long elapsedMs, long deployMs, long slashIntervalMs) {
        if (elapsedMs < deployMs) return 0;
        long interval = Math.max(1L, slashIntervalMs);
        return (int) Math.clamp((elapsedMs - deployMs) / interval + 1L, 0, SKILL3_SWORDS);
    }

    /** 暴风式指定时间点首领相对剑阵中心的穿梭位置。 */
    public static Vec3 stormActorOffset(long seed, long elapsedMs, long deployMs, long slashIntervalMs) {
        int[] order = stormOrder(seed);
        long leadIn = Math.max(150L, deployMs / 5L);
        long moveStart = Math.max(0L, deployMs - leadIn);
        if (elapsedMs < moveStart) return Vec3.ZERO;
        if (elapsedMs < deployMs) {
            double t = smooth((elapsedMs - moveStart) / (double) Math.max(1L, deployMs - moveStart));
            return Vec3.ZERO.lerp(stormSwordOffset(order[0]), t);
        }
        long interval = Math.max(1L, slashIntervalMs);
        long slashEnd = deployMs + interval * SKILL3_SWORDS;
        if (elapsedMs >= slashEnd) return Vec3.ZERO;
        int index = (int) Math.clamp((elapsedMs - deployMs) / interval, 0, SKILL3_SWORDS - 1);
        double t = smooth(((elapsedMs - deployMs) % interval) / (double) interval);
        Vec3 from = stormSwordOffset(order[index]);
        Vec3 to = index == SKILL3_SWORDS - 1 ? Vec3.ZERO : stormSwordOffset(order[index + 1]);
        return from.lerp(to, t);
    }

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private TheaterTowerLiangyue() {}
}

package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;

/**
 * 封印塔第50层专属首领「伊蕾娜」的阶段门槛、数值评级与 12 项技能参数集中定义。
 */
public final class SealTowerElaina {
    public static final String ID = "seal_tower_elaina";
    public static final String DISPLAY_NAME = "伊蕾娜";

    public static final double CAST_TIME_MULTIPLIER = 1.5;
    private static final double COOLDOWN_MULTIPLIER = 1.5;

    // 基础面板评级
    public static final TowerBossGrade HEALTH_GRADE = TowerBossGrade.E; // 1.0
    public static final double HEALTH_MULTIPLIER = HEALTH_GRADE.multiplier() * 0.5;
    public static final TowerBossGrade ATTACK_GRADE = TowerBossGrade.S; // 2.0

    // 元素编码（沿用 ElementAccumulation）
    public static final int ELEMENT_NONE = -1;
    public static final int ELEMENT_LIGHT = 0;
    public static final int ELEMENT_FIRE = 1;
    public static final int ELEMENT_ICE = 2;
    public static final int ELEMENT_DARK = 3;

    // 统一伤害档位倍率（乘 Target.attack(level)）
    public static final double COEFF_LOW_DOT = 0.20;
    public static final double COEFF_MEDIUM = 0.90;
    public static final double COEFF_HIGH = 1.40;

    // 阶段锁血线与演出时长
    public static final double FLOOR_P1 = 0.80;
    public static final double FLOOR_P2 = 0.50;
    public static final double FLOOR_P3 = 0.20;

    public static final long TRANSITION_80_DURATION_MS = 10_000L;
    public static final long TRANSITION_80_ALL_CD_MS = 100_000L;

    public static final long TRANSITION_50_DURATION_MS = castTime(15_000L);
    public static final long MIDNIGHT_TIME_TICKS = 18_000L;
    public static final long MIDNIGHT_RANDOM_METEOR_INTERVAL_MS = castTime(333L);
    public static final long MIDNIGHT_TARGETED_METEOR_INTERVAL_MS = castTime(500L);
    public static final long MIDNIGHT_METEOR_WARNING_MS = castTime(1_500L);
    public static final double MIDNIGHT_METEOR_RADIUS = 2.5;

    public static final long TRANSITION_20_DURATION_MS = castTime(4_000L);

    public static final long FINALE_METEOR_1_WARNING_MS = castTime(15_000L);
    public static final double FINALE_METEOR_1_EDGE_DISTANCE = 48.0;
    public static final double FINALE_METEOR_1_CENTER_RATIO = 1.00;
    public static final double FINALE_METEOR_1_EDGE_RATIO = 0.50;

    public static final long FINALE_DPS_RACE_MS = castTime(30_000L);
    public static final double FINALE_SHIELD_FRACTION = 0.10;

    // 技能 1：魔杖四连冲锋
    public static final long SKILL1_CD_MS = cooldown(14_000L);
    public static final int SKILL1_SEGMENTS = 4;
    public static final double SKILL1_RING_RADIUS = 30.0;
    public static final double SKILL1_SPEED = 30.0 / CAST_TIME_MULTIPLIER;
    public static final long SKILL1_DASH_MS = castTime(1_000L);
    public static final long SKILL1_PAUSE_MS = castTime(200L);
    public static final double SKILL1_OVERSHOOT = 4.0;
    public static final double SKILL1_WIDTH = 2.0;
    public static final double SKILL1_DAMAGE_TAKEN_MULTIPLIER = 0.10; // 90% 减伤

    // 技能 2：追踪星辰
    public static final long SKILL2_CD_MS = cooldown(35_000L);
    public static final long SKILL2_WARNING_MS = castTime(2_000L);
    public static final long SKILL2_DURATION_MS = castTime(3_000L);
    public static final long SKILL2_TICK_MS = castTime(500L);
    public static final double SKILL2_SPEED = 2.0 / CAST_TIME_MULTIPLIER;
    public static final double SKILL2_RADIUS = 2.5;
    /** 周期性位置同步间隔（>= 250ms / 5 ticks，遵守服务器最多每秒 4 次同步红线）。 */
    public static final long SKILL2_SYNC_INTERVAL_MS = 250L;

    // 技能 3：三元素坠球
    public static final long SKILL3_CD_MS = cooldown(16_000L);
    public static final double SKILL3_HOVER_OFFSET = 6.0;
    public static final long SKILL3_CAST_MS = castTime(3_000L);
    public static final long SKILL3_SPAWN_INTERVAL_MS = castTime(250L);
    public static final int SKILL3_ORB_COUNT = 12;
    public static final double SKILL3_SCATTER_RADIUS = 8.0;
    public static final long SKILL3_ORB_WARNING_MS = castTime(5_000L);
    public static final double SKILL3_ORB_RADIUS = 2.8;

    // 技能 4：突发闪现飞行
    public static final long SKILL4_CD_MS = cooldown(7_000L);
    public static final long SKILL4_WINDUP_MS = castTime(150L);
    public static final long SKILL4_FLIGHT_MS = castTime(250L);
    public static final double SKILL4_DISTANCE = 12.0;

    // 技能 5：饮用生命药水
    public static final long SKILL5_CD_MS = cooldown(40_000L);
    public static final long SKILL5_CAST_MS = castTime(1_200L);
    public static final long SKILL5_HEAL_FRAME_MS = castTime(800L);
    public static final double SKILL5_HEAL_FRACTION = 0.05;

    // 技能 6：巫师护盾（被动）
    public static final double SKILL6_PASSIVE_SHIELD_FRACTION = 0.05;

    // 技能 7：二十四连日冕
    public static final long SKILL7_CD_MS = cooldown(28_000L);
    public static final long SKILL7_SPAWN_INTERVAL_MS = castTime(250L);
    public static final int SKILL7_COUNT = 24;
    public static final long SKILL7_DELAY_MS = castTime(4_000L);
    public static final double SKILL7_RISE_HEIGHT = 2.0;
    public static final double SKILL7_RADIUS = 4.0;

    // 技能 8：元素师觉醒脱手版＋绕玩家头顶盘旋飞行
    public static final long SKILL8_CD_MS = cooldown(45_000L);
    public static final double SKILL8_HOVER_OFFSET = 5.0;
    public static final double SKILL8_ORBIT_RADIUS = 6.0;
    public static final long SKILL8_DURATION_MS = castTime(4_000L);
    public static final long SKILL8_SPAWN_INTERVAL_MS = castTime(250L);
    public static final int SKILL8_METEOR_COUNT = 16;
    public static final double SKILL8_FIELD_RADIUS = 12.0;
    public static final long SKILL8_METEOR_WARNING_MS = castTime(2_000L);
    public static final double SKILL8_METEOR_RADIUS = 3.5;

    // 技能 9：元素师三连施法（随机 3 个不重复元素师技能，各自独立预警）
    public static final long SKILL9_CD_MS = cooldown(18_000L);
    public static final long SKILL9_CAST_MS = castTime(1_500L);
    public static final long SKILL9_INTERVAL_MS = castTime(500L);
    public static final int SKILL9_COUNT = 3;
    public static final long SKILL9_WARNING_MS = castTime(3_000L);
    public static final double SKILL9_JACK_RADIUS = 4.5;
    public static final double SKILL9_ARCTIC_RADIUS = 5.5;
    public static final double SKILL9_BLACKHOLE_RADIUS = 5.0;
    public static final double SKILL9_THUNDER_RADIUS = 3.5;

    // 技能 10：时钟锚点回溯
    public static final long SKILL10_CD_MS = cooldown(32_000L);
    public static final long SKILL10_DELAY_MS = castTime(5_000L);
    public static final long SKILL10_ZERO_CD_FALLBACK_MS = 15_000L;

    // 技能 11：时之封印（软狂暴）
    public static final long SKILL11_CD_MS = cooldown(180_000L);
    public static final long SKILL11_CAST_MS = castTime(1_000L);
    public static final long SKILL11_SEAL_DURATION_MS = 999_999_000L;
    /** 服务端双保险清理阈值：超出正常技能冷却上限（300 秒）视为伊蕾娜场次软狂暴封印冷却。 */
    public static final long SERVER_SEAL_CLEANUP_THRESHOLD_MS = 300_000L;

    // 技能 12：空中回旋南瓜爆弹
    public static final long SKILL12_CD_MS = cooldown(12_000L);
    public static final double SKILL12_HOVER_OFFSET = 5.0;
    public static final double SKILL12_ORBIT_RADIUS = 10.0;
    public static final long SKILL12_DURATION_MS = castTime(4_000L);
    public static final long SKILL12_THROW_INTERVAL_MS = castTime(250L);
    public static final int SKILL12_BOMB_COUNT = 16;
    public static final double SKILL12_SPREAD = 3.5;
    public static final long SKILL12_BOMB_WARNING_MS = castTime(900L);
    public static final double SKILL12_BOMB_RADIUS = 3.0;

    /** 12 项技能稳定枚举。 */
    public enum SkillId {
        WAND_CHARGE("wand_charge", SKILL1_CD_MS, true),
        TRACKING_STAR("tracking_star", SKILL2_CD_MS, true),
        ELEMENTAL_ORBS("elemental_orbs", SKILL3_CD_MS, true),
        BLINK_FLIGHT("blink_flight", SKILL4_CD_MS, true),
        DRINK_POTION("drink_potion", SKILL5_CD_MS, true),
        WIZARD_SHIELD("wizard_shield", 0L, false), // 被动技能，不参与主动选招
        CORONA_BARRAGE("corona_barrage", SKILL7_CD_MS, true),
        ELEMENTALIST_AWAKENING("corona_flight", SKILL8_CD_MS, true),
        ELEMENTALIST_TRIPLE_CAST("elementalist_triple_cast", SKILL9_CD_MS, true),
        CLOCK_REWIND("clock_rewind", SKILL10_CD_MS, true),
        TIME_SEAL("time_seal", SKILL11_CD_MS, true),
        PUMPKIN_SPIN("pumpkin_spin", SKILL12_CD_MS, true);

        public final String id;
        public final long cooldownMs;
        public final boolean activeSelectable;

        SkillId(String id, long cooldownMs, boolean activeSelectable) {
            this.id = id;
            this.cooldownMs = cooldownMs;
            this.activeSelectable = activeSelectable;
        }
    }

    private static long cooldown(long baseMillis) {
        return Math.round(baseMillis * COOLDOWN_MULTIPLIER);
    }

    public static long castTime(long baseMillis) {
        return Math.round(baseMillis * CAST_TIME_MULTIPLIER);
    }

    /** 计算终局第 1 枚巨型陨石按水平距离线性衰减的最大生命伤害比例（中心 1.0 -> 48 格边缘 0.5）。 */
    public static double finaleMeteor1Ratio(double horizontalDistance) {
        double t = Math.clamp(horizontalDistance / FINALE_METEOR_1_EDGE_DISTANCE, 0.0, 1.0);
        return FINALE_METEOR_1_CENTER_RATIO - (FINALE_METEOR_1_CENTER_RATIO - FINALE_METEOR_1_EDGE_RATIO) * t;
    }

    private SealTowerElaina() {}
}

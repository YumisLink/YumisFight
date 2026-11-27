package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * 水闸塔第 20 层专属首领「洛琪希」的面板评级、抗性配置、双阶段《豪雷积雨云》转场破核博弈与 7 项水雷法术参数集中定义。
 * <p>
 * 面板定位：平衡大法师档（单命 HP C=1.4 ×2 管血 / ATK B=1.6，二阶段 ×1.25 / 冰抗 65、光抗 35、火抗 -15、暗抗 0），
 * 全程适配水闸塔 0.5× 攻速与施法速率的慢节奏精密走位战。
 */
public final class SluiceTowerRoxy {
    public static final String ID = "sluice_tower_roxy";
    public static final String DISPLAY_NAME = "洛琪希";
    public static final String HYDRO_CORE_MECHANISM_ID = "roxy_hydro_core";

    // 基础面板评级：HP C (1.4) / ATK B (1.6)
    public static final TowerBossGrade HEALTH_GRADE = TowerBossGrade.C;
    public static final TowerBossGrade ATTACK_GRADE = TowerBossGrade.B;

    // 元素抗性：高冰（水）抗、中光（雷）抗、火属性弱点
    public static final double LIGHT_RESIST = BossBalanceRules.current().towerNum(ID, "LIGHT_RESIST", 35.0);
    public static final double FIRE_RESIST = BossBalanceRules.current().towerNum(ID, "FIRE_RESIST", -15.0);
    public static final double ICE_RESIST = BossBalanceRules.current().towerNum(ID, "ICE_RESIST", 65.0);
    public static final double DARK_RESIST = BossBalanceRules.current().towerNum(ID, "DARK_RESIST", 0.0);

    // 元素编码（沿用 ElementAccumulation：0=光/雷，2=冰/水）
    public static final int ELEMENT_LIGHT = 0;
    public static final int ELEMENT_ICE = 2;

    // 双阶段与转场破核博弈参数
    public static final int ENCOUNTER_LIVES = BossBalanceRules.current().towerInt(ID, "ENCOUNTER_LIVES", 2);
    public static final double PHASE2_ATTACK_MULTIPLIER = BossBalanceRules.current().towerNum(ID, "PHASE2_ATTACK_MULTIPLIER", 1.25);

    // 12 秒《水王级·豪雷积雨云》转场时间轴
    public static final long TRANSITION_TOTAL_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_TOTAL_MS", 12_000L);
    public static final long TRANSITION_KNEEL_END_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_KNEEL_END_MS", 1_200L);
    public static final long TRANSITION_RISE_END_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_RISE_END_MS", 3_000L);
    public static final long TRANSITION_DESCEND_START_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_DESCEND_START_MS", 10_800L);
    public static final double TRANSITION_HOVER_HEIGHT = BossBalanceRules.current().towerNum(ID, "TRANSITION_HOVER_HEIGHT", 7.5);
    public static final long TRANSITION_CORE_SPAWN_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_CORE_SPAWN_MS", 2_200L);
    public static final int TRANSITION_CORE_COUNT = BossBalanceRules.current().towerInt(ID, "TRANSITION_CORE_COUNT", 3);
    public static final double TRANSITION_CORE_RING_RADIUS = BossBalanceRules.current().towerNum(ID, "TRANSITION_CORE_RING_RADIUS", 13.0);
    public static final double CORE_SPECIES_HEALTH = BossBalanceRules.current().towerNum(ID, "CORE_SPECIES_HEALTH", 0.85);
    public static final double SHIELD_PER_REMAINING_CORE = BossBalanceRules.current().towerNum(ID, "SHIELD_PER_REMAINING_CORE", 0.06);
    public static final long ALL_CORES_BROKEN_VULNERABLE_MS = BossBalanceRules.current().towerMs(ID, "ALL_CORES_BROKEN_VULNERABLE_MS", 3_000L);
    public static final double ALL_CORES_BROKEN_DAMAGE_TAKEN = BossBalanceRules.current().towerNum(ID, "ALL_CORES_BROKEN_DAMAGE_TAKEN", 1.25);
    public static final double STORM_CD_SCALE_ALL_BROKEN = BossBalanceRules.current().towerNum(ID, "STORM_CD_SCALE_ALL_BROKEN", 1.15);
    public static final long TRANSITION_BOLT_INTERVAL_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_BOLT_INTERVAL_MS", 1_800L);
    public static final int TRANSITION_BOLT_COUNT = BossBalanceRules.current().towerInt(ID, "TRANSITION_BOLT_COUNT", 2);
    public static final long TRANSITION_BOLT_WARNING_MS = BossBalanceRules.current().towerMs(ID, "TRANSITION_BOLT_WARNING_MS", 2_030L);
    public static final double TRANSITION_BOLT_RADIUS = BossBalanceRules.current().towerNum(ID, "TRANSITION_BOLT_RADIUS", 2.8);
    public static final double COEFF_TRANSITION_BOLT = BossBalanceRules.current().towerNum(ID, "COEFF_TRANSITION_BOLT", 0.55);
    public static final double TRANSITION_LAND_RADIUS = BossBalanceRules.current().towerNum(ID, "TRANSITION_LAND_RADIUS", 11.0);
    public static final double COEFF_TRANSITION_LAND = BossBalanceRules.current().towerNum(ID, "COEFF_TRANSITION_LAND", 0.65);

    // 暴风雨天空时间刻度（深海靛蓝雷暴暮夜，区别于伊蕾娜纯黑午夜 18000）
    public static final long STORM_SKY_TIME_TICKS = BossBalanceRules.current().towerMs(ID, "STORM_SKY_TIME_TICKS", 15_800L);

    // 技能 1：水圣弹幕（背后弧阵凝结水枪连射，CD 12s）
    public static final long SKILL1_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL1_CD_MS", 12_000L);
    public static final long SKILL1_WINDUP_MS = BossBalanceRules.current().towerMs(ID, "SKILL1_WINDUP_MS", 1_740L);
    public static final long SKILL1_LAUNCH_INTERVAL_MS = BossBalanceRules.current().towerMs(ID, "SKILL1_LAUNCH_INTERVAL_MS", 110L);
    public static final int SKILL1_COUNT_P1 = BossBalanceRules.current().towerInt(ID, "SKILL1_COUNT_P1", 10);
    public static final int SKILL1_COUNT_P2 = BossBalanceRules.current().towerInt(ID, "SKILL1_COUNT_P2", 14);
    public static final double SKILL1_SPEED = BossBalanceRules.current().towerNum(ID, "SKILL1_SPEED", 22.0);
    public static final double SKILL1_TURN_DEG_PER_SEC = BossBalanceRules.current().towerNum(ID, "SKILL1_TURN_DEG_PER_SEC", 28.0);
    public static final double SKILL1_HIT_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL1_HIT_RADIUS", 0.78);
    public static final long SKILL1_LIFE_MS = BossBalanceRules.current().towerMs(ID, "SKILL1_LIFE_MS", 2_800L);
    public static final int SKILL1_MAX_HITS_PER_CAST = BossBalanceRules.current().towerInt(ID, "SKILL1_MAX_HITS_PER_CAST", 3);
    public static final double COEFF_WATER_BARRAGE = BossBalanceRules.current().towerNum(ID, "COEFF_WATER_BARRAGE", 0.34);
    public static final long SKILL1_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL1_RECOVERY_MS", 800L);

    // 技能 2：高压水刃（宽幅月牙高压水刃波，二阶段三连扇形，CD 10.5s）
    public static final long SKILL2_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL2_CD_MS", 10_500L);
    public static final long SKILL2_WINDUP_MS = BossBalanceRules.current().towerMs(ID, "SKILL2_WINDUP_MS", 1_815L);
    public static final long SKILL2_TRAVEL_MS = BossBalanceRules.current().towerMs(ID, "SKILL2_TRAVEL_MS", 800L);
    public static final double SKILL2_RANGE = BossBalanceRules.current().towerNum(ID, "SKILL2_RANGE", 26.0);
    public static final double SKILL2_WIDTH = BossBalanceRules.current().towerNum(ID, "SKILL2_WIDTH", 4.4);
    public static final double[] SKILL2_P2_ANGLES_DEG = BossBalanceRules.current().towerArray(ID, "SKILL2_P2_ANGLES_DEG", new double[]{-24.0, 0.0, 24.0});
    public static final double COEFF_AQUA_BLADE = BossBalanceRules.current().towerNum(ID, "COEFF_AQUA_BLADE", 0.95);
    public static final long SKILL2_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL2_RECOVERY_MS", 850L);

    // 技能 3：激流喷涌（连环两波冲天高压水柱，CD 14s）
    public static final long SKILL3_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL3_CD_MS", 14_000L);
    public static final long SKILL3_CAST_MS = BossBalanceRules.current().towerMs(ID, "SKILL3_CAST_MS", 1_380L);
    public static final int SKILL3_WAVES = BossBalanceRules.current().towerInt(ID, "SKILL3_WAVES", 2);
    public static final long SKILL3_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs(ID, "SKILL3_WAVE_INTERVAL_MS", 1_350L);
    public static final int SKILL3_COUNT_P1 = BossBalanceRules.current().towerInt(ID, "SKILL3_COUNT_P1", 4);
    public static final int SKILL3_COUNT_P2 = BossBalanceRules.current().towerInt(ID, "SKILL3_COUNT_P2", 6);
    public static final long SKILL3_WARNING_MS = BossBalanceRules.current().towerMs(ID, "SKILL3_WARNING_MS", 2_250L);
    public static final double SKILL3_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL3_RADIUS", 3.6);
    public static final double COEFF_GEYSER = BossBalanceRules.current().towerNum(ID, "COEFF_GEYSER", 0.92);
    public static final long SKILL3_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL3_RECOVERY_MS", 850L);

    // 技能 4：水镜折跃（后撤拉开身距 + 原位水镜延时绽放与径向水箭，CD 13s）
    public static final long SKILL4_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_CD_MS", 13_000L);
    public static final long SKILL4_WINDUP_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_WINDUP_MS", 655L);
    public static final long SKILL4_BLINK_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_BLINK_MS", 400L);
    public static final double SKILL4_DISTANCE = BossBalanceRules.current().towerNum(ID, "SKILL4_DISTANCE", 12.0);
    public static final long SKILL4_MIRROR_DELAY_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_MIRROR_DELAY_MS", 1_670L);
    public static final double SKILL4_MIRROR_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL4_MIRROR_RADIUS", 4.2);
    public static final int SKILL4_BOLTS_P1 = BossBalanceRules.current().towerInt(ID, "SKILL4_BOLTS_P1", 6);
    public static final int SKILL4_BOLTS_P2 = BossBalanceRules.current().towerInt(ID, "SKILL4_BOLTS_P2", 8);
    public static final double SKILL4_BOLT_SPEED = BossBalanceRules.current().towerNum(ID, "SKILL4_BOLT_SPEED", 17.5);
    public static final long SKILL4_BOLT_LIFE_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_BOLT_LIFE_MS", 2_400L);
    public static final double COEFF_MIRROR_BURST = BossBalanceRules.current().towerNum(ID, "COEFF_MIRROR_BURST", 0.78);
    public static final double COEFF_MIRROR_BOLT = BossBalanceRules.current().towerNum(ID, "COEFF_MIRROR_BOLT", 0.45);
    public static final long SKILL4_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL4_RECOVERY_MS", 750L);

    // 技能 5：双层潮汐环（先内圈再外圈，二阶段追加回涌内环，经典先出后进走位，CD 16s）
    public static final long SKILL5_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL5_CD_MS", 16_000L);
    public static final long SKILL5_WINDUP_MS = BossBalanceRules.current().towerMs(ID, "SKILL5_WINDUP_MS", 1_960L);
    public static final double SKILL5_INNER_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL5_INNER_RADIUS", 9.5);
    public static final double SKILL5_OUTER_MIN_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL5_OUTER_MIN_RADIUS", 8.5);
    public static final double SKILL5_OUTER_MAX_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL5_OUTER_MAX_RADIUS", 20.0);
    public static final long SKILL5_RING_GAP_MS = BossBalanceRules.current().towerMs(ID, "SKILL5_RING_GAP_MS", 2_105L);
    public static final double COEFF_TIDAL_INNER = BossBalanceRules.current().towerNum(ID, "COEFF_TIDAL_INNER", 1.05);
    public static final double COEFF_TIDAL_OUTER = BossBalanceRules.current().towerNum(ID, "COEFF_TIDAL_OUTER", 0.95);
    public static final long SKILL5_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL5_RECOVERY_MS", 900L);

    // 技能 6（二阶段专属）：豪雷落瀑（3 波连环天穹雷柱轰击，CD 15s）
    public static final long SKILL6_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL6_CD_MS", 15_000L);
    public static final long SKILL6_CAST_MS = BossBalanceRules.current().towerMs(ID, "SKILL6_CAST_MS", 1_305L);
    public static final int SKILL6_WAVES = BossBalanceRules.current().towerInt(ID, "SKILL6_WAVES", 3);
    public static final long SKILL6_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs(ID, "SKILL6_WAVE_INTERVAL_MS", 1_250L);
    public static final int SKILL6_BOLTS_PER_WAVE = BossBalanceRules.current().towerInt(ID, "SKILL6_BOLTS_PER_WAVE", 5);
    public static final long SKILL6_WARNING_MS = BossBalanceRules.current().towerMs(ID, "SKILL6_WARNING_MS", 1_960L);
    public static final double SKILL6_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL6_RADIUS", 3.2);
    public static final double COEFF_THUNDER_CASCADE = BossBalanceRules.current().towerNum(ID, "COEFF_THUNDER_CASCADE", 0.98);
    public static final long SKILL6_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL6_RECOVERY_MS", 900L);

    // 技能 7（二阶段史诗循环大招）：天穹雷狱·台风眼（移动晴空风暴眼走位生存，CD 38s）
    public static final long SKILL7_CD_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_CD_MS", 38_000L);
    public static final double SKILL7_HOVER_HEIGHT = BossBalanceRules.current().towerNum(ID, "SKILL7_HOVER_HEIGHT", 7.0);
    public static final long SKILL7_WINDUP_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_WINDUP_MS", 2_610L);
    public static final long SKILL7_DURATION_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_DURATION_MS", 8_500L);
    public static final double SKILL7_EYE_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL7_EYE_RADIUS", 5.5);
    public static final double SKILL7_ORBIT_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL7_ORBIT_RADIUS", 15.0);
    public static final double SKILL7_ORBIT_SWEEP_DEG = BossBalanceRules.current().towerNum(ID, "SKILL7_ORBIT_SWEEP_DEG", 200.0);
    public static final long SKILL7_OUTSIDE_TICK_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_OUTSIDE_TICK_MS", 750L);
    public static final double COEFF_TYPHOON_OUTSIDE = BossBalanceRules.current().towerNum(ID, "COEFF_TYPHOON_OUTSIDE", 0.52);
    public static final long SKILL7_INNER_STRIKE_INTERVAL_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_INNER_STRIKE_INTERVAL_MS", 2_400L);
    public static final long SKILL7_INNER_STRIKE_WARNING_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_INNER_STRIKE_WARNING_MS", 1_960L);
    public static final double SKILL7_INNER_STRIKE_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL7_INNER_STRIKE_RADIUS", 2.4);
    public static final double COEFF_TYPHOON_INNER = BossBalanceRules.current().towerNum(ID, "COEFF_TYPHOON_INNER", 0.72);
    public static final double SKILL7_FINALE_RADIUS = BossBalanceRules.current().towerNum(ID, "SKILL7_FINALE_RADIUS", 9.0);
    public static final double COEFF_TYPHOON_FINALE = BossBalanceRules.current().towerNum(ID, "COEFF_TYPHOON_FINALE", 0.85);
    public static final long SKILL7_LAND_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_LAND_MS", 870L);
    public static final long SKILL7_RECOVERY_MS = BossBalanceRules.current().towerMs(ID, "SKILL7_RECOVERY_MS", 2_000L);

    // 水闸塔四角水池相对场地中心的偏移（对应 tools/build_tower_structures.py sluice_arena 四角水池质心）
    public static final double CORNER_POOL_OFFSET = 23.0;

    public enum SkillId {
        WATER_BARRAGE("water_barrage", SKILL1_CD_MS, false),
        AQUA_BLADE("aqua_blade", SKILL2_CD_MS, false),
        GEYSER_ERUPTION("geyser_eruption", SKILL3_CD_MS, false),
        MIRROR_BLINK("mirror_blink", SKILL4_CD_MS, false),
        TIDAL_RINGS("tidal_rings", SKILL5_CD_MS, false),
        THUNDER_CASCADE("thunder_cascade", SKILL6_CD_MS, true),
        TYPHOON_EYE("typhoon_eye", SKILL7_CD_MS, true);

        public final String id;
        public final long cooldownMs;
        public final boolean phase2Only;

        SkillId(String id, long cooldownMs, boolean phase2Only) {
            this.id = id;
            this.cooldownMs = cooldownMs;
            this.phase2Only = phase2Only;
        }
    }

    public static double[] initialResists() {
        return new double[]{LIGHT_RESIST, FIRE_RESIST, ICE_RESIST, DARK_RESIST};
    }

    public static int barrageCount(boolean phase2) {
        return phase2 ? SKILL1_COUNT_P2 : SKILL1_COUNT_P1;
    }

    public static int geyserCount(boolean phase2) {
        return phase2 ? SKILL3_COUNT_P2 : SKILL3_COUNT_P1;
    }

    public static int mirrorBoltCount(boolean phase2) {
        return phase2 ? SKILL4_BOLTS_P2 : SKILL4_BOLTS_P1;
    }

    /** 根据 12 秒转场期间击破的水魔导核心数量（0~3），计算二阶段开场水幕护盾比例。 */
    public static double remainingCoreShield(int brokenCores) {
        int remaining = Math.clamp(TRANSITION_CORE_COUNT - brokenCores, 0, TRANSITION_CORE_COUNT);
        return remaining * SHIELD_PER_REMAINING_CORE;
    }

    /** 3 枚水核全破时缓和二阶段暴风雨技能冷却节奏（×1.15）。 */
    public static double phase2CooldownScale(int brokenCores) {
        return brokenCores >= TRANSITION_CORE_COUNT ? STORM_CD_SCALE_ALL_BROKEN : 1.0;
    }

    /** 12 秒《豪雷积雨云》转场期间洛琪希的浮空高度曲线：0~1.2s 半跪地面 -> 1.2~3.0s 升空 -> 3.0~10.8s 高空咏唱微浮 -> 10.8~12.0s 落地。 */
    public static double transitionHeightOffset(long elapsedMs) {
        if (elapsedMs <= TRANSITION_KNEEL_END_MS) return 0.0;
        if (elapsedMs < TRANSITION_RISE_END_MS) {
            double t = (elapsedMs - TRANSITION_KNEEL_END_MS) / (double) Math.max(1L, TRANSITION_RISE_END_MS - TRANSITION_KNEEL_END_MS);
            return TRANSITION_HOVER_HEIGHT * smooth(t);
        }
        if (elapsedMs < TRANSITION_DESCEND_START_MS) {
            double bob = Math.sin((elapsedMs - TRANSITION_RISE_END_MS) * 0.0032) * 0.35;
            return TRANSITION_HOVER_HEIGHT + bob;
        }
        if (elapsedMs < TRANSITION_TOTAL_MS) {
            double t = (elapsedMs - TRANSITION_DESCEND_START_MS) / (double) Math.max(1L, TRANSITION_TOTAL_MS - TRANSITION_DESCEND_START_MS);
            return TRANSITION_HOVER_HEIGHT * (1.0 - t * t);
        }
        return 0.0;
    }

    /** 返回水闸塔四角蓄水池中心坐标（NW、NE、SW、SE），用于转场与暴雨冲天水柱奇观。 */
    public static List<Vec3> cornerPoolCenters(int cell) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        double o = CORNER_POOL_OFFSET;
        return List.of(
            center.add(-o, -0.5, -o),
            center.add(o, -0.5, -o),
            center.add(-o, -0.5, o),
            center.add(o, -0.5, o)
        );
    }

    /** 计算 3 枚「水魔导核心」在中央八角主平台上的等边三角生成坐标。 */
    public static List<Vec3> hydroCorePositions(int cell, double angleOffsetRad) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        int count = Math.max(1, TRANSITION_CORE_COUNT);
        List<Vec3> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double ang = angleOffsetRad + i * (Math.PI * 2.0 / count);
            positions.add(center.add(
                Math.cos(ang) * TRANSITION_CORE_RING_RADIUS,
                0.0,
                Math.sin(ang) * TRANSITION_CORE_RING_RADIUS
            ));
        }
        return positions;
    }

    /** 计算《天穹雷狱·台风眼》在激活经过 elapsedActiveMs 后的平滑移动“晴空风暴眼”圆心位置。 */
    public static Vec3 typhoonEyePosition(int cell, double startAngleRad, double sweepSign, long elapsedActiveMs) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        double progress = Math.clamp(elapsedActiveMs / (double) Math.max(1L, SKILL7_DURATION_MS), 0.0, 1.0);
        double dir = sweepSign < 0.0 ? -1.0 : 1.0;
        double angle = startAngleRad + dir * Math.toRadians(SKILL7_ORBIT_SWEEP_DEG) * progress;
        // 半径在 12.5 ~ 15.5 之间柔和呼吸，始终稳落在中央八角宽阔平台与四向桥口范围内
        double r = SKILL7_ORBIT_RADIUS - 1.5 * Math.sin(progress * Math.PI);
        return new Vec3(
            center.x + Math.cos(angle) * r,
            center.y,
            center.z + Math.sin(angle) * r
        );
    }

    /** 校验玩家水平位置是否位于台风眼晴空安全区内（含微小 0.35 格容错边距）。 */
    public static boolean insideTyphoonEye(Vec3 playerPos, Vec3 eyeCenter, double eyeRadius) {
        if (playerPos == null || eyeCenter == null) return false;
        double dx = playerPos.x - eyeCenter.x;
        double dz = playerPos.z - eyeCenter.z;
        double allowed = eyeRadius + 0.35;
        return dx * dx + dz * dz <= allowed * allowed;
    }

    /** 背后水圣弹幕双弧槽位偏移（right, up, back）。 */
    public static double[] backOrbSlotOffset(int index, int total) {
        int outerCount = Math.max(1, (total + 1) / 2);
        boolean outer = index < outerCount;
        int ringIndex = outer ? index : index - outerCount;
        int ringTotal = outer ? outerCount : Math.max(1, total - outerCount);
        double frac = ringTotal <= 1 ? 0.5 : ringIndex / (double) (ringTotal - 1);
        double angle = -Math.PI * 0.62 + frac * Math.PI * 1.24;
        double radius = outer ? 2.15 : 1.42;
        double right = Math.sin(angle) * radius;
        double up = 1.55 + Math.cos(angle) * radius * 0.72 + (outer ? 0.25 : 0.0);
        double back = outer ? 0.95 : 0.62;
        return new double[]{right, up, back};
    }

    /** 球面线性插值（SLERP）平滑转向，供水圣弹幕温和弧线追踪。 */
    public static Vec3 stepHomingDirection(Vec3 currentDir, Vec3 toTarget, double maxTurnDegPerSec, double dtSec) {
        Vec3 cur = currentDir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : currentDir.normalize();
        if (dtSec <= 0.0 || toTarget.lengthSqr() < 1e-8) return cur;
        Vec3 desired = toTarget.normalize();
        double dot = Math.clamp(cur.dot(desired), -1.0, 1.0);
        double angle = Math.acos(dot);
        double maxStep = Math.toRadians(Math.max(0.0, maxTurnDegPerSec)) * dtSec;
        if (angle <= maxStep || angle < 1e-6) return desired;
        double t = maxStep / angle;
        double sinAngle = Math.sin(angle);
        if (sinAngle < 1e-6) return cur;
        double w0 = Math.sin((1.0 - t) * angle) / sinAngle;
        double w1 = Math.sin(t * angle) / sinAngle;
        return cur.scale(w0).add(desired.scale(w1)).normalize();
    }

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private SluiceTowerRoxy() {}
}

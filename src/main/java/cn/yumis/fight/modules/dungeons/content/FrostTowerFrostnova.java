package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;
import net.minecraft.world.phys.Vec3;

/**
 * 霜塔第 20 层专属首领「霜星」的面板评级、抗性配置、双阶段强化规则与 6 项技能参数集中定义。
 * <p>
 * 面板定位：HP S (2.0) / 攻击力 B (1.6) / 超高冰抗 (90) / 超高魔防 (4500) / 两阶段复活。
 * 二阶段强化：攻击力 +50%、所有技能冰刺数量 +30%、爆发与冰阵范围 +30%，并解锁专属大招「极寒星爆」。
 */
public final class FrostTowerFrostnova {
    public static final String ID = "frost_tower_frostnova";
    public static final String DISPLAY_NAME = "霜星";

    // 基础面板评级：HP S (2.0) / 攻击力 B (1.6)
    public static final TowerBossGrade HEALTH_GRADE = TowerBossGrade.S;
    public static final TowerBossGrade ATTACK_GRADE = TowerBossGrade.B;

    // 抗性与防御：超高冰抗 + 超高法抗（保留火属性小幅弱点与无属性保底，防止单人构筑死锁）
    public static final double LIGHT_RESIST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "LIGHT_RESIST", 0.0);
    public static final double FIRE_RESIST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "FIRE_RESIST", -15.0);
    public static final double ICE_RESIST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "ICE_RESIST", 90.0);
    public static final double DARK_RESIST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "DARK_RESIST", 0.0);
    public static final double MAGICAL_DEFENSE_BASE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "MAGICAL_DEFENSE_BASE", 4500.0);

    // 元素编码（沿用 ElementAccumulation：2 = 冰属性）
    public static final int ELEMENT_ICE = 2;

    // 两阶段与转阶段强化倍率
    public static final int ENCOUNTER_LIVES = BossBalanceRules.current().towerInt("frost_tower_frostnova", "ENCOUNTER_LIVES", 2);
    public static final double PHASE2_ATTACK_MULTIPLIER = BossBalanceRules.current().towerNum("frost_tower_frostnova", "PHASE2_ATTACK_MULTIPLIER", 1.50);
    public static final double PHASE2_SPIKE_MULTIPLIER = BossBalanceRules.current().towerNum("frost_tower_frostnova", "PHASE2_SPIKE_MULTIPLIER", 1.30);
    public static final double PHASE2_BURST_RADIUS_MULTIPLIER = BossBalanceRules.current().towerNum("frost_tower_frostnova", "PHASE2_BURST_RADIUS_MULTIPLIER", 1.30);

    // 转阶段演出时间轴（总计 4000ms：倒下 -> 浮空飞起 -> 空中站起 -> 落地）
    public static final long REVIVE_TOTAL_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "REVIVE_TOTAL_MS", 4_000L);
    public static final long REVIVE_FALL_END_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "REVIVE_FALL_END_MS", 1_000L);
    public static final long REVIVE_RISE_END_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "REVIVE_RISE_END_MS", 2_400L);
    public static final long REVIVE_STAND_END_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "REVIVE_STAND_END_MS", 3_200L);
    public static final double REVIVE_HOVER_HEIGHT = BossBalanceRules.current().towerNum("frost_tower_frostnova", "REVIVE_HOVER_HEIGHT", 3.8);
    public static final double REVIVE_SHOCKWAVE_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "REVIVE_SHOCKWAVE_RADIUS", 10.5);
    public static final double COEFF_REVIVE_SHOCKWAVE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_REVIVE_SHOCKWAVE", 0.45);

    // 统一伤害系数（乘 Target.attack(level)，二阶段 speciesAttack 已 ×1.5）
    public static final double COEFF_BACK_SPIKE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_BACK_SPIKE", 0.24);
    public static final double COEFF_HOMING_SPIKE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_HOMING_SPIKE", 0.62);
    public static final double COEFF_FROST_BURST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_FROST_BURST", 1.45);
    public static final double COEFF_FIELD_TICK = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_FIELD_TICK", 0.22);
    public static final double COEFF_FIELD_DETONATE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_FIELD_DETONATE", 0.78);
    public static final double COEFF_BLINK_SPIKE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_BLINK_SPIKE", 0.48);
    public static final double COEFF_NOVA_BURST = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_NOVA_BURST", 1.30);
    public static final double COEFF_NOVA_SPIKE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_NOVA_SPIKE", 0.34);
    public static final double COEFF_NOVA_ICICLE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "COEFF_NOVA_ICICLE", 1.15);

    // 碎冰共振：命中已被冻结的玩家时触发额外碎冰增伤并立刻解冻
    public static final double SHATTER_BONUS_MULTIPLIER = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SHATTER_BONUS_MULTIPLIER", 1.35);

    // 技能 1：背后凝结 24 枚冰刺（二阶段 31 枚），每 0.1 秒沿发射瞬间朝向直线发射一枚（冷却 31.2 秒，不再飞行追踪）
    public static final long SKILL1_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL1_CD_MS", 31_200L);
    public static final int SKILL1_BASE_SPIKES = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL1_BASE_SPIKES", 24);
    public static final long SKILL1_WINDUP_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL1_WINDUP_MS", 1_305L);
    public static final long SKILL1_LAUNCH_INTERVAL_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL1_LAUNCH_INTERVAL_MS", 100L);
    public static final double SKILL1_SPEED = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL1_SPEED", 24.0);
    public static final double SKILL1_TURN_DEG_PER_SEC = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL1_TURN_DEG_PER_SEC", 0.0);
    public static final long SKILL1_SPIKE_LIFE_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL1_SPIKE_LIFE_MS", 3_200L);
    public static final double SKILL1_HIT_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL1_HIT_RADIUS", 0.78);
    public static final long SKILL1_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL1_RECOVERY_MS", 650L);

    // 技能 2：射出 30° 扇形的三枚（二阶段 4 枚）直线飞行冰锥（冷却 9.1 秒，不再飞行追踪）
    public static final long SKILL2_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL2_CD_MS", 9_100L);
    public static final int SKILL2_BASE_SPIKES = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL2_BASE_SPIKES", 3);
    public static final double SKILL2_TOTAL_SPREAD_DEG = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL2_TOTAL_SPREAD_DEG", 30.0);
    public static final double SKILL2_TURN_DEG_PER_SEC = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL2_TURN_DEG_PER_SEC", 0.0);
    public static final long SKILL2_WINDUP_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL2_WINDUP_MS", 800L);
    public static final double SKILL2_SPEED = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL2_SPEED", 14.5);
    public static final long SKILL2_SPIKE_LIFE_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL2_SPIKE_LIFE_MS", 4_500L);
    public static final double SKILL2_HIT_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL2_HIT_RADIUS", 0.85);
    public static final long SKILL2_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL2_RECOVERY_MS", 500L);

    // 技能 3：爆发——短暂蓄力，对周围敌人造成高额冰伤并共振引爆寒霜领域（冷却 18.2 秒）
    public static final long SKILL3_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL3_CD_MS", 18_200L);
    public static final long SKILL3_WINDUP_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL3_WINDUP_MS", 1_595L);
    public static final long SKILL3_ACTIVE_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL3_ACTIVE_MS", 550L);
    public static final double SKILL3_BASE_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL3_BASE_RADIUS", 8.0);
    public static final long SKILL3_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL3_RECOVERY_MS", 650L);

    // 技能 4：寒霜领域——铺设 4 块（二阶段 6 块）持续冰阵，减速并持续造成冰伤，可被「爆发」共振引爆（冷却 23.4 秒）
    public static final long SKILL4_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_CD_MS", 23_400L);
    public static final int SKILL4_BASE_FIELDS = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL4_BASE_FIELDS", 4);
    public static final int SKILL4_PHASE2_FIELDS = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL4_PHASE2_FIELDS", 6);
    public static final long SKILL4_CAST_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_CAST_MS", 1_015L);
    public static final long SKILL4_WARNING_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_WARNING_MS", 1_740L);
    public static final long SKILL4_DURATION_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_DURATION_MS", 10_000L);
    public static final long SKILL4_TICK_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_TICK_MS", 600L);
    public static final double SKILL4_BASE_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL4_BASE_RADIUS", 4.0);
    public static final long SKILL4_DETONATE_DELAY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_DETONATE_DELAY_MS", 510L);
    public static final long SKILL4_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL4_RECOVERY_MS", 500L);

    // 技能 5：冰棱折跃——向后滑退拉开距离，在原位留下延时爆裂的冰晶并环形射出 8 枚（二阶段 10 枚）冰刺（冷却 14.3 秒）
    public static final long SKILL5_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_CD_MS", 14_300L);
    public static final long SKILL5_WINDUP_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_WINDUP_MS", 435L);
    public static final long SKILL5_BLINK_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_BLINK_MS", 320L);
    public static final double SKILL5_DISTANCE = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL5_DISTANCE", 12.5);
    public static final long SKILL5_CRYSTAL_DELAY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_CRYSTAL_DELAY_MS", 1_090L);
    public static final int SKILL5_BASE_SPIKES = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL5_BASE_SPIKES", 8);
    public static final double SKILL5_SPIKE_SPEED = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL5_SPIKE_SPEED", 18.0);
    public static final long SKILL5_SPIKE_LIFE_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_SPIKE_LIFE_MS", 2_400L);
    public static final double SKILL5_HIT_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL5_HIT_RADIUS", 0.78);
    public static final long SKILL5_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL5_RECOVERY_MS", 450L);

    // 技能 6（二阶段专属）：极寒星爆——浮空蓄力，脚下爆发 + 3 波直线螺旋冰刺（共 36 枚，不再追踪）+ 8 道巨型冰锥坠落（冷却 45.5 秒）
    public static final long SKILL6_CD_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_CD_MS", 45_500L);
    public static final double SKILL6_HOVER_HEIGHT = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL6_HOVER_HEIGHT", 4.5);
    public static final long SKILL6_WINDUP_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_WINDUP_MS", 2_755L);
    public static final int SKILL6_WAVES = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL6_WAVES", 3);
    public static final long SKILL6_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_WAVE_INTERVAL_MS", 500L);
    public static final int SKILL6_BASE_SPIKES_PER_WAVE = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL6_BASE_SPIKES_PER_WAVE", 9); // 二阶段 ×1.3 -> 12 枚/波，3 波共 36 枚
    public static final double SKILL6_SPIKE_SPEED = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL6_SPIKE_SPEED", 15.5);
    public static final double SKILL6_SPIKE_TURN_DEG_PER_SEC = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL6_SPIKE_TURN_DEG_PER_SEC", 0.0);
    public static final long SKILL6_SPIKE_LIFE_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_SPIKE_LIFE_MS", 3_600L);
    public static final int SKILL6_ICICLE_COUNT = BossBalanceRules.current().towerInt("frost_tower_frostnova", "SKILL6_ICICLE_COUNT", 8);
    public static final long SKILL6_ICICLE_WARNING_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_ICICLE_WARNING_MS", 2_175L);
    public static final double SKILL6_ICICLE_RADIUS = BossBalanceRules.current().towerNum("frost_tower_frostnova", "SKILL6_ICICLE_RADIUS", 3.2);
    public static final long SKILL6_LAND_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_LAND_MS", 450L);
    public static final long SKILL6_RECOVERY_MS = BossBalanceRules.current().towerMs("frost_tower_frostnova", "SKILL6_RECOVERY_MS", 1_200L);

    public enum SkillId {
        BACK_BARRAGE("back_barrage", SKILL1_CD_MS, false),
        HOMING_FAN("homing_fan", SKILL2_CD_MS, false),
        FROST_BURST("frost_burst", SKILL3_CD_MS, false),
        FROST_FIELD("frost_field", SKILL4_CD_MS, false),
        ICE_BLINK("ice_blink", SKILL5_CD_MS, false),
        WINTER_NOVA("winter_nova", SKILL6_CD_MS, true);

        public final String id;
        public final long cooldownMs;
        public final boolean phase2Only;

        SkillId(String id, long cooldownMs, boolean phase2Only) {
            this.id = id;
            this.cooldownMs = cooldownMs;
            this.phase2Only = phase2Only;
        }
    }

    public enum ReviveStage {
        FALLEN,
        RISING,
        STANDING,
        LANDING
    }

    /** 计算冰刺数量：二阶段提高 30%（四舍五入，例如 24 -> 31，3 -> 4，8 -> 10，9 -> 12）。 */
    public static int spikeCount(int baseCount, boolean phase2) {
        if (baseCount <= 0) return 0;
        return phase2 ? (int) Math.round(baseCount * PHASE2_SPIKE_MULTIPLIER) : baseCount;
    }

    /** 计算爆发与冰阵范围：二阶段提高 30%。 */
    public static double burstRadius(double baseRadius, boolean phase2) {
        return phase2 ? baseRadius * PHASE2_BURST_RADIUS_MULTIPLIER : baseRadius;
    }

    /** 计算 30° 扇形内第 index 枚冰刺的水平偏移角（单位：度）。 */
    public static double fanAngleDeg(int index, int count, double totalSpreadDeg) {
        if (count <= 1) return 0.0;
        double fraction = index / (double) (count - 1);
        return -totalSpreadDeg * 0.5 + totalSpreadDeg * fraction;
    }

    /**
     * 水平面按限定角速度向目标方向偏转（例如 60°/s），用于 3D 追踪冰刺的确定性步进。
     * 竖直分量温和追踪目标胸口高度，避免急转穿地或丢失高度。
     */
    public static Vec3 stepHomingDirection(Vec3 currentDir, Vec3 toTarget, double maxTurnDegPerSec, double dtSec) {
        Vec3 cur = currentDir.lengthSqr() < 1e-8 ? new Vec3(0, 0, 1) : currentDir.normalize();
        if (maxTurnDegPerSec <= 0.0 || dtSec <= 0.0 || toTarget.lengthSqr() < 1e-8) return cur;
        Vec3 desired = toTarget.normalize();
        double dot = Math.clamp(cur.dot(desired), -1.0, 1.0);
        double angle = Math.acos(dot);
        double maxStep = Math.toRadians(Math.max(0.0, maxTurnDegPerSec)) * dtSec;
        if (angle <= maxStep || angle < 1e-6) {
            return desired;
        }
        // 球面线性插值（SLERP），精确保证偏转角度严格等于 maxTurnDegPerSec * dtSec
        double t = maxStep / angle;
        double sinAngle = Math.sin(angle);
        if (sinAngle < 1e-6) return cur;
        double w0 = Math.sin((1.0 - t) * angle) / sinAngle;
        double w1 = Math.sin(t * angle) / sinAngle;
        return cur.scale(w0).add(desired.scale(w1)).normalize();
    }

    /** 转阶段演出当前所处拍段。 */
    public static ReviveStage reviveStage(long elapsedMs) {
        if (elapsedMs < REVIVE_FALL_END_MS) return ReviveStage.FALLEN;
        if (elapsedMs < REVIVE_RISE_END_MS) return ReviveStage.RISING;
        if (elapsedMs < REVIVE_STAND_END_MS) return ReviveStage.STANDING;
        return ReviveStage.LANDING;
    }

    /** 转阶段演出实体相对地面的高度偏移：0~1s 倒地(0) -> 1~2.4s 浮起(+3.8) -> 2.4~3.2s 空中立直(+3.8) -> 3.2~4.0s 落地(0)。 */
    public static double reviveHeightOffset(long elapsedMs) {
        if (elapsedMs <= REVIVE_FALL_END_MS) return 0.0;
        if (elapsedMs < REVIVE_RISE_END_MS) {
            double t = (elapsedMs - REVIVE_FALL_END_MS) / (double) (REVIVE_RISE_END_MS - REVIVE_FALL_END_MS);
            return REVIVE_HOVER_HEIGHT * smooth(t);
        }
        if (elapsedMs < REVIVE_STAND_END_MS) {
            return REVIVE_HOVER_HEIGHT;
        }
        if (elapsedMs < REVIVE_TOTAL_MS) {
            double t = (elapsedMs - REVIVE_STAND_END_MS) / (double) (REVIVE_TOTAL_MS - REVIVE_STAND_END_MS);
            // 先慢后快的稳重落势
            return REVIVE_HOVER_HEIGHT * (1.0 - t * t);
        }
        return 0.0;
    }

    /** 背后凝结冰刺扇阵中第 index 枚冰刺的局部偏移（right, up, back），形成优雅的双层冰晶羽翼弧。 */
    public static double[] backSpikeSlotOffset(int index, int total) {
        int outerCount = Math.max(1, (total + 1) / 2);
        boolean outer = index < outerCount;
        int ringIndex = outer ? index : index - outerCount;
        int ringTotal = outer ? outerCount : Math.max(1, total - outerCount);
        double frac = ringTotal <= 1 ? 0.5 : ringIndex / (double) (ringTotal - 1);
        double angle = -Math.PI * 0.62 + frac * Math.PI * 1.24;
        double radius = outer ? 1.85 : 1.22;
        double right = Math.sin(angle) * radius;
        double up = 1.45 + Math.cos(angle) * radius * 0.78 + (outer ? 0.25 : 0.0);
        double back = outer ? 0.85 : 0.55;
        return new double[]{right, up, back};
    }

    public static double[] initialResists() {
        return new double[]{LIGHT_RESIST, FIRE_RESIST, ICE_RESIST, DARK_RESIST};
    }

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private FrostTowerFrostnova() {}
}

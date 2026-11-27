package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 锻炉塔第 20 层专属首领「阿拉德四剑圣」集中参数定义与确定性几何算法：
 * - P1：布万加（钝器）+ 巴恩·巴休特（短剑），各自压至 40% 锁血退场；
 * - P2：阿甘左（巨剑）+ 西岚（太刀），各自压至 40% 锁血退场；
 * - P3：四剑圣以剩余 40% 血量全员返场，双主攻 + 双策应令牌调度，逐一击败即通关；
 * - 纯粹无元素物理白刃战，每人 3 招常规技 + 1 招个人觉醒技，配 P1/P2 双人合击与 P3 四剑合璧。
 */
public final class ForgeTowerSwordMasters {
    public static final String ENCOUNTER_NAME = "阿拉德四剑圣";
    public static final int ELEMENT_NONE = -1;

    // 阶段锁血与全局节奏参数
    public static final double RETREAT_HEALTH_FLOOR = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "RETREAT_HEALTH_FLOOR", 0.40);
    public static final double AWAKENING_TRIGGER_HP = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AWAKENING_TRIGGER_HP", 0.70);
    public static final double DUO_COMBO_TRIGGER_HP = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO_COMBO_TRIGGER_HP", 0.65);
    public static final long DUO_COMBO_TRIGGER_DELAY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO_COMBO_TRIGGER_DELAY_MS", 20_000L);
    public static final long DUO_COMBO_CD_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO_COMBO_CD_MS", 40_000L);
    public static final long QUAD_COMBO_FIRST_DELAY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_COMBO_FIRST_DELAY_MS", 6_000L);
    public static final long QUAD_COMBO_CD_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_COMBO_CD_MS", 48_000L);
    public static final long COMBO_EXHAUST_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "COMBO_EXHAUST_MS", 2_500L);
    public static final long TRANSITION_P1_P2_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "TRANSITION_P1_P2_MS", 2_200L);
    public static final long TRANSITION_P2_P3_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "TRANSITION_P2_P3_MS", 2_800L);
    public static final long AWAKENING_P3_CD_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AWAKENING_P3_CD_MS", 38_000L);

    // P3 调度约束与减员反馈
    public static final int MAX_P3_MAIN_CASTERS = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "MAX_P3_MAIN_CASTERS", 2);
    public static final int MAX_ACTIVE_AWAKENINGS = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "MAX_ACTIVE_AWAKENINGS", 1);
    public static final double P3_DEAD_CD_REDUCTION_PER_KILL = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "P3_DEAD_CD_REDUCTION_PER_KILL", 0.10);

    // P3 策应剑圣近身轻型普攻（避免发呆且不占用主攻令牌）
    public static final long LIGHT_MELEE_CD_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "LIGHT_MELEE_CD_MS", 2_400L);
    public static final double LIGHT_MELEE_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "LIGHT_MELEE_RANGE", 4.2);
    public static final long LIGHT_MELEE_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "LIGHT_MELEE_WINDUP_MS", 765L);
    public static final long LIGHT_MELEE_ACTIVE_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "LIGHT_MELEE_ACTIVE_MS", 220L);
    public static final double COEFF_LIGHT_MELEE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_LIGHT_MELEE", 0.35);

    // 均衡无偏科抗性（纯白刃对决）
    public static final double[] INITIAL_RESISTS = BossBalanceRules.current().towerArray("forge_tower_sword_masters", "INITIAL_RESISTS", new double[]{15.0, 15.0, 15.0, 15.0});

    public enum Member {
        BWANGA("forge_tower_bwanga", "布万加", "钝器剑圣", 0.80, TowerBossGrade.B, "common_blunt", 0xffb36b, 1, -10.5),
        BAAN("forge_tower_baan", "巴恩·巴休特", "短剑剑圣", 0.55, TowerBossGrade.B, "common_shortsword", 0x6ce8c2, 1, -3.5),
        AGANZO("forge_tower_aganzo", "阿甘左", "巨剑剑圣", 0.70, TowerBossGrade.A, "bylle", 0x7eb8ff, 2, 3.5),
        SIRAN("forge_tower_siran", "西岚", "太刀剑圣", 0.55, TowerBossGrade.A, "common_katana", 0xe2eaff, 2, 10.5);

        public final String id;
        public final String displayName;
        public final String title;
        public final double healthMultiplier;
        public final TowerBossGrade attackGrade;
        public final String weaponModelId;
        public final int themeColor;
        public final int introPhase;
        public final double balconyOffsetX;

        Member(String id, String displayName, String title, double healthMultiplier, TowerBossGrade attackGrade,
               String weaponModelId, int themeColor, int introPhase, double balconyOffsetX) {
            this.id = id;
            this.displayName = displayName;
            this.title = title;
            var tb = BossBalanceRules.current().towerBoss("forge_tower_sword_masters"); var m = tb == null ? null : tb.member(id); this.healthMultiplier = m == null ? healthMultiplier : m.healthMultiplier();
            this.attackGrade = attackGrade;
            this.weaponModelId = weaponModelId;
            this.themeColor = themeColor;
            this.introPhase = introPhase;
            this.balconyOffsetX = balconyOffsetX;
        }

        public static Member find(String id) {
            if (id == null) return null;
            for (Member m : values()) {
                if (m.id.equals(id)) return m;
            }
            return null;
        }
    }

    public static final String LEAD_ID = Member.BWANGA.id;

    public static boolean isMemberId(String id) {
        return Member.find(id) != null;
    }

    public enum SkillId {
        // 布万加（钝器）
        BWANGA_EARTH_SLAM(Member.BWANGA, "bwanga_earth_slam", 7_000L, false),
        BWANGA_BANTU_CHARGE(Member.BWANGA, "bwanga_bantu_charge", 11_000L, false),
        BWANGA_BOOMERANG_HAMMER(Member.BWANGA, "bwanga_boomerang_hammer", 13_000L, false),
        BWANGA_AWAKENING(Member.BWANGA, "bwanga_awakening", AWAKENING_P3_CD_MS, true),

        // 巴恩（短剑）
        BAAN_TRIPLE_THRUST(Member.BAAN, "baan_triple_thrust", 6_500L, false),
        BAAN_HURRICANE_BLADES(Member.BAAN, "baan_hurricane_blades", 9_500L, false),
        BAAN_ZIGZAG_STEP(Member.BAAN, "baan_zigzag_step", 12_000L, false),
        BAAN_AWAKENING(Member.BAAN, "baan_awakening", AWAKENING_P3_CD_MS, true),

        // 阿甘左（巨剑）
        AGANZO_GALE_CLEAVE(Member.AGANZO, "aganzo_gale_cleave", 7_500L, false),
        AGANZO_RISING_CRASH(Member.AGANZO, "aganzo_rising_crash", 11_000L, false),
        AGANZO_WHIRL_SLASH(Member.AGANZO, "aganzo_whirl_slash", 14_000L, false),
        AGANZO_AWAKENING(Member.AGANZO, "aganzo_awakening", AWAKENING_P3_CD_MS, true),

        // 西岚（太刀）
        SIRAN_IAI_DRAW(Member.SIRAN, "siran_iai_draw", 7_000L, false),
        SIRAN_TRIPLE_FLASH(Member.SIRAN, "siran_triple_flash", 11_500L, false),
        SIRAN_ECHO_CROSS(Member.SIRAN, "siran_echo_cross", 13_000L, false),
        SIRAN_AWAKENING(Member.SIRAN, "siran_awakening", AWAKENING_P3_CD_MS, true);

        public final Member member;
        public final String id;
        public final long cooldownMs;
        public final boolean awakening;

        SkillId(Member member, String id, long cooldownMs, boolean awakening) {
            this.member = member;
            this.id = id;
            var tb = BossBalanceRules.current().towerBoss("forge_tower_sword_masters"); this.cooldownMs = tb == null ? cooldownMs : tb.skillCooldown(id, cooldownMs);
            this.awakening = awakening;
        }
    }

    // ==================== 1. 布万加技能参数 ====================
    // S1 裂地重锤
    public static final long BWANGA_S1_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S1_WINDUP_MS", 1_275L);
    public static final double BWANGA_S1_SLAM_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S1_SLAM_RADIUS", 4.5);
    public static final double BWANGA_S1_RING_END_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S1_RING_END_RADIUS", 14.0);
    public static final double BWANGA_S1_RING_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S1_RING_WIDTH", 2.5);
    public static final long BWANGA_S1_RING_DURATION_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S1_RING_DURATION_MS", 950L);
    public static final double COEFF_BWANGA_S1_SLAM = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S1_SLAM", 0.90);
    public static final double COEFF_BWANGA_S1_RING = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S1_RING", 0.65);
    public static final long BWANGA_S1_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S1_RECOVERY_MS", 550L);

    // S2 班图狂奔·破城冲撞
    public static final long BWANGA_S2_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S2_WINDUP_MS", 1_105L);
    public static final double BWANGA_S2_DASH_DIST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S2_DASH_DIST", 18.0);
    public static final long BWANGA_S2_DASH_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S2_DASH_MS", 620L);
    public static final double BWANGA_S2_DASH_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S2_DASH_WIDTH", 3.2);
    public static final long BWANGA_S2_SWEEP_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S2_SWEEP_WINDUP_MS", 595L);
    public static final double BWANGA_S2_SWEEP_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S2_SWEEP_RADIUS", 5.0);
    public static final double COEFF_BWANGA_S2_DASH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S2_DASH", 0.85);
    public static final double COEFF_BWANGA_S2_SWEEP = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S2_SWEEP", 0.70);
    public static final long BWANGA_S2_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S2_RECOVERY_MS", 550L);

    // S3 回旋掷锤
    public static final long BWANGA_S3_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S3_WINDUP_MS", 1_020L);
    public static final double BWANGA_S3_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S3_RANGE", 20.0);
    public static final double BWANGA_S3_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_S3_WIDTH", 2.6);
    public static final long BWANGA_S3_OUT_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S3_OUT_MS", 700L);
    public static final long BWANGA_S3_HOVER_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S3_HOVER_MS", 680L);
    public static final long BWANGA_S3_RETURN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S3_RETURN_MS", 700L);
    public static final double COEFF_BWANGA_S3_OUT = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S3_OUT", 0.60);
    public static final double COEFF_BWANGA_S3_RETURN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_S3_RETURN", 0.65);
    public static final long BWANGA_S3_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_S3_RECOVERY_MS", 500L);

    // 觉醒：族长之怒·千钧震天击
    public static final long BWANGA_A_LEAP_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_A_LEAP_WARN_MS", 1_530L);
    public static final double BWANGA_A_LAND_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_A_LAND_RADIUS", 7.0);
    public static final double COEFF_BWANGA_A_LAND = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_A_LAND", 1.15);
    public static final long BWANGA_A_WAVE1_DURATION_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_A_WAVE1_DURATION_MS", 900L);
    public static final long BWANGA_A_WAVE2_DURATION_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_A_WAVE2_DURATION_MS", 950L);
    public static final long BWANGA_A_WAVE3_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_A_WAVE3_WARN_MS", 1_445L);
    public static final double BWANGA_A_RING_MAX_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_A_RING_MAX_RADIUS", 19.0);
    public static final double BWANGA_A_RING_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_A_RING_WIDTH", 2.8);
    public static final double BWANGA_A_SAFE_BAND_MIN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_A_SAFE_BAND_MIN", 7.5);
    public static final double BWANGA_A_SAFE_BAND_MAX = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BWANGA_A_SAFE_BAND_MAX", 12.5);
    public static final double COEFF_BWANGA_A_WAVE1 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_A_WAVE1", 0.80);
    public static final double COEFF_BWANGA_A_WAVE2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_A_WAVE2", 0.85);
    public static final double COEFF_BWANGA_A_FINAL = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BWANGA_A_FINAL", 1.35);
    public static final long BWANGA_A_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BWANGA_A_RECOVERY_MS", 1_800L);

    // ==================== 2. 巴恩技能参数 ====================
    // S1 流心·连突刺
    public static final long BAAN_S1_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S1_WINDUP_MS", 850L);
    public static final long BAAN_S1_STEP_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S1_STEP_WARN_MS", 375L);
    public static final int BAAN_S1_STRIKES = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "BAAN_S1_STRIKES", 3);
    public static final double BAAN_S1_DASH_DIST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S1_DASH_DIST", 6.0);
    public static final double BAAN_S1_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S1_WIDTH", 2.0);
    public static final long BAAN_S1_STEP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S1_STEP_MS", 340L);
    public static final double[] COEFF_BAAN_S1 = BossBalanceRules.current().towerArray("forge_tower_sword_masters", "COEFF_BAAN_S1", new double[]{0.45, 0.45, 0.70});
    public static final long BAAN_S1_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S1_RECOVERY_MS", 500L);

    // S2 飓风三刃斩
    public static final long BAAN_S2_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S2_WINDUP_MS", 1_020L);
    public static final long BAAN_S2_WAVE2_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S2_WAVE2_WARN_MS", 440L);
    public static final double[] BAAN_S2_WAVE1_ANGLES = BossBalanceRules.current().towerArray("forge_tower_sword_masters", "BAAN_S2_WAVE1_ANGLES", new double[]{-28.0, 0.0, 28.0});
    public static final double[] BAAN_S2_WAVE2_ANGLES = BossBalanceRules.current().towerArray("forge_tower_sword_masters", "BAAN_S2_WAVE2_ANGLES", new double[]{-14.0, 14.0});
    public static final long BAAN_S2_WAVE2_DELAY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S2_WAVE2_DELAY_MS", 450L);
    public static final double BAAN_S2_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S2_RANGE", 22.0);
    public static final double BAAN_S2_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S2_WIDTH", 2.2);
    public static final long BAAN_S2_TRAVEL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S2_TRAVEL_MS", 680L);
    public static final double COEFF_BAAN_S2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BAAN_S2", 0.65);
    public static final long BAAN_S2_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S2_RECOVERY_MS", 500L);

    // S3 帝国穿云步
    public static final long BAAN_S3_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S3_WINDUP_MS", 935L);
    public static final double BAAN_S3_SEG_DIST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S3_SEG_DIST", 10.0);
    public static final double BAAN_S3_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S3_WIDTH", 2.4);
    public static final long BAAN_S3_SEG_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S3_SEG_MS", 280L);
    public static final long BAAN_S3_UPPER_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S3_UPPER_WARN_MS", 545L);
    public static final double BAAN_S3_UPPER_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_S3_UPPER_RADIUS", 4.0);
    public static final double COEFF_BAAN_S3_DASH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BAAN_S3_DASH", 0.75);
    public static final double COEFF_BAAN_S3_UPPER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BAAN_S3_UPPER", 0.60);
    public static final long BAAN_S3_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_S3_RECOVERY_MS", 500L);

    // 觉醒：飓风剑狱·极风乱舞
    public static final int BAAN_A_WAVES = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "BAAN_A_WAVES", 3);
    public static final int BAAN_A_BLADES_PER_WAVE = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "BAAN_A_BLADES_PER_WAVE", 6);
    public static final double BAAN_A_RING_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_A_RING_RADIUS", 11.0);
    public static final double BAAN_A_GAP_HALF_DEG = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_A_GAP_HALF_DEG", 30.0);
    public static final long BAAN_A_WAVE_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_WAVE_WARN_MS", 1_275L);
    public static final long BAAN_A_WAVE_TRAVEL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_WAVE_TRAVEL_MS", 520L);
    public static final long BAAN_A_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_WAVE_INTERVAL_MS", 1_615L);
    public static final double BAAN_A_BLADE_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_A_BLADE_WIDTH", 2.0);
    public static final double COEFF_BAAN_A_BLADE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BAAN_A_BLADE", 0.75);
    public static final long BAAN_A_FINISHER_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_FINISHER_WARN_MS", 1_360L);
    public static final long BAAN_A_FINISHER_TRAVEL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_FINISHER_TRAVEL_MS", 480L);
    public static final double BAAN_A_FINISHER_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_A_FINISHER_RANGE", 24.0);
    public static final double BAAN_A_FINISHER_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "BAAN_A_FINISHER_WIDTH", 4.2);
    public static final double COEFF_BAAN_A_FINISHER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_BAAN_A_FINISHER", 1.35);
    public static final long BAAN_A_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "BAAN_A_RECOVERY_MS", 1_800L);

    // ==================== 3. 阿甘左技能参数 ====================
    // S1 浪人重劈·风压破
    public static final long AGANZO_S1_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S1_WINDUP_MS", 1_360L);
    public static final double AGANZO_S1_FAN_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S1_FAN_RADIUS", 6.5);
    public static final double AGANZO_S1_FAN_HALF_DEG = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S1_FAN_HALF_DEG", 60.0);
    public static final double AGANZO_S1_WAVE_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S1_WAVE_RANGE", 24.0);
    public static final double AGANZO_S1_WAVE_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S1_WAVE_WIDTH", 5.0);
    public static final long AGANZO_S1_WAVE_TRAVEL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S1_WAVE_TRAVEL_MS", 780L);
    public static final double COEFF_AGANZO_S1_FAN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S1_FAN", 0.90);
    public static final double COEFF_AGANZO_S1_WAVE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S1_WAVE", 0.80);
    public static final long AGANZO_S1_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S1_RECOVERY_MS", 600L);

    // S2 破军升龙·重踏斩
    public static final long AGANZO_S2_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S2_WINDUP_MS", 1_105L);
    public static final double AGANZO_S2_DASH_DIST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S2_DASH_DIST", 12.0);
    public static final double AGANZO_S2_DASH_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S2_DASH_WIDTH", 3.4);
    public static final long AGANZO_S2_DASH_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S2_DASH_MS", 480L);
    public static final long AGANZO_S2_SLAM_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S2_SLAM_WARN_MS", 935L);
    public static final double AGANZO_S2_SLAM_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S2_SLAM_RADIUS", 5.5);
    public static final double COEFF_AGANZO_S2_DASH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S2_DASH", 0.75);
    public static final double COEFF_AGANZO_S2_SLAM = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S2_SLAM", 0.95);
    public static final long AGANZO_S2_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S2_RECOVERY_MS", 600L);

    // S3 回旋大风车
    public static final long AGANZO_S3_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S3_WINDUP_MS", 1_190L);
    public static final long AGANZO_S3_SPIN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S3_SPIN_MS", 2_400L);
    public static final long AGANZO_S3_TICK_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S3_TICK_MS", 400L);
    public static final double AGANZO_S3_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S3_RADIUS", 5.5);
    public static final double AGANZO_S3_PULL_OUTER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S3_PULL_OUTER", 11.0);
    public static final double AGANZO_S3_FINISHER_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_S3_FINISHER_RADIUS", 6.5);
    public static final long AGANZO_S3_FINISHER_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S3_FINISHER_WARN_MS", 715L);
    public static final double COEFF_AGANZO_S3_SPIN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S3_SPIN", 0.40);
    public static final double COEFF_AGANZO_S3_FINISHER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_S3_FINISHER", 0.85);
    public static final long AGANZO_S3_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_S3_RECOVERY_MS", 650L);

    // 觉醒：苍岚灭世·巨剑裂地式
    public static final long AGANZO_A_CHARGE_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_A_CHARGE_MS", 1_870L);
    public static final double AGANZO_A_SUPER_ARMOR_TAKEN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_A_SUPER_ARMOR_TAKEN", 0.50);
    public static final double AGANZO_A_CROSS_HALF_LEN = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_A_CROSS_HALF_LEN", 18.0);
    public static final double AGANZO_A_CROSS_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_A_CROSS_WIDTH", 4.5);
    public static final long AGANZO_A_CROSS_GAP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_A_CROSS_GAP_MS", 1_530L);
    public static final long AGANZO_A_FINISHER_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_A_FINISHER_WARN_MS", 1_615L);
    public static final long AGANZO_A_FINISHER_TRAVEL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_A_FINISHER_TRAVEL_MS", 750L);
    public static final double AGANZO_A_FINISHER_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_A_FINISHER_RANGE", 26.0);
    public static final double AGANZO_A_FINISHER_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "AGANZO_A_FINISHER_WIDTH", 6.5);
    public static final double COEFF_AGANZO_A_CROSS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_A_CROSS", 1.25);
    public static final double COEFF_AGANZO_A_FINISHER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_AGANZO_A_FINISHER", 1.50);
    public static final long AGANZO_A_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "AGANZO_A_RECOVERY_MS", 2_000L);

    // ==================== 4. 西岚技能参数 ====================
    // S1 极意·居合拔刀斩
    public static final long SIRAN_S1_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S1_WINDUP_MS", 1_190L);
    public static final double SIRAN_S1_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S1_RADIUS", 8.5);
    public static final double SIRAN_S1_HALF_DEG = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S1_HALF_DEG", 135.0); // 270° 扇形，背后 90° 安全盲区
    public static final double COEFF_SIRAN_S1 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_S1", 0.95);
    public static final long SIRAN_S1_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S1_RECOVERY_MS", 550L);

    // S2 疾风残影·三叠瞬闪
    public static final long SIRAN_S2_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S2_WINDUP_MS", 935L);
    public static final long SIRAN_S2_STEP_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S2_STEP_WARN_MS", 850L);
    public static final long SIRAN_S2_STEP_FLASH_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S2_STEP_FLASH_MS", 180L);
    public static final int SIRAN_S2_STRIKES = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "SIRAN_S2_STRIKES", 3);
    public static final double SIRAN_S2_DIST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S2_DIST", 14.0);
    public static final double SIRAN_S2_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S2_WIDTH", 2.2);
    public static final double COEFF_SIRAN_S2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_S2", 0.70);
    public static final long SIRAN_S2_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S2_RECOVERY_MS", 550L);

    // S3 破空·延迟二重斩
    public static final long SIRAN_S3_WINDUP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S3_WINDUP_MS", 1_105L);
    public static final double SIRAN_S3_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S3_RANGE", 26.0);
    public static final double SIRAN_S3_WIDTH_FIRST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S3_WIDTH_FIRST", 2.4);
    public static final double SIRAN_S3_WIDTH_ECHO = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_S3_WIDTH_ECHO", 3.0);
    public static final long SIRAN_S3_ECHO_DELAY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S3_ECHO_DELAY_MS", 1_445L);
    public static final double COEFF_SIRAN_S3_FIRST = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_S3_FIRST", 0.65);
    public static final double COEFF_SIRAN_S3_ECHO = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_S3_ECHO", 0.75);
    public static final long SIRAN_S3_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_S3_RECOVERY_MS", 550L);

    // 觉醒：刹那芳华·时空无痕斩
    public static final int SIRAN_A_LINE_COUNT = BossBalanceRules.current().towerInt("forge_tower_sword_masters", "SIRAN_A_LINE_COUNT", 6);
    public static final long SIRAN_A_DRAW_SPAN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_A_DRAW_SPAN_MS", 3_060L);
    public static final long SIRAN_A_DETONATE_STEP_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_A_DETONATE_STEP_MS", 240L);
    public static final double SIRAN_A_LINE_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_A_LINE_WIDTH", 2.8);
    public static final double COEFF_SIRAN_A_LINE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_A_LINE", 0.95);
    public static final long SIRAN_A_FINISHER_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_A_FINISHER_WARN_MS", 1_445L);
    public static final double SIRAN_A_FINISHER_RANGE = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_A_FINISHER_RANGE", 30.0);
    public static final double SIRAN_A_FINISHER_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "SIRAN_A_FINISHER_WIDTH", 3.6);
    public static final double COEFF_SIRAN_A_FINISHER = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_SIRAN_A_FINISHER", 1.50);
    public static final long SIRAN_A_RECOVERY_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SIRAN_A_RECOVERY_MS", 2_000L);

    // ==================== 5. 合击技参数 ====================
    // P1 合击：崩山飓风协奏
    public static final long DUO1_STAGE1_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO1_STAGE1_WARN_MS", 1_530L);
    public static final double DUO1_STAGE1_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO1_STAGE1_RADIUS", 8.0);
    public static final double COEFF_DUO1_STAGE1 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO1_STAGE1", 0.95);
    public static final long DUO1_STAGE2_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO1_STAGE2_WARN_MS", 1_445L);
    public static final double DUO1_STAGE2_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO1_STAGE2_WIDTH", 3.0);
    public static final double COEFF_DUO1_STAGE2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO1_STAGE2", 0.85);
    public static final long DUO1_STAGE3_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO1_STAGE3_WARN_MS", 595L);
    public static final long DUO1_STAGE3_DURATION_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO1_STAGE3_DURATION_MS", 1_050L);
    public static final double DUO1_STAGE3_START_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO1_STAGE3_START_RADIUS", 19.0);
    public static final double DUO1_STAGE3_END_RADIUS = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO1_STAGE3_END_RADIUS", 6.0);
    public static final double DUO1_STAGE3_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO1_STAGE3_WIDTH", 3.2);
    public static final double COEFF_DUO1_STAGE3 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO1_STAGE3", 1.15);

    // P2 合击：苍岚·刹那交锋
    public static final long DUO2_STAGE1_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO2_STAGE1_WARN_MS", 1_615L);
    public static final double DUO2_STAGE1_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO2_STAGE1_WIDTH", 4.6);
    public static final double COEFF_DUO2_STAGE1 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO2_STAGE1", 1.10);
    public static final long DUO2_STAGE2_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO2_STAGE2_WARN_MS", 1_445L);
    public static final double DUO2_STAGE2_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO2_STAGE2_WIDTH", 3.6);
    public static final double COEFF_DUO2_STAGE2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO2_STAGE2", 1.10);
    public static final long DUO2_STAGE3_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "DUO2_STAGE3_WARN_MS", 1_615L);
    public static final double DUO2_STAGE3_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "DUO2_STAGE3_WIDTH", 5.2);
    public static final double COEFF_DUO2_STAGE3 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_DUO2_STAGE3", 1.35);

    // P3 终极大招：四剑合璧·悲鸣绝响
    public static final long QUAD_STAGE1_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_STAGE1_WARN_MS", 1_445L);
    public static final long QUAD_STAGE1_RING_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_STAGE1_RING_MS", 900L);
    public static final double QUAD_STAGE1_START_R = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "QUAD_STAGE1_START_R", 6.5);
    public static final double QUAD_STAGE1_END_R = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "QUAD_STAGE1_END_R", 20.0);
    public static final double COEFF_QUAD_STAGE1 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_QUAD_STAGE1", 1.00);

    public static final long QUAD_STAGE2_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_STAGE2_WARN_MS", 1_360L);
    public static final double QUAD_STAGE2_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "QUAD_STAGE2_WIDTH", 3.2);
    public static final double COEFF_QUAD_STAGE2 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_QUAD_STAGE2", 0.95);

    public static final long QUAD_STAGE3_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_STAGE3_WARN_MS", 1_445L);
    public static final double QUAD_STAGE3_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "QUAD_STAGE3_WIDTH", 5.2);
    public static final double COEFF_QUAD_STAGE3 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_QUAD_STAGE3", 1.25);

    public static final long QUAD_STAGE4_WARN_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "QUAD_STAGE4_WARN_MS", 1_615L);
    public static final double QUAD_STAGE4_WIDTH = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "QUAD_STAGE4_WIDTH", 3.4);
    public static final double COEFF_QUAD_STAGE4 = BossBalanceRules.current().towerNum("forge_tower_sword_masters", "COEFF_QUAD_STAGE4", 1.50);

    public static final long SYNC_MIN_INTERVAL_MS = BossBalanceRules.current().towerMs("forge_tower_sword_masters", "SYNC_MIN_INTERVAL_MS", 250L);

    public record SlashSegment(Vec3 from, Vec3 to) {}

    public static double[] initialResists() {
        return INITIAL_RESISTS.clone();
    }

    /** P3 每击败一名剑圣，其余剑圣冷却缩减 10%。 */
    public static double p3CooldownScale(int deadCount) {
        int clamped = Math.clamp(deadCount, 0, Member.values().length - 1);
        return Math.max(0.60, 1.0 - clamped * P3_DEAD_CD_REDUCTION_PER_KILL);
    }

    public static long scaledCooldown(long baseMs, int deadCount, boolean inP3) {
        if (!inP3 || deadCount <= 0) return baseMs;
        return Math.max(1_000L, Math.round(baseMs * p3CooldownScale(deadCount)));
    }

    public static List<SkillId> regularSkills(Member member) {
        List<SkillId> list = new ArrayList<>(3);
        for (SkillId s : SkillId.values()) {
            if (s.member == member && !s.awakening) list.add(s);
        }
        return list;
    }

    public static SkillId awakeningOf(Member member) {
        for (SkillId s : SkillId.values()) {
            if (s.member == member && s.awakening) return s;
        }
        throw new IllegalStateException("缺少剑圣个人觉醒技：" + member);
    }

    /** 退场观战位：锻炉塔北侧熔炉前的平坦地坪，彼此错开不重叠。 */
    public static Vec3 retreatPosition(int cell, Member member) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        return new Vec3(center.x + member.balconyOffsetX, center.y, center.z - 20.0);
    }

    /** P3 返场站位（四角对峙）。 */
    public static Vec3 quadStartPosition(int cell, Member member) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        return switch (member) {
            case BWANGA -> center.add(-9.5, 0, -8.5);
            case BAAN -> center.add(9.5, 0, -8.5);
            case AGANZO -> center.add(-9.5, 0, 8.5);
            case SIRAN -> center.add(9.5, 0, 8.5);
        };
    }

    /** 四剑合璧东西南北四方位站位。 */
    public static Vec3 quadComboPosition(int cell, Member member) {
        Vec3 center = AwakeningTowerLayout.guard(cell);
        return switch (member) {
            case BWANGA -> center.add(0, 0, -14.0);
            case BAAN -> center.add(14.0, 0, 0);
            case AGANZO -> center.add(-14.0, 0, 0);
            case SIRAN -> center.add(0, 0, 14.0);
        };
    }

    /** 巴恩觉醒：计算单波向心剑风角度（留出对称 2 处 60° 宽阔安全缺口）。 */
    public static double[] hurricaneWaveAnglesRad(int count, double gapAngleRad) {
        int safeCount = Math.max(2, count);
        double[] angles = new double[safeCount];
        double gapHalfRad = Math.toRadians(BAAN_A_GAP_HALF_DEG);
        double usableSpan = Math.PI - 2.0 * gapHalfRad;
        int firstHalf = safeCount / 2;
        int secondHalf = safeCount - firstHalf;
        for (int i = 0; i < firstHalf; i++) {
            double frac = firstHalf <= 1 ? 0.5 : i / (double) (firstHalf - 1);
            angles[i] = gapAngleRad + gapHalfRad + frac * usableSpan;
        }
        for (int i = 0; i < secondHalf; i++) {
            double frac = secondHalf <= 1 ? 0.5 : i / (double) (secondHalf - 1);
            angles[firstHalf + i] = gapAngleRad + Math.PI + gapHalfRad + frac * usableSpan;
        }
        return angles;
    }

    /**
     * 西岚觉醒「刹那芳华·时空无痕斩」的 6 条交错银白斩线：
     * 3 条平行线 + 3 条正交/斜交线，以固定间距留出清晰的菱形安全空地，确保可纯走位无伤躲避。
     */
    public static List<SlashSegment> siranAwakeningLines(Vec3 center, long seed) {
        SplittableRandom rng = new SplittableRandom(seed ^ 0x51A49E21L);
        double baseAngle = rng.nextDouble(0.0, Math.PI);
        double crossAngle = baseAngle + Math.PI * 0.42;
        double halfLen = 19.0;
        double[] offsets = {-8.5, 0.0, 8.5};
        List<SlashSegment> segments = new ArrayList<>(SIRAN_A_LINE_COUNT);
        for (int i = 0; i < 3; i++) {
            segments.add(offsetLine(center, baseAngle, offsets[i], halfLen));
            segments.add(offsetLine(center, crossAngle, offsets[i], halfLen));
        }
        return segments;
    }

    private static SlashSegment offsetLine(Vec3 center, double angleRad, double lateralOffset, double halfLen) {
        Vec3 dir = new Vec3(Math.cos(angleRad), 0, Math.sin(angleRad));
        Vec3 normal = new Vec3(-dir.z, 0, dir.x);
        Vec3 mid = center.add(normal.scale(lateralOffset));
        return new SlashSegment(mid.subtract(dir.scale(halfLen)), mid.add(dir.scale(halfLen)));
    }

    private ForgeTowerSwordMasters() {}
}

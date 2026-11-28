package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.TowerBossGrade;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 星仪塔第 20 层专属首领「吉尔伽美什（英雄王）」的阶段门槛、面板评级、抗性配置与全套技能参数集中定义。
 * <p>
 * 面板定位：HP E (1.0) / ATK A (1.8)（叠星仪塔 HP×3、ATK×1.5 后为极致高攻玻璃王），
 * 常驻黄金甲 25% 减伤，四元素抗性均衡偏高（45.0）。
 * 阶段结构：单管血 + 65%（维摩那转场） / 25%（拔出乖离剑 Ea） / 1%（濒死终焉 DPS Check）。
 */
public final class OrreryTowerGilgamesh {
    public static final String ID = "orrery_tower_gilgamesh";
    public static final String DISPLAY_NAME = "吉尔伽美什";
    public static final String WEDGE_MECHANISM_ID = "gilgamesh_wedge";
    public static final String ENKIDU_MECHANISM_ID = "gilgamesh_enkidu";

    // 基础面板评级：HP E (1.0) / ATK A (1.8)
    public static final TowerBossGrade HEALTH_GRADE = TowerBossGrade.E;
    public static final TowerBossGrade ATTACK_GRADE = TowerBossGrade.A;

    // 黄金甲常驻 25% 减伤
    public static final double GOLDEN_ARMOR_DAMAGE_TAKEN = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "GOLDEN_ARMOR_DAMAGE_TAKEN", 0.75);

    // 神代抗魔力：四元素均衡偏高抗性
    public static final double LIGHT_RESIST = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "LIGHT_RESIST", 45.0);
    public static final double FIRE_RESIST = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "FIRE_RESIST", 45.0);
    public static final double ICE_RESIST = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "ICE_RESIST", 45.0);
    public static final double DARK_RESIST = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "DARK_RESIST", 45.0);

    // 元素编码（沿用 ElementAccumulation）
    public static final int ELEMENT_NONE = -1;
    public static final int ELEMENT_LIGHT = 0;

    // 阶段锁血线
    public static final double FLOOR_P1 = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "FLOOR_P1", 0.65);
    public static final double FLOOR_P2 = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "FLOOR_P2", 0.25);

    // 65% 转场：维摩那·天球巡航（仅此一次，无敌 9s）
    public static final long TRANSITION_65_DURATION_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "TRANSITION_65_DURATION_MS", 9_000L);
    public static final double VIMANA_HOVER_HEIGHT = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "VIMANA_HOVER_HEIGHT", 6.0);
    public static final double VIMANA_ORBIT_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "VIMANA_ORBIT_RADIUS", 22.0);
    public static final long VIMANA_SYNC_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "VIMANA_SYNC_INTERVAL_MS", 250L); // >= 250ms 满足 4Hz 同步红线
    public static final long VIMANA_RING_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "VIMANA_RING_INTERVAL_MS", 2_100L);
    public static final long VIMANA_RING_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "VIMANA_RING_WARNING_MS", 2_030L);
    public static final long VIMANA_ORB_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "VIMANA_ORB_INTERVAL_MS", 1_050L);
    public static final long VIMANA_ORB_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "VIMANA_ORB_WARNING_MS", 1_450L);
    public static final double VIMANA_ORB_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "VIMANA_ORB_RADIUS", 3.0);
    public static final double COEFF_VIMANA_RING = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_VIMANA_RING", 0.85);
    public static final double COEFF_VIMANA_ORB = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_VIMANA_ORB", 0.80);

    // 25% 转场：拔出乖离剑·Ea（无敌 4s）
    public static final long TRANSITION_25_DURATION_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "TRANSITION_25_DURATION_MS", 4_000L);
    public static final double EA_DRAW_HOVER_HEIGHT = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "EA_DRAW_HOVER_HEIGHT", 3.5);
    public static final double EA_DRAW_WAVE_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "EA_DRAW_WAVE_RADIUS", 10.0);
    public static final double COEFF_EA_DRAW_WAVE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_EA_DRAW_WAVE", 0.40);

    // 1% 濒死锁血：乖离终焉 DPS Check
    public static final long FINALE_DPS_RACE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "FINALE_DPS_RACE_MS", 18_000L);
    public static final double FINALE_SHIELD_FRACTION = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "FINALE_SHIELD_FRACTION", 0.10);
    public static final long FINALE_RIFT_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "FINALE_RIFT_INTERVAL_MS", 2_500L);
    public static final int FINALE_RIFT_COUNT = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "FINALE_RIFT_COUNT", 3);
    public static final long FINALE_RIFT_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "FINALE_RIFT_WARNING_MS", 1_740L);
    public static final double FINALE_RIFT_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "FINALE_RIFT_RADIUS", 3.0);
    public static final double COEFF_FINALE_RIFT = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_FINALE_RIFT", 0.65);

    // P3 王财施法加速倍率（前摇压缩 15%）
    public static final double P3_BABYLON_WINDUP_SCALE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "P3_BABYLON_WINDUP_SCALE", 0.85);

    // S1：王之财宝·背后弧阵齐射（CD 6.5s）
    public static final long SKILL1_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL1_CD_MS", 6_500L);
    public static final long SKILL1_WINDUP_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL1_WINDUP_MS", 1_160L);
    public static final long SKILL1_LAUNCH_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL1_LAUNCH_INTERVAL_MS", 75L);
    public static final int SKILL1_COUNT_P1 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL1_COUNT_P1", 8);
    public static final int SKILL1_COUNT_P2 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL1_COUNT_P2", 12);
    public static final int SKILL1_COUNT_P3 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL1_COUNT_P3", 16);
    public static final double SKILL1_SPEED = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL1_SPEED", 28.0);
    public static final double SKILL1_HIT_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL1_HIT_RADIUS", 0.75);
    public static final long SKILL1_PROJECTILE_LIFE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL1_PROJECTILE_LIFE_MS", 2_200L);
    public static final double COEFF_BABYLON_SHOT = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_BABYLON_SHOT", 0.40);
    public static final int SKILL1_MAX_HITS_PER_CAST = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL1_MAX_HITS_PER_CAST", 3);
    public static final long SKILL1_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL1_RECOVERY_MS", 500L);

    // S2：王之财宝·宝具楔阵（倒计时引爆，CD 16s）
    public static final long SKILL2_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL2_CD_MS", 16_000L);
    public static final long SKILL2_CAST_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL2_CAST_MS", 1_090L);
    public static final long SKILL2_DROP_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL2_DROP_WARNING_MS", 1_595L);
    public static final long SKILL2_DETONATE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL2_DETONATE_MS", 5_000L);
    public static final int SKILL2_COUNT_P1 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL2_COUNT_P1", 4);
    public static final int SKILL2_COUNT_P2 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL2_COUNT_P2", 6);
    public static final double SKILL2_DROP_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL2_DROP_RADIUS", 2.4);
    public static final double SKILL2_EXPLODE_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL2_EXPLODE_RADIUS", 6.5);
    public static final double WEDGE_SPECIES_HEALTH = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "WEDGE_SPECIES_HEALTH", 1.6);
    public static final double COEFF_WEDGE_DROP = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_WEDGE_DROP", 0.65);
    public static final double COEFF_WEDGE_EXPLODE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_WEDGE_EXPLODE", 1.25);
    public static final long SKILL2_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL2_RECOVERY_MS", 500L);

    // S3：王之威压·斥退刃环（玩家 < 7 格优先，CD 11s）
    public static final long SKILL3_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL3_CD_MS", 11_000L);
    public static final double SKILL3_TRIGGER_RANGE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL3_TRIGGER_RANGE", 7.0);
    public static final long SKILL3_WINDUP_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL3_WINDUP_MS", 1_235L);
    public static final long SKILL3_ACTIVE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL3_ACTIVE_MS", 300L);
    public static final double SKILL3_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL3_RADIUS", 8.0);
    public static final double SKILL3_KNOCKBACK = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL3_KNOCKBACK", 8.0);
    public static final double COEFF_ROYAL_REPULSE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_ROYAL_REPULSE", 0.75);
    public static final long SKILL3_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL3_RECOVERY_MS", 450L);

    // S4：王之财宝·全方位环绕围杀（P2 起，CD 15s）
    public static final long SKILL4_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL4_CD_MS", 15_000L);
    public static final long SKILL4_WINDUP_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL4_WINDUP_MS", 1_815L);
    public static final long SKILL4_ACTIVE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL4_ACTIVE_MS", 400L);
    public static final int SKILL4_COUNT_P2 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL4_COUNT_P2", 12);
    public static final int SKILL4_COUNT_P3 = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL4_COUNT_P3", 16);
    public static final double SKILL4_RING_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL4_RING_RADIUS", 7.5);
    public static final double SKILL4_GAP_HALF_DEG = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL4_GAP_HALF_DEG", 25.0); // 两处各 50° 逃生缺口
    public static final double SKILL4_SPEED = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL4_SPEED", 26.0);
    public static final double SKILL4_HIT_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL4_HIT_RADIUS", 0.75);
    public static final long SKILL4_PROJECTILE_LIFE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL4_PROJECTILE_LIFE_MS", 1_400L);
    public static final double COEFF_SURROUND_SHOT = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_SURROUND_SHOT", 0.55);
    public static final int SKILL4_MAX_HITS_PER_CAST = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL4_MAX_HITS_PER_CAST", 2);
    public static final long SKILL4_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL4_RECOVERY_MS", 550L);

    // S5：王之财宝·天穹剑雨（P2 起，纯轰炸不残留，CD 18s）
    public static final long SKILL5_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL5_CD_MS", 18_000L);
    public static final long SKILL5_CAST_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL5_CAST_MS", 1_305L);
    public static final int SKILL5_WAVES = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL5_WAVES", 3);
    public static final long SKILL5_WAVE_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL5_WAVE_INTERVAL_MS", 1_200L);
    public static final long SKILL5_DROP_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL5_DROP_WARNING_MS", 1_670L);
    public static final double SKILL5_DROP_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL5_DROP_RADIUS", 2.6);
    public static final int SKILL5_DROPS_PER_WAVE = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL5_DROPS_PER_WAVE", 8);
    public static final double COEFF_SKY_RAIN = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_SKY_RAIN", 0.75);
    public static final long SKILL5_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL5_RECOVERY_MS", 600L);

    // S6：天之锁·Enkidu（P2 起，纯控制 + 被控可放技能击破锁链，不拆则直接处决，CD 20s）
    public static final long SKILL6_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_CD_MS", 20_000L);
    public static final long SKILL6_CAST_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_CAST_MS", 870L);
    public static final int SKILL6_STRIKES = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL6_STRIKES", 3);
    public static final long SKILL6_STRIKE_INTERVAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_STRIKE_INTERVAL_MS", 450L);
    public static final long SKILL6_WARNING_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_WARNING_MS", 1_305L);
    public static final double SKILL6_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL6_RADIUS", 2.6);
    public static final long SKILL6_BIND_DURATION_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_BIND_DURATION_MS", 4_000L);
    public static final double ENKIDU_SPECIES_HEALTH = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "ENKIDU_SPECIES_HEALTH", 1.8);
    public static final double ENKIDU_EXECUTE_RATIO = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "ENKIDU_EXECUTE_RATIO", 0.90);
    public static final long SKILL6_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL6_RECOVERY_MS", 500L);

    // S7：乖离剑·空间撕裂斩（P3 起，CD 9s）
    public static final long SKILL7_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL7_CD_MS", 9_000L);
    public static final long SKILL7_WINDUP_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL7_WINDUP_MS", 1_305L);
    public static final long SKILL7_SLASH_TRAVEL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL7_SLASH_TRAVEL_MS", 550L);
    public static final long SKILL7_COLLAPSE_DELAY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL7_COLLAPSE_DELAY_MS", 1_595L);
    public static final double SKILL7_RANGE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL7_RANGE", 28.0);
    public static final double SKILL7_WIDTH = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL7_WIDTH", 3.0);
    public static final double[] SKILL7_FAN_ANGLES_DEG = BossBalanceRules.current().towerArray("orrery_tower_gilgamesh", "SKILL7_FAN_ANGLES_DEG", new double[]{-25.0, 0.0, 25.0});
    public static final double COEFF_EA_SLASH = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_EA_SLASH", 0.85);
    public static final double COEFF_EA_COLLAPSE = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "COEFF_EA_COLLAPSE", 0.65);
    public static final long SKILL7_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL7_RECOVERY_MS", 550L);

    // S8：天地乖离开辟之星（律令版，纯机制躲外圈星柱护幕，P3 首次必放，后续 CD 42s）
    public static final long SKILL8_CD_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_CD_MS", 42_000L);
    public static final long SKILL8_MOVE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_MOVE_MS", 600L);
    public static final long SKILL8_CHARGE_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_CHARGE_MS", 8_700L);
    public static final long SKILL8_SHELTER_REVEAL_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_SHELTER_REVEAL_MS", 1_000L);
    public static final long SKILL8_SWEEP_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_SWEEP_MS", 1_200L);
    public static final int SKILL8_SHELTER_COUNT = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "SKILL8_SHELTER_COUNT", 2);
    public static final double SKILL8_SHELTER_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL8_SHELTER_RADIUS", 4.5);
    public static final double SKILL8_OUTSIDE_RATIO = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "SKILL8_OUTSIDE_RATIO", 0.95);
    public static final long SKILL8_RECOVERY_MS = BossBalanceRules.current().towerMs("orrery_tower_gilgamesh", "SKILL8_RECOVERY_MS", 1_200L);

    // 星仪塔外圈石柱几何参数（对应 build_tower_structures.py orrery_arena 12 柱）
    public static final int ORRERY_PILLAR_COUNT = BossBalanceRules.current().towerInt("orrery_tower_gilgamesh", "ORRERY_PILLAR_COUNT", 12);
    public static final double ORRERY_SHELTER_RING_RADIUS = BossBalanceRules.current().towerNum("orrery_tower_gilgamesh", "ORRERY_SHELTER_RING_RADIUS", 38.0);

    public enum SkillId {
        BABYLON_VOLLEY("babylon_volley", SKILL1_CD_MS, 1),
        BABYLON_WEDGE("babylon_wedge", SKILL2_CD_MS, 1),
        ROYAL_REPULSE("royal_repulse", SKILL3_CD_MS, 1),
        BABYLON_SURROUND("babylon_surround", SKILL4_CD_MS, 2),
        BABYLON_SKY_RAIN("babylon_sky_rain", SKILL5_CD_MS, 2),
        ENKIDU_CHAINS("enkidu_chains", SKILL6_CD_MS, 2),
        EA_SPACE_REND("ea_space_rend", SKILL7_CD_MS, 3),
        ENUMA_ELISH_DECREE("enuma_elish_decree", SKILL8_CD_MS, 3);

        public final String id;
        public final long cooldownMs;
        public final int minPhase;

        SkillId(String id, long cooldownMs, int minPhase) {
            this.id = id;
            this.cooldownMs = cooldownMs;
            this.minPhase = minPhase;
        }
    }

    public static double[] initialResists() {
        return new double[]{LIGHT_RESIST, FIRE_RESIST, ICE_RESIST, DARK_RESIST};
    }

    public static int volleyCount(int phaseTier) {
        if (phaseTier >= 3) return SKILL1_COUNT_P3;
        if (phaseTier == 2) return SKILL1_COUNT_P2;
        return SKILL1_COUNT_P1;
    }

    public static int wedgeCount(int phaseTier) {
        return phaseTier >= 2 ? SKILL2_COUNT_P2 : SKILL2_COUNT_P1;
    }

    public static int surroundCount(int phaseTier) {
        return phaseTier >= 3 ? SKILL4_COUNT_P3 : SKILL4_COUNT_P2;
    }

    public static long babylonWindup(long baseMs, int phaseTier) {
        return phaseTier >= 3 ? Math.round(baseMs * P3_BABYLON_WINDUP_SCALE) : baseMs;
    }

    /** 背后王之财宝金色涟漪扇阵的第 index 个槽位偏移（right, up, back）。 */
    public static double[] backGateSlotOffset(int index, int total) {
        int outerCount = Math.max(1, (total + 1) / 2);
        boolean outer = index < outerCount;
        int ringIndex = outer ? index : index - outerCount;
        int ringTotal = outer ? outerCount : Math.max(1, total - outerCount);
        double frac = ringTotal <= 1 ? 0.5 : ringIndex / (double) (ringTotal - 1);
        double angle = -Math.PI * 0.66 + frac * Math.PI * 1.32;
        double radius = outer ? 2.55 : 1.72;
        double right = Math.sin(angle) * radius;
        double up = 1.65 + Math.cos(angle) * radius * 0.72 + (outer ? 0.35 : 0.0);
        double back = outer ? 1.15 : 0.78;
        return new double[]{right, up, back};
    }

    /** 计算 360° 环绕围杀中各涟漪门的角度（自动避开对称两处 50° 逃生缺口）。 */
    public static double[] surroundGateAnglesRad(int count, double gapAngleRad) {
        int safeCount = Math.max(2, count);
        double[] angles = new double[safeCount];
        double gapHalfRad = Math.toRadians(SKILL4_GAP_HALF_DEG);
        double usableSpan = Math.PI - 2.0 * gapHalfRad;
        int half = safeCount / 2;
        int secondHalf = safeCount - half;
        for (int i = 0; i < half; i++) {
            double f = half <= 1 ? 0.5 : i / (double) (half - 1);
            angles[i] = gapAngleRad + gapHalfRad + f * usableSpan;
        }
        for (int i = 0; i < secondHalf; i++) {
            double f = secondHalf <= 1 ? 0.5 : i / (double) (secondHalf - 1);
            angles[half + i] = gapAngleRad + Math.PI + gapHalfRad + f * usableSpan;
        }
        return angles;
    }

    /** 计算星仪塔外圈第 index 根星柱内侧的时空护幕安全区中心（固定避开正南入口方向）。 */
    public static Vec3 starPillarShelter(int cell, int pillarIndex) {
        return starPillarShelter(AwakeningTowerLayout.guard(cell), ORRERY_SHELTER_RING_RADIUS, pillarIndex);
    }

    public static Vec3 starPillarShelter(Vec3 center, double ringRadius, int pillarIndex) {
        int idx = Math.floorMod(pillarIndex, ORRERY_PILLAR_COUNT);
        double theta = Math.PI * 2.0 * (idx + 0.5) / ORRERY_PILLAR_COUNT;
        return new Vec3(
            center.x + Math.cos(theta) * ringRadius,
            center.y,
            center.z + Math.sin(theta) * ringRadius
        );
    }

    /** 随机挑选 count 个不相邻的外圈星柱护幕（跳过正南门通道附近的 2、3 号柱）。 */
    public static List<Vec3> pickStarShelters(int cell, int count, Random rng) {
        return pickStarShelters(AwakeningTowerLayout.guard(cell), ORRERY_SHELTER_RING_RADIUS, count, rng);
    }

    public static List<Vec3> pickStarShelters(Vec3 center, double ringRadius, int count, Random rng) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < ORRERY_PILLAR_COUNT; i++) {
            double theta = Math.PI * 2.0 * (i + 0.5) / ORRERY_PILLAR_COUNT;
            // 正南为 +Z (theta ~ PI/2)，避开正南大门窄通道
            if (Math.sin(theta) > 0.88) continue;
            candidates.add(i);
        }
        List<Integer> chosen = new ArrayList<>();
        while (chosen.size() < count && !candidates.isEmpty()) {
            int pick = candidates.remove(rng.nextInt(candidates.size()));
            chosen.add(pick);
            // 尽量不选紧挨着的相邻柱子，增加跑位变化
            candidates.removeIf(c -> Math.abs(c - pick) <= 1 || Math.abs(c - pick) >= ORRERY_PILLAR_COUNT - 1);
        }
        List<Vec3> result = new ArrayList<>(chosen.size());
        for (int idx : chosen) {
            result.add(starPillarShelter(center, ringRadius, idx));
        }
        return result;
    }

    /** 65% 转场维摩那绕场巡航轨迹位置。 */
    public static Vec3 vimanaOrbitPosition(int cell, double startAngleRad, long elapsedMs) {
        return vimanaOrbitPosition(AwakeningTowerLayout.guard(cell), VIMANA_ORBIT_RADIUS, startAngleRad, elapsedMs);
    }

    public static Vec3 vimanaOrbitPosition(Vec3 center, double orbitRadius, double startAngleRad, long elapsedMs) {
        double total = Math.max(1.0, (double) TRANSITION_65_DURATION_MS);
        double progress = Math.clamp(elapsedMs / total, 0.0, 1.0);
        if (progress < 0.12) {
            double t = smooth(progress / 0.12);
            Vec3 orbitStart = center.add(
                Math.cos(startAngleRad) * orbitRadius,
                VIMANA_HOVER_HEIGHT,
                Math.sin(startAngleRad) * orbitRadius
            );
            return center.lerp(orbitStart, t);
        }
        if (progress > 0.88) {
            double t = smooth((progress - 0.88) / 0.12);
            double endAngle = startAngleRad + Math.PI * 2.0;
            Vec3 orbitEnd = center.add(
                Math.cos(endAngle) * orbitRadius,
                VIMANA_HOVER_HEIGHT,
                Math.sin(endAngle) * orbitRadius
            );
            return orbitEnd.lerp(center, t);
        }
        double orbitProgress = (progress - 0.12) / 0.76;
        double angle = startAngleRad + orbitProgress * Math.PI * 2.0;
        double bob = Math.sin(orbitProgress * Math.PI * 6.0) * 0.45;
        return new Vec3(
            center.x + Math.cos(angle) * orbitRadius,
            center.y + VIMANA_HOVER_HEIGHT + bob,
            center.z + Math.sin(angle) * orbitRadius
        );
    }

    /** 校验玩家位置是否位于任一蓝色时空护幕安全区内。 */
    public static boolean insideAnyShelter(Vec3 playerPos, List<Vec3> shelters, double radius) {
        if (shelters == null || shelters.isEmpty()) return false;
        double rSq = radius * radius;
        for (Vec3 s : shelters) {
            double dx = playerPos.x - s.x;
            double dz = playerPos.z - s.z;
            if (dx * dx + dz * dz <= rSq) return true;
        }
        return false;
    }

    private static double smooth(double x) {
        double c = Math.clamp(x, 0.0, 1.0);
        return c * c * (3.0 - 2.0 * c);
    }

    private OrreryTowerGilgamesh() {}
}

package cn.yumis.fight.modules.dungeons.content;

import cn.yumis.fight.modules.dungeons.api.BossBalanceRules;
import cn.yumis.fight.modules.dungeons.api.EnemyBalanceRules;

/**
 * 天空城 · 龙人之塔首领「鲁卡斯（雷光龙枪王）」规则常量与平衡参数入口。
 * <p>
 * 遵循主线 Boss 定位：
 * - 绝不锁血（无 healthFloor、无转阶段无敌），跨越 65% / 30% 阈值时直接释放霸体转阶段技与横幅；
 * - 全部面板与招式参数统一来自 rules/balance/bosses/dungeon_bosses.json 的 dragon_tower_lucas。
 */
public final class DragonTowerLucas {
    public static final String ID = "lucas";
    public static final String CLONE_ID = "lucas_clone";
    public static final String BALANCE_ID = "dragon_tower_lucas";
    public static final String DISPLAY_NAME = "鲁卡斯";
    public static final String CLONE_DISPLAY_NAME = "鲁卡斯的幻影";

    public static final String EVENT_ACTION = "lucasBossEvent";
    public static final String HAZARD_ACTION = "lucasBossHazard";

    // 苍蓝紫电 + 亮金雷芯 + 冷青幻影视觉配色
    public static final int COLOR_SKY_BLUE = 0x6fd8ff;
    public static final int COLOR_VIOLET_BOLT = 0xa67bff;
    public static final int COLOR_GOLD_CORE = 0xfff0b2;
    public static final int COLOR_CLONE_CYAN = 0x68f0ff;
    public static final int COLOR_SAFE_GREEN = 0x4dff9e;
    public static final int COLOR_DANGER_VIOLET = 0xb86bff;

    public static final double[] INITIAL_RESISTS = {15.0, 0.0, 0.0, 0.0};

    // 阶段阈值与狂暴参数（不锁血）
    public static final double DEFAULT_PHASE2_THRESHOLD = 0.65;
    public static final double DEFAULT_PHASE3_THRESHOLD = 0.30;
    public static final double DEFAULT_PHASE3_CD_SCALE = 0.80;
    public static final double DEFAULT_CLONE_HEALTH_RATIO = 0.22;
    public static final double DEFAULT_CLONE_ATTACK_RATIO = 0.55;
    public static final int DEFAULT_CAGE_BREAK_HITS = 3;
    public static final long DEFAULT_CAGE_DURATION_MS = 2500L;

    public enum SkillId {
        SPEAR_COMBO("SPEAR_COMBO", "spear_combo"),
        DRAGON_CHARGE("DRAGON_CHARGE", "dragon_charge"),
        THUNDER_DISCHARGE("THUNDER_DISCHARGE", "thunder_discharge"),
        THUNDER_CAGE("THUNDER_CAGE", "thunder_cage"),
        SKY_SPEAR_RAIN("SKY_SPEAR_RAIN", "sky_spear_rain"),
        JUMP_SLAM("JUMP_SLAM", "jump_slam"),
        CROWN_TEMPEST("CROWN_TEMPEST", "crown_tempest");

        public final String key;
        public final String animation;

        SkillId(String key, String animation) {
            this.key = key;
            this.animation = animation;
        }
    }

    private DragonTowerLucas() {}

    public static BossBalanceRules.DungeonBossEntry rules() {
        return BossBalanceRules.current().dungeonBoss(BALANCE_ID);
    }

    public static double[] initialResists() {
        var r = rules();
        return r != null && r.resists() != null ? r.resists() : INITIAL_RESISTS.clone();
    }

    public static double param(String key, double fallback) {
        var r = rules();
        return r == null ? fallback : r.param(key, fallback);
    }

    public static long paramMs(String key, long fallback) {
        return Math.round(param(key, fallback));
    }

    public static int paramInt(String key, int fallback) {
        return (int) Math.round(param(key, fallback));
    }

    public static EnemyBalanceRules.TimedMoveData move(String key) {
        var r = rules();
        return r == null ? null : r.move(key);
    }

    public static double phase2Threshold() {
        return param("phase2Threshold", DEFAULT_PHASE2_THRESHOLD);
    }

    public static double phase3Threshold() {
        return param("phase3Threshold", DEFAULT_PHASE3_THRESHOLD);
    }

    public static double phase3CooldownScale() {
        return param("phase3CooldownScale", DEFAULT_PHASE3_CD_SCALE);
    }

    public static double cloneHealthRatio() {
        return param("cloneHealthRatio", DEFAULT_CLONE_HEALTH_RATIO);
    }

    public static double cloneAttackRatio() {
        return param("cloneAttackRatio", DEFAULT_CLONE_ATTACK_RATIO);
    }

    public static int cageBreakHits() {
        return Math.max(1, paramInt("cageBreakHits", DEFAULT_CAGE_BREAK_HITS));
    }

    public static long cageDurationMs() {
        return Math.max(1000L, paramMs("cageDurationMs", DEFAULT_CAGE_DURATION_MS));
    }

    public static long moveWindup(String key, long fallback) {
        var m = move(key);
        return m != null && m.windup() > 0 ? m.windup() : fallback;
    }

    public static long moveActive(String key, long fallback) {
        var m = move(key);
        return m != null && m.active() > 0 ? m.active() : fallback;
    }

    public static long moveDuration(String key, long fallback) {
        var m = move(key);
        return m != null && m.duration() > 0 ? m.duration() : fallback;
    }

    public static long moveCooldown(String key, long fallback) {
        var m = move(key);
        return m != null && m.cooldown() > 0 ? m.cooldown() : fallback;
    }

    public static double moveRange(String key, double fallback) {
        var m = move(key);
        return m != null && m.range() > 0 ? m.range() : fallback;
    }

    public static double moveCoeff(String key, double fallback) {
        var m = move(key);
        return m != null && m.coefficient() > 0 ? m.coefficient() : fallback;
    }
}

package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import java.util.*;

/**
 * Pure, shared Battle Mage arithmetic:
 * weapon mastery (Spear / Club / Staff & Wand), Battle Groove combo stages,
 * random/weighted 5-color Chaser generation, independent 5-color Chaser buffs,
 * Fusion Resonance buff, charge interpolation, cancel bonus, and Teana Transformation.
 */
public final class BattleMageRules {
    public static final int COLOR_NEUTRAL = 0xE2E8F0;
    public static final int COLOR_LIGHT = 0xFFE066;
    public static final int COLOR_FIRE = 0xFF6B35;
    public static final int COLOR_ICE = 0x66D9FF;
    public static final int COLOR_DARK = 0xA855F7;
    public static final int COLOR_TEANA = 0xFFD700;
    public static final int COLOR_MARTIAL = 0xFF9F5A;
    public static final double MIN_CHARGE_SECONDS = 0.05;

    private static final int[] BASE_COMBO_THRESHOLDS = {1, 6, 15, 28, 45, 65};
    private static final String[] CHASER_DAMAGE_KEYS = {
        "neutralDamage", "lightDamage", "fireDamage", "iceDamage", "darkDamage"
    };
    private static final String[] CHASER_WEIGHT_KEYS = {
        "neutralWeight", "lightWeight", "fireWeight", "iceWeight", "darkWeight"
    };
    private static final double[] DEFAULT_CHASER_COEFFS = {0.95, 1.00, 1.08, 0.98, 1.05};
    public static final int[] ALL_FOUR_ELEMENTS = {0, 1, 2, 3};

    private BattleMageRules() {}

    public static int chaserColor(int type, boolean teana) {
        if (teana) return COLOR_TEANA;
        return switch (type) {
            case BattleMageState.LIGHT -> COLOR_LIGHT;
            case BattleMageState.FIRE -> COLOR_FIRE;
            case BattleMageState.ICE -> COLOR_ICE;
            case BattleMageState.DARK -> COLOR_DARK;
            default -> COLOR_NEUTRAL;
        };
    }

    public static String chaserName(int type, boolean teana) {
        if (teana) return "贝亚娜炫纹";
        return switch (type) {
            case BattleMageState.LIGHT -> "光炫纹";
            case BattleMageState.FIRE -> "火炫纹";
            case BattleMageState.ICE -> "冰炫纹";
            case BattleMageState.DARK -> "暗炫纹";
            default -> "无炫纹";
        };
    }

    public static String chaserShort(int type) {
        return switch (type) {
            case BattleMageState.LIGHT -> "光";
            case BattleMageState.FIRE -> "火";
            case BattleMageState.ICE -> "冰";
            case BattleMageState.DARK -> "暗";
            default -> "无";
        };
    }

    /** Maps Chaser color index (0..4) to combat element (-1=none, 0=Light, 1=Fire, 2=Ice, 3=Dark). */
    public static int chaserElement(int type) {
        return switch (type) {
            case BattleMageState.LIGHT -> 0;
            case BattleMageState.FIRE -> 1;
            case BattleMageState.ICE -> 2;
            case BattleMageState.DARK -> 3;
            default -> -1;
        };
    }

    public static int[] chaserElements(int type, boolean teana) {
        if (teana) return ALL_FOUR_ELEMENTS.clone();
        int e = chaserElement(type);
        return e < 0 ? new int[0] : new int[]{e};
    }

    public static int[] unionElements(Collection<BattleMageState.Chaser> consumed, boolean forceTeana) {
        if (forceTeana) return ALL_FOUR_ELEMENTS.clone();
        boolean[] present = new boolean[4];
        int count = 0;
        for (var c : consumed) {
            if (c == null) continue;
            if (c.teana) return ALL_FOUR_ELEMENTS.clone();
            int e = chaserElement(c.type);
            if (e >= 0 && !present[e]) {
                present[e] = true;
                count++;
            }
        }
        int[] out = new int[count];
        int idx = 0;
        for (int e = 0; e < 4; e++) {
            if (present[e]) out[idx++] = e;
        }
        return out;
    }

    public static int morphMode(SkillParameters.Snapshot parameters) {
        if (parameters == null) return 0;
        int mode = (int) Math.round(parameters.get("morphMode", 0.0));
        return mode == 1 || mode == 2 ? mode : 0;
    }

    public static int masteryRank(Stats stats, Progress progress) {
        if (progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return 0;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(BattleMageSkills.WEAPON_MASTERY));
    }

    public static int grooveRank(Stats stats, Progress progress) {
        if (progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return 0;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(BattleMageSkills.BATTLE_GROOVE));
    }

    public static int launcherRank(Stats stats, Progress progress) {
        if (progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return 0;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(BattleMageSkills.CHASER_LAUNCHER));
    }

    public static SkillParameters.Snapshot masteryParams(Stats stats, Progress progress) {
        int rank = Math.max(1, masteryRank(stats, progress));
        return SkillParameters.resolve(SkillCatalog.require(BattleMageSkills.WEAPON_MASTERY), rank, stats.parameters);
    }

    public static SkillParameters.Snapshot grooveParams(Stats stats, Progress progress) {
        int rank = Math.max(1, grooveRank(stats, progress));
        return SkillParameters.resolve(SkillCatalog.require(BattleMageSkills.BATTLE_GROOVE), rank, stats.parameters);
    }

    public static SkillParameters.Snapshot launcherParams(Stats stats, Progress progress) {
        int rank = Math.max(1, launcherRank(stats, progress));
        return SkillParameters.resolve(SkillCatalog.require(BattleMageSkills.CHASER_LAUNCHER), rank, stats.parameters);
    }

    // --- Weapon Mastery formulas ---

    public static double spearPhysMut(int rank, double bonusScale) {
        if (rank <= 0) return 0.0;
        return (0.06 + 0.015 * (rank - 1)) * Math.max(0.0, bonusScale);
    }

    public static double spearCrit(int rank) {
        return rank <= 0 ? 0.0 : 0.04 + 0.008 * (rank - 1);
    }

    public static double clubDualMut(int rank, double bonusScale) {
        if (rank <= 0) return 0.0;
        return (0.04 + 0.010 * (rank - 1)) * Math.max(0.0, bonusScale);
    }

    public static double clubCrit(int rank) {
        return rank <= 0 ? 0.0 : 0.03 + 0.006 * (rank - 1);
    }

    public static double clubCdRecovery(int rank) {
        return rank <= 0 ? 0.0 : 0.08 + 0.01 * (rank - 1);
    }

    public static double staffMagicMut(int rank, double bonusScale) {
        if (rank <= 0) return 0.0;
        return (0.06 + 0.014 * (rank - 1)) * Math.max(0.0, bonusScale);
    }

    public static double staffCrit(int rank) {
        return rank <= 0 ? 0.0 : 0.04 + 0.008 * (rank - 1);
    }

    // --- Battle Groove (斗神意志) combo stages ---

    public static int minComboForStage(int stage, double thresholdScale) {
        if (stage <= 0) return 0;
        double scale = Math.max(0.25, thresholdScale);
        int idx = Math.min(stage - 1, BASE_COMBO_THRESHOLDS.length - 1);
        int base = BASE_COMBO_THRESHOLDS[idx];
        if (stage > BASE_COMBO_THRESHOLDS.length) {
            base += (stage - BASE_COMBO_THRESHOLDS.length) * 20;
        }
        return Math.max(1, (int) Math.round(base * scale));
    }

    public static int stageForCombo(int combo, int cap, double thresholdScale) {
        int maxStage = Math.max(1, cap);
        int stage = 0;
        for (int s = 1; s <= maxStage; s++) {
            if (combo >= minComboForStage(s, thresholdScale)) stage = s;
            else break;
        }
        return stage;
    }

    public static double groovePerStageMut(int rank, double bonusScale) {
        if (rank <= 0) return 0.0;
        return (0.015 + 0.0025 * (rank - 1)) * Math.max(0.0, bonusScale);
    }

    public static double autoChaserIntervalSeconds(int rank, SkillParameters.Snapshot grooveParams, Stats stats) {
        double base = grooveParams.get("autoChaserInterval", 4.0);
        double rankFactor = Math.max(0.65, 1.0 - 0.025 * Math.max(0, rank - 1));
        double clubScale = stats != null ? stats.modifiers.getOrDefault("bmAutoChaserScale", 1.0) : 1.0;
        return Math.max(0.25, base * rankFactor * Math.max(0.2, clubScale));
    }

    // --- 5-Color Chaser Buffs & Damage ---

    public static double chaserCoefficient(SkillParameters.Snapshot launcherParams, int type, boolean teana, double teanaScale) {
        int clamped = Math.clamp(type, 0, 4);
        double coeff = launcherParams.get(CHASER_DAMAGE_KEYS[clamped], DEFAULT_CHASER_COEFFS[clamped]);
        return teana ? coeff * Math.max(1.0, teanaScale) : coeff;
    }

    public static double[] chaserProbabilities(SkillParameters.Snapshot grooveParams) {
        double[] probs = new double[5];
        if (grooveParams != null && grooveParams.get("forcedChaserEnabled", 0.0) >= 1.0) {
            int forced = Math.clamp((int) Math.round(grooveParams.get("forcedChaserType", 0.0)), 0, 4);
            probs[forced] = 1.0;
            return probs;
        }
        double sum = 0.0;
        for (int i = 0; i < 5; i++) {
            double w = grooveParams == null ? 1.0 : Math.max(0.0, grooveParams.get(CHASER_WEIGHT_KEYS[i], 1.0));
            probs[i] = w;
            sum += w;
        }
        if (sum <= 1e-9) {
            Arrays.fill(probs, 0.20);
        } else {
            for (int i = 0; i < 5; i++) probs[i] /= sum;
        }
        return probs;
    }

    /** Rolls a random 5-color Chaser using configured weights (with mild 2-streak anti-repeat smoothing). */
    public static int rollChaserColor(SkillParameters.Snapshot grooveParams, BattleMageState state, Random rng) {
        if (grooveParams != null && grooveParams.get("forcedChaserEnabled", 0.0) >= 1.0) {
            return Math.clamp((int) Math.round(grooveParams.get("forcedChaserType", 0.0)), 0, 4);
        }
        double[] weights = new double[5];
        double sum = 0.0;
        boolean streak = state != null && state.lastColor >= 0 && state.lastColor == state.secondLastColor;
        for (int i = 0; i < 5; i++) {
            double w = grooveParams == null ? 1.0 : Math.max(0.0, grooveParams.get(CHASER_WEIGHT_KEYS[i], 1.0));
            if (streak && i == state.lastColor) w *= 0.5;
            weights[i] = w;
            sum += w;
        }
        if (sum <= 1e-9) return rng.nextInt(5);
        double pick = rng.nextDouble() * sum;
        double acc = 0.0;
        for (int i = 0; i < 5; i++) {
            acc += weights[i];
            if (pick <= acc) return i;
        }
        return 4;
    }

    public static double neutralMoveSpeed(int rank, SkillParameters.Snapshot p) {
        return Math.max(0.0, p.get("neutralMoveSpeed", 0.15) + 0.01 * Math.max(0, rank - 1));
    }

    public static double lightAttackSpeed(int rank, SkillParameters.Snapshot p) {
        return Math.max(0.0, p.get("lightAttackSpeed", 0.12) + 0.01 * Math.max(0, rank - 1));
    }

    public static double fireDamageMut(int rank, SkillParameters.Snapshot p) {
        return Math.max(0.0, p.get("fireDamageMut", 0.05) + 0.0075 * Math.max(0, rank - 1));
    }

    public static double iceDamageReduction(int rank, SkillParameters.Snapshot p) {
        return Math.clamp(p.get("iceDamageReduction", 0.08) + 0.005 * Math.max(0, rank - 1), 0.0, 0.25);
    }

    public static double darkCritBonus(int rank, SkillParameters.Snapshot p) {
        return Math.max(0.0, p.get("darkCritBonus", 0.08) + 0.01 * Math.max(0, rank - 1));
    }

    public static double fusionAttackBonus(int rank, SkillParameters.Snapshot p) {
        return Math.max(0.0, p.get("fusionAttackBonus", 0.08) + 0.01 * Math.max(0, rank - 1));
    }

    // --- Charge arithmetic (落花掌 & 强袭流星打) ---

    public static boolean isChargeable(SkillDefinition skill, Progress progress, SkillParameters.Snapshot parameters) {
        if (skill == null || progress == null || parameters == null) return false;
        if (!progress.hasJob(JobCatalog.BATTLE_MAGE)) return false;
        if (!BattleMageSkills.PALM_BLAST.equals(skill.id) && !BattleMageSkills.DRAGON_STRIKE.equals(skill.id)) return false;
        return parameters.get("chargeSeconds", 0.0) > 0.0;
    }

    public static double effectiveChargeSeconds(double baseChargeSeconds, Stats stats) {
        if (!(baseChargeSeconds > 0)) return 0.0;
        double weaponScale = stats == null ? 1.0 : Math.max(0.2, stats.modifiers.getOrDefault("bmChargeScale", 1.0));
        double attackSpeedMul = stats == null ? 1.0 : Math.max(0.2, 1.0 + stats.get(Stat.ATTACK_SPEED) / 100.0);
        return Math.max(MIN_CHARGE_SECONDS, baseChargeSeconds * weaponScale / attackSpeedMul);
    }

    public static double chargeDamageMultiplier(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        double u = Math.clamp(progress, 0.0, 1.0);
        double min = parameters.get("minChargeRatio", 0.80);
        double max = parameters.get("chargeDamageMultiplier", 1.30);
        return min + (max - min) * u;
    }

    public static double chargeDashMultiplier(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        double u = Math.clamp(progress, 0.0, 1.0);
        double max = Math.max(1.0, parameters.get("chargeDashMultiplier", 1.50));
        return 1.0 + (max - 1.0) * u;
    }

    public static double chargeScaleMultiplier(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        double u = Math.clamp(progress, 0.0, 1.0);
        double max = Math.max(1.0, parameters.get("chargeScaleMultiplier", 1.25));
        return 1.0 + (max - 1.0) * u;
    }

    public static double fullChargeMultiplier(SkillParameters.Snapshot parameters) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        return Math.max(0.0, parameters.get("chargeDamageMultiplier", 1.30));
    }

    // --- Live Stats aggregation (called from CareerRules.current) ---

    public static Stats current(Stats source, Progress progress, BattleMageState state, long now) {
        if (progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return source;
        var result = source.copy();

        // 1. Weapon Mastery (strictly separates physical Spear vs dual Club vs magic Staff/Wand)
        int mRank = masteryRank(source, progress);
        if (mRank > 0) {
            var mp = masteryParams(source, progress);
            String w = source.weaponType == null ? "" : source.weaponType;
            if ("SPEAR".equals(w)) {
                result.mut(Stat.PHYSICAL_ATTACK, spearPhysMut(mRank, mp.get("spearBonusScale", 1.0)));
                result.modifiers.merge("crit", spearCrit(mRank), Double::sum);
                result.extra(Stat.ATTACK_SPEED, 10.0);
                result.modifiers.put("bmMeleeRangeScale", mp.get("spearRangeScale", 1.15));
                result.modifiers.put("bmChargeScale", mp.get("spearChargeScale", 0.85));
            } else if ("CLUB".equals(w)) {
                double dual = clubDualMut(mRank, mp.get("clubBonusScale", 1.0));
                result.mut(Stat.PHYSICAL_ATTACK, dual);
                result.mut(Stat.MAGICAL_ATTACK, dual);
                result.modifiers.merge("crit", clubCrit(mRank), Double::sum);
                result.extra(Stat.ATTACK_SPEED, 8.0);
                result.modifiers.merge("cooldownRecovery", clubCdRecovery(mRank), Double::sum);
                result.modifiers.put("bmAutoChaserScale", mp.get("clubAutoChaserScale", 0.80));
                result.modifiers.put("bmChaserRadiusScale", mp.get("clubChaserRadiusScale", 1.15));
            } else if ("STAFF".equals(w) || "WAND".equals(w)) {
                result.mut(Stat.MAGICAL_ATTACK, staffMagicMut(mRank, mp.get("staffBonusScale", 1.0)));
                result.modifiers.merge("crit", staffCrit(mRank), Double::sum);
                result.modifiers.put("bmChaserCdScale", mp.get("staffChaserCdScale", 0.85));
            }
        }

        // 2. Battle Groove (斗神意志) combo stage scaling
        int gRank = grooveRank(source, progress);
        if (gRank > 0 && state != null && state.grooveStage > 0) {
            var gp = grooveParams(source, progress);
            int stage = Math.min(state.grooveStage, (int) Math.max(1, gp.get("comboStageCap", 6.0)));
            double stageMut = stage * groovePerStageMut(gRank, gp.get("grooveBonusScale", 1.0));
            double stageCrit = stage * gp.get("perStageCrit", 0.015);
            result.modifier("final").mut += stageMut;
            result.modifiers.merge("crit", stageCrit, Double::sum);
        }

        // 3. Independent 5-Color Chaser Buffs
        if (state != null && state.buffUntil != null) {
            int lRank = Math.max(1, launcherRank(source, progress));
            var lp = launcherParams(source, progress);
            if (state.buffUntil[BattleMageState.NEUTRAL] > now) {
                result.mut(Stat.MOVEMENT_SPEED, neutralMoveSpeed(lRank, lp));
            }
            if (state.buffUntil[BattleMageState.LIGHT] > now) {
                result.extra(Stat.ATTACK_SPEED, lightAttackSpeed(lRank, lp) * 100.0);
            }
            if (state.buffUntil[BattleMageState.FIRE] > now) {
                result.modifier("final").mut += fireDamageMut(lRank, lp);
            }
            if (state.buffUntil[BattleMageState.ICE] > now) {
                result.modifiers.put("bmIceDr", iceDamageReduction(lRank, lp));
            }
            if (state.buffUntil[BattleMageState.DARK] > now) {
                result.modifiers.merge("crit", darkCritBonus(lRank, lp), Double::sum);
            }
        }

        // 4. Fusion Resonance buff (physical + magical attack & crit)
        if (state != null && state.fusionUntil > now) {
            if (state.fusionAttackBonus > 0) {
                result.mut(Stat.PHYSICAL_ATTACK, state.fusionAttackBonus);
                result.mut(Stat.MAGICAL_ATTACK, state.fusionAttackBonus);
            }
            if (state.fusionCritBonus > 0) {
                result.modifiers.merge("crit", state.fusionCritBonus, Double::sum);
            }
        }

        // 5. Teana Transformation active state
        if (state != null && state.transformed(now)) {
            int tRank = Math.max(1, SkillLearning.effective(source, progress, SkillCatalog.require(BattleMageSkills.TEANA_TRANSFORMATION)));
            var tp = SkillParameters.resolve(SkillCatalog.require(BattleMageSkills.TEANA_TRANSFORMATION), tRank, source.parameters);
            result.extra(Stat.ATTACK_SPEED, tp.get("teanaAttackSpeed", 0.20) * 100.0);
            result.mut(Stat.MOVEMENT_SPEED, tp.get("teanaMoveSpeed", 0.20));
            result.modifiers.merge("crit", tp.get("teanaCritBonus", 0.12), Double::sum);
            result.modifiers.put("bmTeana", 1.0);
            result.modifiers.put("bmTeanaSkillScale", tp.get("teanaSkillScale", 1.30));
            result.modifiers.put("bmTeanaChaserScale", tp.get("teanaChaserScale", 1.45));
        }

        return result;
    }

    /** Records combo hits and advances Battle Groove stage. */
    public static void onHitCombo(BattleMageState state, int hits, Stats stats, Progress progress, long now) {
        if (state == null || hits <= 0 || progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return;
        int gRank = grooveRank(stats, progress);
        if (gRank <= 0) return;
        var gp = grooveParams(stats, progress);
        int cap = (int) Math.max(1, gp.get("comboStageCap", 6.0));
        double scale = gp.get("comboThresholdScale", 1.0);
        long holdMs = Math.max(1000L, Math.round(gp.get("comboHoldSeconds", 8.0) * 1000.0));
        state.comboCount = Math.min(999, state.comboCount + hits);
        state.grooveStage = stageForCombo(state.comboCount, cap, scale);
        state.comboHoldUntil = now + holdMs;
        state.nextComboDecayAt = state.comboHoldUntil;
        state.revision++;
    }

    /** Generates random 5-color Chasers (honoring forcedChaserEnabled/forcedChaserType and Teana state). */
    public static void generateChasers(BattleMageState state, int count, Stats stats, Progress progress, long now, Random rng) {
        if (state == null || count <= 0 || progress == null || !progress.hasJob(JobCatalog.BATTLE_MAGE)) return;
        var gp = grooveParams(stats, progress);
        var lp = launcherParams(stats, progress);
        int maxCap = (int) Math.max(1, lp.get("maxChasers", 7.0));
        long chaserMs = Math.max(1000L, Math.round(lp.get("chaserDuration", 25.0) * 1000.0));
        boolean teana = state.transformed(now);
        for (int i = 0; i < count; i++) {
            int color = rollChaserColor(gp, state, rng);
            state.pushChaser(color, now + chaserMs, teana, maxCap);
        }
    }

    /** Triggers/refreshes the personal Buff for a fired Chaser color. */
    public static void applyChaserBuff(BattleMageState state, int color, SkillParameters.Snapshot launcherParams, long now) {
        if (state == null) return;
        if (state.buffUntil == null || state.buffUntil.length < 5) state.buffUntil = new long[5];
        int c = Math.clamp(color, 0, 4);
        long durMs = Math.max(1000L, Math.round(launcherParams.get("buffDuration", 20.0) * 1000.0));
        state.buffUntil[c] = now + durMs;
        state.revision++;
    }

    public static String skillTitle(SkillDefinition skill, SkillParameters.Snapshot parameters, boolean teana) {
        if (skill == null) return "";
        if (teana) {
            if (BattleMageSkills.PALM_BLAST.equals(skill.id)) return "苍天击";
            if (BattleMageSkills.FULL_SWING.equals(skill.id)) return "天地碎霸";
        }
        int mode = morphMode(parameters);
        if (mode > 0) {
            var preset = BattleMageMorphs.current().find(skill.id, mode);
            if (preset != null) return skill.title + " · " + preset.title();
        }
        return skill.title;
    }

    /** Returns true for Battle Mage skills (and base Dragon Fang) that participate in martial cancel chains. */
    public static boolean isCancelSource(String skillId) {
        return MageSkills.FANG.equals(skillId)
            || BattleMageSkills.PALM_BLAST.equals(skillId)
            || BattleMageSkills.FULL_SWING.equals(skillId)
            || BattleMageSkills.PUNTO_WAVE.equals(skillId);
    }

    public static boolean isCancelTarget(String skillId) {
        return MageSkills.FANG.equals(skillId)
            || BattleMageSkills.PALM_BLAST.equals(skillId)
            || BattleMageSkills.FULL_SWING.equals(skillId)
            || BattleMageSkills.CHASER_FUSION.equals(skillId)
            || BattleMageSkills.CHASER_PRESS.equals(skillId)
            || BattleMageSkills.PUNTO_WAVE.equals(skillId)
            || BattleMageSkills.DRAGON_STRIKE.equals(skillId)
            || BattleMageSkills.DRAGON_SPEAR.equals(skillId)
            || BattleMageSkills.TEANA_TRANSFORMATION.equals(skillId);
    }
}

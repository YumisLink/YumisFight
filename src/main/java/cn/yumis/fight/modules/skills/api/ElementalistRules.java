package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.*;

/** Pure, shared Elementalist arithmetic: Moving Cast, Elemental Burn, dyeing and Astral Storm probability weights. */
public final class ElementalistRules {
    public static final int[] ALL_FOUR_ELEMENTS = {0, 1, 2, 3};
    public static final int COLOR_LIGHT = 0xFFE066;
    public static final int COLOR_FIRE = 0xFF7B39;
    public static final int COLOR_ICE = 0x75D8F5;
    public static final int COLOR_DARK = 0xA864F5;
    public static final int COLOR_PRISM = 0xE0C8FF;
    public static final double MIN_CHARGE_SECONDS = 0.05;
    public static final double MAX_MEMORIZE_REDUCTION = 0.60;
    private static final Set<String> CHARGE_DAMAGE_KEYS = Set.of("damage", "finisher", "emberDot", "pulseDot", "volcanoDot");
    private static final Set<String> CHARGE_SCALE_KEYS = Set.of("radius", "blastRadius", "shockwaveRadius", "pullRadius");

    public enum PostChantAction {
        RELEASE_DIRECT,
        RELEASE_UNCHARGED,
        BEGIN_CHARGE
    }

    private ElementalistRules() {}

    public static int elementColor(int element) {
        return switch (element) {
            case 0 -> COLOR_LIGHT;
            case 1 -> COLOR_FIRE;
            case 2 -> COLOR_ICE;
            case 3 -> COLOR_DARK;
            default -> COLOR_PRISM;
        };
    }

    public static String elementName(int element) {
        return switch (element) {
            case 0 -> "光";
            case 1 -> "火";
            case 2 -> "冰";
            case 3 -> "暗";
            default -> "全元素";
        };
    }

    public static boolean movingCastLearned(Stats stats, Progress progress) {
        if (!progress.hasJob(JobCatalog.ELEMENTALIST)) return false;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(ElementalistSkills.MOVING_CAST)) > 0;
    }

    public static int burnRank(Stats stats, Progress progress) {
        if (!progress.hasJob(JobCatalog.ELEMENTALIST)) return 0;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(ElementalistSkills.ELEMENTAL_BURN));
    }

    public static SkillParameters.Snapshot burnParameters(Stats stats, Progress progress) {
        int rank = Math.max(1, burnRank(stats, progress));
        var burnSkill = SkillCatalog.require(ElementalistSkills.ELEMENTAL_BURN);
        return SkillParameters.resolve(burnSkill, rank, stats.parameters);
    }

    public static double perLampMut(int rank, double perLampBonusScale) {
        if (rank <= 0) return 0.0;
        return (0.03 + 0.004 * (rank - 1)) * Math.max(0.0, perLampBonusScale);
    }

    public static double fullLampMut(int rank, double perLampBonusScale, double allLampsBonusMut) {
        if (rank <= 0) return 0.0;
        return 4.0 * perLampMut(rank, perLampBonusScale) + Math.max(0.0, allLampsBonusMut);
    }

    public static double allLampsAllElem(int rank, double baseAllElem) {
        if (rank <= 0) return 0.0;
        return Math.max(0.0, baseAllElem) + 2.0 * (rank - 1);
    }

    public static double memorizeReduction(int rank, double memorizeBonus) {
        if (rank <= 0) return 0.0;
        return Math.min(MAX_MEMORIZE_REDUCTION, Math.max(0.0, 0.15 + 0.03 * (rank - 1) + memorizeBonus));
    }

    /** Returns cast speed bonus in percentage points (e.g. 10.0 at rank 1 = +10%). */
    public static double castSpeedBonusPercent(int rank, double festivalSpeedScale) {
        if (rank <= 0) return 0.0;
        return (10.0 + 2.0 * (rank - 1)) * Math.max(0.0, festivalSpeedScale);
    }

    public static double cooldownRecoveryBonus(int rank, double festivalCdRecoveryAdd) {
        if (rank <= 0) return 0.0;
        return Math.max(0.0, 0.12 + 0.015 * (rank - 1) + festivalCdRecoveryAdd);
    }

    /**
     * Effective full-charge duration in seconds:
     * max(0.05, 3 * chargeSeconds * (1 - memorizeReduction) / castSpeedMultiplier).
     * Returns 0 for non-chargeable skills.
     */
    public static double effectiveChargeSeconds(double baseChargeSeconds, Stats stats) {
        if (!(baseChargeSeconds > 0)) return 0.0;
        double memorize = stats == null ? 0.0 : Math.clamp(stats.modifiers.getOrDefault("memorizeReduction", 0d), 0.0, 0.90);
        double castSpeedMultiplier = stats == null ? 1.0 : Math.max(0.01, stats.get(Stat.CAST_SPEED));
        return Math.max(MIN_CHARGE_SECONDS, 3.0 * baseChargeSeconds * (1.0 - memorize) / castSpeedMultiplier);
    }

    public static boolean usesCastTime(SkillDefinition skill, Progress progress) {
        return !skill.passive() && JobCatalog.MAGE.equals(skill.baseJob) && progress.hasJob(JobCatalog.ELEMENTALIST);
    }

    public static double effectiveCastSeconds(SkillDefinition skill, double castSpeed) {
        double base = skill.chantSeconds > 0.0 ? skill.chantSeconds : ElementalistSkills.DEFAULT_CAST_SECONDS;
        return Math.max(0.05, base / Math.max(0.01, castSpeed));
    }

    public static double fullChargeMultiplier(SkillParameters.Snapshot parameters) {
        if (parameters == null) return 1.0;
        if (parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        return Math.max(0.0, parameters.get("chargeDamageMultiplier", 1.0));
    }

    public static double chargeDamageMultiplier(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        double u = Math.clamp(progress, 0.0, 1.0);
        double min = parameters.get("minChargeRatio", 0.75);
        double max = parameters.get("chargeDamageMultiplier", 1.25);
        return min + (max - min) * u;
    }

    public static double chargeScaleMultiplier(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return 1.0;
        double u = Math.clamp(progress, 0.0, 1.0);
        double maxScale = Math.max(1.0, parameters.get("chargeScaleMultiplier", 1.25));
        if (ElementalistSkills.VOID_SPHERE.equals(parameters.skill().id)) {
            return maxScale * (0.25 + 0.75 * u);
        }
        return 1.0 + (maxScale - 1.0) * u;
    }

    public static boolean isChargeable(SkillDefinition skill, Progress progress, SkillParameters.Snapshot parameters) {
        if (skill == null || progress == null || parameters == null) return false;
        if (!progress.hasJob(JobCatalog.ELEMENTALIST)) return false;
        if (!ElementalistSkills.contains(skill.id) && !MageSkills.JACK.equals(skill.id)) return false;
        return parameters.get("chargeSeconds", 0.0) > 0.0;
    }

    /** Decides what happens when Elementalist chant completes: non-chargeable releases directly, released key fires uncharged, held key enters charge. */
    public static PostChantAction postChantAction(boolean chargeable, boolean keyReleased) {
        if (!chargeable) return PostChantAction.RELEASE_DIRECT;
        return keyReleased ? PostChantAction.RELEASE_UNCHARGED : PostChantAction.BEGIN_CHARGE;
    }

    /** Applies Elementalist charge damage/scale multipliers (and full-charge Lightning Vortex bonus orb) to a cast snapshot. */
    public static SkillParameters.Snapshot applyChargeParameters(SkillParameters.Snapshot parameters, double progress) {
        if (parameters == null || parameters.get("chargeSeconds", 0.0) <= 0.0) return parameters;
        double u = Math.clamp(progress, 0.0, 1.0);
        double dmgMul = chargeDamageMultiplier(parameters, u);
        double scaleMul = chargeScaleMultiplier(parameters, u);
        var values = new LinkedHashMap<>(parameters.values());
        for (String key : CHARGE_DAMAGE_KEYS) {
            if (values.containsKey(key)) values.put(key, values.get(key) * dmgMul);
        }
        for (String key : CHARGE_SCALE_KEYS) {
            if (values.containsKey(key) && values.get(key) > 0) values.put(key, values.get(key) * scaleMul);
        }
        if (ElementalistSkills.LIGHTNING_VORTEX.equals(parameters.skill().id)
            && morphMode(parameters) == 0 && u >= 0.999) {
            values.put("orbCount", values.getOrDefault("orbCount", 4.0) + 1.0);
        }
        return new SkillParameters.Snapshot(parameters.skill(), parameters.rank(), values);
    }

    public static int voidSphereTicks(SkillParameters.Snapshot p) {
        double duration = Math.max(0.05, p.get("duration", 4.0));
        double interval = Math.max(0.05, p.get("interval", 0.25));
        return Math.max(1, (int) Math.floor(duration / interval + 1e-9));
    }

    /** Applies Elemental Burn (四灯增伤共鸣 + 魔法记忆 + 魔法秀常驻) to the live Stats snapshot. */
    public static Stats current(Stats source, Progress progress, ElementalistState state, long now) {
        if (!progress.hasJob(JobCatalog.ELEMENTALIST)) return source;
        int rank = burnRank(source, progress);
        if (rank <= 0) return source;
        var result = source.copy();
        var burnParams = burnParameters(source, progress);
        double memorize = memorizeReduction(rank, burnParams.get("memorizeBonus", 0.0));
        double castSpeedPct = castSpeedBonusPercent(rank, burnParams.get("festivalSpeedScale", 1.0));
        double cdRecovery = cooldownRecoveryBonus(rank, burnParams.get("festivalCdRecoveryAdd", 0.0));

        result.modifiers.put("memorizeReduction", memorize);
        result.mut(Stat.CAST_SPEED, castSpeedPct / 100.0);
        result.mut(Stat.COOLDOWN_RECOVERY, cdRecovery);

        int lit = state == null ? 0 : state.litCount(now);
        result.modifiers.put("elementalistBurnRank", (double) rank);
        result.modifiers.put("elementalistLampsLit", (double) lit);
        if (lit > 0) {
            double perLamp = perLampMut(rank, burnParams.get("perLampBonusScale", 1.0));
            double mutBonus = lit * perLamp;
            if (lit >= 4) {
                mutBonus += burnParams.get("allLampsBonusMut", 0.06);
                double allElem = allLampsAllElem(rank, burnParams.get("allLampsAllElem", 12.0));
                result.extra(Stat.LIGHT, allElem);
                result.extra(Stat.FIRE, allElem);
                result.extra(Stat.ICE, allElem);
                result.extra(Stat.DARK, allElem);
            }
            result.modifier("final").mut += mutBonus;
        }
        return result;
    }

    /**
     * Lights rune lamp(s) when a skill is successfully released.
     * ALWAYS uses innateElement (never dyed element) so pure-element dye builds still light all 4 lamps.
     */
    public static ElementalistState onSkillReleased(ElementalistState state, SkillDefinition skill, Progress progress, Stats stats, long now) {
        if (state == null || skill == null || !progress.hasJob(JobCatalog.ELEMENTALIST)) return state;
        int rank = burnRank(stats, progress);
        if (rank <= 0) return state;
        var burnParams = burnParameters(stats, progress);
        long durationMs = Math.max(1000L, Math.round(burnParams.get("lampDuration", 30.0) * 1000.0));
        var next = state.copy();
        boolean changed = false;

        if (ElementalistSkills.ASTRAL_STORM.equals(skill.id) || isPrismActive(stats)) {
            for (int i = 0; i < 4; i++) {
                next.lampUntil[i] = Math.max(next.lampUntil[i], now + durationMs);
            }
            changed = true;
        } else {
            int innate = ElementalistSkills.innateElement(skill.id);
            if (innate >= 0 && innate < 4) {
                next.lampUntil[innate] = Math.max(next.lampUntil[innate], now + durationMs);
                changed = true;
            }
            if (burnParams.get("autoRefreshAllOnUpper", 0.0) >= 1.0 && ElementalistSkills.isUpperSkill(skill.id)) {
                for (int i = 0; i < 4; i++) {
                    if (next.lampUntil[i] > now) {
                        next.lampUntil[i] = Math.max(next.lampUntil[i], now + durationMs);
                        changed = true;
                    }
                }
            }
        }
        if (changed) next.revision++;
        return next;
    }

    /**
     * Unified elemental dyeing resolver:
     * Priority: single-skill dye (dyeEnabled >= 1 -> dyeElement) > global dye (stats.modifiers["elementalDye"] >= 0) > innateElement.
     */
    public static int effectiveElement(SkillParameters.Snapshot params, Stats stats, int innateElement) {
        if (params != null && params.get("dyeEnabled", 0.0) >= 1.0) {
            return (int) Math.clamp(Math.round(params.get("dyeElement", 0.0)), 0, 3);
        }
        if (stats != null) {
            double skillDye = stats.modifiers.getOrDefault("skillDyeElement", -1.0);
            if (skillDye >= 0.0) return (int) Math.clamp(Math.round(skillDye), 0, 3);
            double globalDye = stats.modifiers.getOrDefault("elementalDye", -1.0);
            if (globalDye >= 0.0) return (int) Math.clamp(Math.round(globalDye), 0, 3);
        }
        return innateElement;
    }

    public static Stats prepareCastStats(Stats source, SkillParameters.Snapshot params) {
        if (source == null || params == null) return source;
        if (params.get("dyeEnabled", 0.0) < 1.0) return source;
        var copy = source.copy();
        copy.modifiers.put("skillDyeElement", (double) Math.clamp(Math.round(params.get("dyeElement", 0.0)), 0, 3));
        return copy;
    }

    public static boolean isPrismActive(Stats stats) {
        return TowerBattleCard.ELEM_BURN_PRISM.held(stats);
    }

    public static boolean isInstantFullCharge(SkillDefinition skill, Stats stats) {
        return skill != null && ElementalistSkills.VOID_SPHERE.equals(skill.id)
            && TowerBattleCard.ELEM_VOID_MULTICHARGE.held(stats);
    }

    public static double voidCrystalFinisher(SkillParameters.Snapshot parameters, Stats stats) {
        if (parameters == null || !TowerBattleCard.ELEM_VOID_CRYSTAL.held(stats)) return 0.0;
        return voidSphereTicks(parameters) * parameters.get("damage", 0.0) * (0.50 / 1.30);
    }

    public static int effectiveMorphMode(String skillId, SkillParameters.Snapshot parameters, Stats stats) {
        int mode = morphMode(parameters);
        if (mode != 0 || stats == null || skillId == null) return mode;
        return switch (skillId) {
            case ElementalistSkills.ICE_WALL -> TowerBattleCard.ELEM_ICE_WALL_WAVE.held(stats) ? 1 : 0;
            case ElementalistSkills.LIGHTNING_VORTEX -> TowerBattleCard.ELEM_VORTEX_RAILGUN.held(stats) ? 1 : 0;
            case ElementalistSkills.VOID_SPHERE -> TowerBattleCard.ELEM_VOID_CRYSTAL.held(stats) ? 1 : 0;
            case ElementalistSkills.FLAME_PILLAR -> TowerBattleCard.ELEM_FLAME_CHAIN.held(stats) ? 2 : 0;
            case ElementalistSkills.HALLOWEEN_BUSTER -> TowerBattleCard.ELEM_BUSTER_COLOSSUS.held(stats) ? 1 : 0;
            case ElementalistSkills.ARCTIC_FEAST -> TowerBattleCard.ELEM_ARCTIC_THRONE.held(stats) ? 1 : 0;
            case ElementalistSkills.BLACK_HOLE -> TowerBattleCard.ELEM_BH_SINGULARITY.held(stats) ? 1 : 0;
            case ElementalistSkills.LIGHTNING_STRIKE -> TowerBattleCard.ELEM_STRIKE_CLOUD.held(stats) ? 2 : 0;
            default -> 0;
        };
    }

    public static int[] previewElements(SkillDefinition skill, Stats stats, int[] original) {
        if (skill == null || stats == null) return original;
        if (!skill.passive() && isPrismActive(stats)) {
            return ALL_FOUR_ELEMENTS;
        }
        if (!ElementalistSkills.contains(skill.id) && !MageSkills.JACK.equals(skill.id)) return original;
        int innate = ElementalistSkills.innateElement(skill.id);
        int eff = effectiveElement(null, stats, innate);
        if (eff >= 0 && (original.length != 1 || original[0] != eff)) {
            return new int[]{eff};
        }
        return original;
    }

    /** Astral Storm meteor probability distribution proportional to max(0, elemental strength). */
    public static double[] meteorProbabilities(Stats stats) {
        double[] strengths = new double[4];
        if (stats != null) {
            strengths[0] = Math.max(0.0, stats.get(Stat.LIGHT));
            strengths[1] = Math.max(0.0, stats.get(Stat.FIRE));
            strengths[2] = Math.max(0.0, stats.get(Stat.ICE));
            strengths[3] = Math.max(0.0, stats.get(Stat.DARK));
        }
        double sum = strengths[0] + strengths[1] + strengths[2] + strengths[3];
        if (!(sum > 0.0) || !Double.isFinite(sum)) {
            return new double[]{0.25, 0.25, 0.25, 0.25};
        }
        return new double[]{strengths[0] / sum, strengths[1] / sum, strengths[2] / sum, strengths[3] / sum};
    }

    public static int sampleMeteorElement(Stats stats, Random random) {
        double[] probs = meteorProbabilities(stats);
        double roll = random.nextDouble();
        double acc = 0.0;
        for (int e = 0; e < 4; e++) {
            acc += probs[e];
            if (roll < acc) return e;
        }
        return 3;
    }

    public static int morphMode(SkillParameters.Snapshot parameters) {
        if (parameters == null) return 0;
        int mode = (int) Math.round(parameters.get("morphMode", 0.0));
        return mode == 1 || mode == 2 ? mode : 0;
    }

    public static String skillTitle(SkillDefinition skill, SkillParameters.Snapshot parameters) {
        int mode = morphMode(parameters);
        if (mode == 0) return skill.title;
        var preset = ElementalistMorphs.current().find(skill.id, mode);
        return preset == null ? skill.title : skill.title + " · " + preset.title();
    }

    public record IceWallAbsorb(boolean absorbed, double remainingShield, boolean shattered, double discardedOverflow) {}

    /** Checks whether a player position (feet) lies inside an active Ice Wall cylinder. */
    public static boolean insideIceWall(double px, double py, double pz,
                                        double cx, double cy, double cz,
                                        double radius, double wallHeight) {
        if (!(radius > 0.0) || !Double.isFinite(radius)) return false;
        double dx = px - cx;
        double dz = pz - cz;
        if (dx * dx + dz * dz > radius * radius + 1e-9) return false;
        double dy = py - cy;
        double maxY = Math.max(3.0, Math.max(0.0, wallHeight) + 1.0);
        return dy >= -1.0 - 1e-9 && dy <= maxY + 1e-9;
    }

    /**
     * Resolves Ice Wall absorption for a single incoming hit.
     * Any single hit that meets or exceeds the remaining shield breaks the wall immediately
     * and discards all overflow damage so the protected player takes 0 damage from that hit.
     */
    public static IceWallAbsorb absorbIceWallDamage(double currentShield, double incomingDamage) {
        if (!(currentShield > 0.0) || !Double.isFinite(currentShield)) {
            return new IceWallAbsorb(false, 0.0, false, 0.0);
        }
        if (!(incomingDamage > 0.0) || !Double.isFinite(incomingDamage)) {
            return new IceWallAbsorb(false, currentShield, false, 0.0);
        }
        if (incomingDamage < currentShield) {
            return new IceWallAbsorb(true, currentShield - incomingDamage, false, 0.0);
        }
        return new IceWallAbsorb(true, 0.0, true, incomingDamage - currentShield);
    }
}

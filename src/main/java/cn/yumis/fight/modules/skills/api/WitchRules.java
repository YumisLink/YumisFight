package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.combat.api.ElementAccumulation;
import cn.yumis.fight.modules.combat.api.Life;
import net.minecraft.world.phys.Vec3;

/**
 * Pure, shared Witch (魔道学者 / 魔术师) arithmetic:
 * - Single unified passive (魔道学概论): Broomstick Mastery + Lucky Lollipop (stable Great Success)
 *   + Four Familiar Affinity + Elemental Accumulation conversion & resonance
 * - Bitter Lollipop (苦涩炸炉) 85% early detonation formula
 * - Vanilla Elytra-style broomstick gliding physics step
 * - Unified elemental dyeing resolver
 */
public final class WitchRules {
    public static final int COLOR_LIGHT = 0xFFE66B;
    public static final int COLOR_FIRE = 0xFF7738;
    public static final int COLOR_ICE = 0x76DCFF;
    public static final int COLOR_DARK = 0xB368FF;
    public static final int COLOR_WITCH = 0xE57BFF;
    public static final int COLOR_CANDY = 0xFF9DE2;
    public static final int[] ALL_FOUR_ELEMENTS = {0, 1, 2, 3};

    private WitchRules() {}

    public static int elementColor(int element) {
        return switch (element) {
            case 0 -> COLOR_LIGHT;
            case 1 -> COLOR_FIRE;
            case 2 -> COLOR_ICE;
            case 3 -> COLOR_DARK;
            default -> COLOR_WITCH;
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

    public static int masteryRank(Stats stats, Progress progress) {
        if (progress == null || !progress.hasJob(JobCatalog.WITCH)) return 0;
        return SkillLearning.effective(stats, progress, SkillCatalog.require(WitchSkills.WITCHCRAFT_MASTERY));
    }

    public static SkillParameters.Snapshot masteryParams(Stats stats, Progress progress) {
        int rank = Math.max(1, masteryRank(stats, progress));
        var skill = SkillCatalog.require(WitchSkills.WITCHCRAFT_MASTERY);
        return SkillParameters.resolve(skill, rank, stats == null ? java.util.List.of() : stats.parameters);
    }

    public static boolean hasBroom(Stats stats) {
        return stats != null && "BROOM".equals(stats.weaponType);
    }

    public static double broomCastSpeedPercent(int rank, double scale) {
        if (rank <= 0) return 0.0;
        return (10.0 + 1.5 * (rank - 1)) * Math.max(0.0, scale);
    }

    public static double broomAttackSpeedPercent(int rank, double scale) {
        if (rank <= 0) return 0.0;
        return (8.0 + 1.2 * (rank - 1)) * Math.max(0.0, scale);
    }

    public static double broomCooldownRecovery(int rank, double scale) {
        if (rank <= 0) return 0.0;
        return (0.06 + 0.008 * (rank - 1)) * Math.max(0.0, scale);
    }

    public static double broomDamageMut(int rank, double scale) {
        if (rank <= 0) return 0.0;
        return (0.05 + 0.006 * (rank - 1)) * Math.max(0.0, scale);
    }

    public static double familiarAllElem(int rank, double baseElem) {
        if (rank <= 0) return 0.0;
        return Math.max(0.0, baseElem) + 1.5 * (rank - 1);
    }

    public static double ailmentResonanceMut(Stats stats, Life targetLife, long serverNow) {
        if (stats == null || targetLife == null) return 0.0;
        double perKind = stats.modifiers.getOrDefault("witchAilmentResonancePerKind", 0.0);
        if (!(perKind > 0.0)) return 0.0;
        int active = 0;
        for (var kind : ElementAccumulation.Kind.values()) {
            if (targetLife.elemental.active(kind, targetLife, serverNow)) active++;
        }
        return active * perKind;
    }

    /** Applies 魔道学概论 (Broomstick Mastery + Four Familiar Affinity + Elemental Conversion) and Lucky Candy Blessing. */
    public static Stats current(Stats source, Progress progress, WitchState state, long now) {
        if (progress == null || !progress.hasJob(JobCatalog.WITCH)) return source;
        int rank = masteryRank(source, progress);
        if (rank <= 0) return source;
        var result = source.copy();
        var mp = masteryParams(source, progress);
        boolean broom = hasBroom(result);
        double broomScale = mp.get("broomBonusScale", 1.0);

        double cdRec = broomCooldownRecovery(rank, broomScale) * (broom ? 1.0 : 0.5);
        double mut = broomDamageMut(rank, broomScale) * (broom ? 1.0 : 0.5);
        result.mut(Stat.COOLDOWN_RECOVERY, cdRec);
        result.modifier("final").mut += mut;

        if (broom) {
            result.mut(Stat.CAST_SPEED, broomCastSpeedPercent(rank, broomScale) / 100.0);
            result.extra(Stat.ATTACK_SPEED, broomAttackSpeedPercent(rank, broomScale));
            result.modifiers.put("witchBroomActive", 1.0);
        }

        double allElem = familiarAllElem(rank, mp.get("familiarBaseElem", 6.0));
        result.extra(Stat.LIGHT, allElem);
        result.extra(Stat.FIRE, allElem);
        result.extra(Stat.ICE, allElem);
        result.extra(Stat.DARK, allElem);

        double conv = Math.max(0.0, mp.get("buildupBonusRatio", 0.35));
        if (conv > 0.0) {
            ElementAccumulation.addConversion(result, 0, ElementAccumulation.Kind.SHOCK, conv);
            ElementAccumulation.addConversion(result, 1, ElementAccumulation.Kind.IGNITE, conv);
            ElementAccumulation.addConversion(result, 2, ElementAccumulation.Kind.FREEZE, conv);
            ElementAccumulation.addConversion(result, 3, ElementAccumulation.Kind.EROSION, conv);
        }

        result.modifiers.put("witchMasteryRank", (double) rank);
        result.modifiers.put("witchAilmentResonancePerKind", Math.max(0.0, mp.get("ailmentResonancePerKind", 0.025)));

        if (state != null && state.candyActive(now)) {
            if (state.candyDamageMut > 0.0) result.modifier("final").mut += state.candyDamageMut;
            if (state.candyCastSpeed > 0.0) result.mut(Stat.CAST_SPEED, state.candyCastSpeed);
            if (state.candyMoveSpeed > 0.0) result.mut(Stat.MOVEMENT_SPEED, state.candyMoveSpeed);
            result.modifiers.put("witchCandyActive", 1.0);
        }
        return result;
    }

    public static WitchState applyCandyBuff(WitchState current, SkillParameters.Snapshot swatParams, long serverNow) {
        var next = current == null ? new WitchState() : current.copy();
        long durMs = Math.max(1000L, Math.round(swatParams.get("buffDuration", 15.0) * 1000.0));
        next.candyBuffUntil = Math.max(next.candyBuffUntil, serverNow + durMs);
        next.candyDamageMut = Math.max(0.0, swatParams.get("buffDamageMut", 0.08));
        next.candyCastSpeed = Math.max(0.0, swatParams.get("buffCastSpeed", 0.12));
        next.candyMoveSpeed = Math.max(0.0, swatParams.get("buffMoveSpeed", 0.10));
        next.revision++;
        return next;
    }

    public static boolean isRidingMachine(String id) {
        return WitchSkills.RAGING_FLAME_FURNACE.equals(id)
            || WitchSkills.MEGA_DRILL.equals(id)
            || WitchSkills.FLORAE_COLLIDER.equals(id)
            || WitchSkills.FUSION_CRAFT.equals(id);
    }

    public static boolean isAirCastable(String id) {
        return MageSkills.STAR.equals(id)
            || WitchSkills.SHADOW_CLOAK.equals(id)
            || WitchSkills.LAVA_POTION.equals(id)
            || WitchSkills.ACID_RAIN.equals(id);
    }

    /** Bitter Lollipop (苦涩炸炉) early detonation coefficient: 85% remaining channel ticks + 100% finisher. */
    public static double bitterDetonateCoefficient(int remainingTicks, double perTickCoeff, double keepRatio, double finisherCoeff) {
        int rem = Math.max(0, remainingTicks);
        double keep = Math.clamp(keepRatio, 0.0, 1.0);
        return rem * Math.max(0.0, perTickCoeff) * keep + Math.max(0.0, finisherCoeff);
    }

    /**
     * Vanilla Elytra-inspired broomstick gliding velocity step (blocks/tick).
     * Mirrors LivingEntity.updateFallFlyingMovement so diving builds horizontal speed,
     * pulling up converts speed into lift, and horizontal velocity smoothly follows look heading.
     */
    public static Vec3 broomGlideStep(Vec3 velocity, Vec3 lookAngle, float pitchDegrees) {
        Vec3 look = lookAngle.lengthSqr() > 1e-8 ? lookAngle.normalize() : new Vec3(0, 0, 1);
        double pitchRad = Math.toRadians(pitchDegrees);
        double horizLook = Math.hypot(look.x, look.z);
        double horizVel = velocity.horizontalDistance();
        double cosPitch = Math.cos(pitchRad);
        double cosPitch2 = cosPitch * cosPitch;
        double gravity = 0.08;

        double vx = velocity.x;
        double vy = velocity.y;
        double vz = velocity.z;

        vy += gravity * (-1.0 + cosPitch2 * 0.78);

        if (vy < 0.0 && horizLook > 0.0) {
            double lift = vy * -0.1 * cosPitch2;
            vx += look.x * lift / horizLook;
            vz += look.z * lift / horizLook;
            vy += lift;
        }

        if (pitchRad < 0.0 && horizLook > 0.0) {
            double climb = horizVel * -Math.sin(pitchRad) * 0.04;
            vy += climb * 3.2;
            vx -= look.x * climb / horizLook;
            vz -= look.z * climb / horizLook;
        }

        if (horizLook > 0.0) {
            vx += (look.x / horizLook * horizVel - vx) * 0.14;
            vz += (look.z / horizLook * horizVel - vz) * 0.14;
        }

        vx *= 0.99;
        vy *= 0.98;
        vz *= 0.99;

        // Clamp gentle horizontal floor when gliding level so broom doesn't stall completely
        double newHoriz = Math.hypot(vx, vz);
        if (newHoriz < 0.22 && horizLook > 0.0 && pitchDegrees >= -25.0f && pitchDegrees <= 45.0f) {
            double boost = 0.22 - newHoriz;
            vx += (look.x / horizLook) * boost * 0.35;
            vz += (look.z / horizLook) * boost * 0.35;
        }

        // Clamp top speed for safe server sync and crisp combat handling
        double speed = Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (speed > 1.85) {
            double s = 1.85 / speed;
            vx *= s;
            vy *= s;
            vz *= s;
        }
        return new Vec3(vx, vy, vz);
    }

    public static int morphMode(SkillParameters.Snapshot parameters) {
        if (parameters == null) return 0;
        int mode = (int) Math.round(parameters.get("morphMode", 0.0));
        return mode == 1 || mode == 2 ? mode : 0;
    }

    /** Unified elemental dyeing resolver for Witch skills (supports future single-element gear dyeing). */
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

    public static int[] previewElements(SkillDefinition skill, Stats stats, int[] original) {
        if (skill == null || stats == null) return original;
        boolean witchStar = MageSkills.STAR.equals(skill.id) && stats.modifiers.getOrDefault("witchMasteryRank", 0.0) > 0;
        if (!WitchSkills.contains(skill.id) && !witchStar) return original;
        int innate = witchStar ? 0 : WitchSkills.innateElement(skill.id);
        int eff = effectiveElement(null, stats, innate);
        if (eff >= 0 && (original.length != 1 || original[0] != eff)) {
            return new int[]{eff};
        }
        return original;
    }
}

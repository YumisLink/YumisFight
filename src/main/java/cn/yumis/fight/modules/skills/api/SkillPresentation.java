package cn.yumis.fight.modules.skills.api;

import java.util.*;
import java.util.function.ToLongFunction;

/** Content metadata shared by cast scheduling, cancel priority, movement tagging and visual lifetime validation. */
public final class SkillPresentation {
    public static final int CANCEL_NORMAL = 0;
    public static final int CANCEL_MOBILITY = 20;
    public static final int STIFF_NORMAL = 0;
    public static final int STIFF_SUPER = 30;

    public record Traits(
        boolean charging,
        boolean fixedActionTime,
        boolean fixedVisualTime,
        boolean invulnerable,
        long segmentDelay,
        long segmentWindow,
        int color,
        boolean overdriveColor,
        ToLongFunction<SkillCast> duration,
        boolean movement,
        int cancelLevel,
        int stiffLevel
    ) {
        public Traits(
            boolean charging, boolean fixedActionTime, boolean fixedVisualTime, boolean invulnerable,
            long segmentDelay, long segmentWindow, int color, boolean overdriveColor, ToLongFunction<SkillCast> duration
        ) {
            this(charging, fixedActionTime, fixedVisualTime, invulnerable, segmentDelay, segmentWindow, color, overdriveColor, duration, false, CANCEL_NORMAL, invulnerable ? STIFF_SUPER : STIFF_NORMAL);
        }

        public Traits withAction(boolean movement, int cancelLevel, int stiffLevel) {
            return new Traits(charging, fixedActionTime, fixedVisualTime, invulnerable, segmentDelay, segmentWindow, color, overdriveColor, duration, movement, cancelLevel, stiffLevel);
        }
    }

    private static final Map<SkillDefinition, Traits> CONTENT = new LinkedHashMap<>();
    private static final Set<String> CAREER_MOVEMENT = Set.of(
        CareerSkills.PHANTOM, CareerSkills.CRASH, CareerSkills.ERUPTION, OracleSkills.THRUST
    );
    private static final Set<String> CAREER_SUPER_STIFF = Set.of(
        CareerSkills.BLACHE, CareerSkills.AGNI, OracleSkills.REPLICA
    );

    static {
        for (String id : List.of(MageSkills.STAR, MageSkills.FANG, MageSkills.JACK, MageSkills.HERDER))
            register(MageSkills.skill(id), new Traits(false, false, false, false, 0, 0, id.equals(MageSkills.JACK) ? 0xff9b35 : 0xbcefff, false, c -> 1500));
        for (String id : SummonerSkills.ALL)
            register(SkillCatalog.require(id), new Traits(false, false, false, false, 0, 0, 0xbcefff, false, c -> 1500));
        for (String id : SunMoonSkills.ALL)
            register(SkillCatalog.require(id), new Traits(false, false, true, false, 0, 0, SkillCatalog.require(id).aspect == SkillDefinition.Aspect.SUN ? 0xff9b35 : 0x9ddcff, false, c -> 3500));
        ElementalistSkills.presentation();
        BattleMageSkills.presentation();
        DemonHunterSkills.presentation();
        register(SkillCatalog.BLINK, new Traits(false, true, true, false, 0, 0, 0xb4f2ff, false, c -> 100, true, CANCEL_MOBILITY, STIFF_NORMAL));
        register(SkillCatalog.GHOST, new Traits(true, false, false, false, 0, 0, 0x8E56D9, false, c -> 3800));
        register(SkillCatalog.TRIPLE, new Traits(false, false, false, false, 200, 1200, 0xB4F2FF, true, c -> (long) (1200 * c.parameters.get("slashes", 3) + 500), true, CANCEL_MOBILITY, STIFF_NORMAL));
        register(SkillCatalog.WAVE, new Traits(false, false, false, false, 0, 0, 0xB4F2FF, false, c -> 1800));
        register(SkillCatalog.MASTERY, new Traits(false, false, false, false, 0, 0, 0xB4F2FF, false, c -> 1800));
        register(SkillCatalog.OVERDRIVE, new Traits(false, false, true, false, 0, 0, 0xB4F2FF, false, c -> 30000));
        register(SkillCatalog.RISING, new Traits(false, false, false, false, 0, 0, 0xB4F2FF, true, c -> 1800, true, CANCEL_MOBILITY, STIFF_NORMAL));
        register(SkillCatalog.DRAW, new Traits(false, false, false, false, 0, 0, 0xB4F2FF, true, c -> 1800));
        register(SkillCatalog.DRAGON, new Traits(false, false, false, false, 250, 1050, 0xB4F2FF, true, c -> (long) (1050 * c.parameters.get("slashes", 4) + 500), true, CANCEL_MOBILITY, STIFF_NORMAL));
        register(SkillCatalog.DANCE, new Traits(false, false, false, false, 0, 0, 0xB4F2FF, true, c -> (long) (c.parameters.get("duration", 1.25) * 1000 + 600)));
        register(SkillCatalog.STORM, new Traits(false, true, true, true, 0, 0, 0xB4F2FF, true, c -> StormChoreography.schedule(c.parameters.values()).endAt(), false, CANCEL_NORMAL, STIFF_SUPER));
    }

    private SkillPresentation() {}

    public static void register(SkillDefinition skill, Traits traits) {
        if (CONTENT.putIfAbsent(skill, Objects.requireNonNull(traits)) != null)
            throw new IllegalArgumentException("Duplicate presentation: " + skill);
    }

    public static Traits require(SkillDefinition skill) {
        var value = CONTENT.get(skill);
        if (value == null) {
            var content = CareerSkills.content(skill.id);
            if (content != null) {
                boolean landing = skill.id.equals(CareerSkills.CRASH) || skill.id.equals(CareerSkills.ERUPTION);
                boolean move = CAREER_MOVEMENT.contains(skill.id);
                int cancel = move ? CANCEL_MOBILITY : CANCEL_NORMAL;
                int stiff = content.invulnerable() || CAREER_SUPER_STIFF.contains(skill.id) ? STIFF_SUPER : STIFF_NORMAL;
                return new Traits(
                    content.charging(), false, landing || CareerSkills.persistent(skill.id),
                    content.invulnerable(), 0, 0, content.color(), false,
                    c -> landing ? 30000 : (long) (c.parameters.get("actionDuration", c.parameters.get("duration", 1)) * 1000),
                    move, cancel, stiff
                );
            }
        }
        if (value == null) throw new IllegalArgumentException("Missing skill presentation: " + skill);
        return value;
    }

    public static boolean fixedActionTime(SkillDefinition skill) { return require(skill).fixedActionTime(); }
    public static boolean invulnerable(SkillDefinition skill) { return require(skill).invulnerable(); }
    public static boolean charging(SkillDefinition skill) { return require(skill).charging(); }
    public static boolean movement(SkillDefinition skill) { return skill != null && require(skill).movement(); }
    public static boolean movement(String id) { return id != null && SkillCatalog.contains(id) && movement(SkillCatalog.require(id)); }
    public static int cancelLevel(SkillDefinition skill) { return skill == null ? CANCEL_NORMAL : require(skill).cancelLevel(); }
    public static int stiffLevel(SkillDefinition skill) { return skill == null ? STIFF_NORMAL : require(skill).stiffLevel(); }

    public static int effectiveStiffLevel(SkillCast cast) {
        if (cast == null || cast.skill == null) return STIFF_NORMAL;
        if (cast.terminal && (cast.skill.id.equals(CareerSkills.EYE) || cast.skill.id.equals(CareerSkills.SWORD))) return STIFF_SUPER;
        return stiffLevel(cast.skill);
    }

    /**
     * Mobility/high-cancel skills can cancel basic attacks and any skill that has entered canBreak/recovery,
     * while never interrupting high-stiff ultimate main phases.
     */
    public static boolean canCancel(SkillDefinition incoming, SkillCast active, boolean poseYieldable) {
        if (active == null) return true;
        if (poseYieldable) return true;
        int cancel = cancelLevel(incoming);
        if (cancel <= CANCEL_NORMAL) return false;
        return active.canBreak && cancel > effectiveStiffLevel(active);
    }

    public static boolean canCancelRecovery(SkillDefinition incoming) {
        return cancelLevel(incoming) >= CANCEL_MOBILITY;
    }

    public static boolean segmented(SkillDefinition skill) { return require(skill).segmentWindow() > 0; }
    public static long segmentDelay(SkillDefinition skill) { return require(skill).segmentDelay(); }
    public static long segmentWindow(SkillDefinition skill) { return require(skill).segmentWindow(); }

    public static long duration(SkillCast cast) {
        // The synchronized actor draws the full 60-second preparation without extending visual transport clocks.
        if (cast.skill.id.equals(CareerSkills.SWORD) && !cast.terminal) return 1000;
        if (CareerSkills.contains(cast.skill.id) && cast.terminal) return (long) (cast.parameters.get("actionDuration", 1) * 1000 / cast.speed);
        var traits = require(cast.skill);
        long value = traits.duration().applyAsLong(cast);
        return traits.fixedVisualTime() ? value : (long) (value / cast.speed);
    }

    public static double visualSpeed(SkillCast cast) {
        if (CareerSkills.contains(cast.skill.id) && cast.terminal) return cast.speed;
        return require(cast.skill).fixedVisualTime() ? 1 : cast.speed;
    }

    public static int color(SkillDefinition skill, boolean overdrive) {
        var t = require(skill);
        return overdrive && t.overdriveColor() ? 0xFFD368 : t.color();
    }
}

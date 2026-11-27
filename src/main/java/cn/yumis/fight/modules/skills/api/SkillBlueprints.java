package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.combat.api.ActionBlueprint;
import cn.yumis.fight.modules.combat.api.ActionRegistry;
import cn.yumis.fight.modules.combat.api.ActionStatus;

/**
 * Publishes reusable, actor-agnostic ActionBlueprints for player skills into combat/api's ActionRegistry.
 * Humanoid enemies (e.g. Abyss champions) and sourceless world actions can execute these directly.
 */
public final class SkillBlueprints {
    private static boolean installed;

    private SkillBlueprints() {}

    public static synchronized void install() {
        if (installed) return;
        installed = true;

        registerFrom(SkillCatalog.GHOST, 900, 500)
            .slash(400, 3.0, 3.0, 2.0, 1.0, 0.4, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.TRIPLE, 700, 200)
            .dash(0, 2.5, 2.0, 1.0 / 3.0, "SEGMENT")
            .dash(220, 2.5, 2.0, 1.0 / 3.0, "SEGMENT")
            .dash(440, 2.5, 2.0, 1.0 / 3.0, "SEGMENT")
            .buildAndRegister();
        registerFrom(SkillCatalog.WAVE, 400, 150)
            .wave(100, 10.0, 2.0, 1.5, 1.0, true, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.RISING, 700, 300)
            .dash(0, 4.0, 2.0, 0.4, "START")
            .slash(300, 2.0, 2.0, 3.0, 0.6, 0.6, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.DRAW, 800, 400)
            .fan(400, 5.0, 360.0, 3.0, 1.0, 0.4, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.DRAGON, 1000, 250)
            .dash(0, 4.0, 2.0, 0.25, "SEGMENT")
            .dash(250, 4.0, 2.0, 0.25, "SEGMENT")
            .dash(500, 4.0, 2.0, 0.25, "SEGMENT")
            .dash(750, 4.0, 2.0, 0.25, "SEGMENT")
            .buildAndRegister();
        {
            var dance = registerFrom(SkillCatalog.DANCE, 1300, 1250);
            for (int i = 0; i < 10; i++) {
                dance.slash(i * 125L, 4.0, 3.0, 2.0, 0.06, 0.04, "HIT");
            }
            dance.wave(1250, 8.0, 3.0, 2.0, 0.4, false, "FINISH").buildAndRegister();
        }
        {
            var storm = registerFrom(SkillCatalog.STORM, 3200, 2800)
                .status(ActionStatus.INVULNERABLE)
                .status(ActionStatus.NO_GRAVITY);
            for (int i = 0; i < StormChoreography.SLASHES; i++) {
                storm.area(200L + i * 220L, 5.0, 0.55 / StormChoreography.SLASHES, true, true, "HIT");
            }
            storm.area(2800, 5.0, 0.45, true, true, "FINISH").buildAndRegister();
        }
        registerFrom(SkillCatalog.BLINK, 100, 0)
            .effect(0, "blink", "START", true)
            .buildAndRegister();
        registerFrom(SkillCatalog.METEOR_RISE, 550, 260)
            .dash(0, 2.4, 2.6, 0.46, "START")
            .slash(220, 4.2, 3.4, 4.2, 0.54, 0.45, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.METEOR_LEAP, 650, 320)
            .dash(0, 6.5, 2.8, 0.33, "START")
            .area(280, 4.4, 0.67, false, false, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.METEOR_PIERCE, 450, 280)
            .dash(0, 3.2, 2.4, 0.34, "START")
            .slash(120, 5.4, 2.4, 2.6, 0.33, 0.25, "HIT")
            .slash(240, 5.4, 2.4, 2.6, 0.33, 0.35, "RELEASE")
            .buildAndRegister();

        // Soul Bender / Asura / Berserker reusable blueprints
        registerFrom(SkillCatalog.require(CareerSkills.MOONLIGHT), 800, 200)
            .dash(0, 2.4, 3.2, 6.0 / 22.0, "SEGMENT")
            .dash(220, 2.4, 3.2, 6.0 / 22.0, "SEGMENT")
            .area(460, 4.2, 10.0 / 22.0, false, true, "FINISH")
            .buildAndRegister();
        {
            var tomb = registerFrom(SkillCatalog.require(CareerSkills.TOMBSTONE), 2000, 200);
            for (int i = 0; i < 10; i++) tomb.area((i + 1) * 200L, 4.0, 0.1, false, true, "HIT");
            tomb.buildAndRegister();
        }
        registerFrom(SkillCatalog.require(CareerSkills.PHANTOM), 400, 250)
            .status(ActionStatus.INVULNERABLE)
            .dash(0, 8.0, 3.0, 1.0, "TRAIL")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.KALLA), 900, 600)
            .slash(0, 6.0, 4.0, 3.0, 1.0 / 3.0, 0.1, "HIT")
            .slash(300, 6.0, 4.0, 3.0, 1.0 / 3.0, 0.1, "HIT")
            .slash(600, 6.0, 4.0, 3.0, 1.0 / 3.0, 0.1, "HIT")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.BLACHE), 2000, 1200)
            .status(ActionStatus.INVULNERABLE)
            .area(1200, 8.0, 1.0, false, true, "FINISH")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.CRESCENT), 600, 250)
            .wave(150, 12.0, 3.0, 2.0, 1.0, false, "RELEASE")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.ICE), 600, 400)
            .wave(0, 12.0, 3.0, 2.0, 1.0 / 3.0, true, "HIT")
            .wave(200, 12.0, 3.0, 2.0, 1.0 / 3.0, true, "HIT")
            .wave(400, 12.0, 3.0, 2.0, 1.0 / 3.0, true, "HIT")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.FIRE), 700, 250)
            .wave(150, 10.0, 3.0, 2.0, 1.0, false, "RELEASE")
            .buildAndRegister();
        {
            var agni = registerFrom(SkillCatalog.require(CareerSkills.AGNI), 3000, 2850);
            for (int i = 0; i < 10; i++) agni.area((i + 1) * 280L, 5.0, 0.06, false, true, "HIT");
            agni.area(2900, 5.0, 0.4, false, true, "FINISH").buildAndRegister();
        }
        registerFrom(SkillCatalog.require(CareerSkills.EYE), 1200, 800)
            .status(ActionStatus.INVULNERABLE)
            .area(800, 16.0, 1.0, false, true, "FINISH")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.CRASH), 700, 500)
            .dash(0, 3.0, 2.5, 0.25, "START")
            .area(500, 4.0, 0.75, false, true, "FINISH")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(CareerSkills.HAND), 600, 350)
            .slash(350, 4.0, 3.0, 3.0, 1.0, 0.0, "RELEASE")
            .buildAndRegister();
        {
            var rage = registerFrom(SkillCatalog.require(CareerSkills.RAGE), 800, 150);
            for (int i = 0; i < 5; i++) rage.area(i * 150L, 5.0, 0.2, false, true, "HIT");
            rage.buildAndRegister();
        }
        registerFrom(SkillCatalog.require(CareerSkills.BLADE), 650, 200)
            .slash(200, 6.0, 3.0, 3.0, 0.25, 0.0, "RELEASE")
            .area(350, 4.0, 0.75, false, true, "FINISH")
            .buildAndRegister();
        {
            var eruption = registerFrom(SkillCatalog.require(CareerSkills.ERUPTION), 1650, 850)
                .dash(250, 6.0, 3.0, 0.0, "START")
                .area(850, 7.0, 0.35, false, true, "RELEASE");
            for (int i = 0; i < 4; i++) eruption.area(850 + i * 200L, 7.0, 0.65 / 4.0, false, true, "HIT");
            eruption.buildAndRegister();
        }
        {
            var sword = registerFrom(SkillCatalog.require(CareerSkills.SWORD), 2000, 300)
                .status(ActionStatus.INVULNERABLE)
                .area(300, 7.5, 0.3, false, true, "RELEASE");
            for (int i = 0; i < 5; i++) sword.area(600 + i * 300L, 7.5, 0.14, false, true, i == 4 ? "FINISH" : "HIT");
            sword.buildAndRegister();
        }

        // Oracle
        registerFrom(SkillCatalog.require(OracleSkills.DOWN), 600, 300)
            .slash(300, 4.0, 3.0, 3.0, 1.0, 0.3, "HIT").buildAndRegister();
        registerFrom(SkillCatalog.require(OracleSkills.THRUST), 550, 250)
            .dash(0, 6.0, 2.5, 1.0, "TRAIL").buildAndRegister();
        registerFrom(SkillCatalog.require(OracleSkills.SWEEP), 900, 500)
            .fan(500, 5.0, 145.0, 3.0, 1.0, 0.4, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(OracleSkills.SMILE), 600, 300)
            .slash(100, 4.0, 3.0, 3.0, 0.5, 0.2, "HIT")
            .slash(300, 4.0, 3.0, 3.0, 0.5, 0.2, "HIT")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(OracleSkills.RIFT), 650, 350)
            .dash(0, 5.0, 3.0, 1.0, "TRAIL").buildAndRegister();

        // Mage / DemonHunter / SunMoon / Elementalist representative blueprints
        registerFrom(MageSkills.skill(MageSkills.FANG), 600, 200)
            .slash(200, 5.0, 1.5, 2.0, 1.0, 0.1, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(DemonHunterSkills.CHAOS), 300, 150)
            .slash(0, 3.0, 3.0, 2.0, 0.5, 0.1, "HIT")
            .slash(150, 3.0, 3.0, 2.0, 0.5, 0.1, "HIT")
            .buildAndRegister();
        registerFrom(SkillCatalog.require(DemonHunterSkills.RUSH), 400, 350)
            .dash(0, 12.0, 2.0, 1.0, "START").buildAndRegister();
        registerFrom(SkillCatalog.require(DemonHunterSkills.META), 1000, 950)
            .status(ActionStatus.INVULNERABLE)
            .area(1000, 4.0, 1.0, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(ElementalistSkills.FLAME_PILLAR), 600, 200)
            .area(200, 3.0, 1.0, false, true, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(ElementalistSkills.HALLOWEEN_BUSTER), 750, 550)
            .area(550, 4.5, 0.7, false, true, "RELEASE")
            .area(650, 7.0, 0.3, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(BattleMageSkills.PALM_BLAST), 450, 280)
            .dash(0, 4.5, 2.6, 1.0, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(BattleMageSkills.FULL_SWING), 420, 160)
            .fan(160, 4.5, 210.0, 2.5, 1.0, 0.25, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(BattleMageSkills.DRAGON_STRIKE), 700, 420)
            .dash(0, 9.0, 3.5, 0.4, "START")
            .area(420, 5.0, 0.6, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(BattleMageSkills.DRAGON_SPEAR), 2000, 1900)
            .slash(150, 7.5, 3.2, 2.2, 0.25, 0.2, "START")
            .area(1750, 6.5, 0.35, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.SHADOW_CLOAK), 650, 450)
            .status(ActionStatus.INVULNERABLE)
            .area(120, 4.5, 0.34, true, true, "START")
            .area(450, 4.5, 0.66, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.GIANT_FLYSWATTER), 500, 220)
            .area(220, 3.8, 1.0, false, true, "RELEASE").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.ANTI_GRAVITY), 1200, 350)
            .area(350, 5.5, 0.26, false, true, "RELEASE")
            .area(850, 6.2, 0.74, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.RAGING_FLAME_FURNACE), 2800, 250)
            .area(1400, 7.5, 0.69, false, true, "HIT")
            .area(2800, 6.5, 0.31, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.MEGA_DRILL), 3200, 250)
            .area(1600, 4.2, 0.67, true, true, "HIT")
            .area(3200, 6.8, 0.33, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.FLORAE_COLLIDER), 3000, 250)
            .area(1500, 8.5, 0.67, false, true, "HIT")
            .area(3000, 8.5, 0.33, false, true, "FINISH").buildAndRegister();
        registerFrom(SkillCatalog.require(WitchSkills.FUSION_CRAFT), 3600, 600)
            .status(ActionStatus.INVULNERABLE)
            .area(1800, 7.5, 0.63, true, true, "HIT")
            .area(3600, 10.5, 0.37, false, true, "FINISH").buildAndRegister();
        {
            var starfall = registerFrom(SkillCatalog.require(SunMoonSkills.STARFALL), 2200, 200);
            for (int i = 0; i < 8; i++) starfall.area(i * 250L, 4.0, 0.125, false, true, "SEGMENT");
            starfall.buildAndRegister();
        }
    }

    private static Registrable registerFrom(SkillDefinition skill, long durationMillis, long canBreakAt) {
        var traits = SkillPresentation.require(skill);
        var builder = ActionBlueprint.builder(skill.id, skill.title)
            .poseClip(skill.id)
            .duration(durationMillis)
            .canBreakAt(canBreakAt)
            .movement(traits.movement())
            .fixedActionTime(traits.fixedActionTime())
            .cancelLevel(traits.cancelLevel())
            .stiffLevel(traits.stiffLevel());
        if (traits.invulnerable()) builder.status(ActionStatus.INVULNERABLE);
        return new Registrable(builder);
    }

    private record Registrable(ActionBlueprint.Builder builder) {
        Registrable status(ActionStatus status) { builder.status(status); return this; }
        Registrable slash(long at, double range, double width, double height, double share, double kb, String phase) { builder.slash(at, range, width, height, share, kb, phase); return this; }
        Registrable fan(long at, double range, double degrees, double height, double share, double kb, String phase) { builder.fan(at, range, degrees, height, share, kb, phase); return this; }
        Registrable area(long at, double radius, double share, boolean inward, boolean atCenter, String phase) { builder.area(at, radius, share, inward, atCenter, phase); return this; }
        Registrable wave(long at, double range, double width, double height, double share, boolean ground, String phase) { builder.wave(at, range, width, height, share, ground, phase); return this; }
        Registrable dash(long at, double distance, double width, double share, String phase) { builder.dash(at, distance, width, share, phase); return this; }
        Registrable effect(long at, String tag, String phase, boolean releasePose) { builder.effect(at, tag, phase, releasePose); return this; }
        void buildAndRegister() { ActionRegistry.registerOrReplace(builder.build()); }
    }
}

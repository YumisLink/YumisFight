package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Condition;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Cue;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Form;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Kit;
import cn.yumis.fight.modules.dungeons.api.AbyssChampionKits.Motion;
import cn.yumis.fight.modules.dungeons.api.AbyssAttack.Effect;
import cn.yumis.fight.modules.dungeons.api.AbyssAttack.Shape;

import java.util.List;

import static cn.yumis.fight.modules.dungeons.api.AbyssAttack.Effect.*;
import static cn.yumis.fight.modules.dungeons.api.AbyssAttack.Shape.*;

/**
 * Transcribes the real player catalog into abyss champion kits: coefficients and cooldowns are read
 * from {@link SkillCatalog}, geometry, reach, release conditions and the hidden per-skill enhancement
 * (baseline x2.0) are designed here. Every contact releases instantly at the focus position.
 */
public final class AbyssChampionSkills {
    private static final String NS = "yumis_dungeon_and_fight:";

    static void install() {
        // 剑魂冠军持光剑（用户指定，光剑资格为剑魂专属）；其余职业取平衡登记的默认武器。
        AbyssChampionKits.install(JobCatalog.SWORDMASTER, swordmaster(), "LIGHTSABER");
        AbyssChampionKits.install(JobCatalog.ORACLE, oracle(), SkillBalance.defaultWeapon(JobCatalog.ORACLE));
        AbyssChampionKits.install(JobCatalog.SOUL_BENDER, soulBender(), SkillBalance.defaultWeapon(JobCatalog.SOUL_BENDER));
        AbyssChampionKits.install(JobCatalog.ASURA, asura(), SkillBalance.defaultWeapon(JobCatalog.ASURA));
        AbyssChampionKits.install(JobCatalog.BERSERKER, berserker(), SkillBalance.defaultWeapon(JobCatalog.BERSERKER));
        AbyssChampionKits.install(JobCatalog.HAVOC, havoc(), SkillBalance.defaultWeapon(JobCatalog.HAVOC));
        AbyssChampionKits.install(JobCatalog.SUMMONER, summoner(), SkillBalance.defaultWeapon(JobCatalog.SUMMONER));
        AbyssChampionKits.install(JobCatalog.SUN_MOON, sunMoon(), SkillBalance.defaultWeapon(JobCatalog.SUN_MOON));
    }

    // ------------------------------------------------------------------ sword family

    private static List<Kit> swordBase(boolean ghostMagic) {
        return List.of(
            plain("ghost", ghostMagic, 3, 4.5, 2, fan(4, .8, 0, 1)),
            mobile("triple", false, -1, 12, 2, Motion.DASH,
                slash(5, 1.6, 0, .34), slash(5, 1.6, 250, .33), slash(5, 1.6, 500, .33)),
            plain("wave", true, -1, 11, 2, ray(WAVE, 11, 2.2, 0, 1)));
    }

    private static List<Kit> swordmaster() {
        var kit = new java.util.ArrayList<>(swordBase(false));
        kit.addAll(List.of(
            form("overdrive", false, -1, 2, 2, Form.OVERDRIVE, 28000, ring(2.5, .3, 0, 1)),
            mobile("rising", false, -1, 8, 2, Motion.DASH, slash(7, 2.4, 0, .6), drop(CIRCLE, SLASH, 3, 0, 300, .4)),
            plain("draw", false, -1, 5, 2, circle(5, 0, 1)),
            mobile("dragon", false, -1, 12, 2, Motion.DASH,
                slash(7, 2, 0, .5), turn(slash(6, 2, 300, .5), .3)),
            plain("dance", false, -1, 6, 2,
                fan(4, .9, 0, .1), fan(4, .9, 125, .1), fan(4, .9, 250, .1), fan(4, .9, 375, .1),
                drop(CIRCLE, SLASH, 8, 0, 500, .6)),
            gated("storm", false, -1, 13, 2, Condition.AWAKENING,
                ring(7, 2, 0, .2), drop(CIRCLE, SLASH, 5, 0, 400, .3), turn(fan(7, 1.25, 1200, .5), 2.6))));
        return List.copyOf(kit);
    }

    private static List<Kit> oracle() {
        var kit = new java.util.ArrayList<>(swordBase(false));
        kit.addAll(List.of(
            plain("oracle_down", false, -1, 7, 2, slash(7, 3, 0, 1)),
            plain("oracle_thrust", false, -1, 7, 2, slash(7, 1.65, 0, 1)),
            plain("oracle_sweep", false, -1, 6, 2, fan(5.5, 1.35, 0, 1)),
            plain("oracle_smile", false, -1, 5, 2, fan(4, .8, 0, .5), fan(5, 1.1, 650, .5)),
            plain("oracle_rift", false, -1, 12, 2, drop(LINE, WAVE, 9, 2.4, 0, .55), turn(drop(LINE, WAVE, 9, 2.4, 250, .45), 1.22)),
            gated("furioso_replica", false, -1, 13, 2, Condition.AWAKENING,
                slash(10, 3.3, 0, .125), slash(10, 3.3, 300, .125), slash(10, 3.3, 600, .125), slash(10, 3.3, 900, .125),
                drop(CIRCLE, SLASH, 6, 0, 1300, .5))));
        return List.copyOf(kit);
    }

    private static List<Kit> soulBender() {
        var kit = new java.util.ArrayList<>(swordBase(true));
        kit.addAll(List.of(
            form("ghost_field", true, 3, 12, 2, Form.GHOST_FIELD, 19000, drop(RING, WAVE, 5, 1, 0, 1)),
            plain("tombstone", true, 3, 12, 2,
                column(-1, -2, 0, .2), column(1.5, -2.5, 250, .2), column(3, 1.6, 550, .2),
                column(-3, -1, 1000, .2), column(0, 3, 1250, .2)),
            mobile("phantom_slash", true, 3, 13, 2, Motion.DASH, ray(WAVE, 11, 2.3, 0, 1)),
            form("kalla", true, 1, 10, 2, Form.KALLA, 28000, ray(FIRE, 10, 2.2, 0, .34), ray(FIRE, 10, 2.2, 500, .33), ray(FIRE, 10, 2.2, 1000, .33)),
            gated("blache", true, 3, 15, 2, Condition.AWAKENING, drop(CIRCLE, WAVE, 8, 0, 0, 1))));
        return List.copyOf(kit);
    }

    private static List<Kit> asura() {
        var kit = new java.util.ArrayList<>(swordBase(true));
        kit.addAll(List.of(
            plain("ghost_orb", true, -1, 11, 2, ray(ORB, 10, 2.4, 0, .5), ray(ORB, 12, 2.4, 500, .5)),
            plain("spirit_crescent", true, -1, 14, 2, ray(WAVE, 14, 4, 0, 1)),
            plain("ice_wave", true, 2, 13, 2,
                side(ICE, 13, -2.5, 0, .2), side(ICE, 13, 0, 150, .2), side(ICE, 13, 2.5, 300, .2),
                side(ICE, 13, -1.2, 600, .2), side(ICE, 13, 1.2, 750, .2)),
            plain("fire_wave", true, 1, 12, 2, ray(FIRE, 12, 3, 0, .7), drop(CIRCLE, FIRE, 3, 0, 350, .3)),
            plain("agni", true, -1, 6, 2,
                fan(5, 1.05, 0, .15), turn(fan(5, 1.05, 1150, .15), 2.08), turn(fan(5, 1.05, 1850, .15), 4.16),
                circle(5, 2750, .25), ring(7.5, 2.5, 3650, .3)),
            gated("wave_eye", true, 3, 16, 2, Condition.AWAKENING,
                drop(CIRCLE, WAVE, 7, 0, 0, .4), drop(RING, WAVE, 8, 2, 1000, .2), drop(RING, WAVE, 8, 2, 3000, .2), drop(RING, WAVE, 8, 2, 5000, .2))));
        return List.copyOf(kit);
    }

    private static List<Kit> berserker() {
        var kit = new java.util.ArrayList<>(swordBase(false));
        kit.addAll(List.of(
            form("frenzy", false, -1, 2, 2, Form.FRENZY, 240000, ring(2.5, .3, 0, 1)),
            mobile("mountain_crash", false, -1, 8, 2, Motion.LEAP, drop(CIRCLE, SLASH, 3.3, 0, 0, 1)),
            plain("blood_hand", false, -1, 4, 2, fan(3, .7, 0, 1)),
            plain("blood_rage", false, -1, 5, 2, circle(3.8, 0, .35), circle(4.4, 350, .35), ring(7, 2.6, 700, .3)),
            plain("blood_blade", false, -1, 7, 2, slash(7, 2, 0, .6), drop(CIRCLE, SLASH, 2.6, 0, 300, .4)),
            mobile("blood_eruption", false, -1, 9, 2, Motion.LEAP,
                fan(8, .85, 0, .5), turn(ray(WAVE, 8, 1.9, 400, .25), -.65), turn(ray(WAVE, 8, 1.9, 700, .25), .65)),
            gated("blood_sword", false, -1, 9, 2, Condition.AWAKENING,
                slash(8, 2.5, 0, .15), slash(8, 2.5, 600, .15), slash(8, 2.5, 1200, .15), slash(8, 2.5, 1800, .15),
                drop(CIRCLE, SLASH, 7, 0, 2300, .4))));
        return List.copyOf(kit);
    }

    // ------------------------------------------------------------------ havoc

    private static List<Kit> havoc() {
        return List.of(
            plain("chaos_strike", false, 3, 4, 2, fan(3.5, .85, 0, .5), fan(3.5, .85, 150, .5)),
            plain("throw_glaive", false, -1, 16, 2, ray(ORB, 16, 1.5, 0, 1)),
            plain("immolation_aura", true, 1, 4, 2,
                circle(4, 0, 1 / 6d), circle(4, 1000, 1 / 6d), circle(4, 2000, 1 / 6d), circle(4, 3000, 1 / 6d),
                circle(4, 4000, 1 / 6d), circle(4, 5000, 1 / 6d)),
            plain("eye_beam", true, 1, 16, 2, ray(BEAM, 16, 3, 0, .34), ray(BEAM, 16, 3, 450, .33), ray(BEAM, 16, 3, 900, .33)),
            mobile("fel_rush", false, -1, 12, 2, Motion.DASH, slash(12, 2.5, 0, 1)),
            plain("blade_dance", false, 3, 4, 2, circle(3, 0, .2), circle(4, 600, .2), drop(CIRCLE, SLASH, 3, 0, 1600, .6)),
            mobile("the_hunt", true, 1, 16, 2, Motion.DASH,
                ray(WAVE, 8, 3, 0, .05), ray(WAVE, 8, 3, 100, .05), ray(WAVE, 8, 3, 200, .05), ray(WAVE, 8, 3, 300, .05),
                ray(WAVE, 8, 3, 400, .05), ray(WAVE, 8, 3, 500, .05), ray(WAVE, 8, 3, 600, .05), ray(WAVE, 8, 3, 700, .05),
                drop(CIRCLE, SLASH, 4, 0, 900, .6)),
            gated("metamorphosis", true, 1, 16, 2, Condition.AWAKENING, drop(CIRCLE, FIRE, 6, 0, 0, 1)));
    }

    // ------------------------------------------------------------------ mage family

    private static List<Kit> mageBase() {
        return List.of(
            plain("magic_star", true, -1, 14, 2, ray(ORB, 14, 1.2, 0, 1)),
            plain("dragon_fang", false, -1, 5, 2, slash(5, 1.6, 0, 1)),
            plain("jack_bomb", true, 1, 12, 2, drop(CIRCLE, FIRE, 3, 0, 0, 1)),
            form("herder", true, 2, 15, 2, Form.SUMMON, 60000,
                drop(CIRCLE, PROJECTION, .6, 0, 0, 0), drop(CIRCLE, ICE, 3.2, 0, 1300, .34),
                drop(CIRCLE, ICE, 3.2, 0, 1900, .33), drop(CIRCLE, ICE, 3.2, 0, 2500, .33)));
    }

    private static List<Kit> summoner() {
        var kit = new java.util.ArrayList<>(mageBase());
        kit.addAll(List.of(
            form("four_spirits", true, 2, 14, 2, Form.SUMMON, 60000,
                drop(CIRCLE, SUMMON, 2.6, 0, 0, .4), drop(CIRCLE, SUMMON, 2.6, 0, 1200, .3), drop(CIRCLE, SUMMON, 2.6, 0, 2500, .3)),
            form("louise", true, 2, 15, 2, Form.SUMMON, 60000,
                drop(CIRCLE, SUMMON, 3, 0, 0, .4), drop(CIRCLE, ICE, 3.2, 0, 1600, .6)),
            form("sandor", false, -1, 15, 2, Form.SUMMON, 60000,
                drop(LINE, SUMMON, 8, 2.7, 0, .5), drop(FAN, SUMMON, 4.5, 1.2, 1500, .5)),
            form("echeverria", true, 0, 16, 2, Form.SUMMON, 60000,
                drop(RING, SUMMON, 5, 2, 0, .4), drop(CIRCLE, BEAM, 3.2, 0, 1900, .6)),
            build("summon_frenzy", true, 3, 2, 2, Condition.SUMMON_ACTIVE, Form.SUMMON_FRENZY, 28000, Motion.STATIONARY,
                ring(2.5, .3, 0, 1)),
            gated("kasijas", true, 3, 16, 2, Condition.AWAKENING,
                drop(FAN, SUMMON, 8, 1, 0, .15), slash(10, 3, 500, .25), drop(CIRCLE, SLASH, 5, 0, 1000, .6))));
        return List.copyOf(kit);
    }

    private static List<Kit> sunMoon() {
        var kit = new java.util.ArrayList<>(mageBase());
        kit.addAll(List.of(
            form("moon_flow", true, 2, 16, 2, Form.MOON, 32000, ray(WAVE, 15, 2, 0, 1)),
            form("sunfire", true, 1, 16, 2, Form.SUN, 32000, drop(CIRCLE, FIRE, 3.4, 0, 0, 1)),
            form("moon_reflux", true, 2, 2, 2, Form.MOON_REFLUX, 24000, ring(3, .5, 0, 1)),
            form("solar_flare", true, 1, 16, 2, Form.SUN, 32000,
                drop(CIRCLE, FIRE, 4.3, 0, 0, .6), drop(RING, FIRE, 6.8, 2.7, 500, .4)),
            gated("corona", true, 1, 16, 2, Condition.SUN_HEART, drop(RING, FIRE, 5, 2.35, 0, 1)),
            gated("moon_starfall", true, 2, 16, 2, Condition.MOON_HEART,
                drop(CIRCLE, ICE, 2.35, 0, 0, .2), drop(CIRCLE, ICE, 2.35, 0, 250, .2), drop(CIRCLE, ICE, 2.35, 0, 500, .2),
                drop(CIRCLE, ICE, 2.35, 0, 750, .2), drop(CIRCLE, ICE, 2.35, 0, 1000, .2)),
            paradox()));
        return List.copyOf(kit);
    }

    /** Paradox is player charge-gated with catalog cooldown 0; the champion gate is awakening plus a designed 90s cycle. */
    private static Kit paradox() {
        var def = SkillCatalog.require(NS + "paradox");
        return new Kit("paradox", def.id, def.title, true, 0, def.coefficient, 90000, 16, 2,
            SkillVisualEvent.color(def, SkillVisualEvent.Element.LIGHT, false), Condition.AWAKENING,
            Form.NONE, 0, Motion.STATIONARY, List.of(drop(CIRCLE, FIRE, 6.4, 0, 0, .5), drop(RING, ICE, 7.6, 3, 0, .5)));
    }

    // ------------------------------------------------------------------ builders

    private static Kit plain(String id, boolean magic, int element, double reach, double enhance, Cue... cues) {
        return build(id, magic, element, reach, enhance, Condition.ALWAYS, Form.NONE, 0, Motion.STATIONARY, cues);
    }

    private static Kit form(String id, boolean magic, int element, double reach, double enhance, Form form, long formTime, Cue... cues) {
        return build(id, magic, element, reach, enhance, Condition.ALWAYS, form, formTime, Motion.STATIONARY, cues);
    }

    private static Kit mobile(String id, boolean magic, int element, double reach, double enhance, Motion motion, Cue... cues) {
        return build(id, magic, element, reach, enhance, Condition.ALWAYS, Form.NONE, 0, motion, cues);
    }

    private static Kit gated(String id, boolean magic, int element, double reach, double enhance, Condition condition, Cue... cues) {
        return build(id, magic, element, reach, enhance, condition, Form.NONE, 0, Motion.STATIONARY, cues);
    }

    private static Kit build(String id, boolean magic, int element, double reach, double enhance,
                             Condition condition, Form form, long formTime, Motion motion, Cue... cues) {
        var def = SkillCatalog.require(NS + id);
        var visual = element < 0 ? null : SkillVisualEvent.Element.values()[element];
        return new Kit(id, def.id, def.title, magic, element, def.coefficient, Math.round(def.cooldown * 1000),
            reach, enhance, SkillVisualEvent.color(def, visual, false), condition, form, formTime, motion, List.of(cues));
    }

    private static Cue fan(double range, double angle, long delay, double share) {
        return new Cue(FAN, SLASH, false, 0, 0, 0, range, angle, delay, share);
    }

    private static Cue slash(double range, double width, long delay, double share) {
        return new Cue(LINE, SLASH, false, 0, 0, 0, range, width, delay, share);
    }

    private static Cue ray(Effect effect, double range, double width, long delay, double share) {
        return new Cue(LINE, effect, false, 0, 0, 0, range, width, delay, share);
    }

    private static Cue side(Effect effect, double range, double side, long delay, double share) {
        return new Cue(LINE, effect, false, 0, side, 0, range, 1.2, delay, share);
    }

    private static Cue circle(double range, long delay, double share) {
        return new Cue(CIRCLE, SLASH, false, 0, 0, 0, range, 0, delay, share);
    }

    private static Cue ring(double range, double width, long delay, double share) {
        return new Cue(RING, WAVE, false, 0, 0, 0, range, width, delay, share);
    }

    private static Cue drop(Shape shape, Effect effect, double range, double width, long delay, double share) {
        return new Cue(shape, effect, true, 0, 0, 0, range, width, delay, share);
    }

    private static Cue column(double forward, double side, long delay, double share) {
        return new Cue(CIRCLE, COLUMN, true, forward, side, 0, 2, 0, delay, share);
    }

    private static Cue turn(Cue cue, double radians) {
        return new Cue(cue.shape(), cue.effect(), cue.atTarget(), cue.forward(), cue.side(), radians,
            cue.range(), cue.width(), cue.delay(), cue.share());
    }

    private AbyssChampionSkills() {}
}

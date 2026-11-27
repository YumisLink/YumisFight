package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import java.util.*;

/** Elementalist (元素师 / 大魔导师) skill definitions, calibrated balance profiles, previews and presentation traits. */
public final class ElementalistSkills {
    private static final String NS = "yumis_dungeon_and_fight:";
    static final double DEFAULT_CAST_SECONDS = 0.30;
    public static final String
        MOVING_CAST = NS + "moving_cast",
        ELEMENTAL_BURN = NS + "elemental_burn",
        ICE_WALL = NS + "ice_wall",
        LIGHTNING_VORTEX = NS + "lightning_vortex",
        VOID_SPHERE = NS + "void_sphere",
        FLAME_PILLAR = NS + "flame_pillar",
        HALLOWEEN_BUSTER = NS + "halloween_buster",
        ARCTIC_FEAST = NS + "arctic_feast",
        BLACK_HOLE = NS + "black_hole",
        LIGHTNING_STRIKE = NS + "lightning_strike",
        ASTRAL_STORM = NS + "astral_storm";

    public static final List<String> ALL = List.of(
        MOVING_CAST, ELEMENTAL_BURN, ICE_WALL, LIGHTNING_VORTEX, VOID_SPHERE, FLAME_PILLAR,
        HALLOWEEN_BUSTER, ARCTIC_FEAST, BLACK_HOLE, LIGHTNING_STRIKE, ASTRAL_STORM
    );

    public static final List<String> ACTIVE = List.of(
        ICE_WALL, LIGHTNING_VORTEX, VOID_SPHERE, FLAME_PILLAR,
        HALLOWEEN_BUSTER, ARCTIC_FEAST, BLACK_HOLE, LIGHTNING_STRIKE, ASTRAL_STORM
    );

    private static final Set<String> SET = Set.copyOf(ALL);

    private ElementalistSkills() {}

    public static boolean contains(String id) {
        return SET.contains(id);
    }

    /** Innate element used by Elemental Burn lamp lighting (decoupled from elemental dyeing). */
    public static int innateElement(String id) {
        if (LIGHTNING_VORTEX.equals(id) || LIGHTNING_STRIKE.equals(id)) return 0;
        if (FLAME_PILLAR.equals(id) || HALLOWEEN_BUSTER.equals(id) || MageSkills.JACK.equals(id)) return 1;
        if (ICE_WALL.equals(id) || ARCTIC_FEAST.equals(id)) return 2;
        if (VOID_SPHERE.equals(id) || BLACK_HOLE.equals(id)) return 3;
        return -1;
    }

    public static boolean isUpperSkill(String id) {
        return HALLOWEEN_BUSTER.equals(id) || ARCTIC_FEAST.equals(id) || BLACK_HOLE.equals(id)
            || LIGHTNING_STRIKE.equals(id) || ASTRAL_STORM.equals(id);
    }

    static void install() {
        var moving = register(MOVING_CAST, "移动施法", 20, 1, 0, 0, 0, true, Map.of("range", 0d, "slashes", 1d, "duration", 0d));
        moving.automaticByJob = true;
        moving.maximumLearnedRank = 1;
        moving.attackTypeRanks = false;

        var burn = register(ELEMENTAL_BURN, "圣灵符文", 20, 30, 0, 0, 0, true, Map.of("range", 0d, "slashes", 1d, "duration", 0d));
        burn.automaticByJob = true;
        burn.attackTypeRanks = false;
        burn.formParameter("lampDuration", 30.0)
            .formParameter("perLampBonusScale", 1.0)
            .formParameter("allLampsBonusMut", 0.06)
            .formParameter("allLampsAllElem", 12.0)
            .formParameter("memorizeBonus", 0.0)
            .formParameter("festivalSpeedScale", 1.0)
            .formParameter("festivalCdRecoveryAdd", 0.0)
            .formParameter("autoRefreshAllOnUpper", 0.0);

        var iceWall = active(ICE_WALL, "冰墙", 20, 30, 32, 12.0, 0.70, false, 0.0, 1.0, 1.0, 1.0);
        iceWall.damageParameter("finisher", 0.30).damageParameter("pulseDot", 0.0)
            .formParameter("range", 10.0).formParameter("radius", 3.5).formParameter("blastRadius", 4.0)
            .formParameter("wallHeight", 2.2).formParameter("wallSegments", 12.0)
            .formParameter("duration", 6.0).formParameter("interval", 0.50).formParameter("slashes", 1.0)
            .formParameter("knockbackDistance", 2.5).formParameter("slowRatio", 0.30)
            .formParameter("wallShieldMultiplier", 2.0);
        SkillDamagePhases.register(ICE_WALL, "pulse", true, true);

        var vortex = active(LIGHTNING_VORTEX, "雷旋", 20, 30, 30, 7.0, 1.0, false, 0.40, 0.80, 1.20, 1.25);
        vortex.formParameter("orbCount", 4.0).formParameter("range", 14.0).formParameter("radius", 4.0)
            .formParameter("centerOffset", 2.0).formParameter("slashes", 3.0)
            .formParameter("interval", 0.25).formParameter("duration", 0.75);

        var voidSphere = active(VOID_SPHERE, "虚无之球", 25, 40, 52, 9.0, 1.0, false, 0.50, 0.75, 1.25, 1.30);
        voidSphere.damageParameter("finisher", 0.0)
            .formParameter("range", 12.0).formParameter("radius", 1.8).formParameter("blastRadius", 3.8)
            .formParameter("projectileSpeed", 3.0).formParameter("pushStrength", 0.35)
            .formParameter("duration", 4.0).formParameter("interval", 0.25).formParameter("slashes", 16.0)
            .formParameter("manualDetonate", 0.0).formParameter("earlyDetonateKeepRatio", 0.0);

        var flame = active(FLAME_PILLAR, "烈焰冲击", 25, 40, 52, 10.0, 0.80, true, 0.50, 0.80, 1.25, 1.25);
        flame.formParameter("range", 14.0).formParameter("radius", 3.0)
            .formParameter("pillarCount", 1.0).formParameter("pillarSpread", 2.8)
            .formParameter("impactDelay", 0.20).formParameter("controlSeconds", 0.60)
            .formParameter("slashes", 1.0).formParameter("duration", 0.60);
        SkillDamagePhases.register(FLAME_PILLAR, "volcano", true, true);

        var buster = active(HALLOWEEN_BUSTER, "杰克降临", 30, 50, 95, 18.0, 0.70, true, 0.65, 0.75, 1.25, 1.25);
        buster.damageParameter("finisher", 0.30).damageParameter("emberDot", 0.0)
            .formParameter("range", 16.0).formParameter("fallDelay", 0.55)
            .formParameter("radius", 4.5).formParameter("shockwaveRadius", 7.0)
            .formParameter("meteorCount", 1.0).formParameter("twinGapSeconds", 0.30)
            .formParameter("emberDuration", 0.0).formParameter("emberInterval", 0.50)
            .formParameter("slashes", 1.0).formParameter("duration", 0.75);
        SkillDamagePhases.register(HALLOWEEN_BUSTER, "ember", true, true);

        var arctic = active(ARCTIC_FEAST, "极冰盛宴", 35, 50, 115, 22.0, 0.09375, false, 0.70, 0.75, 1.25, 1.25);
        arctic.damageParameter("finisher", 0.25)
            .formParameter("range", 14.0).formParameter("radius", 5.5).formParameter("centerOffset", 4.5)
            .formParameter("slashes", 8.0).formParameter("interval", 0.22).formParameter("duration", 2.0)
            .formParameter("channeling", 0.0).formParameter("pullRadius", 0.0).formParameter("pullStrength", 0.0)
            .formParameter("freezeSeconds", 1.5);

        var blackHole = active(BLACK_HOLE, "湮灭黑洞", 40, 60, 150, 28.0, 0.06, true, 0.80, 0.75, 1.25, 1.25);
        blackHole.damageParameter("finisher", 0.40).damageParameter("volcanoDot", 0.0)
            .formParameter("range", 16.0).formParameter("radius", 4.2).formParameter("blastRadius", 6.0)
            .formParameter("pullRadius", 7.5).formParameter("pullStrength", 4.5)
            .formParameter("slashes", 10.0).formParameter("interval", 0.30).formParameter("duration", 3.0)
            .formParameter("manualDetonate", 1.0).formParameter("earlyDetonateKeepRatio", 0.40)
            .formParameter("subHoleDuration", 0.0);
        SkillDamagePhases.register(BLACK_HOLE, "volcano", true, true);

        var strike = active(LIGHTNING_STRIKE, "天雷", 45, 80, 190, 32.0, 0.20, false, 0.80, 0.75, 1.25, 1.25);
        strike.formParameter("range", 18.0).formParameter("radius", 3.5)
            .formParameter("slashes", 5.0).formParameter("interval", 0.35).formParameter("duration", 1.75)
            .formParameter("cursorSpeed", 14.0).formParameter("pullOnHit", 1.8)
            .formParameter("channeling", 1.0).formParameter("channelProtection", 0.65);

        var storm = active(ASTRAL_STORM, "陨星幻灭", 50, 100, 285, 90.0, 0.040625, false, 0.0, 1.0, 1.0, 1.0);
        storm.damageParameter("finisher", 0.35)
            .formParameter("range", 16.0).formParameter("centerOffset", 4.0)
            .formParameter("fieldRadius", 10.0).formParameter("meteorRadius", 4.2).formParameter("finisherRadius", 11.0)
            .formParameter("slashes", 16.0).formParameter("interval", 0.18).formParameter("duration", 4.0)
            .formParameter("homingBias", 0.65).formParameter("channeling", 1.0);
    }

    private static SkillDefinition active(String id, String title, int unlock, int sp, double mp, double cd,
                                          double coefficient, boolean groundTargeted,
                                          double chargeSeconds, double minChargeRatio,
                                          double chargeDamageMultiplier, double chargeScaleMultiplier) {
        var skill = register(id, title, unlock, sp, mp, cd, coefficient, false, Map.of("range", 14.0, "slashes", 1.0, "duration", 0.5));
        skill.groundTargeted = groundTargeted;
        skill.chantSeconds = DEFAULT_CAST_SECONDS;
        skill.formParameter("chargeSeconds", chargeSeconds)
            .formParameter("minChargeRatio", minChargeRatio)
            .formParameter("chargeDamageMultiplier", chargeDamageMultiplier)
            .formParameter("chargeScaleMultiplier", chargeScaleMultiplier)
            .formParameter("maxHoldSeconds", 2.0)
            .formParameter("chargeMoveSpeedRatio", 1.0)
            .formParameter("maxCharges", 1.0)
            .formParameter("morphMode", 0.0)
            .formParameter("dyeEnabled", 0.0)
            .formParameter("dyeElement", 0.0);
        return skill;
    }

    private static SkillDefinition register(String id, String title, int unlock, int sp, double mp, double cd,
                                            double coefficient, boolean passive, Map<String, Double> base) {
        double mpRatio = mp <= 0 ? 0 : mp / (100.0 + 14.0 * (unlock - 1));
        var skill = new SkillDefinition(id, title, "ELEMENT", JobCatalog.ELEMENTALIST, unlock, sp, mpRatio, cd, coefficient, passive, base);
        skill.baseJob = JobCatalog.MAGE;
        skill.magicDamage = true;
        skill.magicTag = true;
        return SkillCatalog.register(skill);
    }

    static void variants() {
        MageSkills.careerScales(JobCatalog.ELEMENTALIST, 1.0, 0.5);
    }

    public static void previews() {
        SkillPreviewRules.register(SkillCatalog.require(MOVING_CAST), new SkillPreviewRules.Definition(p -> 0, false, false, true, -1));
        SkillPreviewRules.register(SkillCatalog.require(ELEMENTAL_BURN), new SkillPreviewRules.Definition(p -> 0, false, false, true, -1));

        SkillPreviewRules.total(MageSkills.skill(MageSkills.JACK), (progress, stats, p, effects) -> {
            if (!progress.hasJob(JobCatalog.ELEMENTALIST)) return null;
            boolean triple = TowerBattleCard.ELEM_JACK_TRIPLE.held(stats);
            boolean split = TowerBattleCard.ELEM_JACK_SPLIT.held(stats);
            double cm = ElementalistRules.fullChargeMultiplier(p);
            double base = p.get("damage", 0) * cm;
            int volley = triple ? Math.max(1, TowerBattleCard.ELEM_JACK_TRIPLE.count("count")) : 1;
            double mainScale = triple ? TowerBattleCard.ELEM_JACK_TRIPLE.ratio("damage") : 1.0;
            double splitTotal = split ? TowerBattleCard.ELEM_JACK_SPLIT.count("count") * TowerBattleCard.ELEM_JACK_SPLIT.ratio("damage") : 0.0;
            double totalCoeff = base * volley * mainScale * (1.0 + splitTotal);
            return SkillPreview.damage(progress, stats, p.skill(), totalCoeff, false, effects);
        });

        registerPreview(ICE_WALL, p -> 1, true, 2);
        SkillPreviewRules.pattern(SkillCatalog.require(ICE_WALL), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern(1, p.get("damage", 0) * cm, p.get("finisher", 0) * cm);
        });
        SkillPreviewRules.supplement(SkillCatalog.require(ICE_WALL), (progress, stats, p, effects) -> {
            if (TowerBattleCard.ELEM_ICE_WALL_WAVE.held(stats) && TowerBattleCard.ELEM_ICE_WALL_MOBILE.held(stats)) {
                int extraWaves = Math.max(0, TowerBattleCard.ELEM_ICE_WALL_MOBILE.count("waves") - 1);
                if (extraWaves > 0) {
                    double cm = ElementalistRules.fullChargeMultiplier(p);
                    return SkillPreview.damage(progress, stats, p.skill(), p.get("damage", 0) * cm, false, effects).times(extraWaves);
                }
            }
            return new SkillPreview.Damage(0, 0);
        });
        SkillPreviewRules.finisherLabel(SkillCatalog.require(ICE_WALL), "碎冰终结");

        registerPreview(LIGHTNING_VORTEX, p -> (int) Math.max(1, p.get("slashes", 3)), false, 0);
        SkillPreviewRules.pattern(SkillCatalog.require(LIGHTNING_VORTEX), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern((int) Math.max(1, p.get("slashes", 3)), p.get("damage", 0) * cm, 0);
        });

        registerPreview(VOID_SPHERE, ElementalistRules::voidSphereTicks, true, 3);
        SkillPreviewRules.pattern(SkillCatalog.require(VOID_SPHERE), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern(ElementalistRules.voidSphereTicks(p), p.get("damage", 0) * cm, p.get("finisher", 0) * cm);
        });
        SkillPreviewRules.supplement(SkillCatalog.require(VOID_SPHERE), (progress, stats, p, effects) -> {
            double crystalFin = ElementalistRules.voidCrystalFinisher(p, stats);
            if (!(crystalFin > 0)) return new SkillPreview.Damage(0, 0);
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return SkillPreview.damage(progress, stats, p.skill(), crystalFin * cm, false, effects);
        });
        SkillPreviewRules.finisherLabel(SkillCatalog.require(VOID_SPHERE), "坍缩终爆");

        registerPreview(FLAME_PILLAR, p -> (int) Math.max(1, p.get("pillarCount", 1)), false, 1);
        SkillPreviewRules.pattern(SkillCatalog.require(FLAME_PILLAR), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern((int) Math.max(1, p.get("pillarCount", 1)), p.get("damage", 0) * cm, 0);
        });
        SkillPreviewRules.supplement(SkillCatalog.require(FLAME_PILLAR), (progress, stats, p, effects) -> {
            if (!TowerBattleCard.ELEM_FLAME_VOLCANO.held(stats)) return new SkillPreview.Damage(0, 0);
            double cm = ElementalistRules.fullChargeMultiplier(p);
            int bombs = Math.max(1, (int) Math.floor(TowerBattleCard.ELEM_FLAME_VOLCANO.value("duration")
                / Math.max(0.05, TowerBattleCard.ELEM_FLAME_VOLCANO.value("interval")) + 1e-9));
            double bombCoeff = p.get("damage", 0) * cm * TowerBattleCard.ELEM_FLAME_VOLCANO.ratio("damage");
            int[] elems = ElementalistRules.isPrismActive(stats) ? ElementalistRules.ALL_FOUR_ELEMENTS : new int[]{1, 3};
            return SkillPreview.multiElementDamage(progress, stats, p.skill(), bombCoeff, elems, effects).times(bombs);
        });

        registerPreview(HALLOWEEN_BUSTER, p -> (int) Math.max(1, p.get("meteorCount", 1)), true, 1);
        SkillPreviewRules.pattern(SkillCatalog.require(HALLOWEEN_BUSTER), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            int count = (int) Math.max(1, p.get("meteorCount", 1));
            return new SkillPreviewRules.Pattern(count, p.get("damage", 0) * cm, p.get("finisher", 0) * cm * count);
        });
        SkillPreviewRules.supplement(SkillCatalog.require(HALLOWEEN_BUSTER), (progress, stats, p, effects) -> {
            if (!TowerBattleCard.ELEM_BUSTER_TRIPLE.held(stats)) return new SkillPreview.Damage(0, 0);
            double cm = ElementalistRules.fullChargeMultiplier(p);
            int ticks = Math.max(1, (int) Math.floor(p.get("emberDuration", 4.0) / Math.max(0.1, p.get("emberInterval", 0.5)) + 1e-9));
            double emberCoeff = Math.max(p.get("emberDot", 0), p.get("damage", 0) * 0.08) * cm;
            return SkillPreview.damage(progress, stats, p.skill(), emberCoeff, true, effects).times(ticks);
        });
        SkillPreviewRules.finisherLabel(SkillCatalog.require(HALLOWEEN_BUSTER), "熔岩冲击波");

        registerPreview(ARCTIC_FEAST, p -> (int) Math.max(1, p.get("slashes", 8)), true, 2);
        SkillPreviewRules.pattern(SkillCatalog.require(ARCTIC_FEAST), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern((int) Math.max(1, p.get("slashes", 8)), p.get("damage", 0) * cm, p.get("finisher", 0) * cm);
        });
        SkillPreviewRules.finisherLabel(SkillCatalog.require(ARCTIC_FEAST), "终结冰爆");

        registerPreview(BLACK_HOLE, p -> (int) Math.max(1, p.get("slashes", 10)), true, 3);
        SkillPreviewRules.pattern(SkillCatalog.require(BLACK_HOLE), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern((int) Math.max(1, p.get("slashes", 10)), p.get("damage", 0) * cm, p.get("finisher", 0) * cm);
        });
        SkillPreviewRules.finisherLabel(SkillCatalog.require(BLACK_HOLE), "坍缩终爆");

        registerPreview(LIGHTNING_STRIKE, p -> (int) Math.max(1, p.get("slashes", 5)), false, 0);
        SkillPreviewRules.pattern(SkillCatalog.require(LIGHTNING_STRIKE), p -> {
            double cm = ElementalistRules.fullChargeMultiplier(p);
            return new SkillPreviewRules.Pattern((int) Math.max(1, p.get("slashes", 5)), p.get("damage", 0) * cm, 0);
        });

        registerPreview(ASTRAL_STORM, p -> (int) Math.max(1, p.get("slashes", 16)), true, -1);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(ASTRAL_STORM), "混沌主星核");
        SkillPreviewRules.total(SkillCatalog.require(ASTRAL_STORM), (progress, stats, p, effects) -> {
            int meteors = (int) Math.max(1, p.get("slashes", 16));
            double meteorCoeff = p.get("damage", 0);
            double coreCoeff = p.get("finisher", 0);
            if (ElementalistRules.isPrismActive(stats)) {
                var meteorSingle = SkillPreview.multiElementDamage(progress, stats, p.skill(), meteorCoeff, ElementalistRules.ALL_FOUR_ELEMENTS, effects);
                var coreDamage = SkillPreview.multiElementDamage(progress, stats, p.skill(), coreCoeff, ElementalistRules.ALL_FOUR_ELEMENTS, effects);
                return meteorSingle.times(meteors).plus(coreDamage);
            }
            int dyed = ElementalistRules.effectiveElement(p, stats, -1);
            SkillPreview.Damage meteorSingle;
            SkillPreview.Damage coreDamage;
            if (dyed >= 0) {
                meteorSingle = SkillPreview.elementDamage(progress, stats, p.skill(), meteorCoeff, dyed, effects);
                coreDamage = SkillPreview.elementDamage(progress, stats, p.skill(), coreCoeff, dyed, effects);
            } else {
                double[] probs = ElementalistRules.meteorProbabilities(stats);
                double min = 0, max = 0;
                for (int e = 0; e < 4; e++) {
                    if (probs[e] <= 0) continue;
                    var d = SkillPreview.elementDamage(progress, stats, p.skill(), meteorCoeff, e, effects);
                    min += probs[e] * d.minimum();
                    max += probs[e] * d.maximum();
                }
                meteorSingle = new SkillPreview.Damage(min, max);
                coreDamage = SkillPreview.multiElementDamage(progress, stats, p.skill(), coreCoeff, new int[]{0, 1, 2, 3}, effects);
            }
            return meteorSingle.times(meteors).plus(coreDamage);
        });
    }

    private static void registerPreview(String id, java.util.function.ToIntFunction<SkillParameters.Snapshot> hits, boolean finisher, int element) {
        SkillPreviewRules.register(SkillCatalog.require(id), new SkillPreviewRules.Definition(hits, finisher, false, true, element));
    }

    public static void presentation() {
        for (String id : ALL) {
            int color = ElementalistRules.elementColor(innateElement(id));
            long defaultDuration = switch (id) {
                case ICE_WALL -> 6500L;
                case VOID_SPHERE -> 4500L;
                case ARCTIC_FEAST -> 2600L;
                case BLACK_HOLE -> 3800L;
                case LIGHTNING_STRIKE -> 3800L;
                case ASTRAL_STORM -> 4800L;
                default -> 2200L;
            };
            int stiff = ASTRAL_STORM.equals(id) || LIGHTNING_STRIKE.equals(id)
                ? SkillPresentation.STIFF_SUPER : SkillPresentation.STIFF_NORMAL;
            SkillPresentation.register(SkillCatalog.require(id),
                new SkillPresentation.Traits(false, false, true, false, 0, 0, color, false,
                    c -> Math.max(defaultDuration, (long) (c.parameters.get("duration", defaultDuration / 1000.0) * 1000) + 600L),
                    false, SkillPresentation.CANCEL_NORMAL, stiff));
        }
    }
}

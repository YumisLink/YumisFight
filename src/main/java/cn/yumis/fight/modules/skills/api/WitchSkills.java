package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/** Witch (魔道学者 / 魔术师) skill definitions, previews, and presentation traits. */
public final class WitchSkills {
    private static final String NS = "yumis_dungeon_and_fight:";

    public static final String
        WITCHCRAFT_MASTERY = NS + "witchcraft_mastery",
        SHADOW_CLOAK = NS + "shadow_cloak",
        GIANT_FLYSWATTER = NS + "giant_flyswatter",
        LAVA_POTION = NS + "lava_potion",
        ACID_RAIN = NS + "acid_rain",
        ANTI_GRAVITY = NS + "anti_gravity",
        RAGING_FLAME_FURNACE = NS + "raging_flame_furnace",
        MEGA_DRILL = NS + "mega_drill",
        FLORAE_COLLIDER = NS + "florae_collider",
        FUSION_CRAFT = NS + "fusion_craft";

    public static final List<String> ALL = List.of(
        WITCHCRAFT_MASTERY, SHADOW_CLOAK, GIANT_FLYSWATTER, LAVA_POTION, ACID_RAIN,
        ANTI_GRAVITY, RAGING_FLAME_FURNACE, MEGA_DRILL, FLORAE_COLLIDER, FUSION_CRAFT
    );

    public static final List<String> ACTIVE = List.of(
        SHADOW_CLOAK, GIANT_FLYSWATTER, LAVA_POTION, ACID_RAIN,
        ANTI_GRAVITY, RAGING_FLAME_FURNACE, MEGA_DRILL, FLORAE_COLLIDER, FUSION_CRAFT
    );

    private static final Set<String> SET = Set.copyOf(ALL);

    private WitchSkills() {}

    public static boolean contains(String id) {
        return SET.contains(id);
    }

    /** Innate element: 0=Light, 1=Fire, 2=Ice, 3=Dark, -1=All/Multi. */
    public static int innateElement(String id) {
        if (FLORAE_COLLIDER.equals(id) || MageSkills.STAR.equals(id)) return 0;
        if (LAVA_POTION.equals(id) || RAGING_FLAME_FURNACE.equals(id)) return 1;
        if (ACID_RAIN.equals(id) || MEGA_DRILL.equals(id)) return 2;
        if (SHADOW_CLOAK.equals(id) || GIANT_FLYSWATTER.equals(id) || ANTI_GRAVITY.equals(id)) return 3;
        return -1;
    }

    static void install() {
        // 1. Lv20 Unified Core Passive: 魔道学概论 (Broomstick Mastery + Lucky Lollipop + Four Familiar Affinity)
        var mastery = register(WITCHCRAFT_MASTERY, "魔道学概论", "WITCHCRAFT", 20, 30, 0, 0, 0, true,
            Map.of("range", 0d, "slashes", 1d, "duration", 0d));
        mastery.automaticByJob = true;
        mastery.attackTypeRanks = false;
        mastery.formParameter("broomBonusScale", 1.0)
            .formParameter("familiarBaseElem", 6.0)
            .formParameter("buildupBonusRatio", 0.35)
            .formParameter("ailmentResonancePerKind", 0.025)
            .formParameter("broomAirDashCount", 2.0)
            .formParameter("broomDashSpeed", 1.15);

        // 2. Lv20 Active (Dark, invulnerable gather + dark burst, air-castable): 暗影斗篷
        var cloak = active(SHADOW_CLOAK, "暗影斗篷", "WITCH_DARK", 20, 30, 28, 8.0, 6.5, false);
        cloak.damageParameter("finisher", 13.5)
            .formParameter("range", 5.5)
            .formParameter("centerOffset", 2.8)
            .formParameter("radius", 4.5)
            .formParameter("pullRadius", 5.0)
            .formParameter("invulnerableMillis", 650.0)
            .formParameter("grabDelay", 0.12)
            .formParameter("burstDelay", 0.45)
            .formParameter("controlSeconds", 0.65)
            .formParameter("slashes", 1.0)
            .formParameter("duration", 0.65);

        // 3. Lv20 Active (Dark heavy slam + Lucky Candy Blessing + 2~3 random mini-familiar puppets): 变异苍蝇拍
        var swat = active(GIANT_FLYSWATTER, "变异苍蝇拍", "WITCH_DARK", 20, 30, 30, 7.5, 16.5, false);
        swat.damageParameter("puppetDamage", 2.8)
            .formParameter("range", 5.5)
            .formParameter("centerOffset", 2.8)
            .formParameter("radius", 3.8)
            .formParameter("impactDelay", 0.22)
            .formParameter("buffDuration", 15.0)
            .formParameter("buffDamageMut", 0.06)
            .formParameter("buffCastSpeed", 0.12)
            .formParameter("buffMoveSpeed", 0.10)
            .formParameter("summonBaseCount", 2.0)
            .formParameter("summonBonusCount", 1.0)
            .formParameter("summonRadius", 2.4)
            .formParameter("summonSeekRange", 10.0)
            .formParameter("summonLifetime", 1.8)
            .formParameter("slashes", 1.0)
            .formParameter("duration", 0.50);
        SkillDamagePhases.register(GIANT_FLYSWATTER, "swat", true, false);
        SkillDamagePhases.register(GIANT_FLYSWATTER, "puppet", true, false);

        // 4. Lv25 Active (Fire ground pool + 3 Great Success lava geysers, air-throwable): 熔岩药瓶
        var lava = active(LAVA_POTION, "熔岩药瓶", "WITCH_FIRE", 25, 40, 46, 11.0, 1.35, true);
        lava.damageParameter("geyserDamage", 4.6)
            .formParameter("range", 16.0)
            .formParameter("radius", 7.68)
            .formParameter("geyserRadius", 5.12)
            .formParameter("throwDelay", 0.25)
            .formParameter("duration", 8.0)
            .formParameter("interval", 0.50)
            .formParameter("slashes", 16.0)
            .formParameter("geyserCount", 3.0)
            .formParameter("slowRatio", 0.30);
        SkillDamagePhases.register(LAVA_POTION, "pool", true, false);
        SkillDamagePhases.register(LAVA_POTION, "geyser", true, false);

        // 5. Lv25 Active (Ice acid rain + 4 Great Success Light thunderbolts, air-castable): 魔道酸雨云
        var acid = active(ACID_RAIN, "魔道酸雨云", "WITCH_ICE", 25, 40, 48, 12.0, 1.10, true);
        acid.damageParameter("lightningDamage", 4.2)
            .formParameter("range", 16.0)
            .formParameter("radius", 8.32)
            .formParameter("boltRadius", 5.12)
            .formParameter("cloudHeight", 5.4)
            .formParameter("spawnDelay", 0.20)
            .formParameter("duration", 8.0)
            .formParameter("interval", 0.50)
            .formParameter("slashes", 16.0)
            .formParameter("boltCount", 4.0)
            .formParameter("boltInterval", 1.60);
        SkillDamagePhases.register(ACID_RAIN, "rain", true, false);
        SkillDamagePhases.register(ACID_RAIN, "lightning", true, false);

        // 6. Lv30 Active (Dark deployable anti-gravity lift + heavy dark slam): 反重力装置
        var antiG = active(ANTI_GRAVITY, "反重力装置", "WITCH_DARK", 30, 50, 78, 15.0, 12.5, true);
        antiG.damageParameter("finisher", 35.5)
            .formParameter("range", 15.0)
            .formParameter("radius", 5.5)
            .formParameter("blastRadius", 6.2)
            .formParameter("liftDelay", 0.35)
            .formParameter("slamDelay", 0.85)
            .formParameter("controlSeconds", 0.50)
            .formParameter("slashes", 1.0)
            .formParameter("duration", 1.20);

        // 7. Lv35 Active (Fire riding furnace + 14 lava barrages + Bitter Lollipop early detonate): 爆炎加热炉
        var furnace = active(RAGING_FLAME_FURNACE, "爆炎加热炉", "WITCH_FIRE", 35, 50, 108, 20.0, 3.95, false);
        furnace.damageParameter("finisher", 26.0)
            .formParameter("range", 8.0)
            .formParameter("radius", 7.5)
            .formParameter("blastRadius", 6.5)
            .formParameter("duration", 2.80)
            .formParameter("interval", 0.20)
            .formParameter("slashes", 14.0)
            .formParameter("channelProtection", 0.55)
            .formParameter("minChannelSeconds", 0.0)
            .formParameter("bitterKeepRatio", 0.65)
            .formParameter("bitterLaunchHeight", 2.2);

        // 8. Lv40 Active (Ice drivable mega drill + vortex pull + Bitter Lollipop early detonate): 冰霜钻孔车
        var drill = active(MEGA_DRILL, "冰霜钻孔车", "WITCH_ICE", 40, 60, 138, 24.0, 4.50, false);
        drill.damageParameter("finisher", 36.0)
            .formParameter("range", 7.5)
            .formParameter("pullRadius", 7.5)
            .formParameter("pullStrength", 4.8)
            .formParameter("radius", 4.2)
            .formParameter("blastRadius", 6.8)
            .formParameter("driveSpeedRatio", 1.35)
            .formParameter("duration", 3.20)
            .formParameter("interval", 0.20)
            .formParameter("slashes", 16.0)
            .formParameter("channelProtection", 0.60)
            .formParameter("minChannelSeconds", 0.0)
            .formParameter("bitterKeepRatio", 0.65)
            .formParameter("bitterDashDistance", 3.0);

        // 9. Lv45 Active (Light riding collider + massive chain-lightning CC + Bitter Lollipop detonate): 电鳗碰撞机
        var collider = active(FLORAE_COLLIDER, "电鳗碰撞机", "WITCH_LIGHT", 45, 80, 178, 30.0, 6.80, false);
        collider.damageParameter("finisher", 42.0)
            .formParameter("range", 9.0)
            .formParameter("radius", 8.5)
            .formParameter("blastRadius", 8.5)
            .formParameter("duration", 3.00)
            .formParameter("interval", 0.25)
            .formParameter("slashes", 12.0)
            .formParameter("controlSeconds", 0.40)
            .formParameter("bitterControlSeconds", 0.80)
            .formParameter("channelProtection", 0.55)
            .formParameter("minChannelSeconds", 0.0)
            .formParameter("bitterKeepRatio", 0.65);

        // 10. Lv50 Awakening (All 4 elements invulnerable Fusion Craft roller + Bitter Overload): 技艺融合
        var fusion = active(FUSION_CRAFT, "技艺融合", "WITCH_AWAKENING", 50, 100, 285, 90.0, 15.0, false);
        fusion.damageParameter("finisher", 145.0)
            .formParameter("range", 10.5)
            .formParameter("pullRadius", 9.5)
            .formParameter("radius", 7.5)
            .formParameter("finisherRadius", 10.5)
            .formParameter("windupSeconds", 0.40)
            .formParameter("duration", 3.60)
            .formParameter("interval", 0.20)
            .formParameter("slashes", 16.0)
            .formParameter("driveSpeedRatio", 0.55)
            .formParameter("minChannelSeconds", 0.0)
            .formParameter("bitterKeepRatio", 0.65)
            .formParameter("invulnerable", 1.0);

        // Extend base Lv1 Magic Star with Witch Improved Magic Star (改良魔法星弹) parameters
        var star = MageSkills.skill(MageSkills.STAR);
        star.formParameter("witchBounceCount", 3.0)
            .formParameter("witchBounceRange", 5.5)
            .formParameter("witchBlastRadius", 2.2)
            .formParameter("witchSoloEchoRatio", 0.60)
            .formParameter("dyeEnabled", 0.0)
            .formParameter("dyeElement", 0.0);
    }

    private static SkillDefinition active(String id, String title, String family, int unlock, int sp,
                                          double mp, double cd, double coefficient, boolean groundTargeted) {
        var skill = register(id, title, family, unlock, sp, mp, cd, coefficient, false,
            Map.of("range", 12.0, "slashes", 1.0, "duration", 0.5));
        skill.groundTargeted = groundTargeted;
        skill.formParameter("morphMode", 0.0)
            .formParameter("maxCharges", 1.0)
            .formParameter("dyeEnabled", 0.0)
            .formParameter("dyeElement", 0.0);
        return skill;
    }

    private static SkillDefinition register(String id, String title, String family, int unlock, int sp,
                                            double mp, double cd, double coefficient, boolean passive,
                                            Map<String, Double> base) {
        double mpRatio = mp <= 0 ? 0 : mp / (100.0 + 14.0 * (unlock - 1));
        var skill = new SkillDefinition(id, title, family, JobCatalog.WITCH, unlock, sp, mpRatio, cd, coefficient, passive, base);
        skill.baseJob = JobCatalog.MAGE;
        skill.magicDamage = true;
        skill.magicTag = true;
        return SkillCatalog.register(skill);
    }

    static void variants() {
        MageSkills.careerScales(JobCatalog.WITCH, 1.0, 0.5);
    }

    public static void previews() {
        SkillPreviewRules.register(SkillCatalog.require(WITCHCRAFT_MASTERY),
            new SkillPreviewRules.Definition(p -> 0, false, false, true, -1));

        registerPreview(SHADOW_CLOAK, p -> 1, true, 3);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(SHADOW_CLOAK), "暗影爆破");

        // Giant Flyswatter: dark slam + 2..3 random 4-element mini-familiar explosions
        var swat = SkillCatalog.require(GIANT_FLYSWATTER);
        registerPreview(GIANT_FLYSWATTER, p -> 1, false, 3);
        SkillPreviewRules.total(swat, (progress, stats, p, effects) -> {
            int dyed = WitchRules.effectiveElement(p, stats, 3);
            var main = SkillPreview.elementDamage(progress, stats, swat, p.get("damage", 0), dyed, effects);
            int puppets = (int) Math.max(1, Math.round(p.get("summonBaseCount", 2.0)));
            double puppetCoeff = p.get("puppetDamage", 0);
            if (puppetCoeff <= 0) return main;
            double pMin = 0, pMax = 0;
            int puppetDyed = WitchRules.effectiveElement(p, stats, -1);
            if (puppetDyed >= 0) {
                var d = SkillPreview.elementDamage(progress, stats, swat, puppetCoeff, puppetDyed, effects);
                pMin = d.minimum();
                pMax = d.maximum();
            } else {
                for (int e = 0; e < 4; e++) {
                    var d = SkillPreview.elementDamage(progress, stats, swat, puppetCoeff, e, effects);
                    pMin += 0.25 * d.minimum();
                    pMax += 0.25 * d.maximum();
                }
            }
            return main.plus(new SkillPreview.Damage(pMin * puppets, pMax * puppets));
        });

        // Lava Potion: 16 fire ticks + 3 fire geysers
        var lava = SkillCatalog.require(LAVA_POTION);
        registerPreview(LAVA_POTION, p -> (int) Math.max(1, p.get("slashes", 16)), false, 1);
        SkillPreviewRules.total(lava, (progress, stats, p, effects) -> {
            int elem = WitchRules.effectiveElement(p, stats, 1);
            int ticks = (int) Math.max(1, p.get("slashes", 16));
            int geysers = (int) Math.max(0, Math.round(p.get("geyserCount", 3.0)));
            var tickDmg = SkillPreview.elementDamage(progress, stats, lava, p.get("damage", 0), elem, effects).times(ticks);
            var geyserDmg = geysers > 0
                ? SkillPreview.elementDamage(progress, stats, lava, p.get("geyserDamage", 0), elem, effects).times(geysers)
                : new SkillPreview.Damage(0, 0);
            return tickDmg.plus(geyserDmg);
        });

        // Acid Rain: 16 ice ticks + 4 light lightning bolts
        var acid = SkillCatalog.require(ACID_RAIN);
        registerPreview(ACID_RAIN, p -> (int) Math.max(1, p.get("slashes", 16)), false, 2);
        SkillPreviewRules.total(acid, (progress, stats, p, effects) -> {
            int rainElem = WitchRules.effectiveElement(p, stats, 2);
            int boltElem = WitchRules.effectiveElement(p, stats, 0);
            int ticks = (int) Math.max(1, p.get("slashes", 16));
            int bolts = (int) Math.max(0, Math.round(p.get("boltCount", 4.0)));
            var rainDmg = SkillPreview.elementDamage(progress, stats, acid, p.get("damage", 0), rainElem, effects).times(ticks);
            var boltDmg = bolts > 0
                ? SkillPreview.elementDamage(progress, stats, acid, p.get("lightningDamage", 0), boltElem, effects).times(bolts)
                : new SkillPreview.Damage(0, 0);
            return rainDmg.plus(boltDmg);
        });

        registerPreview(ANTI_GRAVITY, p -> 1, true, 3);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(ANTI_GRAVITY), "重力砸地");

        registerPreview(RAGING_FLAME_FURNACE, p -> (int) Math.max(1, p.get("slashes", 14)), true, 1);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(RAGING_FLAME_FURNACE), "炉体终爆");

        registerPreview(MEGA_DRILL, p -> (int) Math.max(1, p.get("slashes", 16)), true, 2);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(MEGA_DRILL), "暴风雪终爆");

        registerPreview(FLORAE_COLLIDER, p -> (int) Math.max(1, p.get("slashes", 12)), true, 0);
        SkillPreviewRules.finisherLabel(SkillCatalog.require(FLORAE_COLLIDER), "超导雷爆");

        // Fusion Craft: 16 hits rotating across 4 elements + multi-element {0,1,2,3} nuclear finisher
        var fusion = SkillCatalog.require(FUSION_CRAFT);
        registerPreview(FUSION_CRAFT, p -> (int) Math.max(1, p.get("slashes", 16)), true, -1);
        SkillPreviewRules.finisherLabel(fusion, "全元素融合核爆");
        SkillPreviewRules.total(fusion, (progress, stats, p, effects) -> {
            int hits = (int) Math.max(1, p.get("slashes", 16));
            double hitCoeff = p.get("damage", 0);
            double finCoeff = p.get("finisher", 0);
            int dyed = WitchRules.effectiveElement(p, stats, -1);
            SkillPreview.Damage crush;
            SkillPreview.Damage fin;
            if (dyed >= 0) {
                crush = SkillPreview.elementDamage(progress, stats, fusion, hitCoeff, dyed, effects).times(hits);
                fin = SkillPreview.elementDamage(progress, stats, fusion, finCoeff, dyed, effects);
            } else {
                double min = 0, max = 0;
                for (int i = 0; i < hits; i++) {
                    int elem = (i + 1) % 4; // Fire(1)->Ice(2)->Dark(3)->Light(0) balanced 4-cycle
                    var d = SkillPreview.elementDamage(progress, stats, fusion, hitCoeff, elem, effects);
                    min += d.minimum();
                    max += d.maximum();
                }
                crush = new SkillPreview.Damage(min, max);
                fin = SkillPreview.multiElementDamage(progress, stats, fusion, finCoeff, WitchRules.ALL_FOUR_ELEMENTS, effects);
            }
            return crush.plus(fin);
        });
    }

    private static void registerPreview(String id, java.util.function.ToIntFunction<SkillParameters.Snapshot> hits, boolean finisher, int element) {
        SkillPreviewRules.register(SkillCatalog.require(id), new SkillPreviewRules.Definition(hits, finisher, false, true, element));
    }

    public static void presentation() {
        for (String id : ALL) {
            int color = WitchRules.elementColor(innateElement(id));
            boolean invuln = FUSION_CRAFT.equals(id);
            boolean superStiff = FUSION_CRAFT.equals(id) || FLORAE_COLLIDER.equals(id) || MEGA_DRILL.equals(id) || RAGING_FLAME_FURNACE.equals(id);
            int stiff = superStiff ? SkillPresentation.STIFF_SUPER : SkillPresentation.STIFF_NORMAL;
            int cancel = SHADOW_CLOAK.equals(id) ? SkillPresentation.CANCEL_MOBILITY : SkillPresentation.CANCEL_NORMAL;
            long defaultDuration = switch (id) {
                case SHADOW_CLOAK -> 900L;
                case GIANT_FLYSWATTER -> 1200L;
                case LAVA_POTION, ACID_RAIN -> 8600L;
                case ANTI_GRAVITY -> 1600L;
                case RAGING_FLAME_FURNACE -> 3400L;
                case MEGA_DRILL -> 3800L;
                case FLORAE_COLLIDER -> 3600L;
                case FUSION_CRAFT -> 4200L;
                default -> 1400L;
            };
            SkillPresentation.register(SkillCatalog.require(id),
                new SkillPresentation.Traits(false, false, true, invuln, 0, 0, color, false,
                    c -> Math.max(defaultDuration, (long) (c.parameters.get("duration", defaultDuration / 1000.0) * 1000) + 600L),
                    false, cancel, stiff));
        }
    }
}

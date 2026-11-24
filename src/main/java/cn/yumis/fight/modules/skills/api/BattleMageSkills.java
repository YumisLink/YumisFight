package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/**
 * Battle Mage (战斗法师 / 贝亚娜斗神) skill definitions, strict physical/magical phase split,
 * calibrated balance profiles, previews and presentation traits.
 */
public final class BattleMageSkills {
    private static final String NS = "yumis_dungeon_and_fight:";
    public static final String
        WEAPON_MASTERY = NS + "battle_weapon_mastery",
        BATTLE_GROOVE = NS + "battle_groove",
        CHASER_LAUNCHER = NS + "chaser_launcher",
        PALM_BLAST = NS + "palm_blast",
        FULL_SWING = NS + "full_swing",
        CHASER_FUSION = NS + "chaser_fusion",
        CHASER_PRESS = NS + "chaser_press",
        PUNTO_WAVE = NS + "punto_wave",
        DRAGON_STRIKE = NS + "dragon_strike",
        DRAGON_SPEAR = NS + "dragon_spear",
        TEANA_TRANSFORMATION = NS + "teana_transformation";

    public static final List<String> ALL = List.of(
        WEAPON_MASTERY, BATTLE_GROOVE, CHASER_LAUNCHER, PALM_BLAST, FULL_SWING,
        CHASER_FUSION, CHASER_PRESS, PUNTO_WAVE, DRAGON_STRIKE, DRAGON_SPEAR, TEANA_TRANSFORMATION
    );

    public static final List<String> ACTIVE = List.of(
        CHASER_LAUNCHER, PALM_BLAST, FULL_SWING, CHASER_FUSION, CHASER_PRESS,
        PUNTO_WAVE, DRAGON_STRIKE, DRAGON_SPEAR, TEANA_TRANSFORMATION
    );

    private static final Set<String> SET = Set.copyOf(ALL);

    private BattleMageSkills() {}

    public static boolean contains(String id) {
        return SET.contains(id);
    }

    static void install() {
        // 1. Lv20 Passive: Weapon Mastery (auto Lv1, upgradeable to Lv13)
        var mastery = register(WEAPON_MASTERY, "战法武器精通", "MARTIAL", 20, 30, 0, 0, 0, true, false,
            Map.of("range", 0d, "slashes", 1d, "duration", 0d));
        mastery.automaticByJob = true;
        mastery.attackTypeRanks = false;
        mastery.formParameter("spearBonusScale", 1.0)
            .formParameter("spearRangeScale", 1.15)
            .formParameter("spearChargeScale", 0.85)
            .formParameter("clubBonusScale", 1.0)
            .formParameter("clubAutoChaserScale", 0.80)
            .formParameter("clubChaserRadiusScale", 1.15)
            .formParameter("staffBonusScale", 1.0)
            .formParameter("staffChaserCdScale", 0.85);

        // 2. Lv20 Core Passive: Battle Groove + Auto Chaser + Niu Combat Art (auto Lv1, upgradeable to Lv13)
        var groove = register(BATTLE_GROOVE, "斗神意志", "MARTIAL", 20, 30, 0, 0, 0, true, false,
            Map.of("range", 0d, "slashes", 1d, "duration", 0d));
        groove.automaticByJob = true;
        groove.attackTypeRanks = false;
        groove.formParameter("comboStageCap", 6.0)
            .formParameter("comboThresholdScale", 1.0)
            .formParameter("comboHoldSeconds", 8.0)
            .formParameter("grooveBonusScale", 1.0)
            .formParameter("perStageCrit", 0.015)
            .formParameter("autoChaserInterval", 4.0)
            .formParameter("autoChaserChance", 1.0)
            .formParameter("basicChaserChance", 0.0)
            .formParameter("forcedChaserEnabled", 0.0)
            .formParameter("forcedChaserType", 0.0)
            .formParameter("neutralWeight", 1.0)
            .formParameter("lightWeight", 1.0)
            .formParameter("fireWeight", 1.0)
            .formParameter("iceWeight", 1.0)
            .formParameter("darkWeight", 1.0);

        // 3. Lv20 Active (Pure Magic, 0-GCD overlay cast): Chaser Launcher (5 independent colors!)
        var launcher = active(CHASER_LAUNCHER, "炫纹发射", "CHASER", 20, 30, 8, 0.45, 1.0, true);
        launcher.damageParameter("neutralDamage", 0.95)
            .damageParameter("lightDamage", 1.00)
            .damageParameter("fireDamage", 1.08)
            .damageParameter("iceDamage", 0.98)
            .damageParameter("darkDamage", 1.05)
            .formParameter("maxChasers", 7.0)
            .formParameter("chaserDuration", 25.0)
            .formParameter("buffDuration", 20.0)
            .formParameter("radius", 2.4)
            .formParameter("range", 16.0)
            .formParameter("projectileSpeed", 28.0)
            .formParameter("lockWindowSeconds", 1.2)
            .formParameter("neutralMoveSpeed", 0.15)
            .formParameter("lightAttackSpeed", 0.12)
            .formParameter("fireDamageMut", 0.05)
            .formParameter("iceDamageReduction", 0.08)
            .formParameter("darkCritBonus", 0.08)
            .formParameter("autoFireOnHit", 0.0)
            .formParameter("extraVolleyCount", 0.0);

        // 4. Lv20 Active (Pure Physical, instant / hold-to-charge): Palm Blast (落花掌 / 苍天击)
        var palm = active(PALM_BLAST, "落花掌", "MARTIAL", 20, 30, 26, 6.0, 1.0, false);
        palm.formParameter("chargeSeconds", 0.45)
            .formParameter("minChargeRatio", 0.80)
            .formParameter("chargeDamageMultiplier", 1.30)
            .formParameter("chargeDashMultiplier", 1.60)
            .formParameter("chargeScaleMultiplier", 1.20)
            .formParameter("maxHoldSeconds", 1.5)
            .formParameter("chargeMoveSpeedRatio", 0.65)
            .formParameter("dashDistance", 4.5)
            .formParameter("dashSeconds", 0.28)
            .formParameter("radius", 2.6)
            .formParameter("knockbackDistance", 3.5)
            .formParameter("cancelWindowSeconds", 0.45)
            .formParameter("chaserGen", 1.0);

        // 5. Lv25 Active (Pure Physical + optional Magic morph wave): Full Swing (碎霸 / 天地碎霸)
        var swing = active(FULL_SWING, "碎霸", "MARTIAL", 25, 40, 42, 7.5, 1.0, false);
        swing.damageParameter("chaserSlash", 0.0)
            .formParameter("radius", 4.5)
            .formParameter("arcDegrees", 210.0)
            .formParameter("cancelWindowSeconds", 0.45)
            .formParameter("consumeChaserForBoost", 0.0)
            .formParameter("chaserGen", 1.0);
        SkillDamagePhases.register(FULL_SWING, "phys", false, false);
        SkillDamagePhases.register(FULL_SWING, "magicWave", true, false);

        // 6. Lv30 Active (Pure Magic, consumes 2 Chasers + Dual Buff): Chaser Fusion (炫纹融合)
        var fusion = active(CHASER_FUSION, "炫纹融合", "CHASER", 30, 50, 72, 11.0, 1.0, true);
        fusion.formParameter("consumeCount", 2.0)
            .formParameter("autoGenerateOnEmpty", 0.0)
            .formParameter("radius", 4.2)
            .formParameter("centerOffset", 3.2)
            .formParameter("impactDelay", 0.25)
            .formParameter("fusionBuffDuration", 15.0)
            .formParameter("fusionAttackBonus", 0.08)
            .formParameter("fusionCritBonus", 0.06);

        // 7. Lv35 Active (Pure Magic, consumes all Chasers): Chaser Press (炫纹强压)
        var press = active(CHASER_PRESS, "炫纹强压", "CHASER", 35, 50, 105, 16.0, 0.35, true);
        press.damageParameter("perChaserDamage", 0.1083333333)
            .formParameter("radius", 3.5)
            .formParameter("radiusPerChaser", 0.45)
            .formParameter("autoGenerateOnEmpty", 1.0)
            .formParameter("impactDelay", 0.30)
            .formParameter("controlSeconds", 0.80)
            .formParameter("pullRadius", 0.0);

        // 8. Lv35 Active (Pure Physical rapid thrusts + sweep): Punto Wave (流星闪影击)
        var punto = active(PUNTO_WAVE, "流星闪影击", "MARTIAL", 35, 50, 105, 18.0, 0.0541666667, false);
        punto.damageParameter("finisher", 0.35)
            .formParameter("slashes", 12.0)
            .formParameter("interval", 0.09)
            .formParameter("chaserEveryHits", 3.0)
            .formParameter("range", 5.5)
            .formParameter("width", 3.2)
            .formParameter("finisherRadius", 5.5)
            .formParameter("stepMoveSpeed", 2.0)
            .formParameter("cancelWindowSeconds", 0.45)
            .formParameter("duration", 1.35);

        // 9. Lv40 Active (Pure Physical, instant / hold-to-charge): Dragon Strike (强袭流星打)
        var strike = active(DRAGON_STRIKE, "强袭流星打", "MARTIAL", 40, 60, 145, 24.0, 0.40, false);
        strike.damageParameter("finisher", 0.60)
            .formParameter("chargeSeconds", 0.60)
            .formParameter("minChargeRatio", 0.78)
            .formParameter("chargeDamageMultiplier", 1.30)
            .formParameter("chargeDashMultiplier", 1.45)
            .formParameter("chargeScaleMultiplier", 1.25)
            .formParameter("maxHoldSeconds", 1.5)
            .formParameter("chargeMoveSpeedRatio", 0.65)
            .formParameter("dashDistance", 9.0)
            .formParameter("dashSeconds", 0.30)
            .formParameter("width", 3.5)
            .formParameter("blastRadius", 5.0)
            .formParameter("returnToOrigin", 1.0)
            .formParameter("dashCount", 1.0)
            .formParameter("chaserGen", 2.0);

        // 10. Lv45 Active (Hybrid Physical + Magic!): Dragon Spear (煌龙偃月)
        var spear = active(DRAGON_SPEAR, "煌龙偃月", "MARTIAL", 45, 80, 190, 32.0, 0.25, false);
        spear.damageParameter("thrust", 0.25)
            .damageParameter("dragonChaser", 0.05)
            .damageParameter("finisherPhys", 0.175)
            .damageParameter("finisherMagic", 0.175)
            .formParameter("dragonChaserCount", 8.0)
            .formParameter("interval", 0.14)
            .formParameter("duration", 2.0)
            .formParameter("holdSeconds", 1.4)
            .formParameter("range", 7.5)
            .formParameter("width", 3.2)
            .formParameter("blastRadius", 6.5)
            .formParameter("chaserGen", 2.0);
        SkillDamagePhases.register(DRAGON_SPEAR, "thrust", false, false);
        SkillDamagePhases.register(DRAGON_SPEAR, "dragonChaser", true, false);
        SkillDamagePhases.register(DRAGON_SPEAR, "finisherPhys", false, false);
        SkillDamagePhases.register(DRAGON_SPEAR, "finisherMagic", true, false);

        // 11. Lv50 Awakening (Hybrid Physical + Magic Transformation + 2nd-Press Finisher): Teana Transformation
        var teana = active(TEANA_TRANSFORMATION, "变身：贝亚娜", "MARTIAL", 50, 100, 285, 90.0, 0.15, false);
        teana.damageParameter("auraPhys", 0.15)
            .damageParameter("auraMagic", 0.15)
            .damageParameter("finisherPhys", 0.35)
            .damageParameter("finisherMagic", 0.35)
            .formParameter("transformSeconds", 30.0)
            .formParameter("finisherDuration", 1.4)
            .formParameter("keepTransformAfterFinisher", 0.0)
            .formParameter("teanaChaserScale", 1.45)
            .formParameter("teanaSkillScale", 1.30)
            .formParameter("teanaShieldRatio", 0.25)
            .formParameter("teanaAttackSpeed", 0.20)
            .formParameter("teanaMoveSpeed", 0.20)
            .formParameter("teanaCritBonus", 0.12)
            .formParameter("auraRadius", 5.5)
            .formParameter("finisherRadius", 8.5)
            .formParameter("range", 10.0)
            .formParameter("chaserGen", 3.0);
        SkillDamagePhases.register(TEANA_TRANSFORMATION, "auraPhys", false, false);
        SkillDamagePhases.register(TEANA_TRANSFORMATION, "auraMagic", true, false);
        SkillDamagePhases.register(TEANA_TRANSFORMATION, "finisherPhys", false, false);
        SkillDamagePhases.register(TEANA_TRANSFORMATION, "finisherMagic", true, false);

        // Extend base Dragon Fang with Battle Mage cancel/morph/chaserGen form parameters
        var fang = MageSkills.skill(MageSkills.FANG);
        fang.formParameter("morphMode", 0.0)
            .formParameter("maxCharges", 1.0)
            .formParameter("cancelBonusMut", 0.05)
            .formParameter("cancelWindowSeconds", 0.45)
            .formParameter("chaserGen", 1.0);
    }

    private static SkillDefinition active(String id, String title, String family, int unlock, int sp,
                                          double mp, double cd, double coefficient, boolean magic) {
        var skill = register(id, title, family, unlock, sp, mp, cd, coefficient, false, magic,
            Map.of("range", 5.0, "slashes", 1.0, "duration", 0.5));
        skill.formParameter("morphMode", 0.0)
            .formParameter("maxCharges", 1.0)
            .formParameter("cancelBonusMut", 0.05);
        return skill;
    }

    private static SkillDefinition register(String id, String title, String family, int unlock, int sp,
                                            double mp, double cd, double coefficient, boolean passive, boolean magic,
                                            Map<String, Double> base) {
        double mpRatio = mp <= 0 ? 0 : mp / (100.0 + 14.0 * (unlock - 1));
        var skill = new SkillDefinition(id, title, family, JobCatalog.BATTLE_MAGE, unlock, sp, mpRatio, cd, coefficient, passive, base);
        skill.baseJob = JobCatalog.MAGE;
        skill.magicDamage = magic;
        skill.magicTag = magic;
        return SkillCatalog.register(skill);
    }

    private static SkillBalanceProfile.Hit hit(double seconds, double coeff) {
        return new SkillBalanceProfile.Hit(Math.round(seconds * 1000), coeff, false);
    }

    static void balance() {
        MageSkills.careerScales(JobCatalog.BATTLE_MAGE, 1.0, 0.5);
        SkillBalance.nonDamage(SkillCatalog.require(WEAPON_MASTERY));
        SkillBalance.nonDamage(SkillCatalog.require(BATTLE_GROOVE));

        // Chaser Launcher uses a 2.5s effective generation cadence for balance reference so its short 0.45s burst CD doesn't starve per-orb damage.
        SkillBalance.register(SkillCatalog.require(CHASER_LAUNCHER), new SkillBalanceProfile(1.8, 2.5, false, false, 0, (s, r, c) -> {
            var p = s.parameters(r);
            double avg = (p.get("neutralDamage") + p.get("lightDamage") + p.get("fireDamage")
                + p.get("iceDamage") + p.get("darkDamage")) / 5.0;
            return List.of(hit(0.10, avg));
        }));

        registerProfile(PALM_BLAST, 1.8, (s, r, c) -> {
            var p = s.parameters(r);
            double cm = p.getOrDefault("chargeDamageMultiplier", 1.30);
            return List.of(hit(Math.max(0.05, p.getOrDefault("dashSeconds", 0.28)), p.get("damage") * cm));
        });

        registerProfile(FULL_SWING, 2.4, (s, r, c) -> {
            var p = s.parameters(r);
            return List.of(hit(0.16, p.get("damage")));
        });

        registerProfile(CHASER_FUSION, 3.0, (s, r, c) -> {
            var p = s.parameters(r);
            return List.of(hit(Math.max(0.05, p.getOrDefault("impactDelay", 0.25)), p.get("damage")));
        });

        registerProfile(CHASER_PRESS, 4.0, (s, r, c) -> {
            var p = s.parameters(r);
            int cap = (int) Math.max(1, SkillCatalog.require(CHASER_LAUNCHER).parameters(r).getOrDefault("maxChasers", 7.0));
            double total = p.get("damage") + p.get("perChaserDamage") * (cap - 1);
            return List.of(hit(Math.max(0.05, p.getOrDefault("impactDelay", 0.30)), total));
        });

        registerProfile(PUNTO_WAVE, 3.8, (s, r, c) -> {
            var p = s.parameters(r);
            int hits = (int) Math.max(1, p.getOrDefault("slashes", 12.0));
            double gap = Math.max(0.05, p.getOrDefault("interval", 0.09));
            var out = new ArrayList<SkillBalanceProfile.Hit>(hits + 1);
            for (int i = 0; i < hits; i++) out.add(hit(0.10 + i * gap, p.get("damage")));
            out.add(hit(0.10 + hits * gap + 0.15, p.get("finisher")));
            return out;
        });

        registerProfile(DRAGON_STRIKE, 5.0, (s, r, c) -> {
            var p = s.parameters(r);
            double cm = p.getOrDefault("chargeDamageMultiplier", 1.30);
            double dash = Math.max(0.05, p.getOrDefault("dashSeconds", 0.30));
            return List.of(hit(dash * 0.5, p.get("damage") * cm), hit(dash + 0.12, p.get("finisher") * cm));
        });

        registerProfile(DRAGON_SPEAR, 5.6, (s, r, c) -> {
            var p = s.parameters(r);
            int balls = (int) Math.max(1, p.getOrDefault("dragonChaserCount", 8.0));
            double gap = Math.max(0.05, p.getOrDefault("interval", 0.14));
            var out = new ArrayList<SkillBalanceProfile.Hit>(balls + 3);
            out.add(hit(0.15, p.get("thrust")));
            for (int i = 0; i < balls; i++) out.add(hit(0.35 + i * gap, p.get("dragonChaser")));
            double endAt = Math.max(0.55, 0.35 + balls * gap + 0.20);
            out.add(hit(endAt, p.get("finisherPhys")));
            out.add(hit(endAt, p.get("finisherMagic")));
            return out;
        });

        registerProfile(TEANA_TRANSFORMATION, 10.0, (s, r, c) -> {
            var p = s.parameters(r);
            return List.of(
                hit(0.20, p.get("auraPhys")),
                hit(0.20, p.get("auraMagic")),
                hit(1.20, p.get("finisherPhys")),
                hit(1.20, p.get("finisherMagic"))
            );
        });
    }

    private static void registerProfile(String id, double weight, SkillBalanceProfile.Pattern pattern) {
        SkillBalance.register(SkillCatalog.require(id), new SkillBalanceProfile(weight, 0, false, false, 0, pattern));
    }

    public static void previews() {
        SkillPreviewRules.register(SkillCatalog.require(WEAPON_MASTERY), new SkillPreviewRules.Definition(p -> 0, false, false, false, -1));
        SkillPreviewRules.register(SkillCatalog.require(BATTLE_GROOVE), new SkillPreviewRules.Definition(p -> 0, false, false, false, -1));

        // Chaser Launcher: deterministic expectation across 5 color weights
        var launcher = SkillCatalog.require(CHASER_LAUNCHER);
        SkillPreviewRules.register(launcher, new SkillPreviewRules.Definition(p -> 1 + (int) Math.max(0, p.get("extraVolleyCount", 0)), false, false, true, -1));
        SkillPreviewRules.total(launcher, (progress, stats, p, effects) -> {
            var gp = BattleMageRules.grooveParams(stats, progress);
            double[] probs = BattleMageRules.chaserProbabilities(gp);
            boolean teana = stats.modifiers.getOrDefault("bmTeana", 0.0) > 0;
            double teanaScale = stats.modifiers.getOrDefault("bmTeanaChaserScale", 1.45);
            int volley = 1 + (int) Math.max(0, p.get("extraVolleyCount", 0));
            double min = 0, max = 0;
            for (int c = 0; c < 5; c++) {
                if (probs[c] <= 0) continue;
                double coeff = BattleMageRules.chaserCoefficient(p, c, teana, teanaScale);
                int[] elems = BattleMageRules.chaserElements(c, teana);
                var dmg = SkillPreview.multiElementDamage(progress, stats, launcher, coeff, elems, effects);
                min += probs[c] * dmg.minimum();
                max += probs[c] * dmg.maximum();
            }
            return new SkillPreview.Damage(min * volley, max * volley);
        });

        // Palm Blast (full charge preview + Teana scale)
        var palm = SkillCatalog.require(PALM_BLAST);
        SkillPreviewRules.register(palm, new SkillPreviewRules.Definition(p -> 1, false, false, false, -1));
        SkillPreviewRules.pattern(palm, p -> new SkillPreviewRules.Pattern(1, p.get("damage", 0) * BattleMageRules.fullChargeMultiplier(p), 0));

        // Full Swing (phys + optional magicWave)
        var swing = SkillCatalog.require(FULL_SWING);
        SkillPreviewRules.register(swing, new SkillPreviewRules.Definition(p -> 1, false, false, false, -1));
        SkillPreviewRules.phases(swing, "phys", "");
        SkillPreviewRules.total(swing, (progress, stats, p, effects) -> {
            double teanaScale = stats.modifiers.getOrDefault("bmTeana", 0.0) > 0 ? stats.modifiers.getOrDefault("bmTeanaSkillScale", 1.30) : 1.0;
            var phys = SkillPreview.phaseDamage(progress, stats, swing, p.get("damage", 0) * teanaScale, "phys", effects);
            double waveCoeff = p.get("chaserSlash", 0);
            if (waveCoeff > 0) {
                var wave = SkillPreview.phaseDamage(progress, stats, swing, waveCoeff, "magicWave", effects);
                return phys.plus(wave);
            }
            return phys;
        });

        // Chaser Fusion
        var fusion = SkillCatalog.require(CHASER_FUSION);
        SkillPreviewRules.register(fusion, new SkillPreviewRules.Definition(p -> 1, false, false, true, -1));

        // Chaser Press (preview at full maxChasers)
        var press = SkillCatalog.require(CHASER_PRESS);
        SkillPreviewRules.register(press, new SkillPreviewRules.Definition(p -> 1, false, false, true, -1));
        SkillPreviewRules.total(press, (progress, stats, p, effects) -> {
            var lp = BattleMageRules.launcherParams(stats, progress);
            int maxCap = (int) Math.max(1, lp.get("maxChasers", 7.0));
            double coeff = p.get("damage", 0) + p.get("perChaserDamage", 0) * (maxCap - 1);
            boolean teana = stats.modifiers.getOrDefault("bmTeana", 0.0) > 0;
            int[] elems = teana ? BattleMageRules.ALL_FOUR_ELEMENTS : new int[0];
            return SkillPreview.multiElementDamage(progress, stats, press, coeff, elems, effects);
        });

        // Punto Wave
        var punto = SkillCatalog.require(PUNTO_WAVE);
        SkillPreviewRules.register(punto, new SkillPreviewRules.Definition(p -> (int) Math.max(1, p.get("slashes", 12)), true, false, false, -1));
        SkillPreviewRules.finisherLabel(punto, "终结横扫");

        // Dragon Strike
        var strike = SkillCatalog.require(DRAGON_STRIKE);
        SkillPreviewRules.register(strike, new SkillPreviewRules.Definition(p -> (int) Math.max(1, p.get("dashCount", 1)), true, false, false, -1));
        SkillPreviewRules.pattern(strike, p -> {
            double cm = BattleMageRules.fullChargeMultiplier(p);
            int dashes = (int) Math.max(1, p.get("dashCount", 1));
            return new SkillPreviewRules.Pattern(dashes, p.get("damage", 0) * cm, p.get("finisher", 0) * cm * dashes);
        });
        SkillPreviewRules.finisherLabel(strike, "流星爆发");

        // Dragon Spear (hybrid physical + magic)
        var spear = SkillCatalog.require(DRAGON_SPEAR);
        SkillPreviewRules.register(spear, new SkillPreviewRules.Definition(p -> 1 + (int) Math.max(0, p.get("dragonChaserCount", 8)), true, false, false, -1));
        SkillPreviewRules.phases(spear, "thrust", "finisherPhys");
        SkillPreviewRules.finisherLabel(spear, "煌龙终爆");
        SkillPreviewRules.total(spear, (progress, stats, p, effects) -> {
            var thrust = SkillPreview.phaseDamage(progress, stats, spear, p.get("thrust", 0), "thrust", effects);
            int balls = (int) Math.max(0, p.get("dragonChaserCount", 8));
            double ballCoeff = p.get("dragonChaser", 0);
            var ballsDmg = balls > 0 && ballCoeff > 0
                ? multiPhaseMagic(progress, stats, spear, ballCoeff, "dragonChaser", BattleMageRules.ALL_FOUR_ELEMENTS, effects).times(balls)
                : new SkillPreview.Damage(0, 0);
            var finPhys = p.get("finisherPhys", 0) > 0
                ? SkillPreview.phaseDamage(progress, stats, spear, p.get("finisherPhys", 0), "finisherPhys", effects)
                : new SkillPreview.Damage(0, 0);
            var finMag = p.get("finisherMagic", 0) > 0
                ? multiPhaseMagic(progress, stats, spear, p.get("finisherMagic", 0), "finisherMagic", BattleMageRules.ALL_FOUR_ELEMENTS, effects)
                : new SkillPreview.Damage(0, 0);
            return thrust.plus(ballsDmg).plus(finPhys).plus(finMag);
        });

        // Teana Transformation (hybrid physical + magic)
        var teana = SkillCatalog.require(TEANA_TRANSFORMATION);
        SkillPreviewRules.register(teana, new SkillPreviewRules.Definition(p -> 1, true, false, false, -1));
        SkillPreviewRules.phases(teana, "auraPhys", "finisherPhys");
        SkillPreviewRules.finisherLabel(teana, "使徒穿刺终结");
        SkillPreviewRules.total(teana, (progress, stats, p, effects) -> {
            var aPhys = SkillPreview.phaseDamage(progress, stats, teana, p.get("auraPhys", 0), "auraPhys", effects);
            var aMag = multiPhaseMagic(progress, stats, teana, p.get("auraMagic", 0), "auraMagic", BattleMageRules.ALL_FOUR_ELEMENTS, effects);
            var fPhys = SkillPreview.phaseDamage(progress, stats, teana, p.get("finisherPhys", 0), "finisherPhys", effects);
            var fMag = multiPhaseMagic(progress, stats, teana, p.get("finisherMagic", 0), "finisherMagic", BattleMageRules.ALL_FOUR_ELEMENTS, effects);
            return aPhys.plus(aMag).plus(fPhys).plus(fMag);
        });
    }

    private static SkillPreview.Damage multiPhaseMagic(cn.yumis.fight.modules.character.api.Progress progress,
                                                       cn.yumis.fight.modules.attributes.api.Stats stats,
                                                       SkillDefinition skill, double coeff, String phase,
                                                       int[] elements,
                                                       cn.yumis.fight.modules.equipment.api.EquipmentEffects.Snapshot effects) {
        var type = SkillDamagePhases.require(skill, phase);
        var copy = stats.copy();
        copy.modifiers.put("previewPhaseMagic", type.magic() ? 1d : 0d);
        return SkillPreview.multiElementDamage(progress, copy, skill, coeff, elements, effects);
    }

    public static void presentation() {
        for (String id : ALL) {
            boolean move = PALM_BLAST.equals(id) || DRAGON_STRIKE.equals(id);
            boolean superStiff = TEANA_TRANSFORMATION.equals(id) || DRAGON_SPEAR.equals(id);
            int cancel = move || FULL_SWING.equals(id) ? SkillPresentation.CANCEL_MOBILITY : SkillPresentation.CANCEL_NORMAL;
            int stiff = superStiff ? SkillPresentation.STIFF_SUPER : SkillPresentation.STIFF_NORMAL;
            int color = switch (id) {
                case CHASER_LAUNCHER, CHASER_FUSION, CHASER_PRESS -> BattleMageRules.COLOR_DARK;
                case TEANA_TRANSFORMATION -> BattleMageRules.COLOR_TEANA;
                case DRAGON_SPEAR -> 0xFFB833;
                case DRAGON_STRIKE -> 0xFF5A36;
                default -> BattleMageRules.COLOR_MARTIAL;
            };
            long defaultDuration = switch (id) {
                case CHASER_LAUNCHER -> 700L;
                case PALM_BLAST, FULL_SWING -> 900L;
                case CHASER_FUSION, CHASER_PRESS -> 1200L;
                case PUNTO_WAVE -> 1800L;
                case DRAGON_STRIKE -> 1500L;
                case DRAGON_SPEAR -> 2400L;
                case TEANA_TRANSFORMATION -> 2200L;
                default -> 1200L;
            };
            SkillPresentation.register(SkillCatalog.require(id),
                new SkillPresentation.Traits(false, false, true, false, 0, 0, color, false,
                    c -> Math.max(defaultDuration, (long) (c.parameters.get("duration", defaultDuration / 1000.0) * 1000) + 500L),
                    move, cancel, stiff));
        }
    }
}

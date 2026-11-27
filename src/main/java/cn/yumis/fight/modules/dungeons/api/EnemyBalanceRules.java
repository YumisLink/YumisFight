package cn.yumis.fight.modules.dungeons.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Dungeons-owned enemy global formulas, enemy panels, move tables, tower climb, abyss and practice rules. */
public final class EnemyBalanceRules {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public record DifficultyScaling(double health, double attack, double xp) {
        public DifficultyScaling {
            requirePositive(health, "difficulty.health");
            requirePositive(attack, "difficulty.attack");
            requireNonNegative(xp, "difficulty.xp");
        }
    }

    public record EnemyPanel(
        String title,
        double healthMultiplier,
        double attackMultiplier,
        double physicalDefenseBase,
        double magicalDefenseBase,
        double[] resists
    ) {
        public EnemyPanel {
            requirePositive(healthMultiplier, "enemy.healthMultiplier");
            requireNonNegative(attackMultiplier, "enemy.attackMultiplier");
            if (!Double.isFinite(physicalDefenseBase) || !Double.isFinite(magicalDefenseBase))
                throw new IllegalArgumentException("enemy defense base must be finite");
            if (resists != null) {
                if (resists.length != 4) throw new IllegalArgumentException("enemy resists must have length 4");
                resists = resists.clone();
            }
        }
        @Override public double[] resists() { return resists == null ? null : resists.clone(); }
    }

    public record ClassicMoveData(
        String title, long duration, long cooldown, String shape, double range, double width,
        double coefficient, boolean magic, int element, String effect, int stunTicks, double[] contacts
    ) {
        public ClassicMoveData {
            if (duration <= 0 || cooldown < duration || range < 0 || width <= 0 || coefficient < 0 || contacts == null || contacts.length == 0)
                throw new IllegalArgumentException("Invalid classic move data: " + title);
            contacts = contacts.clone();
        }
        @Override public double[] contacts() { return contacts.clone(); }
    }

    public record TimedMoveData(
        String animation, String shape, long windup, long active, long recovery, long duration, long cooldown,
        double range, double coefficient, boolean magic, int element, int hits, long interval
    ) {
        public TimedMoveData {
            if (windup < 0 || active < 0 || recovery < 0 || duration < 0 || cooldown < 0 || range < 0 || coefficient < 0 || hits < 1 || interval < 0)
                throw new IllegalArgumentException("Invalid timed move data");
        }
        public long totalDuration() { return duration > 0 ? duration : windup + active + recovery; }
    }

    public record CastleRules(
        int knightLevel, int karlLevel, int phantomSwordWindupTicks, int phantomSwordCooldownTicks,
        double phantomSwordCastRange, int phantomSwordFlightTicks, double phantomSwordSpeed
    ) {
        public CastleRules {
            if (knightLevel < 1 || karlLevel < 1 || phantomSwordWindupTicks < 0 || phantomSwordCooldownTicks < 0
                || phantomSwordCastRange <= 0 || phantomSwordFlightTicks < 1 || phantomSwordSpeed <= 0)
                throw new IllegalArgumentException("Invalid castle rules");
        }
    }

    public record TowerClimbData(
        int floorCount, int enemyLevelOffset, double orreryHealthFactor, double orreryAttackFactor,
        double movementFactor, double fireHealthFraction, double actionSpeedFactor,
        double manaRecoveryFactor, double frostDamageFraction, double cooldownRecoveryFactor,
        Map<String, Double> grades
    ) {
        public TowerClimbData {
            if (floorCount < 1) throw new IllegalArgumentException("tower.floorCount must be >= 1");
            requirePositive(orreryHealthFactor, "tower.orreryHealthFactor");
            requirePositive(orreryAttackFactor, "tower.orreryAttackFactor");
            requirePositive(movementFactor, "tower.movementFactor");
            requireNonNegative(fireHealthFraction, "tower.fireHealthFraction");
            requirePositive(actionSpeedFactor, "tower.actionSpeedFactor");
            requireNonNegative(manaRecoveryFactor, "tower.manaRecoveryFactor");
            requireNonNegative(frostDamageFraction, "tower.frostDamageFraction");
            requirePositive(cooldownRecoveryFactor, "tower.cooldownRecoveryFactor");
            grades = Map.copyOf(Objects.requireNonNull(grades, "tower.grades"));
        }
        public double gradeMultiplier(String name, double fallback) {
            return grades.getOrDefault(name, fallback);
        }
    }

    public record AbyssData(
        double chance, double firstFloor, double secondFloor, List<Double> skeletonThresholds,
        int secondWave, int champions, double radius, long castGapMs,
        double giantBasicCoefficient, double skeletonBasicCoefficient, double championBasicCoefficient,
        double championSkillEnhanceMultiplier, Map<String, EnemyPanel> enemies
    ) {
        public AbyssData {
            requireNonNegative(chance, "abyss.chance");
            requireNonNegative(firstFloor, "abyss.firstFloor");
            requireNonNegative(secondFloor, "abyss.secondFloor");
            skeletonThresholds = List.copyOf(Objects.requireNonNull(skeletonThresholds));
            if (secondWave < 0 || champions < 0 || radius <= 0 || castGapMs < 0)
                throw new IllegalArgumentException("Invalid abyss wave/radius/castGap");
            requireNonNegative(giantBasicCoefficient, "abyss.giantBasicCoefficient");
            requireNonNegative(skeletonBasicCoefficient, "abyss.skeletonBasicCoefficient");
            requireNonNegative(championBasicCoefficient, "abyss.championBasicCoefficient");
            requireNonNegative(championSkillEnhanceMultiplier, "abyss.championSkillEnhanceMultiplier");
            enemies = Map.copyOf(Objects.requireNonNull(enemies));
        }
        public double healthMultiplier(String enemyId, double fallback) {
            var p = enemies.get(enemyId);
            return p == null ? fallback : p.healthMultiplier();
        }
    }

    public record PracticeData(
        int waveSize, double mobEliteChance, double mobHealthMultiplier, double mobExperienceMultiplier
    ) {
        public PracticeData {
            if (waveSize < 1) throw new IllegalArgumentException("practice.waveSize must be >= 1");
            requireNonNegative(mobEliteChance, "practice.mobEliteChance");
            requirePositive(mobHealthMultiplier, "practice.mobHealthMultiplier");
            requireNonNegative(mobExperienceMultiplier, "practice.mobExperienceMultiplier");
        }
    }

    public record GlobalScaling(
        double normalHealthMultiplier, double normalAttackMultiplier,
        double eliteHealthMultiplier, double eliteAttackMultiplier,
        double bossHealthMultiplier, double bossAttackMultiplier,
        double baseNormalHealth, double baseWitherHealth, double baseBossHealth, double baseTrainingHealth,
        double healthGrowthBase,
        double physicalDefenseBase, double physicalDefensePerLevel,
        double magicalDefenseBase, double magicalDefensePerLevel,
        double normalAttackBase, double normalAttackPerLevel,
        double bossAttackBase, double bossAttackPerLevel,
        double witherAttackBase, double witherAttackPerLevel,
        double dragonAttackBase, double dragonAttackPerLevel,
        double attackScaleMin, double attackScaleMax, int attackScaleMaxLevel,
        double eliteBaseHealthFactor, double eliteBaseAttackFactor,
        double elitePowerStrongHealth, double elitePowerRageAttack,
        double elitePowerArmorDefenseAdd, double elitePowerElementalResistAdd,
        Map<String, DifficultyScaling> difficulties,
        double[] partyScaling, double partyScalingPerExtra
    ) {
        public GlobalScaling {
            requirePositive(normalHealthMultiplier, "enemies.normalHealthMultiplier");
            requireNonNegative(normalAttackMultiplier, "enemies.normalAttackMultiplier");
            requirePositive(eliteHealthMultiplier, "enemies.eliteHealthMultiplier");
            requireNonNegative(eliteAttackMultiplier, "enemies.eliteAttackMultiplier");
            requirePositive(bossHealthMultiplier, "enemies.bossHealthMultiplier");
            requireNonNegative(bossAttackMultiplier, "enemies.bossAttackMultiplier");
            requirePositive(baseNormalHealth, "enemies.baseNormalHealth");
            requirePositive(baseWitherHealth, "enemies.baseWitherHealth");
            requirePositive(baseBossHealth, "enemies.baseBossHealth");
            requirePositive(baseTrainingHealth, "enemies.baseTrainingHealth");
            requirePositive(healthGrowthBase, "enemies.healthGrowthBase");
            requireNonNegative(physicalDefenseBase, "enemies.physicalDefenseBase");
            requireNonNegative(physicalDefensePerLevel, "enemies.physicalDefensePerLevel");
            requireNonNegative(magicalDefenseBase, "enemies.magicalDefenseBase");
            requireNonNegative(magicalDefensePerLevel, "enemies.magicalDefensePerLevel");
            requireNonNegative(attackScaleMin, "enemies.attackScaleMin");
            requireNonNegative(attackScaleMax, "enemies.attackScaleMax");
            if (attackScaleMaxLevel < 2) throw new IllegalArgumentException("enemies.attackScaleMaxLevel must be >= 2");
            requirePositive(eliteBaseHealthFactor, "enemies.eliteBaseHealthFactor");
            requireNonNegative(eliteBaseAttackFactor, "enemies.eliteBaseAttackFactor");
            requirePositive(elitePowerStrongHealth, "enemies.elitePowerStrongHealth");
            requireNonNegative(elitePowerRageAttack, "enemies.elitePowerRageAttack");
            requireNonNegative(elitePowerArmorDefenseAdd, "enemies.elitePowerArmorDefenseAdd");
            if (partyScaling == null || partyScaling.length == 0) throw new IllegalArgumentException("enemies.partyScaling must not be empty");
            partyScaling = partyScaling.clone();
            difficulties = Map.copyOf(Objects.requireNonNull(difficulties, "enemies.difficulties"));
        }
        @Override public double[] partyScaling() { return partyScaling.clone(); }

        public DifficultyScaling difficulty(Difficulty d) {
            DifficultyScaling s = difficulties.get(d.name());
            return s != null ? s : new DifficultyScaling(d.health, d.attack, d.xp);
        }

        public double partyMultiplier(int n) {
            int idx = Math.max(1, n);
            if (idx <= partyScaling.length) return partyScaling[idx - 1];
            return partyScaling[partyScaling.length - 1] + partyScalingPerExtra * (idx - partyScaling.length);
        }
    }

    public final GlobalScaling global;
    public final Map<String, EnemyPanel> enemies;
    public final Map<String, ClassicMoveData> classicMoves;
    public final Map<String, TimedMoveData> darkCityMoves;
    public final double darkCityGuardDefenseIncrease;
    public final Map<String, TimedMoveData> forbiddenMoves;
    public final long forbiddenBarrierLifetimeMs;
    public final double forbiddenCoreHealth;
    public final int forbiddenSummonLimit;
    public final Map<String, TimedMoveData> skasaMoves;
    public final Map<String, TimedMoveData> gblMoves;
    public final Map<String, TimedMoveData> palaceMoves;
    public final CastleRules castle;
    public final TowerClimbData towerClimb;
    public final AbyssData abyss;
    public final PracticeData practice;

    public EnemyBalanceRules(
        GlobalScaling global,
        Map<String, EnemyPanel> enemies,
        Map<String, ClassicMoveData> classicMoves,
        Map<String, TimedMoveData> darkCityMoves,
        double darkCityGuardDefenseIncrease,
        Map<String, TimedMoveData> forbiddenMoves,
        long forbiddenBarrierLifetimeMs,
        double forbiddenCoreHealth,
        int forbiddenSummonLimit,
        Map<String, TimedMoveData> skasaMoves,
        Map<String, TimedMoveData> gblMoves,
        Map<String, TimedMoveData> palaceMoves,
        CastleRules castle,
        TowerClimbData towerClimb,
        AbyssData abyss,
        PracticeData practice
    ) {
        this.global = Objects.requireNonNull(global);
        this.enemies = Map.copyOf(Objects.requireNonNull(enemies));
        this.classicMoves = Map.copyOf(Objects.requireNonNull(classicMoves));
        this.darkCityMoves = Map.copyOf(Objects.requireNonNull(darkCityMoves));
        this.darkCityGuardDefenseIncrease = darkCityGuardDefenseIncrease;
        this.forbiddenMoves = Map.copyOf(Objects.requireNonNull(forbiddenMoves));
        this.forbiddenBarrierLifetimeMs = forbiddenBarrierLifetimeMs;
        this.forbiddenCoreHealth = forbiddenCoreHealth;
        this.forbiddenSummonLimit = forbiddenSummonLimit;
        this.skasaMoves = Map.copyOf(Objects.requireNonNull(skasaMoves));
        this.gblMoves = Map.copyOf(Objects.requireNonNull(gblMoves));
        this.palaceMoves = Map.copyOf(Objects.requireNonNull(palaceMoves));
        this.castle = Objects.requireNonNull(castle);
        this.towerClimb = Objects.requireNonNull(towerClimb);
        this.abyss = Objects.requireNonNull(abyss);
        this.practice = Objects.requireNonNull(practice);
    }

    private static volatile EnemyBalanceRules DEFAULT;
    private static volatile Supplier<EnemyBalanceRules> source;

    public static EnemyBalanceRules defaults() {
        EnemyBalanceRules v = DEFAULT;
        if (v == null) {
            synchronized (EnemyBalanceRules.class) {
                v = DEFAULT;
                if (v == null) DEFAULT = v = loadDefaults();
            }
        }
        return v;
    }

    public static EnemyBalanceRules current() {
        var provider = source;
        return provider == null ? defaults() : Objects.requireNonNull(provider.get());
    }

    public static void source(Supplier<EnemyBalanceRules> provider) {
        source = Objects.requireNonNull(provider);
    }

    public EnemyPanel panel(String enemyId) { return enemies.get(enemyId); }
    public ClassicMoveData classicMove(String moveId) { return classicMoves.get(moveId); }
    public TimedMoveData darkCityMove(String name) { return darkCityMoves.get(name); }
    public TimedMoveData forbiddenMove(String name) { return forbiddenMoves.get(name); }
    public TimedMoveData skasaMove(String name) { return skasaMoves.get(name); }
    public TimedMoveData gblMove(String name) { return gblMoves.get(name); }
    public TimedMoveData palaceMove(String name) { return palaceMoves.get(name); }

    public void validate() {
        for (Difficulty d : Difficulty.values()) {
            if (!global.difficulties.containsKey(d.name()))
                throw new IllegalArgumentException("master.json 缺少难度配置：" + d.name());
        }
        for (ClassicEnemyKind k : ClassicEnemyKind.values()) {
            if (!enemies.containsKey(k.id)) throw new IllegalArgumentException("classic_dungeons.json 缺少敌人面板：" + k.id);
        }
        for (DarkCityMoves m : DarkCityMoves.values()) {
            if (!darkCityMoves.containsKey(m.name())) throw new IllegalArgumentException("early_dungeons.json 缺少暗黑城招式：" + m.name());
        }
        for (ForbiddenMoves m : ForbiddenMoves.values()) {
            if (!forbiddenMoves.containsKey(m.name())) throw new IllegalArgumentException("early_dungeons.json 缺少天帷禁地招式：" + m.name());
        }
        for (SkasaMoves m : SkasaMoves.values()) {
            if (!skasaMoves.containsKey(m.name())) throw new IllegalArgumentException("early_dungeons.json 缺少冰龙招式：" + m.name());
        }
    }

    private static EnemyBalanceRules loadDefaults() {
        try {
            return parseAll(
                readResource("/data/yumis_dungeon_and_fight/rules/balance/master.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/enemies/early_dungeons.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/enemies/classic_dungeons.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/enemies/tower_climb.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/enemies/abyss.json"),
                readResource("/data/yumis_dungeon_and_fight/rules/balance/enemies/practice.json")
            );
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readResource(String path) throws IOException {
        try (var in = EnemyBalanceRules.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static EnemyBalanceRules parseAll(String masterJson, String earlyJson, String classicJson, String towerJson, String abyssJson, String practiceJson) {
        JsonObject master = JsonParser.parseString(masterJson).getAsJsonObject();
        GlobalScaling global = parseGlobal(master.getAsJsonObject("enemies"));

        JsonObject early = JsonParser.parseString(earlyJson).getAsJsonObject();
        JsonObject classic = JsonParser.parseString(classicJson).getAsJsonObject();
        JsonObject tower = JsonParser.parseString(towerJson).getAsJsonObject();
        JsonObject abyssObj = JsonParser.parseString(abyssJson).getAsJsonObject();
        JsonObject practiceObj = JsonParser.parseString(practiceJson).getAsJsonObject();

        Map<String, EnemyPanel> allEnemies = new LinkedHashMap<>();
        readPanels(early.getAsJsonObject("enemies"), allEnemies);
        readPanels(classic.getAsJsonObject("enemies"), allEnemies);

        Map<String, EnemyPanel> abyssPanels = new LinkedHashMap<>();
        readPanels(abyssObj.getAsJsonObject("enemies"), abyssPanels);
        allEnemies.putAll(abyssPanels);

        Map<String, ClassicMoveData> classicMoves = new LinkedHashMap<>();
        JsonObject classicMoveGroups = classic.getAsJsonObject("moves");
        for (var actorEntry : classicMoveGroups.entrySet()) {
            String actorId = actorEntry.getKey();
            for (var moveEntry : actorEntry.getValue().getAsJsonObject().entrySet()) {
                classicMoves.put(actorId + "/" + moveEntry.getKey(), parseClassicMove(moveEntry.getValue().getAsJsonObject()));
            }
        }

        Map<String, TimedMoveData> darkCityMoves = parseTimedMoves(early.getAsJsonObject("darkCityMoves"));
        double darkCityGuardDef = early.get("darkCityGuardDefenseIncrease").getAsDouble();
        Map<String, TimedMoveData> forbiddenMoves = parseTimedMoves(early.getAsJsonObject("forbiddenMoves"));
        long forbiddenBarrierLifetime = early.get("forbiddenBarrierLifetimeMs").getAsLong();
        double forbiddenCoreHealth = early.get("forbiddenCoreHealth").getAsDouble();
        int forbiddenSummonLimit = early.get("forbiddenSummonLimit").getAsInt();
        Map<String, TimedMoveData> skasaMoves = parseTimedMoves(early.getAsJsonObject("skasaMoves"));
        Map<String, TimedMoveData> gblMoves = parseTimedMoves(early.getAsJsonObject("gblMoves"));
        Map<String, TimedMoveData> palaceMoves = parseTimedMoves(early.getAsJsonObject("palaceMoves"));
        CastleRules castle = JSON.fromJson(early.getAsJsonObject("castle"), CastleRules.class);

        Map<String, Double> grades = new LinkedHashMap<>();
        for (var e : tower.getAsJsonObject("grades").entrySet()) grades.put(e.getKey(), e.getValue().getAsDouble());
        TowerClimbData towerClimb = new TowerClimbData(
            tower.get("floorCount").getAsInt(),
            tower.get("enemyLevelOffset").getAsInt(),
            tower.get("orreryHealthFactor").getAsDouble(),
            tower.get("orreryAttackFactor").getAsDouble(),
            tower.get("movementFactor").getAsDouble(),
            tower.get("fireHealthFraction").getAsDouble(),
            tower.get("actionSpeedFactor").getAsDouble(),
            tower.get("manaRecoveryFactor").getAsDouble(),
            tower.get("frostDamageFraction").getAsDouble(),
            tower.get("cooldownRecoveryFactor").getAsDouble(),
            grades
        );

        List<Double> skelThresholds = new ArrayList<>();
        for (JsonElement el : abyssObj.getAsJsonArray("skeletonThresholds")) skelThresholds.add(el.getAsDouble());
        AbyssData abyss = new AbyssData(
            abyssObj.get("chance").getAsDouble(),
            abyssObj.get("firstFloor").getAsDouble(),
            abyssObj.get("secondFloor").getAsDouble(),
            skelThresholds,
            abyssObj.get("secondWave").getAsInt(),
            abyssObj.get("champions").getAsInt(),
            abyssObj.get("radius").getAsDouble(),
            abyssObj.get("castGapMs").getAsLong(),
            abyssObj.get("giantBasicCoefficient").getAsDouble(),
            abyssObj.get("skeletonBasicCoefficient").getAsDouble(),
            abyssObj.get("championBasicCoefficient").getAsDouble(),
            abyssObj.get("championSkillEnhanceMultiplier").getAsDouble(),
            abyssPanels
        );

        PracticeData practice = JSON.fromJson(practiceObj, PracticeData.class);
        return new EnemyBalanceRules(global, allEnemies, classicMoves, darkCityMoves, darkCityGuardDef,
            forbiddenMoves, forbiddenBarrierLifetime, forbiddenCoreHealth, forbiddenSummonLimit,
            skasaMoves, gblMoves, palaceMoves, castle, towerClimb, abyss, practice);
    }

    public static EnemyBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        GlobalScaling global = parseGlobal(root.getAsJsonObject("global"));
        Map<String, EnemyPanel> allEnemies = new LinkedHashMap<>();
        readPanels(root.getAsJsonObject("enemies"), allEnemies);
        Map<String, ClassicMoveData> classicMoves = new LinkedHashMap<>();
        for (var e : root.getAsJsonObject("classicMoves").entrySet()) {
            classicMoves.put(e.getKey(), parseClassicMove(e.getValue().getAsJsonObject()));
        }
        Map<String, TimedMoveData> darkCityMoves = parseTimedMoves(root.getAsJsonObject("darkCityMoves"));
        double darkCityGuardDef = root.get("darkCityGuardDefenseIncrease").getAsDouble();
        Map<String, TimedMoveData> forbiddenMoves = parseTimedMoves(root.getAsJsonObject("forbiddenMoves"));
        long forbiddenBarrierLifetime = root.get("forbiddenBarrierLifetimeMs").getAsLong();
        double forbiddenCoreHealth = root.get("forbiddenCoreHealth").getAsDouble();
        int forbiddenSummonLimit = root.get("forbiddenSummonLimit").getAsInt();
        Map<String, TimedMoveData> skasaMoves = parseTimedMoves(root.getAsJsonObject("skasaMoves"));
        Map<String, TimedMoveData> gblMoves = parseTimedMoves(root.getAsJsonObject("gblMoves"));
        Map<String, TimedMoveData> palaceMoves = parseTimedMoves(root.getAsJsonObject("palaceMoves"));
        CastleRules castle = JSON.fromJson(root.getAsJsonObject("castle"), CastleRules.class);
        TowerClimbData towerClimb = JSON.fromJson(root.getAsJsonObject("towerClimb"), TowerClimbData.class);
        JsonObject abyssObj = root.getAsJsonObject("abyss");
        List<Double> skelThresholds = new ArrayList<>();
        for (JsonElement el : abyssObj.getAsJsonArray("skeletonThresholds")) skelThresholds.add(el.getAsDouble());
        Map<String, EnemyPanel> abyssPanels = new LinkedHashMap<>();
        readPanels(abyssObj.getAsJsonObject("enemies"), abyssPanels);
        AbyssData abyss = new AbyssData(
            abyssObj.get("chance").getAsDouble(),
            abyssObj.get("firstFloor").getAsDouble(),
            abyssObj.get("secondFloor").getAsDouble(),
            skelThresholds,
            abyssObj.get("secondWave").getAsInt(),
            abyssObj.get("champions").getAsInt(),
            abyssObj.get("radius").getAsDouble(),
            abyssObj.get("castGapMs").getAsLong(),
            abyssObj.get("giantBasicCoefficient").getAsDouble(),
            abyssObj.get("skeletonBasicCoefficient").getAsDouble(),
            abyssObj.get("championBasicCoefficient").getAsDouble(),
            abyssObj.get("championSkillEnhanceMultiplier").getAsDouble(),
            abyssPanels
        );
        PracticeData practice = JSON.fromJson(root.getAsJsonObject("practice"), PracticeData.class);
        return new EnemyBalanceRules(global, allEnemies, classicMoves, darkCityMoves, darkCityGuardDef,
            forbiddenMoves, forbiddenBarrierLifetime, forbiddenCoreHealth, forbiddenSummonLimit,
            skasaMoves, gblMoves, palaceMoves, castle, towerClimb, abyss, practice);
    }

    private static GlobalScaling parseGlobal(JsonObject e) {
        Map<String, DifficultyScaling> diffs = new LinkedHashMap<>();
        for (var entry : e.getAsJsonObject("difficulties").entrySet()) {
            diffs.put(entry.getKey(), JSON.fromJson(entry.getValue(), DifficultyScaling.class));
        }
        JsonArray partyArr = e.getAsJsonArray("partyScaling");
        double[] party = new double[partyArr.size()];
        for (int i = 0; i < party.length; i++) party[i] = partyArr.get(i).getAsDouble();
        return new GlobalScaling(
            e.get("normalHealthMultiplier").getAsDouble(),
            e.get("normalAttackMultiplier").getAsDouble(),
            e.get("eliteHealthMultiplier").getAsDouble(),
            e.get("eliteAttackMultiplier").getAsDouble(),
            e.get("bossHealthMultiplier").getAsDouble(),
            e.get("bossAttackMultiplier").getAsDouble(),
            e.get("baseNormalHealth").getAsDouble(),
            e.get("baseWitherHealth").getAsDouble(),
            e.get("baseBossHealth").getAsDouble(),
            e.get("baseTrainingHealth").getAsDouble(),
            e.get("healthGrowthBase").getAsDouble(),
            e.get("physicalDefenseBase").getAsDouble(),
            e.get("physicalDefensePerLevel").getAsDouble(),
            e.get("magicalDefenseBase").getAsDouble(),
            e.get("magicalDefensePerLevel").getAsDouble(),
            e.get("normalAttackBase").getAsDouble(),
            e.get("normalAttackPerLevel").getAsDouble(),
            e.get("bossAttackBase").getAsDouble(),
            e.get("bossAttackPerLevel").getAsDouble(),
            e.get("witherAttackBase").getAsDouble(),
            e.get("witherAttackPerLevel").getAsDouble(),
            e.get("dragonAttackBase").getAsDouble(),
            e.get("dragonAttackPerLevel").getAsDouble(),
            e.get("attackScaleMin").getAsDouble(),
            e.get("attackScaleMax").getAsDouble(),
            e.get("attackScaleMaxLevel").getAsInt(),
            e.get("eliteBaseHealthFactor").getAsDouble(),
            e.get("eliteBaseAttackFactor").getAsDouble(),
            e.get("elitePowerStrongHealth").getAsDouble(),
            e.get("elitePowerRageAttack").getAsDouble(),
            e.get("elitePowerArmorDefenseAdd").getAsDouble(),
            e.get("elitePowerElementalResistAdd").getAsDouble(),
            diffs,
            party,
            e.get("partyScalingPerExtra").getAsDouble()
        );
    }

    private static void readPanels(JsonObject obj, Map<String, EnemyPanel> out) {
        if (obj == null) return;
        for (var entry : obj.entrySet()) {
            JsonObject o = entry.getValue().getAsJsonObject();
            String title = o.has("title") ? o.get("title").getAsString() : entry.getKey();
            double hp = o.has("healthMultiplier") ? o.get("healthMultiplier").getAsDouble() : 1.0;
            double atk = o.has("attackMultiplier") ? o.get("attackMultiplier").getAsDouble() : 1.0;
            double pDef = o.has("physicalDefenseBase") ? o.get("physicalDefenseBase").getAsDouble() : -1.0;
            double mDef = o.has("magicalDefenseBase") ? o.get("magicalDefenseBase").getAsDouble() : -1.0;
            double[] resists = null;
            if (o.has("resists") && o.get("resists").isJsonArray()) {
                JsonArray arr = o.getAsJsonArray("resists");
                resists = new double[arr.size()];
                for (int i = 0; i < resists.length; i++) resists[i] = arr.get(i).getAsDouble();
            }
            out.put(entry.getKey(), new EnemyPanel(title, hp, atk, pDef, mDef, resists));
        }
    }

    private static ClassicMoveData parseClassicMove(JsonObject o) {
        JsonArray arr = o.getAsJsonArray("contacts");
        double[] contacts = new double[arr.size()];
        for (int i = 0; i < contacts.length; i++) contacts[i] = arr.get(i).getAsDouble();
        return new ClassicMoveData(
            o.get("title").getAsString(),
            o.get("duration").getAsLong(),
            o.get("cooldown").getAsLong(),
            o.get("shape").getAsString(),
            o.get("range").getAsDouble(),
            o.get("width").getAsDouble(),
            o.get("coefficient").getAsDouble(),
            o.get("magic").getAsBoolean(),
            o.get("element").getAsInt(),
            o.has("effect") ? o.get("effect").getAsString() : "",
            o.has("stunTicks") ? o.get("stunTicks").getAsInt() : 0,
            contacts
        );
    }

    private static Map<String, TimedMoveData> parseTimedMoves(JsonObject obj) {
        Map<String, TimedMoveData> out = new LinkedHashMap<>();
        if (obj == null) return out;
        for (var e : obj.entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            out.put(e.getKey(), new TimedMoveData(
                o.has("animation") ? o.get("animation").getAsString() : "",
                o.has("shape") ? o.get("shape").getAsString() : "",
                o.has("windup") ? o.get("windup").getAsLong() : 0L,
                o.has("active") ? o.get("active").getAsLong() : 0L,
                o.has("recovery") ? o.get("recovery").getAsLong() : 0L,
                o.has("duration") ? o.get("duration").getAsLong() : 0L,
                o.has("cooldown") ? o.get("cooldown").getAsLong() : 0L,
                o.has("range") ? o.get("range").getAsDouble() : 0d,
                o.has("coefficient") ? o.get("coefficient").getAsDouble() : 0d,
                o.has("magic") && o.get("magic").getAsBoolean(),
                o.has("element") ? o.get("element").getAsInt() : -1,
                o.has("hits") ? o.get("hits").getAsInt() : 1,
                o.has("interval") ? o.get("interval").getAsLong() : 0L
            ));
        }
        return out;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}

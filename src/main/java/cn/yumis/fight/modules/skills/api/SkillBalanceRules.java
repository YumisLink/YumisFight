package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Skill-owned direct-coefficient rules, sourced from rules/balance/master.json, skills/*.json and player.json. */
public final class SkillBalanceRules {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public static final List<String> SKILL_GROUPS = List.of(
        "common", "swordmaster", "soul_bender", "asura", "berserker", "oracle",
        "mage", "summoner", "sun_moon", "elementalist", "battle_mage", "witch", "demon_hunter", "abyss_champions"
    );

    public record SkillEntry(
        String title,
        int unlock,
        int sp,
        double mpRatio,
        double cooldown,
        double cooldownPerRank,
        Double rankGrowth,
        boolean damaging,
        Map<String, Double> damage,
        Map<String, Double> parameters
    ) {
        public SkillEntry {
            if (unlock < 1 || sp < 1) throw new IllegalArgumentException("Invalid unlock/sp for skill: " + title);
            requireNonNegative(mpRatio, "mpRatio");
            requireNonNegative(cooldown, "cooldown");
            requireNonNegative(cooldownPerRank, "cooldownPerRank");
            if (rankGrowth != null) requirePositive(rankGrowth, "rankGrowth");
            Map<String, Double> dmg = new LinkedHashMap<>();
            if (damage != null) {
                for (var e : damage.entrySet()) {
                    requireNonNegative(e.getValue(), "damage." + e.getKey());
                    dmg.put(e.getKey(), e.getValue());
                }
            }
            Map<String, Double> params = new LinkedHashMap<>();
            if (parameters != null) {
                for (var e : parameters.entrySet()) {
                    requireNonNegative(e.getValue(), "parameters." + e.getKey());
                    params.put(e.getKey(), e.getValue());
                }
            }
            damage = Map.copyOf(dmg);
            parameters = Map.copyOf(params);
        }

        public double effectiveRankGrowth(double defaultGrowth) {
            return rankGrowth == null ? defaultGrowth : rankGrowth;
        }
    }

    public record BasicAttackEntry(
        String defaultWeapon,
        double defaultScale,
        Map<String, Double> weapons,
        Map<String, Double> frenzyWeapons
    ) {
        public BasicAttackEntry {
            Objects.requireNonNull(defaultWeapon, "defaultWeapon");
            requireNonNegative(defaultScale, "defaultScale");
            Map<String, Double> w = new LinkedHashMap<>();
            if (weapons != null) {
                for (var e : weapons.entrySet()) {
                    requireNonNegative(e.getValue(), "weapons." + e.getKey());
                    w.put(e.getKey(), e.getValue());
                }
            }
            Map<String, Double> fw = new LinkedHashMap<>();
            if (frenzyWeapons != null) {
                for (var e : frenzyWeapons.entrySet()) {
                    requireNonNegative(e.getValue(), "frenzyWeapons." + e.getKey());
                    fw.put(e.getKey(), e.getValue());
                }
            }
            weapons = Map.copyOf(w);
            frenzyWeapons = Map.copyOf(fw);
        }

        public double scale(String weapon, boolean frenzy) {
            String key = weapon == null || weapon.isEmpty() ? defaultWeapon : weapon;
            if (frenzy && frenzyWeapons.containsKey(key)) return frenzyWeapons.get(key);
            if (weapons.containsKey(key)) return weapons.get(key);
            return defaultScale;
        }
    }

    public final double globalSkillDamageMultiplier;
    public final double globalBasicAttackMultiplier;
    public final double defaultRankDamageGrowth;
    public final double basicAttackRankGrowth;
    public final double defaultRankMpGrowth;
    public final double mpUnlockBase;
    public final double mpUnlockPerLevel;
    public final Map<String, SkillEntry> skills;
    public final Map<String, BasicAttackEntry> basicAttacks;

    public SkillBalanceRules(
        double globalSkillDamageMultiplier,
        double globalBasicAttackMultiplier,
        double defaultRankDamageGrowth,
        double basicAttackRankGrowth,
        double defaultRankMpGrowth,
        double mpUnlockBase,
        double mpUnlockPerLevel,
        Map<String, SkillEntry> skills,
        Map<String, BasicAttackEntry> basicAttacks
    ) {
        requireNonNegative(globalSkillDamageMultiplier, "skills.globalSkillDamageMultiplier");
        requireNonNegative(globalBasicAttackMultiplier, "skills.globalBasicAttackMultiplier");
        requirePositive(defaultRankDamageGrowth, "skills.defaultRankDamageGrowth");
        requirePositive(basicAttackRankGrowth, "skills.basicAttackRankGrowth");
        requirePositive(defaultRankMpGrowth, "skills.defaultRankMpGrowth");
        requireNonNegative(mpUnlockBase, "skills.mpUnlockBase");
        requireNonNegative(mpUnlockPerLevel, "skills.mpUnlockPerLevel");
        this.globalSkillDamageMultiplier = globalSkillDamageMultiplier;
        this.globalBasicAttackMultiplier = globalBasicAttackMultiplier;
        this.defaultRankDamageGrowth = defaultRankDamageGrowth;
        this.basicAttackRankGrowth = basicAttackRankGrowth;
        this.defaultRankMpGrowth = defaultRankMpGrowth;
        this.mpUnlockBase = mpUnlockBase;
        this.mpUnlockPerLevel = mpUnlockPerLevel;
        this.skills = Map.copyOf(Objects.requireNonNull(skills, "skills"));
        this.basicAttacks = Map.copyOf(Objects.requireNonNull(basicAttacks, "basicAttacks"));
    }

    private static volatile SkillBalanceRules DEFAULT;
    private static volatile Supplier<SkillBalanceRules> source;

    public static SkillBalanceRules defaults() {
        SkillBalanceRules value = DEFAULT;
        if (value == null) {
            synchronized (SkillBalanceRules.class) {
                value = DEFAULT;
                if (value == null) DEFAULT = value = loadDefaults();
            }
        }
        return value;
    }

    public static SkillBalanceRules current() {
        var provider = source;
        return provider == null ? defaults() : Objects.requireNonNull(provider.get());
    }

    public static void source(Supplier<SkillBalanceRules> provider) {
        source = Objects.requireNonNull(provider);
    }

    public SkillEntry skill(String id) {
        return skills.get(id);
    }

    public boolean damaging(SkillDefinition skill) {
        SkillEntry entry = skills.get(skill.id);
        return entry != null && entry.damaging();
    }

    public double rankDamageGrowth(SkillDefinition skill) {
        SkillEntry entry = skills.get(skill.id);
        return entry == null ? defaultRankDamageGrowth : entry.effectiveRankGrowth(defaultRankDamageGrowth);
    }

    /** Direct-coefficient parameter snapshot for the given skill and rank. */
    public Map<String, Double> parameters(SkillDefinition skill, int rank) {
        SkillEntry entry = skills.get(skill.id);
        if (entry == null) return skill.rawParameters(rank);
        var values = new LinkedHashMap<String, Double>(entry.parameters());
        double growth = Math.pow(entry.effectiveRankGrowth(defaultRankDamageGrowth), Math.max(0, rank - 1));
        for (var e : entry.damage().entrySet()) {
            values.put(e.getKey(), e.getValue() * growth * globalSkillDamageMultiplier);
        }
        values.put("balanceScale", globalSkillDamageMultiplier);
        return values;
    }

    public double basicScale(String jobId, String weapon, boolean frenzy) {
        return basicScale(jobId, weapon, frenzy, 1);
    }

    public double basicScale(String jobId, String weapon, boolean frenzy, int level) {
        BasicAttackEntry entry = basicAttacks.get(jobId);
        if (entry == null) entry = basicAttacks.get(JobCatalog.NONE);
        double base = entry == null ? 1.0 : entry.scale(weapon, frenzy);
        double growth = Math.pow(basicAttackRankGrowth, Math.max(0, level - 1) / 5.0);
        return base * growth * globalBasicAttackMultiplier;
    }

    public String defaultWeapon(String jobId) {
        BasicAttackEntry entry = basicAttacks.get(jobId);
        if (entry == null) entry = basicAttacks.get(JobCatalog.NONE);
        return entry == null ? "KATANA" : entry.defaultWeapon();
    }

    public void validate() {
        for (SkillDefinition skill : SkillCatalog.all()) {
            SkillEntry entry = skills.get(skill.id);
            if (entry == null) throw new IllegalArgumentException("技能直出表缺少定义：" + skill.id);
            var raw = skill.rawParameters(1);
            for (String dmgKey : skill.damageParameters()) {
                if (raw.containsKey(dmgKey) && !entry.damage().containsKey(dmgKey)) {
                    throw new IllegalArgumentException("技能 " + skill.id + " 缺少伤害参数：" + dmgKey);
                }
            }
        }
        for (var job : JobCatalog.all()) {
            if (!basicAttacks.containsKey(job.id())) {
                throw new IllegalArgumentException("player.json 缺少职业普攻定义：" + job.id());
            }
        }
    }

    private static SkillBalanceRules loadDefaults() {
        try {
            String master = readResource("/data/yumis_dungeon_and_fight/rules/balance/master.json");
            String player = readResource("/data/yumis_dungeon_and_fight/rules/balance/player.json");
            Map<String, String> groupJsons = new LinkedHashMap<>();
            for (String group : SKILL_GROUPS) {
                groupJsons.put(group, readResource("/data/yumis_dungeon_and_fight/rules/balance/skills/" + group + ".json"));
            }
            return parseAll(master, player, groupJsons);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readResource(String path) throws IOException {
        try (var in = SkillBalanceRules.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing resource: " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static SkillBalanceRules parseAll(String masterJson, String playerJson, Map<String, String> skillGroupJsons) {
        JsonObject master = JsonParser.parseString(masterJson).getAsJsonObject();
        JsonObject sk = master.getAsJsonObject("skills");
        JsonObject player = JsonParser.parseString(playerJson).getAsJsonObject();
        Map<String, SkillEntry> mergedSkills = new LinkedHashMap<>();
        for (var groupEntry : skillGroupJsons.entrySet()) {
            JsonObject fileObj = JsonParser.parseString(groupEntry.getValue()).getAsJsonObject();
            JsonObject skillsObj = fileObj.getAsJsonObject("skills");
            for (var e : skillsObj.entrySet()) {
                if (mergedSkills.put(e.getKey(), parseSkillEntry(e.getValue().getAsJsonObject())) != null) {
                    throw new IllegalArgumentException("重复的技能直出表定义：" + e.getKey());
                }
            }
        }
        Map<String, BasicAttackEntry> basics = parseBasicAttacks(player.getAsJsonObject("basicAttacks"));
        return new SkillBalanceRules(
            sk.get("globalSkillDamageMultiplier").getAsDouble(),
            sk.get("globalBasicAttackMultiplier").getAsDouble(),
            sk.get("defaultRankDamageGrowth").getAsDouble(),
            sk.get("basicAttackRankGrowth").getAsDouble(),
            sk.get("defaultRankMpGrowth").getAsDouble(),
            sk.get("mpUnlockBase").getAsDouble(),
            sk.get("mpUnlockPerLevel").getAsDouble(),
            mergedSkills,
            basics
        );
    }

    public static SkillBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        Map<String, SkillEntry> parsedSkills = new LinkedHashMap<>();
        for (var e : root.getAsJsonObject("skills").entrySet()) {
            parsedSkills.put(e.getKey(), parseSkillEntry(e.getValue().getAsJsonObject()));
        }
        Map<String, BasicAttackEntry> basics = parseBasicAttacks(root.getAsJsonObject("basicAttacks"));
        return new SkillBalanceRules(
            root.get("globalSkillDamageMultiplier").getAsDouble(),
            root.get("globalBasicAttackMultiplier").getAsDouble(),
            root.get("defaultRankDamageGrowth").getAsDouble(),
            root.get("basicAttackRankGrowth").getAsDouble(),
            root.get("defaultRankMpGrowth").getAsDouble(),
            root.get("mpUnlockBase").getAsDouble(),
            root.get("mpUnlockPerLevel").getAsDouble(),
            parsedSkills,
            basics
        );
    }

    private static SkillEntry parseSkillEntry(JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "";
        int unlock = obj.get("unlock").getAsInt();
        int sp = obj.get("sp").getAsInt();
        double mpRatio = obj.get("mpRatio").getAsDouble();
        double cooldown = obj.get("cooldown").getAsDouble();
        double cooldownPerRank = obj.has("cooldownPerRank") ? obj.get("cooldownPerRank").getAsDouble() : 0d;
        Double rankGrowth = obj.has("rankGrowth") && !obj.get("rankGrowth").isJsonNull() ? obj.get("rankGrowth").getAsDouble() : null;
        boolean damaging = obj.has("damaging") && obj.get("damaging").getAsBoolean();
        Map<String, Double> damage = parseDoubleMap(obj.getAsJsonObject("damage"));
        Map<String, Double> params = parseDoubleMap(obj.getAsJsonObject("parameters"));
        return new SkillEntry(title, unlock, sp, mpRatio, cooldown, cooldownPerRank, rankGrowth, damaging, damage, params);
    }

    private static Map<String, BasicAttackEntry> parseBasicAttacks(JsonObject obj) {
        Map<String, BasicAttackEntry> basics = new LinkedHashMap<>();
        if (obj == null) return basics;
        for (var e : obj.entrySet()) {
            JsonObject jobObj = e.getValue().getAsJsonObject();
            String defaultWeapon = jobObj.get("defaultWeapon").getAsString();
            double defaultScale = jobObj.get("defaultScale").getAsDouble();
            Map<String, Double> weapons = parseDoubleMap(jobObj.getAsJsonObject("weapons"));
            Map<String, Double> frenzy = jobObj.has("frenzyWeapons") ? parseDoubleMap(jobObj.getAsJsonObject("frenzyWeapons")) : Map.of();
            basics.put(e.getKey(), new BasicAttackEntry(defaultWeapon, defaultScale, weapons, frenzy));
        }
        return basics;
    }

    private static Map<String, Double> parseDoubleMap(JsonObject obj) {
        Map<String, Double> map = new LinkedHashMap<>();
        if (obj == null) return map;
        for (var e : obj.entrySet()) {
            JsonElement v = e.getValue();
            if (v != null && !v.isJsonNull()) map.put(e.getKey(), v.getAsDouble());
        }
        return map;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}

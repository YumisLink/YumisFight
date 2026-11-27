package cn.yumis.fight.modules.character.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/** Character-owned player growth, SP/XP progression and job style rules from rules/balance/player.json. */
public final class CharacterBalanceRules {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public record SpBracket(int belowLevel, int spPerLevel) {
        public SpBracket {
            if (belowLevel < 1 || spPerLevel < 0) throw new IllegalArgumentException("Invalid spBracket");
        }
    }

    public record BaseAttributes(
        double healthBase,
        double healthPerLevel,
        double mpBase,
        double mpPerLevel,
        double strengthBase,
        double intelligenceBase,
        double vitalityBase,
        double vitalityPerLevel,
        double spiritBase,
        double spiritPerLevel,
        double physicalAttackBase,
        double physicalAttackPerLevel,
        double magicalAttackBase,
        double magicalAttackPerLevel,
        double physicalDefenseBase,
        double magicalDefenseBase,
        double staminaBase,
        double staminaRecoveryBase,
        double shieldCooldownRecoveryBase,
        double shieldRegenerationBase,
        double movementSpeedBase,
        double castSpeedBase,
        double manaRegenerationBase,
        double cooldownRecoveryBase,
        double experienceGainBase
    ) {
        public BaseAttributes {
            requirePositive(healthBase, "healthBase");
            requireNonNegative(healthPerLevel, "healthPerLevel");
            requirePositive(mpBase, "mpBase");
            requireNonNegative(mpPerLevel, "mpPerLevel");
            requireNonNegative(strengthBase, "strengthBase");
            requireNonNegative(intelligenceBase, "intelligenceBase");
            requireNonNegative(vitalityBase, "vitalityBase");
            requireNonNegative(vitalityPerLevel, "vitalityPerLevel");
            requireNonNegative(spiritBase, "spiritBase");
            requireNonNegative(spiritPerLevel, "spiritPerLevel");
            requireNonNegative(physicalAttackBase, "physicalAttackBase");
            requireNonNegative(physicalAttackPerLevel, "physicalAttackPerLevel");
            requireNonNegative(magicalAttackBase, "magicalAttackBase");
            requireNonNegative(magicalAttackPerLevel, "magicalAttackPerLevel");
            requireNonNegative(physicalDefenseBase, "physicalDefenseBase");
            requireNonNegative(magicalDefenseBase, "magicalDefenseBase");
            requirePositive(staminaBase, "staminaBase");
            requireNonNegative(staminaRecoveryBase, "staminaRecoveryBase");
            requirePositive(shieldCooldownRecoveryBase, "shieldCooldownRecoveryBase");
            requireNonNegative(shieldRegenerationBase, "shieldRegenerationBase");
            requirePositive(movementSpeedBase, "movementSpeedBase");
            requirePositive(castSpeedBase, "castSpeedBase");
            requireNonNegative(manaRegenerationBase, "manaRegenerationBase");
            requirePositive(cooldownRecoveryBase, "cooldownRecoveryBase");
            requireNonNegative(experienceGainBase, "experienceGainBase");
        }
    }

    public record JobOverride(boolean magic, double damageTaken) {
        public JobOverride {
            requireNonNegative(damageTaken, "job.damageTaken");
        }
    }

    public final int maxLevel;
    public final int newbieExperienceMaxLevel;
    public final double newbieExperienceFactor;
    public final long experienceBase;
    public final long experiencePerLevel;
    public final long experienceFactorBase;
    public final long experienceFactorPerLevel;
    public final List<SpBracket> spBrackets;
    public final BaseAttributes baseAttributes;
    public final Map<String, JobOverride> jobs;

    public CharacterBalanceRules(
        int maxLevel,
        int newbieExperienceMaxLevel,
        double newbieExperienceFactor,
        long experienceBase,
        long experiencePerLevel,
        long experienceFactorBase,
        long experienceFactorPerLevel,
        List<SpBracket> spBrackets,
        BaseAttributes baseAttributes,
        Map<String, JobOverride> jobs
    ) {
        if (maxLevel < 1) throw new IllegalArgumentException("maxLevel must be >= 1");
        if (newbieExperienceMaxLevel < 0) throw new IllegalArgumentException("newbieExperienceMaxLevel must be >= 0");
        requireNonNegative(newbieExperienceFactor, "newbieExperienceFactor");
        if (experienceBase < 0 || experiencePerLevel < 0 || experienceFactorBase < 0 || experienceFactorPerLevel < 0)
            throw new IllegalArgumentException("experience formula constants must be >= 0");
        if (spBrackets == null || spBrackets.isEmpty()) throw new IllegalArgumentException("spBrackets must not be empty");
        this.maxLevel = maxLevel;
        this.newbieExperienceMaxLevel = newbieExperienceMaxLevel;
        this.newbieExperienceFactor = newbieExperienceFactor;
        this.experienceBase = experienceBase;
        this.experiencePerLevel = experiencePerLevel;
        this.experienceFactorBase = experienceFactorBase;
        this.experienceFactorPerLevel = experienceFactorPerLevel;
        this.spBrackets = List.copyOf(spBrackets);
        this.baseAttributes = Objects.requireNonNull(baseAttributes, "baseAttributes");
        this.jobs = Map.copyOf(Objects.requireNonNull(jobs, "jobs"));
    }

    public int spForLevel(int level) {
        for (SpBracket bracket : spBrackets) {
            if (level < bracket.belowLevel()) return bracket.spPerLevel();
        }
        return spBrackets.get(spBrackets.size() - 1).spPerLevel();
    }

    private static final CharacterBalanceRules DEFAULT = loadDefaults();
    private static volatile Supplier<CharacterBalanceRules> source = () -> DEFAULT;

    public static CharacterBalanceRules defaults() { return DEFAULT; }
    public static CharacterBalanceRules current() { return Objects.requireNonNull(source.get()); }
    public static void source(Supplier<CharacterBalanceRules> provider) { source = Objects.requireNonNull(provider); }

    private static CharacterBalanceRules loadDefaults() {
        try (var in = CharacterBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/player.json")) {
            if (in == null) throw new IllegalStateException("Missing rules/balance/player.json");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CharacterBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        int maxLevel = root.get("maxLevel").getAsInt();
        int newbieMax = root.get("newbieExperienceMaxLevel").getAsInt();
        double newbieFactor = root.get("newbieExperienceFactor").getAsDouble();
        long xpBase = root.get("experienceBase").getAsLong();
        long xpPerLevel = root.get("experiencePerLevel").getAsLong();
        long xpFactorBase = root.get("experienceFactorBase").getAsLong();
        long xpFactorPerLevel = root.get("experienceFactorPerLevel").getAsLong();
        List<SpBracket> brackets = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("spBrackets")) {
            brackets.add(JSON.fromJson(element, SpBracket.class));
        }
        BaseAttributes attrs = JSON.fromJson(root.getAsJsonObject("baseAttributes"), BaseAttributes.class);
        Map<String, JobOverride> jobs = new LinkedHashMap<>();
        for (var entry : root.getAsJsonObject("jobs").entrySet()) {
            jobs.put(entry.getKey(), JSON.fromJson(entry.getValue(), JobOverride.class));
        }
        return new CharacterBalanceRules(maxLevel, newbieMax, newbieFactor, xpBase, xpPerLevel, xpFactorBase, xpFactorPerLevel, brackets, attrs, jobs);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}

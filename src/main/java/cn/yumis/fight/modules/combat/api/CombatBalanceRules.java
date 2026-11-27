package cn.yumis.fight.modules.combat.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Combat-owned balance constants and sustain rules, sourced from rules/balance/master.json. */
public final class CombatBalanceRules {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    public record Elemental(
        double threshold,
        long settlementGraceMs,
        long resistanceWindowMs,
        double resistanceBuildupMultiplier,
        long shockDurationMs,
        long shockStrongDurationMs,
        double shockDamageRatio,
        long igniteDurationMs,
        long igniteStrongDurationMs,
        double igniteFraction,
        double igniteStrongFraction,
        double igniteMagicTaken,
        double igniteStrongMagicTaken,
        long freezeDurationMs,
        long freezeStrongDurationMs,
        double chillCastTimeScale,
        long erosionDurationMs,
        long erosionStrongDurationMs,
        double erosionFraction,
        double erosionStrongFraction,
        double erosionDefenseFactor,
        double erosionStrongDefenseFactor
    ) {
        public Elemental {
            requirePositive(threshold, "elemental.threshold");
            requirePositive(settlementGraceMs, "elemental.settlementGraceMs");
            requireNonNegative(resistanceWindowMs, "elemental.resistanceWindowMs");
            requireNonNegative(resistanceBuildupMultiplier, "elemental.resistanceBuildupMultiplier");
            requirePositive(shockDurationMs, "elemental.shockDurationMs");
            requirePositive(shockStrongDurationMs, "elemental.shockStrongDurationMs");
            requireNonNegative(shockDamageRatio, "elemental.shockDamageRatio");
            requirePositive(igniteDurationMs, "elemental.igniteDurationMs");
            requirePositive(igniteStrongDurationMs, "elemental.igniteStrongDurationMs");
            requireNonNegative(igniteFraction, "elemental.igniteFraction");
            requireNonNegative(igniteStrongFraction, "elemental.igniteStrongFraction");
            requirePositive(igniteMagicTaken, "elemental.igniteMagicTaken");
            requirePositive(igniteStrongMagicTaken, "elemental.igniteStrongMagicTaken");
            requirePositive(freezeDurationMs, "elemental.freezeDurationMs");
            requirePositive(freezeStrongDurationMs, "elemental.freezeStrongDurationMs");
            requirePositive(chillCastTimeScale, "elemental.chillCastTimeScale");
            requirePositive(erosionDurationMs, "elemental.erosionDurationMs");
            requirePositive(erosionStrongDurationMs, "elemental.erosionStrongDurationMs");
            requireNonNegative(erosionFraction, "elemental.erosionFraction");
            requireNonNegative(erosionStrongFraction, "elemental.erosionStrongFraction");
            requirePositive(erosionDefenseFactor, "elemental.erosionDefenseFactor");
            requirePositive(erosionStrongDefenseFactor, "elemental.erosionStrongDefenseFactor");
        }
    }

    public record Combat(
        double playerDamageMultiplier,
        double playerDamageTakenMultiplier,
        double defenseDivisor,
        double elementalDivisor,
        double playerResistFloor,
        double baseCritMultiplier,
        double evasionSoftCap,
        double evasionSoftSlope,
        double evasionDivisor,
        double evasionMaxChance,
        double movementProtectionMultiplier,
        int levelAdvantageGrace,
        double levelAdvantageHighPerLevel,
        double levelAdvantageLowPerLevel,
        double levelAdvantageFloor,
        Elemental elemental
    ) {
        public Combat {
            requireNonNegative(playerDamageMultiplier, "combat.playerDamageMultiplier");
            requireNonNegative(playerDamageTakenMultiplier, "combat.playerDamageTakenMultiplier");
            requirePositive(defenseDivisor, "combat.defenseDivisor");
            requirePositive(elementalDivisor, "combat.elementalDivisor");
            requireNonNegative(playerResistFloor, "combat.playerResistFloor");
            requirePositive(baseCritMultiplier, "combat.baseCritMultiplier");
            requireNonNegative(evasionSoftCap, "combat.evasionSoftCap");
            requirePositive(evasionSoftSlope, "combat.evasionSoftSlope");
            requirePositive(evasionDivisor, "combat.evasionDivisor");
            if (!Double.isFinite(evasionMaxChance) || evasionMaxChance < 0 || evasionMaxChance > 1)
                throw new IllegalArgumentException("combat.evasionMaxChance must be in [0, 1]");
            requireNonNegative(movementProtectionMultiplier, "combat.movementProtectionMultiplier");
            if (levelAdvantageGrace < 0) throw new IllegalArgumentException("combat.levelAdvantageGrace must be >= 0");
            requireNonNegative(levelAdvantageHighPerLevel, "combat.levelAdvantageHighPerLevel");
            requireNonNegative(levelAdvantageLowPerLevel, "combat.levelAdvantageLowPerLevel");
            requireNonNegative(levelAdvantageFloor, "combat.levelAdvantageFloor");
            Objects.requireNonNull(elemental, "combat.elemental");
        }
    }

    public record Sustain(
        double combatTimeoutSeconds,
        long shieldDelayMs,
        double shieldRegenRatePerSecond,
        double shieldRegenMultiplier,
        double shieldCooldownRecoveryMultiplier,
        double outOfCombatHealthRegenRatio,
        double healthRegenMultiplier,
        double manaRegenBaseRatio,
        double manaRegenSpiritSweetSpot,
        double manaRegenSpiritAtSweetSpot,
        double manaRegenSpiritMaxBonus,
        double manaRegenSpiritDecayScale,
        double manaRegenOutOfCombatMultiplier,
        double manaRegenMultiplier,
        double skillManaCostMultiplier,
        double cooldownRecoveryMultiplier,
        double staminaMax,
        double staminaCost,
        int staminaCooldownTicks,
        int staminaRecoveryDelayTicks,
        double staminaRecoveryMultiplier,
        int staminaProtectionTicks
    ) {
        public Sustain {
            requirePositive(combatTimeoutSeconds, "sustain.combatTimeoutSeconds");
            requireNonNegative(shieldDelayMs, "sustain.shieldDelayMs");
            requireNonNegative(shieldRegenRatePerSecond, "sustain.shieldRegenRatePerSecond");
            requireNonNegative(shieldRegenMultiplier, "sustain.shieldRegenMultiplier");
            requirePositive(shieldCooldownRecoveryMultiplier, "sustain.shieldCooldownRecoveryMultiplier");
            requireNonNegative(outOfCombatHealthRegenRatio, "sustain.outOfCombatHealthRegenRatio");
            requireNonNegative(healthRegenMultiplier, "sustain.healthRegenMultiplier");
            requireNonNegative(manaRegenBaseRatio, "sustain.manaRegenBaseRatio");
            requireNonNegative(manaRegenSpiritSweetSpot, "sustain.manaRegenSpiritSweetSpot");
            requireNonNegative(manaRegenSpiritAtSweetSpot, "sustain.manaRegenSpiritAtSweetSpot");
            requireNonNegative(manaRegenSpiritMaxBonus, "sustain.manaRegenSpiritMaxBonus");
            if (manaRegenSpiritAtSweetSpot > manaRegenSpiritMaxBonus)
                throw new IllegalArgumentException("sustain.manaRegenSpiritAtSweetSpot must be <= manaRegenSpiritMaxBonus");
            requirePositive(manaRegenSpiritDecayScale, "sustain.manaRegenSpiritDecayScale");
            requireNonNegative(manaRegenOutOfCombatMultiplier, "sustain.manaRegenOutOfCombatMultiplier");
            requireNonNegative(manaRegenMultiplier, "sustain.manaRegenMultiplier");
            requireNonNegative(skillManaCostMultiplier, "sustain.skillManaCostMultiplier");
            requirePositive(cooldownRecoveryMultiplier, "sustain.cooldownRecoveryMultiplier");
            requirePositive(staminaMax, "sustain.staminaMax");
            requireNonNegative(staminaCost, "sustain.staminaCost");
            if (staminaCooldownTicks < 0) throw new IllegalArgumentException("sustain.staminaCooldownTicks must be >= 0");
            if (staminaRecoveryDelayTicks < 0) throw new IllegalArgumentException("sustain.staminaRecoveryDelayTicks must be >= 0");
            requireNonNegative(staminaRecoveryMultiplier, "sustain.staminaRecoveryMultiplier");
            if (staminaProtectionTicks < 0) throw new IllegalArgumentException("sustain.staminaProtectionTicks must be >= 0");
        }
    }

    public final Combat combat;
    public final Sustain sustain;

    public CombatBalanceRules(Combat combat, Sustain sustain) {
        this.combat = Objects.requireNonNull(combat);
        this.sustain = Objects.requireNonNull(sustain);
    }

    private static final CombatBalanceRules DEFAULT = loadDefaults();
    private static volatile Supplier<CombatBalanceRules> source = () -> DEFAULT;

    public static CombatBalanceRules defaults() { return DEFAULT; }
    public static CombatBalanceRules current() { return Objects.requireNonNull(source.get()); }
    public static void source(Supplier<CombatBalanceRules> provider) { source = Objects.requireNonNull(provider); }

    private static CombatBalanceRules loadDefaults() {
        try (var in = CombatBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/master.json")) {
            if (in == null) throw new IllegalStateException("Missing rules/balance/master.json");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CombatBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject combatObj = root.has("combat") ? root.getAsJsonObject("combat") : root;
        JsonObject sustainObj = root.has("sustain") ? root.getAsJsonObject("sustain") : null;
        if (combatObj == null || sustainObj == null) {
            throw new IllegalArgumentException("CombatBalanceRules requires combat and sustain sections");
        }
        Combat combat = JSON.fromJson(combatObj, Combat.class);
        Sustain sustain = JSON.fromJson(sustainObj, Sustain.class);
        return new CombatBalanceRules(combat, sustain);
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0) throw new IllegalArgumentException(name + " must be > 0");
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}

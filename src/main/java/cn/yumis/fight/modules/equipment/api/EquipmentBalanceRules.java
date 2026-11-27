package cn.yumis.fight.modules.equipment.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Equipment-owned global multipliers and spirit conversion coefficients from master.json + player.json. */
public final class EquipmentBalanceRules {
    private static final Gson JSON = new GsonBuilder().disableHtmlEscaping().create();

    /** 100-point sweet-spot diminishing-return curve: linear up to sweetSpot, sharp hyperbolic decay toward maxBonus. */
    public record SoftCapCurve(
        double sweetSpot,
        double atSweetSpot,
        double maxBonus,
        double decayScale
    ) {
        public SoftCapCurve {
            requireNonNegative(sweetSpot, "curve.sweetSpot");
            requireNonNegative(atSweetSpot, "curve.atSweetSpot");
            requireNonNegative(maxBonus, "curve.maxBonus");
            if (atSweetSpot > maxBonus) throw new IllegalArgumentException("curve.atSweetSpot must be <= maxBonus");
            if (!Double.isFinite(decayScale) || decayScale <= 0) throw new IllegalArgumentException("curve.decayScale must be > 0");
        }

        public double evaluate(double value) {
            double x = Math.max(0.0, value);
            if (x <= 0.0 || maxBonus <= 0.0) return 0.0;
            if (sweetSpot > 0.0 && x <= sweetSpot) {
                return atSweetSpot * (x / sweetSpot);
            }
            double excess = x - sweetSpot;
            double remaining = Math.max(0.0, maxBonus - atSweetSpot);
            return atSweetSpot + remaining * (excess / (excess + decayScale));
        }
    }

    public record VitalityConversions(
        double healthPerVitality,
        double physicalDefensePerVitality,
        double mpPerVitality,
        double staminaPerVitality,
        SoftCapCurve staminaRecovery,
        SoftCapCurve attackSpeed
    ) {
        public VitalityConversions {
            requireNonNegative(healthPerVitality, "vitality.healthPerVitality");
            requireNonNegative(physicalDefensePerVitality, "vitality.physicalDefensePerVitality");
            requireNonNegative(mpPerVitality, "vitality.mpPerVitality");
            requireNonNegative(staminaPerVitality, "vitality.staminaPerVitality");
            Objects.requireNonNull(staminaRecovery, "vitality.staminaRecovery");
            Objects.requireNonNull(attackSpeed, "vitality.attackSpeed");
        }
    }

    public record SpiritConversions(
        double magicalDefensePerSpirit,
        SoftCapCurve cooldownRecovery,
        SoftCapCurve castSpeed
    ) {
        public SpiritConversions {
            requireNonNegative(magicalDefensePerSpirit, "spirit.magicalDefensePerSpirit");
            Objects.requireNonNull(cooldownRecovery, "spirit.cooldownRecovery");
            Objects.requireNonNull(castSpeed, "spirit.castSpeed");
        }
    }

    public final double baseContributionMultiplier;
    public final double enhancementContributionMultiplier;
    public final VitalityConversions vitalityConversions;
    public final SpiritConversions spiritConversions;

    public EquipmentBalanceRules(double baseContributionMultiplier, double enhancementContributionMultiplier, VitalityConversions vitalityConversions, SpiritConversions spiritConversions) {
        requireNonNegative(baseContributionMultiplier, "equipment.baseContributionMultiplier");
        requireNonNegative(enhancementContributionMultiplier, "equipment.enhancementContributionMultiplier");
        this.baseContributionMultiplier = baseContributionMultiplier;
        this.enhancementContributionMultiplier = enhancementContributionMultiplier;
        this.vitalityConversions = Objects.requireNonNull(vitalityConversions, "vitalityConversions");
        this.spiritConversions = Objects.requireNonNull(spiritConversions, "spiritConversions");
    }

    private static final EquipmentBalanceRules DEFAULT = loadDefaults();
    private static volatile Supplier<EquipmentBalanceRules> source = () -> DEFAULT;

    public static EquipmentBalanceRules defaults() { return DEFAULT; }
    public static EquipmentBalanceRules current() { return Objects.requireNonNull(source.get()); }
    public static void source(Supplier<EquipmentBalanceRules> provider) { source = Objects.requireNonNull(provider); }

    private static EquipmentBalanceRules loadDefaults() {
        try (var masterIn = EquipmentBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/master.json");
             var playerIn = EquipmentBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/player.json")) {
            if (masterIn == null || playerIn == null) throw new IllegalStateException("Missing master.json or player.json");
            return parse(new String(masterIn.readAllBytes(), StandardCharsets.UTF_8), new String(playerIn.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static EquipmentBalanceRules parse(String masterJson, String playerJson) {
        JsonObject master = JsonParser.parseString(masterJson).getAsJsonObject();
        JsonObject eq = master.getAsJsonObject("equipment");
        JsonObject player = JsonParser.parseString(playerJson).getAsJsonObject();
        VitalityConversions vitality = JSON.fromJson(player.getAsJsonObject("vitalityConversions"), VitalityConversions.class);
        SpiritConversions spirit = JSON.fromJson(player.getAsJsonObject("spiritConversions"), SpiritConversions.class);
        return new EquipmentBalanceRules(
            eq.get("baseContributionMultiplier").getAsDouble(),
            eq.get("enhancementContributionMultiplier").getAsDouble(),
            vitality,
            spirit
        );
    }

    public static EquipmentBalanceRules parsePacket(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        VitalityConversions vitality = JSON.fromJson(root.getAsJsonObject("vitalityConversions"), VitalityConversions.class);
        SpiritConversions spirit = JSON.fromJson(root.getAsJsonObject("spiritConversions"), SpiritConversions.class);
        return new EquipmentBalanceRules(
            root.get("baseContributionMultiplier").getAsDouble(),
            root.get("enhancementContributionMultiplier").getAsDouble(),
            vitality,
            spirit
        );
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be >= 0");
    }
}

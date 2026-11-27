package cn.yumis.fight.modules.economy.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Economy-owned global pacing multipliers sourced from rules/balance/master.json. */
public record EconomyBalanceRules(
    double killExperienceMultiplier,
    double killGoldMultiplier,
    double dropQuantityMultiplier
) {
    public EconomyBalanceRules {
        if (!Double.isFinite(killExperienceMultiplier) || killExperienceMultiplier < 0)
            throw new IllegalArgumentException("economy.killExperienceMultiplier must be >= 0");
        if (!Double.isFinite(killGoldMultiplier) || killGoldMultiplier < 0)
            throw new IllegalArgumentException("economy.killGoldMultiplier must be >= 0");
        if (!Double.isFinite(dropQuantityMultiplier) || dropQuantityMultiplier < 0)
            throw new IllegalArgumentException("economy.dropQuantityMultiplier must be >= 0");
    }

    private static final EconomyBalanceRules DEFAULT = loadDefaults();
    private static volatile Supplier<EconomyBalanceRules> source = () -> DEFAULT;

    public static EconomyBalanceRules defaults() { return DEFAULT; }
    public static EconomyBalanceRules current() { return Objects.requireNonNull(source.get()); }
    public static void source(Supplier<EconomyBalanceRules> provider) { source = Objects.requireNonNull(provider); }

    private static EconomyBalanceRules loadDefaults() {
        try (var in = EconomyBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/master.json")) {
            if (in == null) throw new IllegalStateException("Missing rules/balance/master.json");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static EconomyBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject eco = root.has("economy") ? root.getAsJsonObject("economy") : root;
        return new EconomyBalanceRules(
            eco.get("killExperienceMultiplier").getAsDouble(),
            eco.get("killGoldMultiplier").getAsDouble(),
            eco.get("dropQuantityMultiplier").getAsDouble()
        );
    }
}

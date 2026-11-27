package cn.yumis.fight.modules.talents.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Supplier;

/** Talent-owned global value multiplier sourced from rules/balance/master.json. */
public record TalentBalanceRules(double valueMultiplier) {
    public TalentBalanceRules {
        if (!Double.isFinite(valueMultiplier) || valueMultiplier < 0)
            throw new IllegalArgumentException("talents.valueMultiplier must be >= 0");
    }

    private static final TalentBalanceRules DEFAULT = loadDefaults();
    private static volatile Supplier<TalentBalanceRules> source = () -> DEFAULT;

    public static TalentBalanceRules defaults() { return DEFAULT; }
    public static TalentBalanceRules current() { return Objects.requireNonNull(source.get()); }
    public static void source(Supplier<TalentBalanceRules> provider) { source = Objects.requireNonNull(provider); }

    private static TalentBalanceRules loadDefaults() {
        try (var in = TalentBalanceRules.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/balance/master.json")) {
            if (in == null) throw new IllegalStateException("Missing rules/balance/master.json");
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static TalentBalanceRules parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject talents = root.has("talents") ? root.getAsJsonObject("talents") : root;
        return new TalentBalanceRules(talents.get("valueMultiplier").getAsDouble());
    }
}

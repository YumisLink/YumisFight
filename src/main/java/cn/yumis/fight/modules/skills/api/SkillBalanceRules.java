package cn.yumis.fight.modules.skills.api;

import com.google.gson.JsonParser;
import java.util.Objects;
import java.util.function.Supplier;

/** Skill-owned, single-scalar damage budget. */
public record SkillBalanceRules(double baseWeightDps) {
    private static Supplier<SkillBalanceRules> source=SkillBalanceRules::defaults;
    public SkillBalanceRules {if(!Double.isFinite(baseWeightDps)||baseWeightDps<=0)throw new IllegalArgumentException("baseWeightDps必须是大于0的有限数值");}
    public static SkillBalanceRules defaults(){return new SkillBalanceRules(.5);}
    public static SkillBalanceRules current(){return Objects.requireNonNull(source.get());}
    public static void source(Supplier<SkillBalanceRules> provider){source=Objects.requireNonNull(provider);}
    public static SkillBalanceRules parse(String json){var value=JsonParser.parseString(json).getAsJsonObject();if(!value.has("baseWeightDps"))throw new IllegalArgumentException("技能规则缺少baseWeightDps");return new SkillBalanceRules(value.get("baseWeightDps").getAsDouble());}
}

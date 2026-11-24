package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.architecture.StableId;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;
import cn.yumis.fight.platform.ItemData;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

/** Data-driven Battle Mage morph presets (morphMode = 1 / 2) loaded from rules/battle_mage.json. */
public final class BattleMageMorphs {
    public record ModifierSpec(String parameter, double additive, double multiplier) {
        public ModifierSpec {
            if (parameter == null || parameter.isBlank() || !Double.isFinite(additive)
                || !Double.isFinite(multiplier) || multiplier < 0) {
                throw new IllegalArgumentException("战斗法师形态参数修饰无效：" + parameter);
            }
        }
    }

    public record Preset(String title, String summary, List<ModifierSpec> modifiers) {
        public Preset {
            if (title == null || title.isBlank() || summary == null || summary.isBlank() || modifiers == null) {
                throw new IllegalArgumentException("战斗法师形态预设不完整");
            }
            modifiers = List.copyOf(modifiers);
        }
    }

    private Map<String, Map<String, Preset>> morphs = Map.of();
    private static Supplier<BattleMageMorphs> source = BattleMageMorphs::defaults;

    private static final class Defaults {
        private static final String JSON = read();

        private static String read() {
            try (var input = BattleMageMorphs.class.getResourceAsStream("/data/" + ItemData.NS + "/rules/battle_mage.json")) {
                if (input == null) throw new IllegalStateException("缺少默认战斗法师形态规则 rules/battle_mage.json");
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            } catch (Exception failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }

    public static BattleMageMorphs defaults() {
        return parse(Defaults.JSON);
    }

    public static void source(Supplier<BattleMageMorphs> value) {
        source = Objects.requireNonNull(value);
    }

    public static BattleMageMorphs current() {
        return Objects.requireNonNull(source.get());
    }

    public static BattleMageMorphs parse(String json) {
        var result = new Gson().fromJson(json, BattleMageMorphs.class);
        if (result == null || result.morphs == null) {
            throw new IllegalArgumentException("战斗法师形态规则缺失 morphs");
        }
        var validated = new LinkedHashMap<String, Map<String, Preset>>();
        for (var entry : result.morphs.entrySet()) {
            String skillId = entry.getKey();
            new StableId(skillId);
            if (entry.getValue() == null) throw new IllegalArgumentException("形态表为空：" + skillId);
            var modes = new LinkedHashMap<String, Preset>();
            for (var modeEntry : entry.getValue().entrySet()) {
                int mode;
                try {
                    mode = Integer.parseInt(modeEntry.getKey());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("形态编号无效：" + skillId + "/" + modeEntry.getKey());
                }
                if (mode != 1 && mode != 2) {
                    throw new IllegalArgumentException("形态编号仅允许 1 或 2：" + skillId + "/" + mode);
                }
                Preset preset = modeEntry.getValue();
                if (preset == null) throw new IllegalArgumentException("形态预设为空：" + skillId + "/" + mode);
                var validatedMods = new ArrayList<ModifierSpec>();
                var seenParams = new HashSet<String>();
                for (ModifierSpec spec : preset.modifiers()) {
                    if (!seenParams.add(spec.parameter())) {
                        throw new IllegalArgumentException("同一形态重复参数修饰：" + skillId + "/" + mode + "/" + spec.parameter());
                    }
                    validatedMods.add(new ModifierSpec(spec.parameter(), spec.additive(), spec.multiplier()));
                }
                modes.put(Integer.toString(mode), new Preset(preset.title(), preset.summary(), validatedMods));
            }
            validated.put(skillId, Map.copyOf(modes));
        }
        result.morphs = Map.copyOf(validated);
        return result;
    }

    public Preset find(String skillId, int morphMode) {
        if (morphMode != 1 && morphMode != 2) return null;
        var skillModes = morphs.get(skillId);
        return skillModes == null ? null : skillModes.get(Integer.toString(morphMode));
    }

    public static int resolveMorphMode(String skillId, Collection<ParameterModifier> changes) {
        double add = 0;
        double mul = 1;
        boolean touched = false;
        for (ParameterModifier mod : changes) {
            if (skillId.equals(mod.skill()) && "morphMode".equals(mod.parameter())) {
                add += mod.additive();
                mul *= mod.multiplier();
                touched = true;
            }
        }
        if (!touched) return 0;
        int mode = (int) Math.round(Math.max(0, add * mul));
        return mode == 1 || mode == 2 ? mode : 0;
    }

    public static List<ParameterModifier> modifierBundle(String skillId, int morphMode) {
        var preset = current().find(skillId, morphMode);
        if (preset == null || preset.modifiers().isEmpty()) return List.of();
        var out = new ArrayList<ParameterModifier>(preset.modifiers().size());
        String sourcePrefix = "battle_mage_morph:" + skillId + ":" + morphMode;
        for (var spec : preset.modifiers()) {
            out.add(new ParameterModifier(sourcePrefix, skillId, spec.parameter(), spec.additive(), spec.multiplier()));
        }
        return out;
    }

    public static List<ParameterModifier> activeModifiers(String skillId, Collection<ParameterModifier> changes) {
        if (!BattleMageSkills.contains(skillId) && !MageSkills.FANG.equals(skillId)) return List.of();
        int mode = resolveMorphMode(skillId, changes);
        return mode > 0 ? modifierBundle(skillId, mode) : List.of();
    }
}

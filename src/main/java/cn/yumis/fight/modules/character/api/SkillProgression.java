package cn.yumis.fight.modules.character.api;

import com.google.gson.JsonObject;

/** The character transaction delegates skill changes to the registered skill application. */
@FunctionalInterface
public interface SkillProgression {
    void apply(Progress progress,JsonObject request);
}

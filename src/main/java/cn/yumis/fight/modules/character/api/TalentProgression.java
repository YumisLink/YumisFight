package cn.yumis.fight.modules.character.api;

/** The character transaction delegates talent placement and removal to the registered talent application. */
@FunctionalInterface
public interface TalentProgression {
    void apply(Progress progress,String kind,String talent);
}

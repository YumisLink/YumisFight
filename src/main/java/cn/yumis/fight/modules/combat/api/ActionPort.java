package cn.yumis.fight.modules.combat.api;

/** Platform/faction execution adapter for driving an ActionBlueprint across players, enemies or sourceless actions. */
public interface ActionPort {
    default void onStart(ActionContext context, ActionBlueprint blueprint) {}
    void onCue(ActionContext context, ActionBlueprint blueprint, ActionBlueprint.Cue cue, int cueIndex);
    default void onCanBreak(ActionContext context, ActionBlueprint blueprint) {}
    default void onComplete(ActionContext context, ActionBlueprint blueprint) {}
    default void onAbandon(ActionContext context, ActionBlueprint blueprint) {}
}

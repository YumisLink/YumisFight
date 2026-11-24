package cn.yumis.fight.modules.combat.api;

/** Explicit execution phase for player casts, enemy moves and sourceless actions. */
public enum ActionPhase {
    IDLE,
    CHANTING,
    ACTIVE,
    ONGOING,
    COMPLETED,
    ABANDONED;

    public boolean running() {
        return this == CHANTING || this == ACTIVE || this == ONGOING;
    }

    public boolean terminal() {
        return this == COMPLETED || this == ABANDONED;
    }
}

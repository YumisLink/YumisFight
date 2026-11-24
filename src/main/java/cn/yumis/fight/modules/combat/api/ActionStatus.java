package cn.yumis.fight.modules.combat.api;

/** Declarative runtime state flags managed via owner-scoped leases across players, enemies and sourceless actions. */
public enum ActionStatus {
    INVULNERABLE,
    STEADFAST,
    NO_GRAVITY,
    LOCK_MOVEMENT,
    GUARDED,
    INVISIBLE
}

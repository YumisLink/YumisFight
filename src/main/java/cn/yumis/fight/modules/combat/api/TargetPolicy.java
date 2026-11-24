package cn.yumis.fight.modules.combat.api;

/** Target faction policy for player, enemy and sourceless skill execution. */
public enum TargetPolicy {
    HOSTILE_TO_ENEMIES,
    HOSTILE_TO_PLAYERS,
    BOTH;

    public boolean hitsEnemies() {
        return this == HOSTILE_TO_ENEMIES || this == BOTH;
    }

    public boolean hitsPlayers() {
        return this == HOSTILE_TO_PLAYERS || this == BOTH;
    }
}

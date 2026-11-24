package cn.yumis.fight.modules.dungeons.api;

/** Six-tier stat scaling grades for special awakening tower bosses (E=1.0, +0.20 per grade up to S=2.0). */
public enum TowerBossGrade {
    E(1.0),
    D(1.2),
    C(1.4),
    B(1.6),
    A(1.8),
    S(2.0);

    private final double multiplier;

    TowerBossGrade(double multiplier) {
        this.multiplier = multiplier;
    }

    public double multiplier() {
        return multiplier;
    }
}

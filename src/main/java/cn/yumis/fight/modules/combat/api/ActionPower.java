package cn.yumis.fight.modules.combat.api;

import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.attributes.api.Stats;

/**
 * Lightweight combat power snapshot for executing a skill blueprint without requiring full player Stats.
 * Players adapt from Stats, enemies adapt from Target, and sourceless actions pass direct values.
 */
public record ActionPower(
    double baseAttack,
    double coefficient,
    boolean magic,
    int[] elements,
    double actionSpeed,
    double controlSeconds,
    double knockback
) {
    public ActionPower {
        if (!Double.isFinite(baseAttack) || baseAttack < 0) throw new IllegalArgumentException("Invalid baseAttack");
        if (!Double.isFinite(coefficient) || coefficient < 0) throw new IllegalArgumentException("Invalid coefficient");
        if (!Double.isFinite(actionSpeed) || actionSpeed <= 0) actionSpeed = 1.0;
        if (!Double.isFinite(controlSeconds) || controlSeconds < 0) controlSeconds = 0;
        if (!Double.isFinite(knockback) || knockback < 0) knockback = 0;
        elements = elements == null ? new int[0] : elements.clone();
    }

    @Override
    public int[] elements() {
        return elements.clone();
    }

    public int primaryElement() {
        return elements.length == 0 ? -1 : elements[0];
    }

    public static ActionPower fromStats(Stats stats, double coefficient, boolean magic, int[] elements, double actionSpeed) {
        double attack = stats == null ? 0 : stats.get(magic ? Stat.MAGICAL_ATTACK : Stat.PHYSICAL_ATTACK);
        return new ActionPower(Math.max(0, attack), Math.max(0, coefficient), magic, elements, Math.max(0.01, actionSpeed), 0, 0);
    }

    public static ActionPower fromEnemy(double enemyAttack, double coefficient, boolean magic, int element, double actionSpeed) {
        int[] elem = element < 0 ? new int[0] : new int[]{element};
        return new ActionPower(Math.max(0, enemyAttack), Math.max(0, coefficient), magic, elem, Math.max(0.01, actionSpeed), 0, 0);
    }

    public static ActionPower fixed(double baseAttack, double coefficient, boolean magic, int[] elements, double actionSpeed) {
        return new ActionPower(Math.max(0, baseAttack), Math.max(0, coefficient), magic, elements, Math.max(0.01, actionSpeed), 0, 0);
    }

    public ActionPower withCoefficient(double next) {
        return new ActionPower(baseAttack, Math.max(0, next), magic, elements, actionSpeed, controlSeconds, knockback);
    }

    public ActionPower withControl(double next) {
        return new ActionPower(baseAttack, coefficient, magic, elements, actionSpeed, Math.max(0, next), knockback);
    }

    public ActionPower withKnockback(double next) {
        return new ActionPower(baseAttack, coefficient, magic, elements, actionSpeed, controlSeconds, Math.max(0, next));
    }
}

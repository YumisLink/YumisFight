package cn.yumis.fight.modules.combat.api;

import net.minecraft.world.damagesource.DamageSource;

/** A non-hostile world entity whose life is maintained by the existing server life owner. */
public interface WorldVitalEntity {
    int combatLevel();
    double combatMaximumHealth();
    boolean rejectsNativeDamage(DamageSource source);
}

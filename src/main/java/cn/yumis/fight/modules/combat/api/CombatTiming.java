package cn.yumis.fight.modules.combat.api;
/** Damage clocks share one definition on the client, server and tests. */
public final class CombatTiming {
    public static final long COMBAT_DELAY=3000,SHIELD_DELAY=5000;
    private CombatTiming(){}
    public static void dealt(cn.yumis.fight.modules.combat.api.Runtime r,long now){r.combatUntil=Math.max(r.combatUntil,now+COMBAT_DELAY);}
    public static void taken(cn.yumis.fight.modules.combat.api.Runtime r,long now){dealt(r,now);r.shieldRecoveryAt=Math.max(r.shieldRecoveryAt,now+SHIELD_DELAY);}
    public static boolean out(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.combatUntil;}
    public static boolean shieldReady(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.shieldRecoveryAt;}
}

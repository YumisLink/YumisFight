package cn.yumis.fight.rules;

/** Damage clocks share one definition on the client, server and tests. */
public final class CombatTiming {
    public static final long COMBAT_DELAY=3000,SHIELD_DELAY=5000;
    private CombatTiming(){}
    public static void dealt(Models.Runtime r,long now){r.combatUntil=Math.max(r.combatUntil,now+COMBAT_DELAY);}
    public static void taken(Models.Runtime r,long now){dealt(r,now);r.shieldRecoveryAt=Math.max(r.shieldRecoveryAt,now+SHIELD_DELAY);}
    public static boolean out(Models.Runtime r,long now){return now>=r.combatUntil;}
    public static boolean shieldReady(Models.Runtime r,long now){return now>=r.shieldRecoveryAt;}
}

package cn.yumis.fight.modules.combat.api;
/** Damage clocks share one definition on the client, server and tests. */
public final class CombatTiming {
    public static final long SHIELD_DELAY=5000;
    private CombatTiming(){}
    public static void dealt(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings){r.combatUntil=Math.max(r.combatUntil,now+settings.delayMillis());EquipmentBuffs.combat(r,now);}
    public static void taken(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings,double shieldRecovery){dealt(r,now,settings);r.shieldRecoveryAt=Math.max(r.shieldRecoveryAt,now+Math.round(SHIELD_DELAY/shieldRecovery));}
    public static boolean out(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.combatUntil;}
    public static boolean shieldReady(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.shieldRecoveryAt;}
}

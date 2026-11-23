package cn.yumis.fight.modules.combat.api;
/** Damage clocks share one definition on the client, server and tests. */
public final class CombatTiming {
    public static final long SHIELD_DELAY=5000;
    private CombatTiming(){}
    public static void dealt(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings){r.combatUntil=Math.max(r.combatUntil,now+settings.delayMillis());EquipmentBuffs.combat(r,now);}
    public static void taken(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings,double shieldRecovery){dealt(r,now,settings);r.shieldRecoveryAt=Math.max(r.shieldRecoveryAt,now+Math.round(SHIELD_DELAY/shieldRecovery));}
    public static boolean out(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.combatUntil;}
    /** Out-of-combat health recovery per tick: 2% of maximum plus flat HEALTH_REGENERATION points per second. */
    public static double regeneration(double tick,double maximum,double regeneration){return .02*tick+(maximum>0?regeneration*tick/maximum:0);}
    /** Base mana recovery per second: (1% plus 0.05% per spirit point) of maximum MP. */
    public static double manaRegeneration(double maximum,double spirit){return maximum*(.01+.0005*spirit);}
    public static boolean shieldReady(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.shieldRecoveryAt;}
}

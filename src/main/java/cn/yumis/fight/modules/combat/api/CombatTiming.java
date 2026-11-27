package cn.yumis.fight.modules.combat.api;
/** Damage clocks share one definition on the client, server and tests. */
public final class CombatTiming {
    public static final long SHIELD_DELAY=5000;
    private CombatTiming(){}
    public static void dealt(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings){r.combatUntil=Math.max(r.combatUntil,now+settings.delayMillis());EquipmentBuffs.combat(r,now);}
    public static void taken(cn.yumis.fight.modules.combat.api.Runtime r,long now,CombatSettings settings,double shieldRecovery){
        dealt(r,now,settings);
        var sustain=CombatBalanceRules.current().sustain;
        double effectiveRecovery=Math.max(1e-6,shieldRecovery*sustain.shieldCooldownRecoveryMultiplier());
        r.shieldRecoveryAt=Math.max(r.shieldRecoveryAt,now+Math.round(sustain.shieldDelayMs()/effectiveRecovery));
    }
    public static boolean out(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.combatUntil;}
    /** Out-of-combat health recovery per tick: ratio of maximum plus flat HEALTH_REGENERATION points per second. */
    public static double regeneration(double tick,double maximum,double regeneration){
        var sustain=CombatBalanceRules.current().sustain;
        return (sustain.outOfCombatHealthRegenRatio()*tick+(maximum>0?regeneration*tick/maximum:0))*sustain.healthRegenMultiplier();
    }
    /** Base mana recovery per second with 100-point soft-cap diminishing spirit curve. */
    public static double manaRegeneration(double maximum,double spirit){
        var sustain=CombatBalanceRules.current().sustain;
        double x=Math.max(0.0,spirit);
        double bonus;
        if(x<=0.0||sustain.manaRegenSpiritMaxBonus()<=0.0){
            bonus=0.0;
        }else if(sustain.manaRegenSpiritSweetSpot()>0.0&&x<=sustain.manaRegenSpiritSweetSpot()){
            bonus=sustain.manaRegenSpiritAtSweetSpot()*(x/sustain.manaRegenSpiritSweetSpot());
        }else{
            double excess=x-sustain.manaRegenSpiritSweetSpot();
            double remaining=Math.max(0.0,sustain.manaRegenSpiritMaxBonus()-sustain.manaRegenSpiritAtSweetSpot());
            bonus=sustain.manaRegenSpiritAtSweetSpot()+remaining*(excess/(excess+sustain.manaRegenSpiritDecayScale()));
        }
        return maximum*(sustain.manaRegenBaseRatio()+bonus)*sustain.manaRegenMultiplier();
    }
    public static double manaOutOfCombatMultiplier(){return CombatBalanceRules.current().sustain.manaRegenOutOfCombatMultiplier();}
    public static double shieldRegenerationRate(){
        var sustain=CombatBalanceRules.current().sustain;
        return sustain.shieldRegenRatePerSecond()*sustain.shieldRegenMultiplier();
    }
    public static boolean shieldReady(cn.yumis.fight.modules.combat.api.Runtime r,long now){return now>=r.shieldRecoveryAt;}
}

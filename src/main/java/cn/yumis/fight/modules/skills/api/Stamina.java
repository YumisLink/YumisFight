package cn.yumis.fight.modules.skills.api;
import cn.yumis.fight.modules.combat.api.CombatBalanceRules;
/** Movement resource; recovery follows the last actual expense, independent of grounded state. */
public final class Stamina {
    public static final int MAXIMUM=100,COST=20,COOLDOWN=5,RECOVERY_DELAY=20,PROTECTION=200;
    public double value=MAXIMUM,maximum=MAXIMUM,recovery=1;
    public static int protectionTicks(){return CombatBalanceRules.current().sustain.staminaProtectionTicks();}
    public void attributes(double maximum,double recovery){this.maximum=Math.max(1,maximum);this.recovery=Math.max(0,recovery);value=Math.min(value,this.maximum);}
    public long lastUse=-RECOVERY_DELAY;
    private long lastConsumption=-RECOVERY_DELAY;
    private long lastRecovery=Long.MIN_VALUE;
    public boolean ready(long tick){return tick-lastUse>=CombatBalanceRules.current().sustain.staminaCooldownTicks();}
    public boolean use(long tick,boolean exempt){
        double cost=CombatBalanceRules.current().sustain.staminaCost();
        if(!ready(tick)||!exempt&&value<cost)return false;
        if(!exempt){value-=cost;lastConsumption=tick;}
        lastUse=tick;
        return true;
    }
    public boolean recover(long tick,boolean grounded,boolean exempt){
        if(tick==lastRecovery)return false;
        lastRecovery=tick;
        var sustain=CombatBalanceRules.current().sustain;
        if(exempt||tick-lastConsumption<sustain.staminaRecoveryDelayTicks()||value>=maximum)return false;
        value=Math.min(maximum,value+recovery*sustain.staminaRecoveryMultiplier());
        return true;
    }
}

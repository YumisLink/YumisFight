package cn.yumis.fight.modules.skills.api;
/** Movement resource; recovery follows the last actual expense, independent of grounded state. */
public final class Stamina {
    public static final int MAXIMUM=100,COST=20,COOLDOWN=5,RECOVERY_DELAY=20,PROTECTION=200;
    public double value=MAXIMUM,maximum=MAXIMUM,recovery=1;
    public void attributes(double maximum,double recovery){this.maximum=Math.max(1,maximum);this.recovery=Math.max(0,recovery);value=Math.min(value,this.maximum);}
    public long lastUse=-RECOVERY_DELAY;
    private long lastConsumption=-RECOVERY_DELAY;
    private long lastRecovery=Long.MIN_VALUE;
    public boolean ready(long tick){return tick-lastUse>=COOLDOWN;}
    public boolean use(long tick,boolean exempt){
        if(!ready(tick)||!exempt&&value<COST)return false;
        if(!exempt){value-=COST;lastConsumption=tick;}
        lastUse=tick;
        return true;
    }
    public boolean recover(long tick,boolean grounded,boolean exempt){
        if(tick==lastRecovery)return false;
        lastRecovery=tick;
        if(exempt||tick-lastConsumption<RECOVERY_DELAY||value>=maximum)return false;
        value=Math.min(maximum,value+recovery);
        return true;
    }
}

package cn.yumis.fight.modules.skills.api;
/** Server-tick based movement resource, independent of client-uploaded combat resources. */
public final class Stamina {
    public static final int MAXIMUM=100,COST=20,COOLDOWN=5,RECOVERY_DELAY=20,PROTECTION=200;
    public int value=MAXIMUM;
    public long lastUse=-RECOVERY_DELAY;
    private long lastRecovery=Long.MIN_VALUE;
    public boolean ready(long tick){return tick-lastUse>=COOLDOWN;}
    public boolean use(long tick,boolean exempt){
        if(!ready(tick)||!exempt&&value<COST)return false;
        if(!exempt)value-=COST;
        lastUse=tick;
        return true;
    }
    public boolean recover(long tick,boolean grounded,boolean exempt){
        if(tick==lastRecovery)return false;
        lastRecovery=tick;
        if(!grounded||exempt||tick-lastUse<RECOVERY_DELAY||value>=MAXIMUM)return false;
        value++;
        return true;
    }
}

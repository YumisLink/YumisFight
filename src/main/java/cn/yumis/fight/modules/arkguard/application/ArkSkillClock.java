package cn.yumis.fight.modules.arkguard.application;

final class ArkSkillClock {
    private long started,activeUntil,readyAt;
    private double recovery=1;
    private boolean cast;

    void reset(long now){
        started=activeUntil=0;
        readyAt=now;
        recovery=1;
        cast=false;
    }

    void start(long now,long duration,double cooldownSeconds){
        started=now;
        activeUntil=duration>0?now+duration:0;
        readyAt=now+duration+cooldownMillis(cooldownSeconds);
        cast=true;
    }

    boolean ready(long now){return now>=readyAt&&!active(now);}
    boolean active(long now){return activeUntil>now;}
    boolean hasCast(){return cast;}
    long end(){return activeUntil;}
    long duration(){return activeUntil-started;}
    long cooldownMillis(double seconds){return Math.round(seconds*1000/recovery);}
    long cooldownRemaining(long now){return Math.max(0,readyAt-Math.max(now,activeUntil));}

    void recovery(double value,long now){
        long cooldownTime=Math.max(now,activeUntil);
        if(value!=recovery&&readyAt>cooldownTime)
            readyAt=cooldownTime+Math.round((readyAt-cooldownTime)*recovery/value);
        recovery=value;
    }

    void accelerate(long millis,long now){
        if(!active(now)&&readyAt>now)readyAt=Math.max(now,readyAt-millis);
    }

    boolean finish(long now){
        if(activeUntil==0||now<activeUntil)return false;
        activeUntil=0;
        return true;
    }

    void interrupt(long now){
        if(active(now))readyAt=now+cooldownRemaining(now);
        activeUntil=0;
    }
}

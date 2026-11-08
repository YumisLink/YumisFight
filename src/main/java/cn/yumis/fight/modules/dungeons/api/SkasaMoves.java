package cn.yumis.fight.modules.dungeons.api;

/** Milliseconds and per-contact budgets shared by AI, animation and warnings. */
public enum SkasaMoves {
    PUNCH("punch",450,200,600,1700,2.5,1,false,-1,1,0),
    KICK("kick",550,200,550,1800,2.9,1,false,-1,1,0),
    NASRA_KICK("rush_kick",700,400,700,4500,6,.9,false,-1,1,0),
    NASRA_CYCLONE("ice_spin",900,1200,800,8000,3.5,.3,true,2,3,400),
    SPIDER_BITE("bite",450,200,600,1800,2.5,.8,false,-1,1,0),
    ICE_LINE("ice_line",700,650,600,3000,12,.8,true,2,1,0),
    HATCHLING_CHARGE("charge",650,450,700,3000,8,1,false,-1,1,0),
    CLAW("claw",650,200,800,3500,6,1,false,-1,1,0),
    SLAM("slam",1000,300,1000,9000,30,1.4,false,-1,1,0),
    TRIPLE("triple",1200,2600,1100,14000,30,.45,false,-1,3,1200),
    ROAR("roar",1000,1000,900,12000,8,.3,true,-1,3,400),
    VORTEX("vortex",1200,2400,1000,16000,3,.25,true,2,4,600),
    BREATH("breath",1800,2400,1200,18000,30,.35,true,2,4,600),
    ROUTE_BREATH("breath",1400,1200,400,0,30,.15,true,2,3,400);

    public final String animation;
    public final long windup,active,recovery,cooldown,interval;
    public final double range,coefficient;
    public final boolean magic;
    public final int element,hits;
    SkasaMoves(String animation,long windup,long active,long recovery,long cooldown,double range,double coefficient,boolean magic,int element,int hits,long interval){
        this.animation=animation;this.windup=windup;this.active=active;this.recovery=recovery;this.cooldown=cooldown;
        this.range=range;this.coefficient=coefficient;this.magic=magic;this.element=element;this.hits=hits;this.interval=interval;
    }
    public long duration(){return windup+active+recovery;}
    public long contactWindow(){return hits==1?active:Math.min(250,interval);}
    public boolean travels(){return this==NASRA_KICK||this==HATCHLING_CHARGE||this==ICE_LINE;}
    public boolean movesBody(){return this==NASRA_KICK||this==HATCHLING_CHARGE;}
    public String animation(int side){return this==CLAW||this==SLAM||this==TRIPLE?animation+(side<0?"_left":"_right"):animation;}
}

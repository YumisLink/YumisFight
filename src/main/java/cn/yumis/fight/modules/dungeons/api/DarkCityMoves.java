package cn.yumis.fight.modules.dungeons.api;

/** Approved first-pass values. Contact, warning and model animation share these milliseconds. */
public enum DarkCityMoves {
    CLAW("claw","FAN",350,150,450,1600,2.4,.8,false,3),
    SKULL("throw","LINE",500,800,400,2600,14,.8,false,3),
    ARROW("shoot","LINE",500,800,400,2400,16,.7,false,-1),
    SMASH("smash","FAN",650,200,650,2800,3.2,1.2,false,-1),
    TINGO_SLASH("slash","FAN",450,200,550,2000,3,1,false,-1),
    SHIELD_BASH("shield_bash","FAN",600,150,650,4000,2.6,1.1,false,-1),
    GUARD("guard","GUARD",500,1500,600,8000,0,0,false,-1),
    HAMMER("hammer","FAN",750,200,750,3000,3.5,1.2,false,-1),
    QUAKE("quake","WAVE",1000,700,900,6500,9,1.1,false,-1),
    SLASH("slash","FAN",650,200,650,2500,4.5,1.1,false,-1),
    KICK("kick","FAN",450,150,600,4000,2.8,.7,false,-1),
    SPIN("spin","CIRCLE",800,250,900,6000,4,1.2,false,-1),
    CHARGE("charge","CHARGE",1000,900,1100,8000,18,1.35,false,-1),
    NIGHTMARE("nightmare","LINE",1200,1000,1100,10000,18,1.1,true,3);

    public static final double GUARD_DEFENSE_INCREASE=.5;
    public static final int RANGED_GROUP_LIMIT=2;
    public final String animation,shape;
    public final long windup,active,recovery,cooldown;
    public final double range,coefficient;
    public final boolean magic;
    public final int element;
    DarkCityMoves(String animation,String shape,long windup,long active,long recovery,long cooldown,double range,double coefficient,boolean magic,int element){
        this.animation=animation;this.shape=shape;this.windup=windup;this.active=active;this.recovery=recovery;this.cooldown=cooldown;this.range=range;this.coefficient=coefficient;this.magic=magic;this.element=element;
    }
    public long duration(){return windup+active+recovery;}
    public boolean ranged(){return shape.equals("LINE")||shape.equals("WAVE");}
    public double height(){return this==ARROW?1.25:this==SKULL||this==NIGHTMARE?1.1:0;}
    public double width(){return this==NIGHTMARE?.7:this==QUAKE?1.15:.25;}
}

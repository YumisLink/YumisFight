package cn.yumis.fight.modules.dungeons.api;

/** Approved content parameters. Animation, telegraph and contact use these same milliseconds. */
public enum ForbiddenMoves {
    EXPLOSION("explode",0,180,0,0,2.2,.9,true,1),
    KNIFE("throw",450,700,250,1850,13,1,false,-1),
    STAB("stab",350,200,700,1700,2.5,1,false,-1),
    PRIEST_WAVE("shockwave",800,800,700,6000,4,1.1,true,3),
    DARK_KNIVES("dark_knives",600,650,750,3000,13,.4,true,3),
    BOSS_KNIVES("knife_burst",600,650,950,3000,15,.45,false,-1),
    BOSS_STAB("stab",450,200,600,2500,3,1.1,false,-1),
    BOSS_WAVE("shockwave",850,800,650,8000,6,1.4,true,3),
    PRIEST_SUMMON("summon",900,0,700,12000,0,0,true,3),
    BISHOP_SUMMON("summon",1000,0,700,14000,0,0,true,3),
    BOSS_SUMMON("summon",1000,0,700,14000,0,0,true,3),
    BARRIER("barrier",900,0,600,20000,0,0,true,3);
    public static final long BARRIER_LIFETIME=6000;
    public static final double CORE_HEALTH=4;
    public static final int SUMMON_LIMIT=6;
    public final String animation;
    public final long windup,active,recovery,cooldown;
    public final double range,coefficient;
    public final boolean magic;
    public final int element;
    ForbiddenMoves(String animation,long windup,long active,long recovery,long cooldown,double range,double coefficient,boolean magic,int element){
        this.animation=animation;this.windup=windup;this.active=active;this.recovery=recovery;this.cooldown=cooldown;this.range=range;this.coefficient=coefficient;this.magic=magic;this.element=element;
    }
    public long duration(){return windup+active+recovery;}
}

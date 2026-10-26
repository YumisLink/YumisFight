package cn.yumis.fight.rules;

import cn.yumis.fight.api.SkillParameters;
import cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;

/** Zero-defense, zero-resistance, non-critical panel damage; never rolls combat RNG. */
public final class SkillPreview {
    private SkillPreview(){}
    public static Stats currentStats(Stats equipment,Models.Runtime resources,long now){
        Stats stats=Models.copy(equipment,Stats.class);
        if(resources.buffs.getOrDefault("OVERDRIVE",0L)>now){
            double power=resources.absorption.getOrDefault("overdrivePower",0d);
            stats.values.put(Stat.PHYSICAL_ATTACK,stats.get(Stat.PHYSICAL_ATTACK)*(1+power));
        }
        return stats;
    }
    public record Damage(double minimum,double maximum){
        public Damage times(double count){return new Damage(minimum*count,maximum*count);}
        public Damage plus(Damage other){return new Damage(minimum+other.minimum,maximum+other.maximum);}
    }
    public record Preview(int rank,int slashes,Damage hit,Damage finisher,Damage total,Damage shortSwordWave,int mp,double cooldown,SkillParameters.Snapshot parameters){}
    public static double mastery(Progress progress,Stats stats){
        int rank=stats.effective(progress,Skill.MASTERY);
        return rank<=0?0:Math.pow(1.12,rank-1);
    }
    public static double cooldown(Skill skill,Stats stats){
        double value=skill.cooldown/(1+stats.modifiers.getOrDefault("cooldownRecovery",0d));
        return skill.ordinal()<3?value*(1-stats.modifiers.getOrDefault("basicSkillCooldown",0d)):value;
    }
    public static Damage damage(Progress progress,Stats stats,Skill skill,double coefficient,boolean passive){
        boolean magic=!progress.swordmaster&&(skill==Skill.GHOST||skill==Skill.WAVE);
        int[] elements=skill==Skill.GHOST?new int[]{3}:new int[0];
        double mastery=mastery(progress,stats);
        var low=CombatRules.panel(stats,magic,elements,coefficient,!passive,false);
        var high=CombatRules.panel(stats,magic,elements,coefficient,!passive,true);
        return new Damage(CombatRules.skillDamage(low,stats,skill,mastery,false,passive,1).total(),CombatRules.skillDamage(high,stats,skill,mastery,false,passive,1).total());
    }
    public static Preview resolve(Progress progress,Stats stats,Skill skill,SkillParameters.Snapshot parameters){
        int rank=stats.effective(progress,skill);
        int count=switch(skill){case DANCE->SkillSchedule.dance(parameters).slashes();case TRIPLE,DRAGON,STORM->(int)parameters.get("slashes",1);case MASTERY,OVERDRIVE->0;default->1;};
        Damage hit=count==0?new Damage(0,0):damage(progress,stats,skill,parameters.get("damage",0),false);
        Damage finisher=switch(skill){case DANCE,STORM,RISING->damage(progress,stats,skill,parameters.get("finisher",0),false);default->new Damage(0,0);};
        Damage wave=new Damage(0,0);
        if(stats.weaponType.equals("SHORTSWORD")&&mastery(progress,stats)>0&&(skill==Skill.TRIPLE||skill==Skill.DRAW||skill==Skill.DANCE))
            wave=damage(progress,stats,skill,parameters.get("damage",0)*.2*mastery(progress,stats),true);
        return new Preview(rank,count,hit,finisher,hit.times(count).plus(finisher),wave,skill.mp(Math.max(1,rank)),cooldown(skill,stats),parameters);
    }
}

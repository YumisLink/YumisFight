package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;

/** Fury and transformation arithmetic shared by authoritative payments and client previews. */
public final class DemonHunterRules {
    public static final int MAX_FURY=100,COLOR=0x86ed35;
    private DemonHunterRules(){}
    public static int cost(String id){return id.equals(DemonHunterSkills.CHAOS)?20:id.equals(DemonHunterSkills.RUSH)?10:id.equals(DemonHunterSkills.DANCE)?30:id.equals(DemonHunterSkills.BEAM)||id.equals(DemonHunterSkills.HUNT)?50:id.equals(DemonHunterSkills.META)?100:0;}
    public static Stats transformed(Stats source){
        if(source.modifiers.getOrDefault("demonTransformed",0d)>0)return source;
        Stats result=source.copy();result.mut(Stat.PHYSICAL_ATTACK,.30);result.mut(Stat.MAGICAL_ATTACK,.30);
        result.modifiers.merge("allSkillAttackProduct",1.30,(a,b)->a*b);result.modifiers.merge("lifeStealRateAdd",.10,Double::sum);result.modifiers.put("demonTransformed",1d);return result;
    }
    public static Stats current(Stats source,Progress progress,DemonHunterState state,long now){
        if(!progress.baseJob().equals(JobCatalog.DEMON_HUNTER))return source;
        var result=source.copy();if(result.weaponType.equals("WARGLAIVE")){result.modifiers.put("basicSlashes",2d);result.modifiers.put("basicStrikeCoefficient",.5);result.modifiers.put("basicContactOffset",.25);result.modifiers.put("basicDark",1d);}
        return state.metamorphosisUntil>now?transformed(result):result;
    }
    public static boolean sight(Stats stats,Progress progress){return SkillLearning.effective(stats,progress,SkillCatalog.require(DemonHunterSkills.SIGHT))>0;}
    public static String title(SkillDefinition skill,Stats stats){return skill.id.equals(DemonHunterSkills.DANCE)&&stats.modifiers.getOrDefault("demonTransformed",0d)>0?"死亡横扫":skill.title;}
}

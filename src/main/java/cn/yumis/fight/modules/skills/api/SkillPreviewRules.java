package cn.yumis.fight.modules.skills.api;

import java.util.*;
import java.util.function.ToIntFunction;

/** Preview-specific content. The shared damage calculator has no per-skill count or finisher branches. */
public final class SkillPreviewRules {
    public record Definition(ToIntFunction<SkillParameters.Snapshot> hits,boolean finisher,boolean shortSwordWave,boolean magicBeforePromotion,int element){}
    private static final Map<SkillDefinition,Definition> DEFINITIONS=new LinkedHashMap<>();
    static {
        for(String id:List.of(MageSkills.STAR,MageSkills.FANG,MageSkills.JACK,MageSkills.HERDER))register(MageSkills.skill(id),new Definition(p->1,false,false,!id.equals(MageSkills.FANG),id.equals(MageSkills.JACK)?1:-1));
        register(SkillCatalog.BLINK,new Definition(p->0,false,false,true,-1));
        register(SkillCatalog.GHOST,new Definition(p->1,false,false,true,3));
        register(SkillCatalog.TRIPLE,new Definition(p->(int)p.get("slashes",1),false,true,false,-1));
        register(SkillCatalog.WAVE,new Definition(p->1,false,false,true,-1));
        register(SkillCatalog.MASTERY,new Definition(p->0,false,false,false,-1));
        register(SkillCatalog.OVERDRIVE,new Definition(p->0,false,false,false,-1));
        register(SkillCatalog.RISING,new Definition(p->1,true,false,false,-1));
        register(SkillCatalog.DRAW,new Definition(p->1,false,true,false,-1));
        register(SkillCatalog.DRAGON,new Definition(p->(int)p.get("slashes",1),false,false,false,-1));
        register(SkillCatalog.DANCE,new Definition(p->SkillSchedule.dance(p).slashes(),true,true,false,-1));
        register(SkillCatalog.STORM,new Definition(p->(int)p.get("slashes",1),true,false,false,-1));
    }
    private SkillPreviewRules(){}
    public static void register(SkillDefinition skill,Definition definition){if(DEFINITIONS.putIfAbsent(skill,Objects.requireNonNull(definition))!=null)throw new IllegalArgumentException("Duplicate skill preview: "+skill);}
    public static Definition require(SkillDefinition skill){
        var definition=DEFINITIONS.get(skill);
        if(definition==null){var content=CareerSkills.content(skill.id);if(content!=null)return new Definition(p->skill.id.equals(CareerSkills.SWORD)?5:(int)p.get("slashes",content.hits()),content.finisher()||skill.id.equals(CareerSkills.SWORD),false,cn.yumis.fight.modules.character.api.JobCatalog.style(skill.requiredJob).magic(),content.element());}
        if(definition==null)throw new IllegalArgumentException("Missing skill preview: "+skill);return definition;
    }
    public static boolean magic(SkillDefinition skill,boolean promoted){return skill!=null&&!promoted&&require(skill).magicBeforePromotion();}
    public static int[] elements(SkillDefinition skill){return skill!=null&&require(skill).element()>=0?new int[]{require(skill).element()}:new int[0];}
}

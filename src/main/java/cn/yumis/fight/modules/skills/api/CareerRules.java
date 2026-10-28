package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.character.api.*;
import java.util.*;

/** Pure, shared arithmetic for career sources and resource snapshots. */
public final class CareerRules {
    public static final int EYE_COLOR=0xb9a4ff;
    private static final com.google.gson.Gson JSON=new com.google.gson.Gson();
    private CareerRules(){}
    public static Stats current(Stats base,Progress progress,CareerState state,double life,double red){
        Stats result=JSON.fromJson(JSON.toJson(base),Stats.class);
        int keiga=SkillLearning.effective(result,progress,SkillCatalog.require(CareerSkills.KEIGA));
        int brands=SkillLearning.effective(result,progress,SkillCatalog.require(CareerSkills.BRANDS));
        if(keiga>0)result.mut(Stat.MAGICAL_ATTACK,.15*CareerSkills.growth(keiga));
        if(brands>0)result.mut(Stat.MAGICAL_ATTACK,.15*CareerSkills.growth(brands));
        int frenzy=SkillLearning.effective(result,progress,SkillCatalog.require(CareerSkills.FRENZY));
        if(state.frenzy&&frenzy>0){
            result.mut(Stat.PHYSICAL_ATTACK,.15*CareerSkills.growth(frenzy));
            result.extra(Stat.ATTACK_SPEED,15);result.mut(Stat.MOVEMENT_SPEED,.10);
            result.mut(Stat.PHYSICAL_DEFENSE,-.15);result.mut(Stat.MAGICAL_DEFENSE,-.15);
            result.modifier("final").mut+=CareerSkills.lowHealthMut(life);
            result.modifiers.put("basicSlashes",2d);result.modifiers.put("careerFrenzy",1d);
        }
        if(red>0){result.mut(Stat.STRENGTH,red);result.mut(Stat.INTELLIGENCE,red);}
        return result;
    }
    public static double monsterTaken(Progress progress,Stats stats){
        double value=JobCatalog.style(progress.jobId()).damageTaken();
        if(SkillLearning.effective(stats,progress,SkillCatalog.require(CareerSkills.KEIGA))>0)value*=.9;
        return value;
    }
    public static SkillParameters.Snapshot resourceSnapshot(SkillParameters.Snapshot source,int brands,int blood,int fieldRank,boolean terminal){
        var values=new LinkedHashMap<>(source.values());double g=CareerSkills.growth(source.rank());String id=source.skill().id;
        values.put("resource",(double)brands);values.put("blood",(double)blood);values.put("fieldRank",(double)fieldRank);values.put("terminal",terminal?1d:0d);
        if(id.equals(CareerSkills.ORB)){int hits=CareerSkills.orbHits(brands);double total=(3+.4*brands)*g;values.put("slashes",(double)hits);values.put("damage",total/hits);values.put("finisher",0d);values.put("interval",3d/hits);}
        if(id.equals(CareerSkills.AGNI)){double total=(4+1.6*brands)*g;values.put("damage",total*.6/8);values.put("finisher",total*.4);}
        if(id.equals(CareerSkills.SWORD)){double total=(24+1.6*blood)*g;values.put("damage",total);values.put("previewDamage",total*.7/5);values.put("finisher",total*.3);}
        if(id.equals(CareerSkills.BLACHE))values.put("skillMut",CareerSkills.blacheMut(fieldRank));
        return new SkillParameters.Snapshot(source.skill(),source.rank(),values);
    }
    public static boolean magic(SkillDefinition skill,Progress progress){
        if(skill!=null&&skill.magicTag)return true;
        if(!progress.hasJob(JobCatalog.BASE))return JobCatalog.style(progress.jobId()).magic();
        return skill!=null&&SkillPreviewRules.require(skill).magicBeforePromotion();
    }
    public static boolean producesBrand(String id){return Set.of(SkillCatalog.WAVE.id,CareerSkills.CRESCENT,CareerSkills.ICE,CareerSkills.FIRE).contains(id);}
    public static Stats castDamage(Stats source,SkillParameters.Snapshot parameters){
        double desired=parameters.get("skillMut",0),applied=source.modifiers.getOrDefault("careerCastMut",0d);
        if(desired==applied)return source;
        Stats result=JSON.fromJson(JSON.toJson(source),Stats.class);result.modifier("final").mut+=desired-applied;result.modifiers.put("careerCastMut",desired);return result;
    }
}

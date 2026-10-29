package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/** Mage content and its reference choreography. Families are not random affix releases. */
public final class MageSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String STAR=NS+"magic_star",FANG=NS+"dragon_fang",JACK=NS+"jack_bomb",HERDER=NS+"herder";
    private MageSkills(){}
    public static boolean contains(String id){return Set.of(STAR,FANG,JACK,HERDER).contains(id);}
    public static SkillDefinition skill(String id){return SkillCatalog.require(id);}
    static void install(){
        add(STAR,"魔法星弹","MAGIC_CRAFT",1,.06,3,true,12,Map.of("pierces",3d));
        add(FANG,"龙牙","MARTIAL",5,12d/156,5,false,5,Map.of("width",1.5));
        add(JACK,"杰克爆弹","ELEMENT",5,16d/156,8,true,12,Map.of("radius",3d));
        add(HERDER,"契约召唤：赫尔德","SUMMON",10,45d/226,20,true,16,Map.of("summonDuration",120d,"meleeInterval",1.2,"stoneInterval",4d,"stoneGap",.2));
        skill(HERDER).damageParameter("stoneDamage",1.2);
    }
    private static void add(String id,String title,String family,int unlock,double mp,double cooldown,boolean magic,double range,Map<String,Double> extra){
        var parameters=new LinkedHashMap<>(extra);parameters.put("range",range);parameters.put("duration",.4);parameters.put("slashes",1d);
        var definition=new SkillDefinition(id,title,family,"",unlock,15,mp,cooldown,1,false,parameters);
        definition.baseJob=JobCatalog.MAGE;definition.magicDamage=magic;definition.magicTag=magic;SkillCatalog.register(definition);
    }
    public record Attack(long at,boolean stone,int index){}
    /** Shared near-target timing, with throwing taking precedence over melee. */
    public static List<Attack> attacks(int rank,long duration,double speed){
        var result=new ArrayList<Attack>();long melee=Math.max(50,Math.round(1200/speed)),throwing=Math.max(50,Math.round(4000/speed)),gap=Math.max(10,Math.round(200/speed));long nextStone=0,nextMelee=0;int index=0;
        for(long at=0;at<duration;){
            if(rank>=5&&at>=nextStone){int count=rank>=10?3:1;for(int i=0;i<count&&at+i*gap<duration;i++)result.add(new Attack(at+i*gap,true,index++));nextStone=at+throwing;at+=Math.max(Math.max(50,Math.round(300/speed)),count*gap);nextMelee=at;}
            else if(at>=nextMelee){result.add(new Attack(at,false,index++));nextMelee=at+melee;at+=Math.max(50,Math.round(300/speed));}
            else at=Math.min(nextMelee,rank>=5?nextStone:Long.MAX_VALUE);
        }
        return result;
    }
    static void balance(){
        for(String id:List.of(STAR,FANG,JACK))SkillBalance.register(skill(id),new SkillBalanceProfile(1,0,false,false,0,(s,r,c)->List.of(new SkillBalanceProfile.Hit(200,s.parameters(r).get("damage"),false))));
        SkillBalance.register(skill(HERDER),new SkillBalanceProfile(1,120,false,false,0,(s,r,c)->attacks(r,120000,1).stream().map(a->new SkillBalanceProfile.Hit(a.at(),s.parameters(r).get(a.stone()?"stoneDamage":"damage"),false)).toList()));
    }
}

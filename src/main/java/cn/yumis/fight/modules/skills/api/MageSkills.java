package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import java.util.*;

/** Mage content and its reference choreography. Families are not random affix releases. */
public final class MageSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String STAR=NS+"magic_star",FANG=NS+"dragon_fang",JACK=NS+"jack_bomb",HERDER=NS+"herder";
    private static final Map<String,Map<String,Double>> CAREER_SCALES=new LinkedHashMap<>();
    private MageSkills(){}
    public static boolean contains(String id){return Set.of(STAR,FANG,JACK,HERDER).contains(id)||SummonerSkills.contains(id)||SunMoonSkills.contains(id);}
    public static SkillDefinition skill(String id){return SkillCatalog.require(id);}
    static void careerScales(String job,double ordinary,double herder){
        if(CAREER_SCALES.putIfAbsent(job,Map.of(STAR,ordinary,FANG,ordinary,JACK,ordinary,HERDER,herder))!=null)throw new IllegalArgumentException("Duplicate mage career scale: "+job);
    }
    private static SkillParameters.Snapshot careerDamage(SkillParameters.Snapshot input,Progress progress){
        double scale=CAREER_SCALES.getOrDefault(progress.jobId(),Map.of()).getOrDefault(input.skill().id,1d);
        if(scale==1)return input;
        var values=new LinkedHashMap<>(input.values());for(String key:input.skill().damageParameters())values.computeIfPresent(key,(k,v)->v*scale);
        return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
    }
    static void install(){
        add(STAR,"魔法星弹","MAGIC_CRAFT",1,.06,1.2,true,12,Map.of("pierces",3d));
        add(FANG,"龙牙","MARTIAL",5,12d/156,5,false,5,Map.of("width",1.5));
        add(JACK,"杰克爆弹","ELEMENT",5,16d/156,8,true,12,Map.of("radius",3d));
        skill(JACK).chantSeconds=.5;
        skill(STAR).projectile=new SkillDefinition.Projectile(20,3,false,500,false);skill(JACK).projectile=new SkillDefinition.Projectile(15,1,true,600,false);
        add(HERDER,"契约召唤：赫尔德","SUMMON",10,45d/226,20,true,16,Map.of("summonDuration",120d,"meleeInterval",1.2,"stoneInterval",4d,"stoneGap",.2));
        skill(HERDER).damageParameter("stoneDamage",1.2);
        skill(STAR).formParameter("projectileSpeed",20);
        skill(FANG).formParameter("fuseMillis",200);
        skill(JACK).formParameter("projectileSpeed",15);
        skill(HERDER).formParameter("stoneVolley",3).formParameter("stoneUnlockRank",5).formParameter("heavyEvery",3).formParameter("bombEvery",3);
    }
    private static void add(String id,String title,String family,int unlock,double mp,double cooldown,boolean magic,double range,Map<String,Double> extra){
        var parameters=new LinkedHashMap<>(extra);parameters.put("range",range);parameters.put("duration",.4);parameters.put("slashes",1d);
        var definition=new SkillDefinition(id,title,family,"",unlock,15,mp,cooldown,1,false,parameters);
        definition.baseJob=JobCatalog.MAGE;definition.magicDamage=magic;definition.magicTag=magic;SkillCatalog.register(definition);
    }
    public record Attack(long at,boolean stone,int index){}
    /** Shared near-target timing, with throwing taking precedence over melee. Rhythm keys are equipment-modifiable. */
    public static List<Attack> attacks(SkillParameters.Snapshot p,long duration,double speed){
        int rank=p.rank(),unlock=Math.max(1,(int)p.get("stoneUnlockRank",5)),volley=Math.max(1,(int)p.get("stoneVolley",3));
        long melee=Math.max(50,Math.round(p.get("meleeInterval",1.2)*1000/speed)),throwing=Math.max(50,Math.round(p.get("stoneInterval",4)*1000/speed)),gap=Math.max(10,Math.round(p.get("stoneGap",.2)*1000/speed));long nextStone=0,nextMelee=0;int index=0;
        var result=new ArrayList<Attack>();
        for(long at=0;at<duration;){
            if(rank>=unlock&&at>=nextStone){int count=rank>=10?volley:1;for(int i=0;i<count&&at+i*gap<duration;i++)result.add(new Attack(at+i*gap,true,index++));nextStone=at+throwing;at+=Math.max(Math.max(50,Math.round(300/speed)),count*gap);nextMelee=at;}
            else if(at>=nextMelee){result.add(new Attack(at,false,index++));nextMelee=at+melee;at+=Math.max(50,Math.round(300/speed));}
            else at=Math.min(nextMelee,rank>=unlock?nextStone:Long.MAX_VALUE);
        }
        return result;
    }
    static void balance(){
        // Keep the original reference period so the shorter cooldown does not reduce damage per shot.
        for(String id:List.of(STAR,FANG,JACK))SkillBalance.register(skill(id),new SkillBalanceProfile(1,id.equals(STAR)?3:0,false,false,0,(s,r,c)->List.of(new SkillBalanceProfile.Hit(200,s.parameters(r).get("damage"),false))));
        SkillBalance.register(skill(HERDER),new SkillBalanceProfile(1,120,false,false,0,(s,r,c)->SummonMoves.cues(SummonKind.HERDER,new SkillParameters.Snapshot(s,r,s.parameters(r)),120000,1).stream().map(a->new SkillBalanceProfile.Hit(a.at(),s.parameters(r).get(a.stone()?"stoneDamage":"damage")*a.share(),false)).toList()));
        for(String id:List.of(STAR,FANG,JACK,HERDER))SkillBalance.registerVariant(skill(id),(input,progress,stats)->careerDamage(input,progress));
    }
}

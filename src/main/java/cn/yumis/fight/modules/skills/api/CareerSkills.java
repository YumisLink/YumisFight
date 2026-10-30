package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/** Approved career content. Scheduling, transport and attribute arithmetic remain content agnostic. */
public final class CareerSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String KEIGA=NS+"keiga",FIELD=NS+"ghost_field",SOUL_SLASH=NS+"soul_slash",TOMBSTONE=NS+"tombstone",PHANTOM=NS+"phantom_slash",KALLA=NS+"kalla",BLACHE=NS+"blache";
    public static final String BRANDS=NS+"wave_brands",ORB=NS+"ghost_orb",CRESCENT=NS+"spirit_crescent",ICE=NS+"ice_wave",FIRE=NS+"fire_wave",AGNI=NS+"agni",EYE=NS+"wave_eye";
    public static final String FRENZY=NS+"frenzy",CRASH=NS+"mountain_crash",HAND=NS+"blood_hand",RAGE=NS+"blood_rage",BLADE=NS+"blood_blade",ERUPTION=NS+"blood_eruption",SWORD=NS+"blood_sword";
    public record Content(SkillDefinition skill,int element,int color,int hits,boolean finisher,boolean charging,boolean invulnerable,double hpCost){}
    private static final Map<String,Content> CONTENT=new LinkedHashMap<>();
    private CareerSkills(){}
    static void install(){
        AsuraRules.install();
        add(KEIGA,"残影之凯贾",JobCatalog.SOUL_BENDER,"GHOST",20,0,0,0,true,3,0x8855cc,0,0,0,0,0,false,false,0);
        add(FIELD,"鬼神之阵",JobCatalog.SOUL_BENDER,"GHOST",20,.10,20,0,false,3,0x8b52be,5,1,0,1,12,false,false,0);
        add(SOUL_SLASH,"噬灵鬼斩",JobCatalog.SOUL_BENDER,"GHOST",25,0,0,2,true,3,0xb56ee8,3,0,0,0,0,false,false,0);
        add(TOMBSTONE,"死亡墓碑",JobCatalog.SOUL_BENDER,"GHOST",30,.14,15,.65,false,3,0x88749e,4,10,0,.2,2,false,false,0);
        add(PHANTOM,"鬼影闪",JobCatalog.SOUL_BENDER,"GHOST",35,.18,22,8.8,false,3,0x9e65ee,8,1,0,.25,.4,false,false,0);
        add(KALLA,"冥炎之卡洛",JobCatalog.SOUL_BENDER,"GHOST",40,.22,30,2,false,3,0x7043ac,6,3,0,.3,20,false,false,0);
        add(BLACHE,"第七鬼神：邪神之怖拉修",JobCatalog.SOUL_BENDER,"GHOST",50,.35,90,12,false,3,0x542677,8,1,0,1.2,2,false,true,0);
        add(BRANDS,"波动刻印",JobCatalog.ASURA,"WAVE",20,0,0,0,true,-1,0xe0c975,0,0,0,0,0,false,false,0);
        add(ORB,"鬼印珠",JobCatalog.ASURA,"WAVE",20,.08,8,3,false,-1,0xc9bbeb,10,28,0,.5,5,false,false,0);
        add(CRESCENT,"邪光斩",JobCatalog.ASURA,"WAVE",25,.12,12,2,false,-1,0xe8e0b5,12,3,0,.1,3,true,false,0);
        add(ICE,"冰刃·波动剑",JobCatalog.ASURA,"WAVE",30,.14,15,2,false,2,0x75d8f5,12,3,0,.2,.6,false,false,0);
        add(FIRE,"爆炎·波动剑",JobCatalog.ASURA,"WAVE",35,.18,22,8.8,false,1,0xff923e,10,1,0,.2,3,false,false,0);
        add(AGNI,"不动明王阵",JobCatalog.ASURA,"WAVE",40,.22,30,.528,false,-1,0xf4b552,5,10,3.52,.85/3,3,false,false,0);
        add(EYE,"暗天波动眼",JobCatalog.ASURA,"WAVE",50,.35,90,16,false,-1,0xd7b8ee,16,1,0,.8,20,false,false,0);
        add(FRENZY,"血之狂暴",JobCatalog.BERSERKER,"BLOOD",20,.10,5,0,false,-1,0xd52e47,0,0,0,0,0,false,false,0);
        add(CRASH,"崩山击",JobCatalog.BERSERKER,"BLOOD",20,.06,8,.6,false,-1,0xe75145,3,1,2,.5,.7,false,false,0);
        add(HAND,"嗜魂之手",JobCatalog.BERSERKER,"BLOOD",25,.12,12,4,false,-1,0xc92f43,3,1,0,.35,.6,false,false,0);
        add(RAGE,"怒气爆发",JobCatalog.BERSERKER,"BLOOD",30,.14,15,1.3,false,-1,0xe33450,4,5,0,.15,.8,false,false,.04);
        add(BLADE,"血气之刃",JobCatalog.BERSERKER,"BLOOD",35,.18,22,2.2,false,-1,0xf14955,5,1,6.6,.35,.65,false,false,.05);
        add(ERUPTION,"崩山裂地斩",JobCatalog.BERSERKER,"BLOOD",40,.22,30,2,false,-1,0xee5945,6,4,4,.2,1.2,false,false,.08);
        add(SWORD,"魔狱血刹",JobCatalog.BERSERKER,"BLOOD",50,.35,90,24,false,-1,0xbf1738,StormChoreography.RADIUS,1,0,.6,60,false,false,.10);
        SkillCatalog.require(ORB).damageParameter("perBrandDamage",.4);
        SkillCatalog.require(SWORD).damageParameter("perBloodDamage",1.6);
        SkillCatalog.require(FIELD).damageParameter("iceDot",.25).damageParameter("darkDot",.20);
        SkillCatalog.require(KALLA).damageParameter("flameDot",.10);
    }
    private static void add(String id,String title,String job,String family,int unlock,double mp,double cd,double coefficient,boolean passive,int element,int color,double range,int hits,double finish,double interval,double duration,boolean charging,boolean invulnerable,double hp){
        int sp=unlock==20?30:unlock==25?40:unlock<40?50:unlock==40?60:100;
        var skill=SkillCatalog.register(new SkillDefinition(id,title,family,job,unlock,sp,mp,cd,coefficient,passive,Map.of("range",range,"slashes",(double)Math.max(1,hits),"finisher",finish,"interval",interval,"duration",duration,"hpCost",hp)));
        CONTENT.put(id,new Content(skill,element,color,hits,finish>0,charging,invulnerable,hp));
    }
    public static void register(Content content){if(CONTENT.putIfAbsent(content.skill().id,content)!=null)throw new IllegalArgumentException("Duplicate career content");}
    public static boolean contains(String id){SkillCatalog.all();return CONTENT.containsKey(id);}
    public static Content content(String id){SkillCatalog.all();return CONTENT.get(id);}
    public static Collection<Content> all(){SkillCatalog.all();return List.copyOf(CONTENT.values());}
    public static double growth(int rank){return Math.pow(1.12,Math.max(0,rank-1));}
    public static int ghosts(int rank){return rank<=0?0:1+(rank>=3?1:0)+(rank>=8?1:0)+(rank>=15?1:0);}
    public static double redBonus(int rank){return rank<=0?0:.10+.01*(rank-1);}
    public static double blacheMut(int rank){return rank<=0?0:ghosts(rank)+.05*(rank-1);}
    public static double lowHealthMut(double fraction){return fraction<=.4?.20:fraction<=.7?.10:0;}
    public static boolean persistent(String id){return Set.of(FIELD,KALLA,EYE,SWORD,ORB,CRESCENT,FIRE).contains(id);}
    public static int orbHits(int brands){return 3+5*Math.clamp(brands,0,5);}
    public static double controlLimit(String id){return Map.of(FIELD,.5,PHANTOM,.5,ICE,1.5,AGNI,3d,HAND,.3).getOrDefault(id,0d);}
    public static int hitLimit(String id,boolean terminal){
        if(id.equals(FIELD))return 27;
        if(id.equals(KALLA))return terminal?2:127;
        if(id.equals(SWORD))return terminal?5:-1;
        if(id.equals(EYE))return terminal?0:AsuraRules.GEAR_INDEX;
        if(id.equals(ERUPTION))return 4;
        if(id.equals(AGNI))return SkillResourceRules.agniHits(5);
        var content=content(id);return content==null?-1:content.hits()-1+(content.finisher()?1:0);
    }
    public static boolean canPayHealth(double fraction,double maximum,double cost){return cost==0||maximum>0&&(fraction-cost)*maximum>=1-1e-9;}
}

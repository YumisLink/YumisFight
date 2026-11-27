package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/** Approved demon hunter content. Raw coefficients describe relative contacts, not displayed percentages. */
public final class DemonHunterSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String CHAOS=NS+"chaos_strike",GLAIVE=NS+"throw_glaive",SIGHT=NS+"demon_sight",AURA=NS+"immolation_aura",BEAM=NS+"eye_beam",RUSH=NS+"fel_rush",DANCE=NS+"blade_dance",HUNT=NS+"the_hunt",META=NS+"metamorphosis";
    public static final String BASIC_ATTACK=NS+"warglaive_attack";
    public static final List<String> ALL=List.of(CHAOS,GLAIVE,SIGHT,AURA,BEAM,RUSH,DANCE,HUNT,META);
    private DemonHunterSkills(){}
    public static boolean contains(String id){return ALL.contains(id);}
    static void install(){
        SkillVisualRules.register(BASIC_ATTACK,new SkillVisualRules.Policy(false,"","",false));
        add(CHAOS,"混乱打击",false,1,15,1,1,false,Map.of("range",3d,"width",3d,"slashes",2d,"interval",.15,"duration",.3));
        var glaive=add(GLAIVE,"利刃投掷",false,5,15,8,1,false,Map.of("range",16d,"slashes",1d,"duration",.8));glaive.projectile=new SkillDefinition.Projectile(20,1,false,250,false);
        var sight=add(SIGHT,"恶魔视觉",false,10,15,0,0,false,Map.of("slashes",0d));sight.maximumLearnedRank=1;sight.attackTypeRanks=false;
        add(AURA,"献祭光环",false,15,15,14,1,true,Map.of("range",4d,"radius",4d,"duration",6d,"interval",1d,"slashes",6d));
        add(BEAM,"眼棱",false,30,50,30,1,true,Map.of("range",16d,"width",3d,"duration",2d,"rise",.2,"interval",.25,"slashes",8d)).formParameter("beamWidth",1.5).formParameter("beamDamageTaken",.2);
        add(RUSH,"邪能冲撞",true,20,30,12,1,false,Map.of("range",12d,"duration",.4,"slashes",1d));
        add(DANCE,"刃舞",true,25,40,12,.2,false,Map.of("range",3d,"radius",3d,"duration",2d,"slashes",2d,"finisher",.6)).formParameter("danceRadius",3).formParameter("dancePeak",5);
        add(HUNT,"恶魔追击",true,40,60,60,.05,false,Map.of("range",DemonHunterChoreography.HUNT_RANGE,
            "duration",DemonHunterChoreography.HUNT_RANGE/DemonHunterChoreography.HUNT_SPEED,
            "slashes",8d,"interval",.1,"finisher",.6)).formParameter("huntSpeed",DemonHunterChoreography.HUNT_SPEED);
        var meta=add(META,"恶魔变形",true,50,100,90,1,true,Map.of("range",16d,"radius",4d,"duration",1d,"slashes",1d)).formParameter("transformSeconds",20);meta.groundTargeted=true;
        for(String id:ALL)if(!id.equals(SIGHT)){var s=SkillCatalog.require(id);SkillDamagePhases.register(id,"strike",Boolean.TRUE.equals(s.magicDamage),false);}
        SkillDamagePhases.register(AURA,"pulse",true,true);SkillDamagePhases.register(BEAM,"pulse",true,false);SkillDamagePhases.register(HUNT,"impact",true,false);
    }
    private static SkillDefinition add(String id,String title,boolean advanced,int level,int sp,double cd,double damage,boolean magic,Map<String,Double> values){
        var s=new SkillDefinition(id,title,"HAVOC",advanced?JobCatalog.HAVOC:"",level,sp,0,cd,damage,id.equals(SIGHT),values);s.baseJob=JobCatalog.DEMON_HUNTER;s.magicDamage=magic;s.magicTag=magic;return SkillCatalog.register(s);
    }
    static void variants(){
        SkillParameters.registerVariant(SkillCatalog.require(DANCE),(p,progress,stats)->{if(stats.modifiers.getOrDefault("demonTransformed",0d)<=0)return p;var values=new LinkedHashMap<>(p.values());values.merge("slashes",2d,Double::sum);return new SkillParameters.Snapshot(p.skill(),p.rank(),values);});
    }
    public static void previews(){
        for(String id:ALL){var s=SkillCatalog.require(id);SkillPreviewRules.register(s,new SkillPreviewRules.Definition(p->(int)p.get("slashes",1d),id.equals(DANCE)||id.equals(HUNT),false,Boolean.TRUE.equals(s.magicDamage),3));}
        SkillPreviewRules.castStats(SkillCatalog.require(META),DemonHunterRules::transformed);
        SkillPreviewRules.phases(SkillCatalog.require(AURA),"pulse","");SkillPreviewRules.phases(SkillCatalog.require(HUNT),"strike","impact");
        SkillPreviewRules.total(SkillCatalog.require(AURA),(p,s,v,e)->SkillPreview.phaseDamage(p,s,v.skill(),v.get("damage",0),"pulse",e).times((int)v.get("slashes",6)));
        SkillPreviewRules.total(SkillCatalog.require(HUNT),(p,s,v,e)->SkillPreview.phaseDamage(p,s,v.skill(),v.get("finisher",0),"impact",e).plus(SkillPreview.phaseDamage(p,s,v.skill(),v.get("damage",0),"strike",e).times((int)v.get("slashes",8))));
        SkillPreviewRules.finisherLabel(SkillCatalog.require(HUNT),"暗魔法首击");
    }
    public static void presentation(){for(String id:ALL){boolean move=id.equals(RUSH)||id.equals(HUNT)||id.equals(META)||id.equals(DANCE);int cancel=move?SkillPresentation.CANCEL_MOBILITY:SkillPresentation.CANCEL_NORMAL;int stiff=id.equals(META)||id.equals(DANCE)||id.equals(BEAM)||id.equals(HUNT)?SkillPresentation.STIFF_SUPER:SkillPresentation.STIFF_NORMAL;SkillPresentation.register(SkillCatalog.require(id),new SkillPresentation.Traits(false,id.equals(BEAM)||id.equals(HUNT),true,false,0,0,DemonHunterRules.COLOR,false,c->id.equals(AURA)?6500:id.equals(BEAM)?DemonHunterChoreography.BEAM_DURATION:id.equals(HUNT)?5200:id.equals(META)?1500:id.equals(DANCE)?2500:1200,move,cancel,stiff));}}
}

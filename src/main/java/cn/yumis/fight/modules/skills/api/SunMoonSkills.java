package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import java.util.*;

/** Sun/moon content; derived coefficients share the calibrated Sunfire baseline. */
public final class SunMoonSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String CYCLE=NS+"sun_moon_cycle",MOON=NS+"moon_flow",SUN=NS+"sunfire",REFLUX=NS+"moon_reflux",FLARE=NS+"solar_flare",CORONA=NS+"corona",STARFALL=NS+"moon_starfall",PARADOX=NS+"paradox";
    public static final List<String> ALL=List.of(CYCLE,MOON,SUN,REFLUX,FLARE,CORONA,STARFALL,PARADOX);
    private SunMoonSkills(){}
    public static boolean contains(String id){return ALL.contains(id);}
    static void install(){
        var cycle=add(CYCLE,"日月流转",20,30,0,0,0,0,0,SkillDefinition.Aspect.NONE,true);cycle.automaticByJob=true;
        add(MOON,"月流",20,30,20,0,1,3,1,SkillDefinition.Aspect.MOON,false);
        SkillCatalog.require(MOON).projectile=new SkillDefinition.Projectile(16,1,true,2000,true);
        add(SUN,"日炎",25,40,40,0,1.8,3,1,SkillDefinition.Aspect.SUN,false);
        add(REFLUX,"月辰回流",30,50,0,30,0,0,0,SkillDefinition.Aspect.MOON,false);
        add(FLARE,"烈日爆炎",35,50,120,0,2.5,4,1,SkillDefinition.Aspect.SUN,false);
        add(CORONA,"日冕",40,60,60,0,1.8,4,1,SkillDefinition.Aspect.SUN,false);
        add(STARFALL,"月见星辉",45,80,40,0,2.5,4,8,SkillDefinition.Aspect.MOON,false);
        add(PARADOX,"悖论",50,100,0,0,0,1,2,SkillDefinition.Aspect.NONE,false);
    }
    private static SkillDefinition add(String id,String title,int level,int sp,double mp,double cd,double chant,double radius,int hits,SkillDefinition.Aspect aspect,boolean passive){
        var skill=new SkillDefinition(id,title,"ELEMENT",JobCatalog.SUN_MOON,level,sp,mp/(100+14d*(level-1)),cd,hits==0?0:1,passive,Map.of("range",16d,"radius",radius,"slashes",(double)Math.max(1,hits),"duration",chant,"interval",id.equals(STARFALL)?.25:0d));
        skill.baseJob=JobCatalog.MAGE;skill.magicDamage=skill.magicTag=true;skill.chantSeconds=chant;skill.aspect=aspect;skill.groundTargeted=Set.of(SUN,FLARE,CORONA,STARFALL,PARADOX).contains(id);return SkillCatalog.register(skill);
    }
    static void balance(){
        var sun=SkillCatalog.require(SUN);
        for(String id:ALL){var s=SkillCatalog.require(id);if(id.equals(CYCLE)||id.equals(REFLUX)){SkillBalance.nonDamage(s);continue;}
            double period=s.chantSeconds>0?s.chantSeconds:1;
            SkillBalance.register(s,new SkillBalanceProfile(id.equals(PARADOX)?10:2,period,false,false,0,(definition,rank,context)->List.of(new SkillBalanceProfile.Hit((long)(period*1000),definition.parameters(rank).get("damage"),false))));
            if(!id.equals(SUN)){double factor=id.equals(MOON)?.6:id.equals(FLARE)?2:id.equals(CORONA)?3:id.equals(STARFALL)?1.32:10;
                SkillBalance.registerParameters(s,values->values.put("damage",values.get("damage")/values.getOrDefault("balanceScale",1d)*SkillBalance.parameters(sun,1).get("damage")*factor));}
        }
    }
    public static void previews(){
        for(String id:ALL){var skill=SkillCatalog.require(id);int hits=id.equals(CYCLE)||id.equals(REFLUX)?0:id.equals(STARFALL)?8:id.equals(PARADOX)?2:1;
            SkillPreviewRules.register(skill,new SkillPreviewRules.Definition(p->hits,false,false,true,skill.aspect==SkillDefinition.Aspect.MOON?2:1));
            if(hits>1)SkillPreviewRules.pattern(skill,p->new SkillPreviewRules.Pattern(hits,p.get("damage",0)/hits,0));
        }
        SkillPreviewRules.total(SkillCatalog.require(PARADOX),(progress,stats,parameters,effects)->SkillPreview.elementDamage(progress,stats,parameters.skill(),parameters.get("damage",0)/2,2,effects).plus(SkillPreview.elementDamage(progress,stats,parameters.skill(),parameters.get("damage",0)/2,1,effects)));
    }
}

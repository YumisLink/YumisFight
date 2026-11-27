package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.dungeons.api.TowerBattleCard;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import java.util.*;

/** Approved Oracle content. Its matching-weapon multipliers are skill coefficient exceptions. */
public final class OracleSkills {
    private static final String NS="yumis_dungeon_and_fight:";
    public static final String WILL=NS+"oracle_will",DOWN=NS+"oracle_down",THRUST=NS+"oracle_thrust",SWEEP=NS+"oracle_sweep",SMILE=NS+"oracle_smile",RIFT=NS+"oracle_rift",REPLICA=NS+"furioso_replica";
    public static final List<String> ACTIONS=List.of(DOWN,THRUST,SWEEP,SMILE,RIFT);
    public static final List<String> WEAPONS=List.of("BLUNT","KATANA","GREATSWORD","SHORTSWORD","LIGHTSABER");
    public static final int PRELUDE_STEPS=8,COMBO_STEPS=8,FINISH_STEP=PRELUDE_STEPS+COMBO_STEPS,FULL_STEPS=FINISH_STEP+1;
    public static final double COMBO_INTERVAL=.1,FULL_DURATION=4.75;
    private static final double[] MATCH={2.2,2.4,2.2,3.2,2.4};
    private OracleSkills(){}
    static void install(){
        add(WILL,"指令之意",20,30,0,0,0,true,0,0,0);SkillCatalog.require(WILL).bindingProxy=true;
        add(DOWN,"重量下压",25,40,.12,12,1,false,4.8,1,.6);
        add(THRUST,"直线突刺",30,50,.14,12,1,false,7.0,1,.55);
        add(SWEEP,"沉重横批",35,50,.18,12,1,false,5.8,1,.9);
        add(SMILE,"举刃对笑",40,60,.22,12,1,false,4.8,2,.6);
        add(RIFT,"空间斩裂",45,80,.26,12,1,false,6.0,1,.65);
        add(REPLICA,"Furioso-Replica",50,100,.35,90,1,false,9.0,FINISH_STEP*2+1,FULL_DURATION);
        SkillCatalog.require(REPLICA).formParameter("preludeStepSeconds",.35).formParameter("comboStepSeconds",.1).formParameter("comboMoveDistance",8.5).formParameter("finisherShare",.35);
    }
    private static void add(String id,String name,int unlock,int sp,double mp,double cd,double coefficient,boolean passive,double range,int hits,double duration){
        var skill=SkillCatalog.register(new SkillDefinition(id,name,"SWORD",JobCatalog.ORACLE,unlock,sp,mp,cd,coefficient,passive,Map.of("range",range,"slashes",(double)hits,"duration",duration,"finisher",0d)));
        skill.magicDamage=false;CareerSkills.register(new CareerSkills.Content(skill,-1,0xf2cb72,hits,false,false,id.equals(REPLICA),0));
    }
    static void variants(){
        SkillPreviewRules.pattern(SkillCatalog.require(SMILE),p->new SkillPreviewRules.Pattern(2,p.get("damage",0)*.5,0));
        SkillPreviewRules.pattern(SkillCatalog.require(REPLICA),p->{int stacks=(int)p.get("replicaStacks",9);return new SkillPreviewRules.Pattern(Math.min(8,stacks),p.get("damage",0)*.0625,stacks==9?p.get("damage",0)*.75:0);});
        SkillPreviewRules.finisherLabel(SkillCatalog.require(REPLICA),"连斩与终结合计");
        SkillParameters.registerVariant(SkillCatalog.require(REPLICA),(input,p,stats)->{var values=new LinkedHashMap<>(input.values());int stacks=OracleRules.replicaStacks(stats,p,stats.modifiers.getOrDefault("oracleHermes",9d).intValue());values.put("replicaStacks",(double)stacks);values.put("duration",stacks==9?FULL_DURATION:stacks*.35);return new SkillParameters.Snapshot(input.skill(),input.rank(),values);});
        for(int i=0;i<ACTIONS.size();i++){
            var skill=SkillCatalog.require(ACTIONS.get(i));
            SkillParameters.registerVariant(skill,(input,p,stats)->{
                int index=ACTIONS.indexOf(input.skill().id);
                var values=new LinkedHashMap<>(input.values());
                boolean changed=false;
                if(WEAPONS.get(index).equals(stats.weaponType)){
                    values.computeIfPresent("damage",(k,v)->v*MATCH[index]);
                    changed=true;
                }
                if("GREATSWORD".equals(stats.weaponType)&&TowerBattleCard.ORACLE_GREATSWORD_CLEAVE.held(stats)){
                    double rangeScale=1+TowerBattleCard.ORACLE_GREATSWORD_CLEAVE.ratio("range");
                    double damageScale=1+TowerBattleCard.ORACLE_GREATSWORD_CLEAVE.ratio("damage");
                    values.computeIfPresent("damage",(k,v)->v*damageScale);
                    values.put("meleeScale",values.getOrDefault("meleeScale",1d)*rangeScale);
                    if(input.skill().id.equals(THRUST)||input.skill().id.equals(RIFT))values.computeIfPresent("range",(k,v)->v*rangeScale);
                    changed=true;
                }
                if("KATANA".equals(stats.weaponType)&&TowerBattleCard.ORACLE_KATANA_FLASH.held(stats)){
                    double damageScale=1+TowerBattleCard.ORACLE_KATANA_FLASH.ratio("damage");
                    values.computeIfPresent("damage",(k,v)->v*damageScale);
                    changed=true;
                }
                return changed?new SkillParameters.Snapshot(input.skill(),input.rank(),values):input;
            });
            SkillPreviewRules.supplement(skill,(progress,stats,parameters,effects)->{
                var extra=new SkillPreview.Damage(0,0);
                if("LIGHTSABER".equals(stats.weaponType)&&TowerBattleCard.ORACLE_LIGHTSABER_ECHO.held(stats))
                    extra=extra.plus(SkillPreview.damage(progress,stats,skill,parameters.get("damage",0)*TowerBattleCard.ORACLE_LIGHTSABER_ECHO.ratio("damage"),false,effects));
                if("BLUNT".equals(stats.weaponType)&&TowerBattleCard.ORACLE_BLUNT_QUAKE.held(stats))
                    extra=extra.plus(SkillPreview.damage(progress,stats,skill,parameters.get("damage",0)*TowerBattleCard.ORACLE_BLUNT_QUAKE.ratio("damage"),true,effects));
                return extra;
            });
        }
    }
    public static boolean contains(String id){return id.equals(WILL)||id.equals(REPLICA)||ACTIONS.contains(id);}
    public static int action(int seed,int index){var random=new Random(seed);int previous=-1;for(int i=0;i<=index;i++){int next=random.nextInt(previous<0?5:4);if(previous>=0&&next>=previous)next++;previous=next;}return previous;}
    public static boolean combo(int index){return index>=PRELUDE_STEPS&&index<FINISH_STEP;}
    public static double stepAt(int index){return stepAt(index,null);}
    public static double stepAt(int index,SkillParameters.Snapshot p){double prelude=p==null?.35:p.get("preludeStepSeconds",.35),comboStep=p==null?COMBO_INTERVAL:p.get("comboStepSeconds",COMBO_INTERVAL);return index<PRELUDE_STEPS?index*prelude:combo(index)?PRELUDE_STEPS*prelude+(index-PRELUDE_STEPS)*comboStep:PRELUDE_STEPS*prelude+COMBO_STEPS*comboStep;}
    public static double stepDuration(int index){return stepDuration(index,null);}
    public static double stepDuration(int index,SkillParameters.Snapshot p){return index<PRELUDE_STEPS?(p==null?.35:p.get("preludeStepSeconds",.35)):combo(index)?(p==null?COMBO_INTERVAL:p.get("comboStepSeconds",COMBO_INTERVAL)):1.15;}
}

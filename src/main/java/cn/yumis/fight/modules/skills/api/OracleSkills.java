package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
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
        add(DOWN,"重量下压",25,40,.12,12,1,false,4,1,.6);
        add(THRUST,"直线突刺",30,50,.14,12,1,false,6,1,.55);
        add(SWEEP,"沉重横批",35,50,.18,12,1,false,5,1,.9);
        add(SMILE,"举刃对笑",40,60,.22,12,1,false,4,2,.6);
        add(RIFT,"空间斩裂",45,80,.26,12,1,false,5,1,.65);
        add(REPLICA,"Furioso-Replica",50,100,.35,90,1,false,8,FINISH_STEP*2+1,FULL_DURATION);
    }
    private static void add(String id,String name,int unlock,int sp,double mp,double cd,double coefficient,boolean passive,double range,int hits,double duration){
        var skill=SkillCatalog.register(new SkillDefinition(id,name,"SWORD",JobCatalog.ORACLE,unlock,sp,mp,cd,coefficient,passive,Map.of("range",range,"slashes",(double)hits,"duration",duration,"finisher",0d)));
        skill.magicDamage=false;CareerSkills.register(new CareerSkills.Content(skill,-1,0xf2cb72,hits,false,false,id.equals(REPLICA),0));
    }
    static void balance(){
        SkillPreviewRules.pattern(SkillCatalog.require(SMILE),p->new SkillPreviewRules.Pattern(2,p.get("damage",0)*.5,0));
        SkillPreviewRules.pattern(SkillCatalog.require(REPLICA),p->{int stacks=(int)p.get("replicaStacks",9);return new SkillPreviewRules.Pattern(Math.min(8,stacks),p.get("damage",0)*.0625,stacks==9?p.get("damage",0)*.75:0);});
        SkillPreviewRules.finisherLabel(SkillCatalog.require(REPLICA),"连斩与终结合计");
        SkillBalance.registerVariant(SkillCatalog.require(REPLICA),(input,p,stats)->{var values=new LinkedHashMap<>(input.values());int stacks=Math.clamp(stats.modifiers.getOrDefault("oracleHermes",9d).intValue(),1,OracleRules.cap(p));values.put("replicaStacks",(double)stacks);values.put("duration",stacks==9?FULL_DURATION:stacks*.35);return new SkillParameters.Snapshot(input.skill(),input.rank(),values);});
        SkillBalance.registerStyle(JobCatalog.ORACLE,new SkillBalanceStyle("KATANA",false,(p,c,passive)->1.45*(1+p.personalMut()),(c,f)->{
            double interval=Math.max(.05,.65/(1+WeaponTraits.speed(c.weapon())/100));var hits=new ArrayList<SkillBalanceProfile.Hit>();
            for(double t=0;t<120;t+=interval)hits.add(new SkillBalanceProfile.Hit(Math.round(t*1000),1.45,false));
            return new SkillBalanceStyle.Basic(.25,interval,hits);
        },w->SkillBalanceStyle.Intrinsic.NONE,1));
        SkillBalance.nonDamage(SkillCatalog.require(WILL));
        for(int i=0;i<ACTIONS.size();i++){
            var skill=SkillCatalog.require(ACTIONS.get(i));double weight=new double[]{1.2,1.8,2.4,3,3}[i];
            SkillBalance.register(skill,new SkillBalanceProfile(weight,0,false,false,0,(s,r,c)->{
                double coefficient=s.parameters(r).get("damage");
                return s.id.equals(SMILE)?List.of(new SkillBalanceProfile.Hit(100,coefficient*.5,false),new SkillBalanceProfile.Hit(300,coefficient*.5,false)):List.of(new SkillBalanceProfile.Hit(s.id.equals(SWEEP)?500:300,coefficient,false));
            }));
            SkillBalance.registerVariant(skill,(input,p,stats)->{int index=ACTIONS.indexOf(input.skill().id);if(!WEAPONS.get(index).equals(stats.weaponType))return input;var values=new LinkedHashMap<>(input.values());values.computeIfPresent("damage",(k,v)->v*MATCH[index]);return new SkillParameters.Snapshot(input.skill(),input.rank(),values);});
        }
        // Preserve the original calibration basis: the additional five strikes are a real damage increase.
        SkillBalance.register(SkillCatalog.require(REPLICA),new SkillBalanceProfile(7.5,90,false,false,0,(s,r,c)->{
            double total=s.parameters(r).get("damage");var hits=new ArrayList<SkillBalanceProfile.Hit>();for(int i=0;i<8;i++)hits.add(new SkillBalanceProfile.Hit(i*600+300,total*.0625,false));
            for(int i=0;i<3;i++)hits.add(new SkillBalanceProfile.Hit(4800+i*250,total*.05,false));hits.add(new SkillBalanceProfile.Hit(6150,total*.35,false));return hits;
        }));
    }
    public static boolean contains(String id){return id.equals(WILL)||id.equals(REPLICA)||ACTIONS.contains(id);}
    public static int action(int seed,int index){var random=new Random(seed);int previous=-1;for(int i=0;i<=index;i++){int next=random.nextInt(previous<0?5:4);if(previous>=0&&next>=previous)next++;previous=next;}return previous;}
    public static boolean combo(int index){return index>=PRELUDE_STEPS&&index<FINISH_STEP;}
    public static double stepAt(int index){return index<PRELUDE_STEPS?index*.35:combo(index)?2.8+(index-PRELUDE_STEPS)*COMBO_INTERVAL:3.6;}
    public static double stepDuration(int index){return index<PRELUDE_STEPS?.35:combo(index)?COMBO_INTERVAL:1.15;}
}

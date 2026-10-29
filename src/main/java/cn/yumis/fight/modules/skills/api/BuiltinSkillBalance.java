package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.equipment.api.WeaponTraits;
import java.util.*;
import static cn.yumis.fight.modules.skills.api.SkillBalanceProfile.*;
import static cn.yumis.fight.modules.skills.api.SkillBalanceStyle.Intrinsic.*;

/** Content-owned reference choreography. New skills register a profile beside their content. */
final class BuiltinSkillBalance {
    private static volatile Double eyeMut;
    private BuiltinSkillBalance(){}
    private static Hit hit(double at,double amount){return new Hit(Math.round(at*1000),amount,false);}
    private static Hit dot(double at,double amount){return new Hit(Math.round(at*1000),amount,true);}
    private static void add(SkillDefinition skill,double weight,Pattern pattern){SkillBalance.register(skill,new SkillBalanceProfile(weight,0,true,false,0,pattern));}
    private static void career(String id,double weight,Pattern pattern){add(SkillCatalog.require(id),weight,pattern);}
    static void install(){
        styles();
        SkillBalance.nonDamage(SkillCatalog.MASTERY);SkillBalance.nonDamage(SkillCatalog.OVERDRIVE);SkillBalance.nonDamage(SkillCatalog.BLINK);
        for(String id:List.of(CareerSkills.KEIGA,CareerSkills.BRANDS,CareerSkills.FRENZY))SkillBalance.nonDamage(SkillCatalog.require(id));
        SkillBalance.register(SkillCatalog.GHOST,new SkillBalanceProfile(1,0,false,false,0,(s,r,c)->List.of(hit(1,s.parameters(r).get("damage")*2))));
        add(SkillCatalog.TRIPLE,1,(s,r,c)->{
            var p=s.parameters(r);int count=3+(c.mastery()?(Set.of("KATANA","LIGHTSABER").contains(c.weapon())?4:2):0);var out=new ArrayList<Hit>();
            for(int i=0;i<count;i++){out.add(hit(i*.2+.1,p.get("damage")));if(c.mastery()&&c.weapon().equals("SHORTSWORD"))out.add(dot(i*.2+.2,p.get("damage")*.2*CareerSkills.growth(9)));}return out;
        });
        add(SkillCatalog.WAVE,1,(s,r,c)->List.of(hit(.1,s.parameters(r).get("damage"))));
        add(SkillCatalog.RISING,2,(s,r,c)->List.of(hit(.15,s.parameters(r).get("damage")),hit(.3,s.parameters(r).get("finisher"))));
        add(SkillCatalog.DRAW,3,(s,r,c)->{double damage=s.parameters(r).get("damage");return c.mastery()&&c.weapon().equals("SHORTSWORD")?List.of(hit(.4,damage),dot(.5,damage*.2*CareerSkills.growth(9))):List.of(hit(.4,damage));});
        add(SkillCatalog.DRAGON,4,(s,r,c)->{var out=new ArrayList<Hit>();int count=4+(c.mastery()&&c.weapon().equals("LIGHTSABER")?1:0);for(int i=0;i<count;i++)out.add(hit(i*.25+.125,s.parameters(r).get("damage")));return out;});
        add(SkillCatalog.DANCE,5,(s,r,c)->{
            var p=s.parameters(r);int count=10+(c.mastery()&&Set.of("KATANA","LIGHTSABER").contains(c.weapon())?10:0);var out=new ArrayList<Hit>();double interval=Math.min(.125,1.25/count);
            for(int i=0;i<count;i++){out.add(hit(i*interval,p.get("damage")));if(c.mastery()&&c.weapon().equals("SHORTSWORD"))out.add(dot(i*interval+.1,p.get("damage")*.2*CareerSkills.growth(9)));}out.add(hit(1.35,p.get("finisher")));return out;
        });
        add(SkillCatalog.STORM,10,(s,r,c)->{var p=s.parameters(r);double duration=p.get("duration");var out=new ArrayList<Hit>();for(int i=0;i<24;i++)out.add(hit(duration*.125+i*duration*.75/24,p.get("damage")));out.add(hit(duration*.9,p.get("finisher")));return out;});
        SkillBalance.register(SkillCatalog.require(CareerSkills.SOUL_SLASH),new SkillBalanceProfile(.5,6,false,false,0,(s,r,c)->List.of(hit(1,s.parameters(r).get("damage")))));
        SkillBalance.register(SkillCatalog.require(CareerSkills.FIELD),new SkillBalanceProfile(2,0,false,false,0,(s,r,c)->{var out=new ArrayList<Hit>();var p=s.parameters(r);for(int i=1;i<=12;i++){if(r>=8)out.add(dot(i,p.get("iceDot")));if(r>=15)out.add(dot(i,p.get("darkDot")));}return out;}));
        career(CareerSkills.TOMBSTONE,3,(s,r,c)->{var out=new ArrayList<Hit>();for(int i=1;i<=10;i++)out.add(hit(i*.2,s.parameters(r).get("damage")));return out;});
        career(CareerSkills.PHANTOM,4,(s,r,c)->List.of(hit(.125,s.parameters(r).get("damage"))));
        career(CareerSkills.KALLA,5,BuiltinSkillBalance::kalla);
        SkillBalance.register(SkillCatalog.require(CareerSkills.BLACHE),new SkillBalanceProfile(10,0,true,true,CareerSkills.blacheMut(9),(s,r,c)->List.of(hit(1.2,s.parameters(r).get("damage")))));
        career(CareerSkills.ORB,2,(s,r,c)->{var p=s.parameters(r);var out=new ArrayList<Hit>();int count=CareerSkills.orbHits(3);double damage=(p.get("damage")+3*p.get("perBrandDamage"))/count;for(int i=1;i<=count;i++)out.add(hit(.15+i*.35,damage));return out;});
        career(CareerSkills.CRESCENT,2,(s,r,c)->{var out=new ArrayList<Hit>();for(int i=0;i<3;i++)out.add(hit(1.1+i*.05,s.parameters(r).get("damage")));return out;});
        career(CareerSkills.ICE,3,(s,r,c)->{var out=new ArrayList<Hit>();for(int i=0;i<3;i++)out.add(hit(.1+i*.2,s.parameters(r).get("damage")));return out;});
        career(CareerSkills.FIRE,4,(s,r,c)->List.of(hit(2d/3,s.parameters(r).get("damage"))));
        career(CareerSkills.AGNI,5,(s,r,c)->{var p=s.parameters(r);var out=new ArrayList<Hit>();for(int i=1;i<=SkillResourceRules.agniHits(3);i++)out.add(hit(i*SkillResourceRules.agniInterval(3),p.get("damage")));out.add(hit(3,p.get("finisher")));return out;});
        career(CareerSkills.EYE,9,(s,r,c)->List.of(hit(20.8,s.parameters(r).get("damage"))));
        career(CareerSkills.CRASH,2,(s,r,c)->List.of(hit(.5,s.parameters(r).get("damage")),hit(.5,s.parameters(r).get("finisher"))));
        career(CareerSkills.HAND,2,(s,r,c)->List.of(hit(.35,s.parameters(r).get("damage"))));
        career(CareerSkills.RAGE,3,(s,r,c)->{var out=new ArrayList<Hit>();for(int i=0;i<5;i++)out.add(hit(i*.15,s.parameters(r).get("damage")));return out;});
        career(CareerSkills.BLADE,4,(s,r,c)->List.of(hit(.2,s.parameters(r).get("damage")),hit(.35,s.parameters(r).get("finisher"))));
        career(CareerSkills.ERUPTION,5,(s,r,c)->{var p=s.parameters(r);var out=new ArrayList<Hit>();out.add(hit(.6,p.get("finisher")));for(int i=0;i<4;i++)out.add(hit(.6+i*.2,p.get("damage")));return out;});
        career(CareerSkills.SWORD,10,(s,r,c)->{var p=s.parameters(r);double total=p.get("damage")+15*p.get("perBloodDamage"),delay=14*Math.max(.5,SkillBalance.basicInterval(c,true));var out=new ArrayList<Hit>();out.add(hit(delay+.3,total*.3));for(int i=0;i<5;i++)out.add(hit(delay+.6+i*.3,total*.7/5));return out;});
        SkillBalance.registerParameters(SkillCatalog.require(CareerSkills.EYE),values->values.put("eyeBonusMut",eyeBonus()));
        SkillBalance.registerVariant(SkillCatalog.GHOST,(input,progress,stats)->{
            var values=new LinkedHashMap<>(input.values());var skill=SkillCatalog.require(CareerSkills.SOUL_SLASH);int rank=SkillLearning.effective(stats,progress,skill);
            if(rank>0){values.put("soulBonus",SkillParameters.resolve(skill,rank,stats.parameters).get("damage",0));if(stats.modifiers.getOrDefault("ownGhostField",0d)>0)values.put("ghostFieldMultiplier",2/redMultiplier());}
            return new SkillParameters.Snapshot(input.skill(),input.rank(),values);
        });
    }
    private static double redMultiplier(){return (1+133*1.18/100)/(1+133d/100);}
    private static void styles(){
        SkillBalance.registerStyle(JobCatalog.NONE,new SkillBalanceStyle("KATANA",false,(p,c,passive)->1+p.personalMut(),(c,f)->basic(c,false,.25,1,0),w->NONE,1));
        SkillBalance.registerStyle(JobCatalog.MAGE,new SkillBalanceStyle("STAFF",false,(p,c,passive)->1+p.personalMut(),(c,f)->basic(c,false,.25,1,0),w->NONE,1));
        double g=CareerSkills.growth(9),attack=1+.15*g,overdrive=.15*g*SkillBalance.coverage(30,40),red=1+(redMultiplier()-1)*.6;
        SkillBalance.registerStyle(JobCatalog.BASE,new SkillBalanceStyle("KATANA",false,(p,c,passive)->1+p.personalMut(),(c,f)->basic(c,false,.25,1,0),w->NONE,1));
        SkillBalance.registerStyle(JobCatalog.SWORDMASTER,new SkillBalanceStyle("KATANA",true,(p,c,passive)->(1+overdrive)*(1+p.personalMut())*(c.weapon().equals("GREATSWORD")&&!passive?1+.2*g:1),(c,f)->basic(c,false,c.weapon().equals("LIGHTSABER")?2:1,1+overdrive,0),w->w.equals("KATANA")?BLEED:w.equals("LIGHTSABER")?SHOCK:NONE,g));
        SkillBalance.registerStyle(JobCatalog.SOUL_BENDER,new SkillBalanceStyle("SHORTSWORD",false,(p,c,passive)->attack*(1+p.personalMut())*(p.redCoverage()?1+(redMultiplier()-1)*(p.fullRed()?1:.6):1),(c,f)->basic(c,false,.25,attack*red,0),w->NONE,1));
        SkillBalance.registerStyle(JobCatalog.ASURA,new SkillBalanceStyle("SHORTSWORD",false,(p,c,passive)->attack*(1+p.personalMut()),(c,f)->basic(c,false,.25,attack,0),w->NONE,1));
        SkillBalance.registerStyle(JobCatalog.BERSERKER,new SkillBalanceStyle("GREATSWORD",false,(p,c,passive)->attack*(1+.20+p.personalMut()),(c,f)->basic(c,f,f?2:.25,f?attack*1.2:1,15),w->NONE,1));
    }
    private static SkillBalanceStyle.Basic basic(Context context,boolean frenzy,double weight,double boost,double frenzySpeed){
        double interval=Math.max(.05,.65/(1+(WeaponTraits.speed(context.weapon())+(frenzy?frenzySpeed:0))/100)*(context.mastery()&&context.weapon().equals("GREATSWORD")?2:1));
        double coefficient=frenzy?.65:context.mastery()&&context.weapon().equals("GREATSWORD")?1.5:context.mastery()&&context.weapon().equals("LIGHTSABER")?.75:1;
        int count=frenzy||context.mastery()&&context.weapon().equals("LIGHTSABER")?2:1;var hits=new ArrayList<Hit>();
        for(double start=0;start<120;start+=interval){
            for(int slash=0;slash<count;slash++){long at=Math.round((start+slash*interval/2)*1000);if(at<SkillBalance.WINDOW)hits.add(new Hit(at,coefficient*boost,false));}
            if(context.mastery()&&context.weapon().equals("SHORTSWORD")){long at=Math.round((start+.1)*1000);if(at<SkillBalance.WINDOW)hits.add(new Hit(at,.2*CareerSkills.growth(9)*boost,true));}
        }
        hits.sort(Comparator.comparingLong(Hit::at));return new SkillBalanceStyle.Basic(weight,interval,hits);
    }
    /** Evaluate one identical four-action timeline; the difference is the weight-3 eye contribution. */
    private static double eyeBonus(){
        if(eyeMut!=null)return eyeMut;var context=SkillBalance.context(JobCatalog.ASURA,"SHORTSWORD");double basis=0,nextBasic=0;double[] ready={0,0,0};SkillDefinition[] skills={SkillCatalog.require(CareerSkills.FIRE),SkillCatalog.require(CareerSkills.ICE),SkillCatalog.WAVE};
        for(double now=0;now<120;){
            int chosen=-1;for(int i=0;i<skills.length;i++)if(ready[i]<=now+1e-9){chosen=i;break;}
            boolean active=now<20||now>=90&&now<110;
            if(chosen>=0){var skill=skills[chosen];var profile=SkillBalance.profile(skill);double start=now;
                if(active)for(var hit:profile.pattern().hits(skill,SkillBalance.rank(skill),context))if(start+hit.at()/1000d<120)basis+=hit.coefficient()*SkillBalance.boost(context,profile,hit.passive())*SkillBalance.scale(skill,context,.5);
                ready[chosen]=now+skill.cooldown(SkillBalance.rank(skill));now+=chosen==1?.6:.05;
            }else if(nextBasic<=now+1e-9){if(active)basis+=SkillBalance.boost(context,new SkillBalanceProfile(1,1,false,false,0,(s,r,c)->List.of()),true)*SkillBalance.basicFactor(context,false,.5);nextBasic=now+SkillBalance.basicInterval(context,false);now+=.05;}
            else now=Math.min(nextBasic,Arrays.stream(ready).min().orElse(120));
        }
        if(!(basis>0))throw new IllegalArgumentException("波动眼参考轮转没有有效伤害");eyeMut=180/basis;return eyeMut;
    }
    private static List<Hit> kalla(SkillDefinition skill,int rank,Context context){
        var p=skill.parameters(rank);var out=new ArrayList<Hit>();double interval=SkillBalance.basicInterval(context,false),attach=.1,next=.6,expires=0;int stacks=0;
        while(true){
            if(attach<19.1&&attach<=next){stacks=Math.min(3,stacks+1);expires=attach+3;attach+=interval;continue;}
            if(next>expires)break;out.add(dot(next,p.get("flameDot")*stacks));next+=.5;
        }
        for(int i=0;i<3;i++)out.add(hit(19.1+i*.3,p.get("damage")));out.sort(Comparator.comparingLong(Hit::at));return out;
    }
}

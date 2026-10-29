package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.modules.character.api.JobCatalog;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import java.util.*;
import static cn.yumis.fight.modules.skills.api.SkillBalanceProfile.*;

/** Reference-only coefficient calibration. Actual player speed, cooldown and gear never renormalize damage. */
public final class SkillBalance {
    public static final int LEVEL=60;
    public static final long WINDOW=120000;
    private static final Map<SkillDefinition,SkillBalanceProfile> PROFILES=new LinkedHashMap<>();
    private static final Set<SkillDefinition> NON_DAMAGE=new HashSet<>();
    private static final Map<String,SkillBalanceStyle> STYLES=new LinkedHashMap<>();
    private static final Map<String,SkillBalanceStyle.Basic> BASICS=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<SkillDefinition,java.util.function.Consumer<Map<String,Double>>> PARAMETERS=new LinkedHashMap<>();
    @FunctionalInterface public interface Variant {SkillParameters.Snapshot apply(SkillParameters.Snapshot input,Progress progress,Stats stats);}
    private static final Map<SkillDefinition,Variant> VARIANTS=new LinkedHashMap<>();
    private static final Map<String,Double> SCALES=new java.util.concurrent.ConcurrentHashMap<>();
    private SkillBalance(){}
    public static void register(SkillDefinition skill,SkillBalanceProfile profile){if(PROFILES.putIfAbsent(skill,Objects.requireNonNull(profile))!=null||NON_DAMAGE.contains(skill))throw new IllegalArgumentException("Duplicate balance profile: "+skill);}
    public static void nonDamage(SkillDefinition skill){if(PROFILES.containsKey(skill)||!NON_DAMAGE.add(skill))throw new IllegalArgumentException("Duplicate balance profile: "+skill);}
    public static void registerStyle(String job,SkillBalanceStyle style){JobCatalog.require(job);if(STYLES.putIfAbsent(job,Objects.requireNonNull(style))!=null)throw new IllegalArgumentException("Duplicate balance style: "+job);}
    public static void registerParameters(SkillDefinition skill,java.util.function.Consumer<Map<String,Double>> parameters){if(PARAMETERS.putIfAbsent(skill,parameters)!=null)throw new IllegalArgumentException("Duplicate balance parameters: "+skill);}
    public static void registerVariant(SkillDefinition skill,Variant variant){if(VARIANTS.putIfAbsent(skill,variant)!=null)throw new IllegalArgumentException("Duplicate balance variant: "+skill);}
    private static SkillBalanceStyle style(String job){SkillCatalog.all();return Objects.requireNonNull(STYLES.get(job),"Missing career balance style: "+job);}
    static SkillBalanceProfile profile(SkillDefinition skill){return Objects.requireNonNull(PROFILES.get(skill));}
    public static double weight(SkillDefinition skill){SkillCatalog.all();var profile=PROFILES.get(skill);if(profile==null&&!NON_DAMAGE.contains(skill))throw new IllegalArgumentException("Missing balance registration: "+skill);return profile==null?0:profile.weight();}
    public static double futureWeight(int awakening){return awakening>=3?20:awakening>=1?10:5;}
    /** Content damage boost: unlock level 1 is x10, level 50 and later are x3. */
    public static double unlockMultiplier(int unlock){return 10-7*Math.clamp((unlock-1)/49d,0,1);}
    public static int rank(SkillDefinition skill){return 1+Math.max(0,LEVEL-skill.unlock)/5;}
    public static Context context(String job,String weapon){return new Context(job,weapon,style(job).mastery());}
    public static Context reference(SkillDefinition skill){String job=skill.advanced()?skill.requiredJob:skill.baseJob;return context(job,defaultWeapon(job));}
    public static String defaultWeapon(String job){return style(job).defaultWeapon();}
    public static double coverage(double duration,double cooldown){double covered=0;for(double t=0;t<WINDOW/1000d;t+=cooldown)covered+=Math.min(duration,WINDOW/1000d-t);return covered/(WINDOW/1000d);}
    public static double boost(Context context,SkillBalanceProfile profile,boolean passive){
        return style(context.job()).multiplier().apply(profile,context,passive);
    }
    public static List<Hit> timeline(SkillDefinition skill,Context context){
        SkillCatalog.all();var profile=Objects.requireNonNull(PROFILES.get(skill),"Missing reference pattern");
        double period=profile.period()>0?profile.period():skill.cooldown(rank(skill));if(period<=0)throw new IllegalArgumentException("Reference damage period must be positive");
        var hits=new ArrayList<Hit>();var pattern=profile.pattern().hits(skill,rank(skill),context);
        for(double t=0;t<WINDOW;t+=period*1000)for(var hit:pattern){long at=Math.round(t)+hit.at();if(at<WINDOW)hits.add(new Hit(at,hit.coefficient()*boost(context,profile,hit.passive()),hit.passive()));}
        hits.sort(Comparator.comparingLong(Hit::at));return hits;
    }
    public static double referenceOutput(SkillDefinition skill,Context context){return expected(timeline(skill,context),context);}
    private static double unitScale(SkillDefinition skill,Context context){
        String key=skill.id+"/"+context.job()+"/"+context.weapon();
        return SCALES.computeIfAbsent(key,ignored->{double output=referenceOutput(skill,context);if(!(output>0)||!Double.isFinite(output))throw new IllegalArgumentException("Invalid reference output: "+skill);return 120*weight(skill)/output;});
    }
    public static double scale(SkillDefinition skill,Context context,double base){return unitScale(skill,context)*base;}
    public static void validate(SkillBalanceRules rules){
        SkillCatalog.all();
        for(var skill:PROFILES.keySet()){
            var jobs=skill.advanced()?List.of(skill.requiredJob):JobCatalog.all().stream().filter(j->JobCatalog.base(j.id()).equals(skill.baseJob)).map(JobCatalog.Definition::id).toList();
            for(String job:jobs)for(String weapon:List.of("KATANA","LIGHTSABER","SHORTSWORD","BLUNT","GREATSWORD","STAFF","CLUB")){
                double factor=scale(skill,context(job,weapon),rules.baseWeightDps())*unlockMultiplier(skill.unlock);if(!(factor>0)||!Double.isFinite(factor))throw new IllegalArgumentException("技能基数超出可计算范围");
                for(String key:skill.damageParameters())if(!Double.isFinite(skill.parameters(rank(skill)).getOrDefault(key,0d)*factor))throw new IllegalArgumentException("技能倍率超出可计算范围");
            }
        }
        for(String job:STYLES.keySet())for(String weapon:List.of("KATANA","LIGHTSABER","SHORTSWORD","BLUNT","GREATSWORD"))for(boolean frenzy:List.of(false,true)){
            double factor=basicFactor(context(job,weapon),frenzy,rules.baseWeightDps())*unlockMultiplier(1);if(!(factor>0)||!Double.isFinite(factor))throw new IllegalArgumentException("普攻基数超出可计算范围");
        }
        PARAMETERS.forEach((skill,processor)->{var values=skill.parameters(rank(skill));processor.accept(values);if(values.values().stream().anyMatch(value->!Double.isFinite(value)))throw new IllegalArgumentException("技能派生参数无效");});
    }
    public static Map<String,Double> parameters(SkillDefinition skill,int rank){
        Map<String,Double> values=skill.parameters(rank);weight(skill);var rules=SkillBalanceRules.current();
        if(PROFILES.containsKey(skill)){double scale=scale(skill,reference(skill),rules.baseWeightDps())*unlockMultiplier(skill.unlock);for(String key:skill.damageParameters())values.computeIfPresent(key,(k,v)->v*scale);values.put("balanceScale",scale);}
        var processor=PARAMETERS.get(skill);if(processor!=null)processor.accept(values);
        return values;
    }
    public static SkillParameters.Snapshot variant(SkillParameters.Snapshot input,Progress progress,Stats stats){
        SkillParameters.Snapshot result=input;
        if(PROFILES.containsKey(input.skill())){
            var values=new LinkedHashMap<>(input.values());var context=context(progress.jobId(),stats.weaponType);double ratio=unitScale(input.skill(),context)/unitScale(input.skill(),reference(input.skill()));
            for(String key:input.skill().damageParameters())values.computeIfPresent(key,(k,v)->v*ratio);values.computeIfPresent("balanceScale",(k,v)->v*ratio);result=new SkillParameters.Snapshot(input.skill(),input.rank(),values);
        }
        var processor=VARIANTS.get(input.skill());return processor==null?result:processor.apply(result,progress,stats);
    }
    /** Finite-window expected mastery damage; no combat RNG is rolled by calibration. */
    private record BleedKey(long expires,long next,int index){}
    private record BleedValue(double probability,double moment){}
    private static void merge(Map<BleedKey,BleedValue> states,BleedKey key,double probability,double moment){if(probability<1e-15)return;var old=states.get(key);states.put(key,new BleedValue(probability+(old==null?0:old.probability),moment+(old==null?0:old.moment)));}
    private static double expected(List<Hit> hits,Context context){
        var reference=style(context.job());var intrinsic=reference.intrinsic().apply(context.weapon());boolean katana=intrinsic==SkillBalanceStyle.Intrinsic.BLEED,light=intrinsic==SkillBalanceStyle.Intrinsic.SHOCK;
        double g=reference.intrinsicGrowth(),total=0,inactive=1;var shocks=new ArrayDeque<Long>();Map<BleedKey,BleedValue> states=new HashMap<>();
        int cursor=0;
        while(cursor<hits.size()||!states.isEmpty()){
            long hitAt=cursor<hits.size()?hits.get(cursor).at():WINDOW;
            long dotAt=states.keySet().stream().mapToLong(key->Math.min(key.next(),key.expires()+1)).min().orElse(WINDOW);
            long at=Math.min(hitAt,dotAt);if(at>=WINDOW)break;
            if(dotAt<=hitAt){
                Map<BleedKey,BleedValue> next=new HashMap<>();
                for(var entry:states.entrySet()){var key=entry.getKey();var value=entry.getValue();if(key.expires()<at||key.index()>=10){inactive+=value.probability;continue;}
                    if(key.next()==at){total+=value.moment*.005;merge(next,new BleedKey(key.expires(),at+1000,key.index()+1),value.probability,value.moment);}else merge(next,key,value.probability,value.moment);
                }
                states=next;continue;
            }
            Hit hit=hits.get(cursor++);double main=hit.coefficient();
            if(katana&&!hit.passive()){
                double active=states.values().stream().mapToDouble(BleedValue::probability).sum(),boost=1+.05*(2*g-1);total+=main*(inactive+active*boost);
                Map<BleedKey,BleedValue> next=new HashMap<>();
                for(var entry:states.entrySet()){var key=entry.getKey();var value=entry.getValue();merge(next,key,.9*value.probability,.9*value.moment);merge(next,new BleedKey(at+10000,key.next(),0),.1*value.probability,.1*value.probability*main*boost);}
                merge(next,new BleedKey(at+10000,at+1000,0),.1*inactive,.1*inactive*main);inactive*=.9;states=next;
            }else total+=main;
            if(light&&!hit.passive()){
                while(!shocks.isEmpty()&&shocks.peekFirst()<=at-5000)shocks.removeFirst();
                // The intrinsic flat shock component is normalized with its source, separately from gear extras.
                double shock=.13*g*(1-Math.pow(.8,shocks.size()));
                total+=shock;shocks.addLast(at);
            }
        }
        return total;
    }
    private static SkillBalanceStyle.Basic basicReference(Context context,boolean frenzy){return BASICS.computeIfAbsent(context.job()+"/"+context.weapon()+"/"+frenzy,ignored->style(context.job()).basic().create(context,frenzy));}
    public static double basicWeight(String job,String weapon,boolean frenzy){return basicReference(context(job,weapon),frenzy).weight();}
    public static double basicInterval(Context context,boolean frenzy){return basicReference(context,frenzy).interval();}
    public static double basicScale(String job,String weapon,boolean frenzy){
        return basicFactor(context(job,weapon),frenzy,SkillBalanceRules.current().baseWeightDps())*unlockMultiplier(1);
    }
    static double basicFactor(Context context,boolean frenzy,double base){return basicUnitScale(context,frenzy)*base;}
    private static double basicUnitScale(Context context,boolean frenzy){
        String key="basic/"+context.job()+"/"+context.weapon()+"/"+frenzy;
        return SCALES.computeIfAbsent(key,ignored->{
            var basic=basicReference(context,frenzy);double output=expected(basic.hits(),context);if(!(output>0)||!Double.isFinite(output))throw new IllegalArgumentException("Invalid basic reference output");return 120*basic.weight()/output;
        });
    }
}

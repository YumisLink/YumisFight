package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.architecture.StableId;
import java.util.Map;

/** SkillDefinition content; IDs, learning rules and base parameters do not depend on enum ordering. */
public final class SkillDefinition {
    public final String id,title,family,requiredJob;
    public final int unlock,sp;
    public final double mpRatio,cooldown,coefficient;
    public boolean equipmentGranted,cardGranted,magicTag,bindingProxy;
    public boolean automaticByJob;
    /** Family and passive rank sources remain independent of magic/physical rank sources. */
    public boolean attackTypeRanks=true;
    public int maximumLearnedRank=Integer.MAX_VALUE;
    public boolean groundTargeted;
    public double chantSeconds;
    public enum Aspect { NONE, SUN, MOON }
    public Aspect aspect=Aspect.NONE;
    public record Projectile(double speed,int pierces,boolean explodes,long impactMillis,boolean boundingBoxBlast){}
    public Projectile projectile;
    public Boolean magicDamage;
    public String baseJob=cn.yumis.fight.modules.character.api.JobCatalog.BASE;
    public double cooldownPerRank;
    private final boolean passive;
    private final Map<String,Double> parameters;
    private final java.util.Set<String> damageParameters=new java.util.LinkedHashSet<>(java.util.List.of("damage","finisher"));
    public SkillDefinition(String id,String title,String family,String requiredJob,int unlock,int sp,double mpRatio,double cooldown,double coefficient,boolean passive,Map<String,Double> parameters){
        new StableId(id);
        if(unlock<1||sp<1||mpRatio<0||cooldown<0||coefficient<0||!Double.isFinite(mpRatio+cooldown+coefficient))throw new IllegalArgumentException("Invalid skill definition");
        parameters.forEach((key,value)->{if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid skill parameter: "+key);});
        this.id=id;this.title=title;this.family=family;this.requiredJob=requiredJob;this.unlock=unlock;this.sp=sp;this.mpRatio=mpRatio;this.cooldown=cooldown;this.coefficient=coefficient;this.passive=passive;this.parameters=new java.util.LinkedHashMap<>(parameters);
    }
    public int effectiveUnlock(){var e=SkillBalanceRules.current().skill(id);return e==null?unlock:e.unlock();}
    public int effectiveSp(){var e=SkillBalanceRules.current().skill(id);return e==null?sp:e.sp();}
    public boolean basicSkill(){return !equipmentGranted&&!cardGranted&&requiredJob.isEmpty()&&baseJob.equals(cn.yumis.fight.modules.character.api.JobCatalog.BASE);}
    public double cooldown(int rank){
        var e=SkillBalanceRules.current().skill(id);
        double baseCd=e==null?cooldown:e.cooldown();
        double perRank=e==null?cooldownPerRank:e.cooldownPerRank();
        return Math.max(0,baseCd-perRank*Math.max(0,rank-1));
    }
    public boolean passive(){return passive;}
    public boolean advanced(){return !requiredJob.isEmpty();}
    public boolean available(cn.yumis.fight.modules.character.api.Progress progress){int u=effectiveUnlock();return equipmentGranted||progress.selected()&&(advanced()?progress.hasJob(requiredJob):progress.baseJob().equals(baseJob))&&(u<50||progress.awakened());}
    public boolean weaponAllowed(cn.yumis.fight.modules.character.api.Progress progress,cn.yumis.fight.modules.attributes.api.Stats stats){return equipmentGranted||stats.weaponValid&&cn.yumis.fight.modules.character.api.JobCatalog.weapons(progress.jobId()).contains(stats.weaponType);}
    public int limit(int level,String job){int u=effectiveUnlock();return level<u||(advanced()?!requiredJob.equals(job):!cn.yumis.fight.modules.character.api.JobCatalog.base(job).equals(baseJob))?0:Math.min(maximumLearnedRank,1+(level-u)/5);}
    public int mp(int rank){
        if(rank<=0)return 0;
        var rules=SkillBalanceRules.current();
        var e=rules.skill(id);
        int u=e==null?unlock:e.unlock();
        double ratio=e==null?mpRatio:e.mpRatio();
        return (int)Math.round((rules.mpUnlockBase+rules.mpUnlockPerLevel*(u-1))*ratio*Math.pow(rules.defaultRankMpGrowth,rank-1));
    }
    public String icon(){return id.substring(id.indexOf(':')+1);}
    public SkillDefinition damageParameter(String key,double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid damage parameter");parameters.put(key,value);damageParameters.add(key);return this;}
    /** Form parameters never rank-grow and never take the balance scale; equipment can modify them per cast. */
    public SkillDefinition formParameter(String key,double value){if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid form parameter");parameters.put(key,value);return this;}
    public java.util.Set<String> damageParameters(){return java.util.Set.copyOf(damageParameters);}
    Map<String,Double> rawParameters(int rank){var result=new java.util.LinkedHashMap<>(parameters);result.put("damage",coefficient);double growth=Math.pow(SkillBalanceRules.current().defaultRankDamageGrowth,Math.max(0,rank-1));for(String key:damageParameters)result.computeIfPresent(key,(k,v)->v*growth);return result;}
    public Map<String,Double> parameters(int rank){return SkillBalanceRules.current().parameters(this,rank);}
    @Override public String toString(){return id;}
}

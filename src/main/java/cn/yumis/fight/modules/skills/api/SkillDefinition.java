package cn.yumis.fight.modules.skills.api;

import cn.yumis.fight.architecture.StableId;
import java.util.Map;

/** SkillDefinition content; IDs, learning rules and base parameters do not depend on enum ordering. */
public final class SkillDefinition {
    public final String id,title,family,requiredJob;
    public final int unlock,sp,cap;
    public final double mpRatio,cooldown,coefficient;
    public boolean equipmentGranted,magicTag;
    public double cooldownPerRank;
    private final boolean passive;
    private final Map<String,Double> parameters;
    public SkillDefinition(String id,String title,String family,String requiredJob,int unlock,int sp,int cap,double mpRatio,double cooldown,double coefficient,boolean passive,Map<String,Double> parameters){
        new StableId(id);
        if(unlock<1||sp<1||cap<1||mpRatio<0||cooldown<0||coefficient<0||!Double.isFinite(mpRatio+cooldown+coefficient))throw new IllegalArgumentException("Invalid skill definition");
        parameters.forEach((key,value)->{if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid skill parameter: "+key);});
        this.id=id;this.title=title;this.family=family;this.requiredJob=requiredJob;this.unlock=unlock;this.sp=sp;this.cap=cap;this.mpRatio=mpRatio;this.cooldown=cooldown;this.coefficient=coefficient;this.passive=passive;this.parameters=Map.copyOf(parameters);
    }
    public boolean basicSkill(){return !equipmentGranted&&requiredJob.isEmpty();}
    public double cooldown(int rank){return Math.max(0,cooldown-cooldownPerRank*Math.max(0,rank-1));}
    public boolean passive(){return passive;}
    public boolean advanced(){return !requiredJob.isEmpty();}
    public int limit(int level,String job){return level<unlock||advanced()&&!requiredJob.equals(job)?0:Math.min(cap,1+(level-unlock)/5);}
    public int mp(int rank){return rank<=0?0:(int)Math.round((100+14*(unlock-1))*mpRatio*Math.pow(1.08,rank-1));}
    public double coefficient(int rank){return coefficient*Math.pow(1.12,Math.max(0,rank-1));}
    public String icon(){return id.substring(id.indexOf(':')+1);}
    public Map<String,Double> parameters(int rank){var result=new java.util.LinkedHashMap<>(parameters);result.put("damage",coefficient(rank));result.computeIfPresent("finisher",(k,v)->v*Math.pow(1.12,Math.max(0,rank-1)));return result;}
    @Override public String toString(){return id;}
}

package cn.yumis.fight.modules.skills.api;

import java.util.*;
import cn.yumis.fight.modules.attributes.api.ParameterModifier;
/** Resolved once at cast start; equipment integrations can supply additive and multiplicative changes. */
public final class SkillParameters {
    private static final Map<String,java.util.function.BiFunction<UUID,SkillDefinition,Collection<ParameterModifier>>> SOURCES=new LinkedHashMap<>();
    public static synchronized void register(String id,java.util.function.BiFunction<UUID,SkillDefinition,Collection<ParameterModifier>> provider){
        SOURCES.put(id,Objects.requireNonNull(provider));
    }
    public static synchronized void remove(String id){
        SOURCES.remove(id);
    }
    public static synchronized Snapshot forCast(UUID player,SkillDefinition skill,int rank,Collection<ParameterModifier> equipment){
        List<ParameterModifier> all=new ArrayList<>(equipment);
        for(var provider:SOURCES.values())all.addAll(provider.apply(player,skill));
        return resolve(skill,rank,all);
    }

    public record Snapshot(SkillDefinition skill,int rank,Map<String,Double> values){
        public Snapshot{
            values=Map.copyOf(values);
        }
        public double get(String key,double fallback){
            return values.getOrDefault(key,fallback);
        }
    }
    private SkillParameters(){
    }
    /** Form keys whose equipment changes must never disable a skill; values are hard floors applied after modifier resolution. */
    private static final Map<String,Double> COUNT_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("slashes",1d),java.util.Map.entry("auraPulses",1d),java.util.Map.entry("pierces",1d),
        java.util.Map.entry("orbBaseHits",1d),java.util.Map.entry("flameStacks",1d),java.util.Map.entry("brandCap",1d),
        java.util.Map.entry("bloodCap",1d),java.util.Map.entry("swordRushes",1d),java.util.Map.entry("stoneVolley",1d),
        java.util.Map.entry("heavyEvery",1d),java.util.Map.entry("bombEvery",1d),java.util.Map.entry("replicaSteps",1d),
        java.util.Map.entry("stoneUnlockRank",1d),java.util.Map.entry("sunMoonStackCap",1d)));
    private static final Map<String,Double> TIME_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("interval",.05d),java.util.Map.entry("duration",.05d),java.util.Map.entry("chargeSeconds",.05d),
        java.util.Map.entry("dashSeconds",.05d),java.util.Map.entry("hopSeconds",.05d),java.util.Map.entry("flameSeconds",.05d),
        java.util.Map.entry("flameDotInterval",.05d),java.util.Map.entry("fieldTickInterval",.05d),java.util.Map.entry("orbTickInterval",.05d),
        java.util.Map.entry("crescentChargeSeconds",.05d),java.util.Map.entry("preludeStepSeconds",.05d),java.util.Map.entry("comboStepSeconds",.05d),
        java.util.Map.entry("drawSeconds",.05d),java.util.Map.entry("stormInterval",.05d),java.util.Map.entry("stormDeploy",.05d),
        java.util.Map.entry("finishDelay",.05d),java.util.Map.entry("brandInterval",.05d),java.util.Map.entry("maxChargeSeconds",1d),java.util.Map.entry("transformSeconds",1d)));
    private static final Map<String,Double> DISTANCE_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("range",.5d),java.util.Map.entry("radius",.5d),java.util.Map.entry("width",.5d),
        java.util.Map.entry("dashDistance",.5d),java.util.Map.entry("hopDistance",.5d),java.util.Map.entry("finisherRange",.5d),
        java.util.Map.entry("beamWidth",.5d),java.util.Map.entry("danceRadius",.5d),java.util.Map.entry("auraRadius",.5d),
        java.util.Map.entry("fieldRadius",.5d),java.util.Map.entry("blastRadius",.5d),java.util.Map.entry("boltRange",.5d),
        java.util.Map.entry("gearRange",.5d)));
    private static final Map<String,Double> SPEED_FLOORS=new java.util.HashMap<>(Map.ofEntries(
        java.util.Map.entry("projectileSpeed",1d),java.util.Map.entry("fireSpeed",1d),java.util.Map.entry("gearSpeed",1d),
        java.util.Map.entry("huntSpeed",1d),java.util.Map.entry("chargeMultiplier",1d)));
    private static final Set<String> RATIO_KEYS=Set.of("swordFirstShare","finisherShare","elementSplit","beamDamageTaken");
    public static Snapshot resolve(SkillDefinition skill,int rank,Collection<ParameterModifier> changes){
        Map<String,Double> p=SkillBalance.parameters(skill,rank);
        Map<String,Double> additions=new HashMap<>(),products=new HashMap<>();
        Set<String> seen=new HashSet<>();
        for(ParameterModifier c:changes)if(c.skill().equals(skill.id)){
            if(!seen.add(c.source()+"/"+c.parameter()))throw new IllegalArgumentException("Duplicate skill source");
            additions.merge(c.parameter(),c.additive(),Double::sum);
            products.merge(c.parameter(),c.multiplier(),(a,b)->a*b);
        }
        for(String k:new HashSet<>(p.keySet()))p.put(k,Math.max(0,(p.get(k)+additions.getOrDefault(k,0d))*products.getOrDefault(k,1d)));
        p.put("slashes",Math.max(1,Math.floor(p.get("slashes"))));
        for(var floor:COUNT_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(1,Math.floor(v)));
        for(var floor:TIME_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(floor.getValue(),v));
        for(var floor:DISTANCE_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(floor.getValue(),v));
        for(var floor:SPEED_FLOORS.entrySet())p.computeIfPresent(floor.getKey(),(k,v)->Math.max(floor.getValue(),v));
        for(String k:RATIO_KEYS)p.computeIfPresent(k,(key,v)->Math.clamp(v,0,1));
        return new Snapshot(skill,rank,p);
    }
}

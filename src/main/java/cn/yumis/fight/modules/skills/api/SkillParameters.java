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
        return new Snapshot(skill,rank,p);
    }
}

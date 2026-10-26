package cn.yumis.fight.api;
import java.util.*;
import cn.yumis.fight.rules.Rules.*;
/** Resolved once at cast start; equipment integrations can supply additive and multiplicative changes. */
public final class SkillParameters {
    private static final Map<String,java.util.function.BiFunction<UUID,Skill,Collection<Change>>> SOURCES=new LinkedHashMap<>();
    public static synchronized void register(String id,java.util.function.BiFunction<UUID,Skill,Collection<Change>> provider){
        SOURCES.put(id,Objects.requireNonNull(provider));
    }
    public static synchronized void remove(String id){
        SOURCES.remove(id);
    }
    public static synchronized Snapshot forCast(UUID player,Skill skill,int rank,Collection<Change> equipment){
        List<Change> all=new ArrayList<>(equipment);
        for(var provider:SOURCES.values())all.addAll(provider.apply(player,skill));
        return resolve(skill,rank,all);
    }
    public record Change(String source,Skill skill,String parameter,double additive,double multiplier) {
        public Change{
            if(!Double.isFinite(additive)||!Double.isFinite(multiplier)||multiplier<0)throw new IllegalArgumentException("Invalid skill modifier");
        }
    }
    public record Snapshot(Skill skill,int rank,Map<String,Double> values){
        public Snapshot{
            values=Map.copyOf(values);
        }
        public double get(String key,double fallback){
            return values.getOrDefault(key,fallback);
        }
    }
    private SkillParameters(){
    }
    public static Snapshot resolve(Skill skill,int rank,Collection<Change> changes){
        Map<String,Double> p=new LinkedHashMap<>();
        p.put("range",switch(skill){
            case DRAW->5d;case WAVE->10d;case STORM->5d;case DANCE->4d;default->3d;
        }
        );
        p.put("slashes",skill==Skill.DANCE?10d:skill==Skill.STORM?24d:skill==Skill.TRIPLE?3d:skill==Skill.DRAGON?4d:1d);
        p.put("damage",skill.coefficient(rank));
        p.put("finisher",(skill==Skill.DANCE?5:skill==Skill.STORM?8:skill==Skill.RISING?2.4:0)*Math.pow(1.12,Math.max(0,rank-1)));
        p.put("interval",skill==Skill.DANCE?.125:skill==Skill.STORM?.25:.2);
        p.put("duration",skill==Skill.STORM?8d:skill==Skill.DANCE?1.25:.8);
        Map<String,Double> additions=new HashMap<>(),products=new HashMap<>();
        Set<String> seen=new HashSet<>();
        for(Change c:changes)if(c.skill==skill){
            if(!seen.add(c.source+"/"+c.parameter))throw new IllegalArgumentException("Duplicate skill source");
            additions.merge(c.parameter,c.additive,Double::sum);
            products.merge(c.parameter,c.multiplier,(a,b)->a*b);
        }
        for(String k:new HashSet<>(p.keySet()))p.put(k,Math.max(0,(p.get(k)+additions.getOrDefault(k,0d))*products.getOrDefault(k,1d)));
        p.put("slashes",Math.max(1,Math.floor(p.get("slashes"))));
        return new Snapshot(skill,rank,p);
    }
}

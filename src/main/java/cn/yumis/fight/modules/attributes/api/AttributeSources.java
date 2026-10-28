package cn.yumis.fight.modules.attributes.api;

import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import java.util.*;
/** Stable extension point. Revisions prevent removed or replaced sources being resurrected. */
public final class AttributeSources {
    public enum Operation {
        ADD,BASE_RATIO,TOTAL_RATIO
    }
    public enum Stage {
        ITEM,CHARACTER
    }
    public record Value(String attribute,double value,Operation operation,Stage stage){
        public Value{
            if(attribute==null||!attribute.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Attribute ID must be namespaced");
            Objects.requireNonNull(operation);
            Objects.requireNonNull(stage);
            finite(value);
        }
        public Value(Stat stat,double value,Operation operation,Stage stage){
            this(id(stat),value,operation,stage);
        }
    }
    public record Source(String id,long revision,List<Value> values){
        public Source{
            values=List.copyOf(values);
        }
    }
    private long revision=-1;
    private final Map<String,Source> sources=new LinkedHashMap<>();
    private final Map<String,Long> removed=new HashMap<>();
    public synchronized boolean replace(long revision,List<Source> next){
        if(revision<this.revision)return false;
        Map<String,Source> m=new LinkedHashMap<>();
        for(Source s:next){
            if(m.put(s.id,s)!=null)throw new IllegalArgumentException("Duplicate attribute source");
            Set<String> fields=new HashSet<>();
            for(Value v:s.values)if(!fields.add(v.attribute+"/"+v.operation+"/"+v.stage))throw new IllegalArgumentException("Duplicate source category");
            Source old=sources.get(s.id);
            if(old!=null&&(s.revision<old.revision||s.revision==old.revision&&!s.values.equals(old.values)))throw new IllegalArgumentException("Conflicting per-source revision");
            if(old==null&&removed.getOrDefault(s.id,Long.MIN_VALUE)>=s.revision)throw new IllegalArgumentException("Removed source cannot be revived by an old revision");
        }
        if(revision==this.revision){
            if(!sources.equals(m))throw new IllegalArgumentException("Conflicting source revision");
            return false;
        }
        for(Source old:sources.values())if(!m.containsKey(old.id))removed.merge(old.id,old.revision,Math::max);
        sources.clear();
        sources.putAll(m);
        this.revision=revision;
        return true;
    }
    public synchronized Map<Stat,Double> apply(Map<Stat,Double> base){
        return apply(apply(base,Stage.ITEM),Stage.CHARACTER);
    }
    public static String id(Stat stat){
        return "yumis_dungeon_and_fight:"+stat.name().toLowerCase(Locale.ROOT);
    }
    public synchronized Map<Stat,Double> apply(Map<Stat,Double> base,Stage stage){
        Map<String,Double> named=new LinkedHashMap<>();
        base.forEach((stat,value)->named.put(id(stat),value));
        Map<String,Double> applied=applyNamed(named,stage);
        Map<Stat,Double> result=new EnumMap<>(Stat.class);
        for(Stat stat:Stat.values())result.put(stat,applied.getOrDefault(id(stat),0d));
        return result;
    }
    public synchronized Map<String,Double> applyNamed(Map<String,Double> base,Stage stage){
        Map<String,Double> result=new LinkedHashMap<>(base);
        Set<String> attributes=new LinkedHashSet<>(base.keySet());
        for(Source source:sources.values())for(Value value:source.values)if(value.stage==stage)attributes.add(value.attribute);
        for(String attribute:attributes){
            double add=0,ratio=0,total=1;
            for(Source source:sources.values())for(Value v:source.values)if(v.attribute.equals(attribute)&&v.stage==stage){
                switch(v.operation){
                    case ADD->add+=v.value;
                    case BASE_RATIO->ratio+=v.value;
                    case TOTAL_RATIO->total*=1+v.value;
                }
            }
            result.put(attribute,(result.getOrDefault(attribute,0d)*(1+ratio)+add)*total);
        }
        return result;
    }
    public synchronized long revision(){
        return revision;
    }

    public synchronized void applyCharacter(cn.yumis.fight.modules.attributes.api.Stats stats){
        for(Source source:sources.values())for(Value v:source.values)if(v.stage==Stage.CHARACTER){
            String key=v.attribute.startsWith("yumis_dungeon_and_fight:")?v.attribute.substring(v.attribute.indexOf(':')+1).toUpperCase(Locale.ROOT):v.attribute;
            var a=stats.attribute(key,0);
            switch(v.operation){case ADD->a.add+=v.value;case BASE_RATIO->a.mut+=v.value;case TOTAL_RATIO->a.more(v.value);}
        }
    }
    public synchronized List<Source> snapshot(){
        return List.copyOf(sources.values());
    }
}

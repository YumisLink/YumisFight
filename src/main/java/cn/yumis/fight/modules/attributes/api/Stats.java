package cn.yumis.fight.modules.attributes.api;
import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;

import java.util.*;

import cn.yumis.fight.architecture.AttributeValue;

/** attributes模块拥有的数据合同；仅接收当前存档代际。 */
public class Stats {
    public Map<String,AttributeValue> attributes=new LinkedHashMap<>();
    /** Computed views, never independent numeric storage. Serialized snapshots use attributes. */
    public transient Map<Stat,Double> values=new AbstractMap<>(){
        public Double get(Object key){return key instanceof Stat s&&attributes.containsKey(s.name())?Stats.this.get(s):null;}
        public Double put(Stat key,Double value){Double old=get(key);finite(value);attributes.put(key.name(),new AttributeValue(value));return old;}
        public void clear(){for(Stat s:Stat.values())attributes.remove(s.name());}
        public Set<Entry<Stat,Double>> entrySet(){Set<Entry<Stat,Double>> out=new LinkedHashSet<>();for(Stat s:Stat.values())if(attributes.containsKey(s.name()))out.add(new SimpleImmutableEntry<>(s,Stats.this.get(s)));return out;}
    };
    public transient Map<String,Double> modifiers=new AbstractMap<>(){
        public Double get(Object key){String id="modifier/"+key;var a=attributes.get(id);if(a==null)return null;double v=a.value()-(key.equals("final")?1:0);return key.equals("crit")?Math.clamp(v,0,1):v;}
        public Double put(String key,Double value){Double old=get(key);finite(value);var a=new AttributeValue(key.equals("final")?1:value);if(key.equals("final"))a.mut=value;attributes.put("modifier/"+key,a);return old;}
        public Double remove(Object key){Double old=get(key);attributes.remove("modifier/"+key);return old;}
        public void clear(){attributes.keySet().removeIf(k->k.startsWith("modifier/"));}
        public Set<Entry<String,Double>> entrySet(){Set<Entry<String,Double>> out=new LinkedHashSet<>();for(String k:attributes.keySet())if(k.startsWith("modifier/")){String id=k.substring(9);out.add(new SimpleImmutableEntry<>(id,get(id)));}return out;}
    };
    public Map<String,Integer> ranks=new LinkedHashMap<>();
    public List<ExtraDamage> extras=new ArrayList<>();
    public List<ParameterModifier> parameters=new ArrayList<>();
    public String weaponInstance="",weaponType="";
    public int armorSet,jewelrySet;
    public boolean weaponValid;
    public double get(Stat s){
        var a=attributes.get(s.name());return a==null?0:a.value();
    }
    public AttributeValue attribute(Stat s){return attribute(s.name(),0);}
    public AttributeValue attribute(String key,double initial){return attributes.computeIfAbsent(key,k->new AttributeValue(initial));}
    public AttributeValue modifier(String key){return attribute("modifier/"+key,key.equals("final")||key.equals("skillProduct")?1:0);}
    public void mut(Stat s,double v){finite(v);attribute(s).mut+=v;}
    public void extra(Stat s,double v){finite(v);attribute(s).add+=v;}
    public void add(Stat s,double v){
        finite(v);
        attribute(s).base+=v;
    }

    public double panel(boolean magic){
        return get(magic?Stat.MAGICAL_ATTACK:Stat.PHYSICAL_ATTACK)*(1+get(magic?Stat.INTELLIGENCE:Stat.STRENGTH)/100);
    }
}

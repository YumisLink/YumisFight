package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.attributes.api.Stat;

import static cn.yumis.fight.modules.attributes.api.AttributeNumbers.finite;
import static cn.yumis.fight.modules.equipment.api.EquipmentCurves.defenses;
import cn.yumis.fight.modules.equipment.api.EquipmentJson;
import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
/** Data definitions and item instances are distinct: display text never determines an effect. */
public final class EquipmentDefinitions {
    public record Curve(Stat stat,double base,double growth,String reference,double factor){
        public double at(Slot slot,int level){
            double value=base+growth*(level-1);
            if(reference!=null&&!reference.isEmpty()){
                if(reference.equals("armor_physical"))value=defenses(level,false)[slot.ordinal()-1];
                else if(reference.equals("armor_magical"))value=defenses(level,true)[slot.ordinal()-1];
                else throw new IllegalArgumentException("Unknown curve "+reference);
            }
            return value*factor;
        }
    }
    public record EquipmentTemplate(String id,Slot slot,List<Curve> base){
    }
    public record ArmorTemplate(String name,double defense,double shield,double evasion){}
    public record JewelryTemplate(String name,List<Curve> base){}
    public record SetEffect(int pieces,Stat stat,double value,String modifier){
    }
    public record SetDefinition(String id,String name,String group,List<SetEffect> effects){
    }
    public record Ingredient(String id,int count,boolean scaled){
    }
    public record RecipeDefinition(String id,Slot slot,int regionalCount,List<Ingredient> vanilla){
    }
    public long revision;
    public Map<Slot,EquipmentTemplate> equipment=new EnumMap<>(Slot.class);
    public Map<ArmorType,ArmorTemplate> armorTypes=new EnumMap<>(ArmorType.class);
    public Map<JewelryType,JewelryTemplate> jewelryTypes=new EnumMap<>(JewelryType.class);
    public Map<String,SetDefinition> sets=new LinkedHashMap<>();
    public Map<Slot,RecipeDefinition> recipes=new EnumMap<>(Slot.class);
    private static final EquipmentDefinitions DEFAULT=defaults();
    private static volatile java.util.function.Supplier<EquipmentDefinitions> source;
    public static EquipmentDefinitions current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<EquipmentDefinitions> value){source=java.util.Objects.requireNonNull(value);}
    public static EquipmentDefinitions defaults(){
        try(var input=EquipmentDefinitions.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/equipment.json")){
            if(input==null)throw new IllegalStateException("Missing equipment definitions");
            return parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        catch(java.io.IOException e){
            throw new IllegalStateException(e);
        }
    }
    public static EquipmentDefinitions parse(String json){
        EquipmentDefinitions d=EquipmentJson.JSON.fromJson(json,EquipmentDefinitions.class);
        for(ArmorType type:ArmorType.values())if(type!=ArmorType.UNTYPED){
            ArmorTemplate t=d.armorTypes.get(type);
            if(t==null||!Double.isFinite(t.defense)||!Double.isFinite(t.shield)||!Double.isFinite(t.evasion)||t.defense<=0||t.shield<0||t.evasion<0)throw new IllegalArgumentException("Invalid armor type "+type);
        }
        for(JewelryType type:JewelryType.values())if(type!=JewelryType.UNTYPED){
            JewelryTemplate t=d.jewelryTypes.get(type);
            if(t==null||t.base==null||t.base.isEmpty())throw new IllegalArgumentException("Missing jewelry type "+type);
            for(Curve c:t.base)for(int l=1;l<=80;l++){double v=c.at(Slot.RING,l);finite(v);if(v<0)throw new IllegalArgumentException("Negative jewelry attribute");}
        }
        for(Slot slot:Slot.values()){
            if(!d.equipment.containsKey(slot)||!d.recipes.containsKey(slot))throw new IllegalArgumentException("Missing definition for "+slot);
            RecipeDefinition r=d.recipes.get(slot);
            if(r.regionalCount<1||r.regionalCount>64)throw new IllegalArgumentException("Invalid material cost");
            for(Curve c:d.equipment.get(slot).base)for(int l=slot.minimum();l<=80;l++){
                double value=c.at(slot,l);
                finite(value);
                if(value<0)throw new IllegalArgumentException("Negative template attribute");
            }
        }
        for(var e:d.sets.entrySet())if(!e.getKey().equals(e.getValue().id)||!Set.of("armor","jewelry").contains(e.getValue().group))throw new IllegalArgumentException("Invalid set definition");
        return d;
    }
    /** Validate the complete replacement before publishing; omitted IDs are retained for existing current-generation items. */
    public void retainRetiredFrom(EquipmentDefinitions previous){
        for(var entry:previous.sets.entrySet())sets.putIfAbsent(entry.getKey(),entry.getValue());
        revision=Math.incrementExact(previous.revision);
    }
    public List<MaterialCatalog.Need> recipe(Slot slot,int level,String regional){
        var r=recipes.get(slot);
        int q=(level+9)/10;
        List<MaterialCatalog.Need> out=new ArrayList<>();
        for(Ingredient i:r.vanilla)out.add(new MaterialCatalog.Need(i.id,i.count*(i.scaled?q:1),false));
        out.add(new MaterialCatalog.Need(regional,r.regionalCount,true));
        return List.copyOf(out);
    }
}

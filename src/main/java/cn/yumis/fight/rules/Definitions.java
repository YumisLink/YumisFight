package cn.yumis.fight.rules;
import static cn.yumis.fight.rules.Rules.*;
import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Data definitions and item instances are distinct: display text never determines an effect. */
public final class Definitions {
    public record AffixDefinition(String id,String name,String operation,String group,Stat stat,int element){
    }
    public record Curve(Stat stat,double base,double growth,String reference,double factor){
        public double at(Slot slot,int level){
            double value=base+growth*(level-1);
            if(reference!=null&&!reference.isEmpty()){
                if(reference.equals("armor_physical"))value=Rules.defenses(level,false)[slot.ordinal()-1];
                else if(reference.equals("armor_magical"))value=Rules.defenses(level,true)[slot.ordinal()-1];
                else throw new IllegalArgumentException("Unknown curve "+reference);
            }
            return value*factor;
        }
    }
    public record EquipmentTemplate(String id,Slot slot,List<Curve> base){
    }
    public record SetEffect(int pieces,Stat stat,double value,String modifier){
    }
    public record SetDefinition(String id,String name,String group,List<SetEffect> effects){
    }
    public record Ingredient(String id,int count,boolean scaled){
    }
    public record RecipeDefinition(String id,Slot slot,int regionalCount,List<Ingredient> vanilla){
    }
    public Map<String,AffixDefinition> affixes=new LinkedHashMap<>();
    public Map<Slot,EquipmentTemplate> equipment=new EnumMap<>(Slot.class);
    public Map<String,SetDefinition> sets=new LinkedHashMap<>();
    public Map<Slot,RecipeDefinition> recipes=new EnumMap<>(Slot.class);
    public static volatile Definitions CURRENT=defaults();
    public static Definitions defaults(){
        try(var input=Definitions.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/equipment.json")){
            if(input==null)throw new IllegalStateException("Missing equipment definitions");
            return parse(new String(input.readAllBytes(),StandardCharsets.UTF_8));
        }
        catch(java.io.IOException e){
            throw new IllegalStateException(e);
        }
    }
    public static Definitions parse(String json){
        Definitions d=Models.JSON.fromJson(json,Definitions.class);
        for(Slot slot:Slot.values()){
            if(!d.equipment.containsKey(slot)||!d.recipes.containsKey(slot))throw new IllegalArgumentException("Missing definition for "+slot);
            RecipeDefinition r=d.recipes.get(slot);
            if(r.regionalCount<1||r.regionalCount>64)throw new IllegalArgumentException("Invalid material cost");
            for(Curve c:d.equipment.get(slot).base)for(int l=slot.minimum();l<=80;l++){
                double value=c.at(slot,l);
                Rules.finite(value);
                if(value<0)throw new IllegalArgumentException("Negative template attribute");
            }
        }
        for(var e:d.affixes.entrySet())if(!e.getKey().equals(e.getValue().id))throw new IllegalArgumentException("Affix ID mismatch");
        for(var e:d.sets.entrySet())if(!e.getKey().equals(e.getValue().id)||!Set.of("armor","jewelry").contains(e.getValue().group))throw new IllegalArgumentException("Invalid set definition");
        return d;
    }
    public List<Catalog.Need> recipe(Slot slot,int level,String regional){
        var r=recipes.get(slot);
        int q=(level+9)/10;
        List<Catalog.Need> out=new ArrayList<>();
        for(Ingredient i:r.vanilla)out.add(new Catalog.Need(i.id,i.count*(i.scaled?q:1),false));
        out.add(new Catalog.Need(regional,r.regionalCount,true));
        return List.copyOf(out);
    }
}

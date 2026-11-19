package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.equipment.api.EquipmentJson;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class MaterialCatalog {
    public record Material(String id,String name,String texture){}
    public record Region(int minimum,int maximum,String equipment,String armor){}
    public record Need(String id,int count,boolean regional){}
    public record Alternative(String id,int minimum,int maximum,List<Slot> slots,Need ingredient){}
    public Map<String,Material> materials=new LinkedHashMap<>();
    public List<Region> regions=new ArrayList<>();
    public List<Alternative> alternatives=new ArrayList<>();
    public long revision=1;
    private static final MaterialCatalog DEFAULT=defaults();
    private static volatile java.util.function.Supplier<MaterialCatalog> source;
    public static MaterialCatalog current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<MaterialCatalog> value){source=Objects.requireNonNull(value);}
    public static MaterialCatalog defaults(){try(var in=MaterialCatalog.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/catalog.json")){if(in==null)throw new IllegalStateException("Missing catalog");return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static MaterialCatalog parse(String json){
        var c=EquipmentJson.JSON.fromJson(json,MaterialCatalog.class);
        if(c==null||c.materials==null||c.regions==null)throw new IllegalArgumentException("Missing material catalog");
        for(var entry:c.materials.entrySet())if(entry.getValue()==null||!entry.getKey().equals(entry.getValue().id()))throw new IllegalArgumentException("Material ID mismatch");
        for(int level=1;level<=80;level++){int l=level;var ranges=c.regions.stream().filter(x->l>=x.minimum&&l<=x.maximum).toList();if(ranges.size()!=1||!c.materials.containsKey(ranges.getFirst().equipment)||!c.materials.containsKey(ranges.getFirst().armor))throw new IllegalArgumentException("Invalid regional coverage at "+level);}
        var ids=new HashSet<String>();
        for(var option:c.alternatives){
            if(option==null||option.id()==null||!option.id().matches("[a-z0-9_]+")||!ids.add(option.id())
                    ||option.minimum()<1||option.maximum()>80||option.minimum()>option.maximum()
                    ||option.slots()==null||option.slots().isEmpty()||option.slots().contains(null)
                    ||option.ingredient()==null||option.ingredient().id()==null||!option.ingredient().id().contains(":")
                    ||option.ingredient().regional()||option.ingredient().count()<1){
                throw new IllegalArgumentException("装备专材配方定义无效");
            }
        }
        return c;
    }
    public String material(Slot slot,int level){
        return regions.stream().filter(x->level>=x.minimum&&level<=x.maximum).map(x->slot.armor()?x.armor:x.equipment).findFirst().orElseThrow();
    }
    public List<Need> recipe(Slot slot,int level){
        if(level<slot.minimum()||level>80)throw new IllegalArgumentException("装备等级未开放");
        return EquipmentDefinitions.current().recipe(slot,level,material(slot,level));
    }
    public List<Alternative> alternatives(Slot slot,int level){
        return alternatives.stream().filter(option->option.slots().contains(slot)
            &&level>=option.minimum()&&level<=option.maximum()).toList();
    }
    public List<Need> recipe(Slot slot,int level,String alternative){
        var standard=recipe(slot,level);
        if(alternative.isEmpty())return standard;
        var option=alternatives(slot,level).stream().filter(entry->entry.id().equals(alternative)).findFirst()
            .orElseThrow(()->new IllegalArgumentException("当前等级和部位没有该专材配方"));
        return standard.stream().map(need->need.regional()?option.ingredient():need).toList();
    }
}

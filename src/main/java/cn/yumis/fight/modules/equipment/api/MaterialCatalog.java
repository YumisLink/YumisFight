package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.equipment.api.EquipmentJson;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class MaterialCatalog {
    public record Material(String id,String name,String texture){}
    public record Region(int minimum,int maximum,String equipment,String armor){}
    public record Need(String id,int count,boolean regional){}
    public Map<String,Material> materials=new LinkedHashMap<>();
    public List<Region> regions=new ArrayList<>();
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
        return c;
    }
    public String material(Slot slot,int level){
        return regions.stream().filter(x->level>=x.minimum&&level<=x.maximum).map(x->slot.armor()?x.armor:x.equipment).findFirst().orElseThrow();
    }
    public List<Need> recipe(Slot slot,int level){
        if(level<slot.minimum()||level>80)throw new IllegalArgumentException("装备等级未开放");
        return EquipmentDefinitions.current().recipe(slot,level,material(slot,level));
    }
}

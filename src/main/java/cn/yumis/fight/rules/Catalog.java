package cn.yumis.fight.rules;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static cn.yumis.fight.rules.Rules.*;
/** Server-reloadable gameplay definitions; IDs remain stable when names or art change. */
public final class Catalog {
    public static volatile Catalog CURRENT=defaults();
    public record Material(String id,String name,String texture) {
    }
    public record Region(int minimum,int maximum,String equipment,String armor) {
    }
    public record DungeonDef(String id,String name,int level,boolean available,int cell,List<String> materials) {
    }
    public record Need(String id,int count,boolean regional) {
    }
    public Map<String,Material> materials=new LinkedHashMap<>();
    public List<Region> regions=new ArrayList<>();
    public List<DungeonDef> dungeons=new ArrayList<>();
    public Map<String,String> statNames=new LinkedHashMap<>();
    public JsonObject templates=new JsonObject();
    public long revision=1;
    public static Catalog defaults(){
        try(InputStream in=Catalog.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/catalog.json")){
            if(in==null)throw new IllegalStateException("Missing catalog");
            return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));
        }
        catch(IOException e){
            throw new IllegalStateException(e);
        }
    }
    public static Catalog parse(String source){
        Catalog c=Models.JSON.fromJson(source,Catalog.class);
        Set<String> ids=new HashSet<>();
        for(DungeonDef d:c.dungeons){
            if(!ids.add(d.id)||d.level<1||d.level>80||d.materials.isEmpty())throw new IllegalArgumentException("Invalid dungeon definition");
            for(String m:d.materials)if(!c.materials.containsKey(m))throw new IllegalArgumentException("Unknown material "+m);
        }
        for(int level=1;level<=80;level++){
            int l=level;
            List<Region> r=c.regions.stream().filter(x->l>=x.minimum&&l<=x.maximum).toList();
            if(r.size()!=1||!c.materials.containsKey(r.getFirst().equipment)||!c.materials.containsKey(r.getFirst().armor))throw new IllegalArgumentException("Invalid regional coverage at "+level);
        }
        return c;
    }
    public DungeonDef dungeon(String id){
        return dungeons.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知副本"));
    }
    public String material(Slot slot,int level){
        return regions.stream().filter(x->level>=x.minimum&&level<=x.maximum).map(x->slot.armor()?x.armor:x.equipment).findFirst().orElseThrow();
    }
    public List<Need> recipe(Slot slot,int level){
        if(level<slot.minimum()||level>80)throw new IllegalArgumentException("装备等级未开放");
        return Definitions.CURRENT.recipe(slot,level,material(slot,level));
    }
    public static String statName(Stat stat){
        return CURRENT.statNames.getOrDefault(stat.name(),stat.name());
    }
}

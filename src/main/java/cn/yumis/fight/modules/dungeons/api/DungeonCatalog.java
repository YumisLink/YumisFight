package cn.yumis.fight.modules.dungeons.api;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class DungeonCatalog {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public record DungeonDef(String id,String name,int level,boolean available,int cell,List<String> materials){}
    public List<DungeonDef> dungeons=new ArrayList<>();
    public long revision=1;
    private static final DungeonCatalog DEFAULT=defaults();
    private static volatile java.util.function.Supplier<DungeonCatalog> source;
    public static DungeonCatalog current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<DungeonCatalog> value){source=Objects.requireNonNull(value);}
    public static DungeonCatalog defaults(){try(var in=DungeonCatalog.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/catalog.json")){if(in==null)throw new IllegalStateException("Missing catalog");return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static DungeonCatalog parse(String json){var c=JSON.fromJson(json,DungeonCatalog.class);if(c==null||c.dungeons==null)throw new IllegalArgumentException("Missing dungeon catalog");Set<String> ids=new HashSet<>();for(var d:c.dungeons)if(!ids.add(d.id)||d.level<1||d.level>80||d.materials==null||d.materials.isEmpty())throw new IllegalArgumentException("Invalid dungeon definition");return c;}
    public DungeonDef dungeon(String id){return dungeons.stream().filter(x->x.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("未知副本"));}
}

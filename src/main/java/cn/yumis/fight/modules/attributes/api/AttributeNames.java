package cn.yumis.fight.modules.attributes.api;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class AttributeNames {
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();

    public Map<String,String> statNames=new LinkedHashMap<>();
    private static final AttributeNames DEFAULT=defaults();
    private static volatile java.util.function.Supplier<AttributeNames> source;
    public static AttributeNames current(){var read=source;return read==null?DEFAULT:read.get();}
    public static void source(java.util.function.Supplier<AttributeNames> value){source=Objects.requireNonNull(value);}
    public static AttributeNames defaults(){try(var in=AttributeNames.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/catalog.json")){if(in==null)throw new IllegalStateException("Missing catalog");return parse(new String(in.readAllBytes(),StandardCharsets.UTF_8));}catch(java.io.IOException e){throw new IllegalStateException(e);}}
    public static AttributeNames parse(String json){var c=JSON.fromJson(json,AttributeNames.class);if(c==null||c.statNames==null)throw new IllegalArgumentException("Missing attribute names");return c;}
    public static String statName(Stat stat){return current().statNames.getOrDefault(stat.name(),stat.name());}
}

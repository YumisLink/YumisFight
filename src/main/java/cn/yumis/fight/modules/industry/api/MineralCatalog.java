package cn.yumis.fight.modules.industry.api;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Mining energy is the complete batch cost at the minimum tier, normal deposit and dry operation. */
public final class MineralCatalog {
    public record Mineral(int tier,int chance,int ticks,long energy,String item,int count){}
    private static final Map<String,Mineral> DEFAULTS=load();
    private MineralCatalog(){}
    public static boolean universal(String id){return id.equals("cobblestone");}
    public static Map<String,Mineral> defaults(){return new LinkedHashMap<>(DEFAULTS);}
    private static Map<String,Mineral> load(){
        try(var stream=Objects.requireNonNull(MineralCatalog.class.getResourceAsStream("/data/yumis_dungeon_and_fight/rules/industrial.json"))){
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("minerals");
            var result=new LinkedHashMap<String,Mineral>();var gson=new Gson();
            for(var entry:json.entrySet())result.put(entry.getKey(),gson.fromJson(entry.getValue(),Mineral.class));
            return Collections.unmodifiableMap(result);
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}

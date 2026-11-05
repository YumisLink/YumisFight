package cn.yumis.fight.modules.industry.api;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Industrial content grades. Technology, processing access and machine layout are independent. */
public final class IndustrialTechnology {
    private static final JsonObject DATA=read();
    private IndustrialTechnology(){}
    private static JsonObject read(){
        try(var in=Objects.requireNonNull(IndustrialTechnology.class.getResourceAsStream("/assets/yumis_dungeon_and_fight/industry/technology.json"),"缺少工业科技目录")){
            return JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
    public static int itemTier(String id){var value=DATA.getAsJsonObject("items").get(id);return value==null?0:value.getAsInt();}
    public static int itemTier(ItemStack item){return item.isEmpty()?0:itemTier(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());}
    public static int mineralTier(ItemStack item){if(item.isEmpty())return 0;var value=DATA.getAsJsonObject("mineral_tiers").get(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());return value==null?0:value.getAsInt();}
    public static int fluidTier(String id){var value=DATA.getAsJsonObject("fluids").get(id);return value==null?0:value.getAsInt();}
    public static String label(int tier){if(tier<1||tier>6)return "未分级";return "T"+tier+" "+DATA.getAsJsonArray("grades").get(tier-1).getAsJsonObject().get("name").getAsString();}
    public static int color(int tier){return Integer.parseInt(DATA.getAsJsonArray("grades").get(Math.clamp(tier,1,6)-1).getAsJsonObject().getAsJsonArray("colors").get(1).getAsString(),16);}
    public static JsonObject content(){return DATA.getAsJsonObject("content");}
    public static boolean isDevice(String id){return DATA.getAsJsonObject("devices").has(id);}
    public static int energyTier(String recipe){String id=recipe.startsWith("yumis_dungeon_and_fight:")?recipe.substring(recipe.indexOf(':')+1):recipe;var value=DATA.getAsJsonObject("recipe_energy_tiers").get(id);return value==null?0:value.getAsInt();}
    public static JsonObject spec(String id){var value=DATA.getAsJsonObject("devices").get(id);if(value==null)throw new IllegalArgumentException("未登记工业设备："+id);return value.getAsJsonObject();}
    public static int technology(String id){return spec(id).get("technology").getAsInt();}
    public static int processing(String id){return spec(id).get("processing").getAsInt();}
    public static Map<String,Integer> upgradeCosts(String id){
        var definition=spec(id);var result=new LinkedHashMap<String,Integer>();
        if(definition.has("upgrade_costs"))for(var entry:definition.getAsJsonObject("upgrade_costs").entrySet())result.put(entry.getKey(),entry.getValue().getAsInt());
        return Collections.unmodifiableMap(result);
    }
}

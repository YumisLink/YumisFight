package cn.yumis.fight.modules.economy.client;

import cn.yumis.fight.modules.economy.api.AutoSellRule;
import com.google.gson.*;

import java.nio.file.*;
import java.util.*;

/** 本机自动出售偏好（总开关加多条规则）；纯客户端数据，绝不上传服务器。 */
public final class AutoSellSettings {
    private static final List<AutoSellRule> RULES=new ArrayList<>();
    private static boolean enabled,loaded;
    private static long revision;
    private static String error="";
    private AutoSellSettings(){}
    private static Path path(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight-modules-v1-auto-sell.json");}
    private static void load(){
        if(loaded)return;loaded=true;
        try{
            if(Files.exists(path())){
                var root=JsonParser.parseString(Files.readString(path())).getAsJsonObject();
                enabled=root.has("enabled")&&root.get("enabled").getAsBoolean();
                if(root.has("rules"))for(var raw:root.getAsJsonArray("rules")){
                    var rule=AutoSellRule.decode(raw);
                    if(rule!=null)RULES.add(rule);
                }
            }
        }catch(Exception failure){error="自动出售配置读取失败";System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,error,failure);}
    }
    public static long revision(){load();return revision;}
    public static String error(){load();return error;}
    public static boolean enabled(){load();return enabled;}
    public static List<AutoSellRule> rules(){load();return List.copyOf(RULES);}
    /** 整体替换本机配置；监视器按 revision 感知变化，失败时回滚内存态。 */
    public static void commit(boolean active,List<AutoSellRule> next){
        load();
        var before=List.copyOf(RULES);boolean previous=enabled;
        enabled=active;RULES.clear();
        for(var rule:next)if(rule!=null)RULES.add(rule);
        try{
            var file=path();Files.createDirectories(file.getParent());
            var temporary=file.resolveSibling(file.getFileName()+".tmp");
            var root=new JsonObject();root.addProperty("enabled",enabled);
            var rules=new JsonArray();for(var rule:RULES)rules.add(rule.encode());
            root.add("rules",rules);
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(root));
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            revision++;error="";
        }catch(Exception failure){
            enabled=previous;RULES.clear();RULES.addAll(before);
            error="自动出售配置保存失败";System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,error,failure);
        }
    }
}

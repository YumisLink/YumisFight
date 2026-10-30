package cn.yumis.fight.modules.affixes.api.client;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.network.chat.Component;
import cn.yumis.fight.modules.affixes.api.Affix;
import cn.yumis.fight.modules.affixes.api.AffixResolver;

/** Local display preferences keyed by stable definitions; never sent to the server or written into gear. */
public final class AffixFavorites {
    private static final Set<String> IDS=new TreeSet<>();
    private static boolean loaded;
    private static long revision;
    private static String error="";
    private AffixFavorites(){}
    private static Path path(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("yumis_dungeon_and_fight-modules-v1-affix-favorites.json");}
    private static void load(){
        if(loaded)return;loaded=true;
        try{if(Files.exists(path()))for(var entry:JsonParser.parseString(Files.readString(path())).getAsJsonArray())if(entry.isJsonPrimitive()&&entry.getAsJsonPrimitive().isString())IDS.add(entry.getAsString());}
        catch(Exception failure){error="词条收藏读取失败";System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,error,failure);}
    }
    public static long revision(){load();return revision;}
    public static String error(){load();return error;}
    public static boolean contains(String id){load();return IDS.contains(id);}
    public static void toggle(String id){
        load();boolean previous=IDS.contains(id);if(previous)IDS.remove(id);else IDS.add(id);
        try{
            var file=path();Files.createDirectories(file.getParent());var temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(IDS));
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            revision++;error="";
        }catch(Exception failure){if(previous)IDS.add(id);else IDS.remove(id);error="词条收藏保存失败";System.getLogger("yumis_dungeon_and_fight").log(System.Logger.Level.WARNING,error,failure);}
    }
    public static Component decorate(Affix affix,Component text){return decorate(AffixResolver.definitionId(affix),text);}
    public static Component decorate(String id,Component text){return contains(id)?Component.literal("★ ").withColor(0xffd778).append(text.copy()):text;}
}

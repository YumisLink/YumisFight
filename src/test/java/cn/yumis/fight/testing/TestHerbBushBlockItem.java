package cn.yumis.fight.testing;
import cn.yumis.fight.modules.professions.platform.HerbContent;
import cn.yumis.fight.platform.ItemRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

/** 野生草药丛 BlockItem：注册进共享物品表且与方块同 ID；外观与名称沿用既有兜底，不留缺失贴图模型。 */
class TestHerbBushBlockItem {
    /** 静默事件总线：注册监听不做任何事，仅供无头环境走完 HerbContent.register 的正式登记路径。 */
    private static final IEventBus SILENT_BUS=new IEventBus(){
        @Override public void register(Object target){}
        @Override public <T extends Event> void addListener(Consumer<T> handler){}
        @Override public <T extends Event> void addListener(Class<T> type,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(EventPriority priority,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(EventPriority priority,Class<T> type,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(EventPriority priority,boolean receiveCancelled,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(EventPriority priority,boolean receiveCancelled,Class<T> type,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(boolean receiveCancelled,Consumer<T> handler){}
        @Override public <T extends Event> void addListener(boolean receiveCancelled,Class<T> type,Consumer<T> handler){}
        @Override public void unregister(Object target){}
        @Override public <T extends Event> T post(T event){return event;}
        @Override public <T extends Event> T post(EventPriority priority,T event){return event;}
        @Override public void start(){}
    };

    @BeforeAll static void registerHerbContent(){
        // HerbContent 静态初始化引用原版方块与音效常量，先完成原版注册表引导再走正式登记路径。
        try{net.minecraft.server.Bootstrap.bootStrap();}catch(Throwable ignored){}
        HerbContent.register(SILENT_BUS);
    }

    private static Path asset(String path){
        for(Path candidate=Paths.get(System.getProperty("user.dir","."));candidate!=null;candidate=candidate.getParent()){
            Path file=candidate.resolve("src/main/resources/assets/yumis_dungeon_and_fight").resolve(path);
            if(Files.isRegularFile(file))return file;
        }
        throw new IllegalStateException("找不到资源："+path);
    }

    @Test void bushItemRegistersIntoSharedItemRegistry(){
        assertEquals("herb_bush",HerbContent.BUSH_ITEM_ID);
        assertEquals("herb_bush",HerbContent.HERB_BUSH.getId().getPath());
        var holder=ItemRegistry.ALL.get(HerbContent.BUSH_ITEM_ID);
        assertNotNull(holder,"野生草药丛的物品未进入共享物品表");
        assertEquals(HerbContent.HERB_BUSH.getId(),holder.getId(),"物品与方块注册 ID 必须一致，创造中键复制才能拿到该方块");
    }

    @Test void itemModelUsesVanillaGrassFallback() throws Exception{
        var model=new Gson().fromJson(Files.readString(asset("items/herb_bush.json"),StandardCharsets.UTF_8),JsonObject.class);
        var entry=model.getAsJsonObject("model");
        assertEquals("minecraft:model",entry.get("type").getAsString());
        assertEquals("minecraft:item/short_grass",entry.get("model").getAsString(),"无专属贴图的草药外观须兜底到原版草");
        var tint=entry.getAsJsonArray("tints").get(0).getAsJsonObject();
        assertEquals("minecraft:grass",tint.get("type").getAsString());
        assertEquals(0.5,tint.get("temperature").getAsDouble());
        assertEquals(1.0,tint.get("downfall").getAsDouble());
    }

    @Test void blockLangEntryNamesThePickedItem() throws Exception{
        var lang=new Gson().fromJson(Files.readString(asset("lang/zh_cn.json"),StandardCharsets.UTF_8),JsonObject.class);
        assertEquals("野生草药丛",lang.get("block.yumis_dungeon_and_fight.herb_bush").getAsString(),
            "BlockItem 名称键派生自方块 ID，必须命中既有语言词条");
    }
}

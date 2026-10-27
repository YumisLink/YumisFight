package cn.yumis.fight.client;

import cn.yumis.fight.game.GameItems;
import cn.yumis.fight.mixin.FontProviderAccessor;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Client-local font library. It never changes Minecraft's default font or language setting. */
public final class UiFonts {
    public static final String PREVIEW="字体预览：天地玄黄 技能伤害 ABC abc 0123456789 +−%";
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final LinkedHashMap<String,String> library=new LinkedHashMap<>();
    private static Font uiFont,baseFont;
    private static volatile boolean busy;
    private static String active="modern",status="";
    private UiFonts(){}
    private static Path root(){return FMLPaths.CONFIGDIR.get().resolve(GameItems.NS).resolve("fonts");}
    private static Path pack(){return root().resolve("pack");}
    private static Path fontRoot(){return pack().resolve("assets").resolve(GameItems.NS).resolve("font");}
    private static Path file(String id){
        if(!id.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("无效字体ID");
        return fontRoot().resolve("imported").resolve(id+".ttf");
    }
    public static void register(IEventBus bus){
        library.put("modern","Modern UI (Inter + 思源黑体)（默认）");
        try{
            Path index=root().resolve("index.json");
            if(Files.isRegularFile(index)){
                JsonObject saved=JsonParser.parseString(Files.readString(index)).getAsJsonObject();
                for(var entry:saved.entrySet())if(entry.getKey().matches("[a-f0-9]{64}")&&entry.getValue().isJsonPrimitive()&&Files.isRegularFile(file(entry.getKey())))library.put(entry.getKey(),entry.getValue().getAsString());
            }
            active="modern";
            if(!active.equals("modern"))UiOpenType.validate(file(active));
            writeSelection(active);
        }
        catch(Exception failure){
            active="modern";
            status="字体读取失败，已恢复Modern UI (Inter + 思源黑体)："+failure.getMessage();
            try{writeSelection(active);}catch(IOException ignored){}
        }
        if(!ClientSettings.uiFont.equals(active)){ClientSettings.uiFont=active;ClientSettings.save();}
        bus.addListener((AddPackFindersEvent event)->{
            if(event.getPackType()!=PackType.CLIENT_RESOURCES)return;
            event.addRepositorySource(consumer->consumer.accept(new Pack(
                new PackLocationInfo(GameItems.NS+"/local_fonts",Component.literal("Yumis 本地字体"),PackSource.BUILT_IN,Optional.empty()),
                new PathPackResources.PathResourcesSupplier(pack()),
                new Pack.Metadata(Component.literal("仅本模组界面使用的本地字体"),PackCompatibility.COMPATIBLE,FeatureFlagSet.of(),List.of(),true),
                new PackSelectionConfig(true,Pack.Position.TOP,true))));
        });
    }
    private static FontDescription selected(){return cn.yumis.fight.game.ModText.FONT;}
    /** String width, splitting and rendering all use the same selected glyph source. */
    public static Font font(){
        Font vanilla=Minecraft.getInstance().font;
        if(uiFont==null||baseFont!=vanilla){
            baseFont=vanilla;
            Font.Provider source=((FontProviderAccessor)(Object)vanilla).ydnf$provider();
            uiFont=new Font(new Font.Provider(){
                @Override public GlyphSource glyphs(FontDescription description){
                    return source.glyphs(description.equals(FontDescription.DEFAULT)?selected():description);
                }
                @Override public EffectGlyph effect(){return source.effect();}
            });
        }
        return uiFont;
    }
    /** Apply to this mod's text displayed by vanilla tooltip/chat renderers. */
    public static Component decorate(Component component){return component.copy().withStyle(style->style.withFont(selected()));}
    public static boolean busy(){return busy;}
    public static String status(){return status;}
    public static String currentName(){return library.getOrDefault(active,"Modern UI (Inter + 思源黑体)（默认）");}
    public static Map<String,String> choices(){return Collections.unmodifiableMap(new LinkedHashMap<>(library));}
    public static void restoreDefault(Runnable changed){select("modern",changed);}
    public static void cycle(Runnable changed){
        List<String> ids=new ArrayList<>(library.keySet());
        select(ids.get((Math.max(0,ids.indexOf(active))+1)%ids.size()),changed);
    }
    public static void chooseFile(Runnable changed){
        if(busy)return;
        busy=true;status="请选择TTF或OTF字体文件";
        CompletableFuture.supplyAsync(()->{
            try(var stack=MemoryStack.stackPush()){
                var filters=stack.mallocPointer(2);
                filters.put(stack.UTF8("*.ttf")).put(stack.UTF8("*.otf")).flip();
                return TinyFileDialogs.tinyfd_openFileDialog("选择界面字体",null,filters,"TrueType / OpenType",false);
            }
        }).whenComplete((selected,failure)->Minecraft.getInstance().execute(()->{
            if(failure!=null){finish("打开字体选择器失败："+message(failure),changed);return;}
            if(selected==null){finish("已取消选择，保留当前字体",changed);return;}
            importFile(Path.of(selected),changed);
        }));
    }
    /** Also used by verification to exercise Unicode paths without automating an OS dialog. */
    public static void importFile(Path source,Runnable changed){
        busy=true;status="正在检查字体…";
        CompletableFuture.supplyAsync(()->{
            try{
                String lower=source.getFileName().toString().toLowerCase(Locale.ROOT);
                if(!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))throw new IOException("请选择.ttf或.otf文件");
                UiOpenType.validate(source);
                byte[] bytes=Files.readAllBytes(source);
                String id=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                Path target=file(id);
                Files.createDirectories(target.getParent());
                if(!Files.exists(target))atomicWrite(target,bytes);
                return id;
            }
            catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}
        }).whenComplete((id,failure)->Minecraft.getInstance().execute(()->{
            if(failure!=null){finish("字体导入失败，保留当前字体："+message(failure),changed);return;}
            library.put(id,source.getFileName().toString());
            try{saveIndex();}
            catch(IOException error){library.remove(id);finish("无法保存字体列表："+error.getMessage(),changed);return;}
            busy=false;
            select(id,changed);
        }));
    }
    public static void select(String id,Runnable changed){
        if(busy)return;
        if(!library.containsKey(id)){finish("字体不存在，请重新导入",changed);return;}
        if(active.equals(id)){finish("已选择："+currentName(),changed);return;}
        busy=true;status="正在应用字体…";
        String previous=active;
        try{
            if(!id.equals("modern"))UiOpenType.validate(file(id));
            writeSelection(id);
        }
        catch(Exception failure){finish("字体加载失败，保留当前字体："+message(failure),changed);return;}
        UiOpenType.clearFailures();
        Minecraft.getInstance().reloadResourcePacks().orTimeout(90,TimeUnit.SECONDS).whenComplete((ignored,failure)->Minecraft.getInstance().execute(()->{
            if(failure!=null||UiOpenType.failed()){
                rollback(previous,changed,failure==null?"字体提供器加载失败":message(failure));
                return;
            }
            active=id;ClientSettings.uiFont=id;ClientSettings.save();
            finish("已应用："+currentName()+"；缺字使用默认字体",changed);
        }));
    }
    private static void rollback(String previous,Runnable changed,String reason){
        try{writeSelection(previous);}
        catch(IOException failure){active="modern";ClientSettings.uiFont="modern";ClientSettings.save();finish("字体恢复失败，使用内置字体："+reason,changed);return;}
        // Reload may already have installed the rejected provider. Restore the previous resource definition too.
        Minecraft.getInstance().reloadResourcePacks().orTimeout(90,TimeUnit.SECONDS).whenComplete((ignored,failure)->Minecraft.getInstance().execute(()->{
            active=failure==null?previous:"modern";
            ClientSettings.uiFont=active;ClientSettings.save();
            finish("字体加载失败，已恢复"+(active.equals("modern")?"内置字体":"此前字体")+"："+reason,changed);
        }));
    }
    private static void writeSelection(String id) throws IOException {
        JsonArray providers=new JsonArray();
        if(!id.equals("modern")){
            JsonObject provider=new JsonObject();
            provider.addProperty("type","ttf");provider.addProperty("file",GameItems.NS+":imported/"+id+".ttf");
            provider.addProperty("size",9);provider.addProperty("oversample",2);
            providers.add(provider);
        }
        JsonObject fallback=new JsonObject();fallback.addProperty("type","reference");fallback.addProperty("id",GameItems.NS+":modern");providers.add(fallback);
        JsonObject definition=new JsonObject();definition.add("providers",providers);
        Files.createDirectories(fontRoot());
        atomicWrite(fontRoot().resolve("ui.json"),JSON.toJson(definition).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Retire the old managed definition: an orphan import must not poison future reloads.
        atomicWrite(fontRoot().resolve("ui_imported.json"),("{\"providers\":[{\"type\":\"reference\",\"id\":\""+GameItems.NS+":ui\"}]}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void saveIndex() throws IOException {
        JsonObject index=new JsonObject();
        library.forEach((id,name)->{if(!id.equals("modern"))index.addProperty(id,name);});
        atomicWrite(root().resolve("index.json"),JSON.toJson(index).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void atomicWrite(Path target,byte[] bytes) throws IOException {
        Path temporary=Files.createTempFile(target.getParent(),"font-",".tmp");
        try{
            Files.write(temporary,bytes);
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }
        finally{Files.deleteIfExists(temporary);}
    }
    private static String message(Throwable error){
        while(error.getCause()!=null)error=error.getCause();
        return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();
    }
    private static void finish(String result,Runnable changed){busy=false;status=result;changed.run();}
}

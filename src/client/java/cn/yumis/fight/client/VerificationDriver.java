package cn.yumis.fight.client;
import cn.yumis.fight.game.*;
import static cn.yumis.fight.rules.Models.JSON;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.*;
import java.nio.file.*;
import java.util.*;
/** Explicit opt-in developer harness, inactive in ordinary launches. Uses real menus and payloads. */
public final class VerificationDriver {
    private static long seen=-1,last;
    private VerificationDriver(){
    }
    public static void tick(){
        if(!Boolean.getBoolean("ydnf.verify"))return;
        var mc=Minecraft.getInstance();
        long now=ClientState.now();
        if(now-last<100)return;
        last=now;
        Path dir=mc.gameDirectory.toPath().resolve("verification");
        try{
            Files.createDirectories(dir);
            Path control=dir.resolve("control.json");
            if(Files.exists(control)){
                JsonObject j=JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                long id=j.get("id").getAsLong();
                if(id!=seen){
                    seen=id;
                    execute(mc,j);
                }
            }
            JsonObject status=new JsonObject();
            status.addProperty("control",seen);
            status.addProperty("ready",ClientState.INSTANCE.ready);
            status.addProperty("session",ClientState.INSTANCE.session);
            status.add("targets",JSON.toJsonTree(ClientState.INSTANCE.targets));
            status.add("party",JSON.toJsonTree(ClientState.INSTANCE.party));
            status.add("dungeon",JSON.toJsonTree(ClientState.INSTANCE.dungeon));
            status.addProperty("screen",mc.gui.screen()==null?"":mc.gui.screen().getClass().getSimpleName());
            status.addProperty("width",mc.getWindow().getGuiScaledWidth());
            status.addProperty("height",mc.getWindow().getGuiScaledHeight());
            status.add("progress",JSON.toJsonTree(ClientState.INSTANCE.progress));
            status.add("life",JSON.toJsonTree(ClientState.INSTANCE.own));
            status.add("stats",JSON.toJsonTree(ClientState.INSTANCE.stats));
            status.addProperty("pending",ClientState.INSTANCE.pending.size());
            status.add("vfxMetrics",JSON.toJsonTree(Effects.VFX.metrics()));
            status.addProperty("vfxActive",Effects.VFX.activeCount());
            if(mc.player!=null){
                status.addProperty("player",mc.player.getStringUUID());
                status.addProperty("nativeMaximumHealth",mc.player.getMaxHealth());
                status.addProperty("nativeHealth",mc.player.getHealth());
                status.add("equipment",JSON.toJsonTree(java.util.stream.IntStream.range(0,MenuHooks.extra(mc.player).getContainerSize()).mapToObj(i->Map.of("index",i,"group",MenuHooks.extra(mc.player).groups.get(i),"gear",GameItems.string(MenuHooks.extra(mc.player).getItem(i),GameItems.NS))).toList()));
                status.addProperty("cursor",mc.player.containerMenu.getCarried().toString());
                status.addProperty("dimension",mc.player.level().dimension().identifier().toString());
                status.addProperty("x",mc.player.getX());
                status.addProperty("y",mc.player.getY());
                status.addProperty("z",mc.player.getZ());
                JsonArray items=new JsonArray();
                for(int i=0;i<mc.player.getInventory().getContainerSize();i++){
                    var stack=mc.player.getInventory().getItem(i);
                    JsonObject item=new JsonObject();
                    item.addProperty("index",i);
                    item.addProperty("id",GameItems.id(stack));
                    item.addProperty("count",stack.getCount());
                    item.addProperty("material",GameItems.material(stack));
                    item.add("gear",JSON.toJsonTree(GameItems.gear(stack)));
                    items.add(item);
                }
                status.add("inventory",items);
                status.add("resources",JSON.toJsonTree(ClientState.INSTANCE.resources));
            }
            JsonArray widgets=new JsonArray();
            if(mc.gui.screen()!=null)for(var child:mc.gui.screen().children())if(child instanceof AbstractWidget w){
                JsonObject widget=new JsonObject();
                widget.addProperty("text",w.getMessage().getString());
                widget.addProperty("x",w.getX());
                widget.addProperty("y",w.getY());
                widget.addProperty("width",w.getWidth());
                widget.addProperty("height",w.getHeight());
                widget.addProperty("visible",w.visible);
                widget.addProperty("active",w.active);
                widgets.add(widget);
            }
            status.add("widgets",widgets);
            Path next=dir.resolve("status.next");
            Files.writeString(next,JSON.toJson(status));
            Files.move(next,dir.resolve("status.json"),StandardCopyOption.REPLACE_EXISTING);
        }
        catch(Exception e){
            try{
                Files.writeString(dir.resolve("error.txt"),e.toString());
            }
            catch(Exception ignored){
            }
        }
    }
    private static void execute(Minecraft mc,JsonObject j)throws Exception{
        String action=j.get("action").getAsString();
        var state=ClientState.INSTANCE;
        switch(action){
            case "connect"->{
                String address="127.0.0.1:25568";
                net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new net.minecraft.client.gui.screens.TitleScreen(),mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),new net.minecraft.client.multiplayer.ServerData("Local verification",address,net.minecraft.client.multiplayer.ServerData.Type.OTHER),false,null);
            }
            case "menu"->mc.gui.setScreen(j.has("page")?new FightScreen(j.get("page").getAsInt()):new FightScreen());
            case "inventory"->mc.gui.setScreen(new InventoryScreen(mc.player));
            case "close"->{
                if(mc.gui.screen()!=null)mc.gui.screen().onClose();
            }
            case "key"->{
                KeyEvent event=new KeyEvent(j.get("key").getAsInt(),0,j.has("modifiers")?j.get("modifiers").getAsInt():0);
                if(mc.gui.screen()!=null)mc.gui.screen().keyPressed(event);
                else ClientBootstrap.key(event,j.has("release")&&j.get("release").getAsBoolean()?0:1);
            }
            case "click"->{
                if(mc.gui.screen()!=null){
                    MouseButtonEvent e=new MouseButtonEvent(j.get("x").getAsDouble(),j.get("y").getAsDouble(),new MouseButtonInfo(j.has("button")?j.get("button").getAsInt():0,j.has("modifiers")?j.get("modifiers").getAsInt():0));
                    var event=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonPressed.Pre(mc.gui.screen(),e,false);
                    if(!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event).isCanceled())mc.gui.screen().mouseClicked(e,false);
                    mc.gui.screen().mouseReleased(e);
                }
            }
            case "button"->{
                String text=j.get("text").getAsString();
                if(mc.gui.screen()!=null)for(var child:mc.gui.screen().children())if(child instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(text)){
                    b.onPress(new KeyEvent(257,0,0));
                    break;
                }
            }
            case "scroll"->{
                if(mc.gui.screen()!=null)mc.gui.screen().mouseScrolled(100,100,0,j.get("amount").getAsDouble());
            }
            case "screenshot"->Screenshot.grab(mc.gameDirectory,j.get("name").getAsString()+".png",mc.gameRenderer.mainRenderTarget(),1,c->{
            }
            );
            case "request"->{
                JsonObject data=j.getAsJsonObject("data");
                if(!data.has("session"))data.addProperty("session",state.session);
                if(!data.has("operation"))data.addProperty("operation",UUID.randomUUID().toString());
                state.send(j.get("name").getAsString(),data);
            }
            case "command"->{
                if(mc.getConnection()!=null)mc.getConnection().sendCommand(j.get("command").getAsString());
            }
            case "slot"->{
                var input=net.minecraft.world.inventory.ContainerInput.valueOf(j.has("input")?j.get("input").getAsString():"PICKUP");
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId,j.get("slot").getAsInt(),j.has("button")?j.get("button").getAsInt():0,input,mc.player);
            }
            case "hotbar"->mc.player.getInventory().setSelectedSlot(j.get("slot").getAsInt());
            case "creativeInventory"->{
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen)((cn.yumis.fight.mixin.CreativeTabAccessor)screen).ydnf$tab(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getValue(net.minecraft.resources.Identifier.withDefaultNamespace("inventory")));
            }
            case "useBlock"->{
                var pos=new net.minecraft.core.BlockPos(j.get("x").getAsInt(),j.get("y").getAsInt(),j.get("z").getAsInt());
                mc.gameMode.useItemOn(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));
            }
            case "breakBlock"->{
                var pos=new net.minecraft.core.BlockPos(j.get("x").getAsInt(),j.get("y").getAsInt(),j.get("z").getAsInt());
                mc.gameMode.startDestroyBlock(pos,net.minecraft.core.Direction.UP);
            }
            case "look"->{
                mc.player.setYRot(j.get("yaw").getAsFloat());
                mc.player.setXRot(j.get("pitch").getAsFloat());
            }
            case "attack"->ClientCombat.INSTANCE.attack();
            case "skill"->ClientCombat.INSTANCE.press(j.get("slot").getAsInt());
            case "release"->ClientCombat.INSTANCE.release(j.get("slot").getAsInt());
            case "scale"->{
                mc.options.guiScale().set(j.get("value").getAsInt());
                mc.resizeGui();
            }
            case "profile"->state.send("profile");
            default->throw new IllegalArgumentException("Unknown verification action");
        }
    }
}

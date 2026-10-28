package cn.yumis.fight.verification;
import cn.yumis.fight.bootstrap.ClientBootstrap;
import cn.yumis.fight.bootstrap.FightScreen;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.bootstrap.ClientSession;

import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.industry.platform.IndustrialMenu;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import cn.yumis.fight.modules.skills.client.Effects;

import cn.yumis.fight.modules.inventory.api.DimensionalMenu;

import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.*;
import java.nio.file.*;
import java.util.*;
/** Explicit opt-in developer harness, inactive in ordinary launches. Uses real menus and payloads. */
public final class VerificationDriver {
    private static JsonObject jeiInspection=new JsonObject();
    private static long seen=-1,last;
    public static boolean tooltipShift;
    private VerificationDriver(){
    }
    public static void tick(){
        if(!Boolean.getBoolean("ydnf.verify"))return;
        var mc=Minecraft.getInstance();
        long now=ClientSession.now();
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
            status.addProperty("ready",ClientSession.INSTANCE.ready);
            status.addProperty("rulesRevision",cn.yumis.fight.modules.equipment.api.EquipmentDefinitions.current().revision);
            status.addProperty("catalogRevision",MaterialCatalog.current().revision);
            status.addProperty("session",ClientSession.INSTANCE.session);
            status.add("targets",JSON.toJsonTree(ClientSession.INSTANCE.targets));
            status.add("party",JSON.toJsonTree(ClientSession.INSTANCE.party));
            status.add("dungeon",JSON.toJsonTree(ClientSession.INSTANCE.dungeon));
            status.addProperty("screen",mc.gui.screen()==null?"":mc.gui.screen().getClass().getSimpleName());
            status.addProperty("width",mc.getWindow().getGuiScaledWidth());
            status.addProperty("height",mc.getWindow().getGuiScaledHeight());
            status.add("progress",JSON.toJsonTree(ClientSession.INSTANCE.progress));
            status.add("career",JSON.toJsonTree(cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.view));
            if(ClientSession.INSTANCE.ready)status.add("careerStats",JSON.toJsonTree(SkillController.INSTANCE.snapshot()));
            status.add("life",JSON.toJsonTree(ClientSession.INSTANCE.own));
            status.add("stats",JSON.toJsonTree(ClientSession.INSTANCE.stats));
            status.addProperty("gold",ClientSession.INSTANCE.gold.balance);status.add("goldFeedback",JSON.toJsonTree(ClientSession.INSTANCE.gold.gains(now)));
            status.add("damageMeter",ClientSession.INSTANCE.damageMeter.deepCopy());
            status.add("jei",jeiInspection.deepCopy());status.addProperty("industrialBeams",IndustrialVisuals.beams().size());status.addProperty("industrialBeamRemaining",IndustrialVisuals.remaining());
            if(ClientSession.INSTANCE.profile.has("stats"))status.add("serverStats",ClientSession.INSTANCE.profile.get("stats"));
            status.addProperty("font",UiFonts.currentName());
            status.addProperty("characterDark",ClientSettings.characterDark);
            status.addProperty("themeRevision",UiTheme.revision);status.addProperty("themeStatus",UiTheme.status);status.addProperty("themePanel",Integer.toHexString(UiTheme.PANEL));
            status.addProperty("fontBusy",UiFonts.busy());
            status.addProperty("fontStatus",UiFonts.status());
            status.addProperty("mouseX",mc.mouseHandler.getScaledXPos(mc.getWindow()));
            status.addProperty("mouseY",mc.mouseHandler.getScaledYPos(mc.getWindow()));
            status.addProperty("overlay",mc.gui.overlay()==null?"":mc.gui.overlay().getClass().getSimpleName());
            status.addProperty("pending",ClientSession.INSTANCE.prediction.pendingCount());
            status.add("feedback",FeedbackProbe.snapshot());
            status.add("vfxMetrics",JSON.toJsonTree(Effects.VFX.metrics()));
            status.addProperty("vfxActive",Effects.VFX.activeCount());
            status.add("visualEvents",JSON.toJsonTree(Effects.VFX.activeEvents()));
            var cast=SkillController.INSTANCE.cast;
            if(cast!=null){JsonObject detail=new JsonObject();detail.addProperty("id",cast.id);detail.addProperty("skill",cast.skill.id);detail.addProperty("slashes",cast.slashes);detail.addProperty("segment",cast.segment);detail.addProperty("start",cast.start);detail.addProperty("speed",cast.speed);detail.add("parameters",JSON.toJsonTree(cast.parameters.values()));status.add("cast",detail);}
            if(mc.player!=null){
                if(mc.player.containerMenu instanceof cn.yumis.fight.modules.industry.platform.IndustrialMenu industry)status.add("industry",industry.info.deepCopy());
                var bag=MenuHooks.extra(mc.player);status.addProperty("bagTier",bag.bagTier);status.addProperty("bagPage",bag.bagPage);status.addProperty("bagSchema",bag.bagSchema);
                status.addProperty("player",mc.player.getStringUUID());
                status.addProperty("nativeMaximumHealth",mc.player.getMaxHealth());
                status.addProperty("nativeMoveSpeed",mc.player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED));
                status.addProperty("nativeHealth",mc.player.getHealth());
                status.addProperty("nativeDead",mc.player.isDeadOrDying());
                status.add("equipment",JSON.toJsonTree(java.util.stream.IntStream.range(0,MenuHooks.extra(mc.player).getContainerSize()).mapToObj(i->Map.of("index",i,"group",MenuHooks.extra(mc.player).groups.get(i),"gear",ItemData.string(MenuHooks.extra(mc.player).getItem(i),ItemData.NS))).toList()));
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
                    item.addProperty("id",ItemData.id(stack));
                    item.addProperty("count",stack.getCount());
                    item.addProperty("material",EquipmentItems.material(stack));
                    item.add("gear",JSON.toJsonTree(EquipmentItems.gear(stack)));
                    items.add(item);
                }
                status.add("inventory",items);
                status.add("resources",JSON.toJsonTree(ClientSession.INSTANCE.resources));
                status.addProperty("stamina",CloudMovement.stamina());
                status.addProperty("onGround",mc.player.onGround());
                status.addProperty("velocityY",mc.player.getDeltaMovement().y);
                status.addProperty("cloudStep",mc.player.hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.CLOUD_STEP));
                var menu=mc.player.containerMenu;
                status.addProperty("menu",menu.containerId);
                status.add("menuSlots",JSON.toJsonTree(menu.slots.stream().map(slot->Map.of("index",slot.index,"x",slot.x,"y",slot.y,"id",ItemData.id(slot.getItem()),"count",slot.getItem().getCount(),"gear",ItemData.string(slot.getItem(),ItemData.NS))).toList()));
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> container){
                    status.addProperty("left",container.getLeftPos());status.addProperty("top",container.getTopPos());
                    status.add("visibleSlots",JSON.toJsonTree(container.getMenu().slots.stream().filter(net.minecraft.world.inventory.Slot::isActive).map(slot->Map.of("index",slot.index,"x",slot.x,"y",slot.y,"id",ItemData.id(slot.getItem()),"count",slot.getItem().getCount())).toList()));
                }
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
        var state=ClientSession.INSTANCE;
        switch(action){
            case "jei"->jeiInspection=(JsonObject)Class.forName("cn.yumis.fight.modules.industry.client.jei.IndustrialJeiPlugin").getMethod("inspect",String.class,String.class).invoke(null,j.has("operation")?j.get("operation").getAsString():"summary",j.has("key")?j.get("key").getAsString():"");

            case "connect"->{
                String address=j.has("address")?j.get("address").getAsString():"127.0.0.1:25568";
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
                    var release=new net.neoforged.neoforge.client.event.ScreenEvent.MouseButtonReleased.Pre(mc.gui.screen(),e);
                    if(!net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(release).isCanceled())mc.gui.screen().mouseReleased(e);
                }
            }
            case "hover","mouse"->{
                double px=j.get("x").getAsDouble()*mc.getWindow().getScreenWidth()/mc.getWindow().getGuiScaledWidth();
                double py=j.get("y").getAsDouble()*mc.getWindow().getScreenHeight()/mc.getWindow().getGuiScaledHeight();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().handle(),px,py);
                var move=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onMove",long.class,double.class,double.class);
                move.setAccessible(true);move.invoke(mc.mouseHandler,mc.getWindow().handle(),px,py);
                if(action.equals("mouse")){
                    var press=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onButton",long.class,MouseButtonInfo.class,int.class);
                    press.setAccessible(true);
                    var info=new MouseButtonInfo(j.has("button")?j.get("button").getAsInt():0,j.has("modifiers")?j.get("modifiers").getAsInt():0);
                    press.invoke(mc.mouseHandler,mc.getWindow().handle(),info,1);press.invoke(mc.mouseHandler,mc.getWindow().handle(),info,0);
                }
            }
            case "fontImport"->UiFonts.importFile(Path.of(j.get("path").getAsString()),()->{});
            case "fontDefault"->UiFonts.restoreDefault(()->{});
            case "feedbackReset"->FeedbackProbe.reset();
            case "respawn"->mc.player.respawn();
            case "tooltipShift"->tooltipShift=j.get("enabled").getAsBoolean();
            case "combatSeed"->SkillController.INSTANCE.verificationSeed(j.get("seed").getAsLong());
            case "holdUse"->{mc.options.keyUse.setDown(true);mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.OFF_HAND);}
            case "releaseUse"->{mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(mc.player);}
            case "replayNative","duplicateNative"->{
                if(FeedbackProbe.lastReport!=null){
                    var report=FeedbackProbe.lastReport.deepCopy();
                    if(action.equals("duplicateNative"))report.addProperty("operation",report.get("operation").getAsLong()+1000000);
                    state.send("damage",report);
                }
            }
            case "quit"->mc.stop();
            case "button"->{
                String text=j.get("text").getAsString();
                if(mc.gui.screen()!=null)for(var child:mc.gui.screen().children())if(child instanceof Button b&&b.visible&&b.active&&b.getMessage().getString().equals(text)){
                    b.onPress(new KeyEvent(257,0,0));
                    break;
                }
            }
            case "scroll"->{
                if(mc.gui.screen()!=null)mc.gui.screen().mouseScrolled(j.has("x")?j.get("x").getAsDouble():100,j.has("y")?j.get("y").getAsDouble():100,0,j.get("amount").getAsDouble());
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
            case "creativeTab"->{
                if(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen screen)((cn.yumis.fight.mixin.CreativeTabAccessor)screen).ydnf$tab(net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getValue(net.minecraft.resources.Identifier.parse(j.get("tab").getAsString())));
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
            case "holdAttack"->mc.options.keyAttack.setDown(true);
            case "stopAttack"->mc.options.keyAttack.setDown(false);
            case "storageSearch"->{if(mc.gui.screen()!=null)for(var child:mc.gui.screen().children())if(child instanceof net.minecraft.client.gui.components.EditBox box){box.setValue(j.get("text").getAsString());break;}}
            case "useItem"->mc.gameMode.useItem(mc.player,net.minecraft.world.InteractionHand.MAIN_HAND);
            case "storageScroll"->{if(mc.player.containerMenu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu bag)mc.gameMode.handleInventoryButtonClick(bag.containerId,100+j.get("row").getAsInt());}
            case "drag"->{if(mc.gui.screen()!=null){var first=new MouseButtonEvent(j.get("x").getAsDouble(),j.get("y").getAsDouble(),new MouseButtonInfo(0,0));var last=new MouseButtonEvent(j.get("toX").getAsDouble(),j.get("toY").getAsDouble(),new MouseButtonInfo(0,0));mc.gui.screen().mouseClicked(first,false);mc.gui.screen().mouseDragged(last,last.x()-first.x(),last.y()-first.y());mc.gui.screen().mouseReleased(last);}}
            case "attack"->SkillController.INSTANCE.attack();
            case "skill"->SkillController.INSTANCE.press(j.get("slot").getAsInt());
            case "release"->SkillController.INSTANCE.release(j.get("slot").getAsInt());
            case "scale"->{
                mc.options.guiScale().set(j.get("value").getAsInt());
                mc.resizeGui();
            }
            case "profile"->state.send("profile");
            default->throw new IllegalArgumentException("Unknown verification action");
        }
    }
}

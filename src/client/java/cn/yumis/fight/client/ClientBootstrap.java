package cn.yumis.fight.client;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.game.*;
import cn.yumis.fight.network.Network;
import cn.yumis.fight.rules.Models.Gear;
import cn.yumis.fight.rules.Rules.*;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.*;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;
@Mod(value=GameItems.NS,dist=Dist.CLIENT)
public final class ClientBootstrap {
    public static final KeyMapping.Category CATEGORY=new KeyMapping.Category(id("combat"));
    public static final KeyMapping MENU=new KeyMapping("key.ydnf.menu",GLFW.GLFW_KEY_I,CATEGORY);
    public static final KeyMapping LEAP=new KeyMapping("key.ydnf.cloud_leap",GLFW.GLFW_KEY_C,CATEGORY);
    public static final KeyMapping[] SKILLS=new KeyMapping[8];
    static {
        int[] keys={
            GLFW.GLFW_KEY_R,GLFW.GLFW_KEY_T,GLFW.GLFW_KEY_Z,GLFW.GLFW_KEY_X,GLFW.GLFW_KEY_N,GLFW.GLFW_KEY_V,GLFW.GLFW_KEY_F,GLFW.GLFW_KEY_G
        }
        ;
        for(int i=0;i<8;i++)SKILLS[i]=new KeyMapping("key.ydnf.skill_"+(i+1),keys[i],CATEGORY);
    }
    public ClientBootstrap(IEventBus bus){
        Network.CLIENT=ClientState.INSTANCE::accept;
        Effects.VFX.register();
        ClientSettings.load();
        UiFonts.register(bus);
        CurrencyUi.register();
        ExtraInventory.CLIENT_PROGRESS=p->ClientState.INSTANCE.progress;
        bus.addListener((RegisterKeyMappingsEvent e)->{
            e.registerCategory(CATEGORY);e.register(MENU);e.register(LEAP);for(var key:SKILLS)e.register(key);
        }
        );
        bus.addListener((RegisterSelectItemModelPropertyEvent e)->{
            e.register(id("katana_style"),StyleProperty.TYPE);e.register(id("material"),MaterialProperty.TYPE);
        }
        );
        bus.addListener((RegisterMenuScreensEvent e)->{e.register(FightMod.FABRICATOR_MENU.get(),FabricatorScreen::new);e.register(cn.yumis.fight.industry.IndustrialContent.MACHINE_MENU.get(),IndustrialScreen::new);e.register(cn.yumis.fight.industry.IndustrialContent.STORAGE_MENU.get(),DimensionalScreen::new);});
        bus.addListener((RegisterFluidModelsEvent e)->e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xff70cbd2)),cn.yumis.fight.industry.IndustrialContent.COOLANT,cn.yumis.fight.industry.IndustrialContent.FLOWING_COOLANT));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            e.registerBlockEntityRenderer(cn.yumis.fight.industry.IndustrialContent.MACHINE_BE.get(),IndustrialRenderer::new);
            e.registerEntityRenderer(FightMod.PRIVATE_LOOT.get(),LootRenderer::new);e.registerEntityRenderer(FightMod.CAGE.get(),CageRenderer::new);for(var type:FightMod.ENEMIES.entrySet()){
                if(type.getKey().startsWith("goblin"))e.registerEntityRenderer(type.getValue().get(),GoblinRenderer::new);
                else{
                    int role=java.util.List.of("dragonman","blue_dragonman","wyvern","blue_wyvern","lucas").indexOf(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new DragonRenderer(context,role));
                }
            }
        }
        );
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e)->{
            for(String kind:java.util.List.of("goblin","dragonman","wyvern"))e.registerLayerDefinition(EnemyRenderer.layer(kind),()->EnemyModel.layer(kind));
        }
        );
        bus.addListener((RegisterGuiLayersEvent e)->{e.registerAboveAll(id("hud"),Hud::render);e.registerAboveAll(id("stamina"),CloudMovement::render);});
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{
            IndustrialVisuals.tick();
            ClientState.INSTANCE.tick();ClientCombat.INSTANCE.tick();CloudMovement.tick();VerificationDriver.tick();migrateKeys();while(MENU.consumeClick()){
                var mc=Minecraft.getInstance();if(mc.player!=null&&mc.gui.screen()==null){
                    ClientState.INSTANCE.upload();mc.gui.setScreen(new FightScreen());
                }
            }
            for(var key:SKILLS)while(key.consumeClick()){
            }
            ;
        }
        );
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e)->{
            if(e.isAttack()&&ClientCombat.INSTANCE.validWeapon()&&(Minecraft.getInstance().hitResult==null||Minecraft.getInstance().hitResult.getType()!=HitResult.Type.BLOCK)){
                e.setCanceled(true);e.setSwingHand(true);ClientCombat.INSTANCE.attack();
            }
            else if(e.isAttack()&&Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&ClientCombat.INSTANCE.nativeAttack(hit.getEntity())){
                e.setCanceled(true);e.setSwingHand(true);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e)->{
            if(e.getNewScreen()!=null&&Minecraft.getInstance().player!=null)ClientCombat.INSTANCE.cancel(false);
            CloudMovement.screen(e.getNewScreen()!=null);
        }
        );
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e)->{
            if(ClientCombat.INSTANCE.invulnerable()||e.getEntity().hasEffect(FightMod.STUN)){
                e.getInput().keyPresses=net.minecraft.world.entity.player.Input.EMPTY;((cn.yumis.fight.mixin.InputAccessor)e.getInput()).ydnf$move(net.minecraft.world.phys.Vec2.ZERO);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e)->{
            Tooltips.item(e);
            if(GameItems.own(e.getItemStack()))e.getToolTip().replaceAll(UiFonts::decorate);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Background e)->Hud.equipment(e));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre e)->{
            if(MENU.matches(e.getKeyEvent())&&e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen&&screen.getTitle().getString().equals("次元背包")){
                ClientState.INSTANCE.upload();Minecraft.getInstance().player.closeContainer();Minecraft.getInstance().gui.setScreen(new FightScreen());e.setCanceled(true);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonPressed.Pre e)->currency(e));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseButtonReleased.Pre e)->{
            if(e.getButton()==1&&currencyGesture){currencyGesture=false;e.setCanceled(true);}
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseDragged.Pre e)->{
            if(e.getMouseButton()==1&&currencyGesture)e.setCanceled(true);
        });
        NeoForge.EVENT_BUS.addListener((RenderGuiLayerEvent.Pre e)->{
            if(ClientState.INSTANCE.ready&&(e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH)||e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL)))e.setCanceled(true);
        }
        );
    }
    public static Identifier id(String path){
        return Identifier.fromNamespaceAndPath(GameItems.NS,path);
    }
    public static int layer(){
        var window=Minecraft.getInstance().getWindow();
        boolean ctrl=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_CONTROL),alt=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_ALT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_ALT);
        if(ctrl&&alt)return -1;
        return ctrl?1:alt?2:0;
    }
    public static boolean key(KeyEvent event,int action){
        if(CloudMovement.key(event,action))return true;
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.gui.screen()!=null||!ClientCombat.INSTANCE.validWeapon()||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_F3))return false;
        for(int i=0;i<8;i++)if(SKILLS[i].matches(event)){
            SKILLS[i].setDown(action!=GLFW.GLFW_RELEASE);
            if(action==GLFW.GLFW_PRESS&&layer()>=0)ClientCombat.INSTANCE.press(layer()*8+i);
            else if(action==GLFW.GLFW_RELEASE)ClientCombat.INSTANCE.release(i);
            return true;
        }
        return false;
    }
    private static boolean currencyGesture;
    private static void currency(ScreenEvent.MouseButtonPressed.Pre e){
        var mc=Minecraft.getInstance();
        if(e.getButton()!=1||!(e.getScreen() instanceof AbstractContainerScreen<?> screen)||mc.player==null)return;
        var target=CurrencyUi.target(screen,e.getMouseX(),e.getMouseY());
        if(target==null)return;
        var menu=target.menu();
        String cursor=GameItems.id(menu.getCarried());
        if(!CurrencyUi.isCurrency(menu.getCarried()))return;
        Gear gear=GameItems.gear(target.slot().getItem());
        if(gear==null)return;
        e.setCanceled(true);
        currencyGesture=true;
        var s=ClientState.INSTANCE;
        var j=s.operation("currency");
        j.addProperty("session",s.session);
        j.addProperty("menu",menu.containerId);
        j.addProperty("slot",target.index());
        j.addProperty("equipment",gear.instance.toString());
        j.addProperty("revision",gear.revision);
        j.addProperty("count",menu.getCarried().getCount());
        j.add("cursor",net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),menu.getCarried()).getOrThrow());
        if(cursor.equals("affix_gem")&&gear.sockets>1)mc.gui.pushScreenLayer(new GemScreen(e.getScreen(),j,gear));
        else s.send("currency",j);
    }
    public static void inventoryClick(int slot,int button,ContainerInput input){
        var s=ClientState.INSTANCE;
        JsonObject j=s.operation("click");
        j.addProperty("session",s.session);
        j.addProperty("slot",slot);
        j.addProperty("button",button);
        j.addProperty("input",input.name());
        s.send("inventoryClick",j);
    }
    private static boolean migrated;
    private static void migrateKeys(){
        var mc=Minecraft.getInstance();
        if(migrated||mc.player==null)return;
        migrated=true;
        if(ClientSettings.migration>=1)return;
        int[][] moves={
            {
                GLFW.GLFW_KEY_T,GLFW.GLFW_KEY_ENTER
            }
            ,{
                GLFW.GLFW_KEY_F,GLFW.GLFW_KEY_Y
            }
            ,{
                GLFW.GLFW_KEY_X,GLFW.GLFW_KEY_END
            }
            ,{
                GLFW.GLFW_KEY_G,GLFW.GLFW_KEY_INSERT
            }
        }
        ;
        for(KeyMapping key:mc.options.keyMappings){
            if(key==MENU||java.util.Arrays.asList(SKILLS).contains(key)||!key.isDefault())continue;
            for(int[] move:moves)if(key.getKey().getValue()==move[0]){
                boolean occupied=java.util.Arrays.stream(mc.options.keyMappings).anyMatch(k->k!=key&&k.getKey().getValue()==move[1]);
                if(!occupied)key.setKey(InputConstants.Type.KEYSYM.getOrCreate(move[1]));
                else ClientState.INSTANCE.message("默认按键冲突："+key.getName()+"，目标键已有绑定，请在设置中调整");
            }
        }
        KeyMapping.resetMapping();
        mc.options.save();
        ClientSettings.migration=1;
        ClientSettings.save();
    }
}

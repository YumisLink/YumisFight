package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.equipment.client.EquipmentTooltip;
import cn.yumis.fight.modules.skills.api.client.CloudMovement;
import cn.yumis.fight.modules.skills.api.client.SkillController;
import cn.yumis.fight.verification.VerificationDriver;
import cn.yumis.fight.modules.dungeons.client.CageRenderer;
import cn.yumis.fight.modules.dungeons.client.GoblinRenderer;
import cn.yumis.fight.modules.dungeons.client.DragonRenderer;
import cn.yumis.fight.modules.dungeons.client.EnemyModel;
import cn.yumis.fight.modules.dungeons.client.EnemyRenderer;
import cn.yumis.fight.modules.economy.client.LootRenderer;
import cn.yumis.fight.modules.equipment.client.CurrencyUi;
import cn.yumis.fight.modules.inventory.client.DimensionalScreen;
import cn.yumis.fight.modules.equipment.client.FabricatorScreen;
import cn.yumis.fight.modules.equipment.client.GemScreen;
import cn.yumis.fight.modules.equipment.client.MaterialProperty;
import cn.yumis.fight.modules.equipment.client.StyleProperty;
import cn.yumis.fight.modules.industry.client.IndustrialRecipeCache;
import cn.yumis.fight.modules.industry.client.IndustrialRenderer;
import cn.yumis.fight.modules.industry.client.IndustrialScreen;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.equipment.api.EquipmentEligibility;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import cn.yumis.fight.modules.skills.client.Effects;

import cn.yumis.fight.network.Network;
import cn.yumis.fight.modules.equipment.api.Gear;
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
@Mod(value=ItemData.NS,dist=Dist.CLIENT)
public final class ClientBootstrap {
    public ClientBootstrap(IEventBus bus){
        cn.yumis.fight.modules.presentation.api.client.ClientUi.install(ClientSession.INSTANCE);
        cn.yumis.fight.modules.equipment.api.client.EquipmentClient.install(ClientSession.INSTANCE);
        cn.yumis.fight.modules.skills.api.client.SkillsClient.install(ClientSession.INSTANCE);
        cn.yumis.fight.modules.dungeons.api.TrainingActions.client(cn.yumis.fight.modules.dungeons.client.TrainingInput::use);
        cn.yumis.fight.bootstrap.PlayerPageAssembly.register();
        cn.yumis.fight.modules.presentation.api.client.MenuInput.navigation(page->Minecraft.getInstance().gui.setScreen(new FightScreen(page)));
        IndustrialRecipeCache.register();
        Network.CLIENT=ClientSession.INSTANCE::accept;
        if(Boolean.getBoolean("ydnf.verify"))cn.yumis.fight.modules.skills.api.client.SkillDiagnostics.attack=(weapon,value)->{cn.yumis.fight.verification.FeedbackProbe.weapon=weapon;cn.yumis.fight.verification.FeedbackProbe.attack=value;};
        Effects.VFX.register();
        cn.yumis.fight.modules.industry.client.IndustrialBeamRenderer.register();
        ClientSettings.load();
        UiTheme.load();
        UiFonts.register(bus);
        CurrencyUi.register();
        NeoForge.EVENT_BUS.addListener((RenderTooltipEvent.Texture event)->{if(cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mythic(event.getItemStack())||cn.yumis.fight.modules.equipment.api.client.MythicDisplay.marked(event.getComponents()))event.setTexture(cn.yumis.fight.modules.equipment.api.client.MythicDisplay.TOOLTIP);});
        EquipmentEligibility.CLIENT_PROGRESS=p->ClientSession.INSTANCE.progress;
        EquipmentTooltip.install(id->{var currency=cn.yumis.fight.modules.economy.api.CurrencyRules.find(id);if(currency==null)return null;var label=cn.yumis.fight.modules.presentation.api.client.UiFonts.decorate(net.minecraft.network.chat.Component.literal(currency.rarity().title()+" · 通货").withStyle(style->style.withColor(currency.rarity().color()).withItalic(false)));return currency.rarity()==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC?cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(label):label;},
            id->cn.yumis.fight.modules.dungeons.api.DungeonCatalog.current().dungeons.stream().filter(d->d.materials().contains(id)).map(d->"来源："+d.name()+(d.available()?"":"（尚未开放）")).toList(),
            ()->Boolean.getBoolean("ydnf.verify")&&VerificationDriver.tooltipShift);
        bus.addListener((RegisterKeyMappingsEvent e)->{
            e.registerCategory(cn.yumis.fight.modules.presentation.api.client.MenuInput.CATEGORY);e.register(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP);for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)e.register(key);
        }
        );
        bus.addListener((RegisterSelectItemModelPropertyEvent e)->{
            e.register(id("katana_style"),StyleProperty.TYPE);e.register(id("material"),MaterialProperty.TYPE);
        }
        );
        bus.addListener((RegisterMenuScreensEvent e)->{e.register(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR_MENU.get(),FabricatorScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_MENU.get(),IndustrialScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.STORAGE_MENU.get(),DimensionalScreen::new);});
        bus.addListener((RegisterFluidModelsEvent e)->e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xff70cbd2)),cn.yumis.fight.modules.industry.platform.IndustrialContent.COOLANT,cn.yumis.fight.modules.industry.platform.IndustrialContent.FLOWING_COOLANT));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            e.registerEntityRenderer(cn.yumis.fight.modules.skills.api.SkillContentTypes.HERDER.get(),cn.yumis.fight.modules.skills.client.HerderRenderer::new);
            e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.TRAINING_SANDBAG.get(),cn.yumis.fight.modules.dungeons.client.TrainingSandbagRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_BE.get(),IndustrialRenderer::new);
            e.registerEntityRenderer(cn.yumis.fight.modules.economy.api.RewardContentTypes.PRIVATE_LOOT.get(),LootRenderer::new);e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.CAGE.get(),CageRenderer::new);for(var type:cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.ENEMIES.entrySet()){
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
            ClientSession.INSTANCE.tick();SkillController.INSTANCE.tick();CloudMovement.tick();VerificationDriver.tick();while(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU.consumeClick()){
                var mc=Minecraft.getInstance();if(mc.player!=null&&mc.gui.screen()==null){
                    ClientSession.INSTANCE.upload();mc.gui.setScreen(new FightScreen());
                }
            }
            for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)while(key.consumeClick()){
            }
            ;
        }
        );
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event)->{if(cn.yumis.fight.modules.skills.client.Effects.VFX.blocksHotbar())event.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e)->{
            if(e.isAttack()&&SkillController.INSTANCE.validWeapon()&&(Minecraft.getInstance().hitResult==null||Minecraft.getInstance().hitResult.getType()!=HitResult.Type.BLOCK)){
                e.setCanceled(true);e.setSwingHand(false);SkillController.INSTANCE.attack();
            }
            else if(e.isAttack()&&Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&SkillController.INSTANCE.nativeAttack(hit.getEntity())){
                e.setCanceled(true);e.setSwingHand(false);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e)->{
            cn.yumis.fight.modules.equipment.client.FabricatorOpening.opening(e);
            if(e.getNewScreen()!=null&&Minecraft.getInstance().player!=null)SkillController.INSTANCE.cancel(false);
            CloudMovement.screen(e.getNewScreen()!=null);
        }
        );
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e)->{
            if(SkillController.INSTANCE.invulnerable()||e.getEntity().hasEffect(cn.yumis.fight.modules.combat.api.CombatContentTypes.STUN)){
                e.getInput().keyPresses=net.minecraft.world.entity.player.Input.EMPTY;((cn.yumis.fight.mixin.InputAccessor)e.getInput()).ydnf$move(net.minecraft.world.phys.Vec2.ZERO);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e)->{
            EquipmentTooltip.item(e);
            if(ItemData.own(e.getItemStack()))e.getToolTip().replaceAll(UiFonts::decorate);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Background e)->Hud.equipment(e));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.KeyPressed.Pre e)->{
            if(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU.matches(e.getKeyEvent())&&e.getScreen() instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen screen&&screen.getTitle().getString().equals("次元背包")){
                ClientSession.INSTANCE.upload();Minecraft.getInstance().player.closeContainer();Minecraft.getInstance().gui.setScreen(new FightScreen());e.setCanceled(true);
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
            if(ClientSession.INSTANCE.ready&&(e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.PLAYER_HEALTH)||e.getName().equals(net.neoforged.neoforge.client.gui.VanillaGuiLayers.ARMOR_LEVEL)))e.setCanceled(true);
        }
        );
    }
    public static Identifier id(String path){
        return Identifier.fromNamespaceAndPath(ItemData.NS,path);
    }
    public static int layer(){
        var window=Minecraft.getInstance().getWindow();
        boolean ctrl=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_CONTROL),alt=InputConstants.isKeyDown(window,GLFW.GLFW_KEY_LEFT_ALT)||InputConstants.isKeyDown(window,GLFW.GLFW_KEY_RIGHT_ALT);
        if(ctrl&&alt)return -1;
        return ctrl?1:alt?2:0;
    }
    public static boolean key(KeyEvent event,int action){
        var mc=Minecraft.getInstance();
        boolean control=InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_RIGHT_CONTROL);
        if(mc.player!=null&&ClientSession.INSTANCE.ready&&event.key()==GLFW.GLFW_KEY_RIGHT_BRACKET&&control&&(mc.gui.screen()==null||mc.gui.screen() instanceof FightScreen)){
            if(action==GLFW.GLFW_PRESS){var state=ClientSession.INSTANCE;cn.yumis.fight.modules.combat.api.client.DamageAnalysis.toggle(state.resources.combatUntil,state.trainingMeter.has("id")&&!state.trainingMeter.get("completed").getAsBoolean());state.message("伤害分析："+(cn.yumis.fight.modules.combat.api.client.DamageAnalysis.recording()?"开始记录":"停止记录")+"；在角色页面查看结果");}
            return true;
        }
        if(CloudMovement.key(event,action))return true;
        if(mc.player==null||!ClientSession.INSTANCE.ready||mc.gui.screen()!=null||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_F3))return false;
        for(int i=0;i<8;i++)if(cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].matches(event)){
            var key=cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i];
            if(action==GLFW.GLFW_RELEASE&&key.isDown()){key.setDown(false);SkillController.INSTANCE.release(i);return true;}
            if(!SkillController.INSTANCE.validWeapon()){
                int currentLayer=layer();if(currentLayer<0)return false;
                var state=ClientSession.INSTANCE;String binding=state.progress.slots[currentLayer*8+i];
                if(!cn.yumis.fight.modules.skills.api.SkillCatalog.contains(binding))return false;
                var skill=cn.yumis.fight.modules.skills.api.SkillCatalog.require(binding);
                if(!skill.equipmentGranted||!cn.yumis.fight.modules.skills.api.SkillLearning.canBind(state.stats,state.progress,skill))return false;
            }
            cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS[i].setDown(action!=GLFW.GLFW_RELEASE);
            if(action==GLFW.GLFW_PRESS&&layer()>=0)SkillController.INSTANCE.press(layer()*8+i);
            else if(action==GLFW.GLFW_RELEASE)SkillController.INSTANCE.release(i);
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
        String cursor=ItemData.id(menu.getCarried());
        if(!CurrencyUi.isCurrency(menu.getCarried()))return;
        Gear gear=EquipmentItems.gear(target.slot().getItem());
        if(gear==null)return;
        e.setCanceled(true);
        currencyGesture=true;
        var s=ClientSession.INSTANCE;
        var j=s.operation("currency");
        j.addProperty("session",s.session);
        j.addProperty("menu",menu.containerId);
        j.addProperty("slot",target.index());
        j.addProperty("equipment",gear.instance.toString());
        j.addProperty("revision",gear.revision);
        j.addProperty("count",menu.getCarried().getCount());
        j.add("cursor",net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.encodeStart(mc.player.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),menu.getCarried()).getOrThrow());
        if(cursor.equals("affix_extraction_stone")&&cn.yumis.fight.modules.equipment.api.GemExtraction.available(gear))mc.gui.pushScreenLayer(new cn.yumis.fight.modules.equipment.client.ExtractionScreen(j,gear));
        else if(cursor.equals("affix_gem")&&gear.sockets>1)mc.gui.pushScreenLayer(new GemScreen(e.getScreen(),j,gear));
        else s.send("currency",j);
    }
    public static void inventoryClick(int slot,int button,ContainerInput input){
        var s=ClientSession.INSTANCE;
        JsonObject j=s.operation("click");
        j.addProperty("session",s.session);
        j.addProperty("slot",slot);
        j.addProperty("button",button);
        j.addProperty("input",input.name());
        s.send("inventoryClick",j);
    }
}

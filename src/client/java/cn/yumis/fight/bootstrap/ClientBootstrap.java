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
import cn.yumis.fight.modules.economy.client.JarClient;
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
import net.neoforged.bus.api.EventPriority;
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
        var local=ClientSession.INSTANCE;
        cn.yumis.fight.modules.character.client.CharacterClient.install(local.preparations,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->new cn.yumis.fight.modules.character.api.ProfileConditions(!local.own.dead,local.dungeon!=null||!local.own.instance.isEmpty(),local.resources.training||local.trainingMeter.has("id")&&!local.trainingMeter.get("completed").getAsBoolean(),SkillController.INSTANCE.locked(),local.resources.cooldowns.values().stream().anyMatch(until->until>ClientSession.now())),()->local.resources.combatUntil,cn.yumis.fight.modules.skills.api.SkillLearning::apply,job->EquipmentItems.gear(cn.yumis.fight.modules.equipment.api.EquipmentTemplates.starter(job,new java.util.Random())));
        cn.yumis.fight.modules.professions.client.ProfessionClient.install(local.preparations,()->local.professions,points->local.report(local.own,points*.05,true,SkillController.INSTANCE.snapshot().get(cn.yumis.fight.modules.attributes.api.Stat.HEALTH),0x8ef5ad,false,null),()->!local.own.dead);
        cn.yumis.fight.modules.equipment.client.EquipmentActions.install(local.preparations,()->local.progress,()->local.profile,cn.yumis.fight.modules.professions.client.ProfessionClient::get);
        cn.yumis.fight.modules.economy.client.EconomyActions.install(local.preparations,()->local.profile,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->!local.own.dead&&local.dungeon==null&&local.own.instance.isEmpty()&&ClientSession.now()>=local.resources.combatUntil);
        cn.yumis.fight.modules.dungeons.client.DungeonActions.install(local.preparations,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->!local.own.dead,cn.yumis.fight.modules.economy.api.ChestRewards::waystoneChest,luck->cn.yumis.fight.modules.economy.api.LootOdds.rarity(cn.yumis.fight.modules.dungeons.api.Difficulty.HELL,true,false,luck,new java.util.Random()),()->!local.own.dead&&local.dungeon==null&&local.own.instance.isEmpty());
        local.preparations.seal();
        cn.yumis.fight.modules.dungeons.api.TrainingActions.client(cn.yumis.fight.modules.dungeons.client.TrainingInput::use);
        cn.yumis.fight.modules.dungeons.client.WaystoneClient.register(bus,()->ClientSession.INSTANCE.dungeon);
        cn.yumis.fight.bootstrap.PlayerPageAssembly.register();
        cn.yumis.fight.modules.presentation.api.client.MenuInput.navigation(page->Minecraft.getInstance().gui.setScreen(new FightScreen(page)));
        cn.yumis.fight.modules.presentation.client.InventoryMenuButton.register();
        IndustrialRecipeCache.register();
        Network.CLIENT=ClientSession.INSTANCE::accept;
        if(Boolean.getBoolean("ydnf.verify"))cn.yumis.fight.modules.skills.api.client.SkillDiagnostics.attack=(weapon,value)->{cn.yumis.fight.verification.FeedbackProbe.weapon=weapon;cn.yumis.fight.verification.FeedbackProbe.attack=value;};
        Effects.VFX.register();
        cn.yumis.fight.modules.presentation.api.client.NearEffectRendering.register(bus);
        cn.yumis.fight.modules.presentation.api.client.EffectParticles.register(bus);
        cn.yumis.fight.modules.dungeons.client.TreasureWeaponVisuals.register();
        cn.yumis.fight.modules.dungeons.client.GblVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.PalaceVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.ForbiddenVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.SkasaVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.DarkCityVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.ClassicVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.DarkCityClient.register(()->ClientSession.INSTANCE.own);
        cn.yumis.fight.modules.dungeons.client.EncounterClientContacts.register(()->ClientSession.INSTANCE.own,()->ClientSession.INSTANCE.progress.level,()->ClientSession.INSTANCE.encounterSync(),()->SkillController.INSTANCE.invulnerable());
        cn.yumis.fight.modules.dungeons.client.EnemyTelegraphs.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.industry.client.IndustrialBeamRenderer.register();
        cn.yumis.fight.modules.industry.client.ManaStorageBagClient.register(bus);
        cn.yumis.fight.modules.industry.client.ManaWandClient.register(bus,stack->{var rarity=IndustrialItemDisplay.rarity(stack);return rarity==null?null:rarity.color();});
        cn.yumis.fight.modules.skills.client.SkillTooltip.register(bus);
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
            e.registerCategory(cn.yumis.fight.modules.presentation.api.client.MenuInput.CATEGORY);e.register(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.RENEW);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.FOLLOW);for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)e.register(key);
        }
        );
        bus.addListener((RegisterSelectItemModelPropertyEvent e)->{
            e.register(id("katana_style"),StyleProperty.TYPE);e.register(id("material"),MaterialProperty.TYPE);
        }
        );
        bus.addListener((RegisterMenuScreensEvent e)->{e.register(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR_MENU.get(),FabricatorScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_MENU.get(),IndustrialScreen::new);e.register(IndustrialContent.PROSPECTOR_MENU.get(),cn.yumis.fight.modules.industry.client.ProspectorScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.STORAGE_MENU.get(),DimensionalScreen::new);});
        bus.addListener((RegisterFluidModelsEvent e)->e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xff70cbd2)),cn.yumis.fight.modules.industry.platform.IndustrialContent.COOLANT,cn.yumis.fight.modules.industry.platform.IndustrialContent.FLOWING_COOLANT));
        bus.addListener((RegisterFluidModelsEvent e)->{
            for(var liquid:IndustrialContent.LIQUIDS.values())e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(liquid.color)),liquid.source,liquid.flowing);
        });
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            for(var entry:cn.yumis.fight.modules.skills.api.SkillContentTypes.SUMMONS.entrySet())e.registerEntityRenderer(entry.getValue().get(),c->new cn.yumis.fight.modules.skills.client.SummonRenderer(c,entry.getKey()));
            e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.TRAINING_SANDBAG.get(),cn.yumis.fight.modules.dungeons.client.TrainingSandbagRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_BE.get(),IndustrialRenderer::new);
            e.registerEntityRenderer(cn.yumis.fight.modules.economy.api.RewardContentTypes.PRIVATE_LOOT.get(),LootRenderer::new);e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.CAGE.get(),CageRenderer::new);for(var type:cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.ENEMIES.entrySet()){
                if(type.getKey().equals("golden_king"))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.GoldenKingRenderer::new);
                else if(cn.yumis.fight.modules.dungeons.api.GblEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.GblEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"gbl/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"forbidden/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"skasa/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"dark_city/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,kind.resource(),kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.PalaceEnemyRenderer(context,kind));}
                else if(type.getKey().startsWith("goblin"))e.registerEntityRenderer(type.getValue().get(),GoblinRenderer::new);
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
        // Jade renders in RenderGuiEvent.Post at NORMAL; draw the target panel afterward.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,Hud::renderTarget);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{
            IndustrialVisuals.tick();
            ClientSession.INSTANCE.tick();JarClient.tick();if(JarClient.blocking())cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.stopRenewing();SkillController.INSTANCE.tick();CloudMovement.tick();VerificationDriver.tick();while(cn.yumis.fight.modules.presentation.api.client.MenuInput.MENU.consumeClick()){
                var mc=Minecraft.getInstance();if(!JarClient.blocking()&&mc.player!=null&&mc.gui.screen()==null){
                    ClientSession.INSTANCE.upload();mc.gui.setScreen(new FightScreen());
                }
            }
            while(cn.yumis.fight.modules.skills.api.client.SkillInputs.RENEW.consumeClick())if(!JarClient.blocking())cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.renewSummons();
            while(cn.yumis.fight.modules.skills.api.client.SkillInputs.FOLLOW.consumeClick())if(!JarClient.blocking())cn.yumis.fight.modules.presentation.api.client.ClientUi.current().send("summonFollow",cn.yumis.fight.modules.presentation.api.client.ClientUi.current().operation("summonFollow"));
            for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)while(key.consumeClick()){
            }
            ;
        }
        );
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event)->{if(JarClient.blocking()||cn.yumis.fight.modules.skills.client.Effects.VFX.blocksHotbar())event.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e)->{
            if(JarClient.blocking()){e.setCanceled(true);e.setSwingHand(false);return;}
            if(e.isAttack()&&SkillController.INSTANCE.validWeapon()&&(Minecraft.getInstance().hitResult==null||Minecraft.getInstance().hitResult.getType()!=HitResult.Type.BLOCK)){
                e.setCanceled(true);e.setSwingHand(false);SkillController.INSTANCE.attack();
            }
            else if(e.isAttack()&&Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&SkillController.INSTANCE.nativeAttack(hit.getEntity())){
                e.setCanceled(true);e.setSwingHand(false);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Opening e)->{
            JarClient.opening(e);if(e.isCanceled())return;
            cn.yumis.fight.modules.equipment.client.FabricatorOpening.opening(e);
            if(e.getNewScreen()!=null&&Minecraft.getInstance().player!=null)SkillController.INSTANCE.cancel(false);
            CloudMovement.screen(e.getNewScreen()!=null);
        }
        );
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e)->{
            if(JarClient.blocking()||SkillController.INSTANCE.invulnerable()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(e.getEntity())){
                e.getInput().keyPresses=net.minecraft.world.entity.player.Input.EMPTY;((cn.yumis.fight.mixin.InputAccessor)e.getInput()).ydnf$move(net.minecraft.world.phys.Vec2.ZERO);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e)->{
            EquipmentTooltip.item(e);
            if(ItemData.own(e.getItemStack()))e.getToolTip().replaceAll(UiFonts::decorate);
        });
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Background e)->Hud.equipment(e));
        NeoForge.EVENT_BUS.addListener((ScreenEvent.Render.Foreground e)->Hud.creativeFrames(e));
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
        if(JarClient.blocking())return false;
        var mc=Minecraft.getInstance();
        boolean control=InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_RIGHT_CONTROL);
        if(mc.player!=null&&ClientSession.INSTANCE.ready&&event.key()==GLFW.GLFW_KEY_RIGHT_BRACKET&&control&&(mc.gui.screen()==null||mc.gui.screen() instanceof FightScreen)){
            if(action==GLFW.GLFW_PRESS){var state=ClientSession.INSTANCE;cn.yumis.fight.modules.combat.api.client.DamageAnalysis.toggle(state.resources.combatUntil,state.trainingMeter.has("id")&&!state.trainingMeter.get("completed").getAsBoolean());state.message("伤害分析："+(cn.yumis.fight.modules.combat.api.client.DamageAnalysis.recording()?"开始记录":"停止记录")+"；在角色页面查看结果");}
            return true;
        }
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.DemonHunterClient.cancelChannel())return true;
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.ChantClient.active()){cn.yumis.fight.modules.skills.client.ChantClient.cancel();return true;}
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

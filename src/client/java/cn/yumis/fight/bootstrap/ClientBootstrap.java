package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.arkguard.api.ArkEnemyEntity;
import cn.yumis.fight.modules.combat.client.EnemyHealthBars;
import cn.yumis.fight.modules.dungeons.api.RitualMechanismEntity;
import cn.yumis.fight.modules.dungeons.api.TrainingSandbag;
import cn.yumis.fight.modules.equipment.client.EquipmentTooltip;
import cn.yumis.fight.modules.equipment.client.LightsaberLight;
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
import cn.yumis.fight.modules.equipment.client.EquipmentActions;
import cn.yumis.fight.modules.inventory.api.InventoryEdits;
import cn.yumis.fight.modules.inventory.client.DimensionalScreen;
import cn.yumis.fight.modules.equipment.client.FabricatorScreen;
import cn.yumis.fight.modules.equipment.client.GemSocketSelection;
import cn.yumis.fight.modules.equipment.client.MaterialProperty;
import cn.yumis.fight.modules.equipment.client.StyleProperty;
import cn.yumis.fight.modules.industry.client.IndustrialRecipeCache;
import cn.yumis.fight.modules.industry.client.IndustrialRenderer;
import cn.yumis.fight.modules.industry.client.IndustrialScreen;
import cn.yumis.fight.modules.mining.api.VeinMining;
import cn.yumis.fight.modules.mining.client.VeinMiningClient;
import cn.yumis.fight.modules.presentation.api.client.ClientSettings;
import cn.yumis.fight.modules.presentation.api.client.LocalLights;
import cn.yumis.fight.modules.presentation.api.client.MenuInput;
import cn.yumis.fight.modules.presentation.api.client.UiFonts;
import cn.yumis.fight.modules.presentation.api.client.UiTheme;
import cn.yumis.fight.modules.equipment.api.EquipmentEligibility;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import cn.yumis.fight.modules.industry.client.IndustrialVisuals;
import cn.yumis.fight.modules.skills.client.ChampionSkillPoses;
import cn.yumis.fight.modules.skills.client.Effects;

import cn.yumis.fight.network.Network;
import cn.yumis.fight.modules.equipment.api.Gear;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.lwjgl.glfw.GLFW;
@Mod(value=ItemData.NS,dist=Dist.CLIENT)
public final class ClientBootstrap {
    private static final int BOSS_LIGHT_LEVEL = 9;

    public ClientBootstrap(IEventBus bus){
        cn.yumis.fight.modules.presentation.api.client.ClientUi.install(ClientSession.INSTANCE);
        VeinMiningClient.register(bus,MenuInput.CATEGORY,()->ClientSettings.veinMiningLimit,
                plan->ClientSession.INSTANCE.send(VeinMining.ACTION,plan));
        cn.yumis.fight.modules.equipment.api.client.EquipmentClient.install(ClientSession.INSTANCE);
        cn.yumis.fight.modules.skills.api.client.SkillsClient.install(ClientSession.INSTANCE);
        var local=ClientSession.INSTANCE;
        cn.yumis.fight.modules.character.client.CharacterClient.install(local.preparations,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->new cn.yumis.fight.modules.character.api.ProfileConditions(!local.own.dead,local.dungeon!=null||!local.own.instance.isEmpty(),local.trainingMeter.has("id")&&!local.trainingMeter.get("completed").getAsBoolean(),SkillController.INSTANCE.locked(),local.resources.cooldowns.values().stream().anyMatch(until->until>ClientSession.now())),()->local.resources.combatUntil,cn.yumis.fight.modules.skills.api.SkillLearning::apply,(progress,kind,talent)->cn.yumis.fight.modules.talents.api.TalentChanges.apply(cn.yumis.fight.modules.talents.api.TalentCatalog.current(),progress,kind,talent),job->EquipmentItems.gear(cn.yumis.fight.modules.equipment.api.EquipmentTemplates.starter(job,new java.util.Random())));
        cn.yumis.fight.modules.industry.api.EnergyCrystalItem.consuming(cn.yumis.fight.modules.character.client.CharacterClient::useCrystal);
        cn.yumis.fight.modules.professions.client.ProfessionClient.install(local.preparations,()->local.professions,points->local.report(local.own,points*.05,true,SkillController.INSTANCE.snapshot().get(cn.yumis.fight.modules.attributes.api.Stat.HEALTH),0x8ef5ad,false,null),()->!local.own.dead);
        cn.yumis.fight.modules.professions.client.AlchemyClient.install(SkillController.INSTANCE::snapshot,fraction->local.report(local.own,fraction,true,SkillController.INSTANCE.snapshot().get(cn.yumis.fight.modules.attributes.api.Stat.HEALTH),0x8ef5ad,false,null),amount->local.resources.mp=Math.min(SkillController.INSTANCE.snapshot().get(cn.yumis.fight.modules.attributes.api.Stat.MP),local.resources.mp+amount));
        cn.yumis.fight.modules.equipment.client.EquipmentActions.install(local.preparations,()->local.progress,()->local.profile,cn.yumis.fight.modules.professions.client.ProfessionClient::get);
        cn.yumis.fight.modules.economy.client.EconomyActions.install(local.preparations,()->local.profile,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->!local.own.dead&&local.dungeon==null&&local.own.instance.isEmpty()&&ClientSession.now()>=local.resources.combatUntil);
        cn.yumis.fight.modules.dungeons.client.DungeonActions.install(local.preparations,()->local.progress,()->SkillController.INSTANCE.snapshot(),()->!local.own.dead,cn.yumis.fight.modules.economy.api.ChestRewards::waystoneChest,luck->cn.yumis.fight.modules.economy.api.LootOdds.rarity(cn.yumis.fight.modules.dungeons.api.Difficulty.HELL,true,false,luck,new java.util.Random()),()->!local.own.dead&&local.dungeon==null&&local.own.instance.isEmpty());
        cn.yumis.fight.modules.economy.client.ShopClient.install(local.preparations,()->local.profile,()->local.ready&&!local.own.dead,(entity,shop)->{var definition=cn.yumis.fight.modules.npc.api.NpcInteractionComponent.query(entity);return definition!=null&&definition.actions().stream().anyMatch(action->action.type().equals("shop")&&action.value().equals(shop));});
        cn.yumis.fight.modules.npc.client.NpcClient.blocked(()->local.own.dead||JarClient.blocking()||Effects.VFX.blocksHotbar());
        cn.yumis.fight.modules.quests.client.TutorialClient.install(local.preparations,()->local.progress,()->local.profile,()->local.ready&&!local.own.dead);
        local.preparations.seal();
        cn.yumis.fight.modules.dungeons.api.TrainingActions.client(cn.yumis.fight.modules.dungeons.client.TrainingInput::use);
        cn.yumis.fight.modules.dungeons.client.WaystoneClient.register(bus,()->ClientSession.INSTANCE.dungeon);
        cn.yumis.fight.modules.mapgeneration.client.WorldDecorationClient.register(bus);
        cn.yumis.fight.bootstrap.PlayerPageAssembly.register();
        MenuInput.navigation(FightScreen::navigate);
        cn.yumis.fight.modules.economy.client.SaleClient.manualPage(()->Minecraft.getInstance().gui.screen() instanceof FightScreen screen
            &&screen.page>=0&&cn.yumis.fight.modules.presentation.api.PlayerPages.at(screen.page).id().equals("sale"));
        cn.yumis.fight.modules.presentation.client.InventoryMenuButton.register();
        IndustrialRecipeCache.register();
        Network.CLIENT=ClientSession.INSTANCE::accept;
        if(Boolean.getBoolean("ydnf.verify"))cn.yumis.fight.modules.skills.api.client.SkillDiagnostics.attack=(weapon,value)->{cn.yumis.fight.verification.FeedbackProbe.weapon=weapon;cn.yumis.fight.verification.FeedbackProbe.attack=value;};
        Effects.VFX.register();
        ChampionSkillPoses.install();
        cn.yumis.fight.modules.presentation.api.client.NearEffectRendering.register(bus);
        cn.yumis.fight.modules.presentation.api.client.EffectParticles.register(bus);
        cn.yumis.fight.modules.presentation.api.client.WorldEffects.register();
        EnemyHealthBars.register(
            entity -> entity instanceof ArkEnemyEntity || entity instanceof TrainingSandbag || entity instanceof RitualMechanismEntity,
            ClientBootstrap::isBoss,
            entity -> {
                var target = local.targets.get(entity.getUUID());
                return target == null ? entity.getMaxHealth()
                    : target.health(target.level(local.progress.level, local.encounterSync()));
            });
        LocalLights.register(bus, () -> Math.max(LightsaberLight.heldLight(), IndustrialVisuals.heldLight()),
            entity -> isBoss(entity) ? BOSS_LIGHT_LEVEL : 0);
        cn.yumis.fight.modules.party.client.PartyHealthBars.register(() -> ClientSession.INSTANCE.party, player -> {
            var session=ClientSession.INSTANCE;
            var target=session.targets.get(player.getUUID());
            return target==null ? Math.clamp((double) player.getHealth() / player.getMaxHealth(), 0, 1)
                : Math.clamp(target.life.q, 0, 1);
        });
        cn.yumis.fight.modules.dungeons.client.TreasureWeaponVisuals.register();
        cn.yumis.fight.modules.dungeons.client.GblVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.PalaceVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.ForbiddenVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.SkasaVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.DarkCityVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.ClassicVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.DarkCityClient.register(()->ClientSession.INSTANCE.own);
        cn.yumis.fight.modules.dungeons.client.EncounterClientContacts.register(()->ClientSession.INSTANCE.own,()->ClientSession.INSTANCE.progress.level,()->ClientSession.INSTANCE.encounterSync(),()->SkillController.INSTANCE.invulnerable());
        cn.yumis.fight.modules.dungeons.client.CastleCombatClient.register(bus, local::context, SkillController.INSTANCE::boss);
        cn.yumis.fight.modules.dungeons.client.EnemyTelegraphs.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.KenoVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.AbyssVisuals.register(()->ClientSession.INSTANCE.own.instance);
        cn.yumis.fight.modules.dungeons.client.ElainaBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.ElainaBossClient.register(new cn.yumis.fight.modules.dungeons.client.ElainaBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void cancelActiveForControl(){SkillController.INSTANCE.cancelActiveForControl();}
            public void applyAllCooldown100s(long cooldownMs){
                var s=ClientSession.INSTANCE;
                if(cn.yumis.fight.modules.skills.api.SealBossSkillPenalties.applyAllActiveCooldowns(s.progress,SkillController.INSTANCE.snapshot(),s.resources,ClientSession.now(),cooldownMs)>0)s.upload();
            }
            public void applyRewindRandomCooldown(java.util.Set<String> permaSealed,long zeroCdFallbackMs){
                var s=ClientSession.INSTANCE;
                String chosen=cn.yumis.fight.modules.skills.api.SealBossSkillPenalties.applyRewindRandomCooldown(s.progress,SkillController.INSTANCE.snapshot(),s.resources.equipment,s.resources,permaSealed,ClientSession.now(),zeroCdFallbackMs,new java.util.Random());
                if(!chosen.isEmpty())s.upload();
            }
            public String sealRandomActiveSkill(java.util.Set<String> permaSealed,long sealDurationMs){
                var s=ClientSession.INSTANCE;
                String chosen=cn.yumis.fight.modules.skills.api.SealBossSkillPenalties.applyPermasealSkill(s.progress,SkillController.INSTANCE.snapshot(),s.resources,permaSealed,ClientSession.now(),sealDurationMs,new java.util.Random());
                if(!chosen.isEmpty())s.upload();
                return chosen;
            }
            public void clearSealedCooldowns(java.util.Set<String> permaSealed){
                var s=ClientSession.INSTANCE;
                if(cn.yumis.fight.modules.skills.api.SealBossSkillPenalties.clearPermasealedCooldowns(s.resources,permaSealed,ClientSession.now()))s.upload();
            }
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
            public void finaleMeteor1Damage(double ratio,com.google.gson.JsonObject boss){SkillController.INSTANCE.sealBossRatioDamage(ratio,boss);}
            public void finaleMeteor2Wipe(com.google.gson.JsonObject boss){SkillController.INSTANCE.sealBossInstantDefeat(boss);}
        });
        cn.yumis.fight.modules.dungeons.client.LiangyueBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.LiangyueBossClient.register(new cn.yumis.fight.modules.dungeons.client.LiangyueBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
        });
        cn.yumis.fight.modules.dungeons.client.FrostnovaBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.register(new cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
        });
        cn.yumis.fight.modules.dungeons.client.GilgameshBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.register(new cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
            public void ratioDamage(double ratio,com.google.gson.JsonObject boss){SkillController.INSTANCE.sealBossRatioDamage(ratio,boss);}
            public void instantDefeat(com.google.gson.JsonObject boss){SkillController.INSTANCE.sealBossInstantDefeat(boss);}
        });
        cn.yumis.fight.modules.dungeons.client.SwordMastersBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.SwordMastersBossClient.register(new cn.yumis.fight.modules.dungeons.client.SwordMastersBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
        });
        cn.yumis.fight.modules.dungeons.client.RoxyBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.RoxyBossClient.register(new cn.yumis.fight.modules.dungeons.client.RoxyBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
        });
        cn.yumis.fight.modules.dungeons.client.LucasBossVisuals.register();
        cn.yumis.fight.modules.dungeons.client.LucasBossClient.register(new cn.yumis.fight.modules.dungeons.client.LucasBossClient.Callbacks(){
            public cn.yumis.fight.modules.combat.api.Life own(){return ClientSession.INSTANCE.own;}
            public void bossContact(com.google.gson.JsonObject hitPacket){SkillController.INSTANCE.boss(hitPacket);}
        });
        cn.yumis.fight.modules.economy.client.LootBeamVisuals.register();
        cn.yumis.fight.modules.dungeons.client.RitualFightClient.register(()->ClientSession.INSTANCE.own,cn.yumis.fight.modules.quests.client.TutorialClient::awakeningAtAltar);
        cn.yumis.fight.modules.industry.client.IndustrialBeamRenderer.register();
        cn.yumis.fight.modules.industry.client.InfusionParticles.register(bus);
        cn.yumis.fight.modules.industry.client.ManaStorageBagClient.register(bus);
        cn.yumis.fight.modules.industry.client.ConveyorPlacementClient.register();
        cn.yumis.fight.modules.industry.client.ManaWandClient.register(bus,stack->{var rarity=IndustrialItemDisplay.rarity(stack);return rarity==null?null:rarity.color();});
        cn.yumis.fight.modules.industry.client.ArtificialStarHazard.register(SkillController.INSTANCE::environmentDamage);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.player.ItemTooltipEvent event)->{
            IndustrialItemDisplay.tooltip(event);ProfessionItemDisplay.tooltip(event);
            if(IndustrialItemDisplay.rarity(event.getItemStack())==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC||ProfessionItemDisplay.rarity(event.getItemStack())==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC){int last=event.getToolTip().size()-1;event.getToolTip().set(last,cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(event.getToolTip().get(last)));}
        });
        cn.yumis.fight.modules.skills.client.SkillTooltip.register(bus);
        cn.yumis.fight.modules.equipment.api.client.MythicDisplay.additional(stack->cn.yumis.fight.modules.economy.api.ItemRarity.rarity(stack)==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC);
        ClientSettings.load();
        UiTheme.load();
        UiFonts.register(bus);
        CurrencyUi.register();
        DimensionalScreen.links(()->cn.yumis.fight.modules.economy.client.SaleClient.open(true),cn.yumis.fight.modules.economy.client.ShopPage::openProduct);
        NeoForge.EVENT_BUS.addListener((RenderTooltipEvent.Texture event)->{if(cn.yumis.fight.modules.economy.api.ItemRarity.rarity(event.getItemStack())==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC||cn.yumis.fight.modules.equipment.api.client.MythicDisplay.marked(event.getComponents()))event.setTexture(cn.yumis.fight.modules.equipment.api.client.MythicDisplay.TOOLTIP);});
        EquipmentEligibility.CLIENT_PROGRESS=p->ClientSession.INSTANCE.progress;
        EquipmentTooltip.install(id->{var currency=cn.yumis.fight.modules.economy.api.CurrencyRules.find(id);if(currency==null)return null;var label=cn.yumis.fight.modules.presentation.api.client.UiFonts.decorate(net.minecraft.network.chat.Component.literal(currency.rarity().title()+" · 通货").withStyle(style->style.withColor(currency.rarity().color()).withItalic(false)));return currency.rarity()==cn.yumis.fight.modules.equipment.api.Rarity.MYTHIC?cn.yumis.fight.modules.equipment.api.client.MythicDisplay.mark(label):label;},
            id->cn.yumis.fight.modules.dungeons.api.DungeonCatalog.current().dungeons.stream().filter(d->d.materials().contains(id)).map(d->"来源："+d.name()+(d.available()?"":"（尚未开放）")).toList(),
            ()->Boolean.getBoolean("ydnf.verify")&&VerificationDriver.tooltipShift);
        bus.addListener((RegisterKeyMappingsEvent e)->{
            MenuInput.register(e);
            e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.LEAP);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.RENEW);e.register(cn.yumis.fight.modules.skills.api.client.SkillInputs.FOLLOW);for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)e.register(key);
        }
        );
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (ClientStartedEvent e)->MenuInput.clearDefaultConflictsOnce(e.getClient()));
        bus.addListener((RegisterSelectItemModelPropertyEvent e)->{
            e.register(id("katana_style"),StyleProperty.TYPE);e.register(id("material"),MaterialProperty.TYPE);
        }
        );
        bus.addListener((RegisterMenuScreensEvent e)->{e.register(cn.yumis.fight.modules.economy.platform.ShopContent.SHOP.get(),cn.yumis.fight.modules.economy.client.ShopScreen::new);e.register(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR_MENU.get(),FabricatorScreen::new);e.register(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.HORADRIC_CUBE_MENU.get(),cn.yumis.fight.modules.equipment.client.HoradricCubeScreen::new);e.register(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.ENHANCE_MENU.get(),cn.yumis.fight.modules.equipment.client.EnhanceScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_MENU.get(),IndustrialScreen::new);e.register(IndustrialContent.PROSPECTOR_MENU.get(),cn.yumis.fight.modules.industry.client.ProspectorScreen::new);e.register(cn.yumis.fight.modules.industry.platform.IndustrialContent.STORAGE_MENU.get(),DimensionalScreen::new);});
        bus.addListener((RegisterFluidModelsEvent e)->e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(0xff70cbd2)),cn.yumis.fight.modules.industry.platform.IndustrialContent.COOLANT,cn.yumis.fight.modules.industry.platform.IndustrialContent.FLOWING_COOLANT));
        bus.addListener((RegisterFluidModelsEvent e)->{
            for(var liquid:IndustrialContent.LIQUIDS.values())e.register(new net.minecraft.client.renderer.block.FluidModel.Unbaked(new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_still")),new net.minecraft.client.resources.model.sprite.Material(Identifier.withDefaultNamespace("block/water_flow")),null,net.neoforged.neoforge.client.fluid.FluidTintSources.constant(liquid.color)),liquid.source,liquid.flowing);
        });
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{
            for(var entry:cn.yumis.fight.modules.skills.api.SkillContentTypes.SUMMONS.entrySet())e.registerEntityRenderer(entry.getValue().get(),c->new cn.yumis.fight.modules.skills.client.SummonRenderer(c,entry.getKey()));
            e.registerEntityRenderer(cn.yumis.fight.modules.npc.api.NpcContentTypes.NPC.get(),cn.yumis.fight.modules.npc.api.client.NpcRendering::human);
            e.registerEntityRenderer(cn.yumis.fight.modules.arkguard.api.ArkContentTypes.ROLE.get(),cn.yumis.fight.modules.npc.api.client.NpcRendering::human);
            for(var entry:cn.yumis.fight.modules.arkguard.api.ArkContentTypes.ENEMIES.entrySet())e.registerEntityRenderer(entry.getValue().get(),context->entry.getKey().equals("goblin_shaman")?new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"goblin_camp/keno",.9f,.8f):entry.getKey().equals("lucas")?new cn.yumis.fight.modules.dungeons.client.LucasRenderer(context,false,.8f):cn.yumis.fight.modules.dungeons.api.client.DungeonMobRendering.basic(context,entry.getKey()));
            e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.TRAINING_SANDBAG.get(),cn.yumis.fight.modules.dungeons.client.TrainingSandbagRenderer::new);
            for(var mechanism:cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.MECHANISMS.entrySet())
                e.registerEntityRenderer(mechanism.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.RitualMechanismRenderer(context,mechanism.getKey()));
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.MACHINE_BE.get(),IndustrialRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.DISPLAY_STAND_BE.get(),cn.yumis.fight.modules.industry.client.DisplayStandRenderer::new);
            e.registerBlockEntityRenderer(IndustrialContent.DEMON_ALTAR_BE.get(),cn.yumis.fight.modules.industry.client.DemonAltarRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.CONVEYOR_BE.get(),cn.yumis.fight.modules.industry.client.ConveyorRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.FLUID_PIPE_BE.get(),cn.yumis.fight.modules.industry.client.FluidPipeRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.industry.platform.IndustrialContent.INFINITE_SOURCE_BE.get(),cn.yumis.fight.modules.industry.client.InfiniteSourceRenderer::new);
            e.registerBlockEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.WAYSTONE_PEDESTAL_ENTITY.get(),cn.yumis.fight.modules.dungeons.client.WaystonePedestalRenderer::new);
            e.registerEntityRenderer(cn.yumis.fight.modules.economy.api.RewardContentTypes.PRIVATE_LOOT.get(),LootRenderer::new);e.registerEntityRenderer(cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.CAGE.get(),CageRenderer::new);for(var type:cn.yumis.fight.modules.dungeons.api.DungeonContentTypes.ENEMIES.entrySet()){
                if(type.getKey().equals("golden_king"))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.GoldenKingRenderer::new);
                else if(type.getKey().equals(cn.yumis.fight.modules.dungeons.content.SealTowerElaina.ID))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.ElainaBossRenderer::new);
                else if(type.getKey().equals(cn.yumis.fight.modules.dungeons.content.TheaterTowerLiangyue.ID))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.LiangyueBossRenderer::new);
                else if(type.getKey().equals(cn.yumis.fight.modules.dungeons.content.FrostTowerFrostnova.ID))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.FrostnovaBossRenderer::new);
                else if(type.getKey().equals(cn.yumis.fight.modules.dungeons.content.OrreryTowerGilgamesh.ID))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.GilgameshBossRenderer::new);
                else if(cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.Member.find(type.getKey())!=null){var member=cn.yumis.fight.modules.dungeons.content.ForgeTowerSwordMasters.Member.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.SwordMastersBossRenderer(context,member));}
                else if(type.getKey().equals(cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy.ID))e.registerEntityRenderer(type.getValue().get(),cn.yumis.fight.modules.dungeons.client.RoxyBossRenderer::new);
                else if(cn.yumis.fight.modules.dungeons.api.GblEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.GblEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"gbl/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.ForbiddenEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"forbidden/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.SkasaEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"skasa/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.DarkCityEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"dark_city/"+kind.model,kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.ClassicEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,kind.resource(),kind.width));}
                else if(cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.PalaceEnemyKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.PalaceEnemyRenderer(context,kind));}
                else if(cn.yumis.fight.modules.dungeons.api.AltarBossKind.find(type.getKey())!=null){var kind=cn.yumis.fight.modules.dungeons.api.AltarBossKind.find(type.getKey());e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,kind.resource(),kind.width));}
                else if(type.getKey().equals("goblin_shaman"))e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.GblEnemyRenderer(context,"goblin_camp/keno",1.3f));
                else if(type.getKey().equals("lucas"))e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.LucasRenderer(context,false,1.0f));
                else if(type.getKey().equals("lucas_clone"))e.registerEntityRenderer(type.getValue().get(),context->new cn.yumis.fight.modules.dungeons.client.LucasRenderer(context,true,0.96f));
                else if(type.getKey().startsWith("goblin"))e.registerEntityRenderer(type.getValue().get(),context->cn.yumis.fight.modules.dungeons.api.client.DungeonMobRendering.basic(context,type.getKey()));
                else{
                    e.registerEntityRenderer(type.getValue().get(),context->cn.yumis.fight.modules.dungeons.api.client.DungeonMobRendering.basic(context,type.getKey()));
                }
            }
        }
        );
        bus.addListener((EntityRenderersEvent.RegisterLayerDefinitions e)->{
            for(String kind:java.util.List.of("goblin","dragonman","wyvern"))e.registerLayerDefinition(EnemyRenderer.layer(kind),()->EnemyModel.layer(kind));
            e.registerLayerDefinition(cn.yumis.fight.modules.dungeons.client.RoxyBossRenderer.GEAR_LAYER,cn.yumis.fight.modules.dungeons.client.RoxyBossRenderer::gearLayer);
        }
        );
        bus.addListener((RegisterGuiLayersEvent e)->{e.registerAboveAll(id("hud"),Hud::render);e.registerAboveAll(id("stamina"),CloudMovement::render);e.registerAboveAll(id("ritual"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.RitualFightClient.hud(graphics));e.registerAboveAll(id("tower"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.TowerClient.hud(graphics));e.registerAboveAll(id("seal_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.ElainaBossClient.hud(graphics));e.registerAboveAll(id("theater_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.LiangyueBossClient.hud(graphics));e.registerAboveAll(id("frost_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.FrostnovaBossClient.hud(graphics));e.registerAboveAll(id("orrery_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.hud(graphics));e.registerAboveAll(id("forge_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.SwordMastersBossClient.hud(graphics));e.registerAboveAll(id("lucas_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.LucasBossClient.hud(graphics));e.registerAboveAll(id("sluice_boss"),(graphics,delta)->cn.yumis.fight.modules.dungeons.client.RoxyBossClient.hud(graphics));});
        bus.addListener((RegisterGuiLayersEvent e)->e.registerAboveAll(id("dungeon_entry"),
            (graphics,delta)->cn.yumis.fight.modules.dungeons.client.DungeonEntryTitle.render(graphics)));
        // Jade renders in RenderGuiEvent.Post at NORMAL; draw the target panel afterward.
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,Hud::renderTarget);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(RenderGuiEvent.Post event)->{cn.yumis.fight.modules.npc.client.NpcClient.render(event.getGuiGraphics());cn.yumis.fight.modules.quests.client.TutorialOverlay.render(event.getGuiGraphics());});
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,(RenderGuiEvent.Post event)->cn.yumis.fight.modules.arkguard.client.ArkGuardClient.render(event.getGuiGraphics()));
        cn.yumis.fight.modules.arkguard.client.ArkVisuals.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,cn.yumis.fight.modules.arkguard.client.ArkGuardClient::scroll);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,cn.yumis.fight.modules.arkguard.client.ArkGuardClient::interact);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,cn.yumis.fight.modules.npc.client.NpcClient::scroll);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,cn.yumis.fight.modules.npc.client.NpcClient::interact);
        NeoForge.EVENT_BUS.addListener(ClientBootstrap::movementFov);
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{
            cn.yumis.fight.modules.npc.client.NpcClient.tick();cn.yumis.fight.modules.quests.client.TutorialOverlay.tick();IndustrialVisuals.tick();
            ClientSession.INSTANCE.tick();cn.yumis.fight.modules.arkguard.client.ArkGuardClient.tick();cn.yumis.fight.modules.quests.client.TutorialClient.tick();JarClient.tick();cn.yumis.fight.modules.economy.client.AutoSellClient.tick();if(JarClient.blocking())cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.stopRenewing();SkillController.INSTANCE.tick();CloudMovement.tick();VerificationDriver.tick();
            cn.yumis.fight.modules.dungeons.client.DungeonDefeatScreen.update(ClientSession.INSTANCE.ready,ClientSession.INSTANCE.own);
            var mc=Minecraft.getInstance();
            MenuInput.consumeClicks(()->!JarClient.blocking()&&mc.player!=null&&mc.gui.screen()==null);
            while(cn.yumis.fight.modules.skills.api.client.SkillInputs.RENEW.consumeClick())if(!JarClient.blocking())cn.yumis.fight.modules.skills.client.CareerClient.INSTANCE.renewSummons();
            while(cn.yumis.fight.modules.skills.api.client.SkillInputs.FOLLOW.consumeClick())if(!JarClient.blocking())cn.yumis.fight.modules.presentation.api.client.ClientUi.current().send("summonFollow",cn.yumis.fight.modules.presentation.api.client.ClientUi.current().operation("summonFollow"));
            for(var key:cn.yumis.fight.modules.skills.api.client.SkillInputs.SKILLS)while(key.consumeClick()){
            }
            ;
        }
        );
        NeoForge.EVENT_BUS.addListener((InputEvent.MouseScrollingEvent event)->{if(JarClient.blocking()||cn.yumis.fight.modules.skills.client.Effects.VFX.blocksHotbar())event.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered e)->{
            if(JarClient.blocking()||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen()){e.setCanceled(true);e.setSwingHand(false);return;}
            boolean attachedDecoration=Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity() instanceof net.minecraft.world.entity.decoration.BlockAttachedEntity;
            if(e.isAttack()&&!attachedDecoration&&SkillController.INSTANCE.validWeapon()&&SkillController.INSTANCE.shouldInterceptBlockAttack(Minecraft.getInstance())){
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
            if(e.getNewScreen()!=null&&Minecraft.getInstance().player!=null){
                SkillController.INSTANCE.cancel(false);
                cn.yumis.fight.modules.skills.client.DemonHunterClient.cancelOnScreen();
                cn.yumis.fight.modules.skills.client.ElementalistChargeClient.cancel();
                cn.yumis.fight.modules.skills.client.ElementalistClient.cancelOnScreen();
                cn.yumis.fight.modules.skills.client.BattleMageChargeClient.cancel();
                cn.yumis.fight.modules.skills.client.BattleMageClient.cancelOnScreen();
                cn.yumis.fight.modules.skills.client.WitchClient.cancelOnScreen();
            }
            CloudMovement.screen(e.getNewScreen()!=null);
        }
        );
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e)->{
            if(JarClient.blocking()||SkillController.INSTANCE.invulnerable()||cn.yumis.fight.modules.skills.client.ElementalistClient.blocksMovement()||cn.yumis.fight.modules.skills.client.BattleMageClient.blocksMovement()||cn.yumis.fight.modules.skills.client.WitchClient.blocksMovement()||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen()||cn.yumis.fight.modules.dungeons.client.GilgameshBossClient.blocksMovement()||cn.yumis.fight.modules.combat.api.CombatContentTypes.controlled(e.getEntity())){
                e.getInput().keyPresses=net.minecraft.world.entity.player.Input.EMPTY;((cn.yumis.fight.mixin.InputAccessor)e.getInput()).ydnf$move(net.minecraft.world.phys.Vec2.ZERO);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ItemTooltipEvent e)->{
            EquipmentTooltip.item(e);
            var material=cn.yumis.fight.modules.economy.api.ShopDefinitions.materialRarity(e.getItemStack());
            if(material!=null){if(!e.getToolTip().isEmpty())e.getToolTip().set(0,e.getToolTip().getFirst().copy().withColor(material.color()));e.getToolTip().add(UiFonts.decorate(net.minecraft.network.chat.Component.literal("稀有度 · "+material.title()).withColor(material.color())));}
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
        NeoForge.EVENT_BUS.addListener((ScreenEvent.MouseScrolled.Pre e)->gemSocketScroll(e));
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
        if(JarClient.blocking()||cn.yumis.fight.modules.dungeons.api.client.SealBossState.isFrozen())return false;
        var mc=Minecraft.getInstance();
        boolean control=InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_LEFT_CONTROL)||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_RIGHT_CONTROL);
        if(mc.player!=null&&ClientSession.INSTANCE.ready&&event.key()==GLFW.GLFW_KEY_RIGHT_BRACKET&&control&&(mc.gui.screen()==null||mc.gui.screen() instanceof FightScreen)){
            if(action==GLFW.GLFW_PRESS){var state=ClientSession.INSTANCE;cn.yumis.fight.modules.combat.api.client.DamageAnalysis.toggle(state.resources.combatUntil,state.trainingMeter.has("id")&&!state.trainingMeter.get("completed").getAsBoolean());state.message("伤害分析："+(cn.yumis.fight.modules.combat.api.client.DamageAnalysis.recording()?"开始记录":"停止记录")+"；在角色页面查看结果");}
            return true;
        }
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.DemonHunterClient.cancelChannel())return true;
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.ElementalistClient.cancelChannel())return true;
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.BattleMageClient.cancelChannel())return true;
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.WitchClient.cancelChannel())return true;
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.ChantClient.active()){cn.yumis.fight.modules.skills.client.ChantClient.cancel();return true;}
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.ElementalistChargeClient.active()){cn.yumis.fight.modules.skills.client.ElementalistChargeClient.cancel();return true;}
        if(action==GLFW.GLFW_PRESS&&event.key()==GLFW.GLFW_KEY_ESCAPE&&cn.yumis.fight.modules.skills.client.BattleMageChargeClient.active()){cn.yumis.fight.modules.skills.client.BattleMageChargeClient.cancel();return true;}
        if(action==GLFW.GLFW_REPEAT&&mc.player!=null&&repeatMenuShortcut(mc.gui.screen(),InputConstants.getKey(event)))return true;
        if(cn.yumis.fight.modules.skills.client.WitchClient.handleKey(event,action))return true;
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
    private static boolean isBoss(LivingEntity entity) {
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return true;
        if (entity instanceof ArkEnemyEntity enemy) return enemy.boss();
        var target = ClientSession.INSTANCE.targets.get(entity.getUUID());
        return target != null && target.boss();
    }

    private static boolean repeatMenuShortcut(Screen screen,InputConstants.Key key){
        if(screen!=null&&screen.getFocused() instanceof EditBox box&&box.isFocused())return false;
        if(screen==null||screen instanceof FightScreen)return MenuInput.MENU.matches(key)||MenuInput.pageShortcut(key)!=null;
        return screen instanceof DimensionalScreen&&(MenuInput.MENU.matches(key)||MenuInput.matchesPageShortcut("bag",key));
    }
    private static boolean currencyGesture;
    private static void movementFov(net.neoforged.neoforge.client.event.ComputeFovModifierEvent event){
        var player=event.getPlayer();
        var speed=player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        float walking=player.getAbilities().getWalkingSpeed();
        if(speed==null||walking<=0)return;
        double base=Math.max(walking,speed.getBaseValue()),baseMultiplier=1,totalMultiplier=1;
        for(var modifier:speed.getModifiers()){
            if(modifier.amount()<=0)continue;
            switch(modifier.operation()){
                case ADD_VALUE->base+=modifier.amount();
                case ADD_MULTIPLIED_BASE->baseMultiplier+=modifier.amount();
                case ADD_MULTIPLIED_TOTAL->totalMultiplier*=1+modifier.amount();
            }
        }
        double normal=speed.getAttribute().value().sanitizeValue(base*baseMultiplier*totalMultiplier);
        double actual=speed.getValue();
        if(actual>=normal)return;
        float speedRatio=(float)((walking+normal)/(walking+actual));
        event.setNewFovModifier(event.getNewFovModifier()
            +event.getFovScale()*event.getFovModifier()*(speedRatio-1));
    }
    private static void currency(ScreenEvent.MouseButtonPressed.Pre e){
        if(e.getButton()!=1)return;
        currencyGesture=false;
        var mc=Minecraft.getInstance();
        if(!(e.getScreen() instanceof AbstractContainerScreen<?> screen)||mc.player==null)return;
        if(screen instanceof cn.yumis.fight.modules.economy.client.ShopScreen shop&&shop.modal()){e.setCanceled(true);return;}
        var s=ClientSession.INSTANCE;
        if(!s.ready)return;
        var target=CurrencyUi.target(screen,e.getMouseX(),e.getMouseY());
        if(target==null)return;
        var menu=target.menu();
        var carried=CurrencyUi.processingItem(menu);
        if(!CurrencyUi.isCurrency(carried))return;
        Gear gear=EquipmentItems.gear(target.slot().getItem());
        if(gear==null)return;
        e.setCanceled(true);
        currencyGesture=true;
        boolean creative=screen instanceof CreativeModeInventoryScreen;
        if(EquipmentActions.currencyPending())return;
        var j=s.operation("currency");
        if(creative)j.addProperty("creativeCursor",true);
        if(menu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu dimensional&&dimensional.selectedMaterial()!=null)j.addProperty("storageMaterial",true);
        j.addProperty("session",s.session);
        j.addProperty("menu",menu.containerId);
        j.addProperty("slot",target.index());
        j.addProperty("equipment",gear.instance.toString());
        j.addProperty("revision",gear.revision);
        j.addProperty("count",carried.getCount());
        j.add("cursor",InventoryEdits.encode(mc.player,carried));
        String currencyId=ItemData.id(carried);
        if(cn.yumis.fight.modules.equipment.api.Gemstones.currency(currencyId))j.addProperty("socket",GemSocketSelection.insertTarget(menu.containerId,target.index(),gear));
        EquipmentActions.beginCurrency(j.get("operation").getAsString());
        s.send("currency",j);
        GemSocketSelection.clear();
    }
    private static void gemSocketScroll(ScreenEvent.MouseScrolled.Pre e){
        if(e.getScrollDeltaY()==0)return;
        if(!(e.getScreen() instanceof AbstractContainerScreen<?> screen)||Minecraft.getInstance().player==null)return;
        var s=ClientSession.INSTANCE;
        if(!s.ready)return;
        var target=CurrencyUi.target(screen,e.getMouseX(),e.getMouseY());
        if(target==null)return;
        if(!cn.yumis.fight.modules.equipment.api.Gemstones.currency(ItemData.id(CurrencyUi.processingItem(target.menu()))))return;
        Gear gear=EquipmentItems.gear(target.slot().getItem());
        if(gear==null||gear.sockets<=1||gear.gems.size()<gear.sockets)return;
        e.setCanceled(true);
        GemSocketSelection.scroll(target.menu().containerId,target.index(),gear,e.getScrollDeltaY());
    }
}

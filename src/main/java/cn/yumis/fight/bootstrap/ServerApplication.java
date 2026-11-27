package cn.yumis.fight.bootstrap;
import cn.yumis.fight.verification.IndustrialVisualVerification;
import cn.yumis.fight.verification.IndustrialVerification;
import cn.yumis.fight.modules.equipment.api.EquipmentEligibility;
import cn.yumis.fight.modules.presentation.api.ModText;

import cn.yumis.fight.modules.equipment.api.EquipmentStats;
import cn.yumis.fight.modules.equipment.api.EquipmentLayout;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.modules.equipment.api.MaterialCatalog;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.equipment.api.EquipmentDefinitions;
import cn.yumis.fight.modules.affixes.api.AffixDefinitions;
import cn.yumis.fight.modules.attributes.api.AttributeNames;
import cn.yumis.fight.modules.attributes.api.AttributeServices;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.dungeons.api.Difficulty;
import cn.yumis.fight.modules.industry.platform.IndustrialWorld;
import cn.yumis.fight.modules.industry.api.IndustrialRules;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import cn.yumis.fight.modules.inventory.application.InventoryTransactions;

import cn.yumis.fight.modules.economy.application.LootService;
import cn.yumis.fight.modules.economy.application.JarService;
import cn.yumis.fight.modules.economy.api.JarDefinitions;
import cn.yumis.fight.modules.economy.api.LootJarItem;
import cn.yumis.fight.modules.dungeons.application.DungeonService;
import cn.yumis.fight.modules.dungeons.application.DamageMeterService;
import cn.yumis.fight.modules.skills.application.VisualService;
import cn.yumis.fight.modules.combat.application.NativeHits;
import cn.yumis.fight.modules.equipment.api.EquipmentRules;
import cn.yumis.fight.modules.inventory.api.DimensionalMenu;
import cn.yumis.fight.foundation.Journal;

import cn.yumis.fight.network.Network;

import cn.yumis.fight.modules.character.api.Progress;

import cn.yumis.fight.modules.combat.api.Runtime;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.combat.api.Life;
import cn.yumis.fight.modules.dungeons.api.Target;

import static cn.yumis.fight.bootstrap.ApplicationJson.JSON;
import static cn.yumis.fight.bootstrap.ApplicationJson.copy;
import static cn.yumis.fight.modules.combat.api.LifeRules.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.resources.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.*;

import net.minecraft.world.item.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.storage.LevelResource;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.nio.file.*;
import java.util.*;
public final class ServerApplication {
    private static final org.slf4j.Logger LOG=com.mojang.logging.LogUtils.getLogger();
    private static ServerApplication CURRENT;
    public final MinecraftServer server;
    public final Path journalPath;
    public final Journal journal;
    public final cn.yumis.fight.modules.professions.application.ProfessionService professionService;
    public final cn.yumis.fight.modules.party.application.PartyService partyService;
    public final InventoryTransactions inventory;
    private final cn.yumis.fight.modules.industry.application.ConveyorConstructionService conveyorConstruction;
    public final cn.yumis.fight.modules.economy.application.ChestService chests;
    public final JarService jars;
    public final cn.yumis.fight.modules.economy.application.ShopService shops;
    public final cn.yumis.fight.modules.npc.application.NpcService npcs;
    public final cn.yumis.fight.modules.arkguard.application.ArkGuardService arkGuard;
    public final cn.yumis.fight.modules.quests.application.TutorialService tutorials;
    public final cn.yumis.fight.modules.quests.application.AwakeningQuestService awakeningQuests;
    public final cn.yumis.fight.modules.quests.application.PromotionQuestService promotionQuests;
    public final cn.yumis.fight.modules.quests.application.CommissionService commissions;
    public final cn.yumis.fight.modules.dungeons.application.RitualEncounterService rituals;
    private final cn.yumis.fight.modules.economy.application.EconomyService.Drafts economyDrafts=new cn.yumis.fight.modules.economy.application.EconomyService.Drafts();
    public final DungeonService dungeons;
    public final LootService loot;
    public final VisualService visuals;
    public final DamageMeterService damageMeter;
    public final cn.yumis.fight.modules.dungeons.application.TrainingService trainingService;
    public final NativeHits nativeHits;
    public final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private final Set<UUID> loggingOut=new HashSet<>();
    private final Set<UUID> recoveringDefeat=new HashSet<>();
    private final ArrayDeque<Runnable> combatQueue=new ArrayDeque<>();
    public final Random random=new Random();
    public boolean bridge;
    public Difficulty worldDifficulty=Difficulty.NORMAL;
    public RewardSettings rewards=RewardSettings.DEFAULT;
    public cn.yumis.fight.modules.combat.api.CombatSettings combatSettings=cn.yumis.fight.modules.combat.api.CombatSettings.DEFAULT;
    private final cn.yumis.fight.architecture.CommandRouter<ServerPlayer,JsonObject> commands=new cn.yumis.fight.architecture.CommandRouter<>();
    public final cn.yumis.fight.modules.equipment.application.EquipmentApplication equipmentApplication;
    public final cn.yumis.fight.modules.economy.application.EconomyService economyService;
    public final cn.yumis.fight.modules.equipment.application.EquipmentContracts equipmentContracts;
    public final cn.yumis.fight.modules.character.application.ProgressService progressService;
    public final cn.yumis.fight.modules.character.api.CharacterPersistence characters;
    public final cn.yumis.fight.modules.inventory.application.InventoryApplication inventoryApplication;
    public final cn.yumis.fight.modules.combat.application.CombatApplication combatApplication;
    public final cn.yumis.fight.modules.skills.application.CareerSkillService careers;
    public final cn.yumis.fight.modules.skills.application.MageSkillService mages;
    public final cn.yumis.fight.modules.equipment.application.EquipmentLayoutService equipmentLayout;
    public final cn.yumis.fight.modules.dungeons.application.TargetService targets;
    public final cn.yumis.fight.modules.dungeons.application.WorldHealthService worldHealth;
    private int ticks;
    private final Map<UUID,ProfileSync.Sender> viewSenders=new HashMap<>();
    private ApplicationRules.Bundle definitionsBundle;
    private JsonObject wireDefinitions;
    private String definitionsKey="";
    public String definitionKey(){definitions();return definitionsKey;}
    private JsonObject definitions(){
        var bundle=ApplicationRules.current();
        if(bundle!=definitionsBundle){
            JsonObject value=new JsonObject();
            value.add("industrialRules",JSON.toJsonTree(bundle.industry()));value.add("materialCatalog",JSON.toJsonTree(bundle.materials()));
            value.add("dungeonDefinitions",JSON.toJsonTree(bundle.dungeons()));value.add("attributeNames",JSON.toJsonTree(bundle.names()));
            value.add("affixDefinitions",JSON.toJsonTree(bundle.affixes()));value.add("equipmentDefinitions",JSON.toJsonTree(bundle.equipment()));value.add("equipmentLayout",JSON.toJsonTree(bundle.layout()));
            value.add("skillBalanceRules",JSON.toJsonTree(bundle.skills()));
            value.add("chestDefinitions",JSON.toJsonTree(bundle.chests()));
            value.add("jarDefinitions",JSON.toJsonTree(bundle.jars()));
            value.add("shopDefinitions",JSON.toJsonTree(bundle.shops()));value.add("npcDefinitions",JSON.toJsonTree(bundle.npcs()));
            value.add("questDefinitions",JSON.toJsonTree(bundle.quests()));
            value.add("arkWarRules",JSON.toJsonTree(bundle.arkWar()));
            value.add("ritualBosses",JSON.toJsonTree(bundle.ritualBosses()));
            value.add("elementalistMorphs",JSON.toJsonTree(bundle.elementalistMorphs()));
            value.add("battleMageMorphs",JSON.toJsonTree(bundle.battleMageMorphs()));
            value.add("combatBalance",JSON.toJsonTree(bundle.combatBalance()));
            value.add("characterBalance",JSON.toJsonTree(bundle.characterBalance()));
            value.add("equipmentBalance",JSON.toJsonTree(bundle.equipmentBalance()));
            value.add("talentBalance",JSON.toJsonTree(bundle.talentBalance()));
            value.add("economyBalance",JSON.toJsonTree(bundle.economyBalance()));
            value.add("enemyBalance",JSON.toJsonTree(bundle.enemyBalance()));
            value.add("bossBalance",JSON.toJsonTree(bundle.bossBalance()));
            wireDefinitions=value;definitionsKey=ProfileSync.digest(value);definitionsBundle=bundle;
        }
        return wireDefinitions;
    }
    private final cn.yumis.fight.modules.combat.application.StunControl stunControl=new cn.yumis.fight.modules.combat.application.StunControl();
    public static final class Session extends cn.yumis.fight.modules.combat.api.CombatSession {
        public Progress progress;
        public Stats viewStats=new Stats();
        public boolean statsDirty;
        public long lastActionAt=System.currentTimeMillis();
        public double lastX,lastY,lastZ;
        public float lastYaw,lastPitch;
        public boolean positionInitialized;
        public long sourceRevision=-1;
        public long runtimeSequence=-1;
        public long runtimeGeneration=-1,runtimeEpoch=-1;
        public Session(Progress p,cn.yumis.fight.modules.combat.api.Runtime r,Life l){super(r,l);progress=p;r.equipmentBuffs.clear();}
    }
    private ServerApplication(MinecraftServer server)throws Exception{
        this.server=server;
        cn.yumis.fight.bootstrap.ApplicationRules.install();
        journalPath=server.getWorldPath(LevelResource.ROOT).resolve(ItemData.NS).resolve("modules-v1").resolve("state.wal");
        journal=new Journal(journalPath);
        characters=new cn.yumis.fight.modules.character.application.CharacterStore(journal);
        professionService=new cn.yumis.fight.modules.professions.application.ProfessionService(journal);
        partyService=new cn.yumis.fight.modules.party.application.PartyService(journal,server.getPlayerList()::getPlayerByName,id->server.getPlayerList().getPlayer(id)!=null,this::refreshAll);
        loot=cn.yumis.fight.bootstrap.RuntimeAssembly.lootService(this,economyDrafts);
        inventory=new InventoryTransactions(journal,loot,this::profile,this::message,cn.yumis.fight.bootstrap.RuntimeAssembly.currencyAction(this),ServerEvents::lootAcquired);
        equipmentLayout=new cn.yumis.fight.modules.equipment.application.EquipmentLayoutService(journal,inventory,this::profile);
        visuals=cn.yumis.fight.bootstrap.RuntimeAssembly.visualService(this);
        targets=RuntimeAssembly.targetService(this);
        nativeHits=cn.yumis.fight.bootstrap.RuntimeAssembly.nativeHits(this);
        dungeons=cn.yumis.fight.bootstrap.RuntimeAssembly.dungeonService(this);
        dungeons.installTreasure(inventory,new cn.yumis.fight.modules.dungeons.api.TreasurePorts(){
            public void chest(ServerPlayer p,String definition,int tier,int level,net.minecraft.world.phys.Vec3 position,String instance)throws Exception{loot.privateDrop(p,cn.yumis.fight.modules.economy.application.ChestService.chest(definition,tier,level),position,instance);}
            public void publish(){loot.publish();}public void abort(){loot.abort();}
        },random);
        dungeons.installWaystones(inventory,RuntimeAssembly.waystonePorts(this),random);
        chests=new cn.yumis.fight.modules.economy.application.ChestService(journal,inventory,RuntimeAssembly.rewardPlayers(this),loot,economyDrafts);
        jars=new JarService(journal,inventory,RuntimeAssembly.rewardPlayers(this),loot,economyDrafts,(p,message)->Network.send(p,"jarState",message));

        worldHealth=new cn.yumis.fight.modules.dungeons.application.WorldHealthService(server,journal,targets::resolve,targets::cached,this::session,()->bridge,value->bridge=value);
        damageMeter=cn.yumis.fight.bootstrap.RuntimeAssembly.damageMeterService(this);
        trainingService=new cn.yumis.fight.modules.dungeons.application.TrainingService(server,journal,this::session,p->dungeons.current(p.getUUID())!=null,(p,bag)->context(p,bag),this::message);
        nativeHits.targetAccess(trainingService::allowsNative);
        equipmentContracts=new cn.yumis.fight.modules.equipment.application.EquipmentContracts(journal);
        economyService=new cn.yumis.fight.modules.economy.application.EconomyService(journal,inventory,p->session(p).id,this::profile,equipmentContracts,economyDrafts,loot);
        equipmentApplication=new cn.yumis.fight.modules.equipment.application.EquipmentApplication(journal,inventory,p->session(p).id,this::profile,this::message,economyService::receiveBalances);
        shops=new cn.yumis.fight.modules.economy.application.ShopService(journal,inventory,economyService,economyDrafts,loot,p->session(p).id,this::profile);
        npcs=new cn.yumis.fight.modules.npc.application.NpcService(journal);
        cn.yumis.fight.modules.npc.api.NpcSpawns.install(npcs::group);
        cn.yumis.fight.modules.npc.api.NpcSpawns.relocator(npcs::relocateUnique);
        cn.yumis.fight.modules.npc.api.NpcSpawns.starter(npcs::starterWorld);
        cn.yumis.fight.modules.npc.api.NpcEntity.conversations(npcs::conversing);
        cn.yumis.fight.modules.npc.api.NpcEntity.staleUnique(npcs::isStaleUnique);
        cn.yumis.fight.modules.npc.api.NpcInteractionComponent.unregister("shop");
        cn.yumis.fight.modules.npc.api.NpcInteractionComponent.register("shop",(p,e,a)->shops.open(p,e.getUUID(),a.value()));
        cn.yumis.fight.modules.npc.api.NpcInteractionComponent.register("enhance",(p,e,a)->cn.yumis.fight.modules.equipment.api.EnhanceMenu.open(p));
        arkGuard=new cn.yumis.fight.modules.arkguard.application.ArkGuardService(server,journal,p->session(p).id.toString(),(p,value)->Network.send(p,"arkGuard",value),this::message,targets,loot::prepareExperience);
        arkGuard.installDungeonExclusion(p->dungeons.current(p.getUUID())!=null||p.level().dimension()==DungeonService.DIMENSION);
        arkGuard.installIdleMillis(this::idleMillis);
        inventory.onActivity(this::touchAction);
        nativeHits.worldDamage(worldHealth::nativeDamage);
        progressService=new cn.yumis.fight.modules.character.application.ProgressService(journal,characters,inventory,p->session(p).progress,(p,next)->session(p).progress=next,this::profile);
        tutorials=new cn.yumis.fight.modules.quests.application.TutorialService(journal,inventory,characters,RuntimeAssembly.rewardPlayers(this),RuntimeAssembly.rewardBalance(this),p->session(p).life.generation);
        awakeningQuests=new cn.yumis.fight.modules.quests.application.AwakeningQuestService(journal,inventory,characters,RuntimeAssembly.rewardPlayers(this),p->session(p).life.generation,server.getPlayerList()::getPlayer);
        promotionQuests=new cn.yumis.fight.modules.quests.application.PromotionQuestService(journal,characters,RuntimeAssembly.rewardPlayers(this),p->session(p).life.generation,server.getPlayerList()::getPlayer);
        commissions=new cn.yumis.fight.modules.quests.application.CommissionService(journal,inventory,RuntimeAssembly.rewardPlayers(this),RuntimeAssembly.rewardBalance(this),p->session(p).life.generation,server.getPlayerList()::getPlayer);
        rituals=RuntimeAssembly.rituals(this);
        inventoryApplication=new cn.yumis.fight.modules.inventory.application.InventoryApplication(journal,inventory,this::message);
        cn.yumis.fight.modules.industry.api.IndustrialPorts.install(inventory,this::message);
        cn.yumis.fight.modules.industry.application.BuddyGeneratorCapture.start(journal,server);
        conveyorConstruction=new cn.yumis.fight.modules.industry.application.ConveyorConstructionService(journal,inventory,server,p->session(p).id.toString(),(p,result)->Network.send(p,"conveyorBuilt",result),this::message);
        combatApplication=new cn.yumis.fight.modules.combat.application.CombatApplication(journal,server,this::session,cn.yumis.fight.modules.combat.api.ManagedEntities::attackable,targets::resolve,
            new cn.yumis.fight.modules.combat.api.CombatPorts.Encounters(){
                public boolean participant(ServerPlayer p,String instance){return dungeons.participant(p,instance);}
                public boolean recordPlayerDefeat(ServerPlayer p,String instance)throws Exception{return dungeons.recordPlayerDefeat(p,instance);}
                public void died(LivingEntity entity){if(entity instanceof cn.yumis.fight.modules.dungeons.api.DungeonEnemy)dungeons.died(entity.getUUID());}
                public void cancelBoss(UUID id){if(!rituals.cancelBoss(id))dungeons.cancelBoss(id);}
            },new cn.yumis.fight.modules.combat.api.CombatPorts.Rewards(){
                public void death(ServerPlayer p,LivingEntity entity,cn.yumis.fight.modules.combat.api.CombatTarget target,boolean triggers)throws Exception{
                    if(!(entity instanceof cn.yumis.fight.modules.dungeons.api.RitualBossEntity||entity instanceof cn.yumis.fight.modules.dungeons.api.RitualMechanismEntity))loot.death(p,entity,(Target)target,triggers);
                }
                public void publish(){loot.publish();}
                public void abort(){loot.abort();}
            },nativeHits,(p,target,fraction,time)->damageMeter.record(p,(Target)target,fraction,time),value->bridge=value,this::message);
        careers=new cn.yumis.fight.modules.skills.application.CareerSkillService(journal,server,this::session,p->session(p).progress,(p,id)->{var team=partyService.team(p.getUUID());return team!=null&&team.members.contains(id);});
        mages=new cn.yumis.fight.modules.skills.application.MageSkillService(server,journal,this::session,p->session(p).progress,targets::resolve,visuals::receive,p->dungeons.current(p.getUUID())==null?session(p).progress.sync:dungeons.current(p.getUUID()).syncFor(p.getUUID()));
        progressService.transitions(new cn.yumis.fight.modules.character.api.JobTransitions(){
            private Change clean(ServerPlayer player,Change change){return new Change(change.writes(),()->{change.committed().run();mages.clear(player);});}
            public Change prepare(ServerPlayer player,Progress next,String destination){return clean(player,careers.prepare(player,next,destination));}
            public Change clear(ServerPlayer player,Progress next){return clean(player,careers.clear(player,next));}
            public Change switchProfile(ServerPlayer player,Progress next){var change=careers.switchProfile(player,next);return new Change(change.writes(),()->{change.committed().run();mages.clear(player);visuals.clear(player);cn.yumis.fight.modules.equipment.api.EquipmentStats.invalidate(player);});}
            public Change reduceSkill(ServerPlayer player,Progress next,String id){var change=careers.reduceSkill(player,next,id);return new Change(change.writes(),()->{change.committed().run();});}
        });combatApplication.skills(cn.yumis.fight.modules.combat.api.CombatPorts.combine(careers,mages,dungeons.treasure,rituals,dungeons.elaina));
        combatApplication.equipment(this::profile);
        registerCommands();
        combatApplication.training(trainingService);
        loadDefinitions();
        discardTransientRewards();
        loot.recover();
    }
    public static void start(MinecraftServer server){
        try{
            CURRENT=new ServerApplication(server);
            RuntimeAssembly.installRitualPorts(CURRENT.rituals);
            cn.yumis.fight.modules.economy.api.LootEntityActions.install(CURRENT.loot::validate,CURRENT.loot::pickup);
            cn.yumis.fight.modules.economy.api.WorldItemTransfers.install(CURRENT.loot);
            cn.yumis.fight.modules.dungeons.api.CageActions.install(CURRENT.dungeons::cageHit);
            cn.yumis.fight.modules.dungeons.api.TrainingActions.install(CURRENT.trainingService);
            cn.yumis.fight.modules.dungeons.api.TreasureActions.install(CURRENT.dungeons.treasure::use);
            cn.yumis.fight.modules.dungeons.api.WaystoneActions.install(CURRENT.dungeons.waystones);
            cn.yumis.fight.modules.dungeons.api.KarlSummoningAltarBlockEntity.install(CURRENT.inventory);
            cn.yumis.fight.modules.professions.platform.AlchemyCauldronBlockEntity.installInventory(CURRENT.inventory);
            cn.yumis.fight.modules.dungeons.api.AwakeningTowerStele.install(CURRENT.dungeons::interactTowerStele);
            cn.yumis.fight.modules.skills.api.SummonActions.install(CURRENT.mages);
            cn.yumis.fight.modules.equipment.api.EnhanceMenu.ON_OPEN=CURRENT.equipmentApplication::sendBagSnapshot;
            cn.yumis.fight.modules.equipment.api.FabricatorMenu.ON_OPEN=CURRENT.equipmentApplication::sendBagSnapshot;
            Network.SERVER=CURRENT::handle;
            var runtime=CURRENT;cn.yumis.fight.modules.combat.api.NativeEffects.configure(entity->!runtime.bridge&&cn.yumis.fight.modules.combat.api.ManagedEntities.includes(entity));
        }
        catch(Exception e){
            throw new IllegalStateException("无法打开世界状态日志",e);
        }
        EquipmentEligibility.PROGRESS=p->p instanceof ServerPlayer sp?get().session(sp).progress:EquipmentEligibility.CLIENT_PROGRESS.apply(p);
        EquipmentEligibility.LEVEL_ALLOWANCE=p->p instanceof ServerPlayer sp?(get().equipmentContracts.contractUntil(sp.getUUID())>System.currentTimeMillis()?5:0):EquipmentEligibility.CLIENT_ALLOWANCE.applyAsInt(p);
    }
    public static boolean ready(){
        return CURRENT!=null;
    }
    public static ServerApplication get(){
        if(CURRENT==null)throw new IllegalStateException("服务未就绪");
        return CURRENT;
    }
    public static void stop(){
        if(CURRENT==null)return;
        try{
            for(ServerPlayer p:CURRENT.server.getPlayerList().getPlayers()){if(p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.RewardMenu menu){menu.forfeit();CURRENT.inventory.closeCursor(p);}cn.yumis.fight.modules.inventory.api.RewardMenu.discardPending(p);CURRENT.mages.clear(p);CURRENT.savePlayer(p);}
            CURRENT.arkGuard.close();
            CURRENT.dungeons.shutdown();
            CURRENT.rituals.shutdown();
            CURRENT.discardTransientRewards();
            CURRENT.journal.checkpoint();
            CURRENT.journal.close();
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
        finally{
            cn.yumis.fight.modules.industry.application.BuddyGeneratorCapture.stop();
            cn.yumis.fight.modules.industry.api.IndustrialPorts.clear();
            RuntimeAssembly.clearRitualPorts();
            cn.yumis.fight.modules.npc.api.NpcSpawns.install(null);
            cn.yumis.fight.modules.npc.api.NpcSpawns.relocator(null);
            cn.yumis.fight.modules.npc.api.NpcSpawns.starter(level->{});
            cn.yumis.fight.modules.npc.api.NpcEntity.conversations(entity->false);
            cn.yumis.fight.modules.npc.api.NpcEntity.staleUnique(entity->false);
            cn.yumis.fight.modules.combat.api.NativeEffects.clear();
            cn.yumis.fight.modules.economy.api.LootEntityActions.clear();
            cn.yumis.fight.modules.economy.api.WorldItemTransfers.clear();
            cn.yumis.fight.modules.dungeons.api.CageActions.clear();
            cn.yumis.fight.modules.dungeons.api.TrainingActions.clear();
            cn.yumis.fight.modules.dungeons.api.TreasureActions.install((p,h)->{});
            cn.yumis.fight.modules.dungeons.api.WaystoneActions.install(null);
            cn.yumis.fight.modules.dungeons.api.KarlSummoningAltarBlockEntity.install(null);
            cn.yumis.fight.modules.professions.platform.AlchemyCauldronBlockEntity.installInventory(null);
            cn.yumis.fight.modules.dungeons.api.AwakeningTowerStele.install(null);
            cn.yumis.fight.modules.skills.api.SummonActions.clear();
            cn.yumis.fight.modules.equipment.api.EnhanceMenu.ON_OPEN=p->{};
            cn.yumis.fight.modules.equipment.api.FabricatorMenu.ON_OPEN=p->{};
            CURRENT=null;
            Network.SERVER=(p,a,j)->{throw new IllegalStateException("服务未就绪");};
        }
    }
    public static void registerEvents(){
        cn.yumis.fight.modules.industry.application.ConveyorConstructionService.registerGuards(()->ready()?get().conveyorConstruction:null);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event)->IndustrialVerification.register(event));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.RegisterCommandsEvent event)->IndustrialVisualVerification.register(event));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().login(p);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().logout(p);
        }
        );
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p)get().respawn(p);
        }
        );
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(net.neoforged.neoforge.event.entity.living.LivingDeathEvent event)->{
            if(ready()&&event.getEntity() instanceof ServerPlayer player)get().jars.interrupt(player);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof LivingEntity target&&cn.yumis.fight.modules.combat.api.ManagedEntities.attackable(target)){
                get().context(p,target);if(target instanceof ServerPlayer caster)get().visuals.track(p,caster);
            }
        }
        );
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e)->{
            if(ready())get().tick();
        }
        );
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent e)->{
            if(ready())get().nativeHits.incoming(e);
        }
        );
        NeoForge.EVENT_BUS.addListener((LivingDamageEvent.Pre e)->{
            if(ready())get().nativeHits.pre(e);
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingHealEvent event)->{if(ready())get().worldHealth.nativeHeal(event);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p){
                var runtime=get();var s=runtime.session(p);try{
                    runtime.jars.interrupt(p);
                    if(!s.life.instance.isEmpty()&&p.level().dimension()!=DungeonService.DIMENSION&&!runtime.recoveringDefeat.contains(p.getUUID()))runtime.dungeons.leave(p,false);
                    s.life.dimension=p.level().dimension().identifier().toString();s.life.revision++;
                    if(!runtime.recoveringDefeat.contains(p.getUUID())){runtime.savePlayer(p);runtime.profile(p);}
                }
                catch(Exception failure){
                    runtime.message(p,failure.getMessage());
                }
            }
        }
        );
        ServerEvents.register();
    }
    public void loadDefinitions(){
        try{
            var nextIndustrial=cn.yumis.fight.modules.industry.platform.IndustrialWorld.readRules(server);
            MaterialCatalog nextCatalog=MaterialCatalog.current();
            DungeonCatalog nextDungeons=DungeonCatalog.current();
            AttributeNames nextNames=AttributeNames.current();
            AffixDefinitions nextAffixes=AffixDefinitions.parse(JSON.toJson(AffixDefinitions.current()));
            EquipmentDefinitions nextDefinitions=EquipmentDefinitions.parse(JSON.toJson(EquipmentDefinitions.current()));
            var resource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/catalog.json"));
            if(resource.isPresent())try(var reader=resource.get().openAsReader()){
                String data=JsonParser.parseReader(reader).toString();
                nextCatalog=MaterialCatalog.parse(data);nextDungeons=DungeonCatalog.parse(data);nextNames=AttributeNames.parse(data);
                nextCatalog.revision=MaterialCatalog.current().revision+1;nextDungeons.revision=nextCatalog.revision;
            }
            var equipment=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/equipment.json"));
            if(equipment.isPresent())try(var reader=equipment.get().openAsReader()){
                String data=JsonParser.parseReader(reader).toString();
                nextDefinitions=EquipmentDefinitions.parse(data);nextAffixes=AffixDefinitions.parse(data);
            }
            EquipmentDefinitions previousDefinitions=EquipmentDefinitions.defaults();
            JsonElement archived=journal.get("definitions/equipment");
            if(!archived.isJsonNull())previousDefinitions=EquipmentDefinitions.parse(archived.toString());
            nextDefinitions.retainRetiredFrom(previousDefinitions);
            JsonElement affixArchive=journal.get("definitions/affixes");
            // This save generation reads only module-owned records.
            var previousAffixes=!affixArchive.isJsonNull()?AffixDefinitions.parse(affixArchive.toString()):AffixDefinitions.defaults();
            nextAffixes.retainRetiredFrom(previousAffixes);
            EquipmentLayout nextLayout=EquipmentLayout.load(server.getResourceManager());
            Path config=net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve(ItemData.NS+".toml");
            if(!Files.exists(config))Files.writeString(config,"# 大世界难度：NORMAL / ADVANCED / WARRIOR / KING / HELL\nworldDifficulty=\"NORMAL\"\n# 只影响战斗经验；不影响副职业和管理员奖励\ncombatExperienceMultiplier="+RewardSettings.DEFAULT.combatExperienceMultiplier()+"\n# 只影响杀怪金币；不影响出售价格\nkillGoldMultiplier=1.0\n");
            String text=Files.readString(config);
            var match=java.util.regex.Pattern.compile("worldDifficulty\\s*=\\s*\"(\\w+)\"").matcher(text);
            Difficulty nextDifficulty=match.find()?Difficulty.valueOf(match.group(1)):worldDifficulty;
            RewardSettings nextRewards=RewardSettings.parse(text);
            var nextCombat=cn.yumis.fight.modules.combat.api.CombatSettings.parse(text);
            String additions="";
            if(!java.util.regex.Pattern.compile("(?m)^\\s*combatTimeoutSeconds\\s*=").matcher(text).find())additions+="\n# 连续未造成或受到有效伤害的脱战秒数\ncombatTimeoutSeconds=5\n";
            if(!java.util.regex.Pattern.compile("(?m)^\\s*combatExperienceMultiplier\\s*=").matcher(text).find())additions+="\n# 战斗经验倍率；不影响副职业或管理员经验\ncombatExperienceMultiplier="+RewardSettings.DEFAULT.combatExperienceMultiplier()+"\n";
            if(!java.util.regex.Pattern.compile("(?m)^\\s*killGoldMultiplier\\s*=").matcher(text).find())additions+="\n# 仅杀怪金币倍率；出售价格不变\nkillGoldMultiplier=1.0\n";
            if(!additions.isEmpty())Files.writeString(config,additions,StandardOpenOption.APPEND);
            var nextSkills=cn.yumis.fight.modules.skills.api.SkillBalanceRules.defaults();
            var nextChests=cn.yumis.fight.modules.economy.api.ChestDefinitions.defaults();
            var chestResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/chests.json"));
            if(chestResource.isPresent())try(var reader=chestResource.get().openAsReader()){nextChests=cn.yumis.fight.modules.economy.api.ChestDefinitions.parse(JsonParser.parseReader(reader).toString());}
            var nextJars=JarDefinitions.defaults();
            var jarResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/jars.json"));
            if(jarResource.isPresent())try(var reader=jarResource.get().openAsReader()){nextJars=JarDefinitions.parse(JsonParser.parseReader(reader).toString());}
            var nextShops=cn.yumis.fight.modules.economy.api.ShopDefinitions.defaults();var nextNpcs=cn.yumis.fight.modules.npc.api.NpcDefinitions.defaults();
            var shopResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/shops.json"));
            if(shopResource.isPresent())try(var reader=shopResource.get().openAsReader()){nextShops=cn.yumis.fight.modules.economy.api.ShopDefinitions.parse(JsonParser.parseReader(reader).toString());}
            var npcResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/npcs.json"));
            if(npcResource.isPresent())try(var reader=npcResource.get().openAsReader()){nextNpcs=cn.yumis.fight.modules.npc.api.NpcDefinitions.parse(JsonParser.parseReader(reader).toString());}
            var nextQuests=cn.yumis.fight.modules.quests.api.QuestDefinitions.defaults();
            var questResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/quests.json"));
            if(questResource.isPresent())try(var reader=questResource.get().openAsReader()){nextQuests=cn.yumis.fight.modules.quests.api.QuestDefinitions.parse(JsonParser.parseReader(reader).toString());}
            var nextArk=cn.yumis.fight.modules.arkguard.api.ArkRules.defaults();
            var arkResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/ark_war.json"));
            if(arkResource.isPresent())try(var reader=arkResource.get().openAsReader()){nextArk=cn.yumis.fight.modules.arkguard.api.ArkRules.parse(JsonParser.parseReader(reader).toString());}
            var nextRitualBosses=cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.defaults();
            var ritualResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/balance/bosses/altar_bosses.json"));
            if(ritualResource.isPresent())try(var reader=ritualResource.get().openAsReader()){nextRitualBosses=cn.yumis.fight.modules.dungeons.api.RitualBossCatalog.parse(JsonParser.parseReader(reader).toString());}
            var nextTalents=cn.yumis.fight.modules.talents.api.TalentCatalog.defaults();
            var talentResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/talents.json"));
            if(talentResource.isPresent())try(var reader=talentResource.get().openAsReader()){nextTalents=cn.yumis.fight.modules.talents.api.TalentCatalog.parse(JsonParser.parseReader(reader).toString());}
            var nextElementalistMorphs=cn.yumis.fight.modules.skills.api.ElementalistMorphs.defaults();
            var elementalistResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/elementalist.json"));
            if(elementalistResource.isPresent())try(var reader=elementalistResource.get().openAsReader()){nextElementalistMorphs=cn.yumis.fight.modules.skills.api.ElementalistMorphs.parse(JsonParser.parseReader(reader).toString());}
            var nextBattleMageMorphs=cn.yumis.fight.modules.skills.api.BattleMageMorphs.defaults();
            var battleMageResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/battle_mage.json"));
            if(battleMageResource.isPresent())try(var reader=battleMageResource.get().openAsReader()){nextBattleMageMorphs=cn.yumis.fight.modules.skills.api.BattleMageMorphs.parse(JsonParser.parseReader(reader).toString());}
            var nextCombatBalance=cn.yumis.fight.modules.combat.api.CombatBalanceRules.defaults();
            var nextCharacterBalance=cn.yumis.fight.modules.character.api.CharacterBalanceRules.defaults();
            var nextEquipmentBalance=cn.yumis.fight.modules.equipment.api.EquipmentBalanceRules.defaults();
            var nextTalentBalance=cn.yumis.fight.modules.talents.api.TalentBalanceRules.defaults();
            var nextEconomyBalance=cn.yumis.fight.modules.economy.api.EconomyBalanceRules.defaults();
            var nextEnemyBalance=cn.yumis.fight.modules.dungeons.api.EnemyBalanceRules.defaults();
            var nextBossBalance=cn.yumis.fight.modules.dungeons.api.BossBalanceRules.defaults();
            var candidate=cn.yumis.fight.bootstrap.ApplicationRules.Bundle.validated(nextCatalog,nextDungeons,nextNames,nextDefinitions,nextAffixes,nextLayout,nextIndustrial,nextSkills,nextChests,nextJars,nextShops,nextNpcs,nextQuests,nextArk,nextRitualBosses,nextTalents,nextElementalistMorphs,nextBattleMageMorphs,nextCombatBalance,nextCharacterBalance,nextEquipmentBalance,nextTalentBalance,nextEconomyBalance,nextEnemyBalance,nextBossBalance);
            journal.transaction(Map.ofEntries(Map.entry("definitions/equipment",JSON.toJsonTree(candidate.equipment())),Map.entry("definitions/affixes",JSON.toJsonTree(candidate.affixes())),Map.entry("definitions/skills",JSON.toJsonTree(candidate.skills())),Map.entry("definitions/chests",JSON.toJsonTree(candidate.chests())),Map.entry("definitions/jars",JSON.toJsonTree(candidate.jars())),Map.entry("definitions/shops",JSON.toJsonTree(candidate.shops())),Map.entry("definitions/npcs",JSON.toJsonTree(candidate.npcs())),Map.entry("definitions/quests",JSON.toJsonTree(candidate.quests())),Map.entry("definitions/ark_war",JSON.toJsonTree(candidate.arkWar())),Map.entry("definitions/ritual_bosses",JSON.toJsonTree(candidate.ritualBosses())),Map.entry("definitions/talents",JSON.toJsonTree(candidate.talents())),Map.entry("definitions/elementalist",JSON.toJsonTree(candidate.elementalistMorphs())),Map.entry("definitions/battle_mage",JSON.toJsonTree(candidate.battleMageMorphs()))));
            cn.yumis.fight.bootstrap.ApplicationRules.publish(candidate);
            worldDifficulty=nextDifficulty;
            rewards=nextRewards;
            combatSettings=nextCombat;
        }
        catch(Exception e){
            server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("地下城定义重载失败，保留有效定义："+e.getMessage()));
            return;
        }
        cn.yumis.fight.modules.industry.platform.IndustrialWorld.rulesPublished(server);
        for(ServerPlayer player:server.getPlayerList().getPlayers()){
            try{this.equipmentLayout.reconcile(player,this.equipmentLayout.bonus(player));profile(player);}
            catch(Exception e){server.sendSystemMessage(ModText.of("规则已重载，玩家界面刷新失败："+player.getScoreboardName()+"："+e.getMessage()));}
        }
    }
    public Session session(ServerPlayer p){
        return sessions.computeIfAbsent(p.getUUID(),id->{
            if(loggingOut.contains(id))throw new IllegalStateException("登出时没有已建立的会话");
            var opened=characters.open(id);
            cn.yumis.fight.modules.combat.api.Runtime r=opened.fresh()?new cn.yumis.fight.modules.combat.api.Runtime():journal.get("resources/"+id,cn.yumis.fight.modules.combat.api.Runtime.class,new cn.yumis.fight.modules.combat.api.Runtime());
            Life l=opened.fresh()?new Life():journal.get("life/"+id,Life.class,new Life());l.target=id;l.dimension=p.level().dimension().identifier().toString();
            if(opened.fresh())try{var writes=new java.util.LinkedHashMap<>(characters.writes(id,opened.progress(),opened.profiles()));writes.put("resources/"+id,JSON.toJsonTree(r));writes.put("life/"+id,JSON.toJsonTree(l));journal.transaction(writes);}catch(Exception failure){throw new IllegalStateException("新角色初始化失败",failure);}
            var result=new Session(opened.progress(),r,l);result.viewStats=journal.get("playerStats/"+id,Stats.class,new Stats());return result;
        }
        );
    }
    public void touchAction(ServerPlayer p){
        if(p==null||loggingOut.contains(p.getUUID()))return;
        Session s=sessions.get(p.getUUID());
        if(s!=null)s.lastActionAt=System.currentTimeMillis();
    }
    public long idleMillis(ServerPlayer p){
        if(p==null)return 0L;
        Session s=sessions.get(p.getUUID());
        return s==null?0L:Math.max(0L,System.currentTimeMillis()-s.lastActionAt);
    }
    public void login(ServerPlayer p){
        personalSequences.remove(p.getUUID());
        damageMeter.forget(p);
        try{
            inventory.restore(p);
            this.equipmentLayout.reconcile(p,this.equipmentLayout.bonus(p));
            Session s=session(p);
            s.lastActionAt=System.currentTimeMillis();
            s.positionInitialized=false;
            if(!dungeons.resumeTower(p)){
                s.life.dungeonDefeated=false;
                dungeons.enterWorld(p,!arkGuard.canResumeInPlace(p));
            }
            if(s.life.dead&&!s.life.dungeonDefeated&&!p.isDeadOrDying()){
                bridge=true;
                try{
                    p.hurtServer(p.level(),p.damageSources().genericKill(),Float.MAX_VALUE);
                }
                finally{
                    bridge=false;
                }
            }
            jars.recoverLogin(p);
            conveyorConstruction.deliverRefunds(p);
            savePlayer(p);
            profile(p);
            dungeons.waystones.login(p);
            loot.deliverPendingExperience(p);
        }
        catch(Exception e){
            p.connection.disconnect(cn.yumis.fight.modules.presentation.api.ModText.of("状态恢复失败："+e.getMessage()));
        }
    }
    public void logout(ServerPlayer p){
        UUID id=p.getUUID();
        if(!loggingOut.add(id))return;
        try{
            logoutStep(p,"清理方舟状态",()->arkGuard.logout(p));
            if(p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.RewardMenu menu){
                logoutStep(p,"关闭奖励界面",menu::forfeit);
            }
            cn.yumis.fight.modules.inventory.api.RewardMenu.discardPending(p);
            logoutStep(p,"清理练功状态",()->trainingService.logout(p));
            logoutStep(p,"清理召唤物",()->mages.logout(p));
            careers.clear(p);

            Session active=sessions.get(id);
            if(active==null)return;
            logoutStep(p,"保底结算未完成的开罐结果",()->jars.interrupt(p));
            logoutStep(p,"收回背包临时物品",()->{
                inventory.closeCursor(p);
                // doCloseContainer invokes the inventory checkpoint after the native close.
                if(p.containerMenu!=p.inventoryMenu&&p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.CheckpointMenu)p.doCloseContainer();
            });
            logoutStep(p,"保存玩家",()->{
                active.resources.equipmentBuffs.clear();
                savePlayer(p);
            });
            logoutStep(p,"结束掉落会话",()->loot.endSession(p));
            logoutStep(p,"清理引路台报名和未结算奖励",()->dungeons.waystones.logout(p));
            logoutStep(p,"退出副本",()->dungeons.logout(p,!arkGuard.canResumeInPlace(p)));
            logoutStep(p,"退出魔王祭坛",()->rituals.logout(p));
            logoutStep(p,"清理会话收据",()->cleanupLogoutReceipts(id));
        }
        finally{
            viewSenders.remove(id);
            personalSequences.remove(id);
            economyDrafts.forget(id);
            economyService.forget(id);
            sessions.remove(id);
            loggingOut.remove(id);
        }
    }
    private void cleanupLogoutReceipts(UUID id)throws java.io.IOException{
        var discarded=new LinkedHashMap<String,JsonElement>();
        for(String key:journal.keys("receipt/"+id+"/"))discarded.put(key,JsonNull.INSTANCE);
        for(String key:journal.keys("inventoryReceipt/"+id+"/"))discarded.put(key,JsonNull.INSTANCE);
        if(!discarded.isEmpty())journal.transaction(discarded);
    }
    private void discardTransientRewards()throws java.io.IOException{
        var discarded=new LinkedHashMap<String,JsonElement>();
        for(String namespace:List.of("personalKill/","waystonePersonal/","receipt/","inventoryReceipt/","industrialReceipt/")){
            for(String key:journal.keys(namespace))discarded.put(key,JsonNull.INSTANCE);
        }
        if(!discarded.isEmpty())journal.transaction(discarded);
    }
    @FunctionalInterface
    private interface LogoutStep {
        void run()throws Exception;
    }
    private void logoutStep(ServerPlayer p,String description,LogoutStep step){
        try{step.run();}
        catch(Exception failure){
            LOG.warn("玩家 {} 登出时{}失败",p.getScoreboardName(),description,failure);
        }
    }
    public void respawn(ServerPlayer p){
        inventory.restore(p);
        resetLife(p);
        dungeons.respawn(p);
        savePlayer(p);
        profile(p);
    }
    private void recoverDungeonDefeat(ServerPlayer p,JsonObject choice)throws Exception{
        var session=session(p);
        var life=session.life;
        if(!session.id.toString().equals(choice.get("session").getAsString()))return;
        if(!life.dungeonDefeated||life.generation!=choice.get("generation").getAsLong()){
            profile(p);
            return;
        }
        String kind=choice.get("kind").getAsString();
        if(kind.equals("return")){
            if(!dungeons.allowDefeatReturn(p)){profile(p);return;}
            dungeons.returnToStart(p);
        }else if(!kind.equals("leave")){
            throw new IllegalArgumentException("未知副本复起选项");
        }
        var previousLife=session.life;
        var previousResources=session.resources;
        session.life=JSON.fromJson(JSON.toJsonTree(previousLife),Life.class);
        session.resources=JSON.fromJson(JSON.toJsonTree(previousResources),cn.yumis.fight.modules.combat.api.Runtime.class);
        recoveringDefeat.add(p.getUUID());
        try{
            resetLife(p);
            if(kind.equals("leave"))dungeons.leave(p,true);
            session.life.dimension=p.level().dimension().identifier().toString();
            savePlayer(p);
        }catch(RuntimeException|Error failure){
            session.life=previousLife;
            session.resources=previousResources;
            throw failure;
        }finally{
            recoveringDefeat.remove(p.getUUID());
        }
        p.stopRiding();
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        p.resetFallDistance();
        p.clearFire();
        p.removeAllEffects();
        bridge=true;
        try{p.setHealth(p.getMaxHealth());}finally{bridge=false;}
        profile(p);
    }
    private void resetLife(ServerPlayer p){
        mages.clear(p);
        careers.clear(p);
        Session s=session(p);
        jars.interrupt(p);
        s.life.generation++;
        s.life.revision=0;
        s.life.q=1;
        s.life.dead=false;
        s.life.dungeonDefeated=false;
        s.life.elemental=new cn.yumis.fight.modules.combat.api.ElementAccumulation.State();
        s.life.dimension=p.level().dimension().identifier().toString();
        s.resources.mp=-1;
        if(s.resources.training)s.resources.trainingBoundary=s.resources.mp;
        s.resources.shield=-1;
        s.resources.combatUntil=0;
        s.resources.shieldRecoveryAt=0;
        s.resources.buffs.clear();
        s.resources.equipmentBuffs.clear();
        s.resources.absorption.clear();
        s.resources.absorptionUntil.clear();
        p.getFoodData().setFoodLevel(20);
    }
    public void savePlayer(ServerPlayer p){
        try{
            Session s=session(p);
            journal.begin();
            for(var write:characters.writes(p.getUUID(),s.progress).entrySet())journal.put(write.getKey(),write.getValue());
            journal.put("resources/"+p.getUUID(),s.resources);
            journal.put("life/"+p.getUUID(),s.life);journal.put("playerStats/"+p.getUUID(),s.viewStats);
            journal.finish();
            s.statsDirty=false;
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public Stats stats(ServerPlayer p){
        if(dungeons!=null&&dungeons.waystones!=null)dungeons.waystones.updateSources(p);
        return session(p).viewStats.copy();
    }
    private void syncVanillaHealth(ServerPlayer p,Session s,Stats effective){
        var attribute=p.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null)attribute.setBaseValue(Math.max(1,effective.get(Stat.HEALTH)));
        bridge=true;
        try{
            if(!s.life.dead)p.setHealth((float)Math.max(.001,s.life.q*p.getMaxHealth()));
        }
        finally{
            bridge=false;
        }
    }
    public void profile(ServerPlayer p){
        if(loggingOut.contains(p.getUUID()))return;
        Session s=session(p);
        Stats stats=stats(p);
        s.sourceRevision=cn.yumis.fight.modules.attributes.api.AttributeServices.attributes(p.getUUID()).revision();
        syncVanillaHealth(p,s,stats);
        JsonObject j=new JsonObject();
        j.addProperty("personalSequence",personalSequences.getOrDefault(p.getUUID(),0L));
        j.addProperty("protocol",cn.yumis.fight.architecture.ProtocolVersion.CURRENT);
        j.addProperty("rules",cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION);
        j.addProperty("session",s.id.toString());
        j.addProperty("serverNow",System.currentTimeMillis());
        j.addProperty("gold",economyService.gold(p));j.addProperty("goldRevision",economyService.goldRevision(p));
        j.addProperty("combatExperienceMultiplier",rewards.combatExperienceMultiplier());
        j.addProperty("contractUntil",equipmentContracts.contractUntil(p.getUUID()));
        j.addProperty("points",economyService.points(p));
        j.addProperty("colorlessCrystals",economyService.colorlessCrystals(p));
        j.add("professionEffects",professionService.effects(p.getUUID()));
        j.add("npcTutorial",JSON.toJsonTree(tutorials.state(p.getUUID())));
        j.add("awakeningQuest",awakeningQuests.view(p));
        j.add("promotionQuest",promotionQuests.view(p));
        j.add("commission",commissions.view(p));
        j.add("professionState",JSON.toJsonTree(professionService.snapshot(p.getUUID())));
        j.add("progress",JSON.toJsonTree(s.progress));
        j.add("careerProfiles",characters.profiles(p.getUUID()).view(s.progress));
        j.add("resources",JSON.toJsonTree(s.resources));
        j.add("life",JSON.toJsonTree(s.life));
        j.add("stats",JSON.toJsonTree(stats));
        var sources=cn.yumis.fight.modules.attributes.api.AttributeServices.attributes(p.getUUID());
        j.addProperty("attributeRevision",sources.revision());
        j.add("attributeSources",JSON.toJsonTree(sources.snapshot()));
        j.add("party",JSON.toJsonTree(partyService.team(p.getUUID())));
        var dungeon=dungeons.current(p.getUUID());
        j.add("dungeon",JSON.toJsonTree(dungeon));
        j.addProperty("dungeonAbyss",dungeons.pendingAbyss(dungeon));
        j.add("towerCards",dungeons.towerCards(dungeon,p.getUUID()));
        j.add("towerBattle",dungeons.towerBattle(p.getUUID()));
        j.addProperty("equipmentState",inventory.clientStorageState(p));
        j.addProperty("craftAllowed",cn.yumis.fight.platform.Permissions.allowed(p.getUUID(),"craft"));
        j.add("equipmentGroups",JSON.toJsonTree(MenuHooks.extra(p).groups));
        JsonObject definition=definitions();
        j.add("combatSettings",JSON.toJsonTree(combatSettings));
        j.add("partyInvitations",JSON.toJsonTree(partyService.incoming(p.getUUID())));
        viewSenders.computeIfAbsent(p.getUUID(),id->new ProfileSync.Sender()).publish(s.id.toString(),definitionsKey,definition,j,(action,packet)->Network.send(p,action,packet));
        careers.sync(p);
        damageMeter.send(p,dungeons.current(p.getUUID()));
    }
    public void message(ServerPlayer p,String message){
        Network.send(p,"message",Map.of("text",message==null?"操作失败":message));
    }
    public void context(ServerPlayer p,LivingEntity e){
        if(e instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag bag&&bag.timed()&&!bag.ownedBy(p))return;
        Target t=targets.resolve(e);
        Network.send(p,"context",t);
        for(String key:journal.keys("ailments/"+t.id+"/")){
            JsonElement ailments=journal.get(key);
            if(!ailments.isJsonNull())Network.send(p,"ailments",ailments);
        }
    }
    private final Map<UUID,Long> personalSequences=new HashMap<>();
    private static final Set<String> PASSIVE_ACTIONS=Set.of("hello","profile","playerStats","runtime","context","npcDialogue");
    public void handle(ServerPlayer p,String action,JsonObject j){
        if(!PASSIVE_ACTIONS.contains(action))touchAction(p);
        String personalSession=null;
        long sequence=0;
        boolean received=false;
        try{
            String activeSession=session(p).id.toString();
            if(j.has("clientTransaction")){
                if(!activeSession.equals(j.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");
                sequence=j.get("clientTransaction").getAsLong();
                personalSession=activeSession;
            }
            if(!action.equals("progress")&&!action.equals("profile")&&!action.equals("playerStats")&&j.has("profileId")){
                try{characters.profiles(p.getUUID()).requireCurrent(j);}
                catch(IllegalArgumentException stale){if(personalSession==null)profile(p);throw stale;}
            }
            if(!commands.dispatch(p,action,j))throw new IllegalArgumentException("未知操作："+action);
            received=true;
        }
        catch(Exception e){message(p,e.getMessage());}
        finally{
            if(personalSession!=null){
                personalSequences.merge(p.getUUID(),sequence,Math::max);
                try{profile(p);}
                catch(Exception failure){LOG.warn("处理个人事务后刷新档案失败：{}",p.getScoreboardName(),failure);}
                boolean committed=received;
                if(!committed&&j.has("operation")){
                    var receipt=journal.get("inventoryReceipt/"+p.getUUID()+"/"+j.get("operation").getAsString());
                    committed=receipt.isJsonObject()&&j.equals(receipt.getAsJsonObject().get("intent"));
                }
                Network.send(p,"personalCommit",Map.of("session",personalSession,"sequence",sequence,"received",committed));
            }
        }
    }
    private void registerCommands(){
        conveyorConstruction.register(commands);
        cn.yumis.fight.modules.mining.api.VeinMining.register(commands);
        cn.yumis.fight.modules.industry.platform.ManaStorageBagItem.register(commands,p->session(p).id.toString());
        commands.register("industry","industrialReady",(p,j)->IndustrialWorld.get(p.level()).opening.ready(p,j));
        commands.register("industry","industrialRoute",(p,j)->{if(p.containerMenu instanceof cn.yumis.fight.modules.industry.platform.IndustrialMenu menu)menu.routeAction(p,j);});
        commands.register("industry","prospectorPan",(p,j)->{if(p.containerMenu instanceof cn.yumis.fight.modules.industry.platform.ProspectorMenu menu&&menu.containerId==j.get("menu").getAsInt()&&menu.stillValid(p))menu.pan(p,j.get("x").getAsInt(),j.get("z").getAsInt());});
        careers.register(commands);mages.register(commands);
        awakeningQuests.register(commands);
        promotionQuests.register(commands);
        commissions.register(commands);
        commands.register("dungeons","ritualSummon",rituals::summonCommand);
        commands.register("dungeons","ritualJoin",rituals::joinCommand);
        commands.register("equipment","playerStats",(p,j)->{
            Session s=session(p);
            if(!s.id.toString().equals(j.get("session").getAsString()))return;
            s.viewStats=JSON.fromJson(j.get("stats"),Stats.class);
            s.statsDirty=true;
            syncVanillaHealth(p,s,stats(p));
        });
        equipmentApplication.register(commands);economyService.register(commands);jars.register(commands);shops.register(commands);npcs.register(commands);tutorials.register(commands);chests.register(commands);progressService.register(commands);inventoryApplication.register(commands);
        commands.register("presentation","hello",(p,j)->{if(j.get("protocol").getAsInt()!=cn.yumis.fight.architecture.ProtocolVersion.CURRENT||j.has("rules")&&j.get("rules").getAsInt()!=cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION){p.connection.disconnect(ModText.of("模组协议或规则版本不一致"));return;}profile(p);});
        commands.register("presentation","profile",(p,j)->{var sender=viewSenders.get(p.getUUID());if(sender!=null){if(j.has("definitions")&&j.get("definitions").getAsBoolean())sender.resetDefinitions();else sender.resetPersonal();}profile(p);});
        commands.register("combat","context",(p,j)->{Entity e=p.level().getEntity(j.get("entity").getAsInt());if(e instanceof LivingEntity l&&cn.yumis.fight.modules.combat.api.ManagedEntities.attackable(l))context(p,l);});
        commands.register("combat","runtime",(p,j)->{
            var state=session(p);long sequence=j.get("checkpoint").getAsLong();
            if(state.runtimeGeneration!=state.life.generation||state.runtimeEpoch!=state.resources.controlEpoch){state.runtimeSequence=-1;state.runtimeGeneration=state.life.generation;state.runtimeEpoch=state.resources.controlEpoch;}
            boolean accepted=state.id.toString().equals(j.get("session").getAsString())&&state.life.generation==j.get("generation").getAsLong()&&state.resources.controlEpoch==j.getAsJsonObject("state").get("controlEpoch").getAsLong()&&(sequence<=state.runtimeSequence||combatApplication.runtime(p,j));
            if(accepted)state.runtimeSequence=Math.max(state.runtimeSequence,sequence);
            Network.send(p,"runtimeAck",Map.of("session",state.id.toString(),"generation",state.life.generation,"checkpoint",sequence,"accepted",accepted,"epoch",state.resources.controlEpoch,"recordedAt",state.resources.recordedAt,"serverNow",System.currentTimeMillis()));
            if(!accepted&&(state.life.generation!=j.get("generation").getAsLong()||state.resources.controlEpoch!=j.getAsJsonObject("state").get("controlEpoch").getAsLong())){var sender=viewSenders.get(p.getUUID());if(sender!=null)sender.resetPersonal();profile(p);}
        });
        for(String action:List.of("damage","heal","nativeOutcome"))commands.register("combat",action,(p,j)->{JsonObject report=j.deepCopy();combatQueue.add(()->combatApplication.report(p,action,report));});
        commands.register("professions","professions",(p,j)->professions(p));commands.register("party","party",partyService::request);
        commands.register("economy","economyDraft",(p,j)->{
            if(!session(p).id.toString().equals(j.get("session").getAsString()))throw new IllegalArgumentException("会话已失效");
            economyDrafts.part(p,j);
        });
        commands.register("economy","lootReward",loot::receiveReward);
        professionService.register(commands,inventory,this::profile,p->session(p).id);
        commands.register("dungeons","dungeon",dungeons::request);commands.register("dungeons","cageHit",dungeons::cageHit);
        commands.register("dungeons","towerClimb",dungeons::towerAction);
        commands.register("dungeons","dungeonDefeat",this::recoverDungeonDefeat);
        commands.register("dungeons","waystoneTable",dungeons.waystones::command);commands.register("dungeons","waystoneReturnResult",dungeons.waystones::receiveReward);
        commands.register("dungeons","trainingStart",trainingService::start);
        commands.register("arkguard","arkGuard",arkGuard::request);
        commands.register("skills","visual",visuals::receive);
        cn.yumis.fight.bootstrap.ApplicationModules.validateCommands(commands);
        commands.seal();
    }

    public void professions(ServerPlayer p){
        Network.send(p,"professions",Map.of("professions",professionService.snapshot(p.getUUID()),"gold",economyService.gold(p)));
    }

    public void refreshAll(){
        for(ServerPlayer p:server.getPlayerList().getPlayers())profile(p);
    }

    private void tick(){
        ticks++;
        conveyorConstruction.tick();
        dungeons.treasure.tick();
        dungeons.tickElaina();
        dungeons.tickLiangyue();
        dungeons.tickFrostnova();
        dungeons.tickGilgamesh();
        dungeons.tickSwordMasters();
        dungeons.tickRoxy();
        for(ServerPlayer p:server.getPlayerList().getPlayers())cn.yumis.fight.modules.inventory.api.RewardMenu.tick(p,inventory);
        for(ServerPlayer viewer:server.getPlayerList().getPlayers()){var sender=viewSenders.get(viewer.getUUID());if(sender!=null)sender.flush((action,packet)->Network.send(viewer,action,packet));}
        cn.yumis.fight.modules.industry.platform.IndustrialWorld.tick(server);
        IndustrialWorld.get(server.overworld()).opening.tick(server);
        mages.tick();
        int size=combatQueue.size();
        for(int i=0;i<size;i++)combatQueue.removeFirst().run();
        jars.tick(server.getPlayerList().getPlayers());
        trainingService.tick();
        arkGuard.tick();
        rituals.tick();
        if(ticks%5==0){
            careers.tick();
            dungeons.tick();
            loot.tick();
            combatApplication.expireAilments();
            worldHealth.tick(ticks);
            visuals.tick();
        }
        if(ticks%20==0){
            long now=System.currentTimeMillis();
            var dirtyStats=new LinkedHashMap<String,JsonElement>();
            var dirtySessions=new ArrayList<Session>();
            for(ServerPlayer p:server.getPlayerList().getPlayers()){
                Session s=session(p);
                double x=p.getX(),y=p.getY(),z=p.getZ();
                float yaw=p.getYRot(),pitch=p.getXRot();
                if(!s.positionInitialized){
                    s.lastX=x;s.lastY=y;s.lastZ=z;s.lastYaw=yaw;s.lastPitch=pitch;s.positionInitialized=true;
                }else{
                    double dx=x-s.lastX,dy=y-s.lastY,dz=z-s.lastZ;
                    if(dx*dx+dy*dy+dz*dz>.01||Math.abs(yaw-s.lastYaw)>1f||Math.abs(pitch-s.lastPitch)>1f){
                        s.lastX=x;s.lastY=y;s.lastZ=z;s.lastYaw=yaw;s.lastPitch=pitch;s.lastActionAt=now;
                    }
                }
                if(s.statsDirty){dirtyStats.put("playerStats/"+p.getUUID(),JSON.toJsonTree(s.viewStats));dirtySessions.add(s);}
                damageMeter.send(p,dungeons.current(p.getUUID()));
                if(inventory.isBag(p)&&!(p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu))inventory.bag(p,false);
                if(cn.yumis.fight.modules.attributes.api.AttributeServices.attributes(p.getUUID()).revision()!=s.sourceRevision)profile(p);
            }
            if(!dirtyStats.isEmpty()){
                try{
                    journal.transaction(dirtyStats);
                    for(Session s:dirtySessions)s.statsDirty=false;
                }catch(Exception failure){LOG.warn("批量落盘玩家属性快照失败",failure);}
            }
        }
        if(ticks%12000==0){
            try{
                if(Files.exists(journalPath)&&Files.size(journalPath)>16L*1024*1024)journal.checkpoint();
            }catch(Exception failure){LOG.warn("定期压缩 WAL 失败",failure);}
        }
        stunControl.tick(server,entity->targets.resolve(entity).life);
    }
}

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
import cn.yumis.fight.modules.dungeons.application.DungeonService;
import cn.yumis.fight.modules.dungeons.application.DamageMeterService;
import cn.yumis.fight.modules.skills.application.CloudLeapService;
import cn.yumis.fight.modules.skills.application.VisualService;
import cn.yumis.fight.modules.combat.application.NativeHits;
import cn.yumis.fight.modules.combat.application.BasicAttackService;
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
    private static ServerApplication CURRENT;
    public final MinecraftServer server;
    public final Journal journal;
    public final cn.yumis.fight.modules.professions.application.ProfessionService professionService;
    public final cn.yumis.fight.modules.party.application.PartyService partyService;
    public final InventoryTransactions inventory;
    public final DungeonService dungeons;
    public final LootService loot;
    public final VisualService visuals;
    public final BasicAttackService basicAttacks=cn.yumis.fight.bootstrap.RuntimeAssembly.basicAttackService(this);
    public final DamageMeterService damageMeter;
    public final CloudLeapService movement;
    public final NativeHits nativeHits;
    public final Map<UUID,Session> sessions=new LinkedHashMap<>();
    private final ArrayDeque<Runnable> combatQueue=new ArrayDeque<>();
    public final Random random=new Random();
    public boolean bridge;
    public Difficulty worldDifficulty=Difficulty.NORMAL;
    public RewardSettings rewards=RewardSettings.DEFAULT;
    private final cn.yumis.fight.architecture.CommandRouter<ServerPlayer,JsonObject> commands=new cn.yumis.fight.architecture.CommandRouter<>();
    public final cn.yumis.fight.modules.equipment.application.EquipmentApplication equipmentApplication;
    public final cn.yumis.fight.modules.economy.application.EconomyService economyService;
    public final cn.yumis.fight.modules.equipment.application.EquipmentContracts equipmentContracts;
    public final cn.yumis.fight.modules.character.application.ProgressService progressService;
    public final cn.yumis.fight.modules.inventory.application.InventoryApplication inventoryApplication;
    public final cn.yumis.fight.modules.combat.application.CombatApplication combatApplication;
    public final cn.yumis.fight.modules.skills.application.CareerSkillService careers;
    public final cn.yumis.fight.modules.skills.application.SkillStartService skillStarts;
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
            wireDefinitions=value;definitionsKey=ProfileSync.digest(value);definitionsBundle=bundle;
        }
        return wireDefinitions;
    }
    private final cn.yumis.fight.modules.combat.application.StunControl stunControl=new cn.yumis.fight.modules.combat.application.StunControl();
    public static final class Session extends cn.yumis.fight.modules.combat.api.CombatSession {
        public Progress progress;
        public long statsRevision=-1;
        public long runtimeSequence=-1;
        public long runtimeGeneration=-1,runtimeEpoch=-1;
        public Session(Progress p,cn.yumis.fight.modules.combat.api.Runtime r,Life l){super(r,l);progress=p;r.equipmentBuffs.clear();}
    }
    private ServerApplication(MinecraftServer server)throws Exception{
        this.server=server;
        cn.yumis.fight.bootstrap.ApplicationRules.install();
        journal=new Journal(server.getWorldPath(LevelResource.ROOT).resolve(ItemData.NS).resolve("modules-v1").resolve("state.wal"));
        professionService=new cn.yumis.fight.modules.professions.application.ProfessionService(journal);
        partyService=new cn.yumis.fight.modules.party.application.PartyService(journal,server.getPlayerList()::getPlayerByName,this::refreshAll);
        loot=cn.yumis.fight.bootstrap.RuntimeAssembly.lootService(this);
        inventory=new InventoryTransactions(journal,server,loot,this::profile,this::message,cn.yumis.fight.bootstrap.RuntimeAssembly.currencyAction(this));
        equipmentLayout=new cn.yumis.fight.modules.equipment.application.EquipmentLayoutService(journal,inventory,this::profile);
        visuals=cn.yumis.fight.bootstrap.RuntimeAssembly.visualService(this);
        targets=RuntimeAssembly.targetService(this);
        nativeHits=cn.yumis.fight.bootstrap.RuntimeAssembly.nativeHits(this);
        movement=cn.yumis.fight.bootstrap.RuntimeAssembly.cloudLeapService(this);
        dungeons=cn.yumis.fight.bootstrap.RuntimeAssembly.dungeonService(this);

        worldHealth=new cn.yumis.fight.modules.dungeons.application.WorldHealthService(server,journal,targets::resolve,this::session,()->bridge,value->bridge=value);
        damageMeter=cn.yumis.fight.bootstrap.RuntimeAssembly.damageMeterService(this);
        equipmentApplication=new cn.yumis.fight.modules.equipment.application.EquipmentApplication(journal,inventory,p->session(p).progress,p->session(p).id,(p,id)->professionService.get(p.getUUID(),id),this::profile,this::message,random,cn.yumis.fight.modules.economy.api.CurrencyRules::salvageCurrency);
        equipmentContracts=new cn.yumis.fight.modules.equipment.application.EquipmentContracts(journal);
        economyService=new cn.yumis.fight.modules.economy.application.EconomyService(journal,inventory,p->session(p).id,this::profile,equipmentContracts);
        progressService=new cn.yumis.fight.modules.character.application.ProgressService(journal,inventory,p->session(p).progress,p->session(p).resources.combatUntil,(p,next)->session(p).progress=next,this::profile,this::stats,cn.yumis.fight.modules.skills.api.SkillLearning::apply);
        inventoryApplication=new cn.yumis.fight.modules.inventory.application.InventoryApplication(journal,inventory,p->session(p).id,this::message);
        cn.yumis.fight.modules.industry.api.IndustrialPorts.install(inventory,inventory::recoverIndustrial,this::message);
        combatApplication=new cn.yumis.fight.modules.combat.application.CombatApplication(journal,server,this::session,cn.yumis.fight.modules.combat.api.ManagedEntities::includes,targets::resolve,
            new cn.yumis.fight.modules.combat.api.CombatPorts.Encounters(){
                public boolean participant(ServerPlayer p,String instance){return dungeons.participant(p,instance);}
                public void died(LivingEntity entity){if(entity instanceof cn.yumis.fight.modules.dungeons.api.DungeonEnemy)dungeons.died(entity.getUUID());}
                public void cancelBoss(UUID id){dungeons.cancelBoss(id);}
            },new cn.yumis.fight.modules.combat.api.CombatPorts.Rewards(){
                public void death(ServerPlayer p,LivingEntity entity,cn.yumis.fight.modules.combat.api.CombatTarget target)throws Exception{loot.death(p,entity,(Target)target);}
                public void publish(){loot.publish();}
                public void abort(){loot.abort();}
            },nativeHits,basicAttacks,(p,target,fraction,time)->damageMeter.record(p,(Target)target,fraction,time),value->bridge=value,this::message);
        careers=new cn.yumis.fight.modules.skills.application.CareerSkillService(journal,server,this::session,p->session(p).progress,this::stats,(p,id)->{var team=partyService.team(p.getUUID());return team!=null&&team.members.contains(id);});
        skillStarts=new cn.yumis.fight.modules.skills.application.SkillStartService(journal,this::session,p->session(p).progress,this::stats,careers::castStats,careers::baseSucceeded,inventory::locked);
        progressService.transitions(careers);combatApplication.skills(cn.yumis.fight.modules.combat.api.CombatPorts.combine(careers,skillStarts));
        combatApplication.equipment(p->cn.yumis.fight.modules.skills.api.SkillPreview.currentStats(stats(p),session(p).resources,System.currentTimeMillis()),p->session(p).progress,p->dungeons.current(p.getUUID())==null?session(p).progress.sync:dungeons.current(p.getUUID()).sync,this::profile);
        registerCommands();
        loadDefinitions();
        dungeons.recover();
        loot.recover();
    }
    public static void start(MinecraftServer server){
        try{
            CURRENT=new ServerApplication(server);
            cn.yumis.fight.modules.economy.api.LootEntityActions.install(CURRENT.loot::validate,CURRENT.loot::pickup);
            cn.yumis.fight.modules.dungeons.api.CageActions.install(CURRENT.dungeons::cageHit);
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
            for(ServerPlayer p:CURRENT.server.getPlayerList().getPlayers())CURRENT.savePlayer(p);
            CURRENT.dungeons.shutdown();
            CURRENT.journal.close();
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
        finally{
            cn.yumis.fight.modules.industry.api.IndustrialPorts.clear();
            cn.yumis.fight.modules.combat.api.NativeEffects.clear();
            cn.yumis.fight.modules.economy.api.LootEntityActions.clear();
            cn.yumis.fight.modules.dungeons.api.CageActions.clear();
            CURRENT=null;
            Network.SERVER=(p,a,j)->{throw new IllegalStateException("服务未就绪");};
        }
    }
    public static void registerEvents(){
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent event)->{if(ready()&&event.getEntity() instanceof ServerPlayer player&&get().careers.frenzy(player))event.setCanceled(true);});
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
        NeoForge.EVENT_BUS.addListener((PlayerEvent.StartTracking e)->{
            if(ready()&&e.getEntity() instanceof ServerPlayer p&&e.getTarget() instanceof LivingEntity target&&cn.yumis.fight.modules.combat.api.ManagedEntities.includes(target)){
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
                    if(!s.life.instance.isEmpty()&&p.level().dimension()!=DungeonService.DIMENSION)runtime.dungeons.leave(p,false);s.life.dimension=p.level().dimension().identifier().toString();s.life.revision++;runtime.savePlayer(p);runtime.profile(p);
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
            if(!Files.exists(config))Files.writeString(config,"# 大世界难度：NORMAL / ADVANCED / WARRIOR / KING / HELL\nworldDifficulty=\"NORMAL\"\n# 只影响战斗经验；不影响副职业和管理员奖励\ncombatExperienceMultiplier=1.0\n# 只影响杀怪金币；不影响出售价格\nkillGoldMultiplier=1.0\n");
            String text=Files.readString(config);
            var match=java.util.regex.Pattern.compile("worldDifficulty\\s*=\\s*\"(\\w+)\"").matcher(text);
            Difficulty nextDifficulty=match.find()?Difficulty.valueOf(match.group(1)):worldDifficulty;
            RewardSettings nextRewards=RewardSettings.parse(text);
            String additions="";
            if(!java.util.regex.Pattern.compile("(?m)^\\s*combatExperienceMultiplier\\s*=").matcher(text).find())additions+="\n# 战斗经验倍率；不影响副职业或管理员经验\ncombatExperienceMultiplier=1.0\n";
            if(!java.util.regex.Pattern.compile("(?m)^\\s*killGoldMultiplier\\s*=").matcher(text).find())additions+="\n# 仅杀怪金币倍率；出售价格不变\nkillGoldMultiplier=1.0\n";
            if(!additions.isEmpty())Files.writeString(config,additions,StandardOpenOption.APPEND);
            var nextSkills=cn.yumis.fight.modules.skills.api.SkillBalanceRules.defaults();
            var skillResource=server.getResourceManager().getResource(Identifier.fromNamespaceAndPath(ItemData.NS,"rules/skills.json"));
            if(skillResource.isPresent())try(var reader=skillResource.get().openAsReader()){nextSkills=cn.yumis.fight.modules.skills.api.SkillBalanceRules.parse(JsonParser.parseReader(reader).toString());}
            var candidate=cn.yumis.fight.bootstrap.ApplicationRules.Bundle.validated(nextCatalog,nextDungeons,nextNames,nextDefinitions,nextAffixes,nextLayout,nextIndustrial,nextSkills);
            journal.transaction(Map.of("definitions/equipment",JSON.toJsonTree(candidate.equipment()),"definitions/affixes",JSON.toJsonTree(candidate.affixes()),"definitions/skills",JSON.toJsonTree(candidate.skills())));
            cn.yumis.fight.bootstrap.ApplicationRules.publish(candidate);
            worldDifficulty=nextDifficulty;
            rewards=nextRewards;
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
            Progress progress=journal.get("player/"+id,Progress.class,new Progress());cn.yumis.fight.modules.combat.api.Runtime r=journal.get("resources/"+id,cn.yumis.fight.modules.combat.api.Runtime.class,new cn.yumis.fight.modules.combat.api.Runtime());Life l=journal.get("life/"+id,Life.class,new Life());l.target=id;l.dimension=p.level().dimension().identifier().toString();return new Session(progress,r,l);
        }
        );
    }
    public void login(ServerPlayer p){
        damageMeter.forget(p);
        try{
            inventory.restore(p);
            this.equipmentLayout.reconcile(p,this.equipmentLayout.bonus(p));
            Session s=session(p);
            movement.reset(p);
            dungeons.reconnect(p);
            VisualService.resetGravity(p);
            var stats=stats(p);
            if(s.resources.mp<0)s.resources.mp=stats.get(Stat.MP);
            if(s.resources.shield<0)s.resources.shield=stats.get(Stat.SHIELD);
            if(!s.progress.starterGranted){
                Progress next=copy(s.progress,Progress.class);
                next.starterGranted=true;
                ItemStack starter=EquipmentItems.gear(EquipmentRules.create(Slot.WEAPON,Weapon.KATANA,Rarity.COMMON,1,random));
                JsonObject intent=new JsonObject();
                intent.addProperty("starter",true);
                try{
                    inventory.transact(p,"starter",intent,()->inventory.requireSpace(p,starter),Map.of("player/"+p.getUUID(),JSON.toJsonTree(next)));
                }
                catch(InventoryTransactions.NoSpace full){
                    journal.begin();
                    loot.privateDrop(p,starter,p.position(),"");
                    journal.put("player/"+p.getUUID(),next);
                    journal.finish();
                    loot.publish();
                }
                s.progress=next;
            }
            if(s.life.dead&&!p.isDeadOrDying()){
                bridge=true;
                try{
                    p.hurtServer(p.level(),p.damageSources().genericKill(),Float.MAX_VALUE);
                }
                finally{
                    bridge=false;
                }
            }
            savePlayer(p);
            profile(p);
        }
        catch(Exception e){
            p.connection.disconnect(cn.yumis.fight.modules.presentation.api.ModText.of("状态恢复失败："+e.getMessage()));
        }
    }
    public void logout(ServerPlayer p){
        viewSenders.remove(p.getUUID());
        try{
            careers.clear(p);
            session(p).resources.equipmentBuffs.clear();
            inventory.closeCursor(p);
            if(p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu)inventory.finishMenu(p,p.containerMenu);
            savePlayer(p);
            loot.endSession(p);
            dungeons.disconnected(p);
            sessions.remove(p.getUUID());
            movement.logout(p);
        }
        catch(Exception e){
            server.sendSystemMessage(cn.yumis.fight.modules.presentation.api.ModText.of("保存玩家失败："+e.getMessage()));
        }
    }
    public void respawn(ServerPlayer p){
        careers.clear(p);
        Session s=session(p);
        inventory.restore(p);
        s.life.generation++;
        movement.reset(p);
        s.life.revision=0;
        s.life.q=1;
        s.life.dead=false;
        s.life.dimension=p.level().dimension().identifier().toString();
        Stats stats=stats(p);
        s.resources.mp=stats.get(Stat.MP);
        if(s.resources.training)s.resources.trainingBoundary=s.resources.mp;
        VisualService.resetGravity(p);
        s.resources.shield=stats.get(Stat.SHIELD);
        s.resources.combatUntil=0;
        s.resources.shieldRecoveryAt=0;
        s.resources.buffs.clear();
        s.resources.equipmentBuffs.clear();
        s.resources.absorption.clear();
        s.resources.absorptionUntil.clear();
        p.getFoodData().setFoodLevel(20);
        dungeons.respawn(p);
        savePlayer(p);
        profile(p);
    }
    public void savePlayer(ServerPlayer p){
        try{
            Session s=session(p);
            journal.begin();
            journal.put("player/"+p.getUUID(),s.progress);
            journal.put("resources/"+p.getUUID(),s.resources);
            journal.put("life/"+p.getUUID(),s.life);
            journal.finish();
        }
        catch(Exception e){
            journal.abort();
            throw new IllegalStateException(e);
        }
    }
    public Stats stats(ServerPlayer p){
        Stats result=EquipmentStats.build(p,session(p).progress);session(p).resources.equipment=EquipmentStats.effects(p,session(p).progress);return result;
    }
    public void profile(ServerPlayer p){
        Session s=session(p);
        Stats stats=stats(p);
        s.statsRevision=EquipmentStats.cacheRevision(p);
        var effective=cn.yumis.fight.modules.skills.api.SkillPreview.currentStats(stats,s.resources,System.currentTimeMillis());
        var attribute=p.getAttribute(Attributes.MAX_HEALTH);
        if(attribute!=null)attribute.setBaseValue(Math.max(1,effective.get(Stat.HEALTH)));
        bridge=true;
        try{
            if(!s.life.dead)p.setHealth((float)Math.max(.001,s.life.q*p.getMaxHealth()));
        }
        finally{
            bridge=false;
        }
        JsonObject j=new JsonObject();
        j.addProperty("protocol",cn.yumis.fight.architecture.ProtocolVersion.CURRENT);
        j.addProperty("rules",cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION);
        j.addProperty("session",s.id.toString());
        j.addProperty("serverNow",System.currentTimeMillis());
        j.addProperty("gold",economyService.gold(p));j.addProperty("goldRevision",economyService.goldRevision(p));
        j.addProperty("contractUntil",equipmentContracts.contractUntil(p.getUUID()));
        j.add("progress",JSON.toJsonTree(s.progress));
        j.add("equipmentEffects",JSON.toJsonTree(s.resources.equipment));
        j.add("resources",JSON.toJsonTree(s.resources));
        j.add("life",JSON.toJsonTree(s.life));
        j.add("stats",JSON.toJsonTree(stats));
        j.add("movement",movement.snapshot(p));
        var sources=cn.yumis.fight.modules.attributes.api.AttributeServices.attributes(p.getUUID());
        j.addProperty("attributeRevision",sources.revision());
        j.add("attributeSources",JSON.toJsonTree(sources.snapshot()));
        j.add("party",JSON.toJsonTree(partyService.team(p.getUUID())));
        j.add("dungeon",JSON.toJsonTree(dungeons.current(p.getUUID())));
        j.addProperty("equipmentState",inventory.clientStorageState(p));
        j.add("equipmentGroups",JSON.toJsonTree(MenuHooks.extra(p).groups));
        JsonObject definition=definitions();
        viewSenders.computeIfAbsent(p.getUUID(),id->new ProfileSync.Sender()).publish(s.id.toString(),definitionsKey,definition,j,(action,packet)->Network.send(p,action,packet));
        careers.sync(p);
        damageMeter.send(p,dungeons.current(p.getUUID()));
    }
    public void message(ServerPlayer p,String message){
        Network.send(p,"message",Map.of("text",message==null?"操作失败":message));
    }
    public void context(ServerPlayer p,LivingEntity e){
        if(e instanceof cn.yumis.fight.modules.dungeons.api.TrainingSandbag)return;
        Target t=targets.resolve(e);
        Network.send(p,"context",t);
        for(String key:journal.keys("ailments/"+t.id+"/")){
            JsonElement ailments=journal.get(key);
            if(!ailments.isJsonNull())Network.send(p,"ailments",ailments);
        }
    }
    public void handle(ServerPlayer p,String action,JsonObject j){
        try{session(p);if(!commands.dispatch(p,action,j))message(p,"未知操作："+action);}
        catch(Exception e){message(p,e.getMessage());}
    }
    private void registerCommands(){
        careers.register(commands);skillStarts.register(commands);
        equipmentApplication.register(commands);economyService.register(commands);progressService.register(commands);inventoryApplication.register(commands);
        commands.register("presentation","hello",(p,j)->{if(j.get("protocol").getAsInt()!=cn.yumis.fight.architecture.ProtocolVersion.CURRENT||j.has("rules")&&j.get("rules").getAsInt()!=cn.yumis.fight.bootstrap.ApplicationRules.DATA_VERSION){p.connection.disconnect(ModText.of("模组协议或规则版本不一致"));return;}profile(p);});
        commands.register("presentation","profile",(p,j)->{var sender=viewSenders.get(p.getUUID());if(sender!=null){if(j.has("definitions")&&j.get("definitions").getAsBoolean())sender.resetDefinitions();else sender.resetPersonal();}profile(p);});
        commands.register("combat","context",(p,j)->{Entity e=p.level().getEntity(j.get("entity").getAsInt());if(e instanceof LivingEntity l&&cn.yumis.fight.modules.combat.api.ManagedEntities.includes(l))context(p,l);});
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
        commands.register("dungeons","dungeon",dungeons::request);commands.register("dungeons","cageHit",dungeons::cageHit);
        commands.register("combat","basicStart",basicAttacks::start);commands.register("skills","visual",visuals::receive);
        commands.register("skills","cloudLeap",movement::request);commands.register("skills","movementUi",movement::ui);
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
        for(ServerPlayer viewer:server.getPlayerList().getPlayers()){var sender=viewSenders.get(viewer.getUUID());if(sender!=null)sender.flush((action,packet)->Network.send(viewer,action,packet));}
        cn.yumis.fight.modules.industry.platform.IndustrialWorld.tick(server);
        movement.tick();
        int size=combatQueue.size();
        for(int i=0;i<size;i++)combatQueue.removeFirst().run();
        if(ticks%5==0){
            careers.tick();
            for(ServerPlayer player:server.getPlayerList().getPlayers())EquipmentStats.applyMovement(player,careers.currentStats(player));
            dungeons.tick();
            loot.tick();
            combatApplication.expireAilments();
            worldHealth.tick(ticks);
            visuals.tick();
        }
        if(ticks%20==0)for(ServerPlayer p:server.getPlayerList().getPlayers()){
            Session s=session(p);
            damageMeter.send(p,dungeons.current(p.getUUID()));
            cn.yumis.fight.modules.equipment.application.EquipmentMaintenance.initializeUnconfiguredItems(p,s.progress,random);
            if(inventory.isBag(p)&&!(p.containerMenu instanceof cn.yumis.fight.modules.inventory.api.DimensionalMenu))inventory.bag(p,false);
            Stats st=stats(p);
            if(cn.yumis.fight.modules.combat.api.EquipmentBuffs.prune(s.resources,s.resources.equipment,System.currentTimeMillis(),s.life.dead))profile(p);
            var max=p.getAttribute(Attributes.MAX_HEALTH);
            if(EquipmentStats.cacheRevision(p)!=s.statsRevision)profile(p);
        }
        stunControl.tick(server);
    }
}

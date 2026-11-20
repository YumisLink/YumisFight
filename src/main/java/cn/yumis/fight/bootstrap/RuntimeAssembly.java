package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import cn.yumis.fight.modules.economy.application.LootService;
import cn.yumis.fight.modules.dungeons.application.DungeonService;
import cn.yumis.fight.modules.dungeons.application.DamageMeterService;
import cn.yumis.fight.modules.dungeons.application.RitualEncounterService;
import cn.yumis.fight.modules.industry.api.IndustrialRituals;
import cn.yumis.fight.modules.industry.platform.DemonAltarBlockEntity;
import cn.yumis.fight.modules.npc.api.AwakeningQuest;
import cn.yumis.fight.modules.skills.application.VisualService;
import cn.yumis.fight.modules.combat.application.NativeHits;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.combat.api.CombatPorts;
import cn.yumis.fight.modules.combat.api.CombatSession;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Composition of narrow module ports. No gameplay decisions belong here. */
public final class RuntimeAssembly {
    private RuntimeAssembly(){}
    public static RewardPorts.Players rewardPlayers(ServerApplication runtime){return new RewardPorts.Players(){
        public UUID session(ServerPlayer p){return runtime.session(p).id;}public Progress progress(ServerPlayer p){return runtime.session(p).progress;}public void progress(ServerPlayer p,Progress next){runtime.session(p).progress=next;}
        public Stats stats(ServerPlayer p){return runtime.stats(p);}
        public void profile(ServerPlayer p){runtime.profile(p);}public void message(ServerPlayer p,String text){runtime.message(p,text);}
        public void groundPickup(ServerPlayer p,ItemStack stack){ServerEvents.groundPickup(p,stack);}
    };}
    public static cn.yumis.fight.modules.dungeons.application.TargetService targetService(ServerApplication runtime){return new cn.yumis.fight.modules.dungeons.application.TargetService(runtime.journal,runtime.random,()->runtime.worldDifficulty,id->runtime.dungeons.byId(id));}
    public static cn.yumis.fight.modules.inventory.application.InventoryTransactions.CurrencyAction currencyAction(ServerApplication runtime){return (p,menu,slot,button,input)->cn.yumis.fight.modules.inventory.api.InventoryIntake.click(p,menu,slot,button,input,()->runtime.equipmentApplication.versionlessCurrencyClick(p,menu,slot,button,input));}

    public static cn.yumis.fight.modules.combat.application.NativeHits nativeHits(ServerApplication runtime){return new cn.yumis.fight.modules.combat.application.NativeHits(runtime.journal,runtime::session,runtime.targets::resolve,cn.yumis.fight.modules.combat.api.ManagedEntities::includes,()->runtime.bridge,runtime.inventory::encode);}
    public static cn.yumis.fight.modules.skills.application.VisualService visualService(ServerApplication runtime){return new cn.yumis.fight.modules.skills.application.VisualService(runtime.server,runtime::session);}
    public static cn.yumis.fight.modules.dungeons.application.DamageMeterService damageMeterService(ServerApplication runtime){return new cn.yumis.fight.modules.dungeons.application.DamageMeterService(runtime.journal,runtime.server,id->runtime.dungeons.current(id),(p,id)->runtime.dungeons.participant(p,id),p->runtime.session(p).progress.level,()->runtime.combatSettings);}
    public static DungeonService dungeonService(ServerApplication runtime){return new DungeonService(runtime.journal,runtime.characters,runtime.server,new DungeonPorts.Players(){
        public CombatSession session(ServerPlayer p){return runtime.session(p);}
        public int level(ServerPlayer p){return runtime.session(p).progress.level;}
        public Progress progress(ServerPlayer p){return runtime.session(p).progress;}
        public void progress(ServerPlayer p,Progress next){runtime.session(p).progress=next;}
        public void save(ServerPlayer p)throws Exception{runtime.savePlayer(p);}
        public void profile(ServerPlayer p){runtime.profile(p);}
        public void message(ServerPlayer p,String text){runtime.message(p,text);}
    },new DungeonPorts.Targets(){
        public Target target(LivingEntity e){return runtime.targets.resolve(e);}
        public void context(ServerPlayer p,LivingEntity e){runtime.context(p,e);}
    },new DungeonPorts.Rewards(){
        public void move(ServerPlayer p,String instance,ReturnPoint point){runtime.loot.move(p,instance,point);}
        public void publish(){runtime.loot.publish();}
        public void closePractice(String instance)throws Exception{runtime.loot.closePractice(instance);}
    },new DungeonPorts.Meter(){
        public void join(Dungeon d,ServerPlayer p)throws Exception{runtime.damageMeter.join(d,p);}
        public void leave(Dungeon d,ServerPlayer p)throws Exception{runtime.damageMeter.leave(d,p);}
        public void freeze(Dungeon d)throws Exception{runtime.damageMeter.freeze(d);}
    },runtime.partyService::team,runtime::refreshAll,cn.yumis.fight.modules.mapgeneration.api.WorldDecorations::block,(instance,participants)->{
        var clear=runtime.awakeningQuests.prepareTowerClear(instance,participants);
        return new CombatPorts.Commit(){
            @Override public void persist()throws Exception{runtime.journal.transaction(clear.writes());}
            @Override public void publish(){clear.publish().run();}
        };
    },(instance,dungeon,difficulty,participants)->runtime.promotionQuests.prepareStoryClear(instance,dungeon,difficulty,participants));}
    public static LootService lootService(ServerApplication runtime){return new LootService(runtime.journal,runtime.characters,runtime.server,()->runtime.inventory,rewardPlayers(runtime),new RewardPorts.Encounters(){
        public boolean participant(ServerPlayer p,String instance){return runtime.dungeons.participant(p,instance);}
        public Dungeon byId(String instance){return runtime.dungeons.byId(instance);}
        public String currentInstance(ServerPlayer p){var d=runtime.dungeons.current(p.getUUID());return d==null?"":d.id.toString();}
        public boolean finalPracticeKill(Dungeon dungeon,UUID enemy){return runtime.dungeons.finalPracticeKill(dungeon,enemy);}
    },rewardBalance(runtime));}
    public static RewardPorts.Balance rewardBalance(ServerApplication runtime){return new RewardPorts.Balance(){
        public RewardSettings settings(){return runtime.rewards;}
        public long gold(ServerPlayer p){return runtime.economyService.gold(p);}
        public long revision(ServerPlayer p){return runtime.economyService.goldRevision(p);}
        public void notifyGain(ServerPlayer p,long amount,long balance,long revision){runtime.economyService.goldGain(p,amount,balance,revision);}
        public Map<String,com.google.gson.JsonElement> receive(ServerPlayer p,com.google.gson.JsonObject result){return runtime.economyService.receiveBalances(p,result);}
    };}
    public static WaystonePorts waystonePorts(ServerApplication runtime){return new WaystonePorts(){
        public Map<String,com.google.gson.JsonElement> drop(ServerPlayer player,net.minecraft.world.item.ItemStack stack,ReturnPoint point,String instance){var drop=runtime.loot.preparePrivate(player,stack,point,instance);return Map.of("loot/"+drop.id,new com.google.gson.Gson().toJsonTree(drop));}
        public void publish(java.util.Collection<String> committedKeys){runtime.loot.publishCommittedDrops(committedKeys);}
    };}

    private static DemonAltarBlockEntity altar(ServerLevel level,BlockPos position){
        return level.hasChunkAt(position)&&level.getBlockEntity(position) instanceof DemonAltarBlockEntity found&&!found.isRemoved()?found:null;
    }

    public static RitualEncounterService rituals(ServerApplication runtime){
        cn.yumis.fight.modules.industry.platform.RitualEnergy.install(runtime.journal);
        var service=new RitualEncounterService(runtime.journal,runtime.server,runtime.inventory,runtime.targets,new RitualPorts.AltarPower(){
            @Override public String identity(ServerLevel level,BlockPos pos){var altar=altar(level,pos);return altar==null?"":altar.key();}
            @Override public boolean usable(ServerPlayer player,BlockPos pos){var altar=altar(player.level(),pos);return altar!=null&&altar.stillValid(player);}
            @Override public boolean commitSummon(ServerLevel level,BlockPos pos,long cost,String runId,Map<String,com.google.gson.JsonElement> writes)throws Exception{
                var altar=altar(level,pos);
                return altar!=null&&altar.commitSummonEnergy(level,cost,runId,writes);
            }
        },(clear,material)->{
            var players=new LinkedHashSet<UUID>();
            for(String id:clear.participants())players.add(UUID.fromString(id));
            var quest=runtime.awakeningQuests.prepareAltarClear(UUID.fromString(clear.runId()),clear.bossId(),clear.ending().equals("surrender"),players);
            var rewards=runtime.loot.prepareRitual(clear,material,quest.chantPagePlayers(),AwakeningQuest.CHANT_PAGE);
            var writes=new LinkedHashMap<>(quest.writes());
            writes.putAll(rewards.writes());
            return new RitualPorts.ClearCommit(writes,()->{rewards.publish().run();quest.publish().run();runtime.loot.publish();});
        },cn.yumis.fight.modules.mapgeneration.api.MapGeneration::spawnSceneActor,runtime.random);
        runtime.loot.ritualUnlocks(service::unlocked);
        return service;
    }

    public static void installRitualPorts(RitualEncounterService service){
        IndustrialRituals.install(new IndustrialRituals.Handler(){
            @Override public void unlock(ServerPlayer player,ServerLevel level,BlockPos pos,net.minecraft.world.item.ItemStack stack){service.unlock(player,pos,stack);}
            @Override public void summonMenu(ServerPlayer player,ServerLevel level,BlockPos pos,long storedMana,boolean connected,long grid){service.sendMenu(player,level,pos,storedMana,connected,grid);}
        });
        RitualBossEntity.wire(service::bossInteract);
        RitualMechanismEntity.wire(service::mechanismInteract);
    }

    public static void clearRitualPorts(){
        IndustrialRituals.clear();
        RitualBossEntity.wire((player,boss)->false);
        RitualMechanismEntity.wire((player,mechanism)->false);
        cn.yumis.fight.modules.industry.platform.RitualEnergy.install(null);
    }
}

package cn.yumis.fight.bootstrap;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import cn.yumis.fight.modules.dungeons.api.DungeonPorts;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.economy.api.RewardSettings;
import cn.yumis.fight.modules.economy.application.LootService;
import cn.yumis.fight.modules.dungeons.application.DungeonService;
import cn.yumis.fight.modules.dungeons.application.DamageMeterService;
import cn.yumis.fight.modules.skills.application.CloudLeapService;
import cn.yumis.fight.modules.skills.application.VisualService;
import cn.yumis.fight.modules.combat.application.NativeHits;
import cn.yumis.fight.modules.combat.application.BasicAttackService;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.combat.api.CombatSession;
import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.attributes.api.Stats;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import java.util.*;
/** Composition of narrow module ports. No gameplay decisions belong here. */
public final class RuntimeAssembly {
    private RuntimeAssembly(){}
    public static cn.yumis.fight.modules.dungeons.application.TargetService targetService(ServerApplication runtime){return new cn.yumis.fight.modules.dungeons.application.TargetService(runtime.journal,runtime.random,()->runtime.worldDifficulty,id->runtime.dungeons.byId(id));}
    public static cn.yumis.fight.modules.inventory.application.InventoryTransactions.CurrencyAction currencyAction(ServerApplication runtime){return (p,menu,slot,button,input)->runtime.equipmentApplication.versionlessCurrencyClick(p,menu,slot,button,input);}

    public static cn.yumis.fight.modules.combat.application.BasicAttackService basicAttackService(ServerApplication runtime){return new cn.yumis.fight.modules.combat.application.BasicAttackService(runtime::session,p->runtime.session(p).progress,p->cn.yumis.fight.modules.skills.api.WeaponSkills.basicAttack(runtime.session(p).progress,runtime.careers.currentStats(p))).sources(p->cn.yumis.fight.modules.skills.api.WeaponSkills.basicSnapshot(runtime.session(p).progress,runtime.careers.currentStats(p)),p->runtime.session(p).resources.equipment);}
    public static cn.yumis.fight.modules.combat.application.NativeHits nativeHits(ServerApplication runtime){return new cn.yumis.fight.modules.combat.application.NativeHits(runtime.journal,runtime::session,runtime.targets::resolve,cn.yumis.fight.modules.combat.api.ManagedEntities::includes,p->runtime.visuals.invulnerable(p)||runtime.careers.invulnerable(p),()->runtime.bridge,runtime.inventory::encode).launches((player,weapon)->runtime.careers.projectileSnapshot(player,weapon));}
    public static cn.yumis.fight.modules.skills.application.VisualService visualService(ServerApplication runtime){return new cn.yumis.fight.modules.skills.application.VisualService(runtime.server,runtime::session);}
    public static cn.yumis.fight.modules.skills.application.CloudLeapService cloudLeapService(ServerApplication runtime){return new cn.yumis.fight.modules.skills.application.CloudLeapService(runtime.server,runtime::session,runtime.inventory::locked,runtime.visuals::movementLocked);}
    public static cn.yumis.fight.modules.dungeons.application.DamageMeterService damageMeterService(ServerApplication runtime){return new cn.yumis.fight.modules.dungeons.application.DamageMeterService(runtime.journal,runtime.server,id->runtime.dungeons.current(id),(p,id)->runtime.dungeons.participant(p,id),p->runtime.session(p).progress.level,()->runtime.combatSettings);}
    public static DungeonService dungeonService(ServerApplication runtime){return new DungeonService(runtime.journal,runtime.server,new DungeonPorts.Players(){
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
    },new DungeonPorts.Meter(){
        public void join(Dungeon d,ServerPlayer p)throws Exception{runtime.damageMeter.join(d,p);}
        public void leave(Dungeon d,ServerPlayer p)throws Exception{runtime.damageMeter.leave(d,p);}
        public void freeze(Dungeon d)throws Exception{runtime.damageMeter.freeze(d);}
    },runtime.partyService::team,runtime::refreshAll);}
    public static LootService lootService(ServerApplication runtime){return new LootService(runtime.journal,runtime.server,()->runtime.inventory,new RewardPorts.Players(){
        public UUID session(ServerPlayer p){return runtime.session(p).id;}
        public Progress progress(ServerPlayer p){return runtime.session(p).progress;}
        public void progress(ServerPlayer p,Progress value){runtime.session(p).progress=value;}
        public Stats stats(ServerPlayer p){return cn.yumis.fight.modules.skills.api.SkillPreview.currentStats(runtime.stats(p),runtime.session(p).resources,System.currentTimeMillis());}
        public void profile(ServerPlayer p){runtime.profile(p);}
        public void message(ServerPlayer p,String text){runtime.message(p,text);}
    },new RewardPorts.Encounters(){
        public boolean participant(ServerPlayer p,String instance){return runtime.dungeons.participant(p,instance);}
        public Dungeon byId(String instance){return runtime.dungeons.byId(instance);}
    },new RewardPorts.Balance(){
        public RewardSettings settings(){return runtime.rewards;}
        public long gold(ServerPlayer p){return runtime.economyService.gold(p);}
        public long revision(ServerPlayer p){return runtime.economyService.goldRevision(p);}
        public void notifyGain(ServerPlayer p,long amount,long balance,long revision){runtime.economyService.goldGain(p,amount,balance,revision);}
    },runtime.random);}
}

package cn.yumis.fight.modules.dungeons.application;

import cn.yumis.fight.modules.dungeons.api.*;
import cn.yumis.fight.modules.attributes.api.*;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.network.Network;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Public pedestal enrollment and the isolated Hell+ lifecycle, owned by dungeons. */
public final class WaystoneService implements WaystoneActions.Handler {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final String SOURCE="yumis_dungeon_and_fight:waystone/";
    private final DungeonService owner;
    private final Journal journal;
    private final MinecraftServer server;
    private final DungeonPorts.Players players;
    private final DungeonPorts.Targets targets;
    private final DungeonPorts.Meter meter;
    private final InventoryOperations inventory;
    private final WaystonePorts rewards;
    private final Runnable recovery;
    private final Random random;
    private final Map<String,Table> tables=new LinkedHashMap<>();
    private boolean recoveryRequested;
    private static final class Table {
        String id,dimension,raw="",rules="";
        BlockPos pos;
        long revision,deadline;
        UUID startedBy;
        final Map<UUID,Boolean> members=new LinkedHashMap<>();
        final Set<UUID> viewers=new HashSet<>();
    }
    WaystoneService(DungeonService owner,Journal journal,MinecraftServer server,DungeonPorts.Players players,DungeonPorts.Targets targets,DungeonPorts.Meter meter,InventoryOperations inventory,WaystonePorts rewards,Runnable recovery,Random random){this.owner=owner;this.journal=journal;this.server=server;this.players=players;this.targets=targets;this.meter=meter;this.inventory=inventory;this.rewards=rewards;this.recovery=recovery;this.random=random;}
    @Override public void recover(){recoveryRequested=true;}
    private WaystonePedestalBlockEntity block(Table t){var world=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(t.dimension)));return world!=null&&world.hasChunkAt(t.pos)&&world.getBlockEntity(t.pos) instanceof WaystonePedestalBlockEntity b&&b.key().equals(t.id)?b:null;}
    private Table table(WaystonePedestalBlockEntity b){var t=tables.computeIfAbsent(b.key(),id->{var value=new Table();value.id=id;value.dimension=b.getLevel().dimension().identifier().toString();value.pos=b.getBlockPos().immutable();return value;});observe(t,b);return t;}
    private void observe(Table t,WaystonePedestalBlockEntity b){String raw=cn.yumis.fight.platform.ItemData.string(b.getItem(0),Waystone.DATA),rules=JSON.toJson(WaystoneRules.current());if(!t.raw.equals(raw)||!t.rules.equals(rules)){t.raw=raw;t.rules=rules;changed(t);}}
    private void changed(Table t){t.revision++;t.deadline=0;t.members.replaceAll((id,ready)->false);}
    @Override public boolean countingDown(WaystonePedestalBlockEntity b){var t=tables.get(b.key());return t!=null&&t.deadline>0;}
    @Override public void removed(WaystonePedestalBlockEntity b){var t=tables.remove(b.key());if(t!=null){for(UUID id:union(t)){var p=server.getPlayerList().getPlayer(id);if(p!=null)Network.send(p,"waystoneTable",Map.of("removed",true,"table",t.id));}}}
    private Set<UUID> union(Table t){Set<UUID> ids=new HashSet<>(t.viewers);ids.addAll(t.members.keySet());return ids;}
    @Override public void open(ServerPlayer p,WaystonePedestalBlockEntity b){try{recovery.run();b.display();if(!b.stillValid(p)||p.level().dimension()!=Level.OVERWORLD)throw new IllegalArgumentException("请在主世界引路台附近操作");var t=table(b);t.viewers.add(p.getUUID());send(t,b,p,true);}catch(Exception e){players.message(p,e.getMessage());}}
    public void command(ServerPlayer p,JsonObject j){try{
        String id=j.get("table").getAsString(),action=j.get("action").getAsString();var t=tables.get(id);if(t==null)throw new IllegalArgumentException("引路台报名已结束，请重新打开");
        if(action.equals("close")){t.viewers.remove(p.getUUID());return;}
        if(action.equals("leave")){if(t.members.remove(p.getUUID())!=null)changed(t);var b=block(t);if(b!=null)broadcast(t,b);return;}
        var b=block(t);if(b==null||!b.stillValid(p))throw new IllegalArgumentException("请在引路台8格内操作");recover();observe(t,b);
        if(!j.has("revision")||j.get("revision").getAsLong()!=t.revision){send(t,b,p,false);throw new IllegalArgumentException("引路台内容已变化，请重新确认");}
        var stone=Waystone.read(b.getItem(0));
        switch(action){
            case "insert"->{
                if(!b.isEmpty())throw new IllegalArgumentException("请先取下台上的引路石");var held=p.getMainHandItem();var next=Waystone.read(held);if(next==null)throw new IllegalArgumentException("请将引路石拿在主手");eligible(p,next);String raw=cn.yumis.fight.platform.ItemData.string(held,Waystone.DATA);var item=held.copyWithCount(1);
                boolean committed=inventory.transactMachine(p,"waystone-table/"+j.get("operation").getAsString(),j,b,()->{if(!b.isEmpty()||!cn.yumis.fight.platform.ItemData.string(p.getMainHandItem(),Waystone.DATA).equals(raw))throw new IllegalArgumentException("引路石发生变化");b.setItem(0,item);b.inserter=p.getUUID();p.getMainHandItem().shrink(1);},Map.of());if(committed){b.display();observe(t,b);}
            }
            case "take"->{if(stone==null)throw new IllegalArgumentException("台上没有引路石");String raw=t.raw;var item=b.getItem(0).copy();boolean committed=inventory.transactMachine(p,"waystone-table/"+j.get("operation").getAsString(),j,b,()->{if(!cn.yumis.fight.platform.ItemData.string(b.getItem(0),Waystone.DATA).equals(raw))throw new IllegalArgumentException("台上的引路石发生变化");inventory.requireSpace(p,item);b.clearContent();},Map.of());if(committed){b.display();observe(t,b);}}
            case "join"->{if(stone==null)throw new IllegalArgumentException("请先放入引路石");eligible(p,stone);if(owner.current(p.getUUID())!=null)throw new IllegalArgumentException("请先离开当前副本");if(!t.members.containsKey(p.getUUID())&&t.members.size()>=WaystoneRules.current().maximumPlayers)throw new IllegalArgumentException("本场人数已满");for(var other:tables.values())if(other!=t&&other.members.remove(p.getUUID())!=null){changed(other);var old=block(other);if(old!=null)broadcast(other,old);}if(t.members.putIfAbsent(p.getUUID(),false)==null)changed(t);}
            case "ready"->{if(stone==null||!t.members.containsKey(p.getUUID()))throw new IllegalArgumentException("请先报名");eligible(p,stone);if(owner.current(p.getUUID())!=null)throw new IllegalArgumentException("已进入其他副本，请先退出报名");t.members.put(p.getUUID(),!t.members.get(p.getUUID()));t.deadline=0;t.revision++;}
            case "start"->{if(!Boolean.TRUE.equals(t.members.get(p.getUUID())))throw new IllegalArgumentException("报名并准备后才可开始战斗");if(t.deadline>0)throw new IllegalArgumentException("本场已经开始倒数");validate(t,b);t.startedBy=p.getUUID();t.deadline=System.currentTimeMillis()+WaystoneRules.current().countdownMillis;t.revision++;}
            default->throw new IllegalArgumentException("未知引路台操作");
        }
        broadcast(t,b);
    }catch(Exception e){players.message(p,e.getMessage());}}
    private void eligible(ServerPlayer p,Waystone stone){var d=DungeonCatalog.current().dungeon(stone.dungeon);if(!d.available())throw new IllegalArgumentException("该引路石副本尚未开放");if(players.level(p)<d.level())throw new IllegalArgumentException("角色尚未解锁该副本");if(players.session(p).life.dead)throw new IllegalArgumentException("死亡状态不能参加挑战");}
    private void validate(Table t,WaystonePedestalBlockEntity b){
        var stone=Waystone.read(b.getItem(0));if(stone==null||b.inserter==null)throw new IllegalArgumentException("请先插入引路石");if(!t.members.containsKey(b.inserter))throw new IllegalArgumentException("石主需要报名并准备");if(t.members.isEmpty()||t.members.size()>WaystoneRules.current().maximumPlayers||t.members.values().stream().anyMatch(v->!v))throw new IllegalArgumentException("需要所有报名者准备");
        for(UUID id:t.members.keySet()){ServerPlayer p=server.getPlayerList().getPlayer(id);if(p==null)throw new IllegalArgumentException("报名者必须全部在线");eligible(p,stone);if(owner.current(id)!=null)throw new IllegalArgumentException("有报名者正在其他副本内");}
    }
    private void send(Table t,WaystonePedestalBlockEntity b,ServerPlayer p,boolean open){
        var j=new JsonObject();j.addProperty("table",t.id);j.addProperty("revision",t.revision);j.addProperty("open",open);j.addProperty("deadline",t.deadline);j.addProperty("serverNow",System.currentTimeMillis());j.addProperty("owner",b.inserter==null?"":b.inserter.toString());j.add("stone",JSON.toJsonTree(Waystone.read(b.getItem(0))));var members=new JsonArray();
        for(var entry:t.members.entrySet()){var m=new JsonObject();m.addProperty("id",entry.getKey().toString());var player=server.getPlayerList().getPlayer(entry.getKey());m.addProperty("name",player==null?entry.getKey().toString():player.getName().getString());m.addProperty("ready",entry.getValue());m.addProperty("online",player!=null);members.add(m);}j.add("members",members);Network.send(p,"waystoneTable",j);
    }
    private void broadcast(Table t,WaystonePedestalBlockEntity b){for(UUID id:union(t)){var p=server.getPlayerList().getPlayer(id);if(p!=null)send(t,b,p,false);}}
    public void tick(){
        if(recoveryRequested){recoveryRequested=false;recovery.run();}
        for(var t:new ArrayList<>(tables.values()))try{
            boolean left=t.members.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null||owner.current(id)!=null);if(left)changed(t);
            var b=block(t);if(b==null){if(t.deadline>0)changed(t);continue;}b.display();long old=t.revision;observe(t,b);if(left||old!=t.revision)broadcast(t,b);
            if(t.deadline>0&&System.currentTimeMillis()>=t.deadline)try{begin(t,b);}catch(Exception e){changed(t);broadcast(t,b);for(UUID id:t.members.keySet()){var p=server.getPlayerList().getPlayer(id);if(p!=null)players.message(p,"开战失败："+e.getMessage());}}
        }catch(Exception e){server.sendSystemMessage(net.minecraft.network.chat.Component.literal("引路台处理失败："+e.getMessage()));}
        for(ServerPlayer p:server.getPlayerList().getPlayers())updateSources(p);
    }
    private void begin(Table t,WaystonePedestalBlockEntity b)throws Exception{
        validate(t,b);var stone=Waystone.read(b.getItem(0));var rules=JSON.fromJson(JSON.toJson(WaystoneRules.current()),WaystoneRules.class);var stoneOwner=server.getPlayerList().getPlayer(b.inserter);
        var operator=server.getPlayerList().getPlayer(t.startedBy);if(operator==null||operator.level()!=b.getLevel())throw new IllegalArgumentException("点击开始的参战者须留在主世界直至倒数结束");
        Dungeon d=new Dungeon();d.dungeon=stone.dungeon;d.difficulty=Difficulty.HELL;d.cell=owner.freeCell();d.waystone=new WaystoneRun();var run=d.waystone;run.stone=stone;run.owner=b.inserter;run.ownerLevel=players.level(stoneOwner);run.lastOwnerDropRate=rewards.dropRate(stoneOwner);run.rules=rules;run.monsterLevel=rules.monsterLevel(stone.tier);run.statMultiplier=rules.statMultiplier(stone.tier);
        Target probe=new Target();probe.kind="BOSS";probe.elite=true;probe.elitePower="strong";probe.partySize=t.members.size();probe.difficulty=Difficulty.HELL;apply(probe,d);
        for(double stat:new double[]{probe.health(run.monsterLevel),probe.attack(run.monsterLevel),probe.defense(run.monsterLevel,false),probe.defense(run.monsterLevel,true)})if(!Double.isFinite(stat)||stat<=0)throw new IllegalArgumentException("当前阶数与等级配置超出可计算范围，引路石未消耗");
        for(UUID id:t.members.keySet()){var p=server.getPlayerList().getPlayer(id);d.roster.add(id);d.synchronization.put(id,false);d.returns.put(id.toString(),owner.point(p));}
        try{owner.forceArena(d,true);owner.ensureArena(DungeonCatalog.current().dungeon(d.dungeon),d.cell);owner.clearCell(d.cell);owner.resetDoors(d);}catch(Exception e){owner.forceArena(d,false);owner.clearCell(d.cell);throw e;}
        run.startedAt=System.currentTimeMillis();Map<String,JsonElement> edits=new LinkedHashMap<>();edits.put("instance/"+d.id,JSON.toJsonTree(d));d.returns.forEach((id,point)->edits.put("return/"+id,JSON.toJsonTree(point)));
        var intent=new JsonObject();intent.addProperty("stone",stone.id.toString());intent.addProperty("table",b.key());intent.add("members",JSON.toJsonTree(d.roster));String raw=t.raw;
        boolean committed;
        try{committed=inventory.transactMachine(operator,"waystone-start/"+stone.id,intent,b,()->{if(!cn.yumis.fight.platform.ItemData.string(b.getItem(0),Waystone.DATA).equals(raw)||!run.owner.equals(b.inserter))throw new IllegalArgumentException("开战前引路石发生变化");b.clearContent();},edits);}catch(Exception e){if(owner.byId(d.id.toString())==null){owner.forceArena(d,false);owner.clearCell(d.cell);}throw e;}
        if(!committed){owner.forceArena(d,false);return;}
        t.members.clear();changed(t);b.display();observe(t,b);broadcast(t,b);
        try{
        for(UUID id:d.roster){var p=server.getPlayerList().getPlayer(id);var life=players.session(p).life;life.instance=d.id.toString();life.dimension=DungeonService.DIMENSION.identifier().toString();life.revision++;players.save(p);meter.join(d,p);updateSources(p);owner.teleport(p,owner.level(),d.cell*512+.5,80,2.5);owner.gbl.track(p);owner.palace.track(p);Network.send(p,"waystoneStarted",Map.of("instance",d.id.toString()));}
        run.startedAt=System.currentTimeMillis();journal.put("instance/"+d.id,d);owner.spawnWave(d);
        for(UUID id:d.roster){var p=server.getPlayerList().getPlayer(id);players.profile(p);for(UUID enemy:d.enemies){var entity=owner.level().getEntity(enemy);if(entity instanceof net.minecraft.world.entity.LivingEntity living)targets.context(p,living);}}
        }catch(Exception failure){owner.close(d);for(UUID id:d.roster){var p=server.getPlayerList().getPlayer(id);if(p!=null&&players.session(p).life.instance.equals(d.id.toString())){var point=d.returns.get(id.toString());owner.back(p,point);players.session(p).life.instance="";players.session(p).life.dimension=p.level().dimension().identifier().toString();players.save(p);players.profile(p);}}throw failure;}
    }
    public void apply(Target target,Dungeon d){if(d.waystone==null)return;var run=d.waystone;var stone=run.stone;target.waystoneHealth=run.statMultiplier*(1+stone.effect("monster_health"));target.waystoneAttack=run.statMultiplier*(1+stone.effect("monster_attack"));target.waystoneDefense=run.statMultiplier;target.waystoneDefenseIncrease=stone.effect("monster_defense");target.waystoneResistance=stone.effect("monster_resistance");}
    public void complete(Dungeon d,List<ServerPlayer> here)throws Exception{settle(d,true,here);}
    public void fail(Dungeon d)throws Exception{if(d.waystone!=null&&!d.waystone.settled)settle(d,false,List.of());}
    private void settle(Dungeon d,boolean completed,List<ServerPlayer> here)throws Exception{
        var run=d.waystone;if(run.settled)return;if(!journal.get("waystoneSettlement/"+d.id).isJsonNull())throw new IllegalStateException("引路石结算状态不一致");
        run.finishedAt=System.currentTimeMillis();rewards.publish();
        var p=server.getPlayerList().getPlayer(run.owner);if(p!=null){run.lastOwnerDropRate=rewards.dropRate(p);run.ownerLevel=players.level(p);}run.resultingTier=run.rules.nextTier(run.stone.tier,run.elapsed(run.finishedAt),completed);run.result=completed?"COMPLETED":"FAILED";
        var replacement=Waystone.generate(run.resultingTier,run.ownerLevel,rewards.rarity(run.stone.effectiveDropRate(run.lastOwnerDropRate)),random);
        ReturnPoint point=completed&&p!=null&&here.contains(p)?owner.point(p):d.returns.get(run.owner.toString());if(point==null)throw new IllegalStateException("石主返回位置缺失");String instance=completed&&p!=null&&here.contains(p)?d.id.toString():"";
        var edits=new LinkedHashMap<String,JsonElement>(rewards.drop(run.owner,replacement.item(),point,instance));
        if(completed)for(ServerPlayer member:here)edits.putAll(rewards.drop(member.getUUID(),rewards.chest(run.stone.tier),owner.point(member),d.id.toString()));
        run.settled=true;d.status=completed?"COMPLETED":"CLOSED";edits.put("instance/"+d.id,JSON.toJsonTree(d));edits.put("waystoneSettlement/"+d.id,new JsonPrimitive(run.result));journal.transaction(edits);meter.freeze(d);rewards.publish();
        for(ServerPlayer member:server.getPlayerList().getPlayers())if(d.roster.contains(member.getUUID())){updateSources(member);players.profile(member);players.message(member,(completed?"地狱+通关":"地狱+挑战结束")+"，用时 "+(run.elapsed(run.finishedAt)/1000)+"秒；石主获得 +"+run.resultingTier+" 引路石。");}
    }
    public void disconnected(ServerPlayer p,Dungeon d)throws Exception{var run=d.waystone;if(run.owner.equals(p.getUUID())){run.lastOwnerDropRate=rewards.dropRate(p);run.ownerLevel=players.level(p);journal.put("instance/"+d.id,d);}if(server.getPlayerList().getPlayers().stream().noneMatch(other->!other.getUUID().equals(p.getUUID())&&owner.participant(other,d.id.toString())&&other.level().dimension()==DungeonService.DIMENSION))owner.close(d);}
    public void reconnect(ServerPlayer p,Dungeon d)throws Exception{if(!d.status.equals("RUNNING"))return;var life=players.session(p).life;life.instance=d.id.toString();life.dimension=DungeonService.DIMENSION.identifier().toString();life.revision++;if(p.level().dimension()!=DungeonService.DIMENSION)owner.teleport(p,owner.level(),d.cell*512+.5,80,2.5);updateSources(p);for(UUID id:d.enemies){var e=owner.level().getEntity(id);if(e instanceof net.minecraft.world.entity.LivingEntity living)targets.context(p,living);}players.profile(p);}
    public void death(Dungeon d)throws Exception{if(d.status.equals("RUNNING")){d.waystone.penaltyMillis=Math.addExact(d.waystone.penaltyMillis,d.waystone.rules.deathPenaltyMillis);journal.put("instance/"+d.id,d);}}
    public void updateSources(ServerPlayer p){
        var d=owner.current(p.getUUID());String id=d!=null&&d.waystone!=null&&d.status.equals("RUNNING")?SOURCE+d.id:"";var values=new ArrayList<AttributeSources.Value>();
        if(!id.isEmpty()){var stone=d.waystone.stone;ratio(values,Stat.MANA_REGENERATION,stone.effect("mana_regeneration"));ratio(values,Stat.COOLDOWN_RECOVERY,stone.effect("cooldown_recovery"));ratio(values,Stat.MOVEMENT_SPEED,stone.effect("movement_speed"));ratio(values,Stat.CAST_SPEED,stone.effect("cast_speed"));ratio(values,Stat.PHYSICAL_DEFENSE,stone.effect("player_defense"));ratio(values,Stat.MAGICAL_DEFENSE,stone.effect("player_defense"));double resist=stone.effect("player_resistance");if(resist>0)for(var stat:List.of(Stat.LIGHT_RESIST,Stat.FIRE_RESIST,Stat.ICE_RESIST,Stat.DARK_RESIST))values.add(new AttributeSources.Value(stat,-resist,AttributeSources.Operation.ADD,AttributeSources.Stage.CHARACTER));}
        var sources=AttributeServices.attributes(p.getUUID());synchronized(sources){var before=sources.snapshot();var old=before.stream().filter(s->s.id().startsWith(SOURCE)).toList();if(id.isEmpty()&&old.isEmpty()||old.size()==1&&old.getFirst().id().equals(id)&&old.getFirst().values().equals(values))return;
        long revision=Math.incrementExact(sources.revision());var next=new ArrayList<>(before.stream().filter(s->!s.id().startsWith(SOURCE)).toList());if(!id.isEmpty())next.add(new AttributeSources.Source(id,revision,List.copyOf(values)));sources.replace(revision,next);}
    }
    private static void ratio(List<AttributeSources.Value> values,Stat stat,double reduction){if(reduction>0)values.add(new AttributeSources.Value(stat,-reduction,AttributeSources.Operation.TOTAL_RATIO,AttributeSources.Stage.CHARACTER));}
}

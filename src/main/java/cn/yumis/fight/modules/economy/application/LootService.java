package cn.yumis.fight.modules.economy.application;
import cn.yumis.fight.modules.economy.api.PrivateLoot;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.dungeons.api.AbyssParty;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.economy.api.LootOdds;
import cn.yumis.fight.modules.economy.api.RewardRules;
import cn.yumis.fight.modules.dungeons.api.Dungeon;
import cn.yumis.fight.modules.dungeons.api.PracticeRoom;
import cn.yumis.fight.modules.dungeons.api.RitualClear;
import cn.yumis.fight.modules.dungeons.api.RitualPorts;
import cn.yumis.fight.modules.dungeons.api.Difficulty;

import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.inventory.api.InventoryDrops;
import cn.yumis.fight.modules.inventory.api.InventoryIntake;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import java.util.function.Supplier;
import cn.yumis.fight.*;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import java.util.function.Function;
public class LootService implements InventoryDrops,cn.yumis.fight.modules.economy.api.WorldItemTransfers.Port {
    private final cn.yumis.fight.modules.character.api.CharacterPersistence characters;
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}

    /** The entity UUID also identifies adopted native loot if a crash preceded its NBT save. */
    private String nativeIdentity(ItemEntity entity){
        String id=entity.getPersistentData().getStringOr("ydnf_drop","");
        if(id.isEmpty()&&!journal.get("loot/"+entity.getUUID()).isJsonNull()){
            id=entity.getUUID().toString();entity.getPersistentData().putString("ydnf_drop",id);
        }
        return id;
    }

    public void nativePickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre e){
            if(!(e.getPlayer() instanceof ServerPlayer p)||e.canPickup().isFalse())return;
            // NeoForge fires Pre before vanilla checks the delay/target. Keep both checks before our transaction.
            if(e.getItemEntity().hasPickUpDelay()||e.getItemEntity().getTarget()!=null&&!p.getUUID().equals(e.getItemEntity().getTarget())||inventory.get().locked(p.getUUID()))return;
            String id=nativeIdentity(e.getItemEntity());
            if(id.isEmpty()){
                // Receptacle pickups need a durable source even when vanilla would find no free slot.
                if(InventoryIntake.capacity(p,e.getItemEntity().getItem())<=0)return;
                try{
                    var prepared=prepare(p,e.getItemEntity());
                    journal.put(prepared.journalKey(),prepared.state());
                    committed(List.of(prepared.journalKey()));
                    id=prepared.id().toString();e.getItemEntity().getPersistentData().putString("ydnf_drop",id);
                }catch(Exception failure){e.setCanPickup(net.minecraft.util.TriState.FALSE);players.message(p,failure.getMessage());return;}
            }
            e.setCanPickup(net.minecraft.util.TriState.FALSE);
            LootService.Drop drop=journal.get("loot/"+id,LootService.Drop.class,null);
            if(drop==null||drop.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull()||drop.practice&&!practiceOpen(drop.instance)){e.getItemEntity().discard();return;}
            if(drop.practice&&!encounters.participant(p,drop.instance))return;
            ItemStack actual=e.getItemEntity().getItem(),expected=inventory.get().decode(p,drop.item);
            if(!ItemStack.isSameItemSameComponents(actual,expected))return;
            if(actual.getCount()>expected.getCount()){actual=expected.copy();e.getItemEntity().setItem(actual);}
            if(accountPickup(p,drop,actual)){if(!journal.get("lootEnd/"+id).isJsonNull())e.getItemEntity().discard();return;}int room=inventory.get().room(p,actual);
            int take=Math.min(room,actual.getCount());if(take<=0)return;ItemStack result=actual.copyWithCount(take),left=actual.copyWithCount(actual.getCount()-take);var next=copy(drop,LootService.Drop.class);next.item=inventory.get().encode(p,left);Map<String,JsonElement> edits=new HashMap<>();edits.put("loot/"+id,JSON.toJsonTree(next));if(left.isEmpty())edits.put("lootEnd/"+id,JSON.toJsonTree("picked"));JsonObject intent=new JsonObject();intent.addProperty("publicPickup",id);intent.addProperty("count",take);try{
                if(!inventory.get().transact(p,"public-pickup/"+UUID.randomUUID(),intent,()->inventory.get().requireSpace(p,result),edits))return;
                if(left.isEmpty())e.getItemEntity().discard();else e.getItemEntity().setItem(left);
                players.groundPickup(p,result);
                var view=EquipmentItems.view(result);if(view!=null&&view.creativeTemplate())players.profile(p);
            }
            catch(Exception failure){
                players.message(p,failure.getMessage());
            }
    }
    public static class Drop {
        public UUID id=UUID.randomUUID();
        public String owner="",session="",dimension="",instance="";
        public double x,y,z;
        public boolean nativeToss;
        public boolean logistics;
        public boolean practice;
        public double dx,dy,dz;
        public String thrower="";
        public long pickupAt;
        public long expires=System.currentTimeMillis()+300000;
        public JsonElement item;
    }
    private final Journal journal;
    private final MinecraftServer server;
    private final Supplier<InventoryOperations> inventory;
    private final RewardPorts.Players players;
    private final RewardPorts.Encounters encounters;
    private final RewardPorts.Balance balance;
    private final List<Drop> publish=new ArrayList<>();
    private record RewardNotice(UUID player,JsonElement fact){}
    private final List<RewardNotice> rewardNotices=new ArrayList<>();
    private java.util.function.Predicate<String> ritualUnlocked=id->true;

    public void ritualUnlocks(java.util.function.Predicate<String> unlocked){ritualUnlocked=unlocked;}

    public RewardPorts.ExperienceCommit prepareExperience(String id,Map<UUID,List<Integer>> percentages){
        var writes=new LinkedHashMap<String,JsonElement>();
        var notices=new ArrayList<RewardNotice>();
        percentages.forEach((player,waves)->{
            var online=server.getPlayerList().getPlayer(player);
            if(online==null||online.hasDisconnected())return;
            var fact=JSON.toJsonTree(RewardRules.Context.experience(id,waves));
            writes.put(rewardPrefix(online)+id,fact);
            notices.add(new RewardNotice(player,fact));
        });
        return new RewardPorts.ExperienceCommit(writes,()->rewardNotices.addAll(notices));
    }

    public RitualPorts.ClearCommit prepareRitual(RitualClear clear,String material,Set<UUID> pagePlayers,String page) {
        var target=new Target();
        target.id=UUID.fromString(clear.runId());
        target.life.target=target.id;
        target.originalLevel=clear.level();
        target.fixedLevel=true;
        target.dimension=clear.dimension();
        target.kind="BOSS";
        target.difficulty=Difficulty.WARRIOR;
        var writes=new LinkedHashMap<String,JsonElement>();
        var notices=new ArrayList<RewardNotice>();
        for(String participant:clear.participants()){
            UUID player=UUID.fromString(participant);
            var online=server.getPlayerList().getPlayer(player);
            if(online==null||online.hasDisconnected())continue;
            String id=RitualClear.idOf(clear.runId(),participant);
            var bonus=new RewardRules.RitualReward(material,pagePlayers.contains(player)?page:"");
            var context=new RewardRules.Context(id,player,target,null,balance.settings(),clear.dimension(),
                clear.x(),clear.y(),clear.z(),Set.of(player),null,bonus,null);
            var fact=JSON.toJsonTree(context);
            writes.put(rewardPrefix(online)+id,fact);
            notices.add(new RewardNotice(player,fact));
        }
        return new RitualPorts.ClearCommit(Map.copyOf(writes),()->rewardNotices.addAll(notices));
    }
    private final ArrayDeque<Drop> recover=new ArrayDeque<>();
    private Map<UUID,Long> activeDrops=new LinkedHashMap<>();
    private boolean dropIndexValid;
    private long recoverAfter=System.currentTimeMillis()+1500;
    private void refreshDrop(UUID id,Map<UUID,Long> active,long now){
        Drop drop=journal.get("lootEnd/"+id).isJsonNull()?journal.get("loot/"+id,Drop.class,null):null;
        if(drop==null||drop.expires<now){active.remove(id);return;}
        active.put(id,drop.expires);
    }
    private void ensureDropIndex(){
        if(dropIndexValid)return;
        // Cache repair is read-only; recover() also reconciles sessions and must not run here.
        Map<UUID,Long> active=new LinkedHashMap<>();
        long now=System.currentTimeMillis();
        for(String key:journal.keys("loot/"))refreshDrop(UUID.fromString(key.substring(5)),active,now);
        activeDrops=active;dropIndexValid=true;
    }
    private void expireDropIndex(){
        long now=System.currentTimeMillis();
        activeDrops.values().removeIf(expiry->expiry<now);
    }
    private List<UUID> activeDropIds(){ensureDropIndex();expireDropIndex();return List.copyOf(activeDrops.keySet());}
    @Override public void committed(Collection<String> journalKeys){
        if(!dropIndexValid)return;
        try{
            Set<UUID> changed=new LinkedHashSet<>();
            for(String key:journalKeys){
                if(key.startsWith("loot/"))changed.add(UUID.fromString(key.substring(5)));
                else if(key.startsWith("lootEnd/"))changed.add(UUID.fromString(key.substring(8)));
            }
            long now=System.currentTimeMillis();
            for(UUID id:changed)refreshDrop(id,activeDrops,now);
        }catch(RuntimeException failure){dropIndexValid=false;}
    }
    public Drop manual(ServerPlayer p,ItemStack stack,Vec3 pos){
        Drop d=new Drop();
        d.dimension=p.level().dimension().identifier().toString();
        d.x=pos.x;
        d.y=pos.y+.7;
        d.z=pos.z;
        d.item=inventory.get().encode(p,stack);
        return d;
    }
    public void publishDrops(Collection<Drop> drops){
        publish.addAll(drops);
        publish();
    }
    public LootService(Journal journal,cn.yumis.fight.modules.character.api.CharacterPersistence characters,MinecraftServer server,Supplier<InventoryOperations> inventory,RewardPorts.Players players,RewardPorts.Encounters encounters,RewardPorts.Balance balance){
        this.characters=characters;
        this.journal=journal;this.server=server;this.inventory=inventory;this.players=players;this.encounters=encounters;this.balance=balance;
    }
    @Override public InventoryDrops.Prepared prepare(ServerPlayer player,ItemEntity projection){
        Drop drop=new Drop();drop.id=projection.getUUID();drop.nativeToss=true;
        drop.dimension=player.level().dimension().identifier().toString();
        drop.x=projection.getX();drop.y=projection.getY();drop.z=projection.getZ();
        var motion=projection.getDeltaMovement();drop.dx=motion.x;drop.dy=motion.y;drop.dz=motion.z;
        var access=(cn.yumis.fight.mixin.ItemDropAccessor)projection;
        var thrower=access.ydnf$thrower();drop.thrower=thrower==null?"":thrower.getUUID().toString();
        drop.pickupAt=System.currentTimeMillis()+access.ydnf$pickupDelay()*50L;
        drop.item=inventory.get().encode(player,projection.getItem());
        return new InventoryDrops.Prepared(drop.id,new Vec3(drop.x,drop.y,drop.z),"loot/"+drop.id,JSON.toJsonTree(drop));
    }
    @Override public void publishPrepared(Collection<InventoryDrops.Prepared> drops){
        for(var drop:drops)publish.add(JSON.fromJson(drop.state(),Drop.class));
        publish();
    }
    private JsonElement encodeWorld(ItemStack stack){return ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow();}
    @Override public int collect(ItemEntity source,cn.yumis.fight.modules.inventory.api.PersistentContainer target,java.util.function.ToIntFunction<ItemStack> receive){
        if(!source.isAlive()||source.getItem().isEmpty()||source.level()!=target.getLevel()||source.getTarget()!=null)return 0;
        String id=nativeIdentity(source);
        try{
            if(id.isEmpty()){
                Drop adopted=new Drop();adopted.id=source.getUUID();adopted.nativeToss=true;adopted.logistics=true;
                adopted.dimension=source.level().dimension().identifier().toString();adopted.x=source.getX();adopted.y=source.getY();adopted.z=source.getZ();
                var velocity=source.getDeltaMovement();adopted.dx=velocity.x;adopted.dy=velocity.y;adopted.dz=velocity.z;adopted.item=encodeWorld(source.getItem());
                journal.put("loot/"+adopted.id,adopted);committed(List.of("loot/"+adopted.id));
                id=adopted.id.toString();source.getPersistentData().putString("ydnf_drop",id);
            }
            Drop original=journal.get("loot/"+id,Drop.class,null);
            if(original==null||!original.owner.isEmpty()||original.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull())return 0;
            ItemStack expected=decode(original),actual=source.getItem();
            if(!ItemStack.isSameItemSameComponents(expected,actual))return 0;
            ItemStack offered=actual.copyWithCount(Math.min(expected.getCount(),actual.getCount()));
            if(offered.isEmpty())return 0;
            // Ledger quantity is authoritative when a previous durable claim preceded its projection update.
            if(actual.getCount()>offered.getCount())source.setItem(offered.copy());
            final String identity=id;final int[] accepted={0};final ItemStack[] remainder={offered.copy()};
            Map<String,JsonElement> edits=new LinkedHashMap<>();JsonObject intent=new JsonObject();intent.addProperty("worldItem",id);intent.addProperty("container",target.key());
            inventory.get().transactContainer("world-intake/"+UUID.randomUUID(),intent,target,()->{
                accepted[0]=receive.applyAsInt(offered.copy());
                if(accepted[0]<0||accepted[0]>offered.getCount())throw new IllegalStateException("世界物品收取数量无效");
                remainder[0]=offered.copyWithCount(offered.getCount()-accepted[0]);
                Drop next=copy(original,Drop.class);next.logistics=true;next.item=encodeWorld(remainder[0]);
                edits.put("loot/"+identity,JSON.toJsonTree(next));
                if(remainder[0].isEmpty())edits.put("lootEnd/"+identity,JSON.toJsonTree("industrial-intake"));
            },edits);
            if(remainder[0].isEmpty())source.discard();else{source.setItem(remainder[0]);source.setUnlimitedLifetime();}
            return accepted[0];
        }catch(Exception failure){throw new IllegalStateException("地面物品接收未完成",failure);}
    }
    @Override public void spill(cn.yumis.fight.modules.inventory.api.PersistentContainer source,String operation,List<cn.yumis.fight.modules.economy.api.WorldItemTransfers.Emission> emissions,Runnable debit){
        List<Drop> drops=new ArrayList<>();Map<String,JsonElement> edits=new LinkedHashMap<>();JsonObject intent=new JsonObject();intent.addProperty("container",source.key());
        JsonArray identities=new JsonArray(),payload=new JsonArray();int index=0;
        for(var emission:emissions){
            if(emission.stack().isEmpty())continue;
            Drop drop=new Drop();drop.id=UUID.nameUUIDFromBytes((source.key()+"/"+operation+"/"+index++).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            drop.nativeToss=true;drop.logistics=true;drop.pickupAt=System.currentTimeMillis()+500;
            drop.dimension=source.getLevel().dimension().identifier().toString();drop.x=emission.position().x;drop.y=emission.position().y;drop.z=emission.position().z;
            drop.dx=emission.velocity().x;drop.dy=emission.velocity().y;drop.dz=emission.velocity().z;drop.item=encodeWorld(emission.stack());
            drops.add(drop);identities.add(drop.id.toString());edits.put("loot/"+drop.id,JSON.toJsonTree(drop));
            JsonObject emitted=new JsonObject();emitted.add("item",drop.item.deepCopy());emitted.addProperty("x",drop.x);emitted.addProperty("y",drop.y);emitted.addProperty("z",drop.z);emitted.addProperty("dx",drop.dx);emitted.addProperty("dy",drop.dy);emitted.addProperty("dz",drop.dz);payload.add(emitted);
        }
        intent.add("drops",identities);intent.add("payload",payload);
        try{inventory.get().transactContainer(operation,intent,source,debit,edits);}
        finally{
            // Retain committed emissions if their world projection needs another attempt.
            for(Drop drop:drops)if(!journal.get("loot/"+drop.id).isJsonNull()&&publish.stream().noneMatch(queued->queued.id.equals(drop.id)))publish.add(drop);
        }
        publish();
    }
    public void privateDrop(ServerPlayer p,ItemStack stack,Vec3 pos,String instance)throws Exception{
        create(p,stack,pos,instance,true);
    }
    /** Prepares a private reward for the current session's transaction. */
    public Drop preparePrivate(ServerPlayer player,ItemStack stack,ReturnPoint point,String instance){
        Drop d=new Drop();d.owner=player.getStringUUID();d.session=players.session(player).toString();d.dimension=point.dimension;d.instance=instance;d.x=point.x;d.y=point.y+.4;d.z=point.z;d.expires=Long.MAX_VALUE;
        d.item=ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow();return d;
    }
    public Drop prepareOverflow(ServerPlayer player,ItemStack stack){var point=new ReturnPoint();point.dimension=player.level().dimension().identifier().toString();point.x=player.getX();point.y=player.getY();point.z=player.getZ();return preparePrivate(player,stack,point,encounters.currentInstance(player));}
    public void publishCommittedDrops(Collection<String> keys){
        for(String key:keys)if(key.startsWith("loot/")){
            var drop=journal.get(key,Drop.class,null);if(drop!=null)publish.add(drop);
        }
        publish();
    }
    private void create(ServerPlayer p,ItemStack stack,Vec3 pos,String instance,boolean privateLoot)throws Exception{
        Drop d=new Drop();
        if(cn.yumis.fight.platform.ItemData.own(stack)&&Set.of("treasure_chest","waystone",cn.yumis.fight.modules.economy.api.LootJarItem.ID).contains(cn.yumis.fight.platform.ItemData.id(stack)))d.expires=Long.MAX_VALUE;
        if(privateLoot){
            d.owner=p.getStringUUID();
            d.session=players.session(p).toString();
        }
        d.dimension=p.level().dimension().identifier().toString();
        d.instance=instance;
        d.x=pos.x;
        d.y=pos.y+.4;
        d.z=pos.z;
        d.item=inventory.get().encode(p,stack);
        journal.put("loot/"+d.id,d);
        publish.add(d);
    }
    public void death(ServerPlayer killer,LivingEntity entity,Target target)throws Exception{
        if(target.abyss!=null&&target.noRewards)return;
        var encounter=target.instance.isEmpty()?null:encounters.byId(target.instance);
        if(encounter!=null&&encounter.practice!=null&&encounter.practice.kind()!=PracticeRoom.Kind.MOB){
            practiceDeath(killer,entity,target,encounter);
            return;
        }
        var nearby=new LinkedHashSet<UUID>();for(var player:server.getPlayerList().getPlayers())if(player.level()==entity.level()&&player.distanceToSqr(entity)<=4096)nearby.add(player.getUUID());
        String worldBossType=target.instance.isEmpty()&&target.boss()?BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString():null;
        var context=new RewardRules.Context(target.id+"/"+target.life.generation,killer.getUUID(),target,encounter,balance.settings(),entity.level().dimension().identifier().toString(),entity.getX(),entity.getY(),entity.getZ(),Set.copyOf(nearby),null,null,ritualSupply(target,encounter),null,worldBossType);
        var fact=JSON.toJsonTree(context);
        for(var player:server.getPlayerList().getPlayers())if(nearby.contains(player.getUUID())||player==killer){
            offerReward(player,context.id(),fact);
        }
    }

    private RewardRules.Supply ritualSupply(Target target,Dungeon encounter){
        if(encounter==null||encounter.treasure!=null||encounter.waystone!=null||!target.boss()||target.abyss!=null)return null;
        return switch(encounter.dungeon){
            case "skasa" -> ritualUnlocked.test("fallen_scale_king")?null:new RewardRules.Supply("warm_dragon_scale",1);
            case "gbl_church" -> ritualUnlocked.test("thousandfold_confession")?null:new RewardRules.Supply("penitent_shard",13);
            default -> null;
        };
    }

    private void practiceDeath(ServerPlayer killer,LivingEntity entity,Target target,Dungeon encounter)throws Exception{
        if(target.noRewards||!encounter.status.equals("RUNNING")||!encounter.enemies.contains(target.id))return;
        String id=target.id+"/"+target.life.generation;
        long seed=target.id.getMostSignificantBits()^target.id.getLeastSignificantBits();
        if(encounter.practice.kind()==PracticeRoom.Kind.GOLD){
            if(!encounters.finalPracticeKill(encounter,target.id))return;
            var present=server.getPlayerList().getPlayers().stream()
                .filter(player->player.level()==entity.level()&&encounters.participant(player,target.instance)).toList();
            for(var player:present){
                var practice=new RewardRules.PracticeReward(target.instance,encounter.practice,players.progress(player).level,present.size(),0,seed);
                offerReward(player,id,practiceFact(id,killer,entity,practice));
            }
        }else{
            double luck=encounter.practice.kind()==PracticeRoom.Kind.CURRENCY?players.stats(killer).get(Stat.DROP_RATE):0;
            var practice=new RewardRules.PracticeReward(target.instance,encounter.practice,0,0,luck,seed);
            // 范围内共享：所有在场参与者各自结算。
            for(var player:server.getPlayerList().getPlayers())if(player.level()==entity.level()&&encounters.participant(player,target.instance))offerReward(player,id,practiceFact(id,killer,entity,practice));
        }
    }

    private JsonElement practiceFact(String id,ServerPlayer killer,LivingEntity entity,RewardRules.PracticeReward practice){
        return JSON.toJsonTree(new RewardRules.Context(id,killer.getUUID(),null,null,balance.settings(),
            entity.level().dimension().identifier().toString(),entity.getX(),entity.getY(),entity.getZ(),Set.of(),practice));
    }

    private String rewardPrefix(ServerPlayer player){return "personalKill/"+player.getUUID()+"/"+players.session(player)+"/";}
    private void offerReward(ServerPlayer player,String id,JsonElement fact)throws Exception{
        if(player.hasDisconnected())return;
        journal.put(rewardPrefix(player)+id,fact);
        rewardNotices.add(new RewardNotice(player.getUUID(),fact));
    }

    private boolean practiceOpen(String instance){
        var room=encounters.byId(instance);
        return room!=null&&room.practice!=null&&room.status.equals("RUNNING");
    }

    public void closePractice(String instance)throws Exception{
        var edits=new LinkedHashMap<String,JsonElement>();
        for(UUID id:activeDropIds()){
            var drop=journal.get("loot/"+id,Drop.class,null);
            if(drop!=null&&drop.practice&&drop.instance.equals(instance)&&journal.get("lootEnd/"+id).isJsonNull()){
                edits.put("lootEnd/"+id,new JsonPrimitive("practice-closed"));
            }
        }
        if(edits.isEmpty())return;
        journal.transaction(edits);
        committed(edits.keySet());
    }
    public void receiveReward(ServerPlayer player,JsonObject request){
        String id=request.getAsJsonObject("context").get("id").getAsString();
        String key="personalKillDone/"+player.getUUID()+"/"+id,pending=rewardPrefix(player)+id;
        if(!journal.get(key).isJsonNull()){inventory.get().restore(player);players.progress(player,characters.read(player.getUUID()));players.profile(player);return;}
        var context=journal.get(pending,RewardRules.Context.class,null);
        if(context==null)throw new IllegalArgumentException("找不到待结算的个人奖励");
        if(request.has("unchanged")){try{journal.transaction(Map.of(key,request.deepCopy(),pending,JsonNull.INSTANCE));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}players.profile(player);return;}
        if(!JSON.toJsonTree(players.progress(player)).equals(request.get("beforeProgress")))throw new IllegalArgumentException("角色存储快照已变化");
        var progress=JSON.fromJson(request.get("progress"),Progress.class);var edits=new LinkedHashMap<>(characters.writes(player.getUUID(),progress));
        if(!context.experienceOnly())edits.putAll(balance.receive(player,request));
        edits.put(key,request.deepCopy());edits.put(pending,JsonNull.INSTANCE);
        var drops=new ArrayList<Drop>();
        var direct=new ArrayList<ItemStack>();
        var random=RandomSource.create();
        // Plain practice-room mobs settle like ordinary kills, but their ground drops keep the room's lifecycle.
        boolean practice=context.practice()!=null||context.encounter()!=null&&context.encounter().practice!=null;
        for(var raw:context.experienceOnly()||practice&&!practiceOpen(context.instance())?new JsonArray():request.getAsJsonArray("rewardDrops")){
            var row=raw.getAsJsonObject();
            var received=inventory.get().decode(player,row.get("item"));
            if(context.ritual()!=null||context.supply()!=null&&cn.yumis.fight.platform.ItemData.own(received)
                    &&context.supply().item().equals(cn.yumis.fight.platform.ItemData.id(received))){
                direct.add(received);
                continue;
            }
            var drop=new Drop();boolean privateLoot=row.get("privateLoot").getAsBoolean();
            if(privateLoot){drop.owner=player.getStringUUID();drop.session=players.session(player).toString();}
            drop.practice=practice;
            drop.dimension=context.dimension();drop.instance=context.instance();drop.x=context.x();drop.y=context.y()+.4;drop.z=context.z();drop.item=row.get("item").deepCopy();
            if(cn.yumis.fight.platform.ItemData.own(received)&&Set.of("treasure_chest","waystone",cn.yumis.fight.modules.economy.api.LootJarItem.ID).contains(cn.yumis.fight.platform.ItemData.id(received)))drop.expires=Long.MAX_VALUE;
            if(privateLoot&&!drop.instance.isEmpty()&&!drop.instance.equals(encounters.currentInstance(player))){drop.dimension=player.level().dimension().identifier().toString();drop.instance="";drop.x=player.getX();drop.y=player.getY()+.4;drop.z=player.getZ();}
            scatter(drop,random);
            drops.add(drop);edits.put("loot/"+drop.id,JSON.toJsonTree(drop));
        }
        try{inventory.get().transact(player,request.get("operation").getAsString(),request,()->{
            for(var item:direct)inventory.get().deliverOrStash(player,item,edits);
        },edits);}catch(RuntimeException failure){if(journal.get(key).equals(request))players.progress(player,characters.read(player.getUUID()));throw failure;}
        players.progress(player,progress);publishDrops(drops);players.profile(player);
        abyssFlash(player,context,drops);
        if(context.experienceOnly())players.message(player,"方舟战争结算："+context.experiencePercentages().size()+"波，获得"+request.get("experienceGain").getAsLong()+"点经验。");
        else if(request.has("goldGain"))balance.notifyGain(player,request.get("goldGain").getAsLong(),balance.gold(player),balance.revision(player));
    }
    /** 敌人死亡的地面掉落参考原版玩家死亡散落一地的做法散开：公共与私人掉落都带随机方向抛射速度，由各自实体的物理散开落地。 */
    public static void scatter(Drop drop,RandomSource random){
        // 原版 LivingEntity.createItemStackToDrop 的死亡散落算法：0~0.5 随机水平速度加 0.2 上抛；私人掉落拾取本就无延迟，公共掉落保持 10 tick 拾取窗口。
        double angle=random.nextDouble()*Math.PI*2,speed=random.nextDouble()*.5;
        drop.nativeToss=true;drop.dx=-Math.sin(angle)*speed;drop.dy=.2;drop.dz=Math.cos(angle)*speed;
        if(drop.owner.isEmpty())drop.pickupAt=System.currentTimeMillis()+500;
    }
    /** Presents committed facts only: an abyss clear whose landed loot contains epic or mythic gear flashes for the instance. */
    private void abyssFlash(ServerPlayer player,RewardRules.Context context,List<Drop> drops){
        if(context.target()==null||context.target().abyss!=AbyssParty.Enemy.PILLAR)return;
        var fact=abyssFlashFact(drops,item->EquipmentItems.gear(inventory.get().decode(player,item)));
        if(fact!=null)encounters.abyssFlash(fact.instance(),fact.x(),fact.y(),fact.z(),fact.mythic());
    }
    /** First in-instance epic or mythic landing spot, and whether any mythic gear is present; null means no flash. */
    public record AbyssFlashFact(String instance,boolean mythic,double x,double y,double z){}
    public static AbyssFlashFact abyssFlashFact(List<Drop> drops,Function<JsonElement,Gear> gearAt){
        boolean mythic=false;String instance="";double x=0,y=0,z=0;
        for(Drop drop:drops){
            if(drop.instance.isEmpty())continue;
            var gear=gearAt.apply(drop.item);
            if(gear==null||gear.rarity!=Rarity.EPIC&&gear.rarity!=Rarity.MYTHIC)continue;
            if(instance.isEmpty()){instance=drop.instance;x=drop.x;y=drop.y;z=drop.z;}
            mythic|=gear.rarity==Rarity.MYTHIC;
        }
        return instance.isEmpty()?null:new AbyssFlashFact(instance,mythic,x,y,z);
    }
    public void abort(){
        publish.removeIf(drop->!drop.logistics);
        rewardNotices.clear();
    }
    public void recover(){
        dropIndexValid=false;
        Map<UUID,Long> active=new LinkedHashMap<>();
        try{
            for(String key:journal.keys("loot/")){
                Drop d=journal.get(key,Drop.class,null);
                if(d==null||!journal.get("lootEnd/"+d.id).isJsonNull())continue;
                if(!d.owner.isEmpty()){journal.put(key,JsonNull.INSTANCE);continue;}
                if(d.expires<System.currentTimeMillis()){journal.put("lootEnd/"+d.id,"expired-session");continue;}
                if(d.expires!=Long.MAX_VALUE)recover.add(d);
                active.put(d.id,d.expires);
            }
            activeDrops=active;dropIndexValid=true;
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    private ItemStack decode(Drop d){
        return ItemStack.OPTIONAL_CODEC.parse(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),d.item).getOrThrow();
    }
    private ItemEntity projection(ServerLevel level,Drop drop,ItemStack stack){
        ItemEntity entity=new ItemEntity(level,drop.x,drop.y,drop.z,stack);
        entity.setUUID(drop.id);entity.getPersistentData().putString("ydnf_drop",drop.id.toString());
        if(drop.nativeToss){
            entity.setDeltaMovement(drop.dx,drop.dy,drop.dz);
            entity.setPickUpDelay((int)Math.clamp((drop.pickupAt-System.currentTimeMillis()+49)/50,0,32767));
            if(!drop.thrower.isEmpty())((cn.yumis.fight.mixin.ItemDropAccessor)entity).ydnf$thrower(EntityReference.of(UUID.fromString(drop.thrower)));
        }else entity.setDefaultPickUpDelay();
        // The ledger, rather than native merging/age, owns logistics projections and their expiry.
        if(drop.logistics||drop.practice)entity.setUnlimitedLifetime();
        return entity;
    }
    public void publish(){
        committed(publish.stream().map(drop->"loot/"+drop.id).toList());
        var queued=new LinkedHashMap<UUID,Drop>();for(Drop drop:publish)queued.put(drop.id,drop);publish.clear();
        for(Drop requested:queued.values()){
            Drop d=journal.get("loot/"+requested.id,Drop.class,null);
            if(d==null||!journal.get("lootEnd/"+requested.id).isJsonNull()||d.expires<System.currentTimeMillis()||d.practice&&!practiceOpen(d.instance))continue;
            if(!d.owner.isEmpty()&&!activeOwner(d,server.getPlayerList().getPlayer(UUID.fromString(d.owner))))continue;
            ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
            if(level==null){publish.add(d);continue;}
            if(d.logistics&&!level.hasChunkAt(net.minecraft.core.BlockPos.containing(d.x,d.y,d.z))){publish.add(d);continue;}
            ItemStack stack=decode(d);
            if(level.getEntity(d.id)!=null)continue;
            if(d.owner.isEmpty()){
                ItemEntity e=projection(level,d,stack);
                if(!level.addFreshEntity(e))publish.add(d);
            }
            else{
                PrivateLoot e=new PrivateLoot(cn.yumis.fight.modules.economy.api.RewardContentTypes.PRIVATE_LOOT.get(),level);
                e.setUUID(d.id);
                e.initialize(d.id.toString(),d.owner,stack);
                e.setPos(d.x,d.y,d.z);
                if(d.nativeToss)e.setDeltaMovement(d.dx,d.dy,d.dz);
                if(!level.addFreshEntity(e))publish.add(d);
            }
        }
        for(var notice:rewardNotices){
            var player=server.getPlayerList().getPlayer(notice.player);
            String id=notice.fact.getAsJsonObject().get("id").getAsString();
            if(player!=null&&!player.hasDisconnected()&&journal.get(rewardPrefix(player)+id).equals(notice.fact))
                cn.yumis.fight.network.Network.send(player,"killReward",notice.fact);
        }
        rewardNotices.clear();
    }
    private boolean activeOwner(Drop drop,ServerPlayer player){
        return player!=null&&!player.hasDisconnected()&&players.session(player).toString().equals(drop.session);
    }
    public void validate(PrivateLoot e){
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        ServerPlayer p=d==null?null:server.getPlayerList().getPlayer(UUID.fromString(d.owner));
        if(d==null||!activeOwner(d,p)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull()){
            e.discard();
            return;
        }
    }
    public void pickup(ServerPlayer p,PrivateLoot e){
        if(!p.getStringUUID().equals(e.owner())||inventory.get().locked(p.getUUID()))return;
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        if(d==null||!activeOwner(d,p)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull())return;
        ItemStack stack=inventory.get().decode(p,d.item);
        if(accountPickup(p,d,stack)){if(!journal.get("lootEnd/"+e.dropId).isJsonNull())e.discard();return;}
        int room=inventory.get().room(p,stack);
        int take=Math.min(room,stack.getCount());
        if(take<=0)return;
        ItemStack received=stack.copyWithCount(take);
        stack.shrink(take);
        Drop after=copy(d,Drop.class);
        after.item=inventory.get().encode(p,stack);
        JsonObject j=new JsonObject();
        j.addProperty("drop",e.dropId);
        j.addProperty("take",take);
        Map<String,JsonElement> changes=new HashMap<>();
        changes.put("loot/"+e.dropId,JSON.toJsonTree(after));
        if(stack.isEmpty())changes.put("lootEnd/"+e.dropId,JSON.toJsonTree("picked"));
        try{
            if(!inventory.get().transact(p,"pickup/"+UUID.randomUUID(),j,()->inventory.get().requireSpace(p,received),changes))return;
            players.groundPickup(p,received);
            var view=EquipmentItems.view(received);if(view!=null&&view.creativeTemplate())players.profile(p);
            if(stack.isEmpty())e.discard();
            else e.initialize(e.dropId,e.owner(),stack);
        }
        catch(Exception failure){
            players.message(p,failure.getMessage());
        }
    }
    /** Currency projections settle their balance and durable pickup marker atomically, even with a full bag. */
    private boolean accountPickup(ServerPlayer player,Drop drop,ItemStack actual){
        if(!cn.yumis.fight.platform.ItemData.own(actual)||!cn.yumis.fight.platform.ItemData.id(actual).equals(CurrencyRules.COLORLESS_CRYSTAL.id()))return false;
        try{
            ItemStack expected=inventory.get().decode(player,drop.item);
            if(drop.expires<System.currentTimeMillis()||actual.isEmpty()||!ItemStack.matches(expected,actual))return true;
            String key=CurrencyRules.COLORLESS_CRYSTAL.id()+"/"+player.getUUID();long balance=Math.addExact(journal.get(key,Long.class,0L),actual.getCount());
            JsonObject intent=new JsonObject();intent.addProperty("accountPickup",drop.id.toString());intent.addProperty("count",actual.getCount());
            inventory.get().transact(player,"account-pickup/"+drop.id,intent,()->{},Map.of(key,new JsonPrimitive(balance),"lootEnd/"+drop.id,new JsonPrimitive("picked")));
            players.profile(player);
        }catch(RuntimeException failure){players.message(player,failure.getMessage());}
        return true;
    }
    public void move(ServerPlayer p,String instance,ReturnPoint point){
        try{
            for(UUID id:activeDropIds()){
                String key="loot/"+id;
                Drop d=journal.get(key,Drop.class,null);
                if(d!=null&&d.owner.equals(p.getStringUUID())&&d.instance.equals(instance)&&journal.get("lootEnd/"+d.id).isJsonNull()){
                    for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof PrivateLoot loot&&loot.dropId.equals(d.id.toString()))e.discard();
                    d.dimension=point.dimension;
                    d.x=point.x;
                    d.y=point.y+.3;
                    d.z=point.z;
                    d.instance="";
                    d.nativeToss=false;d.dx=0;d.dy=0;d.dz=0;
                    journal.put(key,d);
                    committed(List.of(key));
                    publish.add(d);
                }
            }
        }
        catch(Exception failure){
            throw new IllegalStateException(failure);
        }
    }
    public void endSession(ServerPlayer p)throws Exception{
        var edits=new LinkedHashMap<String,JsonElement>();
        for(String key:journal.keys(rewardPrefix(p)))edits.put(key,JsonNull.INSTANCE);
        String session=players.session(p).toString();
        for(UUID id:activeDropIds()){
            Drop d=journal.get("loot/"+id,Drop.class,null);
            if(d!=null&&d.owner.equals(p.getStringUUID())&&d.session.equals(session))
                edits.put("loot/"+id,JsonNull.INSTANCE);
        }
        if(edits.isEmpty())return;
        journal.transaction(edits);
        committed(edits.keySet());
    }
    public void nativeLeft(ItemEntity entity){
        String id=nativeIdentity(entity);
        // Leaving the tracked entity set also occurs on chunk visibility changes with no removal reason.
        // Only actual destruction closes the durable drop; unloading must remain recoverable.
        var reason=entity.getRemovalReason();
        if(id.isEmpty()||reason==null||!reason.shouldDestroy())return;
        try{
            if(journal.get("lootEnd/"+id).isJsonNull())journal.put("lootEnd/"+id,"native-ended");
            committed(List.of("lootEnd/"+id));
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    public void tick(){
        if(!publish.isEmpty()||!rewardNotices.isEmpty())publish();
        if(System.currentTimeMillis()>=recoverAfter){
            for(int i=0;i<4&&!recover.isEmpty();i++){
                Drop queued=recover.removeFirst();Drop d=journal.get("loot/"+queued.id,Drop.class,null);
                if(d==null||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+d.id).isJsonNull()||d.practice&&!practiceOpen(d.instance))continue;
                ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
                if(level==null)continue;
                net.minecraft.core.BlockPos pos=net.minecraft.core.BlockPos.containing(d.x,d.y,d.z);
                level.getChunk(pos);
                if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(pos))){
                    recover.addLast(d);
                    continue;
                }
                Entity existing=level.getEntity(d.id);
                if(existing==null){
                    ItemEntity item=projection(level,d,decode(d));
                    if(!level.addFreshEntity(item))recover.addLast(d);
                }
                else if(existing instanceof ItemEntity item){
                    ItemStack expected=decode(d);
                    if(item.getItem().getCount()>expected.getCount())item.setItem(expected);
                }
            }
        }
        var practiceRoomsOpen=new HashMap<String,Boolean>();
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof ItemEntity item){
            String id=nativeIdentity(item);
            if(!id.isEmpty()){
                Drop d=journal.get("loot/"+id,Drop.class,null);
                if(d==null||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull()||d.practice&&!practiceRoomsOpen.computeIfAbsent(d.instance,this::practiceOpen))item.discard();
                else{
                    int old=decode(d).getCount();
                    if(item.getItem().getCount()>old)item.setItem(decode(d));
                    if(old!=item.getItem().getCount()||Math.abs(d.x-item.getX())>.5||Math.abs(d.y-item.getY())>.5||Math.abs(d.z-item.getZ())>.5||!d.dimension.equals(level.dimension().identifier().toString())){
                        d.item=ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),item.getItem()).getOrThrow();
                        d.x=item.getX();
                        d.y=item.getY();
                        d.z=item.getZ();
                        d.dimension=level.dimension().identifier().toString();
                        if(d.nativeToss){var velocity=item.getDeltaMovement();d.dx=velocity.x;d.dy=velocity.y;d.dz=velocity.z;}
                        try{
                            journal.put("loot/"+id,d);
                        }
                        catch(Exception e1){
                            throw new IllegalStateException(e1);
                        }
                    }
                }
            }
        }
    }
}

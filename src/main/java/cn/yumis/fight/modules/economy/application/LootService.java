package cn.yumis.fight.modules.economy.application;
import cn.yumis.fight.modules.economy.api.PrivateLoot;
import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.platform.ItemRegistry;
import cn.yumis.fight.modules.dungeons.api.DungeonCatalog;
import cn.yumis.fight.modules.equipment.api.EquipmentTemplates;
import cn.yumis.fight.modules.attributes.api.Stat;
import cn.yumis.fight.modules.equipment.api.Rarity;
import cn.yumis.fight.modules.equipment.api.Slot;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.economy.api.CurrencyRules;
import cn.yumis.fight.modules.economy.api.LootOdds;

import cn.yumis.fight.modules.economy.api.RewardPorts;
import cn.yumis.fight.modules.inventory.api.InventoryOperations;
import cn.yumis.fight.modules.inventory.api.InventoryDrops;
import cn.yumis.fight.modules.inventory.api.InventoryIntake;
import cn.yumis.fight.modules.inventory.api.InventoryPacking;
import cn.yumis.fight.foundation.Journal;
import net.minecraft.server.MinecraftServer;
import java.util.function.Supplier;
import cn.yumis.fight.*;

import cn.yumis.fight.modules.character.api.Progress;
import cn.yumis.fight.modules.equipment.api.Gear;
import cn.yumis.fight.modules.attributes.api.Stats;
import cn.yumis.fight.modules.dungeons.api.Target;
import cn.yumis.fight.modules.dungeons.api.ReturnPoint;
import com.google.gson.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public class LootService implements InventoryDrops {
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
            if(drop==null||drop.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull()){e.getItemEntity().discard();return;}
            ItemStack actual=e.getItemEntity().getItem(),expected=inventory.get().decode(p,drop.item);
            if(!ItemStack.isSameItemSameComponents(actual,expected))return;
            if(actual.getCount()>expected.getCount()){actual=expected.copy();e.getItemEntity().setItem(actual);}
            if(accountPickup(p,drop,actual)){if(!journal.get("lootEnd/"+id).isJsonNull())e.getItemEntity().discard();return;}int room=InventoryPacking.room(p,actual);
            int take=Math.min(room,actual.getCount());if(take<=0)return;ItemStack result=actual.copyWithCount(take),left=actual.copyWithCount(actual.getCount()-take);var next=copy(drop,LootService.Drop.class);next.item=inventory.get().encode(p,left);Map<String,JsonElement> edits=new HashMap<>();edits.put("loot/"+id,JSON.toJsonTree(next));if(left.isEmpty())edits.put("lootEnd/"+id,JSON.toJsonTree("picked"));JsonObject intent=new JsonObject();intent.addProperty("publicPickup",id);intent.addProperty("count",take);try{
                inventory.get().transact(p,"public-pickup/"+UUID.randomUUID(),intent,()->inventory.get().requireSpace(p,result),edits);if(left.isEmpty())e.getItemEntity().discard();else e.getItemEntity().setItem(left);
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
        public boolean durableChest;
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
    private final ArrayDeque<Drop> recover=new ArrayDeque<>();
    private Map<UUID,Long> activeDrops=new LinkedHashMap<>();
    private Set<UUID> persistentDrops=new LinkedHashSet<>();
    private boolean dropIndexValid;
    private long recoverAfter=System.currentTimeMillis()+1500;
    private void refreshDrop(UUID id,Map<UUID,Long> active,Set<UUID> persistent,long now){
        Drop drop=journal.get("lootEnd/"+id).isJsonNull()?journal.get("loot/"+id,Drop.class,null):null;
        if(drop==null||drop.expires<now){active.remove(id);persistent.remove(id);return;}
        active.put(id,drop.expires);
        if(drop.durableChest)persistent.add(id);else persistent.remove(id);
    }
    private void ensureDropIndex(){
        if(dropIndexValid)return;
        // Cache repair is read-only; recover() also reconciles sessions and must not run here.
        Map<UUID,Long> active=new LinkedHashMap<>();
        Set<UUID> persistent=new LinkedHashSet<>();
        long now=System.currentTimeMillis();
        for(String key:journal.keys("loot/"))refreshDrop(UUID.fromString(key.substring(5)),active,persistent,now);
        activeDrops=active;persistentDrops=persistent;dropIndexValid=true;
    }
    private void expireDropIndex(){
        long now=System.currentTimeMillis();
        activeDrops.entrySet().removeIf(entry->{if(entry.getValue()>=now)return false;persistentDrops.remove(entry.getKey());return true;});
    }
    private List<UUID> activeDropIds(){ensureDropIndex();expireDropIndex();return List.copyOf(activeDrops.keySet());}
    private List<UUID> persistentDropIds(){ensureDropIndex();expireDropIndex();return List.copyOf(persistentDrops);}
    @Override public void committed(Collection<String> journalKeys){
        if(!dropIndexValid)return;
        try{
            Set<UUID> changed=new LinkedHashSet<>();
            for(String key:journalKeys){
                if(key.startsWith("loot/"))changed.add(UUID.fromString(key.substring(5)));
                else if(key.startsWith("lootEnd/"))changed.add(UUID.fromString(key.substring(8)));
            }
            long now=System.currentTimeMillis();
            for(UUID id:changed)refreshDrop(id,activeDrops,persistentDrops,now);
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
    public void privateDrop(ServerPlayer p,ItemStack stack,Vec3 pos,String instance)throws Exception{
        create(p,stack,pos,instance,true);
    }
    /** Prepares a durable private reward without publishing or writing before its caller commits. */
    public Drop preparePersistent(UUID player,ItemStack stack,ReturnPoint point,String instance){
        Drop d=new Drop();d.owner=player.toString();d.dimension=point.dimension;d.instance=instance;d.x=point.x;d.y=point.y+.4;d.z=point.z;d.durableChest=true;d.expires=Long.MAX_VALUE;
        d.item=ItemStack.OPTIONAL_CODEC.encodeStart(server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),stack).getOrThrow();return d;
    }
    public Drop prepareOverflow(ServerPlayer player,ItemStack stack){var point=new ReturnPoint();point.dimension=player.level().dimension().identifier().toString();point.x=player.getX();point.y=player.getY();point.z=player.getZ();return preparePersistent(player.getUUID(),stack,point,encounters.currentInstance(player));}
    public void publishPersistent(){for(UUID id:persistentDropIds()){var d=journal.get("loot/"+id,Drop.class,null);if(d!=null&&d.durableChest&&journal.get("lootEnd/"+d.id).isJsonNull())publish.add(d);}publish();}
    private void create(ServerPlayer p,ItemStack stack,Vec3 pos,String instance,boolean privateLoot)throws Exception{
        Drop d=new Drop();
        d.durableChest=cn.yumis.fight.platform.ItemData.own(stack)&&Set.of("treasure_chest","waystone",cn.yumis.fight.modules.economy.api.LootJarItem.ID).contains(cn.yumis.fight.platform.ItemData.id(stack));if(d.durableChest)d.expires=Long.MAX_VALUE;
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
        var encounter=target.instance.isEmpty()?null:encounters.byId(target.instance);
        var nearby=new LinkedHashSet<UUID>();for(var player:server.getPlayerList().getPlayers())if(player.level()==entity.level()&&player.distanceToSqr(entity)<=4096)nearby.add(player.getUUID());
        var context=new cn.yumis.fight.modules.economy.api.RewardRules.Context(target.id+"/"+target.life.generation,killer.getUUID(),target,encounter,balance.settings(),entity.level().dimension().identifier().toString(),entity.getX(),entity.getY(),entity.getZ(),Set.copyOf(nearby));
        var fact=JsonParser.parseString(JSON.toJson(context));
        for(var player:server.getPlayerList().getPlayers())if(nearby.contains(player.getUUID())||player==killer){
            journal.put("personalKill/"+player.getUUID()+"/"+context.id(),fact);rewardNotices.add(new RewardNotice(player.getUUID(),fact));
        }
    }
    public void pendingRewards(ServerPlayer player){
        for(String key:journal.keys("personalKill/"+player.getUUID()+"/")){
            var context=journal.get(key);if(context.isJsonNull())continue;String id=context.getAsJsonObject().get("id").getAsString();
            if(journal.get("personalKillDone/"+player.getUUID()+"/"+id).isJsonNull())cn.yumis.fight.network.Network.send(player,"killReward",context);
        }
    }
    public void receiveReward(ServerPlayer player,JsonObject request){
        var context=JSON.fromJson(request.get("context"),cn.yumis.fight.modules.economy.api.RewardRules.Context.class);String key="personalKillDone/"+player.getUUID()+"/"+context.id();
        var previous=journal.get(key);if(!previous.isJsonNull()){if(!previous.equals(request))throw new IllegalArgumentException("奖励操作号冲突");inventory.get().restore(player);players.progress(player,characters.read(player.getUUID()));players.profile(player);return;}
        if(!journal.get("personalKill/"+player.getUUID()+"/"+context.id()).equals(request.get("context")))throw new IllegalArgumentException("世界死亡事实已变化");
        if(request.has("unchanged")){try{journal.transaction(Map.of(key,request.deepCopy(),"personalKill/"+player.getUUID()+"/"+context.id(),JsonNull.INSTANCE));}catch(java.io.IOException failure){throw new IllegalStateException(failure);}players.profile(player);return;}
        if(!JSON.toJsonTree(players.progress(player)).equals(request.get("beforeProgress")))throw new IllegalArgumentException("角色存储快照已变化");
        var progress=JSON.fromJson(request.get("progress"),Progress.class);var edits=new LinkedHashMap<>(characters.writes(player.getUUID(),progress));edits.putAll(balance.receive(player,request));edits.put(key,request.deepCopy());edits.put("personalKill/"+player.getUUID()+"/"+context.id(),JsonNull.INSTANCE);
        var drops=new ArrayList<Drop>();
        for(var raw:request.getAsJsonArray("rewardDrops")){
            var row=raw.getAsJsonObject();var drop=new Drop();boolean privateLoot=row.get("privateLoot").getAsBoolean();
            if(privateLoot){drop.owner=player.getStringUUID();drop.session=players.session(player).toString();}
            drop.dimension=context.dimension();drop.instance=context.target().instance;drop.x=context.x();drop.y=context.y()+.4;drop.z=context.z();drop.item=row.get("item").deepCopy();
            var stack=inventory.get().decode(player,drop.item);drop.durableChest=cn.yumis.fight.platform.ItemData.own(stack)&&Set.of("treasure_chest","waystone",cn.yumis.fight.modules.economy.api.LootJarItem.ID).contains(cn.yumis.fight.platform.ItemData.id(stack));if(drop.durableChest)drop.expires=Long.MAX_VALUE;
            if(privateLoot&&!drop.instance.isEmpty()&&!drop.instance.equals(encounters.currentInstance(player))){drop.dimension=player.level().dimension().identifier().toString();drop.instance="";drop.x=player.getX();drop.y=player.getY()+.4;drop.z=player.getZ();}
            drops.add(drop);edits.put("loot/"+drop.id,JSON.toJsonTree(drop));
        }
        try{inventory.get().transact(player,request.get("operation").getAsString(),request,()->{},edits);}catch(RuntimeException failure){if(journal.get(key).equals(request))players.progress(player,characters.read(player.getUUID()));throw failure;}
        players.progress(player,progress);publishDrops(drops);players.profile(player);if(request.has("goldGain"))balance.notifyGain(player,request.get("goldGain").getAsLong(),balance.gold(player),balance.revision(player));
    }
    public void abort(){
        publish.clear();
        rewardNotices.clear();
    }
    public void recover(){
        dropIndexValid=false;
        Map<UUID,Long> active=new LinkedHashMap<>();
        Set<UUID> persistent=new LinkedHashSet<>();
        try{
            for(String key:journal.keys("loot/")){
                Drop d=journal.get(key,Drop.class,null);
                if(d==null||!journal.get("lootEnd/"+d.id).isJsonNull())continue;
                if(d.durableChest){
                    if(!d.instance.isEmpty()){var encounter=encounters.byId(d.instance);var point=encounter==null?null:encounter.returns.get(d.owner);if(point!=null){d.dimension=point.dimension;d.x=point.x;d.y=point.y+.3;d.z=point.z;d.instance="";journal.put("loot/"+d.id,d);}}
                }
                else if(!d.owner.isEmpty()||d.expires<System.currentTimeMillis()){journal.put("lootEnd/"+d.id,"expired-session");continue;}
                else recover.add(d);
                if(d.expires>=System.currentTimeMillis()){active.put(d.id,d.expires);if(d.durableChest)persistent.add(d.id);}
            }
            activeDrops=active;persistentDrops=persistent;dropIndexValid=true;
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
        return entity;
    }
    public void publish(){
        committed(publish.stream().map(drop->"loot/"+drop.id).toList());
        for(Drop d:new ArrayList<>(publish)){
            ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
            if(level==null)continue;
            ServerPlayer player=d.owner.isEmpty()?server.getPlayerList().getPlayers().stream().findFirst().orElse(null):server.getPlayerList().getPlayer(UUID.fromString(d.owner));
            if(!d.owner.isEmpty()&&player==null)continue;
            ItemStack stack=decode(d);
            if(level.getEntity(d.id)!=null)continue;
            if(d.owner.isEmpty()){
                ItemEntity e=projection(level,d,stack);
                level.addFreshEntity(e);
            }
            else{
                PrivateLoot e=new PrivateLoot(cn.yumis.fight.modules.economy.api.RewardContentTypes.PRIVATE_LOOT.get(),level);
                e.setUUID(d.id);
                e.initialize(d.id.toString(),d.owner,stack);
                e.setPos(d.x,d.y,d.z);
                level.addFreshEntity(e);
            }
        }
        publish.clear();
        for(var notice:rewardNotices){var player=server.getPlayerList().getPlayer(notice.player);if(player!=null)cn.yumis.fight.network.Network.send(player,"killReward",notice.fact);}
        rewardNotices.clear();
    }
    public void validate(PrivateLoot e){
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        ServerPlayer p=d==null?null:server.getPlayerList().getPlayer(UUID.fromString(d.owner));
        if(d==null||p==null||!d.durableChest&&!players.session(p).toString().equals(d.session)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull()){
            e.discard();
            return;
        }
    }
    public void pickup(ServerPlayer p,PrivateLoot e){
        if(!p.getStringUUID().equals(e.owner())||inventory.get().locked(p.getUUID()))return;
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        if(d==null||!d.durableChest&&!players.session(p).toString().equals(d.session)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull())return;
        ItemStack stack=inventory.get().decode(p,d.item);
        if(accountPickup(p,d,stack)){if(!journal.get("lootEnd/"+e.dropId).isJsonNull())e.discard();return;}
        int room=InventoryPacking.room(p,stack);
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
            inventory.get().transact(p,"pickup/"+UUID.randomUUID(),j,()->inventory.get().requireSpace(p,received),changes);
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
        for(UUID id:activeDropIds()){
            Drop d=journal.get("loot/"+id,Drop.class,null);
            if(d!=null&&!d.durableChest&&d.owner.equals(p.getStringUUID())&&d.session.equals(players.session(p).toString())){
                String key="lootEnd/"+id;journal.put(key,"session-ended");committed(List.of(key));
            }
        }
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
        for(UUID id:persistentDropIds()){
            Drop d=journal.get("loot/"+id,Drop.class,null);
            if(d==null||!d.durableChest||!journal.get("lootEnd/"+id).isJsonNull()){committed(List.of("loot/"+id));continue;}
            if(d.owner.isEmpty())continue;
            ServerPlayer p=server.getPlayerList().getPlayer(UUID.fromString(d.owner));if(p==null)continue;
            ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
            if(level!=null&&level.getEntity(d.id)==null)publish.add(d);
        }
        if(!publish.isEmpty())publish();
        if(System.currentTimeMillis()>=recoverAfter){
            for(int i=0;i<4&&!recover.isEmpty();i++){
                Drop d=recover.removeFirst();
                if(d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+d.id).isJsonNull())continue;
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
                    level.addFreshEntity(item);
                }
                else if(existing instanceof ItemEntity item){
                    ItemStack expected=decode(d);
                    if(item.getItem().getCount()>expected.getCount())item.setItem(expected);
                }
            }
        }
        for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof ItemEntity item){
            String id=nativeIdentity(item);
            if(!id.isEmpty()){
                Drop d=journal.get("loot/"+id,Drop.class,null);
                if(d==null||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull())item.discard();
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

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
    private static final com.google.gson.Gson JSON=new com.google.gson.GsonBuilder().disableHtmlEscaping().create();
    private static <T>T copy(T value,Class<T> type){return JSON.fromJson(JSON.toJson(value),type);}

    public void nativePickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre e){
            if(!(e.getPlayer() instanceof ServerPlayer p)||e.canPickup().isFalse())return;String id=e.getItemEntity().getPersistentData().getStringOr("ydnf_drop","");if(id.isEmpty())return;e.setCanPickup(net.minecraft.util.TriState.FALSE);
            // NeoForge fires Pre before vanilla checks the delay/target. Keep both checks before our transaction.
            if(e.getItemEntity().hasPickUpDelay()||e.getItemEntity().getTarget()!=null&&!p.getUUID().equals(e.getItemEntity().getTarget())||inventory.get().locked(p.getUUID()))return;
            LootService.Drop drop=journal.get("loot/"+id,LootService.Drop.class,null);if(drop==null||!journal.get("lootEnd/"+id).isJsonNull())return;ItemStack actual=e.getItemEntity().getItem();int room=0;for(int i=0;i<36;i++){
                ItemStack s=p.getInventory().getItem(i);room+=s.isEmpty()?actual.getMaxStackSize():ItemStack.isSameItemSameComponents(s,actual)?Math.max(0,s.getMaxStackSize()-s.getCount()):0;
            }
            int take=Math.min(room,actual.getCount());if(take<=0)return;ItemStack result=actual.copyWithCount(take),left=actual.copyWithCount(actual.getCount()-take);var next=copy(drop,LootService.Drop.class);next.item=inventory.get().encode(p,left);Map<String,JsonElement> edits=new HashMap<>();edits.put("loot/"+id,JSON.toJsonTree(next));if(left.isEmpty())edits.put("lootEnd/"+id,JSON.toJsonTree("picked"));JsonObject intent=new JsonObject();intent.addProperty("publicPickup",id);intent.addProperty("count",take);try{
                inventory.get().transact(p,"public-pickup/"+UUID.randomUUID(),intent,()->inventory.get().requireSpace(p,result),edits);if(left.isEmpty())e.getItemEntity().discard();else e.getItemEntity().setItem(left);
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
    private final Random random;
    private final List<Drop> publish=new ArrayList<>();
    private final Set<UUID> progressChanged=new HashSet<>();
    private record GoldGain(UUID player,long amount,long balance,long revision){}
    private final List<GoldGain> goldGains=new ArrayList<>();
    private final ArrayDeque<Drop> recover=new ArrayDeque<>();
    private long recoverAfter=System.currentTimeMillis()+1500;
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
    public LootService(Journal journal,MinecraftServer server,Supplier<InventoryOperations> inventory,RewardPorts.Players players,RewardPorts.Encounters encounters,RewardPorts.Balance balance,Random random){
        this.journal=journal;this.server=server;this.inventory=inventory;this.players=players;this.encounters=encounters;this.balance=balance;this.random=random;
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
    private void create(ServerPlayer p,ItemStack stack,Vec3 pos,String instance,boolean privateLoot)throws Exception{
        Drop d=new Drop();
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
        if(target.noRewards)return;
        boolean instance=!target.instance.isEmpty(),boss=!target.kind.equals("NORMAL");
        List<ServerPlayer> candidates=instance?server.getPlayerList().getPlayers().stream().filter(p->p.level()==entity.level()&&p.distanceToSqr(entity)<=4096&&encounters.participant(p,target.instance)).toList():List.of(killer);
        for(ServerPlayer p:candidates){
            Progress progress=copy(players.progress(p),Progress.class);
            Stats stats=players.stats(p);
            int level=target.level(progress.level,instance?encounters.byId(target.instance).syncFor(p.getUUID()):progress.sync);
            long xp=CurrencyRules.roundedReward((10+2*level)*(boss?10:target.elite?3:1)*target.difficulty.xp*balance.settings().combatExperienceMultiplier()*stats.get(Stat.EXPERIENCE_GAIN));
            progress.grant(xp);
            long gold=CurrencyRules.killGold(Math.clamp(level,1,80),target.elite,boss,target.difficulty,balance.settings().killGoldMultiplier());
            if(gold>0){long balance=Math.addExact(this.balance.gold(p),gold),revision=Math.incrementExact(this.balance.revision(p));journal.put("gold/"+p.getUUID(),balance);journal.put("goldRevision/"+p.getUUID(),revision);goldGains.add(new GoldGain(p.getUUID(),gold,balance,revision));}
            journal.put("player/"+p.getUUID(),progress);
            progressChanged.add(p.getUUID());
            int count=boss?3:random.nextDouble()<(target.elite?.45:.05)?1:0;
            for(int i=0;i<count;i++){
                Rarity rarity=LootOdds.rarity(target.difficulty,boss,boss&&!instance,stats.get(Stat.DROP_RATE),random);
                List<Slot> eligible=new ArrayList<>(List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING));
                for(Slot s:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))if(progress.level>=s.minimum()&&level>=s.minimum())eligible.add(s);
                double group=random.nextDouble();
                Slot slot=rarity==Rarity.MYTHIC?(group<.5?Slot.WEAPON:eligible.get(random.nextInt(eligible.size()))):group<.2?Slot.WEAPON:group<.6?Slot.values()[1+random.nextInt(4)]:eligible.get(random.nextInt(eligible.size()));
                Gear gear=EquipmentTemplates.createStandard(slot,Weapon.values()[random.nextInt(5)],rarity,Math.clamp(level,1,80),random);
                gear.source=target.life.target+"/"+target.life.generation;
                create(p,EquipmentItems.gear(gear),entity.position(),target.instance,instance);
            }
            var currency=CurrencyRules.roll(stats.get(Stat.DROP_RATE),random);
            if(currency!=null)create(p,ItemRegistry.item(currency.id(),1),entity.position(),target.instance,instance);
            if(instance){
                DungeonCatalog.DungeonDef def=DungeonCatalog.current().dungeon(encounters.byId(target.instance).dungeon);
                int rolls=boss?4:target.elite?2:random.nextDouble()<.2?1:0;
                for(int i=0;i<rolls;i++)create(p,EquipmentItems.material(def.materials().get(random.nextInt(def.materials().size())),1),entity.position(),target.instance,true);
            }
        }
    }
    public void abort(){
        publish.clear();
        progressChanged.clear();
        goldGains.clear();
    }
    public void recover(){
        try{
            for(String key:journal.keys("loot/")){
                Drop d=journal.get(key,Drop.class,null);
                if(d==null||!journal.get("lootEnd/"+d.id).isJsonNull())continue;
                if(!d.owner.isEmpty()||d.expires<System.currentTimeMillis())journal.put("lootEnd/"+d.id,"expired-session");
                else recover.add(d);
            }
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
                e.initialize(d.id.toString(),d.owner,stack);
                e.setPos(d.x,d.y,d.z);
                level.addFreshEntity(e);
            }
        }
        publish.clear();
        for(UUID id:progressChanged){
            ServerPlayer p=server.getPlayerList().getPlayer(id);
            if(p!=null){
                players.progress(p,journal.get("player/"+p.getUUID(),Progress.class,players.progress(p)));
                players.profile(p);
            }
        }
        progressChanged.clear();
        for(GoldGain gain:goldGains){ServerPlayer p=server.getPlayerList().getPlayer(gain.player);if(p!=null)balance.notifyGain(p,gain.amount,gain.balance,gain.revision);}
        goldGains.clear();
    }
    public void validate(PrivateLoot e){
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        ServerPlayer p=d==null?null:server.getPlayerList().getPlayer(UUID.fromString(d.owner));
        if(d==null||p==null||!players.session(p).toString().equals(d.session)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull()){
            e.discard();
            return;
        }
    }
    public void pickup(ServerPlayer p,PrivateLoot e){
        if(!p.getStringUUID().equals(e.owner())||inventory.get().locked(p.getUUID()))return;
        Drop d=journal.get("loot/"+e.dropId,Drop.class,null);
        if(d==null||!players.session(p).toString().equals(d.session)||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+e.dropId).isJsonNull())return;
        ItemStack stack=inventory.get().decode(p,d.item);
        int room=0;
        for(int i=0;i<36;i++){
            ItemStack s=p.getInventory().getItem(i);
            if(s.isEmpty())room+=stack.getMaxStackSize();
            else if(ItemStack.isSameItemSameComponents(s,stack))room+=Math.max(0,s.getMaxStackSize()-s.getCount());
        }
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
            if(stack.isEmpty())e.discard();
            else e.initialize(e.dropId,e.owner(),stack);
        }
        catch(Exception failure){
            players.message(p,failure.getMessage());
        }
    }
    public void move(ServerPlayer p,String instance,ReturnPoint point){
        try{
            for(String key:journal.keys("loot/")){
                Drop d=journal.get(key,Drop.class,null);
                if(d!=null&&d.owner.equals(p.getStringUUID())&&d.instance.equals(instance)&&journal.get("lootEnd/"+d.id).isJsonNull()){
                    for(ServerLevel level:server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof PrivateLoot loot&&loot.dropId.equals(d.id.toString()))e.discard();
                    d.dimension=point.dimension;
                    d.x=point.x;
                    d.y=point.y+.3;
                    d.z=point.z;
                    d.instance="";
                    journal.put(key,d);
                    publish.add(d);
                }
            }
        }
        catch(Exception failure){
            throw new IllegalStateException(failure);
        }
    }
    public void endSession(ServerPlayer p)throws Exception{
        for(String key:journal.keys("loot/")){
            Drop d=journal.get(key,Drop.class,null);
            if(d!=null&&d.owner.equals(p.getStringUUID())&&d.session.equals(players.session(p).toString()))journal.put("lootEnd/"+d.id,"session-ended");
        }
    }
    public void nativeLeft(ItemEntity entity){
        String id=entity.getPersistentData().getStringOr("ydnf_drop","");
        // Leaving the tracked entity set also occurs on chunk visibility changes with no removal reason.
        // Only actual destruction closes the durable drop; unloading must remain recoverable.
        var reason=entity.getRemovalReason();
        if(id.isEmpty()||reason==null||!reason.shouldDestroy())return;
        try{
            if(journal.get("lootEnd/"+id).isJsonNull())journal.put("lootEnd/"+id,"native-ended");
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    public void tick(){
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
            String id=item.getPersistentData().getStringOr("ydnf_drop","");
            if(!id.isEmpty()){
                Drop d=journal.get("loot/"+id,Drop.class,null);
                if(d==null||d.expires<System.currentTimeMillis()||!journal.get("lootEnd/"+id).isJsonNull())item.discard();
                else{
                    int old=decode(d).getCount();
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

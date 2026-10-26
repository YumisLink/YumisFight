package cn.yumis.fight.server;
import cn.yumis.fight.*;
import cn.yumis.fight.game.*;
import cn.yumis.fight.rules.*;
import static cn.yumis.fight.rules.Models.*;
import static cn.yumis.fight.rules.Rules.*;
import com.google.gson.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;
public final class LootService {
    public static class Drop {
        public UUID id=UUID.randomUUID();
        public String owner="",session="",dimension="",instance="";
        public double x,y,z;
        public long expires=System.currentTimeMillis()+300000;
        public JsonElement item;
    }
    private final ServerRuntime r;
    private final List<Drop> publish=new ArrayList<>();
    private final Set<UUID> progressChanged=new HashSet<>();
    private final ArrayDeque<Drop> recover=new ArrayDeque<>();
    private long recoverAfter=System.currentTimeMillis()+1500;
    public Drop manual(ServerPlayer p,ItemStack stack,Vec3 pos){
        Drop d=new Drop();
        d.dimension=p.level().dimension().identifier().toString();
        d.x=pos.x;
        d.y=pos.y+.7;
        d.z=pos.z;
        d.item=r.inventory.encode(p,stack);
        return d;
    }
    public void publish(Collection<Drop> drops){
        publish.addAll(drops);
        publish();
    }
    public LootService(ServerRuntime r){
        this.r=r;
    }
    public void privateDrop(ServerPlayer p,ItemStack stack,Vec3 pos,String instance)throws Exception{
        create(p,stack,pos,instance,true);
    }
    private void create(ServerPlayer p,ItemStack stack,Vec3 pos,String instance,boolean privateLoot)throws Exception{
        Drop d=new Drop();
        if(privateLoot){
            d.owner=p.getStringUUID();
            d.session=r.session(p).id.toString();
        }
        d.dimension=p.level().dimension().identifier().toString();
        d.instance=instance;
        d.x=pos.x;
        d.y=pos.y+.4;
        d.z=pos.z;
        d.item=r.inventory.encode(p,stack);
        r.journal.put("loot/"+d.id,d);
        publish.add(d);
    }
    public void death(ServerPlayer killer,LivingEntity entity,Target target)throws Exception{
        if(target.noRewards)return;
        boolean instance=!target.instance.isEmpty(),boss=!target.kind.equals("NORMAL");
        List<ServerPlayer> candidates=instance?r.server.getPlayerList().getPlayers().stream().filter(p->p.level()==entity.level()&&p.distanceToSqr(entity)<=4096&&r.dungeons.participant(p,target.instance)).toList():List.of(killer);
        for(ServerPlayer p:candidates){
            var session=r.session(p);
            Progress progress=copy(session.progress,Progress.class);
            int level=target.level(progress.level,instance?r.dungeons.byId(target.instance).sync:progress.sync);
            long xp=Math.round((10+2*level)*(boss?10:target.elite?3:1)*target.difficulty.xp);
            progress.grant(xp);
            if(p==killer&&progress.questAccepted&&!progress.swordmaster&&level>=20){
                progress.questKills++;
                if(target.elite)progress.questElites++;
            }
            r.journal.put("player/"+p.getUUID(),progress);
            progressChanged.add(p.getUUID());
            Stats stats=r.stats(p);
            int count=boss?3:r.random.nextDouble()<(target.elite?.45:.05)?1:0;
            for(int i=0;i<count;i++){
                Rarity rarity=CombatRules.rarity(target.difficulty,boss,boss&&!instance,stats.get(Stat.DROP_RATE),r.random);
                List<Slot> eligible=new ArrayList<>(List.of(Slot.BRACELET,Slot.NECKLACE,Slot.RING));
                for(Slot s:List.of(Slot.SUB_EQUIPMENT,Slot.MAGIC_STONE,Slot.EARRING))if(progress.level>=s.minimum()&&level>=s.minimum())eligible.add(s);
                double group=r.random.nextDouble();
                Slot slot=rarity==Rarity.MYTHIC?(group<.5?Slot.WEAPON:eligible.get(r.random.nextInt(eligible.size()))):group<.2?Slot.WEAPON:group<.6?Slot.values()[1+r.random.nextInt(4)]:eligible.get(r.random.nextInt(eligible.size()));
                Gear gear=EquipmentRules.create(slot,Weapon.values()[r.random.nextInt(5)],rarity,Math.clamp(level,1,80),r.random);
                gear.source=target.life.target+"/"+target.life.generation;
                create(p,GameItems.gear(gear),entity.position(),target.instance,instance);
            }
            if(r.random.nextDouble()<.05){
                double[] weights={
                    50,25,15,7,3
                }
                ;
                double sum=0;
                for(int i=0;i<5;i++){
                    weights[i]*=Math.pow(1+Math.max(0,stats.get(Stat.DROP_RATE))/100,i);
                    sum+=weights[i];
                }
                double pick=r.random.nextDouble()*sum;
                int index=0;
                while(pick>=weights[index])pick-=weights[index++];
                create(p,GameItems.item(List.of("transmutation_stone","augmentation_stone","regal_stone","exalted_stone","divine_stone").get(index),1),entity.position(),target.instance,instance);
            }
            if(instance){
                Catalog.DungeonDef def=Catalog.CURRENT.dungeon(r.dungeons.byId(target.instance).dungeon);
                int rolls=boss?4:target.elite?2:r.random.nextDouble()<.2?1:0;
                for(int i=0;i<rolls;i++)create(p,GameItems.material(def.materials().get(r.random.nextInt(def.materials().size())),1),entity.position(),target.instance,true);
            }
        }
    }
    public void abort(){
        publish.clear();
        progressChanged.clear();
    }
    public void recover(){
        try{
            for(String key:r.journal.keys("loot/")){
                Drop d=r.journal.get(key,Drop.class,null);
                if(d==null||!r.journal.get("lootEnd/"+d.id).isJsonNull())continue;
                if(!d.owner.isEmpty()||d.expires<System.currentTimeMillis())r.journal.put("lootEnd/"+d.id,"expired-session");
                else recover.add(d);
            }
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    private ItemStack decode(Drop d){
        return ItemStack.OPTIONAL_CODEC.parse(r.server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),d.item).getOrThrow();
    }
    public void publish(){
        for(Drop d:new ArrayList<>(publish)){
            ServerLevel level=r.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
            if(level==null)continue;
            ServerPlayer player=d.owner.isEmpty()?r.server.getPlayerList().getPlayers().stream().findFirst().orElse(null):r.server.getPlayerList().getPlayer(UUID.fromString(d.owner));
            if(!d.owner.isEmpty()&&player==null)continue;
            ItemStack stack=decode(d);
            if(level.getEntity(d.id)!=null)continue;
            if(d.owner.isEmpty()){
                ItemEntity e=new ItemEntity(level,d.x,d.y,d.z,stack);
                e.setUUID(d.id);
                e.getPersistentData().putString("ydnf_drop",d.id.toString());
                e.setDefaultPickUpDelay();
                level.addFreshEntity(e);
            }
            else{
                PrivateLoot e=new PrivateLoot(FightMod.PRIVATE_LOOT.get(),level);
                e.initialize(d.id.toString(),d.owner,stack);
                e.setPos(d.x,d.y,d.z);
                level.addFreshEntity(e);
            }
        }
        publish.clear();
        for(UUID id:progressChanged){
            ServerPlayer p=r.server.getPlayerList().getPlayer(id);
            if(p!=null){
                r.session(p).progress=r.journal.get("player/"+p.getUUID(),Progress.class,r.session(p).progress);
                r.profile(p);
            }
        }
        progressChanged.clear();
    }
    public void validate(PrivateLoot e){
        Drop d=r.journal.get("loot/"+e.dropId,Drop.class,null);
        ServerPlayer p=d==null?null:r.server.getPlayerList().getPlayer(UUID.fromString(d.owner));
        if(d==null||p==null||!r.session(p).id.toString().equals(d.session)||d.expires<System.currentTimeMillis()||!r.journal.get("lootEnd/"+e.dropId).isJsonNull()){
            e.discard();
            return;
        }
    }
    public void pickup(ServerPlayer p,PrivateLoot e){
        if(!p.getStringUUID().equals(e.owner())||r.inventory.locked(p.getUUID()))return;
        Drop d=r.journal.get("loot/"+e.dropId,Drop.class,null);
        if(d==null||!r.session(p).id.toString().equals(d.session)||d.expires<System.currentTimeMillis()||!r.journal.get("lootEnd/"+e.dropId).isJsonNull())return;
        ItemStack stack=r.inventory.decode(p,d.item);
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
        after.item=r.inventory.encode(p,stack);
        JsonObject j=new JsonObject();
        j.addProperty("drop",e.dropId);
        j.addProperty("take",take);
        Map<String,JsonElement> changes=new HashMap<>();
        changes.put("loot/"+e.dropId,JSON.toJsonTree(after));
        if(stack.isEmpty())changes.put("lootEnd/"+e.dropId,JSON.toJsonTree("picked"));
        try{
            r.inventory.transact(p,"pickup/"+UUID.randomUUID(),j,()->r.inventory.requireSpace(p,received),changes);
            if(stack.isEmpty())e.discard();
            else e.initialize(e.dropId,e.owner(),stack);
        }
        catch(Exception failure){
            r.message(p,failure.getMessage());
        }
    }
    public void move(ServerPlayer p,String instance,ReturnPoint point){
        try{
            for(String key:r.journal.keys("loot/")){
                Drop d=r.journal.get(key,Drop.class,null);
                if(d!=null&&d.owner.equals(p.getStringUUID())&&d.instance.equals(instance)&&r.journal.get("lootEnd/"+d.id).isJsonNull()){
                    for(ServerLevel level:r.server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof PrivateLoot loot&&loot.dropId.equals(d.id.toString()))e.discard();
                    d.dimension=point.dimension;
                    d.x=point.x;
                    d.y=point.y+.3;
                    d.z=point.z;
                    d.instance="";
                    r.journal.put(key,d);
                    publish.add(d);
                }
            }
        }
        catch(Exception failure){
            throw new IllegalStateException(failure);
        }
    }
    public void endSession(ServerPlayer p)throws Exception{
        for(String key:r.journal.keys("loot/")){
            Drop d=r.journal.get(key,Drop.class,null);
            if(d!=null&&d.owner.equals(p.getStringUUID())&&d.session.equals(r.session(p).id.toString()))r.journal.put("lootEnd/"+d.id,"session-ended");
        }
    }
    public void nativeLeft(ItemEntity entity){
        String id=entity.getPersistentData().getStringOr("ydnf_drop","");
        if(id.isEmpty()||entity.getRemovalReason()==Entity.RemovalReason.UNLOADED_TO_CHUNK||entity.getRemovalReason()==Entity.RemovalReason.CHANGED_DIMENSION)return;
        try{
            if(r.journal.get("lootEnd/"+id).isJsonNull())r.journal.put("lootEnd/"+id,"native-ended");
        }
        catch(Exception e){
            throw new IllegalStateException(e);
        }
    }
    public void tick(){
        if(System.currentTimeMillis()>=recoverAfter){
            for(int i=0;i<4&&!recover.isEmpty();i++){
                Drop d=recover.removeFirst();
                if(d.expires<System.currentTimeMillis()||!r.journal.get("lootEnd/"+d.id).isJsonNull())continue;
                ServerLevel level=r.server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.Identifier.parse(d.dimension)));
                if(level==null)continue;
                net.minecraft.core.BlockPos pos=net.minecraft.core.BlockPos.containing(d.x,d.y,d.z);
                level.getChunk(pos);
                if(!level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.pack(pos))){
                    recover.addLast(d);
                    continue;
                }
                Entity existing=level.getEntity(d.id);
                if(existing==null){
                    ItemEntity item=new ItemEntity(level,d.x,d.y,d.z,decode(d));
                    item.setUUID(d.id);
                    item.getPersistentData().putString("ydnf_drop",d.id.toString());
                    item.setDefaultPickUpDelay();
                    level.addFreshEntity(item);
                }
                else if(existing instanceof ItemEntity item){
                    ItemStack expected=decode(d);
                    if(item.getItem().getCount()>expected.getCount())item.setItem(expected);
                }
            }
        }
        for(ServerLevel level:r.server.getAllLevels())for(Entity e:level.getAllEntities())if(e instanceof ItemEntity item){
            String id=item.getPersistentData().getStringOr("ydnf_drop","");
            if(!id.isEmpty()){
                Drop d=r.journal.get("loot/"+id,Drop.class,null);
                if(d==null||d.expires<System.currentTimeMillis()||!r.journal.get("lootEnd/"+id).isJsonNull())item.discard();
                else{
                    int old=decode(d).getCount();
                    if(old!=item.getItem().getCount()||Math.abs(d.x-item.getX())>.5||Math.abs(d.y-item.getY())>.5||Math.abs(d.z-item.getZ())>.5||!d.dimension.equals(level.dimension().identifier().toString())){
                        d.item=ItemStack.OPTIONAL_CODEC.encodeStart(r.server.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),item.getItem()).getOrThrow();
                        d.x=item.getX();
                        d.y=item.getY();
                        d.z=item.getZ();
                        d.dimension=level.dimension().identifier().toString();
                        try{
                            r.journal.put("loot/"+id,d);
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

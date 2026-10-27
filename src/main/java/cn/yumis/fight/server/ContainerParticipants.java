package cn.yumis.fight.server;
import cn.yumis.fight.game.*;
import cn.yumis.fight.mixin.CompoundContainerAccessor;
import com.google.gson.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import java.util.*;
import static cn.yumis.fight.rules.Models.JSON;
/** Adds real external inventories and temporary input escrow to the player's WAL transaction. */
final class ContainerParticipants {
    record Address(String kind,String dimension,String owner,String type,int x,int y,int z,int slot){}
    record Change(Address address,JsonElement before,JsonElement after){}
    record Pending(String player,String operation,List<Change> changes){}
    record Escrow(int menu,List<JsonElement> items){}
    record Ref(Container container,int slot,Address address,JsonElement before,boolean temporary){}
    record MachineRef(cn.yumis.fight.industry.MachineBlockEntity machine,Address address,JsonElement before){}
    record Capture(List<Ref> refs,List<MachineRef> machines){}
    record After(List<JsonElement> values,List<Change> persistent,Escrow escrow,boolean hasTemporary,List<JsonElement> metadata){}
    private final ServerRuntime runtime;
    private final InventoryTransactions inventory;
    ContainerParticipants(ServerRuntime runtime,InventoryTransactions inventory){this.runtime=runtime;this.inventory=inventory;}
    private String escrowKey(ServerPlayer player){return "containerTransient/"+player.getUUID();}
    private static boolean generated(Slot slot){return slot instanceof ResultSlot||slot instanceof MerchantResultSlot||slot instanceof NonInteractiveResultSlot||slot.container instanceof ResultContainer;}
    private static boolean temporary(AbstractContainerMenu menu){
        return menu instanceof AbstractCraftingMenu||menu instanceof ItemCombinerMenu||menu instanceof EnchantmentMenu||menu instanceof GrindstoneMenu||menu instanceof CartographyTableMenu||menu instanceof LoomMenu||menu instanceof StonecutterMenu||menu instanceof MerchantMenu||menu instanceof BeaconMenu;
    }
    Capture capture(ServerPlayer player){return capture(player,null);}
    Capture capture(ServerPlayer player,cn.yumis.fight.industry.MachineBlockEntity explicit){
        List<Ref> refs=new ArrayList<>();
        IdentityHashMap<Container,Set<Integer>> seen=new IdentityHashMap<>();
        var extra=MenuHooks.extra(player);
        for(Slot slot:player.containerMenu.slots){
            Container container=slot.container;int index=slot.getContainerSlot();
            while(container instanceof CompoundContainer compound){
                var parts=(CompoundContainerAccessor)compound;int first=parts.ydnf$first().getContainerSize();
                if(index<first)container=parts.ydnf$first();else{index-=first;container=parts.ydnf$second();}
            }
            if(index<0||index>=container.getContainerSize()||container==player.getInventory()||container==extra||container==extra.bag||container==extra.bagOverflow)continue;
            if(player.inventoryMenu.slots.stream().anyMatch(s->s.container==slot.container))continue;
            if(!seen.computeIfAbsent(container,ignored->new HashSet<>()).add(index))continue;
            Address address=address(player,container,index);
            if(address==null&&generated(slot))continue;
            refs.add(new Ref(container,index,address,inventory.encode(player,container.getItem(index)),address==null&&temporary(player.containerMenu)));
        }
        List<MachineRef> machines=new ArrayList<>();
        for(Container container:seen.keySet())if(container instanceof cn.yumis.fight.industry.MachineBlockEntity be){var pos=be.getBlockPos();machines.add(new MachineRef(be,new Address("industrial",player.level().dimension().identifier().toString(),be.identity.toString(),be.getClass().getName(),pos.getX(),pos.getY(),pos.getZ(),-1),be.transactionState()));}
        if(explicit!=null&&machines.stream().noneMatch(ref->ref.machine==explicit)){var pos=explicit.getBlockPos();machines.add(new MachineRef(explicit,new Address("industrial",player.level().dimension().identifier().toString(),explicit.identity.toString(),explicit.getClass().getName(),pos.getX(),pos.getY(),pos.getZ(),-1),explicit.transactionState()));}
        return new Capture(refs,machines);
    }
    private Address address(ServerPlayer player,Container container,int slot){
        if(container==player.getEnderChestInventory())return new Address("ender","",player.getStringUUID(),"",0,0,0,slot);
        if(container instanceof BlockEntity block){
            var pos=block.getBlockPos();
            String token=block.getPersistentData().getStringOr("ydnf_container_identity","");
            if(token.isEmpty()){token=UUID.randomUUID().toString();block.getPersistentData().putString("ydnf_container_identity",token);block.setChanged();}
            return new Address("block",player.level().dimension().identifier().toString(),token,block.getClass().getName(),pos.getX(),pos.getY(),pos.getZ(),slot);
        }
        Entity owner=container instanceof Entity entity?entity:null;
        if(owner==null)for(Entity entity:player.level().getAllEntities())if(entity instanceof AbstractHorse horse&&horse.getInventory()==container){owner=horse;break;}
        if(owner!=null)return new Address(owner instanceof AbstractHorse?"horse":"entity",owner.level().dimension().identifier().toString(),owner.getStringUUID(),owner.getClass().getName(),owner.getBlockX(),owner.getBlockY(),owner.getBlockZ(),slot);
        return null;
    }
    After after(ServerPlayer player,Capture capture){
        List<JsonElement> values=new ArrayList<>(),temporary=new ArrayList<>();List<Change> persistent=new ArrayList<>();boolean hasTemporary=false;
        for(Ref ref:capture.refs){
            JsonElement value=inventory.encode(player,ref.container.getItem(ref.slot));values.add(value);
            if(ref.temporary){hasTemporary=true;if(!ref.container.getItem(ref.slot).isEmpty())temporary.add(value);}
            else if(ref.address==null||ref.container instanceof cn.yumis.fight.industry.MachineBlockEntity)continue;
            else if(!ref.before.equals(value))persistent.add(new Change(ref.address,ref.before,value));
        }
        List<JsonElement> metadata=new ArrayList<>();for(MachineRef ref:capture.machines){JsonElement after=ref.machine.transactionState();metadata.add(after);if(!after.equals(ref.before))persistent.add(new Change(ref.address,ref.before,after));}
        return new After(values,persistent,new Escrow(player.containerMenu.containerId,temporary),hasTemporary,metadata);
    }
    void apply(ServerPlayer player,Capture capture,List<JsonElement> values,List<JsonElement> metadata){
        for(int i=0;i<capture.machines.size();i++){MachineRef ref=capture.machines.get(i);ref.machine.restoreTransactionState(metadata==null?ref.before:metadata.get(i));}
        for(int i=0;i<capture.refs.size();i++){
            Ref ref=capture.refs.get(i);JsonElement value=values==null?ref.before:values.get(i);
            if(!inventory.encode(player,ref.container.getItem(ref.slot)).equals(value))ref.container.setItem(ref.slot,inventory.decode(player,value));
            ref.container.setChanged();
        }
    }
    void journal(ServerPlayer player,String operation,After after,Map<String,JsonElement> edits){
        if(!after.persistent.isEmpty())edits.put("containerPending/"+player.getUUID()+"/"+operation,JSON.toJsonTree(new Pending(player.getStringUUID(),operation,after.persistent)));
        if(after.hasTemporary)edits.putIfAbsent(escrowKey(player),JSON.toJsonTree(after.escrow));
    }
    void flush(ServerPlayer player,After after){
        flushLevels(player,after.persistent);
        // All viewers, including the second player looking into a shared chest, see the committed item.
        for(ServerPlayer viewer:player.level().getServer().getPlayerList().getPlayers())viewer.containerMenu.broadcastChanges();
    }
    private void flushLevels(ServerPlayer player,List<Change> changes){
        Set<String> dimensions=new HashSet<>();
        for(Change change:changes)if(!change.address.dimension.isEmpty())dimensions.add(change.address.dimension);
        for(String dimension:dimensions){
            var level=player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(dimension)));
            if(level!=null)level.save(null,true,false);
        }
    }
    void completed(ServerPlayer player,String operation,After after,Map<String,JsonElement> edits){
        if(!after.persistent.isEmpty())edits.put("containerPending/"+player.getUUID()+"/"+operation,JsonNull.INSTANCE);
    }
    void recoverPersistent(ServerPlayer player){
        recoverIndustrial();
        for(String key:runtime.journal.keys("containerPending/")){
            Pending pending=runtime.journal.get(key,Pending.class,null);if(pending==null)continue;
            if(pending.changes.stream().anyMatch(c->c.address.kind.equals("industrial")))continue;
            // Private ender inventories can only be recovered when their owner is online.
            ServerPlayer codecPlayer=pending.player.equals(player.getStringUUID())?player:player.level().getServer().getPlayerList().getPlayer(UUID.fromString(pending.player));
            if(codecPlayer==null&&pending.changes.stream().anyMatch(c->c.address.kind.equals("ender")))continue;
            if(codecPlayer==null)codecPlayer=player;
            for(Change change:pending.changes){
                Container container=resolve(codecPlayer,change.address);
                if(container==null)throw new IllegalStateException("容器事务恢复等待原容器加载："+change.address.kind+" "+change.address.owner);
                JsonElement actual=inventory.encode(player,container.getItem(change.address.slot));
                if(actual.equals(change.after))continue;
                if(!actual.equals(change.before))throw new IllegalStateException("容器事务恢复冲突，已保留记录并停止覆盖物品");
                container.setItem(change.address.slot,inventory.decode(player,change.after));container.setChanged();
            }
            codecPlayer.level().getServer().getPlayerList().getPlayerIo().save(codecPlayer);
            flushLevels(codecPlayer,pending.changes);
            try{runtime.journal.put(key,JsonNull.INSTANCE);}catch(Exception e){throw new IllegalStateException("容器事务恢复保存失败",e);}
        }
    }
    void recoverIndustrial(){
        for(String key:runtime.journal.keys("containerPending/")){
            Pending pending=runtime.journal.get(key,Pending.class,null);if(pending==null||pending.changes.isEmpty()||pending.changes.stream().anyMatch(c->!c.address.kind.equals("industrial")))continue;
            boolean waiting=false;
            for(Change change:pending.changes){Address address=change.address;var level=runtime.server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(address.dimension)));BlockPos pos=new BlockPos(address.x,address.y,address.z);if(level==null||!level.hasChunkAt(pos)){waiting=true;continue;}
                if(!(level.getBlockEntity(pos) instanceof cn.yumis.fight.industry.MachineBlockEntity be)||!be.identity.toString().equals(address.owner))throw new IllegalStateException("工业恢复目标已替换，已保留事务记录");
                JsonElement actual=be.transactionState();if(actual.equals(change.after))continue;if(!actual.equals(change.before))throw new IllegalStateException("工业事务恢复冲突，未覆盖机器状态");be.restoreTransactionState(change.after);level.save(null,true,false);
            }
            if(!waiting)try{runtime.journal.put(key,JsonNull.INSTANCE);}catch(Exception e){throw new IllegalStateException("工业事务恢复记录保存失败",e);}
        }
    }
    private Container resolve(ServerPlayer player,Address address){
        if(address.kind.equals("ender"))return player.getStringUUID().equals(address.owner)?player.getEnderChestInventory():null;
        var level=player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(address.dimension)));
        if(level==null)return null;
        level.getChunk(address.x>>4,address.z>>4);
        Container result=null;
        if(address.kind.equals("block")){
            BlockEntity block=level.getBlockEntity(new BlockPos(address.x,address.y,address.z));
            if(block instanceof Container container&&block.getClass().getName().equals(address.type)){
                String token=block.getPersistentData().getStringOr("ydnf_container_identity","");
                // First use may crash before the identity itself is saved. Item CAS remains mandatory.
                if(token.isEmpty()){block.getPersistentData().putString("ydnf_container_identity",address.owner);block.setChanged();}
                else if(!token.equals(address.owner))return null;
                result=container;
            }
        }else{
            Entity entity=level.getEntity(UUID.fromString(address.owner));
            if(entity!=null&&entity.getClass().getName().equals(address.type)){
                if(address.kind.equals("horse")&&entity instanceof AbstractHorse horse)result=horse.getInventory();
                else if(entity instanceof Container container)result=container;
            }
        }
        return result!=null&&address.slot>=0&&address.slot<result.getContainerSize()?result:null;
    }
    void recoverEscrow(ServerPlayer player){
        Escrow escrow=runtime.journal.get(escrowKey(player),Escrow.class,null);
        if(escrow==null)return;
        JsonElement current=runtime.journal.get("equipmentWaiting/"+player.getUUID());
        JsonArray waiting=current.isJsonArray()?current.getAsJsonArray():new JsonArray();
        for(JsonElement item:escrow.items)if(!inventory.decode(player,item).isEmpty())waiting.add(item.deepCopy());
        try{runtime.journal.transaction(Map.of("equipmentWaiting/"+player.getUUID(),waiting,escrowKey(player),JsonNull.INSTANCE));}
        catch(Exception e){throw new IllegalStateException("临时容器恢复保存失败",e);}
    }
    Map<String,JsonElement> close(ServerPlayer player,AbstractContainerMenu oldMenu){
        Escrow escrow=runtime.journal.get(escrowKey(player),Escrow.class,null);
        return escrow!=null&&escrow.menu==oldMenu.containerId?Map.of(escrowKey(player),JsonNull.INSTANCE):Map.of();
    }
}

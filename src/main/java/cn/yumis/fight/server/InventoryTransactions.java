package cn.yumis.fight.server;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.foundation.Journal;
import cn.yumis.fight.game.*;
import static cn.yumis.fight.rules.Models.*;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;
public final class InventoryTransactions {
    public record Snapshot(List<JsonElement> items,List<JsonElement> equipment,List<JsonElement> bag,JsonElement cursor,String operation,List<String> groups,List<JsonElement> crafting) {
    }
    private final ServerRuntime runtime;
    private final Set<UUID> locks=new HashSet<>(),bags=new HashSet<>();
    private boolean inside;
    public static final class NoSpace extends IllegalStateException {
        public NoSpace(){
            super("背包空间不足");
        }
    }
    private final List<LootService.Drop> stagedDrops=new ArrayList<>();
    public net.minecraft.world.entity.item.ItemEntity stageDrop(ServerPlayer p,ItemStack stack){
        if(stack.isEmpty())return null;
        LootService.Drop drop=runtime.loot.manual(p,stack,p.position());
        stagedDrops.add(drop);
        var entity=new net.minecraft.world.entity.item.ItemEntity(p.level(),drop.x,drop.y,drop.z,stack.copy());
        entity.setUUID(drop.id);
        entity.setDefaultPickUpDelay();
        return entity;
    }
    public InventoryTransactions(ServerRuntime runtime){
        this.runtime=runtime;
    }
    public boolean inside(){
        return inside;
    }
    public boolean locked(UUID player){
        return locks.contains(player);
    }
    public boolean isBag(ServerPlayer p){
        return bags.contains(p.getUUID());
    }
    public void bag(ServerPlayer p,boolean open){
        if(open)bags.add(p.getUUID());
        else bags.remove(p.getUUID());
    }
    public JsonElement encode(ServerPlayer p,ItemStack stack){
        return ItemStack.OPTIONAL_CODEC.encodeStart(p.registryAccess().createSerializationContext(JsonOps.INSTANCE),stack).getOrThrow();
    }
    public ItemStack decode(ServerPlayer p,JsonElement value){
        return ItemStack.OPTIONAL_CODEC.parse(p.registryAccess().createSerializationContext(JsonOps.INSTANCE),value).getOrThrow();
    }
    public Snapshot capture(ServerPlayer p,String operation){
        List<JsonElement> items=new ArrayList<>(),eq=new ArrayList<>(),bag=new ArrayList<>();
        for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(encode(p,p.getInventory().getItem(i)));
        var extra=MenuHooks.extra(p);
        for(int i=0;i<extra.getContainerSize();i++)eq.add(encode(p,extra.getItem(i)));
        for(int i=0;i<54;i++)bag.add(encode(p,extra.bag.getItem(i)));
        List<JsonElement> crafting=new ArrayList<>();
        for(int i=1;i<=4;i++)crafting.add(encode(p,p.inventoryMenu.getSlot(i).getItem()));
        return new Snapshot(items,eq,bag,encode(p,p.containerMenu.getCarried()),operation,List.copyOf(extra.groups),crafting);
    }
    private void apply(ServerPlayer p,Snapshot s){
        for(int i=0;i<s.items.size();i++)p.getInventory().setItem(i,decode(p,s.items.get(i)));
        var e=MenuHooks.extra(p);
        if(s.groups!=null)e.layout(s.groups);
        for(int i=0;i<e.getContainerSize();i++)e.setItem(i,i<s.equipment.size()?decode(p,s.equipment.get(i)):ItemStack.EMPTY);
        for(int i=0;i<54;i++)e.bag.setItem(i,i<s.bag.size()?decode(p,s.bag.get(i)):ItemStack.EMPTY);
        if(s.crafting!=null)for(int i=0;i<Math.min(4,s.crafting.size());i++)p.inventoryMenu.getSlot(i+1).set(decode(p,s.crafting.get(i)));
        p.containerMenu.setCarried(decode(p,s.cursor));
        p.setData(FightMod.STATE,JSON.toJson(s));
    }
    public void restore(ServerPlayer p){
        try{
            Snapshot s=runtime.journal.get("inventory/"+p.getUUID(),Snapshot.class,null);
            String nativeState=p.getData(FightMod.STATE);
            if(!nativeState.isEmpty()){
                Snapshot stored=JSON.fromJson(nativeState,Snapshot.class);
                var e=MenuHooks.extra(p);
                if(stored.groups!=null)e.layout(stored.groups);
                for(int i=0;i<Math.min(e.getContainerSize(),stored.equipment.size());i++)e.setItem(i,decode(p,stored.equipment.get(i)));
                for(int i=0;i<Math.min(54,stored.bag.size());i++)e.bag.setItem(i,decode(p,stored.bag.get(i)));
                if(stored.crafting!=null)for(int i=0;i<Math.min(4,stored.crafting.size());i++)p.inventoryMenu.getSlot(i+1).set(decode(p,stored.crafting.get(i)));
                if(s==null||!s.operation.equals(stored.operation)){
                    p.inventoryMenu.setCarried(decode(p,stored.cursor));
                }
            }
            String applied=runtime.journal.get("inventoryApplied/"+p.getUUID(),String.class,"");
            if(s!=null&&!s.operation.equals(applied)){
                apply(p,s);
                verify(p,s);
                runtime.journal.put("inventoryApplied/"+p.getUUID(),s.operation);
            }
        }
        catch(Exception failure){
            throw new IllegalStateException("背包恢复未完成",failure);
        }
    }
    private void verify(ServerPlayer p,Snapshot snapshot)throws Exception{
        p.setData(FightMod.STATE,JSON.toJson(snapshot));
        p.level().getServer().getPlayerList().getPlayerIo().save(p);
        Path path=p.level().getServer().getPlayerList().getPlayerIo().getPlayerDir().toPath().resolve(p.getStringUUID()+".dat");
        CompoundTag nbt=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
        String value=nbt.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty(GameItems.NS+":state").getStringOr("value","");
        if(!value.equals(JSON.toJson(snapshot)))throw new IllegalStateException("原生存档回读与背包事务不一致");
    }
    void verificationRestore(ServerPlayer player,Snapshot snapshot){
        if(!Boolean.getBoolean("ydnf.verify"))throw new IllegalStateException("verification disabled");
        apply(player,snapshot);
    }
    private String nativeOperation(ServerPlayer player){
        try{
            String raw=player.getData(FightMod.STATE);
            return raw.isEmpty()?"initial":JSON.fromJson(raw,Snapshot.class).operation;
        }
        catch(RuntimeException e){
            return "initial";
        }
    }
    public boolean transact(ServerPlayer p,String operation,JsonObject intent,Runnable mutation,Map<String,JsonElement> changes){
        if(!locks.add(p.getUUID()))throw new IllegalArgumentException("背包事务处理中");
        Snapshot before=capture(p,nativeOperation(p));
        boolean committed=false;
        try{
            String key="inventoryReceipt/"+p.getUUID()+"/"+operation;
            JsonElement receipt=runtime.journal.get(key);
            if(!receipt.isJsonNull()){
                if(!receipt.getAsJsonObject().get("intent").equals(intent))throw new IllegalArgumentException("操作号与原请求冲突");
                restore(p);
                return false;
            }
            inside=true;
            stagedDrops.clear();
            mutation.run();
            Snapshot after=capture(p,operation);
            apply(p,before);
            Map<String,JsonElement> edits=new LinkedHashMap<>(changes);
            edits.put("inventory/"+p.getUUID(),JSON.toJsonTree(after));
            JsonObject r=new JsonObject();
            r.add("intent",intent.deepCopy());
            edits.put(key,r);
            for(var drop:stagedDrops)edits.put("loot/"+drop.id,JSON.toJsonTree(drop));
            runtime.journal.transaction(edits);
            committed=true;
            apply(p,after);
            verify(p,after);
            runtime.journal.put("inventoryApplied/"+p.getUUID(),operation);
            p.containerMenu.broadcastFullState();
            if(!stagedDrops.isEmpty())runtime.loot.publish(stagedDrops);
            return true;
        }
        catch(Exception error){
            if(!committed)apply(p,before);
            else p.connection.disconnect(Component.literal("背包已保存，原生恢复未完成，请重新连接："+error.getMessage()));
            if(!committed&&error instanceof NoSpace space)throw space;
            throw new IllegalStateException(error.getMessage(),error);
        }
        finally{
            inside=false;
            stagedDrops.clear();
            locks.remove(p.getUUID());
        }
    }
    public void click(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        try{
            JsonObject j=new JsonObject();
            j.addProperty("menu",menu.containerId);
            j.addProperty("slot",slot);
            j.addProperty("button",button);
            j.addProperty("input",input.name());
            transact(p,UUID.randomUUID().toString(),j,()->{
                if(!runtime.currencyClick(p,menu,slot,button,input)&&!MenuHooks.transfer(menu,slot,button,input,p))menu.clicked(slot,button,input,p);
            }
            ,Map.of());
            runtime.profile(p);
        }
        catch(RuntimeException e){
            runtime.message(p,e.getMessage());
            menu.broadcastFullState();
        }
    }
    public void requireSpace(ServerPlayer p,ItemStack result){
        ItemStack copy=result.copy();
        if(InventoryPacking.room(p,copy)<copy.getCount())throw new NoSpace();
        InventoryPacking.insert(p,copy);
        if(!copy.isEmpty())throw new NoSpace();
    }
    private void stash(ServerPlayer p,ItemStack item,Map<String,JsonElement> changes){
        InventoryPacking.insert(p,item);
        var bag=MenuHooks.extra(p).bag;
        for(int i=0;i<54&&!item.isEmpty();i++)if(bag.getItem(i).isEmpty()){
            bag.setItem(i,item.copy());
            item.setCount(0);
        }
        if(!item.isEmpty()){
            JsonElement old=changes.getOrDefault("equipmentWaiting/"+p.getUUID(),runtime.journal.get("equipmentWaiting/"+p.getUUID()));
            JsonArray waiting=old.isJsonArray()?old.getAsJsonArray():new JsonArray();
            waiting.add(encode(p,item));
            changes.put("equipmentWaiting/"+p.getUUID(),waiting);
            item.setCount(0);
        }
    }
    public void closeCursor(ServerPlayer p){
        boolean craftingPresent=p.containerMenu==p.inventoryMenu&&java.util.stream.IntStream.rangeClosed(1,4).anyMatch(i->p.inventoryMenu.getSlot(i).hasItem());
        if(p.containerMenu.getCarried().isEmpty()&&!craftingPresent)return;
        Map<String,JsonElement> changes=new HashMap<>();
        JsonObject j=new JsonObject();
        j.addProperty("close",true);
        transact(p,"close/"+UUID.randomUUID(),j,()->{
            ItemStack cursor=p.containerMenu.getCarried();p.containerMenu.setCarried(ItemStack.EMPTY);if(craftingPresent){
                for(int i=1;i<=4;i++){
                    ItemStack crafted=p.inventoryMenu.getSlot(i).getItem();p.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);stash(p,crafted,changes);
                }
            }
            InventoryPacking.insert(p,cursor);var extra=MenuHooks.extra(p);for(int i=0;i<54&&!cursor.isEmpty();i++){
                ItemStack stored=extra.bag.getItem(i);if(stored.isEmpty()){
                    extra.bag.setItem(i,cursor.copy());cursor.setCount(0);
                }
                else if(ItemStack.isSameItemSameComponents(stored,cursor)){
                    int n=Math.min(cursor.getCount(),stored.getMaxStackSize()-stored.getCount());stored.grow(n);cursor.shrink(n);
                }
            }
            if(!cursor.isEmpty()){
                JsonElement current=runtime.journal.get("equipmentWaiting/"+p.getUUID());JsonArray waiting=current.isJsonNull()?new JsonArray():current.getAsJsonArray();waiting.add(encode(p,cursor));changes.put("equipmentWaiting/"+p.getUUID(),waiting);cursor.setCount(0);
            }
        }
        ,changes);
    }
}

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
    public record Snapshot(List<JsonElement> items,List<JsonElement> equipment,List<JsonElement> bag,JsonElement cursor,String operation,List<String> groups,List<JsonElement> crafting,boolean curios,int bagSchema,int bagTier,int bagPage,List<JsonElement> bagOverflow) {
    }
    private final ServerRuntime runtime;
    private final ContainerParticipants containers;
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
        this.containers=new ContainerParticipants(runtime,this);
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
        for(int i=0;i<extra.bagLimit();i++)bag.add(encode(p,extra.bag.getItem(i)));
        List<JsonElement> overflow=new ArrayList<>();
        for(int i=0;i<extra.bagOverflow.getContainerSize();i++)overflow.add(encode(p,extra.bagOverflow.getItem(i)));
        List<JsonElement> crafting=new ArrayList<>();
        for(int i=1;i<=4;i++)crafting.add(encode(p,p.inventoryMenu.getSlot(i).getItem()));
        return new Snapshot(items,eq,bag,encode(p,p.containerMenu.getCarried()),operation,List.copyOf(extra.groups),crafting,true,extra.bagSchema,extra.bagTier,extra.bagPage,overflow);
    }
    private void apply(ServerPlayer p,Snapshot s){
        for(int i=0;i<s.items.size();i++)p.getInventory().setItem(i,decode(p,s.items.get(i)));
        var e=MenuHooks.extra(p);
        e.clearContent();
        if(s.groups!=null)e.layout(s.groups);
        for(int i=0;i<e.getContainerSize();i++)e.setItem(i,i<s.equipment.size()?decode(p,s.equipment.get(i)):ItemStack.EMPTY);
        restoreBag(p,s);
        if(s.crafting!=null)for(int i=0;i<Math.min(4,s.crafting.size());i++)p.inventoryMenu.getSlot(i+1).set(decode(p,s.crafting.get(i)));
        p.containerMenu.setCarried(decode(p,s.cursor));
        p.setData(FightMod.STATE,JSON.toJson(s));
    }
    public void restore(ServerPlayer p){
        try{
            containers.recoverPersistent(p);
            Snapshot s=runtime.journal.get("inventory/"+p.getUUID(),Snapshot.class,null);
            String nativeState=p.getData(FightMod.STATE);
            if(!nativeState.isEmpty()){
                Snapshot stored=JSON.fromJson(nativeState,Snapshot.class);
                var e=MenuHooks.extra(p);
                if(!stored.curios){
                    e.clearContent();
                    if(stored.groups!=null)e.layout(stored.groups);
                    for(int i=0;i<Math.min(e.getContainerSize(),stored.equipment.size());i++)e.setItem(i,decode(p,stored.equipment.get(i)));
                }else if(stored.groups!=null)e.layout(stored.groups);
                restoreBag(p,stored);
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
            if(!nativeState.isEmpty()&&!JSON.fromJson(nativeState,Snapshot.class).curios){
                verify(p,capture(p,nativeOperation(p)));
            }
            if(p.containerMenu==p.inventoryMenu)containers.recoverEscrow(p);
            if(MenuHooks.extra(p).bagSchema==0){
                if(s!=null&&s.bagSchema>0){apply(p,s);verify(p,s);}
                else {
                    JsonObject intent=new JsonObject();intent.addProperty("bagMigration",1);
                    transact(p,"bag-migration/v1",intent,()->{
                        var extra=MenuHooks.extra(p);
                        for(int i=8;i<54;i++){extra.bagOverflow.setItem(i-8,extra.bag.getItem(i).copy());extra.bag.setItem(i,ItemStack.EMPTY);}
                        extra.bagSchema=1;extra.bagTier=1;extra.bagPage=0;
                    },Map.of());
                }
            }
        }
        catch(Exception failure){
            throw new IllegalStateException("背包恢复未完成",failure);
        }
    }
    private void restoreBag(ServerPlayer p,Snapshot snapshot){
        var extra=MenuHooks.extra(p);
        if(snapshot.bagSchema!=0&&(snapshot.bagSchema!=1||snapshot.bagTier<1||snapshot.bagTier>16))throw new IllegalStateException("次元储物档案无效，原数据已保留");
        int limit=snapshot.bagSchema==0?54:cn.yumis.fight.industry.StorageRules.capacity(snapshot.bagTier);
        if(snapshot.bag==null||snapshot.bag.size()>limit||snapshot.bagOverflow!=null&&snapshot.bagOverflow.size()>extra.bagOverflow.getContainerSize())throw new IllegalStateException("次元储物记录超出容量，原数据已保留");
        extra.bag.clearContent();extra.bagOverflow.clearContent();extra.bagSchema=snapshot.bagSchema;extra.bagTier=snapshot.bagSchema==0?1:snapshot.bagTier;
        extra.bagPage=Math.clamp(snapshot.bagPage,0,cn.yumis.fight.industry.StorageRules.pages(extra.bagTier)-1);
        for(int i=0;i<snapshot.bag.size();i++)extra.bag.setItem(i,decode(p,snapshot.bag.get(i)));
        if(snapshot.bagOverflow!=null)for(int i=0;i<snapshot.bagOverflow.size();i++)extra.bagOverflow.setItem(i,decode(p,snapshot.bagOverflow.get(i)));
    }
    public String clientStorageState(ServerPlayer p){
        var extra=MenuHooks.extra(p);JsonObject value=new JsonObject();value.addProperty("bagSchema",extra.bagSchema);value.addProperty("bagTier",extra.bagTier);value.addProperty("bagPage",extra.bagPage);return value.toString();
    }
    private void verify(ServerPlayer p,Snapshot snapshot)throws Exception{
        p.setData(FightMod.STATE,JSON.toJson(snapshot));
        p.level().getServer().getPlayerList().getPlayerIo().save(p);
        Path path=p.level().getServer().getPlayerList().getPlayerIo().getPlayerDir().toPath().resolve(p.getStringUUID()+".dat");
        CompoundTag nbt=NbtIo.readCompressed(path,NbtAccounter.unlimitedHeap());
        String value=FightMod.STATE_CODEC.codec().parse(NbtOps.INSTANCE,nbt.getCompoundOrEmpty("neoforge:attachments").getCompoundOrEmpty(GameItems.NS+":state")).getOrThrow();
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
        if(!inside)containers.recoverPersistent(p);
        if(!locks.add(p.getUUID()))throw new IllegalArgumentException("背包事务处理中");
        Snapshot before=capture(p,nativeOperation(p));
        var external=containers.capture(p);
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
            var externalAfter=containers.after(p,external);
            apply(p,before);
            containers.apply(p,external,null,null);
            Map<String,JsonElement> edits=new LinkedHashMap<>(changes);
            containers.journal(p,operation,externalAfter,edits);
            edits.put("inventory/"+p.getUUID(),JSON.toJsonTree(after));
            JsonObject r=new JsonObject();
            r.add("intent",intent.deepCopy());
            edits.put(key,r);
            for(var drop:stagedDrops)edits.put("loot/"+drop.id,JSON.toJsonTree(drop));
            runtime.journal.transaction(edits);
            committed=true;
            apply(p,after);
            containers.apply(p,external,externalAfter.values(),externalAfter.metadata());
            verify(p,after);
            containers.flush(p,externalAfter);
            Map<String,JsonElement> completed=new LinkedHashMap<>();
            completed.put("inventoryApplied/"+p.getUUID(),JSON.toJsonTree(operation));
            containers.completed(p,operation,externalAfter,completed);
            runtime.journal.transaction(completed);
            p.containerMenu.broadcastFullState();
            if(!stagedDrops.isEmpty())runtime.loot.publish(stagedDrops);
            return true;
        }
        catch(Exception error){
            if(!committed){apply(p,before);containers.apply(p,external,null,null);}
            else p.connection.disconnect(ModText.of("背包已保存，原生恢复未完成，请重新连接："+error.getMessage()));
            if(!committed&&error instanceof NoSpace space)throw space;
            throw new IllegalStateException(error.getMessage(),error);
        }
        finally{
            if(committed)cn.yumis.fight.industry.IndustrialWorld.get(p.level()).requestRecovery();
            inside=false;
            stagedDrops.clear();
            locks.remove(p.getUUID());
        }
    }
    public void recoverContainers(ServerPlayer player){containers.recoverPersistent(player);}
    public void recoverIndustrial(){containers.recoverIndustrial();}
    /** Called only after the native menu has returned its temporary inputs to the player. */
    public void finishMenu(ServerPlayer player,AbstractContainerMenu oldMenu){
        var changes=containers.close(player,oldMenu);
        if(changes.isEmpty()||inside)return;
        JsonObject intent=new JsonObject();intent.addProperty("closedMenu",oldMenu.containerId);
        transact(player,"menu-close/"+UUID.randomUUID(),intent,()->{},changes);
    }
    public void click(ServerPlayer p,AbstractContainerMenu menu,int slot,int button,ContainerInput input){
        try{
            JsonObject j=new JsonObject();
            j.addProperty("menu",menu.containerId);
            j.addProperty("slot",slot);
            j.addProperty("button",button);
            j.addProperty("input",input.name());
            transact(p,UUID.randomUUID().toString(),j,()->{
                if(!runtime.currencyClick(p,menu,slot,button,input))menu.clicked(slot,button,input,p);
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
        for(int i=0;i<MenuHooks.extra(p).bagLimit()&&!item.isEmpty();i++)if(bag.getItem(i).isEmpty()){
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
        var temporary=containers.capture(p).refs().stream().filter(ContainerParticipants.Ref::temporary).toList();
        boolean temporaryPresent=temporary.stream().anyMatch(r->!r.container().getItem(r.slot()).isEmpty());
        if(p.containerMenu.getCarried().isEmpty()&&!craftingPresent&&!temporaryPresent)return;
        Map<String,JsonElement> changes=new HashMap<>();
        JsonObject j=new JsonObject();
        j.addProperty("close",true);
        transact(p,"close/"+UUID.randomUUID(),j,()->{
            ItemStack cursor=p.containerMenu.getCarried();p.containerMenu.setCarried(ItemStack.EMPTY);if(craftingPresent){
                for(int i=1;i<=4;i++){
                    ItemStack crafted=p.inventoryMenu.getSlot(i).getItem();p.inventoryMenu.getSlot(i).set(ItemStack.EMPTY);stash(p,crafted,changes);
                }
            }
            for(var input:temporary){
                ItemStack stack=input.container().getItem(input.slot());
                input.container().setItem(input.slot(),ItemStack.EMPTY);
                stash(p,stack,changes);
            }
            InventoryPacking.insert(p,cursor);var extra=MenuHooks.extra(p);for(int i=0;i<extra.bagLimit()&&!cursor.isEmpty();i++){
                ItemStack stored=extra.bag.getItem(i);if(stored.isEmpty()){
                    extra.bag.setItem(i,cursor.copy());cursor.setCount(0);
                }
                else if(ItemStack.isSameItemSameComponents(stored,cursor)){
                    int n=Math.min(cursor.getCount(),stored.getMaxStackSize()-stored.getCount());stored.grow(n);cursor.shrink(n);
                }
            }
            if(!cursor.isEmpty()){
                JsonElement current=changes.getOrDefault("equipmentWaiting/"+p.getUUID(),runtime.journal.get("equipmentWaiting/"+p.getUUID()));JsonArray waiting=current.isJsonNull()?new JsonArray():current.getAsJsonArray();waiting.add(encode(p,cursor));changes.put("equipmentWaiting/"+p.getUUID(),waiting);cursor.setCount(0);
            }
        }
        ,changes);
    }
}

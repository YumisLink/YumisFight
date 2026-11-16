package cn.yumis.fight.modules.inventory.application;

import cn.yumis.fight.mixin.CompoundContainerAccessor;
import cn.yumis.fight.modules.inventory.api.MenuHooks;
import cn.yumis.fight.modules.inventory.api.PersistentContainer;
import com.google.gson.JsonElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.inventory.*;
import java.util.*;

/** In-memory snapshots of external containers for transaction rollback. */
final class ContainerParticipants {
    record Ref(Container container,int slot,JsonElement before,boolean temporary){}
    record MachineRef(PersistentContainer machine,JsonElement before){}
    record Capture(List<Ref> refs,List<MachineRef> machines){}
    record After(List<JsonElement> values,List<JsonElement> metadata){}
    private final InventoryTransactions inventory;
    ContainerParticipants(InventoryTransactions inventory){this.inventory=inventory;}
    private static boolean generated(Slot slot){return slot instanceof ResultSlot||slot instanceof MerchantResultSlot||slot instanceof NonInteractiveResultSlot||slot.container instanceof ResultContainer;}
    static boolean temporary(AbstractContainerMenu menu){
        return menu instanceof AbstractCraftingMenu||menu instanceof ItemCombinerMenu||menu instanceof EnchantmentMenu||menu instanceof GrindstoneMenu||menu instanceof CartographyTableMenu||menu instanceof LoomMenu||menu instanceof StonecutterMenu||menu instanceof MerchantMenu||menu instanceof BeaconMenu;
    }
    Capture capture(ServerPlayer player){return capture(player,null);}
    Capture capture(ServerPlayer player,PersistentContainer explicit){
        List<Ref> refs=new ArrayList<>();
        IdentityHashMap<Container,Set<Integer>> seen=new IdentityHashMap<>();
        var extra=MenuHooks.extra(player);
        for(Slot slot:player.containerMenu.slots){
            Container container=slot.container;int index=slot.getContainerSlot();
            while(container instanceof CompoundContainer compound){
                var parts=(CompoundContainerAccessor)compound;int first=parts.ydnf$first().getContainerSize();
                if(index<first)container=parts.ydnf$first();else{index-=first;container=parts.ydnf$second();}
            }
            if(index<0||index>=container.getContainerSize()||container==player.getInventory()||container==extra||container==extra.bag||generated(slot))continue;
            if(player.inventoryMenu.slots.stream().anyMatch(s->s.container==slot.container))continue;
            if(!seen.computeIfAbsent(container,ignored->new HashSet<>()).add(index))continue;
            refs.add(new Ref(container,index,inventory.encode(player,container.getItem(index)),temporary(player.containerMenu)));
        }
        List<MachineRef> machines=new ArrayList<>();
        for(Container container:seen.keySet())if(container instanceof PersistentContainer machine)machines.add(new MachineRef(machine,machine.transactionState()));
        if(explicit!=null&&machines.stream().noneMatch(ref->ref.machine==explicit))machines.add(new MachineRef(explicit,explicit.transactionState()));
        return new Capture(refs,machines);
    }
    After after(ServerPlayer player,Capture capture){
        List<JsonElement> values=new ArrayList<>(),metadata=new ArrayList<>();
        for(Ref ref:capture.refs)values.add(inventory.encode(player,ref.container.getItem(ref.slot)));
        for(MachineRef ref:capture.machines)metadata.add(ref.machine.transactionState());
        return new After(values,metadata);
    }
    void apply(ServerPlayer player,Capture capture,List<JsonElement> values,List<JsonElement> metadata){
        for(int i=0;i<capture.machines.size();i++){MachineRef ref=capture.machines.get(i);ref.machine.restoreTransactionState(metadata==null?ref.before:metadata.get(i));}
        for(int i=0;i<capture.refs.size();i++){
            Ref ref=capture.refs.get(i);JsonElement value=values==null?ref.before:values.get(i);
            if(!inventory.encode(player,ref.container.getItem(ref.slot)).equals(value))ref.container.setItem(ref.slot,inventory.decode(player,value));
            ref.container.setChanged();
        }
    }
}

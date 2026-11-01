package cn.yumis.fight.modules.inventory.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.util.*;

/** Take-only, session-local reward container. Unknown external slots are snapshotted by inventory transactions;
 * there is deliberately no recovery escrow: disconnect/restart forfeits the unclaimed contents. */
public final class RewardMenu extends ChestMenu implements CheckpointMenu {
    public record Claim(Map<String,JsonElement> edits,Runnable take){}
    private record Retry(List<ItemStack> items,java.util.function.BiFunction<ServerPlayer,ItemStack,Map<String,JsonElement>> credits){}
    private static final Map<UUID,Retry> retry=new HashMap<>();
    private final SimpleContainer contents;
    private final ArrayDeque<ItemStack> remaining;
    private final InventoryOperations inventory;
    private final java.util.function.BiFunction<ServerPlayer,ItemStack,Map<String,JsonElement>> credits;
    private boolean forfeited,closed;
    public RewardMenu(int id,Inventory player,SimpleContainer contents,ArrayDeque<ItemStack> remaining,InventoryOperations inventory,java.util.function.BiFunction<ServerPlayer,ItemStack,Map<String,JsonElement>> credits){super(MenuType.GENERIC_9x6,id,player,contents,6);this.contents=contents;this.remaining=remaining;this.inventory=inventory;this.credits=credits;for(int i=0;i<54;i++){Slot old=slots.get(i);Slot takeOnly=new Slot(contents,i,old.x,old.y){@Override public boolean mayPlace(ItemStack stack){return false;}};takeOnly.index=old.index;slots.set(i,takeOnly);}}
    public static void open(ServerPlayer p,String name,List<ItemStack> items,InventoryOperations inventory,java.util.function.BiFunction<ServerPlayer,ItemStack,Map<String,JsonElement>> credits){
        ArrayDeque<ItemStack> pending=new ArrayDeque<>();for(var stack:items){var copy=stack.copy();for(var stored:pending)if(ItemStack.isSameItemSameComponents(stored,copy)){int n=Math.min(copy.getCount(),stored.getMaxStackSize()-stored.getCount());stored.grow(n);copy.shrink(n);}while(!copy.isEmpty())pending.add(copy.split(Math.min(copy.getCount(),copy.getMaxStackSize())));}
        SimpleContainer container=new SimpleContainer(54){@Override public boolean canPlaceItem(int slot,ItemStack stack){return false;}};
        fill(container,pending);
        p.openMenu(new SimpleMenuProvider((id,inv,owner)->new RewardMenu(id,inv,container,pending,inventory,credits),Component.literal(name)));
    }
    private static void fill(SimpleContainer container,ArrayDeque<ItemStack> pending){for(int i=0;i<54&&!pending.isEmpty();i++)if(container.getItem(i).isEmpty())container.setItem(i,pending.removeFirst());}
    /** A page advances only after its items have been removed, keeping vanilla chest UI and slot IDs. */
    public void nextPage(){if(!closed&&contents.isEmpty())fill(contents,remaining);}
    public void forfeit(){forfeited=true;remaining.clear();contents.clearContent();}
    public static void discardPending(ServerPlayer p){retry.remove(p.getUUID());}
    public static void tick(ServerPlayer p,InventoryOperations inventory){if(p.containerMenu instanceof RewardMenu menu)menu.nextPage();else if(p.containerMenu==p.inventoryMenu&&p.connection.isAcceptingMessages()){Retry pending=retry.remove(p.getUUID());if(pending!=null)open(p,"宝箱 · 未领取物品",pending.items(),inventory,pending.credits());}}
    /** A business owner supplies balance edits; inventory joins them to the very same slot-removal transaction. */
    public Claim claim(ServerPlayer p,int slot,ContainerInput input){
        if(slot<0||slot>=54||input!=ContainerInput.PICKUP&&input!=ContainerInput.QUICK_MOVE||contents.getItem(slot).isEmpty())return null;
        ItemStack expected=contents.getItem(slot).copy();Map<String,JsonElement> edits=credits.apply(p,expected);if(edits.isEmpty())return null;
        return new Claim(edits,()->{if(!ItemStack.matches(contents.getItem(slot),expected))throw new IllegalArgumentException("宝箱槽位已改变");contents.setItem(slot,ItemStack.EMPTY);});
    }
    @Override public ItemStack quickMoveStack(Player p,int slot){if(slot>=54)return ItemStack.EMPTY;return super.quickMoveStack(p,slot);}
    @Override public void clicked(int slot,int button,ContainerInput input,Player player){
        if(slot>=0&&slot<54&&(input==ContainerInput.SWAP||input==ContainerInput.QUICK_CRAFT))return;
        super.clicked(slot,button,input,player);
    }
    @Override public void removed(Player player){
        if(closed)return;
        if(player instanceof ServerPlayer p&&!p.connection.isAcceptingMessages())forfeit();
        if(player instanceof ServerPlayer p&&!forfeited){
            JsonObject intent=new JsonObject();intent.addProperty("rewardClose",containerId);
            boolean committed=false;
            try{committed=inventory.transact(p,"reward-close/"+UUID.randomUUID(),intent,()->{
                for(int i=0;i<54;i++){ItemStack stack=contents.removeItemNoUpdate(i);if(!stack.isEmpty())p.drop(stack,false);}
                for(var stack:remaining)p.drop(stack.copy(),false);
            },Map.of());}catch(RuntimeException failure){p.sendSystemMessage(Component.literal("宝箱物品暂时无法落地，未领取内容将保留在宝箱中。"));}
            if(!committed&&p.connection.isAcceptingMessages()){
                List<ItemStack> pending=new ArrayList<>();for(int i=0;i<54;i++)if(!contents.getItem(i).isEmpty())pending.add(contents.getItem(i).copy());for(var stack:remaining)pending.add(stack.copy());retry.put(p.getUUID(),new Retry(pending,credits));
            }
            remaining.clear();contents.clearContent();
        }else{contents.clearContent();remaining.clear();}
        closed=true;super.removed(player);
    }
}

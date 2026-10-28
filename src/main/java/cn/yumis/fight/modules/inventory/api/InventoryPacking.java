package cn.yumis.fight.modules.inventory.api;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Real capacity, including in creative mode. Vanilla Inventory.add may discard creative overflow. */
public final class InventoryPacking {
    private InventoryPacking(){
    }
    public static int room(Player player,ItemStack incoming){
        int room=0;
        for(int i=0;i<36;i++){
            ItemStack existing=player.getInventory().getItem(i);
            if(existing.isEmpty())room+=incoming.getMaxStackSize();
            else if(ItemStack.isSameItemSameComponents(existing,incoming))room+=Math.max(0,existing.getMaxStackSize()-existing.getCount());
        }
        return room;
    }
    public static void insert(Player player,ItemStack incoming){
        if(incoming.isEmpty())return;
        for(int i=0;i<36&&!incoming.isEmpty();i++){
            ItemStack existing=player.getInventory().getItem(i);
            if(!existing.isEmpty()&&ItemStack.isSameItemSameComponents(existing,incoming)){
                int count=Math.min(incoming.getCount(),Math.max(0,existing.getMaxStackSize()-existing.getCount()));
                existing.grow(count);
                incoming.shrink(count);
            }
        }
        List<Integer> order=new ArrayList<>();
        order.add(player.getInventory().getSelectedSlot());
        for(int i=0;i<36;i++)if(!order.contains(i))order.add(i);
        for(int i:order)if(!incoming.isEmpty()&&player.getInventory().getItem(i).isEmpty())player.getInventory().setItem(i,incoming.split(Math.min(incoming.getCount(),incoming.getMaxStackSize())));
        player.getInventory().setChanged();
    }
}

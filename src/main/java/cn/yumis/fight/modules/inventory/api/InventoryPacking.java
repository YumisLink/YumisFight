package cn.yumis.fight.modules.inventory.api;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;
/** Real capacity, including in creative mode. Vanilla Inventory.add may discard creative overflow. */
public final class InventoryPacking {
    private InventoryPacking(){
    }
    public static int room(Player player,ItemStack incoming){
        return (int)Math.min(Integer.MAX_VALUE,(long)rawRoom(player,incoming)+InventoryIntake.capacity(player,incoming));
    }
    /** 拾取容量按自动收纳偏好计入次元背包。 */
    public static int room(BagOption option,Player player,ItemStack incoming){
        int base=room(player,incoming);
        if(!option.autoStore())return base;
        return (int)Math.min(Integer.MAX_VALUE,(long)base+bagRoom(player,incoming)+MenuHooks.extra(player).categories.room(incoming));
    }
    /** 次元背包的同类合并与空格容量。 */
    public static int bagRoom(Player player,ItemStack incoming){
        if(incoming.isEmpty())return 0;
        var extra=MenuHooks.extra(player);
        int room=0;
        for(int i=0;i<extra.bagLimit();i++){
            ItemStack existing=extra.bag.getItem(i);
            if(existing.isEmpty())room+=incoming.getMaxStackSize();
            else if(ItemStack.isSameItemSameComponents(existing,incoming))room+=Math.max(0,existing.getMaxStackSize()-existing.getCount());
        }
        return room;
    }
    /** 按偏好入库：直收模式先进次元背包，其余先进普通背包，放不下再按开关收进次元背包。 */
    public static void insertWithOption(BagOption option,Player player,ItemStack incoming){
        if(incoming.isEmpty())return;
        if(option.autoStore())MenuHooks.extra(player).categories.insert(incoming);
        if(option.autoStore()&&!option.onlyWhenFull())insertBag(player,incoming);
        if(!incoming.isEmpty())insert(player,incoming);
        if(!incoming.isEmpty()&&option.autoStore())insertBag(player,incoming);
    }
    /** 合并同类后填入次元背包空格，放不下的部分留在传入堆栈。 */
    public static void insertBag(Player player,ItemStack incoming){
        if(incoming.isEmpty())return;
        var extra=MenuHooks.extra(player);
        for(int i=0;i<extra.bagLimit()&&!incoming.isEmpty();i++){
            ItemStack existing=extra.bag.getItem(i);
            if(!existing.isEmpty()&&ItemStack.isSameItemSameComponents(existing,incoming)){
                int count=Math.min(incoming.getCount(),Math.max(0,existing.getMaxStackSize()-existing.getCount()));
                existing.grow(count);
                incoming.shrink(count);
            }
        }
        for(int i=0;i<extra.bagLimit()&&!incoming.isEmpty();i++)if(extra.bag.getItem(i).isEmpty())extra.bag.setItem(i,incoming.split(Math.min(incoming.getCount(),incoming.getMaxStackSize())));
        extra.bag.setChanged();
    }
    /** Physical slots only, for explicitly withdrawn items that must stay available to use. */
    public static int rawRoom(Player player,ItemStack incoming){
        int room=0;
        for(int i=0;i<36;i++){
            ItemStack existing=player.getInventory().getItem(i);
            if(existing.isEmpty())room+=incoming.getMaxStackSize();
            else if(ItemStack.isSameItemSameComponents(existing,incoming))room+=Math.max(0,existing.getMaxStackSize()-existing.getCount());
        }
        return room;
    }
    public static void insert(Player player,ItemStack incoming){
        InventoryIntake.receive(player,incoming);
        insertRaw(player,incoming);
    }
    public static void insertRaw(Player player,ItemStack incoming){
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

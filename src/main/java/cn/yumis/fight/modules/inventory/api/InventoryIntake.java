package cn.yumis.fight.modules.inventory.api;

import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

/** Item acquisition policies. Mutations run inside the caller's existing inventory transaction. */
public final class InventoryIntake {
    public interface Receiver {
        boolean accepts(ItemStack stack);
        int capacity(ServerPlayer player,ItemStack stack);
        /** Consume only the accepted part of incoming; leave any overflow in the supplied stack. */
        void receive(ServerPlayer player,ItemStack incoming);
    }
    private static final Map<String,Receiver> RECEIVERS=new LinkedHashMap<>();
    private InventoryIntake(){}
    public static void register(String id,Receiver receiver){
        if(RECEIVERS.putIfAbsent(id,Objects.requireNonNull(receiver))!=null)throw new IllegalArgumentException("Duplicate inventory receiver: "+id);
    }
    private static Receiver receiver(ItemStack stack){
        if(stack.isEmpty())return null;
        for(var receiver:RECEIVERS.values())if(receiver.accepts(stack))return receiver;
        return null;
    }
    public static int capacity(Player player,ItemStack stack){
        var receiver=receiver(stack);
        return player instanceof ServerPlayer server&&receiver!=null?Math.max(0,receiver.capacity(server,stack)):0;
    }
    public static void receive(Player player,ItemStack stack){
        var receiver=receiver(stack);
        if(player instanceof ServerPlayer server&&receiver!=null)receiver.receive(server,stack);
    }
    private static List<ItemStack> carried(Player player){
        List<ItemStack> stacks=new ArrayList<>();
        for(int i=0;i<36;i++)stacks.add(player.getInventory().getItem(i));
        stacks.add(player.getOffhandItem());
        stacks.add(player.containerMenu.getCarried());
        return stacks;
    }
    private static int count(List<ItemStack> stacks,ItemStack item){
        int count=0;
        for(var stack:stacks)if(ItemStack.isSameItemSameComponents(stack,item))count=Math.addExact(count,stack.getCount());
        return count;
    }
    /** Handles Shift+click collection from reward or machine output slots into inventory receivers. */
    private static boolean quickCollect(ServerPlayer player,AbstractContainerMenu menu,int index,ContainerInput input){
        if(input!=ContainerInput.QUICK_MOVE||!(menu instanceof QuickIntakeMenu intake)||!intake.allowsQuickIntake(index)||index<0||index>=menu.slots.size())return false;
        Slot slot=menu.slots.get(index);
        if(slot.container==player.getInventory()||!slot.isActive()||!slot.mayPickup(player))return false;
        ItemStack source=slot.getItem();
        int availableCapacity=capacity(player,source);
        if(source.isEmpty()||availableCapacity<=0)return false;
        ItemStack custom=intake.extractForQuickIntake(player,index,availableCapacity);
        if(custom!=null){
            if(custom.isEmpty())return false;
            ItemStack taken=custom.copy();
            receive(player,custom);
            if(!custom.isEmpty())throw new IllegalStateException("收纳容量发生变化，请重试");
            slot.setChanged();slot.onTake(player,taken);
            return true;
        }
        int take=Math.min(source.getCount(),availableCapacity);
        if(take<=0)return false;
        ItemStack removed=slot.remove(take),taken=removed.copy();
        if(removed.getCount()!=take)throw new IllegalStateException("物品来源发生变化，请重试");
        receive(player,removed);
        if(!removed.isEmpty())throw new IllegalStateException("收纳容量发生变化，请重试");
        slot.setChanged();slot.onTake(player,taken);
        return true;
    }
    /** Only Shift+click on QuickIntakeMenu reward/output slots auto-intakes into receivers; normal storage keeps physical items. */
    public static boolean click(ServerPlayer player,AbstractContainerMenu menu,int slot,int button,ContainerInput input,BooleanSupplier alternate){
        if(alternate.getAsBoolean())return true;
        if(quickCollect(player,menu,slot,input))return true;
        menu.clicked(slot,button,input,player);
        return true;
    }
}

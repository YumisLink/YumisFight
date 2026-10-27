package cn.yumis.fight.game;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import cn.yumis.fight.rules.CurrencyRules;
import net.neoforged.neoforge.transfer.item.ResourceHandlerSlot;
/** One shared definition of real, modifiable equipment slots. */
public final class CurrencySlots {
    private CurrencySlots(){}
    public static boolean isCurrency(ItemStack stack){
        if(stack.isEmpty()||!GameItems.own(stack))return false;
        String id=GameItems.id(stack);
        return CurrencyRules.find(id)!=null;
    }
    public static boolean isWritable(Slot slot,Player player){
        if(slot==null||!slot.isActive()||!slot.mayPickup(player)||!slot.mayPlace(slot.getItem()))return false;
        // ResourceHandlerSlot uses an empty placeholder container; its handler is validated by mayPlace.
        return slot instanceof ResourceHandlerSlot||slot.container.canPlaceItem(slot.getContainerSlot(),slot.getItem());
    }
    public static Slot requireWritable(ServerPlayer player,AbstractContainerMenu menu,int index){
        if(menu!=player.containerMenu||!menu.stillValid(player))throw new IllegalArgumentException("加工界面已失效");
        if(index<0||index>=menu.slots.size())throw new IllegalArgumentException("槽位无效");
        Slot slot=menu.slots.get(index);
        if(!isWritable(slot,player))throw new IllegalArgumentException("该栏位不能加工装备");
        return slot;
    }
}

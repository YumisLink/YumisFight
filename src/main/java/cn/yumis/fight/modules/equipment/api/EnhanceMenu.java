package cn.yumis.fight.modules.equipment.api;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

/** 凯莉的强化炉：上方炉口放置装备，下方为玩家背包；强化/增幅结果由本人客户端判定。 */
public final class EnhanceMenu extends AbstractContainerMenu {
    public final SimpleContainer furnace=new SimpleContainer(1);
    public EnhanceMenu(int id,Inventory inventory){
        super(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.ENHANCE_MENU.get(),id);
        addSlot(new net.minecraft.world.inventory.Slot(furnace,0,80,80){
            @Override public boolean mayPlace(ItemStack stack){return EquipmentItems.gear(stack)!=null;}
        });
        addStandardInventorySlots(inventory,8,186);
    }
    public static void open(ServerPlayer player){
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new EnhanceMenu(id,inventory),Component.literal("凯莉的强化炉")));
    }
    @Override public boolean stillValid(Player player){return player.isAlive();}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);var stack=slot.getItem();
        if(stack.isEmpty())return ItemStack.EMPTY;
        var copy=stack.copy();
        if(index==0){
            if(!moveItemStackTo(stack,1,slots.size(),false))return ItemStack.EMPTY;
        }else if(slots.get(0).getItem().isEmpty()&&slots.get(0).mayPlace(stack)){
            if(!moveItemStackTo(stack,0,1,false))return ItemStack.EMPTY;
        }else if(!moveItemStackTo(stack,index<28?28:1,index<28?slots.size():28,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();
        return copy;
    }
    @Override public void removed(Player player){
        // 关闭界面时把炉口装备完好交还，不吞装备。
        var held=furnace.getItem(0);
        if(!held.isEmpty()&&!player.level().isClientSide()){
            furnace.setItem(0,ItemStack.EMPTY);
            if(!player.getInventory().add(held))player.drop(held,false);
        }
        super.removed(player);
    }
}

package cn.yumis.fight.modules.equipment.api;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
public final class FabricatorMenu extends AbstractContainerMenu {
    public boolean initialContentsReady;
    @Override public void initializeContents(int stateId,java.util.List<ItemStack> items,ItemStack carried){super.initializeContents(stateId,items,carried);initialContentsReady=true;}
    private final ContainerLevelAccess access;
    public FabricatorMenu(int id,Inventory inventory){
        this(id,inventory,BlockPos.ZERO);
    }
    public FabricatorMenu(int id,Inventory inventory,BlockPos pos){
        super(cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR_MENU.get(),id);
        access=ContainerLevelAccess.create(inventory.player.level(),pos);
        addStandardInventorySlots(inventory,8,112);
    }
    @Override public boolean stillValid(Player p){
        return stillValid(access,p,cn.yumis.fight.modules.equipment.api.EquipmentContentTypes.FABRICATOR.get());
    }
    @Override public ItemStack quickMoveStack(Player p,int index){
        ItemStack s=slots.get(index).getItem();
        if(s.isEmpty())return ItemStack.EMPTY;
        ItemStack copy=s.copy();
        if(!moveItemStackTo(s,index<27?27:0,index<27?36:27,false))return ItemStack.EMPTY;
        slots.get(index).setChanged();
        return copy;
    }
}

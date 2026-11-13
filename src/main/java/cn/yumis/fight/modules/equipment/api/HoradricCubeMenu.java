package cn.yumis.fight.modules.equipment.api;

import cn.yumis.fight.modules.inventory.api.CheckpointMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Vanilla crafting escrow, with owner-authored essence processing and a non-takeable preview. */
public final class HoradricCubeMenu extends AbstractCraftingMenu implements CheckpointMenu {
    private final Player player;
    private final ContainerLevelAccess access;
    public boolean initialContentsReady;
    public HoradricCubeMenu(int id,Inventory inventory){this(id,inventory,BlockPos.ZERO);}
    public HoradricCubeMenu(int id,Inventory inventory,BlockPos pos){
        super(EquipmentContentTypes.HORADRIC_CUBE_MENU.get(),id,3,3);player=inventory.player;access=ContainerLevelAccess.create(player.level(),pos);
        addSlot(new net.minecraft.world.inventory.Slot(resultSlots,0,124,35){@Override public boolean mayPlace(ItemStack stack){return false;}@Override public boolean mayPickup(Player p){return false;}});
        addCraftingGridSlots(30,17);addStandardInventorySlots(inventory,8,84);
    }
    @Override public void initializeContents(int state,List<ItemStack> items,ItemStack carried){super.initializeContents(state,items,carried);initialContentsReady=true;}
    @Override protected Player owner(){return player;}
    @Override public net.minecraft.world.inventory.Slot getResultSlot(){return slots.getFirst();}
    @Override public List<net.minecraft.world.inventory.Slot> getInputGridSlots(){return List.copyOf(slots.subList(1,10));}
    @Override public RecipeBookType getRecipeBookType(){return RecipeBookType.CRAFTING;}
    @Override public PostPlaceAction handlePlacement(boolean maximum,boolean dropping,RecipeHolder<?> recipe,ServerLevel level,Inventory inventory){return PostPlaceAction.NOTHING;}
    public List<ItemStack> grid(){return getInputGridSlots().stream().map(net.minecraft.world.inventory.Slot::getItem).toList();}
    @Override public boolean stillValid(Player p){return stillValid(access,p,EquipmentContentTypes.HORADRIC_CUBE.get());}
    @Override public void removed(Player p){super.removed(p);if(!p.level().isClientSide())clearContainer(p,craftSlots);}
    @Override public ItemStack quickMoveStack(Player p,int index){
        if(index<=0||index>=slots.size())return ItemStack.EMPTY;
        var slot=slots.get(index);var source=slot.getItem();if(source.isEmpty())return ItemStack.EMPTY;var before=source.copy();
        if(index<10){if(!moveItemStackTo(source,10,46,true))return ItemStack.EMPTY;}
        else if(index<37){if(!moveItemStackTo(source,37,46,false))return ItemStack.EMPTY;}
        else if(!moveItemStackTo(source,10,37,false))return ItemStack.EMPTY;
        if(source.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        return before;
    }
}

package cn.yumis.fight.modules.inventory.api;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Native 26.2 SimpleContainer has no listeners; mutation revisions invalidate derived views. */
public final class TrackedContainer extends SimpleContainer {
    private long revision;
    public TrackedContainer(int size){super(size);}
    public long revision(){return revision;}
    @Override public void setChanged(){revision++;super.setChanged();}
    @Override public void setItem(int slot,ItemStack stack,boolean insideTransaction){super.setItem(slot,stack,insideTransaction);if(insideTransaction)revision++;}
    @Override public ItemStack removeItemNoUpdate(int slot){ItemStack removed=super.removeItemNoUpdate(slot);if(!removed.isEmpty())revision++;return removed;}
}

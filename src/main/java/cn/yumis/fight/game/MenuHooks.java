package cn.yumis.fight.game;
import cn.yumis.fight.FightMod;
import cn.yumis.fight.server.ServerRuntime;
import net.minecraft.world.entity.player.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import cn.yumis.fight.mixin.MenuAccessor;
public final class MenuHooks {
    private MenuHooks(){
    }
    public static ExtraInventory extra(Player p){
        ExtraInventory inventory=p.getData(FightMod.EXTRA);
        var menu=p.inventoryMenu;
        if(menu!=null)for(int i=0;i<menu.slots.size();i++){
            var old=menu.slots.get(i);
            if(old.container instanceof ExtraInventory&&old.container!=inventory){
                int item=old.getContainerSlot();
                var slot=equipmentSlot(inventory,item,p);
                slot.index=i;
                menu.slots.set(i,slot);
            }
        }
        return inventory;
    }
    private static net.minecraft.world.inventory.Slot equipmentSlot(ExtraInventory c,int index,Player p){
        return new net.minecraft.world.inventory.Slot(c,index,184+(index%2)*23,19+((index%6)/2)*29){
            @Override public boolean mayPlace(ItemStack stack){
                return c.canPlaceItem(index,stack);
            }
            @Override public boolean isActive(){
                return index<c.getContainerSize()&&(!p.level().isClientSide()||index/6==c.page);
            }
            @Override public int getMaxStackSize(){
                return 1;
            }
        }
        ;
    }
    public static void attach(AbstractContainerMenu menu,Player p){
        if(menu.slots.stream().anyMatch(s->s.container instanceof ExtraInventory))return;
        ExtraInventory c=extra(p);
        for(int i=0;i<EquipmentLayout.CAPACITY;i++){
            int index=i;
            ((MenuAccessor)menu).ydnf$addSlot(equipmentSlot(c,index,p));
        }
    }
    public static boolean click(AbstractContainerMenu menu,int slot,int button,ContainerInput input,Player p){
        if(!(p instanceof ServerPlayer player))return transfer(menu,slot,button,input,p);
        if(!ServerRuntime.ready()||ServerRuntime.get().inventory.inside())return false;
        if(menu!=p.inventoryMenu&&!(menu instanceof FabricatorMenu)&&!ServerRuntime.get().inventory.isBag(player))return false;
        ServerRuntime.get().inventory.click(player,menu,slot,button,input);
        return true;
    }
    public static boolean transfer(AbstractContainerMenu menu,int index,int button,ContainerInput input,Player player){
        if(menu!=player.inventoryMenu||index<0||index>=menu.slots.size())return false;
        ExtraInventory extra=extra(player);
        var source=menu.slots.get(index);
        if(source.container instanceof ExtraInventory){
            int position=source.getContainerSlot();
            if(position<0||position>=extra.getContainerSize())return true;
            var stored=extra.getItem(position);
            if(input==ContainerInput.QUICK_MOVE){
                var moving=stored.copy();
                InventoryPacking.insert(player,moving);
                extra.setItem(position,moving);
                return true;
            }
            if(input==ContainerInput.PICKUP&&(button==0||button==1)){
                var cursor=menu.getCarried();
                if(cursor.isEmpty()){
                    menu.setCarried(stored);
                    extra.setItem(position,net.minecraft.world.item.ItemStack.EMPTY);
                }
                else if(extra.canPlaceItem(position,cursor)&&cursor.getCount()==1){
                    extra.setItem(position,cursor.copy());
                    menu.setCarried(stored);
                }
                return true;
            }
        }
        if(input!=ContainerInput.QUICK_MOVE||source.container!=player.getInventory()||source.getContainerSlot()<0||source.getContainerSlot()>=36)return false;
        var moving=source.getItem();
        var gear=GameItems.gear(moving);
        if(gear==null||gear.slot.ordinal()<5)return false;
        for(int position=0;position<extra.getContainerSize();position++)if(extra.getItem(position).isEmpty()&&extra.canPlaceItem(position,moving)){
            extra.setItem(position,moving.copyWithCount(1));
            moving.shrink(1);
            source.set(moving.isEmpty()?net.minecraft.world.item.ItemStack.EMPTY:moving);
            return true;
        }
        return true;
    }
}

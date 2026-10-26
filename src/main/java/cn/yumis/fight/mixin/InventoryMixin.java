package cn.yumis.fight.mixin;
import cn.yumis.fight.game.MenuHooks;
import net.minecraft.world.inventory.*;
import net.minecraft.world.entity.player.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(InventoryMenu.class)
public abstract class InventoryMixin {
    @Inject(method="<init>",at=@At("RETURN")) private void ydnf$slots(Inventory inventory,boolean active,Player owner,CallbackInfo ci){
        MenuHooks.attach((InventoryMenu)(Object)this,owner);
    }
    @Inject(method="quickMoveStack",at=@At("HEAD"),cancellable=true) private void ydnf$quickMove(Player p,int index,CallbackInfoReturnable<net.minecraft.world.item.ItemStack> cir){
        InventoryMenu menu=(InventoryMenu)(Object)this;
        if(index<0||index>=menu.slots.size())return;
        var result=menu.slots.get(index).getItem().copy();
        if(MenuHooks.transfer(menu,index,0,ContainerInput.QUICK_MOVE,p))cir.setReturnValue(result);
    }
}

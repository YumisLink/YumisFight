package cn.yumis.fight.mixin;
import cn.yumis.fight.game.ExtraInventory;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractContainerScreen.class)
public abstract class EquipmentScreenBoundsMixin {
    @Inject(method="hasClickedOutside",at=@At("HEAD"),cancellable=true)
    private void ydnf$inside(double x,double y,int left,int top,CallbackInfoReturnable<Boolean> ci){
        var screen=(AbstractContainerScreen<?>)(Object)this;
        if(!(screen instanceof InventoryScreen||screen instanceof CreativeModeInventoryScreen))return;
        for(var slot:screen.getMenu().slots)if(slot.container instanceof ExtraInventory&&slot.isActive()&&x>=left+slot.x-5&&x<left+slot.x+21&&y>=top+slot.y-5&&y<top+slot.y+21){
            ci.setReturnValue(false);
            return;
        }
    }
}

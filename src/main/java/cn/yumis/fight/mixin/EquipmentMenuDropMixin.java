package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.EquipmentDropRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public abstract class EquipmentMenuDropMixin {
    @Inject(method="doClick",at=@At("HEAD"),cancellable=true)
    private void ydnf$protectDiscard(int index,int button,ContainerInput input,Player player,CallbackInfo callback){
        var menu=(AbstractContainerMenu)(Object)this;ItemStack candidate=ItemStack.EMPTY;
        if(input==ContainerInput.THROW&&index>=0&&index<menu.slots.size())candidate=menu.getSlot(index).getItem();
        else if(index==-999&&(input==ContainerInput.PICKUP||input==ContainerInput.QUICK_MOVE))candidate=menu.getCarried();
        if(EquipmentDropRules.blocked(player,candidate))callback.cancel();
    }
}

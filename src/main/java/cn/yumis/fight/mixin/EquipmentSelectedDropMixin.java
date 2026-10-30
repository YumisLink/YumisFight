package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.EquipmentDropRules;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class EquipmentSelectedDropMixin {
    @Shadow @Final public Player player;
    @Shadow public abstract ItemStack getSelectedItem();
    @Inject(method="removeFromSelected",at=@At("HEAD"),cancellable=true)
    private void ydnf$protectSelected(boolean all,CallbackInfoReturnable<ItemStack> result){
        if(EquipmentDropRules.blocked(player,getSelectedItem()))result.setReturnValue(ItemStack.EMPTY);
    }
}

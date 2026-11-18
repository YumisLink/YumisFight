package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.client.EquipmentActions;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeScreenMixin {
    @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
    private void ydnf$holdCurrencyCursor(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo callback) {
        if (EquipmentActions.creativeCurrencyPending()) callback.cancel();
    }
}

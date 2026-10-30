package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.client.MythicDisplay;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MythicItemNameMixin {
    @Inject(method="getHoverName",at=@At("RETURN"),cancellable=true)
    private void ydnf$mythicName(CallbackInfoReturnable<Component> result){
        if(MythicDisplay.mythic((ItemStack)(Object)this))result.setReturnValue(MythicDisplay.mark(result.getReturnValue()));
    }
}

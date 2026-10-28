package cn.yumis.fight.mixin;
import cn.yumis.fight.platform.ItemData;

import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ModItemNameMixin {
    @Inject(method="getHoverName",at=@At("RETURN"),cancellable=true)
    private void ydnf$font(CallbackInfoReturnable<Component> callback){
        if(ItemData.own((ItemStack)(Object)this))callback.setReturnValue(ModText.decorate(callback.getReturnValue()));
    }
}

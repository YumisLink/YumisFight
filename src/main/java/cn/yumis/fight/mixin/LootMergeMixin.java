package cn.yumis.fight.mixin;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ItemEntity.class)
public abstract class LootMergeMixin {
    @Inject(method="isMergable",at=@At("HEAD"),cancellable=true)
    private void ydnf$stableDropIdentity(CallbackInfoReturnable<Boolean> cir){
        if(!((ItemEntity)(Object)this).getPersistentData().getStringOr("ydnf_drop","").isEmpty())cir.setReturnValue(false);
    }
}

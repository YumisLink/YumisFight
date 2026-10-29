package cn.yumis.fight.mixin;

import cn.yumis.fight.bootstrap.ServerApplication;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native toss events run first; only their world projection waits for the inventory commit. */
@Mixin(ServerLevel.class)
public abstract class WorldDropMixin {
    @Inject(method="addFreshEntity",at=@At("HEAD"),cancellable=true)
    private void ydnf$deferDrop(Entity entity,CallbackInfoReturnable<Boolean> result){
        if(entity instanceof ItemEntity item&&ServerApplication.ready()&&ServerApplication.get().inventory.deferDrop(item))result.setReturnValue(true);
    }
}

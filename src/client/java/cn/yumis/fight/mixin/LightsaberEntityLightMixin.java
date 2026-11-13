package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.client.LightsaberLight;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class LightsaberEntityLightMixin {
    @Inject(method="getBlockLightLevel",at=@At("RETURN"),cancellable=true)
    private void ydnf$localEntityLight(Entity entity,BlockPos pos,CallbackInfoReturnable<Integer> result){result.setReturnValue(LightsaberLight.block(result.getReturnValue(),pos));}
}

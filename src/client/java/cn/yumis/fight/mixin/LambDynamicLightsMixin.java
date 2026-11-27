package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.presentation.api.client.LocalLights;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "dev.lambdaurora.lambdynlights.resource.entity.EntityLightSources", remap = false)
public abstract class LambDynamicLightsMixin {
    @Inject(method = "getLuminance(Lnet/minecraft/world/entity/Entity;)I", at = @At("RETURN"), cancellable = true, require = 0)
    private void ydnf$entityEmission(Entity entity, CallbackInfoReturnable<Integer> result) {
        int emission = LocalLights.entityEmission(entity);
        if (emission > result.getReturnValueI()) result.setReturnValue(emission);
    }
}

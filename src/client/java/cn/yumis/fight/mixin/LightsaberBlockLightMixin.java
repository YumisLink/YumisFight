package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.client.LightsaberLight;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
public abstract class LightsaberBlockLightMixin {
    @Inject(method="getLightCoords(Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/core/BlockPos;)I",at=@At("RETURN"),cancellable=true)
    private static void ydnf$localLight(BlockAndLightGetter level,BlockPos pos,CallbackInfoReturnable<Integer> result){result.setReturnValue(LightsaberLight.packed(result.getReturnValue(),pos));}
    @Inject(method="getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",at=@At("RETURN"),cancellable=true)
    private static void ydnf$localSample(LightCoordsUtil.BrightnessGetter getter,BlockAndLightGetter level,BlockState state,BlockPos pos,CallbackInfoReturnable<Integer> result){result.setReturnValue(LightsaberLight.packed(result.getReturnValue(),pos));}
}

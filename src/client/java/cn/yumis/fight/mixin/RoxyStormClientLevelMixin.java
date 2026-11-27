package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.dungeons.api.client.SluiceBossState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 确保水闸塔副本维度在《豪雷积雨云》激活期间于本机渲染真实倾盆雨幕（不受虚空/干燥群系降雨限制）。
 */
@Mixin(ClientLevel.class)
public abstract class RoxyStormClientLevelMixin {
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void ydnf$roxyStormPrecipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> ci) {
        if (SluiceBossState.stormStrength() > 0.05f) {
            ci.setReturnValue(Biome.Precipitation.RAIN);
        }
    }
}

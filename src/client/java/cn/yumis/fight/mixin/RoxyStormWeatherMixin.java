package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.dungeons.api.client.SluiceBossState;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 水闸塔第 20 层洛琪希《豪雷积雨云》本地暴雨与雷暴天象隔离：
 * 仅在当前客户端处于该洛琪希暴雨场次时提升本地降雨/雷暴等级，绝不污染服务端天气或其他副本。
 */
@Mixin(Level.class)
public abstract class RoxyStormWeatherMixin {
    @Shadow
    public abstract boolean isClientSide();

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void ydnf$roxyStormRainLevel(float partialTick, CallbackInfoReturnable<Float> ci) {
        if (isClientSide()) {
            float storm = SluiceBossState.stormStrength();
            if (storm > 0.001f) {
                ci.setReturnValue(Math.max(ci.getReturnValue(), storm));
            }
        }
    }

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void ydnf$roxyStormThunderLevel(float partialTick, CallbackInfoReturnable<Float> ci) {
        if (isClientSide()) {
            float thunder = SluiceBossState.thunderStrength();
            if (thunder > 0.001f) {
                ci.setReturnValue(Math.max(ci.getReturnValue(), thunder));
            }
        }
    }
}

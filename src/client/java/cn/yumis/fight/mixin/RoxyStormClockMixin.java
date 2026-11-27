package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.dungeons.api.client.SluiceBossState;
import cn.yumis.fight.modules.dungeons.content.SluiceTowerRoxy;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 水闸塔第 20 层洛琪希《豪雷积雨云》本地雷暴暗夜天象隔离：
 * 仅在当前客户端处于洛琪希暴雨场次时将天色切为深蓝风暴暮夜（15800 刻），不影响服务端时间与其他实例。
 */
@Mixin(ClientClockManager.class)
public abstract class RoxyStormClockMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void ydnf$roxyStormSkyTicks(Holder<WorldClock> clock, CallbackInfoReturnable<Long> ci) {
        if (!SealBossState.isMidnightSky() && SluiceBossState.darkSkyActive() && clock.is(WorldClocks.OVERWORLD)) {
            ci.setReturnValue(SluiceTowerRoxy.STORM_SKY_TIME_TICKS);
        }
    }

    @Inject(method = "getPartialTick", at = @At("HEAD"), cancellable = true)
    private void ydnf$roxyStormSkyPartial(Holder<WorldClock> clock, CallbackInfoReturnable<Float> ci) {
        if (!SealBossState.isMidnightSky() && SluiceBossState.darkSkyActive() && clock.is(WorldClocks.OVERWORLD)) {
            ci.setReturnValue(0.0f);
        }
    }
}

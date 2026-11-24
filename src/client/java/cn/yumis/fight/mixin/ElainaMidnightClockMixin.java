package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.dungeons.api.client.SealBossState;
import cn.yumis.fight.modules.dungeons.content.SealTowerElaina;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 封印塔第50层伊蕾娜50%转阶段本地午夜天象隔离：
 * 仅在当前客户端处于该伊蕾娜场次且午夜激活时，将本地主世界时钟渲染值固定为 18000 刻（午夜12点整），
 * 绝不调用 ServerLevel.setDayTime 污染其他副本实例。
 */
@Mixin(ClientClockManager.class)
public abstract class ElainaMidnightClockMixin {
    @Inject(method = "getTotalTicks", at = @At("HEAD"), cancellable = true)
    private void ydnf$elainaMidnightTicks(Holder<WorldClock> clock, CallbackInfoReturnable<Long> ci) {
        if (SealBossState.isMidnightSky() && clock.is(WorldClocks.OVERWORLD)) {
            ci.setReturnValue(SealTowerElaina.MIDNIGHT_TIME_TICKS);
        }
    }

    @Inject(method = "getPartialTick", at = @At("HEAD"), cancellable = true)
    private void ydnf$elainaMidnightPartial(Holder<WorldClock> clock, CallbackInfoReturnable<Float> ci) {
        if (SealBossState.isMidnightSky() && clock.is(WorldClocks.OVERWORLD)) {
            ci.setReturnValue(0.0f);
        }
    }
}

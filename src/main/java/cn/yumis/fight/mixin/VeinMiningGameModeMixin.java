package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.mining.api.VeinMining;
import cn.yumis.fight.modules.mining.api.VeinMining.Plan;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerGameMode.class)
public abstract class VeinMiningGameModeMixin implements VeinMining.MiningInput {
    @Shadow protected ServerLevel level;
    @Shadow @Final protected ServerPlayer player;
    @Shadow private boolean hasDelayedDestroy;
    @Shadow private BlockPos delayedDestroyPos;
    @Shadow public abstract GameType getGameModeForPlayer();
    @Unique private Plan ydnf$queued;
    @Unique private Plan ydnf$active;
    @Unique private Plan ydnf$delayed;

    @Override
    public void ydnf$queueMining(Plan plan) {
        ydnf$queued = plan;
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void ydnf$clearMiningInput(ServerLevel next, CallbackInfo callback) {
        ydnf$queued = null;
        ydnf$delayed = null;
        ydnf$active = null;
    }

    @WrapMethod(method = "handleBlockBreakAction")
    private void ydnf$applyMiningInput(BlockPos pos, Action action, Direction direction, int maxY,
                                      int sequence, Operation<Void> original) {
        Plan plan = ydnf$queued;
        ydnf$queued = null;
        if (plan == null || plan.sequence() != sequence || plan.level() != level || !plan.origin().equals(pos)
                || (action != Action.START_DESTROY_BLOCK && action != Action.STOP_DESTROY_BLOCK)) plan = null;

        boolean wasDelayed = hasDelayedDestroy;
        ydnf$active = plan;
        try {
            original.call(pos, action, direction, maxY, sequence);
            if (!wasDelayed && hasDelayedDestroy) {
                ydnf$delayed = delayedDestroyPos.equals(pos) ? ydnf$active : null;
            }
        } finally {
            ydnf$active = null;
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayerGameMode;destroyBlock(Lnet/minecraft/core/BlockPos;)Z"))
    private boolean ydnf$finishDelayedMining(ServerPlayerGameMode mode, BlockPos pos, Operation<Boolean> original) {
        ydnf$active = ydnf$delayed;
        ydnf$delayed = null;
        try {
            return original.call(mode, pos);
        } finally {
            ydnf$active = null;
        }
    }

    @WrapMethod(method = "destroyBlock")
    private boolean ydnf$breakVein(BlockPos pos, Operation<Boolean> original) {
        Plan plan = ydnf$active;
        if (plan == null || plan.level() != level || !plan.origin().equals(pos)) return original.call(pos);
        ydnf$active = null;
        var tool = player.getMainHandItem();
        boolean hadTool = !tool.isEmpty();
        var state = level.getBlockState(pos);
        boolean chain = state.is(plan.block()) && VeinMining.canMine(player, getGameModeForPlayer())
                && level.mayInteract(player, pos) && VeinMining.canHarvest(level, player, pos, state);

        boolean result = chain ? VeinMining.breakAtFeet(player, pos, () -> original.call(pos)) : original.call(pos);
        if (chain && result && (!hadTool || !tool.isEmpty()) && !level.getBlockState(pos).is(plan.block())) {
            VeinMining.breakConnected(player, plan, tool);
        }
        return result;
    }
}
